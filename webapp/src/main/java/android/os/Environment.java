package android.os;

public class Environment {
    public static final String DIRECTORY_DOWNLOADS = "Download", DIRECTORY_PICTURES = "Pictures";
    public static java.io.File getExternalStorageDirectory() { return new java.io.File("/sdcard"); }
}
