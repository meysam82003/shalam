package android.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

/**
 * The page's single Activity. The browser window (divan.web.WebWindow) drives its lifecycle: create, resume when
 * the page is visible, pause when hidden; the browser back button calls onBackPressed.
 */
public class Activity extends android.view.ContextThemeWrapper {
    public static final int RESULT_OK = -1, RESULT_CANCELED = 0, RESULT_FIRST_USER = 1;

    private final Window window = new Window(this, false);
    private Intent intent = new Intent(Intent.ACTION_MAIN);
    private boolean finishing;

    public Activity() {}

    public Window getWindow() { return window; }
    public Intent getIntent() { return intent; }
    public void setIntent(Intent newIntent) { intent = newIntent; }
    public android.view.WindowManager getWindowManager() { return null; }
    public final Activity getParent() { return null; }

    public void setContentView(View view) {
        window.setContentView(view);
        divan.web.WebWindow.setActivityContent(this, view);
    }

    public void setContentView(View view, android.view.ViewGroup.LayoutParams params) { setContentView(view); }

    public <T extends View> T findViewById(int id) { return window.findViewById(id); }

    protected void onCreate(Bundle savedInstanceState) {}
    protected void onStart() {}
    protected void onRestart() {}
    protected void onResume() {}
    protected void onPostResume() {}
    protected void onPause() {}
    protected void onStop() {}
    protected void onDestroy() {}
    protected void onNewIntent(Intent intent) {}
    protected void onSaveInstanceState(Bundle outState) {}
    protected void onRestoreInstanceState(Bundle savedInstanceState) {}
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {}
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {}
    public void onWindowFocusChanged(boolean hasFocus) {}
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {}
    public void onLowMemory() {}
    public void onTrimMemory(int level) {}

    @Deprecated
    public void onBackPressed() { finish(); }

    public void finish() {
        finishing = true;
        divan.web.WebWindow.activityFinished(this);
    }

    public boolean isFinishing() { return finishing; }
    public boolean isDestroyed() { return false; }
    public boolean isChangingConfigurations() { return false; }
    public boolean moveTaskToBack(boolean nonRoot) { return true; }
    public void recreate() { divan.web.WebWindow.reload(); }

    @Deprecated
    public void startActivityForResult(Intent intent, int requestCode) { divan.web.Intents.start(this, intent, requestCode); }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options) { startActivityForResult(intent, requestCode); }

    /** Result of a browser picker / permission prompt, delivered like Android does. */
    public void dispatchActivityResult(int requestCode, int resultCode, Intent data) { onActivityResult(requestCode, resultCode, data); }

    public final void requestPermissions(String[] permissions, int requestCode) { divan.web.Permissions.request(this, permissions, requestCode); }

    public boolean shouldShowRequestPermissionRationale(String permission) { return false; }

    public final void runOnUiThread(Runnable action) { action.run(); }

    public void setRequestedOrientation(int requestedOrientation) {}
    public void setTitle(CharSequence title) {}
    public void overridePendingTransition(int enterAnim, int exitAnim) {}
    public void setFinishOnTouchOutside(boolean finish) {}
    public void reportFullyDrawn() {}
}
