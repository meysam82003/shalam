/*
 * Measure, layout (including the right-to-left start position) and clamping follow AOSP HorizontalScrollView
 * (android-14.0.0_r1), Apache License 2.0.
 */
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class HorizontalScrollView extends FrameLayout implements divan.web.TouchScrollable {
    private final OverScroller mScroller;
    private boolean mFillViewport;
    private long mLastScroll;

    public HorizontalScrollView(Context context) {
        super(context);
        mScroller = new OverScroller(context);
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
    }

    public HorizontalScrollView(Context context, android.util.AttributeSet attrs) { this(context); }

    @Override public boolean shouldDelayChildPressedState() { return true; }

    @Override public void addView(View child) {
        if (getChildCount() > 0) throw new IllegalStateException("HorizontalScrollView can host only one direct child");
        super.addView(child);
    }

    @Override public void addView(View child, int index) {
        if (getChildCount() > 0) throw new IllegalStateException("HorizontalScrollView can host only one direct child");
        super.addView(child, index);
    }

    @Override public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) throw new IllegalStateException("HorizontalScrollView can host only one direct child");
        super.addView(child, params);
    }

    @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) throw new IllegalStateException("HorizontalScrollView can host only one direct child");
        super.addView(child, index, params);
    }

    private boolean canScroll() {
        View child = getChildAt(0);
        if (child != null) return getWidth() < child.getWidth() + mPaddingLeft + mPaddingRight;
        return false;
    }

    public boolean isFillViewport() { return mFillViewport; }
    public void setFillViewport(boolean fillViewport) { if (fillViewport != mFillViewport) { mFillViewport = fillViewport; requestLayout(); } }
    public void setSmoothScrollingEnabled(boolean enabled) {}

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mFillViewport) return;
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode == MeasureSpec.UNSPECIFIED) return;
        if (getChildCount() > 0) {
            final View child = getChildAt(0);
            final FrameLayout.LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int widthPadding = mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin;
            final int heightPadding = mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin;
            final int desiredWidth = getMeasuredWidth() - widthPadding;
            if (child.getMeasuredWidth() < desiredWidth) {
                final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY);
                final int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, heightPadding, lp.height);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        final int horizontalPadding = mPaddingLeft + mPaddingRight;
        final int childWidthMeasureSpec = MeasureSpec.makeSafeMeasureSpec(Math.max(0, MeasureSpec.getSize(parentWidthMeasureSpec) - horizontalPadding), MeasureSpec.UNSPECIFIED);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, mPaddingTop + mPaddingBottom, lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
        final int usedTotal = mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin + widthUsed;
        final int childWidthMeasureSpec = MeasureSpec.makeSafeMeasureSpec(Math.max(0, MeasureSpec.getSize(parentWidthMeasureSpec) - usedTotal), MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childWidth = 0;
        int childMargins = 0;
        if (getChildCount() > 0) {
            childWidth = getChildAt(0).getMeasuredWidth();
            LayoutParams childParams = (LayoutParams) getChildAt(0).getLayoutParams();
            childMargins = childParams.leftMargin + childParams.rightMargin;
        }
        final int available = r - l - getPaddingLeftWithForeground() - getPaddingRightWithForeground() - childMargins;
        final boolean forceLeftGravity = (childWidth > available);
        layoutChildren(l, t, r, b, forceLeftGravity);
        if (!isLaidOut()) {
            final int scrollRange = Math.max(0, childWidth - (r - l - mPaddingLeft - mPaddingRight));
            if (isLayoutRtl()) mScrollX = scrollRange - mScrollX;
            if (mScrollX > scrollRange) mScrollX = scrollRange;
            else if (mScrollX < 0) mScrollX = 0;
        }
        scrollTo(mScrollX, mScrollY);
    }

    private int getScrollRange() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0, child.getWidth() - (getWidth() - mPaddingLeft - mPaddingRight));
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
        if (duration > ScrollView.ANIMATED_SCROLL_GAP) {
            final int width = getWidth() - mPaddingRight - mPaddingLeft;
            final int right = getChildAt(0).getWidth();
            final int maxX = Math.max(0, right - width);
            final int scrollX = mScrollX;
            dx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX;
            mScroller.startScroll(scrollX, mScrollY, dx, 0);
            postInvalidateOnAnimation();
        } else {
            if (!mScroller.isFinished()) mScroller.abortAnimation();
            scrollBy(dx, dy);
        }
        mLastScroll = android.view.animation.AnimationUtils.currentAnimationTimeMillis();
    }

    public final void smoothScrollTo(int x, int y) { smoothScrollBy(x - mScrollX, y - mScrollY); }

    public boolean fullScroll(int direction) {
        boolean right = direction == View.FOCUS_RIGHT;
        int width = getWidth();
        int target = right && getChildCount() > 0 ? Math.max(0, getChildAt(0).getRight() + mPaddingRight - width) : 0;
        smoothScrollTo(target, mScrollY);
        return true;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int x = mScroller.getCurrX(), y = mScroller.getCurrY();
            if (mScrollX != x || mScrollY != y) scrollTo(x, y);
            if (!mScroller.isFinished()) postInvalidateOnAnimation();
        }
    }

    public void fling(int velocityX) {
        if (getChildCount() > 0) {
            int width = getWidth() - mPaddingRight - mPaddingLeft;
            int right = getChildAt(0).getWidth();
            mScroller.fling(mScrollX, mScrollY, velocityX, 0, 0, Math.max(0, right - width), 0, 0);
            postInvalidateOnAnimation();
        }
    }

    @Override public boolean canScrollHorizontally(int direction) {
        final int offset = mScrollX;
        final int range = getScrollRange();
        if (range == 0) return false;
        return direction < 0 ? offset > 0 : offset < range;
    }

    @Override public boolean scrollsVertically() { return false; }
    @Override public boolean canScrollBy(int delta) { return canScroll() && canScrollHorizontally(delta); }
    @Override public void dragBy(int delta) { if (!mScroller.isFinished()) mScroller.abortAnimation(); scrollTo(mScrollX + delta, mScrollY); }
    @Override public void flingWith(int velocity) { fling(velocity); }
    @Override public void stopFling() { if (!mScroller.isFinished()) mScroller.forceFinished(true); }
}
