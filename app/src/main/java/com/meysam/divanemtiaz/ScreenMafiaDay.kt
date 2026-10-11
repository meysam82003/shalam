package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.random.Random

/** «صحبت»: each alive player in seat order with his own timer and an optional challenger. */
class MafiaSpeakingScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private val timers = ArrayList<MafiaTimer>()
    private val challengers = HashMap<Int, Int>()
    private var cuePlayed = false

    private fun pickChallenger(p: MafiaPlayer) {
        val taken = challengers.filterKeys { it != p.id }.values.toSet()
        pickPlayers("چه کسی از ${p.name} چالش گرفت؟", g.alive.filter { it.id != p.id && it.id !in taken && MafiaFlow.silenced(it) == null }, 1, 1, false,
            challengers[p.id]?.let { setOf(it) } ?: emptySet()) { ids ->
            challengers[p.id] = ids.first(); host.refresh()
        }
    }

    override fun build(): View {
        if (!load()) return missing()
        timers.forEach { it.stop() }
        timers.clear()
        val s = mafiaSettings
        return scaffold("صحبت", "زمان صحبت ${kit.n(s.speakTime)} • چالش ${kit.n(s.challengeTime)} ثانیه",
            actions = listOf(
                kit.iconButton(RoyalIcon.SWAP, "ترتیب نشستن", ButtonKind.SECONDARY, 44) { host.push(MafiaSeatingScreen(host, gameId)) },
                kit.iconButton(RoyalIcon.UNDO, "بازنشانی", ButtonKind.SECONDARY, 44) { challengers.clear(); host.refresh() }
            ),
            bottom = kit.button("پایان", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) { finish() }) {
            addView(narration("صحبت‌ها به ترتیب نشستن؛ بازیکن اول سر صحبت است.", MafiaCues.SPEAKING, PanelStyle.NORMAL))
            MafiaManual.speakingOrder(g).forEachIndexed { i, p ->
                val challenger = challengers[p.id]?.let { g.player(it) }
                val extra = kit.horizontal().apply {
                    if (challenger == null) {
                        addView(kit.chip("چالش به …", false) { pickChallenger(p) }.apply { setPadding(kit.dp(12), kit.dp(4), kit.dp(12), kit.dp(6)) })
                    } else {
                        addView(kit.weight(kit.text("${challenger.name} از ${p.name} چالش گرفت", TextStyle.LABEL_BOLD, Royal.turquoiseLight, maxLines = 2)))
                        val time = kit.text(clock(s.challengeTime), TextStyle.HEADING, Royal.goldLight, Gravity.CENTER, 1)
                        lateinit var timer: MafiaTimer
                        timer = MafiaTimer(this@MafiaSpeakingScreen, s.challengeTime) { left, _ -> time.text = clock(left) }
                        timers += timer
                        addView(time, LinearLayout.LayoutParams(kit.dp(54), ViewGroup.LayoutParams.WRAP_CONTENT))
                        addView(kit.chip("شروع/توقف", false, ButtonKind.CHIP_GOLD) {
                            if (timer.running) timer.pause() else { timers.filter { it !== timer }.forEach { it.pause() }; timer.start() }
                        }.apply { setPadding(kit.dp(8), kit.dp(4), kit.dp(8), kit.dp(6)) })
                        addView(kit.hgap(4))
                        addView(kit.iconButton(RoyalIcon.EDIT, "اصلاح چلنجر ${p.name}", ButtonKind.CHIP, 36) { timer.stop(); pickChallenger(p) })
                        addView(kit.hgap(4))
                        addView(kit.iconButton(RoyalIcon.UNDO, "برگرداندن انتخاب چالش ${p.name}", ButtonKind.CHIP, 36) {
                            timer.stop()
                            challengers.remove(p.id)
                            kit.toast("انتخاب اشتباه برگشت؛ ${p.name} دوباره می‌تواند چالش بدهد")
                            host.refresh()
                        })
                    }
                }
                val note = (if (i == 0) "سر صحبت" else null) ?: if (p.has("extraTime")) "زمان دوبرابر (کشیش)" else null
                addView(timerRow(p, MafiaFlow.speakTime(p, s), note, MafiaFlow.silenced(p), timers, extra))
            }
        }
    }

    private fun finish() {
        timers.forEach { it.stop() }
        val fire = MafiaDay.fireback(g)
        if (fire.isNotEmpty()) {
            kit.dialog("تفنگ جنگی", "بازیکن از تفنگ جنگی خود استفاده نکرده است\n" + fire.joinToString("، ") { it.name }, null, listOf(
                DialogAction("خروج", ButtonKind.DANGER) {
                    fire.forEach { it.marks.remove("gun") }
                    g.day.speakingDone = true
                    g.log += MafiaLog(g.phaseTitle, "صحبت روز انجام شد؛ تفنگ جنگی به سمت ${fire.joinToString("، ") { it.name }} برگشت")
                    store()
                    host.replace(MafiaExitScreen(host, gameId, fire.map { it.id }, MafiaDeath.GUN))
                },
                DialogAction("بازگشت به صحبت")
            )).show()
            return
        }
        g.day.speakingDone = true
        g.log += MafiaLog(g.phaseTitle, "صحبت روز انجام شد")
        store()
        host.pop()
    }

    override fun onShow() {
        if (!cuePlayed && mafiaSettings.autoPlay) { cuePlayed = true; playCue(MafiaCues.SPEAKING) }
    }

    override fun onHide() { timers.forEach { it.stop() } }
}

