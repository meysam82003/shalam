package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.Executors

/*
 * Pro subscription core: which store this build sells through, the account on the server,
 * the server-signed certificate that keeps Pro working offline, and the purchase flow.
 *
 * Everything here is optional for the player: without an account the app works exactly as before
 * (all modules free unless the server says otherwise, no ads unless enabled). Nothing in the games
 * depends on the network.
 */

// ================================================================ store of this build
/** Values written into the manifest by the build flavor (bazaar / myket). */
object StoreInfo {
    const val DEFAULT_API = "https://httpuser87890.ir/divan/api/index.php"

    var storeId: String = "bazaar"; private set
    var apiBase: String = DEFAULT_API; private set
    var pinnedKey: String = ""; private set
    var myketKey: String = ""; private set
    var tapsellAppId: String = ""; private set
    private var loaded = false

    val storeName: String get() = if (storeId == "myket") "مایکت" else "کافه‌بازار"

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val meta = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        }.getOrNull() ?: return
        meta.getString("divan.store")?.takeIf { it == "bazaar" || it == "myket" }?.let { storeId = it }
        meta.getString("divan.api")?.takeIf { it.startsWith("https://") }?.let { apiBase = it }
        pinnedKey = meta.getString("divan.licenseKey").orEmpty().trim()
        myketKey = meta.getString("divan.myketKey").orEmpty().trim()
        tapsellAppId = meta.getString("divan.tapsellAppId").orEmpty().trim()
    }

    /** Tests choose the store and server directly. */
    fun configure(store: String, api: String = DEFAULT_API) { storeId = store; apiBase = api; loaded = true }
}

// ================================================================ base64url and the certificate (pure, unit-tested)
object B64 {
    private const val STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val INV = IntArray(128) { -1 }.also { t ->
        STD.forEachIndexed { i, c -> t[c.code] = i }
        t['-'.code] = 62; t['_'.code] = 63
    }

    fun decode(s: String): ByteArray {
        val clean = s.filter { !it.isWhitespace() && it != '=' }
        val out = ByteArrayOutputStream(clean.length * 3 / 4)
        var buf = 0; var bits = 0
        for (c in clean) {
            val v = if (c.code < 128) INV[c.code] else -1
            require(v >= 0) { "bad base64" }
            buf = (buf shl 6) or v; bits += 6
            if (bits >= 8) { bits -= 8; out.write((buf shr bits) and 0xFF) }
        }
        return out.toByteArray()
    }

    fun encode(data: ByteArray, url: Boolean = false): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else -1
            sb.append(STD[b0 shr 2])
            sb.append(STD[((b0 and 3) shl 4) or (if (b1 < 0) 0 else b1 shr 4)])
            sb.append(if (b1 < 0) '=' else STD[((b1 and 15) shl 2) or (if (b2 < 0) 0 else b2 shr 6)])
            sb.append(if (b2 < 0) '=' else STD[b2 and 63])
            i += 3
        }
        return if (url) sb.toString().replace('+', '-').replace('/', '_').trimEnd('=') else sb.toString()
    }

    /** DER bytes of a PEM public key (or of bare base64). */
    fun pemToDer(pem: String): ByteArray = decode(pem.lines().filter { !it.startsWith("-----") }.joinToString(""))
}

