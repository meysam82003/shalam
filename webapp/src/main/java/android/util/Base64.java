package android.util;

/** android.util.Base64 flags over java.util.Base64. */
public class Base64 {
    public static final int DEFAULT = 0, NO_PADDING = 1, NO_WRAP = 2, CRLF = 4, URL_SAFE = 8, NO_CLOSE = 16;

    public static byte[] decode(String str, int flags) {
        String s = str.replaceAll("\\s", "");
        if ((flags & URL_SAFE) != 0 || s.indexOf('-') >= 0 || s.indexOf('_') >= 0) s = s.replace('-', '+').replace('_', '/');
        int pad = (4 - s.length() % 4) % 4;
        if (pad == 3) throw new IllegalArgumentException("bad base-64");
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < pad; i++) sb.append('=');
        try {
            return java.util.Base64.getDecoder().decode(sb.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("bad base-64");
        }
    }

    public static byte[] decode(byte[] input, int flags) { return decode(new String(input, java.nio.charset.StandardCharsets.US_ASCII), flags); }

    public static String encodeToString(byte[] input, int flags) {
        String s = java.util.Base64.getEncoder().encodeToString(input);
        if ((flags & URL_SAFE) != 0) s = s.replace('+', '-').replace('/', '_');
        if ((flags & NO_PADDING) != 0) { int e = s.length(); while (e > 0 && s.charAt(e - 1) == '=') e--; s = s.substring(0, e); }
        if ((flags & NO_WRAP) == 0) {
            StringBuilder sb = new StringBuilder();
            String nl = (flags & CRLF) != 0 ? "\r\n" : "\n";
            for (int i = 0; i < s.length(); i += 76) sb.append(s, i, Math.min(s.length(), i + 76)).append(nl);
            s = sb.toString();
        }
        return s;
    }

    public static byte[] encode(byte[] input, int flags) { return encodeToString(input, flags).getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
}
