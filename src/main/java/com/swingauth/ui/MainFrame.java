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

  private final User user;
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

    // ===== 상단: 아이디(지역) + 로그아웃 =====
    JPanel top = new JPanel(new BorderLayout());
    top.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

    String neighborhood = (user.neighborhood != null && !user.neighborhood.isBlank())
        ? user.neighborhood : "unknown";
    JLabel idAndLoc = new JLabel(user.username + " (" + neighborhood + ")");
    idAndLoc.setFont(idAndLoc.getFont().deriveFont(Font.BOLD, 14f));

    JButton logout = new JButton("로그아웃");
    logout.addActionListener(e -> {
      SwingUtilities.invokeLater(() -> new AuthFrame().setVisible(true));
      dispose();
    });

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    right.add(idAndLoc);
    right.add(logout);

    top.add(right, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);

    // ===== 중앙: 게시판 리스트 (선택 가능) =====
    JPanel centerWrap = new JPanel(new GridBagLayout());
    JPanel boardBox = new JPanel(new BorderLayout());
    boardBox.setBorder(new LineBorder(NEON_CYAN, 2, true));
    boardBox.setBackground(DARK_BG2);
    boardBox.setPreferredSize(new Dimension(360, 320));

    JList<String> list = new JList<>(boards);
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

    JScrollPane scroll = new JScrollPane(list);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    boardBox.add(scroll, BorderLayout.CENTER);

    // 하단: 선택된 게시판 열기 버튼
    JPanel openBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
    JButton btnOpen = new JButton("열기");
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
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 10));
    JButton btnChat = new JButton("랜덤 채팅");
    JButton btnVideo = new JButton("랜덤 영상 통화");

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
  }

  private void openSelectedBoard(String boardName) {
    // 새 창(프레임)으로 해당 게시판 열기
    SwingUtilities.invokeLater(() -> new BoardFrame(user, boardName).setVisible(true));
  }
}
