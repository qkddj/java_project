// 파일 이름: ChatClient.java
import java.net.*;
import java.io.*;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {
        System.out.println("[클라이언트] 서버에 접속 시도: " + SERVER_ADDRESS + ":" + SERVER_PORT);
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out   = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner   = new Scanner(System.in)) {

            System.out.println("[클라이언트] 접속 성공");

            Thread readerThread = new Thread(() -> {
                try {
                    String serverMsg;
                    while ((serverMsg = in.readLine()) != null) {
                        System.out.println("[서버→나] " + serverMsg);
                    }
                } catch (IOException e) {
                    System.err.println("[클라이언트] 서버 메시지 읽기 오류: " + e.getMessage());
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            while (true) {
                String userInput = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(userInput)) {
                    out.println(userInput);
                    System.out.println("[클라이언트] 종료 명령 전송");
                    break;
                }
                out.println(userInput);
                System.out.println("[클라이언트] 나→서버 : " + userInput);
            }

        } catch (IOException e) {
            System.err.println("[클라이언트] 접속 오류: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[클라이언트] 채팅 종료");
    }
}
