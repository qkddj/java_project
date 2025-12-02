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
     * 여러 네트워크 인터페이스가 있는 경우 실제 통신 가능한 IP를 우선 선택
     */
    public static String detectLocalIP() {
        String preferredIP = null;
        java.util.List<String> allIPs = new java.util.ArrayList<>();
        
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
                        String ip = address.getHostAddress();
                        allIPs.add(ip);
                        
                        // 우선순위: 192.168.x.x > 172.16-31.x.x > 10.x.x.x
                        // (일반적으로 192.168.x.x가 가장 일반적인 홈/로컬 네트워크)
                        if (preferredIP == null) {
                            preferredIP = ip; // 일단 첫 번째 IP 저장
                        } else {
                            // 더 우선순위가 높은 IP가 있으면 변경
                            if (ip.startsWith("192.168.") && !preferredIP.startsWith("192.168.")) {
                                preferredIP = ip;
                            } else if (ip.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\..*") && 
                                      !preferredIP.startsWith("192.168.") && 
                                      !preferredIP.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\..*")) {
                                preferredIP = ip;
                            }
                        }
                    }
                }
            }
            
            // 모든 네트워크 인터페이스 IP 출력
            if (!allIPs.isEmpty()) {
                System.out.println("발견된 네트워크 인터페이스 IP 주소:");
                for (String ip : allIPs) {
                    String marker = ip.equals(preferredIP) ? " ← 선택됨" : "";
                    System.out.println("  - " + ip + marker);
                }
            }
            
            // 우선순위 IP가 있으면 반환, 없으면 첫 번째 IP 반환
            return preferredIP != null ? preferredIP : (allIPs.isEmpty() ? "localhost" : allIPs.get(0));
        } catch (Exception e) {
            System.err.println("IP 주소 자동 감지 실패: " + e.getMessage());
        }
        return "localhost";
    }
}

