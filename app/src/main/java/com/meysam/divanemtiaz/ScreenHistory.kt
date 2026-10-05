package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.abs

class ResultScreen(host: MainActivity, private val session: GameSession) : Screen(host) {
    override val sessionId: Long get() = session.id

    override fun build(): View {
        val totals = GameEngine.totals(session)
        val winners = GameEngine.winners(session)
        val winnerText = if (winners.isEmpty()) "مساوی" else "برنده: ${winners.joinToString(" و ") { session.sides[it].name }}"
        return scaffold(
            title = "نتیجهٔ بازی ${session.game.title}",
            subtitle = sessionSubtitle(session),
            bottom = kit.vertical().apply {
                if (session.leagueId != 0L && host.repo.league(session.leagueId) != null) {
                    addView(kit.button("بازگشت به لیگ", ButtonKind.PRIMARY, RoyalIcon.TROPHY) { backToLeague() }, kit.spaced(8))
                } else {
                    addView(kit.button("بازی دوباره با همین ترکیب", ButtonKind.PRIMARY, RoyalIcon.PLAY) { rematch() }, kit.spaced(8))
                }
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.button("ادامهٔ بازی", ButtonKind.SECONDARY, RoyalIcon.UNDO, 48) { reopen() }))
                    addView(kit.hgap(8))
                    addView(kit.weight(kit.button("خانه", ButtonKind.SECONDARY, RoyalIcon.HOME, 48) { host.resetTo(HomeScreen(host)) }))
                })
            }
        ) {
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 22f)
                setPadding(kit.dp(14), kit.dp(16), kit.dp(14), kit.dp(18))
                layoutParams = kit.spaced(10)
                addView(TrophyView(host), LinearLayout.LayoutParams(kit.dp(96), kit.dp(96)))
                addView(kit.text(winnerText, TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
                addView(kit.text(reason(totals), TextStyle.LABEL, Royal.muted, Gravity.CENTER))
            })
            if (session.game.isTeamGame) {
                addView(kit.horizontal(Gravity.TOP).apply {
                    layoutParams = kit.spaced(10)
                    session.sides.forEachIndexed { i, side ->
                        if (i > 0) addView(kit.hgap(10))
                        addView(kit.weight(sideScoreCard(side, kit.signed(totals[i]), null, winners.singleOrNull() == i, null)))
                    }
                })
            } else {
                GameEngine.ranking(session).forEachIndexed { place, i ->
                    addView(kit.horizontal().apply {
                        background = PanelDrawable(kit.density, if (place == 0 && winners.isNotEmpty()) PanelStyle.RAISED else PanelStyle.NORMAL, 16f)
                        setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
                        layoutParams = kit.spaced(5)
                        addView(kit.text("${kit.n(place + 1)}.", TextStyle.BODY_BOLD, Royal.gold))
                        addView(kit.hgap(8))
                        addView(kit.avatar(session.sides[i].avatar, 34))
                        addView(kit.hgap(8))
                        addView(kit.weight(kit.text(session.sides[i].name, TextStyle.BODY_BOLD, maxLines = 1)))
                        addView(kit.text(kit.signed(totals[i]), TextStyle.HEADING, Royal.goldLight))
                    })
                }
            }
            addView(kit.panel(PanelStyle.FLAT, 14).apply {
                layoutParams = kit.spaced(10)
                listOf(
                    "تعداد دست‌ها" to kit.n(GameEngine.playedHands(session)),
                    "مدت بازی" to timerText(session),
                    "شروع" to JalaliDate.format(session.id, settings.general.persianDigits),
                    "پایان" to (session.endedAt?.let { JalaliDate.format(it, settings.general.persianDigits) } ?: "—")
                ).forEach { (label, value) ->
                    addView(kit.horizontal().apply {
                        addView(kit.weight(kit.text(label, TextStyle.LABEL, Royal.muted)))
                        addView(kit.text(value, TextStyle.LABEL_BOLD, Royal.ivory))
                    })
                }
            })
            if (session.game == GameType.SHALAM) {
                addView(kit.button("آمار کامل بازی", ButtonKind.GHOST, RoyalIcon.CHART, 46) { shalamStatsDialog(session) }, kit.spaced(10))
            }
            addView(kit.section("دست‌ها", RoyalIcon.HISTORY))
            session.rounds.forEachIndexed { index, round ->
                addView(roundRow(index, session.sides.indices.map { kit.signed(round.score(it)) }, session.sides.indices.map { scoreColor(round.score(it)) }, describeRound(session, round), null))
            }
        }
    }

    private fun reason(totals: List<Int>): String = when (session.game) {
        GameType.SHALAM -> {
            val r = session.rules.shalam
            when {
                totals.any { it >= r.endPoint } -> "رسیدن به امتیاز پایان ${kit.n(r.endPoint)}"
                r.mode.endWithDiff && abs(totals[0] - totals[1]) >= r.mode.endDiff -> "رسیدن اختلاف امتیازها به ${kit.n(r.mode.endDiff)}"
                else -> "پایان با تأیید داور"
            }
        }
        GameType.MENFI -> {
            val r = session.rules.menfi
            val basis = if (r.highWins) "بیشترین جمع" else "کمترین جمع"
            if (GameEngine.playedHands(session) >= r.hands) "پایان ${kit.n(r.hands)} دست • برنده با $basis" else "پایان با تأیید داور • برنده با $basis"
        }
        GameType.HEZARTAII -> {
            val r = session.rules.hezar
            when {
                r.target > 0 && totals.any { it >= r.target } -> "رسیدن به امتیاز هدف ${kit.n(r.target)}"
                r.rounds > 0 && GameEngine.playedHands(session) >= r.rounds -> "پایان ${kit.n(r.rounds)} دور"
                else -> "پایان با تأیید داور"
            }
        }
        GameType.DOLO -> if (DoloEngine.state(session).active.size == 1) "آخرین بازیکن باقی‌مانده" else "پایان با تأیید داور"
    }

    private fun reopen() {
        session.finished = false
        session.endedAt = null
        SessionOps.commit(host, session)
        host.replace(host.boardFor(session))
    }

    private fun backToLeague() {
        val league = host.repo.league(session.leagueId) ?: return
        host.resetTo(HomeScreen(host), LeagueListScreen(host), LeagueScreen(host, league.id))
    }

    private fun rematch() {
        val id = host.repo.newSessionId()
        val fresh = GameSession(id, session.game, session.sides.toList(), rules = session.rules, updatedAt = id, label = session.label)
        host.resetTo(HomeScreen(host), host.boardFor(fresh))
    }
}

