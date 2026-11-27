package com.meysam.divanemtiaz

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShalamEntrySmokeTest {
    @Test fun switchingFromANumberRequiresTwoTapsAndClearsTheNumber() {
        Api.transport = { _, _, _, _ -> JSONObject().put("ok", true).put("data", JSONObject()) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { host ->
                    Notices.markAsked(host)
                    host.updateSettings { it.copy(general = it.general.copy(animations = false),
                        shalam = it.shalam.copy(keyboardInput = true)) }
                    host.resetTo(HomeScreen(host))
                    val game = GameSession(host.repo.newSessionId(), GameType.SHALAM, listOf(Side("ما", 0), Side("اونا", 1)), rules = GameRules())
                    host.push(ShalamHandScreen(host, game, null))
                    fun views() = descendants(host.findViewById(android.R.id.content))
                    fun field() = views().filterIsInstance<EditText>().single()
                    fun option(title: String) = views().first { it.contentDescription == title }

                    field().setText("135")
                    assertTrue(option("شلم").alpha < 1f)
                    option("شلم").performClick()
                    assertEquals("135", field().text.toString())
                    assertTrue(game.rounds.isEmpty())
                    option("شلم").performClick()
                    assertEquals("", field().text.toString())
                    assertEquals(1f, option("شلم").alpha, 0.01f)

                    field().setText("125")
                    option("پاس").performClick()
                    assertTrue(game.rounds.isEmpty())
                    option("پاس").performClick()
                    assertEquals(RoundKind.SHALAM_PASS, game.rounds.single().kind)
                    assertEquals(0, game.rounds.single().bid)
                }
            }
        } finally {
            Api.transport = null
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
