package com.meysam.divanemtiaz

import org.json.JSONArray
import org.json.JSONObject

/** How a player left the game (source app's DeathType). */
object MafiaDeath {
    const val NONE = ""
    const val NIGHT = "night"
    const val VOTE = "vote"
    const val DAY_SHOT = "day_shot"
    const val CARD = "card"
    const val DISCIPLINE = "discipline"
    const val POISON = "poison"
    const val EXECUTE = "execute"
    const val SHAKE_HAND = "shake_hand"
    const val LOTTERY = "lottery"

    fun title(type: String): String = when (type) {
        NIGHT -> "کشته در شب"
        VOTE -> "خروج با رأی"
        DAY_SHOT -> "خروج در روز"
        CARD -> "کارت حرکت آخر"
        DISCIPLINE -> "خروج انضباطی"
        POISON -> "زهر"
        EXECUTE -> "سلاخی"
        SHAKE_HAND -> "دست دادن"
        LOTTERY -> "قرعهٔ مرگ"
        else -> "خارج از بازی"
    }
}

class MafiaPlayer(val id: Int, var name: String, var avatar: Int) {
    var role: MafiaRole? = null
    var alive: Boolean = true
    /** The player has looked at his role card during distribution. */
    var seen: Boolean = false
    /** Role publicly announced (inquiry, show-identity card…). */
    var revealed: Boolean = false
    var outNight: Int = -1
    var deathType: String = MafiaDeath.NONE
    /** Remaining uses of each ability, keyed by the role option label (e.g. «تعداد نجات کل»). */
    val counters: MutableMap<String, Int> = HashMap()
    var armor: Int = 0
    /** Small persistent markers: «smith», «taken», «talisman», «web», «redCarpet», «greenMile», «handcuff», «vertigo»… */
    val flags: MutableSet<String> = HashSet()
    /** Numeric markers such as the poison night or infection night. */
    val marks: MutableMap<String, Int> = HashMap()

    fun counter(key: String): Int = counters[key] ?: 0
    fun has(flag: String): Boolean = flag in flags

    val side: MafiaSide? get() = if (has("converted")) MafiaSide.MAFIA else role?.side
}

/** What one night job chose. */
data class NightRecord(
    val key: String,
    val targets: List<Int> = emptyList(),
    val choice: String = "",
    val guess: MafiaRole? = null,
    val number: Int = 0,
    val text: String = "",
    /** What the narrator was shown (inquiry answer etc.), kept for the night report and the investigator. */
    val result: String = "",
    /** Done but without effect (vertigo, decoy for an absent role, cancelled by the sleepwalker). */
    val void: Boolean = false
)

data class MafiaLog(val phase: String, val text: String)

data class MafiaCardState(val card: MafiaLastCard, var picked: Boolean = false, var pending: Boolean = false, var holder: Int = -1, var targets: List<Int> = emptyList())

/** Everything decided during the current day; cleared at nightfall. */
class MafiaDayState {
    var speakingDone = false
    val firstVotes: MutableMap<Int, Int> = HashMap()
    var firstVoteDone = false
    val defenders: MutableList<Int> = ArrayList()
    var defenceDone = false
    val secondVotes: MutableMap<Int, Int> = HashMap()
    var secondVoteDone = false
    var mayorDone = false
    var mayorText = ""
    var resultDone = false
    val exited: MutableList<Int> = ArrayList()
    val done: MutableSet<String> = HashSet()
}

