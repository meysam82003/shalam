package com.meysam.divanemtiaz

import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.Calendar

/** Saving, recalculation and end-of-game handling shared by every live board. */
object SessionOps {
    fun commit(host: MainActivity, session: GameSession) {
        GameEngine.recompute(session)
        session.updatedAt = System.currentTimeMillis()
        host.repo.save(session)
    }

    /** Commits and, when the rules say the game is over, marks it finished. Returns true if it ended. */
    fun commitAndCheck(host: MainActivity, session: GameSession): Boolean {
        GameEngine.recompute(session)
        val complete = GameEngine.isComplete(session)
        if (complete) {
            session.finished = true
            session.endedAt = System.currentTimeMillis()
        }
        session.updatedAt = System.currentTimeMillis()
        host.repo.save(session)
        return complete
    }

    fun rosterAdd(host: MainActivity, sides: List<Side>, isTeam: Boolean) {
        val roster = host.repo.roster().toMutableList()
        var changed = false
        sides.forEach { side ->
            val existing = roster.indexOfFirst { it.name == side.name && it.isTeam == isTeam }
            if (existing < 0) {
                roster += RosterEntry(System.currentTimeMillis() + roster.size, side.name, side.avatar, isTeam)
                changed = true
            } else if (roster[existing].avatar != side.avatar) {
                roster[existing] = roster[existing].copy(avatar = side.avatar)
                changed = true
            }
        }
        if (changed) host.repo.saveRoster(roster)
    }
}

object JalaliDate {
    /** Gregorian → Solar Hijri, the standard arithmetic conversion. */
    fun toJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return intArrayOf(jy, jm, jd)
    }

    fun format(timestamp: Long, digits: Boolean): String {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        val j = toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        val text = String.format(java.util.Locale.US, "%04d/%02d/%02d  %02d:%02d", j[0], j[1], j[2], c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        return PersianText.digits(text, digits)
    }
}

fun Screen.avatarPicker(current: Int, onPick: (Int) -> Unit) {
    lateinit var dialog: Dialog
    val cells = (0 until Emblems.COUNT).map { index ->
        kit.vertical(Gravity.CENTER).apply {
            background = PanelDrawable(kit.density, if (index == current) PanelStyle.SELECTED else PanelStyle.FLAT, 14f)
            setPadding(kit.dp(4), kit.dp(6), kit.dp(4), kit.dp(8))
            addView(kit.avatar(index, 52))
            addView(kit.text(Emblems.names[index], TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 1))
            isClickable = true
            contentDescription = Emblems.names[index]
            setOnClickListener {
                kit.tap(it)
                dialog.dismiss()
                onPick(index)
            }
        }
    }
    dialog = kit.dialog("انتخاب نشان", null, kit.grid(4, cells, 6), listOf(DialogAction("بستن")))
    dialog.show()
}

fun Screen.rosterPicker(isTeam: Boolean, onPick: (RosterEntry) -> Unit) {
    val entries = host.repo.roster().filter { it.isTeam == isTeam }
    if (entries.isEmpty()) {
        kit.toast(if (isTeam) "هنوز تیمی در فهرست نیست" else "هنوز بازیکنی در فهرست نیست")
        return
    }
    lateinit var dialog: Dialog
    val list = kit.vertical().apply {
        entries.forEach { entry ->
            addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
                layoutParams = kit.spaced(6)
                addView(kit.avatar(entry.avatar, 40))
                addView(kit.hgap(10))
                addView(kit.weight(kit.text(entry.name, TextStyle.BODY_BOLD)))
                isClickable = true
                setOnClickListener {
                    kit.tap(it)
                    dialog.dismiss()
                    onPick(entry)
                }
            })
        }
    }
    dialog = kit.dialog(if (isTeam) "انتخاب از تیم‌ها" else "انتخاب از بازیکنان", null, list, listOf(DialogAction("بستن")))
    dialog.show()
}

