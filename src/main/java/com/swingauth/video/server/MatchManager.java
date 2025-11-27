package com.swingauth.video.server;

import com.swingauth.db.Mongo;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchManager {
    private static final MatchManager instance = new MatchManager();
    private final Queue<MatchSocket> waitingQueue = new LinkedList<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, MatchSocket> sockets = new ConcurrentHashMap<>();
    private final Map<String, String> userIdToUsername = new ConcurrentHashMap<>(); // userId -> username
    private final Set<String> failedPairs = new HashSet<>(); // 매칭 실패한 유저 쌍 기록 (무한 루프 방지)
    
    // 매칭을 위한 username 가져오기 (맵에서 먼저 확인)
    private String getUsernameForMatching(MatchSocket socket) {
        if (socket == null) return "unknown";
        String userId = socket.getUserId();
        String username = userIdToUsername.get(userId);
        if (username != null && !username.isEmpty() && !"unknown".equals(username)) {
            return username;
        }
        username = socket.getUsername();
        if (username != null && !username.isEmpty() && !"unknown".equals(username)) {
            userIdToUsername.put(userId, username);
            return username;
        }
        return "unknown";
    }

    public static MatchManager getInstance() {
        return instance;
    }
    
    public void registerUsername(String userId, String username) {
        if (userId != null && username != null) {
            userIdToUsername.put(userId, username);
        }
    }

    public synchronized void enqueue(MatchSocket socket) {
        String userId = socket.getUserId();
        
        // 이미 대기열에 있거나 소켓이 닫혀있으면 무시
        if (sockets.containsKey(userId) || !socket.isOpen()) {
            // 이미 대기열에 있으면 현재 상태만 전송
            if (sockets.containsKey(userId)) {
                int queueSize = waitingQueue.size();
                socket.sendQueueStatus(Math.max(0, queueSize - 1));
            }
            return;
        }
        
        waitingQueue.offer(socket);
        sockets.put(userId, socket);
        int queueSize = waitingQueue.size();
        
        // 모든 대기 중인 클라이언트에게 업데이트된 대기열 상태 전송
        int otherPeopleCount = Math.max(0, queueSize - 1);
        for (MatchSocket s : waitingQueue) {
            if (s.isOpen()) {
                s.sendQueueStatus(otherPeopleCount);
            }
        }
        
        // 대기열에 추가된 후 매칭 시도
        tryMatch();
    }

    public synchronized void dequeue(MatchSocket socket) {
        String userId = socket.getUserId();
        MatchSocket existing = sockets.get(userId);
        if (existing != null) {
            waitingQueue.remove(existing);
            sockets.remove(userId);
            
            // 남은 대기 중인 클라이언트에게 업데이트된 상태 전송
            int otherPeopleCount = Math.max(0, waitingQueue.size() - 1);
            for (MatchSocket s : waitingQueue) {
                if (s.isOpen()) {
                    s.sendQueueStatus(otherPeopleCount);
                }
            }
        }
        // 대기열에서 제거된 후 다른 사용자들과 매칭 시도
        tryMatch();
    }

    public synchronized void tryMatch() {
        // 먼저 연결이 끊어진 소켓들을 대기열에서 제거
        List<MatchSocket> toRemove = new ArrayList<>();
        for (MatchSocket socket : waitingQueue) {
            if (!socket.isOpen() || !sockets.containsKey(socket.getUserId())) {
                toRemove.add(socket);
            }
        }
        waitingQueue.removeAll(toRemove);
        
        // 대기열에 2명 이상이 있으면 매칭 시도
        // 조건: 두 사용자의 평균 평점이 4점 이상이면 매칭
        while (waitingQueue.size() >= 2) {
            List<MatchSocket> queueList = new ArrayList<>(waitingQueue);
            MatchSocket user1 = null;
            MatchSocket user2 = null;
            double bestCombinedAvg = -1;
            
            // 모든 가능한 쌍을 확인하여 매칭 가능한 쌍 중 평균 평점이 가장 높은 쌍을 찾기
            for (int i = 0; i < queueList.size(); i++) {
                for (int j = i + 1; j < queueList.size(); j++) {
                    MatchSocket u1 = queueList.get(i);
                    MatchSocket u2 = queueList.get(j);
                    
                    if (!u1.isOpen() || !u2.isOpen()) continue;
                    
                    String u1Username = getUsernameForMatching(u1);
                    String u2Username = getUsernameForMatching(u2);
                    
                    // 이 두 유저 쌍의 기존 평점 확인 (이전에 만난 적 있는 경우)
                    double pairAvg = getPairAverageRating(u1Username, u2Username);
                    if (pairAvg >= 0 && pairAvg <= 2.0) {
                        // 이전에 만났고, 평균 평점이 2점 이하면 매칭 불가
                        continue;
                    }
                    
                    double u1Rating = getAverageRating(u1Username);
                    double u2Rating = getAverageRating(u2Username);
                    double combinedAvg = (u1Rating + u2Rating) / 2.0;
                    
                    // 매칭 가능한 쌍 중 평균 평점이 가장 높은 쌍 선택
                    if (combinedAvg > bestCombinedAvg) {
                        user1 = u1;
                        user2 = u2;
                        bestCombinedAvg = combinedAvg;
                    }
                }
            }
            
            // 매칭 가능한 쌍을 찾지 못했으면 대기
            if (user1 == null || user2 == null) {
                break;
            }
            
            // 선택된 쌍을 대기열에서 제거
            waitingQueue.remove(user1);
            waitingQueue.remove(user2);
            
            // 유효성 검사
            if (user1 == null || user2 == null) {
                if (user1 != null) waitingQueue.offer(user1);
                if (user2 != null) waitingQueue.offer(user2);
                break;
            }
            
            if (!user1.isOpen() || !user2.isOpen()) {
                if (user1.isOpen() && sockets.containsKey(user1.getUserId())) {
                    waitingQueue.offer(user1);
                }
                if (user2.isOpen() && sockets.containsKey(user2.getUserId())) {
                    waitingQueue.offer(user2);
                }
                continue;
            }
            
            // username 가져오기 (맵에서 먼저 확인, 없으면 MatchSocket의 username 필드 확인)
            String user1Username = userIdToUsername.get(user1.getUserId());
            if (user1Username == null || user1Username.isEmpty() || "unknown".equals(user1Username)) {
                user1Username = user1.getUsername();
                if (user1Username != null && !user1Username.isEmpty() && !"unknown".equals(user1Username)) {
                    // MatchSocket의 username이 있으면 맵에도 저장
                    userIdToUsername.put(user1.getUserId(), user1Username);
                } else {
                    user1Username = "unknown";
                }
            }
            
            String user2Username = userIdToUsername.get(user2.getUserId());
            if (user2Username == null || user2Username.isEmpty() || "unknown".equals(user2Username)) {
                user2Username = user2.getUsername();
                if (user2Username != null && !user2Username.isEmpty() && !"unknown".equals(user2Username)) {
                    // MatchSocket의 username이 있으면 맵에도 저장
                    userIdToUsername.put(user2.getUserId(), user2Username);
                } else {
                    user2Username = "unknown";
                }
            }
            
            // 평점 체크: 두 사용자의 평균 평점 계산
            double user1AvgRating = getAverageRating(user1Username);
            double user2AvgRating = getAverageRating(user2Username);
            double ratingSum = user1AvgRating + user2AvgRating;
            double combinedAvgRating = ratingSum / 2.0;
            
            // 유저 쌍 키 생성 (정렬하여 항상 같은 순서로)
            String pairKey = user1.getUserId().compareTo(user2.getUserId()) < 0 
                ? user1.getUserId() + "_" + user2.getUserId()
                : user2.getUserId() + "_" + user1.getUserId();
            
            // 이미 매칭 실패한 쌍이면 건너뛰기 (무한 루프 방지)
            if (failedPairs.contains(pairKey)) {
                if (user1.isOpen() && sockets.containsKey(user1.getUserId())) {
                    waitingQueue.offer(user1);
                }
                continue;
            }
            
            // 이 두 유저 쌍의 기존 평점이 2점 이하면 매칭하지 않음
            double pairAvgRating = getPairAverageRating(user1Username, user2Username);
            if (pairAvgRating >= 0 && pairAvgRating <= 2.0) {
                failedPairs.add(pairKey);
                if (user1.isOpen() && sockets.containsKey(user1.getUserId())) {
                    waitingQueue.offer(user1);
                }
                if (user2.isOpen() && sockets.containsKey(user2.getUserId())) {
                    waitingQueue.offer(user2);
                }
                continue;
            }
            
            // 매칭 성공 시 실패 기록에서 제거
            failedPairs.remove(pairKey);
            
            // 두 사용자가 모두 유효하고 평점 조건을 만족하면 매칭
            String roomId = UUID.randomUUID().toString();
            
            // 매칭 로그 출력
            System.out.println("[매칭] " + user1Username + "(" + String.format("%.1f", user1AvgRating) + ") ↔ " + 
                             user2Username + "(" + String.format("%.1f", user2AvgRating) + ") [평균: " + String.format("%.1f", combinedAvgRating) + "]");
            
            // 두 유저의 영상통화 횟수 증가
            incrementVideoCallCount(user1Username);
            incrementVideoCallCount(user2Username);
            
            Room room = new Room(roomId, user1, user2);
            rooms.put(roomId, room);

            // partnerUsername을 포함하여 전송
            user1.sendMatched(roomId, user2.getUserId(), user2Username);
            user2.sendMatched(roomId, user1.getUserId(), user1Username);
        }
    }
    
    /**
     * 유저의 영상통화 횟수를 1 증가시킵니다.
     * @param username 유저명
     */
    private void incrementVideoCallCount(String username) {
        try {
            Mongo.users().updateOne(
                Filters.eq("username", username),
                new Document("$inc", new Document("videoCallCount", 1))
            );
        } catch (Exception e) {
            // 로그 생략
        }
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }

    public synchronized void removeSocket(String userId) {
        MatchSocket socket = sockets.remove(userId);
        if (socket != null) {
            waitingQueue.remove(socket);
            
            // 해당 유저와 관련된 failedPairs 기록 삭제 (재연결 시 다시 매칭 가능하도록)
            failedPairs.removeIf(pairKey -> pairKey.contains(userId));
            
            // 남은 대기 중인 클라이언트에게 업데이트된 상태 전송
            int otherPeopleCount = Math.max(0, waitingQueue.size() - 1);
            for (MatchSocket s : waitingQueue) {
                if (s.isOpen()) {
                    s.sendQueueStatus(otherPeopleCount);
                }
            }
            
            // 대기열에서 제거된 후 다른 사용자들과 매칭 시도
            tryMatch();
        }
    }

    /**
     * 두 유저 쌍의 기존 평점 평균을 조회합니다.
     * @param username1 유저1 이름
     * @param username2 유저2 이름
     * @return 해당 쌍의 평균 평점 (기록 없으면 -1 반환)
     */
    private double getPairAverageRating(String username1, String username2) {
        try {
            // username으로 ObjectId 조회
            Document userDoc1 = Mongo.users().find(Filters.eq("username", username1)).first();
            Document userDoc2 = Mongo.users().find(Filters.eq("username", username2)).first();
            
            if (userDoc1 == null || userDoc2 == null) return -1;
            
            Object id1 = userDoc1.get("_id");
            Object id2 = userDoc2.get("_id");
            
            if (!(id1 instanceof org.bson.types.ObjectId) || !(id2 instanceof org.bson.types.ObjectId)) return -1;
            
            org.bson.types.ObjectId objId1 = (org.bson.types.ObjectId) id1;
            org.bson.types.ObjectId objId2 = (org.bson.types.ObjectId) id2;
            
            // user1Id, user2Id 정렬 (일관된 순서로)
            org.bson.types.ObjectId sortedId1, sortedId2;
            if (objId1.compareTo(objId2) < 0) {
                sortedId1 = objId1;
                sortedId2 = objId2;
            } else {
                sortedId1 = objId2;
                sortedId2 = objId1;
            }
            
            // ratings 컬렉션에서 해당 유저 쌍의 문서 조회
            Document ratingDoc = Mongo.ratings().find(
                Filters.and(
                    Filters.eq("user1Id", sortedId1),
                    Filters.eq("user2Id", sortedId2)
                )
            ).first();
            
            if (ratingDoc == null) return -1; // 기록 없음
            
            // averageRating 조회
            Object avgRating = ratingDoc.get("averageRating");
            if (avgRating instanceof Number) {
                return ((Number) avgRating).doubleValue();
            }
            
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 사용자의 평균 평점을 실시간으로 계산합니다.
     * @param username 사용자명
     * @return 평균 평점 (없으면 5.0)
     */
    private double getAverageRating(String username) {
        try {
            Document userDoc = Mongo.users().find(Filters.eq("username", username)).first();
            if (userDoc == null) return 5.0;
            
            Object id = userDoc.get("_id");
            if (!(id instanceof org.bson.types.ObjectId)) return 5.0;
            
            org.bson.types.ObjectId userId = (org.bson.types.ObjectId) id;
            
            // 해당 사용자가 받은 모든 평점을 실시간으로 계산
            double sum = 0.0;
            int count = 0;
            
            for (Document doc : Mongo.ratings().find(
                Filters.or(
                    Filters.eq("user1Id", userId),
                    Filters.eq("user2Id", userId)
                )
            )) {
                org.bson.types.ObjectId docUser1Id = doc.get("user1Id", org.bson.types.ObjectId.class);
                org.bson.types.ObjectId docUser2Id = doc.get("user2Id", org.bson.types.ObjectId.class);
                
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
            
            return count > 0 ? sum / count : 5.0;
        } catch (Exception e) {
            return 5.0;
        }
    }

    public static class Room {
        public final String roomId;
        public final MatchSocket user1;
        public final MatchSocket user2;

        public Room(String roomId, MatchSocket user1, MatchSocket user2) {
            this.roomId = roomId;
            this.user1 = user1;
            this.user2 = user2;
        }
    }
}

