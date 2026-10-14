package android.content.res;

/** The app's assets (word lists, certificates, fonts) bundled with the page and loaded before start. */
public final class AssetManager {
    public static final AssetManager INSTANCE = new AssetManager();
    public static final int ACCESS_STREAMING = 2;

    public java.io.InputStream open(String fileName) throws java.io.IOException {
        byte[] data = divan.web.Assets.get(fileName);
        if (data == null) throw new java.io.FileNotFoundException(fileName);
        return new java.io.ByteArrayInputStream(data);
    }

    public java.io.InputStream open(String fileName, int accessMode) throws java.io.IOException { return open(fileName); }

    public String[] list(String path) { return divan.web.Assets.list(path); }
}
