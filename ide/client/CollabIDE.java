package ide.client;

import ide.net.CollabClient;
import ide.net.CollabCallbacks;
import ide.ui.EditorTab;
import ide.ui.LineNumberView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;

public class CollabIDE extends JFrame implements CollabCallbacks {
    // UI
    private final JTree fileTree = new JTree();
    private final JTabbedPane editorTabs = new JTabbedPane();
    private final JTextArea console = new JTextArea();
    private final JLabel statusLabel = new JLabel("Offline");
    private final JPanel statusPanel = new JPanel(new BorderLayout());

    private File projectRoot;
    private final CollabClient collab = new CollabClient(this);
    private volatile boolean keystrokeMode = false;
    private final Map<String, Color> userColors = new HashMap<>();
    private final Map<String, String> userRoles = new HashMap<>();
    private final Set<String> lockedFiles = new HashSet<>();
    // Optimization: Fast lookup for tabs
    private final Map<String, EditorTab> tabMap = new HashMap<>();

    // Follow Me Mode
    private JToggleButton btnFollowMe;
    private boolean followMeActive = false;
    private final javax.swing.Timer viewportDebounce;

    // Laser Pointer Mode
    private JToggleButton btnLaser;
    private boolean laserActive = false;
    private final javax.swing.Timer laserDebounce;

    // Optimization: Cache role colors
    private static final Color COLOR_PROFESSOR = new Color(255, 105, 180, 110);
    private static final Color COLOR_STUDENT = new Color(0, 255, 0, 110);
    
    // Attendance Check
    private JToggleButton btnAttendance;
    private AttendanceDialog attendanceDialog;
    private static final String[] EXPECTED_STUDENTS = {"student1", "student2", "student3", "student4", "student5"};

