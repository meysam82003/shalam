package com.meysam.divanemtiaz

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/*
 * «دیوان Pro»: plans and purchase, account (username + password + recovery key; no phone number),
 * «اشتراک من» and support. The games never wait for any of this.
 */

private fun Screen.toman(v: Int): String = kit.n(String.format(java.util.Locale.US, "%,d", v)).replace(',', '٬') + " تومان"
private fun Screen.date(sec: Long?): String = if (sec == null || sec <= 0) "—" else JalaliDate.format(sec * 1000, settings.general.persianDigits).substringBefore("  ")
private fun Screen.secretField(hint: String): EditText = kit.field(hint).apply {
    // A password input type switches to a monospace face; the app's face and a right-to-left hint are restored.
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    typeface = RoyalFonts.get(context, true)
    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    gravity = Gravity.CENTER_VERTICAL or Gravity.START
}
private fun Screen.latinField(hint: String): EditText = kit.field(hint).apply {
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    typeface = RoyalFonts.get(context, true)
    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
}
private fun Screen.errorLine(): TextView = kit.text("", TextStyle.LABEL, Royal.alert).apply { visibility = View.GONE }
/** Where a purchase or a signed-in device comes from, in words ("" when the server did not say). */
private fun storeLabel(store: String?): String = when (store) { "myket" -> "مایکت"; "bazaar" -> "بازار"; "direct" -> "نسخهٔ سایت"; "web" -> "نسخهٔ وب"; else -> "" }

private fun TextView.show(msg: String?) { text = msg.orEmpty(); visibility = if (msg.isNullOrBlank()) View.GONE else View.VISIBLE }

/** Shown when this device is signed in but the subscription is held by another device (one device at a time). */
private fun Screen.elsewherePanel(): View? {
    if (!ProState.loggedIn) return null
    val name = ProState.license.elsewhere ?: return null
    return kit.panel(PanelStyle.DANGER).apply {
        addView(kit.text("اشتراک شما روی دستگاه «$name» فعال است", TextStyle.BODY_BOLD, Royal.ivory))
        addView(kit.text("حساب در هر زمان روی یک دستگاه باز است. می‌توانید اشتراک را به این دستگاه منتقل کنید؛ دستگاه قبلی در اولین اتصال به اینترنت از حساب خارج می‌شود. انتقال حداکثر چند بار در ماه ممکن است.", TextStyle.LABEL, Royal.muted))
        addView(kit.gap(8))
        addView(kit.button("انتقال اشتراک به این دستگاه", ButtonKind.PRIMARY, RoyalIcon.SWAP, 46) {
            val input = secretField("رمز عبور")
            lateinit var d: android.app.Dialog
            d = kit.dialog("انتقال به این دستگاه", "برای انتقال، رمز عبور حساب را وارد کنید.", kit.vertical().apply { addView(input, kit.fill()) }, listOf(
                DialogAction("انتقال", ButtonKind.PRIMARY, dismiss = false) {
                    ProSync.transferHere(input.text.toString()) { e ->
                        if (e != null) kit.toast(e.message ?: "") else { d.dismiss(); kit.toast("اشتراک به این دستگاه منتقل شد"); host.refresh() }
                    }
                },
                DialogAction("انصراف")
            ))
            d.show()
        })
    }
}

/** Pro listener that re-renders the page while it is shown. */
private fun Screen.watchPro(): () -> Unit = { host.post(0) { if (host.current === this) host.refresh() } }

// ================================================================ plans and purchase
class ProScreen(host: MainActivity, private val reason: String? = null, private val focusPlan: String? = null) : Screen(host) {
    private var busy = false
    /** Discount code accepted for this visit: code, percent and the plan it is for (null = all plans). */
    private var promo: Triple<String, Int, String?>? = null
    private val listener = watchPro()

    override fun onShow() { ProState.listeners += listener; ProSync.refreshConfig { ProSync.refreshStorePrices(host) }; checkPending() }
    override fun onHide() { ProState.listeners -= listener }

