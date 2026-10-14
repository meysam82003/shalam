package android.graphics;

public final class Insets {
    public static final Insets NONE = new Insets(0, 0, 0, 0);
    public final int left, top, right, bottom;

    private Insets(int left, int top, int right, int bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }

    public static Insets of(int left, int top, int right, int bottom) {
        if (left == 0 && top == 0 && right == 0 && bottom == 0) return NONE;
        return new Insets(left, top, right, bottom);
    }

    public static Insets max(Insets a, Insets b) { return of(Math.max(a.left, b.left), Math.max(a.top, b.top), Math.max(a.right, b.right), Math.max(a.bottom, b.bottom)); }
}
