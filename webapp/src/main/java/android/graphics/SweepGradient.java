package android.graphics;

import org.teavm.jso.JSObject;

public class SweepGradient extends Shader {
    private final float cx, cy;
    private final int[] colors;
    private final float[] positions;

    public SweepGradient(float cx, float cy, int[] colors, float[] positions) {
        this.cx = cx; this.cy = cy;
        this.colors = colors.clone();
        this.positions = positions(colors.length, positions);
    }

    public SweepGradient(float cx, float cy, int color0, int color1) { this(cx, cy, new int[] {color0, color1}, null); }

    @Override protected JSObject createStyle() { return divan.web.JS.sweepGradient(cx, cy, colors, positions); }
}
