package android.graphics;

public class DashPathEffect extends PathEffect {
    final float[] intervals;
    final float phase;

    public DashPathEffect(float[] intervals, float phase) {
        if (intervals.length < 2) throw new ArrayIndexOutOfBoundsException();
        this.intervals = intervals.clone();
        this.phase = phase;
    }
}
