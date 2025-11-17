package com.swingauth.util;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * 같은 네트워크에서 채팅 서버를 자동으로 찾는 유틸리티
 */
public class NetworkDiscovery {
    private static final int DISCOVERY_PORT = 3002;
    private static final String DISCOVERY_MESSAGE = "CHAT_SERVER_DISCOVERY";
    private static final String RESPONSE_PREFIX = "CHAT_SERVER_IP:";
    
    /**
     * 서버가 시작되면 네트워크에 브로드캐스트로 알림
     */
    public static void startServerBroadcast(String serverIP) {
        Thread broadcastThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                byte[] message = (RESPONSE_PREFIX + serverIP).getBytes(StandardCharsets.UTF_8);
                
                // 브로드캐스트 주소로 전송
                InetAddress broadcast = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(message, message.length, broadcast, DISCOVERY_PORT);
                
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket.send(packet);
                        Thread.sleep(2000); // 2초마다 브로드캐스트
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("서버 브로드캐스트 실패: " + e.getMessage());
            }
        });
        broadcastThread.setDaemon(true);
        broadcastThread.start();
    }
    
    /**
     * 클라이언트가 네트워크에서 서버를 찾음
     * @return 찾은 서버 IP 주소, 없으면 null
     */
    public static String discoverServer(int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs);
            
            // 브로드캐스트 요청 전송
            byte[] request = DISCOVERY_MESSAGE.getBytes(StandardCharsets.UTF_8);
            InetAddress broadcast = InetAddress.getByName("255.255.255.255");
            DatagramPacket requestPacket = new DatagramPacket(request, request.length, broadcast, DISCOVERY_PORT);
            socket.send(requestPacket);
            
            // 응답 대기
            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(responsePacket);
                    String response = new String(responsePacket.getData(), 0, responsePacket.getLength(), StandardCharsets.UTF_8);
                    
                    if (response.startsWith(RESPONSE_PREFIX)) {
                        String serverIP = response.substring(RESPONSE_PREFIX.length()).trim();
                        System.out.println("서버 발견: " + serverIP);
                        return serverIP;
                    }
                } catch (SocketTimeoutException e) {
                    // 타임아웃 - 계속 시도
                }
            }
        } catch (IOException e) {
            System.err.println("서버 발견 실패: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 서버가 브로드캐스트 요청을 받고 응답하는 리스너 시작
     */
    public static void startServerListener(String serverIP) {
        Thread listenerThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                socket.setBroadcast(true);
                byte[] buffer = new byte[1024];
                
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        
                        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        if (DISCOVERY_MESSAGE.equals(message)) {
                            // 서버 IP 응답 전송
                            byte[] response = (RESPONSE_PREFIX + serverIP).getBytes(StandardCharsets.UTF_8);
                            DatagramPacket responsePacket = new DatagramPacket(
                                response, response.length, 
                                packet.getAddress(), packet.getPort()
                            );
                            socket.send(responsePacket);
                            System.out.println("서버 발견 요청에 응답: " + packet.getAddress());
                        }
                    } catch (IOException e) {
                        if (!socket.isClosed()) {
                            System.err.println("서버 리스너 오류: " + e.getMessage());
                        }
                    }
                }
            } catch (SocketException e) {
                System.err.println("서버 리스너 시작 실패: " + e.getMessage());
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    
    /**
     * 로컬 네트워크 IP 주소 감지
     */
    public static String detectLocalIP() {
        try {
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
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("IP 주소 자동 감지 실패: " + e.getMessage());
        }
        return "localhost";
    }
}

