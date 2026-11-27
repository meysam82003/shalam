package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

const val APP_VERSION_NAME = "5.6.7"
const val APP_VERSION_LABEL = "نسخهٔ ۵.۶.۷"

class SplashScreen(host: MainActivity) : Screen(host) {
    /**
     * The entrance plays on the phone; the web versions already played it on their loading page (same picture),
     * and with «انیمیشن‌های روان» off the logo is shown still.
     */
    private val animated: Boolean get() = settings.general.animations && android.os.Build.DEVICE != "web"
    private val staged = ArrayList<Pair<View, Long>>()

    override fun build(): View = FrameLayout(host).apply {
        staged.clear()
        val fan = SplashFanView(host)
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(fan, LinearLayout.LayoutParams(kit.dp(280), kit.dp(215)))
            addView(kit.gap(6))
            addView(stage(kit.text("دیوان امتیاز", TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER).apply { textSize = kit.textSize(34f) }, 1160))
            addView(stage(kit.text("داور و دفتر امتیاز شاهانه", TextStyle.BODY, Royal.muted, Gravity.CENTER), 1320))
            addView(kit.gap(10))
            addView(stage(kit.divider(), 1430), LinearLayout.LayoutParams(kit.dp(220), kit.dp(18)))
            addView(stage(kit.text(gameNames(), TextStyle.LABEL_BOLD, Royal.gold, Gravity.CENTER).apply { setLineSpacing(kit.dpf(4f), 1f) }, 1530))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(stage(kit.text("$APP_VERSION_LABEL  •  آفلاین", TextStyle.CAPTION, Royal.dim, Gravity.CENTER), 1660),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = kit.dp(28) })
        isClickable = true
        setOnClickListener { goHome() }
        if (animated) {
            fan.play()
            staged.forEach { (v, delay) -> rise(v, delay) }
        } else {
            fan.still()
        }
    }

    private fun stage(v: View, delay: Long): View {
        staged += v to delay
        if (animated) { v.alpha = 0f; v.translationY = kit.dpf(18f) }
        return v
    }

    /** One line of text coming up into place on the spring while it fades in. */
    private fun rise(v: View, delay: Long) {
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(340).setInterpolator(Motion.EASE_OUT).start()
    }

    private var done = false

    /** Every game of the app (new score games are added automatically), then the mafia narrator. */
    private fun gameNames(): String {
        val names = GameType.values().map { it.title } + listOf("راوی مافیا", "پانتومیم", "جاسوس")
        return names.chunked(4).joinToString("\n") { it.joinToString("  •  ") }
    }

    private fun goHome() {
        if (done) return
        done = true
        host.cancelPosts()
        host.resetTo(if (Themes.isChosen(settings.general.theme)) HomeScreen(host) else ThemeWelcomeScreen(host))
        host.openAfterSplash?.let { intent -> host.openAfterSplash = null; host.post(350) { host.openFromIntent(intent) } }
    }

    /** Home follows once the logo has been seen (after its entrance, or briefly when it is still); a touch skips it. */
    override fun onShow() {
        host.post(if (animated) ENTRANCE_MS else LOGO_MS) { goHome() }
    }

    companion object {
        const val LOGO_MS = 650L
        /** The whole entrance plus a short look at the finished logo (a touch skips it). */
        const val ENTRANCE_MS = 2250L
        @Volatile var askedThisRun = false
    }

    override fun onHide() {
        host.cancelPosts()
    }
}

