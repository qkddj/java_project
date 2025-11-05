package com.test.video.swing;

import javax.swing.*;

public class SwingMain extends JFrame {
    
    private ServerLauncherWrapper serverLauncher;
    
    public SwingMain() {
        setTitle("커뮤니티 애플리케이션");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        
        serverLauncher = new ServerLauncherWrapper();
        serverLauncher.startServer();
        
        MainMenuPanel menuPanel = new MainMenuPanel(this);
        add(menuPanel);
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
