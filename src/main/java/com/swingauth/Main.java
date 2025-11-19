package com.swingauth;

import com.swingauth.ui.AuthFrame;
import com.swingauth.video.ServerLauncher;

import javax.swing.*;

public class Main {
  public static void main(String[] args) {
    // 애플리케이션 시작 시 영상통화 서버 시작
    System.out.println("========================================");
    System.out.println("애플리케이션 시작");
    System.out.println("========================================");
    
    try {
      ServerLauncher serverLauncher = ServerLauncher.getInstance();
      serverLauncher.start();
      
      // 서버 포트 확인
      int port = serverLauncher.getPort();
      if (port > 0) {
        System.out.println("비디오 통화 서버가 포트 " + port + "에서 실행 중입니다.");
        System.out.println("접속 URL: http://localhost:" + port + "/video-call.html");
      } else {
        System.out.println("경고: 서버 포트를 확인할 수 없습니다.");
      }
    } catch (Exception e) {
      System.err.println("서버 시작 실패: " + e.getMessage());
      e.printStackTrace();
      // 서버 시작 실패해도 애플리케이션은 계속 실행
      // (VideoCallFrame에서 다시 시도할 수 있음)
    }
    
    System.out.println("========================================");
    
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {}
      new AuthFrame().setVisible(true);
    });
  }
}
