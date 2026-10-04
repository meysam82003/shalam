package com.meysam.divanemtiaz

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

interface KeyValueStore {
    fun get(key: String): String?

    /** Applies every change in one transaction; a null value removes the key. */
    fun write(changes: Map<String, String?>)
}

class PrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun write(changes: Map<String, String?>) {
        val editor = prefs.edit()
        changes.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putString(key, value) }
        editor.apply()
    }
}

/** Raw data written by earlier versions; it is only read, never modified or deleted. */
data class LegacyData(val v1History: String? = null, val v2History: String? = null, val v2Settings: String? = null) {
    companion object {
        val NONE = LegacyData()
    }
}

class GameRepository(private val kv: KeyValueStore, private val legacy: () -> LegacyData = { LegacyData.NONE }) {
    private var migrated = false

    @Synchronized
    fun migrateIfNeeded() {
        if (migrated) return
        migrated = true
        if ((kv.get(KEY_SCHEMA)?.toIntOrNull() ?: 0) >= SCHEMA) return
        val data = legacy()
        val changes = linkedMapOf<String, String?>()
        val settings = data.v2Settings?.let { runCatching { LegacyCodec.settingsFromV2(JSONObject(it)) }.getOrNull() }
            ?: AppSettings()
        if (kv.get(KEY_SETTINGS) == null) changes[KEY_SETTINGS] = GameCodec.encodeSettings(settings).toString()
        val sessions = mutableListOf<GameSession>()
        data.v2History?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { item ->
                        runCatching { LegacyCodec.sessionFromV2(item, settings) }.getOrNull()?.let(sessions::add)
                    }
                }
            }
        }
        data.v1History?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { item ->
                        runCatching { LegacyCodec.sessionFromV1(item, settings) }.getOrNull()?.let { session ->
                            if (sessions.none { it.id == session.id }) sessions.add(session)
                        }
                    }
                }
            }
        }
        val existing = index().toMutableList()
        sessions.filter { kv.get(gameKey(it.id)) == null }.forEach { session ->
            changes[gameKey(session.id)] = GameCodec.encodeSession(session).toString()
            existing.add(session.id)
        }
        val ordered = existing.distinct().mapNotNull { id ->
            val updated = sessions.firstOrNull { it.id == id }?.updatedAt
                ?: kv.get(gameKey(id))?.let { runCatching { JSONObject(it).optLong("updatedAt", id) }.getOrNull() }
            updated?.let { id to it }
        }.sortedByDescending { it.second }.map { it.first }
        changes[KEY_INDEX] = JSONArray(ordered).toString()
        changes[KEY_SCHEMA] = SCHEMA.toString()
        kv.write(changes)
    }

    fun index(): List<Long> = kv.get(KEY_INDEX)?.let { raw ->
        runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getLong(it) }
        }.getOrNull()
    } ?: emptyList()

    fun session(id: Long): GameSession? {
        migrateIfNeeded()
        return kv.get(gameKey(id))?.let { raw -> runCatching { GameCodec.decodeSession(JSONObject(raw)) }.getOrNull() }
    }

    fun sessions(): List<GameSession> {
        migrateIfNeeded()
        return index().mapNotNull { session(it) }.sortedByDescending { it.updatedAt }
    }

    fun latestUnfinished(): GameSession? = sessions().firstOrNull { !it.finished && it.rounds.isNotEmpty() }

    fun newSessionId(): Long {
        migrateIfNeeded()
        var id = System.currentTimeMillis()
        val used = index().toSet()
        while (id in used) id++
        return id
    }

    fun save(session: GameSession) {
        migrateIfNeeded()
        val ids = index().filterNot { it == session.id }
        kv.write(
            mapOf(
                gameKey(session.id) to GameCodec.encodeSession(session).toString(),
                KEY_INDEX to JSONArray(listOf(session.id) + ids).toString()
            )
        )
    }

    fun delete(id: Long) {
        migrateIfNeeded()
        kv.write(mapOf(gameKey(id) to null, KEY_INDEX to JSONArray(index().filterNot { it == id }).toString()))
    }

    fun clearHistory() {
        migrateIfNeeded()
        val changes = linkedMapOf<String, String?>()
        index().forEach { changes[gameKey(it)] = null }
        changes[KEY_INDEX] = JSONArray().toString()
        kv.write(changes)
    }

    fun settings(): AppSettings {
        migrateIfNeeded()
        return kv.get(KEY_SETTINGS)?.let { raw -> runCatching { GameCodec.decodeSettings(JSONObject(raw)) }.getOrNull() }
            ?: AppSettings()
    }

    fun saveSettings(settings: AppSettings) {
        kv.write(mapOf(KEY_SETTINGS to GameCodec.encodeSettings(settings).toString()))
    }

    fun roster(): List<RosterEntry> = kv.get(KEY_ROSTER)?.let { raw ->
        runCatching { GameCodec.decodeRoster(JSONArray(raw)) }.getOrNull()
    } ?: emptyList()

    fun saveRoster(entries: List<RosterEntry>) {
        kv.write(mapOf(KEY_ROSTER to GameCodec.encodeRoster(entries).toString()))
    }

    companion object {
        const val SCHEMA = 3
        const val KEY_SCHEMA = "schema"
        const val KEY_INDEX = "index"
        const val KEY_SETTINGS = "settings"
        const val KEY_ROSTER = "roster"
        fun gameKey(id: Long) = "game_$id"
    }
}

