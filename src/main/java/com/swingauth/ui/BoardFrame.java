package com.swingauth.ui;

import com.swingauth.comment.Comment;
import com.swingauth.comment.CommentService;
import com.swingauth.model.Post;
import com.swingauth.model.User;
import com.swingauth.service.PostService;
import com.swingauth.service.ReportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BoardFrame extends JFrame implements ThemeManager.ThemeChangeListener {

  private final ThemeManager themeManager = ThemeManager.getInstance();
  private final User user;
  private final String boardName;

  private final PostService postService = new PostService();
  private final CommentService commentService = new CommentService();
  private final ReportService reportService = new ReportService();

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

    // ===== 상단: 보드명 + 사용자/지역 =====
    JPanel top = new JPanel(new BorderLayout());
    top.setBorder(new EmptyBorder(10, 12, 0, 12));

    JLabel title = new JLabel(boardName);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

    String neighborhood = (user.neighborhood != null && !user.neighborhood.isBlank())
        ? user.neighborhood : "unknown";
    JLabel who = new JLabel(user.username + " (" + neighborhood + ")");

    top.add(title, BorderLayout.WEST);
    top.add(who, BorderLayout.EAST);

    // ===== 검색 + 글쓰기 영역 =====
    JPanel searchPanel = new JPanel(new BorderLayout(8, 8));
    searchPanel.setBorder(new EmptyBorder(8, 0, 8, 0));

    searchField = new JTextField();
    JButton btnSearch = new JButton("검색");
    JButton btnNew = new JButton("글쓰기");
    
    // 초기 색상 설정
    btnSearch.setBackground(ThemeManager.LIGHT_BG2);
    btnSearch.setForeground(ThemeManager.TEXT_DARK);
    btnNew.setBackground(ThemeManager.LIGHT_BG2);
    btnNew.setForeground(ThemeManager.TEXT_DARK);
    
    ThemeManager.disableButtonPressedEffect(btnSearch);
    ThemeManager.disableButtonPressedEffect(btnNew);
    
    // 초기 색상 저장
    ThemeManager.updateButtonColors(btnSearch, ThemeManager.LIGHT_BG2, ThemeManager.TEXT_DARK);
    ThemeManager.updateButtonColors(btnNew, ThemeManager.LIGHT_BG2, ThemeManager.TEXT_DARK);

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

    // ===== 카드 목록 패널 =====
    cardsPanel = new JPanel();
    cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
    cardsPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
    cardsPanel.setBackground(ThemeManager.DARK_BG); // 배경색 설정
    cardsPanel.setOpaque(true); // 윈도우에서 겹침 문제 해결

    scrollPane = new JScrollPane(cardsPanel);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    add(scrollPane, BorderLayout.CENTER);

    // 스크롤 페이징
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

    // ===== 하단 버튼 =====
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
    JButton btnRefresh = new JButton("새로고침");
    JButton btnClose = new JButton("닫기");
    
    // 초기 색상 설정
    btnRefresh.setBackground(ThemeManager.LIGHT_BG2);
    btnRefresh.setForeground(ThemeManager.TEXT_DARK);
    btnClose.setBackground(ThemeManager.LIGHT_BG2);
    btnClose.setForeground(ThemeManager.TEXT_DARK);
    
    ThemeManager.disableButtonPressedEffect(btnRefresh);
    ThemeManager.disableButtonPressedEffect(btnClose);
    
    // 초기 색상 저장
    ThemeManager.updateButtonColors(btnRefresh, ThemeManager.LIGHT_BG2, ThemeManager.TEXT_DARK);
    ThemeManager.updateButtonColors(btnClose, ThemeManager.LIGHT_BG2, ThemeManager.TEXT_DARK);
    btnRefresh.addActionListener(e -> resetAndLoad());
    btnClose.addActionListener(e -> dispose());
    bottom.add(btnRefresh);
    bottom.add(btnClose);
    add(bottom, BorderLayout.SOUTH);

    // 첫 로딩
    resetAndLoad();
    
    // ThemeManager에 리스너 등록
    themeManager.addThemeChangeListener(this);
    
    // 초기 테마 적용
    applyTheme();
  }
  
  @Override
  public void onThemeChanged() {
    applyTheme();
    // 카드 목록도 다시 그리기
    cardsPanel.revalidate();
    cardsPanel.repaint();
  }
  
  private void applyTheme() {
    boolean isDarkMode = themeManager.isDarkMode();
    
    // JOptionPane 배경색 설정
    UIManager.put("OptionPane.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("Panel.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("OptionPane.messageForeground", isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
    
    try {
      // 프레임 배경
      getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      
      // 상단 패널
      if (getContentPane().getComponentCount() > 0) {
        Component northComp = getContentPane().getComponent(0);
        if (northComp instanceof JPanel) {
          JPanel northPanel = (JPanel) northComp;
          northPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
          if (northPanel.getComponentCount() > 0) {
            Component topComp = northPanel.getComponent(0);
            if (topComp instanceof JPanel) {
              ((JPanel) topComp).setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
            }
          }
          if (northPanel.getComponentCount() > 1) {
            Component searchComp = northPanel.getComponent(1);
            if (searchComp instanceof JPanel) {
              ((JPanel) searchComp).setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
            }
          }
        }
      }
      
      // 카드 패널 배경
      cardsPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      
      // 하단 패널
      if (getContentPane().getComponentCount() > 2) {
        Component bottomComp = getContentPane().getComponent(2);
        if (bottomComp instanceof JPanel) {
          ((JPanel) bottomComp).setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        }
      }
      
      // 모든 컴포넌트 재색칠
      updateAllComponents(this, isDarkMode);
    } catch (Exception e) {
      // 컴포넌트 접근 실패 시 무시
      System.err.println("테마 적용 중 오류: " + e.getMessage());
    }
  }
  
  private void updateAllComponents(Container container, boolean isDarkMode) {
    for (Component comp : container.getComponents()) {
      if (comp instanceof JPanel) {
        JPanel panel = (JPanel) comp;
        if (panel.isOpaque()) {
          panel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        }
        updateAllComponents(panel, isDarkMode);
      } else if (comp instanceof JLabel) {
        JLabel label = (JLabel) comp;
        if (label.getForeground().equals(ThemeManager.TEXT_LIGHT) || 
            label.getForeground().equals(ThemeManager.TEXT_DIM) ||
            label.getForeground().equals(ThemeManager.TEXT_DARK)) {
          label.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        }
      } else if (comp instanceof JTextField) {
        JTextField field = (JTextField) comp;
        field.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        field.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        field.setBorder(BorderFactory.createLineBorder(
            isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
      } else if (comp instanceof JTextArea) {
        JTextArea area = (JTextArea) comp;
        area.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        area.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      } else if (comp instanceof JButton) {
        JButton btn = (JButton) comp;
        Color bg = isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
        Color fg = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setBorder(BorderFactory.createLineBorder(
            isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
        // 테마 변경 시 버튼 색상 업데이트
        ThemeManager.updateButtonColors(btn, bg, fg);
      } else if (comp instanceof JScrollPane) {
        JScrollPane scroll = (JScrollPane) comp;
        scroll.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        scroll.getViewport().setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      } else if (comp instanceof Container) {
        updateAllComponents((Container) comp, isDarkMode);
      }
    }
  }

  private void resetAndLoad() {
    loadedCount = 0;
    noMore = false;
    cardsPanel.removeAll();
    // 윈도우에서 이전 카드가 겹쳐 보이는 문제 해결
    cardsPanel.revalidate();
    cardsPanel.repaint();
    // 스크롤 위치 초기화
    SwingUtilities.invokeLater(() -> {
      scrollPane.getViewport().setViewPosition(new Point(0, 0));
      cardsPanel.repaint();
    });
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
          // 윈도우에서 카드 겹침 문제 해결을 위해 강제 갱신
          cardsPanel.revalidate();
          cardsPanel.repaint();
          scrollPane.revalidate();
          scrollPane.repaint();
          setCursor(Cursor.getDefaultCursor());
          loading = false;
        }
      }
    }.execute();
  }

  /** 게시글 카드 UI 생성 (높이 고정) */
  private void addPostCard(CardData data) {
    Post p = data.post;

    JPanel card = new JPanel(new BorderLayout(8, 4));
    boolean isDarkMode = themeManager.isDarkMode();
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER),
        new EmptyBorder(8, 8, 8, 8)
    ));
    card.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
    card.setOpaque(true); // 윈도우에서 렌더링 문제 해결을 위해 명시적으로 설정

    int CARD_HEIGHT = 80;
    card.setPreferredSize(new Dimension(10, CARD_HEIGHT));
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));

    JLabel titleLabel = new JLabel(p.title != null ? p.title : "(제목 없음)");
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
    titleLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);

    String body = p.content != null ? p.content : "";
    String summary = body.length() > 30 ? body.substring(0, 30) + "..." : body;
    JLabel summaryLabel = new JLabel(summary);
    summaryLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);

    String timeStr = formatCreatedAt(p.createdAt);
    String meta = String.format("댓글 %d  |  좋아요 %d  |  %s  |  %s",
        data.commentCount,
        data.likesCount,
        timeStr,
        p.authorUsername != null ? p.authorUsername : "-"
    );
    JLabel metaLabel = new JLabel(meta);
    metaLabel.setFont(metaLabel.getFont().deriveFont(11f));
    metaLabel.setForeground(isDarkMode ? ThemeManager.TEXT_DIM : ThemeManager.TEXT_DARK);

    JPanel center = new JPanel(new BorderLayout(4, 4));
    center.setOpaque(false);
    center.add(titleLabel, BorderLayout.NORTH);
    center.add(summaryLabel, BorderLayout.CENTER);
    center.add(metaLabel, BorderLayout.SOUTH);

    card.add(center, BorderLayout.CENTER);

    card.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        openPostDetail(data);
      }

      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        boolean isDarkMode = themeManager.isDarkMode();
        // 알파 값 사용 시 윈도우에서 텍스트 겹침 문제 발생 - 불투명 색상 사용
        if (isDarkMode) {
          card.setBackground(new Color(20, 50, 55));
          card.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(ThemeManager.NEON_CYAN),
              new EmptyBorder(8, 8, 8, 8)
          ));
        } else {
          card.setBackground(new Color(220, 240, 250));
          card.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(ThemeManager.LIGHT_CYAN),
              new EmptyBorder(8, 8, 8, 8)
          ));
        }
        card.repaint(); // 윈도우에서 렌더링 문제 해결
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        boolean isDarkMode = themeManager.isDarkMode();
        card.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER),
            new EmptyBorder(8, 8, 8, 8)
        ));
        card.repaint(); // 윈도우에서 렌더링 문제 해결
      }
    });

    cardsPanel.add(card);
    cardsPanel.add(Box.createVerticalStrut(8));
  }

  /** 상세 보기: JDialog 안에서 댓글 입력/등록 가능, 창은 닫기 누를 때만 닫힘 */
  private void openPostDetail(CardData data) {
    Post p = data.post;

    // 모달 다이얼로그
    JDialog dialog = new JDialog(this, p.title, true);
    dialog.setSize(720, 600);
    dialog.setLocationRelativeTo(this);
    dialog.setLayout(new BorderLayout());

    // 상단 정보 라벨
    JLabel infoLabel = new JLabel();
    infoLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

    // 본문 영역
    JTextArea contentArea = new JTextArea(p.content == null ? "" : p.content);
    contentArea.setEditable(false);
    contentArea.setLineWrap(true);
    contentArea.setWrapStyleWord(true);
    contentArea.setBorder(new EmptyBorder(8, 8, 8, 8));
    JScrollPane contentScroll = new JScrollPane(contentArea);
    contentScroll.setPreferredSize(new Dimension(680, 220));

    // 댓글 목록 영역
    JTextArea commentArea = new JTextArea();
    commentArea.setEditable(false);
    commentArea.setLineWrap(true);
    commentArea.setWrapStyleWord(true);
    commentArea.setBorder(new EmptyBorder(4, 8, 8, 8));
    JScrollPane commentScroll = new JScrollPane(commentArea);
    commentScroll.setPreferredSize(new Dimension(680, 160));

    // 새 댓글 입력 영역
    JTextArea newCommentArea = new JTextArea(3, 40);
    newCommentArea.setLineWrap(true);
    newCommentArea.setWrapStyleWord(true);
    JScrollPane newCommentScroll = new JScrollPane(newCommentArea);
    newCommentScroll.setPreferredSize(new Dimension(680, 80));

    // 중앙 패널 (본문 + 댓글 + 새 댓글)
    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
    centerPanel.setBorder(new EmptyBorder(4, 8, 8, 8));
    centerPanel.add(contentScroll);
    centerPanel.add(Box.createVerticalStrut(8));
    JLabel commentListLabel = new JLabel("댓글 목록");
    centerPanel.add(commentListLabel);
    centerPanel.add(commentScroll);
    centerPanel.add(Box.createVerticalStrut(8));
    JLabel newCommentLabel = new JLabel("새 댓글");
    centerPanel.add(newCommentLabel);
    centerPanel.add(newCommentScroll);

    dialog.add(infoLabel, BorderLayout.NORTH);
    dialog.add(centerPanel, BorderLayout.CENTER);

    boolean isOwner = p.authorUsername != null && p.authorUsername.equals(user.username);

    JButton btnLike = new JButton("좋아요");
    JButton btnDislike = new JButton("싫어요");
    JButton btnEdit = new JButton("수정");
    JButton btnComment = new JButton("댓글 등록");
    JButton btnReport = new JButton("신고");
    JButton btnClose = new JButton("닫기");
    
    // 초기 색상 설정 (현재 테마에 맞게)
    boolean currentDarkMode = themeManager.isDarkMode();
    Color btnBg = currentDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
    Color btnFg = currentDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
    
    btnLike.setBackground(btnBg);
    btnLike.setForeground(btnFg);
    btnDislike.setBackground(btnBg);
    btnDislike.setForeground(btnFg);
    btnEdit.setBackground(btnBg);
    btnEdit.setForeground(btnFg);
    btnComment.setBackground(btnBg);
    btnComment.setForeground(btnFg);
    btnReport.setBackground(btnBg);
    btnReport.setForeground(btnFg);
    btnClose.setBackground(btnBg);
    btnClose.setForeground(btnFg);
    
    // 테두리 설정
    Color borderColor = currentDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER;
    btnLike.setBorder(BorderFactory.createLineBorder(borderColor, 1));
    btnDislike.setBorder(BorderFactory.createLineBorder(borderColor, 1));
    btnEdit.setBorder(BorderFactory.createLineBorder(borderColor, 1));
    btnComment.setBorder(BorderFactory.createLineBorder(borderColor, 1));
    btnReport.setBorder(BorderFactory.createLineBorder(borderColor, 1));
    btnClose.setBorder(BorderFactory.createLineBorder(borderColor, 1));
    
    btnLike.setFocusPainted(false);
    btnDislike.setFocusPainted(false);
    btnEdit.setFocusPainted(false);
    btnComment.setFocusPainted(false);
    btnReport.setFocusPainted(false);
    btnClose.setFocusPainted(false);
    
    ThemeManager.disableButtonPressedEffect(btnLike);
    ThemeManager.disableButtonPressedEffect(btnDislike);
    ThemeManager.disableButtonPressedEffect(btnEdit);
    ThemeManager.disableButtonPressedEffect(btnComment);
    ThemeManager.disableButtonPressedEffect(btnReport);
    ThemeManager.disableButtonPressedEffect(btnClose);
    
    // 색상 저장
    ThemeManager.updateButtonColors(btnLike, btnBg, btnFg);
    ThemeManager.updateButtonColors(btnDislike, btnBg, btnFg);
    ThemeManager.updateButtonColors(btnEdit, btnBg, btnFg);
    ThemeManager.updateButtonColors(btnComment, btnBg, btnFg);
    ThemeManager.updateButtonColors(btnReport, btnBg, btnFg);
    ThemeManager.updateButtonColors(btnClose, btnBg, btnFg);

    if (!isOwner) {
      btnEdit.setEnabled(false);
    }

    // 이미 신고했는지 미리 체크해서 버튼 상태 변경
    boolean alreadyReported = reportService.hasReported(user, p);
    if (alreadyReported) {
      btnReport.setEnabled(false);
      btnReport.setText("신고 완료");
    }

    // info 라벨/댓글 영역 갱신용 헬퍼
    Runnable refreshCommentsAndInfo = () -> {
      List<Comment> comments = commentService.listByPostId(p.id, 200);
      StringBuilder sb = new StringBuilder();
      for (Comment c : comments) {
        sb.append(c.authorUsername)
            .append(" : ")
            .append(c.content)
            .append("\n");
      }
      commentArea.setText(sb.toString());
      data.commentCount = comments.size();

      String info = String.format("댓글 %d  |  좋아요 %d  |  %s  |  %s",
          data.commentCount,
          data.likesCount,
          formatCreatedAt(p.createdAt),
          p.authorUsername
      );
      infoLabel.setText(info);
    };

    refreshCommentsAndInfo.run();

    // ===== 버튼 액션들 =====
    btnLike.addActionListener(e -> {
      try {
        int newLikes = postService.toggleLike(user, p.id);
        data.likesCount = newLikes;
        refreshCommentsAndInfo.run();
        resetAndLoad(); // 목록 카드 숫자 갱신
      } catch (Exception ex) {
        JOptionPane.showMessageDialog(dialog,
            "좋아요 처리 실패: " + ex.getMessage(),
            "오류", JOptionPane.ERROR_MESSAGE);
      }
    });

    btnDislike.addActionListener(e -> {
      try {
        boolean nowDisliked = postService.toggleDislike(user, p.id);
        String msg = nowDisliked
            ? "이 게시글에 싫어요를 눌렀습니다."
            : "이 게시글의 싫어요를 취소했습니다.";
        JOptionPane.showMessageDialog(dialog, msg,
            "알림", JOptionPane.INFORMATION_MESSAGE);
      } catch (Exception ex) {
        JOptionPane.showMessageDialog(dialog,
            "싫어요 처리 실패: " + ex.getMessage(),
            "오류", JOptionPane.ERROR_MESSAGE);
      }
    });

    btnEdit.addActionListener(e -> {
      if (!isOwner) return;
      openEditPostDialog(p);

      // 수정 후 DB에서 다시 읽어서 내용 갱신
      Post reloaded = postService.getById(p.id);
      if (reloaded != null) {
        p.title = reloaded.title;
        p.content = reloaded.content;
        contentArea.setText(p.content == null ? "" : p.content);
        dialog.setTitle(p.title);
      }
      resetAndLoad();
    });

    btnComment.addActionListener(e -> {
      String text = newCommentArea.getText();
      if (text == null || text.trim().isEmpty()) {
        JOptionPane.showMessageDialog(dialog, "댓글 내용을 입력하세요.",
            "알림", JOptionPane.INFORMATION_MESSAGE);
        return;
      }

      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      new SwingWorker<Void, Void>() {
        @Override
        protected Void doInBackground() {
          commentService.create(p.id, user.username, text.trim());
          return null;
        }

        @Override
        protected void done() {
          setCursor(Cursor.getDefaultCursor());
          newCommentArea.setText("");
          refreshCommentsAndInfo.run();
          resetAndLoad(); // 목록의 댓글 수 갱신
        }
      }.execute();
    });

    // 신고 버튼
    btnReport.addActionListener(e -> {
      if (p.authorUsername != null && p.authorUsername.equals(user.username)) {
        JOptionPane.showMessageDialog(dialog,
            "본인이 작성한 글은 신고할 수 없습니다.",
            "알림", JOptionPane.INFORMATION_MESSAGE);
        return;
      }

      JDialog reportDialog = new JDialog(dialog, "게시글 신고", true);
      reportDialog.setSize(500, 300);
      reportDialog.setLocationRelativeTo(dialog);
      reportDialog.setLayout(new BorderLayout());

      JTextArea taReason = new JTextArea(5, 30);
      taReason.setLineWrap(true);
      taReason.setWrapStyleWord(true);

      JPanel panel = new JPanel(new BorderLayout(8, 8));
      panel.setBorder(new EmptyBorder(8, 8, 8, 8));
      JLabel reasonLabel = new JLabel("신고 사유를 입력하세요:");
      panel.add(reasonLabel, BorderLayout.NORTH);
      JScrollPane reasonScroll = new JScrollPane(taReason);
      panel.add(reasonScroll, BorderLayout.CENTER);

      JButton okButton = new JButton("확인");
      JButton cancelButton = new JButton("취소");
      
      // 초기 색상 설정
      boolean reportDarkMode = themeManager.isDarkMode();
      Color reportBtnBg = reportDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
      Color reportBtnFg = reportDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
      
      okButton.setBackground(reportBtnBg);
      okButton.setForeground(reportBtnFg);
      okButton.setBorder(BorderFactory.createLineBorder(
          reportDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
      okButton.setFocusPainted(false);
      
      cancelButton.setBackground(reportBtnBg);
      cancelButton.setForeground(reportBtnFg);
      cancelButton.setBorder(BorderFactory.createLineBorder(
          reportDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
      cancelButton.setFocusPainted(false);
      
      ThemeManager.disableButtonPressedEffect(okButton);
      ThemeManager.disableButtonPressedEffect(cancelButton);
      
      ThemeManager.updateButtonColors(okButton, reportBtnBg, reportBtnFg);
      ThemeManager.updateButtonColors(cancelButton, reportBtnBg, reportBtnFg);
      
      okButton.addActionListener(ev -> {
        String reason = taReason.getText();
        if (reason == null || reason.trim().isEmpty()) {
          JOptionPane.showMessageDialog(reportDialog,
              "신고 사유를 입력하세요.",
              "알림", JOptionPane.INFORMATION_MESSAGE);
          return;
        }
        reportDialog.dispose();
        
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
          @Override
          protected Void doInBackground() {
            reportService.reportPost(user, p, reason.trim());
            return null;
          }

          @Override
          protected void done() {
            setCursor(Cursor.getDefaultCursor());
            try {
              get(); // 예외 전파
              JOptionPane.showMessageDialog(dialog,
                  "신고가 접수되었습니다.",
                  "알림", JOptionPane.INFORMATION_MESSAGE);
              btnReport.setEnabled(false);
              btnReport.setText("신고 완료");
            } catch (Exception ex) {
              Throwable cause = ex.getCause();
              String msg = (cause != null ? cause.getMessage() : ex.getMessage());
              JOptionPane.showMessageDialog(dialog,
                  "신고 처리 실패: " + msg,
                  "오류", JOptionPane.ERROR_MESSAGE);
            }
          }
        }.execute();
      });
      
      cancelButton.addActionListener(ev -> reportDialog.dispose());

      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
      buttonPanel.add(cancelButton);
      buttonPanel.add(okButton);

      reportDialog.add(panel, BorderLayout.CENTER);
      reportDialog.add(buttonPanel, BorderLayout.SOUTH);

      // 테마 적용 함수
      Runnable applyReportDialogTheme = () -> {
        boolean isDarkMode = themeManager.isDarkMode();
        
        reportDialog.getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        panel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        buttonPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        reasonLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        
        taReason.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        taReason.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        taReason.setCaretColor(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        
        reasonScroll.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        reasonScroll.getViewport().setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        
        Color reportThemeBtnBg = isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
        Color reportThemeBtnFg = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
        Color reportThemeBtnBorder = isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER;
        
        okButton.setBackground(reportThemeBtnBg);
        okButton.setForeground(reportThemeBtnFg);
        okButton.setBorder(BorderFactory.createLineBorder(reportThemeBtnBorder, 1));
        ThemeManager.disableButtonPressedEffect(okButton);
        ThemeManager.updateButtonColors(okButton, reportThemeBtnBg, reportThemeBtnFg);
        
        cancelButton.setBackground(reportThemeBtnBg);
        cancelButton.setForeground(reportThemeBtnFg);
        cancelButton.setBorder(BorderFactory.createLineBorder(reportThemeBtnBorder, 1));
        ThemeManager.disableButtonPressedEffect(cancelButton);
        ThemeManager.updateButtonColors(cancelButton, reportThemeBtnBg, reportThemeBtnFg);
        
        reportDialog.repaint();
      };
      
      ThemeManager.ThemeChangeListener reportDialogThemeListener = () -> applyReportDialogTheme.run();
      themeManager.addThemeChangeListener(reportDialogThemeListener);
      
      reportDialog.addWindowListener(new java.awt.event.WindowAdapter() {
        @Override
        public void windowClosed(java.awt.event.WindowEvent e) {
          themeManager.removeThemeChangeListener(reportDialogThemeListener);
        }
      });
      
      applyReportDialogTheme.run();
      reportDialog.setVisible(true);
      
      // 다이얼로그가 닫힌 후 처리
      if (!reportDialog.isVisible()) {
        return;
      }

    });

    btnClose.addActionListener(e -> dialog.dispose());

    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
    bottomPanel.add(btnLike);
    bottomPanel.add(btnDislike);
    bottomPanel.add(btnEdit);
    bottomPanel.add(btnComment);
    bottomPanel.add(btnReport);
    bottomPanel.add(btnClose);
    dialog.add(bottomPanel, BorderLayout.SOUTH);

    // 테마 적용 함수
    Runnable applyDialogTheme = () -> {
      boolean isDarkMode = themeManager.isDarkMode();
      
      // 다이얼로그 배경
      dialog.getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      
      // 정보 라벨 (다크모드: 흰색, 라이트모드: 검정색)
      infoLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      // 라벨들 (다크모드: 흰색, 라이트모드: 검정색)
      commentListLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      newCommentLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      // 텍스트 영역들
      contentArea.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      contentArea.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      contentArea.setCaretColor(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      commentArea.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      commentArea.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      commentArea.setCaretColor(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      newCommentArea.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      newCommentArea.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      newCommentArea.setCaretColor(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      // 스크롤 패널들
      contentScroll.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      contentScroll.getViewport().setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      commentScroll.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      commentScroll.getViewport().setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      newCommentScroll.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      newCommentScroll.getViewport().setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      
      // 중앙 패널
      centerPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      
      // 버튼들 (다크모드: 흰색, 라이트모드: 검정색)
      Color detailBtnBg = isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
      Color detailBtnFg = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
      Color detailBtnBorder = isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER;
      
      btnLike.setBackground(detailBtnBg);
      btnLike.setForeground(detailBtnFg);
      btnLike.setBorder(BorderFactory.createLineBorder(detailBtnBorder, 1));
      btnLike.setFocusPainted(false);
      ThemeManager.updateButtonColors(btnLike, detailBtnBg, detailBtnFg);
      
      btnDislike.setBackground(detailBtnBg);
      btnDislike.setForeground(detailBtnFg);
      btnDislike.setBorder(BorderFactory.createLineBorder(detailBtnBorder, 1));
      btnDislike.setFocusPainted(false);
      ThemeManager.updateButtonColors(btnDislike, detailBtnBg, detailBtnFg);
      
      btnEdit.setBackground(detailBtnBg);
      btnEdit.setForeground(detailBtnFg);
      btnEdit.setBorder(BorderFactory.createLineBorder(detailBtnBorder, 1));
      btnEdit.setFocusPainted(false);
      ThemeManager.updateButtonColors(btnEdit, detailBtnBg, detailBtnFg);
      
      btnComment.setBackground(detailBtnBg);
      btnComment.setForeground(detailBtnFg);
      btnComment.setBorder(BorderFactory.createLineBorder(detailBtnBorder, 1));
      btnComment.setFocusPainted(false);
      ThemeManager.updateButtonColors(btnComment, detailBtnBg, detailBtnFg);
      
      btnReport.setBackground(detailBtnBg);
      btnReport.setForeground(detailBtnFg);
      btnReport.setBorder(BorderFactory.createLineBorder(detailBtnBorder, 1));
      btnReport.setFocusPainted(false);
      ThemeManager.updateButtonColors(btnReport, detailBtnBg, detailBtnFg);
      
      btnClose.setBackground(detailBtnBg);
      btnClose.setForeground(detailBtnFg);
      btnClose.setBorder(BorderFactory.createLineBorder(detailBtnBorder, 1));
      btnClose.setFocusPainted(false);
      ThemeManager.updateButtonColors(btnClose, detailBtnBg, detailBtnFg);
      
      // 하단 패널
      bottomPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      
      dialog.repaint();
    };
    
    // 테마 변경 리스너 등록
    ThemeManager.ThemeChangeListener dialogThemeListener = () -> applyDialogTheme.run();
    themeManager.addThemeChangeListener(dialogThemeListener);
    
    // 다이얼로그가 닫힐 때 리스너 제거
    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent e) {
        themeManager.removeThemeChangeListener(dialogThemeListener);
      }
    });
    
    // 초기 테마 적용
    applyDialogTheme.run();

    dialog.setVisible(true);
  }

  /** 새 글 작성 */
  private void openNewPostDialog() {
    JDialog dialog = new JDialog(this, "새 글 작성", true);
    dialog.setSize(600, 500);
    dialog.setLocationRelativeTo(this);
    dialog.setLayout(new BorderLayout());

    JTextField tfTitle = new JTextField();
    JTextArea taContent = new JTextArea(10, 40);
    taContent.setLineWrap(true);
    taContent.setWrapStyleWord(true);

    JPanel panel = new JPanel(new BorderLayout(8, 8));
    JPanel north = new JPanel(new BorderLayout(8, 8));
    JLabel titleLabel = new JLabel("제목");
    north.add(titleLabel, BorderLayout.WEST);
    north.add(tfTitle, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    JScrollPane contentScroll = new JScrollPane(taContent);
    panel.add(contentScroll, BorderLayout.CENTER);
    panel.setBorder(new EmptyBorder(8, 8, 8, 8));

    JButton okButton = new JButton("확인");
    JButton cancelButton = new JButton("취소");
    
    // 초기 색상 설정
    boolean newPostDarkMode = themeManager.isDarkMode();
    Color newPostBtnBg = newPostDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
    Color newPostBtnFg = newPostDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
    
    okButton.setBackground(newPostBtnBg);
    okButton.setForeground(newPostBtnFg);
    okButton.setBorder(BorderFactory.createLineBorder(
        newPostDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
    okButton.setFocusPainted(false);
    
    cancelButton.setBackground(newPostBtnBg);
    cancelButton.setForeground(newPostBtnFg);
    cancelButton.setBorder(BorderFactory.createLineBorder(
        newPostDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
    cancelButton.setFocusPainted(false);
    
    ThemeManager.disableButtonPressedEffect(okButton);
    ThemeManager.disableButtonPressedEffect(cancelButton);
    
    ThemeManager.updateButtonColors(okButton, newPostBtnBg, newPostBtnFg);
    ThemeManager.updateButtonColors(cancelButton, newPostBtnBg, newPostBtnFg);
    
    okButton.addActionListener(e -> {
      String title = tfTitle.getText();
      String content = taContent.getText();
      dialog.dispose();
      
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      new SwingWorker<Void, Void>() {
        @Override
        protected Void doInBackground() {
          postService.create(user, boardName, title, content);
          return null;
        }

        @Override
        protected void done() {
          setCursor(Cursor.getDefaultCursor());
          resetAndLoad();
        }
      }.execute();
    });
    
    cancelButton.addActionListener(e -> dialog.dispose());

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
    buttonPanel.add(cancelButton);
    buttonPanel.add(okButton);

    dialog.add(panel, BorderLayout.CENTER);
    dialog.add(buttonPanel, BorderLayout.SOUTH);

    // 테마 적용 함수
    Runnable applyDialogTheme = () -> {
      boolean isDarkMode = themeManager.isDarkMode();
      
      dialog.getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      panel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      north.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      buttonPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      
      titleLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      tfTitle.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      tfTitle.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      tfTitle.setBorder(BorderFactory.createLineBorder(
        isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
      
      taContent.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      taContent.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      taContent.setCaretColor(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      contentScroll.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      contentScroll.getViewport().setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      
      Color btnBg = isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
      Color btnFg = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
      Color btnBorder = isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER;
      
      okButton.setBackground(btnBg);
      okButton.setForeground(btnFg);
      okButton.setBorder(BorderFactory.createLineBorder(btnBorder, 1));
      ThemeManager.disableButtonPressedEffect(okButton);
      ThemeManager.updateButtonColors(okButton, btnBg, btnFg);
      
      cancelButton.setBackground(btnBg);
      cancelButton.setForeground(btnFg);
      cancelButton.setBorder(BorderFactory.createLineBorder(btnBorder, 1));
      ThemeManager.disableButtonPressedEffect(cancelButton);
      ThemeManager.updateButtonColors(cancelButton, btnBg, btnFg);
      
      dialog.repaint();
    };
    
    ThemeManager.ThemeChangeListener dialogThemeListener = () -> applyDialogTheme.run();
    themeManager.addThemeChangeListener(dialogThemeListener);
    
    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent e) {
        themeManager.removeThemeChangeListener(dialogThemeListener);
      }
    });
    
    applyDialogTheme.run();
    dialog.setVisible(true);
  }

  /** 게시글 수정 (본인 글만) */
  private void openEditPostDialog(Post p) {
    JDialog dialog = new JDialog(this, "게시글 수정", true);
    dialog.setSize(600, 500);
    dialog.setLocationRelativeTo(this);
    dialog.setLayout(new BorderLayout());

    JTextField tfTitle = new JTextField(p.title);
    JTextArea taContent = new JTextArea(p.content, 10, 40);
    taContent.setLineWrap(true);
    taContent.setWrapStyleWord(true);

    JPanel panel = new JPanel(new BorderLayout(8, 8));
    JPanel north = new JPanel(new BorderLayout(8, 8));
    JLabel titleLabel = new JLabel("제목");
    north.add(titleLabel, BorderLayout.WEST);
    north.add(tfTitle, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    JScrollPane contentScroll = new JScrollPane(taContent);
    panel.add(contentScroll, BorderLayout.CENTER);
    panel.setBorder(new EmptyBorder(8, 8, 8, 8));

    JButton okButton = new JButton("확인");
    JButton cancelButton = new JButton("취소");
    
    // 초기 색상 설정
    boolean editDarkMode = themeManager.isDarkMode();
    Color editBtnBg = editDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
    Color editBtnFg = editDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
    
    okButton.setBackground(editBtnBg);
    okButton.setForeground(editBtnFg);
    okButton.setBorder(BorderFactory.createLineBorder(
        editDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
    okButton.setFocusPainted(false);
    
    cancelButton.setBackground(editBtnBg);
    cancelButton.setForeground(editBtnFg);
    cancelButton.setBorder(BorderFactory.createLineBorder(
        editDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
    cancelButton.setFocusPainted(false);
    
    ThemeManager.disableButtonPressedEffect(okButton);
    ThemeManager.disableButtonPressedEffect(cancelButton);
    
    ThemeManager.updateButtonColors(okButton, editBtnBg, editBtnFg);
    ThemeManager.updateButtonColors(cancelButton, editBtnBg, editBtnFg);
    
    okButton.addActionListener(e -> {
      String newTitle = tfTitle.getText();
      String newContent = taContent.getText();
      dialog.dispose();
      
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      new SwingWorker<Void, Void>() {
        @Override
        protected Void doInBackground() {
          postService.update(p, newTitle, newContent);
          return null;
        }

        @Override
        protected void done() {
          setCursor(Cursor.getDefaultCursor());
          resetAndLoad();
        }
      }.execute();
    });
    
    cancelButton.addActionListener(e -> dialog.dispose());

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
    buttonPanel.add(cancelButton);
    buttonPanel.add(okButton);

    dialog.add(panel, BorderLayout.CENTER);
    dialog.add(buttonPanel, BorderLayout.SOUTH);

    // 테마 적용 함수
    Runnable applyDialogTheme = () -> {
      boolean isDarkMode = themeManager.isDarkMode();
      
      dialog.getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      panel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      north.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      buttonPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      
      titleLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      tfTitle.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      tfTitle.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      tfTitle.setBorder(BorderFactory.createLineBorder(
        isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
      
      taContent.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      taContent.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      taContent.setCaretColor(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
      
      contentScroll.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
      contentScroll.getViewport().setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
      
      Color btnBg = isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
      Color btnFg = isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
      Color btnBorder = isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER;
      
      okButton.setBackground(btnBg);
      okButton.setForeground(btnFg);
      okButton.setBorder(BorderFactory.createLineBorder(btnBorder, 1));
      ThemeManager.disableButtonPressedEffect(okButton);
      ThemeManager.updateButtonColors(okButton, btnBg, btnFg);
      
      cancelButton.setBackground(btnBg);
      cancelButton.setForeground(btnFg);
      cancelButton.setBorder(BorderFactory.createLineBorder(btnBorder, 1));
      ThemeManager.disableButtonPressedEffect(cancelButton);
      ThemeManager.updateButtonColors(cancelButton, btnBg, btnFg);
      
      dialog.repaint();
    };
    
    ThemeManager.ThemeChangeListener dialogThemeListener = () -> applyDialogTheme.run();
    themeManager.addThemeChangeListener(dialogThemeListener);
    
    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent e) {
        themeManager.removeThemeChangeListener(dialogThemeListener);
      }
    });
    
    applyDialogTheme.run();
    dialog.setVisible(true);
  }

  /** 등록일시 포맷 */
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
