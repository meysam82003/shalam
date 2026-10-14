package android.graphics;

import org.teavm.jso.JSObject;

/** Base of the gradient and bitmap shaders: produces a browser canvas style once and reuses it. */
public class Shader {
    public enum TileMode { CLAMP, REPEAT, MIRROR, DECAL }

    private Matrix local;
    private JSObject style;

    public void setLocalMatrix(Matrix localM) {
        local = localM == null || localM.isIdentity() ? null : new Matrix(localM);
        style = null;
    }

    public boolean getLocalMatrix(Matrix out) {
        if (local == null) { out.reset(); return false; }
        out.set(local);
        return true;
    }

    /** Local matrix of the shader, or null. */
    public Matrix localMatrix() { return local; }

    /** Browser style (gradient or pattern) in the shader's own space. */
    public JSObject style() {
        if (style == null) style = createStyle();
        return style;
    }

    /** True when the style can carry the local matrix itself (patterns); gradients are drawn in a transformed space. */
    public boolean selfTransforms() { return false; }

    protected JSObject createStyle() { return divan.web.JS.str("rgba(0,0,0,0)"); }

    static float[] positions(int count, float[] positions) {
        if (positions != null) return positions;
        float[] p = new float[count];
        for (int i = 0; i < count; i++) p[i] = count == 1 ? 0 : i / (float) (count - 1);
        return p;
    }
}
