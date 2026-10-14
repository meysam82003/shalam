package android.text;

public class TextUtils {
    public enum TruncateAt { START, MIDDLE, END, MARQUEE }

    public static boolean isEmpty(CharSequence str) { return str == null || str.length() == 0; }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a != null && b != null) {
            int length = a.length();
            if (length != b.length()) return false;
            if (a instanceof String && b instanceof String) return a.equals(b);
            for (int i = 0; i < length; i++) if (a.charAt(i) != b.charAt(i)) return false;
            return true;
        }
        return false;
    }

    public static String join(CharSequence delimiter, Iterable tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object t : tokens) { if (!first) sb.append(delimiter); sb.append(t); first = false; }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Object[] tokens) { return join(delimiter, java.util.Arrays.asList(tokens)); }

    public static boolean isDigitsOnly(CharSequence str) {
        for (int i = 0; i < str.length(); i++) if (!Character.isDigit(str.charAt(i))) return false;
        return true;
    }

    public static String htmlEncode(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '\'': sb.append("&#39;"); break;
                case '"': sb.append("&quot;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static CharSequence concat(CharSequence... text) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence t : text) sb.append(t);
        return sb.toString();
    }

    public static int getLayoutDirectionFromLocale(java.util.Locale locale) { return 0; }
}
