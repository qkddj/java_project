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
            String serviceType = json.optString("serviceType", "video");
            
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
            
            // 현재 유저가 user1Id인지 확인
            boolean isUser1 = currentUserId.equals(user1Id);
            
            // 필터: ObjectId 타입으로 검색
            Document filter = new Document("user1Id", user1Id)
                .append("user2Id", user2Id)
                .append("serviceType", serviceType);
            
            // 기존 문서 확인 (같은 유저 쌍에 대한 기존 평점이 있는지 확인)
            Document existingDoc = Mongo.ratings().find(filter).first();
            boolean isNewDocument = (existingDoc == null);
            
            if (isNewDocument) {
                // 새 문서 생성: 첫 번째 만남이거나 아직 평점이 없는 경우
                Document newDoc = new Document();
                newDoc.put("user1Id", user1Id);
                newDoc.put("user2Id", user2Id);
                newDoc.append("serviceType", serviceType);
                if (isUser1) {
                    newDoc.append("user1Rating", rating);
                    newDoc.append("user2Rating", null);
                } else {
                    newDoc.append("user1Rating", null);
                    newDoc.append("user2Rating", rating);
                }
                
                Mongo.ratings().insertOne(newDoc);
                
                // 평점 저장 후 두 유저 모두의 평균 평점 재계산 및 업데이트
                updateUserAverageRating(user1Id);
                updateUserAverageRating(user2Id);
                
                // 두 유저 쌍의 평균 평점 계산 및 저장 (serviceType이 "video"인 경우만)
                if ("video".equals(serviceType)) {
                    updatePairAverageRating(user1Id, user2Id);
                }
                
                return true;
            } else {
                // 기존 문서 업데이트: 같은 유저 쌍이 다시 만난 경우, 기존 평점을 새 점수로 변경
                Document setDoc = new Document();
                if (isUser1) {
                    setDoc.append("user1Rating", rating);
                } else {
                    setDoc.append("user2Rating", rating);
                }
                Document update = new Document("$set", setDoc);
                
                Mongo.ratings().updateOne(filter, update);
                
                // 평점 업데이트 후 두 유저 모두의 평균 평점 재계산 및 업데이트
                updateUserAverageRating(user1Id);
                updateUserAverageRating(user2Id);
                
                // 두 유저 쌍의 평균 평점 계산 및 저장 (serviceType이 "video"인 경우만)
                if ("video".equals(serviceType)) {
                    updatePairAverageRating(user1Id, user2Id);
                }
                
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
     * 사용자의 평균 평점을 재계산하고 ratings 컬렉션에 저장합니다.
     * @param userId 사용자 ObjectId
     */
    private void updateUserAverageRating(ObjectId userId) {
        try {
            // 해당 사용자가 받은 모든 평점 조회 (serviceType이 "average"가 아닌 것만)
            double sum = 0.0;
            int count = 0;
            
            for (Document doc : Mongo.ratings().find(
                Filters.and(
                    Filters.or(
                        Filters.eq("user1Id", userId),
                        Filters.eq("user2Id", userId)
                    ),
                    Filters.ne("serviceType", "average") // 평균 평점 문서는 제외
                )
            )) {
                ObjectId docUser1Id = doc.get("user1Id", ObjectId.class);
                ObjectId docUser2Id = doc.get("user2Id", ObjectId.class);
                
                // 해당 사용자가 받은 평점만 추출
                if (docUser1Id != null && docUser1Id.equals(userId)) {
                    Object user1Rating = doc.get("user1Rating");
                    if (user1Rating != null && user1Rating instanceof Number) {
                        sum += ((Number) user1Rating).doubleValue();
                        count++;
                    }
                } else if (docUser2Id != null && docUser2Id.equals(userId)) {
                    Object user2Rating = doc.get("user2Rating");
                    if (user2Rating != null && user2Rating instanceof Number) {
                        sum += ((Number) user2Rating).doubleValue();
                        count++;
                    }
                }
            }
            
            // 평균 계산
            double averageRating = count > 0 ? sum / count : 5.0;
            
            // ratings 컬렉션에 평균 평점 저장/업데이트 (serviceType: "average")
            Document filter = new Document("userId", userId)
                .append("serviceType", "average");
            
            Document existingDoc = Mongo.ratings().find(filter).first();
            
            if (existingDoc != null) {
                Document updateDoc = new Document("$set", new Document("averageRating", averageRating));
                Mongo.ratings().updateOne(filter, updateDoc);
            } else {
                Document newDoc = new Document("userId", userId)
                    .append("serviceType", "average")
                    .append("averageRating", averageRating);
                Mongo.ratings().insertOne(newDoc);
            }
            
        } catch (Exception e) {
            System.err.println("[오류] 평균 평점 업데이트 실패: " + e.getMessage());
        }
    }

    /**
     * 두 유저 쌍의 평균 평점을 계산하고 해당 문서의 average 필드에 저장합니다.
     * user1Id, user2Id, serviceType이 "video"인 문서의 user1Rating과 user2Rating의 평균을 계산합니다.
     * @param user1Id 첫 번째 유저 ObjectId
     * @param user2Id 두 번째 유저 ObjectId
     */
    private void updatePairAverageRating(ObjectId user1Id, ObjectId user2Id) {
        try {
            Document filter = new Document("user1Id", user1Id)
                .append("user2Id", user2Id)
                .append("serviceType", "video");
            
            Document ratingDoc = Mongo.ratings().find(filter).first();
            if (ratingDoc == null) return;
            
            Object user1RatingObj = ratingDoc.get("user1Rating");
            Object user2RatingObj = ratingDoc.get("user2Rating");
            
            double user1Rating = 0.0;
            double user2Rating = 0.0;
            int count = 0;
            
            if (user1RatingObj != null && user1RatingObj instanceof Number) {
                user1Rating = ((Number) user1RatingObj).doubleValue();
                count++;
            }
            
            if (user2RatingObj != null && user2RatingObj instanceof Number) {
                user2Rating = ((Number) user2RatingObj).doubleValue();
                count++;
            }
            
            double pairAverage;
            if (count == 0) {
                pairAverage = 5.0;
            } else if (count == 1) {
                pairAverage = (user1RatingObj != null && user1RatingObj instanceof Number) ? user1Rating : user2Rating;
            } else {
                pairAverage = (user1Rating + user2Rating) / 2.0;
            }
            
            Document update = new Document("$set", new Document("average", pairAverage));
            Mongo.ratings().updateOne(filter, update);
            
        } catch (Exception e) {
            System.err.println("[오류] 유저 쌍 평균 평점 업데이트 실패: " + e.getMessage());
        }
    }

    /**
     * 사용자의 평균 평점을 ratings 컬렉션에서 조회합니다.
     * serviceType이 "average"인 문서에서 값을 가져옵니다.
     * @param userId 사용자 ObjectId
     * @return 평균 평점 (없으면 5.0)
     */
    private double getUserAverageRating(ObjectId userId) {
        try {
            // ratings 컬렉션에서 평균 평점 조회 (serviceType이 "average"인 문서)
            Document avgDoc = Mongo.ratings().find(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("serviceType", "average")
                )
            ).first();
            
            if (avgDoc == null) {
                return 5.0; // 평균 평점이 없으면 기본값 5.0
            }
            
            Object ar = avgDoc.get("averageRating");
            if (ar == null) {
                return 5.0;
            } else if (ar instanceof Double) {
                return (Double) ar;
            } else if (ar instanceof Number) {
                return ((Number) ar).doubleValue();
            } else {
                return 5.0;
            }
            
        } catch (Exception e) {
            System.err.println("[사용자 평균 평점 조회] 오류 발생: userId=" + userId + " - " + e.getMessage());
            return 5.0;
        }
    }
}

