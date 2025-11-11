package com.swingauth.ui;

import com.swingauth.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class BoardFrame extends JFrame {

  private final User user;
  private final String boardName;

  public BoardFrame(User user, String boardName) {
    this.user = user;
    this.boardName = boardName;

    setTitle(boardName);
    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setSize(720, 520);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    // 상단: 보드명 + 사용자/지역 표시
    JPanel top = new JPanel(new BorderLayout());
    top.setBorder(new EmptyBorder(10, 12, 10, 12));
    JLabel title = new JLabel(boardName);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

    String neighborhood = (user.neighborhood != null && !user.neighborhood.isBlank())
        ? user.neighborhood : "unknown";
    JLabel who = new JLabel(user.username + " (" + neighborhood + ")");

    top.add(title, BorderLayout.WEST);
    top.add(who, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);

    // 가운데: 게시글 리스트 (데모용)
    DefaultListModel<String> model = new DefaultListModel<>();
    for (int i = 1; i <= 20; i++) {
      model.addElement(String.format("[%s] 샘플 글 %02d", boardName, i));
    }
    JList<String> postList = new JList<>(model);
    postList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    postList.setFixedCellHeight(28);
    postList.setBorder(new EmptyBorder(10, 10, 10, 10));
    add(new JScrollPane(postList), BorderLayout.CENTER);

    // 하단: 새 글 / 열기 / 닫기 버튼
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
    JButton btnNew = new JButton("새 글");
    JButton btnOpen = new JButton("열기");
    JButton btnClose = new JButton("닫기");

    btnNew.addActionListener(e -> JOptionPane.showMessageDialog(
        this, "새 글 작성 UI는 추후 연결 예정", "알림", JOptionPane.INFORMATION_MESSAGE));

    btnOpen.addActionListener(e -> {
      String sel = postList.getSelectedValue();
      if (sel == null) {
        JOptionPane.showMessageDialog(this, "게시글을 선택하세요.", "알림", JOptionPane.INFORMATION_MESSAGE);
        return;
      }
      JOptionPane.showMessageDialog(this, sel + "\n\n(본문 보기/댓글 UI는 추후 연결 예정)",
          "게시글 열기", JOptionPane.INFORMATION_MESSAGE);
    });

    btnClose.addActionListener(e -> dispose());

    bottom.add(btnNew);
    bottom.add(btnOpen);
    bottom.add(btnClose);
    add(bottom, BorderLayout.SOUTH);
  }
}
