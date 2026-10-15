package com.meysam.divanemtiaz

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/** Colours and emblem of «پانتومیم». */
object PantoLook {
    const val TINT = 0xFF6A3E9E.toInt()
    val glyph = MafiaGlyph.THEATER
    const val GOLD_TINT = 0xFFC4801C.toInt()
}

/** The word bank, loaded once from the app's assets (first use may load it in the background). */
fun Screen.pantoBank(): PantoBank = PantoBank.get { host.assets.open(PantoBank.ASSET) }

/** Starts loading the bank off the main thread so the first match opens instantly. */
fun Screen.preloadPantoBank() { Thread { runCatching { pantoBank() } }.start() }

private val Screen.pantoSounds: GameSounds get() = PantoSoundHolder.get(host)

private object PantoSoundHolder {
    private var sounds: GameSounds? = null
    fun get(host: MainActivity): GameSounds = sounds ?: GameSounds { host.spy.sound() }.also { sounds = it }
}

/** Big emblem card used at the top of the game pages. */
fun Screen.gameHero(glyph: MafiaGlyph, tint: Int, title: String, caption: String, action: View? = null): View =
    hubBanner(RoleSealView(host, glyph, tint), title, caption, action)

/** Square menu tile (icon, title, caption). */
fun Screen.menuTile(icon: RoyalIcon, title: String, caption: String, onClick: () -> Unit): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
    background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
    setPadding(kit.dp(6), kit.dp(13), kit.dp(6), kit.dp(15))
    addView(kit.icon(icon, Royal.gold, 30))
    addView(kit.gap(4))
    addView(kit.text(title, TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER, 1))
    addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 2))
    isClickable = true
    contentDescription = title
    setOnClickListener { kit.tap(it); onClick() }
}

/** «پانتومیم» home. */
class PantoHomeScreen(host: MainActivity) : Screen(host) {
    override val adZone: String? get() = "hub"
    override val featureKey: String get() = "panto"
    override fun build(): View {
        preloadPantoBank()
        val live = host.panto.latestUnfinished()
        return scaffold("پانتومیم", "داوری مسابقهٔ پانتومیم تیمی") {
            addView(gameHero(PantoLook.glyph, PantoLook.TINT, "پانتومیم", "۲۰ دستهٔ واژه • پنج نوع مسابقه و دور طلایی",
                kit.button("مسابقهٔ جدید", ButtonKind.PRIMARY, RoyalIcon.PLUS, 52) { host.push(PantoSetupScreen(host)) }))
            if (live != null) {
                addView(resumeStrip(RoleSealView(host, PantoLook.glyph, PantoLook.TINT), "ادامهٔ مسابقهٔ ${live.mode.title}",
                    live.standings.take(3).joinToString("  •  ") { "${live.teams[it].name} ${kit.n(live.teams[it].points)}" }) { host.push(PantoBoardScreen(host, live.id)) })
            }
            addView(kit.listGroup(listOf(
                kit.listRow(kit.iconChip(RoyalIcon.CARDS), "بانک واژه‌ها", "جست‌وجو در ۲۰ دسته و سطح‌ها") { host.push(PantoBankScreen(host)) },
                kit.listRow(kit.iconChip(RoyalIcon.EDIT), "کلمات من", "${kit.n(host.panto.userWords().size)} کلمه • از ۱۰ کلمه وارد مسابقه می‌شود") { host.push(PantoWordsScreen(host)) },
                kit.listRow(kit.iconChip(RoyalIcon.HISTORY), "مسابقه‌های گذشته", "${kit.n(host.panto.index().size)} مسابقه") { host.push(PantoMatchesScreen(host)) },
                kit.listRow(kit.iconChip(RoyalIcon.RULES), "قوانین", "قوانین مشترک و هر نوع مسابقه") { host.push(PantoRulesScreen(host)) }
            ), "ابزارها"))
            addView(kit.switchRow("صدای بازی", "صدای پاسخ درست و نادرست، ثانیه‌های آخر و زنگ پایان", host.spy.sound()) { host.spy.setSound(it) })
        }
    }
}

/** «مسابقهٔ جدید»: match type, teams and their people, rounds, time and the referee. */
class PantoSetupScreen(host: MainActivity) : Screen(host) {
    private var mode = PantoMode.NORMAL
    private val teams = ArrayList<PantoTeam>()
    private val defaults = host.panto.defaults()
    private var rounds = defaults.rounds
    private var manual = defaults.manualTime
    private var seconds = defaults.seconds
    private var referee = ""

    init {
        repeat(2) { addTeam() }
    }

    private fun addTeam() {
        val n = teams.size + 1
        teams += PantoTeam(if (mode.individual) "نفر ${PersianText.digits(n)}" else "تیم ${PersianText.digits(n)}", (n * 5 + 1) % Emblems.COUNT)
    }

