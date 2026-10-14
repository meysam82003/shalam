package android.view;

/** What an attached view needs from its window (implemented by the browser window, divan.web.WebWindow). */
public abstract class AttachInfo {
    public final ViewTreeObserver treeObserver = new ViewTreeObserver();
    public final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    public View focused;

    public abstract void scheduleTraversal();

    public abstract void requestLayoutRoot();

    public abstract View rootView();

    /** Window position of the root on the screen (dialogs are placed inside the page). */
    public abstract int windowLeft();

    public abstract int windowTop();

    public abstract void requestApplyInsets();

    public abstract void onFocusChanged(View old, View now);

    public abstract void vibrate(int ms);

    public abstract float density();
}
