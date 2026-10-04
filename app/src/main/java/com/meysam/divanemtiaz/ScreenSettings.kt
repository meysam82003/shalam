package com.meysam.divanemtiaz

import android.view.View
import android.widget.LinearLayout

class SettingsScreen(host: MainActivity, private var tab: Int = TAB_GENERAL) : Screen(host) {
    private var shalamTab = 0

    override fun build(): View = scaffold(title = "تنظیمات", subtitle = "همهٔ تغییرات خودکار ذخیره می‌شوند") {
        addView(kit.grid(4, listOf("عمومی", "شلم", "منفی", "هزارتایی").mapIndexed { i, label ->
            kit.chip(label, tab == i, ButtonKind.CHIP_GOLD) { tab = i; host.refresh() }
        }, 6), kit.spaced(8))
        when (tab) {
            TAB_SHALAM -> shalam(this)
            TAB_MENFI -> {
                addView(kit.section("قوانین منفی", RoyalIcon.EYE_OFF))
                editMenfi(this, settings.menfi.rules()) { r ->
                    host.updateSettings { it.copy(menfi = MenfiSettings(r.hands, r.hidden, r.threeSuccess, r.threeFailure, r.highWins)) }
                }
                resetButton(this, "منفی") { it.copy(menfi = MenfiSettings()) }
            }
            TAB_HEZAR -> {
                addView(kit.section("قوانین هزارتایی", RoyalIcon.TROPHY))
                editHezar(this, settings.hezar.rules()) { r ->
                    host.updateSettings { it.copy(hezar = it.hezar.copy(target = r.target, rounds = r.rounds, zeroPenalty = r.zeroPenalty)) }
                }
                addView(kit.stepperRow("تعداد پیش‌فرض بازیکنان", "در آماده‌سازی بازی قابل تغییر است", settings.hezar.players, 2, 6, 1) { v ->
                    host.updateSettings { it.copy(hezar = it.hezar.copy(players = v)) }
                })
                resetButton(this, "هزارتایی") { it.copy(hezar = HezarSettings()) }
            }
            else -> general(this)
        }
    }

    private fun general(parent: LinearLayout) {
        val g = settings.general
        parent.addView(kit.section("تجربهٔ برنامه", RoyalIcon.SETTINGS))
        parent.addView(kit.switchRow("بازخورد لمسی", "لرزش کوتاه هنگام لمس دکمه‌ها", g.haptic) { v -> host.updateSettings { it.copy(general = it.general.copy(haptic = v)) } })
        parent.addView(kit.switchRow("روشن ماندن صفحه نمایش", "صفحه در طول بازی خاموش نمی‌شود", g.keepScreenAwake) { v -> host.updateSettings { it.copy(general = it.general.copy(keepScreenAwake = v)) } })
        parent.addView(kit.switchRow("اعداد فارسی", "نمایش همهٔ امتیازها با رقم فارسی", g.persianDigits) { v ->
            host.updateSettings { it.copy(general = it.general.copy(persianDigits = v)) }
            host.refresh()
        })
        parent.addView(kit.switchRow("متن درشت", "افزایش اندازهٔ همهٔ متن‌ها", g.largeText) { v ->
            host.updateSettings { it.copy(general = it.general.copy(largeText = v)) }
            host.refresh()
        })
        parent.addView(kit.section("نام پیش‌فرض تیم‌ها", RoyalIcon.PLAYERS))
        parent.addView(kit.settingRow("تیم اول", null, kit.text(g.defaultTeam1, TextStyle.BODY_BOLD, Royal.goldLight)) {
            kit.textPrompt("نام پیش‌فرض تیم اول", g.defaultTeam1) { v -> host.updateSettings { it.copy(general = it.general.copy(defaultTeam1 = v)) }; host.refresh() }
        })
        parent.addView(kit.settingRow("تیم دوم", null, kit.text(g.defaultTeam2, TextStyle.BODY_BOLD, Royal.goldLight)) {
            kit.textPrompt("نام پیش‌فرض تیم دوم", g.defaultTeam2) { v -> host.updateSettings { it.copy(general = it.general.copy(defaultTeam2 = v)) }; host.refresh() }
        })
        resetButton(parent, "عمومی") { it.copy(general = GeneralSettings()) }
    }

