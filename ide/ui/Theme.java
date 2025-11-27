package ide.ui;

import java.awt.Color;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

public class Theme {
    // Light Mode Theme (For Editor)
    public static final Color EDITOR_BG = Color.WHITE;                      // White Background
    public static final Color EDITOR_FG = Color.BLACK;                      // Black Text
    public static final Color EDITOR_SELECTION = new Color(173, 214, 255);  // Light Blue Selection
    public static final Color EDITOR_SELECTION_FG = Color.BLACK;
    public static final Color EDITOR_CARET = Color.BLACK;                   // Black Caret
    public static final Color EDITOR_LINE_NUMBER_BG = new Color(245, 245, 245); // Very Light Grey
    public static final Color EDITOR_LINE_NUMBER_FG = new Color(100, 100, 100); // Dark Grey Line Numbers
    
    // Status Bar (Optional, can keep colored or make it light)
    // Let's keep a default status bar color if needed, or just use null
    public static final Color STATUS_BAR_BG = new Color(212, 212, 212); // Keep for reference if needed

    public static void apply() {
        // No global UIManager overrides - use System LookAndFeel (Light Mode)
    }
}
