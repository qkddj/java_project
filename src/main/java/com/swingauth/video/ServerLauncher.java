package com.swingauth.video;

import com.swingauth.video.server.MatchWebSocketCreator;
import com.swingauth.util.NetworkDiscovery;
import com.swingauth.util.NgrokUtil;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ServerLauncher {
    private static ServerLauncher instance;
    private Server server;
    private int port = 0; // 명시적으로 0으로 초기화
    private int httpsPort;
    private String localIpAddress;
    private ServerConnector connector; // 포트 정보를 가져오기 위해 저장
    private static final String PORT_FILE_PATH = System.getProperty("user.home") + "/.video-call-server-port";
    // 고정 포트는 제거 - 파일 기반 포트 공유 방식 사용
    private Process ngrokProcess; // ngrok 프로세스 추적

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
     * 저장된 포트 번호를 파일에서 읽기
     * @return 저장된 포트 번호, 없으면 0
     */
    private int readPortFromFile() {
        try {
            Path portFile = Paths.get(PORT_FILE_PATH);
            if (Files.exists(portFile)) {
                String portStr = Files.readAllLines(portFile).get(0).trim();
                int savedPort = Integer.parseInt(portStr);
                System.out.println("파일에서 포트 읽음: " + savedPort);
                return savedPort;
            }
        } catch (Exception e) {
            System.err.println("포트 파일 읽기 실패: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * 포트 번호를 파일에 저장
     */
    private void savePortToFile(int port) {
        try {
            Path portFile = Paths.get(PORT_FILE_PATH);
            Files.write(portFile, String.valueOf(port).getBytes());
            System.out.println("포트 번호를 파일에 저장: " + port + " (" + PORT_FILE_PATH + ")");
        } catch (Exception e) {
            System.err.println("포트 파일 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 특정 포트가 사용 가능한지 확인 (새로 바인딩할 수 있는지)
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true; // 바인딩 성공 = 사용 가능
        } catch (Exception e) {
            return false; // 바인딩 실패 = 사용 중
        }
    }
    
    /**
     * 특정 포트에서 서버가 실행 중인지 HTTP로 확인
     */
    private boolean isServerRunningOnPort(int port) {
        try {
            java.net.URL url = new java.net.URL("http://localhost:" + port + "/video-call.html");
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1000); // 1초 타임아웃
            connection.setReadTimeout(1000);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            // 200-399 범위면 서버가 실행 중
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            return false; // 연결 실패 = 서버 없음
        }
    }
    
    /**
     * 새로 사용 가능한 포트를 찾아서 반환 (파일 체크 없이)
     * 실제로 바인딩 가능한 포트를 찾기 위해 여러 번 시도
     * @return 새로 찾은 포트 번호
     */
    private int findNewAvailablePort() {
        // 먼저 저장된 포트를 확인 (서버가 실행 중이 아닐 수 있으므로)
        int savedPort = readPortFromFile();
        if (savedPort > 0 && isPortAvailable(savedPort)) {
            System.out.println("저장된 포트(" + savedPort + ")가 사용 가능합니다.");
            return savedPort;
        }
        
        // 랜덤 포트 찾기 시도 (최대 10회)
        for (int attempt = 0; attempt < 10; attempt++) {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                // 포트 0을 사용하면 자동으로 사용 가능한 포트를 할당받음
                int newPort = serverSocket.getLocalPort();
                // 포트를 닫은 후 실제로 사용 가능한지 다시 확인
                serverSocket.close();
                Thread.sleep(50); // 포트 해제 대기
                
                if (isPortAvailable(newPort)) {
                    System.out.println("새 포트 발견: " + newPort + " (시도: " + (attempt + 1) + ")");
                    savePortToFile(newPort);
                    return newPort;
                }
            } catch (Exception e) {
                // 계속 시도
                continue;
            }
        }
        
        // 랜덤 포트 찾기 실패 시 기본 포트 범위에서 찾기 시도
        System.out.println("랜덤 포트 찾기 실패, 범위 검색 시작...");
        for (int port = 8080; port <= 65535; port++) {
            if (isPortAvailable(port)) {
                System.out.println("범위 검색으로 포트 발견: " + port);
                savePortToFile(port);
                return port;
            }
        }
        
        throw new RuntimeException("사용 가능한 포트를 찾을 수 없습니다.");
    }
    
    /**
     * 사용 가능한 포트를 자동으로 찾아서 반환
     * 저장된 포트가 있으면 우선 사용
     * @return 사용 가능한 포트 번호
     */
    private int findAvailablePort() {
        // 먼저 저장된 포트를 확인
        int savedPort = readPortFromFile();
        if (savedPort > 0) {
            // 저장된 포트가 사용 가능하면 반환
            if (isPortAvailable(savedPort)) {
                return savedPort;
            }
            // 사용 불가능하면 파일 삭제하고 새로 찾기
            System.out.println("저장된 포트(" + savedPort + ")가 사용 중입니다. 새 포트를 찾습니다.");
            try {
                Files.deleteIfExists(Paths.get(PORT_FILE_PATH));
            } catch (Exception e) {
                System.err.println("포트 파일 삭제 실패: " + e.getMessage());
            }
        }
        
        // 저장된 포트가 없거나 사용 불가능하면 새로 찾기
        return findNewAvailablePort();
    }

    public synchronized void start() throws Exception {
        // 이미 실행 중이면 재시작하지 않음
        if (server != null && server.isStarted()) {
            // 포트가 설정되지 않았으면 connector에서 가져오기
            if (port == 0 && connector != null) {
                try {
                    port = connector.getLocalPort();
                } catch (Exception e) {
                    System.err.println("포트를 가져오는 중 오류: " + e.getMessage());
                }
            }
            System.out.println("========================================");
            System.out.println("서버가 이미 실행 중입니다. 포트: " + port);
            System.out.println("접속 URL: http://localhost:" + port + "/video-call.html");
            System.out.println("========================================");
            return;
        }
        
        System.out.println("========================================");
        System.out.println("비디오 통화 서버 시작 중...");
        
        // 중요: 새 서버를 시작하기 전에 반드시 저장된 포트 확인
        // 여러 번 확인하여 타이밍 이슈 방지
        int savedPort = readPortFromFile();
        if (savedPort > 0) {
            System.out.println("파일에서 저장된 포트 확인: " + savedPort);
            
            // 저장된 포트에서 서버가 실행 중인지 확인 (여러 번 시도)
            for (int checkAttempt = 0; checkAttempt < 5; checkAttempt++) {
                if (isServerRunningOnPort(savedPort)) {
                    System.out.println("========================================");
                    System.out.println("포트 " + savedPort + "에서 비디오 통화 서버가 실행 중입니다.");
                    System.out.println("기존 서버 포트를 사용합니다: " + savedPort);
                    System.out.println("접속 URL: http://localhost:" + savedPort + "/video-call.html");
                    System.out.println("(모든 클라이언트가 이 포트로 연결됩니다)");
                    System.out.println("========================================");
                    // 다른 프로세스에서 서버가 실행 중이면 그 포트를 그대로 사용
                    port = savedPort;
                    localIpAddress = findLocalIpAddress();
                    return; // 서버를 시작하지 않고 종료
                }
                
                // 서버가 아직 시작 중일 수 있으므로 잠시 대기
                if (checkAttempt < 4) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            System.out.println("저장된 포트(" + savedPort + ")에서 서버가 실행 중이 아닙니다.");
            // 저장된 포트를 사용할 수 없으면 파일 삭제
            try {
                Files.deleteIfExists(Paths.get(PORT_FILE_PATH));
            } catch (Exception e) {
                System.err.println("포트 파일 삭제 실패: " + e.getMessage());
            }
        }
        
        // 포트 결정: 파일에 저장된 포트가 없거나 서버가 실행 중이 아니면 새 포트 찾기
        int targetPort = this.port;
        if (targetPort == 0) {
            System.out.println("새로운 포트 찾는 중...");
            targetPort = findNewAvailablePort();
            System.out.println("새 포트 할당: " + targetPort);
        } else {
            System.out.println("기존 포트 사용: " + targetPort);
        }
        
        // 바인딩 시도 (실패 시 다른 포트로 재시도)
        int maxRetries = 5;
        int retryCount = 0;
        Exception lastError = null;
        
        while (retryCount < maxRetries) {
            try {
                // 바인딩 전에 포트가 실제로 사용 가능한지 확인
                if (!isPortAvailable(targetPort)) {
                    System.out.println("포트 " + targetPort + "가 사용 중입니다. 다른 포트를 찾는 중...");
                    
                    // 바인딩 실패 시 다시 한 번 저장된 포트에서 서버가 실행 중인지 확인
                    int savedPortCheck = readPortFromFile();
                    if (savedPortCheck > 0 && isServerRunningOnPort(savedPortCheck)) {
                        System.out.println("========================================");
                        System.out.println("포트 " + savedPortCheck + "에서 비디오 통화 서버가 실행 중입니다.");
                        System.out.println("기존 서버 포트를 사용합니다: " + savedPortCheck);
                        System.out.println("접속 URL: http://localhost:" + savedPortCheck + "/video-call.html");
                        System.out.println("(모든 클라이언트가 이 포트로 연결됩니다)");
                        System.out.println("========================================");
                        port = savedPortCheck;
                        localIpAddress = findLocalIpAddress();
                        return; // 서버를 시작하지 않고 종료
                    }
                    
                    // 새 포트 찾기
                    targetPort = findNewAvailablePort();
                    System.out.println("새 포트로 재시도: " + targetPort);
                    retryCount++;
                    continue;
                }
                
                server = new Server();
                connector = new ServerConnector(server);
                
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

                // 정적 파일 서빙 - 클래스패스 리소스 사용
                URL resourceBase = getClass().getClassLoader().getResource("public");
                if (resourceBase == null) {
                    // 개발 환경에서 직접 경로 사용
                    String publicPath = System.getProperty("user.dir") + "/src/main/resources/public";
                    java.io.File publicDir = new java.io.File(publicPath);
                    if (!publicDir.exists() || !publicDir.isDirectory()) {
                        // Maven 빌드 경로 시도
                        publicPath = System.getProperty("user.dir") + "/target/classes/public";
                        publicDir = new java.io.File(publicPath);
                        if (!publicDir.exists() || !publicDir.isDirectory()) {
                            throw new Exception("정적 파일 디렉토리를 찾을 수 없습니다. " + 
                                "src/main/resources/public 또는 target/classes/public 경로를 확인하세요.");
                        }
                    }
                    resourceBase = publicDir.toURI().toURL();
                }
                
                // Resource 객체로 변환하여 설정
                Resource resource = Resource.newResource(resourceBase);
                context.setBaseResource(resource);
                
                // DefaultServlet 추가 (정적 파일 서빙)
                ServletHolder defaultServlet = new ServletHolder("default", DefaultServlet.class);
                defaultServlet.setInitParameter("dirAllowed", "false");
                context.addServlet(defaultServlet, "/");

                server.setHandler(context);
                
                // 서버 시작 (동기적으로 완료될 때까지 대기)
                System.out.println("서버 시작 중... (포트: " + targetPort + ")");
                server.start();
                
                // 성공적으로 시작되면 루프 종료
                break;
                
            } catch (Exception bindError) {
                lastError = bindError;
                System.out.println("포트 " + targetPort + " 바인딩 실패: " + bindError.getMessage());
                
                // 바인딩 실패 시 저장된 포트에서 서버가 실행 중인지 확인 (여러 번 시도)
                int savedPortCheck = readPortFromFile();
                if (savedPortCheck > 0) {
                    for (int checkAttempt = 0; checkAttempt < 3; checkAttempt++) {
                        if (isServerRunningOnPort(savedPortCheck)) {
                            System.out.println("========================================");
                            System.out.println("포트 " + savedPortCheck + "에서 비디오 통화 서버가 실행 중입니다.");
                            System.out.println("기존 서버 포트를 사용합니다: " + savedPortCheck);
                            System.out.println("접속 URL: http://localhost:" + savedPortCheck + "/video-call.html");
                            System.out.println("(모든 클라이언트가 이 포트로 연결됩니다)");
                            System.out.println("========================================");
                            port = savedPortCheck;
                            localIpAddress = findLocalIpAddress();
                            return; // 서버를 시작하지 않고 종료
                        }
                        
                        // 서버가 아직 시작 중일 수 있으므로 잠시 대기
                        if (checkAttempt < 2) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                
                // 재시도 가능하면 새 포트 찾기
                if (retryCount < maxRetries - 1) {
                    System.out.println("다른 포트로 재시도 중... (시도: " + (retryCount + 1) + "/" + maxRetries + ")");
                    targetPort = findNewAvailablePort();
                    retryCount++;
                    
                    // 서버 객체 정리
                    if (server != null && !server.isStarted()) {
                        try {
                            server.stop();
                        } catch (Exception e) {
                            // 무시
                        }
                        server = null;
                        connector = null;
                    }
                    
                    // 잠시 대기 후 재시도
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new Exception("서버 시작이 중단되었습니다.", e);
                    }
                } else {
                    // 최대 재시도 횟수 초과
                    throw new Exception("포트 바인딩 실패. " + maxRetries + "회 시도 후에도 사용 가능한 포트를 찾을 수 없습니다. 마지막 오류: " + 
                        bindError.getMessage(), bindError);
                }
            }
        }
        
        // 최종적으로 저장된 포트에서 서버가 실행 중인지 확인 (서버 시작 실패 시)
        if (server == null || !server.isStarted()) {
            int savedPortFinal = readPortFromFile();
            if (savedPortFinal > 0) {
                // 여러 번 확인하여 타이밍 이슈 방지
                for (int checkAttempt = 0; checkAttempt < 5; checkAttempt++) {
                    if (isServerRunningOnPort(savedPortFinal)) {
                        System.out.println("========================================");
                        System.out.println("포트 " + savedPortFinal + "에서 비디오 통화 서버가 실행 중입니다.");
                        System.out.println("기존 서버 포트를 사용합니다: " + savedPortFinal);
                        System.out.println("접속 URL: http://localhost:" + savedPortFinal + "/video-call.html");
                        System.out.println("(모든 클라이언트가 이 포트로 연결됩니다)");
                        System.out.println("========================================");
                        port = savedPortFinal;
                        localIpAddress = findLocalIpAddress();
                        return;
                    }
                    
                    // 서버가 아직 시작 중일 수 있으므로 잠시 대기
                    if (checkAttempt < 4) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            if (lastError != null) {
                throw lastError;
            }
        }
        
        // 서버가 완전히 시작될 때까지 대기
        while (!server.isStarted()) {
            Thread.sleep(10);
        }
        
        // 포트 정보 저장
        port = connector.getLocalPort();
        localIpAddress = findLocalIpAddress();
        
        // 포트를 파일에 저장 (이미 저장되어 있어도 덮어쓰기)
        savePortToFile(port);
        
        // ngrok 자동 실행 시도
        startNgrok(port);
        
        // 잠시 대기 후 ngrok URL 감지 (ngrok이 시작되는 시간 필요)
        String ngrokUrl = null;
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(500); // 0.5초 대기
                ngrokUrl = NgrokUtil.getNgrokHttpsUrl();
                if (ngrokUrl != null) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 네트워크 브로드캐스트 시작 (ngrok URL 포함)
        NetworkDiscovery.startVideoServerBroadcast(localIpAddress, port, ngrokUrl);
        
        System.out.println("========================================");
        System.out.println("비디오 통화 서버 시작 완료!");
        System.out.println("포트: " + port);
        System.out.println("로컬 접속: http://localhost:" + port + "/video-call.html");
        if (ngrokUrl != null) {
            System.out.println("HTTPS 접속 (ngrok): " + ngrokUrl + "/video-call.html");
            System.out.println("✅ 다른 컴퓨터에서 위 HTTPS URL로 접속하면 카메라/마이크 사용 가능!");
        } else {
            System.out.println("⚠️  ngrok이 실행되지 않았습니다. 수동으로 실행하려면:");
            System.out.println("   ngrok http " + port);
        }
        System.out.println("========================================");
        
        List<String> ipAddresses = getLocalIpAddresses();
        if (!ipAddresses.isEmpty() && !ipAddresses.get(0).equals("localhost")) {
            String mainIp = ipAddresses.get(0);
            System.out.println("네트워크 접속: http://" + mainIp + ":" + port + "/video-call.html");
        }
        System.out.println("========================================");
    }
    
    public String getLocalIpAddress() {
        return localIpAddress != null ? localIpAddress : findLocalIpAddress();
    }
    
    public int getHttpsPort() {
        return httpsPort;
    }
    
    /**
     * ngrok HTTPS URL 가져오기
     */
    public String getNgrokUrl() {
        return NgrokUtil.getNgrokHttpsUrl();
    }
    
    /**
     * ngrok 자동 실행
     */
    private void startNgrok(int port) {
        // 이미 ngrok이 실행 중인지 확인
        if (NgrokUtil.isNgrokRunning()) {
            System.out.println("[ServerLauncher] ngrok이 이미 실행 중입니다.");
            return;
        }
        
        // ngrok 프로세스가 이미 실행 중이면 중지
        if (ngrokProcess != null && ngrokProcess.isAlive()) {
            System.out.println("[ServerLauncher] 기존 ngrok 프로세스가 실행 중입니다.");
            return;
        }
        
        Thread ngrokThread = new Thread(() -> {
            try {
                System.out.println("[ServerLauncher] ngrok 자동 실행 시도 중... (포트: " + port + ")");
                
                // 운영체제에 따라 ngrok 명령어 경로 확인
                String ngrokCommand = findNgrokCommand();
                if (ngrokCommand == null) {
                    System.out.println("[ServerLauncher] ⚠️  ngrok을 찾을 수 없습니다.");
                    System.out.println("[ServerLauncher]    ngrok 설치: https://ngrok.com/");
                    System.out.println("[ServerLauncher]    또는 PATH에 ngrok을 추가하세요.");
                    return;
                }
                
                // ngrok 실행
                // localhost가 IPv6(::1)로 해석되어 업스트림 연결이 실패하는 경우가 있어
                // 항상 IPv4 루프백(127.0.0.1)으로 명시적으로 지정한다.
                // 예: ERR_NGROK_8012, dial tcp [::1]:포트: connect: connection refused
                ProcessBuilder pb = new ProcessBuilder(
                        ngrokCommand,
                        "http",
                        "127.0.0.1:" + port
                );
                pb.redirectErrorStream(true);
                ngrokProcess = pb.start();
                
                System.out.println("[ServerLauncher] ✅ ngrok 실행 시작됨");
                System.out.println("[ServerLauncher]    ngrok이 시작되는 동안 잠시 기다려주세요...");
                
                // ngrok 프로세스 출력을 읽어서 로그에 표시 (선택사항)
                Thread outputThread = new Thread(() -> {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(ngrokProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && ngrokProcess.isAlive()) {
                            // ngrok 출력은 조용히 처리 (너무 많은 로그 방지)
                            if (line.contains("started tunnel") || line.contains("Session Status")) {
                                System.out.println("[ngrok] " + line);
                            }
                        }
                    } catch (Exception e) {
                        // 무시
                    }
                });
                outputThread.setDaemon(true);
                outputThread.start();
                
            } catch (Exception e) {
                System.err.println("[ServerLauncher] ngrok 실행 실패: " + e.getMessage());
                System.err.println("[ServerLauncher] 수동으로 실행하려면: ngrok http " + port);
            }
        });
        ngrokThread.setDaemon(true);
        ngrokThread.start();
    }
    
    /**
     * ngrok 명령어 경로 찾기
     */
    private String findNgrokCommand() {
        // 먼저 "ngrok" 명령어가 PATH에 있는지 확인
        String os = System.getProperty("os.name").toLowerCase();
        String[] commands;
        
        if (os.contains("win")) {
            // Windows: ngrok.exe 또는 ngrok
            commands = new String[]{"ngrok.exe", "ngrok"};
        } else {
            // macOS/Linux: ngrok
            commands = new String[]{"ngrok"};
        }
        
        for (String cmd : commands) {
            try {
                Process process = Runtime.getRuntime().exec(
                    os.contains("win") ? new String[]{"where", cmd} : new String[]{"which", cmd});
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream()))) {
                        String path = reader.readLine();
                        if (path != null && !path.isEmpty()) {
                            return cmd; // 명령어만 반환 (PATH에서 찾을 수 있음)
                        }
                    }
                }
            } catch (Exception e) {
                // 계속 시도
            }
        }
        
        // 일반적인 설치 경로 확인 (macOS)
        if (os.contains("mac")) {
            String[] commonPaths = {
                "/usr/local/bin/ngrok",
                "/opt/homebrew/bin/ngrok",
                System.getProperty("user.home") + "/ngrok"
            };
            for (String path : commonPaths) {
                java.io.File file = new java.io.File(path);
                if (file.exists() && file.canExecute()) {
                    return path;
                }
            }
        }
        
        return null;
    }

    public void stop() throws Exception {
        // ngrok 프로세스 종료
        if (ngrokProcess != null && ngrokProcess.isAlive()) {
            System.out.println("[ServerLauncher] ngrok 프로세스 종료 중...");
            ngrokProcess.destroy();
            try {
                // 프로세스가 종료될 때까지 최대 3초 대기
                if (!ngrokProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    ngrokProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                ngrokProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            ngrokProcess = null;
        }
        
        if (server != null && server.isStarted()) {
            server.stop();
            // 서버 종료 시 포트 파일 삭제 (선택사항 - 서버 재시작 시 같은 포트 사용하려면 유지)
            // try {
            //     Files.deleteIfExists(Paths.get(PORT_FILE_PATH));
            // } catch (Exception e) {
            //     System.err.println("포트 파일 삭제 실패: " + e.getMessage());
            // }
        }
    }

    public int getPort() {
        return port;
    }
}

