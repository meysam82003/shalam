package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;

public class ColorDrawable extends Drawable {
    private int baseColor;
    private int useColor;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ColorDrawable() {}

    public ColorDrawable(int color) { setColor(color); }

    @Override public void draw(Canvas canvas) {
        if ((useColor >>> 24) != 0) {
            paint.setColor(useColor);
            canvas.drawRect(getBounds(), paint);
        }
    }

    public int getColor() { return useColor; }

    public void setColor(int color) {
        if (baseColor != color || useColor != color) {
            baseColor = useColor = color;
            invalidateSelf();
        }
    }

    @Override public int getAlpha() { return useColor >>> 24; }

    /** As on Android, the alpha scales the colour's own alpha. */
    @Override public void setAlpha(int alpha) {
        alpha += alpha >> 7;
        int baseAlpha = baseColor >>> 24;
        int useAlpha = baseAlpha * alpha >> 8;
        int useColor = (baseColor << 8 >>> 8) | (useAlpha << 24);
        if (this.useColor != useColor) {
            this.useColor = useColor;
            invalidateSelf();
        }
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {}

    @Override public int getOpacity() {
        switch (useColor >>> 24) {
            case 255: return PixelFormat.OPAQUE;
            case 0: return PixelFormat.TRANSPARENT;
            default: return PixelFormat.TRANSLUCENT;
        }
    }
}
