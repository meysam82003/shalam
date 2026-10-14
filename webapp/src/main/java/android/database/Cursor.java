package android.database;

public interface Cursor extends java.io.Closeable {
    int getCount();
    boolean moveToFirst();
    boolean moveToNext();
    int getColumnIndex(String columnName);
    String getString(int columnIndex);
    long getLong(int columnIndex);
    int getInt(int columnIndex);
    boolean isNull(int columnIndex);
    @Override void close();
}
