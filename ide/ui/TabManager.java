package ide.ui;

import ide.app.CollabActions;
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

/**
 * 에디터 탭(Tab)들을 관리하는 UI 매니저 클래스.
 *
 * 여러 개의 파일을 탭 형태로 열고 닫는 기능을 제공하며,
 * 각 탭의 편집 이벤트(키 입력 등)를 감지하여 컨트롤러에게 전달한다.
 * 또한, 원격에서 수신된 편집/커서/뷰포트 이벤트를 해당 탭에 반영한다.
 *
 */
public class TabManager {

    // UI 컴포넌트
    private final JTabbedPane editorTabs = new JTabbedPane();

    // 상태 관리
    private final Map<String, EditorTab> tabMap = new HashMap<>(); // 가상 경로 -> 탭 매핑
    private final CollabActions collab; // 컨트롤러 인터페이스
    private final BooleanSupplier isKeystrokeMode; // 실시간 모드 여부 확인용
    private final Consumer<EditorTab> onTabUpdated; // 탭 상태 변경 시 콜백

    // 기능: Follow Me (화면 동기화)
    private boolean followMeActive = false;
    private final Timer viewportDebounce;

    // 기능: 레이저 포인터
    private boolean laserActive = false;

    /**
     * TabManager 생성자.
     *
     * @param collab          컨트롤러 인터페이스 (네트워크 요청용)
     * @param isKeystrokeMode 실시간 전송 모드 여부를 반환하는 공급자
     * @param onTabUpdated    탭 상태(제목 등)가 변경되었을 때 호출될 콜백
     */
    public TabManager(CollabActions collab, BooleanSupplier isKeystrokeMode, Consumer<EditorTab> onTabUpdated) {
        this.collab = collab;
        this.isKeystrokeMode = isKeystrokeMode;
        this.onTabUpdated = onTabUpdated;

        // 탭 변경 리스너: 탭을 바꿀 때마다 최신 내용을 서버로 전송 (동기화 보장)
        editorTabs.addChangeListener(e -> {
            getActiveEditor().ifPresent(tab -> {
                if (collab.isConnected())
                    collab.sendSnapshot(tab.getVirtualPath(), tab.getText());
            });
            if (followMeActive && collab.isConnected()) {
                sendViewportNow();
            }
        });

        // 뷰포트 전송 디바운스 타이머 (너무 잦은 전송 방지)
        viewportDebounce = new Timer(100, e -> {
            if (followMeActive && collab.isConnected())
                sendViewportNow();
        });
        viewportDebounce.setRepeats(false);
    }

    /**
     * 탭 패널 컴포넌트를 반환한다.
     *
     * @return JTabbedPane 인스턴스
     */
    public JComponent getComponent() {
        return editorTabs;
    }

    /**
     * 로컬 파일을 열어 새 탭을 생성하거나, 이미 열려있다면 해당 탭을 활성화한다.
     *
     * @param file 열고자 하는 파일
     */
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

    /**
     * 제목 없는 새 문서를 연다.
     */
    public void openUntitled() {
        addTab(null, "", null);
    }

    /**
     * 내부적으로 탭을 추가하고 초기화한다.
     *
     * @param file          파일 객체 (없으면 null)
     * @param text          초기 텍스트 내용
     * @param vPathOverride 가상 경로 강제 지정 (원격 파일 오픈 시 사용)
     */
    private void addTab(File file, String text, String vPathOverride) {
        EditorTab tab = new EditorTab(file, text, vPathOverride, collab, isKeystrokeMode, onTabUpdated);
        JScrollPane sp = new JScrollPane(tab);
        sp.setRowHeaderView(new LineNumberView(tab));

        String title = tab.getDisplayName();
        editorTabs.addTab(title, sp);
        int idx = editorTabs.getTabCount() - 1;
        editorTabs.setSelectedIndex(idx);
        editorTabs.setToolTipTextAt(idx,
                tab.getFile() != null ? tab.getFile().getAbsolutePath() : tab.getVirtualPath());

        tabMap.put(tab.getVirtualPath(), tab);
        setupViewportListener(tab);
        setupLaserListener(tab);
        onTabUpdated.accept(tab);
    }

    // --- 탭 접근 및 관리 ---

    private EditorTab getTabAt(int index) {
        return (EditorTab) ((JScrollPane) editorTabs.getComponentAt(index)).getViewport().getView();
    }

    /**
     * 현재 활성화된 에디터 탭을 반환한다.
     *
     * @return 활성 탭 (없으면 empty)
     */
    public Optional<EditorTab> getActiveEditor() {
        int idx = editorTabs.getSelectedIndex();
        if (idx < 0)
            return Optional.empty();
        return Optional.of(getTabAt(idx));
    }

