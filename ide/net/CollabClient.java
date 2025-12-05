package ide.net;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CollabClient {
    private final CollabCallbacks ui;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private Thread readerThread;
    private volatile boolean connected = false;
    private String nickname = "?";

    public CollabClient(CollabCallbacks ui) {
        this.ui = ui;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getNickname() {
        return nickname;
    }

    public void connect(String host, int port, String nick, String role) throws IOException {
        disconnect();
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        nickname = nick;
        sendLine(Protocol.JOIN + Protocol.SEPARATOR + nick + Protocol.SEPARATOR + role);
        connected = true;
        readerThread = new Thread(this::readLoop, "collab-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

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

    private void readLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                final String msg = line;
                // Simple parsing using Protocol helper would be cleaner, but we might check
                // startsWith first for efficiency
                // However, splitting first is also fine for this scale.

                if (msg.startsWith(Protocol.INFO + Protocol.SEPARATOR)) {
                    // optional: ignore or print elsewhere
                } else if (msg.startsWith(Protocol.EDIT + Protocol.SEPARATOR)) {
                    // Re-using manual split for safety on base64 content which might
                    // have pipes if not careful (though base64 doesn't)
                    // But wait, Base64 doesn't use '|'.
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
                    String[] p = msg.split(Protocol.DELIMITER, 3);
                    if (p.length == 3) {
                        ui.onRoleInfo(p[1], p[2]);
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

    // === senders ===
    public void sendSnapshot(String vpath, String text) {
        if (!connected)
            return;
        String b64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        sendLine(Protocol.EDIT + Protocol.SEPARATOR + vpath + Protocol.SEPARATOR + b64);
    }

    public void sendCursor(String vpath, int dot, int mark) {
        if (!connected)
            return;
        sendLine(Protocol.CURSOR + Protocol.SEPARATOR + vpath + Protocol.SEPARATOR + nickname + Protocol.SEPARATOR + dot
                + Protocol.SEPARATOR + mark);
    }

    public void sendViewport(String vpath, int line) {
        if (!connected)
            return;
        sendLine(Protocol.VIEWPORT + Protocol.SEPARATOR + vpath + Protocol.SEPARATOR + line);
    }

    public void sendLaser(String vpath, int x, int y) {
        if (!connected)
            return;
        sendLine(Protocol.LASER + Protocol.SEPARATOR + vpath + Protocol.SEPARATOR + x + Protocol.SEPARATOR + y);
    }

    // compile

    // file ops broadcast (for logs)
    public void sendFileCreate(String fpath, boolean isDir) {
        if (connected)
            sendLine(Protocol.FILE_CREATE + Protocol.SEPARATOR + fpath + Protocol.SEPARATOR + isDir + Protocol.SEPARATOR
                    + nickname);
    }

    public void sendFileDelete(String fpath) {
        if (connected)
            sendLine(Protocol.FILE_DELETE + Protocol.SEPARATOR + fpath + Protocol.SEPARATOR + nickname);
    }

    public void sendFileRename(String oldPath, String newPath) {
        if (connected)
            sendLine(Protocol.FILE_RENAME + Protocol.SEPARATOR + oldPath + Protocol.SEPARATOR + newPath
                    + Protocol.SEPARATOR + nickname);
    }

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
