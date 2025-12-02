package com.swingauth.ui;

import com.swingauth.model.User;
import io.socket.client.Socket;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MainFrame extends JFrame implements ThemeManager.ThemeChangeListener {

  private final ThemeManager themeManager = ThemeManager.getInstance();
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
    ThemeManager.disableButtonPressedEffect(themeToggleBtn);
    themeToggleBtn.addActionListener(e -> {
      themeManager.toggleTheme();
    });
    
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

    top.add(themeToggleBtn, BorderLayout.WEST);
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
        
        // UI 스레드에서 직접 실행 (비동기 스레드 문제 해결)
        try {
            System.out.println("[MainFrame] VideoCallFrame 생성 시작...");
            VideoCallFrame frame = new VideoCallFrame(user, themeManager.isDarkMode());
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

  private void openSelectedBoard(String boardName) {
    // 새 창(프레임)으로 해당 게시판 열기
    SwingUtilities.invokeLater(() -> new BoardFrame(user, boardName).setVisible(true));
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
    if (isDarkMode) {
      // 다크모드 적용
      getContentPane().setBackground(ThemeManager.DARK_BG);
      top.setBackground(ThemeManager.DARK_BG);
      right.setBackground(ThemeManager.DARK_BG);
      idAndLoc.setForeground(ThemeManager.TEXT_LIGHT);
      logout.setBackground(ThemeManager.DARK_BG2);
      logout.setForeground(ThemeManager.TEXT_LIGHT);
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
      btnOpen.setBackground(ThemeManager.DARK_BG);
      btnOpen.setForeground(ThemeManager.TEXT_LIGHT);
      btnOpen.setBorder(BorderFactory.createLineBorder(ThemeManager.DARK_BORDER, 1));
      
      bottom.setBackground(ThemeManager.DARK_BG);
      bottom.setOpaque(true);
      btnChat.setBackground(ThemeManager.NEON_CYAN);
      btnChat.setForeground(ThemeManager.DARK_BG);
      btnVideo.setBackground(ThemeManager.NEON_PINK);
      btnVideo.setForeground(Color.WHITE);
      
      themeToggleBtn.setText("🌙 다크모드");
      themeToggleBtn.setBackground(ThemeManager.DARK_BG2);
      themeToggleBtn.setForeground(ThemeManager.TEXT_LIGHT);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.DARK_BORDER, 1));
    } else {
      // 라이트모드 적용
      getContentPane().setBackground(ThemeManager.LIGHT_BG);
      top.setBackground(ThemeManager.LIGHT_BG);
      right.setBackground(ThemeManager.LIGHT_BG);
      idAndLoc.setForeground(ThemeManager.TEXT_DARK);
      logout.setBackground(ThemeManager.LIGHT_BG2);
      logout.setForeground(ThemeManager.TEXT_DARK);
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
      btnOpen.setBackground(ThemeManager.LIGHT_BG);
      btnOpen.setForeground(ThemeManager.TEXT_DARK);
      btnOpen.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));
      
      bottom.setBackground(ThemeManager.LIGHT_BG);
      bottom.setOpaque(true);
      btnChat.setBackground(ThemeManager.LIGHT_CYAN);
      btnChat.setForeground(Color.WHITE);
      btnVideo.setBackground(ThemeManager.LIGHT_PINK);
      btnVideo.setForeground(Color.WHITE);
      
      themeToggleBtn.setText("☀️ 라이트모드");
      themeToggleBtn.setBackground(ThemeManager.LIGHT_BG2);
      themeToggleBtn.setForeground(ThemeManager.TEXT_DARK);
      themeToggleBtn.setBorder(BorderFactory.createLineBorder(ThemeManager.LIGHT_BORDER, 1));
    }
    
    // 스크롤바 스타일도 적용
    UIManager.put("ScrollBar.background", isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
    UIManager.put("ScrollBar.thumb", isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER);
    SwingUtilities.updateComponentTreeUI(this);
  }
}
