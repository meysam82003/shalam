package com.meysam.divanemtiaz

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View

fun Screen.openContact(address: String) {
    val target = when {
        address.matches(Regex("@[A-Za-z0-9_]{5,32}")) -> "https://t.me/" + address.drop(1)
        address.startsWith("https://") || address.startsWith("http://") -> address
        address.contains('@') && !address.contains(' ') -> "mailto:$address"
        else -> null
    }
    fun copy() {
        (host.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("contact", address))
        kit.toast("راه ارتباطی کپی شد")
    }
    if (target == null) copy()
    else runCatching { host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }.onFailure { copy() }
}

class SponsorshipScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("ثبت تبلیغ و اسپانسری", "معرفی کسب‌وکار شما در دیوان امتیاز") {
        val address = ProState.config.optString("sponsor_contact", "@Meysam_pardaz").ifBlank { "@Meysam_pardaz" }
        addView(kit.text("برای ثبت بنر، تصویر، GIF، ویدئو یا حمایت از برنامه، از راه زیر با ما در ارتباط باشید.", TextStyle.BODY, Royal.ivory), kit.spaced(12))
        addView(kit.text(address, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER).apply { setTextIsSelectable(true) }, kit.spaced(12))
        addView(kit.button("ارتباط برای ثبت تبلیغ", ButtonKind.PRIMARY, RoyalIcon.SHARE) { openContact(address) })
    }
}