class HomeScreen(host: MainActivity) : Screen(host) {
    private var campaignId: Int? = null
    private var displayedCampaign: Int? = null
    /** First run: one clear question about offers in the notification bar (Pro players are not asked). */
    private fun askAboutOffers() {
        if (Notices.asked(host) || ProState.isPro || SplashScreen.askedThisRun) return
        SplashScreen.askedThisRun = true
        host.post(900) {
            if (host.current !== this || kit.hasOpenDialogs()) return@post
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
        AdsManager.prepareReward(host)
        RewardInvitation.offer(this)
    }

    override val adZone: String? get() = "home"
    override fun build(): View {
        val column = kit.vertical().apply {
            setPadding(kit.dp(16), kit.dp(6), kit.dp(16), kit.dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        column.addView(topBar())
        if (AppUpdates.available(host) != null) {
            column.addView(kit.button("به‌روزرسانی برنامه", ButtonKind.PRIMARY, height = 44) { host.showUpdate(manual = true) }, kit.spaced(8))
        }
        column.addView(homeCarousel())
        latestGameCard()?.let { column.addView(it) }
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
        column.addView(kit.grid(2, tiles, 10), kit.spaced(10))
        column.addView(spyTile(), kit.spaced(6))
        column.addView(kit.button("انتقال بازی و لیگ", ButtonKind.SECONDARY, RoyalIcon.SHARE, 48) {
            host.push(TransferScreen(host))
        }, kit.spaced(8))
        column.addView(kit.section("دیوان", RoyalIcon.CROWN))
        column.addView(kit.vertical().apply {
            background = PanelDrawable(kit.density, PanelStyle.NORMAL, 22f)
            setPadding(kit.dp(6), kit.dp(10), kit.dp(6), kit.dp(12))
            addView(kit.grid(5, listOf(
                kit.toolTile(RoyalIcon.TROPHY, "لیگ‌ها") { host.push(LeagueListScreen(host)) },
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
            ), 4))
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
            addView(kit.text("دیوان امتیاز", TextStyle.TITLE, Royal.title, maxLines = 1))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(giftButton())
        addView(kit.wordButton("Pro", "دیوان Pro", if (ProState.isPro) ButtonKind.SUCCESS else ButtonKind.PRIMARY, 42) {
            host.push(if (ProState.loggedIn) SubscriptionScreen(host) else ProScreen(host))
        })
        addView(kit.hgap(8))
        addView(kit.iconButton(RoyalIcon.STAR, "ظاهر برنامه", ButtonKind.SECONDARY, 42) { host.push(ThemeScreen(host)) })
        addView(kit.hgap(8))
        addView(kit.iconButton(RoyalIcon.SETTINGS, "تنظیمات", ButtonKind.SECONDARY, 42) { host.push(SettingsScreen(host)) })
    }

    private fun greeting(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (h) { in 5..11 -> "صبح بخیر"; in 12..16 -> "روز بخیر"; in 17..20 -> "عصر بخیر"; else -> "شب بخیر" }
    }

    private fun homeCarousel(): View {
        val key = "home_carousel"
        val ads = AdEngine.campaigns(key, campaignId)
        val current = if (campaignId == null) ads.firstOrNull() else ads.firstOrNull { it.id == campaignId }
        if (current != null || campaignId != null) campaignId = current?.id ?: 0
        val shouldCount = current?.id != displayedCampaign
        displayedCampaign = current?.id
        val content = if (current == null) banner() else AdEngine.campaignCard(this, key, current,
            countImpression = shouldCount, onClose = {
                campaignId = 0
                host.refresh()
            })
        if (ads.isEmpty()) return content
        return kit.vertical().apply {
            addView(kit.horizontal(Gravity.CENTER_VERTICAL).apply {
                addView(kit.weight(kit.text(if (current == null) "آمار دیوان" else "حامیان دیوان", TextStyle.LABEL_BOLD, Royal.muted)))
                addView(kit.iconButton(RoyalIcon.NEXT, "کادر بعدی", ButtonKind.SECONDARY, 40) {
                    val index = ads.indexOfFirst { it.id == current?.id }
                    campaignId = ads.getOrNull(index + 1)?.id ?: 0
                    host.refresh()
                })
            }, kit.spaced(4))
            addView(content, kit.fill())
        }
    }

    /** Headline card: the card-fan crest, the promise of the app and three counters. */
    private fun banner(): View = kit.vertical().apply {
        background = PanelDrawable(kit.density, PanelStyle.RAISED, 26f)
        setPadding(kit.dp(14), kit.dp(12), kit.dp(14), kit.dp(16))
        layoutParams = kit.spaced(12)
        contentDescription = "آمار دیوان، باز کردن تاریخچه"
        setOnClickListener { kit.tap(it); host.push(HistoryScreen(host)) }
        addView(kit.horizontal().apply {
            addView(kit.vertical().apply {
                addView(kit.text("دفتر امتیاز دورهمی‌ها", TextStyle.LABEL_BOLD, Royal.gold, maxLines = 1))
                addView(kit.text("بازی کن،\nامتیاز ثبت کن", TextStyle.TITLE, Royal.title).apply { setLineSpacing(0f, 1.05f) })
                addView(kit.text("هفت بازی در یک دیوان", TextStyle.CAPTION, Royal.muted, maxLines = 1))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(HeroCrestView(host), LinearLayout.LayoutParams(kit.dp(138), kit.dp(106)))
        })
        addView(kit.gap(10))
        val games = host.repo.sessions().size + host.mafia.games().count { it.started } + host.panto.matches().size + host.spy.games().size
        val people = host.repo.roster().count { !it.isTeam }
        val leagues = host.repo.leagues().size
        addView(kit.grid(3, listOf(kit.stat(kit.n(games), "بازی ثبت‌شده"), kit.stat(kit.n(people), "نفر"), kit.stat(kit.n(leagues), "لیگ")), 8))
    }

    /** Most recent saved play across all game engines, including completed games. */
    private fun latestGameCard(): View? {
        val options = ArrayList<Pair<Long, () -> View>>()
        fun label(title: String, finished: Boolean) = if (finished) "آخرین بازی · $title" else "ادامهٔ بازی $title"
        host.repo.sessions().firstOrNull()?.let { s ->
            options += s.updatedAt to {
                val totals = GameEngine.totals(s)
                val score = if (!s.finished && s.game == GameType.MENFI && s.rules.menfi.hidden) "جمع‌ها پنهان است"
                else GameEngine.ranking(s).take(3).joinToString("  •  ") { i -> "${s.sides[i].name} ${kit.signed(totals[i])}" }
                resumeStrip(GameSealView(host, s.game), label(s.game.title, s.finished), score) { host.openSession(s) }
            }
        }
        host.mafia.games().firstOrNull { it.started }?.let { g ->
            options += g.updatedAt to {
                val detail = if (g.finished) g.winner.ifBlank { "پایان‌یافته" } else "${g.phaseTitle} • ${kit.n(g.alive.size)} نفر در بازی"
                resumeStrip(RoleSealView(host, MafiaGlyph.MASK, MafiaColors.MAFIA), label("مافیا", g.finished), detail) {
                    host.push(MafiaGameScreen(host, g.id))
                }
            }
        }
        host.panto.matches().firstOrNull()?.let { m ->
            options += m.updatedAt to {
                val standings = m.standings.take(3).joinToString("  •  ") { "${m.teams[it].name} ${kit.n(m.teams[it].points)}" }
                resumeStrip(RoleSealView(host, PantoLook.glyph, PantoLook.TINT), label("پانتومیم", m.finished), standings) {
                    host.push(when {
                        m.finished -> PantoResultScreen(host, m.id)
                        m.turn?.words?.isNotEmpty() == true -> PantoPlayScreen(host, m.id)
                        else -> PantoBoardScreen(host, m.id)
                    })
                }
            }
        }
        host.spy.games().firstOrNull()?.let { g ->
            options += g.updatedAt to {
                resumeStrip(RoleSealView(host, SpyLook.glyph, SpyLook.TINT), label("جاسوس", g.finished), "${kit.n(g.players.size)} بازیکن") {
                    host.push(when {
                        g.finished -> SpyResultScreen(host, g.id)
                        g.allViewed -> SpyTimerScreen(host, g.id)
                        else -> SpyRevealScreen(host, g.id)
                    })
                }
            }
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
