package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/** «طراحی بازی»: three tabs (players, roles, last-move cards) and «توزیع نقش». The draft is saved on every change. */
class MafiaDesignScreen(host: MainActivity, private var tab: Int = 0) : Screen(host) {
    private val draft: MafiaGame = host.mafia.draft()
    private var side = MafiaSide.CITY

    private fun save() { host.mafia.saveDraft(draft); host.refresh() }

    override fun build(): View {
        val (ok, message) = MafiaFlow.validate(draft.players.size, draft.roleCounts, draft.deck.map { it.card })
        return scaffold("طراحی بازی", "${kit.n(draft.players.size)} بازیکن  •  ${kit.n(draft.roleCounts.values.sum())} نقش  •  ${kit.n(draft.deck.size)} کارت",
            actions = listOf(kit.iconButton(RoyalIcon.SAVE, "ذخیره به عنوان گروه", ButtonKind.SECONDARY, 44) { saveGroup() }),
            bottom = kit.button("توزیع نقش", if (ok) ButtonKind.PRIMARY else ButtonKind.GHOST, RoyalIcon.CARDS, 52) {
                if (!ok) kit.toast(message) else {
                    draft.players.forEach { it.role = null; it.seen = false }
                    host.mafia.saveDraft(draft)
                    host.push(MafiaDistributeScreen(host))
                }
            }) {
            addView(kit.grid(3, listOf("بازیکنان", "نقش ها", "کارت حرکت آخر").mapIndexed { i, t ->
                kit.chip(t, tab == i, ButtonKind.CHIP_GOLD) { tab = i; host.refresh() }
            }, 6), kit.spaced(8))
            addView(kit.panel(if (ok) PanelStyle.SUCCESS else PanelStyle.DANGER, 10).apply {
                layoutParams = kit.spaced(10)
                addView(kit.horizontal().apply {
                    addView(kit.icon(if (ok) RoyalIcon.CHECK else RoyalIcon.WARNING, if (ok) Royal.turquoiseLight else Royal.crimsonLight, 22))
                    addView(kit.hgap(8))
                    addView(kit.weight(kit.text(message, TextStyle.BODY_BOLD, if (ok) Royal.turquoiseLight else Royal.crimsonLight)))
                })
            })
            when (tab) {
                0 -> playersTab(this)
                1 -> rolesTab(this)
                else -> cardsTab(this)
            }
        }
    }

    // ------------------------------------------------------------------ players

