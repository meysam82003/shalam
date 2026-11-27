package com.meysam.divanemtiaz

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCampaignSmokeTest {
    @Test fun nextCampaignReturnsToStatsAndLatestGameIncludesCompletedPantomime() {
        val ad = JSONObject().put("id", 567321).put("format", "text").put("title", "حامی آزمایشی")
            .put("placements", JSONArray().put("home_carousel")).put("daily_cap", JSONObject.NULL)
        val config = JSONObject().put("ads", JSONObject().put("enabled", true)
            .put("placements", JSONArray().put(JSONObject().put("key", "home_carousel").put("active", true)
                .put("formats", JSONArray().put("text")))).put("ads", JSONArray().put(ad)))
        Api.transport = { _, path, _, _ -> JSONObject().put("ok", true).put("data", if (path == "v1/config") config else JSONObject()) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { host ->
                    Notices.markAsked(host)
                    host.updateSettings { it.copy(general = it.general.copy(animations = false)) }
                    ProState.configJson = config.toString()
                    val id = host.repo.newSessionId()
                    host.repo.save(GameSession(id, GameType.MENFI, listOf(Side("الف", 0), Side("ب", 1)), rules = GameRules(), updatedAt = id - 500))
                    val match = PantoMatch(id + 1).apply {
                        teams += PantoTeam("زرد", points = 12)
                        teams += PantoTeam("آبی", points = 10)
                        finished = true
                    }
                    host.panto.save(match)
                    host.resetTo(HomeScreen(host))

                    fun views() = descendants(host.findViewById(android.R.id.content))
                    fun hasText(text: String) = views().filterIsInstance<TextView>().any { it.text.toString() == text }
                    assertTrue(hasText("حامی آزمایشی"))
                    assertTrue(hasText("آخرین بازی · پانتومیم"))
                    views().first { it.contentDescription == "کادر بعدی" }.performClick()
                    assertFalse(hasText("حامی آزمایشی"))
                    views().first { it.contentDescription == "آمار دیوان، باز کردن تاریخچه" }.performClick()
                    assertTrue(host.current is HistoryScreen)
                    host.panto.delete(match.id)
                    host.repo.delete(id)
                }
            }
        } finally {
            ProState.configJson = null
            Api.transport = null
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
