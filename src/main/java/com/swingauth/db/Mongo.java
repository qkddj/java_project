package com.swingauth.db;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import com.mongodb.MongoWriteException;

public class Mongo {
  private static MongoClient client;
  private static MongoDatabase db;

  public static synchronized MongoDatabase getDb() {
    if (db == null) {
      // ✅ 네 Atlas 클러스터 주소 & DB 이름
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

  private static void ensureIndexes() {
    // username 고유 인덱스
    try {
      users().createIndex(Indexes.ascending("username"),
          new IndexOptions().unique(true).name("uniq_username"));
    } catch (MongoWriteException ignored) {}

    users().createIndex(Indexes.ascending("createdAt"));
  }
}
