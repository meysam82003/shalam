package android.graphics;

import divan.web.JS;
import org.teavm.jso.JSObject;

/**
 * Vector path stored as a command list (move, line, quad, cubic, close) in the app's coordinates, so transform,
 * offset and bounds are exact; ovals, circles and round rects become cubic segments as in Skia. {@link #op} runs
 * a real polygon boolean operation (curves flattened finely), so filled and stroked results match Android.
 */
public class Path {
    public enum Direction { CW, CCW }
    public enum FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
    public enum Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

    static final int MOVE = 0, LINE = 1, QUAD = 2, CUBIC = 3, CLOSE = 4;
    private static final float K = 0.5522847498f; // cubic approximation of a quarter circle

    private float[] cmd = new float[32];
    private int len;
    private float lastMoveX, lastMoveY, curX, curY;
    private boolean hasCurrent;
    private FillType fillType = FillType.WINDING;
    /** Result of a boolean op: rings are filled even-odd (holes keep their own orientation). */
    private boolean opResult;
    private JSObject js;

    public Path() {}

    public Path(Path src) { if (src != null) set(src); }

    public void set(Path src) {
        if (src == this) return;
        cmd = java.util.Arrays.copyOf(src.cmd, Math.max(32, src.len));
        len = src.len;
        lastMoveX = src.lastMoveX; lastMoveY = src.lastMoveY; curX = src.curX; curY = src.curY; hasCurrent = src.hasCurrent;
        fillType = src.fillType; opResult = src.opResult;
        js = src.js;
    }

    public void reset() { len = 0; hasCurrent = false; opResult = false; fillType = FillType.WINDING; js = null; }

    public void rewind() { reset(); }

    public boolean isEmpty() { return len == 0; }

    public FillType getFillType() { return fillType; }

    public void setFillType(FillType ft) { fillType = ft; js = null; }

    public boolean isInverseFillType() { return fillType == FillType.INVERSE_WINDING || fillType == FillType.INVERSE_EVEN_ODD; }

    /** Fill rule the canvas uses for this path. */
    public boolean evenOdd() { return fillType == FillType.EVEN_ODD || fillType == FillType.INVERSE_EVEN_ODD || opResult; }

    private void ensure(int n) {
        if (len + n > cmd.length) cmd = java.util.Arrays.copyOf(cmd, Math.max(cmd.length * 2, len + n));
        js = null;
    }

    private void injectMove() { if (!hasCurrent) moveTo(lastMoveX, lastMoveY); }

    public void moveTo(float x, float y) {
        ensure(3);
        cmd[len++] = MOVE; cmd[len++] = x; cmd[len++] = y;
        lastMoveX = curX = x; lastMoveY = curY = y;
        hasCurrent = true;
    }

    public void rMoveTo(float dx, float dy) { moveTo(curX + dx, curY + dy); }

    public void lineTo(float x, float y) {
        injectMove();
        ensure(3);
        cmd[len++] = LINE; cmd[len++] = x; cmd[len++] = y;
        curX = x; curY = y;
    }

    public void rLineTo(float dx, float dy) { lineTo(curX + dx, curY + dy); }

    public void quadTo(float x1, float y1, float x2, float y2) {
        injectMove();
        ensure(5);
        cmd[len++] = QUAD; cmd[len++] = x1; cmd[len++] = y1; cmd[len++] = x2; cmd[len++] = y2;
        curX = x2; curY = y2;
    }