/** Large score medallion card used at the top of every board. */
fun Screen.sideScoreCard(
    side: Side,
    total: String,
    caption: String?,
    leader: Boolean,
    progress: Float?,
    accent: Int = Royal.gold,
    onAvatar: (() -> Unit)? = null
): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
    background = PanelDrawable(kit.density, if (leader) PanelStyle.RAISED else PanelStyle.NORMAL, 18f)
    setPadding(kit.dp(8), kit.dp(8), kit.dp(8), kit.dp(11))
    addView(FrameLayout(host).apply {
        addView(kit.avatar(side.avatar, 50).apply {
            layoutParams = FrameLayout.LayoutParams(kit.dp(50), kit.dp(50), Gravity.CENTER)
            if (onAvatar != null) setOnClickListener { kit.tap(it); onAvatar() }
        })
        if (leader) {
            addView(IconView(host, RoyalIcon.CROWN, Royal.goldLight), FrameLayout.LayoutParams(kit.dp(18), kit.dp(18), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
    }, LinearLayout.LayoutParams(kit.dp(60), kit.dp(60)))
    addView(kit.text(side.name, TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER, 1))
    addView(kit.text(total, TextStyle.NUMBER_XL, accent, Gravity.CENTER, 1))
    if (caption != null) addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 2))
    if (progress != null) {
        addView(kit.gap(4))
        addView(kit.progress(progress, if (leader) Royal.turquoise else Royal.gold, 6))
    }
}

