package android.content;

public abstract class Context {
    public static final int MODE_PRIVATE = 0, MODE_APPEND = 0x8000;
    public static final String CLIPBOARD_SERVICE = "clipboard", NOTIFICATION_SERVICE = "notification", JOB_SCHEDULER_SERVICE = "jobscheduler",
        AUDIO_SERVICE = "audio", VIBRATOR_SERVICE = "vibrator", INPUT_METHOD_SERVICE = "input_method", WINDOW_SERVICE = "window",
        CONNECTIVITY_SERVICE = "connectivity", ALARM_SERVICE = "alarm", LAYOUT_INFLATER_SERVICE = "layout_inflater";

    public abstract android.content.res.AssetManager getAssets();
    public abstract android.content.res.Resources getResources();
    public abstract android.content.pm.PackageManager getPackageManager();
    public abstract ContentResolver getContentResolver();
    public abstract Context getApplicationContext();
    public abstract String getPackageName();
    public abstract android.content.pm.ApplicationInfo getApplicationInfo();
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public abstract java.io.File getFilesDir();
    public abstract java.io.File getCacheDir();
    public abstract java.io.File getDir(String name, int mode);
    public abstract java.io.File getExternalFilesDir(String type);
    public abstract Object getSystemService(String name);
    public abstract int checkSelfPermission(String permission);
    public abstract void startActivity(Intent intent);
    public abstract void sendBroadcast(Intent intent);
    public abstract android.content.res.Resources.Theme getTheme();

    public final String getString(int resId) { return getResources().getString(resId); }
    public final CharSequence getText(int resId) { return getResources().getText(resId); }
    public final int getColor(int id) { return getResources().getColor(id, null); }
    public java.io.FileOutputStream openFileOutput(String name, int mode) throws java.io.FileNotFoundException { return new java.io.FileOutputStream(new java.io.File(getFilesDir(), name), (mode & MODE_APPEND) != 0); }
    public java.io.FileInputStream openFileInput(String name) throws java.io.FileNotFoundException { return new java.io.FileInputStream(new java.io.File(getFilesDir(), name)); }
    public boolean deleteFile(String name) { return new java.io.File(getFilesDir(), name).delete(); }
}
