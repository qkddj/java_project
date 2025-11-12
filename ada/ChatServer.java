// ChatServer.java
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 5000;
    private static final List<ClientHandler> waiting = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("[서버] 시작. 포트: " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                handler.start(); // 각 클라이언트마다 스레드 시작 (대기 및 매칭 처리)
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 클라이언트 핸들러 (스레드)
    private static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private ClientHandler partner; // 매칭된 상대

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            } catch (IOException e) {
                closeEverything();
            }
        }

        @Override
        public void run() {
            try {
                // 매칭 프로세스
                synchronized (waiting) {
                    if (waiting.isEmpty()) {
                        waiting.add(this);
                        out.println("[서버] 접속 완료. 상대를 기다리는 중...");
                        // 현재 스레드는 매칭될 때까지 기다림 (notify로 깨움)
                        // wait는 synchronized 블록 안에서 호출되어야 함
                        // 여기서는 synchronized(waiting) 블록을 끝내고 아래에서 동기화해서 기다림
                    } else {
                        // 짝이 있으면 바로 매칭
                        ClientHandler other = waiting.remove(0);
                        this.partner = other;
                        other.partner = this;

                        // 알림 전송
                        this.out.println("[서버] 매칭 완료! 대화를 시작하세요.");
                        other.out.println("[서버] 매칭 완료! 대화를 시작하세요.");
                        
                        // 다른 스레드가 run에서(read loop) 동작하도록 알림
                        synchronized (other) {
                            other.notify(); // other는 waiting에서 빠져나와 대기중일 수 있음
                        }
                    }
                }

                // 만약 아직 partner가 없으면 객체 자체에서 wait
                synchronized (this) {
                    while (this.partner == null) {
                        try {
                            this.wait(); // 매칭될 때까지 대기
                        } catch (InterruptedException ignored) {}
                    }
                }

                // partner가 설정되어 이제 양방향 중계 시작
                String line;
                while ((line = in.readLine()) != null) {
                    // 상대가 존재하면 전달
                    if (partner != null && partner.out != null) {
                        partner.out.println("[상대] " + line);
                    } else {
                        out.println("[서버] 현재 상대가 없습니다.");
                    }
                }

            } catch (IOException e) {
                // 읽기 중 예외 발생 (연결 끊김 등)
            } finally {
                // 정리: 소켓 닫기 및 상대에게 알림
                try {
                    if (partner != null && partner.out != null) {
                        partner.out.println("[서버] 상대 연결이 종료되었습니다.");
                        // 상대측 partner 참조 제거
                        partner.partner = null;
                    }
                } catch (Exception ignored) {}

                // 만약 아직 waiting 리스트에 남아있다면 제거
                synchronized (waiting) {
                    waiting.remove(this);
                }

                closeEverything();
            }
        }

        private void closeEverything() {
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        }
    }
}
