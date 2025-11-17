package com.swingauth;

import com.swingauth.chat.server.ChatServer;
import com.swingauth.ui.AuthFrame;

import javax.swing.*;

public class Main {
  public static void main(String[] args) {
    // 환경 변수로 서버 실행 여부 제어
    // CHAT_SERVER_ENABLED=true로 설정하면 서버 실행, 설정 안 하면 서버 실행 안 함
    String serverEnabled = System.getenv("CHAT_SERVER_ENABLED");
    String serverEnabledProp = System.getProperty("chat.server.enabled");
    boolean shouldStartServer = false;
    
    // 디버깅: 환경 변수 값 출력
    System.out.println("=== 서버 설정 확인 ===");
    System.out.println("CHAT_SERVER_ENABLED (환경 변수): " + serverEnabled);
    System.out.println("chat.server.enabled (시스템 속성): " + serverEnabledProp);
    
    // 우선순위: 시스템 속성 > 환경 변수 > 기본값(false)
    if (serverEnabledProp != null) {
      shouldStartServer = Boolean.parseBoolean(serverEnabledProp);
      System.out.println("시스템 속성에서 서버 실행 여부 결정: " + shouldStartServer);
    } else if (serverEnabled != null) {
      shouldStartServer = Boolean.parseBoolean(serverEnabled);
      System.out.println("환경 변수에서 서버 실행 여부 결정: " + shouldStartServer);
    } else {
      // 기본값: 서버 실행 안 함 (클라이언트만 사용)
      shouldStartServer = false;
      System.out.println("기본값 사용: 서버 실행 안 함");
    }
    System.out.println("최종 결정: 서버 실행 = " + shouldStartServer);
    System.out.println("===================");
    
    if (shouldStartServer) {
      // 백그라운드에서 채팅 서버 시작
      Thread serverThread = new Thread(() -> {
        try {
          // 서버 시작 전 약간의 지연
          Thread.sleep(500);
          ChatServer server = ChatServer.getInstance();
          if (!server.isRunning()) {
            System.out.println("채팅 서버를 시작하는 중...");
            server.start();
            // 서버가 완전히 시작될 때까지 대기
            Thread.sleep(1000);
          }
        } catch (Exception e) {
          System.err.println("채팅 서버 시작 실패: " + e.getMessage());
          e.printStackTrace();
        }
      });
      serverThread.setDaemon(true);
      serverThread.start();
    } else {
      System.out.println("서버 모드 비활성화됨. 클라이언트 모드로 실행됩니다.");
      System.out.println("서버를 실행하려면 환경 변수를 설정하세요:");
      System.out.println("  Windows: set CHAT_SERVER_ENABLED=true");
      System.out.println("  macOS/Linux: export CHAT_SERVER_ENABLED=true");
    }

    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {}
      new AuthFrame().setVisible(true);
    });

    // 애플리케이션 종료 시 서버 정리 (서버가 실행 중인 경우만)
    if (shouldStartServer) {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        ChatServer.getInstance().stop();
      }));
    }
  }
}
