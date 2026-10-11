package com.meysam.divanemtiaz

import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Loads the game for a mafia screen; every screen re-reads it so the stack never shows stale state. */
abstract class MafiaScreen(host: MainActivity, val gameId: Long) : Screen(host) {
    lateinit var g: MafiaGame

    /** True once [load] has read the game (onShow can run before the first build). */
    val ready: Boolean get() = this::g.isInitialized

    fun load(): Boolean {
        g = host.mafia.game(gameId) ?: return false
        MafiaRoleRegistry.activate(host.mafia.scenario(g.extra["scenario"]))
        return true
    }

    fun store() { host.mafia.save(g) }

    fun missing(): View = scaffold("بازی پیدا نشد") { addView(kit.text("این بازی دیگر وجود ندارد.", TextStyle.BODY, Royal.muted, Gravity.CENTER)) }

    /** Grid dialog to choose [min]..[max] players. */
    fun pickPlayers(title: String, candidates: List<MafiaPlayer>, min: Int, max: Int, showRole: Boolean = true, preset: Set<Int> = emptySet(), onDone: (List<Int>) -> Unit) {
        if (candidates.isEmpty()) { kit.toast("بازیکنی برای انتخاب نیست"); return }
        val chosen = LinkedHashSet(preset)
        val grid = kit.grid(3, emptyList(), 6)
        fun fill() {
            grid.removeAllViews()
            candidates.forEach { p ->
                grid.addView(playerTile(p, showRole, p.id in chosen) {
                    if (p.id in chosen) chosen.remove(p.id)
                    else { if (chosen.size >= max) chosen.remove(chosen.first()); chosen += p.id }
                    fill()
                })
            }
        }
        fill()
        lateinit var dialog: Dialog
        dialog = kit.dialog(title, if (max > 1) "${kit.n(min)} تا ${kit.n(max)} نفر" else null, grid, listOf(
            DialogAction("تایید", ButtonKind.PRIMARY, dismiss = false) {
                if (chosen.size < min) kit.toast("دست‌کم ${kit.n(min)} نفر را انتخاب کنید") else { dialog.dismiss(); onDone(chosen.toList()) }
            },
            DialogAction("لغو")
        ))
        dialog.show()
    }

    fun playersDialog(title: String, list: List<MafiaPlayer>) {
        if (list.isEmpty()) { kit.toast("بازیکنی در این فهرست نیست"); return }
        kit.dialog(title, null, kit.grid(3, list.map { p ->
            playerTile(p, true, false, if (!p.alive) MafiaDeath.title(p.deathType) else MafiaFlow.silenced(p)?.let { "ساکت" })
        }, 6), listOf(DialogAction("بستن"))).show()
    }
}

