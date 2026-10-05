package com.meysam.divanemtiaz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NewGamesTest {
    private fun players(n: Int) = List(n) { Side("بازیکن $it", it) }

    @Test fun doloMinimumFollowsPlayerCount() {
        val r = DoloRules()
        assertEquals(3, r.minimumFor(4))
        assertEquals(2, r.minimumFor(5))
        assertEquals(2, r.minimumFor(6))
        assertEquals(1, r.minimumFor(7))
        assertEquals(1, r.minimumFor(12))
        assertEquals(4, r.copy(minUpTo4 = 4).minimumFor(3))
    }

    @Test fun doloHandScoresMadeAndMissedDeclarations() {
        val round = Round(RoundKind.DOLO_HAND, emptyList(), numbers = listOf(2, 4, -1, 2), raw = listOf(1, 0, 0, 1))
        assertEquals(listOf(2, -4, 0, 2), DoloEngine.scoreRound(round, 4, DoloRules()))
        assertEquals(listOf(4, -4, 0, 4), DoloEngine.scoreRound(round, 4, DoloRules(madeMultiplier = 2)))
    }

    @Test fun doloEliminationIsDueAfterARoundAndTieGivesExtraHands() {
        val session = GameSession(10L, GameType.DOLO, players(3), rules = GameRules(dolo = DoloRules()))
        fun hand(made: List<Int>) = Round(RoundKind.DOLO_HAND, emptyList(), numbers = listOf(3, 3, 3), raw = made)
        session.rounds += hand(listOf(1, 1, 0))
        session.rounds += hand(listOf(1, 0, 1))
        GameEngine.recompute(session)
        assertFalse(DoloEngine.state(session).eliminationDue)
        session.rounds += hand(listOf(1, 1, 0))
        GameEngine.recompute(session)
        // totals: 9, 3, -3 → player 2 is the lowest, no tie
        assertEquals(listOf(9, 3, -3), GameEngine.totals(session))
        assertTrue(DoloEngine.state(session).eliminationDue)
        assertEquals(listOf(2) to false, DoloEngine.candidates(session))
        session.rounds += Round(RoundKind.DOLO_ELIM, emptyList(), raw = listOf(0, 0, 1))
        GameEngine.recompute(session)
        val st = DoloEngine.state(session)
        assertEquals(listOf(0, 1), st.active)
        assertEquals(2, st.handsDue)
        assertEquals(3, GameEngine.playedHands(session))
        // two hands later both have the same score → tie at the cut
        session.rounds += Round(RoundKind.DOLO_HAND, emptyList(), numbers = listOf(3, 3, -1), raw = listOf(0, 1, 0))
        session.rounds += Round(RoundKind.DOLO_HAND, emptyList(), numbers = listOf(3, 3, -1), raw = listOf(0, 1, 0))
        GameEngine.recompute(session)
        assertEquals(listOf(3, 9, -3), GameEngine.totals(session))
        assertEquals(listOf(0) to false, DoloEngine.candidates(session))
        session.rounds += Round(RoundKind.DOLO_ELIM, emptyList(), raw = listOf(0, 0, 0), bid = 2)
        GameEngine.recompute(session)
        assertEquals(2, DoloEngine.state(session).handsDue)
        assertFalse(DoloEngine.state(session).eliminationDue)
        session.rounds += Round(RoundKind.DOLO_ELIM, emptyList(), raw = listOf(1, 0, 0))
        GameEngine.recompute(session)
        assertTrue(GameEngine.isComplete(session))
        assertEquals(listOf(1), GameEngine.winners(session))
        assertEquals(listOf(1, 0, 2), GameEngine.ranking(session))
    }

    @Test fun doloTieAtTheCutIsDetected() {
        val session = GameSession(11L, GameType.DOLO, players(4), rules = GameRules())
        session.rounds += Round(RoundKind.DOLO_HAND, emptyList(), numbers = listOf(3, 3, 3, 3), raw = listOf(1, 1, 0, 0))
        GameEngine.recompute(session)
        assertEquals(true, DoloEngine.candidates(session).second)
    }

    @Test fun deckCalculatorGivesWholeCards() {
        val dolo = DeckCalc.dolo(6, 75, DoloRules())
        assertEquals(12, dolo.perPlayer)
        assertEquals(3, dolo.removeCards)
        assertEquals(2, dolo.minimum)
        val deal = DeckCalc.hezar(4, 2, 4, DeckSettings(), CardValues())
        assertEquals(108, deal.totalCards)
        assertEquals(listOf(6, 4, 4), deal.packets)
        assertEquals(56, deal.dealt)
        assertEquals(52, deal.stock)
        assertTrue(deal.fits)
        val tight = DeckCalc.hezar(8, 2, 0, DeckSettings(), CardValues())
        assertEquals(8, tight.short)
        assertFalse(tight.fixableFromBottom)
        assertEquals(3, tight.decksNeeded)
        val near = DeckCalc.hezar(4, 1, 0, DeckSettings(), CardValues())
        assertEquals(4, near.short)
        assertTrue(near.fixableFromBottom)
        assertEquals(listOf(5), DeckCalc.packets(5, 6, 4))
        assertEquals(listOf(6, 4, 4, 1), DeckCalc.packets(15, 6, 4))
    }

    @Test fun cardPointsFollowHezartaiiValues() {
        assertEquals(400, CardCalc.deckPoints(1, 0, CardValues()))
        assertEquals(960, CardCalc.deckPoints(2, 4, CardValues()))
        assertEquals(3 * 5 + 2 * 10 + 20 + 40, CardCalc.points(CardCount(3, 2, 1, 1), CardValues()))
    }

    private fun finishedGame(id: Long, a: Int, b: Int): GameSession =
        GameSession(id, GameType.MENFI, players(2), listOf(Round(RoundKind.FIXED, listOf(a, b))), GameRules(), finished = true)

    @Test fun knockoutNeedsTwoWinsAndOneAllGoesToADecider() {
        val league = League(1L, "لیگ", GameType.MENFI, players(4), GameRules(), LeagueFormat.KNOCKOUT, LeagueSettings())
        LeagueEngine.start(league, listOf(0, 1, 2, 3), Random(1))
        assertEquals(2, league.matches.size)
        val games = mutableMapOf<Long, GameSession>()
        val lookup: (Long) -> GameSession? = { games[it] }
        fun play(matchIndex: Int, id: Long, a: Int, b: Int) {
            games[id] = finishedGame(id, a, b)
            val m = league.matches[matchIndex]
            league.matches[matchIndex] = m.copy(games = m.games + id)
        }
        play(0, 100, 10, 5)
        play(0, 101, 3, 8)
        val tied = LeagueEngine.state(league, league.matches[0], lookup)
        assertEquals(1, tied.winsA)
        assertEquals(1, tied.winsB)
        assertTrue(tied.isDecider)
        assertNull(tied.winner)
        play(0, 102, 9, 9)
        assertEquals(1, LeagueEngine.state(league, league.matches[0], lookup).draws)
        assertNull(LeagueEngine.state(league, league.matches[0], lookup).winner)
        play(0, 103, 2, 7)
        assertEquals(1, LeagueEngine.state(league, league.matches[0], lookup).winner)
        assertFalse(LeagueEngine.advance(league, lookup))
        play(1, 104, 6, 1)
        play(1, 105, 6, 1)
        assertTrue(LeagueEngine.advance(league, lookup))
        val final = league.matches.last()
        assertTrue(final.isFinal)
        val fs = LeagueEngine.state(league, final, lookup)
        assertEquals(1, fs.teamA)
        assertEquals(2, fs.teamB)
        play(2, 106, 1, 4)
        play(2, 107, 1, 4)
        assertTrue(LeagueEngine.advance(league, lookup))
        assertTrue(league.finished)
        assertEquals(2, LeagueEngine.champion(league, lookup))
    }

    @Test fun knockoutWithOddTeamsGivesOneByeAndNeverTwiceInARow() {
        val league = League(2L, "لیگ", GameType.SHALAM, players(5), GameRules(), LeagueFormat.KNOCKOUT, LeagueSettings(winsNeeded = 1))
        LeagueEngine.start(league, listOf(0, 1, 2, 3, 4), Random(7))
        assertEquals(1, league.matches.count { it.isBye })
        val games = mutableMapOf<Long, GameSession>()
        val lookup: (Long) -> GameSession? = { games[it] }
        var id = 200L
        fun decideStage() {
            val stage = league.matches.maxOf { it.stage }
            league.matches.indices.filter { league.matches[it].stage == stage && !league.matches[it].isBye }.forEach { i ->
                games[id] = finishedGame(id, 10, 1)
                league.matches[i] = league.matches[i].copy(games = league.matches[i].games + id)
                id++
            }
        }
        val firstBye = LeagueEngine.state(league, league.matches.first { it.isBye }, lookup).winner
        decideStage()
        assertTrue(LeagueEngine.advance(league, lookup))
        val secondBye = league.matches.filter { it.stage == 2 && it.isBye }.map { LeagueEngine.state(league, it, lookup).winner }
        assertEquals(1, secondBye.size)
        assertTrue(secondBye.single() != firstBye)
        while (!league.finished) {
            decideStage()
            LeagueEngine.advance(league, lookup)
        }
        assertTrue(LeagueEngine.champion(league, lookup) != null)
    }

    @Test fun roundRobinTableAndFinal() {
        val league = League(3L, "لیگ", GameType.MENFI, players(4), GameRules(), LeagueFormat.ROUND_ROBIN, LeagueSettings())
        LeagueEngine.start(league, listOf(0, 1, 2, 3))
        assertEquals(6, league.matches.size)
        assertEquals(3, league.matches.map { it.stage }.distinct().size)
        league.matches.groupBy { it.stage }.values.forEach { round ->
            val teams = round.flatMap { listOf(it.teamA, it.teamB) }
            assertEquals(teams.size, teams.distinct().size)
        }
        val games = mutableMapOf<Long, GameSession>()
        val lookup: (Long) -> GameSession? = { games[it] }
        var id = 300L
        league.matches.indices.forEach { i ->
            val m = league.matches[i]
            val (a, b) = if (m.teamA < m.teamB) 10 to 0 else 0 to 10
            games[id] = finishedGame(id, a, b)
            league.matches[i] = m.copy(games = listOf(id))
            id++
        }
        val table = LeagueEngine.standings(league, lookup)
        assertEquals(listOf(0, 1, 2, 3), table.map { it.team })
        assertEquals(9, table[0].leaguePoints)
        assertTrue(LeagueEngine.advance(league, lookup))
        val final = league.matches.last()
        assertTrue(final.isFinal)
        assertEquals(0 to 1, final.teamA to final.teamB)
        assertEquals(2, final.winsNeeded)
    }

    @Test fun roundRobinDoubleLegSwapsSides() {
        val rounds = LeagueEngine.roundRobin(listOf(0, 1, 2), true)
        val pairs = rounds.flatten()
        assertEquals(6, pairs.size)
        assertEquals(pairs.size, pairs.distinct().size)
    }

    @Test fun storageKeepsOldMenfiScoringAndRoundTripsNewData() {
        val old = org.json.JSONObject("""{"id":5,"game":"menfi","rules":{"menfi":{"hands":8,"hidden":true,"threeSuccess":20,"threeFailure":-10,"highWins":true}},"sides":[{"name":"الف"},{"name":"ب"}],"rounds":[]}""")
        assertEquals(MenfiScoring.LEGACY, GameCodec.decodeSession(old).rules.menfi.scoring)
        val fresh = GameSession(6L, GameType.DOLO, players(4), rules = GameRules(dolo = DoloRules(minUpTo4 = 4)), leagueId = 9L)
        val back = GameCodec.decodeSession(GameCodec.encodeSession(fresh))
        assertEquals(4, back.rules.dolo.minUpTo4)
        assertEquals(9L, back.leagueId)
        assertEquals(MenfiScoring.TABLE, back.rules.menfi.scoring)
        val league = League(7L, "لیگ", GameType.MENFI, players(3), GameRules(), LeagueFormat.KNOCKOUT, LeagueSettings())
        LeagueEngine.start(league, listOf(0, 1, 2), Random(3))
        val decoded = GameCodec.decodeLeague(GameCodec.encodeLeague(league))
        assertEquals(league.matches, decoded.matches)
        assertEquals(3, decoded.teams.size)
        val settings = AppSettings(general = GeneralSettings(uiScale = 85), deck = DeckSettings(decks = 3))
        val s2 = GameCodec.decodeSettings(GameCodec.encodeSettings(settings))
        assertEquals(85, s2.general.uiScale)
        assertEquals(3, s2.deck.decks)
    }

    @Test fun autoDealSplitsIntoRoundsAndAdaptsToCards() {
        assertEquals(listOf(6, 4, 4), DeckCalc.splitPackets(14))
        assertEquals(listOf(4, 4, 4), DeckCalc.splitPackets(12))
        assertEquals(listOf(5, 4, 4), DeckCalc.splitPackets(13))
        assertEquals(listOf(4, 3, 3), DeckCalc.splitPackets(10))
        assertEquals(listOf(4, 3), DeckCalc.splitPackets(7))
        assertEquals(listOf(3), DeckCalc.splitPackets(3))
        val nine = DeckCalc.hezarAuto(9, DeckSettings(decks = 2, jokers = 6))
        assertEquals(110, nine.totalCards)
        assertEquals(DealMode.REDUCED, nine.mode)
        assertEquals(12, nine.handSize)
        assertEquals(listOf(4, 4, 4), nine.packets)
        assertEquals(2, nine.stock)
        assertEquals(3, nine.decksNeeded)
        val four = DeckCalc.hezarAuto(4, DeckSettings())
        assertEquals(DealMode.FITS, four.mode)
        assertEquals(listOf(6, 4, 4), four.packets)
        assertEquals(52, four.stock)
        val bottom = DeckCalc.hezarAuto(4, DeckSettings(decks = 1, jokers = 0))
        assertEquals(DealMode.BOTTOM, bottom.mode)
        assertEquals(4, bottom.short)
        val custom = DeckCalc.hezarAuto(5, DeckSettings(customTotalEnabled = true, customTotal = 80))
        assertEquals(80, custom.totalCards)
        assertEquals(14, custom.handSize)
        assertEquals(10, custom.stock)
    }

    @Test fun menfiTieAtTheEndAddsExtraHands() {
        val session = GameSession(20L, GameType.MENFI, players(2), rules = GameRules(menfi = MenfiRules(hands = 2)))
        session.rounds += Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(5, 5), outcome = MenfiEngine.A_ONLY)
        session.rounds += Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(5, 5), outcome = MenfiEngine.B_ONLY)
        GameEngine.recompute(session)
        assertEquals(listOf(0, 0), GameEngine.totals(session))
        assertFalse(GameEngine.isComplete(session))
        assertTrue(MenfiEngine.tiedAtEnd(2, listOf(0, 0), session.rules.menfi))
        session.rules = session.rules.copy(menfi = session.rules.menfi.copy(extraHands = 2))
        assertEquals(4, session.rules.menfi.totalHands)
        session.rounds += Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(3, 3), outcome = MenfiEngine.BOTH)
        session.rounds += Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(3, 4), outcome = MenfiEngine.BOTH)
        GameEngine.recompute(session)
        assertTrue(GameEngine.isComplete(session))
        assertEquals(listOf(1), GameEngine.winners(session))
        session.rules = session.rules.copy(menfi = session.rules.menfi.copy(tieBreak = false, extraHands = 0))
        session.rounds.removeAt(3)
        session.rounds.removeAt(2)
        GameEngine.recompute(session)
        assertTrue(GameEngine.isComplete(session))
    }

    @Test fun eloRatingsHeadToHeadAndRecords() {
        val a = Side("الف", 0)
        val b = Side("ب", 1)
        val g1 = GameSession(30L, GameType.MENFI, listOf(a, b), listOf(Round(RoundKind.FIXED, listOf(10, 3))), GameRules(), finished = true, endedAt = 100L)
        val stats = StatsEngine.compute(listOf(g1))
        assertEquals(1016L, Math.round(stats.getValue(Identity("الف", true)).rating))
        assertEquals(984L, Math.round(stats.getValue(Identity("ب", true)).rating))
        val g2 = GameSession(31L, GameType.MENFI, listOf(b, a), listOf(Round(RoundKind.FIXED, listOf(4, 4))), GameRules(), finished = true, endedAt = 200L)
        val s2 = StatsEngine.compute(listOf(g2, g1))
        val pa = s2.getValue(Identity("الف", true))
        assertEquals(2, pa.games)
        assertEquals(1, pa.wins)
        assertEquals(1, pa.draws)
        assertEquals(14 to 7, pa.pointsFor to pa.pointsAgainst)
        val h = StatsEngine.headToHead(pa).single()
        assertEquals(listOf(2, 1, 1, 0), listOf(h.games, h.wins, h.draws, h.losses))
        val multi = GameSession(32L, GameType.HEZARTAII, players(3), listOf(Round(RoundKind.FIXED, listOf(300, 100, 200))), GameRules(), finished = true, endedAt = 300L)
        val m = StatsEngine.compute(listOf(multi))
        assertEquals(1016L, Math.round(m.getValue(Identity("بازیکن 0", false)).rating))
        assertEquals(984L, Math.round(m.getValue(Identity("بازیکن 1", false)).rating))
        assertEquals(1000L, Math.round(m.getValue(Identity("بازیکن 2", false)).rating))
        assertEquals("حریف قوی‌تر", StatsEngine.strengthLabel(1000.0, 1100.0))
        assertEquals(listOf(listOf(0, 10), listOf(0, 3)), StatsEngine.progression(g1))
        assertTrue(StatsEngine.records(listOf(g1, g2, multi)) { it.toString() }.any { it.title == "بزرگ‌ترین اختلاف برد" && it.holder == "الف" })
    }

    private class MemoryStore : KeyValueStore {
        val map = linkedMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun write(changes: Map<String, String?>) {
            changes.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }

    @Test fun backupRoundTripAndMergeWithoutDuplicates() {
        val source = GameRepository(MemoryStore())
        source.saveSettings(AppSettings(general = GeneralSettings(defaultTeam1 = "شیران")))
        source.saveRoster(listOf(RosterEntry(1L, "شیران", 2, true)))
        val game = GameSession(40L, GameType.SHALAM, listOf(Side("شیران", 2), Side("عقاب", 3)), listOf(Round(RoundKind.FIXED, listOf(100, 50))), GameRules(), finished = true)
        source.save(game)
        val league = League(41L, "لیگ", GameType.MENFI, players(2), GameRules(), LeagueFormat.KNOCKOUT, LeagueSettings())
        LeagueEngine.start(league, listOf(0, 1))
        source.saveLeague(league)
        val backup = org.json.JSONObject(source.exportBackup().toString())
        val target = GameRepository(MemoryStore())
        target.saveRoster(listOf(RosterEntry(9L, "پلنگ", 1, true)))
        val first = target.importBackup(backup, replace = false)
        assertEquals(1, first.sessionsAdded)
        assertEquals(1, first.leaguesAdded)
        assertEquals(listOf(100, 50), GameEngine.totals(target.session(40L)!!))
        assertEquals(setOf("پلنگ", "شیران"), target.roster().map { it.name }.toSet())
        assertEquals("ما", target.settings().general.defaultTeam1)
        val again = target.importBackup(backup, replace = false)
        assertEquals(0, again.sessionsAdded)
        assertEquals(1, target.sessions().size)
        assertEquals(1, target.leagues().size)
        assertEquals(2, target.roster().size)
        target.importBackup(backup, replace = true)
        assertEquals(listOf("شیران"), target.roster().map { it.name })
        assertEquals("شیران", target.settings().general.defaultTeam1)
        assertEquals(league.matches, target.league(41L)!!.matches)
    }
}