    override fun build(): View = scaffold("مسابقهٔ جدید", "پانتومیم • ${mode.title}",
        bottom = kit.button("شروع مسابقه", ButtonKind.PRIMARY, RoyalIcon.PLAY, 54) { start() }) {
        addView(kit.section("نوع مسابقه", RoyalIcon.CARDS))
        addView(kit.grid(2, PantoMode.values().map { m ->
            kit.vertical().apply {
                background = PanelDrawable(kit.density, if (m == mode) PanelStyle.SELECTED else PanelStyle.NORMAL, 16f)
                setPadding(kit.dp(10), kit.dp(9), kit.dp(10), kit.dp(11))
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.text(m.title, TextStyle.HEADING, if (m == mode) Royal.turquoiseLight else Royal.goldLight)))
                    if (Gate.itemLocked("panto_modes", m.name)) addView(Gate.badge(this@PantoSetupScreen))
                })
                addView(kit.text(m.summary, TextStyle.CAPTION, Royal.muted, maxLines = 3))
                isClickable = true
                contentDescription = m.title
                setOnClickListener {
                    kit.tap(it)
                    if (Gate.itemLocked("panto_modes", m.name)) { Gate.lockedItem(this@PantoSetupScreen, "مسابقهٔ " + m.title); return@setOnClickListener }
                    val wasIndividual = mode.individual
                    mode = m
                    if (wasIndividual != m.individual) teams.forEachIndexed { i, t ->
                        if (t.members.isEmpty()) t.name = if (m.individual) "نفر ${PersianText.digits(i + 1)}" else "تیم ${PersianText.digits(i + 1)}"
                    }
                    host.refresh()
                }
            }
        }, 8), kit.spaced(10))
        addView(kit.section(if (mode.individual) "شرکت‌کنندگان (${kit.n(teams.size)})" else "تیم‌ها (${kit.n(teams.size)})", RoyalIcon.PLAYERS,
            kit.horizontal().apply {
                addView(kit.iconButton(RoyalIcon.MINUS, "کم کردن", ButtonKind.CHIP, 40) { if (teams.size > PantoEngine.MIN_TEAMS) { teams.removeAt(teams.lastIndex); host.refresh() } })
                addView(kit.hgap(6))
                addView(kit.iconButton(RoyalIcon.PLUS, "افزودن", ButtonKind.CHIP_GOLD, 40) { if (teams.size < PantoEngine.MAX_TEAMS) { addTeam(); host.refresh() } else kit.toast("حداکثر ${kit.n(PantoEngine.MAX_TEAMS)}") })
            }))
        teams.forEachIndexed { i, t -> addView(teamRow(i, t)) }
        addView(kit.horizontal().apply {
            layoutParams = kit.spaced(8)
            if (!mode.individual) {
                addView(kit.weight(kit.button("تقسیم تصادفی افراد", ButtonKind.SECONDARY, RoyalIcon.PLAYERS, 44) { randomSplit() }))
                addView(kit.hgap(6))
            }
            addView(kit.weight(kit.button("ترتیب تصادفی", ButtonKind.SECONDARY, RoyalIcon.SWAP, 44) { teams.shuffle(); host.refresh() }))
        })
        addView(kit.section("دورها و زمان", RoyalIcon.TIMER))
        if (mode == PantoMode.JANGI) {
            addView(kit.settingRow("تعداد دورها", "خودکار: یکی کمتر از تعداد تیم‌ها (${kit.n(teams.size - 1)} دور)", kit.badge("خودکار", Royal.turquoise)))
        } else {
            addView(kit.stepperRow("تعداد دورها", null, rounds, PantoEngine.MIN_ROUNDS, PantoEngine.MAX_ROUNDS, 1) { rounds = it })
        }
        if (mode == PantoMode.FIVE) {
            addView(kit.settingRow("زمان پاسخ", "برای هر سؤال", kit.badge("${kit.n(5)} ثانیه", Royal.gold)))
        } else {
            addView(kit.choiceRow("نوع زمان", when (mode) {
                PantoMode.NORMAL -> "خودکار: نسبت به سختی کلمه"
                PantoMode.SPEEDY -> "خودکار: نسبت به سختی کلمات"
                PantoMode.SEDA -> "خودکار: ۱ دقیقه"
                else -> "خودکار: نسبت به تعداد تیم‌ها"
            }, listOf(0 to "خودکار", 1 to "دستی"), if (manual) 1 else 0) { manual = it == 1; host.refresh() })
            if (manual) addView(kit.stepperRow("زمان دستی", "از ${kit.n(45)} تا ${kit.n(345)} ثانیه", seconds, PantoEngine.MIN_TIME, PantoEngine.MAX_TIME, PantoEngine.TIME_STEP,
                format = { "${kit.n(it)} ث" }) { seconds = it })
        }
        addView(kit.section("ناظر بی‌طرف (اختیاری)", RoyalIcon.EYE))
        addView(kit.settingRow(referee.ifBlank { "بدون ناظر" }, "یک نفر بی‌طرف برای داوری خطاها و پاسخ‌ها", kit.icon(RoyalIcon.EDIT, Royal.gold, 20)) {
            memberPicker(listOfNotNull(referee.takeIf { it.isNotBlank() }), 1, "ناظر بی‌طرف") { referee = it.firstOrNull() ?: ""; host.refresh() }
        })
    }

    /** Chosen people are dealt at random into the teams (as evenly as possible). */
    private fun randomSplit() {
        memberPicker(teams.flatMap { it.members }, 999, "افراد این مسابقه") { names ->
            if (names.size < teams.size) { kit.toast("دست‌کم ${kit.n(teams.size)} نفر انتخاب کنید"); return@memberPicker }
            PantoEngine.split(names, teams.size, Random.Default).forEachIndexed { i, group ->
                teams[i].members.clear(); teams[i].members += group; teams[i].size = group.size
            }
            kit.toast("${kit.n(names.size)} نفر به ${kit.n(teams.size)} تیم تقسیم شدند")
            host.refresh()
        }
    }

    private fun teamRow(i: Int, t: PantoTeam): View = kit.vertical().apply {
        background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
        setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
        layoutParams = kit.spaced(6)
        addView(kit.horizontal().apply {
            addView(kit.avatar(t.avatar, 44).apply {
                isClickable = true
                contentDescription = "عکس ${t.name}"
                setOnClickListener { avatarPicker(t.avatar) { picked -> t.avatar = picked; host.refresh() } }
            })
            addView(kit.hgap(8))
            addView(kit.vertical().apply {
                addView(kit.text(t.name, TextStyle.BODY_BOLD, Royal.goldLight, maxLines = 1))
                addView(kit.text(if (t.members.isEmpty()) (if (mode.individual) "از افراد انتخاب کنید یا نام بنویسید" else "${kit.n(t.size)} نفر • اعضا را از افراد انتخاب کنید (هر تعداد)")
                    else "${kit.n(t.members.size)} نفر: " + t.members.joinToString("، "), TextStyle.CAPTION, Royal.muted, maxLines = 3))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(kit.iconButton(RoyalIcon.EDIT, "نام ${t.name}", ButtonKind.CHIP, 40) {
                kit.textPrompt(if (mode.individual) "نام شرکت‌کننده" else "نام تیم", t.name) { n -> if (n.isNotBlank()) { t.name = n.trim(); host.refresh() } }
            })
            if (!mode.individual) {
                addView(kit.hgap(6))
                addView(kit.iconButton(RoyalIcon.FLAG, "انتخاب از تیم‌های ثبت‌شده", ButtonKind.CHIP, 40) {
                    rosterPicker(true) { e ->
                        val taken = teams.filterIndexed { j, _ -> j != i }.flatMap { it.members }.toSet()
                        t.name = e.name; t.avatar = e.avatar
                        t.members.clear(); t.members += e.members.filter { it !in taken }
                        if (t.members.isNotEmpty()) t.size = maxOf(2, t.members.size)
                        host.refresh()
                    }
                })
            }
            addView(kit.hgap(6))
            addView(kit.iconButton(RoyalIcon.PLAYERS, "اعضای ${t.name}", ButtonKind.CHIP_GOLD, 40) {
                val taken = teams.filterIndexed { j, _ -> j != i }.flatMap { it.members }.toSet()
                memberPicker(t.members, if (mode.individual) 1 else 999, if (mode.individual) "شرکت‌کننده" else "اعضای ${t.name}") { names ->
                    val clash = names.filter { it in taken }
                    if (clash.isNotEmpty()) kit.toast("${clash.joinToString("، ")} در تیم دیگری است")
                    t.members.clear(); t.members += names.filter { it !in taken }
                    if (t.members.isNotEmpty()) t.size = t.members.size
                    if (mode.individual && t.members.size == 1) {
                        t.name = t.members.first()
                        host.repo.roster().firstOrNull { it.name == t.name && !it.isTeam }?.let { e -> t.avatar = e.avatar }
                    }
                    host.refresh()
                }
            })
        })
        if (!mode.individual && t.members.isEmpty()) {
            addView(kit.gap(6))
            addView(kit.stepperRow("تعداد نفرات تیم", "بدون محدودیت؛ برای چرخش اجراکننده", t.size, 2, 99, 1) { t.size = it })
        }
    }

    private fun start() {
        if (Gate.itemLocked("panto_modes", mode.name)) { Gate.lockedItem(this, "مسابقهٔ " + mode.title); return }
        if (teams.map { it.name.trim() }.toSet().size != teams.size) { kit.toast("نام‌ها نباید تکراری باشند"); return }
        val id = System.currentTimeMillis()
        val m = PantoEngine.create(id, mode, teams.map { PantoTeam(it.name.trim(), it.avatar, ArrayList(it.members), size = if (mode.individual) 1 else it.people) }, rounds, if (manual && mode != PantoMode.FIVE) seconds else null)
        m.referee = referee
        host.panto.save(m)
        host.replace(PantoBoardScreen(host, id))
    }
}

/** Base of the match pages: loads and saves the match. */
abstract class PantoScreen(host: MainActivity, protected val matchId: Long) : Screen(host) {
    override val compactHeader: Boolean get() = true

    protected lateinit var m: PantoMatch
    protected val loaded: Boolean get() = ::m.isInitialized

    protected fun load(): Boolean {
        m = host.panto.match(matchId) ?: return false
        return true
    }

    protected fun store() { host.panto.save(m) }

    protected fun missing(): View = scaffold("پانتومیم") {
        addView(kit.text("این مسابقه پیدا نشد", TextStyle.BODY, Royal.muted, Gravity.CENTER))
    }

    protected fun roundTitle(): String = if (m.golden) "دور طلایی" else "دور ${kit.n(m.round)} از ${kit.n(m.rounds)}"

    /** Team chip with the green turn ring (battle and five-second modes). */
    protected fun turnChip(i: Int, active: Boolean): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        val t = m.teams[i]
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = kit.dpf(16f)
            setColor(Royal.alpha(if (active) Royal.turquoise else Royal.night, if (active) 0.22f else 0.5f))
            setStroke(kit.dp(if (active) 3 else 1), if (active) 0xFF35D07F.toInt() else Royal.alpha(Royal.gold, 0.4f))
        }
        setPadding(kit.dp(4), kit.dp(6), kit.dp(4), kit.dp(8))
        alpha = if (t.inGame) 1f else 0.4f
        addView(kit.avatar(t.avatar, 36))
        addView(kit.text(t.name, TextStyle.CAPTION, if (active) Royal.turquoiseLight else Royal.ivory, Gravity.CENTER, 1))
        addView(kit.text(kit.n(t.points), TextStyle.LABEL_BOLD, Royal.goldLight, Gravity.CENTER, 1))
    }

    protected fun rulesDialog() {
        kit.dialog("قوانین ${m.mode.title}", null, kit.vertical().apply {
            PantoEngine.rules(m.mode).forEach { addView(kit.text("•  $it", TextStyle.LABEL, Royal.ivory), kit.spaced(4)) }
            if (m.referee.isNotBlank()) addView(kit.text("ناظر بی‌طرف: ${m.referee}", TextStyle.LABEL_BOLD, Royal.turquoiseLight), kit.spaced(4))
        }, listOf(DialogAction("بستن"))).show()
    }
}

