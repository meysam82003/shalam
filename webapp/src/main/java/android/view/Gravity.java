package android.view;

/** android.view.Gravity constants and the absolute-gravity resolution of AOSP. */
public class Gravity {
    public static final int NO_GRAVITY = 0x0000;
    public static final int AXIS_SPECIFIED = 0x0001, AXIS_PULL_BEFORE = 0x0002, AXIS_PULL_AFTER = 0x0004, AXIS_CLIP = 0x0008;
    public static final int AXIS_X_SHIFT = 0, AXIS_Y_SHIFT = 4;
    public static final int TOP = (AXIS_PULL_BEFORE | AXIS_SPECIFIED) << AXIS_Y_SHIFT;
    public static final int BOTTOM = (AXIS_PULL_AFTER | AXIS_SPECIFIED) << AXIS_Y_SHIFT;
    public static final int LEFT = (AXIS_PULL_BEFORE | AXIS_SPECIFIED) << AXIS_X_SHIFT;
    public static final int RIGHT = (AXIS_PULL_AFTER | AXIS_SPECIFIED) << AXIS_X_SHIFT;
    public static final int CENTER_VERTICAL = AXIS_SPECIFIED << AXIS_Y_SHIFT;
    public static final int FILL_VERTICAL = TOP | BOTTOM;
    public static final int CENTER_HORIZONTAL = AXIS_SPECIFIED << AXIS_X_SHIFT;
    public static final int FILL_HORIZONTAL = LEFT | RIGHT;
    public static final int CENTER = CENTER_VERTICAL | CENTER_HORIZONTAL;
    public static final int FILL = FILL_VERTICAL | FILL_HORIZONTAL;
    public static final int CLIP_VERTICAL = AXIS_CLIP << AXIS_Y_SHIFT;
    public static final int CLIP_HORIZONTAL = AXIS_CLIP << AXIS_X_SHIFT;
    public static final int RELATIVE_LAYOUT_DIRECTION = 0x00800000;
    public static final int HORIZONTAL_GRAVITY_MASK = (AXIS_SPECIFIED | AXIS_PULL_BEFORE | AXIS_PULL_AFTER) << AXIS_X_SHIFT;
    public static final int VERTICAL_GRAVITY_MASK = (AXIS_SPECIFIED | AXIS_PULL_BEFORE | AXIS_PULL_AFTER) << AXIS_Y_SHIFT;
    public static final int DISPLAY_CLIP_VERTICAL = 0x10000000, DISPLAY_CLIP_HORIZONTAL = 0x01000000;
    public static final int START = RELATIVE_LAYOUT_DIRECTION | LEFT;
    public static final int END = RELATIVE_LAYOUT_DIRECTION | RIGHT;
    public static final int RELATIVE_HORIZONTAL_GRAVITY_MASK = START | END;

    public static int getAbsoluteGravity(int gravity, int layoutDirection) {
        int result = gravity;
        if ((result & RELATIVE_LAYOUT_DIRECTION) > 0) {
            if ((result & START) == START) {
                result &= ~START;
                result |= layoutDirection == View.LAYOUT_DIRECTION_RTL ? RIGHT : LEFT;
            } else if ((result & END) == END) {
                result &= ~END;
                result |= layoutDirection == View.LAYOUT_DIRECTION_RTL ? LEFT : RIGHT;
            }
            result &= ~RELATIVE_LAYOUT_DIRECTION;
        }
        return result;
    }

    public static boolean isVertical(int gravity) { return gravity > 0 && (gravity & VERTICAL_GRAVITY_MASK) != 0; }

    public static boolean isHorizontal(int gravity) { return gravity > 0 && (gravity & RELATIVE_HORIZONTAL_GRAVITY_MASK) != 0; }
}
