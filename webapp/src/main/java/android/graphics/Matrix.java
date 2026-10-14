package android.graphics;

/**
 * Affine 3x3 matrix with Android's semantics: x' = sx*x + kx*y + tx, y' = ky*x + sy*y + ty.
 * «pre» multiplies on the right (applied first), «post» on the left (applied last).
 */
public class Matrix {
    public static final int MSCALE_X = 0, MSKEW_X = 1, MTRANS_X = 2, MSKEW_Y = 3, MSCALE_Y = 4, MTRANS_Y = 5, MPERSP_0 = 6, MPERSP_1 = 7, MPERSP_2 = 8;

    public enum ScaleToFit { FILL, START, CENTER, END }

    // a=sx, b=ky, c=kx, d=sy, e=tx, f=ty (DOMMatrix order)
    public float a = 1, b = 0, c = 0, d = 1, e = 0, f = 0;

    public Matrix() {}

    public Matrix(Matrix src) { if (src != null) set(src); }

    public void set(Matrix m) { if (m == null) { reset(); return; } a = m.a; b = m.b; c = m.c; d = m.d; e = m.e; f = m.f; }

    public void reset() { a = 1; b = 0; c = 0; d = 1; e = 0; f = 0; }

    public boolean isIdentity() { return a == 1 && b == 0 && c == 0 && d == 1 && e == 0 && f == 0; }

    private void setValues6(float na, float nb, float nc, float nd, float ne, float nf) { a = na; b = nb; c = nc; d = nd; e = ne; f = nf; }

    /** this = m1 * m2 (m2 applied first). */
    private void setConcat6(float a1, float b1, float c1, float d1, float e1, float f1, float a2, float b2, float c2, float d2, float e2, float f2) {
        setValues6(a1 * a2 + c1 * b2, b1 * a2 + d1 * b2, a1 * c2 + c1 * d2, b1 * c2 + d1 * d2, a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1);
    }

    private void post6(float na, float nb, float nc, float nd, float ne, float nf) { setConcat6(na, nb, nc, nd, ne, nf, a, b, c, d, e, f); }

    private void pre6(float na, float nb, float nc, float nd, float ne, float nf) { setConcat6(a, b, c, d, e, f, na, nb, nc, nd, ne, nf); }

    public void setTranslate(float dx, float dy) { setValues6(1, 0, 0, 1, dx, dy); }
    public void setScale(float sx, float sy) { setValues6(sx, 0, 0, sy, 0, 0); }
    public void setScale(float sx, float sy, float px, float py) { setValues6(sx, 0, 0, sy, px - sx * px, py - sy * py); }

    public void setRotate(float degrees) { setRotate(degrees, 0, 0); }

    public void setRotate(float degrees, float px, float py) {
        double rad = Math.toRadians(degrees);
        float sin = (float) Math.sin(rad), cos = (float) Math.cos(rad);
        if (Math.abs(sin) < 1e-7f) sin = 0;
        if (Math.abs(cos) < 1e-7f) cos = 0;
        // translate(px,py) * rotate * translate(-px,-py)
        setValues6(cos, sin, -sin, cos, px - cos * px + sin * py, py - sin * px - cos * py);
    }

    public boolean postTranslate(float dx, float dy) { e += dx; f += dy; return true; }
    public boolean postScale(float sx, float sy) { post6(sx, 0, 0, sy, 0, 0); return true; }
    public boolean postScale(float sx, float sy, float px, float py) { post6(sx, 0, 0, sy, px - sx * px, py - sy * py); return true; }

    public boolean postRotate(float degrees) { return postRotate(degrees, 0, 0); }

    public boolean postRotate(float degrees, float px, float py) {
        Matrix r = new Matrix();
        r.setRotate(degrees, px, py);
        post6(r.a, r.b, r.c, r.d, r.e, r.f);
        return true;
    }

    public boolean postConcat(Matrix m) { post6(m.a, m.b, m.c, m.d, m.e, m.f); return true; }
    public boolean preTranslate(float dx, float dy) { pre6(1, 0, 0, 1, dx, dy); return true; }
    public boolean preScale(float sx, float sy) { pre6(sx, 0, 0, sy, 0, 0); return true; }
    public boolean preScale(float sx, float sy, float px, float py) { pre6(sx, 0, 0, sy, px - sx * px, py - sy * py); return true; }

