package android.webkit;

public class WebViewClient {
    @Deprecated public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return shouldOverrideUrlLoading(view, request.getUrl().toString()); }
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {}
    public void onPageFinished(WebView view, String url) {}
}
