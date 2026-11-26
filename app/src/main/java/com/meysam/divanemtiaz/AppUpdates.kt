package com.meysam.divanemtiaz

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import org.json.JSONObject

data class AppUpdate(val versionCode: Int, val versionName: String, val required: Boolean, val notes: String) {
    fun isNewerThan(installed: Long) = versionCode.toLong() > installed

    val description: String get() = buildString {
        append("نسخهٔ ").append(PersianText.digits(versionName)).append(" آمادهٔ دریافت است.")
        if (required) append("\nبرای ادامه، ابتدا برنامه را به‌روزرسانی کنید.")
        if (notes.isNotBlank()) append("\n\n").append(notes)
    }

    companion object {
        fun read(data: JSONObject): AppUpdate? {
            val code = data.optLong("version_code", -1)
            val name = data.optString("version_name")
            val required = data.opt("required") as? Boolean ?: return null
            if (code !in 0L..2_100_000_000L || name.length > 40) return null
            if (code == 0L && required || code > 0 && !name.matches(Regex("""[0-9]+(?:\.[0-9]+){1,3}"""))) return null
            return AppUpdate(code.toInt(), name, required, data.optString("notes").take(1200))
        }
    }
}

object AppUpdates {
    @Volatile private var installed: Long? = null
    private fun supported() = Build.DEVICE != "web"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("divan_app_updates", Context.MODE_PRIVATE)
    private fun policyKey(store: String) = "policy_$store"

    @Suppress("DEPRECATION")
    private fun version(context: Context): Long = installed ?: runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0).also { if (it > 0) installed = it }

    fun available(context: Context): AppUpdate? {
        if (!supported()) return null
        val value = prefs(context).getString(policyKey(StoreInfo.storeId), null) ?: return null
        val code = version(context)
        return runCatching { AppUpdate.read(JSONObject(value)) }.getOrNull()?.takeIf { code > 0 && it.isNewerThan(code) }
    }

    /** A failed or invalid response leaves the last policy in place, including across an offline restart. */
    fun sync(context: Context) {
        if (!supported()) return
        val store = StoreInfo.storeId
        val data = Api.blocking("GET", "v1/app-update", JSONObject().put("store", store), null)
        val state = prefs(context)
        if (data != null && AppUpdate.read(data) != null && state.getString(policyKey(store), null) != data.toString()) {
            state.edit().putString(policyKey(store), data.toString()).commit()
        }
        val update = available(context)
        val notified = "notified_$store"
        if (update == null) {
            Notices.clearUpdate(context)
        } else {
            val key = "${update.versionCode}:${update.required}"
            if (key != state.getString(notified, null) && Notices.postUpdate(context, update)) {
                state.edit().putString(notified, key).apply()
            }
        }
    }

    private data class Destination(val label: String, val url: String, val scheme: String? = null, val packageName: String? = null)

    private fun destination(): Destination = when (StoreInfo.storeId) {
        "myket" -> Destination("مایکت", "https://myket.ir/app/com.meysam.divanemtiaz", "myket", "ir.mservices.market")
        "direct" -> Destination("سایت", "https://divan-emtiaz.ir/download.html#android")
        else -> Destination("کافه‌بازار", "https://cafebazaar.ir/app/com.meysam.divanemtiaz", "bazaar", "com.farsitel.bazaar")
    }

    val downloadLabel: String get() = "دریافت از ${destination().label}"

    fun download(host: MainActivity) {
        val dest = destination()
        if (dest.scheme != null) {
            for (scheme in listOf(dest.scheme, "market")) {
                val link = "$scheme://details?id=com.meysam.divanemtiaz"
                if (runCatching {
                    host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(dest.packageName))
                }.isSuccess) return
            }
        }
        if (runCatching { host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dest.url))) }.isFailure) {
            host.kit.toast("مسیر دریافت به‌روزرسانی باز نشد.")
        }
    }

    fun showOptional(host: MainActivity, update: AppUpdate) = host.kit.dialog(
        "به‌روزرسانی دیوان امتیاز", null,
        host.kit.text(update.description, TextStyle.BODY, Royal.ivory, Gravity.CENTER),
        listOf(
            DialogAction(downloadLabel, ButtonKind.PRIMARY) { download(host) },
            DialogAction("بعداً")
        )
    ).also { it.show() }
}

class RequiredUpdateScreen(host: MainActivity, var update: AppUpdate) : Screen(host) {
    override fun build(): View = FrameLayout(host).apply {
        val card = kit.panel(PanelStyle.RAISED, 18).apply {
            addView(kit.text("به‌روزرسانی الزامی است", TextStyle.HEADING, Royal.title, Gravity.CENTER))
            addView(kit.divider())
            addView(kit.text(update.description, TextStyle.BODY, Royal.ivory, Gravity.CENTER))
            addView(kit.button(AppUpdates.downloadLabel, ButtonKind.PRIMARY, height = 48) { AppUpdates.download(host) }, kit.spaced(12))
            addView(kit.button("بررسی دوباره", ButtonKind.SECONDARY, height = 44) { Notices.check(host) }, kit.spaced(8))
        }
        addView(ScrollView(host).apply { addView(card) }, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
            setMargins(kit.dp(18), kit.dp(18), kit.dp(18), kit.dp(18))
        })
    }

    override fun onBack(): Boolean {
        host.finish()
        return true
    }
}
