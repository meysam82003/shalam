package android.os;

import org.teavm.jso.JSBody;

public final class SystemClock {
    private SystemClock() {}

    @JSBody(script = "return Math.floor(performance.now());")
    private static native double perf();

    public static long uptimeMillis() { return (long) perf(); }
    public static long elapsedRealtime() { return (long) perf(); }
    public static long elapsedRealtimeNanos() { return (long) perf() * 1000000L; }
    public static long currentThreadTimeMillis() { return (long) perf(); }
    public static void sleep(long ms) {}
}
