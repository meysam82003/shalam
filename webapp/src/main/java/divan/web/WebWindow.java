package divan.web;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.AttachInfo;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRoots;
import android.view.WindowManager;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * The browser window as Android's window manager and view root: one canvas, the activity's content under any
 * open dialogs, measured, laid out and drawn on animation frames; pointer, wheel, key and back events are turned
 * into the same calls Android's touch handling makes (press, click, long press, drag scrolling and fling).
 */
public final class WebWindow {
    private WebWindow() {}

    @JSFunctor interface Frame extends JSObject { void call(double time); }
    @JSFunctor interface Pointer extends JSObject { void call(int kind, float x, float y, double time); }
    @JSFunctor interface Wheel extends JSObject { void call(float x, float y, float dx, float dy); }
    @JSFunctor interface Simple extends JSObject { void call(); }

    // ------------------------------------------------------------------ page
    @JSBody(script = "return DH.ctx;")
    private static native JSObject ctx();

    @JSBody(script = "return DH.width;")
    private static native int pageWidth();

    @JSBody(script = "return DH.height;")
    private static native int pageHeight();

    @JSBody(script = "return DH.dpr;")
    private static native float dpr();

    @JSBody(params = "cb", script = "DH.requestFrame(cb);")
    private static native void requestFrame(Frame cb);

    @JSBody(params = {"pointer", "wheel", "back", "resize", "visible", "hidden"},
        script = "DH.listen(pointer, wheel, back, resize, visible, hidden);")
    private static native void listen(Pointer pointer, Wheel wheel, Simple back, Simple resize, Simple visible, Simple hidden);

    @JSBody(params = "i", script = "return DH.inset(i);")
    private static native int inset(int i);

    @JSBody(script = "return DH.imeHeight();")
    private static native int imeHeight();

    @JSBody(params = "ms", script = "if (navigator.vibrate) try { navigator.vibrate(ms); } catch (e) {}")
    private static native void vibrateNow(int ms);

    @JSBody(params = "light", script = "DH.lightBars(light);")
    private static native void lightBarsNow(boolean light);

    @JSBody(params = "on", script = "DH.keepAwake(on);")
    private static native void keepAwake(boolean on);

    @JSBody(script = "location.reload();")
    private static native void reloadPage();

    @JSBody(script = "DH.leave();")
    private static native void leavePage();

    // ------------------------------------------------------------------ roots
    /** A root on the window: the activity's content or a dialog. */
    static final class Layer extends AttachInfo {
        final View view;
        final Dialog dialog;
        int x, y, w, h;
        boolean needsLayout = true;
        float anim = 1f, dim;
        long animStart = -1;
        boolean leaving;

        Layer(View view, Dialog dialog) { this.view = view; this.dialog = dialog; }

        @Override public void scheduleTraversal() { WebWindow.schedule(); }
        @Override public void requestLayoutRoot() { needsLayout = true; WebWindow.schedule(); }
        @Override public View rootView() { return view; }
        @Override public int windowLeft() { return x; }
        @Override public int windowTop() { return y; }
        @Override public void requestApplyInsets() { insetsDirty = true; WebWindow.schedule(); }
        @Override public void onFocusChanged(View old, View now) {}
        @Override public void vibrate(int ms) { vibrateNow(ms); }
        @Override public float density() { return dpr(); }
    }

    private static Activity activity;
    private static Layer content;
    private static final java.util.ArrayList<Layer> dialogs = new java.util.ArrayList<>();
    private static boolean scheduled, insetsDirty = true;
    private static int width, height;
    private static final Frame FRAME = WebWindow::frame;

    // ------------------------------------------------------------------ setup
    @JSBody(params = "cb", script = "DH.redraw = cb;")
    private static native void onRedraw(Simple cb);

    public static void init() {
        onRedraw(() -> android.os.Looper.dispatch(() -> {
            // a picture finished decoding: draw (and measure) everything again
            if (content != null) invalidateTree(content.view);
            for (Layer l : dialogs) invalidateTree(l.view);
        }));
        width = pageWidth(); height = pageHeight();
        android.content.res.Resources.INSTANCE.getDisplayMetrics().setTo(metrics());
        android.animation.AnimationHandler.setFrameRequester(WebWindow::schedule);
        listen(WebWindow::pointer, WebWindow::wheel, () -> android.os.Looper.dispatch(WebWindow::back),
            () -> android.os.Looper.dispatch(WebWindow::resized),
            () -> android.os.Looper.dispatch(Main::resumed), () -> android.os.Looper.dispatch(Main::paused));
    }