    public void rQuadTo(float dx1, float dy1, float dx2, float dy2) { quadTo(curX + dx1, curY + dy1, curX + dx2, curY + dy2); }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        injectMove();
        ensure(7);
        cmd[len++] = CUBIC; cmd[len++] = x1; cmd[len++] = y1; cmd[len++] = x2; cmd[len++] = y2; cmd[len++] = x3; cmd[len++] = y3;
        curX = x3; curY = y3;
    }

    public void rCubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        cubicTo(curX + x1, curY + y1, curX + x2, curY + y2, curX + x3, curY + y3);
    }

    public void close() {
        if (len > 0 && cmd[lastCmdIndex()] != CLOSE) {
            ensure(1);
            cmd[len++] = CLOSE;
        }
        curX = lastMoveX; curY = lastMoveY;
        hasCurrent = false;
    }

    private int lastCmdIndex() {
        int i = 0, last = 0;
        while (i < len) { last = i; i += 1 + argCount((int) cmd[i]); }
        return last;
    }

    static int argCount(int c) {
        switch (c) { case MOVE: case LINE: return 2; case QUAD: return 4; case CUBIC: return 6; default: return 0; }
    }

    // ------------------------------------------------------------------ shapes
    public void addRect(RectF r, Direction dir) { addRect(r.left, r.top, r.right, r.bottom, dir); }

    public void addRect(float l, float t, float r, float b, Direction dir) {
        moveTo(l, t);
        if (dir == Direction.CW) { lineTo(r, t); lineTo(r, b); lineTo(l, b); } else { lineTo(l, b); lineTo(r, b); lineTo(r, t); }
        close();
    }

    public void addOval(RectF oval, Direction dir) { addOval(oval.left, oval.top, oval.right, oval.bottom, dir); }

    /** Starts at the right-middle point like Skia, four cubic quarters. */
    public void addOval(float l, float t, float r, float b, Direction dir) {
        float cx = (l + r) / 2, cy = (t + b) / 2, rx = (r - l) / 2, ry = (b - t) / 2;
        float kx = rx * K, ky = ry * K;
        moveTo(r, cy);
        if (dir == Direction.CW) {
            cubicTo(r, cy + ky, cx + kx, b, cx, b);
            cubicTo(cx - kx, b, l, cy + ky, l, cy);
            cubicTo(l, cy - ky, cx - kx, t, cx, t);
            cubicTo(cx + kx, t, r, cy - ky, r, cy);
        } else {
            cubicTo(r, cy - ky, cx + kx, t, cx, t);
            cubicTo(cx - kx, t, l, cy - ky, l, cy);
            cubicTo(l, cy + ky, cx - kx, b, cx, b);
            cubicTo(cx + kx, b, r, cy + ky, r, cy);
        }
        close();
    }

    public void addCircle(float x, float y, float radius, Direction dir) {
        if (radius > 0) addOval(x - radius, y - radius, x + radius, y + radius, dir);
    }

    public void addRoundRect(RectF rect, float rx, float ry, Direction dir) { addRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, dir); }

    public void addRoundRect(float l, float t, float r, float b, float rx, float ry, Direction dir) {
        if (l >= r || t >= b) return;
        rx = Math.max(0, Math.min(rx, (r - l) / 2));
        ry = Math.max(0, Math.min(ry, (b - t) / 2));
        if (rx == 0 || ry == 0) { addRect(l, t, r, b, dir); return; }
        float kx = rx * K, ky = ry * K;
        if (dir == Direction.CW) {
            moveTo(l + rx, t);
            lineTo(r - rx, t);
            cubicTo(r - rx + kx, t, r, t + ry - ky, r, t + ry);
            lineTo(r, b - ry);
            cubicTo(r, b - ry + ky, r - rx + kx, b, r - rx, b);
            lineTo(l + rx, b);
            cubicTo(l + rx - kx, b, l, b - ry + ky, l, b - ry);
            lineTo(l, t + ry);
            cubicTo(l, t + ry - ky, l + rx - kx, t, l + rx, t);
        } else {
            moveTo(l + rx, t);
            cubicTo(l + rx - kx, t, l, t + ry - ky, l, t + ry);
            lineTo(l, b - ry);
            cubicTo(l, b - ry + ky, l + rx - kx, b, l + rx, b);
            lineTo(r - rx, b);
            cubicTo(r - rx + kx, b, r, b - ry + ky, r, b - ry);
            lineTo(r, t + ry);
            cubicTo(r, t + ry - ky, r - rx + kx, t, r - rx, t);
        }
        close();
    }

    public void addRoundRect(RectF rect, float[] radii, Direction dir) {
        float rx = radii.length > 0 ? radii[0] : 0, ry = radii.length > 1 ? radii[1] : rx;
        addRoundRect(rect, rx, ry, dir);
    }

    public void addArc(RectF oval, float startAngle, float sweepAngle) { arcTo(oval, startAngle, sweepAngle, true); }

    public void arcTo(RectF oval, float startAngle, float sweepAngle) { arcTo(oval, startAngle, sweepAngle, false); }

    /** Arc as cubic segments of at most 90 degrees. */
    public void arcTo(RectF oval, float startAngle, float sweepAngle, boolean forceMoveTo) {
        float cx = oval.centerX(), cy = oval.centerY(), rx = oval.width() / 2, ry = oval.height() / 2;
        double a0 = Math.toRadians(startAngle);
        float sx = cx + (float) (rx * Math.cos(a0)), sy = cy + (float) (ry * Math.sin(a0));
        if (forceMoveTo || !hasCurrent) moveTo(sx, sy); else lineTo(sx, sy);
        int segs = Math.max(1, (int) Math.ceil(Math.abs(sweepAngle) / 90f));
        double step = Math.toRadians(sweepAngle) / segs;
        double a = a0;
        for (int i = 0; i < segs; i++) {
            double b = a + step;
            double k = 4.0 / 3.0 * Math.tan((b - a) / 4);
            float x1 = cx + (float) (rx * (Math.cos(a) - k * Math.sin(a))), y1 = cy + (float) (ry * (Math.sin(a) + k * Math.cos(a)));
            float x2 = cx + (float) (rx * (Math.cos(b) + k * Math.sin(b))), y2 = cy + (float) (ry * (Math.sin(b) - k * Math.cos(b)));
            cubicTo(x1, y1, x2, y2, cx + (float) (rx * Math.cos(b)), cy + (float) (ry * Math.sin(b)));
            a = b;
        }
    }

    public void addPath(Path src) { addPath(src, null); }

    public void addPath(Path src, float dx, float dy) {
        Matrix m = new Matrix();
        m.setTranslate(dx, dy);
        addPath(src, m);
    }

    public void addPath(Path src, Matrix m) {
        float[] s = java.util.Arrays.copyOf(src.cmd, src.len);
        if (m != null) mapCommands(s, s.length, m);
        int i = 0;
        while (i < s.length) {
            int c = (int) s[i];
            switch (c) {
                case MOVE: moveTo(s[i + 1], s[i + 2]); break;
                case LINE: lineTo(s[i + 1], s[i + 2]); break;
                case QUAD: quadTo(s[i + 1], s[i + 2], s[i + 3], s[i + 4]); break;
                case CUBIC: cubicTo(s[i + 1], s[i + 2], s[i + 3], s[i + 4], s[i + 5], s[i + 6]); break;
                default: close();
            }
            i += 1 + argCount(c);
        }
        if (src.opResult) opResult = true;
    }

    // ------------------------------------------------------------------ transforms and bounds
    private static void mapCommands(float[] c, int n, Matrix m) {
        int i = 0;
        while (i < n) {
            int k = (int) c[i];
            int args = argCount(k);
            for (int j = 0; j < args; j += 2) {
                float x = c[i + 1 + j], y = c[i + 2 + j];
                c[i + 1 + j] = m.a * x + m.c * y + m.e;
                c[i + 2 + j] = m.b * x + m.d * y + m.f;
            }
            i += 1 + args;
        }
    }

    public void transform(Matrix m) {
        mapCommands(cmd, len, m);
        float x = lastMoveX, y = lastMoveY;
        lastMoveX = m.a * x + m.c * y + m.e; lastMoveY = m.b * x + m.d * y + m.f;
        x = curX; y = curY;
        curX = m.a * x + m.c * y + m.e; curY = m.b * x + m.d * y + m.f;
        js = null;
    }

    public void transform(Matrix m, Path dst) {
        if (dst == null) { transform(m); return; }
        dst.set(this);
        dst.transform(m);
    }

    public void offset(float dx, float dy) {
        Matrix m = new Matrix();
        m.setTranslate(dx, dy);
        transform(m);
    }

    public void offset(float dx, float dy, Path dst) {
        if (dst == null) { offset(dx, dy); return; }
        dst.set(this);
        dst.offset(dx, dy);
    }

    /** Bounds of all points, control points included (as Android's computeBounds). */
    public void computeBounds(RectF bounds, boolean exact) {
        if (len == 0) { bounds.set(0, 0, 0, 0); return; }
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        int i = 0;
        while (i < len) {
            int k = (int) cmd[i];
            int args = argCount(k);
            for (int j = 0; j < args; j += 2) {
                float x = cmd[i + 1 + j], y = cmd[i + 2 + j];
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > b) b = y;
            }
            i += 1 + args;
        }
        bounds.set(l, t, r, b);
    }

    // ------------------------------------------------------------------ boolean operations
    /** Contours flattened to polygons: [ringCount, n, x0, y0, ..., n, ...]. */
    private float[] rings() {
        float[] out = new float[64];
        int o = 1, rings = 0, countAt = -1, pts = 0;
        float px = 0, py = 0;
        int i = 0;
        while (i < len) {
            int k = (int) cmd[i];
            if (k == MOVE) {
                if (countAt >= 0) out[countAt] = pts;
                out = grow(out, o + 3);
                countAt = o++;
                rings++;
                pts = 0;
                px = cmd[i + 1]; py = cmd[i + 2];
                out[o++] = px; out[o++] = py; pts++;
            } else if (k == LINE) {
                out = grow(out, o + 2);
                px = cmd[i + 1]; py = cmd[i + 2];
                out[o++] = px; out[o++] = py; pts++;
            } else if (k == QUAD || k == CUBIC) {
                float x1 = cmd[i + 1], y1 = cmd[i + 2], x2 = cmd[i + 3], y2 = cmd[i + 4];
                float x3 = k == CUBIC ? cmd[i + 5] : x2, y3 = k == CUBIC ? cmd[i + 6] : y2;
                if (k == QUAD) { // degree elevation to a cubic
                    float qx = x1, qy = y1;
                    x1 = px + 2f / 3f * (qx - px); y1 = py + 2f / 3f * (qy - py);
                    x2 = x3 + 2f / 3f * (qx - x3); y2 = y3 + 2f / 3f * (qy - y3);
                }
                float span = Math.abs(x1 - px) + Math.abs(y1 - py) + Math.abs(x2 - x1) + Math.abs(y2 - y1) + Math.abs(x3 - x2) + Math.abs(y3 - y2);
                int steps = Math.max(4, Math.min(64, (int) Math.ceil(span / 1.5f)));
                out = grow(out, o + steps * 2);
                for (int s = 1; s <= steps; s++) {
                    float t = s / (float) steps, u = 1 - t;
                    float x = u * u * u * px + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
                    float y = u * u * u * py + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3;
                    out[o++] = x; out[o++] = y; pts++;
                }
                px = x3; py = y3;
            }
            i += 1 + argCount(k);
        }
        if (countAt >= 0) out[countAt] = pts;
        out[0] = rings;
        return java.util.Arrays.copyOf(out, o);
    }

    private static float[] grow(float[] a, int need) { return need <= a.length ? a : java.util.Arrays.copyOf(a, Math.max(a.length * 2, need)); }

    public boolean op(Path path, Op op) { return op(this, path, op); }

    public boolean op(Path path1, Path path2, Op op) {
        int code;
        switch (op) {
            case DIFFERENCE: code = 0; break;
            case INTERSECT: code = 1; break;
            case UNION: code = 2; break;
            case XOR: code = 3; break;
            default: code = 4;
        }
        float[] result = JS.pathOp(path1.rings(), path2.rings(), code);
        reset();
        int o = 1;
        int rings = (int) result[0];
        for (int r = 0; r < rings; r++) {
            int n = (int) result[o++];
            for (int p = 0; p < n; p++) {
                if (p == 0) moveTo(result[o], result[o + 1]); else lineTo(result[o], result[o + 1]);
                o += 2;
            }
            if (n > 0) close();
        }
        opResult = true;
        return true;
    }

    // ------------------------------------------------------------------ browser path
    /** Canvas Path2D of the commands, rebuilt only after a change. */
    public JSObject js() {
        if (js == null) js = JS.path2d(java.util.Arrays.copyOf(cmd, len));
        return js;
    }
}
