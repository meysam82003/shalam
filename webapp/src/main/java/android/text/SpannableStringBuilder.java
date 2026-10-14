package android.text;

/** Plain-text Editable (the app keeps text plain; spans are accepted and ignored). */
public class SpannableStringBuilder implements Editable, Spannable {
    private final StringBuilder sb = new StringBuilder();
    private InputFilter[] filters = new InputFilter[0];
    /** Called after any change (EditText listens to keep the field in sync). */
    Runnable onChange;

    public SpannableStringBuilder() {}

    public SpannableStringBuilder(CharSequence text) { if (text != null) sb.append(text); }

    private void changed() { if (onChange != null) onChange.run(); }

    @Override public SpannableStringBuilder replace(int st, int en, CharSequence text) { sb.replace(st, en, text.toString()); changed(); return this; }
    @Override public SpannableStringBuilder insert(int where, CharSequence text) { sb.insert(where, text); changed(); return this; }
    @Override public SpannableStringBuilder delete(int st, int en) { sb.delete(st, en); changed(); return this; }
    @Override public SpannableStringBuilder append(CharSequence text) { sb.append(text); changed(); return this; }
    @Override public SpannableStringBuilder append(CharSequence text, int start, int end) { sb.append(text, start, end); changed(); return this; }
    @Override public SpannableStringBuilder append(char text) { sb.append(text); changed(); return this; }
    @Override public void clear() { sb.setLength(0); changed(); }
    @Override public void clearSpans() {}
    @Override public void setFilters(InputFilter[] f) { filters = f; }
    @Override public InputFilter[] getFilters() { return filters; }
    @Override public void setSpan(Object what, int start, int end, int flags) {}
    @Override public void removeSpan(Object what) {}
    @Override public int length() { return sb.length(); }
    @Override public char charAt(int index) { return sb.charAt(index); }
    @Override public CharSequence subSequence(int start, int end) { return sb.substring(start, end); }
    @Override public String toString() { return sb.toString(); }

    /** Replaces everything without notifying (used when the browser field reports the user's typing). */
    void setQuietly(String s) { sb.setLength(0); sb.append(s); }
}
