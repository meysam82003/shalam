package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Clipboard and the browser's notifications. */
public final class Host {
    private Host() {}

    @JSFunctor interface Click extends JSObject { void call(int id); }

    @JSBody(params = "t", script = "DH.copy(t);")
    public static native void copyText(String text);

    @JSBody(script = "return typeof Notification !== 'undefined' && Notification.permission === 'granted';")
    public static native boolean notificationsAllowed();

    @JSBody(params = {"id", "title", "body", "cb"}, script = "DH.notify(id, title, body, cb);")
    private static native void show(int id, String title, String body, Click cb);

    private static final java.util.HashMap<Integer, android.content.Intent> targets = new java.util.HashMap<>();

    public static void notify(int id, android.app.Notification n) {
        if (n == null) return;
        if (n.contentIntent != null) targets.put(id, n.contentIntent.intent());
        show(id, n.title == null ? "" : n.title.toString(), n.text == null ? "" : n.text.toString(),
            clicked -> android.os.Looper.dispatch(() -> Main.newIntent(targets.remove(clicked))));
    }
}
