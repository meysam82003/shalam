package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Shows a day action's result; when somebody leaves, the exit flow (with last-move card) follows. */
fun MafiaScreen.resultThenExit(title: String, message: String, exits: List<Int>, type: String, allowCard: Boolean = true, replace: Boolean = false, typeOf: Map<Int, String> = emptyMap()) {
    store()
    val body = if (exits.isEmpty()) null else kit.grid(3, exits.mapNotNull { g.player(it) }.map { playerTile(it, false, true) }, 6)
    kit.dialog(title, message, body, listOf(DialogAction("تایید", ButtonKind.PRIMARY) {
        if (exits.isEmpty()) host.refresh()
        else {
            val screen = MafiaExitScreen(host, gameId, exits, type, allowCard = allowCard, typeOf = typeOf)
            if (replace) host.replace(screen) else host.push(screen)
        }
    })).show()
}

/** Exits without will or card (bomb, suicide blast…) and checks the win right away. */
fun MafiaScreen.exitNow(ids: List<Int>, type: String) {
    ids.forEach { MafiaFlow.exit(g, it, type) }
    val win = MafiaFlow.checkWin(g)
    store()
    if (win != null) playCue(MafiaFlow.winCue(win))
}

/** «اقدام روز نقش‌ها»: every day action of the roles in this game. */
class MafiaDayActionScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    override fun build(): View {
        if (!load()) return missing()
        val actions = MafiaDay.actions(g)
        return scaffold("اقدام روز نقش‌ها", "${kit.n(actions.size)} اقدام در دسترس") {
            if (actions.isEmpty()) addView(kit.text("الان نقشی اقدام روز ندارد.", TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(8))
            actions.forEach { a ->
                addView(kit.horizontal().apply {
                    background = PanelDrawable(kit.density, if (a.refusal == null) PanelStyle.RAISED else PanelStyle.GHOST, 16f)
                    setPadding(kit.dp(10), kit.dp(10), kit.dp(10), kit.dp(12))
                    layoutParams = kit.spaced(8)
                    addView(seal(a.role.glyph, a.role.side, 48))
                    addView(kit.hgap(8))
                    addView(kit.vertical().apply {
                        addView(kit.text(a.title, TextStyle.BODY_BOLD, Royal.goldLight))
                        addView(kit.text(a.refusal ?: a.caption, TextStyle.CAPTION, if (a.refusal == null) Royal.muted else Royal.crimsonLight, maxLines = 3))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(kit.iconButton(RoyalIcon.RULES, "توضیح ${a.role.faName}", ButtonKind.CHIP, 38) { roleInfo(a.role, g.options[a.role] ?: emptyMap()) })
                    isClickable = true
                    contentDescription = a.title
                    setOnClickListener { kit.tap(it); if (a.refusal != null) kit.toast(a.refusal) else run(a) }
                })
            }
            addView(kit.section("خروج دستی در روز", RoyalIcon.FLAG))
            addView(kit.button("انتخاب بازیکنان خارج‌شده", ButtonKind.DANGER, RoyalIcon.USER, 48) {
                pickPlayers("چه کسانی از بازی خارج می‌شوند؟", g.alive, 1, g.alive.size, false) { ids ->
                    host.replace(MafiaExitScreen(host, gameId, ids, MafiaDeath.DAY_SHOT))
                }
            }, kit.spaced(6))
        }
    }

    private fun run(a: DayAction) {
        when (a.key) {
            MafiaDay.TERROR -> {
                val t = g.aliveHolder(MafiaRole.TERRORIST) ?: return
                pickPlayers("تروریست چه کسی را ترور می‌کند؟", MafiaDay.terrorTargets(g), 1, 1, false) { ids ->
                    val target = ids.first()
                    kit.confirm("ترور", "بازیکن ${g.player(target)?.name} توسط تروریست انتخاب شده است\nآیا تایید میکنید؟\nدرصورت تایید قابل لغو نخواهد بود", "تایید", true) {
                        t.revealed = true
                        g.log += MafiaLog(g.phaseTitle, "${t.name} (تروریست) ${g.player(target)?.name} را ترور کرد")
                        resultThenExit("ترور", "تروریست و بازیکن انتخابی از بازی خارج می‌شوند", listOf(target, t.id), MafiaDeath.TERROR, replace = true)
                    }
                }
            }
            MafiaDay.COWBOY -> {
                val c = g.aliveHolder(MafiaRole.COWBOY) ?: return
                pickPlayers("کابوی چه کسی را با خود بیرون می‌برد؟", g.alive.filter { it.id != c.id }, 1, 1, false) { ids ->
                    MafiaDay.cowboy(g)
                    exitNow(listOf(c.id), MafiaDeath.COWBOY)
                    g.log += MafiaLog(g.phaseTitle, "${c.name} (کابوی) ${g.player(ids.first())?.name} را با خود بیرون برد")
                    resultThenExit("کابوی", "کابوی نقش خود را افشا کرد و ${g.player(ids.first())?.name} را با خود بیرون می‌برد؛ بازیکن انتخابی حق کارت حرکت آخر دارد", ids, MafiaDeath.COWBOY, replace = true)
                }
            }
            MafiaDay.SUICIDE -> {
                val victims = MafiaDay.suicideVictims(g)
                kit.confirm("بمبر", "آیا بمبر میخواهد عملیاتش را انجام دهد؟\n" + victims.mapNotNull { g.player(it)?.name }.joinToString("، ") + " از بازی خارج می‌شوند", "انفجار", true) {
                    g.aliveHolder(MafiaRole.SUICIDE_BOMBER)?.revealed = true
                    g.log += MafiaLog(g.phaseTitle, "بمبر بین بازیکنان کناری خود منفجر شد")
                    exitNow(victims, MafiaDeath.SUICIDE)
                    host.refresh()
                }
            }
            MafiaDay.GUN -> {
                pickPlayers("چه کسی شلیک می‌کند؟", MafiaDay.gunHolders(g), 1, 1, false) { s ->
                    val shooter = s.first()
                    val targets = g.alive.filter { it.id != shooter }
                    kit.dialog("شلیک تیر", "${g.player(shooter)?.name} به چه کسی شلیک می‌کند؟", null, listOf(
                        DialogAction("انتخاب هدف", ButtonKind.PRIMARY) {
                            pickPlayers("هدف تیر", targets, 1, 1, false) { t ->
                                val (msg, exits) = MafiaDay.gunShot(g, shooter, t.first())
                                resultThenExit("شلیک تیر", msg, exits, MafiaDeath.GUN)
                            }
                        },
                        DialogAction("شلیک هوایی") { val (msg, _) = MafiaDay.gunShot(g, shooter, null); resultThenExit("شلیک تیر", msg, emptyList(), MafiaDeath.GUN) },
                        DialogAction("لغو")
                    )).show()
                }
            }
            MafiaDay.ROULETTE -> {
                val holder = g.extra["rouletteOn"]?.toIntOrNull() ?: return
                pickPlayers("${g.player(holder)?.name} به چه کسی شلیک می‌کند؟", g.alive.filter { it.id != holder }, 1, 1, false) { t ->
                    val (msg, out) = MafiaDay.rouletteShot(g, t.first())
                    resultThenExit("رولت روسی", msg, if (out) t else emptyList(), MafiaDeath.ROULETTE)
                }
            }
            MafiaDay.CURSE -> {
                val cursed = g.extra["voodooOn"]?.toIntOrNull()?.let { g.player(it) } ?: return
                kit.dialog("خروج با کلمه ممنوعه", "کلمه ممنوعه : ${g.extra["voodooWord"]}\nآیا ${cursed.name} از کلمه ممنوعه استفاده کرده است؟", null, listOf(
                    DialogAction("بله، خارج می‌شود", ButtonKind.DANGER) {
                        MafiaDay.curseUsed(g)
                        g.log += MafiaLog(g.phaseTitle, "${cursed.name} کلمهٔ ممنوعه را گفت")
                        resultThenExit("کلمه ممنوعه", "بازیکن از کلمه ممنوعه استفاده کرد و از بازی خارج می‌شود", listOf(cursed.id), MafiaDeath.CURSE)
                    },
                    DialogAction("خیر")
                )).show()
            }
            MafiaDay.SACRIFICE -> {
                pickPlayers("فدایی به جای چه کسی بیرون می‌رود؟", MafiaDay.todaysDayDead(g), 1, 1, false) { ids ->
                    MafiaDay.sacrifice(g, ids.first())
                    val win = MafiaFlow.checkWin(g)
                    store()
                    if (win != null) playCue(MafiaFlow.winCue(win))
                    host.refresh()
                }
            }
            else -> if (a.key.startsWith("G_")) generic(a)
        }
    }

    /** Day action of a data-defined role (or a role whose rules the narrator edited). */
    private fun generic(a: DayAction) {
        val r = a.role
        val holder = g.aliveHolder(r) ?: return
        val day = MafiaGeneric.def(r)?.dayAbility ?: return
        fun done(text: String) { MafiaGeneric.spendDay(g, r); g.log += MafiaLog(g.phaseTitle, text) }
        when (day) {
            MafiaDayAbility.TAKE_ALONG -> pickPlayers("${r.faName} چه کسی را با خود می‌برد؟", g.alive.filter { it.id != holder.id }, 1, 1, false) { ids ->
                holder.revealed = true
                done("${holder.name} (${r.faName}) ${g.player(ids.first())?.name} را با خود بیرون برد")
                resultThenExit(r.faName, "${r.faName} و بازیکن انتخابی از بازی خارج می‌شوند", listOf(ids.first(), holder.id), MafiaDeath.MANUAL, replace = true)
            }
            MafiaDayAbility.DAY_SHOT -> pickPlayers("${r.faName} به چه کسی شلیک می‌کند؟", g.alive.filter { it.id != holder.id }, 1, 1, false) { ids ->
                val t = g.player(ids.first()) ?: return@pickPlayers
                if (t.armor > 0) {
                    t.armor -= 1
                    done("${holder.name} (${r.faName}) به ${t.name} شلیک کرد؛ زره او شکست")
                    store()
                    kit.dialog(r.faName, "شلیک به ${t.name} با زره دفع شد", null, listOf(DialogAction("باشه") { host.refresh() })).show()
                } else {
                    done("${holder.name} (${r.faName}) به ${t.name} شلیک کرد")
                    resultThenExit(r.faName, "${t.name} با شلیک ${r.faName} از بازی خارج می‌شود", listOf(t.id), MafiaDeath.DAY_SHOT, replace = true)
                }
            }
            MafiaDayAbility.KEEP_FROM_VOTE -> {
                val out = MafiaDay.todaysDayDead(g)
                if (out.isEmpty()) { kit.toast("امروز کسی با رأی خارج نشده است"); return }
                pickPlayers("${r.faName} چه کسی را در بازی نگه می‌دارد؟", out, 1, 1, false) { ids ->
                    MafiaManual.revive(g, ids.first())
                    done("${holder.name} (${r.faName}) ${g.player(ids.first())?.name} را در بازی نگه داشت")
                    store(); host.refresh()
                }
            }
            MafiaDayAbility.REMOVE_FROM_DEFENCE -> {
                val defenders = g.day.defenders.mapNotNull { g.player(it) }.filter { it.alive }
                if (defenders.isEmpty()) { kit.toast("هنوز کسی در دفاعیه نیست"); return }
                pickPlayers("${r.faName} چه کسی را از دفاعیه بیرون می‌آورد؟", defenders, 1, 1, false) { ids ->
                    g.day.defenders.remove(ids.first())
                    done("${holder.name} (${r.faName}) ${g.player(ids.first())?.name} را از دفاعیه خارج کرد")
                    store(); host.refresh()
                }
            }
            MafiaDayAbility.GUESS_EXIT -> pickPlayers("${r.faName} نقش چه کسی را حدس می‌زند؟", g.alive.filter { it.id != holder.id }, 1, 1, false) { ids ->
                val t = g.player(ids.first()) ?: return@pickPlayers
                rolePicker("نقش ${t.name}", g.roleCounts.keys.filter { it != MafiaRole.CITIZEN && it != MafiaRole.MAFIA }) { guess ->
                    if (t.role == guess) {
                        done("${holder.name} (${r.faName}) نقش ${t.name} را درست حدس زد")
                        resultThenExit(r.faName, "حدس درست بود؛ ${t.name} از بازی خارج می‌شود", listOf(t.id), MafiaDeath.EXECUTE, replace = true)
                    } else {
                        done("${holder.name} (${r.faName}) نقش ${t.name} را اشتباه حدس زد")
                        store()
                        kit.dialog(r.faName, "حدس اشتباه بود", null, listOf(DialogAction("باشه") { host.refresh() })).show()
                    }
                }
            }
            MafiaDayAbility.NONE -> Unit
        }
    }
}

/** «تفنگ کاپو»: the first day's vote replaced by the town trustee's two guns. */
class MafiaCapoScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private val timers = ArrayList<MafiaTimer>()
    private var shotGun = 0
    private var shotTarget: Int? = null
    private var air = false

    override fun build(): View {
        if (!load()) return missing()
        timers.forEach { it.stop() }
        timers.clear()
        val stage = g.extra["capoStage"]?.toIntOrNull() ?: 0
        val trustee = g.extra["capoTrustee"]?.toIntOrNull()?.let { g.player(it) }
        val picks = g.extra["capoPicks"]?.split(",")?.mapNotNull { it.toIntOrNull()?.let { id -> g.player(id) } } ?: emptyList()
        fun next(s: Int) { g.extra["capoStage"] = s.toString(); store(); host.refresh() }
        return when (stage) {
            0 -> scaffold("تفنگ کاپو", "انتخاب معتمد شهر") {
                addView(narration("انتخاب معتمد شهر", null))
                addView(kit.grid(3, g.alive.map { p -> playerTile(p, false, false) {
                    g.extra["capoTrustee"] = p.id.toString(); g.log += MafiaLog(g.phaseTitle, "${p.name} معتمد شهر شد"); next(1)
                } }, 6))
            }
            1 -> scaffold("تفنگ کاپو", "معتمد شهر: ${trustee?.name}") {
                addView(narration("گروه مافیا از خواب بیدار شوند\nتفنگ جنگی را برای معتمد شهر انتخاب کنند", MafiaCues.MAFIA_WAKE))
                addView(kit.grid(2, listOf(1, 2).map { n ->
                    kit.button("تفنگ شماره : ${kit.n(n)}", ButtonKind.PRIMARY, RoyalIcon.CHECK, 64) { g.extra["capoGun"] = n.toString(); playCue(MafiaCues.MAFIA_SLEEP); next(2) }
                }, 8))
            }
            2 -> {
                val chosen = ArrayList(picks.map { it.id })
                scaffold("تفنگ کاپو", "معتمد شهر: ${trustee?.name}", bottom = kit.button("تایید", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) {
                    if (chosen.size != 2) kit.toast("دو نفر را انتخاب کنید") else { g.extra["capoPicks"] = chosen.joinToString(","); next(3) }
                }) {
                    addView(narration("معتمد شهر پس از پابان صحبت هایش دو نفر را انتخاب کند", null))
                    addView(kit.grid(3, g.alive.filter { it.id != trustee?.id }.map { p -> playerTile(p, false, p.id in chosen) {
                        if (p.id in chosen) chosen.remove(p.id) else { if (chosen.size >= 2) chosen.removeAt(0); chosen += p.id }
                        g.extra["capoPicks"] = chosen.joinToString(","); store(); host.refresh()
                    } }, 6))
                }
            }
            3 -> scaffold("تفنگ کاپو", "دفاعیه", bottom = kit.button("پایان", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) { next(4) }) {
                addView(narration("افراد انتخاب شده از خودشون به ترتیب دفاع کنند", MafiaCues.DEFENCE))
                picks.forEach { p -> addView(timerRow(p, mafiaSettings.defenceTime, null, MafiaFlow.silenced(p)?.takeIf { p.has("lambs") }, timers)) }
            }
            else -> scaffold("تفنگ کاپو", "شلیک", bottom = kit.button("شلیک", ButtonKind.DANGER, RoyalIcon.CHECK, 52) {
                if (shotGun == 0) { kit.toast("شماره تفنگ را انتخاب کنید"); return@button }
                if (!air && shotTarget == null) { kit.toast("هدف را انتخاب کنید"); return@button }
                val (msg, out) = MafiaDay.capoShot(g, shotGun, if (air) null else shotTarget)
                g.day.done += "capo"
                g.day.resultDone = true
                resultThenExit("تفنگ کاپو", msg, listOfNotNull(out), MafiaDeath.CAPO, replace = true)
                if (out == null) host.pop()
            }) {
                addView(narration("معتمد شهر یکی از دو تفنگ را برمی‌دارد و به یکی از دو نفر شلیک می‌کند", null))
                addView(kit.text("تفنگ", TextStyle.LABEL_BOLD, Royal.gold))
                addView(kit.grid(2, listOf(1, 2).map { n -> kit.chip("تفنگ شماره : ${kit.n(n)}", shotGun == n, ButtonKind.CHIP_GOLD) { shotGun = n; host.refresh() } }, 8), kit.spaced(10))
                addView(kit.text("هدف", TextStyle.LABEL_BOLD, Royal.gold))
                addView(kit.grid(3, picks.map { p -> playerTile(p, false, !air && shotTarget == p.id) { shotTarget = p.id; air = false; host.refresh() } } +
                    kit.chip("شلیک هوایی", air, ButtonKind.CHIP_GOLD) { air = true; shotTarget = null; host.refresh() }, 6))
            }
        }
    }

    override fun onHide() { timers.forEach { it.stop() } }
}

/** «بازپرسی»: the two summoned players defend, then the interrogator may force a vote between them. */
class MafiaInterrogationScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private val timers = ArrayList<MafiaTimer>()
    private var stage = 0
    private val votes = HashMap<Int, Int>()

