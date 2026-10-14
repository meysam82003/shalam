package android.graphics;

import divan.web.JS;
import org.teavm.jso.JSObject;

/**
 * android.graphics.Canvas on a browser 2D context. Coordinates and sizes are device pixels; the view system draws
 * with the same calls the app's own views use. Alpha of views (and saveLayerAlpha) is carried as a multiplier.
 */
public class Canvas {
    public static final int ALL_SAVE_FLAG = 0x1F;

    private JSObject ctx;
    private Bitmap bitmap;
    private int width, height;
    private int saveCount = 1;
    private float[] alphaStack = new float[32];
    private float alpha = 1f;

    public Canvas() {}

    public Canvas(Bitmap bitmap) { setBitmap(bitmap); }

    /** Canvas over a browser context (the screen or an offscreen layer). */
    public Canvas(JSObject context, int width, int height) {
        this.ctx = context; this.width = width; this.height = height;
    }

    public void setBitmap(Bitmap b) {
        bitmap = b;
        if (b == null) { ctx = null; width = height = 0; return; }
        if (!b.isMutable()) throw new IllegalStateException("Immutable bitmap passed to Canvas constructor");
        ctx = JS.context2d(b.canvas());
        width = b.getWidth(); height = b.getHeight();
        JS.smoothing(ctx, true);
    }

    public JSObject context() { return ctx; }

