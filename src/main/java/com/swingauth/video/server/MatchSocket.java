package com.swingauth.video.server;

import com.swingauth.db.Mongo;
import com.swingauth.video.server.MatchManager.Room;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import java.io.IOException;
import java.util.Date;
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
                    manager.registerUsername(userId, username);
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
            String currentUsername = this.username;
            String partnerUsername = json.optString("partnerUsername");
            int rating = json.optInt("rating");
            String serviceType = json.optString("serviceType", "video");
            
            if (currentUsername == null || partnerUsername == null || rating < 1 || rating > 5) {
                System.err.println("평점 데이터 유효성 검사 실패: currentUsername=" + currentUsername + 
                    ", partnerUsername=" + partnerUsername + ", rating=" + rating);
                return;
            }
            
            // MongoDB에 평점 저장
            boolean success = saveRatingToMongo(currentUsername, partnerUsername, rating, serviceType);
            
            if (success) {
                System.out.println("========================================");
                System.out.println("평점 저장 성공!");
                System.out.println("평가자: " + currentUsername);
                System.out.println("피평가자: " + partnerUsername);
                System.out.println("평점: " + rating + "점");
                System.out.println("서비스 타입: " + serviceType);
                System.out.println("========================================");
            }
            
            sendMessage("{\"type\":\"ratingSubmitted\",\"status\":\"success\"}");
        } catch (Exception e) {
            System.err.println("평점 처리 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * MongoDB에 평점 저장
     * @return 저장 성공 여부
     */
    private boolean saveRatingToMongo(String currentUser, String partnerUser, int rating, String serviceType) {
        try {
            // user1Id와 user2Id를 사전순으로 정렬
            String[] sorted = sortUserIds(currentUser, partnerUser);
            String user1Id = sorted[0];
            String user2Id = sorted[1];
            
            // 현재 유저가 user1Id인지 확인
            boolean isUser1 = currentUser.equals(user1Id);
            
            Document filter = new Document("user1Id", user1Id)
                .append("user2Id", user2Id)
                .append("serviceType", serviceType);
            
            Document update = new Document();
            if (isUser1) {
                update.append("$set", new Document("user1Rating", rating)
                    .append("updatedAt", new Date()));
            } else {
                update.append("$set", new Document("user2Rating", rating)
                    .append("updatedAt", new Date()));
            }
            
            update.append("$setOnInsert", new Document()
                .append("createdAt", new Date()));
            
            Mongo.ratings().updateOne(filter, update, new UpdateOptions().upsert(true));
            return true;
        } catch (com.mongodb.MongoWriteException e) {
            // 중복 키 에러는 무시 (이미 저장된 경우)
            if (e.getError().getCode() == 11000) {
                System.out.println("평점 저장: 중복 키 에러 (이미 저장된 평가)");
                return true; // 이미 저장되어 있으므로 성공으로 간주
            } else {
                System.err.println("평점 저장 실패 (MongoWriteException): " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            System.err.println("평점 저장 실패: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 두 username을 사전순으로 정렬
     */
    private String[] sortUserIds(String id1, String id2) {
        if (id1.compareTo(id2) < 0) {
            return new String[]{id1, id2};
        } else {
            return new String[]{id2, id1};
        }
    }
}

