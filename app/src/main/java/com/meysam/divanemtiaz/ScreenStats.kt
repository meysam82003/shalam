package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Running totals of a game as a line chart; at most six sides (the leaders) are drawn. */
fun Screen.gameChart(session: GameSession, height: Int = 190): View {
    val lines = StatsEngine.progression(session)
    val totals = GameEngine.totals(session)
    val shown = if (session.sides.size <= 6) session.sides.indices.toList()
    else GameEngine.ranking(session).take(6)
    val series = shown.mapIndexed { k, i -> ChartSeries(session.sides[i].name, ChartColors.of(k), lines[i].map { it.toFloat() }) }
    return kit.vertical().apply {
        addView(LineChartView(host, series, { kit.n(it) }, kit.density), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(height)))
        addView(kit.flow(shown.mapIndexed { k, i ->
            kit.horizontal().apply {
                addView(View(host).apply {
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(ChartColors.of(k)); cornerRadius = kit.dpf(4f) }
                }, LinearLayout.LayoutParams(kit.dp(12), kit.dp(5)))
                addView(kit.hgap(4))
                addView(kit.text("${session.sides[i].name} ${kit.signed(totals[i])}", TextStyle.CAPTION, Royal.ivory, maxLines = 1))
            }
        }, 10))
    }
}

