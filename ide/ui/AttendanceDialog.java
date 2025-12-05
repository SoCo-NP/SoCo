package ide.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Set;
import ide.app.CollabActions;

/**
 * 출석 체크 기능을 담당하는 다이얼로그 클래스.
 *
 * 등록된 학생 명단을 표시하고, 접속 상태에 따라 색상과 아이콘으로 시각화한다.
 * 학생이 접속하면 닉네임을 명단과 매칭하여 상태를 실시간으로 업데이트한다.
 */
public class AttendanceDialog extends JDialog {

    // 등록된 학생 명단 (추후 파일로 외부화 가능)
    private static final String[] STUDENT_ROSTER = {
            "유상완", "송승윤", "허현", "노수민", "신성",
            "장재영", "김성동", "황기태", "안영아", "박지성"
    };

    private final CollabActions collabActions;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;

    /**
     * AttendanceDialog 생성자.
     *
     * @param owner         다이얼로그의 부모 프레임
     * @param collabActions 접속 중인 사용자 정보를 조회하기 위한 인터페이스
     */
    public AttendanceDialog(Frame owner, CollabActions collabActions) {
        super(owner, "📋 출석부 (Attendance)", false);
        this.collabActions = collabActions;

        // 테이블 모델 설정
        tableModel = new DefaultTableModel(new String[] { "번호", "학생 이름", "상태" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(40);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        // 컬럼 너비 설정
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setMaxWidth(100);

        // 번호 컬럼 가운데 정렬
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        // 상태 컬럼에 커스텀 렌더러 적용
        table.getColumnModel().getColumn(2).setCellRenderer(new StatusRenderer());

        // 헤더 스타일링
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 14));
        table.getTableHeader().setBackground(new Color(70, 130, 180));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));

        // UI 구성
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // 상태 표시 패널
        statusLabel = new JLabel("총 " + STUDENT_ROSTER.length + "명");
        statusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(245, 245, 245));
        topPanel.add(statusLabel, BorderLayout.WEST);

        // 버튼 패널
        JButton refreshButton = new JButton("🔄 새로고침");
        refreshButton.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        refreshButton.setFocusPainted(false);
        refreshButton.addActionListener(e -> refreshAttendance());

        JButton closeButton = new JButton("닫기");
        closeButton.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);

        // 범례 패널
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        legendPanel.setBackground(Color.WHITE);
        legendPanel.add(createLegendLabel("● 출석", new Color(76, 175, 80)));
        legendPanel.add(createLegendLabel("● 결석", new Color(244, 67, 54)));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(legendPanel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        setLayout(new BorderLayout(0, 0));
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(500, 600);
        setLocationRelativeTo(owner);

        // 배경색 설정
        getContentPane().setBackground(Color.WHITE);

        // 모든 UI 초기화가 완료된 후 데이터 로드
        refreshAttendance();
    }

    /**
     * 범례 라벨을 생성한다.
     */
    private JLabel createLegendLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        label.setForeground(color);
        return label;
    }

    /**
     * 출석 상태를 새로고침한다.
     * 접속 중인 학생 목록을 가져와서 명단과 매칭한다.
     */
    private void refreshAttendance() {
        Set<String> connectedStudents = collabActions.getConnectedStudents();

        // 디버그: 접속 중인 학생 출력
        System.out.println("[출석부] 접속 중인 학생: " + connectedStudents);
        System.out.println("[출석부] 학생 수: " + connectedStudents.size());

        // 테이블 초기화
        tableModel.setRowCount(0);

        int presentCount = 0;

        // 각 학생에 대해 상태 확인
        for (int i = 0; i < STUDENT_ROSTER.length; i++) {
            String studentName = STUDENT_ROSTER[i];
            boolean isConnected = connectedStudents.contains(studentName);
            if (isConnected)
                presentCount++;

            System.out.println("[출석부] " + studentName + " -> " + (isConnected ? "출석" : "결석"));

            tableModel.addRow(new Object[] {
                    (i + 1),
                    studentName,
                    isConnected ? "출석" : "결석"
            });
        }

        // 상태 업데이트
        statusLabel.setText(String.format("총 %d명  |  출석: %d명  |  결석: %d명",
                STUDENT_ROSTER.length, presentCount, STUDENT_ROSTER.length - presentCount));
    }

    /**
     * 상태 컬럼을 색상으로 표시하는 커스텀 렌더러.
     */
    private static class StatusRenderer extends DefaultTableCellRenderer {
        private static final Color PRESENT_COLOR = new Color(76, 175, 80); // Green
        private static final Color ABSENT_COLOR = new Color(244, 67, 54); // Red
        private static final Color PRESENT_BG = new Color(232, 245, 233); // Light green
        private static final Color ABSENT_BG = new Color(255, 235, 238); // Light red

        public StatusRenderer() {
            setHorizontalAlignment(CENTER);
            setOpaque(true);
            setFont(new Font("맑은 고딕", Font.BOLD, 13));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {
            String status = value != null ? value.toString() : "";
            setText("● " + status);

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                boolean isPresent = "출석".equals(status);
                setForeground(isPresent ? PRESENT_COLOR : ABSENT_COLOR);
                setBackground(isPresent ? PRESENT_BG : ABSENT_BG);
            }

            setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

            return this;
        }
    }
}
