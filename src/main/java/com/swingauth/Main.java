package com.swingauth;

import com.swingauth.chat.server.ChatServer;
import com.swingauth.config.ServerConfig;
import com.swingauth.util.NetworkDiscovery;
import com.swingauth.ui.AuthFrame;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.Color;
import com.formdev.flatlaf.FlatDarkLaf;

public class Main {
  public static void main(String[] args) {
    // CHAT_SERVER_HOST가 설정되어 있으면 클라이언트만 실행, 없으면 서버+클라이언트 실행
    String serverHost = System.getenv("CHAT_SERVER_HOST");
    String serverHostProp = System.getProperty("chat.server.host");
    boolean isClientOnly = (serverHostProp != null && !serverHostProp.isEmpty()) || 
                           (serverHost != null && !serverHost.isEmpty());
    
    if (!isClientOnly) {
      // 서버 모드: 서버를 자동으로 시작
      System.out.println("=== 서버 모드: 서버를 자동으로 시작합니다 ===");
      Thread serverThread = new Thread(() -> {
        try {
          // 서버 시작 전 약간의 지연
          Thread.sleep(500);
          ChatServer server = ChatServer.getInstance();
          if (!server.isRunning()) {
            System.out.println("채팅 서버를 시작하는 중...");
            server.start();
            System.out.println("채팅 서버가 실행 중입니다.");
            
            // 서버 IP 주소 감지
            System.out.println("\n📡 네트워크 인터페이스 검색 중...");
            String serverIP = NetworkDiscovery.detectLocalIP();
            if (!serverIP.equals("localhost")) {
              System.out.println("\n✅ 선택된 서버 IP 주소: " + serverIP);
              
              // 서버 IP를 ServerConfig에 설정 (클라이언트가 이 IP로 연결하도록)
              ServerConfig.setServerHost(serverIP);
              
              // 네트워크 발견 서비스 시작 (다른 컴퓨터가 자동으로 찾을 수 있도록)
              NetworkDiscovery.startServerListener(serverIP);
              NetworkDiscovery.startServerBroadcast(serverIP);
              System.out.println("✅ 네트워크 자동 발견 서비스 시작됨 (포트 3002)");
              System.out.println("   다른 컴퓨터가 이 IP로 자동으로 연결할 수 있습니다: " + serverIP);
            } else {
              System.out.println("⚠️  네트워크 인터페이스를 찾을 수 없습니다. localhost를 사용합니다.");
            }
          } else {
            System.out.println("채팅 서버가 이미 실행 중입니다.");
          }
        } catch (Exception e) {
          System.err.println("채팅 서버 시작 실패: " + e.getMessage());
          e.printStackTrace();
        }
      });
      serverThread.setDaemon(true);
      serverThread.start();

      // 애플리케이션 종료 시 서버 정리
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        ChatServer.getInstance().stop();
      }));
    } else {
      // 클라이언트 모드: 서버 실행 안 함, 네트워크에서 서버 자동 발견 시도
      System.out.println("=== 클라이언트 모드: 네트워크에서 서버를 찾는 중... ===");
      String configuredServerIP = serverHostProp != null ? serverHostProp : serverHost;
      
      // 환경 변수가 설정되지 않았으면 네트워크에서 서버 찾기 시도
      if (configuredServerIP == null || configuredServerIP.isEmpty()) {
        System.out.println("네트워크에서 서버를 자동으로 찾는 중...");
        String discoveredIP = NetworkDiscovery.discoverServer(5000); // 5초 동안 찾기
        if (discoveredIP != null && !discoveredIP.isEmpty()) {
          ServerConfig.setServerHost(discoveredIP);
          System.out.println("서버를 발견했습니다: " + discoveredIP);
        } else {
          // 발견 실패 시 로컬 IP 사용
          String localIP = NetworkDiscovery.detectLocalIP();
          ServerConfig.setServerHost(localIP);
          System.out.println("서버를 찾을 수 없어 로컬 IP 사용: " + localIP);
        }
      } else {
        System.out.println("설정된 서버 주소: " + configuredServerIP);
      }
    }

    // 클라이언트 UI 시작
    SwingUtilities.invokeLater(() -> {
      try {
        // FlatLaf 다크 테마 기반 사이버펑크 네온 테마
        UIManager.setLookAndFeel(new FlatDarkLaf());
        
        // ===== 사이버펑크 네온 색상 팔레트 =====
        // 네온 포인트 색상
        Color neonCyan = new Color(0, 255, 255);         // #00FFFF 네온 시안
        Color neonPink = new Color(255, 0, 128);         // #FF0080 네온 핑크
        Color neonPurple = new Color(191, 64, 255);      // #BF40FF 네온 퍼플
        Color neonGreen = new Color(57, 255, 20);        // #39FF14 네온 그린
        Color neonYellow = new Color(255, 255, 0);       // #FFFF00 네온 옐로우
        
        // 다크 배경
        Color darkBg = new Color(18, 18, 24);            // #121218 매우 어두운 배경
        Color darkBg2 = new Color(28, 28, 36);           // #1C1C24 약간 밝은 배경
        Color darkBg3 = new Color(38, 38, 48);           // #262630 컴포넌트 배경
        Color darkBorder = new Color(60, 60, 80);        // #3C3C50 테두리
        
        // 텍스트 색상
        Color textLight = new Color(240, 240, 255);      // #F0F0FF 밝은 텍스트
        Color textDim = new Color(160, 160, 180);        // #A0A0B4 희미한 텍스트
        
        // ===== 기본 컴포넌트 스타일 =====
        // 배경
        UIManager.put("Panel.background", darkBg);
        UIManager.put("RootPane.background", darkBg);
        UIManager.put("ScrollPane.background", darkBg);
        UIManager.put("Viewport.background", darkBg);
        
        // 버튼 (네온 스타일)
        UIManager.put("Button.background", darkBg3);
        UIManager.put("Button.foreground", textLight);
        UIManager.put("Button.hoverBackground", darkBorder);
        UIManager.put("Button.pressedBackground", new Color(80, 80, 100));
        UIManager.put("Button.default.background", neonCyan);
        UIManager.put("Button.default.foreground", darkBg);
        UIManager.put("Button.default.hoverBackground", new Color(0, 200, 200));
        UIManager.put("Button.arc", 8);
        
        // 텍스트 필드
        UIManager.put("TextField.background", darkBg2);
        UIManager.put("TextField.foreground", textLight);
        UIManager.put("TextField.caretForeground", neonCyan);
        UIManager.put("TextField.selectionBackground", neonPurple);
        UIManager.put("TextField.selectionForeground", textLight);
        UIManager.put("TextArea.background", darkBg2);
        UIManager.put("TextArea.foreground", textLight);
        UIManager.put("PasswordField.background", darkBg2);
        UIManager.put("PasswordField.foreground", textLight);
        
        // 레이블
        UIManager.put("Label.foreground", textLight);
        UIManager.put("Label.disabledForeground", textDim);
        
        // 콤보박스
        UIManager.put("ComboBox.background", darkBg2);
        UIManager.put("ComboBox.foreground", textLight);
        UIManager.put("ComboBox.selectionBackground", neonPurple);
        UIManager.put("ComboBox.selectionForeground", textLight);
        
        // 리스트 & 테이블
        UIManager.put("List.background", darkBg2);
        UIManager.put("List.foreground", textLight);
        UIManager.put("List.selectionBackground", new Color(neonCyan.getRed(), neonCyan.getGreen(), neonCyan.getBlue(), 80));
        UIManager.put("List.selectionForeground", neonCyan);
        UIManager.put("Table.background", darkBg2);
        UIManager.put("Table.foreground", textLight);
        UIManager.put("Table.selectionBackground", new Color(neonPurple.getRed(), neonPurple.getGreen(), neonPurple.getBlue(), 80));
        UIManager.put("Table.selectionForeground", neonPurple);
        UIManager.put("TableHeader.background", darkBg3);
        UIManager.put("TableHeader.foreground", neonCyan);
        
        // 스크롤바
        UIManager.put("ScrollBar.thumb", darkBorder);
        UIManager.put("ScrollBar.track", darkBg2);
        UIManager.put("ScrollBar.width", 10);
        
        // 포커스 & 테두리
        UIManager.put("Component.focusColor", neonCyan);
        UIManager.put("Component.focusedBorderColor", neonCyan);
        UIManager.put("Component.borderColor", darkBorder);
        UIManager.put("Component.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        
        // 체크박스 & 라디오버튼
        UIManager.put("CheckBox.icon.selectedBackground", neonGreen);
        UIManager.put("CheckBox.icon.checkmarkColor", darkBg);
        UIManager.put("RadioButton.icon.selectedBackground", neonPink);
        
        // 프로그레스바
        UIManager.put("ProgressBar.foreground", neonCyan);
        UIManager.put("ProgressBar.background", darkBg3);
        UIManager.put("ProgressBar.selectionBackground", neonCyan);
        
        // 탭
        UIManager.put("TabbedPane.selectedBackground", darkBg2);
        UIManager.put("TabbedPane.underlineColor", neonPink);
        UIManager.put("TabbedPane.focusColor", neonCyan);
        UIManager.put("TabbedPane.background", darkBg);
        
        // 메뉴
        UIManager.put("MenuBar.background", darkBg);
        UIManager.put("Menu.background", darkBg2);
        UIManager.put("Menu.foreground", textLight);
        UIManager.put("MenuItem.background", darkBg2);
        UIManager.put("MenuItem.foreground", textLight);
        UIManager.put("MenuItem.selectionBackground", neonPurple);
        UIManager.put("MenuItem.selectionForeground", textLight);
        
        // 툴팁
        UIManager.put("ToolTip.background", neonCyan);
        UIManager.put("ToolTip.foreground", darkBg);
        
        // 옵션 다이얼로그
        UIManager.put("OptionPane.background", darkBg);
        UIManager.put("OptionPane.foreground", textLight);
        UIManager.put("OptionPane.messageForeground", textLight);
        
        // 다이얼로그
        UIManager.put("Dialog.background", darkBg);
        
        // 텍스트 영역
        UIManager.put("TextPane.background", darkBg2);
        UIManager.put("TextPane.foreground", textLight);
        UIManager.put("EditorPane.background", darkBg2);
        UIManager.put("EditorPane.foreground", textLight);
        
        // 스플릿 패널
        UIManager.put("SplitPane.background", darkBg);
        UIManager.put("SplitPane.dividerColor", darkBorder);
        
        // 슬라이더
        UIManager.put("Slider.trackColor", darkBg3);
        UIManager.put("Slider.thumbColor", neonPink);
        
        // 스피너
        UIManager.put("Spinner.background", darkBg2);
        UIManager.put("Spinner.foreground", textLight);
        
        // 툴바
        UIManager.put("ToolBar.background", darkBg);
        UIManager.put("ToolBar.foreground", textLight);
        
        // 내부 프레임
        UIManager.put("InternalFrame.background", darkBg);
        UIManager.put("InternalFrame.activeTitleBackground", neonPurple);
        UIManager.put("InternalFrame.activeTitleForeground", textLight);
        
        // 팝업 메뉴
        UIManager.put("PopupMenu.background", darkBg2);
        UIManager.put("PopupMenu.foreground", textLight);
        UIManager.put("PopupMenu.border", new LineBorder(neonCyan, 1));
        
        // 구분선
        UIManager.put("Separator.foreground", darkBorder);
        
        // 타이틀바 (FlatLaf 전용)
        UIManager.put("TitlePane.background", darkBg);
        UIManager.put("TitlePane.foreground", textLight);
        UIManager.put("TitlePane.inactiveBackground", darkBg2);
        
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("🌙 적용된 테마: 사이버펑크 네온 다크 테마");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  💎 네온 시안: #00FFFF");
        System.out.println("  💖 네온 핑크: #FF0080");
        System.out.println("  💜 네온 퍼플: #BF40FF");
        System.out.println("  💚 네온 그린: #39FF14");
        System.out.println("  🖤 다크 배경: #121218");
        System.out.println("  ✨ 미래적 & 세련된 디자인");
        System.out.println("═══════════════════════════════════════════════════════════");
        
      } catch (Exception e) {
        System.err.println("FlatLaf 테마 적용 실패: " + e.getMessage());
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
      }
      new AuthFrame().setVisible(true);
    });
  }
}
