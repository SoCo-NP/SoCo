package ide.server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CollabServer {
    private final int port;
    private final Set<Client> clients = Collections.synchronizedSet(new HashSet<>());
    // 파일별 컴파일 락: absolutePath -> holder nickname
    private final Map<String, String> compileLocks = Collections.synchronizedMap(new HashMap<>());

    public CollabServer(int port) { this.port = port; }

    public void start() throws IOException {
        System.out.println("[SERVER] Starting on port " + port);
        try (ServerSocket ss = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
            while (true) {
                Socket s = ss.accept();
                Client c = new Client(s);
                clients.add(c);
                c.start();
            }
        }
    }

    private void broadcast(String line, Client except) {
        synchronized (clients) {
            for (Client c : clients) if (c != except) c.send(line);
        }
    }

    private class Client extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private BufferedWriter out;
        private String nick = "?";
        private String role = "Student";

        Client(Socket socket) { this.socket = socket; }

        @Override public void run() {
            try (
                    InputStream is = socket.getInputStream();
                    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    OutputStream os = socket.getOutputStream();
                    OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                    BufferedWriter bw = new BufferedWriter(osw)
            ) {
                in = br; out = bw;
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("JOIN|")) {
                        String[] parts = line.split("\\|", 3);
                        if (parts.length >= 2) {
                            nick = parts[1];
                            if (parts.length == 3) {
                                role = parts[2];
                            }
                            send("INFO|Welcome " + nick);
                            System.out.println("[SERVER] Client connected: " + nick + " (" + role + ") from " + socket.getRemoteSocketAddress());

                            // Announce the new user's role to everyone
                            broadcast("ROLE_INFO|" + nick + "|" + role, null);

                            // Send existing user roles to the new user
                            synchronized (clients) {
                                for (Client c : clients) {
                                    if (c != this) {
                                        send("ROLE_INFO|" + c.nick + "|" + c.role);
                                    }
                                }
                            }
                        }
                    } else if (line.startsWith("EDIT|")
                            || line.startsWith("CURSOR|")
                            || line.startsWith("COMPILE_START|")
                            || line.startsWith("COMPILE_OUT|")
                            || line.startsWith("COMPILE_END|")
                            || line.startsWith("FILE_CREATE|")
                            || line.startsWith("FILE_DELETE|")
                            || line.startsWith("FILE_RENAME|")) {
                        broadcast(line, this);
                    } else if (line.startsWith("COMPILE_REQ|")) {
                        String[] p = line.split("\\|", 3);
                        if (p.length == 3) {
                            String fpath = p[1]; String reqNick = p[2];
                            String holder = compileLocks.get(fpath);
                            if (holder == null) {
                                compileLocks.put(fpath, reqNick);
                                send("COMPILE_GRANTED|" + fpath + "|" + reqNick);
                                broadcast("COMPILE_GRANTED|" + fpath + "|" + reqNick, this);
                            } else {
                                send("COMPILE_DENIED|" + fpath + "|" + holder);
                            }
                        }
                    } else if (line.startsWith("COMPILE_RELEASE|")) {
                        String[] p = line.split("\\|", 3);
                        if (p.length == 3) {
                            String fpath = p[1]; String reqNick = p[2];
                            String holder = compileLocks.get(fpath);
                            if (holder != null && holder.equals(reqNick)) {
                                compileLocks.remove(fpath);
                                broadcast("COMPILE_RELEASE|" + fpath + "|" + reqNick, this);
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            } finally {
                // disconnect → 이 닉이 가진 락 해제
                List<String> toRelease = new ArrayList<>();
                synchronized (compileLocks) {
                    for (Map.Entry<String,String> e : compileLocks.entrySet()) {
                        if (Objects.equals(e.getValue(), nick)) toRelease.add(e.getKey());
                    }
                    for (String f : toRelease) compileLocks.remove(f);
                }
                for (String f : toRelease) broadcast("COMPILE_RELEASE|" + f + "|" + nick, this);

                clients.remove(this);
                System.out.println("[SERVER] Client disconnected: " + nick + " from " + socket.getRemoteSocketAddress());
                try { socket.close(); } catch (IOException ignored2) {}
            }
        }

        void send(String s) {
            try { out.write(s); out.write('\n'); out.flush(); } catch (IOException ignored) {}
        }
    }

    // entry
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("사용법: java ide.server.CollabServer <port>\n예:     java ide.server.CollabServer 6000");
            return;
        }
        new CollabServer(Integer.parseInt(args[0])).start();
    }
}
