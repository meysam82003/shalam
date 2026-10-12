package com.meysam.divanemtiaz

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

object SpyLook {
    const val TINT = 0xFF14805F.toInt()
    val glyph = MafiaGlyph.BINOCULARS
}

/** Built-in words (read once) plus the players' own words. */
object SpyWords {
    const val ASSET = "spy/words.json"
    @Volatile private var defaults: List<SpyWord>? = null
    fun get(open: () -> java.io.InputStream): List<SpyWord> = defaults ?: synchronized(this) {
        defaults ?: open().use { SpyEngine.defaults(it) }.also { defaults = it }
    }
}

fun Screen.spyAllWords(): List<SpyWord> = SpyWords.get { host.assets.open(SpyWords.ASSET) } + host.spy.userWords()

fun Screen.spyEnabledWords(): List<SpyWord> {
    val off = host.spy.disabled()
    return spyAllWords().filter { it.id !in off }
}

private var spySoundsHolder: GameSounds? = null
private val Screen.spySounds: GameSounds get() = spySoundsHolder ?: GameSounds { host.spy.sound() }.also { spySoundsHolder = it }

/** «جاسوس» home: players, spies and time, the words, and the start. */
class SpyHomeScreen(host: MainActivity) : Screen(host) {
    /** People chosen as players (optional; otherwise «بازیکن ۱»، «بازیکن ۲»…). */
    private val people = ArrayList<String>()