/** The day / night board (source DayAndNightManager). */
class MafiaGameScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    override fun build(): View {
        if (!load()) return missing()
        if (g.finished) return finished()
        return if (g.isDay) day() else night()
    }

    override fun onShow() {
        if (mafiaSettings.keepScreenOn) host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (ready && !g.isDay && !g.finished && !host.audio().musicPlaying && mafiaSettings.nightMusic.isSet) startNightMusic()
    }

    override fun onHide() {
        host.applyWindowSettings()
    }

    private fun toolsButton(): View = kit.iconButton(RoyalIcon.SLIDERS, "ابزار راوی", ButtonKind.SECONDARY, 44) { host.push(MafiaToolsScreen(host, gameId)) }

    private fun menuButton(): View = kit.iconButton(RoyalIcon.MENU, "منو", ButtonKind.SECONDARY, 44) { menu() }

    private fun header(glyph: MafiaGlyph, title: String, line: String, cues: List<String>): View = kit.panel(PanelStyle.RAISED, 12).apply {
        layoutParams = kit.spaced(10)
        addView(kit.horizontal().apply {
            addView(seal(glyph, null, 58))
            addView(kit.hgap(10))
            addView(kit.vertical().apply {
                addView(kit.text(title, TextStyle.TITLE, Royal.goldLight))
                addView(kit.text(line, TextStyle.CAPTION, Royal.muted, maxLines = 2))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        if (cues.isNotEmpty()) {
            addView(kit.gap(8))
            addView(kit.flow(cues.map { key ->
                kit.horizontal().apply {
                    background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
                    setPadding(kit.dp(6), kit.dp(4), kit.dp(10), kit.dp(5))
                    addView(cueButton(key, 34))
                    addView(kit.hgap(6))
                    addView(kit.text(MafiaCues.title(key), TextStyle.LABEL_BOLD, Royal.ivory, maxLines = 1))
                }
            }, 6))
        }
    }

    private fun counts(): String = "${kit.n(g.alive.size)} نفر در بازی  •  ${kit.n(g.out.size)} نفر بیرون"

    // ------------------------------------------------------------------ night

    private fun night(): View = scaffold("شب : ${MafiaText.ordinal(g.night)}", counts(), actions = listOf(toolsButton(), menuButton()),
        bottom = kit.button(if (g.night == 0) "پایان شب معارفه" else "پایان شب", ButtonKind.PRIMARY, RoyalIcon.NEXT, 52) { endNight() }) {
        addView(header(MafiaGlyph.MOON, if (g.night == 0) "شب معارفه" else "شب می‌شود", "نقش‌ها را به ترتیب بیدار کنید؛ ترتیب پیشنهادی از راست به چپ است.",
            listOf(MafiaCues.NIGHT_START, MafiaCues.ALL_SLEEP)))
        if (mafiaSettings.nightMusic.isSet) addView(kit.button(if (host.audio().musicPlaying) "قطع موزیک شب" else "پخش موزیک شب", ButtonKind.GHOST, RoyalIcon.PLAY, 42) {
            if (host.audio().musicPlaying) host.audio().stopMusic() else startNightMusic()
            host.refresh()
        }, kit.spaced(8))
        if (MafiaFlow.insomnia(g)) {
            addView(narration("امشب بی خوابی داریم\nزد و خوردی انجام نمیشود\nمستقیم به روز بعد خواهیم رفت", null, PanelStyle.SELECTED))
            return@scaffold
        }
        val jobs = MafiaNight.jobs(g)
        if (jobs.isEmpty()) {
            addView(narration(if (g.alive.size <= 3) "با سه بازیکن یا کمتر، شب کاری انجام نمی‌شود" else "امشب نقشی برای بیدار شدن نیست", null, PanelStyle.SELECTED))
            return@scaffold
        }
        if (g.extra["blackNight"] == g.night.toString()) addView(kit.badge("شب سیاه: نقش‌های شهروندی امشب بی‌اثرند", Royal.crimsonLight), kit.spaced(8))
        addView(kit.grid(2, jobs.map { job -> jobTile(job) }, 8))
    }

    private fun jobTile(job: NightJob): View {
        val done = job.key in g.records
        val refusal = MafiaNight.refusal(g, job)
        return kit.horizontal().apply {
            background = PanelDrawable(kit.density, when { done -> PanelStyle.SUCCESS; refusal != null -> PanelStyle.GHOST; else -> PanelStyle.RAISED }, 16f)
            setPadding(kit.dp(8), kit.dp(8), kit.dp(8), kit.dp(10))
            addView(seal(job.glyph, job.side, 48))
            addView(kit.hgap(6))
            addView(kit.vertical().apply {
                addView(kit.text(job.title, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 2))
                addView(kit.text(when { done -> "انجام شد ✓"; refusal != null -> "غیرفعال"; else -> "در انتظار" }, TextStyle.CAPTION,
                    when { done -> Royal.turquoiseLight; refusal != null -> Royal.dim; else -> Royal.gold }, maxLines = 1))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            isClickable = true
            contentDescription = job.title
            setOnClickListener {
                kit.tap(it)
                if (job.key == MafiaNight.INTRO) host.push(MafiaIntroScreen(host, gameId)) else host.push(MafiaJobScreen(host, gameId, job.key))
            }
        }
    }

    private fun endNight() {
        val refusal = if (MafiaFlow.insomnia(g)) null else MafiaFlow.nightEndRefusal(g)
        val pending = if (refusal == null) "" else MafiaNight.jobs(g).filter { it.key !in g.records && MafiaNight.refusal(g, it) == null }.joinToString("، ") { it.title }
        val message = if (refusal == null) "شب تمام شود و روز آغاز شود؟" else "$refusal\nانجام‌نشده: $pending\nبه تشخیص راوی شب تمام شود؟"
        kit.confirm(if (g.night == 0) "پایان شب معارفه" else "پایان شب", message, if (refusal == null) "روز می‌شود" else "پایان شب به هر حال", refusal != null) {
            if (refusal != null) g.log += MafiaLog(g.phaseTitle, "راوی شب را بدون انجام همهٔ مراحل به پایان رساند")
            host.audio().stopMusic()
            val outcome = MafiaFlow.endNight(g)
            val win = MafiaFlow.checkWin(g)
            store()
            playCue(MafiaCues.DAY_START) { if (outcome.dead.isNotEmpty()) playCue(MafiaCues.DEAD_REPORT) }
            host.refresh()
            if (win != null) playCue(MafiaFlow.winCue(win))
        }
    }

    // ------------------------------------------------------------------ day

    private fun day(): View = scaffold("روز : ${MafiaText.ordinal(g.night)}", counts(), actions = listOf(toolsButton(), menuButton()),
        bottom = kit.button("شروع شب", ButtonKind.PRIMARY, RoyalIcon.NEXT, 52) { startNight() }) {
        addView(header(MafiaGlyph.CROWN, "روز می‌شود", "گزارش شب را اعلام کنید و مراحل روز را به ترتیب پیش ببرید.", listOf(MafiaCues.DAY_START, MafiaCues.DEAD_REPORT)))
        addView(report())
        g.extra["nightTrace"]?.takeIf { it.isNotBlank() && g.extra["nightLikeDay"] == (g.night - 1).toString() }?.let {
            addView(kit.section("گزارش وقایع شب گذشته", RoyalIcon.EYE))
            addView(kit.text(it, TextStyle.LABEL, Royal.ivory), kit.spaced(8))
        }
        addView(kit.section("مراحل روز", RoyalIcon.FLAG))
        val d = g.day
        val interrogation = !g.extra["interrogate"].isNullOrBlank()
        if (interrogation) addView(step("بازپرسی", MafiaCues.DEFENCE, "interrogation" in d.done, true, MafiaDay.interrogated(g).joinToString("، ") { it.name }) {
            if ("interrogation" in d.done) kit.toast("بازپرسی امروز انجام شده است") else host.push(MafiaInterrogationScreen(host, gameId))
        })
        val speakOk = !interrogation || "interrogation" in d.done
        addView(step("صحبت", MafiaCues.SPEAKING, d.speakingDone, speakOk, if (d.speakingDone) "انجام شد" else "زمان صحبت ${kit.n(mafiaSettings.speakTime)} ثانیه") {
            host.push(MafiaSpeakingScreen(host, gameId))
        })
        if (!g.extra["jigsaw"].isNullOrBlank()) addView(step("خروج با اره", null, "jigsaw" in d.done, d.speakingDone, MafiaDay.saw(g)?.first?.let { "اره دست ${it.name}" }) {
            if ("jigsaw" in d.done) kit.toast("خروج با اره انجام شده است") else host.push(MafiaSawScreen(host, gameId))
        })
        val bombOk = !MafiaDay.bombPending(g)
        if (MafiaDay.bombHolder(g) != null || "bomb" in d.done) addView(step("خنثی سازی بمب", null, "bomb" in d.done, d.speakingDone, MafiaDay.bombHolder(g)?.let { "بمب جلوی ${it.name}" }) {
            if ("bomb" in d.done) kit.toast("بمب امروز تعیین تکلیف شده است") else host.push(MafiaBombScreen(host, gameId))
        })
        if (MafiaFlow.shakeHandDue(g) && g.night > 0) {
            addView(step("دست دادن", null, "shake" in d.done, d.speakingDone, "تعداد بازیکنان به ${kit.n(3)} نفر رسیده است") { host.push(MafiaShakeHandScreen(host, gameId)) })
        } else if (MafiaDay.capoDay(g)) {
            addView(step("تفنگ کاپو", null, "capo" in d.done, d.speakingDone && bombOk, "معتمد شهر و دو تفنگ") {
                if ("capo" in d.done) kit.toast("تفنگ کاپو امروز انجام شده است") else host.push(MafiaCapoScreen(host, gameId))
            })
        } else if (g.night > 0) {
            addView(step("رای گیری دور اول", MafiaCues.VOTE_FIRST, d.firstVoteDone, d.speakingDone && bombOk,
                if (d.firstVoteDone) "${kit.n(d.defenders.size)} نفر به دفاع رفتند" else "رای لازم برای به دفاع رفتن بازیکن : ${kit.n(MafiaFlow.firstEdge(g, mafiaSettings))}") {
                host.push(MafiaVoteScreen(host, gameId, false))
            })
            if (MafiaFlow.defenceAvailable(g)) addView(step("دفاعیه", MafiaCues.DEFENCE, d.defenceDone, d.firstVoteDone, d.defenders.mapNotNull { g.player(it)?.name }.joinToString("، ")) {
                host.push(MafiaDefenceScreen(host, gameId))
            })
            if (d.defenders.isNotEmpty()) addView(step("رای گیری دور دوم", MafiaCues.VOTE_SECOND, d.secondVoteDone, d.defenceDone || !MafiaFlow.defenceAvailable(g), null) {
                host.push(MafiaVoteScreen(host, gameId, true))
            })
            if (MafiaFlow.mayorAvailable(g) || (d.mayorDone && g.inGame(MafiaRole.MAYOR))) addView(step("شهردار", null, d.mayorDone, d.secondVoteDone, d.mayorText.ifBlank { null }) { mayor() })
            addView(step("نتیجه رای گیری", MafiaCues.VOTE_RESULT, d.resultDone, d.firstVoteDone && (d.defenders.isEmpty() || d.secondVoteDone), null) { voteResult() })
        }
        addView(kit.section("ابزار روز", RoyalIcon.SLIDERS))
        val tools = ArrayList<View>()
        if (MafiaFlow.groupQueryAvailable(g, mafiaSettings)) tools += tool(MafiaGlyph.SCALES, "استعلام گروهی", "${kit.n(MafiaFlow.queryCount(g, mafiaSettings) - g.queriesUsed)} بار مانده") { groupQuery() }
        if (g.inGame(MafiaRole.DIE_HARD) && g.extra["dieHardQuery"] == g.night.toString()) tools += tool(MafiaGlyph.HELMET, "استعلام جان سخت", "نقش خارج‌شده‌ها") {
            playersDialog("استعلام جان سخت", g.out)
        }
        g.extra["zodiacLetter"]?.takeIf { it.isNotBlank() }?.let { letter -> tools += tool(MafiaGlyph.ZODIAC, "نامه زودیاک", "خواندن نامه") { kit.dialog("نامه زودیاک", letter, null, listOf(DialogAction("بستن"))).show() } }
        val acts = MafiaDay.actions(g)
        tools += tool(MafiaGlyph.PISTOL, "اقدام روز نقش‌ها", if (acts.isEmpty()) "خروج دستی در روز" else acts.joinToString("، ") { it.title }) { host.push(MafiaDayActionScreen(host, gameId)) }
        tools += tool(MafiaGlyph.COMPASS, "ترتیب نشستن", "جابه‌جایی و سر صحبت") { host.push(MafiaSeatingScreen(host, gameId)) }
        tools += tool(MafiaGlyph.HOURGLASS, "تایمر", "زمان آزاد") { freeTimer() }
        addView(kit.grid(2, tools, 8))
    }

    private fun report(): View = kit.panel(PanelStyle.NORMAL, 12).apply {
        layoutParams = kit.spaced(10)
        addView(kit.horizontal().apply {
            addView(kit.weight(kit.text(if (g.night == 1) "وقایع شب معارفه" else "وقایع شب گذشته", TextStyle.HEADING, Royal.goldLight)))
            addView(cueButton(MafiaCues.DEAD_REPORT, 36))
        })
        g.lastReport.forEach { addView(kit.text(it.trim(), TextStyle.BODY_BOLD, if (it.contains("کشته ای نداشتیم")) Royal.turquoiseLight else Royal.ivory)) }
        if (g.lastDead.isNotEmpty()) {
            addView(kit.gap(6))
            addView(kit.text("گزارش کشته های شب", TextStyle.LABEL_BOLD, Royal.crimsonLight))
            addView(kit.grid(4, g.lastDead.mapNotNull { g.player(it) }.map { playerTile(it, false, false, null) }, 6))
        }
    }

    private fun step(title: String, cue: String?, done: Boolean, enabled: Boolean, caption: String?, onClick: () -> Unit): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, when { done -> PanelStyle.SUCCESS; enabled -> PanelStyle.RAISED; else -> PanelStyle.GHOST }, 16f)
        setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(12))
        layoutParams = kit.spaced(6)
        addView(kit.icon(if (done) RoyalIcon.CHECK else RoyalIcon.NEXT, if (done) Royal.turquoiseLight else if (enabled) Royal.gold else Royal.dim, 24))
        addView(kit.hgap(10))
        addView(kit.vertical().apply {
            addView(kit.text(title, TextStyle.BODY_BOLD, if (enabled || done) Royal.ivory else Royal.dim))
            if (!caption.isNullOrBlank()) addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, maxLines = 2))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (cue != null) addView(cueButton(cue, 36))
        isClickable = true
        contentDescription = title
        setOnClickListener {
            kit.tap(it)
            if (!enabled && !done) kit.confirm(title, "مرحلهٔ قبل هنوز انجام نشده است.\nبه تشخیص راوی این مرحله باز شود؟", "باز کن") { onClick() } else onClick()
        }
    }

    private fun tool(glyph: MafiaGlyph, title: String, caption: String, onClick: () -> Unit): View = kit.horizontal().apply {
        background = PanelDrawable(kit.density, PanelStyle.NORMAL, 16f)
        setPadding(kit.dp(8), kit.dp(8), kit.dp(8), kit.dp(10))
        addView(seal(glyph, null, 40))
        addView(kit.hgap(6))
        addView(kit.vertical().apply {
            addView(kit.text(title, TextStyle.LABEL_BOLD, Royal.ivory, maxLines = 1))
            addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, maxLines = 1))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        isClickable = true
        setOnClickListener { kit.tap(it); onClick() }
    }

    private fun mayor() {
        if (g.day.mayorDone) { kit.toast(g.day.mayorText); return }
        MafiaFlow.mayorRefusal(g)?.let { kit.toast(it); return }
        kit.dialog("شهردار", "آیا ${MafiaRole.MAYOR.faName} میخواهد کاری انحام دهد؟\n${MafiaRole.MAYOR.faName} رای خروج می دهد یا ملغی میکند؟", null, listOf(
            DialogAction("خروج", ButtonKind.DANGER) {
                pickPlayers("کدامیک از بازیکنان برای خروج انتخاب می شوند؟", g.day.defenders.mapNotNull { g.player(it) }, 1, 1, false) { ids ->
                    MafiaFlow.mayor(g, ids.first()); store(); host.refresh()
                }
            },
            DialogAction("ملغی", ButtonKind.PRIMARY) { MafiaFlow.mayor(g, null); store(); host.refresh() },
            DialogAction("کاری انجام نمی‌دهد") { g.day.mayorDone = true; g.day.mayorText = "شهردار کاری انجام نداد"; store(); host.refresh() }
        )).show()
    }

    private fun voteResult() {
        if (g.day.resultDone) { kit.toast("نتیجه رای کیری پیش از این نمایش داده شده است"); return }
        playCue(MafiaCues.VOTE_RESULT)
        val (exiting, tie) = MafiaFlow.voteOutcome(g, mafiaSettings)
        if (tie) { host.push(MafiaLotteryScreen(host, gameId, exiting)); return }
        val body = if (exiting.isEmpty()) null else kit.grid(3, exiting.mapNotNull { g.player(it) }.map { playerTile(it, false, true) }, 6)
        kit.dialog("نتیجه رای گیری", MafiaFlow.voteResultText(g, exiting), body, listOf(DialogAction("تایید", ButtonKind.PRIMARY) {
            if (exiting.isEmpty()) {
                g.day.resultDone = true
                g.players.forEach { it.flags.removeAll(setOf("redCarpet", "greenMile", "lambs")) }
                store(); host.refresh()
            } else host.push(MafiaExitScreen(host, gameId, exiting, MafiaDeath.VOTE, voteResult = true))
        })).show()
    }

    private fun groupQuery() {
        val need = g.alive.size / 2 + 1
        kit.dialog("استعلام گروهی", "آیا آرا برای استعلام گروهی به حد نصاب رسیده است؟\n حداقل تعداد آرا : ${kit.n(need)}", null, listOf(
            DialogAction("بله", ButtonKind.PRIMARY) {
                val text = MafiaFlow.groupQuery(g)
                store()
                kit.dialog("استعلام گروهی", text, null, listOf(DialogAction("بستن") { host.refresh() })).show()
            },
            DialogAction("خیر")
        )).show()
    }

    private fun freeTimer() {
        val timers = ArrayList<MafiaTimer>()
        val time = kit.text(clock(mafiaSettings.speakTime), TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER)
        val timer = MafiaTimer(this, mafiaSettings.speakTime) { left, _ -> time.text = clock(left) }
        timers += timer
        val body = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(time)
            addView(kit.flow(listOf(15, 30, 45, 60, 90, 120).map { s -> kit.chip(kit.n(s) + " ثانیه", false) { timer.reset(s) } }, 6))
        }
        val dialog = kit.dialog("تایمر", null, body, listOf(
            DialogAction("شروع", ButtonKind.SUCCESS, dismiss = false) { timer.start() },
            DialogAction("توقف", ButtonKind.SECONDARY, dismiss = false) { timer.pause() },
            DialogAction("بستن") { timer.stop() }
        ))
        dialog.setOnDismissListener { timer.stop() }
        dialog.show()
    }

    private fun startNight() {
        val refusal = MafiaFlow.dayEndRefusal(g)
        val win = MafiaFlow.checkWin(g)
        if (win != null) { store(); playCue(MafiaFlow.winCue(win)); host.refresh(); return }
        val message = if (refusal == null) "روز تمام شود و شب ${MafiaText.ordinal(g.night)} آغاز شود؟" else "$refusal\nبه تشخیص راوی روز تمام شود و شب آغاز شود؟"
        kit.confirm("شروع شب", message, if (refusal == null) "شب می‌شود" else "شروع شب به هر حال", refusal != null) {
            if (refusal != null) g.log += MafiaLog(g.phaseTitle, "راوی روز را بدون انجام همهٔ مراحل به پایان رساند")
            MafiaFlow.startNight(g)
            store()
            playCue(MafiaCues.NIGHT_START) { if (mafiaSettings.nightMusic.isSet) host.post(3000) { startNightMusic() } }
            host.refresh()
        }
    }

    // ------------------------------------------------------------------ finished

    private fun finished(): View = scaffold("پایان بازی", g.phaseTitle, actions = listOf(toolsButton(), menuButton())) {
        addView(kit.panel(PanelStyle.RAISED, 16).apply {
            layoutParams = kit.spaced(10)
            gravity = Gravity.CENTER_HORIZONTAL
            addView(seal(MafiaGlyph.CROWN, null, 110).apply { (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL })
            addView(kit.text(g.winner, TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER))
            addView(kit.gap(6))
            addView(cueButton(MafiaFlow.winCue(g.winner), 44).apply { (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL })
        })
        addView(kit.section("نقش‌ها", RoyalIcon.PLAYERS))
        addView(kit.grid(3, g.players.map { playerTile(it, true, false, if (!it.alive) MafiaDeath.title(it.deathType) else "در بازی") }, 6))
        addView(kit.gap(10))
        addView(kit.button("گزارش بازی", ButtonKind.PRIMARY, RoyalIcon.HISTORY, 50) { host.push(MafiaReportScreen(host, gameId)) }, kit.spaced(8))
        addView(kit.button("شروع بازی جدید", ButtonKind.SECONDARY, RoyalIcon.PLAY, 50) { host.resetTo(HomeScreen(host), MafiaHomeScreen(host), MafiaDesignScreen(host)) })
    }

    // ------------------------------------------------------------------ menu

    private fun menu() {
        val items = ArrayList<DialogAction>()
        items += DialogAction("ابزار راوی", ButtonKind.PRIMARY) { host.push(MafiaToolsScreen(host, gameId)) }
        items += DialogAction("ترتیب نشستن") { host.push(MafiaSeatingScreen(host, gameId)) }
        items += DialogAction("بازیکنان حاضر در بازی") { playersDialog("بازیکنان حاضر در بازی", g.alive) }
        items += DialogAction("بازیکنان خارج از بازی") { playersDialog("بازیکنان خارج از بازی", g.out) }
        if (!g.finished) {
            items += DialogAction("سکوت انضباطی") {
                pickPlayers("سکوت انضباطی", g.alive, 0, g.alive.size, false, g.alive.filter { it.has("discipline") }.map { it.id }.toSet()) { ids ->
                    g.alive.forEach { p ->
                        val on = p.id in ids
                        if (on && !p.has("discipline")) g.log += MafiaLog(g.phaseTitle, "${p.name} به دلیل انضباطی ساکت شد")
                        if (on) p.flags += "discipline" else p.flags -= "discipline"
                    }
                    store(); host.refresh()
                }
            }
            items += DialogAction("خروج انضباطی", ButtonKind.DANGER) {
                pickPlayers("خروج انضباطی", g.alive, 1, g.alive.size, false) { ids ->
                    ids.forEach { MafiaFlow.exit(g, it, MafiaDeath.DISCIPLINE); g.log += MafiaLog(g.phaseTitle, "${g.player(it)?.name} به دلیل انضباطی از بازی خارج شد") }
                    MafiaFlow.checkWin(g)
                    store(); host.refresh()
                }
            }
            val sent = g.out.filter { it.deathType == MafiaDeath.DISCIPLINE }
            if (sent.isNotEmpty()) items += DialogAction("بازگشت به بازی") {
                pickPlayers("لیست نفرات خروج انضباطی", sent, 1, sent.size, false) { ids -> ids.forEach { MafiaFlow.returnPlayer(g, it) }; store(); host.refresh() }
            }
        }
        items += DialogAction("گزارش بازی") { host.push(MafiaReportScreen(host, gameId)) }
        if (g.snapshots.size > 1) items += DialogAction("بازگشت به گذشته") { host.push(MafiaPastScreen(host, gameId)) }
        items += DialogAction("یادداشت گرداننده") { note() }
        if (g.inGame(MafiaRole.CORONA)) items += DialogAction("بیماران کرونایی") { playersDialog("بیماران کرونایی", g.players.filter { it.marks.containsKey("infected") }) }
        items += DialogAction("تنظیمات بازی") { host.push(MafiaSettingsScreen(host)) }
        items += DialogAction("بستن")
        kit.dialog("منوی راوی", null, null, items).show()
    }

    private fun note() {
        val input: EditText = kit.field("یادداشت", g.note).apply {
            setSingleLine(false)
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
        }
        kit.dialog("یادداشت", null, kit.vertical().apply { addView(input, kit.fill()) }, listOf(
            DialogAction("پایان", ButtonKind.PRIMARY) { g.note = input.text.toString(); store() },
            DialogAction("لغو")
        )).show()
    }
}

/** «معارفه»: the intro-night queue, one step at a time. */
class MafiaIntroScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private var index = 0
    private var played = -1

    override fun build(): View {
        if (!load()) return missing()
        val queue = MafiaFlow.introQueue(g)
        val step = queue[index.coerceIn(0, queue.lastIndex)]
        return scaffold("معارفه", "مرحلهٔ ${kit.n(index + 1)} از ${kit.n(queue.size)}",
            bottom = kit.horizontal().apply {
                if (index > 0) {
                    addView(kit.button("قبلی", ButtonKind.SECONDARY, RoyalIcon.BACK, 52) { index--; host.refresh() }, LinearLayout.LayoutParams(kit.dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(kit.hgap(8))
                }
                addView(kit.weight(kit.button(step.button, ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) {
                    if (index < queue.lastIndex) { index++; host.refresh() } else finish()
                }))
            }) {
            addView(kit.progress((index + 1f) / queue.size, Royal.gold, 8).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = kit.dp(10) })
            val role = MafiaRole.values().firstOrNull { it.name == step.key }
            addView(kit.horizontal(Gravity.CENTER).apply {
                layoutParams = kit.spaced(10)
                if (role != null) addView(roleCard(role), LinearLayout.LayoutParams(kit.dp(150), kit.dp((150 * RoleCardView.RATIO).toInt())))
                else addView(seal(step.glyph, if (step.key == "MAFIA_GROUP" || step.key == "ROLE_SLEEP") MafiaSide.MAFIA else null, 130))
            })
            addView(narration(step.text, step.cue))
            if (step.players.isNotEmpty()) {
                addView(kit.section("بازیکنان این نقش", RoyalIcon.PLAYERS))
                addView(kit.grid(3, step.players.mapNotNull { g.player(it) }.map { playerTile(it, true, false) }, 6))
            }
        }
    }

    override fun onShow() { autoPlay() }

    private fun autoPlay() {
        if (!mafiaSettings.autoPlay || played == index || !ready) return
        played = index
        val step = MafiaFlow.introQueue(g).getOrNull(index) ?: return
        if (index == 0) playCue(MafiaCues.INTRO_START) { playCue(step.cue) } else playCue(step.cue)
    }

    private fun finish() {
        val job = MafiaNight.jobs(g).first { it.key == MafiaNight.INTRO }
        MafiaNight.perform(g, job, job.modes.first(), emptyList(), null, 0, "")
        g.log += MafiaLog(g.phaseTitle, "معارفه انجام شد")
        store()
        host.pop()
    }

    override fun onHide() { host.audio().stopCue() }
}

/** One night job: wake text, its cue, target choice, immediate answer, done / cancel. */
class MafiaJobScreen(host: MainActivity, gameId: Long, private val key: String) : MafiaScreen(host, gameId) {
    private var modeIndex = 0
    private val chosen = ArrayList<Int>()
    private var guess: MafiaRole? = null
    private var number = 0
    private var word = ""
    private val types = HashMap<Int, String>()
    private var wakePlayed = false
    /** The narrator chose to run a blocked job anyway. */
    private var forced = false

    private fun wakeCue(job: NightJob): String? = when {
        job.key == MafiaNight.MAFIA_GROUP -> MafiaCues.MAFIA_WAKE
        job.role != null -> MafiaCues.wake(job.role)
        else -> null
    }

    private fun doneCues(job: NightJob): List<String> = when {
        job.key == MafiaNight.MAFIA_GROUP -> listOf(MafiaCues.MAFIA_DONE, MafiaCues.MAFIA_SLEEP)
        job.role != null -> listOf(MafiaCues.done(job.role), MafiaCues.sleep(job.role))
        else -> emptyList()
    }

    private fun playDone(job: NightJob) {
        val cues = doneCues(job)
        if (cues.isEmpty()) return
        playCue(cues[0]) { if (cues.size > 1) playCue(cues[1]) }
    }

    override fun build(): View {
        if (!load()) return missing()
        val job = MafiaNight.jobs(g).firstOrNull { it.key == key } ?: return scaffold("کار شب") {
            addView(kit.text("این کار امشب در دسترس نیست.", TextStyle.BODY, Royal.muted, Gravity.CENTER))
        }
        val record = g.records[key]
        val refusal = if (forced) null else MafiaNight.refusal(g, job)
        val decoy = MafiaNight.isDecoy(g, job)
        val mode = job.modes.getOrElse(modeIndex) { job.modes.first() }
        val bottom: View = when {
            record != null -> kit.horizontal().apply {
                if (key != MafiaNight.cardKey(MafiaLastCard.CARD_EXCHANGE)) {
                    addView(kit.weight(kit.button("لغو عملیات", ButtonKind.DANGER, RoyalIcon.UNDO, 50) { MafiaNight.cancel(g, job); store(); host.refresh() }))
                    addView(kit.hgap(8))
                }
                addView(kit.weight(kit.button("بازگشت", ButtonKind.SECONDARY, RoyalIcon.BACK, 50) { host.pop() }))
            }
            refusal != null -> kit.horizontal().apply {
                addView(kit.weight(kit.button("انجام به تشخیص راوی", ButtonKind.SECONDARY, RoyalIcon.SLIDERS, 52) { forced = true; host.refresh() }))
                addView(kit.hgap(8))
                addView(kit.weight(kit.button("بازگشت", ButtonKind.PRIMARY, RoyalIcon.BACK, 52) { playDone(job); host.pop() }))
            }
            else -> kit.horizontal().apply {
                if (mode.min == 0 || job.role != null) {
                    addView(kit.button("کاری انجام نداد", ButtonKind.GHOST, null, 52) {
                        MafiaNight.skip(g, job); store(); playDone(job); host.pop()
                    }, LinearLayout.LayoutParams(kit.dp(128), ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(kit.hgap(8))
                }
                addView(kit.weight(kit.button("انجام شد", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) { done(job, mode) }))
            }
        }
        return scaffold(job.title, "شب : ${MafiaText.ordinal(g.night)}", bottom = bottom) {
            addView(kit.horizontal().apply {
                layoutParams = kit.spaced(10)
                if (job.role != null) addView(roleCard(job.role, dead = decoy), LinearLayout.LayoutParams(kit.dp(118), kit.dp((118 * RoleCardView.RATIO).toInt())))
                else addView(seal(job.glyph, job.side, 110))
                addView(kit.hgap(10))
                addView(kit.vertical().apply {
                    addView(kit.text(job.title, TextStyle.TITLE, Royal.goldLight))
                    val names = job.holders.mapNotNull { g.player(it) }
                    if (names.isNotEmpty()) addView(kit.text(names.joinToString("، ") { it.name + if (!it.alive) " (بیرون)" else "" }, TextStyle.BODY_BOLD, Royal.ivory))
                    job.role?.let { r -> MafiaNight.remainingText(g, r)?.let { addView(kit.text(it, TextStyle.CAPTION, Royal.muted)) } }
                    if (decoy) addView(kit.badge("صاحب نقش بیرون است؛ صوری اجرا کنید", Royal.crimsonLight).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = kit.dp(4) }
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(narration(job.prompt, wakeCue(job)))
            if (record != null) {
                addView(kit.panel(PanelStyle.SUCCESS, 12).apply {
                    layoutParams = kit.spaced(8)
                    addView(kit.text("انجام شد", TextStyle.HEADING, Royal.turquoiseLight))
                    val names = record.targets.mapNotNull { g.player(it)?.name }.joinToString("، ")
                    if (record.choice.isNotBlank()) addView(kit.text(record.choice, TextStyle.BODY_BOLD, Royal.ivory))
                    if (names.isNotBlank()) addView(kit.text("انتخاب: $names", TextStyle.BODY, Royal.ivory))
                    record.guess?.let { addView(kit.text("حدس نقش: ${it.faName}", TextStyle.BODY, Royal.ivory)) }
                    if (record.result.isNotBlank()) addView(kit.text(record.result, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
                })
                return@scaffold
            }
            if (refusal != null) {
                addView(kit.panel(PanelStyle.DANGER, 12).apply {
                    layoutParams = kit.spaced(8)
                    addView(kit.text(refusal, TextStyle.HEADING, Royal.crimsonLight, Gravity.CENTER))
                })
                return@scaffold
            }
            if (job.modes.size > 1) {
                addView(kit.text(if (job.key == MafiaNight.MAFIA_GROUP) "کار امشب گروه مافیا" else "انتخاب", TextStyle.LABEL_BOLD, Royal.gold))
                addView(kit.flow(job.modes.mapIndexed { i, m ->
                    kit.chip(m.label, i == modeIndex, ButtonKind.CHIP_GOLD) { modeIndex = i; chosen.clear(); guess = null; host.refresh() }.apply { setPadding(kit.dp(12), kit.dp(4), kit.dp(12), kit.dp(6)) }
                }, 6), kit.spaced(10))
            }
            if (mode.pool != TargetPool.NONE) {
                val candidates = MafiaNight.candidates(g, job, mode)
                addView(kit.section(if (mode.max > 1) "انتخاب ${kit.n(mode.min)} تا ${kit.n(mode.max)} نفر" else "انتخاب یک نفر", RoyalIcon.USER))
                if (candidates.isEmpty()) addView(kit.text("بازیکنی برای انتخاب نیست", TextStyle.LABEL, Royal.muted, Gravity.CENTER))
                addView(kit.grid(3, candidates.map { p ->
                    val i = chosen.indexOf(p.id)
                    playerTile(p, true, i >= 0, if (i >= 0 && mode.max > 1) kit.n(i + 1) else null) {
                        if (p.id in chosen) chosen.remove(p.id)
                        else { if (chosen.size >= mode.max) chosen.removeAt(0); chosen += p.id }
                        host.refresh()
                    }
                }, 6), kit.spaced(8))
            }
            if (mode.typed.isNotEmpty() && chosen.isNotEmpty()) {
                chosen.forEach { id ->
                    val current = types[id] ?: mode.typed.first()
                    addView(kit.choiceRow(g.player(id)?.name ?: "", null, mode.typed.mapIndexed { i, t -> i to t }, mode.typed.indexOf(current)) { i -> types[id] = mode.typed[i] })
                }
            }
            if (mode.guessRole) {
                addView(kit.button(guess?.let { "حدس نقش: ${it.faName}" } ?: "انتخاب نقش برای حدس", ButtonKind.SECONDARY, RoyalIcon.CARDS, 46) {
                    rolePicker("حدس نقش", g.players.mapNotNull { it.role }.distinct().sortedBy { it.ordinal }) { r -> guess = r; host.refresh() }
                }, kit.spaced(8))
            }
            mode.number?.let { range ->
                addView(kit.text("عدد", TextStyle.LABEL_BOLD, Royal.gold))
                addView(kit.flow(range.map { n -> kit.chip(kit.n(n), number == n, ButtonKind.CHIP_GOLD) { number = n; host.refresh() } }, 6), kit.spaced(8))
            }
            if (mode.word) {
                addView(kit.field(if (job.role == MafiaRole.VOODOO) "کلمهٔ ممنوعه" else "متن", word).apply {
                    setOnFocusChangeListener { v, has -> if (!has) word = (v as EditText).text.toString() }
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(s: android.text.Editable?) { word = s?.toString() ?: "" }
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    })
                }, kit.spaced(8))
            }
            if (job.role == MafiaRole.NOSTRADAMUS) {
                addView(kit.choiceRow("نوستراداموس با شهر بازی میکند یا با مافیا", null, listOf(0 to "شهر", 1 to "مافیا"), if (word == "مافیا") 1 else if (word == "شهر") 0 else -1) { i ->
                    word = if (i == 0) "شهر" else "مافیا"
                })
            }
        }
    }

    override fun onShow() {
        if (wakePlayed || !mafiaSettings.autoPlay || !ready) return
        wakePlayed = true
        val job = MafiaNight.jobs(g).firstOrNull { it.key == key } ?: return
        if (key !in g.records) wakeCue(job)?.let { playCue(it) }
    }

    private fun done(job: NightJob, mode: JobMode) {
        if (mode.pool != TargetPool.NONE && chosen.size < mode.min) { kit.toast("دست‌کم ${kit.n(mode.min)} نفر را انتخاب کنید"); return }
        if (mode.guessRole && guess == null) { kit.toast("نقش را برای حدس انتخاب کنید"); return }
        if (mode.number != null && number !in mode.number) { kit.toast("عدد را انتخاب کنید"); return }
        if (job.role == MafiaRole.NOSTRADAMUS && word.isBlank()) { kit.toast("سمت نوستراداموس را انتخاب کنید"); return }
        val text = if (mode.typed.isNotEmpty()) chosen.joinToString(",") { types[it] ?: mode.typed.first() } else word.trim()
        val result = MafiaNight.perform(g, job, mode, chosen.toList(), guess, number, text)
        store()
        playDone(job)
        if (result.isNotBlank()) {
            kit.dialog(job.title, result, null, listOf(DialogAction("تایید", ButtonKind.PRIMARY) { host.pop() })).show()
        } else host.pop()
    }

    override fun onHide() { }
}
