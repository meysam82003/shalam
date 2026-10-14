package divan.web;

import org.teavm.jso.JSBody;

/** Values of this build, written into the page by the build (window.DIVAN_CONFIG). */
public final class Platform {
    private Platform() {}

    @JSBody(params = "k", script = "var c = window.DIVAN_CONFIG || {}; var v = c[k]; return v == null ? null : String(v);")
    private static native String cfg(String key);

    @JSBody(script = "return navigator.userAgent || '';")
    private static native String ua();

    private static String or(String v, String d) { return v == null || v.isEmpty() ? d : v; }

    public static String fingerprint() { return "web"; }
    public static int sdkInt() { return 35; }
    public static String packageName() { return or(cfg("package"), "com.meysam.divanemtiaz.web"); }
    public static String versionName() { return or(cfg("versionName"), "5.5.0"); }
    public static int versionCode() { try { return Integer.parseInt(or(cfg("versionCode"), "23")); } catch (NumberFormatException e) { return 23; } }
    public static String channel() { return or(cfg("channel"), "web"); }

    public static String manufacturer() {
        String u = ua();
        if (u.contains("iPhone") || u.contains("iPad") || u.contains("Macintosh")) return "Apple";
        if (u.contains("Windows")) return "Windows";
        if (u.contains("Android")) return "Android";
        return "Web";
    }

    public static String model() {
        String u = ua();
        String browser = u.contains("Edg/") ? "Edge" : u.contains("Firefox/") ? "Firefox" : u.contains("Chrome/") ? "Chrome" : u.contains("Safari/") ? "Safari" : "Browser";
        String kind = u.contains("iPhone") ? "iPhone" : u.contains("iPad") ? "iPad" : u.contains("Android") ? "Phone" : "Desktop";
        return kind + " " + browser + ("windows".equals(channel()) ? " App" : "");
    }

    /** Build meta-data (same keys as the Android manifest). */
    public static android.os.Bundle metaData() {
        android.os.Bundle b = new android.os.Bundle();
        b.putString("divan.store", or(cfg("store"), "direct"));
        b.putString("divan.api", or(cfg("api"), "https://httpuser87890.ir/divan/api/index.php"));
        b.putString("divan.licenseKey", or(cfg("licenseKey"), ""));
        b.putString("divan.myketKey", "");
        b.putString("divan.tapsellAppId", "");
        return b;
    }

    /** Random id of this browser profile, kept with the app's data (the «ANDROID_ID» of the web version). */
    public static String deviceSeed() {
        android.content.SharedPreferences p = WebPrefs.get("divan_web_device");
        String seed = p.getString("seed", null);
        if (seed == null) {
            seed = java.util.UUID.randomUUID().toString().replace("-", "");
            p.edit().putString("seed", seed).apply();
        }
        return seed;
    }
}
