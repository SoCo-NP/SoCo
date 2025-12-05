package ide.ui;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;

/**
 * 에디터 좌측에 줄 번호를 표시하는 컴포넌트 클래스.
 * <p>
 * JTextArea와 연동되어 텍스트의 줄 수에 맞춰 자동으로 번호를 렌더링한다.
 * 텍스트 변경이나 커서 이동 시 화면을 갱신하여 항상 최신 줄 번호를 유지한다.
 * </p>
 */
public class LineNumberView extends JComponent implements DocumentListener, CaretListener {
    private static final int MARGIN = 8;
    private final JTextArea textArea;
    private int lastDigits = 2; // 마지막으로 계산된 자릿수 (최적화용)

    /**
     * LineNumberView 생성자.
     *
     * @param area 줄 번호를 표시할 대상 JTextArea
     */
    public LineNumberView(JTextArea area) {
        this.textArea = area;
        area.getDocument().addDocumentListener(this);
        area.addCaretListener(this);
        setFont(area.getFont());

        // 테마 적용 (다크 모드)
        setBackground(ide.ui.Theme.EDITOR_LINE_NUMBER_BG);
        setForeground(ide.ui.Theme.EDITOR_LINE_NUMBER_FG);

        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, MARGIN));
    }

    /**
     * 컴포넌트의 권장 크기를 계산한다.
     * 줄 번호의 자릿수에 따라 너비가 가변적으로 조정된다.
     */
    @Override
    public Dimension getPreferredSize() {
        int lines = Math.max(1, textArea.getLineCount());
        int digits = Integer.toString(lines).length();
        if (digits != lastDigits)
            lastDigits = digits;
        FontMetrics fm = getFontMetrics(getFont());
        int width = MARGIN + fm.charWidth('0') * (lastDigits + 1);
        return new Dimension(width, textArea.getHeight());
    }

    /**
     * 줄 번호를 그린다.
     * 현재 화면(Viewport)에 보이는 영역에 대해서만 번호를 렌더링하여 성능을 최적화한다.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Rectangle clip = g.getClipBounds();
        FontMetrics fm = getFontMetrics(getFont());
        int ascent = fm.getAscent();
        int fontHeight = fm.getHeight();

        // 보이는 영역의 시작과 끝 오프셋 계산
        int startOffset = textArea.viewToModel(new Point(0, clip.y));
        int endOffset = textArea.viewToModel(new Point(0, clip.y + clip.height));

        try {
            int startLine = textArea.getLineOfOffset(startOffset);
            int endLine = textArea.getLineOfOffset(endOffset);

            for (int line = startLine; line <= endLine; line++) {
                Rectangle r = textArea.modelToView(textArea.getLineStartOffset(line));
                int y = r.y + r.height - (fontHeight - ascent);
                String num = Integer.toString(line + 1);
                int x = getPreferredSize().width - MARGIN;
                g.drawString(num, x - fm.stringWidth(num), y); // 오른쪽 정렬
            }
        } catch (BadLocationException ignored) {
        }
    }

    // --- 이벤트 리스너 (화면 갱신 트리거) ---
    @Override
    public void insertUpdate(DocumentEvent e) {
        revalidate();
        repaint();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        revalidate();
        repaint();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        revalidate();
        repaint();
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        repaint();
    }
}