    override fun build(): View {
        val s = host.spy.settings()
        val enabled = spyEnabledWords()
        val live = host.spy.latestUnfinished()
        return scaffold("جاسوس", "پیدا کردن جاسوس با سؤال و جواب",
            actions = listOf(kit.iconButton(RoyalIcon.RULES, "قوانین", ButtonKind.SECONDARY, 44) { host.push(SpyRulesScreen(host)) }),
            bottom = kit.button("شروع بازی", ButtonKind.PRIMARY, RoyalIcon.PLAY, 54) { start() }) {
            addView(gameHero(SpyLook.glyph, SpyLook.TINT, "جاسوس", "${kit.n(spyAllWords().size)} کلمه در ${kit.n(SpyCategory.values().size)} دسته • پیش‌فرض ۶ بازیکن، ۱ جاسوس، ۵ دقیقه"))
            if (live != null) {
                addView(kit.panel(PanelStyle.SELECTED, 12).apply {
                    layoutParams = kit.spaced(10)
                    addView(kit.text("بازی در جریان • ${kit.n(live.players.size)} بازیکن", TextStyle.BODY_BOLD, Royal.turquoiseLight))
                    addView(kit.gap(8))
                    addView(kit.button("ادامهٔ بازی", ButtonKind.SUCCESS, RoyalIcon.PLAY, 46) { host.push(if (live.allViewed) SpyTimerScreen(host, live.id) else SpyRevealScreen(host, live.id)) })
                })
            }
            addView(kit.section("تنظیمات بازی", RoyalIcon.SLIDERS))
            val count = if (people.isNotEmpty()) people.size else s.players
            addView(kit.stepperRow("تعداد بازیکنان", if (people.isNotEmpty()) "از روی افراد انتخاب‌شده" else null, count, SpyEngine.MIN_PLAYERS, SpyEngine.MAX_PLAYERS, 1) { v ->
                host.spy.saveSettings(host.spy.settings().copy(players = v, spies = host.spy.settings().spies.coerceAtMost(v - 1)))
                if (people.isNotEmpty() && v != people.size) people.clear()
                host.refresh()
            })
            addView(kit.stepperRow("تعداد جاسوس‌ها", "همیشه کمتر از تعداد بازیکنان", s.spies, 1, (count - 1).coerceAtLeast(1), 1) { v ->
                host.spy.saveSettings(host.spy.settings().copy(spies = v))
            })
            addView(kit.stepperRow("زمان بازی", null, s.minutes, SpyEngine.MIN_MINUTES, SpyEngine.MAX_MINUTES, 1, format = { "${kit.n(it)} دقیقه" }) { v ->
                host.spy.saveSettings(host.spy.settings().copy(minutes = v))
            })
            addView(kit.settingRow("بازیکنان", if (people.isEmpty()) "اختیاری: نام‌ها را از افراد انتخاب کنید تا در رنک‌بندی بیاید" else people.joinToString("، "),
                kit.icon(RoyalIcon.PLAYERS, Royal.gold, 22)) {
                memberPicker(people, SpyEngine.MAX_PLAYERS, "بازیکنان جاسوس") { names ->
                    people.clear(); people += names
                    if (names.size >= SpyEngine.MIN_PLAYERS) host.spy.saveSettings(host.spy.settings().copy(players = names.size, spies = host.spy.settings().spies.coerceAtMost(names.size - 1)))
                    host.refresh()
                }
            })
            addView(kit.section("کلمات", RoyalIcon.CARDS))
            addView(kit.settingRow("${kit.n(enabled.size)} کلمهٔ انتخاب‌شده", SpyCategory.values().joinToString("  •  ") { c -> "${c.title} ${kit.n(enabled.count { it.category == c })}" },
                kit.icon(RoyalIcon.EDIT, Royal.gold, 20)) { host.push(SpyWordsScreen(host)) })
            addView(kit.flow(SpyCategory.values().map { c ->
                val inCat = spyAllWords().filter { it.category == c }
                val on = inCat.isNotEmpty() && inCat.any { it.id !in host.spy.disabled() }
                kit.chip("${c.title} ${kit.n(enabled.count { it.category == c })}", on, ButtonKind.CHIP_GOLD) {
                    val d = host.spy.disabled()
                    if (on) d += inCat.map { it.id } else d -= inCat.map { it.id }.toSet()
                    host.spy.saveDisabled(d); host.refresh()
                }.apply { setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(6)) }
            }, 6), kit.spaced(8))
            addView(kit.text("در شروع بازی، یک کلمه به‌صورت تصادفی از میان کلمات انتخاب‌شده انتخاب می‌شود.", TextStyle.CAPTION, Royal.muted, Gravity.CENTER), kit.spaced(10))
            addView(kit.section("گزینه‌ها", RoyalIcon.SETTINGS))
            addView(kit.switchRow("راهنمای دسته برای جاسوس", "جاسوس فقط دستهٔ کلمه (مثلاً «مکان») را می‌بیند؛ مناسب تازه‌کارها", s.spyHint) { v ->
                host.spy.saveSettings(host.spy.settings().copy(spyHint = v))
            })
            addView(kit.switchRow("صدای بازی", "زنگ پایان زمان", host.spy.sound()) { host.spy.setSound(it) })
            val games = host.spy.games().filter { it.finished }
            if (games.isNotEmpty()) {
                addView(kit.section("بازی‌های گذشته", RoyalIcon.HISTORY))
                games.take(10).forEach { addView(spyCard(it)) }
            }
        }
    }

    private fun start() {
        val s0 = host.spy.settings()
        val s = if (people.size >= SpyEngine.MIN_PLAYERS) s0.copy(players = people.size, spies = s0.spies.coerceAtMost(people.size - 1)) else s0
        val words = spyEnabledWords()
        SpyEngine.error(s, words.size)?.let { kit.toast(it); return }
        val roster = host.repo.roster()
        val names = people.map { n -> n to (roster.firstOrNull { it.name == n && !it.isTeam }?.avatar ?: 0) }
        val g = SpyEngine.start(System.currentTimeMillis(), s, names, words, Random.Default)
        host.spy.save(g)
        host.push(SpyRevealScreen(host, g.id))
    }
}