    private static android.util.DisplayMetrics metrics() {
        android.util.DisplayMetrics m = new android.util.DisplayMetrics();
        float d = dpr();
        m.density = d; m.scaledDensity = d; m.densityDpi = Math.round(d * 160); m.xdpi = m.ydpi = d * 160;
        m.widthPixels = pageWidth(); m.heightPixels = pageHeight();
        return m;
    }

    private static void resized() {
        width = pageWidth(); height = pageHeight();
        android.content.res.Resources.INSTANCE.getDisplayMetrics().setTo(metrics());
        insetsDirty = true;
        if (content != null) content.needsLayout = true;
        for (Layer l : dialogs) l.needsLayout = true;
        schedule();
    }

    private static void invalidateTree(View v) {
        v.requestLayout();
        v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) invalidateTree(((ViewGroup) v).getChildAt(i));
    }

    public static Activity activity() { return activity; }

    public static void setActivityContent(Activity a, View view) {
        activity = a;
        if (content != null) ViewRoots.detach(content.view);
        content = new Layer(view, null);
        a.getWindow().setOnChange(WebWindow::windowChanged);
        ViewRoots.attach(view, content);
        insetsDirty = true;
        schedule();
    }

    private static void windowChanged() {
        if (activity != null) keepAwake((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        for (Layer l : dialogs) l.needsLayout = true;
        schedule();
    }

    public static void activityFinished(Activity a) { leavePage(); }

    public static void reload() { reloadPage(); }

    public static void setLightBars(boolean light) { lightBarsNow(light); }

    // ------------------------------------------------------------------ dialogs
    public static void showDialog(Dialog d, View view) {
        if (view == null) return;
        Layer l = new Layer(view, d);
        android.view.Window w = d.getWindow();
        w.setOnChange(() -> { l.needsLayout = true; schedule(); });
        boolean animated = w.getAttributes().windowAnimations != 0;
        l.anim = animated ? 0f : 1f;
        dialogs.add(l);
        ViewRoots.attach(view, l);
        Inputs.blurAll();
        schedule();
    }

    public static void dismissDialog(Dialog d) {
        for (Layer l : dialogs) {
            if (l.dialog != d || l.leaving) continue;
            Inputs.blurAll();
            if (d.getWindow().getAttributes().windowAnimations != 0) { l.leaving = true; l.animStart = -1; schedule(); }
            else remove(l);
            return;
        }
    }

    private static void remove(Layer l) {
        dialogs.remove(l);
        ViewRoots.detach(l.view);
        schedule();
    }

    // ------------------------------------------------------------------ frames
    static void schedule() {
        if (scheduled) return;
        scheduled = true;
        requestFrame(FRAME);
    }

    private static void frame(double time) {
        scheduled = false;
        android.os.Looper.dispatch(() -> traverse((long) time));
    }

    private static void traverse(long now) {
        android.animation.AnimationHandler.doFrame(now);
        if (insetsDirty && content != null) {
            insetsDirty = false;
            android.view.WindowInsets insets = new android.view.WindowInsets(android.graphics.Insets.of(inset(3), inset(0), inset(1), inset(2)), android.graphics.Insets.of(0, 0, 0, imeHeight()));
            android.view.WindowInsets.setCurrent(insets);
            content.view.dispatchApplyWindowInsets(insets);
        }
        if (content != null && (content.needsLayout || content.view.isLayoutRequested() || content.w != width || content.h != height)) {
            content.needsLayout = false;
            content.w = width; content.h = height;
            content.view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            content.view.layout(0, 0, width, height);
            content.treeObserver.dispatchOnGlobalLayout();
        }
        for (Layer l : new java.util.ArrayList<>(dialogs)) {
            if (l.needsLayout || l.view.isLayoutRequested()) { layoutDialog(l); l.treeObserver.dispatchOnGlobalLayout(); }
        }
        // As ViewRootImpl: pre-draw listeners may cancel this frame (they asked for another one).
        boolean cancelDraw = false;
        if (content != null && content.treeObserver.dispatchOnPreDraw()) cancelDraw = true;
        for (Layer l : new java.util.ArrayList<>(dialogs)) if (l.treeObserver.dispatchOnPreDraw()) cancelDraw = true;
        if (content != null && (content.view.isLayoutRequested())) { schedule(); }
        boolean animating = false;
        for (Layer l : new java.util.ArrayList<>(dialogs)) {
            if (l.leaving || l.anim < 1f) {
                if (l.animStart < 0) l.animStart = now;
                float t = Math.min(1f, (now - l.animStart) / (l.leaving ? 150f : 220f));
                l.anim = l.leaving ? 1f - t : t;
                if (t >= 1f && l.leaving) { remove(l); continue; }
                if (t < 1f) animating = true;
            }
        }
        if (cancelDraw) { schedule(); return; }
        draw();
        if (content != null) content.treeObserver.dispatchOnDraw();
        Inputs.place();
        Videos.place();
        if (animating || android.animation.AnimationHandler.isActive()) schedule();
    }

    private static void layoutDialog(Layer l) {
        l.needsLayout = false;
        WindowManager.LayoutParams a = l.dialog.getWindow().getAttributes();
        int top = inset(0), bottom = inset(2) + imeHeight(), left = inset(3), right = inset(1);
        int availW = width - left - right, availH = height - top - bottom;
        int wSpec = a.width == ViewGroup.LayoutParams.MATCH_PARENT ? View.MeasureSpec.makeMeasureSpec(availW, View.MeasureSpec.EXACTLY)
            : a.width > 0 ? View.MeasureSpec.makeMeasureSpec(Math.min(a.width, availW), View.MeasureSpec.EXACTLY)
            : View.MeasureSpec.makeMeasureSpec(availW, View.MeasureSpec.AT_MOST);
        int hSpec = a.height == ViewGroup.LayoutParams.MATCH_PARENT ? View.MeasureSpec.makeMeasureSpec(availH, View.MeasureSpec.EXACTLY)
            : a.height > 0 ? View.MeasureSpec.makeMeasureSpec(Math.min(a.height, availH), View.MeasureSpec.EXACTLY)
            : View.MeasureSpec.makeMeasureSpec(availH, View.MeasureSpec.AT_MOST);
        l.view.measure(wSpec, hSpec);
        l.w = l.view.getMeasuredWidth(); l.h = l.view.getMeasuredHeight();
        int g = a.gravity == 0 ? Gravity.CENTER : a.gravity;
        int hg = g & Gravity.HORIZONTAL_GRAVITY_MASK, vg = g & Gravity.VERTICAL_GRAVITY_MASK;
        l.x = left + (hg == Gravity.LEFT ? 0 : hg == Gravity.RIGHT ? availW - l.w : (availW - l.w) / 2);
        l.y = top + (vg == Gravity.TOP ? 0 : vg == Gravity.BOTTOM ? availH - l.h : (availH - l.h) / 2);
        l.dim = (a.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0 ? a.dimAmount : 0f;
        l.view.layout(0, 0, l.w, l.h);
    }

    private static Canvas canvas;

    private static void draw() {
        JSObject c = ctx();
        if (canvas == null || canvas.getWidth() != width || canvas.getHeight() != height) canvas = new Canvas(c, width, height);
        canvas.restoreToCount(1);
        canvas.drawColor(Color.BLACK);
        if (content != null) {
            Drawable bg = activity != null ? activity.getWindow().backgroundDrawable() : null;
            if (bg != null) { bg.setBounds(0, 0, width, height); bg.draw(canvas); }
            ViewRoots.draw(content.view, canvas, width, height);
        }
        for (Layer l : dialogs) {
            float e = l.leaving ? accelerateCubic(l.anim) : decelerateCubic(l.anim);
            if (l.dim > 0) canvas.drawColor(Color.argb(Math.round(255 * l.dim * Math.min(1f, l.anim * 1.2f)), 0, 0, 0));
            int save = canvas.save();
            canvas.translate(l.x, l.y);
            float s = l.leaving ? 0.94f + 0.06f * e : 0.88f + 0.12f * e;
            if (s != 1f) canvas.scale(s, s, l.w / 2f, l.h / 2f);
            float alpha = l.leaving ? l.anim : Math.min(1f, l.anim * 220f / 180f);
            if (alpha < 1f) canvas.multiplyAlpha(alpha);
            Drawable bg = l.dialog.getWindow().backgroundDrawable();
            if (bg != null) { bg.setBounds(0, 0, l.w, l.h); bg.draw(canvas); }
            ViewRoots.draw(l.view, canvas, l.w, l.h);
            canvas.restoreToCount(save);
        }
    }

    private static float decelerateCubic(float t) { float u = 1f - t; return 1f - u * u * u; }
    private static float accelerateCubic(float t) { float u = 1f - t; return 1f - u * u * u; }

    // ------------------------------------------------------------------ touch
    private static final int DOWN = 0, MOVE = 1, UP = 2, CANCEL = 3;
    private static Layer touchLayer;
    private static java.util.ArrayList<View> path = new java.util.ArrayList<>();
    private static View touchTarget, pressed, clickable, longClickable;
    private static TouchScrollable dragging;
    private static boolean outside, longFired, moved;
    private static float downX, downY, lastX, lastY;
    private static final float[] vx = new float[6], vy = new float[6];
    private static final double[] vt = new double[6];
    private static int vn;
    private static final Runnable LONG_PRESS = WebWindow::longPress;
    private static final android.os.Handler HANDLER = new android.os.Handler(android.os.Looper.getMainLooper());

    private static Layer topLayer() {
        for (int i = dialogs.size() - 1; i >= 0; i--) if (!dialogs.get(i).leaving) return dialogs.get(i);
        return content;
    }

    private static void pointer(int kind, float x, float y, double time) {
        android.os.Looper.dispatch(() -> onPointer(kind, x, y, time));
    }

    private static void onPointer(int kind, float x, float y, double time) {
        if (kind == DOWN) {
            touchLayer = topLayer();
            if (touchLayer == null) return;
            downX = lastX = x; downY = lastY = y; vn = 0; track(x, y, time);
            moved = false; longFired = false; dragging = null; touchTarget = null; pressed = null; clickable = null; longClickable = null;
            outside = touchLayer.dialog != null && (x < touchLayer.x || y < touchLayer.y || x >= touchLayer.x + touchLayer.w || y >= touchLayer.y + touchLayer.h);
            if (outside) return;
            path = new java.util.ArrayList<>();
            hit(touchLayer.view, x - touchLayer.x, y - touchLayer.y, path);
            for (TouchScrollable s : scrollables()) s.stopFling();
            MotionEvent down = new MotionEvent(MotionEvent.ACTION_DOWN, 0, 0, x, y, (long) time);
            for (int i = path.size() - 1; i >= 0; i--) {
                View v = path.get(i);
                if (v.dispatchTouchListener(local(down, v)) || v.onTouchEvent(local(down, v))) { touchTarget = v; break; }
                if (v.isEnabled() && (v.isClickable() || v.isLongClickable())) break;
            }
            for (int i = path.size() - 1; i >= 0; i--) {
                View v = path.get(i);
                if (!v.isEnabled()) { if (v.isClickable() || v.isLongClickable()) break; continue; }
                if (clickable == null && v.isClickable()) clickable = v;
                if (longClickable == null && v.isLongClickable()) longClickable = v;
                if (clickable != null || longClickable != null) break;
            }
            pressed = clickable != null ? clickable : longClickable;
            if (pressed != null) pressed.setPressed(true);
            if (longClickable != null) HANDLER.postDelayed(LONG_PRESS, 500);
            schedule();
            return;
        }
        if (touchLayer == null) return;
        if (kind == MOVE) {
            track(x, y, time);
            float slop = 8 * dpr();
            if (!moved && (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop)) {
                moved = true;
                HANDLER.removeCallbacks(LONG_PRESS);
                boolean vertical = Math.abs(y - downY) >= Math.abs(x - downX);
                int delta = Math.round(vertical ? downY - y : downX - x);
                for (TouchScrollable s : scrollables()) {
                    if (s.scrollsVertically() == vertical && s.canScrollBy(delta)) { dragging = s; break; }
                }
                if (dragging != null || touchTarget == null) unpress();
            }
            if (dragging != null) {
                int delta = Math.round(dragging.scrollsVertically() ? lastY - y : lastX - x);
                if (delta != 0) dragging.dragBy(delta);
            } else if (touchTarget != null) {
                touchTarget.onTouchEvent(local(new MotionEvent(MotionEvent.ACTION_MOVE, 0, 0, x, y, (long) time), touchTarget));
            }
            lastX = x; lastY = y;
            schedule();
            return;
        }
        HANDLER.removeCallbacks(LONG_PRESS);
        if (kind == UP && outside) {
            Layer l = touchLayer;
            if (l.dialog != null && l.dialog.cancelsOnTouchOutside()) l.dialog.cancel();
        } else if (dragging != null) {
            if (kind == UP) dragging.flingWith(-Math.round(velocity(dragging.scrollsVertically())));
        } else {
            MotionEvent up = new MotionEvent(kind == UP ? MotionEvent.ACTION_UP : MotionEvent.ACTION_CANCEL, 0, 0, x, y, (long) time);
            boolean consumed = touchTarget != null && touchTarget.onTouchEvent(local(up, touchTarget));
            View click = clickable;
            unpress();
            // The click runs inside the browser's own pointer event, so pickers, sharing and windows are allowed.
            if (kind == UP && !moved && !longFired && !consumed && click != null && click.isEnabled() && click.isAttachedToWindow()) click.performClick();
        }
        touchLayer = null;
        dragging = null;
        schedule();
    }

    private static void unpress() {
        if (pressed != null) { pressed.setPressed(false); pressed = null; }
    }

    private static void longPress() {
        if (longClickable == null || moved || !longClickable.isAttachedToWindow()) return;
        longFired = longClickable.performLongClick();
        if (longFired) { longClickable.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); unpress(); }
    }

    private static java.util.List<TouchScrollable> scrollables() {
        java.util.ArrayList<TouchScrollable> out = new java.util.ArrayList<>();
        for (int i = path.size() - 1; i >= 0; i--) if (path.get(i) instanceof TouchScrollable) out.add((TouchScrollable) path.get(i));
        return out;
    }

    private static void track(float x, float y, double t) {
        int i = vn % vx.length;
        vx[i] = x; vy[i] = y; vt[i] = t; vn++;
    }

    /** Pixels per second over the last ~100 ms of the drag. */
    private static float velocity(boolean vertical) {
        if (vn < 2) return 0;
        int last = (vn - 1) % vx.length;
        int first = last;
        for (int k = 1; k < Math.min(vn, vx.length); k++) {
            int i = (vn - 1 - k) % vx.length;
            if (vt[last] - vt[i] > 100) break;
            first = i;
        }
        double dt = vt[last] - vt[first];
        if (dt <= 0) return 0;
        float d = vertical ? vy[last] - vy[first] : vx[last] - vx[first];
        return (float) (d * 1000 / dt);
    }

    /** Views under (x, y), from the root down to the deepest, topmost child. */
    private static void hit(View v, float x, float y, java.util.List<View> out) {
        out.add(v);
        if (!(v instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) v;
        float cx = x + v.getScrollX(), cy = y + v.getScrollY();
        for (int i = g.getChildCount() - 1; i >= 0; i--) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getAlpha() <= 0f) continue;
            float lx = cx - c.getLeft() - c.getTranslationX(), ly = cy - c.getTop() - c.getTranslationY();
            if (c.hasTransform()) {
                float px = c.getPivotX(), py = c.getPivotY();
                lx -= px; ly -= py;
                if (c.getRotation() != 0) {
                    double r = Math.toRadians(-c.getRotation());
                    float rx = (float) (lx * Math.cos(r) - ly * Math.sin(r)), ry = (float) (lx * Math.sin(r) + ly * Math.cos(r));
                    lx = rx; ly = ry;
                }
                lx = lx / (c.getScaleX() == 0 ? 1 : c.getScaleX()) + px;
                ly = ly / (c.getScaleY() == 0 ? 1 : c.getScaleY()) + py;
            }
            if (lx >= 0 && ly >= 0 && lx < c.getWidth() && ly < c.getHeight()) { hit(c, lx, ly, out); return; }
        }
    }

    /** The event in the view's own coordinates. */
    private static MotionEvent local(MotionEvent e, View v) {
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        return new MotionEvent(e.getAction(), e.getRawX() - loc[0], e.getRawY() - loc[1], e.getRawX(), e.getRawY(), e.getEventTime());
    }

    // ------------------------------------------------------------------ wheel and back
    private static void wheel(float x, float y, float dx, float dy) {
        android.os.Looper.dispatch(() -> {
            Layer l = topLayer();
            if (l == null) return;
            java.util.ArrayList<View> p = new java.util.ArrayList<>();
            hit(l.view, x - l.x, y - l.y, p);
            boolean vertical = Math.abs(dy) >= Math.abs(dx);
            int delta = Math.round(vertical ? dy : dx);
            for (int i = p.size() - 1; i >= 0; i--) {
                if (!(p.get(i) instanceof TouchScrollable)) continue;
                TouchScrollable s = (TouchScrollable) p.get(i);
                if (s.scrollsVertically() == vertical && s.canScrollBy(delta)) { s.stopFling(); s.dragBy(delta); break; }
            }
            schedule();
        });
    }

    private static void back() {
        if (Inputs.anyShowing()) { Inputs.blurAll(); return; }
        Layer l = topLayer();
        if (l != null && l.dialog != null) { l.dialog.onBackPressed(); return; }
        if (activity != null) activity.onBackPressed();
    }
}
