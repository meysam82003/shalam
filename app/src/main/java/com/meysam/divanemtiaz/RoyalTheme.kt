package com.meysam.divanemtiaz

/** How a card's edge is drawn. */
enum class ThemeFrame {
    /** Gold gradient frame, inner hairline and corner jewels (the classic royal card). */
    ORNATE,
    /** Thin metal line with an optional inner double line. */
    HAIRLINE,
    /** No visible metal: a soft border and a tinted drop shadow. */
    SOFT,
    /** Translucent glass with a coloured, glowing edge. */
    GLOW
}

/** How buttons and chips are shaped and lit. */
enum class ThemeButton {
    /** Beveled 3D face on a darker edge (the classic royal button). */
    BEVEL,
    /** Flat face with a soft coloured shadow. */
    FLAT,
    /** Matte face, thin border and a deep shadow. */
    MATTE,
    /** Rich gradient face with a fine top gloss. */
    GRADIENT,
    /** Translucent glass with a neon glow. */
    GLASS
}

/** Texture drawn over the page background. */
enum class ThemePattern { KHATAM, LATTICE, DIAMOND, GIRIH, ARABESQUE, DOTS, NEON }

/** A radial light on the page background; positions and radius are fractions of the page width/height. */
class ThemeGlow(val x: Float, val y: Float, val radius: Float, val color: Int)

/**
 * One visual style of the app. Every widget reads its colours, shapes, lights and textures from the active
 * spec, so screens and game logic stay the same while the whole look changes.
 */
class ThemeSpec(
    val id: String,
    val title: String,
    val latin: String,
    val summary: String,
    val light: Boolean,
    // Page
    val bg: IntArray,
    val glows: List<ThemeGlow>,
    val vignette: Int,
    val pattern: ThemePattern,
    val patternColor: Int,
    // Cards
    val surface: Int, val surfaceLow: Int,
    val raised: Int, val raisedLow: Int,
    val flat: Int, val flatLow: Int,
    val border: Int, val borderLow: Int,
    val inner: Int,
    val jewels: Boolean,
    val frame: ThemeFrame,
    val frameWidth: Float,
    val shadow: Int,
    val shadowDepth: Float,
    val gloss: Float,
    val radius: Float,
    // Accent (headings, icons, selection) and the metal of frames, medallions and seals
    val accent: Int, val accentText: Int, val accentDeep: Int, val accentShadow: Int,
    val metalLight: Int, val metal: Int, val metalDeep: Int, val metalShadow: Int,
    val success: Int, val successText: Int, val successDeep: Int,
    val danger: Int, val dangerText: Int, val dangerDeep: Int,
    val text: Int, val muted: Int, val dim: Int, val titleColor: Int,
    val onAccent: Int,
    val scrim: Int,
    val lapis: Int, val lapisLight: Int,
    // Buttons
    val button: ThemeButton,
    val primary: IntArray, val primaryHorizontal: Boolean, val primaryText: Int, val primaryEdge: Int,
    val secondary: IntArray, val secondaryText: Int, val secondaryBorder: Int,
    val chip: IntArray, val chipText: Int, val chipBorder: Int,
    val successFill: IntArray, val successOn: Int,
    val dangerFill: IntArray, val dangerOn: Int,
    val buttonRadius: Float,
    val glow: Boolean,
    // Timers
    val ring: IntArray,
    val ringTrack: Int,
    /** Colours of the theme's swatch in the theme picker. */
    val swatch: IntArray,
    val layout: ThemeLayout
)

/** Persistent ids stay stable across upgrades; the layout is a separate visual identity. */
enum class ThemeLayout { ATLAS, PAPER, ORBIT, PALM, VOLT, WAVE }

private fun c(v: Long): Int = v.toInt()