/** The decoded and checked server certificate. */
data class License(
    val valid: Boolean,
    val reason: String,
    val uid: Long = 0,
    val user: String = "",
    val pro: Boolean = false,
    val lifetime: Boolean = false,
    val plan: String? = null,
    val expiresAt: Long? = null,
    val grace: Long? = null,
    val issuedAt: Long = 0,
    val offlineUntil: Long = 0,
    val features: JSONObject = JSONObject(),
    val store: String? = null,
    val kid: String = "",
    /** Phone the subscription is active on, when it is not this one. */
    val elsewhere: String? = null
) {
    /** Pro right now: signed, not past its offline window and (unless lifetime) not past the paid end + grace. */
    fun proAt(now: Long): Boolean = valid && pro && now < offlineUntil && (lifetime || (grace ?: expiresAt ?: 0L) > now)

    fun daysLeft(now: Long): Int? = if (lifetime || expiresAt == null) null else maxOf(0, ((expiresAt - now + 86399) / 86400).toInt())

    /** Offline window ran out: the app must reach the server once to continue Pro. */
    fun needsSync(now: Long): Boolean = valid && pro && now >= offlineUntil && (lifetime || (grace ?: expiresAt ?: 0L) > now)

    companion object {
        val NONE = License(false, "none")

        /** Verifies `base64url(json).base64url(RSA-SHA256)` with the server's public key (DER). */
        fun parse(cert: String?, publicKeyDer: ByteArray?, expectedUid: Long? = null, expectedDevice: String? = null): License {
            if (cert.isNullOrBlank()) return NONE
            val dot = cert.indexOf('.')
            if (dot <= 0 || publicKeyDer == null) return License(false, "format")
            val body = cert.substring(0, dot)
            val sig = runCatching { B64.decode(cert.substring(dot + 1)) }.getOrNull() ?: return License(false, "format")
            val ok = runCatching {
                val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
                Signature.getInstance("SHA256withRSA").run { initVerify(key); update(body.toByteArray(Charsets.US_ASCII)); verify(sig) }
            }.getOrDefault(false)
            if (!ok) return License(false, "signature")
            val j = runCatching { JSONObject(String(B64.decode(body), Charsets.UTF_8)) }.getOrNull() ?: return License(false, "format")
            val uid = j.optLong("uid")
            if (expectedUid != null && expectedUid != uid) return License(false, "user")
            fun optLongOrNull(k: String): Long? = if (j.isNull(k) || !j.has(k)) null else j.optLong(k)
            // A certificate is signed for one phone; copied to another phone it gives no Pro.
            val device = if (j.isNull("device") || !j.has("device")) null else j.optString("device")
            val samePhone = device == null || expectedDevice == null || device == expectedDevice
            val conflict = j.optJSONObject("device_conflict")
            return License(
                valid = true, reason = if (samePhone) "ok" else "device", uid = uid, user = j.optString("user"), pro = j.optBoolean("pro") && samePhone,
                lifetime = j.optBoolean("lifetime"), plan = if (j.isNull("plan")) null else j.optString("plan"),
                expiresAt = optLongOrNull("exp"), grace = optLongOrNull("grace"), issuedAt = j.optLong("iat"),
                offlineUntil = j.optLong("offline_until"), features = j.optJSONObject("features") ?: JSONObject(),
                store = if (j.isNull("store")) null else j.optString("store"), kid = j.optString("kid"),
                elsewhere = conflict?.optString("name")?.ifBlank { "گوشی دیگر" }?.takeIf { it != "null" } ?: if (!samePhone) "گوشی دیگر" else null
            )
        }

        fun keyId(der: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }.take(16)
    }
}

/**
 * A clock the player cannot wind back. Time never runs below the latest time seen (device or server)
 * and, within one boot, advances with the monotonic uptime even if the wall clock is set back.
 */
class TrustedClock(private val read: (String) -> Long, private val write: (String, Long) -> Unit) {
    fun now(wallMs: Long = System.currentTimeMillis(), elapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        val wall = wallMs / 1000
        val maxSeen = read("clock_max")
        val anchorElapsed = read("clock_elapsed")
        val sameBoot = anchorElapsed in 1..elapsedMs
        val fromUptime = if (sameBoot && maxSeen > 0) maxSeen + (elapsedMs - anchorElapsed) / 1000 else maxSeen
        val trusted = maxOf(wall, fromUptime)
        if (trusted > maxSeen + 30 || !sameBoot) { write("clock_max", trusted); write("clock_elapsed", maxOf(1L, elapsedMs)) }
        return trusted
    }

    /** The server's own time is the best anchor. */
    fun anchor(serverSec: Long, elapsedMs: Long = SystemClock.elapsedRealtime()) {
        if (serverSec <= 0) return
        if (serverSec > read("clock_max")) write("clock_max", serverSec)
        write("clock_elapsed", maxOf(1L, elapsedMs))
    }

    /** Device clock set noticeably behind what was already seen. */
    fun tampered(wallMs: Long = System.currentTimeMillis()): Boolean = read("clock_max") - wallMs / 1000 > 600
}

// ================================================================ server API
class ApiError(val code: String, message: String, val status: Int = 0) : Exception(message)

object Api {
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** Replaced by tests: (method, route, body, token) → response JSON. */
    var transport: ((String, String, JSONObject?, String?) -> JSONObject)? = null

