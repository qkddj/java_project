// ChatClientGUI.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;

public class ChatClientGUI {
    private JFrame frame;
    private JTextArea textArea;
    private JTextField inputField;
    private JButton sendButton;
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;

    public ChatClientGUI(String serverAddress, int port) {
        initUI();
        connectToServer(serverAddress, port);
    }

    private void initUI() {
        frame = new JFrame("랜덤 채팅 클라이언트");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        inputField = new JTextField();
        inputField.setEnabled(false);

        sendButton = new JButton("보내기");
        sendButton.setEnabled(false);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);

        frame.setSize(480, 360);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // 창 닫을 때 리소스 정리
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closeEverything();
            }
        });
    }

    private void connectToServer(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            socket.setTcpNoDelay(true);

            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            SwingUtilities.invokeLater(() -> {
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                textArea.append("[서버 연결 성공]\n");
            });

            // 메시지 수신 스레드
            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        final String message = msg;
                        SwingUtilities.invokeLater(() -> {
                            textArea.append(message + "\n");
                            textArea.setCaretPosition(textArea.getDocument().getLength());
                        });
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> textArea.append("[연결 종료]\n"));
                } finally {
                    closeEverything();
                }
            }).start();

            // 공통 전송 동작 (엔터 & 버튼 둘 다 이 Runnable을 호출)
            Runnable sendAction = () -> {
                String message = inputField.getText().trim();
                if (message.isEmpty()) return;
                // 서버로 메시지 전송 (한 번만)
                out.println(message);
                // UI에 내가 보낸 메시지 표시 (UI 갱신은 EDT에서)
                SwingUtilities.invokeLater(() -> {
                    textArea.append("[나]: " + message + "\n");
                    textArea.setCaretPosition(textArea.getDocument().getLength());
                    inputField.setText("");
                });
            };

            // 엔터로 전송
            inputField.addActionListener(e -> sendAction.run());
            // 버튼으로 전송
            sendButton.addActionListener(e -> sendAction.run());

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> textArea.append("[서버에 연결할 수 없습니다: " + e.getMessage() + "]\n"));
            closeEverything();
        }
    }

    private void closeEverything() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientGUI("localhost", 5000));
    }
}
