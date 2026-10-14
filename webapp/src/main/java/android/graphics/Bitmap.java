package android.graphics;

import divan.web.JS;
import org.teavm.jso.JSObject;

/**
 * Bitmap backed by a browser canvas (or, for a decoded file, by the decoded image until it is drawn into).
 * Pixel sizes are device pixels, as on Android.
 */
public final class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE }
    public enum CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    private JSObject canvas;
    private JSObject image;
    private final int width, height;
    private final boolean mutable;
    private boolean recycled;
    private int generation;
    private final Config config;

    private Bitmap(int w, int h, Config config, boolean mutable) {
        this.width = w; this.height = h; this.config = config == null ? Config.ARGB_8888 : config; this.mutable = mutable;
    }

    /** A decoded image (immutable, like BitmapFactory results). */
    static Bitmap ofImage(JSObject image) {
        Bitmap b = new Bitmap(JS.imageWidth(image), JS.imageHeight(image), Config.ARGB_8888, false);
        b.image = image;
        return b;
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width and height must be > 0");
        Bitmap b = new Bitmap(width, height, config, true);
        b.canvas = JS.newCanvas(width, height);
        return b;
    }

    public static Bitmap createBitmap(Bitmap src) { return createBitmap(src, 0, 0, src.width, src.height); }

    public static Bitmap createBitmap(Bitmap src, int x, int y, int width, int height) { return createBitmap(src, x, y, width, height, null, false); }

    /** Android's rule: the result is sized to the source rectangle mapped through the matrix. */
    public static Bitmap createBitmap(Bitmap src, int x, int y, int width, int height, Matrix m, boolean filter) {
        RectF dst = new RectF(0, 0, width, height);
        if (m != null && !m.isIdentity()) m.mapRect(dst, new RectF(0, 0, width, height));
        int nw = Math.max(1, Math.round(dst.width())), nh = Math.max(1, Math.round(dst.height()));
        Bitmap b = createBitmap(nw, nh, src.config);
        JSObject ctx = JS.context2d(b.canvas);
        JS.smoothing(ctx, true);
        JS.translate(ctx, -dst.left, -dst.top);
        if (m != null && !m.isIdentity()) JS.transform(ctx, m.a, m.b, m.c, m.d, m.e, m.f);
        JS.drawImageRect(ctx, src.source(), x, y, width, height, 0, 0, width, height);
        return b;
    }

    public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {
        Bitmap b = createBitmap(Math.max(1, dstWidth), Math.max(1, dstHeight), src.config);
        JSObject ctx = JS.context2d(b.canvas);
        JS.smoothing(ctx, filter);
        JS.drawImageRect(ctx, src.source(), 0, 0, src.width, src.height, 0, 0, dstWidth, dstHeight);
        return b;
    }

    public Bitmap copy(Config config, boolean isMutable) {
        Bitmap b = createBitmap(width, height, config);
        JS.drawImage(JS.context2d(b.canvas), source(), 0, 0);
        return b;
    }

    /** Image to draw from. */
    public JSObject source() { return canvas != null ? canvas : image; }

    /** Canvas to draw into or to use as a pattern; a decoded image is copied into one the first time. */
    public JSObject canvas() {
        if (canvas == null) {
            canvas = JS.newCanvas(width, height);
            JS.drawImage(JS.context2d(canvas), image, 0, 0);
            image = null;
        }
        return canvas;
    }

    void touched() { generation++; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getScaledWidth(int density) { return width; }
    public int getScaledHeight(int density) { return height; }
    public int getDensity() { return 160; }
    public void setDensity(int density) {}
    public Config getConfig() { return config; }
    public boolean isMutable() { return mutable; }
    public boolean hasAlpha() { return true; }
    public int getByteCount() { return width * height * 4; }
    public int getAllocationByteCount() { return getByteCount(); }
    public int getRowBytes() { return width * 4; }
    public int getGenerationId() { return generation; }
    public boolean isRecycled() { return recycled; }

    public void recycle() {
        if (recycled) return;
        recycled = true;
        if (canvas != null) JS.releaseCanvas(canvas);
        canvas = null;
        image = null;
    }

    public int getPixel(int x, int y) { return JS.pixel(canvas(), x, y); }

    public void eraseColor(int c) {
        JSObject ctx = JS.context2d(canvas());
        JS.clearRect(ctx, 0, 0, width, height);
        if (c != 0) {
            JS.fillStyle(ctx, JS.str(Color.css(c)), 1);
            JS.fillRect(ctx, 0, 0, width, height);
        }
        touched();
    }

    /** Encodes PNG or JPEG; the result is remembered so decoding the same bytes later is immediate. */
    public boolean compress(CompressFormat format, int quality, java.io.OutputStream stream) {
        String mime = format == CompressFormat.PNG ? "image/png" : format == CompressFormat.JPEG ? "image/jpeg" : "image/webp";
        byte[] data = JS.encodeImage(source(), mime, Math.max(0, Math.min(100, quality)) / 100f);
        if (data == null || data.length == 0) return false;
        JS.rememberDecoded(BitmapFactory.key(data, 0, data.length), source());
        try {
            stream.write(data);
        } catch (java.io.IOException e) {
            return false;
        }
        return true;
    }
}