    override fun build(): View = scaffold("دیوان Pro", "بدون تبلیغ، با همهٔ قابلیت‌ها") {
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(HeroCrestView(host), LinearLayout.LayoutParams(kit.dp(190), kit.dp(140)))
            addView(kit.text("دیوان Pro", TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER))
            addView(kit.text("حمایت از ساخت دیوان، بدون تبلیغ و با همهٔ قابلیت‌ها", TextStyle.BODY, Royal.muted, Gravity.CENTER))
        }, kit.spaced(12))

        elsewherePanel()?.let { addView(it, kit.spaced(12)) }
        if (reason != null) addView(kit.panel(PanelStyle.SELECTED).apply {
            addView(kit.text(reason, TextStyle.BODY, Royal.ivory))
        }, kit.spaced(12))

        val lic = ProState.license
        val now = ProState.now()
        if (ProState.isPro) {
            addView(kit.panel(PanelStyle.SUCCESS).apply {
                addView(kit.text(if (lic.lifetime) "اشتراک Pro مادام‌العمر — فعال" else "Pro شما تا ${date(lic.expiresAt)} فعال است", TextStyle.BODY_BOLD, Royal.ivory))
                if (!lic.lifetime) addView(kit.text("${kit.n(lic.daysLeft(now) ?: 0)} روز باقی‌مانده؛ خرید تازه به انتهای همین دوره اضافه می‌شود.", TextStyle.LABEL, Royal.muted))
                addView(kit.gap(8))
                addView(kit.button("اشتراک من", ButtonKind.SECONDARY, RoyalIcon.CROWN, 44) { host.push(SubscriptionScreen(host)) })
            }, kit.spaced(12))
        }

        addView(kit.section("چه چیزهایی دارد", RoyalIcon.STAR))
        addView(kit.listGroup(benefits().map { (icon, title, caption) -> kit.listRow(kit.iconChip(icon), title, caption) }))

        addView(kit.section("پلن‌ها", RoyalIcon.CROWN))
        val plans = ProState.plans
        if (plans.isEmpty()) {
            addView(kit.panel(PanelStyle.FLAT).apply {
                addView(kit.text("برای دیدن قیمت‌ها، یک بار به اینترنت وصل شوید. بازی‌ها بدون اینترنت کار می‌کنند.", TextStyle.BODY, Royal.muted))
                addView(kit.gap(8))
                addView(kit.button("دریافت قیمت‌ها", ButtonKind.SECONDARY, RoyalIcon.UNDO, 44) { ProSync.refreshConfig(force = true) { host.refresh() } })
            }, kit.spaced(12))
        } else {
            val monthly = plans.firstOrNull { it.days == 30 }?.finalPrice
            // A plan opened from an ad comes first and highlighted.
            plans.sortedBy { if (it.code == focusPlan) 0 else 1 }.forEach { addView(planCard(it, monthly), kit.spaced(10)) }
        }

        addView(kit.button(promo?.let { "کد ${it.first}: ${kit.n(it.second)}٪ تخفیف فعال است" } ?: "کد تخفیف یا هدیه دارید؟", ButtonKind.GHOST, RoyalIcon.STAR, 44) { promoDialog() }, kit.spaced(10))
        addView(kit.text("پرداخت فقط از راه ${StoreInfo.storeName} و با همان قیمتی انجام می‌شود که اینجا می‌بینید. ${if (ProState.plans.any { it.productType == "subscription" }) "اشتراک‌های «تمدید خودکار» را هر زمان از ${StoreInfo.storeName} لغو کنید؛ بقیهٔ پلن‌ها خودکار تمدید نمی‌شوند." else "اشتراک‌های زمان‌دار خودکار تمدید نمی‌شوند."} با همین حساب در هر نسخه‌ای (بازار، مایکت، سایت، وب، ویندوز و آیفون) وارد شوید؛ حساب در هر زمان روی یک دستگاه باز است و ورود روی دستگاه تازه، دستگاه قبلی را خودکار خارج می‌کند. خرید و تمدید در هر نسخه با روش پرداخت همان نسخه انجام می‌شود.",
            TextStyle.CAPTION, Royal.dim), kit.spaced(10))
        addView(kit.horizontal().apply {
            addView(kit.weight(kit.button("بازیابی خرید", ButtonKind.SECONDARY, RoyalIcon.UNDO, 44) { restore() }))
            addView(kit.hgap(8))
            addView(kit.weight(kit.button(if (ProState.loggedIn) "اشتراک من" else "ورود به حساب", ButtonKind.GHOST, RoyalIcon.USER, 44) {
                host.push(if (ProState.loggedIn) SubscriptionScreen(host) else AccountScreen(host))
            }))
        }, kit.spaced(16))
    }

    private fun benefits(): List<Triple<RoyalIcon, String, String>> {
        val out = mutableListOf(Triple(RoyalIcon.EYE_OFF, "بدون هیچ تبلیغی", "در هیچ صفحه‌ای تبلیغ نمایش داده نمی‌شود و کتابخانهٔ تبلیغات اجرا نمی‌شود."))
        val features = ProState.config.optJSONObject("features")
        val names = Features.titles
        val pro = features?.keys()?.asSequence()?.filter { features.optJSONObject(it)?.optString("mode") in setOf("pro", "limited") }?.toList().orEmpty()
        pro.take(6).forEach { k -> out += Triple(RoyalIcon.CROWN, names[k] ?: k, if (features?.optJSONObject(k)?.optString("mode") == "limited") "بدون محدودیت" else "فقط برای Pro") }
        out += Triple(RoyalIcon.SAVE, "با دستگاه تازه از دست نمی‌رود", "روی هر دستگاه و نسخه‌ای با همان حساب وارد شوید؛ دستگاه قبلی خودکار از حساب خارج می‌شود.")
        out += Triple(RoyalIcon.TIMER, "بدون اینترنت هم معتبر", "اشتراک تا چند روز بدون اتصال کار می‌کند و بعد با یک اتصال کوتاه تمدید می‌شود.")
        out += Triple(RoyalIcon.HEART, "حمایت از ساخت بازی‌های تازه", "هزینهٔ سرور و توسعهٔ دیوان از همین راه تأمین می‌شود.")
        return out
    }

    private fun planCard(p: PlanOffer, monthly: Int?): View = kit.panel(if (p.code == focusPlan) PanelStyle.SELECTED else if (p.badge != null) PanelStyle.RAISED else PanelStyle.NORMAL).apply {
        contentDescription = p.title
        addView(kit.horizontal().apply {
            addView(kit.text(p.title, TextStyle.HEADING, Royal.title, maxLines = 1), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (p.badge != null) addView(kit.badge(p.badge, p.badgeColor?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: Royal.gold, filled = true))
        })
        if (p.description.isNotBlank()) addView(kit.text(p.description, TextStyle.LABEL, Royal.muted))
        addView(kit.gap(6))
        val fromStore = p.storeOverride()
        val promoPrice = if (fromStore != null) null else promo?.takeIf { it.third == null || it.third == p.code }?.let { maxOf(1000, minOf(p.finalPrice, Math.round(p.finalPrice * (100 - it.second) / 100.0 / 100.0).toInt() * 100)) }
        if (fromStore != null) addView(kit.vertical().apply {
            // The store's own price wins: the buyer sees exactly what Bazaar or Myket will charge.
            addView(kit.text(fromStore, TextStyle.NUMBER_L, Royal.goldLight, maxLines = 1))
            addView(kit.text("قیمت ${StoreInfo.storeName}", TextStyle.CAPTION, Royal.dim))
        }) else if (promoPrice != null && promoPrice < p.finalPrice) addView(kit.horizontal().apply {
            addView(kit.text(toman(promoPrice), TextStyle.NUMBER_L, Royal.turquoiseLight, maxLines = 1))
            addView(kit.hgap(10))
            addView(kit.text(toman(p.finalPrice), TextStyle.LABEL, Royal.dim, maxLines = 1).apply { paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG })
            addView(kit.hgap(8))
            addView(kit.badge("کد ${kit.n(promo!!.second)}٪", Royal.turquoise, filled = true))
        }) else addView(kit.horizontal().apply {
            addView(kit.text(toman(p.finalPrice), TextStyle.NUMBER_L, Royal.goldLight, maxLines = 1))
            if (p.discountPercent > 0 && p.basePrice > p.finalPrice) {
                addView(kit.hgap(10))
                addView(kit.text(toman(p.basePrice), TextStyle.LABEL, Royal.dim, maxLines = 1).apply { paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG })
                addView(kit.hgap(8))
                addView(kit.badge("${kit.n(p.discountPercent)}٪ تخفیف", Royal.crimson, filled = true))
            }
        })
        val months = p.days?.let { it / 30 } ?: 0
        if (fromStore == null && monthly != null && months > 1 && p.finalPrice < monthly * months) {
            addView(kit.text("ماهی ${toman(p.finalPrice / months)} — ${kit.n(100 - p.finalPrice * 100 / (monthly * months))}٪ ارزان‌تر از خرید ماهانه", TextStyle.CAPTION, Royal.turquoise))
        }
        if (p.campaignEndsAt != null) addView(kit.text("تخفیف تا ${date(p.campaignEndsAt)}", TextStyle.CAPTION, Royal.crimsonLight))
        if (!p.renewalNote.isNullOrBlank()) addView(kit.text(p.renewalNote, TextStyle.CAPTION, Royal.dim))
        addView(kit.gap(8))
        val label = when {
            !p.purchasable && p.state == "price_sync_pending" -> "به‌زودی در ${StoreInfo.storeName}"
            !p.purchasable -> "فعلاً قابل خرید نیست"
            ProState.license.lifetime && ProState.isPro -> "مادام‌العمر دارید"
            p.productType == "subscription" -> "اشتراک با ${StoreInfo.storeName}"
            else -> "خرید با ${StoreInfo.storeName}"
        }
        val enabled = p.purchasable && !(ProState.license.lifetime && ProState.isPro)
        addView(kit.button(label, if (enabled) ButtonKind.PRIMARY else ButtonKind.GHOST, if (enabled) RoyalIcon.CROWN else null, 48) { if (enabled) buy(p) }.apply { isEnabled = enabled; alpha = if (enabled) 1f else 0.6f })
    }

    private fun buy(p: PlanOffer) {
        if (busy) return
        if (!ProState.loggedIn) {
            kit.dialog("حساب برای خرید", "اشتراک به حساب شما وصل می‌شود تا اگر گوشی عوض شد یا برنامه پاک شد، از بین نرود. ساخت حساب فقط نام کاربری و رمز می‌خواهد؛ شماره تلفن لازم نیست.", null, listOf(
                DialogAction("ساخت حساب یا ورود", ButtonKind.PRIMARY) { host.push(AccountScreen(host) { host.pop(); buy(p) }) },
                DialogAction("بعداً")
            )).show()
            return
        }
        busy = true
        ProSync.methods(p.code) { list, err ->
            busy = false
            when {
                list == null -> kit.dialog("خرید انجام نشد", err?.message ?: "ارتباط با سرور برقرار نشد.", null, listOf(DialogAction("باشه", ButtonKind.PRIMARY))).show()
                // Never a list of switched-off gateways: one polite sentence.
                list.isEmpty() -> kit.dialog("خرید", "امکان خرید موقتاً در دسترس نیست. کمی بعد دوباره امتحان کنید.", null, listOf(DialogAction("باشه", ButtonKind.PRIMARY))).show()
                list.size == 1 -> pay(p, list[0])
                else -> kit.dialog("روش پرداخت", "${p.title} — ${toman(p.finalPrice)}", null,
                    list.map { m -> DialogAction(m.title, ButtonKind.PRIMARY) { pay(p, m) } } + DialogAction("انصراف")).show()
            }
        }
    }

    private fun pay(p: PlanOffer, m: ProSync.PayMethod) {
        if (m.kind == "web") {
            busy = true
            kit.toast("در حال باز کردن صفحهٔ امن پرداخت…")
            ProSync.webPay(m, p) { url, back, e ->
                busy = false
                if (url != null) host.push(PayBrowserScreen(host, url, m.title, back))
                else kit.dialog("خرید انجام نشد", e?.message ?: "ارتباط با سرور برقرار نشد.", null, listOf(DialogAction("باشه", ButtonKind.PRIMARY))).show()
            }
            return
        }
        storeBuy(p)
    }

    /** Back from the payment page: the server asks the gateway; a few quiet retries while it settles. */
    private fun checkPending(tries: Int = 0) {
        val order = ProState.pendingOrder ?: return
        ProSync.payStatus(order) { status, _ ->
            when (status) {
                "paid" -> { kit.toast("Pro فعال شد؛ سپاس از حمایت شما"); host.replace(SubscriptionScreen(host)) }
                "failed", "expired" -> kit.dialog("پرداخت انجام نشد", "اگر مبلغی کم شده، بانک طبق قانون ظرف ۷۲ ساعت آن را برمی‌گرداند.", null, listOf(DialogAction("باشه", ButtonKind.PRIMARY))).show()
                else -> if (tries < 4 && host.current === this) host.post(3000) { checkPending(tries + 1) }
            }
        }
    }

    private fun storeBuy(p: PlanOffer) {
        busy = true
        kit.toast("در حال آماده‌سازی خرید…")
        val code = promo?.takeIf { it.third == null || it.third == p.code }?.first
        ProSync.buy(host, p, code) { status, err ->
            busy = false
            when {
                err?.code == "canceled" -> kit.toast(err.message ?: "")
                err != null -> kit.dialog("خرید انجام نشد", err.message + if (err.code == "network") "\n\nاگر مبلغ کم شده، نگران نباشید: رسید خرید روی گوشی نگه داشته شده و با اتصال بعدی ثبت می‌شود." else "", null, listOf(DialogAction("باشه", ButtonKind.PRIMARY))).show()
                status == "pending" -> kit.dialog("در انتظار تأیید فروشگاه", "پرداخت دریافت شد و منتظر تأیید ${StoreInfo.storeName} است. به‌محض تأیید، Pro فعال می‌شود.", null, listOf(DialogAction("باشه", ButtonKind.PRIMARY))).show()
                else -> { kit.toast("Pro فعال شد؛ سپاس از حمایت شما"); host.replace(SubscriptionScreen(host)) }
            }
        }
    }

    private fun promoDialog() {
        if (!ProState.loggedIn) {
            kit.dialog("کد تخفیف یا هدیه", "برای استفاده از کد، اول وارد حساب شوید یا حساب بسازید (فقط نام کاربری و رمز).", null, listOf(
                DialogAction("ورود یا ساخت حساب", ButtonKind.PRIMARY) { host.push(AccountScreen(host) { host.pop(); promoDialog() }) },
                DialogAction("انصراف")
            )).show()
            return
        }
        val input = latinField("کد را وارد کنید")
        val err = errorLine()
        lateinit var d: android.app.Dialog
        d = kit.dialog("کد تخفیف یا هدیه", "کد هدیه بلافاصله فعال می‌شود؛ کد تخفیف روی قیمت پلن‌ها اعمال می‌شود.", kit.vertical().apply { addView(input, kit.spaced(8)); addView(err) }, listOf(
            DialogAction("ثبت کد", ButtonKind.PRIMARY, dismiss = false) {
                val code = input.text.toString().trim()
                if (code.isEmpty()) return@DialogAction
                err.show(null)
                ProSync.checkPromo(code) { info, e ->
                    when {
                        e != null -> err.show(e.message)
                        info!!.optString("kind") == "discount" && !info.optBoolean("usable_here", true) -> err.show(info.optString("note"))
                        info.optString("kind") == "discount" -> {
                            promo = Triple(info.optString("code"), info.optInt("percent"), info.optString("plan").takeIf { it.isNotBlank() && it != "null" })
                            d.dismiss(); kit.toast(info.optString("title")); host.refresh()
                        }
                        else -> ProSync.redeemPromo(code) { got, e2 ->
                            if (e2 != null) err.show(e2.message) else { d.dismiss(); kit.toast((got?.optString("title") ?: "هدیه") + " فعال شد"); host.refresh() }
                        }
                    }
                }
            },
            DialogAction("انصراف")
        ))
        d.show()
    }

    private fun restore() {
        if (!ProState.loggedIn) { host.push(AccountScreen(host) { host.pop(); restore() }); return }
        kit.toast("در حال بررسی خریدها…")
        ProSync.restore(host) { n, err ->
            if (err != null) kit.dialog("بازیابی خرید", err.message, null, listOf(DialogAction("باشه", ButtonKind.PRIMARY))).show()
            else kit.toast(if (n > 0) "${kit.n(n)} خرید بازیابی شد" else if (ProState.isPro) "اشتراک شما به‌روز است" else "خرید تازه‌ای پیدا نشد")
            host.refresh()
        }
    }
}

