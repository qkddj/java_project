package com.test.video.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import java.io.IOException;
import java.util.UUID;

public class MatchSocket implements WebSocketListener {

    private Session session;
    private String userId;

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {}

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        MatchManager.getInstance().unregister(session);
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.session = session;
        this.userId = UUID.randomUUID().toString();
        MatchManager.getInstance().register(userId, session);
        try {
            session.getRemote().sendString(Json.message("hello").put("userId", userId).toString());
        } catch (IOException ignored) {}
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        try { if (session != null) session.close(StatusCode.SERVER_ERROR, cause.getMessage()); } catch (Throwable ignored) {}
    }

    @Override
    public void onWebSocketText(String message) {
        try {
            ObjectNode msg = Json.parse(message);
            String type = msg.get("type").asText();
            MatchManager mm = MatchManager.getInstance();
            switch (type) {
                case "joinQueue":
                    mm.enqueue(userId);
                    break;
                case "leaveQueue":
                    mm.leaveQueue(userId);
                    break;
                case "rtc.offer":
                case "rtc.answer":
                case "rtc.ice":
                    mm.forward(msg.get("roomId").asText(), userId, type, msg.get("data"));
                    break;
                // chat removed
                case "endCall":
                    mm.endCall(msg.get("roomId").asText(), "manual");
                    break;
                default:
                    // ignore
            }
        } catch (Exception e) {
            onWebSocketError(e);
        }
    }
}


