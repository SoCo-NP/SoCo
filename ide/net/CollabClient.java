package ide.net;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 클라이언트 측 네트워크 통신을 담당하는 클래스.
 *
 * 서버와의 소켓 연결을 관리하고, 메시지 송수신을 처리한다.
 * 수신된 메시지는 CollabCallbacks 인터페이스를 통해 애플리케이션 계층으로 전달된다.
 * 별도의 스레드에서 수신 루프(Read Loop)가 실행된다.
 */
public class CollabClient {
    private final CollabCallbacks ui;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private Thread readerThread;
    private volatile boolean connected = false;
    private String nickname = "?";

    /**
     * CollabClient 생성자.
     *
     * @param ui 네트워크 이벤트를 수신할 콜백 인터페이스
     */
    public CollabClient(CollabCallbacks ui) {
        this.ui = ui;
    }

    /**
     * 현재 서버와 연결되어 있는지 여부를 반환한다.
     *
     * @return 연결 상태 (true: 연결됨, false: 연결 끊김)
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 현재 클라이언트의 닉네임을 반환한다.
     *
     * @return 닉네임 문자열
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 서버에 연결을 시도하고 초기화 메시지(JOIN)를 전송한다.
     *
     * @param host 서버 호스트 주소
     * @param port 서버 포트 번호
     * @param nick 사용자 닉네임
     * @param role 사용자 역할
     * @throws IOException 연결 실패 시 발생
     */
    public void connect(String host, int port, String nick, String role) throws IOException {
        System.out.println("[CLIENT] Connecting to " + host + ":" + port);
        System.out.println("[CLIENT] Nickname: " + nick + ", Role: " + role);

        disconnect();
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        nickname = nick;

        // 서버와 연결
        connected = true;

        String joinMsg = Protocol.JOIN + Protocol.SEPARATOR + nick + Protocol.SEPARATOR + role;
        System.out.println("[CLIENT] Sending JOIN: " + joinMsg);
        sendLine(joinMsg);

        readerThread = new Thread(this::readLoop, "collab-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        System.out.println("[CLIENT] Connected successfully!");
    }

    /**
     * 서버와의 연결을 종료하고 리소스를 정리한다.
     */
    public void disconnect() {
        connected = false;
        if (readerThread != null) {
            try {
                readerThread.interrupt();
            } catch (Exception ignored) {
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        in = null;
        out = null;
    }

    /**
     * 서버로부터 메시지를 지속적으로 수신하는 루프.
     * 수신된 메시지를 파싱하여 적절한 콜백 메소드를 호출한다.
     */
    private void readLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                final String msg = line;

                if (msg.startsWith(Protocol.INFO + Protocol.SEPARATOR)) {
                    // 시스템 정보 메시지 처리 (현재는 무시)
                } else if (msg.startsWith(Protocol.EDIT + Protocol.SEPARATOR)) {
                    String[] p = msg.split(Protocol.DELIMITER, 3);
                    if (p.length == 3) {
                        String path = p[1];
                        String text = new String(Base64.getDecoder().decode(p[2]), StandardCharsets.UTF_8);
                        ui.applyRemoteEdit(path, text);
                    }
                } else if (msg.startsWith(Protocol.CURSOR + Protocol.SEPARATOR)) {
                    String[] p = msg.split(Protocol.DELIMITER, 5);
                    if (p.length == 5) {
                        ui.applyRemoteCursor(p[1], p[2], Protocol.safeInt(p[3]), Protocol.safeInt(p[4]));
                    }
                } else if (msg.startsWith(Protocol.VIEWPORT + Protocol.SEPARATOR)) {
                    String[] p = msg.split(Protocol.DELIMITER, 3);
                    if (p.length == 3) {
                        ui.applyRemoteViewport(p[1], Protocol.safeInt(p[2]));
                    }
                } else if (msg.startsWith(Protocol.LASER + Protocol.SEPARATOR)) {
                    String[] p = msg.split(Protocol.DELIMITER, 4);
                    if (p.length == 4) {
                        ui.applyRemoteLaser(p[1], Protocol.safeInt(p[2]), Protocol.safeInt(p[3]));
                    }
                } else if (msg.startsWith(Protocol.ROLE_INFO + Protocol.SEPARATOR)) {
                    System.out.println("[CLIENT] Received ROLE_INFO: " + msg);
                    String[] p = msg.split(Protocol.DELIMITER, 3);
                    if (p.length == 3) {
                        System.out.println("[CLIENT] Parsing ROLE_INFO: nick=" + p[1] + ", role=" + p[2]);
                        ui.onRoleInfo(p[1], p[2]);
                    } else {
                        System.out.println("[CLIENT] Invalid ROLE_INFO format: " + msg);
                    }
                } else if (msg.startsWith(Protocol.QUESTION + Protocol.SEPARATOR)) {
                    System.out.println("[CLIENT] Received QUESTION: " + msg);
                    String[] p = msg.split(Protocol.DELIMITER, 3);
                    if (p.length == 3) {
                        String studentNick = p[1];
                        String questionText = new String(
                                Base64.getDecoder().decode(p[2]), StandardCharsets.UTF_8);
                        System.out.println("[CLIENT] Question from " + studentNick + ": " + questionText);
                        ui.onQuestion(studentNick, questionText);
                    }
                } else if (msg.startsWith(Protocol.FILE_CREATE + Protocol.SEPARATOR) ||
                        msg.startsWith(Protocol.FILE_DELETE + Protocol.SEPARATOR) ||
                        msg.startsWith(Protocol.FILE_RENAME + Protocol.SEPARATOR)) {
                    System.out.println("[REMOTE FILE EVT] " + msg);
                }
            }
        } catch (IOException ignored) {
        } finally {
            connected = false;
        }
    }

