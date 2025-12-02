package com.swingauth.ui;

import com.swingauth.config.ServerConfig;
import com.swingauth.model.User;
import com.swingauth.util.NetworkDiscovery;
import io.socket.client.IO;
import io.socket.client.Socket;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URISyntaxException;

public class MatchingFrame extends JFrame implements ThemeManager.ThemeChangeListener {
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private Socket socket;
    private JLabel statusLabel;
    private JButton startButton;
    private JButton endButton;
    private boolean isMatching = false;
    private boolean chatFrameOpened = false; // 채팅 화면이 열렸는지 여부
    private Runnable onMatchedCallback;
    private User user;
    private String partnerUsername = null; // 매칭된 상대방 username

    public MatchingFrame(User user, Runnable onMatched) {
        this.user = user;
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
        statusLabel.setForeground(ThemeManager.TEXT_LIGHT);
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(statusLabel, gbc);

        startButton = new JButton("매칭 시작");
        startButton.setPreferredSize(new Dimension(150, 40));
        startButton.setBackground(ThemeManager.NEON_CYAN);
        startButton.setForeground(ThemeManager.DARK_BG);
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> startMatching());
        gbc.gridy = 1;
        centerPanel.add(startButton, gbc);

        endButton = new JButton("매칭 종료");
        endButton.setPreferredSize(new Dimension(150, 40));
        endButton.setBackground(ThemeManager.NEON_PINK);
        endButton.setForeground(Color.WHITE);
        endButton.setFocusPainted(false);
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

        // Socket.io 연결 (네트워크 자동 발견 포함)
        connectSocket();
        
        // ThemeManager에 리스너 등록
        themeManager.addThemeChangeListener(this);
        
