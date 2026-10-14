package android.content.pm;

public class ApplicationInfo extends PackageItemInfo {
    public static final int FLAG_DEBUGGABLE = 2;
    public int targetSdkVersion;
    public int minSdkVersion;
    public int flags;
    public String dataDir;
}
