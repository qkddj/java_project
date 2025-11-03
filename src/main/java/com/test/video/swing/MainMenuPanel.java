package com.test.video.swing;

import javax.swing.*;
import java.awt.*;

public class MainMenuPanel extends JPanel {
    @SuppressWarnings("unused")
    private SwingMain parent;
    
    public MainMenuPanel(SwingMain parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        
        // 헤더
        JPanel header = new JPanel();
        header.setBackground(new Color(43, 100, 255));
        header.setPreferredSize(new Dimension(0, 60));
        JLabel title = new JLabel("커뮤니티 메인", JLabel.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        header.add(title);
        
        // 메뉴 그리드
        JPanel menuGrid = new JPanel(new GridLayout(0, 2, 16, 16));
        menuGrid.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        
        String[] menus = {
            "자유 게시판", "동네 소식", "동네 질문", "중고 거래",
            "분실물", "소모임", "퀴즈", "랜덤 채팅",
            "공지사항"
        };
        
        for (String menu : menus) {
            JButton btn = new JButton(menu);
            btn.setPreferredSize(new Dimension(200, 80));
            btn.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
            btn.addActionListener(e -> {
                if (menu.equals("랜덤 채팅")) {
                    JOptionPane.showMessageDialog(this, "준비 중입니다.");
                }
            });
            menuGrid.add(btn);
        }
        
        // 영상통화 버튼
        JButton videoBtn = new JButton("랜덤 영상통화");
        videoBtn.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        videoBtn.setBackground(new Color(43, 100, 255));
        videoBtn.setForeground(Color.WHITE);
        videoBtn.setPreferredSize(new Dimension(300, 50));
        videoBtn.addActionListener(e -> parent.showVideoCall());
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(menuGrid, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(videoBtn);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        JScrollPane scrollPane = new JScrollPane(centerPanel);
        scrollPane.setBorder(null);
        
        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }
}

