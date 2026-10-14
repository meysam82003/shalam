package android.view;

/** Global layout, pre-draw and scroll listeners of one window (a view's observer before attach is merged on attach). */
public final class ViewTreeObserver {
    public interface OnGlobalLayoutListener { void onGlobalLayout(); }
    public interface OnPreDrawListener { boolean onPreDraw(); }
    public interface OnScrollChangedListener { void onScrollChanged(); }
    public interface OnDrawListener { void onDraw(); }
    public interface OnGlobalFocusChangeListener { void onGlobalFocusChanged(View oldFocus, View newFocus); }
    public interface OnWindowFocusChangeListener { void onWindowFocusChanged(boolean hasFocus); }
    public interface OnTouchModeChangeListener { void onTouchModeChanged(boolean isInTouchMode); }

    private final java.util.ArrayList<OnGlobalLayoutListener> layout = new java.util.ArrayList<>();
    private final java.util.ArrayList<OnPreDrawListener> preDraw = new java.util.ArrayList<>();
    private final java.util.ArrayList<OnScrollChangedListener> scroll = new java.util.ArrayList<>();
    private final java.util.ArrayList<OnDrawListener> draw = new java.util.ArrayList<>();
    private boolean alive = true;

    void merge(ViewTreeObserver o) {
        for (OnGlobalLayoutListener l : o.layout) if (!layout.contains(l)) layout.add(l);
        for (OnPreDrawListener l : o.preDraw) if (!preDraw.contains(l)) preDraw.add(l);
        for (OnScrollChangedListener l : o.scroll) if (!scroll.contains(l)) scroll.add(l);
        for (OnDrawListener l : o.draw) if (!draw.contains(l)) draw.add(l);
        o.layout.clear(); o.preDraw.clear(); o.scroll.clear(); o.draw.clear();
        o.alive = false;
    }

    public boolean isAlive() { return alive; }

    private void check() { if (!alive) throw new IllegalStateException("This ViewTreeObserver is not alive, call getViewTreeObserver() again"); }

    public void addOnGlobalLayoutListener(OnGlobalLayoutListener l) { check(); layout.add(l); }
    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l) { check(); layout.remove(l); }
    @Deprecated public void removeGlobalOnLayoutListener(OnGlobalLayoutListener l) { removeOnGlobalLayoutListener(l); }
    public void addOnPreDrawListener(OnPreDrawListener l) { check(); preDraw.add(l); }
    public void removeOnPreDrawListener(OnPreDrawListener l) { check(); preDraw.remove(l); }
    public void addOnScrollChangedListener(OnScrollChangedListener l) { check(); scroll.add(l); }
    public void removeOnScrollChangedListener(OnScrollChangedListener l) { check(); scroll.remove(l); }
    public void addOnDrawListener(OnDrawListener l) { check(); draw.add(l); }
    public void removeOnDrawListener(OnDrawListener l) { check(); draw.remove(l); }
    public void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) {}
    public void removeOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) {}
    public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener l) {}
    public void removeOnWindowFocusChangeListener(OnWindowFocusChangeListener l) {}
    public void addOnTouchModeChangeListener(OnTouchModeChangeListener l) {}

    public void dispatchOnGlobalLayout() {
        if (layout.isEmpty()) return;
        for (OnGlobalLayoutListener l : new java.util.ArrayList<>(layout)) l.onGlobalLayout();
    }

    /** False when a listener cancelled this frame's draw (it is then tried again next frame, as on Android). */
    public boolean dispatchOnPreDraw() {
        boolean cancel = false;
        if (preDraw.isEmpty()) return false;
        for (OnPreDrawListener l : new java.util.ArrayList<>(preDraw)) cancel |= !l.onPreDraw();
        return cancel;
    }

    public void dispatchOnScrollChanged() {
        if (scroll.isEmpty()) return;
        for (OnScrollChangedListener l : new java.util.ArrayList<>(scroll)) l.onScrollChanged();
    }

    public void dispatchOnDraw() {
        if (draw.isEmpty()) return;
        for (OnDrawListener l : new java.util.ArrayList<>(draw)) l.onDraw();
    }
}
