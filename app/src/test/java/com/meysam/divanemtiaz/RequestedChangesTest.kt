package com.meysam.divanemtiaz

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RequestedChangesTest {
    @Test fun panelApiMigrationAcceptsOnlyCleanHttpsEndpoints() {
        assertTrue(StoreInfo.validApi("https://divan-emtiaz.ir/divan/api/index.php"))
        assertFalse(StoreInfo.validApi("https://user:password@example.org/api/index.php"))
        assertFalse(StoreInfo.validApi("http://example.org/api/index.php"))
        assertFalse(StoreInfo.validApi("https://example.org/api/index.php?redirect=evil"))
    }

    @Test fun previousPantoWordSurvivesResumeWithoutBecomingSelectable() {
        val bank = PantoBank(listOf(PantoWord(1, 1, 1, "اول"), PantoWord(2, 1, 1, "دوم")))
        val m = PantoEngine.create(77, PantoMode.NORMAL, listOf(PantoTeam("الف"), PantoTeam("ب")), 3, null)
        val used = hashSetOf<Int>()
        PantoEngine.startTurn(m, bank, emptyList(), used, PantoChoice(1, 1), Random(1))
        val first = m.turn!!.word
        assertTrue(PantoEngine.changeWord(m, bank, emptyList(), used, Random(2)))
        val restored = PantoCodec.decode(PantoCodec.encode(m))
        assertEquals(first, restored.turn!!.previousWord)
        assertNotEquals(first, restored.turn!!.word)
        assertFalse(PantoEngine.changeWord(restored, bank, emptyList(), used, Random(3)))
        assertEquals(first, restored.turn!!.previousWord)
    }

    @Test fun mafiaCanTargetItsOwnTeamOnlyWhenEnabled() {
        val g = MafiaGame(1)
        listOf(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.CITIZEN, MafiaRole.CITIZEN, MafiaRole.CITIZEN).forEachIndexed { i, r ->
            g.players += MafiaPlayer(i + 1, "p$i", 0).apply { role = r }
        }
        MafiaFlow.start(g, MafiaSettings(selfKill = true), Random(1))
        g.night = 1
        val group = MafiaNight.jobs(g).first { it.key == MafiaNight.MAFIA_GROUP }
        assertEquals(g.alive.map { it.id }.toSet(), MafiaNight.candidates(g, group, group.modes.first()).map { it.id }.toSet())
        g.extra["selfKill"] = "0"
        val disabled = MafiaNight.jobs(g).first { it.key == MafiaNight.MAFIA_GROUP }
        assertTrue(MafiaNight.candidates(g, disabled, disabled.modes.first()).all { it.side != MafiaSide.MAFIA })
        assertTrue(MafiaNight.rolePrompt(g, MafiaRole.JACK_SPARROW).contains("طلسم"))
    }

    @Test fun onlyTimerHasDefaultMafiaAudio() {
        assertEquals(setOf(MafiaCues.TIMER_TICK, MafiaCues.TIMER_END), MafiaCues.defaults().keys)
    }

    @Test fun extendingOrConvertingLeagueKeepsSavedGamesAndCodec() {
        val teams = listOf(Side("الف", 0), Side("ب", 1))
        val league = League(1, "تست", GameType.MENFI, teams, GameRules(), LeagueFormat.KNOCKOUT, LeagueSettings())
        LeagueEngine.start(league, listOf(0, 1), Random(1))
        league.matches[0] = league.matches[0].copy(games = listOf(100L))
        LeagueEngine.reconfigure(league, teams + listOf(Side("ج", 2), Side("د", 3)), LeagueSettings(format = LeagueFormat.ROUND_ROBIN))
        assertEquals(4, league.teams.size)
        assertEquals(LeagueFormat.ROUND_ROBIN, league.format)
        assertTrue(100L in league.pastGames)
        val pair = league.matches.first()
        league.matches[0] = pair.copy(games = listOf(101L))
        LeagueEngine.reconfigure(league, league.teams + Side("ه", 4), league.config)
        assertEquals(listOf(101L), league.matches.first { it.teamA == pair.teamA && it.teamB == pair.teamB }.games)
        val saved = GameCodec.decodeLeague(JSONObject(GameCodec.encodeLeague(league).toString()))
        assertEquals(league.pastGames, saved.pastGames)
        assertEquals(league.matches, saved.matches)
    }
}