/** «صفحهٔ دورها»: round, standings, whose turn it is; starts each turn. */
class PantoBoardScreen(host: MainActivity, matchId: Long) : PantoScreen(host, matchId) {
    override fun build(): View {
        if (!load()) return missing()
        if (m.finished) {
            host.post(0) { host.replace(PantoResultScreen(host, matchId)) }
            return missing()
        }
        val cur = m.teams[m.current]
        val startLabel = when {
            m.turn != null -> "ادامهٔ نوبت ${m.teams[m.turn!!.team].name}"
            m.mode == PantoMode.JANGI -> "شروع ${roundTitle()}"
            else -> "نوبت ${cur.name}"
        }
        return scaffold("پانتومیم • ${m.mode.title}", roundTitle(),
            actions = listOf(kit.iconButton(RoyalIcon.RULES, "قوانین", ButtonKind.SECONDARY, 44) { rulesDialog() }),
            bottom = kit.vertical().apply {
                addView(kit.button(startLabel, ButtonKind.PRIMARY, RoyalIcon.PLAY, 54) { startTurn() })
                if (m.turn == null && m.mode != PantoMode.JANGI) {
                    addView(kit.gap(6))
                    addView(kit.button("این تیم نوبتش را بازی نمی‌کند (بدون امتیاز)", ButtonKind.GHOST, RoyalIcon.CLOSE, 42) {
                        kit.confirm("رد کردن نوبت", "${cur.name} این نوبت را بازی نمی‌کند و امتیازی نمی‌گیرد.", "رد کن") {
                            PantoEngine.skip(m); store(); host.refresh()
                        }
                    })
                }
            }) {
            addView(roundBanner())
            m.standings.forEachIndexed { place, i -> addView(teamRow(place, i)) }
            if (m.log.isNotEmpty()) {
                addView(kit.section("نوبت‌های گذشته", RoyalIcon.HISTORY))
                m.log.takeLast(12).reversed().forEach { l ->
                    addView(kit.settingRow(m.teams.getOrNull(l.team)?.name ?: "", (if (l.golden) "دور طلایی • " else "دور ${kit.n(l.round)} • ") + l.text,
                        kit.badge(if (l.points > 0) "+" + kit.n(l.points) else kit.n(l.points), if (l.points > 0) Royal.turquoise else Royal.muted)))
                }
            }
        }
    }

    override fun onShow() {
        if (loaded && m.golden && !m.goldenShown && !m.finished) {
            m.goldenShown = true
            store()
            pantoSounds.bell()
            kit.dialog("دور طلایی", "امتیاز ${m.inside.joinToString(" و ") { m.teams[it].name }} برابر شد. دور طلایی حذفی است و تا تعیین برنده تکرار می‌شود.",
                null, listOf(DialogAction("شروع دور طلایی", ButtonKind.PRIMARY))).show()
        }
    }

