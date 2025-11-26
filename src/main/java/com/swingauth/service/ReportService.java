package com.swingauth.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.swingauth.db.Mongo;
import com.swingauth.model.Post;
import com.swingauth.model.User;
import org.bson.Document;

import java.util.Date;

public class ReportService {

  private final MongoCollection<Document> reports = Mongo.reports();

  /** 이 유저가 이 게시글을 이미 신고했는지 여부 */
  public boolean hasReported(User reporter, Post post) {
    if (reporter == null || post == null || post.id == null) return false;

    long count = reports.countDocuments(
        Filters.and(
            Filters.eq("postId", post.id),
            Filters.eq("reporterUsername", reporter.username)
        )
    );
    return count > 0;
  }

  /**
   * 게시글 신고 등록
   * - reports 컬렉션에 신고 내용 저장
   * - 신고 당한 사용자(users.reportsReceived) +1
   * - 같은 유저가 같은 글을 여러 번 신고하면 IllegalStateException 발생
   */
  public void reportPost(User reporter, Post post, String reason) {
    if (post == null || post.id == null) {
      throw new IllegalArgumentException("유효하지 않은 게시글입니다.");
    }
    if (reason == null || reason.trim().isEmpty()) {
      throw new IllegalArgumentException("신고 사유를 입력하세요.");
    }
    String trimmed = reason.trim();

    String reportedUsername = post.authorUsername;

    Document doc = new Document()
        .append("postId", post.id)
        .append("board", post.board)
        .append("reportedUsername", reportedUsername)
        .append("reporterUsername", reporter.username)
        .append("reason", trimmed)
        .append("createdAt", new Date());

    try {
      reports.insertOne(doc);
    } catch (Exception e) {
      // 유니크 인덱스(같은 글에 같은 사람이 두번 신고) 위반
      if (e.getMessage() != null && e.getMessage().contains("E11000")) {
        throw new IllegalStateException("이미 이 게시글을 신고했습니다.");
      }
      throw e;
    }

    if (reportedUsername != null) {
      Mongo.users().updateOne(
          Filters.eq("username", reportedUsername),
          new Document("$inc", new Document("reportsReceived", 1))
      );
    }
  }
}
