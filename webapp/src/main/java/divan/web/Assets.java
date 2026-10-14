package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;

/** The app's assets folder, loaded by the page before start (host.js, DH.boot.assets). */
public final class Assets {
    private Assets() {}

    @JSBody(params = "p", script = "var a = DH.boot.assets[p]; return a ? a : null;")
    @JSByRef
    private static native byte[] bytes(String p);

    @JSBody(params = "p", script = "return !!DH.boot.assets[p];")
    private static native boolean has(String p);

    @JSBody(params = "p", script = "var out = []; var pre = p ? p.replace(/\\/$/, '') + '/' : ''; Object.keys(DH.boot.assets).forEach(function (k) { if (k.indexOf(pre) === 0) { var rest = k.substring(pre.length); var n = rest.split('/')[0]; if (out.indexOf(n) < 0) out.push(n); } }); return out.join('\\n');")
    private static native String names(String p);

    public static byte[] get(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (!has(p)) return null;
        byte[] b = bytes(p);
        return java.util.Arrays.copyOf(b, b.length);
    }

    public static String[] list(String path) {
        String s = names(path == null ? "" : path);
        return s.isEmpty() ? new String[0] : s.split("\n");
    }
}
