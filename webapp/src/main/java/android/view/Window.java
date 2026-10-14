package android.view;

import android.graphics.drawable.Drawable;

/**
 * Window of an Activity or Dialog: holds the content, the layout size, dim and flags. The browser window
 * (divan.web.WebWindow) places and draws it.
 */
public class Window {
    public static final int FEATURE_NO_TITLE = 1, FEATURE_ACTION_BAR = 8;
    public static final int ID_ANDROID_CONTENT = 0x01020002;

    private final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
    private final android.content.Context context;
    private Drawable background;
    private View decor;
    private int statusBarColor, navigationBarColor;
    private boolean decorFits = true;
    private final boolean isDialog;
    private Runnable onChange;

    public Window(android.content.Context context, boolean dialog) {
        this.context = context;
        this.isDialog = dialog;
        if (dialog) {
            attrs.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            attrs.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            attrs.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            attrs.dimAmount = 0.6f;
        }
    }

    public boolean isDialog() { return isDialog; }
    public android.content.Context getContext() { return context; }

    /** Called by the browser window to learn about changes (size, flags, background). */
    public void setOnChange(Runnable r) { onChange = r; }

    private void changed() { if (onChange != null) onChange.run(); }

    public final WindowManager.LayoutParams getAttributes() { return attrs; }

    public void setAttributes(WindowManager.LayoutParams a) {
        if (a != attrs) {
            attrs.flags = a.flags; attrs.softInputMode = a.softInputMode; attrs.windowAnimations = a.windowAnimations;
            attrs.dimAmount = a.dimAmount; attrs.width = a.width; attrs.height = a.height; attrs.gravity = a.gravity; attrs.alpha = a.alpha;
        }
        changed();
    }

    public void setLayout(int width, int height) { attrs.width = width; attrs.height = height; changed(); }
    public void setGravity(int gravity) { attrs.gravity = gravity; changed(); }
    public void addFlags(int flags) { setFlags(flags, flags); }
    public void clearFlags(int flags) { setFlags(0, flags); }
    public void setFlags(int flags, int mask) { attrs.flags = (attrs.flags & ~mask) | (flags & mask); changed(); }
    public void setDimAmount(float amount) { attrs.dimAmount = amount; changed(); }
    public void setSoftInputMode(int mode) { attrs.softInputMode = mode; changed(); }
    public void setWindowAnimations(int resId) { attrs.windowAnimations = resId; }
    public void setBackgroundDrawable(Drawable drawable) { background = drawable; changed(); }
    public void setBackgroundDrawableResource(int resId) {}
    public Drawable backgroundDrawable() { return background; }
    public void setStatusBarColor(int color) { statusBarColor = color; changed(); }
    public int getStatusBarColor() { return statusBarColor; }
    public void setNavigationBarColor(int color) { navigationBarColor = color; changed(); }
    public int getNavigationBarColor() { return navigationBarColor; }
    public void setDecorFitsSystemWindows(boolean decorFitsSystemWindows) { decorFits = decorFitsSystemWindows; }
    public void setNavigationBarContrastEnforced(boolean enforce) {}
    public void setStatusBarContrastEnforced(boolean enforce) {}

    public WindowInsetsController getInsetsController() {
        return new WindowInsetsController() {
            @Override public void setSystemBarsAppearance(int appearance, int mask) { divan.web.WebWindow.setLightBars((appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0); }
            @Override public void hide(int types) {}
            @Override public void show(int types) {}
            @Override public void setSystemBarsBehavior(int behavior) {}
        };
    }

    public View getDecorView() { return decor; }
    public void setDecorView(View v) { decor = v; }
    public View peekDecorView() { return decor; }
    public void setContentView(View v) { decor = v; changed(); }
    public <T extends View> T findViewById(int id) { return decor == null ? null : decor.findViewById(id); }
    public boolean requestFeature(int featureId) { return true; }
    public void setTitle(CharSequence title) {}
}
