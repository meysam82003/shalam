package android.app;

public class NotificationManager {
    public static final int IMPORTANCE_NONE = 0, IMPORTANCE_MIN = 1, IMPORTANCE_LOW = 2, IMPORTANCE_DEFAULT = 3, IMPORTANCE_HIGH = 4;

    public void createNotificationChannel(NotificationChannel channel) {}
    public boolean areNotificationsEnabled() { return divan.web.Host.notificationsAllowed(); }
    public void notify(int id, Notification notification) { divan.web.Host.notify(id, notification); }
    public void notify(String tag, int id, Notification notification) { notify(id, notification); }
    public void cancel(int id) {}
    public void cancelAll() {}
    public NotificationChannel getNotificationChannel(String id) { return null; }
}