    private fun roundBanner(): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        background = PanelDrawable(kit.density, if (m.golden) PanelStyle.SELECTED else PanelStyle.RAISED, 20f)
        if (m.golden) background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF7A5A12.toInt(), 0xFF3A2A08.toInt())).apply { cornerRadius = kit.dpf(20f); setStroke(kit.dp(2), Royal.goldLight) }
        setPadding(kit.dp(12), kit.dp(12), kit.dp(12), kit.dp(14))
        layoutParams = kit.spaced(10)
        if (m.golden) addView(kit.icon(RoyalIcon.CROWN, Royal.goldLight, 34))
        addView(kit.text(roundTitle(), TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER))
        val sub = when (m.mode) {
            PantoMode.JANGI -> "همهٔ تیم‌ها با هم • ${kit.n(m.inside.size)} تیم در مسابقه"
            PantoMode.FIVE -> "نوبت ${m.teams[m.current].name}"
            else -> "نوبت ${m.teams[m.current].name} • اجراکننده: ${m.teams[m.current].performer}"
        }
        addView(kit.text(sub, TextStyle.BODY_BOLD, if (m.golden) Royal.ivory else Royal.turquoiseLight, Gravity.CENTER))
    }

    private fun teamRow(place: Int, i: Int): View = kit.horizontal().apply {
        val t = m.teams[i]
        val isTurn = i == m.current && m.mode != PantoMode.JANGI
        background = PanelDrawable(kit.density, if (isTurn) PanelStyle.SELECTED else PanelStyle.NORMAL, 16f)
        setPadding(kit.dp(10), kit.dp(7), kit.dp(10), kit.dp(10))
        layoutParams = kit.spaced(6)
        alpha = if (t.inGame) 1f else 0.55f
        addView(kit.text(kit.n(place + 1), TextStyle.HEADING, Royal.gold))
        addView(kit.hgap(8))
        addView(kit.avatar(t.avatar, 40))
        addView(kit.hgap(8))
        addView(kit.vertical().apply {
            addView(kit.text(t.name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
            val status = when {
                !t.inGame -> if (m.golden) "خارج از دور طلایی" else "حذف‌شده"
                isTurn -> "نوبت این تیم"
                else -> if (t.members.isEmpty()) "در مسابقه" else t.members.joinToString("، ")
            }
            addView(kit.text(status, TextStyle.CAPTION, if (isTurn) Royal.turquoiseLight else Royal.muted, maxLines = 2))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.text(kit.n(t.points), TextStyle.TITLE, Royal.goldLight))
    }

    private fun startTurn() {
        if (m.turn != null) { host.push(PantoPlayScreen(host, matchId)); return }
        if (m.mode.picksCategory) { host.push(PantoCategoryScreen(host, matchId)); return }
        val used = host.panto.used()
        if (PantoEngine.startTurn(m, pantoBank(), host.panto.userWords(), used, null, Random.Default) == null) { kit.toast("واژه‌ای برای این نوبت پیدا نشد"); return }
        host.panto.saveUsed(used)
        store()
        host.push(PantoPlayScreen(host, matchId))
    }
}

/** Category and level of the current team's turn (normal and speedy). */
class PantoCategoryScreen(host: MainActivity, matchId: Long) : PantoScreen(host, matchId) {
    private var query = ""

    override fun build(): View {
        if (!load()) return missing()
        val user = host.panto.userWords()
        val options = PantoEngine.options(m, user)
        val byCat = options.groupBy { it.choice.cat }
        return scaffold("انتخاب موضوع", "${m.teams[m.current].name} • ${roundTitle()}") {
            addView(kit.text("هر موضوع و سختی را هر تیم فقط یک بار در مسابقه می‌تواند انتخاب کند.", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(8))
            val rows = ArrayList<Pair<View, String>>()
            val slot = FrameLayout(host)
            addView(slot, kit.spaced(8))
            byCat[PantoCategories.GOLDEN]?.firstOrNull()?.let { g -> addView(goldenCard(g).also { rows += it to "موضوع طلایی" }) }
            byCat.forEach { (cat, opts) ->
                if (cat == PantoCategories.GOLDEN) return@forEach
                addView(categoryCard(cat, opts).also { rows += it to PantoCategories.name(cat) })
            }
            slot.addView(kit.searchFilter("جست‌وجوی دسته", rows, query) { query = it })
        }
    }

    /** The golden topic in one slim gold strip (crown, title and its terms). */
    private fun goldenCard(o: PantoEngine.Option): View = kit.horizontal().apply {
        background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF8A6418.toInt(), 0xFF3E2C08.toInt())).apply { cornerRadius = kit.dpf(14f); setStroke(kit.dp(1), Royal.goldLight) }
        setPadding(kit.dp(10), kit.dp(7), kit.dp(10), kit.dp(8))
        layoutParams = kit.spaced(6)
        alpha = if (o.enabled) 1f else 0.45f
        addView(kit.icon(RoyalIcon.CROWN, Royal.goldLight, 22))
        addView(kit.hgap(8))
        addView(kit.weight(kit.vertical().apply {
            addView(kit.text("موضوع طلایی", TextStyle.BODY_BOLD, Royal.goldLight))
            addView(kit.text(if (o.enabled) "پاسخ نادرست = خروج از مسابقه" else "این تیم موضوع طلایی را انتخاب کرده است", TextStyle.CAPTION, Royal.ivory, maxLines = 1))
        }))
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(kit.text(kit.n(30), TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
            addView(kit.text("${kit.n(3)} دقیقه", TextStyle.CAPTION, Royal.ivory, Gravity.CENTER, 1))
        })
        contentDescription = "موضوع طلایی"
        setOnClickListener {
            if (!o.enabled) { kit.toast("این تیم موضوع طلایی را قبلاً انتخاب کرده است"); return@setOnClickListener }
            kit.tap(it)
            kit.confirm("موضوع طلایی", "اگر به موضوع طلایی پاسخ درست ندهید، از ادامهٔ مسابقه خارج می‌شوید. ادامه می‌دهید؟", "انتخاب") { pick(o.choice) }
        }
    }

    /** One slim row per topic: its name, then a small tile per level (points, level and time). */
    private fun categoryCard(cat: Int, opts: List<PantoEngine.Option>): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, PanelStyle.NORMAL, 14f)
        setPadding(kit.dp(8), kit.dp(5), kit.dp(8), kit.dp(6))
        layoutParams = kit.spaced(5)
        gravity = Gravity.CENTER_VERTICAL
        addView(kit.vertical().apply {
            addView(kit.text(PantoCategories.name(cat), TextStyle.BODY_BOLD, Royal.goldLight, maxLines = 2))
            if (cat == PantoCategories.USER) addView(kit.text("کلمات شما", TextStyle.CAPTION, Royal.turquoiseLight, maxLines = 1))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f))
        opts.forEachIndexed { i, o ->
            if (i > 0) addView(kit.hgap(5))
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = ButtonDrawable(kit.density, if (o.enabled) ButtonKind.SECONDARY else ButtonKind.GHOST, 10f)
                setPadding(kit.dp(2), kit.dp(3), kit.dp(2), kit.dp(5))
                alpha = if (o.enabled) 1f else 0.4f
                addView(kit.text(kit.n(o.choice.points), TextStyle.HEADING, if (o.enabled) Royal.goldLight else Royal.muted, Gravity.CENTER, 1))
                addView(kit.text("${PantoCategories.levelNames[o.choice.level]} • ${kit.n(PantoEngine.seconds(m, o.choice))}ث", TextStyle.CAPTION, Royal.ivory, Gravity.CENTER, 1).apply {
                    textSize = kit.textSize(10.5f)
                })
                contentDescription = "${PantoCategories.name(cat)} ${o.choice.points}"
                setOnClickListener {
                    if (!o.enabled) { kit.toast("این موضوع را این تیم قبلاً بازی کرده است"); return@setOnClickListener }
                    kit.tap(it); pick(o.choice)
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun pick(c: PantoChoice) {
        val used = host.panto.used()
        if (PantoEngine.startTurn(m, pantoBank(), host.panto.userWords(), used, c, Random.Default) == null) { kit.toast("در این دسته واژه‌ای نیست"); return }
        host.panto.saveUsed(used)
        store()
        host.replace(PantoPlayScreen(host, matchId))
    }
}

/** The turn being played: big word, big timer, right / wrong / fault, per match type. */
class PantoPlayScreen(host: MainActivity, matchId: Long) : PantoScreen(host, matchId) {
    /** 0 = word hidden, 1 = word shown to the performer, 2 = timer started. */
    private var phase = 0
    /** The timer should run (kept across page rebuilds; the timer itself is recreated on each build). */
    private var wantRunning = false
    /** The performer hid the word while the clock runs (the phone can be put down). */
    private var wordHidden = false
    private var timer: Countdown? = null
    private var timeText: TextView? = null
    private var ring: RingTimerView? = null
    private var lastSecond = -1
    private var ended = false

    override fun build(): View {
        if (!load()) return missing()
        val t = m.turn ?: run { host.post(0) { host.pop() }; return missing() }
        if (m.mode != PantoMode.NORMAL && phase == 0) phase = 1
        val team = m.teams[t.team]
        timer?.pause()
        timer = Countdown(t.remainingMs, { ms -> onTick(ms) }, { onTimeUp() })
        if (wantRunning && !ended) host.post(0) { if (wantRunning && !ended) timer?.start() }
        return scaffold(if (m.mode == PantoMode.JANGI) roundTitle() else team.name, "${m.mode.title} • ${roundTitle()}" + (t.choice?.let { " • ${it.title}" } ?: ""),
            actions = listOf(kit.iconButton(RoyalIcon.RULES, "قوانین", ButtonKind.SECONDARY, 44) { rulesDialog() }),
            bottom = controls(t)) {
            if (m.mode == PantoMode.JANGI || m.mode == PantoMode.FIVE) {
                val active = if (m.mode == PantoMode.JANGI) m.inside.getOrNull(t.jangiPos) else t.team
                addView(kit.grid(minOf(6, m.teams.size).coerceAtLeast(3), m.teams.indices.map { turnChip(it, it == active) }, 6), kit.spaced(10))
            }
            addView(timerCard(t))
            addView(wordCard(t))
            if (m.mode == PantoMode.SPEEDY || m.mode == PantoMode.SEDA) {
                addView(kit.text("کلمهٔ ${kit.n((t.index + 1).coerceAtMost(t.words.size))} از ${kit.n(t.words.size)} • ${kit.n(t.correct)} درست", TextStyle.BODY_BOLD, Royal.turquoiseLight, Gravity.CENTER), kit.spaced(8))
            }
            if (m.mode != PantoMode.JANGI && m.mode != PantoMode.FIVE) {
                addView(kit.horizontal(Gravity.CENTER).apply {
                    layoutParams = kit.spaced(6)
                    addView(kit.badge("خطا: ${kit.n(t.faults)} از ${kit.n(PantoEngine.MAX_FAULTS)}", if (t.faults > 0) Royal.crimsonLight else Royal.muted, t.faults >= 2))
                    if (m.mode == PantoMode.NORMAL && t.choice != null) {
                        addView(kit.hgap(8))
                        addView(kit.badge("امتیاز موضوع: ${kit.n(t.choice.points)}", Royal.gold))
                    }
                })
            }
            if (m.mode == PantoMode.SEDA) addView(kit.text("اجراکننده فقط صدای کلمه را درمی‌آورد.", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
            if (m.mode == PantoMode.JANGI) addView(kit.text("تیمی که حلقهٔ سبز دارد اجرا می‌کند؛ با «گفته شد» نوبت به تیم بعد می‌رسد. پایان زمان = حذف تیمِ نوبت.", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
        }
    }

    private fun timerCard(t: PantoTurn): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        background = PanelDrawable(kit.density, PanelStyle.RAISED, 22f)
        setPadding(kit.dp(10), kit.dp(10), kit.dp(10), kit.dp(12))
        layoutParams = kit.spaced(10)
        ring = RingTimerView(host).apply {
            totalMs = t.seconds * 1000L
            alertSeconds = if (m.mode == PantoMode.FIVE) 2 else 10
            label = if (phase == 2 && !wantRunning && !ended) "زمان متوقف است" else "زمان باقی‌مانده"
            runningSource = { timer?.running == true }
            update(t.remainingMs, false)
        }
        addView(ring, LinearLayout.LayoutParams(kit.dp(if (m.mode == PantoMode.FIVE) 150 else 176), kit.dp(if (m.mode == PantoMode.FIVE) 150 else 176)))
    }

    private fun wordCard(t: PantoTurn): View = kit.vertical(Gravity.CENTER).apply {
        val golden = t.choice?.golden == true
        background = if (golden) android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF8A6418.toInt(), 0xFF3E2C08.toInt())).apply { cornerRadius = kit.dpf(22f); setStroke(kit.dp(2), Royal.goldLight) }
        else PanelDrawable(kit.density, PanelStyle.SELECTED, 22f)
        setPadding(kit.dp(14), kit.dp(18), kit.dp(14), kit.dp(20))
        minimumHeight = kit.dp(150)
        layoutParams = kit.spaced(10)
        if (phase >= 1 && wordHidden) {
            addView(kit.icon(RoyalIcon.EYE_OFF, Royal.gold, 36))
            addView(kit.text("کلمه پنهان شد", TextStyle.TITLE, Royal.ivory, Gravity.CENTER))
            addView(kit.text("زمان ادامه دارد؛ در پایان نوبت کلمه نشان داده می‌شود.", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
        } else if (phase == 0) {
            addView(kit.icon(RoyalIcon.EYE_OFF, Royal.gold, 36))
            addView(kit.text("کلمه پنهان است", TextStyle.TITLE, Royal.ivory, Gravity.CENTER))
            addView(kit.text("اجراکننده: ${m.teams[t.team].performer}", TextStyle.HEADING, Royal.turquoiseLight, Gravity.CENTER))
            addView(kit.text("فقط اجراکننده صفحه را ببیند و «نمایش کلمه» را بزند.", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
        } else {
            if (golden) addView(kit.text("موضوع طلایی", TextStyle.LABEL_BOLD, Royal.goldLight, Gravity.CENTER))
            val word = t.word?.shown ?: "—"
            addView(kit.text(word, TextStyle.DISPLAY, Royal.ivory, Gravity.CENTER).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, kit.dpf(if (word.length > 28) 26f else if (word.length > 14) 32f else 40f))
            })
            t.word?.takeIf { it.cat in PantoCategories.names }?.let { addView(kit.text(PantoCategories.name(it.cat), TextStyle.CAPTION, Royal.muted, Gravity.CENTER)) }
        }
    }

    private fun controls(t: PantoTurn): View = kit.vertical().apply {
        when {
            phase == 0 -> addView(kit.button("نمایش کلمه به اجراکننده", ButtonKind.PRIMARY, RoyalIcon.EYE, 56) { phase = 1; host.refresh() })
            phase == 1 || !wantRunning && phase == 2 -> {
                if (m.mode == PantoMode.NORMAL && !t.wordChanged && phase == 1) {
                    addView(kit.button("عوض کردن کلمه (۱ امتیاز کم می‌شود)", ButtonKind.GHOST, RoyalIcon.SWAP, 42) { changeWord() }, kit.spaced(6))
                }
                addView(kit.button(if (phase == 1) (if (m.mode == PantoMode.FIVE) "شروع ۵ ثانیه" else "شروع زمان") else "ادامهٔ زمان", ButtonKind.PRIMARY, RoyalIcon.PLAY, 56) {
                    phase = 2; wantRunning = true; host.refresh()
                })
                if (phase == 2) addView(actionRow(t), kit.spaced(0).apply { topMargin = kit.dp(6) })
            }
            else -> addView(actionRow(t))
        }
    }

    private fun actionRow(t: PantoTurn): View = kit.vertical().apply {
        when (m.mode) {
            PantoMode.NORMAL, PantoMode.SPEEDY, PantoMode.SEDA -> {
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.button("درست", ButtonKind.SUCCESS, RoyalIcon.CHECK, 58) { right(true) }))
                    addView(kit.hgap(8))
                    addView(kit.weight(kit.button("نادرست", ButtonKind.DANGER, RoyalIcon.CLOSE, 58) { right(false) }))
                })
                addView(kit.gap(6))
                addView(kit.button("خطا (لب زدن / اشاره)", ButtonKind.SECONDARY, RoyalIcon.WARNING, 48) { fault() })
                addView(kit.gap(6))
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.button(if (wordHidden) "نمایش کلمه" else "پنهان کردن کلمه", ButtonKind.GHOST, if (wordHidden) RoyalIcon.EYE else RoyalIcon.EYE_OFF, 46) {
                        wordHidden = !wordHidden; host.refresh()
                    }))
                    addView(kit.hgap(6))
                    addView(kit.weight(kit.button("توقف زمان", ButtonKind.GHOST, RoyalIcon.TIMER, 46) { pauseTimer() }))
                })
                if (m.mode == PantoMode.NORMAL && !t.wordChanged) {
                    addView(kit.gap(6))
                    addView(kit.button("عوض کردن کلمه (۱ امتیاز کم می‌شود)", ButtonKind.SECONDARY, RoyalIcon.SWAP, 48) { changeWord() })
                }
            }
            PantoMode.JANGI -> {
                addView(kit.button("گفته شد • نوبت تیم بعد", ButtonKind.SUCCESS, RoyalIcon.NEXT, 60) {
                    PantoEngine.jangiSaid(m, pantoBank(), host.panto.used().also { u -> pendingUsed = u }, Random.Default)
                    pendingUsed?.let { host.panto.saveUsed(it) }
                    pantoSounds.correct()
                    t.remainingMs = timer?.remaining ?: t.remainingMs
                    store(); host.refresh()
                })
                addView(kit.gap(6))
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.button(if (wordHidden) "نمایش کلمه" else "پنهان کردن کلمه", ButtonKind.GHOST, if (wordHidden) RoyalIcon.EYE else RoyalIcon.EYE_OFF, 44) {
                        wordHidden = !wordHidden; host.refresh()
                    }))
                    addView(kit.hgap(6))
                    addView(kit.weight(kit.button("توقف زمان", ButtonKind.SECONDARY, RoyalIcon.TIMER, 44) { pauseTimer() }))
                })
            }
            PantoMode.FIVE -> {
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.button("پاسخ کامل (+۵)", ButtonKind.SUCCESS, RoyalIcon.CHECK, 58) { five(true) }))
                    addView(kit.hgap(8))
                    addView(kit.weight(kit.button("پاسخ ناقص", ButtonKind.DANGER, RoyalIcon.CLOSE, 58) { five(false) }))
                })
            }
        }
    }

    private var pendingUsed: MutableSet<Int>? = null

    private fun pauseTimer() {
        wantRunning = false
        timer?.pause()
        m.turn?.remainingMs = timer?.remaining ?: (m.turn?.remainingMs ?: 0)
        store(); host.refresh()
    }

    private fun onTick(ms: Long) {
        m.turn?.remainingMs = ms
        timeText?.text = clockText(ms)
        ring?.update(ms, true)
        val s = ((ms + 999) / 1000).toInt()
        if (s != lastSecond) {
            lastSecond = s
            timeText?.setTextColor(if (s <= 10) Royal.alert else Royal.goldLight)
            if (s in 1..10) pantoSounds.tick()
            if (s % 5 == 0) store()
        }
    }

    private fun onTimeUp() {
        pantoSounds.bell()
        m.turn?.remainingMs = 0
        val t = m.turn
        when (m.mode) {
            PantoMode.NORMAL -> finishWith(PantoEngine.incorrect(m, timeUp = true), t)
            PantoMode.SPEEDY, PantoMode.SEDA -> finishWith(PantoEngine.endList(m), t)
            PantoMode.JANGI -> finishWith(PantoEngine.jangiTimeUp(m), t)
            PantoMode.FIVE -> { wantRunning = false; store(); host.refresh() } // the referee judges the answer
        }
    }

    private fun right(ok: Boolean) {
        val t = m.turn ?: return
        t.remainingMs = timer?.remaining ?: t.remainingMs
        if (ok) pantoSounds.correct() else pantoSounds.wrong()
        when (m.mode) {
            PantoMode.NORMAL -> finishWith(if (ok) PantoEngine.correct(m) else PantoEngine.incorrect(m), t)
            else -> {
                val end = PantoEngine.answer(m, ok)
                if (end != null) finishWith(end, t) else { store(); host.refresh() }
            }
        }
    }

    private fun fault() {
        val t = m.turn ?: return
        t.remainingMs = timer?.remaining ?: t.remainingMs
        pantoSounds.wrong()
        val end = PantoEngine.fault(m)
        if (end != null) finishWith(end, t) else { store(); host.refresh() }
    }

    private fun changeWord() {
        val used = host.panto.used()
        if (PantoEngine.changeWord(m, pantoBank(), host.panto.userWords(), used, Random.Default)) {
            host.panto.saveUsed(used)
            m.turn?.remainingMs = timer?.remaining ?: (m.turn?.remainingMs ?: 0)
            wordHidden = false
            store(); host.refresh()
        }
    }

    private fun five(full: Boolean) {
        wantRunning = false
        timer?.pause()
        if (full) pantoSounds.correct() else pantoSounds.wrong()
        val played = m.turn
        val roundBefore = m.round
        val goldenBefore = m.golden
        val end = PantoEngine.fiveAnswer(m, full)
        store()
        if (end != null && !m.finished && m.round == roundBefore && m.golden == goldenBefore) {
            // Next participant of the same round: next question right here.
            val used = host.panto.used()
            if (PantoEngine.startTurn(m, pantoBank(), emptyList(), used, null, Random.Default) != null) {
                host.panto.saveUsed(used)
                store()
                phase = 1
                wordHidden = false
                kit.toast((if (full) "+۵ " else "") + "نوبت ${m.teams[m.current].name}")
                host.refresh()
                return
            }
        }
        finishWith(end, played)
    }

    /** Shows the turn's result and returns to the rounds page (or the result when the match is over). */
    private fun finishWith(end: PantoEngine.TurnEnd?, played: PantoTurn? = null) {
        wantRunning = false
        timer?.pause()
        ended = true
        store()
        if (end == null) { host.pop(); return }
        val body = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(kit.text(if (end.points > 0) "+" + kit.n(end.points) else kit.n(end.points), TextStyle.DISPLAY, if (end.points > 0) Royal.turquoiseLight else Royal.crimsonLight, Gravity.CENTER).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, kit.dpf(52f))
            })
            addView(kit.text(end.text, TextStyle.BODY, Royal.ivory, Gravity.CENTER))
            val shownWord = played?.words?.getOrNull(played.index.coerceAtMost(played.words.lastIndex))?.takeIf { m.mode == PantoMode.NORMAL || m.mode == PantoMode.FIVE || m.mode == PantoMode.JANGI }
            if (shownWord != null) {
                addView(kit.gap(8))
                addView(kit.text(if (m.mode == PantoMode.FIVE) "سؤال" else "کلمه", TextStyle.LABEL_BOLD, Royal.gold, Gravity.CENTER))
                addView(kit.text(shownWord.shown, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
            }
            if (played != null && played.words.size > 1 && (m.mode == PantoMode.SPEEDY || m.mode == PantoMode.SEDA)) {
                addView(kit.gap(8))
                played.words.forEachIndexed { i, w ->
                    addView(kit.horizontal().apply {
                        addView(kit.icon(if (played.answers.getOrNull(i) == true) RoyalIcon.CHECK else RoyalIcon.CLOSE,
                            if (played.answers.getOrNull(i) == true) Royal.turquoiseLight else Royal.crimsonLight, 18))
                        addView(kit.hgap(6))
                        addView(kit.weight(kit.text(w.shown, TextStyle.LABEL, Royal.ivory)))
                    }, kit.spaced(2))
                }
            }
        }
        kit.dialog(if (end.eliminated) "حذف" else "پایان نوبت", null, body, listOf(DialogAction("ادامه", ButtonKind.PRIMARY) {
            if (m.finished) host.replace(PantoResultScreen(host, matchId)) else host.pop()
        })).apply { setCancelable(false) }.show()
    }

    override fun onHide() {
        wantRunning = false
        timer?.pause()
        if (loaded && m.turn != null) { m.turn?.remainingMs = timer?.remaining ?: (m.turn?.remainingMs ?: 0); store() }
    }

    override fun onBack(): Boolean {
        if (timer?.running == true) {
            wantRunning = false
            timer?.pause()
            m.turn?.remainingMs = timer?.remaining ?: 0
            store()
        }
        return false
    }
}

