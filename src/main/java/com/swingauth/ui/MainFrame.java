package com.swingauth.ui;

import com.swingauth.model.User;
import com.swingauth.service.SafetyAlertService;
import com.swingauth.service.SafetyAlertService.Alert;
import io.socket.client.Socket;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 메인 화면
 * - 게시판 선택/열기
 * - 랜덤 채팅 / 랜덤 영상통화
 * - 테마 전환
 * - 내 지역 기준 안전알림(재난문자/실종경보 등) 조회
 */
public class MainFrame extends JFrame implements ThemeManager.ThemeChangeListener {

  private final ThemeManager themeManager = ThemeManager.getInstance();
  private final User user;
  private final SafetyAlertService safetyAlertService = new SafetyAlertService();

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
  private JButton btnSafety;        // ★ 안전알림 버튼
  private JPanel leftPanel;

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

    // ===== 상단: 테마 전환 버튼 + 안전알림 + 아이디(지역) + 로그아웃 =====
    top = new JPanel(new BorderLayout());
    top.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

    // 좌측 상단: 테마 전환 버튼 + 안전알림
    leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

    themeToggleBtn = new JButton("🌙 다크모드");
    themeToggleBtn.setFont(themeToggleBtn.getFont().deriveFont(Font.BOLD, 12f));
    themeToggleBtn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    themeToggleBtn.setFocusPainted(false);
    ThemeManager.disableButtonPressedEffect(themeToggleBtn);
    themeToggleBtn.addActionListener(e -> themeManager.toggleTheme());
    leftPanel.add(themeToggleBtn);

    // ★ 안전알림 버튼 (내 지역 재난/실종 경보 등 최대 30건 조회)
    btnSafety = new JButton("안전알림");
    btnSafety.setFont(btnSafety.getFont().deriveFont(Font.BOLD, 12f));
    btnSafety.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    btnSafety.setFocusPainted(false);
    ThemeManager.disableButtonPressedEffect(btnSafety);
    btnSafety.addActionListener(e -> openSafetyDialog());
    leftPanel.add(btnSafety);

    // ThemeManager에 리스너 등록
    themeManager.addThemeChangeListener(this);

    String neighborhood = (user.neighborhood != null && !user.neighborhood.isBlank())
        ? user.neighborhood : "unknown";
    idAndLoc = new JLabel(user.username + " (" + neighborhood + ")");
    idAndLoc.setFont(idAndLoc.getFont().deriveFont(Font.BOLD, 14f));

    logout = new JButton("로그아웃");
    ThemeManager.disableButtonPressedEffect(logout);
    logout.addActionListener(e -> {
      SwingUtilities.invokeLater(() -> new AuthFrame().setVisible(true));
      dispose();
    });

    right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    right.add(idAndLoc);
    right.add(logout);

    top.add(leftPanel, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);

    // ===== 중앙: 게시판 리스트 (선택 가능) =====
    centerWrap = new JPanel(new GridBagLayout());
    centerWrap.setOpaque(true);
    boardBox = new JPanel(new BorderLayout());
    boardBox.setBorder(new LineBorder(ThemeManager.NEON_CYAN, 2, true));
    boardBox.setBackground(ThemeManager.DARK_BG2);
    boardBox.setOpaque(true);
    boardBox.setPreferredSize(new Dimension(360, 320));