/** «رای گیری دور اول» / «رای گیری دور دوم»: votes received by each player. */
class MafiaVoteScreen(host: MainActivity, gameId: Long, private val second: Boolean) : MafiaScreen(host, gameId) {
    private var cuePlayed = false

    override fun build(): View {
        if (!load()) return missing()
        val s = mafiaSettings
        val n = g.alive.size
        val votes = if (second) g.day.secondVotes else g.day.firstVotes
        val rows = if (second) g.day.defenders.mapNotNull { g.player(it) } else g.alive
        val title = if (second) "رای گیری دور دوم" else "رای گیری دور اول"
        val edgeText = if (!second) "رای لازم برای به دفاع رفتن بازیکن : ${kit.n(MafiaFlow.firstEdge(n, s))}"
            else if (rows.size == 1) "رای لازم برای خروج بازیکن : ${kit.n(MafiaFlow.secondEdge(n, s))}"
            else "بیشترین رأی از بازی خارج می‌شود؛ تساوی = قرعهٔ مرگ"
        return scaffold(title, "${kit.n(n)} نفر در بازی",
            bottom = kit.button("پایان", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) {
                if (second) {
                    g.day.secondVoteDone = true
                    rows.forEach { p -> g.log += MafiaLog(g.phaseTitle, "${p.name}: ${PersianText.digits(votes[p.id] ?: 0)} رای، در رای گیری دوم کسب کرد") }
                } else MafiaFlow.finishFirstVote(g, s)
                store()
                host.pop()
            }) {
            addView(narration(edgeText, if (second) MafiaCues.VOTE_SECOND else MafiaCues.VOTE_FIRST, PanelStyle.NORMAL))
            rows.forEach { p ->
                val blocked = when {
                    p.has("greenMile") -> "مسیر سبز: در رأی‌گیری قرار نمی‌گیرد"
                    else -> null
                }
                val tags = listOfNotNull(if (p.has("redCarpet")) "فرش قرمز" else null)
                if (blocked != null) {
                    addView(kit.settingRow(p.name, blocked, kit.badge("مصون", Royal.turquoise)))
                } else {
                    addView(kit.stepperRow(p.name, tags.joinToString(" • ").ifBlank { null }, votes[p.id] ?: 0, 0, (n - 1).coerceAtLeast(1), 1) { v ->
                        votes[p.id] = v; store()
                    })
                }
            }
        }
    }

    override fun onShow() {
        if (!cuePlayed && mafiaSettings.autoPlay) { cuePlayed = true; playCue(if (second) MafiaCues.VOTE_SECOND else MafiaCues.VOTE_FIRST) }
    }
}

