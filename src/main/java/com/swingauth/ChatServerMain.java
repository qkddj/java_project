package com.swingauth;

import com.swingauth.chat.server.ChatServer;

public class ChatServerMain {
    public static void main(String[] args) {
        System.out.println("=== 채팅 서버 시작 ===");
        
        try {
            ChatServer server = ChatServer.getInstance();
            if (!server.isRunning()) {
                System.out.println("채팅 서버를 시작하는 중...");
                server.start();
                System.out.println("채팅 서버가 실행 중입니다.");
                System.out.println("서버를 종료하려면 Ctrl+C를 누르세요.");
                
                // 서버가 계속 실행되도록 대기
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("\n서버 종료 중...");
                    server.stop();
                    System.out.println("서버가 종료되었습니다.");
                }));
                
                // 메인 스레드가 종료되지 않도록 대기
                Thread.currentThread().join();
            } else {
                System.out.println("채팅 서버가 이미 실행 중입니다.");
            }
        } catch (Exception e) {
            System.err.println("채팅 서버 시작 실패: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

