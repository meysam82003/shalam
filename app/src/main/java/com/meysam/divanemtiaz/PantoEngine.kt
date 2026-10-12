package com.meysam.divanemtiaz

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import kotlin.random.Random

/** «پانتومیم»: the five match types of the game, each with its own rules. */
enum class PantoMode(val code: Int, val title: String, val summary: String) {
    NORMAL(1, "عادی", "انتخاب دسته و سختی؛ امتیاز با سختی، زمان باقی‌مانده و خطاها"),
    SPEEDY(2, "سرعتی", "۸ کلمه در هر نوبت؛ هر پاسخ درست به اندازهٔ امتیاز دسته"),
    SEDA(3, "صدابازی", "۵ کلمه فقط با صدا؛ هر پاسخ درست ۲ امتیاز"),
    JANGI(4, "جنگی", "همهٔ تیم‌ها با هم؛ هر دور یک تیم حذف می‌شود"),
    FIVE(5, "پنج‌ثانیه‌ای", "سؤال سه‌بخشی، ۵ ثانیه، پاسخ کامل ۵ امتیاز");

    /** Normal and speedy turns start with choosing a category and level. */
    val picksCategory: Boolean get() = this == NORMAL || this == SPEEDY

    /** Five-second matches are played by single players, the rest by teams. */
    val individual: Boolean get() = this == FIVE

    companion object {
        fun of(code: Int) = values().firstOrNull { it.code == code } ?: NORMAL
    }
}

/** One word or question of the bank (ids, categories and levels exactly as in the game's database). */
data class PantoWord(val id: Int, val cat: Int, val point: Int, val text: String) {
    /** Text for display: Arabic ي/ك shown as Persian ی/ک (the stored text is not changed). */
    val shown: String get() = PantoBank.display(text)
}

/** Category names of the bank. 21, 22 and 50 are the special pools; 9999 holds the players' own words. */
object PantoCategories {
    const val PROVERB = 13
    const val FIVE_SECOND = 21
    const val SOUND = 22
    const val GOLDEN = 50
    const val USER = 9999

    val names: Map<Int, String> = linkedMapOf(
        1 to "اشیاء", 2 to "عمومی", 3 to "فیلم و سریال", 4 to "مشاهیر", 5 to "اماکن و گردشگری", 6 to "ورزشی",
        7 to "تکنولوژی و علمی", 8 to "خوراکی", 9 to "جانوران", 10 to "شغل", 11 to "کتاب", 12 to "تاریخ",
        13 to "ضرب‌المثل", 14 to "انتزاعی", 15 to "شهر و کشور", 16 to "موسیقی", 17 to "کودکان", 18 to "کارتون",
        19 to "سلبریتی", 20 to "فوتبال"
    )

    val specials: Map<Int, String> = linkedMapOf(
        FIVE_SECOND to "پرسش‌های پنج‌ثانیه‌ای", SOUND to "واژه‌های صدابازی", GOLDEN to "محتوای طلایی", USER to "کلمات من"
    )

    fun name(cat: Int): String = names[cat] ?: specials[cat] ?: "دسته $cat"

    val levelNames = mapOf(1 to "آسان", 2 to "متوسط", 3 to "سخت")
}

/**
 * The words bank: all records of the game's database, loaded once and indexed by category and level. Players' own
 * words (category 9999) are kept apart and joined when drawing.
 */
class PantoBank(val words: List<PantoWord>) {
    private val byCatLevel: Map<Pair<Int, Int>, List<PantoWord>> = words.groupBy { it.cat to it.point }
    private val byCat: Map<Int, List<PantoWord>> = words.groupBy { it.cat }

    fun of(cat: Int, level: Int): List<PantoWord> = byCatLevel[cat to level] ?: emptyList()
    fun of(cat: Int): List<PantoWord> = byCat[cat] ?: emptyList()
    fun count(cat: Int): Int = byCat[cat]?.size ?: 0

    companion object {
        const val ASSET = "panto/words.tsv"
        @Volatile private var loaded: PantoBank? = null

        fun get(open: () -> InputStream): PantoBank = loaded ?: synchronized(this) { loaded ?: read(open()).also { loaded = it } }

        fun read(input: InputStream): PantoBank {
            val out = ArrayList<PantoWord>(18100)
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isEmpty()) return@forEach
                    val a = line.split('\t', limit = 4)
                    if (a.size == 4) out += PantoWord(a[0].toInt(), a[1].toInt(), a[2].toInt(), unescape(a[3]))
                }
            }
            return PantoBank(out)
        }

        private fun unescape(s: String): String {
            if ('\\' !in s) return s
            val b = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        't' -> b.append('\t'); 'n' -> b.append('\n'); 'r' -> b.append('\r'); else -> b.append(s[i + 1])
                    }
                    i += 2
                } else { b.append(c); i++ }
            }
            return b.toString()
        }

        fun display(s: String): String = s.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').trim()
    }
}