class MafiaGame(val id: Long) {
    val players: MutableList<MafiaPlayer> = ArrayList()
    /** Chosen roles with their counts (only CITIZEN and MAFIA can exceed one). */
    val roleCounts: MutableMap<MafiaRole, Int> = LinkedHashMap()
    /** Per-role option values by label, as chosen at setup. */
    val options: MutableMap<MafiaRole, MutableMap<String, String>> = HashMap()
    val deck: MutableList<MafiaCardState> = ArrayList()
    var night: Int = 0
    var isDay: Boolean = false
    var started: Boolean = false
    var introDone: Boolean = false
    val records: MutableMap<String, NightRecord> = LinkedHashMap()
    var day: MafiaDayState = MafiaDayState()
    val log: MutableList<MafiaLog> = ArrayList()
    var lastReport: List<String> = emptyList()
    var lastDead: List<Int> = emptyList()
    var note: String = ""
    var queriesUsed: Int = 0
    var finished: Boolean = false
    var winner: String = ""
    /** Game-wide state: «nostradamusSide», «voodooWord», «bombCode», «snowball», «extraShot»… */
    val extra: MutableMap<String, String> = HashMap()
    var updatedAt: Long = id
    var elapsedMs: Long = 0L
    /** Full snapshots taken at each day/night switch for «بازگشت به گذشته». */
    val snapshots: MutableList<Pair<String, String>> = ArrayList()

    fun player(id: Int): MafiaPlayer? = players.firstOrNull { it.id == id }
    val alive: List<MafiaPlayer> get() = players.filter { it.alive }
    val out: List<MafiaPlayer> get() = players.filter { !it.alive }
    fun holders(role: MafiaRole): List<MafiaPlayer> = players.filter { it.role == role }
    fun aliveHolder(role: MafiaRole): MafiaPlayer? = players.firstOrNull { it.role == role && it.alive }
    fun inGame(role: MafiaRole): Boolean = players.any { it.role == role }

    fun option(role: MafiaRole, label: String): String? =
        options[role]?.get(label) ?: MafiaRoleOptions.of(role).firstOrNull { it.label == label }?.default

    /** Numeric option value; «∞» counts as 1000, «بله» as 1 and «خیر» as 0. */
    fun optionInt(role: MafiaRole, label: String, fallback: Int = 0): Int = when (val v = option(role, label)) {
        null -> fallback
        "∞" -> 1000
        "بله" -> 1
        "خیر" -> 0
        else -> v.toIntOrNull() ?: fallback
    }

    val phaseTitle: String get() = if (isDay) "روز ${MafiaText.ordinal(night)}" else "شب ${MafiaText.ordinal(night)}"
}

object MafiaText {
    private val ordinals = listOf("معارفه", "اول", "دوم", "سوم", "چهارم", "پنجم", "ششم", "هفتم", "هشتم", "نهم", "دهم",
        "یازدهم", "دوازدهم", "سیزدهم", "چهاردهم", "پانزدهم", "شانزدهم", "هفدهم", "هیجدهم", "نوزدهم", "بیستم")

    fun ordinal(n: Int): String = ordinals.getOrElse(n) { PersianText.digits(n) + "ام" }
}

/** Narrator settings (source app's «تنظیمات برنامه») plus sound cues and role pictures. */
data class MafiaSettings(
    val speakTime: Int = 60,
    val challengeTime: Int = 30,
    val defenceTime: Int = 60,
    val queryCount: Int = 2,
    /** true = «۵۰ درصد گرد شده رو به بالا» (floor(N/2)+1), false = rounded down (floor(N/2)). */
    val voteEdgeUp: Boolean = true,
    val capoGun: Boolean = false,
    val selfKill: Boolean = true,
    val keepScreenOn: Boolean = true,
    val soundEffects: Boolean = true,
    val effectVolume: Int = 100,
    val musicVolume: Int = 40,
    val vibrate: Boolean = true,
    /** Play a step's cue as soon as the step opens (otherwise only on the play button). */
    val autoPlay: Boolean = true,
    val nightMusic: SoundChoice = SoundChoice(),
    val sounds: Map<String, SoundChoice> = emptyMap(),
    /** Narrator's own picture for a role (path inside the app's storage). */
    val rolePictures: Map<String, String> = emptyMap()
)