    override fun build(): View {
        if (!load()) return missing()
        timers.forEach { it.stop() }
        timers.clear()
        val two = MafiaDay.interrogated(g).filter { it.alive }
        val blocked = MafiaDay.blocked(g, MafiaRole.INTERROGATOR)
        return when (stage) {
            0 -> scaffold("بازپرسی", "دفاع دو بازیکن", bottom = kit.button("پایان دفاع", ButtonKind.PRIMARY, RoyalIcon.NEXT, 52) { stage = 1; host.refresh() }) {
                if (blocked != null) addView(narration("$blocked\nادامه روند بازپرسی انجام خواهد شد", null, PanelStyle.DANGER))
                addView(narration("بازیکنان به ترتیب برای بازپرسی دفاع کنند", MafiaCues.DEFENCE))
                two.forEach { p -> addView(timerRow(p, mafiaSettings.defenceTime, null, null, timers)) }
            }
            1 -> scaffold("بازپرسی", "تصمیم بازپرس", bottom = kit.horizontal().apply {
                addView(kit.weight(kit.button("لغو بازپرسی", ButtonKind.SECONDARY, RoyalIcon.CLOSE, 52) {
                    g.log += MafiaLog(g.phaseTitle, "بازپرس ادامه روند بازپرسی را لغو کرد و تصمیم به ادامه بازی گرفت")
                    finish()
                }))
                addView(kit.hgap(8))
                addView(kit.weight(kit.button("رای گیری", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) {
                    g.log += MafiaLog(g.phaseTitle, "بازپرس تصمیم به رای گیری برای ادامه بازپرسی گرفت")
                    stage = 2; host.refresh()
                }))
            }) {
                addView(narration("آیا ${MafiaRole.INTERROGATOR.faName} رای به ادامه رای گیری خواهد داد؟", null))
            }
            else -> scaffold("بازپرسی", "رای گیری اجباری", bottom = kit.button("پایان", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) { vote(two) }) {
                addView(narration("همهٔ بازیکنان باید به یکی از این دو نفر رای بدهند", MafiaCues.VOTE_FIRST))
                two.forEach { p -> addView(kit.stepperRow(p.name, null, votes[p.id] ?: 0, 0, g.alive.size, 1) { v -> votes[p.id] = v }) }
            }
        }
    }

