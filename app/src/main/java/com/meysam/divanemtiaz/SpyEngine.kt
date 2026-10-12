package com.meysam.divanemtiaz

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import kotlin.random.Random

/** «جاسوس»: word categories of the game (and the players' own words). */
enum class SpyCategory(val title: String, val key: String) {
    PLACE("مکان", "مکان‌ها"), FOOD("غذا", "غذاها"), ANIMAL("حیوان", "حیوانات"), JOB("شغل", "شغل‌ها"), USER("کلمات کاربر", "");

    companion object {
        fun of(name: String?) = values().firstOrNull { it.name == name } ?: USER
    }
}

data class SpyWord(val id: Int, val text: String, val category: SpyCategory, val user: Boolean = false)

/** How a game ended. */
enum class SpyOutcome(val title: String, val spiesWin: Boolean) {
    FOUND("جاسوس شناسایی شد", false),
    HIDDEN("جاسوس تا پایان شناسایی نشد", true),
    GUESSED("جاسوس خودش را معرفی کرد و کلمه را درست حدس زد", true);

    companion object {
        fun of(name: String?) = values().firstOrNull { it.name == name }
    }
}

data class SpySettings(val players: Int = 6, val spies: Int = 1, val minutes: Int = 5)

class SpyGame(val id: Long) {
    /** Names of the players in seating order (people of the roster or «بازیکن n»). */
    val players: MutableList<String> = ArrayList()
    val avatars: MutableList<Int> = ArrayList()
    val spies: MutableSet<Int> = HashSet()
    var word: String = ""
    var category: SpyCategory = SpyCategory.PLACE
    var minutes: Int = 5
    /** Player whose card is next (players.size once everyone has seen his card). */
    var revealIndex: Int = 0
    var revealed: Boolean = false
    var remainingMs: Long = 0
    var finished: Boolean = false
    var outcome: SpyOutcome? = null
    /** Players were chosen from the people list (their games count in the rankings). */
    var named: Boolean = false
    var createdAt: Long = System.currentTimeMillis()
    var updatedAt: Long = createdAt

    val allViewed: Boolean get() = revealIndex >= players.size
    val spiesWin: Boolean? get() = outcome?.spiesWin
    fun isSpy(i: Int) = i in spies
}

/** Rules of «جاسوس». */
object SpyEngine {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 30
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 30

    /** Why a game cannot start, or null. */
    fun error(s: SpySettings, enabledWords: Int): String? = when {
        s.players < MIN_PLAYERS -> "دست‌کم ${PersianText.digits(MIN_PLAYERS)} بازیکن لازم است"
        s.spies < 1 -> "دست‌کم یک جاسوس لازم است"
        s.players <= s.spies -> "تعداد بازیکنان باید از تعداد جاسوس‌ها بیشتر باشد"
        enabledWords == 0 -> "دست‌کم یک کلمه باید انتخاب شده باشد"
        else -> null
    }

    /** New game: one random word of the enabled words and [SpySettings.spies] different random spies. */
    fun start(id: Long, s: SpySettings, names: List<Pair<String, Int>>, enabled: List<SpyWord>, rng: Random): SpyGame {
        require(error(s, enabled.size) == null)
        val w = enabled.random(rng)
        return SpyGame(id).apply {
            (0 until s.players).forEach { i ->
                val p = names.getOrNull(i)
                players += p?.first?.takeIf { it.isNotBlank() } ?: "بازیکن ${PersianText.digits(i + 1)}"
                avatars += p?.second ?: (i * 5 + 2) % Emblems.COUNT
            }
            named = names.isNotEmpty()
            spies += (0 until s.players).shuffled(rng).take(s.spies)
            word = w.text
            category = w.category
            minutes = s.minutes
            remainingMs = s.minutes * 60_000L
        }
    }

    /** What the player at [i] sees after touching his card. */
    fun cardText(g: SpyGame, i: Int): String = if (g.isSpy(i)) "نقش شما: جاسوس" else "کلمهٔ مخفی: ${g.word}"

    /** Shows the current player's card. */
    fun reveal(g: SpyGame) { if (!g.allViewed) g.revealed = true }

    /** Covers the card again and passes the device to the next player. */
    fun cover(g: SpyGame) {
        if (!g.revealed) return
        g.revealed = false
        g.revealIndex++
    }

    fun end(g: SpyGame, outcome: SpyOutcome) {
        g.outcome = outcome
        g.finished = true
        g.updatedAt = System.currentTimeMillis()
    }

    /** Built-in Persian words of the game. */
    fun defaults(input: InputStream): List<SpyWord> {
        val o = JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
        val out = ArrayList<SpyWord>()
        SpyCategory.values().filter { it != SpyCategory.USER }.forEachIndexed { ci, c ->
            val a = o.optJSONArray(c.key) ?: return@forEachIndexed
            for (i in 0 until a.length()) out += SpyWord((ci + 1) * 1000 + i, a.optString(i), c)
        }
        return out
    }