fun Screen.gameChartDialog(session: GameSession) {
    if (session.rounds.none { it.kind != RoundKind.DOLO_ELIM }) {
        kit.toast("هنوز دستی ثبت نشده است")
        return
    }
    kit.dialog("نمودار بازی", null, kit.vertical().apply {
        addView(kit.text("جمع امتیاز پس از هر دست", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
        addView(gameChart(session, 220), kit.fill())
    }, listOf(DialogAction("بستن"))).show()
}

/** Common frame of every shared image: game seal, title, content and the app's name. */
private fun Screen.shareFrame(game: GameType?, title: String, subtitle: String, content: LinearLayout.() -> Unit): View =
    kit.vertical().apply {
        setPadding(kit.dp(16), kit.dp(16), kit.dp(16), kit.dp(14))
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        addView(kit.horizontal().apply {
            addView(GameSealView(host, game), LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)))
            addView(kit.hgap(10))
            addView(kit.vertical().apply {
                addView(kit.text(title, TextStyle.TITLE, Royal.goldLight))
                addView(kit.text(subtitle, TextStyle.CAPTION, Royal.muted))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(kit.divider())
        content()
        addView(kit.gap(8))
        addView(kit.text("دیوان امتیاز  •  ${JalaliDate.format(System.currentTimeMillis(), settings.general.persianDigits)}", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
    }

fun Screen.shareSession(session: GameSession) {
    val totals = GameEngine.totals(session)
    val winners = GameEngine.winners(session)
    val hidden = session.game == GameType.MENFI && session.rules.menfi.hidden && !session.finished
    val status = when {
        hidden -> "جمع امتیازها تا پایان بازی پنهان است"
        session.finished && winners.isEmpty() -> "نتیجه: مساوی"
        session.finished -> "برنده: ${winners.joinToString(" و ") { session.sides[it].name }}"
        else -> "در جریان  •  ${kit.n(GameEngine.playedHands(session))} دست"
    }
    val card = shareFrame(session.game, "بازی ${session.game.title}", sessionSubtitle(session)) {
        addView(kit.text(status, TextStyle.HEADING, if (session.finished) Royal.turquoiseLight else Royal.goldLight, Gravity.CENTER), kit.spaced(8))
        GameEngine.ranking(session).forEachIndexed { place, i ->
            addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, if (place == 0 && winners.isNotEmpty() && !hidden) PanelStyle.RAISED else PanelStyle.FLAT, 14f)
                setPadding(kit.dp(10), kit.dp(5), kit.dp(10), kit.dp(8))
                layoutParams = kit.spaced(4)
                addView(kit.text(kit.n(place + 1), TextStyle.BODY_BOLD, Royal.gold), LinearLayout.LayoutParams(kit.dp(24), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(kit.avatar(session.sides[i].avatar, 30))
                addView(kit.hgap(8))
                addView(kit.weight(kit.text(session.sides[i].name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1)))
                addView(kit.text(if (hidden) "•••" else kit.signed(totals[i]), TextStyle.HEADING, if (hidden) Royal.dim else scoreColor(totals[i])))
            })
        }
        if (!hidden && session.rounds.isNotEmpty()) {
            addView(kit.gap(6))
            addView(gameChart(session, 170), kit.fill())
        }
        addView(kit.text("${kit.n(GameEngine.playedHands(session))} دست  •  ${timerText(session)}", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
    }
    host.shareImage(card, "بازی ${session.game.title} در دیوان امتیاز")
}

fun Screen.shareRound(session: GameSession, index: Int) {
    val round = session.rounds.getOrNull(index) ?: return
    val hidden = session.game == GameType.MENFI && session.rules.menfi.hidden && !session.finished
    val card = shareFrame(session.game, "دست ${kit.n(index + 1)} • ${session.game.title}", session.sides.joinToString(" • ") { it.name }) {
        val detail = describeRound(session, round)
        if (detail.isNotBlank() && !hidden) addView(kit.text(detail, TextStyle.LABEL, Royal.ivory, Gravity.CENTER), kit.spaced(8))
        session.sides.indices.filter { round.score(it) != 0 || session.game.isTeamGame || round.numbers.getOrElse(it) { -1 } >= 0 }.forEach { i ->
            addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                setPadding(kit.dp(10), kit.dp(5), kit.dp(10), kit.dp(8))
                layoutParams = kit.spaced(4)
                addView(kit.avatar(session.sides[i].avatar, 30))
                addView(kit.hgap(8))
                addView(kit.weight(kit.text(session.sides[i].name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1)))
                addView(kit.text(if (hidden) "•••" else kit.signed(round.score(i)), TextStyle.HEADING, if (hidden) Royal.dim else scoreColor(round.score(i))))
            })
        }
    }
    host.shareImage(card, "دست ${kit.n(index + 1)} بازی ${session.game.title}")
}

fun Screen.shareProfile(p: PlayerStats, place: Int?) {
    val card = shareFrame(null, p.id.name, if (p.id.isTeam) "کارنامهٔ تیم" else "کارنامهٔ بازیکن") {
        addView(profileHeadline(p, place))
        addView(statTiles(p))
    }
    host.shareImage(card, "کارنامهٔ ${p.id.name} در دیوان امتیاز")
}

private fun Screen.profileHeadline(p: PlayerStats, place: Int?): View = kit.horizontal(Gravity.CENTER).apply {
    layoutParams = kit.spaced(8)
    addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        addView(kit.text(kit.n(p.rating.roundToInt()), TextStyle.NUMBER_XL, Royal.goldLight, Gravity.CENTER))
        addView(kit.text("امتیاز رتبه", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
    })
    addView(kit.hgap(18))
    addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        addView(kit.badge(StatsEngine.tier(p.rating), Royal.gold, true))
        if (place != null) addView(kit.text("رتبهٔ ${kit.n(place + 1)}", TextStyle.LABEL_BOLD, Royal.turquoiseLight, Gravity.CENTER))
    })
}

private fun Screen.statTiles(p: PlayerStats): View = kit.grid(3, listOf(
    "بازی" to kit.n(p.games),
    "برد" to kit.n(p.wins),
    "باخت" to kit.n(p.losses),
    "مساوی" to kit.n(p.draws),
    "درصد برد" to "${kit.n((p.winRate * 100).roundToInt())}٪",
    "بهترین زنجیره" to kit.n(p.bestStreak)
).map { (label, value) ->
    kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
        setPadding(kit.dp(4), kit.dp(6), kit.dp(4), kit.dp(9))
        addView(kit.text(value, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER, 1))
        addView(kit.text(label, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 1))
    }
}, 6).apply { layoutParams = kit.spaced(8) }

/** Rankings of teams and players with Elo ratings, plus the all-time records. */
class RankingScreen(host: MainActivity) : Screen(host) {
    private var teams = true
    private var game: GameType? = null

    override fun build(): View {
        val sessions = host.repo.sessions()
        val list = StatsEngine.leaderboard(sessions, teams, game)
        return scaffold(title = "رنک‌بندی", subtitle = "رتبهٔ Elo از همهٔ بازی‌های پایان‌یافته") {
            addView(kit.grid(2, listOf(
                kit.chip("تیم‌ها", teams, ButtonKind.CHIP_GOLD) { teams = true; game = null; host.refresh() },
                kit.chip("بازیکنان", !teams, ButtonKind.CHIP_GOLD) { teams = false; game = null; host.refresh() }
            ), 6), kit.spaced(6))
            val games = GameType.values().filter { it.isTeamGame == teams }
            addView(kit.flow(listOf(kit.chip("همهٔ بازی‌ها", game == null) { game = null; host.refresh() }) + games.map { g ->
                kit.chip(g.title, game == g) { game = g; host.refresh() }
            }.map { it.apply { setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(6)) } }, 6), kit.spaced(8))
            if (list.isEmpty()) {
                addView(emptyState(RoyalIcon.TROPHY, "هنوز بازی پایان‌یافته‌ای نیست", "با پایان هر بازی، رتبهٔ ${if (teams) "تیم‌ها" else "بازیکنان"} اینجا ساخته می‌شود."))
            }
            val roster = host.repo.roster()
            list.forEachIndexed { place, p ->
                val avatar = roster.firstOrNull { it.name == p.id.name && it.isTeam == p.id.isTeam }?.avatar ?: sessions.firstNotNullOfOrNull { s -> s.sides.firstOrNull { it.name == p.id.name }?.avatar } ?: 0
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, if (place == 0) PanelStyle.RAISED else PanelStyle.NORMAL, 16f)
                    setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
                    layoutParams = kit.spaced(5)
                    val medal = when (place) {
                        0 -> ButtonKind.CHIP_GOLD
                        1 -> ButtonKind.CHIP_SELECTED
                        else -> ButtonKind.CHIP
                    }
                    addView(kit.text(kit.n(place + 1), TextStyle.LABEL_BOLD, ButtonDrawable.textColor(medal), Gravity.CENTER).apply {
                        background = ButtonDrawable(kit.density, medal, 14f)
                        setPadding(0, 0, 0, kit.dp(2))
                    }, LinearLayout.LayoutParams(kit.dp(30), kit.dp(30)))
                    addView(kit.hgap(8))
                    addView(kit.avatar(avatar, 36))
                    addView(kit.hgap(8))
                    addView(kit.vertical().apply {
                        addView(kit.text(p.id.name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
                        addView(kit.text("${kit.n(p.wins)} برد • ${kit.n(p.draws)} مساوی • ${kit.n(p.losses)} باخت • ${StatsEngine.tier(p.rating)}", TextStyle.CAPTION, Royal.muted, maxLines = 1))
                        addView(kit.progress(p.winRate, Royal.turquoise, 4))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(kit.hgap(8))
                    addView(kit.text(kit.n(p.rating.roundToInt()), TextStyle.HEADING, Royal.goldLight))
                    isClickable = true
                    setOnClickListener { kit.tap(it); host.push(ProfileScreen(host, p.id.name, p.id.isTeam)) }
                })
            }
            val records = StatsEngine.records(sessions) { kit.n(it) }
            if (records.isNotEmpty()) {
                addView(kit.section("رکوردها", RoyalIcon.STAR))
                records.forEach { r ->
                    addView(kit.horizontal().apply {
                        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                        setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
                        layoutParams = kit.spaced(5)
                        addView(kit.icon(RoyalIcon.CROWN, Royal.gold, 18))
                        addView(kit.hgap(8))
                        addView(kit.vertical().apply {
                            addView(kit.text(r.title, TextStyle.CAPTION, Royal.muted))
                            addView(kit.text("${r.holder}  —  ${r.value}", TextStyle.LABEL_BOLD, Royal.ivory))
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        val id = r.sessionId
                        if (id != null) {
                            isClickable = true
                            setOnClickListener { kit.tap(it); host.repo.session(id)?.let { s -> host.openSession(s) } }
                        }
                    })
                }
            }
            addView(kit.text("رتبه از ۱۰۰۰ شروع می‌شود؛ برد مقابل حریف قوی‌تر امتیاز بیشتری دارد.", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
        }
    }
}

/** Profile of one team or player: rating, record, charts, head-to-head and recent games. */
class ProfileScreen(host: MainActivity, private val name: String, private val isTeam: Boolean) : Screen(host) {
    override fun build(): View {
        val sessions = host.repo.sessions()
        val all = StatsEngine.compute(sessions)
        val p = all[Identity(name, isTeam)]
        val board = all.values.filter { it.id.isTeam == isTeam }.sortedByDescending { it.rating }
        val place = p?.let { board.indexOf(it) }?.takeIf { it >= 0 }
        val avatar = host.repo.roster().firstOrNull { it.name == name && it.isTeam == isTeam }?.avatar
            ?: sessions.firstNotNullOfOrNull { s -> s.sides.firstOrNull { it.name == name }?.avatar } ?: 0
        return scaffold(
            title = name,
            subtitle = if (isTeam) "کارنامهٔ تیم" else "کارنامهٔ بازیکن",
            actions = if (p != null) listOf(kit.iconButton(RoyalIcon.SHARE, "اشتراک تصویر کارنامه", ButtonKind.SECONDARY, 38) { shareProfile(p, place) }) else emptyList()
        ) {
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 20f)
                setPadding(kit.dp(12), kit.dp(12), kit.dp(12), kit.dp(14))
                layoutParams = kit.spaced(8)
                addView(kit.avatar(avatar, 64))
                addView(kit.text(name, TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
                if (p != null) addView(profileHeadline(p, place))
            })
            if (p == null || p.games == 0) {
                addView(emptyState(RoyalIcon.CHART, "هنوز بازی پایان‌یافته‌ای نیست", "پس از پایان اولین بازی، آمار و نمودارها اینجا نمایش داده می‌شوند."))
                return@scaffold
            }
            addView(statTiles(p))
            addView(kit.horizontal(Gravity.CENTER_VERTICAL).apply {
                background = PanelDrawable(kit.density, PanelStyle.FLAT, 16f)
                setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
                layoutParams = kit.spaced(8)
                addView(DonutView(host, listOf(p.wins to Royal.turquoise, p.draws to Royal.gold, p.losses to Royal.crimson), "${kit.n((p.winRate * 100).roundToInt())}٪", "برد", kit.density), LinearLayout.LayoutParams(kit.dp(110), kit.dp(110)))
                addView(kit.hgap(10))
                addView(kit.vertical().apply {
                    legend(this, Royal.turquoise, "برد", p.wins)
                    legend(this, Royal.gold, "مساوی", p.draws)
                    legend(this, Royal.crimson, "باخت", p.losses)
                    if (p.games > 0 && p.id.isTeam) {
                        addView(kit.gap(4))
                        addView(kit.text("امتیاز زده ${kit.n(p.pointsFor)} • خورده ${kit.n(p.pointsAgainst)}", TextStyle.CAPTION, Royal.muted))
                    }
                    val streak = when {
                        p.streak > 0 -> "${kit.n(p.streak)} برد پشت سر هم"
                        p.streak < 0 -> "${kit.n(-p.streak)} باخت پشت سر هم"
                        else -> "بدون زنجیره"
                    }
                    addView(kit.text("وضعیت فعلی: $streak", TextStyle.CAPTION, Royal.muted))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(kit.section("روند رتبه", RoyalIcon.CHART))
            val history = listOf(StatsEngine.START.toFloat()) + p.history.map { it.second.toFloat() }
            addView(kit.panel(PanelStyle.FLAT, 10).apply {
                layoutParams = kit.spaced(8)
                addView(LineChartView(host, listOf(ChartSeries(name, Royal.gold, history)), { kit.n(it) }, kit.density, fill = true, includeZero = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(160)))
                addView(kit.text("محور افقی: شمارهٔ بازی", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
            })
            addView(kit.section("بازی‌ها به تفکیک", RoyalIcon.CARDS))
            addView(kit.flow(p.byGame.map { (g, c) -> kit.badge("${g.title}: ${kit.n(c)}", Royal.turquoise) }, 6), kit.spaced(8))
            StatsEngine.bestWin(p)?.let { m -> addView(highlight("بهترین برد", "مقابل ${m.opponent} (رتبهٔ ${kit.n(m.opponentBefore.roundToInt())}) • ${StatsEngine.strengthLabel(m.ownBefore, m.opponentBefore)}", Royal.turquoiseLight, m.sessionId)) }
            StatsEngine.worstLoss(p)?.let { m -> addView(highlight("تلخ‌ترین باخت", "مقابل ${m.opponent} (رتبهٔ ${kit.n(m.opponentBefore.roundToInt())}) • ${StatsEngine.strengthLabel(m.ownBefore, m.opponentBefore)}", Royal.crimsonLight, m.sessionId)) }
            addView(kit.section("رویارویی‌ها", RoyalIcon.PLAYERS))
            StatsEngine.headToHead(p).forEach { h ->
                val now = all[Identity(h.opponent, isTeam)]?.rating ?: StatsEngine.START
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                    setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
                    layoutParams = kit.spaced(5)
                    addView(kit.vertical().apply {
                        addView(kit.text(h.opponent, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
                        addView(kit.text("${kit.n(h.games)} بازی • رتبهٔ حریف ${kit.n(now.roundToInt())} • ${StatsEngine.strengthLabel(p.rating, now)}", TextStyle.CAPTION, Royal.muted, maxLines = 1))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(kit.text(kit.n(h.wins), TextStyle.HEADING, Royal.turquoiseLight))
                    addView(kit.text(" – ${kit.n(h.draws)} – ", TextStyle.LABEL, Royal.gold))
                    addView(kit.text(kit.n(h.losses), TextStyle.HEADING, Royal.crimsonLight))
                    isClickable = true
                    setOnClickListener { kit.tap(it); host.push(ProfileScreen(host, h.opponent, isTeam)) }
                })
            }
            addView(kit.text("ترتیب اعداد: برد – مساوی – باخت", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
            addView(kit.section("بازی‌های اخیر", RoyalIcon.HISTORY))
            p.meetings.groupBy { it.sessionId }.values.map { it.first() }.sortedByDescending { it.time }.take(15).forEach { m ->
                val label = when (m.result) {
                    1 -> "برد"
                    -1 -> "باخت"
                    else -> "مساوی"
                }
                val color = when (m.result) {
                    1 -> Royal.turquoise
                    -1 -> Royal.crimsonLight
                    else -> Royal.gold
                }
                val opponents = p.meetings.filter { it.sessionId == m.sessionId }.joinToString("، ") { it.opponent }
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                    setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
                    layoutParams = kit.spaced(5)
                    addView(kit.badge(label, color, true))
                    addView(kit.hgap(8))
                    addView(kit.vertical().apply {
                        addView(kit.text("${m.game.title} • مقابل $opponents", TextStyle.LABEL_BOLD, Royal.ivory, maxLines = 1))
                        val score = if (m.ownPoints != null && m.opponentPoints != null) "${kit.signed(m.ownPoints)} به ${kit.signed(m.opponentPoints)} • " else ""
                        addView(kit.text(score + JalaliDate.format(m.time, settings.general.persianDigits), TextStyle.CAPTION, Royal.muted, maxLines = 1))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    isClickable = true
                    setOnClickListener { kit.tap(it); host.repo.session(m.sessionId)?.let { s -> host.openSession(s) } }
                })
            }
        }
    }

    private fun legend(parent: LinearLayout, color: Int, label: String, value: Int) {
        parent.addView(kit.horizontal().apply {
            addView(View(host).apply {
                background = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = kit.dpf(5f) }
            }, LinearLayout.LayoutParams(kit.dp(10), kit.dp(10)))
            addView(kit.hgap(6))
            addView(kit.weight(kit.text(label, TextStyle.LABEL, Royal.ivory)))
            addView(kit.text(kit.n(value), TextStyle.BODY_BOLD, Royal.goldLight))
        })
    }

    private fun highlight(title: String, text: String, color: Int, sessionId: Long): View = kit.vertical().apply {
        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
        setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
        layoutParams = kit.spaced(5)
        addView(kit.text(title, TextStyle.LABEL_BOLD, color))
        addView(kit.text(text, TextStyle.CAPTION, Royal.ivory))
        isClickable = true
        setOnClickListener { kit.tap(it); host.repo.session(sessionId)?.let { s -> host.openSession(s) } }
    }
}