    /** Resets the canvas for a new frame of the given size (screen canvas). */
    public void reset(int w, int h) {
        width = w; height = h;
        saveCount = 1;
        alpha = 1f;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isHardwareAccelerated() { return true; }
    public int getDensity() { return 160; }
    public void setDensity(int density) {}

    private void touched() { if (bitmap != null) bitmap.touched(); }

    // ------------------------------------------------------------------ state
    public int save() {
        if (saveCount >= alphaStack.length) alphaStack = java.util.Arrays.copyOf(alphaStack, alphaStack.length * 2);
        alphaStack[saveCount] = alpha;
        JS.save(ctx);
        return saveCount++;
    }

    public int save(int flags) { return save(); }

    public int saveLayerAlpha(RectF bounds, int a) { int c = save(); alpha *= Math.max(0, Math.min(255, a)) / 255f; return c; }
    public int saveLayerAlpha(float l, float t, float r, float b, int a) { return saveLayerAlpha((RectF) null, a); }
    public int saveLayerAlpha(RectF bounds, int a, int flags) { return saveLayerAlpha(bounds, a); }
    public int saveLayer(RectF bounds, Paint paint) { int c = save(); if (paint != null) alpha *= paint.getAlpha() / 255f; return c; }
    public int saveLayer(RectF bounds, Paint paint, int flags) { return saveLayer(bounds, paint); }
    public int saveLayer(float l, float t, float r, float b, Paint paint) { return saveLayer((RectF) null, paint); }

    public void restore() {
        if (saveCount <= 1) throw new IllegalStateException("Underflow in restore - more restores than saves");
        saveCount--;
        alpha = alphaStack[saveCount];
        JS.restore(ctx);
    }

    public int getSaveCount() { return saveCount; }

    public void restoreToCount(int count) {
        if (count < 1) count = 1;
        while (saveCount > count) restore();
    }

    /** Extra alpha for everything drawn until the matching restore (view alpha). */
    public void multiplyAlpha(float a) { alpha *= a; }

    public float alpha() { return alpha; }

    public void translate(float dx, float dy) { if (dx != 0 || dy != 0) JS.translate(ctx, dx, dy); }
    public void scale(float sx, float sy) { JS.scale(ctx, sx, sy); }

    public final void scale(float sx, float sy, float px, float py) {
        translate(px, py);
        scale(sx, sy);
        translate(-px, -py);
    }

    public void rotate(float degrees) { JS.rotate(ctx, (float) Math.toRadians(degrees)); }

    public final void rotate(float degrees, float px, float py) {
        translate(px, py);
        rotate(degrees);
        translate(-px, -py);
    }

    public void skew(float sx, float sy) { JS.transform(ctx, 1, sy, sx, 1, 0, 0); }

    public void concat(Matrix m) { if (m != null && !m.isIdentity()) JS.transform(ctx, m.a, m.b, m.c, m.d, m.e, m.f); }

    public void setMatrix(Matrix m) {
        if (m == null) JS.setTransform(ctx, 1, 0, 0, 1, 0, 0); else JS.setTransform(ctx, m.a, m.b, m.c, m.d, m.e, m.f);
    }

    public void getMatrix(Matrix out) {
        float[] t = JS.getTransform(ctx);
        out.a = t[0]; out.b = t[1]; out.c = t[2]; out.d = t[3]; out.e = t[4]; out.f = t[5];
    }

    public boolean clipRect(float l, float t, float r, float b) { JS.clipRect(ctx, l, t, r, b); return r > l && b > t; }
    public boolean clipRect(int l, int t, int r, int b) { return clipRect((float) l, (float) t, (float) r, (float) b); }
    public boolean clipRect(RectF r) { return clipRect(r.left, r.top, r.right, r.bottom); }
    public boolean clipRect(Rect r) { return clipRect((float) r.left, r.top, r.right, r.bottom); }
    public boolean clipPath(Path path) { JS.clipPath(ctx, path.js(), path.evenOdd()); return true; }
    public boolean clipOutRect(float l, float t, float r, float b) { return true; }

    public boolean getClipBounds(Rect bounds) { bounds.set(0, 0, width, height); return true; }

    public boolean quickReject(RectF rect) { return false; }
    public boolean quickReject(float l, float t, float r, float b) { return false; }

    // ------------------------------------------------------------------ paint application
    private boolean fills(Paint p) { return p.getStyle() != Paint.Style.STROKE; }
    private boolean strokes(Paint p) { return p.getStyle() != Paint.Style.FILL; }

    private void fillState(Paint p) { JS.fillStyle(ctx, p.canvasStyle(), p.canvasAlpha() * alpha); }

    private void strokeState(Paint p) {
        float w = p.getStrokeWidth();
        JS.strokeStyle(ctx, p.canvasStyle(), p.canvasAlpha() * alpha, w <= 0 ? 1f : w, p.capCss(), p.joinCss(), p.getStrokeMiter(), p.dash() != null ? p.dash() : new float[0], p.dashPhase());
    }

    /** Gradients cannot carry a matrix: draw in the shader's space with the path mapped back. */
    private Matrix gradientMatrix(Paint p) {
        Shader s = p.getShader();
        if (s == null || s.selfTransforms()) return null;
        return s.localMatrix();
    }

    private void drawJsPath(JSObject path, boolean evenOdd, Paint p) {
        Matrix m = gradientMatrix(p);
        if (m != null) {
            Matrix inv = new Matrix();
            if (m.invert(inv)) {
                JSObject q = JS.transformPath(path, inv.a, inv.b, inv.c, inv.d, inv.e, inv.f);
                JS.save(ctx);
                JS.transform(ctx, m.a, m.b, m.c, m.d, m.e, m.f);
                if (fills(p)) { fillState(p); JS.fillPath(ctx, q, evenOdd); }
                if (strokes(p)) { strokeState(p); JS.strokePath(ctx, q); }
                JS.restore(ctx);
                touched();
                return;
            }
        }
        if (fills(p)) { fillState(p); JS.fillPath(ctx, path, evenOdd); }
        if (strokes(p)) { strokeState(p); JS.strokePath(ctx, path); }
        touched();
    }

    // ------------------------------------------------------------------ drawing
    public void drawColor(int color) {
        if ((color >>> 24) == 0) return;
        JS.fillStyle(ctx, JS.str(Color.css(color)), alpha);
        JS.save(ctx);
        JS.setTransform(ctx, 1, 0, 0, 1, 0, 0);
        JS.fillRect(ctx, 0, 0, width, height);
        JS.restore(ctx);
        touched();
    }

    public void drawARGB(int a, int r, int g, int b) { drawColor(Color.argb(a, r, g, b)); }
    public void drawRGB(int r, int g, int b) { drawColor(Color.rgb(r, g, b)); }

    public void drawPaint(Paint p) {
        JS.save(ctx);
        JS.setTransform(ctx, 1, 0, 0, 1, 0, 0);
        fillState(p);
        JS.fillRect(ctx, 0, 0, width, height);
        JS.restore(ctx);
        touched();
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        if (gradientMatrix(p) != null) {
            Path path = new Path();
            path.addRect(l, t, r, b, Path.Direction.CW);
            drawPath(path, p);
            return;
        }
        if (fills(p)) { fillState(p); JS.fillRect(ctx, l, t, r, b); }
        if (strokes(p)) { strokeState(p); JS.strokeRect(ctx, l, t, r, b); }
        touched();
    }

    public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }
    public void drawRect(Rect r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }

