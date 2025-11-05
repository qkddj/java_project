// 파일 이름: ChatServer.java
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 5000;
    private static final Queue<ClientHandler> waitingQueue = new ConcurrentLinkedQueue<>();

    public static void main(String[] args) {
        System.out.println("[서버] 시작 – 포트 " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[서버] 리스닝 중...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[서버] 클라이언트 접속: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("[서버] 소켓 생성 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private ClientHandler partner;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("채팅 서버에 접속했습니다. 매칭을 기다리는 중...");
                System.out.println("[핸들러] 클라이언트 대기 상태: " + socket.getRemoteSocketAddress());

                waitingQueue.add(this);
                matchPartner();

                System.out.println("[핸들러] 메시지 송수신 시작: " + socket.getRemoteSocketAddress() +
                                   " ↔ " + partner.socket.getRemoteSocketAddress());

                String msg;
                while ((msg = in.readLine()) != null) {
                    msg = msg.trim();
                    System.out.println("[받음][" + socket.getRemoteSocketAddress() + "] : " + msg);

                    if ("exit".equalsIgnoreCase(msg)) {
                        out.println("종료 명령 받음, 연결을 종료합니다.");
                        System.out.println("[핸들러] 종료 명령 처리: " + socket.getRemoteSocketAddress());
                        break;
                    }
                    if (partner != null) {
                        System.out.println("[보냄][" + partner.socket.getRemoteSocketAddress() + "] : " + msg);
                        partner.out.println("상대: " + msg);
                    } else {
                        out.println("아직 상대가 없습니다. 잠시만 기다려주세요.");
                        System.out.println("[핸들러] 상대 없음 상태: " + socket.getRemoteSocketAddress());
                    }
                }

            } catch (IOException e) {
                System.err.println("[핸들러] 오류 발생: " + socket.getRemoteSocketAddress() + " – " + e.getMessage());
                e.printStackTrace();
            } finally {
                closeConnection();
            }
        }

        private void matchPartner() {
            System.out.println("[매칭] 대기열 크기: " + waitingQueue.size());
            while (true) {
                if (waitingQueue.size() >= 2) {
                    ClientHandler first  = waitingQueue.poll();
                    ClientHandler second = waitingQueue.poll();
                    if (first != null && second != null && first != second) {
                        first.partner  = second;
                        second.partner = first;
                        first.out.println("매칭 완료! 상대와 대화를 시작하세요.");
                        second.out.println("매칭 완료! 상대와 대화를 시작하세요.");
                        System.out.println("[매칭] " + first.socket.getRemoteSocketAddress()
                                           + " ↔ " + second.socket.getRemoteSocketAddress());
                        return;
                    } else {
                        if (first  != null) waitingQueue.offer(first);
                        if (second != null) waitingQueue.offer(second);
                    }
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("[매칭] 매칭 대기 중 인터럽트됨");
                    return;
                }
            }
        }

        private void closeConnection() {
            try {
                System.out.println("[핸들러] 연결 종료: " + socket.getRemoteSocketAddress());
                socket.close();
            } catch (IOException e) {
                System.err.println("[핸들러] 소켓 닫기 실패: " + e.getMessage());
            }
        }
    }
}
