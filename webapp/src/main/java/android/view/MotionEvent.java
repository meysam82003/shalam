package android.view;

/** Touch event delivered to views (single pointer). */
public final class MotionEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3, ACTION_OUTSIDE = 4;
    private final int action;
    private final float x, y, rawX, rawY;
    private final long time;

    public MotionEvent(int action, float x, float y, float rawX, float rawY, long time) {
        this.action = action; this.x = x; this.y = y; this.rawX = rawX; this.rawY = rawY; this.time = time;
    }

    public int getAction() { return action; }
    public int getActionMasked() { return action; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getRawX() { return rawX; }
    public float getRawY() { return rawY; }
    public long getEventTime() { return time; }
    public int getPointerCount() { return 1; }

    public MotionEvent offset(float dx, float dy) { return new MotionEvent(action, x + dx, y + dy, rawX, rawY, time); }
}
