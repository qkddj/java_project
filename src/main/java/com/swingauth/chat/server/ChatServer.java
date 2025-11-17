package com.swingauth.chat.server;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatServer {
    private static ChatServer instance;
    private SocketIOServer server;
    private final Queue<SocketIOClient> matchQueue = new LinkedList<>();
    private final Map<String, String> matchedPairs = new HashMap<>(); // clientId -> matchedClientId
    private final Map<String, SocketIOClient> clients = new HashMap<>();
    private int port = 3001;
    private boolean isRunning = false;

    private ChatServer() {}

    public static synchronized ChatServer getInstance() {
        if (instance == null) {
            instance = new ChatServer();
        }
        return instance;
    }

    public boolean isRunning() {
        return isRunning && server != null;
    }

    public void start() {
        // 이미 실행 중이면 재시작하지 않음
        if (isRunning && server != null) {
            System.out.println("채팅 서버가 이미 실행 중입니다.");
            return;
        }
        Configuration config = new Configuration();
        // 0.0.0.0으로 설정하여 모든 네트워크 인터페이스에서 접근 가능하도록 함
        config.setHostname("0.0.0.0");
        config.setPort(port);
        
        // CORS 설정
        config.setOrigin("*");
        
        // Socket.io 호환성 설정
        config.setAllowCustomRequests(true);
        config.setUpgradeTimeout(10000);
        config.setPingTimeout(60000);
        config.setPingInterval(25000);

        server = new SocketIOServer(config);

        // 연결 이벤트
        server.addConnectListener(new ConnectListener() {
            @Override
            public void onConnect(SocketIOClient client) {
                System.out.println("클라이언트 연결: " + client.getSessionId());
                clients.put(client.getSessionId().toString(), client);
            }
        });

        // 연결 해제 이벤트
        server.addDisconnectListener(new DisconnectListener() {
            @Override
            public void onDisconnect(SocketIOClient client) {
                System.out.println("클라이언트 연결 해제: " + client.getSessionId());
                endMatching(client);
                clients.remove(client.getSessionId().toString());
            }
        });

        // 매칭 시작
        server.addEventListener("startMatching", Object.class, (client, data, ackSender) -> {
            String clientId = client.getSessionId().toString();
            System.out.println("startMatching 이벤트 수신: " + clientId + ", 데이터: " + data);
            if (!matchQueue.contains(client) && !matchedPairs.containsKey(clientId)) {
                matchQueue.offer(client);
                client.sendEvent("matchingStarted");
                System.out.println("매칭 시작: " + clientId + ", 대기열 크기: " + matchQueue.size());
                tryMatch();
            } else {
                System.out.println("이미 대기열에 있거나 매칭 중: " + clientId);
            }
        });

        // 매칭 종료
        server.addEventListener("endMatching", Object.class, (client, data, ackSender) -> {
            endMatching(client);
        });

        // 메시지 전송
        server.addEventListener("sendMessage", String.class, (client, data, ackSender) -> {
            String clientId = client.getSessionId().toString();
            System.out.println("메시지 수신: " + clientId + ", 내용: " + data);
            String matchedClientId = matchedPairs.get(clientId);
            System.out.println("매칭된 상대 ID: " + matchedClientId);
            if (matchedClientId != null) {
                SocketIOClient matchedClient = clients.get(matchedClientId);
                if (matchedClient != null && matchedClient.isChannelOpen()) {
                    JSONObject messageData = new JSONObject();
                    messageData.put("text", data);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    messageData.put("timestamp", sdf.format(new Date()));
                    matchedClient.sendEvent("receiveMessage", messageData.toString());
                    System.out.println("메시지 전송 완료: " + clientId + " -> " + matchedClientId);
                } else {
                    System.out.println("매칭된 상대 클라이언트가 없거나 연결이 끊어짐: " + matchedClientId);
                }
            } else {
                System.out.println("매칭된 상대가 없음. 클라이언트 ID: " + clientId + ", 매칭 맵: " + matchedPairs);
            }
        });

        try {
            server.start();
            isRunning = true;
            System.out.println("채팅 서버가 포트 " + port + "에서 시작되었습니다.");
            System.out.println("서버 주소: http://0.0.0.0:" + port);
            System.out.println("다른 컴퓨터에서 접속하려면 서버 컴퓨터의 IP 주소를 사용하세요.");
            printLocalIPAddresses();
        } catch (Exception e) {
            isRunning = false;
            System.err.println("채팅 서버 시작 실패: " + e.getMessage());
            if (e.getCause() instanceof java.net.BindException) {
                System.err.println("포트 " + port + "가 이미 사용 중입니다. 기존 서버가 실행 중일 수 있습니다.");
            }
            throw e;
        }
    }

    private synchronized void tryMatch() {
        while (matchQueue.size() >= 2) {
            SocketIOClient user1 = matchQueue.poll();
            SocketIOClient user2 = matchQueue.poll();

            if (user1 == null || user2 == null) {
                if (user1 != null) matchQueue.offer(user1);
                if (user2 != null) matchQueue.offer(user2);
                break;
            }

            if (!user1.isChannelOpen() || !user2.isChannelOpen()) {
                if (user1.isChannelOpen()) matchQueue.offer(user1);
                if (user2.isChannelOpen()) matchQueue.offer(user2);
                continue;
            }

            String user1Id = user1.getSessionId().toString();
            String user2Id = user2.getSessionId().toString();

            matchedPairs.put(user1Id, user2Id);
            matchedPairs.put(user2Id, user1Id);

            user1.sendEvent("matched", new JSONObject().put("partnerId", user2Id).toString());
            user2.sendEvent("matched", new JSONObject().put("partnerId", user1Id).toString());

            System.out.println("매칭 완료: " + user1Id + " <-> " + user2Id);
        }
    }

    private synchronized void endMatching(SocketIOClient client) {
        String clientId = client.getSessionId().toString();
        
        // 대기열에서 제거
        matchQueue.remove(client);
        client.sendEvent("matchingEnded");

        // 매칭된 상대에게 알림
        String matchedClientId = matchedPairs.remove(clientId);
        if (matchedClientId != null) {
            matchedPairs.remove(matchedClientId);
            SocketIOClient matchedClient = clients.get(matchedClientId);
            if (matchedClient != null) {
                matchedClient.sendEvent("partnerDisconnected");
            }
            System.out.println("매칭 종료: " + clientId + " <-> " + matchedClientId);
        }
    }

    public void stop() {
        if (server != null && isRunning) {
            try {
                server.stop();
                isRunning = false;
                System.out.println("채팅 서버가 중지되었습니다.");
            } catch (Exception e) {
                System.err.println("서버 중지 중 오류: " + e.getMessage());
            }
        }
    }

    public int getPort() {
        return port;
    }
    
    /**
     * 로컬 네트워크 IP 주소들을 출력
     */
    private void printLocalIPAddresses() {
        try {
            System.out.println("사용 가능한 네트워크 주소:");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') == -1) {
                        System.out.println("  - http://" + address.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("IP 주소 출력 실패: " + e.getMessage());
        }
    }
}

