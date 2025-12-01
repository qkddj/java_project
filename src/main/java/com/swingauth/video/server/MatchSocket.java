package com.swingauth.video.server;

import com.swingauth.db.Mongo;
import com.swingauth.video.server.MatchManager.Room;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import java.io.IOException;
import java.util.UUID;

import org.json.JSONObject;

public class MatchSocket implements WebSocketListener {
    private Session session;
    private String userId;
    private String username;
    private final MatchManager manager = MatchManager.getInstance();

    @Override
    public void onWebSocketConnect(Session session) {
        this.session = session;
        this.userId = UUID.randomUUID().toString();
        sendMessage("{\"type\":\"hello\",\"userId\":\"" + userId + "\"}");
    }

    @Override
    public void onWebSocketText(String message) {
        try {
            // RTC 메시지는 먼저 처리 (JSON 파싱 전에 체크)
            if (message.contains("\"type\":\"rtc.")) {
                handleRtcMessage(message);
                return;
            }
            
            // endCall 메시지도 먼저 체크
            if (message.contains("\"type\":\"endCall\"")) {
                handleEndCall(message);
                return;
            }
            
            // 나머지 메시지는 JSON으로 파싱
            try {
                JSONObject json = new JSONObject(message);
                String type = json.optString("type", "");
                
                if ("registerUsername".equals(type)) {
                    this.username = json.optString("username", null);
                    if (this.username != null && !this.username.isEmpty() && !"unknown".equals(this.username)) {
                        manager.registerUsername(userId, username);
                        System.out.println("[MatchSocket] Username 등록됨: userId=" + userId + ", username=" + this.username);
                    } else {
                        System.err.println("[MatchSocket] Username 등록 실패: userId=" + userId + ", username=" + this.username);
                    }
                } else if ("joinQueue".equals(type)) {
                    manager.enqueue(this);
                } else if ("leaveQueue".equals(type)) {
                    manager.dequeue(this);
                } else if ("submitRating".equals(type)) {
                    handleSubmitRating(json);
                } else if ("endCall".equals(type)) {
                    handleEndCall(message);
                }
            } catch (org.json.JSONException e) {
                // JSON 파싱 실패 시 원본 메시지로 처리 시도
                if (message.contains("\"type\":\"joinQueue\"") || message.contains("joinQueue")) {
                    manager.enqueue(this);
                } else if (message.contains("\"type\":\"leaveQueue\"") || message.contains("leaveQueue")) {
                    manager.dequeue(this);
                }
            }
        } catch (Exception e) {
            System.err.println("메시지 처리 오류: " + message);
            e.printStackTrace();
        }
    }

