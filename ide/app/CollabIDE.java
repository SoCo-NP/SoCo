package ide.app;

import ide.net.CollabClient;
import ide.net.CollabCallbacks;
import ide.ui.EditorTab;
import ide.ui.FileTreeManager;
import ide.ui.TabManager;
import ide.ui.ToolBarManager;
import ide.ui.Theme;
import ide.domain.Role;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * 애플리케이션의 메인 컨트롤러 클래스.
 * UI 계층, 네트워크 계층, 도메인 계층 간의 상호작용을 조정한다.
 */
public class CollabIDE extends JFrame implements CollabCallbacks, CollabActions {

    // UI 매니저 (View)
    private final TabManager tabManager;
    private final FileTreeManager fileTreeManager;
    private final ToolBarManager toolBarManager;

    // 네트워크 클라이언트 (Infrastructure)
    private final CollabClient collab;

    // 애플리케이션 상태
    private volatile boolean keystrokeMode = false;
    private final Map<String, Color> userColors = new HashMap<>();
    private final Map<String, Role> userRoles = new HashMap<>();

    // Swing 컴포넌트
    private final JTextArea console = new JTextArea();
    private final JLabel statusLabel = new JLabel("Offline");
    private final JPanel statusPanel = new JPanel(new BorderLayout());

