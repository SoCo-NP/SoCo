package ide.app;

import ide.net.CollabClient;
import ide.net.CollabCallbacks;
import ide.ui.EditorTab;
import ide.ui.FileTreeManager;
import ide.ui.TabManager;
import ide.ui.ToolBarManager;
import ide.ui.QuestionDialog;
import ide.domain.Role;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * 애플리케이션의 메인 컨트롤러 클래스.
 * 
 * 매니저 클래스들을 조율하고, CollabCallbacks/CollabActions 인터페이스를 구현하여
 * UI 계층과 네트워크 계층 간의 중재 역할을 수행한다.
 */
public class CollabIDE extends JFrame implements CollabCallbacks, CollabActions {

    // 매니저 클래스들
    private final ConnectionManager connectionMgr;
    private final UserSessionManager sessionMgr;
    private final UICoordinator uiCoordinator;

    // UI 매니저
    private final TabManager tabManager;
    private final FileTreeManager fileTreeManager;
    private final ToolBarManager toolBarManager;

    // UI 컴포넌트
    private final JTextArea console = new JTextArea();
    private final JLabel statusLabel = new JLabel("Offline");
    private final JPanel statusPanel = new JPanel(new BorderLayout());
    private QuestionDialog questionDialog;

    // 애플리케이션 상태
    private volatile boolean keystrokeMode = false;

    /**
     * CollabIDE 생성자.
     * 매니저 클래스들과 UI를 초기화한다.
     */
    public CollabIDE() {
        super("SoCo IDE");

        // 1. 네트워크 및 매니저 초기화
        CollabClient client = new CollabClient(this);
        this.connectionMgr = new ConnectionManager(client);
        this.sessionMgr = new UserSessionManager();

        // 2. UI 매니저 초기화
        this.tabManager = new TabManager(this, () -> keystrokeMode, this::onTabUpdated);
        this.fileTreeManager = new FileTreeManager(this, this, tabManager);
        this.toolBarManager = new ToolBarManager(this, this, tabManager, fileTreeManager,
                () -> connectionMgr.promptConnect(this));

        // 3. UI 조정자 초기화
        this.uiCoordinator = new UICoordinator(this, tabManager, toolBarManager, console, statusLabel);

        // 4. UI 구성
        setupUI();

        // 5. 윈도우 설정
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 780));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                dispose();
                System.exit(0);
            }
        });
    }

    /**
     * UI 레이아웃을 구성한다.
     */
    private void setupUI() {
        setJMenuBar(toolBarManager.getMenuBar());
        getContentPane().add(toolBarManager.getToolBar(), BorderLayout.NORTH);

        JScrollPane treeScroll = new JScrollPane(fileTreeManager.getComponent());
        treeScroll.setPreferredSize(new Dimension(280, 0));

        JSplitPane h = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                treeScroll,
                tabManager.getComponent());
        h.setResizeWeight(0.22);

        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane consoleScrollPane = new JScrollPane(console);
        consoleScrollPane.setPreferredSize(new Dimension(0, 180));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                h, consoleScrollPane);
        mainSplit.setResizeWeight(0.8);

        // 질문 다이얼로그 초기화
        questionDialog = new QuestionDialog(this);

        statusPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        getContentPane().add(mainSplit, BorderLayout.CENTER);
        getContentPane().add(statusPanel, BorderLayout.SOUTH);
    }

    /**
     * 탭 상태가 변경되었을 때 상태바를 업데이트한다.
     */
    private void onTabUpdated(EditorTab tab) {
        String path = tab.getVirtualPath();
        String dirty = tab.isDirty() ? "[수정됨]" : "";
        statusLabel.setText(String.format("%s  %s  Ln %d, Col %d  |  %s  %s",
                path, dirty, tab.getCaretLine(), tab.getCaretCol(),
                connectionMgr.isConnected() ? "Online" : "Offline",
                keystrokeMode ? "(실시간)" : "(200ms)"));
        tabManager.updateTabTitle(tab);
    }

    // --- CollabActions 구현 (UI -> Controller) ---

    public void connect(String host, int port, String nickname, String roleStr) {
        try {
            connectionMgr.connect(host, port, nickname, roleStr);
            uiCoordinator.log("서버 연결됨: " + host + ":" + port);
        } catch (Exception e) {
            uiCoordinator.showError("연결 실패: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        if (connectionMgr.isConnected()) {
            connectionMgr.disconnect();
            sessionMgr.clear();
            uiCoordinator.log("서버 연결 해제");
        }
    }

    @Override
    public boolean isConnected() {
        return connectionMgr.isConnected();
    }

    @Override
    public String getNickname() {
        return connectionMgr.getNickname();
    }

    @Override
    public void setFollowMe(boolean active) {
        keystrokeMode = active;
    }

    @Override
    public void setLaser(boolean active) {
        tabManager.setLaser(active);
    }

    @Override
    public void sendSnapshot(String vPath, String text) {
        connectionMgr.getClient().sendSnapshot(vPath, text);
    }

    @Override
    public void sendCursor(String vPath, int dot, int mark) {
        connectionMgr.getClient().sendCursor(vPath, dot, mark);
    }

    @Override
    public void sendViewport(String vPath, int line) {
        connectionMgr.getClient().sendViewport(vPath, line);
    }

    @Override
    public void sendLaser(String vPath, int x, int y) {
        connectionMgr.getClient().sendLaser(vPath, x, y);
    }

    @Override
    public void sendFileCreate(String path, boolean isDir) {
        connectionMgr.getClient().sendFileCreate(path, isDir);
    }

    @Override
    public void sendFileDelete(String path) {
        connectionMgr.getClient().sendFileDelete(path);
    }

    @Override
    public void sendFileRename(String oldPath, String newPath) {
        connectionMgr.getClient().sendFileRename(oldPath, newPath);
    }

    @Override
    public Set<String> getConnectedUsers() {
        return sessionMgr.getConnectedUsers();
    }

    @Override
    public Set<String> getConnectedStudents() {
        return sessionMgr.getConnectedStudents();
    }

    @Override
    public void sendQuestion(String questionText) {
        connectionMgr.getClient().sendQuestion(questionText);
    }

    @Override
    public void showQuestionDialog() {
        if (questionDialog != null) {
            questionDialog.setVisible(true);
        }
    }

    // --- CollabCallbacks 구현 (Network -> Controller) ---

    @Override
    public void applyRemoteEdit(String path, String text) {
        tabManager.applyRemoteEdit(path, text);
    }

    @Override
    public void applyRemoteCursor(String path, String nick, int dot, int mark) {
        Color color = sessionMgr.getColorForNick(nick);
        tabManager.applyRemoteCursor(path, nick, color, dot, mark);
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
            sessionMgr.addUser(nick, role);

            System.out.println("[CollabIDE] ROLE_INFO received: " + nick + " -> " + role);
            System.out.println("[CollabIDE] Current userRoles: " + sessionMgr.getConnectedUsers());

            if (Objects.equals(nick, connectionMgr.getNickname())) {
                uiCoordinator.updateThemeForRole(role);
                toolBarManager.updateRoleUI(role == Role.PROFESSOR);
            }

            uiCoordinator.log("[참여자 정보] " + nick + " = " + role);
        });
    }

    @Override
    public void onQuestion(String studentNick, String questionText) {
        System.out.println("[CollabIDE] Question from " + studentNick + ": " + questionText);
        if (questionDialog != null) {
            questionDialog.addQuestion(studentNick, questionText);
        }
    }

    // --- 메인 메소드 ---

    /**
     * 애플리케이션 진입점.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new CollabIDE().setVisible(true);
        });
    }
}