object Features {
    val titles = mapOf(
        "shalam" to "داوری شلم", "menfi" to "داوری منفی", "hezartaii" to "هزارتایی", "dolo" to "دو لو گشنیز",
        "mafia" to "راوی مافیا", "mafia_scenarios" to "سناریوهای مافیا", "mafia_custom" to "ساخت و ویرایش سناریو و نقش مافیا",
        "mafia_last_cards" to "کارت حرکت آخر", "mafia_groups" to "گروه‌های بازی مافیا", "mafia_report" to "گزارش بازی مافیا",
        "mafia_audio" to "موسیقی و صدای راوی", "panto" to "پانتومیم", "panto_modes" to "نوع مسابقهٔ پانتومیم", "panto_words" to "کلمات من در پانتومیم",
        "spy" to "جاسوس", "spy_words" to "کلمات من در جاسوس", "leagues" to "لیگ‌ها", "rankings" to "رنک‌بندی", "profiles" to "کارنامهٔ افراد و تیم‌ها",
        "history" to "تاریخچه", "charts" to "نمودار و آمار کامل بازی", "photos" to "عکس از گالری", "themes" to "سبک‌های ظاهری",
        "backup" to "پشتیبان‌گیری", "cloud_backup" to "پشتیبان ابری", "share" to "اشتراک تصویر", "deck_calc" to "ماشین‌حساب ورق"
    )
}