    private fun shalam(parent: LinearLayout) {
        parent.addView(kit.grid(3, listOf("تنظیمات کلی", "بازی بدون جوکر", "بازی با جوکر").mapIndexed { i, label ->
            kit.chip(label, shalamTab == i) { shalamTab = i; host.refresh() }
        }, 6), kit.spaced(4))
        val s = settings.shalam
        when (shalamTab) {
            1, 2 -> {
                val joker = shalamTab == 2
                parent.addView(kit.section(if (joker) "شلم با جوکر" else "شلم بدون جوکر", RoyalIcon.SPADE))
                editShalamMode(parent, s.mode(joker), true) { m ->
                    host.updateSettings { it.copy(shalam = if (joker) it.shalam.copy(joker = m) else it.shalam.copy(noJoker = m)) }
                }
                resetButton(parent, if (joker) "با جوکر" else "بدون جوکر") {
                    it.copy(shalam = if (joker) it.shalam.copy(joker = ShalamModeRules.JOKER) else it.shalam.copy(noJoker = ShalamModeRules.NO_JOKER))
                }
            }
            else -> {
                parent.addView(kit.section("تنظیمات کلی شلم", RoyalIcon.CROWN))
                parent.addView(kit.choiceRow("حالت پیش‌فرض بازی جدید", null, listOf(0 to "بدون جوکر", 1 to "با جوکر"), if (s.defaultJoker) 1 else 0) { v ->
                    host.updateSettings { it.copy(shalam = it.shalam.copy(defaultJoker = v == 1)) }
                })
                parent.addView(kit.choiceRow("نوع بازی", "برچسب پیش‌فرض بازی‌های جدید", listOf(
                    DealType.TWELVE to DealType.title(DealType.TWELVE),
                    DealType.TWELVE_POSITIVE_ONLY to DealType.title(DealType.TWELVE_POSITIVE_ONLY),
                    DealType.FOUR to DealType.title(DealType.FOUR)
                ), s.dealType) { v -> host.updateSettings { it.copy(shalam = it.shalam.copy(dealType = v)) } })
                editShalamCommon(parent, s.rules(false)) { r ->
                    host.updateSettings {
                        it.copy(shalam = it.shalam.copy(
                            doubleType = r.doubleType,
                            contractOnly = r.contractOnly,
                            highLimitEnabled = r.highLimitEnabled,
                            highLimit = r.highLimit,
                            loserPointsAboveLimit = r.loserPointsAboveLimit
                        ))
                    }
                }
                parent.addView(kit.switchRow("استفاده از صفحه کلید برای ثبت امتیازات", "به سبک قدیم ثبت امتیازات از طریق نوشتن امتیاز توسط صفحه کلید انجام می‌شود؛ در غیر این صورت اعداد آماده نمایش داده می‌شوند.", s.keyboardInput) { v ->
                    host.updateSettings { it.copy(shalam = it.shalam.copy(keyboardInput = v)) }
                })
                resetButton(parent, "کلی شلم") {
                    val d = ShalamSettings()
                    it.copy(shalam = it.shalam.copy(
                        dealType = d.dealType, doubleType = d.doubleType, contractOnly = d.contractOnly,
                        highLimitEnabled = d.highLimitEnabled, highLimit = d.highLimit, loserPointsAboveLimit = d.loserPointsAboveLimit,
                        keyboardInput = d.keyboardInput, defaultJoker = d.defaultJoker
                    ))
                }
            }
        }
    }

    private fun resetButton(parent: LinearLayout, name: String, reset: (AppSettings) -> AppSettings) {
        parent.addView(kit.gap(8))
        parent.addView(kit.button("بازگشت به پیش‌فرض‌های $name", ButtonKind.GHOST, RoyalIcon.UNDO, 46) {
            kit.confirm("بازگشت به پیش‌فرض", "تنظیمات بخش «$name» به مقدار اولیه برگردد؟", "بازگشت") {
                host.updateSettings(reset)
                host.refresh()
            }
        })
    }

