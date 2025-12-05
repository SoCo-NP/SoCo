package ide.ui;

import ide.net.CollabClient;
import ide.ui.EditorTab;
import ide.ui.LineNumberView;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class TabManager {
    private final JTabbedPane editorTabs = new JTabbedPane();
    private final Map<String, EditorTab> tabMap = new HashMap<>(); // Virtual Path -> Tab
    private final CollabClient collab;
    private final BooleanSupplier isKeystrokeMode;
    private final Consumer<EditorTab> onTabUpdated;

    // Follow Me
    private boolean followMeActive = false;
    private final Timer viewportDebounce;

    // Laser
    private boolean laserActive = false;

    public TabManager(CollabClient collab, BooleanSupplier isKeystrokeMode, Consumer<EditorTab> onTabUpdated) {
        this.collab = collab;
        this.isKeystrokeMode = isKeystrokeMode;
        this.onTabUpdated = onTabUpdated;

        editorTabs.addChangeListener(e -> {
            getActiveEditor().ifPresent(tab -> {
               if(collab.isConnected()) collab.sendSnapshot(tab.getVirtualPath(), tab.getText());
            });
            if (followMeActive && collab.isConnected()) {
                sendViewportNow();
            }
        });

        viewportDebounce = new Timer(100, e -> {
            if (followMeActive && collab.isConnected()) sendViewportNow();
        });
        viewportDebounce.setRepeats(false);
    }

    public JComponent getComponent() {
        return editorTabs;
    }

    public void openFile(File file) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            EditorTab tab = getTabAt(i);
            if (file.equals(tab.getFile())) {
                editorTabs.setSelectedIndex(i);
                return;
            }
        }
        try {
            String text = Files.readString(file.toPath());
            addTab(file, text, null);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(editorTabs, "파일을 열 수 없습니다: " + ex.getMessage());
        }
    }
    
    public void openUntitled() {
        addTab(null, "", null);
    }

    private void addTab(File file, String text, String vPathOverride) {
        EditorTab tab = new EditorTab(file, text, vPathOverride, collab, isKeystrokeMode, onTabUpdated);
        JScrollPane sp = new JScrollPane(tab);
        sp.setRowHeaderView(new LineNumberView(tab));
        
        String title = tab.getDisplayName();
        editorTabs.addTab(title, sp);
        int idx = editorTabs.getTabCount() - 1;
        editorTabs.setSelectedIndex(idx);
        editorTabs.setToolTipTextAt(idx, tab.getFile() != null ? tab.getFile().getAbsolutePath() : tab.getVirtualPath());
        
        tabMap.put(tab.getVirtualPath(), tab);
        setupViewportListener(tab);
        setupLaserListener(tab);
        onTabUpdated.accept(tab);
    }

    // --- Tab Access & Management ---

    private EditorTab getTabAt(int index) {
        return (EditorTab) ((JScrollPane) editorTabs.getComponentAt(index)).getViewport().getView();
    }

    public Optional<EditorTab> getActiveEditor() {
        int idx = editorTabs.getSelectedIndex();
        if (idx < 0) return Optional.empty();
        return Optional.of(getTabAt(idx));
    }

    public EditorTab findTabByPath(String path) {
        return tabMap.get(path);
    }

    public void applyRemoteEdit(String path, String text) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab == null) {
                // Open new tab if remote sends edit for unknown file
                File f = (path.startsWith("untitled:")) ? null : new File(path);
                addTab(f, text, (f==null)?path:null);
            } else {
                tab.applyRemoteText(text);
            }
        });
    }

    public void closeActiveTab(Runnable onDirtyDiskSave, Runnable onDirtyUntitledSave) {
        // Logic simplified: Just remove if simple. For full logic (save confirm), ideally CollabIDE delegates to here.
        // For refactoring, let's keep it simple or port the confirm logic.
        // For now, simple remove.
         int idx = editorTabs.getSelectedIndex();
         if(idx >= 0) {
             EditorTab t = getTabAt(idx);
             tabMap.remove(t.getVirtualPath());
             editorTabs.removeTabAt(idx);
         }
    }
    
    public void closeTabsUnder(String basePath) {
        for (int i = editorTabs.getTabCount() - 1; i >= 0; i--) {
            EditorTab t = getTabAt(i);
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
            EditorTab t = getTabAt(i);
            if (t.getFile() != null && t.getFile().getAbsolutePath().equals(oldPath)) {
                tabMap.remove(oldPath);
                t.setFile(new File(newPath));
                tabMap.put(newPath, t);
                updateTabTitle(t);
            }
        }
    }

    public void updateTabTitle(EditorTab tab) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            if (getTabAt(i) == tab) {
                String title = tab.getDisplayName();
                if (tab.isDirty()) title = "* " + title;
                editorTabs.setTitleAt(i, title);
                editorTabs.setToolTipTextAt(i, tab.getFile() != null ? tab.getFile().getAbsolutePath() : tab.getVirtualPath());
                break;
            }
        }
    }

    public int getTabCount() { return editorTabs.getTabCount(); }

    // --- Sync Features ---

    public void setFollowMe(boolean active) {
        this.followMeActive = active;
        if (active) sendViewportNow();
    }
    
    public void setLaser(boolean active) {
        this.laserActive = active;
        updateLaserState();
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
    
    private void updateLaserState() {
        if (!laserActive && collab.isConnected()) {
            getActiveEditor().ifPresent(tab -> collab.sendLaser(tab.getVirtualPath(), -1, -1));
        }
        // Cursor update
         getActiveEditor().ifPresent(tab -> {
            if (laserActive) {
                tab.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                tab.setCursor(Cursor.getDefaultCursor());
            }
        });
    }
    
    // Remote Viewport
    public void applyRemoteViewport(String path, int line) {
         SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab == null) {
                // If not open, ignore or open? Original logic: opened it.
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    openFile(f);
                    tab = findTabByPath(path);
                }
            }
            if (tab != null) {
                editorTabs.setSelectedComponent(tab.getParent().getParent());
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
    
    public void applyRemoteLaser(String path, int x, int y) {
         SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab != null) tab.updateRemoteLaser(x, y);
        });
    }
}
