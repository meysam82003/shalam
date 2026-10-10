package com.meysam.divanemtiaz

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Timer bookkeeping shared by the three live boards. */
abstract class BoardScreen(host: MainActivity, val session: GameSession) : Screen(host) {
    override val sessionId: Long get() = session.id
    private var shownAt = 0L
    private var active = false
    protected var timerLabel: TextView? = null

    override fun onShow() {
        if (active) return
        active = true
        shownAt = System.currentTimeMillis()
        tick()
    }

    private fun tick() {
        host.post(1000) {
            if (active) {
                timerLabel?.text = timerText(session, System.currentTimeMillis() - shownAt)
                tick()
            }
        }
    }

    override fun onHide() {
        if (!active) return
        active = false
        host.cancelPosts()
        session.elapsedMs += System.currentTimeMillis() - shownAt
        if (session.rounds.isNotEmpty()) host.repo.save(session)
    }

    /** Called after any change; saves and moves to the result page when the game ended. */
    protected fun afterChange() {
        val ended = SessionOps.commitAndCheck(host, session)
        if (ended) host.replace(ResultScreen(host, session)) else host.refresh()
    }

    protected fun statusRow(extra: String): View = kit.horizontal(Gravity.CENTER).apply {
        layoutParams = kit.spaced(8)
        addView(kit.icon(RoyalIcon.TIMER, Royal.gold, 16))
        addView(kit.hgap(4))
        timerLabel = kit.text(timerText(session), TextStyle.LABEL_BOLD, Royal.goldLight, Gravity.CENTER)
        addView(timerLabel)
        addView(kit.text("   •   $extra", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
    }

    protected fun menuButton(items: List<Pair<String, () -> Unit>>): View =
        kit.iconButton(RoyalIcon.MENU, "گزینه‌های بازی", ButtonKind.SECONDARY, 42) {
            lateinit var dialog: android.app.Dialog
            val list = kit.vertical().apply {
                items.forEach { (label, action) ->
                    addView(kit.button(label, ButtonKind.SECONDARY, height = 48) {
                        dialog.dismiss()
                        action()
                    }, kit.spaced(8))
                }
            }
            dialog = kit.dialog("گزینه‌های بازی", null, list, listOf(DialogAction("بستن")))
            dialog.show()
        }

    protected fun commonMenu(): List<Pair<String, () -> Unit>> = listOf(
        "نمودار بازی" to { gameChartDialog(session) },
        "اشتراک تصویر وضعیت بازی" to { shareSession(session) },
        "ثبت تقلب / جریمه" to { penaltyDialog(session) { afterChange() } },
        "ویرایش جمع امتیازها" to { adjustTotalsDialog(session) { afterChange() } },
        "ویرایش نام و نشان‌ها" to { editSidesDialog(session) { host.refresh() } },
        "قوانین این بازی" to { host.push(SessionRulesScreen(host, session)) },
        "پایان بازی و ثبت نتیجه" to {
            if (session.rounds.isEmpty()) kit.toast("حداقل یک دست ثبت کنید")
            else kit.confirm("پایان بازی", "بازی پایان‌یافته ثبت شود؟", "پایان بازی") {
                session.finished = true
                session.endedAt = System.currentTimeMillis()
                SessionOps.commit(host, session)
                host.replace(ResultScreen(host, session))
            }
        }
    )

    protected fun editScoresDialog(index: Int) {
        val round = session.rounds[index]
        manualScoresDialog("ویرایش دست ${kit.n(index + 1)}", session.sides.map { it.name }, session.sides.indices.map { round.score(it) }) { values ->
            session.rounds[index] = when (round.kind) {
                RoundKind.PENALTY, RoundKind.ADJUST -> round.copy(raw = values, scores = values)
                else -> round.copy(kind = RoundKind.FIXED, scores = values, note = round.note.ifBlank { "ویرایش‌شده" })
            }
            afterChange()
        }
    }
}

class ShalamBoardScreen(host: MainActivity, session: GameSession) : BoardScreen(host, session) {
    private val rules get() = session.rules.shalam

    override fun build(): View {
        val totals = GameEngine.totals(session)
        val leader = GameEngine.winners(session).singleOrNull()
        return scaffold(
            title = "داوری شلم",
            subtitle = sessionSubtitle(session),
            actions = listOf(menuButton(listOf("آمار بازی" to { shalamStatsDialog(session) }) + commonMenu())),
            bottom = kit.horizontal().apply {
                addView(kit.weight(kit.button("ثبت دست جدید", ButtonKind.PRIMARY, RoyalIcon.PLUS) {
                    host.push(ShalamHandScreen(host, session, null))
                }))
                addView(kit.hgap(8))
                addView(kit.iconButton(RoyalIcon.UNDO, "حذف دست آخر", ButtonKind.SECONDARY, 52) {
                    if (session.rounds.isEmpty()) kit.toast("هنوز دستی ثبت نشده است")
                    else kit.confirm("حذف دست آخر", "آیا از حذف دست آخر بازی مطمئن هستید؟", "حذف", true) {
                        session.rounds.removeAt(session.rounds.lastIndex)
                        afterChange()
                    }
                })
            }
        ) {
            addView(kit.horizontal(Gravity.TOP).apply {
                layoutParams = kit.spaced(8)
                session.sides.forEachIndexed { i, side ->
                    if (i > 0) addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                        setPadding(0, kit.dp(24), 0, 0)
                        addView(kit.text("اختلاف", TextStyle.CAPTION, Royal.dim, Gravity.CENTER, 1))
                        addView(kit.text(kit.n(abs(totals[0] - totals[1])), TextStyle.HEADING, Royal.goldLight, Gravity.CENTER, 1))
                        addView(kit.icon(RoyalIcon.STAR, Royal.alpha(Royal.gold, 0.7f), 14))
                    }, LinearLayout.LayoutParams(kit.dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
                    val fraction = if (rules.endPoint > 0) totals[i].toFloat() / rules.endPoint else null
                    addView(kit.weight(sideScoreCard(side, kit.signed(totals[i]), "پایان: ${kit.n(rules.endPoint)}", leader == i, fraction)))
                }
            })
            addView(statusRow("${kit.n(GameEngine.playedHands(session))} دست"))
            if (rules.highLimitEnabled) {
                val above = session.sides.indices.filter { totals[it] >= rules.highLimit }
                if (above.isNotEmpty()) addView(kit.badge("بالای ${kit.n(rules.highLimit)}: ${above.joinToString("، ") { session.sides[it].name }} فقط با بردن حکم امتیاز می‌گیرد", Royal.crimsonLight).apply {
                    layoutParams = kit.spaced(8)
                })
            }
            addView(kit.section("دست‌های ثبت‌شده", RoyalIcon.HISTORY))
            if (session.rounds.isEmpty()) {
                addView(emptyState(RoyalIcon.SPADE, "هنوز دستی ثبت نشده", "با «ثبت دست جدید» تیم حاکم و امتیاز خوانده‌شده را وارد کنید."))
            }
            session.rounds.indices.reversed().forEach { index ->
                val round = session.rounds[index]
                addView(roundRow(index, session.sides.indices.map { kit.signed(round.score(it)) }, session.sides.indices.map { scoreColor(round.score(it)) }, describe(round)) {
                    roundActions("دست ${kit.n(index + 1)}", editAction(index), share = { shareRound(session, index) }) {
                        session.rounds.removeAt(index)
                        afterChange()
                    }
                })
            }
        }
    }

    private fun editAction(index: Int): (() -> Unit)? {
        val round = session.rounds[index]
        return when {
            round.kind.isShalamContract -> { { host.push(ShalamHandScreen(host, session, index)) } }
            round.kind == RoundKind.SHALAM_PASS -> null
            else -> { { editScoresDialog(index) } }
        }
    }

    private fun describe(round: Round): String {
        val team = session.sides.getOrNull(round.contractTeam)?.name ?: ""
        val suit = if (round.suit != Suit.NONE) " ${Suit.title(round.suit)}" else ""
        return when (round.kind) {
            RoundKind.SHALAM_HAND -> {
                val o = ShalamEngine.outcome(round, rules)
                val result = when (o.result) {
                    ShalamResult.SUCCESS -> if (o.collectedAll) "همهٔ امتیازها" else "موفق"
                    ShalamResult.DOUBLE_POSITIVE -> "دوبل مثبت"
                    ShalamResult.DOUBLE_NEGATIVE -> "دوبل منفی"
                    ShalamResult.SHELEM_WIN -> "همهٔ امتیازها = شلم"
                    else -> "ناموفق"
                }
                "حاکم: $team • تعهد ${kit.n(round.bid)}$suit • حریف ${kit.n(round.taken)} • $result"
            }
            RoundKind.SHALAM_SHELEM, RoundKind.SHALAM_DOUBLE_SHELEM -> {
                val o = ShalamEngine.outcome(round, rules)
                val name = if (round.kind == RoundKind.SHALAM_SHELEM) "شلم" else "شلم دوبل"
                "حاکم: $team • $name$suit • ${if (o.succeeded) "برد" else "باخت (حریف ${kit.n(round.taken)})"}"
            }
            RoundKind.SHALAM_PASS -> "پاس"
            else -> round.note
        }
    }
}

fun Screen.shalamStatsDialog(session: GameSession) {
    val stats = GameEngine.shalamStats(session)
    val body = kit.vertical()
    session.sides.forEachIndexed { i, side ->
        val t = stats.teams.getOrElse(i) { ShalamTeamStats() }
        body.addView(kit.panel(PanelStyle.FLAT, 12).apply {
            layoutParams = kit.spaced(8)
            addView(kit.horizontal().apply {
                addView(kit.avatar(side.avatar, 34))
                addView(kit.hgap(8))
                addView(kit.text(side.name, TextStyle.BODY_BOLD, Royal.goldLight))
            })
            listOf(
                "تعداد دست‌هایی که حاکم بوده" to t.contracts,
                "دست‌های پیروز" to t.wins,
                "دست‌هایی که منفی خورده" to t.fails,
                "دست‌هایی که شلم خوانده" to t.shelemCalls,
                "شلم‌های موفق" to t.shelemWins,
                "دوبل شده" to t.doubled,
                "گرفتن همهٔ امتیازها" to t.collectedAll
            ).forEach { (label, value) ->
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.text(label, TextStyle.LABEL, Royal.muted)))
                    addView(kit.text(kit.n(value), TextStyle.LABEL_BOLD, Royal.ivory))
                })
            }
        })
    }
    body.addView(kit.panel(PanelStyle.FLAT, 12).apply {
        listOf(
            "تعداد دست‌ها" to kit.n(stats.hands),
            "پاس" to kit.n(stats.passes),
            "بیشینهٔ اختلاف" to if (stats.maxLeadTeam >= 0) "${kit.n(stats.maxLead)} (${session.sides[stats.maxLeadTeam].name})" else kit.n(0),
            "مدت زمان" to timerText(session),
            "خال‌ها" to Suit.all.filter { (stats.suits[it] ?: 0) > 0 }.joinToString("، ") { "${Suit.title(it)} ${kit.n(stats.suits[it] ?: 0)}" }.ifBlank { "—" }
        ).forEach { (label, value) ->
            addView(kit.horizontal().apply {
                addView(kit.weight(kit.text(label, TextStyle.LABEL, Royal.muted)))
                addView(kit.text(value, TextStyle.LABEL_BOLD, Royal.ivory))
            })
        }
    })
    kit.dialog("آمار بازی", null, body, listOf(DialogAction("بستن"))).show()
}

