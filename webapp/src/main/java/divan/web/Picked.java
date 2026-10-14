package divan.web;

/**
 * Files picked in the browser (content://web/picked/N) and files the app asked to create (content://web/save/N, a
 * download when the app closes the stream).
 */
public final class Picked {
    private Picked() {}

    static final class File {
        final String name, mime;
        byte[] data;
        File(String name, String mime, byte[] data) { this.name = name; this.mime = mime; this.data = data; }
    }

    private static final java.util.HashMap<String, File> files = new java.util.HashMap<>();
    private static int next = 1;

    static android.net.Uri add(String kind, String name, String mime, byte[] data) {
        String key = "content://web/" + kind + "/" + (next++);
        files.put(key, new File(name, mime, data));
        return android.net.Uri.parse(key);
    }

    private static File of(android.net.Uri uri) { return uri == null ? null : files.get(uri.toString()); }

    public static byte[] bytes(android.net.Uri uri) {
        File f = of(uri);
        if (f != null) return f.data;
        // the app's share folder (ShareProvider): content://<package>.share/<name>
        if (uri != null && "content".equals(uri.getScheme()) && uri.getAuthority() != null && uri.getAuthority().endsWith(".share")) {
            java.io.File file = new java.io.File("/data/data/" + Platform.packageName() + "/cache/share/" + uri.getLastPathSegment());
            return Storage.readFile(file.getAbsolutePath());
        }
        return null;
    }

    public static String name(android.net.Uri uri) { File f = of(uri); return f != null ? f.name : uri != null ? uri.getLastPathSegment() : null; }

    public static String mime(android.net.Uri uri) { File f = of(uri); return f != null ? f.mime : null; }

    public static java.io.OutputStream saveStream(android.net.Uri uri) throws java.io.FileNotFoundException {
        File f = of(uri);
        if (f == null) throw new java.io.FileNotFoundException(String.valueOf(uri));
        return new java.io.ByteArrayOutputStream() {
            private boolean done;
            @Override public void close() {
                if (done) return;
                done = true;
                f.data = toByteArray();
                Intents.download(f.name, f.mime, f.data);
            }
        };
    }
}
