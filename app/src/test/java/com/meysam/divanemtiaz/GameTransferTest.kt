package com.meysam.divanemtiaz

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GameTransferTest {
    private val a = Side("الف", 1, listOf("رضا", "مصطفی"))
    private val b = Side("ب", 2, listOf("بهروز", "بهزاد"))
    private val rules = GameRules()

    @Test fun leagueTransferPreservesTheOpenGameAndNamesWithoutDuplicating() {
        val source = MemoryStore()
        val dest = MemoryStore()
        val src = GameRepository(source)
        val destRepo = GameRepository(dest)
        val league = League(31L, "لیگ منفی", GameType.MENFI, listOf(Side("۱", 3), Side("۲", 4)),
            rules, LeagueFormat.KNOCKOUT, LeagueSettings(winsNeeded = 2))
        LeagueEngine.start(league, listOf(0, 1))
        val deal = MenfiDeal.fromSeats(MenfiDeal.defaultSeats, first = 1)
        val fixture = league.matches.single()
        val game = GameSession(42L, GameType.MENFI, listOf(a, b), rules = rules, menfiDeal = deal)
        game.rounds += Round(RoundKind.MENFI_HAND, listOf(4, 6), numbers = listOf(4, 6), outcome = MenfiEngine.BOTH,
            dealerName = "مصطفی", dealerTeam = 0)
        assertTrue(LeagueEngine.attach(league, fixture.id, game, false, src::session))
        assertEquals(listOf(a, b), game.sides)
        assertEquals(listOf(b, a), league.teams)
        assertEquals(deal, game.menfiDeal)
        src.save(game)
        src.saveLeague(league)

        val saved = GameSession(game.id, GameType.SHALAM, listOf(a, b), rules = rules)
        destRepo.save(saved)
        val transfer = GameTransfer(dest)
        val text = GameTransfer(source).export(TransferTarget(TransferKind.LEAGUE, league.id), false)
        val target = transfer.import(text, false)
        val imported = destRepo.league(target.id)!!
        val match = imported.matches.single()
        val resumed = destRepo.session(match.games.single())!!
        assertTrue(GameTransfer.importedLeague(dest, imported.id))
        assertTrue(resumed.id != saved.id)
        assertEquals(imported.id, resumed.leagueId)
        assertEquals(1, resumed.leagueSideA)
        assertEquals(listOf(a, b), resumed.sides)
        assertEquals(deal, resumed.menfiDeal)
        assertEquals(game.rounds, resumed.rounds)
        assertEquals(resumed.id, LeagueEngine.state(imported, match, destRepo::session).openGame)
        assertEquals("بهزاد", LeagueEngine.menfiArrangement(imported, match, imported.teams, destRepo::session)!!
            .let { arrangement -> imported.teams[arrangement.dealer(1) / 2].members[arrangement.dealer(1) % 2] })
        assertEquals(6, destRepo.roster().size)
        assertEquals(target, transfer.import(text, false))
        assertEquals(1, destRepo.leagues().size)
        assertEquals(2, destRepo.sessions().size)
        assertEquals(GameType.SHALAM, destRepo.session(saved.id)!!.game)

        val broken = JSONObject(text).put("sessions", org.json.JSONArray()).toString()
        val commits = dest.writes
        assertThrows(IllegalArgumentException::class.java) { transfer.import(broken, false) }
        assertEquals(commits, dest.writes)
    }

    @Test fun scoringGameTransfersWithoutALeagueLink() {
        val source = MemoryStore()
        val dest = MemoryStore()
        val repo = GameRepository(source)
        for (type in GameType.values()) {
            val sides = if (type.isTeamGame) listOf(a, b) else List(type.minSides) { Side("بازیکن $it", it) }
            val game = GameSession(42L, type, sides, rules = rules, leagueId = if (type.isTeamGame) 31L else 0L)
            repo.save(game)
            val text = GameTransfer(source).export(TransferTarget(TransferKind.GAME, game.id), false)
            val target = GameTransfer(dest).import(text, false)
            val restored = GameRepository(dest).session(target.id)!!
            assertEquals(type, restored.game)
            assertEquals(game.sides, restored.sides)
            assertEquals(0L, restored.leagueId)
        }
    }

    @Test fun partyGamesPreserveStateAndMafiaRequiresProOnBothEnds() {
        val source = MemoryStore()
        val dest = MemoryStore()
        val export = GameTransfer(source)
        val receiver = GameTransfer(dest)
        val panto = PantoMatch(61L).apply {
            teams += PantoTeam("الف", points = 8)
            teams += PantoTeam("ب", points = 2)
            current = 1
        }
        PantoRepository(source).save(panto)
        val p = receiver.import(export.export(TransferTarget(TransferKind.PANTO, panto.id), false), false)
        assertEquals(8, PantoRepository(dest).match(p.id)!!.teams[0].points)
        assertEquals(1, PantoRepository(dest).match(p.id)!!.current)

        val spy = SpyGame(62L).apply {
            players += listOf("رضا", "بهزاد", "مصطفی", "بهروز")
            spies += 1
            revealIndex = players.size
            remainingMs = 12_500
            named = true
        }
        SpyRepository(source).save(spy)
        val s = receiver.import(export.export(TransferTarget(TransferKind.SPY, spy.id), false), false)
        assertEquals(spy.remainingMs, SpyRepository(dest).game(s.id)!!.remainingMs)
        assertEquals(spy.spies, SpyRepository(dest).game(s.id)!!.spies)

        val mafia = MafiaGame(63L).apply {
            players += MafiaPlayer(1, "راوی", 1)
            night = 2
            snapshots += "شب اول" to MafiaCodec.encodeGame(this, withSnapshots = false).toString()
        }
        MafiaRepository(source).save(mafia)
        val ref = TransferTarget(TransferKind.MAFIA, mafia.id)
        assertThrows(IllegalArgumentException::class.java) { export.export(ref, false) }
        val text = export.export(ref, true)
        assertThrows(IllegalArgumentException::class.java) { receiver.import(text, false) }
        assertTrue(MafiaRepository(dest).index().isEmpty())
        val m = receiver.import(text, true)
        val restored = MafiaRepository(dest).game(m.id)!!
        assertEquals(2, restored.night)
        assertEquals(m.id, JSONObject(restored.snapshots.single().second).getLong("id"))
        assertNotNull(GameTransfer.mafiaContext(dest, m.id))
    }

    private class MemoryStore : KeyValueStore {
        private val data = mutableMapOf<String, String>()
        var writes = 0
        override fun get(key: String): String? = data[key]
        override fun write(changes: Map<String, String?>) {
            changes.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
            writes++
        }
    }
}
