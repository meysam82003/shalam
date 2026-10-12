package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
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

    /** Scroll position of the page, kept while another page is on top and across rebuilds. */
    var savedScroll = 0

    /** Pages of a running game keep a compact page bar instead of the large title. */
    open val compactHeader: Boolean get() = sessionId != null

    /** Tab of the bottom navigation this page belongs to ([NavBar]); -1 = no navigation bar. */
    open val navTab: Int get() = -1

    /** Where on this page ads may appear (home, history, …); null for every game, timer and input page. */
    open val adZone: String? get() = null

    /** Module of this page for the free / Pro rules of the panel (null: always open). */
    open val featureKey: String? get() = null

    /** For count-limited modules: how many the player already has when this page creates a new one (-1: not a create page). */
    open val featureUsed: Int get() = -1

    /** Session shown by this screen, used to restore the game after process death. */
    open val sessionId: Long? get() = null

    /** Subtitle of the page bar (pages update it in place). */
    var subtitleView: android.widget.TextView? = null
        private set

    fun scaffold(
        title: String,
        subtitle: String? = null,
        showBack: Boolean = true,
        actions: List<View> = emptyList(),
        bottom: View? = null,
        content: LinearLayout.() -> Unit
    ): View {
        val root = kit.vertical().apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val light = ThemeEngine.spec.light
        val barFill = android.graphics.drawable.ColorDrawable(Royal.alpha(Royal.scrim, if (light) 0.86f else 0.80f)).apply { alpha = 0 }
        val bar = kit.horizontal().apply {
            setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(6))
            minimumHeight = kit.dp(54)
            background = barFill
        }
        if (showBack) {
            bar.addView(kit.iconButton(RoyalIcon.BACK, "بازگشت", ButtonKind.SECONDARY, 42) { host.onBackPressedCompat() })
            bar.addView(kit.hgap(10))
        }
        // Game pages keep a compact title; other pages show a large title that slides into the bar on scroll.
        val large = !compactHeader
        val smallTitle = kit.vertical().apply {
            addView(kit.text(title, if (large) TextStyle.HEADING else TextStyle.TITLE, Royal.title, maxLines = 1))
            subtitleView = null
            if (!large && !subtitle.isNullOrBlank()) addView(kit.text(subtitle, TextStyle.CAPTION, Royal.muted, maxLines = 1).also { subtitleView = it })
            if (large) alpha = 0f
        }
        bar.addView(smallTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.forEach {
            bar.addView(kit.hgap(6))
            bar.addView(it)
        }
        root.addView(bar)
        val hairline = View(host).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(0, Royal.alpha(Royal.gold, if (light) 0.35f else 0.55f), 0)
            )
            if (large) alpha = 0f
        }
        root.addView(hairline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(1)))
        val header = if (large) kit.vertical().apply {
            setPadding(kit.dp(4), kit.dp(2), kit.dp(4), kit.dp(14))
            addView(kit.text(title, TextStyle.DISPLAY, Royal.title, maxLines = 2).apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, kit.textSize(26f)) })
            if (!subtitle.isNullOrBlank()) addView(kit.text(subtitle, TextStyle.LABEL, Royal.muted, maxLines = 2).also { subtitleView = it })
            addView(View(host).apply {
                background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT,
                    ThemeEngine.spec.primary).apply { cornerRadius = kit.dpf(2f) }
            }, LinearLayout.LayoutParams(kit.dp(34), kit.dp(3)).apply { topMargin = kit.dp(8) })
        } else null
        val column = kit.vertical().apply {
            setPadding(kit.dp(16), kit.dp(if (large) 2 else 10), kit.dp(16), kit.dp(22))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            if (header != null) addView(header, kit.fill())
            content()
        }
        root.addView(object : ScrollView(host) {
            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                super.onScrollChanged(l, t, oldl, oldt)
                if (header == null) return
                val f = (t / maxOf(1f, header.height * 0.75f)).coerceIn(0f, 1f)
                smallTitle.alpha = f
                hairline.alpha = f
                barFill.alpha = (255 * f).toInt()
            }
        }.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(column)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (bottom != null) {
            root.addView(FrameLayout(host).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Royal.alpha(Royal.scrim, 0.2f), Royal.alpha(Royal.scrim, 0.92f))
                )
                setPadding(kit.dp(14), kit.dp(8), kit.dp(14), kit.dp(10))
                addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            })
        }
        if (navTab >= 0) root.addView(NavBar.view(this, navTab))
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
    private lateinit var frame: LinearLayout
    private val proListener: () -> Unit = { handler.post { onProChanged() } }
    private lateinit var topAd: FrameLayout
    private lateinit var bottomAd: FrameLayout
    private lateinit var rootView: FrameLayout
    private val stack = ArrayList<Screen>()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false

    val current: Screen? get() = stack.lastOrNull()

    /** Mafia narrator games and settings, kept in the same store as the score games. */
    val mafia: MafiaRepository by lazy { MafiaRepository(repo.store) }

    /** «پانتومیم» matches and words, and «جاسوس» games and words, in the same store. */
    val panto: PantoRepository by lazy { PantoRepository(repo.store) }
    val spy: SpyRepository by lazy { SpyRepository(repo.store) }

    /** Narrator cues, night music and voice recording. */
    val audio: MafiaAudio by lazy { MafiaAudio(this) }
    private var audioUsed = false
    fun audio(): MafiaAudio { audioUsed = true; return audio }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = GameStore.open(this)
        settings = repo.settings()
        kit = RoyalKit(this) { settings }
        ThemeEngine.apply(Themes.byId(settings.general.theme))
        PhotoStore.init(this)
        ProState.init(this)
        AdEngine.init(this)
        MafiaRoleRegistry.user = mafia.roleDefs()
        val root = FrameLayout(this).apply {
            background = BackdropDrawable(resources.displayMetrics.density)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        rootView = root
        container = FrameLayout(this)
        // Pages sit between two fixed ad bars (hidden unless the panel turns them on); the bars take their own
        // space, so they never cover a button, and they stay still while pages change.
        frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        topAd = FrameLayout(this).apply { visibility = View.GONE }
        bottomAd = FrameLayout(this).apply { visibility = View.GONE }
        frame.addView(topAd, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        frame.addView(bottomAd, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ProState.listeners += proListener
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
        // Plans, module access and the account are refreshed in the background; games never wait for it.
        if (Build.FINGERPRINT != "robolectric") ProSync.onStart()
        resumed = true
        current?.onShow()
    }

    override fun onPause() {
        AdEngine.flush()
        current?.onHide()
        resumed = false
        if (audioUsed) {
            audio.stopMusic()
            if (audio.isRecording) audio.stopRecording(discard = true)
        }
        super.onPause()
    }

    override fun onDestroy() {
        ProState.listeners -= proListener
        if (audioUsed) audio.release()
        super.onDestroy()
    }

    /**
     * Draws behind the status and navigation bars on every Android version (Android 15 forces this)
     * and pads the content by the bars, display cutout and keyboard so nothing is hidden under them.
     */
    @Suppress("DEPRECATION")
    private fun fitSystemBars(root: FrameLayout) {
        paintSystemBars()
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
                frame.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            } else {
                frame.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
    }

    /** Bar colours of the active style; light styles get dark status and navigation icons. */
    @Suppress("DEPRECATION")
    private fun paintSystemBars() {
        val light = ThemeEngine.spec.light
        window.statusBarColor = Royal.alpha(Royal.scrim, if (light) 0.72f else 0.55f)
        window.navigationBarColor = Royal.alpha(Royal.scrim, if (light) 0.9f else 0.75f)
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else if (Build.VERSION.SDK_INT >= 23) {
            var flags = rootView.systemUiVisibility
            val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
            flags = if (light) flags or lightFlags else flags and lightFlags.inv()
            rootView.systemUiVisibility = flags
        }
    }

    /** Switches the whole interface to another style without losing the page stack. */
    fun applyTheme(id: String) {
        val next = Themes.byId(id)
        if (next === ThemeEngine.spec) return
        ThemeEngine.apply(next)
        rootView.background = BackdropDrawable(resources.displayMetrics.density)
        paintSystemBars()
        refresh()
    }

    fun applyWindowSettings() {
        if (settings.general.keepScreenAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = settings.general.theme
        settings = transform(settings)
        repo.saveSettings(settings)
        applyWindowSettings()
        if (settings.general.theme != before) applyTheme(settings.general.theme)
    }

    fun push(screen: Screen) {
        if (!Gate.allows(this, screen)) return
        saveScroll()
        current?.onHide()
        stack += screen
        render()
    }

    /** Replaces the top screen (for example board → result) without growing the back stack. */
    fun replace(screen: Screen) {
        if (!Gate.allows(this, screen)) return
        current?.onHide()
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack += screen
        render()
    }

    fun resetTo(vararg screens: Screen) {
        screens.lastOrNull()?.let { if (!Gate.allows(this, it)) return }
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
        saveScroll()
        render(animate = false, notify = false)
    }

    /** Remembers how far the page on screen is scrolled (the last child is the page being shown). */
    private fun saveScroll() {
        val screen = current ?: return
        val page = container.getChildAt(container.childCount - 1) ?: return
        findScroll(page)?.let { screen.savedScroll = it.scrollY }
    }

    /** Puts a rebuilt or returning page back where the user left it, once it is laid out. */
    private fun restoreScroll(view: View, y: Int) {
        if (y <= 0) return
        val sv = findScroll(view) ?: return
        sv.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (sv.viewTreeObserver.isAlive) sv.viewTreeObserver.removeOnPreDrawListener(this)
                sv.scrollTo(0, y)
                return true
            }
        })
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

    private var pendingResult: ((Uri?) -> Unit)? = null

    /** Opens the system file picker; [onResult] gets the chosen document (null when cancelled). */
    fun pickDocument(create: Boolean, fileName: String, onResult: (Uri?) -> Unit) {
        val intent = if (create) {
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
        pendingResult = onResult
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_DOCUMENT)
        } catch (e: Exception) {
            pendingResult = null
            kit.toast("انتخاب فایل در این گوشی در دسترس نیست")
        }
    }

    /** Opens the system picker for any of [mimeTypes] (e.g. audio or images). */
    fun pickFile(mimeTypes: Array<String>, onResult: (Uri?) -> Unit) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        pendingResult = onResult
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_DOCUMENT)
        } catch (e: Exception) {
            pendingResult = null
            kit.toast("انتخاب فایل در این گوشی در دسترس نیست")
        }
    }

    private var pendingPermission: ((Boolean) -> Unit)? = null

    /** Asks for the microphone (Android 6+); older versions grant it at install. */
    fun requestMicrophone(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onResult(true)
            return
        }
        pendingPermission = onResult
        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MIC) return
        val callback = pendingPermission ?: return
        pendingPermission = null
        callback(grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DOCUMENT) return
        val callback = pendingResult ?: return
        pendingResult = null
        callback(if (resultCode == RESULT_OK) data?.data else null)
    }

    /**
     * Draws [view] at the given width into a PNG and opens the share sheet. The view is laid out
     * off screen, so it never disturbs the current page.
     */
    fun shareImage(view: View, title: String, widthPx: Int = kit.dp(420)) {
        try {
            view.layoutDirection = View.LAYOUT_DIRECTION_RTL
            view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val bitmap = Bitmap.createBitmap(view.measuredWidth.coerceAtLeast(1), view.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            BackdropDrawable(resources.displayMetrics.density).apply { setBounds(0, 0, bitmap.width, bitmap.height) }.draw(canvas)
            view.draw(canvas)
            val dir = java.io.File(cacheDir, ShareProvider.DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = java.io.File(dir, "divan-${System.currentTimeMillis()}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val uri = Uri.parse("content://${ShareProvider.authority(packageName)}/${file.name}")
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, title)
                clipData = android.content.ClipData.newRawUri(title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "اشتراک تصویر").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            kit.toast("ساخت تصویر ممکن نشد")
        }
    }

    /** Sends a text file (a backup) to any app the user picks, e.g. a messenger or cloud drive. */
    fun shareFile(name: String, text: String, mime: String) {
        try {
            val dir = java.io.File(cacheDir, ShareProvider.DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = java.io.File(dir, name)
            file.writeText(text, Charsets.UTF_8)
            val uri = Uri.parse("content://${ShareProvider.authority(packageName)}/${file.name}")
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri(name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "ارسال فایل پشتیبان").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            kit.toast("ارسال فایل ممکن نشد")
        }
    }

    /** Reads settings again after a backup replaced them. */
    fun reloadSettings() {
        settings = repo.settings()
        applyWindowSettings()
        applyTheme(settings.general.theme)
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

    private var transition = 0

    private fun render(animate: Boolean = true, forward: Boolean = true, notify: Boolean = true) {
        val screen = current ?: return
        val view = screen.build()
        restoreScroll(view, screen.savedScroll)
        if (!animate || !settings.general.animations) {
            transition++
            for (i in 0 until container.childCount) container.getChildAt(i).animate().cancel()
            container.removeAllViews()
            container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            // Slide + fade on hardware layers (RTL: a new page enters from the left, going back slides it to the right).
            // The motion starts only after the new page's first frame is drawn, so building and drawing a heavy page
            // never eats the animation's frames.
            val shift = container.width.takeIf { it > 0 }?.let { it * 0.22f } ?: kit.dpf(80f)
            // Only the page on screen slides out; anything left from an interrupted transition goes at once.
            val children = (0 until container.childCount).map { container.getChildAt(it) }
            children.forEach { it.animate().cancel() }
            children.dropLast(1).forEach { container.removeView(it) }
            val olds = children.takeLast(1).onEach { it.isEnabled = false; it.alpha = 1f; it.translationX = 0f }
            val id = ++transition
            container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            view.alpha = 0f
            view.translationX = if (forward) -shift else shift
            val entrance = Entrance.prepare(view, resources.displayMetrics.density)
            ArtWarmer.hold(Motion.PAGE_IN + 400)
            val start = Runnable {
                if (id != transition) return@Runnable
                olds.forEach { old ->
                    old.animate().alpha(0f).translationX(if (forward) shift * 0.6f else -shift * 0.6f).setDuration(Motion.PAGE_OUT)
                        .setInterpolator(Motion.EASE_IN_OUT).withLayer().withEndAction { container.removeView(old) }.start()
                }
                view.animate().alpha(1f).translationX(0f).setDuration(Motion.PAGE_IN).setInterpolator(Motion.EASE_OUT).withLayer().start()
                Entrance.play(entrance)
            }
            view.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                    view.post(start)
                    return true
                }
            })
        }
        if (notify && resumed) screen.onShow()
        updateStickyAds()
    }

    /** Fixed top / bottom ad bars for pages that allow them; an ad that is already showing is left in place. */
    fun updateStickyAds() {
        val screen = current
        AdEngine.sticky(screen, "sticky_top", topAd)
        AdEngine.sticky(screen, "sticky_bottom", bottomAd)
    }

    /** New plans, rules or ads from the server, Pro bought or ended: pages that show ads or Pro are redrawn. */
    private fun onProChanged() {
        val screen = current ?: return
        if (screen.adZone != null) refresh() else updateStickyAds()
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
        private const val REQUEST_DOCUMENT = 4101
        private const val REQUEST_MIC = 4102
    }
}
