package com.meysam.divanemtiaz

import kotlin.random.Random

/** Live state of one league match, derived from its saved games so edits always flow through. */
data class MatchState(
    val match: LeagueMatch,
    val teamA: Int?,
    val teamB: Int?,
    val winsA: Int,
    val winsB: Int,
    val draws: Int,
    val played: Int,
    val pointsA: Int,
    val pointsB: Int,
    val winner: Int?,
    val decided: Boolean,
    val openGame: Long?
) {
    val ready: Boolean get() = teamA != null && (teamB != null || match.isBye)
    /** Knockout tie at 1–1 (with two wins needed): the next game is the decider between these two. */
    val isDecider: Boolean get() = !decided && match.winsNeeded > 1 && winsA == match.winsNeeded - 1 && winsB == match.winsNeeded - 1
}

data class Standing(
    val team: Int,
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val pointsFor: Int = 0,
    val pointsAgainst: Int = 0,
    val leaguePoints: Int = 0
) {
    val diff: Int get() = pointsFor - pointsAgainst
}

object LeagueEngine {
    fun state(league: League, match: LeagueMatch, lookup: (Long) -> GameSession?): MatchState {
        val a = if (match.feederA >= 0) winnerOf(league, match.feederA, lookup) else match.teamA
        val b = when {
            match.feederB >= 0 -> winnerOf(league, match.feederB, lookup)
            match.teamB == LeagueMatch.BYE -> null
            else -> match.teamB
        }
        if (match.isBye) {
            return MatchState(match, a, null, 0, 0, 0, 0, 0, 0, a, a != null, null)
        }
        var winsA = 0
        var winsB = 0
        var draws = 0
        var played = 0
        var pointsA = 0
        var pointsB = 0
        var open: Long? = null
        match.games.forEach { id ->
            val s = lookup(id) ?: return@forEach
            if (!s.finished) {
                if (open == null) open = id
                return@forEach
            }
            played++
            val totals = GameEngine.totals(s)
            val sign = if (GameEngine.highWins(s)) 1 else -1
            pointsA += totals.getOrElse(0) { 0 } * sign
            pointsB += totals.getOrElse(1) { 0 } * sign
            when (GameEngine.winners(s).singleOrNull()) {
                0 -> winsA++
                1 -> winsB++
                else -> draws++
            }
        }
        val need = match.winsNeeded.coerceAtLeast(1)
        val winner = when {
            a == null || b == null -> null
            winsA >= need -> a
            winsB >= need -> b
            else -> null
        }
        val decided = if (league.format == LeagueFormat.ROUND_ROBIN && !match.isFinal) played >= 1 else winner != null
        return MatchState(match, a, b, winsA, winsB, draws, played, pointsA, pointsB, winner, decided, open)
    }

    fun winnerOf(league: League, matchId: Int, lookup: (Long) -> GameSession?): Int? =
        league.matches.firstOrNull { it.id == matchId }?.let { state(league, it, lookup).winner }

    fun stages(league: League): List<Int> = league.matches.map { it.stage }.distinct().sorted()

    fun stageTitle(league: League, stage: Int): String {
        val matches = league.matches.filter { it.stage == stage }
        if (matches.any { it.isFinal }) return "فینال"
        if (league.format == LeagueFormat.ROUND_ROBIN) return "دور ${PersianText.digits(stage)}"
        return when (matches.count { !it.isBye }) {
            2 -> "نیمه‌نهایی"
            in 3..4 -> "یک‌چهارم نهایی"
            else -> "مرحلهٔ ${PersianText.digits(stage)}"
        }
    }

    /** Creates the first stage from the drawn order of teams. */
    fun start(league: League, order: List<Int>, random: Random = Random.Default) {
        league.matches.clear()
        if (league.format == LeagueFormat.ROUND_ROBIN) {
            roundRobin(order, league.config.doubleRoundRobin).forEachIndexed { round, pairs ->
                pairs.forEach { (a, b) -> league.matches += LeagueMatch(nextId(league), round + 1, a, b, winsNeeded = 1) }
            }
            if (order.size == 2 && league.matches.size <= 2) {
                league.matches.clear()
                league.matches += LeagueMatch(nextId(league), 1, order[0], order[1], winsNeeded = league.config.winsNeeded, isFinal = true)
            }
        } else {
            val teams = order.toMutableList()
            var bye: Int? = null
            if (teams.size % 2 == 1) bye = teams.removeAt(random.nextInt(teams.size))
            val single = teams.size == 2 && bye == null
            teams.chunked(2).forEach { (a, b) ->
                league.matches += LeagueMatch(nextId(league), 1, a, b, winsNeeded = league.config.winsNeeded, isFinal = single)
            }
            if (bye != null) league.matches += LeagueMatch(nextId(league), 1, bye, LeagueMatch.BYE)
        }
    }

