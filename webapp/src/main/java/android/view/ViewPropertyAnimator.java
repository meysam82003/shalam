package android.view;

import android.animation.AnimationHandler;
import android.animation.Animator;
import android.animation.TimeInterpolator;

/**
 * AOSP ViewPropertyAnimator behaviour: property calls queue up and start together on the next frame (or at start()),
 * start values are taken when a property is named, a newer animation of a property takes it over from older ones,
 * cancel() stops everything without running end actions.
 */
public class ViewPropertyAnimator {
    static final int TRANSLATION_X = 0x0001, TRANSLATION_Y = 0x0002, TRANSLATION_Z = 0x0004, SCALE_X = 0x0008, SCALE_Y = 0x0010,
        ROTATION = 0x0020, X = 0x0080, Y = 0x0100, ALPHA = 0x0400;

    private static final TimeInterpolator DEFAULT = new android.view.animation.AccelerateDecelerateInterpolator();

    private final View view;
    private long duration = 300;
    private boolean durationSet;
    private long startDelay;
    private TimeInterpolator interpolator;
    private Runnable pendingStart, pendingEnd;
    private Animator.AnimatorListener listener;
    private android.animation.ValueAnimator.AnimatorUpdateListener updateListener;
    private final java.util.ArrayList<float[]> pending = new java.util.ArrayList<>(); // {name, from, delta}
    private final java.util.ArrayList<Run> running = new java.util.ArrayList<>();
    private final Runnable starter = this::startAnimation;
    private boolean starterPosted;

    ViewPropertyAnimator(View view) { this.view = view; }

    private final class Run implements AnimationHandler.FrameCallback {
        final java.util.ArrayList<float[]> props;
        int mask;
        final long dur, delay;
        final TimeInterpolator interp;
        final Runnable endAction;
        final Animator.AnimatorListener l;
        final android.animation.ValueAnimator.AnimatorUpdateListener ul;
        long startTime = -1;
        boolean started;
        final Runnable startAction;

        Run(java.util.ArrayList<float[]> props, long dur, long delay, TimeInterpolator interp, Runnable startAction, Runnable endAction,
            Animator.AnimatorListener l, android.animation.ValueAnimator.AnimatorUpdateListener ul) {
            this.props = props; this.dur = dur; this.delay = delay; this.interp = interp; this.startAction = startAction; this.endAction = endAction;
            this.l = l; this.ul = ul;
            for (float[] p : props) mask |= (int) p[0];
        }

        @Override public boolean doAnimationFrame(long frameTime) {
            if (startTime < 0) startTime = frameTime + delay;
            if (frameTime < startTime) return false;
            if (!started) {
                started = true;
                if (startAction != null) startAction.run();
                if (l != null) l.onAnimationStart(null);
            }
            float fraction = dur > 0 ? Math.min(1f, (frameTime - startTime) / (float) dur) : 1f;
            float v = interp.getInterpolation(fraction);
            for (float[] p : props) set((int) p[0], p[1] + p[2] * v);
            if (ul != null) ul.onAnimationUpdate(null);
            if (fraction >= 1f) {
                running.remove(this);
                if (endAction != null) endAction.run();
                if (l != null) l.onAnimationEnd(null);
                return true;
            }
            return false;
        }

        void cancelRun() {
            AnimationHandler.remove(this);
            running.remove(this);
            if (l != null) { l.onAnimationCancel(null); l.onAnimationEnd(null); }
        }
    }

    public ViewPropertyAnimator setDuration(long duration) {
        if (duration < 0) throw new IllegalArgumentException("Animators cannot have negative duration: " + duration);
        durationSet = true;
        this.duration = duration;
        return this;
    }

    public long getDuration() { return duration; }
    public long getStartDelay() { return startDelay; }

    public ViewPropertyAnimator setStartDelay(long startDelay) {
        if (startDelay < 0) throw new IllegalArgumentException("Animators cannot have negative start delay: " + startDelay);
        this.startDelay = startDelay;
        return this;
    }

    public ViewPropertyAnimator setInterpolator(TimeInterpolator interpolator) { this.interpolator = interpolator; return this; }
    public TimeInterpolator getInterpolator() { return interpolator != null ? interpolator : DEFAULT; }
    public ViewPropertyAnimator setListener(Animator.AnimatorListener listener) { this.listener = listener; return this; }
    public ViewPropertyAnimator setUpdateListener(android.animation.ValueAnimator.AnimatorUpdateListener l) { updateListener = l; return this; }
    public ViewPropertyAnimator withLayer() { return this; }
    public ViewPropertyAnimator withStartAction(Runnable runnable) { pendingStart = runnable; return this; }
    public ViewPropertyAnimator withEndAction(Runnable runnable) { pendingEnd = runnable; return this; }

    public void start() {
        view.removeCallbacks(starter);
        starterPosted = false;
        startAnimation();
    }

