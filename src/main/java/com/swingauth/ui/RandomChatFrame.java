package com.swingauth.ui;

import com.swingauth.config.ServerConfig;
import com.swingauth.model.User;
import com.swingauth.service.RatingService;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RandomChatFrame extends JFrame implements ThemeManager.ThemeChangeListener {
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private Socket socket;
    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel charCountLabel;
    private boolean isConnected = false;
    private static final int MAX_CHARS = 100;
    private User currentUser;
    private String partnerId; // 상대방 ID (Socket ID)
    private String partnerUsername; // 상대방 username
    private boolean ratingShown = false; // 평점 다이얼로그가 이미 표시되었는지 여부
    private boolean chatStarted = false; // 채팅이 한 번이라도 시작되었는지 여부 (평점을 남길 수 있는지 확인용)

    public RandomChatFrame(Socket existingSocket, User user) {
        this(existingSocket, user, null);
    }
    
    public RandomChatFrame(Socket existingSocket, User user, String partnerUsername) {
        // 기존 소켓 사용
        this.socket = existingSocket;
        this.currentUser = user;
        this.partnerUsername = partnerUsername; // MatchingFrame에서 전달받은 partnerUsername
        if (partnerUsername != null) {
            System.out.println("RandomChatFrame 생성: partnerUsername=" + partnerUsername);
        }
        setTitle("랜덤 채팅");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 상단: 매칭 종료 버튼
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton exitButton = new JButton("← 매칭 종료");
        ThemeManager.disableButtonPressedEffect(exitButton);
        exitButton.addActionListener(e -> closeWindow());
        topPanel.add(exitButton);
        add(topPanel, BorderLayout.NORTH);

        // 중앙: 채팅 영역
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setContentType("text/html");
        chatArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        chatArea.setBorder(new LineBorder(ThemeManager.NEON_CYAN, 1));
        chatArea.setBackground(ThemeManager.DARK_BG2);
        // HTML 문서 초기화
        HTMLEditorKit kit = new HTMLEditorKit();
        HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
        chatArea.setEditorKit(kit);
        chatArea.setDocument(doc);
        // 기본 스타일 설정
        String style = "body { font-family: sans-serif; font-size: 14px; margin: 0; padding: 5px; }";
        kit.getStyleSheet().addRule(style);
        // 초기 HTML 구조 설정
        try {
            chatArea.setText("<html><body></body></html>");
        } catch (Exception e) {
            e.printStackTrace();
        }
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(scrollPane, BorderLayout.CENTER);

        // 하단: 입력 영역
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputField = new JTextField();
        inputField.setBorder(new LineBorder(ThemeManager.DARK_BORDER, 1));
        inputField.setEnabled(false);
        inputField.setPreferredSize(new Dimension(0, 35));
        
        // 입력 필드에 100자 제한 적용
        ((AbstractDocument) inputField.getDocument()).setDocumentFilter(
            new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) 
                        throws BadLocationException {
                    String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
                    int newLength = currentText.length() + string.length();
                    if (newLength <= MAX_CHARS) {
                        super.insertString(fb, offset, string, attr);
                    } else {
                        // 100자 초과 시 알람 표시
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(RandomChatFrame.this, 
                                "메시지는 최대 " + MAX_CHARS + "자까지 입력할 수 있습니다.",
                                "입력 제한", JOptionPane.WARNING_MESSAGE);
                        });
                        // 가능한 만큼만 삽입
                        int remaining = MAX_CHARS - currentText.length();
                        if (remaining > 0) {
                            super.insertString(fb, offset, string.substring(0, remaining), attr);
                        }
                    }
                }
                
                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) 
                        throws BadLocationException {
                    String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
                    int newLength = currentText.length() - length + text.length();
                    if (newLength <= MAX_CHARS) {
                        super.replace(fb, offset, length, text, attrs);
                    } else {
                        // 100자 초과 시 알람 표시
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(RandomChatFrame.this, 
                                "메시지는 최대 " + MAX_CHARS + "자까지 입력할 수 있습니다.",
                                "입력 제한", JOptionPane.WARNING_MESSAGE);
                        });
                        // 가능한 만큼만 교체
                        int remaining = MAX_CHARS - (currentText.length() - length);
                        if (remaining > 0) {
                            super.replace(fb, offset, length, text.substring(0, Math.min(remaining, text.length())), attrs);
                        }
                    }
                }
            }
        );
        
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && isConnected) {
                    sendMessage();
                }
            }
        });
        inputPanel.add(inputField, BorderLayout.CENTER);

        charCountLabel = new JLabel("0/" + MAX_CHARS);
        charCountLabel.setFont(charCountLabel.getFont().deriveFont(12f));
        charCountLabel.setForeground(ThemeManager.TEXT_DIM);
        inputPanel.add(charCountLabel, BorderLayout.EAST);

        sendButton = new JButton("보내기");
        sendButton.setPreferredSize(new Dimension(80, 35));
        sendButton.setEnabled(false);
        ThemeManager.disableButtonPressedEffect(sendButton);
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.WEST);

        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // 입력 필드 글자 수 표시 업데이트
        inputField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateCharCount();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateCharCount();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateCharCount();
            }
        });

        // 창 닫기 이벤트
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });

        // Socket.io 연결 (기존 소켓이 있으면 재사용, 없으면 새로 연결)
        if (socket != null && socket.connected()) {
            setupSocketListeners();
            // username 등록 (이미 연결된 소켓이어도 등록)
            if (currentUser != null && currentUser.username != null && !currentUser.username.isBlank()) {
                socket.emit("registerUsername", currentUser.username);
                System.out.println("RandomChatFrame: Username 등록 전송: " + currentUser.username + " (Socket ID: " + socket.id() + ")");
            }
            // 이미 매칭된 상태이므로 바로 활성화
            isConnected = true;
            chatStarted = true; // 채팅이 시작되었음을 표시
            inputField.setEnabled(true);
            sendButton.setEnabled(true);
            addMessage("시스템", "매칭 완료! 대화를 시작하세요.", false);
            // 이미 매칭된 상태이므로 partnerUsername은 나중에 matched 이벤트에서 받을 수 있음
            // 하지만 이미 매칭된 경우 matched 이벤트가 다시 발생하지 않을 수 있으므로
            // partnerUsername은 null로 초기화 (matched 이벤트에서 업데이트됨)
            partnerUsername = null;
            System.out.println("이미 매칭된 상태로 RandomChatFrame 생성됨. partnerUsername은 matched 이벤트에서 받을 예정");
        } else {
            connectSocket();
        }
        
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
        
        // JOptionPane 배경색 설정
        UIManager.put("OptionPane.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        UIManager.put("Panel.background", isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        UIManager.put("OptionPane.messageForeground", isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        
        getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        JPanel topPanel = (JPanel) getContentPane().getComponent(0);
        topPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        chatArea.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        chatArea.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        chatArea.setBorder(new LineBorder(isDarkMode ? ThemeManager.NEON_CYAN : ThemeManager.LIGHT_CYAN, 1));
        
        JPanel bottomPanel = (JPanel) getContentPane().getComponent(2);
        bottomPanel.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        inputField.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        inputField.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        inputField.setBorder(new LineBorder(isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
        
        charCountLabel.setForeground(isDarkMode ? ThemeManager.TEXT_DIM : ThemeManager.LIGHT_BORDER);
        
        sendButton.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        sendButton.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        sendButton.setBorder(BorderFactory.createLineBorder(
            isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
        ThemeManager.disableButtonPressedEffect(sendButton);
        
        JButton exitButton = (JButton) topPanel.getComponent(0);
        exitButton.setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        exitButton.setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        exitButton.setBorder(BorderFactory.createLineBorder(
            isDarkMode ? ThemeManager.DARK_BORDER : ThemeManager.LIGHT_BORDER, 1));
        ThemeManager.disableButtonPressedEffect(exitButton);
        
        JScrollPane scrollPane = (JScrollPane) getContentPane().getComponent(1);
        scrollPane.setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        scrollPane.getViewport().setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
        
        SwingUtilities.updateComponentTreeUI(this);
    }

    private void setupSocketListeners() {
        if (socket == null) return;
        
        // 기존 리스너 제거 (중복 방지)
        socket.off();
        
        // 연결 오류 처리
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            SwingUtilities.invokeLater(() -> {
                String errorMsg = args.length > 0 ? args[0].toString() : "알 수 없는 오류";
                System.err.println("소켓 연결 오류: " + errorMsg);
                addMessage("시스템", "서버 연결 오류: " + errorMsg, false);
                JOptionPane.showMessageDialog(this, 
                    "서버 연결에 실패했습니다.\n서버가 실행 중인지 확인하세요.\n\n서버 주소: " + ServerConfig.getServerURL(),
                    "연결 오류", JOptionPane.ERROR_MESSAGE);
            });
        });
        
        // 연결 해제 처리
        socket.on(Socket.EVENT_DISCONNECT, args -> {
            SwingUtilities.invokeLater(() -> {
                System.out.println("소켓 연결 해제");
                addMessage("시스템", "서버 연결이 끊어졌습니다.", false);
                isConnected = false;
                inputField.setEnabled(false);
                sendButton.setEnabled(false);
            });
        });
        
        socket.on(Socket.EVENT_CONNECT, args -> {
            SwingUtilities.invokeLater(() -> {
                System.out.println("소켓 연결 성공: " + socket.id());
                addMessage("시스템", "서버에 연결되었습니다.", false);
                // username 등록
                if (currentUser != null && currentUser.username != null && !currentUser.username.isBlank()) {
                    socket.emit("registerUsername", currentUser.username);
                    System.out.println("RandomChatFrame: Username 등록 전송: " + currentUser.username + " (Socket ID: " + socket.id() + ")");
                }
            });
        });

        socket.on("receiveMessage", args -> {
            try {
                String jsonStr = args[0].toString();
                JSONObject data = new JSONObject(jsonStr);
                String text = data.getString("text");
                SwingUtilities.invokeLater(() -> {
                    addMessage("상대방", text, false);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        socket.on("partnerDisconnected", args -> {
            SwingUtilities.invokeLater(() -> {
                isConnected = false;
                inputField.setEnabled(false);
                sendButton.setEnabled(false);
                addMessage("시스템", "상대방이 연결을 종료했습니다. 원하시면 매칭 종료 버튼을 눌러 평점을 남겨주세요.", false);
                // 상대방이 종료했다는 알림만 표시하고, 사용자가 직접 종료할 때 평점 다이얼로그 표시
                // 창은 자동으로 닫지 않음 - 사용자가 직접 종료 버튼을 눌러야 함
            });
        });

        socket.on("matched", args -> {
            SwingUtilities.invokeLater(() -> {
                isConnected = true;
                chatStarted = true; // 채팅이 시작되었음을 표시
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                addMessage("시스템", "매칭 완료! 대화를 시작하세요.", false);
                // 상대방 ID 및 username 저장
                try {
                    System.out.println("matched 이벤트 수신: args.length=" + args.length);
                    if (args.length > 0) {
                        String argStr = args[0].toString();
                        System.out.println("matched 이벤트 데이터: " + argStr);
                        JSONObject data = new JSONObject(argStr);
                        partnerId = data.optString("partnerId", "anonymous");
                        partnerUsername = data.optString("partnerUsername", "unknown");
                        System.out.println("매칭 완료: partnerId=" + partnerId + ", partnerUsername=" + partnerUsername);
                        
                        if (partnerUsername == null || partnerUsername.equals("unknown") || partnerUsername.equals("anonymous")) {
                            System.err.println("경고: partnerUsername이 유효하지 않습니다: " + partnerUsername);
                        }
                    } else {
                        System.err.println("경고: matched 이벤트에 데이터가 없습니다.");
                        partnerId = "anonymous";
                        partnerUsername = "unknown";
                    }
                } catch (Exception e) {
                    System.err.println("matched 이벤트 처리 중 오류 발생:");
                    e.printStackTrace();
                    partnerId = "anonymous";
                    partnerUsername = "unknown";
                }
            });
        });
    }

    private void updateCharCount() {
        int length = inputField.getText().length();
        charCountLabel.setText(length + "/" + MAX_CHARS);
        boolean isDarkMode = themeManager.isDarkMode();
        if (length > MAX_CHARS) {
            charCountLabel.setForeground(ThemeManager.NEON_PINK);
        } else {
            charCountLabel.setForeground(isDarkMode ? ThemeManager.TEXT_DIM : ThemeManager.LIGHT_BORDER);
        }
    }

    private void connectSocket() {
        try {
            String serverURL = ServerConfig.getServerURL();
            System.out.println("소켓 연결 시도: " + serverURL);
            
            IO.Options options = IO.Options.builder()
                    .setTransports(new String[]{"websocket", "polling"})
                    .setReconnection(true)
                    .setReconnectionAttempts(5)
                    .setReconnectionDelay(1000)
                    .setTimeout(20000)
                    .build();
            socket = IO.socket(serverURL, options);
            setupSocketListeners();
            socket.connect();
            
            // 연결 타임아웃 체크
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // 5초 대기
                    if (socket != null && !socket.connected()) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, 
                                "서버 연결 시간이 초과되었습니다.\n\n서버 주소: " + serverURL + "\n서버가 실행 중인지 확인하세요.",
                                "연결 타임아웃", JOptionPane.WARNING_MESSAGE);
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        } catch (URISyntaxException e) {
            System.err.println("서버 URL 오류: " + e.getMessage());
            JOptionPane.showMessageDialog(this, 
                "서버 연결 실패: " + e.getMessage() + "\n\n서버 주소: " + ServerConfig.getServerURL(),
                "연결 오류", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            System.err.println("소켓 연결 중 예외 발생: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "소켓 연결 중 오류가 발생했습니다: " + e.getMessage() + "\n\n서버 주소: " + ServerConfig.getServerURL(),
                "연결 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        
        if (text.length() > MAX_CHARS) {
            JOptionPane.showMessageDialog(this, 
                "메시지는 최대 " + MAX_CHARS + "자까지 입력할 수 있습니다.\n현재: " + text.length() + "자",
                "입력 제한", JOptionPane.WARNING_MESSAGE);
            // 100자로 제한
            inputField.setText(text.substring(0, MAX_CHARS));
            return;
        }

        if (socket != null && socket.connected() && isConnected) {
            System.out.println("메시지 전송: " + text + ", Socket ID: " + socket.id());
            socket.emit("sendMessage", text);
            addMessage("나", text, true);
            inputField.setText("");
        } else {
            System.out.println("메시지 전송 실패 - Socket: " + (socket != null ? "있음" : "null") + 
                             ", 연결됨: " + (socket != null && socket.connected()) + 
                             ", 매칭됨: " + isConnected);
            JOptionPane.showMessageDialog(this, "메시지를 보낼 수 없습니다. 연결 상태를 확인하세요.",
                    "오류", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void addMessage(String sender, String text, boolean isMine) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String time = sdf.format(new Date());
        
        // HTML 이스케이프 처리
        String escapedText = text.replace("&", "&amp;")
                                 .replace("<", "&lt;")
                                 .replace(">", "&gt;")
                                 .replace("\"", "&quot;");
        
        // 메시지 형식 그대로 유지: [시간] 발신자: 메시지
        String messageText = String.format("[%s] %s: %s", time, sender, escapedText);
        
        // 정렬 설정: 내 메시지는 오른쪽, 상대방/시스템 메시지는 왼쪽
        String alignment = isMine ? "right" : "left";
        String htmlMessage = String.format(
            "<div style='text-align: %s;'>%s</div>",
            alignment, messageText
        );
        
        try {
            // 기존 내용 가져오기
            String currentContent = chatArea.getText();
            String bodyContent = "";
            
            // body 태그 내용 추출
            if (currentContent.contains("<body>") && currentContent.contains("</body>")) {
                int bodyStart = currentContent.indexOf("<body>") + 6;
                int bodyEnd = currentContent.indexOf("</body>");
                bodyContent = currentContent.substring(bodyStart, bodyEnd);
            }
            
            // 새 메시지 추가
            String newContent = "<html><body>" + bodyContent + htmlMessage + "</body></html>";
            chatArea.setText(newContent);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        } catch (Exception e) {
            e.printStackTrace();
            // 오류 발생 시 일반 텍스트로 추가
            String fallbackMessage = String.format("[%s] %s: %s\n", time, sender, text);
            String currentText = chatArea.getText();
            if (currentText.contains("<body>") && currentText.contains("</body>")) {
                int bodyStart = currentText.indexOf("<body>") + 6;
                int bodyEnd = currentText.indexOf("</body>");
                String bodyContent = currentText.substring(bodyStart, bodyEnd);
                chatArea.setText("<html><body>" + bodyContent + fallbackMessage + "</body></html>");
            } else {
                chatArea.setText("<html><body>" + fallbackMessage + "</body></html>");
            }
        }
    }

    private void closeWindow() {
        // 평점 다이얼로그 표시 후 종료
        showRatingAndClose();
    }
    
    private void showRatingAndClose() {
        // 평점 다이얼로그 표시 (이미 표시된 경우 중복 방지)
        if (ratingShown) {
            // 이미 평점을 표시했으면 바로 종료
            closeWithoutRating();
            return;
        }
        
        // 채팅이 시작되었고 사용자 정보가 있으면 평점 다이얼로그 표시
        // isConnected가 false여도 (상대방이 먼저 종료했어도) 평점을 남길 수 있음
        if (currentUser != null && chatStarted) {
            ratingShown = true;
            RatingDialog ratingDialog = new RatingDialog(this);
            int rating = ratingDialog.showRatingDialog();
            
            // rating이 0이면 건너뛰고, 1-5면 평점을 저장
            if (rating == 0) {
                System.out.println("평점 건너뛰기 선택 - DB에 저장하지 않습니다.");
            } else if (rating > 0) {
                // 평점 저장
                try {
                    RatingService ratingService = new RatingService();
                    // partnerId는 Socket ID이거나 username일 수 있음
                    // 실제 username을 사용해야 함
                    // partnerUsername 사용
                    String ratedUsername = partnerUsername;
                    System.out.println("평점 저장 시도: currentUser.username=" + currentUser.username + ", partnerUsername=" + partnerUsername + ", rating=" + rating);
                    
                    if (ratedUsername == null || ratedUsername.isBlank() || ratedUsername.equals("anonymous") || ratedUsername.equals("unknown")) {
                        // partnerUsername이 없으면 경고 메시지와 함께 디버그 정보 출력
                        System.err.println("평점 저장 실패: 상대방 username이 없습니다.");
                        System.err.println("  - partnerId: " + partnerId);
                        System.err.println("  - partnerUsername: " + partnerUsername);
                        System.err.println("  - chatStarted: " + chatStarted);
                        System.err.println("  - isConnected: " + isConnected);
                        
                        JOptionPane.showMessageDialog(this, 
                            "상대방 정보를 확인할 수 없어 평점을 저장할 수 없습니다.\n" +
                            "partnerUsername: " + (partnerUsername != null ? partnerUsername : "null"), 
                            "평점 저장 실패", 
                            JOptionPane.WARNING_MESSAGE);
                    } else {
                        try {
                            ratingService.createRating(currentUser.username, ratedUsername, rating);
                            JOptionPane.showMessageDialog(this, 
                                "평점이 저장되었습니다. 감사합니다!", 
                                "평점 제출 완료", 
                                JOptionPane.INFORMATION_MESSAGE);
                        } catch (IllegalArgumentException e) {
                            JOptionPane.showMessageDialog(this, 
                                "평점 저장 실패: " + e.getMessage(), 
                                "평점 저장 오류", 
                                JOptionPane.ERROR_MESSAGE);
                            System.err.println("평점 저장 실패: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // 사용자에게 알림 표시
                    JOptionPane.showMessageDialog(this, 
                        "평점 저장 실패: " + e.getMessage(), 
                        "평점 저장 오류", 
                        JOptionPane.ERROR_MESSAGE);
                    System.err.println("평점 저장 실패: " + e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, 
                        "평점 저장 중 오류가 발생했습니다: " + e.getMessage(), 
                        "평점 저장 오류", 
                        JOptionPane.ERROR_MESSAGE);
                    System.err.println("평점 저장 실패: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        closeWithoutRating();
    }
    
    private void closeWithoutRating() {
        // 매칭 종료 이벤트 전송 (서버에서 매칭 상태 정리를 위해 필요)
        // 서버가 상대방에게 partnerDisconnected를 보내지만, 
        // 상대방의 창은 자동으로 닫히지 않도록 이미 수정함
        if (socket != null && socket.connected()) {
            System.out.println("매칭 종료 요청 (소켓 연결 유지)");
            socket.emit("endMatching");
            // 소켓 연결은 유지 - disconnect() 호출하지 않음
        }
        
        // 채팅 메시지 초기화
        chatArea.setText("<html><body></body></html>");
        
        // dispose() 대신 setVisible(false)를 사용하여 소켓 연결 유지
        // dispose()는 창 리소스를 해제하면서 소켓 연결도 끊을 수 있음
        // setVisible(false)는 창만 숨기고 리소스는 유지하여 소켓 연결도 유지됨
        setVisible(false);
        
        // 창이 닫혔지만 소켓은 유지되어 상대방이 평점을 남길 수 있음
    }
}

