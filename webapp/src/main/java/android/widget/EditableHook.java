package android.widget;

/** Access to the change hook of the plain-text Editable (package android.text). */
final class EditableHook {
    private EditableHook() {}

    static void hook(android.text.SpannableStringBuilder b, Runnable r) { android.text.EditableAccess.setOnChange(b, r); }

    static void setQuietly(android.text.SpannableStringBuilder b, String s) { android.text.EditableAccess.setQuietly(b, s); }
}