/** Hidden cards passed around: each player touches his card, sees his role, and covers it again. */
class SpyRevealScreen(host: MainActivity, private val gameId: Long) : Screen(host) {
    override fun build(): View {
        val g = host.spy.game(gameId) ?: return scaffold("جاسوس") {}
        if (g.allViewed) { host.post(0) { host.replace(SpyTimerScreen(host, gameId)) }; return scaffold("جاسوس") {} }
        val i = g.revealIndex
        return scaffold("نقش‌ها", "بازیکن ${kit.n(i + 1)} از ${kit.n(g.players.size)}",
            bottom = if (g.revealed) kit.button(if (i + 1 < g.players.size) "دیدم؛ کارت را بپوشان و به نفر بعد بده" else "دیدم؛ شروع زمان", ButtonKind.PRIMARY, RoyalIcon.CHECK, 56) {
                SpyEngine.cover(g); host.spy.save(g); host.refresh()
            } else null) {
            addView(kit.horizontal(Gravity.CENTER).apply {
                layoutParams = kit.spaced(10)
                addView(kit.avatar(g.avatars.getOrElse(i) { 0 }, 46))
                addView(kit.hgap(10))
                addView(kit.text(g.players[i], TextStyle.TITLE, Royal.goldLight))
            })
            addView(card(g, i), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(360)))
            addView(kit.text(if (g.revealed) "بعد از دیدن، کارت را بپوشانید تا نفر بعد نقش شما را نبیند." else "دستگاه را به ${g.players[i]} بدهید.", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(10))
        }
    }