    private fun playersTab(parent: LinearLayout) {
        val roster = host.repo.roster().filter { !it.isTeam }
        parent.addView(kit.section("بازیکنان این بازی (${kit.n(draft.players.size)})", RoyalIcon.PLAYERS))
        if (draft.players.isEmpty()) parent.addView(kit.text("از فهرست پایین بازیکنان را انتخاب کنید. ترتیب این فهرست، ترتیب نشستن و صحبت است.", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(8))
        draft.players.forEachIndexed { i, p ->
            parent.addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(8))
                layoutParams = kit.spaced(5)
                addView(kit.badge(kit.n(i + 1), Royal.gold, true))
                addView(kit.hgap(8))
                addView(kit.avatar(p.avatar, 38))
                addView(kit.hgap(8))
                addView(kit.weight(kit.text(p.name, TextStyle.BODY_BOLD)))
                addView(kit.iconButton(RoyalIcon.NEXT, "بالاتر", ButtonKind.CHIP, 36) {
                    if (i > 0) { draft.players.add(i - 1, draft.players.removeAt(i)); save() }
                }.apply { rotation = 90f })
                addView(kit.hgap(4))
                addView(kit.iconButton(RoyalIcon.CLOSE, "حذف از بازی", ButtonKind.CHIP, 36) { draft.players.removeAt(i); save() })
            })
        }
        parent.addView(kit.section("فهرست بازیکنان", RoyalIcon.USER, kit.iconButton(RoyalIcon.PLUS, "بازیکن جدید", ButtonKind.CHIP_GOLD, 40) { addPlayer() }))
        if (roster.isEmpty()) parent.addView(kit.text("هنوز بازیکنی ثبت نشده است؛ با + اضافه کنید.", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
        parent.addView(kit.grid(3, roster.map { e ->
            val inGame = draft.players.any { it.name == e.name }
            kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = PanelDrawable(kit.density, if (inGame) PanelStyle.SELECTED else PanelStyle.FLAT, 14f)
                setPadding(kit.dp(4), kit.dp(8), kit.dp(4), kit.dp(10))
                addView(kit.avatar(e.avatar, 46))
                addView(kit.text(e.name, TextStyle.LABEL_BOLD, if (inGame) Royal.turquoiseLight else Royal.ivory, Gravity.CENTER, 1))
                isClickable = true
                contentDescription = e.name
                setOnClickListener {
                    kit.tap(it)
                    if (inGame) draft.players.removeAll { p -> p.name == e.name }
                    else draft.players += MafiaPlayer((draft.players.maxOfOrNull { p -> p.id } ?: 0) + 1, e.name, e.avatar)
                    save()
                }
            }
        }, 6))
    }

    private fun addPlayer() {
        kit.textPrompt("نام بازیکن", "") { name ->
            val n = name.trim()
            if (n.isEmpty()) return@textPrompt
            val roster = host.repo.roster().toMutableList()
            val avatar = (roster.size * 7 + 3) % Emblems.COUNT
            if (roster.none { it.name == n && !it.isTeam }) {
                roster += RosterEntry(System.currentTimeMillis(), n, avatar, false)
                host.repo.saveRoster(roster)
            }
            if (draft.players.none { it.name == n }) draft.players += MafiaPlayer((draft.players.maxOfOrNull { it.id } ?: 0) + 1, n, roster.first { it.name == n && !it.isTeam }.avatar)
            save()
        }
    }

    // ------------------------------------------------------------------ roles

    private fun rolesTab(parent: LinearLayout) {
        val c = draft.roleCounts.filter { it.key.side == MafiaSide.CITY }.values.sum()
        val m = draft.roleCounts.filter { it.key.side == MafiaSide.MAFIA }.values.sum()
        val i = draft.roleCounts.filter { it.key.side == MafiaSide.INDEPENDENT }.values.sum()
        parent.addView(kit.grid(3, listOf(MafiaSide.CITY to c, MafiaSide.MAFIA to m, MafiaSide.INDEPENDENT to i).map { (s, n) ->
            kit.chip("${s.title}  ${kit.n(n)}", side == s, ButtonKind.CHIP_GOLD) { side = s; host.refresh() }
        }, 6), kit.spaced(8))
        val selected = draft.roleCounts.filter { it.value > 0 }.keys
        if (selected.isNotEmpty()) {
            parent.addView(kit.flow(selected.map { r ->
                kit.badge("${r.faName}${if ((draft.roleCounts[r] ?: 0) > 1) " ×" + kit.n(draft.roleCounts[r]!!) else ""}", r.side.textColor())
            }, 4), kit.spaced(8))
        }
        parent.addView(kit.grid(3, MafiaRole.values().filter { it.side == side }.map { r ->
            val count = draft.roleCounts[r] ?: 0
            FrameLayout(host).apply {
                background = PanelDrawable(kit.density, if (count > 0) PanelStyle.SELECTED else PanelStyle.GHOST, 14f)
                setPadding(kit.dp(4), kit.dp(4), kit.dp(4), kit.dp(5))
                addView(roleCard(r).apply { alpha = if (count > 0) 1f else 0.78f }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                if (count > 0) addView(kit.badge(if (count > 1) "×" + kit.n(count) else "✓", Royal.turquoise, true),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT).apply { topMargin = kit.dp(4); leftMargin = kit.dp(4) })
                isClickable = true
                contentDescription = r.faName
                setOnClickListener { kit.tap(it); roleDialog(r) }
            }
        }, 6))
    }

    private fun roleDialog(role: MafiaRole) {
        val current = draft.roleCounts[role] ?: 0
        val values = HashMap(draft.options[role] ?: emptyMap())
        var count = if (current > 0) current else 1
        val body = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(roleCard(role), LinearLayout.LayoutParams(kit.dp(150), kit.dp((150 * RoleCardView.RATIO).toInt())))
            addView(kit.gap(6))
            addView(kit.text("ویژگی های نقش : ", TextStyle.LABEL_BOLD, Royal.gold))
            addView(kit.text(MafiaRoleText.explain(role), TextStyle.LABEL, Royal.ivory))
            val defs = MafiaRoleOptions.of(role)
            if (defs.isNotEmpty()) {
                addView(kit.gap(6))
                addView(kit.text("قابلیت های قابل تنظیم نقش", TextStyle.LABEL_BOLD, Royal.gold))
            }
            defs.forEach { d ->
                if (d.values.size > 20) {
                    addView(kit.stepperRow(d.label, null, count, 1, d.values.size, 1) { count = it })
                } else {
                    val sel = values[d.label] ?: d.default
                    addView(kit.choiceRow(d.label, null, d.values.mapIndexed { idx, v -> idx to kit.n(v) }, d.values.indexOf(sel).coerceAtLeast(0)) { idx ->
                        values[d.label] = d.values[idx]
                    })
                }
            }
        }
        val actions = ArrayList<DialogAction>()
        actions += DialogAction(if (current > 0) "تایید" else "افزودن به بازی", ButtonKind.PRIMARY) {
            draft.roleCounts[role] = if (role.isMulti) count else 1
            draft.options[role] = values
            save()
        }
        if (current > 0) actions += DialogAction("حذف کن", ButtonKind.DANGER) {
            kit.confirm("حذف نقش", "نقش ${role.faName} را می خوای حذف کنی؟", "حذف کن", true) {
                draft.roleCounts.remove(role); draft.options.remove(role); save()
            }
        }
        actions += DialogAction("لغو")
        kit.dialog(role.faName, null, body, actions).show()
    }

    // ------------------------------------------------------------------ cards

    private fun cardsTab(parent: LinearLayout) {
        parent.addView(kit.horizontal().apply {
            layoutParams = kit.spaced(8)
            addView(kit.weight(kit.button("انتخاب همه", ButtonKind.SECONDARY, RoyalIcon.CHECK, 42) {
                draft.deck.clear()
                MafiaLastCard.values().filter { c -> c.requires == null || (draft.roleCounts[c.requires] ?: 0) > 0 }.forEach { draft.deck += MafiaCardState(it) }
                save()
            }))
            addView(kit.hgap(6))
            addView(kit.weight(kit.button("حذف همه", ButtonKind.GHOST, RoyalIcon.CLOSE, 42) { draft.deck.clear(); save() }))
        })
        parent.addView(kit.grid(3, MafiaLastCard.values().map { c ->
            val on = draft.deck.any { it.card == c }
            FrameLayout(host).apply {
                background = PanelDrawable(kit.density, if (on) PanelStyle.SELECTED else PanelStyle.GHOST, 14f)
                setPadding(kit.dp(4), kit.dp(4), kit.dp(4), kit.dp(5))
                addView(lastCard(c).apply { alpha = if (on) 1f else 0.75f }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                if (on) addView(kit.badge("✓", Royal.turquoise, true), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT).apply { topMargin = kit.dp(4); leftMargin = kit.dp(4) })
                isClickable = true
                contentDescription = c.faName
                setOnClickListener {
                    kit.tap(it)
                    if (on) draft.deck.removeAll { s -> s.card == c } else draft.deck += MafiaCardState(c)
                    save()
                }
                setOnLongClickListener { cardInfo(c); true }
            }
        }, 6))
        parent.addView(kit.text("برای دیدن توضیح هر کارت، آن را نگه دارید.", TextStyle.CAPTION, Royal.dim, Gravity.CENTER), kit.spaced(4))
    }

    private fun saveGroup() {
        kit.textPrompt("نام گروه", "") { name ->
            if (name.isBlank()) return@textPrompt
            val groups = host.mafia.groups()
            groups.put(org.json.JSONObject().put("name", name.trim()).put("game", MafiaCodec.encodeGame(draft, withSnapshots = false)))
            host.mafia.saveGroups(groups)
            kit.toast("گروه «${name.trim()}» ذخیره شد")
        }
    }
}