/** A category and level a team plays (with its points and automatic time). */
data class PantoChoice(val cat: Int, val level: Int) {
    val golden: Boolean get() = cat == PantoCategories.GOLDEN

    /** Points of the subject: 2/4/6 for the categories and the players' own words, 3/5/7 for proverbs, 30 golden. */
    val points: Int get() = when {
        golden -> 30
        cat == PantoCategories.PROVERB -> when (level) { 1 -> 3; 2 -> 5; else -> 7 }
        else -> level * 2
    }

    /** Automatic time in seconds: 90/105/120, proverbs 105/120/165, golden 180. */
    val seconds: Int get() = when {
        golden -> 180
        cat == PantoCategories.PROVERB -> when (level) { 1 -> 105; 2 -> 120; else -> 165 }
        else -> when (level) { 1 -> 90; 2 -> 105; else -> 120 }
    }

    val title: String get() = if (golden) "موضوع طلایی" else "${PantoCategories.name(cat)} • ${PantoCategories.levelNames[level] ?: ""}"

    val key: String get() = "$cat:$level"
}

class PantoTeam(
    var name: String,
    var avatar: Int = 0,
    /** People of the team (for each person's record); a five-second player is his own member. */
    val members: MutableList<String> = ArrayList(),
    var points: Int = 0,
    var faults: Int = 0,
    var inGame: Boolean = true,
    /** Number of people in the team (any number; the named members when there are any). */
    var size: Int = 2,
    /** Rotation of the performer: how many turns the team has played. */
    var turns: Int = 0,
    /** Right answers over the match. */
    var correct: Int = 0
) {
    /** People counted in the team. */
    val people: Int get() = if (members.isNotEmpty()) members.size else size.coerceAtLeast(1)

    /** Who performs the team's next turn (members in turn, or «نفر n»). */
    val performer: String get() = if (members.isNotEmpty()) members[turns % members.size] else "نفر ${PersianText.digits(turns % people + 1)}"
}

/** The turn being played: its words, answers, faults and time (kept so a game can be resumed). */
class PantoTurn(
    val team: Int,
    val choice: PantoChoice?,
    val words: MutableList<PantoWord> = ArrayList(),
    val answers: MutableList<Boolean?> = ArrayList(),
    var index: Int = 0,
    var faults: Int = 0,
    var wordChanged: Boolean = false,
    val seconds: Int,
    var remainingMs: Long,
    /** Battle mode: position of the team acting now among the teams still in. */
    var jangiPos: Int = 0
) {
    val word: PantoWord? get() = words.getOrNull(index)
    val correct: Int get() = answers.count { it == true }
}

data class PantoLog(val round: Int, val golden: Boolean, val team: Int, val text: String, val points: Int)

class PantoMatch(val id: Long) {
    var mode: PantoMode = PantoMode.NORMAL
    val teams: MutableList<PantoTeam> = ArrayList()
    var rounds: Int = 3
    /** Manual time in seconds, or null for the automatic time of each mode. */
    var manualSeconds: Int? = null
    var round: Int = 1
    /** Index of the team whose turn it is. */
    var current: Int = 0
    var golden: Boolean = false
    /** Optional neutral referee. */
    var referee: String = ""
    /** Golden round announced to the players (shown once when it begins). */
    var goldenShown: Boolean = false
    /** Subjects each team has played: "team:cat:level" (each subject once per team in a match). */
    val usedSubjects: MutableSet<String> = HashSet()
    var turn: PantoTurn? = null
    var finished: Boolean = false
    var createdAt: Long = System.currentTimeMillis()
    var updatedAt: Long = createdAt
    val log: MutableList<PantoLog> = ArrayList()

    val inside: List<Int> get() = teams.indices.filter { teams[it].inGame }

    /** Teams from best to worst by points. */
    val standings: List<Int> get() = teams.indices.sortedWith(compareByDescending<Int> { teams[it].points }.thenBy { it })

    val winner: Int? get() = if (!finished) null else standings.firstOrNull()
}

