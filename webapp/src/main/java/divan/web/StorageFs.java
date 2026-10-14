package divan.web;

import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.runtime.fs.VirtualFileSystem;

/** java.io.File / FileInputStream / FileOutputStream on {@link Storage}. */
final class StorageFs implements VirtualFileSystem {
    @Override public String getUserDir() { return "/"; }
    @Override public VirtualFile getFile(String path) { return new F(Storage.norm(path)); }
    @Override public boolean isWindows() { return false; }
    @Override public String canonicalize(String path) { return Storage.norm(path); }
    @Override public String[] getRoots() { return new String[] {"/"}; }

    static final class F implements VirtualFile {
        final String path;

        F(String path) { this.path = path; }

        @Override public String getName() { int i = path.lastIndexOf('/'); return path.substring(i + 1); }
        @Override public boolean isDirectory() { return Storage.isDir(path); }
        @Override public boolean isFile() { return Storage.isFile(path); }
        @Override public boolean exists() { return Storage.exists(path); }
        @Override public String[] listFiles() { return isDirectory() ? Storage.list(path) : null; }

        @Override public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
            if (!writable && !Storage.isFile(path)) return null;
            byte[] initial = Storage.readFile(path);
            if (initial == null || (writable && !append && !readable)) initial = new byte[0];
            return new Acc(path, initial, writable, append);
        }

        private String child(String name) { return Storage.norm(path + "/" + name); }

        @Override public boolean createFile(String name) {
            String c = child(name);
            if (Storage.exists(c)) return false;
            Storage.writeFile(c, new byte[0]);
            return true;
        }

        @Override public boolean createDirectory(String name) { return Storage.mkdir(child(name)); }
        @Override public boolean delete() { return Storage.delete(path); }

        @Override public boolean adopt(VirtualFile file, String fileName) {
            F f = (F) file;
            return Storage.rename(f.path, child(fileName));
        }

        @Override public boolean canRead() { return exists(); }
        @Override public boolean canWrite() { return true; }
        @Override public long lastModified() { return Storage.modified(path); }
        @Override public boolean setLastModified(long lastModified) { Storage.touch(path, lastModified); return true; }
        @Override public boolean setReadOnly(boolean readOnly) { return true; }
        @Override public int length() { return Storage.length(path); }
    }

    /** Open file: reads from a copy, writes are saved when the stream is flushed or closed. */
    static final class Acc implements VirtualFileAccessor {
        private final String path;
        private byte[] data;
        private int size;
        private int pos;
        private final boolean writable;
        private boolean dirty;

        Acc(String path, byte[] initial, boolean writable, boolean append) {
            this.path = path;
            this.data = java.util.Arrays.copyOf(initial, Math.max(16, initial.length));
            this.size = initial.length;
            this.writable = writable;
            this.pos = append ? size : 0;
            if (writable && !append && initial.length == 0) dirty = true;
        }

        @Override public int read(byte[] buffer, int offset, int limit) {
            int n = Math.min(limit, size - pos);
            if (n <= 0) return -1;
            System.arraycopy(data, pos, buffer, offset, n);
            pos += n;
            return n;
        }

        @Override public void write(byte[] buffer, int offset, int limit) {
            int end = pos + limit;
            if (end > data.length) data = java.util.Arrays.copyOf(data, Math.max(end, data.length * 2));
            System.arraycopy(buffer, offset, data, pos, limit);
            pos = end;
            if (pos > size) size = pos;
            dirty = true;
        }

        @Override public int tell() { return pos; }
        @Override public void seek(int target) { pos = target; }
        @Override public void skip(int amount) { pos += amount; }
        @Override public int size() { return size; }

        @Override public void resize(int s) {
            if (s > data.length) data = java.util.Arrays.copyOf(data, s);
            size = s;
            if (pos > size) pos = size;
            dirty = true;
        }

        @Override public void close() { flush(); }

        @Override public void flush() {
            if (writable && dirty) {
                Storage.writeFile(path, java.util.Arrays.copyOf(data, size));
                dirty = false;
            }
        }
    }
}
