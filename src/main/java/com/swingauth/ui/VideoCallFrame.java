package com.swingauth.ui;

import com.swingauth.model.User;
import com.swingauth.video.ServerLauncher;

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
            
            // 먼저 파일에서 저장된 포트 확인
            int savedPort = readPortFromFile();
            if (savedPort > 0) {
                System.out.println("[VideoCallFrame] 파일에서 포트 읽음: " + savedPort);
                // 저장된 포트로 서버가 실행 중인지 확인하려고 시도
                // (실제로는 서버 시작 시 같은 포트를 사용하도록 함)
            }
            
            // 서버 시작 (이미 실행 중이면 그대로 사용)
            try {
                serverLauncher.start();
                System.out.println("[VideoCallFrame] 서버 시작 완료");
            } catch (Exception startEx) {
                System.err.println("[VideoCallFrame] 서버 시작 실패: " + startEx.getMessage());
                startEx.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, 
                        "서버 시작 실패: " + startEx.getMessage() + "\n자세한 내용은 콘솔을 확인하세요.", 
                        "서버 시작 오류", JOptionPane.ERROR_MESSAGE);
                });
                dispose();
                return;
            }
            
            // 포트 가져오기 (서버가 완전히 시작될 때까지 대기)
            int currentPort = waitForPort();
            
            System.out.println("[VideoCallFrame] 가져온 포트: " + currentPort);
            
            if (currentPort == 0) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, 
                        "서버 포트를 가져올 수 없습니다.\n서버가 시작 중일 수 있습니다. 잠시 후 다시 시도해주세요.", 
                        "오류", JOptionPane.ERROR_MESSAGE);
                });
                dispose();
                return;
            }
            
            final int port = currentPort; // final로 선언하여 람다에서 사용 가능하게 함
            
            // 콘솔에는 실제 username과 URL 표시
            System.out.println("========================================");
            System.out.println("[VideoCallFrame] 브라우저 열기");
            System.out.println("사용자 ID: " + actualUsername);
            System.out.println("URL: http://localhost:" + port + "/video-call.html?username=" + actualUsername);
            System.out.println("========================================");
            
            // 서버가 열린 포트로 접속 (localhost 사용)
            // 서버는 0.0.0.0으로 바인딩되어 있어 localhost로 접속 가능
            final String hostAddress = "localhost";
            
            // 외부 브라우저로 열기 (더 나은 WebRTC 지원)
            SwingUtilities.invokeLater(() -> {
            try {
                // URL을 명확하게 구성하여 이중 슬래시 방지
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append("http://").append(hostAddress).append(":").append(port).append("/video-call.html");
                
                // 실제 username과 테마 정보를 URL 파라미터로 전달
                urlBuilder.append("?username=").append(java.net.URLEncoder.encode(actualUsername, "UTF-8"));
                urlBuilder.append("&theme=").append(isDarkMode ? "dark" : "light");
                
                String url = urlBuilder.toString();
                System.out.println("[VideoCallFrame] 실제 생성된 URL: " + url);
                URI uri = new URI(url);
                Desktop.getDesktop().browse(uri);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, 
                        "브라우저를 열 수 없습니다: " + ex.getMessage(), 
                        "오류", JOptionPane.ERROR_MESSAGE);
                });
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
    
    public void cleanup() {
        try {
            serverLauncher.stop();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
