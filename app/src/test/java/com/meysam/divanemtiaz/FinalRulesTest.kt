package com.meysam.divanemtiaz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
    fun `outcome scoring uses configured positive and negative values`() {
        val result = MenfiRulesV4.score(8, 3, MenfiOutcomeV4.A_FAILED_B_MADE, settings)
        assertFalse(result.teamAMade)
        assertTrue(result.teamBMade)
        assertEquals(MenfiRulesV4.scoreForBid(8, false, settings), result.teamAScore)
        assertEquals(MenfiRulesV4.scoreForBid(3, true, settings), result.teamBScore)
    }

    @Test
    fun `hezartaii accepts 5 through 60 players`() {
        HezartaiiRulesV4.validatePlayers((1..5).map { V3Player("p$it") })
        HezartaiiRulesV4.validatePlayers((1..60).map { V3Player("p$it") })
    }
}
