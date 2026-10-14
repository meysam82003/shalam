package com.meysam.divanemtiaz

/** One person's record in one card game (a team game counts for every person of the team). */
class PersonGameLine {
    var rating = StatsEngine.START
    var games = 0
    var wins = 0
    var draws = 0
    var losses = 0
    var pointsFor = 0
    var pointsAgainst = 0
    var bestScore = Int.MIN_VALUE
    val history = mutableListOf<Pair<Long, Double>>()
    val winRate: Float get() = if (games == 0) 0f else wins.toFloat() / games
}

/** A person's mafia record: sides, roles, wins and how and when he left the games. */
class PersonMafia {
    var games = 0
    var wins = 0
    var survived = 0
    val sideGames = linkedMapOf<MafiaSide, Int>()
    val sideWins = linkedMapOf<MafiaSide, Int>()
    val roles = linkedMapOf<MafiaRole, Int>()
    val deaths = linkedMapOf<String, Int>()
    /** Night/day number in which he left each game he did not survive. */
    val exitNights = mutableListOf<Int>()
    /** Left in the first night or first day. */
    var firstOut = 0
    val winRate: Float get() = if (games == 0) 0f else wins.toFloat() / games
}

/** A person's record in a party game (pantomime or spy). */
class PersonParty {
    var games = 0
    var wins = 0
    var points = 0
    /** Spy: games played as the spy and won as the spy. */
    var special = 0
    var specialWins = 0
    val winRate: Float get() = if (games == 0) 0f else wins.toFloat() / games
}

class PersonRecord(val name: String) {
    val byGame = linkedMapOf<GameType, PersonGameLine>()
    /** Partner → (games together, wins together) in team games. */
    val partners = linkedMapOf<String, Pair<Int, Int>>()
    val teams = linkedMapOf<String, Int>()
    val mafia = PersonMafia()
    val panto = PersonParty()
    val spy = PersonParty()
    val sessions = mutableListOf<Long>()

    val cardGames: Int get() = byGame.values.sumOf { it.games }
    val cardWins: Int get() = byGame.values.sumOf { it.wins }
    val totalGames: Int get() = cardGames + mafia.games + panto.games + spy.games
    val totalWins: Int get() = cardWins + mafia.wins + panto.wins + spy.wins
    fun line(game: GameType) = byGame.getOrPut(game) { PersonGameLine() }
}

/**
 * Per-person statistics across every game: Elo per card game (a team's rating is the average of its people),
 * partners, and the mafia narrator's games (side, role, win, exit).
 */
object PersonStats {
    const val PANTO = "panto"
    const val SPY = "spy"

    /** The people behind a side: the team's members, or the player himself in individual games. */
    fun people(s: GameSession, side: Side): List<String> =
        if (s.game.isTeamGame) side.members.map { it.trim() }.filter { it.isNotEmpty() } else listOf(side.name.trim())

