package com.swingauth.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.swingauth.db.Mongo;
import com.swingauth.model.Post;
import com.swingauth.model.User;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PostService {

  private final MongoCollection<Document> posts = Mongo.posts();

  /** 보드별 최신 글 목록 가져오기 */
  public List<Post> listByBoard(String board, int limit) {
    List<Post> list = new ArrayList<>();
    try (MongoCursor<Document> cur = posts.find(Filters.eq("board", board))
        .sort(Sorts.descending("createdAt"))
        .limit(limit)
        .iterator()) {
      while (cur.hasNext()) list.add(Post.fromDoc(cur.next()));
    }
    return list;
  }

  /** 새 글 등록 */
  public Post create(User user, String board, String title, String content) {
    String t = (title == null ? "" : title.trim());
    String c = (content == null ? "" : content.trim());
    if (board == null || board.isBlank()) throw new IllegalArgumentException("게시판 이름이 없습니다.");
    if (t.isBlank()) throw new IllegalArgumentException("제목을 입력하세요.");
    if (c.isBlank()) throw new IllegalArgumentException("내용을 입력하세요.");

    Post p = new Post();
    p.board = board;
    p.title = t;
    p.content = c;
    p.authorUsername = user.username;
    p.neighborhood = user.neighborhood;
    p.createdAt = new Date();

    posts.insertOne(p.toDoc());
    // insertOne은 _id를 반환하지 않으므로 다시 읽을 필요가 있으면 조건 구성 필요(간단히는 최신 글 재조회)
    return p;
  }
}
