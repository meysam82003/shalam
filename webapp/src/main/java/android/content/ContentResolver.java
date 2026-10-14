package android.content;

/** Reads files picked in the browser (content://web/…) and app files (file://…). */
public class ContentResolver {
    public static final ContentResolver INSTANCE = new ContentResolver();

    public final java.io.InputStream openInputStream(android.net.Uri uri) throws java.io.FileNotFoundException {
        if (uri == null) throw new java.io.FileNotFoundException("null uri");
        byte[] data = divan.web.Picked.bytes(uri);
        if (data != null) return new java.io.ByteArrayInputStream(data);
        if ("file".equals(uri.getScheme())) return new java.io.FileInputStream(uri.getPath());
        throw new java.io.FileNotFoundException(uri.toString());
    }

    public final java.io.OutputStream openOutputStream(android.net.Uri uri) throws java.io.FileNotFoundException {
        if (uri == null) throw new java.io.FileNotFoundException("null uri");
        if ("file".equals(uri.getScheme())) return new java.io.FileOutputStream(uri.getPath());
        return divan.web.Picked.saveStream(uri);
    }

    public final java.io.OutputStream openOutputStream(android.net.Uri uri, String mode) throws java.io.FileNotFoundException { return openOutputStream(uri); }

    public final String getType(android.net.Uri uri) { return divan.web.Picked.mime(uri); }

    public final android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        android.database.MatrixCursor c = new android.database.MatrixCursor(new String[] {android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE});
        String name = divan.web.Picked.name(uri);
        byte[] data = divan.web.Picked.bytes(uri);
        if (name != null) c.addRow(new Object[] {name, data != null ? (long) data.length : 0L});
        return c;
    }

    public void takePersistableUriPermission(android.net.Uri uri, int modeFlags) {}
}
