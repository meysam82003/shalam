package com.meysam.divanemtiaz

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.VideoView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/*
 * Ads for free users only, in safe places only (home, history, finished results, rankings, settings, game hubs;
 * never on boards, timers, mafia night/vote, role reveal or score entry), always labelled «تبلیغات».
 * Pro users get nothing: no ad is chosen, no image is downloaded and the ad network is never started.
 */

/** One direct ad from the panel. */
data class DirectAd(
    val id: Int, val format: String, val title: String, val body: String, val mediaUrl: String?, val mediaKind: String?, val mediaSha: String?,
    val target: String?, val cta: String?, val bg: Int, val fg: Int, val accent: Int, val closable: Boolean, val dailyCap: Int?,
    val minIntervalSec: Int, val priority: Int, val placements: List<String>, val endsAt: Long?
) {
    companion object {
        private fun color(s: String?, d: Int): Int = runCatching { if (s != null && s.startsWith("#")) Color.parseColor(s) else d }.getOrDefault(d)

        fun from(j: JSONObject): DirectAd {
            val m = j.optJSONObject("media")
            val st = j.optJSONObject("style") ?: JSONObject()
            val pl = j.optJSONArray("placements") ?: JSONArray()
            return DirectAd(
                j.optInt("id"), j.optString("format"), j.optString("title").takeIf { it != "null" }.orEmpty(), j.optString("body").takeIf { it != "null" }.orEmpty(),
                m?.optString("url"), m?.optString("kind"), m?.optString("sha256"),
                j.optString("target_url").takeIf { it.startsWith("http") || it.startsWith("divan://") }, j.optString("cta").takeIf { it.isNotBlank() && it != "null" },
                color(st.optString("bg", null), 0xFF1C2A4A.toInt()), color(st.optString("fg", null), Color.WHITE), color(st.optString("accent", null), 0xFFC39A4A.toInt()),
                j.optBoolean("closable", true), if (j.isNull("daily_cap")) null else j.optInt("daily_cap"), j.optInt("min_interval_sec"), j.optInt("priority"),
                (0 until pl.length()).map { pl.optString(it) }, if (j.isNull("ends_at")) null else j.optLong("ends_at")
            )
        }
    }
}

/** Frequency rules, kept pure for the unit tests. */
object AdRules {
    class Seen(val day: Long, val count: Int, val last: Long)

    fun eligible(ad: DirectAd, placement: String, slotFormats: List<String>, now: Long, seen: Seen?): Boolean {
        if (placement !in ad.placements || ad.format !in slotFormats) return false
        if (ad.endsAt != null && ad.endsAt <= now) return false
        val today = now / 86400
        if (seen != null && seen.day == today && ad.dailyCap != null && seen.count >= ad.dailyCap) return false
        if (seen != null && ad.minIntervalSec > 0 && now - seen.last < ad.minIntervalSec) return false
        return true
    }

    fun interstitialAllowed(now: Long, last: Long, todayCount: Int, lastDay: Long, minMinutes: Int, dailyCap: Int): Boolean {
        if (dailyCap <= 0) return false
        if (now - last < minMinutes * 60L) return false
        return !(lastDay == now / 86400 && todayCount >= dailyCap)
    }
}

/** Ad network bridge; only builds made with a Tapsell app key contain an implementation. */
interface NetworkAds {
    fun init(activity: Activity, appId: String, test: Boolean)
    fun banner(activity: Activity, zone: String, container: FrameLayout, done: (Boolean) -> Unit)
    fun interstitial(activity: Activity, zone: String, done: (Boolean) -> Unit)
    fun rewarded(activity: Activity, zone: String, done: (Boolean) -> Unit)
    fun destroy(container: FrameLayout) {}
}

object AdEngine {
    private val MEDIA_FORMATS = setOf("image", "video", "corner", "interstitial", "rewarded")
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val downloading = HashSet<String>()
    private val events = JSONArray()
    private var networkStarted = false

