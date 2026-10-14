package android.media;

/** Orientation tag of JPEG photos (read directly from the EXIF block). */
public class ExifInterface {
    public static final String TAG_ORIENTATION = "Orientation";
    public static final int ORIENTATION_UNDEFINED = 0, ORIENTATION_NORMAL = 1, ORIENTATION_FLIP_HORIZONTAL = 2, ORIENTATION_ROTATE_180 = 3,
        ORIENTATION_FLIP_VERTICAL = 4, ORIENTATION_TRANSPOSE = 5, ORIENTATION_ROTATE_90 = 6, ORIENTATION_TRANSVERSE = 7, ORIENTATION_ROTATE_270 = 8;

    private int orientation = ORIENTATION_UNDEFINED;

    public ExifInterface(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0 && total < 262144) { out.write(buf, 0, n); total += n; }
        orientation = parse(out.toByteArray());
    }

    public ExifInterface(String filename) throws java.io.IOException {
        byte[] d = divan.web.Storage.readFile(filename);
        if (d != null) orientation = parse(d);
    }

    public int getAttributeInt(String tag, int defaultValue) {
        if (TAG_ORIENTATION.equals(tag)) return orientation == ORIENTATION_UNDEFINED ? defaultValue : orientation;
        return defaultValue;
    }

    public String getAttribute(String tag) { return TAG_ORIENTATION.equals(tag) && orientation != 0 ? String.valueOf(orientation) : null; }

    private static int parse(byte[] d) {
        if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) return 0;
        int p = 2;
        while (p + 4 < d.length) {
            if ((d[p] & 0xFF) != 0xFF) return 0;
            int marker = d[p + 1] & 0xFF;
            int len = ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
            if (marker == 0xE1 && p + 10 < d.length && d[p + 4] == 'E' && d[p + 5] == 'x' && d[p + 6] == 'i' && d[p + 7] == 'f') {
                int t = p + 10;
                boolean le = d[t] == 'I';
                int ifd = t + u32(d, t + 4, le);
                if (ifd + 2 > d.length) return 0;
                int count = u16(d, ifd, le);
                for (int i = 0; i < count; i++) {
                    int e = ifd + 2 + i * 12;
                    if (e + 12 > d.length) return 0;
                    if (u16(d, e, le) == 0x0112) return u16(d, e + 8, le);
                }
                return 0;
            }
            if (marker == 0xDA) return 0;
            p += 2 + len;
        }
        return 0;
    }

    private static int u16(byte[] d, int o, boolean le) { return le ? (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8) : ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF); }
    private static int u32(byte[] d, int o, boolean le) { return le ? u16(d, o, true) | (u16(d, o + 2, true) << 16) : (u16(d, o, false) << 16) | u16(d, o + 2, false); }
}
