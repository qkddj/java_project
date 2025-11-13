package com.swingauth.ui;

import com.swingauth.video.ServerLauncher;

import javax.swing.*;
import java.awt.Desktop;
import java.net.URI;

public class VideoCallFrame extends JFrame {
    private final ServerLauncher serverLauncher;

    public VideoCallFrame() {
        serverLauncher = new ServerLauncher();
        try {
            serverLauncher.start();
            int port = serverLauncher.getPort();
            
            // 외부 브라우저로 열기 (더 나은 WebRTC 지원)
            SwingUtilities.invokeLater(() -> {
                try {
                    URI uri = new URI("http://localhost:" + port + "/video-call.html");
                    Desktop.getDesktop().browse(uri);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, 
                        "브라우저를 열 수 없습니다: " + ex.getMessage(), 
                        "오류", JOptionPane.ERROR_MESSAGE);
                }
            });
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, 
                "서버 시작 실패: " + e.getMessage(), 
                "오류", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
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
