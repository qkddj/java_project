package com.test.video.swing;

import com.test.video.ServerLauncher;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerLauncherWrapper {
    private Thread serverThread;
    private AtomicInteger port = new AtomicInteger(8080);
    
    public void startServer() {
        if (serverThread != null && serverThread.isAlive()) {
            return; // 이미 실행 중
        }
        
        serverThread = new Thread(() -> {
            try {
                System.setProperty("PORT", "8080");
                ServerLauncher.main(new String[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        
        // 서버 시작 대기
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public int getPort() {
        return port.get();
    }
}

