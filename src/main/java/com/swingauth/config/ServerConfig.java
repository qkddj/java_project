package com.swingauth.config;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class ServerConfig {
    private static final String DEFAULT_SERVER_HOST = "localhost";
    private static final int DEFAULT_SERVER_PORT = 3001;
    
    private static String serverHost = null;
    private static int serverPort = DEFAULT_SERVER_PORT;
    
    static {
        // 환경 변수에서 서버 주소 가져오기
        String envHost = System.getenv("CHAT_SERVER_HOST");
        String envPort = System.getenv("CHAT_SERVER_PORT");
        
        // 시스템 속성에서 서버 주소 가져오기
        String propHost = System.getProperty("chat.server.host");
        String propPort = System.getProperty("chat.server.port");
        
        // 우선순위: 시스템 속성 > 환경 변수 > 자동 감지(네트워크 IP)
        if (propHost != null && !propHost.isEmpty()) {
            serverHost = propHost;
        } else if (envHost != null && !envHost.isEmpty()) {
            serverHost = envHost;
        } else {
            // 기본값: 로컬 IP 사용 (서버가 시작되면 Main에서 자동으로 설정됨)
            serverHost = detectLocalIP();
        }
        
        if (propPort != null && !propPort.isEmpty()) {
            try {
                serverPort = Integer.parseInt(propPort);
            } catch (NumberFormatException e) {
                // 기본값 사용
            }
        } else if (envPort != null && !envPort.isEmpty()) {
            try {
                serverPort = Integer.parseInt(envPort);
            } catch (NumberFormatException e) {
                // 기본값 사용
            }
        }
    }
    
    /**
     * 로컬 네트워크 IP 주소를 자동으로 감지
     * localhost가 아닌 실제 네트워크 인터페이스의 IP를 반환
     */
    private static String detectLocalIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                // 루프백 인터페이스는 제외
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // IPv4 주소만 사용 (IPv6 제외)
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') == -1) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("IP 주소 자동 감지 실패: " + e.getMessage());
        }
        
        // 감지 실패 시 기본값 반환
        return DEFAULT_SERVER_HOST;
    }
    
    /**
     * 서버 호스트 주소 반환
     */
    public static String getServerHost() {
        return serverHost;
    }
    
    /**
     * 서버 포트 번호 반환
     */
    public static int getServerPort() {
        return serverPort;
    }
    
    /**
     * 서버 URL 반환 (http://host:port 형식)
     */
    public static String getServerURL() {
        return "http://" + serverHost + ":" + serverPort;
    }
    
    /**
     * 서버 주소를 수동으로 설정 (테스트용)
     */
    public static void setServerHost(String host) {
        serverHost = host;
    }
    
    /**
     * 서버 포트를 수동으로 설정 (테스트용)
     */
    public static void setServerPort(int port) {
        serverPort = port;
    }
}