object Themes {
    private fun style(id: String, title: String, latin: String, summary: String, layout: ThemeLayout,
        light: Boolean, page: Long, card: Long, ink: Long, muted: Long, accent: Long, on: Long,
        edge: Long, radius: Float, frame: ThemeFrame, button: ThemeButton, pattern: ThemePattern): ThemeSpec {
        val bg = c(page); val surface = c(card); val text = c(ink); val a = c(accent); val onA = c(on)
        val line = c(edge)
        val success = c(if (light) 0xFF126348 else 0xFF6CE8BA)
        val danger = c(if (light) 0xFFAE233B else 0xFFFF9DA7)
        val metalLight = c(when (layout) {
            ThemeLayout.ORBIT -> 0xFFF1D6FF
            ThemeLayout.PALM, ThemeLayout.WAVE -> 0xFFE0F3FF
            else -> if (light) 0xFFE5C67B else 0xFFFFE6A4
        })
        val metalDeep = c(when (layout) {
            ThemeLayout.ORBIT -> 0xFF654083
            ThemeLayout.PALM, ThemeLayout.WAVE -> 0xFF507897
            else -> if (light) 0xFF9D7431 else 0xFF8B6028
        })
        return ThemeSpec(id, title, latin, summary, light,
            intArrayOf(bg, bg, bg), emptyList(), 0, pattern, a,
            surface, surface, surface, surface, bg, bg, line, metalDeep, (a and 0x00FFFFFF) or 0x18000000, false,
            frame, 1.2f, c(if (light) 0x30232116 else 0xAA000000), 4f, .12f, radius,
            a, a, a, metalDeep, metalLight, if (layout == ThemeLayout.ATLAS || layout == ThemeLayout.VOLT || layout == ThemeLayout.PAPER) c(0xFFD3A851) else a, metalDeep, c(0xFF352716),
            success, success, success, danger, danger, danger,
            text, c(muted), c(muted), text, onA, bg, surface, surface,
            button, if (light) intArrayOf(a, a, a) else intArrayOf(metalLight, a, c(when (layout) { ThemeLayout.ORBIT -> 0xFFBB85E4; ThemeLayout.PALM -> 0xFF76B9E4; else -> 0xFFD49C40 })), true, onA, metalDeep,
            intArrayOf(surface, surface, bg), text, line,
            intArrayOf(surface, surface, bg), text, line,
            intArrayOf(c(if (light) 0xFF197B59 else 0xFF267F62), c(if (light) 0xFF126348 else 0xFF17543E), c(0xFF093C2A)), c(0xFFFFFFFF),
            intArrayOf(c(0xFFCC344E), c(0xFFA6233B), c(0xFF651328)), c(0xFFFFFFFF),
            radius, layout == ThemeLayout.ORBIT,
            intArrayOf(c(0xFF57D9DC), a, success), (a and 0x00FFFFFF) or 0x24000000, intArrayOf(bg, a, surface), layout)
    }
    val COBALT = style("cobalt", "زمرد سلطنتی", "ROYAL EMERALD", "مخمل سبز، فلز طلایی و نور گرم", ThemeLayout.ATLAS,
        false, 0xFF020F10, 0xFF082A26, 0xFFFFF5DD, 0xFFB2C8BF, 0xFFF2CA79, 0xFF271B08, 0xFF6B714D,
        1f, ThemeFrame.ORNATE, ThemeButton.BEVEL, ThemePattern.LATTICE)
    val IRANIC = style("iranic", "مروارید", "PEARL ATELIER", "مرمر روشن، قاب شامپاینی و بافت کاغذ", ThemeLayout.PAPER,
        true, 0xFFF3EDE1, 0xFFFFFCF5, 0xFF28241F, 0xFF686057, 0xFF805522, 0xFFFFFFFF, 0xFFC4B18B,
        .85f, ThemeFrame.HAIRLINE, ThemeButton.MATTE, ThemePattern.GIRIH)
    val NEON = style("neon", "تئاتر نئون", "NEON THEATRE", "بنفش شب، شیشهٔ رنگی و نورهای صحنه", ThemeLayout.ORBIT,
        false, 0xFF080717, 0xFF21173B, 0xFFF6F0FF, 0xFFC2B3DA, 0xFFE2BCFF, 0xFF21112F, 0xFF7956A2,
        1.2f, ThemeFrame.GLOW, ThemeButton.GLASS, ThemePattern.NEON)
    val EMERALD = style("emerald", "نیمه‌شب", "MIDNIGHT NOIR", "آبی نفتی، فولاد صیقلی و نور یخی", ThemeLayout.PALM,
        false, 0xFF040D19, 0xFF10253B, 0xFFF1F8FF, 0xFFAABFD4, 0xFF9CD7FF, 0xFF0B1E33, 0xFF476A8C,
        1.15f, ThemeFrame.GLOW, ThemeButton.GRADIENT, ThemePattern.ARABESQUE)
    val COPPER = style("copper", "مخمل سرخ", "CRIMSON VELVET", "زرشکی عمیق، طلای سرخ و قاب‌های تراش‌خورده", ThemeLayout.VOLT,
        false, 0xFF17060D, 0xFF351420, 0xFFFFF0E8, 0xFFD4B1B2, 0xFFF8C498, 0xFF30140F, 0xFF91614F,
        .9f, ThemeFrame.ORNATE, ThemeButton.BEVEL, ThemePattern.DIAMOND)
    val IVORY = style("ivory", "کریستال", "CRYSTAL PALACE", "شیشهٔ روشن، آبی بلوری و نور نقره‌ای", ThemeLayout.WAVE,
        true, 0xFFEAF2F7, 0xFFFCFEFF, 0xFF172A42, 0xFF50677E, 0xFF245D9C, 0xFFFFFFFF, 0xFFA8C2D9,
        1.1f, ThemeFrame.SOFT, ThemeButton.FLAT, ThemePattern.DOTS)

