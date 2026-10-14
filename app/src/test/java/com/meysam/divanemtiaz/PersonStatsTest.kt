package com.meysam.divanemtiaz

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PersonStatsTest {
    private fun shalam(id: Long, a: Side, b: Side, winA: Boolean): GameSession {
        val s = GameSession(id, GameType.SHALAM, listOf(a, b), rules = GameRules(), finished = true, endedAt = id)
        s.rounds += Round(RoundKind.FIXED, if (winA) listOf(1200, 300) else listOf(300, 1200))
        return s
    }

    @Test
    fun teamGamesCountForEveryMemberWithPartners() {
        val t1 = Side("شیرها", 0, listOf("علی", "رضا"))
        val t2 = Side("عقاب‌ها", 1, listOf("سارا", "مریم"))
        val t3 = Side("ستاره‌ها", 2, listOf("علی", "مریم"))
        val sessions = listOf(shalam(1, t1, t2, true), shalam(2, t3, t2.copy(members = listOf("سارا", "رضا")), true), shalam(3, t1, t2, false))
        val all = PersonStats.compute(sessions, emptyList())
        val ali = all.getValue("علی")
        assertEquals(3, ali.byGame.getValue(GameType.SHALAM).games)
        assertEquals(2, ali.byGame.getValue(GameType.SHALAM).wins)
        assertEquals(2 to 1, ali.partners.getValue("رضا"))
        assertEquals(1 to 1, ali.partners.getValue("مریم"))
        assertTrue(ali.byGame.getValue(GameType.SHALAM).rating > all.getValue("سارا").byGame.getValue(GameType.SHALAM).rating)
        // A team without members is not counted for anybody.
        assertEquals(3, PersonStats.compute(sessions + shalam(4, Side("بی‌نام", 3), t2, true), emptyList()).getValue("علی").cardGames)
    }

    @Test
    fun individualGamesUsePlayerNames() {
        val s = GameSession(5, GameType.HEZARTAII, listOf(Side("علی", 0), Side("سارا", 1), Side("رضا", 2)), rules = GameRules(), finished = true, endedAt = 5)
        s.rounds += Round(RoundKind.HEZAR_ROUND, emptyList(), raw = listOf(1000, 400, 200))
        GameEngine.recompute(s)
        val all = PersonStats.compute(listOf(s), emptyList())
        assertEquals(1, all.getValue("علی").byGame.getValue(GameType.HEZARTAII).wins)
        assertEquals(1, all.getValue("رضا").byGame.getValue(GameType.HEZARTAII).losses)
    }

    @Test
    fun mafiaAnalysisPerPersonIncludingSubstitutes() {
        val g = MafiaGame(9L)
        listOf("علی" to MafiaRole.GODFATHER, "رضا" to MafiaRole.DOCTOR, "سارا" to MafiaRole.CITIZEN, "مریم" to MafiaRole.CITIZEN, "نیما" to MafiaRole.DETECTIVE)
            .forEachIndexed { i, (n, r) -> g.players += MafiaPlayer(i + 1, n, 0).apply { role = r }; g.roleCounts[r] = (g.roleCounts[r] ?: 0) + 1 }
        MafiaFlow.start(g, MafiaSettings(), Random(1))
        g.night = 1; g.isDay = true
        MafiaFlow.exit(g, 3, MafiaDeath.VOTE)
        MafiaManual.substitute(g, 2, 3)
        MafiaFlow.exit(g, 1, MafiaDeath.VOTE)
        assertEquals("شهروندها برنده بازی شدند", MafiaFlow.checkWin(g))
        val all = PersonStats.compute(emptyList(), listOf(g))
        val sara = all.getValue("سارا").mafia
        assertEquals(2, sara.games)
        assertEquals(2, sara.wins)
        assertEquals(1, sara.roles.getValue(MafiaRole.DOCTOR))
        val ali = all.getValue("علی").mafia
        assertEquals(1, ali.sideGames.getValue(MafiaSide.MAFIA))
        assertEquals(0, ali.wins)
        assertEquals(1, ali.deaths.getValue(MafiaDeath.VOTE))
        assertEquals(1, all.getValue("رضا").mafia.deaths.getValue(MafiaDeath.LEFT))
        assertEquals("علی", PersonStats.leaderboard(all, null, true).last().name)
    }

    @Test
    fun membersSurviveStorage() {
        val s = shalam(7, Side("شیرها", 0, listOf("علی", "رضا")), Side("عقاب‌ها", 1), true)
        val back = GameCodec.decodeSession(GameCodec.encodeSession(s))
        assertEquals(listOf("علی", "رضا"), back.sides[0].members)
        assertTrue(back.sides[1].members.isEmpty())
        val roster = GameCodec.decodeRoster(GameCodec.encodeRoster(listOf(RosterEntry(1, "شیرها", 0, true, listOf("علی", "رضا")), RosterEntry(2, "علی", 1, false))))
        assertEquals(listOf("علی", "رضا"), roster[0].members)
        // Old data without members still loads.
        val old = GameCodec.decodeRoster(JSONArray("[{\"id\":1,\"name\":\"تیم\",\"avatar\":0,\"team\":true}]"))
        assertTrue(old[0].members.isEmpty())
    }
}
