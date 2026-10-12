package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.roundToInt

/**
 * People and teams. Every person is entered once and used everywhere: as a member of a team in شلم and منفی
 * (and their leagues), as a player in هزارتایی and دو لو گشنیز, and in the mafia narrator.
 */
class PlayersScreen(host: MainActivity) : Screen(host) {
    private var teams = false
    private var query = ""

    override fun build(): View {
        val roster = host.repo.roster()
        val shown = roster.filter { it.isTeam == teams }
        val stats = allPeopleStats()
        val sessions = host.repo.sessions()
        return scaffold(
            title = "افراد و تیم‌ها",
            subtitle = "${kit.n(roster.count { !it.isTeam })} نفر • ${kit.n(roster.count { it.isTeam })} تیم",
            bottom = kit.button(if (teams) "افزودن تیم" else "افزودن فرد", ButtonKind.PRIMARY, RoyalIcon.PLUS) { edit(null) }
        ) {
            addView(kit.grid(2, listOf(
                kit.chip("افراد", !teams, ButtonKind.CHIP_GOLD) { teams = false; host.refresh() },
                kit.chip("تیم‌ها (شلم و منفی)", teams, ButtonKind.CHIP_GOLD) { teams = true; host.refresh() }
            ), 6), kit.spaced(10))
            if (!teams) addView(kit.text("هر نفر را یک بار ثبت کنید؛ در تیم‌های شلم و منفی، لیگ‌ها، هزارتایی، دو لو گشنیز و مافیا از همین فهرست استفاده می‌شود و کارنامهٔ هر نفر جدا ساخته می‌شود.", TextStyle.CAPTION, Royal.muted), kit.spaced(8))
            if (shown.isEmpty()) {
                addView(emptyState(RoyalIcon.PLAYERS, if (teams) "هنوز تیمی ثبت نشده" else "هنوز کسی ثبت نشده", if (teams) "هر تیم را از دو نفر از افراد بسازید." else "با «افزودن فرد» نام‌ها را یکی‌یکی وارد کنید."))
            }
            val rows = ArrayList<Pair<View, String>>()
            val searchSlot = android.widget.FrameLayout(host)
            if (shown.size >= 7) addView(searchSlot, kit.spaced(8))
            shown.forEach { entry ->
                val caption = if (teams) {
                    val related = sessions.filter { s -> s.game.isTeamGame && s.sides.any { it.name == entry.name } }
                    val wins = related.count { s -> s.finished && GameEngine.winners(s).any { s.sides[it].name == entry.name } }
                    (if (entry.members.isEmpty()) "بدون عضو" else entry.members.joinToString(" و ")) + "  •  ${kit.n(related.size)} بازی  •  ${kit.n(wins)} برد"
                } else {
                    val r = stats[entry.name]
                    if (r == null || r.totalGames == 0) "هنوز بازی پایان‌یافته‌ای ندارد"
                    else "${kit.n(r.totalGames)} بازی  •  ${kit.n(r.totalWins)} برد" + if (r.mafia.games > 0) "  •  مافیا ${kit.n(r.mafia.games)}" else ""
                }
                addView(kit.horizontal().apply {
                    rows += this to entry.name
                    background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
                    setPadding(kit.dp(10), kit.dp(7), kit.dp(10), kit.dp(10))
                    layoutParams = kit.spaced(6)
                    addView(kit.avatar(entry.avatar, 40))
                    addView(kit.hgap(10))
                    addView(kit.vertical().apply {
                        addView(kit.text(entry.name, TextStyle.BODY_BOLD, Royal.goldLight, maxLines = 1))
                        addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, maxLines = 2))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val open = { if (teams) host.push(ProfileScreen(host, entry.name, true)) else host.push(PersonProfileScreen(host, entry.name)) }
                    isClickable = true
                    setOnClickListener { kit.tap(it); open() }
                    addView(kit.iconButton(RoyalIcon.CHART, "کارنامهٔ ${entry.name}", ButtonKind.CHIP, 44) { open() })
                    addView(kit.hgap(6))
                    addView(kit.iconButton(RoyalIcon.EDIT, "ویرایش ${entry.name}", ButtonKind.CHIP, 44) { edit(entry) })
                    addView(kit.hgap(6))
                    addView(kit.iconButton(RoyalIcon.TRASH, "حذف ${entry.name}", ButtonKind.CHIP, 44) {
                        kit.confirm("حذف از فهرست", "«${entry.name}» از فهرست حذف شود؟ بازی‌های ثبت‌شده تغییری نمی‌کنند.", "حذف", true) {
                            host.repo.saveRoster(host.repo.roster().filterNot { it.id == entry.id })
                            host.refresh()
                        }
                    })
                })
            }
            if (shown.size >= 7) kit.searchFilter("جست‌وجوی نام", rows, query) { query = it }?.let { searchSlot.addView(it) }
        }
    }

