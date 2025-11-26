package com.swingauth.ui;

import com.swingauth.model.User;
import io.socket.client.Socket;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MainFrame extends JFrame {

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
    boardBox.setBorder(new LineBorder(Color.BLACK, 3, true));
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

    btnChat.setBorder(new LineBorder(Color.BLACK, 2, true));
    btnVideo.setBorder(new LineBorder(Color.BLACK, 2, true));

    btnChat.addActionListener(e -> {
        MatchingFrame[] matchingFrameRef = new MatchingFrame[1];
        matchingFrameRef[0] = new MatchingFrame(() -> {
            // 매칭 완료 시 채팅 화면 열기 (소켓 전달)
            SwingUtilities.invokeLater(() -> {
                Socket socket = matchingFrameRef[0].getSocket();
                new RandomChatFrame(socket).setVisible(true);
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
