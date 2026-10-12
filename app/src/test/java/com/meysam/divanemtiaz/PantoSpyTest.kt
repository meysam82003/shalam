package com.meysam.divanemtiaz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class PantoSpyTest {
    private val assets = listOf("app/src/main/assets", "../app/src/main/assets", "/home/user/shalam/app/src/main/assets").map { File(it) }.first { it.isDirectory }
    private val bank by lazy { File(assets, PantoBank.ASSET).inputStream().use { PantoBank.read(it) } }

    private fun teams(n: Int) = (1..n).map { PantoTeam("تیم $it") }

    private fun match(mode: PantoMode, n: Int = 2, rounds: Int = 3) = PantoEngine.create(1L, mode, teams(n), rounds, null)

    @Test fun bankKeepsEveryRecordAndCategory() {
        assertEquals(18017, bank.words.size)
        assertEquals(18017, bank.words.map { it.id }.toSet().size)
        (1..20).forEach { assertTrue("cat $it", bank.count(it) > 0) }
        assertEquals(931, bank.count(PantoCategories.FIVE_SECOND))
        assertEquals(260, bank.count(PantoCategories.SOUND))
        assertEquals(1114, bank.count(PantoCategories.GOLDEN))
        assertEquals(2178, bank.count(1))
        assertEquals(3121, bank.count(2))
        assertEquals(509, bank.count(20))
    }

    @Test fun drawsWithoutRepeatsUntilThePoolRunsOut() {
        val used = HashSet<Int>()
        val pool = bank.of(14, 3)
        val seen = (1..pool.size).map { PantoEngine.draw(pool, used, Random(it))!!.id }
        assertEquals(pool.size, seen.toSet().size)
        PantoEngine.draw(pool, used, Random(7))
        assertEquals(1, used.size) // the pool ran out: the list is cleared and drawing starts over
    }

    @Test fun subjectPointsAndTimes() {
        assertEquals(listOf(2, 4, 6), (1..3).map { PantoChoice(1, it).points })
        assertEquals(listOf(90, 105, 120), (1..3).map { PantoChoice(1, it).seconds })
        assertEquals(listOf(3, 5, 7), (1..3).map { PantoChoice(13, it).points })
        assertEquals(listOf(105, 120, 165), (1..3).map { PantoChoice(13, it).seconds })
        assertEquals(30, PantoChoice(50, 50).points)
        assertEquals(180, PantoChoice(50, 50).seconds)
        assertEquals(listOf(2, 4, 6), (1..3).map { PantoChoice(PantoCategories.USER, it).points })
    }

    @Test fun normalScoringAndThreeFaults() {
        assertEquals(4 - 1 + 2 - 1, PantoEngine.normalPoints(4, 1, 65, true))
        assertEquals(0, PantoEngine.normalPoints(6, 3, 100, false))
        assertEquals(0, PantoEngine.normalPoints(2, 2, 0, true))
        val m = match(PantoMode.NORMAL)
        PantoEngine.startTurn(m, bank, emptyList(), HashSet(), PantoChoice(1, 2), Random(1))
        assertNull(PantoEngine.fault(m)); assertNull(PantoEngine.fault(m))
        val end = PantoEngine.fault(m)!!
        assertEquals(0, end.points)
        assertNull(m.turn)
        assertEquals(1, m.current)
        // Each subject once per team.
        assertFalse(PantoEngine.options(m.apply { current = 0 }, emptyList()).first { it.choice == PantoChoice(1, 2) }.enabled)
    }

    @Test fun goldenSubjectMissedPutsTheTeamOut() {
        val m = match(PantoMode.NORMAL, 3)
        PantoEngine.startTurn(m, bank, emptyList(), HashSet(), PantoChoice(50, 50), Random(2))
        assertTrue(m.turn!!.word!!.text.isNotBlank())
        PantoEngine.incorrect(m)
        assertFalse(m.teams[0].inGame)
        assertEquals(1, m.current)
    }

    @Test fun speedyHasEightWordsAndSoundModeFive() {
        val s = match(PantoMode.SPEEDY)
        val t = PantoEngine.startTurn(s, bank, emptyList(), HashSet(), PantoChoice(8, 1), Random(3))!!
        assertEquals(8, t.words.size)
        assertEquals(8, t.words.map { it.id }.toSet().size)
        repeat(5) { PantoEngine.answer(s, true) }
        repeat(2) { PantoEngine.answer(s, false) }
        PantoEngine.fault(s)
        val end = PantoEngine.answer(s, true)!!
        assertEquals(6 * 2 - 1, end.points)

        val d = match(PantoMode.SEDA)
        val td = PantoEngine.startTurn(d, bank, emptyList(), HashSet(), null, Random(4))!!
        assertEquals(5, td.words.size)
        assertTrue(td.words.all { it.cat == PantoCategories.SOUND })
        assertEquals(60, td.seconds)
        repeat(3) { PantoEngine.fault(d) }
        assertEquals(0, d.teams[0].points)
    }

    @Test fun fiveSecondModeSixPlayersFiveSeconds() {
        val m = match(PantoMode.FIVE, 6)
        val t = PantoEngine.startTurn(m, bank, emptyList(), HashSet(), null, Random(5))!!
        assertEquals(5, t.seconds)
        assertTrue(t.word!!.text.startsWith("۳ تا"))
        PantoEngine.fiveAnswer(m, true)
        assertEquals(5, m.teams[0].points)
        assertEquals(1, m.current)
        assertTrue(runCatching { PantoEngine.create(2, PantoMode.FIVE, teams(7), 3, null) }.isFailure)
    }

    @Test fun battleEliminatesOneTeamEachRound() {
        val m = match(PantoMode.JANGI, 4)
        assertEquals(3, m.rounds)
        val used = HashSet<Int>()
        var t = PantoEngine.startTurn(m, bank, emptyList(), used, null, Random(6))!!
        assertEquals(120, t.seconds)
        assertTrue(t.words.first().text.length < 10)
        PantoEngine.jangiSaid(m, bank, used, Random(7))
        PantoEngine.jangiTimeUp(m)
        assertEquals(3, m.inside.size)
        assertFalse(m.teams[1].inGame)
        assertEquals(listOf(10, 0, 10, 10), m.teams.map { it.points })
        repeat(2) {
            t = PantoEngine.startTurn(m, bank, emptyList(), used, null, Random(8))!!
            PantoEngine.jangiTimeUp(m)
        }
        assertTrue(m.finished)
        assertEquals(1, m.inside.size)
        assertEquals(m.inside.single(), m.winner)
    }

    @Test fun tieStartsTheGoldenRoundUntilOneLeads() {
        val m = match(PantoMode.FIVE, 3, 3)
        // Three rounds, nobody answers: all tied at 0.
        repeat(9) { PantoEngine.startTurn(m, bank, emptyList(), HashSet(), null, Random(it)); PantoEngine.fiveAnswer(m, false) }
        assertTrue(m.golden)
        assertFalse(m.finished)
        assertEquals(3, m.inside.size)
        // Golden round: still tied → again; then team 1 answers → wins.
        repeat(3) { PantoEngine.startTurn(m, bank, emptyList(), HashSet(), null, Random(it)); PantoEngine.fiveAnswer(m, false) }
        assertFalse(m.finished)
        PantoEngine.startTurn(m, bank, emptyList(), HashSet(), null, Random(1)); PantoEngine.fiveAnswer(m, false)
        PantoEngine.startTurn(m, bank, emptyList(), HashSet(), null, Random(2)); PantoEngine.fiveAnswer(m, true)
        PantoEngine.startTurn(m, bank, emptyList(), HashSet(), null, Random(3)); PantoEngine.fiveAnswer(m, false)
        assertTrue(m.finished)
        assertEquals(1, m.winner)
    }

    @Test fun matchStateSurvivesSaving() {
        val m = match(PantoMode.SPEEDY, 3)
        PantoEngine.startTurn(m, bank, emptyList(), HashSet(), PantoChoice(5, 3), Random(9))
        PantoEngine.answer(m, true); PantoEngine.fault(m)
        m.turn!!.remainingMs = 42_000
        val back = PantoCodec.decode(PantoCodec.encode(m))
        assertEquals(m.turn!!.words.map { it.id }, back.turn!!.words.map { it.id })
        assertEquals(1, back.turn!!.index)
        assertEquals(1, back.turn!!.faults)
        assertEquals(42_000L, back.turn!!.remainingMs)
        assertEquals(m.usedSubjects, back.usedSubjects)
        assertEquals(PantoChoice(5, 3), back.turn!!.choice)
    }

    @Test fun userWords() {
        val list = listOf(PantoWord(1_000_001, PantoCategories.USER, 1, "کتری"))
        assertNotNull(PantoEngine.userWordError("  ", list))
        assertNotNull(PantoEngine.userWordError("کتري", list))
        assertNull(PantoEngine.userWordError("کتری", list, exceptId = 1_000_001))
        val m = match(PantoMode.NORMAL)
        assertTrue(PantoEngine.options(m, list).none { it.choice.cat == PantoCategories.USER })
        val ten = (1..10).map { PantoWord(1_000_000 + it, PantoCategories.USER, if (it <= 5) 1 else 3, "واژه $it") }
        val opts = PantoEngine.options(m, ten).filter { it.choice.cat == PantoCategories.USER }
        assertEquals(listOf(true, false, true), opts.map { it.enabled })
        val t = PantoEngine.startTurn(m, bank, ten, HashSet(), PantoChoice(PantoCategories.USER, 3), Random(1))!!
        assertTrue(t.word!!.id > 1_000_005)
    }

    // ------------------------------------------------------------------ spy

    private val spyWords by lazy { File(assets, "spy/words.json").inputStream().use { SpyEngine.defaults(it) } }

    @Test fun spyDefaults() {
        assertEquals(262, spyWords.size)
        assertEquals(96, spyWords.count { it.category == SpyCategory.PLACE })
        assertEquals(262, spyWords.map { it.id }.toSet().size)
    }

    @Test fun spyStartRules() {
        assertNotNull(SpyEngine.error(SpySettings(4, 4, 5), 10))
        assertNotNull(SpyEngine.error(SpySettings(4, 5, 5), 10))
        assertNotNull(SpyEngine.error(SpySettings(6, 1, 5), 0))
        assertNull(SpyEngine.error(SpySettings(6, 1, 5), 1))
        val g = SpyEngine.start(1, SpySettings(6, 1, 5), emptyList(), spyWords, Random(3))
        assertEquals(6, g.players.size)
        assertEquals(1, g.spies.size)
        assertEquals(300_000L, g.remainingMs)
        val many = SpyEngine.start(2, SpySettings(9, 3, 5), emptyList(), spyWords, Random(4))
        assertEquals(3, many.spies.size)
        assertTrue(many.spies.all { it in 0 until 9 })
    }

    @Test fun spyNeverSeesTheWordAndOthersSeeTheSameWord() {
        val enabled = spyWords.filter { it.category == SpyCategory.FOOD }.take(3)
        val g = SpyEngine.start(1, SpySettings(7, 2, 3), emptyList(), enabled, Random(11))
        assertTrue(g.word in enabled.map { it.text })
        val cards = g.players.indices.map { SpyEngine.cardText(g, it) }
        g.spies.forEach { assertFalse(cards[it].contains(g.word)); assertEquals("نقش شما: جاسوس", cards[it]) }
        assertEquals(1, g.players.indices.filter { !g.isSpy(it) }.map { cards[it] }.toSet().size)
        // Reveal and cover: the next player's card is covered before he sees it.
        repeat(g.players.size) {
            assertFalse(g.revealed)
            SpyEngine.reveal(g); assertTrue(g.revealed)
            SpyEngine.cover(g)
        }
        assertTrue(g.allViewed)
        val back = SpyCodec.decode(SpyCodec.encode(g))
        assertEquals(g.spies, back.spies)
        assertEquals(g.word, back.word)
        SpyEngine.end(g, SpyOutcome.GUESSED)
        assertEquals(true, g.spiesWin)
    }

    @Test fun teamsOfAnySizeRotateThePerformer() {
        val big = PantoTeam("بزرگ", members = ArrayList((1..50).map { "نفر$it" }))
        val m = PantoEngine.create(9, PantoMode.NORMAL, listOf(big, PantoTeam("کوچک", size = 12)), 3, null)
        assertEquals(50, m.teams[0].people)
        assertEquals("نفر1", m.teams[0].performer)
        PantoEngine.startTurn(m, bank, emptyList(), HashSet(), PantoChoice(2, 1), Random(1)); PantoEngine.correct(m)
        assertEquals("نفر2", m.teams[0].performer)
        assertEquals(1, m.teams[0].turns)
        assertEquals(1, m.teams[0].correct)
        assertEquals(12, PantoCodec.decode(PantoCodec.encode(m)).teams[1].people)
        val split = PantoEngine.split((1..23).map { "p$it" }, 4, Random(2))
        assertEquals(23, split.flatten().toSet().size)
        assertTrue(split.all { it.size in 5..6 })
    }

    @Test fun spyVoteHintAndStarter() {
        val g = SpyEngine.start(1, SpySettings(6, 1, 5, spyHint = true), emptyList(), spyWords, Random(5))
        assertTrue(g.starter in 0 until 6)
        val spy = g.spies.first()
        assertTrue(SpyEngine.cardText(g, spy).contains(g.category.title))
        assertFalse(SpyEngine.cardText(g, spy).contains(g.word))
        val innocent = (0 until 6).first { !g.isSpy(it) }
        assertEquals(SpyOutcome.HIDDEN, SpyEngine.vote(g, innocent))
        assertEquals(true, g.spiesWin)
        val g2 = SpyEngine.start(2, SpySettings(6, 1, 5), emptyList(), spyWords, Random(6))
        assertEquals(SpyOutcome.FOUND, SpyEngine.vote(g2, g2.spies.first()))
        assertEquals(false, g2.spiesWin)
        assertEquals(g2.accused, SpyCodec.decode(SpyCodec.encode(g2)).accused)
    }
}