    private fun edit(entry: RosterEntry?) {
        var avatar = entry?.avatar ?: (host.repo.roster().size * 3) % Emblems.COUNT
        var members = entry?.members ?: emptyList()
        val name = kit.field(if (teams) "نام تیم" else "نام فرد", entry?.name ?: "")
        val avatarView = kit.avatar(avatar, 64)
        avatarView.setOnClickListener {
            avatarPicker(avatar) { picked ->
                avatar = picked
                avatarView.avatar = picked
            }
        }
        val membersLabel = kit.text("", TextStyle.LABEL_BOLD, Royal.turquoiseLight, Gravity.CENTER)
        fun showMembers() { membersLabel.text = if (members.isEmpty()) "بدون عضو" else "اعضا: " + members.joinToString(" و ") }
        showMembers()
        val error = kit.text("", TextStyle.CAPTION, Royal.crimsonLight, Gravity.CENTER)
        lateinit var dialog: android.app.Dialog
        dialog = kit.dialog(if (entry == null) (if (teams) "تیم تازه" else "فرد تازه") else "ویرایش", null, kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(avatarView)
            addView(kit.text("برای تغییر نشان لمس کنید", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
            addView(kit.gap(8))
            addView(name, kit.fill())
            if (teams) {
                addView(kit.gap(8))
                addView(membersLabel)
                addView(kit.button("انتخاب اعضا از افراد", ButtonKind.SECONDARY, RoyalIcon.PLAYERS, 42) {
                    memberPicker(members) { picked ->
                        members = picked
                        showMembers()
                        if (name.text.isBlank() && picked.isNotEmpty()) name.setText(picked.joinToString(" و "))
                    }
                }, kit.fill())
            }
            addView(error)
        }, listOf(
            DialogAction("ذخیره", ButtonKind.PRIMARY, dismiss = false) {
                val value = name.text.toString().trim()
                val roster = host.repo.roster().toMutableList()
                when {
                    value.isEmpty() -> error.text = "نام را وارد کنید"
                    roster.any { it.name == value && it.isTeam == teams && it.id != entry?.id } -> error.text = "این نام قبلاً ثبت شده است"
                    else -> {
                        dialog.dismiss()
                        if (entry == null) roster += RosterEntry(System.currentTimeMillis(), value, avatar, teams, members)
                        else {
                            val i = roster.indexOfFirst { it.id == entry.id }
                            if (i >= 0) roster[i] = entry.copy(name = value, avatar = avatar, members = members)
                        }
                        host.repo.saveRoster(roster)
                        host.refresh()
                    }
                }
            },
            DialogAction("انصراف")
        ))
        dialog.show()
    }
}

/** Ranking of people for one game, all card games, or the mafia narrator. */
/** Every person's record in every game of the app (card games, mafia, pantomime and spy). */
fun Screen.allPeopleStats(): Map<String, PersonRecord> = PersonStats.compute(host.repo.sessions(), host.mafia.games(), host.panto.matches(), host.spy.games())

fun Screen.peopleRanking(parent: LinearLayout, sessions: List<GameSession>, game: GameType?, mafia: Boolean, party: String? = null, onFilter: (GameType?, Boolean, String?) -> Unit) {
    val all = PersonStats.compute(sessions, host.mafia.games(), host.panto.matches(), host.spy.games())
    val chips = ArrayList<android.widget.TextView>()
    chips += kit.chip("همهٔ بازی‌ها", game == null && !mafia && party == null) { onFilter(null, false, null) }
    GameType.values().forEach { g -> chips += kit.chip(g.title, game == g && !mafia && party == null) { onFilter(g, false, null) } }
    chips += kit.chip("مافیا", mafia) { onFilter(null, true, null) }
    chips += kit.chip("پانتومیم", party == PersonStats.PANTO) { onFilter(null, false, PersonStats.PANTO) }
    chips += kit.chip("جاسوس", party == PersonStats.SPY) { onFilter(null, false, PersonStats.SPY) }
    chips.forEach { it.setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(6)) }
    parent.addView(kit.flow(chips, 6), kit.spaced(8))
    val list = PersonStats.leaderboard(all, game, mafia && party == null, party)
    if (list.isEmpty()) {
        parent.addView(emptyState(RoyalIcon.TROPHY, "هنوز آماری نیست", if (party == PersonStats.PANTO) "اعضای تیم‌ها را از افراد انتخاب کنید تا با پایان هر مسابقه، آمار هر نفر ساخته شود."
            else if (party == PersonStats.SPY) "بازیکنان جاسوس را از افراد انتخاب کنید تا نتیجهٔ هر بازی برای هر نفر ثبت شود."
            else if (mafia) "با پایان هر بازی مافیا، آمار هر نفر اینجا ساخته می‌شود." else
            "تیم‌ها را از افراد بسازید تا با پایان هر بازی، رتبهٔ هر نفر ساخته شود."))
        return
    }
    val roster = host.repo.roster()
    list.forEachIndexed { place, r ->
        val avatar = roster.firstOrNull { it.name == r.name && !it.isTeam }?.avatar ?: 0
        val (value, caption, rate) = when {
            party == PersonStats.PANTO -> Triple(kit.n((r.panto.winRate * 100).roundToInt()) + "٪", "${kit.n(r.panto.wins)} برد از ${kit.n(r.panto.games)} مسابقه • ${kit.n(r.panto.points)} امتیاز", r.panto.winRate)
            party == PersonStats.SPY -> Triple(kit.n((r.spy.winRate * 100).roundToInt()) + "٪", "${kit.n(r.spy.wins)} برد از ${kit.n(r.spy.games)} بازی • جاسوس ${kit.n(r.spy.special)} بار", r.spy.winRate)
            mafia -> Triple(kit.n((r.mafia.winRate * 100).roundToInt()) + "٪", "${kit.n(r.mafia.wins)} برد از ${kit.n(r.mafia.games)} بازی • شهر ${kit.n(r.mafia.sideGames[MafiaSide.CITY] ?: 0)} • مافیا ${kit.n(r.mafia.sideGames[MafiaSide.MAFIA] ?: 0)}", r.mafia.winRate)
            game != null -> r.byGame[game]!!.let { l -> Triple(kit.n(l.rating.roundToInt()), "${kit.n(l.wins)} برد • ${kit.n(l.draws)} مساوی • ${kit.n(l.losses)} باخت • ${StatsEngine.tier(l.rating)}", l.winRate) }
            else -> Triple(kit.n(PersonStats.overall(r).roundToInt()), "${kit.n(r.totalGames)} بازی • ${kit.n(r.totalWins)} برد", if (r.totalGames == 0) 0f else r.totalWins.toFloat() / r.totalGames)
        }
        parent.addView(kit.horizontal().apply {
            background = PanelDrawable(kit.density, if (place == 0) PanelStyle.RAISED else PanelStyle.NORMAL, 16f)
            setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
            layoutParams = kit.spaced(5)
            val medal = when (place) { 0 -> ButtonKind.CHIP_GOLD; 1 -> ButtonKind.CHIP_SELECTED; else -> ButtonKind.CHIP }
            addView(kit.text(kit.n(place + 1), TextStyle.LABEL_BOLD, ButtonDrawable.textColor(medal), Gravity.CENTER).apply {
                background = ButtonDrawable(kit.density, medal, 14f)
                setPadding(0, 0, 0, kit.dp(2))
            }, LinearLayout.LayoutParams(kit.dp(30), kit.dp(30)))
            addView(kit.hgap(8))
            addView(kit.avatar(avatar, 36))
            addView(kit.hgap(8))
            addView(kit.vertical().apply {
                addView(kit.text(r.name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
                addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, maxLines = 1))
                addView(kit.progress(rate, Royal.turquoise, 4))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(kit.hgap(8))
            addView(kit.text(value, TextStyle.HEADING, Royal.goldLight))
            isClickable = true
            setOnClickListener { kit.tap(it); host.push(PersonProfileScreen(host, r.name)) }
        })
    }
}

/** One person's record in every game: card-game ratings and partners, and a full mafia analysis. */
class PersonProfileScreen(host: MainActivity, private val name: String) : Screen(host) {
    override fun build(): View {
        val all = allPeopleStats()
        val r = all[name]
        val avatar = host.repo.roster().firstOrNull { it.name == name && !it.isTeam }?.avatar ?: 0
        return scaffold(name, "کارنامهٔ فردی") {
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 20f)
                setPadding(kit.dp(12), kit.dp(12), kit.dp(12), kit.dp(14))
                layoutParams = kit.spaced(8)
                addView(kit.avatar(avatar, 64))
                addView(kit.text(name, TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
                if (r != null && r.cardGames > 0) addView(kit.text("رتبهٔ کلی ${kit.n(PersonStats.overall(r).roundToInt())} • ${StatsEngine.tier(PersonStats.overall(r))}", TextStyle.LABEL_BOLD, Royal.turquoiseLight, Gravity.CENTER))
            })
            if (r == null || r.totalGames == 0) {
                addView(emptyState(RoyalIcon.CHART, "هنوز بازی پایان‌یافته‌ای نیست", "در شلم و منفی تیم را از افراد بسازید؛ در هزارتایی، دو لو گشنیز و مافیا نام همین فرد را انتخاب کنید."))
                return@scaffold
            }
            addView(tiles(listOf(
                "بازی" to kit.n(r.totalGames),
                "برد" to kit.n(r.totalWins),
                "درصد برد" to kit.n(if (r.totalGames == 0) 0 else (r.totalWins * 100 / r.totalGames)) + "٪"
            )))
            r.byGame.filter { it.value.games > 0 }.forEach { (game, l) ->
                addView(kit.section(game.title, RoyalIcon.CARDS))
                addView(tiles(listOf(
                    "رتبه" to kit.n(l.rating.roundToInt()),
                    "بازی" to kit.n(l.games),
                    "برد" to kit.n(l.wins),
                    "مساوی" to kit.n(l.draws),
                    "باخت" to kit.n(l.losses),
                    "بهترین امتیاز" to kit.signed(l.bestScore)
                )))
                if (l.games > 0 && (l.pointsFor != 0 || l.pointsAgainst != 0)) addView(kit.text("امتیاز گرفته ${kit.signed(l.pointsFor)} • امتیاز داده ${kit.signed(l.pointsAgainst)}", TextStyle.CAPTION, Royal.muted), kit.spaced(4))
            }
            if (r.partners.isNotEmpty()) {
                addView(kit.section("هم‌تیمی‌ها", RoyalIcon.PLAYERS))
                r.partners.entries.sortedByDescending { it.value.first }.forEach { (partner, gw) ->
                    addView(line(partner, "${kit.n(gw.first)} بازی • ${kit.n(gw.second)} برد • ${kit.n(if (gw.first == 0) 0 else gw.second * 100 / gw.first)}٪") { host.push(PersonProfileScreen(host, partner)) })
                }
            }
            if (r.teams.isNotEmpty()) {
                addView(kit.section("تیم‌ها", RoyalIcon.FLAG))
                r.teams.entries.sortedByDescending { it.value }.forEach { (team, n) -> addView(line(team, "${kit.n(n)} بازی") { host.push(ProfileScreen(host, team, true)) }) }
            }
            if (r.panto.games > 0) {
                addView(kit.section("پانتومیم", RoyalIcon.STAR))
                addView(tiles(listOf(
                    "مسابقه" to kit.n(r.panto.games),
                    "برد" to kit.n(r.panto.wins),
                    "درصد برد" to kit.n((r.panto.winRate * 100).roundToInt()) + "٪",
                    "امتیاز" to kit.n(r.panto.points)
                )))
            }
            if (r.spy.games > 0) {
                addView(kit.section("جاسوس", RoyalIcon.EYE))
                addView(tiles(listOf(
                    "بازی" to kit.n(r.spy.games),
                    "برد" to kit.n(r.spy.wins),
                    "درصد برد" to kit.n((r.spy.winRate * 100).roundToInt()) + "٪",
                    "جاسوس بود" to kit.n(r.spy.special),
                    "برد جاسوسی" to kit.n(r.spy.specialWins)
                )))
            }
            val m = r.mafia
            if (m.games > 0) {
                addView(kit.section("مافیا", RoyalIcon.STAR))
                addView(tiles(listOf(
                    "بازی" to kit.n(m.games),
                    "برد" to kit.n(m.wins),
                    "درصد برد" to kit.n((m.winRate * 100).roundToInt()) + "٪",
                    "تا آخر ماند" to kit.n(m.survived),
                    "اولین خروج" to kit.n(m.firstOut),
                    "میانگین خروج" to if (m.exitNights.isEmpty()) "-" else "روز ${MafiaText.ordinal(Math.round(m.exitNights.average()).toInt())}"
                )))
                addView(kit.text("ساید", TextStyle.LABEL_BOLD, Royal.gold))
                MafiaSide.values().forEach { side ->
                    val games = m.sideGames[side] ?: 0
                    if (games == 0) return@forEach
                    val wins = m.sideWins[side] ?: 0
                    addView(kit.horizontal().apply {
                        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                        setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
                        layoutParams = kit.spaced(5)
                        addView(seal(if (side == MafiaSide.CITY) MafiaGlyph.CITIZEN else if (side == MafiaSide.MAFIA) MafiaGlyph.MASK else MafiaGlyph.JESTER, side, 36))
                        addView(kit.hgap(8))
                        addView(kit.vertical().apply {
                            addView(kit.text("${side.title}: ${kit.n(games)} بار", TextStyle.BODY_BOLD, side.textColor()))
                            addView(kit.progress(wins.toFloat() / games, Royal.turquoise, 4))
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(kit.hgap(8))
                        addView(kit.text("${kit.n(wins)} برد", TextStyle.LABEL_BOLD, Royal.goldLight))
                    })
                }
                addView(kit.text("نقش‌ها", TextStyle.LABEL_BOLD, Royal.gold))
                addView(kit.grid(4, m.roles.entries.sortedByDescending { it.value }.map { (role, n) ->
                    kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                        setPadding(kit.dp(4), kit.dp(6), kit.dp(4), kit.dp(8))
                        addView(seal(role.glyph, role.side, 40))
                        addView(kit.text(role.faName, TextStyle.CAPTION, role.side.textColor(), Gravity.CENTER, 1))
                        addView(kit.text("${kit.n(n)} بار", TextStyle.LABEL_BOLD, Royal.ivory, Gravity.CENTER))
                    }
                }, 6), kit.spaced(8))
                if (m.deaths.isNotEmpty()) {
                    addView(kit.text("نحوهٔ خروج از بازی", TextStyle.LABEL_BOLD, Royal.gold))
                    m.deaths.entries.sortedByDescending { it.value }.forEach { (type, n) -> addView(line(MafiaDeath.title(type), "${kit.n(n)} بار", null)) }
                }
            }
        }
    }

    private fun tiles(items: List<Pair<String, String>>): View = kit.grid(3, items.map { (label, value) ->
        kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
            setPadding(kit.dp(4), kit.dp(8), kit.dp(4), kit.dp(10))
            addView(kit.text(value, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER, 1))
            addView(kit.text(label, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 1))
        }
    }, 6).apply { layoutParams = kit.spaced(8) }

    private fun line(title: String, value: String, onClick: (() -> Unit)?): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
        setPadding(kit.dp(10), kit.dp(7), kit.dp(10), kit.dp(9))
        layoutParams = kit.spaced(5)
        addView(kit.weight(kit.text(title, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1)))
        addView(kit.text(value, TextStyle.LABEL, Royal.muted, maxLines = 1))
        if (onClick != null) {
            isClickable = true
            setOnClickListener { kit.tap(it); onClick() }
        }
    }
}
