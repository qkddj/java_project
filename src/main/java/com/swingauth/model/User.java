package com.swingauth.model;

import org.bson.Document;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class User {
  // 필수
  public String username;
  public String passwordHash;

  // 생성/로그인 타임스탬프
  public Instant createdAt = Instant.now();
  public Instant lastLoginAt;

  // IP/위치 정보
  public String lastKnownIp;
  public Double lat;
  public Double lon;
  public String country;
  public String region;      // 시/도
  public String city;        // 시/군/구
  public String neighborhood; // region + city 등 파생표시
  
  // 평점 및 채팅 통계
  public Integer totalRatingReceived = 0;  // 받은 평점 합계
  public Integer ratingCountReceived = 0;  // 받은 평점 횟수
  public Integer chatCount = 0;            // 채팅 횟수

  public Document toDoc() {
    return new Document(Map.of(
        "username", username,
        "passwordHash", passwordHash,
        // ✅ Instant -> Date 로 저장
        "createdAt", createdAt != null ? Date.from(createdAt) : null
    ))
    // ✅ Instant -> Date 로 저장
    .append("lastLoginAt", lastLoginAt != null ? Date.from(lastLoginAt) : null)
    .append("lastKnownIp", lastKnownIp)
    .append("lat", lat)
    .append("lon", lon)
    .append("country", country)
    .append("region", region)
    .append("city", city)
    .append("neighborhood", neighborhood)
    .append("totalRatingReceived", totalRatingReceived != null ? totalRatingReceived : 0)
    .append("ratingCountReceived", ratingCountReceived != null ? ratingCountReceived : 0)
    .append("chatCount", chatCount != null ? chatCount : 0);
  }

  public static User fromDoc(Document d) {
    if (d == null) return null;
    User u = new User();
    u.username = d.getString("username");
    u.passwordHash = d.getString("passwordHash");

    // ✅ createdAt: Date 또는 Instant 모두 안전 처리
    Object ca = d.get("createdAt");
    if (ca instanceof Date) {
      u.createdAt = ((Date) ca).toInstant();
    } else if (ca instanceof Instant) {
      u.createdAt = (Instant) ca;
    }

    // ✅ lastLoginAt: Date 또는 Instant 모두 안전 처리
    Object lla = d.get("lastLoginAt");
    if (lla instanceof Date) {
      u.lastLoginAt = ((Date) lla).toInstant();
    } else if (lla instanceof Instant) {
      u.lastLoginAt = (Instant) lla;
    }

    u.lastKnownIp = d.getString("lastKnownIp");
    u.lat = d.getDouble("lat");
    u.lon = d.getDouble("lon");
    u.country = d.getString("country");
    u.region = d.getString("region");
    u.city = d.getString("city");
    u.neighborhood = d.getString("neighborhood");
    
    // 평점 및 채팅 통계
    Object trr = d.get("totalRatingReceived");
    if (trr instanceof Number) u.totalRatingReceived = ((Number) trr).intValue();
    else u.totalRatingReceived = 0;
    
    Object rcr = d.get("ratingCountReceived");
    if (rcr instanceof Number) u.ratingCountReceived = ((Number) rcr).intValue();
    else u.ratingCountReceived = 0;
    
    Object cc = d.get("chatCount");
    if (cc instanceof Number) u.chatCount = ((Number) cc).intValue();
    else u.chatCount = 0;
    
    return u;
  }
}
