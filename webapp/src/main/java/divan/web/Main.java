package divan.web;

import com.meysam.divanemtiaz.MainActivity;

/**
 * Entry point of the web, Windows and iPhone versions. host.js loads the stored files and settings, then calls
 * main(); from here the app runs exactly as on Android: Application, MainActivity.onCreate, onResume, and the
 * same lifecycle calls when the page is hidden and shown again.
 */
public final class Main {
    private Main() {}

    private static MainActivity activity;
    private static boolean resumed;

    @org.teavm.jso.JSBody(params = "m", script = "DH.crash(m);")
    private static native void crash(String m);

    @org.teavm.jso.JSBody(script = "DH.started();")
    private static native void started();

    public static void main(String[] args) {
        android.os.Looper.setCrashHandler(t -> {
            java.io.StringWriter w = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(w));
            JS.error(w.toString());
            crash(String.valueOf(t));
        });
        android.os.Looper.dispatch(Main::start);
    }

    private static void start() {
        Storage.init();
        Storage.decodeStoredImages(Main::run);
    }

    private static void run() {
        WebWindow.init();
        com.meysam.divanemtiaz.Billing.INSTANCE.setOverride(new com.meysam.divanemtiaz.FlavorBilling());
        WebJobScheduler.register(com.meysam.divanemtiaz.NoticeJob.class, com.meysam.divanemtiaz.NoticeJob::new);
        activity = new MainActivity();
        activity.performCreate(null);
        activity.performStart();
        resume();
        started();
    }

    private static void resume() {
        if (resumed || activity == null) return;
        resumed = true;
        activity.performResume();
        activity.onWindowFocusChanged(true);
    }

    static void resumed() {
        if (activity == null || resumed) return;
        activity.performRestart();
        activity.performStart();
        resume();
    }

    static void paused() {
        if (activity == null || !resumed) return;
        resumed = false;
        activity.onWindowFocusChanged(false);
        activity.performPause();
        activity.performSaveInstanceState(new android.os.Bundle());
        activity.performStop();
    }

    /** A notification or a divan:// link: delivered like Android's onNewIntent. */
    static void newIntent(android.content.Intent intent) {
        if (activity == null || intent == null) return;
        activity.setIntent(intent);
        activity.performNewIntent(intent);
    }
}
