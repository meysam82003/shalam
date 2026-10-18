package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** «طراحی بازی»: three tabs (players, roles, last-move cards) and «توزیع نقش». The draft is saved on every change. */
class MafiaDesignScreen(host: MainActivity, private var tab: Int = 0) : MafiaSectionScreen(host) {
    /** Re-read on every build: the reveal screen saves «دیدم» into the stored draft. */
    private var draft: MafiaGame = host.mafia.draft()
    private var side = MafiaSide.CITY
    private var showAll = false
    private var scenario: MafiaScenario? = null

    // Live parts of the page, updated in place: a change to the draft never rebuilds the page or its cards.
    private var content: LinearLayout? = null
    private var statusBox: FrameLayout? = null
    private var sideTabs: SlidingTabs? = null
    private var selectedBox: FrameLayout? = null
    private var roleGrid: CardGrid? = null
    private var cardGrid: CardGrid? = null
    private var emptyRoles: TextView? = null
    private var roleQuery = ""
    private var rosterQuery = ""
    private var cardQuery = ""
    /** One cell per role / card for the life of the page (stable keys). */
    private val roleCells = HashMap<MafiaRole, RoleCell>()
    private val cardCells = HashMap<MafiaLastCard, CardCell>()

    private fun save() {
        host.mafia.saveDraft(draft)
        updateLive()
    }

    /** Refreshes everything that depends on the draft without touching the rest of the page. */
    private fun updateLive() {
        subtitleView?.text = subtitle()
        renderStatus()
        when (tab) {
            0 -> showTab(false)
            1 -> {
                updateSideLabels()
                renderSelected()
                roleCells.values.forEach { it.bind() }
                updateRoleGrid(true)
            }
            else -> cardCells.values.forEach { it.bind() }
        }
    }

    private fun distribute() {
        val (ok, message) = MafiaFlow.validate(draft.players.size, draft.roleCounts, draft.deck.map { it.card })
        val go = {
            // Narrator's call: missing roles are filled with simple citizens.
            val missing = draft.players.size - draft.roleCounts.values.sum()
            if (missing > 0) draft.roleCounts[MafiaRole.CITIZEN] = (draft.roleCounts[MafiaRole.CITIZEN] ?: 0) + missing
            draft.players.forEach { it.role = null; it.seen = false }
            // «ذهن زیبا» only for the special role that is in this game.
            val keep = MafiaScenarios.matchMind(draft.deck.map { it.card }, draft.roleCounts.filter { it.value > 0 }.keys)
            draft.deck.removeAll { it.card !in keep }
            host.mafia.saveDraft(draft)
            host.push(MafiaDistributeScreen(host))
        }
        when {
            ok -> go()
            draft.players.size < 2 -> kit.toast(message)
            else -> kit.confirm("ادامه به تشخیص راوی", message + "\nبا این حال توزیع نقش انجام شود؟" +
                if (draft.players.size > draft.roleCounts.values.sum()) "\n(نقش‌های کم با شهروند ساده پر می‌شوند)" else "", "ادامه") { go() }
        }
    }

    private fun subtitle() = (scenario?.let { it.name + "  •  " } ?: "") +
        "${kit.n(draft.players.size)} بازیکن  •  ${kit.n(draft.roleCounts.values.sum())} نقش  •  ${kit.n(draft.deck.size)} کارت"

    private var bottomBox = FrameLayout(host)

    private fun renderStatus() {
        val box = statusBox ?: return
        val (ok, message) = MafiaFlow.validate(draft.players.size, draft.roleCounts, draft.deck.map { it.card })
        bottomBox.removeAllViews()
        bottomBox.addView(kit.button("توزیع نقش", if (ok) ButtonKind.PRIMARY else ButtonKind.SECONDARY, RoyalIcon.CARDS, 52) { distribute() })
        box.removeAllViews()
        box.addView(kit.panel(if (ok) PanelStyle.SUCCESS else PanelStyle.DANGER, 10).apply {
            addView(kit.horizontal().apply {
                addView(kit.icon(if (ok) RoyalIcon.CHECK else RoyalIcon.WARNING, if (ok) Royal.turquoiseLight else Royal.crimsonLight, 22))
                addView(kit.hgap(8))
                addView(kit.weight(kit.text(message, TextStyle.BODY_BOLD, if (ok) Royal.turquoiseLight else Royal.crimsonLight)))
            })
        })
    }

