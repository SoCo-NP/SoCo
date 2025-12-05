package ide.ui;

import java.awt.Color;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/**
 * IDE의 UI 테마 색상을 정의하는 클래스.
 *
 * 에디터 배경, 글자색, 선택 영역, 줄 번호 등 주요 UI 요소의 색상 상수를 관리한다.
 * 현재는 다크 모드 스타일의 색상 팔레트를 제공한다.
 *
 */
public class Theme {

    // 에디터 색상 (다크 모드)
    public static final Color EDITOR_BG = Color.WHITE; // 배경색 (현재는 화이트로 설정됨)
    public static final Color EDITOR_FG = Color.BLACK; // 글자색
    public static final Color EDITOR_SELECTION = new Color(173, 214, 255); // 선택 영역 배경색 (연한 파랑)
    public static final Color EDITOR_SELECTION_FG = Color.BLACK; // 선택 영역 글자색
    public static final Color EDITOR_CARET = Color.BLACK; // 커서 색상

    // 줄 번호 영역 색상
    public static final Color EDITOR_LINE_NUMBER_BG = new Color(245, 245, 245); // 배경색 (연한 회색)
    public static final Color EDITOR_LINE_NUMBER_FG = new Color(100, 100, 100); // 글자색 (진한 회색)

    // 상태 표시줄 (선택 사항)
    public static final Color STATUS_BAR_BG = new Color(212, 212, 212);

    /**
     * 전역 UI 설정을 적용한다.
     * 현재는 시스템 기본 LookAndFeel을 사용하므로 별도의 오버라이드는 없다.
     */
    public static void apply() {
        // No global UIManager overrides - use System LookAndFeel (Light Mode)
    }
}
