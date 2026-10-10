package com.meysam.divanemtiaz

import android.graphics.Bitmap
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import java.io.File

@Suppress("DEPRECATION")
class NativeSmokeTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
    private fun findAction(v: View, label: String): View? {
        if (v.isClickable && v.contentDescription?.toString() == label) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) {
            findAction(v.getChildAt(i), label)?.let { return it }
        }
        return null
    }

    private fun capture(a: MainActivity, name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(250)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val dir = File(a.getExternalFilesDir(null), "smoke-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    fun testNativeNavigationAndGameCreation() {
        val a = activity
        Thread.sleep(500)
        instrumentation.waitForIdleSync()
        assertTrue(a.current is HomeScreen)
        capture(a, "home")
        val originalSettings = GameCodec.encodeSettings(a.settings).toString()
        val originalGames = a.repo.sessions().map { GameCodec.encodeSession(it).toString() }
        GameType.values().forEach { game ->
            instrumentation.runOnMainSync {
                a.resetTo(HomeScreen(a), SetupScreen(a, game))
                val start = findAction(a.findViewById(android.R.id.content), "شروع داوری ${game.title}")
                assertNotNull(start)
                assertTrue(start!!.performClick())
                assertNotNull(a.current?.sessionId)
            }
            capture(a, "game-${game.key}")
        }
        instrumentation.runOnMainSync {
            a.resetTo(HomeScreen(a), HistoryScreen(a))
            assertEquals(originalGames, a.repo.sessions().map { GameCodec.encodeSession(it).toString() })
        }
        capture(a, "history")
        instrumentation.runOnMainSync { a.resetTo(HomeScreen(a), SettingsScreen(a)) }
        capture(a, "settings")
        assertEquals(originalSettings, GameCodec.encodeSettings(a.settings).toString())
    }
}
