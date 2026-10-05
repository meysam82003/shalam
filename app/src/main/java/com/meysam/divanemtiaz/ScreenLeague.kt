package com.meysam.divanemtiaz

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

class LeagueListScreen(host: MainActivity) : Screen(host) {
    override fun build(): View {
        val leagues = host.repo.leagues()
        return scaffold(
            title = "لیگ‌ها",
            subtitle = "مسابقهٔ چند تیم در شلم یا منفی",
            bottom = kit.button("لیگ تازه", ButtonKind.PRIMARY, RoyalIcon.PLUS) { host.push(LeagueSetupScreen(host)) }
        ) {
            if (leagues.isEmpty()) {
                addView(emptyState(RoyalIcon.TROPHY, "هنوز لیگی ساخته نشده", "تیم‌ها را وارد کنید؛ قرعه، رویارویی‌ها، جدول و فینال خودکار مشخص می‌شود."))
            }
            leagues.forEach { league ->
                val lookup: (Long) -> GameSession? = { host.repo.session(it) }
                val champion = LeagueEngine.champion(league, lookup)
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, if (league.finished) PanelStyle.RAISED else PanelStyle.NORMAL, 16f)
                    setPadding(kit.dp(10), kit.dp(9), kit.dp(10), kit.dp(12))
                    layoutParams = kit.spaced(8)
                    addView(GameSealView(host, league.game), LinearLayout.LayoutParams(kit.dp(42), kit.dp(42)))
                    addView(kit.hgap(10))
                    addView(kit.vertical().apply {
                        addView(kit.text(league.name, TextStyle.BODY_BOLD, Royal.goldLight, maxLines = 1))
                        addView(kit.text("${league.game.title} • ${formatTitle(league.format)} • ${kit.n(league.teams.size)} تیم", TextStyle.CAPTION, Royal.muted))
                        addView(kit.text(if (champion != null) "قهرمان: ${league.teams[champion].name}" else "در جریان", TextStyle.LABEL_BOLD, if (champion != null) Royal.turquoiseLight else Royal.ivory))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(kit.icon(RoyalIcon.NEXT, Royal.gold, 20))
                    isClickable = true
                    setOnClickListener { kit.tap(it); host.push(LeagueScreen(host, league.id)) }
                })
            }
        }
    }
}

fun formatTitle(format: Int): String = if (format == LeagueFormat.ROUND_ROBIN) "دوره‌ای (جدول)" else "حذفی دو برده"

class LeagueSetupScreen(host: MainActivity) : Screen(host) {
    private var game = GameType.MENFI
    private var name = "لیگ ${PersianText.digits(host.repo.leagueIndex().size + 1, host.settings.general.persianDigits)}"
    private val teams = MutableList(4) { Side("تیم ${PersianText.digits(it + 1, host.settings.general.persianDigits)}", (it * 3) % Emblems.COUNT) }
    private var format = settings.league.format
    private var winsNeeded = settings.league.winsNeeded
    private var doubleRoundRobin = settings.league.doubleRoundRobin
    private var finalAfterTable = settings.league.finalAfterTable
    private var joker = settings.shalam.defaultJoker
    private var endPoint = settings.shalam.mode(joker).defaultEndPoint
    private var menfiHands = settings.menfi.hands
    private var menfiHidden = settings.menfi.hidden

