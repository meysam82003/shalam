package com.meysam.divanemtiaz

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntranceAndTrackSmokeTest {
    @Test fun launcherShowsTranslucentPlaqueAndHandsOffOnce() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        assertEquals(LaunchActivity::class.java.name, intent.component?.className)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val monitor = instrument.addMonitor(MainActivity::class.java.name, null, false)
        try {
            val launch = instrument.startActivitySync(intent) as LaunchActivity
            instrument.runOnMainSync {
                val translucent = TypedValue()
                launch.theme.resolveAttribute(android.R.attr.windowIsTranslucent, translucent, true)
                assertTrue(translucent.data != 0)
                val content = launch.findViewById<ViewGroup>(android.R.id.content)
                val overlay = content.getChildAt(0) as ViewGroup
                assertTrue(overlay.getChildAt(0).width in 1 until overlay.width)
            }
            val main = requireNotNull(instrument.waitForMonitorWithTimeout(monitor, 8000)) as MainActivity
            instrument.runOnMainSync {
                assertFalse(main.current is SplashScreen)
                main.finish()
            }
        } finally {
            instrument.removeMonitor(monitor)
        }
    }

    @Test fun scoreTrackFillsFromLeftInEveryPalette() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val original = ThemeEngine.spec
            try {
                for (theme in Themes.all) {
                    ThemeEngine.apply(theme)
                    for (fill in listOf(Royal.turquoise, Royal.gold)) {
                        val bitmap = Bitmap.createBitmap(220, 22, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        fun draw(ratio: Float) {
                            bitmap.eraseColor(0)
                            ScoreTrackDrawable(1f, ratio, fill).apply { setBounds(0, 0, 220, 22); draw(canvas) }
                        }
                        draw(0f)
                        val empty = bitmap.getPixel(190, 11)
                        assertEquals(Royal.metal, bitmap.getPixel(5, 11))
                        draw(25f / 1165)
                        assertEquals(fill, bitmap.getPixel(16, 11))
                        draw(800f / 1165)
                        assertEquals(fill, bitmap.getPixel(100, 11))
                        assertNotEquals(empty, fill)
                        assertEquals(empty, bitmap.getPixel(190, 11))
                        draw(1f)
                        assertEquals(fill, bitmap.getPixel(190, 11))
                        bitmap.recycle()
                    }
                }
            } finally { ThemeEngine.apply(original) }
        }
    }
}
