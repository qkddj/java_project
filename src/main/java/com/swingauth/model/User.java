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
  public String region;       // 시/도
  public String city;         // 시/군/구
  public String neighborhood; // region + city 등 파생표시

  // 평점 및 채팅 통계 (기본 집계)
  public Integer totalRatingReceived = 0;   // 받은 평점 합계 (전체)
  public Integer ratingCountReceived = 0;   // 받은 평점 횟수 (전체, 기존 필드 유지용)
  public Integer chatCount = 0;             // 채팅 횟수(전체 채팅 카운트)

  // 평점 정보 (전체)
  public Double  averageRating;            // 전체 평균 평점
  public Integer ratingCount;             // 전체 받은 평점 횟수(새 필드, 필요 시 ratingCountReceived와 동일하게 사용)

  // 랜덤 영상통화 통계
  public Integer videoCallCount;   // 영상통화 진행 횟수
  public Integer videoTotalRating; // 영상통화에서 받은 평점 합
  public Integer videoRatingCount; // 영상통화에서 받은 평점 횟수

  // 랜덤 채팅 통계
  public Integer randomChatCount;  // 랜덤채팅 진행 횟수
  public Integer chatTotalRating;  // 랜덤채팅에서 받은 평점 합
  public Integer chatRatingCount;  // 랜덤채팅에서 받은 평점 횟수

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

    // 평점 및 채팅 통계 (기본 집계)
    .append("totalRatingReceived", totalRatingReceived != null ? totalRatingReceived : 0)
    .append("ratingCountReceived", ratingCountReceived != null ? ratingCountReceived : 0)
    .append("chatCount", chatCount != null ? chatCount : 0)

    // 평점 정보 (전체)
    .append("averageRating", averageRating)
    .append("ratingCount", ratingCount)

    // 랜덤 영상통화 통계
    .append("videoCallCount", videoCallCount != null ? videoCallCount : 0)
    .append("videoTotalRating", videoTotalRating != null ? videoTotalRating : 0)
    .append("videoRatingCount", videoRatingCount != null ? videoRatingCount : 0)

    // 랜덤 채팅 통계
    .append("randomChatCount", randomChatCount != null ? randomChatCount : 0)
    .append("chatTotalRating", chatTotalRating != null ? chatTotalRating : 0)
    .append("chatRatingCount", chatRatingCount != null ? chatRatingCount : 0);
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

    // ===== 평점 정보 (전체) =====
    // averageRating: Double 또는 Number 타입 처리
    Object ar = d.get("averageRating");
    if (ar instanceof Double) {
      u.averageRating = (Double) ar;
    } else if (ar instanceof Number) {
      u.averageRating = ((Number) ar).doubleValue();
    } else {
      u.averageRating = null;
    }

    // totalRatingReceived: Integer 또는 Number 타입 처리 (기본 0)
    Object trr = d.get("totalRatingReceived");
    if (trr instanceof Number) {
      u.totalRatingReceived = ((Number) trr).intValue();
    } else {
      u.totalRatingReceived = 0;
    }

    // ratingCount (새 필드) : 없으면 ratingCountReceived로 fallback 가능
    Object rc = d.get("ratingCount");
    if (rc instanceof Number) {
      u.ratingCount = ((Number) rc).intValue();
    } else {
      u.ratingCount = null;
    }

    // ratingCountReceived (기존 필드)
    Object rcr = d.get("ratingCountReceived");
    if (rcr instanceof Number) {
      u.ratingCountReceived = ((Number) rcr).intValue();
    } else {
      u.ratingCountReceived = (u.ratingCount != null ? u.ratingCount : 0);
    }

    // ===== 랜덤 영상통화 통계 =====
    Object vcc = d.get("videoCallCount");
    if (vcc instanceof Number) {
      u.videoCallCount = ((Number) vcc).intValue();
    } else {
      u.videoCallCount = 0;
    }

    Object vtr = d.get("videoTotalRating");
    if (vtr instanceof Number) {
      u.videoTotalRating = ((Number) vtr).intValue();
    } else {
      u.videoTotalRating = 0;
    }

    Object vrc = d.get("videoRatingCount");
    if (vrc instanceof Number) {
      u.videoRatingCount = ((Number) vrc).intValue();
    } else {
      u.videoRatingCount = 0;
    }

    // ===== 랜덤 채팅 통계 =====
    Object rcc = d.get("randomChatCount");
    if (rcc instanceof Number) {
      u.randomChatCount = ((Number) rcc).intValue();
    } else {
      u.randomChatCount = 0;
    }

    Object ctr = d.get("chatTotalRating");
    if (ctr instanceof Number) {
      u.chatTotalRating = ((Number) ctr).intValue();
    } else {
      u.chatTotalRating = 0;
    }

    Object crc = d.get("chatRatingCount");
    if (crc instanceof Number) {
      u.chatRatingCount = ((Number) crc).intValue();
    } else {
      u.chatRatingCount = 0;
    }

    // ===== 기본 채팅 카운트 =====
    Object cc = d.get("chatCount");
    if (cc instanceof Number) {
      u.chatCount = ((Number) cc).intValue();
    } else {
      u.chatCount = 0;
    }

    return u;
  }
}