/** Final standings and the winner. */
class PantoResultScreen(host: MainActivity, matchId: Long) : PantoScreen(host, matchId) {
    override fun build(): View {
        if (!load()) return missing()
        val win = m.winner
        return scaffold("نتیجهٔ مسابقه", "پانتومیم • ${m.mode.title}",
            bottom = kit.vertical().apply {
                addView(kit.button("مسابقهٔ دوباره با همین تیم‌ها", ButtonKind.PRIMARY, RoyalIcon.PLAY, 52) {
                    val again = PantoEngine.rematch(System.currentTimeMillis(), m).also { it.referee = m.referee }
                    host.panto.save(again)
                    host.replace(PantoBoardScreen(host, again.id))
                })
                addView(kit.gap(6))
                addView(kit.button("خانه", ButtonKind.SECONDARY, RoyalIcon.HOME, 46) { host.resetTo(HomeScreen(host)) })
            }) {
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 22f)
                setPadding(kit.dp(14), kit.dp(16), kit.dp(14), kit.dp(18))
                layoutParams = kit.spaced(10)
                addView(TrophyView(host), LinearLayout.LayoutParams(kit.dp(96), kit.dp(96)))
                addView(kit.text(if (win != null) "برنده: ${m.teams[win].name}" else "پایان مسابقه", TextStyle.TITLE, Royal.goldLight, Gravity.CENTER))
                if (win != null) addView(kit.text("امتیاز ${kit.n(m.teams[win].points)}", TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER))
            })
            m.standings.forEachIndexed { place, i ->
                val t = m.teams[i]
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, if (place == 0) PanelStyle.SELECTED else PanelStyle.NORMAL, 16f)
                    setPadding(kit.dp(10), kit.dp(7), kit.dp(10), kit.dp(10))
                    layoutParams = kit.spaced(6)
                    addView(kit.text(kit.n(place + 1), TextStyle.HEADING, Royal.gold))
                    addView(kit.hgap(8))
                    addView(kit.avatar(t.avatar, 38))
                    addView(kit.hgap(8))
                    addView(kit.vertical().apply {
                        addView(kit.text(t.name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
                        if (t.members.isNotEmpty()) addView(kit.text(t.members.joinToString("، "), TextStyle.CAPTION, Royal.muted, maxLines = 2))
                        addView(kit.text("${kit.n(t.turns)} نوبت • ${kit.n(t.correct)} پاسخ درست • ${kit.n(t.faults)} خطا" + if (!t.inGame && m.mode != PantoMode.JANGI) " • حذف‌شده" else "",
                            TextStyle.CAPTION, Royal.turquoiseLight, maxLines = 1))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(kit.text(kit.n(t.points), TextStyle.TITLE, Royal.goldLight))
                })
            }
        }
    }

    override fun onShow() { pantoSounds.bell() }
}

