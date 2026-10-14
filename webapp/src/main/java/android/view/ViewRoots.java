package android.view;

/** What the browser window (divan.web.WebWindow) does with a root view that Android's ViewRootImpl does. */
public final class ViewRoots {
    private ViewRoots() {}

    public static void attach(View root, AttachInfo info) { root.dispatchAttachedToWindow(info); }

    public static void detach(View root) { if (root.isAttachedToWindow()) root.dispatchDetachedFromWindow(); }

    /** Draws the root with the visible part of the window as its cull rectangle. */
    public static void draw(View root, android.graphics.Canvas canvas, int width, int height) {
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            g.cullL = root.getScrollX(); g.cullT = root.getScrollY(); g.cullR = root.getScrollX() + width; g.cullB = root.getScrollY() + height;
        }
        root.computeScroll();
        root.draw(canvas);
    }
}