/**
 * Free / Pro rules from the panel, with explicit safe defaults before the first successful fetch.
 * Whole modules are checked when their page opens ([Screen.featureKey]); single items (a mafia scenario,
 * a last-move card, a pantomime mode, a style) with [itemLocked].
 */
object Gate {
    /** Pro-only items of a module for this user (empty for Pro). */
    fun proItems(key: String): Set<String> = ProState.access(key).items

    /** True when this item (scenario, card, mode, style) needs Pro: ticked on its own, or the whole module is Pro. */
    fun itemLocked(key: String, id: String): Boolean = ProState.access(key).let { it.allItems || id in it.items }

    /** Opens the Pro page explaining why, for a locked item. */
    fun lockedItem(screen: Screen, title: String) = screen.host.push(ProScreen(screen.host, "«$title» از امکانات Pro است."))

    /** Small crown badge for locked items in lists. */
    fun badge(screen: Screen): View = screen.kit.badge("Pro", Royal.gold, filled = true)

    /** True when the page may open; otherwise explains (or opens the Pro page) and returns false. */
    fun allows(host: MainActivity, screen: Screen): Boolean {
        if (screen.mafiaArea && !ProState.mafiaAllowed) {
            host.push(MafiaAccessScreen(host))
            return false
        }
        val key = screen.featureKey ?: return true
        val current = host.current ?: return true
        var ok = false
        open(current, key, screen.featureUsed) { ok = true }
        return ok
    }

