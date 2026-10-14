package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * The separate browser window of a web payment (see android.webkit.WebView). Divan's pages in that window post
 * {divan: "back", href: "divan://pay/…"} to this page when the buyer taps «back to Divan», then close themselves.
 */
public final class PayWindow {
    private PayWindow() {}

    public interface Listener { void onBack(String href); }

    @JSFunctor
    interface Cb extends JSObject { void call(String href); }

    private static final java.util.ArrayList<Listener> listeners = new java.util.ArrayList<>();
    private static boolean hooked;

    public static void listen(Listener l) {
        listeners.add(l);
        if (!hooked) { hooked = true; hook(href -> android.os.Looper.dispatch(() -> { for (Listener x : new java.util.ArrayList<>(listeners)) x.onBack(href); })); }
    }

    public static void unlisten(Listener l) { listeners.remove(l); }

    @JSBody(params = "cb", script = "DH.onPayBack(cb);")
    private static native void hook(Cb cb);

    @JSBody(params = "u", script = "return DH.payOpen(u);")
    public static native boolean open(String url);

    @JSBody(script = "return DH.payFocus();")
    public static native boolean focus();

    @JSBody(script = "return DH.payIsOpen();")
    public static native boolean isOpen();

    @JSBody(script = "DH.payClose();")
    public static native void close();
}
