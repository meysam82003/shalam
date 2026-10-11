package com.meysam.divanemtiaz

import org.json.JSONArray
import org.json.JSONObject
import com.meysam.divanemtiaz.MafiaRole.*

/** Game settings a scenario fixes (challenge, night phase, inquiries, vote thresholds, cards, mafia intro). */
data class ScenarioSetup(
    val challenge: Boolean = true,
    val noNight: Boolean = false,
    val queryCount: Int? = null,
    /** Votes needed to enter the defence, per number of players alive. */
    val thresholds: Map<Int, Int> = emptyMap(),
    /** "up" / "down": rounding of half the votes, or null for the narrator's setting. */
    val edge: String? = null,
    val noCards: Boolean = false,
    val noMafiaIntro: Boolean = false,
    val capo: Boolean? = null,
    /** The game opens with the intro night (no intro day before it). */
    val noIntroDay: Boolean = false
)

/**
 * A scenario: rules, the roles it uses (shown first in the role picker), reference line-ups per number of players,
 * its own role names and night order, and its last-move cards. Reference line-ups are suggestions, never limits.
 */
data class MafiaScenario(
    val id: String,
    val name: String,
    val summary: String,
    val rules: String,
    val pool: List<MafiaRole>,
    val presets: Map<Int, Map<MafiaRole, Int>> = emptyMap(),
    val names: Map<MafiaRole, String> = emptyMap(),
    val wake: Map<MafiaRole, Int> = emptyMap(),
    val cards: List<MafiaLastCard> = emptyList(),
    val setup: ScenarioSetup = ScenarioSetup(),
    val players: IntRange? = null,
    val custom: Boolean = false,
    /** Line-up for any number of players is built from the pool (mafia about a third). */
    val auto: Boolean = true,
    /** Scenarios of one family (e.g. the three پدرخوانده versions) are listed together. */
    val family: String? = null
) {
    /** Reference line-up for [n] players: an exact preset, or the nearest one trimmed / topped up. Only a suggestion. */
    fun lineup(n: Int): Map<MafiaRole, Int>? {
        if (n < 1) return null
        presets[n]?.let { return LinkedHashMap(it) }
        if (presets.isEmpty() || auto) return if (pool.isNotEmpty() && n >= 4) MafiaScenarios.autoLineup(pool, n) else null
        val below = presets.keys.filter { it < n }.maxOrNull()
        if (below != null) {
            val base = LinkedHashMap(presets.getValue(below))
            base[MafiaRole.CITIZEN] = (base[MafiaRole.CITIZEN] ?: 0) + (n - below)
            return base
        }
        return MafiaScenarios.trim(LinkedHashMap(presets.getValue(presets.keys.minOrNull()!!)), n)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("summary", summary); put("rules", rules)
        put("pool", JSONArray(pool.map { it.name }))
        put("presets", JSONObject().apply { presets.forEach { (n, m) -> put(n.toString(), JSONObject().apply { m.forEach { (r, c) -> put(r.name, c) } }) } })
        put("names", JSONObject().apply { names.forEach { (r, v) -> put(r.name, v) } })
        put("wakeOrder", JSONObject().apply { wake.forEach { (r, v) -> put(r.name, v) } })
        put("cards", JSONArray(cards.map { it.name }))
        put("challenge", setup.challenge); put("noNight", setup.noNight); setup.queryCount?.let { put("query", it) }
        setup.edge?.let { put("edge", it) }; put("noCards", setup.noCards); put("noMafiaIntro", setup.noMafiaIntro)
        setup.capo?.let { put("capo", it) }; put("noIntroDay", setup.noIntroDay)
        put("thresholds", setup.thresholds.entries.joinToString(",") { "${it.key}:${it.value}" })
        put("custom", custom); put("auto", auto); family?.let { put("family", it) }
    }

    companion object {
        private fun role(n: String) = MafiaRole.values().firstOrNull { it.name == n }

        private fun roleMap(o: JSONObject?): Map<MafiaRole, String> {
            val out = LinkedHashMap<MafiaRole, String>()
            o?.keys()?.forEach { k -> role(k)?.let { out[it] = o.optString(k) } }
            return out
        }

        fun fromJson(o: JSONObject): MafiaScenario {
            val pool = o.optJSONArray("pool")?.let { a -> List(a.length()) { a.optString(it) }.mapNotNull(::role) } ?: emptyList()
            val presets = LinkedHashMap<Int, Map<MafiaRole, Int>>()
            o.optJSONObject("presets")?.let { p -> p.keys().forEach { k -> k.toIntOrNull()?.let { n -> val m = LinkedHashMap<MafiaRole, Int>(); p.getJSONObject(k).let { x -> x.keys().forEach { r -> role(r)?.let { m[it] = x.optInt(r) } } }; presets[n] = m } } }
            val cards = o.optJSONArray("cards")?.let { a -> List(a.length()) { a.optString(it) }.mapNotNull { c -> MafiaLastCard.values().firstOrNull { it.name == c } } } ?: emptyList()
            val th = o.optString("thresholds").split(",").mapNotNull { e -> val p = e.split(":"); if (p.size == 2) p[0].toIntOrNull()?.let { a -> p[1].toIntOrNull()?.let { a to it } } else null }.toMap()
            return MafiaScenario(o.optString("id"), o.optString("name"), o.optString("summary"), o.optString("rules"), pool, presets,
                roleMap(o.optJSONObject("names")), roleMap(o.optJSONObject("wakeOrder")).mapNotNull { (r, v) -> v.toIntOrNull()?.let { r to it } }.toMap(),
                cards, ScenarioSetup(o.optBoolean("challenge", true), o.optBoolean("noNight"), if (o.has("query")) o.optInt("query") else null,
                    th, if (o.has("edge")) o.optString("edge") else null, o.optBoolean("noCards"), o.optBoolean("noMafiaIntro"), if (o.has("capo")) o.optBoolean("capo") else null, o.optBoolean("noIntroDay")),
                custom = o.optBoolean("custom", true), auto = o.optBoolean("auto", presets.isEmpty()), family = if (o.has("family")) o.optString("family") else null)
        }
    }
}

object MafiaScenarios {
    const val FREE = "free"

    /** Game extras a scenario sets (copied from the draft to the started game). */
    val KEYS = listOf("scenario", "noChallenge", "noNight", "thresholds", "queryCount", "edge", "noMafiaIntro", "scenarioCapo", "noIntroDay")

    private fun m(vararg p: Pair<MafiaRole, Int>): Map<MafiaRole, Int> = linkedMapOf(*p)

    /** Line-up from a pool: mafia about a third, special roles first, the rest simple citizens / mafia. */
    fun autoLineup(pool: List<MafiaRole>, n: Int): Map<MafiaRole, Int> {
        val out = LinkedHashMap<MafiaRole, Int>()
        val mafiaCount = maxOf(1, n / 3)
        val mafiaSpecial = pool.filter { it.side == MafiaSide.MAFIA && it != MAFIA }.distinct()
        val boss = mafiaSpecial.firstOrNull()
        val mafiaTake = mafiaSpecial.take(mafiaCount)
        mafiaTake.forEach { out[it] = 1 }
        if (mafiaCount > mafiaTake.size) out[MAFIA] = mafiaCount - mafiaTake.size
        if (boss == null && out.isEmpty()) out[MAFIA] = mafiaCount
        var left = n - mafiaCount
        val independents = pool.filter { it.side == MafiaSide.INDEPENDENT }.distinct()
        if (n >= 9) independents.take(1).forEach { if (left > 2) { out[it] = 1; left-- } }
        pool.filter { it.side == MafiaSide.CITY && it != CITIZEN }.distinct().forEach { if (left > maxOf(1, n / 5)) { out[it] = 1; left-- } }
        if (left > 0) out[CITIZEN] = (out[CITIZEN] ?: 0) + left
        return out
    }

    const val GODFATHER_FAMILY = "پدرخوانده"

    /** Godfather family deck: the shared cards plus only this version's «ذهن زیبا». */
    fun godfatherCards(mind: MafiaLastCard) = listOf(MafiaLastCard.SILENCE_OF_THE_LAMBS, MafiaLastCard.SHOW_IDENTITY, mind, MafiaLastCard.HANDCUFF, MafiaLastCard.FACE_OFF)

    private val beautifulMinds = setOf(MafiaLastCard.NOSTRADAMUS_BEAUTIFUL_MIND, MafiaLastCard.JACK_SPARROW_BEAUTIFUL_MIND, MafiaLastCard.SHERLOCK_HOLMES_BEAUTIFUL_MIND)

    /** Keeps only the «ذهن زیبا» of the roles in the game (a version's own card). */
    fun matchMind(cards: List<MafiaLastCard>, roles: Set<MafiaRole>): List<MafiaLastCard> =
        cards.filter { it !in beautifulMinds || it.requires == null || it.requires in roles }

    private fun godfatherRules(order: String, special: String, mind: String) = """• خانوادهٔ پدرخوانده؛ قوانین پایه در سه نسخه مشترک است و فقط نقش مستقل ویژه و کارت «ذهن زیبا» فرق دارد.
• سه ساید: شهروند، مافیا و مستقل ویژهٔ این نسخه.
• ترتیب بیداری شب: $order.
• پدرخوانده: رهبر شلیک؛ استعلام منفی؛ یک جلیقه در برابر لئون؛ به جای شلیک می‌تواند هر شب «حس ششم» بزند (حدس درست نقش = سلاخی که نجات و جلیقه جلویش را نمی‌گیرد و کنستانتین هم برنمی‌گرداند).
• ساول گودمن: فقط پس از خروج یک مافیا، یک بار به جای شلیک یک شهروند ساده را می‌خرد.
• ماتادور: هر شب توانایی یک بازیکن را برای همان چرخه مسدود می‌کند.
• دکتر واتسون: نجات شب؛ نجات خود محدود.
• لئون حرفه‌ای: دو تیر؛ شلیک به شهروند ← خود لئون خارج می‌شود؛ اولین شلیک روی پدرخوانده جلیقهٔ او را می‌گیرد.
• همشهری کین: یک بار یک نفر را نشان می‌دهد؛ اگر مافیا باشد روز بعد سایدش اعلام و کین شب بعد خارج می‌شود؛ شهروند یا نوستراداموس ← اتفاقی نمی‌افتد.
• کنستانتین: یک بار یک بازیکن خارج‌شده را برمی‌گرداند، به شرطی که هویتش افشا نشده و سلاخی نشده باشد.
• ترکیب مرجع ۱۱ نفره: ۳ مافیا، ۷ شهروند، ۱ مستقل؛ ۱۳ نفره: ۴ مافیا، ۸ شهروند، ۱ مستقل.
$special
• روز معارفه (۳۰ تا ۴۰ ثانیه برای هر نفر، بدون چالش) پیش از شب معارفه؛ روزهای بعد با چالش.
• سر صحبت هر روز دو نفر جلوتر می‌رود (اگر بیرون باشد نفر بعدی).
• خروج با رأی ← کشیدن کارت حرکت آخر. کارت‌های مرجع: سکوت بره‌ها، افشای هویت، $mind، دستبند، تغییر چهره.
• تیم مافیا در یک شب فقط یکی از شلیک، حس ششم یا خرید را انجام می‌دهد.
• تعداد نفرات و نقش‌ها قفل نیست: ترکیب مرجع فقط پیشنهاد است؛ راوی می‌تواند نقش کم، اضافه یا جایگزین کند و کارت‌ها را تغییر دهد."""

    /** Suggestion for fewer players than a reference line-up: drops simple citizens first, keeps mafia about a third. */
    fun trim(m: LinkedHashMap<MafiaRole, Int>, n: Int): Map<MafiaRole, Int> {
        while (m.values.sum() > n && m.values.sum() > 0) {
            val total = m.values.sum()
            val mafia = m.filter { it.key.side == MafiaSide.MAFIA }.values.sum()
            val mafiaKeys = m.keys.filter { it.side == MafiaSide.MAFIA && (m[it] ?: 0) > 0 }
            val victim = when {
                mafia > maxOf(1, (total - 1) / 3) && mafiaKeys.size > 1 -> if ((m[MAFIA] ?: 0) > 0) MAFIA else mafiaKeys.last()
                (m[CITIZEN] ?: 0) > 1 -> CITIZEN
                else -> m.keys.lastOrNull { it.side != MafiaSide.MAFIA && it != CITIZEN && (m[it] ?: 0) > 0 } ?: m.keys.last { (m[it] ?: 0) > 0 }
            }
            m[victim] = (m[victim] ?: 1) - 1
            if (m[victim] == 0) m.remove(victim)
        }
        return m
    }

    private val godfatherNames = mapOf(DOCTOR to "دکتر واتسون", PROFESSIONAL to "لئون")
    private val natoThresholds = mapOf(10 to 4, 9 to 4, 8 to 4, 7 to 3, 6 to 3, 5 to 2, 4 to 2)

