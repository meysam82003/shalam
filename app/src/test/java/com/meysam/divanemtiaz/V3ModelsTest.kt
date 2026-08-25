package com.meysam.divanemtiaz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3ModelsTest {
    private val settings = V3Settings()

    @Test fun shalam_success_uses_actual_by_default() {
        val result = ShalamEngineV3.score(120, 40, settings)
        assertTrue(result.succeeded)
        assertEquals(125, result.contractScore)
        assertEquals(40, result.opponentScore)
    }

    @Test fun shalam_failure_and_yasa_are_distinct() {
        assertEquals(-120, ShalamEngineV3.score(120, 60, settings).contractScore)
        val yasa = ShalamEngineV3.score(120, 90, settings)
        assertTrue(yasa.yasaApplied)
        assertEquals(-165, yasa.contractScore)
    }

    @Test fun full_shalam_uses_multiplier() {
        val result = ShalamEngineV3.score(165, 0, settings, true)
        assertTrue(result.shalamApplied)
        assertEquals(330, result.contractScore)
    }

    @Test fun menfi_base_and_overrides_are_configurable() {
        assertEquals(20, MenfiEngineV3.scoreForBid(3, true, settings))
        assertEquals(-10, MenfiEngineV3.scoreForBid(3, false, settings))
        val custom = settings.copy(menfiWin11 = 75, menfiLoss11 = -90)
        assertEquals(75, MenfiEngineV3.scoreForBid(11, true, custom))
        assertEquals(-90, MenfiEngineV3.scoreForBid(11, false, custom))
    }

    @Test fun hezartaii_zero_penalty_and_ranking() {
        assertEquals(-50, HezartaiiEngineV3.normalize(0, settings))
        val players = listOf(V3Player("الف"), V3Player("ب"), V3Player("ج"), V3Player("د"), V3Player("ه"))
        val rounds = listOf(
            V3Round(mutableListOf(10, 20, -50, -5, 30)),
            V3Round(mutableListOf(15, -50, 25, 10, 5))
        )
        assertEquals("ه", HezartaiiEngineV3.ranking(players, rounds).first().first.name)
    }

    @Test fun league_round_robin_has_all_pairs() {
        val pairs = LeaguePlannerV3.roundRobin(listOf(1, 2, 3, 4))
        assertEquals(6, pairs.size)
        assertTrue(pairs.contains(1L to 4L))
        assertFalse(pairs.contains(1L to 1L))
    }
}
