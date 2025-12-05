package ide.app;

import ide.domain.Role;
import ide.ui.TabManager;
import ide.ui.ToolBarManager;

import javax.swing.*;
import java.awt.*;

/**
 * UI 계층 간 조율을 담당하는 클래스.
 * 
 * 상태바 업데이트, 콘솔 로그 등의 UI 관련 작업을 수행한다.
 */
public class UICoordinator {
    private final JFrame frame;
    private final JTextArea console;
    private final JLabel statusLabel;

    /**
     * UICoordinator 생성자.
     *
     * @param frame          메인 프레임
     * @param tabManager     탭 매니저 (미사용, 향후 확장용)
     * @param toolBarManager 툴바 매니저 (미사용, 향후 확장용)
     * @param console        콘솔 텍스트 영역
     * @param statusLabel    상태 라벨
     */
    public UICoordinator(JFrame frame, TabManager tabManager, ToolBarManager toolBarManager,
            JTextArea console, JLabel statusLabel) {
        this.frame = frame;
        this.console = console;
        this.statusLabel = statusLabel;
    }

    /**
     * 상태바를 업데이트한다.
     *
     * @param status 상태 메시지
     */
    public void updateStatusLabel(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    /**
     * 콘솔에 로그 메시지를 출력한다.
     *
     * @param message 로그 메시지
     */
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            console.append(message + "\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    /**
     * 에러 다이얼로그를 표시한다.
     *
     * @param message 에러 메시지
     */
    public void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    /**
     * Role에 따라 UI 테마를 업데이트한다.
     *
     * @param role 사용자 역할
     */
    public void updateThemeForRole(Role role) {
        SwingUtilities.invokeLater(() -> {
            // 간단한 테마 변경 (색상만)
            Color bg, fg;
            if (role == Role.PROFESSOR) {
                bg = new Color(240, 248, 255); // AliceBlue
                fg = Color.BLACK;
            } else {
                bg = new Color(255, 250, 240); // FloralWhite
                fg = Color.BLACK;
            }

            console.setBackground(bg);
            console.setForeground(fg);

            log("테마가 " + role + " 모드로 변경되었습니다.");
        });
    }
}
