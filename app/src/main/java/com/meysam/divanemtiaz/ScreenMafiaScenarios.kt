package com.meysam.divanemtiaz

import android.app.Dialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout

// ------------------------------------------------------------------ shared editors

/** Text entry that may be multi-line and may be left empty (empty = back to the default). */
fun Screen.longPrompt(title: String, initial: String, multi: Boolean, hint: String = "خالی = متن پیش‌فرض", onValue: (String) -> Unit) {
    val input = kit.field(hint, initial).apply {
        if (multi) {
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4; maxLines = 12
            gravity = Gravity.TOP or Gravity.START
        }
    }
    lateinit var dialog: Dialog
    dialog = kit.dialog(title, null, kit.vertical().apply { addView(input, kit.fill()) }, listOf(
        DialogAction("ذخیره", ButtonKind.PRIMARY, dismiss = false) { dialog.dismiss(); onValue(input.text.toString().trim()) },
        DialogAction("انصراف")
    ))
    dialog.show()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
}

/** One choice out of a list, as a grid of chips. */
fun Screen.optionPicker(title: String, options: List<String>, selected: Int, columns: Int = 2, onPick: (Int) -> Unit) {
    lateinit var dialog: Dialog
    val grid = kit.grid(columns, options.mapIndexed { i, o ->
        kit.chip(o, i == selected, ButtonKind.CHIP_GOLD) { dialog.dismiss(); onPick(i) }
    }, 6)
    dialog = kit.dialog(title, null, grid, listOf(DialogAction("انصراف")))
    dialog.show()
}

