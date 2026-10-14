package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/** Port of the parts of AOSP GradientDrawable the app uses (shapes, solid/gradient fill, corner radius, stroke). */
public class GradientDrawable extends Drawable {
    public static final int RECTANGLE = 0, OVAL = 1, LINE = 2, RING = 3;
    public static final int LINEAR_GRADIENT = 0, RADIAL_GRADIENT = 1, SWEEP_GRADIENT = 2;

    public enum Orientation { TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR }

    private int shape = RECTANGLE;
    private int gradient = LINEAR_GRADIENT;
    private Orientation orientation = Orientation.TOP_BOTTOM;
    private int[] colors;
    private float[] positions;
    private Integer solid;
    private float radius;
    private float[] radii;
    private float gradientRadius = 0.5f;
    private float centerX = 0.5f, centerY = 0.5f;
    private int alpha = 255;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint strokePaint;
    private final RectF rect = new RectF();
    private boolean dirty = true;

    public GradientDrawable() { this(Orientation.TOP_BOTTOM, null); }

    public GradientDrawable(Orientation orientation, int[] colors) {
        this.orientation = orientation;
        setColors(colors);
    }

    public void setShape(int shape) { this.shape = shape; invalidateSelf(); }
    public void setGradientType(int type) { gradient = type; dirty = true; invalidateSelf(); }
    public int getGradientType() { return gradient; }
    public int getShape() { return shape; }
    public float getGradientRadius() { return gradientRadius; }
    public void setGradientRadius(float r) { gradientRadius = r; dirty = true; invalidateSelf(); }
    public void setGradientCenter(float x, float y) { centerX = x; centerY = y; dirty = true; invalidateSelf(); }
    public void setOrientation(Orientation o) { orientation = o; dirty = true; invalidateSelf(); }
    public Orientation getOrientation() { return orientation; }
    public void setCornerRadius(float r) { radius = Math.max(0, r); radii = null; invalidateSelf(); }
    public float getCornerRadius() { return radius; }
    public void setCornerRadii(float[] r) { radii = r; invalidateSelf(); }

    public void setColors(int[] colors) {
        this.colors = colors;
        this.positions = null;
        if (colors != null) solid = null;
        dirty = true;
        invalidateSelf();
    }

    public void setColors(int[] colors, float[] offsets) { setColors(colors); positions = offsets; }

    public int[] getColors() { return colors == null ? null : colors.clone(); }

    public void setColor(int argb) {
        solid = argb;
        colors = null;
        fillPaint.setShader(null);
        fillPaint.setColor(argb);
        invalidateSelf();
    }

    public void setStroke(int width, int color) { setStroke(width, color, 0, 0); }

    public void setStroke(int width, int color, float dashWidth, float dashGap) {
        if (strokePaint == null) {
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
        }
        strokePaint.setStrokeWidth(width);
        strokePaint.setColor(color);
        strokePaint.setPathEffect(dashWidth > 0 ? new android.graphics.DashPathEffect(new float[] {dashWidth, dashGap}, 0) : null);
        dirty = true;
        invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect r) { dirty = true; }

    private boolean ensureValidRect() {
        if (dirty) {
            dirty = false;
            Rect b = getBounds();
            float inset = strokePaint != null ? strokePaint.getStrokeWidth() * 0.5f : 0;
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            if (colors != null) {
                RectF r = rect;
                if (gradient == LINEAR_GRADIENT) {
                    float x0, y0, x1, y1;
                    switch (orientation) {
                        case TOP_BOTTOM: x0 = r.left; y0 = r.top; x1 = x0; y1 = r.bottom; break;
                        case TR_BL: x0 = r.right; y0 = r.top; x1 = r.left; y1 = r.bottom; break;
                        case RIGHT_LEFT: x0 = r.right; y0 = r.top; x1 = r.left; y1 = y0; break;
                        case BR_TL: x0 = r.right; y0 = r.bottom; x1 = r.left; y1 = r.top; break;
                        case BOTTOM_TOP: x0 = r.left; y0 = r.bottom; x1 = x0; y1 = r.top; break;
                        case BL_TR: x0 = r.left; y0 = r.bottom; x1 = r.right; y1 = r.top; break;
                        case LEFT_RIGHT: x0 = r.left; y0 = r.top; x1 = r.right; y1 = y0; break;
                        default: x0 = r.left; y0 = r.top; x1 = r.right; y1 = r.bottom;
                    }
                    if (x0 != x1 || y0 != y1) fillPaint.setShader(new LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP));
                    else fillPaint.setShader(null);
                } else if (gradient == RADIAL_GRADIENT) {
                    float x0 = r.left + r.width() * centerX, y0 = r.top + r.height() * centerY;
                    float rad = gradientRadius <= 0 ? 0.001f : gradientRadius;
                    fillPaint.setShader(new RadialGradient(x0, y0, rad, colors, null, Shader.TileMode.CLAMP));
                } else {
                    float x0 = r.left + r.width() * centerX, y0 = r.top + r.height() * centerY;
                    fillPaint.setShader(new SweepGradient(x0, y0, colors, null));
                }
                fillPaint.setColor(0xFF000000);
            }
        }
        return !rect.isEmpty();
    }

    private int modulate(int a) { return a * (alpha + (alpha >> 7)) >> 8; }

    @Override public void draw(Canvas canvas) {
        if (!ensureValidRect()) return;
        int prevFill = fillPaint.getAlpha();
        int prevStroke = strokePaint != null ? strokePaint.getAlpha() : 0;
        int fillA = modulate(prevFill), strokeA = modulate(prevStroke);
        boolean haveStroke = strokeA > 0 && strokePaint != null && strokePaint.getStrokeWidth() > 0;
        boolean haveFill = fillA > 0 && (colors != null || solid != null);
        fillPaint.setAlpha(fillA);
        if (haveStroke) strokePaint.setAlpha(strokeA);
        switch (shape) {
            case RECTANGLE:
                if (radii != null) {
                    Path p = new Path();
                    p.addRoundRect(rect, radii, Path.Direction.CW);
                    if (haveFill) canvas.drawPath(p, fillPaint);
                    if (haveStroke) canvas.drawPath(p, strokePaint);
                } else if (radius > 0) {
                    float rad = Math.min(radius, Math.min(rect.width(), rect.height()) * 0.5f);
                    if (haveFill) canvas.drawRoundRect(rect, rad, rad, fillPaint);
                    if (haveStroke) canvas.drawRoundRect(rect, rad, rad, strokePaint);
                } else {
                    if (haveFill && (fillPaint.getColor() != 0 || fillPaint.getShader() != null)) canvas.drawRect(rect, fillPaint);
                    if (haveStroke) canvas.drawRect(rect, strokePaint);
                }
                break;
            case OVAL:
                if (haveFill) canvas.drawOval(rect, fillPaint);
                if (haveStroke) canvas.drawOval(rect, strokePaint);
                break;
            case LINE:
                if (haveStroke) canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), strokePaint);
                break;
            default:
                if (haveFill) canvas.drawOval(rect, fillPaint);
        }
        fillPaint.setAlpha(prevFill);
        if (strokePaint != null) strokePaint.setAlpha(prevStroke);
    }

    @Override public void setAlpha(int alpha) { if (alpha != this.alpha) { this.alpha = alpha; invalidateSelf(); } }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
