package android.content.pm;

/** Meta-data of the build (store, API address, keys) come from the page configuration (divan.web.Platform). */
public class PackageManager {
    public static final PackageManager INSTANCE = new PackageManager();
    public static final int PERMISSION_GRANTED = 0, PERMISSION_DENIED = -1;
    public static final int GET_META_DATA = 0x80, GET_ACTIVITIES = 1, GET_SIGNATURES = 0x40, GET_SIGNING_CERTIFICATES = 0x08000000;

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException() {}
        public NameNotFoundException(String name) { super(name); }
    }

    private ApplicationInfo info;

    public ApplicationInfo app() {
        if (info == null) {
            info = new ApplicationInfo();
            info.packageName = divan.web.Platform.packageName();
            info.metaData = divan.web.Platform.metaData();
            info.targetSdkVersion = 35;
            info.minSdkVersion = 21;
        }
        return info;
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException { return app(); }

    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        PackageInfo p = new PackageInfo();
        p.packageName = packageName;
        p.versionName = divan.web.Platform.versionName();
        p.versionCode = divan.web.Platform.versionCode();
        return p;
    }

    public boolean hasSystemFeature(String name) { return false; }
    public String getInstallerPackageName(String packageName) { return null; }
}