        // 초기 테마 적용
        applyTheme();
    }
    
    @Override
    public void onThemeChanged() {
        applyTheme();
    }
    
    private void applyTheme() {
        boolean isDarkMode = themeManager.isDarkMode();
        
        getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        JPanel topPanel = (JPanel) getContentPane().getComponent(0);
        topPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        JPanel centerPanel = (JPanel) getContentPane().getComponent(1);
        centerPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        statusLabel.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        
        if (isDarkMode) {
            startButton.setBackground(ThemeManager.NEON_CYAN);
            startButton.setForeground(ThemeManager.DARK_BG);
            endButton.setBackground(ThemeManager.NEON_PINK);
            endButton.setForeground(Color.WHITE);
        } else {
            startButton.setBackground(ThemeManager.LIGHT_CYAN);
            startButton.setForeground(Color.WHITE);
            endButton.setBackground(ThemeManager.LIGHT_PINK);
            endButton.setForeground(Color.WHITE);
        }
        
        JButton exitButton = (JButton) topPanel.getComponent(0);
        exitButton.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        exitButton.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        exitButton.setBorder(BorderFactory.createLineBorder(
            isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
        
        SwingUtilities.updateComponentTreeUI(this);
    }

    private void connectSocket() {
        // 먼저 네트워크에서 서버를 자동으로 찾기 시도 (백그라운드 스레드)
        String currentServerHost = ServerConfig.getServerHost();
        // localhost나 이미 설정된 IP가 없으면 네트워크에서 찾기
        if (currentServerHost == null || currentServerHost.equals("localhost") || currentServerHost.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("네트워크에서 서버를 찾는 중...");
            });
            
            // 백그라운드 스레드에서 서버 찾기
            Thread discoveryThread = new Thread(() -> {
                System.out.println("네트워크에서 서버를 자동으로 찾는 중...");
                String discoveredServerIP = NetworkDiscovery.discoverServer(3000); // 3초 동안 찾기
                
                SwingUtilities.invokeLater(() -> {
                    if (discoveredServerIP != null && !discoveredServerIP.isEmpty()) {
                        System.out.println("서버를 자동으로 발견했습니다: " + discoveredServerIP);
                        ServerConfig.setServerHost(discoveredServerIP);
                        statusLabel.setText("서버 발견: " + discoveredServerIP + " (연결 중...)");
                    } else {
                        System.out.println("네트워크에서 서버를 찾을 수 없습니다. 기존 설정 사용: " + currentServerHost);
                        // 로컬 IP 감지 시도
                        String localIP = NetworkDiscovery.detectLocalIP();
                        if (localIP != null && !localIP.equals("localhost") && !localIP.isEmpty()) {
                            ServerConfig.setServerHost(localIP);
                            System.out.println("로컬 IP 사용: " + localIP);
                        }
                        statusLabel.setText("서버 연결 중...");
                    }
                    // 서버 찾기 완료 후 실제 연결 시도
                    tryConnectSocket();
                });
            });
            discoveryThread.setDaemon(true);
            discoveryThread.start();
        } else {
            // 이미 서버 IP가 설정되어 있으면 바로 연결
            tryConnectSocket();
        }
    }
    
    private void tryConnectSocket() {
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
                    // username 등록 (연결 직후 즉시 등록)
                    if (user != null && user.username != null && !user.username.isBlank()) {
                        socket.emit("registerUsername", user.username);
                        System.out.println("Username 등록 전송: " + user.username + " (Socket ID: " + socket.id() + ")");
                    } else {
                        System.err.println("경고: username이 없어 등록할 수 없습니다. user=" + user);
                    }
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
                    Exception error = args.length > 0 && args[0] instanceof Exception 
                        ? (Exception) args[0] 
                        : null;
                    String errorMsg = error != null ? error.getMessage() : "알 수 없음";
                    System.out.println("Socket 연결 오류: " + errorMsg);
                    System.out.println("연결 시도한 서버: " + ServerConfig.getServerURL());
                    if (error != null) {
                        error.printStackTrace();
                    }
                    
                    String statusMsg = "서버 연결 실패: " + errorMsg;
                    if (errorMsg.contains("Connection refused") || errorMsg.contains("connect")) {
                        statusMsg += "\n서버가 실행 중인지 확인하세요.";
                        
                        // 연결 실패 시 네트워크에서 서버를 자동으로 찾기 시도
                        statusLabel.setText("네트워크에서 서버를 찾는 중...");
                        
                        // 백그라운드 스레드에서 서버 찾기 시도
                        Thread discoveryThread = new Thread(() -> {
                            System.out.println("연결 실패 후 네트워크에서 서버를 자동으로 찾는 중...");
                            String discoveredServerIP = NetworkDiscovery.discoverServer(5000); // 5초 동안 찾기
                            
                            SwingUtilities.invokeLater(() -> {
                                if (discoveredServerIP != null && !discoveredServerIP.isEmpty()) {
                                    System.out.println("서버를 자동으로 발견했습니다: " + discoveredServerIP);
                                    ServerConfig.setServerHost(discoveredServerIP);
                                    statusLabel.setText("서버 발견: " + discoveredServerIP + " (재연결 중...)");
                                    
                                    // 자동으로 재연결 시도
                                    try {
                                        if (socket != null && socket.connected()) {
                                            socket.disconnect();
                                        }
                                        Thread.sleep(500);
                                        connectSocket();
                                    } catch (Exception e) {
                                        System.err.println("자동 재연결 실패: " + e.getMessage());
                                        e.printStackTrace();
                                        showServerIPDialog();
                                    }
                                } else {
                                    // 서버를 찾지 못한 경우 사용자에게 IP 입력 다이얼로그 표시
                                    showServerIPDialog();
                                }
                            });
                        });
                        discoveryThread.setDaemon(true);
                        discoveryThread.start();
                    } else {
                        statusLabel.setText(statusMsg);
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
                    
                    // partnerUsername 저장
                    try {
                        if (args.length > 0 && args[0] instanceof String) {
                            org.json.JSONObject data = new org.json.JSONObject(args[0].toString());
                            partnerUsername = data.optString("partnerUsername", "unknown");
                            System.out.println("MatchingFrame: matched 이벤트 수신 - partnerUsername=" + partnerUsername);
                        }
                    } catch (Exception e) {
                        System.err.println("MatchingFrame: matched 이벤트 파싱 오류:");
                        e.printStackTrace();
                        partnerUsername = "unknown";
                    }
                    
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
    
    public String getPartnerUsername() {
        return partnerUsername;
    }
    
    /**
     * 서버 IP 입력 다이얼로그를 표시하고 재연결을 시도하는 헬퍼 메서드
     */
    private void showServerIPDialog() {
        int option = JOptionPane.showConfirmDialog(
            MatchingFrame.this,
            "서버에 연결할 수 없습니다.\n\n" +
            "현재 시도한 주소: " + ServerConfig.getServerURL() + "\n\n" +
            "서버 IP 주소를 변경하시겠습니까?",
            "서버 연결 실패",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE
        );
        
        if (option == JOptionPane.YES_OPTION) {
            // 서버 IP 입력 다이얼로그 표시
            if (ServerIPDialog.showDialog(MatchingFrame.this)) {
                // 새로운 서버 주소로 재연결 시도
                try {
                    if (socket != null && socket.connected()) {
                        socket.disconnect();
                    }
                    connectSocket();
                } catch (Exception e) {
                    System.err.println("재연결 실패: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
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

