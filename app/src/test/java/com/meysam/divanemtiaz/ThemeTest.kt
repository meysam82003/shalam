package com.meysam.divanemtiaz

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeTest {
    @After fun restore() = ThemeEngine.apply(Themes.ROYAL)

    private fun channel(c: Int, shift: Int): Double {
        val v = ((c shr shift) and 0xFF) / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Int): Double = 0.2126 * channel(c, 16) + 0.7152 * channel(c, 8) + 0.0722 * channel(c, 0)

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Colour of a translucent card over the page background. */
    private fun over(top: Int, base: Int): Int {
        val a = ((top ushr 24) and 0xFF) / 255.0
        fun mix(s: Int) = (((top shr s) and 0xFF) * a + ((base shr s) and 0xFF) * (1 - a)).toInt() shl s
        return (0xFF shl 24) or mix(16) or mix(8) or mix(0)
    }

    @Test fun sevenStylesWithTheClassicAsDefault() {
        assertEquals(7, Themes.all.size)
        assertEquals(7, Themes.all.map { it.id }.toSet().size)
        assertEquals("royal", GeneralSettings().theme)
        assertEquals(Themes.ROYAL, Themes.byId(null))
        assertEquals(Themes.ROYAL, Themes.byId("unknown"))
        assertEquals(Themes.NEON, Themes.byId("neon"))
    }

    @Test fun stylesDifferInShapeNotOnlyColour() {
        assertTrue(Themes.all.map { it.button }.toSet().size >= 5)
        assertTrue(Themes.all.map { it.frame }.toSet().size >= 4)
        assertTrue(Themes.all.map { it.pattern }.toSet().size >= 7)
        assertEquals(3, Themes.all.count { it.light })
    }

    @Test fun textIsReadableOnCardsInEveryStyle() {
        Themes.all.forEach { t ->
            val card = over(t.surface, t.bg[1])
            assertTrue("${t.id} text", contrast(t.text, card) >= 7.0)
            assertTrue("${t.id} muted", contrast(t.muted, card) >= 3.6)
            assertTrue("${t.id} heading", contrast(t.accentText, card) >= 4.5)
            assertTrue("${t.id} title", contrast(t.titleColor, over(0, t.bg[0]) or (0xFF shl 24)) >= 7.0)
            assertTrue("${t.id} primary button", contrast(t.primaryText, t.primary[1]) >= 2.4)
        }
    }

    @Test fun applyingAStyleSetsEveryToken() {
        ThemeEngine.apply(Themes.COBALT)
        assertEquals(Themes.COBALT.text, Royal.ivory)
        assertEquals(Themes.COBALT.accentText, Royal.goldLight)
        assertEquals(Themes.COBALT.metal, Royal.metal)
        assertEquals(Themes.COBALT.titleColor, Royal.title)
        val v = ThemeEngine.version
        ThemeEngine.apply(Themes.ROYAL)
        assertTrue(ThemeEngine.version > v)
        assertEquals(0xFFF8F0DC.toInt(), Royal.ivory)
        assertEquals(0xFFE6BE6A.toInt(), Royal.gold)
    }

    @Test fun themeAndLeaguePictureAreStoredAndOldDataStillLoads() {
        val s = AppSettings(general = GeneralSettings(theme = "iranic"))
        assertEquals("iranic", GameCodec.decodeSettings(GameCodec.encodeSettings(s)).general.theme)
        val old = GameCodec.encodeSettings(AppSettings())
        old.getJSONObject("general").remove("theme")
        assertEquals("royal", GameCodec.decodeSettings(old).general.theme)

        val league = League(5L, "جام", GameType.SHALAM, listOf(Side("الف", 1), Side("ب", 2)), GameRules(), LeagueFormat.KNOCKOUT, LeagueSettings(), avatar = PhotoStore.BASE + 3)
        assertEquals(PhotoStore.BASE + 3, GameCodec.decodeLeague(GameCodec.encodeLeague(league)).avatar)
        val legacy = GameCodec.encodeLeague(league).apply { remove("avatar") }
        assertEquals(-1, GameCodec.decodeLeague(JSONObject(legacy.toString())).avatar)
    }

    @Test fun picturesUseTheirOwnAvatarRange() {
        assertTrue(PhotoStore.isPhoto(PhotoStore.BASE))
        assertFalse(PhotoStore.isPhoto(Emblems.COUNT - 1))
        assertFalse(PhotoStore.exists(PhotoStore.BASE + 99))
    }
}
