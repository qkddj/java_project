package com.test.video.server;

import org.eclipse.jetty.websocket.api.Session;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 간단한 인메모리 매칭 매니저(FIFO).
 */
public class MatchManager {

    private static final MatchManager INSTANCE = new MatchManager();

    public static MatchManager getInstance() { return INSTANCE; }

    private final Queue<String> waitingQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Session> userIdToSession = new ConcurrentHashMap<>();
    private final Map<Session, String> sessionToUserId = new ConcurrentHashMap<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public void register(String userId, Session session) {
        userIdToSession.put(userId, session);
        sessionToUserId.put(session, userId);
    }

    public void unregister(Session session) {
        String userId = sessionToUserId.remove(session);
        if (userId != null) {
            userIdToSession.remove(userId);
            waitingQueue.remove(userId);
            // 방에서 나가면 상대에게 종료 통지
            rooms.values().stream()
                .filter(r -> r.hasUser(userId))
                .findFirst()
                .ifPresent(room -> endCall(room.roomId, "left"));
        }
    }

    public void enqueue(String userId) throws IOException {
        if (waitingQueue.contains(userId)) return;
        waitingQueue.add(userId);
        // 본인 포함 총 대기 인원(queueSize)도 함께 전송
        send(userId, Json.message("enqueued")
                .put("position", waitingQueue.size())
                .put("queueSize", waitingQueue.size()));
        broadcastQueuePositions();
        tryMatch();
    }

    public void leaveQueue(String userId) {
        waitingQueue.remove(userId);
        Session s = userIdToSession.get(userId);
        if (s != null && s.isOpen()) {
            try { s.getRemote().sendString(Json.message("dequeued").toString()); } catch (IOException ignored) {}
        }
        try { broadcastQueuePositions(); } catch (IOException ignored) {}
    }

    private void tryMatch() throws IOException {
        while (waitingQueue.size() >= 2) {
            String a = waitingQueue.poll();
            String b = waitingQueue.poll();
            if (a == null || b == null) return;
            String roomId = UUID.randomUUID().toString();
            Room room = new Room(roomId, a, b);
            rooms.put(roomId, room);
            send(a, Json.message("matched").put("roomId", roomId).put("peerId", b));
            send(b, Json.message("matched").put("roomId", roomId).put("peerId", a));
        }
        broadcastQueuePositions();
    }

    public void forward(String roomId, String fromUserId, String type, com.fasterxml.jackson.databind.JsonNode payload) throws IOException {
        Room room = rooms.get(roomId);
        if (room == null) return;
        String target = room.otherOf(fromUserId);
        if (target == null) return;
        com.fasterxml.jackson.databind.node.ObjectNode msg = Json.message(type)
            .put("roomId", roomId)
            .put("from", fromUserId);
        msg.set("data", payload);
        send(target, msg);
    }

    public void endCall(String roomId, String reason) {
        Room room = rooms.remove(roomId);
        if (room == null) return;
        try { send(room.userA, Json.message("callEnded").put("roomId", roomId).put("reason", reason)); } catch (IOException ignored) {}
        try { send(room.userB, Json.message("callEnded").put("roomId", roomId).put("reason", reason)); } catch (IOException ignored) {}
    }

    public String userIdOf(Session session) { return sessionToUserId.get(session); }

    private void send(String userId, com.fasterxml.jackson.databind.node.ObjectNode json) throws IOException {
        Session s = userIdToSession.get(userId);
        if (s != null && s.isOpen()) s.getRemote().sendString(json.toString());
    }

    private void broadcastQueuePositions() throws IOException {
        int idx = 0;
        for (String uid : waitingQueue) {
            idx++;
            send(uid, Json.message("queueUpdate")
                    .put("position", idx)
                    .put("ahead", idx - 1)
                    .put("queueSize", waitingQueue.size()));
        }
    }

    private static class Room {
        final String roomId;
        final String userA;
        final String userB;
        Room(String roomId, String userA, String userB) { this.roomId = roomId; this.userA = userA; this.userB = userB; }
        boolean hasUser(String u) { return userA.equals(u) || userB.equals(u); }
        String otherOf(String u) { return userA.equals(u) ? userB : (userB.equals(u) ? userA : null); }
    }
}


