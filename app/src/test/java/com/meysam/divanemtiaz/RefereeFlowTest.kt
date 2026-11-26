package com.meysam.divanemtiaz

import org.junit.Assert.*
import org.junit.Test

class RefereeFlowTest {
    private val a = Side("تیم یک", 0, listOf("رضا", "مصطفی"))
    private val b = Side("تیم دو", 1, listOf("بهروز", "بهزاد"))

    @Test fun seatsOppositeRotateAndOtherTeamBidsFirstEvenAfterReload() {
        val seats = listOf(0, 2, 1, 3)
        val deal = MenfiDeal.fromSeats(seats, first = 3, direction = MenfiDirection.COUNTERCLOCKWISE)
        assertEquals(listOf(3, 0, 2, 1, 3, 0, 2, 1), (0..7).map(deal::dealer))
        assertEquals(listOf(0, 1), deal.biddingOrder(0))
        assertEquals(listOf(1, 0), deal.biddingOrder(1))
        val game = GameSession(35L, GameType.MENFI, listOf(a, b), rules = GameRules(), leagueId = 13L, menfiDeal = deal)
        val payload = GameCodec.encodeSession(game)
        val restored = GameCodec.decodeSession(payload)
        assertEquals(deal, restored.menfiDeal)
        assertEquals(13L, restored.leagueId)
        payload.remove("menfiDeal")
        assertNull(GameCodec.decodeSession(payload).menfiDeal)
        assertNull(MenfiDeal.restore(listOf(0, 1, 2, 3), seats))
    }