    fun compute(sessions: List<GameSession>, mafiaGames: List<MafiaGame>, pantoMatches: List<PantoMatch> = emptyList(), spyGames: List<SpyGame> = emptyList()): Map<String, PersonRecord> {
        val out = linkedMapOf<String, PersonRecord>()
        fun of(name: String) = out.getOrPut(name) { PersonRecord(name) }
        sessions.filter { it.finished && it.sides.size >= 2 }.sortedWith(compareBy<GameSession> { StatsEngine.time(it) }.thenBy { it.id }).forEach { s ->
            val groups = s.sides.map { people(s, it) }
            if (groups.any { it.isEmpty() }) return@forEach
            if (groups.flatten().distinct().size != groups.flatten().size) return@forEach
            val totals = GameEngine.totals(s)
            val winners = GameEngine.winners(s)
            val place = StatsEngine.placeOf(s, totals)
            val lines = groups.map { g -> g.map { of(it).line(s.game) } }
            val teamRating = lines.map { l -> l.map { it.rating }.average() }
            val n = groups.size
            val kEff = StatsEngine.K / (n - 1)
            val delta = DoubleArray(n)
            for (i in 0 until n) for (j in i + 1 until n) {
                val score = StatsEngine.pairScore(place[i], place[j])
                val e = StatsEngine.expected(teamRating[i], teamRating[j])
                delta[i] += kEff * (score - e)
                delta[j] -= kEff * (score - e)
            }
            val sign = if (GameEngine.highWins(s)) 1 else -1
            val t = StatsEngine.time(s)
            groups.forEachIndexed { i, people ->
                val result = when {
                    winners.size == 1 && winners[0] == i -> 1
                    winners.isEmpty() || i in winners -> 0
                    else -> -1
                }
                people.forEachIndexed { k, name ->
                    val rec = of(name)
                    val line = lines[i][k]
                    line.games++
                    when (result) { 1 -> line.wins++; 0 -> line.draws++; else -> line.losses++ }
                    line.bestScore = maxOf(line.bestScore, totals[i])
                    if (n == 2) { line.pointsFor += totals[i] * sign; line.pointsAgainst += totals[1 - i] * sign }
                    line.rating += delta[i]
                    line.history += t to line.rating
                    rec.sessions += s.id
                    if (s.game.isTeamGame) {
                        rec.teams[s.sides[i].name] = (rec.teams[s.sides[i].name] ?: 0) + 1
                        people.filter { it != name }.forEach { partner ->
                            val (g0, w0) = rec.partners[partner] ?: (0 to 0)
                            rec.partners[partner] = (g0 + 1) to (w0 + if (result == 1) 1 else 0)
                        }
                    }
                }
            }
        }
        mafiaGames.filter { it.finished && it.started }.sortedBy { it.updatedAt }.forEach { g -> mafiaGame(g).forEach { e -> addMafia(of(e.name).mafia, e) } }
        // Pantomime: every person of a team shares its result and points.
        pantoMatches.filter { it.finished }.forEach { m ->
            val win = m.winner
            m.teams.forEachIndexed { i, t ->
                t.members.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
                    val p = of(name).panto
                    p.games++; p.points += t.points
                    if (i == win) p.wins++
                }
            }
        }
        // Spy: the spies win together, or the other players do.
        spyGames.filter { it.finished && it.named && it.outcome != null }.forEach { g ->
            val spiesWin = g.spiesWin == true
            g.players.forEachIndexed { i, name ->
                val p = of(name.trim()).spy
                val won = if (g.isSpy(i)) spiesWin else !spiesWin
                p.games++
                if (won) p.wins++
                if (g.isSpy(i)) { p.special++; if (won) p.specialWins++ }
            }
        }
        return out
    }

    /** One person's part in one finished mafia game. */
    data class MafiaEntry(val name: String, val role: MafiaRole, val side: MafiaSide, val won: Boolean, val survived: Boolean, val deathType: String, val outNight: Int)

    fun mafiaGame(g: MafiaGame): List<MafiaEntry> {
        val win = g.extra["winSide"] ?: MafiaFlow.winSideOf(g.winner)
        fun won(role: MafiaRole, side: MafiaSide): Boolean = when (win) {
            "CITY" -> side == MafiaSide.CITY
            "MAFIA" -> side == MafiaSide.MAFIA
            "CITY+NOSTRADAMUS" -> side == MafiaSide.CITY || role == MafiaRole.NOSTRADAMUS
            "MAFIA+NOSTRADAMUS" -> side == MafiaSide.MAFIA || role == MafiaRole.NOSTRADAMUS
            else -> role.name == win
        }
        val out = ArrayList<MafiaEntry>()
        g.stints.forEach { st -> out += MafiaEntry(st.name, st.role, st.side, won(st.role, st.side), false, st.deathType, st.toNight) }
        g.players.forEach { p ->
            val role = p.role ?: return@forEach
            val side = p.side ?: role.side
            out += MafiaEntry(p.name, role, side, won(role, side), p.alive, p.deathType, p.outNight)
        }
        return out
    }

    private fun addMafia(m: PersonMafia, e: MafiaEntry) {
        m.games++
        if (e.won) m.wins++
        m.sideGames[e.side] = (m.sideGames[e.side] ?: 0) + 1
        if (e.won) m.sideWins[e.side] = (m.sideWins[e.side] ?: 0) + 1
        m.roles[e.role] = (m.roles[e.role] ?: 0) + 1
        if (e.survived) m.survived++ else {
            m.deaths[e.deathType] = (m.deaths[e.deathType] ?: 0) + 1
            m.exitNights += e.outNight.coerceAtLeast(0)
            if (e.outNight <= 1) m.firstOut++
        }
    }

    /** Ranking of people for one card game, all card games (null), or mafia ([mafia] true). */
    fun leaderboard(all: Map<String, PersonRecord>, game: GameType?, mafia: Boolean, party: String? = null): List<PersonRecord> = when {
        party == PANTO -> all.values.filter { it.panto.games > 0 }
            .sortedWith(compareByDescending<PersonRecord> { it.panto.winRate }.thenByDescending { it.panto.wins }.thenByDescending { it.panto.points }.thenBy { it.name })
        party == SPY -> all.values.filter { it.spy.games > 0 }
            .sortedWith(compareByDescending<PersonRecord> { it.spy.winRate }.thenByDescending { it.spy.wins }.thenBy { it.name })
        mafia -> all.values.filter { it.mafia.games > 0 }
            .sortedWith(compareByDescending<PersonRecord> { it.mafia.winRate }.thenByDescending { it.mafia.wins }.thenBy { it.name })
        game != null -> all.values.filter { (it.byGame[game]?.games ?: 0) > 0 }
            .sortedWith(compareByDescending<PersonRecord> { it.byGame[game]!!.rating }.thenByDescending { it.byGame[game]!!.wins }.thenBy { it.name })
        else -> all.values.filter { it.totalGames > 0 }
            .sortedWith(compareByDescending<PersonRecord> { overall(it) }.thenByDescending { it.totalWins }.thenBy { it.name })
    }

    /** Overall strength: card-game ratings averaged by games played. */
    fun overall(r: PersonRecord): Double {
        val lines = r.byGame.values.filter { it.games > 0 }
        if (lines.isEmpty()) return StatsEngine.START
        return lines.sumOf { it.rating * it.games } / lines.sumOf { it.games }
    }
}
