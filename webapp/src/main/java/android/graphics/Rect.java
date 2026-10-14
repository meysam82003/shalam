package android.graphics;

/** Integer rectangle (AOSP semantics: right and bottom are exclusive, empty when left >= right or top >= bottom). */
public final class Rect {
    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() {}

    public Rect(int left, int top, int right, int bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }

    public Rect(Rect r) { if (r != null) { left = r.left; top = r.top; right = r.right; bottom = r.bottom; } }

    public final boolean isEmpty() { return left >= right || top >= bottom; }
    public final int width() { return right - left; }
    public final int height() { return bottom - top; }
    public final int centerX() { return (left + right) >> 1; }
    public final int centerY() { return (top + bottom) >> 1; }
    public final float exactCenterX() { return (left + right) * 0.5f; }
    public final float exactCenterY() { return (top + bottom) * 0.5f; }
    public void setEmpty() { left = right = top = bottom = 0; }
    public void set(int left, int top, int right, int bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }
    public void set(Rect src) { left = src.left; top = src.top; right = src.right; bottom = src.bottom; }
    public void offset(int dx, int dy) { left += dx; top += dy; right += dx; bottom += dy; }
    public void offsetTo(int newLeft, int newTop) { right += newLeft - left; bottom += newTop - top; left = newLeft; top = newTop; }
    public void inset(int dx, int dy) { left += dx; top += dy; right -= dx; bottom -= dy; }
    public void inset(int l, int t, int r, int b) { left += l; top += t; right -= r; bottom -= b; }
    public boolean contains(int x, int y) { return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom; }
    public boolean contains(Rect r) { return left < right && top < bottom && left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom; }

    public boolean intersect(int l, int t, int r, int b) {
        if (left < r && l < right && top < b && t < bottom) {
            if (left < l) left = l;
            if (top < t) top = t;
            if (right > r) right = r;
            if (bottom > b) bottom = b;
            return true;
        }
        return false;
    }

    public boolean intersect(Rect r) { return intersect(r.left, r.top, r.right, r.bottom); }

    public boolean intersects(int l, int t, int r, int b) { return left < r && l < right && top < b && t < bottom; }

    public static boolean intersects(Rect a, Rect b) { return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom; }

    public void union(int l, int t, int r, int b) {
        if (l < r && t < b) {
            if (left < right && top < bottom) {
                if (left > l) left = l;
                if (top > t) top = t;
                if (right < r) right = r;
                if (bottom < b) bottom = b;
            } else {
                left = l; top = t; right = r; bottom = b;
            }
        }
    }

    public void union(Rect r) { union(r.left, r.top, r.right, r.bottom); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rect)) return false;
        Rect r = (Rect) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override public int hashCode() { return 31 * (31 * (31 * left + top) + right) + bottom; }

    @Override public String toString() { return "Rect(" + left + ", " + top + " - " + right + ", " + bottom + ")"; }
}
