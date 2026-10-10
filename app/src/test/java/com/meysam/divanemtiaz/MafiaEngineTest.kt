package com.meysam.divanemtiaz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MafiaEngineTest {
    private class MemoryStore : KeyValueStore {
        val data = linkedMapOf<String, String>()
        override fun get(key: String): String? = data[key]
        override fun write(changes: Map<String, String?>) {
            changes.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
        }
    }

    private val settings = MafiaSettings()

    /** Seats in order with fixed roles. */
    private fun game(vararg roles: MafiaRole): MafiaGame {
        val g = MafiaGame(1L)
        roles.forEachIndexed { i, r ->
            g.players += MafiaPlayer(i + 1, "بازیکن ${i + 1}", 0).apply { role = r }
            g.roleCounts[r] = (g.roleCounts[r] ?: 0) + 1
        }
        MafiaFlow.start(g, settings, Random(1))
        return g
    }

    private fun p(g: MafiaGame, r: MafiaRole) = g.holders(r).first()

    private fun job(g: MafiaGame, key: String) = MafiaNight.jobs(g).first { it.key == key }

    private fun act(g: MafiaGame, key: String, targets: List<Int>, mode: Int = 0, guess: MafiaRole? = null): String {
        val j = job(g, key)
        return MafiaNight.perform(g, j, j.modes[mode], targets, guess, 0, "")
    }

    private fun toNightOne(g: MafiaGame) {
        act(g, MafiaNight.INTRO, emptyList())
        MafiaFlow.endNight(g)
        g.day.speakingDone = true
        g.day.resultDone = true
        MafiaFlow.startNight(g)
    }

    @Test
    fun validationFollowsSourceMessages() {
        assertEquals("تعداد بازیکنان نباید از ۴ نفر کمتر باشد", MafiaFlow.validate(3, mapOf(MafiaRole.CITIZEN to 2, MafiaRole.MAFIA to 1), emptyList()).second)
        assertEquals("تعداد بازیکنان با نقش ها برابر نیست", MafiaFlow.validate(5, mapOf(MafiaRole.CITIZEN to 3, MafiaRole.MAFIA to 1), emptyList()).second)
        assertEquals("نقش شهروندی باید از نقش مافیایی بیشتر باشد", MafiaFlow.validate(4, mapOf(MafiaRole.CITIZEN to 2, MafiaRole.MAFIA to 2), emptyList()).second)
        assertTrue(MafiaFlow.validate(5, mapOf(MafiaRole.CITIZEN to 3, MafiaRole.GODFATHER to 1, MafiaRole.MAFIA to 1), emptyList()).first)
        assertFalse(MafiaFlow.validate(5, mapOf(MafiaRole.CITIZEN to 3, MafiaRole.COMMANDER to 1, MafiaRole.MAFIA to 1), emptyList()).first)
        assertFalse(MafiaFlow.validate(5, mapOf(MafiaRole.CITIZEN to 4, MafiaRole.MAFIA to 1), listOf(MafiaLastCard.GOD_FATHER_PRESENT)).first)
    }

    @Test
    fun distributionKeepsManualPicksAndFillsTheRest() {
        val g = MafiaGame(2L)
        repeat(6) { g.players += MafiaPlayer(it + 1, "p$it", 0) }
        g.roleCounts[MafiaRole.CITIZEN] = 3; g.roleCounts[MafiaRole.DOCTOR] = 1; g.roleCounts[MafiaRole.GODFATHER] = 1; g.roleCounts[MafiaRole.MAFIA] = 1
        g.players[0].role = MafiaRole.DOCTOR
        MafiaNight.distribute(g, Random(7))
        assertEquals(MafiaRole.DOCTOR, g.players[0].role)
        assertEquals(3, g.players.count { it.role == MafiaRole.CITIZEN })
        assertEquals(1, g.players.count { it.role == MafiaRole.GODFATHER })
    }

    @Test
    fun introQueueWakesMafiaTogetherThenEachRole() {
        val g = game(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.DETECTIVE, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        val q = MafiaFlow.introQueue(g)
        assertEquals("گروه مافیا از خواب بیدار شوند", q.first().text)
        assertEquals("بیدار شدند", q.first().button)
        assertEquals("ROLE_SLEEP", q[3].key)
        assertEquals("شب معارفه به اتمام رسید", q.last().text)
        assertTrue(q.any { it.key == MafiaRole.DOCTOR.name && it.button == "مشاهده شد" })
    }

    @Test
    fun doctorSavesMafiaShotAndDetectiveSeesGodfatherNegative() {
        val g = game(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.DETECTIVE, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        toNightOne(g)
        assertEquals(1, g.night)
        val citizen = g.holders(MafiaRole.CITIZEN).first()
        assertTrue(act(g, MafiaRole.DETECTIVE.name, listOf(p(g, MafiaRole.GODFATHER).id)).contains("منفی"))
        assertTrue(act(g, MafiaRole.DETECTIVE.name, listOf(p(g, MafiaRole.MAFIA).id)).contains("مثبت"))
        act(g, MafiaNight.MAFIA_GROUP, listOf(citizen.id))
        act(g, MafiaRole.DOCTOR.name, listOf(citizen.id))
        assertNull(MafiaFlow.nightEndRefusal(g))
        val out = MafiaFlow.endNight(g)
        assertTrue(out.dead.isEmpty())
        assertEquals("دیشب کشته ای نداشتیم", g.lastReport.first())
        assertTrue(citizen.alive)
    }

    @Test
    fun unsavedShotKillsAndArmourAbsorbs() {
        val g = game(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.PROFESSIONAL, MafiaRole.CITIZEN, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        toNightOne(g)
        val citizen = g.holders(MafiaRole.CITIZEN).first()
        act(g, MafiaNight.MAFIA_GROUP, listOf(citizen.id))
        act(g, MafiaRole.DOCTOR.name, listOf(p(g, MafiaRole.DOCTOR).id))
        act(g, MafiaRole.PROFESSIONAL.name, listOf(p(g, MafiaRole.GODFATHER).id))
        MafiaFlow.endNight(g)
        assertFalse(citizen.alive)
        assertTrue(p(g, MafiaRole.GODFATHER).alive)
        assertEquals(0, p(g, MafiaRole.GODFATHER).armor)
        assertEquals("دیشب  ۱ نفر کشته شد", g.lastReport.first())
    }

    @Test
    fun professionalShootingCitizenLeavesHimself() {
        val g = game(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.PROFESSIONAL, MafiaRole.CITIZEN, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        toNightOne(g)
        val cits = g.holders(MafiaRole.CITIZEN)
        act(g, MafiaNight.MAFIA_GROUP, listOf(cits[0].id))
        act(g, MafiaRole.DOCTOR.name, listOf(cits[0].id))
        act(g, MafiaRole.PROFESSIONAL.name, listOf(cits[1].id))
        MafiaFlow.endNight(g)
        assertFalse(p(g, MafiaRole.PROFESSIONAL).alive)
        assertTrue(cits[1].alive)
    }

    @Test
    fun voteThresholdsAndTieLottery() {
        val g = game(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.DETECTIVE, MafiaRole.CITIZEN, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        act(g, MafiaNight.INTRO, emptyList())
        MafiaFlow.endNight(g)
        assertEquals(4, MafiaFlow.firstEdge(7, settings))
        assertEquals(3, MafiaFlow.firstEdge(7, settings.copy(voteEdgeUp = false)))
        val a = g.players[4].id; val b = g.players[5].id
        g.day.speakingDone = true
        g.day.firstVotes[a] = 4; g.day.firstVotes[b] = 5; g.day.firstVotes[g.players[0].id] = 2
        MafiaFlow.finishFirstVote(g, settings)
        assertEquals(listOf(a, b), g.day.defenders)
        g.day.secondVotes[a] = 3; g.day.secondVotes[b] = 3
        g.day.secondVoteDone = true
        val (exiting, tie) = MafiaFlow.voteOutcome(g, settings)
        assertTrue(tie)
        assertEquals(2, exiting.size)
        // Single defender needs floor((N-1)/2)+1 votes.
        g.day.defenders.clear(); g.day.defenders += a; g.day.secondVotes[a] = 3
        assertTrue(MafiaFlow.voteOutcome(g, settings).first.isEmpty())
        g.day.secondVotes[a] = 4
        assertEquals(listOf(a), MafiaFlow.voteOutcome(g, settings).first)
    }

    @Test
    fun redCarpetAndGreenMileFromLastCards() {
        val g = game(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.CITIZEN, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        g.deck += MafiaCardState(MafiaLastCard.RED_CARPET)
        val st = MafiaFlow.draw(g, 0, g.players[3].id)!!
        val out = MafiaFlow.applyCard(g, st, listOf(g.players[4].id), null)
        assertFalse(out.stays)
        MafiaFlow.finishFirstVote(g, settings)
        assertEquals(listOf(g.players[4].id), g.day.defenders)
    }

    @Test
    fun winRules() {
        val g = game(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.CITIZEN, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        assertNull(MafiaFlow.checkWin(g))
        MafiaFlow.exit(g, g.players[3].id, MafiaDeath.VOTE)
        MafiaFlow.exit(g, g.players[4].id, MafiaDeath.VOTE)
        assertEquals("گروه مافیا برنده بازی شدند", MafiaFlow.checkWin(g))
        val h = game(MafiaRole.GODFATHER, MafiaRole.DOCTOR, MafiaRole.CITIZEN, MafiaRole.CITIZEN, MafiaRole.JOKER)
        MafiaFlow.exit(h, h.players[4].id, MafiaDeath.VOTE)
        assertEquals("جوکر برنده بازی شد", MafiaFlow.checkWin(h))
        val c = game(MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        MafiaFlow.exit(c, c.players[0].id, MafiaDeath.VOTE)
        assertEquals("شهروندها برنده بازی شدند", MafiaFlow.checkWin(c))
    }

    @Test
    fun gameSurvivesStoreRoundTripAndReturnToPast() {
        val g = game(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.DOCTOR, MafiaRole.DETECTIVE, MafiaRole.CITIZEN, MafiaRole.CITIZEN)
        toNightOne(g)
        act(g, MafiaRole.DOCTOR.name, listOf(g.players[4].id))
        val repo = MafiaRepository(MemoryStore())
        repo.save(g)
        val back = repo.game(g.id)!!
        assertEquals(g.night, back.night)
        assertEquals(MafiaRole.DOCTOR, back.players[2].role)
        assertNotNull(back.records[MafiaRole.DOCTOR.name])
        assertTrue(back.snapshots.isNotEmpty())
        val past = MafiaFlow.restore(back, 0)
        assertEquals(0, past.night)
        assertFalse(past.isDay)
        repo.saveSettings(settings.copy(speakTime = 45, sounds = mapOf(MafiaCues.NIGHT_START to SoundChoice(SoundSource.FILE, "", "/x.m4a", "صدای من"))))
        val s = repo.settings()
        assertEquals(45, s.speakTime)
        assertEquals("/x.m4a", s.cue(MafiaCues.NIGHT_START)?.path)
        assertEquals("mafia_morning_sound", s.cue(MafiaCues.DAY_START)?.builtin)
        assertTrue(JSONObject(MafiaCodec.encodeGame(g).toString()).has("players"))
    }

    @Test
    fun allRolesAndCardsHaveTextAndJobsBuild() {
        MafiaRole.values().forEach { r ->
            assertTrue(r.name, MafiaRoleText.explain(r).isNotBlank())
            assertTrue(r.name, MafiaRoleText.intro(r).isNotBlank())
        }
        MafiaLastCard.values().forEach { assertTrue(it.name, it.explain.isNotBlank()) }
        // Every role's night button can be built and its candidates listed without errors.
        val roles = MafiaRole.values().toList()
        val g = MafiaGame(9L)
        roles.forEachIndexed { i, r -> g.players += MafiaPlayer(i + 1, "p$i", 0).apply { role = r } }
        MafiaFlow.start(g, settings, Random(3))
        listOf(0, 1, 2, 3).forEach { night ->
            g.night = night
            MafiaNight.jobs(g).forEach { j -> j.modes.forEach { m -> MafiaNight.candidates(g, j, m) } }
        }
        g.night = 2
        MafiaNight.resolve(g, apply = false)
    }
}
