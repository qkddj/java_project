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
            System.out.println("========================================");
            System.out.println("[평점 저장 시작]");
            System.out.println("currentUser: " + currentUser);
            System.out.println("partnerUser: " + partnerUser);
            System.out.println("rating: " + rating);
            System.out.println("serviceType: " + serviceType);
            
            // username을 ObjectId로 변환
            ObjectId currentUserId = getUserIdFromUsername(currentUser);
            ObjectId partnerUserId = getUserIdFromUsername(partnerUser);
            
            System.out.println("[평점 저장] currentUserId 조회 결과: " + currentUserId);
            System.out.println("[평점 저장] partnerUserId 조회 결과: " + partnerUserId);
            
            if (currentUserId == null || partnerUserId == null) {
                System.err.println("평점 저장 실패: 사용자 ID를 찾을 수 없습니다. currentUser=" + currentUser + ", partnerUser=" + partnerUser);
                System.err.println("currentUserId: " + currentUserId + ", partnerUserId: " + partnerUserId);
                return false;
            }
            
            // user1Id와 user2Id를 ObjectId 16진수 문자열 비교로 사전순 정렬
            ObjectId[] sorted = sortObjectIds(currentUserId, partnerUserId);
            ObjectId user1Id = sorted[0];
            ObjectId user2Id = sorted[1];
            
            // 디버깅: ObjectId 확인
            System.out.println("[평점 저장] user1Id (ObjectId): " + user1Id);
            System.out.println("[평점 저장] user2Id (ObjectId): " + user2Id);
            System.out.println("[평점 저장] user1Id 타입: " + user1Id.getClass().getName());
            System.out.println("[평점 저장] user2Id 타입: " + user2Id.getClass().getName());
            
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
                // 각 유저의 전체 평균 평점도 함께 저장
                double user1AverageRating = getUserAverageRating(user1Id);
                double user2AverageRating = getUserAverageRating(user2Id);
                
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
                newDoc.append("user1AverageRating", user1AverageRating);
                newDoc.append("user2AverageRating", user2AverageRating);
                
                Mongo.ratings().insertOne(newDoc);
                System.out.println("[평점 저장] ✅ 새 문서 생성 완료 (첫 번째 만남)");
                System.out.println("  - user1Id: " + user1Id + ", user2Id: " + user2Id);
                System.out.println("  - " + (isUser1 ? "user1Rating" : "user2Rating") + ": " + rating);
                System.out.println("  - user1AverageRating: " + user1AverageRating + ", user2AverageRating: " + user2AverageRating);
                
                // 평점 저장 후 두 유저 모두의 평균 평점 재계산 및 업데이트
                // user1Id와 user2Id 모두에 대해 average 문서 생성/업데이트
                System.out.println("[평점 저장] 평균 평점 업데이트 시작 - user1Id: " + user1Id + ", user2Id: " + user2Id);
                updateUserAverageRating(user1Id); // user1Id의 평균 업데이트
                System.out.println("[평점 저장] user1Id 평균 업데이트 완료");
                updateUserAverageRating(user2Id); // user2Id의 평균 업데이트
                System.out.println("[평점 저장] user2Id 평균 업데이트 완료");
                
                // 두 유저 쌍의 평균 평점 계산 및 저장 (serviceType이 "video"인 경우만)
                if ("video".equals(serviceType)) {
                    updatePairAverageRating(user1Id, user2Id);
                }
                
                return true;
            } else {
                // 기존 문서 업데이트: 같은 유저 쌍이 다시 만난 경우, 기존 평점을 새 점수로 변경
                // 기존 평점 확인 (로깅용)
                Object oldRating = null;
                if (isUser1) {
                    oldRating = existingDoc.get("user1Rating");
                } else {
                    oldRating = existingDoc.get("user2Rating");
                }
                
                Document setDoc = new Document();
                if (isUser1) {
                    setDoc.append("user1Rating", rating);
                } else {
                    setDoc.append("user2Rating", rating);
                }
                Document update = new Document("$set", setDoc);
                
                System.out.println("[평점 저장] ✅ 기존 문서 업데이트 (다시 만남 - 점수 변경)");
                System.out.println("  - user1Id: " + user1Id + ", user2Id: " + user2Id);
                System.out.println("  - 기존 평점: " + (oldRating != null ? oldRating : "없음") + " → 새 평점: " + rating);
                System.out.println("  - Filter: " + filter.toJson());
                System.out.println("  - Update: " + update.toJson());
                
                com.mongodb.client.result.UpdateResult result = Mongo.ratings().updateOne(filter, update);
                System.out.println("  - 업데이트 결과 - Matched: " + result.getMatchedCount() + ", Modified: " + result.getModifiedCount());
                
                // 평점 업데이트 후 두 유저 모두의 평균 평점 재계산 및 업데이트
                // user1Id와 user2Id 모두에 대해 average 문서 생성/업데이트
                System.out.println("[평점 저장] 평균 평점 업데이트 시작 - user1Id: " + user1Id + ", user2Id: " + user2Id);
                updateUserAverageRating(user1Id); // user1Id의 평균 업데이트
                System.out.println("[평점 저장] user1Id 평균 업데이트 완료");
                updateUserAverageRating(user2Id); // user2Id의 평균 업데이트
                System.out.println("[평점 저장] user2Id 평균 업데이트 완료");
                
                // 두 유저 쌍의 평균 평점 계산 및 저장 (serviceType이 "video"인 경우만)
                if ("video".equals(serviceType)) {
                    updatePairAverageRating(user1Id, user2Id);
                }
                
                return true;
            }
            
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
     * username으로부터 MongoDB의 ObjectId를 조회
     * @param username 사용자명
     * @return ObjectId, 존재하지 않으면 null
     */
    private ObjectId getUserIdFromUsername(String username) {
        try {
            System.out.println("[getUserIdFromUsername] 조회 중: username=" + username);
            Document userDoc = Mongo.users().find(Filters.eq("username", username)).first();
            System.out.println("[getUserIdFromUsername] 조회 결과: " + (userDoc != null ? "찾음" : "없음"));
            if (userDoc != null) {
                System.out.println("[getUserIdFromUsername] 문서 내용: " + userDoc.toJson());
                Object id = userDoc.get("_id");
                System.out.println("[getUserIdFromUsername] _id 값: " + id);
                System.out.println("[getUserIdFromUsername] _id 타입: " + (id != null ? id.getClass().getName() : "null"));
                if (id instanceof ObjectId) {
                    System.out.println("[getUserIdFromUsername] ObjectId 반환: " + id);
                    return (ObjectId) id;
                } else {
                    System.err.println("[getUserIdFromUsername] 경고: _id가 ObjectId 타입이 아님. 타입: " + (id != null ? id.getClass().getName() : "null"));
                }
            } else {
                System.err.println("[getUserIdFromUsername] 사용자를 찾을 수 없음: " + username);
            }
            return null;
        } catch (Exception e) {
            System.err.println("사용자 ID 조회 실패 (username=" + username + "): " + e.getMessage());
            e.printStackTrace();
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
            double averageRating;
            if (count > 0) {
                averageRating = sum / count;
            } else {
                averageRating = 5.0; // 평점이 없으면 기본값 5.0
            }
            
            // ratings 컬렉션에 평균 평점 저장/업데이트 (serviceType: "average")
            // 각 유저마다 별도의 average 문서 생성
            try {
                Document filter = new Document("userId", userId)
                    .append("serviceType", "average");
                
                // 먼저 기존 문서가 있는지 확인
                Document existingDoc = Mongo.ratings().find(filter).first();
                
                if (existingDoc != null) {
                    // 기존 문서가 있으면 업데이트
                    Document updateDoc = new Document("$set", new Document("averageRating", averageRating));
                    com.mongodb.client.result.UpdateResult result = Mongo.ratings().updateOne(filter, updateDoc);
                    
                    System.out.println("[평균 평점 업데이트] userId: " + userId + ", 평균: " + averageRating + " (" + count + "개 평점) - 기존 문서 업데이트");
                    System.out.println("  - 업데이트 결과 - Matched: " + result.getMatchedCount() + ", Modified: " + result.getModifiedCount());
                } else {
                    // 기존 문서가 없으면 새로 생성 (user1Id, user2Id 필드 없이)
                    Document newDoc = new Document("userId", userId)
                        .append("serviceType", "average")
                        .append("averageRating", averageRating);
                    
                    Mongo.ratings().insertOne(newDoc);
                    System.out.println("[평균 평점 업데이트] userId: " + userId + ", 평균: " + averageRating + " (" + count + "개 평점) - 새 문서 생성");
                }
            } catch (Exception e) {
                System.err.println("[평균 평점 업데이트] 오류 발생: userId=" + userId + " - " + e.getMessage());
                e.printStackTrace();
            }
            
        } catch (Exception e) {
            System.err.println("[평균 평점 업데이트] 오류 발생: userId=" + userId + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 두 유저 쌍의 평균 평점을 계산하고 해당 문서의 average 필드에 저장합니다.
     * user1Id, user2Id, serviceType이 "video"인 문서의 user1Rating과 user2Rating의 평균을 계산합니다.
     * 또한 각 유저의 전체 평균 평점도 함께 저장합니다.
     * @param user1Id 첫 번째 유저 ObjectId
     * @param user2Id 두 번째 유저 ObjectId
     */
    private void updatePairAverageRating(ObjectId user1Id, ObjectId user2Id) {
        try {
            // user1Id, user2Id, serviceType: "video"인 문서 조회
            Document filter = new Document("user1Id", user1Id)
                .append("user2Id", user2Id)
                .append("serviceType", "video");
            
            Document ratingDoc = Mongo.ratings().find(filter).first();
            
            if (ratingDoc == null) {
                System.out.println("[유저 쌍 평균 평점 업데이트] 문서를 찾을 수 없음: user1Id=" + user1Id + ", user2Id=" + user2Id);
                return;
            }
            
            // user1Rating과 user2Rating 가져오기
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
            
            // 두 평점의 평균 계산
            double pairAverage;
            if (count == 0) {
                pairAverage = 5.0; // 평점이 없으면 기본값
            } else if (count == 1) {
                pairAverage = (user1RatingObj != null && user1RatingObj instanceof Number) ? user1Rating : user2Rating;
            } else {
                pairAverage = (user1Rating + user2Rating) / 2.0;
            }
            
            // 각 유저의 전체 평균 평점 조회
            double user1AverageRating = getUserAverageRating(user1Id);
            double user2AverageRating = getUserAverageRating(user2Id);
            
            // average 필드와 각 유저의 평균 평점 업데이트
            Document update = new Document("$set", new Document("average", pairAverage)
                .append("user1AverageRating", user1AverageRating)
                .append("user2AverageRating", user2AverageRating));
            com.mongodb.client.result.UpdateResult result = Mongo.ratings().updateOne(filter, update);
            
            System.out.println("[유저 쌍 평균 평점 업데이트] user1Id: " + user1Id + ", user2Id: " + user2Id);
            System.out.println("  - user1Rating: " + (user1RatingObj != null ? user1RatingObj : "없음") + 
                             ", user2Rating: " + (user2RatingObj != null ? user2RatingObj : "없음"));
            System.out.println("  - user1AverageRating: " + user1AverageRating + ", user2AverageRating: " + user2AverageRating);
            System.out.println("  - 유저 쌍 평균 (user1Rating + user2Rating) / 2: " + pairAverage);
            System.out.println("  - 업데이트 결과 - Matched: " + result.getMatchedCount() + ", Modified: " + result.getModifiedCount());
            
        } catch (Exception e) {
            System.err.println("[유저 쌍 평균 평점 업데이트] 오류 발생: user1Id=" + user1Id + ", user2Id=" + user2Id + " - " + e.getMessage());
            e.printStackTrace();
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