    /**
     * 가상 경로로 열려있는 탭을 찾는다.
     *
     * @param path 파일의 가상 경로
     * @return 해당 탭 (없으면 null)
     */
    public EditorTab findTabByPath(String path) {
        return tabMap.get(path);
    }

    /**
     * 원격에서 수신된 편집 내용을 반영한다.
     * 해당 파일이 열려있지 않다면 새로 연다.
     *
     * @param path 파일 경로
     * @param text 변경된 전체 텍스트
     */
    public void applyRemoteEdit(String path, String text) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab == null) {
                // 원격에서 모르는 파일에 대한 편집이 오면 새 탭으로 엽니다.
                File f = (path.startsWith("untitled:")) ? null : new File(path);
                addTab(f, text, (f == null) ? path : null);
            } else {
                tab.applyRemoteText(text);
            }
        });
    }

    /**
     * 원격에서 수신된 커서 위치를 반영한다.
     *
     * @param path  파일 경로
     * @param nick  사용자 닉네임
     * @param color 커서 색상
     * @param dot   커서 위치
     * @param mark  선택 영역 시작점
     */
    public void applyRemoteCursor(String path, String nick, Color color, int dot, int mark) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab != null) {
                tab.updateRemoteCursor(nick, dot, mark, color);
            }
        });
    }

    /**
     * 현재 활성화된 탭을 닫는다.
     *
     * @param onDirtyDiskSave     저장되지 않은 디스크 파일일 경우 실행할 콜백
     * @param onDirtyUntitledSave 저장되지 않은 새 문서일 경우 실행할 콜백
     */
    public void closeActiveTab(Runnable onDirtyDiskSave, Runnable onDirtyUntitledSave) {
        // 현재는 단순 닫기만 구현 (저장 확인 로직 생략)
        int idx = editorTabs.getSelectedIndex();
        if (idx >= 0) {
            EditorTab t = getTabAt(idx);
            tabMap.remove(t.getVirtualPath());
            editorTabs.removeTabAt(idx);
        }
    }

    /**
     * 특정 경로 하위에 있는 모든 탭을 닫는다. (폴더 삭제 시 사용)
     *
     * @param basePath 기준 경로
     */
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

    /**
     * 파일 이름 변경 시 열려있는 탭 정보를 갱신한다.
     *
     * @param oldPath 변경 전 경로
     * @param newPath 변경 후 경로
     */
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

    /**
     * 탭의 제목(파일명, 수정 여부 표시)을 갱신한다.
     *
     * @param tab 대상 탭
     */
    public void updateTabTitle(EditorTab tab) {
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            if (getTabAt(i) == tab) {
                String title = tab.getDisplayName();
                if (tab.isDirty())
                    title = "* " + title;
                editorTabs.setTitleAt(i, title);
                editorTabs.setToolTipTextAt(i,
                        tab.getFile() != null ? tab.getFile().getAbsolutePath() : tab.getVirtualPath());
                break;
            }
        }
    }

    public int getTabCount() {
        return editorTabs.getTabCount();
    }

    // --- 동기화 기능 (Follow Me, Laser) ---

    /**
     * Follow Me (화면 동기화) 기능을 활성화/비활성화한다.
     *
     * @param active true면 활성화
     */
    public void setFollowMe(boolean active) {
        this.followMeActive = active;
        if (active)
            sendViewportNow();
    }

    /**
     * 레이저 포인터 기능을 활성화/비활성화한다.
     *
     * @param active true면 활성화
     */
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
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                if (laserActive && collab.isConnected()) {
                    collab.sendLaser(tab.getVirtualPath(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
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
            } catch (Exception ignored) {
            }
        });
    }

    private void updateLaserState() {
        if (!laserActive && collab.isConnected()) {
            getActiveEditor().ifPresent(tab -> collab.sendLaser(tab.getVirtualPath(), -1, -1));
        }
        // 커서 모양 변경 (십자선)
        getActiveEditor().ifPresent(tab -> {
            if (laserActive) {
                tab.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                tab.setCursor(Cursor.getDefaultCursor());
            }
        });
    }

    // --- 원격 뷰포트/레이저 반영 ---

    /**
     * 원격에서 수신된 뷰포트 위치(스크롤)를 반영한다.
     *
     * @param path 파일 경로
     * @param line 이동할 줄 번호
     */
    public void applyRemoteViewport(String path, int line) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab == null) {
                // 파일이 안 열려있으면 엽니다.
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
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * 원격에서 수신된 레이저 포인터 위치를 반영한다.
     *
     * @param path 파일 경로
     * @param x    X 좌표
     * @param y    Y 좌표
     */
    public void applyRemoteLaser(String path, int x, int y) {
        SwingUtilities.invokeLater(() -> {
            EditorTab tab = findTabByPath(path);
            if (tab != null)
                tab.updateRemoteLaser(x, y);
        });
    }
}
