package ide.ui;

import ide.app.CollabActions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * 상단 툴바(ToolBar)와 메뉴바(MenuBar)를 관리하는 UI 매니저 클래스.
 *
 * 사용자가 접근할 수 있는 모든 액션 버튼과 메뉴 항목을 생성하고 이벤트 리스너를 연결한다.
 * 파일 열기/저장, 네트워크 연결, 기능 토글(Follow Me, Laser) 등의 진입점 역할을 한다.
 *
 */
public class ToolBarManager {

    // UI 컴포넌트
    private final JToolBar toolBar = new JToolBar();
    private final JMenuBar menuBar = new JMenuBar();

    // 의존성
    private final Component parentFrame;
    private final CollabActions collab;
    private final TabManager tabManager;
    private final FileTreeManager fileTreeManager;

    // 상태 토글 버튼 (외부에서 상태 업데이트 필요)
    private final JToggleButton btnFollowMe = new JToggleButton("🌟 FollowMe");
    private final JToggleButton btnLaser = new JToggleButton("⚡ Laser");
    private final JButton btnAttendance = new JButton("📋 Attendance");
    private final JButton btnQuestionDialog = new JButton("💬 학생질문"); // 교수자 전용
    private final JButton btnQuestion = new JButton("💬 질문하기"); // 학생 전용

    // 연결 다이얼로그 실행 콜백
    private final Runnable promptConnectAction;

    /**
     * ToolBarManager 생성자.
     *
     * @param parentFrame         부모 프레임 (다이얼로그용)
     * @param collab              컨트롤러 인터페이스
     * @param tabManager          탭 매니저
     * @param fileTreeManager     파일 트리 매니저
     * @param promptConnectAction 연결 다이얼로그 실행 람다
     */
    public ToolBarManager(Component parentFrame, CollabActions collab, TabManager tabManager,
            FileTreeManager fileTreeManager, Runnable promptConnectAction) {
        this.parentFrame = parentFrame;
        this.collab = collab;
        this.tabManager = tabManager;
        this.fileTreeManager = fileTreeManager;
        this.promptConnectAction = promptConnectAction;

        initActions();
    }