/** «کلمات من»: the players' own words in three levels (add, edit, delete, search). */
class PantoWordsScreen(host: MainActivity) : Screen(host) {
    override val featureKey: String get() = "panto_words"
    private var level = 1
    private var query = ""

    override fun build(): View {
        val words = host.panto.userWords()
        return scaffold("کلمات من", "${kit.n(words.size)} کلمه • از ${kit.n(PantoEngine.USER_WORDS_MIN)} کلمه به بالا در مسابقهٔ عادی می‌آید") {
            val field = kit.field("کلمهٔ مورد نظر را اینجا بنویسید")
            addView(kit.panel(PanelStyle.RAISED, 12).apply {
                layoutParams = kit.spaced(10)
                addView(field, kit.spaced(8))
                addView(SlidingTabs(host, kit, listOf("آسان (۲)", "متوسط (۴)", "سخت (۶)"), level - 1) { level = it + 1 }, kit.spaced(8))
                addView(kit.button("افزودن کلمه", ButtonKind.PRIMARY, RoyalIcon.PLUS, 48) {
                    val list = host.panto.userWords()
                    val err = PantoEngine.userWordError(field.text.toString(), list)
                    if (err != null) { kit.toast("متن خالی یا کلمهٔ تکراری وارد شده"); return@button }
                    host.panto.saveUserWords(list + PantoWord(host.panto.nextUserWordId(), PantoCategories.USER, level, field.text.toString().trim()))
                    kit.toast("ذخیره شد")
                    host.refresh()
                })
            })
            if (words.isEmpty()) { addView(emptyState(RoyalIcon.EDIT, "هنوز کلمه‌ای ندارید", "کلمه‌های خودتان را با سختی دلخواه اضافه کنید.")); return@scaffold }
            val rows = words.mapIndexed { i, w -> row(i, w) to w.text }
            kit.searchFilter("جست‌وجوی کلمه", rows, query, minItems = 6) { query = it }?.let { addView(it, kit.spaced(8)) }
            rows.forEach { addView(it.first) }
            addView(kit.gap(10))
            addView(kit.button("حذف همهٔ کلمات من", ButtonKind.DANGER, RoyalIcon.TRASH, 44) {
                kit.confirm("حذف همه", "همهٔ ${kit.n(words.size)} کلمهٔ شما حذف شود؟", "حذف همه", true) { host.panto.saveUserWords(emptyList()); host.refresh() }
            })
        }
    }