fun Screen.describeRound(session: GameSession, round: Round): String {
    val team = session.sides.getOrNull(round.contractTeam)?.name ?: ""
    return when (round.kind) {
        RoundKind.SHALAM_HAND -> {
            val o = ShalamEngine.outcome(round, session.rules.shalam)
            "حاکم: $team • تعهد ${kit.n(round.bid)} • حریف ${kit.n(round.taken)} • ${if (o.succeeded) "موفق" else if (o.isDouble) "دوبل" else "ناموفق"}"
        }
        RoundKind.SHALAM_SHELEM -> "حاکم: $team • شلم • ${if (round.taken == 0) "برد" else "باخت"}"
        RoundKind.SHALAM_DOUBLE_SHELEM -> "حاکم: $team • شلم دوبل • ${if (round.taken == 0) "برد" else "باخت"}"
        RoundKind.SHALAM_PASS -> "پاس"
        RoundKind.MENFI_HAND -> listOfNotNull(
            if (round.numbers.size == 2) "اعداد ${kit.n(round.numbers[0])} و ${kit.n(round.numbers[1])}" else null,
            menfiOutcomeTitle(session, round.outcome).ifBlank { null }
        ).joinToString(" • ")
        RoundKind.HEZAR_ROUND -> round.raw.mapIndexed { i, v -> "${session.sides.getOrNull(i)?.name ?: ""} ${kit.n(v)}" }.joinToString(" • ")
        RoundKind.DOLO_HAND -> {
            val parts = mutableListOf("حداقل ${kit.n(round.bid)}")
            session.sides.getOrNull(round.contractTeam)?.let { parts += "حکم: ${it.name}" + if (round.suit != Suit.NONE) " (${Suit.title(round.suit)})" else "" }
            if (round.outcome == MenfiEngine.MANUAL) parts += "ثبت دستی"
            else {
                val missed = session.sides.indices.filter { round.numbers.getOrElse(it) { -1 } >= 0 && round.raw.getOrElse(it) { 0 } != 1 }
                parts += if (missed.isEmpty()) "همه گرفتند" else "نگرفتند: ${missed.joinToString("، ") { session.sides[it].name }}"
            }
            parts.joinToString(" • ")
        }
        RoundKind.DOLO_ELIM -> {
            val out = round.raw.indices.filter { round.raw[it] == 1 }
            if (out.isNotEmpty()) "حذف: ${out.joinToString("، ") { session.sides.getOrNull(it)?.name ?: "" }}"
            else "${round.note.ifBlank { "ادامه" }} • ${kit.n(round.bid)} دست اضافه"
        }
        else -> round.note
    }
}

class HistoryScreen(host: MainActivity) : Screen(host) {
    private var status = 0
    private var gameFilter: GameType? = null