    /** Stops running and queued animations of this view; end actions are not run. */
    public void cancel() {
        for (Run r : new java.util.ArrayList<>(running)) r.cancelRun();
        pending.clear();
        pendingStart = null;
        pendingEnd = null;
        view.removeCallbacks(starter);
        starterPosted = false;
    }

    /** Animations of a view leaving its parent stop quietly. */
    void cancelForRemoval() { for (Run r : new java.util.ArrayList<>(running)) { AnimationHandler.remove(r); running.remove(r); } }

    public ViewPropertyAnimator x(float value) { animateProperty(X, value); return this; }
    public ViewPropertyAnimator xBy(float value) { animatePropertyBy(X, value); return this; }
    public ViewPropertyAnimator y(float value) { animateProperty(Y, value); return this; }
    public ViewPropertyAnimator yBy(float value) { animatePropertyBy(Y, value); return this; }
    public ViewPropertyAnimator rotation(float value) { animateProperty(ROTATION, value); return this; }
    public ViewPropertyAnimator rotationBy(float value) { animatePropertyBy(ROTATION, value); return this; }
    public ViewPropertyAnimator translationX(float value) { animateProperty(TRANSLATION_X, value); return this; }
    public ViewPropertyAnimator translationXBy(float value) { animatePropertyBy(TRANSLATION_X, value); return this; }
    public ViewPropertyAnimator translationY(float value) { animateProperty(TRANSLATION_Y, value); return this; }
    public ViewPropertyAnimator translationYBy(float value) { animatePropertyBy(TRANSLATION_Y, value); return this; }
    public ViewPropertyAnimator translationZ(float value) { animateProperty(TRANSLATION_Z, value); return this; }
    public ViewPropertyAnimator scaleX(float value) { animateProperty(SCALE_X, value); return this; }
    public ViewPropertyAnimator scaleXBy(float value) { animatePropertyBy(SCALE_X, value); return this; }
    public ViewPropertyAnimator scaleY(float value) { animateProperty(SCALE_Y, value); return this; }
    public ViewPropertyAnimator scaleYBy(float value) { animatePropertyBy(SCALE_Y, value); return this; }
    public ViewPropertyAnimator alpha(float value) { animateProperty(ALPHA, value); return this; }
    public ViewPropertyAnimator alphaBy(float value) { animatePropertyBy(ALPHA, value); return this; }
    public ViewPropertyAnimator rotationX(float value) { return this; }
    public ViewPropertyAnimator rotationY(float value) { return this; }

    private void animateProperty(int name, float to) {
        float from = get(name);
        animatePropertyBy(name, from, to - from);
    }

    private void animatePropertyBy(int name, float by) { animatePropertyBy(name, get(name), by); }

    private void animatePropertyBy(int name, float from, float delta) {
        // a newer animation of this property replaces the running ones
        for (Run r : new java.util.ArrayList<>(running)) {
            if ((r.mask & name) != 0) {
                r.props.removeIf(p -> (int) p[0] == name);
                r.mask &= ~name;
                if (r.props.isEmpty()) { AnimationHandler.remove(r); running.remove(r); }
            }
        }
        pending.removeIf(p -> (int) p[0] == name);
        pending.add(new float[] {name, from, delta});
        if (!starterPosted) {
            starterPosted = true;
            view.post(starter);
        }
    }

    private void startAnimation() {
        starterPosted = false;
        if (pending.isEmpty() && pendingStart == null && pendingEnd == null) return;
        Run r = new Run(new java.util.ArrayList<>(pending), duration, startDelay, getInterpolator(), pendingStart, pendingEnd, listener, updateListener);
        pending.clear();
        pendingStart = null;
        pendingEnd = null;
        running.add(r);
        AnimationHandler.add(r);
    }

    boolean isRunning() { return !running.isEmpty(); }

    private float get(int name) {
        switch (name) {
            case TRANSLATION_X: return view.mTranslationX;
            case TRANSLATION_Y: return view.mTranslationY;
            case TRANSLATION_Z: return view.mTranslationZ;
            case SCALE_X: return view.mScaleX;
            case SCALE_Y: return view.mScaleY;
            case ROTATION: return view.mRotation;
            case X: return view.mLeft + view.mTranslationX;
            case Y: return view.mTop + view.mTranslationY;
            case ALPHA: return view.mAlpha;
            default: return 0;
        }
    }

    private void set(int name, float v) {
        switch (name) {
            case TRANSLATION_X: view.setTranslationX(v); break;
            case TRANSLATION_Y: view.setTranslationY(v); break;
            case TRANSLATION_Z: view.setTranslationZ(v); break;
            case SCALE_X: view.setScaleX(v); break;
            case SCALE_Y: view.setScaleY(v); break;
            case ROTATION: view.setRotation(v); break;
            case X: view.setTranslationX(v - view.mLeft); break;
            case Y: view.setTranslationY(v - view.mTop); break;
            case ALPHA: view.setAlpha(v); break;
            default:
        }
    }
}
