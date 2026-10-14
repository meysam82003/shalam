package android.content;

public interface DialogInterface {
    int BUTTON_POSITIVE = -1, BUTTON_NEGATIVE = -2, BUTTON_NEUTRAL = -3;
    void cancel();
    void dismiss();
    interface OnDismissListener { void onDismiss(DialogInterface dialog); }
    interface OnCancelListener { void onCancel(DialogInterface dialog); }
    interface OnShowListener { void onShow(DialogInterface dialog); }
    interface OnClickListener { void onClick(DialogInterface dialog, int which); }
    interface OnKeyListener { boolean onKey(DialogInterface dialog, int keyCode, android.view.KeyEvent event); }
}
