package android.animation;

public abstract class Animator {
    public interface AnimatorListener {
        default void onAnimationStart(Animator animation, boolean isReverse) { onAnimationStart(animation); }
        void onAnimationStart(Animator animation);
        default void onAnimationEnd(Animator animation, boolean isReverse) { onAnimationEnd(animation); }
        void onAnimationEnd(Animator animation);
        void onAnimationCancel(Animator animation);
        void onAnimationRepeat(Animator animation);
    }

    protected java.util.ArrayList<AnimatorListener> listeners;

    public void addListener(AnimatorListener listener) {
        if (listeners == null) listeners = new java.util.ArrayList<>();
        listeners.add(listener);
    }

    public void removeListener(AnimatorListener listener) { if (listeners != null) listeners.remove(listener); }
    public void removeAllListeners() { if (listeners != null) listeners.clear(); }
    public java.util.ArrayList<AnimatorListener> getListeners() { return listeners; }

    public abstract void start();
    public abstract void cancel();
    public abstract void end();
    public abstract boolean isRunning();
    public boolean isStarted() { return isRunning(); }
    public abstract Animator setDuration(long duration);
    public abstract long getDuration();
    public abstract void setInterpolator(TimeInterpolator value);
    public abstract void setStartDelay(long startDelay);
    public long getStartDelay() { return 0; }
}
