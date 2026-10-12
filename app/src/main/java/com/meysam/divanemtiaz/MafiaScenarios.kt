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
        MafiaScenario("nato_commando", "ناتو / تکاور", "نگهبان، گروگانگیر، تکاور و تفنگدار",
            """• ترتیب شب: نگهبان ← گروگانگیر ← تیم مافیا بدون گروگانگیر ← کارآگاه ← تکاور ← پزشک ← تفنگدار.
• تکاور فقط در شرط فعال شدن خودش تیر دارد؛ اگر هدف رئیس مافیا باشد تعامل مخصوص رئیس اعمال می‌شود و اگر مافیای دیگر باشد حذف معتبر است. «شلیک نکن» هم مجاز است.
• پزشک: نجات خود محدود؛ در تعداد بالا امکان نجات دو نفر.
• نگهبان هدف‌های مجاز را محافظت می‌کند و می‌تواند گروگانگیر را بلاک کند.
• تفنگدار: تفنگ مشقی و جنگی توزیع می‌کند.
• حد نصاب ورود به دفاع: ۱۰، ۹ و ۸ نفر ← ۴؛ ۷ و ۶ ← ۳؛ ۵ و ۴ ← ۲.
• تعداد بازیکنان: ۱۰، ۱۲ یا ۱۳ نفر. ترکیب ۱۰ نفره: رئیس مافیا، ناتو، گروگانگیر؛ کارآگاه، پزشک، تکاور، نگهبان، تفنگدار، ۲ شهروند ساده.
• ناتو: یک بار در بازی با تأیید رئیس و به جای شات؛ حدس درست ← هدف حتی با نجات پزشک خارج می‌شود؛ نگهبان در برابر ناتو مصون است.
• گروگانگیر تنها بیدار می‌شود و هر شب یک نفر را بلاک می‌کند (دو شب پیاپی یک نفر نه). نگهبان با ۸ نفر یا بیشتر از ۲ نفر و کمتر از آن از ۱ نفر محافظت می‌کند و در روز زره دارد.
• تفنگدار: ۱ تفنگ جنگی و تفنگ مشقی نامحدود، هر شب تا ۲ تفنگ؛ بعد از شلیک جنگی ساید هدف اعلام می‌شود. تکاور فقط اگر مافیا به او شات بزند حق شلیک دارد.
• روز معارفه چالش ندارد.""",
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
• حرفه‌ای: شلیک شب به مافیا؛ شلیک به شهروند ← خروج خودش؛ شلیک به زودیاک بی‌اثر.
• ترکیب ۱۲ نفره: مافیا: آل‌کاپن، بمب‌گذار، شعبده‌باز؛ شهر: کارآگاه، پزشک، محافظ، حرفه‌ای، تفنگدار، اوشن، ۲ شهروند ساده؛ مستقل: زودیاک.
• استعلام کارآگاه: فقط بمب‌گذار و شعبده‌باز مثبت. زودیاک با هیچ شلیک شبی خارج نمی‌شود؛ فقط با تفنگ روز، بمب خنثی‌نشده یا رأی.
• بمب: یک بار با کد ۱ تا ۴؛ در خواب نیمروز محافظ می‌تواند به جای هدف کد را بزند (درست ← هر دو می‌مانند؛ غلط ← فقط محافظ خارج)؛ وگرنه خود هدف کد را حدس می‌زند.
• اوشن در کل دو بار بیدار می‌کند؛ بیدار کردن مافیا یا زودیاک ← اوشن فردا خارج می‌شود (حتی با نجات پزشک).""",
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
        MafiaScenario("mafia_nights", "شب مافیا", "مجموعهٔ کامل نقش‌های شب مافیا",
            """• بازیکنان به دو دستهٔ اکثریت شهروند و اقلیت مافیا تقسیم می‌شوند؛ جز صاحب نقش و گرداننده کسی از نقش دیگران مطلع نیست، اما گروه مافیا در شب اول یکدیگر را می‌شناسند.
• هدف شهروندان پاک‌سازی شهر از مافیاست؛ بازی با شب آغاز می‌شود و در شب‌ها و روزهای متوالی ادامه می‌یابد.
• روز: همه نقش شهروند را بازی می‌کنند و با اتهام، دفاع و رأی‌گیری می‌کوشند مافیا را بیرون کنند. افشای نقش یا فهمیدن نقش دیگری با تقلب باعث اخراج بازیکن توسط گرداننده می‌شود.
• رأی‌گیری دور اول برای همهٔ بازیکنان حاضر انجام می‌شود؛ بازیکنانی که رأی حداقل نیمی از افراد حاضر را بگیرند وارد دفاعیه می‌شوند.
• پس از دفاعیه، رأی‌گیری دور دوم با چشمان باز (اگر فقط یک نفر در دفاعیه باشد) یا با چشمان بسته در «خواب نیمروزی» (اگر بیش از یک نفر باشد) انجام می‌شود؛ بازیکنان حاضر در دفاعیه حق رأی ندارند؛ در دور دوم هر کس یک انتخاب دارد.
• خروج: با یک نفر در دفاعیه، کسی که حداقل نیمی از آرا را بگیرد؛ با چند نفر، کسی که بیشترین رأی را بگیرد. در تساوی دو بازیکن «قرعهٔ مرگ»: بازیکنی که کارت تیک سبز را بردارد می‌ماند و دیگری خارج می‌شود.
• شب: به فرمان گرداننده گروه مافیا بیدار می‌شود و پنهانی دربارهٔ هدف شلیک مشورت می‌کند؛ سپس گرداننده برخی شهروندان را برای اجرای نقش شب بیدار می‌کند. در شبی که خوابگرد خود را فدا کند، تیم مافیا بیدار نمی‌شود و کاری انجام نمی‌دهد.
• کارت‌های حرکت آخر: مسیر سبز (بازیکن خارج‌شونده یک نفر را در مسیر سبز آن روز قرار می‌دهد تا تحت هیچ اتهامی وارد دفاعیه نشود)؛ فرش قرمز (بازیکنی را متهم می‌کند تا در هر شرایطی به دفاعیهٔ پایان روز برود)؛ ذهن زیبا (یکی از نقش‌های اعلام‌نشده را حدس می‌زند؛ اگر درست باشد به بازی برمی‌گردد؛ اگر خودش مافیا باشد فقط نقش شهروندان را می‌تواند حدس بزند)؛ بی‌خوابی (امشب کسی نمی‌خوابد، کسی کشته نمی‌شود و مستقیم به روز بعد می‌رویم)؛ سرگیجه (یک نفر را انتخاب می‌کند که تا ۲۴ ساعت قابلیت شب یا نیمروزش اعمال نمی‌شود و رأیش هم بی‌آنکه بداند شمرده نمی‌شود)؛ شلیک نهایی (به بازداشت موقت می‌رود، پس از رأی‌گیری دور دوم برمی‌گردد و به جای مافیا شلیک شب را به انتخاب خودش انجام می‌دهد؛ دکتر می‌تواند او را سیو کند و همهٔ قوانین شب اجرا می‌شود).
• شرط برد: شهروندان با پاک‌سازی شهر از مافیا برنده می‌شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود.
• تیم شهروند: شهروند ساده، حرفه‌ای، شهردار، روان‌پزشک، جان‌سخت، دکتر شهر، کارآگاه، فروشنده و خوابگرد؛ تیم مافیا: پدرخوانده، جوکر، دکتر لکتر، بادیگارد و مافیای ساده.
• اگر فروشنده جان‌سخت را انتخاب کند و جلیقهٔ جان‌سخت هنوز سالم باشد، فقط جلیقه از بین می‌رود و جان‌سخت توانایی‌اش را حفظ می‌کند.
• نقش‌ها:
• شهردار: فقط یک بار در طول بازی و در صورت حضور حداقل دو بازیکن در دفاعیه و عدم حضور خود او در دفاعیه، می‌تواند دور دوم رأی‌گیری را ملغی و از خروج یک بازیکن جلوگیری کند، یا حکم خروج مستقیم یکی از افراد حاضر در دفاعیه را بدهد.
• دکتر شهر: هر شب می‌تواند جان یک نفر را از شلیک شب نجات دهد. جان خودش را فقط یک بار می‌تواند نجات دهد.
• روان‌پزشک: دو بار در طول بازی می‌تواند قدرت مکالمهٔ بازیکنی را بابت روان‌درمانی از او بگیرد.
• کارآگاه: هر شب می‌تواند استعلام یک نفر را از گرداننده برای تعیین وضعیت شهروند یا مافیا بگیرد. اگر جوکر بازیکنی را انتخاب کرده باشد، استعلام او برعکس می‌شود. البته رئیس مافیا (پدرخوانده) همیشه به او شهروند معرفی می‌شود.
• حرفه‌ای: در کل بازی دو تیر دارد و در شب می‌تواند به یک مافیا شلیک کند؛ اما با شلیک اشتباه به شهروندان، خودش از بازی خارج می‌شود.
• جان‌سخت: با شلیک اول از سوی مافیا کشته نمی‌شود و از شلیک اول هم بی‌خبر است. گرفتن استعلام نفرات خارج‌شده از بازی به عهدهٔ اوست؛ دو بار می‌تواند استعلام وضعیت بگیرد.
• فروشنده: یک بار در طول بازی می‌تواند توانایی یک فرد را برای کل بازی از وی بگیرد. کسی که انتخاب فروشنده بوده، فردای همان روز توسط راوی اعلام می‌شود.
• شهروند ساده: قابلیت منحصربه‌فردی در شب ندارد و مهم‌ترین مشارکت او در بخش روز و رأی‌گیری است.
• خوابگرد: اولین نفر بیدار می‌شود و یک بار در طول بازی می‌تواند خود را فدا کند تا کل گروه مافیا آن شب نتوانند کاری انجام دهند؛ فردای آن شب از بازی خارج می‌شود.
• پدرخوانده (آل‌کاپون): استعلام او برای کارآگاه همیشه شهروند است و تعیین شلیک شب گروه مافیا به عهدهٔ اوست. در این سناریو سپر ندارد.
• دکتر لکتر: هر شب می‌تواند یک نفر را از شلیک حرفه‌ای نجات دهد. جان خودش را فقط یک بار می‌تواند نجات دهد.
• جوکر: عضو مافیاست و هر شب می‌تواند یکی از اعضای مافیا یا شهروندان را انتخاب کند تا در آن شب استعلام آن فرد برای کارآگاه برعکس شود. فقط یک بار می‌تواند خودش را انتخاب کند و نمی‌تواند دو شب متوالی یک بازیکن را انتخاب کند.
• مافیا ساده: از اعضای مافیا که در شب همراه پدرخوانده، دکتر لکتر و جوکر به گروه مشورت می‌دهد.
• بادیگارد: بار اول از شلیک شب آسیبی نمی‌بیند؛ تا زمانی که در بازی باشد شلیک شب روی پدرخوانده تأثیری ندارد و اگر تیری به سمت پدرخوانده شلیک شود به سمت بادیگارد می‌رود.""",
            listOf(GODFATHER, DOCTOR_LECTOR, CHARLATAN, BODYGUARD, MAYOR, DOCTOR, PSYCHOLOGIST, DETECTIVE, PROFESSIONAL, DIE_HARD, SALESMAN, SLEEP_WALKER, CITIZEN, MAFIA),
            presets = mapOf(12 to m(GODFATHER to 1, DOCTOR_LECTOR to 1, CHARLATAN to 1, BODYGUARD to 1, MAYOR to 1, DOCTOR to 1, PSYCHOLOGIST to 1, DETECTIVE to 1, PROFESSIONAL to 1, DIE_HARD to 1, SALESMAN to 1, SLEEP_WALKER to 1)),
            names = mapOf(CHARLATAN to "جوکر", DOCTOR to "دکتر شهر", PSYCHOLOGIST to "روان‌پزشک", GODFATHER to "پدرخوانده (آل‌کاپون)"),
            wake = mapOf(SLEEP_WALKER to 1, GODFATHER to 26, DOCTOR_LECTOR to 26, CHARLATAN to 26, MAFIA to 26, BODYGUARD to 26, DOCTOR to 30, DETECTIVE to 31, PROFESSIONAL to 32, PSYCHOLOGIST to 33, SALESMAN to 34, DIE_HARD to 35),
            cards = listOf(MafiaLastCard.GREEN_MILE, MafiaLastCard.RED_CARPET, MafiaLastCard.INSOMNIA, MafiaLastCard.VERTIGO, MafiaLastCard.FINAL_SHOT),
            setup = ScenarioSetup(noIntroDay = true, edge = "down", queryCount = 2), auto = false),
        MafiaScenario("court", "دربار", "درباریان در برابر رعایا",
            """• دو جناح: دربار (مافیا) و رعایا (شهر). ۱۰ نفره: سه درباری (سلطان، چریک، مزدور) در برابر هفت رعیت (مهمات‌چی، مفتش، طبیب، سربدار، کاتب، شبگرد، ریش‌سفید)؛ هر صندلی یک نقش مشخص دارد.
• در دک ۱۳ نفره جلاد (دربار)، شمشیرساز و کشیک (رعایا) اضافه می‌شوند.
• روز اول کور است.
• ترتیب شب: شبگرد ← سلطان ← مزدور ← طبیب ← مفتش ← مهمات‌چی ← شمشیرساز ← جلاد ← کشیک ← چریک ← کاتب.
• شلیک اسلحهٔ مهمات‌چی پیش از رأی‌گیری روز است و پس از آن جناح کشته‌شده اعلام می‌شود؛ اگر چریک آن اسلحه را دستکاری کرده باشد، شلیک‌کننده به جای هدف می‌میرد.
• شرط برد: شهر وقتی همهٔ اعضای مافیا حذف شوند؛ مافیا همان لحظه که تعدادش با شهروندان برابر (یا بیشتر) شود.
• نقش‌ها:
• سلطان: فردی جوان و زیرک که رئیس دربار است. هر شب به یک نفر سم می‌دهد؛ فرد مسموم صبح را نخواهد دید. می‌تواند به‌جای کشتن با سم، هویت یکی از رعایا را حدس بزند و او را تحت هر شرایطی از بازی خارج کند. در صورت کشته شدن سلطان، سایر اعضای دربار همچنان می‌توانند سم بدهند اما دیگر نمی‌توانند نقش کسی را حدس بزنند. مفتش هیچ‌گاه به درباری بودن او آگاه نخواهد شد.
• مزدور: هر شب یکی از رعایا را انتخاب می‌کند؛ اگر مفتش همان شب هویت آن فرد را تفتیش کند، او را درباری می‌بیند. فقط روی رعایا کار می‌کند نه روی دربار، و نباید دو شب پشت سر هم به چادر یک نفر برود.
• جلاد: (فقط ۱۳ نفره) دو توانایی دارد که هر دو در یک شب انجام نمی‌شود. اول: نصف شب بالای سر یک نفر می‌رود؛ اگر آن فرد برای استفاده از شمشیرِ شمشیرساز از چادرش خارج شده باشد، کشته می‌شود؛ این کار را هر شب تا وقتی شمشیر استفاده شود می‌توان انجام داد. دوم: یک بار در کل بازی یک نفر را انتخاب می‌کند؛ اگر آن شخص با رأی یا اسلحه خارج شود سایدش تا آخر بازی مجهول می‌ماند، و اگر در شب (با سم یا شمشیر) کشته شود سایدش آشکار می‌شود. اگر جلاد خودش کشته شود سایدش همیشه اعلام می‌شود؛ اگر در شب کشته شود، روز بعد.
• طبیب: طبیب هر شب یک نفر را از سم نجات می‌دهد و دو بار در بازی می‌تواند خودش را هم درمان کند.
• مفتش: مفتش هر شب یک بازیکن را تفتیش می‌کند و می‌فهمد درباری (مافیا) است یا رعیت. سلطان برای او رعیت دیده می‌شود و هدف پاپوش مزدور در همان شب درباری دیده می‌شود.
• سربدار: هیچ توانایی ندارد. اگر با رأی بیرون برود، دربار آن شب هیچ شاتی ندارد؛ بقیهٔ فعالیت‌های شب عادی انجام می‌شود. کسی خبردار نمی‌شود چرا؛ تنها کسی که شات دست اوست می‌فهمد امشب شاتی در کار نیست.
• کاتب: هر شب نام یک نفر را می‌نویسد. اگر آن فرد همان شب کشته شود، فردای آن روز پیش از رفتن وصیت دارد؛ اگر زنده بماند هیچ اتفاقی نمی‌افتد و کسی باخبر نمی‌شود.
• شبگرد: یک شب در طول بازی از چادر خود خارج می‌شود و آن شب هیچ‌کس به او دسترسی ندارد: سم بی‌اثر است، اسلحهٔ مهمات‌چی به او نمی‌رسد (و مهمات‌چی بی‌اطلاع می‌ماند) و پاپوش مزدور ناکام می‌ماند. با این حال مفتش دربارهٔ او پاسخ درست می‌گیرد.
• ریش‌سفید: یک بار در بازی، پس از آنکه رأی‌گیری روز متهمان را مشخص کرد، برای یکی از آن‌ها نزد گرداننده ریش گرو می‌گذارد و آن فرد به مرحلهٔ دوم رأی‌گیری نمی‌رود. تا زمانی که خودش متهم است نمی‌تواند برای خود وساطت کند.
• چریک: نیروی خاص دربار. هر شب کورکورانه به چادر یک رعیت می‌رود و اگر اسلحه‌ای ببیند آن را دستکاری می‌کند؛ بدین ترتیب فرد مسلح‌شده به‌محض شلیک اسلحهٔ خود، به‌جای هدفش جان خود را از دست می‌دهد. هرگز به چریک گفته نمی‌شود که چه یافته است.
• مهمات‌چی: تنها اسلحهٔ زرادخانه دست اوست و یک بار در بازی آن را به کسی می‌دهد. فرد مسلح فردای همان شب می‌تواند پیش از آغاز رأی‌گیری فریاد بزند «زنده باد وطن» و پس از تأیید گرداننده دو نفری را که بیشترین ظن را به آن‌ها دارد اعلام کند. آن دو هرکدام یک دقیقه از خود دفاع می‌کنند و سپس فرد مسلح یکی از آن دو را هدف می‌گیرد و شلیک می‌کند. پس از آن جناح فرد کشته‌شده برای همه اعلام می‌شود.
• شمشیرساز: در دک ۱۳ نفره اضافه می‌شود. حق ندارد از شمشیری که می‌سازد خودش استفاده کند. یک شب در طول بازی شمشیر را در اختیار یک نفر دیگر قرار می‌دهد و گرداننده همان شب به آن شخص می‌گوید که شمشیر دارد و می‌تواند یک نفر را بکشد؛ اگر بخواهد می‌کشد، اگر نخواهد نه، و اگر همان شب از شمشیر استفاده نکند دیگر حق استفاده ندارد. در روز آن فرد می‌تواند اعلام کند که دیشب شمشیر داشته است. شمشیر روی سلطان کارساز نیست و طبیب می‌تواند هدف شمشیر را نجات دهد.
• کشیک: در دک ۱۳ نفره اضافه می‌شود. یک شب در بازی می‌تواند از گرداننده بپرسد چه کسانی روی او اکت انجام داده‌اند؛ گرداننده فقط افرادی را که در تمام شب‌های گذشته سراغ او رفته‌اند نشان می‌دهد، بدون اعلام نقش آن‌ها.""",
            listOf(SULTAN, GUERRILLA, MERCENARY, QUARTERMASTER, DETECTIVE, DOCTOR, SARBEDAR, SCRIBE, NIGHTWALKER, ELDER, EXECUTIONER, SWORDSMITH, SENTRY),
            presets = mapOf(10 to m(SULTAN to 1, GUERRILLA to 1, MERCENARY to 1, QUARTERMASTER to 1, DETECTIVE to 1, DOCTOR to 1, SARBEDAR to 1, SCRIBE to 1, NIGHTWALKER to 1, ELDER to 1),
                13 to m(SULTAN to 1, GUERRILLA to 1, MERCENARY to 1, EXECUTIONER to 1, QUARTERMASTER to 1, DETECTIVE to 1, DOCTOR to 1, SARBEDAR to 1, SCRIBE to 1, NIGHTWALKER to 1, ELDER to 1, SWORDSMITH to 1, SENTRY to 1)),
            names = mapOf(DOCTOR to "طبیب", DETECTIVE to "مفتش", NIGHTWALKER to "شبگرد", CITIZEN to "رعیت"),
            wake = mapOf(NIGHTWALKER to 1, SULTAN to 2, MERCENARY to 3, DOCTOR to 4, DETECTIVE to 5, QUARTERMASTER to 6, SWORDSMITH to 7, EXECUTIONER to 8, SENTRY to 9, GUERRILLA to 10, SCRIBE to 11), auto = false),
        MafiaScenario("masked", "نقاب‌دار", "نقابدار، یاکوزا، ژوپیتر و رونانت",
            """• سناریوی ثابت ۱۰ نفره با روز اول کور: سه مافیا (پدرخوانده، یاکوزا، نقاب‌دار) در برابر هفت شهروند (کارآگاه، پزشک، ژوپیتر، رونانت، اونجر و دو شهروند ساده).
• نقاب‌دار و تیم مافیا یکدیگر را نمی‌شناسند.
• عضوگیری دو راه دارد: سوداگری پدرخوانده یا فرمان پدرخوانده و خرید یاکوزا (یک‌باره). در هر دو حالت با همان کلمات به میز اعلام می‌شود که معامله‌ای در جریان است و فقط شهروند ساده برگردانده می‌شود. عضو تازه همان شب در استعلام مافیا است، ولی تا شب بعد به اتاق و فهرست تیم دسترسی ندارد.
• ترتیب شب: اونجر ← پدرخوانده ← یاکوزا ← ژوپیتر ← نقاب‌دار ← پزشک ← کارآگاه.
• شرط برد: شهر وقتی همهٔ مافیا حذف شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود.
• نقش‌ها:
• پدرخوانده: رهبر مافیا و فرماندهٔ شلیک شب. در استعلام کارآگاه منفی است. به جای شلیک دو راه عضوگیری دارد: «سوداگری» — یکی از مردان خودش را می‌دهد و به جایش یک شهروند ساده را به مافیا می‌آورد — یا صرف شبش برای صدور فرمان تا یاکوزا خرید را انجام دهد. در هر دو حالت با همان کلمات به کل میز گفته می‌شود که معامله‌ای در جریان است و فقط یک شهروند ساده واقعاً برگردانده می‌شود.
• یاکوزا: عضوگیر خانواده؛ فقط در شبی که پدرخوانده به جای شلیک فرمان صادر کند یک بازیکن را نام می‌برد و تنها یک بار فرستاده می‌شود. اگر شهروند ساده (بدون پوشش ژوپیتر) باشد، عضو تازهٔ مافیا می‌شود و از همان لحظه در استعلام کارآگاه مافیا خوانده می‌شود، ولی اتاق تیم تا شب بعد به رویش بسته است؛ هر کس دیگری را نام ببرد بی‌حاصل است. یاکوزا در همان شب معامله از بازی خارج می‌شود، چه موفق شود چه نه، و دلیلش هرگز گفته نمی‌شود.
• نقابدار: مافیایی که تیمش او را نمی‌شناسد و او هم تیمش را نمی‌شناسد؛ در اتاق مافیا نیست. شلیک شبانهٔ مافیا روی او اثر ندارد (نه او خبردار می‌شود نه آن‌ها)، ولی تفنگ شهروند و رأی روز عادی به او می‌رسد. استعلامش مثبت (مافیا) است. دو شارژ دارد، از هرکدام یکی، که از شب دوم به بعد خرج می‌کند و می‌تواند هر دو را در یک شب بزند: «نقاب» — یک بازیکن (هر کس جز خودش) را نام می‌برد و از آن شب کارآگاه تا پایان بازی نمی‌تواند او را استعلام کند؛ به هیچ‌کس اعلام نمی‌شود. «نام» — نقش دقیق یک بازیکن را می‌گوید؛ اگر درست باشد آن بازیکن با جناح و همه‌چیز شهروند ساده می‌شود (حتی پدرخواندهٔ خودش)؛ اگر غلط باشد اتفاقی نمی‌افتد؛ فقط خودش از نتیجه باخبر می‌شود. وقتی پدرخوانده و یاکوزا هر دو بیرون رفتند، همان شب به میز اعلام می‌شود که نقاب افتاده و تیر مافیا به دست او رسیده (نام نقش گفته می‌شود نه صندلی).
• ژوپیتر: هر شب از شب دوم یک بازیکن را (خودش هم مجاز است) می‌پوشاند؛ آن شب هیچ عضوگیری — سوداگری پدرخوانده یا خرید یاکوزا — روی او موفق نمی‌شود و تلاش هم می‌سوزد. پوشش فقط برای همان شب است و به هیچ‌کس اعلام نمی‌شود.
• پزشک: هر شب یک بازیکن را نجات می‌دهد و می‌تواند دو تا از نجات‌هایش را روی خودش خرج کند.
• رونانت: اگر شلیک شبانهٔ مافیا به او برسد از پا نمی‌افتد؛ صبح نام و نقشش به میز اعلام می‌شود، تمام آن روز را بازی می‌کند و در پایان همان روز خارج می‌شود. نجات دکتر در همان شب همه‌چیز را خنثی می‌کند؛ دو مهاجم در یک شب او را بی‌درنگ از پا می‌اندازند؛ رأی شهر را نمی‌تواند تاب بیاورد.
• کارآگاه: هر شب یک نفر را استعلام می‌کند. پدرخوانده منفی؛ نقاب‌دار و عضو تازه‌خریده‌شده مثبت. بازیکنی که نقاب‌دار پوشانده دیگر قابل استعلام نیست.
• اونجر: شهروندی که روی شب شرط می‌بندد. هر شب از شب دوم، تا وقتی ده تا هشت نفر زنده‌اند دو بازیکن و از هفت نفر به پایین یک بازیکن را نام می‌برد؛ حدسش از اینکه تاریکی چه کسی را می‌برد. اگر تا صبح یکی از آن‌ها رفته باشد، مسلح بیدار می‌شود و تمام روز تفنگ دست اوست: بلند می‌گوید که می‌خواهد شلیک کند، نوبت را نگه می‌دارد تا دلیلش را بگوید و شلیک می‌کند؛ کسی که خورده پیش از اعلام جناحش وصیت (حرف آخر) می‌گوید. هیچ‌چیز از انتخاب او به کسی اعلام نمی‌شود و بودن تفنگ تنها نشانهٔ درست بودن حدس است. شبی که مافیا معامله (عضوگیری) می‌کند، اونجر اصلاً وارد نمی‌شود.""",
            listOf(GODFATHER, YAKUZA, MASKED, DETECTIVE, DOCTOR, JUPITER, REVENANT, ONJER, CITIZEN),
            presets = mapOf(10 to m(GODFATHER to 1, YAKUZA to 1, MASKED to 1, DETECTIVE to 1, DOCTOR to 1, JUPITER to 1, REVENANT to 1, ONJER to 1, CITIZEN to 2)),
            names = mapOf(DOCTOR to "پزشک"),
            wake = mapOf(ONJER to 1, GODFATHER to 2, YAKUZA to 3, JUPITER to 4, MASKED to 5, DOCTOR to 6, DETECTIVE to 7), auto = false),
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
• ترتیب شب: دکتر شهر ← پدرخوانده با مافیا ← دکتر لکتر ← جوکر ← کارآگاه ← حرفه‌ای ← روانپزشک ← فروشنده ← جان‌سخت.
• کارت‌های حرکت آخر (۶ کارت): مسیر سبز، فرش قرمز، شلیک نهایی، هوش و ذکاوت (حدس درست نقش یک بازیکن ← بازگشت به بازی)، بی‌خوابی، دروغ سیزده.
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
• تعداد بازیکنان: ۹، ۱۰، ۱۲، ۱۳ یا ۱۵ نفر.
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
• کارآگاه: رئیس مافیا منفی.
• نسخهٔ کم‌جمعیت (۷، ۸، ۹، ۱۰ و ۱۲ نفر): شهر: ساقی، کارآگاه، پزشک، اسنایپر، جان‌سخت، شهروند ساده؛ مافیا: پدرخوانده، دکتر لکتر، ناتو، مافیای ساده. ترتیب شب: ساقی ← مافیا ← کارآگاه ← اسنایپر ← جان‌سخت ← پزشک.
• ساقی (شهر): هدف را ۲۴ ساعت از کار می‌اندازد؛ روی کارآگاه ← استعلام برعکس؛ روی دکتر یا دکتر لکتر ← نجات انجام نمی‌شود؛ روی پدرخوانده ← شات مافیا خطا می‌رود. دو شب پیاپی یک نفر نه؛ خودش را یک بار.""",
            listOf(GODFATHER, TERRORIST, KINGSLAYER, SAGHI, MAFIA, DOCTOR, GUNMAN, JUDGE, SACRIFICE, POLICE_CHIEF, DETECTIVE, CITIZEN),
            names = mapOf(DOCTOR to "پزشک", GUNMAN to "تفنگ‌ساز", GODFATHER to "رئیس مافیا")),
        MafiaScenario("epic", "اپیک", "مستقل‌های جیگساو، ورولف و پویزنر",
            """• جیکسا: شب نامیرا؛ هر شب سه نفر را انتخاب و اره را به یکی می‌دهد؛ روز فرایند اتاق گاز اجرا می‌شود. هر بازیکن فقط یک بار هدف اتاق گاز.
• ورولف: شب‌های فرد نامیرا؛ شب زوج بیرون می‌رود و زخمی می‌کند.
• پویزنر: شب نامیرا؛ هر شب سم با تأخیر.
• برد مستقل‌ها: رسیدن به سه نفر نهایی طبق قانون هر مستقل.
• مافیا: گادفادر (شلیک)، کانسورت (مسدود کردن)، جنیتور (حذف وصیت یک شب در میان)، شدو (حدس و کنترل نقش)، دوعل‌من (تفنگ دوئل)، سایلنسر (سکوت کامل)، تروریست.
• شهر: وکیل (اجبار یا منع دفاعیه)، روژاک (ضد جیکسا)، کروسایدر (محافظت قوی)، دکتر.
• جیکسا هر شب سه نفر را برای اتاق گاز فردا انتخاب می‌کند؛ دارندهٔ اره یکی از دو نفر دیگر را حذف می‌کند.
• ورولف: شب‌های فرد در خانه می‌ماند و مهمانانش را می‌کشد؛ شب‌های زوج یا در خانه می‌ماند یا یک نفر را زخمی می‌کند (زخمی و ملاقات‌کنندگانش صبح خارج می‌شوند). کروسایدر حملهٔ شب زوج ورولف را خنثی می‌کند.
• پویزنر: سم در روز بعد اثر می‌کند. شدو نقشی را حدس می‌زند و به جای آن عمل می‌کند. سامورایی هر شب یک نفر را انتخاب می‌کند و می‌کشد یا تا شب بعد امان می‌دهد. وترن تعداد محدودی «میدان الکتریکی» فعال می‌کند. هایپر سپری دارد که کارهای مضر را برمی‌گرداند.
• شهردار دو شب اول نامیراست و می‌تواند اعلام کند و ۳ رأی اضافه کند. قاضی یک بار رأی روز را لغو می‌کند. روژاک تا جیکسا زنده است مصون است؛ اگر با یکی از انتخاب‌های جیکسا یکی شود، جیکسا به اتاق گاز می‌رود.
• تروریست در این سناریو یار خاموش مافیاست.""",
            listOf(JIGSAW, WEREWOLF, POISONER, GODFATHER, CONSORT, JANITOR, SHADOW, DUELMAN, SILENCER, TERRORIST, LAWYER, ROJAK, CRUSADER, DOCTOR, CITIZEN),
            names = mapOf(GODFATHER to "گادفادر", JIGSAW to "جیکسا")),
        MafiaScenario("black_army", "لشکر سیاه", "افزونهٔ پرجمعیت با هزارچهره",
            """• افزونه برای بازی‌های پرجمعیت روی پایهٔ شب مافیا.
• هزارچهره: مستقل اصلی؛ نقش بازیکنی را که با رأی روز خارج شده تصاحب می‌کند (نقش کشته‌های شب را نمی‌تواند بگیرد).
• نایب: حامی هزارچهره؛ برد او به بقای هزارچهره وابسته است.
• شیطان‌صفت: از شب‌های بعد چند بازیکن را به هم پیوند می‌دهد؛ اگر همه از یک ساید باشند خروج گروهی رخ می‌دهد.
• برد مستقل لشکر سیاه با رسیدن هزارچهره به وضعیت نهایی است و نباید با برابری عددی مافیا زودتر اعلام شود.
• برای جمع‌های بزرگ (بیش از ۱۵ تا ۳۰ نفر): مجموعهٔ شب مافیا به‌علاوهٔ چند نقش.
• هزارچهره در شب نامیراست؛ توانایی کسی را که با رأی روز خارج شده یک روز به دست می‌آورد و کارهای انجام‌شده روی او به انجام‌دهنده برمی‌گردد.
• شیطان‌صفت از شب دوم دو نفر را به هم پیوند می‌دهد (تعداد با شمارهٔ شب زیاد می‌شود)؛ اگر هم‌ساید باشند هر دو خارج می‌شوند؛ نجات دکتر جلویش را نمی‌گیرد؛ با خروج هزارچهره خارج می‌شود.
• نائب شهردار را می‌شناسد و تا وقتی دکتر و جراح زنده‌اند در شب نمی‌میرد.
• نقش‌های مقابل: سپیدار (شهروند؛ تا زنده است لشکر سیاه نمی‌برد و توانایی‌هایش رویش اثر ندارد) و زرافشان (هر شب می‌فهمد یک نفر عضو لشکر سیاه است یا نه).""",
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
• دزد (مستقل) هر شب نقش یک نفر را می‌دزدد؛ سایه در شب‌های سوم و پنجم سه اسم می‌دهد (دو روستایی و یک کافر ← هر سه کشته).
• ۱۹ نفره: + قمارباز، نانوا و سایه (۶ کافر، ۱۱ روستایی، ۲ مستقل). ۲۲ نفره: + مزدور، مست و هتلدار (۷ کافر، ۱۳ روستایی، ۲ مستقل).
• قاتل: یک شات قطعی به جای شات معمول. هفت‌خط: دو بار در بازی حدس نقش؛ درست ← حذف. قمارباز: هر شب سنگ‌کاغذقیچی با یک نفر؛ دو برد پیاپی ← هدف کشته، باخت ← خروج خود قمارباز.
• ساقی تنها بیدار می‌شود و یک نفر را مست می‌کند. خائن کافر را می‌شناسد و ریش‌سفید خائن را. مزدور از شب چهارم یک روستایی را جذب می‌کند (جز ریش‌سفید، محافظ، مستقل، کلانتر، کابوی، حرفه‌ای).
• کلانتر شب یک نفر را بازداشت می‌کند و او فردا ساکت است. کابوی یک نفر را با خود می‌برد (هیچ‌کدام اعتراف نمی‌دهند). مارشال هر شب یا بررسی دزد بودن یا شلیک (دو شلیک در بازی، برای مستقل‌ها).
• کشیش با سه اعتراف روستایی از بازی خارج می‌شود. محافظ می‌تواند خودش را اعلام کند تا رأی یا دوئل نخورد. هتلدار: آوردن کافر، ساقی یا خائن به هتل ← کشته شدن ساکنان هتل. نانوا سه نان دارد (یک نان قوی، دو نان مسموم).
• دزد هر شب نقشی می‌دزدد، استعلامش روستایی است و فقط با مارشال یا قاتل می‌میرد؛ برد دزد: برابر شدن تعداد سه گروه.""",
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
• ترتیب شب: مافیا ← پزشک ← کلانتر ← تفنگ‌ساز ← گورکن ← کارآگاه.
• گورکن دو بار و نه در دو شب پشت سر هم. پس از قطعی شدن خروج با رأی، اول فدایی و اگر استفاده نکرد قاضی تصمیم می‌گیرد. در مقایسهٔ کارآگاه، رئیس و جاسوس شهروند حساب می‌شوند.""",
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
• اگر بادیگارد خودش را نجات دهد هیچ‌کس خارج نمی‌شود.
• ترکیب مرجع ۱۲ نفره: مافیا (۴): گادفادر، کانسورت، نینجا، تروریست (یار خاموش). شهر (۸): دیتکتیو، پزشک، بادیگارد، اسنایپر، ۴ شهروند ساده.
• کانسورت هر شب یک نفر را بلاک می‌کند. تروریست پیش از رأی دور دوم یک نفر را با خود می‌برد (شهروند ساده زنده می‌ماند). اسنایپر حداکثر دو شلیک. اگر شلیک‌کننده نینجا باشد، بادیگارد و او خارج نمی‌شوند.
• روز: استعلام وضعیت ندارد؛ هر دور یک چالش؛ رأی به خود مجاز؛ افراد دفاعیه حق رأی دارند؛ نصف آرا برای دفاع و خروج.""",
            listOf(GODFATHER, NINJA, CONSORT, TERROR_C, MAFIA, DETECTIVE, DOCTOR, BODYGUARD_C, SNIPER, CITIZEN),
            presets = mapOf(12 to m(GODFATHER to 1, CONSORT to 1, NINJA to 1, TERROR_C to 1, DETECTIVE to 1, DOCTOR to 1, BODYGUARD_C to 1, SNIPER to 1, CITIZEN to 4)),
            names = mapOf(GODFATHER to "گادفادر"), setup = ScenarioSetup(queryCount = 0, edge = "down")),
        MafiaScenario("scam", "اسکام", "ادعای آزاد، بدون چالش، رأی هم‌زمان",
            """• ادعای نقش کاملاً آزاد؛ بلافاصله بعد از تقسیم نقش، ۱ تا ۲ دقیقه سناریوچینی.
• شروع‌کنندهٔ صحبت از میان داوطلبان قرعه‌کشی می‌شود؛ چالش ندارد؛ دو دور کامل صحبت.
• رأی مخفی: هر بازیکن مخفیانه یک نفر را انتخاب می‌کند و همه هم‌زمان اعلام می‌شوند. ۲ رأی یا بیشتر ← دفاع؛ بیش از ۵۰٪ کل آرا ← خروج مستقیم.
• رأی دوم بین افراد دفاعیه اجباری است و افراد دفاعیه هم حق رأی دارند.
• صحبت خارج از نوبت یا هر علامت در زمان دفاع دیگران ← اخراج انضباطی (راوی با ابزار «خروج انضباطی» اعمال می‌کند).
• هر خروج روز بلافاصله شب ایجاد می‌کند و نقش خارج‌شده اعلام می‌شود.
• ترکیب ۱۰ نفره (۳ مافیا، ۷ شهروند): دان، هوکر، فلیمر؛ کارآگاه، دکتر، اسنایپر و شهروند ساده. ۱۲/۱۳ نفر: + تروریست و رویین‌تن و اسنایپر ۲ شات. ۱۵ نفر: + خرابکار و تفنگدار.
• فلیمر استعلام شهروندان را برای کارآگاه مثبت می‌کند. هوکر با تیم مافیا بیدار می‌شود و یک نقش را مسدود می‌کند.
• دکتر (۱۰ و ۱۳ نفر): هر شب یک نجات و نجات خود نامحدود. تفنگدار (۱۵ نفر): دو تفنگ جنگی؛ صبح دارندگان تفنگ اعلام می‌شوند.
• هوکر یک شب در میان کار می‌کند و فقط کارآگاه از بلاک شدن باخبر می‌شود. گادفادر استعلام منفی دارد و تیر اسنایپر به او نمی‌رسد؛ نجات دکتر و تیر اسنایپر به گادفادر هر دو با «تیر به سنگ خورد» اعلام می‌شوند.
• بیش از ۵۰٪ آرا ← خروج مستقیم («هدشات») با وصیت. نقش خارج‌شده در شب اعلام می‌شود.""",
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
            """• طراح: سپهر ازقندی. سه ساید: شهر (۳ شهروند + پزشک)، یک مافیا، یک جوکر مستقل.
• هر روز ۲ دور صحبت «بدون چالش».
• حد نصاب دفاعیه: ۳ رأی؛ در ۵ نفر ۲ رأی. هر کس در دفاع باشد حق رأی دارد. کسی که در رأی‌گیری دربارهٔ کسی صحبت می‌کند اجازهٔ رأی دادن ندارد.
• نقش هر کس که از بازی خارج شود اعلام می‌شود.
• برد شهر: خروج تمام نقش‌های منفی بازی. برد مافیا: دست گرفتن در ۳ نفر آخر. برد جوکر: خروج با رأی در فردای شبی که خندیده است.
• نقش‌ها:
• جوکر: مستقل. در طول بازی دو شب می‌تواند انتخاب کند که بخندد؛ اگر فردای آن شب با رأی از بازی خارج شود، بازی را می‌برد. اگر خندید و فردا از بازی خارج نشد، تا ۲۴ ساعت نمی‌تواند دوباره از قابلیتش استفاده کند. اگر از قابلیتش استفاده نکند، رتبهٔ سوم را می‌گیرد. جوکر در شب نامیراست و اگر به ۳ نفر آخر برسد به نقش شهروندی تبدیل می‌شود. ادعای جوکر آزاد است.
• مافیای ساده: هر شب یک تیر دارد و راه بردش دست گرفتن در ۳ نفر آخر است.
• پزشک: هر شب یک نفر را برای نجات انتخاب می‌کند و در طول بازی یک بار می‌تواند خودش را نجات دهد.""",
            listOf(MAFIA, DOCTOR, JOKER, CITIZEN),
            presets = mapOf(6 to m(MAFIA to 1, DOCTOR to 1, JOKER to 1, CITIZEN to 3)),
            names = mapOf(DOCTOR to "پزشک"),
            setup = ScenarioSetup(challenge = false, thresholds = mapOf(6 to 3, 5 to 2)), auto = false),
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
• ترکیب مرجع ۱۳ نفره: مافیا (۴): گادفادر، سلاخ، گنگستر، دزد. شهر (۹): نگهبان، پزشک، سیرکوله، تفنگدار، بازرس، دکستر، کارآگاه خصوصی، ۲ شهروند ساده.
• با خروج گادفادر، سلاخ و گنگستر توانایی ویژه را از دست می‌دهند و مافیای ساده می‌شوند.
• سلاخ: یک بار جنازهٔ خروج رأیی را سلاخی می‌کند و یک بار به جای شلیک ناتویی (حدس نقش) دارد. دکستر نقش مقابل سلاخ است (یک بار).
• گنگستر فقط یک شب تفنگ می‌دهد (یک جنگی و یک مشقی؛ جنگی به یار خودش نه). دزد یارانش را نمی‌شناسد، هر شب کنش دو نفر را جابه‌جا می‌کند (نه کنش نگهبان)، با شات مافیا خارج نمی‌شود و اگر به نگهبان بزند خارج می‌شود.
• نگهبان در شب از ۱ یا ۲ نفر در برابر دزد نگهبانی می‌دهد و در روز زره دارد. سیرکوله هر شب به خانهٔ یک نفر می‌رود؛ اگر قربانی شب را درست حدس بزند قاتل را می‌بیند و بیرون از خانه از شات مافیا در امان است. کارآگاه خصوصی فقط دزد را مثبت می‌بیند. پزشک خودش را دو بار.
• حد نصاب: ۱۱ تا ۱۳ نفر ← ۵ رأی.
• دکستر یک بار در بازی شبی را که سلاخ جنازه را سلاخی می‌کند حدس می‌زند.""",
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
            """• این بازی برای ۱۰ تا ۱۳ نفر طراحی شده است (ده، دوازده و سیزده نفره).
• در این سناریو مافیاها با واژهٔ «شو» و شهروندان با واژهٔ «تپان» شناخته می‌شوند.
• ادعای نقش در این سناریو کاملاً آزاد است.
• شروع بازی: نقش‌ها پخش می‌شود؛ معارفه دارد و هر نفر ۱۵ ثانیه صحبت می‌کند.
• شب (معارفه): شوها به جز شو مستر یکدیگر را می‌شناسند (شو مستر یارانش را نمی‌شناسد ولی یارانش او را می‌شناسند).
• هر روز یک چالش آزاد وجود دارد؛ سپس صحبت‌ها تمام می‌شود و رأی‌گیری.
• برای ورود به دفاعیه نصف آرا نیاز است و تمامی افراد حق رأی دارند، حتی افراد داخل دفاعیه.
• سابقه در بازی ملاک نیست؛ اگر کسی وارد دفاعیه شود و رأی‌ها مساوی شود، کسی خارج نخواهد شد حتی اگر سابقه داشته باشد.
• شو مستر هر شب، حتی تکراری، می‌تواند یک نفر را گروگان بگیرد؛ اگر شو فادر را گروگان بگیرد آن شب شات ندارند.
• شرط برد: حذف همهٔ شوها ← برد تپان‌ها؛ برابری تعداد شوها با تپان‌ها ← برد شوها.
• نقش‌ها:
• تپان: تپان عضو ساده گروه شهروندی است.
• تپانگر: تپانگر هر شب می‌تواند استعلام یک شخص را بگیرد.
• تپانخواه: تپانخواه هر شب می‌تواند یک نفر را نجات دهد؛ خودش را یک بار و مابقی را بی‌نهایت. دو شب متوالی یک نفر را نمی‌تواند نجات دهد.
• شو ملق: شو ملق شوی ساده است که مابقی شوها را می‌شناسد.
• شو فادر: رهبر گروه شو است و تیر شب به دست اوست؛ همچنین استعلام او برای تپانگر منفی است.
• شو مستر: هر شب می‌تواند یک تپان را، حتی تکراری، گروگان بگیرد و آن شخص نمی‌تواند از قابلیتش استفاده کند. شو مستر یاران خودش را نمی‌شناسد ولی یارانش او را می‌شناسند. اگر شو فادر را گروگان بگیرد (گروه شو) حق شات ندارد.
• تپانچه: هر شب یک تفنگ دارد که می‌تواند به یک نفر بدهد و نوع تفنگ را هم مشخص می‌کند: خودی یا جنگی. اگر تفنگ خودی باشد و فرد شلیک کند، خودش از بازی بیرون می‌رود. اگر بخواهد می‌تواند تفنگ ندهد.""",
            listOf(SHOW_FATHER, SHOW_MASTER, MAFIA, TAPANCHE, DETECTIVE, DOCTOR, CITIZEN),
            presets = mapOf(10 to m(SHOW_FATHER to 1, SHOW_MASTER to 1, MAFIA to 1, TAPANCHE to 1, DETECTIVE to 1, DOCTOR to 1, CITIZEN to 4)),
            names = mapOf(CITIZEN to "تپان", DETECTIVE to "تپانگر", DOCTOR to "تپانخواه", MAFIA to "شو ملق"),
            wake = mapOf(SHOW_FATHER to 26, SHOW_MASTER to 25, MAFIA to 26, TAPANCHE to 30, DETECTIVE to 31, DOCTOR to 32),
            setup = ScenarioSetup(edge = "down"), auto = false),
        MafiaScenario("specialist", "متخصص", "۱۰ نفره با لابی‌من و سایه",
            """• نسخهٔ پیشرفتهٔ سناریوی «رویال». ۱۰ نفره؛ مافیا: گادفادر، لابی‌من، سایه (خاموش)؛ شهر: متخصص، مارشال، رنجر، آدم‌برفی، آفیسر، شوالیه، رویالیست.
• ترتیب شب: شوالیه ← تیم مافیا (به جز سایه) ← سایه ← متخصص ← مارشال ← رنجر ← آفیسر ← آدم‌برفی/دارندهٔ گوله برف.
• حد نصاب ورود به دفاعیه = حد نصاب خروج: ۸–۱۰ نفر ۴ رأی، ۶–۷ نفر ۳ رأی، ۴–۵ نفر ۲ رأی.
• برد: خروج همهٔ مافیا ← شهر؛ برابری مافیا با شهر ← مافیا؛ تنها یک تیم برنده می‌شود و بازی تساوی ندارد.
• نقش‌ها:
• گادفادر: رئیس تیم مافیا؛ در شب نامیراست و هیچ‌گاه در شب از بازی حذف نمی‌شود. تصمیم نهایی شات شب و تأیید لابی با اوست. می‌تواند به‌جای شلیک از «طلسم» استفاده کند: نقش یک شهروند را حدس می‌زند و در صورت درستی آن شهروند شبانه کشته می‌شود؛ درمان متخصص روی طلسم اثری ندارد؛ اولین طلسم اشتباه توانایی طلسم را برای همیشه می‌سوزاند. طلسم در شب به همه اعلام می‌شود اما نتیجه‌اش در روز اعلام نمی‌شود. تا زنده است همهٔ استعلام‌هایش شهروندی است؛ پس از خروج در اعلام وضعیت مافیا خوانده می‌شود (مگر لابی شده باشد). گوله‌برف به او می‌رسد (آب نمی‌شود).
• لابی من: عضو رنک دوم و بیدار مافیا؛ در نبود گادفادر شات شب با اوست. هر شب با راوی روی ساید یکی از بازیکنان «لابی» می‌کند: اگر آن بازیکن فقط در روز پیش‌رو و صرفاً در فاز رأی‌گیری انتهای روز خارج شود، راوی ساید او را در اعلام وضعیت‌های بعدی معکوس اعلام می‌کند. لابی یک‌بار مصرف است (پس از یک‌بار موفقیت از بین می‌رود). اگر آفیسر لابی را کشف کند قابلیت لابی برای کل بازی از بین می‌رود ولی لابی‌من و آفیسر مطلع نمی‌شوند.
• سایه: عضو رنک سوم و خاموش مافیا؛ تنها بیدار می‌شود و گادفادر و لابی‌من او را نمی‌شناسند (شات تیم مافیا روی سایه باعث مرگ او می‌شود). «پوش»: هر شب نام یک نفر را به راوی می‌دهد؛ اگر آن فرد روز بعد وارد دفاعیه (یا دوئل مارشال) شود و رأی خروج نیاورد، با اعلام راوی «پوش شده» و خارج می‌شود؛ اگر با رأی بازیکنان خارج شود پوش اجرا نمی‌شود و قابلیت برای سایه باقی می‌ماند؛ اگر یکی از دو عضو بیدار مافیا را انتخاب کند آن مافیا پوش نمی‌شود؛ در سه نفر آخر کار نمی‌کند.
• متخصص: دکتر تیم شهروندی؛ در شب داروی شفابخش را به شهروندی که در خطر می‌بیند می‌دهد. در کل بازی ۵ داروی شفابخش دارد و خودش تقسیمشان می‌کند. روی طلسم گادفادر اثر ندارد.
• مارشال: یک «کارت مأموریت دوئل» دارد که در فاز شب باید به یک بازیکن بدهد. گیرنده فردا (تا پیش از رأی‌گیری) می‌تواند دوئل اعلام کند و حریف انتخاب کند؛ دو نفر در دفاعیهٔ نیمروزی با هم مناظره می‌کنند (هرکدام ۳۰ ثانیه)؛ بیشترین رأی خارج می‌شود و سایدش خوانده می‌شود؛ در برابری هر دو می‌مانند؛ دوئل حد نصاب ندارد.
• رنجر: در شب تلاش می‌کند شات تیم مافیا را ردیابی کند؛ اگر ردیابی درست باشد فرصت انتقام پیدا می‌کند و باید شلیک کند. این توانایی یک‌بار مصرف است و پس از آن رنجر شهروند ساده می‌شود. شلیک رنجر به متخصص، شوالیهٔ زره‌دار یا گادفادر به هدف نمی‌رسد.
• آدم برفی: یک «گوله برف آگاهی» دارد. در ۱ شب از بازی گوله را به سمت بازیکن الف پرتاب می‌کند: اگر الف مافیا باشد (به جز گادفادر) گوله آب می‌شود (لایک راوی = استعلام مافیایی)، وگرنه گوله به الف می‌رسد (دیسلایک = شهروندی). آدم‌برفی نتیجه را از راوی می‌فهمد و الف در هر حال با لمس دست از پرتاب آگاه می‌شود. گوله برف در کل بازی فقط ۲ شب پرتاب می‌شود (دارندهٔ گوله — حتی گادفادر — می‌تواند شب بعد پرتاب کند اگر فقط یک پرتاب انجام شده باشد). هرگونه درخواست، اعلام دیتا یا صحبت دربارهٔ گوله برف ← کیک.
• آفیسر: دو قابلیت دارد ولی هر شب فقط یکی: «کشف» (ردیابی درست محل لابی در همان شب؛ اگر درست باشد لابی تا آخر بازی لغو می‌شود و به هیچ طرف اعلام نمی‌شود) یا «بازجویی» (پرسیدن اینکه آیا اعلام وضعیت دستکاری شده است؛ پس از اولین اعلام وضعیت باز می‌شود). وارد کردن دیتای مستقیم (بدون فکت) توسط آفیسر ← کیک مستقیم.
• شوالیه: یک شیلد دفاعی دارد؛ یک‌بار در بازی می‌تواند یک شب آن را بپوشد و آن شب از مرگ در امان باشد.
• رویالیست: شهروند ساده.""",
            listOf(GODFATHER, LOBBYIST, SHADOW_M, EXPERT, MARSHAL, RANGER, SNOWMAN, OFFICER, KNIGHT, ROYALIST),
            presets = mapOf(10 to m(GODFATHER to 1, LOBBYIST to 1, SHADOW_M to 1, EXPERT to 1, MARSHAL to 1, RANGER to 1, SNOWMAN to 1, OFFICER to 1, KNIGHT to 1, ROYALIST to 1)),
            names = mapOf(GODFATHER to "گادفادر", LOBBYIST to "لابی من", SHADOW_M to "سایه"),
            wake = mapOf(KNIGHT to 1, GODFATHER to 26, LOBBYIST to 26, SHADOW_M to 27, EXPERT to 30, MARSHAL to 31, RANGER to 32, OFFICER to 33, SNOWMAN to 34),
            setup = ScenarioSetup(thresholds = mapOf(10 to 4, 9 to 4, 8 to 4, 7 to 3, 6 to 3, 5 to 2, 4 to 2)), auto = false),
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
• راهنما اگر یک مافیا را انتخاب کند، هویتش برای مافیا افشا می‌شود. دفاعیه: هر نماینده یک اتهام، ۲۰ ثانیه؛ متهم می‌تواند یک نفر را برای دفاع از خود انتخاب کند.
• رأی خیانت در خواب نیمروز انتخاب می‌شود. راهنما دو شب پیاپی یک نفر را انتخاب نمی‌کند. استعلام دن منفی. در دفاعیه اول پوشش‌ها و بعد تارگت‌ها صحبت می‌کنند.""",
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
• کارت‌های حرکت آخر: هوش و ذکاوت (حدس نقش؛ در بازی می‌مانید)، سرگیجه (۲۴ ساعت بی‌اثر)، دال (۲۴ ساعت نه شلیک نه رأی)، مانی‌سا (توانایی شب دو برابر)، آینه (جابه‌جایی هویت).
• بعد از خروج بلانکو، اسکوبار می‌تواند در یک شب هم شات و هم سلاخی کند. مورنو فقط تفنگ مشقی را می‌تواند به خودش بدهد. ترکیب استاندارد: ۱۱ نفر.""",
            listOf(PABLO, JUAN, BLANCO, MAFIA, CHURCHILL, BRIDGET, MORENO, BONAPARTE, MARTINEZ, CHAPLIN, CITIZEN), cards = listOf(MafiaLastCard.INTELLIGENCE, MafiaLastCard.VERTIGO, MafiaLastCard.DAL, MafiaLastCard.MANISA, MafiaLastCard.MIRROR)),
        MafiaScenario("classic10", "کلاسیک ۱۰ نفره", "ترکیب دقیق ۱۰ نفره",
            """• مافیا ×۳: رئیس مافیا، مافیای ساده ×۲. شهر ×۷: کارآگاه، دکتر، شهروند ساده ×۵.
• دقیقاً ۱۰ بازیکن؛ مافیا در شب معارفه همدیگر را می‌شناسند؛ هر بازیکن روزانه یک چالش.
• رأی روز اول اختیاری و از روز دوم اجباری؛ کشتهٔ شب حق وصیت دارد.""",
            listOf(GODFATHER, MAFIA, DETECTIVE, DOCTOR, CITIZEN), presets = mapOf(10 to m(GODFATHER to 1, MAFIA to 2, DETECTIVE to 1, DOCTOR to 1, CITIZEN to 5)),
            names = mapOf(GODFATHER to "رئیس مافیا"), players = 10..10, auto = false),
        MafiaScenario("cover", "کاور", "رئیس مافیا بعد از معارفه انتخاب می‌شود (لایه)",
            """• در شروع رئیس مافیای از پیش تعیین‌شده وجود ندارد. بعد از شب معارفه، اعضای مافیا یک بازیکن را به عنوان رئیس پیشنهادی انتخاب می‌کنند.
• هدف می‌تواند پیشنهاد را قبول یا رد کند؛ اگر قبول کند نقش قبلی خود را از دست می‌دهد و رئیس می‌شود (راوی این کار را با ابزار «تغییر نقش» انجام می‌دهد).
• نقش «مجهول» جایگزین این مبادله است.""",
            emptyList()),
        MafiaScenario("target", "تارگت", "رئیس یارانش را انتخاب می‌کند (لایه)",
            """• در قرعهٔ اولیه یک بازیکن با کارت «رئیس» مشخص می‌شود و در فاز آماده‌سازی بیدار می‌شود.
• رئیس سناریوی پایه را انتخاب و بر اساس تعداد جای مافیا، یاران خود را انتخاب می‌کند.
• سپس نقش‌های واقعی پخش می‌شوند و منتخب‌ها روی جای مافیا قرار می‌گیرند (راوی نقش‌ها را در صفحهٔ توزیع نقش به‌صورت دستی پخش می‌کند).""",
            emptyList()),
        MafiaScenario("hannibal", "هانیبال", "خرید در معارفه و اثر سایه",
            """• خانوادهٔ کلاسیک، ۱۰، ۱۲ یا ۱۳ نفره، با روز اول کور.
• رئیس مافیا پخش نمی‌شود: شب معارفه هانیبال به یک بازیکن پیشنهاد ریاست می‌دهد؛ پذیرش ← آن بازیکن رئیس مافیا و طرفدار صاحب نقش شهروندی او؛ رد ← طرفدار رئیس مافیا. در هر دو حالت بازی سه مافیا در برابر هفت شهروند ادامه می‌یابد و فقط هانیبال می‌داند چطور به اینجا رسیده.
• در ۱۲ و ۱۳ نفره ناتو به مافیا و اسنایپر الیت به شهر اضافه می‌شوند و رئیس دیگر ناتویی ندارد؛ در ۱۳ نفره یک شهروند ساده سوم اضافه می‌شود.
• ترتیب شب: ناتو ← رئیس مافیا ← پزشک ← کارآگاه ← اسنایپر الیت.
• دوئل کارت معمار: بازیکن علامت‌خورده حریف انتخاب می‌کند، هرکدام دو بار صحبت، رأی شهر بین دو نفر، بازنده خارج.
• شرط برد: شهر وقتی همهٔ مافیا حذف شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود.
• نقش‌ها:
• رئیس مافیا: رئیس مافیا پخش نمی‌شود و از پیشنهاد هانیبال در شب معارفه ساخته می‌شود. فرمان شلیک شب را می‌دهد و در استعلام کارآگاه منفی است. در ۱۰ نفره توانایی ناتویی (حدس نقش به جای شلیک) دارد؛ در ۱۲ و ۱۳ نفره ناتویی ندارد و آن شب را به ناتو می‌سپارد.
• سایه: سایه هر شب پشت سر یک نفر می‌ایستد و هر کاری آن بازیکن آن شب انجام دهد به خودش برمی‌گردد: سیو دکتر روی خود دکتر می‌افتد و تیر روی خود تیرانداز. مافیاست و هم‌تیمی‌هایش را می‌شناسد.
• قهرمان: قهرمان با یک تیر از بازی خارج نمی‌شود: بار اول که مافیا به او شلیک کند زنده می‌ماند و شهر چیزی نمی‌فهمد. شبی که دکتر او را سیو کند جانی از او کم نمی‌شود، چون تیر به او نخورده است.
• پزشک: هر شب یک بازیکن را نجات می‌دهد؛ دو بار در بازی می‌تواند خودش را سیو کند و هر شبی که سیو نکند، شب بعد دو سیو دارد.
• معمار: یک بار در کل بازی، شب، کارتی جلوی یک بازیکن می‌گذارد. روز بعد، پس از پایان صحبت آن بازیکن، کارت رو می‌شود و او باید یک حریف انتخاب کند؛ هر دو دو بار صحبت می‌کنند، شهر بین آن‌ها رأی می‌دهد و بازنده خارج می‌شود. معمار همان شب تعیین می‌کند رأی‌گیری از کدام صندلی و در چه جهتی شروع شود. نام معمار هیچ‌وقت اعلام نمی‌شود.
• انتقام‌جو: در شب‌های عادی کاری ندارد؛ فقط شبی بیدار می‌شود که مافیا به او شلیک کرده و دکتر نجاتش نداده باشد. در هر صورت خارج می‌شود و فقط تصمیم می‌گیرد کسی با او برود یا نه: سایه، هانیبال یا ناتو با او می‌روند؛ شهروند ساده یا رئیس مافیا نه.
• ناتو: (فقط ۱۲ و ۱۳ نفره) هر شب می‌تواند نقش یک شهروند را حدس بزند؛ اگر درست باشد، بازیکن تحت هر شرایطی — حتی با نجات — حذف می‌شود. جایگزین شلیک معمول مافیاست و فقط وقتی فعال است که رئیس فرمانش را بدهد.
• اسنایپر الیت: (فقط ۱۲ و ۱۳ نفره) در کل بازی یک تیر دارد. شلیک به مافیا ← هدف شب خارج می‌شود مگر دکتر نجاتش داده باشد؛ شلیک به رئیس مافیا کسی را خارج نمی‌کند؛ شلیک به شهروند ← خود اسنایپر خارج می‌شود. اگر شب اول شلیک کند، نتیجه ابتدای روز سوم اعلام می‌شود و در روز دوم نه او و نه هدفش خارج نمی‌شوند.
• هانیبال: هانیبال از تیم مافیاست ولی تیمش رئیس ندارد و او باید رئیس را بخرد. شب معارفه به یک بازیکن پیشنهاد ریاست می‌دهد؛ اگر بپذیرد، آن بازیکن رئیس مافیا می‌شود و طرفدار نقش قبلی او را می‌گیرد؛ اگر رد کند، طرفدار مجبور به پذیرفتن ریاست می‌شود. از شب دوم بی‌سروصدا یک بازیکن را به عنوان همراه علامت می‌زند: اگر هانیبال در روز از بازی خارج شود و علامتش روی یک شهروند ساده باشد، آن بازیکن هم با او می‌رود و وصیت نمی‌کند. اگر علامت روی شهروند نقش‌دار باشد یا هانیبال شب کشته شود، هیچ اتفاقی نمی‌افتد.
• طرفدار: بلاتکلیف؛ در ابتدا در هیچ تیمی نیست و این وضعیت فقط یک شب طول می‌کشد. اگر پیشنهاد ریاست هانیبال پذیرفته شود، طرفدار نقش شهروندی آن بازیکن را می‌گیرد؛ اگر رد شود، خودش رئیس مافیا می‌شود. از او چیزی پرسیده نمی‌شود و کاری انجام نمی‌دهد.""",
            listOf(HANNIBAL_R, FAN, GODFATHER, SHADOW_M, ARMORED, DOCTOR, ARCHITECT, AVENGER, NATO, ELITE_SNIPER),
            names = mapOf(GODFATHER to "رئیس مافیا", SHADOW_M to "سایه", ARMORED to "قهرمان", DOCTOR to "پزشک", AVENGER to "انتقام‌جو"),
            wake = mapOf(NATO to 1, GODFATHER to 2, DOCTOR to 3, DETECTIVE to 4, ELITE_SNIPER to 5)),
        MafiaScenario("pro_club", "باشگاه حرفه‌ای‌ها", "تهمت‌زن، خرابکار و تفنگدار",
            """• ادعای نقش آزاد؛ روز معارفه ندارد؛ شروع‌کننده تصادفی از داوطلبان؛ هر بازیکن دو نوبت ۴۰ ثانیه.
• چالش معمولی ندارد؛ در پایان یک داوطلب تصادفی چالش مشترک یک‌دقیقه‌ای می‌گیرد.
• رأی هم‌زمان: ۲ رأی یا بیشتر ← دفاع؛ بیش از ۵۰٪ ← خروج مستقیم. بعد از دفاع فقط افراد دفاعیه رأی می‌دهند؛ تساوی دوم ← بدون خروج. خروج ← وصیت و اعلام نقش.
• مافیا: رئیس (شلیک، استعلام منفی)، تهمت‌زن (پاپوش)، خرابکار. شهر: کارآگاه، پزشک (بدون نجات خود)، تفنگدار، شهروند ساده ×۴.""",
            listOf(GODFATHER, FRAMER, WRECKER, DETECTIVE, DOCTOR, GUNMAN, CITIZEN),
            presets = mapOf(10 to m(GODFATHER to 1, FRAMER to 1, WRECKER to 1, DETECTIVE to 1, DOCTOR to 1, GUNMAN to 1, CITIZEN to 4)),
            names = mapOf(GODFATHER to "رئیس", DOCTOR to "پزشک"), setup = ScenarioSetup(challenge = false), auto = false),
        MafiaScenario("shahnameh", "شاهنامه", "اهریمن در برابر روشنایی",
            """• سناریوی ثابت ۱۰ نفره با روز اول کور؛ سه مافیا (ضحاک، بوف، افراسیاب) در برابر هفت شهروند (رستم، کاوه، سیمرغ، جاماسب، آرش کمانگیر و دو شهروند ساده).
• پر: پر سیمرغ نجات می‌دهد و پر بوف می‌کشد؛ گیرنده این دو را از هم تشخیص نمی‌دهد (همان پرسش و همان واژه‌ها، بدون نام فرستنده). نگه داشتن ← نجات یا مرگ؛ رد کردن ← هیچ؛ سپردن به دیگری ← اثر به او می‌رسد.
• روز دوم: هر بازیکن به نوبت یک نفر دیگر را نام می‌برد؛ هر دو نفری که نام یکدیگر را ببرند تن‌به‌تن می‌شوند. انجمن ضحاک، رستم و کاوه به هر تن‌به‌تن جداگانه رأی می‌دهد (دو از سه) و هر تن‌به‌تن تأییدشده هر دو نفرش به دفاع می‌روند. راه دوم: هر که چهار رأی یا بیشتر بیاورد بی‌تن‌به‌تن و بی‌رأی انجمن به دفاع می‌رود.
• ترتیب شب: افراسیاب ← ضحاک ← بوف ← سیمرغ ← کاوه ← جاماسب ← رستم ← آرش کمانگیر.
• شرط برد: شهر وقتی همهٔ مافیا حذف شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود.
• نقش‌ها:
• ضحاک: شاه ماردوش و سردستهٔ مافیا. شلیک شبانه را او می‌زند؛ در استعلام معمولی جاماسب بی‌گناه خوانده می‌شود؛ سایهٔ رستم بر او کارگر نیست؛ در انجمنی می‌نشیند که تصمیم می‌گیرد کدام تن‌به‌تن‌های روز دوم به دفاع بروند.
• بوف: یک پر زهرآلود برای تمام بازی دارد. آن را به یک بازیکن می‌دهد و از او پرسیده می‌شود — بی‌آنکه بداند فرستنده کیست — که نگهش دارد، ردش کند یا به دیگری بسپارد. هر کس شب را با آن به پایان ببرد صبح را نمی‌بیند. شبی که بوف پر را می‌دهد، مافیا شلیکی ندارد.
• افراسیاب: دو توانایی جدا دارد: شب دوم به یکی از هم‌تیمی‌هایش در برابر تیر روزِ آرش سپر می‌دهد؛ و از همان شب، هر شب در خاموشی یک بازیکن را «رستم» حدس می‌زند. هرگز به او گفته نمی‌شود درست زده یا نه، اما اگر درست زده باشد، هر وقت افراسیاب از بازی بیرون برود رستم هم با او می‌رود.
• سیمرغ: پنج پر برای بخشیدن دارد؛ هر شب یک یا دو پر می‌دهد و فقط یک بار می‌تواند یکی را برای خودش نگه دارد. پر پیشنهاد است نه نجات: گیرنده آن را نگه می‌دارد، رد می‌کند یا به دیگری می‌سپارد، و فقط کسی که شب را با پر به پایان ببرد از شلیک مافیا در امان است.
• جاماسب: پیشگو. هر شب از یک بازیکن استعلام می‌گیرد که مافیا هست یا نه؛ ضحاک نزد او بی‌گناه و سیمرغ به دروغ مافیا خوانده می‌شود. یک بار در بازی به جای آن استعلام خونین می‌گیرد که فقط به یک پرسش پاسخ می‌دهد: این رستم است یا ضحاک؟
• رستم: پهلوان. یک بار در بازی سایه‌اش را روی یک بازیکن می‌اندازد و آن شب اتفاقی نمی‌افتد؛ شب بعد می‌تواند آن را تأیید کند و آن بازیکن از بازی می‌رود. ضحاک از دسترس او بیرون است و به رستم در هیچ حالتی چیزی گفته نمی‌شود. عضو انجمن سه‌نفرهٔ روز دوم است.
• کاوه: آهنگر. شب دوم به یک بازیکن در برابر تیر روزِ آرش (کمان) سپر می‌دهد. اگر ضحاک هنوز در بازی باشد و مافیا شب کاوه را بزند و او خارج شود، شب بعد مافیا شلیکی ندارد. عضو انجمن سه‌نفرهٔ روز دوم (ضحاک، رستم، کاوه) است. (سپر فقط در «شب دوم» داده می‌شود.)
• آرش کمانگیر: کمانگیر. یک بار در بازی، از شب دوم، تنها کمان واقعی‌اش را به یک بازیکن می‌دهد. روز بعد آن بازیکن می‌تواند به هر کسی شلیک کند: هدفی که سپر (از کاوه یا افراسیاب) نداشته باشد از بازی می‌رود و جناحش برای همه اعلام می‌شود.""",
            listOf(ZAHHAK, OWL, AFRASIAB, RUSTAM, KAVEH, SIMORGH, JAMASB, ARASH, CITIZEN),
            presets = mapOf(10 to m(ZAHHAK to 1, OWL to 1, AFRASIAB to 1, RUSTAM to 1, KAVEH to 1, SIMORGH to 1, JAMASB to 1, ARASH to 1, CITIZEN to 2)),
            wake = mapOf(AFRASIAB to 1, ZAHHAK to 2, OWL to 3, SIMORGH to 4, KAVEH to 5, JAMASB to 6, RUSTAM to 7, ARASH to 8), auto = false),
        MafiaScenario("secret_army", "ارتش سری", "۱۰ نفره: گروه نجات، گشتاپو و دو مشتری؛ اسلحه، گفتگوی خصوصی و شلیک روز",
            """• طراح: سعید ابوطالب؛ فضای جنگ جهانی دوم. ۱۰ بازیکن + گرداننده.
• گروه نجات (۵): کرتیس (فرمانده، مسلح)، مأمور نجات ×۲ (مسلح)، دکتر پاسکال (بدون اسلحه، هرگز مسلح نمی‌شود)، خلبان (بدون اسلحه).
• گشتاپو (۳): سرهنگ کسلر، راینهارت، گشتاپوی ساده (هر سه مسلح). مستقل (۲): مشتری ×۲ (بی‌طرف و بدون اسلحه).
• اسلحه: ۶ اسلحه نزد بازیکنان و ۱ اسلحه نزد گرداننده (کل ۷).
• معارفه: یک دور صحبت مقدماتی؛ وضعیت اضطراری معارفه: کسلر، خلبان و دکتر بدون باز کردن چشم علامت می‌دهند؛ کرتیس چشم باز می‌کند و سه نفر را می‌بیند ولی نمی‌داند کدام کیست؛ سپس راینهارت و گشتاپوی ساده چشم باز می‌کنند و کسلر علامت می‌دهد (کسلر آن‌ها را نمی‌بیند). دو مأمور نجات همدیگر را نمی‌شناسند.
• روز: هر روز حداکثر ۲ گفتگوی خصوصی و ۲ شلیک. هر بازیکن فقط یک بار در کل بازی حق شلیک و یک بار حق درخواست گفتگوی خصوصی دارد.
• اتاق خصوصی (VIP): درخواست‌کننده پلاک خود را می‌دهد (دعوت‌شده پلاکش مصرف نمی‌شود). درخواست‌کننده می‌تواند دروغ بگوید؛ دعوت‌شده باید ساید واقعی‌اش را بگوید، جز کسلر و خلبان. تا پایان همان روز افشای گفتگو ممنوع است؛ حتی بدون انتقال اسلحه فرصت مصرف می‌شود.
• مسلح کردن (با اسلحهٔ گرداننده، نه اسلحهٔ درخواست‌کننده): کرتیس و مأموران نجات می‌توانند مشتری یا خلبان را مسلح کنند؛ گشتاپوها فقط مشتری را. مشتری با گرفتن اسلحه به همان جناح می‌پیوندد. پاسخ گرداننده: «تأیید می‌شود، مبادله می‌شود» / «اسلحه‌ای در دست گرداننده نیست» / «امکان مبادله وجود ندارد». اگر نجات بخواهد کسلر را مسلح کند، پاسخ «تأیید» است ولی اسلحه‌ای جابه‌جا نمی‌شود.
• اسلحهٔ استفاده‌نشدهٔ بازیکنی که خارج می‌شود به گرداننده برمی‌گردد.
• شلیک: فرد تیرخورده صحبت پایانی دارد و دکتر را حدس می‌زند؛ حدس درست ← نجات اجباری (اگر نجات مانده باشد). دکتر پاسکال در کل ۲ نجات دارد؛ خلبان و کسلر قابل نجات نیستند؛ شلیک دوم به یک نفر قابل نجات نیست.
• جلیقه: کسلر و خلبان شلیک اول را می‌گیرند. خلبان اولین رأی خروج موفق را هم زنده می‌ماند؛ خروج خلبان با رأی دوم بازی را تمام نمی‌کند.
• وضعیت اضطراری: اگر یک دور روز بدون شلیک تمام شود؛ اول راینهارت نقش یک نفر را حدس می‌زند (خلبان نه؛ درست ← خروج هدف و پایان توانایی؛ اولین اشتباه یک فرصت دیگر) و بعد رأی‌گیری. حد نصاب در ۱۰ نفر ۵ رأی؛ تساوی ← کسی خارج نمی‌شود.
• استعلام: پس از خروج دو بازیکن: ساید خارج‌شده‌ها، تعداد شلیک‌ها، تعداد گفتگوهای خصوصی و اسلحه‌های آزاد گرداننده.
• برد: همهٔ گشتاپوها خارج ← نجات؛ تعداد گشتاپو برابر گروه نجات ← گشتاپو (مشتری بی‌طرف شمرده نمی‌شود).
• شلیک مرگبار دوم به خلبان: جناح شلیک‌کننده بازنده است؛ استثنا: اگر از گشتاپو فقط یک عضو مسلح مانده و خلبان را بزند ← برد گشتاپو.
• کیاس: اگر فقط خلبان، یک نجات و یک گشتاپو بمانند، گرداننده به خلبان اسلحه می‌دهد؛ شلیک به گشتاپو ← برد نجات، شلیک به نجات ← برد گشتاپو.
• کارت حرکت آخر ندارد. راوی همهٔ این موارد را در «میز ارتش سری» در مراحل روز اجرا می‌کند.""",
            listOf(CURTIS, PARTISAN, PASCAL, PILOT, KESSLER, REINHART, GESTAPO, CUSTOMER),
            presets = mapOf(10 to m(CURTIS to 1, PARTISAN to 2, PASCAL to 1, PILOT to 1, KESSLER to 1, REINHART to 1, GESTAPO to 1, CUSTOMER to 2)),
            names = mapOf(CURTIS to "کرتیس", PARTISAN to "مأمور نجات", KESSLER to "سرهنگ کسلر", GESTAPO to "گشتاپوی ساده"),
            players = 10..10, setup = ScenarioSetup(noNight = true, thresholds = mapOf(10 to 5), edge = "down", noCards = true), auto = false),
        MafiaScenario("boss_heads_award", "جایزهٔ سرِ رئیس", "۱۵ نفره",
            """• ۱۵ نفره و با شب آغاز می‌شود. مافیا: پدرخوانده، مافیا، تروریست، ناتاشا، وکیل مافیا. شهر: پزشک، کارآگاه، تک‌تیرانداز، قاضی، کشیش، محافظ و چهار شهروند ساده.
• شب اول: پدرخوانده می‌تواند نقشش را با یک هم‌تیمی عوض کند؛ قاضی پنهانی دو بازیکنی را که پدرخوانده و دکتر هستند می‌بیند، بدون دانستن اینکه کدام کدام است.
• اعدام در دفاع می‌تواند بازی را تمام کند: محکوم شدن پدرخوانده ← برد شهروندان؛ محکوم شدن دکتر ← برد مافیا.
• از روز سوم رأی‌گیری روز یک‌به‌یک انجام می‌شود.
• ترتیب شب: پدرخوانده ← وکیل مافیا ← ناتاشا ← پزشک ← کارآگاه ← تک‌تیرانداز ← کشیش.
• شرط برد: شهر وقتی همهٔ مافیا حذف شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود.
• نقش‌ها:
• پدرخوانده: رهبر مافیا و فرماندهٔ شلیک شب؛ در استعلام کارآگاه منفی. در این سناریو فقط یک جان دارد و یک شلیک او را حذف می‌کند. در شب اول می‌تواند نقشش را با یک هم‌تیمی عوض کند. اگر پدرخوانده در دفاع اعدام (با رأی خارج) شود، شهروندان برنده‌اند.
• مافیا ساده: عضو عادی مافیا قدرت شبانه ندارد؛ اگر پدرخوانده خارج شود، یک عضو عادی شلیک شبانه را انجام می‌دهد.
• تروریست: عضو مافیاست؛ هم‌تیمی‌هایش را می‌شناسد ولی آن‌ها او را نمی‌شناسند؛ توانایی شبانه ندارد. یک بار در بازی انفجارش را اعلام می‌کند و خودش را همراه یک بازیکن انتخابی از بازی خارج می‌کند؛ در این سناریو مافیای کامل است و انفجارش هر شهروندی را می‌کشد (مگر محافظ بایستد).
• ناتاشا: هر شب یک بازیکن را ساکت می‌کند: تمام روز بعد نه نوبت صحبت، نه چالش، نه دفاع و نه رأی در رأی‌گیری روز یا دفاع دارد. اثر یک روز است؛ دو شب پیاپی یک نفر را نمی‌تواند هدف بگیرد و شبی که بلاک شود بی‌اثر است.
• وکیل مافیا: وکیل مافیا هر شب یک بازیکن (هر کسی، حتی خودش) را انتخاب می‌کند؛ اگر کارآگاه همان شب همان بازیکن را استعلام کند، نتیجه وارونه می‌شود (حتی پدرخوانده قرمز دیده می‌شود). شبی که بلاک شود بی‌اثر است.
• تک‌تیرانداز: تک‌تیرانداز شب‌ها شلیک می‌کند. شلیک به مافیا او را حذف می‌کند؛ شلیک اشتباه به شهروند به قیمت جان خود تک‌تیرانداز تمام می‌شود؛ اگر توانایی‌اش وارونه شود شلیکش به خودش می‌خورد.
• قاضی: یک بار در بازی می‌تواند اعدام روز را لغو کند و بازیکن محکوم را به بازی برگرداند؛ حذف خودش را نمی‌تواند وتو کند. در شب اول پنهانی می‌فهمد کدام دو بازیکن پدرخوانده و دکتر هستند (بدون اینکه بداند کدام کدام است).
• کشیش: رقیب ناتاشا. هر شب دو بازیکن متفاوت را انتخاب می‌کند: اولی از سکوت درمی‌آید (اگر ناتاشا ساکتش کرده باشد)؛ دومی روز بعد نوبت ویژه می‌گیرد — اول صحبت می‌کند و در پایان روز یک نوبت اضافه دارد. اگر خود نفر دوم ساکت شده باشد، ناتاشا برنده است. قابل بلاک است و برای کارآگاه سبز دیده می‌شود.
• پزشک: هر شب یک بازیکن را از شلیک مافیا نجات می‌دهد. اگر پزشک در دفاع اعدام (با رأی خارج) شود، مافیا برنده است.
• محافظ: رقیب تروریست؛ اقدام شبانه ندارد. تا زمانی که تروریستی زنده باشد جاودانه است (هیچ چیز نمی‌تواند او را بکشد) و به‌محض خروج آخرین تروریست به شهروند عادی با یک جان تبدیل می‌شود. وقتی تروریست هدف انفجار را انتخاب می‌کند، یک فرصت ۱۰ ثانیه‌ای برای ایستادن محافظ باز می‌شود: اگر بایستد، همراه تروریست می‌میرد و هدف نجات می‌یابد؛ اگر نایستد، انفجار عادی اجرا می‌شود.""",
            listOf(GODFATHER, MAFIA, TERRORIST, NATASHA, CHARLATAN, DOCTOR, DETECTIVE, PROFESSIONAL, JUDGE, PRIEST, TERROR_GUARD, CITIZEN),
            presets = mapOf(15 to m(GODFATHER to 1, MAFIA to 1, TERRORIST to 1, NATASHA to 1, CHARLATAN to 1, DOCTOR to 1, DETECTIVE to 1, PROFESSIONAL to 1, JUDGE to 1, PRIEST to 1, TERROR_GUARD to 1, CITIZEN to 4)),
            names = mapOf(CHARLATAN to "وکیل مافیا", PROFESSIONAL to "تک‌تیرانداز", DOCTOR to "پزشک"),
            wake = mapOf(GODFATHER to 1, CHARLATAN to 2, NATASHA to 3, DOCTOR to 4, DETECTIVE to 5, PROFESSIONAL to 6, PRIEST to 7),
            setup = ScenarioSetup(noIntroDay = true), auto = false),
        MafiaScenario("dread_octopus", "اختاپوس مخوف", "۱۳ نفره",
            """• سناریوی ثابت ۱۳ نفره که با شب آغاز می‌شود: سه مافیا (پدرخوانده، محافظ مافیا، قاتل حرفه‌ای)، نه شهروند (پزشک، کارآگاه، شهردار، قاضی، گورکن، گردن‌کلفت، فرشتهٔ نجات، دست‌کج، قهرمان) و سندیکا (مستقل).
• شلیک ویژهٔ قاتل حرفه‌ای علاوه بر شلیک معمول تیم است و سه روز بعد پیش از رأی‌گیری اجرا می‌شود.
• پاسخ‌های شهر بیشتر روزانه‌اند: شهردار (بخشش کل دفاع)، قاضی (بیرون کشیدن از دفاع)، فرشتهٔ نجات (لغو یک حذف با رأی)؛ تنها محافظت شبانه از قهرمان است.
• ترتیب شب: دست‌کج ← پدرخوانده ← قاتل حرفه‌ای ← سندیکا ← پزشک ← قهرمان ← کارآگاه ← گورکن.
• شرط برد: شهر وقتی همهٔ مافیا حذف شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود؛ سندیکا با ماندن بین سه نفر آخر.
• نقش‌ها:
• پدرخوانده: رهبر مافیا و فرماندهٔ شلیک شب؛ در استعلام کارآگاه منفی. تا وقتی محافظ مافیا زنده است شلیک شبانهٔ شهروندی او را نمی‌کشد.
• قاتل حرفه‌ای: یک بار در بازی، در نوبت شبانهٔ مافیا یک شلیک ویژه می‌زند و تیم همان شب شلیک معمولش را هم دارد. آن شب برای هدف اتفاقی نمی‌افتد؛ در عوض برای مرگ بی‌قیدوشرط نشانه‌گذاری می‌شود که سه روز بعد، درست پیش از رأی‌گیری آن روز و در برابر کل جمع اعلام می‌شود: هیچ دکتر، محافظ یا سپر قهرمانی جلودارش نیست. اگر هدف تا آن زمان خارج شده باشد، حکم منتفی است.
• شهردار: یک بار در بازی می‌تواند کل دفاع را ببخشد: همهٔ متهمان آزاد می‌شوند و رأی‌گیری انجام نمی‌شود؛ تصمیمش بر تصمیم قاضی ارجح است. هر شب هویت پزشک را پنهانی می‌فهمد.
• قاضی: یک بار در بازی می‌تواند یک نفر را از جایگاه دفاع بیرون بکشد (اعدام روز را وتو کند). حذف خودش را نمی‌تواند وتو کند. اگر هم‌زمان با شهردار عمل کند، تصمیم شهردار ارجح است.
• گورکن: دو بار در بازی گورها را می‌شکافد؛ صبح روز بعد گرداننده نقش همهٔ بازیکنان حذف‌شده را علنی (بدون نام) اعلام می‌کند.
• گردن‌کلفت: وقتی مافیا شبانه او را هدف بگیرد بی‌درنگ نمی‌میرد: تمام روز بعد زنده می‌ماند و در پایان آن روز خارج می‌شود. نجات دکتر در همان شب زخم را کاملاً خنثی می‌کند؛ دو مهاجم در یک شب او را بی‌درنگ می‌کشند. در استعلام کارآگاه بی‌گناه است.
• دست‌کج: یک بار در بازی هر چیزی را که بازیکنی را خاص می‌کند از او می‌رباید: هدف تیم و جان‌هایش را نگه می‌دارد اما نقش ساده می‌شود و از شب بعد همهٔ توانایی‌هایش را از دست می‌دهد. در پایان شب اعمال می‌شود (اقدام خود هدف در همان شب کار می‌کند)؛ روز بعد شهر می‌فهمد کدام صندلی خالی شده ولی نمی‌فهمد کار چه کسی بوده. مستقل‌ها را نمی‌توان دزدید.
• قهرمان: از شب سوم به بعد و یک‌شب‌درمیان (شب‌های ۳، ۵، ۷ …) از یک بازیکن — هرگز خودش — به مدت کامل ۲۴ ساعت محافظت می‌کند: در شب در برابر شلیک مافیا، انفجارها و دزدی دست‌کج، و در روز بعد در برابر لیست سندیکا و هر حذف دیگری که رأی جمع نباشد. رأی‌گیری و حکم قاتل حرفه‌ای هرگز پوشش داده نمی‌شوند. هیچ‌چیز اعلام نمی‌شود. در استعلام کارآگاه بی‌گناه است.
• محافظ مافیا: نگهبان منفعل پدرخوانده؛ اقدام شبانه ندارد. تا وقتی زنده است، هیچ شلیک شبانهٔ شهروندی پدرخوانده را نمی‌کشد و شلیک بی‌اثر می‌ماند. برای رسیدن به پدرخوانده، شهر باید نخست محافظ مافیا را خارج کند.
• فرشتهٔ نجات: شهروندی با یک نجات. وقتی رأی‌گیری دفاع بازیکنی را محکوم می‌کند، به فرشته پیشنهاد می‌شود که آن را لغو کند و او را در بازی نگه دارد. اگر نپذیرد، بازیکن محکوم یک فرصت دارد تا حدس بزند فرشته کیست؛ حدس درست ← نجات به‌هرحال انجام می‌شود؛ حدس اشتباه ← طبق روال خارج می‌شود. در هر صورت توانایی مصرف می‌شود و فرشته شهروند ساده می‌شود. هرگز نمی‌تواند خودش را نجات دهد و با حدس خودش نامیده نمی‌شود.
• سندیکا: اختاپوس؛ مستقل و با ماندن بین سه نفر آخر برنده می‌شود. تقریباً فناناپذیر است (۱۰۰ جان): هیچ شلیکی به او نمی‌رسد، فقط رأی شهر. در شب معارفه اعضای مافیا، دکتر و کارآگاه را می‌شناسد و مخفیانه یک لیست ثابت حذف می‌سازد: یک صندلی شروع و یک جهت (ساعت‌گرد یا پادساعت‌گرد) و از آنجا هر سه نفر یک نفر، تا آخر بازی ثابت؛ خودش هرگز در لیست نیست. در اختاپوس مخوف «لیست نقره‌ای» استفاده می‌شود: نقش‌های مهم میز به او نشان داده می‌شوند و او سه نفرشان را برای شناختن انتخاب می‌کند و آنچه فهمیده برای همه اعلام می‌شود. از آن پس هر روزی که خودش در دفاع نباشد می‌تواند لیست را فعال کند و نفر زندهٔ بعدی لیست را ببرد. دست‌کج نمی‌تواند قابلیتش را بگیرد؛ سپر قهرمان یک نام را یک روز از دستش دور نگه می‌دارد.""",
            listOf(GODFATHER, GF_GUARD, HITMAN, DOCTOR, DETECTIVE, MAYOR, JUDGE, GRAVEDIGGER, TOUGH, VOTE_ANGEL, PICKPOCKET, HERO_N, SYNDICATE),
            presets = mapOf(13 to m(GODFATHER to 1, GF_GUARD to 1, HITMAN to 1, DOCTOR to 1, DETECTIVE to 1, MAYOR to 1, JUDGE to 1, GRAVEDIGGER to 1, TOUGH to 1, VOTE_ANGEL to 1, PICKPOCKET to 1, HERO_N to 1, SYNDICATE to 1)),
            names = mapOf(DOCTOR to "پزشک"),
            wake = mapOf(PICKPOCKET to 1, GODFATHER to 2, HITMAN to 3, SYNDICATE to 4, DOCTOR to 5, HERO_N to 6, DETECTIVE to 7, GRAVEDIGGER to 8),
            setup = ScenarioSetup(noIntroDay = true), auto = false),
        MafiaScenario("misled", "گمراه", "۸ نفره",
            """• هشت بازیکن ثابت؛ بازی از شب آغاز می‌شود و روز اول بی‌خبر ندارد. شب نخست فقط معارفه است: مافیا همدیگر را می‌شناسند، کسی کاری نمی‌کند و کسی بیرون نمی‌رود.
• دو مافیا (پدرخوانده، شارلاتان) در برابر شش شهروند (پزشک، کارآگاه، دندانپزشک، کلانتر، جان سخت، شهروند خاص).
• اگر دو نفر یا بیشتر به دفاعیه بروند، رأی‌گیری دفاع پنهان است: اتاق تاریک می‌شود و فقط شمارش نهایی خوانده می‌شود.
• ترتیب شب: پدرخوانده ← شارلاتان ← دندانپزشک ← پزشک ← کارآگاه ← کلانتر ← شهروند خاص ← جان سخت.
• شرط برد: شهر وقتی همهٔ مافیا حذف شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود.
• نقش‌ها:
• پدرخوانده: رهبر مافیا و فرماندهٔ شلیک شب؛ در استعلام کارآگاه منفی. در این سناریو فقط یک جان دارد و یک شلیک درست کلانتر کارش را تمام می‌کند.
• شارلاتان: دو بار در بازی، از شب دوم، یک بازیکن را برای آن شب نشانه می‌گیرد؛ اگر کارآگاه همان بازیکن را استعلام کند نتیجه وارونه برمی‌گردد (مافیا بی‌گناه و شهروند مجرم). می‌تواند خودش را هم نشانه بگیرد. شلیک مخصوص به خود ندارد.
• پزشک: هر شب یک بازیکن را نجات می‌دهد؛ یک بار در بازی می‌تواند خودش را نجات دهد.
• دندانپزشک: دندانپزشک یک بار در بازی و تنها از شب دوم به بعد یک بازیکن را ۲۴ ساعت ساکت می‌کند: روز بعد نه نوبت صحبت، نه چالش، نه دفاع و نه رأی در هیچ رأی‌گیری دارد. این سکوت را سخنران (کشیش) نمی‌تواند بردارد.
• کلانتر: هر شب یک نفر را انتخاب می‌کند (شلیک اجباری نیست): مافیا ← حذف؛ شهروند ← خود کلانتر حذف می‌شود؛ مستقل ← بی‌اثر و تیر هدر می‌رود. روی بازیکنی که دکتر نجاتش داده اثری ندارد. گرداننده نتیجهٔ هر شلیک را اعلام می‌کند.
• جان سخت: یک بار در بازی شب‌هنگام قبرها را می‌شکافد و صبح روز بعد گرداننده نقش همهٔ بازیکنان حذف‌شده را علنی و بدون نام اعلام می‌کند. نخستین شلیک مافیا فقط زرهش را می‌برد و به او گفته نمی‌شود.
• شهروند خاص: در بیشتر بازی فقط یک صندلی است: نه اطلاعات، نه انتخاب، نه توانایی. تنها زمانی بیدار می‌شود که نیمی از میز رفته باشد (چهار نفر از هشت نفر). از آن شب به بعد نقش‌های شهروندی را به ترتیب دلخواه نام می‌برد و نخستین نامی که صاحبش پیش‌تر از بازی بیرون رفته باشد از آن او می‌شود: به همان نقش تبدیل می‌شود، با توانایی‌های دوباره پرشده، و تا پایان همان را بازی می‌کند. نام بردن نقشی که هنوز در بازی است فقط یک خطاست و شب بعد دوباره تلاش می‌کند. فقط یک نقش می‌گیرد؛ به هیچ‌کس گفته نمی‌شود و همان شبی که موفق شود با توانایی تازه‌اش عمل می‌کند.""",
            listOf(GODFATHER, CHARLATAN, DOCTOR, DETECTIVE, PSYCHOLOGIST, SHERIFF, DIE_HARD, SPECIAL_HEIR),
            presets = mapOf(8 to m(GODFATHER to 1, CHARLATAN to 1, DOCTOR to 1, DETECTIVE to 1, PSYCHOLOGIST to 1, SHERIFF to 1, DIE_HARD to 1, SPECIAL_HEIR to 1)),
            names = mapOf(PSYCHOLOGIST to "دندانپزشک", DOCTOR to "پزشک"),
            wake = mapOf(GODFATHER to 1, CHARLATAN to 2, PSYCHOLOGIST to 3, DOCTOR to 4, DETECTIVE to 5, SHERIFF to 6, SPECIAL_HEIR to 7, DIE_HARD to 8),
            setup = ScenarioSetup(noIntroDay = true, queryCount = 1), auto = false),
        MafiaScenario("popular", "پرطرفدار", "۱۲، ۱۳ نفره",
            """• سناریوی ۱۲ یا ۱۳ نفره که از شب آغاز می‌شود؛ روز اول کور ندارد. سه مافیا در برابر نه شهروند؛ نقش سیزدهم «مستقل» است.
• وقتی رأی‌گیری کسی را محکوم کرد، فرشتهٔ نجات می‌تواند آن را لغو کند؛ اگر نپذیرد، محکوم یک فرصت حدس فرشته دارد.
• با دو مدافع یا بیشتر، رأی‌گیری دفاع مخفی است: اتاق تاریک می‌شود و فقط شمارش نهایی خوانده می‌شود.
• ترتیب شب: زندانبان ← دست‌کج ← پدرخوانده ← مستقل ← جلب ← شارلاتان ← پزشک ← کارآگاه ← گورکن.
• شرط برد: شهر وقتی همهٔ مافیا حذف شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود؛ مستقل با زنده ماندن تا آخر.
• نقش‌ها:
• پدرخوانده: رهبر مافیا و فرماندهٔ شلیک شب؛ در استعلام کارآگاه منفی.
• شارلاتان: دو بار در بازی، از شب دوم، یک بازیکن را برای آن شب نشانه می‌گیرد؛ اگر کارآگاه همان بازیکن را استعلام کند نتیجه وارونه می‌شود. می‌تواند خودش را هم نشانه بگیرد. شلیک مخصوص ندارد.
• شهردار: از شب دوم دکتر را می‌شناسد و یک بار در بازی می‌تواند کل دفاع را ببخشد — بدون رأی‌گیری و بدون نتیجه. تصمیم شهردار همیشه بر قاضی مقدم است.
• قاضی: یک بار در بازی می‌تواند اعدام روز را وتو کند و یکی از مدافعان را از جایگاه دفاع بیرون بکشد؛ حذف خودش را نمی‌تواند وتو کند. تصمیم شهردار بر قاضی مقدم است.
• گورکن: دو بار در بازی گورها را می‌شکافد؛ صبح روز بعد گرداننده نقش همهٔ بازیکنان حذف‌شده را علنی (بدون نام) اعلام می‌کند.
• گردن‌کلفت: با شلیک شب مافیا بی‌درنگ نمی‌میرد: تمام روز بعد زنده می‌ماند و در پایان آن روز خارج می‌شود. نجات دکتر در همان شب زخم را خنثی می‌کند؛ دو مهاجم در یک شب او را بی‌درنگ می‌کشند. استعلامش بی‌گناه است.
• دست‌کج: یک بار در بازی هر چیزی را که بازیکنی را خاص می‌کند از او می‌رباید (هدف تیم و جان‌هایش را نگه می‌دارد ولی از شب بعد نقش ساده و بی‌توانایی می‌شود). در پایان شب اعمال می‌شود؛ روز بعد شهر می‌فهمد کدام صندلی خالی شده ولی نه اینکه کار چه کسی بوده. مستقل‌ها را نمی‌توان دزدید.
• جلب: تقلب‌کار رأی مافیا با دو توانایی یک‌بارمصرف که هیچ‌کدام اعلام نمی‌شود. (۱) شب پنهانی یک بازیکن را انتخاب می‌کند که رأی روز بعدش بی‌سروصدا باطل می‌شود — هم در رأی‌گیری روز و هم در رأی‌گیری دفاع؛ آن بازیکن مثل همیشه رأی می‌دهد و رأیش نشان داده می‌شود ولی شمرده نمی‌شود. (۲) یک بار در بازی، در رأی‌گیری کور دفاع، رأی دوم دارد: به دو متهم یا دو بار به یک متهم؛ نه وقتی فقط یک نفر در دفاع است و نه وقتی خودش متهم است.
• زندانبان: دو بار در بازی، پیش از مافیا بیدار می‌شود و یک یا دو بازیکن را ۲۴ ساعت به زندان می‌فرستد. زندانی کاملاً از اتاق بیرون می‌رود: نه می‌بیند نه می‌شنود، نوبت صحبت و چالش ندارد، نه رأی می‌دهد نه رأی می‌گیرد، توانایی شبانه‌اش را از دست می‌دهد و هیچ‌کس نمی‌تواند او را هدف بگیرد. اگر دو نفر را بفرستد، آن دو اتاق خصوصی دارند و می‌توانند با هم صحبت کنند. صبح روز بعد به جمع گفته می‌شود چه کسانی زندانی شده‌اند.
• فرشتهٔ نجات: شهروندی با یک نجات. وقتی رأی‌گیری دفاع بازیکنی را محکوم می‌کند، به فرشته پیشنهاد می‌شود که آن را لغو کند و او را در بازی نگه دارد. اگر نپذیرد، بازیکن محکوم یک فرصت دارد تا حدس بزند فرشته کیست؛ حدس درست ← نجات به‌هرحال انجام می‌شود؛ حدس اشتباه ← طبق روال خارج می‌شود. در هر صورت توانایی مصرف می‌شود و فرشته شهروند ساده می‌شود. هرگز نمی‌تواند خودش را نجات دهد و با حدس خودش نامیده نمی‌شود.
• مستقل: فقط برای خودش بازی می‌کند؛ نه تیم، نه اتاق، نه اطلاعات. تنها راه بردش این است که تا آخر سرپا بماند، وقتی کار شهر و مافیا با هم تمام شده باشد؛ تا او زنده است هیچ‌کدام نمی‌توانند بازی را تمام کنند. اولین تیری که به او بخورد از پا درش نمی‌آورد. یک بار در کل بازی شب به هر بازیکنی شلیک می‌کند؛ دکتر می‌تواند هدف را نجات دهد و هرگز اعلام نمی‌شود چه کسی شلیک کرده است.""",
            listOf(GODFATHER, JALAB_V, CHARLATAN, DOCTOR, DETECTIVE, MAYOR, JUDGE, GRAVEDIGGER, TOUGH, PICKPOCKET, JAILER, VOTE_ANGEL, LONER),
            presets = mapOf(12 to m(GODFATHER to 1, JALAB_V to 1, CHARLATAN to 1, DOCTOR to 1, DETECTIVE to 1, MAYOR to 1, JUDGE to 1, GRAVEDIGGER to 1, TOUGH to 1, PICKPOCKET to 1, JAILER to 1, VOTE_ANGEL to 1),
                13 to m(GODFATHER to 1, JALAB_V to 1, CHARLATAN to 1, DOCTOR to 1, DETECTIVE to 1, MAYOR to 1, JUDGE to 1, GRAVEDIGGER to 1, TOUGH to 1, PICKPOCKET to 1, JAILER to 1, VOTE_ANGEL to 1, LONER to 1)),
            names = mapOf(DOCTOR to "پزشک"),
            wake = mapOf(JAILER to 1, PICKPOCKET to 2, GODFATHER to 3, LONER to 4, JALAB_V to 5, CHARLATAN to 6, DOCTOR to 7, DETECTIVE to 8, GRAVEDIGGER to 9),
            setup = ScenarioSetup(noIntroDay = true), auto = false),
        MafiaScenario("pretender", "نیمروز مهم", "۹، ۱۰ نفره",
            """• ۹ یا ۱۰ بازیکن؛ با شب آغاز می‌شود و روز اول کور ندارد. شب نخست فقط معارفه است: مافیا همدیگر را می‌بیند، دورو یک‌طرفه به آن‌ها نگاه می‌کند، کسی اقدامی نمی‌کند و کسی نمی‌میرد.
• سه مافیا (پدرخوانده، شاه‌کش، دورو) مقابل شش شهروند (پزشک، گورکن، وکیل مدافع، گردن‌کلفت، کلانتر، جادوگر)؛ نقش دهم «مستقل» است.
• اتاق مافیا در عمل دو نفره است: دورو هرگز با تیم بیدار نمی‌شود و فقط وقتی آخرین بازماندهٔ مافیا باشد شلیک را به ارث می‌برد.
• با دو بازیکن یا بیشتر در دفاع، رأی‌گیری پنهان است: اتاق تاریک می‌شود و فقط شمارش نهایی خوانده می‌شود.
• ترتیب شب: شاه‌کش ← جادوگر ← پدرخوانده ← مستقل ← پزشک ← کلانتر ← گورکن.
• شرط برد: شهر وقتی همهٔ مافیا (از جمله دورو) حذف شوند؛ مافیا وقتی تعدادش با شهروندان برابر شود؛ مستقل با زنده ماندن تا آخر.
• نقش‌ها:
• پدرخوانده: رهبر مافیا و فرماندهٔ شلیک شب؛ در استعلام منفی.
• جادوگر: جادوگر (شهروند) اول از همه پیش از مافیا بیدار می‌شود. یک بار در شب و یک بار در مرحلهٔ دوم رأی‌گیری می‌تواند کسی را جادو کند: بازیکن جادوشده ۲۴ ساعت همهٔ توانایی‌هایش را از دست می‌دهد و شب هم بیدار نمی‌شود. جادو شدن برای همه اعلام می‌شود. جادو کردن پدرخوانده واقعاً جلوی کشتن را می‌گیرد.
• کلانتر: هر شب یک نفر را انتخاب می‌کند (اجباری نیست): مافیا (از جمله دورو) ← حذف؛ شهروند ← خود کلانتر حذف می‌شود؛ مستقل ← بی‌اثر. روی بازیکن نجات‌یافته توسط دکتر اثری ندارد. گرداننده نتیجه را اعلام می‌کند.
• گورکن: دو بار در بازی گورها را می‌شکافد؛ صبح روز بعد نقش همهٔ بازیکنان حذف‌شده علنی و بدون نام اعلام می‌شود.
• گردن‌کلفت: با شلیک شب مافیا بی‌درنگ نمی‌میرد: روز بعد را زنده می‌ماند و در پایان آن روز خارج می‌شود. نجات دکتر زخم را خنثی می‌کند؛ دو مهاجم در یک شب او را بی‌درنگ می‌کشند.
• شاه‌کش: حدس‌زنندهٔ نقش مافیا. در مجموع دو شارژ دارد که به هر ترکیبی میان دو توانایی‌اش خرج می‌شود و حدس اشتباه هم یک شارژ را می‌سوزاند. شب: یک بازیکن را نشانه می‌رود و نقش دقیقش را حدس می‌زند؛ حدس درست ← آن بازیکن در نتیجهٔ شب خارج می‌شود و اقدام شبانه‌اش هم بی‌اثر می‌ماند. این کار جایگزین شلیک معمول مافیا نیست و هر دو در همان شب انجام می‌شوند. روز: وقتی رأی‌گیری بیش از یک نفر را به دفاع بفرستد و خودش میان آن‌ها نباشد، پیش از آغاز دفاع می‌تواند دوباره حدس بزند (روی هر بازیکن واجد شرایط، نه فقط متهم‌ها)؛ حدس درست هدف را همان‌جا خارج می‌کند و دفاع مثل قبل ادامه می‌یابد؛ حدس اشتباه بی‌اثر است.
• دورو: شهروندنما: مافیای قدیمی و وفاداری که هویتش باید تا آخر پنهان بماند. همهٔ افراد مافیا را با نقش می‌شناسد (در شب معارفه یک‌طرفه به آن‌ها نگاه می‌کند) ولی آن‌ها او را نمی‌شناسند و هرگز با تیم بیدار نمی‌شود. در استعلام‌ها شهروند خوانده می‌شود ولی در شرط پیروزی مافیا حساب می‌شود. اگر کلانتر او را بزند کشته می‌شود. اگر تنها مافیای باقی‌مانده باشد بیدار می‌شود و شلیک را به دست می‌گیرد.
• وکیل مدافع: یک بار در کل بازی، در یکی از رأی‌گیری‌های بلایند، یک بازیکن را موکل خود می‌کند: تا وقتی وکیل در بازی است، آن موکل با رأی‌گیری خارج نمی‌شود. موکل نمی‌داند وکیلش کیست. در روزی که خود وکیل جزو متهمان است نمی‌تواند کسی را انتخاب کند و خودش را هم نمی‌تواند انتخاب کند.
• مستقل: فقط برای خودش بازی می‌کند؛ نه تیم، نه اتاق، نه اطلاعات. تنها راه بردش این است که تا آخر سرپا بماند، وقتی کار شهر و مافیا با هم تمام شده باشد؛ تا او زنده است هیچ‌کدام نمی‌توانند بازی را تمام کنند. اولین تیری که به او بخورد از پا درش نمی‌آورد. یک بار در کل بازی شب به هر بازیکنی شلیک می‌کند؛ دکتر می‌تواند هدف را نجات دهد و هرگز اعلام نمی‌شود چه کسی شلیک کرده است.""",
            listOf(GODFATHER, INQUISITOR, TWO_FACE, DOCTOR, GRAVEDIGGER, DEF_LAWYER, TOUGH, SHERIFF, SORCERER_C, LONER),
            presets = mapOf(9 to m(GODFATHER to 1, INQUISITOR to 1, TWO_FACE to 1, DOCTOR to 1, GRAVEDIGGER to 1, DEF_LAWYER to 1, TOUGH to 1, SHERIFF to 1, SORCERER_C to 1),
                10 to m(GODFATHER to 1, INQUISITOR to 1, TWO_FACE to 1, DOCTOR to 1, GRAVEDIGGER to 1, DEF_LAWYER to 1, TOUGH to 1, SHERIFF to 1, SORCERER_C to 1, LONER to 1)),
            names = mapOf(SORCERER_C to "جادوگر", DOCTOR to "پزشک"),
            wake = mapOf(INQUISITOR to 1, SORCERER_C to 2, GODFATHER to 3, LONER to 4, DOCTOR to 5, SHERIFF to 6, GRAVEDIGGER to 7),
            setup = ScenarioSetup(noIntroDay = true), auto = false),
        MafiaScenario("specter_partners", "همراهان شبح", "۷، ۸ نفره",
            """• ترکیب: ۲ شبح، ۱ پزشک و بقیه شهروند ساده؛ بدون پدرخوانده، بدون کارآگاه و بدون فاز استعلام.
• شب صفر: هر دو شبح هم‌زمان یک تیر خاموش به یک بازیکن زنده می‌زنند و حدس همراه را اعلام می‌کنند.
• اگر هر دو حدس مقابل هم درست باشد، شبح‌ها به‌عنوان یک تیم متصل می‌شوند و از آن به بعد مشورت و شلیک تیمی کلاسیک دارند (راوی این کار را با ابزار «تغییر ساید/بیدار شدن با مافیا» انجام می‌دهد).
• اگر تنها یکی درست بزند، حدس همراه شب بعد برای کسی که هنوز درست حدس نزده فعال می‌ماند.
• ترتیب شب: شبح‌ها (شلیک بی‌صدا) ← پزشک (نجات).
• برد: شهروندان با حذف هر دو شبح؛ مافیا وقتی تعدادش با شهروندان برابر شود.
• نقش‌ها:
• پزشک: پزشک هر شب یک بازیکن را برای محافظت انتخاب می‌کند؛ اگر همان شب شلیک به او شود، نجاتش می‌دهد. توانایی نجات او تا زمانی که شبح‌ها تیم نشده‌اند یا یکی از شبح‌ها اعدام نشده و بازمانده رهبر تنها نشده است، خواب است.
• شبح: شبح یکی از دو غریبهٔ پنهان مافیاست و در ابتدا هویت شبح دیگر را نمی‌داند. در شب صفر هر دو شبح هم‌زمان یک «تیر خاموش» به یکی از بازیکنان زنده شلیک می‌کنند و «حدس همراه» می‌زنند (نام کسی را که گمان می‌کنند شبح دیگر است می‌گویند). اگر هر دو حدس درست باشد، دو شبح یک تیم می‌شوند و از آن پس با هم بیدار می‌شوند و شلیک تیمی دارند. اگر فقط یکی درست حدس بزند، حدس همراه شب بعد برای کسی که هنوز درست نزده فعال می‌ماند.""",
            listOf(SPECTER, DOCTOR, CITIZEN),
            presets = mapOf(7 to m(SPECTER to 2, DOCTOR to 1, CITIZEN to 4),
                8 to m(SPECTER to 2, DOCTOR to 1, CITIZEN to 5)),
            names = mapOf(DOCTOR to "پزشک"),
            wake = mapOf(SPECTER to 1, DOCTOR to 2),
            setup = ScenarioSetup(queryCount = 0), auto = false)
    )

    /** «سناریوی آزاد»: every role, nothing fixed. */
    val free = MafiaScenario(FREE, "سناریوی آزاد", "همهٔ نقش‌ها و کارت‌ها؛ ترکیب کاملاً دلخواه",
        "• همهٔ نقش‌های آماده و نقش‌هایی که خودتان ساخته‌اید در دسترس است.\n• هیچ ترکیب یا قانون ثابتی تحمیل نمی‌شود.", emptyList(), auto = false)

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