    /** Opens the next stage when the current one is decided. Returns true when anything changed. */
    fun advance(league: League, lookup: (Long) -> GameSession?, random: Random = Random.Default): Boolean {
        if (league.matches.isEmpty()) return false
        val last = league.matches.maxOf { it.stage }
        val current = league.matches.filter { it.stage == last }
        val states = current.map { state(league, it, lookup) }
        if (states.any { !it.decided }) {
            if (league.finished) {
                league.finished = false
                return true
            }
            return false
        }
        if (league.format == LeagueFormat.ROUND_ROBIN && current.none { it.isFinal }) {
            val table = standings(league, lookup)
            val tieAtTop = table.size >= 2 && sameRank(table[0], table[1])
            if (league.teams.size >= 3 && (league.config.finalAfterTable || tieAtTop)) {
                league.matches += LeagueMatch(
                    nextId(league), last + 1, table[0].team, table[1].team,
                    winsNeeded = league.config.winsNeeded.coerceAtLeast(1), isFinal = true
                )
                return true
            }
            val done = !league.finished
            league.finished = true
            return done
        }
        if (current.size == 1) {
            val done = !league.finished
            league.finished = true
            return done
        }
        val slots = current.map { it.id }.toMutableList()
        var byeSlot: Int? = null
        if (slots.size % 2 == 1) {
            val hadBye = league.matches.filter { it.isBye }.mapNotNull { m -> state(league, m, lookup).winner }.toSet()
            val fresh = slots.filter { slot -> winnerOf(league, slot, lookup) !in hadBye }
            val pool = fresh.ifEmpty { slots }
            byeSlot = pool[random.nextInt(pool.size)]
            slots.remove(byeSlot)
        }
        val single = slots.size == 2 && byeSlot == null
        slots.chunked(2).forEach { (a, b) ->
            league.matches += LeagueMatch(
                nextId(league), last + 1, -1, -1, feederA = a, feederB = b,
                winsNeeded = league.config.winsNeeded, isFinal = single
            )
        }
        if (byeSlot != null) league.matches += LeagueMatch(nextId(league), last + 1, -1, LeagueMatch.BYE, feederA = byeSlot)
        return true
    }

    fun champion(league: League, lookup: (Long) -> GameSession?): Int? {
        if (!league.finished || league.matches.isEmpty()) return null
        val last = league.matches.maxOf { it.stage }
        val final = league.matches.filter { it.stage == last }
        if (league.format == LeagueFormat.ROUND_ROBIN && final.none { it.isFinal }) {
            return standings(league, lookup).firstOrNull()?.team
        }
        return final.singleOrNull()?.let { state(league, it, lookup).winner }
    }

    /** Table of a round-robin league (the final is not counted). */
    fun standings(league: League, lookup: (Long) -> GameSession?): List<Standing> {
        val rows = league.teams.indices.associateWith { Standing(it) }.toMutableMap()
        val cfg = league.config
        league.matches.filter { !it.isFinal && !it.isBye }.forEach { m ->
            val a = m.teamA
            val b = m.teamB
            if (a < 0 || b < 0) return@forEach
            m.games.mapNotNull(lookup).filter { it.finished }.forEach { s ->
                val totals = GameEngine.totals(s)
                val sign = if (GameEngine.highWins(s)) 1 else -1
                val pa = totals.getOrElse(0) { 0 } * sign
                val pb = totals.getOrElse(1) { 0 } * sign
                val w = GameEngine.winners(s).singleOrNull()
                rows[a] = add(rows.getValue(a), pa, pb, w == 0, w == 1, cfg)
                rows[b] = add(rows.getValue(b), pb, pa, w == 1, w == 0, cfg)
            }
        }
        return rows.values.sortedWith(
            compareByDescending<Standing> { it.leaguePoints }
                .thenByDescending { it.diff }
                .thenByDescending { it.pointsFor }
                .thenByDescending { it.won }
                .thenBy { it.team }
        )
    }

    private fun add(s: Standing, own: Int, other: Int, won: Boolean, lost: Boolean, cfg: LeagueSettings): Standing = s.copy(
        played = s.played + 1,
        won = s.won + if (won) 1 else 0,
        lost = s.lost + if (lost) 1 else 0,
        drawn = s.drawn + if (!won && !lost) 1 else 0,
        pointsFor = s.pointsFor + own,
        pointsAgainst = s.pointsAgainst + other,
        leaguePoints = s.leaguePoints + when {
            won -> cfg.pointsWin
            lost -> cfg.pointsLoss
            else -> cfg.pointsDraw
        }
    )

    private fun sameRank(a: Standing, b: Standing): Boolean =
        a.leaguePoints == b.leaguePoints && a.diff == b.diff && a.pointsFor == b.pointsFor && a.won == b.won

    /** Circle method: every team meets every other once; nobody plays twice in one round. */
    fun roundRobin(order: List<Int>, double: Boolean): List<List<Pair<Int, Int>>> {
        if (order.size < 2) return emptyList()
        val teams: MutableList<Int?> = order.toMutableList<Int?>()
        if (teams.size % 2 == 1) teams += null
        val n = teams.size
        val rounds = mutableListOf<List<Pair<Int, Int>>>()
        val rotating = teams.subList(1, n).toMutableList()
        for (r in 0 until n - 1) {
            val line = listOf(teams[0]) + rotating
            val pairs = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until n / 2) {
                val x = line[i]
                val y = line[n - 1 - i]
                if (x != null && y != null) pairs += if ((r + i) % 2 == 0) x to y else y to x
            }
            rounds += pairs
            rotating.add(0, rotating.removeAt(rotating.lastIndex))
        }
        if (double) rounds += rounds.map { round -> round.map { (a, b) -> b to a } }
        return rounds
    }

    fun nextId(league: League): Int = (league.matches.maxOfOrNull { it.id } ?: 0) + 1

    /** Next game of a match: an unfinished one to continue, or null when a new game must be created. */
    fun openGame(state: MatchState): Long? = state.openGame
}
