package ide.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 학생들의 질문을 표시하는 다이얼로그 (교수자 전용).
 * 
 * 질문 목록을 테이블 형태로 표시하며, 시간, 학생 이름, 질문 내용을 보여준다.
 */
public class QuestionDialog extends JDialog {
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * QuestionDialog 생성자.
     *
     * @param parent 부모 프레임
     */
    public QuestionDialog(Frame parent) {
        super(parent, "💬 학생 질문", false); // non-modal

        setSize(700, 500);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // 테이블 모델
        tableModel = new DefaultTableModel(
                new String[] { "시간", "학생", "질문 내용" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(35);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setMaxWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(500);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 하단 버튼 패널
        JButton clearButton = new JButton("전체 삭제");
        clearButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        clearButton.addActionListener(e -> tableModel.setRowCount(0));

        JButton closeButton = new JButton("닫기");
        closeButton.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        closeButton.addActionListener(e -> setVisible(false));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearButton);
        buttonPanel.add(closeButton);
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

            // 다이얼로그가 숨겨져 있으면 자동으로 표시
            if (!isVisible()) {
                setVisible(true);
                toFront(); // 앞으로 가져오기
            }
        });
    }
}
