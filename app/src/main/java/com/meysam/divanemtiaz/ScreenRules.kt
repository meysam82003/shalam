package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View

/** Plain-language rules generated from the active rule values, so the text always matches the scoring. */
object RulesText {
    fun shalamSummary(r: ShalamRules, kit: RoyalKit): List<String> {
        val m = r.mode
        return listOf(
            "هر دست ${kit.n(m.maxPoints)} امتیاز • پایان در ${kit.n(r.endPoint)}" + if (m.endWithDiff) " یا اختلاف ${kit.n(m.endDiff)}" else "",
            "دوبل: ${doubleName(r.doubleType)} • حد دوبل ${kit.n(m.doubleLimit)}",
            "شلم: ${shelemName(m, kit)} • شلم دوبل: ${if (m.doubleShelemMode == DoubleShelemMode.DOUBLE) "دو برابر" else "${kit.signed(m.doubleShelemWin)} / ${kit.signed(m.doubleShelemLose)}"}",
            if (r.contractOnly) "حاکم در برد فقط امتیاز تعهد را می‌گیرد" else "حاکم در برد امتیاز واقعی خود را می‌گیرد"
        ) + if (r.highLimitEnabled) listOf("محدودیت امتیاز بالا از ${kit.n(r.highLimit)}") else emptyList()
    }

    fun menfiSummary(r: MenfiRules, kit: RoyalKit): List<String> = listOf(
        "${kit.n(r.hands)} دست • اعداد ${kit.n(3)} تا ${kit.n(13)}",
        "عدد ۳: ${kit.signed(r.threeSuccess)} / ${kit.signed(r.threeFailure)} • سایر اعداد: ±(۱۳ − عدد)",
        if (r.highWins) "برنده: بیشترین جمع امتیاز" else "برنده: کمترین جمع امتیاز"
    )

    fun doubleName(type: Int): String = when (type) {
        DoubleType.POSITIVE -> "مثبت"
        DoubleType.NEGATIVE -> "منفی"
        DoubleType.ASK -> "پرسش در بازی"
        else -> "غیرفعال"
    }

    fun shelemName(m: ShalamModeRules, kit: RoyalKit): String {
        val base = kit.n(m.maxPoints * 2)
        return when (m.shelemMode) {
            ShelemMode.CONTRACTOR -> "$base امتیاز برای حاکم"
            ShelemMode.OPPONENT_NEGATIVE -> "$base امتیاز منفی برای تیم بازنده"
            ShelemMode.CONTRACTOR_AND_OPPONENT -> "$base برای حاکم و امتیاز تیم بازنده"
            else -> "دلخواه (${kit.signed(m.shelemContractor)} / ${kit.signed(m.shelemOpponent)})"
        }
    }

    fun full(game: GameType, rules: GameRules, kit: RoyalKit): List<Pair<String, List<String>>> = when (game) {
        GameType.SHALAM -> shalam(rules.shalam, kit)
        GameType.MENFI -> menfi(rules.menfi, kit)
        GameType.HEZARTAII -> hezar(rules.hezar, kit)
    }

