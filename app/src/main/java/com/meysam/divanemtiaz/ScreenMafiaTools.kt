package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** «ترتیب نشستن»: the narrator arranges seats as people sit and picks who starts talking. */
class MafiaSeatingScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private var changed = false

    override fun build(): View {
        if (!load()) return missing()
        val starter = MafiaManual.speakingOrder(g).firstOrNull()?.id
        return scaffold("ترتیب نشستن", "به ترتیبی که بازیکنان نشسته‌اند بچینید",
            bottom = kit.button("تایید", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) {
                if (changed) { MafiaManual.seatLog(g); store() }
                host.pop()
            }) {
            addView(kit.text("با فلش‌ها جای هر نفر را عوض کنید و با «سر صحبت» نفر شروع‌کننده را مشخص کنید. صحبت، همسایه‌های بمبر و دست دادن از همین ترتیب پیروی می‌کنند.", TextStyle.LABEL, Royal.muted), kit.spaced(8))
            g.players.forEachIndexed { i, p ->
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, if (p.id == starter) PanelStyle.SELECTED else if (p.alive) PanelStyle.FLAT else PanelStyle.GHOST, 14f)
                    setPadding(kit.dp(8), kit.dp(6), kit.dp(8), kit.dp(8))
                    layoutParams = kit.spaced(5)
                    addView(kit.badge(kit.n(i + 1), Royal.gold, true))
                    addView(kit.hgap(6))
                    addView(kit.avatar(p.avatar, 38))
                    addView(kit.hgap(6))
                    addView(kit.vertical().apply {
                        addView(kit.text(p.name, TextStyle.BODY_BOLD, if (p.alive) Royal.ivory else Royal.dim, maxLines = 1))
                        addView(kit.text(if (!p.alive) MafiaDeath.title(p.deathType) else if (p.id == starter) "سر صحبت" else "در بازی", TextStyle.CAPTION, if (p.id == starter) Royal.turquoiseLight else Royal.muted, maxLines = 1))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (p.alive && p.id != starter) addView(kit.chip("سر صحبت", false) { MafiaManual.setStarter(g, p.id); changed = true; store(); host.refresh() }.apply { setPadding(kit.dp(8), kit.dp(4), kit.dp(8), kit.dp(6)) })
                    addView(kit.hgap(4))
                    addView(kit.iconButton(RoyalIcon.NEXT, "بالاتر ${p.name}", ButtonKind.CHIP, 36) { MafiaManual.move(g, p.id, -1); changed = true; store(); host.refresh() }.apply { rotation = 90f })
                    addView(kit.hgap(4))
                    addView(kit.iconButton(RoyalIcon.NEXT, "پایین‌تر ${p.name}", ButtonKind.CHIP, 36) { MafiaManual.move(g, p.id, 1); changed = true; store(); host.refresh() }.apply { rotation = -90f })
                })
            }
        }
    }
}

/**
 * «ابزار راوی»: every correction by hand — take anyone out or bring him back, change roles, silence,
 * reveal, abilities and armour, substitutes, forcing day or night, and declaring or withdrawing the result.
 */
class MafiaToolsScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    override fun build(): View {
        if (!load()) return missing()
        return scaffold("ابزار راوی", g.phaseTitle + if (g.finished) " • پایان یافته" else "") {
            addView(kit.section("روند بازی", RoyalIcon.FLAG))
            val flow = ArrayList<View>()
            if (g.finished) flow += big("ادامهٔ بازی", "لغو پایان بازی", ButtonKind.SUCCESS, RoyalIcon.PLAY) {
                kit.confirm("ادامهٔ بازی", "پایان بازی لغو شود و بازی ادامه پیدا کند؟", "ادامه") { MafiaManual.resume(g); store(); host.refresh() }
            } else flow += big("اعلام برنده", "پایان بازی به انتخاب راوی", ButtonKind.PRIMARY, RoyalIcon.CROWN) { declare() }
            if (!g.finished) {
                if (g.isDay) {
                    flow += big("رفتن به شب", "بدون بررسی مراحل روز", ButtonKind.SECONDARY, RoyalIcon.NEXT) {
                        kit.confirm("رفتن به شب", "روز بدون انجام مراحل باقی‌مانده تمام شود؟", "شب می‌شود") {
                            MafiaManual.forceNight(g); store(); playCue(MafiaCues.NIGHT_START); host.pop()
                        }
                    }
                    flow += big("رد کردن مراحل روز", "همهٔ مراحل امروز انجام‌شده حساب شوند", ButtonKind.SECONDARY, RoyalIcon.CHECK) { MafiaManual.skipDay(g); store(); kit.toast("مراحل روز انجام‌شده علامت خوردند"); host.refresh() }
                    flow += big("رأی‌گیری از نو", "رأی‌ها و دفاعیهٔ امروز پاک شوند", ButtonKind.SECONDARY, RoyalIcon.UNDO) {
                        kit.confirm("رأی‌گیری از نو", "رأی‌گیری امروز از ابتدا انجام شود؟", "از نو") { MafiaManual.resetVotes(g); store(); host.refresh() }
                    }
                } else {
                    flow += big("رفتن به روز", "بدون بررسی کارهای شب", ButtonKind.SECONDARY, RoyalIcon.NEXT) {
                        kit.confirm("رفتن به روز", "شب با همین کارهای انجام‌شده تمام شود؟", "روز می‌شود") {
                            host.audio().stopMusic()
                            MafiaManual.forceDay(g); MafiaFlow.checkWin(g); store(); playCue(MafiaCues.DAY_START); host.pop()
                        }
                    }
                }
            }
            flow += big("ترتیب نشستن", "جابه‌جایی صندلی‌ها و سر صحبت", ButtonKind.SECONDARY, RoyalIcon.SWAP) { host.push(MafiaSeatingScreen(host, gameId)) }
            if (!g.finished) flow += big("جایگزینی بازیکن", "یک نفر می‌رود و دیگری با نقش او می‌نشیند", ButtonKind.SECONDARY, RoyalIcon.USER) { substitute() }
            addView(kit.grid(2, flow, 8), kit.spaced(10))

            addView(kit.section("بازیکنان", RoyalIcon.PLAYERS))
            g.players.forEach { p -> addView(row(p)) }
        }
    }

    private fun big(title: String, caption: String, kind: ButtonKind, icon: RoyalIcon, onClick: () -> Unit): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        background = PanelDrawable(kit.density, if (kind == ButtonKind.PRIMARY || kind == ButtonKind.SUCCESS) PanelStyle.RAISED else PanelStyle.NORMAL, 16f)
        setPadding(kit.dp(6), kit.dp(10), kit.dp(6), kit.dp(12))
        addView(kit.icon(icon, if (kind == ButtonKind.SUCCESS) Royal.turquoiseLight else Royal.gold, 26))
        addView(kit.text(title, TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER, 1))
        addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 2))
        isClickable = true
        contentDescription = title
        setOnClickListener { kit.tap(it); onClick() }
    }

    private fun row(p: MafiaPlayer): View = kit.vertical().apply {
        background = PanelDrawable(kit.density, if (p.alive) PanelStyle.FLAT else PanelStyle.GHOST, 16f)
        setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
        layoutParams = kit.spaced(6)
        val role = p.shownRole
        addView(kit.horizontal().apply {
            addView(kit.avatar(p.avatar, 40))
            addView(kit.hgap(8))
            addView(kit.vertical().apply {
                addView(kit.text(p.name, TextStyle.BODY_BOLD, if (p.alive) Royal.ivory else Royal.dim, maxLines = 1))
                val state = buildList {
                    add(if (p.alive) "در بازی" else MafiaDeath.title(p.deathType))
                    if (MafiaFlow.silenced(p) != null) add("ساکت")
                    if (p.revealed) add("نقش اعلام شده")
                    if (p.armor > 0) add("زره ${kit.n(p.armor)}")
                }
                addView(kit.text(state.joinToString(" • "), TextStyle.CAPTION, Royal.muted, maxLines = 2))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (role != null) addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                addView(seal(role.glyph, role.side, 36))
                addView(kit.text(role.faName, TextStyle.CAPTION, (p.side ?: role.side).textColor(), Gravity.CENTER, 1))
            }, LinearLayout.LayoutParams(kit.dp(76), ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        addView(kit.gap(6))
        val actions = ArrayList<View>()
        fun chip(label: String, kind: ButtonKind = ButtonKind.CHIP, onClick: () -> Unit) {
            actions += kit.chip(label, false, kind) { onClick() }.apply {
                if (kind != ButtonKind.CHIP) background = ButtonDrawable(kit.density, kind, 12f)
                setTextColor(ButtonDrawable.textColor(kind))
                setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(6))
            }
        }
        if (p.alive) chip("خروج", ButtonKind.DANGER) { exitDialog(p) }
        else if (p.role != null) chip("بازگشت به بازی", ButtonKind.SUCCESS) {
            kit.confirm("بازگشت به بازی", "${p.name} با نقش ${p.role?.faName} به بازی برگردد؟", "بازگشت") { MafiaManual.revive(g, p.id); store(); host.refresh() }
        }
        if (p.role != null) {
            chip("تغییر نقش") {
                rolePicker("نقش جدید ${p.name}", MafiaRole.values().toList()) { r -> MafiaManual.setRole(g, p.id, r); store(); host.refresh() }
            }
            if (p.alive) chip(if (MafiaFlow.silenced(p) != null) "برداشتن سکوت" else "ساکت کردن") { MafiaManual.toggleSilence(g, p.id); store(); host.refresh() }
            chip(if (p.revealed) "پنهان کردن نقش" else "اعلام نقش") { MafiaManual.toggleReveal(g, p.id); store(); host.refresh() }
            chip("قابلیت‌ها و زره") { abilities(p) }
        }
        addView(kit.flow(actions, 6))
    }

    private fun exitDialog(p: MafiaPlayer) {
        val actions = MafiaDeath.manual.map { type ->
            DialogAction(MafiaDeath.title(type)) {
                if (g.isDay && type != MafiaDeath.DISCIPLINE && type != MafiaDeath.LEFT && type != MafiaDeath.NIGHT && MafiaFlow.remainingCards(g).isNotEmpty()) {
                    kit.dialog("کارت حرکت آخر", "آیا برای خروج حق انتخاب کارت حرکت اخر را دارد؟", null, listOf(
                        DialogAction("بله", ButtonKind.PRIMARY) { host.push(MafiaExitScreen(host, gameId, listOf(p.id), type)) },
                        DialogAction("خیر") { MafiaManual.exit(g, p.id, type); MafiaFlow.checkWin(g); store(); host.refresh() }
                    )).show()
                } else { MafiaManual.exit(g, p.id, type); MafiaFlow.checkWin(g); store(); host.refresh() }
            }
        } + DialogAction("لغو")
        kit.dialog("خروج ${p.name}", "نوع خروج را انتخاب کنید", null, actions).show()
    }

    private fun abilities(p: MafiaPlayer) {
        val body = kit.vertical().apply {
            addView(kit.stepperRow("زره", null, p.armor, 0, 9, 1) { v -> p.armor = v; store() })
            p.counters.keys.filter { it != "poison" && it != "buy" && it != "trade" }.sorted().forEach { k ->
                val label = if (k == "once") "قابلیت یک‌باره" else k
                val v = p.counter(k)
                addView(kit.stepperRow(label, if (v >= 1000) "نامحدود" else null, if (v >= 1000) 99 else v, 0, 99, 1) { nv -> p.counters[k] = if (nv >= 99) 1000 else nv; store() })
            }
        }
        kit.dialog("قابلیت‌های ${p.name}", p.role?.faName, body, listOf(DialogAction("بستن") {
            g.log += MafiaLog(g.phaseTitle, "راوی قابلیت‌های ${p.name} را تنظیم کرد"); store(); host.refresh()
        })).show()
    }

    private fun declare() {
        val actions = ArrayList<DialogAction>()
        actions += DialogAction("شهروندها", ButtonKind.SUCCESS) { finish("CITY") }
        actions += DialogAction("گروه مافیا", ButtonKind.DANGER) { finish("MAFIA") }
        g.players.mapNotNull { it.shownRole }.filter { it.side == MafiaSide.INDEPENDENT }.distinct().forEach { r -> actions += DialogAction(r.faName) { finish(r.name) } }
        actions += DialogAction("لغو")
        kit.dialog("اعلام برنده", "برندهٔ بازی را انتخاب کنید", null, actions).show()
    }

    private fun finish(key: String) {
        MafiaManual.declare(g, key)
        store()
        playCue(MafiaFlow.winCue(g.winner))
        host.pop()
    }

    private fun substitute() {
        pickPlayers("چه کسی بازی را ترک می‌کند؟", g.alive.filter { it.role != null }, 1, 1, true) { l ->
            val leaving = l.first()
            val outs = g.out.filter { it.role != null || it.formerRole == null }
            val roster = host.repo.roster().filter { !it.isTeam && g.players.none { p -> p.name == it.name } }
            val actions = ArrayList<DialogAction>()
            if (outs.isNotEmpty()) actions += DialogAction("یکی از خارج‌شده‌ها", ButtonKind.PRIMARY) {
                pickPlayers("چه کسی جای او می‌نشیند؟", outs, 1, 1, true) { inc -> done(leaving, inc.first(), "", 0) }
            }
            if (roster.isNotEmpty()) actions += DialogAction("از فهرست افراد") {
                lateinit var d: android.app.Dialog
                d = kit.dialog("فرد جدید", null, kit.grid(3, roster.map { e ->
                    kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                        setPadding(kit.dp(4), kit.dp(8), kit.dp(4), kit.dp(10))
                        addView(kit.avatar(e.avatar, 44))
                        addView(kit.text(e.name, TextStyle.LABEL_BOLD, Royal.ivory, Gravity.CENTER, 1))
                        isClickable = true
                        setOnClickListener { d.dismiss(); done(leaving, null, e.name, e.avatar) }
                    }
                }, 6), listOf(DialogAction("لغو")))
                d.show()
            }
            actions += DialogAction("نام جدید") {
                kit.textPrompt("نام فرد جدید", "") { name ->
                    val all = host.repo.roster().toMutableList()
                    val avatar = all.firstOrNull { it.name == name && !it.isTeam }?.avatar ?: ((all.size * 7 + 3) % Emblems.COUNT)
                    if (all.none { it.name == name && !it.isTeam }) { all += RosterEntry(System.currentTimeMillis(), name, avatar, false); host.repo.saveRoster(all) }
                    done(leaving, null, name, avatar)
                }
            }
            actions += DialogAction("لغو")
            kit.dialog("جایگزین", "چه کسی با نقش ${g.player(leaving)?.name} بازی را ادامه می‌دهد؟", null, actions).show()
        }
    }

    private fun done(leaving: Int, incoming: Int?, name: String, avatar: Int) {
        val b = MafiaManual.substitute(g, leaving, incoming, name, avatar)
        store()
        if (b != null) kit.dialog("جایگزینی بازیکن", "${b.name} با نقش ${b.role?.faName} به جای ${g.player(leaving)?.name} نشست.\nنقش را به او نشان دهید.", null, listOf(DialogAction("تایید") { host.refresh() })).show()
    }
}
