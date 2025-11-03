package com.test.video.swing;

import javax.swing.*;
import java.awt.*;

public class SwingMain extends JFrame {
    
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private ServerLauncherWrapper serverLauncher;
    
    public SwingMain() {
        setTitle("커뮤니티 애플리케이션");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        
        // 서버 백그라운드 시작
        serverLauncher = new ServerLauncherWrapper();
        serverLauncher.startServer();
        
        // 카드 레이아웃으로 화면 전환
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        
        // 메인 메뉴 패널
        MainMenuPanel menuPanel = new MainMenuPanel(this);
        mainPanel.add(menuPanel, "menu");
        
        // 영상통화 패널
        VideoCallPanel videoPanel = new VideoCallPanel(this);
        mainPanel.add(videoPanel, "video");
        
        add(mainPanel);
        cardLayout.show(mainPanel, "menu");
    }
    
    public void showMenu() {
        cardLayout.show(mainPanel, "menu");
    }
    
    public void showVideoCall() {
        cardLayout.show(mainPanel, "video");
    }
    
    public int getServerPort() {
        return serverLauncher.getPort();
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new SwingMain().setVisible(true);
        });
    }
}

