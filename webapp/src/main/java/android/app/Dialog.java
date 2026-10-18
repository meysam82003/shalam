package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;

/** A dialog window over the page: dimmed background, content sized by Window.setLayout, back/outside to cancel. */
public class Dialog implements DialogInterface {
    private final Context context;
    private final Window window;
    private View content;
    private boolean showing;
    private boolean cancelable = true;
    private boolean cancelOnTouchOutside = true;
    private OnDismissListener dismissListener;
    private OnCancelListener cancelListener;
    private OnShowListener showListener;

    public Dialog(Context context) { this(context, 0); }

    public Dialog(Context context, int themeResId) {
        this.context = context;
        this.window = new Window(context, true);
    }

    public final Context getContext() { return context; }
    public Window getWindow() { return window; }
    public boolean dispatchTouchEvent(android.view.MotionEvent event) { return window.getDecorView().onTouchEvent(event); }
    public final boolean requestWindowFeature(int featureId) { return true; }

    public void setContentView(View view) {
        content = view;
        window.setContentView(view);
    }

    public void setContentView(View view, android.view.ViewGroup.LayoutParams params) { setContentView(view); }

    public <T extends View> T findViewById(int id) { return content == null ? null : content.findViewById(id); }

    public void setCancelable(boolean flag) { cancelable = flag; if (!flag) cancelOnTouchOutside = false; }
    public void setCanceledOnTouchOutside(boolean cancel) { if (cancel && !cancelable) cancelable = true; cancelOnTouchOutside = cancel; }
    public boolean isCancelable() { return cancelable; }
    public boolean cancelsOnTouchOutside() { return cancelOnTouchOutside; }
    public void setOnDismissListener(OnDismissListener listener) { dismissListener = listener; }
    public void setOnCancelListener(OnCancelListener listener) { cancelListener = listener; }
    public void setOnShowListener(OnShowListener listener) { showListener = listener; }
    public void setOnKeyListener(OnKeyListener onKeyListener) {}
    public boolean isShowing() { return showing; }
    public void setTitle(CharSequence title) {}

    public void show() {
        if (showing) return;
        showing = true;
        divan.web.WebWindow.showDialog(this, content);
        if (showListener != null) showListener.onShow(this);
    }

    public void hide() { dismiss(); }

    @Override public void dismiss() {
        if (!showing) return;
        showing = false;
        divan.web.WebWindow.dismissDialog(this);
        if (dismissListener != null) dismissListener.onDismiss(this);
    }

    @Override public void cancel() {
        if (cancelListener != null) cancelListener.onCancel(this);
        dismiss();
    }

    /** Back button or a tap outside the content. */
    public void onBackPressed() { if (cancelable) cancel(); }

    public void create() {}
}
