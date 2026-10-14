package android.animation;

/** ValueAnimator with Android's defaults (300 ms, accelerate-decelerate) on the shared frame clock. */
public class ValueAnimator extends Animator implements AnimationHandler.FrameCallback {
    public static final int RESTART = 1, REVERSE = 2, INFINITE = -1;

    public interface AnimatorUpdateListener { void onAnimationUpdate(ValueAnimator animation); }

    private static final TimeInterpolator DEFAULT = new android.view.animation.AccelerateDecelerateInterpolator();

    private long duration = 300;
    private long startDelay;
    private TimeInterpolator interpolator = DEFAULT;
    private float[] floatValues;
    private int[] intValues;
    private Object[] objectValues;
    private TypeEvaluator evaluator;
    private boolean argb;
    private int repeatCount;
    private int repeatMode = RESTART;
    private boolean running, started;
    private long startTime = -1;
    private float animatedFraction;
    private Object animatedValue;
    private final java.util.ArrayList<AnimatorUpdateListener> updateListeners = new java.util.ArrayList<>();

    public ValueAnimator() {}

    public static ValueAnimator ofFloat(float... values) { ValueAnimator a = new ValueAnimator(); a.setFloatValues(values); return a; }
    public static ValueAnimator ofInt(int... values) { ValueAnimator a = new ValueAnimator(); a.setIntValues(values); return a; }

    public static ValueAnimator ofArgb(int... values) {
        ValueAnimator a = new ValueAnimator();
        a.setIntValues(values);
        a.argb = true;
        return a;
    }

    public static ValueAnimator ofObject(TypeEvaluator evaluator, Object... values) {
        ValueAnimator a = new ValueAnimator();
        a.objectValues = values;
        a.evaluator = evaluator;
        return a;
    }

    public void setFloatValues(float... values) { floatValues = values; intValues = null; objectValues = null; animatedValue = values.length > 0 ? values[0] : 0f; }
    public void setIntValues(int... values) { intValues = values; floatValues = null; objectValues = null; animatedValue = values.length > 0 ? values[0] : 0; }
    public void setObjectValues(Object... values) { objectValues = values; }
    public void setEvaluator(TypeEvaluator value) { evaluator = value; }

    @Override public ValueAnimator setDuration(long duration) {
        if (duration < 0) throw new IllegalArgumentException("Animators cannot have negative duration: " + duration);
        this.duration = duration;
        return this;
    }

    @Override public long getDuration() { return duration; }
    @Override public void setStartDelay(long startDelay) { this.startDelay = Math.max(0, startDelay); }
    @Override public long getStartDelay() { return startDelay; }
    @Override public void setInterpolator(TimeInterpolator value) { interpolator = value != null ? value : new android.view.animation.LinearInterpolator(); }
    public TimeInterpolator getInterpolator() { return interpolator; }
    public void setRepeatCount(int value) { repeatCount = value; }
    public int getRepeatCount() { return repeatCount; }
    public void setRepeatMode(int value) { repeatMode = value; }
    public int getRepeatMode() { return repeatMode; }
    public void addUpdateListener(AnimatorUpdateListener l) { updateListeners.add(l); }
    public void removeUpdateListener(AnimatorUpdateListener l) { updateListeners.remove(l); }
    public void removeAllUpdateListeners() { updateListeners.clear(); }
    public Object getAnimatedValue() { return animatedValue; }
    public float getAnimatedFraction() { return animatedFraction; }
    public long getCurrentPlayTime() { return startTime < 0 ? 0 : android.os.SystemClock.uptimeMillis() - startTime; }
    public static long getFrameDelay() { return 16; }
    public static float getDurationScale() { return 1f; }
    public static boolean areAnimatorsEnabled() { return true; }

    @Override public void start() {
        if (running) cancel();
        running = true;
        started = true;
        startTime = -1;
        animateValue(0f);
        if (listeners != null) for (AnimatorListener l : new java.util.ArrayList<>(listeners)) l.onAnimationStart(this, false);
        AnimationHandler.add(this);
    }