/** Every narrator moment that can carry a sound: general cues and a wake / sleep / done cue per role. */
object MafiaCues {
    const val NIGHT_START = "night_start"
    const val DAY_START = "day_start"
    const val INTRO_START = "intro_start"
    const val INTRO_END = "intro_end"
    const val MAFIA_WAKE = "mafia_wake"
    const val MAFIA_SLEEP = "mafia_sleep"
    const val ALL_SLEEP = "all_sleep"
    const val SPEAKING = "speaking"
    const val VOTE_FIRST = "vote_first"
    const val DEFENCE = "defence"
    const val VOTE_SECOND = "vote_second"
    const val VOTE_RESULT = "vote_result"
    const val LAST_CARD = "last_card"
    const val DEAD_REPORT = "dead_report"
    const val TIMER_TICK = "timer_tick"
    const val TIMER_END = "timer_end"
    const val WIN_CITY = "win_city"
    const val WIN_MAFIA = "win_mafia"
    const val WIN_INDEPENDENT = "win_independent"

    val general: List<Pair<String, String>> = listOf(
        NIGHT_START to "شب می‌شود",
        DAY_START to "روز می‌شود",
        INTRO_START to "شروع شب معارفه",
        INTRO_END to "پایان شب معارفه",
        MAFIA_WAKE to "گروه مافیا بیدار شوند",
        MAFIA_SLEEP to "گروه مافیا بخوابند",
        ALL_SLEEP to "همه بخوابند",
        DEAD_REPORT to "اعلام کشته‌های شب",
        SPEAKING to "شروع صحبت",
        VOTE_FIRST to "رأی‌گیری دور اول",
        DEFENCE to "دفاعیه",
        VOTE_SECOND to "رأی‌گیری دور دوم",
        VOTE_RESULT to "نتیجهٔ رأی‌گیری",
        LAST_CARD to "کارت حرکت آخر",
        TIMER_TICK to "تیک ده ثانیهٔ آخر",
        TIMER_END to "پایان زمان",
        WIN_CITY to "برد شهروندان",
        WIN_MAFIA to "برد مافیا",
        WIN_INDEPENDENT to "برد مستقل"
    )

    fun wake(role: MafiaRole) = "wake_${role.name}"
    fun sleep(role: MafiaRole) = "sleep_${role.name}"
    fun done(role: MafiaRole) = "done_${role.name}"

    fun defaults(): Map<String, SoundChoice> {
        val m = HashMap<String, SoundChoice>()
        m[NIGHT_START] = SoundChoice(SoundSource.BUILTIN, "mafia_night_sound")
        m[DAY_START] = SoundChoice(SoundSource.BUILTIN, "mafia_morning_sound")
        m[TIMER_TICK] = SoundChoice(SoundSource.BUILTIN, "mafia_timer_one_tik")
        m[TIMER_END] = SoundChoice(SoundSource.BUILTIN, "mafia_end_timer")
        m[DEAD_REPORT] = SoundChoice(SoundSource.BUILTIN, "mafia_heart_sound")
        m[LAST_CARD] = SoundChoice(SoundSource.BUILTIN, "mafia_typing_sound")
        MafiaRole.values().forEach { r -> if (r.nightSound.isNotBlank()) m[done(r)] = SoundChoice(SoundSource.BUILTIN, r.nightSound) }
        m[done(MafiaRole.JOKER)] = SoundChoice(SoundSource.BUILTIN, "mafia_joker_laugh")
        return m
    }

    fun title(key: String): String {
        general.firstOrNull { it.first == key }?.let { return it.second }
        val role = MafiaRole.values().firstOrNull { key.endsWith("_" + it.name) && key.length == key.indexOf('_') + 1 + it.name.length }
            ?: return key
        return when {
            key.startsWith("wake_") -> "${role.faName} بیدار شود"
            key.startsWith("sleep_") -> "${role.faName} بخوابد"
            else -> "پایان کار ${role.faName}"
        }
    }
}

fun MafiaSettings.cue(key: String): SoundChoice? = sounds[key] ?: MafiaCues.defaults()[key]

