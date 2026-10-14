package android.view;

import android.graphics.Insets;

/** Window insets: system bars from the browser's safe area, and the on-screen keyboard (ime). */
public final class WindowInsets {
    public static final class Type {
        public static int statusBars() { return 1; }
        public static int navigationBars() { return 2; }
        public static int captionBar() { return 4; }
        public static int ime() { return 8; }
        public static int systemGestures() { return 16; }
        public static int mandatorySystemGestures() { return 32; }
        public static int tappableElement() { return 64; }
        public static int displayCutout() { return 128; }
        public static int systemBars() { return statusBars() | navigationBars() | captionBar(); }
    }

    private static WindowInsets current = new WindowInsets(Insets.NONE, Insets.NONE);

    private final Insets bars;
    private final Insets ime;
    private boolean consumed;

    public WindowInsets(Insets bars, Insets ime) { this.bars = bars; this.ime = ime; }

    public static WindowInsets current() { return current; }

    public static void setCurrent(WindowInsets insets) { current = insets; }

    public Insets getInsets(int typeMask) {
        Insets r = Insets.NONE;
        if ((typeMask & (Type.systemBars() | Type.displayCutout() | Type.tappableElement())) != 0) r = Insets.max(r, bars);
        if ((typeMask & Type.ime()) != 0) r = Insets.max(r, ime);
        return r;
    }

    public Insets getInsetsIgnoringVisibility(int typeMask) { return getInsets(typeMask); }
    public boolean isVisible(int typeMask) { return (typeMask & Type.ime()) == 0 || ime.bottom > 0; }
    public int getSystemWindowInsetLeft() { return bars.left; }
    public int getSystemWindowInsetTop() { return bars.top; }
    public int getSystemWindowInsetRight() { return bars.right; }
    public int getSystemWindowInsetBottom() { return Math.max(bars.bottom, ime.bottom); }
    public Insets getSystemWindowInsets() { return Insets.of(bars.left, bars.top, bars.right, getSystemWindowInsetBottom()); }
    public int getStableInsetBottom() { return bars.bottom; }
    public boolean hasSystemWindowInsets() { return bars != Insets.NONE || ime != Insets.NONE; }
    public boolean isConsumed() { return consumed; }
    public WindowInsets consumeSystemWindowInsets() { WindowInsets w = new WindowInsets(Insets.NONE, Insets.NONE); w.consumed = true; return w; }
    public static final WindowInsets CONSUMED = new WindowInsets(Insets.NONE, Insets.NONE);
    static { CONSUMED.consumed = true; }
}
