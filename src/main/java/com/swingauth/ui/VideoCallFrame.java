package com.swingauth.ui;

import com.swingauth.video.ServerLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Desktop;
import java.net.URI;

public class VideoCallFrame extends JFrame {
    private final ServerLauncher serverLauncher;
    private boolean serverStarted = false;

    public VideoCallFrame() {
        setTitle("랜덤 영상통화");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(400, 200);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        serverLauncher = new ServerLauncher();
        
        // 상태 표시 패널
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel statusLabel = new JLabel("영상통화 서버를 시작하는 중...");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        
        JButton closeButton = new JButton("닫기");
        closeButton.addActionListener(e -> closeWindow());
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(closeButton);
        statusPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(statusPanel, BorderLayout.CENTER);
        
        // 창 닫기 이벤트 처리
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });
        
        // 서버 시작 및 브라우저 열기
        new Thread(() -> {
            try {
                serverLauncher.start();
                serverStarted = true;
                int port = serverLauncher.getPort();
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("서버가 시작되었습니다. 브라우저를 여는 중...");
                    try {
                        URI uri = new URI("http://localhost:" + port + "/video-call.html");
                        Desktop.getDesktop().browse(uri);
                        statusLabel.setText("브라우저가 열렸습니다. 포트: " + port);
                    } catch (Exception ex) {
                        statusLabel.setText("브라우저를 열 수 없습니다: " + ex.getMessage());
                        JOptionPane.showMessageDialog(VideoCallFrame.this, 
                            "브라우저를 열 수 없습니다.\n수동으로 다음 주소를 열어주세요:\nhttp://localhost:" + port + "/video-call.html", 
                            "오류", JOptionPane.ERROR_MESSAGE);
                    }
                });
                
            } catch (Exception e) {
                serverStarted = false;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("서버 시작 실패: " + e.getMessage());
                    JOptionPane.showMessageDialog(VideoCallFrame.this, 
                        "서버 시작 실패: " + e.getMessage(), 
                        "오류", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                });
            }
        }).start();
    }
    
    private void closeWindow() {
        if (serverStarted) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "영상통화 서버를 종료하시겠습니까?",
                "확인",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                cleanup();
                dispose();
            }
        } else {
            dispose();
        }
    }
    
    public void cleanup() {
        try {
            if (serverLauncher != null) {
                serverLauncher.stop();
                System.out.println("영상통화 서버가 종료되었습니다.");
            }
        } catch (Exception ex) {
            System.err.println("서버 종료 중 오류: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
