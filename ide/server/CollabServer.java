package ide.server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import ide.net.Protocol;

public class CollabServer {
    private final int port;
    private final Set<Client> clients = Collections.synchronizedSet(new HashSet<>());
    // 파일별 컴파일 락: absolutePath -> holder nickname
    private final Map<String, String> compileLocks = Collections.synchronizedMap(new HashMap<>());

    public CollabServer(int port) {
        this.port = port;
    }

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
            for (Client c : clients)
                if (c != except)
                    c.send(line);
        }
    }

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
            try (
                    InputStream is = socket.getInputStream();
                    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    OutputStream os = socket.getOutputStream();
                    OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                    BufferedWriter bw = new BufferedWriter(osw)) {
                in = br;
                out = bw;
                in = br;
                out = bw;
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith(Protocol.JOIN + Protocol.SEPARATOR)) {
                        String[] parts = line.split(Protocol.DELIMITER, 3);
                        if (parts.length >= 2) {
                            nick = parts[1];
                            if (parts.length == 3) {
                                role = parts[2];
                            }
                            send(Protocol.INFO + Protocol.SEPARATOR + "Welcome " + nick);
                            System.out.println("[SERVER] Client connected: " + nick + " (" + role + ") from "
                                    + socket.getRemoteSocketAddress());

                            // Announce the new user's role to everyone
                            broadcast(Protocol.ROLE_INFO + Protocol.SEPARATOR + nick + Protocol.SEPARATOR + role, null);

                            // Send existing user roles to the new user
                            synchronized (clients) {
                                for (Client c : clients) {
                                    if (c != this) {
                                        send(Protocol.ROLE_INFO + Protocol.SEPARATOR + c.nick + Protocol.SEPARATOR
                                                + c.role);
                                    }
                                }
                            }
                        }
                    } else if (line.startsWith(Protocol.EDIT + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.CURSOR + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.COMPILE_START + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.COMPILE_OUT + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.COMPILE_END + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.FILE_CREATE + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.FILE_DELETE + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.FILE_RENAME + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.VIEWPORT + Protocol.SEPARATOR)
                            || line.startsWith(Protocol.LASER + Protocol.SEPARATOR)) {
                        broadcast(line, this);
                    } else if (line.startsWith(Protocol.COMPILE_REQ + Protocol.SEPARATOR)) {
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
                    } else if (line.startsWith(Protocol.COMPILE_RELEASE + Protocol.SEPARATOR)) {
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
                }
            } catch (IOException ignored) {
            } finally {
                // disconnect → 이 닉이 가진 락 해제
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
                System.out
                        .println("[SERVER] Client disconnected: " + nick + " from " + socket.getRemoteSocketAddress());
                try {
                    socket.close();
                } catch (IOException ignored2) {
                }
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

    // entry
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("사용법: java ide.server.CollabServer <port>\n예:     java ide.server.CollabServer 6000");
            return;
        }
        new CollabServer(Integer.parseInt(args[0])).start();
    }
}
