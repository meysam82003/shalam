package com.meysam.divanemtiaz

import kotlin.math.abs
import kotlin.math.pow

/** A team (شلم، منفی) or a player (هزارتایی، دو لو گشنیز), identified by name. */
data class Identity(val name: String, val isTeam: Boolean)

/** One finished game seen from one side against one opponent. */
data class Meeting(
    val sessionId: Long,
    val time: Long,
    val game: GameType,
    val opponent: String,
    val result: Int,
    val ownBefore: Double,
    val opponentBefore: Double,
    val ownPoints: Int?,
    val opponentPoints: Int?
)

data class HeadToHead(
    val opponent: String,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val lastOpponentRating: Double
)

class PlayerStats(val id: Identity) {
    var rating = StatsEngine.START
    var games = 0
    var wins = 0
    var losses = 0
    var draws = 0
    var pointsFor = 0
    var pointsAgainst = 0
    var streak = 0
    var bestStreak = 0
    var bestScore = Int.MIN_VALUE
    val byGame = linkedMapOf<GameType, Int>()
    val history = mutableListOf<Pair<Long, Double>>()
    val meetings = mutableListOf<Meeting>()
    val sessions = mutableListOf<Long>()

    val winRate: Float get() = if (games == 0) 0f else wins.toFloat() / games
}

data class GameRecord(val title: String, val holder: String, val value: String, val sessionId: Long?)

/**
 * Elo ratings and records from every finished game. Two-side games use standard Elo (K = 32);
 * games with more players compare every pair of players by final place, with K / (n − 1).
 */
object StatsEngine {
    const val START = 1000.0
    const val K = 32.0
    const val STRENGTH_GAP = 75.0

    fun time(s: GameSession): Long = s.endedAt ?: s.updatedAt

    fun expected(a: Double, b: Double): Double = 1.0 / (1.0 + 10.0.pow((b - a) / 400.0))

    fun compute(sessions: List<GameSession>): Map<Identity, PlayerStats> {
        val stats = linkedMapOf<Identity, PlayerStats>()
        fun of(id: Identity) = stats.getOrPut(id) { PlayerStats(id) }
        sessions.filter { it.finished && it.sides.size >= 2 }.sortedWith(compareBy<GameSession> { time(it) }.thenBy { it.id }).forEach { s ->
            val ids = s.sides.map { Identity(it.name.trim(), s.game.isTeamGame) }
            if (ids.distinct().size != ids.size) return@forEach
            val players = ids.map(::of)
            val before = players.map { it.rating }
            val totals = GameEngine.totals(s)
            val winners = GameEngine.winners(s)
            val n = players.size
            val place = placeOf(s, totals)
            val delta = DoubleArray(n)
            val kEff = K / (n - 1)
            for (i in 0 until n) for (j in i + 1 until n) {
                val score = pairScore(place[i], place[j])
                val e = expected(before[i], before[j])
                delta[i] += kEff * (score - e)
                delta[j] -= kEff * (score - e)
            }
            val sign = if (GameEngine.highWins(s)) 1 else -1
            val t = time(s)
            players.forEachIndexed { i, p ->
                val result = when {
                    winners.size == 1 && winners[0] == i -> 1
                    winners.isEmpty() || (i in winners) -> 0
                    else -> -1
                }
                p.games++
                when (result) {
                    1 -> p.wins++
                    0 -> p.draws++
                    else -> p.losses++
                }
                p.streak = when (result) {
                    1 -> if (p.streak > 0) p.streak + 1 else 1
                    -1 -> if (p.streak < 0) p.streak - 1 else -1
                    else -> 0
                }
                p.bestStreak = maxOf(p.bestStreak, p.streak)
                p.byGame[s.game] = (p.byGame[s.game] ?: 0) + 1
                p.bestScore = maxOf(p.bestScore, totals[i])
                p.sessions += s.id
                if (n == 2) {
                    p.pointsFor += totals[i] * sign
                    p.pointsAgainst += totals[1 - i] * sign
                }
                for (j in 0 until n) if (j != i) {
                    val pair = pairScore(place[i], place[j])
                    p.meetings += Meeting(
                        s.id, t, s.game, ids[j].name,
                        if (n == 2) result else if (pair > 0.5) 1 else if (pair < 0.5) -1 else 0,
                        before[i], before[j],
                        if (n == 2) totals[i] else null,
                        if (n == 2) totals[j] else null
                    )
                }
            }
            players.forEachIndexed { i, p ->
                p.rating = before[i] + delta[i]
                p.history += t to p.rating
            }
        }
        return stats
    }