    /** Swaps only the tab's content (with a short cross-fade), never the page. */
    private fun showTab(animate: Boolean) {
        val box = content ?: return
        roleGrid = null; cardGrid = null; sideTabs = null; selectedBox = null; emptyRoles = null
        box.removeAllViews()
        when (tab) {
            0 -> playersTab(box)
            1 -> rolesTab(box)
            else -> cardsTab(box)
        }
        if (animate && kit.settings.general.animations) {
            box.alpha = 0f
            box.translationY = kit.dpf(8f)
            box.animate().alpha(1f).translationY(0f).setDuration(Motion.ITEM).setInterpolator(Motion.EASE_OUT).start()
        }
    }

    override fun build(): View {
        draft = host.mafia.draft()
        scenario = host.mafia.scenario(draft.extra["scenario"])
        MafiaRoleRegistry.activate(scenario)
        // Names or rules may have changed on another page: cells are made afresh for this build and then reused.
        roleCells.clear(); cardCells.clear()
        bottomBox = FrameLayout(host)
        return scaffold("طراحی بازی", subtitle(),
            actions = listOf(kit.iconButton(RoyalIcon.RULES, "تغییر سناریو", ButtonKind.SECONDARY, 44) { host.push(MafiaScenarioListScreen(host)) },
                kit.iconButton(RoyalIcon.SAVE, "ذخیره به عنوان گروه", ButtonKind.SECONDARY, 44) { saveGroup() }),
            bottom = bottomBox) {
            addView(SlidingTabs(host, kit, listOf("بازیکنان", "نقش ها", "کارت حرکت آخر"), tab) { i -> tab = i; showTab(true) }, kit.spaced(8))
            statusBox = FrameLayout(host)
            addView(statusBox, kit.spaced(10))
            renderStatus()
            content = kit.vertical()
            addView(content, kit.fill())
            showTab(false)
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
        val tiles = roster.map { e ->
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
        }
        kit.searchFilter("جست‌وجوی بازیکن", tiles.zip(roster.map { it.name }), rosterQuery, minItems = 7) { rosterQuery = it }?.let { parent.addView(it, kit.spaced(8)) }
        parent.addView(kit.grid(3, tiles, 6))
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

    private fun sideCount(s: MafiaSide) = draft.roleCounts.filter { it.key.side == s }.values.sum()

    private fun sideLabel(s: MafiaSide) = "${s.title}  ${kit.n(sideCount(s))}"

    private fun updateSideLabels() {
        val t = sideTabs ?: return
        MafiaSide.values().forEachIndexed { i, s -> t.setLabel(i, sideLabel(s)) }
    }

    private fun renderSelected() {
        val box = selectedBox ?: return
        box.removeAllViews()
        val selected = draft.roleCounts.filter { it.value > 0 }.keys
        if (selected.isEmpty()) { box.visibility = View.GONE; return }
        box.visibility = View.VISIBLE
        box.addView(kit.flow(selected.map { r ->
            kit.badge("${r.faName}${if ((draft.roleCounts[r] ?: 0) > 1) " ×" + kit.n(draft.roleCounts[r]!!) else ""}", r.side.textColor())
        }, 4))
    }

    /** Roles shown for the current side / pool / search; filtering only, on data already in memory. */
    private fun shownRoles(): List<MafiaRole> {
        val sc = scenario?.takeIf { it.id != MafiaScenarios.FREE }
        val selected = draft.roleCounts.filter { it.value > 0 }.keys
        val pool = sc?.pool?.takeIf { it.isNotEmpty() && !showAll }
        val base = pool?.let { p -> (p + selected).distinct() } ?: MafiaRoleRegistry.available()
        // A search looks through every side, so a name is found without knowing its side first.
        return if (roleQuery.isNotBlank()) base.filter { SearchText.matches(roleQuery, it.faName, it.defaultName, it.tagline) }
        else base.filter { it.side == side }
    }

    private fun updateRoleGrid(animate: Boolean) {
        val grid = roleGrid ?: return
        val shown = shownRoles()
        grid.submit(shown.map { it.name }, animate) { key -> roleCell(MafiaRole.valueOf(key)) }
        emptyRoles?.let {
            it.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            it.text = if (roleQuery.isNotBlank()) "نقشی با «${roleQuery.trim()}» پیدا نشد" else "در این سناریو نقشی از این ساید نیست؛ «همهٔ نقش‌ها» را بزنید."
        }
    }

    private fun roleCell(r: MafiaRole): RoleCell = roleCells.getOrPut(r) { RoleCell(r) }.also { it.bind() }

    /** A role card with its selection frame and count badge; re-bound in place when the draft changes. */
    private inner class RoleCell(val role: MafiaRole) : FrameLayout(host) {
        val card = roleCard(role)
        private var badgeView: TextView? = null
        private val badge: TextView get() = badgeView ?: kit.badge("✓", Royal.turquoise, true).also {
            badgeView = it
            addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT).apply { topMargin = kit.dp(4); leftMargin = kit.dp(4) })
        }
        private var state = -1

        init {
            setPadding(kit.dp(4), kit.dp(4), kit.dp(4), kit.dp(5))
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            isClickable = true
            contentDescription = role.faName
            setOnClickListener { kit.tap(it); roleDialog(role) }
        }

        fun bind() {
            val count = draft.roleCounts[role] ?: 0
            if (count == state) return
            val was = state
            state = count
            background = PanelDrawable(kit.density, if (count > 0) PanelStyle.SELECTED else PanelStyle.GHOST, 14f)
            card.alpha = if (count > 0) 1f else 0.78f
            if (count > 0) {
                badge.text = if (count > 1) "×" + kit.n(count) else "✓"
                badge.visibility = View.VISIBLE
            } else badgeView?.visibility = View.GONE
            if (was >= 0 && count > 0 && was != count && kit.settings.general.animations) kit.pulse(badge)
        }
    }

