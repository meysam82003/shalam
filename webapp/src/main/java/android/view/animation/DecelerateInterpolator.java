package android.view.animation;

public class DecelerateInterpolator extends BaseInterpolator {
    private final float factor;
    public DecelerateInterpolator() { this(1.0f); }
    public DecelerateInterpolator(float factor) { this.factor = factor; }
    @Override public float getInterpolation(float input) {
        if (factor == 1.0f) return 1.0f - (1.0f - input) * (1.0f - input);
        return (float) (1.0f - Math.pow((1.0f - input), 2 * factor));
    }
}
