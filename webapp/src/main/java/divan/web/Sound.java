package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** MediaPlayer and ToneGenerator on the browser's audio (host.js, DH.snd). */
public final class Sound {
    private Sound() {}

    @JSFunctor interface Done extends JSObject { void call(); }

    @JSBody(params = "u", script = "return DH.snd.loadUrl(u);")
    private static native int loadUrl(String url);

    @JSBody(params = "b", script = "return DH.snd.loadBytes(b);")
    private static native int loadBytes(@JSByRef byte[] data);

    public static int load(String url, byte[] data) { return data != null ? loadBytes(data) : url != null ? loadUrl(url) : -1; }

    @JSBody(params = {"h", "loop", "vol", "done"}, script = "DH.snd.play(h, loop, vol, done);")
    private static native void play0(int h, boolean loop, float vol, Done done);

    public static void play(int h, boolean loop, float volume, Runnable onEnd) { play0(h, loop, volume, () -> android.os.Looper.dispatch(onEnd)); }

    @JSBody(params = "h", script = "DH.snd.stop(h);")
    public static native void stop(int h);

    @JSBody(params = "h", script = "DH.snd.release(h);")
    public static native void release(int h);

    @JSBody(params = "h", script = "return DH.snd.playing(h);")
    public static native boolean playing(int h);

    @JSBody(params = {"h", "v"}, script = "DH.snd.volume(h, v);")
    public static native void volume(int h, float v);

    @JSBody(params = "h", script = "return DH.snd.duration(h);")
    public static native int duration(int h);

    @JSBody(params = {"type", "ms", "vol"}, script = "DH.snd.tone(type, ms, vol);")
    public static native void tone(int type, int ms, float volume);

    @JSBody(script = "DH.snd.stopTone();")
    public static native void stopTone();
}