    val rules = listOf(
        "معرفی: در این بازی یک یا چند نفر به‌عنوان جاسوس انتخاب می‌شوند و بقیهٔ بازیکنان باید آن‌ها را شناسایی کنند.",
        "۱. تعداد بازیکنان و زمان بازی مشخص می‌شود.",
        "۲. یک کلمه از دسته‌های انتخاب‌شده مثل مکان، شغل، غذا یا حیوان به‌صورت تصادفی انتخاب می‌شود و به همهٔ بازیکنان به‌جز جاسوس نمایش داده می‌شود.",
        "۳. بازیکنان به‌ترتیب دستگاه را به نفر بعد می‌دهند و هر نفر نقش یا کلمهٔ مخفی خود را مشاهده می‌کند.",
        "۴. بازی با پرسیدن سؤال بین بازیکنان آغاز می‌شود. در شروع، هر بازیکن از نفر بعدی دربارهٔ کلمهٔ مخفی سؤال می‌پرسد.",
        "۵. سؤال‌ها باید طوری پرسیده شوند که جاسوس نتواند به‌راحتی کلمهٔ مخفی را حدس بزند.",
        "۶. اگر جاسوس درست شناسایی شود، بازیکنان عادی برنده‌اند. اگر جاسوس کلمه را درست حدس بزند یا تا پایان شناسایی نشود، جاسوس برنده است.",
        "در پایان بازی، اگر جاسوس خودش را معرفی کند و کلمهٔ مخفی را درست حدس بزند، برنده می‌شود.",
        "نکته: در شروع بازی، یک کلمه به‌صورت تصادفی از میان کلمات انتخاب‌شده انتخاب می‌شود."
    )
}

object SpyCodec {
    fun encode(g: SpyGame): JSONObject = JSONObject().apply {
        put("id", g.id); put("players", JSONArray(g.players)); put("avatars", JSONArray(g.avatars)); put("spies", JSONArray(g.spies.toList()))
        put("word", g.word); put("cat", g.category.name); put("min", g.minutes); put("ri", g.revealIndex); put("rv", g.revealed)
        put("rem", g.remainingMs); put("fin", g.finished); put("named", g.named); g.outcome?.let { put("out", it.name) }; put("created", g.createdAt); put("updated", g.updatedAt)
    }

    fun decode(o: JSONObject): SpyGame = SpyGame(o.optLong("id")).apply {
        o.optJSONArray("players")?.let { a -> for (i in 0 until a.length()) players += a.optString(i) }
        o.optJSONArray("avatars")?.let { a -> for (i in 0 until a.length()) avatars += a.optInt(i) }
        o.optJSONArray("spies")?.let { a -> for (i in 0 until a.length()) spies += a.optInt(i) }
        word = o.optString("word"); category = SpyCategory.of(o.optString("cat")); minutes = o.optInt("min", 5)
        revealIndex = o.optInt("ri"); revealed = o.optBoolean("rv"); remainingMs = o.optLong("rem")
        finished = o.optBoolean("fin"); named = o.optBoolean("named"); outcome = SpyOutcome.of(o.optString("out")); createdAt = o.optLong("created"); updatedAt = o.optLong("updated", createdAt)
    }

    fun encodeWords(list: List<SpyWord>): String = JSONArray(list.map { JSONObject().put("i", it.id).put("t", it.text).put("c", it.category.name) }).toString()

    fun decodeWords(raw: String?): List<SpyWord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            JSONArray(raw).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> SpyWord(o.optInt("i"), o.optString("t"), SpyCategory.of(o.optString("c")), true) } } }
        }.getOrDefault(emptyList())
    }
}

/** Settings, word selection, the players' own words and the games of «جاسوس». */
class SpyRepository(private val kv: KeyValueStore) {
    fun settings(): SpySettings = kv.get(KEY_SETTINGS)?.let { raw ->
        runCatching { JSONObject(raw).let { SpySettings(it.optInt("p", 6), it.optInt("s", 1), it.optInt("m", 5)) } }.getOrNull()
    } ?: SpySettings()

    fun saveSettings(s: SpySettings) = kv.write(mapOf(KEY_SETTINGS to JSONObject().put("p", s.players).put("s", s.spies).put("m", s.minutes).toString()))

    /** Ids of words switched off (every word is on by default). */
    fun disabled(): MutableSet<Int> = kv.get(KEY_DISABLED)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toHashSet() ?: HashSet()

    fun saveDisabled(s: Set<Int>) = kv.write(mapOf(KEY_DISABLED to s.joinToString(",")))

    fun userWords(): List<SpyWord> = SpyCodec.decodeWords(kv.get(KEY_USER_WORDS))

    fun saveUserWords(list: List<SpyWord>) = kv.write(mapOf(KEY_USER_WORDS to SpyCodec.encodeWords(list)))

    fun nextUserWordId(): Int = (userWords().maxOfOrNull { it.id } ?: USER_ID_BASE).coerceAtLeast(USER_ID_BASE) + 1

    fun index(): List<Long> = kv.get(KEY_INDEX)?.let { raw -> runCatching { JSONArray(raw).let { a -> List(a.length()) { a.optLong(it) } } }.getOrNull() } ?: emptyList()

    fun game(id: Long): SpyGame? = kv.get(KEY_GAME + id)?.let { runCatching { SpyCodec.decode(JSONObject(it)) }.getOrNull() }

    fun games(): List<SpyGame> = index().mapNotNull { game(it) }.sortedByDescending { it.updatedAt }

    fun save(g: SpyGame) {
        g.updatedAt = System.currentTimeMillis()
        val ids = listOf(g.id) + index().filterNot { it == g.id }
        kv.write(mapOf(KEY_GAME + g.id to SpyCodec.encode(g).toString(), KEY_INDEX to JSONArray(ids).toString()))
    }

    fun delete(id: Long) = kv.write(mapOf(KEY_GAME + id to null, KEY_INDEX to JSONArray(index().filterNot { it == id }).toString()))

    fun latestUnfinished(): SpyGame? = games().firstOrNull { !it.finished }

    companion object {
        const val KEY_SETTINGS = "spy_settings"
        const val KEY_DISABLED = "spy_disabled"
        const val KEY_USER_WORDS = "spy_user_words"
        const val KEY_INDEX = "spy_index"
        const val KEY_GAME = "spy_game_"
        const val USER_ID_BASE = 100_000
    }
}