object GameStore {
    private const val PREFS_V3 = "divan_emtiaz_v3"
    private const val PREFS_V2 = "divan_emtiaz_v2"
    private const val PREFS_V1 = "divan_prefs"

    @Volatile private var instance: GameRepository? = null

    fun open(context: Context): GameRepository = instance ?: synchronized(this) {
        instance ?: run {
            val app = context.applicationContext ?: context
            GameRepository(PrefsKeyValueStore(app.getSharedPreferences(PREFS_V3, Context.MODE_PRIVATE))) {
                val v2 = app.getSharedPreferences(PREFS_V2, Context.MODE_PRIVATE)
                val v1 = app.getSharedPreferences(PREFS_V1, Context.MODE_PRIVATE)
                LegacyData(
                    v1History = v1.getString("history", null),
                    v2History = v2.getString("history_v2", null),
                    v2Settings = v2.getString("settings_v2", null)
                )
            }.also { instance = it }
        }
    }
}

object GameCodec {
    fun encodeSession(session: GameSession): JSONObject = JSONObject().apply {
        put("v", GameRepository.SCHEMA)
        put("id", session.id)
        put("game", session.game.key)
        put("finished", session.finished)
        put("updatedAt", session.updatedAt)
        put("elapsed", session.elapsedMs)
        session.endedAt?.let { put("endedAt", it) }
        put("label", session.label)
        put("sides", JSONArray().apply {
            session.sides.forEach { side -> put(JSONObject().put("name", side.name).put("avatar", side.avatar)) }
        })
        put("rules", encodeRules(session.rules))
        put("rounds", JSONArray().apply { session.rounds.forEach { put(encodeRound(it)) } })
        put("teamA", session.sides.getOrNull(0)?.name ?: "")
        put("teamB", session.sides.getOrNull(1)?.name ?: "")
        put("avatarA", session.sides.getOrNull(0)?.avatar ?: 0)
        put("avatarB", session.sides.getOrNull(1)?.avatar ?: 1)
        put("timestamp", session.updatedAt)
    }

    fun decodeSession(o: JSONObject): GameSession {
        val game = GameType.fromKey(o.optString("game"))
        val sidesJson = o.optJSONArray("sides")
        val sides = if (sidesJson != null && sidesJson.length() > 0) {
            List(sidesJson.length()) { i ->
                val s = sidesJson.optJSONObject(i) ?: JSONObject()
                Side(s.optString("name", "بازیکن ${i + 1}"), s.optInt("avatar", i))
            }
        } else {
            listOf(
                Side(o.optString("teamA", "تیم اول"), o.optInt("avatarA", 0)),
                Side(o.optString("teamB", "تیم دوم"), o.optInt("avatarB", 1))
            )
        }
        val roundsJson = o.optJSONArray("rounds") ?: JSONArray()
        val rounds = List(roundsJson.length()) { i -> decodeRound(roundsJson.optJSONObject(i) ?: JSONObject()) }
        val id = o.optLong("id", o.optLong("timestamp"))
        return GameSession(
            id = id,
            game = game,
            sides = sides,
            rounds = rounds,
            rules = o.optJSONObject("rules")?.let(::decodeRules) ?: GameRules(),
            finished = o.optBoolean("finished"),
            updatedAt = o.optLong("updatedAt", o.optLong("timestamp", id)),
            elapsedMs = o.optLong("elapsed", 0L),
            endedAt = if (o.has("endedAt")) o.optLong("endedAt") else null,
            label = o.optString("label", "")
        )
    }

