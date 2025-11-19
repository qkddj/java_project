package com.swingauth.ui;

import com.swingauth.model.User;
import com.swingauth.video.ServerLauncher;

import javax.swing.*;
import java.awt.Desktop;
import java.net.URI;

public class VideoCallFrame extends JFrame {
    private final ServerLauncher serverLauncher;
    private final User user;

    public VideoCallFrame() {
        this(null);
    }

    public VideoCallFrame(User user) {
        this.user = user;
        // 서버는 이미 Main에서 시작되었으므로, 포트만 가져옴
        serverLauncher = ServerLauncher.getInstance();
        int currentPort = serverLauncher.getPort();
        
        if (currentPort == 0) {
            // 서버가 아직 시작되지 않았으면 시작 시도
            try {
                serverLauncher.start();
                currentPort = serverLauncher.getPort();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, 
                    "서버 시작 실패: " + e.getMessage(), 
                    "오류", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
                return;
            }
        }
        
        final int port = currentPort; // final로 선언하여 람다에서 사용 가능하게 함
        final User finalUser = user; // final로 선언
        
        // 외부 브라우저로 열기 (더 나은 WebRTC 지원)
        SwingUtilities.invokeLater(() -> {
            try {
                // URL을 명확하게 구성하여 이중 슬래시 방지
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append("http://localhost:").append(port).append("/video-call.html");
                
                // username을 URL 파라미터로 전달
                if (finalUser != null && finalUser.username != null && !finalUser.username.isEmpty()) {
                    urlBuilder.append("?username=").append(java.net.URLEncoder.encode(finalUser.username, "UTF-8"));
                }
                
                String url = urlBuilder.toString();
                URI uri = new URI(url);
                Desktop.getDesktop().browse(uri);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, 
                    "브라우저를 열 수 없습니다: " + ex.getMessage(), 
                    "오류", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });
    }
    
    public void cleanup() {
        try {
            serverLauncher.stop();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