    val builtin: List<MafiaScenario> = listOf(
        MafiaScenario("classic", "کلاسیک", "دو ساید شهر و مافیا با نقش‌های پایه",
            """• دو ساید: شهروند و مافیا؛ نسبت مافیا حدود یک‌سوم جمعیت.
• پس از تقسیم نقش، شب معارفه برای شناخت تیم مافیا اجرا می‌شود.
• روزها نوبت صحبت برابر است؛ ادعای نقش مجاز است ولی کارت واقعی نشان داده نمی‌شود.
• پدرخوانده تصمیم نهایی شلیک شب مافیا را می‌گیرد.
• کارآگاه هر شب یک نفر را بررسی می‌کند؛ نتیجهٔ رئیس مافیا منفی است.
• دکتر هر شب یک نفر را نجات می‌دهد و می‌تواند خودش را هم نجات دهد.
• رأی مرحلهٔ اول می‌تواند چند نفر را به دفاعیه ببرد؛ مرحلهٔ خروج جداست.
• صبح فقط نتیجهٔ مرگ یا نجات اعلام می‌شود.
• برد: حذف همهٔ مافیا ← شهروندان؛ برابری مافیا با شهروندان ← مافیا.""",
            listOf(GODFATHER, MAFIA, DOCTOR, DETECTIVE, CITIZEN)),
        MafiaScenario("classic_advanced", "کلاسیک پیشرفته", "خانوادهٔ پرنقش با مستقل‌های اختیاری",
            """• مافیا + شهروند + در صورت انتخاب، مستقل‌ها.
• تعداد نقش‌های منفی نزدیک یک‌سوم کل بازیکنان.
• استعلام وضعیت پیشنهادی: اختلاف = تعداد زنده‌ها − ۲ × منفی‌های زنده (اختلاف بالا سبز، میانی زرد، نزدیک قرمز).
• قانون اختیاری: یک بار در بازی پرسش «آیا مستقل در بازی باقی مانده؟».
• توانایی هر نقش همان تعریف اصلی خودش است.""",
            listOf(GODFATHER, DOCTOR_LECTOR, CHARLATAN, MATADOR, MAFIA, DOCTOR, DETECTIVE, PROFESSIONAL, PSYCHOLOGIST, DIE_HARD, MAYOR, SALESMAN, CITIZEN, JOKER, KILLER)),
        MafiaScenario("godfather", "پدرخوانده – نوستراداموس", "نقش ویژه: نوستراداموس • کارت: ذهن زیبا – نوستراداموس",
            godfatherRules("نوستراداموس در معارفه ← تیم مافیا ← دکتر واتسون ← لئون ← همشهری کین ← کنستانتین",
                "• نوستراداموس (مستقل این نسخه): اول از همه در شب معارفه بیدار می‌شود، سه نفر را انتخاب می‌کند و تعداد مافیای میان آن‌ها را می‌فهمد (پدرخوانده برای او شهروند حساب می‌شود). اگر ۲ مافیا یا بیشتر باشد مجبور است با مافیا بازی کند؛ در غیر این صورت آزادانه شهر یا مافیا را انتخاب می‌کند. شلیک شب روی او اثری ندارد ولی با رأی خارج می‌شود.",
                "ذهن زیبا – نوستراداموس"),
            listOf(GODFATHER, GOOD_MAN, MATADOR, NOSTRADAMUS, DOCTOR, PROFESSIONAL, CITIZEN_KANE, CONSTANTINE, CITIZEN, MAFIA),
            presets = mapOf(10 to m(GODFATHER to 1, GOOD_MAN to 1, MATADOR to 1, NOSTRADAMUS to 1, DOCTOR to 1, PROFESSIONAL to 1, CITIZEN_KANE to 1, CONSTANTINE to 1, CITIZEN to 2),
                11 to m(GODFATHER to 1, GOOD_MAN to 1, MATADOR to 1, NOSTRADAMUS to 1, DOCTOR to 1, PROFESSIONAL to 1, CITIZEN_KANE to 1, CONSTANTINE to 1, CITIZEN to 3),
                12 to m(GODFATHER to 1, GOOD_MAN to 1, MATADOR to 1, MAFIA to 1, NOSTRADAMUS to 1, DOCTOR to 1, PROFESSIONAL to 1, CITIZEN_KANE to 1, CONSTANTINE to 1, CITIZEN to 3),
                13 to m(GODFATHER to 1, GOOD_MAN to 1, MATADOR to 1, MAFIA to 1, NOSTRADAMUS to 1, DOCTOR to 1, PROFESSIONAL to 1, CITIZEN_KANE to 1, CONSTANTINE to 1, CITIZEN to 4)),
            names = godfatherNames, wake = mapOf(NOSTRADAMUS to 1, DOCTOR to 30, PROFESSIONAL to 31, CITIZEN_KANE to 32, CONSTANTINE to 33),
            cards = godfatherCards(MafiaLastCard.NOSTRADAMUS_BEAUTIFUL_MIND), setup = ScenarioSetup(), auto = false, family = GODFATHER_FAMILY),
        MafiaScenario("godfather_jack", "پدرخوانده – جک گنجشکه", "پدرخوانده – جک اسپارو • نقش ویژه: جک اسپارو • کارت: ذهن زیبا – جک اسپارو",
            godfatherRules("تیم مافیا ← دکتر واتسون ← لئون ← همشهری کین ← کنستانتین ← جک اسپارو",
                "• جک اسپارو / جک گنجشکه (مستقل این نسخه): از شب معارفه هر شب باید یک نفر را طلسم کند (تا همه طلسم نشده‌اند تکراری نه) و آخر از همه بیدار می‌شود. شلیک شب او را نمی‌کشد؛ اگر با رأی روز خارج شود نقشش اعلام می‌شود و در بازی می‌ماند. اگر طلسم‌شده تا ۲۴ ساعت خارج شود، جک هم همراهش خارج می‌شود؛ با حدس درست «ذهن زیبا» یا حس ششم پدرخوانده هم خارج می‌شود. برد جک: خروج همهٔ مافیا یا رسیدن جک به سه نفر آخر؛ در شمارش برابری مافیا و شهر حساب نمی‌شود.",
                "ذهن زیبا – جک اسپارو"),
            listOf(GODFATHER, GOOD_MAN, MATADOR, JACK_SPARROW, DOCTOR, PROFESSIONAL, CITIZEN_KANE, CONSTANTINE, CITIZEN, MAFIA),
            presets = mapOf(10 to m(GODFATHER to 1, GOOD_MAN to 1, MATADOR to 1, JACK_SPARROW to 1, DOCTOR to 1, PROFESSIONAL to 1, CITIZEN_KANE to 1, CONSTANTINE to 1, CITIZEN to 2)),
            names = godfatherNames, wake = mapOf(DOCTOR to 30, PROFESSIONAL to 31, CITIZEN_KANE to 32, CONSTANTINE to 33, JACK_SPARROW to 34),
            cards = godfatherCards(MafiaLastCard.JACK_SPARROW_BEAUTIFUL_MIND), auto = false, family = GODFATHER_FAMILY),
        MafiaScenario("godfather_sherlock", "پدرخوانده – شرلوک هلمز", "نقش ویژه: شرلوک هلمز • کارت: ذهن زیبا – شرلوک هلمز",
            godfatherRules("شرلوک هلمز ← تیم مافیا ← دکتر واتسون ← لئون ← همشهری کین ← کنستانتین",
                "• شرلوک هلمز (مستقل این نسخه): هر شب پیش از تیم مافیا بیدار می‌شود و نقش یک نفر را حدس می‌زند؛ حدس درست ← شرلوک نقش و ساید او را می‌گیرد و هدف سلاخی می‌شود. دو شب اول در برابر شلیک نامیراست و در روز با رأی خارج می‌شود. تیم مافیا می‌تواند توانایی او را بلاک کند. برد: رسیدن به سه نفر آخر (مرحلهٔ دست دادن).",
                "ذهن زیبا – شرلوک هلمز"),
            listOf(SHERLOCK_HOLMES, GODFATHER, GOOD_MAN, MATADOR, DOCTOR, PROFESSIONAL, CITIZEN_KANE, CONSTANTINE, CITIZEN, MAFIA),
            presets = mapOf(10 to m(SHERLOCK_HOLMES to 1, GODFATHER to 1, GOOD_MAN to 1, MATADOR to 1, DOCTOR to 1, PROFESSIONAL to 1, CITIZEN_KANE to 1, CONSTANTINE to 1, CITIZEN to 2),
                13 to m(SHERLOCK_HOLMES to 1, GODFATHER to 1, GOOD_MAN to 1, MATADOR to 1, MAFIA to 1, DOCTOR to 1, PROFESSIONAL to 1, CITIZEN_KANE to 1, CONSTANTINE to 1, CITIZEN to 4)),
            names = godfatherNames, wake = mapOf(SHERLOCK_HOLMES to 2, DOCTOR to 30, PROFESSIONAL to 31, CITIZEN_KANE to 32, CONSTANTINE to 33),
            cards = godfatherCards(MafiaLastCard.SHERLOCK_HOLMES_BEAUTIFUL_MIND), auto = false, family = GODFATHER_FAMILY),
        MafiaScenario("investigator", "بازپرس", "بازپرس، محقق، شیاد و ناتو",
            """• ترتیب شب: محقق ← تیم مافیا ← شیاد ← کارآگاه ← پزشک ← بازپرس.
• بازپرس دو نفر را وارد بازپرسی می‌کند؛ اگر هر دو صبح زنده باشند هر کدام دو نوبت دفاع کوتاه می‌گیرند و بازپرس تصمیم می‌گیرد رأی ویژه انجام شود یا نه. دو متهم در همان رأی حق رأی ندارند؛ رأی بالاتر خارج می‌شود و تساوی یعنی خروج نیست.
• اگر بازپرس همان شب بمیرد، بازپرسی صبح اجرا می‌شود؛ اگر یکی از هدف‌ها شب حذف شود، توانایی بازپرس نمی‌سوزد.
• محقق هر شب خودش را به یک بازیکن پیوند می‌زند؛ اگر محقق با شلیک یا رأی خارج شود، هدف وابسته هم خارج می‌شود (رئیس مافیا مستثناست).
• شیاد هر شب یک نفر را پاپوش می‌کند؛ انتخاب تکراری مجاز است.
• ناتو یک بار نقش دقیق یک شهروند نقش‌دار را حدس می‌زند؛ حدس درست = حذف مستقیم.
• حد نصاب دفاع و خروج: ۱۱ تا ۱۳ نفر ← ۵ رأی؛ ۸ تا ۱۰ ← ۴؛ ۶ و ۷ ← ۳. تساوی ← کسی خارج نمی‌شود.
• شیاد کارآگاه را بلاک می‌کند (استعلام آن شب منفی). رئیس مافیا می‌تواند شهروند ساده یا رویین‌تن را بخرد (سوداگری).""",
            listOf(GODFATHER, NATO, FRAUD, MAFIA, RESEARCHER, HERO, INTERROGATOR, DOCTOR, DETECTIVE, SNIPER, CITIZEN),
            presets = mapOf(10 to m(GODFATHER to 1, NATO to 1, FRAUD to 1, RESEARCHER to 1, HERO to 1, INTERROGATOR to 1, DOCTOR to 1, DETECTIVE to 1, CITIZEN to 2),
                12 to m(GODFATHER to 1, NATO to 1, FRAUD to 1, MAFIA to 1, RESEARCHER to 1, HERO to 1, INTERROGATOR to 1, DOCTOR to 1, DETECTIVE to 1, SNIPER to 1, CITIZEN to 2),
                13 to m(GODFATHER to 1, NATO to 1, FRAUD to 1, MAFIA to 1, RESEARCHER to 1, HERO to 1, INTERROGATOR to 1, DOCTOR to 1, DETECTIVE to 1, SNIPER to 1, CITIZEN to 3)),
            names = mapOf(GODFATHER to "رئیس مافیا", SNIPER to "تک‌تیرانداز"),
            wake = mapOf(RESEARCHER to 10, FRAUD to 26, DETECTIVE to 30, DOCTOR to 31, INTERROGATOR to 32),
            setup = ScenarioSetup(thresholds = mapOf(13 to 5, 12 to 5, 11 to 5, 10 to 4, 9 to 4, 8 to 4, 7 to 3, 6 to 3)), auto = false),
        MafiaScenario("nato_commando", "ناتو / تکاور", "محافظ، گروگانگیر، تکاور و تفنگ‌ساز",
            """• گروگانگیر = افسونگر برنامه (نام‌مستعار معتبر).
• ترتیب شب: نگهبان ← گروگانگیر ← تیم مافیا بدون گروگانگیر ← کارآگاه ← تکاور ← پزشک ← تفنگدار.
• تکاور فقط در شرط فعال شدن خودش تیر دارد؛ اگر هدف رئیس مافیا باشد تعامل مخصوص رئیس اعمال می‌شود و اگر مافیای دیگر باشد حذف معتبر است. «شلیک نکن» هم مجاز است.
• پزشک: نجات خود محدود؛ در تعداد بالا امکان نجات دو نفر.
• نگهبان هدف‌های مجاز را محافظت می‌کند و می‌تواند گروگانگیر را بلاک کند.
• تفنگدار: تفنگ مشقی و جنگی توزیع می‌کند.
• حد نصاب ورود به دفاع: ۱۰، ۹ و ۸ نفر ← ۴؛ ۷ و ۶ ← ۳؛ ۵ و ۴ ← ۲.""",
            listOf(GODFATHER, NATO, VOODOO, MAFIA, GUARDIAN, DETECTIVE, COMMANDO, DOCTOR, GUNMAN, CITIZEN),
            presets = mapOf(10 to m(GODFATHER to 1, NATO to 1, VOODOO to 1, GUARDIAN to 1, DETECTIVE to 1, COMMANDO to 1, DOCTOR to 1, GUNMAN to 1, CITIZEN to 2)),
            names = mapOf(VOODOO to "گروگانگیر", DOCTOR to "پزشک", GODFATHER to "رئیس مافیا"),
            wake = mapOf(GUARDIAN to 5, VOODOO to 6, DETECTIVE to 30, COMMANDO to 31, DOCTOR to 32, GUNMAN to 33),
            setup = ScenarioSetup(thresholds = natoThresholds), auto = false),
        MafiaScenario("zodiac", "زودیاک", "سه ساید با قاتل مستقل زودیاک",
            """• سه ساید: شهر، مافیا و مستقل زودیاک.
• روز معارفه بدون چالش؛ کارت حرکت آخر ندارد؛ نصف آرا برای ورود به دفاع.
• اگر دو نفر یا بیشتر در دفاع باشند، رأی خواب نیمروز اجرا و بالاترین رأی خارج می‌شود.
• فقط دو استعلام وضعیت برای افراد خارج‌شده.
• ترتیب شب: مافیا ← زودیاک ← حرفه‌ای ← دکتر ← کارآگاه ← تفنگدار ← اوشن.
• آل‌کاپن: رئیس؛ شلیک شب؛ استعلام منفی؛ شلیک حرفه‌ای می‌تواند او را خارج کند.
• شعبده‌باز: توانایی یک بازیکن را ۲۴ ساعت می‌گیرد؛ دو شب پیاپی روی یک هدف نه.
• بمب‌گذار: یک بار بمب روی هر ساید با کد ۱ تا ۴.
• زودیاک: در شب‌های زوج شلیک دارد؛ اگر محافظ را بزند خودش خارج می‌شود.
• محافظ: ضد زودیاک؛ اگر بمب جلوی خودش باشد کد را می‌داند.
• اوشن: دو شب یک شهروند را بیدار می‌کند؛ اگر مافیا یا زودیاک را انتخاب کند خودش خارج می‌شود.
• تفنگدار: هر شب حداکثر دو تفنگ می‌دهد؛ نوع تفنگ برای گیرنده پنهان است.
• حرفه‌ای: شلیک شب به مافیا؛ شلیک به شهروند ← خروج خودش؛ شلیک به زودیاک بی‌اثر.""",
            listOf(GODFATHER, ILLUSIONIST, BOMBER, ZODIAC, PROTECTOR, OCEAN, GUNMAN, PROFESSIONAL, DOCTOR, DETECTIVE, CITIZEN),
            presets = mapOf(12 to m(GODFATHER to 1, ILLUSIONIST to 1, BOMBER to 1, ZODIAC to 1, PROTECTOR to 1, OCEAN to 1, GUNMAN to 1, PROFESSIONAL to 1, DOCTOR to 1, DETECTIVE to 1, CITIZEN to 2)),
            names = mapOf(GODFATHER to "آل‌کاپن", DOCTOR to "پزشک"),
            wake = mapOf(ZODIAC to 26, PROFESSIONAL to 27, DOCTOR to 28, DETECTIVE to 29, GUNMAN to 30, OCEAN to 31),
            setup = ScenarioSetup(queryCount = 2, edge = "down", noCards = true), auto = false),
        MafiaScenario("advanced", "پیشرفته", "سناریوی منعطف با ترکیب دلخواه گرداننده",
            """• تعداد منفی‌ها نزدیک یک‌سوم کل نفرات؛ ترکیب نقش‌ها را گرداننده می‌سازد.
• شهر برای برد باید همهٔ تهدیدهای منفی را حذف کند.
• مافیا با رسیدن به برابری عددی برنده می‌شود.
• مستقل‌ها شرط برد اختصاصی دارند و در شمارش شهر و مافیا حساب نمی‌شوند.
• استعلام وضعیت و ادعای نقش را می‌توان در تنظیمات بازی فعال یا غیرفعال کرد.""",
            emptyList()),
        MafiaScenario("mafia_nights", "شب مافیا", "مجموعهٔ نقش‌های شب مافیا (بدون ترکیب رسمی)",
            """• صفحهٔ مرجع ترکیب کامل رسمی را نشان نمی‌دهد؛ ترکیب رسمی ادعا نمی‌شود.
• مجموعهٔ نقش‌های شب مافیا در برنامه: شهروند ساده، فروشنده، خوابگرد، حرفه‌ای، کارآگاه، جان‌سخت، دکتر، روانپزشک، شهردار؛ مافیا ساده، دکتر لکتر، شارلاتان، بادیگارد، پدرخوانده.
• ترکیب نهایی را راوی از همین نقش‌ها می‌سازد.
• کارت حرکت آخر مرجع ذکر نشده است.""",
            listOf(GODFATHER, DOCTOR_LECTOR, CHARLATAN, BODYGUARD, MAFIA, SALESMAN, SLEEP_WALKER, PROFESSIONAL, DETECTIVE, DIE_HARD, DOCTOR, PSYCHOLOGIST, MAYOR, CITIZEN)),
        MafiaScenario("court", "دربار", "درباریان در برابر رعایا",
            """• دو جناح: درباریان (مافیا) و رعایا (شهر).
• سلطان: رهبر؛ شب یا سم می‌دهد یا به جای آن نقش یک رعیت را حدس می‌زند؛ حدس درست حذف مستقیم. استعلام مفتش روی سلطان درباری نیست. پس از مرگ سلطان، دربار سم را ادامه می‌دهد ولی حدس او از بین می‌رود.
• چریک: اسلحهٔ بازیکن مسلح را دستکاری می‌کند؛ وقتی قربانی شلیک کند خودش به جای هدف می‌میرد.
• مزدور: هر شب یک رعیت را پاپوش می‌کند تا مفتش او را درباری ببیند؛ دو شب پیاپی یک هدف ممنوع.
• جلاد (۱۳ نفر): شکار شبانه و نشان یک‌باره برای مجهول کردن ساید.
• مهمات‌چی: یک بار تفنگ می‌دهد؛ دارنده روز بعد پیش از رأی شلیک دارد و ساید مقتول اعلام می‌شود.
• مفتش: هر شب ساید یک نفر را استعلام می‌کند.
• طبیب: پادزهر شب؛ استفاده روی خود حداکثر دو بار.
• سربدار: اگر با رأی خارج شود، درباریان شب بعد هیچ کاری ندارند.
• کاتب: به یک نفر امکان وصیت در صورت مرگ همان شب می‌دهد.
• ریش‌سفید: یک بار در خواب نیمروز یک نفر را از مرحلهٔ دوم رأی خارج می‌کند.
• شب‌گرد: یک شب کامل دست‌نیافتنی است.
• طبیب: هر شب یک پادزهر می‌دهد که فقط اگر هدف همان مسموم باشد نجاتش می‌دهد؛ روی خودش حداکثر دو بار. کاتب: هر شب قلم و کاغذ به یک نفر می‌دهد تا اگر همان شب کشته شد وصیت منتشر کند. ریش‌سفید: یک بار در خواب نیمروز یک نفر را از مرحلهٔ دوم رأی معاف می‌کند. مفتش سلطان را رعیت می‌بیند.""",
            listOf(SULTAN, MERCENARY, EXECUTIONER, MAFIA, SARBEDAR, SCRIBE, ELDER, NIGHTWALKER, CITIZEN),
            names = mapOf(MAFIA to "درباری ساده", CITIZEN to "رعیت ساده")),
        MafiaScenario("masked", "نقابدار", "نقابدار، یاکوزا، ژوپیتر و رونانت",
            """• نقابدار: استعلام کارآگاه مثبت؛ در ابتدا خودش و یارانش یکدیگر را نمی‌شناسند؛ با شلیک شب مافیا حذف نمی‌شود. آلفا: یک بار استعلام کارآگاه را خنثی می‌کند. بتا: یک بار نقش یک شهروند را حدس می‌زند؛ درست = هدف شهروند ساده می‌شود و قابل یاکوزا شدن است.
• اگر رئیس و یاکوزا خارج شوند، نقابدار مسئول شلیک می‌شود.
• یاکوزا: یک بار به جای شلیک و با تأیید رئیس تبدیل انجام می‌دهد: شهروند ویژه یا ساده محافظت‌شده توسط ژوپیتر ← یاکوزا می‌میرد و تبدیل شکست؛ شهروند ساده بدون حفاظت ← یاکوزا می‌میرد و هدف از شب بعد مافیاست؛ نقابدار ← یاکوزا نمی‌میرد و نقابدار به تیم متصل می‌شود.
• کارآگاه: رئیس و شهر منفی؛ یاکوزا، نقابدار و تبدیل‌شده مثبت.
• دکتر: هر شب یک نجات؛ نجات خود دو بار.
• ژوپیتر: هر شب یک شهروند ساده را از تبدیل یاکوزا محافظت می‌کند.
• رونانت: اگر شلیک مافیا بخورد و نجات نشود یک روز مهلت دارد.
• آلفا: یک شب استعلام کارآگاه را فریز می‌کند؛ استعلام آن شب خنثی و از آن نفر تا آخر بازی استعلام گرفته نمی‌شود.
• اگر فرد یاکوزایی‌شده‌ای باشد، نقابدار و او به هم معرفی شده و از آن شب با هم بیدار می‌شوند.
• یاکوزا: یک بار در بازی با تأیید رئیس و به جای شات، خودش را فدا می‌کند تا هدف را مافیا کند؛ فقط شهروند ساده یا نقابدار. دکتر: هر شب یک نجات، خودش دو بار. ژوپیتر هر شب یک نفر را از یاکوزایی شدن محافظت می‌کند.""",
            listOf(GODFATHER, YAKUZA, MASKED, MAFIA, DETECTIVE, DOCTOR, JUPITER, REVENANT, CITIZEN)),
        MafiaScenario("filimo", "فیلیمو / دکتر لکتر", "دکتر لکتر، جوکر مافیا، جان‌سخت و فروشنده",
            """• دکتر شهر: نجات خود فقط یک بار.
• دکتر لکتر: نجات خود یک بار و هر شب یک عضو مافیا را از شلیک نجات می‌دهد.
• جوکر مافیا دو بار نتیجهٔ استعلام را دستکاری می‌کند (یک انتخاب از شهر و یکی از مافیا).
• حرفه‌ای هر شب حق شلیک دارد؛ شلیک اشتباه ← خروج خودش.
• شهردار توانایی یک‌باره؛ روانشناس مجموعاً دو سکوت و هر نفر فقط یک بار.
• اگر دکتر جان‌سخت را هم‌زمان با شلیک مافیا نجات دهد، جان اضافهٔ جان‌سخت مصرف نمی‌شود.
• فروشنده یک بار توانایی دارد؛ روی جان‌سخت ابتدا زره را می‌سوزاند.
• رئیس مافیا استعلام منفی.
• رأی: نصف بازیکنان برای ورود به دفاع؛ برای خروج نصف «سایرین».
• بازی با شب شروع می‌شود و اعضای مافیا یکدیگر را می‌شناسند. چند دفاعیه ← رأی دوم در خواب نیمروزی (با شهردار).
• جوکر مافیا (نقش جدا از جوکر مستقل): در کل بازی دو بار، یک نفر از مافیا و یک نفر از شهر، استعلامشان برای کارآگاه معکوس می‌شود؛ استعلام خود جوکر مثبت.
• استعلام کارآگاه: جوکر، دکتر لکتر و مافیای ساده مثبت؛ پدرخوانده و شهروندان منفی.
• روانشناس: دو بار در کل بازی یک نفر را ۲۴ ساعت ساکت می‌کند. جان‌سخت: اولین شلیک مافیا را تحمل می‌کند و دو بار استعلام وضعیت دارد.
• ترتیب شب (برگهٔ گردانندگی): دکتر شهر ← پدرخوانده با مافیا ← دکتر لکتر ← جوکر ← کارآگاه ← حرفه‌ای ← روانپزشک ← فروشنده ← جان‌سخت.
• کارت‌های مرجع (۶ کارت): مسیر سبز، فرش قرمز، شلیک نهایی، حدس نقش (= هوش و ذکاوت: حدس درست نقش یک بازیکن ← بازگشت به بازی)، بی‌خوابی، دروغ سیزده.
• فروشنده: یک بار در بازی توانایی یک نفر را برای همیشه می‌گیرد (روی جان‌سخت با جلیقهٔ سالم فقط جلیقه می‌سوزد).
• تساوی دو نفر در رأی دوم ← قرعهٔ مرگ.
• بازی با شب معارفه شروع می‌شود و بعد از آن روز معارفه با چالش ۳۰ ثانیه‌ای است (روز معارفه پیش از شب ندارد).""",
            listOf(GODFATHER, DOCTOR_LECTOR, JOKER_M, MAFIA, CITIZEN, SALESMAN, SLEEP_WALKER, PROFESSIONAL, DETECTIVE, DIE_HARD, DOCTOR, PSYCHOLOGIST, MAYOR),
            names = mapOf(GODFATHER to "رئیس مافیا"),
            cards = listOf(MafiaLastCard.GREEN_MILE, MafiaLastCard.RED_CARPET, MafiaLastCard.FINAL_SHOT, MafiaLastCard.INTELLIGENCE, MafiaLastCard.INSOMNIA, MafiaLastCard.THIRTEEN_LIE),
            setup = ScenarioSetup(edge = "down", noIntroDay = true)),
        MafiaScenario("negotiation", "مذاکره", "مذاکره‌کننده، خبرنگار، تک‌تیرانداز و زره‌پوش",
            """• مذاکره فقط وقتی ممکن است که مذاکره‌کننده زنده باشد و تیم مافیا حداقل یک عضو از دست داده باشد. مذاکره جای شلیک شب را می‌گیرد.
• هدف فقط شهروند ساده یا زره‌پوشِ بدون زره است؛ موفقیت ← بازیکن از همان زمان مافیا می‌شود. راوی فقط اعلام می‌کند «فرایند مذاکره انجام شد».
• مذاکره‌کننده فقط یک بار مذاکره دارد و در شب مذاکره مافیا شلیک ندارد؛ همهٔ نقش‌ها (حتی پزشک) همچنان بیدار می‌شوند.
• خبرنگار: از شب مذاکره به بعد فعال؛ یک بار استعلام می‌گیرد که با یک نفر مذاکره شده یا نه.
• تک‌تیرانداز: هر شب بیدار می‌شود ولی فقط یک شلیک دارد؛ رئیس مافیا ← کسی خارج نمی‌شود؛ مافیای دیگر ← خارج؛ شهروند ← خود تک‌تیرانداز خارج می‌شود.
• پزشک: هر شب یک نجات؛ نجات خود دو بار در کل بازی. زره‌پوش: یک زره در شب؛ بعد از افتادن زره شهروند ساده است.
• استعلام: مذاکره‌کننده و مافیای ساده مثبت؛ رئیس مافیا و شهروندان منفی.
• ترتیب شب: مافیا ← کارآگاه ← تک‌تیرانداز ← پزشک ← خبرنگار (پس از مذاکره).
• حد نصاب رأی: ۱۰، ۹ و ۸ نفر ← ۴ رأی برای دفاع و خروج (بقیه مثل ناتو).
• تعداد بازیکنان: ۹، ۱۰، ۱۲، ۱۳ و ۱۵ نفر. کارت حرکت آخر در منابع ذکر نشده.
• پزشک تا وقتی ۸ نفر یا بیشتر در بازی‌اند هر شب دو نجات دارد.""",
            listOf(GODFATHER, NEGOTIATOR, MAFIA, DETECTIVE, SNIPER, DOCTOR, REPORTER, ARMORED, CITIZEN),
            wake = mapOf(DETECTIVE to 30, SNIPER to 31, DOCTOR to 32, REPORTER to 33),
            names = mapOf(GODFATHER to "رئیس مافیا", DOCTOR to "پزشک", SNIPER to "تک‌تیرانداز"),
            setup = ScenarioSetup(thresholds = natoThresholds)),
        MafiaScenario("saghi", "ساقی", "تروریست، شاه‌کش، قاضی و رئیس پلیس",
            """• تروریست: پیش از رأی یا خروج می‌تواند یک نفر را همراه خود ببرد؛ اگر این کار انجام شود، مافیا آن شب شلیک ندارد.
• شاه‌کش: هر شب یا کارآگاه را هدف می‌گیرد تا استعلام‌هایش معکوس شود، یا نقش یک نفر را حدس می‌زند تا توانایی آن نقش غیرفعال شود.
• پزشک: نجات شب؛ نجات خود یک بار.
• تفنگ‌ساز: تفنگ جنگی یا خودی می‌سازد؛ تفنگ خودی هنگام استفاده شلیک‌کننده را می‌کشد.
• قاضی: یک بار بازیکن قطعی خروج با رأی را در بازی نگه می‌دارد.
• فدایی: فقط هنگام کار تروریست جای قربانی را می‌گیرد.
• رئیس پلیس: تیر اصلی او وقتی با رأی خارج شود فعال می‌شود.
• کارآگاه: رئیس مافیا منفی.""",
            listOf(GODFATHER, TERRORIST, KINGSLAYER, SAGHI, MAFIA, DOCTOR, GUNMAN, JUDGE, SACRIFICE, POLICE_CHIEF, DETECTIVE, CITIZEN),
            names = mapOf(DOCTOR to "پزشک", GUNMAN to "تفنگ‌ساز", GODFATHER to "رئیس مافیا")),
        MafiaScenario("epic", "اپیک", "مستقل‌های جیگساو، ورولف و پویزنر",
            """• جیگساو: شب نامیرا؛ هر شب سه نفر را انتخاب و اره را به یکی می‌دهد؛ روز فرایند اتاق گاز اجرا می‌شود. هر بازیکن فقط یک بار هدف اتاق گاز.
• ورولف: شب‌های فرد نامیرا؛ شب زوج بیرون می‌رود و زخمی می‌کند.
• پویزنر: شب نامیرا؛ هر شب سم با تأخیر.
• برد مستقل‌ها: رسیدن به سه نفر نهایی طبق قانون هر مستقل.
• مافیا: گادفادر (شلیک)، کانسورت (مسدود کردن)، جنیتور (حذف وصیت یک شب در میان)، شدو (حدس و کنترل نقش)، دوعل‌من (تفنگ دوئل)، سایلنسر (سکوت کامل)، تروریست.
• شهر: وکیل (اجبار یا منع دفاعیه)، روژاک (ضد جیگساو)، کروسایدر (محافظت قوی)، دکتر.""",
            listOf(JIGSAW, WEREWOLF, POISONER, GODFATHER, CONSORT, JANITOR, SHADOW, DUELMAN, SILENCER, TERRORIST, LAWYER, ROJAK, CRUSADER, DOCTOR, CITIZEN),
            names = mapOf(GODFATHER to "گادفادر", JIGSAW to "جیکسا")),
        MafiaScenario("black_army", "لشکر سیاه", "افزونهٔ پرجمعیت با هزارچهره",
            """• افزونه برای بازی‌های پرجمعیت روی پایهٔ شب مافیا.
• هزارچهره: مستقل اصلی؛ نقش بازیکنی را که با رأی روز خارج شده تصاحب می‌کند (نقش کشته‌های شب منبع نیست).
• نایب: حامی هزارچهره؛ برد او به بقای هزارچهره وابسته است.
• شیطان‌صفت: از شب‌های بعد چند بازیکن را به هم پیوند می‌دهد؛ اگر همه از یک ساید باشند خروج گروهی رخ می‌دهد.
• برد مستقل لشکر سیاه با رسیدن هزارچهره به وضعیت نهایی است و نباید با برابری عددی مافیا زودتر اعلام شود.""",
            listOf(THOUSAND_FACES, DEPUTY, WICKED, GODFATHER, BODYGUARD, CHARLATAN, MAFIA, MAYOR, DOCTOR, DETECTIVE, SACRIFICE, SALVATION_ANGEL, CITIZEN)),
        MafiaScenario("kafir", "کافر", "کافران، روستاییان و مستقل‌ها",
            """• سه گروه: کافران، روستاییان، مستقل‌ها؛ از ۱۰ نفر به بالا.
• کافر: رئیس؛ شلیک شب؛ اعتراف و استعلام کشیش او را روستایی نشان می‌دهد؛ یک بار دوئل ریش‌سفید را رد می‌کند.
• قاتل: یک شلیک قطعی به جای شلیک اصلی؛ دزد می‌تواند آن را متوقف کند.
• هفت‌خط: دو بار حدس نقش؛ درست ← حذف.
• ساقی: تنها بیدار می‌شود و هدف را مست می‌کند.
• خائن: فقط کافر را می‌شناسد؛ هنگام خروج می‌تواند شب سیاه را فعال کند.
• قمارباز: شب با هدف سنگ/کاغذ/قیچی بازی می‌کند.
• ترکیب ۱۶ نفره: کافر (۵): کافر، قاتل، هفت‌خط، ساقی، خائن. روستایی (۱۰): دکتر، کلانتر، محافظ، کابوی، کشیش، حرفه‌ای، مارشال، فاحشه، شهردار، ریش‌سفید. مستقل (۱): دزد. بهترین حالت ۱۶ تا ۲۴ نفر؛ در ۱۹ و ۲۲ نفر سایه، نانوا، قمارباز و مزدور اضافه می‌شوند.
• ریش‌سفید دو بار بین دو نفر دوئل برگزار می‌کند (با تأیید کافر). کشیش هر شب اعتراف می‌گیرد؛ کافر و خائن برای او روستایی‌اند. محافظ: شات کافر روی او اثر ندارد. دکتر: نجات خود نامحدود.
• دزد (مستقل) هر شب نقش یک نفر را می‌دزدد؛ سایه در شب‌های سوم و پنجم سه اسم می‌دهد (دو روستایی و یک کافر ← هر سه کشته).""",
            listOf(KAFIR, ASSASSIN, HAFTKHAT, SAGHI, TRAITOR, GAMBLER, PRIEST, VILLAGE_THIEF, ELDER, DOCTOR, DETECTIVE, CITIZEN),
            names = mapOf(CITIZEN to "روستایی")),
        MafiaScenario("white_show", "وایت شو", "کلانتر، تفنگ‌ساز، گورکن و قاضی",
            """• رئیس مافیا: شلیک؛ یک بار در برابر تیر کلانتر مصون؛ استعلام شهروندی.
• تروریست: پیش از رأی می‌تواند خود و یک نفر را خارج کند.
• ناتو: هر شب حدس نقش (حتی تکراری)؛ حدس درست هدف را امشب و روز بعد بی‌توانایی می‌کند.
• جاسوس: هر استعلامی او را شهروند نشان می‌دهد.
• کلانتر: شلیک نامحدود؛ اگر شهروند را بزند خودش خارج می‌شود. اگر با رأی خارج شود یک شلیک خروجی دارد.
• کارآگاه: هر شب دو نفر را مقایسه می‌کند (هم‌ساید = تأیید)؛ رئیس و جاسوس شهروند دیده می‌شوند.
• پزشک: فقط شب اول دو نجات؛ نجات خود یک بار.
• فدایی: پس از قطعی شدن رأی خروج بازیکن، می‌تواند جایگزین شود.
• گورکن: دو بار ساید خارج‌شده‌ها را از راوی استعلام می‌گیرد.
• تفنگ‌ساز: یک بار یک تفنگ (خودی، مشقی یا جنگی) به یک نفر می‌دهد.
• گردن‌کلفت: اگر شب تیر بخورد، فردا پس از رأی‌گیری اعلام و خارج می‌شود.
• قاضی: یک بار نتیجهٔ رأی‌گیری روز را لغو می‌کند.
• ترتیب شب: مافیا ← پزشک ← کلانتر ← تفنگ‌ساز ← گورکن ← کارآگاه.""",
            listOf(GODFATHER, TERRORIST, NATO, SPY, SHERIFF, COMPARER, DOCTOR, SACRIFICE, GRAVEDIGGER, GUNSMITH, TOUGH, JUDGE, CITIZEN),
            names = mapOf(GODFATHER to "رئیس مافیا", DOCTOR to "پزشک", COMPARER to "کارآگاه"),
            wake = mapOf(DOCTOR to 30, SHERIFF to 31, GUNSMITH to 32, GRAVEDIGGER to 33, COMPARER to 34)),
        MafiaScenario("mythic", "میتیک", "نینجا، کانسورت و بادیگارد",
            """• اگر نینجا در بازی نیست، شلیک شب به کانسورت منتقل می‌شود.
• اگر گادفادر، نینجا و کانسورت همه زنده‌اند، تیم می‌تواند مسدود کردن و شلیک را در یک شب انجام دهد.
• اگر شلیک‌کنندهٔ مافیا هدف محافظت‌شدهٔ بادیگارد را بزند، هدف زنده و شلیک‌کننده و بادیگارد خارج می‌شوند.
• دکتر و بادیگارد نجات خود فقط یک بار. استعلام وضعیت ندارد.
• گادفادر یک بار «وصیت‌کشی» دارد. ترور در شمارش برابری جزو شهر است.
• اسنایپر شلیک اشتباه ← خودش شب بعد خارج می‌شود.
• نصف آرا برای دفاع و خروج؛ همهٔ افراد دفاعیه حق رأی دارند.
• ادعای همهٔ نقش‌ها آزاد است جز ادعای نقش ترور.
• اگر بادیگارد خودش را نجات دهد هیچ‌کس خارج نمی‌شود.""",
            listOf(GODFATHER, NINJA, CONSORT, MAFIA, BODYGUARD_C, DOCTOR, SNIPER, TERROR_C, DETECTIVE, CITIZEN),
            names = mapOf(GODFATHER to "گادفادر"), setup = ScenarioSetup(queryCount = 0, edge = "down")),
        MafiaScenario("scam", "اسکام", "ادعای آزاد، بدون چالش، رأی هم‌زمان",
            """• ادعای نقش کاملاً آزاد؛ بلافاصله بعد از تقسیم نقش، ۱ تا ۲ دقیقه سناریوچینی.
• شروع‌کنندهٔ صحبت از میان داوطلبان قرعه‌کشی می‌شود؛ چالش ندارد؛ دو دور کامل صحبت.
• رأی مخفی: هر بازیکن مخفیانه یک نفر را انتخاب می‌کند و همه هم‌زمان اعلام می‌شوند. ۲ رأی یا بیشتر ← دفاع؛ بیش از ۵۰٪ کل آرا ← خروج مستقیم.
• رأی دوم بین افراد دفاعیه اجباری است و افراد دفاعیه هم حق رأی دارند.
• صحبت خارج از نوبت یا هر علامت در زمان دفاع دیگران ← اخراج انضباطی (با ابزار راوی، خروج انضباطی).
• هر خروج روز بلافاصله شب ایجاد می‌کند و نقش خارج‌شده اعلام می‌شود.
• ترکیب ۱۰ نفره (۳ مافیا، ۷ شهروند): دان، هوکر (= افسونگر)، فلیمر؛ کارآگاه، دکتر، اسنایپر و شهروند ساده. ۱۲/۱۳ نفر: + تروریست و رویین‌تن و اسنایپر ۲ شات. ۱۵ نفر: + خرابکار و تفنگدار.
• فلیمر استعلام شهروندان را برای کارآگاه مثبت می‌کند. هوکر با تیم مافیا بیدار می‌شود و یک نقش را مسدود می‌کند.
• دکتر (۱۰ و ۱۳ نفر): هر شب یک نجات و نجات خود نامحدود. تفنگدار (۱۵ نفر): دو تفنگ جنگی؛ صبح دارندگان تفنگ اعلام می‌شوند.""",
            listOf(GODFATHER, VOODOO, FLAMER, DETECTIVE, DOCTOR, SNIPER, CITIZEN, TERRORIST, HERO, WRECKER, GUNMAN, MAFIA),
            names = mapOf(VOODOO to "هوکر"), setup = ScenarioSetup(challenge = false, queryCount = 0)),
        MafiaScenario("red_status", "وضعیت قرمز", "۶ نفره بدون فاز شب",
            """• ترکیب ثابت ۶ نفره: مافیا ساده ×۲، شهروند ساده ×۴.
• فاز شب ندارد و دو مافیا یکدیگر را نمی‌شناسند.
• دو روز اول گفت‌وگو و چالش، سپس رأی. در ۶ نفر، ۳ رأی برای دفاع.
• خروج نیاز به نصف تعداد دارد (۶ نفر = ۳، ۵ نفر = ۳). بازیکن خارج‌شده سایدش را اعلام می‌کند.
• از روز چهارم یا بعد از اولین خروج، دوئل باز می‌شود: هر روز حداکثر یک دوئل؛ یک نفر درخواست می‌دهد و هدف قبول یا رد می‌کند؛ هر دو ساید خود را محرمانه به داور می‌گویند.""",
            listOf(MAFIA, CITIZEN), presets = mapOf(6 to m(MAFIA to 2, CITIZEN to 4)),
            setup = ScenarioSetup(noNight = true, noMafiaIntro = true, thresholds = mapOf(6 to 3, 5 to 3, 4 to 2), noCards = true), auto = false),
        MafiaScenario("pure_classic", "کلاسیک محض", "۱۰ تا ۱۳ نفر؛ دو رأی اجباری",
            """• شهر: دکتر، رویین‌تن، شهروند ساده؛ مافیا: گادفادر، تروریست، مافیای ساده.
• معارفه ۱۵ ثانیه برای هر نفر؛ شب معارفه مافیاها همدیگر را می‌شناسند؛ هر روز یک چالش آزاد.
• مرحلهٔ اول: هر بازیکن دو رأی اجباری؛ حد نصاب دفاع: ۱۰ ← ۴، ۱۲ ← ۵، ۱۳ ← ۵.
• مرحلهٔ خروج: هر بازیکن یک رأی؛ بالاترین رأی حذف؛ تساوی ← کسی خارج نمی‌شود.
• بیشترین رأی در مرحلهٔ خروج حذف می‌شود (خروج اجباری).
• ۱۰ نفره: دو «اعلام وضعیت» رنگی؛ بازی از وضعیت زرد شروع می‌شود (اختلاف شهر و مافیا ۱–۲ قرمز، ۳–۴ زرد، ۵ به بالا سفید).""",
            listOf(GODFATHER, TERRORIST, MAFIA, DOCTOR, HERO, CITIZEN),
            presets = mapOf(10 to m(GODFATHER to 1, TERRORIST to 1, MAFIA to 1, DOCTOR to 1, HERO to 1, CITIZEN to 5),
                12 to m(GODFATHER to 1, TERRORIST to 1, MAFIA to 2, DOCTOR to 1, HERO to 1, CITIZEN to 6),
                13 to m(GODFATHER to 1, TERRORIST to 1, MAFIA to 2, DOCTOR to 1, HERO to 1, CITIZEN to 7)),
            names = mapOf(GODFATHER to "گادفادر"), setup = ScenarioSetup(thresholds = mapOf(10 to 4, 12 to 5, 13 to 5), queryCount = 2), auto = false),
        MafiaScenario("weather", "آب و هوا", "هوای هر شب روی بازی اثر دارد",
            """• همهٔ رأی‌گیری‌ها با چشم بسته؛ ۶ تا ۱۶ نفر.
• کارشناس آب‌وهوا: یک بار (در تعداد بالا دو بار) هوای بعدی را عوض می‌کند و در شب طوفانی یک نفر را مجبور به دفاع فردا می‌کند.
• دکتر: نجات خود یک بار. حرفه‌ای: به تعداد مافیاها تیر دارد. جان‌سخت: یک جان اضافه و دو شب استعلام وضعیت.
• کارآگاه: پدرخوانده منفی. رفتگر: دو شب محافظت از خود در برابر یخبندان.
• هوای هر شب را راوی اعلام و اثرش را با ابزار راوی اعمال می‌کند.""",
            listOf(GODFATHER, MAFIA, WEATHERMAN, DOCTOR, PROFESSIONAL, DIE_HARD, DETECTIVE, SWEEPER, CITIZEN), players = 6..16),
        MafiaScenario("joker_mafia", "جوکر مافیا", "کم‌نفر با جوکر مستقل",
            """• سه ساید: شهر، مافیا، جوکر مستقل.
• پزشک: هر شب یک نجات؛ نجات خود یک بار. مافیا: یک شلیک شب.
• جوکر: شب نامیرا؛ اگر به سه نفر آخر برسد شهروند می‌شود. در دو شب می‌تواند «خنده» را فعال کند؛ اگر فردای آن با رأی خارج شود برنده است.
• هر روز دو دور صحبت بدون چالش. حد نصاب دفاع: ۶ نفر ← ۳، ۵ نفر ← ۲. افراد دفاع حق رأی دارند. خروج هر بازیکن ← اعلام نقش.
• طراح: سپهر ازقندی (وایت‌شو). نسخهٔ بزرگ‌تر «کلاسیک پیشرفته با جوکر» (۱۲، ۱۳، ۱۵ نفر) در «کلاسیک پیشرفته» است.""",
            listOf(MAFIA, DOCTOR, JOKER, CITIZEN), presets = mapOf(6 to m(MAFIA to 1, DOCTOR to 1, JOKER to 1, CITIZEN to 3)),
            names = mapOf(DOCTOR to "پزشک"), setup = ScenarioSetup(challenge = false, thresholds = mapOf(6 to 3, 5 to 2))),
        MafiaScenario("elixir", "اکسیر", "گروه بیماری در برابر انسان‌ها",
            """• چندتیمی با مفهوم بیماری و انسان.
• جادوگر بیماری: قاتل شب؛ حتی عضو تیم خودش را می‌تواند هدف بگیرد.
• یاغی: در نقطهٔ مجاز از ساید اصلی جدا می‌شود و با یک انسان شاخهٔ جدید می‌سازد.
• عاصی: رقیب قدرتی جادوگر با شرط برد مستقل.
• اکسیر و چاپار با جادوگر تعامل اختصاصی دارند.""",
            listOf(ELIXIR_WITCH, REBEL_E, ASI, ELIXIR, CHAPAR, DOCTOR, DETECTIVE, CITIZEN, MAFIA)),
        MafiaScenario("thief_gangster", "دزد و گنگستر", "سلاخ، گنگستر، دکستر و بازرس",
            """• ترتیب شب: نگهبان ← دزد ← مافیا به جز دزد ← دکستر ← کارآگاه خصوصی ← پزشک ← سیرکوله ← گنگستر تنها ← تفنگدار ← بازرس.
• گادفادر: شلیک؛ اگر خارج شود سلاخ و گنگستر توانایی ویژه را از دست می‌دهند.
• سلاخ: یک بار جنازهٔ خروج رأیی را سلاخی می‌کند؛ به جای شلیک نقش یک شهروند ویژه را حدس می‌زند.
• شهر: نگهبان، پزشک (خودش دو بار)، سیرکوله، تفنگدار (یک جنگی و مشقی نامحدود)، بازرس، دکستر، کارآگاه خصوصی، ۲ شهروند ساده.
• دزد کاملاً مخفی است و یارانش را نمی‌شناسد؛ هر شب کنش دو نفر را جابه‌جا می‌کند (کنش نگهبان را نه، دو شب پیاپی تکراری نه). کارآگاه خصوصی فقط استعلام دزد را مثبت می‌گیرد. بازرس آخر از همه بیدار می‌شود و کنش شبانهٔ یک نفر را می‌فهمد.
• ترکیب مرجع ۱۳ نفره: مافیا (۴): گادفادر، سلاخ، گنگستر، دزد. شهر (۹): نگهبان، پزشک، سیرکوله، تفنگدار، بازرس، دکستر، کاراگاه خصوصی، ۲ شهروند ساده.
• با خروج گادفادر، سلاخ و گنگستر توانایی ویژه را از دست می‌دهند و مافیای ساده می‌شوند.
• سلاخ: یک بار جنازهٔ خروج رأیی را سلاخی می‌کند و یک بار به جای شلیک ناتویی (حدس نقش) دارد. دکستر نقش مقابل سلاخ است (یک بار).
• گنگستر فقط یک شب تفنگ می‌دهد (یک جنگی و یک مشقی؛ جنگی به یار خودش نه). دزد یارانش را نمی‌شناسد، هر شب کنش دو نفر را جابه‌جا می‌کند (نه کنش نگهبان)، با شات مافیا خارج نمی‌شود و اگر به نگهبان بزند خارج می‌شود.
• نگهبان در شب از ۱ یا ۲ نفر در برابر دزد نگهبانی می‌دهد و در روز زره دارد. سیرکوله هر شب به خانهٔ یک نفر می‌رود؛ اگر قربانی شب را درست حدس بزند قاتل را می‌بیند و بیرون از خانه از شات مافیا در امان است. کاراگاه خصوصی فقط دزد را مثبت می‌بیند. پزشک خودش را دو بار.
• حد نصاب: ۱۱ تا ۱۳ نفر ← ۵ رأی.""",
            listOf(GODFATHER, BUTCHER, GANGSTER, THIEF, GUARD, DOCTOR, CIRCULE, GUNMAN, INSPECTOR, DEXTER, PRIVATE_EYE, CITIZEN),
            presets = mapOf(13 to m(GODFATHER to 1, BUTCHER to 1, GANGSTER to 1, THIEF to 1, GUARD to 1, DOCTOR to 1, CIRCULE to 1, GUNMAN to 1, INSPECTOR to 1, DEXTER to 1, PRIVATE_EYE to 1, CITIZEN to 2)),
            names = mapOf(GUARD to "نگهبان", DOCTOR to "پزشک", GODFATHER to "گادفادر"),
            wake = mapOf(GUARD to 2, THIEF to 3, DEXTER to 30, PRIVATE_EYE to 31, DOCTOR to 32, CIRCULE to 33, GANGSTER to 34, GUNMAN to 35, INSPECTOR to 36),
            setup = ScenarioSetup(thresholds = mapOf(13 to 5, 12 to 5, 11 to 5)), auto = false),
        MafiaScenario("parallel", "دنیای موازی", "بازی هم‌زمان در دو دنیا (لایه روی سناریوی پایه)",
            """• یک سناریوی پایه انتخاب می‌شود (مثلاً بازپرس).
• زمان‌های گفتگو دو برابر؛ بازیکن زمانش را بین دنیای ۱ و ۲ تقسیم می‌کند.
• پس از یک دور صحبت: رأی دنیای ۱ ← خروج دنیای ۱ ← رأی دنیای ۲.
• خروج در یک دنیا فقط حق آن دنیا را می‌گیرد.
• شب ابتدا کارهای دنیای ۱ و سپس دنیای ۲ جداگانه انجام می‌شود؛ شرط برد هر دنیا جداست.
• برای اجرا، دو بازی هم‌زمان (یکی برای هر دنیا) با همین بازیکنان بسازید.
• طراح: محسن شهاب. سناریوی پایهٔ اصلی «بازپرس» است (۱۰، ۱۲ و ۱۳ نفر).
• هر بازیکن در هر دنیا یک نقش دارد (ممکن است یکسان باشد). کسی که از یک دنیا خارج شده تا آخر بازی حق اظهار نظر دربارهٔ آن دنیا را ندارد؛ تخلف ← اخراج.
• دو گروه برنده وجود دارد (یکی برای هر دنیا)؛ نقش دنیایی که زودتر تمام شده تا پایان هر دو دنیا فاش نمی‌شود.""",
            emptyList()),
        MafiaScenario("triple", "تریپل ۱۲ نفره", "ناتلی، پوتون، جاناتان و مونیکا",
            """• مافیا ×۴: گادفادر (استعلام بازپرس منفی)، ناتلی (حدس نقش همراه شلیک؛ دو اشتباه پشت سر هم ← خروج خودش)، تروریست، پوتون (یک بار حدس زره آهنگر).
• شهر ×۸: بازپرس (یک بار مقایسهٔ ساید دو نفر)، جاناتان (یک بار کشتن مستقیم)، آهنگر (یک زره)، دکتر، قاضی، مونیکا (سکوت یا گرفتن حق رأی)، شهروند ساده ×۲.""",
            listOf(GODFATHER, NATALI, TERRORIST, POTON, COMPARER, JONATHAN, SMITH, DOCTOR, JUDGE, MONICA, CITIZEN),
            presets = mapOf(12 to m(GODFATHER to 1, NATALI to 1, TERRORIST to 1, POTON to 1, COMPARER to 1, JONATHAN to 1, SMITH to 1, DOCTOR to 1, JUDGE to 1, MONICA to 1, CITIZEN to 2)),
            names = mapOf(GODFATHER to "گادفادر", COMPARER to "بازپرس"), auto = false),
        MafiaScenario("molagh", "ملق", "تپان‌ها در برابر شوها",
            """• واژگان: شهروند = تپان، مافیا = شو. ادعای نقش آزاد.
• تپانچه: هر شب می‌تواند تفنگ بدهد (جنگی یا خودی؛ خودی شلیک‌کننده را می‌کشد).
• تپانگر: استعلام شب. تپان‌خواه: نجات شب؛ نجات خود یک بار؛ دو شب پیاپی یک هدف ممنوع.
• شو فادر: رهبر و شلیک؛ استعلام منفی. شو مستر: هر شب یک تپان را گروگان می‌گیرد؛ اگر شو فادر را گروگان بگیرد تیم آن شب شلیک ندارد. شو ملق: شوی ساده.
• معارفه ۱۵ ثانیه؛ هر روز یک چالش آزاد؛ نصف آرا برای دفاع.
• نقش‌های تپان/شو قوانین خاص خودشان را دارند و در برنامه نیستند؛ به خاطر شباهت اسمی با نقش دیگری جایگزین نشده‌اند. با «افزودن نقش» بسازید.
• «شو ملق» برای ۱۰ تا ۱۳ نفر طراحی شده است.""",
            emptyList(), setup = ScenarioSetup(edge = "down"), auto = false),
        MafiaScenario("scorpion", "اسکورپیون", "کودتای تریتور و متخصص",
            """• ۱۲ یا ۱۳ نفر. تریتور (مافیای خائن با برد جدا) و متخصص (در ظاهر شهر ولی هم‌پیمان تریتور) در شب معارفه آشنا می‌شوند و از شب‌های بعد اول بیدار می‌شوند و روی یک بازیکن بمب مخفی می‌گذارند یا هدف را تغییر می‌دهند.
• تا وقتی متخصص در بازی است: تریتور در شمارش برد حساب نمی‌شود، برد شخصی ندارد، در برابر شلیک معمول مصون است (جز اسکورپیون) و ریموت فعال نیست.
• پس از خروج متخصص: تریتور مستقل فعال می‌شود، هدف بمب ثابت می‌شود و ریموت یک بار فعال می‌شود؛ اگر بمب روی رئیس مافیا منفجر شود تیم تریتور فوراً برنده است.""",
            listOf(GODFATHER, MAFIA, TRAITOR_S, SPECIALIST_S, SCORPION, DOCTOR, DETECTIVE, CITIZEN), players = 12..13),
        MafiaScenario("specialist", "متخصص", "۱۰ نفره با لابی‌من و سایه",
            """• مافیا: گادفادر، لابی‌من، سایه. رویالیست/شهر: متخصص، مارشال، رنجر، آدم‌برفی، آفیسر، شوالیه، رویالیست.
• برد: همهٔ مافیا خارج ← شهر؛ مافیا برابر شهر ← مافیا؛ تساوی ندارد.
• گادفادر: شب نامیرا؛ شلیک؛ هر شب می‌تواند به جای شلیک نقش یک شهروند را «طلسم» کند (درمان متخصص مانعش نیست)؛ اولین طلسم اشتباه ← توانایی طلسم برای همیشه می‌سوزد.
• شلیک تیم مافیا روی سایه باعث مرگ سایه می‌شود.
• لابی‌من: ساید یک نفر را برای اعلام وضعیت آینده لابی می‌کند.
• نسخهٔ پیشرفتهٔ سناریوی رویال برای ۱۰ نفر؛ تنها یک تیم برنده است و تساوی ندارد.
• ترتیب شب: شوالیه ← تیم مافیا (به جز سایه) ← سایه ← متخصص ← مارشال ← رنجر ← آفیسر ← آدم‌برفی.
• متخصص در کل بازی ۵ داروی شفابخش دارد و خودش تقسیمشان می‌کند. رنجر شات شب مافیا را ردیابی می‌کند. لابی‌من در نبود گادفادر شلیک دارد.
• گادفادر در شب نامیراست و تا زنده است استعلامش شهروندی است؛ تصمیم شات و تأیید لابی با اوست.
• سایه (خاموش، تنها بیدار می‌شود): «پوش» — اگر فرد انتخابی فردا وارد دفاعیه (یا دوئل مارشال) شود و رأی خروج نیاورد، پوش شده و خارج می‌شود؛ در سه نفر آخر کار نمی‌کند.
• مارشال: کارت مأموریت دوئل را شب به یک نفر می‌دهد که تا پیش از رأی‌گیری فردا می‌تواند دوئل اعلام کند.
• رنجر: اگر هدف شات مافیا را درست ردیابی کند فرصت شلیک انتقام دارد (به گادفادر، شوالیهٔ زره‌پوش و فرد نجات‌یافته نمی‌رسد).
• آدم‌برفی: شب اول یک گلولهٔ برف آگاهی پرتاب می‌کند؛ روی مافیا (جز گادفادر) آب می‌شود. آفیسر: هر شب یکی از دو قابلیت «کشف» یا «بازجویی». شوالیه: یک شب شیلد می‌پوشد. رویالیست: شهروند ساده.
• حد نصاب دفاع و خروج: ۸ تا ۱۰ نفر ← ۴ رأی.""",
            listOf(GODFATHER, LOBBYIST, SHADOW_M, EXPERT, MARSHAL, RANGER, SNOWMAN, OFFICER, KNIGHT, ROYALIST),
            presets = mapOf(10 to m(GODFATHER to 1, LOBBYIST to 1, SHADOW_M to 1, EXPERT to 1, MARSHAL to 1, RANGER to 1, SNOWMAN to 1, OFFICER to 1, KNIGHT to 1, ROYALIST to 1)),
            names = mapOf(GODFATHER to "گادفادر"), wake = mapOf(KNIGHT to 2, SHADOW_M to 26, EXPERT to 30, MARSHAL to 31, RANGER to 32, OFFICER to 33, SNOWMAN to 34),
            setup = ScenarioSetup(thresholds = mapOf(10 to 4, 9 to 4, 8 to 4)), auto = false),
        MafiaScenario("representative", "نماینده", "نماینده‌های روز و رأی خیانت",
            """• دن: استعلام منفی؛ «رأی خیانت» می‌تواند رأی هدف یکی از نماینده‌ها را یکی کم یا زیاد کند.
• یاغی: پس از خروج رئیس یا هکر به ترور تبدیل می‌شود و تا پیش از رأی می‌تواند یک نفر را با خود ببرد.
• هکر: هر شب مسیر توانایی یک بازیکن را مختل می‌کند.
• دکتر: یک نجات شب؛ نجات خود مجموعاً دو بار.
• راهنما: اگر هدف شهر باشد بیدار می‌شود و استعلام می‌گیرد؛ اگر مافیا باشد هویت راهنما را می‌فهمد.
• نقش‌های تکمیلی: مین‌گذار، وکیل، محافظ، شهروند ساده؛ در تعداد بالا ناتو و سرباز.
• روز: انتخاب نماینده‌ها؛ هر نماینده هدف معرفی می‌کند؛ رأی خیانت پیش از نهایی شدن رأی اعمال می‌شود (راوی رأی‌ها را دستی تنظیم می‌کند).
• تعداد مجاز ۱۰، ۱۲ و ۱۳ نفر؛ در ۱۲ و ۱۳ نفر ناتو به مافیا و سرباز و شهروند ساده به شهر اضافه می‌شوند.
• روز اول: هر نفر از سر صحبت یک نماینده انتخاب می‌کند؛ بیشترین رأی نمایندهٔ اول؛ رأی دوم از انتهای صحبت برای نمایندهٔ دوم (تساوی ← هر دو نماینده).
• هکر دو نفر را به ترتیب انتخاب می‌کند؛ اگر نفر اول روی نفر دوم کاری کرده باشد خنثی می‌شود. محافظ هر شب یک نفر را در برابر یاغی محافظت می‌کند. وکیل یک بار کسی را از دفاعیهٔ فردا معاف می‌کند. سرباز یک تیر دارد که به کسی می‌دهد؛ دادن به مافیا ← خروج خود سرباز.
• مین‌گذار یک بار جلوی خانهٔ یک نفر مین می‌گذارد؛ اگر مافیا به او شات بزند مین فعال می‌شود و مافیا به دستور رئیس یک فدایی از خودش می‌دهد (حتی اگر پزشک نجات دهد). مافیا یک بار در بازی می‌تواند هم‌زمان با شات مین را خنثی کند.
• راهنما اگر یک مافیا را انتخاب کند، هویتش برای مافیا افشا می‌شود. دفاعیه: هر نماینده یک اتهام، ۲۰ ثانیه؛ متهم می‌تواند یک نفر را برای دفاع از خود انتخاب کند.""",
            listOf(GODFATHER, HACKER_M, REBEL, NATO, GUIDE, DOCTOR, MINER, BODYGUARD_C, LAWYER, SOLDIER, CITIZEN),
            presets = mapOf(10 to m(GODFATHER to 1, HACKER_M to 1, REBEL to 1, GUIDE to 1, DOCTOR to 1, MINER to 1, BODYGUARD_C to 1, LAWYER to 1, CITIZEN to 2),
                12 to m(GODFATHER to 1, HACKER_M to 1, REBEL to 1, NATO to 1, GUIDE to 1, DOCTOR to 1, MINER to 1, BODYGUARD_C to 1, LAWYER to 1, SOLDIER to 1, CITIZEN to 2),
                13 to m(GODFATHER to 1, HACKER_M to 1, REBEL to 1, NATO to 1, GUIDE to 1, DOCTOR to 1, MINER to 1, BODYGUARD_C to 1, LAWYER to 1, SOLDIER to 1, CITIZEN to 3)),
            names = mapOf(GODFATHER to "دن", BODYGUARD_C to "محافظ"), auto = false),
        MafiaScenario("capo", "کاپو", "دن، جادوگر، جلاد و عطار",
            """• دن: استعلام منفی؛ یک پادزهر برای سم عطار؛ قابلیت سوداگری روی شهروند ساده یا مظنون.
• جادوگر: هر شب یک شهروند را هدف می‌گیرد؛ اگر هدف توانایی‌دار باشد اثر توانایی روی خودش برمی‌گردد.
• جلاد: حدس نقش؛ حدس درست هدف را خارج می‌کند؛ به جای شلیک.
• خبرچین: جاسوس مافیا؛ اگر کدخدا او را بیدار کند شهروند ظاهر می‌شود.
• شهر (۱۰ نفر): کارآگاه، مظنون، زره‌ساز، عطار، وارث، شهروند ساده ×۲؛ در تعداد بالاتر خبررسان و کدخدا.
• روز اول با تفنگ کاپو (معتمد شهر و دو تفنگ) به جای رأی‌گیری.
• ۱۲ نفره: + خبرچین (مافیا) و کدخدا (شهر)؛ ۱۳ نفره: + یک شهروند ساده.
• وارث: در شب معارفه یک نفر را انتخاب می‌کند؛ اگر شهروند باشد تا او در بازی است وارث با تیر مافیا نمی‌میرد؛ انتخاب مافیا ← قابلیت از دست می‌رود. وارث قابل سوداگری نیست.
• زره‌ساز: هر شب یک نفر را در برابر تیر مافیا زره می‌پوشاند؛ خودش را یک شب در کل بازی.
• تفنگ کاپو: پایان روز اول رأی اعتماد؛ معتمد دو فشنگ دارد (یکی جنگی، یکی مشقی)؛ به یک نفر دو بار نمی‌شود شلیک کرد.
• ترتیب شب: تیم مافیا (جادوگر تنها اکت می‌کند) ← زره‌ساز ← کارآگاه ← عطار ← وارث.
• استعلام کارآگاه: مظنون، جلاد و جادوگر همیشه مثبت؛ دن منفی. اگر جادوگر کارآگاه را بزند، استعلام به خود کارآگاه برمی‌گردد. دن در کل بازی یک سوداگری دارد.""",
            listOf(GODFATHER, WITCHER, NATO, INFORMANT, DETECTIVE, SUSPECT, DOCTOR, ATTAR, INHERITOR, HEADMAN, CITIZEN),
            presets = mapOf(10 to m(GODFATHER to 1, WITCHER to 1, NATO to 1, DETECTIVE to 1, SUSPECT to 1, DOCTOR to 1, ATTAR to 1, INHERITOR to 1, CITIZEN to 2),
                12 to m(GODFATHER to 1, WITCHER to 1, NATO to 1, INFORMANT to 1, DETECTIVE to 1, SUSPECT to 1, DOCTOR to 1, ATTAR to 1, INHERITOR to 1, HEADMAN to 1, CITIZEN to 2),
                13 to m(GODFATHER to 1, WITCHER to 1, NATO to 1, INFORMANT to 1, DETECTIVE to 1, SUSPECT to 1, DOCTOR to 1, ATTAR to 1, INHERITOR to 1, HEADMAN to 1, CITIZEN to 3)),
            wake = mapOf(DOCTOR to 30, DETECTIVE to 31, ATTAR to 32, INHERITOR to 33),
            names = mapOf(GODFATHER to "دن", DOCTOR to "زره‌ساز", NATO to "جلاد"),
            setup = ScenarioSetup(capo = true)),
        MafiaScenario("eminence", "عالیجناب", "خائنین در برابر وفاداران",
            """• ۱۲ نفره؛ تیم‌ها: خائنین و وفاداران.
• جان میلتون: رئیس؛ شلیک شب؛ «قبض روح» با حدس دقیق نقش.
• شرور: یک وفادار را تهدید می‌کند؛ اگر دو شب روی یک هدف ادامه دهد و هنری نجاتش نداده باشد، هدف خارج می‌شود.
• هانیبال: یک وفادار ساده را به خائنین جذب می‌کند. خائن: بدون توانایی خاص.
• هنری: نجات؛ در ۱۲ نفر نجات خود دو بار. آرتور: دو استعلام. فردریگ: شناسایی کارت.
• لوئیس (وفادار): یک سپر هدیه می‌دهد؛ با خروج او خائنین شب بعد کاری ندارند. حد نصاب: ۱۱ و ۱۲ نفر ← ۵ رأی، ۸ تا ۱۰ ← ۴ رأی.""",
            listOf(MILTON, VILLAIN, HANNIBAL, TRAITOR, HENRY, ARTHUR, FREDERICK, CITIZEN), players = 12..12,
            presets = mapOf(12 to m(MILTON to 1, VILLAIN to 1, HANNIBAL to 1, TRAITOR to 1, HENRY to 1, ARTHUR to 1, FREDERICK to 1, CITIZEN to 5)),
            names = mapOf(CITIZEN to "وفادار ساده", TRAITOR to "خائن"), setup = ScenarioSetup(thresholds = mapOf(12 to 5, 11 to 5, 10 to 4, 9 to 4, 8 to 4)), auto = false),
        MafiaScenario("el_clasico", "ال کلاسیکو", "پابلو اسکوبار و چرچیل مستقل",
            """• پابلو اسکوبار: رئیس با شلیک و سلاخی (حدس نقش، هر شب ممکن)؛ در برابر مرگ معمولی نامیرا ولی با شناسایی درست هویت خارج می‌شود.
• خوآن: هر شب توانایی یک شهروند را می‌گیرد؛ دو شب پشت سر هم یک هدف ممنوع.
• بلانکو: پس از خروج حداقل یک مافیا شهروند ساده یا چاپلین را می‌خرد.
• چرچیل (مستقل): از روز دوم پیش‌بینی می‌کند چه کسی فردا خارج می‌شود؛ شب اول یک زره.
• سه نفر آخر: چرچیل + یک مافیا + یک شهر ← برد چرچیل؛ چرچیل + دو شهر ← باید یکی را برای دست دادن قانع کند؛ چرچیل + دو مافیا ← باخت.
• شهر: دکتر بریجت (نجات؛ خودش دو بار، نه دو شب پیاپی)، مورنو (تفنگ جنگی و مشقی)، بناپارت (بیدار کردن با کد)، مارتینز (یک شلیک و یک سلاخی)، چاپلین (شب اول یک شهروند ساده را از خرید مصون می‌کند)، شهروند ساده.
• کارت‌های خروج مرجع: حدس نقش (در بازی می‌مانید)، سرگیجه (۲۴ ساعت بی‌اثر)، دال (۲۴ ساعت نه شلیک نه رأی)، مانی‌سا (توانایی شب دو برابر)، آینه (جابه‌جایی هویت). هر پنج کارت در برنامه هست («هوش و ذکاوت» همان حدس نقش است).""",
            listOf(PABLO, JUAN, BLANCO, MAFIA, CHURCHILL, BRIDGET, MORENO, BONAPARTE, MARTINEZ, CHAPLIN, CITIZEN), cards = listOf(MafiaLastCard.INTELLIGENCE, MafiaLastCard.VERTIGO, MafiaLastCard.DAL, MafiaLastCard.MANISA, MafiaLastCard.MIRROR)),
        MafiaScenario("classic10", "کلاسیک ۱۰ نفره", "ترکیب دقیق ۱۰ نفره",
            """• مافیا ×۳: رئیس مافیا، مافیای ساده ×۲. شهر ×۷: کارآگاه، دکتر، شهروند ساده ×۵.
• دقیقاً ۱۰ بازیکن؛ مافیا در شب معارفه همدیگر را می‌شناسند؛ هر بازیکن روزانه یک چالش.
• رأی روز اول اختیاری و از روز دوم اجباری؛ کشتهٔ شب حق وصیت دارد.""",
            listOf(GODFATHER, MAFIA, DETECTIVE, DOCTOR, CITIZEN), presets = mapOf(10 to m(GODFATHER to 1, MAFIA to 2, DETECTIVE to 1, DOCTOR to 1, CITIZEN to 5)),
            names = mapOf(GODFATHER to "رئیس مافیا"), players = 10..10, auto = false),
        MafiaScenario("cover", "کاور", "رئیس مافیا بعد از معارفه انتخاب می‌شود (لایه)",
            """• در شروع رئیس مافیای از پیش تعیین‌شده وجود ندارد. بعد از شب معارفه، اعضای مافیا یک بازیکن را به عنوان رئیس پیشنهادی انتخاب می‌کنند.
• هدف می‌تواند پیشنهاد را قبول یا رد کند؛ اگر قبول کند نقش قبلی خود را از دست داده و رئیس می‌شود (با ابزار راوی «تغییر نقش»).
• نقش «مجهول» جایگزین این مبادله است.""",
            emptyList()),
        MafiaScenario("target", "تارگت", "رئیس یارانش را انتخاب می‌کند (لایه)",
            """• در قرعهٔ اولیه یک بازیکن با کارت «رئیس» مشخص می‌شود و در فاز آماده‌سازی بیدار می‌شود.
• رئیس سناریوی پایه را انتخاب و بر اساس تعداد جای مافیا، یاران خود را انتخاب می‌کند.
• سپس نقش‌های واقعی پخش می‌شوند و منتخب‌ها روی جای مافیا قرار می‌گیرند (توزیع دستی در صفحهٔ توزیع نقش).""",
            emptyList()),
        MafiaScenario("hannibal", "هانیبال", "خرید در معارفه و اثر سایه",
            """• در شب معارفه، هانیبال می‌تواند به یک شهروند ساده پیشنهاد خرید بدهد؛ اگر بازیکن رد کرد، فردا دربارهٔ آن صحبت مستقیم نکند (تخلف ← خروج انضباطی).
• سایه: روی یک هدف دو شب پشت سر هم کار نمی‌کند. اثر سایه بدون حفاظت قهرمان: کارآگاه ← استعلام‌ها منفی؛ دکتر ← نجات روی خودش برمی‌گردد؛ معمار ← کارت روی خودش؛ انتقام‌جو ← در صورت کار خودش خارج می‌شود؛ اسنایپر الیت ← شلیک برگشتی.
• رئیس: یک شب به جای شلیک حدس نقش دارد.
• ترتیب شب (۱۰ نفره): قهرمان ← سایه ← هانیبال ← تیم مافیا ← کارآگاه ← دکتر ← معمار ← انتقام‌جو. در ۱۲ و ۱۳ نفر ناتو و اسنایپر الیت اضافه می‌شوند.""",
            listOf(GODFATHER, HANNIBAL, SHADOW_M, MAFIA, DETECTIVE, DOCTOR, HERO_P, ARCHITECT, AVENGER, ELITE_SNIPER, CITIZEN),
            names = mapOf(GODFATHER to "رئیس", HERO_P to "قهرمان", SHADOW_M to "سایه"),
            wake = mapOf(HERO_P to 2, SHADOW_M to 3, HANNIBAL to 4, DETECTIVE to 30, DOCTOR to 31, ARCHITECT to 32, AVENGER to 33)),
        MafiaScenario("wild_west", "غرب وحشی", "دوئل به جای رأی خروج",
            """• ۸ نفر؛ سه ساید: خوب (شهر)، بد (تبهکار)، زشت (مستقل).
• کشتن شبانهٔ معمولی وجود ندارد؛ شب‌ها بیشتر مجوز و اطلاعات است. شلیک‌ها در روز است.
• به جای رأی خروج، دوئل: نامزد دوئل نصف به علاوهٔ یک رأی می‌گیرد، حریف را انتخاب می‌کند، یک موافق و یک مخالف صحبت می‌کنند، دو دوئل‌کننده صحبت می‌کنند، رأی نهایی نصف به علاوهٔ یک؛ سپس هر دو تاس می‌اندازند و عدد بیشتر خارج می‌شود (تساوی = لغو).
• کلانتر: پنج تفنگ در بازی؛ شب اول به دو نفر مجوز خرید می‌دهد.""",
            listOf(SHERIFF_W, WILSON, BOUNTY, CITIZEN, DOCTOR, DETECTIVE), players = 8..8),
        MafiaScenario("pro_club", "باشگاه حرفه‌ای‌ها", "تهمت‌زن، خرابکار و تفنگدار",
            """• ادعای نقش آزاد؛ روز معارفه ندارد؛ شروع‌کننده تصادفی از داوطلبان؛ هر بازیکن دو نوبت ۴۰ ثانیه.
• چالش معمولی ندارد؛ در پایان یک داوطلب تصادفی چالش مشترک یک‌دقیقه‌ای می‌گیرد.
• رأی هم‌زمان: ۲ رأی یا بیشتر ← دفاع؛ بیش از ۵۰٪ ← خروج مستقیم. بعد از دفاع فقط افراد دفاعیه رأی می‌دهند؛ تساوی دوم ← بدون خروج. خروج ← وصیت و اعلام نقش.
• مافیا: رئیس (شلیک، استعلام منفی)، تهمت‌زن (پاپوش)، خرابکار. شهر: کارآگاه، پزشک (بدون نجات خود)، تفنگدار، شهروند ساده ×۴.""",
            listOf(GODFATHER, FRAMER, WRECKER, DETECTIVE, DOCTOR, GUNMAN, CITIZEN),
            presets = mapOf(10 to m(GODFATHER to 1, FRAMER to 1, WRECKER to 1, DETECTIVE to 1, DOCTOR to 1, GUNMAN to 1, CITIZEN to 4)),
            names = mapOf(GODFATHER to "رئیس", DOCTOR to "پزشک"), setup = ScenarioSetup(challenge = false), auto = false),
        MafiaScenario("shahnameh", "شاهنامه", "اهریمن در برابر روشنایی",
            """• دو جناح: اهریمن و روشنایی؛ روز معارفه ندارد.
• اهریمن‌ها: ضحاک (رئیس و شلیک)، بوف (یک پر زهرآلود)، افراسیاب. روشنایی: دو یار ساده و پنج نقش ویژه (رستم، جاماسب، سیمرغ و ...).
• نوع پر به بازیکن گفته نمی‌شود؛ راوی فقط می‌گوید «پر داری». تیم اهریمن در آن شب بین شلیک و استفادهٔ پر یکی را انتخاب می‌کند.""",
            listOf(ZAHHAK, OWL, AFRASIAB, RUSTAM, JAMASB, SIMORGH, DOCTOR, CITIZEN),
            names = mapOf(CITIZEN to "یار ساده")),
        MafiaScenario("ahriman_rise", "ظهور اهریمن", "۱۰ تا ۱۳ نفر با روشنا و استاد",
            """• ۱۰ نفر: اهریمن، تاریک، بچه اهریمن؛ روشنایی: تیرانداز، طبیب، شوالیه، روشنا، شهروند ساده ×۳.
• ۱۲ نفر: + روانگر برای اهریمن و استاد برای روشنایی. ۱۳ نفر: + یک شهروند ساده.
• روانگر هدف استاد را پیش‌بینی می‌کند؛ تاریک شوالیه یا هدفش را پیش‌بینی و استعلامش را برعکس می‌کند؛ بچه اهریمن یک بار دعوت روشنا را پیش‌بینی می‌کند.""",
            listOf(AHRIMAN, DARK, AHRIMAN_CHILD, PSYCHER, ARCHER, DOCTOR, KNIGHT, ROSHANA, MASTER, CITIZEN),
            presets = mapOf(10 to m(AHRIMAN to 1, DARK to 1, AHRIMAN_CHILD to 1, ARCHER to 1, DOCTOR to 1, KNIGHT to 1, ROSHANA to 1, CITIZEN to 3),
                12 to m(AHRIMAN to 1, DARK to 1, AHRIMAN_CHILD to 1, PSYCHER to 1, ARCHER to 1, DOCTOR to 1, KNIGHT to 1, ROSHANA to 1, MASTER to 1, CITIZEN to 3),
                13 to m(AHRIMAN to 1, DARK to 1, AHRIMAN_CHILD to 1, PSYCHER to 1, ARCHER to 1, DOCTOR to 1, KNIGHT to 1, ROSHANA to 1, MASTER to 1, CITIZEN to 4)),
            names = mapOf(DOCTOR to "طبیب"), auto = false),
        MafiaScenario("secret_army", "ارتش سری", "۱۰ نفره: نجات، گشتاپو و مشتری‌ها",
            """• دقیقاً ۱۰ نفر. گروه نجات (۵): کرتیس، پارتیزان ×۲، دکتر پاسکال، خلبان. گشتاپو (۳): کسلر، راینهارت، گشتاپو. مستقل (۲): مشتری ×۲.
• اسلحهٔ اولیه (کل ۷): کرتیس، دو پارتیزان، کسلر، راینهارت، گشتاپو هر کدام ۱ و گرداننده ۱. دکتر، خلبان و مشتری‌ها اسلحه ندارند.
• شب معارفه: کسلر، خلبان و دکتر علامت می‌دهند؛ کرتیس فقط می‌فهمد این سه نفر کسلر، خلبان و دکترند؛ سپس راینهارت و گشتاپو بیدار می‌شوند و کسلر خودش را معرفی می‌کند.
• چرخه: گفتگوی روز ← گفتگوی خصوصی یا شلیک ← نتیجهٔ شلیک یا شب اضطراری ← کار راینهارت ← رأی اضطراری ← استعلام وضعیت ← بررسی برد.
• گفتگوی خصوصی: هر دور حداکثر ۲؛ هر بازیکن در کل بازی یکی. دعوت‌کننده می‌تواند دروغ بگوید؛ دعوت‌شده باید ساید واقعی را بگوید (به جز کسلر و خلبان).
• هر بازیکن فقط یک شلیک در کل بازی؛ هر دور حداکثر ۲ شلیک. بازیکن مسلح بدون شلیک که خارج شود، اسلحه‌اش به گرداننده برمی‌گردد.
• مشتری با گرفتن اسلحه از یک جناح به همان جناح می‌پیوندد (ابزار راوی ← تغییر ساید).
• خلبان: زره اولین شلیک و اولین خروج با رأی؛ دکتر روی او و کسلر اثر ندارد. کسلر: زره اولین شلیک.
• دکتر پاسکال: مجموعاً دو نجات؛ قربانی باید حدس دکتر را بگوید.
• راینهارت: پیش از رأی اضطراری بازیکن و نقش را حدس می‌زند؛ درست = خروج؛ اولین اشتباه یک شانس دیگر دارد.
• اگر در یک دور هیچ شلیکی نشد ← شب اضطراری، سپس رأی؛ حد نصاب ۵ رأی؛ تساوی ← کسی خارج نمی‌شود.
• برد: همهٔ گشتاپو حذف ← نجات؛ برابری گشتاپو و نجات ← گشتاپو. آشوب: اگر فقط خلبان، یک نجات و یک گشتاپو بمانند، خلبان با تفنگ ویژه یکی را می‌زند و طرف مقابل برنده است.""",
            listOf(CURTIS, PARTISAN, PASCAL, PILOT, KESSLER, REINHART, GESTAPO, CUSTOMER),
            presets = mapOf(10 to m(CURTIS to 1, PARTISAN to 2, PASCAL to 1, PILOT to 1, KESSLER to 1, REINHART to 1, GESTAPO to 1, CUSTOMER to 2)),
            players = 10..10, setup = ScenarioSetup(noNight = true, thresholds = mapOf(10 to 5, 9 to 5, 8 to 4, 7 to 4, 6 to 3, 5 to 3, 4 to 2), noCards = true), auto = false)
    )

