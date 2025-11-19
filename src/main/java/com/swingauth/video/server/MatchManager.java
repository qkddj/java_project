package com.swingauth.video.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchManager {
    private static final MatchManager instance = new MatchManager();
    private final Queue<MatchSocket> waitingQueue = new LinkedList<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, MatchSocket> sockets = new ConcurrentHashMap<>();
    private final Map<String, String> userIdToUsername = new ConcurrentHashMap<>(); // userId -> username

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
        while (waitingQueue.size() >= 2) {
            MatchSocket user1 = waitingQueue.poll();
            MatchSocket user2 = waitingQueue.poll();
            
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
            
            // 두 사용자가 모두 유효하면 매칭
            String roomId = UUID.randomUUID().toString();
            
            // username 가져오기
            String user1Username = userIdToUsername.getOrDefault(user1.getUserId(), "unknown");
            String user2Username = userIdToUsername.getOrDefault(user2.getUserId(), "unknown");
            
            System.out.println("매칭 완료: " + user1Username + " <-> " + user2Username);
            
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

