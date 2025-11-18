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
  private final MongoCollection<Document> likes = Mongo.likes(); // ★ 좋아요 컬렉션

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

  /** 좋아요 (한 유저당 한 번만 가능) */
  public int like(User user, String postId) {
    if (postId == null)
      throw new IllegalArgumentException("postId 누락");
    if (user == null || user.username == null)
      throw new IllegalArgumentException("사용자 정보 누락");

    ObjectId oid = new ObjectId(postId);

    // 이미 좋아요 눌렀는지 확인
    Document existing = likes.find(
        Filters.and(
            Filters.eq("postId", oid),
            Filters.eq("username", user.username)
        )
    ).first();

    if (existing != null) {
      // 이미 좋아요 한 사람
      throw new IllegalStateException("이미 좋아요를 누른 게시글입니다.");
    }

    // 좋아요 추가
    likes.insertOne(new Document()
        .append("postId", oid)
        .append("username", user.username)
        .append("createdAt", new Date())
    );

    // 좋아요 수 재계산
    long count = likes.countDocuments(Filters.eq("postId", oid));

    posts.updateOne(
        Filters.eq("_id", oid),
        new Document("$set", new Document("likesCount", (int) count))
    );

    return (int) count;
  }

  public Post getById(String id) {
    Document d = posts.find(Filters.eq("_id", new ObjectId(id))).first();
    return Post.fromDoc(d);
  }
}