    fun call(method: String, route: String, body: JSONObject?, token: String?, done: (JSONObject?, ApiError?) -> Unit) {
        val t = transport
        if (t != null) {
            val r = runCatching { t(method, route, body, token) }
            deliver(r.getOrNull(), r.exceptionOrNull(), done)
            return
        }
        pool.execute {
            val r = runCatching { http(method, route, body, token) }
            main.post { deliver(r.getOrNull(), r.exceptionOrNull(), done) }
        }
    }

    private fun deliver(json: JSONObject?, error: Throwable?, done: (JSONObject?, ApiError?) -> Unit) {
        if (json == null) {
            done(null, error as? ApiError ?: ApiError("network", "ارتباط با سرور برقرار نشد. اینترنت را بررسی کنید؛ بازی‌ها بدون اینترنت هم کار می‌کنند."))
            return
        }
        json.optLong("server_time").takeIf { it > 0 }?.let { ProState.anchorClock(it) }
        if (json.optBoolean("ok")) done(json.optJSONObject("data") ?: JSONObject(), null)
        else {
            val e = json.optJSONObject("error")
            done(null, ApiError(e?.optString("code") ?: "error", e?.optString("message")?.takeIf { it.isNotBlank() } ?: "خطای سرور"))
        }
    }

    private fun http(method: String, route: String, body: JSONObject?, token: String?): JSONObject {
        val sep = if (StoreInfo.apiBase.contains('?')) "&" else "?"
        var url = StoreInfo.apiBase + sep + "r=" + route
        if (method == "GET" && body != null) body.keys().forEach { k -> url += "&" + k + "=" + java.net.URLEncoder.encode(body.optString(k), "UTF-8") }
        val c = URL(url).openConnection() as HttpURLConnection
        Tls.apply(c)
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.requestMethod = method
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "DivanEmtiaz/$APP_VERSION_NAME (${StoreInfo.storeId}; Android ${Build.VERSION.SDK_INT})")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (method != "GET") {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        c.disconnect()
        return runCatching { JSONObject(text) }.getOrElse { throw ApiError("bad_response", "پاسخ سرور خوانا نبود (HTTP $code).", code) }
    }
}

/** Older Android (5–7.0) lacks the newer root certificates; they are bundled in assets. */
object Tls {
    @Volatile private var factory: javax.net.ssl.SSLSocketFactory? = null
    var context: Context? = null

    fun apply(c: HttpURLConnection) {
        if (Build.VERSION.SDK_INT >= 25 || c !is javax.net.ssl.HttpsURLConnection) return
        val f = factory ?: build()?.also { factory = it } ?: return
        c.sslSocketFactory = f
    }

    private fun build(): javax.net.ssl.SSLSocketFactory? = runCatching {
        val ctx = context ?: return null
        val ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply { load(null, null) }
        // System roots first, then the bundled ones.
        val sys = java.security.KeyStore.getInstance("AndroidCAStore").apply { load(null, null) }
        sys.aliases().toList().forEach { a -> sys.getCertificate(a)?.let { ks.setCertificateEntry("sys-$a", it) } }
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        ctx.assets.list("certs")?.forEach { name ->
            ctx.assets.open("certs/$name").use { ks.setCertificateEntry("bundled-$name", cf.generateCertificate(it)) }
        }
        val tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
        javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }.socketFactory
    }.getOrNull()
}

// ================================================================ local state
/** One store receipt, kept until the server has recorded it (and the store has consumed it when asked). */
data class StoreReceipt(
    val sku: String, val token: String, val orderId: String?, val originalJson: String,
    val signature: String, val payload: String?, val purchaseTime: Long
) {
    fun toJson(): JSONObject = JSONObject().put("sku", sku).put("token", token).put("order_id", orderId ?: "")
        .put("original_json", originalJson).put("signature", signature).put("payload", payload ?: "").put("purchase_time", purchaseTime)

    companion object {
        fun from(j: JSONObject) = StoreReceipt(j.optString("sku"), j.optString("token"), j.optString("order_id").ifBlank { null },
            j.optString("original_json"), j.optString("signature"), j.optString("payload").ifBlank { null }, j.optLong("purchase_time"))
    }
}

