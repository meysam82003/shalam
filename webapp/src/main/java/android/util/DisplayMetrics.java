package android.util;

public class DisplayMetrics {
    public static final int DENSITY_DEFAULT = 160;
    public float density = 1f;
    public float scaledDensity = 1f;
    public int densityDpi = 160;
    public int widthPixels;
    public int heightPixels;
    public float xdpi = 160f, ydpi = 160f;

    public void setTo(DisplayMetrics o) { density = o.density; scaledDensity = o.scaledDensity; densityDpi = o.densityDpi; widthPixels = o.widthPixels; heightPixels = o.heightPixels; xdpi = o.xdpi; ydpi = o.ydpi; }
}
