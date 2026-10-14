package android.view.animation;

public class LinearInterpolator extends BaseInterpolator {
    public LinearInterpolator() {}
    @Override public float getInterpolation(float input) { return input; }
}
