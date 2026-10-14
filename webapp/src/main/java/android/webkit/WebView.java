package android.webkit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import divan.web.JS;
import divan.web.PayWindow;

/**
 * The payment view of the web, Windows and iPhone versions. A gateway page cannot be shown inside another site's
 * page (gateways forbid framing), so it opens in its own secure window, without the app around it; this view shows
 * a short royal note and a button to bring that window back. Divan's result page in that window sends «back to
 * Divan» here (window message), which reaches the screen as the same divan:// link the Android view would see.
 */
public class WebView extends View {
    private final WebSettings settings = new WebSettings();
    private WebViewClient client = new WebViewClient();
    private WebChromeClient chrome = new WebChromeClient();
    private String url;
    private boolean destroyed;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF button = new RectF();
    private final PayWindow.Listener listener = href -> {
        if (destroyed) return;
        if (!client.shouldOverrideUrlLoading(this, request(href))) loadUrl(href);
    };

    public WebView(Context context) {
        super(context);
        setClickable(true);
        PayWindow.listen(listener);
    }

    public WebSettings getSettings() { return settings; }
    public WebViewClient getWebViewClient() { return client; }
    public WebChromeClient getWebChromeClient() { return chrome; }
    public void setWebViewClient(WebViewClient c) { client = c != null ? c : new WebViewClient(); }
    public void setWebChromeClient(WebChromeClient c) { chrome = c != null ? c : new WebChromeClient(); }
    public void setDownloadListener(DownloadListener l) {}
    public String getUrl() { return url; }
    public boolean canGoBack() { return false; }
    public void goBack() {}
    public void stopLoading() {}

    public void loadUrl(String u) {
        if (destroyed || u == null) return;
        url = u;
        client.onPageStarted(this, u, null);
        if (destroyed) return;
        // Opened now when the browser allows it (shortly after the buyer's tap); otherwise by the button below.
        PayWindow.open(u);
        chrome.onProgressChanged(this, 100);
        client.onPageFinished(this, u);
        invalidate();
    }

    public void destroy() {
        destroyed = true;
        PayWindow.unlisten(listener);
        PayWindow.close();
    }

    private static WebResourceRequest request(String href) {
        android.net.Uri uri = android.net.Uri.parse(href);
        return new WebResourceRequest() {
            @Override public android.net.Uri getUrl() { return uri; }
            @Override public boolean isForMainFrame() { return true; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return true; }
            @Override public String getMethod() { return "GET"; }
        };
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight(), cx = w / 2f;
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xFFFBE3A6);
        paint.setTextSize(18 * d);
        canvas.drawText("صفحهٔ امن پرداخت در پنجرهٔ جدا باز است", cx, h * 0.36f, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFFB3C3DD);
        paint.setTextSize(14 * d);
        canvas.drawText("پس از پرداخت، نتیجه خودکار همین‌جا نمایش داده می‌شود.", cx, h * 0.36f + 30 * d, paint);
        float bw = Math.min(w - 48 * d, 300 * d), bh = 52 * d, top = h * 0.36f + 60 * d;
        button.set(cx - bw / 2f, top, cx + bw / 2f, top + bh);
        paint.setShader(new android.graphics.LinearGradient(button.left, 0, button.right, 0, new int[] {0xFFB1832F, 0xFFE6BE6A, 0xFFFBE3A6}, null, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRoundRect(button, bh / 2f, bh / 2f, paint);
        paint.setShader(null);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setColor(0xFF1A1206);
        paint.setTextSize(16 * d);
        canvas.drawText(PayWindow.isOpen() ? "نمایش صفحهٔ پرداخت" : "باز کردن صفحهٔ پرداخت", cx, top + bh / 2f + 6 * d, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && url != null && button.contains(e.getX(), e.getY())) {
            // Inside the tap itself, so the browser never blocks the window.
            if (!PayWindow.focus()) PayWindow.open(url);
            invalidate();
            return true;
        }
        return super.onTouchEvent(e) || e.getAction() == MotionEvent.ACTION_DOWN;
    }
}
