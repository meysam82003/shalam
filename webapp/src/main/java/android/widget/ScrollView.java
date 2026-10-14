/*
 * Measure, layout and scroll clamping follow AOSP ScrollView (android-14.0.0_r1), Apache License 2.0.
 */
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class ScrollView extends FrameLayout implements divan.web.TouchScrollable {
    static final int ANIMATED_SCROLL_GAP = 250;

    private final OverScroller mScroller;
    private boolean mFillViewport;
    private boolean mSmoothScrollingEnabled = true;
    private long mLastScroll;

    public ScrollView(Context context) {
        super(context);
        mScroller = new OverScroller(context);
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
    }

    public ScrollView(Context context, android.util.AttributeSet attrs) { this(context); }

    @Override public boolean shouldDelayChildPressedState() { return true; }

    public int getMaxScrollAmount() { return (int) (0.5f * (mBottom - mTop)); }

    @Override public void addView(View child) {
        if (getChildCount() > 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(child);
    }

    @Override public void addView(View child, int index) {
        if (getChildCount() > 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(child, index);
    }

    @Override public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(child, params);
    }

    @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(child, index, params);
    }

    private boolean canScroll() {
        View child = getChildAt(0);
        if (child != null) {
            int childHeight = child.getHeight();
            return getHeight() < childHeight + mPaddingTop + mPaddingBottom;
        }
        return false;
    }

    public boolean isFillViewport() { return mFillViewport; }

    public void setFillViewport(boolean fillViewport) {
        if (fillViewport != mFillViewport) { mFillViewport = fillViewport; requestLayout(); }
    }

    public boolean isSmoothScrollingEnabled() { return mSmoothScrollingEnabled; }
    public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) { mSmoothScrollingEnabled = smoothScrollingEnabled; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mFillViewport) return;
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.UNSPECIFIED) return;
        if (getChildCount() > 0) {
            final View child = getChildAt(0);
            final FrameLayout.LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int widthPadding = mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin;
            final int heightPadding = mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin;
            final int desiredHeight = getMeasuredHeight() - heightPadding;
            if (child.getMeasuredHeight() < desiredHeight) {
                final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, widthPadding, lp.width);
                final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, mPaddingLeft + mPaddingRight, lp.width);
        final int verticalPadding = mPaddingTop + mPaddingBottom;
        int childHeightMeasureSpec = MeasureSpec.makeSafeMeasureSpec(Math.max(0, MeasureSpec.getSize(parentHeightMeasureSpec) - verticalPadding), MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
        final int usedTotal = mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin + heightUsed;
        final int childHeightMeasureSpec = MeasureSpec.makeSafeMeasureSpec(Math.max(0, MeasureSpec.getSize(parentHeightMeasureSpec) - usedTotal), MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!isLaidOut()) {
            final int childHeight = (getChildCount() > 0) ? getChildAt(0).getMeasuredHeight() : 0;
            final int scrollRange = Math.max(0, childHeight - (b - t - mPaddingBottom - mPaddingTop));
            if (mScrollY > scrollRange) mScrollY = scrollRange;
            else if (mScrollY < 0) mScrollY = 0;
        }
        scrollTo(mScrollX, mScrollY);
    }

    private int getScrollRange() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0, child.getHeight() - (getHeight() - mPaddingBottom - mPaddingTop));
        }
        return scrollRange;
    }

    @Override
    public void scrollTo(int x, int y) {
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            x = clamp(x, getWidth() - mPaddingRight - mPaddingLeft, child.getWidth());
            y = clamp(y, getHeight() - mPaddingBottom - mPaddingTop, child.getHeight());
            if (x != mScrollX || y != mScrollY) super.scrollTo(x, y);
        }
    }

    private static int clamp(int n, int my, int child) {
        if (my >= child || n < 0) return 0;
        if ((my + n) > child) return child - my;
        return n;
    }

    public final void smoothScrollBy(int dx, int dy) {
        if (getChildCount() == 0) return;
        long duration = android.view.animation.AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if (duration > ANIMATED_SCROLL_GAP) {
            final int height = getHeight() - mPaddingBottom - mPaddingTop;
            final int bottom = getChildAt(0).getHeight();
            final int maxY = Math.max(0, bottom - height);
            final int scrollY = mScrollY;
            dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;
            mScroller.startScroll(mScrollX, scrollY, 0, dy);
            postInvalidateOnAnimation();
        } else {
            if (!mScroller.isFinished()) mScroller.abortAnimation();
            scrollBy(dx, dy);
        }
        mLastScroll = android.view.animation.AnimationUtils.currentAnimationTimeMillis();
    }

    public final void smoothScrollTo(int x, int y) { smoothScrollBy(x - mScrollX, y - mScrollY); }

    public boolean fullScroll(int direction) {
        boolean down = direction == View.FOCUS_DOWN;
        int height = getHeight();
        int target = 0;
        if (down && getChildCount() > 0) target = Math.max(0, getChildAt(0).getBottom() + mPaddingBottom - height);
        smoothScrollTo(mScrollX, target);
        return true;
    }

    public boolean pageScroll(int direction) {
        int height = getHeight();
        smoothScrollTo(mScrollX, mScrollY + (direction == View.FOCUS_DOWN ? height : -height));
        return true;
    }

    public boolean arrowScroll(int direction) { return pageScroll(direction); }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int oldX = mScrollX, oldY = mScrollY;
            int x = mScroller.getCurrX(), y = mScroller.getCurrY();
            if (oldX != x || oldY != y) scrollTo(x, y);
            if (!mScroller.isFinished()) postInvalidateOnAnimation();
        }
    }

    public void fling(int velocityY) {
        if (getChildCount() > 0) {
            int height = getHeight() - mPaddingBottom - mPaddingTop;
            int bottom = getChildAt(0).getHeight();
            mScroller.fling(mScrollX, mScrollY, 0, velocityY, 0, 0, 0, Math.max(0, bottom - height));
            postInvalidateOnAnimation();
        }
    }

    @Override public boolean canScrollVertically(int direction) {
        final int offset = mScrollY;
        final int range = getScrollRange();
        if (range == 0) return false;
        return direction < 0 ? offset > 0 : offset < range;
    }

    @Override public boolean scrollsVertically() { return true; }
    @Override public boolean canScrollBy(int delta) { return canScroll() && canScrollVertically(delta); }
    @Override public void dragBy(int delta) { if (!mScroller.isFinished()) mScroller.abortAnimation(); scrollTo(mScrollX, mScrollY + delta); }
    @Override public void flingWith(int velocity) { fling(velocity); }
    @Override public void stopFling() { if (!mScroller.isFinished()) mScroller.forceFinished(true); }
}
