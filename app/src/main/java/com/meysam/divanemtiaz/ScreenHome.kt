package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

const val APP_VERSION_NAME = "5.7.0"
const val APP_VERSION_LABEL = "نسخهٔ ۵.۷.۰"

class SplashScreen(host: MainActivity) : Screen(host) {
    private var done = false
    override fun build(): View = FrameLayout(host).apply {
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(RegalCrownView(host), LinearLayout.LayoutParams(kit.dp(132), kit.dp(132)))
            addView(kit.gap(24))
            addView(kit.text("دیوان", TextStyle.DISPLAY, Royal.title, Gravity.CENTER).apply { textSize = kit.textSize(42f) })
            addView(kit.text("هر دورهمی، یک بازی تازه", TextStyle.BODY, Royal.muted, Gravity.CENTER))
            addView(kit.gap(18))
            addView(kit.badge(ThemeEngine.spec.latin, Royal.gold, true))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(kit.text(APP_VERSION_LABEL, TextStyle.CAPTION, Royal.dim, Gravity.CENTER),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = kit.dp(28) })
        isClickable = true; setOnClickListener { goHome() }
        if (settings.general.animations) { alpha = 0f; animate().alpha(1f).setDuration(300).start() }
    }
    private fun goHome() {
        if (done) return
        done = true; host.cancelPosts()
        host.resetTo(if (Themes.isChosen(settings.general.theme)) HomeScreen(host) else ThemeWelcomeScreen(host))
        host.openAfterSplash?.let { intent -> host.openAfterSplash = null; host.post(350) { host.openFromIntent(intent) } }
    }
    override fun onShow() { host.post(LOGO_MS) { goHome() } }
    override fun onHide() { host.cancelPosts() }
    companion object {
        const val LOGO_MS = 800L
        const val ENTRANCE_MS = 800L
        @Volatile var askedThisRun = false
    }
}

class HomeScreen(host: MainActivity) : Screen(host) {
    /** First run: one clear question about offers in the notification bar (Pro players are not asked). */
    private fun askAboutOffers() {
        if (Notices.asked(host) || ProState.isPro || SplashScreen.askedThisRun) return
        SplashScreen.askedThisRun = true
        host.post(900) {
            if (host.current !== this) return@post
            kit.dialog("اعلان پیشنهادها",
                "دیوان می‌تواند پیشنهادها و تخفیف‌های خودش را (حداکثر یکی در روز) در نوار اعلان گوشی نشان دهد. هر وقت خواستید از «تنظیمات ← اعلان‌ها» خاموشش کنید.",
                null, listOf(
                    DialogAction("بله، اعلان بده", ButtonKind.PRIMARY) { Notices.setOffers(host, true); Notices.requestPermission(host); Notices.schedule(host) },
                    DialogAction("نه، ممنون") { Notices.setOffers(host, false) }
                )).show()
        }
    }

    override fun onShow() {
        super.onShow()
        askAboutOffers()
    }

