package com.swingauth.db;

import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

public class Mongo {
  private static MongoClient client;
  private static MongoDatabase db;

  public static synchronized MongoDatabase getDb() {
    if (db == null) {
      String uri = "mongodb+srv://qkddj:NJnlSBVbnOtH9y1y@qkddj.ayzxepo.mongodb.net/?retryWrites=true&w=majority";
      String dbName = "java_project_connect";

      client = MongoClients.create(uri);
      db = client.getDatabase(dbName);
      ensureIndexes();
    }
    return db;
  }

  public static MongoCollection<Document> users() {
    return getDb().getCollection("users");
  }

  public static MongoCollection<Document> posts() {
    return getDb().getCollection("posts");
  }

  // ★ 댓글용 컬렉션
  public static MongoCollection<Document> comments() {
    return getDb().getCollection("comments");
  }

  private static void ensureIndexes() {
    try {
      users().createIndex(Indexes.ascending("username"),
          new IndexOptions().unique(true).name("uniq_username"));
    } catch (MongoWriteException ignored) {}

    users().createIndex(Indexes.ascending("createdAt"));

    // 게시글: 게시판 + 생성일 역순 정렬 인덱스
    posts().createIndex(Indexes.descending("board", "createdAt"),
        new IndexOptions().name("board_createdAt_desc"));
    posts().createIndex(Indexes.ascending("authorUsername"));

    // 댓글: postId + createdAt 인덱스
    comments().createIndex(Indexes.ascending("postId"));
    comments().createIndex(Indexes.descending("postId", "createdAt"),
        new IndexOptions().name("post_createdAt_desc"));
  }
}