    private fun shalam(r: ShalamRules, kit: RoyalKit): List<Pair<String, List<String>>> {
        val m = r.mode
        val max = kit.n(m.maxPoints)
        val base = m.maxPoints * 2
        val example1 = ShalamEngine.scoreHand(135, 35, r)
        val example2 = ShalamEngine.scoreHand(135, 15, r)
        val end = mutableListOf("بازی چهارنفره و دو تیمی است و ${if (r.joker) "با جوکر" else "بدون جوکر"} انجام می‌شود.",
            "بازی وقتی تمام می‌شود که یک تیم به ${kit.n(r.endPoint)} امتیاز برسد (در شروع هر بازی بین ${kit.n(400)} تا ${kit.n(2800)} قابل انتخاب است).")
        if (m.endWithDiff) end += "اگر اختلاف امتیاز دو تیم به ${kit.n(m.endDiff)} برسد، بازی خودکار پایان می‌یابد."
        val doubleLines = mutableListOf("اگر تعهد حاکم شکست بخورد و حریف دست‌کم ${kit.n(m.doubleLimit)} امتیاز بگیرد، دست «دوبل» است.")
        doubleLines += when (r.doubleType) {
            DoubleType.POSITIVE -> "دوبل مثبت: حاکم منفیِ تعهد می‌گیرد و امتیاز حریف دو برابر ثبت می‌شود."
            DoubleType.NEGATIVE -> "دوبل منفی: حاکم دو برابرِ تعهد منفی می‌شود و حریف امتیاز خودش را می‌گیرد."
            DoubleType.ASK -> "پرسش در بازی: پس از هر دوبل، نوع آن (مثبت یا منفی) را انتخاب می‌کنید."
            else -> "دوبل غیرفعال است و شکست، همان منفیِ تعهد است."
        }
        val collect = when (m.collectAll) {
            CollectAll.DOUBLE -> "حاکم دو برابرِ تعهد خود را می‌گیرد."
            CollectAll.SHELEM -> "دست به‌صورت خودکار «شلم» محاسبه می‌شود."
            CollectAll.CUSTOM -> "حاکم ${kit.n(m.collectAllCustom)} امتیاز می‌گیرد."
            else -> "حاکم $max امتیاز می‌گیرد."
        }
        val shelemLines = mutableListOf("شلم یعنی تیم حاکم همهٔ امتیازهای دست را بگیرد؛ اگر حریف حتی یک امتیاز بگیرد، شلم شکست خورده است.")
        shelemLines += when (m.shelemMode) {
            ShelemMode.CONTRACTOR -> "برد: حاکم ${kit.signed(base)}، حریف صفر. باخت: حاکم ${kit.signed(-base)}."
            ShelemMode.OPPONENT_NEGATIVE -> "برد: تیم بازنده (حریف) ${kit.signed(-base)}. باخت: حریف ${kit.signed(base)} می‌گیرد."
            ShelemMode.CONTRACTOR_AND_OPPONENT -> "برد: حاکم ${kit.signed(base)}. باخت: حاکم ${kit.signed(-base)} و حریف امتیاز گرفته‌شدهٔ خودش را می‌گیرد."
            else -> "برد: حاکم ${kit.signed(m.shelemContractor)} و حریف ${kit.signed(m.shelemOpponent)}. باخت: حاکم ${kit.signed(-m.shelemContractor)} و حریف ${kit.signed(m.shelemOpponent)}."
        }
        shelemLines += if (m.doubleShelemMode == DoubleShelemMode.DOUBLE) "شلم دوبل: همهٔ امتیازهای شلم دو برابر می‌شود."
        else "شلم دوبل: برد ${kit.signed(m.doubleShelemWin)} و باخت ${kit.signed(m.doubleShelemLose)} برای حاکم."
        if (m.maxBidIsShelem) shelemLines += "خواندن $max به‌صورت خودکار شلم محاسبه می‌شود."
        val sections = mutableListOf(
            "هدف و پایان بازی" to end,
            "امتیاز هر دست" to listOf(
                "هر دست $max امتیاز دارد" + if (m.maxPoints == 165) ": هر دست‌برد ۵ امتیاز (۱۳ × ۵ = ۶۵)، هر آس و هر ده ۱۰ امتیاز و هر پنج ۵ امتیاز." else ".",
                "امتیازها همیشه مضرب ۵ هستند."
            ),
            "تعهد (امتیاز خوانده‌شده)" to listOf(
                "تیم حاکم عددی بین ${kit.n(ShalamEngine.MIN_PICKER_BID)} تا $max با گام ۵ می‌خواند؛ با صفحه‌کلید از ${kit.n(ShalamEngine.MIN_TYPED_BID)} نیز مجاز است و عدد کمتر از ۱۰۰ به‌صورت ۱xx خوانده می‌شود.",
                "«شلم» و «شلم دوبل» جدا انتخاب می‌شوند. پاس یعنی دست بی‌امتیاز.",
                "انتخاب خال حکم اختیاری است و در آمار بازی دیده می‌شود."
            ),
            "محاسبهٔ دست" to listOf(
                "پس از بازی فقط امتیاز تیم حریف وارد می‌شود. امتیاز واقعی حاکم = $max منهای امتیاز حریف.",
                "اگر تعهد + امتیاز حریف از $max بیشتر نشود، حاکم " + (if (r.contractOnly) "فقط امتیاز تعهد" else "امتیاز واقعی خود") + " را می‌گیرد و حریف هم امتیاز خودش را.",
                "در غیر این صورت حاکم منفیِ تعهد می‌گیرد و حریف امتیاز خودش را.",
                "نمونه: تعهد ${kit.n(135)} و حریف ${kit.n(35)} ← حاکم ${kit.signed(example1.contractor)}، حریف ${kit.signed(example1.opponent)}.",
                "نمونه: تعهد ${kit.n(135)} و حریف ${kit.n(15)} ← حاکم ${kit.signed(example2.contractor)}، حریف ${kit.signed(example2.opponent)}."
            ),
            "دوبل" to doubleLines,
            "گرفتن همهٔ امتیازها بدون شلم" to listOf("اگر حاکم بدون خواندن شلم همهٔ امتیازها را بگیرد: $collect"),
            "شلم" to shelemLines
        )
        if (r.highLimitEnabled) sections += "محدودیت امتیاز بالا" to listOf(
            "تیمی که جمع امتیازش به ${kit.n(r.highLimit)} یا بیشتر رسیده، فقط وقتی امتیاز می‌گیرد که خودش حاکم باشد و ببرد.",
            if (r.loserPointsAboveLimit) "اگر حاکمِ حریف شکست بخورد، این تیم امتیاز خودش را می‌گیرد." else "حتی با شکست حاکمِ حریف هم امتیازی به این تیم نمی‌رسد."
        )
        sections += "ثبت، ویرایش و حذف" to listOf(
            "هر دست قابل ویرایش و حذف است و جمع‌ها همیشه دوباره محاسبه می‌شوند.",
            "از منوی بازی می‌توان امتیاز تقلب یا جریمه برای یک تیم ثبت کرد و جمع امتیازها را مستقیم اصلاح کرد.",
            "بازی پس از هر دست خودکار ذخیره می‌شود و از تاریخچه قابل ادامه است."
        )
        return sections
    }