    private fun card(g: SpyGame, i: Int): View = kit.vertical(Gravity.CENTER).apply {
        setPadding(kit.dp(16), kit.dp(16), kit.dp(16), kit.dp(16))
        if (!g.revealed) {
            background = PanelDrawable(kit.density, PanelStyle.RAISED, 26f)
            addView(RoleSealView(host, MafiaGlyph.QUESTION, SpyLook.TINT), LinearLayout.LayoutParams(kit.dp(110), kit.dp(110)))
            addView(kit.gap(12))
            addView(kit.text("با لمس این کارت، نقش خود را مشاهده می‌کنید.", TextStyle.HEADING, Royal.ivory, Gravity.CENTER))
            isClickable = true
            contentDescription = "دیدن نقش"
            setOnClickListener { kit.tap(it); SpyEngine.reveal(g); host.spy.save(g); host.refresh() }
        } else if (g.isSpy(i)) {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF7A1424.toInt(), 0xFF2A0710.toInt())).apply { cornerRadius = kit.dpf(26f); setStroke(kit.dp(3), Royal.crimsonLight) }
            addView(RoleSealView(host, SpyLook.glyph, 0xFFA62B35.toInt()), LinearLayout.LayoutParams(kit.dp(120), kit.dp(120)))
            addView(kit.gap(12))
            addView(kit.text("نقش شما: جاسوس", TextStyle.DISPLAY, 0xFFFFD0D4.toInt(), Gravity.CENTER).apply { setTextSize(TypedValue.COMPLEX_UNIT_PX, kit.dpf(36f)) })
            if (g.spyHint) addView(kit.text("دستهٔ کلمه: ${g.category.title}", TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
            addView(kit.text("کلمه را نمی‌دانید؛ از سؤال‌ها حدسش بزنید و لو نروید.", TextStyle.LABEL, Royal.ivory, Gravity.CENTER))
        } else {
            background = PanelDrawable(kit.density, PanelStyle.SELECTED, 26f)
            addView(kit.text("کلمهٔ مخفی", TextStyle.HEADING, Royal.turquoiseLight, Gravity.CENTER))
            addView(kit.gap(8))
            addView(kit.text(g.word, TextStyle.DISPLAY, Royal.ivory, Gravity.CENTER).apply { setTextSize(TypedValue.COMPLEX_UNIT_PX, kit.dpf(46f)) })
            addView(kit.text("دسته: ${g.category.title}", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
        }
    }
}

/** Countdown of the questioning, the bell at the end, and how the game ended. */
class SpyTimerScreen(host: MainActivity, private val gameId: Long) : Screen(host) {
    private var timer: Countdown? = null
    private var timeText: TextView? = null
    private var g: SpyGame? = null
    private var lastSecond = -1
    /** The countdown should run (kept across page rebuilds; the countdown is recreated on each build). */
    private var wantRunning = false

    override fun build(): View {
        val game = host.spy.game(gameId) ?: return scaffold("جاسوس") {}
        g = game
        if (game.finished) { host.post(0) { host.replace(SpyResultScreen(host, gameId)) }; return scaffold("جاسوس") {} }
        timer?.pause()
        timer = Countdown(game.remainingMs, { ms -> tick(ms) }, { timeUp() })
        val over = game.remainingMs <= 0
        if (over) wantRunning = false
        if (wantRunning) host.post(0) { if (wantRunning) timer?.start() }
        return scaffold("زمان‌سنج", "${kit.n(game.players.size)} بازیکن، ${kit.n(game.spies.size)} جاسوس",
            bottom = kit.vertical().apply {
                if (spySounds.alarmPlaying) {
                    addView(kit.button("قطع زنگ", ButtonKind.DANGER, RoyalIcon.CLOSE, 54) { spySounds.stopAlarm(); host.refresh() }, kit.spaced(6))
                }
                if (!over) addView(kit.button(if (wantRunning) "توقف زمان" else if (game.remainingMs < game.minutes * 60_000L) "ادامهٔ زمان" else "شروع زمان",
                    if (wantRunning) ButtonKind.SECONDARY else ButtonKind.PRIMARY, RoyalIcon.TIMER, 52) { toggle() }, kit.spaced(6))
                addView(kit.button(if (over) "رأی‌گیری و نتیجه" else "پایان بازی", if (over) ButtonKind.PRIMARY else ButtonKind.SECONDARY, RoyalIcon.FLAG, 48) {
                    if (over) outcome() else {
                        val was = wantRunning
                        wantRunning = false
                        timer?.pause(); save()
                        kit.dialog("پایان بازی", "مطمئن هستید که می‌خواهید بازی را پایان دهید؟", null, listOf(
                            DialogAction("تأیید", ButtonKind.DANGER) { outcome() },
                            DialogAction("خیر، ادامهٔ بازی") { if (was) { wantRunning = true; timer?.start() } }
                        )).show()
                    }
                })
            }) {
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 26f)
                setPadding(kit.dp(12), kit.dp(22), kit.dp(12), kit.dp(24))
                layoutParams = kit.spaced(12)
                addView(RoleSealView(host, SpyLook.glyph, SpyLook.TINT), LinearLayout.LayoutParams(kit.dp(84), kit.dp(84)))
                timeText = kit.text(clockText(game.remainingMs), TextStyle.DISPLAY, if (over) 0xFFFF4F58.toInt() else Royal.goldLight, Gravity.CENTER).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, kit.dpf(72f)); layoutDirection = View.LAYOUT_DIRECTION_LTR
                }
                addView(timeText)
                addView(kit.text(if (over) "زمان تمام شد" else "بازیکنان به نوبت از یکدیگر سؤال بپرسند", TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER))
            })
            addView(kit.settingRow("سؤال اول را ${game.players.getOrElse(game.starter) { "" }} می‌پرسد", "بعد هر نفر از نفر کناری‌اش می‌پرسد",
                kit.iconButton(RoyalIcon.SWAP, "قرعهٔ دوباره", ButtonKind.CHIP, 40) {
                    host.spy.game(gameId)?.let { st -> SpyEngine.newStarter(st, Random.Default); st.remainingMs = timer?.remaining ?: st.remainingMs; host.spy.save(st) }
                    host.refresh()
                }))
            addView(kit.text("سؤال‌ها باید با کلمهٔ مخفی ارتباط داشته باشند ولی آن را مستقیم لو ندهند.", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
        }
    }

    private fun toggle() {
        wantRunning = !wantRunning
        if (!wantRunning) { timer?.pause(); save() }
        host.refresh()
    }

    private fun tick(ms: Long) {
        g?.remainingMs = ms
        timeText?.text = clockText(ms)
        val s = ((ms + 999) / 1000).toInt()
        if (s != lastSecond) {
            lastSecond = s
            timeText?.setTextColor(if (s <= 10) 0xFFFF4F58.toInt() else Royal.goldLight)
            if (s % 10 == 0) save()
        }
    }

    private fun timeUp() {
        wantRunning = false
        g?.remainingMs = 0
        save()
        spySounds.startAlarm()
        host.refresh()
    }

    /** Saves the remaining time on the stored game (never over a game that has meanwhile ended). */
    private fun save() {
        val stored = host.spy.game(gameId) ?: return
        if (stored.finished) return
        stored.remainingMs = timer?.remaining ?: (g?.remainingMs ?: stored.remainingMs)
        host.spy.save(stored)
    }

    private fun outcome() {
        spySounds.stopAlarm()
        val game = g ?: return
        lateinit var dialog: android.app.Dialog
        val body = kit.vertical().apply {
            addView(kit.button("رأی‌گیری: انتخاب متهم", ButtonKind.PRIMARY, RoyalIcon.USER, 50) { dialog.dismiss(); vote(game) }, kit.spaced(10))
            addView(kit.text("یا نتیجه را مستقیم ثبت کنید:", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(6))
            SpyOutcome.values().forEach { o ->
                addView(kit.button(o.title, if (o.spiesWin) ButtonKind.DANGER else ButtonKind.SUCCESS, null, 50) {
                    dialog.dismiss()
                    game.remainingMs = timer?.remaining ?: game.remainingMs
                    SpyEngine.end(game, o); host.spy.save(game)
                    host.replace(SpyResultScreen(host, gameId))
                }, kit.spaced(6))
            }
        }
        dialog = kit.dialog("نتیجهٔ بازی", "چه اتفاقی افتاد؟", body, listOf(DialogAction("بستن")))
        dialog.show()
    }

    /** The others choose one player; the app shows whether he was a spy and records the result. */
    private fun vote(game: SpyGame) {
        lateinit var d: android.app.Dialog
        val grid = kit.grid(3, game.players.mapIndexed { i, n ->
            kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                setPadding(kit.dp(4), kit.dp(8), kit.dp(4), kit.dp(10))
                addView(kit.avatar(game.avatars.getOrElse(i) { 0 }, 42))
                addView(kit.text(n, TextStyle.LABEL_BOLD, Royal.ivory, Gravity.CENTER, 1))
                isClickable = true
                contentDescription = n
                setOnClickListener {
                    kit.tap(it); d.dismiss()
                    game.remainingMs = timer?.remaining ?: game.remainingMs
                    val o = SpyEngine.vote(game, i)
                    host.spy.save(game)
                    kit.dialog(if (o == SpyOutcome.FOUND) "$n جاسوس بود!" else "$n جاسوس نبود", o.title, null, listOf(DialogAction("دیدن نتیجه", ButtonKind.PRIMARY) {
                        host.replace(SpyResultScreen(host, gameId))
                    })).apply { setCancelable(false) }.show()
                }
            }
        }, 6)
        d = kit.dialog("متهم کیست؟", "بازیکنان روی یک نفر توافق کنند", grid, listOf(DialogAction("بستن")))
        d.show()
    }

    override fun onHide() { wantRunning = false; timer?.pause(); save(); spySounds.stopAlarm() }
}