    public boolean preRotate(float degrees, float px, float py) {
        Matrix r = new Matrix();
        r.setRotate(degrees, px, py);
        pre6(r.a, r.b, r.c, r.d, r.e, r.f);
        return true;
    }

    public boolean preRotate(float degrees) { return preRotate(degrees, 0, 0); }
    public boolean preConcat(Matrix m) { pre6(m.a, m.b, m.c, m.d, m.e, m.f); return true; }

    public boolean setConcat(Matrix m1, Matrix m2) { setConcat6(m1.a, m1.b, m1.c, m1.d, m1.e, m1.f, m2.a, m2.b, m2.c, m2.d, m2.e, m2.f); return true; }

    /** Same result as Skia's setRectToRect for the scale-to-fit modes. */
    public boolean setRectToRect(RectF src, RectF dst, ScaleToFit stf) {
        if (src.isEmpty()) { reset(); return false; }
        if (dst.isEmpty()) { setValues6(0, 0, 0, 0, 0, 0); return true; }
        float sx = dst.width() / src.width();
        float sy = dst.height() / src.height();
        boolean xLarger = false;
        if (stf != ScaleToFit.FILL) {
            if (sx > sy) { xLarger = true; sx = sy; } else { sy = sx; }
        }
        float tx = dst.left - src.left * sx;
        float ty = dst.top - src.top * sy;
        if (stf == ScaleToFit.CENTER || stf == ScaleToFit.END) {
            float diff;
            if (xLarger) diff = dst.width() - src.width() * sy; else diff = dst.height() - src.height() * sy;
            if (stf == ScaleToFit.CENTER) diff = diff / 2;
            if (xLarger) tx += diff; else ty += diff;
        }
        setValues6(sx, 0, 0, sy, tx, ty);
        return true;
    }

    public boolean invert(Matrix inverse) {
        float det = a * d - b * c;
        if (det == 0) return false;
        float ia = d / det, ib = -b / det, ic = -c / det, id = a / det;
        float ie = -(ia * e + ic * f), iff = -(ib * e + id * f);
        if (inverse != null) inverse.setValues6(ia, ib, ic, id, ie, iff);
        return true;
    }

    public void mapPoints(float[] pts) { mapPoints(pts, 0, pts, 0, pts.length / 2); }

    public void mapPoints(float[] dst, float[] src) { mapPoints(dst, 0, src, 0, src.length / 2); }

    public void mapPoints(float[] dst, int dstIndex, float[] src, int srcIndex, int pointCount) {
        for (int i = 0; i < pointCount; i++) {
            float x = src[srcIndex + i * 2], y = src[srcIndex + i * 2 + 1];
            dst[dstIndex + i * 2] = a * x + c * y + e;
            dst[dstIndex + i * 2 + 1] = b * x + d * y + f;
        }
    }

    public boolean mapRect(RectF dst, RectF src) {
        float[] p = {src.left, src.top, src.right, src.top, src.right, src.bottom, src.left, src.bottom};
        mapPoints(p);
        float l = Math.min(Math.min(p[0], p[2]), Math.min(p[4], p[6]));
        float r = Math.max(Math.max(p[0], p[2]), Math.max(p[4], p[6]));
        float t = Math.min(Math.min(p[1], p[3]), Math.min(p[5], p[7]));
        float bt = Math.max(Math.max(p[1], p[3]), Math.max(p[5], p[7]));
        dst.set(l, t, r, bt);
        return b == 0 && c == 0;
    }

    public boolean mapRect(RectF rect) { return mapRect(rect, rect); }

    public void getValues(float[] values) {
        values[0] = a; values[1] = c; values[2] = e; values[3] = b; values[4] = d; values[5] = f; values[6] = 0; values[7] = 0; values[8] = 1;
    }

    public void setValues(float[] v) { setValues6(v[0], v[3], v[1], v[4], v[2], v[5]); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Matrix)) return false;
        Matrix m = (Matrix) o;
        return a == m.a && b == m.b && c == m.c && d == m.d && e == m.e && f == m.f;
    }

    @Override public int hashCode() { return Float.floatToIntBits(a) ^ Float.floatToIntBits(d) * 31 ^ Float.floatToIntBits(e) * 17 ^ Float.floatToIntBits(f); }
}
