package android.os;

public class ParcelFileDescriptor implements java.io.Closeable {
    public static final int MODE_READ_ONLY = 0x10000000, MODE_WRITE_ONLY = 0x20000000, MODE_READ_WRITE = 0x30000000;
    private final java.io.File file;

    private ParcelFileDescriptor(java.io.File file) { this.file = file; }

    public static ParcelFileDescriptor open(java.io.File file, int mode) throws java.io.FileNotFoundException {
        if (!file.exists() && (mode & MODE_WRITE_ONLY) == 0) throw new java.io.FileNotFoundException(file.getPath());
        return new ParcelFileDescriptor(file);
    }

    public java.io.File file() { return file; }
    public long getStatSize() { return file.length(); }
    @Override public void close() {}
}