    override fun build(): View {
        val all = host.repo.sessions()
        val shown = all.filter { s ->
            (gameFilter == null || s.game == gameFilter) &&
                when (status) {
                    1 -> !s.finished
                    2 -> s.finished
                    else -> true
                }
        }
        return scaffold(title = "تاریخچهٔ بازی‌ها", subtitle = "${kit.n(all.size)} بازی ذخیره‌شده") {
            addView(kit.grid(3, listOf(
                kit.chip("همه", status == 0) { status = 0; host.refresh() },
                kit.chip("در جریان", status == 1) { status = 1; host.refresh() },
                kit.chip("پایان‌یافته", status == 2) { status = 2; host.refresh() }
            ), 6), kit.spaced(6))
            addView(kit.flow(listOf(
                kit.chip("همهٔ بازی‌ها", gameFilter == null, ButtonKind.CHIP_GOLD) { gameFilter = null; host.refresh() }
            ) + GameType.values().map { g ->
                kit.chip(g.title, gameFilter == g, ButtonKind.CHIP_GOLD) { gameFilter = g; host.refresh() }
            }.map { it.apply { setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(6)) } }, 6), kit.spaced(10))
            if (shown.isEmpty()) {
                addView(emptyState(RoyalIcon.HISTORY, "بازی‌ای پیدا نشد", "پس از ثبت اولین دست، بازی اینجا ذخیره می‌شود."))
            }
            shown.forEach { addView(card(it)) }
            if (all.isNotEmpty()) {
                addView(kit.gap(12))
                addView(kit.button("پاک‌کردن کل تاریخچه", ButtonKind.DANGER, RoyalIcon.TRASH, 48) {
                    kit.confirm("پاک‌کردن تاریخچه", "تمام بازی‌های ذخیره‌شده برای همیشه حذف شوند؟", "حذف همه", true) {
                        host.repo.clearHistory()
                        host.refresh()
                    }
                })
            }
        }
    }

    private fun card(session: GameSession): View {
        val totals = GameEngine.totals(session)
        val hidden = session.game == GameType.MENFI && session.rules.menfi.hidden && !session.finished
        val winners = if (session.finished) GameEngine.winners(session) else emptyList()
        return kit.vertical().apply {
            background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
            setPadding(kit.dp(10), kit.dp(9), kit.dp(10), kit.dp(12))
            layoutParams = kit.spaced(8)
            addView(kit.horizontal().apply {
                addView(GameSealView(host, session.game), LinearLayout.LayoutParams(kit.dp(36), kit.dp(36)))
                addView(kit.hgap(10))
                addView(kit.vertical().apply {
                    addView(kit.horizontal().apply {
                        addView(kit.text(session.game.title, TextStyle.BODY_BOLD, Royal.goldLight))
                        addView(kit.hgap(8))
                        addView(if (session.finished) kit.badge("پایان‌یافته", Royal.gold) else kit.badge("در جریان", Royal.turquoise, true))
                    })
                    addView(kit.text(JalaliDate.format(session.updatedAt, settings.general.persianDigits), TextStyle.CAPTION, Royal.muted))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(kit.iconButton(RoyalIcon.TRASH, "حذف بازی", ButtonKind.CHIP, 38) {
                    kit.confirm("حذف بازی", "این بازی برای همیشه از تاریخچه حذف شود؟", "حذف", true) {
                        host.repo.delete(session.id)
                        host.refresh()
                    }
                })
            })
            addView(kit.gap(8))
            val shownSides = if (session.game.isTeamGame) session.sides.indices.toList() else GameEngine.ranking(session).take(4)
            shownSides.forEach { i ->
                val side = session.sides[i]
                addView(kit.horizontal().apply {
                    addView(kit.avatar(side.avatar, 24))
                    addView(kit.hgap(8))
                    addView(kit.weight(kit.text(side.name + if (i in winners) "  ♛" else "", TextStyle.LABEL_BOLD, if (i in winners) Royal.goldLight else Royal.ivory, maxLines = 1)))
                    addView(kit.text(if (hidden) "•••" else kit.signed(totals[i]), TextStyle.BODY_BOLD, if (hidden) Royal.dim else scoreColor(totals[i])))
                })
            }
            if (shownSides.size < session.sides.size) addView(kit.text("و ${kit.n(session.sides.size - shownSides.size)} بازیکن دیگر", TextStyle.CAPTION, Royal.dim))
            addView(kit.gap(4))
            addView(kit.text("${kit.n(GameEngine.playedHands(session))} دست  •  ${timerText(session)}  •  ${sessionSubtitle(session)}", TextStyle.CAPTION, Royal.dim, maxLines = 2))
            isClickable = true
            contentDescription = "باز کردن بازی ${session.game.title}"
            setOnClickListener { kit.tap(it); host.openSession(session) }
        }
    }
}
