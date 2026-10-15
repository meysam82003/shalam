package android.window;

/** Android 12+ splash screen of an activity; on the web the loading page plays this part (see LaunchLook). */
public interface SplashScreen {
    void setSplashScreenTheme(int themeId);
    int getSplashScreenTheme();
}
