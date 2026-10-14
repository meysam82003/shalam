package android.widget;

import android.content.Context;

/** Short message at the bottom of the screen; shown by the browser window (same look as the system toast). */
public class Toast {
    public static final int LENGTH_SHORT = 0, LENGTH_LONG = 1;
    private CharSequence text;
    private int duration;

    public Toast(Context context) {}

    public static Toast makeText(Context context, CharSequence text, int duration) {
        Toast t = new Toast(context);
        t.text = text;
        t.duration = duration;
        return t;
    }

    public void show() { divan.web.WebToast.show(text == null ? "" : text.toString(), duration == LENGTH_LONG ? 3500 : 2000); }
    public void cancel() { divan.web.WebToast.hide(); }
    public void setText(CharSequence s) { text = s; }
    public void setDuration(int duration) { this.duration = duration; }
    public void setGravity(int gravity, int xOffset, int yOffset) {}
}