    fun encodeRound(r: Round): JSONObject = JSONObject().apply {
        put("k", r.kind.key)
        put("s", ints(r.scores))
        if (r.note.isNotEmpty()) put("n", r.note)
        if (r.contractTeam >= 0) put("ct", r.contractTeam)
        if (r.bid != 0) put("bid", r.bid)
        if (r.taken != 0) put("tk", r.taken)
        if (r.suit != Suit.NONE) put("su", r.suit)
        if (r.double != DoubleChoice.AUTO) put("db", r.double)
        if (r.numbers.isNotEmpty()) put("num", ints(r.numbers))
        if (r.outcome >= 0) put("oc", r.outcome)
        if (r.raw.isNotEmpty()) put("raw", ints(r.raw))
        put("teamA", r.score(0))
        put("teamB", r.score(1))
    }

    fun decodeRound(o: JSONObject): Round {
        val scores = o.optJSONArray("s")?.let(::intList) ?: listOf(o.optInt("teamA"), o.optInt("teamB"))
        return Round(
            kind = RoundKind.fromKey(o.optString("k", RoundKind.FIXED.key)),
            scores = scores,
            note = o.optString("n", o.optString("note", "")),
            contractTeam = o.optInt("ct", -1),
            bid = o.optInt("bid", 0),
            taken = o.optInt("tk", 0),
            suit = o.optInt("su", Suit.NONE),
            double = o.optInt("db", DoubleChoice.AUTO),
            numbers = o.optJSONArray("num")?.let(::intList) ?: emptyList(),
            outcome = o.optInt("oc", -1),
            raw = o.optJSONArray("raw")?.let(::intList) ?: emptyList()
        )
    }

    fun encodeRules(rules: GameRules): JSONObject = JSONObject().apply {
        val s = rules.shalam
        put("shalam", JSONObject().apply {
            put("joker", s.joker)
            put("endPoint", s.endPoint)
            put("mode", encodeMode(s.mode))
            put("doubleType", s.doubleType)
            put("contractOnly", s.contractOnly)
            put("highLimitEnabled", s.highLimitEnabled)
            put("highLimit", s.highLimit)
            put("loserPointsAboveLimit", s.loserPointsAboveLimit)
            put("dealType", s.dealType)
        })
        put("menfi", JSONObject().apply {
            put("hands", rules.menfi.hands)
            put("hidden", rules.menfi.hidden)
            put("threeSuccess", rules.menfi.threeSuccess)
            put("threeFailure", rules.menfi.threeFailure)
            put("highWins", rules.menfi.highWins)
        })
        put("hezar", JSONObject().apply {
            put("target", rules.hezar.target)
            put("rounds", rules.hezar.rounds)
            put("zeroPenalty", rules.hezar.zeroPenalty)
        })
    }

    fun decodeRules(o: JSONObject): GameRules {
        val d = GameRules()
        val s = o.optJSONObject("shalam") ?: JSONObject()
        val joker = s.optBoolean("joker", d.shalam.joker)
        val mode = s.optJSONObject("mode")?.let { decodeMode(it, if (joker) ShalamModeRules.JOKER else ShalamModeRules.NO_JOKER) }
            ?: if (joker) ShalamModeRules.JOKER else ShalamModeRules.NO_JOKER
        val m = o.optJSONObject("menfi") ?: JSONObject()
        val h = o.optJSONObject("hezar") ?: JSONObject()
        return GameRules(
            shalam = ShalamRules(
                joker = joker,
                endPoint = s.optInt("endPoint", mode.defaultEndPoint),
                mode = mode,
                doubleType = s.optInt("doubleType", d.shalam.doubleType),
                contractOnly = s.optBoolean("contractOnly", d.shalam.contractOnly),
                highLimitEnabled = s.optBoolean("highLimitEnabled", d.shalam.highLimitEnabled),
                highLimit = s.optInt("highLimit", d.shalam.highLimit),
                loserPointsAboveLimit = s.optBoolean("loserPointsAboveLimit", d.shalam.loserPointsAboveLimit),
                dealType = s.optInt("dealType", d.shalam.dealType)
            ),
            menfi = MenfiRules(
                hands = m.optInt("hands", d.menfi.hands),
                hidden = m.optBoolean("hidden", d.menfi.hidden),
                threeSuccess = m.optInt("threeSuccess", d.menfi.threeSuccess),
                threeFailure = m.optInt("threeFailure", d.menfi.threeFailure),
                highWins = m.optBoolean("highWins", d.menfi.highWins)
            ),
            hezar = HezarRules(
                target = h.optInt("target", d.hezar.target),
                rounds = h.optInt("rounds", d.hezar.rounds),
                zeroPenalty = h.optInt("zeroPenalty", d.hezar.zeroPenalty)
            )
        )
    }

