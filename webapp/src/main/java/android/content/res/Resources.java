package android.content.res;

/** Display metrics of the browser window and the named resources the app looks up by name. */
public class Resources {
    public static final Resources INSTANCE = new Resources();
    public static final int ID_NULL = 0;

    public static class NotFoundException extends RuntimeException {
        public NotFoundException() {}
        public NotFoundException(String m) { super(m); }
    }


    public static class Theme {}

    private final android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
    private final Configuration configuration = new Configuration();

    public android.util.DisplayMetrics getDisplayMetrics() { return metrics; }
    public Configuration getConfiguration() { return configuration; }
    public static Resources getSystem() { return INSTANCE; }

    /** Resource ids by name: sounds (raw), the notification icon and the dialog animation style. */
    public int getIdentifier(String name, String defType, String defPackage) { return divan.web.Res.id(name, defType); }

    public String getResourceName(int id) { return divan.web.Res.name(id); }
    public String getString(int id) { return divan.web.Res.string(id); }
    public CharSequence getText(int id) { return getString(id); }
    public int getColor(int id, Theme theme) { return 0; }
    public float getDimension(int id) { return 0; }
    public int getDimensionPixelSize(int id) { return 0; }
}
