package android.content;

/** The browser page is the app: one context provides storage, resources and system services. */
public class ContextWrapper extends Context {
    @Override public android.content.res.AssetManager getAssets() { return android.content.res.AssetManager.INSTANCE; }
    @Override public android.content.res.Resources getResources() { return android.content.res.Resources.INSTANCE; }
    @Override public android.content.pm.PackageManager getPackageManager() { return android.content.pm.PackageManager.INSTANCE; }
    @Override public ContentResolver getContentResolver() { return ContentResolver.INSTANCE; }
    @Override public Context getApplicationContext() { return this; }
    @Override public String getPackageName() { return divan.web.Platform.packageName(); }
    @Override public android.content.pm.ApplicationInfo getApplicationInfo() { return android.content.pm.PackageManager.INSTANCE.app(); }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) { return divan.web.WebPrefs.get(name); }
    @Override public java.io.File getFilesDir() { return dir("files"); }
    @Override public java.io.File getCacheDir() { return dir("cache"); }
    @Override public java.io.File getDir(String name, int mode) { return dir("app_" + name); }
    @Override public java.io.File getExternalFilesDir(String type) { return dir("external" + (type == null ? "" : "/" + type)); }

    private java.io.File dir(String name) {
        java.io.File f = new java.io.File("/data/data/" + getPackageName() + "/" + name);
        f.mkdirs();
        return f;
    }

    @Override public Object getSystemService(String name) { return divan.web.SystemServices.get(name, this); }
    @Override public int checkSelfPermission(String permission) { return android.content.pm.PackageManager.PERMISSION_GRANTED; }
    @Override public void startActivity(Intent intent) { divan.web.Intents.start(this, intent, -1); }
    @Override public void sendBroadcast(Intent intent) {}
    @Override public android.content.res.Resources.Theme getTheme() { return new android.content.res.Resources.Theme(); }
}