    fun encodeMode(m: ShalamModeRules): JSONObject = JSONObject().apply {
        put("maxPoints", m.maxPoints)
        put("collectAll", m.collectAll)
        put("collectAllCustom", m.collectAllCustom)
        put("doubleLimit", m.doubleLimit)
        put("maxBidIsShelem", m.maxBidIsShelem)
        put("shelemMode", m.shelemMode)
        put("shelemContractor", m.shelemContractor)
        put("shelemOpponent", m.shelemOpponent)
        put("endWithDiff", m.endWithDiff)
        put("endDiff", m.endDiff)
        put("doubleShelemMode", m.doubleShelemMode)
        put("doubleShelemWin", m.doubleShelemWin)
        put("doubleShelemLose", m.doubleShelemLose)
        put("defaultEndPoint", m.defaultEndPoint)
    }

    fun decodeMode(o: JSONObject, d: ShalamModeRules): ShalamModeRules = ShalamModeRules(
        maxPoints = o.optInt("maxPoints", d.maxPoints),
        collectAll = o.optInt("collectAll", d.collectAll),
        collectAllCustom = o.optInt("collectAllCustom", d.collectAllCustom),
        doubleLimit = o.optInt("doubleLimit", d.doubleLimit),
        maxBidIsShelem = o.optBoolean("maxBidIsShelem", d.maxBidIsShelem),
        shelemMode = o.optInt("shelemMode", d.shelemMode),
        shelemContractor = o.optInt("shelemContractor", d.shelemContractor),
        shelemOpponent = o.optInt("shelemOpponent", d.shelemOpponent),
        endWithDiff = o.optBoolean("endWithDiff", d.endWithDiff),
        endDiff = o.optInt("endDiff", d.endDiff),
        doubleShelemMode = o.optInt("doubleShelemMode", d.doubleShelemMode),
        doubleShelemWin = o.optInt("doubleShelemWin", d.doubleShelemWin),
        doubleShelemLose = o.optInt("doubleShelemLose", d.doubleShelemLose),
        defaultEndPoint = o.optInt("defaultEndPoint", d.defaultEndPoint)
    )

    fun encodeSettings(s: AppSettings): JSONObject = JSONObject().apply {
        put("v", GameRepository.SCHEMA)
        put("general", JSONObject().apply {
            put("haptic", s.general.haptic)
            put("keepScreenAwake", s.general.keepScreenAwake)
            put("persianDigits", s.general.persianDigits)
            put("largeText", s.general.largeText)
            put("defaultTeam1", s.general.defaultTeam1)
            put("defaultTeam2", s.general.defaultTeam2)
        })
        put("shalam", JSONObject().apply {
            put("dealType", s.shalam.dealType)
            put("doubleType", s.shalam.doubleType)
            put("contractOnly", s.shalam.contractOnly)
            put("highLimitEnabled", s.shalam.highLimitEnabled)
            put("highLimit", s.shalam.highLimit)
            put("loserPointsAboveLimit", s.shalam.loserPointsAboveLimit)
            put("keyboardInput", s.shalam.keyboardInput)
            put("defaultJoker", s.shalam.defaultJoker)
            put("noJoker", encodeMode(s.shalam.noJoker))
            put("joker", encodeMode(s.shalam.joker))
        })
        put("menfi", JSONObject().apply {
            put("hands", s.menfi.hands)
            put("hidden", s.menfi.hidden)
            put("threeSuccess", s.menfi.threeSuccess)
            put("threeFailure", s.menfi.threeFailure)
            put("highWins", s.menfi.highWins)
        })
        put("hezar", JSONObject().apply {
            put("target", s.hezar.target)
            put("rounds", s.hezar.rounds)
            put("zeroPenalty", s.hezar.zeroPenalty)
            put("players", s.hezar.players)
        })
    }

