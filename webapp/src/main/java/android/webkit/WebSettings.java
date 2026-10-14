package android.webkit;

/** Settings of the payment view; the browser window opened for it has its own, so these are only remembered. */
public class WebSettings {
    public static final int MIXED_CONTENT_ALWAYS_ALLOW = 0, MIXED_CONTENT_NEVER_ALLOW = 1, MIXED_CONTENT_COMPATIBILITY_MODE = 2;
    private boolean js, dom, file = true, content = true, multi, autoWin, geo = true, overview, wide, zoom, form = true, safe = true;
    private int mixed = MIXED_CONTENT_NEVER_ALLOW;

    public void setJavaScriptEnabled(boolean v) { js = v; }
    public boolean getJavaScriptEnabled() { return js; }
    public void setDomStorageEnabled(boolean v) { dom = v; }
    public boolean getDomStorageEnabled() { return dom; }
    public void setAllowFileAccess(boolean v) { file = v; }
    public boolean getAllowFileAccess() { return file; }
    public void setAllowContentAccess(boolean v) { content = v; }
    public boolean getAllowContentAccess() { return content; }
    public void setSupportMultipleWindows(boolean v) { multi = v; }
    public boolean supportMultipleWindows() { return multi; }
    public void setJavaScriptCanOpenWindowsAutomatically(boolean v) { autoWin = v; }
    public boolean getJavaScriptCanOpenWindowsAutomatically() { return autoWin; }
    public void setGeolocationEnabled(boolean v) { geo = v; }
    public void setMixedContentMode(int v) { mixed = v; }
    public int getMixedContentMode() { return mixed; }
    public void setLoadWithOverviewMode(boolean v) { overview = v; }
    public boolean getLoadWithOverviewMode() { return overview; }
    public void setUseWideViewPort(boolean v) { wide = v; }
    public boolean getUseWideViewPort() { return wide; }
    public void setBuiltInZoomControls(boolean v) { zoom = v; }
    public boolean getBuiltInZoomControls() { return zoom; }
    @Deprecated public void setSaveFormData(boolean v) { form = v; }
    @Deprecated public boolean getSaveFormData() { return form; }
    public void setSafeBrowsingEnabled(boolean v) { safe = v; }
    public boolean getSafeBrowsingEnabled() { return safe; }
}
