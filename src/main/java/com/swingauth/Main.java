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
          System.out.println("채팅 서버가 실행 중입니다.");
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

    // 클라이언트 UI 시작
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