/** Several roles at once (toggle chips grouped by side). */
fun Screen.multiRolePicker(title: String, initial: Collection<MafiaRole>, onDone: (List<MafiaRole>) -> Unit) {
    val chosen = LinkedHashSet(initial)
    var query = ""
    val box = kit.vertical()
    fun fill() {
        box.removeAllViews()
        MafiaSide.values().forEach { s ->
            val roles = MafiaRoleRegistry.available().filter { it.side == s && SearchText.matches(query, it.faName, it.defaultName) }
            if (roles.isEmpty()) return@forEach
            box.addView(kit.text(s.title, TextStyle.LABEL_BOLD, s.textColor()))
            box.addView(kit.flow(roles.map { r ->
                kit.chip(r.faName, r in chosen, ButtonKind.CHIP_GOLD) { if (!chosen.remove(r)) chosen += r; fill() }
            }, 4), kit.spaced(8))
        }
        if (box.childCount == 0) box.addView(kit.text("نقشی پیدا نشد", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
    }
    fill()
    val body = kit.vertical().apply {
        addView(kit.searchBox("جست‌وجوی نقش") { q -> query = q; fill() }, kit.spaced(8))
        addView(box)
    }
    kit.dialog(title, null, body, listOf(DialogAction("تأیید", ButtonKind.PRIMARY) { onDone(chosen.toList()) }, DialogAction("انصراف"))).show()
}

/** Several last-move cards at once. */
fun Screen.multiCardPicker(title: String, initial: Collection<MafiaLastCard>, onDone: (List<MafiaLastCard>) -> Unit) {
    val chosen = LinkedHashSet(initial)
    var query = ""
    val box = kit.vertical()
    fun fill() {
        box.removeAllViews()
        box.addView(kit.flow(MafiaLastCard.values().filter { SearchText.matches(query, it.faName, it.tagline) }.map { c ->
            kit.chip(c.faName.replace("\n", " "), c in chosen, ButtonKind.CHIP_GOLD) { if (!chosen.remove(c)) chosen += c; fill() }
        }, 4))
    }
    fill()
    val body = kit.vertical().apply {
        addView(kit.searchBox("جست‌وجوی کارت") { q -> query = q; fill() }, kit.spaced(8))
        addView(box)
    }
    kit.dialog(title, null, body, listOf(DialogAction("تأیید", ButtonKind.PRIMARY) { onDone(chosen.toList()) }, DialogAction("انصراف"))).show()
}

private fun Screen.valueRow(title: String, value: String, onClick: () -> Unit): View =
    kit.settingRow(title, value.ifBlank { "—" }, kit.icon(RoyalIcon.EDIT, Royal.gold, 20), onClick)

private fun Screen.lineupText(m: Map<MafiaRole, Int>): String =
    m.filter { it.value > 0 }.entries.joinToString("، ") { (r, c) -> r.faName + if (c > 1) " ×" + kit.n(c) else "" }

/** Draft setup from a scenario: its settings, the reference line-up for the chosen players and its cards. */
fun Screen.startScenario(s: MafiaScenario) {
    if (Gate.itemLocked("mafia_scenarios", s.id)) { Gate.lockedItem(this, "سناریوی " + s.name); return }
    val d = host.mafia.draft()
    MafiaScenarios.applyTo(d, s)
    if (s.id != MafiaScenarios.FREE) {
        d.roleCounts.clear(); d.options.clear()
        s.lineup(d.players.size)?.let { d.roleCounts.putAll(it) }
        d.deck.clear()
        if (!s.setup.noCards) {
            val cards = if (d.roleCounts.isEmpty()) s.cards else MafiaScenarios.matchMind(s.cards, d.roleCounts.keys)
            cards.filter { MafiaCardRules.usable(it) }.forEach { d.deck += MafiaCardState(it) }
        }
    }
    d.players.forEach { it.role = null; it.seen = false }
    host.mafia.saveDraft(d)
    host.push(MafiaDesignScreen(host, if (d.players.size >= 4) 1 else 0))
}

// ------------------------------------------------------------------ scenario list

/** «انتخاب سناریو»: built-in scenarios (families together), the narrator's own ones and «سناریوی آزاد». */
class MafiaScenarioListScreen(host: MainActivity, private val browse: Boolean = false) : Screen(host) {
    private var query = ""

    /** A scenario is found by its name, summary or the names of its roles. */
    private fun searchText(s: MafiaScenario) = s.name + " " + s.summary + " " + s.pool.joinToString(" ") { it.faName }

    override fun build(): View {
        MafiaRoleRegistry.activate(null)
        val saved = host.mafia.scenarios()
        val list = MafiaScenarios.all(saved)
        return scaffold(if (browse) "سناریوها" else "انتخاب سناریو", "${kit.n(list.size)} سناریو  •  روی هر سناریو بزنید",
            actions = listOf(kit.iconButton(RoyalIcon.USER, "نقش‌ها و قوانین", ButtonKind.SECONDARY, 44) { host.push(MafiaRolesScreen(host)) }),
            bottom = kit.button("سناریوی خودت را بساز", ButtonKind.PRIMARY, RoyalIcon.PLUS, 52) { host.push(MafiaScenarioEditScreen(host, null)) }) {
            addView(kit.text("ترکیب نقش و کارت هر سناریو فقط پیشنهاد اولیه است و در صفحهٔ طراحی بازی قابل تغییر است.", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(8))
            val rows = ArrayList<Pair<View, String>>()
            val headers = ArrayList<View>()
            val searchSlot = FrameLayout(host)
            addView(searchSlot, kit.spaced(8))
            val shownFamilies = HashSet<String>()
            list.forEach { s ->
                val fam = s.family
                if (fam != null) {
                    if (!shownFamilies.add(fam)) return@forEach
                    addView(kit.section("خانوادهٔ $fam", RoyalIcon.CROWN).also { headers += it })
                    list.filter { it.family == fam }.forEach { f -> addView(row(f, saved).also { rows += it to searchText(f) }) }
                    addView(kit.gap(4).also { headers += it })
                } else addView(row(s, saved).also { rows += it to searchText(s) })
            }
            searchSlot.addView(kit.searchFilter("جست‌وجوی سناریو یا نقش", rows, query) { q ->
                query = q
                headers.forEach { it.visibility = if (q.isBlank()) View.VISIBLE else View.GONE }
            })
            if (query.isNotBlank()) headers.forEach { it.visibility = View.GONE }
        }
    }

    private fun row(s: MafiaScenario, saved: List<MafiaScenario>): View = kit.panel(if (s.id == MafiaScenarios.FREE) PanelStyle.SELECTED else PanelStyle.NORMAL, 12).apply {
        layoutParams = kit.spaced(6)
        addView(kit.horizontal().apply {
            addView(seal(if (s.custom) MafiaGlyph.BOOK else if (s.id == MafiaScenarios.FREE) MafiaGlyph.CARDS else MafiaGlyph.SCROLL, null, 44))
            addView(kit.hgap(10))
            addView(kit.vertical().apply {
                addView(kit.text(s.name, TextStyle.BODY_BOLD, Royal.goldLight, maxLines = 2))
                addView(kit.text(s.summary, TextStyle.CAPTION, Royal.muted, maxLines = 2))
                val tags = ArrayList<View>()
                if (s.custom) tags += kit.badge("سناریوی شما", Royal.turquoise)
                else if (saved.any { it.id == s.id }) tags += kit.badge("ویرایش‌شده", Royal.turquoise)
                if (Gate.itemLocked("mafia_scenarios", s.id)) tags += Gate.badge(this@MafiaScenarioListScreen)
                if (s.presets.isNotEmpty()) tags += kit.badge("ترکیب مرجع: " + s.presets.keys.sorted().joinToString("، ") { kit.n(it) } + " نفر", Royal.gold)
                if (tags.isNotEmpty()) addView(kit.flow(tags, 4))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        isClickable = true
        contentDescription = s.name
        setOnClickListener { kit.tap(it); host.push(MafiaScenarioScreen(host, s.id)) }
    }
}

// ------------------------------------------------------------------ scenario detail

class MafiaScenarioScreen(host: MainActivity, private val id: String) : Screen(host) {
    override fun build(): View {
        val saved = host.mafia.scenarios()
        val s = MafiaScenarios.find(id, saved) ?: return scaffold("سناریو پیدا نشد") {}
        MafiaRoleRegistry.activate(s)
        val edited = !s.custom && saved.any { it.id == s.id }
        return scaffold(s.name, s.summary,
            actions = listOf(kit.iconButton(RoyalIcon.EDIT, "ویرایش سناریو", ButtonKind.SECONDARY, 44) { host.push(MafiaScenarioEditScreen(host, s.id)) }),
            bottom = kit.button("شروع با این سناریو", ButtonKind.PRIMARY, RoyalIcon.PLAY, 52) { startScenario(s) }) {
            addView(kit.section("قوانین", RoyalIcon.RULES))
            addView(kit.panel(PanelStyle.FLAT, 12).apply {
                layoutParams = kit.spaced(8)
                addView(kit.text(s.rules.ifBlank { "قانونی ثبت نشده است" }, TextStyle.BODY, Royal.ivory))
            })
            if (s.pool.isNotEmpty()) {
                addView(kit.section("نقش‌های سناریو (${kit.n(s.pool.size)})", RoyalIcon.PLAYERS))
                addView(kit.text("روی هر نقش بزنید تا قوانینش را ببینید یا ویرایش کنید.", TextStyle.CAPTION, Royal.muted))
                addView(kit.flow(s.pool.map { r ->
                    kit.chip(r.faName, false, ButtonKind.CHIP_GOLD) {
                        roleInfo(r, extraActions = listOf(DialogAction("ویرایش قوانین نقش", ButtonKind.PRIMARY) { host.push(MafiaRoleEditScreen(host, r)) }))
                    }.apply { setTextColor(r.side.textColor()) }
                }, 4), kit.spaced(10))
            }
            if (s.presets.isNotEmpty()) {
                addView(kit.section("ترکیب مرجع", RoyalIcon.CARDS))
                addView(kit.text("فقط پیشنهاد است؛ برای هر تعداد بازیکن، راوی می‌تواند نقش کم، اضافه یا جایگزین کند.", TextStyle.CAPTION, Royal.muted))
                s.presets.toSortedMap().forEach { (n, m) ->
                    addView(kit.panel(PanelStyle.FLAT, 10).apply {
                        layoutParams = kit.spaced(6)
                        addView(kit.text("${kit.n(n)} نفر", TextStyle.BODY_BOLD, Royal.goldLight))
                        addView(kit.text(lineupText(m), TextStyle.LABEL, Royal.ivory))
                    })
                }
            }
            addView(kit.section("کارت‌های حرکت آخر", RoyalIcon.CARDS))
            if (s.setup.noCards) addView(kit.text("این سناریو کارت حرکت آخر ندارد.", TextStyle.LABEL, Royal.muted), kit.spaced(8))
            else if (s.cards.isEmpty()) addView(kit.text("کارت مرجعی ندارد؛ در طراحی بازی کارت‌ها را انتخاب کنید.", TextStyle.LABEL, Royal.muted), kit.spaced(8))
            else addView(kit.flow(s.cards.map { c -> kit.chip(c.faName.replace("\n", " "), false, ButtonKind.CHIP_GOLD) { cardInfo(c) } }, 4), kit.spaced(10))
            addView(kit.section("تنظیمات سناریو", RoyalIcon.SLIDERS))
            addView(kit.panel(PanelStyle.FLAT, 12).apply {
                layoutParams = kit.spaced(8)
                setupLines(s).forEach { addView(kit.text("• $it", TextStyle.LABEL, Royal.ivory)) }
            })
            addView(kit.horizontal().apply {
                layoutParams = kit.spaced(8)
                addView(kit.weight(kit.button("ویرایش سناریو", ButtonKind.SECONDARY, RoyalIcon.EDIT, 44) { host.push(MafiaScenarioEditScreen(host, s.id)) }))
                addView(kit.hgap(6))
                addView(kit.weight(kit.button("کپی به سناریوی جدید", ButtonKind.SECONDARY, RoyalIcon.PLUS, 44) {
                    val copy = s.copy(id = "u" + System.currentTimeMillis(), name = s.name + " (کپی)", custom = true)
                    host.mafia.saveScenario(copy)
                    host.replace(MafiaScenarioScreen(host, copy.id))
                }))
            })
            if (edited) addView(kit.button("بازگردانی سناریوی پیش‌فرض", ButtonKind.GHOST, RoyalIcon.UNDO, 44) {
                kit.confirm("بازگردانی", "تغییرات شما در این سناریو حذف و نسخهٔ اصلی برگردانده شود؟", "بازگردانی", true) { host.mafia.deleteScenario(s.id); host.refresh() }
            }, kit.spaced(8))
            if (s.custom) addView(kit.button("حذف سناریو", ButtonKind.DANGER, RoyalIcon.TRASH, 44) {
                kit.confirm("حذف سناریو", "سناریوی «${s.name}» حذف شود؟", "حذف", true) { host.mafia.deleteScenario(s.id); host.pop() }
            }, kit.spaced(8))
        }
    }

    private fun setupLines(s: MafiaScenario): List<String> {
        val st = s.setup
        val out = ArrayList<String>()
        out += if (st.challenge) "چالش دارد" else "بدون چالش"
        out += if (st.noNight) "بدون فاز شب" else "فاز شب دارد"
        if (st.noMafiaIntro) out += "مافیاها در معارفه همدیگر را نمی‌شناسند"
        out += "استعلام وضعیت: " + (st.queryCount?.let { if (it == 0) "ندارد" else kit.n(it) + " بار" } ?: "طبق تنظیمات راوی")
        out += "حد ورود به دفاع: " + when {
            st.thresholds.isNotEmpty() -> st.thresholds.entries.sortedByDescending { it.key }.joinToString("، ") { "${kit.n(it.key)} نفر ← ${kit.n(it.value)}" }
            st.edge == "down" -> "نصف آرا"
            st.edge == "up" -> "بیش از نصف آرا"
            else -> "طبق تنظیمات راوی"
        }
        st.capo?.let { out += if (it) "روز اول: تفنگ کاپو" else "بدون تفنگ کاپو" }
        if (s.names.isNotEmpty()) out += "نام نقش‌ها در این سناریو: " + s.names.entries.joinToString("، ") { "${it.key.defaultName} ← ${it.value}" }
        return out
    }
}

// ------------------------------------------------------------------ scenario editor

/** Edits a built-in scenario (saved as the narrator's version) or creates / edits his own scenario. */
class MafiaScenarioEditScreen(host: MainActivity, id: String?) : Screen(host) {
    override val featureKey: String get() = "mafia_custom"
    private val isNew = id == null
    private var s: MafiaScenario = id?.let { host.mafia.scenario(it) }
        ?: MafiaScenario("u" + System.currentTimeMillis(), "سناریوی من", "سناریوی ساخت خودم", "", emptyList(), custom = true, auto = true)

    override fun build(): View {
        MafiaRoleRegistry.activate(null)
        return scaffold(if (isNew) "سناریوی خودت" else "ویرایش سناریو", s.name,
            bottom = kit.button("ذخیرهٔ سناریو", ButtonKind.PRIMARY, RoyalIcon.SAVE, 52) {
                host.mafia.saveScenario(s)
                kit.toast("سناریو ذخیره شد")
                if (isNew) host.replace(MafiaScenarioScreen(host, s.id)) else host.pop()
            }) {
            addView(kit.section("مشخصات", RoyalIcon.RULES))
            addView(valueRow("نام سناریو", s.name) { kit.textPrompt("نام سناریو", s.name) { s = s.copy(name = it); host.refresh() } })
            addView(valueRow("خلاصه", s.summary) { longPrompt("خلاصه", s.summary, false, "") { s = s.copy(summary = it); host.refresh() } })
            addView(valueRow("قوانین سناریو", s.rules.take(160)) { longPrompt("قوانین سناریو", s.rules, true, "هر قانون در یک خط") { s = s.copy(rules = it); host.refresh() } })

            addView(kit.section("نقش‌های سناریو (${kit.n(s.pool.size)})", RoyalIcon.PLAYERS))
            addView(kit.text("نقش‌هایی که در طراحی بازی اول نمایش داده می‌شوند. روی هر نقش بزنید تا نام یا ترتیب بیداری‌اش در این سناریو را تغییر دهید.", TextStyle.CAPTION, Royal.muted))
            if (s.pool.isNotEmpty()) addView(kit.flow(s.pool.map { r ->
                val label = (s.names[r] ?: r.faName) + (s.wake[r]?.let { " • " + kit.n(it) } ?: "")
                kit.chip(label, false, ButtonKind.CHIP_GOLD) { roleInScenario(r) }.apply { setTextColor(r.side.textColor()) }
            }, 4), kit.spaced(6))
            addView(kit.horizontal().apply {
                layoutParams = kit.spaced(10)
                addView(kit.weight(kit.button("انتخاب نقش‌ها", ButtonKind.SECONDARY, RoyalIcon.PLAYERS, 44) {
                    multiRolePicker("نقش‌های سناریو", s.pool) { picked -> s = s.copy(pool = picked); host.refresh() }
                }))
                addView(kit.hgap(6))
                addView(kit.weight(kit.button("افزودن نقش جدید", ButtonKind.SECONDARY, RoyalIcon.PLUS, 44) { host.push(MafiaRoleEditScreen(host, null)) }))
            })

            addView(kit.section("ترکیب مرجع", RoyalIcon.CARDS))
            addView(kit.text("پیشنهاد برای هر تعداد بازیکن؛ برای تعدادهای دیگر نزدیک‌ترین ترکیب کم یا زیاد می‌شود.", TextStyle.CAPTION, Royal.muted))
            s.presets.toSortedMap().forEach { (n, m) ->
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                    setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
                    layoutParams = kit.spaced(6)
                    addView(kit.vertical().apply {
                        addView(kit.text("${kit.n(n)} نفر  (${kit.n(m.values.sum())} نقش)", TextStyle.BODY_BOLD, Royal.goldLight))
                        addView(kit.text(lineupText(m), TextStyle.CAPTION, Royal.ivory))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(kit.iconButton(RoyalIcon.EDIT, "ویرایش", ButtonKind.CHIP, 38) { presetDialog(n, m) })
                    addView(kit.hgap(4))
                    addView(kit.iconButton(RoyalIcon.TRASH, "حذف", ButtonKind.CHIP, 38) { s = s.copy(presets = s.presets - n); host.refresh() })
                })
            }
            addView(kit.button("افزودن ترکیب مرجع", ButtonKind.GHOST, RoyalIcon.PLUS, 44) {
                kit.numberPrompt("تعداد بازیکنان", 10, false) { n -> if (n in 3..60) presetDialog(n, s.lineup(n) ?: emptyMap()) else kit.toast("تعداد معتبر نیست") }
            }, kit.spaced(10))
            addView(kit.switchRow("ساخت خودکار ترکیب", "برای تعدادهای بدون ترکیب مرجع، از نقش‌های سناریو ساخته شود", s.auto) { s = s.copy(auto = it) })

            addView(kit.section("کارت‌های حرکت آخر (${kit.n(s.cards.size)})", RoyalIcon.CARDS))
            if (s.cards.isNotEmpty()) addView(kit.flow(s.cards.map { kit.badge(it.faName.replace("\n", " "), Royal.gold) }, 4), kit.spaced(6))
            addView(kit.button("انتخاب کارت‌ها", ButtonKind.SECONDARY, RoyalIcon.CARDS, 44) {
                multiCardPicker("کارت‌های مرجع سناریو", s.cards) { s = s.copy(cards = it); host.refresh() }
            }, kit.spaced(6))
            addView(kit.switchRow("بدون کارت حرکت آخر", null, s.setup.noCards) { s = s.copy(setup = s.setup.copy(noCards = it)) })

            addView(kit.section("قوانین روز و شب", RoyalIcon.SLIDERS))
            addView(kit.switchRow("چالش دارد", null, s.setup.challenge) { s = s.copy(setup = s.setup.copy(challenge = it)) })
            addView(kit.switchRow("بدون فاز شب", "بازی فقط با روزها ادامه پیدا می‌کند", s.setup.noNight) { s = s.copy(setup = s.setup.copy(noNight = it)) })
            addView(kit.switchRow("مافیاها در معارفه همدیگر را نمی‌شناسند", null, s.setup.noMafiaIntro) { s = s.copy(setup = s.setup.copy(noMafiaIntro = it)) })
            val q = listOf(null, 0, 1, 2, 3, 4)
            addView(kit.choiceRow("استعلام وضعیت", null, q.mapIndexed { i, v -> i to (v?.let { if (it == 0) "ندارد" else kit.n(it) } ?: "پیش‌فرض") },
                q.indexOf(s.setup.queryCount).coerceAtLeast(0)) { s = s.copy(setup = s.setup.copy(queryCount = q[it])) })
            val edges = listOf(null, "down", "up")
            addView(kit.choiceRow("حد ورود به دفاع", null, listOf(0 to "پیش‌فرض", 1 to "نصف", 2 to "بیش از نصف"),
                edges.indexOf(s.setup.edge).coerceAtLeast(0)) { s = s.copy(setup = s.setup.copy(edge = edges[it])) })
            addView(valueRow("حد نصاب به ازای تعداد زنده‌ها", s.setup.thresholds.entries.sortedByDescending { it.key }.joinToString("، ") { "${kit.n(it.key)}:${kit.n(it.value)}" }) {
                longPrompt("حد نصاب (تعداد زنده:رأی)", s.setup.thresholds.entries.sortedByDescending { it.key }.joinToString(",") { "${it.key}:${it.value}" }, false, "مثلاً 10:4,9:4,7:3") { raw ->
                    val th = PersianText.toLatin(raw).split(",", "،").mapNotNull { e -> val p = e.trim().split(":"); if (p.size == 2) p[0].toIntOrNull()?.let { a -> p[1].toIntOrNull()?.let { a to it } } else null }.toMap()
                    s = s.copy(setup = s.setup.copy(thresholds = th)); host.refresh()
                }
            })
            val capo = listOf(null, true, false)
            addView(kit.choiceRow("تفنگ کاپو روز اول", null, listOf(0 to "پیش‌فرض", 1 to "دارد", 2 to "ندارد"),
                capo.indexOf(s.setup.capo).coerceAtLeast(0)) { s = s.copy(setup = s.setup.copy(capo = capo[it])) })
        }
    }

    /** Name and night order of one role inside this scenario only. */
    private fun roleInScenario(r: MafiaRole) {
        val name = kit.field("نام در این سناریو (خالی = ${r.faName})", s.names[r] ?: "")
        val wake = kit.field("ترتیب بیداری (خالی = پیش‌فرض ${kit.n(r.wakeOrder)})", s.wake[r]?.toString() ?: "", numeric = true)
        val body = kit.vertical().apply {
            addView(name, kit.spaced(8)); addView(wake, kit.spaced(4))
            addView(kit.text("عدد کمتر یعنی زودتر بیدار می‌شود.", TextStyle.CAPTION, Royal.muted))
        }
        kit.dialog(r.faName, null, body, listOf(
            DialogAction("ذخیره", ButtonKind.PRIMARY) {
                val n = name.text.toString().trim()
                val w = PersianText.parseInt(wake.text.toString())
                s = s.copy(names = if (n.isEmpty()) s.names - r else s.names + (r to n), wake = if (w == null) s.wake - r else s.wake + (r to w))
                host.refresh()
            },
            DialogAction("قوانین نقش") { host.push(MafiaRoleEditScreen(host, r)) },
            DialogAction("حذف از سناریو", ButtonKind.DANGER) { s = s.copy(pool = s.pool - r, names = s.names - r, wake = s.wake - r); host.refresh() },
            DialogAction("انصراف")
        )).show()
    }

    private fun presetDialog(n: Int, start: Map<MafiaRole, Int>) {
        val counts = LinkedHashMap(start)
        val roles = (s.pool + start.keys).distinct()
        val total = kit.text("", TextStyle.BODY_BOLD, Royal.goldLight, Gravity.CENTER)
        fun sum() { total.text = "${kit.n(counts.values.sum())} نقش از ${kit.n(n)} بازیکن" }
        sum()
        val body = kit.vertical().apply {
            addView(total, kit.spaced(6))
            if (roles.isEmpty()) addView(kit.text("ابتدا نقش‌های سناریو را انتخاب کنید.", TextStyle.LABEL, Royal.muted))
            roles.forEach { r ->
                addView(kit.stepperRow(r.faName, r.side.title, counts[r] ?: 0, 0, if (r.isMulti) n else 1, 1) { v ->
                    if (v == 0) counts.remove(r) else counts[r] = v; sum()
                })
            }
        }
        kit.dialog("ترکیب مرجع ${kit.n(n)} نفره", null, body, listOf(
            DialogAction("ذخیره", ButtonKind.PRIMARY) { s = s.copy(presets = s.presets + (n to LinkedHashMap(counts)), pool = (s.pool + counts.keys).distinct()); host.refresh() },
            DialogAction("انصراف")
        )).show()
    }
}

// ------------------------------------------------------------------ role editor

/** «افزودن نقش» / «ویرایش قوانین نقش»: every rule of a role, for existing roles and the narrator's own roles. */
class MafiaRoleEditScreen(host: MainActivity, role: MafiaRole?) : Screen(host) {
    override val featureKey: String get() = "mafia_custom"
    private val slot: MafiaRole? = role ?: MafiaRoleRegistry.nextFreeSlot()
    private val isNew = role == null
    private var d: MafiaRoleDef = slot?.let { MafiaRoleRegistry.user[it] } ?: if (isNew) MafiaRoleDef(name = "نقش جدید", side = MafiaSide.CITY,
        glyph = MafiaGlyph.QUESTION, wakes = true, wakeOrder = 50, ability = MafiaAbility.RECORD, min = 1, max = 1, uses = 0, nights = MafiaNights.EVERY) else MafiaRoleDef()

    /** Value the role has without this screen's edits (built-in data or the original role). */
    private val base: MafiaRoleDef? get() = slot?.let { MafiaGenericRoles.builtin[it] }

    override fun build(): View {
        val r = slot ?: return scaffold("افزودن نقش") { addView(kit.text("ظرفیت نقش‌های سفارشی پر شده است.", TextStyle.BODY, Royal.muted, Gravity.CENTER)) }
        MafiaRoleRegistry.activate(null)
        val coded = !r.isDataRole
        val name = d.name ?: base?.name ?: r.defaultName
        val side = d.side ?: base?.side ?: r.defaultSide
        val glyph = d.glyph ?: base?.glyph ?: r.defaultGlyph
        val wakes = d.wakes ?: base?.wakes ?: (r.defaultWake > 0)
        val ability = d.ability ?: base?.ability
        val alt = d.altAbility ?: base?.altAbility
        return scaffold(if (isNew) "افزودن نقش" else "قوانین نقش", name,
            bottom = kit.button("ذخیرهٔ قوانین نقش", ButtonKind.PRIMARY, RoyalIcon.SAVE, 52) { save(r) }) {
            addView(kit.horizontal(Gravity.CENTER).apply {
                layoutParams = kit.spaced(8)
                addView(seal(glyph, side, 64))
                addView(kit.hgap(10))
                addView(kit.vertical().apply {
                    addView(kit.text(name, TextStyle.TITLE, side.textColor()))
                    addView(kit.badge(side.title, side.textColor()))
                })
            })

            addView(kit.section("هویت نقش", RoyalIcon.USER))
            addView(valueRow("نام نقش", name) { kit.textPrompt("نام نقش", name) { d = d.copy(name = it); host.refresh() } })
            addView(valueRow("ساید", side.title) {
                optionPicker("ساید نقش", MafiaSide.values().map { it.title }, side.ordinal, 3) { d = d.copy(side = MafiaSide.values()[it]); host.refresh() }
            })
            addView(kit.settingRow("نماد نقش", "برای تغییر بزنید", seal(glyph, side, 40)) { glyphPicker(side) })
            addView(valueRow("شعار کوتاه", d.tagline ?: base?.tagline ?: r.defaultTagline) {
                longPrompt("شعار کوتاه", d.tagline ?: "", false) { d = d.copy(tagline = it.ifBlank { null }); host.refresh() }
            })
            addView(valueRow("توضیح و قوانین کامل نقش", (d.explain ?: MafiaRoleText.explain(r)).take(200)) {
                longPrompt("توضیح و قوانین کامل نقش", d.explain ?: MafiaRoleText.explain(r), true) { d = d.copy(explain = it.ifBlank { null }); host.refresh() }
            })
            addView(valueRow("متن معارفه (شب معارفه)", (d.intro ?: MafiaRoleText.intro(r)).take(120)) {
                longPrompt("متن معارفه", d.intro ?: "", true) { d = d.copy(intro = it.ifBlank { null }); host.refresh() }
            })

            addView(kit.section("زمان بیداری", RoyalIcon.TIMER))
            addView(kit.switchRow("شب بیدار می‌شود", null, wakes) { d = d.copy(wakes = it); host.refresh() })
            addView(kit.stepperRow("ترتیب بیداری شب", "عدد کمتر = زودتر", d.wakeOrder ?: base?.wakeOrder ?: r.defaultWake.coerceAtLeast(1), 1, 99, 1) { d = d.copy(wakeOrder = it) })
            addView(kit.stepperRow("ترتیب در معارفه", "عدد کمتر = زودتر", d.introOrder ?: base?.introOrder ?: r.defaultIntroOrder.coerceIn(1, 299), 1, 299, 1) { d = d.copy(introOrder = it) })
            // Night timing of a data rule (an original role keeps its own timing until its night work is replaced).
            if (!coded || ability != null) {
                val nights = d.nights ?: base?.nights ?: MafiaNights.EVERY
                addView(valueRow("شب‌های فعالیت", nights.title) {
                    optionPicker("شب‌های فعالیت", MafiaNights.values().map { it.title }, nights.ordinal) { d = d.copy(nights = MafiaNights.values()[it]); host.refresh() }
                })
                addView(kit.switchRow("با تیم مافیا بیدار می‌شود", "کارش در نوبت گروه مافیا انتخاب می‌شود", d.withMafia ?: base?.withMafia ?: false) { d = d.copy(withMafia = it) })
            }

            addView(kit.section("کار شب", RoyalIcon.SLIDERS))
            if (coded) addView(kit.text("کار شب این نقش از پیش تعریف شده است. اگر «کار شب» دیگری انتخاب کنید، قانون شما جایگزین آن می‌شود؛ «قانون اصلی نقش» آن را برمی‌گرداند.", TextStyle.CAPTION, Royal.muted))
            val abilities = MafiaAbility.values().toList()
            val abilityLabels = (if (coded) listOf("قانون اصلی نقش") else emptyList()) + abilities.map { it.title }
            val abilityIndex = if (ability == null) 0 else abilities.indexOf(ability) + (if (coded) 1 else 0)
            addView(valueRow("کار شب", if (ability == null) "قانون اصلی نقش" else ability.title + " — " + ability.hint) {
                optionPicker("کار شب", abilityLabels, abilityIndex) { i ->
                    d = d.copy(ability = if (coded && i == 0) null else abilities[i - if (coded) 1 else 0])
                    if (coded && i == 0) d = MafiaRoleDef.fromJson(d.toJson().apply { remove("ability"); remove("alt") })
                    host.refresh()
                }
            })
            if (ability != null || !coded) {
                addView(valueRow("کار جایگزین (به جای کار اصلی)", alt?.title ?: "ندارد") {
                    optionPicker("کار جایگزین", listOf("ندارد") + abilities.map { it.title }, if (alt == null) 0 else abilities.indexOf(alt) + 1) { i ->
                        d = d.copy(altAbility = if (i == 0) MafiaAbility.NONE else abilities[i - 1]); host.refresh()
                    }
                })
                addView(kit.stepperRow("حداقل تعداد هدف", null, d.min ?: base?.min ?: 1, 0, 5, 1) { d = d.copy(min = it) })
                addView(kit.stepperRow("حداکثر تعداد هدف", null, d.max ?: base?.max ?: 1, 1, 5, 1) { d = d.copy(max = it) })
                addView(kit.stepperRow("تعداد استفاده در کل بازی", "۰ = نامحدود", d.uses ?: base?.uses ?: 0, 0, 20, 1) { d = d.copy(uses = it) })
                addView(kit.switchRow("می‌تواند خودش را انتخاب کند", null, d.selfTarget ?: base?.selfTarget ?: false) { d = d.copy(selfTarget = it) })
                addView(kit.switchRow("فقط روی خودش", null, d.selfOnly ?: base?.selfOnly ?: false) { d = d.copy(selfOnly = it) })
            }
            addView(valueRow("متن بیدار کردن (راوی)", d.prompt ?: base?.prompt ?: "پیش‌فرض") {
                longPrompt("متن بیدار کردن", d.prompt ?: "", true) { d = d.copy(prompt = it.ifBlank { null }); host.refresh() }
            })

            addView(kit.section("اقدام روز", RoyalIcon.PLAY))
            val day = d.dayAbility ?: base?.dayAbility ?: MafiaDayAbility.NONE
            addView(valueRow("اقدام روز", day.title) {
                optionPicker("اقدام روز", MafiaDayAbility.values().map { it.title }, day.ordinal) { d = d.copy(dayAbility = MafiaDayAbility.values()[it]); host.refresh() }
            })
            if (day != MafiaDayAbility.NONE) addView(kit.stepperRow("تعداد اقدام روز", "در کل بازی", d.dayUses ?: base?.dayUses ?: 1, 1, 20, 1) { d = d.copy(dayUses = it) })

            addView(kit.section("دفاع و استعلام", RoyalIcon.EYE))
            addView(kit.switchRow("در شب نامیرا است", "شلیک شب او را خارج نمی‌کند", d.nightImmune ?: base?.nightImmune ?: false) { d = d.copy(nightImmune = it) })
            val look = d.inquiry ?: base?.inquiry ?: InquiryLook.NORMAL
            addView(kit.choiceRow("نتیجهٔ استعلام", null, InquiryLook.values().mapIndexed { i, l -> i to l.title }, look.ordinal) { d = d.copy(inquiry = InquiryLook.values()[it]) })
            addView(kit.stepperRow("تعداد زره", "هر زره یک شلیک را دفع می‌کند", d.armor ?: base?.armor ?: 0, 0, 5, 1) { d = d.copy(armor = it) })

            addView(kit.gap(6))
            if (r.isCustomSlot && !isNew) addView(kit.button("حذف این نقش", ButtonKind.DANGER, RoyalIcon.TRASH, 44) {
                kit.confirm("حذف نقش", "نقش «$name» حذف شود؟", "حذف", true) {
                    host.mafia.saveRoleDefs(host.mafia.roleDefs() - r); host.pop()
                }
            }, kit.spaced(8))
            else if (!isNew && MafiaRoleRegistry.user.containsKey(r)) addView(kit.button("بازگردانی قوانین پیش‌فرض", ButtonKind.GHOST, RoyalIcon.UNDO, 44) {
                kit.confirm("بازگردانی", "تغییرات شما روی نقش «$name» حذف شود؟", "بازگردانی", true) {
                    host.mafia.saveRoleDefs(host.mafia.roleDefs() - r); host.pop()
                }
            }, kit.spaced(8))
        }
    }

    private fun glyphPicker(side: MafiaSide) {
        lateinit var dialog: Dialog
        val grid = kit.grid(5, MafiaGlyph.values().map { gl ->
            seal(gl, side, 44).apply {
                isClickable = true
                contentDescription = gl.name
                setOnClickListener { kit.tap(it); dialog.dismiss(); d = d.copy(glyph = gl); host.refresh() }
            }
        }, 6)
        dialog = kit.dialog("نماد نقش", null, grid, listOf(DialogAction("انصراف")))
        dialog.show()
    }

    private fun save(r: MafiaRole) {
        val all = LinkedHashMap(host.mafia.roleDefs())
        all[r] = d
        host.mafia.saveRoleDefs(all)
        kit.toast("قوانین نقش ذخیره شد")
        host.pop()
    }
}
