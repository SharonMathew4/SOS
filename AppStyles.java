import java.awt.*;

public class AppStyles {
    // Colors
    public static final Color DARK_RED = new Color(220, 53, 69);
    public static final Color BACKGROUND_COLOR = new Color(246, 247, 250);
    public static final Color SELECTED_BACKGROUND_COLOR = new Color(230, 244, 255);
    public static final Color ACCENT_COLOR = new Color(29, 155, 240);
    public static final Color SECONDARY_TEXT_COLOR = new Color(108, 117, 125);
    public static final Color SIDEBAR_COLOR = new Color(24, 28, 36);
    public static final Color SIDEBAR_HOVER = new Color(40, 44, 52);
    public static final Color TEXT_COLOR = new Color(230, 230, 230);

    private static Font appFont = null;

    public static void setAppFont(Font font) {
        appFont = font;
    }

    public static Font getAppFont(int style, float size) {
        if (appFont != null) {
            return appFont.deriveFont(style, size);
        }
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }
}