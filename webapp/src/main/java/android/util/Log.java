package android.util;

public final class Log {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6;
    public static int v(String tag, String msg) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int i(String tag, String msg) { divan.web.JS.log(tag + ": " + msg); return 0; }
    public static int w(String tag, String msg) { divan.web.JS.log(tag + ": " + msg); return 0; }
    public static int w(String tag, String msg, Throwable tr) { divan.web.JS.log(tag + ": " + msg + " " + tr); return 0; }
    public static int w(String tag, Throwable tr) { divan.web.JS.log(tag + ": " + tr); return 0; }
    public static int e(String tag, String msg) { divan.web.JS.error(tag + ": " + msg); return 0; }
    public static int e(String tag, String msg, Throwable tr) { divan.web.JS.error(tag + ": " + msg + " " + tr); return 0; }
    public static String getStackTraceString(Throwable tr) { return String.valueOf(tr); }
    public static boolean isLoggable(String tag, int level) { return level >= WARN; }
}
