package com.meysam.divanemtiaz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {
    private val noJoker = ShalamRules()
    private val joker = ShalamSettings().rules(withJoker = true)

    @Test fun bidOptionsFollowReferencePicker() {
        assertEquals(80, ShalamEngine.bidOptions(noJoker.mode).first())
        assertEquals(165, ShalamEngine.bidOptions(noJoker.mode).last())
        assertEquals(200, ShalamEngine.bidOptions(joker.mode).last())
        assertEquals(160, ShalamEngine.takenOptions(noJoker.mode).last())
        assertEquals(135, ShalamEngine.normalizeTypedBid(35, noJoker.mode))
        assertEquals(80, ShalamEngine.normalizeTypedBid(80, noJoker.mode))
        assertEquals(180, ShalamEngine.normalizeTypedBid(80, joker.mode))
        assertFalse(ShalamEngine.isValidBid(45, noJoker.mode))
        assertFalse(ShalamEngine.isValidBid(170, noJoker.mode))
        assertFalse(ShalamEngine.isValidTaken(165, noJoker.mode))
    }

    @Test fun bid135FailsWhenOpponentTakes35() {
        val result = ShalamEngine.scoreHand(135, 35, noJoker)
        assertEquals(130, result.actual)
        assertEquals(-135, result.contractor)
        assertEquals(35, result.opponent)
        assertFalse(result.succeeded)
    }

    @Test fun bid135Wins150WhenOpponentTakes15() {
        val result = ShalamEngine.scoreHand(135, 15, noJoker)
        assertEquals(150, result.contractor)
        assertEquals(15, result.opponent)
        assertTrue(result.succeeded)
    }

    @Test fun contractOnlyModeAwardsDeclaredValue() {
        assertEquals(135, ShalamEngine.scoreHand(135, 15, noJoker.copy(contractOnly = true)).contractor)
    }

    @Test fun collectingAllPointsFollowsSelectedMode() {
        assertEquals(165, ShalamEngine.scoreHand(120, 0, noJoker).contractor)
        val double = noJoker.copy(mode = noJoker.mode.copy(collectAll = CollectAll.DOUBLE))
        assertEquals(240, ShalamEngine.scoreHand(120, 0, double).contractor)
        val custom = noJoker.copy(mode = noJoker.mode.copy(collectAll = CollectAll.CUSTOM, collectAllCustom = 200))
        assertEquals(200, ShalamEngine.scoreHand(120, 0, custom).contractor)
        val shelem = noJoker.copy(mode = noJoker.mode.copy(collectAll = CollectAll.SHELEM))
        val outcome = ShalamEngine.scoreHand(120, 0, shelem)
        assertEquals(ShalamResult.SHELEM_WIN, outcome.result)
        assertEquals(330, outcome.contractor)
        assertTrue(outcome.collectedAll)
        assertEquals(165, ShalamEngine.scoreHand(120, 0, noJoker.copy(contractOnly = true)).contractor)
    }

    @Test fun doubleAppliesOnlyWhenOpponentReachesLimit() {
        val negative = ShalamEngine.scoreHand(120, 90, noJoker)
        assertEquals(-240, negative.contractor)
        assertEquals(90, negative.opponent)
        val positive = ShalamEngine.scoreHand(120, 90, noJoker.copy(doubleType = DoubleType.POSITIVE))
        assertEquals(-120, positive.contractor)
        assertEquals(180, positive.opponent)
        val disabled = ShalamEngine.scoreHand(120, 90, noJoker.copy(doubleType = DoubleType.DISABLED))
        assertEquals(-120, disabled.contractor)
        assertEquals(90, disabled.opponent)
        val ask = ShalamEngine.scoreHand(120, 90, noJoker.copy(doubleType = DoubleType.ASK))
        assertTrue(ask.needsDoubleChoice)
        assertEquals(-240, ShalamEngine.scoreHand(120, 90, noJoker.copy(doubleType = DoubleType.ASK), DoubleChoice.NEGATIVE).contractor)
        val underLimit = ShalamEngine.scoreHand(160, 80, noJoker)
        assertEquals(-160, underLimit.contractor)
        assertFalse(underLimit.isDouble)
        assertTrue(ShalamEngine.scoreHand(150, 105, joker).isDouble)
        assertFalse(ShalamEngine.scoreHand(150, 100, joker).isDouble)
    }

    @Test fun shelemModesMatchReference() {
        fun rules(mode: Int) = noJoker.copy(mode = noJoker.mode.copy(shelemMode = mode))
        val contractor = rules(ShelemMode.CONTRACTOR)
        assertEquals(330 to 0, ShalamEngine.scoreShelem(false, 0, contractor).let { it.contractor to it.opponent })
        assertEquals(-330 to 0, ShalamEngine.scoreShelem(false, 20, contractor).let { it.contractor to it.opponent })
        val opponent = rules(ShelemMode.OPPONENT_NEGATIVE)
        assertEquals(0 to -330, ShalamEngine.scoreShelem(false, 0, opponent).let { it.contractor to it.opponent })
        assertEquals(0 to 330, ShalamEngine.scoreShelem(false, 20, opponent).let { it.contractor to it.opponent })
        val both = rules(ShelemMode.CONTRACTOR_AND_OPPONENT)
        assertEquals(330 to 0, ShalamEngine.scoreShelem(false, 0, both).let { it.contractor to it.opponent })
        assertEquals(-330 to 25, ShalamEngine.scoreShelem(false, 25, both).let { it.contractor to it.opponent })
        val custom = rules(ShelemMode.CUSTOM)
        assertEquals(165 to -165, ShalamEngine.scoreShelem(false, 0, custom).let { it.contractor to it.opponent })
        assertEquals(-165 to -165, ShalamEngine.scoreShelem(false, 10, custom).let { it.contractor to it.opponent })
        assertEquals(400, ShalamEngine.scoreShelem(false, 0, joker).contractor)
    }

    @Test fun doubleShelemDoublesOrUsesCustomPoints() {
        assertEquals(660, ShalamEngine.scoreShelem(true, 0, noJoker).contractor)
        assertEquals(-660, ShalamEngine.scoreShelem(true, 5, noJoker).contractor)
        val custom = noJoker.copy(mode = noJoker.mode.copy(doubleShelemMode = DoubleShelemMode.CUSTOM, doubleShelemWin = 700, doubleShelemLose = -500))
        assertEquals(700, ShalamEngine.scoreShelem(true, 0, custom).contractor)
        assertEquals(-500, ShalamEngine.scoreShelem(true, 5, custom).contractor)
        assertEquals(800, ShalamEngine.scoreShelem(true, 0, joker).contractor)
    }

    @Test fun teamAboveLimitScoresOnlyWhenContractorFails() {
        val limited = noJoker.copy(highLimitEnabled = true, highLimit = 1000)
        assertEquals(0, ShalamEngine.limitedOpponentScore(150, 15, 1000, limited))
        assertEquals(35, ShalamEngine.limitedOpponentScore(-135, 35, 1000, limited))
        assertEquals(0, ShalamEngine.limitedOpponentScore(-135, 35, 1000, limited.copy(loserPointsAboveLimit = false)))
        assertEquals(15, ShalamEngine.limitedOpponentScore(150, 15, 995, limited))
        assertEquals(15, ShalamEngine.limitedOpponentScore(150, 15, 1200, noJoker))
    }

    @Test fun shalamEndsAtTargetOrDifference() {
        assertFalse(ShalamEngine.isComplete(listOf(0, 0), noJoker))
        assertTrue(ShalamEngine.isComplete(listOf(1170, 300), noJoker))
        assertFalse(ShalamEngine.isComplete(listOf(1150, 40), noJoker))
        val byDiff = noJoker.copy(mode = noJoker.mode.copy(endWithDiff = true, endDiff = 1100))
        assertTrue(ShalamEngine.isComplete(listOf(1150, 40), byDiff))
        assertFalse(ShalamEngine.isComplete(listOf(500, 300), byDiff))
    }

    @Test fun positiveOnlyTwelveCardsNeverGoesNegative() {
        val r = noJoker.copy(dealType = DealType.TWELVE_POSITIVE_ONLY, doubleType = DoubleType.NEGATIVE)
        fun hand(bid: Int, took: Int) = ShalamEngine.scoreHand(bid, 165 - took, r).let { listOf(it.contractor, it.opponent) }
        assertEquals(listOf(120, 35), hand(120, 130))
        assertEquals(listOf(120, 45), hand(120, 120))
        assertEquals(listOf(0, 50), hand(120, 115))
        assertEquals(listOf(0, 85), hand(130, 80))
        assertEquals(listOf(0, 120), hand(160, 45))
        assertFalse(ShalamEngine.isDoubleHand(160, 120, r))
        assertEquals(listOf(330, 0), hand(120, 165))
        assertEquals(330, ShalamEngine.scoreShelem(false, 0, r).contractor)
        assertEquals(listOf(0, 10), ShalamEngine.scoreShelem(false, 10, r).let { listOf(it.contractor, it.opponent) })
        val session = GameSession(9L, GameType.SHALAM, listOf(Side("ما", 0), Side("اونا", 1)), rules = GameRules(shalam = r))
        session.rounds += Round(RoundKind.SHALAM_HAND, emptyList(), contractTeam = 0, bid = 120, taken = 35)
        session.rounds += Round(RoundKind.SHALAM_HAND, emptyList(), contractTeam = 0, bid = 130, taken = 85)
        session.rounds += Round(RoundKind.SHALAM_HAND, emptyList(), contractTeam = 1, bid = 150, taken = 60)
        GameEngine.recompute(session)
        assertEquals(listOf(listOf(120, 35), listOf(0, 85), listOf(60, 0)), session.rounds.map { it.scores })
        assertEquals(listOf(180, 120), GameEngine.totals(session))
        assertTrue(session.rounds.all { round -> round.scores.all { it >= 0 } })
    }

    @Test fun recomputeAppliesLimitWithRunningTotals() {
        val rules = GameRules(shalam = noJoker.copy(highLimitEnabled = true, highLimit = 100))
        val session = GameSession(1L, GameType.SHALAM, listOf(Side("الف", 0), Side("ب", 1)), rules = rules)
        session.rounds += Round(RoundKind.SHALAM_HAND, emptyList(), contractTeam = 0, bid = 120, taken = 40)
        session.rounds += Round(RoundKind.SHALAM_HAND, emptyList(), contractTeam = 1, bid = 100, taken = 30)
        session.rounds += Round(RoundKind.SHALAM_PASS, emptyList())
        GameEngine.recompute(session)
        assertEquals(listOf(125, 40), session.rounds[0].scores)
        assertEquals(listOf(0, 135), session.rounds[1].scores)
        assertEquals(listOf(125, 175), GameEngine.totals(session))
        assertEquals(listOf(1), GameEngine.winners(session))
        assertEquals(2, GameEngine.shalamStats(session).hands)
    }

    @Test fun menfiLegacyScoringIsKeptForOldGames() {
        val legacy = MenfiRules(scoring = MenfiScoring.LEGACY)
        val results = MenfiEngine.outcomes(3, 10, legacy).map { it.teamAScore to it.teamBScore }
        assertEquals(listOf(20 to 3, 20 to -3, -10 to 3), results)
        val custom = MenfiEngine.outcomes(3, 3, legacy.copy(threeSuccess = 25, threeFailure = -15))
        assertEquals(listOf(25 to 25, 25 to -15, -15 to 25), custom.map { it.teamAScore to it.teamBScore })
    }

    @Test fun menfiSixAndEightSumFourteenExactlyOneTeamMakesIt() {
        val results = MenfiEngine.outcomes(6, 8)
        assertEquals(listOf(MenfiEngine.A_ONLY, MenfiEngine.B_ONLY), results.map { it.index })
        assertEquals(listOf(6 to -8, -6 to 8), results.map { it.teamAScore to it.teamBScore })
    }

    @Test fun menfiFiveAndTenSumFifteenCanBothFail() {
        val results = MenfiEngine.outcomes(5, 10)
        assertEquals(listOf(MenfiEngine.A_ONLY, MenfiEngine.B_ONLY, MenfiEngine.NONE), results.map { it.index })
        assertEquals(listOf(5 to -10, -5 to 20, -5 to -10), results.map { it.teamAScore to it.teamBScore })
    }

    @Test fun menfiSumUpToThirteenAllowsBothToMakeIt() {
        val results = MenfiEngine.outcomes(3, 10)
        assertEquals(listOf(MenfiEngine.BOTH, MenfiEngine.A_ONLY, MenfiEngine.B_ONLY), results.map { it.index })
        assertEquals(listOf(3 to 20, 3 to -10, -3 to 20), results.map { it.teamAScore to it.teamBScore })
    }

    @Test fun menfiScoreTableIsConfigurableAndManualEntryWins() {
        val table = MenfiRules(success = MenfiScoring.defaultSuccess.toMutableList().also { it[11 - 3] = 30 }, failure = MenfiScoring.defaultFailure.toMutableList().also { it[11 - 3] = -15 })
        assertEquals(30, MenfiEngine.successScore(11, table))
        assertEquals(-15, MenfiEngine.failureScore(11, table))
        assertEquals(12, MenfiEngine.successScore(12, table))
        val manual = Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(4, 9), outcome = MenfiEngine.MANUAL, raw = listOf(-7, 25))
        assertEquals(listOf(-7, 25), MenfiEngine.scoreRound(manual, table))
    }

    @Test fun menfiWinnerAndHandLimitFollowSettings() {
        val session = GameSession(2L, GameType.MENFI, listOf(Side("الف", 0), Side("ب", 1)), rules = GameRules(menfi = MenfiRules(hands = 2)))
        session.rounds += Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(3, 10), outcome = 1)
        GameEngine.recompute(session)
        assertFalse(GameEngine.isComplete(session))
        session.rounds += Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(5, 8), outcome = 0)
        GameEngine.recompute(session)
        assertEquals(listOf(8, -2), GameEngine.totals(session))
        assertTrue(GameEngine.isComplete(session))
        assertEquals(listOf(0), GameEngine.winners(session))
        session.rules = session.rules.copy(menfi = session.rules.menfi.copy(highWins = false))
        assertEquals(listOf(1), GameEngine.winners(session))
    }

    @Test fun totalsAndWinnerUseEditedRoundValues() {
        val session = GameSession(3L, GameType.MENFI, listOf(Side("الف", 0), Side("ب", 1)), rules = GameRules())
        session.rounds += Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(3, 10), outcome = 0)
        session.rounds += Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(3, 10), outcome = 2)
        GameEngine.recompute(session)
        session.rounds[1] = session.rounds[1].copy(outcome = 1)
        GameEngine.recompute(session)
        assertEquals(listOf(6, 10), GameEngine.totals(session))
        assertEquals(listOf(1), GameEngine.winners(session))
    }

    @Test fun hezartaiiZeroPenaltyRankingAndEnd() {
        val rules = GameRules(hezar = HezarRules(target = 300, rounds = 3, zeroPenalty = -50))
        val players = listOf(Side("الف", 0), Side("ب", 1), Side("پ", 2))
        val session = GameSession(4L, GameType.HEZARTAII, players, rules = rules)
        session.rounds += Round(RoundKind.HEZAR_ROUND, emptyList(), raw = listOf(0, 120, 60))
        session.rounds += Round(RoundKind.HEZAR_ROUND, emptyList(), raw = listOf(100, -40, 90))
        GameEngine.recompute(session)
        assertEquals(listOf(-50, 120, 60), session.rounds[0].scores)
        assertEquals(listOf(50, 80, 150), GameEngine.totals(session))
        assertEquals(listOf(2, 1, 0), GameEngine.ranking(session))
        assertFalse(GameEngine.isComplete(session))
        session.rounds += Round(RoundKind.HEZAR_ROUND, emptyList(), raw = listOf(10, 10, 10))
        GameEngine.recompute(session)
        assertTrue(GameEngine.isComplete(session))
    }

    @Test fun penaltiesAndAdjustmentsDoNotCountAsHands() {
        val session = GameSession(5L, GameType.SHALAM, listOf(Side("الف", 0), Side("ب", 1)), rules = GameRules())
        session.rounds += Round(RoundKind.PENALTY, emptyList(), raw = listOf(-50, 0))
        session.rounds += Round(RoundKind.ADJUST, emptyList(), raw = listOf(10, 20))
        GameEngine.recompute(session)
        assertEquals(listOf(-40, 20), GameEngine.totals(session))
        assertEquals(0, GameEngine.playedHands(session))
    }

    @Test fun persianTextFormatsAndParsesDigits() {
        assertEquals("۱۶۵", PersianText.digits(165))
        assertEquals("‎+۲۰‎", PersianText.signed(20))
        assertEquals("‎−۳‎", PersianText.signed(-3))
        assertEquals(-135, PersianText.parseInt("−۱۳۵"))
        assertEquals(42, PersianText.parseInt("٤٢"))
    }
}
