package com.test.video.swing;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;

public class VideoCallPanel extends JPanel {
    @SuppressWarnings("unused")
    private SwingMain parent;
    private JFXPanel fxPanel;
    private WebEngine webEngine;
    
    public VideoCallPanel(SwingMain parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        
        // 헤더 (뒤로가기 + 제목)
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(43, 100, 255));
        header.setPreferredSize(new Dimension(0, 50));
        
        JButton backBtn = new JButton("← 메인");
        backBtn.setForeground(Color.WHITE);
        backBtn.setBackground(new Color(43, 100, 255));
        backBtn.setBorderPainted(false);
        backBtn.setFocusPainted(false);
        backBtn.addActionListener(e -> parent.showMenu());
        
        JLabel title = new JLabel("랜덤 영상통화", JLabel.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        
        header.add(backBtn, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        
        // JavaFX WebView 임베드
        fxPanel = new JFXPanel();
        
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();
            webEngine.setJavaScriptEnabled(true);
            
            // 영상통화 HTML 로드
            String url = String.format("http://localhost:%d/video-call.html", parent.getServerPort());
            webEngine.load(url);
            
            Scene scene = new Scene(webView);
            fxPanel.setScene(scene);
        });
        
        add(header, BorderLayout.NORTH);
        add(fxPanel, BorderLayout.CENTER);
    }
}
