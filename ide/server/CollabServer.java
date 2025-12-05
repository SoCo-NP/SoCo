package ide.server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import ide.net.Protocol;

/**
 * 교수자 - 학생자 IDE의 서버 사이드 로직을 담당하는 메인 클래스.
 *
 * 다수의 클라이언트 연결을 관리하고, 메시지 브로드캐스팅, 역할 관리,
 * 컴파일 락(Lock) 관리 등의 핵심 기능을 수행한다.
 * 각 클라이언트는 별도의 스레드(Client 클래스)에서 처리된다.
 */
public class CollabServer {
    private final int port;
    private final Set<Client> clients = Collections.synchronizedSet(new HashSet<>());

    // 파일별 컴파일 락: absolutePath -> holder nickname
    private final Map<String, String> compileLocks = Collections.synchronizedMap(new HashMap<>());

    /**
     * CollabServer 생성자.
     *
     * @param port 서버가 리스닝할 포트 번호
     */
    public CollabServer(int port) {
        this.port = port;
    }

    /**
     * 서버를 시작하고 클라이언트 연결을 대기한다.
     * 연결된 클라이언트는 별도의 스레드로 생성되어 관리된다.
     *
     * @throws IOException 소켓 생성 또는 연결 수락 실패 시 발생
     */
    public void start() throws IOException {
        System.out.println("[SERVER] Starting on port " + port);
        try (ServerSocket ss = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
            while (true) {
                Socket s = ss.accept();
                System.out.println("[SERVER] Accepted connection from: " + s.getRemoteSocketAddress());
                Client c = new Client(s);
                clients.add(c);
                c.start();
                System.out.println("[SERVER] Client thread started");
            }
        }
    }

    /**
     * 특정 클라이언트를 제외한 모든 클라이언트에게 메시지를 전송한다.
     *
     * @param line   전송할 메시지 문자열
     * @param except 전송에서 제외할 클라이언트 (본인에게 다시 보내지 않기 위함)
     */
    private void broadcast(String line, Client except) {
        synchronized (clients) {
            for (Client c : clients)
                if (c != except)
                    c.send(line);
        }
    }

    /**
     * 개별 클라이언트와의 통신을 담당하는 내부 스레드 클래스.
     */
    private class Client extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private BufferedWriter out;
        private String nick = "?";
        private String role = "Student";

        Client(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            System.out.println("[SERVER Client] Thread started for " + socket.getRemoteSocketAddress());
            try (
                    InputStream is = socket.getInputStream();
                    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    OutputStream os = socket.getOutputStream();
                    OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                    BufferedWriter bw = new BufferedWriter(osw)) {
                in = br;
                out = bw;

                System.out.println("[SERVER Client] Waiting for messages...");
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("[SERVER Client] Received: " + line);
                    if (line.startsWith(Protocol.JOIN + Protocol.SEPARATOR)) {
                        System.out.println("[SERVER Client] Detected JOIN message");
                        handleJoin(line);
                    } else if (isBroadcastMessage(line)) {
                        broadcast(line, this);
                    } else if (line.startsWith(Protocol.COMPILE_REQ + Protocol.SEPARATOR)) {
                        handleCompileReq(line);
                    } else if (line.startsWith(Protocol.COMPILE_RELEASE + Protocol.SEPARATOR)) {
                        handleCompileRelease(line);
                    } else if (line.startsWith(Protocol.QUESTION + Protocol.SEPARATOR)) {
                        System.out.println("[SERVER Client] Received QUESTION from " + nick);
                        handleQuestion(line);
                    }
                }
                System.out.println("[SERVER Client] readLine() returned null, connection closed");
            } catch (IOException e) {
                System.out.println("[SERVER Client] IOException: " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("[SERVER Client] Cleanup for " + nick);
                cleanup();
            }
        }

        /**
         * 입장 메시지(JOIN)를 처리한다.
         * 닉네임과 역할을 설정하고, 다른 클라이언트들에게 알린다.
         */
        private void handleJoin(String line) {
            String[] parts = line.split(Protocol.DELIMITER, 3);
            if (parts.length >= 2) {
                nick = parts[1];
                if (parts.length == 3) {
                    role = parts[2];
                }
                send(Protocol.INFO + Protocol.SEPARATOR + "Welcome " + nick);
                System.out.println("[SERVER] Client connected: " + nick + " (" + role + ") from "
                        + socket.getRemoteSocketAddress());

                // 새 접속자의 정보를 모두에게 알림
                String newUserRoleInfo = Protocol.ROLE_INFO + Protocol.SEPARATOR + nick + Protocol.SEPARATOR + role;
                System.out.println("[SERVER] Broadcasting new user ROLE_INFO: " + newUserRoleInfo);
                broadcast(newUserRoleInfo, null);

                // 기존 접속자들의 정보를 새 접속자에게 전송
                synchronized (clients) {
                    for (Client c : clients) {
                        if (c != this) {
                            String existingRoleInfo = Protocol.ROLE_INFO + Protocol.SEPARATOR + c.nick
                                    + Protocol.SEPARATOR + c.role;
                            System.out.println("[SERVER] Sending existing user to " + nick + ": " + existingRoleInfo);
                            send(existingRoleInfo);
                        }
                    }
                }
            }
        }