/** Offer of one plan in this build's store, as the server computed it. */
data class PlanOffer(
    val code: String, val title: String, val description: String, val days: Int?, val lifetime: Boolean,
    val basePrice: Int, val finalPrice: Int, val discountPercent: Int, val badge: String?, val badgeColor: String?,
    val campaignEndsAt: Long?, val renewalNote: String?, val purchasable: Boolean, val state: String
) {
    companion object {
        fun from(j: JSONObject) = PlanOffer(
            j.optString("code"), j.optString("title"), j.optString("description"),
            if (j.isNull("duration_days")) null else j.optInt("duration_days"), j.optBoolean("lifetime"),
            j.optInt("base_price"), j.optInt("final_price"), j.optInt("discount_percent"),
            j.optString("badge_text").takeIf { it.isNotBlank() && it != "null" }, j.optString("badge_color").takeIf { it.startsWith("#") },
            if (j.isNull("campaign_ends_at")) null else j.optLong("campaign_ends_at"), j.optString("renewal_note").takeIf { it.isNotBlank() && it != "null" },
            j.optBoolean("purchasable"), j.optString("state")
        )
    }
}

object ProState {
    private var prefs: SharedPreferences? = null
    private val memory = HashMap<String, Any?>()
    val listeners = ArrayList<() -> Unit>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("divan_pro", Context.MODE_PRIVATE)
        StoreInfo.load(context)
        Tls.context = context.applicationContext
        @Suppress("HardwareIds")
        val androidId = runCatching { android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) }.getOrNull()
        if (getS("device_id") == null) {
            // Stable per phone (and kept on reinstall on Android 8+); only its hash ever leaves the phone.
            val seed = androidId?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" } ?: java.util.UUID.randomUUID().toString()
            putS("device_id", MessageDigest.getInstance("SHA-256").digest("divan|${context.packageName}|$seed".toByteArray()).joinToString("") { "%02x".format(it) })
        }
    }

    /** SHA-256 identifier of this phone, sent to the server to keep one subscription on one phone. */
    val deviceId: String get() = getS("device_id") ?: (java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()).replace("-", "").also { putS("device_id", it) }

    // ---- small typed storage (works without prefs in tests)
    private fun getS(k: String): String? = prefs?.getString(k, null) ?: memory[k] as? String
    private fun putS(k: String, v: String?) { val p = prefs; if (p != null) p.edit().putString(k, v).apply() else memory[k] = v }
    fun getL(k: String): Long = prefs?.getLong(k, 0L) ?: (memory[k] as? Long ?: 0L)
    fun putL(k: String, v: Long) { val p = prefs; if (p != null) p.edit().putLong(k, v).apply() else memory[k] = v }

    val clock = TrustedClock(::getL, ::putL)
    fun now(): Long = clock.now()
    fun anchorClock(serverSec: Long) = clock.anchor(serverSec)

    var token: String? get() = getS("token"); set(v) = putS("token", v)
    var username: String? get() = getS("username"); set(v) = putS("username", v)
    var userId: Long get() = getL("uid"); set(v) = putL("uid", v)
    var certificate: String? get() = getS("cert"); set(v) = putS("cert", v)
    var publicKey: String? get() = getS("pubkey"); set(v) = putS("pubkey", v)
    var configJson: String? get() = getS("config"); set(v) = putS("config", v)
    var accountJson: String? get() = getS("account"); set(v) = putS("account", v)
    var adFreeUntil: Long get() = getL("ad_free_until"); set(v) = putL("ad_free_until", v)

    val loggedIn: Boolean get() = !token.isNullOrBlank()

    private var cached: Pair<String, License>? = null
    val license: License
        get() {
            val c = certificate ?: return License.NONE
            cached?.takeIf { it.first == c }?.let { return it.second }
            val key = (StoreInfo.pinnedKey.ifBlank { publicKey.orEmpty() }).takeIf { it.isNotBlank() }?.let { runCatching { B64.pemToDer(it) }.getOrNull() }
            return License.parse(c, key, userId.takeIf { it > 0 }, deviceId).also { cached = c to it }
        }

    val isPro: Boolean get() = loggedIn && license.proAt(now())

    /** Ads are never prepared for Pro users or during a rewarded ad-free window. */
    val adsAllowed: Boolean get() = !isPro && now() >= adFreeUntil

    val config: JSONObject get() = configJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    val plans: List<PlanOffer>
        get() = config.optJSONArray("plans")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(PlanOffer::from) } }.orEmpty()

    /** Module access: the signed certificate wins; otherwise the cached public config; otherwise free. */
    fun feature(key: String): Triple<String, Boolean, Int?> {
        val signed = if (loggedIn && license.valid) license.features.optJSONObject(key) else null
        val f = signed ?: config.optJSONObject("features")?.optJSONObject(key) ?: return Triple("free", true, null)
        val mode = f.optString("mode", "free")
        val allowed = if (signed == null && mode == "pro") isPro else f.optBoolean("allowed", true)
        val limit = if (isPro || f.isNull("limit") || !f.has("limit")) null else f.optInt("limit")
        return Triple(mode, allowed, limit)
    }

    var pending: List<StoreReceipt>
        get() = getS("pending")?.let { s -> runCatching { JSONArray(s) }.getOrNull() }?.let { a -> (0 until a.length()).map { StoreReceipt.from(a.getJSONObject(it)) } }.orEmpty()
        set(v) = putS("pending", JSONArray().apply { v.forEach { put(it.toJson()) } }.toString())

    fun addPending(r: StoreReceipt) { pending = pending.filter { it.token != r.token } + r }
    fun removePending(token: String) { pending = pending.filter { it.token != token } }

    fun applyAccount(data: JSONObject) {
        data.optString("token").takeIf { it.isNotBlank() }?.let { token = it }
        data.optJSONObject("user")?.let { u -> username = u.optString("username"); userId = u.optLong("id") }
        data.optString("certificate").takeIf { it.isNotBlank() }?.let { certificate = it }
        accountJson = data.toString()
        putL("synced_at", now())
        notifyChanged()
    }

    fun logout() {
        token = null; certificate = null; accountJson = null; userId = 0; username = null
        notifyChanged()
    }

    fun notifyChanged() { cached = null; listeners.toList().forEach { it() } }
    fun lastSync(): Long = getL("synced_at")
    fun configAge(): Long = now() - getL("config_at")
    fun markConfig() = putL("config_at", now())
}

