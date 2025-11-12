package com.swingauth.ui;

import com.swingauth.video.ServerLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;

public class VideoCallFrame extends JFrame {
    private final ServerLauncher serverLauncher;
    private JEditorPane webView;

    public VideoCallFrame() {
        setTitle("랜덤 영상통화");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        serverLauncher = new ServerLauncher();
        try {
            serverLauncher.start();
            int port = serverLauncher.getPort();
            
            // JEditorPane으로 HTML 표시 (제한적이지만 기본 기능은 동작)
            webView = new JEditorPane();
            webView.setContentType("text/html");
            webView.setEditable(false);
            
            // 외부 브라우저로 열기 (더 나은 WebRTC 지원)
            SwingUtilities.invokeLater(() -> {
                try {
                    URI uri = new URI("http://localhost:" + port + "/video-call.html");
                    Desktop.getDesktop().browse(uri);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, 
                        "브라우저를 열 수 없습니다: " + ex.getMessage(), 
                        "오류", JOptionPane.ERROR_MESSAGE);
                }
            });
            
            // 대안: JEditorPane에 HTML 로드 (WebRTC는 제한적)
            String htmlContent = "<html><body style='font-family: sans-serif; padding: 20px;'>" +
                "<h2>랜덤 영상통화</h2>" +
                "<p>브라우저 창이 열렸습니다. 브라우저에서 영상통화를 진행하세요.</p>" +
                "<p>서버 주소: <a href='http://localhost:" + port + "/video-call.html'>http://localhost:" + port + "/video-call.html</a></p>" +
                "</body></html>";
            webView.setText(htmlContent);
            
            add(new JScrollPane(webView), BorderLayout.CENTER);
            
            // 창 닫을 때 서버 종료
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    try {
                        serverLauncher.stop();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "서버 시작 실패: " + e.getMessage(), 
                "오류", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
}