    @Override public boolean doAnimationFrame(long frameTime) {
        if (!running) return true;
        if (startTime < 0) startTime = frameTime + startDelay;
        if (frameTime < startTime) return false;
        long elapsed = frameTime - startTime;
        float fraction = duration > 0 ? (float) elapsed / duration : 1f;
        int iteration = (int) Math.floor(fraction);
        boolean done = false;
        if (repeatCount != INFINITE && iteration > repeatCount) {
            fraction = 1f;
            done = true;
        } else if (fraction >= 1f) {
            if (repeatCount == 0) { fraction = 1f; done = true; }
            else {
                fraction = fraction - iteration;
                if (repeatMode == REVERSE && (iteration & 1) == 1) fraction = 1f - fraction;
                if (listeners != null) for (AnimatorListener l : new java.util.ArrayList<>(listeners)) l.onAnimationRepeat(this);
            }
        }
        animateValue(fraction);
        if (done) {
            running = false;
            if (listeners != null) for (AnimatorListener l : new java.util.ArrayList<>(listeners)) l.onAnimationEnd(this, false);
        }
        return done;
    }

    void animateValue(float fraction) {
        animatedFraction = fraction;
        float f = interpolator.getInterpolation(fraction);
        animatedValue = valueAt(f);
        for (AnimatorUpdateListener l : new java.util.ArrayList<>(updateListeners)) l.onAnimationUpdate(this);
    }

    private Object valueAt(float f) {
        if (floatValues != null) {
            if (floatValues.length == 1) return floatValues[0] * f;
            float pos = f * (floatValues.length - 1);
            int i = Math.max(0, Math.min(floatValues.length - 2, (int) Math.floor(pos)));
            float local = pos - i;
            return floatValues[i] + (floatValues[i + 1] - floatValues[i]) * local;
        }
        if (intValues != null) {
            if (intValues.length == 1) return Math.round(intValues[0] * f);
            float pos = f * (intValues.length - 1);
            int i = Math.max(0, Math.min(intValues.length - 2, (int) Math.floor(pos)));
            float local = pos - i;
            if (argb) return ArgbEvaluator.getInstance().evaluate(local, intValues[i], intValues[i + 1]);
            return (int) (intValues[i] + (intValues[i + 1] - intValues[i]) * local);
        }
        if (objectValues != null && objectValues.length > 1 && evaluator != null) {
            float pos = f * (objectValues.length - 1);
            int i = Math.max(0, Math.min(objectValues.length - 2, (int) Math.floor(pos)));
            return evaluator.evaluate(pos - i, objectValues[i], objectValues[i + 1]);
        }
        return objectValues != null && objectValues.length > 0 ? objectValues[0] : animatedValue;
    }

    @Override public void cancel() {
        if (!running) return;
        running = false;
        AnimationHandler.remove(this);
        if (listeners != null) {
            for (AnimatorListener l : new java.util.ArrayList<>(listeners)) l.onAnimationCancel(this);
            for (AnimatorListener l : new java.util.ArrayList<>(listeners)) l.onAnimationEnd(this, false);
        }
    }

    @Override public void end() {
        if (!running && !started) start();
        if (!running) return;
        running = false;
        AnimationHandler.remove(this);
        animateValue(repeatMode == REVERSE && (repeatCount & 1) == 1 ? 0f : 1f);
        if (listeners != null) for (AnimatorListener l : new java.util.ArrayList<>(listeners)) l.onAnimationEnd(this, false);
    }

    @Override public boolean isRunning() { return running && startTime >= 0 && android.os.SystemClock.uptimeMillis() >= startTime; }
    @Override public boolean isStarted() { return running; }

    public void reverse() {
        if (floatValues != null) { float[] r = floatValues.clone(); for (int i = 0; i < r.length; i++) r[i] = floatValues[floatValues.length - 1 - i]; floatValues = r; }
        if (intValues != null) { int[] r = intValues.clone(); for (int i = 0; i < r.length; i++) r[i] = intValues[intValues.length - 1 - i]; intValues = r; }
        start();
    }
}
