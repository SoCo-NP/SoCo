package ide.ui;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;

public class LineNumberView extends JComponent implements DocumentListener, CaretListener {
    private static final int MARGIN = 8;
    private final JTextArea textArea;
    private int lastDigits = 2;

    public LineNumberView(JTextArea area) {
        this.textArea = area;
        area.getDocument().addDocumentListener(this);
        area.addCaretListener(this);
        area.addCaretListener(this);
        setFont(area.getFont());
        
        // Dark Mode Theme
        setBackground(new Color(30, 30, 30)); // #1E1E1E
        setForeground(new Color(133, 133, 133)); // #858585
        
        setBorder(BorderFactory.createEmptyBorder(0,0,0,MARGIN));
    }

    @Override public Dimension getPreferredSize() {
        int lines = Math.max(1, textArea.getLineCount());
        int digits = Integer.toString(lines).length();
        if (digits != lastDigits) lastDigits = digits;
        FontMetrics fm = getFontMetrics(getFont());
        int width = MARGIN + fm.charWidth('0') * (lastDigits + 1);
        return new Dimension(width, textArea.getHeight());
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Rectangle clip = g.getClipBounds();
        FontMetrics fm = getFontMetrics(getFont());
        int ascent = fm.getAscent();
        int fontHeight = fm.getHeight();
        int startOffset = textArea.viewToModel(new Point(0, clip.y));
        int endOffset   = textArea.viewToModel(new Point(0, clip.y + clip.height));
        try {
            int startLine = textArea.getLineOfOffset(startOffset);
            int endLine   = textArea.getLineOfOffset(endOffset);
            for (int line = startLine; line <= endLine; line++) {
                Rectangle r = textArea.modelToView(textArea.getLineStartOffset(line));
                int y = r.y + r.height - (fontHeight - ascent);
                String num = Integer.toString(line + 1);
                int x = getPreferredSize().width - MARGIN;
                g.drawString(num, x - fm.stringWidth(num), y);
            }
        } catch (BadLocationException ignored) {}
    }
    @Override public void insertUpdate(DocumentEvent e) { revalidate(); repaint(); }
    @Override public void removeUpdate(DocumentEvent e) { revalidate(); repaint(); }
    @Override public void changedUpdate(DocumentEvent e) { revalidate(); repaint(); }
    @Override public void caretUpdate(CaretEvent e) { repaint(); }
}
