package com.swingauth.db;

import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;

public class Mongo {
  private static MongoClient client;
  private static MongoDatabase db;

  public static synchronized MongoDatabase getDb() {
    if (db == null) {
      // 네 Atlas 설정에 맞게 하드코딩되어 있다고 가정 (필요시 환경변수로 변경 가능)
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

  private static void ensureIndexes() {
    try {
      users().createIndex(Indexes.ascending("username"),
          new IndexOptions().unique(true).name("uniq_username"));
    } catch (MongoWriteException ignored) {}
    users().createIndex(Indexes.ascending("createdAt"));

    // 게시글 인덱스: 보드별 최신 정렬
    posts().createIndex(Indexes.descending("board", "createdAt"),
        new IndexOptions().name("board_createdAt_desc"));
    posts().createIndex(Indexes.ascending("authorUsername"));
  }
}