// ================================================================ store billing (implemented by each flavor)
sealed class BillingResult {
    class Success(val receipt: StoreReceipt) : BillingResult()
    object Canceled : BillingResult()
    class Failed(val message: String) : BillingResult()
}

interface StoreBilling {
    /** Whether the store app is installed and billing is configured in this build. */
    fun availability(context: Context): String?
    fun purchase(activity: Activity, sku: String, productType: String, payload: String, dynamicPriceToken: String?, done: (BillingResult) -> Unit)
    fun consume(context: Context, receipt: StoreReceipt, done: (Boolean) -> Unit)
    fun owned(context: Context, done: (List<StoreReceipt>?, String?) -> Unit)
}

/** Build without a store library (tests): purchases are not offered. */
class NoStoreBilling : StoreBilling {
    override fun availability(context: Context): String = "پرداخت در این نسخه فعال نیست."
    override fun purchase(activity: Activity, sku: String, productType: String, payload: String, dynamicPriceToken: String?, done: (BillingResult) -> Unit) = done(BillingResult.Failed("پرداخت در این نسخه فعال نیست."))
    override fun consume(context: Context, receipt: StoreReceipt, done: (Boolean) -> Unit) = done(false)
    override fun owned(context: Context, done: (List<StoreReceipt>?, String?) -> Unit) = done(emptyList(), null)
}

object Billing {
    var override: StoreBilling? = null
    /** Each flavor ships `FlavorBilling`; the shared code never names a store library directly. */
    val impl: StoreBilling by lazy {
        override ?: runCatching { Class.forName("com.meysam.divanemtiaz.FlavorBilling").getDeclaredConstructor().newInstance() as StoreBilling }.getOrNull() ?: NoStoreBilling()
    }
    fun get(): StoreBilling = override ?: impl
}

