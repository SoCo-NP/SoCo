package ide.ui;

import ide.client.CollabIDE;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TabManager {

    private final CollabIDE ide;
    private final JTabbedPane editorTabs = new JTabbedPane();
    private final Map<String, EditorTab> tabMap = new HashMap<>();

    public TabManager(CollabIDE ide) {
        this.ide = ide;
    }

    public JTabbedPane getEditorTabs() {
        return editorTabs;
    }

    public void openFileInEditor(File file) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            EditorTab tab = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(i)).getViewport().getView();
            if (file.equals(tab.getFile())) {
                editorTabs.setSelectedIndex(i);
                return;
            }
        }
        try {
            String text = Files.readString(file.toPath());
            EditorTab tab = new EditorTab(file, text, null, ide.getCollab(), ide::isKeystrokeMode, ide::onTabUpdated);
            addTab(tab, file.getName(), file.getAbsolutePath());
        } catch (IOException ex) {
            ide.showError("파일을 열 수 없습니다: " + ex.getMessage());
        }
    }

    public void actionNewUntitled() {
        EditorTab tab = new EditorTab(null, "", null, ide.getCollab(), ide::isKeystrokeMode, ide::onTabUpdated);
        addTab(tab, "Untitled", "Untitled");
    }

    private void addTab(EditorTab tab, String title, String tip) {
        JScrollPane sp = new JScrollPane(tab);
        sp.setRowHeaderView(new LineNumberView(tab));
        editorTabs.addTab(title, sp);
        int idx = editorTabs.getTabCount() - 1;
        editorTabs.setSelectedIndex(idx);
        editorTabs.setToolTipTextAt(idx, tip);
        tabMap.put(tab.getVirtualPath(), tab);
        ide.setupViewportListener(tab);
        ide.setupLaserListener(tab);
        ide.onTabUpdated(tab);
    }
    
    public Optional<EditorTab> getActiveEditor() {
        int idx = editorTabs.getSelectedIndex();
        if (idx < 0) return Optional.empty();
        JScrollPane sp = (JScrollPane) editorTabs.getComponentAt(idx);
        return Optional.of((EditorTab) sp.getViewport().getView());
    }

    public void setTabTitleFor(EditorTab tab) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            EditorTab t = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(i)).getViewport().getView();
            if (t == tab) {
                String title = tab.getDisplayName();
                if (tab.isDirty()) title = "* " + title;
                editorTabs.setTitleAt(i, title);
                editorTabs.setToolTipTextAt(i, tab.getFile() != null ? tab.getFile().getAbsolutePath() : tab.getVirtualPath());
                break;
            }
        }
    }

    public void actionCloseActiveTab() {
        int idx = editorTabs.getSelectedIndex();
        if (idx < 0) return;
        EditorTab tab = (EditorTab) ((JScrollPane) editorTabs.getComponentAt(idx)).getViewport().getView();
        if (ide.confirmCloseTab(tab)) {
            tabMap.remove(tab.getVirtualPath());
            editorTabs.removeTabAt(idx);
        }
    }

    public void closeTabsUnder(String basePath) {
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

    public void updateTabsOnRename(String oldPath, String newPath) {
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
    
    public EditorTab findTabByPath(String path) {
        return tabMap.get(path);
    }
    
    public void addRemoteTab(String path, String text) {
        EditorTab tab = findTabByPath(path);
        if (tab == null) {
            File f = (path.startsWith("untitled:")) ? null : new File(path);
            String vpath = (f == null) ? path : null;
            tab = new EditorTab(f, text, vpath, ide.getCollab(), ide::isKeystrokeMode, ide::onTabUpdated);
            addTab(tab, tab.getDisplayName(), f != null ? f.getAbsolutePath() : path);
            ide.log("[REMOTE] Opened " + path);
        } else {
            tab.applyRemoteText(text);
            ide.log("[REMOTE] Updated " + path);
        }
    }
}