    public CollabIDE() {
        super("Mini IDE - IntelliJ style");
        
        // Global Dark Theme Settings
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        UIManager.put("Panel.background", Color.DARK_GRAY);
        UIManager.put("Panel.foreground", Color.WHITE);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("Button.background", Color.DARK_GRAY);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("TextField.background", Color.DARK_GRAY);
        UIManager.put("TextField.foreground", Color.WHITE);
        UIManager.put("TextField.caretForeground", Color.WHITE);
        UIManager.put("TextArea.background", Color.DARK_GRAY);
        UIManager.put("TextArea.foreground", Color.WHITE);
        UIManager.put("ScrollPane.background", Color.DARK_GRAY);
        UIManager.put("Viewport.background", Color.DARK_GRAY);
        UIManager.put("Tree.background", Color.DARK_GRAY);
        UIManager.put("Tree.foreground", Color.WHITE);
        UIManager.put("Tree.textForeground", Color.WHITE);
        UIManager.put("List.background", Color.DARK_GRAY);
        UIManager.put("List.foreground", Color.WHITE);
        UIManager.put("MenuBar.background", Color.DARK_GRAY);
        UIManager.put("MenuBar.foreground", Color.WHITE);
        UIManager.put("Menu.background", Color.DARK_GRAY);
        UIManager.put("Menu.foreground", Color.WHITE);
        UIManager.put("MenuItem.background", Color.DARK_GRAY);
        UIManager.put("MenuItem.foreground", Color.WHITE);
        
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 780));
        setLocationRelativeTo(null);

        setJMenuBar(createMenuBar());
        add(createToolBar(), BorderLayout.NORTH);

        JSplitPane h = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createFileTreePanel(), createEditorPanel());
        h.setResizeWeight(0.22);
        h.setBackground(Color.DARK_GRAY);

        JScrollPane consoleScroll = new JScrollPane(console);
        console.setEditable(false);
        console.setBackground(new Color(40, 40, 40));
        console.setForeground(new Color(200, 200, 200));
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        consoleScroll.setPreferredSize(new Dimension(0, 180));

        JSplitPane v = new JSplitPane(JSplitPane.VERTICAL_SPLIT, h, consoleScroll);
        v.setResizeWeight(0.8);

        statusPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
        statusPanel.setBackground(Color.DARK_GRAY);
        statusLabel.setForeground(Color.WHITE);
        statusPanel.add(statusLabel, BorderLayout.WEST);

        getContentPane().add(v, BorderLayout.CENTER);
        getContentPane().add(statusPanel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (confirmCloseAllTabs()) { collab.disconnect(); dispose(); }
            }
        });

        editorTabs.addChangeListener(e -> {
            getActiveEditor().ifPresent(this::sendSnapshotNow);
            if (followMeActive && collab.isConnected()) {
                sendViewportNow();
            }
        });

        viewportDebounce = new javax.swing.Timer(100, e -> {
            if (followMeActive && collab.isConnected()) sendViewportNow();
        });
        viewportDebounce.setRepeats(false);

        laserDebounce = new javax.swing.Timer(50, e -> {
            // This timer is used to throttle laser updates if needed, 
            // but we might just send them directly in mouseMoved for smoothness.
            // For now, we'll use direct sending in the listener with a small check.
        });
    }

    private JComponent createEditorPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.add(editorTabs, BorderLayout.CENTER);
        return center;
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem openFolder = new JMenuItem("Open Folder...");
        openFolder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, toolkitShortcut()));
        openFolder.addActionListener(e -> chooseAndOpenProjectFolder());

        JMenuItem openFile = new JMenuItem("Open File...");
        openFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, toolkitShortcut()));
        openFile.addActionListener(e -> actionOpenFile());

        JMenuItem refresh = new JMenuItem("Refresh Folder");
        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, toolkitShortcut()));
        refresh.addActionListener(e -> reloadFileTree());

        JMenuItem newUntitled = new JMenuItem("New Untitled Tab");
        newUntitled.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, toolkitShortcut()));
        newUntitled.addActionListener(e -> actionNewUntitled());

        JMenuItem createFile = new JMenuItem("Create File on Disk...");
        createFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, toolkitShortcut() | InputEvent.SHIFT_DOWN_MASK));
        createFile.addActionListener(e -> actionCreateFileOnDisk());

        JMenuItem createFolder = new JMenuItem("Create Folder...");
        createFolder.addActionListener(e -> actionCreateFolder());

        JMenuItem rename = new JMenuItem("Rename...");
        rename.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        rename.addActionListener(e -> actionRenameSelected());

        JMenuItem delete = new JMenuItem("Delete");
        delete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        delete.addActionListener(e -> actionDeleteSelected());

        JMenuItem save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, toolkitShortcut()));
        save.addActionListener(e -> actionSaveActive());

        JMenuItem saveAs = new JMenuItem("Save As...");
        saveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, toolkitShortcut() | InputEvent.SHIFT_DOWN_MASK));
        saveAs.addActionListener(e -> actionSaveAsActive());

        JMenuItem closeTab = new JMenuItem("Close Tab");
        closeTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, toolkitShortcut()));
        closeTab.addActionListener(e -> actionCloseActiveTab());

        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> { if (confirmCloseAllTabs()) { collab.disconnect(); dispose(); } });

        file.add(openFolder); file.add(openFile); file.add(refresh); file.addSeparator();
        file.add(newUntitled); file.add(createFile); file.add(createFolder); file.addSeparator();
        file.add(rename); file.add(delete); file.addSeparator();
        file.add(save); file.add(saveAs); file.addSeparator();
        file.add(closeTab); file.addSeparator(); file.add(exit);
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        JMenuItem undo = new JMenuItem("Undo"); undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, toolkitShortcut()));
        undo.addActionListener(e -> getActiveEditor().ifPresent(EditorTab::undo));
        JMenuItem redo = new JMenuItem("Redo"); redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, toolkitShortcut()));
        redo.addActionListener(e -> getActiveEditor().ifPresent(EditorTab::redo));
        edit.add(undo); edit.add(redo);
        bar.add(edit);

        JMenu net = new JMenu("Network");
        JMenuItem connect = new JMenuItem("Connect..."); connect.addActionListener(e -> promptConnect());
        JMenuItem disconnect = new JMenuItem("Disconnect"); disconnect.addActionListener(e -> collab.disconnect());
        JCheckBoxMenuItem keystroke = new JCheckBoxMenuItem("Keystroke mode (no debounce)");
        keystroke.addItemListener(e -> keystrokeMode = keystroke.isSelected());
        net.add(connect); net.add(disconnect); net.addSeparator(); net.add(keystroke);
        bar.add(net);

        JMenu build = new JMenu("Build");
        JMenuItem compile = new JMenuItem("Compile Active Java");
        compile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));
        compile.addActionListener(e -> actionCompileActiveJava());
        JMenuItem run = new JMenuItem("Run Active Java");
        run.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0));
        run.addActionListener(e -> actionRunActiveJava());
        build.add(compile);
        build.add(run);
        bar.add(build);

        return bar;
    }

    private JToolBar createToolBar() {
        JToolBar tb = new JToolBar();
        JButton btnOpen = new JButton("Open Folder"); btnOpen.addActionListener(e -> chooseAndOpenProjectFolder());
        JButton btnSave = new JButton("Save"); btnSave.addActionListener(e -> actionSaveActive());
        JButton btnCompile = new JButton("Compile"); btnCompile.addActionListener(e -> actionCompileActiveJava());
        JButton btnRun = new JButton("Run"); btnRun.addActionListener(e -> actionRunActiveJava());
        JButton btnConnect = new JButton("Connect"); btnConnect.addActionListener(e -> promptConnect());
        
        btnFollowMe = new JToggleButton("Follow Me");
        btnFollowMe.setVisible(false); // Hidden by default
        btnFollowMe.addActionListener(e -> {
            followMeActive = btnFollowMe.isSelected();
            if (followMeActive) sendViewportNow();
        });

        btnLaser = new JToggleButton("Laser");
        btnLaser.setVisible(false);
        btnLaser.addActionListener(e -> {
            laserActive = btnLaser.isSelected();
            updateLaserState();
        });

        btnAttendance = new JToggleButton("Attendance");
        btnAttendance.setVisible(false);
        btnAttendance.addActionListener(e -> {
            if (btnAttendance.isSelected()) {
                showAttendanceDialog();
            } else {
                hideAttendanceDialog();
            }
        });

        tb.add(btnOpen); tb.add(btnSave); tb.add(btnCompile); tb.add(btnRun); tb.addSeparator(); tb.add(btnConnect); tb.addSeparator(); tb.add(btnFollowMe); tb.add(btnLaser); tb.add(btnAttendance);
        return tb;
    }

    private int toolkitShortcut() { return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx(); }

    // ===== Left: Project tree =====
    private JComponent createFileTreePanel() {
        fileTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No folder opened")));
        fileTree.setRootVisible(true);
        fileTree.setShowsRootHandles(true);
        
        // Dark theme for tree
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp, boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, focus);
                Object user = ((DefaultMutableTreeNode)value).getUserObject();
                if (user instanceof File f) {
                    setText(f.getName().isEmpty() ? f.getAbsolutePath() : f.getName());
                    setIcon(UIManager.getIcon(f.isDirectory() ? "FileView.directoryIcon" : "FileView.fileIcon"));
                }
                return this;
            }
        };
        renderer.setBackgroundNonSelectionColor(Color.DARK_GRAY);
        renderer.setTextNonSelectionColor(Color.WHITE);
        renderer.setBackgroundSelectionColor(new Color(60, 60, 60));
        renderer.setTextSelectionColor(Color.WHITE);
        fileTree.setCellRenderer(renderer);

        // context menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open"); open.addActionListener(e -> openSelectedInEditor()); popup.add(open);
        popup.addSeparator();
        JMenuItem createF = new JMenuItem("Create File..."); createF.addActionListener(e -> actionCreateFileOnDisk()); popup.add(createF);
        JMenuItem createD = new JMenuItem("Create Folder..."); createD.addActionListener(e -> actionCreateFolder()); popup.add(createD);
        popup.addSeparator();
        JMenuItem rename = new JMenuItem("Rename..."); rename.addActionListener(e -> actionRenameSelected()); popup.add(rename);
        JMenuItem delete = new JMenuItem("Delete"); delete.addActionListener(e -> actionDeleteSelected()); popup.add(delete);

        fileTree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = fileTree.getClosestRowForLocation(e.getX(), e.getY());
                    fileTree.setSelectionRow(row);
                    popup.show(fileTree, e.getX(), e.getY());
                } else if (e.getClickCount() == 2) {
                    TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                    if (last instanceof File f && f.isFile()) {
                        openFileInEditor(f);
                        getActiveEditor().ifPresent(thisTab -> sendSnapshotNow(thisTab));
                    }
                }
            }
        });
        return new JScrollPane(fileTree);
    }

    private Optional<File> getSelectedFile() {
        TreePath sel = fileTree.getSelectionPath();
        if (sel == null) return Optional.empty();
        Object user = ((DefaultMutableTreeNode) sel.getLastPathComponent()).getUserObject();
        return (user instanceof File f) ? Optional.of(f) : Optional.empty();
    }
    private File getSelectedDirOrRoot() {
        return getSelectedFile().map(f -> f.isDirectory() ? f : f.getParentFile())
                .orElse(projectRoot != null ? projectRoot : new File("."));
    }

    private void chooseAndOpenProjectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Open Project Folder");
        if (projectRoot != null) chooser.setCurrentDirectory(projectRoot);
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            projectRoot = chooser.getSelectedFile();
            reloadFileTree();
            log("Opened project: " + projectRoot.getAbsolutePath());
        }
    }

    private void reloadFileTree() {
        if (projectRoot == null) return;
        DefaultMutableTreeNode rootNode = createFileNode(projectRoot);
        fileTree.setModel(new DefaultTreeModel(rootNode));
        for (int i = 0; i < fileTree.getRowCount(); i++) fileTree.expandRow(i);
    }
    private DefaultMutableTreeNode createFileNode(File file) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(file);
        if (file.isDirectory()) {
            File[] ch = file.listFiles();
            if (ch != null) {
                Arrays.sort(ch, (a,b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File c : ch) node.add(createFileNode(c));
            }
        }
        return node;
    }

    // ===== Tabs =====
    private void openSelectedInEditor() { getSelectedFile().ifPresent(f -> { if (f.isFile()) openFileInEditor(f); }); }

    private void openFileInEditor(File file) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            EditorTab tab = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(i)).getViewport().getView();
            if (file.equals(tab.getFile())) { editorTabs.setSelectedIndex(i); return; }
        }
        try {
            String text = Files.readString(file.toPath());
            EditorTab tab = new EditorTab(file, text, null, collab, this::isKeystrokeMode, this::onTabUpdated);
            JScrollPane sp = new JScrollPane(tab);
            sp.setRowHeaderView(new LineNumberView(tab));
            editorTabs.addTab(file.getName(), sp);
            int idx = editorTabs.getTabCount() - 1; editorTabs.setSelectedIndex(idx);
            editorTabs.setToolTipTextAt(idx, file.getAbsolutePath());
            tabMap.put(tab.getVirtualPath(), tab); // Add to map
            setupViewportListener(tab);
            setupLaserListener(tab);
            onTabUpdated(tab);
        } catch (IOException ex) { showError("파일을 열 수 없습니다: " + ex.getMessage()); }
    }
    private boolean isKeystrokeMode() { return keystrokeMode; }


    private void actionNewUntitled() {
        EditorTab tab = new EditorTab(null, "", null, collab, this::isKeystrokeMode, this::onTabUpdated);
        JScrollPane sp = new JScrollPane(tab);
        sp.setRowHeaderView(new LineNumberView(tab));
        editorTabs.addTab("Untitled", sp);
        editorTabs.setSelectedIndex(editorTabs.getTabCount() - 1);
        tabMap.put(tab.getVirtualPath(), tab); // Add to map
        setupViewportListener(tab);
        setupLaserListener(tab);
        onTabUpdated(tab);
    }

    private void actionOpenFile() {
        JFileChooser chooser = new JFileChooser(projectRoot != null ? projectRoot : new File("."));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Open File");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (projectRoot == null) { projectRoot = f.getParentFile(); reloadFileTree(); }
            openFileInEditor(f);
        }
    }

    // ===== File Ops =====
    private void actionCreateFileOnDisk() {
        if (projectRoot == null) { showError("먼저 Open Folder로 프로젝트 폴더를 여세요."); return; }
        File dir = getSelectedDirOrRoot();
        String name = JOptionPane.showInputDialog(this, "파일 이름", "NewFile.java");
        if (name == null || name.trim().isEmpty()) return;
        File target = new File(dir, name.trim());
        if (target.exists()) { showError("이미 존재합니다: " + target.getAbsolutePath()); return; }
        try {
            if (target.createNewFile()) {
                log("[FILE] Created: " + target.getAbsolutePath());
                reloadFileTree();
                openFileInEditor(target);
                collab.sendFileCreate(target.getAbsolutePath(), false);
            } else showError("파일 생성 실패");
        } catch (IOException e) { showError("파일 생성 오류: " + e.getMessage()); }
    }

    private void actionCreateFolder() {
        if (projectRoot == null) { showError("먼저 Open Folder로 프로젝트 폴더를 여세요."); return; }
        File dir = getSelectedDirOrRoot();
        String name = JOptionPane.showInputDialog(this, "폴더 이름", "newFolder");
        if (name == null || name.trim().isEmpty()) return;
        File target = new File(dir, name.trim());
        if (target.exists()) { showError("이미 존재합니다: " + target.getAbsolutePath()); return; }
        if (target.mkdirs()) {
            log("[FILE] Folder Created: " + target.getAbsolutePath());
            reloadFileTree();
            collab.sendFileCreate(target.getAbsolutePath(), true);
        } else showError("폴더 생성 실패");
    }

    private void actionRenameSelected() {
        if (projectRoot == null) { showError("먼저 Open Folder로 프로젝트 폴더를 여세요."); return; }
        Optional<File> of = getSelectedFile(); if (of.isEmpty()) { showError("선택된 항목이 없습니다."); return; }
        File src = of.get();
        String newName = JOptionPane.showInputDialog(this, "새 이름", src.getName());
        if (newName == null || newName.trim().isEmpty()) return;
        File dst = new File(src.getParentFile(), newName.trim());
        if (dst.exists()) { showError("이미 존재합니다: " + dst.getAbsolutePath()); return; }
        boolean ok = src.renameTo(dst);
        if (!ok) { showError("이름 변경 실패"); return; }
        log("[FILE] Renamed: " + src.getAbsolutePath() + " -> " + dst.getAbsolutePath());
        updateTabsOnRename(src.getAbsolutePath(), dst.getAbsolutePath());
        reloadFileTree();
        collab.sendFileRename(src.getAbsolutePath(), dst.getAbsolutePath());
    }

    private void actionDeleteSelected() {
        if (projectRoot == null) { showError("먼저 Open Folder로 프로젝트 폴더를 여세요."); return; }
        Optional<File> of = getSelectedFile(); if (of.isEmpty()) { showError("선택된 항목이 없습니다."); return; }
        File target = of.get();
        int r = JOptionPane.showConfirmDialog(this, (target.isDirectory()? "폴더":"파일") + "를 삭제할까요?",
                "Delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        if (!deleteRecursive(target)) { showError("삭제 실패"); return; }
        log("[FILE] Deleted: " + target.getAbsolutePath());
        closeTabsUnder(target.getAbsolutePath());
        reloadFileTree();
        collab.sendFileDelete(target.getAbsolutePath());
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) if (!deleteRecursive(c)) return false;
        }
        return f.delete();
    }

    private void closeTabsUnder(String basePath) {
        for (int i = editorTabs.getTabCount() - 1; i >= 0; i--) {
            EditorTab t = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(i)).getViewport().getView();
            if (t.getFile() != null) {
                String p = t.getFile().getAbsolutePath();
                if (p.equals(basePath) || p.startsWith(basePath + File.separator)) {
                    tabMap.remove(t.getVirtualPath());
                    editorTabs.removeTabAt(i);
                }
            }
        }
    }
    private void updateTabsOnRename(String oldPath, String newPath) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            EditorTab t = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(i)).getViewport().getView();
            if (t.getFile() != null && t.getFile().getAbsolutePath().equals(oldPath)) {
                tabMap.remove(oldPath);
                t.setFile(new File(newPath));
                tabMap.put(newPath, t);
                setTabTitleFor(t);
            }
        }
    }

    // ===== Save / Close =====
    private void actionSaveActive() { getActiveEditor().ifPresent(tab -> {
        if (tab.getFile() == null) { actionSaveAsActive(); }
        else {
            if (tab.saveTo(tab.getFile())) {
                // Path shouldn't change here but good practice to re-put if needed, though for simple save it's same
                setTabTitleFor(tab); log("Saved: " + tab.getFile().getAbsolutePath()); reloadFileTree();
            }
        }
    });}
    private void actionSaveAsActive() { getActiveEditor().ifPresent(tab -> {
        JFileChooser chooser = new JFileChooser(projectRoot != null ? projectRoot : new File("."));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            String oldPath = tab.getVirtualPath();
            if (tab.saveTo(target)) {
                tabMap.remove(oldPath);
                tabMap.put(tab.getVirtualPath(), tab);
                setTabTitleFor(tab); log("Saved As: " + target.getAbsolutePath()); reloadFileTree();
            }
        }
    });}
    private void actionCloseActiveTab() {
        int idx = editorTabs.getSelectedIndex(); if (idx < 0) return;
        EditorTab tab = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(idx)).getViewport().getView();
        if (confirmCloseTab(tab)) {
            tabMap.remove(tab.getVirtualPath());
            editorTabs.removeTabAt(idx);
        }
    }
    private boolean confirmCloseAllTabs() {
        for (int i = editorTabs.getTabCount()-1; i>=0; i--) {
            EditorTab tab = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(i)).getViewport().getView();
            if (!confirmCloseTab(tab)) return false;
            tabMap.remove(tab.getVirtualPath());
            editorTabs.removeTabAt(i);
        }
        return true;
    }
    private boolean confirmCloseTab(EditorTab tab) {
        if (tab.isDirty()) {
            int r = JOptionPane.showConfirmDialog(this, "저장되지 않은 변경사항이 있습니다. 저장할까요?",
                    "Save changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.CANCEL_OPTION) return false;
            if (r == JOptionPane.YES_OPTION) {
                if (tab.getFile() == null) {
                    JFileChooser chooser = new JFileChooser(projectRoot != null ? projectRoot : new File("."));
                    int res = chooser.showSaveDialog(this);
                    if (res != JFileChooser.APPROVE_OPTION) return false;
                    if (!tab.saveTo(chooser.getSelectedFile())) return false; setTabTitleFor(tab); reloadFileTree();
                } else {
                    if (!tab.saveTo(tab.getFile())) return false;
                }
            }
        }
        return true;
    }

    private Optional<EditorTab> getActiveEditor() {
        int idx = editorTabs.getSelectedIndex(); if (idx < 0) return Optional.empty();
        JScrollPane sp = (JScrollPane) editorTabs.getComponentAt(idx);
        return Optional.of((EditorTab) sp.getViewport().getView());
    }
    private void setTabTitleFor(EditorTab tab) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            EditorTab t = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(i)).getViewport().getView();
            if (t == tab) {
                String title = tab.getDisplayName(); if (tab.isDirty()) title = "* " + title;
                editorTabs.setTitleAt(i, title);
                editorTabs.setToolTipTextAt(i, tab.getFile() != null ? tab.getFile().getAbsolutePath() : tab.getVirtualPath());
                break;
            }
        }
    }
    private void onTabUpdated(EditorTab tab) {
        String path = tab.getVirtualPath();
        String dirty = tab.isDirty() ? "[modified]" : "";
        statusLabel.setText(String.format("%s  %s  Ln %d, Col %d  |  %s  %s",
                path, dirty, tab.getCaretLine(), tab.getCaretCol(),
                collab.isConnected() ? "Online" : "Offline",
                keystrokeMode ? "(keystroke)" : "(200ms)"));
        setTabTitleFor(tab);
    }
    private void sendSnapshotNow(EditorTab tab) {
        if (collab.isConnected()) collab.sendSnapshot(tab.getVirtualPath(), tab.getText());
    }

    private void promptConnect() {
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        JTextField host = new JTextField("127.0.0.1");
        JTextField port = new JTextField("6000");
        JTextField nick = new JTextField("user" + (int)(Math.random()*100));
        JComboBox<String> role = new JComboBox<>(new String[]{"Student", "Professor"});
        p.add(new JLabel("Host:")); p.add(host);
        p.add(new JLabel("Port:")); p.add(port);
        p.add(new JLabel("Nickname:")); p.add(nick);
        p.add(new JLabel("Role:")); p.add(role);
        int r = JOptionPane.showConfirmDialog(this, p, "Connect to Server", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            try {
                String selectedRole = (String) role.getSelectedItem();
                collab.connect(host.getText().trim(), Integer.parseInt(port.getText().trim()), nick.getText().trim(), selectedRole);
                statusLabel.setText("Connected");
                log("Connected to " + host.getText() + ":" + port.getText() + " as " + selectedRole);
                updateThemeForRole(selectedRole); // Apply theme immediately
                getActiveEditor().ifPresent(this::sendSnapshotNow);
            } catch (Exception ex) { showError("연결 실패: " + ex.getMessage()); }
        }
    }

    // ===== Build: compile (client-side) =====
    private void actionCompileActiveJava() {
        Optional<EditorTab> opt = getActiveEditor(); if (opt.isEmpty()) { showError("열린 탭이 없습니다."); return; }
        EditorTab tab = opt.get();
        File f = tab.getFile();
        if (f == null) { showError("먼저 파일을 저장하세요(untitled는 컴파일 불가)."); return; }
        if (!f.getName().endsWith(".java")) { showError("현재 탭은 .java 파일이 아닙니다."); return; }
        if (tab.isDirty()) { if (!tab.saveTo(f)) { showError("저장 실패: 컴파일 취소"); return; } reloadFileTree(); }
        String path = f.getAbsolutePath();
        if (lockedFiles.contains(path)) { showError("이미 컴파일 중입니다(다른 사용자). 잠시 후 다시 시도"); return; }
        log("[BUILD] Requesting compile lock: " + path);
        collab.requestCompile(path);
    }

    // ===== CollabCallbacks (from network) =====
    @Override public void applyRemoteEdit(String path, String text) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab == null) {
                File f = (path.startsWith("untitled:")) ? null : new File(path);
                String vpath = (f == null) ? path : null;
                tab = new EditorTab(f, text, vpath, collab, this::isKeystrokeMode, this::onTabUpdated);
                JScrollPane sp = new JScrollPane(tab);
                sp.setRowHeaderView(new LineNumberView(tab));
                editorTabs.addTab(tab.getDisplayName(), sp);
                int idx = editorTabs.getTabCount() - 1; editorTabs.setSelectedIndex(idx);
                editorTabs.setToolTipTextAt(idx, f != null ? f.getAbsolutePath() : path);
                tabMap.put(tab.getVirtualPath(), tab);
                setupViewportListener(tab);
                setupLaserListener(tab);
                onTabUpdated(tab);
                log("[REMOTE] Opened " + path);
            } else {
                tab.applyRemoteText(text);
                log("[REMOTE] Updated " + path);
            }
        });
    }

    @Override public void applyRemoteCursor(String path, String nick, int dot, int mark) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab == null) return;
            tab.updateRemoteCursor(nick, dot, mark, colorForNick(nick));
        });
    }

    @Override public void applyRemoteViewport(String path, int line) {
        SwingUtilities.invokeLater(() -> {
            // Only follow if I am a student (or simply not the one broadcasting)
            // But here we assume only Professor broadcasts, so everyone else follows.
            // If I am Professor, I shouldn't follow myself (loop), but the server echoes back?
            // Server broadcast logic usually excludes sender. So it's safe.
            // However, if there are multiple professors, they might fight.
            // For now, just apply it.
            
            EditorTab tab = findTabByPath(path);
            if (tab == null) {
                // If file not open, try to open it (if it's a real file)
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    openFileInEditor(f);
                    tab = findTabByPath(path);
                }
            }
            
            if (tab != null) {
                editorTabs.setSelectedComponent(tab.getParent().getParent()); // Select tab (JScrollPane -> Tab)
                try {
                    int offset = tab.getLineStartOffset(Math.max(0, line - 1));
                    Rectangle rect = tab.modelToView(offset);
                    if (rect != null) {
                        // Center the view if possible, or just scroll to top
                        JViewport vp = (JViewport) tab.getParent();
                        vp.setViewPosition(new Point(0, rect.y));
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    @Override public void applyRemoteLaser(String path, int x, int y) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab != null) {
                tab.updateRemoteLaser(x, y);
            }
        });
    }

    @Override public void onRoleInfo(String nick, String role) {
        SwingUtilities.invokeLater(() -> {
            userRoles.put(nick, role);
            refreshCursors();
            if (role.equals("Professor") || role.equals("Student")) {
                String msg = "[ROLE] " + nick + " = " + role;
                log(msg);
            }
            
            if (Objects.equals(nick, collab.getNickname())) {
                updateThemeForRole(role);
            }
            
            // Update attendance
            updateAttendanceStatus();
        });
    }

    @Override public void onCompileGranted(String fpath, String byNick) {
        SwingUtilities.invokeLater(() -> {
            if (Objects.equals(byNick, collab.getNickname())) {
                log("[BUILD] Granted. Compiling: " + fpath);
                lockedFiles.add(fpath);
                new Thread(() -> runJavac(fpath), "compile-thread").start();
            } else {
                lockedFiles.add(fpath);
                log("[BUILD] " + byNick + " is compiling: " + fpath);
            }
        });
    }
    @Override public void onCompileDenied(String fpath, String holder) { SwingUtilities.invokeLater(() -> log("[BUILD] Denied. Current holder: " + holder + " for " + fpath)); }
    @Override public void onCompileStart(String fpath, String nick) { SwingUtilities.invokeLater(() -> { log("[BUILD] START by " + nick + " → " + fpath); lockedFiles.add(fpath); }); }
    @Override public void onCompileOut(String fpath, String nick, String line) { SwingUtilities.invokeLater(() -> log("[" + new File(fpath).getName() + "] " + nick + ": " + line)); }
    @Override public void onCompileEnd(String fpath, String nick, int exit) { SwingUtilities.invokeLater(() -> log("[BUILD] END by " + nick + " (exit=" + exit + ") → " + fpath)); }
    @Override public void onCompileReleased(String fpath, String nick) { SwingUtilities.invokeLater(() -> { lockedFiles.remove(fpath); log("[BUILD] RELEASED by " + nick + " → " + fpath); }); }

    private void runJavac(String fpath) {
        File file = new File(fpath);
        if (!file.exists()) {
            log("[BUILD] File not found: " + fpath);
            collab.releaseCompile(fpath);
            return;
        }
        if (projectRoot == null) {
            log("[BUILD] Project root not set.");
            collab.releaseCompile(fpath);
            return;
        }

        collab.sendCompileStart(fpath);
        // The file path should be relative to the project root for javac
        String relativePath = projectRoot.toURI().relativize(file.toURI()).getPath();

        ProcessBuilder pb = new ProcessBuilder("javac", "-d", "out", "-cp", "out", relativePath);
        pb.directory(projectRoot); // Run from the project root
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = br.readLine()) != null) { collab.sendCompileOut(fpath, line); onCompileOut(fpath, collab.getNickname(), line); }
            }
            int exit = p.waitFor();
            collab.sendCompileEnd(fpath, exit);
            onCompileEnd(fpath, collab.getNickname(), exit);
        } catch (IOException | InterruptedException e) {
            collab.sendCompileOut(fpath, "ERROR: " + e.getMessage());
        } finally {
            collab.releaseCompile(fpath);
        }
    }

    private void actionRunActiveJava() {
        Optional<EditorTab> opt = getActiveEditor();
        if (opt.isEmpty()) {
            showError("열린 탭이 없습니다.");
            return;
        }
        EditorTab tab = opt.get();
        File f = tab.getFile();
        if (f == null) {
            showError("먼저 파일을 저장하세요(untitled는 실행 불가).");
            return;
        }
        if (!f.getName().endsWith(".java")) {
            showError("현재 탭은 .java 파일이 아닙니다.");
            return;
        }
        if (projectRoot == null) {
            showError("프로젝트 폴더가 열려있지 않습니다. 'File > Open Folder'로 프로젝트를 먼저 여세요.");
            return;
        }

        // Ensure the file is compiled and up-to-date.
        // For simplicity, we'll just assume it's compiled.
        // A better implementation would check timestamps or trigger a compile.

        String relativePath = projectRoot.toURI().relativize(f.toURI()).getPath();
        if (relativePath.startsWith("out/") || relativePath.startsWith("out\\")) {
            showError("out 폴더 내의 파일은 직접 실행할 수 없습니다.");
            return;
        }
        String className = relativePath.replaceFirst("\\.java$", "").replace(File.separatorChar, '.');

        log("[RUN] Running: " + className);
        new Thread(() -> runJavaProcess(className), "run-java-thread").start();
    }

    private void runJavaProcess(String className) {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", "out", className);
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    log("[RUN] " + line);
                }
            }
            int exitCode = p.waitFor();
            log("[RUN] Process finished with exit code " + exitCode);
        } catch (IOException | InterruptedException e) {
            showError("실행 오류: " + e.getMessage());
            log("[RUN] ERROR: " + e.getMessage());
        }
    }

    // ===== helpers =====
    private EditorTab findTabByPath(String path) {
        return tabMap.get(path);
    }
    private void log(String msg) { console.append(msg + "\n"); console.setCaretPosition(console.getDocument().getLength()); }
    private void showError(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); log("ERROR: " + msg); }
