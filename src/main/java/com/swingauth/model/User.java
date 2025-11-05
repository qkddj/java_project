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
    .append("neighborhood", neighborhood);
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
    return u;
  }
}