/** Rules of «پانتومیم», exactly as the game plays them. */
object PantoEngine {
    const val SPEEDY_WORDS = 8
    const val SEDA_WORDS = 5
    const val MAX_FAULTS = 3
    const val FIVE_SECONDS = 5
    const val FIVE_POINTS = 5
    const val JANGI_BONUS = 10
    const val GOLDEN_FAULTS_OUT = true
    const val MIN_TEAMS = 2
    const val MAX_TEAMS = 6
    const val MIN_ROUNDS = 3
    const val MAX_ROUNDS = 30
    const val MIN_TIME = 45
    const val MAX_TIME = 345
    const val TIME_STEP = 30
    /** The players' own words appear as a category once there are this many. */
    const val USER_WORDS_MIN = 10
    private const val LETTERS = "ضصثقفغعهخحجچشسیبلاتنمکگپظطزرذدئوژ"

    fun create(id: Long, mode: PantoMode, teams: List<PantoTeam>, rounds: Int, manualSeconds: Int?): PantoMatch {
        require(teams.size in MIN_TEAMS..MAX_TEAMS) { "teams" }
        return PantoMatch(id).apply {
            this.mode = mode
            this.teams += teams
            // Battle: one team leaves each round, so there are teams − 1 rounds.
            this.rounds = if (mode == PantoMode.JANGI) teams.size - 1 else rounds.coerceIn(MIN_ROUNDS, MAX_ROUNDS)
            this.manualSeconds = if (mode == PantoMode.FIVE) null else manualSeconds?.coerceIn(MIN_TIME, MAX_TIME)
        }
    }

    /** Seconds of a turn: manual time, or per mode (category time, 60 s for sound, 30 s per team in battle, 5 s). */
    fun seconds(m: PantoMatch, choice: PantoChoice?): Int = when (m.mode) {
        PantoMode.FIVE -> FIVE_SECONDS
        PantoMode.NORMAL, PantoMode.SPEEDY -> m.manualSeconds ?: (choice?.seconds ?: 90)
        PantoMode.SEDA -> m.manualSeconds ?: 60
        PantoMode.JANGI -> m.manualSeconds ?: (m.inside.size * 30)
    }

    /** A subject offered to the current team, and whether it can still be chosen. */
    data class Option(val choice: PantoChoice, val enabled: Boolean)

    /**
     * Subjects for the current team: the golden subject (normal only, once), the players' own words (normal only,
     * from [USER_WORDS_MIN] words), proverbs and every other category in three levels — each once per team.
     */
    fun options(m: PantoMatch, userWords: List<PantoWord>): List<Option> {
        val team = m.current
        fun used(c: PantoChoice) = "$team:${c.key}" in m.usedSubjects
        val out = ArrayList<Option>()
        if (m.mode == PantoMode.NORMAL) {
            val g = PantoChoice(PantoCategories.GOLDEN, PantoCategories.GOLDEN)
            out += Option(g, !used(g))
            if (userWords.size >= USER_WORDS_MIN) (1..3).forEach { l ->
                val c = PantoChoice(PantoCategories.USER, l)
                out += Option(c, userWords.any { it.point == l } && !used(c))
            }
        }
        (1..3).forEach { l -> PantoChoice(PantoCategories.PROVERB, l).let { out += Option(it, !used(it)) } }
        PantoCategories.names.keys.filter { it != PantoCategories.PROVERB }.forEach { cat ->
            (1..3).forEach { l -> PantoChoice(cat, l).let { out += Option(it, !used(it)) } }
        }
        return out
    }

    // ------------------------------------------------------------------ drawing words

