package android.text;

public class TextPaint extends android.graphics.Paint {
    public int bgColor;
    public int baselineShift;
    public int linkColor;
    public float density = 1.0f;

    public TextPaint() { super(); }
    public TextPaint(int flags) { super(flags); }
    public TextPaint(android.graphics.Paint p) { super(p); }
}
