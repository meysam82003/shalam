package android.app;

public final class PendingIntent {
    public static final int FLAG_ONE_SHOT = 1 << 30, FLAG_NO_CREATE = 1 << 29, FLAG_CANCEL_CURRENT = 1 << 28, FLAG_UPDATE_CURRENT = 1 << 27,
        FLAG_IMMUTABLE = 1 << 26, FLAG_MUTABLE = 1 << 25;
    final android.content.Intent intent;
    private PendingIntent(android.content.Intent intent) { this.intent = intent; }
    public static PendingIntent getActivity(android.content.Context context, int requestCode, android.content.Intent intent, int flags) { return new PendingIntent(intent); }
    public static PendingIntent getBroadcast(android.content.Context context, int requestCode, android.content.Intent intent, int flags) { return new PendingIntent(intent); }
    public android.content.Intent intent() { return intent; }
}
