package android.os;

/** Build values of the browser version (behaves like Android 15 for the app's version checks). */
public class Build {
    public static final String FINGERPRINT = divan.web.Platform.fingerprint();
    public static final String MANUFACTURER = divan.web.Platform.manufacturer();
    public static final String MODEL = divan.web.Platform.model();
    public static final String BRAND = MANUFACTURER;
    public static final String DEVICE = "web";
    public static final String PRODUCT = "web";
    public static final String HARDWARE = "web";
    public static final String BOARD = "web";
    public static final String[] SUPPORTED_ABIS = {"web"};

    public static class VERSION {
        public static final int SDK_INT = divan.web.Platform.sdkInt();
        public static final String RELEASE = "15";
        public static final String CODENAME = "REL";
    }

    public static class VERSION_CODES {
        public static final int LOLLIPOP = 21, LOLLIPOP_MR1 = 22, M = 23, N = 24, N_MR1 = 25, O = 26, O_MR1 = 27, P = 28, Q = 29, R = 30, S = 31,
            S_V2 = 32, TIRAMISU = 33, UPSIDE_DOWN_CAKE = 34, VANILLA_ICE_CREAM = 35;
    }
}
