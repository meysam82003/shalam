package com.meysam.divanemtiaz

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** «دربارهٔ برنامه»: a word to the players, what the app does, and the address for criticism and suggestions. */
class AboutScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("دربارهٔ برنامه", "$APP_VERSION_LABEL  •  آفلاین") {
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(HeroCrestView(host), LinearLayout.LayoutParams(kit.dp(220), kit.dp(170)))
            addView(kit.text("دیوان امتیاز", TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER))
            addView(kit.text("داور و دفتر امتیاز شاهانه", TextStyle.BODY, Royal.muted, Gravity.CENTER))
        }, kit.spaced(12))

        addView(kit.section("عرض ادب", RoyalIcon.CROWN))
        addView(kit.panel(PanelStyle.RAISED).apply {
            addView(kit.text("""با سلام و عرض ادب و احترام خدمت شما همراه گرامی،

از اینکه «دیوان امتیاز» را برای دورهمی‌هایتان برگزیده‌اید صمیمانه سپاسگزاریم. این برنامه با عشق به بازی‌های اصیل ایرانی و برای آسوده‌تر شدن داوری و ثبت امتیاز ساخته شده تا شما فارغ از حساب و کتاب، از کنار هم بودن لذت ببرید.

دیوان امتیاز کاملاً آفلاین است، هیچ اطلاعاتی از شما جمع نمی‌کند و بدون تبلیغ در خدمت شماست. اگر در جایی کم‌وکاستی دیدید، پیشاپیش پوزش می‌خواهیم و قول می‌دهیم با کمک نظرات ارزشمند شما هر روز بهترش کنیم.

بزم‌هایتان گرم، دست‌هایتان پربرگ و دلتان شاد باد.""", TextStyle.BODY, Royal.ivory))
        }, kit.spaced(12))

        addView(kit.section("در دیوان چه هست", RoyalIcon.STAR))
        addView(kit.panel(PanelStyle.FLAT).apply {
            (GameType.values().map { "${it.title}: ${it.subtitle}" } + listOf(
                "راوی مافیا: سناریوها، نقش‌ها، کارت‌ها و داوری کامل شب و روز",
                "پانتومیم: ۱۸٬۰۱۷ واژه و پرسش، مسابقهٔ عادی، سرعتی، صدابازی، جنگی، پنج‌ثانیه‌ای و دور طلایی",
                "جاسوس: پخش پنهان نقش‌ها، کلمهٔ مخفی از چهار دسته و کلمات شما، زمان‌سنج و زنگ پایان",
                "لیگ، رنک‌بندی، کارنامهٔ افراد و تاریخچهٔ کامل بازی‌ها",
                "ذخیرهٔ خودکار، ادامهٔ بازی، ویرایش دست‌ها و پشتیبان‌گیری"
            )).forEach { addView(kit.text("•  $it", TextStyle.LABEL, Royal.ivory), kit.spaced(4)) }
        }, kit.spaced(12))

        addView(kit.section("انتقاد و پیشنهاد", RoyalIcon.EDIT))
        addView(kit.panel(PanelStyle.RAISED).apply {
            addView(kit.text("هر انتقاد، پیشنهاد، گزارش اشکال یا ایده‌ای برای بازی تازه دارید، با کمال میل پذیرای آن هستیم:", TextStyle.BODY, Royal.ivory))
            addView(kit.gap(10))
            addView(kit.text(EMAIL, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                textDirection = View.TEXT_DIRECTION_LTR
                setTextIsSelectable(true)
            })
            addView(kit.gap(10))
            addView(kit.horizontal().apply {
                addView(kit.weight(kit.button("ارسال ایمیل", ButtonKind.PRIMARY, RoyalIcon.SHARE, 46) { sendMail() }))
                addView(kit.hgap(8))
                addView(kit.weight(kit.button("کپی نشانی", ButtonKind.SECONDARY, RoyalIcon.CARDS, 46) { copyMail() }))
            })
        }, kit.spaced(12))

        addView(kit.text("با سپاس از همراهی شما  •  $APP_VERSION_LABEL", TextStyle.CAPTION, Royal.dim, Gravity.CENTER), kit.spaced(16))
    }

    private fun sendMail() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "دیوان امتیاز — انتقاد و پیشنهاد ($APP_VERSION_LABEL)")
        }
        runCatching { host.startActivity(Intent.createChooser(intent, "ارسال ایمیل")) }
            .onFailure { copyMail() }
    }

    private fun copyMail() {
        val cm = host.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("email", EMAIL))
        kit.toast("نشانی ایمیل کپی شد")
    }

    companion object {
        const val EMAIL = "meysamsalamatnasab@gmail.com"
    }
}
