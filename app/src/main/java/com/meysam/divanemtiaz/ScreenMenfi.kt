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
                addView(kit.weight(kit.button("ثبت دست ${kit.n(played + 1)}", ButtonKind.PRIMARY, RoyalIcon.PLUS) {
                    host.push(MenfiHandScreen(host, session, null))
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
            addView(statusRow("دست ${kit.n(played)} از ${kit.n(rules.hands)}"))
            addView(kit.progress(played.toFloat() / rules.hands.coerceAtLeast(1), Royal.turquoise, 8).apply {
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
                    roundActions("دست ${kit.n(index + 1)}", editAction(index)) {
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
            round.numbers.size == 2 && (round.kind == RoundKind.MENFI_HAND || round.kind == RoundKind.FIXED) -> { { host.push(MenfiHandScreen(host, session, index)) } }
            else -> { { editScoresDialog(index) } }
        }
    }

    private fun describe(round: Round): String {
        if (hiddenNow) return if (round.kind == RoundKind.MENFI_HAND || round.numbers.isNotEmpty()) "ثبت شد  ✓" else round.note
        if (round.numbers.size == 2) {
            val title = outcomeTitle(round.outcome)
            return "اعداد: ${session.sides[0].name} ${kit.n(round.numbers[0])} • ${session.sides[1].name} ${kit.n(round.numbers[1])}" + if (title.isNotBlank()) " • $title" else ""
        }
        return round.note
    }

    private fun outcomeTitle(outcome: Int): String = when (outcome) {
        0 -> "هر دو تیم گرفتند"
        1 -> "${session.sides[0].name} گرفت؛ ${session.sides[1].name} نگرفت"
        2 -> "${session.sides[0].name} نگرفت؛ ${session.sides[1].name} گرفت"
        else -> ""
    }
}

class MenfiHandScreen(host: MainActivity, private val session: GameSession, private val editIndex: Int?) : Screen(host) {
    private val rules get() = session.rules.menfi
    private val editing = editIndex?.let { session.rounds.getOrNull(it) }
    private val numbers = arrayOf(editing?.numbers?.getOrNull(0), editing?.numbers?.getOrNull(1))
    private var picking = if (editing == null) 0 else -1
    private var outcome = editing?.outcome?.takeIf { it in 0..2 }

    override val sessionId: Long get() = session.id

    override fun build(): View {
        val ready = numbers.all { it != null }
        return scaffold(
            title = if (editing != null) "ویرایش دست ${kit.n(editIndex!! + 1)}" else "دست ${kit.n(GameEngine.playedHands(session) + 1)} از ${kit.n(rules.hands)}",
            subtitle = if (rules.hidden) "عددها پنهانی ثبت می‌شوند" else "ثبت عدد هر دو تیم",
            bottom = kit.button(if (editing != null) "ذخیرهٔ تغییرات" else "ثبت این دست", ButtonKind.PRIMARY, RoyalIcon.CHECK) { save() }
        ) {
            session.sides.forEachIndexed { i, side -> addView(numberPanel(i, side)) }
            if (ready) {
                addView(kit.section("حالت رخ‌داده را انتخاب کنید", RoyalIcon.EYE))
                addView(kit.text("${session.sides[0].name}: ${kit.n(numbers[0]!!)}   |   ${session.sides[1].name}: ${kit.n(numbers[1]!!)}", TextStyle.BODY_BOLD, Royal.goldLight, Gravity.CENTER))
                addView(kit.gap(6))
                MenfiEngine.outcomes(numbers[0]!!, numbers[1]!!, rules).forEachIndexed { index, o ->
                    val title = when (index) {
                        0 -> "هر دو تیم گرفتند"
                        1 -> "${session.sides[0].name} گرفت؛ ${session.sides[1].name} نگرفت"
                        else -> "${session.sides[0].name} نگرفت؛ ${session.sides[1].name} گرفت"
                    }
                    addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                        background = PanelDrawable(kit.density, if (outcome == index) PanelStyle.SELECTED else PanelStyle.NORMAL, 18f)
                        setPadding(kit.dp(12), kit.dp(12), kit.dp(12), kit.dp(15))
                        layoutParams = kit.spaced(8)
                        addView(kit.horizontal(Gravity.CENTER).apply {
                            addView(kit.text(kit.signed(o.teamAScore), TextStyle.NUMBER_L, scoreColor(o.teamAScore), Gravity.CENTER))
                            addView(kit.text("   |   ", TextStyle.HEADING, Royal.dim))
                            addView(kit.text(kit.signed(o.teamBScore), TextStyle.NUMBER_L, scoreColor(o.teamBScore), Gravity.CENTER))
                        })
                        addView(kit.text(title, TextStyle.LABEL, Royal.muted, Gravity.CENTER))
                        isClickable = true
                        contentDescription = title
                        setOnClickListener { kit.tap(it); outcome = index; host.refresh() }
                    })
                }
            }
        }
    }

    private fun numberPanel(index: Int, side: Side): View = kit.panel(if (numbers[index] != null && picking != index) PanelStyle.SUCCESS else PanelStyle.NORMAL, 14).apply {
        layoutParams = kit.spaced(10)
        addView(kit.horizontal().apply {
            addView(kit.avatar(side.avatar, 40))
            addView(kit.hgap(10))
            addView(kit.weight(kit.text("عدد ${side.name}", TextStyle.BODY_BOLD, Royal.goldLight)))
            if (numbers[index] != null && picking != index) {
                addView(kit.button("تغییر", ButtonKind.GHOST, height = 38) {
                    picking = index
                    outcome = null
                    host.refresh()
                })
            }
        })
        if (picking == index || numbers[index] == null) {
            addView(kit.gap(8))
            addView(kit.grid(6, MenfiEngine.readyNumbers.map { value ->
                kit.chip(kit.n(value), !rules.hidden && numbers[index] == value, ButtonKind.CHIP_GOLD) {
                    numbers[index] = value
                    outcome = null
                    picking = numbers.indexOfFirst { it == null }
                    host.refresh()
                }
            }, 6))
            if (rules.hidden) addView(kit.text("عدد پس از انتخاب پنهان می‌شود.", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
        } else {
            val shown = if (rules.hidden) "ثبت شد  ✓" else "عدد: ${kit.n(numbers[index]!!)}"
            addView(kit.text(shown, TextStyle.BODY_BOLD, Royal.turquoiseLight, Gravity.CENTER))
        }
    }

    private fun save() {
        val a = numbers[0]
        val b = numbers[1]
        if (a == null || b == null) {
            kit.toast("ابتدا عدد هر دو تیم را ثبت کنید")
            return
        }
        val chosen = outcome
        if (chosen == null) {
            kit.toast("حالت رخ‌داده را انتخاب کنید")
            return
        }
        val round = Round(RoundKind.MENFI_HAND, emptyList(), numbers = listOf(a, b), outcome = chosen)
        if (editIndex != null) session.rounds[editIndex] = round else session.rounds += round
        val ended = SessionOps.commitAndCheck(host, session)
        host.pop()
        if (ended) host.replace(ResultScreen(host, session))
    }
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
            actions = listOf(menuButton(commonMenu())),
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
                        }) {
                            session.rounds.removeAt(index)
                            afterChange()
                        }
                    }
                })
            }
        }
    }

    private fun rankRow(place: Int, side: Side, total: Int, leader: Boolean): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, if (leader) PanelStyle.RAISED else PanelStyle.NORMAL, 18f)
        setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(13))
        layoutParams = kit.spaced(8)
        val medal = when (place) {
            0 -> ButtonKind.CHIP_GOLD
            1 -> ButtonKind.CHIP_SELECTED
            else -> ButtonKind.CHIP
        }
        addView(kit.text(kit.n(place + 1), TextStyle.HEADING, ButtonDrawable.textColor(medal), Gravity.CENTER).apply {
            background = ButtonDrawable(kit.density, medal, 16f)
            setPadding(0, 0, 0, kit.dp(2))
        }, LinearLayout.LayoutParams(kit.dp(36), kit.dp(36)))
        addView(kit.hgap(10))
        addView(kit.avatar(side.avatar, 46))
        addView(kit.hgap(10))
        addView(kit.vertical().apply {
            addView(kit.horizontal().apply {
                addView(kit.text(side.name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
                if (leader) {
                    addView(kit.hgap(6))
                    addView(kit.icon(RoyalIcon.CROWN, Royal.goldLight, 16))
                }
            })
            addView(kit.gap(4))
            addView(kit.progress(if (rules.target > 0) total.toFloat() / rules.target else 0f, if (leader) Royal.turquoise else Royal.gold, 6))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.hgap(10))
        addView(kit.text(kit.signed(total), TextStyle.NUMBER_L, if (leader) Royal.goldLight else Royal.ivory, Gravity.CENTER))
    }
}

class HezarRoundScreen(host: MainActivity, private val session: GameSession, private val editIndex: Int?) : Screen(host) {
    private val rules get() = session.rules.hezar
    private val editing = editIndex?.let { session.rounds.getOrNull(it) }
    private val values = MutableList(session.sides.size) { editing?.raw?.getOrNull(it)?.toString() ?: "" }

    override val sessionId: Long get() = session.id

    override fun build(): View = scaffold(
        title = if (editing != null) "ویرایش دور ${kit.n(editIndex!! + 1)}" else "دور ${kit.n(GameEngine.playedHands(session) + 1)}",
        subtitle = "صفر با جریمهٔ ${kit.signed(rules.zeroPenalty)} ثبت می‌شود؛ امتیاز منفی مجاز است",
        bottom = kit.button(if (editing != null) "ذخیرهٔ تغییرات" else "ثبت امتیاز این دور", ButtonKind.PRIMARY, RoyalIcon.CHECK) { save() }
    ) {
        session.sides.forEachIndexed { i, side ->
            val preview = kit.text(previewText(i), TextStyle.CAPTION, Royal.muted, Gravity.CENTER)
            val field = kit.field("امتیاز ${side.name}", values[i], numeric = true, signed = true).apply {
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        values[i] = s?.toString().orEmpty()
                        preview.text = previewText(i)
                    }
                })
            }
            addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, PanelStyle.NORMAL, 18f)
                setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(13))
                layoutParams = kit.spaced(8)
                addView(kit.avatar(side.avatar, 46))
                addView(kit.hgap(10))
                addView(kit.vertical().apply {
                    addView(kit.text(side.name, TextStyle.BODY_BOLD, Royal.goldLight))
                    addView(preview)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(kit.hgap(8))
                addView(field, LinearLayout.LayoutParams(kit.dp(130), ViewGroup.LayoutParams.WRAP_CONTENT))
            })
        }
    }

    private fun previewText(i: Int): String {
        val v = PersianText.parseInt(values[i]) ?: return "منتظر امتیاز"
        val score = HezarEngine.roundScore(v, rules)
        return if (v == 0) "ثبت: ${kit.signed(score)} (جریمهٔ صفر)" else "ثبت: ${kit.signed(score)}"
    }

    private fun save() {
        val parsed = values.map { PersianText.parseInt(it) }
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
