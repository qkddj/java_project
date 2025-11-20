package com.swingauth.db;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.conversions.Bson;

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

  public static MongoCollection<Document> comments() {
    return getDb().getCollection("comments");
  }

  public static MongoCollection<Document> likes() {
    return getDb().getCollection("likes");
  }

  public static MongoCollection<Document> ratings() {
    return getDb().getCollection("ratings");
  }

  /** 인덱스 만들 때 충돌(이미 존재 등)은 그냥 무시하는 헬퍼 */
  private static void safeCreateIndex(MongoCollection<Document> coll, Bson keys, IndexOptions options) {
    try {
      coll.createIndex(keys, options);
    } catch (MongoWriteException | MongoCommandException ignored) {
      // 인덱스 이미 존재하거나 옵션 충돌 시 무시
    } catch (Exception ignored) {
    }
  }

  private static void safeCreateIndex(MongoCollection<Document> coll, Bson keys) {
    try {
      coll.createIndex(keys);
    } catch (MongoWriteException | MongoCommandException ignored) {
    } catch (Exception ignored) {
    }
  }

  private static void ensureIndexes() {
    // users
    safeCreateIndex(
        users(),
        Indexes.ascending("username"),
        new IndexOptions().unique(true).name("uniq_username")
    );
    safeCreateIndex(users(), Indexes.ascending("createdAt"));

    // posts: 게시판 + 생성일 역순
    safeCreateIndex(posts(), Indexes.descending("board", "createdAt"));
    safeCreateIndex(posts(), Indexes.ascending("authorUsername"));

    // comments: postId + createdAt
    safeCreateIndex(comments(), Indexes.ascending("postId"));
    safeCreateIndex(comments(), Indexes.descending("postId", "createdAt"));

    // likes: (postId, username) 유니크 = 한 유저당 한 번만 좋아요
    safeCreateIndex(
        likes(),
        Indexes.ascending("postId", "username"),
        new IndexOptions().unique(true).name("uniq_post_user_like")
    );
    safeCreateIndex(likes(), Indexes.ascending("postId"));

    // ratings: 평점 인덱스
    // 기존에 있던 잘못된 인덱스 삭제 시도
    try {
      ratings().dropIndex("uniq_sessionId");
    } catch (Exception ignored) {}
    
    // 평점 인덱스: 유저 쌍과 서비스 타입으로 유니크 제약
    safeCreateIndex(
        ratings(),
        Indexes.ascending("user1Id", "user2Id", "serviceType"),
        new IndexOptions().unique(true).name("uniq_user_pair_service")
    );
    safeCreateIndex(ratings(), Indexes.ascending("user1Id"), 
        new IndexOptions().name("idx_user1Id"));
    safeCreateIndex(ratings(), Indexes.ascending("user2Id"), 
        new IndexOptions().name("idx_user2Id"));
    safeCreateIndex(ratings(), Indexes.ascending("serviceType"), 
        new IndexOptions().name("idx_serviceType"));
    safeCreateIndex(ratings(), Indexes.descending("createdAt"), 
        new IndexOptions().name("idx_createdAt_desc"));
  }
}
