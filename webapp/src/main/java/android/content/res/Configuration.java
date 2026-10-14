package android.content.res;

public final class Configuration {
    public static final int ORIENTATION_PORTRAIT = 1, ORIENTATION_LANDSCAPE = 2;
    public static final int UI_MODE_NIGHT_MASK = 0x30, UI_MODE_NIGHT_NO = 0x10, UI_MODE_NIGHT_YES = 0x20;
    public static final int SCREENLAYOUT_LAYOUTDIR_RTL = 0x80;
    public int orientation = ORIENTATION_PORTRAIT;
    public int uiMode = UI_MODE_NIGHT_YES;
    public float fontScale = 1f;
    public int screenWidthDp, screenHeightDp, densityDpi;
    public java.util.Locale locale = java.util.Locale.getDefault();
    public int getLayoutDirection() { return 1; }
}
