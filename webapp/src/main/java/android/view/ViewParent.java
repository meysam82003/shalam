package android.view;

public interface ViewParent {
    void requestLayout();
    boolean isLayoutRequested();
    ViewParent getParent();
    void requestChildFocus(View child, View focused);
    void focusableViewAvailable(View v);
    void requestDisallowInterceptTouchEvent(boolean disallowIntercept);
}
