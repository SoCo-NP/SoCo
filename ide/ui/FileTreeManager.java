package ide.ui;

import ide.app.CollabActions;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;

/**
 * 프로젝트 파일 탐색기(File Explorer)를 관리하는 UI 매니저 클래스.
 * <p>
 * 좌측의 파일 트리 뷰를 담당하며, 파일 열기, 생성, 삭제, 이름 변경 등의 기능을 제공한다.
 * 사용자 액션 발생 시 컨트롤러(CollabActions)를 통해 서버로 변경 사항을 전파한다.
 * </p>
 */
public class FileTreeManager {

    // UI 컴포넌트
    private final JTree fileTree = new JTree();

    // 상태 및 의존성
    private File projectRoot; // 현재 열린 프로젝트의 루트 디렉토리
    private final Component parentFrame; // 다이얼로그의 부모 컴포넌트 (메인 프레임)
    private final CollabActions collab; // 컨트롤러 인터페이스
    private final TabManager tabManager; // 파일 열기 요청을 전달할 탭 매니저

    /**
     * FileTreeManager 생성자.
     *
     * @param parentFrame 다이얼로그를 띄울 부모 프레임
     * @param collab      컨트롤러 인터페이스
     * @param tabManager  탭 매니저
     */
    public FileTreeManager(Component parentFrame, CollabActions collab, TabManager tabManager) {
        this.parentFrame = parentFrame;
        this.collab = collab;
        this.tabManager = tabManager;
        initTree();
    }