    /**
     * 툴바와 메뉴바의 모든 항목을 초기화하고 액션을 연결한다.
     */
    private void initActions() {
        // --- 툴바 (Toolbar) 구성 ---
        JButton btnOpen = new JButton("Open Folder");
        btnOpen.addActionListener(e -> chooseAndOpenProjectFolder());

        JButton btnSave = new JButton("Save");
        btnSave.addActionListener(e -> actionSaveActive());

        JButton btnConnect = new JButton("Connect");
        btnConnect.addActionListener(e -> promptConnectAction.run());

        // 기능 버튼 초기화 (기본 숨김, 권한에 따라 표시)
        btnFollowMe.setVisible(false);
        btnFollowMe.addActionListener(e -> tabManager.setFollowMe(btnFollowMe.isSelected()));

        btnLaser.setVisible(false);
        btnLaser.addActionListener(e -> tabManager.setLaser(btnLaser.isSelected()));

        btnAttendance.setVisible(false);
        btnAttendance.addActionListener(e -> {
            AttendanceDialog dialog = new AttendanceDialog((Frame) parentFrame, collab);
            dialog.setVisible(true);
        });

        btnQuestionDialog.setVisible(false); // 초기에는 숨김
        btnQuestionDialog.addActionListener(e -> {
            collab.showQuestionDialog();
        });

        btnQuestion.setVisible(false); // 초기에는 숨김
        btnQuestion.addActionListener(e -> {
            String question = JOptionPane.showInputDialog(
                    parentFrame,
                    "교수자에게 질문할 내용을 입력하세요:",
                    "질문하기",
                    JOptionPane.QUESTION_MESSAGE);

            if (question != null && !question.trim().isEmpty()) {
                collab.sendQuestion(question.trim());
                JOptionPane.showMessageDialog(
                        parentFrame,
                        "질문이 전송되었습니다!",
                        "전송 완료",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        toolBar.add(btnOpen);
        toolBar.add(btnSave);
        toolBar.addSeparator();
        toolBar.add(btnConnect);
        toolBar.addSeparator();
        toolBar.add(btnFollowMe);
        toolBar.add(btnLaser);
        toolBar.add(btnAttendance);
        toolBar.add(btnQuestionDialog);
        toolBar.add(btnQuestion);

        // --- 메뉴바 (MenuBar) 구성 ---
        JMenu file = new JMenu("File");

        JMenuItem openFolder = new JMenuItem("Open Folder...");
        openFolder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, toolkitShortcut()));
        openFolder.addActionListener(e -> chooseAndOpenProjectFolder());

        JMenuItem openFile = new JMenuItem("Open File...");
        openFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, toolkitShortcut()));
        openFile.addActionListener(e -> actionOpenFile());

        JMenuItem refresh = new JMenuItem("Refresh Folder");
        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, toolkitShortcut()));
        refresh.addActionListener(e -> fileTreeManager.reloadFileTree());

        JMenuItem newUntitled = new JMenuItem("New Untitled Tab");
        newUntitled.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, toolkitShortcut()));
        newUntitled.addActionListener(e -> tabManager.openUntitled());

        JMenuItem createFile = new JMenuItem("Create File on Disk...");
        createFile
                .setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, toolkitShortcut() | InputEvent.SHIFT_DOWN_MASK));
        createFile.addActionListener(e -> fileTreeManager.actionCreateFileOnDisk());

        JMenuItem createFolder = new JMenuItem("Create Folder...");
        createFolder.addActionListener(e -> fileTreeManager.actionCreateFolder());

        JMenuItem rename = new JMenuItem("Rename...");
        rename.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        rename.addActionListener(e -> fileTreeManager.actionRenameSelected());

        JMenuItem delete = new JMenuItem("Delete");
        delete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        delete.addActionListener(e -> fileTreeManager.actionDeleteSelected());

        JMenuItem save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, toolkitShortcut()));
        save.addActionListener(e -> actionSaveActive());

        JMenuItem saveAs = new JMenuItem("Save As...");
        saveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, toolkitShortcut() | InputEvent.SHIFT_DOWN_MASK));
        saveAs.addActionListener(e -> actionSaveAsActive());

        JMenuItem closeTab = new JMenuItem("Close Tab");
        closeTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, toolkitShortcut()));
        closeTab.addActionListener(e -> tabManager.closeActiveTab(null, null));

        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));

        file.add(openFolder);
        file.add(openFile);
        file.add(refresh);
        file.addSeparator();
        file.add(newUntitled);
        file.add(createFile);
        file.add(createFolder);
        file.addSeparator();
        file.add(rename);
        file.add(delete);
        file.addSeparator();
        file.add(save);
        file.add(saveAs);
        file.addSeparator();
        file.add(closeTab);
        file.addSeparator();
        file.add(exit);
        menuBar.add(file);

        // Edit 메뉴
        JMenu edit = new JMenu("Edit");
        JMenuItem undo = new JMenuItem("Undo");
        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, toolkitShortcut()));
        undo.addActionListener(e -> tabManager.getActiveEditor().ifPresent(EditorTab::undo));
        JMenuItem redo = new JMenuItem("Redo");
        redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, toolkitShortcut()));
        redo.addActionListener(e -> tabManager.getActiveEditor().ifPresent(EditorTab::redo));
        edit.add(undo);
        edit.add(redo);
        menuBar.add(edit);

        // Network 메뉴
        JMenu net = new JMenu("Network");
        JMenuItem connect = new JMenuItem("Connect...");
        connect.addActionListener(e -> promptConnectAction.run());
        JMenuItem disconnect = new JMenuItem("Disconnect");
        disconnect.addActionListener(e -> collab.disconnect());

        net.add(connect);
        net.add(disconnect);
        menuBar.add(net);
    }

    /**
     * 프로젝트 폴더 열기 다이얼로그를 띄운다.
     */
    private void chooseAndOpenProjectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Open Project Folder");
        if (fileTreeManager.getProjectRoot() != null)
            chooser.setCurrentDirectory(fileTreeManager.getProjectRoot());
        int res = chooser.showOpenDialog(parentFrame);
        if (res == JFileChooser.APPROVE_OPTION) {
            fileTreeManager.setProjectRoot(chooser.getSelectedFile());
        }
    }

    /**
     * 단일 파일 열기 다이얼로그를 띄운다.
     */
    private void actionOpenFile() {
        JFileChooser chooser = new JFileChooser(
                fileTreeManager.getProjectRoot() != null ? fileTreeManager.getProjectRoot() : new File("."));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Open File");
        if (chooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (fileTreeManager.getProjectRoot() == null) {
                fileTreeManager.setProjectRoot(f.getParentFile());
            }
            tabManager.openFile(f);
        }
    }

    /**
     * 현재 활성화된 탭을 저장한다.
     */
    private void actionSaveActive() {
        tabManager.getActiveEditor().ifPresent(tab -> {
            if (tab.getFile() == null) {
                actionSaveAsActive();
            } else {
                if (tab.saveTo(tab.getFile())) {
                    tabManager.updateTabTitle(tab);
                    fileTreeManager.reloadFileTree();
                }
            }
        });
    }

    /**
     * 다른 이름으로 저장 다이얼로그를 띄운다.
     */
    private void actionSaveAsActive() {
        tabManager.getActiveEditor().ifPresent(tab -> {
            JFileChooser chooser = new JFileChooser(
                    fileTreeManager.getProjectRoot() != null ? fileTreeManager.getProjectRoot() : new File("."));
            int res = chooser.showSaveDialog(parentFrame);
            if (res == JFileChooser.APPROVE_OPTION) {
                File target = chooser.getSelectedFile();
                if (tab.saveTo(target)) {
                    tabManager.updateTabTitle(tab);
                    // 실제로는 탭의 파일 참조도 업데이트해야 함 (간소화됨)
                }
            }
        });
    }

    /**
     * OS별 단축키 마스크를 반환한다. (Mac: Command, Win: Ctrl)
     */
    private int toolkitShortcut() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    // --- UI 컴포넌트 접근자 (Getters) ---

    public JToolBar getToolBar() {
        return toolBar;
    }

    public JMenuBar getMenuBar() {
        return menuBar;
    }

    public JToggleButton getBtnFollowMe() {
        return btnFollowMe;
    }

    public JToggleButton getBtnLaser() {
        return btnLaser;
    }

    public JButton getBtnAttendance() {
        return btnAttendance;
    }

    /**
     * Role에 따라 버튼 표시/숨김을 업데이트한다.
     *
     * @param isProfessor 교수자 여부
     */
    public void updateRoleUI(boolean isProfessor) {
        // 교수자 전용 버튼
        btnFollowMe.setVisible(isProfessor);
        btnLaser.setVisible(isProfessor);
        btnAttendance.setVisible(isProfessor);
        btnQuestionDialog.setVisible(isProfessor);

        // 학생 전용 버튼
        btnQuestion.setVisible(!isProfessor);
    }
}
