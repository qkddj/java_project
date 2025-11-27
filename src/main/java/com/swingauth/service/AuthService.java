package com.swingauth.service;

import com.swingauth.db.Mongo;
import com.swingauth.model.User;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;

public class AuthService {
  private final MongoCollection<Document> users = Mongo.users();
  private final GeoService geo = new GeoService();

  public void signUp(String username, String password) {
    String uname = normalize(username);
    if (uname.isBlank() || password == null || password.length() < 8) {
      throw new IllegalArgumentException("아이디/비밀번호를 확인하세요. (비밀번호 8자 이상)");
    }
    if (users.find(Filters.eq("username", uname)).first() != null) {
      throw new IllegalStateException("이미 존재하는 아이디입니다.");
    }

    // 위치 조회
    GeoService.GeoInfo g = geo.fetch();

    User u = new User();
    u.username = uname;
    u.passwordHash = BCrypt.hashpw(password, BCrypt.gensalt()); // 해시
    u.createdAt = Instant.now();
    u.lastLoginAt = null;
    u.lastKnownIp = g.ip;
    u.lat = g.lat;
    u.lon = g.lon;
    u.country = g.country;
    u.region = g.region;
    u.city = g.city;
    u.neighborhood = g.neighborhood;

    try {
      users.insertOne(u.toDoc());
    } catch (MongoWriteException e) {
      // unique 인덱스 경합 시
      if (e.getError() != null && e.getError().getCode() == 11000) {
        throw new IllegalStateException("이미 존재하는 아이디입니다.");
      }
      throw e;
    }
  }

  public User login(String username, String password) {
    String uname = normalize(username);
    Document found = users.find(Filters.eq("username", uname)).first();
    if (found == null) throw new IllegalArgumentException("존재하지 않는 아이디입니다.");

    User u = User.fromDoc(found);
    if (!BCrypt.checkpw(password, u.passwordHash)) {
      throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
    }

    // 기존 사용자 계정에 통계 필드가 없으면 기본값으로 추가
    ensureUserStatsFields(uname, found);

    return User.fromDoc(found);
  }
  
  /**
   * 사용자 계정에 통계 필드가 없으면 기본값으로 추가
   */
  private void ensureUserStatsFields(String username, Document userDoc) {
    boolean needsUpdate = false;
    Document updateFields = new Document();
    
    if (!userDoc.containsKey("totalRatingReceived")) {
      updateFields.append("totalRatingReceived", 0);
      needsUpdate = true;
    }
    if (!userDoc.containsKey("ratingCountReceived")) {
      updateFields.append("ratingCountReceived", 0);
      needsUpdate = true;
    }
    if (!userDoc.containsKey("chatCount")) {
      updateFields.append("chatCount", 0);
      needsUpdate = true;
    }
    
    if (needsUpdate) {
      users.updateOne(
        Filters.eq("username", username),
        new Document("$set", updateFields)
      );
      System.out.println("사용자 통계 필드 초기화: username=" + username);
    }
  }

  private String normalize(String s) {
    return s == null ? "" : s.trim();
  }
}
