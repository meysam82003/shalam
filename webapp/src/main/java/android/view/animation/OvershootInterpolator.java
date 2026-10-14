package android.view.animation;

public class OvershootInterpolator extends BaseInterpolator {
    private final float tension;
    public OvershootInterpolator() { tension = 2.0f; }
    public OvershootInterpolator(float tension) { this.tension = tension; }
    @Override public float getInterpolation(float t) { t -= 1.0f; return t * t * ((tension + 1) * t + tension) + 1.0f; }
}
