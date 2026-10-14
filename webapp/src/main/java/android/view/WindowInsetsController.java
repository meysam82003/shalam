package android.view;

public interface WindowInsetsController {
    int APPEARANCE_LIGHT_STATUS_BARS = 8;
    int APPEARANCE_LIGHT_NAVIGATION_BARS = 16;
    int BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 2;
    void setSystemBarsAppearance(int appearance, int mask);
    void hide(int types);
    void show(int types);
    void setSystemBarsBehavior(int behavior);
}
