package com.swingauth.comment;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.swingauth.db.Mongo;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CommentService {

  private final MongoCollection<Document> comments = Mongo.comments();

  /** 해당 게시글의 댓글 수 */
  public int countByPostId(String postId) {
    if (postId == null) return 0;
    ObjectId oid = new ObjectId(postId);
    return (int) comments.countDocuments(Filters.eq("postId", oid));
  }

  /** 댓글 목록 (필요시 상세 보기에서 사용) */
  public List<Comment> listByPostId(String postId, int limit) {
    List<Comment> list = new ArrayList<>();
    if (postId == null) return list;
    ObjectId oid = new ObjectId(postId);

    try (MongoCursor<Document> cur = comments.find(Filters.eq("postId", oid))
        .sort(Sorts.ascending("createdAt"))
        .limit(limit)
        .iterator()) {
      while (cur.hasNext()) list.add(Comment.fromDoc(cur.next()));
    }
    return list;
  }

  /** 댓글 작성 */
  public Comment create(String postId, String authorUsername, String content) {
    if (postId == null || postId.isBlank())
      throw new IllegalArgumentException("postId 누락");
    if (authorUsername == null || authorUsername.isBlank())
      throw new IllegalArgumentException("작성자 누락");
    if (content == null || content.isBlank())
      throw new IllegalArgumentException("내용을 입력하세요.");

    Comment c = new Comment();
    c.postId = postId;
    c.authorUsername = authorUsername;
    c.content = content.trim();
    c.createdAt = new Date();

    comments.insertOne(c.toDoc());
    return c;
  }
}