    private fun row(i: Int, w: PantoWord): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
        setPadding(kit.dp(10), kit.dp(6), kit.dp(10), kit.dp(8))
        layoutParams = kit.spaced(5)
        addView(kit.text(kit.n(i + 1), TextStyle.LABEL_BOLD, Royal.gold))
        addView(kit.hgap(8))
        addView(kit.weight(kit.text(w.shown, TextStyle.BODY_BOLD, Royal.ivory)))
        addView(kit.badge("${PantoCategories.levelNames[w.point]} • ${kit.n(w.point * 2)}", Royal.turquoise))
        addView(kit.hgap(6))
        addView(kit.iconButton(RoyalIcon.EDIT, "ویرایش ${w.text}", ButtonKind.CHIP, 38) { edit(w) })
        addView(kit.hgap(4))
        addView(kit.iconButton(RoyalIcon.TRASH, "حذف ${w.text}", ButtonKind.CHIP, 38) {
            kit.confirm("حذف کلمه", "«${w.shown}» حذف شود؟", "حذف", true) { host.panto.saveUserWords(host.panto.userWords().filterNot { it.id == w.id }); host.refresh() }
        })
    }

    private fun edit(w: PantoWord) {
        var lvl = w.point
        val field = kit.field("کلمه", w.text)
        val body = kit.vertical().apply {
            addView(field, kit.spaced(8))
            addView(SlidingTabs(host, kit, listOf("آسان", "متوسط", "سخت"), lvl - 1) { lvl = it + 1 })
        }
        kit.dialog("ویرایش کلمه", null, body, listOf(DialogAction("ذخیره", ButtonKind.PRIMARY) {
            val list = host.panto.userWords()
            if (PantoEngine.userWordError(field.text.toString(), list, w.id) != null) { kit.toast("متن خالی یا کلمهٔ تکراری وارد شده"); return@DialogAction }
            host.panto.saveUserWords(list.map { if (it.id == w.id) it.copy(text = field.text.toString().trim(), point = lvl) else it })
            host.refresh()
        }, DialogAction("انصراف"))).show()
    }
}

