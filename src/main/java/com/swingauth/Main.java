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
        }
      } catch (Exception e) {
        System.err.println("채팅 서버 시작 실패: " + e.getMessage());
        e.printStackTrace();
      }
    });
    serverThread.setDaemon(true);
    serverThread.start();

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
