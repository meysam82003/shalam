package divan.web;

/** A view that pointer dragging and the mouse wheel scroll (ScrollView, HorizontalScrollView). */
public interface TouchScrollable {
    boolean scrollsVertically();
    /** True when there is content to scroll in the given direction (sign of delta). */
    boolean canScrollBy(int delta);
    void dragBy(int delta);
    void flingWith(int velocity);
    void stopFling();
}
