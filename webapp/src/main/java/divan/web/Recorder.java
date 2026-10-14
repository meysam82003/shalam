package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** MediaRecorder on the microphone (the permission prompt already gave host.js the stream). */
public final class Recorder {
    private Recorder() {}

    @JSFunctor interface Saved extends JSObject { void call(@JSByRef byte[] data); }

    @JSBody(script = "return DH.rec.ready();")
    public static native boolean ready();

    @JSBody(script = "return DH.rec.start();")
    private static native int start0();

    @JSBody(params = {"s", "discard", "cb"}, script = "DH.rec.stop(s, discard, cb);")
    private static native void stop0(int session, boolean discard, Saved cb);

    @JSBody(script = "return DH.rec.level();")
    public static native int amplitude();

    private static final java.util.HashMap<Integer, String> outputs = new java.util.HashMap<>();

    public static int start(String output) {
        int s = start0();
        if (s >= 0) outputs.put(s, output);
        return s;
    }

    /** The recording is written to the file the app chose once the browser hands it over (a moment later). */
    public static void stop(int session, boolean discard) {
        String out = outputs.remove(session);
        stop0(session, discard, data -> {
            if (discard || out == null || data == null) return;
            byte[] copy = java.util.Arrays.copyOf(data, data.length);
            android.os.Looper.dispatch(() -> Storage.writeFile(new java.io.File(out).getAbsolutePath(), copy));
        });
    }
}
