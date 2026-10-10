package com.meysam.divanemtiaz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiRegressionTest {
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    private fun content(a: MainActivity): ViewGroup = a.findViewById(android.R.id.content)
    private fun layout(a: MainActivity): View {
        val v = content(a)
        val m = a.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(m.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return v
    }
    private fun click(a: MainActivity, description: String) {
        val target = descendants(content(a)).firstOrNull { it.isClickable && it.contentDescription?.toString() == description }
        assertNotNull("Missing action: $description", target)
        assertTrue(target!!.performClick())
        layout(a)
    }
    private fun capture(a: MainActivity, name: String) {
        val v = layout(a)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        val dir = File("build/ui-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
    }
    private fun session(a: MainActivity, game: GameType = GameType.SHALAM): GameSession = GameSession(
        a.repo.newSessionId(), game,
        (0 until game.minSides).map { Side(if (it == 0) "ما" else if (it == 1) "اونا" else "بازیکن ${it + 1}", it) },
        rules = a.settings.rulesFor(game)
    )

    @Test fun selectingContractAndPointsKeepsTheScreenAndSavesTheSameInputs() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = c.get()
        a.updateSettings { it.copy(shalam = it.shalam.copy(keyboardInput = false)) }
        val s = session(a)
        val rulesBefore = GameCodec.encodeRules(s.rules).toString()
        a.resetTo(HomeScreen(a), ShalamBoardScreen(a, s), ShalamHandScreen(a, s, null))
        layout(a)
        val before = descendants(content(a)).first { it is android.widget.ScrollView }
        click(a, "حاکم ما")
        click(a, "۱۳۵")
        click(a, "پیک")
        assertSame(before, descendants(content(a)).first { it is android.widget.ScrollView })
        capture(a, "shalam-contract")
        click(a, "مرحلهٔ بعد: امتیاز حریف")
        val pointsScreen = descendants(content(a)).first { it is android.widget.ScrollView }
        click(a, "۰")
        assertSame(pointsScreen, descendants(content(a)).first { it is android.widget.ScrollView })
        capture(a, "shalam-points")
        click(a, "ثبت این دست")
        assertEquals(1, s.rounds.size)
        assertEquals(135, s.rounds.single().bid)
        assertEquals(0, s.rounds.single().taken)
        assertEquals(Suit.SPADE, s.rounds.single().suit)
        assertEquals(0, s.rounds.single().contractTeam)
        assertEquals(rulesBefore, GameCodec.encodeRules(s.rules).toString())
        val stored = a.repo.session(s.id)!!
        assertEquals(GameEngine.totals(s), GameEngine.totals(stored))
        assertEquals(s.rounds, stored.rounds)
        capture(a, "shalam-board")
        c.pause().stop().destroy()
    }

    @Test @Config(qualifiers = "w320dp-h700dp-xhdpi")
    fun narrowNumberPadAndLongButtonsShowAllText() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = c.get()
        a.updateSettings { it.copy(general = it.general.copy(largeText = true, uiScale = 115)) }
        val kit = a.kit
        val grid = kit.grid(6, (100..125 step 5).map { kit.chip(kit.n(it), false) {} })
        val width = (a.resources.displayMetrics.widthPixels - 2 * kit.dp(14))
        grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
        descendants(grid).filterIsInstance<TextView>().forEach { tv ->
            val l = tv.layout
            assertTrue("number split across lines: ${tv.text}", l.lineCount == 1)
            assertEquals(0, l.getEllipsisCount(0))
            assertTrue(l.getLineWidth(0) <= tv.width - tv.paddingLeft - tv.paddingRight + 1)
        }
        val button = kit.button("پشتیبان‌گیری و بازگردانی همهٔ داده‌ها", icon = RoyalIcon.SAVE) {}
        button.measure(View.MeasureSpec.makeMeasureSpec(width / 2, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        button.layout(0, 0, button.measuredWidth, button.measuredHeight)
        val tv = descendants(button).filterIsInstance<TextView>().single()
        assertTrue(tv.right <= button.width - button.paddingRight)
        assertTrue(tv.layout.lineCount > 1)
        assertEquals(tv.text.length, tv.layout.getLineEnd(tv.layout.lineCount - 1))
        for (line in 0 until tv.layout.lineCount) assertEquals(0, tv.layout.getEllipsisCount(line))
        a.resetTo(HomeScreen(a))
        capture(a, "home-narrow-large")
        a.resetTo(SettingsScreen(a))
        capture(a, "settings-narrow-large")
        c.pause().stop().destroy()
    }

    @Test fun allMainScreensAndFourGamesRenderWithoutChangingRulesOrStoredGames() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = c.get()
        val settings = GameCodec.encodeSettings(a.settings).toString()
        val screens = listOf<Screen>(HomeScreen(a), HistoryScreen(a), PlayersScreen(a),
            SettingsScreen(a), RulesScreen(a, null), LeagueListScreen(a),
            DeckCalcScreen(a), RankingScreen(a), BackupScreen(a))
        screens.forEach { screen ->
            a.resetTo(screen)
            capture(a, screen.javaClass.simpleName)
        }
        GameType.values().forEach { game ->
            a.resetTo(SetupScreen(a, game))
            capture(a, "setup-${game.key}")
            val s = session(a, game)
            val rules = GameCodec.encodeRules(s.rules).toString()
            a.resetTo(a.boardFor(s))
            capture(a, "board-${game.key}")
            assertEquals(rules, GameCodec.encodeRules(s.rules).toString())
        }
        assertEquals(settings, GameCodec.encodeSettings(a.settings).toString())
        assertTrue(a.repo.sessions().isEmpty())
        c.pause().stop().destroy()
    }

    @Test fun splashDoesNotImposeTheOldOnePointFourSecondDelay() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertTrue(c.get().current is HomeScreen)
        c.pause().stop().destroy()
    }
}
