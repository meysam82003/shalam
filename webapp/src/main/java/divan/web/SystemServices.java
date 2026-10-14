package divan.web;

/** Context.getSystemService for the browser. */
public final class SystemServices {
    private SystemServices() {}

    private static android.content.ClipboardManager clipboard;
    private static android.app.NotificationManager notifications;
    private static android.view.inputmethod.InputMethodManager ime;
    private static android.media.AudioManager audio;

    public static Object get(String name, android.content.Context context) {
        if (name == null) return null;
        switch (name) {
            case android.content.Context.CLIPBOARD_SERVICE: return clipboard != null ? clipboard : (clipboard = new android.content.ClipboardManager());
            case android.content.Context.NOTIFICATION_SERVICE: return notifications != null ? notifications : (notifications = new android.app.NotificationManager());
            case android.content.Context.INPUT_METHOD_SERVICE: return ime != null ? ime : (ime = new android.view.inputmethod.InputMethodManager());
            case android.content.Context.AUDIO_SERVICE: return audio != null ? audio : (audio = new android.media.AudioManager());
            case android.content.Context.JOB_SCHEDULER_SERVICE: return WebJobScheduler.INSTANCE;
            default: return null;
        }
    }
}
