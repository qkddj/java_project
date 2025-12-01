package com.swingauth.ui;

import com.swingauth.model.User;
import io.socket.client.Socket;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MainFrame extends JFrame {

  // 사이버펑크 네온 다크 테마 색상
  private static final Color NEON_CYAN = new Color(0, 255, 255);
  private static final Color NEON_PINK = new Color(255, 0, 128);
  private static final Color DARK_BG = new Color(18, 18, 24);
  private static final Color DARK_BG2 = new Color(28, 28, 36);
  private static final Color DARK_BORDER = new Color(60, 60, 80);
  private static final Color TEXT_LIGHT = new Color(240, 240, 255);

  // 라이트 테마 색상
  private static final Color LIGHT_BG = new Color(245, 245, 250);
  private static final Color LIGHT_BG2 = new Color(255, 255, 255);
  private static final Color LIGHT_BORDER = new Color(200, 200, 220);
  private static final Color TEXT_DARK = new Color(30, 30, 40);
  private static final Color LIGHT_CYAN = new Color(0, 180, 200);
  private static final Color LIGHT_PINK = new Color(200, 0, 100);

  private boolean isDarkMode = true;
  private final User user;
  
  // 테마 적용을 위한 컴포넌트 참조
  private JPanel top;
  private JPanel right;
  private JLabel idAndLoc;
  private JButton logout;
  private JPanel centerWrap;
  private JPanel boardBox;
  private JList<String> list;
  private JScrollPane scroll;
  private JPanel openBar;
  private JButton btnOpen;
  private JPanel bottom;
  private JButton btnChat;
  private JButton btnVideo;
  private JButton themeToggleBtn;
  private final String[] boards = {
      "자유 게시판",
      "동네 소식 게시판",
      "동네 질문 게시판",
      "중고 거래 게시판",
      "분실물 게시판",
      "소모임 게시판",
      "퀴즈 게시판"
  };

  public MainFrame(User user) {
    this.user = user;
    setTitle("메인 화면");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(560, 520);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    // ===== 상단: 테마 전환 버튼 + 아이디(지역) + 로그아웃 =====
    top = new JPanel(new BorderLayout());
    top.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

    // 좌측 상단: 테마 전환 버튼
    themeToggleBtn = new JButton("🌙 다크모드");
    themeToggleBtn.setFont(themeToggleBtn.getFont().deriveFont(Font.BOLD, 12f));
    themeToggleBtn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    themeToggleBtn.setFocusPainted(false);
    themeToggleBtn.addActionListener(e -> {
      isDarkMode = !isDarkMode;
      applyTheme();
    });

    String neighborhood = (user.neighborhood != null && !user.neighborhood.isBlank())
        ? user.neighborhood : "unknown";
    idAndLoc = new JLabel(user.username + " (" + neighborhood + ")");
    idAndLoc.setFont(idAndLoc.getFont().deriveFont(Font.BOLD, 14f));

    logout = new JButton("로그아웃");
    logout.addActionListener(e -> {
      SwingUtilities.invokeLater(() -> new AuthFrame().setVisible(true));
      dispose();
    });

    right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    right.add(idAndLoc);
    right.add(logout);

    top.add(themeToggleBtn, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);

    // ===== 중앙: 게시판 리스트 (선택 가능) =====
    centerWrap = new JPanel(new GridBagLayout());
    boardBox = new JPanel(new BorderLayout());
    boardBox.setBorder(new LineBorder(NEON_CYAN, 2, true));
    boardBox.setBackground(DARK_BG2);
    boardBox.setPreferredSize(new Dimension(360, 320));

    list = new JList<>(boards);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setFont(list.getFont().deriveFont(16f));
    list.setFixedCellHeight(36);
    list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // 엔터/더블클릭으로 열기
    list.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && list.getSelectedIndex() >= 0) {
          openSelectedBoard(list.getSelectedValue());
        }
      }
    });
    list.addKeyListener(new java.awt.event.KeyAdapter() {
      @Override public void keyPressed(java.awt.event.KeyEvent e) {
        if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && list.getSelectedIndex() >= 0) {
          openSelectedBoard(list.getSelectedValue());
        }
      }
    });

    scroll = new JScrollPane(list);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    boardBox.add(scroll, BorderLayout.CENTER);

    // 하단: 선택된 게시판 열기 버튼
    openBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
    btnOpen = new JButton("열기");
    btnOpen.addActionListener(e -> {
      String sel = list.getSelectedValue();
      if (sel == null) {
        JOptionPane.showMessageDialog(this, "게시판을 선택하세요.", "알림", JOptionPane.INFORMATION_MESSAGE);
        return;
      }
      openSelectedBoard(sel);
    });
    openBar.add(btnOpen);
    boardBox.add(openBar, BorderLayout.SOUTH);

    centerWrap.add(boardBox);
    add(centerWrap, BorderLayout.CENTER);

    // ===== 하단: 랜덤 채팅 / 랜덤 영상 통화 =====
    bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 10));
    btnChat = new JButton("랜덤 채팅");
    btnVideo = new JButton("랜덤 영상 통화");

    // 네온 스타일 버튼
    btnChat.setBackground(NEON_CYAN);
    btnChat.setForeground(DARK_BG);
    btnChat.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    btnChat.setFocusPainted(false);
    
    btnVideo.setBackground(NEON_PINK);
    btnVideo.setForeground(Color.WHITE);
    btnVideo.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    btnVideo.setFocusPainted(false);

    btnChat.addActionListener(e -> {
        MatchingFrame[] matchingFrameRef = new MatchingFrame[1];
        matchingFrameRef[0] = new MatchingFrame(user, () -> {
            // 매칭 완료 시 채팅 화면 열기 (소켓 전달)
            SwingUtilities.invokeLater(() -> {
                Socket socket = matchingFrameRef[0].getSocket();
                String partnerUsername = matchingFrameRef[0].getPartnerUsername();
                new RandomChatFrame(socket, user, partnerUsername).setVisible(true);
            });
        });
        matchingFrameRef[0].setVisible(true);
    });
    btnVideo.addActionListener(e -> {
        SwingUtilities.invokeLater(() -> {
            new VideoCallFrame(user);
        });
    });

    bottom.add(btnChat);
    bottom.add(btnVideo);
    add(bottom, BorderLayout.SOUTH);

    // 초기 테마 적용
    applyTheme();
  }

  private void openSelectedBoard(String boardName) {
    // 새 창(프레임)으로 해당 게시판 열기
    SwingUtilities.invokeLater(() -> new BoardFrame(user, boardName).setVisible(true));
  }

  private void applyTheme() {
    if (isDarkMode) {
      // 다크모드 적용
      getContentPane().setBackground(DARK_BG);
      top.setBackground(DARK_BG);
      right.setBackground(DARK_BG);
      idAndLoc.setForeground(TEXT_LIGHT);
      logout.setBackground(DARK_BG2);
      logout.setForeground(TEXT_LIGHT);
      logout.setBorder(BorderFactory.createLineBorder(DARK_BORDER, 1));
      
      centerWrap.setBackground(DARK_BG);
      boardBox.setBorder(new LineBorder(NEON_CYAN, 2, true));
      boardBox.setBackground(DARK_BG2);
      
      list.setBackground(DARK_BG2);
      list.setForeground(TEXT_LIGHT);
      list.setSelectionBackground(NEON_CYAN);
      list.setSelectionForeground(DARK_BG);
      
      scroll.setBackground(DARK_BG2);
      scroll.getViewport().setBackground(DARK_BG2);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      
      openBar.setBackground(DARK_BG2);
      btnOpen.setBackground(DARK_BG);
      btnOpen.setForeground(TEXT_LIGHT);
      btnOpen.setBorder(BorderFactory.createLineBorder(DARK_BORDER, 1));
      
      bottom.setBackground(DARK_BG);
      btnChat.setBackground(NEON_CYAN);
      btnChat.setForeground(DARK_BG);
      btnVideo.setBackground(NEON_PINK);
      btnVideo.setForeground(Color.WHITE);
      
      themeToggleBtn.setText("🌙 다크모드");
      themeToggleBtn.setBackground(DARK_BG2);
      themeToggleBtn.setForeground(TEXT_LIGHT);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(DARK_BORDER, 1));
    } else {
      // 라이트모드 적용
      getContentPane().setBackground(LIGHT_BG);
      top.setBackground(LIGHT_BG);
      right.setBackground(LIGHT_BG);
      idAndLoc.setForeground(TEXT_DARK);
      logout.setBackground(LIGHT_BG2);
      logout.setForeground(TEXT_DARK);
      logout.setBorder(BorderFactory.createLineBorder(LIGHT_BORDER, 1));
      
      centerWrap.setBackground(LIGHT_BG);
      boardBox.setBorder(new LineBorder(LIGHT_CYAN, 2, true));
      boardBox.setBackground(LIGHT_BG2);
      
      list.setBackground(LIGHT_BG2);
      list.setForeground(TEXT_DARK);
      list.setSelectionBackground(LIGHT_CYAN);
      list.setSelectionForeground(Color.WHITE);
      
      scroll.setBackground(LIGHT_BG2);
      scroll.getViewport().setBackground(LIGHT_BG2);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      
      openBar.setBackground(LIGHT_BG2);
      btnOpen.setBackground(LIGHT_BG);
      btnOpen.setForeground(TEXT_DARK);
      btnOpen.setBorder(BorderFactory.createLineBorder(LIGHT_BORDER, 1));
      
      bottom.setBackground(LIGHT_BG);
      btnChat.setBackground(LIGHT_CYAN);
      btnChat.setForeground(Color.WHITE);
      btnVideo.setBackground(LIGHT_PINK);
      btnVideo.setForeground(Color.WHITE);
      
      themeToggleBtn.setText("☀️ 라이트모드");
      themeToggleBtn.setBackground(LIGHT_BG2);
      themeToggleBtn.setForeground(TEXT_DARK);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(LIGHT_BORDER, 1));
    }
    
    // 스크롤바 스타일도 적용
    UIManager.put("ScrollBar.background", isDarkMode ? DARK_BG2 : LIGHT_BG2);
    UIManager.put("ScrollBar.thumb", isDarkMode ? DARK_BORDER : LIGHT_BORDER);
    SwingUtilities.updateComponentTreeUI(this);
  }
}
