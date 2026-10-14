package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;

/**
 * App files in memory, saved to the browser's IndexedDB after every change (host.js keeps the database).
 * Everything stored is loaded before the app starts, so reading stays synchronous as on Android.
 */
public final class Storage {
    private Storage() {}

    static final class Entry {
        byte[] data;
        long modified;
        Entry(byte[] d, long m) { data = d; modified = m; }
    }

    static final java.util.TreeMap<String, Entry> files = new java.util.TreeMap<>();
    static final java.util.TreeSet<String> dirs = new java.util.TreeSet<>();

    @JSBody(script = "return DH.boot.files.length;")
    private static native int bootCount();

    @JSBody(params = "i", script = "return DH.boot.files[i][0];")
    private static native String bootPath(int i);

    @JSBody(params = "i", script = "return DH.boot.files[i][1];")
    @JSByRef
    private static native byte[] bootData(int i);

    @JSBody(params = "i", script = "return DH.boot.files[i][2];")
    private static native double bootTime(int i);

    @JSBody(params = {"p", "d", "t"}, script = "DH.saveFile(p, d, t);")
    private static native void persist(String path, @JSByRef byte[] data, double time);

    @JSBody(params = "p", script = "DH.deleteFile(p);")
    private static native void unpersist(String path);

    /** Loads the stored files and registers this storage as java.io.File's file system. */
    public static void init() {
        int n = bootCount();
        for (int i = 0; i < n; i++) {
            String p = bootPath(i);
            byte[] src = bootData(i);
            files.put(p, new Entry(java.util.Arrays.copyOf(src, src.length), (long) bootTime(i)));
            addParents(p);
        }
        org.teavm.runtime.fs.VirtualFileSystemProvider.setInstance(new StorageFs());
    }

    private static boolean isImage(String p) {
        String l = p.toLowerCase();
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
    }

    /** Decodes every stored picture before the app starts, so BitmapFactory finds them synchronously. */
    public static void decodeStoredImages(Runnable done) {
        java.util.ArrayList<java.util.Map.Entry<String, Entry>> list = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Entry> e : files.entrySet()) if (isImage(e.getKey())) list.add(e);
        if (list.isEmpty()) { done.run(); return; }
        int[] left = {list.size()};
        for (java.util.Map.Entry<String, Entry> e : list) {
            byte[] d = e.getValue().data;
            JS.decodeThen(android.graphics.BitmapFactory.key(d, 0, d.length), d, () -> {
                if (--left[0] == 0) android.os.Looper.dispatch(done);
            });
        }
    }

    static String norm(String path) {
        if (path == null) return "/";
        String p = path.replace('\\', '/');
        if (!p.startsWith("/")) p = "/" + p;
        java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
        for (String s : p.split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); continue; }
            parts.addLast(s);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : parts) sb.append('/').append(s);
        return sb.length() == 0 ? "/" : sb.toString();
    }

    static void addParents(String path) {
        String p = path;
        int i;
        while ((i = p.lastIndexOf('/')) > 0) {
            p = p.substring(0, i);
            dirs.add(p);
        }
    }

    public static byte[] readFile(String path) {
        Entry e = files.get(norm(path));
        return e == null ? null : e.data;
    }

    @JSBody(params = {"key", "d"}, script = "return DH.blobUrl(key, d);")
    private static native String blob(String key, @JSByRef byte[] data);

    /** A browser address for a stored file (videos of ads). */
    public static String blobUrl(String path) {
        Entry e = files.get(norm(path));
        return e == null ? "" : blob(norm(path) + "@" + e.modified, e.data);
    }

    public static boolean exists(String path) { String p = norm(path); return files.containsKey(p) || dirs.contains(p) || p.equals("/"); }

    public static void writeFile(String path, byte[] data) {
        String p = norm(path);
        long now = (long) JS.now();
        files.put(p, new Entry(data, now));
        addParents(p);
        persist(p, data, now);
        String lower = p.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
            JS.decodeLater(android.graphics.BitmapFactory.key(data, 0, data.length), data);
        }
    }

    public static boolean delete(String path) {
        String p = norm(path);
        if (files.remove(p) != null) { unpersist(p); return true; }
        if (dirs.contains(p)) {
            String prefix = p + "/";
            for (String f : files.keySet()) if (f.startsWith(prefix)) return false;
            for (String d : dirs) if (d.startsWith(prefix)) return false;
            dirs.remove(p);
            return true;
        }
        return false;
    }

    public static String[] list(String dir) {
        String p = norm(dir);
        String prefix = p.equals("/") ? "/" : p + "/";
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (String f : files.keySet()) if (f.startsWith(prefix)) { String rest = f.substring(prefix.length()); int s = rest.indexOf('/'); names.add(s < 0 ? rest : rest.substring(0, s)); }
        for (String d : dirs) if (d.startsWith(prefix)) { String rest = d.substring(prefix.length()); int s = rest.indexOf('/'); names.add(s < 0 ? rest : rest.substring(0, s)); }
        names.remove("");
        return names.toArray(new String[0]);
    }

    public static boolean isDir(String path) { String p = norm(path); return p.equals("/") || dirs.contains(p); }
    public static boolean isFile(String path) { return files.containsKey(norm(path)); }
    public static long modified(String path) { Entry e = files.get(norm(path)); return e == null ? 0 : e.modified; }
    public static int length(String path) { Entry e = files.get(norm(path)); return e == null ? 0 : e.data.length; }

    public static boolean mkdir(String path) {
        String p = norm(path);
        if (exists(p)) return false;
        dirs.add(p);
        addParents(p);
        return true;
    }

    public static boolean rename(String from, String to) {
        String a = norm(from), b = norm(to);
        Entry e = files.remove(a);
        if (e == null) return false;
        unpersist(a);
        writeFile(b, e.data);
        return true;
    }

    public static void touch(String path, long time) {
        Entry e = files.get(norm(path));
        if (e != null) { e.modified = time; persist(norm(path), e.data, time); }
    }
}