    @Test fun clockwiseIsDefaultAndRecordedDealerSurvivesChangingDirection() {
        val seats = MenfiDeal.defaultSeats
        val default = MenfiDeal.fromSeats(seats, first = 1)
        val reverse = MenfiDeal.fromSeats(seats, first = 1, direction = MenfiDirection.COUNTERCLOCKWISE)
        assertEquals(MenfiDirection.CLOCKWISE, default.direction)
        assertEquals(listOf(1, 2, 0, 3, 1), (0..4).map(default::dealer))
        assertEquals(listOf(1, 3, 0, 2, 1), (0..4).map(reverse::dealer))
        val hand = Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(7, 6), outcome = MenfiEngine.A_ONLY,
            dealerName = "مصطفی", dealerTeam = 0)
        val session = GameSession(36L, GameType.MENFI, listOf(a, b), listOf(hand), GameRules(), menfiDeal = reverse)
        val restored = GameCodec.decodeSession(GameCodec.encodeSession(session))
        GameEngine.recompute(restored)
        assertEquals(reverse, restored.menfiDeal)
        assertEquals("مصطفی", restored.rounds.single().dealerName)
        assertEquals(0, restored.rounds.single().dealerTeam)
        assertEquals(listOf(7, -6), restored.rounds.single().scores)
        assertTrue(GameCodec.decodeRound(org.json.JSONObject().put("teamA", 7)).dealerName.isBlank())
    }

    @Test fun previewUsesActualRulesAndNeverChangesTheSavedHands() {
        val game = GameSession(1L, GameType.MENFI, listOf(a, b),
            List(7) { if (it == 0) Round(RoundKind.FIXED, listOf(34, 35)) else Round(RoundKind.FIXED, listOf(0, 0)) }, GameRules())
        fun round(outcome: Int) = Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(8, 3), outcome = outcome)
        assertEquals(listOf(42, 38), MenfiEngine.project(game, round(MenfiEngine.BOTH)).totals)
        assertEquals(listOf(42, 32), MenfiEngine.project(game, round(MenfiEngine.A_ONLY)).totals)
        assertEquals(listOf(0), MenfiEngine.project(game, round(MenfiEngine.BOTH)).winners)
        assertEquals(7, game.rounds.size)
        assertEquals(listOf(34, 35), GameEngine.totals(game))
        game.rules = game.rules.copy(menfi = game.rules.menfi.copy(success = MenfiScoring.defaultSuccess.toMutableList().apply { this[5] = 33 }))
        assertEquals(listOf(67, 38), MenfiEngine.project(game, round(MenfiEngine.BOTH)).totals)
        game.rounds += round(MenfiEngine.A_ONLY)
        GameEngine.recompute(game)
        assertEquals(listOf(67, 38), MenfiEngine.project(game, round(MenfiEngine.BOTH), 7).totals)
        assertEquals(listOf(67, 32), GameEngine.totals(game))
    }

    @Test fun reversedPartnersHaveOneRankAndCanBeHiddenWithoutDeletingGames() {
        val reversed = Side("مصطفی و رضا", 0, a.members.reversed())
        assertEquals(TeamIdentity.key(a), TeamIdentity.key(reversed))
        assertFalse(TeamIdentity.validCardTeam(a.copy(members = listOf("رضا", "مصطفی", "علی"))))
        assertFalse(TeamIdentity.validCardTeam(a.copy(members = listOf("رضا", "رضا"))))
        val store = object : KeyValueStore {
            private val data = mutableMapOf<String, String>()
            override fun get(key: String): String? = data[key]
            override fun write(changes: Map<String, String?>) {
                changes.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
            }
        }
        val repo = GameRepository(store)
        val first = RosterEntry(10L, a.name, a.avatar, true, a.members)
        val second = first.copy(id = 11L, name = reversed.name, members = reversed.members)
        repo.saveRoster(listOf(first, second))
        assertEquals(listOf(first), repo.roster())
        for ((i, side) in listOf(a, reversed).withIndex()) {
            repo.save(GameSession(i + 20L, GameType.MENFI, listOf(side, b),
                listOf(Round(RoundKind.FIXED, listOf(3, -3))), GameRules(), finished = true))
        }
        val stats = StatsEngine.leaderboard(repo.sessions(), true)
        assertEquals(2, stats.size)
        assertEquals(2, stats.first { it.id.rankingKey == TeamIdentity.key(a) }.games)
        repo.removeFromRoster(first)
        assertTrue(TeamIdentity.key(a) in repo.hiddenRanks())
        assertEquals(2, repo.sessions().size)
        assertTrue(StatsEngine.records(repo.sessions(), repo.hiddenRanks().keys) { it.toString() }.none { it.holder == a.name })
        repo.restoreRank(TeamIdentity.key(a))
        assertTrue(repo.hiddenRanks().isEmpty())
    }

    @Test fun freeMenfiAndNineProColorsWorkWithAnOldServerConfig() {
        val before = ProState.configJson
        try {
            ProState.configJson = """{"features":{"menfi":{"mode":"pro"},"leagues":{"mode":"free"},"rankings":{"mode":"free"},"themes":{"mode":"free"}}}"""
            assertTrue(ProState.access("menfi").allowed)
            for (key in Features.newProModules) assertFalse(ProState.access(key).allowed)
            assertEquals(9, Themes.all.count { Gate.itemLocked("themes", it.id) })
            assertFalse(Gate.itemLocked("themes", "ivory"))
            assertFalse(Gate.itemLocked("themes", "iranic"))
        } finally {
            ProState.configJson = before
        }
    }

    @Test fun groupReadAloudTextNeverUsesTheShootersRealName() {
        val game = MafiaGame(1L).apply {
            night = 1
            players += MafiaPlayer(1, "نام محرمانه", 0).apply { role = MafiaRole.GODFATHER }
            repeat(4) { players += MafiaPlayer(it + 2, "شهروند", 0).apply { role = MafiaRole.CITIZEN } }
        }
        val job = MafiaNight.jobs(game).first { it.key == MafiaNight.MAFIA_GROUP }
        val said = MafiaNight.brief(game, job, job.modes.first())
        assertTrue(said.contains("صاحب تیر"))
        assertFalse(said.contains("نام محرمانه"))
        val sixth = MafiaNight.brief(game, job, job.modes.first { it.label == "حس ششم" })
        assertTrue(sixth.contains("نقش دقیقش را حدس بزن"))
        assertTrue(sixth.contains("جای شلیک"))
    }

    @Test fun matadorSummaryLeadsWithTheTaskAndFullTextIsKept() {
        val game = MafiaGame(1L)
        val role = MafiaRole.MATADOR
        val mode = JobMode("", TargetPool.NON_MAFIA, 1, 1)
        val prompt = MafiaNight.rolePrompt(game, role)
        val job = NightJob(role.name, role.faName, role, role.side, role.glyph, prompt, listOf(mode), emptyList())
        assertEquals("ماتادور، بیدار شو. یک شهروند را نشان بده تا توانایی شبش بسته شود.", MafiaNight.brief(game, job, mode))
        assertTrue(prompt.contains("انجام کار منع خواهد شد"))
        assertTrue(prompt.lines().size > 2)
    }
}
