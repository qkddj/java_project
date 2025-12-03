package com.swingauth.ui;

import com.swingauth.model.User;
import com.swingauth.video.ServerLauncher;
import com.swingauth.util.NetworkDiscovery;

import javax.swing.*;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VideoCallFrame extends JFrame {
    private final ServerLauncher serverLauncher;
    private final User user;
    private final boolean isDarkMode;

    public VideoCallFrame() {
        this(null, true);
    }

    public VideoCallFrame(User user, boolean isDarkMode) {
        super(); // JFrame 초기화
        this.user = user;
        this.isDarkMode = isDarkMode;
        serverLauncher = ServerLauncher.getInstance();
        
        // 창을 보이지 않게 설정 (브라우저만 열기)
        setVisible(false);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        // 서버가 실행 중이 아니면 시작
        try {
            final User finalUser = user; // final로 선언
            
            // 디버깅: user 객체 상태 확인
            System.out.println("========================================");
            System.out.println("[VideoCallFrame] 영상통화 시작");
            System.out.println("[VideoCallFrame] user 객체: " + (user != null ? "존재" : "null"));
            if (user != null) {
                System.out.println("[VideoCallFrame] user.username: " + user.username);
                System.out.println("[VideoCallFrame] user.username null 여부: " + (user.username == null));
                System.out.println("[VideoCallFrame] user.username empty 여부: " + (user.username != null && user.username.isEmpty()));
            }
            
            final String actualUsername = (finalUser != null && finalUser.username != null && !finalUser.username.isEmpty())
                ? finalUser.username
                : "unknown";
            
            System.out.println("[VideoCallFrame] 최종 사용자 ID: " + actualUsername);
            System.out.println("========================================");
            
            // 랜덤 채팅처럼 네트워크에서 서버를 자동으로 찾기
            connectToVideoServer(actualUsername, isDarkMode);
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, 
                    "서버 시작 실패: " + e.getMessage(), 
                    "오류", JOptionPane.ERROR_MESSAGE);
            });
            e.printStackTrace();
            dispose();
        }
    }
    
    /**
     * 파일에서 저장된 포트 번호 읽기
     */
    private int readPortFromFile() {
        try {
            String portFilePath = System.getProperty("user.home") + "/.video-call-server-port";
            Path portFile = Paths.get(portFilePath);
            if (Files.exists(portFile)) {
                String portStr = Files.readAllLines(portFile).get(0).trim();
                return Integer.parseInt(portStr);
            }
        } catch (Exception e) {
            System.err.println("[VideoCallFrame] 포트 파일 읽기 실패: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * 서버 포트가 설정될 때까지 대기
     */
    private int waitForPort() {
        int maxAttempts = 50; // 최대 5초 대기 (100ms * 50)
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            int port = serverLauncher.getPort();
            if (port > 0) {
                System.out.println("[VideoCallFrame] 포트 확인 성공: " + port + " (시도: " + (attempt + 1) + ")");
                return port;
            }
            
            try {
                Thread.sleep(100); // 100ms 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
            attempt++;
            
            // 10번마다 로그 출력
            if (attempt % 10 == 0) {
                System.out.println("[VideoCallFrame] 포트 대기 중... (시도: " + attempt + "/" + maxAttempts + ")");
            }
        }
        
        int finalPort = serverLauncher.getPort();
        System.out.println("[VideoCallFrame] 최종 포트 확인: " + finalPort);
        return finalPort; // 마지막으로 한 번 더 시도
    }
    
    /**
     * 네트워크에서 영상통화 서버를 자동으로 찾아서 연결 (랜덤 채팅과 유사한 구조)
     */
    private void connectToVideoServer(String username, boolean isDarkMode) {
        // 백그라운드 스레드에서 서버 찾기
        Thread discoveryThread = new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                // 상태 표시 (선택사항 - 다이얼로그 없이 조용히 진행)
            });
            
            System.out.println("[VideoCallFrame] 네트워크에서 영상통화 서버를 자동으로 찾는 중...");
            NetworkDiscovery.VideoServerInfo serverInfo = NetworkDiscovery.discoverVideoServer(10000); // 10초 동안 찾기
            
            SwingUtilities.invokeLater(() -> {
                if (serverInfo != null) {
                    // 서버를 찾았으면 ngrok URL이 있으면 HTTPS로, 없으면 HTTP로 접속
                    String accessUrl = serverInfo.getAccessUrl();
                    String fullUrl = accessUrl + "/video-call.html?username=" + 
                        java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8) +
                        "&theme=" + (isDarkMode ? "dark" : "light");
                    
                    System.out.println("========================================");
                    System.out.println("[VideoCallFrame] 영상통화 서버 발견!");
                    System.out.println("서버 IP: " + serverInfo.ip + ":" + serverInfo.port);
                    if (serverInfo.ngrokUrl != null) {
                        System.out.println("HTTPS URL (ngrok): " + serverInfo.ngrokUrl);
                        System.out.println("✅ HTTPS로 접속 - 카메라/마이크 사용 가능!");
                    } else {
                        System.out.println("HTTP URL: " + accessUrl);
                        System.out.println("⚠️  카메라/마이크 사용을 위해 서버에서 ngrok을 실행하세요");
                    }
                    System.out.println("접속 URL: " + fullUrl);
                    System.out.println("========================================");
                    
                    // 브라우저로 열기
                    try {
                        URI uri = new URI(fullUrl);
                        Desktop.getDesktop().browse(uri);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, 
                            "브라우저를 열 수 없습니다: " + ex.getMessage(), 
                            "오류", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                        dispose();
                    }
                } else {
                    // 서버를 찾지 못했으면 로컬 서버 시작
                    System.out.println("[VideoCallFrame] 서버를 찾지 못했습니다. 로컬 서버를 시작합니다...");
                    startLocalServer(username, isDarkMode);
                }
            });
        });
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }
    
    /**
     * 로컬 서버 시작 (서버를 찾지 못한 경우)
     */
    private void startLocalServer(String username, boolean isDarkMode) {
        try {
            // 서버 시작 (이미 실행 중이면 그대로 사용)
            serverLauncher.start();
            System.out.println("[VideoCallFrame] 로컬 서버 시작 완료");
            
            // 포트 가져오기
            int port = waitForPort();
            if (port == 0) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, 
                        "서버 포트를 가져올 수 없습니다.", 
                        "오류", JOptionPane.ERROR_MESSAGE);
                });
                dispose();
                return;
            }
            
            // ngrok URL 확인
            String ngrokUrl = serverLauncher.getNgrokUrl();
            String accessUrl;
            if (ngrokUrl != null) {
                accessUrl = ngrokUrl;
                System.out.println("[VideoCallFrame] ngrok HTTPS URL 감지: " + ngrokUrl);
            } else {
                accessUrl = "http://localhost:" + port;
            }
            
            String fullUrl = accessUrl + "/video-call.html?username=" + 
                java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8) +
                "&theme=" + (isDarkMode ? "dark" : "light");
            
            System.out.println("[VideoCallFrame] 접속 URL: " + fullUrl);
            
            // 브라우저로 열기
            SwingUtilities.invokeLater(() -> {
                try {
                    URI uri = new URI(fullUrl);
                    Desktop.getDesktop().browse(uri);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, 
                        "브라우저를 열 수 없습니다: " + ex.getMessage(), 
                        "오류", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                    dispose();
                }
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, 
                    "서버 시작 실패: " + e.getMessage(), 
                    "오류", JOptionPane.ERROR_MESSAGE);
            });
            e.printStackTrace();
            dispose();
        }
    }
    
    /**
     * 네트워크 접속 안내 다이얼로그 표시 (사용하지 않음 - 자동 발견으로 대체)
     */
    private void showNetworkAccessDialog(String serverIp, int port, String username, boolean isDarkMode) {
        String localUrl = "http://localhost:" + port + "/video-call.html?username=" + username;
        String networkUrl = "http://" + serverIp + ":" + port + "/video-call.html?username=" + username;
        
        String message = "랜덤 영상통화 서버가 시작되었습니다!\n\n" +
                        "로컬 접속:\n" + localUrl + "\n\n" +
                        "다른 컴퓨터에서 접속:\n" + networkUrl + "\n\n" +
                        "⚠️ 중요: 다른 컴퓨터에서 카메라/마이크를 사용하려면\n" +
                        "HTTPS가 필요합니다. 다음 방법 중 하나를 사용하세요:\n\n" +
                        "1. ngrok 사용 (권장):\n" +
                        "   - ngrok 설치: https://ngrok.com/\n" +
                        "   - 터미널에서 실행: ngrok http " + port + "\n" +
                        "   - 표시된 https://xxx.ngrok.io URL 사용\n\n" +
                        "2. 또는 서버 컴퓨터에서 localhost로 접속\n" +
                        "   (카메라/마이크 사용 가능)\n\n" +
                        "URL을 클립보드에 복사하시겠습니까?";
        
        int option = JOptionPane.showConfirmDialog(
            this,
            message,
            "영상통화 서버 시작 완료",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE
        );
        
        if (option == JOptionPane.YES_OPTION) {
            // 네트워크 URL을 클립보드에 복사
            try {
                java.awt.datatransfer.StringSelection selection = 
                    new java.awt.datatransfer.StringSelection(networkUrl);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(selection, null);
                JOptionPane.showMessageDialog(
                    this,
                    "네트워크 URL이 클립보드에 복사되었습니다:\n" + networkUrl,
                    "복사 완료",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception e) {
                System.err.println("클립보드 복사 실패: " + e.getMessage());
            }
        }
    }
    
    public void cleanup() {
        try {
            serverLauncher.stop();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
