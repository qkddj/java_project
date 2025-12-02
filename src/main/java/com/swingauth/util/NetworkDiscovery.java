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
     * 라우터(기본 게이트웨이)와 같은 서브넷에 있는 IP를 우선적으로 선택
     */
    public static String detectLocalIP() {
        java.util.List<IPInfo> allIPs = new java.util.ArrayList<>();
        String defaultGateway = getDefaultGateway();
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                // 루프백 또는 비활성 인터페이스 제외
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                // 가상 인터페이스 제외 (VPN 등)
                String name = networkInterface.getName().toLowerCase();
                if (name.contains("tun") || name.contains("tap") || 
                    name.contains("vpn") || name.contains("ppp") ||
                    name.contains("utun") || name.contains("vmnet") ||
                    name.contains("vboxnet") || name.contains("virbr")) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // IPv4 주소만 선택 (IPv6 제외)
                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {
                        String ip = address.getHostAddress();
                        boolean isVirtual = isVirtualInterface(networkInterface);
                        boolean sameSubnetAsGateway = defaultGateway != null && isSameSubnet(ip, defaultGateway);
                        int priority = getIPPriority(ip, sameSubnetAsGateway);
                        allIPs.add(new IPInfo(ip, name, isVirtual, priority, sameSubnetAsGateway));
                    }
                }
            }
            
            // 우선순위에 따라 정렬
            allIPs.sort((a, b) -> {
                // 라우터와 같은 서브넷인 IP를 최우선
                if (a.sameSubnetAsGateway != b.sameSubnetAsGateway) {
                    return a.sameSubnetAsGateway ? -1 : 1;
                }
                // 가상 인터페이스는 뒤로
                if (a.isVirtual != b.isVirtual) {
                    return a.isVirtual ? 1 : -1;
                }
                // 우선순위 높은 순서
                return Integer.compare(b.priority, a.priority);
            });
            
            // 모든 네트워크 인터페이스 IP 출력
            if (!allIPs.isEmpty()) {
                System.out.println("발견된 네트워크 인터페이스 IPv4 주소:");
                if (defaultGateway != null) {
                    System.out.println("  기본 게이트웨이(라우터): " + defaultGateway);
                }
                for (int i = 0; i < allIPs.size(); i++) {
                    IPInfo info = allIPs.get(i);
                    String marker = (i == 0) ? " ← 선택됨 (최우선)" : "";
                    String type = info.isVirtual ? " [가상]" : " [물리]";
                    String subnet = info.sameSubnetAsGateway ? " [라우터와 같은 서브넷]" : "";
                    System.out.println("  - " + info.ip + " (" + info.interfaceName + ")" + type + subnet + marker);
                }
            }
            
            // 최우선 IP 반환
            return allIPs.isEmpty() ? "localhost" : allIPs.get(0).ip;
        } catch (Exception e) {
            System.err.println("IP 주소 자동 감지 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return "localhost";
    }
    
    /**
     * 기본 게이트웨이(라우터) 주소를 가져옴
     */
    private static String getDefaultGateway() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("win")) {
                // Windows: ipconfig를 통해 기본 게이트웨이 찾기
                Process process = Runtime.getRuntime().exec("ipconfig");
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), "MS949"));
                String line;
                boolean inAdapter = false;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.contains("어댑터") || line.contains("Adapter")) {
                        inAdapter = true;
                    } else if (line.contains("기본 게이트웨이") || line.contains("Default Gateway")) {
                        String[] parts = line.split("[:\\s]+");
                        for (String part : parts) {
                            if (part.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                return part;
                            }
                        }
                    } else if (line.isEmpty() && inAdapter) {
                        inAdapter = false;
                    }
                }
            } else if (os.contains("mac")) {
                // macOS: route get default 사용
                Process process = Runtime.getRuntime().exec("route -n get default");
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("gateway:")) {
                        String[] parts = line.split("[:\\s]+");
                        for (String part : parts) {
                            if (part.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                return part;
                            }
                        }
                    }
                }
            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux: ip route 사용
                Process process = Runtime.getRuntime().exec("ip route | grep default");
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\s+");
                    for (String part : parts) {
                        if (part.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") && 
                            !part.startsWith("0.0.0.0") && 
                            !part.equals("255.255.255.0")) {
                            return part;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("기본 게이트웨이 검색 실패: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 두 IP 주소가 같은 서브넷에 있는지 확인
     */
    private static boolean isSameSubnet(String ip1, String ip2) {
        try {
            String[] parts1 = ip1.split("\\.");
            String[] parts2 = ip2.split("\\.");
            
            if (parts1.length != 4 || parts2.length != 4) {
                return false;
            }
            
            // 일반적인 서브넷 마스크 가정 (192.168.x.x는 /24, 10.x.x.x는 /8 등)
            int prefixLength;
            if (ip1.startsWith("192.168.") || ip1.startsWith("172.")) {
                prefixLength = 24; // /24 (255.255.255.0)
            } else if (ip1.startsWith("10.")) {
                prefixLength = 8;  // /8 (255.0.0.0)
            } else {
                prefixLength = 24; // 기본값
            }
            
            // 서브넷 마스크에 따라 비교
            int bytesToCheck = prefixLength / 8;
            for (int i = 0; i < bytesToCheck; i++) {
                if (!parts1[i].equals(parts2[i])) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 가상 인터페이스인지 확인
     */
    private static boolean isVirtualInterface(NetworkInterface networkInterface) {
        try {
            String name = networkInterface.getName().toLowerCase();
            // macOS의 utun, Windows의 가상 어댑터 등
            return name.contains("utun") || 
                   name.contains("vmnet") || 
                   name.contains("vboxnet") ||
                   name.contains("virbr") ||
                   networkInterface.isVirtual();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * IP 주소의 우선순위 반환 (높을수록 우선)
     * 라우터와 같은 서브넷이면 추가 보너스 점수
     */
    private static int getIPPriority(String ip, boolean sameSubnetAsGateway) {
        int basePriority;
        if (ip.startsWith("192.168.")) {
            basePriority = 100; // 가장 일반적인 로컬 네트워크
        } else if (ip.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\..*")) {
            basePriority = 80;  // 사설 IP 대역
        } else if (ip.startsWith("10.")) {
            basePriority = 60;  // 사설 IP 대역 (보통 VPN이나 회사 네트워크)
        } else if (ip.startsWith("169.254.")) {
            basePriority = 20;  // APIPA (자동 할당, 우선순위 낮음)
        } else {
            basePriority = 40;  // 기타 공인 IP
        }
        
        // 라우터와 같은 서브넷이면 보너스 점수 추가
        if (sameSubnetAsGateway) {
            basePriority += 200; // 매우 높은 우선순위
        }
        
        return basePriority;
    }
    
    /**
     * IP 정보를 저장하는 내부 클래스
     */
    private static class IPInfo {
        String ip;
        String interfaceName;
        boolean isVirtual;
        int priority;
        boolean sameSubnetAsGateway;
        
        IPInfo(String ip, String interfaceName, boolean isVirtual, int priority, boolean sameSubnetAsGateway) {
            this.ip = ip;
            this.interfaceName = interfaceName;
            this.isVirtual = isVirtual;
            this.priority = priority;
            this.sameSubnetAsGateway = sameSubnetAsGateway;
        }
    }
}

