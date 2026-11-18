package android.graphics;
import java.io.InputStream;
/** Browser decoded images retain their animation; each frame redraws the live image source. */
public class Movie {
    private final Bitmap bitmap;
    private Movie(Bitmap bitmap) { this.bitmap = bitmap; }
    public static Movie decodeStream(InputStream in) { Bitmap b = BitmapFactory.decodeStream(in); return b == null ? null : new Movie(b); }
    public int width() { return bitmap.getWidth(); }
    public int height() { return bitmap.getHeight(); }
    public int duration() { return 1000; }
    public boolean setTime(int time) { return true; }
    public void draw(Canvas canvas, float x, float y) { canvas.drawBitmap(bitmap, x, y, null); }
}