    override fun build(): View = scaffold(
        title = "لیگ تازه",
        subtitle = "تیم‌ها، قالب مسابقه و قوانین",
        bottom = kit.button("قرعه‌کشی نهایی و شروع لیگ", ButtonKind.PRIMARY, RoyalIcon.PLAY) { start() }
    ) {
        addView(kit.field("نام لیگ", name).apply { addTextChangedListener(watcher { name = it }) }, kit.spaced(8))
        addView(kit.grid(2, listOf(GameType.MENFI, GameType.SHALAM).map { g ->
            kit.chip(g.title, game == g, ButtonKind.CHIP_GOLD) { game = g; host.refresh() }
        }, 6), kit.spaced(6))
        addView(kit.choiceRow("قالب مسابقه", null, listOf(
            LeagueFormat.KNOCKOUT to "حذفی دو برده",
            LeagueFormat.ROUND_ROBIN to "دوره‌ای (همه با همه)"
        ), format) { format = it; host.refresh() })
        addView(kit.stepperRow("تعداد برد لازم در هر رویارویی", if (format == LeagueFormat.KNOCKOUT) "۲ یعنی دو برده: در ۱–۱ بازی فینال این دو تیم انجام می‌شود" else "برای فینال پس از جدول", winsNeeded, 1, 5, 1) { winsNeeded = it })
        if (format == LeagueFormat.ROUND_ROBIN) {
            addView(kit.switchRow("رفت و برگشت", "هر دو تیم دو بار با هم بازی می‌کنند", doubleRoundRobin) { doubleRoundRobin = it })
            addView(kit.switchRow("فینال بین دو تیم اول جدول", "اگر خاموش باشد صدر جدول قهرمان است (در تساوی کامل فینال برگزار می‌شود)", finalAfterTable) { finalAfterTable = it })
        }
        if (game == GameType.SHALAM) {
            addView(kit.choiceRow("جوکر", null, listOf(0 to "بدون جوکر", 1 to "با جوکر"), if (joker) 1 else 0) {
                joker = it == 1
                endPoint = settings.shalam.mode(joker).defaultEndPoint
                host.refresh()
            })
            addView(kit.stepperRow("امتیاز پایان هر بازی", null, endPoint, 400, 2800, 5) { endPoint = it })
        } else {
            addView(kit.stepperRow("تعداد دست هر بازی", null, menfiHands, 1, 40, 1) { menfiHands = it })
            addView(kit.switchRow("پنهان‌بودن جمع امتیاز", null, menfiHidden) { menfiHidden = it })
        }
        addView(kit.section("تیم‌ها (${kit.n(teams.size)})", RoyalIcon.PLAYERS, kit.button("قرعه‌کشی", ButtonKind.SECONDARY, RoyalIcon.SWAP, 34) {
            teams.shuffle()
            host.refresh()
        }))
        addView(kit.text("ترتیب فهرست، ترتیب رویارویی‌هاست: ۱ با ۲، ۳ با ۴ و …؛ با «قرعه‌کشی» تصادفی چیده می‌شود.", TextStyle.CAPTION, Royal.muted), kit.spaced(6))
        teams.indices.forEach { i -> addView(teamRow(i)) }
        addView(kit.grid(2, listOf(
            kit.button("افزودن تیم", ButtonKind.GHOST, RoyalIcon.PLUS, 40) {
                teams += Side("تیم ${kit.n(teams.size + 1)}", (teams.size * 3) % Emblems.COUNT)
                host.refresh()
            },
            kit.button("از فهرست تیم‌ها", ButtonKind.GHOST, RoyalIcon.USER, 40) {
                rosterPicker(true) { entry ->
                    teams += Side(entry.name, entry.avatar)
                    host.refresh()
                }
            }
        ), 6))
        if (format == LeagueFormat.KNOCKOUT) {
            addView(kit.section("رویارویی‌های مرحلهٔ اول", RoyalIcon.FLAG))
            val pairs = teams.chunked(2)
            pairs.forEach { p ->
                addView(kit.text(if (p.size == 2) "${p[0].name}  ⚔  ${p[1].name}" else "یک تیم با قرعه استراحت می‌کند و مستقیم صعود می‌کند", TextStyle.LABEL, Royal.ivory, Gravity.CENTER))
            }
        }
    }

    private fun teamRow(i: Int): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
        setPadding(kit.dp(8), kit.dp(5), kit.dp(8), kit.dp(8))
        layoutParams = kit.spaced(5)
        addView(kit.text(kit.n(i + 1), TextStyle.LABEL_BOLD, Royal.gold, Gravity.CENTER), LinearLayout.LayoutParams(kit.dp(22), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(kit.avatar(teams[i].avatar, 32).apply {
            setOnClickListener {
                avatarPicker(teams[i].avatar) { picked ->
                    teams[i] = teams[i].copy(avatar = picked)
                    host.refresh()
                }
            }
        })
        addView(kit.hgap(6))
        addView(kit.weight(kit.field("نام تیم", teams[i].name).apply {
            addTextChangedListener(watcher { text -> if (i < teams.size) teams[i] = teams[i].copy(name = text) })
        }))
        if (teams.size > 2) {
            addView(kit.hgap(6))
            addView(kit.iconButton(RoyalIcon.TRASH, "حذف تیم", ButtonKind.CHIP, 34) {
                teams.removeAt(i)
                host.refresh()
            })
        }
    }

    private fun start() {
        val clean = teams.mapIndexed { i, t -> Side(t.name.trim().ifBlank { "تیم ${kit.n(i + 1)}" }, t.avatar) }
        if (clean.size < 2) {
            kit.toast("دست‌کم دو تیم لازم است")
            return
        }
        if (clean.map { it.name }.distinct().size != clean.size) {
            kit.toast("نام تیم‌ها نباید تکراری باشد")
            return
        }
        val base = settings.rulesFor(game, joker, endPoint)
        val rules = if (game == GameType.MENFI) base.copy(menfi = base.menfi.copy(hands = menfiHands, hidden = menfiHidden)) else base
        val config = settings.league.copy(format = format, winsNeeded = winsNeeded, doubleRoundRobin = doubleRoundRobin, finalAfterTable = finalAfterTable)
        val id = System.currentTimeMillis()
        val league = League(id, name.trim().ifBlank { "لیگ" }, game, clean, rules, format, config)
        LeagueEngine.start(league, clean.indices.toList())
        host.repo.saveLeague(league)
        SessionOps.rosterAdd(host, clean, true)
        host.replace(LeagueScreen(host, id))
    }

    private fun watcher(onText: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onText(s?.toString().orEmpty())
    }
}

class LeagueScreen(host: MainActivity, private val leagueId: Long) : Screen(host) {
    private val lookup: (Long) -> GameSession? = { host.repo.session(it) }

