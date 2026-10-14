package divan.web;

import android.widget.EditText;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Typing: while an EditText has focus, a real browser field sits exactly over it (same font, size, colour and
 * direction), so the phone's keyboard, Persian input, paste and autocorrect all work; every change goes back to
 * the EditText as typed text.
 */
public final class Inputs {
    private Inputs() {}

    @JSFunctor interface Changed extends JSObject { void call(String value); }
    @JSFunctor interface Action extends JSObject { void call(); }

    @JSBody(params = {"multi", "type", "value", "hint", "changed", "action", "blurred"}, script = "DH.field.open(multi, type, value, hint, changed, action, blurred);")
    private static native void open(boolean multi, String type, String value, String hint, Changed changed, Action action, Action blurred);

    @JSBody(script = "DH.field.close();")
    private static native void close();

    @JSBody(params = "v", script = "DH.field.value(v);")
    private static native void setValue(String v);

    @JSBody(params = {"s", "e"}, script = "DH.field.select(s, e);")
    private static native void select(int s, int e);

    @JSBody(params = {"x", "y", "w", "h", "font", "color", "hintColor", "align", "rtl", "padTop", "lineH"},
        script = "DH.field.place(x, y, w, h, font, color, hintColor, align, rtl, padTop, lineH);")
    private static native void place0(float x, float y, float w, float h, String font, String color, String hintColor, String align, boolean rtl, float padTop, float lineH);

    private static EditText current;

    public static boolean anyShowing() { return current != null; }

    public static boolean isShowing(EditText e) { return current == e; }

    public static void focus(EditText e) {
        if (current == e || !e.isAttachedToWindow()) return;
        if (current != null) blur(current);
        current = e;
        int t = e.getInputType();
        int cls = t & android.text.InputType.TYPE_MASK_CLASS;
        String type = cls == android.text.InputType.TYPE_CLASS_NUMBER || cls == android.text.InputType.TYPE_CLASS_PHONE ? "number"
            : (t & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD && cls == android.text.InputType.TYPE_CLASS_TEXT ? "password" : "text";
        CharSequence hint = e.getHint();
        open(!e.fieldSingleLine(), type, e.getText().toString(), hint == null ? "" : hint.toString(),
            v -> android.os.Looper.dispatch(() -> { if (current == e) e.onBrowserInput(v); }),
            () -> android.os.Looper.dispatch(() -> { if (current == e) e.onBrowserEditorAction(); }),
            () -> android.os.Looper.dispatch(() -> { if (current == e) { current = null; close(); e.clearFocus(); e.invalidate(); } }));
        if (e.takeSelectAll()) select(0, e.length()); else select(e.getSelectionStart(), e.getSelectionEnd());
        e.invalidate();
        WebWindow.schedule();
    }

    public static void blur(EditText e) {
        if (current != e) return;
        current = null;
        close();
        e.invalidate();
    }

    public static void blurAll() { if (current != null) { EditText e = current; blur(e); e.clearFocus(); } }

    public static void syncFrom(EditText e) { if (current == e) setValue(e.getText().toString()); }

    public static void syncSelection(EditText e) {
        if (current != e) return;
        if (e.takeSelectAll()) select(0, e.length()); else select(e.getSelectionStart(), e.getSelectionEnd());
    }

    private static String css(int c) {
        return "rgba(" + ((c >> 16) & 255) + "," + ((c >> 8) & 255) + "," + (c & 255) + "," + (((c >>> 24) & 255) / 255f) + ")";
    }

    /** Keeps the browser field over the EditText after every frame (scrolling, animations, keyboard). */
    static void place() {
        EditText e = current;
        if (e == null) return;
        if (!e.isAttachedToWindow() || !e.isShown()) { blurAll(); return; }
        int[] loc = new int[2];
        e.getLocationInWindow(loc);
        float d = 1f / android.content.res.Resources.INSTANCE.getDisplayMetrics().density;
        float x = loc[0] + e.getCompoundPaddingLeft(), y = loc[1] + e.getCompoundPaddingTop();
        float w = e.getWidth() - e.getCompoundPaddingLeft() - e.getCompoundPaddingRight();
        float h = e.getHeight() - e.getCompoundPaddingTop() - e.getCompoundPaddingBottom();
        android.graphics.Paint p = e.fieldPaint();
        int hg = android.view.Gravity.getAbsoluteGravity(e.fieldGravity(), e.getLayoutDirection()) & android.view.Gravity.HORIZONTAL_GRAVITY_MASK;
        boolean rtl = e.fieldRtl();
        String align = (e.fieldGravity() & android.view.Gravity.HORIZONTAL_GRAVITY_MASK) == android.view.Gravity.CENTER_HORIZONTAL ? "center"
            : hg == android.view.Gravity.RIGHT ? "right" : hg == android.view.Gravity.LEFT ? "left" : (rtl ? "right" : "left");
        String font = ((p.getTypeface() != null && p.getTypeface().isBold()) ? "bold " : "") + (p.getTextSize() * d) + "px Vazir, Tahoma, sans-serif";
        place0(x * d, y * d, w * d, h * d, font, css(e.fieldTextColor()), css(e.fieldHintColor()), align, rtl, e.fieldVerticalOffset() * d, e.fieldLineHeight() * d);
    }
}
