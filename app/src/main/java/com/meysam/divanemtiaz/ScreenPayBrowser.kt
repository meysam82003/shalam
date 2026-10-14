package com.meysam.divanemtiaz

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * The gateway's payment page inside the app (site version): no address bar and no outside browser, so the buyer
 * stays in Divan and never sees a link. Only the first page is checked (HTTPS, a domain the server listed for the
 * method); the gateway may then move through its own and the bank's pages. The page decides nothing: while it is
 * open the server is asked about the order every few seconds, and our return page or a divan:// link closes it.
 * When it closes, the Pro page asks the server once more and shows the result.
 */
class PayBrowserScreen(host: MainActivity, private val url: String, private val methodTitle: String) : Screen(host) {
    private var web: WebView? = null
    private var bar: View? = null
    private var closed = false
    private var polls = 0
    private val returnHost = runCatching { java.net.URI(StoreInfo.apiBase).host?.lowercase() }.getOrNull()

    override fun build(): View {
        val root = kit.vertical().apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val top = kit.horizontal().apply {
            setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(6))
            minimumHeight = kit.dp(54)
            setBackgroundColor(Royal.alpha(Royal.scrim, 0.86f))
            addView(kit.iconButton(RoyalIcon.CLOSE, "بستن صفحهٔ پرداخت", ButtonKind.SECONDARY, 42) { askClose() })
            addView(kit.hgap(10))
            addView(kit.vertical().apply {
                addView(kit.text("پرداخت امن", TextStyle.TITLE, Royal.title, maxLines = 1))
                addView(kit.text(methodTitle, TextStyle.CAPTION, Royal.muted, maxLines = 1))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(top)
        val line = FrameLayout(host).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(0, Royal.alpha(Royal.gold, 0.55f), 0)
            )
        }
        // Grows from the right, as the page reads.
        val progress = View(host).apply {
            setBackgroundColor(Royal.gold); scaleX = 0.1f
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.pivotX = v.width.toFloat() }
        }
        line.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        bar = progress
        root.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(2)))
        root.addView(webView(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    /** One page for the whole visit: a rebuild (theme, font size) moves the same page into the new layout. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun webView(): WebView {
        web?.let { (it.parent as? ViewGroup)?.removeView(it); return it }
        val w = WebView(host)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            @Suppress("DEPRECATION")
            saveFormData = false
            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }
        w.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = route(url)

            @TargetApi(24)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                if (request.isForMainFrame) route(request.url.toString()) else false

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (isReturn(url)) finish()
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                bar?.apply { scaleX = (newProgress / 100f).coerceIn(0.05f, 1f); alpha = if (newProgress >= 100) 0f else 1f }
            }
        }
        // The gateway's receipt image: handed to the phone (gallery or browser) only when the buyer asks for it.
        w.setDownloadListener { u, _, _, _, _ -> openOutside(u) }
        w.setBackgroundColor(Royal.deep)
        w.loadUrl(url)
        web = w
        return w
    }

    /** true = handled here (not loaded in the page). */
    private fun route(u: String): Boolean {
        val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return true
        return when (uri.scheme?.lowercase()) {
            "divan" -> { finish(); true }
            "https" -> if (isReturn(u)) { finish(); true } else false
            // Never a plain-HTTP, local or script page inside the payment view.
            "http", "file", "content", "javascript", "data", "about" -> true
            // Bank apps, a phone number or an intent link of the gateway page.
            else -> { openOutside(u); true }
        }
    }

    /** Our own «back to the app» page (pay/return.php on the Divan server). */
    private fun isReturn(u: String): Boolean {
        val uri = runCatching { java.net.URI(u) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host?.lowercase() == returnHost && (uri.path ?: "").endsWith("/pay/return.php")
    }

    private fun openOutside(u: String) {
        val intent = runCatching {
            if (u.startsWith("intent:")) Intent.parseUri(u, Intent.URI_INTENT_SCHEME).apply { component = null; selector = null; addCategory(Intent.CATEGORY_BROWSABLE) }
            else Intent(Intent.ACTION_VIEW, Uri.parse(u)).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        }.getOrNull()
        if (intent == null || runCatching { host.startActivity(intent) }.isFailure) kit.toast("برنامه‌ای برای باز کردن این مورد پیدا نشد.")
    }

    override fun onShow() { poll() }

    /** The server asks the gateway; often at first, then calmer (the server limits how often it asks the gateway). */
    private fun poll() {
        if (closed || host.current !== this) return
        val order = ProState.pendingOrder ?: return
        host.post(if (polls < 12) 6000L else 20000L) {
            if (closed || host.current !== this) return@post
            polls++
            ProSync.payStatus(order) { status, _ ->
                when (status) {
                    "paid" -> finish()
                    "failed", "expired" -> Unit
                    else -> poll()
                }
            }
        }
    }

    override fun onBack(): Boolean {
        val w = web
        if (w != null && w.canGoBack()) { w.goBack(); return true }
        askClose()
        return true
    }

    private fun askClose() {
        kit.dialog("بستن صفحهٔ پرداخت", "اگر واریز را انجام داده‌اید، نگران نباشید: پرداخت از سمت سرور بررسی و Pro خودکار فعال می‌شود.", null, listOf(
            DialogAction("بستن", ButtonKind.PRIMARY) { finish() },
            DialogAction("ادامهٔ پرداخت")
        )).show()
    }

    /** Back to the Pro page, which asks the server once more and shows the result. */
    private fun finish() {
        if (closed) return
        closed = true
        host.post(0) {
            if (host.current === this) host.pop()
            web?.apply { stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() }
            web = null
        }
    }
}
