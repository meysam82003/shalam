package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

const val APP_VERSION_LABEL = "نسخهٔ ۴.۱.۱"

class SplashScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = FrameLayout(host).apply {
        addView(CrestView(host), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(300), Gravity.TOP).apply {
            topMargin = kit.dp(70)
        })
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(GameSealView(host, null), LinearLayout.LayoutParams(kit.dp(128), kit.dp(128)))
            addView(kit.gap(18))
            addView(kit.text("دیوان امتیاز", TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER))
            addView(kit.text("داور و دفتر امتیاز شاهانه", TextStyle.BODY, Royal.muted, Gravity.CENTER))
            addView(kit.gap(10))
            addView(kit.divider(), LinearLayout.LayoutParams(kit.dp(220), kit.dp(18)))
            addView(kit.text("شلم  •  منفی  •  هزارتایی  •  دو لو گشنیز", TextStyle.LABEL_BOLD, Royal.gold, Gravity.CENTER))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(kit.text("$APP_VERSION_LABEL  •  آفلاین", TextStyle.CAPTION, Royal.dim, Gravity.CENTER),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = kit.dp(28) })
        isClickable = true
        setOnClickListener { goHome() }
        alpha = 0f
        animate().alpha(1f).setDuration(180).start()
    }

    private var done = false

    private fun goHome() {
        if (done) return
        done = true
        host.cancelPosts()
        host.resetTo(HomeScreen(host))
    }

    override fun onShow() {
        host.post(350) { goHome() }
    }

    override fun onHide() {
        host.cancelPosts()
    }
}

