package android.view.animation;

public abstract class Animation {
    public void setDuration(long durationMillis) {}
    public void setInterpolator(Interpolator i) {}
    public void cancel() {}
}
