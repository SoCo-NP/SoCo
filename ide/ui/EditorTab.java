package ide.ui;

import ide.net.CollabClient;

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

public class EditorTab extends JTextArea {
    private File file;               // null이면 Untitled
    private boolean dirty = false;
    private final UndoManager undoManager = new UndoManager();
    private int baseFontSize = 14;
    private final java.util.Map<String, RemoteCursor> remoteCursors = new java.util.concurrent.ConcurrentHashMap<>();
    private Point remoteLaserPoint = null;

    private final CollabClient collab;
    private final BooleanSupplier isKeystrokeMode;
    private final Consumer<EditorTab> onUpdate;
    private boolean suppressBroadcast = false;
    private final String virtualPath; // untitled:UUID or file path
    private final javax.swing.Timer debounce;       // 200ms text
    private final javax.swing.Timer cursorDebounce; // 120ms caret

    // remote cursors
    private final Map<String, Object> cursorTags = new HashMap<>();

    public EditorTab(File file,
                     String text,
                     String providedVirtualPath,
                     CollabClient collab,
                     BooleanSupplier keystrokeMode,
                     Consumer<EditorTab> onUpdate) {
        super(text);
        this.file = file;
        this.collab = collab;
        this.isKeystrokeMode = keystrokeMode;
        this.onUpdate = onUpdate;
        this.virtualPath = (file != null) ? file.getAbsolutePath()
                : (providedVirtualPath != null ? providedVirtualPath : ("untitled:" + UUID.randomUUID()));

        setFont(new Font(Font.MONOSPACED, Font.PLAIN, baseFontSize));
        setTabSize(4); setLineWrap(false); setWrapStyleWord(false);
        setMargin(new Insets(8,8,8,8));

        // Dark Mode Theme
        setBackground(new Color(30, 30, 30)); // #1E1E1E
        setForeground(new Color(212, 212, 212)); // #D4D4D4
        setCaretColor(Color.WHITE);
        setSelectionColor(new Color(38, 79, 120)); // #264F78
        setSelectedTextColor(Color.WHITE);

        getDocument().addUndoableEditListener(e -> { undoManager.addEdit(e.getEdit()); markDirty(true); });
        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleBroadcast(); markDirty(true); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleBroadcast(); markDirty(true); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleBroadcast(); markDirty(true); }
        });

        addCaretListener(e -> { onUpdate.accept(EditorTab.this); scheduleCursorSend(); });

        // Ctrl/Cmd+S
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "save");
        getActionMap().put("save", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onUpdate.accept(EditorTab.this); }
        });

        // timers
        debounce = new javax.swing.Timer(200, e -> {
            if (!suppressBroadcast && collab.isConnected()) collab.sendSnapshot(getVirtualPath(), getText());
        });
        debounce.setRepeats(false);

        cursorDebounce = new javax.swing.Timer(120, e -> {
            if (!collab.isConnected()) return;
            collab.sendCursor(getVirtualPath(), getCaret().getDot(), getCaret().getMark());
        });
        cursorDebounce.setRepeats(false);
    }

    private void scheduleBroadcast() {
        if (suppressBroadcast) return;
        if (isKeystrokeMode.getAsBoolean()) {
            if (collab.isConnected()) collab.sendSnapshot(getVirtualPath(), getText());
        } else {
            debounce.restart();
        }
    }
    private void scheduleCursorSend() { cursorDebounce.restart(); }

    public File getFile() { return file; }
    public void setFile(File f) { this.file = f; this.dirty = false; onUpdate.accept(this); }
    public String getDisplayName() { return file != null ? file.getName() : "Untitled"; }
    public boolean isDirty() { return dirty; }
    public String getVirtualPath() { return (file != null) ? file.getAbsolutePath() : virtualPath; }

    public void markDirty(boolean d) { if (this.dirty != d) { this.dirty = d; } onUpdate.accept(this); }

    public boolean saveTo(File target) {
        Objects.requireNonNull(target, "target");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
            w.write(getText());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "저장 실패: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        this.file = target; this.dirty = false; onUpdate.accept(this); return true;
    }

    public void undo() { try { if (undoManager.canUndo()) undoManager.undo(); } catch (CannotUndoException ignored) {} onUpdate.accept(this); }
    public void redo() { try { if (undoManager.canRedo()) undoManager.redo(); } catch (CannotRedoException ignored) {} onUpdate.accept(this); }
    public void adjustFont(int d) { baseFontSize = Math.max(8, Math.min(48, baseFontSize + d)); setFont(getFont().deriveFont((float)baseFontSize)); onUpdate.accept(this); }
    public void resetFont() { baseFontSize = 14; setFont(getFont().deriveFont((float) baseFontSize)); onUpdate.accept(this); }
    public int getCaretLine() { int c = getCaretPosition(); try { return getLineOfOffset(c) + 1; } catch (BadLocationException e) { return 1; } }
    public int getCaretCol() { int c = getCaretPosition(); try { int l = getLineOfOffset(c); int s = getLineStartOffset(l); return c - s + 1; } catch (BadLocationException e) { return 1; } }

    // --- remote apply ---
    public void applyRemoteText(String text) {
        suppressBroadcast = true;
        try {
            int caret = getCaretPosition();
            Point viewPos = null;
            if (getParent() instanceof JViewport) {
                viewPos = ((JViewport) getParent()).getViewPosition();
            }
            setText(text);
            try { setCaretPosition(Math.min(caret, getDocument().getLength())); } catch (Exception ignored) {}
            if (viewPos != null && getParent() instanceof JViewport) {
                ((JViewport) getParent()).setViewPosition(viewPos);
            }
            markDirty(true);
        } finally { suppressBroadcast = false; }
    }

    public void updateRemoteCursor(String nick, int dot, int mark, Color color) {
        Highlighter hl = getHighlighter();
        RemoteCursor old = remoteCursors.get(nick);
        if (old != null && old.tag != null) hl.removeHighlight(old.tag);

        int len = getDocument().getLength();
        if (len == 0) return;
        dot = Math.min(Math.max(0, dot), len);
        mark = Math.min(Math.max(0, mark), len);

        try {
            Object tag = hl.addHighlight(Math.min(dot, mark), Math.max(dot, mark),
                    new DefaultHighlighter.DefaultHighlightPainter(color));
            remoteCursors.put(nick, new RemoteCursor(tag, dot, mark, color));
        } catch (BadLocationException ignored) {}
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw remote cursors (vertical lines)
        g.setColor(Color.BLACK);
        for (RemoteCursor rc : remoteCursors.values()) {
            try {
                Rectangle r = modelToView(rc.dot);
                if (r != null) {
                    g.setColor(rc.color.darker());
                    g.fillRect(r.x, r.y, 2, r.height);
                    g.drawString(" ", r.x, r.y - 2); // simple label placeholder
                }
            } catch (BadLocationException ignored) {}
        }
    }

    @Override public void paintChildren(Graphics g) {
        super.paintChildren(g);
        // Draw Laser
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
            this.tag = tag; this.dot = dot; this.mark = mark; this.color = color;
        }
    }
}
