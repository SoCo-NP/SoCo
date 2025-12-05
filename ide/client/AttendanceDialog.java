package ide.client;

import javax.swing.*;
import java.awt.*;

public class AttendanceDialog extends JDialog {
    public AttendanceDialog(Frame owner) {
        super(owner, "Attendance", false);
        setSize(300, 400);
        setLocationRelativeTo(owner);
        add(new JLabel("Attendance feature coming soon!"), BorderLayout.CENTER);
    }
}