    private fun vote(two: List<MafiaPlayer>) {
        if (two.size < 2) { finish(); return }
        val a = votes[two[0].id] ?: 0
        val b = votes[two[1].id] ?: 0
        if (a == b) {
            g.log += MafiaLog(g.phaseTitle, "تعداد آرا برای بازیکنان به تساوی کشید و کسی از بازی بیرون نخواهد رفت")
            g.day.done += "interrogation"
            store()
            kit.dialog("بازپرسی", "تعداد آرا برای بازیکنان به تساوی کشید و کسی از بازی بیرون نخواهد رفت", null, listOf(DialogAction("تایید") { host.pop() })).show()
            return
        }
        val loser = if (a > b) two[0] else two[1]
        g.day.done += "interrogation"
        resultThenExit("بازپرسی", "${loser.name} با بیشترین رای از بازی خارج می‌شود\nساید او: ${loser.side?.title ?: ""}", listOf(loser.id), MafiaDeath.INTERROGATION, replace = true)
    }

    private fun finish() {
        g.day.done += "interrogation"
        store()
        host.pop()
    }

    override fun onHide() { timers.forEach { it.stop() } }
}

/** «خنثی سازی بمب»: the protector may defuse; otherwise the bomb holder guesses the code. */
class MafiaBombScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private var protectorTries: Boolean? = null

    override fun build(): View {
        if (!load()) return missing()
        val holder = MafiaDay.bombHolder(g) ?: return scaffold("خنثی سازی بمب") { addView(kit.text("بمبی در بازی نیست", TextStyle.BODY, Royal.muted, Gravity.CENTER)) }
        val code = g.extra["bombCode"]?.toIntOrNull() ?: 1
        val protector = g.aliveHolder(MafiaRole.PROTECTOR)
        if (MafiaDay.bombOnProtector(g)) return scaffold("خنثی سازی بمب", holder.name, bottom = kit.button("تایید", ButtonKind.PRIMARY, RoyalIcon.CHECK, 52) {
            MafiaDay.bombDone(g, "بمب جلوی محافظ بود و خنثی شد"); store(); host.pop()
        }) {
            addView(narration("بمب گذار بمب را در جلو محافظ قرار داده است\nرمز خنثی سازی به او گفته خواهد شد", null, PanelStyle.SUCCESS))
        }
        val askProtector = protector != null && MafiaDay.protectorCan(g) && protectorTries == null
        return scaffold("خنثی سازی بمب", "بمب جلوی ${holder.name}") {
            addView(kit.horizontal(Gravity.CENTER).apply { addView(playerTile(holder, false, false, "بمب"), LinearLayout.LayoutParams(kit.dp(130), ViewGroup.LayoutParams.WRAP_CONTENT)) }, kit.spaced(8))
            addView(kit.badge("کد بمب (فقط برای راوی): ${kit.n(code)}", Royal.crimsonLight).apply { (layoutParams as? LinearLayout.LayoutParams)?.gravity = Gravity.CENTER_HORIZONTAL }, kit.spaced(8))
            if (askProtector) {
                addView(narration("آیا محافظ میخواهد بمب را خنثی کند؟", null))
                addView(kit.grid(2, listOf(
                    kit.button("بله", ButtonKind.PRIMARY, RoyalIcon.CHECK, 50) { protectorTries = true; host.refresh() },
                    kit.button("خیر", ButtonKind.SECONDARY, RoyalIcon.CLOSE, 50) { protectorTries = false; g.log += MafiaLog(g.phaseTitle, "محافظ تمایل به خنثی سازی نداشت"); host.refresh() }
                ), 8))
                return@scaffold
            }
            val byProtector = protectorTries == true && protector != null
            addView(narration(if (byProtector) "رمز حنثی سازی بمب را اعلام کنید" else "بازیکن باید رمز خنثی سازی بمب را اعلام کند", null))
            addView(kit.grid(4, (1..4).map { n ->
                kit.button(kit.n(n), ButtonKind.PRIMARY, null, 60) {
                    if (n == code) {
                        MafiaDay.bombDone(g, "بمب با رمز ${PersianText.digits(n)} خنثی شد")
                        store()
                        kit.dialog("خنثی سازی بمب", "رمز درست بود و بمب خنثی شد", null, listOf(DialogAction("تایید") { host.pop() })).show()
                    } else {
                        val victim = if (byProtector) protector!! else holder
                        MafiaDay.bombDone(g, "رمز ${PersianText.digits(n)} اشتباه بود و بمب منفجر شد")
                        exitNow(listOf(victim.id), MafiaDeath.BOMB)
                        kit.dialog("انفجار بمب", "رمز اشتباه بود\n${victim.name} بدون هیچ وصیتی از بازی خارج می‌شود", null, listOf(DialogAction("تایید") { host.pop() })).show()
                    }
                }
            }, 8))
        }
    }
}