    val all = listOf(COBALT, IRANIC, NEON, EMERALD, COPPER, IVORY)
    val FALLBACK = COBALT
    fun byId(id: String): ThemeSpec = all.firstOrNull { it.id == id } ?: FALLBACK
    fun isChosen(id: String): Boolean = all.any { it.id == id }
}

object ThemeEngine {
    @Volatile var spec: ThemeSpec = Themes.FALLBACK
        private set

    /** Changes with every switch, so cached artwork of the previous style is never reused. */
    @Volatile var version: Int = 0
        private set

    fun apply(next: ThemeSpec) {
        if (next === spec && version > 0) return
        spec = next
        version++
        Royal.night = next.onAccent
        Royal.scrim = next.scrim
        Royal.deep = next.raisedLow
        Royal.lapis = next.lapis
        Royal.lapisLight = next.lapisLight
        Royal.panelTop = next.surface
        Royal.panelBottom = next.surfaceLow
        Royal.gold = next.accent
        Royal.goldLight = next.accentText
        Royal.goldDeep = next.accentDeep
        Royal.bronze = next.accentShadow
        Royal.metalLight = next.metalLight
        Royal.metal = next.metal
        Royal.metalDeep = next.metalDeep
        Royal.metalShadow = next.metalShadow
        Royal.turquoise = next.success
        Royal.turquoiseLight = next.successText
        Royal.turquoiseDeep = next.successDeep
        Royal.crimson = next.danger
        Royal.crimsonLight = next.dangerText
        Royal.crimsonDeep = next.dangerDeep
        Royal.ivory = next.text
        Royal.muted = next.muted
        Royal.dim = next.dim
        Royal.title = next.titleColor
        Royal.alert = if (next.light) next.dangerText else 0xFFFF4F58.toInt()
    }
}

/**
 * The first picture before the app has drawn anything, in the colours of the chosen style (no white or navy flash):
 * Android 13+ keeps a splash theme per style for the next start; the web versions keep the colours for their
 * loading page (the host mirrors this preferences file into the browser's local storage).
 */
object LaunchLook {
    const val PREFS = "divan_launch"