    companion object {
        const val TAB_GENERAL = 0
        const val TAB_SHALAM = 1
        const val TAB_MENFI = 2
        const val TAB_HEZAR = 3
    }
}

/** Shared editor rows; used for global settings and for the rules of a running game. */
fun Screen.editShalamCommon(parent: LinearLayout, rules: ShalamRules, update: (ShalamRules) -> Unit) {
    var current = rules
    fun set(next: ShalamRules, redraw: Boolean = false) {
        current = next
        update(next)
        if (redraw) host.refresh()
    }
    parent.addView(kit.choiceRow(
        "دوبل",
        "با انتخاب پرسش در بازی، پس از هر بار دوبل، می‌توانید دوبل مثبت یا منفی را انتخاب کنید",
        listOf(DoubleType.POSITIVE to "مثبت", DoubleType.NEGATIVE to "منفی", DoubleType.ASK to "پرسش در بازی", DoubleType.DISABLED to "غیرفعال"),
        current.doubleType
    ) { set(current.copy(doubleType = it)) })
    parent.addView(kit.switchRow("محاسبه امتیاز تعهد", "تیم حاکم در صورت برنده شدن فقط امتیاز تعهد را می‌گیرد.", current.contractOnly) {
        set(current.copy(contractOnly = it))
    })
    parent.addView(kit.switchRow("محدودیت ثبت امتیاز بالا", "تیمی که امتیاز آن بالای امتیاز مشخص شده باشد، فقط در صورت برنده شدن امتیاز خواهد گرفت.", current.highLimitEnabled) {
        set(current.copy(highLimitEnabled = it), redraw = true)
    })
    if (current.highLimitEnabled) {
        parent.addView(kit.stepperRow("حد امتیاز", null, current.highLimit, 100, 5000, 50) { set(current.copy(highLimit = it)) })
        parent.addView(kit.switchRow("محاسبه شدن امتیاز مثبت، با منفی شدن حریف", "اگر حاکمِ حریف شکست بخورد، تیم بالای حد امتیاز خود را می‌گیرد.", current.loserPointsAboveLimit) {
            set(current.copy(loserPointsAboveLimit = it))
        })
    }
}

