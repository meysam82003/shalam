package android.app;

public final class NotificationChannel {
    private final String id;
    private final CharSequence name;
    private String description;
    public NotificationChannel(String id, CharSequence name, int importance) { this.id = id; this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public String getId() { return id; }
    public void setShowBadge(boolean show) {}
    public void enableVibration(boolean v) {}
    public void setSound(android.net.Uri sound, Object attrs) {}
}
