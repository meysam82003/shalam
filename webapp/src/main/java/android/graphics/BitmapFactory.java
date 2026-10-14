package android.graphics;

import divan.web.JS;
import org.teavm.jso.JSObject;

/**
 * Decoding in a browser is asynchronous, so images are decoded ahead of use: files in app storage at start,
 * every image file as soon as it is written, picked images before the picker returns, and anything this app
 * encodes itself. A file not decoded yet returns null (as an unreadable file does on Android) and is decoded
 * in the background; the screen is redrawn when it is ready.
 */
public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize;
        public int outWidth;
        public int outHeight;
        public String outMimeType;
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
        public boolean inMutable;
        public boolean inScaled = true;
        public int inDensity;
        public int inTargetDensity;
    }

    /** Content key of encoded bytes. */
    public static String key(byte[] data, int offset, int length) {
        int h1 = 0x811C9DC5, h2 = 0x01000193 * 7;
        int step = length > 262144 ? 3 : 1;
        for (int i = offset; i < offset + length; i += step) {
            h1 = (h1 ^ (data[i] & 0xFF)) * 0x01000193;
            h2 = h2 * 31 + data[i];
        }
        return length + ":" + Integer.toHexString(h1) + ":" + Integer.toHexString(h2);
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) {
        if (data == null || length <= 0) return null;
        String k = key(data, offset, length);
        JSObject img = JS.decodedImage(k);
        if (img == null || JS.isNull(img)) {
            byte[] copy = offset == 0 && length == data.length ? data : java.util.Arrays.copyOfRange(data, offset, offset + length);
            JS.decodeLater(k, copy);
            if (opts != null) { opts.outWidth = -1; opts.outHeight = -1; }
            return null;
        }
        int w = JS.imageWidth(img), h = JS.imageHeight(img);
        if (opts != null) {
            opts.outWidth = w;
            opts.outHeight = h;
            opts.outMimeType = mime(data, offset);
            if (opts.inJustDecodeBounds) return null;
            int sample = opts.inSampleSize;
            if (sample > 1) {
                int sw = Math.max(1, w / sample), sh = Math.max(1, h / sample);
                opts.outWidth = sw;
                opts.outHeight = sh;
                return Bitmap.createScaledBitmap(Bitmap.ofImage(img), sw, sh, true);
            }
        }
        return Bitmap.ofImage(img);
    }

    private static String mime(byte[] d, int o) {
        if (d.length > o + 3 && (d[o] & 0xFF) == 0x89 && d[o + 1] == 'P') return "image/png";
        if (d.length > o + 2 && (d[o] & 0xFF) == 0xFF && (d[o + 1] & 0xFF) == 0xD8) return "image/jpeg";
        if (d.length > o + 11 && d[o + 8] == 'W' && d[o + 9] == 'E') return "image/webp";
        return "image/*";
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) { return decodeByteArray(data, offset, length, null); }

    public static Bitmap decodeFile(String pathName, Options opts) {
        try {
            byte[] data = divan.web.Storage.readFile(pathName);
            if (data == null) return null;
            return decodeByteArray(data, 0, data.length, opts);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap decodeFile(String pathName) { return decodeFile(pathName, null); }

    public static Bitmap decodeStream(java.io.InputStream is, Rect outPadding, Options opts) {
        if (is == null) return null;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            byte[] data = out.toByteArray();
            return decodeByteArray(data, 0, data.length, opts);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    public static Bitmap decodeStream(java.io.InputStream is) { return decodeStream(is, null, null); }
}
