package com.swingauth.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.swingauth.db.Mongo;
import com.swingauth.model.Post;
import com.swingauth.model.User;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class PostService {

  private final MongoCollection<Document> posts = Mongo.posts();
  private final MongoCollection<Document> likes = Mongo.likes();       // 좋아요 기록
  private final MongoCollection<Document> dislikes = Mongo.dislikes(); // 싫어요 기록

  /** 게시판 + 지역 + 검색어 기반 목록 (페이징) */
  public List<Post> listByBoard(User user, String board, String keyword, int skip, int limit) {
    List<Post> list = new ArrayList<>();
    List<Bson> filters = new ArrayList<>();

    filters.add(Filters.eq("board", board));

    String neighborhood = (user.neighborhood == null || user.neighborhood.isBlank())
        ? "unknown" : user.neighborhood;
    filters.add(Filters.eq("neighborhood", neighborhood));

    if (keyword != null && !keyword.isBlank()) {
      String kw = keyword.trim();
      Pattern regex = Pattern.compile(Pattern.quote(kw),
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      filters.add(Filters.or(
          Filters.regex("title", regex),
          Filters.regex("content", regex)
      ));
    }

    Bson finalFilter = Filters.and(filters);

    try (MongoCursor<Document> cur = posts.find(finalFilter)
        .sort(Sorts.descending("createdAt"))
        .skip(skip)
        .limit(limit)
        .iterator()) {

      while (cur.hasNext()) {
        list.add(Post.fromDoc(cur.next()));
      }
    }
    return list;
  }

  /** 새 글 등록 */
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
    p.neighborhood = user.neighborhood;
    p.createdAt = new Date();
    p.likesCount = 0;

    posts.insertOne(p.toDoc());
    return p;
  }

  /** 게시글 수정 */
  public Post update(Post p, String newTitle, String newContent) {
    if (p.id == null)
      throw new IllegalArgumentException("글 ID가 없습니다.");

    String t = (newTitle == null ? "" : newTitle.trim());
    String c = (newContent == null ? "" : newContent.trim());

    if (t.isBlank())
      throw new IllegalArgumentException("제목을 입력하세요.");
    if (c.isBlank())
      throw new IllegalArgumentException("내용을 입력하세요.");

    posts.updateOne(
        Filters.eq("_id", new ObjectId(p.id)),
        new Document("$set", new Document()
            .append("title", t)
            .append("content", c)
        )
    );

    p.title = t;
    p.content = c;
    return p;
  }

  /** 👍 좋아요 토글
   *  - likes 컬렉션에 (postId, username) 기록/삭제
   *  - posts.likesCount 증가/감소
   *  - 글 작성자 users.likesReceived 증가/감소
   *  @return 변경 후 좋아요 수
   */
  public int toggleLike(User user, String postId) {
    ObjectId oid = new ObjectId(postId);

    // 게시글 조회 (작성자 정보 얻기)
    Document postDoc = posts.find(Filters.eq("_id", oid)).first();
    if (postDoc == null) {
      throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
    }
    String author = postDoc.getString("authorUsername");

    Document filter = new Document("postId", postId)
        .append("username", user.username);

    Document found = likes.find(filter).first();

    int delta; // +1 or -1

    if (found == null) {
      // 좋아요 추가
      likes.insertOne(new Document(filter).append("createdAt", new Date()));
      delta = 1;
    } else {
      // 좋아요 취소
      likes.deleteOne(filter);
      delta = -1;
    }

    // 게시글 좋아요 수 변경
    posts.updateOne(
        Filters.eq("_id", oid),
        new Document("$inc", new Document("likesCount", delta))
    );

    // 작성자 누적 좋아요 수 변경
    if (author != null) {
      Mongo.users().updateOne(
          Filters.eq("username", author),
          new Document("$inc", new Document("likesReceived", delta))
      );
    }

    // 변경된 좋아요 수 반환
    Document updated = posts.find(Filters.eq("_id", oid)).first();
    int likesCount = 0;
    if (updated != null) {
      Object lcObj = updated.get("likesCount");
      if (lcObj instanceof Number) {
        likesCount = ((Number) lcObj).intValue();
      }
    }
    return likesCount;
  }

  /** 👎 싫어요 토글
   *  - dislikes 컬렉션에 (postId, username) 기록/삭제
   *  - 글 작성자 users.dislikesReceived 증가/감소
   *  - 화면에는 개수 표시 안 함
   *  @return true  = 지금 상태가 "싫어요 눌림"
   *          false = 지금 상태가 "싫어요 취소"
   */
  public boolean toggleDislike(User user, String postId) {
    ObjectId oid = new ObjectId(postId);

    Document postDoc = posts.find(Filters.eq("_id", oid)).first();
    if (postDoc == null) {
      throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
    }
    String author = postDoc.getString("authorUsername");

    Document filter = new Document("postId", postId)
        .append("username", user.username);

    Document found = dislikes.find(filter).first();

    int delta;
    boolean nowDisliked;

    if (found == null) {
      // 새로 싫어요
      dislikes.insertOne(new Document(filter).append("createdAt", new Date()));
      delta = 1;
      nowDisliked = true;
    } else {
      // 싫어요 취소
      dislikes.deleteOne(filter);
      delta = -1;
      nowDisliked = false;
    }

    if (author != null) {
      Mongo.users().updateOne(
          Filters.eq("username", author),
          new Document("$inc", new Document("dislikesReceived", delta))
      );
    }

    return nowDisliked;
  }

  public Post getById(String id) {
    Document d = posts.find(Filters.eq("_id", new ObjectId(id))).first();
    return Post.fromDoc(d);
  }
}