    public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) {
        if (gradientMatrix(p) != null) {
            Path path = new Path();
            path.addRoundRect(l, t, r, b, rx, ry, Path.Direction.CW);
            drawPath(path, p);
            return;
        }
        if (fills(p)) { fillState(p); JS.roundRect(ctx, l, t, r, b, rx, ry, false); }
        if (strokes(p)) { strokeState(p); JS.roundRect(ctx, l, t, r, b, rx, ry, true); }
        touched();
    }

    public void drawRoundRect(RectF r, float rx, float ry, Paint p) { drawRoundRect(r.left, r.top, r.right, r.bottom, rx, ry, p); }

    public void drawOval(float l, float t, float r, float b, Paint p) {
        if (gradientMatrix(p) != null) {
            Path path = new Path();
            path.addOval(l, t, r, b, Path.Direction.CW);
            drawPath(path, p);
            return;
        }
        float cx = (l + r) / 2, cy = (t + b) / 2, rx = (r - l) / 2, ry = (b - t) / 2;
        if (fills(p)) { fillState(p); JS.ellipse(ctx, cx, cy, rx, ry, false); }
        if (strokes(p)) { strokeState(p); JS.ellipse(ctx, cx, cy, rx, ry, true); }
        touched();
    }

    public void drawOval(RectF r, Paint p) { drawOval(r.left, r.top, r.right, r.bottom, p); }

    public void drawCircle(float cx, float cy, float radius, Paint p) {
        if (radius <= 0) return;
        drawOval(cx - radius, cy - radius, cx + radius, cy + radius, p);
    }

    public void drawArc(RectF oval, float startAngle, float sweepAngle, boolean useCenter, Paint p) {
        drawArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, useCenter, p);
    }

    public void drawArc(float l, float t, float r, float b, float startAngle, float sweepAngle, boolean useCenter, Paint p) {
        if (sweepAngle == 0) return;
        float cx = (l + r) / 2, cy = (t + b) / 2, rx = (r - l) / 2, ry = (b - t) / 2;
        if (gradientMatrix(p) != null) {
            Path path = new Path();
            if (useCenter) path.moveTo(cx, cy);
            path.arcTo(new RectF(l, t, r, b), startAngle, sweepAngle, !useCenter);
            if (useCenter) path.close();
            drawPath(path, p);
            return;
        }
        if (fills(p)) { fillState(p); JS.arc(ctx, cx, cy, rx, ry, startAngle, sweepAngle, useCenter, false); }
        if (strokes(p)) { strokeState(p); JS.arc(ctx, cx, cy, rx, ry, startAngle, sweepAngle, useCenter, true); }
        touched();
    }

    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        strokeState(p);
        JS.line(ctx, x0, y0, x1, y1);
        touched();
    }

    public void drawLines(float[] pts, int offset, int count, Paint p) {
        for (int i = offset; i + 3 < offset + count; i += 4) drawLine(pts[i], pts[i + 1], pts[i + 2], pts[i + 3], p);
    }

    public void drawLines(float[] pts, Paint p) { drawLines(pts, 0, pts.length, p); }

    public void drawPoint(float x, float y, Paint p) {
        float w = Math.max(1, p.getStrokeWidth());
        fillState(p);
        if (p.getStrokeCap() == Paint.Cap.ROUND) JS.ellipse(ctx, x, y, w / 2, w / 2, false); else JS.fillRect(ctx, x - w / 2, y - w / 2, x + w / 2, y + w / 2);
        touched();
    }

    public void drawPath(Path path, Paint p) {
        if (path.isEmpty()) return;
        drawJsPath(path.js(), path.evenOdd(), p);
    }

    public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        if (bitmap == null || bitmap.isRecycled()) return;
        JS.globalAlpha(ctx, (paint == null ? 1f : paint.getAlpha() / 255f) * alpha);
        JS.drawImage(ctx, bitmap.source(), left, top);
        touched();
    }

    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        if (bitmap == null || bitmap.isRecycled()) return;
        float sx = src == null ? 0 : src.left, sy = src == null ? 0 : src.top;
        float sw = src == null ? bitmap.getWidth() : src.width(), sh = src == null ? bitmap.getHeight() : src.height();
        JS.globalAlpha(ctx, (paint == null ? 1f : paint.getAlpha() / 255f) * alpha);
        JS.drawImageRect(ctx, bitmap.source(), sx, sy, sw, sh, dst.left, dst.top, dst.width(), dst.height());
        touched();
    }

    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) { drawBitmap(bitmap, src, new RectF(dst), paint); }

    public void drawBitmap(Bitmap bitmap, Matrix matrix, Paint paint) {
        if (bitmap == null || bitmap.isRecycled()) return;
        JS.save(ctx);
        concat(matrix);
        JS.globalAlpha(ctx, (paint == null ? 1f : paint.getAlpha() / 255f) * alpha);
        JS.drawImage(ctx, bitmap.source(), 0, 0);
        JS.restore(ctx);
        touched();
    }

    /** Draws a browser image or canvas directly (used by the view system for cached layers). */
    public void drawImage(JSObject image, float left, float top, float w, float h) {
        JS.globalAlpha(ctx, alpha);
        JS.drawImageRect(ctx, image, 0, 0, JS.imageWidth(image), JS.imageHeight(image), left, top, w, h);
    }

    public void drawText(String text, float x, float y, Paint p) {
        if (text == null || text.isEmpty()) return;
        JS.font(ctx, p.cssFont());
        String align = p.getTextAlign() == Paint.Align.CENTER ? "center" : p.getTextAlign() == Paint.Align.RIGHT ? "right" : "left";
        String dir = Paint.direction(text);
        float spacing = p.getLetterSpacing() * p.getTextSize();
        if (fills(p)) { fillState(p); JS.text(ctx, text, x, y, align, dir, false, spacing); }
        if (strokes(p)) { strokeState(p); JS.text(ctx, text, x, y, align, dir, true, spacing); }
        touched();
    }

    public void drawText(CharSequence text, int start, int end, float x, float y, Paint p) { drawText(text.subSequence(start, end).toString(), x, y, p); }

    public void drawText(String text, int start, int end, float x, float y, Paint p) { drawText(text.substring(start, end), x, y, p); }

    public void drawText(char[] text, int index, int count, float x, float y, Paint p) { drawText(new String(text, index, count), x, y, p); }

    /** Text with an explicit paragraph direction and physical alignment (TextView lines). */
    public void drawTextRun(String text, float x, float y, String align, boolean rtl, Paint p) {
        if (text == null || text.isEmpty()) return;
        JS.font(ctx, p.cssFont());
        float spacing = p.getLetterSpacing() * p.getTextSize();
        fillState(p);
        JS.text(ctx, text, x, y, align, rtl ? "rtl" : "ltr", false, spacing);
        touched();
    }
}
