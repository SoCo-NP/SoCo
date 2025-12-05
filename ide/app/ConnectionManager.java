package ide.app;

import ide.net.CollabClient;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * 네트워크 연결 라이프사이클을 관리하는 클래스.
 * 
 * 서버 연결, 연결 해제, 연결 상태 확인 등의 책임을 담당한다.
 */
public class ConnectionManager {
    private final CollabClient client;
    private String currentNickname = "?";
    private boolean connected = false;

    /**
     * ConnectionManager 생성자.
     *
     * @param client CollabClient 인스턴스
     */
    public ConnectionManager(CollabClient client) {
        this.client = client;
    }

    /**
     * 서버 접속 다이얼로그를 표시한다.
     *
     * @param parent 부모 프레임
     */
    public void promptConnect(Frame parent) {
        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
        JTextField hostField = new JTextField("127.0.0.1");
        JTextField portField = new JTextField("6000");
        JTextField nickField = new JTextField("user" + System.currentTimeMillis() % 100);
        JComboBox<String> roleCombo = new JComboBox<>(new String[] { "Student", "Professor" });

        panel.add(new JLabel("Host:"));
        panel.add(hostField);
        panel.add(new JLabel("Port:"));
        panel.add(portField);
        panel.add(new JLabel("Nickname:"));
        panel.add(nickField);
        panel.add(new JLabel("Role:"));
        panel.add(roleCombo);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Connect to Server",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                String host = hostField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                String nickname = nickField.getText().trim();
                String role = (String) roleCombo.getSelectedItem();

                connect(host, port, nickname, role);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent,
                        "Connection failed: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 서버에 연결한다.
     *
     * @param host     서버 호스트
     * @param port     서버 포트
     * @param nickname 닉네임
     * @param roleStr  역할 문자열
     * @throws IOException 연결 실패 시
     */
    public void connect(String host, int port, String nickname, String roleStr) throws IOException {
        this.currentNickname = nickname;
        client.connect(host, port, nickname, roleStr);
        this.connected = true;
    }

    /**
     * 서버 연결을 해제한다.
     */
    public void disconnect() {
        if (connected) {
            client.disconnect();
            connected = false;
            currentNickname = "?";
        }
    }

    /**
     * 현재 연결 상태를 반환한다.
     *
     * @return 연결 여부
     */
    public boolean isConnected() {
        return connected && client.isConnected();
    }

    /**
     * 현재 닉네임을 반환한다.
     *
     * @return 닉네임
     */
    public String getNickname() {
        return currentNickname;
    }

    /**
     * CollabClient 인스턴스를 반환한다.
     *
     * @return CollabClient 인스턴스
     */
    public CollabClient getClient() {
        return client;
    }
}
