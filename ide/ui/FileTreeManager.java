package ide.ui;

import ide.client.CollabIDE;

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

public class FileTreeManager {

    private JTree fileTree;
    private File projectRoot;

    public JComponent createFileTreePanel(CollabIDE ide) {
        this.fileTree = new JTree();
        fileTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No folder opened")));
        fileTree.setRootVisible(true);
        fileTree.setShowsRootHandles(true);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp, boolean leaf, int row, boolean focus) {
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

        JPopupMenu popup = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        open.addActionListener(e -> ide.openSelectedInEditor());
        popup.add(open);
        popup.addSeparator();
        JMenuItem createF = new JMenuItem("Create File...");
        createF.addActionListener(e -> ide.actionCreateFileOnDisk());
        popup.add(createF);
        JMenuItem createD = new JMenuItem("Create Folder...");
        createD.addActionListener(e -> ide.actionCreateFolder());
        popup.add(createD);
        popup.addSeparator();
        JMenuItem rename = new JMenuItem("Rename...");
        rename.addActionListener(e -> ide.actionRenameSelected());
        popup.add(rename);
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> ide.actionDeleteSelected());
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
                    if (path == null) return;
                    Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                    if (last instanceof File f && f.isFile()) {
                        ide.openFileInEditor(f);
                        ide.getActiveEditor().ifPresent(ide::sendSnapshotNow);
                    }
                }
            }
        });
        return new JScrollPane(fileTree);
    }

    public JTree getFileTree() {
        return fileTree;
    }

    public void setProjectRoot(File projectRoot) {
        this.projectRoot = projectRoot;
        reloadFileTree();
    }
    
    public File getProjectRoot() {
        return projectRoot;
    }

    public Optional<File> getSelectedFile() {
        if (fileTree == null) return Optional.empty();
        TreePath sel = fileTree.getSelectionPath();
        if (sel == null) return Optional.empty();
        Object user = ((DefaultMutableTreeNode) sel.getLastPathComponent()).getUserObject();
        return (user instanceof File f) ? Optional.of(f) : Optional.empty();
    }

    public File getSelectedDirOrRoot() {
        return getSelectedFile().map(f -> f.isDirectory() ? f : f.getParentFile())
                .orElse(projectRoot != null ? projectRoot : new File("."));
    }

    public void reloadFileTree() {
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
                Arrays.sort(ch, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File c : ch) node.add(createFileNode(c));
            }
        }
        return node;
    }
}