    // === 전송 메소드 (Senders) ===

    /**
     * 텍스트 변경 사항(스냅샷)을 서버로 전송한다.
     *
     * @param vpath 가상 파일 경로
     * @param text  변경된 텍스트 내용
     */
    public void sendSnapshot(String vpath, String text) {
        if (!connected)
            return;
        String b64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        sendLine(Protocol.EDIT + Protocol.SEPARATOR + vpath + Protocol.SEPARATOR + b64);
    }

    /**
     * 커서 위치 정보를 서버로 전송한다.
     *
     * @param vpath 가상 파일 경로
     * @param dot   커서 위치
     * @param mark  선택 영역 시작점
     */
    public void sendCursor(String vpath, int dot, int mark) {
        if (!connected)
            return;
        sendLine(Protocol.CURSOR + Protocol.SEPARATOR + vpath + Protocol.SEPARATOR + nickname + Protocol.SEPARATOR + dot
                + Protocol.SEPARATOR + mark);
    }

    /**
     * 뷰포트(스크롤) 위치 정보를 서버로 전송한다.
     *
     * @param vpath 가상 파일 경로
     * @param line  현재 보고 있는 줄 번호
     */
    public void sendViewport(String vpath, int line) {
        if (!connected)
            return;
        sendLine(Protocol.VIEWPORT + Protocol.SEPARATOR + vpath + Protocol.SEPARATOR + line);
    }

    /**
     * 레이저 포인터 위치 정보를 서버로 전송한다.
     *
     * @param vpath 가상 파일 경로
     * @param x     X 좌표
     * @param y     Y 좌표
     */
    public void sendLaser(String vpath, int x, int y) {
        if (!connected)
            return;
        sendLine(Protocol.LASER + Protocol.SEPARATOR + vpath + Protocol.SEPARATOR + x + Protocol.SEPARATOR + y);
    }

    // === 파일 시스템 이벤트 전송 ===

    /**
     * 파일 생성 이벤트를 서버로 전송한다.
     *
     * @param fpath 파일 경로
     * @param isDir 디렉토리 여부
     */
    public void sendFileCreate(String fpath, boolean isDir) {
        if (connected)
            sendLine(Protocol.FILE_CREATE + Protocol.SEPARATOR + fpath + Protocol.SEPARATOR + isDir + Protocol.SEPARATOR
                    + nickname);
    }

    /**
     * 파일 삭제 이벤트를 서버로 전송한다.
     *
     * @param fpath 삭제된 파일 경로
     */
    public void sendFileDelete(String fpath) {
        if (connected)
            sendLine(Protocol.FILE_DELETE + Protocol.SEPARATOR + fpath + Protocol.SEPARATOR + nickname);
    }

    /**
     * 파일 이름 변경 이벤트를 서버로 전송한다.
     *
     * @param oldPath 변경 전 경로
     * @param newPath 변경 후 경로
     */
    public void sendFileRename(String oldPath, String newPath) {
        if (connected)
            sendLine(Protocol.FILE_RENAME + Protocol.SEPARATOR + oldPath + Protocol.SEPARATOR + newPath
                    + Protocol.SEPARATOR + nickname);
    }

    /**
     * 질문을 서버로 전송한다 (학생 전용).
     *
     * @param questionText 질문 내용
     */
    public void sendQuestion(String questionText) {
        if (!connected)
            return;
        String encoded = Base64.getEncoder().encodeToString(
                questionText.getBytes(StandardCharsets.UTF_8));
        sendLine(Protocol.QUESTION + Protocol.SEPARATOR + nickname +
                Protocol.SEPARATOR + encoded);
        System.out.println("[CLIENT] Sent question: " + questionText);
    }

    /**
     * 메시지 한 줄을 서버로 전송한다.
     *
     * @param s 전송할 메시지 문자열
     */
    private synchronized void sendLine(String s) {
        if (!connected)
            return;
        try {
            out.write(s);
            out.write('\n');
            out.flush();
        } catch (IOException ignored) {
        }
    }
}