    override fun build(): View {
        val league = host.repo.league(leagueId) ?: return scaffold(title = "لیگ") {
            addView(emptyState(RoyalIcon.WARNING, "این لیگ پیدا نشد", "ممکن است حذف شده باشد."))
        }
        if (LeagueEngine.advance(league, lookup)) {
            league.updatedAt = System.currentTimeMillis()
            host.repo.saveLeague(league)
        }
        val champion = LeagueEngine.champion(league, lookup)
        return scaffold(
            title = league.name,
            subtitle = "${league.game.title} • ${formatTitle(league.format)} • ${kit.n(league.teams.size)} تیم",
            actions = listOf(kit.iconButton(RoyalIcon.MENU, "گزینه‌های لیگ", ButtonKind.SECONDARY, 38) { menu(league) })
        ) {
            if (champion != null) {
                addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                    background = PanelDrawable(kit.density, PanelStyle.RAISED, 20f)
                    setPadding(kit.dp(12), kit.dp(12), kit.dp(12), kit.dp(15))
                    layoutParams = kit.spaced(10)
                    addView(TrophyView(host), LinearLayout.LayoutParams(kit.dp(84), kit.dp(84)))
                    addView(kit.avatar(league.teams[champion].avatar, 44))
                    addView(kit.text("قهرمان لیگ: ${league.teams[champion].name}", TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
                })
            }
            if (league.format == LeagueFormat.ROUND_ROBIN) table(this, league)
            LeagueEngine.stages(league).reversed().forEach { stage ->
                addView(kit.section(LeagueEngine.stageTitle(league, stage), RoyalIcon.FLAG))
                league.matches.filter { it.stage == stage }.forEach { addView(matchCard(league, it)) }
            }
            addView(kit.section("قوانین لیگ", RoyalIcon.RULES))
            addView(kit.panel(PanelStyle.FLAT, 12).apply {
                layoutParams = kit.spaced(6)
                RulesText.leagueLines(league.format, league.config, kit).forEach { addView(kit.text("•  $it", TextStyle.LABEL, Royal.ivory)) }
            })
        }
    }

    private fun table(parent: LinearLayout, league: League) {
        parent.addView(kit.section("جدول", RoyalIcon.CHART))
        val rows = LeagueEngine.standings(league, lookup)
        parent.addView(kit.vertical().apply {
            background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
            setPadding(kit.dp(8), kit.dp(6), kit.dp(8), kit.dp(9))
            layoutParams = kit.spaced(6)
            addView(tableRow(listOf("#", "تیم", "بازی", "برد", "مساوی", "باخت", "تفاضل", "امتیاز"), Royal.gold, true))
            rows.forEachIndexed { place, r ->
                addView(tableRow(listOf(kit.n(place + 1), league.teams[r.team].name, kit.n(r.played), kit.n(r.won), kit.n(r.drawn), kit.n(r.lost), kit.signed(r.diff), kit.n(r.leaguePoints)), if (place == 0) Royal.goldLight else Royal.ivory, false))
            }
        })
        parent.addView(kit.text("رتبه‌بندی: امتیاز لیگ، سپس تفاضل، سپس امتیاز زده، سپس تعداد برد.", TextStyle.CAPTION, Royal.muted), kit.spaced(6))
    }

    private fun tableRow(cells: List<String>, color: Int, header: Boolean): View = kit.horizontal().apply {
        setPadding(0, kit.dp(3), 0, kit.dp(3))
        val weights = listOf(0.5f, 2.4f, 0.8f, 0.8f, 0.9f, 0.8f, 1.1f, 1f)
        cells.forEachIndexed { i, c ->
            addView(kit.text(c, if (header) TextStyle.CAPTION else TextStyle.LABEL, color, if (i == 1) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER, 1),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weights[i]))
        }
    }