    /** Place of each side: 0 is best; equal places mean a draw between them. */
    private fun placeOf(s: GameSession, totals: List<Int>): List<Int> {
        if (s.game == GameType.DOLO) {
            val ranking = GameEngine.ranking(s)
            return s.sides.indices.map { ranking.indexOf(it) }
        }
        val high = GameEngine.highWins(s)
        return totals.map { v -> totals.count { if (high) it > v else it < v } }
    }

    private fun pairScore(a: Int, b: Int): Double = when {
        a < b -> 1.0
        a > b -> 0.0
        else -> 0.5
    }

    fun headToHead(p: PlayerStats): List<HeadToHead> = p.meetings.groupBy { it.opponent }.map { (opponent, list) ->
        HeadToHead(
            opponent,
            list.size,
            list.count { it.result == 1 },
            list.count { it.result == 0 },
            list.count { it.result == -1 },
            list.maxByOrNull { it.time }?.opponentBefore ?: START
        )
    }.sortedWith(compareByDescending<HeadToHead> { it.games }.thenBy { it.opponent })

    /** 1: opponent was stronger, −1: weaker, 0: similar level. */
    fun strength(own: Double, opponent: Double): Int = when {
        opponent - own >= STRENGTH_GAP -> 1
        own - opponent >= STRENGTH_GAP -> -1
        else -> 0
    }

    fun strengthLabel(own: Double, opponent: Double): String = when (strength(own, opponent)) {
        1 -> "حریف قوی‌تر"
        -1 -> "حریف ضعیف‌تر"
        else -> "هم‌سطح"
    }

    fun tier(rating: Double): String = when {
        rating >= 1200 -> "طلایی"
        rating >= 1100 -> "نقره‌ای"
        rating >= 1000 -> "برنزی"
        else -> "تازه‌کار"
    }

    /** Ranking list for teams or players, optionally only one game. */
    fun leaderboard(sessions: List<GameSession>, teams: Boolean, game: GameType? = null): List<PlayerStats> =
        compute(sessions.filter { game == null || it.game == game })
            .values.filter { it.id.isTeam == teams }
            .sortedWith(compareByDescending<PlayerStats> { it.rating }.thenByDescending { it.wins }.thenBy { it.id.name })

    fun bestWin(p: PlayerStats): Meeting? = p.meetings.filter { it.result == 1 }.maxByOrNull { it.opponentBefore }

    fun worstLoss(p: PlayerStats): Meeting? = p.meetings.filter { it.result == -1 }.minByOrNull { it.opponentBefore }

    fun records(sessions: List<GameSession>, digits: (Any) -> String): List<GameRecord> {
        val finished = sessions.filter { it.finished && it.sides.size >= 2 }
        val out = mutableListOf<GameRecord>()
        var best: Triple<GameSession, Int, Int>? = null
        finished.forEach { s ->
            GameEngine.totals(s).forEachIndexed { i, v -> if (best == null || v > best!!.third) best = Triple(s, i, v) }
        }
        best?.let { (s, i, v) -> out += GameRecord("بیشترین جمع امتیاز در یک بازی", s.sides[i].name, "${digits(v)} در ${s.game.title}", s.id) }
        var margin: Triple<GameSession, Int, Int>? = null
        finished.filter { it.sides.size == 2 }.forEach { s ->
            val w = GameEngine.winners(s).singleOrNull() ?: return@forEach
            val t = GameEngine.totals(s)
            val d = abs(t[0] - t[1])
            if (margin == null || d > margin!!.third) margin = Triple(s, w, d)
        }
        margin?.let { (s, w, d) -> out += GameRecord("بزرگ‌ترین اختلاف برد", s.sides[w].name, "${digits(d)} امتیاز مقابل ${s.sides[1 - w].name}", s.id) }
        val all = compute(finished).values
        all.maxByOrNull { it.bestStreak }?.takeIf { it.bestStreak > 0 }?.let { out += GameRecord("طولانی‌ترین زنجیرهٔ برد", it.id.name, "${digits(it.bestStreak)} برد پشت سر هم", null) }
        all.maxByOrNull { it.games }?.let { out += GameRecord("بیشترین بازی", it.id.name, "${digits(it.games)} بازی", null) }
        all.maxByOrNull { it.rating }?.let { out += GameRecord("بالاترین رتبه", it.id.name, digits(Math.round(it.rating)), null) }
        return out
    }

    /** Running total of every side after each hand, for the game chart. */
    fun progression(s: GameSession): List<List<Int>> {
        val running = MutableList(s.sides.size) { 0 }
        val out = mutableListOf(running.toList())
        s.rounds.forEach { r ->
            if (r.kind == RoundKind.DOLO_ELIM) return@forEach
            s.sides.indices.forEach { running[it] += r.score(it) }
            out += running.toList()
        }
        return s.sides.indices.map { side -> out.map { it[side] } }
    }
}