fun Screen.editShalamMode(parent: LinearLayout, mode: ShalamModeRules, showDefaultEnd: Boolean, update: (ShalamModeRules) -> Unit) {
    var current = mode
    fun set(next: ShalamModeRules, redraw: Boolean = false) {
        current = next
        update(next)
        if (redraw) host.refresh()
    }
    val base = kit.n(current.maxPoints * 2)
    parent.addView(kit.stepperRow("حداکثر امتیاز یک دست", "بدون جوکر ${kit.n(165)} • با جوکر ${kit.n(200)}", current.maxPoints, 100, 400, 5) {
        set(current.copy(maxPoints = it), redraw = true)
    })
    if (showDefaultEnd) {
        parent.addView(kit.stepperRow("امتیاز پایان بازی (پیش‌فرض)", "در شروع هر بازی قابل تغییر است", current.defaultEndPoint, 400, 2800, 5) {
            set(current.copy(defaultEndPoint = it))
        })
    }
    parent.addView(kit.choiceRow(
        "بدست آوردن کلیه امتیازات یک دست",
        "وقتی حاکم بدون خواندن شلم همهٔ امتیازها را بگیرد",
        listOf(CollectAll.POINT to "امتیاز (${kit.n(current.maxPoints)})", CollectAll.DOUBLE to "دوبل تعهد", CollectAll.SHELEM to "شلم", CollectAll.CUSTOM to "دلخواه"),
        current.collectAll
    ) { set(current.copy(collectAll = it), redraw = true) })
    if (current.collectAll == CollectAll.CUSTOM) {
        parent.addView(kit.stepperRow("امتیاز دلخواه", null, current.collectAllCustom, 0, 2000, 5) { set(current.copy(collectAllCustom = it)) })
    }
    parent.addView(kit.stepperRow("حد امتیاز محاسبه دوبل", "اگر حاکم شکست بخورد و حریف دست‌کم این امتیاز را بگیرد، دوبل محاسبه می‌شود", current.doubleLimit, 0, current.maxPoints, 5) {
        set(current.copy(doubleLimit = it))
    })
    parent.addView(kit.switchRow("محاسبه شلم برای تعهد", "در صورت خواندن ${kit.n(current.maxPoints)} به صورت اتوماتیک شلم محاسبه می‌شود", current.maxBidIsShelem) {
        set(current.copy(maxBidIsShelem = it))
    })
    parent.addView(kit.choiceRow(
        "نحوه محاسبه امتیاز شلم",
        "در شکست شلم، امتیاز حاکم منفی می‌شود",
        listOf(
            ShelemMode.CONTRACTOR to "$base امتیاز برای حاکم",
            ShelemMode.OPPONENT_NEGATIVE to "$base امتیاز منفی برای تیم بازنده",
            ShelemMode.CONTRACTOR_AND_OPPONENT to "$base امتیاز تیم حاکم و محاسبه امتیاز تیم بازنده",
            ShelemMode.CUSTOM to "تنظیم امتیازات دلخواه"
        ),
        current.shelemMode
    ) { set(current.copy(shelemMode = it), redraw = true) })
    if (current.shelemMode == ShelemMode.CUSTOM) {
        parent.addView(kit.stepperRow("امتیاز تیم حاکم", null, current.shelemContractor, -3000, 3000, 5, { kit.signed(it) }) { set(current.copy(shelemContractor = it)) })
        parent.addView(kit.stepperRow("امتیاز تیم حریف", null, current.shelemOpponent, -3000, 3000, 5, { kit.signed(it) }) { set(current.copy(shelemOpponent = it)) })
    }
    parent.addView(kit.choiceRow(
        "دوبل امتیازات شلم",
        "امتیاز «شلم دوبل»",
        listOf(DoubleShelemMode.DOUBLE to "دوبل امتیاز شلم", DoubleShelemMode.CUSTOM to "تنظیم امتیازات دلخواه"),
        current.doubleShelemMode
    ) { set(current.copy(doubleShelemMode = it), redraw = true) })
    if (current.doubleShelemMode == DoubleShelemMode.CUSTOM) {
        parent.addView(kit.stepperRow("امتیاز مثبت", "برد شلم دوبل", current.doubleShelemWin, 0, 5000, 5, { kit.signed(it) }) { set(current.copy(doubleShelemWin = it)) })
        parent.addView(kit.stepperRow("امتیاز منفی", "باخت شلم دوبل", current.doubleShelemLose, -5000, 0, 5, { kit.signed(it) }) { set(current.copy(doubleShelemLose = it)) })
    }
    parent.addView(kit.switchRow("پایان بازی با اختلاف", "پس از رسیدن اختلاف امتیازات به حد مشخص شده به صورت اتوماتیک بازی پایان می‌یابد.", current.endWithDiff) {
        set(current.copy(endWithDiff = it), redraw = true)
    })
    if (current.endWithDiff) {
        parent.addView(kit.stepperRow("حد اختلاف امتیازات", null, current.endDiff, 50, 5000, 50) { set(current.copy(endDiff = it)) })
    }
}

