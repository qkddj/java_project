package com.swingauth.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.swingauth.db.Mongo;
import com.swingauth.model.Post;
import com.swingauth.model.User;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PostService {

  private final MongoCollection<Document> posts = Mongo.posts();

  /** ===============================
   *  지역 기반 + 게시판 필터 + 최신순 목록 조회
   *  =============================== */
  public List<Post> listByBoard(User user, String board, int limit) {
    List<Post> list = new ArrayList<>();

    String neighborhood = (user.neighborhood == null || user.neighborhood.isBlank())
        ? "unknown" : user.neighborhood;

    try (MongoCursor<Document> cur = posts.find(
            Filters.and(
                Filters.eq("board", board),
                Filters.eq("neighborhood", neighborhood)   // ★ 지역 필터 추가
            ))
        .sort(Sorts.descending("createdAt"))
        .limit(limit)
        .iterator()) {

      while (cur.hasNext()) list.add(Post.fromDoc(cur.next()));
    }

    return list;
  }

  /** ===============================
   *  새 글 등록
   *  =============================== */
  public Post create(User user, String board, String title, String content) {

    if (board == null || board.isBlank())
      throw new IllegalArgumentException("게시판 이름이 없습니다.");

    if (title == null || title.isBlank())
      throw new IllegalArgumentException("제목을 입력하세요.");

    if (content == null || content.isBlank())
      throw new IllegalArgumentException("내용을 입력하세요.");

    Post p = new Post();
    p.board = board;
    p.title = title.trim();
    p.content = content.trim();
    p.authorUsername = user.username;
    p.neighborhood = user.neighborhood; // ★ 지역 저장
    p.createdAt = new Date();

    posts.insertOne(p.toDoc());
    return p;
  }

  /** ===============================
   *  게시글 수정
   *  =============================== */
  public Post update(Post p, String newTitle, String newContent) {
    if (p.id == null) throw new IllegalArgumentException("글 ID가 없습니다.");

    if (newTitle == null || newTitle.isBlank())
      throw new IllegalArgumentException("제목을 입력하세요.");
    if (newContent == null || newContent.isBlank())
      throw new IllegalArgumentException("내용을 입력하세요.");

    posts.updateOne(
        Filters.eq("_id", new ObjectId(p.id)),
        new Document("$set", new Document()
            .append("title", newTitle)
            .append("content", newContent)
        )
    );

    p.title = newTitle;
    p.content = newContent;
    return p;
  }

  /** ===============================
   *  ID로 게시글 단건 조회
   *  =============================== */
  public Post getById(String id) {
    Document d = posts.find(Filters.eq("_id", new ObjectId(id))).first();
    return Post.fromDoc(d);
  }
}