    private fun rolesTab(parent: LinearLayout) {
        sideTabs = SlidingTabs(host, kit, MafiaSide.values().map { sideLabel(it) }, side.ordinal) { i ->
            side = MafiaSide.values()[i]
            updateRoleGrid(true)
        }
        parent.addView(sideTabs, kit.spaced(8))
        val sc = scenario?.takeIf { it.id != MafiaScenarios.FREE }
        if (sc != null) {
            val ref = sc.lineup(draft.players.size)
            parent.addView(kit.panel(PanelStyle.FLAT, 10).apply {
                layoutParams = kit.spaced(8)
                addView(kit.text("سناریو: ${sc.name}", TextStyle.BODY_BOLD, Royal.goldLight))
                addView(kit.text(if (ref != null) "ترکیب مرجع ${kit.n(draft.players.size)} نفره فقط پیشنهاد است؛ هر نقشی را می‌توانید کم، اضافه یا جایگزین کنید."
                    else "برای ترکیب مرجع ابتدا بازیکنان را انتخاب کنید.", TextStyle.CAPTION, Royal.muted))
                addView(kit.gap(6))
                addView(kit.horizontal().apply {
                    if (ref != null) {
                        addView(kit.weight(kit.button("ترکیب مرجع", ButtonKind.SECONDARY, RoyalIcon.CARDS, 40) {
                            draft.roleCounts.clear(); draft.options.clear(); draft.roleCounts.putAll(ref); save()
                        }))
                        addView(kit.hgap(6))
                    }
                    addView(kit.weight(kit.button("ذخیره در سناریو", ButtonKind.SECONDARY, RoyalIcon.SAVE, 40) { saveToScenario(sc) }))
                })
            })
            if (sc.pool.isNotEmpty()) parent.addView(SlidingTabs(host, kit, listOf("نقش‌های سناریو", "همهٔ نقش‌ها"), if (showAll) 1 else 0) { i ->
                showAll = i == 1
                updateRoleGrid(true)
            }, kit.spaced(8))
        }
        selectedBox = FrameLayout(host)
        parent.addView(selectedBox, kit.spaced(8))
        renderSelected()
        parent.addView(kit.searchBox("جست‌وجوی نقش (همهٔ سایدها)", roleQuery) { q ->
            roleQuery = q
            updateRoleGrid(true)
        }, kit.spaced(8))
        emptyRoles = kit.text("", TextStyle.LABEL, Royal.muted, Gravity.CENTER)
        parent.addView(emptyRoles, kit.spaced(8))
        parent.addView(kit.button("ساخت نقش جدید", ButtonKind.GHOST, RoyalIcon.PLUS, 40) { host.push(MafiaRoleEditScreen(host, null)) }, kit.spaced(8))
        roleGrid = CardGrid(host, 3, kit.dp(6))
        parent.addView(roleGrid, kit.fill())
        updateRoleGrid(false)
        prebuildRoles()
    }

