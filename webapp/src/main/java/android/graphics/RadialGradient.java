package android.graphics;

import org.teavm.jso.JSObject;

public class RadialGradient extends Shader {
    private final float cx, cy, radius;
    private final int[] colors;
    private final float[] positions;

    public RadialGradient(float centerX, float centerY, float radius, int[] colors, float[] stops, TileMode tileMode) {
        if (radius <= 0) throw new IllegalArgumentException("radius must be > 0");
        this.cx = centerX; this.cy = centerY; this.radius = radius;
        this.colors = colors.clone();
        this.positions = positions(colors.length, stops);
    }

    public RadialGradient(float centerX, float centerY, float radius, int centerColor, int edgeColor, TileMode tileMode) {
        this(centerX, centerY, radius, new int[] {centerColor, edgeColor}, null, tileMode);
    }

    @Override protected JSObject createStyle() { return divan.web.JS.radialGradient(cx, cy, radius, colors, positions); }
}