    list = new JList<>(boards);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setFont(list.getFont().deriveFont(16f));
    list.setFixedCellHeight(36);
    list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // 엔터/더블클릭으로 열기
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && list.getSelectedIndex() >= 0) {
          openSelectedBoard(list.getSelectedValue());
        }
      }
    });
    list.addKeyListener(new java.awt.event.KeyAdapter() {
      @Override
      public void keyPressed(java.awt.event.KeyEvent e) {
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
    ThemeManager.disableButtonPressedEffect(btnOpen);
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
    bottom.setOpaque(true);
    btnChat = new JButton("랜덤 채팅");
    btnVideo = new JButton("랜덤 영상 통화");

    // 네온 스타일 버튼
    btnChat.setBackground(ThemeManager.NEON_CYAN);
    btnChat.setForeground(ThemeManager.DARK_BG);
    btnChat.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    btnChat.setFocusPainted(false);
    ThemeManager.disableButtonPressedEffect(btnChat);

    btnVideo.setBackground(ThemeManager.NEON_PINK);
    btnVideo.setForeground(Color.WHITE);
    btnVideo.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    btnVideo.setFocusPainted(false);
    ThemeManager.disableButtonPressedEffect(btnVideo);

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
      System.out.println("[MainFrame] 랜덤 영상 통화 버튼 클릭됨");
      System.out.println("[MainFrame] user: " + (user != null ? user.username : "null"));
      System.out.println("[MainFrame] isDarkMode: " + themeManager.isDarkMode());
      try {
        System.out.println("[MainFrame] VideoCallFrame 생성 시작...");
        new VideoCallFrame(user, themeManager.isDarkMode());
        System.out.println("[MainFrame] VideoCallFrame 생성 완료");
      } catch (Exception ex) {
        System.err.println("[MainFrame] VideoCallFrame 생성 실패: " + ex.getMessage());
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this,
            "영상통화를 시작할 수 없습니다: " + ex.getMessage() + "\n\n자세한 내용은 콘솔을 확인하세요.",
            "오류", JOptionPane.ERROR_MESSAGE);
      } catch (Throwable t) {
        System.err.println("[MainFrame] VideoCallFrame 생성 중 예상치 못한 오류: " + t.getMessage());
        t.printStackTrace();
        JOptionPane.showMessageDialog(this,
            "영상통화를 시작할 수 없습니다: " + t.getMessage() + "\n\n자세한 내용은 콘솔을 확인하세요.",
            "오류", JOptionPane.ERROR_MESSAGE);
      }
    });

    bottom.add(btnChat);
    bottom.add(btnVideo);
    add(bottom, BorderLayout.SOUTH);

    // 초기 테마 적용
    applyTheme();
  }

  /* ===================== 게시판 열기 ===================== */

  private void openSelectedBoard(String boardName) {
    SwingUtilities.invokeLater(() -> new BoardFrame(user, boardName).setVisible(true));
  }

  /* ===================== 안전알림 다이얼로그 ===================== */

  /**
   * User.region / User.city 기반으로 내 지역 안전알림
   * (재난문자 + 실종경보 등) 최대 30건을 조회하여 보여준다.
   *
   * 실제 SafetyAlertService 내부에서 공공데이터 API를 호출한다.
   */
  private void openSafetyDialog() {
    JDialog dialog = new JDialog(this, "안전알림", true);
    dialog.setSize(700, 450);
    dialog.setLocationRelativeTo(this);
    dialog.setLayout(new BorderLayout(8, 8));
    boolean isDark = themeManager.isDarkMode();
    Color bg = isDark ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG;
    Color bg2 = isDark ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2;
    Color fg = isDark ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK;
    Color borderColor = isDark ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER;
    Color accent = isDark ? ThemeManager.NEON_CYAN : ThemeManager.LIGHT_CYAN;
    dialog.getContentPane().setBackground(bg);

    // Alert 객체를 직접 담는 리스트
    DefaultListModel<Alert> model = new DefaultListModel<>();
    JList<Alert> alertList = new JList<>(model);
    alertList.setVisibleRowCount(12);
    alertList.setFixedCellHeight(22);
    alertList.setBackground(bg2);
    alertList.setForeground(fg);
    alertList.setSelectionBackground(accent);
    alertList.setSelectionForeground(isDark ? ThemeManager.DARK_BG : Color.WHITE);

    JScrollPane scrollPane = new JScrollPane(alertList);
    scrollPane.getViewport().setBackground(bg2);
    scrollPane.setBackground(bg2);
    scrollPane.setBorder(BorderFactory.createLineBorder(borderColor, 1));
    dialog.add(scrollPane, BorderLayout.CENTER);

    JLabel info = new JLabel("전국 안전알림(최신 20건)을 불러오는 중...");
    info.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    info.setForeground(fg);
    info.setBackground(bg);
    info.setOpaque(true);
    dialog.add(info, BorderLayout.SOUTH);

    // 더블클릭 시 상세 정보 다이얼로그
    alertList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && alertList.getSelectedIndex() >= 0) {
          Alert a = alertList.getSelectedValue();
          if (a != null) {
            showAlertDetailDialog(a, dialog);
          }
        }
      }
    });

    new SwingWorker<List<Alert>, Void>() {
      @Override
      protected List<Alert> doInBackground() {
        try {
          // ✅ 이제는 지역 상관없이 전국 기준 최신 20건 조회
          return safetyAlertService.fetchLatestAlerts(20);
        } catch (Exception ex) {
          ex.printStackTrace();
          SwingUtilities.invokeLater(() ->
              info.setText("오류: " + ex.getMessage())
          );
          return java.util.Collections.emptyList();
        }
      }

      @Override
      protected void done() {
        try {
          List<Alert> alerts = get();
          model.clear();
          if (alerts.isEmpty()) {
            info.setText("표시할 안전알림이 없습니다. (전국 기준)");
          } else {
            for (Alert a : alerts) {
              model.addElement(a);
            }
            info.setText("총 " + alerts.size() + "건 – 전국 기준 최신 알림 (더블클릭 시 상세보기)");
          }
        } catch (Exception ex) {
          info.setText("결과 처리 중 오류: " + ex.getMessage());
        }
      }
    }.execute();

    dialog.setVisible(true);
  }

  /**
   * 실종경보/재난문자 상세 내용을 보여주는 팝업.
   */
  private void showAlertDetailDialog(Alert a, Component parent) {
  StringBuilder sb = new StringBuilder();

  sb.append("종류: ").append(a.type).append("\n");

  if (a.stepName != null && !a.stepName.isBlank()) {
    sb.append("긴급단계: ").append(a.stepName).append("\n");
  }
  if (a.disasterType != null && !a.disasterType.isBlank()) {
    sb.append("재해구분: ").append(a.disasterType).append("\n");
  }

  sb.append("지역: ").append(a.region).append("\n");

  if (a.timeText != null && !a.timeText.isBlank()) {
    sb.append("생성 시각: ").append(a.timeText).append("\n");
  }
  if (a.sn != null && !a.sn.isBlank()) {
    sb.append("일련번호: ").append(a.sn).append("\n");
  }
  if (a.regYmd != null && !a.regYmd.isBlank()) {
    sb.append("등록일자: ").append(a.regYmd).append("\n");
  }
  if (a.mdfcnYmd != null && !a.mdfcnYmd.isBlank()) {
    sb.append("수정일자: ").append(a.mdfcnYmd).append("\n");
  }

  sb.append("\n메시지 내용:\n")
    .append(a.message != null ? a.message : "(없음)");

  JOptionPane.showMessageDialog(
      parent,
      sb.toString(),
      "재난문자 상세 정보",
      JOptionPane.INFORMATION_MESSAGE
  );
}


  /* ===================== 테마 변경 ===================== */

  @Override
  public void onThemeChanged() {
    applyTheme();
  }

  private void applyTheme() {
    boolean isDarkMode = themeManager.isDarkMode();

    UIManager.put("OptionPane.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("Panel.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
    UIManager.put("OptionPane.messageForeground", isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);

    if (isDarkMode) {
      // 다크모드
      getContentPane().setBackground(ThemeManager.DARK_BG);
      top.setBackground(ThemeManager.DARK_BG);
      right.setBackground(ThemeManager.DARK_BG);
      idAndLoc.setForeground(ThemeManager.TEXT_LIGHT);
      ThemeManager.updateButtonColors(logout, ThemeManager.DARK_BG2, ThemeManager.TEXT_LIGHT);
      logout.setBorder(BorderFactory.createLineBorder(ThemeManager.DARK_BORDER, 1));

      centerWrap.setBackground(ThemeManager.DARK_BG);
      centerWrap.setOpaque(true);
      boardBox.setBorder(new LineBorder(ThemeManager.NEON_CYAN, 2, true));
      boardBox.setBackground(ThemeManager.DARK_BG2);
      boardBox.setOpaque(true);

      list.setBackground(ThemeManager.DARK_BG2);
      list.setForeground(ThemeManager.TEXT_LIGHT);
      list.setSelectionBackground(ThemeManager.NEON_CYAN);
      list.setSelectionForeground(ThemeManager.DARK_BG);

      scroll.setBackground(ThemeManager.DARK_BG2);
      scroll.getViewport().setBackground(ThemeManager.DARK_BG2);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      scroll.setOpaque(true);

      openBar.setBackground(ThemeManager.DARK_BG2);
      openBar.setOpaque(true);
      ThemeManager.updateButtonColors(btnOpen, ThemeManager.DARK_BG, ThemeManager.TEXT_LIGHT);
      btnOpen.setBorder(BorderFactory.createLineBorder(ThemeManager.DARK_BORDER, 1));

      bottom.setBackground(ThemeManager.DARK_BG);
      bottom.setOpaque(true);
      ThemeManager.updateButtonColors(btnChat, ThemeManager.NEON_CYAN, ThemeManager.DARK_BG);
      ThemeManager.updateButtonColors(btnVideo, ThemeManager.NEON_PINK, Color.WHITE);

      themeToggleBtn.setText("🌙 다크모드");
      ThemeManager.updateButtonColors(themeToggleBtn, ThemeManager.DARK_BG2, ThemeManager.TEXT_LIGHT);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.DARK_BORDER, 1));

      ThemeManager.updateButtonColors(btnSafety, ThemeManager.DARK_BG2, ThemeManager.TEXT_LIGHT);
      btnSafety.setBorder(BorderFactory.createLineBorder(ThemeManager.DARK_BORDER, 1));

      leftPanel.setBackground(ThemeManager.DARK_BG);
    } else {
      // 라이트모드
      getContentPane().setBackground(ThemeManager.LIGHT_BG);
      top.setBackground(ThemeManager.LIGHT_BG);
      right.setBackground(ThemeManager.LIGHT_BG);
      idAndLoc.setForeground(ThemeManager.TEXT_DARK);
      ThemeManager.updateButtonColors(logout, ThemeManager.LIGHT_BG2, ThemeManager.TEXT_DARK);
      logout.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));

      centerWrap.setBackground(ThemeManager.LIGHT_BG);
      centerWrap.setOpaque(true);
      boardBox.setBorder(new LineBorder(ThemeManager.LIGHT_CYAN, 2, true));
      boardBox.setBackground(ThemeManager.LIGHT_BG2);
      boardBox.setOpaque(true);

      list.setBackground(ThemeManager.LIGHT_BG2);
      list.setForeground(ThemeManager.TEXT_DARK);
      list.setSelectionBackground(ThemeManager.LIGHT_CYAN);
      list.setSelectionForeground(Color.WHITE);

      scroll.setBackground(ThemeManager.LIGHT_BG2);
      scroll.getViewport().setBackground(ThemeManager.LIGHT_BG2);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      scroll.setOpaque(true);

      openBar.setBackground(ThemeManager.LIGHT_BG2);
      openBar.setOpaque(true);
      ThemeManager.updateButtonColors(btnOpen, ThemeManager.LIGHT_BG, ThemeManager.TEXT_DARK);
      btnOpen.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));

      bottom.setBackground(ThemeManager.LIGHT_BG);
      bottom.setOpaque(true);
      ThemeManager.updateButtonColors(btnChat, ThemeManager.LIGHT_CYAN, Color.WHITE);
      ThemeManager.updateButtonColors(btnVideo, ThemeManager.LIGHT_PINK, Color.WHITE);

      themeToggleBtn.setText("☀️ 라이트모드");
      ThemeManager.updateButtonColors(themeToggleBtn, ThemeManager.LIGHT_BG2, ThemeManager.TEXT_DARK);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));

      ThemeManager.updateButtonColors(btnSafety, ThemeManager.LIGHT_BG2, ThemeManager.TEXT_DARK);
      btnSafety.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));

      leftPanel.setBackground(ThemeManager.LIGHT_BG);
    }

    UIManager.put("ScrollBar.background", isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
    UIManager.put("ScrollBar.thumb", isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER);
    SwingUtilities.updateComponentTreeUI(this);
  }
}
