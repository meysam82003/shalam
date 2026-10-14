package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

class DoloBoardScreen(host: MainActivity, session: GameSession) : BoardScreen(host, session) {
    private val rules get() = session.rules.dolo

    override fun build(): View {
        val totals = GameEngine.totals(session)
        val st = DoloEngine.state(session)
        val played = GameEngine.playedHands(session)
        return scaffold(
            title = "داوری دو لو گشنیز",
            subtitle = sessionSubtitle(session),
            actions = listOf(menuButton(listOf(
                "حذف دستی بازیکن" to { host.push(DoloEliminationScreen(host, session, manual = true)) },
                "افزودن بازیکن" to { addPlayerDialog(session) { afterChange() } },
                "ماشین‌حساب ورق" to { host.push(DeckCalcScreen(host, GameType.DOLO, st.active.size)) }
            ) + commonMenu())),
            bottom = kit.horizontal().apply {
                addView(kit.weight(kit.button("ثبت دست ${kit.n(played + 1)}", ButtonKind.PRIMARY, RoyalIcon.PLUS) {
                    host.push(DoloHandScreen(host, session, null))
                }))
                addView(kit.hgap(8))
                addView(kit.iconButton(RoyalIcon.UNDO, "حذف ردیف آخر", ButtonKind.SECONDARY, 46) {
                    if (session.rounds.isEmpty()) kit.toast("هنوز دستی ثبت نشده است")
                    else kit.confirm("حذف ردیف آخر", "آخرین دست یا حذف ثبت‌شده برداشته شود؟", "حذف", true) {
                        session.rounds.removeAt(session.rounds.lastIndex)
                        afterChange()
                    }
                })
            }
        ) {
            val minimum = rules.minimumFor(st.active.size)
            addView(kit.panel(PanelStyle.RAISED, 12).apply {
                layoutParams = kit.spaced(8)
                addView(kit.horizontal().apply {
                    addView(GameSealView(host, GameType.DOLO), LinearLayout.LayoutParams(kit.dp(44), kit.dp(44)))
                    addView(kit.hgap(10))
                    addView(kit.vertical().apply {
                        addView(kit.text("${kit.n(st.active.size)} بازیکن در بازی  •  حداقل ${kit.n(minimum)}", TextStyle.BODY_BOLD, Royal.goldLight))
                        val stageText = if (st.extension) "دست اضافهٔ تساوی: ${kit.n(st.handsInStage)} از ${kit.n(st.handsDue)}"
                        else "تا حذف بعدی: دست ${kit.n(st.handsInStage)} از ${kit.n(st.handsDue)}"
                        addView(kit.text(stageText, TextStyle.LABEL, Royal.muted))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })
                addView(kit.gap(6))
                addView(kit.progress(if (st.handsDue > 0) st.handsInStage.toFloat() / st.handsDue else 0f, if (st.eliminationDue) Royal.crimson else Royal.turquoise, 6))
            })
            addView(statusRow("${kit.n(played)} دست"))
            if (st.eliminationDue) {
                val (cut, tie) = DoloEngine.candidates(session)
                addView(kit.panel(PanelStyle.DANGER, 12).apply {
                    layoutParams = kit.spaced(8)
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(kit.text("زمان حذف کم‌امتیازترین‌ها", TextStyle.HEADING, Royal.ivory, Gravity.CENTER))
                    addView(kit.text(
                        if (tie) "تساوی در پایین جدول؛ داور انتخاب می‌کند یا ${kit.n(rules.tieExtraHands)} دست اضافه بازی می‌شود"
                        else "پیشنهاد حذف: ${cut.joinToString("، ") { session.sides[it].name }}",
                        TextStyle.LABEL, Royal.ivory, Gravity.CENTER
                    ))
                    addView(kit.gap(6))
                    addView(kit.button("بررسی و حذف", ButtonKind.DANGER, RoyalIcon.FLAG, 40) {
                        host.push(DoloEliminationScreen(host, session, manual = false))
                    })
                })
            }
            addView(kit.section("بازیکنان در بازی", RoyalIcon.TROPHY))
            val ranking = GameEngine.ranking(session)
            val activeRanked = ranking.filter { it in st.active }
            val leaderTotal = activeRanked.firstOrNull()?.let { totals[it] }
            activeRanked.forEachIndexed { place, side ->
                addView(playerRow(place, side, totals[side], if (rules.resetAfterElimination) st.stageTotals[side] else null, leaderTotal == totals[side] && activeRanked.map { totals[it] }.distinct().size > 1, null))
            }
            val out = ranking.filter { it !in st.active }
            if (out.isNotEmpty()) {
                addView(kit.section("حذف‌شده‌ها", RoyalIcon.CLOSE))
                out.forEach { side ->
                    addView(playerRow(-1, side, totals[side], null, false, "حذف پس از دست ${kit.n(handsBefore(st.eliminatedAt[side] ?: 0))}"))
                }
            }
            addView(kit.section("دست‌های ثبت‌شده", RoyalIcon.HISTORY))
            if (session.rounds.isEmpty()) {
                addView(emptyState(RoyalIcon.CLUB, "هنوز دستی ثبت نشده", "دارندهٔ دو لو گشنیز حکم را تعیین می‌کند؛ هر نفر دست‌کم ${kit.n(minimum)} می‌خواند."))
            }
            session.rounds.indices.reversed().forEach { index ->
                val round = session.rounds[index]
                if (round.kind == RoundKind.DOLO_ELIM) {
                    addView(kit.horizontal().apply {
                        background = PanelDrawable(kit.density, PanelStyle.DANGER, 14f)
                        setPadding(kit.dp(10), kit.dp(7), kit.dp(10), kit.dp(10))
                        layoutParams = kit.spaced(5)
                        addView(kit.icon(RoyalIcon.FLAG, Royal.crimsonLight, 18))
                        addView(kit.hgap(8))
                        addView(kit.weight(kit.text(describeRound(session, round), TextStyle.LABEL_BOLD, Royal.ivory, maxLines = 2)))
                        addView(kit.icon(RoyalIcon.MENU, Royal.dim, 16))
                        isClickable = true
                        setOnClickListener {
                            kit.tap(it)
                            roundActions("ردیف ${kit.n(index + 1)}", null) {
                                session.rounds.removeAt(index)
                                afterChange()
                            }
                        }
                    })
                    return@forEach
                }
                val shown = session.sides.indices.filter { round.score(it) != 0 || round.numbers.getOrElse(it) { -1 } >= 0 }
                addView(kit.vertical().apply {
                    background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                    setPadding(kit.dp(9), kit.dp(7), kit.dp(9), kit.dp(10))
                    layoutParams = kit.spaced(5)
                    addView(kit.horizontal().apply {
                        addView(kit.weight(kit.text("دست ${kit.n(handsBefore(index) + 1)}  •  ${describeRound(session, round)}", TextStyle.LABEL_BOLD, Royal.goldLight, maxLines = 1)))
                        addView(kit.icon(RoyalIcon.MENU, Royal.dim, 16))
                    })
                    addView(kit.gap(4))
                    addView(kit.flow(shown.map { i ->
                        val declared = round.numbers.getOrElse(i) { -1 }
                        kit.badge("${session.sides[i].name}${if (declared >= 0) " (${kit.n(declared)})" else ""} ${kit.signed(round.score(i))}", scoreColor(round.score(i)))
                    }, 5))
                    isClickable = true
                    setOnClickListener {
                        kit.tap(it)
                        roundActions("دست ${kit.n(handsBefore(index) + 1)}", if (round.kind == RoundKind.DOLO_HAND) {
                            { host.push(DoloHandScreen(host, session, index)) }
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

    private fun handsBefore(index: Int): Int = session.rounds.take(index).count {
        it.kind != RoundKind.PENALTY && it.kind != RoundKind.ADJUST && it.kind != RoundKind.DOLO_ELIM
    }

    private fun playerRow(place: Int, side: Int, total: Int, stage: Int?, leader: Boolean, note: String?): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, if (leader) PanelStyle.RAISED else if (place < 0) PanelStyle.FLAT else PanelStyle.NORMAL, 16f)
        setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
        layoutParams = kit.spaced(5)
        if (place >= 0) {
            addView(kit.text(kit.n(place + 1), TextStyle.LABEL_BOLD, Royal.night, Gravity.CENTER).apply {
                background = ButtonDrawable(kit.density, if (place == 0) ButtonKind.CHIP_GOLD else ButtonKind.CHIP_SELECTED, 14f)
                setPadding(0, 0, 0, kit.dp(2))
            }, LinearLayout.LayoutParams(kit.dp(28), kit.dp(28)))
            addView(kit.hgap(8))
        }
        addView(kit.avatar(session.sides[side].avatar, 34))
        addView(kit.hgap(8))
        addView(kit.vertical().apply {
            addView(kit.horizontal().apply {
                addView(kit.text(session.sides[side].name, TextStyle.BODY_BOLD, if (place < 0) Royal.muted else Royal.ivory, maxLines = 1))
                if (leader) {
                    addView(kit.hgap(6))
                    addView(kit.icon(RoyalIcon.CROWN, Royal.goldLight, 15))
                }
            })
            val caption = listOfNotNull(note, stage?.let { "امتیاز این مرحله ${kit.signed(it)}" }).joinToString("  •  ")
            if (caption.isNotBlank()) addView(kit.text(caption, TextStyle.CAPTION, Royal.muted))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.text(kit.signed(total), TextStyle.HEADING, scoreColor(total), Gravity.CENTER))
    }
}

/** One hand: trump holder, minimum, declarations and results with the «همه … به جز» lists. */
class DoloHandScreen(host: MainActivity, private val session: GameSession, private val editIndex: Int?) : Screen(host) {
    private val rules get() = session.rules.dolo
    private val editing = editIndex?.let { session.rounds.getOrNull(it) }
    private val players: List<Int> = if (editing != null && editing.numbers.isNotEmpty()) {
        session.sides.indices.filter { editing.numbers.getOrElse(it) { -1 } >= 0 }
    } else DoloEngine.state(session, editIndex ?: session.rounds.size).active
    private var minimum = editing?.bid?.takeIf { editing.numbers.isNotEmpty() } ?: rules.minimumFor(players.size)
    private val declared = players.associateWith { editing?.numbers?.getOrNull(it)?.takeIf { v -> v >= 0 } ?: minimum }.toMutableMap()
    private val made = players.associateWith { (editing?.raw?.getOrNull(it) ?: 1) == 1 }.toMutableMap()
    private var declMode = if (editing != null) (if (declared.values.all { it == minimum }) 1 else 2) else 0
    private var resultMode = if (editing != null) (if (made.values.all { it }) 1 else 2) else 0
    private var holder = editing?.contractTeam ?: -1
    private var suit = editing?.suit ?: Suit.NONE
    private var manual: List<Int>? = if (editing?.outcome == MenfiEngine.MANUAL) editing.raw else null

    override val sessionId: Long get() = session.id

    override fun build(): View = scaffold(
        title = if (editing != null) "ویرایش دست" else "دست ${kit.n(GameEngine.playedHands(session) + 1)}",
        subtitle = "${kit.n(players.size)} بازیکن  •  حداقل ${kit.n(minimum)}",
        bottom = kit.button(if (editing != null) "ذخیرهٔ تغییرات" else "ثبت این دست", ButtonKind.PRIMARY, RoyalIcon.CHECK) { save() }
    ) {
        addView(kit.section("حکم", RoyalIcon.CLUB))
        addView(kit.text("دو لو گشنیز دست چه کسی است؟ (اختیاری)", TextStyle.LABEL, Royal.muted), kit.spaced(4))
        addView(kit.flow(players.map { i ->
            kit.chip(session.sides[i].name, holder == i, ButtonKind.CHIP_GOLD) { holder = if (holder == i) -1 else i; host.refresh() }
                .apply { setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(6)) }
        }, 5), kit.spaced(6))
        addView(kit.grid(5, (listOf(Suit.NONE) + Suit.all).map { s ->
            if (s == Suit.NONE) kit.chip("بدون", suit == s) { suit = s; host.refresh() } else suitChip(s)
        }, 5), kit.spaced(4))

        addView(kit.stepperRow("حداقل این دست", "پیش‌فرض برای ${kit.n(players.size)} نفر: ${kit.n(rules.minimumFor(players.size))}؛ داور می‌تواند تغییر دهد", minimum, 0, 30, 1) { v ->
            minimum = v
            players.forEach { if (declared.getValue(it) < v) declared[it] = v }
            host.refresh()
        })

        addView(kit.section("عدد هر نفر", RoyalIcon.STAR))
        addView(kit.grid(2, listOf(
            kit.chip("همه حداقل (${kit.n(minimum)})", declMode == 1, ButtonKind.CHIP_SELECTED) {
                declMode = 1
                players.forEach { declared[it] = minimum }
                host.refresh()
            },
            kit.chip("همه حداقل به جز…", declMode == 2, ButtonKind.CHIP_SELECTED) { declMode = 2; host.refresh() }
        ), 6), kit.spaced(6))
        if (declMode == 2) {
            players.forEach { i ->
                val value = kit.text(kit.n(declared.getValue(i)), TextStyle.HEADING, if (declared.getValue(i) > minimum) Royal.goldLight else Royal.ivory, Gravity.CENTER)
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, if (declared.getValue(i) > minimum) PanelStyle.SELECTED else PanelStyle.FLAT, 14f)
                    setPadding(kit.dp(10), kit.dp(5), kit.dp(10), kit.dp(8))
                    layoutParams = kit.spaced(4)
                    addView(kit.avatar(session.sides[i].avatar, 28))
                    addView(kit.hgap(8))
                    addView(kit.weight(kit.text(session.sides[i].name, TextStyle.LABEL_BOLD, Royal.ivory, maxLines = 1)))
                    addView(kit.iconButton(RoyalIcon.PLUS, "افزایش", ButtonKind.CHIP, 36) {
                        declared[i] = declared.getValue(i) + 1
                        host.refresh()
                    })
                    addView(value, LinearLayout.LayoutParams(kit.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(kit.iconButton(RoyalIcon.MINUS, "کاهش", ButtonKind.CHIP, 36) {
                        if (declared.getValue(i) > minimum) declared[i] = declared.getValue(i) - 1 else kit.toast("کمتر از حداقل مجاز نیست")
                        host.refresh()
                    })
                })
            }
        } else if (declMode == 1) {
            addView(kit.text("همه ${kit.n(minimum)} خواندند.", TextStyle.LABEL_BOLD, Royal.turquoiseLight, Gravity.CENTER), kit.spaced(6))
        }

        addView(kit.section("نتیجهٔ دست", RoyalIcon.CHECK))
        addView(kit.grid(2, listOf(
            kit.chip("همه گرفتند", resultMode == 1, ButtonKind.CHIP_SELECTED) {
                resultMode = 1
                manual = null
                players.forEach { made[it] = true }
                host.refresh()
            },
            kit.chip("همه گرفتند به جز…", resultMode == 2, ButtonKind.CHIP_SELECTED) { resultMode = 2; manual = null; host.refresh() }
        ), 6), kit.spaced(6))
        if (resultMode == 2) {
            addView(kit.text("تیک کسانی که نگرفتند را بردارید؛ عددشان منفی ثبت می‌شود.", TextStyle.CAPTION, Royal.muted), kit.spaced(4))
            players.forEach { i ->
                val ok = made.getValue(i)
                val score = score(i)
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, if (ok) PanelStyle.SUCCESS else PanelStyle.DANGER, 14f)
                    setPadding(kit.dp(10), kit.dp(5), kit.dp(10), kit.dp(8))
                    layoutParams = kit.spaced(4)
                    addView(FrameLayout(host).apply {
                        background = ButtonDrawable(kit.density, if (ok) ButtonKind.CHIP_SELECTED else ButtonKind.CHIP, 8f)
                        if (ok) addView(kit.text("✓", TextStyle.HEADING, Royal.night, Gravity.CENTER), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }, LinearLayout.LayoutParams(kit.dp(32), kit.dp(32)))
                    addView(kit.hgap(8))
                    addView(kit.weight(kit.text("${session.sides[i].name}  (${kit.n(declared.getValue(i))})", TextStyle.LABEL_BOLD, Royal.ivory, maxLines = 1)))
                    addView(kit.text(kit.signed(score), TextStyle.BODY_BOLD, scoreColor(score)))
                    isClickable = true
                    contentDescription = session.sides[i].name
                    setOnClickListener { kit.tap(it); made[i] = !ok; host.refresh() }
                })
            }
        }
        if (resultMode != 0 && declMode != 0 && manual == null) {
            addView(kit.panel(PanelStyle.FLAT, 10).apply {
                layoutParams = kit.spaced(6)
                addView(kit.text("امتیاز این دست", TextStyle.LABEL_BOLD, Royal.goldLight))
                addView(kit.flow(players.map { i -> kit.badge("${session.sides[i].name} ${kit.signed(score(i))}", scoreColor(score(i))) }, 5))
            })
        }

        addView(kit.section("ثبت دستی داور", RoyalIcon.EDIT))
        manual?.let { m ->
            addView(kit.panel(PanelStyle.SELECTED, 10).apply {
                layoutParams = kit.spaced(6)
                addView(kit.flow(players.map { i -> kit.badge("${session.sides[i].name} ${kit.signed(m.getOrElse(i) { 0 })}", scoreColor(m.getOrElse(i) { 0 })) }, 5))
            })
        }
        addView(kit.button(if (manual == null) "ورود دستی امتیاز هر نفر (+ / −)" else "ویرایش امتیاز دستی", ButtonKind.SECONDARY, RoyalIcon.SLIDERS, 42) {
            val current = manual
            manualScoresDialog("ثبت دستی امتیاز", players.map { session.sides[it].name }, players.map { i -> current?.getOrNull(i) ?: if (resultMode != 0) score(i) else 0 }) { values ->
                val full = MutableList(session.sides.size) { 0 }
                players.forEachIndexed { k, i -> full[i] = values[k] }
                manual = full
                resultMode = 0
                host.refresh()
            }
        })
    }

    private fun score(i: Int): Int {
        val n = declared.getValue(i)
        return if (made.getValue(i)) n * rules.madeMultiplier else -n * rules.failMultiplier
    }

    private fun suitChip(s: Int): View = FrameLayout(host).apply {
        background = ButtonDrawable(kit.density, if (suit == s) ButtonKind.CHIP_SELECTED else ButtonKind.CHIP, 12f)
        minimumHeight = kit.dp(40)
        isClickable = true
        contentDescription = Suit.title(s)
        addView(IconView(host, RoyalIcons.forSuit(s)!!, if (suit == s) Royal.night else RoyalIcons.suitColor(s)), FrameLayout.LayoutParams(kit.dp(20), kit.dp(20), Gravity.CENTER))
        setOnClickListener { kit.tap(it); suit = s; host.refresh() }
    }

    private fun save() {
        val numbers = MutableList(session.sides.size) { -1 }
        players.forEach { numbers[it] = declared.getValue(it) }
        val m = manual
        val round = if (m != null) {
            Round(RoundKind.DOLO_HAND, emptyList(), contractTeam = holder, bid = minimum, suit = suit, numbers = numbers, outcome = MenfiEngine.MANUAL, raw = m)
        } else {
            if (declMode == 0) {
                kit.toast("عددها را با «همه حداقل» یا «همه حداقل به جز» مشخص کنید")
                return
            }
            if (resultMode == 0) {
                kit.toast("نتیجه را با «همه گرفتند» یا «همه گرفتند به جز» مشخص کنید")
                return
            }
            val flags = MutableList(session.sides.size) { 0 }
            players.forEach { flags[it] = if (made.getValue(it)) 1 else 0 }
            Round(RoundKind.DOLO_HAND, emptyList(), contractTeam = holder, bid = minimum, suit = suit, numbers = numbers, raw = flags)
        }
        if (editIndex != null) session.rounds[editIndex] = round else session.rounds += round
        val ended = SessionOps.commitAndCheck(host, session)
        host.pop()
        if (ended) host.replace(ResultScreen(host, session))
    }
}

/** Referee ticks who leaves; the lowest scores are pre-ticked and a tie at the cut can add hands. */
class DoloEliminationScreen(host: MainActivity, private val session: GameSession, private val manual: Boolean) : Screen(host) {
    private val rules get() = session.rules.dolo
    private val state = DoloEngine.state(session)
    private val candidates = DoloEngine.candidates(session)
    private val ticked: MutableSet<Int> = if (manual) mutableSetOf() else candidates.first.toMutableSet()

    override val sessionId: Long get() = session.id

    override fun build(): View {
        val sorted = state.active.sortedWith(compareBy<Int> { state.stageTotals[it] }.thenBy { it })
        return scaffold(
            title = if (manual) "حذف دستی بازیکن" else "حذف کم‌امتیازترین‌ها",
            subtitle = "از کمترین امتیاز به بیشترین؛ کسانی که تیک دارند حذف می‌شوند",
            bottom = kit.button("حذف ${kit.n(ticked.size)} بازیکن انتخاب‌شده", ButtonKind.DANGER, RoyalIcon.FLAG) { confirm() }
        ) {
            if (!manual && candidates.second) {
                val cutScore = candidates.first.lastOrNull()?.let { state.stageTotals[it] }
                val tied = sorted.filter { state.stageTotals[it] == cutScore }
                addView(kit.panel(PanelStyle.DANGER, 12).apply {
                    layoutParams = kit.spaced(8)
                    addView(kit.text("تساوی در خط حذف", TextStyle.HEADING, Royal.ivory, Gravity.CENTER))
                    addView(kit.text("${tied.joinToString("، ") { session.sides[it].name }} با امتیاز ${kit.signed(cutScore ?: 0)} مساوی‌اند. طبق قانون ${kit.n(rules.tieExtraHands)} دست دیگر بازی می‌شود؛ داور می‌تواند خودش هم انتخاب کند.", TextStyle.LABEL, Royal.ivory, Gravity.CENTER))
                    addView(kit.gap(6))
                    addView(kit.button("${kit.n(rules.tieExtraHands)} دست اضافه بازی شود", ButtonKind.PRIMARY, RoyalIcon.PLAY, 40) { extend(rules.tieExtraHands, "تساوی در خط حذف") })
                })
            }
            sorted.forEach { i ->
                val on = i in ticked
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, if (on) PanelStyle.DANGER else PanelStyle.NORMAL, 14f)
                    setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(9))
                    layoutParams = kit.spaced(5)
                    addView(FrameLayout(host).apply {
                        background = ButtonDrawable(kit.density, if (on) ButtonKind.DANGER else ButtonKind.CHIP, 8f)
                        if (on) addView(kit.text("✓", TextStyle.HEADING, Royal.ivory, Gravity.CENTER), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }, LinearLayout.LayoutParams(kit.dp(32), kit.dp(32)))
                    addView(kit.hgap(8))
                    addView(kit.avatar(session.sides[i].avatar, 32))
                    addView(kit.hgap(8))
                    addView(kit.weight(kit.text(session.sides[i].name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1)))
                    addView(kit.text(kit.signed(state.stageTotals[i]), TextStyle.HEADING, scoreColor(state.stageTotals[i])))
                    isClickable = true
                    contentDescription = session.sides[i].name
                    setOnClickListener {
                        kit.tap(it)
                        if (on) ticked.remove(i) else ticked.add(i)
                        host.refresh()
                    }
                })
            }
            if (!manual) {
                addView(kit.gap(6))
                val regular = (if (rules.handsPerRound > 0) rules.handsPerRound else state.active.size) * rules.eliminateEvery.coerceAtLeast(1)
                addView(kit.button("بدون حذف، ${kit.n(regular)} دست دیگر", ButtonKind.GHOST, RoyalIcon.UNDO, 40) { extend(regular, "ادامه بدون حذف") })
            }
        }
    }

    private fun extend(hands: Int, note: String) {
        session.rounds += Round(RoundKind.DOLO_ELIM, emptyList(), note = note, bid = hands.coerceAtLeast(1), raw = List(session.sides.size) { 0 })
        SessionOps.commit(host, session)
        host.pop()
    }

    private fun confirm() {
        if (ticked.isEmpty()) {
            kit.toast("حداقل یک بازیکن را انتخاب کنید")
            return
        }
        if (ticked.size >= state.active.size) {
            kit.toast("دست‌کم یک بازیکن باید در بازی بماند")
            return
        }
        val names = ticked.joinToString("، ") { session.sides[it].name }
        kit.confirm("حذف از بازی", "$names از بازی حذف شود؟", "حذف", true) {
            session.rounds += Round(RoundKind.DOLO_ELIM, emptyList(), note = "حذف", raw = List(session.sides.size) { if (it in ticked) 1 else 0 })
            val ended = SessionOps.commitAndCheck(host, session)
            host.pop()
            if (ended) host.replace(ResultScreen(host, session))
        }
    }
}
