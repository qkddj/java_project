package com.swingauth;

import com.swingauth.ui.AuthFrame;
import com.swingauth.video.ServerLauncher;

import javax.swing.*;

public class Main {
  public static void main(String[] args) {
    // 애플리케이션 시작 시 영상통화 서버 시작 시도
    // 포트가 이미 사용 중이면 기존 서버에 연결
    try {
      ServerLauncher.getInstance().start();
    } catch (Exception e) {
      // 포트가 사용 중이면 기존 서버에 연결하는 것으로 간주하고 계속 진행
    }
    
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {}
      new AuthFrame().setVisible(true);
    });
  }
}
