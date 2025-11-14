package com.swingauth;

import com.swingauth.chat.server.ChatServer;
import com.swingauth.ui.AuthFrame;

import javax.swing.*;

public class Main {
  public static void main(String[] args) {
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
          System.out.println("채팅 서버가 준비되었습니다.");
        } else {
          System.out.println("채팅 서버가 이미 실행 중입니다. 기존 서버를 사용합니다.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        System.err.println("채팅 서버 시작 실패: " + e.getMessage());
        if (e.getCause() instanceof java.net.BindException) {
          System.err.println("포트가 이미 사용 중입니다. 이전 애플리케이션을 종료하고 다시 시도하세요.");
          System.err.println("포트를 해제하려면: kill -9 $(lsof -ti:3001)");
        }
        e.printStackTrace();
      }
    });
    serverThread.setDaemon(true);
    serverThread.start();

    // UI 시작
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {}
      new AuthFrame().setVisible(true);
    });

    // 애플리케이션 종료 시 서버 정리
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      ChatServer.getInstance().stop();
    }));
  }
}