/** Browse the whole bank by category and level, with search (read only). */
class PantoBankScreen(host: MainActivity) : Screen(host) {
    private var cat = 1
    private var level = 0
    private var query = ""
    private var shown = 0
    private var words: List<PantoWord> = emptyList()
    private var list: LinearLayout? = null
    private var count: TextView? = null
    private var more: View? = null
    private var levelBox: FrameLayout? = null
    private val chips = LinkedHashMap<Int, TextView>()

    override fun build(): View {
        val bank = pantoBank()
        val cats = PantoCategories.names.keys.toList() + listOf(PantoCategories.FIVE_SECOND, PantoCategories.SOUND, PantoCategories.GOLDEN)
        chips.clear()
        return scaffold("بانک واژه‌ها", "۲۰ دستهٔ واژه و پرسش") {
            addView(kit.flow(cats.map { c ->
                kit.chip("${PantoCategories.name(c)} (${kit.n(bank.count(c))})", c == cat, ButtonKind.CHIP_GOLD) {
                    if (c == cat) return@chip
                    chips[cat]?.let { kit.setChip(it, false, ButtonKind.CHIP_GOLD) }
                    cat = c; level = 0
                    chips[cat]?.let { kit.setChip(it, true, ButtonKind.CHIP_GOLD) }
                    levels(bank); fill(bank)
                }.apply { setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(6)); chips[c] = this }
            }, 5), kit.spaced(8))
            levelBox = FrameLayout(host)
            addView(levelBox, kit.spaced(8))
            levels(bank)
            addView(kit.searchBox("جست‌وجو در این دسته", query) { q -> query = q; fill(bank) }, kit.spaced(8))
            count = kit.text("", TextStyle.CAPTION, Royal.muted, Gravity.CENTER)
            addView(count, kit.spaced(6))
            list = kit.vertical()
            addView(list)
            more = kit.button("نمایش بیشتر", ButtonKind.SECONDARY, RoyalIcon.PLUS, 44) { page() }
            addView(more, kit.spaced(8))
            fill(bank)
        }
    }

    private fun levels(bank: PantoBank) {
        val box = levelBox ?: return
        box.removeAllViews()
        val levels = bank.of(cat).map { it.point }.distinct().sorted()
        box.visibility = if (levels.size > 1) View.VISIBLE else View.GONE
        if (levels.size > 1) box.addView(SlidingTabs(host, kit, listOf("همه") + levels.map { PantoCategories.levelNames[it] ?: "سطح ${kit.n(it)}" }, levels.indexOf(level) + 1) { i ->
            level = if (i == 0) 0 else levels[i - 1]; fill(bank)
        })
    }

    private fun fill(bank: PantoBank) {
        words = bank.of(cat).filter { (level == 0 || it.point == level) && SearchText.matches(query, it.text) }
        count?.text = "${kit.n(words.size)} مورد"
        list?.removeAllViews()
        shown = 0
        page()
    }

    /** Adds the next rows; long categories are shown a page at a time. */
    private fun page() {
        val box = list ?: return
        val end = minOf(words.size, shown + PAGE)
        for (i in shown until end) {
            val w = words[i]
            box.addView(kit.horizontal().apply {
                background = PanelDrawable(kit.density, PanelStyle.FLAT, 12f)
                setPadding(kit.dp(10), kit.dp(5), kit.dp(10), kit.dp(7))
                layoutParams = kit.spaced(4)
                addView(kit.weight(kit.text(w.shown, TextStyle.BODY, Royal.ivory)))
                addView(kit.badge(PantoCategories.levelNames[w.point] ?: kit.n(w.point), Royal.gold))
            })
        }
        shown = end
        more?.visibility = if (shown < words.size) View.VISIBLE else View.GONE
    }

    private companion object { const val PAGE = 60 }
}

class PantoRulesScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("قوانین پانتومیم", "قوانین مشترک و هر نوع مسابقه") {
        addView(kit.section("قوانین مشترک", RoyalIcon.RULES))
        PantoEngine.commonRules.forEach { addView(kit.text("•  $it", TextStyle.BODY, Royal.ivory), kit.spaced(4)) }
        PantoMode.values().forEach { m ->
            addView(kit.section("مسابقهٔ ${m.title}", RoyalIcon.CARDS))
            PantoEngine.rules(m).drop(PantoEngine.commonRules.size).forEach { addView(kit.text("•  $it", TextStyle.BODY, Royal.ivory), kit.spaced(4)) }
        }
    }
}

/** Past and unfinished matches. */
class PantoMatchesScreen(host: MainActivity) : Screen(host) {
    override fun build(): View {
        val list = host.panto.matches()
        return scaffold("مسابقه‌های گذشته", "${kit.n(list.size)} مسابقه") {
            if (list.isEmpty()) addView(emptyState(RoyalIcon.HISTORY, "هنوز مسابقه‌ای نیست", "با «مسابقهٔ جدید» اولین مسابقه را شروع کنید."))
            list.forEach { m -> addView(pantoCard(m)) }
        }
    }
}

/** Card of a match for lists and the history page. */
fun Screen.pantoCard(m: PantoMatch): View = kit.vertical().apply {
    background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
    setPadding(kit.dp(10), kit.dp(9), kit.dp(10), kit.dp(12))
    layoutParams = kit.spaced(8)
    addView(kit.horizontal().apply {
        addView(RoleSealView(host, PantoLook.glyph, PantoLook.TINT), LinearLayout.LayoutParams(kit.dp(36), kit.dp(36)))
        addView(kit.hgap(10))
        addView(kit.vertical().apply {
            addView(kit.horizontal().apply {
                addView(kit.text("پانتومیم • ${m.mode.title}", TextStyle.BODY_BOLD, Royal.goldLight))
                addView(kit.hgap(8))
                addView(if (m.finished) kit.badge("پایان‌یافته", Royal.gold) else kit.badge("در جریان", Royal.turquoise, true))
            })
            addView(kit.text(JalaliDate.format(m.updatedAt, settings.general.persianDigits), TextStyle.CAPTION, Royal.muted))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.iconButton(RoyalIcon.TRASH, "حذف مسابقه", ButtonKind.CHIP, 40) {
            kit.confirm("حذف مسابقه", "این مسابقه از تاریخچه حذف شود؟", "حذف", true) { host.panto.delete(m.id); host.refresh() }
        })
    })
    addView(kit.gap(6))
    addView(kit.text(m.standings.joinToString("  •  ") { "${m.teams[it].name} ${kit.n(m.teams[it].points)}" }, TextStyle.LABEL, Royal.ivory, maxLines = 2))
    m.winner?.let { addView(kit.text("برنده: ${m.teams[it].name}", TextStyle.LABEL_BOLD, Royal.turquoiseLight)) }
    isClickable = true
    contentDescription = "مسابقهٔ پانتومیم"
    setOnClickListener { kit.tap(it); host.push(if (m.finished) PantoResultScreen(host, m.id) else PantoBoardScreen(host, m.id)) }
}
