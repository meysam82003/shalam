package com.meysam.divanemtiaz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryRecord(
    val id: Long,
    val game: GameType,
    val teamA: String,
    val teamB: String,
    val avatarA: Int,
    val avatarB: Int,
    val rounds: MutableList<ScoreRound>,
    val timestamp: Long,
    val finished: Boolean
) {
    val scoreA: Int get() = ScoreEngine.total(rounds, true)
    val scoreB: Int get() = ScoreEngine.total(rounds, false)
}

object GameStore {
    private const val PREFS = "divan_emtiaz_v2"
    private const val HISTORY = "history_v2"
    private const val SETTINGS = "settings_v2"

    fun loadHistory(context: Context): MutableList<HistoryRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(HISTORY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { index -> historyFromJson(array.getJSONObject(index)) }
        }.getOrElse { mutableListOf() }
    }

    fun saveSession(context: Context, session: GameSession, finished: Boolean): HistoryRecord {
        val history = loadHistory(context)
        val record = HistoryRecord(
            id = session.startedAt,
            game = session.game,
            teamA = session.teamA,
            teamB = session.teamB,
            avatarA = session.avatarA,
            avatarB = session.avatarB,
            rounds = session.rounds.map { it.copy() }.toMutableList(),
            timestamp = System.currentTimeMillis(),
            finished = finished
        )
        history.removeAll { it.id == record.id }
        history.add(0, record)
        writeHistory(context, history.take(100))
        return record
    }

    fun deleteHistory(context: Context, id: Long) {
        writeHistory(context, loadHistory(context).filterNot { it.id == id })
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(HISTORY).apply()
    }

    fun toSession(record: HistoryRecord): GameSession = GameSession(
        record.game, record.teamA, record.teamB, record.avatarA, record.avatarB,
        record.rounds.map { it.copy() }.toMutableList(), record.id
    )

    fun loadSettings(context: Context): AppSettings {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SETTINGS, null) ?: return AppSettings()
        return runCatching {
            val o = JSONObject(raw)
            AppSettings(
                haptic = o.optBoolean("haptic", true),
                keepScreenAwake = o.optBoolean("keepScreenAwake", true),
                persianDigits = o.optBoolean("persianDigits", true),
                largeText = o.optBoolean("largeText", false),
                shalamTarget = o.optInt("shalamTarget", 1650),
                shalamValue = o.optInt("shalamValue", 330),
                shalamAwardContractOnly = o.optBoolean("shalamAwardContractOnly", false),
                shalamWithJoker = o.optBoolean("shalamWithJoker", false),
                shalamAskDouble = o.optBoolean("shalamAskDouble", true),
                shalamEndDifference = o.optInt("shalamEndDifference", 0),
                menfiHands = o.optInt("menfiHands", 8),
                menfiHiddenUntilReveal = o.optBoolean("menfiHiddenUntilReveal", true),
                hezartaiiRounds = o.optInt("hezartaiiRounds", 10),
                hezartaiiZeroPenalty = o.optInt("hezartaiiZeroPenalty", -50)
            )
        }.getOrDefault(AppSettings())
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        val o = JSONObject().apply {
            put("haptic", settings.haptic)
            put("keepScreenAwake", settings.keepScreenAwake)
            put("persianDigits", settings.persianDigits)
            put("largeText", settings.largeText)
            put("shalamTarget", settings.shalamTarget)
            put("shalamValue", settings.shalamValue)
            put("shalamAwardContractOnly", settings.shalamAwardContractOnly)
            put("shalamWithJoker", settings.shalamWithJoker)
            put("shalamAskDouble", settings.shalamAskDouble)
            put("shalamEndDifference", settings.shalamEndDifference)
            put("menfiHands", settings.menfiHands)
            put("menfiHiddenUntilReveal", settings.menfiHiddenUntilReveal)
            put("hezartaiiRounds", settings.hezartaiiRounds)
            put("hezartaiiZeroPenalty", settings.hezartaiiZeroPenalty)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SETTINGS, o.toString()).apply()
    }

    private fun writeHistory(context: Context, history: List<HistoryRecord>) {
        val array = JSONArray()
        history.forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("game", record.game.key)
                put("teamA", record.teamA)
                put("teamB", record.teamB)
                put("avatarA", record.avatarA)
                put("avatarB", record.avatarB)
                put("timestamp", record.timestamp)
                put("finished", record.finished)
                put("rounds", JSONArray().apply {
                    record.rounds.forEach { round ->
                        put(JSONObject().apply {
                            put("teamA", round.teamA)
                            put("teamB", round.teamB)
                            put("note", round.note)
                            round.sourceA?.let { put("sourceA", it) }
                            round.sourceB?.let { put("sourceB", it) }
                            round.contractTeam?.let { put("contractTeam", it) }
                            round.contract?.let { put("contract", it) }
                        })
                    }
                })
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(HISTORY, array.toString()).apply()
    }

    private fun historyFromJson(o: JSONObject): HistoryRecord {
        val roundsJson = o.optJSONArray("rounds") ?: JSONArray()
        val rounds = MutableList(roundsJson.length()) { index ->
            val r = roundsJson.getJSONObject(index)
            ScoreRound(
                teamA = r.optInt("teamA"), teamB = r.optInt("teamB"), note = r.optString("note"),
                sourceA = if (r.has("sourceA")) r.optInt("sourceA") else null,
                sourceB = if (r.has("sourceB")) r.optInt("sourceB") else null,
                contractTeam = if (r.has("contractTeam")) r.optInt("contractTeam") else null,
                contract = if (r.has("contract")) r.optString("contract") else null
            )
        }
        return HistoryRecord(
            id = o.optLong("id", o.optLong("timestamp")), game = GameType.fromKey(o.optString("game")),
            teamA = o.optString("teamA", "تیم اول"), teamB = o.optString("teamB", "تیم دوم"),
            avatarA = o.optInt("avatarA", 0), avatarB = o.optInt("avatarB", 1), rounds = rounds,
            timestamp = o.optLong("timestamp"), finished = o.optBoolean("finished")
        )
    }
}
