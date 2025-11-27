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

  // 평점 정보 (전체)
  public Double averageRating; // 전체 평균 평점
  public Integer totalRatingReceived; // 전체 받은 평점의 합
  public Integer ratingCount; // 전체 받은 평점 횟수
  
  // 랜덤 영상통화 통계
  public Integer videoCallCount; // 영상통화 진행 횟수
  public Integer videoTotalRating; // 영상통화에서 받은 평점 합
  public Integer videoRatingCount; // 영상통화에서 받은 평점 횟수
  
  // 랜덤 채팅 통계
  public Integer randomChatCount; // 랜덤채팅 진행 횟수
  public Integer chatTotalRating; // 랜덤채팅에서 받은 평점 합
  public Integer chatRatingCount; // 랜덤채팅에서 받은 평점 횟수

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
    .append("averageRating", averageRating)
    .append("totalRatingReceived", totalRatingReceived)
    .append("ratingCount", ratingCount)
    .append("videoCallCount", videoCallCount)
    .append("videoTotalRating", videoTotalRating)
    .append("videoRatingCount", videoRatingCount)
    .append("randomChatCount", randomChatCount)
    .append("chatTotalRating", chatTotalRating)
    .append("chatRatingCount", chatRatingCount);
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
    
    // averageRating: Double 또는 Number 타입 처리
    Object ar = d.get("averageRating");
    if (ar instanceof Double) {
      u.averageRating = (Double) ar;
    } else if (ar instanceof Number) {
      u.averageRating = ((Number) ar).doubleValue();
    } else {
      u.averageRating = null;
    }
    
    // totalRatingReceived: Integer 또는 Number 타입 처리
    Object trr = d.get("totalRatingReceived");
    if (trr instanceof Integer) {
      u.totalRatingReceived = (Integer) trr;
    } else if (trr instanceof Number) {
      u.totalRatingReceived = ((Number) trr).intValue();
    } else {
      u.totalRatingReceived = null;
    }
    
    // ratingCount: Integer 또는 Number 타입 처리
    Object rc = d.get("ratingCount");
    if (rc instanceof Integer) {
      u.ratingCount = (Integer) rc;
    } else if (rc instanceof Number) {
      u.ratingCount = ((Number) rc).intValue();
    } else {
      u.ratingCount = null;
    }
    
    // videoCallCount: Integer 또는 Number 타입 처리
    Object vcc = d.get("videoCallCount");
    if (vcc instanceof Integer) {
      u.videoCallCount = (Integer) vcc;
    } else if (vcc instanceof Number) {
      u.videoCallCount = ((Number) vcc).intValue();
    } else {
      u.videoCallCount = null;
    }
    
    // videoTotalRating
    Object vtr = d.get("videoTotalRating");
    if (vtr instanceof Integer) {
      u.videoTotalRating = (Integer) vtr;
    } else if (vtr instanceof Number) {
      u.videoTotalRating = ((Number) vtr).intValue();
    } else {
      u.videoTotalRating = null;
    }
    
    // videoRatingCount
    Object vrc = d.get("videoRatingCount");
    if (vrc instanceof Integer) {
      u.videoRatingCount = (Integer) vrc;
    } else if (vrc instanceof Number) {
      u.videoRatingCount = ((Number) vrc).intValue();
    } else {
      u.videoRatingCount = null;
    }
    
    // randomChatCount
    Object rcc = d.get("randomChatCount");
    if (rcc instanceof Integer) {
      u.randomChatCount = (Integer) rcc;
    } else if (rcc instanceof Number) {
      u.randomChatCount = ((Number) rcc).intValue();
    } else {
      u.randomChatCount = null;
    }
    
    // chatTotalRating
    Object ctr = d.get("chatTotalRating");
    if (ctr instanceof Integer) {
      u.chatTotalRating = (Integer) ctr;
    } else if (ctr instanceof Number) {
      u.chatTotalRating = ((Number) ctr).intValue();
    } else {
      u.chatTotalRating = null;
    }
    
    // chatRatingCount
    Object crc = d.get("chatRatingCount");
    if (crc instanceof Integer) {
      u.chatRatingCount = (Integer) crc;
    } else if (crc instanceof Number) {
      u.chatRatingCount = ((Number) crc).intValue();
    } else {
      u.chatRatingCount = null;
    }
    
    return u;
  }
}