/** Who the spies were, the word, and the winning side. */
class SpyResultScreen(host: MainActivity, private val gameId: Long) : Screen(host) {
    override fun build(): View {
        val g = host.spy.game(gameId) ?: return scaffold("جاسوس") {}
        val spiesWin = g.spiesWin == true
        return scaffold("نتیجهٔ جاسوس", g.outcome?.title ?: "",
            bottom = kit.vertical().apply {
                addView(kit.button("بازی دوباره", ButtonKind.PRIMARY, RoyalIcon.PLAY, 52) { host.replace(SpyHomeScreen(host)) })
                addView(kit.gap(6))
                addView(kit.button("خانه", ButtonKind.SECONDARY, RoyalIcon.HOME, 46) { host.resetTo(HomeScreen(host)) })
            }) {
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 22f)
                setPadding(kit.dp(14), kit.dp(16), kit.dp(14), kit.dp(18))
                layoutParams = kit.spaced(10)
                addView(TrophyView(host), LinearLayout.LayoutParams(kit.dp(90), kit.dp(90)))
                addView(kit.text(if (spiesWin) "جاسوس برنده شد" else "بازیکنان عادی برنده شدند", TextStyle.TITLE, if (spiesWin) Royal.crimsonLight else Royal.turquoiseLight, Gravity.CENTER))
                addView(kit.text("کلمهٔ مخفی: ${g.word}", TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
                if (g.accused >= 0) addView(kit.text("متهم: ${g.players.getOrElse(g.accused) { "" }}", TextStyle.LABEL, Royal.ivory, Gravity.CENTER))
            })
            addView(kit.section("بازیکنان", RoyalIcon.PLAYERS))
            g.players.forEachIndexed { i, n ->
                addView(kit.settingRow(n, if (g.isSpy(i)) "جاسوس" else "بازیکن عادی",
                    kit.badge(if ((g.isSpy(i) && spiesWin) || (!g.isSpy(i) && !spiesWin)) "برنده" else "بازنده", if (g.isSpy(i)) Royal.crimsonLight else Royal.turquoise, g.isSpy(i))))
            }
        }
    }
}

