package com.meysam.divanemtiaz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStoreTest {
    private class MemoryStore : KeyValueStore {
        val data = linkedMapOf<String, String>()
        override fun get(key: String): String? = data[key]
        override fun write(changes: Map<String, String?>) {
            changes.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
        }
    }

    private val v2Settings = JSONObject()
        .put("haptic", false)
        .put("shalamAwardContractOnly", true)
        .put("shalamAskDouble", false)
        .put("shalamEndDifference", 300)
        .put("menfiHands", 5)
        .put("hezartaiiZeroPenalty", -70)
        .toString()

    private val v2History = JSONArray()
        .put(
            JSONObject()
                .put("id", 1000L).put("game", "shalam").put("teamA", "شیران").put("teamB", "عقاب‌ها")
                .put("avatarA", 2).put("avatarB", 5).put("timestamp", 2000L).put("finished", false)
                .put(
                    "rounds", JSONArray()
                        .put(JSONObject().put("teamA", -135).put("teamB", 35).put("note", "تعهد 135").put("sourceA", 130).put("sourceB", 35).put("contractTeam", 1).put("contract", "135"))
                        .put(JSONObject().put("teamA", 0).put("teamB", 0).put("note", "پاس").put("contractTeam", 2).put("contract", "پاس"))
                )
        )
        .put(
            JSONObject()
                .put("id", 3000L).put("game", "menfi").put("teamA", "الف").put("teamB", "ب")
                .put("timestamp", 4000L).put("finished", true)
                .put("rounds", JSONArray().put(JSONObject().put("teamA", 20).put("teamB", -3).put("note", "x").put("sourceA", 3).put("sourceB", 10)))
        )
        .toString()

    private val v1History = JSONArray()
        .put(JSONObject().put("game", "hezartaii").put("teamA", "رضا").put("teamB", "سارا").put("scoreA", 820).put("scoreB", 1010).put("rounds", 9).put("timestamp", 500L))
        .toString()

    @Test fun migratesVersionOneAndTwoWithoutChangingScores() {
        val store = MemoryStore()
        val repo = GameRepository(store) { LegacyData(v1History, v2History, v2Settings) }
        val sessions = repo.sessions()
        assertEquals(listOf(3000L, 1000L, 500L), sessions.map { it.id })

        val shalam = repo.session(1000L)!!
        assertEquals(listOf(-135, 35), GameEngine.totals(shalam))
        assertEquals("شیران", shalam.sides[0].name)
        assertEquals(5, shalam.sides[1].avatar)
        assertEquals(0, shalam.rounds[0].contractTeam)
        assertTrue(shalam.rounds.all { it.kind == RoundKind.FIXED })
        assertFalse(shalam.finished)

        val menfi = repo.session(3000L)!!
        assertEquals(listOf(3, 10), menfi.rounds[0].numbers)
        assertEquals(1, menfi.rounds[0].outcome)
        assertFalse(menfi.rules.menfi.highWins)

        val v1 = repo.session(500L)!!
        assertTrue(v1.finished)
        assertEquals(listOf(820, 1010), GameEngine.totals(v1))
        assertEquals(GameType.HEZARTAII, v1.game)

        val settings = repo.settings()
        assertFalse(settings.general.haptic)
        assertTrue(settings.shalam.contractOnly)
        assertEquals(DoubleType.NEGATIVE, settings.shalam.doubleType)
        assertTrue(settings.shalam.noJoker.endWithDiff)
        assertEquals(300, settings.shalam.noJoker.endDiff)
        assertEquals(5, settings.menfi.hands)
        assertEquals(-70, settings.hezar.zeroPenalty)
    }

    @Test fun migrationRunsOnceAndKeepsNewData() {
        val store = MemoryStore()
        GameRepository(store) { LegacyData(v1History, v2History, v2Settings) }.sessions()
        val repo = GameRepository(store) { LegacyData(v1History, v2History, v2Settings) }
        val session = repo.session(1000L)!!
        session.rounds += Round(RoundKind.PENALTY, emptyList(), raw = listOf(-20, 0))
        GameEngine.recompute(session)
        repo.save(session)
        val again = GameRepository(store) { LegacyData(v1History, v2History, v2Settings) }
        assertEquals(3, again.sessions().size)
        assertEquals(listOf(-155, 35), GameEngine.totals(again.session(1000L)!!))
    }

    @Test fun sessionRoundTripKeepsInputsAndRules() {
        val rules = AppSettings().rulesFor(GameType.SHALAM, withJoker = true, endPoint = 1500)
        val session = GameSession(42L, GameType.SHALAM, listOf(Side("ما", 3), Side("اونا", 7)), rules = rules, elapsedMs = 61_000L, label = "شلم ۱۲ برگ")
        session.rounds += Round(RoundKind.SHALAM_HAND, emptyList(), note = "x", contractTeam = 1, bid = 150, taken = 120, suit = Suit.HEART, double = DoubleChoice.POSITIVE)
        session.rounds += Round(RoundKind.SHALAM_DOUBLE_SHELEM, emptyList(), contractTeam = 0, taken = 0)
        GameEngine.recompute(session)
        val decoded = GameCodec.decodeSession(GameCodec.encodeSession(session))
        assertEquals(session.rounds, decoded.rounds)
        assertEquals(session.rules, decoded.rules)
        assertEquals(session.sides, decoded.sides)
        assertEquals(61_000L, decoded.elapsedMs)
        assertEquals(listOf(120 + 800, -300), GameEngine.totals(decoded))
    }

    @Test fun settingsRoundTrip() {
        val settings = AppSettings(
            general = GeneralSettings(persianDigits = false, defaultTeam1 = "x"),
            shalam = ShalamSettings(doubleType = DoubleType.ASK, joker = ShalamModeRules.JOKER.copy(endDiff = 900)),
            menfi = MenfiSettings(highWins = false),
            hezar = HezarSettings(players = 4)
        )
        assertEquals(settings, GameCodec.decodeSettings(GameCodec.encodeSettings(settings)))
    }

    @Test fun deleteAndClearOnlyTouchVersionThreeData() {
        val store = MemoryStore()
        val repo = GameRepository(store) { LegacyData(null, v2History, null) }
        assertEquals(2, repo.sessions().size)
        repo.delete(1000L)
        assertEquals(listOf(3000L), repo.sessions().map { it.id })
        repo.clearHistory()
        assertTrue(repo.sessions().isEmpty())
        assertNotNull(store.get(GameRepository.KEY_SETTINGS))
    }
}
