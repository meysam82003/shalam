package android.view.animation;

/** Cubic Bézier interpolator from (0,0) to (1,1), solved per input like AOSP's sampled path (exact solve here). */
public class PathInterpolator extends BaseInterpolator {
    private final float x1, y1, x2, y2;

    public PathInterpolator(float controlX1, float controlY1, float controlX2, float controlY2) {
        x1 = controlX1; y1 = controlY1; x2 = controlX2; y2 = controlY2;
    }

    public PathInterpolator(float controlX, float controlY) { this(controlX * 2f / 3f, controlY * 2f / 3f, 1f / 3f + controlX * 2f / 3f, 1f / 3f + controlY * 2f / 3f); }

    private static float bez(float t, float p1, float p2) { float u = 1 - t; return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t; }

    @Override public float getInterpolation(float t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        float lo = 0, hi = 1, mid = t;
        for (int i = 0; i < 30; i++) {
            mid = (lo + hi) / 2;
            float x = bez(mid, x1, x2);
            if (Math.abs(x - t) < 1e-6f) break;
            if (x < t) lo = mid; else hi = mid;
        }
        return bez(mid, y1, y2);
    }
}
