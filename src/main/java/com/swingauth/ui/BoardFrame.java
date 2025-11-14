package com.swingauth.ui;

import com.swingauth.model.Post;
import com.swingauth.model.User;
import com.swingauth.service.PostService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class BoardFrame extends JFrame {

  private final User user;
  private final String boardName;

  private final DefaultListModel<Post> model = new DefaultListModel<>();
  private final JList<Post> postList = new JList<>(model);
  private final PostService postService = new PostService();

  public BoardFrame(User user, String boardName) {
    this.user = user;
    this.boardName = boardName;

    setTitle(boardName);
    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setSize(720, 520);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    // 상단
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

    // 게시글 리스트
    postList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    postList.setFixedCellHeight(28);
    postList.setBorder(new EmptyBorder(10, 10, 10, 10));

    postList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Post p) setText(p.toString());
        return c;
      }
    });

    add(new JScrollPane(postList), BorderLayout.CENTER);

    // 하단 버튼
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
    JButton btnNew = new JButton("새 글");
    JButton btnOpen = new JButton("열기");
    JButton btnRefresh = new JButton("새로고침");
    JButton btnClose = new JButton("닫기");

    btnNew.addActionListener(e -> openNewPostDialog());
    btnOpen.addActionListener(e -> openSelectedPost());
    btnRefresh.addActionListener(e -> refreshPosts());
    btnClose.addActionListener(e -> dispose());

    bottom.add(btnNew);
    bottom.add(btnOpen);
    bottom.add(btnRefresh);
    bottom.add(btnClose);
    add(bottom, BorderLayout.SOUTH);

    refreshPosts();
  }

  private void refreshPosts() {
    new SwingWorker<List<Post>, Void>() {
      @Override protected List<Post> doInBackground() {
        return postService.listByBoard(user, boardName, 200);
      }
      @Override protected void done() {
        try {
          List<Post> list = get();
          model.clear();
          list.forEach(model::addElement);
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(BoardFrame.this, ex.getMessage());
        }
      }
    }.execute();
  }

  /** 게시글 열기 + 수정 기능 포함 */
  private void openSelectedPost() {
    Post p = postList.getSelectedValue();
    if (p == null) {
      JOptionPane.showMessageDialog(this, "게시글을 선택하세요.");
      return;
    }

    JTextArea area = new JTextArea(p.content == null ? "" : p.content);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);

    JScrollPane sp = new JScrollPane(area);
    sp.setPreferredSize(new Dimension(640, 360));

    // 버튼 구성
    Object[] options;
    if (p.authorUsername.equals(user.username)) {
      options = new Object[]{"수정", "닫기"};
    } else {
      options = new Object[]{"닫기"};
    }

    int result = JOptionPane.showOptionDialog(
        this,
        sp,
        p.title,
        JOptionPane.YES_NO_OPTION,
        JOptionPane.PLAIN_MESSAGE,
        null,
        options,
        options[0]
    );

    // 본인 글이면 수정 가능
    if (p.authorUsername.equals(user.username) && result == 0) {
      openEditPostDialog(p);
    }
  }

  /** 수정 팝업 */
  private void openEditPostDialog(Post p) {
    JTextField tfTitle = new JTextField(p.title);
    JTextArea taContent = new JTextArea(p.content, 10, 40);
    taContent.setLineWrap(true);
    taContent.setWrapStyleWord(true);

    JPanel panel = new JPanel(new BorderLayout(8, 8));
    JPanel north = new JPanel(new BorderLayout(8, 8));
    north.add(new JLabel("제목"), BorderLayout.WEST);
    north.add(tfTitle, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    panel.add(new JScrollPane(taContent), BorderLayout.CENTER);
    panel.setBorder(new EmptyBorder(8, 8, 8, 8));

    int ok = JOptionPane.showConfirmDialog(this, panel, "게시글 수정", JOptionPane.OK_CANCEL_OPTION);
    if (ok != JOptionPane.OK_OPTION) return;

    String newTitle = tfTitle.getText();
    String newContent = taContent.getText();

    new SwingWorker<Void, Void>() {
      @Override protected Void doInBackground() {
        postService.update(p, newTitle, newContent);
        return null;
      }

      @Override protected void done() {
        refreshPosts();
        JOptionPane.showMessageDialog(BoardFrame.this, "수정 완료!");
      }
    }.execute();
  }

  /** 새 글 작성 UI */
  private void openNewPostDialog() {
    JTextField tfTitle = new JTextField();
    JTextArea taContent = new JTextArea(10, 40);
    taContent.setLineWrap(true);
    taContent.setWrapStyleWord(true);

    JPanel panel = new JPanel(new BorderLayout(8, 8));
    JPanel north = new JPanel(new BorderLayout(8, 8));
    north.add(new JLabel("제목"), BorderLayout.WEST);
    north.add(tfTitle, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    panel.add(new JScrollPane(taContent), BorderLayout.CENTER);
    panel.setBorder(new EmptyBorder(8, 8, 8, 8));

    int ok = JOptionPane.showConfirmDialog(this, panel, "새 글 작성", JOptionPane.OK_CANCEL_OPTION);
    if (ok != JOptionPane.OK_OPTION) return;

    String title = tfTitle.getText();
    String content = taContent.getText();

    new SwingWorker<Void, Void>() {
      @Override protected Void doInBackground() {
        postService.create(user, boardName, title, content);
        return null;
      }
      @Override protected void done() {
        refreshPosts();
      }
    }.execute();
  }
}
