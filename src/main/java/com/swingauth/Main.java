package com.swingauth;

import com.swingauth.chat.server.ChatServer;
import com.swingauth.config.ServerConfig;
import com.swingauth.util.NetworkDiscovery;
import com.swingauth.ui.AuthFrame;

import javax.swing.*;

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
            String serverIP = NetworkDiscovery.detectLocalIP();
            if (!serverIP.equals("localhost")) {
              System.out.println("서버 IP 주소: " + serverIP);
              
              // 서버 IP를 ServerConfig에 설정 (클라이언트가 이 IP로 연결하도록)
              ServerConfig.setServerHost(serverIP);
              
              // 네트워크 발견 서비스 시작 (다른 컴퓨터가 자동으로 찾을 수 있도록)
              NetworkDiscovery.startServerListener(serverIP);
              NetworkDiscovery.startServerBroadcast(serverIP);
              System.out.println("네트워크 자동 발견 서비스 시작됨");
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
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {}
      new AuthFrame().setVisible(true);
    });
  }
}
