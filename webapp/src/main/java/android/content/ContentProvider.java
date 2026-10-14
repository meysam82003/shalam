package android.content;

public abstract class ContentProvider {
    private Context context;
    public ContentProvider() {}
    public final Context getContext() { return context; }
    public abstract boolean onCreate();
    public abstract android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder);
    public abstract String getType(android.net.Uri uri);
    public abstract android.net.Uri insert(android.net.Uri uri, ContentValues values);
    public abstract int delete(android.net.Uri uri, String selection, String[] selectionArgs);
    public abstract int update(android.net.Uri uri, ContentValues values, String selection, String[] selectionArgs);
    public android.os.ParcelFileDescriptor openFile(android.net.Uri uri, String mode) throws java.io.FileNotFoundException { throw new java.io.FileNotFoundException(); }
}