object MafiaCodec {
    private fun choice(c: SoundChoice) = JSONObject().put("s", c.source).put("b", c.builtin).put("p", c.path).put("l", c.label)
    private fun choice(o: JSONObject?) = if (o == null) SoundChoice() else SoundChoice(o.optInt("s"), o.optString("b"), o.optString("p"), o.optString("l"))

    fun encodeSettings(s: MafiaSettings): JSONObject = JSONObject().apply {
        put("speak", s.speakTime); put("challenge", s.challengeTime); put("defence", s.defenceTime); put("query", s.queryCount)
        put("edgeUp", s.voteEdgeUp); put("capo", s.capoGun); put("selfKill", s.selfKill); put("screen", s.keepScreenOn)
        put("fx", s.soundEffects); put("fxVol", s.effectVolume); put("musicVol", s.musicVolume); put("vibrate", s.vibrate)
        put("autoPlay", s.autoPlay); put("music", choice(s.nightMusic))
        put("sounds", JSONObject().apply { s.sounds.forEach { (k, v) -> put(k, choice(v)) } })
        put("pictures", JSONObject().apply { s.rolePictures.forEach { (k, v) -> put(k, v) } })
    }

    fun decodeSettings(o: JSONObject): MafiaSettings {
        val d = MafiaSettings()
        val sounds = HashMap<String, SoundChoice>()
        o.optJSONObject("sounds")?.let { j -> j.keys().forEach { k -> sounds[k] = choice(j.optJSONObject(k)) } }
        val pics = HashMap<String, String>()
        o.optJSONObject("pictures")?.let { j -> j.keys().forEach { k -> pics[k] = j.optString(k) } }
        return MafiaSettings(
            o.optInt("speak", d.speakTime), o.optInt("challenge", d.challengeTime), o.optInt("defence", d.defenceTime),
            o.optInt("query", d.queryCount), o.optBoolean("edgeUp", d.voteEdgeUp), o.optBoolean("capo", d.capoGun),
            o.optBoolean("selfKill", d.selfKill), o.optBoolean("screen", d.keepScreenOn), o.optBoolean("fx", d.soundEffects),
            o.optInt("fxVol", d.effectVolume), o.optInt("musicVol", d.musicVolume), o.optBoolean("vibrate", d.vibrate),
            o.optBoolean("autoPlay", d.autoPlay), choice(o.optJSONObject("music")), sounds, pics
        )
    }

