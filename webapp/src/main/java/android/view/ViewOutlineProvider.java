package android.view;

public abstract class ViewOutlineProvider {
    public static final ViewOutlineProvider BACKGROUND = new ViewOutlineProvider() { @Override public void getOutline(View view, Object outline) {} };
    public abstract void getOutline(View view, Object outline);
}
