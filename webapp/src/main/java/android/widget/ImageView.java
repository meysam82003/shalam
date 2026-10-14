package android.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** ImageView for bitmaps and drawables with the AOSP scale types and adjustViewBounds measuring. */
public class ImageView extends View {
    public enum ScaleType { MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE }

    private Bitmap bitmap;
    private Drawable drawable;
    private ScaleType scaleType = ScaleType.FIT_CENTER;
    private boolean adjustViewBounds;
    private int maxWidth = Integer.MAX_VALUE, maxHeight = Integer.MAX_VALUE;
    private int alpha = 255;
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);

    public ImageView(Context context) { super(context); }

    public ImageView(Context context, android.util.AttributeSet attrs) { this(context); }

    public void setImageBitmap(Bitmap bm) { bitmap = bm; drawable = null; requestLayout(); invalidate(); }

    public void setImageDrawable(Drawable d) {
        if (drawable != null) drawable.setCallback(null);
        drawable = d;
        bitmap = null;
        if (d != null) d.setCallback(this);
        requestLayout();
        invalidate();
    }

    public void setImageResource(int resId) { setImageDrawable(null); }
    public Drawable getDrawable() { return drawable; }
    public void setScaleType(ScaleType st) { scaleType = st; invalidate(); }
    public ScaleType getScaleType() { return scaleType; }
    public void setAdjustViewBounds(boolean adjust) { adjustViewBounds = adjust; if (adjust && scaleType == ScaleType.FIT_CENTER) scaleType = ScaleType.FIT_CENTER; requestLayout(); }
    public void setMaxWidth(int w) { maxWidth = w; requestLayout(); }
    public boolean getAdjustViewBounds() { return adjustViewBounds; }
    public int getMaxHeight() { return maxHeight; }
    public int getMaxWidth() { return maxWidth; }
    public void setMaxHeight(int h) { maxHeight = h; requestLayout(); }
    public void setImageAlpha(int a) { alpha = a; invalidate(); }
    public void setColorFilter(int color) {}
    public void setImageTintList(android.content.res.ColorStateList tint) {}

    private int intrinsicW() { return bitmap != null ? bitmap.getWidth() : drawable != null ? drawable.getIntrinsicWidth() : -1; }
    private int intrinsicH() { return bitmap != null ? bitmap.getHeight() : drawable != null ? drawable.getIntrinsicHeight() : -1; }

    @Override protected boolean verifyDrawable(Drawable who) { return who == drawable || super.verifyDrawable(who); }

    /** AOSP ImageView.onMeasure (simplified to what adjustViewBounds needs). */
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = intrinsicW(), h = intrinsicH();
        boolean resizeWidth = false, resizeHeight = false;
        final int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        float desiredAspect = 0.0f;
        if (w <= 0 || h <= 0) {
            w = 0; h = 0;
        } else if (adjustViewBounds) {
            resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
            resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
            desiredAspect = (float) w / (float) h;
        }
        final int pleft = mPaddingLeft, pright = mPaddingRight, ptop = mPaddingTop, pbottom = mPaddingBottom;
        int widthSize, heightSize;
        if (resizeWidth || resizeHeight) {
            widthSize = resolveAdjustedSize(w + pleft + pright, maxWidth, widthMeasureSpec);
            heightSize = resolveAdjustedSize(h + ptop + pbottom, maxHeight, heightMeasureSpec);
            if (desiredAspect != 0.0f) {
                final float actualAspect = (float) (widthSize - pleft - pright) / (heightSize - ptop - pbottom);
                if (Math.abs(actualAspect - desiredAspect) > 0.0000001) {
                    boolean done = false;
                    if (resizeWidth) {
                        int newWidth = (int) (desiredAspect * (heightSize - ptop - pbottom)) + pleft + pright;
                        if (!resizeHeight) widthSize = resolveAdjustedSize(newWidth, maxWidth, widthMeasureSpec);
                        if (newWidth <= widthSize) { widthSize = newWidth; done = true; }
                    }
                    if (!done && resizeHeight) {
                        int newHeight = (int) ((widthSize - pleft - pright) / desiredAspect) + ptop + pbottom;
                        if (!resizeWidth) heightSize = resolveAdjustedSize(newHeight, maxHeight, heightMeasureSpec);
                        if (newHeight <= heightSize) heightSize = newHeight;
                    }
                }
            }
        } else {
            w += pleft + pright;
            h += ptop + pbottom;
            w = Math.max(w, getSuggestedMinimumWidth());
            h = Math.max(h, getSuggestedMinimumHeight());
            widthSize = resolveSizeAndState(w, widthMeasureSpec, 0);
            heightSize = resolveSizeAndState(h, heightMeasureSpec, 0);
        }
        setMeasuredDimension(widthSize, heightSize);
    }

    private int resolveAdjustedSize(int desiredSize, int maxSize, int measureSpec) {
        int result = desiredSize;
        final int specMode = MeasureSpec.getMode(measureSpec);
        final int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED: result = Math.min(desiredSize, maxSize); break;
            case MeasureSpec.AT_MOST: result = Math.min(Math.min(desiredSize, specSize), maxSize); break;
            case MeasureSpec.EXACTLY: result = specSize; break;
        }
        return result;
    }

    @Override protected void onDraw(Canvas canvas) {
        int vw = getWidth() - mPaddingLeft - mPaddingRight, vh = getHeight() - mPaddingTop - mPaddingBottom;
        int dw = intrinsicW(), dh = intrinsicH();
        if (bitmap == null && drawable == null) return;
        canvas.save();
        canvas.clipRect(mPaddingLeft, mPaddingTop, mPaddingLeft + vw, mPaddingTop + vh);
        canvas.translate(mPaddingLeft, mPaddingTop);
        if (drawable != null && (dw <= 0 || dh <= 0 || scaleType == ScaleType.FIT_XY)) {
            drawable.setBounds(0, 0, vw, vh);
            drawable.draw(canvas);
            canvas.restore();
            return;
        }
        Matrix m = new Matrix();
        switch (scaleType) {
            case FIT_XY: m.setScale(vw / (float) dw, vh / (float) dh); break;
            case CENTER: m.setTranslate(Math.round((vw - dw) * 0.5f), Math.round((vh - dh) * 0.5f)); break;
            case CENTER_CROP: {
                float scale, dx = 0, dy = 0;
                if (dw * vh > vw * dh) { scale = (float) vh / (float) dh; dx = (vw - dw * scale) * 0.5f; }
                else { scale = (float) vw / (float) dw; dy = (vh - dh * scale) * 0.5f; }
                m.setScale(scale, scale);
                m.postTranslate(Math.round(dx), Math.round(dy));
                break;
            }
            case CENTER_INSIDE: {
                float scale = (dw <= vw && dh <= vh) ? 1.0f : Math.min((float) vw / (float) dw, (float) vh / (float) dh);
                m.setScale(scale, scale);
                m.postTranslate(Math.round((vw - dw * scale) * 0.5f), Math.round((vh - dh * scale) * 0.5f));
                break;
            }
            default: {
                Matrix.ScaleToFit fit = scaleType == ScaleType.FIT_START ? Matrix.ScaleToFit.START : scaleType == ScaleType.FIT_END ? Matrix.ScaleToFit.END : Matrix.ScaleToFit.CENTER;
                m.setRectToRect(new RectF(0, 0, dw, dh), new RectF(0, 0, vw, vh), fit);
            }
        }
        canvas.concat(m);
        if (bitmap != null) {
            paint.setAlpha(alpha);
            canvas.drawBitmap(bitmap, 0, 0, paint);
        } else {
            drawable.setBounds(0, 0, dw, dh);
            drawable.draw(canvas);
        }
        canvas.restore();
    }
}