    private fun intMap(m: Map<String, Int>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }
    private fun intMap(o: JSONObject?): MutableMap<String, Int> = HashMap<String, Int>().apply { o?.keys()?.forEach { k -> put(k, o.optInt(k)) } }
    private fun ints(l: Collection<Int>) = JSONArray().apply { l.forEach { put(it) } }
    private fun ints(a: JSONArray?): List<Int> = if (a == null) emptyList() else List(a.length()) { a.optInt(it) }
    private fun intIntMap(m: Map<Int, Int>) = JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v) } }
    private fun intIntMap(o: JSONObject?): Map<Int, Int> = HashMap<Int, Int>().apply { o?.keys()?.forEach { k -> k.toIntOrNull()?.let { put(it, o.optInt(k)) } } }
    private fun role(name: String?): MafiaRole? = MafiaRole.values().firstOrNull { it.name == name }

    fun encodeGame(g: MafiaGame, withSnapshots: Boolean = true): JSONObject = JSONObject().apply {
        put("id", g.id)
        put("players", JSONArray().apply {
            g.players.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("avatar", p.avatar); put("role", p.role?.name ?: "")
                    put("alive", p.alive); put("seen", p.seen); put("revealed", p.revealed); put("outNight", p.outNight)
                    put("death", p.deathType); put("counters", intMap(p.counters)); put("armor", p.armor)
                    put("flags", JSONArray(p.flags.toList())); put("marks", intMap(p.marks))
                })
            }
        })
        put("roles", JSONObject().apply { g.roleCounts.forEach { (r, c) -> put(r.name, c) } })
        put("options", JSONObject().apply { g.options.forEach { (r, m) -> put(r.name, JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }) } })
        put("deck", JSONArray().apply {
            g.deck.forEach { c -> put(JSONObject().put("card", c.card.name).put("picked", c.picked).put("pending", c.pending).put("holder", c.holder).put("targets", ints(c.targets))) }
        })
        put("night", g.night); put("isDay", g.isDay); put("started", g.started); put("introDone", g.introDone)
        put("records", JSONArray().apply {
            g.records.values.forEach { r ->
                put(JSONObject().put("key", r.key).put("targets", ints(r.targets)).put("choice", r.choice).put("guess", r.guess?.name ?: "")
                    .put("number", r.number).put("text", r.text).put("result", r.result).put("void", r.void))
            }
        })
        put("day", JSONObject().apply {
            val d = g.day
            put("speaking", d.speakingDone); put("v1", intIntMap(d.firstVotes)); put("v1done", d.firstVoteDone); put("defenders", ints(d.defenders))
            put("defence", d.defenceDone); put("v2", intIntMap(d.secondVotes)); put("v2done", d.secondVoteDone); put("mayor", d.mayorDone)
            put("mayorText", d.mayorText); put("result", d.resultDone); put("exited", ints(d.exited)); put("done", JSONArray(d.done.toList()))
        })
        put("log", JSONArray().apply { g.log.forEach { put(JSONObject().put("p", it.phase).put("t", it.text)) } })
        put("report", JSONArray(g.lastReport)); put("lastDead", ints(g.lastDead)); put("note", g.note); put("queries", g.queriesUsed)
        put("finished", g.finished); put("winner", g.winner)
        put("extra", JSONObject().apply { g.extra.forEach { (k, v) -> put(k, v) } })
        put("updatedAt", g.updatedAt); put("elapsed", g.elapsedMs)
        if (withSnapshots) put("snapshots", JSONArray().apply { g.snapshots.forEach { (label, json) -> put(JSONObject().put("label", label).put("json", json)) } })
    }

    fun decodeGame(o: JSONObject): MafiaGame {
        val g = MafiaGame(o.optLong("id"))
        o.optJSONArray("players")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                g.players += MafiaPlayer(j.optInt("id"), j.optString("name"), j.optInt("avatar")).apply {
                    role = role(j.optString("role")); alive = j.optBoolean("alive", true); seen = j.optBoolean("seen")
                    revealed = j.optBoolean("revealed"); outNight = j.optInt("outNight", -1); deathType = j.optString("death")
                    counters.putAll(intMap(j.optJSONObject("counters"))); armor = j.optInt("armor")
                    j.optJSONArray("flags")?.let { f -> for (k in 0 until f.length()) flags += f.optString(k) }
                    marks.putAll(intMap(j.optJSONObject("marks")))
                }
            }
        }
        o.optJSONObject("roles")?.let { j -> j.keys().forEach { k -> role(k)?.let { g.roleCounts[it] = j.optInt(k) } } }
        o.optJSONObject("options")?.let { j ->
            j.keys().forEach { k -> role(k)?.let { r -> val m = HashMap<String, String>(); j.optJSONObject(k)?.let { x -> x.keys().forEach { l -> m[l] = x.optString(l) } }; g.options[r] = m } }
        }
        o.optJSONArray("deck")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                val card = MafiaLastCard.values().firstOrNull { it.name == j.optString("card") } ?: continue
                g.deck += MafiaCardState(card, j.optBoolean("picked"), j.optBoolean("pending"), j.optInt("holder", -1), ints(j.optJSONArray("targets")))
            }
        }
        g.night = o.optInt("night"); g.isDay = o.optBoolean("isDay"); g.started = o.optBoolean("started"); g.introDone = o.optBoolean("introDone")
        o.optJSONArray("records")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                val r = NightRecord(j.optString("key"), ints(j.optJSONArray("targets")), j.optString("choice"), role(j.optString("guess")),
                    j.optInt("number"), j.optString("text"), j.optString("result"), j.optBoolean("void"))
                g.records[r.key] = r
            }
        }
        o.optJSONObject("day")?.let { j ->
            val d = MafiaDayState()
            d.speakingDone = j.optBoolean("speaking"); d.firstVotes.putAll(intIntMap(j.optJSONObject("v1"))); d.firstVoteDone = j.optBoolean("v1done")
            d.defenders += ints(j.optJSONArray("defenders")); d.defenceDone = j.optBoolean("defence"); d.secondVotes.putAll(intIntMap(j.optJSONObject("v2")))
            d.secondVoteDone = j.optBoolean("v2done"); d.mayorDone = j.optBoolean("mayor"); d.mayorText = j.optString("mayorText")
            d.resultDone = j.optBoolean("result"); d.exited += ints(j.optJSONArray("exited"))
            j.optJSONArray("done")?.let { f -> for (k in 0 until f.length()) d.done += f.optString(k) }
            g.day = d
        }
        o.optJSONArray("log")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { g.log += MafiaLog(it.optString("p"), it.optString("t")) } }
        o.optJSONArray("report")?.let { a -> g.lastReport = List(a.length()) { a.optString(it) } }
        g.lastDead = ints(o.optJSONArray("lastDead")); g.note = o.optString("note"); g.queriesUsed = o.optInt("queries")
        g.finished = o.optBoolean("finished"); g.winner = o.optString("winner")
        o.optJSONObject("extra")?.let { j -> j.keys().forEach { k -> g.extra[k] = j.optString(k) } }
        g.updatedAt = o.optLong("updatedAt", g.id); g.elapsedMs = o.optLong("elapsed")
        o.optJSONArray("snapshots")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { g.snapshots += it.optString("label") to it.optString("json") } }
        return g
    }
}

