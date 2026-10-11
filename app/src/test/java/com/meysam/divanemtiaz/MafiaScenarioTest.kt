package com.meysam.divanemtiaz

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MafiaScenarioTest {
    private class MemoryStore : KeyValueStore {
        val data = linkedMapOf<String, String>()
        override fun get(key: String): String? = data[key]
        override fun write(changes: Map<String, String?>) {
            changes.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
        }
    }

    private val minds = setOf(MafiaLastCard.NOSTRADAMUS_BEAUTIFUL_MIND, MafiaLastCard.JACK_SPARROW_BEAUTIFUL_MIND, MafiaLastCard.SHERLOCK_HOLMES_BEAUTIFUL_MIND)

    @After fun clear() { MafiaRoleRegistry.user = emptyMap(); MafiaRoleRegistry.activate(null) }

    private fun s(id: String) = MafiaScenarios.builtin.first { it.id == id }

    @Test fun allScenariosFromTheFileArePresent() {
        assertEquals(45, MafiaScenarios.builtin.size)
        assertEquals(MafiaScenarios.builtin.size, MafiaScenarios.builtin.map { it.id }.toSet().size)
        assertEquals(MafiaScenarios.FREE, MafiaScenarios.all(emptyList()).last().id)
        MafiaScenarios.builtin.forEach { sc -> sc.presets.forEach { (n, m) -> assertEquals("${sc.id} $n", n, m.values.sum()) } }
    }

    @Test fun godfatherFamilyHasThreeVersionsEachWithOnlyItsOwnBeautifulMind() {
        val fam = MafiaScenarios.builtin.filter { it.family == MafiaScenarios.GODFATHER_FAMILY }
        assertEquals(listOf("godfather", "godfather_jack", "godfather_sherlock"), fam.map { it.id })
        val expected = mapOf("godfather" to (MafiaRole.NOSTRADAMUS to MafiaLastCard.NOSTRADAMUS_BEAUTIFUL_MIND),
            "godfather_jack" to (MafiaRole.JACK_SPARROW to MafiaLastCard.JACK_SPARROW_BEAUTIFUL_MIND),
            "godfather_sherlock" to (MafiaRole.SHERLOCK_HOLMES to MafiaLastCard.SHERLOCK_HOLMES_BEAUTIFUL_MIND))
        val shared = setOf(MafiaLastCard.SILENCE_OF_THE_LAMBS, MafiaLastCard.SHOW_IDENTITY, MafiaLastCard.HANDCUFF, MafiaLastCard.FACE_OFF)
        fam.forEach { sc ->
            val (role, mind) = expected.getValue(sc.id)
            assertEquals(listOf(mind), sc.cards.filter { it in minds })
            assertEquals(shared + mind, sc.cards.toSet())
            assertTrue(role in sc.pool)
            (setOf(MafiaRole.NOSTRADAMUS, MafiaRole.JACK_SPARROW, MafiaRole.SHERLOCK_HOLMES) - role).forEach { assertFalse(it in sc.pool) }
            assertFalse(sc.names.containsKey(role))
        }
        assertEquals("پدرخوانده – جک گنجشکه", s("godfather_jack").name)
    }

    @Test fun referenceLineupIsOnlyASuggestionForAnyPlayerCount() {
        val gf = s("godfather")
        listOf(6, 8, 9, 10, 11, 12, 14).forEach { n ->
            val m = gf.lineup(n)
            assertNotNull("$n", m)
            assertEquals("$n", n, m!!.values.sum())
            assertTrue(m.keys.any { it.side == MafiaSide.MAFIA })
        }
        assertEquals(1, gf.lineup(8)!![MafiaRole.NOSTRADAMUS])
        assertEquals(5, gf.lineup(14)!![MafiaRole.CITIZEN])
    }

    @Test fun beautifulMindFollowsTheSpecialRoleInTheGame() {
        val all = MafiaScenarios.godfatherCards(MafiaLastCard.NOSTRADAMUS_BEAUTIFUL_MIND) + MafiaLastCard.JACK_SPARROW_BEAUTIFUL_MIND
        val kept = MafiaScenarios.matchMind(all, setOf(MafiaRole.GODFATHER, MafiaRole.JACK_SPARROW))
        assertEquals(listOf(MafiaLastCard.JACK_SPARROW_BEAUTIFUL_MIND), kept.filter { it in minds })
        assertTrue(MafiaLastCard.HANDCUFF in kept)
    }

    @Test fun editedScenarioIsSavedAndReplacesTheBuiltInUntilReset() {
        val repo = MafiaRepository(MemoryStore())
        val gf = repo.scenario("godfather")!!
        val edited = gf.copy(pool = gf.pool + MafiaRole.DETECTIVE, presets = gf.presets + (8 to linkedMapOf(MafiaRole.GODFATHER to 1, MafiaRole.MAFIA to 1, MafiaRole.DETECTIVE to 1, MafiaRole.NOSTRADAMUS to 1, MafiaRole.CITIZEN to 4)),
            names = gf.names + (MafiaRole.CITIZEN to "همشهری"), cards = gf.cards - MafiaLastCard.FACE_OFF)
        repo.saveScenario(edited)
        val back = repo.scenario("godfather")!!
        assertEquals(edited.pool, back.pool)
        assertEquals(8, back.lineup(8)!!.values.sum())
        assertEquals(1, back.lineup(8)!![MafiaRole.DETECTIVE])
        assertEquals("همشهری", back.names[MafiaRole.CITIZEN])
        assertFalse(MafiaLastCard.FACE_OFF in back.cards)
        assertEquals(MafiaScenarios.GODFATHER_FAMILY, back.family)
        assertEquals(MafiaScenarios.all(repo.scenarios()).size, MafiaScenarios.builtin.size + 1)
        repo.deleteScenario("godfather")
        assertEquals(gf, repo.scenario("godfather"))
    }

    @Test fun customScenarioRoundTrip() {
        val repo = MafiaRepository(MemoryStore())
        val mine = MafiaScenario("u1", "سناریوی من", "خلاصه", "قانون", listOf(MafiaRole.GODFATHER, MafiaRole.DOCTOR), mapOf(6 to linkedMapOf(MafiaRole.GODFATHER to 2, MafiaRole.CITIZEN to 4)),
            cards = listOf(MafiaLastCard.INSOMNIA), setup = ScenarioSetup(challenge = false, thresholds = mapOf(6 to 3), queryCount = 1), custom = true)
        repo.saveScenario(mine)
        assertEquals(mine, repo.scenario("u1"))
    }

    @Test fun scenarioSettingsReachTheGame() {
        val g = MafiaGame(1L)
        MafiaScenarios.applyTo(g, s("red_status"))
        assertEquals("1", g.extra["noNight"])
        assertEquals("1", g.extra["noMafiaIntro"])
        assertEquals(3, MafiaFlow.thresholds(g)[6])
        MafiaScenarios.applyTo(g, s("scam"))
        assertEquals("1", g.extra["noChallenge"])
        assertEquals(null, g.extra["noNight"])
    }

    @Test fun editedRoleRulesApplyToExistingRoles() {
        val repo = MafiaRepository(MemoryStore())
        repo.saveRoleDefs(mapOf(MafiaRole.DOCTOR to MafiaRoleDef(name = "طبیب", wakeOrder = 3, inquiry = InquiryLook.POSITIVE)))
        assertEquals("طبیب", MafiaRole.DOCTOR.faName)
        assertEquals(3, MafiaRole.DOCTOR.wakeOrder)
        assertFalse(MafiaRole.DOCTOR.isGeneric)
        MafiaRoleRegistry.user = repo.roleDefs()
        val g = MafiaGame(1L)
        listOf(MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.DETECTIVE, MafiaRole.CITIZEN, MafiaRole.CITIZEN).forEachIndexed { i, r ->
            g.players += MafiaPlayer(i + 1, "p$i", 0).apply { role = r }; g.roleCounts[r] = (g.roleCounts[r] ?: 0) + 1
        }
        MafiaFlow.start(g, MafiaSettings(), Random(1))
        assertTrue(MafiaNight.detectivePositive(g, g.holders(MafiaRole.DOCTOR).first(), null))
    }

    @Test fun newRoleWithItsOwnNightRule() {
        val slot = MafiaRoleRegistry.nextFreeSlot()!!
        MafiaRoleRegistry.user = mapOf(slot to MafiaRoleDef(name = "نگهبان شب", side = MafiaSide.CITY, wakes = true, wakeOrder = 40, ability = MafiaAbility.SAVE, min = 1, max = 1, uses = 0, nights = MafiaNights.EVERY))
        assertTrue(slot in MafiaRoleRegistry.available())
        val g = MafiaGame(1L)
        listOf(MafiaRole.GODFATHER, slot, MafiaRole.CITIZEN, MafiaRole.CITIZEN, MafiaRole.CITIZEN).forEachIndexed { i, r ->
            g.players += MafiaPlayer(i + 1, "p$i", 0).apply { role = r }; g.roleCounts[r] = (g.roleCounts[r] ?: 0) + 1
        }
        MafiaFlow.start(g, MafiaSettings(), Random(1))
        g.introDone = true; g.night = 1
        val job = MafiaNight.jobs(g).first { it.role == slot }
        assertEquals("نگهبان شب", job.title)
    }

    @Test fun referenceDecksOnlyWhereTheScenarioNamesThem() {
        val withDeck = MafiaScenarios.builtin.filter { it.cards.isNotEmpty() }.map { it.id }.toSet()
        assertEquals(setOf("godfather", "godfather_jack", "godfather_sherlock", "filimo", "el_clasico"), withDeck)
        assertTrue(s("zodiac").setup.noCards)
        assertTrue(MafiaLastCard.INTELLIGENCE in s("filimo").cards)
        assertTrue(MafiaRole.JOKER_M in s("filimo").pool)
        assertTrue(MafiaRole.NEGOTIATOR in s("negotiation").pool)
        assertTrue(MafiaRole.INFORMANT in s("capo").pool)
        assertEquals(13, s("capo").lineup(13)!!.values.sum())
        assertEquals(listOf(MafiaLastCard.INTELLIGENCE, MafiaLastCard.VERTIGO, MafiaLastCard.DAL, MafiaLastCard.MANISA, MafiaLastCard.MIRROR), s("el_clasico").cards)
        assertTrue(MafiaRole.VOODOO in s("nato_commando").pool)
        assertFalse(MafiaRole.MATADOR in s("nato_commando").pool)
        assertFalse(MafiaRole.CHARLATAN in s("filimo").pool)
        assertFalse(MafiaRole.GOOD_MAN in s("negotiation").pool)
        assertFalse(MafiaRole.SPY in s("capo").pool)
        assertTrue(MafiaRole.PABLO in s("el_clasico").pool)
        assertTrue(s("molagh").names.isEmpty())
        MafiaScenarios.builtin.forEach { assertNotNull(it.id, MafiaScenarios.status[it.id]) }
    }

    private fun table(n: Int): MafiaGame {
        val g = MafiaGame(1L)
        val roles = listOf(MafiaRole.GODFATHER, MafiaRole.GOOD_MAN, MafiaRole.MATADOR, MafiaRole.NOSTRADAMUS, MafiaRole.DOCTOR, MafiaRole.PROFESSIONAL, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        (0 until n).forEach { i -> val r = roles[i % roles.size]; g.players += MafiaPlayer(i + 1, "p${i + 1}", 0).apply { role = r }; g.roleCounts[r] = (g.roleCounts[r] ?: 0) + 1 }
        MafiaFlow.start(g, MafiaSettings(), Random(1))
        return g
    }

    @Test fun introDayComesFirstAndStarterMovesTwoSeatsEachDay() {
        val g = table(8)
        assertEquals("pending", g.extra["introDay"])
        assertEquals(1, MafiaManual.speakingOrder(g).first().id)
        g.night = 1; g.isDay = true
        assertEquals(3, MafiaManual.speakingOrder(g).first().id)
        g.night = 2
        assertEquals(5, MafiaManual.speakingOrder(g).first().id)
        g.player(7)!!.alive = false
        g.night = 3
        assertEquals(8, MafiaManual.speakingOrder(g).first().id)
        // Narrator's choice today; the next day continues two seats on from it.
        MafiaManual.setStarter(g, 2)
        assertEquals(2, MafiaManual.speakingOrder(g).first().id)
        g.night = 4
        assertEquals(4, MafiaManual.speakingOrder(g).first().id)
        MafiaManual.move(g, 4, -1)
        assertEquals(4, MafiaManual.speakingOrder(g).first().id)
    }

    @Test fun nostradamusWakesFirstPicksThreeAndTwoMafiaForceTheMafiaSide() {
        val g = table(8)
        val jobs = MafiaNight.jobs(g)
        assertEquals(MafiaRole.NOSTRADAMUS, jobs.first().role)
        val job = jobs.first()
        assertEquals(3, job.modes[0].min); assertEquals(3, job.modes[0].max)
        fun id(r: MafiaRole) = g.holders(r).first().id
        // Godfather counts as a citizen: Godfather + Saul + Leon = one mafia, side is free.
        assertFalse(MafiaNight.nostradamusForced(g, listOf(id(MafiaRole.GODFATHER), id(MafiaRole.GOOD_MAN), id(MafiaRole.PROFESSIONAL))))
        assertTrue(MafiaNight.nostradamusForced(g, listOf(id(MafiaRole.MATADOR), id(MafiaRole.GOOD_MAN), id(MafiaRole.PROFESSIONAL))))
        val r = MafiaNight.perform(g, job, job.modes[0], listOf(id(MafiaRole.MATADOR), id(MafiaRole.GOOD_MAN), id(MafiaRole.DOCTOR)), null, 0, "مافیا")
        assertTrue(r.contains("باید با مافیا"))
    }

    @Test fun elClasicoCards() {
        val g = table(8)
        g.night = 1; g.isDay = true; g.introDone = true
        val holder = g.player(8)!!
        val target = g.player(5)!!
        MafiaFlow.applyCard(g, MafiaCardState(MafiaLastCard.DAL).apply { this.holder = holder.id }, listOf(target.id), null)
        assertFalse(target in MafiaFlow.votable(g))
        MafiaFlow.startNight(g)
        MafiaFlow.endNight(g)
        assertTrue(MafiaFlow.votable(g).contains(target))
        val before = target.role
        MafiaFlow.applyCard(g, MafiaCardState(MafiaLastCard.MIRROR).apply { this.holder = holder.id }, listOf(target.id), null)
        assertEquals(before, holder.role)
    }
}
