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
  public int toggleLike(User user, String postId) {
    ObjectId oid = new ObjectId(postId);

    Document filter = new Document("postId", postId)
            .append("username", user.username);

    Document found = Mongo.likes().find(filter).first();

    if (found == null) {
        // 좋아요 추가
        Mongo.likes().insertOne(filter.append("createdAt", new Date()));

        Mongo.posts().updateOne(
            Filters.eq("_id", oid),
            new Document("$inc", new Document("likesCount", 1))
        );
    } else {
        // 좋아요 취소
        Mongo.likes().deleteOne(filter);

        Mongo.posts().updateOne(
            Filters.eq("_id", oid),
            new Document("$inc", new Document("likesCount", -1))
        );
    }

    Document post = Mongo.posts().find(Filters.eq("_id", oid)).first();
    return post != null ? post.getInteger("likesCount", 0) : 0;
  }

  public Post getById(String id) {
    Document d = posts.find(Filters.eq("_id", new ObjectId(id))).first();
    return Post.fromDoc(d);
  }
}
