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
import java.util.function.Consumer;

public class FileTreeManager {
    private final JTree fileTree = new JTree();
    private File projectRoot;
    private final Component parentFrame; // For dialogs
    private final CollabActions collab;
    private final TabManager tabManager; // To open files

    public FileTreeManager(Component parentFrame, CollabActions collab, TabManager tabManager) {
        this.parentFrame = parentFrame;
        this.collab = collab;
        this.tabManager = tabManager;
        initTree();
    }

    private void initTree() {
        fileTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No folder opened")));
        fileTree.setRootVisible(true);
        fileTree.setShowsRootHandles(true);

        // Render
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

        // Context Menu
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

    public JComponent getComponent() {
        return new JScrollPane(fileTree);
    }

    public void setProjectRoot(File root) {
        this.projectRoot = root;
        reloadFileTree();
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    public void reloadFileTree() {
        if (projectRoot == null)
            return;
        DefaultMutableTreeNode rootNode = createFileNode(projectRoot);
        fileTree.setModel(new DefaultTreeModel(rootNode));
        for (int i = 0; i < fileTree.getRowCount(); i++)
            fileTree.expandRow(i);
    }

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

    // --- Actions ---

    public void openSelectedInEditor() {
        getSelectedFile().ifPresent(f -> {
            if (f.isFile())
                tabManager.openFile(f);
        });
    }

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