    override val visualScene: CinemaScene get() = CinemaScene.HOME
    override val adZone: String? get() = "home"
    override fun build(): View {
        val column = kit.vertical().apply { setPadding(kit.dp(14), kit.dp(4), kit.dp(14), kit.dp(18)) }
        column.addView(branding())
        resume()?.let { column.addView(it) }
        val cardTiles = GameType.values().map { game ->
            tile(GameSealView(host, game), game.title, gameSummary(game), CinemaSymbols.color(game), needsPro(game.key)) {
                Gate.open(this, game.key) { host.push(SetupScreen(host, game)) }
            }
        }
        column.addView(kit.grid(2, cardTiles, 9), kit.spaced(9))
        column.addView(kit.grid(3, listOf(
            tile(RoleSealView(host, MafiaGlyph.MASK, MafiaColors.MAFIA), "راوی مافیا", null, 0xFFD73C5C.toInt(), needsPro("mafia"), true) { Gate.open(this, "mafia") { host.push(MafiaHomeScreen(host)) } },
            tile(RoleSealView(host, PantoLook.glyph, PantoLook.TINT), "پانتومیم", null, 0xFFAA6FFF.toInt(), needsPro("panto"), true) { Gate.open(this, "panto") { host.push(PantoHomeScreen(host)) } },
            tile(RoleSealView(host, MafiaGlyph.FEDORA, 0xFF3278B7.toInt()), "جاسوس", null, 0xFF56A5F5.toInt(), needsPro("spy"), true) { Gate.open(this, "spy") { host.push(SpyHomeScreen(host)) } }
        ), 8), kit.spaced(12))
        AdEngine.slot(this, "home_inline")?.let { column.addView(it) }
        column.addView(proBanner(), kit.spaced(14))
        column.addView(kit.listGroup(listOf(
            kit.listRow(kit.iconChip(RoyalIcon.MENU), "همهٔ ابزارهای دیوان", "افراد، قوانین، پشتیبان، اشتراک و ظاهرها") { host.push(HomeToolsScreen(host)) },
            kit.listRow(kit.iconChip(RoyalIcon.STAR), "ثبت تبلیغ و اسپانسری", "ارتباط با مدیریت تبلیغات") { host.push(SponsorshipScreen(host)) }
        )))
        AdEngine.slot(this, "home_corner")?.let { column.addView(it) }
        AdEngine.slot(this, "home_bottom")?.let { column.addView(it) }
        column.addView(kit.text(APP_VERSION_LABEL, TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
        return kit.vertical().apply {
            addView(ScrollView(host).apply { isVerticalScrollBarEnabled = false; addView(column) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(NavBar.view(this@HomeScreen, NavBar.HOME))
        }
    }

    private fun branding(): View = FrameLayout(host).apply {
        layoutParams = kit.spaced(12)
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            setPadding(kit.dp(44), kit.dp(3), kit.dp(44), kit.dp(5))
            addView(RegalCrownView(host), LinearLayout.LayoutParams(kit.dp(72), kit.dp(55)))
            addView(kit.text("دیوان امتیاز", TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER).apply { textSize = kit.textSize(28f) })
            addView(kit.text("همهٔ بازی‌های دورهمی در یک دیوان", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(kit.iconButton(RoyalIcon.MENU, "همهٔ ابزارها", ButtonKind.SECONDARY, 42) { host.push(HomeToolsScreen(host)) }, FrameLayout.LayoutParams(kit.dp(42), kit.dp(42), Gravity.TOP or Gravity.START).apply { topMargin = kit.dp(12) })
        addView(giftButton(), FrameLayout.LayoutParams(kit.dp(44), kit.dp(44), Gravity.TOP or Gravity.END).apply { topMargin = kit.dp(12) })
    }

    private fun tile(seal: View, title: String, caption: String?, tint: Int, pro: Boolean, compact: Boolean = false, onClick: () -> Unit): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        background = CinemaTileDrawable(kit.density, tint)
        setPadding(kit.dp(7), kit.dp(8), kit.dp(7), kit.dp(12))
        val size = if (compact) 62 else 86
        addView(seal, LinearLayout.LayoutParams(kit.dp(size), kit.dp(size)))
        addView(kit.text(title, if (compact) TextStyle.BODY_BOLD else TextStyle.HEADING, Royal.ivory, Gravity.CENTER, 1))
        if (caption != null) addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 1))
        if (pro) addView(kit.text("Pro", TextStyle.CAPTION, Royal.goldLight, Gravity.CENTER, 1))
        minimumHeight = kit.dp(if (compact) 110 else 140)
        isClickable = true; contentDescription = "شروع $title"
        setOnClickListener { kit.tap(it); onClick() }
    }

    private fun proBanner(): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, PanelStyle.RAISED, 18f)
        setPadding(kit.dp(14), kit.dp(9), kit.dp(14), kit.dp(12))
        addView(RegalCrownView(host), LinearLayout.LayoutParams(kit.dp(52), kit.dp(42)))
        addView(kit.hgap(10))
        addView(kit.vertical().apply {
            addView(kit.text("دیوان Pro", TextStyle.HEADING, Royal.goldLight))
            addView(kit.text(if (ProState.isPro) "مدیریت اشتراک و حساب شما" else "بازی‌ها، سناریوها و امکانات ویژه", TextStyle.CAPTION, Royal.muted))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.icon(RoyalIcon.NEXT, Royal.goldLight, 20))
        isClickable = true; contentDescription = "دیوان Pro"
        setOnClickListener { kit.tap(it); host.push(if (ProState.loggedIn) SubscriptionScreen(host) else ProScreen(host)) }
    }

    /** The most recent game in progress of any kind. */
    private fun resume(): View? {
        val options = ArrayList<Triple<Long, () -> View, Unit>>()
        host.repo.latestUnfinished()?.let { s ->
            val totals = GameEngine.totals(s)
            val score = if (s.game == GameType.MENFI && s.rules.menfi.hidden) "جمع‌ها پنهان است"
            else GameEngine.ranking(s).take(3).joinToString("  •  ") { i -> "${s.sides[i].name} ${kit.signed(totals[i])}" }
            options += Triple(s.updatedAt, { resumeStrip(GameSealView(host, s.game), "ادامهٔ بازی ${s.game.title}", score) { host.openSession(s) } }, Unit)
        }
        host.mafia.latestUnfinished()?.let { g ->
            options += Triple(g.updatedAt, { resumeStrip(RoleSealView(host, MafiaGlyph.MASK, MafiaColors.MAFIA), "ادامهٔ راوی مافیا", "${g.phaseTitle} • ${kit.n(g.alive.size)} نفر در بازی") { host.push(MafiaGameScreen(host, g.id)) } }, Unit)
        }
        host.panto.latestUnfinished()?.let { m ->
            options += Triple(m.updatedAt, { resumeStrip(RoleSealView(host, PantoLook.glyph, PantoLook.TINT), "ادامهٔ پانتومیم • ${m.mode.title}", m.standings.take(3).joinToString("  •  ") { "${m.teams[it].name} ${kit.n(m.teams[it].points)}" }) { host.push(PantoBoardScreen(host, m.id)) } }, Unit)
        }
        host.spy.latestUnfinished()?.let { g ->
            options += Triple(g.updatedAt, { resumeStrip(RoleSealView(host, SpyLook.glyph, SpyLook.TINT), "ادامهٔ جاسوس", "${kit.n(g.players.size)} بازیکن") { host.push(if (g.allViewed) SpyTimerScreen(host, g.id) else SpyRevealScreen(host, g.id)) } }, Unit)
        }
        return options.maxByOrNull { it.first }?.second?.invoke()
    }

    /** Wide tile of «جاسوس» closing the game grid. */
    private fun spyTile(): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, PanelStyle.RAISED, 22f)
        setPadding(kit.dp(12), kit.dp(12), kit.dp(14), kit.dp(15))
        addView(RoleSealView(host, SpyLook.glyph, SpyLook.TINT), LinearLayout.LayoutParams(kit.dp(56), kit.dp(56)))
        addView(kit.hgap(12))
        addView(kit.vertical().apply {
            addView(kit.text("جاسوس", TextStyle.HEADING, Royal.title))
            addView(kit.text("پخش نقش، کلمهٔ مخفی، زمان‌سنج و رأی‌گیری", TextStyle.CAPTION, Royal.muted, maxLines = 2))
            if (needsPro("spy")) { addView(kit.gap(4)); addView(kit.badge("Pro", Royal.gold, true)) }
            host.spy.latestUnfinished()?.let { addView(kit.gap(4)); addView(kit.badge("در جریان", Royal.turquoise, true)) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.icon(RoyalIcon.NEXT, Royal.goldLight, 18))
        isClickable = true
        contentDescription = "جاسوس"
        setOnClickListener { kit.tap(it); Gate.open(this@HomeScreen, "spy") { host.push(SpyHomeScreen(host)) } }
    }

    /** Sections the panel made Pro carry a crown badge, so a free user knows before tapping. */
    private fun needsPro(key: String): Boolean = !ProState.isPro && !ProState.feature(key).second

    private fun gameSummary(game: GameType): String = when (game) {
        GameType.SHALAM -> "تا ${kit.n(settings.shalam.mode(settings.shalam.defaultJoker).defaultEndPoint)} • ${if (settings.shalam.defaultJoker) "با جوکر" else "بدون جوکر"}"
        GameType.MENFI -> "${kit.n(settings.menfi.hands)} دست • اعداد پنهان"
        GameType.HEZARTAII -> "پاسور تا ${kit.n(settings.hezar.target)}"
        GameType.DOLO -> "حکم و حذف • بی‌سقف"
    }
}

/** The previous home shortcuts remain one tap away; none of their access rules change. */
class HomeToolsScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("دیوان شما", "همهٔ ابزارها و بخش‌های برنامه") {
        addView(kit.listGroup(listOf(
            kit.listRow(kit.iconChip(RoyalIcon.TROPHY), "لیگ‌ها", "مسابقه‌ها و رقابت تیمی") { Gate.open(this@HomeToolsScreen, "leagues") { host.push(LeagueListScreen(host)) } },
            kit.listRow(kit.iconChip(RoyalIcon.HISTORY), "تاریخچه", "بازی‌های ذخیره‌شده") { Gate.open(this@HomeToolsScreen, "history") { host.push(HistoryScreen(host)) } },
            kit.listRow(kit.iconChip(RoyalIcon.PLAYERS), "افراد و تیم‌ها", "مدیریت بازیکنان") { host.push(PlayersScreen(host)) },
            kit.listRow(kit.iconChip(RoyalIcon.CROWN), "رنک‌بندی", "جدول امتیاز و رتبه‌ها") { Gate.open(this@HomeToolsScreen, "rankings") { host.push(RankingScreen(host)) } },
            kit.listRow(kit.iconChip(RoyalIcon.RULES), "قوانین", "راهنمای همهٔ بازی‌ها") { host.push(RulesScreen(host, null)) },
            kit.listRow(kit.iconChip(RoyalIcon.CHART), "ماشین‌حساب ورق", "امتیاز و پخش ورق") { Gate.open(this@HomeToolsScreen, "deck_calc") { host.push(DeckCalcScreen(host)) } }
        )))
        addView(kit.listGroup(listOf(
            kit.listRow(kit.iconChip(RoyalIcon.SAVE), "پشتیبان", "ذخیره و بازیابی اطلاعات") { Gate.open(this@HomeToolsScreen, "backup") { host.push(BackupScreen(host)) } },
            kit.listRow(kit.iconChip(RoyalIcon.SHARE), "اشتراک", "تصویر آخرین بازی") {
                Gate.open(this@HomeToolsScreen, "share") { val last = host.repo.sessions().firstOrNull(); if (last == null) kit.toast("هنوز بازی‌ای ثبت نشده است") else shareSession(last) }
            },
            kit.listRow(kit.iconChip(RoyalIcon.STAR), "ظاهر برنامه", "شش فضای متفاوت برای دیوان") { host.push(ThemeScreen(host)) },
            kit.listRow(kit.iconChip(RoyalIcon.SETTINGS), "تنظیمات", "قوانین، صدا و امکانات") { host.push(SettingsScreen(host)) },
            kit.listRow(kit.iconChip(RoyalIcon.USER), "حساب و اشتراک", "دیوان Pro") { host.push(if (ProState.loggedIn) SubscriptionScreen(host) else ProScreen(host)) },
            kit.listRow(kit.iconChip(RoyalIcon.HEART), "درباره", "دیوان امتیاز") { host.push(AboutScreen(host)) }
        )))
    }
}
