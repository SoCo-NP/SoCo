package ide.net;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CollabClient {
    private final CollabCallbacks ui;
    private Socket socket;
    private BufferedReader in; private BufferedWriter out;
    private Thread readerThread;
    private volatile boolean connected = false;
    private String nickname = "?";

    public CollabClient(CollabCallbacks ui) { this.ui = ui; }

    public boolean isConnected() { return connected; }
    public String getNickname() { return nickname; }

    public void connect(String host, int port, String nick, String role) throws IOException {
        disconnect();
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream(),  StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        nickname = nick;
        sendLine("JOIN|" + nick + "|" + role);
        connected = true;
        readerThread = new Thread(this::readLoop, "collab-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void disconnect() {
        connected = false;
        if (readerThread != null) { try { readerThread.interrupt(); } catch (Exception ignored) {} }
        if (socket != null) { try { socket.close(); } catch (IOException ignored) {} }
        socket = null; in = null; out = null;
    }

    private void readLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                final String msg = line;
                if (msg.startsWith("INFO|")) {
                    // optional: ignore or print elsewhere
                } else if (msg.startsWith("EDIT|")) {
                    String[] p = msg.split("\\|", 3);
                    if (p.length == 3) {
                        String path = p[1];
                        String text = new String(Base64.getDecoder().decode(p[2]), StandardCharsets.UTF_8);
                        ui.applyRemoteEdit(path, text);
                    }
                } else if (msg.startsWith("CURSOR|")) {
                    String[] p = msg.split("\\|", 5);
                    if (p.length == 5) {
                        ui.applyRemoteCursor(p[1], p[2], safe(p[3]), safe(p[4]));
                    }
                } else if (msg.startsWith("ROLE_INFO|")) {
                    String[] p = msg.split("\\|", 3);
                    if (p.length == 3) {
                        ui.onRoleInfo(p[1], p[2]);
                    }
                } else if (msg.startsWith("COMPILE_GRANTED|")) {
                    String[] p = msg.split("\\|", 3); if (p.length == 3) ui.onCompileGranted(p[1], p[2]);
                } else if (msg.startsWith("COMPILE_DENIED|")) {
                    String[] p = msg.split("\\|", 3); if (p.length == 3) ui.onCompileDenied(p[1], p[2]);
                } else if (msg.startsWith("COMPILE_START|")) {
                    String[] p = msg.split("\\|", 3); if (p.length == 3) ui.onCompileStart(p[1], p[2]);
                } else if (msg.startsWith("COMPILE_OUT|")) {
                    String[] p = msg.split("\\|", 4);
                    if (p.length == 4) ui.onCompileOut(p[1], p[2],
                            new String(Base64.getDecoder().decode(p[3]), StandardCharsets.UTF_8));
                } else if (msg.startsWith("COMPILE_END|")) {
                    String[] p = msg.split("\\|", 4); if (p.length == 4) ui.onCompileEnd(p[1], p[2], safe(p[3]));
                } else if (msg.startsWith("COMPILE_RELEASE|")) {
                    String[] p = msg.split("\\|", 3); if (p.length == 3) ui.onCompileReleased(p[1], p[2]);
                } else if (msg.startsWith("FILE_CREATE|") || msg.startsWith("FILE_DELETE|") || msg.startsWith("FILE_RENAME|")) {
                    // 순수 브로드캐스트: UI가 콘솔에만 적절히 표시(실제 디스크 조작은 각자 로컬)
                    // 필요시 클라이언트가 트리 새로고침 여부를 판단할 수 있음
                    System.out.println("[REMOTE FILE EVT] " + msg);
                }
            }
        } catch (IOException ignored) {
        } finally {
            connected = false;
        }
    }

    // === senders ===
    public void sendSnapshot(String vpath, String text) {
        if (!connected) return;
        String b64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        sendLine("EDIT|" + vpath + "|" + b64);
    }

    public void sendCursor(String vpath, int dot, int mark) {
        if (!connected) return;
        sendLine("CURSOR|" + vpath + "|" + nickname + "|" + dot + "|" + mark);
    }

    // compile
    public void requestCompile(String fpath) { if (connected) sendLine("COMPILE_REQ|" + fpath + "|" + nickname); }
    public void releaseCompile(String fpath) { if (connected) sendLine("COMPILE_RELEASE|" + fpath + "|" + nickname); }
    public void sendCompileStart(String fpath) { if (connected) sendLine("COMPILE_START|" + fpath + "|" + nickname); }
    public void sendCompileOut(String fpath, String line) {
        if (connected) {
            String b64 = Base64.getEncoder().encodeToString(line.getBytes(StandardCharsets.UTF_8));
            sendLine("COMPILE_OUT|" + fpath + "|" + nickname + "|" + b64);
        }
    }
    public void sendCompileEnd(String fpath, int exit) { if (connected) sendLine("COMPILE_END|" + fpath + "|" + nickname + "|" + exit); }

    // file ops broadcast (for logs)
    public void sendFileCreate(String fpath, boolean isDir) { if (connected) sendLine("FILE_CREATE|" + fpath + "|" + isDir + "|" + nickname); }
    public void sendFileDelete(String fpath) { if (connected) sendLine("FILE_DELETE|" + fpath + "|" + nickname); }
    public void sendFileRename(String oldPath, String newPath) { if (connected) sendLine("FILE_RENAME|" + oldPath + "|" + newPath + "|" + nickname); }

    private synchronized void sendLine(String s) {
        if (!connected) return;
        try { out.write(s); out.write('\n'); out.flush(); } catch (IOException ignored) {}
    }

    private static int safe(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
}