    fun decodeSettings(o: JSONObject): AppSettings {
        val d = AppSettings()
        val g = o.optJSONObject("general") ?: JSONObject()
        val s = o.optJSONObject("shalam") ?: JSONObject()
        val m = o.optJSONObject("menfi") ?: JSONObject()
        val h = o.optJSONObject("hezar") ?: JSONObject()
        return AppSettings(
            general = GeneralSettings(
                haptic = g.optBoolean("haptic", d.general.haptic),
                keepScreenAwake = g.optBoolean("keepScreenAwake", d.general.keepScreenAwake),
                persianDigits = g.optBoolean("persianDigits", d.general.persianDigits),
                largeText = g.optBoolean("largeText", d.general.largeText),
                defaultTeam1 = g.optString("defaultTeam1", d.general.defaultTeam1),
                defaultTeam2 = g.optString("defaultTeam2", d.general.defaultTeam2)
            ),
            shalam = ShalamSettings(
                dealType = s.optInt("dealType", d.shalam.dealType),
                doubleType = s.optInt("doubleType", d.shalam.doubleType),
                contractOnly = s.optBoolean("contractOnly", d.shalam.contractOnly),
                highLimitEnabled = s.optBoolean("highLimitEnabled", d.shalam.highLimitEnabled),
                highLimit = s.optInt("highLimit", d.shalam.highLimit),
                loserPointsAboveLimit = s.optBoolean("loserPointsAboveLimit", d.shalam.loserPointsAboveLimit),
                keyboardInput = s.optBoolean("keyboardInput", d.shalam.keyboardInput),
                defaultJoker = s.optBoolean("defaultJoker", d.shalam.defaultJoker),
                noJoker = s.optJSONObject("noJoker")?.let { decodeMode(it, ShalamModeRules.NO_JOKER) } ?: ShalamModeRules.NO_JOKER,
                joker = s.optJSONObject("joker")?.let { decodeMode(it, ShalamModeRules.JOKER) } ?: ShalamModeRules.JOKER
            ),
            menfi = MenfiSettings(
                hands = m.optInt("hands", d.menfi.hands),
                hidden = m.optBoolean("hidden", d.menfi.hidden),
                threeSuccess = m.optInt("threeSuccess", d.menfi.threeSuccess),
                threeFailure = m.optInt("threeFailure", d.menfi.threeFailure),
                highWins = m.optBoolean("highWins", d.menfi.highWins)
            ),
            hezar = HezarSettings(
                target = h.optInt("target", d.hezar.target),
                rounds = h.optInt("rounds", d.hezar.rounds),
                zeroPenalty = h.optInt("zeroPenalty", d.hezar.zeroPenalty),
                players = h.optInt("players", d.hezar.players)
            )
        )
    }

    fun encodeRoster(entries: List<RosterEntry>): JSONArray = JSONArray().apply {
        entries.forEach { e ->
            put(JSONObject().put("id", e.id).put("name", e.name).put("avatar", e.avatar).put("team", e.isTeam))
        }
    }

    fun decodeRoster(array: JSONArray): List<RosterEntry> = List(array.length()) { i ->
        val o = array.optJSONObject(i) ?: JSONObject()
        RosterEntry(o.optLong("id", i.toLong()), o.optString("name"), o.optInt("avatar", 0), o.optBoolean("team", true))
    }.filter { it.name.isNotBlank() }

    private fun ints(values: List<Int>): JSONArray = JSONArray().apply { values.forEach { put(it) } }

    private fun intList(array: JSONArray): List<Int> = List(array.length()) { array.optInt(it) }
}

/** Converts data saved by versions 1 and 2 without changing any recorded score. */
object LegacyCodec {
    fun settingsFromV2(o: JSONObject): AppSettings {
        val d = AppSettings()
        val endDifference = o.optInt("shalamEndDifference", 0)
        val target = o.optInt("shalamTarget", 1650)
        var noJoker = ShalamModeRules.NO_JOKER
        var joker = ShalamModeRules.JOKER
        if (endDifference > 0) {
            noJoker = noJoker.copy(endWithDiff = true, endDiff = endDifference)
            joker = joker.copy(endWithDiff = true, endDiff = endDifference)
        }
        if (target != 1650) noJoker = noJoker.copy(defaultEndPoint = roundToFive(target.coerceIn(400, 2800)))
        if (o.optInt("shalamValue", 330) == 400) {
            noJoker = noJoker.copy(shelemMode = ShelemMode.CUSTOM, shelemContractor = 400, shelemOpponent = 0)
        }
        return d.copy(
            general = d.general.copy(
                haptic = o.optBoolean("haptic", d.general.haptic),
                keepScreenAwake = o.optBoolean("keepScreenAwake", d.general.keepScreenAwake),
                persianDigits = o.optBoolean("persianDigits", d.general.persianDigits),
                largeText = o.optBoolean("largeText", d.general.largeText)
            ),
            shalam = d.shalam.copy(
                contractOnly = o.optBoolean("shalamAwardContractOnly", false),
                defaultJoker = o.optBoolean("shalamWithJoker", false),
                doubleType = if (o.optBoolean("shalamAskDouble", true)) DoubleType.ASK else DoubleType.NEGATIVE,
                noJoker = noJoker,
                joker = joker
            ),
            menfi = d.menfi.copy(
                hands = o.optInt("menfiHands", d.menfi.hands),
                hidden = o.optBoolean("menfiHiddenUntilReveal", d.menfi.hidden)
            ),
            hezar = d.hezar.copy(
                rounds = o.optInt("hezartaiiRounds", d.hezar.rounds),
                zeroPenalty = o.optInt("hezartaiiZeroPenalty", d.hezar.zeroPenalty),
                players = 2
            )
        )
    }