/** Word lists by category: select / deselect, search, and the players' own words. */
class SpyWordsScreen(host: MainActivity) : Screen(host) {
    private var cat = SpyCategory.PLACE
    private var query = ""
    private var grid: LinearLayout? = null

    override fun build(): View {
        val off = host.spy.disabled()
        val all = spyAllWords()
        return scaffold("کلمات", "${kit.n(all.count { it.id !in off })} از ${kit.n(all.size)} کلمه انتخاب شده",
            bottom = if (cat == SpyCategory.USER) kit.button("افزودن کلمهٔ جدید", ButtonKind.PRIMARY, RoyalIcon.PLUS, 52) { edit(null) } else null) {
            addView(SlidingTabs(host, kit, SpyCategory.values().map { it.title }, cat.ordinal) { i -> cat = SpyCategory.values()[i]; host.refresh() }, kit.spaced(8))
            val words = all.filter { it.category == cat }
            addView(kit.horizontal().apply {
                layoutParams = kit.spaced(8)
                addView(kit.weight(kit.button("انتخاب همه در [${cat.title}]", ButtonKind.SECONDARY, RoyalIcon.CHECK, 42) {
                    host.spy.saveDisabled(host.spy.disabled() - words.map { it.id }.toSet()); host.refresh()
                }))
                addView(kit.hgap(6))
                addView(kit.weight(kit.button("لغو همه", ButtonKind.GHOST, RoyalIcon.CLOSE, 42) {
                    host.spy.saveDisabled(host.spy.disabled() + words.map { it.id }); host.refresh()
                }))
            })
            if (words.isEmpty()) { addView(emptyState(RoyalIcon.CARDS, "در این دسته کلمه‌ای وجود ندارد", if (cat == SpyCategory.USER) "با «افزودن کلمهٔ جدید» کلمهٔ خودتان را بسازید." else "")); return@scaffold }
            addView(kit.searchBox("جستجوی کلمه", query) { q -> query = q; fill(words) }, kit.spaced(8))
            grid = kit.vertical()
            addView(grid)
            fill(words)
            if (cat == SpyCategory.USER) {
                addView(kit.gap(10))
                addView(kit.button("حذف همهٔ کلمات کاربر", ButtonKind.DANGER, RoyalIcon.TRASH, 44) {
                    kit.confirm("حذف کلمات", "همهٔ ${kit.n(words.size)} کلمهٔ شما حذف شود؟", "حذف همه", true) { host.spy.saveUserWords(emptyList()); host.refresh() }
                })
            }
        }
    }

    private fun fill(words: List<SpyWord>) {
        val box = grid ?: return
        val off = host.spy.disabled()
        box.removeAllViews()
        val shown = words.filter { SearchText.matches(query, it.text) }
        box.addView(kit.grid(2, shown.map { w -> chip(w, w.id !in off) }, 6))
    }

