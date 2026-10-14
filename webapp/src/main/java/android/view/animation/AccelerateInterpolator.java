package android.view.animation;

public class AccelerateInterpolator extends BaseInterpolator {
    private final float factor;
    private final double doubleFactor;
    public AccelerateInterpolator() { this(1f); }
    public AccelerateInterpolator(float factor) { this.factor = factor; this.doubleFactor = 2 * factor; }
    @Override public float getInterpolation(float input) { return factor == 1.0f ? input * input : (float) Math.pow(input, doubleFactor); }
}
