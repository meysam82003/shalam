package divan.web;

import org.teavm.jso.JSBody;

/** Toast: a small royal message near the bottom of the page (host.js). */
public final class WebToast {
    private WebToast() {}

    @JSBody(params = {"t", "ms"}, script = "DH.toast(t, ms);")
    public static native void show(String text, int ms);

    @JSBody(script = "DH.toast(null, 0);")
    public static native void hide();
}
