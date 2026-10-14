package android.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * android.view.View for the browser: the measure, layout, draw and state rules follow AOSP so every screen of the
 * app gets the same sizes and positions as on a phone. Drawing goes to {@link Canvas} (browser 2D canvas).
 */
public class View implements Drawable.Callback {
    public static final int NO_ID = -1;
    public static final int VISIBLE = 0x00000000, INVISIBLE = 0x00000004, GONE = 0x00000008;

    public static final int LAYOUT_DIRECTION_LTR = 0, LAYOUT_DIRECTION_RTL = 1, LAYOUT_DIRECTION_INHERIT = 2, LAYOUT_DIRECTION_LOCALE = 3;
    public static final int TEXT_DIRECTION_INHERIT = 0, TEXT_DIRECTION_FIRST_STRONG = 1, TEXT_DIRECTION_ANY_RTL = 2, TEXT_DIRECTION_LTR = 3,
        TEXT_DIRECTION_RTL = 4, TEXT_DIRECTION_LOCALE = 5, TEXT_DIRECTION_FIRST_STRONG_LTR = 6, TEXT_DIRECTION_FIRST_STRONG_RTL = 7;
    public static final int TEXT_ALIGNMENT_INHERIT = 0, TEXT_ALIGNMENT_GRAVITY = 1, TEXT_ALIGNMENT_TEXT_START = 2, TEXT_ALIGNMENT_TEXT_END = 3,
        TEXT_ALIGNMENT_CENTER = 4, TEXT_ALIGNMENT_VIEW_START = 5, TEXT_ALIGNMENT_VIEW_END = 6;

    public static final int SYSTEM_UI_FLAG_VISIBLE = 0, SYSTEM_UI_FLAG_LOW_PROFILE = 1, SYSTEM_UI_FLAG_HIDE_NAVIGATION = 2, SYSTEM_UI_FLAG_FULLSCREEN = 4,
        SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 0x10, SYSTEM_UI_FLAG_LAYOUT_STABLE = 0x100, SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 0x200,
        SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 0x400, SYSTEM_UI_FLAG_IMMERSIVE = 0x800, SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 0x1000, SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 0x2000;

    public static final int OVER_SCROLL_ALWAYS = 0, OVER_SCROLL_IF_CONTENT_SCROLLS = 1, OVER_SCROLL_NEVER = 2;
    public static final int LAYER_TYPE_NONE = 0, LAYER_TYPE_SOFTWARE = 1, LAYER_TYPE_HARDWARE = 2;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_AUTO = 0, IMPORTANT_FOR_ACCESSIBILITY_YES = 1, IMPORTANT_FOR_ACCESSIBILITY_NO = 2,
        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS = 4;
    public static final int FOCUSABLE_AUTO = 0x10, NOT_FOCUSABLE = 0, FOCUSABLE = 1;
    public static final int MEASURED_SIZE_MASK = 0x00ffffff, MEASURED_STATE_MASK = 0xff000000, MEASURED_HEIGHT_STATE_SHIFT = 16, MEASURED_STATE_TOO_SMALL = 0x01000000;
    public static final int SCROLLBARS_INSIDE_OVERLAY = 0, SCROLLBARS_OUTSIDE_OVERLAY = 0x02000000;
    public static final int FOCUS_BACKWARD = 1, FOCUS_FORWARD = 2, FOCUS_LEFT = 17, FOCUS_UP = 33, FOCUS_RIGHT = 66, FOCUS_DOWN = 130;
    public static final int HAPTIC_FEEDBACK_ENABLED = 0x10000000;

    static final int[] EMPTY_STATE_SET = {};

    public static class MeasureSpec {
        private static final int MODE_SHIFT = 30;
        private static final int MODE_MASK = 0x3 << MODE_SHIFT;
        public static final int UNSPECIFIED = 0;
        public static final int EXACTLY = 1 << MODE_SHIFT;
        public static final int AT_MOST = 2 << MODE_SHIFT;

        public static int makeMeasureSpec(int size, int mode) { return (size & ~MODE_MASK) | (mode & MODE_MASK); }
        public static int makeSafeMeasureSpec(int size, int mode) { return mode == UNSPECIFIED ? 0 : makeMeasureSpec(size, mode); }
        public static int getMode(int measureSpec) { return measureSpec & MODE_MASK; }
        public static int getSize(int measureSpec) { return measureSpec & ~MODE_MASK; }

        static int adjust(int measureSpec, int delta) {
            int mode = getMode(measureSpec);
            int size = getSize(measureSpec);
            if (mode == UNSPECIFIED) return makeMeasureSpec(size, UNSPECIFIED);
            size += delta;
            if (size < 0) size = 0;
            return makeMeasureSpec(size, mode);
        }

        public static String toString(int spec) {
            int mode = getMode(spec);
            return (mode == EXACTLY ? "EXACTLY " : mode == AT_MOST ? "AT_MOST " : "UNSPECIFIED ") + getSize(spec);
        }
    }

