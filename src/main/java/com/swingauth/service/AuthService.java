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
  // 게시글 개수 계산용
  private final MongoCollection<Document> posts = Mongo.posts();
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

    // 이미 정지된 계정이면 바로 차단
    if (getBool(found, "isBanned")) {
      throw new IllegalStateException("정지된 계정입니다. 관리자에게 문의하세요.");
    }

    User u = User.fromDoc(found);
    if (!BCrypt.checkpw(password, u.passwordHash)) {
      throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
    }

    // 통계 필드가 없으면 기본값으로 추가
    ensureUserStatsFields(uname, found);

    // (필요하면) 갱신된 도큐먼트 다시 읽기
    Document userDoc = users.find(Filters.eq("username", uname)).first();
    if (userDoc == null) {
      userDoc = found;
    }

    // 자동 블랙리스트 판정 및 DB 반영
    applyAutoBlacklist(uname, userDoc);

    // 다시 한 번 정지 여부 확인
    if (getBool(userDoc, "isBanned")) {
      throw new IllegalStateException("정지된 계정입니다. 관리자에게 문의하세요.");
    }

    return User.fromDoc(userDoc);
  }

  /**
   * 사용자 계정에 통계 필드가 없으면 기본값으로 추가
   */
  private void ensureUserStatsFields(String username, Document userDoc) {
    boolean needsUpdate = false;
    Document updateFields = new Document();

    // 신고 횟수
    if (!userDoc.containsKey("reportsReceived")) {
      updateFields.append("reportsReceived", 0);
      needsUpdate = true;
    }

    // 영상통화 통계 필드
    if (!userDoc.containsKey("videoCallCount")) {
      updateFields.append("videoCallCount", 0);
      needsUpdate = true;
    }
    if (!userDoc.containsKey("videoTotalRating")) {
      updateFields.append("videoTotalRating", 0);
      needsUpdate = true;
    }

    // 랜덤채팅 통계 필드
    if (!userDoc.containsKey("randomChatCount")) {
      updateFields.append("randomChatCount", 0);
      needsUpdate = true;
    }
    if (!userDoc.containsKey("chatTotalRating")) {
      updateFields.append("chatTotalRating", 0);
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

  /**
   * 로그인 시 자동 블랙리스트 판정
   * - 조건을 만족하면 users 컬렉션에 isBanned, bannedAt, banReason 필드 저장
   */
  private void applyAutoBlacklist(String username, Document userDoc) {
    // 이미 정지된 경우는 다시 계산할 필요 없음
    if (getBool(userDoc, "isBanned")) return;

    // posts DB에서 게시글 개수 가져오기
    long postCount = posts.countDocuments(Filters.eq("authorUsername", username));

    int reportsReceived = getInt(userDoc, "reportsReceived");
    int videoCallCount = getInt(userDoc, "videoCallCount");
    int videoTotalRating = getInt(userDoc, "videoTotalRating");
    int randomChatCount = getInt(userDoc, "randomChatCount");
    int chatTotalRating = getInt(userDoc, "chatTotalRating");

    double avgVideo = (videoCallCount > 0)
        ? (double) videoTotalRating / videoCallCount
        : 0.0;
    double avgChat = (randomChatCount > 0)
        ? (double) chatTotalRating / randomChatCount
        : 0.0;
    double reportRatio = (postCount > 0)
        ? (double) reportsReceived / (double) postCount
        : 0.0;

    boolean rule1 = postCount > 0 && reportRatio >= 10.0;
    boolean rule2 = (videoCallCount >= 5) && (avgVideo < 2.0);
    boolean rule3 = (randomChatCount >= 5) && (avgChat < 2.0);

    boolean ratioOver5 = postCount > 0 && reportRatio >= 5.0;
    boolean videoLow3 = (videoCallCount >= 5) && (avgVideo < 3.0);
    boolean chatLow3 = (randomChatCount >= 5) && (avgChat < 3.0);
    boolean rule4 = ratioOver5 && (videoLow3 || chatLow3);

    boolean shouldBan = rule1 || rule2 || rule3 || rule4;
    if (!shouldBan) return;

    StringBuilder reason = new StringBuilder();
    if (rule1) {
      reason.append("[신고 기준] 신고/게시글 비율 10 이상; ");
    }
    if (rule2) {
      reason.append("[영상통화 기준] 5회 이상, 평균 평점 2 미만; ");
    }
    if (rule3) {
      reason.append("[랜덤채팅 기준] 5회 이상, 평균 평점 2 미만; ");
    }
    if (rule4) {
      reason.append("[조합 기준] 신고 비율 5 이상 + 낮은 평점; ");
    }

    Document set = new Document()
        .append("isBanned", true)
        .append("bannedAt", Date.from(Instant.now()))
        .append("banReason", reason.toString());

    users.updateOne(
        Filters.eq("username", username),
        new Document("$set", set)
    );

    // 현재 메모리상의 userDoc에도 반영
    userDoc.put("isBanned", true);
    userDoc.put("bannedAt", set.get("bannedAt"));
    userDoc.put("banReason", set.get("banReason"));

    System.out.println("[블랙리스트] " + username + " 정지 처리: " + reason);
  }

  private int getInt(Document doc, String key) {
    Object v = doc.get(key);
    if (v instanceof Number) {
      return ((Number) v).intValue();
    }
    return 0;
  }

  private boolean getBool(Document doc, String key) {
    Object v = doc.get(key);
    if (v instanceof Boolean) return (Boolean) v;
    return false;
  }

  private String normalize(String s) {
    return s == null ? "" : s.trim();
  }
}
