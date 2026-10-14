package android.animation;

/**
 * Frame clock of all animators (one per page, like Android's per-thread AnimationHandler). The browser window calls
 * {@link #doFrame} on every animation frame while {@link #isActive} is true.
 */
public final class AnimationHandler {
    public interface FrameCallback { boolean doAnimationFrame(long frameTime); }

    private static final java.util.ArrayList<FrameCallback> callbacks = new java.util.ArrayList<>();
    private static Runnable requestFrame;

    private AnimationHandler() {}

    /** Set by the window: asks the browser for the next frame. */
    public static void setFrameRequester(Runnable r) { requestFrame = r; }

    public static void add(FrameCallback cb) {
        if (!callbacks.contains(cb)) callbacks.add(cb);
        if (requestFrame != null) requestFrame.run();
    }

    public static void remove(FrameCallback cb) { callbacks.remove(cb); }

    public static boolean isActive() { return !callbacks.isEmpty(); }

    /** Advances every animation; callbacks returning true are finished and removed. */
    public static void doFrame(long frameTime) {
        if (callbacks.isEmpty()) return;
        for (FrameCallback cb : new java.util.ArrayList<>(callbacks)) {
            if (!callbacks.contains(cb)) continue;
            boolean done;
            try {
                done = cb.doAnimationFrame(frameTime);
            } catch (RuntimeException e) {
                callbacks.remove(cb);
                throw e;
            }
            if (done) callbacks.remove(cb);
        }
    }
}
