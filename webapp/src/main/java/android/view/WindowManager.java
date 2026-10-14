package android.view;

public interface WindowManager {
    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_DIM_BEHIND = 0x00000002, FLAG_NOT_FOCUSABLE = 0x00000008, FLAG_NOT_TOUCHABLE = 0x00000010,
            FLAG_KEEP_SCREEN_ON = 0x00000080, FLAG_LAYOUT_NO_LIMITS = 0x00000200, FLAG_FULLSCREEN = 0x00000400,
            FLAG_TRANSLUCENT_STATUS = 0x04000000, FLAG_TRANSLUCENT_NAVIGATION = 0x08000000, FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000,
            FLAG_SECURE = 0x00002000;
        public static final int SOFT_INPUT_STATE_UNSPECIFIED = 0, SOFT_INPUT_STATE_UNCHANGED = 1, SOFT_INPUT_STATE_HIDDEN = 2,
            SOFT_INPUT_STATE_ALWAYS_HIDDEN = 3, SOFT_INPUT_STATE_VISIBLE = 4, SOFT_INPUT_STATE_ALWAYS_VISIBLE = 5,
            SOFT_INPUT_ADJUST_UNSPECIFIED = 0x00, SOFT_INPUT_ADJUST_RESIZE = 0x10, SOFT_INPUT_ADJUST_PAN = 0x20, SOFT_INPUT_ADJUST_NOTHING = 0x30;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1, LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3;
        public int flags;
        public int softInputMode;
        public int windowAnimations;
        public float dimAmount = 0.6f;
        public int gravity;
        public float alpha = 1f;
        public int layoutInDisplayCutoutMode;
        public int x, y;

        public LayoutParams() { super(MATCH_PARENT, MATCH_PARENT); }

        public LayoutParams(int w, int h) { super(w, h); }
    }
}
