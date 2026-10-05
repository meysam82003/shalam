package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

class MenfiBoardScreen(host: MainActivity, session: GameSession) : BoardScreen(host, session) {
    private val rules get() = session.rules.menfi
    private var revealed = false

    private val hiddenNow: Boolean get() = rules.hidden && !revealed

    override fun build(): View {
        val totals = GameEngine.totals(session)
        val played = GameEngine.playedHands(session)
        val leader = if (hiddenNow) null else GameEngine.winners(session).singleOrNull()
        val actions = mutableListOf<View>()
        if (rules.hidden) {
            actions += kit.iconButton(if (revealed) RoyalIcon.EYE_OFF else RoyalIcon.EYE, if (revealed) "پنهان کردن جمع" else "نمایش جمع", ButtonKind.SECONDARY, 42) {
                if (revealed) {
                    revealed = false
                    host.refresh()
                } else kit.confirm("نمایش جمع امتیازها", "جمع امتیازها و امتیاز هر دست برای همه نمایش داده شود؟", "نمایش") {
                    revealed = true
                    host.refresh()
                }
            }
        }
        actions += menuButton(commonMenu())
        return scaffold(
            title = "داوری منفی",
            subtitle = sessionSubtitle(session),
            actions = actions,
            bottom = kit.horizontal().apply {
                val tied = MenfiEngine.tiedAtEnd(played, totals, rules)
                addView(kit.weight(kit.button(if (tied) "تساوی: انتخاب دست اضافه" else "ثبت دست ${kit.n(played + 1)}", ButtonKind.PRIMARY, RoyalIcon.PLUS) {
                    if (tied) menfiTieDialog(session) { afterChange() } else host.push(MenfiHandScreen(host, session, null))
                }))
                addView(kit.hgap(8))
                addView(kit.iconButton(RoyalIcon.UNDO, "حذف دست آخر", ButtonKind.SECONDARY, 52) {
                    if (session.rounds.isEmpty()) kit.toast("هنوز دستی ثبت نشده است")
                    else kit.confirm("حذف دست آخر", "آیا از حذف دست آخر مطمئن هستید؟", "حذف", true) {
                        session.rounds.removeAt(session.rounds.lastIndex)
                        afterChange()
                    }
                })
            }
        ) {
            addView(kit.horizontal(Gravity.TOP).apply {
                layoutParams = kit.spaced(8)
                session.sides.forEachIndexed { i, side ->
                    if (i > 0) addView(kit.hgap(10))
                    val total = if (hiddenNow) "•••" else kit.signed(totals[i])
                    addView(kit.weight(sideScoreCard(side, total, if (hiddenNow) "جمع پنهان است" else null, leader == i, null)))
                }
            })
            addView(statusRow("دست ${kit.n(played)} از ${menfiHandsText(rules)}"))
            if (MenfiEngine.tiedAtEnd(played, totals, rules)) {
                addView(kit.panel(PanelStyle.RAISED, 12).apply {
                    layoutParams = kit.spaced(8)
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(kit.text("بازی مساوی شد", TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
                    addView(kit.text("جمع دو تیم برابر است؛ داور تعداد دست اضافه را انتخاب کند.", TextStyle.LABEL, Royal.ivory, Gravity.CENTER))
                    addView(kit.gap(6))
                    addView(kit.button("انتخاب دست اضافه", ButtonKind.PRIMARY, RoyalIcon.PLUS, 40) { menfiTieDialog(session) { afterChange() } })
                })
            }
            addView(kit.progress(played.toFloat() / rules.totalHands.coerceAtLeast(1), Royal.turquoise, 8).apply {
                layoutParams = kit.spaced(10).apply { height = kit.dp(8) }
            })
            addView(kit.text(if (rules.highWins) "برنده: بیشترین جمع امتیاز" else "برنده: کمترین جمع امتیاز", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
            addView(kit.section("دست‌های ثبت‌شده", RoyalIcon.HISTORY))
            if (session.rounds.isEmpty()) {
                addView(emptyState(RoyalIcon.EYE_OFF, "هنوز دستی ثبت نشده", "هر تیم عدد خود را از ۳ تا ۱۳ پنهانی ثبت می‌کند؛ سپس حالت رخ‌داده انتخاب می‌شود."))
            }
            session.rounds.indices.reversed().forEach { index ->
                val round = session.rounds[index]
                val scores = if (hiddenNow) session.sides.map { "•••" } else session.sides.indices.map { kit.signed(round.score(it)) }
                val colors = if (hiddenNow) session.sides.map { Royal.dim } else session.sides.indices.map { scoreColor(round.score(it)) }
                addView(roundRow(index, scores, colors, describe(round)) {
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
            round.kind == RoundKind.MENFI_HAND || (round.numbers.size == 2 && round.kind == RoundKind.FIXED) -> { { host.push(MenfiHandScreen(host, session, index)) } }
            else -> { { editScoresDialog(index) } }
        }
    }

    private fun describe(round: Round): String {
        if (hiddenNow) return if (round.kind == RoundKind.MENFI_HAND || round.numbers.isNotEmpty()) "ثبت شد  ✓" else round.note
        val parts = mutableListOf<String>()
        if (round.numbers.size == 2) parts += "اعداد: ${session.sides[0].name} ${kit.n(round.numbers[0])} • ${session.sides[1].name} ${kit.n(round.numbers[1])}"
        menfiOutcomeTitle(session, round.outcome).takeIf { it.isNotBlank() }?.let { parts += it }
        if (parts.isEmpty()) return round.note
        return parts.joinToString(" • ")
    }
}

fun menfiOutcomeTitle(session: GameSession, outcome: Int): String {
    val a = session.sides.getOrNull(0)?.name ?: "تیم اول"
    val b = session.sides.getOrNull(1)?.name ?: "تیم دوم"
    return when (outcome) {
        MenfiEngine.BOTH -> "هر دو تیم گرفتند"
        MenfiEngine.A_ONLY -> "$a گرفت؛ $b نگرفت"
        MenfiEngine.B_ONLY -> "$a نگرفت؛ $b گرفت"
        MenfiEngine.NONE -> "هیچ‌کدام نگرفتند؛ هر دو منفی"
        MenfiEngine.MANUAL -> "ثبت دستی داور"
        else -> ""
    }
}

class MenfiHandScreen(host: MainActivity, private val session: GameSession, private val editIndex: Int?) : Screen(host) {
    private val rules get() = session.rules.menfi
    private val editing = editIndex?.let { session.rounds.getOrNull(it) }
    private val numbers = arrayOf(editing?.numbers?.getOrNull(0), editing?.numbers?.getOrNull(1))
    private var picking = if (editing == null) 0 else -1
    private var outcome = editing?.outcome?.takeIf { it in 0..3 }
    private var manual: List<Int>? = if (editing?.outcome == MenfiEngine.MANUAL) editing.raw else null

    override val sessionId: Long get() = session.id

    override fun build(): View {
        val ready = numbers.all { it != null }
        return scaffold(
            title = if (editing != null) "ویرایش دست ${kit.n(editIndex!! + 1)}" else "دست ${kit.n(GameEngine.playedHands(session) + 1)} از ${menfiHandsText(rules)}",
            subtitle = if (rules.hidden) "عددها پنهانی ثبت می‌شوند" else "ثبت عدد هر دو تیم",
            bottom = kit.button(if (editing != null) "ذخیرهٔ تغییرات" else "ثبت این دست", ButtonKind.PRIMARY, RoyalIcon.CHECK) { save() }
        ) {
            session.sides.forEachIndexed { i, side -> addView(numberPanel(i, side)) }
            if (ready) {
                val a = numbers[0]!!
                val b = numbers[1]!!
                addView(kit.section("حالت رخ‌داده را انتخاب کنید", RoyalIcon.EYE))
                val sum = a + b
                val hint = when {
                    rules.scoring == MenfiScoring.LEGACY -> "روش امتیاز قدیمی این بازی"
                    sum <= MenfiScoring.TRICKS -> "جمع ${kit.n(sum)}: هر دو تیم می‌توانند بگیرند"
                    sum == MenfiScoring.TRICKS + 1 -> "جمع ${kit.n(sum)}: حتماً یک تیم منفی می‌شود"
                    else -> "جمع ${kit.n(sum)}: ممکن است هر دو تیم منفی شوند"
                }
                addView(kit.text(hint, TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(6))
                MenfiEngine.outcomes(a, b, rules).forEach { o ->
                    val title = menfiOutcomeTitle(session, o.index)
                    val selected = manual == null && outcome == o.index
                    addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                        background = PanelDrawable(kit.density, if (selected) PanelStyle.SELECTED else PanelStyle.NORMAL, 16f)
                        setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(11))
                        layoutParams = kit.spaced(6)
                        addView(kit.horizontal(Gravity.CENTER).apply {
                            addView(kit.text("${session.sides[0].name} ", TextStyle.LABEL, Royal.muted))
                            addView(kit.text(kit.signed(o.teamAScore), TextStyle.NUMBER_L, scoreColor(o.teamAScore), Gravity.CENTER))
                            addView(kit.text("   |   ", TextStyle.HEADING, Royal.dim))
                            addView(kit.text(kit.signed(o.teamBScore), TextStyle.NUMBER_L, scoreColor(o.teamBScore), Gravity.CENTER))
                            addView(kit.text(" ${session.sides[1].name}", TextStyle.LABEL, Royal.muted))
                        })
                        addView(kit.text(title, TextStyle.LABEL, if (selected) Royal.turquoiseLight else Royal.ivory, Gravity.CENTER))
                        isClickable = true
                        contentDescription = title
                        setOnClickListener { kit.tap(it); outcome = o.index; manual = null; host.refresh() }
                    })
                }
            }
            addView(kit.section("ثبت دستی داور", RoyalIcon.EDIT))
            val m = manual
            if (m != null) {
                addView(kit.panel(PanelStyle.SELECTED, 12).apply {
                    layoutParams = kit.spaced(6)
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(kit.text(session.sides.indices.joinToString("   |   ") { "${session.sides[it].name} ${kit.signed(m.getOrElse(it) { 0 })}" }, TextStyle.BODY_BOLD, Royal.turquoiseLight, Gravity.CENTER))
                    addView(kit.text("امتیاز این دست دستی ثبت می‌شود", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
                })
            }
            addView(kit.button(if (m == null) "ورود دستی امتیاز هر تیم (+ / −)" else "ویرایش امتیاز دستی", ButtonKind.SECONDARY, RoyalIcon.SLIDERS, 42) {
                val initial = m ?: outcome?.let { o -> if (ready) MenfiEngine.outcome(numbers[0]!!, numbers[1]!!, o, rules).let { listOf(it.teamAScore, it.teamBScore) } else null } ?: listOf(0, 0)
                manualScoresDialog("ثبت دستی امتیاز", session.sides.map { it.name }, initial, "داور امتیاز هر تیم را با علامت + یا − وارد می‌کند؛ عددها اختیاری‌اند.") { values ->
                    manual = values
                    host.refresh()
                }
            })
        }
    }

    private fun numberPanel(index: Int, side: Side): View = kit.panel(if (numbers[index] != null && picking != index) PanelStyle.SUCCESS else PanelStyle.NORMAL, 12).apply {
        layoutParams = kit.spaced(8)
        addView(kit.horizontal().apply {
            addView(kit.avatar(side.avatar, 34))
            addView(kit.hgap(8))
            addView(kit.weight(kit.text("عدد ${side.name}", TextStyle.BODY_BOLD, Royal.goldLight)))
            if (numbers[index] != null && picking != index) {
                addView(kit.button("تغییر", ButtonKind.GHOST, height = 34) {
                    picking = index
                    outcome = null
                    host.refresh()
                })
            }
        })
        if (picking == index || numbers[index] == null) {
            addView(kit.gap(6))
            addView(kit.grid(6, MenfiEngine.readyNumbers.map { value ->
                kit.chip(kit.n(value), !rules.hidden && numbers[index] == value, ButtonKind.CHIP_GOLD) {
                    numbers[index] = value
                    outcome = null
                    picking = numbers.indexOfFirst { it == null }
                    host.refresh()
                }
            }, 5))
            if (rules.hidden) addView(kit.text("عدد پس از انتخاب پنهان می‌شود.", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
        } else {
            val shown = if (rules.hidden) "ثبت شد  ✓" else "عدد: ${kit.n(numbers[index]!!)}"
            addView(kit.text(shown, TextStyle.BODY_BOLD, Royal.turquoiseLight, Gravity.CENTER))
        }
    }

    private fun save() {
        val a = numbers[0]
        val b = numbers[1]
        val m = manual
        val round = if (m != null) {
            Round(RoundKind.MENFI_HAND, emptyList(), numbers = if (a != null && b != null) listOf(a, b) else emptyList(), outcome = MenfiEngine.MANUAL, raw = m)
        } else {
            if (a == null || b == null) {
                kit.toast("ابتدا عدد هر دو تیم را ثبت کنید یا امتیاز را دستی وارد کنید")
                return
            }
            val chosen = outcome?.takeIf { it in MenfiEngine.possible(a, b, rules) }
            if (chosen == null) {
                kit.toast("حالت رخ‌داده را انتخاب کنید")
                return
            }
            Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(a, b), outcome = chosen)
        }
        if (editIndex != null) session.rounds[editIndex] = round else session.rounds += round
        val ended = SessionOps.commitAndCheck(host, session)
        host.pop()
        if (ended) host.replace(ResultScreen(host, session))
        else if (MenfiEngine.tiedAtEnd(GameEngine.playedHands(session), GameEngine.totals(session), rules)) {
            host.current?.let { board -> board.menfiTieDialog(session) { host.refresh() } }
        }
    }
}

fun Screen.menfiHandsText(rules: MenfiRules): String =
    if (rules.extraHands > 0) "${kit.n(rules.hands)} + ${kit.n(rules.extraHands)}" else kit.n(rules.hands)

/**
 * Last hand played and the totals are equal: the referee picks how many extra hands to play
 * (the default comes from the rules), or ends the game as a draw.
 */
fun Screen.menfiTieDialog(session: GameSession, onDone: () -> Unit) {
    val rules = session.rules.menfi
    fun extend(n: Int) {
        session.rules = session.rules.copy(menfi = rules.copy(extraHands = rules.extraHands + n.coerceIn(1, 40)))
        SessionOps.commit(host, session)
        kit.toast("${kit.n(n)} دست اضافه شد؛ بازی ${kit.n(session.rules.menfi.totalHands)} دستی شد")
        onDone()
    }
    val options = (listOf(rules.tieExtraHands.coerceIn(1, 40)) + listOf(2, 3)).distinct()
    val actions = options.map { n -> DialogAction("${kit.n(n)} دست اضافه", if (n == rules.tieExtraHands) ButtonKind.PRIMARY else ButtonKind.SECONDARY) { extend(n) } } +
        listOf(
            DialogAction("تعداد دیگر…") {
                kit.numberPrompt("تعداد دست اضافه", rules.tieExtraHands, false, "از ${kit.n(1)} تا ${kit.n(40)}") { extend(it) }
            },
            DialogAction("پایان بازی با تساوی", ButtonKind.GHOST) {
                session.finished = true
                session.endedAt = System.currentTimeMillis()
                SessionOps.commit(host, session)
                host.replace(ResultScreen(host, session))
            }
        )
    kit.dialog("بازی مساوی شد", "پس از ${kit.n(GameEngine.playedHands(session))} دست جمع دو تیم برابر است. چند دست دیگر بازی شود؟", null, actions).show()
}

class HezarBoardScreen(host: MainActivity, session: GameSession) : BoardScreen(host, session) {
    private val rules get() = session.rules.hezar

    override fun build(): View {
        val totals = GameEngine.totals(session)
        val ranking = GameEngine.ranking(session)
        val played = GameEngine.playedHands(session)
        val roundsText = if (rules.rounds > 0) "دور ${kit.n(played)} از ${kit.n(rules.rounds)}" else "دور ${kit.n(played)}"
        return scaffold(
            title = "داوری هزارتایی",
            subtitle = sessionSubtitle(session),
            actions = listOf(menuButton(listOf(
                "افزودن بازیکن" to { addPlayerDialog(session) { afterChange() } },
                "ماشین‌حساب ورق و پخش" to { host.push(DeckCalcScreen(host, GameType.HEZARTAII, session.sides.size)) }
            ) + commonMenu())),
            bottom = kit.horizontal().apply {
                addView(kit.weight(kit.button("ثبت دور ${kit.n(played + 1)}", ButtonKind.PRIMARY, RoyalIcon.PLUS) {
                    host.push(HezarRoundScreen(host, session, null))
                }))
                addView(kit.hgap(8))
                addView(kit.iconButton(RoyalIcon.UNDO, "حذف دور آخر", ButtonKind.SECONDARY, 52) {
                    if (session.rounds.isEmpty()) kit.toast("هنوز دوری ثبت نشده است")
                    else kit.confirm("حذف دور آخر", "آیا از حذف دور آخر مطمئن هستید؟", "حذف", true) {
                        session.rounds.removeAt(session.rounds.lastIndex)
                        afterChange()
                    }
                })
            }
        ) {
            addView(kit.section("رتبه‌بندی", RoyalIcon.TROPHY))
            ranking.forEachIndexed { place, side ->
                addView(rankRow(place, session.sides[side], totals[side], place == 0 && totals.distinct().size > 1))
            }
            addView(statusRow("$roundsText  •  هدف ${kit.n(rules.target)}"))
            addView(kit.section("دورهای ثبت‌شده", RoyalIcon.HISTORY))
            if (session.rounds.isEmpty()) {
                addView(emptyState(RoyalIcon.STAR, "هنوز دوری ثبت نشده", "امتیاز هر بازیکن را در هر دور وارد کنید؛ صفر با جریمهٔ ${kit.signed(rules.zeroPenalty)} ثبت می‌شود."))
            }
            session.rounds.indices.reversed().forEach { index ->
                val round = session.rounds[index]
                addView(kit.vertical().apply {
                    background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                    setPadding(kit.dp(10), kit.dp(9), kit.dp(10), kit.dp(12))
                    layoutParams = kit.spaced(6)
                    addView(kit.horizontal().apply {
                        addView(kit.weight(kit.text(if (round.kind == RoundKind.HEZAR_ROUND) "دور ${kit.n(index + 1)}" else round.note.ifBlank { "ردیف ${kit.n(index + 1)}" }, TextStyle.LABEL_BOLD, Royal.goldLight, maxLines = 1)))
                        addView(kit.icon(RoyalIcon.MENU, Royal.dim, 16))
                    })
                    addView(kit.gap(6))
                    addView(kit.flow(session.sides.mapIndexed { i, s ->
                        val penalty = round.kind == RoundKind.HEZAR_ROUND && round.raw.getOrNull(i) == 0
                        kit.badge("${s.name} ${kit.signed(round.score(i))}${if (penalty) " (صفر)" else ""}", scoreColor(round.score(i)))
                    }, 6))
                    isClickable = true
                    setOnClickListener {
                        kit.tap(it)
                        roundActions("دور ${kit.n(index + 1)}", if (round.kind == RoundKind.HEZAR_ROUND) {
                            { host.push(HezarRoundScreen(host, session, index)) }
                        } else {
                            { editScoresDialog(index) }
                        }, share = { shareRound(session, index) }) {
                            session.rounds.removeAt(index)
                            afterChange()
                        }
                    }
                })
            }
        }
    }

    private fun rankRow(place: Int, side: Side, total: Int, leader: Boolean): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, if (leader) PanelStyle.RAISED else PanelStyle.NORMAL, 16f)
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
        addView(kit.avatar(side.avatar, 36))
        addView(kit.hgap(8))
        addView(kit.vertical().apply {
            addView(kit.horizontal().apply {
                addView(kit.text(side.name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
                if (leader) {
                    addView(kit.hgap(6))
                    addView(kit.icon(RoyalIcon.CROWN, Royal.goldLight, 16))
                }
            })
            addView(kit.gap(3))
            addView(kit.progress(if (rules.target > 0) total.toFloat() / rules.target else 0f, if (leader) Royal.turquoise else Royal.gold, 5))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.hgap(8))
        addView(kit.text(kit.signed(total), TextStyle.HEADING, if (leader) Royal.goldLight else Royal.ivory, Gravity.CENTER))
    }
}

class HezarRoundScreen(host: MainActivity, private val session: GameSession, private val editIndex: Int?) : Screen(host) {
    private val rules get() = session.rules.hezar
    private val editing = editIndex?.let { session.rounds.getOrNull(it) }
    private val values = MutableList(session.sides.size) { editing?.raw?.getOrNull(it)?.let { v -> kotlin.math.abs(v).toString() } ?: "" }
    private val negative = MutableList(session.sides.size) { (editing?.raw?.getOrNull(it) ?: 0) < 0 }

    override val sessionId: Long get() = session.id

    override fun build(): View = scaffold(
        title = if (editing != null) "ویرایش دور ${kit.n(editIndex!! + 1)}" else "دور ${kit.n(GameEngine.playedHands(session) + 1)}",
        subtitle = "صفر با جریمهٔ ${kit.signed(rules.zeroPenalty)} ثبت می‌شود؛ با دکمهٔ ± منفی کنید",
        bottom = kit.button(if (editing != null) "ذخیرهٔ تغییرات" else "ثبت امتیاز این دور", ButtonKind.PRIMARY, RoyalIcon.CHECK) { save() }
    ) {
        session.sides.forEachIndexed { i, side ->
            val preview = kit.text(previewText(i), TextStyle.CAPTION, Royal.muted)
            val field = kit.field("امتیاز", values[i], numeric = true).apply {
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val text = s?.toString().orEmpty()
                        if (text.startsWith("-") || text.startsWith("−")) {
                            negative[i] = true
                            values[i] = text.drop(1)
                        } else values[i] = text
                        preview.text = previewText(i)
                    }
                })
            }
            val sign = kit.chip(if (negative[i]) "−" else "+", negative[i], ButtonKind.DANGER) {}
            sign.setOnClickListener {
                kit.tap(it)
                negative[i] = !negative[i]
                sign.text = if (negative[i]) "−" else "+"
                sign.background = ButtonDrawable(kit.density, if (negative[i]) ButtonKind.DANGER else ButtonKind.CHIP, 12f)
                sign.setTextColor(ButtonDrawable.textColor(if (negative[i]) ButtonKind.DANGER else ButtonKind.CHIP))
                preview.text = previewText(i)
            }
            addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
                setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
                layoutParams = kit.spaced(5)
                addView(kit.avatar(side.avatar, 32))
                addView(kit.hgap(8))
                addView(kit.vertical().apply {
                    addView(kit.text(side.name, TextStyle.LABEL_BOLD, Royal.goldLight, maxLines = 1))
                    addView(preview)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(kit.iconButton(RoyalIcon.CHART, "ماشین‌حساب کارت ${side.name}", ButtonKind.CHIP, 36) {
                    cardCalculator(side.name) { points ->
                        negative[i] = points < 0
                        values[i] = kotlin.math.abs(points).toString()
                        host.refresh()
                    }
                })
                addView(kit.hgap(4))
                addView(sign, LinearLayout.LayoutParams(kit.dp(38), kit.dp(38)))
                addView(kit.hgap(4))
                addView(field, LinearLayout.LayoutParams(kit.dp(86), ViewGroup.LayoutParams.WRAP_CONTENT))
            })
        }
    }

    private fun parsed(i: Int): Int? = PersianText.parseInt(values[i])?.let { kotlin.math.abs(it) }?.let { if (negative[i]) -it else it }

    private fun previewText(i: Int): String {
        val v = parsed(i) ?: return "منتظر امتیاز"
        val score = HezarEngine.roundScore(v, rules)
        return if (v == 0) "ثبت: ${kit.signed(score)} (جریمهٔ صفر)" else "ثبت: ${kit.signed(score)}"
    }

    private fun save() {
        val parsed = session.sides.indices.map { parsed(it) }
        if (parsed.any { it == null }) {
            kit.toast("امتیاز همهٔ بازیکنان را وارد کنید")
            return
        }
        val raw = parsed.map { it!! }
        val round = Round(RoundKind.HEZAR_ROUND, emptyList(), raw = raw)
        if (editIndex != null) session.rounds[editIndex] = round else session.rounds += round
        val ended = SessionOps.commitAndCheck(host, session)
        host.pop()
        if (ended) host.replace(ResultScreen(host, session))
    }
}

/** Counts cards by value (2–9, 10–K, ace, joker) and returns their points, positive or negative. */
fun Screen.cardCalculator(name: String, onResult: (Int) -> Unit) {
    val values = settings.hezar.cards
    val counts = intArrayOf(0, 0, 0, 0)
    var minus = false
    val total = kit.text("", TextStyle.NUMBER_L, Royal.goldLight, Gravity.CENTER)
    fun points() = CardCalc.points(CardCount(counts[0], counts[1], counts[2], counts[3]), values).let { if (minus) -it else it }
    fun update() { total.text = kit.signed(points()) }
    val labels = listOf(
        "۲ تا ۹ (هر کدام ${kit.n(values.low)})",
        "۱۰ تا شاه (هر کدام ${kit.n(values.high)})",
        "تک / آس (هر کدام ${kit.n(values.ace)})",
        "جوکر (هر کدام ${kit.n(values.joker)})"
    )
    val signBox = kit.vertical()
    fun drawSign() {
        signBox.removeAllViews()
        signBox.addView(kit.grid(2, listOf(
            kit.chip("امتیاز مثبت", !minus) { minus = false; drawSign(); update() },
            kit.chip("امتیاز منفی (کارت‌های مانده)", minus, ButtonKind.DANGER) { minus = true; drawSign(); update() }
        ), 6))
    }
    drawSign()
    val body = kit.vertical().apply {
        labels.forEachIndexed { i, label ->
            val count = kit.text(kit.n(0), TextStyle.HEADING, Royal.ivory, Gravity.CENTER)
            addView(kit.horizontal().apply {
                layoutParams = kit.spaced(6)
                addView(kit.weight(kit.text(label, TextStyle.LABEL, Royal.ivory)))
                addView(kit.iconButton(RoyalIcon.PLUS, "افزایش", ButtonKind.CHIP, 34) { counts[i]++; count.text = kit.n(counts[i]); update() })
                addView(count, LinearLayout.LayoutParams(kit.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(kit.iconButton(RoyalIcon.MINUS, "کاهش", ButtonKind.CHIP, 34) {
                    if (counts[i] > 0) counts[i]--
                    count.text = kit.n(counts[i])
                    update()
                })
            })
        }
        addView(signBox, kit.spaced(6))
        addView(total)
    }
    update()
    kit.dialog("شمارش کارت‌های $name", null, body, listOf(
        DialogAction("ثبت امتیاز", ButtonKind.PRIMARY) { onResult(points()) },
        DialogAction("انصراف")
    )).show()
}