    /**
     * Everything a tab or filter switch can show is prepared while the app is idle: one cell per role and its
     * card art at the grid's card size. A later switch then only re-arranges views that already exist.
     */
    private fun prebuildRoles() {
        val grid = roleGrid ?: return
        val order = MafiaRoleRegistry.available().sortedBy { if (it.side == side) 0 else 1 }
        val it = order.iterator()
        fun step(): Boolean {
            if (roleGrid !== grid || !grid.isAttachedToWindow) return false
            if (!it.hasNext()) return false
            val r = it.next()
            val fresh = !roleCells.containsKey(r)
            val cell = roleCell(r)
            val sample = grid.keys.firstOrNull()?.let { k -> (grid.viewFor(k) as? RoleCell)?.card }
            val warmed = sample != null && sample.width > 0 && cell.card.warmAt(sample.width, sample.height)
            ArtWarmer.post { step() }
            return fresh || warmed
        }
        ArtWarmer.post { step() }
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
        actions += DialogAction("ویرایش قوانین نقش") { host.push(MafiaRoleEditScreen(host, role)) }
        actions += DialogAction("لغو")
        kit.dialog(role.faName, null, body, actions).show()
    }

    /** Saves the current roles as this scenario's reference line-up for this many players, and the cards as its deck. */
    private fun saveToScenario(sc: MafiaScenario) {
        val n = draft.players.size
        val roles = LinkedHashMap(draft.roleCounts.filter { it.value > 0 })
        if (roles.isEmpty()) { kit.toast("هنوز نقشی انتخاب نشده است"); return }
        val count = if (n > 0) n else roles.values.sum()
        kit.confirm("ذخیره در سناریو", "این نقش‌ها به عنوان ترکیب مرجع ${kit.n(count)} نفره و کارت‌های فعلی به عنوان کارت‌های مرجع «${sc.name}» ذخیره شود؟", "ذخیره") {
            host.mafia.saveScenario(sc.copy(presets = sc.presets + (count to roles), pool = (sc.pool + roles.keys).distinct(),
                cards = draft.deck.map { it.card }.ifEmpty { sc.cards }))
            kit.toast("در سناریو ذخیره شد")
            host.refresh()
        }
    }

    // ------------------------------------------------------------------ cards

    private fun cardCell(c: MafiaLastCard): CardCell = cardCells.getOrPut(c) { CardCell(c) }.also { it.bind() }

