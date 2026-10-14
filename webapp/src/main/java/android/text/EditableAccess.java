package android.text;

/** Bridge for the widget package: change hook and silent replacement of a SpannableStringBuilder. */
public final class EditableAccess {
    private EditableAccess() {}

    public static void setOnChange(SpannableStringBuilder b, Runnable r) { b.onChange = r; }

    public static void setQuietly(SpannableStringBuilder b, String s) { b.setQuietly(s); }
}
