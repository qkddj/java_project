package com.swingauth.service;

import com.swingauth.db.Mongo;
import com.swingauth.model.User;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.Date;

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

    return User.fromDoc(found);
  }

  private String normalize(String s) {
    return s == null ? "" : s.trim();
  }
}
