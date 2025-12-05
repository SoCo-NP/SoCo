package ide.ui;

import ide.app.CollabActions;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 개별 소스 코드를 편집하는 에디터 컴포넌트 클래스.
 * <p>
 * JTextArea를 확장하여 텍스트 편집, 실행 취소(Undo/Redo), 구문 강조(테마 적용) 기능을 제공한다.
 * 사용자의 키 입력을 감지하여 컨트롤러에게 전송하고, 원격 사용자의 커서 및 레이저를 화면에 그린다.
 * </p>
 */
public class EditorTab extends JTextArea {

    // 파일 상태
    private File file; // 연결된 로컬 파일 (null이면 새 문서)
    private boolean dirty = false; // 수정 여부
    private final String virtualPath; // 네트워크 식별용 가상 경로

    // 에디터 기능
    private final UndoManager undoManager = new UndoManager();
    private int baseFontSize = 14;

    // 원격 협업 시각화
    private final java.util.Map<String, RemoteCursor> remoteCursors = new java.util.concurrent.ConcurrentHashMap<>();
    private Point remoteLaserPoint = null;

    // 의존성
    private final CollabActions collab;
    private final BooleanSupplier isKeystrokeMode;
    private final Consumer<EditorTab> onUpdate;

    // 전송 제어
    private boolean suppressBroadcast = false; // 원격 편집 반영 중에는 전송 막기
    private final javax.swing.Timer debounce; // 텍스트 전송 디바운스 타이머
    private final javax.swing.Timer cursorDebounce; // 커서 전송 디바운스 타이머

    // 원격 커서 태그 관리
    private final Map<String, Object> cursorTags = new HashMap<>();