/** Two-step hand entry: contractor + bid, then opponent points with a live result preview. */
class ShalamHandScreen(host: MainActivity, private val session: GameSession, private val editIndex: Int?) : Screen(host) {
    private val rules get() = session.rules.shalam
    private val mode get() = rules.mode
    private val editing: Round? = editIndex?.let { session.rounds.getOrNull(it) }
    private var step = 1
    private var team = editing?.contractTeam ?: -1
    private var kind = editing?.kind?.takeIf { it.isShalamContract } ?: RoundKind.SHALAM_HAND
    private var bid: Int? = editing?.bid?.takeIf { it > 0 }
    private var suit = editing?.suit ?: Suit.NONE
    private var taken: Int? = editing?.taken
    private var bidText = editing?.bid?.takeIf { it > 0 }?.toString() ?: ""
    private var takenText = editing?.taken?.toString() ?: ""
    private var preview: LinearLayout? = null
    private var footer: FrameLayout? = null
    private val contractUpdates = mutableListOf<() -> Unit>()

    override val sessionId: Long get() = session.id

    override fun onBack(): Boolean {
        if (step == 2) {
            step = 1
            host.refresh()
            return true
        }
        return false
    }

    override fun build(): View {
        contractUpdates.clear()
        return if (step == 1) buildContract() else buildResult()
    }

