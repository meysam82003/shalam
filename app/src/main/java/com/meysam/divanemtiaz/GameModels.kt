package com.meysam.divanemtiaz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class GameType(
    val key: String,
    val title: String,
    val subtitle: String,
    val target: Int,
    val badge: String
) {
    SHALAM("shalam", "شلم", "رقابت تیمی تا ۱۶۵۰ امتیاز", 1650, "♠"),
    MENFI("menfi", "منفی", "کمترین امتیاز، بهترین نتیجه", 0, "♥"),
    HEZARTAII("hezartaii", "هزارتایی", "نبرد امتیازی تا ۱۰۰۰", 1000, "♦");

    companion object {
        fun fromKey(key: String): GameType = entries.firstOrNull { it.key == key } ?: SHALAM
    }
}

data class RoundScore(val teamA: Int, val teamB: Int)

object ScoreEngine {
    fun total(rounds: List<RoundScore>, teamA: Boolean): Int =
        rounds.sumOf { if (teamA) it.teamA else it.teamB }

    fun winner(game: GameType, scoreA: Int, scoreB: Int): Int = when {
        scoreA == scoreB -> 0
        game == GameType.MENFI && scoreA < scoreB -> 1
        game == GameType.MENFI -> 2
        scoreA > scoreB -> 1
        else -> 2
    }

    fun reachedTarget(game: GameType, scoreA: Int, scoreB: Int): Boolean =
        game.target > 0 && (scoreA >= game.target || scoreB >= game.target)
}

data class GameSession(
    val game: GameType,
    val teamA: String,
    val teamB: String,
    val avatarA: Int,
    val avatarB: Int,
    val rounds: MutableList<RoundScore> = mutableListOf()
)

data class HistoryRecord(
    val game: GameType,
    val teamA: String,
    val teamB: String,
    val scoreA: Int,
    val scoreB: Int,
    val avatarA: Int,
    val avatarB: Int,
    val rounds: Int,
    val timestamp: Long
)

object HistoryStore {
    private const val PREFS = "divan_prefs"
    private const val KEY_HISTORY = "history"

    fun load(context: Context): List<HistoryRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                HistoryRecord(
                    game = GameType.fromKey(item.getString("game")),
                    teamA = item.getString("teamA"),
                    teamB = item.getString("teamB"),
                    scoreA = item.getInt("scoreA"),
                    scoreB = item.getInt("scoreB"),
                    avatarA = item.optInt("avatarA", 0),
                    avatarB = item.optInt("avatarB", 1),
                    rounds = item.optInt("rounds", 0),
                    timestamp = item.getLong("timestamp")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, record: HistoryRecord) {
        val records = listOf(record) + load(context)
        val array = JSONArray()
        records.take(100).forEach { item ->
            array.put(JSONObject().apply {
                put("game", item.game.key)
                put("teamA", item.teamA)
                put("teamB", item.teamB)
                put("scoreA", item.scoreA)
                put("scoreB", item.scoreB)
                put("avatarA", item.avatarA)
                put("avatarB", item.avatarB)
                put("rounds", item.rounds)
                put("timestamp", item.timestamp)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_HISTORY).apply()
    }
}

