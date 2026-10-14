package android.graphics;

import org.teavm.jso.JSObject;

public class LinearGradient extends Shader {
    private final float x0, y0, x1, y1;
    private final int[] colors;
    private final float[] positions;

    public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions, TileMode tile) {
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        this.colors = colors.clone();
        this.positions = positions(colors.length, positions);
    }

    public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1, TileMode tile) {
        this(x0, y0, x1, y1, new int[] {color0, color1}, null, tile);
    }

    @Override protected JSObject createStyle() { return divan.web.JS.linearGradient(x0, y0, x1, y1, colors, positions != null ? positions : new float[0]); }
}