//    private BooleanSupplier isKeystrokeMode = () -> keystrokeMode;
    private Color colorForNick(String nick) {
        String role = userRoles.get(nick);
        if ("Professor".equals(role)) {
            return COLOR_PROFESSOR;
        }
        if ("Student".equals(role)) {
            return COLOR_STUDENT;
        }
        // Fallback for unknown roles or before role info arrives
        return userColors.computeIfAbsent(nick, n -> {
            Color[] p = { new Color(66,133,244), new Color(52,168,83), new Color(234,67,53), new Color(251,188,5),
                    new Color(171,71,188), new Color(0,172,193), new Color(255,112,67), new Color(124,179,66) };
            Color c = p[Math.abs(n.hashCode()) % p.length];
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 110);
        });
    }

    private void updateThemeForRole(String role) {
        Color themeColor;
        String titleSuffix;

        if ("Professor".equals(role)) {
            themeColor = new Color(255, 182, 193); // Light Pink
            titleSuffix = " [PROFESSOR]";
            btnFollowMe.setVisible(true); // Show button for Professor
            btnLaser.setVisible(true);
            btnAttendance.setVisible(true);
        } else if ("Student".equals(role)) {
            themeColor = new Color(144, 238, 144); // Light Green
            titleSuffix = " [STUDENT]";
            btnFollowMe.setVisible(false);
            followMeActive = false; // Force disable
            btnLaser.setVisible(false);
            laserActive = false;
            btnAttendance.setVisible(false);
            updateLaserState();
        } else {
            themeColor = Color.DARK_GRAY; // Default dark
            titleSuffix = "";
            btnFollowMe.setVisible(false);
            followMeActive = false;
            btnLaser.setVisible(false);
            laserActive = false;
            updateLaserState();
        }

        setTitle("Mini IDE (Java) - " + (collab.getNickname() != null ? collab.getNickname() : "Guest") + titleSuffix);
        
        // Only color the status bar and border, NOT the whole content pane background
        statusPanel.setBackground(themeColor);
        if (getRootPane() != null) {
            getRootPane().setBorder(BorderFactory.createLineBorder(themeColor, 3));
        }
        
        revalidate();
        repaint();
    }

    private void setupViewportListener(EditorTab tab) {
        JViewport vp = (JViewport) tab.getParent();
        vp.addChangeListener(e -> {
            if (followMeActive && collab.isConnected() && editorTabs.getSelectedComponent() == vp.getParent()) {
                viewportDebounce.restart();
            }
        });
    }

    private void setupLaserListener(EditorTab tab) {
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

    private void updateLaserState() {
        // If laser is turned off, send a "hide" message (-1, -1)
        if (!laserActive && collab.isConnected()) {
            getActiveEditor().ifPresent(tab -> collab.sendLaser(tab.getVirtualPath(), -1, -1));
        }
        
        // Change cursor for active editor
        getActiveEditor().ifPresent(tab -> {
            if (laserActive) {
                tab.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                tab.setCursor(Cursor.getDefaultCursor());
            }
        });
    }

    private void sendViewportNow() {
        getActiveEditor().ifPresent(tab -> {
            try {
                JViewport vp = (JViewport) tab.getParent();
                int y = vp.getViewPosition().y;
                int line = tab.getLineOfOffset(tab.viewToModel(new Point(0, y))) + 1;
                collab.sendViewport(tab.getVirtualPath(), line);
            } catch (Exception ignored) {}
        });
    }

    // entry
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new CollabIDE().setVisible(true);
        });
    }
    
    // ===== Attendance Check Methods =====
    private void showAttendanceDialog() {
        if (attendanceDialog == null) {
            attendanceDialog = new AttendanceDialog(this);
        }
        attendanceDialog.setVisible(true);
        updateAttendanceStatus();
    }
    
    private void hideAttendanceDialog() {
        if (attendanceDialog != null) {
            attendanceDialog.setVisible(false);
        }
    }
    
    private void updateAttendanceStatus() {
        if (attendanceDialog != null && attendanceDialog.isVisible()) {
            attendanceDialog.update();
        }
    }
    
    private void refreshCursors() {
        getActiveEditor().ifPresent(tab -> {
            // Update remote cursors for active tab
            for (Map.Entry<String, String> entry : userRoles.entrySet()) {
                String nick = entry.getKey();
                String role = entry.getValue();
                Color color = colorForNick(nick);
                // This would need cursor position data, which we might not have
                // For now, just leave this as a placeholder
            }
        });
    }
    
    // ===== Attendance Dialog =====
    private class AttendanceDialog extends JDialog {
        private final Map<String, JLabel> studentLabels = new HashMap<>();
        
        public AttendanceDialog(JFrame parent) {
            super(parent, "Attendance Check",  false);
            setSize(300, 250);
            setLocationRelativeTo(parent);
            setDefaultCloseOperation(HIDE_ON_CLOSE);
            
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(Color.DARK_GRAY);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            JLabel title = new JLabel("Student Attendance");
            title.setForeground(Color.WHITE);
            title.setFont(title.getFont().deriveFont(16f).deriveFont(java.awt.Font.BOLD));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(title);
            panel.add(Box.createVerticalStrut(15));
            
            for (String student : EXPECTED_STUDENTS) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
                row.setBackground(Color.DARK_GRAY);
                
                JLabel indicator = new JLabel("●");
                indicator.setFont(indicator.getFont().deriveFont(20f));
                indicator.setForeground(Color.RED);
                
                JLabel nameLabel = new JLabel(student);
                nameLabel.setForeground(Color.WHITE);
                nameLabel.setFont(nameLabel.getFont().deriveFont(14f));
                
                row.add(indicator);
                row.add(Box.createHorizontalStrut(10));
                row.add(nameLabel);
                
                panel.add(row);
                studentLabels.put(student, indicator);
            }
            
            add(new JScrollPane(panel));
            
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) {
                    btnAttendance.setSelected(false);
                }
            });
        }
        
        public void update() {
            for (String student : EXPECTED_STUDENTS) {
                JLabel indicator = studentLabels.get(student);
                if (indicator != null) {
                    boolean isPresent = userRoles.containsKey(student);
                    indicator.setForeground(isPresent ? Color.GREEN : Color.RED);
                }
            }
        }
    }
}
