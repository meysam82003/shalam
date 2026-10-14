package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;

/** Base drawable with Android's bounds, state, alpha and callback contract. */
public abstract class Drawable {
    public interface Callback {
        void invalidateDrawable(Drawable who);
        void scheduleDrawable(Drawable who, Runnable what, long when);
        void unscheduleDrawable(Drawable who, Runnable what);
    }

    private static final Rect ZERO = new Rect();
    private static final int[] EMPTY_STATE = new int[0];

    private Rect bounds = ZERO;
    private int[] state = EMPTY_STATE;
    private java.lang.ref.WeakReference<Callback> callback;
    private int level;
    private boolean visible = true;

    public Drawable() {}

    public abstract void draw(Canvas canvas);

    public void setBounds(int left, int top, int right, int bottom) {
        Rect old = bounds;
        if (old == ZERO) old = bounds = new Rect();
        if (old.left != left || old.top != top || old.right != right || old.bottom != bottom) {
            if (!old.isEmpty()) invalidateSelf();
            bounds.set(left, top, right, bottom);
            onBoundsChange(bounds);
        }
    }

    public void setBounds(Rect b) { setBounds(b.left, b.top, b.right, b.bottom); }

    public final void copyBounds(Rect bounds) { bounds.set(this.bounds); }

    public final Rect copyBounds() { return new Rect(bounds); }

    public final Rect getBounds() {
        if (bounds == ZERO) bounds = new Rect();
        return bounds;
    }

    public Rect getDirtyBounds() { return getBounds(); }

    protected void onBoundsChange(Rect bounds) {}

    public final void setCallback(Callback cb) { callback = cb != null ? new java.lang.ref.WeakReference<>(cb) : null; }

    public Callback getCallback() { return callback != null ? callback.get() : null; }

    public void invalidateSelf() {
        Callback cb = getCallback();
        if (cb != null) cb.invalidateDrawable(this);
    }

    public void scheduleSelf(Runnable what, long when) {
        Callback cb = getCallback();
        if (cb != null) cb.scheduleDrawable(this, what, when);
    }

    public void unscheduleSelf(Runnable what) {
        Callback cb = getCallback();
        if (cb != null) cb.unscheduleDrawable(this, what);
    }

    public abstract void setAlpha(int alpha);

    public int getAlpha() { return 0xFF; }

    public abstract void setColorFilter(ColorFilter colorFilter);

    public ColorFilter getColorFilter() { return null; }

    public void setTint(int tintColor) {}

    @Deprecated
    public abstract int getOpacity();

    public boolean isStateful() { return false; }

    public boolean setState(int[] stateSet) {
        if (!java.util.Arrays.equals(state, stateSet)) {
            state = stateSet;
            return onStateChange(stateSet);
        }
        return false;
    }

    public int[] getState() { return state; }

    public Drawable getCurrent() { return this; }

    protected boolean onStateChange(int[] state) { return false; }

    public final boolean setLevel(int level) {
        if (this.level != level) { this.level = level; return onLevelChange(level); }
        return false;
    }

    public final int getLevel() { return level; }

    protected boolean onLevelChange(int level) { return false; }

    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = this.visible != visible;
        this.visible = visible;
        return changed;
    }

    public final boolean isVisible() { return visible; }

    public int getIntrinsicWidth() { return -1; }
    public int getIntrinsicHeight() { return -1; }
    public int getMinimumWidth() { int w = getIntrinsicWidth(); return Math.max(w, 0); }
    public int getMinimumHeight() { int h = getIntrinsicHeight(); return Math.max(h, 0); }

    public boolean getPadding(Rect padding) { padding.set(0, 0, 0, 0); return false; }

    public Drawable mutate() { return this; }

    public void jumpToCurrentState() {}

    public static int resolveOpacity(int op1, int op2) {
        if (op1 == op2) return op1;
        if (op1 == PixelFormat.UNKNOWN || op2 == PixelFormat.UNKNOWN) return PixelFormat.UNKNOWN;
        if (op1 == PixelFormat.TRANSLUCENT || op2 == PixelFormat.TRANSLUCENT) return PixelFormat.TRANSLUCENT;
        if (op1 == PixelFormat.TRANSPARENT || op2 == PixelFormat.TRANSPARENT) return PixelFormat.TRANSPARENT;
        return PixelFormat.OPAQUE;
    }
}
