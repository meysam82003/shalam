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
        if (r.scoring == MenfiScoring.LEGACY) "روش امتیاز قدیمی نسخهٔ ۳"
        else "گرفتن +عدد و نگرفتن −عدد • عدد ۱۰: ${kit.signed(MenfiEngine.successScore(10, r))} / ${kit.signed(MenfiEngine.failureScore(10, r))}",
        "جمع ۱۴ یعنی حتماً یک تیم منفی است؛ جمع ۱۵ و بیشتر یعنی ممکن است هر دو منفی شوند",
        if (r.highWins) "برنده: بیشترین جمع امتیاز" else "برنده: کمترین جمع امتیاز"
    )

    fun leagueLines(format: Int, c: LeagueSettings, kit: RoyalKit): List<String> = if (format == LeagueFormat.ROUND_ROBIN) listOfNotNull(
        "همهٔ تیم‌ها " + (if (c.doubleRoundRobin) "دو بار (رفت و برگشت)" else "یک بار") + " با هم بازی می‌کنند؛ در هر دور هیچ تیمی دو بازی ندارد.",
        "امتیاز جدول: برد ${kit.n(c.pointsWin)}، مساوی ${kit.n(c.pointsDraw)}، باخت ${kit.n(c.pointsLoss)}.",
        "رتبه‌بندی: امتیاز جدول، سپس تفاضل امتیاز بازی‌ها، سپس امتیاز زده، سپس تعداد برد.",
        if (c.finalAfterTable) "پس از پایان جدول، دو تیم اول فینال بازی می‌کنند؛ برندهٔ فینال باید ${kit.n(c.winsNeeded)} بار ببرد." else "صدر جدول قهرمان است؛ اگر دو تیم اول در همهٔ معیارها برابر باشند، فینال برگزار می‌شود.",
        "هر بازی یک بازی کامل با همهٔ قوانین است و در تاریخچه می‌ماند؛ ویرایش یک بازی، جدول را هم به‌روز می‌کند."
    ) else listOf(
        "تیم‌ها دوبه‌دو رویارو می‌شوند (به ترتیب قرعه). در هر رویارویی تیمی برنده است که ${kit.n(c.winsNeeded)} بار حریف را ببرد.",
        if (c.winsNeeded == 2) "اگر هر تیم یک بار ببرد (۱–۱)، بازی سوم «فینال» همین دو تیم است و برنده‌اش بالا می‌رود." else "تا رسیدن یکی از دو تیم به ${kit.n(c.winsNeeded)} برد بازی ادامه دارد.",
        "بازی مساوی برد حساب نمی‌شود و تکرار می‌شود.",
        "برندهٔ رویارویی ۱ با برندهٔ رویارویی ۲ و … بازی می‌کند تا یک قهرمان بماند.",
        "اگر تعداد تیم‌ها فرد باشد، یک تیم با قرعه استراحت می‌کند و مستقیم بالا می‌رود؛ هیچ تیمی دو مرحلهٔ پشت سر هم استراحت نمی‌گیرد.",
        "هر بازی یک بازی کامل با همهٔ قوانین است و در تاریخچه می‌ماند؛ ویرایش یک بازی، نتیجهٔ لیگ را هم به‌روز می‌کند."
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
        GameType.DOLO -> dolo(rules.dolo, kit)
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
        val legacy = r.scoring == MenfiScoring.LEGACY
        val scoreLines = if (legacy) listOf(
            "این بازی با روش قدیمی نسخهٔ ۳ ثبت شده است: عدد ۳ ← ${kit.signed(r.threeSuccess)} / ${kit.signed(r.threeFailure)}؛ سایر اعداد ← ±(۱۳ منهای عدد).",
            "از «قوانین این بازی» می‌توانید آن را به جدول امتیاز جدید تغییر دهید."
        ) else listOf(
            "تیمی که دست‌کم به عدد خودش برسد «گرفته» است و امتیاز گرفتن را می‌گیرد؛ وگرنه امتیاز نگرفتن.",
            "پیش‌فرض: گرفتن +عدد و نگرفتن −عدد؛ عدد ۱۰: گرفتن +۲۰ و نگرفتن −۱۰. همه از تنظیمات قابل تغییر است."
        ) + MenfiEngine.readyNumbers.map { n ->
            "عدد ${kit.n(n)}: گرفتن ${kit.signed(MenfiEngine.successScore(n, r))} • نگرفتن ${kit.signed(MenfiEngine.failureScore(n, r))}"
        }
        val example = { a: Int, b: Int ->
            "${kit.n(a)} و ${kit.n(b)}: " + MenfiEngine.outcomes(a, b, r).joinToString("  •  ") { "${kit.signed(it.teamAScore)} | ${kit.signed(it.teamBScore)}" }
        }
        return listOf(
            "روش بازی" to listOf(
                "بازی دو تیمی است و ${kit.n(r.hands)} دست دارد؛ هر دست ${kit.n(13)} دست‌برد دارد.",
                "در هر دست هر تیم عدد خود را از ${kit.n(3)} تا ${kit.n(13)} " + (if (r.hidden) "پنهانی " else "") + "ثبت می‌کند."
            ),
            "امتیاز هر عدد" to scoreLines,
            "حالت‌های رخ‌داده" to (if (legacy) listOf("سه حالت: هر دو گرفتند، فقط تیم اول، فقط تیم دوم.") else listOf(
                "جمع دو عدد ${kit.n(13)} یا کمتر: هر دو گرفتند، فقط تیم اول، یا فقط تیم دوم.",
                "جمع دو عدد ${kit.n(14)}: حتماً یک تیم منفی است؛ فقط تیم اول یا فقط تیم دوم.",
                "جمع ${kit.n(15)} و بیشتر: فقط تیم اول، فقط تیم دوم، یا هر دو منفی.",
                "نمونه: " + example(6, 8),
                "نمونه: " + example(5, 10),
                "نمونه: " + example(3, 10)
            )) + listOf(
                "داور همیشه می‌تواند امتیاز دست را دستی با علامت + یا − ثبت کند.",
                "تا پیش از تأیید هیچ امتیازی ثبت نمی‌شود و هر دست قابل ویرایش و حذف است."
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
            "رقابت انفرادی از ${kit.n(2)} بازیکن به بالا و بدون سقف تعداد است.",
            "در هر دور امتیاز هر بازیکن وارد می‌شود؛ با دکمهٔ ± امتیاز منفی هم ثبت می‌شود.",
            "ماشین‌حساب کارت هر بازیکن: ۲ تا ۹ = ۵، ۱۰ تا شاه = ۱۰، تک = ۲۰، جوکر = ۴۰ (از تنظیمات قابل تغییر)."
        ),
        "ورق و پخش" to listOf(
            "ماشین‌حساب ورق با تعداد نفرات، دسته‌ها و جوکرها، ورق هر نفر و ترتیب پخش (مثلاً ۶ + ۴ + ۴) را می‌گوید.",
            "اگر تا ۶ برگ کم بیاید، ۶ کارت از زیر دسته برداشته و خوب بُر زده می‌شود؛ بیشتر از آن، دستهٔ دیگری لازم است."
        ),
        "جریمهٔ صفر" to listOf("بازیکنی که در یک دور صفر بگیرد با ${kit.signed(r.zeroPenalty)} ثبت می‌شود."),
        "پایان و رتبه‌بندی" to listOf(
            "بازی با رسیدن یک بازیکن به ${kit.n(r.target)} امتیاز تمام می‌شود" + if (r.rounds > 0) " یا پس از ${kit.n(r.rounds)} دور، هر کدام زودتر برسد." else ".",
            "رتبه‌بندی بر اساس بیشترین جمع امتیاز است.",
            "هر دور قابل ویرایش و حذف است و جمع‌ها دوباره محاسبه می‌شوند؛ در میانهٔ بازی هم می‌توان بازیکن اضافه کرد."
        )
    )

    private fun dolo(r: DoloRules, kit: RoyalKit): List<Pair<String, List<String>>> = listOf(
        "روش بازی" to listOf(
            "بازی انفرادی از ${kit.n(3)} نفر به بالاست. دو لو گشنیز (دوی گشنیز) دست هر کس باشد، حکم را تعیین می‌کند.",
            "ورق‌ها مساوی پخش می‌شود؛ ماشین‌حساب ورق می‌گوید به هر نفر چند برگ برسد و چند برگ کنار گذاشته شود."
        ),
        "حداقل خواندن" to listOf(
            "۴ نفر و کمتر: ${kit.n(r.minUpTo4)} • ۵ و ۶ نفر: ${kit.n(r.min5to6)} • ۷ نفر به بالا: ${kit.n(r.minFrom7)}.",
            "هیچ‌کس کمتر از حداقل نمی‌خواند؛ داور در هر دست می‌تواند حداقل را تغییر دهد.",
            "ثبت عددها: «همه حداقل» یا «همه حداقل به جز…» و بالا بردن عدد کسانی که بیشتر خواندند."
        ),
        "امتیاز هر دست" to listOf(
            "ثبت نتیجه: «همه گرفتند» یا «همه گرفتند به جز…»؛ تیک کسانی که نگرفتند برداشته می‌شود.",
            "گرفتن: +عدد" + (if (r.madeMultiplier != 1) " × ${kit.n(r.madeMultiplier)}" else "") + " • نگرفتن: −عدد" + (if (r.failMultiplier != 1) " × ${kit.n(r.failMultiplier)}" else "") + ".",
            "داور همیشه می‌تواند امتیاز هر نفر را دستی با + یا − ثبت کند."
        ),
        "حذف" to listOf(
            "پس از هر ${kit.n(r.eliminateEvery)} دور (" + (if (r.handsPerRound > 0) "هر دور ${kit.n(r.handsPerRound)} دست" else "هر دور به تعداد بازیکنان باقی‌مانده دست") + ")، ${kit.n(r.eliminateCount)} نفر کم‌امتیازترین حذف می‌شود؛ داور با تیک تأیید می‌کند.",
            "اگر پایینی‌ها مساوی باشند، ${kit.n(r.tieExtraHands)} دست دیگر بازی می‌شود و بعد دوباره بررسی می‌شود.",
            if (r.resetAfterElimination) "پس از هر حذف امتیاز مرحلهٔ بعد از صفر حساب می‌شود." else "امتیازها در کل بازی جمع می‌شوند.",
            "بازی تا ماندن یک نفر ادامه دارد؛ او برنده است."
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
            addView(kit.grid(4, GameType.values().map { g -> kit.chip(g.title, g == game, ButtonKind.CHIP_GOLD) { game = g; host.refresh() } }, 5), kit.spaced(8))
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
            addView(GameSealView(host, game), android.widget.LinearLayout.LayoutParams(kit.dp(64), kit.dp(64)))
            addView(kit.text(game.title, TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
        })
        RulesText.full(game, rules, kit).forEach { (title, lines) ->
            addView(kit.section(title, RoyalIcon.STAR))
            addView(kit.panel(PanelStyle.FLAT, 14).apply {
                layoutParams = kit.spaced(6)
                lines.forEach { addView(kit.text("•  $it", TextStyle.LABEL, Royal.ivory).apply { setPadding(0, kit.dp(2), 0, kit.dp(2)) }) }
            })
        }
        if (session == null) {
            addView(kit.gap(8))
            addView(kit.button("تغییر این قوانین در تنظیمات", ButtonKind.GHOST, RoyalIcon.SLIDERS, 46) {
                host.push(SettingsScreen(host, when (game) {
                    GameType.SHALAM -> SettingsScreen.TAB_SHALAM
                    GameType.MENFI -> SettingsScreen.TAB_MENFI
                    GameType.HEZARTAII -> SettingsScreen.TAB_HEZAR
                    GameType.DOLO -> SettingsScreen.TAB_DOLO
                }))
            })
        }
    }
}
