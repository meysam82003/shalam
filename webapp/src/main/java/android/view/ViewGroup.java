package android.view;

import android.content.Context;
import android.graphics.Canvas;

/** android.view.ViewGroup: children, layout params and the AOSP child measure rules; draws children in order. */
public abstract class ViewGroup extends View implements ViewParent {
    public static final int FOCUS_BEFORE_DESCENDANTS = 0x20000, FOCUS_AFTER_DESCENDANTS = 0x40000, FOCUS_BLOCK_DESCENDANTS = 0x60000;

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        @Deprecated public static final int FILL_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width;
        public int height;

        public LayoutParams(int width, int height) { this.width = width; this.height = height; }

        public LayoutParams(LayoutParams source) { this.width = source.width; this.height = source.height; }

        public void resolveLayoutDirection(int layoutDirection) {}
    }

    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        private int startMargin = Integer.MIN_VALUE, endMargin = Integer.MIN_VALUE;

        public MarginLayoutParams(int width, int height) { super(width, height); }

        public MarginLayoutParams(LayoutParams source) { super(source); }

        public MarginLayoutParams(MarginLayoutParams source) {
            super(source);
            leftMargin = source.leftMargin; topMargin = source.topMargin; rightMargin = source.rightMargin; bottomMargin = source.bottomMargin;
            startMargin = source.startMargin; endMargin = source.endMargin;
        }

        public void setMargins(int left, int top, int right, int bottom) {
            leftMargin = left; topMargin = top; rightMargin = right; bottomMargin = bottom;
            startMargin = endMargin = Integer.MIN_VALUE;
        }

        public void setMarginsRelative(int start, int top, int end, int bottom) { startMargin = start; topMargin = top; endMargin = end; bottomMargin = bottom; }
        public void setMarginStart(int start) { startMargin = start; }
        public void setMarginEnd(int end) { endMargin = end; }
        public int getMarginStart() { return startMargin != Integer.MIN_VALUE ? startMargin : leftMargin; }
        public int getMarginEnd() { return endMargin != Integer.MIN_VALUE ? endMargin : rightMargin; }
        public boolean isMarginRelative() { return startMargin != Integer.MIN_VALUE || endMargin != Integer.MIN_VALUE; }

        /** Start/end margins become left/right for the resolved direction (AOSP resolveLayoutDirection). */
        @Override public void resolveLayoutDirection(int layoutDirection) {
            boolean rtl = layoutDirection == LAYOUT_DIRECTION_RTL;
            if (startMargin != Integer.MIN_VALUE) { if (rtl) rightMargin = startMargin; else leftMargin = startMargin; }
            if (endMargin != Integer.MIN_VALUE) { if (rtl) leftMargin = endMargin; else rightMargin = endMargin; }
        }
    }

    final java.util.ArrayList<View> mChildren = new java.util.ArrayList<>();
    boolean mClipChildren = true;
    boolean mClipToPadding = true;
    private int descendantFocusability = FOCUS_BEFORE_DESCENDANTS;

    public ViewGroup(Context context) {
        super(context);
        mWillNotDraw = true;
    }

    public ViewGroup(Context context, android.util.AttributeSet attrs) { this(context); }

    // ------------------------------------------------------------------ children
    public int getChildCount() { return mChildren.size(); }

    public View getChildAt(int index) { return index < 0 || index >= mChildren.size() ? null : mChildren.get(index); }

    public int indexOfChild(View child) { return mChildren.indexOf(child); }

    public void addView(View child) { addView(child, -1); }

    public void addView(View child, int index) {
        if (child == null) throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        LayoutParams params = child.getLayoutParams();
        if (params == null) {
            params = generateDefaultLayoutParams();
            if (params == null) throw new IllegalArgumentException("generateDefaultLayoutParams() cannot return null");
        }
        addView(child, index, params);
    }

    public void addView(View child, int width, int height) {
        LayoutParams params = generateDefaultLayoutParams();
        params.width = width;
        params.height = height;
        addView(child, -1, params);
    }

    public void addView(View child, LayoutParams params) { addView(child, -1, params); }

    public void addView(View child, int index, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        requestLayout();
        invalidate();
        addViewInner(child, index, params);
    }

    private void addViewInner(View child, int index, LayoutParams params) {
        if (child.getParent() != null) throw new IllegalStateException("The specified child already has a parent. You must call removeView() on the child's parent first.");
        if (!checkLayoutParams(params)) params = generateLayoutParams(params);
        child.mLayoutParams = params;
        if (index < 0 || index > mChildren.size()) index = mChildren.size();
        mChildren.add(index, child);
        child.mParent = this;
        child.mPrivateFlags |= PFLAG_FORCE_LAYOUT;
        if (mAttachInfo != null) child.dispatchAttachedToWindow(mAttachInfo);
        onViewAdded(child);
    }

    public boolean addViewInLayout(View child, int index, LayoutParams params) { addViewInner(child, index, params); return true; }

    protected void onViewAdded(View child) {}

    protected void onViewRemoved(View child) {}

    public void removeView(View view) {
        int i = mChildren.indexOf(view);
        if (i >= 0) removeViewAt(i);
    }

    public void removeViewAt(int index) {
        View child = mChildren.get(index);
        if (child.currentAnimator() != null) child.currentAnimator().cancelForRemoval();
        if (mAttachInfo != null) child.dispatchDetachedFromWindow();
        mChildren.remove(index);
        child.mParent = null;
        onViewRemoved(child);
        requestLayout();
        invalidate();
    }

    public void removeViewInLayout(View view) { removeView(view); }

    public void removeViews(int start, int count) { for (int i = start + count - 1; i >= start; i--) removeViewAt(i); }

    public void removeAllViews() {
        for (int i = mChildren.size() - 1; i >= 0; i--) {
            View child = mChildren.get(i);
            if (child.currentAnimator() != null) child.currentAnimator().cancelForRemoval();
            if (mAttachInfo != null) child.dispatchDetachedFromWindow();
            child.mParent = null;
            onViewRemoved(child);
        }
        mChildren.clear();
        requestLayout();
        invalidate();
    }

    public void removeAllViewsInLayout() { removeAllViews(); }

    public void bringChildToFront(View child) {
        int i = mChildren.indexOf(child);
        if (i >= 0) { mChildren.remove(i); mChildren.add(child); requestLayout(); invalidate(); }
    }

    void onSetLayoutParams(View child, LayoutParams params) { requestLayout(); }

    protected boolean checkLayoutParams(LayoutParams p) { return p != null; }

    protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }

    protected LayoutParams generateLayoutParams(LayoutParams p) { return p; }

    public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) { return generateDefaultLayoutParams(); }

    @Override <T extends View> T findViewTraversal(int id) {
        if (id == mID) {
            @SuppressWarnings("unchecked") T self = (T) this;
            return self;
        }
        for (View c : mChildren) {
            T v = c.findViewTraversal(id);
            if (v != null) return v;
        }
        return null;
    }

    public void setClipChildren(boolean clipChildren) { mClipChildren = clipChildren; invalidate(); }
    public boolean getClipChildren() { return mClipChildren; }
    public void setClipToPadding(boolean clipToPadding) { mClipToPadding = clipToPadding; invalidate(); }
    public boolean getClipToPadding() { return mClipToPadding; }
    public void setDescendantFocusability(int focusability) { descendantFocusability = focusability; }
    public int getDescendantFocusability() { return descendantFocusability; }
    public void setMotionEventSplittingEnabled(boolean split) {}
    public void setLayoutTransition(Object transition) {}
    public void setAddStatesFromChildren(boolean addsStates) {}
    public boolean shouldDelayChildPressedState() { return true; }
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
    public boolean onInterceptTouchEvent(MotionEvent ev) { return false; }

    void childDrawableStateChanged(View child) {}

    @Override protected void dispatchSetPressed(boolean pressed) {
        for (View c : mChildren) if (!pressed || (!c.isClickable() && !c.isLongClickable())) c.setPressed(pressed);
    }

    @Override protected void dispatchSetSelected(boolean selected) { for (View c : mChildren) c.setSelected(selected); }

    @Override public void setEnabled(boolean enabled) { super.setEnabled(enabled); }

    // ------------------------------------------------------------------ ViewParent
    @Override public boolean isLayoutRequested() { return super.isLayoutRequested(); }

    @Override public void requestLayout() { super.requestLayout(); }

    @Override public ViewParent getParent() { return super.getParent(); }

    @Override public void requestChildFocus(View child, View focused) {}

    @Override public void focusableViewAvailable(View v) {}

    @Override public View findFocus() {
        if (isFocused()) return this;
        for (View c : mChildren) {
            View f = c.findFocus();
            if (f != null) return f;
        }
        return null;
    }

    @Override public boolean hasFocus() { return findFocus() != null; }

    // ------------------------------------------------------------------ attach and direction
    @Override void dispatchAttachedToWindow(AttachInfo info) {
        super.dispatchAttachedToWindow(info);
        for (View c : new java.util.ArrayList<>(mChildren)) c.dispatchAttachedToWindow(info);
    }

    @Override void dispatchDetachedFromWindow() {
        for (View c : new java.util.ArrayList<>(mChildren)) c.dispatchDetachedFromWindow();
        super.dispatchDetachedFromWindow();
    }

    @Override public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        WindowInsets result = super.dispatchApplyWindowInsets(insets);
        if (!result.isConsumed()) for (View c : mChildren) c.dispatchApplyWindowInsets(result);
        return result;
    }

    // ------------------------------------------------------------------ measuring (AOSP)
    protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
        for (View child : mChildren) if (child.getVisibility() != GONE) measureChild(child, widthMeasureSpec, heightMeasureSpec);
    }

    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        LayoutParams lp = child.getLayoutParams();
        int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, mPaddingLeft + mPaddingRight, lp.width);
        int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, mPaddingTop + mPaddingBottom, lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
        int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    /** AOSP getChildMeasureSpec for apps targeting Android 6+ (UNSPECIFIED children get size 0). */
    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        int specMode = MeasureSpec.getMode(spec);
        int specSize = MeasureSpec.getSize(spec);
        int size = Math.max(0, specSize - padding);
        int resultSize = 0;
        int resultMode = 0;
        switch (specMode) {
            case MeasureSpec.EXACTLY:
                if (childDimension >= 0) { resultSize = childDimension; resultMode = MeasureSpec.EXACTLY; }
                else if (childDimension == LayoutParams.MATCH_PARENT) { resultSize = size; resultMode = MeasureSpec.EXACTLY; }
                else if (childDimension == LayoutParams.WRAP_CONTENT) { resultSize = size; resultMode = MeasureSpec.AT_MOST; }
                break;
            case MeasureSpec.AT_MOST:
                if (childDimension >= 0) { resultSize = childDimension; resultMode = MeasureSpec.EXACTLY; }
                else if (childDimension == LayoutParams.MATCH_PARENT) { resultSize = size; resultMode = MeasureSpec.AT_MOST; }
                else if (childDimension == LayoutParams.WRAP_CONTENT) { resultSize = size; resultMode = MeasureSpec.AT_MOST; }
                break;
            case MeasureSpec.UNSPECIFIED:
                if (childDimension >= 0) { resultSize = childDimension; resultMode = MeasureSpec.EXACTLY; }
                else if (childDimension == LayoutParams.MATCH_PARENT) { resultSize = 0; resultMode = MeasureSpec.UNSPECIFIED; }
                else if (childDimension == LayoutParams.WRAP_CONTENT) { resultSize = 0; resultMode = MeasureSpec.UNSPECIFIED; }
                break;
        }
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }

    @Override protected abstract void onLayout(boolean changed, int l, int t, int r, int b);

    // ------------------------------------------------------------------ drawing
    /** Visible part of this view in its own coordinates while drawing (children fully outside are skipped). */
    float cullL = -1e9f, cullT = -1e9f, cullR = 1e9f, cullB = 1e9f;

    @Override protected void dispatchDraw(Canvas canvas) {
        boolean clip = mClipToPadding;
        if (clip) {
            canvas.save();
            canvas.clipRect(mScrollX + mPaddingLeft, mScrollY + mPaddingTop, mScrollX + mRight - mLeft - mPaddingRight, mScrollY + mBottom - mTop - mPaddingBottom);
        }
        int n = mChildren.size();
        for (int i = 0; i < n && i < mChildren.size(); i++) {
            View child = mChildren.get(i);
            if (child.getVisibility() == VISIBLE) drawChild(canvas, child, 0);
        }
        if (clip) canvas.restore();
    }

    /** Draws one child: offset, transform, alpha, clipping and its own scroll, as View.draw(Canvas, ViewGroup, long). */
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child.mAlpha <= 0f) return false;
        int w = child.mRight - child.mLeft, h = child.mBottom - child.mTop;
        boolean transformed = child.hasTransform();
        float ox = child.mLeft + child.mTranslationX, oy = child.mTop + child.mTranslationY;
        if (!transformed) {
            // skip children entirely outside the visible part
            if (ox + w < cullL || ox > cullR || oy + h < cullT || oy > cullB) return false;
        }
        int save = canvas.save();
        canvas.translate(ox, oy);
        if (transformed) {
            float px = child.getPivotX(), py = child.getPivotY();
            canvas.translate(px, py);
            if (child.mRotation != 0) canvas.rotate(child.mRotation);
            canvas.scale(child.mScaleX, child.mScaleY);
            canvas.translate(-px, -py);
        }
        if (child.mAlpha < 1f) canvas.multiplyAlpha(child.mAlpha);
        if (mClipChildren) canvas.clipRect(0, 0, w, h);
        int sx = child.mScrollX, sy = child.mScrollY;
        canvas.translate(-sx, -sy);
        if (child instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) child;
            if (transformed) {
                g.cullL = -1e9f; g.cullT = -1e9f; g.cullR = 1e9f; g.cullB = 1e9f;
            } else {
                float l = Math.max(cullL - ox, 0), t = Math.max(cullT - oy, 0);
                float r = Math.min(cullR - ox, w), b = Math.min(cullB - oy, h);
                g.cullL = l + sx; g.cullT = t + sy; g.cullR = r + sx; g.cullB = b + sy;
            }
        }
        child.computeScroll();
        // AOSP skips draw() of a view that will not draw and has no background (only its children)
        if (child.mWillNotDraw && child.mBackground == null) child.dispatchDraw(canvas); else child.draw(canvas);
        canvas.restoreToCount(save);
        return true;
    }

    /** Resets the visible area before the window draws this root. */
    public void setCullBounds(float l, float t, float r, float b) { cullL = l; cullT = t; cullR = r; cullB = b; }
}