    private fun teamName(league: League, team: Int?, feeder: Int): String = when {
        team != null -> league.teams[team].name
        feeder >= 0 -> "برندهٔ رویارویی ${kit.n(feeder)}"
        else -> "—"
    }

    private fun matchCard(league: League, match: LeagueMatch): View {
        val st = LeagueEngine.state(league, match, lookup)
        return kit.vertical().apply {
            background = PanelDrawable(kit.density, when {
                st.decided -> PanelStyle.NORMAL
                st.isDecider || match.isFinal -> PanelStyle.RAISED
                else -> PanelStyle.SELECTED
            }, 16f)
            setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(11))
            layoutParams = kit.spaced(8)
            addView(kit.horizontal().apply {
                addView(kit.badge("رویارویی ${kit.n(match.id)}", Royal.gold))
                addView(kit.hgap(6))
                when {
                    match.isBye -> addView(kit.badge("استراحت • صعود مستقیم", Royal.turquoise))
                    st.decided && st.winner != null -> addView(kit.badge("برنده: ${league.teams[st.winner].name}", Royal.turquoise, true))
                    st.decided -> addView(kit.badge("انجام شد", Royal.turquoise))
                    st.isDecider -> addView(kit.badge("یک به یک • بازی فینال", Royal.crimsonLight, true))
                    match.isFinal -> addView(kit.badge("فینال", Royal.gold, true))
                    else -> addView(kit.badge(if (league.format == LeagueFormat.ROUND_ROBIN && !match.isFinal) "یک بازی" else "${kit.n(match.winsNeeded)} برد لازم", Royal.muted))
                }
            })
            addView(kit.gap(6))
            if (match.isBye) {
                addView(kit.text("${teamName(league, st.teamA, match.feederA)} در این مرحله بازی ندارد و به مرحلهٔ بعد می‌رود.", TextStyle.LABEL, Royal.ivory))
                return@apply
            }
            addView(kit.horizontal(Gravity.CENTER).apply {
                addView(kit.weight(kit.text(teamName(league, st.teamA, match.feederA), TextStyle.BODY_BOLD, if (st.winner != null && st.winner == st.teamA) Royal.goldLight else Royal.ivory, Gravity.CENTER, 2).apply {
                    st.teamA?.let { t -> setOnClickListener { kit.tap(it); host.push(ProfileScreen(host, league.teams[t].name, true)) } }
                }))
                addView(kit.text("${kit.n(st.winsA)}  –  ${kit.n(st.winsB)}", TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
                addView(kit.weight(kit.text(teamName(league, st.teamB, match.feederB), TextStyle.BODY_BOLD, if (st.winner != null && st.winner == st.teamB) Royal.goldLight else Royal.ivory, Gravity.CENTER, 2).apply {
                    st.teamB?.let { t -> setOnClickListener { kit.tap(it); host.push(ProfileScreen(host, league.teams[t].name, true)) } }
                }))
            })
            if (st.draws > 0) addView(kit.text("${kit.n(st.draws)} بازی مساوی" + if (league.format == LeagueFormat.KNOCKOUT || match.isFinal) " (حساب نمی‌شود و تکرار می‌شود)" else "", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
            val games = match.games.mapNotNull { id -> lookup(id) }
            if (games.isNotEmpty()) {
                addView(kit.gap(4))
                addView(kit.flow(games.mapIndexed { k, s ->
                    val totals = GameEngine.totals(s)
                    val label = if (s.finished) "بازی ${kit.n(k + 1)}: ${kit.signed(totals[0])} \u200F|\u200F ${kit.signed(totals[1])}" else "بازی ${kit.n(k + 1)}: در جریان"
                    kit.chip(label, !s.finished) { host.openSession(s) }.apply { setPadding(kit.dp(8), kit.dp(3), kit.dp(8), kit.dp(5)) }
                }, 5))
            }
            if (st.ready && !st.decided) {
                addView(kit.gap(6))
                val open = st.openGame
                addView(kit.button(
                    if (open != null) "ادامهٔ بازی ${kit.n(match.games.indexOf(open) + 1)}" else if (st.isDecider) "شروع بازی فینال" else "شروع بازی ${kit.n(st.played + 1)}",
                    if (open != null) ButtonKind.SUCCESS else ButtonKind.PRIMARY, RoyalIcon.PLAY, 40
                ) { play(league, match, st) })
            } else if (!st.ready) {
                addView(kit.text("منتظر نتیجهٔ رویارویی‌های قبلی", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
            }
        }
    }

    private fun play(league: League, match: LeagueMatch, st: MatchState) {
        st.openGame?.let { id -> lookup(id)?.let { host.openSession(it); return } }
        val a = st.teamA ?: return
        val b = st.teamB ?: return
        val id = host.repo.newSessionId()
        val session = GameSession(
            id = id,
            game = league.game,
            sides = listOf(league.teams[a], league.teams[b]),
            rules = league.rules,
            updatedAt = id,
            label = "${league.name} • ${LeagueEngine.stageTitle(league, match.stage)}",
            leagueId = league.id
        )
        host.repo.save(session)
        val index = league.matches.indexOfFirst { it.id == match.id }
        league.matches[index] = match.copy(games = match.games + id)
        league.updatedAt = System.currentTimeMillis()
        host.repo.saveLeague(league)
        host.push(host.boardFor(session))
    }

    private fun shareLeague(league: League) {
        val champion = LeagueEngine.champion(league, lookup)
        val card = kit.vertical().apply {
            setPadding(kit.dp(16), kit.dp(16), kit.dp(16), kit.dp(14))
            addView(kit.horizontal().apply {
                addView(GameSealView(host, league.game), LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)))
                addView(kit.hgap(10))
                addView(kit.vertical().apply {
                    addView(kit.text(league.name, TextStyle.TITLE, Royal.goldLight))
                    addView(kit.text("${league.game.title} • ${formatTitle(league.format)} • ${kit.n(league.teams.size)} تیم", TextStyle.CAPTION, Royal.muted))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(kit.divider())
            if (champion != null) addView(kit.text("قهرمان: ${league.teams[champion].name}", TextStyle.HEADING, Royal.turquoiseLight, Gravity.CENTER), kit.spaced(8))
            if (league.format == LeagueFormat.ROUND_ROBIN) table(this, league)
            LeagueEngine.stages(league).reversed().forEach { stage ->
                addView(kit.text(LeagueEngine.stageTitle(league, stage), TextStyle.BODY_BOLD, Royal.gold), kit.spaced(4))
                league.matches.filter { it.stage == stage }.forEach { m ->
                    val st = LeagueEngine.state(league, m, lookup)
                    val text = if (m.isBye) "${teamName(league, st.teamA, m.feederA)}: استراحت"
                    else "${teamName(league, st.teamA, m.feederA)}  ${kit.n(st.winsA)} – ${kit.n(st.winsB)}  ${teamName(league, st.teamB, m.feederB)}"
                    addView(kit.text(text, TextStyle.LABEL, if (st.decided) Royal.ivory else Royal.muted, Gravity.CENTER))
                }
            }
            addView(kit.gap(8))
            addView(kit.text("دیوان امتیاز", TextStyle.CAPTION, Royal.dim, Gravity.CENTER))
        }
        host.shareImage(card, "لیگ ${league.name} در دیوان امتیاز")
    }

    private fun menu(league: League) {
        lateinit var dialog: android.app.Dialog
        val list = kit.vertical().apply {
            addView(kit.button("تغییر نام لیگ", ButtonKind.SECONDARY, height = 44) {
                dialog.dismiss()
                kit.textPrompt("نام لیگ", league.name) { v ->
                    league.name = v
                    host.repo.saveLeague(league)
                    host.refresh()
                }
            }, kit.spaced(8))
            addView(kit.button("قوانین بازی‌های این لیگ", ButtonKind.SECONDARY, height = 44) {
                dialog.dismiss()
                host.push(RulesScreen(host, GameSession(0L, league.game, league.teams.take(2), rules = league.rules)))
            }, kit.spaced(8))
            addView(kit.button("اشتراک تصویر لیگ", ButtonKind.SECONDARY, height = 44) {
                dialog.dismiss()
                shareLeague(league)
            }, kit.spaced(8))
            addView(kit.button("رنک‌بندی و کارنامهٔ تیم‌ها", ButtonKind.SECONDARY, height = 44) {
                dialog.dismiss()
                host.push(RankingScreen(host))
            }, kit.spaced(8))
            addView(kit.button("حذف لیگ", ButtonKind.DANGER, height = 44) {
                dialog.dismiss()
                kit.confirm("حذف لیگ", "جدول این لیگ حذف شود؟ بازی‌های انجام‌شده در تاریخچه می‌مانند.", "حذف", true) {
                    host.repo.deleteLeague(league.id)
                    host.pop()
                }
            }, kit.spaced(8))
        }
        dialog = kit.dialog("گزینه‌های لیگ", null, list, listOf(DialogAction("بستن")))
        dialog.show()
    }
}