// ================================================================ flows
object ProSync {
    private fun device(): JSONObject = JSONObject().put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80)).put("device_id", ProState.deviceId)
        .put("platform", "android").put("app_version", APP_VERSION_NAME).put("store", StoreInfo.storeId)

    /** Fetches public plans, module access and ads at most every few hours (cached for offline use). */
    fun refreshConfig(force: Boolean = false, done: (() -> Unit)? = null) {
        if (!force && ProState.configJson != null && ProState.configAge() in 0..6 * 3600) { done?.invoke(); return }
        Api.call("GET", "v1/config", JSONObject().put("store", StoreInfo.storeId), ProState.token) { data, _ ->
            if (data != null) { ProState.configJson = data.toString(); ProState.markConfig(); ProState.notifyChanged() }
            done?.invoke()
        }
    }

    /** Public key of the server, trusted on first use unless the build pins one. */
    private fun ensureKey(done: (ApiError?) -> Unit) {
        if (StoreInfo.pinnedKey.isNotBlank() || !ProState.publicKey.isNullOrBlank()) { done(null); return }
        Api.call("GET", "v1/keys", null, null) { data, err ->
            val pem = data?.optString("public_key").orEmpty()
            if (pem.isNotBlank() && runCatching { License.keyId(B64.pemToDer(pem)) == data?.optString("kid") }.getOrDefault(false)) ProState.publicKey = pem
            done(if (pem.isBlank()) err ?: ApiError("keys", "کلید سرور دریافت نشد.") else null)
        }
    }

    fun register(username: String, password: String, email: String?, done: (String?, ApiError?) -> Unit) = ensureKey { e ->
        if (e != null) { done(null, e); return@ensureKey }
        val body = device().put("username", username).put("password", password)
        if (!email.isNullOrBlank()) body.put("email", email)
        Api.call("POST", "v1/auth/register", body, null) { data, err ->
            if (data != null) { ProState.applyAccount(data); refreshConfig(force = true) }
            done(data?.optString("recovery_key"), err)
        }
    }

    fun login(username: String, password: String, done: (ApiError?) -> Unit) = ensureKey { e ->
        if (e != null) { done(e); return@ensureKey }
        Api.call("POST", "v1/auth/login", device().put("username", username).put("password", password), null) { data, err ->
            if (data != null) { ProState.applyAccount(data); refreshConfig(force = true); retryPending() }
            done(err)
        }
    }

    fun recover(username: String, key: String, newPassword: String, done: (String?, ApiError?) -> Unit) = ensureKey { e ->
        if (e != null) { done(null, e); return@ensureKey }
        Api.call("POST", "v1/auth/recover", device().put("username", username).put("recovery_key", key).put("new_password", newPassword), null) { data, err ->
            if (data != null) { ProState.applyAccount(data); refreshConfig(force = true) }
            done(data?.optString("recovery_key"), err)
        }
    }

    fun newRecoveryKey(password: String, done: (String?, ApiError?) -> Unit) =
        Api.call("POST", "v1/auth/recovery-key", JSONObject().put("password", password), ProState.token) { data, err -> done(data?.optString("recovery_key"), err) }

    fun changePassword(old: String, new: String, done: (ApiError?) -> Unit) =
        Api.call("POST", "v1/auth/password", JSONObject().put("old_password", old).put("new_password", new), ProState.token) { _, err -> done(err) }

    fun logout(done: () -> Unit) {
        val t = ProState.token
        ProState.logout()
        if (t != null) Api.call("POST", "v1/auth/logout", JSONObject(), t) { _, _ -> done() } else done()
    }

    /** Full account (certificate, purchases, devices). A dead token signs the device out. */
    fun refreshAccount(done: ((ApiError?) -> Unit)? = null) {
        val t = ProState.token ?: run { done?.invoke(null); return }
        Api.call("GET", "v1/me", JSONObject().put("store", StoreInfo.storeId), t) { data, err ->
            if (data != null) ProState.applyAccount(data)
            else if (err?.code == "unauthorized") ProState.logout()
            done?.invoke(err)
        }
    }

    /** Moves the subscription to this phone (password needed; limited per 30 days). */
    fun transferHere(password: String, done: (ApiError?) -> Unit) =
        Api.call("POST", "v1/devices/transfer", JSONObject().put("password", password), ProState.token) { data, err ->
            if (data != null) ProState.applyAccount(data)
            done(err)
        }

    fun revokeSession(id: Long, allOthers: Boolean, done: (ApiError?) -> Unit) =
        Api.call("POST", "v1/sessions/revoke", JSONObject().put("id", id).put("all_others", allOthers), ProState.token) { _, err ->
            if (err == null) refreshAccount(done) else done(err)
        }

    /** At app start: config, account and any receipt the server has not recorded yet. */
    fun onStart() {
        refreshConfig()
        if (ProState.loggedIn && ProState.now() - ProState.lastSync() > 6 * 3600) refreshAccount()
        retryPending()
    }

    // ---------------------------------------------------------------- purchase
    fun buy(activity: Activity, plan: PlanOffer, done: (String?, ApiError?) -> Unit) {
        val billing = Billing.get()
        billing.availability(activity)?.let { done(null, ApiError("billing", it)); return }
        Api.call("POST", "v1/purchases/intent", JSONObject().put("plan", plan.code).put("store", StoreInfo.storeId), ProState.token) { intent, err ->
            if (intent == null) { done(null, err); return@call }
            val sku = intent.optString("sku")
            billing.purchase(activity, sku, intent.optString("product_type"), intent.optString("payload"),
                intent.optString("dynamic_price_token").takeIf { it.isNotBlank() && it != "null" }) { r ->
                when (r) {
                    is BillingResult.Canceled -> done(null, ApiError("canceled", "خرید لغو شد؛ مبلغی کم نشده است."))
                    is BillingResult.Failed -> done(null, ApiError("billing", r.message))
                    is BillingResult.Success -> {
                        // Kept until the server records it, so a crash or lost connection never loses a purchase.
                        ProState.addPending(r.receipt)
                        verify(activity, r.receipt) { status, e -> done(status, e) }
                    }
                }
            }
        }
    }

    private fun verify(context: Context, r: StoreReceipt, done: (String?, ApiError?) -> Unit) {
        val body = r.toJson().put("store", StoreInfo.storeId)
        Api.call("POST", "v1/purchases/verify", body, ProState.token) { data, err ->
            if (data == null) {
                // The receipt stays pending only while it can still be verified later.
                if (err != null && err.code in setOf("owned_by_other", "refunded", "rejected", "bad_signature", "bad_receipt", "payload_mismatch", "unknown_sku")) ProState.removePending(r.token)
                done(null, err ?: ApiError("network", "تأیید خرید ممکن نشد؛ دوباره تلاش می‌شود."))
                return@call
            }
            ProState.applyAccount(data)
            val status = data.optString("status")
            if (data.optBoolean("consume")) Billing.get().consume(context, r) { ok -> if (ok) ProState.removePending(r.token) }
            else if (status == "verified" || status == "signature_verified") ProState.removePending(r.token)
            done(status, null)
        }
    }

    /** Sends receipts that were paid but not yet recorded (app closed, no internet). */
    fun retryPending(context: Context? = null) {
        if (!ProState.loggedIn) return
        val ctx = context ?: Tls.context ?: return
        ProState.pending.forEach { verify(ctx, it) { _, _ -> } }
    }

    /** «بازیابی خرید»: asks the store for owned products and records them on this account. */
    fun restore(context: Context, done: (Int, ApiError?) -> Unit) {
        Billing.get().owned(context) { list, error ->
            if (list == null) { done(0, ApiError("billing", error ?: "فهرست خریدها از فروشگاه دریافت نشد.")); return@owned }
            val all = (list + ProState.pending).distinctBy { it.token }
            if (all.isEmpty()) { refreshAccount { e -> done(0, e) }; return@owned }
            val body = JSONObject().put("store", StoreInfo.storeId).put("purchases", JSONArray().apply { all.forEach { put(it.toJson()) } })
            Api.call("POST", "v1/purchases/restore", body, ProState.token) { data, err ->
                if (data == null) { done(0, err); return@call }
                ProState.applyAccount(data)
                val results = data.optJSONArray("results") ?: JSONArray()
                var restored = 0
                for (i in 0 until results.length()) {
                    val res = results.getJSONObject(i)
                    val receipt = all.getOrNull(i) ?: continue
                    if (res.optString("status") in setOf("verified", "signature_verified")) {
                        restored++
                        if (res.optBoolean("consume")) Billing.get().consume(context, receipt) { ok -> if (ok) ProState.removePending(receipt.token) }
                        else ProState.removePending(receipt.token)
                    }
                }
                done(restored, null)
            }
        }
    }

    fun support(kind: String, message: String, contact: String, orderId: String, username: String, done: (Long?, ApiError?) -> Unit) =
        Api.call("POST", "v1/support", JSONObject().put("kind", kind).put("message", message).put("contact", contact).put("order_id", orderId)
            .put("username", username).put("store", StoreInfo.storeId), ProState.token) { data, err -> done(data?.optLong("id"), err) }
}