/** Mafia games and narrator settings, stored beside the score games in the same key-value store. */
class MafiaRepository(private val kv: KeyValueStore) {
    fun settings(): MafiaSettings = kv.get(KEY_SETTINGS)?.let { runCatching { MafiaCodec.decodeSettings(JSONObject(it)) }.getOrNull() } ?: MafiaSettings()

    fun saveSettings(s: MafiaSettings) = kv.write(mapOf(KEY_SETTINGS to MafiaCodec.encodeSettings(s).toString()))

    fun index(): List<Long> = kv.get(KEY_INDEX)?.let { raw -> runCatching { JSONArray(raw).let { a -> List(a.length()) { a.optLong(it) } } }.getOrNull() } ?: emptyList()

    fun game(id: Long): MafiaGame? = kv.get(KEY_GAME + id)?.let { runCatching { MafiaCodec.decodeGame(JSONObject(it)) }.getOrNull() }

    fun games(): List<MafiaGame> = index().mapNotNull { game(it) }.sortedByDescending { it.updatedAt }

    fun save(g: MafiaGame) {
        g.updatedAt = System.currentTimeMillis()
        val ids = listOf(g.id) + index().filterNot { it == g.id }
        kv.write(mapOf(KEY_GAME + g.id to MafiaCodec.encodeGame(g).toString(), KEY_INDEX to JSONArray(ids).toString()))
    }

    fun delete(id: Long) = kv.write(mapOf(KEY_GAME + id to null, KEY_INDEX to JSONArray(index().filterNot { it == id }).toString()))

    /** Saved player line-ups and role selections («گروه بازی»). */
    fun groups(): JSONArray = kv.get(KEY_GROUPS)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()

    fun saveGroups(a: JSONArray) = kv.write(mapOf(KEY_GROUPS to a.toString()))

    companion object {
        const val KEY_SETTINGS = "mafia_settings"
        const val KEY_INDEX = "mafia_index"
        const val KEY_GAME = "mafia_game_"
        const val KEY_GROUPS = "mafia_groups"
    }
}
