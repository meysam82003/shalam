package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.SpannableStringBuilder;

/**
 * EditText: drawn like a TextView on the canvas; while focused, the browser window lays a real text field over it
 * (same font, colour and padding) so typing, the on-screen keyboard and paste work natively.
 */
public class EditText extends TextView {
    private int selStart, selEnd;
    private boolean selectAllPending;

    public EditText(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setLongClickable(true);
        mGravity = android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL;
        mInputType = android.text.InputType.TYPE_CLASS_TEXT;
        mText = newEditable("");
    }

    public EditText(Context context, android.util.AttributeSet attrs) { this(context); }

    private SpannableStringBuilder newEditable(CharSequence s) {
        SpannableStringBuilder b = new SpannableStringBuilder(s);
        EditableHook.hook(b, this::onEditableChanged);
        return b;
    }

    private void onEditableChanged() {
        requestLayoutAndRedraw();
        divan.web.Inputs.syncFrom(this);
    }

    private void requestLayoutAndRedraw() { requestLayout(); invalidate(); }

    @Override CharSequence textForStorage(CharSequence text) { return newEditable(text); }

    @Override public Editable getText() { return (Editable) mText; }

    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        selStart = selEnd = mText.length();
        divan.web.Inputs.syncFrom(this);
    }

    /** Text typed in the browser field. */
    public void onBrowserInput(String value) {
        String old = mText.toString();
        if (old.equals(value)) return;
        EditableHook.setQuietly((SpannableStringBuilder) mText, value);
        selStart = selEnd = value.length();
        requestLayoutAndRedraw();
        sendTextChanged(old, mText);
        onTextChanged(mText, 0, old.length(), value.length());
    }

    /** The «done»/enter key of the on-screen keyboard. */
    public void onBrowserEditorAction() {
        int action = mImeOptions & android.view.inputmethod.EditorInfo.IME_MASK_ACTION;
        if (action == 0) action = android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
        if (mEditorActionListener != null && mEditorActionListener.onEditorAction(this, action, null)) return;
        if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) clearFocus();
    }

    public void setSelection(int start, int stop) { selStart = start; selEnd = stop; divan.web.Inputs.syncSelection(this); }
    public void setSelection(int index) { setSelection(index, index); }

    public void selectAll() {
        selStart = 0;
        selEnd = mText.length();
        selectAllPending = true;
        divan.web.Inputs.syncSelection(this);
    }

    public void extendSelection(int index) { setSelection(selStart, index); }
    public int getSelectionStart() { return selStart; }
    public int getSelectionEnd() { return selEnd; }
    public boolean takeSelectAll() { boolean s = selectAllPending; selectAllPending = false; return s; }

    @Override public boolean onCheckIsTextEditor() { return true; }

    @Override public boolean performClick() {
        requestFocus();
        divan.web.Inputs.focus(this);
        return super.performClick();
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) divan.web.Inputs.focus(this); else divan.web.Inputs.blur(this);
        invalidate();
    }

    @Override protected void onDraw(android.graphics.Canvas canvas) {
        // while the browser field is shown on top, the canvas leaves the text to it
        if (divan.web.Inputs.isShowing(this)) return;
        super.onDraw(canvas);
    }

    @Override protected void onDetachedFromWindow() {
        divan.web.Inputs.blur(this);
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ for the browser field
    public android.graphics.Paint fieldPaint() { return mTextPaint; }
    public int fieldTextColor() { return mCurTextColor; }
    public int fieldHintColor() { return mCurHintTextColor; }
    public int fieldGravity() { return mGravity; }
    public boolean fieldRtl() { return textIsRtl(mText.length() > 0 ? mText : (mHint != null ? mHint : "")); }
    public boolean fieldSingleLine() { return mSingleLine; }
    public int fieldVerticalOffset() { return getVerticalOffset(); }
    public int fieldLineHeight() {
        android.graphics.Paint.FontMetricsInt fm = mTextPaint.getFontMetricsInt();
        return mIncludePad ? fm.bottom - fm.top : fm.descent - fm.ascent;
    }
}
