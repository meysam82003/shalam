package android.widget;

import android.content.Context;

/**
 * Fling and smooth-scroll motion of AOSP OverScroller (spline fling, viscous-fluid scroll), one axis pair.
 * Times come from the shared animation clock.
 */
public class OverScroller {
    private static final float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));
    private static final float INFLEXION = 0.35f;
    private static final float START_TENSION = 0.5f;
    private static final float END_TENSION = 1.0f;
    private static final float P1 = START_TENSION * INFLEXION;
    private static final float P2 = 1.0f - END_TENSION * (1.0f - INFLEXION);
    private static final int NB_SAMPLES = 100;
    private static final float[] SPLINE_POSITION = new float[NB_SAMPLES + 1];
    private static final float[] SPLINE_TIME = new float[NB_SAMPLES + 1];

    static {
        float xMin = 0.0f, yMin = 0.0f;
        for (int i = 0; i < NB_SAMPLES; i++) {
            final float alpha = (float) i / NB_SAMPLES;
            float xMax = 1.0f;
            float x, tx, coef;
            while (true) {
                x = xMin + (xMax - xMin) / 2.0f;
                coef = 3.0f * x * (1.0f - x);
                tx = coef * ((1.0f - x) * P1 + x * P2) + x * x * x;
                if (Math.abs(tx - alpha) < 1E-5) break;
                if (tx > alpha) xMax = x; else xMin = x;
            }
            SPLINE_POSITION[i] = coef * ((1.0f - x) * START_TENSION + x) + x * x * x;
            float yMax = 1.0f;
            float y, dy;
            while (true) {
                y = yMin + (yMax - yMin) / 2.0f;
                coef = 3.0f * y * (1.0f - y);
                dy = coef * ((1.0f - y) * START_TENSION + y) + y * y * y;
                if (Math.abs(dy - alpha) < 1E-5) break;
                if (dy > alpha) yMax = y; else yMin = y;
            }
            SPLINE_TIME[i] = coef * ((1.0f - y) * P1 + y * P2) + y * y * y;
        }
        SPLINE_POSITION[NB_SAMPLES] = SPLINE_TIME[NB_SAMPLES] = 1.0f;
    }

    private static final float VISCOUS_FLUID_SCALE = 8.0f;
    private static final float VISCOUS_FLUID_NORMALIZE = 1.0f / viscousFluid(1.0f);
    private static final float VISCOUS_FLUID_OFFSET = 1.0f - VISCOUS_FLUID_NORMALIZE * viscousFluid(1.0f);

    private static float viscousFluid(float x) {
        x *= VISCOUS_FLUID_SCALE;
        if (x < 1.0f) {
            x -= (1.0f - (float) Math.exp(-x));
        } else {
            float start = 0.36787944117f;
            x = 1.0f - (float) Math.exp(1.0f - x);
            x = start + x * (1.0f - start);
        }
        return x;
    }

    private static float viscous(float input) {
        float interpolated = VISCOUS_FLUID_NORMALIZE * viscousFluid(input);
        return interpolated > 0 ? interpolated + VISCOUS_FLUID_OFFSET : interpolated;
    }

    private final float physicalCoeff;
    private final float friction = 0.015f;
    private boolean finished = true;
    private boolean spline;
    private int startX, startY, finalX, finalY, currX, currY;
    private int distanceX, distanceY;
    private long startTime;
    private int duration, splineDuration;

    public OverScroller(Context context) {
        float ppi = (context != null ? context.getResources().getDisplayMetrics().density : 1f) * 160.0f;
        physicalCoeff = 9.80665f * 39.37f * ppi * 0.84f;
    }

    public final boolean isFinished() { return finished; }
    public final void forceFinished(boolean f) { finished = f; }
    public final int getCurrX() { return currX; }
    public final int getCurrY() { return currY; }
    public final int getFinalX() { return finalX; }
    public final int getFinalY() { return finalY; }

    public void abortAnimation() {
        currX = finalX;
        currY = finalY;
        finished = true;
    }

    public void startScroll(int sx, int sy, int dx, int dy) { startScroll(sx, sy, dx, dy, 250); }

    public void startScroll(int sx, int sy, int dx, int dy, int d) {
        spline = false;
        finished = false;
        duration = d;
        startTime = android.os.SystemClock.uptimeMillis();
        startX = currX = sx; startY = currY = sy;
        finalX = sx + dx; finalY = sy + dy;
    }

    private double splineDeceleration(int velocity) { return Math.log(INFLEXION * Math.abs(velocity) / (friction * physicalCoeff)); }

    private double splineFlingDistance(int velocity) {
        double l = splineDeceleration(velocity);
        double decelMinusOne = DECELERATION_RATE - 1.0;
        return friction * physicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l);
    }

    private int splineFlingDuration(int velocity) {
        double l = splineDeceleration(velocity);
        double decelMinusOne = DECELERATION_RATE - 1.0;
        return (int) (1000.0 * Math.exp(l / decelMinusOne));
    }

    /** One-axis fling (the scroll views fling on their own axis only). */
    public void fling(int sx, int sy, int vx, int vy, int minX, int maxX, int minY, int maxY) {
        spline = true;
        finished = false;
        startTime = android.os.SystemClock.uptimeMillis();
        startX = currX = sx; startY = currY = sy;
        int velocity = vy != 0 ? vy : vx;
        boolean vertical = vy != 0;
        duration = splineDuration = 0;
        double total = 0;
        if (velocity != 0) {
            duration = splineDuration = splineFlingDuration(velocity);
            total = splineFlingDistance(velocity);
        }
        int dist = (int) (total * Math.signum(velocity));
        int start = vertical ? sy : sx, min = vertical ? minY : minX, max = vertical ? maxY : maxX;
        int fin = start + dist;
        if (fin < min) { adjustDuration(start, fin, min); fin = min; }
        if (fin > max) { adjustDuration(start, fin, max); fin = max; }
        if (vertical) { finalY = fin; finalX = sx; distanceY = dist; distanceX = 0; } else { finalX = fin; finalY = sy; distanceX = dist; distanceY = 0; }
    }

    private void adjustDuration(int start, int oldFinal, int newFinal) {
        int oldDistance = oldFinal - start, newDistance = newFinal - start;
        if (oldDistance == 0) return;
        float x = Math.abs((float) newDistance / oldDistance);
        int index = (int) (NB_SAMPLES * x);
        if (index < NB_SAMPLES) {
            float xInf = (float) index / NB_SAMPLES, xSup = (float) (index + 1) / NB_SAMPLES;
            float tInf = SPLINE_TIME[index], tSup = SPLINE_TIME[index + 1];
            float timeCoef = tInf + (x - xInf) / (xSup - xInf) * (tSup - tInf);
            duration = (int) (duration * timeCoef);
        }
    }

    /** Advances the motion; false when it has finished. */
    public boolean computeScrollOffset() {
        if (finished) return false;
        long t = android.os.SystemClock.uptimeMillis() - startTime;
        if (t >= duration) {
            currX = finalX;
            currY = finalY;
            finished = true;
            return true;
        }
        if (spline) {
            float tt = (float) t / splineDuration;
            int index = (int) (NB_SAMPLES * tt);
            float distanceCoef = 1f;
            if (index < NB_SAMPLES) {
                float tInf = (float) index / NB_SAMPLES, tSup = (float) (index + 1) / NB_SAMPLES;
                float dInf = SPLINE_POSITION[index], dSup = SPLINE_POSITION[index + 1];
                float velocityCoef = (dSup - dInf) / (tSup - tInf);
                distanceCoef = dInf + (tt - tInf) * velocityCoef;
            }
            currX = startX + Math.round(distanceCoef * distanceX);
            currY = startY + Math.round(distanceCoef * distanceY);
            if (distanceX != 0) currX = clampTo(currX, startX, finalX);
            if (distanceY != 0) currY = clampTo(currY, startY, finalY);
        } else {
            float q = viscous((float) t / duration);
            currX = startX + Math.round(q * (finalX - startX));
            currY = startY + Math.round(q * (finalY - startY));
        }
        return true;
    }

    private static int clampTo(int v, int from, int to) { return from <= to ? Math.max(from, Math.min(to, v)) : Math.max(to, Math.min(from, v)); }
}
