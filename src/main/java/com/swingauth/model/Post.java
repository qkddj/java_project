package com.swingauth.model;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Post {
  public String id;
  public String board;             // 게시판 이름
  public String title;
  public String content;
  public String authorUsername;
  public String neighborhood;      // 지역
  public Date createdAt;
  public Integer likesCount;       // ★ 좋아요 수 (기본 0)

  public Document toDoc() {
    return new Document()
        .append("board", board)
        .append("title", title)
        .append("content", content)
        .append("authorUsername", authorUsername)
        .append("neighborhood", neighborhood)
        .append("createdAt", createdAt != null ? createdAt : new Date())
        .append("likesCount", likesCount != null ? likesCount : 0);
  }

  public static Post fromDoc(Document d) {
    if (d == null) return null;
    Post p = new Post();
    ObjectId oid = d.getObjectId("_id");
    p.id = (oid != null) ? oid.toHexString() : null;
    p.board = d.getString("board");
    p.title = d.getString("title");
    p.content = d.getString("content");
    p.authorUsername = d.getString("authorUsername");
    p.neighborhood = d.getString("neighborhood");
    Object ca = d.get("createdAt");
    if (ca instanceof Date) p.createdAt = (Date) ca;

    Object lc = d.get("likesCount");
    if (lc instanceof Number) p.likesCount = ((Number) lc).intValue();
    else p.likesCount = 0;

    return p;
  }

  @Override
  public String toString() {
    // 디버그용
    String date = (createdAt != null)
        ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(createdAt)
        : "";
    return "[" + board + "] " + title + " (" + authorUsername + ", " + date + ")";
  }
}
