package com.swingauth.ui;

import com.swingauth.config.ServerConfig;
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

public class RandomChatFrame extends JFrame {
    private Socket socket;
    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel charCountLabel;
    private boolean isConnected = false;
    private static final int MAX_CHARS = 100;

    public RandomChatFrame(Socket existingSocket) {
        // 기존 소켓 사용
        this.socket = existingSocket;
        setTitle("랜덤 채팅");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 상단: 매칭 종료 버튼
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton exitButton = new JButton("← 매칭 종료");
        exitButton.addActionListener(e -> closeWindow());
        topPanel.add(exitButton);
        add(topPanel, BorderLayout.NORTH);

        // 중앙: 채팅 영역
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setContentType("text/html");
        chatArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        chatArea.setBorder(new LineBorder(Color.BLACK, 1));
        chatArea.setBackground(Color.WHITE);
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
        inputField.setBorder(new LineBorder(Color.BLACK, 1));
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
        charCountLabel.setForeground(Color.GRAY);
        inputPanel.add(charCountLabel, BorderLayout.EAST);

        sendButton = new JButton("보내기");
        sendButton.setPreferredSize(new Dimension(80, 35));
        sendButton.setEnabled(false);
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
            // 이미 매칭된 상태이므로 바로 활성화
            isConnected = true;
            inputField.setEnabled(true);
            sendButton.setEnabled(true);
            addMessage("시스템", "매칭 완료! 대화를 시작하세요.", false);
        } else {
            connectSocket();
        }
    }

    private void setupSocketListeners() {
        if (socket == null) return;
        
        // 기존 리스너 제거 (중복 방지)
        socket.off();
        
        socket.on(Socket.EVENT_CONNECT, args -> {
            SwingUtilities.invokeLater(() -> {
                addMessage("시스템", "서버에 연결되었습니다.", false);
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
                addMessage("시스템", "상대방이 연결을 종료했습니다.", false);
            });
        });

        socket.on("matched", args -> {
            SwingUtilities.invokeLater(() -> {
                isConnected = true;
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                addMessage("시스템", "매칭 완료! 대화를 시작하세요.", false);
            });
        });
    }

    private void updateCharCount() {
        int length = inputField.getText().length();
        charCountLabel.setText(length + "/" + MAX_CHARS);
        if (length > MAX_CHARS) {
            charCountLabel.setForeground(Color.RED);
        } else {
            charCountLabel.setForeground(Color.GRAY);
        }
    }

    private void connectSocket() {
        try {
            IO.Options options = IO.Options.builder()
                    .setTransports(new String[]{"websocket", "polling"})
                    .setReconnection(true)
                    .setReconnectionAttempts(5)
                    .setReconnectionDelay(1000)
                    .setTimeout(20000)
                    .build();
            socket = IO.socket(ServerConfig.getServerURL(), options);
            setupSocketListeners();
            socket.connect();
        } catch (URISyntaxException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage(),
                    "오류", JOptionPane.ERROR_MESSAGE);
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
        // 매칭 종료 이벤트만 전송하고 소켓 연결은 유지
        if (socket != null && socket.connected()) {
            System.out.println("매칭 종료 요청 (소켓 연결 유지)");
            socket.emit("endMatching");
            // 소켓 연결은 유지 - disconnect() 호출하지 않음
        }
        // 채팅 메시지 초기화
        chatArea.setText("<html><body></body></html>");
        dispose();
    }
}