    /**
     * 트리 컴포넌트를 초기화하고 렌더러 및 이벤트 리스너를 설정한다.
     */
    private void initTree() {
        fileTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No folder opened")));
        fileTree.setRootVisible(true);
        fileTree.setShowsRootHandles(true);

        // 커스텀 렌더러: 파일/폴더 아이콘 및 이름 표시
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp,
                    boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, focus);
                Object user = ((DefaultMutableTreeNode) value).getUserObject();
                if (user instanceof File f) {
                    setText(f.getName().isEmpty() ? f.getAbsolutePath() : f.getName());
                    setIcon(UIManager.getIcon(f.isDirectory() ? "FileView.directoryIcon" : "FileView.fileIcon"));
                }
                return this;
            }
        };
        fileTree.setCellRenderer(renderer);

        // 컨텍스트 메뉴 (우클릭 메뉴) 구성
        JPopupMenu popup = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        open.addActionListener(e -> openSelectedInEditor());
        popup.add(open);
        popup.addSeparator();

        JMenuItem createF = new JMenuItem("Create File...");
        createF.addActionListener(e -> actionCreateFileOnDisk());
        popup.add(createF);

        JMenuItem createD = new JMenuItem("Create Folder...");
        createD.addActionListener(e -> actionCreateFolder());
        popup.add(createD);
        popup.addSeparator();

        JMenuItem rename = new JMenuItem("Rename...");
        rename.addActionListener(e -> actionRenameSelected());
        popup.add(rename);

        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> actionDeleteSelected());
        popup.add(delete);

        // 마우스 이벤트 리스너: 우클릭 메뉴 및 더블클릭 파일 열기
        fileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = fileTree.getClosestRowForLocation(e.getX(), e.getY());
                    fileTree.setSelectionRow(row);
                    popup.show(fileTree, e.getX(), e.getY());
                } else if (e.getClickCount() == 2) {
                    TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
                    if (path == null)
                        return;
                    Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                    if (last instanceof File f && f.isFile()) {
                        tabManager.openFile(f);
                    }
                }
            }
        });
    }

    /**
     * 파일 트리 스크롤 패널을 반환한다.
     *
     * @return JScrollPane 인스턴스
     */
    public JComponent getComponent() {
        return new JScrollPane(fileTree);
    }

    /**
     * 프로젝트 루트 디렉토리를 설정하고 트리를 갱신한다.
     *
     * @param root 루트 디렉토리 파일 객체
     */
    public void setProjectRoot(File root) {
        this.projectRoot = root;
        reloadFileTree();
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    /**
     * 파일 시스템을 다시 읽어 트리를 갱신한다.
     */
    public void reloadFileTree() {
        if (projectRoot == null)
            return;
        DefaultMutableTreeNode rootNode = createFileNode(projectRoot);
        fileTree.setModel(new DefaultTreeModel(rootNode));
        // 편의상 모든 노드를 펼침
        for (int i = 0; i < fileTree.getRowCount(); i++)
            fileTree.expandRow(i);
    }

    /**
     * 재귀적으로 파일 노드를 생성한다.
     * 폴더를 먼저 정렬하고 그 다음 파일을 정렬하여 표시한다.
     */
    private DefaultMutableTreeNode createFileNode(File file) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(file);
        if (file.isDirectory()) {
            File[] ch = file.listFiles();
            if (ch != null) {
                Arrays.sort(ch, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory())
                        return -1;
                    if (!a.isDirectory() && b.isDirectory())
                        return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File c : ch)
                    node.add(createFileNode(c));
            }
        }
        return node;
    }

    // --- 사용자 액션 (Actions) ---

    /**
     * 선택된 파일을 에디터 탭으로 연다.
     */
    public void openSelectedInEditor() {
        getSelectedFile().ifPresent(f -> {
            if (f.isFile())
                tabManager.openFile(f);
        });
    }

    /**
     * 새 파일을 생성하고 서버에 알린다.
     */
    public void actionCreateFileOnDisk() {
        if (projectRoot == null) {
            showError("먼저 Open Folder로 프로젝트 폴더를 여세요.");
            return;
        }
        File dir = getSelectedDirOrRoot();
        String name = JOptionPane.showInputDialog(parentFrame, "파일 이름", "NewFile.java");
        if (name == null || name.trim().isEmpty())
            return;
        File target = new File(dir, name.trim());
        if (target.exists()) {
            showError("이미 존재합니다: " + target.getAbsolutePath());
            return;
        }
        try {
            if (target.createNewFile()) {
                reloadFileTree();
                tabManager.openFile(target);
                collab.sendFileCreate(target.getAbsolutePath(), false);
            } else
                showError("파일 생성 실패");
        } catch (java.io.IOException e) {
            showError("파일 생성 오류: " + e.getMessage());
        }
    }

    /**
     * 새 폴더를 생성하고 서버에 알린다.
     */
    public void actionCreateFolder() {
        if (projectRoot == null) {
            showError("먼저 Open Folder로 프로젝트 폴더를 여세요.");
            return;
        }
        File dir = getSelectedDirOrRoot();
        String name = JOptionPane.showInputDialog(parentFrame, "폴더 이름", "newFolder");
        if (name == null || name.trim().isEmpty())
            return;
        File target = new File(dir, name.trim());
        if (target.exists()) {
            showError("이미 존재합니다: " + target.getAbsolutePath());
            return;
        }
        if (target.mkdirs()) {
            reloadFileTree();
            collab.sendFileCreate(target.getAbsolutePath(), true);
        } else
            showError("폴더 생성 실패");
    }

    /**
     * 선택된 파일/폴더의 이름을 변경하고 서버에 알린다.
     */
    public void actionRenameSelected() {
        if (projectRoot == null) {
            showError("먼저 Open Folder로 프로젝트 폴더를 여세요.");
            return;
        }
        Optional<File> of = getSelectedFile();
        if (of.isEmpty()) {
            showError("선택된 항목이 없습니다.");
            return;
        }
        File src = of.get();
        String newName = JOptionPane.showInputDialog(parentFrame, "새 이름", src.getName());
        if (newName == null || newName.trim().isEmpty())
            return;
        File dst = new File(src.getParentFile(), newName.trim());
        if (dst.exists()) {
            showError("이미 존재합니다: " + dst.getAbsolutePath());
            return;
        }
        boolean ok = src.renameTo(dst);
        if (!ok) {
            showError("이름 변경 실패");
            return;
        }

        tabManager.updateTabsOnRename(src.getAbsolutePath(), dst.getAbsolutePath());
        reloadFileTree();
        collab.sendFileRename(src.getAbsolutePath(), dst.getAbsolutePath());
    }

    /**
     * 선택된 파일/폴더를 삭제하고 서버에 알린다.
     */
    public void actionDeleteSelected() {
        if (projectRoot == null) {
            showError("먼저 Open Folder로 프로젝트 폴더를 여세요.");
            return;
        }
        Optional<File> of = getSelectedFile();
        if (of.isEmpty()) {
            showError("선택된 항목이 없습니다.");
            return;
        }
        File target = of.get();
        int r = JOptionPane.showConfirmDialog(parentFrame, (target.isDirectory() ? "폴더" : "파일") + "를 삭제할까요?",
                "Delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.OK_OPTION)
            return;
        if (!deleteRecursive(target)) {
            showError("삭제 실패");
            return;
        }

        tabManager.closeTabsUnder(target.getAbsolutePath());
        reloadFileTree();
        collab.sendFileDelete(target.getAbsolutePath());
    }

    private Optional<File> getSelectedFile() {
        TreePath sel = fileTree.getSelectionPath();
        if (sel == null)
            return Optional.empty();
        Object user = ((DefaultMutableTreeNode) sel.getLastPathComponent()).getUserObject();
        return (user instanceof File f) ? Optional.of(f) : Optional.empty();
    }

    private File getSelectedDirOrRoot() {
        return getSelectedFile().map(f -> f.isDirectory() ? f : f.getParentFile())
                .orElse(projectRoot != null ? projectRoot : new File("."));
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null)
                for (File c : ch)
                    if (!deleteRecursive(c))
                        return false;
        }
        return f.delete();
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(parentFrame, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