        /**
         * 단순 브로드캐스트가 필요한 메시지인지 확인한다.
         * (편집, 커서, 파일 시스템 이벤트 등)
         */
        private boolean isBroadcastMessage(String line) {
            return line.startsWith(Protocol.EDIT + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.CURSOR + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.COMPILE_START + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.COMPILE_OUT + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.COMPILE_END + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.FILE_CREATE + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.FILE_DELETE + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.FILE_RENAME + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.VIEWPORT + Protocol.SEPARATOR)
                    || line.startsWith(Protocol.LASER + Protocol.SEPARATOR);
        }

        /**
         * 컴파일 권한 요청(COMPILE_REQ)을 처리한다.
         * 이미 락이 걸려있으면 거부하고, 없으면 승인한다.
         */
        private void handleCompileReq(String line) {
            String[] p = line.split(Protocol.DELIMITER, 3);
            if (p.length == 3) {
                String fpath = p[1];
                String reqNick = p[2];
                String holder = compileLocks.get(fpath);
                if (holder == null) {
                    compileLocks.put(fpath, reqNick);
                    send(Protocol.COMPILE_GRANTED + Protocol.SEPARATOR + fpath + Protocol.SEPARATOR
                            + reqNick);
                    broadcast(Protocol.COMPILE_GRANTED + Protocol.SEPARATOR + fpath + Protocol.SEPARATOR
                            + reqNick, this);
                } else {
                    send(Protocol.COMPILE_DENIED + Protocol.SEPARATOR + fpath + Protocol.SEPARATOR
                            + holder);
                }
            }
        }

        /**
         * 컴파일 권한 반납(COMPILE_RELEASE)을 처리한다.
         * 요청자가 현재 락 소유자인 경우에만 해제한다.
         */
        private void handleCompileRelease(String line) {
            String[] p = line.split(Protocol.DELIMITER, 3);
            if (p.length == 3) {
                String fpath = p[1];
                String reqNick = p[2];
                String holder = compileLocks.get(fpath);
                if (holder != null && holder.equals(reqNick)) {
                    compileLocks.remove(fpath);
                    broadcast(Protocol.COMPILE_RELEASE + Protocol.SEPARATOR + fpath + Protocol.SEPARATOR
                            + reqNick, this);
                }
            }
        }

        /**
         * 학생의 질문을 교수자에게 전달한다.
         */
        private void handleQuestion(String line) {
            System.out.println("[SERVER] Routing question to professors: " + line);
            synchronized (clients) {
                for (Client c : clients) {
                    // Professor role인 클라이언트에게만 전송
                    if ("Professor".equals(c.role)) {
                        c.send(line);
                        System.out.println("[SERVER] Question sent to: " + c.nick);
                    }
                }
            }
        }

        /**
         * 클라이언트 연결 종료 시 리소스를 정리한다.
         * 획득했던 컴파일 락을 모두 해제하고 목록에서 제거한다.
         */
        private void cleanup() {
            List<String> toRelease = new ArrayList<>();
            synchronized (compileLocks) {
                for (Map.Entry<String, String> e : compileLocks.entrySet()) {
                    if (Objects.equals(e.getValue(), nick))
                        toRelease.add(e.getKey());
                }
                for (String f : toRelease)
                    compileLocks.remove(f);
            }
            for (String f : toRelease)
                broadcast(Protocol.COMPILE_RELEASE + Protocol.SEPARATOR + f + Protocol.SEPARATOR + nick, this);

            clients.remove(this);
            System.out.println("[SERVER] Client disconnected: " + nick + " from " + socket.getRemoteSocketAddress());
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        void send(String s) {
            try {
                out.write(s);
                out.write('\n');
                out.flush();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 서버 프로그램 진입점.
     * 포트 번호를 인자로 받아 서버를 시작한다.
     *
     * @param args 커맨드 라인 인자 (포트 번호)
     * @throws Exception 실행 중 예외 발생 시
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("사용법: java ide.server.CollabServer <port>\n예:     java ide.server.CollabServer 6000");
            return;
        }
        new CollabServer(Integer.parseInt(args[0])).start();
    }
}
