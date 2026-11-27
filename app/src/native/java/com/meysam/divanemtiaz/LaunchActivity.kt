package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import org.json.JSONObject

/** The launcher stays visible around the plaque until the opaque game activity takes over. */
class LaunchActivity : Activity() {
    private lateinit var root: FrameLayout
    private var deadline = 0L
    private var resumed = false
    private val enter = Runnable {
        if (resumed && !isFinishing) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // Tapping the launcher icon again should immediately reveal the existing game.
        if (!isTaskRoot) { finish(); return }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        val settings = GameStore.open(this).settings()
        val look = getSharedPreferences(LaunchLook.PREFS, MODE_PRIVATE).getString("look", null)
        val palette = look?.let { runCatching { JSONObject(it).getString("id") }.getOrNull() }
        Themes.configureGeometry(settings.general.paletteGeometry.ifBlank { settings.general.theme })
        ThemeEngine.apply(Themes.byId(palette ?: settings.general.theme))
        val kit = RoyalKit(this) { settings }
        val animated = settings.general.animations
        val fan = SplashFanView(this)
        val title = kit.text("دیوان امتیاز", TextStyle.DISPLAY, Royal.title, Gravity.CENTER)
        val plaque = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(ThemeEngine.spec.surface)
                cornerRadius = kit.dpf(30f)
                setStroke(kit.dp(2), Royal.metal)
            }
            elevation = kit.dpf(8f)
            for (bottom in listOf(false, true)) {
                val corner = ImageView(this@LaunchActivity).apply {
                    setImageResource(R.drawable.launch_engraving)
                    setColorFilter(Royal.metal, PorterDuff.Mode.SRC_IN)
                    alpha = 0.68f
                    rotation = if (bottom) 180f else 0f
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val anchor = if (bottom) Gravity.BOTTOM or Gravity.RIGHT else Gravity.TOP or Gravity.LEFT
                addView(corner, FrameLayout.LayoutParams(kit.dp(54), kit.dp(54), anchor).apply {
                    setMargins(kit.dp(10), kit.dp(10), kit.dp(10), kit.dp(10))
                })
            }
            addView(kit.vertical(Gravity.CENTER).apply {
                setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(17))
                addView(fan, LinearLayout.LayoutParams(-1, kit.dp(137)))
                addView(title, kit.fill())
            }, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(86, 0, 0, 0))
            val width = minOf(kit.dp(222), (resources.displayMetrics.widthPixels - kit.dp(28)).coerceAtLeast(1))
            addView(plaque, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
            setOnClickListener { enter.run() }
        }
        setContentView(root)
        if (animated) {
            plaque.scaleX = 0.65f
            plaque.scaleY = 0.65f
            plaque.alpha = 0f
            title.alpha = 0f
            title.translationY = kit.dpf(9f)
            plaque.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(390).setInterpolator(Motion.SPRING).start()
            fan.play()
            title.animate().alpha(1f).translationY(0f).setStartDelay(910).setDuration(360).setInterpolator(Motion.SPRING).start()
        } else fan.still()
        deadline = SystemClock.uptimeMillis() + if (animated) 2000L else SplashScreen.LOGO_MS
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::root.isInitialized) root.postDelayed(enter, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
    }

    override fun onPause() {
        resumed = false
        if (::root.isInitialized) root.removeCallbacks(enter)
        super.onPause()
    }
}
