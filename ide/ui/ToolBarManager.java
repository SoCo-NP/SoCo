package ide.ui;

import ide.client.CollabIDE;

import javax.swing.*;

public class ToolBarManager {

    private JToggleButton btnFollowMe;
    private JToggleButton btnLaser;
    private JToggleButton btnAttendance;

    public JToolBar createToolBar(CollabIDE ide) {
        JToolBar tb = new JToolBar();
        JButton btnOpen = new JButton("Open Folder");
        btnOpen.addActionListener(e -> ide.chooseAndOpenProjectFolder());
        JButton btnSave = new JButton("Save");
        btnSave.addActionListener(e -> ide.actionSaveActive());
        JButton btnConnect = new JButton("Connect");
        btnConnect.addActionListener(e -> ide.promptConnect());

        btnFollowMe = new JToggleButton("Follow Me");
        btnFollowMe.setVisible(false); // Hidden by default
        btnFollowMe.addActionListener(e -> {
            ide.setFollowMeActive(btnFollowMe.isSelected());
            if (btnFollowMe.isSelected()) {
                ide.sendViewportNow();
            }
        });

        btnLaser = new JToggleButton("Laser");
        btnLaser.setVisible(false);
        btnLaser.addActionListener(e -> {
            ide.setLaserActive(btnLaser.isSelected());
            ide.updateLaserState();
        });

        btnAttendance = new JToggleButton("Attendance");
        btnAttendance.setVisible(false);
        btnAttendance.addActionListener(e -> {
            if (btnAttendance.isSelected()) {
                ide.showAttendanceDialog();
            } else {
                ide.hideAttendanceDialog();
            }
        });

        tb.add(btnOpen);
        tb.add(btnSave);
        tb.addSeparator();
        tb.add(btnConnect);
        tb.addSeparator();
        tb.add(btnFollowMe);
        tb.add(btnLaser);
        tb.add(btnAttendance);
        return tb;
    }

    public JToggleButton getBtnFollowMe() {
        return btnFollowMe;
    }

    public JToggleButton getBtnLaser() {
        return btnLaser;
    }

    public JToggleButton getBtnAttendance() {
        return btnAttendance;
    }
}