/** «توزیع نقش»: random or manual assignment, then each player sees his own card. */
class MafiaDistributeScreen(host: MainActivity) : Screen(host) {
    private val draft: MafiaGame = host.mafia.draft()
    private var showAll = false

    private fun save() { host.mafia.saveDraft(draft); host.refresh() }

    override fun build(): View {
        val allSeen = draft.players.isNotEmpty() && draft.players.all { it.role != null && it.seen }
        return scaffold("توزیع نقش", "${kit.n(draft.players.count { it.seen })} از ${kit.n(draft.players.size)} نفر نقش خود را دیده‌اند",
            bottom = if (allSeen) kit.button("شروع", ButtonKind.SUCCESS, RoyalIcon.PLAY, 52) { start() } else null) {
            addView(kit.grid(3, listOf(
                kit.button("توزیع نقش", ButtonKind.PRIMARY, RoyalIcon.CARDS, 46) {
                    MafiaNight.distribute(draft); draft.players.forEach { it.seen = false }; save()
                },
                kit.button(if (showAll) "مخفی سازی نقش" else "نمایش نقش", ButtonKind.SECONDARY, if (showAll) RoyalIcon.EYE_OFF else RoyalIcon.EYE, 46) { showAll = !showAll; host.refresh() },
                kit.button("حذف نقش", ButtonKind.GHOST, RoyalIcon.CLOSE, 46) { draft.players.forEach { it.role = null; it.seen = false }; save() }
            ), 6), kit.spaced(10))
            addView(kit.text(if (draft.players.any { it.role == null }) "روی هر بازیکن بدون نقش بزنید تا دستی نقش بدهید، یا «توزیع نقش» را بزنید."
                else "هر بازیکن روی نام خودش بزند، نقشش را ببیند و «دیدم» را بزند.", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(8))
            addView(kit.grid(3, draft.players.map { p ->
                val badge = when { p.role == null -> "بدون نقش"; p.seen -> "دیده شد ✓"; else -> "منتظر دیدن" }
                playerTile(p, showAll, p.seen, badge) {
                    if (p.role == null) manualPick(p) else host.push(MafiaRevealScreen(host, p.id))
                }
            }, 6))
        }
    }

    private fun manualPick(p: MafiaPlayer) {
        val pool = ArrayList<MafiaRole>()
        draft.roleCounts.forEach { (r, c) -> repeat(c) { pool += r } }
        draft.players.mapNotNull { it.role }.forEach { pool.remove(it) }
        if (pool.isEmpty()) { kit.toast("نقشی باقی نمانده است"); return }
        rolePicker("یکی از نقش های باقیمانده را انتخاب کنید", pool.distinct()) { r -> p.role = r; p.seen = false; save() }
    }

    private fun start() {
        val g = MafiaGame(System.currentTimeMillis())
        draft.players.forEach { d -> g.players += MafiaPlayer(d.id, d.name, d.avatar).apply { role = d.role; seen = true } }
        g.roleCounts.putAll(draft.roleCounts)
        draft.options.forEach { (r, m) -> g.options[r] = HashMap(m) }
        draft.deck.forEach { g.deck += MafiaCardState(it.card) }
        MafiaFlow.start(g, mafiaSettings)
        host.mafia.save(g)
        draft.players.forEach { it.role = null; it.seen = false }
        host.mafia.saveDraft(draft)
        host.resetTo(HomeScreen(host), MafiaHomeScreen(host), MafiaGameScreen(host, g.id))
    }
}

/** One player's private look at his role card («دیدم»). */
class MafiaRevealScreen(host: MainActivity, private val playerId: Int) : Screen(host) {
    private val draft: MafiaGame = host.mafia.draft()
    private var shown = false

