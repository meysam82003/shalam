package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

/**
 * android.widget.TextView for the browser. Measuring follows AOSP TextView.onMeasure/getDesiredHeight and the line
 * metrics follow StaticLayout (font padding on the first and last line, spacing on every line but the last).
 * Lines wrap with minikin's high-quality (optimal) breaker, the default for TextView.
 */
public class TextView extends View {
    static final int LINES = 1, EMS = 2, PIXELS = 3;
    private static final int VERY_WIDE = 1024 * 1024;
    private static final char ELLIPSIS = '…';

    public enum BufferType { NORMAL, SPANNABLE, EDITABLE }

    public interface OnEditorActionListener { boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event); }

    protected final android.text.TextPaint mTextPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    CharSequence mText = "";
    CharSequence mHint;
    int mCurTextColor = 0xFF000000;
    android.content.res.ColorStateList mTextColor;
    int mCurHintTextColor = 0x80000000;
    int mGravity = Gravity.TOP | Gravity.START;
    int mMaximum = Integer.MAX_VALUE, mMaxMode = LINES;
    int mMinimum = 0, mMinMode = LINES;
    int mMaxWidthValue = Integer.MAX_VALUE, mMaxWidthMode = PIXELS;
    int mMinWidthValue = 0, mMinWidthMode = PIXELS;
    boolean mSingleLine;
    boolean mHorizontallyScrolling;
    TextUtils.TruncateAt mEllipsize;
    float mSpacingAdd = 0f, mSpacingMult = 1f;
    boolean mIncludePad = true;
    int mInputType;
    int mImeOptions;
    boolean mTextSelectable;
    boolean mAllCaps;
    private java.util.ArrayList<TextWatcher> mListeners;
    OnEditorActionListener mEditorActionListener;

    // laid-out lines
    private Lines layout;
    private Lines hintLayout;

    public TextView(Context context) {
        super(context);
        mTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, context.getResources().getDisplayMetrics()));
        mTextPaint.setTypeface(Typeface.DEFAULT);
    }

    public TextView(Context context, android.util.AttributeSet attrs) { this(context); }

    // ------------------------------------------------------------------ text
    public CharSequence getText() { return mText; }

    public int length() { return mText.length(); }

    public final void setText(CharSequence text) { setText(text, BufferType.NORMAL); }

    public void setText(CharSequence text, BufferType type) {
        if (text == null) text = "";
        CharSequence old = mText;
        if (mListeners != null) for (TextWatcher w : new java.util.ArrayList<>(mListeners)) w.beforeTextChanged(old, 0, old.length(), text.length());
        mText = textForStorage(text);
        layout = null;
        requestLayout();
        invalidate();
        if (mListeners != null) {
            for (TextWatcher w : new java.util.ArrayList<>(mListeners)) w.onTextChanged(mText, 0, old.length(), mText.length());
            Editable e = mText instanceof Editable ? (Editable) mText : new android.text.SpannableStringBuilder(mText);
            for (TextWatcher w : new java.util.ArrayList<>(mListeners)) w.afterTextChanged(e);
        }
        onTextChanged(mText, 0, old.length(), mText.length());
    }

    CharSequence textForStorage(CharSequence text) { return text.toString(); }

    public final void setText(int resid) { setText(getContext().getResources().getText(resid)); }

    public final void setText(char[] text, int start, int len) { setText(new String(text, start, len)); }

    public final void append(CharSequence text) { setText(mText.toString() + text); }

    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {}

    public CharSequence getHint() { return mHint; }

    public final void setHint(CharSequence hint) {
        mHint = hint;
        hintLayout = null;
        if (mText.length() == 0) { requestLayout(); invalidate(); }
    }

    public final void setHint(int resid) { setHint(getContext().getResources().getText(resid)); }

    public void addTextChangedListener(TextWatcher watcher) {
        if (mListeners == null) mListeners = new java.util.ArrayList<>();
        mListeners.add(watcher);
    }

    public void removeTextChangedListener(TextWatcher watcher) { if (mListeners != null) mListeners.remove(watcher); }

    void sendTextChanged(CharSequence old, CharSequence now) {
        if (mListeners == null) return;
        for (TextWatcher w : new java.util.ArrayList<>(mListeners)) w.beforeTextChanged(old, 0, old.length(), now.length());
        for (TextWatcher w : new java.util.ArrayList<>(mListeners)) w.onTextChanged(now, 0, old.length(), now.length());
        Editable e = now instanceof Editable ? (Editable) now : new android.text.SpannableStringBuilder(now);
        for (TextWatcher w : new java.util.ArrayList<>(mListeners)) w.afterTextChanged(e);
    }

    // ------------------------------------------------------------------ appearance
    public void setTextColor(int color) {
        mTextColor = null;
        mCurTextColor = color;
        invalidate();
    }

    public void setTextColor(android.content.res.ColorStateList colors) {
        if (colors == null) return;
        mTextColor = colors;
        mCurTextColor = colors.getDefaultColor();
        invalidate();
    }

    public final int getCurrentTextColor() { return mCurTextColor; }
    public final android.content.res.ColorStateList getTextColors() { return mTextColor != null ? mTextColor : android.content.res.ColorStateList.valueOf(mCurTextColor); }
    public final void setHintTextColor(int color) { mCurHintTextColor = color; invalidate(); }
    public final void setHintTextColor(android.content.res.ColorStateList colors) { if (colors != null) setHintTextColor(colors.getDefaultColor()); }
    public final int getCurrentHintTextColor() { return mCurHintTextColor; }
    public void setLinkTextColor(int color) {}
    public void setHighlightColor(int color) {}

    public float getTextSize() { return mTextPaint.getTextSize(); }

    public void setTextSize(float size) { setTextSize(TypedValue.COMPLEX_UNIT_SP, size); }

    public void setTextSize(int unit, float size) {
        float px = TypedValue.applyDimension(unit, size, getContext().getResources().getDisplayMetrics());
        if (px != mTextPaint.getTextSize()) {
            mTextPaint.setTextSize(px);
            textChanged();
        }
    }

    public void setTypeface(Typeface tf) {
        if (mTextPaint.getTypeface() != tf) {
            mTextPaint.setTypeface(tf);
            textChanged();
        }
    }

    public void setTypeface(Typeface tf, int style) { setTypeface(style == Typeface.NORMAL && tf != null ? tf : Typeface.create(tf, style)); }

    public Typeface getTypeface() { return mTextPaint.getTypeface(); }

    public android.text.TextPaint getPaint() { return mTextPaint; }

    public int getPaintFlags() { return mTextPaint.getFlags(); }

    public void setPaintFlags(int flags) {
        if (mTextPaint.getFlags() != flags) { mTextPaint.setFlags(flags); textChanged(); }
    }

    public void setLetterSpacing(float letterSpacing) {
        if (letterSpacing != mTextPaint.getLetterSpacing()) { mTextPaint.setLetterSpacing(letterSpacing); textChanged(); }
    }

    public float getLetterSpacing() { return mTextPaint.getLetterSpacing(); }

    public void setLineSpacing(float add, float mult) {
        if (mSpacingAdd != add || mSpacingMult != mult) { mSpacingAdd = add; mSpacingMult = mult; textChanged(); }
    }

    public float getLineSpacingExtra() { return mSpacingAdd; }
    public float getLineSpacingMultiplier() { return mSpacingMult; }

    public void setIncludeFontPadding(boolean includepad) {
        if (mIncludePad != includepad) { mIncludePad = includepad; textChanged(); }
    }

    public boolean getIncludeFontPadding() { return mIncludePad; }

    public void setGravity(int gravity) {
        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) gravity |= Gravity.START;
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) gravity |= Gravity.TOP;
        if (gravity != mGravity) { mGravity = gravity; textChanged(); }
    }

    public int getGravity() { return mGravity; }

    public void setAllCaps(boolean allCaps) { mAllCaps = allCaps; textChanged(); }

    public void setShadowLayer(float radius, float dx, float dy, int color) {}

    public void setCompoundDrawablePadding(int pad) {}

    public void setCompoundDrawablesWithIntrinsicBounds(android.graphics.drawable.Drawable l, android.graphics.drawable.Drawable t,
        android.graphics.drawable.Drawable r, android.graphics.drawable.Drawable b) {}

    // ------------------------------------------------------------------ lines and sizes
    public void setMaxLines(int maxLines) { mMaximum = maxLines; mMaxMode = LINES; textChanged(); }
    public int getMaxLines() { return mMaxMode == LINES ? mMaximum : -1; }
    public void setMinLines(int minLines) { mMinimum = minLines; mMinMode = LINES; textChanged(); }
    public int getMinLines() { return mMinMode == LINES ? mMinimum : -1; }
    public void setLines(int lines) { mMaximum = mMinimum = lines; mMaxMode = mMinMode = LINES; textChanged(); }
    public void setMaxHeight(int maxPixels) { mMaximum = maxPixels; mMaxMode = PIXELS; textChanged(); }
    public void setMinHeight(int minPixels) { mMinimum = minPixels; mMinMode = PIXELS; textChanged(); }
    public int getMinHeight() { return mMinMode == PIXELS ? mMinimum : -1; }
    public void setMaxWidth(int maxPixels) { mMaxWidthValue = maxPixels; mMaxWidthMode = PIXELS; textChanged(); }
    public int getMaxWidth() { return mMaxWidthMode == PIXELS ? mMaxWidthValue : -1; }
    public void setMinWidth(int minPixels) { mMinWidthValue = minPixels; mMinWidthMode = PIXELS; textChanged(); }
    public int getMinWidth() { return mMinWidthMode == PIXELS ? mMinWidthValue : -1; }
    public void setWidth(int pixels) { mMaxWidthValue = mMinWidthValue = pixels; mMaxWidthMode = mMinWidthMode = PIXELS; textChanged(); }
    public void setHeight(int pixels) { mMaximum = mMinimum = pixels; mMaxMode = mMinMode = PIXELS; textChanged(); }
    public void setEms(int ems) {}

    public void setSingleLine() { setSingleLine(true); }

    public void setSingleLine(boolean singleLine) {
        mSingleLine = singleLine;
        if (singleLine) {
            mHorizontallyScrolling = true;
            setLines(1);
        } else {
            mHorizontallyScrolling = false;
            mMaximum = Integer.MAX_VALUE;
            mMaxMode = LINES;
            mMinimum = 0;
            textChanged();
        }
    }

    public void setHorizontallyScrolling(boolean whether) { mHorizontallyScrolling = whether; textChanged(); }

    public void setEllipsize(TextUtils.TruncateAt where) { if (mEllipsize != where) { mEllipsize = where; textChanged(); } }

    public TextUtils.TruncateAt getEllipsize() { return mEllipsize; }

    public void setInputType(int type) {
        mInputType = type;
        boolean multi = (type & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if ((type & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT && !multi) {
            if (!mSingleLine) setSingleLine(true);
        } else if ((type & android.text.InputType.TYPE_MASK_CLASS) != android.text.InputType.TYPE_CLASS_TEXT) {
            if (!mSingleLine) setSingleLine(true);
        }
    }

    public int getInputType() { return mInputType; }
    public void setImeOptions(int imeOptions) { mImeOptions = imeOptions; }
    public int getImeOptions() { return mImeOptions; }
    public void setOnEditorActionListener(OnEditorActionListener l) { mEditorActionListener = l; }
    public void setTextIsSelectable(boolean selectable) { mTextSelectable = selectable; }
    public boolean isTextSelectable() { return mTextSelectable; }
    public void setFilters(android.text.InputFilter[] filters) {}
    public void setKeyListener(Object input) {}
    public void setCursorVisible(boolean visible) {}
    public void setSelectAllOnFocus(boolean selectAllOnFocus) {}
    public void setMovementMethod(Object movement) {}
    public void setTransformationMethod(Object method) {}
    public void setAutoSizeTextTypeWithDefaults(int type) {}
    public void setBreakStrategy(int strategy) {}
    public void setHyphenationFrequency(int frequency) {}
    public void setJustificationMode(int mode) {}
    public void setFallbackLineSpacing(boolean enabled) {}
    public void setElegantTextHeight(boolean elegant) {}
    public void setTextLocale(java.util.Locale locale) {}

    private void textChanged() {
        layout = null;
        hintLayout = null;
        requestLayout();
        invalidate();
    }

    @Override void onTextDirectionChanged() { layout = null; hintLayout = null; }

    @Override void onRtlPropertiesChangedInternal() { layout = null; hintLayout = null; }

    public int getLineHeight() {
        Paint.FontMetricsInt fm = mTextPaint.getFontMetricsInt();
        return Math.round((fm.descent - fm.ascent) * mSpacingMult + mSpacingAdd);
    }

    public int getLineCount() { return layout != null ? layout.count : 0; }

    public int getCompoundPaddingLeft() { return mPaddingLeft; }
    public int getCompoundPaddingRight() { return mPaddingRight; }
    public int getCompoundPaddingTop() { return mPaddingTop; }
    public int getCompoundPaddingBottom() { return mPaddingBottom; }
    public int getTotalPaddingLeft() { return mPaddingLeft; }
    public int getTotalPaddingRight() { return mPaddingRight; }
    public int getTotalPaddingTop() { return mPaddingTop + getVerticalOffset(); }

    // ------------------------------------------------------------------ direction
    /** Paragraph direction of the whole text, from the resolved text direction (AOSP TextDirectionHeuristics). */
    boolean textIsRtl(CharSequence text) {
        int dir = getTextDirection();
        boolean viewRtl = isLayoutRtl();
        switch (dir) {
            case TEXT_DIRECTION_RTL: return true;
            case TEXT_DIRECTION_LTR: return false;
            case TEXT_DIRECTION_LOCALE: return false;
            case TEXT_DIRECTION_ANY_RTL: return containsRtl(text);
            case TEXT_DIRECTION_FIRST_STRONG_LTR: return firstStrong(text, false);
            case TEXT_DIRECTION_FIRST_STRONG_RTL: return firstStrong(text, true);
            default: return firstStrong(text, viewRtl);
        }
    }

    private static boolean isRtlChar(char c) {
        if ((c >= 0x0660 && c <= 0x0669) || (c >= 0x06F0 && c <= 0x06F9)) return false;
        return (c >= 0x0590 && c <= 0x08FF) || (c >= 0xFB1D && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF);
    }

    private static boolean containsRtl(CharSequence s) {
        for (int i = 0; i < s.length(); i++) if (isRtlChar(s.charAt(i))) return true;
        return false;
    }

    private static boolean firstStrong(CharSequence s, boolean dflt) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isRtlChar(c)) return true;
            if (Character.isLetter(c)) return false;
        }
        return dflt;
    }

    // ------------------------------------------------------------------ layout of lines
    /** Laid-out lines: text, width and vertical metrics (StaticLayout rules). */
    static final class Lines {
        int count;
        String[] text = new String[4];
        float[] width = new float[4];
        int[] top = new int[5];
        int[] baseline = new int[4];
        int[] descent = new int[4];
        boolean rtl;
        int layoutWidth;
        int heightCapped = -1;
        float maxWidth;

        int height() { return top[count]; }

        void add(String s, float w) {
            if (count == text.length) {
                text = java.util.Arrays.copyOf(text, count * 2);
                width = java.util.Arrays.copyOf(width, count * 2);
                top = java.util.Arrays.copyOf(top, count * 2 + 1);
                baseline = java.util.Arrays.copyOf(baseline, count * 2);
                descent = java.util.Arrays.copyOf(descent, count * 2);
            }
            text[count] = s;
            width[count] = w;
            count++;
            if (w > maxWidth) maxWidth = w;
        }
    }

    private String displayText(CharSequence t) {
        String s = t.toString();
        if (mAllCaps) s = s.toUpperCase();
        if (mSingleLine) s = s.replace('\n', ' ').replace('\r', ' ');
        return s;
    }

    /** Unwrapped width: the widest paragraph (AOSP desired width). */
    private int desiredWidth(CharSequence t) {
        String s = displayText(t);
        float max = 0;
        int start = 0;
        while (true) {
            int nl = s.indexOf('\n', start);
            String p = nl < 0 ? s.substring(start) : s.substring(start, nl);
            max = Math.max(max, mTextPaint.measureText(trimEnd(p)));
            if (nl < 0) break;
            start = nl + 1;
        }
        return (int) Math.ceil(max);
    }

    private static String trimEnd(String p) {
        int e = p.length();
        while (e > 0 && p.charAt(e - 1) == ' ') e--;
        return p.substring(0, e);
    }

    Lines makeLines(CharSequence t, int width) {
        String s = displayText(t);
        Lines out = new Lines();
        out.rtl = textIsRtl(s);
        out.layoutWidth = width;
        boolean ellipsize = mEllipsize == TextUtils.TruncateAt.END || mEllipsize == TextUtils.TruncateAt.MARQUEE;
        int maxLines = mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE;
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        int start = 0;
        while (true) {
            int nl = s.indexOf('\n', start);
            String p = nl < 0 ? s.substring(start) : s.substring(start, nl);
            breakParagraph(p, width, lines);
            if (nl < 0) break;
            start = nl + 1;
        }
        boolean ellipsized = false;
        if (ellipsize && lines.size() > maxLines && maxLines > 0) {
            StringBuilder rest = new StringBuilder(lines.get(maxLines - 1));
            for (int i = maxLines; i < lines.size(); i++) rest.append(' ').append(lines.get(i));
            while (lines.size() > maxLines) lines.remove(lines.size() - 1);
            lines.set(maxLines - 1, ellipsizeEnd(rest.toString(), width, true));
            ellipsized = true;
        } else if (ellipsize && maxLines > 0 && lines.size() <= maxLines && !mHorizontallyScrolling) {
            String last = lines.get(lines.size() - 1);
            if (mTextPaint.measureText(trimEnd(last)) > width) { lines.set(lines.size() - 1, ellipsizeEnd(last, width, false)); ellipsized = true; }
        } else if (ellipsize && mHorizontallyScrolling && lines.size() == 1) {
            String only = lines.get(0);
            if (mTextPaint.measureText(trimEnd(only)) > width) { lines.set(0, ellipsizeEnd(only, width, false)); ellipsized = true; }
        }
        for (String l : lines) out.add(l, mTextPaint.measureText(trimEnd(l)));
        // vertical metrics, StaticLayout.out()
        Paint.FontMetricsInt fm = mTextPaint.getFontMetricsInt();
        boolean needMultiply = mSpacingMult != 1f || mSpacingAdd != 0f;
        int v = 0;
        int maxVisible = maxLines;
        for (int j = 0; j < out.count; j++) {
            int above = fm.ascent, below = fm.descent;
            boolean first = j == 0;
            boolean last = ellipsized ? j == out.count - 1 : j == out.count - 1;
            if (first && mIncludePad) above = fm.top;
            if (last && mIncludePad) below = fm.bottom;
            int extra = 0;
            if (needMultiply && !last) {
                double ex = (below - above) * (mSpacingMult - 1) + mSpacingAdd;
                extra = ex >= 0 ? (int) (ex + 0.5) : -(int) (-ex + 0.5);
            }
            out.top[j] = v;
            out.baseline[j] = v - above;
            out.descent[j] = below + extra;
            if (!ellipsized && j + 1 == maxVisible) {
                int maxLineBelow = mIncludePad ? fm.bottom : below;
                out.heightCapped = v + (maxLineBelow - above);
            }
            v += (below - above) + extra;
        }
        out.top[out.count] = v;
        return out;
    }

    private String ellipsizeEnd(String text, int width, boolean multi) {
        String t = trimEnd(text);
        if (mTextPaint.measureText(t) <= width && !multi) return t;
        float ell = mTextPaint.measureText(String.valueOf(ELLIPSIS));
        int lo = 0, hi = t.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (mTextPaint.measureText(trimEnd(t.substring(0, mid))) + ell <= width) lo = mid; else hi = mid - 1;
        }
        if (multi && lo >= t.length()) return t;
        // keep whole characters only (do not split a surrogate pair)
        if (lo > 0 && Character.isHighSurrogate(t.charAt(lo - 1))) lo--;
        return trimEnd(t.substring(0, lo)) + ELLIPSIS;
    }

    /** Minikin optimal line breaking for one paragraph (no hyphenation, ragged edge). */
    private void breakParagraph(String p, int width, java.util.ArrayList<String> out) {
        if (p.isEmpty()) { out.add(""); return; }
        if (mHorizontallyScrolling || width >= VERY_WIDE || mTextPaint.measureText(trimEnd(p)) <= width) { out.add(p); return; }
        // candidates after each run of spaces; words wider than the line are split at characters
        java.util.ArrayList<int[]> segs = new java.util.ArrayList<>(); // {start, wordEnd, end}
        int i = 0, n = p.length();
        while (i < n) {
            int ws = i;
            while (i < n && p.charAt(i) != ' ') i++;
            int we = i;
            while (i < n && p.charAt(i) == ' ') i++;
            segs.add(new int[] {ws, we, i});
        }
        java.util.ArrayList<Integer> cand = new java.util.ArrayList<>(); // break offsets (end of segment)
        java.util.ArrayList<Float> pre = new java.util.ArrayList<>();
        java.util.ArrayList<Float> post = new java.util.ArrayList<>();
        cand.add(0); pre.add(0f); post.add(0f);
        float sum = 0;
        float spaceW = mTextPaint.measureText(" ");
        for (int[] sg : segs) {
            String word = p.substring(sg[0], sg[1]);
            float ww = mTextPaint.measureText(word);
            if (ww > width && word.length() > 1) {
                // desperate breaks inside the word
                int k = sg[0];
                while (k < sg[1]) {
                    int lo = k + 1, hi = sg[1];
                    while (lo < hi) {
                        int mid = (lo + hi + 1) >>> 1;
                        if (mTextPaint.measureText(p.substring(k, mid)) <= width) lo = mid; else hi = mid - 1;
                    }
                    if (lo < sg[1] && Character.isLowSurrogate(p.charAt(lo))) lo = Math.max(k + 1, lo - 1);
                    float pw = mTextPaint.measureText(p.substring(k, lo));
                    sum += pw;
                    k = lo;
                    if (k < sg[1]) { cand.add(k); pre.add(sum); post.add(sum); }
                }
                float spaces = (sg[2] - sg[1]) * spaceW;
                cand.add(sg[2]); pre.add(sum); post.add(sum + spaces);
                sum += spaces;
            } else {
                float spaces = (sg[2] - sg[1]) * spaceW;
                cand.add(sg[2]); pre.add(sum + ww); post.add(sum + ww + spaces);
                sum += ww + spaces;
            }
        }
        int nc = cand.size();
        float linePenalty = 0.5f * mTextPaint.getTextSize() * width * 2f;
        float[] score = new float[nc];
        int[] prev = new int[nc];
        score[0] = 0;
        int active = 0;
        for (int c = 1; c < nc; c++) {
            boolean atEnd = c == nc - 1;
            float best = Float.MAX_VALUE;
            int bestPrev = 0;
            float leftEdge = post.get(c) - width;
            float bestHope = 0;
            for (int j = active; j < c; j++) {
                float jScore = score[j];
                if (jScore + bestHope >= best) continue;
                float delta = pre.get(j) - leftEdge;
                float widthScore;
                if (delta < 0) widthScore = 1e12f;
                else if (atEnd) widthScore = 0f;
                else widthScore = delta * delta;
                if (delta < 0) active = j + 1; else bestHope = widthScore;
                float s = jScore + widthScore;
                if (s <= best) { best = s; bestPrev = j; }
            }
            score[c] = best + linePenalty;
            prev[c] = bestPrev;
        }
        java.util.ArrayList<Integer> breaks = new java.util.ArrayList<>();
        for (int c = nc - 1; c > 0; c = prev[c]) breaks.add(0, c);
        int from = 0;
        for (int b : breaks) {
            int end = cand.get(b);
            out.add(p.substring(from, end));
            from = end;
        }
    }

    private Lines layoutFor(CharSequence t, int width, boolean hint) {
        Lines cached = hint ? hintLayout : layout;
        if (cached != null && cached.layoutWidth == width && cached.rtl == textIsRtl(displayText(t))) return cached;
        Lines l = makeLines(t, width);
        if (hint) hintLayout = l; else layout = l;
        return l;
    }

    private Lines currentLines() {
        boolean showHint = mText.length() == 0 && mHint != null;
        int width = Math.max(0, getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight());
        if (mHorizontallyScrolling && !(mEllipsize == TextUtils.TruncateAt.END)) width = VERY_WIDE;
        return layoutFor(showHint ? mHint : mText, width, showHint);
    }

    // ------------------------------------------------------------------ measuring (AOSP TextView.onMeasure)
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int width;
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else {
            int des = desiredWidth(mText);
            if (mHint != null) des = Math.max(des, desiredWidth(mHint));
            width = des + getCompoundPaddingLeft() + getCompoundPaddingRight();
            if (mMaxWidthMode == PIXELS) width = Math.min(width, mMaxWidthValue);
            if (mMinWidthMode == PIXELS) width = Math.max(width, mMinWidthValue);
            width = Math.max(width, getSuggestedMinimumWidth());
            if (widthMode == MeasureSpec.AT_MOST) width = Math.min(widthSize, width);
        }
        int want = width - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int layoutWidth = mHorizontallyScrolling && mEllipsize != TextUtils.TruncateAt.END ? VERY_WIDE : Math.max(0, want);
        Lines textLines = layoutFor(mText, layoutWidth, false);
        Lines measureLines = textLines;
        if (mText.length() == 0 && mHint != null) measureLines = layoutFor(mHint, layoutWidth, true);
        int height;
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            int desired = getDesiredHeight(measureLines);
            height = desired;
            if (heightMode == MeasureSpec.AT_MOST) height = Math.min(desired, heightSize);
        }
        setMeasuredDimension(width, height);
    }

    private int getDesiredHeight(Lines l) {
        int linecount = l.count;
        int pad = getCompoundPaddingTop() + getCompoundPaddingBottom();
        int desired = l.height();
        if (mMaxMode == LINES) {
            if (linecount > mMaximum) {
                desired = l.heightCapped >= 0 ? l.heightCapped : l.top[mMaximum];
                desired += pad;
                linecount = mMaximum;
            } else {
                desired += pad;
            }
        } else {
            desired += pad;
            desired = Math.min(desired, mMaximum);
        }
        if (mMinMode == LINES) {
            if (linecount < mMinimum) desired += getLineHeight() * (mMinimum - linecount);
        } else {
            desired = Math.max(desired, mMinimum);
        }
        desired = Math.max(desired, getSuggestedMinimumHeight());
        return desired;
    }

    int getVerticalOffset() {
        int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        Lines l = currentLines();
        int voffset = 0;
        if (gravity != Gravity.TOP) {
            int boxht = getHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
            int textht = l.height();
            if (mMaxMode == LINES && l.count > mMaximum) textht = l.heightCapped >= 0 ? l.heightCapped : l.top[mMaximum];
            if (textht < boxht) {
                if (gravity == Gravity.BOTTOM) voffset = boxht - textht; else voffset = (boxht - textht) >> 1;
            }
        }
        return voffset;
    }

    @Override
    public int getBaseline() {
        Lines l = currentLines();
        if (l.count == 0) return -1;
        return getCompoundPaddingTop() + getVerticalOffset() + l.baseline[0];
    }

    // ------------------------------------------------------------------ drawing
    /** Horizontal start of a line within the text box (alignment per AOSP Layout). */
    float lineLeft(Lines l, int i, int boxWidth) {
        int hg = mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        float w = l.width[i];
        int align; // 0 normal, 1 opposite, 2 center, 3 left, 4 right
        int ta = getTextAlignment();
        if (ta == TEXT_ALIGNMENT_GRAVITY) {
            switch (hg) {
                case Gravity.START: align = 0; break;
                case Gravity.END: align = 1; break;
                case Gravity.LEFT: align = 3; break;
                case Gravity.RIGHT: align = 4; break;
                case Gravity.CENTER_HORIZONTAL: align = 2; break;
                default: align = 0;
            }
        } else if (ta == TEXT_ALIGNMENT_CENTER) align = 2;
        else if (ta == TEXT_ALIGNMENT_TEXT_END) align = 1;
        else if (ta == TEXT_ALIGNMENT_VIEW_START) align = isLayoutRtl() ? 4 : 3;
        else if (ta == TEXT_ALIGNMENT_VIEW_END) align = isLayoutRtl() ? 3 : 4;
        else align = 0;
        boolean rtl = l.rtl;
        if (align == 0) align = rtl ? 4 : 3;
        else if (align == 1) align = rtl ? 3 : 4;
        int box = Math.min(boxWidth, l.layoutWidth >= VERY_WIDE ? boxWidth : l.layoutWidth);
        if (l.layoutWidth >= VERY_WIDE) box = boxWidth;
        switch (align) {
            case 2: return (box - w) / 2f;
            case 4: return box - w;
            default: return 0;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean showHint = mText.length() == 0 && mHint != null;
        Lines l = currentLines();
        if (l.count == 0) return;
        int color = showHint ? mCurHintTextColor : mCurTextColor;
        if (mTextColor != null) color = mTextColor.getColorForState(getDrawableState(), mCurTextColor);
        mTextPaint.setColor(color);
        int compoundPaddingLeft = getCompoundPaddingLeft();
        int compoundPaddingTop = getCompoundPaddingTop();
        int compoundPaddingRight = getCompoundPaddingRight();
        int compoundPaddingBottom = getCompoundPaddingBottom();
        int scrollX = mScrollX, scrollY = mScrollY;
        int right = mRight, left = mLeft, bottom = mBottom, top = mTop;
        int vspace = bottom - top - compoundPaddingBottom - compoundPaddingTop;
        int hspace = right - left - compoundPaddingRight - compoundPaddingLeft;
        canvas.save();
        float clipLeft = compoundPaddingLeft + scrollX;
        float clipTop = (scrollY == 0) ? 0 : mPaddingTop + scrollY;
        float clipRight = right - left - compoundPaddingRight + scrollX;
        float clipBottom = bottom - top + scrollY - ((scrollY == maxScrollY()) ? 0 : mPaddingBottom);
        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);
        int voffset = getVerticalOffset();
        canvas.translate(compoundPaddingLeft, compoundPaddingTop + voffset);
        int visible = mMaxMode == LINES ? Math.min(l.count, mMaximum) : l.count;
        for (int i = 0; i < visible; i++) {
            String s = l.text[i];
            if (s.isEmpty()) continue;
            float x = lineLeft(l, i, hspace);
            float y = l.baseline[i];
            if (mHorizontallyScrolling && l.layoutWidth >= VERY_WIDE && l.width[i] > hspace) {
                // a too-long single line shows its start (right edge for right-to-left text)
                x = l.rtl ? hspace - l.width[i] : 0;
            }
            canvas.drawTextRun(trimEnd(s), x, y, "left", l.rtl, mTextPaint);
            if ((mTextPaint.getFlags() & Paint.UNDERLINE_TEXT_FLAG) != 0) {
                float thick = Math.max(1f, mTextPaint.getTextSize() / 18f);
                canvas.drawRect(x, y + thick * 1.5f, x + l.width[i], y + thick * 2.5f, mTextPaint);
            }
        }
        canvas.restore();
    }

    private int maxScrollY() { return 0; }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mTextColor != null && mTextColor.isStateful()) invalidate();
    }

    @Override public boolean onCheckIsTextEditor() { return false; }

    /** Lines currently shown (for the browser text field overlay). */
    String firstLineText() { Lines l = currentLines(); return l.count > 0 ? l.text[0] : ""; }

    public Paint textPaint() { return mTextPaint; }
}
