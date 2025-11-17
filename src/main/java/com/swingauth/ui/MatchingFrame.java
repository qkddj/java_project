package com.swingauth.ui;

import com.swingauth.config.ServerConfig;
import io.socket.client.IO;
import io.socket.client.Socket;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URISyntaxException;

public class MatchingFrame extends JFrame {
    private Socket socket;
    private JLabel statusLabel;
    private JButton startButton;
    private JButton endButton;
    private boolean isMatching = false;
    private boolean chatFrameOpened = false; // 채팅 화면이 열렸는지 여부
    private Runnable onMatchedCallback;

    public MatchingFrame(Runnable onMatched) {
        this.onMatchedCallback = onMatched;
        setTitle("랜덤 채팅 매칭");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(400, 300);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 상단: 종료 버튼
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton exitButton = new JButton("← 종료");
        exitButton.addActionListener(e -> closeWindow());
        topPanel.add(exitButton);
        add(topPanel, BorderLayout.NORTH);

        // 중앙: 상태 표시 및 버튼
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);

        statusLabel = new JLabel("랜덤 채팅 매칭");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 18f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(statusLabel, gbc);

        startButton = new JButton("매칭 시작");
        startButton.setPreferredSize(new Dimension(150, 40));
        startButton.addActionListener(e -> startMatching());
        gbc.gridy = 1;
        centerPanel.add(startButton, gbc);

        endButton = new JButton("매칭 종료");
        endButton.setPreferredSize(new Dimension(150, 40));
        endButton.setEnabled(false);
        endButton.setVisible(false); // 초기에는 숨김
        endButton.addActionListener(e -> endMatching());
        gbc.gridy = 2;
        centerPanel.add(endButton, gbc);

        add(centerPanel, BorderLayout.CENTER);

        // 창 닫기 이벤트
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });

        // Socket.io 연결
        connectSocket();
    }

    private void connectSocket() {
        try {
            IO.Options options = IO.Options.builder()
                    .setTransports(new String[]{"websocket", "polling"}) // polling도 허용
                    .setReconnection(true)
                    .setReconnectionAttempts(5)
                    .setReconnectionDelay(1000)
                    .setTimeout(20000)
                    .build();
            socket = IO.socket(ServerConfig.getServerURL(), options);

            socket.on(Socket.EVENT_CONNECT, args -> {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("서버 연결됨");
                    System.out.println("Socket 연결됨: " + socket.id());
                });
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("서버 연결 끊김");
                    System.out.println("Socket 연결 끊김");
                });
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("서버 연결 실패 - 재연결 시도 중...");
                    Exception error = args.length > 0 && args[0] instanceof Exception 
                        ? (Exception) args[0] 
                        : null;
                    System.out.println("Socket 연결 오류: " + (error != null ? error.getMessage() : "알 수 없음"));
                    if (error != null) {
                        error.printStackTrace();
                    }
                });
            });

            socket.on("matchingStarted", args -> {
                SwingUtilities.invokeLater(() -> {
                    isMatching = true;
                    statusLabel.setText("매칭 중...");
                    startButton.setVisible(false);
                    endButton.setVisible(true);
                    endButton.setEnabled(true);
                });
            });

            socket.on("matched", args -> {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("매칭 완료!");
                    isMatching = false;
                    startButton.setVisible(true);
                    startButton.setEnabled(true);
                    endButton.setVisible(false);
                    endButton.setEnabled(false);
                    
                    // 채팅 화면으로 이동 (소켓 전달)
                    if (onMatchedCallback != null) {
                        chatFrameOpened = true; // 채팅 화면이 열렸음을 표시
                        onMatchedCallback.run();
                    }
                    // 소켓은 유지하고 창만 숨김 (소켓을 RandomChatFrame에서 사용)
                    // dispose()를 호출하지 않아서 소켓이 유지됨
                    setVisible(false);
                });
            });

            socket.on("matchingEnded", args -> {
                SwingUtilities.invokeLater(() -> {
                    isMatching = false;
                    statusLabel.setText("매칭 종료됨");
                    startButton.setVisible(true);
                    startButton.setEnabled(true);
                    endButton.setVisible(false);
                    endButton.setEnabled(false);
                });
            });

            socket.connect();
        } catch (URISyntaxException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage(),
                    "오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startMatching() {
        System.out.println("매칭 시작 버튼 클릭됨");
        if (socket == null) {
            System.out.println("Socket이 null입니다");
            JOptionPane.showMessageDialog(this, "Socket이 초기화되지 않았습니다.",
                    "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (!socket.connected()) {
            System.out.println("Socket이 연결되지 않았습니다. 연결 상태: " + socket.connected());
            JOptionPane.showMessageDialog(this, "서버에 연결되지 않았습니다.",
                    "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        System.out.println("startMatching 이벤트 전송 중... Socket ID: " + socket.id());
        // 서버가 String을 기대하므로 빈 문자열이나 JSON 문자열로 전송
        socket.emit("startMatching", "");
    }

    private void endMatching() {
        if (socket != null && socket.connected()) {
            socket.emit("endMatching");
        }
    }

    public Socket getSocket() {
        return socket;
    }
    
    private void closeWindow() {
        // 매칭 중이면 종료 이벤트 전송
        if (isMatching && socket != null && socket.connected()) {
            socket.emit("endMatching");
        }
        // 채팅 화면이 열려있으면 소켓 연결 유지 (채팅 화면에서 관리)
        // 채팅 화면이 없으면 소켓 연결 해제
        if (!chatFrameOpened && socket != null && socket.connected()) {
            socket.disconnect();
        }
        dispose();
    }
}