    private fun chip(w: SpyWord, on: Boolean): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, if (on) PanelStyle.SELECTED else PanelStyle.FLAT, 14f)
        setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
        addView(kit.icon(if (on) RoyalIcon.CHECK else RoyalIcon.CLOSE, if (on) Royal.turquoiseLight else Royal.dim, 18))
        addView(kit.hgap(6))
        addView(kit.weight(kit.text(w.text, TextStyle.BODY_BOLD, if (on) Royal.ivory else Royal.muted, maxLines = 2)))
        if (w.user) addView(kit.iconButton(RoyalIcon.EDIT, "ویرایش ${w.text}", ButtonKind.CHIP, 34) { edit(w) })
        isClickable = true
        contentDescription = w.text
        setOnClickListener {
            kit.tap(it)
            val d = host.spy.disabled()
            if (!d.remove(w.id)) d += w.id
            host.spy.saveDisabled(d)
            val now = w.id !in d
            background = PanelDrawable(kit.density, if (now) PanelStyle.SELECTED else PanelStyle.FLAT, 14f)
            (getChildAt(0) as? IconView)?.let { _ -> }
            host.refresh()
        }
    }

    /** Add (null) or edit / delete one of the players' own words. */
    private fun edit(w: SpyWord?) {
        val field = kit.field("کلمه را به فارسی وارد کنید", w?.text ?: "")
        val actions = ArrayList<DialogAction>()
        actions += DialogAction("ذخیره", ButtonKind.PRIMARY) {
            val t = field.text.toString().trim()
            val list = host.spy.userWords()
            if (t.isEmpty() || (spyAllWords().any { it.id != w?.id && SearchText.norm(it.text) == SearchText.norm(t) })) { kit.toast("متن خالی یا کلمهٔ تکراری"); return@DialogAction }
            host.spy.saveUserWords(if (w == null) list + SpyWord(host.spy.nextUserWordId(), t, SpyCategory.USER, true) else list.map { if (it.id == w.id) it.copy(text = t) else it })
            host.refresh()
        }
        if (w != null) actions += DialogAction("حذف", ButtonKind.DANGER) {
            host.spy.saveUserWords(host.spy.userWords().filterNot { it.id == w.id }); host.refresh()
        }
        actions += DialogAction("لغو")
        kit.dialog(if (w == null) "افزودن کلمهٔ جدید" else "ویرایش کلمه", "برای افزودن کلمه، آن را وارد و ذخیره کنید. پس از ذخیره، کلمه در «کلمات کاربر» نمایش داده می‌شود.", field, actions).show()
    }
}

class SpyRulesScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("قوانین جاسوس", "روش بازی") {
        SpyEngine.rules.forEach { addView(kit.panel(PanelStyle.FLAT, 12).apply { layoutParams = kit.spaced(6); addView(kit.text(it, TextStyle.BODY, Royal.ivory)) }) }
    }
}

/** Card of a spy game for lists and the history page. */
fun Screen.spyCard(g: SpyGame): View = kit.vertical().apply {
    background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
    setPadding(kit.dp(10), kit.dp(9), kit.dp(10), kit.dp(12))
    layoutParams = kit.spaced(8)
    addView(kit.horizontal().apply {
        addView(RoleSealView(host, SpyLook.glyph, SpyLook.TINT), LinearLayout.LayoutParams(kit.dp(36), kit.dp(36)))
        addView(kit.hgap(10))
        addView(kit.vertical().apply {
            addView(kit.horizontal().apply {
                addView(kit.text("جاسوس", TextStyle.BODY_BOLD, Royal.goldLight))
                addView(kit.hgap(8))
                addView(if (g.finished) kit.badge(if (g.spiesWin == true) "برد جاسوس" else "برد بازیکنان", Royal.gold) else kit.badge("در جریان", Royal.turquoise, true))
            })
            addView(kit.text(JalaliDate.format(g.updatedAt, settings.general.persianDigits), TextStyle.CAPTION, Royal.muted))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(kit.iconButton(RoyalIcon.TRASH, "حذف بازی", ButtonKind.CHIP, 40) {
            kit.confirm("حذف بازی", "این بازی از تاریخچه حذف شود؟", "حذف", true) { host.spy.delete(g.id); host.refresh() }
        })
    })
    addView(kit.gap(6))
    addView(kit.text(if (g.finished) "کلمه: ${g.word} • جاسوس: ${g.spies.sorted().joinToString("، ") { g.players.getOrElse(it) { "" } }}" else "${kit.n(g.players.size)} بازیکن",
        TextStyle.LABEL, Royal.ivory, maxLines = 2))
    isClickable = true
    contentDescription = "بازی جاسوس"
    setOnClickListener { kit.tap(it); host.push(if (g.finished) SpyResultScreen(host, g.id) else if (g.allViewed) SpyTimerScreen(host, g.id) else SpyRevealScreen(host, g.id)) }
}