    /**
     * Random record of [pool] not used yet. When none is left the used list is cleared (as the game does) and the
     * draw is repeated. [used] holds the ids shown before (kept across matches).
     */
    fun draw(pool: List<PantoWord>, used: MutableSet<Int>, rng: Random): PantoWord? {
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it.id !in used }
        val pick = if (fresh.isNotEmpty()) fresh.random(rng) else { used.clear(); pool.random(rng) }
        used += pick.id
        return pick
    }

    /** The golden subject: a long proverb (one time in four) or two short words of two different categories. */
    fun goldenWord(bank: PantoBank, used: MutableSet<Int>, rng: Random): PantoWord {
        if (rng.nextInt(1, 5) == 1) {
            val long = bank.of(PantoCategories.PROVERB).filter { it.text.length > 25 }
            draw(long, used, rng)?.let { return it.copy(cat = PantoCategories.GOLDEN, point = PantoCategories.GOLDEN) }
        }
        val cats = intArrayOf(1, 2, 9, 10)
        val a = rng.nextInt(1, 5)
        var b = rng.nextInt(1, 5)
        while (b == a) b = rng.nextInt(1, 5)
        fun short(i: Int) = bank.of(cats[i - 1]).filter { it.text.length < 8 }.randomOrNull(rng)?.text ?: ""
        return PantoWord(-1, PantoCategories.GOLDEN, PantoCategories.GOLDEN, "${short(a)} ${short(b)}".trim())
    }

    /** Battle word: a short word (under 10 letters) of general, sport, jobs or sound words. */
    fun jangiWord(bank: PantoBank, used: MutableSet<Int>, rng: Random): PantoWord? {
        val cat = when (rng.nextInt(1, 5)) { 1 -> 2; 2 -> 6; 3 -> 10; else -> PantoCategories.SOUND }
        return draw(bank.of(cat).filter { it.text.length < 10 }, used, rng)
    }

    /** Five-second question: «۳تا … با حرف … را نام ببرید» (level 1) or «۳تا … را نام ببرید» (level 2). */
    fun fiveQuestion(bank: PantoBank, used: MutableSet<Int>, rng: Random): PantoWord? {
        val w = draw(bank.of(PantoCategories.FIVE_SECOND), used, rng) ?: return null
        val text = if (w.point == 1) "۳ تا ${w.shown} با حرف «${LETTERS[rng.nextInt(LETTERS.length)]}» را نام ببرید"
        else "۳ تا ${w.shown} را نام ببرید"
        return w.copy(text = text)
    }

    private fun pool(bank: PantoBank, userWords: List<PantoWord>, c: PantoChoice): List<PantoWord> =
        if (c.cat == PantoCategories.USER) userWords.filter { it.point == c.level } else bank.of(c.cat, c.level)

    // ------------------------------------------------------------------ turns

    /** Starts the current team's turn ([choice] for normal and speedy). Returns null when no word can be drawn. */
    fun startTurn(m: PantoMatch, bank: PantoBank, userWords: List<PantoWord>, used: MutableSet<Int>, choice: PantoChoice?, rng: Random): PantoTurn? {
        if (m.mode.picksCategory) requireNotNull(choice)
        val sec = seconds(m, choice)
        val t = PantoTurn(team = m.current, choice = choice, seconds = sec, remainingMs = sec * 1000L)
        when (m.mode) {
            PantoMode.NORMAL -> {
                val c = choice!!
                t.words += (if (c.golden) goldenWord(bank, used, rng) else draw(pool(bank, userWords, c), used, rng) ?: return null)
            }
            PantoMode.SPEEDY -> repeat(SPEEDY_WORDS) { t.words += draw(pool(bank, userWords, choice!!), used, rng) ?: return null }
            PantoMode.SEDA -> repeat(SEDA_WORDS) { t.words += draw(bank.of(PantoCategories.SOUND), used, rng) ?: return null }
            PantoMode.JANGI -> t.words += jangiWord(bank, used, rng) ?: return null
            PantoMode.FIVE -> t.words += fiveQuestion(bank, used, rng) ?: return null
        }
        repeat(t.words.size) { t.answers += null }
        // Subjects are used up when chosen; the players' own words stay open as in the game.
        if (choice != null && choice.cat != PantoCategories.USER) m.usedSubjects += "${m.current}:${choice.key}"
        m.turn = t
        return t
    }

    /** Normal points: subject − faults + 1 per 30 seconds left − 1 for a changed word; 3 faults or less than 0 → 0. */
    fun normalPoints(subject: Int, faults: Int, remainingSeconds: Int, wordChanged: Boolean): Int {
        if (faults >= MAX_FAULTS) return 0
        return (subject - faults + remainingSeconds / 30 - (if (wordChanged) 1 else 0)).coerceAtLeast(0)
    }

    /** Speedy points: correct answers × subject points − faults; sound mode: correct × 2 − faults. */
    fun listPoints(m: PantoMatch, t: PantoTurn): Int {
        if (t.faults >= MAX_FAULTS) return 0
        val each = if (m.mode == PantoMode.SEDA) 2 else (t.choice?.points ?: 0)
        return (t.correct * each - t.faults).coerceAtLeast(0)
    }

    /** Result of a turn, for the narrator. */
    data class TurnEnd(val points: Int, val eliminated: Boolean, val text: String)

    /** One fault; the third ends the turn with no points (and puts a golden-subject team out). */
    fun fault(m: PantoMatch): TurnEnd? {
        val t = m.turn ?: return null
        t.faults++
        if (t.faults < MAX_FAULTS) return null
        val golden = t.choice?.golden == true
        if (golden) m.teams[t.team].inGame = false
        return finishTurn(m, 0, golden, "۳ خطا؛ امتیازی به این نوبت نمی‌رسد" + if (golden) " و تیم از مسابقه خارج می‌شود" else "")
    }

    /** Normal mode: the team got the word right. */
    fun correct(m: PantoMatch): TurnEnd? {
        val t = m.turn ?: return null
        val pts = normalPoints(t.choice?.points ?: 0, t.faults, (t.remainingMs / 1000).toInt(), t.wordChanged)
        return finishTurn(m, pts, false, "پاسخ درست")
    }

    /** Normal mode: wrong answer or time over. A missed golden subject puts the team out of the match. */
    fun incorrect(m: PantoMatch, timeUp: Boolean = false): TurnEnd? {
        val t = m.turn ?: return null
        val golden = t.choice?.golden == true
        if (golden) m.teams[t.team].inGame = false
        return finishTurn(m, 0, golden, (if (timeUp) "زمان تمام شد" else "پاسخ نادرست") + if (golden) "؛ تیم از مسابقه خارج می‌شود" else "")
    }

    /** Normal mode: one change of word (costs one point). */
    fun changeWord(m: PantoMatch, bank: PantoBank, userWords: List<PantoWord>, used: MutableSet<Int>, rng: Random): Boolean {
        val t = m.turn ?: return false
        if (t.wordChanged || m.mode != PantoMode.NORMAL) return false
        val c = t.choice ?: return false
        val w = if (c.golden) goldenWord(bank, used, rng) else draw(pool(bank, userWords, c), used, rng) ?: return false
        t.words[t.index] = w
        t.wordChanged = true
        return true
    }

    /** Speedy and sound modes: answer to the current word; after the last word the turn is scored. */
    fun answer(m: PantoMatch, right: Boolean): TurnEnd? {
        val t = m.turn ?: return null
        if (t.index >= t.words.size) return null
        t.answers[t.index] = right
        t.index++
        return if (t.index >= t.words.size) endList(m) else null
    }

    /** Speedy and sound modes: scores the words answered so far (time over). */
    fun endList(m: PantoMatch): TurnEnd? {
        val t = m.turn ?: return null
        val pts = listPoints(m, t)
        return finishTurn(m, pts, false, "${PersianText.digits(t.correct)} پاسخ درست از ${PersianText.digits(t.words.size)}")
    }

    /** A team that does not play its turn gets no points for it. */
    fun skip(m: PantoMatch): TurnEnd? {
        if (m.turn == null) m.turn = PantoTurn(m.current, null, seconds = 0, remainingMs = 0)
        return finishTurn(m, 0, false, "نوبت بازی نشد")
    }

    /** Five-second mode: full answer (+5) or not. */
    fun fiveAnswer(m: PantoMatch, full: Boolean): TurnEnd? = finishTurn(m, if (full) FIVE_POINTS else 0, false, if (full) "پاسخ کامل" else "پاسخ ناقص")

    /** Battle: the acting team got it; the next team in the circle plays a new word. */
    fun jangiSaid(m: PantoMatch, bank: PantoBank, used: MutableSet<Int>, rng: Random) {
        val t = m.turn ?: return
        val n = m.inside.size
        t.jangiPos = if (n == 0) 0 else (t.jangiPos + 1) % n
        t.answers[t.index] = true
        val w = jangiWord(bank, used, rng) ?: return
        t.words += w
        t.answers += null
        t.index = t.words.lastIndex
    }

    /** Battle: time is over; the team whose turn it is leaves, the others get 10 points, and the next round begins. */
    fun jangiTimeUp(m: PantoMatch): TurnEnd? {
        val t = m.turn ?: return null
        val inside = m.inside
        val out = inside.getOrNull(t.jangiPos) ?: return null
        m.teams[out].inGame = false
        m.inside.forEach { m.teams[it].points += JANGI_BONUS }
        m.log += PantoLog(m.round, false, out, "زمان تمام شد؛ ${m.teams[out].name} حذف شد و هر تیم باقی‌مانده ${PersianText.digits(JANGI_BONUS)} امتیاز گرفت", 0)
        m.turn = null
        if (m.round >= m.rounds || m.inside.size <= 1) finish(m)
        else {
            m.round++
            m.current = m.inside.first()
        }
        m.updatedAt = System.currentTimeMillis()
        return TurnEnd(0, true, "${m.teams[out].name} حذف شد")
    }

    private fun finishTurn(m: PantoMatch, points: Int, eliminated: Boolean, text: String): TurnEnd {
        val t = m.turn
        val team = t?.team ?: m.current
        m.teams[team].points += points
        if (points != 0 && t != null) m.teams[team].faults += t.faults
        if (t != null && t.words.isNotEmpty()) {
            m.teams[team].turns++
            m.teams[team].correct += when (m.mode) {
                PantoMode.SPEEDY, PantoMode.SEDA -> t.correct
                else -> if (points > 0) 1 else 0
            }
        }
        m.log += PantoLog(m.round, m.golden, team, listOfNotNull(t?.choice?.title, text).joinToString(" • "), points)
        m.turn = null
        advance(m)
        m.updatedAt = System.currentTimeMillis()
        return TurnEnd(points, eliminated, text)
    }

    /** Next team / round; after the last turn of the last round a tie at the top starts the golden round. */
    fun advance(m: PantoMatch) {
        val inside = m.inside
        val last = inside.lastOrNull()
        when {
            inside.isEmpty() -> finish(m)
            m.round >= m.rounds && m.current >= last!! -> {
                val tied = topTied(m)
                if (tied.size <= 1) finish(m)
                else {
                    // Golden round: only the tied teams play, again and again until one of them leads.
                    m.golden = true
                    m.goldenShown = false
                    m.teams.forEachIndexed { i, t -> t.inGame = i in tied }
                    m.current = next(m, m.current)
                }
            }
            next(m, m.current) == m.current -> finish(m)
            else -> {
                val n = next(m, m.current)
                if (m.current >= last!!) m.round++
                m.current = n
            }
        }
    }

    /** Teams sharing the highest score (all teams are compared, as in the game). */
    fun topTied(m: PantoMatch): List<Int> {
        val best = m.teams.maxOfOrNull { it.points } ?: return emptyList()
        return m.teams.indices.filter { m.teams[it].points == best }
    }

    /** Next team still in the match after [from] (wrapping around). */
    fun next(m: PantoMatch, from: Int): Int {
        val n = m.teams.size
        var i = (from + 1) % n
        repeat(n) {
            if (m.teams[i].inGame) return i
            i = (i + 1) % n
        }
        return from
    }

    fun finish(m: PantoMatch) {
        m.finished = true
        m.turn = null
    }

    /** A new match with the same mode, teams and settings. */
    fun rematch(id: Long, old: PantoMatch): PantoMatch = create(id, old.mode,
        old.teams.map { PantoTeam(it.name, it.avatar, ArrayList(it.members), size = it.size) }, if (old.mode == PantoMode.JANGI) old.teams.size - 1 else old.rounds, old.manualSeconds)

    // ------------------------------------------------------------------ the players' own words

    /** Splits [people] at random into [teams] teams as evenly as possible. */
    fun split(people: List<String>, teams: Int, rng: Random): List<List<String>> {
        val shuffled = people.shuffled(rng)
        return (0 until teams).map { t -> shuffled.filterIndexed { i, _ -> i % teams == t } }
    }

    /** Validation of a new word: not empty and not a duplicate of another own word. */
    fun userWordError(text: String, existing: List<PantoWord>, exceptId: Int? = null): String? {
        val t = text.trim()
        if (t.isEmpty()) return "متن خالی است"
        if (existing.any { it.id != exceptId && SearchText.norm(it.text) == SearchText.norm(t) }) return "این کلمه تکراری است"
        return null
    }

    /** Rules of each match type (texts of the game). */
    val commonRules = listOf(
        "می‌توانید یک نفر را به عنوان ناظر بی‌طرف برای داوری انتخاب کنید.",
        "اگر تیمی دور خودش را بازی نکند، از دریافت امتیاز آن مرحله محروم خواهد شد.",
        "لب زدن و اشاره به اشیاء در حین انجام بازی، خطا محسوب می‌شود.",
        "در صورت برابری امتیاز گروه‌ها در انتهای دور پایانی، یک دور جدید به نام دور طلایی خواهیم داشت.",
        "دور طلایی به روش حذفی برگزار می‌گردد؛ بنابراین هر تیم که امتیاز بیشتری در آن دور بیاورد، برندهٔ بازی خواهد شد.",
        "تا زمانی که برنده‌ای نداشته باشیم، دور طلایی تکرار خواهد شد."
    )

    fun rules(mode: PantoMode): List<String> = commonRules + when (mode) {
        PantoMode.NORMAL -> listOf(
            "اگر اجراکنندهٔ تیم در حین اجرا مرتکب ۳ خطا شود، تیم وی در آن دور بازنده خواهد شد.",
            "به ازای هر ۳۰ ثانیه اضافه، یک امتیاز به شما تعلق خواهد گرفت.",
            "اگر موضوع طلایی را انتخاب کرده و به آن پاسخ صحیح ندهید، از جریان بازی خارج خواهید شد و دیگر حق ادامهٔ مسابقه را ندارید.",
            "امتیاز هر دسته: آسان ۲، متوسط ۴، سخت ۶ (ضرب‌المثل ۳، ۵ و ۷؛ موضوع طلایی ۳۰). هر خطا یک امتیاز و هر بار عوض کردن کلمه یک امتیاز کم می‌کند."
        )
        PantoMode.SPEEDY -> listOf(
            "اگر اجراکنندهٔ تیم در حین اجرا مرتکب ۳ خطا شود، تیم وی در آن دور بازنده خواهد شد.",
            "در مسابقهٔ سرعتی، هر دور شامل ۸ کلمه است.",
            "در مسابقهٔ سرعتی، امتیاز هر دور، مجموع پاسخ‌های صحیح به ۸ کلمه خواهد بود."
        )
        PantoMode.SEDA -> listOf(
            "اگر اجراکنندهٔ تیم در حین اجرا مرتکب ۳ خطا شود، تیم وی در آن دور بازنده خواهد شد.",
            "در مسابقهٔ صدابازی، هر دور شامل ۵ کلمه است.",
            "در مسابقهٔ صدابازی، امتیاز هر دور، مجموع پاسخ‌های صحیح به ۵ کلمه خواهد بود.",
            "در مسابقهٔ صدابازی، اجراکننده صرفاً باید صدای کلمهٔ نمایش‌داده‌شده را دربیاورد."
        )
        PantoMode.JANGI -> listOf(
            "در مسابقهٔ جنگی، تمامی تیم‌ها هم‌زمان با هم به انجام مسابقه می‌پردازند.",
            "در مسابقهٔ جنگی، تیمی که در هنگام پایان زمان مسابقه موفق به اجرای درست واژه نشده باشد بازنده خواهد بود.",
            "در مسابقهٔ جنگی، در هر دور یک تیم حذف خواهد شد. برندهٔ مسابقه تیمی است که در انتها بدون حذف باقی می‌ماند.",
            "در مسابقهٔ پنج‌ثانیه‌ای و جنگی، نشانگر نوبت با حلقهٔ سبزرنگ، نشان‌دهندهٔ نوبت افراد یا تیم‌ها برای انجام بازی است."
        )
        PantoMode.FIVE -> listOf(
            "در هر مسابقهٔ ۵ ثانیه‌ای، ۶ نفر مجاز به شرکت در مسابقه هستند که هم‌زمان به انجام مسابقه می‌پردازند.",
            "هر دور از مسابقهٔ ۵ ثانیه‌ای شامل یک سؤال ۳ بخشی برای هر یک از شرکت‌کنندگان است که برای پاسخ به آن ۵ ثانیه زمان خواهند داشت.",
            "در مسابقهٔ ۵ ثانیه‌ای برای سنجش درستی پاسخ‌ها می‌توانید یک نفر بی‌طرف را به عنوان داور تعیین کنید.",
            "پاسخ کامل به هر سؤال مسابقهٔ ۵ ثانیه‌ای، ۵ امتیاز خواهد داشت.",
            "در مسابقهٔ پنج‌ثانیه‌ای و جنگی، نشانگر نوبت با حلقهٔ سبزرنگ، نشان‌دهندهٔ نوبت افراد یا تیم‌ها برای انجام بازی است."
        )
    }
}

