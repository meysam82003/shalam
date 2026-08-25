package com.meysam.divanemtiaz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class V3LeagueMode { BEST_OF_THREE, CUMULATIVE }

data class V3LeagueTeam(
    val id: Long,
    var name: String,
    var avatar: Int,
    var matchWins: Int = 0,
    var matchLosses: Int = 0,
    var gameWins: Int = 0,
    var cumulativeScore: Int = 0
)

data class V3LeagueMatch(
    val teamAId: Long,
    val teamBId: Long,
    var winsA: Int = 0,
    var winsB: Int = 0
) {
    val completed: Boolean get() = winsA >= 2 || winsB >= 2
    val winnerId: Long? get() = when {
        winsA >= 2 -> teamAId
        winsB >= 2 -> teamBId
        else -> null
    }
}

data class V3LeagueState(
    val id: Long = System.currentTimeMillis(),
    var name: String = "لیگ دوستانه",
    var mode: V3LeagueMode = V3LeagueMode.BEST_OF_THREE,
    var teams: MutableList<V3LeagueTeam> = mutableListOf(),
    var matches: MutableList<V3LeagueMatch> = mutableListOf(),
    var cumulativeRounds: Int = 0,
    var targetGames: Int = 10,
    var targetScore: Int = 150,
    var finished: Boolean = false
) {
    fun rebuildMatches() {
        matches.clear()
        LeaguePlannerV3.roundRobin(teams.map { it.id }).forEach { (a, b) -> matches += V3LeagueMatch(a, b) }
    }

    fun registerMatchGame(matchIndex: Int, winnerId: Long) {
        val match = matches[matchIndex]
        if (match.completed) return
        if (winnerId == match.teamAId) match.winsA++ else if (winnerId == match.teamBId) match.winsB++ else return
        teams.firstOrNull { it.id == winnerId }?.let { it.gameWins++ }
        if (match.completed) {
            val winner = match.winnerId ?: return
            val loserId = if (winner == match.teamAId) match.teamBId else match.teamAId
            teams.firstOrNull { it.id == winner }?.let { it.matchWins++ }
            teams.firstOrNull { it.id == loserId }?.let { it.matchLosses++ }
            finished = matches.all { it.completed }
        }
    }

    fun registerCumulativeRound(scores: List<Int>) {
        require(scores.size == teams.size)
        teams.forEachIndexed { index, team -> team.cumulativeScore += scores[index] }
        cumulativeRounds++
        finished = cumulativeRounds >= targetGames || teams.any { it.cumulativeScore >= targetScore }
    }

    fun ranking(): List<V3LeagueTeam> = when (mode) {
        V3LeagueMode.BEST_OF_THREE -> teams.sortedWith(compareByDescending<V3LeagueTeam> { it.matchWins }.thenByDescending { it.gameWins }.thenBy { it.matchLosses })
        V3LeagueMode.CUMULATIVE -> teams.sortedByDescending { it.cumulativeScore }
    }
}

object V3LeagueStore {
    private const val PREFS = "divan_emtiaz_v3_league"
    private const val KEY = "active_league"

    fun save(context: Context, state: V3LeagueState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, state.toJson().toString()).apply()
    }

    fun load(context: Context): V3LeagueState? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun V3LeagueState.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("mode", mode.name); put("cumulativeRounds", cumulativeRounds)
        put("targetGames", targetGames); put("targetScore", targetScore); put("finished", finished)
        put("teams", JSONArray().apply { teams.forEach { t -> put(JSONObject().apply {
            put("id", t.id); put("name", t.name); put("avatar", t.avatar); put("matchWins", t.matchWins); put("matchLosses", t.matchLosses); put("gameWins", t.gameWins); put("cumulativeScore", t.cumulativeScore)
        }) } })
        put("matches", JSONArray().apply { matches.forEach { m -> put(JSONObject().apply {
            put("teamAId", m.teamAId); put("teamBId", m.teamBId); put("winsA", m.winsA); put("winsB", m.winsB)
        }) } })
    }

    private fun fromJson(o: JSONObject): V3LeagueState {
        val teams = mutableListOf<V3LeagueTeam>()
        o.optJSONArray("teams")?.let { a -> for (i in 0 until a.length()) {
            val t = a.getJSONObject(i); teams += V3LeagueTeam(t.optLong("id"), t.optString("name"), t.optInt("avatar"), t.optInt("matchWins"), t.optInt("matchLosses"), t.optInt("gameWins"), t.optInt("cumulativeScore"))
        } }
        val matches = mutableListOf<V3LeagueMatch>()
        o.optJSONArray("matches")?.let { a -> for (i in 0 until a.length()) {
            val m = a.getJSONObject(i); matches += V3LeagueMatch(m.optLong("teamAId"), m.optLong("teamBId"), m.optInt("winsA"), m.optInt("winsB"))
        } }
        return V3LeagueState(
            id = o.optLong("id", System.currentTimeMillis()), name = o.optString("name", "لیگ دوستانه"),
            mode = runCatching { V3LeagueMode.valueOf(o.optString("mode")) }.getOrDefault(V3LeagueMode.BEST_OF_THREE),
            teams = teams, matches = matches, cumulativeRounds = o.optInt("cumulativeRounds"), targetGames = o.optInt("targetGames", 10), targetScore = o.optInt("targetScore", 150), finished = o.optBoolean("finished")
        )
    }
}