/** «دفاعیه»: each defender with the defence timer. */
class MafiaDefenceScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private val timers = ArrayList<MafiaTimer>()
    private var cuePlayed = false

    override fun build(): View {
        if (!load()) return missing()
        timers.forEach { it.stop() }
        timers.clear()
        return scaffold("دفاعیه", "زمان دفاع ${kit.n(mafiaSettings.defenceTime)} ثانیه",
            bottom = kit.button("پایان", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) { g.day.defenceDone = true; store(); host.pop() }) {
            addView(narration("بازیکنان زیر به ترتیب از خود دفاع کنند", MafiaCues.DEFENCE, PanelStyle.NORMAL))
            g.day.defenders.mapNotNull { g.player(it) }.forEach { p ->
                addView(timerRow(p, mafiaSettings.defenceTime, "${kit.n(g.day.firstVotes[p.id] ?: 0)} رای در دور اول",
                    if (p.has("lambs")) "بازیکن در سکوت بره ها ساکت شده است" else null, timers))
            }
        }
    }

    override fun onShow() { if (!cuePlayed && mafiaSettings.autoPlay) { cuePlayed = true; playCue(MafiaCues.DEFENCE) } }
    override fun onHide() { timers.forEach { it.stop() } }
}

/** «قرعه مرگ»: one face-down card per tied player, one of them holds the cross. */
class MafiaLotteryScreen(host: MainActivity, gameId: Long, private val tied: List<Int>) : MafiaScreen(host, gameId) {
    private var loser: Int? = null

    override fun build(): View {
        if (!load()) return missing()
        val revealed = loser
        return scaffold("قرعه مرگ", "تساوی آرا بین ${kit.n(tied.size)} نفر",
            bottom = kit.button(if (revealed == null) "نمایش نتیجه" else "تایید", ButtonKind.PRIMARY, if (revealed == null) RoyalIcon.EYE else RoyalIcon.CHECK, 52) {
                if (revealed == null) { loser = MafiaFlow.lottery(tied, Random(System.nanoTime())); host.refresh() }
                else {
                    g.log += MafiaLog(g.phaseTitle, "قرعه مرگ به نام ${g.player(revealed)?.name} افتاد")
                    store()
                    host.replace(MafiaExitScreen(host, gameId, listOf(revealed), MafiaDeath.LOTTERY, voteResult = true))
                }
            }) {
            addView(narration("هر بازیکن یک کارت برمی‌دارد؛ کارتی که ضربدر دارد از بازی خارج می‌شود.", MafiaCues.VOTE_RESULT, PanelStyle.NORMAL))
            addView(kit.grid(2, tied.mapIndexed { i, id ->
                val p = g.player(id)!!
                kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                    background = PanelDrawable(kit.density, if (revealed == id) PanelStyle.DANGER else if (revealed != null) PanelStyle.SUCCESS else PanelStyle.RAISED, 16f)
                    setPadding(kit.dp(6), kit.dp(10), kit.dp(6), kit.dp(12))
                    addView(kit.text("کارت شماره ${kit.n(i + 1)}", TextStyle.LABEL_BOLD, Royal.gold, Gravity.CENTER))
                    addView(seal(if (revealed == null) MafiaGlyph.QUESTION else if (revealed == id) MafiaGlyph.SKULL else MafiaGlyph.HEART, if (revealed == id) MafiaSide.MAFIA else MafiaSide.CITY, 70))
                    addView(kit.avatar(p.avatar, 40))
                    addView(kit.text(p.name, TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER, 1))
                    if (revealed != null) addView(kit.text(if (revealed == id) "از بازی خارج می‌شود" else "در بازی می‌ماند", TextStyle.CAPTION, if (revealed == id) Royal.crimsonLight else Royal.turquoiseLight, Gravity.CENTER))
                }
            }, 8))
        }
    }
}

/**
 * Exit flow (source ExitProgress): special exit rules, then «آیا برای خروج حق انتخاب کارت حرکت اخر را دارد؟»,
 * the face-down deck, the drawn card and its effect.
 */
