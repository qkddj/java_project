package com.swingauth.video.server;

import com.swingauth.video.server.MatchManager.Room;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import java.io.IOException;
import java.util.UUID;

public class MatchSocket implements WebSocketListener {
    private Session session;
    private String userId;
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
            if (message.startsWith("{\"type\":\"joinQueue\"")) {
                manager.enqueue(this);
                manager.tryMatch();
            } else if (message.startsWith("{\"type\":\"leaveQueue\"")) {
                manager.dequeue(this);
            } else if (message.contains("\"type\":\"rtc.")) {
                handleRtcMessage(message);
            } else if (message.contains("\"type\":\"endCall\"")) {
                handleEndCall(message);
            }
        } catch (Exception e) {
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

    public void sendMatched(String roomId, String peerId) {
        sendMessage("{\"type\":\"matched\",\"roomId\":\"" + roomId + "\",\"peerId\":\"" + peerId + "\"}");
    }

    public boolean isOpen() {
        return session != null && session.isOpen();
    }

    public String getUserId() {
        return userId;
    }
}

