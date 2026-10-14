package android.text;

public class SpannableString implements Spannable {
    private final String s;
    public SpannableString(CharSequence source) { s = source.toString(); }
    public static SpannableString valueOf(CharSequence source) { return new SpannableString(source); }
    @Override public void setSpan(Object what, int start, int end, int flags) {}
    @Override public void removeSpan(Object what) {}
    @Override public int length() { return s.length(); }
    @Override public char charAt(int i) { return s.charAt(i); }
    @Override public CharSequence subSequence(int a, int b) { return s.substring(a, b); }
    @Override public String toString() { return s; }
}