class HomeScreen(host: MainActivity) : Screen(host) {
    override fun build(): View {
        val column = kit.vertical().apply {
            setPadding(kit.dp(14), kit.dp(4), kit.dp(14), kit.dp(20))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        column.addView(hero())
        host.repo.latestUnfinished()?.let { column.addView(continueCard(it)) }
        column.addView(kit.section("بازی تازه", RoyalIcon.PLAY))
        GameType.values().forEach { game -> column.addView(gameCard(game), kit.spaced(8)) }
        column.addView(kit.section("دیوان", RoyalIcon.STAR))
        column.addView(kit.grid(3, listOf(
            tile(RoyalIcon.TROPHY, "لیگ‌ها", "دو برده، جدول و فینال") { host.push(LeagueListScreen(host)) },
            tile(RoyalIcon.HISTORY, "تاریخچه", "ادامه و نتیجه‌ها") { host.push(HistoryScreen(host)) },
            tile(RoyalIcon.PLAYERS, "بازیکنان", "تیم‌ها و نشان‌ها") { host.push(PlayersScreen(host)) },
            tile(RoyalIcon.CHART, "ماشین‌حساب ورق", "پخش و ورق اضافه") { host.push(DeckCalcScreen(host)) },
            tile(RoyalIcon.RULES, "قوانین", "شرح هر بازی") { host.push(RulesScreen(host, null)) },
            tile(RoyalIcon.SETTINGS, "تنظیمات", "همهٔ قوانین") { host.push(SettingsScreen(host)) },
            tile(RoyalIcon.CROWN, "رنک‌بندی", "رتبه، کارنامه و رکورد") { host.push(RankingScreen(host)) },
            tile(RoyalIcon.SAVE, "پشتیبان", "ذخیره و بازگردانی") { host.push(BackupScreen(host)) },
            tile(RoyalIcon.SHARE, "اشتراک", "تصویر آخرین بازی") {
                val last = host.repo.sessions().firstOrNull()
                if (last == null) kit.toast("هنوز بازی‌ای ثبت نشده است") else shareSession(last)
            }
        ), 8))
        column.addView(kit.gap(16))
        column.addView(kit.text("$APP_VERSION_LABEL  •  آفلاین  •  بدون تبلیغ", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
        return ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            addView(column)
        }
    }

    private fun hero(): View = FrameLayout(host).apply {
        layoutParams = kit.spaced(6)
        addView(CrestView(host), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(172)))
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            setPadding(0, kit.dp(18), 0, kit.dp(6))
            addView(GameSealView(host, null), LinearLayout.LayoutParams(kit.dp(70), kit.dp(70)))
            addView(kit.gap(4))
            addView(kit.text("دیوان امتیاز", TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER))
            addView(kit.text("داوری، دفتر امتیاز و تاریخچهٔ بازی‌ها", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(172)))
        addView(kit.iconButton(RoyalIcon.SETTINGS, "تنظیمات", ButtonKind.SECONDARY, 38) { host.push(SettingsScreen(host)) },
            FrameLayout.LayoutParams(kit.dp(38), kit.dp(38), Gravity.TOP or Gravity.LEFT).apply { topMargin = kit.dp(6) })
    }

    private fun continueCard(session: GameSession): View {
        val totals = GameEngine.totals(session)
        return kit.panel(PanelStyle.SELECTED, 12).apply {
            layoutParams = kit.spaced(6).apply { topMargin = kit.dp(4) }
            addView(kit.horizontal().apply {
                addView(GameSealView(host, session.game), LinearLayout.LayoutParams(kit.dp(42), kit.dp(42)))
                addView(kit.hgap(10))
                addView(kit.vertical().apply {
                    addView(kit.text("ادامهٔ بازی ${session.game.title}", TextStyle.BODY_BOLD, Royal.turquoiseLight))
                    val score = if (session.game == GameType.MENFI && session.rules.menfi.hidden) "جمع‌ها پنهان است"
                    else GameEngine.ranking(session).take(3).joinToString("  •  ") { i -> "${session.sides[i].name} ${kit.signed(totals[i])}" } +
                        if (session.sides.size > 3) "  …" else ""
                    addView(kit.text(score, TextStyle.LABEL, Royal.ivory, maxLines = 2))
                    addView(kit.text("${kit.n(GameEngine.playedHands(session))} دست  •  ${timerText(session)}", TextStyle.CAPTION, Royal.muted))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(kit.gap(8))
            addView(kit.button("ادامهٔ داوری", ButtonKind.SUCCESS, RoyalIcon.PLAY, 42) { host.openSession(session) })
        }
    }

    private fun gameCard(game: GameType): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, PanelStyle.RAISED, 18f)
        setPadding(kit.dp(12), kit.dp(9), kit.dp(12), kit.dp(12))
        addView(GameSealView(host, game), LinearLayout.LayoutParams(kit.dp(56), kit.dp(56)))
        addView(kit.hgap(10))
        addView(kit.vertical().apply {
            addView(kit.text(game.title, TextStyle.HEADING, Royal.goldLight))
            addView(kit.text(game.subtitle, TextStyle.CAPTION, Royal.muted, maxLines = 2))
            addView(kit.gap(3))
            addView(kit.badge(gameSummary(game), Royal.turquoise))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.icon(RoyalIcon.NEXT, Royal.gold, 20))
        isClickable = true
        contentDescription = "شروع ${game.title}"
        setOnClickListener { kit.tap(it); host.push(SetupScreen(host, game)) }
    }

    private fun gameSummary(game: GameType): String = when (game) {
        GameType.SHALAM -> "تا ${kit.n(settings.shalam.mode(settings.shalam.defaultJoker).defaultEndPoint)} • ${if (settings.shalam.defaultJoker) "با جوکر" else "بدون جوکر"}"
        GameType.MENFI -> "${kit.n(settings.menfi.hands)} دست • اعداد ${kit.n(3)} تا ${kit.n(13)}"
        GameType.HEZARTAII -> "هدف ${kit.n(settings.hezar.target)} • بدون سقف بازیکن"
        GameType.DOLO -> "حداقل ${kit.n(settings.dolo.rules.minUpTo4)} / ${kit.n(settings.dolo.rules.min5to6)} / ${kit.n(settings.dolo.rules.minFrom7)} • حذفی"
    }

    private fun tile(icon: RoyalIcon, title: String, caption: String, onClick: () -> Unit): View =
        kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
            setPadding(kit.dp(6), kit.dp(9), kit.dp(6), kit.dp(11))
            addView(kit.icon(icon, Royal.gold, 24))
            addView(kit.gap(4))
            addView(kit.text(title, TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER))
            addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
            isClickable = true
            contentDescription = title
            setOnClickListener { kit.tap(it); onClick() }
        }
}
