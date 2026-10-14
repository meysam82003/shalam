package android.view;

public class KeyEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1;
    public static final int KEYCODE_BACK = 4, KEYCODE_ENTER = 66;
    private final int action, code;

    public KeyEvent(int action, int code) { this.action = action; this.code = code; }

    public int getAction() { return action; }
    public int getKeyCode() { return code; }
}