/** One recorded hand in a board list; tapping opens edit/delete actions. */
fun Screen.roundRow(
    index: Int,
    scores: List<String>,
    scoreColors: List<Int>,
    detail: String,
    onClick: (() -> Unit)?
): View = kit.horizontal().apply {
    background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
    setPadding(kit.dp(9), kit.dp(7), kit.dp(9), kit.dp(10))
    layoutParams = kit.spaced(5)
    addView(kit.text(kit.n(index + 1), TextStyle.LABEL_BOLD, Royal.night, Gravity.CENTER, 1).apply {
        background = ButtonDrawable(kit.density, ButtonKind.CHIP_GOLD, 10f)
        setPadding(0, 0, 0, kit.dp(2))
    }, LinearLayout.LayoutParams(kit.dp(28), kit.dp(28)))
    addView(kit.hgap(8))
    addView(kit.vertical().apply {
        addView(kit.horizontal().apply {
            scores.forEachIndexed { i, s ->
                if (i > 0) addView(kit.text("|", TextStyle.LABEL, Royal.dim, Gravity.CENTER).apply { setPadding(kit.dp(6), 0, kit.dp(6), 0) })
                addView(kit.text(s, TextStyle.BODY_BOLD, scoreColors.getOrElse(i) { Royal.ivory }, Gravity.CENTER, 1))
            }
        })
        if (detail.isNotBlank()) addView(kit.text(detail, TextStyle.CAPTION, Royal.muted, maxLines = 2))
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    if (onClick != null) {
        addView(kit.icon(RoyalIcon.MENU, Royal.dim, 18))
        isClickable = true
        setOnClickListener { kit.tap(it); onClick() }
    }
}

fun Screen.scoreColor(value: Int): Int = when {
    value > 0 -> Royal.turquoiseLight
    value < 0 -> Royal.crimsonLight
    else -> Royal.muted
}

fun Screen.emptyState(icon: RoyalIcon, title: String, message: String): View = kit.panel(PanelStyle.FLAT, 18).apply {
    gravity = Gravity.CENTER_HORIZONTAL
    addView(kit.icon(icon, Royal.gold, 34))
    addView(kit.gap(6))
    addView(kit.text(title, TextStyle.BODY_BOLD, Royal.goldLight, Gravity.CENTER))
    addView(kit.text(message, TextStyle.LABEL, Royal.muted, Gravity.CENTER))
}

/** Action sheet for one hand: edit, delete, cancel. */
fun Screen.roundActions(title: String, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    val actions = mutableListOf<DialogAction>()
    if (onEdit != null) actions += DialogAction("ویرایش", ButtonKind.PRIMARY, onClick = onEdit)
    actions += DialogAction("حذف", ButtonKind.DANGER) {
        kit.confirm("حذف دست", "این دست حذف و جمع امتیازها دوباره محاسبه شود؟", "حذف", true, onDelete)
    }
    actions += DialogAction("انصراف")
    kit.dialog(title, null, null, actions).show()
}

fun Screen.sessionSubtitle(session: GameSession): String {
    val parts = mutableListOf<String>()
    when (session.game) {
        GameType.SHALAM -> {
            parts += if (session.rules.shalam.joker) "با جوکر" else "بدون جوکر"
            parts += "تا ${kit.n(session.rules.shalam.endPoint)}"
        }
        GameType.MENFI -> parts += "${kit.n(session.rules.menfi.hands)} دست"
        GameType.HEZARTAII -> {
            parts += "${kit.n(session.sides.size)} بازیکن"
            parts += "تا ${kit.n(session.rules.hezar.target)}"
            if (session.rules.hezar.rounds > 0) parts += "${kit.n(session.rules.hezar.rounds)} دور"
        }
        GameType.DOLO -> {
            val st = DoloEngine.state(session)
            parts += "${kit.n(st.active.size)} از ${kit.n(session.sides.size)} بازیکن"
        }
    }
    if (session.label.isNotBlank()) parts += session.label
    return parts.joinToString("  •  ")
}

/** Rename sides and change their emblems for a running game. */
fun Screen.editSidesDialog(session: GameSession, onSaved: () -> Unit) {
    val fields = session.sides.map { kit.field("نام", it.name) }
    val avatars = session.sides.map { it.avatar }.toMutableList()
    val avatarViews = mutableListOf<EmblemAvatarView>()
    val body = kit.vertical().apply {
        session.sides.forEachIndexed { i, _ ->
            addView(kit.horizontal().apply {
                layoutParams = kit.spaced(8)
                val av = kit.avatar(avatars[i], 48)
                avatarViews += av
                av.setOnClickListener {
                    avatarPicker(avatars[i]) { picked ->
                        avatars[i] = picked
                        avatarViews[i].avatar = picked
                    }
                }
                addView(av)
                addView(kit.hgap(8))
                addView(kit.weight(fields[i]))
            })
        }
    }
    kit.dialog("ویرایش نام و نشان", null, body, listOf(
        DialogAction("ذخیره", ButtonKind.PRIMARY) {
            session.sides.indices.forEach { i ->
                val name = fields[i].text.toString().trim().ifBlank { session.sides[i].name }
                session.sides[i] = Side(name, avatars[i])
            }
            SessionOps.commit(host, session)
            onSaved()
        },
        DialogAction("انصراف")
    )).show()
}

/** Manual penalty («تقلب») for one side, as in the reference app. */
fun Screen.penaltyDialog(session: GameSession, onSaved: () -> Unit) {
    var side = 0
    var positive = false
    val amount = kit.field("مقدار امتیاز", "", numeric = true)
    val note = kit.field("توضیح (اختیاری)")
    val sideChips = kit.vertical()
    val signChips = kit.vertical()
    fun redraw() {
        sideChips.removeAllViews()
        sideChips.addView(kit.flow(session.sides.mapIndexed { i, s -> kit.chip(s.name, i == side) { side = i; redraw() }.apply { setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(8)) } }))
        signChips.removeAllViews()
        signChips.addView(kit.flow(listOf(
            kit.chip("منفی (جریمه)", !positive, ButtonKind.CHIP_SELECTED) { positive = false; redraw() }.apply { setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(8)) },
            kit.chip("مثبت", positive, ButtonKind.CHIP_SELECTED) { positive = true; redraw() }.apply { setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(8)) }
        )))
    }
    redraw()
    val error = kit.text("", TextStyle.CAPTION, Royal.crimsonLight, Gravity.CENTER)
    lateinit var dialog: Dialog
    dialog = kit.dialog("ثبت تقلب / جریمه", null, kit.vertical().apply {
        addView(kit.text(if (session.game.isTeamGame) "برای تیم" else "برای بازیکن", TextStyle.LABEL_BOLD, Royal.goldLight))
        addView(sideChips)
        addView(kit.gap(8))
        addView(kit.text("نوع امتیاز", TextStyle.LABEL_BOLD, Royal.goldLight))
        addView(signChips)
        addView(kit.gap(8))
        addView(amount, kit.spaced(8))
        addView(note, kit.fill())
        addView(error)
    }, listOf(
        DialogAction("ثبت", ButtonKind.PRIMARY, dismiss = false) {
            val value = PersianText.parseInt(amount.text.toString())
            if (value == null || value <= 0) {
                error.text = "مقدار مثبت وارد کنید"
            } else {
                dialog.dismiss()
                val raw = MutableList(session.sides.size) { 0 }
                raw[side] = if (positive) value else -value
                val text = note.text.toString().trim().ifBlank { if (positive) "امتیاز مثبت دستی" else "جریمهٔ تقلب" }
                session.rounds += Round(RoundKind.PENALTY, raw, note = text, raw = raw)
                onSaved()
            }
        },
        DialogAction("انصراف")
    ))
    dialog.show()
}

