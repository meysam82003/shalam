package android.content.pm;

public class PackageInfo {
    public String packageName;
    public String versionName;
    public int versionCode;
    public long firstInstallTime, lastUpdateTime;
    /** The web version has no APK signature (the integrity check applies to Android builds only). */
    public Signature[] signatures;
    public SigningInfo signingInfo;
    public long getLongVersionCode() { return versionCode; }
}