    private void handleRtcMessage(String message) {
        try {
            String roomId = extractRoomId(message);
            if (roomId == null) return;

            Room room = manager.getRoom(roomId);
            if (room == null) return;

            MatchSocket other = (room.user1 == this) ? room.user2 : room.user1;
            if (other != null && other.isOpen()) {
                other.sendMessage(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleEndCall(String message) {
        try {
            String roomId = extractRoomId(message);
            if (roomId != null) {
                Room room = manager.getRoom(roomId);
                if (room != null) {
                    MatchSocket other = (room.user1 == this) ? room.user2 : room.user1;
                    if (other != null && other.isOpen()) {
                        other.sendMessage("{\"type\":\"callEnded\"}");
                    }
                    manager.removeRoom(roomId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractRoomId(String json) {
        int idx = json.indexOf("\"roomId\":\"");
        if (idx < 0) return null;
        int start = idx + 10;
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        manager.removeSocket(userId);
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        manager.removeSocket(userId);
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        // WebRTC는 텍스트 메시지만 사용
    }

    public void sendMessage(String message) {
        if (session != null && session.isOpen()) {
            try {
                session.getRemote().sendString(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendQueueStatus(int queueSize) {
        sendMessage("{\"type\":\"enqueued\",\"queueSize\":" + queueSize + "}");
    }

    public void sendMatched(String roomId, String peerId, String partnerUsername) {
        String message = String.format(
            "{\"type\":\"matched\",\"roomId\":\"%s\",\"peerId\":\"%s\",\"partnerUsername\":\"%s\"}",
            roomId, peerId, partnerUsername
        );
        sendMessage(message);
    }

    public boolean isOpen() {
        return session != null && session.isOpen();
    }

    public String getUserId() {
        return userId;
    }
    
    public String getUsername() {
        return username;
    }

    /**
     * 평점 제출 처리
     */
    private void handleSubmitRating(JSONObject json) {
        try {
            // 메시지에서 currentUsername을 먼저 확인하고, 없으면 this.username 사용
            String currentUsername = json.optString("currentUsername", null);
            if (currentUsername == null || currentUsername.isEmpty() || "unknown".equals(currentUsername)) {
                currentUsername = this.username;
            }
            
            String partnerUsername = json.optString("partnerUsername");
            int rating = json.optInt("rating");
            String serviceType = json.optString("serviceType", "randomVideo");
            
            if (currentUsername == null || currentUsername.isEmpty() || "unknown".equals(currentUsername) 
                    || partnerUsername == null || partnerUsername.isEmpty() || "unknown".equals(partnerUsername) 
                    || rating < 1 || rating > 5) {
                System.err.println("[오류] 평점 데이터 유효성 검사 실패");
                return;
            }
            
            // MongoDB에 평점 저장
            boolean success = saveRatingToMongo(currentUsername, partnerUsername, rating, serviceType);
            
            if (success) {
                System.out.println("[평점] " + currentUsername + " → " + partnerUsername + " : " + rating + "점 (" + serviceType + ")");
            }
            
            sendMessage("{\"type\":\"ratingSubmitted\",\"status\":\"success\"}");
        } catch (Exception e) {
            System.err.println("[오류] 평점 처리 실패: " + e.getMessage());
        }
    }

    /**
     * MongoDB에 평점 저장
     * @return 저장 성공 여부
     */
    private boolean saveRatingToMongo(String currentUser, String partnerUser, int rating, String serviceType) {
        try {
            // username을 ObjectId로 변환
            ObjectId currentUserId = getUserIdFromUsername(currentUser);
            ObjectId partnerUserId = getUserIdFromUsername(partnerUser);
            
            if (currentUserId == null || partnerUserId == null) {
                System.err.println("[오류] 사용자 ID 조회 실패: " + currentUser + " 또는 " + partnerUser);
                return false;
            }
            
            // user1Id와 user2Id를 ObjectId 16진수 문자열 비교로 사전순 정렬
            ObjectId[] sorted = sortObjectIds(currentUserId, partnerUserId);
            ObjectId user1Id = sorted[0];
            ObjectId user2Id = sorted[1];
            
            // 필터: ObjectId 타입으로 검색
            Document filter = new Document("user1Id", user1Id)
                .append("user2Id", user2Id)
                .append("serviceType", serviceType);
            
            // 기존 문서 확인 (같은 유저 쌍에 대한 기존 평점이 있는지 확인)
            Document existingDoc = Mongo.ratings().find(filter).first();
            boolean isNewDocument = (existingDoc == null);
            
            if (isNewDocument) {
                // 새 문서 생성: 첫 번째 평점이 평균이 됨
                Document newDoc = new Document()
                    .append("user1Id", user1Id)
                    .append("user2Id", user2Id)
                    .append("serviceType", serviceType)
                    .append("createdAt", new java.util.Date())
                    .append("updatedAt", new java.util.Date())
                    .append("averageRating", (double) rating);
                
                Mongo.ratings().insertOne(newDoc);
                
                // 피평가자(partnerUser)의 평점 합계 업데이트
                updateUserRatingStats(partnerUserId, rating, serviceType);
                
                return true;
            } else {
                // 기존 문서 업데이트: 기존 평균과 새 평점의 평균 계산
                double oldAverage = 5.0;
                Object avgObj = existingDoc.get("averageRating");
                if (avgObj instanceof Number) {
                    oldAverage = ((Number) avgObj).doubleValue();
                }
                double newAverage = Math.round(((oldAverage + rating) / 2.0) * 10.0) / 10.0;
                
                Document update = new Document("$set", new Document()
                    .append("updatedAt", new java.util.Date())
                    .append("averageRating", newAverage));
                
                Mongo.ratings().updateOne(filter, update);
                
                // 피평가자(partnerUser)의 평점 합계 업데이트
                updateUserRatingStats(partnerUserId, rating, serviceType);
                
                return true;
            }
            
        } catch (com.mongodb.MongoWriteException e) {
            if (e.getError().getCode() == 11000) {
                return true; // 이미 저장되어 있으므로 성공으로 간주
            } else {
                System.err.println("[오류] 평점 저장 실패: " + e.getMessage());
                return false;
            }
        } catch (Exception e) {
            System.err.println("[오류] 평점 저장 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * username으로부터 MongoDB의 ObjectId를 조회
     * @param username 사용자명
     * @return ObjectId, 존재하지 않으면 null
     */
    private ObjectId getUserIdFromUsername(String username) {
        try {
            Document userDoc = Mongo.users().find(Filters.eq("username", username)).first();
            if (userDoc != null) {
                Object id = userDoc.get("_id");
                if (id instanceof ObjectId) {
                    return (ObjectId) id;
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("[오류] 사용자 조회 실패: " + username);
            return null;
        }
    }

    /**
     * 두 ObjectId를 16진수 문자열 기준으로 사전순 정렬 (항상 일관된 정렬 결과 보장)
     */
    private ObjectId[] sortObjectIds(ObjectId id1, ObjectId id2) {
        String str1 = id1.toHexString();
        String str2 = id2.toHexString();
        if (str1.compareTo(str2) < 0) {
            return new ObjectId[]{id1, id2};
        } else {
            return new ObjectId[]{id2, id1};
        }
    }

    /**
     * 유저의 평점 합계를 업데이트합니다.
     * @param userId 유저 ObjectId
     * @param rating 평점 값
     * @param serviceType 서비스 타입 ("randomVideo" 또는 "randomChat")
     */
    private void updateUserRatingStats(ObjectId userId, int rating, String serviceType) {
        try {
            Document incDoc = new Document();
            
            // 서비스별 평점 합계 업데이트
            if ("randomVideo".equals(serviceType)) {
                incDoc.append("videoTotalRating", rating);
            } else if ("randomChat".equals(serviceType)) {
                incDoc.append("chatTotalRating", rating);
            }
            
            if (!incDoc.isEmpty()) {
                Document update = new Document("$inc", incDoc);
                Mongo.users().updateOne(Filters.eq("_id", userId), update);
                System.out.println("[영상통화] 사용자 평점 통계 업데이트: userId=" + userId + ", 받은 평점=" + rating);
            }
        } catch (Exception e) {
            System.err.println("[오류] 유저 평점 통계 업데이트 실패: " + e.getMessage());
        }
    }
}

