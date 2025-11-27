package ide.client;

import ide.net.CollabClient;
import ide.net.CollabCallbacks;
import ide.ui.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.*;

public class CollabIDE extends JFrame implements CollabCallbacks {
    // Managers
    private final FileTreeManager fileTreeManager;
    private final TabManager tabManager;

    // UI
    private final JTextArea console = new JTextArea();
    private final JLabel statusLabel = new JLabel("Offline");
    private final JPanel statusPanel = new JPanel(new BorderLayout());

    // State
    private final CollabClient collab = new CollabClient(this);
    private volatile boolean keystrokeMode = false;
    private final Map<String, Color> userColors = new HashMap<>();
    private final Map<String, String> userRoles = new HashMap<>();
    private final Set<String> connectedUsers = new HashSet<>();

    // Modes
    private JToggleButton btnFollowMe;
    private boolean followMeActive = false;
    private final javax.swing.Timer viewportDebounce;
    private JToggleButton btnLaser;
    private boolean laserActive = false;

    // Attendance
    private JToggleButton btnAttendance;
    private AttendanceDialog attendanceDialog;
    private static final String[] EXPECTED_STUDENTS = {"유상완", "송승윤", "신성", "한기준", "김남윤"};

    public CollabIDE() {
        super("Mini IDE - IntelliJ style");

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 780));
        setLocationRelativeTo(null);

        MenuBarManager menuBarManager = new MenuBarManager();
        setJMenuBar(menuBarManager.createMenuBar(this));

        ToolBarManager toolBarManager = new ToolBarManager();
        add(toolBarManager.createToolBar(this), BorderLayout.NORTH);
        this.btnFollowMe = toolBarManager.getBtnFollowMe();
        this.btnLaser = toolBarManager.getBtnLaser();
        this.btnAttendance = toolBarManager.getBtnAttendance();

        fileTreeManager = new FileTreeManager(this);
        JComponent fileTreePanel = fileTreeManager.createFileTreePanel();

        tabManager = new TabManager(this);
        JComponent editorPanel = createEditorPanel();

        JSplitPane h = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileTreePanel, editorPanel);
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

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (tabManager.confirmCloseAllTabs()) {
                    collab.disconnect();
                    dispose();
                }
            }
        });

        tabManager.getEditorTabs().addChangeListener(e -> {
            getActiveEditor().ifPresent(this::sendSnapshotNow);
            if (followMeActive && collab.isConnected()) {
                sendViewportNow();
            }
        });

        viewportDebounce = new javax.swing.Timer(100, e -> {
            if (followMeActive && collab.isConnected()) sendViewportNow();
        });
        viewportDebounce.setRepeats(false);
    }

    private JComponent createEditorPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.add(tabManager.getEditorTabs(), BorderLayout.CENTER);
        return center;
    }

    public void chooseAndOpenProjectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Open Project Folder");
        if (fileTreeManager.getProjectRoot() != null) chooser.setCurrentDirectory(fileTreeManager.getProjectRoot());
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            fileTreeManager.setProjectRoot(chooser.getSelectedFile());
            log("Opened project: " + chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    // ===== Actions =====
    public void actionOpenFile() {
        tabManager.actionOpenFile();
    }
    public void actionNewUntitled() { tabManager.actionNewUntitled(); }
    public void actionCreateFileOnDisk() { fileTreeManager.actionCreateFileOnDisk(); }
    public void actionCreateFolder() { fileTreeManager.actionCreateFolder(); }
    public void actionRenameSelected() { fileTreeManager.actionRenameSelected(); }
    public void actionDeleteSelected() { fileTreeManager.actionDeleteSelected(); }
    public void actionSaveActive() { tabManager.actionSaveActive(); }
    public void actionSaveAsActive() { tabManager.actionSaveAsActive(); }
    public void actionCloseActiveTab() { tabManager.actionCloseActiveTab(); }
    
    // ===== CollabCallbacks (from network) =====
    @Override public void applyRemoteEdit(String path, String text) {
        SwingUtilities.invokeLater(() -> tabManager.addRemoteTab(path, text));
    }

    @Override public void applyRemoteCursor(String path, String nick, int dot, int mark) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = tabManager.findTabByPath(path);
            if (tab == null) return;
            tab.updateRemoteCursor(nick, dot, mark, colorForNick(nick));
        });
    }

    @Override public void applyRemoteViewport(String path, int line) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = tabManager.findTabByPath(path);
            if (tab == null) {
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    tabManager.openFileInEditor(f);
                    tab = tabManager.findTabByPath(path);
                }
            }
            
            if (tab != null) {
                tabManager.getEditorTabs().setSelectedComponent(tab.getParent().getParent());
                try {
                    int offset = tab.getLineStartOffset(Math.max(0, line - 1));
                    Rectangle rect = tab.modelToView(offset);
                    if (rect != null) {
                        JViewport vp = (JViewport) tab.getParent();
                        vp.setViewPosition(new Point(0, rect.y));
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    @Override public void applyRemoteLaser(String path, String nick, int x, int y) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = tabManager.findTabByPath(path);
            if (tab != null) {
                tab.updateRemoteLaser(x, y, colorForNick(nick));
            }
        });
    }

    @Override public void onRoleInfo(String nick, String role) {
        SwingUtilities.invokeLater(() -> {
            userRoles.put(nick, role);
            connectedUsers.add(nick); 
            refreshCursors();
            if (role.equals("Professor") || role.equals("Student")) {
                log("[ROLE] " + nick + " = " + role);
            }
            
            if (Objects.equals(nick, collab.getNickname())) {
                updateThemeForRole(role);
            }
            
            updateAttendanceStatus();
        });
    }

    // ===== UI Updaters =====
    public void onTabUpdated(EditorTab tab) {
        String path = tab.getVirtualPath();
        String dirty = tab.isDirty() ? "[modified]" : "";
        statusLabel.setText(String.format("%s  %s  Ln %d, Col %d  |  %s  %s",
                path, dirty, tab.getCaretLine(), tab.getCaretCol(),
                collab.isConnected() ? "Online" : "Offline",
                keystrokeMode ? "(keystroke)" : "(200ms)"));
        tabManager.setTabTitleFor(tab);
    }
    
    private void updateThemeForRole(String role) {
        Color themeColor;
        String titleSuffix;

        if ("Professor".equals(role)) {
            themeColor = new Color(255, 182, 193); // Light Pink
            titleSuffix = " [PROFESSOR]";
            btnFollowMe.setVisible(true);
            btnLaser.setVisible(true);
            btnAttendance.setVisible(true);
        } else if ("Student".equals(role)) {
            themeColor = new Color(144, 238, 144); // Light Green
            titleSuffix = " [STUDENT]";
            btnFollowMe.setVisible(false);
            followMeActive = false;
            btnLaser.setVisible(false);
            laserActive = false;
            btnAttendance.setVisible(false);
            updateLaserState();
        } else {
            themeColor = Theme.STATUS_BAR_BG; // Default dark
            titleSuffix = "";
            btnFollowMe.setVisible(false);
            followMeActive = false;
            btnLaser.setVisible(false);
            laserActive = false;
            updateLaserState();
        }

        setTitle("Mini IDE (Java) - " + (collab.getNickname() != null ? collab.getNickname() : "Guest") + titleSuffix);
        
        statusPanel.setBackground(themeColor);
        if (getRootPane() != null) {
            getRootPane().setBorder(BorderFactory.createLineBorder(themeColor, 3));
        }
        
        revalidate();
        repaint();
    }
    
    public void setupViewportListener(EditorTab tab) {
        JViewport vp = (JViewport) tab.getParent();
        vp.addChangeListener(e -> {
            if (followMeActive && collab.isConnected() && tabManager.getEditorTabs().getSelectedComponent() == vp.getParent()) {
                viewportDebounce.restart();
            }
        });
    }

    public void setupLaserListener(EditorTab tab) {
        tab.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                if (laserActive && collab.isConnected()) {
                    collab.sendLaser(tab.getVirtualPath(), e.getX(), e.getY());
                }
            }
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                if (laserActive && collab.isConnected()) {
                    collab.sendLaser(tab.getVirtualPath(), e.getX(), e.getY());
                }
            }
        });
    }
    
    public void updateLaserState() {
        if (!laserActive && collab.isConnected()) {
            getActiveEditor().ifPresent(tab -> collab.sendLaser(tab.getVirtualPath(), -1, -1));
        }
        
        getActiveEditor().ifPresent(tab -> {
            if (laserActive) {
                tab.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                tab.setCursor(Cursor.getDefaultCursor());
            }
        });
    }

    public void sendViewportNow() {
        getActiveEditor().ifPresent(tab -> {
            try {
                JViewport vp = (JViewport) tab.getParent();
                int y = vp.getViewPosition().y;
                int line = tab.getLineOfOffset(tab.viewToModel(new Point(0, y))) + 1;
                collab.sendViewport(tab.getVirtualPath(), line);
            } catch (Exception ignored) {}
        });
    }

    public void showAttendanceDialog() {
        if (attendanceDialog == null) {
            attendanceDialog = new AttendanceDialog(this, btnAttendance, EXPECTED_STUDENTS);
        }
        attendanceDialog.setVisible(true);
        updateAttendanceStatus();
    }
    
    public void hideAttendanceDialog() {
        if (attendanceDialog != null) {
            attendanceDialog.setVisible(false);
        }
    }
    
    private void updateAttendanceStatus() {
        if (attendanceDialog != null && attendanceDialog.isVisible()) {
            attendanceDialog.update(connectedUsers);
        }
    }
    
    private void refreshCursors() {
        getActiveEditor().ifPresent(tab -> {
            // This is a placeholder for a more complex implementation
            // To properly refresh cursors, we'd need to know their last positions
        });
    }
    
    public void log(String msg) { console.append(msg + "\n"); console.setCaretPosition(console.getDocument().getLength()); }
    public void showError(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); log("ERROR: " + msg); }
    
    // ===== Getters and Setters =====
    public CollabClient getCollab() { return collab; }
    public boolean isKeystrokeMode() { return keystrokeMode; }
    public void setKeystrokeMode(boolean keystrokeMode) { this.keystrokeMode = keystrokeMode; }
    public void setFollowMeActive(boolean followMeActive) { this.followMeActive = followMeActive; }
    public void setLaserActive(boolean laserActive) { this.laserActive = laserActive; }
    public int toolkitShortcut() { return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx(); }
    public Optional<EditorTab> getActiveEditor() { return tabManager.getActiveEditor(); }
    public FileTreeManager getFileTreeManager() { return fileTreeManager; }
    
    private Color colorForNick(String nick) {
        String role = userRoles.get(nick);
        if ("Professor".equals(role)) {
            return new Color(255, 105, 180, 110);
        }
        if ("Student".equals(role)) {
            return new Color(0, 255, 0, 110);
        }
        return userColors.computeIfAbsent(nick, n -> {
            Color[] p = { new Color(66,133,244), new Color(52,168,83), new Color(234,67,53), new Color(251,188,5),
                    new Color(171,71,188), new Color(0,172,193), new Color(255,112,67), new Color(124,179,66) };
            Color c = p[Math.abs(n.hashCode()) % p.length];
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 110);
        });
    }
    
    // ===== Main =====
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
            
            Theme.apply();
            
            new CollabIDE().setVisible(true);
        });
    }
}