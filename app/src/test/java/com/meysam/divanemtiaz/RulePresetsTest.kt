package com.meysam.divanemtiaz

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class RulePresetsTest {
    private class Memory : KeyValueStore {
        val data = HashMap<String, String>()
        override fun get(key: String) = data[key]
        override fun write(changes: Map<String, String?>) { changes.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value } }
    }
    @Test fun fourNamedPresetsKeepIndependentMenfiScores() {
        val store = Memory()
        val book = RulePresetBook(store)
        val success = AppSettings().menfi.success.toMutableList().apply { this[11 - 3] = 33 }
        val custom = AppSettings().copy(menfi = AppSettings().menfi.copy(success = success))
        for (slot in 0..3) book.save("menfi", slot, "گروه $slot", RulePresetPolicy.capture(custom, "menfi"))
        assertEquals(4, book.entries("menfi").size)
        assertEquals(33, RulePresetPolicy.apply(AppSettings(), "menfi", book.entries("menfi")[0].rules).menfi.success[11 - 3])
        book.save("shalam", 0, "شلم", RulePresetPolicy.capture(AppSettings(), "shalam"))
        book.delete("shalam", 0)
        assertEquals(4, book.entries("menfi").size)
        try { book.save("menfi", 4, "پنجم", JSONObject()); fail("a fifth slot is invalid") } catch (_: IllegalArgumentException) { }
    }
    @Test fun choosingRulesNeverChangesOtherSettingsOrExistingGames() {
        val original = AppSettings()
        val oldRules = original.rulesFor(GameType.MENFI)
        val success = original.menfi.success.toMutableList().apply { this[11 - 3] = 33 }
        val changed = RulePresetPolicy.apply(original, "menfi", RulePresetPolicy.capture(original.copy(menfi = original.menfi.copy(success = success)), "menfi"))
        assertEquals(original.shalam, changed.shalam)
        assertEquals(original.general, changed.general)
        assertEquals(original.league, changed.league)
        assertEquals(original.menfi.success[11 - 3], oldRules.menfi.success[11 - 3])
        assertEquals(33, changed.rulesFor(GameType.MENFI).menfi.success[11 - 3])
        assertEquals(33, MenfiEngine.successScore(11, changed.rulesFor(GameType.MENFI).menfi))
        val standard = RulePresetPolicy.apply(changed, "menfi", RulePresetPolicy.capture(AppSettings(), "menfi"))
        assertEquals(original.menfi, standard.menfi)
    }
    @Test fun presetsSurviveBackupAndOlderBackupsDoNotDeleteThem() {
        val first = Memory(); val repo = GameRepository(first); val book = RulePresetBook(first)
        book.save("league", 0, "حذفی دوستانه", RulePresetPolicy.capture(AppSettings(), "league"))
        val backup = repo.exportBackup()
        val other = Memory(); val restored = GameRepository(other)
        restored.importBackup(backup, true)
        assertEquals("حذفی دوستانه", RulePresetBook(other).entries("league").single().name)
        val old = JSONObject(backup.toString()).apply { remove("rule_presets") }
        restored.importBackup(old, true)
        assertEquals(1, RulePresetBook(other).entries("league").size)
        RulePresetBook(other).save("league", 0, "محلی", JSONObject())
        restored.importBackup(backup, false)
        assertEquals("محلی", RulePresetBook(other).entries("league").single().name)
    }
}