    /** «سناریوی آزاد»: every role, nothing fixed. */
    val free = MafiaScenario(FREE, "سناریوی آزاد", "همهٔ نقش‌ها و کارت‌ها؛ ترکیب کاملاً دلخواه",
        "• همهٔ نقش‌های برنامه و نقش‌هایی که خودتان ساخته‌اید در دسترس است.\n• هیچ ترکیب یا قانون ثابتی تحمیل نمی‌شود.", emptyList(), auto = false)

    /** How completely each scenario can be played with the app's own roles and cards (from the scenario data). */
    val status: Map<String, String> = mapOf(
        "classic" to "✅ کامل", "classic_advanced" to "⚙️ چارچوب آزاد", "godfather" to "✅ کامل", "godfather_jack" to "✅ کامل",
        "godfather_sherlock" to "✅ کامل", "investigator" to "✅ کامل", "nato_commando" to "🟢 تقریباً کامل (گروگانگیر = افسونگر)",
        "zodiac" to "✅ تقریباً کامل، بدون کارت", "advanced" to "⚙️ چارچوب آزاد", "mafia_nights" to "🟡 مرجع ناقص؛ مجموعهٔ نقش برنامه",
        "court" to "🟡 نقش‌ها کامل؛ ترکیب نامشخص", "masked" to "🟡 نقش‌ها کامل؛ ترکیب نامشخص", "filimo" to "✅ کامل",
        "negotiation" to "✅ کامل", "saghi" to "🟠 ناقص", "epic" to "🔴 هستهٔ اختصاصی", "black_army" to "🔴 هستهٔ اختصاصی",
        "kafir" to "🟠 ناقص", "white_show" to "✅ نقش‌ها کامل", "mythic" to "🟠 ناقص", "scam" to "✅ کامل",
        "red_status" to "🟡 فقط ۶ یا ۹ نفره تأیید شد", "pure_classic" to "✅ کامل", "weather" to "🔴 منبع معتبری پیدا نشد؛ قوانین از سند شما", "joker_mafia" to "🟡 قابل ساخت",
        "elixir" to "🔴 منبع معتبری پیدا نشد؛ قوانین از سند شما", "thief_gangster" to "✅ ۱۳ نفره کامل", "parallel" to "⚙️ لایه روی بازپرس", "triple" to "🔴 منبع معتبری پیدا نشد؛ قوانین از سند شما",
        "molagh" to "🔴 نقش‌های اختصاصی در برنامه نیست", "scorpion" to "🔴 منبع معتبری پیدا نشد؛ قوانین از سند شما", "specialist" to "✅ کامل",
        "representative" to "✅ ۱۰، ۱۲ و ۱۳ نفره", "capo" to "✅ ۱۰، ۱۲ و ۱۳ نفره", "eminence" to "🟡 ۱۲ نفره؛ نقش لوئیس دستی", "el_clasico" to "✅ نقش‌ها و کارت‌ها کامل",
        "classic10" to "✅ کامل", "cover" to "⚙️ لایه روی سناریوی پایه", "target" to "⚙️ لایه روی سناریوی پایه", "hannibal" to "🟡 ترتیب شب کامل؛ ترکیب نامشخص",
        "wild_west" to "🟠 ناقص", "pro_club" to "🟡 تهمت‌زن = تغییر استعلام", "shahnameh" to "🔴 نقش‌های اسطوره‌ای ساخته‌شده",
        "ahriman_rise" to "🔴 نقش‌های اختصاصی ساخته‌شده", "secret_army" to "🔴 نقش‌های اختصاصی ساخته‌شده"
    )