    /**
     * EditorTab 생성자.
     *
     * @param file                연결할 파일 객체 (없으면 null)
     * @param text                초기 텍스트 내용
     * @param providedVirtualPath 강제 지정할 가상 경로 (없으면 null)
     * @param collab              컨트롤러 인터페이스
     * @param keystrokeMode       실시간 전송 모드 여부
     * @param onUpdate            상태 변경 시 호출될 콜백
     */
    public EditorTab(File file,
            String text,
            String providedVirtualPath,
            CollabActions collab,
            BooleanSupplier keystrokeMode,
            Consumer<EditorTab> onUpdate) {
        super(text);
        this.file = file;
        this.collab = collab;
        this.isKeystrokeMode = keystrokeMode;
        this.onUpdate = onUpdate;
        this.virtualPath = (file != null) ? file.getAbsolutePath()
                : (providedVirtualPath != null ? providedVirtualPath : ("untitled:" + UUID.randomUUID()));

        // 스타일 설정
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, baseFontSize));
        setTabSize(4);
        setLineWrap(false);
        setWrapStyleWord(false);
        setMargin(new Insets(8, 8, 8, 8));

        // 테마 적용 (다크 모드)
        setBackground(ide.ui.Theme.EDITOR_BG);
        setForeground(ide.ui.Theme.EDITOR_FG);
        setCaretColor(ide.ui.Theme.EDITOR_CARET);
        setSelectionColor(ide.ui.Theme.EDITOR_SELECTION);
        setSelectedTextColor(ide.ui.Theme.EDITOR_SELECTION_FG);

        // Undo/Redo 리스너
        getDocument().addUndoableEditListener(e -> {
            undoManager.addEdit(e.getEdit());
            markDirty(true);
        });

        // 텍스트 변경 리스너 (서버 전송 트리거)
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleBroadcast();
                markDirty(true);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleBroadcast();
                markDirty(true);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleBroadcast();
                markDirty(true);
            }
        });

        // 커서 이동 리스너
        addCaretListener(e -> {
            onUpdate.accept(EditorTab.this);
            scheduleCursorSend();
        });

        // 단축키 설정 (Ctrl+S 저장)
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "save");
        getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onUpdate.accept(EditorTab.this);
            }
        });

        // 디바운스 타이머 설정 (네트워크 부하 감소)
        debounce = new javax.swing.Timer(200, e -> {
            if (!suppressBroadcast && collab.isConnected())
                collab.sendSnapshot(getVirtualPath(), getText());
        });
        debounce.setRepeats(false);

        cursorDebounce = new javax.swing.Timer(120, e -> {
            if (!collab.isConnected())
                return;
            collab.sendCursor(getVirtualPath(), getCaret().getDot(), getCaret().getMark());
        });
        cursorDebounce.setRepeats(false);
    }

    /**
     * 텍스트 변경 사항을 서버로 전송하도록 예약한다.
     * 실시간 모드일 경우 즉시 전송하고, 아니면 타이머를 재시작한다.
     */
    private void scheduleBroadcast() {
        if (suppressBroadcast)
            return;
        if (isKeystrokeMode.getAsBoolean()) {
            if (collab.isConnected())
                collab.sendSnapshot(getVirtualPath(), getText());
        } else {
            debounce.restart();
        }
    }

    private void scheduleCursorSend() {
        cursorDebounce.restart();
    }

    // --- 파일 및 상태 관리 ---

    public File getFile() {
        return file;
    }

    public void setFile(File f) {
        this.file = f;
        this.dirty = false;
        onUpdate.accept(this);
    }

    public String getDisplayName() {
        return file != null ? file.getName() : "Untitled";
    }

    public boolean isDirty() {
        return dirty;
    }

    public String getVirtualPath() {
        return (file != null) ? file.getAbsolutePath() : virtualPath;
    }

    public void markDirty(boolean d) {
        if (this.dirty != d) {
            this.dirty = d;
        }
        onUpdate.accept(this);
    }

    /**
     * 현재 내용을 지정된 파일에 저장한다.
     *
     * @param target 저장할 대상 파일
     * @return 저장 성공 여부
     */
    public boolean saveTo(File target) {
        Objects.requireNonNull(target, "target");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
            w.write(getText());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "저장 실패: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        this.file = target;
        this.dirty = false;
        onUpdate.accept(this);
        return true;
    }

    // --- 편집 기능 ---

    public void undo() {
        try {
            if (undoManager.canUndo())
                undoManager.undo();
        } catch (CannotUndoException ignored) {
        }
        onUpdate.accept(this);
    }

    public void redo() {
        try {
            if (undoManager.canRedo())
                undoManager.redo();
        } catch (CannotRedoException ignored) {
        }
        onUpdate.accept(this);
    }

    public void adjustFont(int d) {
        baseFontSize = Math.max(8, Math.min(48, baseFontSize + d));
        setFont(getFont().deriveFont((float) baseFontSize));
        onUpdate.accept(this);
    }

    public void resetFont() {
        baseFontSize = 14;
        setFont(getFont().deriveFont((float) baseFontSize));
        onUpdate.accept(this);
    }

    public int getCaretLine() {
        int c = getCaretPosition();
        try {
            return getLineOfOffset(c) + 1;
        } catch (BadLocationException e) {
            return 1;
        }
    }

    public int getCaretCol() {
        int c = getCaretPosition();
        try {
            int l = getLineOfOffset(c);
            int s = getLineStartOffset(l);
            return c - s + 1;
        } catch (BadLocationException e) {
            return 1;
        }
    }

    // --- 원격 이벤트 반영 ---

    /**
     * 원격에서 수신된 텍스트를 에디터에 반영한다.
     * 이 과정에서 발생하는 변경 이벤트가 다시 서버로 전송되지 않도록 플래그를 설정한다.
     *
     * @param text 원격 텍스트 전체
     */
    public void applyRemoteText(String text) {
        suppressBroadcast = true;
        try {
            int caret = getCaretPosition();
            Point viewPos = null;
            if (getParent() instanceof JViewport) {
                viewPos = ((JViewport) getParent()).getViewPosition();
            }
            setText(text);
            try {
                setCaretPosition(Math.min(caret, getDocument().getLength()));
            } catch (Exception ignored) {
            }
            if (viewPos != null && getParent() instanceof JViewport) {
                ((JViewport) getParent()).setViewPosition(viewPos);
            }
            markDirty(true);
        } finally {
            suppressBroadcast = false;
        }
    }

    /**
     * 원격 사용자의 커서를 화면에 표시한다.
     *
     * @param nick  사용자 닉네임
     * @param dot   커서 위치
     * @param mark  선택 영역 시작점
     * @param color 커서 색상
     */
    public void updateRemoteCursor(String nick, int dot, int mark, Color color) {
        Highlighter hl = getHighlighter();
        RemoteCursor old = remoteCursors.get(nick);
        if (old != null && old.tag != null)
            hl.removeHighlight(old.tag);

        int len = getDocument().getLength();
        if (len == 0)
            return;
        dot = Math.min(Math.max(0, dot), len);
        mark = Math.min(Math.max(0, mark), len);

        try {
            Object tag = hl.addHighlight(Math.min(dot, mark), Math.max(dot, mark),
                    new DefaultHighlighter.DefaultHighlightPainter(color));
            remoteCursors.put(nick, new RemoteCursor(tag, dot, mark, color));
        } catch (BadLocationException ignored) {
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // 원격 커서 그리기 (세로선)
        g.setColor(Color.BLACK);
        for (RemoteCursor rc : remoteCursors.values()) {
            try {
                Rectangle r = modelToView(rc.dot);
                if (r != null) {
                    g.setColor(rc.color);
                    g.fillRect(r.x, r.y, 2, r.height);
                    g.drawString(" ", r.x, r.y - 2); // 라벨 공간
                }
            } catch (BadLocationException ignored) {
            }
        }
    }

    @Override
    public void paintChildren(Graphics g) {
        super.paintChildren(g);
        // 레이저 포인터 그리기
        if (remoteLaserPoint != null) {
            g.setColor(new Color(255, 0, 0, 180));
            g.fillOval(remoteLaserPoint.x - 5, remoteLaserPoint.y - 5, 10, 10);
            g.setColor(Color.WHITE);
            g.drawOval(remoteLaserPoint.x - 5, remoteLaserPoint.y - 5, 10, 10);
        }
    }

    public void updateRemoteLaser(int x, int y) {
        if (x < 0 || y < 0) {
            remoteLaserPoint = null;
        } else {
            remoteLaserPoint = new Point(x, y);
        }
        repaint();
    }

    private static class RemoteCursor {
        Object tag;
        int dot, mark;
        Color color;

        RemoteCursor(Object tag, int dot, int mark, Color color) {
            this.tag = tag;
            this.dot = dot;
            this.mark = mark;
            this.color = color;
        }
    }
}
