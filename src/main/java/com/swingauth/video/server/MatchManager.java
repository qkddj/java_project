package com.swingauth.video.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchManager {
    private static final MatchManager instance = new MatchManager();
    private final Queue<MatchSocket> waitingQueue = new LinkedList<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, MatchSocket> sockets = new ConcurrentHashMap<>();

    public static MatchManager getInstance() {
        return instance;
    }

    public synchronized void enqueue(MatchSocket socket) {
        if (waitingQueue.contains(socket)) return;
        waitingQueue.offer(socket);
        sockets.put(socket.getUserId(), socket);
        socket.sendQueueStatus(waitingQueue.size());
    }

    public synchronized void dequeue(MatchSocket socket) {
        waitingQueue.remove(socket);
        sockets.remove(socket.getUserId());
    }

    public synchronized void tryMatch() {
        while (waitingQueue.size() >= 2) {
            MatchSocket user1 = waitingQueue.poll();
            MatchSocket user2 = waitingQueue.poll();
            if (user1 == null || user2 == null || !user1.isOpen() || !user2.isOpen()) {
                if (user1 != null && user1.isOpen()) waitingQueue.offer(user1);
                if (user2 != null && user2.isOpen()) waitingQueue.offer(user2);
                continue;
            }

            String roomId = UUID.randomUUID().toString();
            Room room = new Room(roomId, user1, user2);
            rooms.put(roomId, room);

            user1.sendMatched(roomId, user2.getUserId());
            user2.sendMatched(roomId, user1.getUserId());
        }
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }

    public void removeSocket(String userId) {
        MatchSocket socket = sockets.remove(userId);
        if (socket != null) {
            waitingQueue.remove(socket);
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