    private val network: NetworkAds? by lazy {
        if (StoreInfo.tapsellAppId.isBlank()) null
        else runCatching { Class.forName("com.meysam.divanemtiaz.TapsellBridge").getDeclaredConstructor().newInstance() as NetworkAds }.getOrNull()
    }

    private fun cfg(): JSONObject? = if (!ProState.adsAllowed) null else ProState.config.optJSONObject("ads")?.takeIf { it.optBoolean("enabled") }

    fun enabled(): Boolean = cfg() != null

    private fun slotFormats(c: JSONObject, key: String, network: Boolean): List<String>? {
        val a = c.optJSONArray("placements") ?: return null
        for (i in 0 until a.length()) {
            val p = a.getJSONObject(i)
            if (p.optString("key") == key) {
                if (!p.optBoolean("active", true)) return null
                val f = p.optJSONArray(if (network) "network_formats" else "formats") ?: return emptyList()
                return (0 until f.length()).map { f.optString(it) }
            }
        }
        return null
    }

    private fun ads(c: JSONObject): List<DirectAd> = c.optJSONArray("ads")?.let { a -> (0 until a.length()).mapNotNull { runCatching { DirectAd.from(a.getJSONObject(it)) }.getOrNull() } }.orEmpty()

    private val stats: JSONObject by lazy { runCatching { JSONObject(prefs?.getString("ad_seen", "{}") ?: "{}") }.getOrDefault(JSONObject()) }
    private var prefs: android.content.SharedPreferences? = null
    private var cacheDir: File? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("divan_ads", Context.MODE_PRIVATE)
        cacheDir = File(context.cacheDir, "ads").apply { mkdirs() }
    }

    private fun seen(id: Int): AdRules.Seen? = stats.optJSONArray(id.toString())?.let { AdRules.Seen(it.optLong(0), it.optInt(1), it.optLong(2)) }

    private fun markShown(id: Int) {
        val now = ProState.now()
        val s = seen(id)
        val day = now / 86400
        stats.put(id.toString(), JSONArray().put(day).put(if (s != null && s.day == day) s.count + 1 else 1).put(now))
        prefs?.edit()?.putString("ad_seen", stats.toString())?.apply()
    }

    // ---------------------------------------------------------------- events
    fun event(event: String, placement: String, adId: Int = 0, network: Boolean = false) {
        if (!ProState.adsAllowed) return
        synchronized(events) {
            events.put(JSONObject().put("event", event).put("placement", placement).put("ad_id", adId).put("source", if (network) "network" else "direct")
                .put("day", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ProState.now() * 1000))))
        }
        if (events.length() >= 20) flush()
    }

    fun flush() {
        val batch = synchronized(events) {
            if (events.length() == 0) return
            val copy = JSONArray(events.toString())
            while (events.length() > 0) events.remove(0)
            copy
        }
        Api.call("POST", "v1/ads/events", JSONObject().put("events", batch), null) { _, _ -> }
    }

    // ---------------------------------------------------------------- media cache
    private fun cached(ad: DirectAd): File? {
        val sha = ad.mediaSha ?: return null
        return File(cacheDir ?: return null, sha).takeIf { it.isFile && it.length() > 0 }
    }

    private fun download(ad: DirectAd) {
        val url = ad.mediaUrl ?: return
        val sha = ad.mediaSha ?: return
        val dir = cacheDir ?: return
        synchronized(downloading) { if (!downloading.add(sha)) return }
        io.execute {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                Tls.apply(c)
                c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = false
                if (c.responseCode != 200) return@runCatching
                val tmp = File(dir, "$sha.part")
                val md = MessageDigest.getInstance("SHA-256")
                var total = 0L
                c.inputStream.use { input -> tmp.outputStream().use { out ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > 16L * 1024 * 1024) throw IllegalStateException("too large")
                        md.update(buf, 0, n); out.write(buf, 0, n)
                    }
                } }
                c.disconnect()
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (hex == sha) { tmp.renameTo(File(dir, sha)); main.post { ProState.notifyChanged() } } else tmp.delete()
                // Old files are dropped so the cache stays small.
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(30)?.forEach { it.delete() }
            }
            synchronized(downloading) { downloading.remove(sha) }
        }
    }

    private fun bitmap(file: File, maxWidth: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        var sample = 1
        while (o.outWidth / sample > maxWidth * 2) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Picks a direct ad whose media is ready (or needs none); starts downloads for the others. */
    private fun pick(c: JSONObject, key: String, formats: List<String>): DirectAd? {
        val now = ProState.now()
        val candidates = ads(c).filter { AdRules.eligible(it, key, formats, now, seen(it.id)) }.sortedByDescending { it.priority }
        for (ad in candidates) {
            if (ad.mediaUrl != null && cached(ad) == null) {
                download(ad)
                // Text formats can show without their picture; the others wait for the download.
                if (ad.format in MEDIA_FORMATS) continue
            }
            return ad
        }
        return null
    }

    // ---------------------------------------------------------------- inline slots
    /** A ready ad for [key], or null (Pro, ads off, nothing to show). Never blocks. */
    fun slot(screen: Screen, key: String): View? {
        val c = cfg() ?: return null
        val priority = c.optString("priority", "direct_first")
        val direct = if (priority != "network_only") slotFormats(c, key, false)?.let { pick(c, key, it) } else null
        if (direct != null && priority != "network_first") return render(screen, key, direct)
        val zone = c.optJSONObject("network")?.takeIf { it.optBoolean("enabled") }?.optJSONObject("zones")?.optString("banner").orEmpty()
        val netFormats = slotFormats(c, key, true).orEmpty()
        if (priority != "direct_only" && zone.isNotBlank() && "banner" in netFormats && network != null) {
            return networkBanner(screen, key, zone) { direct?.let { render(screen, key, it) } }
        }
        return direct?.let { render(screen, key, it) }
    }

    private fun startNetwork(activity: Activity): NetworkAds? {
        if (!ProState.adsAllowed) return null
        val n = network ?: return null
        if (!networkStarted) {
            val test = ProState.config.optJSONObject("ads")?.optJSONObject("network")?.optBoolean("test") ?: false
            runCatching { n.init(activity, StoreInfo.tapsellAppId, test) }.onFailure { return null }
            networkStarted = true
        }
        return n
    }

    private fun networkBanner(screen: Screen, key: String, zone: String, fallback: () -> View?): View {
        val kit = screen.kit
        val box = FrameLayout(screen.host)
        val wrap = labelled(screen, box, closable = false) {}
        event("request", key, network = true)
        val n = startNetwork(screen.host)
        if (n == null) { fallback()?.let { box.addView(it) } ?: run { wrap.visibility = View.GONE }; return wrap }
        n.banner(screen.host, zone, box) { ok ->
            if (ok) { event("fill", key, network = true); event("impression", key, network = true) }
            else {
                event("error", key, network = true)
                val f = fallback()
                if (f != null) box.addView(f) else wrap.visibility = View.GONE
            }
        }
        wrap.minimumHeight = kit.dp(56)
        return wrap
    }

    /** Frame with the «تبلیغات» label and an optional close button. */
    private fun labelled(screen: Screen, content: View, closable: Boolean, onClose: () -> Unit): LinearLayout {
        val kit = screen.kit
        return kit.vertical().apply {
            layoutParams = kit.spaced(12)
            addView(kit.horizontal().apply {
                addView(kit.badge("تبلیغات", Royal.dim))
                addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                if (closable) addView(kit.text("✕", TextStyle.BODY_BOLD, Royal.dim, Gravity.CENTER).apply {
                    setPadding(kit.dp(10), 0, kit.dp(4), 0)
                    contentDescription = "بستن تبلیغ"
                    setOnClickListener { this@apply.let { (parent?.parent as? View)?.visibility = View.GONE }; onClose() }
                })
            }, kit.spaced(4))
            addView(content, kit.fill())
        }
    }

    /** Opens one of the allowed in-app destinations (ads and notifications); anything unknown opens the Pro page. */
    fun openInApp(host: MainActivity, url: String) {
        val path = url.removePrefix("divan://").trim('/')
        when {
            path == "pro" -> host.push(ProScreen(host))
            path.startsWith("plan/") -> host.push(ProScreen(host, focusPlan = path.removePrefix("plan/")))
            path == "subscription" -> host.push(SubscriptionScreen(host))
            path == "rewarded" -> host.current?.let { screen -> showRewarded(screen) { ok -> screen.kit.toast(if (ok) "تا مدتی تبلیغی نمایش داده نمی‌شود" else "ویدئو کامل نشد") } }
            path == "screen/mafia" -> host.push(MafiaHomeScreen(host))
            path == "screen/panto" -> host.push(PantoHomeScreen(host))
            path == "screen/spy" -> host.push(SpyHomeScreen(host))
            path == "screen/leagues" -> host.push(LeagueListScreen(host))
            path == "screen/themes" -> host.push(ThemeScreen(host))
            path == "screen/settings" -> host.push(SettingsScreen(host))
            path == "" || path == "home" -> {}
            else -> host.push(ProScreen(host))
        }
    }

    private fun open(screen: Screen, ad: DirectAd, key: String) {
        event("click", key, ad.id)
        val url = ad.target ?: return
        val host = screen.host
        // In-app destinations chosen in the panel: divan://pro, divan://plan/m3, divan://subscription, divan://screen/…
        if (url.startsWith("divan://")) { openInApp(host, url); return }
        runCatching { host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun render(screen: Screen, key: String, ad: DirectAd): View {
        val kit = screen.kit
        val ctx = screen.host
        val width = ctx.resources.displayMetrics.widthPixels
        val file = cached(ad)
        val card = kit.vertical().apply {
            background = GradientDrawable().apply { cornerRadius = kit.dpf(16f); setColor(ad.bg) }
            setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(12))
            isClickable = ad.target != null
            if (ad.target != null) setOnClickListener { kit.tap(it); open(screen, ad, key) }
            contentDescription = "تبلیغ: ${ad.title}"
        }
        fun mediaView(maxH: Int): View? {
            val f = file ?: return null
            return if (ad.mediaKind == "video") VideoView(ctx).apply {
                setVideoPath(f.path)
                setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(maxH))
            } else bitmap(f, width)?.let { bm ->
                ImageView(ctx).apply {
                    setImageBitmap(bm); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                    maxHeight = kit.dp(maxH)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
        }
        fun ctaView(): View? = ad.cta?.let { cta ->
            kit.text(cta, TextStyle.LABEL_BOLD, Color.BLACK, Gravity.CENTER, 1).apply {
                background = GradientDrawable().apply { cornerRadius = kit.dpf(10f); setColor(ad.accent) }
                setPadding(kit.dp(14), kit.dp(5), kit.dp(14), kit.dp(6))
            }
        }
        when (ad.format) {
            "banner_small", "text" -> {
                if (ad.title.isNotBlank()) card.addView(kit.text(ad.title, TextStyle.BODY_BOLD, ad.fg, maxLines = 2))
                if (ad.body.isNotBlank() && ad.format == "text") card.addView(kit.text(ad.body, TextStyle.LABEL, ad.fg, maxLines = 3))
                ctaView()?.let { card.addView(kit.gap(6)); card.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
            }
            "native" -> card.addView(kit.horizontal().apply {
                file?.let { f -> bitmap(f, kit.dp(56))?.let { bm -> addView(ImageView(ctx).apply { setImageBitmap(bm); scaleType = ImageView.ScaleType.CENTER_CROP }, LinearLayout.LayoutParams(kit.dp(52), kit.dp(52))); addView(kit.hgap(10)) } }
                addView(kit.vertical().apply {
                    addView(kit.text(ad.title, TextStyle.BODY_BOLD, ad.fg, maxLines = 1))
                    if (ad.body.isNotBlank()) addView(kit.text(ad.body, TextStyle.CAPTION, ad.fg, maxLines = 2))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                ctaView()?.let { addView(kit.hgap(8)); addView(it) }
            })
            "sponsor" -> {
                card.gravity = Gravity.CENTER_HORIZONTAL
                card.addView(kit.text("با حمایت", TextStyle.CAPTION, ad.fg, Gravity.CENTER))
                card.addView(kit.text(ad.title, TextStyle.HEADING, ad.fg, Gravity.CENTER))
                mediaView(60)?.let { card.addView(it) }
            }
            else -> {
                mediaView(if (ad.format == "corner") 110 else 200)?.let { card.addView(it); card.addView(kit.gap(8)) }
                if (ad.title.isNotBlank()) card.addView(kit.text(ad.title, TextStyle.BODY_BOLD, ad.fg, maxLines = 2))
                if (ad.body.isNotBlank()) card.addView(kit.text(ad.body, TextStyle.LABEL, ad.fg, maxLines = 3))
                ctaView()?.let { card.addView(kit.gap(6)); card.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
            }
        }
        val frame = labelled(screen, card, ad.closable) { event("close", key, ad.id) }
        if (ad.format == "corner") frame.layoutParams = LinearLayout.LayoutParams(kit.dp(150), ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START; bottomMargin = kit.dp(12) }
        frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            var counted = false
            override fun onViewAttachedToWindow(v: View) { if (!counted) { counted = true; markShown(ad.id); event("impression", key, ad.id) } }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        return frame
    }

    // ---------------------------------------------------------------- fixed top / bottom bar
    private val dismissed = HashSet<String>()
    private val stickyShown = HashMap<String, String>()

    /**
     * Fixed bar for [key] (sticky_top / sticky_bottom) on pages whose zone the panel allows. The bar is laid out
     * outside the page, so it never covers content, and the same ad is kept in place while pages change.
     */
    fun sticky(screen: Screen?, key: String, bar: FrameLayout) {
        val zone = screen?.adZone
        val c = cfg()
        val p = c?.optJSONArray("placements")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("key") == key } }
        val zones = p?.optJSONArray("screens")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        if (screen == null || c == null || zone == null || p == null || !p.optBoolean("active") || zone !in zones || key in dismissed) {
            bar.removeAllViews(); bar.visibility = View.GONE; stickyShown.remove(key); return
        }
        val formats = (p.optJSONArray("formats") ?: JSONArray()).let { f -> (0 until f.length()).map { f.optString(it) } }
        val ad = if (c.optString("priority") != "network_only") ads(c).filter { AdRules.eligible(it, key, formats, ProState.now(), null) }
            .sortedByDescending { it.priority }.firstOrNull { it.mediaUrl == null || cached(it) != null || run { download(it); it.format !in MEDIA_FORMATS } } else null
        val tag = ad?.let { "ad:${it.id}" } ?: "network"
        if (stickyShown[key] == tag && bar.childCount > 0) { bar.visibility = View.VISIBLE; return }
        bar.removeAllViews()
        val kit = screen.kit
        val strip = if (ad != null) stickyStrip(screen, key, ad, bar) else {
            val zoneId = c.optJSONObject("network")?.takeIf { it.optBoolean("enabled") }?.optJSONObject("zones")?.optString("banner").orEmpty()
            if (zoneId.isBlank() || network == null || c.optString("priority") == "direct_only") null
            else FrameLayout(screen.host).also { box -> startNetwork(screen.host)?.banner(screen.host, zoneId, box) { ok -> event(if (ok) "impression" else "error", key, network = true); if (!ok) bar.visibility = View.GONE } }
        }
        if (strip == null) { bar.visibility = View.GONE; stickyShown.remove(key); return }
        bar.setPadding(kit.dp(8), kit.dp(4), kit.dp(8), kit.dp(4))
        bar.addView(strip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.visibility = View.VISIBLE
        stickyShown[key] = tag
        if (ad != null) { markShown(ad.id); event("impression", key, ad.id) }
    }

    private fun stickyStrip(screen: Screen, key: String, ad: DirectAd, bar: FrameLayout): View {
        val kit = screen.kit
        return kit.horizontal().apply {
            background = GradientDrawable().apply { cornerRadius = kit.dpf(14f); setColor(ad.bg); setStroke(kit.dp(1), Royal.alpha(ad.accent, 0.5f)) }
            setPadding(kit.dp(8), kit.dp(5), kit.dp(8), kit.dp(5))
            minimumHeight = kit.dp(50)
            contentDescription = "تبلیغ: ${ad.title}"
            if (ad.target != null) { isClickable = true; setOnClickListener { kit.tap(it); open(screen, ad, key) } }
            addView(kit.badge("تبلیغات", ad.fg))
            addView(kit.hgap(8))
            cached(ad)?.takeIf { ad.mediaKind != "video" }?.let { f -> bitmap(f, kit.dp(200))?.let { bm ->
                addView(ImageView(context).apply { setImageBitmap(bm); scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true },
                    LinearLayout.LayoutParams(if (ad.format == "image" || ad.format == "banner_wide") 0 else kit.dp(40), kit.dp(40), if (ad.format == "image" || ad.format == "banner_wide") 1f else 0f))
                addView(kit.hgap(8))
            } }
            if (ad.format != "image" && ad.format != "banner_wide" || cached(ad) == null) addView(kit.vertical().apply {
                addView(kit.text(ad.title, TextStyle.LABEL_BOLD, ad.fg, maxLines = 1))
                if (ad.body.isNotBlank()) addView(kit.text(ad.body, TextStyle.CAPTION, ad.fg, maxLines = 1))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            ad.cta?.let { cta ->
                addView(kit.hgap(6))
                addView(kit.text(cta, TextStyle.CAPTION, Color.BLACK, Gravity.CENTER, 1).apply {
                    background = GradientDrawable().apply { cornerRadius = kit.dpf(9f); setColor(ad.accent) }
                    setPadding(kit.dp(10), kit.dp(3), kit.dp(10), kit.dp(4))
                })
            }
            if (ad.closable) addView(kit.text("✕", TextStyle.BODY_BOLD, ad.fg, Gravity.CENTER).apply {
                setPadding(kit.dp(10), 0, kit.dp(2), 0)
                contentDescription = "بستن تبلیغ"
                setOnClickListener { dismissed += key; bar.removeAllViews(); bar.visibility = View.GONE; stickyShown.remove(key); event("close", key, ad.id) }
            })
        }
    }

    // ---------------------------------------------------------------- full-screen
    /** Between two activities (after closing a finished result), within the panel's caps. */
    fun maybeInterstitial(screen: Screen) {
        val c = cfg() ?: return
        val key = "between_games"
        val formats = slotFormats(c, key, false) ?: return
        val p = prefs ?: return
        val now = ProState.now()
        if (!AdRules.interstitialAllowed(now, p.getLong("int_last", 0), p.getInt("int_count", 0), p.getLong("int_day", -1),
                c.optInt("interstitial_min_minutes", 10), c.optInt("interstitial_daily_cap", 3))) return
        val day = now / 86400
        fun mark() { p.edit().putLong("int_last", now).putInt("int_count", if (p.getLong("int_day", -1) == day) p.getInt("int_count", 0) + 1 else 1).putLong("int_day", day).apply() }
        val direct = if (c.optString("priority") != "network_only") pick(c, key, formats) else null
        if (direct != null) { mark(); fullScreen(screen, key, direct, rewarded = false) {}; return }
        val zone = c.optJSONObject("network")?.takeIf { it.optBoolean("enabled") }?.optJSONObject("zones")?.optString("interstitial").orEmpty()
        if (zone.isNotBlank() && c.optString("priority") != "direct_only") {
            val n = startNetwork(screen.host) ?: return
            mark(); event("request", key, network = true)
            n.interstitial(screen.host, zone) { ok -> event(if (ok) "impression" else "error", key, network = true) }
        }
    }

    /** Optional video the player chooses to watch for an ad-free hour. */
    fun rewardedAvailable(): Boolean {
        val c = cfg() ?: return false
        val direct = slotFormats(c, "rewarded_offer", false)?.let { pick(c, "rewarded_offer", it) }
        val zone = c.optJSONObject("network")?.takeIf { it.optBoolean("enabled") }?.optJSONObject("zones")?.optString("rewarded").orEmpty()
        return direct != null || (zone.isNotBlank() && network != null)
    }

    fun showRewarded(screen: Screen, done: (Boolean) -> Unit) {
        val c = cfg() ?: return done(false)
        val minutes = c.optInt("rewarded_ad_free_minutes", 60)
        val grant = { ok: Boolean ->
            if (ok) { ProState.adFreeUntil = ProState.now() + minutes * 60L; event("reward", "rewarded_offer"); ProState.notifyChanged() }
            done(ok)
        }
        val direct = slotFormats(c, "rewarded_offer", false)?.let { pick(c, "rewarded_offer", it) }
        if (direct != null) { fullScreen(screen, "rewarded_offer", direct, rewarded = true, grant); return }
        val zone = c.optJSONObject("network")?.optJSONObject("zones")?.optString("rewarded").orEmpty()
        val n = startNetwork(screen.host) ?: return done(false)
        event("request", "rewarded_offer", network = true)
        n.rewarded(screen.host, zone) { ok -> if (ok) event("impression", "rewarded_offer", network = true); grant(ok) }
    }

    private fun fullScreen(screen: Screen, key: String, ad: DirectAd, rewarded: Boolean, done: (Boolean) -> Unit) {
        val kit = screen.kit
        val ctx = screen.host
        val d = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var finished = !rewarded
        val closeBtn = kit.button(if (rewarded) "صبر کنید…" else "بستن", ButtonKind.SECONDARY, RoyalIcon.CLOSE, 42) {
            if (rewarded && !finished) return@button
            d.dismiss()
        }
        val root = kit.vertical(Gravity.CENTER).apply {
            setBackgroundColor(ad.bg)
            setPadding(kit.dp(18), kit.dp(18), kit.dp(18), kit.dp(18))
            addView(kit.horizontal().apply {
                addView(kit.badge("تبلیغات", Royal.goldLight, filled = true))
                addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
                addView(closeBtn)
            }, kit.spaced(12))
            val f = cached(ad)
            if (f != null && ad.mediaKind == "video") addView(VideoView(ctx).apply {
                setVideoPath(f.path)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { finished = true; (closeBtn.getChildAt(closeBtn.childCount - 1) as? android.widget.TextView)?.text = "بستن" }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            else if (f != null) bitmap(f, ctx.resources.displayMetrics.widthPixels)?.let { bm ->
                addView(ImageView(ctx).apply { setImageBitmap(bm); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
            if (ad.title.isNotBlank()) addView(kit.text(ad.title, TextStyle.TITLE, ad.fg, Gravity.CENTER), kit.spaced(6))
            if (ad.body.isNotBlank()) addView(kit.text(ad.body, TextStyle.BODY, ad.fg, Gravity.CENTER), kit.spaced(10))
            if (ad.cta != null && ad.target != null) addView(kit.button(ad.cta, ButtonKind.PRIMARY, null, 48) { open(screen, ad, key) })
        }
        if (rewarded && cached(ad)?.let { ad.mediaKind != "video" } != false) {
            // An image "video" counts after a short view.
            main.postDelayed({ finished = true; (closeBtn.getChildAt(closeBtn.childCount - 1) as? android.widget.TextView)?.text = "بستن" }, 8000)
        }
        d.setContentView(root)
        d.setOnDismissListener { done(finished); if (!finished) event("close", key, ad.id) }
        markShown(ad.id); event("impression", key, ad.id)
        d.show()
    }
}
