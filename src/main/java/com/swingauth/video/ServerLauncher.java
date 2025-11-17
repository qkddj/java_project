package com.swingauth.video;

import com.swingauth.video.server.MatchWebSocketCreator;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ServerLauncher {
    private static ServerLauncher instance;
    private Server server;
    private int port;
    private int httpsPort;
    private String localIpAddress;

    public static synchronized ServerLauncher getInstance() {
        if (instance == null) {
            instance = new ServerLauncher();
        }
        return instance;
    }
    
    /**
     * 로컬 네트워크 IP 주소를 찾아서 반환
     * Wi-Fi (en0) 또는 이더넷 인터페이스를 우선적으로 선택
     */
    private String findLocalIpAddress() {
        try {
            // macOS에서 Wi-Fi 인터페이스 (en0)를 우선적으로 찾기
            String[] preferredInterfaces = {"en0", "en1", "eth0", "wlan0"};
            
            for (String preferredName : preferredInterfaces) {
                try {
                    NetworkInterface networkInterface = NetworkInterface.getByName(preferredName);
                    if (networkInterface != null && networkInterface.isUp() && !networkInterface.isLoopback()) {
                        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            InetAddress address = addresses.nextElement();
                            if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                                String ip = address.getHostAddress();
                                // 사설 IP 대역만 선택 (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
                                if (ip.startsWith("192.168.") || ip.startsWith("10.") || 
                                    (ip.startsWith("172.") && isPrivate172(ip))) {
                                    return ip;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 해당 인터페이스가 없으면 다음으로
                    continue;
                }
            }
            
            // 우선 인터페이스를 찾지 못하면 모든 인터페이스 검색
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        // 사설 IP 대역만 선택
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || 
                            (ip.startsWith("172.") && isPrivate172(ip))) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("로컬 IP 주소 찾기 실패: " + e.getMessage());
        }
        return "localhost";
    }
    
    /**
     * 172.16.0.0 ~ 172.31.255.255 사설 IP 대역 확인
     */
    private boolean isPrivate172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            }
        } catch (Exception e) {
            // 무시
        }
        return false;
    }
    
    /**
     * 사용 가능한 모든 로컬 IP 주소 목록 반환
     */
    public List<String> getLocalIpAddresses() {
        List<String> ipAddresses = new ArrayList<>();
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
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        ipAddresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("로컬 IP 주소 찾기 실패: " + e.getMessage());
        }
        if (ipAddresses.isEmpty()) {
            ipAddresses.add("localhost");
        }
        return ipAddresses;
    }

    /**
     * 포트가 사용 중인지 확인
     */
    private boolean isPortInUse(int port) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            return false; // 포트가 사용 가능함
        } catch (Exception e) {
            return true; // 포트가 사용 중임
        }
    }

    public void start() throws Exception {
        // 이미 실행 중이면 재시작하지 않음
        if (server != null && server.isStarted()) {
            System.out.println("서버가 이미 실행 중입니다. 포트: " + port);
            return;
        }
        
        int targetPort = 8080;
        
        // 포트가 이미 사용 중이면 서버를 시작하지 않고 포트만 저장
        if (isPortInUse(targetPort)) {
            this.port = targetPort;
            this.localIpAddress = findLocalIpAddress();
            return;
        }
        
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        
        // 모든 네트워크 인터페이스에서 접근 가능하도록 0.0.0.0으로 바인딩
        connector.setHost("0.0.0.0");
        connector.setPort(targetPort);
        
        server.addConnector(connector);
        this.httpsPort = 0; // HTTPS는 ngrok 등을 통해 제공

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // WebSocket
        context.addServlet(new ServletHolder(new MatchWebSocketCreator()), "/ws");
        JettyWebSocketServletContainerInitializer.configure(context, null);

        // 정적 파일 서빙
        URL resourceBase = getClass().getClassLoader().getResource("public");
        if (resourceBase != null) {
            String resourcePath = resourceBase.toURI().toString();
            context.setResourceBase(resourcePath);
        } else {
            // 개발 환경에서 직접 경로 사용
            String publicPath = System.getProperty("user.dir") + "/src/main/resources/public";
            context.setResourceBase(publicPath);
        }
        
        // DefaultServlet 추가 (정적 파일 서빙)
        ServletHolder defaultServlet = new ServletHolder("default", DefaultServlet.class);
        defaultServlet.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServlet, "/");

        server.setHandler(context);
        
        // 서버 시작
        server.start();
        port = connector.getLocalPort();
        localIpAddress = findLocalIpAddress();
        
        System.out.println("Video call server started on port " + port);
        System.out.println("로컬: http://localhost:" + port + "/video-call.html");
        
        List<String> ipAddresses = getLocalIpAddresses();
        if (!ipAddresses.isEmpty() && !ipAddresses.get(0).equals("localhost")) {
            String mainIp = ipAddresses.get(0);
            System.out.println("네트워크: http://" + mainIp + ":" + port + "/video-call.html");
            System.out.println("(다른 디바이스 카메라 사용: ngrok http " + port + " 필요)");
        }
    }
    
    public String getLocalIpAddress() {
        return localIpAddress != null ? localIpAddress : findLocalIpAddress();
    }
    
    public int getHttpsPort() {
        return httpsPort;
    }

    public void stop() throws Exception {
        if (server != null && server.isStarted()) {
            server.stop();
        }
    }

    public int getPort() {
        return port;
    }
}