    fun open(screen: Screen, key: String, used: Int = -1, onAllowed: () -> Unit) {
        if ((key == "mafia" || key.startsWith("mafia_")) && !ProState.mafiaAllowed) {
            screen.host.push(MafiaAccessScreen(screen.host))
            return
        }
        val (mode, allowed, limit) = ProState.feature(key)
        val title = Features.titles[key] ?: key
        when {
            mode == "disabled" -> screen.kit.toast("«$title» موقتاً در دسترس نیست.")
            !allowed && mode == "gift" -> screen.kit.toast("«$title» فعلاً فقط برای کاربران ویژه باز است.")
            !allowed -> screen.host.push(ProScreen(screen.host, "«$title» از قابلیت‌های Pro است."))
            limit != null && used >= 0 && used >= limit -> screen.host.push(ProScreen(screen.host, "در نسخهٔ رایگان تا ${screen.kit.n(limit)} مورد از «$title» می‌توانید بسازید. آنچه ساخته‌اید همیشه می‌ماند."))
            else -> onAllowed()
        }
    }
}

// ================================================================ account: login, register, recover
class AccountScreen(host: MainActivity, private val then: (() -> Unit)? = null) : Screen(host) {
    private var mode = 1 // 0 login, 1 register, 2 recover

    override fun build(): View = scaffold("حساب کاربری", "اشتراک Pro و تست مافیا") {
        addView(kit.text("بازی‌های امتیازی رایگان به حساب نیاز ندارند. مافیا با اشتراک Pro فعال است؛ پس از ثبت‌نام و دریافت موفق تنظیمات سرور یک فرصت تست ۱۰ دقیقه‌ای آغاز می‌شود. پس از شروع، زمان با خروج از برنامه متوقف نمی‌شود. هر دستگاه فقط یک ثبت‌نام دارد؛ برای حساب قبلی از ورود یا بازیابی استفاده کنید. ایمیل اختیاری است.", TextStyle.LABEL, Royal.muted), kit.spaced(10))
        val chips = listOf("ورود", "ساخت حساب", "بازیابی").mapIndexed { i, l -> kit.chip(l, i == mode) { mode = i; host.refresh() } }
        addView(kit.chipRail(chips, mode), kit.spaced(10))
        val err = errorLine()
        when (mode) {
            0 -> {
                val user = latinField("نام کاربری")
                val pass = secretField("رمز عبور")
                addView(kit.panel().apply {
                    addView(user, kit.spaced(8)); addView(pass, kit.spaced(8)); addView(err, kit.spaced(6))
                    addView(kit.button("ورود", ButtonKind.PRIMARY, RoyalIcon.USER, 50) {
                        err.show(null)
                        ProSync.login(user.text.toString().trim(), pass.text.toString()) { e -> if (e != null) err.show(e.message) else done("خوش آمدید") }
                    })
                }, kit.spaced(10))
                addView(kit.button("رمز را فراموش کرده‌ام", ButtonKind.GHOST, null, 42) { mode = 2; host.refresh() })
            }
            1 -> {
                val user = latinField("نام کاربری (حروف لاتین، رقم، نقطه یا _)")
                val pass = secretField("رمز عبور (دست‌کم ۸ نویسه با حرف و رقم)")
                val pass2 = secretField("تکرار رمز")
                val email = latinField("ایمیل (اختیاری)")
                addView(kit.panel().apply {
                    addView(user, kit.spaced(8)); addView(pass, kit.spaced(8)); addView(pass2, kit.spaced(8)); addView(email, kit.spaced(8)); addView(err, kit.spaced(6))
                    addView(kit.button("ساخت حساب", ButtonKind.PRIMARY, RoyalIcon.PLUS, 50) {
                        err.show(null)
                        if (pass.text.toString() != pass2.text.toString()) { err.show("تکرار رمز با رمز یکی نیست."); return@button }
                        ProSync.register(user.text.toString().trim(), pass.text.toString(), email.text.toString().trim()) { key, e ->
                            if (e != null) err.show(e.message)
                            else host.replace(RecoveryKeyScreen(host, key.orEmpty(), then))
                        }
                    })
                }, kit.spaced(10))
            }
            else -> {
                val user = latinField("نام کاربری")
                val key = latinField("کلید بازیابی  XXXX-XXXX-…")
                val pass = secretField("رمز تازه")
                addView(kit.panel().apply {
                    addView(user, kit.spaced(8)); addView(key, kit.spaced(8)); addView(pass, kit.spaced(8)); addView(err, kit.spaced(6))
                    addView(kit.button("بازیابی حساب", ButtonKind.PRIMARY, RoyalIcon.UNDO, 50) {
                        err.show(null)
                        ProSync.recover(user.text.toString().trim(), key.text.toString(), pass.text.toString()) { newKey, e ->
                            if (e != null) err.show(e.message)
                            else host.replace(RecoveryKeyScreen(host, newKey.orEmpty(), then, recovered = true))
                        }
                    })
                }, kit.spaced(10))
                addView(kit.text("پس از بازیابی، از همهٔ گوشی‌ها خارج می‌شوید و یک کلید بازیابی تازه می‌گیرید. اگر کلید را هم ندارید، درخواست پشتیبانی بفرستید و شمارهٔ سفارش خرید را بنویسید.", TextStyle.CAPTION, Royal.dim), kit.spaced(8))
                addView(kit.button("درخواست پشتیبانی", ButtonKind.GHOST, RoyalIcon.EDIT, 42) { supportDialog(this@AccountScreen, "recovery") })
            }
        }
    }

