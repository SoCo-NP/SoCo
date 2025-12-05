package ide.ui;

import javax.swing.*;
import java.awt.*;

/**
 * 출석 체크 기능을 담당하는 다이얼로그 클래스.
 * <p>
 * 현재는 기능 구현 예정 상태이며, 추후 교수님과 학생 간의 출석 확인 기능을 제공할 예정이다.
 * </p>
 */
public class AttendanceDialog extends JDialog {

    /**
     * AttendanceDialog 생성자.
     *
     * @param owner 다이얼로그의 부모 프레임
     */
    public AttendanceDialog(Frame owner) {
        super(owner, "Attendance", false);
        setSize(300, 400);
        setLocationRelativeTo(owner);
        add(new JLabel("Attendance feature coming soon!"), BorderLayout.CENTER);
    }
}
