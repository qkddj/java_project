package com.swingauth.ui;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.FontMetrics;
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
    
    private ThemeManager() {
        // 전역적으로 버튼 효과 제거 설정
        setupGlobalButtonSettings();
    }
    
    private void setupGlobalButtonSettings() {
        // UIManager를 통해 전역 버튼 설정 - 호버 효과 완전 차단
        UIManager.put("Button.select", UIManager.get("Button.background"));
        UIManager.put("Button.focus", UIManager.get("Button.background"));
        UIManager.put("Button.rollover", false);
        UIManager.put("Button.rolloverEnabled", false);
        UIManager.put("Button.rolloverType", "NONE");
        // 모든 호버 관련 속성 비활성화
        try {
            UIManager.put("Button.mouseHoverColor", null);
            UIManager.put("Button.background", UIManager.get("Button.background"));
        } catch (Exception e) {
            // 일부 L&F에서는 지원하지 않을 수 있음
        }
    }
    
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
     * 버튼의 클릭 시 및 마우스 호버 시 색상 변경 효과를 완전히 제거합니다.
     */
    public static void disableButtonPressedEffect(JButton button) {
        // 버튼에 속성 키를 사용하여 색상 참조 저장
        final String BG_KEY = "themeManager.originalBg";
        final String FG_KEY = "themeManager.originalFg";
        
        // 현재 색상을 속성으로 저장 (테마 변경 시 업데이트 가능)
        button.putClientProperty(BG_KEY, button.getBackground());
        button.putClientProperty(FG_KEY, button.getForeground());
        
        // 모든 호버/롤오버 효과 완전 차단
        button.setRolloverEnabled(false);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        
        // 호버 효과 완전 차단을 위한 추가 설정
        try {
            button.putClientProperty("JButton.buttonType", "textured");
            button.putClientProperty("JComponent.sizeVariant", null);
        } catch (Exception e) {
            // 무시
        }
        
        // UIManager 설정도 개별 버튼에 적용
        UIManager.put("Button.rollover", false);
        
        // 커스텀 UI로 모든 상태 변화 무시 - paint 메서드를 완전히 오버라이드
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            protected void paintButtonPressed(java.awt.Graphics g, javax.swing.AbstractButton b) {
                // pressed 상태 그리기 완전 무시
            }
            
            @Override
            public void update(java.awt.Graphics g, javax.swing.JComponent c) {
                // update 메서드 오버라이드하여 호버 상태 업데이트 차단
                paint(g, c);
            }
            
            @Override
            public void paint(java.awt.Graphics g, javax.swing.JComponent c) {
                // 버튼의 현재 설정된 색상을 사용 (테마 변경 시 업데이트됨)
                AbstractButton b = (AbstractButton) c;
                Color bg = (Color) b.getClientProperty(BG_KEY);
                Color fg = (Color) b.getClientProperty(FG_KEY);
                
                // 속성이 없으면 현재 색상 사용
                if (bg == null) bg = b.getBackground();
                if (fg == null) fg = b.getForeground();
                
                // 호버 상태를 강제로 false로 설정
                if (b.getModel().isRollover()) {
                    try {
                        b.getModel().setRollover(false);
                    } catch (Exception e) {
                        // 무시
                    }
                }
                
                // 배경 그리기 - 항상 설정된 색상 (호버 상태 무시)
                if (bg != null) {
                    g.setColor(bg);
                    g.fillRect(0, 0, c.getWidth(), c.getHeight());
                }
                
                // 텍스트 그리기 - 항상 설정된 색상
                if (b.getText() != null && !b.getText().isEmpty() && fg != null) {
                    g.setColor(fg);
                    FontMetrics fm = g.getFontMetrics(b.getFont());
                    int x = (c.getWidth() - fm.stringWidth(b.getText())) / 2;
                    int y = (c.getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    g.drawString(b.getText(), x, y);
                }
                
                // 테두리 그리기
                if (b.getBorder() != null) {
                    b.getBorder().paintBorder(c, g, 0, 0, c.getWidth(), c.getHeight());
                }
            }
            
            @Override
            protected void paintFocus(java.awt.Graphics g, javax.swing.AbstractButton b, 
                                      java.awt.Rectangle viewRect, java.awt.Rectangle textRect, 
                                      java.awt.Rectangle iconRect) {
                // 포커스 표시 완전 무시
            }
        });
        
        // 모델을 커스텀하여 시각적 효과만 제거 (클릭 이벤트는 정상 작동)
        button.setModel(new javax.swing.DefaultButtonModel() {
            @Override
            public boolean isRollover() {
                return false; // 항상 rollover 상태가 아니도록 (시각적 효과 제거)
            }
            
            @Override
            public boolean isPressed() {
                return false; // pressed 상태도 항상 false (시각적 효과 제거)
            }
            
            @Override
            public void setRollover(boolean b) {
                // rollover 상태 변경은 완전 무시 (호버 효과 제거)
                // 부모 클래스 호출하지 않음
            }
            
            @Override
            public void setPressed(boolean b) {
                // pressed 상태는 실제로 설정하되, UI는 이를 그리지 않음
                // 클릭 이벤트를 위해 부모 클래스는 호출
                super.setPressed(b);
            }
        });
        
        // 마우스 리스너로 모든 효과 완전 차단 - 모든 기존 리스너 제거 후 새로 추가
        java.awt.event.MouseListener[] existingListeners = button.getMouseListeners();
        for (java.awt.event.MouseListener listener : existingListeners) {
            button.removeMouseListener(listener);
        }
        
        java.awt.event.MouseMotionListener[] existingMotionListeners = button.getMouseMotionListeners();
        for (java.awt.event.MouseMotionListener listener : existingMotionListeners) {
            button.removeMouseMotionListener(listener);
        }
        
        // 호버 효과 완전 차단을 위한 마우스 리스너 (이벤트 소비 및 색상 강제 유지)
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                // 호버 이벤트 완전 차단
                e.consume();
                // 색상을 즉시 강제로 원래 색상으로 복원
                Color bg = (Color) button.getClientProperty(BG_KEY);
                Color fg = (Color) button.getClientProperty(FG_KEY);
                if (bg != null) {
                    button.setBackground(bg);
                    button.getModel().setRollover(false);
                }
                if (fg != null) {
                    button.setForeground(fg);
                }
                button.repaint();
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                // 마우스 나갈 때도 색상 유지
                e.consume();
                Color bg = (Color) button.getClientProperty(BG_KEY);
                Color fg = (Color) button.getClientProperty(FG_KEY);
                if (bg != null) button.setBackground(bg);
                if (fg != null) button.setForeground(fg);
                button.getModel().setRollover(false);
                button.repaint();
            }
            
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                // 클릭은 허용하되 색상은 변경하지 않음
                Color bg = (Color) button.getClientProperty(BG_KEY);
                Color fg = (Color) button.getClientProperty(FG_KEY);
                if (bg != null) button.setBackground(bg);
                if (fg != null) button.setForeground(fg);
            }
            
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                // 클릭 해제도 허용하되 색상은 변경하지 않음
                Color bg = (Color) button.getClientProperty(BG_KEY);
                Color fg = (Color) button.getClientProperty(FG_KEY);
                if (bg != null) button.setBackground(bg);
                if (fg != null) button.setForeground(fg);
            }
        });
        
        // 마우스 모션 리스너도 추가하여 호버 효과 완전 차단
        button.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                // 마우스 이동 이벤트 완전 차단 및 색상 강제 유지
                e.consume();
                Color bg = (Color) button.getClientProperty(BG_KEY);
                Color fg = (Color) button.getClientProperty(FG_KEY);
                if (bg != null) {
                    button.setBackground(bg);
                    button.getModel().setRollover(false);
                }
                if (fg != null) button.setForeground(fg);
                button.repaint();
            }
            
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                // 드래그 이벤트도 차단
                e.consume();
            }
        });
        
        // 추가 보호: 주기적으로 색상 확인 및 복원
        javax.swing.Timer colorCheckTimer = new javax.swing.Timer(50, e -> {
            Color bg = (Color) button.getClientProperty(BG_KEY);
            Color fg = (Color) button.getClientProperty(FG_KEY);
            if (bg != null && !button.getBackground().equals(bg)) {
                button.setBackground(bg);
            }
            if (fg != null && !button.getForeground().equals(fg)) {
                button.setForeground(fg);
            }
        });
        colorCheckTimer.setRepeats(true);
        colorCheckTimer.start();
        
        // 버튼이 제거될 때 타이머도 정리
        button.addHierarchyListener(new java.awt.event.HierarchyListener() {
            @Override
            public void hierarchyChanged(java.awt.event.HierarchyEvent e) {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                    if (!button.isDisplayable()) {
                        colorCheckTimer.stop();
                    }
                }
            }
        });
    }
    
    /**
     * 테마 변경 시 버튼의 색상을 업데이트합니다.
     */
    public static void updateButtonColors(JButton button, Color bg, Color fg) {
        button.putClientProperty("themeManager.originalBg", bg);
        button.putClientProperty("themeManager.originalFg", fg);
        button.setBackground(bg);
        button.setForeground(fg);
        button.repaint();
    }
}