    fun sessionFromV2(o: JSONObject, settings: AppSettings): GameSession {
        val game = GameType.fromKey(o.optString("game"))
        val roundsJson = o.optJSONArray("rounds") ?: JSONArray()
        val rounds = List(roundsJson.length()) { i ->
            val r = roundsJson.optJSONObject(i) ?: JSONObject()
            val a = r.optInt("teamA")
            val b = r.optInt("teamB")
            val sourceA = if (r.has("sourceA")) r.optInt("sourceA") else null
            val sourceB = if (r.has("sourceB")) r.optInt("sourceB") else null
            val contract = if (r.has("contract")) r.optString("contract") else null
            val contractTeam = if (r.has("contractTeam")) r.optInt("contractTeam") - 1 else -1
            var note = r.optString("note")
            if (game == GameType.SHALAM && contract != null && contractTeam in 0..1 && !note.contains(contract)) {
                note = if (note.isBlank()) contract else "$note • $contract"
            }
            val numbers = if (game == GameType.MENFI && sourceA != null && sourceB != null) listOf(sourceA, sourceB) else emptyList()
            val outcome = if (numbers.size == 2 && numbers.all { it in MenfiEngine.readyNumbers }) {
                MenfiEngine.outcomes(numbers[0], numbers[1]).indexOfFirst { it.teamAScore == a && it.teamBScore == b }
            } else -1
            Round(
                kind = RoundKind.FIXED,
                scores = listOf(a, b),
                note = note,
                contractTeam = contractTeam,
                numbers = numbers,
                outcome = outcome,
                raw = if (game == GameType.HEZARTAII && sourceA != null && sourceB != null) listOf(sourceA, sourceB) else emptyList()
            )
        }
        val id = o.optLong("id", o.optLong("timestamp"))
        return GameSession(
            id = id,
            game = game,
            sides = listOf(
                Side(o.optString("teamA", "تیم اول"), o.optInt("avatarA", 0)),
                Side(o.optString("teamB", "تیم دوم"), o.optInt("avatarB", 1))
            ),
            rounds = rounds,
            rules = legacyRules(game, settings),
            finished = o.optBoolean("finished"),
            updatedAt = o.optLong("timestamp", id),
            label = "نسخهٔ ۲"
        )
    }

    fun sessionFromV1(o: JSONObject, settings: AppSettings): GameSession {
        val game = GameType.fromKey(o.optString("game"))
        val timestamp = o.optLong("timestamp")
        val hands = o.optInt("rounds", 0)
        return GameSession(
            id = timestamp,
            game = game,
            sides = listOf(
                Side(o.optString("teamA", "تیم اول"), o.optInt("avatarA", 0)),
                Side(o.optString("teamB", "تیم دوم"), o.optInt("avatarB", 1))
            ),
            rounds = listOf(
                Round(
                    kind = RoundKind.FIXED,
                    scores = listOf(o.optInt("scoreA"), o.optInt("scoreB")),
                    note = "جمع ثبت‌شده در نسخهٔ ۱ • $hands دست"
                )
            ),
            rules = legacyRules(game, settings),
            finished = true,
            updatedAt = timestamp,
            label = "نسخهٔ ۱"
        )
    }

    /** Earlier versions ranked «منفی» by the lowest total; migrated games keep that meaning. */
    private fun legacyRules(game: GameType, settings: AppSettings): GameRules {
        val rules = settings.rulesFor(game, withJoker = false)
        return rules.copy(menfi = rules.menfi.copy(highWins = false))
    }

    private fun roundToFive(value: Int): Int = (value / 5) * 5
}