    fun isBuiltin(id: String) = id == FREE || builtin.any { it.id == id }

    /** Built-in scenarios (with the narrator's saved edits in place), then his own scenarios, then «سناریوی آزاد». */
    fun all(saved: List<MafiaScenario>): List<MafiaScenario> {
        val byId = saved.associateBy { it.id }
        return builtin.map { byId[it.id] ?: it } + saved.filter { !isBuiltin(it.id) } + (byId[FREE] ?: free)
    }

    fun original(id: String): MafiaScenario? = if (id == FREE) free else builtin.firstOrNull { it.id == id }

    fun find(id: String?, custom: List<MafiaScenario>): MafiaScenario? = if (id.isNullOrBlank()) null else all(custom).firstOrNull { it.id == id }

    /** Stores a scenario's fixed settings on the game (read by voting, speaking, night and inquiries). */
    fun applyTo(g: MafiaGame, s: MafiaScenario?) {
        KEYS.forEach { g.extra.remove(it) }
        if (s == null || s.id == FREE) return
        g.extra["scenario"] = s.id
        if (!s.setup.challenge) g.extra["noChallenge"] = "1"
        if (s.setup.noNight) g.extra["noNight"] = "1"
        if (s.setup.noMafiaIntro) g.extra["noMafiaIntro"] = "1"
        if (s.setup.noIntroDay) g.extra["noIntroDay"] = "1"
        if (s.setup.thresholds.isNotEmpty()) g.extra["thresholds"] = s.setup.thresholds.entries.joinToString(",") { "${it.key}:${it.value}" }
        s.setup.queryCount?.let { g.extra["queryCount"] = it.toString() }
        s.setup.edge?.let { g.extra["edge"] = it }
        s.setup.capo?.let { g.extra["scenarioCapo"] = if (it) "1" else "0" }
    }
}
