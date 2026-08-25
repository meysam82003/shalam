package com.meysam.divanemtiaz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalRulesTest {
    private val settings = V3Settings(menfiMinBid = 3)

    @Test
    fun `sum 11 allows both made but not both failed`() {
        val options = MenfiRulesV4.outcomeOptions(8, 3, settings).associateBy { it.outcome }
        assertTrue(options.getValue(MenfiOutcomeV4.BOTH_MADE).enabled)
        assertFalse(options.getValue(MenfiOutcomeV4.BOTH_FAILED).enabled)
        assertTrue(options.getValue(MenfiOutcomeV4.A_FAILED_B_MADE).enabled)
        assertTrue(options.getValue(MenfiOutcomeV4.A_MADE_B_FAILED).enabled)
    }

    @Test
    fun `sum 14 forces exactly one side to make`() {
        val options = MenfiRulesV4.outcomeOptions(9, 5, settings).associateBy { it.outcome }
        assertFalse(options.getValue(MenfiOutcomeV4.BOTH_MADE).enabled)
        assertFalse(options.getValue(MenfiOutcomeV4.BOTH_FAILED).enabled)
        assertTrue(options.getValue(MenfiOutcomeV4.A_FAILED_B_MADE).enabled)
        assertTrue(options.getValue(MenfiOutcomeV4.A_MADE_B_FAILED).enabled)
    }

    @Test
    fun `sum 15 allows both failed but not both made`() {
        val options = MenfiRulesV4.outcomeOptions(10, 5, settings).associateBy { it.outcome }
        assertFalse(options.getValue(MenfiOutcomeV4.BOTH_MADE).enabled)
        assertTrue(options.getValue(MenfiOutcomeV4.BOTH_FAILED).enabled)
    }

    @Test
    fun `8 and 3 scoring belongs to that exact hand`() {
        val bothMade = MenfiRulesV4.score(8, 3, MenfiOutcomeV4.BOTH_MADE, settings)
        assertEquals(8, bothMade.teamAScore)
        assertEquals(3, bothMade.teamBScore)

        val aFailed = MenfiRulesV4.score(8, 3, MenfiOutcomeV4.A_FAILED_B_MADE, settings)
        assertEquals(-8, aFailed.teamAScore)
        assertEquals(3, aFailed.teamBScore)

        val bFailed = MenfiRulesV4.score(8, 3, MenfiOutcomeV4.A_MADE_B_FAILED, settings)
        assertEquals(8, bFailed.teamAScore)
        assertEquals(-3, bFailed.teamBScore)
    }

    @Test
    fun `10 and 5 can both fail and use minus declaration`() {
        val result = MenfiRulesV4.score(10, 5, MenfiOutcomeV4.BOTH_FAILED, settings)
        assertEquals(-10, result.teamAScore)
        assertEquals(-5, result.teamBScore)
    }

    @Test
    fun `menfi auto decision after target hands`() {
        val rounds = MutableList(8) { V3Round(mutableListOf(8, -3)) }
        assertEquals(MenfiMatchDecision.TEAM_A_WINS, MenfiRulesV4.matchDecision(rounds, 8))
        assertEquals(MenfiMatchDecision.CONTINUE, MenfiRulesV4.matchDecision(rounds.take(7), 8))
    }

    @Test
    fun `recovered shalam totals are 165 and 200`() {
        assertEquals(165, ShalamReferenceEngine.total(V3Settings(shalamWithJoker = false)))
        assertEquals(200, ShalamReferenceEngine.total(V3Settings(shalamWithJoker = true)))
    }

    @Test
    fun `standard shelem is 330 without joker and 400 with joker`() {
        val normal = ShalamReferenceEngine.score(165, 0, V3Settings(shalamWithJoker = false), declaredShalam = true)
        assertEquals(330, normal.contractScore)
        val joker = ShalamReferenceEngine.score(200, 0, V3Settings(shalamWithJoker = true), declaredShalam = true)
        assertEquals(400, joker.contractScore)
    }

    @Test
    fun `hezartaii accepts 5 through 60 players`() {
        HezartaiiRulesV4.validatePlayers((1..5).map { V3Player("p$it") })
        HezartaiiRulesV4.validatePlayers((1..60).map { V3Player("p$it") })
    }
}