/** JSON form of a match (saved after every action, so a game survives rotation and restarts). */
object PantoCodec {
    private fun word(w: PantoWord) = JSONObject().put("i", w.id).put("c", w.cat).put("p", w.point).put("t", w.text)
    private fun word(o: JSONObject) = PantoWord(o.optInt("i"), o.optInt("c"), o.optInt("p"), o.optString("t"))

    fun encode(m: PantoMatch): JSONObject = JSONObject().apply {
        put("id", m.id); put("mode", m.mode.code); put("rounds", m.rounds); m.manualSeconds?.let { put("manual", it) }
        put("round", m.round); put("current", m.current); put("golden", m.golden); put("goldenShown", m.goldenShown); put("referee", m.referee)
        put("finished", m.finished); put("created", m.createdAt); put("updated", m.updatedAt)
        put("used", JSONArray(m.usedSubjects.toList()))
        put("teams", JSONArray(m.teams.map { t ->
            JSONObject().put("n", t.name).put("a", t.avatar).put("m", JSONArray(t.members)).put("p", t.points).put("f", t.faults).put("in", t.inGame)
                .put("sz", t.size).put("tu", t.turns).put("co", t.correct)
        }))
        put("log", JSONArray(m.log.map { JSONObject().put("r", it.round).put("g", it.golden).put("t", it.team).put("x", it.text).put("p", it.points) }))
        m.turn?.let { t ->
            put("turn", JSONObject().apply {
                put("team", t.team); t.choice?.let { put("cat", it.cat); put("lvl", it.level) }
                put("words", JSONArray(t.words.map { word(it) }))
                put("answers", JSONArray(t.answers.map { if (it == null) 0 else if (it) 1 else -1 }))
                put("index", t.index); put("faults", t.faults); put("changed", t.wordChanged); put("sec", t.seconds)
                put("rem", t.remainingMs); put("jpos", t.jangiPos)
            })
        }
    }