class MafiaExitScreen(host: MainActivity, gameId: Long, queue: List<Int>, private val type: String, private val voteResult: Boolean = false, private val allowCard: Boolean = true,
    typeOf: Map<Int, String> = emptyMap()) : MafiaScreen(host, gameId) {
    private enum class Stage { CHECK, ASK, PICK, CARD, RESULT }

    private val queue = ArrayList(queue)
    /** Exit type per queued player when it differs from [type] (e.g. a terror victim after a vote). */
    private val types = HashMap<Int, String>(typeOf)
    private var stage = Stage.CHECK
    private var checked: Pair<List<Int>, String>? = null
    private var card: Int = -1
    private val picks = ArrayList<Int>()
    private var guess: MafiaRole? = null
    private var answer = false
    private var outcome: CardOutcome? = null

    override fun build(): View {
        if (!load()) return missing()
        val id = queue.firstOrNull() ?: return scaffold("خروج") { addView(kit.text("پایان", TextStyle.BODY, Royal.muted, Gravity.CENTER)) }
        val p = g.player(id) ?: return missing()
        if (checked == null && p.alive) checked = MafiaFlow.exitCheck(g, id).also { store() }
        val check = checked ?: (listOf(id) to "")
        val staying = check.first.isEmpty()
        val cards = allowCard && MafiaFlow.remainingCards(g).isNotEmpty()
        val sacrificeOffer = !staying && p.alive && MafiaDay.sacrificeAvailable(g, id)
        if (stage == Stage.CHECK && check.second.isBlank() && !staying && cards && !sacrificeOffer) stage = Stage.ASK
        val proceed = {
            when {
                staying -> nextPlayer()
                cards -> { stage = Stage.ASK; host.refresh() }
                else -> finishPlayer(false)
            }
        }
        return when (stage) {
            Stage.CHECK -> scaffold("خروج بازیکن", p.name, bottom = if (sacrificeOffer) kit.horizontal().apply {
                addView(kit.weight(kit.button("فدایی فدا می‌شود", ButtonKind.DANGER, RoyalIcon.HEART, 52) {
                    MafiaDay.sacrifice(g, id); store(); nextPlayer()
                }))
                addView(kit.hgap(8))
                addView(kit.weight(kit.button("ادامه", ButtonKind.PRIMARY, RoyalIcon.NEXT, 52) { proceed() }))
            } else kit.button("ادامه", ButtonKind.PRIMARY, RoyalIcon.NEXT, 52) { proceed() }) {
                addView(kit.horizontal(Gravity.CENTER).apply { addView(playerTile(p, false, false), LinearLayout.LayoutParams(kit.dp(130), ViewGroup.LayoutParams.WRAP_CONTENT)) }, kit.spaced(8))
                val text = check.second.ifBlank { "بازیکن زیر از بازی خارج خواهد شد" + if (allowCard && !cards) "\nکارت حرکت آخری وجود ندارد" else "" }
                addView(narration(text, null, if (staying) PanelStyle.SUCCESS else PanelStyle.DANGER))
                if (sacrificeOffer) addView(narration("آیا ${MafiaRole.SACRIFICE.faName} میخواهد خود را فدا کند؟", null, PanelStyle.NORMAL))
                check.first.filter { it != id }.mapNotNull { g.player(it) }.forEach { addView(kit.text("${it.name} نیز از بازی خارج می‌شود", TextStyle.BODY_BOLD, Royal.crimsonLight, Gravity.CENTER)) }
            }
            Stage.ASK -> scaffold("کارت حرکت آخر", p.name, bottom = kit.horizontal().apply {
                addView(kit.weight(kit.button("خیر", ButtonKind.SECONDARY, RoyalIcon.CLOSE, 52) { finishPlayer(false) }))
                addView(kit.hgap(8))
                addView(kit.weight(kit.button("بله", ButtonKind.PRIMARY, RoyalIcon.CARDS, 52) { stage = Stage.PICK; host.refresh() }))
            }) {
                addView(kit.horizontal(Gravity.CENTER).apply { addView(playerTile(p, false, false), LinearLayout.LayoutParams(kit.dp(130), ViewGroup.LayoutParams.WRAP_CONTENT)) }, kit.spaced(8))
                addView(narration("آیا برای خروج حق انتخاب کارت حرکت اخر را دارد؟", MafiaCues.LAST_CARD, PanelStyle.RAISED))
                addView(kit.text("${kit.n(MafiaFlow.remainingCards(g).size)} کارت باقی مانده است", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
            }
            Stage.PICK -> scaffold("کارت حرکت آخر", "یکی از کار تهای حرکت آخر را انتخاب کنید") {
                addView(narration("${p.name} یکی از کارت‌ها را انتخاب کند", MafiaCues.LAST_CARD, PanelStyle.NORMAL))
                addView(kit.grid(3, MafiaFlow.remainingCards(g).mapIndexed { i, _ ->
                    kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                        addView(RoleCardView(host).apply { faceDown = true; title = "" })
                        addView(kit.text("کارت شماره : ${kit.n(i + 1)}", TextStyle.LABEL_BOLD, Royal.gold, Gravity.CENTER))
                        isClickable = true
                        setOnClickListener {
                            kit.tap(it)
                            card = MafiaFlow.draw(g, i, p.id)?.let { st -> g.deck.indexOf(st) } ?: -1
                            if (card < 0) return@setOnClickListener
                            store()
                            playCue(MafiaCues.LAST_CARD)
                            stage = Stage.CARD
                            host.refresh()
                        }
                    }
                }, 8))
            }
            Stage.CARD -> cardView(p)
            Stage.RESULT -> scaffold("کارت حرکت آخر", p.name, bottom = kit.button("ادامه", ButtonKind.PRIMARY, RoyalIcon.NEXT, 52) { applyOutcome() }) {
                val st = g.deck[card]
                addView(kit.horizontal(Gravity.CENTER).apply { addView(lastCard(st.card), LinearLayout.LayoutParams(kit.dp(150), kit.dp((150 * RoleCardView.RATIO).toInt()))) }, kit.spaced(8))
                val o = outcome!!
                addView(narration(o.text.ifBlank { st.card.faName }, null, if (o.stays) PanelStyle.SUCCESS else PanelStyle.DANGER))
                addView(kit.text(if (o.stays) "${p.name} در بازی می‌ماند" else "${p.name} از بازی خارج می‌شود", TextStyle.HEADING, if (o.stays) Royal.turquoiseLight else Royal.crimsonLight, Gravity.CENTER))
                o.alsoExit.mapNotNull { g.player(it) }.forEach { addView(kit.text("${it.name} نیز از بازی خارج می‌شود", TextStyle.BODY_BOLD, Royal.crimsonLight, Gravity.CENTER)) }
            }
        }
    }

    private fun cardView(p: MafiaPlayer): View {
        val st = g.deck[card]
        val c = st.card
        val needs = when (c.action) {
            CardAction.PICK_PLAYERS -> true
            CardAction.PICK_OUT_PLAYER -> true
            CardAction.GUESS_ROLE -> !(c == MafiaLastCard.NOSTRADAMUS_BEAUTIFUL_MIND && p.role == MafiaRole.NOSTRADAMUS)
            else -> false
        }
        return scaffold(c.faName.replace("\n", " "), "کارت ${p.name}", bottom = kit.button("اعمال کارت", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) {
            val min = if (c.action == CardAction.PICK_OUT_PLAYER) 0 else if (needs) 1 else 0
            if (needs && picks.size < min) { kit.toast("بازیکن را انتخاب کنید"); return@button }
            if (c == MafiaLastCard.INTELLIGENCE && guess == null) { kit.toast("نقش را برای حدس انتخاب کنید"); return@button }
            outcome = MafiaFlow.applyCard(g, st, picks.toList(), guess, answer, Random(System.nanoTime()))
            store()
            stage = Stage.RESULT
            host.refresh()
        }) {
            addView(kit.horizontal(Gravity.CENTER).apply { addView(lastCard(c), LinearLayout.LayoutParams(kit.dp(170), kit.dp((170 * RoleCardView.RATIO).toInt()))) }, kit.spaced(8))
            addView(kit.text("توضیحات کارت", TextStyle.LABEL_BOLD, Royal.gold))
            addView(kit.text(c.explain, TextStyle.BODY, Royal.ivory), kit.spaced(10))
            if (needs) {
                val pool = if (c.action == CardAction.PICK_OUT_PLAYER) g.out.filter { it.id != p.id } else g.alive.filter { it.id != p.id }
                val max = c.picks.coerceAtLeast(1)
                addView(kit.section(if (c.action == CardAction.PICK_OUT_PLAYER) "انتخاب بازیکن خارج‌شده" else "انتخاب ${kit.n(max)} نفر", RoyalIcon.USER))
                if (pool.isEmpty()) addView(kit.text(if (c.action == CardAction.PICK_OUT_PLAYER) "هیچ بازیکنی از بازی بیرون نرفته است" else "بازیکنی نیست", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
                addView(kit.grid(3, pool.map { t ->
                    playerTile(t, false, t.id in picks) {
                        if (t.id in picks) picks.remove(t.id) else { if (picks.size >= max) picks.removeAt(0); picks += t.id }
                        host.refresh()
                    }
                }, 6), kit.spaced(8))
            }
            if (c == MafiaLastCard.INTELLIGENCE) {
                addView(kit.button(guess?.let { "حدس نقش: ${it.faName}" } ?: "انتخاب نقش برای حدس", ButtonKind.SECONDARY, RoyalIcon.CARDS, 46) {
                    rolePicker("حدس نقش", g.players.mapNotNull { it.role }.distinct()) { r -> guess = r; host.refresh() }
                }, kit.spaced(8))
            }
            if (c == MafiaLastCard.DARE_OR_TRUTH) {
                addView(kit.choiceRow("بازیکن انتخابی", null, listOf(0 to "جرات (خارج می‌شود)", 1 to "حقیقت و درست جواب داد", 2 to "حقیقت ولی نادرست"), if (answer) 1 else 0) { i -> answer = i == 1 })
            }
        }
    }

    private fun applyOutcome() {
        val o = outcome ?: return
        o.alsoExit.forEach { MafiaFlow.exit(g, it, MafiaDeath.CARD) }
        finishPlayer(o.stays)
    }

    /** Finishes the current player: exits him (and anyone his exit rule takes along) unless he stays. */
    private fun finishPlayer(stays: Boolean) {
        val id = queue.first()
        val check = checked ?: (listOf(id) to "")
        val p = g.player(id)
        if (!stays) check.first.forEach { MafiaFlow.exit(g, it, if (it == id) types[id] ?: type else MafiaDeath.DAY_SHOT) }
        else g.log += MafiaLog(g.phaseTitle, "${p?.name} در بازی ماند")
        store()
        if (!stays && p?.role == MafiaRole.TERRORIST && (types[id] ?: type) != MafiaDeath.TERROR && !p.has("taken")) {
            val selfKill = g.extra["selfKill"] != "0"
            val targets = g.alive.filter { selfKill || it.side != MafiaSide.MAFIA }
            if (targets.isNotEmpty()) {
                kit.dialog("ترور", "بازیکن اخراجی ${MafiaRole.TERRORIST.faName} است\nآیا ${MafiaRole.TERRORIST.faName} میخواهد کسی را ترور کند؟", null, listOf(
                    DialogAction("بله", ButtonKind.DANGER) {
                        pickPlayers("ترور", targets, 1, 1, false) { ids ->
                            g.log += MafiaLog(g.phaseTitle, "${p.name} (تروریست) ${g.player(ids.first())?.name} را ترور کرد")
                            store()
                            queue.add(1, ids.first())
                            types[ids.first()] = MafiaDeath.TERROR
                            nextPlayer()
                        }
                    },
                    DialogAction("خیر") { nextPlayer() }
                )).show()
                return
            }
        }
        nextPlayer()
    }

    private fun nextPlayer() {
        queue.removeAt(0)
        checked = null; card = -1; picks.clear(); guess = null; answer = false; outcome = null
        stage = Stage.CHECK
        if (queue.isNotEmpty()) { host.refresh(); return }
        if (voteResult) {
            g.day.resultDone = true
            g.players.forEach { it.flags.removeAll(setOf("redCarpet", "greenMile", "lambs")) }
        }
        val win = MafiaFlow.checkWin(g)
        store()
        if (win != null) playCue(MafiaFlow.winCue(win))
        host.pop()
    }
}

/** «دست دادن» with three players left. */
class MafiaShakeHandScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private var citizen: Int? = null
    private var chosen: Int? = null

    override fun build(): View {
        if (!load()) return missing()
        val c = citizen
        return scaffold("دست دادن", "سه نفر باقی مانده‌اند",
            bottom = kit.button(if (c == null) "تایید" else "دست دادن", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) {
                if (c == null) kit.toast("شهروند خود را انتخاب کنید")
                else if (chosen == null) kit.toast("بازیکن انتخابی را مشخص کنید")
                else {
                    MafiaFlow.shakeHand(g, c, chosen!!)
                    val win = MafiaFlow.checkWin(g)
                    store()
                    if (win != null) playCue(MafiaFlow.winCue(win))
                    host.pop()
                }
            }) {
            addView(narration(if (c == null) "شهروند خود را انتخاب کنید" else "شهروند انتخابی کدام نفر را به عنوان شهروند بر میگزیند؟", null, PanelStyle.RAISED))
            addView(kit.grid(3, g.alive.map { p ->
                val sel = if (c == null) false else p.id == chosen
                playerTile(p, true, p.id == c || sel, if (p.id == c) "شهروند" else null, enabled = c == null || p.id != c) {
                    if (c == null) citizen = p.id else if (p.id != c) chosen = p.id
                    host.refresh()
                }
            }, 6))
            if (c != null) addView(kit.button("انتخاب دوباره", ButtonKind.GHOST, RoyalIcon.UNDO, 42) { citizen = null; chosen = null; host.refresh() }, kit.spaced(8))
        }
    }
}

/** «گزارش بازی»: initial line-up by team and every phase's events. */
class MafiaReportScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    override fun build(): View {
        if (!load()) return missing()
        return scaffold("گزارش بازی", if (g.finished) g.winner else g.phaseTitle,
            actions = listOf(kit.iconButton(RoyalIcon.SHARE, "اشتراک گذاری", ButtonKind.SECONDARY, 44) { host.shareFile("mafia-report.txt", text(), "text/plain") })) {
            val lineup = MafiaFlow.lineup(g)
            listOf(MafiaSide.CITY to "تیم شهروندی", MafiaSide.MAFIA to "تیم مافیایی", MafiaSide.INDEPENDENT to "افراد مستقل").forEach { (side, title) ->
                val list = lineup.filter { it.second?.side == side }
                if (list.isEmpty()) return@forEach
                addView(kit.section("$title (${kit.n(list.size)})", RoyalIcon.PLAYERS))
                addView(kit.grid(3, list.map { (p, r) ->
                    kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                        background = PanelDrawable(kit.density, if (p.alive) PanelStyle.FLAT else PanelStyle.GHOST, 14f)
                        setPadding(kit.dp(4), kit.dp(6), kit.dp(4), kit.dp(8))
                        addView(seal(r?.glyph ?: MafiaGlyph.CITIZEN, side, 40))
                        addView(kit.text(p.name, TextStyle.LABEL_BOLD, Royal.ivory, Gravity.CENTER, 1))
                        addView(kit.text(r?.faName ?: "", TextStyle.CAPTION, side.textColor(), Gravity.CENTER, 1))
                    }
                }, 6))
            }
            val phases = g.log.map { it.phase }.distinct()
            if (phases.isEmpty()) addView(kit.text("در این بازی هنوز اتفاقی ثبت نشده است", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(8))
            phases.forEach { ph ->
                addView(kit.section(ph.replace("شب ", "شب : ").replace("روز ", "روز : "), if (ph.startsWith("شب")) RoyalIcon.EYE_OFF else RoyalIcon.EYE))
                addView(kit.panel(PanelStyle.FLAT, 10).apply {
                    layoutParams = kit.spaced(6)
                    g.log.filter { it.phase == ph }.forEach { addView(kit.text("• " + it.text, TextStyle.LABEL, Royal.ivory)) }
                })
            }
            if (g.note.isNotBlank()) {
                addView(kit.section("یادداشت گرداننده", RoyalIcon.EDIT))
                addView(kit.text(g.note, TextStyle.LABEL, Royal.ivory))
            }
        }
    }

    private fun text(): String {
        val b = StringBuilder("گزارش بازی مافیا\n")
        MafiaFlow.lineup(g).forEach { (p, r) -> b.append("${p.name}: ${r?.faName ?: ""}\n") }
        g.log.map { it.phase }.distinct().forEach { ph ->
            b.append("\n$ph\n")
            g.log.filter { it.phase == ph }.forEach { b.append("- ${it.text}\n") }
        }
        return b.toString()
    }
}

/** «بازگشت به گذشته»: restore the board as it was at the start of an earlier day or night. */
class MafiaPastScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    override fun build(): View {
        if (!load()) return missing()
        return scaffold("بازگشت به گذشته", "روزها و شب‌های ذخیره‌شده") {
            g.snapshots.forEachIndexed { i, (label, _) ->
                addView(kit.settingRow(label, if (i == g.snapshots.lastIndex) "وضعیت فعلی" else null, kit.icon(RoyalIcon.UNDO, Royal.gold, 22)) {
                    kit.confirm("بازگشت به گذشته", "شما $label را انتخاب کرده اید\nآیا ادامه میدهید؟", "تایید") {
                        val restored = MafiaFlow.restore(g, i)
                        host.mafia.save(restored)
                        host.resetTo(HomeScreen(host), MafiaHomeScreen(host), MafiaGameScreen(host, gameId))
                    }
                })
            }
        }
    }
}
