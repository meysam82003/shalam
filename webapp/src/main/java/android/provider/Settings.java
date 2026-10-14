package android.provider;

public final class Settings {
    public static final class Secure {
        public static final String ANDROID_ID = "android_id";
        /** A random id created once per browser profile and kept with the app's data. */
        public static String getString(android.content.ContentResolver resolver, String name) {
            return ANDROID_ID.equals(name) ? divan.web.Platform.deviceSeed() : null;
        }
    }

    public static final class System {
        public static String getString(android.content.ContentResolver resolver, String name) { return null; }
    }

    public static final String ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS";
    public static final String EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";
}
