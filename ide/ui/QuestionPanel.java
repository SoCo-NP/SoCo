package ide.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 학생들의 질문을 표시하는 패널 (교수자 전용).
 * 
 * 질문 목록을 테이블 형태로 표시하며, 시간, 학생 이름, 질문 내용을 보여준다.
 */
public class QuestionPanel extends JPanel {
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * QuestionPanel 생성자.
     */
    public QuestionPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("💬 학생 질문"));

        // 테이블 모델
        tableModel = new DefaultTableModel(
                new String[] { "시간", "학생", "질문 내용" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setMaxWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 하단 버튼 패널
        JButton clearButton = new JButton("전체 삭제");
        clearButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        clearButton.addActionListener(e -> tableModel.setRowCount(0));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * 새로운 질문을 목록에 추가한다.
     * 최신 질문이 맨 위에 표시되도록 한다.
     *
     * @param studentNick  질문한 학생의 닉네임
     * @param questionText 질문 내용
     */
    public void addQuestion(String studentNick, String questionText) {
        SwingUtilities.invokeLater(() -> {
            String time = timeFormat.format(new Date());
            tableModel.insertRow(0, new Object[] { time, studentNick, questionText });
        });
    }
}
