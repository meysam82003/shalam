package com.meysam.divanemtiaz

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Notifications in the phone's notification bar, on three separate channels:
 * account and payments, subscription reminders, and offers. Offers are shown only to players who said yes
 * (asked once, changeable in Settings); a Pro player never gets them unless they turned them on themselves.
 *
 * Delivery: a system job a few times a day asks the server, only when the phone has internet and Android
 * allows it. It is not instant push; nothing keeps the phone awake.
 */
object Notices {
    const val CH_ACCOUNT = "divan_account"
    const val CH_SUBSCRIPTION = "divan_subscription"
    const val CH_OFFERS = "divan_offers"
    const val EXTRA_TARGET = "divan_notice_target"
    const val EXTRA_ID = "divan_notice_id"
    private const val JOB_ID = 4242
    private const val PERIOD_MS = 6 * 3600 * 1000L

    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences("divan_notices", Context.MODE_PRIVATE)

    fun asked(c: Context) = prefs(c).getBoolean("asked", false)
    fun offers(c: Context) = prefs(c).getBoolean("offers", false)
    fun offersPro(c: Context) = prefs(c).getBoolean("offers_pro", false)
    fun reminders(c: Context) = prefs(c).getBoolean("reminders", true)
    fun setOffers(c: Context, on: Boolean) = prefs(c).edit().putBoolean(if (ProState.isPro) "offers_pro" else "offers", on).putBoolean("asked", true).apply()
    fun setReminders(c: Context, on: Boolean) = prefs(c).edit().putBoolean("reminders", on).apply()
    fun markAsked(c: Context) = prefs(c).edit().putBoolean("asked", true).apply()

    /** Offers for this player right now: consent given (Pro players need their own, separate yes) and allowed by Android. */
    fun offersWanted(c: Context): Boolean = canPost(c) && (if (ProState.isPro) offersPro(c) else offers(c))

    fun canPost(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return Build.VERSION.SDK_INT < 24 || nm.areNotificationsEnabled()
    }

    /** Android 13+: the system question; earlier versions allow notifications by default. */
    fun requestPermission(a: Activity) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    fun channels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_ACCOUNT, "حساب و پرداخت", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "تأیید خرید و موارد ضروری حساب" })
        nm.createNotificationChannel(NotificationChannel(CH_SUBSCRIPTION, "اشتراک و تمدید", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "یادآوری پیش از پایان اشتراک Pro" })
        nm.createNotificationChannel(NotificationChannel(CH_OFFERS, "پیشنهادها و تخفیف‌ها", NotificationManager.IMPORTANCE_LOW).apply { description = "فقط اگر خودتان پذیرفته باشید؛ از تنظیمات برنامه خاموش می‌شود" })
    }

    /** The few-times-a-day check; kept across restarts, runs only with internet. */
    fun schedule(c: Context) {
        val js = c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (js.allPendingJobs.any { it.id == JOB_ID }) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(c, NoticeJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .build()
        runCatching { js.schedule(info) }
    }

    /** One check (background thread): a subscription reminder, then at most one offer. */
    fun sync(c: Context) {
        val ctx = c.applicationContext
        ProState.init(ctx)
        channels(ctx)
        if (!canPost(ctx)) return
        remindIfNeeded(ctx)
        if (!offersWanted(ctx)) return
        val q = JSONObject().put("store", StoreInfo.storeId).put("device", ProState.deviceId).put("pro_optin", if (offersPro(ctx)) "1" else "0")
        val data = Api.blocking("GET", "v1/notices", q, ProState.token) ?: return
        val list = data.optJSONArray("notices") ?: return
        val seen = prefs(ctx).getStringSet("seen", emptySet()).orEmpty().toMutableSet()
        for (i in 0 until list.length()) {
            val n = list.optJSONObject(i) ?: continue
            val id = n.optInt("id")
            if (id <= 0 || "$id" in seen) continue
            val target = n.optString("target")
            if (!targetAllowed(target)) continue
            post(ctx, CH_OFFERS, 10_000 + id, n.optString("title").take(80), n.optString("body").take(300), target, id, image(n.optString("image")))
            seen += "$id"
            prefs(ctx).edit().putStringSet("seen", seen.toList().takeLast(200).toSet()).apply()
            Api.blocking("POST", "v1/notices/event", JSONObject().put("id", id).put("device", ProState.deviceId).put("event", "shown"), null)
        }
    }

    /** Timed Pro ending within three days: one reminder per end date (never for lifetime). */
    private fun remindIfNeeded(c: Context) {
        if (!reminders(c) || !ProState.isPro) return
        val lic = ProState.license
        val end = lic.expiresAt ?: return
        if (lic.lifetime) return
        val left = lic.daysLeft(ProState.now()) ?: return
        if (left !in 1..3 || prefs(c).getLong("reminded_for", 0L) == end) return
        post(c, CH_SUBSCRIPTION, 2, "اشتراک Pro رو به پایان است", "${left} روز دیگر از اشتراک Pro شما مانده است. برای ادامه، تمدید کنید.", "divan://subscription", 0, null)
        prefs(c).edit().putLong("reminded_for", end).apply()
    }

    /** Only in-app pages; web links only in the site version (store rules). */
    fun targetAllowed(t: String): Boolean = when {
        t.isBlank() -> true
        t.startsWith("divan://") -> t.matches(Regex("^divan://[a-z0-9_/]{1,60}$"))
        t.startsWith("https://") -> StoreInfo.storeId == "direct"
        else -> false
    }

    /** Pictures only from the Divan server itself, small, decoded safely. */
    private fun image(url: String): Bitmap? {
        if (url.isBlank() || !url.startsWith("https://")) return null
        val apiHost = runCatching { URL(StoreInfo.apiBase).host }.getOrNull() ?: return null
        return runCatching {
            val u = URL(url)
            if (u.host != apiHost) return null
            val conn = u.openConnection() as HttpURLConnection
            Tls.apply(conn)
            conn.connectTimeout = 10000; conn.readTimeout = 15000; conn.instanceFollowRedirects = false
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.size > 1_500_000) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun post(c: Context, channel: String, nid: Int, title: String, text: String, target: String, noticeId: Int, picture: Bitmap?) {
        val open = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET, target)
            putExtra(EXTRA_ID, noticeId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(c, nid, open, flags)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, channel) else Notification.Builder(c)
        val icon = c.resources.getIdentifier("ic_stat_divan", "drawable", c.packageName).takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        b.setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(if (picture != null) Notification.BigPictureStyle().bigPicture(picture).setSummaryText(text) else Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFFE3C27A.toInt())
        if (picture != null) b.setLargeIcon(picture)
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(nid, b.build())
    }

    /** A tapped notification: report «opened» and go to its page (only allowed targets). */
    fun opened(host: MainActivity, intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: return
        val id = intent.getIntExtra(EXTRA_ID, 0)
        intent.removeExtra(EXTRA_TARGET)
        if (id > 0) Api.call("POST", "v1/notices/event", JSONObject().put("id", id).put("device", ProState.deviceId).put("event", "opened"), null) { _, _ -> }
        if (!targetAllowed(target) || target.isBlank()) return
        if (target.startsWith("divan://")) AdEngine.openInApp(host, target)
        else runCatching { host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
    }
}

/** The system job that runs [Notices.sync]; finishes as soon as the check is done. */
class NoticeJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { Notices.sync(applicationContext) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