    private fun menfi(r: MenfiRules, kit: RoyalKit): List<Pair<String, List<String>>> {
        val example = MenfiEngine.outcomes(3, 10, r)
        return listOf(
            "روش بازی" to listOf(
                "بازی دو تیمی است و ${kit.n(r.hands)} دست دارد.",
                "در هر دست هر تیم عدد خود را از ${kit.n(3)} تا ${kit.n(13)} " + (if (r.hidden) "پنهانی " else "") + "ثبت می‌کند."
            ),
            "امتیاز هر عدد" to listOf(
                "گرفتن: عدد ۳ ← ${kit.signed(r.threeSuccess)}؛ سایر اعداد ← ۱۳ منهای عدد.",
                "نگرفتن: عدد ۳ ← ${kit.signed(r.threeFailure)}؛ سایر اعداد ← منفیِ (۱۳ منهای عدد)."
            ) + MenfiEngine.readyNumbers.map { n ->
                "عدد ${kit.n(n)}: گرفتن ${kit.signed(MenfiEngine.successScore(n, r))} • نگرفتن ${kit.signed(MenfiEngine.failureScore(n, r))}"
            },
            "سه حالت نتیجه" to listOf(
                "پس از ثبت هر دو عدد، یکی از سه حالت انتخاب می‌شود: هر دو تیم گرفتند، فقط تیم اول گرفت، یا فقط تیم دوم گرفت.",
                "تا پیش از تأیید هیچ امتیازی ثبت نمی‌شود.",
                "نمونهٔ ${kit.n(3)} و ${kit.n(10)}: " + example.joinToString("  •  ") { "${kit.signed(it.teamAScore)} | ${kit.signed(it.teamBScore)}" }
            ),
            "پایان و برنده" to listOf(
                "بازی پس از ${kit.n(r.hands)} دست تمام می‌شود.",
                "برنده تیمی است که " + (if (r.highWins) "بیشترین" else "کمترین") + " جمع امتیاز را دارد.",
                if (r.hidden) "جمع امتیازها تا پایان بازی پنهان است و فقط با تأیید داور نمایش داده می‌شود." else "جمع امتیازها در طول بازی دیده می‌شود."
            )
        )
    }

