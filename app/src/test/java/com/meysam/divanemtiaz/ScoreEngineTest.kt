package com.meysam.divanemtiaz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {
    @Test fun totalsAllRounds() {
        val rounds = listOf(RoundScore(120, -20), RoundScore(80, 50))
        assertEquals(200, ScoreEngine.total(rounds, true))
        assertEquals(30, ScoreEngine.total(rounds, false))
    }

    @Test fun regularGamesPreferHigherScore() {
        assertEquals(1, ScoreEngine.winner(GameType.SHALAM, 200, 100))
        assertEquals(2, ScoreEngine.winner(GameType.HEZARTAII, 400, 700))
    }

    @Test fun menfiPrefersLowerScore() {
        assertEquals(1, ScoreEngine.winner(GameType.MENFI, -120, -20))
    }

    @Test fun targetOnlyAppliesToTargetGames() {
        assertTrue(ScoreEngine.reachedTarget(GameType.HEZARTAII, 1000, 800))
        assertFalse(ScoreEngine.reachedTarget(GameType.MENFI, 2000, 1000))
    }
}

