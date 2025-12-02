package com.swingauth.ui;

import javax.swing.*;
import java.awt.*;

public class RatingDialog extends JDialog implements ThemeManager.ThemeChangeListener {
    private final ThemeManager themeManager = ThemeManager.getInstance();
    
    private int selectedRating = 0;
    private JButton[] ratingButtons;
    private JButton submitButton;
    private JButton skipButton;
    private JLabel instructionLabel;
    private JLabel descriptionLabel;
    private JPanel topPanel;
    private JPanel centerPanel;
    private JPanel bottomPanel;
    private boolean submitted = false;

    public RatingDialog(JFrame parent) {
        super(parent, "채팅 평점", true);
        setSize(400, 300);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        // 상단: 안내 문구
        topPanel = new JPanel();
        topPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        instructionLabel = new JLabel("이번 채팅에 대한 평점을 남겨주세요");
        instructionLabel.setFont(instructionLabel.getFont().deriveFont(Font.BOLD, 14f));
        topPanel.add(instructionLabel);
        add(topPanel, BorderLayout.NORTH);

        // 중앙: 평점 버튼들
        centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // 버튼들을 담을 패널 (FlowLayout 사용하여 균등한 간격)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        
        ratingButtons = new JButton[5];
        for (int i = 0; i < 5; i++) {
            final int rating = i + 1;
            JButton btn = new JButton(String.valueOf(rating));
            btn.setPreferredSize(new Dimension(50, 50));
            btn.setMinimumSize(new Dimension(50, 50));
            btn.setMaximumSize(new Dimension(50, 50));
            btn.setFont(btn.getFont().deriveFont(Font.BOLD, 16f));
            ThemeManager.disableButtonPressedEffect(btn);
            
            // 클릭 이벤트
            btn.addActionListener(e -> selectRating(rating));
            
            buttonPanel.add(btn);
            ratingButtons[i] = btn;
        }
        
        centerPanel.add(buttonPanel, BorderLayout.CENTER);

        // 평점 설명 레이블
        descriptionLabel = new JLabel("1점(매우 불만족) ~ 5점(매우 만족)");
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(12f));
        descriptionLabel.setHorizontalAlignment(JLabel.CENTER);
        centerPanel.add(descriptionLabel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        // 하단: 제출 및 건너뛰기 버튼
        bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        
        skipButton = new JButton("건너뛰기");
        skipButton.setPreferredSize(new Dimension(100, 35));
        ThemeManager.disableButtonPressedEffect(skipButton);
        skipButton.addActionListener(e -> skipRating());
        
        submitButton = new JButton("제출");
        submitButton.setPreferredSize(new Dimension(100, 35));
        submitButton.setEnabled(false);
        ThemeManager.disableButtonPressedEffect(submitButton);
        submitButton.addActionListener(e -> submitRating());
        
        bottomPanel.add(skipButton);
        bottomPanel.add(submitButton);
        add(bottomPanel, BorderLayout.SOUTH);
        
        // ThemeManager에 리스너 등록
        themeManager.addThemeChangeListener(this);
        
        // 초기 테마 적용
        applyTheme();
    }
    
    @Override
    public void onThemeChanged() {
        applyTheme();
    }
    
    private void applyTheme() {
        boolean isDarkMode = themeManager.isDarkMode();
        
        getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        topPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        centerPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        bottomPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        instructionLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        descriptionLabel.setForeground(isDarkMode ? ThemeManager.TEXT_DIM : ThemeManager.LIGHT_BORDER);
        
        // 평점 버튼들
        for (int i = 0; i < ratingButtons.length; i++) {
            JButton btn = ratingButtons[i];
            if (i < selectedRating) {
                // 선택된 평점까지 강조 표시
                btn.setBackground(isDarkMode ? ThemeManager.NEON_CYAN : ThemeManager.LIGHT_CYAN);
                btn.setForeground(isDarkMode ? ThemeManager.DARK_BG : Color.WHITE);
                btn.setBorder(BorderFactory.createLineBorder(
                    isDarkMode ? ThemeManager.NEON_CYAN : ThemeManager.LIGHT_CYAN, 3));
            } else {
                // 선택되지 않은 버튼
                btn.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
                btn.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
                btn.setBorder(BorderFactory.createLineBorder(
                    isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 2));
            }
        }
        
        // 제출 및 건너뛰기 버튼
        submitButton.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        submitButton.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        submitButton.setBorder(BorderFactory.createLineBorder(
            isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
        
        skipButton.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        skipButton.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        skipButton.setBorder(BorderFactory.createLineBorder(
            isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
        
        SwingUtilities.updateComponentTreeUI(this);
    }

    private void selectRating(int rating) {
        selectedRating = rating;
        
        // 테마 적용 (버튼 스타일 업데이트 포함)
        applyTheme();
        
        submitButton.setEnabled(true);
    }

    private void submitRating() {
        if (selectedRating > 0) {
            submitted = true;
            dispose();
        }
    }
    
    private void skipRating() {
        // 건너뛰기를 선택하면 평점 없이 창 닫기
        selectedRating = 0;
        submitted = true;
        dispose();
    }

    /**
     * 평점 다이얼로그를 표시하고 선택된 평점을 반환
     * @return 선택된 평점 (1-5), 건너뛰기/취소 시 0
     */
    public int showRatingDialog() {
        setVisible(true);
        return submitted ? selectedRating : 0;
    }
}

