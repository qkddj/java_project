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
  public String neighborhood;      // 지역(옵션)
  public Date createdAt;

  public Document toDoc() {
    return new Document()
        .append("board", board)
        .append("title", title)
        .append("content", content)
        .append("authorUsername", authorUsername)
        .append("neighborhood", neighborhood)
        .append("createdAt", createdAt != null ? createdAt : new Date());
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
    return p;
  }

  @Override
  public String toString() {
    // 리스트에서 예쁘게 보이도록 제목 + 날짜
    String date = (createdAt != null)
        ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(createdAt)
        : "";
    return "[" + board + "] " + title + "  —  " + date;
  }
}
