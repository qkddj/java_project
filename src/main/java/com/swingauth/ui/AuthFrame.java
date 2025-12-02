package com.swingauth.ui;

import com.swingauth.model.User;
import com.swingauth.service.AuthService;

import javax.swing.*;
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

  public AuthFrame() {
    setTitle("로그인 / 회원가입 (MongoDB + Swing)");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(480, 360);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    // ===== 상단: 테마 전환 버튼 =====
    top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
    top.setOpaque(true);
    themeToggleBtn = new JButton("🌙 다크모드");
    themeToggleBtn.setFont(themeToggleBtn.getFont().deriveFont(Font.BOLD, 12f));
    themeToggleBtn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    themeToggleBtn.setFocusPainted(false);
    ThemeManager.disableButtonPressedEffect(themeToggleBtn);
    themeToggleBtn.addActionListener(e -> {
      themeManager.toggleTheme();
    });
    top.add(themeToggleBtn);
    add(top, BorderLayout.NORTH);

    // ThemeManager에 리스너 등록
    themeManager.addThemeChangeListener(this);

    tabs = new JTabbedPane();
    loginPanel = buildLoginPanel();
    signUpPanel = buildSignUpPanel();
    tabs.addTab("로그인", loginPanel);
    tabs.addTab("회원가입", signUpPanel);

    add(tabs, BorderLayout.CENTER);

    // 초기 테마 적용
    applyTheme();
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
    
    // 탭 UI 커스터마이징 - 선택 시 시각적 변화 완전 제거
    tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
      @Override
      protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                        int x, int y, int w, int h, boolean isSelected) {
        // 선택 여부와 관계없이 동일한 배경색 사용
        g.setColor(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        g.fillRect(x, y, w, h);
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
        // 선택 여부와 관계없이 동일한 텍스트 색상 사용
        g.setColor(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        super.paintText(g, tabPlacement, font, metrics, tabIndex, title, textRect, isSelected);
      }
    });

    // 배경색 설정
    getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    tabs.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    
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
    } else {
      themeToggleBtn.setText("☀️ 라이트모드");
      themeToggleBtn.setBackground(ThemeManager.LIGHT_BG2);
      themeToggleBtn.setForeground(ThemeManager.TEXT_DARK);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));
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
        
        btn.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        btn.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        btn.setBorder(BorderFactory.createLineBorder(
          isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1
        ));
        btn.setFocusPainted(false);
        ThemeManager.disableButtonPressedEffect(btn);
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
    ThemeManager.disableButtonPressedEffect(submit);

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
