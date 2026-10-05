package com.meysam.divanemtiaz

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

abstract class Screen(val host: MainActivity) {
    val kit: RoyalKit get() = host.kit
    val settings: AppSettings get() = host.settings

    abstract fun build(): View

    /** Returns true when the screen handled back itself. */
    open fun onBack(): Boolean = false
    open fun onShow() {}
    open fun onHide() {}

    /** Session shown by this screen, used to restore the game after process death. */
    open val sessionId: Long? get() = null

    fun scaffold(
        title: String,
        subtitle: String? = null,
        showBack: Boolean = true,
        actions: List<View> = emptyList(),
        bottom: View? = null,
        content: LinearLayout.() -> Unit
    ): View {
        val root = kit.vertical().apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val bar = kit.horizontal().apply {
            setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(5))
            minimumHeight = kit.dp(50)
        }
        if (showBack) {
            bar.addView(kit.iconButton(RoyalIcon.BACK, "بازگشت", ButtonKind.SECONDARY, 38) { host.onBackPressedCompat() })
            bar.addView(kit.hgap(8))
        }
        bar.addView(kit.vertical().apply {
            addView(kit.text(title, TextStyle.TITLE, Royal.goldLight, maxLines = 1))
            if (!subtitle.isNullOrBlank()) addView(kit.text(subtitle, TextStyle.CAPTION, Royal.muted, maxLines = 1))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.forEach {
            bar.addView(kit.hgap(6))
            bar.addView(it)
        }
        root.addView(bar)
        root.addView(View(host).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(0, Royal.alpha(Royal.gold, 0.55f), 0)
            )
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(1)))
        val column = kit.vertical().apply {
            setPadding(kit.dp(14), kit.dp(10), kit.dp(14), kit.dp(20))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            content()
        }
        root.addView(ScrollView(host).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(column)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (bottom != null) {
            root.addView(FrameLayout(host).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Royal.alpha(Royal.night, 0.2f), Royal.alpha(Royal.night, 0.92f))
                )
                setPadding(kit.dp(14), kit.dp(8), kit.dp(14), kit.dp(10))
                addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            })
        }
        return root
    }
}

class MainActivity : Activity() {
    lateinit var repo: GameRepository
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var kit: RoyalKit
        private set
    private lateinit var container: FrameLayout
    private val stack = ArrayList<Screen>()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false

    val current: Screen? get() = stack.lastOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = GameStore.open(this)
        settings = repo.settings()
        kit = RoyalKit(this) { settings }
        val root = FrameLayout(this).apply {
            background = BackdropDrawable(resources.displayMetrics.density)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        container = FrameLayout(this)
        root.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        fitSystemBars(root)
        applyWindowSettings()
        val restored = savedInstanceState?.getLong(STATE_SESSION, 0L)?.takeIf { it != 0L }?.let { repo.session(it) }
        if (restored != null) {
            stack += HomeScreen(this)
            stack += if (restored.finished) ResultScreen(this, restored) else boardFor(restored)
            render(animate = false)
        } else if (savedInstanceState != null) {
            stack += HomeScreen(this)
            render(animate = false)
        } else {
            stack += SplashScreen(this)
            render(animate = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        stack.lastOrNull { it.sessionId != null }?.sessionId?.let { outState.putLong(STATE_SESSION, it) }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        current?.onShow()
    }

    override fun onPause() {
        current?.onHide()
        resumed = false
        super.onPause()
    }

    /**
     * Draws behind the status and navigation bars on every Android version (Android 15 forces this)
     * and pads the content by the bars, display cutout and keyboard so nothing is hidden under them.
     */
    @Suppress("DEPRECATION")
    private fun fitSystemBars(root: FrameLayout) {
        window.statusBarColor = Royal.alpha(Royal.night, 0.55f)
        window.navigationBarColor = Royal.alpha(Royal.night, 0.75f)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            root.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                container.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            } else {
                container.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
    }

    fun applyWindowSettings() {
        if (settings.general.keepScreenAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        repo.saveSettings(settings)
        applyWindowSettings()
    }

    fun push(screen: Screen) {
        current?.onHide()
        stack += screen
        render()
    }

    /** Replaces the top screen (for example board → result) without growing the back stack. */
    fun replace(screen: Screen) {
        current?.onHide()
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack += screen
        render()
    }

    fun resetTo(vararg screens: Screen) {
        current?.onHide()
        stack.clear()
        stack.addAll(screens)
        render()
    }

    fun pop() {
        if (stack.size <= 1) {
            finish()
            return
        }
        current?.onHide()
        stack.removeAt(stack.lastIndex)
        render(forward = false)
    }

    /** Rebuilds the visible screen, e.g. after its state changed. */
    fun refresh() {
        val scroll = findScroll(container)
        val y = scroll?.scrollY ?: 0
        render(animate = false, notify = false)
        findScroll(container)?.let { sv -> sv.post { sv.scrollTo(0, y) } }
    }

    fun onBackPressedCompat() {
        if (current?.onBack() == true) return
        pop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        onBackPressedCompat()
    }

    fun post(delayMs: Long, action: () -> Unit) {
        handler.postDelayed(action, delayMs)
    }

    fun cancelPosts() {
        handler.removeCallbacksAndMessages(null)
    }

    fun boardFor(session: GameSession): Screen = when (session.game) {
        GameType.SHALAM -> ShalamBoardScreen(this, session)
        GameType.MENFI -> MenfiBoardScreen(this, session)
        GameType.HEZARTAII -> HezarBoardScreen(this, session)
        GameType.DOLO -> DoloBoardScreen(this, session)
    }

    /** Opens a stored game: unfinished games go to their board, finished ones to the result page. */
    fun openSession(session: GameSession) {
        push(if (session.finished) ResultScreen(this, session) else boardFor(session))
    }

    private fun render(animate: Boolean = true, forward: Boolean = true, notify: Boolean = true) {
        val screen = current ?: return
        val view = screen.build()
        container.removeAllViews()
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (animate) {
            view.alpha = 0f
            view.translationX = kit.dpf(if (forward) -18f else 18f)
            view.animate().alpha(1f).translationX(0f).setDuration(170).start()
        }
        if (notify && resumed) screen.onShow()
    }

    private fun findScroll(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findScroll(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    companion object {
        private const val STATE_SESSION = "session_id"
    }
}
