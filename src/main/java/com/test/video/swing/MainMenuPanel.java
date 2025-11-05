package com.test.video.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.net.URI;

public class MainMenuPanel extends JPanel {
    
    public MainMenuPanel(SwingMain parent) {
        setLayout(new BorderLayout());
        
        JPanel header = new JPanel();
        header.setBackground(new Color(43, 100, 255));
        header.setPreferredSize(new Dimension(0, 60));
        JLabel title = new JLabel("커뮤니티 메인", JLabel.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        header.add(title);
        
        JPanel menuGrid = new JPanel(new GridLayout(0, 2, 16, 16));
        menuGrid.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        
        String[] menus = {
            "자유 게시판", "동네 소식", "동네 질문", "중고 거래",
            "분실물", "소모임", "퀴즈", "랜덤 채팅",
            "공지사항", "랜덤 영상통화"
        };
        
        for (String menu : menus) {
            JButton btn = new JButton(menu);
            btn.setPreferredSize(new Dimension(200, 80));
            btn.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
            btn.addActionListener(e -> {
                if (menu.equals("랜덤 영상통화")) {
                    try {
                        int port = parent.getServerPort();
                        URI uri = new URI("http://localhost:" + port + "/video-call.html");
                        Desktop.getDesktop().browse(uri);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "브라우저를 열 수 없습니다: " + ex.getMessage());
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "아직 구현하지 않은 기능입니다.");
                }
            });
            menuGrid.add(btn);
        }
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(menuGrid, BorderLayout.CENTER);
        
        JScrollPane scrollPane = new JScrollPane(centerPanel);
        scrollPane.setBorder(null);
        
        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }
}