    private fun done(msg: String) {
        kit.toast(msg)
        val next = then
        if (next != null) next() else host.replace(SubscriptionScreen(host))
    }
}

/** Shows the recovery key once; the player confirms having written it down. */
class RecoveryKeyScreen(host: MainActivity, private val key: String, private val then: (() -> Unit)?, private val recovered: Boolean = false) : Screen(host) {
    private var confirmed = false

    override fun onBack(): Boolean { if (!confirmed) { kit.toast("اول کلید را یادداشت کنید و تأیید بزنید."); return true }; return false }

    override fun build(): View = scaffold("کلید بازیابی", if (recovered) "حساب بازیابی شد" else "حساب ساخته شد") {
        addView(kit.panel(PanelStyle.RAISED).apply {
            addView(kit.text("این کلید تنها راه بازگرداندن حساب در صورت فراموشی رمز است. آن را روی کاغذ بنویسید یا جای امنی ذخیره کنید. دوباره نمایش داده نمی‌شود.", TextStyle.BODY, Royal.ivory))
            addView(kit.gap(12))
            addView(kit.text(key, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                textDirection = View.TEXT_DIRECTION_LTR
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setTextIsSelectable(true)
                letterSpacing = 0.04f
                setPadding(kit.dp(8), kit.dp(12), kit.dp(8), kit.dp(12))
                background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                contentDescription = "کلید بازیابی"
            })
            addView(kit.gap(10))
            addView(kit.button("کپی کلید", ButtonKind.SECONDARY, RoyalIcon.CARDS, 44) {
                (host.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("recovery", key))
                kit.toast("کلید کپی شد؛ آن را جای امنی نگه دارید")
            })
        }, kit.spaced(12))
        addView(kit.text("هیچ‌کس از طرف دیوان امتیاز این کلید را از شما نمی‌خواهد.", TextStyle.CAPTION, Royal.crimsonLight), kit.spaced(12))
        addView(kit.button("کلید را یادداشت کردم", ButtonKind.PRIMARY, RoyalIcon.CHECK, 50) {
            confirmed = true
            val next = then
            if (next != null) next() else host.replace(SubscriptionScreen(host))
        })
    }
}

// ================================================================ «اشتراک من»
class SubscriptionScreen(host: MainActivity) : Screen(host) {
    private val listener = watchPro()
    override fun onShow() { ProState.listeners += listener; if (ProState.loggedIn) ProSync.refreshAccount() }
    override fun onHide() { ProState.listeners -= listener }