    /**
     * CollabIDE 생성자.
     * 네트워크, UI 매니저, 화면 레이아웃을 초기화한다.
     */
    public CollabIDE() {
        super("SoCo IDE");

        // 1. 네트워크 초기화
        collab = new CollabClient(this);

        // 2. UI 매니저 초기화
        tabManager = new TabManager(this, () -> keystrokeMode, this::onTabUpdated);
        fileTreeManager = new FileTreeManager(this, this, tabManager);
        toolBarManager = new ToolBarManager(this, this, tabManager, fileTreeManager, this::promptConnect);

        // 3. UI 구성
        setupUI();

        // 4. 윈도우 설정
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 780));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                dispose();
            }
        });
    }

    private void setupUI() {
        setJMenuBar(toolBarManager.getMenuBar());
        add(toolBarManager.getToolBar(), BorderLayout.NORTH);

        JSplitPane h = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileTreeManager.getComponent(),
                tabManager.getComponent());
        h.setResizeWeight(0.22);

        JScrollPane consoleScroll = new JScrollPane(console);
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        consoleScroll.setPreferredSize(new Dimension(0, 180));

        JSplitPane v = new JSplitPane(JSplitPane.VERTICAL_SPLIT, h, consoleScroll);
        v.setResizeWeight(0.8);

        statusPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        getContentPane().add(v, BorderLayout.CENTER);
        getContentPane().add(statusPanel, BorderLayout.SOUTH);
    }

    // --- 컨트롤러 로직 ---

    /**
     * 탭의 상태가 변경되었을 때 상태바를 업데이트한다.
     */
    private void onTabUpdated(EditorTab tab) {
        String path = tab.getVirtualPath();
        String dirty = tab.isDirty() ? "[수정됨]" : "";
        statusLabel.setText(String.format("%s  %s  Ln %d, Col %d  |  %s  %s",
                path, dirty, tab.getCaretLine(), tab.getCaretCol(),
                collab.isConnected() ? "Online" : "Offline",
                keystrokeMode ? "(실시간)" : "(200ms)"));
        tabManager.updateTabTitle(tab);
    }

    /**
     * 서버 접속 다이얼로그를 표시한다.
     */
    private void promptConnect() {
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        JTextField host = new JTextField("127.0.0.1");
        JTextField port = new JTextField("6000");
        JTextField nick = new JTextField("user" + (int) (Math.random() * 100));
        JComboBox<String> role = new JComboBox<>(new String[] { "Student", "Professor" });
        p.add(new JLabel("Host:"));
        p.add(host);
        p.add(new JLabel("Port:"));
        p.add(port);
        p.add(new JLabel("Nickname:"));
        p.add(nick);
        p.add(new JLabel("Role:"));
        p.add(role);

        int r = JOptionPane.showConfirmDialog(this, p, "서버 연결", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            try {
                String selectedRole = (String) role.getSelectedItem();
                String nickname = nick.getText().trim();

                connect(host.getText().trim(), Integer.parseInt(port.getText().trim()), nickname, selectedRole);

            } catch (Exception ex) {
                showError("연결 실패: " + ex.getMessage());
            }
        }
    }

    // --- CollabActions 구현 (UI -> Controller) ---

    @Override
    public void connect(String host, int port, String nickname, String roleStr) {
        try {
            collab.connect(host, port, nickname, roleStr);
            Role role = Role.fromString(roleStr);
            userRoles.put(nickname, role);

            statusLabel.setText("연결됨");
            log("서버 연결 성공 (" + host + ":" + port + ") - 역할: " + role);
            updateThemeForRole(role);
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        collab.disconnect();
        statusLabel.setText("오프라인");
        log("연결이 종료되었습니다.");
    }

    @Override
    public boolean isConnected() {
        return collab.isConnected();
    }

    @Override
    public String getNickname() {
        return collab.getNickname();
    }

    @Override
    public void setFollowMe(boolean active) {
        toolBarManager.getBtnFollowMe().setSelected(active);
    }

    @Override
    public void setLaser(boolean active) {
        toolBarManager.getBtnLaser().setSelected(active);
    }

    @Override
    public void sendSnapshot(String vPath, String text) {
        collab.sendSnapshot(vPath, text);
    }

    @Override
    public void sendCursor(String vPath, int dot, int mark) {
        collab.sendCursor(vPath, dot, mark);
    }

    @Override
    public void sendViewport(String vPath, int line) {
        collab.sendViewport(vPath, line);
    }

    @Override
    public void sendLaser(String vPath, int x, int y) {
        collab.sendLaser(vPath, x, y);
    }

    @Override
    public void sendFileCreate(String path, boolean isDir) {
        collab.sendFileCreate(path, isDir);
    }

    @Override
    public void sendFileDelete(String path) {
        collab.sendFileDelete(path);
    }

    @Override
    public void sendFileRename(String oldPath, String newPath) {
        collab.sendFileRename(oldPath, newPath);
    }

    @Override
    public java.util.Set<String> getConnectedUsers() {
        return new java.util.HashSet<>(userRoles.keySet());
    }

    @Override
    public java.util.Set<String> getConnectedStudents() {
        java.util.Set<String> students = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, ide.domain.Role> entry : userRoles.entrySet()) {
            if (entry.getValue() == ide.domain.Role.STUDENT) {
                students.add(entry.getKey());
            }
        }
        return students;
    }

    // --- CollabCallbacks 구현 (Network -> Controller) ---

    @Override
    public void applyRemoteEdit(String path, String text) {
        tabManager.applyRemoteEdit(path, text);
        log("[원격 편집] " + path);
    }

    @Override
    public void applyRemoteCursor(String path, String nick, int dot, int mark) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = tabManager.findTabByPath(path);
            if (tab != null) {
                tab.updateRemoteCursor(nick, dot, mark, colorForNick(nick));
            }
        });
    }

    @Override
    public void applyRemoteViewport(String path, int line) {
        tabManager.applyRemoteViewport(path, line);
    }

    @Override
    public void applyRemoteLaser(String path, int x, int y) {
        tabManager.applyRemoteLaser(path, x, y);
    }

    @Override
    public void onRoleInfo(String nick, String roleString) {
        SwingUtilities.invokeLater(() -> {
            Role role = Role.fromString(roleString);
            userRoles.put(nick, role);

            System.out.println("[CollabIDE] ROLE_INFO received: " + nick + " -> " + role);
            System.out.println("[CollabIDE] Current userRoles: " + userRoles);

            if (Objects.equals(nick, collab.getNickname())) {
                updateThemeForRole(role);
            }
            log("[참여자 정보] " + nick + " = " + role);
        });
    }

    // --- 편의 메소드 ---

    private void log(String msg) {
        console.append(msg + "\n");
        console.setCaretPosition(console.getDocument().getLength());
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "오류", JOptionPane.ERROR_MESSAGE);
        log("오류: " + msg);
    }

    private Color colorForNick(String nick) {
        Role role = userRoles.get(nick);
        if (role == Role.PROFESSOR)
            return new Color(255, 105, 180, 110);
        if (role == Role.STUDENT)
            return new Color(0, 255, 0, 110);
        return userColors.computeIfAbsent(nick, n -> {
            Color[] p = { new Color(66, 133, 244), new Color(52, 168, 83), new Color(234, 67, 53),
                    new Color(251, 188, 5) };
            Color c = p[Math.abs(n.hashCode()) % p.length];
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 110);
        });
    }

    private void updateThemeForRole(Role role) {
        Color themeColor;
        String titleSuffix;

        if (role == Role.PROFESSOR) {
            themeColor = new Color(255, 182, 193);
            titleSuffix = " [PROFESSOR]";
        } else if (role == Role.STUDENT) {
            themeColor = new Color(144, 238, 144);
            titleSuffix = " [STUDENT]";
        } else {
            themeColor = Theme.STATUS_BAR_BG;
            titleSuffix = "";
        }

        toolBarManager.getBtnFollowMe().setVisible(role == Role.PROFESSOR);
        toolBarManager.getBtnLaser().setVisible(role == Role.PROFESSOR);
        toolBarManager.getBtnAttendance().setVisible(role == Role.PROFESSOR);

        setTitle("SoCo IDE - " + (collab.getNickname() != null ? collab.getNickname() : "Guest") + titleSuffix);
        statusPanel.setBackground(themeColor);
        if (getRootPane() != null)
            getRootPane().setBorder(BorderFactory.createLineBorder(themeColor, 3));
        revalidate();
        repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            Theme.apply();
            new CollabIDE().setVisible(true);
        });
    }
}
