package com.swingauth.ui;

import com.swingauth.model.User;
import com.swingauth.service.AuthService;

import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;

public class AuthFrame extends JFrame implements ThemeManager.ThemeChangeListener {
  private final ThemeManager themeManager = ThemeManager.getInstance();
  private final AuthService auth = new AuthService();
  
  // 테마 적용을 위한 컴포넌트 참조
  private JTabbedPane tabs;
  private JButton themeToggleBtn;
  private JPanel loginPanel;
  private JPanel signUpPanel;
  private JPanel top;
  private int hoveredTabIndex = -1; // 호버된 탭 인덱스 추적

  public AuthFrame() {
    setTitle("로그인 / 회원가입 (MongoDB + Swing)");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(480, 360);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    // ===== 상단: 테마 전환 버튼 (메인화면과 동일한 스타일) =====
    top = new JPanel(new BorderLayout());
    top.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
    top.setOpaque(true);
    
    themeToggleBtn = new JButton("🌙 다크모드");
    themeToggleBtn.setFont(themeToggleBtn.getFont().deriveFont(Font.BOLD, 12f));
    themeToggleBtn.setFocusPainted(false);
    
    // 초기 색상 설정 (현재 테마에 맞게)
    boolean isDarkMode = themeManager.isDarkMode();
    if (isDarkMode) {
      themeToggleBtn.setBackground(ThemeManager.DARK_BG2);
      themeToggleBtn.setForeground(ThemeManager.TEXT_LIGHT);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.DARK_BORDER, 1));
    } else {
      themeToggleBtn.setBackground(ThemeManager.LIGHT_BG2);
      themeToggleBtn.setForeground(ThemeManager.TEXT_DARK);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));
    }
    
    ThemeManager.disableButtonPressedEffect(themeToggleBtn);
    ThemeManager.updateButtonColors(themeToggleBtn, 
        isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2,
        isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
    themeToggleBtn.addActionListener(e -> {
      themeManager.toggleTheme();
    });
    
    top.add(themeToggleBtn, BorderLayout.WEST);
    add(top, BorderLayout.NORTH);

    // ThemeManager에 리스너 등록
    themeManager.addThemeChangeListener(this);

    tabs = new JTabbedPane();
    tabs.setOpaque(true);
    loginPanel = buildLoginPanel();
    signUpPanel = buildSignUpPanel();
    tabs.addTab("로그인", loginPanel);
    tabs.addTab("회원가입", signUpPanel);
    
    // 탭 선택 변경 시 색상 재설정
    tabs.addChangeListener(e -> {
      boolean currentThemeDark = themeManager.isDarkMode();
      Color tabBg = currentThemeDark ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG;
      Color tabFg = currentThemeDark ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
      for (int i = 0; i < tabs.getTabCount(); i++) {
        tabs.setBackgroundAt(i, tabBg);
        tabs.setForegroundAt(i, tabFg);
      }
      tabs.setBackground(tabBg);
      tabs.setForeground(tabFg);
      tabs.repaint();
    });

    add(tabs, BorderLayout.CENTER);

    // 초기 테마 적용
    applyTheme();
    
    // 탭 색상 명시적으로 재설정 (UI 업데이트 후)
    SwingUtilities.invokeLater(() -> {
      boolean currentThemeDark = themeManager.isDarkMode();
      tabs.setBackgroundAt(0, currentThemeDark ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      tabs.setBackgroundAt(1, currentThemeDark ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      tabs.repaint();
    });
  }

  @Override
  public void onThemeChanged() {
    applyTheme();
  }

  private void applyTheme() {
    boolean isDarkMode = themeManager.isDarkMode();
    
    // JOptionPane 배경색 설정
    UIManager.put("OptionPane.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("Panel.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("OptionPane.messageForeground", isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
    
    // TabbedPane 배경색 설정
    UIManager.put("TabbedPane.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("TabbedPane.selected", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("TabbedPane.unselectedBackground", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("TabbedPane.foreground", isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
    UIManager.put("TabbedPane.selectedForeground", isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
    UIManager.put("TabbedPane.selectedBackground", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("TabbedPane.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    
    // 탭 UI 커스터마이징 - 선택 시 시각적 변화 완전 제거
    tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
      @Override
      protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                        int x, int y, int w, int h, boolean isSelected) {
        // 호버 상태에 따라 배경색 결정
        boolean isHovered = (hoveredTabIndex == tabIndex);
        Color bgColor;
        
        if (isDarkMode) {
          // 다크 모드: 호버 시 검은색 (DARK_BG2 또는 더 어두운 색)
          bgColor = isHovered ? ThemeManager.DARK_BG2 : ThemeManager.DARK_BG;
        } else {
          // 라이트 모드: 호버 시 투명 (배경을 그리지 않음)
          bgColor = isHovered ? new Color(0, 0, 0, 0) : ThemeManager.LIGHT_BG;
        }
        
        // 배경 그리기 (투명이 아닌 경우에만)
        if (bgColor.getAlpha() > 0) {
          g.setColor(bgColor);
          g.fillRect(x, y, w, h);
        }
        
        // 탭 배경색도 명시적으로 설정 (즉시 적용)
        if (tabIndex < tabs.getTabCount()) {
          // 투명인 경우 원래 배경색 유지
          Color tabBgColor = isHovered && !isDarkMode ? ThemeManager.LIGHT_BG : bgColor;
          tabs.setBackgroundAt(tabIndex, tabBgColor);
          tabs.setForegroundAt(tabIndex, isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        }
      }
      
      @Override
      protected void paintTab(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                               Rectangle iconRect, Rectangle textRect) {
        Color fgColor = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
        
        // 기본 paintTab 호출하지 않고 직접 그리기 (호버 효과 포함)
        Rectangle tabRect = rects[tabIndex];
        paintTabBackground(g, tabPlacement, tabIndex, tabRect.x, tabRect.y, tabRect.width, tabRect.height, tabIndex == tabs.getSelectedIndex());
        paintText(g, tabPlacement, tabs.getFont(), g.getFontMetrics(tabs.getFont()), tabIndex, tabs.getTitleAt(tabIndex), textRect, tabIndex == tabs.getSelectedIndex());
        
        // 텍스트 색상 유지
        if (tabIndex < tabs.getTabCount()) {
          tabs.setForegroundAt(tabIndex, fgColor);
        }
      }

      @Override
      protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                    int x, int y, int w, int h, boolean isSelected) {
        // 탭 테두리 완전 제거
      }

      @Override
      protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        // 콘텐츠 영역 테두리 및 선택 표시 완전 제거
      }

      @Override
      protected void paintFocusIndicator(Graphics g, int tabPlacement,
                                        Rectangle[] rects, int tabIndex,
                                        Rectangle iconRect, Rectangle textRect,
                                        boolean isSelected) {
        // 포커스 표시 제거
      }

      @Override
      protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                               int tabIndex, String title, Rectangle textRect, boolean isSelected) {
        // 선택 여부와 관계없이 동일한 텍스트 색상 사용 (명시적으로 그리기)
        Color textColor = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
        g.setColor(textColor);
        g.setFont(font);
        int x = textRect.x;
        int y = textRect.y + metrics.getAscent();
        g.drawString(title, x, y);
      }
      
      @Override
      public void paint(Graphics g, JComponent c) {
        // 전체 탭을 그리기 전에 색상 강제 설정
        Color bgColor = isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG;
        Color fgColor = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
        for (int i = 0; i < tabs.getTabCount(); i++) {
          tabs.setBackgroundAt(i, bgColor);
          tabs.setForegroundAt(i, fgColor);
        }
        // 탭 배경도 강제로 설정
        tabs.setBackground(bgColor);
        super.paint(g, c);
        // 그린 후에도 다시 색상 확인
        for (int i = 0; i < tabs.getTabCount(); i++) {
          tabs.setBackgroundAt(i, bgColor);
          tabs.setForegroundAt(i, fgColor);
        }
      }
    });

    // 배경색 설정
    getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    tabs.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    tabs.setOpaque(true);
    
    // 탭 배경색과 텍스트 색상 명시적으로 설정 (모든 탭에 대해)
    Color tabBg = isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG;
    Color tabFg = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
    for (int i = 0; i < tabs.getTabCount(); i++) {
      tabs.setBackgroundAt(i, tabBg);
      tabs.setForegroundAt(i, tabFg);
    }
    // 탭 전체 배경도 설정
    tabs.setBackground(tabBg);
    tabs.setForeground(tabFg);
    
    // 탭 호버 효과를 위한 마우스 리스너
    tabs.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
      @Override
      public void mouseMoved(java.awt.event.MouseEvent e) {
        // 마우스 위치에 해당하는 탭 인덱스 찾기
        int tabIndex = tabs.indexAtLocation(e.getX(), e.getY());
        if (tabIndex != hoveredTabIndex) {
          hoveredTabIndex = tabIndex;
          tabs.repaint();
        }
      }
    });
    
    tabs.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        // 마우스가 탭 영역을 벗어나면 호버 상태 해제
        if (hoveredTabIndex != -1) {
          hoveredTabIndex = -1;
          tabs.repaint();
        }
      }
    });
    
    // 상단 패널 배경색 설정
    if (top != null) {
      top.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    }
    
    // 테마 전환 버튼 스타일
    if (isDarkMode) {
      themeToggleBtn.setText("🌙 다크모드");
      themeToggleBtn.setBackground(ThemeManager.DARK_BG2);
      themeToggleBtn.setForeground(ThemeManager.TEXT_LIGHT);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.DARK_BORDER, 1));
      ThemeManager.updateButtonColors(themeToggleBtn, ThemeManager.DARK_BG2, ThemeManager.TEXT_LIGHT);
    } else {
      themeToggleBtn.setText("☀️ 라이트모드");
      themeToggleBtn.setBackground(ThemeManager.LIGHT_BG2);
      themeToggleBtn.setForeground(ThemeManager.TEXT_DARK);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));
      ThemeManager.updateButtonColors(themeToggleBtn, ThemeManager.LIGHT_BG2, ThemeManager.TEXT_DARK);
    }
    
    // 패널들에 테마 적용
    applyThemeToPanel(loginPanel, isDarkMode);
    applyThemeToPanel(signUpPanel, isDarkMode);
    
    SwingUtilities.updateComponentTreeUI(this);
  }

  private void applyThemeToPanel(JPanel panel, boolean isDarkMode) {
    if (panel == null) return;
    
    panel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    panel.setOpaque(true);
    
    Component[] components = panel.getComponents();
    for (Component comp : components) {
      if (comp instanceof JLabel) {
        JLabel label = (JLabel) comp;
        label.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      } else if (comp instanceof JTextField) {
        JTextField field = (JTextField) comp;
        field.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        field.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        field.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1),
          BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
      } else if (comp instanceof JPasswordField) {
        JPasswordField field = (JPasswordField) comp;
        field.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        field.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        field.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1),
          BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
      } else if (comp instanceof JButton) {
        JButton btn = (JButton) comp;
        if (btn == themeToggleBtn) continue; // 테마 버튼은 이미 처리됨
        
        // 먼저 색상 설정
        Color bg = isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
        Color fg = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setBorder(BorderFactory.createLineBorder(
          isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1
        ));
        btn.setFocusPainted(false);
        // 호버 효과 완전 제거 (색상 설정 후 적용)
        ThemeManager.disableButtonPressedEffect(btn);
        // 테마 변경 시 색상 업데이트
        ThemeManager.updateButtonColors(btn, bg, fg);
      }
    }
  }

  private JPanel buildSignUpPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(8, 8, 8, 8);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1;

    JTextField username = new JTextField();
    JPasswordField password = new JPasswordField();
    JLabel status = new JLabel(" ");
    JButton submit = new JButton("회원가입");
    
    // 초기 색상 설정 (현재 테마에 맞게)
    boolean currentDarkMode = themeManager.isDarkMode();
    submit.setBackground(currentDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
    submit.setForeground(currentDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
    submit.setBorder(BorderFactory.createLineBorder(
        currentDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
    submit.setFocusPainted(false);
    
    ThemeManager.disableButtonPressedEffect(submit);
    ThemeManager.updateButtonColors(submit, 
        currentDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2,
        currentDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);

    int row = 0;

    c.gridx = 0; c.gridy = row; panel.add(new JLabel("아이디"), c);
    c.gridx = 1; c.gridy = row++; panel.add(username, c);

    c.gridx = 0; c.gridy = row; panel.add(new JLabel("비밀번호 (8자 이상)"), c);
    c.gridx = 1; c.gridy = row++; panel.add(password, c);

    c.gridx = 0; c.gridy = row; c.gridwidth = 2;
    panel.add(submit, c);
    row++;

    c.gridx = 0; c.gridy = row; c.gridwidth = 2;
    panel.add(status, c);

    submit.addActionListener(e -> {
      submit.setEnabled(false);
      status.setText("처리 중...");
      SwingWorker<Void, Void> worker = new SwingWorker<>() {
        @Override protected Void doInBackground() {
          try {
            auth.signUp(username.getText(), new String(password.getPassword()));
            status.setText("가입 완료! 이제 로그인해 주세요.");
            status.setForeground(themeManager.isDarkMode() ? ThemeManager.NEON_CYAN : ThemeManager.LIGHT_CYAN);
          } catch (IllegalArgumentException ex) {
            status.setText("입력 오류: " + ex.getMessage());
            status.setForeground(themeManager.isDarkMode() ? ThemeManager.NEON_PINK : ThemeManager.LIGHT_PINK);
          } catch (IllegalStateException ex) {
            status.setText("실패: " + ex.getMessage());
            status.setForeground(themeManager.isDarkMode() ? ThemeManager.NEON_PINK : ThemeManager.LIGHT_PINK);
          } catch (Exception ex) {
            status.setText("서버 오류: " + ex.getMessage());
            status.setForeground(themeManager.isDarkMode() ? ThemeManager.NEON_PINK : ThemeManager.LIGHT_PINK);
          }
          return null;
        }
        @Override protected void done() { submit.setEnabled(true); }
      };
      worker.execute();
    });

    return panel;
  }

  private JPanel buildLoginPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(8, 8, 8, 8);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1;

    JTextField username = new JTextField();
    JPasswordField password = new JPasswordField();
    JLabel status = new JLabel(" ");
    JButton submit = new JButton("로그인");
    
    // 초기 색상 설정 (현재 테마에 맞게)
    boolean currentDarkMode = themeManager.isDarkMode();
    submit.setBackground(currentDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
    submit.setForeground(currentDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
    submit.setBorder(BorderFactory.createLineBorder(
        currentDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
    submit.setFocusPainted(false);
    
    ThemeManager.disableButtonPressedEffect(submit);
    ThemeManager.updateButtonColors(submit, 
        currentDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2,
        currentDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);

    int row = 0;

    c.gridx = 0; c.gridy = row; panel.add(new JLabel("아이디"), c);
    c.gridx = 1; c.gridy = row++; panel.add(username, c);

    c.gridx = 0; c.gridy = row; panel.add(new JLabel("비밀번호"), c);
    c.gridx = 1; c.gridy = row++; panel.add(password, c);

    c.gridx = 0; c.gridy = row; c.gridwidth = 2;
    panel.add(submit, c);
    row++;

    c.gridx = 0; c.gridy = row; c.gridwidth = 2;
    panel.add(status, c);

    submit.addActionListener(e -> {
      submit.setEnabled(false);
      status.setText("로그인 중...");
      SwingWorker<User, Void> worker = new SwingWorker<>() {
        @Override protected User doInBackground() {
          try {
            return auth.login(username.getText(), new String(password.getPassword()));
          } catch (IllegalArgumentException | IllegalStateException ex) {
            // 아이디 없음, 비밀번호 오류, 정지 계정 등
            status.setText("로그인 실패: " + ex.getMessage());
            status.setForeground(themeManager.isDarkMode() ? ThemeManager.NEON_PINK : ThemeManager.LIGHT_PINK);
            return null;
          } catch (Exception ex) {
            status.setText("서버 오류: " + ex.getMessage());
            status.setForeground(themeManager.isDarkMode() ? ThemeManager.NEON_PINK : ThemeManager.LIGHT_PINK);
            return null;
          }
        }
        @Override protected void done() {
          try {
            User u = get();
            if (u != null) {
              // ✅ 메인 화면으로 전환
              SwingUtilities.invokeLater(() -> {
                new MainFrame(u).setVisible(true);
                // 현재 로그인 창 닫기
                Window win = SwingUtilities.getWindowAncestor(panel);
                if (win != null) win.dispose();
              });
            }
          } catch (Exception ignored) {}
          submit.setEnabled(true);
        }
      };
      worker.execute();
    });

    return panel;
  }
}
