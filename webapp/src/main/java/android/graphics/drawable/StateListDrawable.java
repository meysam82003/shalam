package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;

/** First matching state set wins, as in Android (an empty set matches every state). */
public class StateListDrawable extends Drawable {
    private final java.util.ArrayList<int[]> sets = new java.util.ArrayList<>();
    private final java.util.ArrayList<Drawable> drawables = new java.util.ArrayList<>();
    private Drawable current;
    private int alpha = 255;

    public void addState(int[] stateSet, Drawable drawable) {
        sets.add(stateSet);
        drawables.add(drawable);
        if (current == null) select(getState());
    }

    private static boolean matches(int[] set, int[] state) {
        for (int s : set) {
            boolean want = s > 0;
            int id = Math.abs(s);
            boolean has = false;
            for (int t : state) if (t == id) { has = true; break; }
            if (want != has) return false;
        }
        return true;
    }

    private boolean select(int[] state) {
        Drawable next = null;
        for (int i = 0; i < sets.size(); i++) if (matches(sets.get(i), state)) { next = drawables.get(i); break; }
        if (next == current) return false;
        current = next;
        if (current != null) { current.setBounds(getBounds()); current.setAlpha(alpha); current.setState(state); }
        invalidateSelf();
        return true;
    }

    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] state) { boolean changed = select(state); if (current != null) changed |= current.setState(state); return changed; }
    @Override protected void onBoundsChange(Rect b) { if (current != null) current.setBounds(b); }
    @Override public Drawable getCurrent() { return current != null ? current : this; }
    @Override public void draw(Canvas canvas) { if (current != null) current.draw(canvas); }
    @Override public void setAlpha(int a) { alpha = a; if (current != null) current.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public boolean getPadding(Rect padding) { return current != null ? current.getPadding(padding) : super.getPadding(padding); }
}
