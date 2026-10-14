package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Ad videos: a muted inline video element of the page kept over each VideoView. */
public final class Videos {
    private Videos() {}

    @JSFunctor interface Event extends JSObject { void call(int id, int kind); }

    @JSBody(params = {"id", "src", "play", "cb"}, script = "DH.video(id, src, play, cb);")
    private static native void set(int id, String src, boolean play, Event cb);

    @JSBody(params = {"id", "x", "y", "w", "h", "visible"}, script = "DH.videoPlace(id, x, y, w, h, visible);")
    private static native void placeOne(int id, float x, float y, float w, float h, boolean visible);

    @JSBody(params = "id", script = "DH.videoRemove(id);")
    private static native void remove0(int id);

    private static final java.util.HashMap<android.widget.VideoView, Integer> ids = new java.util.HashMap<>();
    private static int next = 1;

    public static void update(android.widget.VideoView v) {
        if (!v.isAttachedToWindow() || v.videoPath() == null) return;
        Integer id = ids.get(v);
        if (id == null) { id = next++; ids.put(v, id); }
        set(id, src(v.videoPath()), v.wantsPlay(), (vid, kind) -> android.os.Looper.dispatch(() -> {
            if (kind == 0) v.firePrepared(); else v.fireCompletion();
        }));
        WebWindow.schedule();
    }

    private static String src(String path) {
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("blob:")) return path;
        return Storage.blobUrl(new java.io.File(path).getAbsolutePath());
    }

    public static void remove(android.widget.VideoView v) {
        Integer id = ids.remove(v);
        if (id != null) remove0(id);
    }

    static void place() {
        for (java.util.Map.Entry<android.widget.VideoView, Integer> e : ids.entrySet()) {
            android.widget.VideoView v = e.getKey();
            int[] loc = new int[2];
            v.getLocationInWindow(loc);
            boolean shown = v.isShown();
            float d = 1f / android.content.res.Resources.INSTANCE.getDisplayMetrics().density;
            placeOne(e.getValue(), loc[0] * d, loc[1] * d, v.getWidth() * d, v.getHeight() * d, shown);
        }
    }
}