    private fun hezar(r: HezarRules, kit: RoyalKit): List<Pair<String, List<String>>> = listOf(
        "روش بازی" to listOf(
            "رقابت انفرادی ${kit.n(2)} تا ${kit.n(6)} بازیکن است.",
            "در هر دور امتیاز هر بازیکن وارد می‌شود؛ امتیاز منفی هم مجاز است."
        ),
        "جریمهٔ صفر" to listOf("بازیکنی که در یک دور صفر بگیرد با ${kit.signed(r.zeroPenalty)} ثبت می‌شود."),
        "پایان و رتبه‌بندی" to listOf(
            "بازی با رسیدن یک بازیکن به ${kit.n(r.target)} امتیاز تمام می‌شود" + if (r.rounds > 0) " یا پس از ${kit.n(r.rounds)} دور، هر کدام زودتر برسد." else ".",
            "رتبه‌بندی بر اساس بیشترین جمع امتیاز است.",
            "هر دور قابل ویرایش و حذف است و جمع‌ها دوباره محاسبه می‌شوند."
        )
    )
}

/** Rule book; shows a running game's own rules when a session is given. */
class RulesScreen(host: MainActivity, private val session: GameSession?, initial: GameType? = null) : Screen(host) {
    private var game: GameType = session?.game ?: initial ?: GameType.SHALAM
    private var joker = settings.shalam.defaultJoker

    override fun build(): View = scaffold(
        title = if (session != null) "قوانین این بازی" else "قوانین بازی",
        subtitle = if (session != null) "بر اساس قوانین ثبت‌شده برای همین بازی" else "بر اساس تنظیمات فعلی برنامه"
    ) {
        if (session == null) {
            addView(kit.grid(3, GameType.values().map { g -> kit.chip(g.title, g == game, ButtonKind.CHIP_GOLD) { game = g; host.refresh() } }, 6), kit.spaced(8))
            if (game == GameType.SHALAM) {
                addView(kit.grid(2, listOf(
                    kit.chip("بدون جوکر", !joker) { joker = false; host.refresh() },
                    kit.chip("با جوکر", joker) { joker = true; host.refresh() }
                ), 6), kit.spaced(8))
            }
        }
        val rules = session?.rules ?: settings.rulesFor(game, joker)
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            layoutParams = kit.spaced(6)
            addView(GameSealView(host, game), android.widget.LinearLayout.LayoutParams(kit.dp(80), kit.dp(80)))
            addView(kit.text(game.title, TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
        })
        RulesText.full(game, rules, kit).forEach { (title, lines) ->
            addView(kit.section(title, RoyalIcon.STAR))
            addView(kit.panel(PanelStyle.FLAT, 14).apply {
                layoutParams = kit.spaced(6)
                lines.forEach { addView(kit.text("•  $it", TextStyle.BODY, Royal.ivory).apply { setPadding(0, kit.dp(2), 0, kit.dp(2)) }) }
            })
        }
        if (session == null) {
            addView(kit.gap(8))
            addView(kit.button("تغییر این قوانین در تنظیمات", ButtonKind.GHOST, RoyalIcon.SLIDERS, 46) {
                host.push(SettingsScreen(host, when (game) {
                    GameType.SHALAM -> SettingsScreen.TAB_SHALAM
                    GameType.MENFI -> SettingsScreen.TAB_MENFI
                    GameType.HEZARTAII -> SettingsScreen.TAB_HEZAR
                }))
            })
        }
    }
}