    /** A last-move card with its selection frame; tapping toggles it in the deck in place. */
    private inner class CardCell(val card: MafiaLastCard) : FrameLayout(host) {
        private val view = lastCard(card)
        private var badgeView: TextView? = null
        private val badge: TextView get() = badgeView ?: kit.badge("✓", Royal.turquoise, true).also {
            badgeView = it
            addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT).apply { topMargin = kit.dp(4); leftMargin = kit.dp(4) })
        }
        private var state: Boolean? = null

        init {
            setPadding(kit.dp(4), kit.dp(4), kit.dp(4), kit.dp(5))
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            isClickable = true
            contentDescription = card.faName
            setOnClickListener {
                kit.tap(it)
                if (draft.deck.any { s -> s.card == card }) draft.deck.removeAll { s -> s.card == card }
                else if (!MafiaCardRules.usable(card)) { Gate.lockedItem(this@MafiaDesignScreen, "کارت " + card.faName); return@setOnClickListener }
                else draft.deck += MafiaCardState(card)
                save()
            }
            if (!MafiaCardRules.usable(card)) addView(Gate.badge(this@MafiaDesignScreen), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.RIGHT).apply { topMargin = kit.dp(4); rightMargin = kit.dp(4) })
            setOnLongClickListener { cardInfo(card); true }
        }

        fun bind() {
            val on = draft.deck.any { it.card == card }
            if (on == state) return
            val was = state
            state = on
            background = PanelDrawable(kit.density, if (on) PanelStyle.SELECTED else PanelStyle.GHOST, 14f)
            view.alpha = if (on) 1f else 0.75f
            if (on) badge.visibility = View.VISIBLE else badgeView?.visibility = View.GONE
            if (was != null && on && kit.settings.general.animations) kit.pulse(badge)
        }
    }

    private fun updateCardGrid(animate: Boolean) {
        val grid = cardGrid ?: return
        val shown = MafiaLastCard.values().filter { SearchText.matches(cardQuery, it.faName, it.tagline) }
        grid.submit(shown.map { it.name }, animate) { key -> cardCell(MafiaLastCard.valueOf(key)) }
    }

    private fun cardsTab(parent: LinearLayout) {
        if (!ProState.feature("mafia_last_cards").second) {
            parent.addView(kit.panel(PanelStyle.RAISED).apply {
                addView(kit.text("«کارت حرکت آخر» از قابلیت‌های Pro است؛ بازی بدون این کارت‌ها انجام می‌شود.", TextStyle.BODY, Royal.ivory))
                addView(kit.gap(8))
                addView(kit.button("دیوان Pro", ButtonKind.PRIMARY, RoyalIcon.CROWN, 44) { host.push(ProScreen(host)) })
            }, kit.spaced(8))
            return
        }
        parent.addView(kit.horizontal().apply {
            layoutParams = kit.spaced(8)
            addView(kit.weight(kit.button("انتخاب همه", ButtonKind.SECONDARY, RoyalIcon.CHECK, 42) {
                draft.deck.clear()
                MafiaLastCard.values().filter { c -> (c.requires == null || (draft.roleCounts[c.requires] ?: 0) > 0) && MafiaCardRules.usable(c) }.forEach { draft.deck += MafiaCardState(it) }
                save()
            }))
            addView(kit.hgap(6))
            addView(kit.weight(kit.button("حذف همه", ButtonKind.GHOST, RoyalIcon.CLOSE, 42) { draft.deck.clear(); save() }))
        })
        scenario?.takeIf { it.cards.isNotEmpty() }?.let { sc ->
            parent.addView(kit.button("کارت‌های مرجع سناریو", ButtonKind.SECONDARY, RoyalIcon.CARDS, 42) {
                draft.deck.clear()
                val roles = draft.roleCounts.filter { it.value > 0 }.keys
                (if (roles.isEmpty()) sc.cards else MafiaScenarios.matchMind(sc.cards, roles)).filter { MafiaCardRules.usable(it) }.forEach { draft.deck += MafiaCardState(it) }
                save()
            }, kit.spaced(8))
        }
        parent.addView(kit.searchBox("جست‌وجوی کارت حرکت آخر", cardQuery) { q -> cardQuery = q; updateCardGrid(true) }, kit.spaced(8))
        cardGrid = CardGrid(host, 3, kit.dp(6))
        parent.addView(cardGrid, kit.fill())
        updateCardGrid(false)
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
class MafiaDistributeScreen(host: MainActivity) : MafiaSectionScreen(host) {
    /** Re-read on every build: the reveal screen saves «دیدم» into the stored draft. */
    private var draft: MafiaGame = host.mafia.draft()
    private var showAll = false

    private fun save() { host.mafia.saveDraft(draft); host.refresh() }

    override fun build(): View {
        draft = host.mafia.draft()
        val allSeen = draft.players.isNotEmpty() && draft.players.all { it.role != null && it.seen }
        return scaffold("توزیع نقش", "${kit.n(draft.players.count { it.seen })} از ${kit.n(draft.players.size)} نفر نقش خود را دیده‌اند",
            bottom = kit.horizontal().apply {
                if (!allSeen) {
                    addView(kit.button("همه دیدند", ButtonKind.SECONDARY, RoyalIcon.CHECK, 52) {
                        if (draft.players.any { it.role == null }) MafiaNight.distribute(draft)
                        draft.players.forEach { it.seen = true }
                        save()
                    }, LinearLayout.LayoutParams(kit.dp(130), ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(kit.hgap(8))
                }
                addView(kit.weight(kit.button("شروع", if (allSeen) ButtonKind.SUCCESS else ButtonKind.PRIMARY, RoyalIcon.PLAY, 52) {
                    if (allSeen) start()
                    else kit.confirm("شروع به تشخیص راوی", "همهٔ بازیکنان نقش خود را ندیده‌اند" +
                        (if (draft.players.any { it.role == null }) "\nنقش بازیکنان بدون نقش خودکار پخش می‌شود" else "") + "\nبازی شروع شود؟", "شروع") {
                        if (draft.players.any { it.role == null }) MafiaNight.distribute(draft)
                        draft.players.forEach { it.seen = true }
                        host.mafia.saveDraft(draft)
                        start()
                    }
                }))
            }) {
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
        draft.deck.filter { MafiaCardRules.usable(it.card) }.forEach { g.deck += MafiaCardState(it.card) }
        MafiaScenarios.KEYS.forEach { k -> draft.extra[k]?.let { g.extra[k] = it } }
        MafiaFlow.start(g, mafiaSettings)
        host.mafia.save(g)
        draft.players.forEach { it.role = null; it.seen = false }
        host.mafia.saveDraft(draft)
        host.resetTo(HomeScreen(host), MafiaHomeScreen(host), MafiaGameScreen(host, g.id))
    }
}

/** One player's private look at his role card («دیدم»). */
class MafiaRevealScreen(host: MainActivity, private val playerId: Int) : MafiaSectionScreen(host) {
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
