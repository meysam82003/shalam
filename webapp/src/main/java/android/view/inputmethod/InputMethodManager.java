package android.view.inputmethod;

import android.view.View;

public final class InputMethodManager {
    public static final int SHOW_IMPLICIT = 1, SHOW_FORCED = 2, HIDE_IMPLICIT_ONLY = 1, HIDE_NOT_ALWAYS = 2;

    public boolean showSoftInput(View view, int flags) {
        if (view instanceof android.widget.EditText) { view.requestFocus(); divan.web.Inputs.focus((android.widget.EditText) view); }
        return true;
    }

    public boolean hideSoftInputFromWindow(android.os.IBinder windowToken, int flags) { divan.web.Inputs.blurAll(); return true; }

    public boolean isActive() { return divan.web.Inputs.anyShowing(); }
}