    override fun build(): View = scaffold("اشتراک من", ProState.username?.let { "حساب $it" } ?: "حساب کاربری") {
        if (!ProState.loggedIn) {
            addView(kit.panel(PanelStyle.RAISED).apply {
                addView(kit.text("وارد حساب نشده‌اید", TextStyle.HEADING, Royal.title))
                addView(kit.text("همهٔ بازی‌ها بدون حساب کار می‌کنند. اگر Pro خریده‌اید یا می‌خواهید بخرید، وارد شوید یا حساب بسازید.", TextStyle.BODY, Royal.muted))
                addView(kit.gap(10))
                addView(kit.button("ورود یا ساخت حساب", ButtonKind.PRIMARY, RoyalIcon.USER, 48) { host.push(AccountScreen(host)) })
                addView(kit.gap(6))
                addView(kit.button("دیدن پلن‌ها", ButtonKind.SECONDARY, RoyalIcon.CROWN, 44) { host.push(ProScreen(host)) })
            }, kit.spaced(12))
            return@scaffold
        }
        val now = ProState.now()
        val lic = ProState.license
        val acct = ProState.accountJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val pro = ProState.isPro

        elsewherePanel()?.let { addView(it, kit.spaced(12)) }
        addView(kit.panel(if (pro) PanelStyle.SUCCESS else PanelStyle.RAISED).apply {
            addView(kit.horizontal().apply {
                addView(kit.text(if (pro) (if (lic.lifetime) "اشتراک Pro مادام‌العمر — فعال" else "Pro فعال") else "نسخهٔ رایگان", TextStyle.TITLE, Royal.title), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(kit.badge(if (pro) "بدون تبلیغ" else "رایگان", if (pro) Royal.turquoise else Royal.dim, filled = pro))
            })
            if (pro && !lic.lifetime) {
                addView(kit.text("تا ${date(lic.expiresAt)} — ${kit.n(lic.daysLeft(now) ?: 0)} روز باقی‌مانده", TextStyle.BODY, Royal.ivory))
                val total = 30.0.coerceAtLeast(((lic.expiresAt ?: now) - now) / 86400.0)
                addView(kit.gap(6))
                addView(kit.progress(((lic.daysLeft(now) ?: 0) / total).toFloat().coerceIn(0.03f, 1f), Royal.turquoise))
            }
            if (lic.needsSync(now)) addView(kit.text("برای ادامهٔ Pro یک بار به اینترنت وصل شوید.", TextStyle.LABEL, Royal.alert))
            else if (pro && lic.offlineLimitShown()) addView(kit.text("بدون اینترنت تا ${date(lic.offlineUntil)} معتبر است؛ پیش از آن یک بار به اینترنت وصل شوید.", TextStyle.CAPTION, Royal.muted))
            if (ProState.clock.tampered()) addView(kit.text("ساعت گوشی عقب‌تر از زمان واقعی است؛ تاریخ‌ها با زمان درست حساب می‌شوند.", TextStyle.CAPTION, Royal.crimsonLight))
            if (!pro && ProState.adFreeUntil > now) addView(kit.text("بدون تبلیغ تا ${JalaliDate.format(ProState.adFreeUntil * 1000, settings.general.persianDigits)}", TextStyle.CAPTION, Royal.turquoise))
            addView(kit.gap(10))
            addView(kit.horizontal().apply {
                addView(kit.weight(kit.button(if (pro && !lic.lifetime) "تمدید" else if (pro) "پلن‌ها" else "خرید Pro", ButtonKind.PRIMARY, RoyalIcon.CROWN, 46) { host.push(ProScreen(host)) }))
                addView(kit.hgap(8))
                addView(kit.weight(kit.button("بازیابی خرید", ButtonKind.SECONDARY, RoyalIcon.UNDO, 46) { restore() }))
            })
        }, kit.spaced(12))

        addView(kit.listGroup(listOf(
            kit.listRow(kit.iconChip(RoyalIcon.USER), "حساب", ProState.username),
            kit.listRow(kit.iconChip(RoyalIcon.CARDS), "فروشگاه این نسخه", StoreInfo.storeName),
            kit.listRow(kit.iconChip(RoyalIcon.CHECK), "دستگاه اشتراک", if (ProState.license.elsewhere != null) "روی «${ProState.license.elsewhere}» فعال است" else if (pro) "همین دستگاه؛ ورود روی دستگاه دیگر، این‌جا را خارج می‌کند" else "—"),
            kit.listRow(kit.iconChip(RoyalIcon.UNDO), "آخرین همگام‌سازی", if (ProState.lastSync() > 0) JalaliDate.format(ProState.lastSync() * 1000, settings.general.persianDigits) else "—") {
                kit.toast("در حال همگام‌سازی…")
                ProSync.refreshAccount { e -> kit.toast(e?.message ?: "به‌روز شد") }
            }
        ), "وضعیت"))

        val pending = ProState.pending
        if (pending.isNotEmpty()) addView(kit.panel(PanelStyle.DANGER).apply {
            addView(kit.text("${kit.n(pending.size)} خرید منتظر ثبت در سرور است؛ با اتصال به اینترنت خودکار ثبت می‌شود.", TextStyle.LABEL, Royal.ivory))
            addView(kit.gap(6))
            addView(kit.button("ثبت همین حالا", ButtonKind.SECONDARY, null, 40) { ProSync.retryPending(host); kit.toast("در حال ثبت…") })
        }, kit.spaced(12))

        val purchases = acct.optJSONArray("purchases")
        if (purchases != null && purchases.length() > 0) {
            val statusName = mapOf("verified" to "تأییدشده", "signature_verified" to "تأییدشده", "pending" to "در انتظار تأیید", "rejected" to "ردشده", "refunded" to "بازپرداخت", "revoked" to "باطل")
            addView(kit.listGroup((0 until purchases.length()).map { i ->
                val p = purchases.getJSONObject(i)
                val store = storeLabel(p.optString("store"))
                kit.listRow(kit.iconChip(RoyalIcon.CROWN), p.optString("plan_title"),
                    "$store  •  ${date(p.optLong("purchase_time"))}  •  ${statusName[p.optString("status")] ?: p.optString("status")}" + (p.optString("order_id").takeIf { it.isNotBlank() && it != "null" }?.let { "\nسفارش $it" } ?: ""),
                    if (!p.isNull("amount")) kit.text(toman(p.optInt("amount")), TextStyle.LABEL_BOLD, Royal.goldLight, maxLines = 1) else null)
            }, "سابقهٔ خرید"))
        }

        val sessions = acct.optJSONArray("sessions")
        if (sessions != null && sessions.length() > 0) {
            val currentId = acct.optLong("current_session")
            addView(kit.listGroup((0 until sessions.length()).map { i ->
                val s = sessions.getJSONObject(i)
                val mine = s.optLong("id") == currentId
                kit.listRow(kit.iconChip(RoyalIcon.USER), s.optString("device").ifBlank { "دستگاه" } + if (mine) " (همین دستگاه)" else "",
                    "آخرین فعالیت ${date(s.optLong("last_seen_at"))}" + storeLabel(s.optString("store")).let { if (it.isEmpty()) "" else "  •  $it" },
                    if (mine) null else kit.button("خروج", ButtonKind.GHOST, null, 34) {
                        kit.confirm("خروج این دستگاه", "این دستگاه از حساب خارج شود؟", "خروج", danger = true) {
                            ProSync.revokeSession(s.optLong("id"), false) { e -> kit.toast(e?.message ?: "خارج شد") }
                        }
                    })
            }, "دستگاه‌های وارد شده"))
        } else if (ProState.loggedIn) {
            ProSync.refreshAccount()
        }

        addView(kit.listGroup(listOf(
            kit.listRow(kit.iconChip(RoyalIcon.EYE), "کلید بازیابی تازه", "کلید قبلی باطل می‌شود") { passwordPrompt("کلید بازیابی تازه") { pw ->
                ProSync.newRecoveryKey(pw) { key, e -> if (e != null) kit.toast(e.message ?: "") else host.push(RecoveryKeyScreen(host, key.orEmpty(), { host.pop() })) }
            } },
            kit.listRow(kit.iconChip(RoyalIcon.EDIT), "تغییر رمز", "دستگاه‌های دیگر خارج می‌شوند") { changePassword() },
            kit.listRow(kit.iconChip(RoyalIcon.RULES), "پشتیبانی خرید و حساب", ProState.config.optString("support_text").ifBlank { null }) { supportDialog(this@SubscriptionScreen, "purchase") },
            kit.listRow(kit.iconChip(RoyalIcon.BACK, tint = Royal.crimson), "خروج از حساب", "بازی‌ها و تاریخچه روی گوشی می‌مانند", titleColor = Royal.crimsonLight) {
                kit.confirm("خروج از حساب", "پس از خروج، تا ورود دوباره Pro روی این گوشی فعال نیست. بازی‌ها و تاریخچه پاک نمی‌شوند.", "خروج", danger = true) {
                    ProSync.logout { host.refresh() }
                }
            }
        ), "حساب"))
    }

    private fun passwordPrompt(title: String, onValue: (String) -> Unit) {
        val input = secretField("رمز عبور")
        lateinit var d: android.app.Dialog
        d = kit.dialog(title, "برای ادامه رمز عبور را وارد کنید.", kit.vertical().apply { addView(input, kit.fill()) }, listOf(
            DialogAction("ادامه", ButtonKind.PRIMARY, dismiss = false) { if (input.text.isNotEmpty()) { d.dismiss(); onValue(input.text.toString()) } },
            DialogAction("انصراف")
        ))
        d.show()
    }

    private fun changePassword() {
        val old = secretField("رمز فعلی")
        val new = secretField("رمز تازه (۸+ نویسه با حرف و رقم)")
        val err = errorLine()
        lateinit var d: android.app.Dialog
        d = kit.dialog("تغییر رمز", null, kit.vertical().apply { addView(old, kit.spaced(8)); addView(new, kit.spaced(8)); addView(err) }, listOf(
            DialogAction("تغییر", ButtonKind.PRIMARY, dismiss = false) {
                ProSync.changePassword(old.text.toString(), new.text.toString()) { e -> if (e != null) err.show(e.message) else { d.dismiss(); kit.toast("رمز عوض شد") } }
            },
            DialogAction("انصراف")
        ))
        d.show()
    }

    private fun restore() {
        kit.toast("در حال بررسی خریدها…")
        ProSync.restore(host) { n, err ->
            kit.toast(err?.message ?: if (n > 0) "${kit.n(n)} خرید بازیابی شد" else "خرید تازه‌ای پیدا نشد")
            host.refresh()
        }
    }
}

/** Support request (purchase problems, lost account) — works without logging in. */
fun supportDialog(screen: Screen, kind: String) {
    val kit = screen.kit
    val user = screen.run { latinField("نام کاربری حساب") }.apply { setText(ProState.username.orEmpty()) }
    val order = screen.run { latinField("شمارهٔ سفارش (از سابقهٔ خرید ${StoreInfo.storeName})") }
    val contact = kit.field("راه تماس برای پاسخ (ایمیل یا شماره)")
    val msg = kit.field("شرح مشکل").apply { setSingleLine(false); minLines = 3; gravity = Gravity.TOP or Gravity.START }
    val err = screen.run { errorLine() }
    lateinit var d: android.app.Dialog
    d = kit.dialog("درخواست پشتیبانی", "برای حساب گم‌شده، شمارهٔ سفارش خرید بهترین راه اثبات مالکیت است.", kit.vertical().apply {
        listOf(user, order, contact, msg).forEach { addView(it, kit.spaced(8)) }
        addView(err)
    }, listOf(
        DialogAction("ارسال", ButtonKind.PRIMARY, dismiss = false) {
            ProSync.support(kind, msg.text.toString().trim(), contact.text.toString().trim(), order.text.toString().trim(), user.text.toString().trim()) { id, e ->
                if (e != null) err.show(e.message) else { d.dismiss(); kit.toast("درخواست ${kit.n(id ?: 0)} ثبت شد") }
            }
        },
        DialogAction("انصراف")
    ))
    d.show()
}

/** Last-move cards the player may use under the panel's rules. */
object MafiaCardRules {
    fun usable(c: MafiaLastCard): Boolean = !Gate.itemLocked("mafia_last_cards", c.name)
}
