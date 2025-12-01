package com.swingauth.ui;

import com.swingauth.model.User;
import com.swingauth.service.AuthService;

import javax.swing.*;
import java.awt.*;

public class AuthFrame extends JFrame {
  // 사이버펑크 네온 다크 테마 색상
  private static final Color NEON_CYAN = new Color(0, 255, 255);
  private static final Color NEON_GREEN = new Color(57, 255, 20);
  private static final Color NEON_PINK = new Color(255, 0, 128);
  
  private final AuthService auth = new AuthService();

  public AuthFrame() {
    setTitle("로그인 / 회원가입 (MongoDB + Swing)");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(480, 360);
    setLocationRelativeTo(null);

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("로그인", buildLoginPanel());
    tabs.addTab("회원가입", buildSignUpPanel());

    setContentPane(tabs);
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

    int row = 0;

    c.gridx = 0; c.gridy = row; panel.add(new JLabel("아이디"), c);
    c.gridx = 1; c.gridy = row++; panel.add(username, c);

    c.gridx = 0; c.gridy = row; panel.add(new JLabel("비밀번호 (8자 이상)"), c);
    c.gridx = 1; c.gridy = row++; panel.add(password, c);

    c.gridx = 0; c.gridy = row; c.gridwidth = 2;
    panel.add(submit, c);
    row++;

    c.gridx = 0; c.gridy = row; c.gridwidth = 2;
    status.setForeground(NEON_CYAN);
    panel.add(status, c);

    submit.addActionListener(e -> {
      submit.setEnabled(false);
      status.setText("처리 중...");
      SwingWorker<Void, Void> worker = new SwingWorker<>() {
        @Override protected Void doInBackground() {
          try {
            auth.signUp(username.getText(), new String(password.getPassword()));
            status.setText("가입 완료! 이제 로그인해 주세요.");
            status.setForeground(NEON_GREEN);
          } catch (IllegalArgumentException ex) {
            status.setText("입력 오류: " + ex.getMessage());
            status.setForeground(NEON_PINK);
          } catch (IllegalStateException ex) {
            status.setText("실패: " + ex.getMessage());
            status.setForeground(NEON_PINK);
          } catch (Exception ex) {
            status.setText("서버 오류: " + ex.getMessage());
            status.setForeground(NEON_PINK);
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
          } catch (IllegalArgumentException ex) {
            status.setText("로그인 실패: " + ex.getMessage());
            status.setForeground(NEON_PINK);
            return null;
          } catch (Exception ex) {
            status.setText("서버 오류: " + ex.getMessage());
            status.setForeground(NEON_PINK);
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
