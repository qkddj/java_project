package com.swingauth.ui;

import com.swingauth.comment.CommentService;
import com.swingauth.model.Post;
import com.swingauth.model.User;
import com.swingauth.service.PostService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BoardFrame extends JFrame {

  private final User user;
  private final String boardName;

  private final PostService postService = new PostService();
  private final CommentService commentService = new CommentService();

  private JTextField searchField;
  private JPanel cardsPanel;
  private JScrollPane scrollPane;

  private boolean loading = false;
  private boolean noMore = false;
  private int loadedCount = 0;
  private static final int PAGE_SIZE = 10;
  private String currentKeyword = "";

  private static class CardData {
    Post post;
    int commentCount;
    int likesCount;
    CardData(Post p, int cc, int lc) {
      this.post = p;
      this.commentCount = cc;
      this.likesCount = lc;
    }
  }

  public BoardFrame(User user, String boardName) {
    this.user = user;
    this.boardName = boardName;

    setTitle(boardName);
    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setSize(800, 600);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    // 상단: 제목 + 유저/지역
    JPanel top = new JPanel(new BorderLayout());
    top.setBorder(new EmptyBorder(10, 12, 0, 12));

    JLabel title = new JLabel(boardName);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

    String neighborhood = (user.neighborhood != null && !user.neighborhood.isBlank())
        ? user.neighborhood : "unknown";
    JLabel who = new JLabel(user.username + " (" + neighborhood + ")");

    top.add(title, BorderLayout.WEST);
    top.add(who, BorderLayout.EAST);

    // 검색 + 글쓰기 영역
    JPanel searchPanel = new JPanel(new BorderLayout(8, 8));
    searchPanel.setBorder(new EmptyBorder(8, 0, 8, 0));

    searchField = new JTextField();
    JButton btnSearch = new JButton("검색");
    JButton btnNew = new JButton("글쓰기");

    btnSearch.addActionListener(e -> {
      currentKeyword = searchField.getText();
      resetAndLoad();
    });

    searchField.addActionListener(e -> {
      currentKeyword = searchField.getText();
      resetAndLoad();
    });

    btnNew.addActionListener(e -> openNewPostDialog());

    searchPanel.add(searchField, BorderLayout.CENTER);

    JPanel spRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    spRight.add(btnSearch);
    spRight.add(btnNew);
    searchPanel.add(spRight, BorderLayout.EAST);

    JPanel northWrap = new JPanel(new BorderLayout());
    northWrap.add(top, BorderLayout.NORTH);
    northWrap.add(searchPanel, BorderLayout.SOUTH);
    add(northWrap, BorderLayout.NORTH);

    // 카드 목록 패널 (스크롤 안)
    cardsPanel = new JPanel();
    cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
    cardsPanel.setBorder(new EmptyBorder(8, 12, 8, 12));

    scrollPane = new JScrollPane(cardsPanel);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    add(scrollPane, BorderLayout.CENTER);

    // 스크롤 페이징: 끝 근처 도달 시 loadMore()
    scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
      if (loading || noMore) return;
      JScrollBar sb = scrollPane.getVerticalScrollBar();
      int value = sb.getValue();
      int extent = sb.getVisibleAmount();
      int max = sb.getMaximum();
      if (value + extent >= max - 50) {
        loadMore();
      }
    });

    // 하단 닫기 버튼 정도
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
    JButton btnRefresh = new JButton("새로고침");
    JButton btnClose = new JButton("닫기");
    btnRefresh.addActionListener(e -> resetAndLoad());
    btnClose.addActionListener(e -> dispose());
    bottom.add(btnRefresh);
    bottom.add(btnClose);
    add(bottom, BorderLayout.SOUTH);

    // 첫 로딩
    resetAndLoad();
  }

  private void resetAndLoad() {
    loadedCount = 0;
    noMore = false;
    cardsPanel.removeAll();
    cardsPanel.revalidate();
    cardsPanel.repaint();
    loadMore();
  }

  private void loadMore() {
    loading = true;
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    new SwingWorker<List<CardData>, Void>() {
      @Override
      protected List<CardData> doInBackground() {
        List<CardData> result = new ArrayList<>();
        List<Post> posts = postService.listByBoard(user, boardName, currentKeyword, loadedCount, PAGE_SIZE);
        for (Post p : posts) {
          int cc = commentService.countByPostId(p.id);
          int lc = (p.likesCount != null) ? p.likesCount : 0;
          result.add(new CardData(p, cc, lc));
        }
        return result;
      }

      @Override
      protected void done() {
        try {
          List<CardData> data = get();
          if (data.isEmpty()) {
            noMore = true;
          } else {
            for (CardData cd : data) {
              addPostCard(cd);
            }
            loadedCount += data.size();
            if (data.size() < PAGE_SIZE) {
              noMore = true;
            }
          }
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(BoardFrame.this,
              "게시글 로드 실패: " + ex.getMessage(),
              "오류",
              JOptionPane.ERROR_MESSAGE);
        } finally {
          cardsPanel.revalidate();
          cardsPanel.repaint();
          setCursor(Cursor.getDefaultCursor());
          loading = false;
        }
      }
    }.execute();
  }

  /** 게시글 카드 UI 생성 */
  private void addPostCard(CardData data) {
    Post p = data.post;

    JPanel card = new JPanel(new BorderLayout(8, 4));
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Color.LIGHT_GRAY),
        new EmptyBorder(8, 8, 8, 8)
    ));
    card.setBackground(Color.WHITE);

    // 🔹 카드 높이 고정 (원하는 높이로 조절 가능)
    int CARD_HEIGHT = 80; // << 여기 숫자 바꾸면 높이 바뀜
    card.setPreferredSize(new Dimension(10, CARD_HEIGHT));
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT)); // 폭은 쭉, 높이는 고정

    // 제목
    JLabel titleLabel = new JLabel(p.title != null ? p.title : "(제목 없음)");
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));

    // 본문 요약 (30자 제한)
    String body = p.content != null ? p.content : "";
    String summary = body.length() > 30 ? body.substring(0, 30) + "..." : body;
    JLabel summaryLabel = new JLabel(summary);

    // 메타 정보: 댓글 수, 좋아요 수, 등록일, 작성자
    String timeStr = formatCreatedAt(p.createdAt);
    String meta = String.format("댓글 %d  |  좋아요 %d  |  %s  |  %s",
        data.commentCount,
        data.likesCount,
        timeStr,
        p.authorUsername != null ? p.authorUsername : "-"
    );
    JLabel metaLabel = new JLabel(meta);
    metaLabel.setFont(metaLabel.getFont().deriveFont(11f));
    metaLabel.setForeground(Color.DARK_GRAY);

    JPanel center = new JPanel(new BorderLayout(4, 4));
    center.setOpaque(false);
    center.add(titleLabel, BorderLayout.NORTH);
    center.add(summaryLabel, BorderLayout.CENTER);
    center.add(metaLabel, BorderLayout.SOUTH);

    card.add(center, BorderLayout.CENTER);

    // 카드 클릭 → 상세 보기
    card.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        openPostDetail(data);
      }
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        card.setBackground(new Color(245, 245, 255));
      }
      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        card.setBackground(Color.WHITE);
      }
    });

    cardsPanel.add(card);
    cardsPanel.add(Box.createVerticalStrut(8)); // 카드 사이 간격
  }

  /** 상세 보기 (간단 버전 – 제목/내용/댓글수/좋아요수 표시) */
  private void openPostDetail(CardData data) {
    Post p = data.post;

    JTextArea area = new JTextArea(p.content == null ? "" : p.content);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setBorder(new EmptyBorder(8, 8, 8, 8));

    JScrollPane sp = new JScrollPane(area);
    sp.setPreferredSize(new Dimension(600, 350));

    String info = String.format("댓글 %d  |  좋아요 %d  |  %s  |  %s",
        data.commentCount,
        data.likesCount,
        formatCreatedAt(p.createdAt),
        p.authorUsername
    );

    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.add(new JLabel(info), BorderLayout.NORTH);
    panel.add(sp, BorderLayout.CENTER);

    Object[] options;
    boolean isOwner = p.authorUsername != null && p.authorUsername.equals(user.username);
    if (isOwner) {
      options = new Object[]{"좋아요", "수정", "닫기"};
    } else {
      options = new Object[]{"좋아요", "닫기"};
    }

    int res = JOptionPane.showOptionDialog(
        this,
        panel,
        p.title,
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.PLAIN_MESSAGE,
        null,
        options,
        options[0]
    );

    if (res == 0) {
      // 좋아요 +1
      int newLikes = postService.increaseLikes(p.id);
      data.likesCount = newLikes;
      resetAndLoad(); // 다시 로드해서 카드 갱신
    } else if (isOwner && res == 1) {
      // 수정
      openEditPostDialog(p);
    }
  }

  /** 새 글 작성 팝업 */
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

    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    new SwingWorker<Void, Void>() {
      @Override protected Void doInBackground() {
        postService.create(user, boardName, title, content);
        return null;
      }
      @Override protected void done() {
        setCursor(Cursor.getDefaultCursor());
        resetAndLoad();
      }
    }.execute();
  }

  /** 게시글 수정 팝업 (본인 글만) */
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

    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    new SwingWorker<Void, Void>() {
      @Override protected Void doInBackground() {
        postService.update(p, newTitle, newContent);
        return null;
      }
      @Override protected void done() {
        setCursor(Cursor.getDefaultCursor());
        resetAndLoad();
      }
    }.execute();
  }

  /** 등록일시 포맷:
   *  - 1분 이내: "방금 전"
   *  - 1시간 이내: "n분 전"
   *  - 24시간 이내: "n시간 전"
   *  - 이후: "MM/dd"
   */
  private String formatCreatedAt(Date createdAt) {
    if (createdAt == null) return "";
    long now = System.currentTimeMillis();
    long diffMs = now - createdAt.getTime();
    long sec = diffMs / 1000;
    if (sec < 60) return "방금 전";
    long min = sec / 60;
    if (min < 60) return min + "분 전";
    long hour = min / 60;
    if (hour < 24) return hour + "시간 전";

    java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM/dd");
    return fmt.format(createdAt);
  }
}
