package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** MessageQueue idle handlers: run when the browser is idle. */
public final class Idle {
    private Idle() {}

    @JSFunctor interface Cb extends JSObject { void call(); }

    @JSBody(params = "cb", script = "(window.requestIdleCallback || function (f) { return setTimeout(f, 50); })(function () { cb(); });")
    private static native void idle(Cb cb);

    public static void request(Runnable r) { idle(() -> android.os.Looper.dispatch(r)); }
}