    fun decode(o: JSONObject): PantoMatch = PantoMatch(o.optLong("id")).apply {
        mode = PantoMode.of(o.optInt("mode", 1)); rounds = o.optInt("rounds", 3)
        manualSeconds = if (o.has("manual")) o.optInt("manual") else null
        round = o.optInt("round", 1); current = o.optInt("current"); golden = o.optBoolean("golden"); goldenShown = o.optBoolean("goldenShown"); referee = o.optString("referee")
        finished = o.optBoolean("finished"); createdAt = o.optLong("created"); updatedAt = o.optLong("updated", createdAt)
        o.optJSONArray("used")?.let { a -> for (i in 0 until a.length()) usedSubjects += a.optString(i) }
        o.optJSONArray("teams")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { t ->
                teams += PantoTeam(t.optString("n"), t.optInt("a"), ArrayList<String>().apply {
                    t.optJSONArray("m")?.let { mm -> for (k in 0 until mm.length()) add(mm.optString(k)) }
                }, t.optInt("p"), t.optInt("f"), t.optBoolean("in", true), t.optInt("sz", 2), t.optInt("tu"), t.optInt("co"))
            }
        }
        o.optJSONArray("log")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { log += PantoLog(it.optInt("r"), it.optBoolean("g"), it.optInt("t"), it.optString("x"), it.optInt("p")) }
        }
        o.optJSONObject("turn")?.let { t ->
            val choice = if (t.has("cat")) PantoChoice(t.optInt("cat"), t.optInt("lvl")) else null
            turn = PantoTurn(t.optInt("team"), choice, seconds = t.optInt("sec"), remainingMs = t.optLong("rem")).apply {
                t.optJSONArray("words")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { words += word(it) } }
                t.optJSONArray("answers")?.let { a -> for (i in 0 until a.length()) answers += when (a.optInt(i)) { 1 -> true; -1 -> false; else -> null } }
                index = t.optInt("index"); faults = t.optInt("faults"); wordChanged = t.optBoolean("changed"); jangiPos = t.optInt("jpos")
            }
        }
    }

    fun encodeWords(list: List<PantoWord>): String = JSONArray(list.map { word(it) }).toString()

    fun decodeWords(raw: String?): List<PantoWord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { JSONArray(raw).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> word(o) } } } }.getOrDefault(emptyList())
    }
}

