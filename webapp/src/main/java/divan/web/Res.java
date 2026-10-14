package divan.web;

import org.teavm.jso.JSBody;

/** Resource ids looked up by name: the app's raw sounds (served next to the page) and its dialog animation style. */
public final class Res {
    private Res() {}

    private static final int RAW = 0x7f0b0000, STYLE = 0x7f120000;
    public static final int DIALOG_ANIMATION = STYLE + 1;

    @JSBody(script = "return (DH.boot.raw || []).join('\\n');")
    private static native String rawNames();

    private static String[] raw;

    private static String[] raw() {
        if (raw == null) { String s = rawNames(); raw = s.isEmpty() ? new String[0] : s.split("\n"); }
        return raw;
    }

    public static int id(String name, String type) {
        if ("raw".equals(type)) {
            String[] r = raw();
            for (int i = 0; i < r.length; i++) if (r[i].equals(name)) return RAW + i;
            return 0;
        }
        if ("style".equals(type) && "DivanDialogAnimation".equals(name)) return DIALOG_ANIMATION;
        return 0;
    }

    public static String name(int id) {
        String[] r = raw();
        if (id >= RAW && id < RAW + r.length) return Platform.packageName() + ":raw/" + r[id - RAW];
        return null;
    }

    public static String string(int id) { return ""; }

    /** Address of a raw sound, relative to the page. */
    public static String rawUrl(int id) {
        String[] r = raw();
        return id >= RAW && id < RAW + r.length ? "res/raw/" + r[id - RAW] + ".mp3" : null;
    }
}
