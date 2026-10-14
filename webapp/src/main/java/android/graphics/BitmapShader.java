package android.graphics;

import org.teavm.jso.JSObject;

public class BitmapShader extends Shader {
    private final Bitmap bitmap;
    private final TileMode tileX, tileY;

    public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) { this.bitmap = bitmap; this.tileX = tileX; this.tileY = tileY; }

    @Override public boolean selfTransforms() { return true; }

    @Override protected JSObject createStyle() {
        // CLAMP has no browser equivalent; the photos it is used for always cover the shape, so the edge is never reached.
        JSObject p = divan.web.JS.pattern(bitmap.canvas(), tileX == TileMode.REPEAT || tileX == TileMode.MIRROR, tileY == TileMode.REPEAT || tileY == TileMode.MIRROR);
        Matrix m = localMatrix();
        if (m != null) divan.web.JS.patternTransform(p, m.a, m.b, m.c, m.d, m.e, m.f);
        return p;
    }
}