    public interface OnClickListener { void onClick(View v); }
    public interface OnLongClickListener { boolean onLongClick(View v); }
    public interface OnFocusChangeListener { void onFocusChange(View v, boolean hasFocus); }
    public interface OnTouchListener { boolean onTouch(View v, MotionEvent event); }
    public interface OnKeyListener { boolean onKey(View v, int keyCode, KeyEvent event); }
    public interface OnLayoutChangeListener {
        void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom);
    }
    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View v);
        void onViewDetachedFromWindow(View v);
    }
    public interface OnApplyWindowInsetsListener { WindowInsets onApplyWindowInsets(View v, WindowInsets insets); }
    public interface OnScrollChangeListener { void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY); }

    // flags
    static final int PFLAG_FORCE_LAYOUT = 0x1000;
    static final int PFLAG_LAYOUT_REQUIRED = 0x2000;
    static final int PFLAG_MEASURED_DIMENSION_SET = 0x800;
    static final int PFLAG3_IS_LAID_OUT = 0x4;

    protected Context mContext;
    ViewParent mParent;
    AttachInfo mAttachInfo;
    ViewGroup.LayoutParams mLayoutParams;
    int mID = NO_ID;
    int mPrivateFlags = PFLAG_FORCE_LAYOUT;
    int mPrivateFlags3;

    protected int mLeft, mRight, mTop, mBottom;
    protected int mScrollX, mScrollY;
    protected int mPaddingLeft, mPaddingRight, mPaddingTop, mPaddingBottom;
    int mUserPaddingStart = Integer.MIN_VALUE, mUserPaddingEnd = Integer.MIN_VALUE;
    int mMeasuredWidth, mMeasuredHeight;
    int mOldWidthMeasureSpec = Integer.MIN_VALUE, mOldHeightMeasureSpec = Integer.MIN_VALUE;
    int mMinWidth, mMinHeight;

    Drawable mBackground;
    private boolean backgroundSizeChanged = true;

    int mViewFlagsVisibility = VISIBLE;
    boolean mEnabled = true;
    boolean mClickable, mLongClickable, mFocusable, mFocusableInTouchMode;
    boolean mPressed, mSelected, mActivated, mFocused;
    boolean mWillNotDraw;
    boolean mHapticEnabled = true;
    boolean mSoundEffectsEnabled = true;

    float mAlpha = 1f, mTranslationX, mTranslationY, mTranslationZ, mScaleX = 1f, mScaleY = 1f, mRotation, mElevation;
    float mPivotX, mPivotY;
    boolean mPivotExplicit;

    int mLayoutDirection = LAYOUT_DIRECTION_INHERIT;
    int mTextDirection = TEXT_DIRECTION_INHERIT;
    int mTextAlignment = TEXT_ALIGNMENT_GRAVITY;
    int mSystemUiVisibility;
    int mOverScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS;
    int mImportantForAccessibility;

    OnClickListener mOnClickListener;
    OnLongClickListener mOnLongClickListener;
    OnFocusChangeListener mOnFocusChangeListener;
    OnTouchListener mOnTouchListener;
    OnApplyWindowInsetsListener mOnApplyWindowInsetsListener;
    OnScrollChangeListener mOnScrollChangeListener;
    java.util.ArrayList<OnLayoutChangeListener> mOnLayoutChangeListeners;
    java.util.ArrayList<OnAttachStateChangeListener> mOnAttachStateChangeListeners;

    private CharSequence contentDescription;
    private Object tag;
    private java.util.HashMap<Integer, Object> keyedTags;
    private ViewPropertyAnimator animator;
    private ViewTreeObserver floatingTreeObserver;
    private java.util.ArrayList<Object[]> runQueue;

    public View(Context context) {
        mContext = context;
    }

    public View(Context context, android.util.AttributeSet attrs) { this(context); }

    public View(Context context, android.util.AttributeSet attrs, int defStyleAttr) { this(context); }

    // ------------------------------------------------------------------ basics
    public final Context getContext() { return mContext; }
    public Resources getResources() { return mContext != null ? mContext.getResources() : null; }
    public int getId() { return mID; }
    public void setId(int id) { mID = id; }
    public static int generateViewId() { return ++sNextId; }
    private static int sNextId = 0x00FF0000;
    public final ViewParent getParent() { return mParent; }

    public View getRootView() {
        if (mAttachInfo != null) return mAttachInfo.rootView();
        View v = this;
        while (v.mParent instanceof View) v = (View) v.mParent;
        return v;
    }

    public <T extends View> T findViewById(int id) {
        if (id == NO_ID) return null;
        return findViewTraversal(id);
    }

    @SuppressWarnings("unchecked")
    <T extends View> T findViewTraversal(int id) { return id == mID ? (T) this : null; }

    public Object getTag() { return tag; }
    public void setTag(Object tag) { this.tag = tag; }

    public Object getTag(int key) { return keyedTags == null ? null : keyedTags.get(key); }

    public void setTag(int key, Object tag) {
        if (keyedTags == null) keyedTags = new java.util.HashMap<>();
        keyedTags.put(key, tag);
    }

    public CharSequence getContentDescription() { return contentDescription; }
    public void setContentDescription(CharSequence d) { contentDescription = d; }
    public void setImportantForAccessibility(int mode) { mImportantForAccessibility = mode; }
    public int getImportantForAccessibility() { return mImportantForAccessibility; }
    public void setAccessibilityLiveRegion(int mode) {}
    public void announceForAccessibility(CharSequence text) {}
    public void sendAccessibilityEvent(int eventType) {}

    // ------------------------------------------------------------------ layout params, padding, size
    public ViewGroup.LayoutParams getLayoutParams() { return mLayoutParams; }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        if (params == null) throw new NullPointerException("Layout parameters cannot be null");
        mLayoutParams = params;
        if (mParent instanceof ViewGroup) ((ViewGroup) mParent).onSetLayoutParams(this, params);
        requestLayout();
    }

    public void setPadding(int left, int top, int right, int bottom) {
        mUserPaddingStart = Integer.MIN_VALUE;
        mUserPaddingEnd = Integer.MIN_VALUE;
        internalSetPadding(left, top, right, bottom);
    }

    protected void internalSetPadding(int left, int top, int right, int bottom) {
        boolean changed = mPaddingLeft != left || mPaddingRight != right || mPaddingTop != top || mPaddingBottom != bottom;
        mPaddingLeft = left; mPaddingTop = top; mPaddingRight = right; mPaddingBottom = bottom;
        if (changed) { requestLayout(); invalidateOutline(); }
    }

    public void setPaddingRelative(int start, int top, int end, int bottom) {
        mUserPaddingStart = start;
        mUserPaddingEnd = end;
        if (isLayoutRtl()) internalSetPadding(end, top, start, bottom); else internalSetPadding(start, top, end, bottom);
    }

    private void invalidateOutline() {}

    public int getPaddingLeft() { return mPaddingLeft; }
    public int getPaddingRight() { return mPaddingRight; }
    public int getPaddingTop() { return mPaddingTop; }
    public int getPaddingBottom() { return mPaddingBottom; }
    public int getPaddingStart() { return isLayoutRtl() ? mPaddingRight : mPaddingLeft; }
    public int getPaddingEnd() { return isLayoutRtl() ? mPaddingLeft : mPaddingRight; }
    public boolean isPaddingRelative() { return mUserPaddingStart != Integer.MIN_VALUE || mUserPaddingEnd != Integer.MIN_VALUE; }

    public final int getLeft() { return mLeft; }
    public final int getRight() { return mRight; }
    public final int getTop() { return mTop; }
    public final int getBottom() { return mBottom; }
    public final int getWidth() { return mRight - mLeft; }
    public final int getHeight() { return mBottom - mTop; }
    public float getX() { return mLeft + mTranslationX; }
    public float getY() { return mTop + mTranslationY; }
    public void setX(float x) { setTranslationX(x - mLeft); }
    public void setY(float y) { setTranslationY(y - mTop); }

    public final void setLeft(int left) { if (left != mLeft) { int w = mRight - mLeft; mLeft = left; sizeChange(w); } }
    public final void setRight(int right) { if (right != mRight) { int w = mRight - mLeft; mRight = right; sizeChange(w); } }
    public final void setTop(int top) { if (top != mTop) { int h = mBottom - mTop; mTop = top; sizeChangeH(h); } }
    public final void setBottom(int bottom) { if (bottom != mBottom) { int h = mBottom - mTop; mBottom = bottom; sizeChangeH(h); } }

    private void sizeChange(int oldW) { onSizeChanged(getWidth(), getHeight(), oldW, getHeight()); backgroundSizeChanged = true; invalidate(); }

    private void sizeChangeH(int oldH) { onSizeChanged(getWidth(), getHeight(), getWidth(), oldH); backgroundSizeChanged = true; invalidate(); }

    public final void offsetTopAndBottom(int offset) { if (offset != 0) { mTop += offset; mBottom += offset; invalidate(); } }
    public final void offsetLeftAndRight(int offset) { if (offset != 0) { mLeft += offset; mRight += offset; invalidate(); } }

    public final int getMeasuredWidth() { return mMeasuredWidth & MEASURED_SIZE_MASK; }
    public final int getMeasuredHeight() { return mMeasuredHeight & MEASURED_SIZE_MASK; }
    public final int getMeasuredWidthAndState() { return mMeasuredWidth; }
    public final int getMeasuredHeightAndState() { return mMeasuredHeight; }
    public final int getMeasuredState() { return (mMeasuredWidth & MEASURED_STATE_MASK) | ((mMeasuredHeight >> MEASURED_HEIGHT_STATE_SHIFT) & (MEASURED_STATE_MASK >> MEASURED_HEIGHT_STATE_SHIFT)); }

    public int getMinimumWidth() { return mMinWidth; }
    public int getMinimumHeight() { return mMinHeight; }
    public void setMinimumWidth(int minWidth) { mMinWidth = minWidth; requestLayout(); }
    public void setMinimumHeight(int minHeight) { mMinHeight = minHeight; requestLayout(); }

    protected int getSuggestedMinimumWidth() { return mBackground == null ? mMinWidth : Math.max(mMinWidth, mBackground.getMinimumWidth()); }
    protected int getSuggestedMinimumHeight() { return mBackground == null ? mMinHeight : Math.max(mMinHeight, mBackground.getMinimumHeight()); }

    // ------------------------------------------------------------------ measure / layout (AOSP rules)
    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        // AOSP resolves start/end margins for the view's direction before measuring
        if (mLayoutParams != null) mLayoutParams.resolveLayoutDirection(getLayoutDirection());
        boolean forceLayout = (mPrivateFlags & PFLAG_FORCE_LAYOUT) == PFLAG_FORCE_LAYOUT;
        boolean specChanged = widthMeasureSpec != mOldWidthMeasureSpec || heightMeasureSpec != mOldHeightMeasureSpec;
        boolean isSpecExactly = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;
        boolean matchesSpecSize = getMeasuredWidth() == MeasureSpec.getSize(widthMeasureSpec) && getMeasuredHeight() == MeasureSpec.getSize(heightMeasureSpec);
        boolean needsLayout = specChanged && (!isSpecExactly || !matchesSpecSize);
        if (forceLayout || needsLayout) {
            mPrivateFlags &= ~PFLAG_MEASURED_DIMENSION_SET;
            onMeasure(widthMeasureSpec, heightMeasureSpec);
            if ((mPrivateFlags & PFLAG_MEASURED_DIMENSION_SET) != PFLAG_MEASURED_DIMENSION_SET) {
                throw new IllegalStateException(getClass().getName() + "#onMeasure() did not set the measured dimension by calling setMeasuredDimension()");
            }
            mPrivateFlags |= PFLAG_LAYOUT_REQUIRED;
        }
        mOldWidthMeasureSpec = widthMeasureSpec;
        mOldHeightMeasureSpec = heightMeasureSpec;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }

    protected final void setMeasuredDimension(int measuredWidth, int measuredHeight) {
        mMeasuredWidth = measuredWidth;
        mMeasuredHeight = measuredHeight;
        mPrivateFlags |= PFLAG_MEASURED_DIMENSION_SET;
    }

    public static int combineMeasuredStates(int curState, int newState) { return curState | newState; }

    public static int resolveSize(int size, int measureSpec) { return resolveSizeAndState(size, measureSpec, 0) & MEASURED_SIZE_MASK; }

    public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        int result;
        switch (specMode) {
            case MeasureSpec.AT_MOST:
                result = specSize < size ? specSize | MEASURED_STATE_TOO_SMALL : size;
                break;
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
            default:
                result = size;
        }
        return result | (childMeasuredState & MEASURED_STATE_MASK);
    }

    public static int getDefaultSize(int size, int measureSpec) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        return specMode == MeasureSpec.UNSPECIFIED ? size : specSize;
    }

    public void layout(int l, int t, int r, int b) {
        int oldL = mLeft, oldT = mTop, oldB = mBottom, oldR = mRight;
        boolean changed = setFrame(l, t, r, b);
        if (changed || (mPrivateFlags & PFLAG_LAYOUT_REQUIRED) == PFLAG_LAYOUT_REQUIRED) {
            onLayout(changed, l, t, r, b);
            mPrivateFlags &= ~PFLAG_LAYOUT_REQUIRED;
            if (mOnLayoutChangeListeners != null) {
                for (OnLayoutChangeListener li : new java.util.ArrayList<>(mOnLayoutChangeListeners)) li.onLayoutChange(this, l, t, r, b, oldL, oldT, oldR, oldB);
            }
        }
        mPrivateFlags &= ~PFLAG_FORCE_LAYOUT;
        mPrivateFlags3 |= PFLAG3_IS_LAID_OUT;
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {}

    protected boolean setFrame(int left, int top, int right, int bottom) {
        boolean changed = false;
        if (mLeft != left || mRight != right || mTop != top || mBottom != bottom) {
            changed = true;
            int oldWidth = mRight - mLeft, oldHeight = mBottom - mTop;
            int newWidth = right - left, newHeight = bottom - top;
            boolean sizeChanged = newWidth != oldWidth || newHeight != oldHeight;
            mLeft = left; mTop = top; mRight = right; mBottom = bottom;
            if (sizeChanged) {
                if (!mPivotExplicit) { mPivotX = newWidth / 2f; mPivotY = newHeight / 2f; }
                onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
                backgroundSizeChanged = true;
            }
            invalidate();
        }
        return changed;
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {}

    public void requestLayout() {
        mPrivateFlags |= PFLAG_FORCE_LAYOUT;
        if (mParent != null && !mParent.isLayoutRequested()) mParent.requestLayout();
        else if (mParent == null && mAttachInfo != null) mAttachInfo.requestLayoutRoot();
    }

    public void forceLayout() { mPrivateFlags |= PFLAG_FORCE_LAYOUT; }

    public boolean isLayoutRequested() { return (mPrivateFlags & PFLAG_FORCE_LAYOUT) == PFLAG_FORCE_LAYOUT; }

    public boolean isLaidOut() { return (mPrivateFlags3 & PFLAG3_IS_LAID_OUT) == PFLAG3_IS_LAID_OUT; }

    public boolean isInLayout() { return false; }

    public int getBaseline() { return -1; }

    public void addOnLayoutChangeListener(OnLayoutChangeListener listener) {
        if (mOnLayoutChangeListeners == null) mOnLayoutChangeListeners = new java.util.ArrayList<>();
        if (!mOnLayoutChangeListeners.contains(listener)) mOnLayoutChangeListeners.add(listener);
    }

    public void removeOnLayoutChangeListener(OnLayoutChangeListener listener) { if (mOnLayoutChangeListeners != null) mOnLayoutChangeListeners.remove(listener); }

    // ------------------------------------------------------------------ direction
    public void setLayoutDirection(int layoutDirection) {
        if (mLayoutDirection != layoutDirection) {
            mLayoutDirection = layoutDirection;
            onRtlPropertiesChangedInternal();
            requestLayout();
            invalidate();
        }
    }

    protected void onRtlPropertiesChangedInternal() {}

    public int getRawLayoutDirection() { return mLayoutDirection; }

    /** Resolved direction: explicit, or inherited from the parent; the window root defaults to left-to-right. */
    public int getLayoutDirection() {
        if (mLayoutDirection == LAYOUT_DIRECTION_LTR || mLayoutDirection == LAYOUT_DIRECTION_RTL) return mLayoutDirection;
        if (mLayoutDirection == LAYOUT_DIRECTION_LOCALE) return LAYOUT_DIRECTION_LTR;
        if (mParent instanceof View) return ((View) mParent).getLayoutDirection();
        return LAYOUT_DIRECTION_LTR;
    }

    public boolean isLayoutRtl() { return getLayoutDirection() == LAYOUT_DIRECTION_RTL; }

    public void setTextDirection(int textDirection) {
        if (mTextDirection != textDirection) { mTextDirection = textDirection; onTextDirectionChanged(); requestLayout(); invalidate(); }
    }

    protected void onTextDirectionChanged() {}

    public int getRawTextDirection() { return mTextDirection; }

    /** Resolved text direction (inherit walks up; the default is first-strong). */
    public int getTextDirection() {
        if (mTextDirection != TEXT_DIRECTION_INHERIT) return mTextDirection;
        if (mParent instanceof View) return ((View) mParent).getTextDirection();
        return TEXT_DIRECTION_FIRST_STRONG;
    }

    public void setTextAlignment(int textAlignment) { if (mTextAlignment != textAlignment) { mTextAlignment = textAlignment; requestLayout(); invalidate(); } }

    public int getTextAlignment() {
        if (mTextAlignment != TEXT_ALIGNMENT_INHERIT) return mTextAlignment;
        if (mParent instanceof View) return ((View) mParent).getTextAlignment();
        return TEXT_ALIGNMENT_GRAVITY;
    }

    // ------------------------------------------------------------------ visibility and state
    public int getVisibility() { return mViewFlagsVisibility; }

    public void setVisibility(int visibility) {
        if (mViewFlagsVisibility == visibility) return;
        int old = mViewFlagsVisibility;
        mViewFlagsVisibility = visibility;
        if (visibility == GONE || old == GONE) requestLayout();
        if (mParent instanceof View) ((View) mParent).invalidate();
        invalidate();
        if (visibility != VISIBLE && mFocused) clearFocus();
        onVisibilityChanged(this, visibility);
    }

    protected void onVisibilityChanged(View changedView, int visibility) {}

    public boolean isShown() {
        View current = this;
        do {
            if (current.mViewFlagsVisibility != VISIBLE) return false;
            ViewParent parent = current.mParent;
            // the root of an attached window has the window (ViewRootImpl on Android) as its parent
            if (parent == null) return current.mAttachInfo != null && current.mAttachInfo.rootView() == current;
            if (!(parent instanceof View)) return true;
            current = (View) parent;
        } while (current != null);
        return false;
    }

    public boolean isEnabled() { return mEnabled; }

    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) return;
        mEnabled = enabled;
        refreshDrawableState();
        invalidate();
    }

    public boolean isClickable() { return mClickable; }
    public void setClickable(boolean clickable) { mClickable = clickable; }
    public boolean isLongClickable() { return mLongClickable; }
    public void setLongClickable(boolean longClickable) { mLongClickable = longClickable; }
    public boolean isFocusable() { return mFocusable; }
    public void setFocusable(boolean focusable) { mFocusable = focusable; if (!focusable) mFocusableInTouchMode = false; }
    public void setFocusable(int focusable) { setFocusable(focusable != NOT_FOCUSABLE); }
    public boolean isFocusableInTouchMode() { return mFocusableInTouchMode; }
    public void setFocusableInTouchMode(boolean f) { mFocusableInTouchMode = f; if (f) mFocusable = true; }
    public boolean isPressed() { return mPressed; }

    public void setPressed(boolean pressed) {
        if (mPressed == pressed) return;
        mPressed = pressed;
        refreshDrawableState();
        dispatchSetPressed(pressed);
    }

    protected void dispatchSetPressed(boolean pressed) {}

    public boolean isSelected() { return mSelected; }

    public void setSelected(boolean selected) {
        if (mSelected != selected) { mSelected = selected; refreshDrawableState(); invalidate(); dispatchSetSelected(selected); }
    }

    protected void dispatchSetSelected(boolean selected) {}

    public boolean isActivated() { return mActivated; }
    public void setActivated(boolean activated) { if (mActivated != activated) { mActivated = activated; refreshDrawableState(); invalidate(); } }

    public void setWillNotDraw(boolean willNotDraw) { mWillNotDraw = willNotDraw; }
    public boolean willNotDraw() { return mWillNotDraw; }
    public void setHapticFeedbackEnabled(boolean enabled) { mHapticEnabled = enabled; }
    public boolean isHapticFeedbackEnabled() { return mHapticEnabled; }
    public void setSoundEffectsEnabled(boolean enabled) { mSoundEffectsEnabled = enabled; }
    public void playSoundEffect(int soundConstant) {}

    public boolean performHapticFeedback(int feedbackConstant) {
        if (!mHapticEnabled || mAttachInfo == null) return false;
        mAttachInfo.vibrate(feedbackConstant == HapticFeedbackConstants.LONG_PRESS ? 25 : 10);
        return true;
    }

    public boolean performHapticFeedback(int feedbackConstant, int flags) { return performHapticFeedback(feedbackConstant); }

    public void setSystemUiVisibility(int visibility) { mSystemUiVisibility = visibility; }
    public int getSystemUiVisibility() { return mSystemUiVisibility; }
    public void setOverScrollMode(int mode) { mOverScrollMode = mode; }
    public int getOverScrollMode() { return mOverScrollMode; }
    // The web runtime draws no scroll bars (as the app turns them off everywhere).
    private boolean vScrollBar = true, hScrollBar = true;
    public void setVerticalScrollBarEnabled(boolean enabled) { vScrollBar = enabled; }
    public void setHorizontalScrollBarEnabled(boolean enabled) { hScrollBar = enabled; }
    public boolean isVerticalScrollBarEnabled() { return vScrollBar; }
    public boolean isHorizontalScrollBarEnabled() { return hScrollBar; }
    public void setScrollBarStyle(int style) {}
    public void setVerticalFadingEdgeEnabled(boolean enabled) {}
    public void setHorizontalFadingEdgeEnabled(boolean enabled) {}
    public void setFadingEdgeLength(int length) {}
    public void setNestedScrollingEnabled(boolean enabled) {}
    public void setLayerType(int layerType, android.graphics.Paint paint) {}
    public int getLayerType() { return LAYER_TYPE_NONE; }
    public void setDrawingCacheEnabled(boolean enabled) {}
    public void setSaveEnabled(boolean enabled) {}
    public void setKeepScreenOn(boolean keepScreenOn) {}
    public void setFitsSystemWindows(boolean fitSystemWindows) {}
    public void setClipToOutline(boolean clipToOutline) {}
    public void setOutlineProvider(ViewOutlineProvider provider) {}
    public void setStateListAnimator(Object animator) {}
    public void setForeground(Drawable foreground) {}

    // ------------------------------------------------------------------ transforms
    public float getAlpha() { return mAlpha; }

    public void setAlpha(float alpha) {
        if (mAlpha != alpha) { mAlpha = alpha; invalidateParent(); }
    }

    public float getTranslationX() { return mTranslationX; }
    public void setTranslationX(float x) { if (mTranslationX != x) { mTranslationX = x; invalidateParent(); } }
    public float getTranslationY() { return mTranslationY; }
    public void setTranslationY(float y) { if (mTranslationY != y) { mTranslationY = y; invalidateParent(); } }
    public float getTranslationZ() { return mTranslationZ; }
    public void setTranslationZ(float z) { mTranslationZ = z; }
    public float getElevation() { return mElevation; }
    public void setElevation(float elevation) { mElevation = elevation; }
    public float getZ() { return mElevation + mTranslationZ; }
    public float getScaleX() { return mScaleX; }
    public void setScaleX(float s) { if (mScaleX != s) { mScaleX = s; invalidateParent(); } }
    public float getScaleY() { return mScaleY; }
    public void setScaleY(float s) { if (mScaleY != s) { mScaleY = s; invalidateParent(); } }
    public float getRotation() { return mRotation; }
    public void setRotation(float r) { if (mRotation != r) { mRotation = r; invalidateParent(); } }
    public void setRotationX(float r) {}
    public void setRotationY(float r) {}
    public void setCameraDistance(float d) {}
    public float getPivotX() { return mPivotExplicit ? mPivotX : getWidth() / 2f; }
    public void setPivotX(float x) { mPivotX = x; if (!mPivotExplicit) mPivotY = getHeight() / 2f; mPivotExplicit = true; invalidateParent(); }
    public float getPivotY() { return mPivotExplicit ? mPivotY : getHeight() / 2f; }
    public void setPivotY(float y) { mPivotY = y; if (!mPivotExplicit) mPivotX = getWidth() / 2f; mPivotExplicit = true; invalidateParent(); }

    /** True when drawing needs a matrix beyond translation (scale or rotation). */
    public boolean hasTransform() { return mScaleX != 1f || mScaleY != 1f || mRotation != 0f; }

    private void invalidateParent() {
        if (mParent instanceof View) ((View) mParent).invalidate();
        else invalidate();
    }

    public ViewPropertyAnimator animate() {
        if (animator == null) animator = new ViewPropertyAnimator(this);
        return animator;
    }

    /** Current animation of this view (if any), so the window keeps drawing frames. */
    ViewPropertyAnimator currentAnimator() { return animator; }

    public void clearAnimation() { if (animator != null) animator.cancel(); }

    public void startAnimation(android.view.animation.Animation animation) {}

    // ------------------------------------------------------------------ scrolling
    public final int getScrollX() { return mScrollX; }
    public final int getScrollY() { return mScrollY; }

    public void scrollTo(int x, int y) {
        if (mScrollX != x || mScrollY != y) {
            int oldX = mScrollX, oldY = mScrollY;
            mScrollX = x;
            mScrollY = y;
            onScrollChanged(mScrollX, mScrollY, oldX, oldY);
            invalidate();
        }
    }

    public void scrollBy(int x, int y) { scrollTo(mScrollX + x, mScrollY + y); }

    public void setScrollX(int value) { scrollTo(value, mScrollY); }
    public void setScrollY(int value) { scrollTo(mScrollX, value); }

    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        if (mOnScrollChangeListener != null) mOnScrollChangeListener.onScrollChange(this, l, t, oldl, oldt);
        if (mAttachInfo != null) mAttachInfo.treeObserver.dispatchOnScrollChanged();
    }

    public void setOnScrollChangeListener(OnScrollChangeListener l) { mOnScrollChangeListener = l; }

    public boolean canScrollVertically(int direction) { return false; }
    public boolean canScrollHorizontally(int direction) { return false; }

    public void computeScroll() {}

    // ------------------------------------------------------------------ background and drawable state
    public Drawable getBackground() { return mBackground; }

    public void setBackgroundColor(int color) {
        if (mBackground instanceof android.graphics.drawable.ColorDrawable) {
            ((android.graphics.drawable.ColorDrawable) mBackground.mutate()).setColor(color);
            invalidate();
        } else {
            setBackground(new android.graphics.drawable.ColorDrawable(color));
        }
    }

    public void setBackgroundResource(int resid) { setBackground(null); }

    public void setBackground(Drawable background) { setBackgroundDrawable(background); }

    /** AOSP: a drawable that reports padding replaces the view's padding. */
    @Deprecated
    public void setBackgroundDrawable(Drawable background) {
        if (background == mBackground) return;
        boolean requestLayout = false;
        if (mBackground != null) mBackground.setCallback(null);
        if (background != null) {
            Rect padding = new Rect();
            if (background.getPadding(padding)) {
                mUserPaddingStart = Integer.MIN_VALUE;
                mUserPaddingEnd = Integer.MIN_VALUE;
                internalSetPadding(padding.left, padding.top, padding.right, padding.bottom);
            }
            if (mBackground == null || mBackground.getMinimumHeight() != background.getMinimumHeight()
                || mBackground.getMinimumWidth() != background.getMinimumWidth()) requestLayout = true;
            mBackground = background;
            background.setCallback(this);
            if (background.isStateful()) background.setState(getDrawableState());
            background.setVisible(getVisibility() == VISIBLE, false);
        } else {
            mBackground = null;
            requestLayout = true;
        }
        backgroundSizeChanged = true;
        if (requestLayout) requestLayout();
        invalidate();
    }

    public void setBackgroundTintList(android.content.res.ColorStateList tint) {}

    public final int[] getDrawableState() { return onCreateDrawableState(0); }

    protected int[] onCreateDrawableState(int extraSpace) {
        int n = 0;
        int[] s = new int[6 + extraSpace];
        if (mEnabled) s[n++] = android.R.attr.state_enabled;
        if (mPressed) s[n++] = android.R.attr.state_pressed;
        if (mSelected) s[n++] = android.R.attr.state_selected;
        if (mFocused) s[n++] = android.R.attr.state_focused;
        if (mActivated) s[n++] = android.R.attr.state_activated;
        s[n++] = android.R.attr.state_window_focused;
        return java.util.Arrays.copyOf(s, n);
    }

    public void refreshDrawableState() {
        drawableStateChanged();
        if (mParent instanceof ViewGroup) ((ViewGroup) mParent).childDrawableStateChanged(this);
    }

    protected void drawableStateChanged() {
        if (mBackground != null && mBackground.isStateful()) {
            if (mBackground.setState(getDrawableState())) invalidate();
        }
    }

    public void jumpDrawablesToCurrentState() {}

    @Override public void invalidateDrawable(Drawable who) { invalidate(); }

    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { postDelayed(what, Math.max(0, when - android.os.SystemClock.uptimeMillis())); }

    @Override public void unscheduleDrawable(Drawable who, Runnable what) { removeCallbacks(what); }

    protected boolean verifyDrawable(Drawable who) { return who == mBackground; }

    // ------------------------------------------------------------------ drawing
    public void invalidate() { if (mAttachInfo != null) mAttachInfo.scheduleTraversal(); }
    public void invalidate(int l, int t, int r, int b) { invalidate(); }
    public void invalidate(Rect dirty) { invalidate(); }
    public void postInvalidate() { invalidate(); }
    public void postInvalidateDelayed(long delay) { postDelayed(this::invalidate, delay); }
    public void postInvalidateOnAnimation() { invalidate(); }
    public void postOnAnimation(Runnable action) { post(action); }
    public void postOnAnimationDelayed(Runnable action, long delay) { postDelayed(action, delay); }
    public long getDrawingTime() { return android.os.SystemClock.uptimeMillis(); }

    /** Background, own content, children (AOSP order without fading edges and foreground). */
    public void draw(Canvas canvas) {
        drawBackground(canvas);
        onDraw(canvas);
        dispatchDraw(canvas);
        onDrawForeground(canvas);
    }

    private void drawBackground(Canvas canvas) {
        Drawable background = mBackground;
        if (background == null) return;
        if (backgroundSizeChanged) {
            background.setBounds(0, 0, mRight - mLeft, mBottom - mTop);
            backgroundSizeChanged = false;
        }
        if ((mScrollX | mScrollY) == 0) {
            background.draw(canvas);
        } else {
            canvas.translate(mScrollX, mScrollY);
            background.draw(canvas);
            canvas.translate(-mScrollX, -mScrollY);
        }
    }

    protected void onDraw(Canvas canvas) {}

    protected void dispatchDraw(Canvas canvas) {}

    public void onDrawForeground(Canvas canvas) {}

    // ------------------------------------------------------------------ clicks and focus
    public void setOnClickListener(OnClickListener l) {
        if (!mClickable) mClickable = true;
        mOnClickListener = l;
    }

    public boolean hasOnClickListeners() { return mOnClickListener != null; }

    public void setOnLongClickListener(OnLongClickListener l) {
        if (!mLongClickable) mLongClickable = true;
        mOnLongClickListener = l;
    }

    public void setOnTouchListener(OnTouchListener l) { mOnTouchListener = l; }
    public void setOnKeyListener(OnKeyListener l) {}
    public void setOnFocusChangeListener(OnFocusChangeListener l) { mOnFocusChangeListener = l; }
    public OnFocusChangeListener getOnFocusChangeListener() { return mOnFocusChangeListener; }

    public boolean performClick() {
        if (mOnClickListener != null) {
            mOnClickListener.onClick(this);
            return true;
        }
        return false;
    }

    public boolean callOnClick() { return performClick(); }

    public boolean performLongClick() {
        if (mOnLongClickListener != null && mOnLongClickListener.onLongClick(this)) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        }
        return false;
    }

    /** Touch listener hook (rarely used by the app); returns true when it consumed the event. */
    public boolean dispatchTouchListener(MotionEvent e) { return mOnTouchListener != null && mEnabled && mOnTouchListener.onTouch(this, e); }

    public boolean onTouchEvent(MotionEvent event) { return false; }

    public boolean isFocused() { return mFocused; }
    public boolean hasFocus() { return mFocused; }

    public final boolean requestFocus() { return requestFocus(FOCUS_DOWN); }

    public final boolean requestFocus(int direction) { return requestFocus(direction, null); }

    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        if (!mFocusable || mViewFlagsVisibility != VISIBLE || !mEnabled) return false;
        if (mFocused) return true;
        AttachInfo ai = mAttachInfo;
        View old = ai != null ? ai.focused : null;
        if (old != null && old != this) old.clearFocusInternal();
        mFocused = true;
        if (ai != null) ai.focused = this;
        refreshDrawableState();
        onFocusChanged(true, direction, previouslyFocusedRect);
        if (mOnFocusChangeListener != null) mOnFocusChangeListener.onFocusChange(this, true);
        if (ai != null) ai.onFocusChanged(old, this);
        return true;
    }

    public void clearFocus() {
        if (!mFocused) return;
        AttachInfo ai = mAttachInfo;
        clearFocusInternal();
        if (ai != null) ai.onFocusChanged(this, null);
    }

    void clearFocusInternal() {
        mFocused = false;
        if (mAttachInfo != null && mAttachInfo.focused == this) mAttachInfo.focused = null;
        refreshDrawableState();
        onFocusChanged(false, 0, null);
        if (mOnFocusChangeListener != null) mOnFocusChangeListener.onFocusChange(this, false);
    }

    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {}

    public View findFocus() { return mFocused ? this : null; }

    // ------------------------------------------------------------------ attach, posting, observer
    public boolean isAttachedToWindow() { return mAttachInfo != null; }

    public AttachInfo attachInfo() { return mAttachInfo; }

    void dispatchAttachedToWindow(AttachInfo info) {
        mAttachInfo = info;
        if (floatingTreeObserver != null) {
            info.treeObserver.merge(floatingTreeObserver);
            floatingTreeObserver = null;
        }
        onAttachedToWindow();
        if (mOnAttachStateChangeListeners != null) {
            for (OnAttachStateChangeListener l : new java.util.ArrayList<>(mOnAttachStateChangeListeners)) l.onViewAttachedToWindow(this);
        }
        if (runQueue != null) {
            java.util.ArrayList<Object[]> q = runQueue;
            runQueue = null;
            for (Object[] item : q) info.handler.postDelayed((Runnable) item[0], (Long) item[1]);
        }
        refreshDrawableState();
    }

    void dispatchDetachedFromWindow() {
        if (mFocused) clearFocusInternal();
        onDetachedFromWindow();
        if (mOnAttachStateChangeListeners != null) {
            for (OnAttachStateChangeListener l : new java.util.ArrayList<>(mOnAttachStateChangeListeners)) l.onViewDetachedFromWindow(this);
        }
        mAttachInfo = null;
    }

    protected void onAttachedToWindow() {}

    protected void onDetachedFromWindow() {}

    public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) {
        if (mOnAttachStateChangeListeners == null) mOnAttachStateChangeListeners = new java.util.ArrayList<>();
        mOnAttachStateChangeListeners.add(listener);
    }

    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) {
        if (mOnAttachStateChangeListeners != null) mOnAttachStateChangeListeners.remove(listener);
    }

    public boolean post(Runnable action) { return postDelayed(action, 0); }

    public boolean postDelayed(Runnable action, long delayMillis) {
        if (mAttachInfo != null) return mAttachInfo.handler.postDelayed(action, delayMillis);
        if (runQueue == null) runQueue = new java.util.ArrayList<>();
        runQueue.add(new Object[] {action, delayMillis});
        return true;
    }

    public boolean removeCallbacks(Runnable action) {
        if (action == null) return true;
        if (mAttachInfo != null) mAttachInfo.handler.removeCallbacks(action);
        if (runQueue != null) runQueue.removeIf(item -> item[0] == action);
        android.os.Handler.removeEverywhere(action);
        return true;
    }

    public ViewTreeObserver getViewTreeObserver() {
        if (mAttachInfo != null) return mAttachInfo.treeObserver;
        if (floatingTreeObserver == null) floatingTreeObserver = new ViewTreeObserver();
        return floatingTreeObserver;
    }

    public android.os.IBinder getWindowToken() { return mAttachInfo != null ? new android.os.IBinder() : null; }
    public android.os.IBinder getApplicationWindowToken() { return getWindowToken(); }
    public boolean hasWindowFocus() { return mAttachInfo != null; }
    public Display getDisplay() { return new Display(); }

    // ------------------------------------------------------------------ geometry on screen
    public void getLocationInWindow(int[] outLocation) {
        float[] p = {0, 0};
        mapToWindow(p);
        outLocation[0] = Math.round(p[0]);
        outLocation[1] = Math.round(p[1]);
    }

    public void getLocationOnScreen(int[] outLocation) {
        getLocationInWindow(outLocation);
        if (mAttachInfo != null) { outLocation[0] += mAttachInfo.windowLeft(); outLocation[1] += mAttachInfo.windowTop(); }
    }

    /** Maps a point in this view's coordinates to window coordinates (translation, scale, rotation, scrolling). */
    public void mapToWindow(float[] p) {
        View v = this;
        while (v != null) {
            if (v.hasTransform()) {
                float px = v.getPivotX(), py = v.getPivotY();
                float x = (p[0] - px) * v.mScaleX, y = (p[1] - py) * v.mScaleY;
                if (v.mRotation != 0) {
                    double r = Math.toRadians(v.mRotation);
                    float cos = (float) Math.cos(r), sin = (float) Math.sin(r);
                    float rx = x * cos - y * sin, ry = x * sin + y * cos;
                    x = rx; y = ry;
                }
                p[0] = x + px; p[1] = y + py;
            }
            p[0] += v.mLeft + v.mTranslationX;
            p[1] += v.mTop + v.mTranslationY;
            if (!(v.mParent instanceof View)) break;
            View parent = (View) v.mParent;
            p[0] -= parent.mScrollX;
            p[1] -= parent.mScrollY;
            v = parent;
        }
    }

    public boolean getGlobalVisibleRect(Rect r) {
        int[] loc = new int[2];
        getLocationInWindow(loc);
        r.set(loc[0], loc[1], loc[0] + getWidth(), loc[1] + getHeight());
        return isShown() && getWidth() > 0 && getHeight() > 0;
    }

    public boolean getLocalVisibleRect(Rect r) { r.set(0, 0, getWidth(), getHeight()); return isShown(); }

    public void getHitRect(Rect outRect) { outRect.set(mLeft, mTop, mRight, mBottom); }

    public void getDrawingRect(Rect outRect) { outRect.set(mScrollX, mScrollY, mScrollX + getWidth(), mScrollY + getHeight()); }

    public boolean requestRectangleOnScreen(Rect rectangle) { return false; }

    // ------------------------------------------------------------------ insets
    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener listener) { mOnApplyWindowInsetsListener = listener; }

    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        if (mOnApplyWindowInsetsListener != null) return mOnApplyWindowInsetsListener.onApplyWindowInsets(this, insets);
        return onApplyWindowInsets(insets);
    }

    public WindowInsets onApplyWindowInsets(WindowInsets insets) { return insets; }

    public void requestApplyInsets() { if (mAttachInfo != null) mAttachInfo.requestApplyInsets(); }

    public WindowInsets getRootWindowInsets() { return WindowInsets.current(); }

    public WindowInsetsController getWindowInsetsController() { return null; }

    // ------------------------------------------------------------------ misc
    public boolean isInEditMode() { return false; }
    public boolean isHardwareAccelerated() { return true; }
    public boolean isInTouchMode() { return true; }
    public void bringToFront() { if (mParent instanceof ViewGroup) ((ViewGroup) mParent).bringChildToFront(this); }
    public boolean hasOverlappingRendering() { return true; }
    public boolean isOpaque() { return false; }
    public boolean onCheckIsTextEditor() { return false; }
    public void cancelLongPress() {}
    public void setOnCreateContextMenuListener(Object l) {}
    public void setTooltipText(CharSequence text) {}
    public void setAutofillHints(String... hints) {}
    public void setImportantForAutofill(int mode) {}

    @Override public String toString() { return getClass().getName() + "{" + Integer.toHexString(hashCode()) + " " + mLeft + "," + mTop + "-" + mRight + "," + mBottom + "}"; }
}
