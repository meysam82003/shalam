package android.graphics;

import divan.web.JS;
import org.teavm.jso.JSObject;

/** Drawing attributes with Android's behaviour; turned into canvas state by {@link Canvas}. */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 0x01;
    public static final int FILTER_BITMAP_FLAG = 0x02;
    public static final int DITHER_FLAG = 0x04;
    public static final int UNDERLINE_TEXT_FLAG = 0x08;
    public static final int STRIKE_THRU_TEXT_FLAG = 0x10;
    public static final int FAKE_BOLD_TEXT_FLAG = 0x20;
    public static final int LINEAR_TEXT_FLAG = 0x40;
    public static final int SUBPIXEL_TEXT_FLAG = 0x80;

    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Align { LEFT, CENTER, RIGHT }

    public static class FontMetrics {
        public float top, ascent, descent, bottom, leading;
    }

    public static class FontMetricsInt {
        public int top, ascent, descent, bottom, leading;
    }

    // Vazirmatn (2048 units/em): hhea ascender 2100, descender -1100, bbox yMax 2163, yMin -1142 (regular) / -1165 (bold).
    private static final float VZ_ASCENT = 2100f / 2048f, VZ_DESCENT = 1100f / 2048f, VZ_TOP = 2163f / 2048f;
    private static final float VZ_BOTTOM_REGULAR = 1142f / 2048f, VZ_BOTTOM_BOLD = 1165f / 2048f;

    private int flags;
    private int color = 0xFF000000;
    private Style style = Style.FILL;
    private float strokeWidth = 0;
    private float strokeMiter = 4;
    private Cap cap = Cap.BUTT;
    private Join join = Join.MITER;
    private float textSize = 12;
    private Align align = Align.LEFT;
    private Typeface typeface;
    private Shader shader;
    private PathEffect pathEffect;
    private float letterSpacing;
    private float textScaleX = 1;
    private ColorFilter colorFilter;

    private JSObject colorStyle;
    private String font;

    public Paint() { this(ANTI_ALIAS_FLAG); }

    public Paint(int flags) { this.flags = flags; }

    public Paint(Paint p) { set(p); }

    public void set(Paint p) {
        flags = p.flags; color = p.color; style = p.style; strokeWidth = p.strokeWidth; strokeMiter = p.strokeMiter; cap = p.cap; join = p.join;
        textSize = p.textSize; align = p.align; typeface = p.typeface; shader = p.shader; pathEffect = p.pathEffect; letterSpacing = p.letterSpacing;
        textScaleX = p.textScaleX; colorFilter = p.colorFilter; colorStyle = p.colorStyle; font = p.font;
    }

    public void reset() { set(new Paint(ANTI_ALIAS_FLAG)); }

    public int getFlags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }
    public void setAntiAlias(boolean aa) { flags = aa ? flags | ANTI_ALIAS_FLAG : flags & ~ANTI_ALIAS_FLAG; }
    public final boolean isAntiAlias() { return (flags & ANTI_ALIAS_FLAG) != 0; }
    public void setFilterBitmap(boolean filter) { flags = filter ? flags | FILTER_BITMAP_FLAG : flags & ~FILTER_BITMAP_FLAG; }
    public final boolean isFilterBitmap() { return (flags & FILTER_BITMAP_FLAG) != 0; }
    public void setDither(boolean dither) {}
    public void setFakeBoldText(boolean b) { flags = b ? flags | FAKE_BOLD_TEXT_FLAG : flags & ~FAKE_BOLD_TEXT_FLAG; font = null; }
    public void setUnderlineText(boolean b) { flags = b ? flags | UNDERLINE_TEXT_FLAG : flags & ~UNDERLINE_TEXT_FLAG; }
    public void setStrikeThruText(boolean b) { flags = b ? flags | STRIKE_THRU_TEXT_FLAG : flags & ~STRIKE_THRU_TEXT_FLAG; }

    public int getColor() { return color; }

    public void setColor(int color) {
        if (this.color != color) colorStyle = null;
        this.color = color;
    }

    public int getAlpha() { return color >>> 24; }

    public void setAlpha(int a) { setColor(((Math.max(0, Math.min(255, a))) << 24) | (color & 0xFFFFFF)); }

    public void setARGB(int a, int r, int g, int b) { setColor((a << 24) | (r << 16) | (g << 8) | b); }

    public Style getStyle() { return style; }
    public void setStyle(Style style) { this.style = style; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(float width) { strokeWidth = Math.max(0, width); }
    public float getStrokeMiter() { return strokeMiter; }
    public void setStrokeMiter(float miter) { strokeMiter = miter; }
    public Cap getStrokeCap() { return cap; }
    public void setStrokeCap(Cap cap) { this.cap = cap; }
    public Join getStrokeJoin() { return join; }
    public void setStrokeJoin(Join join) { this.join = join; }
    public float getTextSize() { return textSize; }

    public void setTextSize(float textSize) {
        if (textSize > 0 && textSize != this.textSize) { this.textSize = textSize; font = null; }
    }

    public Align getTextAlign() { return align; }
    public void setTextAlign(Align align) { this.align = align; }
    public Typeface getTypeface() { return typeface; }

    public Typeface setTypeface(Typeface tf) {
        if (tf != typeface) font = null;
        typeface = tf;
        return tf;
    }

    public Shader getShader() { return shader; }
    public Shader setShader(Shader shader) { this.shader = shader; return shader; }
    public PathEffect getPathEffect() { return pathEffect; }
    public PathEffect setPathEffect(PathEffect effect) { pathEffect = effect; return effect; }
    public ColorFilter getColorFilter() { return colorFilter; }
    public ColorFilter setColorFilter(ColorFilter filter) { colorFilter = filter; return filter; }
    public float getLetterSpacing() { return letterSpacing; }
    public void setLetterSpacing(float ls) { letterSpacing = ls; }
    public float getTextScaleX() { return textScaleX; }
    public void setTextScaleX(float s) { textScaleX = s; }
    public void setShadowLayer(float radius, float dx, float dy, int shadowColor) {}
    public void clearShadowLayer() {}

    // ------------------------------------------------------------------ text metrics
    private Typeface face() { return typeface != null ? typeface : Typeface.DEFAULT; }

    private boolean bold() { return face().bold || (flags & FAKE_BOLD_TEXT_FLAG) != 0; }

    /** CSS font of this paint. */
    public String cssFont() {
        if (font == null) {
            Typeface t = face();
            font = bold() && !t.bold ? Typeface.create(t, Typeface.BOLD).css(textSize) : t.css(textSize);
        }
        return font;
    }

    public float ascent() { return -VZ_ASCENT * textSize; }

    public float descent() { return VZ_DESCENT * textSize; }

    public float getFontSpacing() { return getFontMetrics(null); }

    public FontMetrics getFontMetrics() {
        FontMetrics fm = new FontMetrics();
        getFontMetrics(fm);
        return fm;
    }

    public float getFontMetrics(FontMetrics m) {
        float s = textSize;
        float top = -VZ_TOP * s, ascent = -VZ_ASCENT * s, descent = VZ_DESCENT * s;
        float bottom = (bold() ? VZ_BOTTOM_BOLD : VZ_BOTTOM_REGULAR) * s;
        if (m != null) { m.top = top; m.ascent = ascent; m.descent = descent; m.bottom = bottom; m.leading = 0; }
        return descent - ascent;
    }

    public FontMetricsInt getFontMetricsInt() {
        FontMetricsInt fm = new FontMetricsInt();
        getFontMetricsInt(fm);
        return fm;
    }

    /** Same rounding as Skia/Android: top floored, ascent and descent rounded, bottom ceiled. */
    public int getFontMetricsInt(FontMetricsInt m) {
        FontMetrics f = new FontMetrics();
        getFontMetrics(f);
        int top = (int) Math.floor(f.top), ascent = Math.round(f.ascent), descent = Math.round(f.descent), bottom = (int) Math.ceil(f.bottom);
        if (m != null) { m.top = top; m.ascent = ascent; m.descent = descent; m.bottom = bottom; m.leading = 0; }
        return descent - ascent;
    }

    /** Paragraph direction Android uses for drawText/measureText (first strong character, LTR by default). */
    public static String direction(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x0590 && c <= 0x08FF) || (c >= 0xFB1D && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF)) {
                // Arabic-Indic digits are weak, not strong
                if ((c >= 0x0660 && c <= 0x0669) || (c >= 0x06F0 && c <= 0x06F9) || c == 0x066B || c == 0x066C) continue;
                return "rtl";
            }
            if (Character.isLetter(c)) return "ltr";
        }
        return "ltr";
    }

    public float measureText(String text) {
        if (text == null || text.isEmpty()) return 0;
        float w = JS.measure(cssFont(), text, direction(text));
        if (letterSpacing != 0) w += letterSpacing * textSize * text.length();
        return w * textScaleX;
    }

    public float measureText(CharSequence text, int start, int end) { return measureText(text.subSequence(start, end).toString()); }

    public float measureText(String text, int start, int end) { return measureText(text.substring(start, end)); }

    public float measureText(char[] text, int index, int count) { return measureText(new String(text, index, count)); }

    public void getTextBounds(String text, int start, int end, Rect bounds) {
        float w = measureText(text, start, end);
        bounds.set(0, Math.round(ascent()), (int) Math.ceil(w), Math.round(descent()));
    }

    public int breakText(String text, boolean measureForwards, float maxWidth, float[] measuredWidth) {
        int n = text.length();
        int lo = 0, hi = n;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            float w = measureForwards ? measureText(text, 0, mid) : measureText(text, n - mid, n);
            if (w <= maxWidth) lo = mid; else hi = mid - 1;
        }
        if (measuredWidth != null && measuredWidth.length > 0) measuredWidth[0] = lo == 0 ? 0 : (measureForwards ? measureText(text, 0, lo) : measureText(text, n - lo, n));
        return lo;
    }

    // ------------------------------------------------------------------ canvas state
    /** Fill or stroke style for the canvas (colour string or the shader's gradient/pattern). */
    JSObject canvasStyle() {
        if (shader != null) return shader.style();
        if (colorStyle == null) colorStyle = JS.str(Color.css(color));
        return colorStyle;
    }

    /** Alpha the canvas applies: with a shader the paint's alpha modulates it, a plain colour already carries it. */
    float canvasAlpha() { return shader != null ? (color >>> 24) / 255f : 1f; }

    String capCss() { return cap == Cap.ROUND ? "round" : cap == Cap.SQUARE ? "square" : "butt"; }

    String joinCss() { return join == Join.ROUND ? "round" : join == Join.BEVEL ? "bevel" : "miter"; }

    float[] dash() { return pathEffect instanceof DashPathEffect ? ((DashPathEffect) pathEffect).intervals : null; }

    float dashPhase() { return pathEffect instanceof DashPathEffect ? ((DashPathEffect) pathEffect).phase : 0; }
}