fun Screen.editMenfi(parent: LinearLayout, rules: MenfiRules, update: (MenfiRules) -> Unit) {
    var current = rules
    fun set(next: MenfiRules) {
        current = next
        update(next)
    }
    parent.addView(kit.stepperRow("تعداد دست‌ها", "بازی پس از این تعداد دست تمام می‌شود", current.hands, 1, 40, 1) { set(current.copy(hands = it)) })
    parent.addView(kit.switchRow("پنهان‌بودن جمع امتیاز", "تا زمان نمایش نتیجه، جمع و امتیاز دست‌ها دیده نشود", current.hidden) { set(current.copy(hidden = it)) })
    parent.addView(kit.stepperRow("امتیاز گرفتن عدد ۳", "سایر اعداد: ۱۳ منهای عدد", current.threeSuccess, 0, 200, 1, { kit.signed(it) }) { set(current.copy(threeSuccess = it)) })
    parent.addView(kit.stepperRow("امتیاز نگرفتن عدد ۳", "سایر اعداد: منفیِ (۱۳ منهای عدد)", current.threeFailure, -200, 0, 1, { kit.signed(it) }) { set(current.copy(threeFailure = it)) })
    parent.addView(kit.choiceRow("برندهٔ بازی", null, listOf(1 to "بیشترین جمع امتیاز", 0 to "کمترین جمع امتیاز"), if (current.highWins) 1 else 0) {
        set(current.copy(highWins = it == 1))
    })
}

fun Screen.editHezar(parent: LinearLayout, rules: HezarRules, update: (HezarRules) -> Unit) {
    var current = rules
    fun set(next: HezarRules) {
        current = next
        update(next)
    }
    parent.addView(kit.stepperRow("امتیاز هدف", "رسیدن به این امتیاز بازی را تمام می‌کند", current.target, 100, 10000, 50) { set(current.copy(target = it)) })
    parent.addView(kit.stepperRow("تعداد دورها", "صفر یعنی بدون محدودیت دور", current.rounds, 0, 100, 1, { if (it == 0) "نامحدود" else kit.n(it) }) { set(current.copy(rounds = it)) })
    parent.addView(kit.stepperRow("جریمهٔ صفر", "امتیاز صفر در یک دور با این مقدار ثبت می‌شود", current.zeroPenalty, -1000, 0, 10, { kit.signed(it) }) { set(current.copy(zeroPenalty = it)) })
}

/** Rules of one running game; every change recalculates all of its hands. */
class SessionRulesScreen(host: MainActivity, private val session: GameSession) : Screen(host) {
    override val sessionId: Long get() = session.id

    private fun apply(rules: GameRules) {
        session.rules = rules
        SessionOps.commit(host, session)
    }

    override fun build(): View = scaffold(
        title = "قوانین این بازی",
        subtitle = "تغییرات فقط روی همین بازی اعمال و همهٔ دست‌ها دوباره محاسبه می‌شوند",
        bottom = kit.button("شرح کامل قوانین", ButtonKind.SECONDARY, RoyalIcon.RULES) { host.push(RulesScreen(host, session)) }
    ) {
        when (session.game) {
            GameType.SHALAM -> {
                val r = session.rules.shalam
                addView(kit.section(if (r.joker) "شلم با جوکر" else "شلم بدون جوکر", RoyalIcon.SPADE))
                addView(kit.stepperRow("امتیاز پایان بازی", "از ${kit.n(400)} تا ${kit.n(2800)}", r.endPoint, 400, 2800, 5) { v ->
                    apply(session.rules.copy(shalam = session.rules.shalam.copy(endPoint = v)))
                })
                editShalamCommon(this, r) { next ->
                    apply(session.rules.copy(shalam = session.rules.shalam.copy(
                        doubleType = next.doubleType,
                        contractOnly = next.contractOnly,
                        highLimitEnabled = next.highLimitEnabled,
                        highLimit = next.highLimit,
                        loserPointsAboveLimit = next.loserPointsAboveLimit
                    )))
                }
                editShalamMode(this, r.mode, false) { m -> apply(session.rules.copy(shalam = session.rules.shalam.copy(mode = m))) }
            }
            GameType.MENFI -> {
                addView(kit.section("قوانین منفی", RoyalIcon.EYE_OFF))
                editMenfi(this, session.rules.menfi) { m -> apply(session.rules.copy(menfi = m)) }
            }
            GameType.HEZARTAII -> {
                addView(kit.section("قوانین هزارتایی", RoyalIcon.TROPHY))
                editHezar(this, session.rules.hezar) { h -> apply(session.rules.copy(hezar = h)) }
            }
        }
    }
}
