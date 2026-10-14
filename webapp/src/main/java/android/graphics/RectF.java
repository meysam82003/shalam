package android.graphics;

/** Float rectangle with the AOSP RectF behaviour. */
public class RectF {
    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {}

    public RectF(float left, float top, float right, float bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }

    public RectF(RectF r) { if (r != null) { left = r.left; top = r.top; right = r.right; bottom = r.bottom; } }

    public RectF(Rect r) { if (r != null) { left = r.left; top = r.top; right = r.right; bottom = r.bottom; } }

    public final boolean isEmpty() { return left >= right || top >= bottom; }
    public final float width() { return right - left; }
    public final float height() { return bottom - top; }
    public final float centerX() { return (left + right) * 0.5f; }
    public final float centerY() { return (top + bottom) * 0.5f; }
    public void setEmpty() { left = right = top = bottom = 0; }
    public void set(float left, float top, float right, float bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }
    public void set(RectF src) { left = src.left; top = src.top; right = src.right; bottom = src.bottom; }
    public void set(Rect src) { left = src.left; top = src.top; right = src.right; bottom = src.bottom; }
    public void offset(float dx, float dy) { left += dx; top += dy; right += dx; bottom += dy; }
    public void offsetTo(float newLeft, float newTop) { right += newLeft - left; bottom += newTop - top; left = newLeft; top = newTop; }
    public void inset(float dx, float dy) { left += dx; top += dy; right -= dx; bottom -= dy; }
    public boolean contains(float x, float y) { return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom; }

    public boolean intersect(float l, float t, float r, float b) {
        if (left < r && l < right && top < b && t < bottom) {
            if (left < l) left = l;
            if (top < t) top = t;
            if (right > r) right = r;
            if (bottom > b) bottom = b;
            return true;
        }
        return false;
    }

    public boolean intersect(RectF r) { return intersect(r.left, r.top, r.right, r.bottom); }

    public void union(float l, float t, float r, float b) {
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

    public void union(RectF r) { union(r.left, r.top, r.right, r.bottom); }

    public void sort() {
        if (left > right) { float t = left; left = right; right = t; }
        if (top > bottom) { float t = top; top = bottom; bottom = t; }
    }

    public void round(Rect dst) { dst.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom)); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RectF)) return false;
        RectF r = (RectF) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override public int hashCode() { return 31 * (31 * (31 * Float.floatToIntBits(left) + Float.floatToIntBits(top)) + Float.floatToIntBits(right)) + Float.floatToIntBits(bottom); }

    @Override public String toString() { return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")"; }
}
