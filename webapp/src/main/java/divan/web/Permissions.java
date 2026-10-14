package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Runtime permissions: the microphone and notifications ask the browser; the result comes back like Android's. */
public final class Permissions {
    private Permissions() {}

    @JSFunctor interface Result extends JSObject { void call(boolean granted); }

    @JSBody(params = "cb", script = "DH.rec.ask(cb);")
    private static native void askMic(Result cb);

    @JSBody(params = "cb", script = "DH.askNotify(cb);")
    private static native void askNotify(Result cb);

    public static int check(String permission) {
        if (android.Manifest.permission.RECORD_AUDIO.equals(permission)) return Recorder.ready() ? 0 : -1;
        if ("android.permission.POST_NOTIFICATIONS".equals(permission)) return Host.notificationsAllowed() ? 0 : -1;
        return 0;
    }

    public static void request(android.app.Activity a, String[] permissions, int code) {
        String p = permissions.length > 0 ? permissions[0] : "";
        Result done = granted -> android.os.Looper.dispatch(() -> {
            int[] results = new int[permissions.length];
            java.util.Arrays.fill(results, granted ? 0 : -1);
            a.onRequestPermissionsResult(code, permissions, results);
        });
        if (android.Manifest.permission.RECORD_AUDIO.equals(p)) askMic(done);
        else if ("android.permission.POST_NOTIFICATIONS".equals(p)) askNotify(done);
        else done.call(true);
    }
}
