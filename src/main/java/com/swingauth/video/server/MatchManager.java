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
        
        int queueSize = waitingQueue.size();
        if (queueSize >= 2) {
            System.out.println("매칭 시도: 대기열 크기=" + queueSize);
        }
        
        // 대기열에 2명 이상이 있으면 매칭 시도
        // 우선순위: 평점 합이 4점 이상인 쌍을 먼저 매칭
        while (waitingQueue.size() >= 2) {
            List<MatchSocket> queueList = new ArrayList<>(waitingQueue);
            MatchSocket user1 = null;
            MatchSocket user2 = null;
            double bestRatingSum = -1;
            
            // 모든 가능한 쌍을 확인하여 평점 합이 4점 이상인 쌍을 우선 찾기
            for (int i = 0; i < queueList.size(); i++) {
                for (int j = i + 1; j < queueList.size(); j++) {
                    MatchSocket u1 = queueList.get(i);
                    MatchSocket u2 = queueList.get(j);
                    
                    if (!u1.isOpen() || !u2.isOpen()) continue;
                    
                    String u1Username = getUsernameForMatching(u1);
                    String u2Username = getUsernameForMatching(u2);
                    double u1Rating = getAverageRating(u1Username);
                    double u2Rating = getAverageRating(u2Username);
                    double ratingSum = u1Rating + u2Rating;
                    
                    // 평점 합이 4점 이상이고, 현재까지 찾은 것보다 높으면 선택
                    if (ratingSum >= 4.0 && ratingSum > bestRatingSum) {
                        user1 = u1;
                        user2 = u2;
                        bestRatingSum = ratingSum;
                    }
                }
            }
            
            // 우선순위 쌍을 찾지 못했으면 일반 순서로 매칭
            if (user1 == null || user2 == null) {
                user1 = waitingQueue.poll();
                user2 = waitingQueue.poll();
            } else {
                // 우선순위 쌍을 대기열에서 제거
                waitingQueue.remove(user1);
                waitingQueue.remove(user2);
            }
            
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
            double ratingSum = user1AvgRating + user2AvgRating; // 두 사람의 평점 합
            double combinedAvgRating = ratingSum / 2.0; // 평균
            
            System.out.println("매칭 체크: " + user1Username + " (평균: " + user1AvgRating + ") <-> " + 
                             user2Username + " (평균: " + user2AvgRating + ")");
            System.out.println("  평점 합: " + ratingSum + ", 평균: " + combinedAvgRating);
            
            // 유저 쌍 키 생성 (정렬하여 항상 같은 순서로)
            String pairKey = user1.getUserId().compareTo(user2.getUserId()) < 0 
                ? user1.getUserId() + "_" + user2.getUserId()
                : user2.getUserId() + "_" + user1.getUserId();
            
            // 이미 매칭 실패한 쌍이면 건너뛰기 (무한 루프 방지)
            if (failedPairs.contains(pairKey)) {
                System.out.println("  매칭 건너뜀: 이미 실패한 쌍 (무한 루프 방지)");
                // 한쪽만 대기열에 추가 (다른 유저와 매칭 기회 제공)
                if (user1.isOpen() && sockets.containsKey(user1.getUserId())) {
                    waitingQueue.offer(user1);
                }
                continue;
            }
            
            // 개별 평균 평점이 2점 미만이면 매칭하지 않음
            if (user1AvgRating < 2.0) {
                System.out.println("  매칭 실패: " + user1Username + "의 평균 평점이 2점 미만 (" + user1AvgRating + ")");
                failedPairs.add(pairKey);
                // 한쪽만 대기열에 추가 (다른 유저와 매칭 기회 제공)
                if (user2.isOpen() && sockets.containsKey(user2.getUserId())) {
                    waitingQueue.offer(user2);
                }
                continue;
            }
            
            if (user2AvgRating < 2.0) {
                System.out.println("  매칭 실패: " + user2Username + "의 평균 평점이 2점 미만 (" + user2AvgRating + ")");
                failedPairs.add(pairKey);
                // 한쪽만 대기열에 추가 (다른 유저와 매칭 기회 제공)
                if (user1.isOpen() && sockets.containsKey(user1.getUserId())) {
                    waitingQueue.offer(user1);
                }
                continue;
            }
            
            // 두 사람의 평점 합이 2점 미만이면 매칭하지 않음
            if (ratingSum < 2.0) {
                System.out.println("  매칭 실패: 두 사람의 평점 합이 2점 미만 (" + ratingSum + ")");
                failedPairs.add(pairKey);
                // 한쪽만 대기열에 추가 (다른 유저와 매칭 기회 제공)
                if (user1.isOpen() && sockets.containsKey(user1.getUserId())) {
                    waitingQueue.offer(user1);
                }
                continue;
            }
            
            // 평점 합이 4점 이상이면 우선순위 매칭 (이미 우선순위로 선택되었으므로 바로 매칭 진행)
            if (ratingSum >= 4.0) {
                System.out.println("  ✅ 우선순위 매칭: 평점 합이 4점 이상 (" + ratingSum + ")");
            }
            
            // 매칭 성공 시 실패 기록에서 제거
            failedPairs.remove(pairKey);
            
            // 두 사용자가 모두 유효하고 평점 조건을 만족하면 매칭
            String roomId = UUID.randomUUID().toString();
            
            System.out.println("매칭 완료: " + user1Username + " <-> " + user2Username);
            System.out.println("  user1 userId: " + user1.getUserId() + ", username: " + user1Username);
            System.out.println("  user2 userId: " + user2.getUserId() + ", username: " + user2Username);
            
            Room room = new Room(roomId, user1, user2);
            rooms.put(roomId, room);

            // partnerUsername을 포함하여 전송
            user1.sendMatched(roomId, user2.getUserId(), user2Username);
            user2.sendMatched(roomId, user1.getUserId(), user1Username);
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
     * 사용자의 평균 평점을 ratings 컬렉션에서 조회합니다.
     * serviceType이 "average"인 문서에서 값을 가져옵니다.
     * 
     * @param username 사용자명
     * @return 평균 평점 (평점이 없으면 5.0 반환, 즉 신규 사용자는 기본적으로 매칭 가능)
     */
    private double getAverageRating(String username) {
        try {
            // username으로부터 ObjectId 조회
            Document userDoc = Mongo.users().find(Filters.eq("username", username)).first();
            if (userDoc == null) {
                System.out.println("[평점 조회] 사용자를 찾을 수 없음: " + username + " -> 기본값 5.0");
                return 5.0; // 사용자를 찾을 수 없으면 기본값 5.0 (신규 사용자)
            }
            
            Object id = userDoc.get("_id");
            if (!(id instanceof org.bson.types.ObjectId)) {
                System.out.println("[평점 조회] 사용자 ID가 유효하지 않음: " + username + " -> 기본값 5.0");
                return 5.0;
            }
            
            org.bson.types.ObjectId userId = (org.bson.types.ObjectId) id;
            
            // ratings 컬렉션에서 평균 평점 조회 (serviceType이 "average"인 문서)
            Document avgDoc = Mongo.ratings().find(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("serviceType", "average")
                )
            ).first();
            
            if (avgDoc == null) {
                // 평균 평점 문서가 없으면 기본값 5.0 (신규 사용자 또는 아직 평점이 없는 경우)
                System.out.println("[평점 조회] 평균 평점이 없음: " + username + " -> 기본값 5.0");
                return 5.0;
            }
            
            Object ar = avgDoc.get("averageRating");
            double avgRating;
            
            if (ar == null) {
                System.out.println("[평점 조회] 평균 평점 값이 null: " + username + " -> 기본값 5.0");
                avgRating = 5.0;
            } else if (ar instanceof Double) {
                avgRating = (Double) ar;
            } else if (ar instanceof Number) {
                avgRating = ((Number) ar).doubleValue();
            } else {
                System.out.println("[평점 조회] 평균 평점 타입이 유효하지 않음: " + username + " -> 기본값 5.0");
                avgRating = 5.0;
            }
            
            System.out.println("[평점 조회] " + username + ": 평균 " + avgRating + " (ratings 컬렉션에서 조회)");
            return avgRating;
            
        } catch (Exception e) {
            System.err.println("[평점 조회] 오류 발생: " + username + " - " + e.getMessage());
            e.printStackTrace();
            return 5.0; // 오류 발생 시 기본값 5.0
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