/** «خروج با اره»: the saw owner cuts one of the other two after they defend. */
class MafiaSawScreen(host: MainActivity, gameId: Long) : MafiaScreen(host, gameId) {
    private val timers = ArrayList<MafiaTimer>()

    override fun build(): View {
        if (!load()) return missing()
        timers.forEach { it.stop() }
        timers.clear()
        val saw = MafiaDay.saw(g)
        val allIn = saw != null && saw.first.alive && saw.second.all { it.alive }
        return scaffold("خروج با اره", saw?.first?.let { "اره دست ${it.name}" } ?: "") {
            if (saw == null || !allIn) {
                addView(narration("همه انتخاب های جیگساو در بازی نیستند", null, PanelStyle.DANGER))
                addView(kit.button("تایید", ButtonKind.PRIMARY, RoyalIcon.CHECK, 50) { g.day.done += "jigsaw"; store(); host.pop() })
                return@scaffold
            }
            addView(narration("${saw.first.name} پس از صحبت ها باید یکی از دو بازیکن را اره کند", null))
            saw.second.forEach { p -> addView(timerRow(p, mafiaSettings.defenceTime, "دفاع", null, timers)) }
            addView(kit.section("صاحب اره چه کسی را اره می‌کند؟", RoyalIcon.USER))
            addView(kit.grid(2, saw.second.map { p ->
                playerTile(p, false, false) {
                    g.day.done += "jigsaw"
                    g.log += MafiaLog(g.phaseTitle, "${saw.first.name} با اره ${p.name} را از بازی خارج کرد")
                    resultThenExit("خروج با اره", "${p.name} با اره از بازی خارج می‌شود", listOf(p.id), MafiaDeath.SAW, replace = true)
                }
            }, 8))
        }
    }

    override fun onHide() { timers.forEach { it.stop() } }
}