/** Matches, the used-word list (no repeats across matches) and the players' own words, in the app's store. */
class PantoRepository(private val kv: KeyValueStore) {
    fun index(): List<Long> = kv.get(KEY_INDEX)?.let { raw -> runCatching { JSONArray(raw).let { a -> List(a.length()) { a.optLong(it) } } }.getOrNull() } ?: emptyList()

    fun match(id: Long): PantoMatch? = kv.get(KEY_MATCH + id)?.let { runCatching { PantoCodec.decode(JSONObject(it)) }.getOrNull() }

    fun matches(): List<PantoMatch> = index().mapNotNull { match(it) }.sortedByDescending { it.updatedAt }

    fun save(m: PantoMatch) {
        m.updatedAt = System.currentTimeMillis()
        val ids = listOf(m.id) + index().filterNot { it == m.id }
        kv.write(mapOf(KEY_MATCH + m.id to PantoCodec.encode(m).toString(), KEY_INDEX to JSONArray(ids).toString()))
    }

    fun delete(id: Long) = kv.write(mapOf(KEY_MATCH + id to null, KEY_INDEX to JSONArray(index().filterNot { it == id }).toString()))

    fun latestUnfinished(): PantoMatch? = matches().firstOrNull { !it.finished }

    /** Ids already shown (the game's TempQuestions), cleared when a pool runs out. */
    fun used(): MutableSet<Int> = kv.get(KEY_USED)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toHashSet() ?: HashSet()

    fun saveUsed(s: Set<Int>) = kv.write(mapOf(KEY_USED to s.joinToString(",")))

    fun userWords(): List<PantoWord> = PantoCodec.decodeWords(kv.get(KEY_USER_WORDS))

    fun saveUserWords(list: List<PantoWord>) = kv.write(mapOf(KEY_USER_WORDS to PantoCodec.encodeWords(list)))

    /** Next id for an own word (kept above the bank's ids). */
    fun nextUserWordId(): Int = (userWords().maxOfOrNull { it.id } ?: USER_ID_BASE).coerceAtLeast(USER_ID_BASE) + 1

    companion object {
        const val KEY_INDEX = "panto_index"
        const val KEY_MATCH = "panto_match_"
        const val KEY_USED = "panto_used"
        const val KEY_USER_WORDS = "panto_user_words"
        const val USER_ID_BASE = 1_000_000
    }
}
