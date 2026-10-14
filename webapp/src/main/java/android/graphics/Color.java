package android.graphics;

/** Colour helpers of android.graphics.Color used by the app (same integer ARGB format). */
public class Color {
    public static final int BLACK = 0xFF000000;
    public static final int DKGRAY = 0xFF444444;
    public static final int GRAY = 0xFF888888;
    public static final int LTGRAY = 0xFFCCCCCC;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static final int YELLOW = 0xFFFFFF00;
    public static final int CYAN = 0xFF00FFFF;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int TRANSPARENT = 0;

    public static int alpha(int color) { return color >>> 24; }
    public static int red(int color) { return (color >> 16) & 0xFF; }
    public static int green(int color) { return (color >> 8) & 0xFF; }
    public static int blue(int color) { return color & 0xFF; }
    public static int rgb(int red, int green, int blue) { return 0xff000000 | (red << 16) | (green << 8) | blue; }
    public static int argb(int alpha, int red, int green, int blue) { return (alpha << 24) | (red << 16) | (green << 8) | blue; }

    /** Same accepted forms as Android: #RRGGBB, #AARRGGBB and the named colours. */
    public static int parseColor(String colorString) {
        if (colorString.charAt(0) == '#') {
            long color = Long.parseLong(colorString.substring(1), 16);
            if (colorString.length() == 7) {
                color |= 0x00000000ff000000L;
            } else if (colorString.length() != 9) {
                throw new IllegalArgumentException("Unknown color");
            }
            return (int) color;
        }
        switch (colorString.toLowerCase(java.util.Locale.ROOT)) {
            case "black": return BLACK;
            case "darkgray": return DKGRAY;
            case "gray": case "grey": return GRAY;
            case "lightgray": case "lightgrey": return LTGRAY;
            case "white": return WHITE;
            case "red": return RED;
            case "green": return GREEN;
            case "blue": return BLUE;
            case "yellow": return YELLOW;
            case "cyan": case "aqua": return CYAN;
            case "magenta": case "fuchsia": return MAGENTA;
            case "lime": return 0xFF00FF00;
            case "maroon": return 0xFF800000;
            case "navy": return 0xFF000080;
            case "olive": return 0xFF808000;
            case "purple": return 0xFF800080;
            case "silver": return 0xFFC0C0C0;
            case "teal": return 0xFF008080;
            case "transparent": return TRANSPARENT;
            default: throw new IllegalArgumentException("Unknown color");
        }
    }

    /** CSS form of an ARGB colour, used by the browser canvas. */
    public static String css(int color) {
        int a = color >>> 24;
        if (a == 255) return "rgb(" + ((color >> 16) & 0xFF) + "," + ((color >> 8) & 0xFF) + "," + (color & 0xFF) + ")";
        return "rgba(" + ((color >> 16) & 0xFF) + "," + ((color >> 8) & 0xFF) + "," + (color & 0xFF) + "," + (a / 255f) + ")";
    }
}