    private fun hex(c: Int) = "#" + Integer.toHexString(c and 0xFFFFFF or 0x1000000).substring(1).uppercase()

    fun remember(activity: android.app.Activity, spec: ThemeSpec) {
        val look = org.json.JSONObject()
            .put("id", spec.id).put("light", spec.light)
            .put("b1", hex(spec.bg[0])).put("b2", hex(spec.bg[1])).put("b3", hex(spec.bg[2]))
            .put("title", hex(spec.accentText)).put("gold", hex(spec.accent)).put("muted", hex(spec.muted)).put("dim", hex(spec.dim))
            .toString()
        val prefs = activity.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (prefs.getString("look", null) != look) prefs.edit().putString("look", look).apply()
        if (android.os.Build.VERSION.SDK_INT >= 33) runCatching {
            val style = activity.resources.getIdentifier("Theme.Divan.Launch." + spec.id.replaceFirstChar { it.uppercase() }, "style", activity.packageName)
            if (style != 0) activity.splashScreen.setSplashScreenTheme(style)
        }
    }
}

/** Bottom navigation of the main pages: home, leagues, history, ranking and settings. */
object NavBar {
    const val HOME = 0
    const val LEAGUES = 1
    const val HISTORY = 2
    const val RANKING = 3
    const val SETTINGS = 4

    private val items = listOf(
        Triple(HOME, RoyalIcon.HOME, "خانه"),
        Triple(LEAGUES, RoyalIcon.TROPHY, "لیگ‌ها"),
        Triple(HISTORY, RoyalIcon.HISTORY, "تاریخچه"),
        Triple(RANKING, RoyalIcon.CROWN, "رنک‌بندی"),
        Triple(SETTINGS, RoyalIcon.SETTINGS, "تنظیمات")
    )

    fun view(screen: Screen, selected: Int): android.view.View {
        val kit = screen.kit
        val host = screen.host
        val t = ThemeEngine.spec
        return android.widget.FrameLayout(host).apply {
            setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(8))
            addView(kit.horizontal(android.view.Gravity.CENTER).apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 26f)
                setPadding(kit.dp(6), kit.dp(5), kit.dp(6), kit.dp(9))
                items.forEach { (id, icon, label) ->
                    val on = id == selected
                    addView(kit.vertical(android.view.Gravity.CENTER_HORIZONTAL).apply {
                        setPadding(0, kit.dp(5), 0, kit.dp(5))
                        if (on) background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = kit.dpf(DivanDesign.radius(t, if (t.layout == ThemeLayout.WAVE) 30f else 12f))
                            colors = intArrayOf(Royal.alpha(t.accent, if (t.light) .15f else .23f), Royal.alpha(t.accent, .035f))
                            setStroke(kit.dp(1), Royal.alpha(t.accent, .5f))
                        }
                        minimumHeight = kit.dp(46)
                        addView(kit.icon(icon, if (on) Royal.goldLight else Royal.muted, 22))
                        addView(kit.text(label, TextStyle.CAPTION, if (on) Royal.goldLight else Royal.muted, android.view.Gravity.CENTER, 1).apply {
                            if (on) typeface = RoyalFonts.get(context, true)
                        })
                        isClickable = true
                        contentDescription = label
                        setOnClickListener {
                            kit.tap(it)
                            if (id == selected) return@setOnClickListener
                            when (id) {
                                HOME -> host.resetTo(HomeScreen(host))
                                LEAGUES -> host.resetTo(HomeScreen(host), LeagueListScreen(host))
                                HISTORY -> host.resetTo(HomeScreen(host), HistoryScreen(host))
                                RANKING -> host.resetTo(HomeScreen(host), RankingScreen(host))
                                SETTINGS -> host.resetTo(HomeScreen(host), SettingsScreen(host))
                            }
                        }
                    }, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = kit.dp(2); marginEnd = kit.dp(2)
                    })
                }
            }, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}
