package com.swingauth.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {
    private static ThemeManager instance;
    
    // 다크 테마 색상
    public static final Color NEON_CYAN = new Color(0, 255, 255);
    public static final Color NEON_PINK = new Color(255, 0, 128);
    public static final Color DARK_BG = new Color(18, 18, 24);
    public static final Color DARK_BG2 = new Color(28, 28, 36);
    public static final Color DARK_BORDER = new Color(60, 60, 80);
    public static final Color TEXT_LIGHT = new Color(240, 240, 255);
    public static final Color TEXT_DIM = new Color(160, 160, 180);
    
    // 라이트 테마 색상
    public static final Color LIGHT_BG = new Color(245, 245, 250);
    public static final Color LIGHT_BG2 = new Color(255, 255, 255);
    public static final Color LIGHT_BORDER = new Color(200, 200, 220);
    public static final Color TEXT_DARK = new Color(30, 30, 40);
    public static final Color LIGHT_CYAN = new Color(0, 180, 200);
    public static final Color LIGHT_PINK = new Color(200, 0, 100);
    
    private boolean isDarkMode = true;
    private List<ThemeChangeListener> listeners = new ArrayList<>();
    
    private ThemeManager() {}
    
    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }
    
    public boolean isDarkMode() {
        return isDarkMode;
    }
    
    public void setDarkMode(boolean darkMode) {
        if (this.isDarkMode != darkMode) {
            this.isDarkMode = darkMode;
            notifyThemeChanged();
        }
    }
    
    public void toggleTheme() {
        setDarkMode(!isDarkMode);
    }
    
    public interface ThemeChangeListener {
        void onThemeChanged();
    }
    
    public void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }
    
    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyThemeChanged() {
        for (ThemeChangeListener listener : listeners) {
            listener.onThemeChanged();
        }
    }
    
    /**
     * 버튼의 클릭 시 색상 변경 효과를 제거합니다.
     */
    public static void disableButtonPressedEffect(JButton button) {
        button.setRolloverEnabled(false);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            protected void paintButtonPressed(java.awt.Graphics g, javax.swing.AbstractButton b) {
                // pressed 상태 그리기 무시
            }
        });
        // 모델을 커스텀하여 rollover 상태 무시
        button.setModel(new javax.swing.DefaultButtonModel() {
            @Override
            public boolean isRollover() {
                return false; // 항상 rollover 상태가 아니도록
            }
        });
    }
}

