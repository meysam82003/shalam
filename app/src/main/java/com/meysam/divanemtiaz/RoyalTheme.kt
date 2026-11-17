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
        val success = c(if (light) 0xFF14634E else 0xFF80E3BA)
        val danger = c(if (light) 0xFFB42337 else 0xFFFFA0AC)
        val depth = if (layout == ThemeLayout.VOLT) 4f else if (layout == ThemeLayout.PAPER) 0f else 2f
        return ThemeSpec(id, title, latin, summary, light,
            intArrayOf(bg, bg, bg),
            if (layout == ThemeLayout.ORBIT) listOf(ThemeGlow(.1f, .1f, 1f, c(0x304F36B8)), ThemeGlow(.95f, .6f, .8f, c(0x202FE4CF))) else emptyList(),
            0, pattern, a,
            surface, surface, surface, surface, bg, bg, line, line, 0, false,
            frame, if (layout == ThemeLayout.VOLT) 2f else .8f,
            c(if (light) 0x18223140 else 0x40000000), depth, if (layout == ThemeLayout.ORBIT) .035f else 0f, radius,
            a, a, a, a, line, line, line, line,
            success, success, success, danger, danger, danger,
            text, c(muted), c(muted), text, onA, bg, surface, surface,
            button, intArrayOf(a, a, a), false, onA, line,
            intArrayOf(surface, surface, surface), text, line,
            intArrayOf(bg, bg, bg), text, line,
            intArrayOf(success, success, success), if (light) c(0xFFFFFFFF) else c(0xFF08261C),
            intArrayOf(danger, danger, danger), if (light) c(0xFFFFFFFF) else c(0xFF410914),
            if (layout == ThemeLayout.WAVE) 3f else radius, layout == ThemeLayout.ORBIT,
            intArrayOf(a, a, a), (a and 0x00FFFFFF) or 0x24000000, intArrayOf(bg, a, surface), layout)
    }

    val COBALT = style("cobalt", "اطلس", "ATLAS", "اتاق فرمان؛ شبکه‌ای، دقیق و آبیِ شب", ThemeLayout.ATLAS,
        false, 0xFF091827, 0xFF12283B, 0xFFF2F7FC, 0xFFA4BACB, 0xFF72DDF1, 0xFF08212D, 0xFF294256,
        .65f, ThemeFrame.HAIRLINE, ThemeButton.FLAT, ThemePattern.LATTICE)
    val IRANIC = style("iranic", "کاغذ", "PAPER", "دفتر بازی؛ کاغذ گرم، جوهر و خطوط صریح", ThemeLayout.PAPER,
        true, 0xFFF3EEE5, 0xFFFFFCF5, 0xFF24241F, 0xFF666359, 0xFF8F372B, 0xFFFFFCF5, 0xFFB7B0A2,
        .16f, ThemeFrame.HAIRLINE, ThemeButton.MATTE, ThemePattern.GIRIH)
    val NEON = style("neon", "اوربیت", "ORBIT", "مدار شب؛ شیشه‌ای، شناور و بنفش", ThemeLayout.ORBIT,
        false, 0xFF141126, 0xFF27213F, 0xFFF7F2FF, 0xFFC0B4D6, 0xFFD1B5FF, 0xFF281445, 0xFF594577,
        1.55f, ThemeFrame.GLOW, ThemeButton.GLASS, ThemePattern.NEON)
    val EMERALD = style("emerald", "نخل", "PALM", "دورهمی آرام؛ سبز پسته‌ای و فرم‌های نرم", ThemeLayout.PALM,
        true, 0xFFEAF0E5, 0xFFF9FCF4, 0xFF20342A, 0xFF53665A, 0xFF315C43, 0xFFFFFFFF, 0xFFCAD7C4,
        1.8f, ThemeFrame.SOFT, ThemeButton.GRADIENT, ThemePattern.ARABESQUE)
    val COPPER = style("copper", "ولتا", "VOLTA", "پرانرژی؛ زرد برقی، قاب محکم و زاویه‌های تیز", ThemeLayout.VOLT,
        false, 0xFF181A19, 0xFF272A27, 0xFFF7F8EF, 0xFFB6BBAF, 0xFFD5F45B, 0xFF20250D, 0xFF858E73,
        .12f, ThemeFrame.HAIRLINE, ThemeButton.MATTE, ThemePattern.DIAMOND)
    val IVORY = style("ivory", "موج", "WAVE", "روشن و خلوت؛ آبی اقیانوس و کنترل‌های کپسولی", ThemeLayout.WAVE,
        true, 0xFFF0F6FB, 0xFFFFFFFF, 0xFF15304A, 0xFF526C83, 0xFF175FC1, 0xFFFFFFFF, 0xFFD8E6F1,
        1.25f, ThemeFrame.SOFT, ThemeButton.FLAT, ThemePattern.DOTS)

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
            setPadding(kit.dp(if (t.layout == ThemeLayout.PAPER || t.layout == ThemeLayout.VOLT) 0 else 16), kit.dp(6), kit.dp(if (t.layout == ThemeLayout.PAPER || t.layout == ThemeLayout.VOLT) 0 else 16), kit.dp(10))
            addView(kit.horizontal(android.view.Gravity.CENTER).apply {
                background = PanelDrawable(kit.density, PanelStyle.NORMAL, if (t.layout == ThemeLayout.WAVE) 40f else 20f)
                setPadding(kit.dp(6), kit.dp(5), kit.dp(6), kit.dp(9))
                items.forEach { (id, icon, label) ->
                    val on = id == selected
                    addView(kit.vertical(android.view.Gravity.CENTER_HORIZONTAL).apply {
                        setPadding(0, kit.dp(5), 0, kit.dp(5))
                        if (on) background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = kit.dpf(DivanDesign.radius(t, if (t.layout == ThemeLayout.WAVE) 30f else 12f))
                            setColor(if (t.layout == ThemeLayout.VOLT) t.accent else Royal.alpha(Royal.gold, if (t.light) 0.10f else 0.14f))
                        }
                        minimumHeight = kit.dp(46)
                        addView(kit.icon(icon, if (on && t.layout == ThemeLayout.VOLT) t.onAccent else if (on) Royal.goldLight else Royal.muted, 22))
                        if (t.layout != ThemeLayout.ORBIT || on) addView(kit.text(label, TextStyle.CAPTION, if (on && t.layout == ThemeLayout.VOLT) t.onAccent else if (on) Royal.goldLight else Royal.muted, android.view.Gravity.CENTER, 1).apply {
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