/** Sets new totals directly; stored as a correction row so history stays traceable. */
fun Screen.adjustTotalsDialog(session: GameSession, onSaved: () -> Unit) {
    val totals = GameEngine.totals(session)
    val fields = session.sides.mapIndexed { i, s -> kit.field(s.name, totals[i].toString(), numeric = true, signed = true) }
    val error = kit.text("", TextStyle.CAPTION, Royal.crimsonLight, Gravity.CENTER)
    lateinit var dialog: Dialog
    dialog = kit.dialog("ویرایش جمع امتیازها", "جمع جدید هر طرف را وارد کنید؛ اختلاف به‌صورت یک ردیف اصلاحی ثبت می‌شود.", kit.vertical().apply {
        session.sides.forEachIndexed { i, s ->
            addView(kit.text(s.name, TextStyle.LABEL_BOLD, Royal.goldLight))
            addView(fields[i], kit.spaced(8))
        }
        addView(error)
    }, listOf(
        DialogAction("ذخیره", ButtonKind.PRIMARY, dismiss = false) {
            val values = fields.map { PersianText.parseInt(it.text.toString()) }
            if (values.any { it == null }) {
                error.text = "همهٔ جمع‌ها را وارد کنید"
            } else {
                dialog.dismiss()
                val raw = values.mapIndexed { i, v -> v!! - totals[i] }
                if (raw.any { it != 0 }) {
                    session.rounds += Round(RoundKind.ADJUST, raw, note = "ویرایش جمع امتیازها", raw = raw)
                    onSaved()
                }
            }
        },
        DialogAction("انصراف")
    ))
    dialog.show()
}

fun Screen.timerText(session: GameSession, extraMs: Long = 0L): String =
    PersianText.duration(session.elapsedMs + extraMs, settings.general.persianDigits)

/**
 * Referee's manual entry: every side gets a value and an explicit + / − sign.
 * Used beside the outcome buttons of every game.
 */
fun Screen.manualScoresDialog(
    title: String,
    names: List<String>,
    initial: List<Int>,
    note: String? = null,
    onSave: (List<Int>) -> Unit
) {
    val signs = initial.map { it >= 0 }.toMutableList()
    val fields = initial.mapIndexed { i, v -> kit.field(names[i], if (v == 0) "" else kotlin.math.abs(v).toString(), numeric = true) }
    val signViews = mutableListOf<LinearLayout>()
    fun drawSign(i: Int) {
        val box = signViews[i]
        box.removeAllViews()
        box.addView(kit.chip("+", signs[i], ButtonKind.CHIP_SELECTED) { signs[i] = true; drawSign(i) }, LinearLayout.LayoutParams(kit.dp(40), kit.dp(40)))
        box.addView(kit.hgap(4))
        box.addView(kit.chip("−", !signs[i], ButtonKind.DANGER) { signs[i] = false; drawSign(i) }, LinearLayout.LayoutParams(kit.dp(40), kit.dp(40)))
    }
    val body = kit.vertical().apply {
        if (note != null) addView(kit.text(note, TextStyle.CAPTION, Royal.muted, Gravity.CENTER), kit.spaced(6))
        names.forEachIndexed { i, name ->
            addView(kit.horizontal().apply {
                layoutParams = kit.spaced(6)
                addView(kit.weight(kit.text(name, TextStyle.LABEL_BOLD, Royal.goldLight, maxLines = 1)))
                val sign = kit.horizontal()
                signViews += sign
                addView(sign)
                addView(kit.hgap(6))
                addView(fields[i], LinearLayout.LayoutParams(kit.dp(92), ViewGroup.LayoutParams.WRAP_CONTENT))
            })
            drawSign(i)
        }
    }
    kit.dialog(title, null, body, listOf(
        DialogAction("ثبت", ButtonKind.PRIMARY) {
            val values = fields.mapIndexed { i, f ->
                val v = kotlin.math.abs(PersianText.parseInt(f.text.toString()) ?: 0)
                if (signs[i]) v else -v
            }
            onSave(values)
        },
        DialogAction("انصراف")
    )).show()
}

/** Adds a player to a running individual game; earlier rounds count as zero for them. */
fun Screen.addPlayerDialog(session: GameSession, onSaved: () -> Unit) {
    kit.textPrompt("افزودن بازیکن", "بازیکن ${kit.n(session.sides.size + 1)}") { name ->
        session.sides += Side(name, (session.sides.size * 5) % Emblems.COUNT)
        SessionOps.rosterAdd(host, listOf(session.sides.last()), false)
        onSaved()
    }
}