    private fun updateContractSelection() = contractUpdates.forEach { it() }

    private fun contractChip(label: String, selectedKind: ButtonKind, selected: () -> Boolean, choose: () -> Unit): View {
        val chip = kit.chip(label, selected(), selectedKind) {
            choose()
            updateContractSelection()
        }
        contractUpdates += { kit.styleChip(chip, selected(), selectedKind) }
        return chip
    }

    private fun buildContract(): View = scaffold(
        title = if (editing != null) "ویرایش دست ${kit.n(editIndex!! + 1)}" else "دست ${kit.n(session.rounds.size + 1)} — تعهد",
        subtitle = "مرحلهٔ ۱ از ۲: تیم حاکم و امتیاز خوانده‌شده",
        bottom = kit.button("مرحلهٔ بعد: امتیاز حریف", ButtonKind.PRIMARY, RoyalIcon.NEXT) { toStepTwo() }
    ) {
        addView(kit.section("تیم حاکم", RoyalIcon.CROWN))
        addView(kit.horizontal().apply {
            layoutParams = kit.spaced(8)
            session.sides.forEachIndexed { i, side ->
                if (i > 0) addView(kit.hgap(10))
                addView(kit.weight(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                    background = PanelDrawable(kit.density, if (team == i) PanelStyle.SELECTED else PanelStyle.NORMAL, 18f)
                    setPadding(kit.dp(8), kit.dp(8), kit.dp(8), kit.dp(11))
                    isSelected = team == i
                    val avatar = kit.avatar(side.avatar, 44).apply { selectedRing = team == i }
                    val name = kit.text(side.name, TextStyle.BODY_BOLD, if (team == i) Royal.turquoiseLight else Royal.ivory, Gravity.CENTER)
                    val badge = kit.badge("حاکم", Royal.turquoise, true).apply {
                        visibility = if (team == i) View.VISIBLE else View.INVISIBLE
                    }
                    addView(avatar)
                    addView(name)
                    addView(badge)
                    contractUpdates += update@{
                        if (isSelected == (team == i)) return@update
                        isSelected = team == i
                        background = PanelDrawable(kit.density, if (team == i) PanelStyle.SELECTED else PanelStyle.NORMAL, 18f)
                        setPadding(kit.dp(8), kit.dp(8), kit.dp(8), kit.dp(11))
                        name.setTextColor(if (team == i) Royal.turquoiseLight else Royal.ivory)
                        badge.visibility = if (team == i) View.VISIBLE else View.INVISIBLE
                        avatar.selectedRing = team == i
                    }
                    isClickable = true
                    contentDescription = "حاکم ${side.name}"
                    setOnClickListener { kit.tap(it); team = i; updateContractSelection() }
                }))
            }
        })
        addView(kit.section("امتیاز خوانده‌شده", RoyalIcon.STAR))
        if (settings.shalam.keyboardInput) {
            val field = kit.field("مثلاً ۱۳۵ (یا فقط ۳۵)", bidText, numeric = true).apply {
                addTextChangedListener(watcher { text ->
                    bidText = text
                    kind = RoundKind.SHALAM_HAND
                    bid = PersianText.parseInt(text)?.let { ShalamEngine.normalizeTypedBid(it, mode) }
                    updateContractSelection()
                })
            }
            addView(field, kit.spaced(6))
            addView(kit.text("عدد کمتر از ۱۰۰ به‌صورت ۱xx ثبت می‌شود؛ مضرب ۵ از ${kit.n(ShalamEngine.MIN_TYPED_BID)} تا ${kit.n(mode.maxPoints)}.", TextStyle.CAPTION, Royal.muted))
        } else {
            addView(kit.grid(5, ShalamEngine.bidOptions(mode).map { value ->
                val label = if (value == mode.maxPoints && mode.maxBidIsShelem) "شلم" else kit.n(value)
                contractChip(label, ButtonKind.CHIP_GOLD, { kind == RoundKind.SHALAM_HAND && bid == value }) {
                    kind = RoundKind.SHALAM_HAND
                    bid = value
                }
            }, 6), kit.spaced(8))
        }
        addView(kit.gap(6))
        addView(kit.grid(4, listOf(
            contractChip("شلم", ButtonKind.CHIP_SELECTED, { kind == RoundKind.SHALAM_SHELEM }) { kind = RoundKind.SHALAM_SHELEM },
            contractChip("شلم دوبل", ButtonKind.CHIP_SELECTED, { kind == RoundKind.SHALAM_DOUBLE_SHELEM }) { kind = RoundKind.SHALAM_DOUBLE_SHELEM },
            kit.chip("پاس", false) { recordPass() },
            kit.chip("ثبت دستی", false) { recordManual() }
        ), 6))
        addView(kit.section("خال حکم (اختیاری)", RoyalIcon.SPADE))
        addView(kit.grid(5, (listOf(Suit.NONE) + Suit.all).map { s ->
            if (s == Suit.NONE) contractChip("بدون", ButtonKind.CHIP_SELECTED, { suit == s }) { suit = s }
            else suitChip(s)
        }, 6))
    }

    private fun suitChip(s: Int): View = FrameLayout(host).apply {
        background = ButtonDrawable(kit.density, if (suit == s) ButtonKind.CHIP_SELECTED else ButtonKind.CHIP, 12f)
        minimumHeight = kit.dp(44)
        isClickable = true
        contentDescription = Suit.title(s)
        isSelected = suit == s
        isFocusable = true
        val icon = IconView(host, RoyalIcons.forSuit(s)!!, if (suit == s) Royal.night else RoyalIcons.suitColor(s))
        addView(icon, FrameLayout.LayoutParams(kit.dp(22), kit.dp(22), Gravity.CENTER))
        contractUpdates += update@{
            if (isSelected == (suit == s)) return@update
            isSelected = suit == s
            background = ButtonDrawable(kit.density, if (suit == s) ButtonKind.CHIP_SELECTED else ButtonKind.CHIP, 12f)
            icon.color = if (suit == s) Royal.night else RoyalIcons.suitColor(s)
            icon.invalidate()
        }
        setOnClickListener { kit.tap(it); suit = s; updateContractSelection() }
    }

    private fun toStepTwo() {
        if (team !in 0..1) {
            kit.toast("لطفا تیم حاکم را انتخاب کنید")
            return
        }
        if (kind == RoundKind.SHALAM_HAND) {
            val value = bid
            if (value == null || !ShalamEngine.isValidBid(value, mode)) {
                kit.toast("امتیاز وارد شده نامعتبر است")
                return
            }
            if (mode.maxBidIsShelem && value == mode.maxPoints) kind = RoundKind.SHALAM_SHELEM
        }
        step = 2
        host.refresh()
    }

    /** Referee types both teams' points with + / − instead of the contract calculation. */
    private fun recordManual() {
        val initial = editing?.takeIf { it.kind == RoundKind.FIXED }?.let { r -> session.sides.indices.map { r.score(it) } } ?: listOf(0, 0)
        manualScoresDialog("ثبت دستی امتیاز دست", session.sides.map { it.name }, initial, "امتیاز هر تیم را با علامت + یا − وارد کنید.") { values ->
            val round = Round(RoundKind.FIXED, values, note = "ثبت دستی داور", contractTeam = team, suit = suit)
            if (editIndex != null) session.rounds[editIndex] = round else session.rounds += round
            finishEntry()
        }
    }

    private fun recordPass() {
        kit.confirm("ثبت پاس", "همه پاس دادند؛ این دست با امتیاز صفر ثبت شود؟", "ثبت پاس") {
            val round = Round(RoundKind.SHALAM_PASS, listOf(0, 0), note = "پاس")
            if (editIndex != null) session.rounds[editIndex] = round else session.rounds += round
            finishEntry()
        }
    }

    private fun buildResult(): View {
        val opponent = 1 - team
        val contractLabel = when (kind) {
            RoundKind.SHALAM_SHELEM -> "شلم"
            RoundKind.SHALAM_DOUBLE_SHELEM -> "شلم دوبل"
            else -> "تعهد ${kit.n(bid ?: 0)}"
        }
        val footerBox = FrameLayout(host)
        footer = footerBox
        return scaffold(
            title = contractLabel,
            subtitle = "مرحلهٔ ۲ از ۲: امتیاز گرفته‌شده توسط ${session.sides[opponent].name}",
            bottom = footerBox
        ) {
            addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 18f)
                setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(13))
                layoutParams = kit.spaced(8)
                addView(kit.avatar(session.sides[team].avatar, 44))
                addView(kit.hgap(10))
                addView(kit.vertical().apply {
                    addView(kit.text("حاکم: ${session.sides[team].name}", TextStyle.BODY_BOLD, Royal.goldLight))
                    val suitText = if (suit != Suit.NONE) " • خال ${Suit.title(suit)}" else ""
                    addView(kit.text("$contractLabel$suitText", TextStyle.LABEL, Royal.ivory))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(kit.button("تغییر", ButtonKind.GHOST, height = 40) { step = 1; host.refresh() })
            })
            addView(kit.section("امتیاز ${session.sides[opponent].name}", RoyalIcon.CHART))
            if (kind != RoundKind.SHALAM_HAND) {
                addView(kit.text("برای موفقیت شلم، حریف باید صفر امتیاز بگیرد.", TextStyle.CAPTION, Royal.muted))
            }
            if (settings.shalam.keyboardInput) {
                addView(kit.field("امتیاز حریف از ۰ تا ${kit.n(mode.maxPoints - 5)}", takenText, numeric = true).apply {
                    addTextChangedListener(watcher { text ->
                        takenText = text
                        taken = PersianText.parseInt(text)
                        updatePreview()
                    })
                }, kit.spaced(8))
            } else {
                val takenChips = mutableListOf<Pair<Int, TextView>>()
                addView(kit.grid(6, ShalamEngine.takenOptions(mode).map { value ->
                    kit.chip(kit.n(value), taken == value, ButtonKind.CHIP_GOLD) {
                        taken = value
                        takenText = value.toString()
                        takenChips.forEach { (v, chip) -> kit.styleChip(chip, taken == v, ButtonKind.CHIP_GOLD) }
                        updatePreview()
                    }.also { takenChips += value to it }
                }, 5), kit.spaced(8))
            }
            val box = kit.vertical()
            preview = box
            addView(box)
            updatePreview()
        }
    }

    private fun totalsBefore(): List<Int> {
        val end = editIndex ?: session.rounds.size
        return session.sides.indices.map { side -> session.rounds.take(end).sumOf { it.score(side) } }
    }

    private fun buildRound(double: Int): Round = Round(
        kind = kind,
        scores = emptyList(),
        contractTeam = team,
        bid = if (kind == RoundKind.SHALAM_HAND) bid ?: 0 else 0,
        taken = taken ?: 0,
        suit = suit,
        double = double
    )

    private fun updatePreview() {
        val box = preview ?: return
        val foot = footer ?: return
        box.removeAllViews()
        foot.removeAllViews()
        val value = taken
        if (value == null || !ShalamEngine.isValidTaken(value, mode)) {
            if (value != null) box.addView(kit.text("امتیاز وارد شده نامعتبر است", TextStyle.LABEL_BOLD, Royal.crimsonLight, Gravity.CENTER))
            foot.addView(kit.button("ثبت این دست", ButtonKind.PRIMARY, RoyalIcon.CHECK) {
                kit.toast("امتیاز حریف را وارد کنید")
            })
            return
        }
        val round = buildRound(DoubleChoice.AUTO)
        val outcome = if (kind == RoundKind.SHALAM_HAND) ShalamEngine.scoreHand(round.bid, value, rules)
        else ShalamEngine.scoreShelem(kind == RoundKind.SHALAM_DOUBLE_SHELEM, value, rules)
        val scores = ShalamEngine.scoreRound(round, rules, totalsBefore())
        val style = if (outcome.succeeded) PanelStyle.SUCCESS else PanelStyle.DANGER
        box.addView(kit.panel(style, 16).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            if (kind == RoundKind.SHALAM_HAND) {
                addView(kit.text("امتیاز واقعی حاکم: ${kit.n(mode.maxPoints)} − ${kit.n(value)} = ${kit.n(outcome.actual)}", TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER))
            }
            val resultText = when (outcome.result) {
                ShalamResult.SUCCESS -> if (outcome.collectedAll) "همهٔ امتیازها گرفته شد" else "تعهد انجام شد"
                ShalamResult.SHELEM_WIN -> if (outcome.collectedAll) "همهٔ امتیازها: شلم محاسبه شد" else "شلم موفق"
                ShalamResult.SHELEM_LOSE -> "شلم ناموفق"
                else -> if (outcome.isDouble) "دوبل! حریف به حد ${kit.n(mode.doubleLimit)} رسید" else "تعهد شکست خورد"
            }
            addView(kit.text(resultText, TextStyle.HEADING, if (outcome.succeeded) Royal.turquoiseLight else Royal.crimsonLight, Gravity.CENTER))
            addView(kit.gap(6))
            if (outcome.needsDoubleChoice) {
                val neg = ShalamEngine.scoreHand(round.bid, value, rules, DoubleChoice.NEGATIVE)
                val pos = ShalamEngine.scoreHand(round.bid, value, rules, DoubleChoice.POSITIVE)
                addView(kit.text("کدام نوع دوبل را انتخاب می‌کنید؟", TextStyle.LABEL_BOLD, Royal.goldLight, Gravity.CENTER))
                addView(kit.text("دوبل مثبت: حاکم ${kit.signed(pos.contractor)} • حریف ${kit.signed(pos.opponent)}", TextStyle.LABEL, Royal.ivory, Gravity.CENTER))
                addView(kit.text("دوبل منفی: حاکم ${kit.signed(neg.contractor)} • حریف ${kit.signed(neg.opponent)}", TextStyle.LABEL, Royal.ivory, Gravity.CENTER))
            } else {
                addView(kit.horizontal(Gravity.CENTER).apply {
                    session.sides.forEachIndexed { i, side ->
                        if (i > 0) addView(kit.text("   |   ", TextStyle.BODY, Royal.dim))
                        addView(kit.text("${side.name}: ${kit.signed(scores[i])}", TextStyle.BODY_BOLD, scoreColor(scores[i]), Gravity.CENTER))
                    }
                })
                if (scores[1 - team] != outcome.opponent) {
                    addView(kit.text("محدودیت امتیاز بالا: ${session.sides[1 - team].name} امتیاز این دست را نمی‌گیرد", TextStyle.CAPTION, Royal.goldLight, Gravity.CENTER))
                }
            }
        })
        if (outcome.needsDoubleChoice) {
            foot.addView(kit.horizontal().apply {
                addView(kit.weight(kit.button("دوبل مثبت", ButtonKind.SUCCESS) { save(DoubleChoice.POSITIVE) }))
                addView(kit.hgap(8))
                addView(kit.weight(kit.button("دوبل منفی", ButtonKind.DANGER) { save(DoubleChoice.NEGATIVE) }))
            })
        } else {
            foot.addView(kit.button(if (editing != null) "ذخیرهٔ تغییرات" else "ثبت این دست", ButtonKind.PRIMARY, RoyalIcon.CHECK) { save(DoubleChoice.AUTO) })
        }
    }

    private fun save(double: Int) {
        val value = taken
        if (value == null || !ShalamEngine.isValidTaken(value, mode)) {
            kit.toast("امتیاز وارد شده نامعتبر است")
            return
        }
        val round = buildRound(double)
        if (editIndex != null) session.rounds[editIndex] = round else session.rounds += round
        finishEntry()
    }

    private fun finishEntry() {
        val ended = SessionOps.commitAndCheck(host, session)
        host.pop()
        if (ended) host.replace(ResultScreen(host, session))
    }

    private fun watcher(onText: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onText(s?.toString().orEmpty())
    }
}
