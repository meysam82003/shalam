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
            addView(HeroCrestView(host), LinearLayout.LayoutParams(kit.dp(132), kit.dp(132)))
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

    override val adZone: String? get() = "home"
    override fun build(): View {
        val column = kit.vertical().apply {
            setPadding(kit.dp(16), kit.dp(6), kit.dp(16), kit.dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        column.addView(topBar())
        column.addView(banner())
        resume()?.let { column.addView(it) }
        AdEngine.slot(this, "home_inline")?.let { column.addView(it) }
        column.addView(kit.section("انتخاب بازی", RoyalIcon.PLAY))
        val tiles = GameType.values().map { game ->
            kit.gameTile(GameSealView(host, game), game.title, gameSummary(game), pro = needsPro(game.key)) { Gate.open(this, game.key) { host.push(SetupScreen(host, game)) } }
        } + listOf(
            kit.gameTile(RoleSealView(host, MafiaGlyph.MASK, MafiaColors.MAFIA), "راوی مافیا", "${kit.n(MafiaScenarios.builtin.size)} سناریو • ${kit.n(MafiaRoleRegistry.available().size)} نقش",
                host.mafia.latestUnfinished()?.let { "در جریان" }, needsPro("mafia")) { Gate.open(this, "mafia") { host.push(MafiaHomeScreen(host)) } },
            kit.gameTile(RoleSealView(host, PantoLook.glyph, PantoLook.TINT), "پانتومیم", "پنج نوع مسابقه و دور طلایی",
                host.panto.latestUnfinished()?.let { "در جریان" }, needsPro("panto")) { Gate.open(this, "panto") { host.push(PantoHomeScreen(host)) } }
        )
        when (ThemeEngine.spec.layout) {
            ThemeLayout.PAPER, ThemeLayout.WAVE -> tiles.forEach { column.addView(it, kit.spaced(10)) }
            ThemeLayout.ORBIT -> {
                column.addView(tiles.first(), kit.spaced(12))
                column.addView(kit.grid(2, tiles.drop(1).take(4), 12), kit.spaced(12))
                column.addView(tiles.last(), kit.spaced(12))
            }
            ThemeLayout.ATLAS -> column.addView(kit.grid(2, tiles, 8), kit.spaced(10))
            ThemeLayout.PALM -> column.addView(kit.grid(2, tiles, 14), kit.spaced(14))
            ThemeLayout.VOLT -> column.addView(kit.grid(2, tiles, 6), kit.spaced(10))
        }
        column.addView(spyTile(), kit.spaced(6))
        column.addView(kit.section("همهٔ ابزارها", RoyalIcon.MENU))
        column.addView(kit.vertical().apply {
            background = PanelDrawable(kit.density, PanelStyle.NORMAL, 22f)
            setPadding(kit.dp(6), kit.dp(10), kit.dp(6), kit.dp(12))
            addView(kit.grid(2, listOf(
                kit.toolTile(RoyalIcon.TROPHY, "لیگ‌ها") { Gate.open(this@HomeScreen, "leagues") { host.push(LeagueListScreen(host)) } },
                kit.toolTile(RoyalIcon.HISTORY, "تاریخچه") { Gate.open(this@HomeScreen, "history") { host.push(HistoryScreen(host)) } },
                kit.toolTile(RoyalIcon.PLAYERS, "افراد و تیم‌ها") { host.push(PlayersScreen(host)) },
                kit.toolTile(RoyalIcon.CROWN, "رنک‌بندی") { Gate.open(this@HomeScreen, "rankings") { host.push(RankingScreen(host)) } },
                kit.toolTile(RoyalIcon.RULES, "قوانین") { host.push(RulesScreen(host, null)) },
                kit.toolTile(RoyalIcon.CHART, "ماشین‌حساب ورق") { Gate.open(this@HomeScreen, "deck_calc") { host.push(DeckCalcScreen(host)) } },
                kit.toolTile(RoyalIcon.SAVE, "پشتیبان") { Gate.open(this@HomeScreen, "backup") { host.push(BackupScreen(host)) } },
                kit.toolTile(RoyalIcon.SHARE, "اشتراک") {
                    Gate.open(this@HomeScreen, "share") {
                        val last = host.repo.sessions().firstOrNull()
                        if (last == null) kit.toast("هنوز بازی‌ای ثبت نشده است") else shareSession(last)
                    }
                },
                kit.toolTile(RoyalIcon.STAR, "ظاهر برنامه") { host.push(ThemeScreen(host)) },
                kit.toolTile(RoyalIcon.HEART, "درباره") { host.push(AboutScreen(host)) }
            ), 8))
        }, kit.spaced(12))
        AdEngine.slot(this, "home_corner")?.let { column.addView(it) }
        AdEngine.slot(this, "home_bottom")?.let { column.addView(it) }
        column.addView(kit.button("ثبت تبلیغ و اسپانسری", ButtonKind.GHOST, RoyalIcon.STAR, 44) { host.push(SponsorshipScreen(host)) }, kit.spaced(8))
        column.addView(kit.text("$APP_VERSION_LABEL  •  آفلاین" + if (ProState.isPro) "  •  Pro" else "", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
        return kit.vertical().apply {
            addView(ScrollView(host).apply {
                isVerticalScrollBarEnabled = false
                addView(column)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(NavBar.view(this@HomeScreen, NavBar.HOME))
        }
    }

    /** App name and the two shortcuts of the page. */
    private fun topBar(): View = kit.horizontal().apply {
        layoutParams = kit.spaced(10)
        setPadding(kit.dp(2), kit.dp(6), kit.dp(2), 0)
        addView(kit.vertical().apply {
            addView(kit.text(greeting(), TextStyle.LABEL, Royal.muted, maxLines = 1))
            addView(kit.text("دیوان", TextStyle.TITLE, Royal.title, maxLines = 1))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(giftButton())
        addView(kit.wordButton("Pro", "دیوان Pro", if (ProState.isPro) ButtonKind.SUCCESS else ButtonKind.PRIMARY, 42) {
            host.push(if (ProState.loggedIn) SubscriptionScreen(host) else ProScreen(host))
        })
        addView(kit.hgap(8))
        addView(kit.iconButton(RoyalIcon.STAR, "ظاهر برنامه", ButtonKind.SECONDARY, 42) { host.push(ThemeScreen(host)) })

    }

    private fun greeting(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (h) { in 5..11 -> "صبح بخیر"; in 12..16 -> "روز بخیر"; in 17..20 -> "عصر بخیر"; else -> "شب بخیر" }
    }

    /** Each identity composes its opening differently, using the same real game counters. */
    private fun banner(): View = kit.vertical().apply {
        val t = ThemeEngine.spec
        val editorial = t.layout == ThemeLayout.PAPER
        val centered = t.layout == ThemeLayout.PALM || t.layout == ThemeLayout.ORBIT
        if (!editorial) background = PanelDrawable(kit.density, PanelStyle.RAISED, 26f)
        setPadding(kit.dp(if (editorial) 2 else 20), kit.dp(22), kit.dp(if (editorial) 2 else 20), kit.dp(22))
        layoutParams = kit.spaced(18)
        val headline = when (t.layout) {
            ThemeLayout.ATLAS -> "امشب،\nنوبت بازیه."
            ThemeLayout.PAPER -> "قصهٔ این دور\nاز اینجا شروع می‌شه."
            ThemeLayout.ORBIT -> "برو تو مدارِ بازی"
            ThemeLayout.PALM -> "کنار هم،\nخوش می‌گذره."
            ThemeLayout.VOLT -> "بزن بریم!"
            ThemeLayout.WAVE -> "یه دور بازی؟"
        }
        if (centered) {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(HeroCrestView(host), LinearLayout.LayoutParams(kit.dp(88), kit.dp(88)))
            addView(kit.gap(14))
            addView(kit.text(headline, TextStyle.DISPLAY, Royal.title, Gravity.CENTER))
            addView(kit.text("بازی را انتخاب کن؛ امتیازها با دیوان", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
        } else {
            addView(kit.text(if (editorial) "دفتر دورهمی / دیوان" else t.latin + "  /  دیوان", TextStyle.CAPTION, Royal.goldLight))
            addView(kit.gap(8))
            addView(kit.horizontal().apply {
                addView(kit.weight(kit.text(headline, TextStyle.DISPLAY, Royal.title)))
                if (!editorial) {
                    addView(kit.hgap(8))
                    addView(HeroCrestView(host), LinearLayout.LayoutParams(kit.dp(if (t.layout == ThemeLayout.VOLT) 56 else 74), kit.dp(if (t.layout == ThemeLayout.VOLT) 56 else 74)))
                }
            })
            addView(kit.text("هفت بازی، یک جمع، کلی خاطره", TextStyle.LABEL, Royal.muted))
        }
        addView(kit.gap(20))
        val games = host.repo.sessions().size + host.mafia.games().count { it.started } + host.panto.matches().size + host.spy.games().size
        val people = host.repo.roster().count { !it.isTeam }
        val leagues = host.repo.leagues().size
        addView(kit.grid(3, listOf(kit.stat(kit.n(games), "بازی"), kit.stat(kit.n(people), "بازیکن"), kit.stat(kit.n(leagues), "لیگ")), if (editorial) 2 else 10))
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
