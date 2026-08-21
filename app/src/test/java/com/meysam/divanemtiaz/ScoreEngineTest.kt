package com.meysam.divanemtiaz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {
    @Test fun shalamBidsAreExactlyReadyValues() {
        assertEquals("پاس", ShalamEngine.readyBids.first().title)
        assertEquals((100..160 step 5).toList(), ShalamEngine.readyBids.filter { it.kind == ShalamBidKind.CONTRACT }.map { it.value })
        assertEquals(listOf("شلم", "دبل‌شلم"), ShalamEngine.readyBids.takeLast(2).map { it.title })
    }

    @Test fun bid135FailsWhenOpponentTakes35() {
        val bid = ShalamBid(ShalamBidKind.CONTRACT, 135, "135")
        val result = ShalamEngine.calculate(bid, opponentPoints = 35)
        assertEquals(130, result.actualContractPoints)
        assertEquals(-135, result.contractScore)
        assertEquals(35, result.opponentScore)
        assertFalse(result.succeeded)
    }

    @Test fun bid135Wins150WhenOpponentTakes15() {
        val bid = ShalamBid(ShalamBidKind.CONTRACT, 135, "135")
        val result = ShalamEngine.calculate(bid, opponentPoints = 15)
        assertEquals(150, result.actualContractPoints)
        assertEquals(150, result.contractScore)
        assertEquals(15, result.opponentScore)
        assertTrue(result.succeeded)
    }

    @Test fun contractOnlyModeAwardsDeclaredValue() {
        val bid = ShalamBid(ShalamBidKind.CONTRACT, 135, "135")
        assertEquals(135, ShalamEngine.calculate(bid, 15, awardContractOnly = true).contractScore)
    }

    @Test fun menfiThreeAndTenExposeRequestedThreeResults() {
        val results = MenfiEngine.outcomes(3, 10).map { it.teamAScore to it.teamBScore }
        assertEquals(listOf(20 to 3, 20 to -3, -10 to 3), results)
    }

    @Test fun totalsAndWinnerUseEditedRoundValues() {
        val rounds = mutableListOf(ScoreRound(20, 3), ScoreRound(-10, 3))
        rounds[1] = rounds[1].copy(teamA = 20, teamB = -3)
        assertEquals(40, ScoreEngine.total(rounds, true))
        assertEquals(0, ScoreEngine.total(rounds, false))
        assertEquals(2, ScoreEngine.winner(GameType.MENFI, 40, 0))
    }
}