    override fun build(): View {
        val p = draft.player(playerId) ?: return scaffold("نمایش نقش") {}
        val role = p.role ?: return scaffold("نمایش نقش") {}
        return scaffold(p.name, "فقط خود بازیکن نگاه کند",
            bottom = kit.button(if (shown) "دیدم" else "نمایش نقش من", if (shown) ButtonKind.SUCCESS else ButtonKind.PRIMARY, if (shown) RoyalIcon.CHECK else RoyalIcon.EYE, 52) {
                if (!shown) { shown = true; host.refresh() } else {
                    p.seen = true
                    host.mafia.saveDraft(draft)
                    host.pop()
                    host.refresh()
                }
            }) {
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                addView(kit.avatar(p.avatar, 64))
                addView(kit.text(p.name, TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
                addView(kit.gap(8))
                addView(roleCard(role, faceDown = !shown).apply {
                    isClickable = true
                    setOnClickListener { if (!shown) { shown = true; host.refresh() } }
                }, LinearLayout.LayoutParams(kit.dp(230), kit.dp((230 * RoleCardView.RATIO).toInt())))
                addView(kit.gap(10))
                if (shown) {
                    addView(kit.badge(role.side.title, role.side.textColor()))
                    addView(kit.gap(6))
                    addView(kit.text(MafiaRoleText.explain(role), TextStyle.BODY, Royal.ivory, Gravity.CENTER))
                    val defs = MafiaRoleOptions.of(role).filter { !it.label.startsWith("تعداد ${role.faName}") }
                    if (defs.isNotEmpty()) {
                        addView(kit.gap(6))
                        defs.forEach { d ->
                            addView(kit.horizontal().apply {
                                addView(kit.weight(kit.text(d.label, TextStyle.LABEL, Royal.muted)))
                                addView(kit.text(kit.n(draft.options[role]?.get(d.label) ?: d.default), TextStyle.BODY_BOLD, Royal.ivory))
                            })
                        }
                    }
                } else addView(kit.text("برای دیدن نقش روی کارت بزنید", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
            })
        }
    }
}
