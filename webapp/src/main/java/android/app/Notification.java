package android.app;

/** Notification shown through the browser's notification API (when the user allowed it). */
public class Notification {
    public static final int PRIORITY_DEFAULT = 0, PRIORITY_HIGH = 1, PRIORITY_LOW = -1;
    public static final int VISIBILITY_PUBLIC = 1, VISIBILITY_PRIVATE = 0;
    public static final int DEFAULT_ALL = -1, DEFAULT_SOUND = 1, DEFAULT_VIBRATE = 2;
    public static final String CATEGORY_PROMO = "promo", CATEGORY_REMINDER = "reminder";

    public CharSequence title, text;
    public PendingIntent contentIntent;
    public android.graphics.Bitmap picture;

    public abstract static class Style {}

    public static class BigTextStyle extends Style {
        CharSequence big;
        public BigTextStyle() {}
        public BigTextStyle bigText(CharSequence cs) { big = cs; return this; }
        public BigTextStyle setBigContentTitle(CharSequence t) { return this; }
        public BigTextStyle setSummaryText(CharSequence t) { return this; }
    }

    public static class BigPictureStyle extends Style {
        android.graphics.Bitmap picture;
        public BigPictureStyle() {}
        public BigPictureStyle bigPicture(android.graphics.Bitmap b) { picture = b; return this; }
        public BigPictureStyle setSummaryText(CharSequence cs) { return this; }
        public BigPictureStyle setBigContentTitle(CharSequence t) { return this; }
        public BigPictureStyle bigLargeIcon(android.graphics.Bitmap b) { return this; }
    }

    public static class Builder {
        private final Notification n = new Notification();
        public Builder(android.content.Context context) {}
        public Builder(android.content.Context context, String channelId) {}
        public Builder setContentTitle(CharSequence title) { n.title = title; return this; }
        public Builder setContentText(CharSequence text) { n.text = text; return this; }
        public Builder setContentIntent(PendingIntent intent) { n.contentIntent = intent; return this; }
        public Builder setSmallIcon(int icon) { return this; }
        public Builder setLargeIcon(android.graphics.Bitmap b) { return this; }
        public Builder setAutoCancel(boolean autoCancel) { return this; }
        public Builder setColor(int argb) { return this; }
        public Builder setOnlyAlertOnce(boolean b) { return this; }
        public Builder setPriority(int pri) { return this; }
        public Builder setCategory(String category) { return this; }
        public Builder setVisibility(int visibility) { return this; }
        public Builder setWhen(long when) { return this; }
        public Builder setShowWhen(boolean show) { return this; }
        public Builder setTicker(CharSequence t) { return this; }
        public Builder setDefaults(int defaults) { return this; }
        public Builder setStyle(Style style) {
            if (style instanceof BigTextStyle && ((BigTextStyle) style).big != null) n.text = ((BigTextStyle) style).big;
            if (style instanceof BigPictureStyle) n.picture = ((BigPictureStyle) style).picture;
            return this;
        }
        public Notification build() { return n; }
    }
}
