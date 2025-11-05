package com.test.video.swing;

import com.test.video.ServerLauncher;

public class ServerLauncherWrapper {
    private Thread serverThread;
    
    public void startServer() {
        if (serverThread != null && serverThread.isAlive()) {
            return;
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
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public int getPort() { return 8080; }
}

