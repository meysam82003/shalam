package com.meysam.divanemtiaz

import kotlin.random.Random

/** One step of the intro-night queue (source app: IntroduceQueue). */
data class IntroStep(val key: String, val title: String, val text: String, val button: String, val players: List<Int>, val glyph: MafiaGlyph, val cue: String)

/** What a drawn last-move card did. */
data class CardOutcome(val stays: Boolean, val text: String, val alsoExit: List<Int> = emptyList(), val replaceBy: Int? = null)

/**
 * Game life cycle of «راوی مافیا»: setup checks, start, intro night, ending a night, the day's speaking and
 * voting rules, exits with last-move cards, group inquiry, win checks and «بازگشت به گذشته».
 */
object MafiaFlow {

    // ------------------------------------------------------------------ setup

    /** Validation in the source app's order; returns (ok, message). */
    fun validate(players: Int, roles: Map<MafiaRole, Int>, cards: Collection<MafiaLastCard>): Pair<Boolean, String> {
        val total = roles.values.sum()
        val city = roles.filter { it.key.side == MafiaSide.CITY }.values.sum()
        val mafia = roles.filter { it.key.side == MafiaSide.MAFIA }.values.sum()
        val independent = roles.filter { it.key.side == MafiaSide.INDEPENDENT }.values.sum()
        fun has(r: MafiaRole) = (roles[r] ?: 0) > 0
        if (players < 4) return false to "تعداد بازیکنان نباید از ${PersianText.digits(4)} نفر کمتر باشد"
        if (players != total) return false to "تعداد بازیکنان با نقش ها برابر نیست"
        if (city == 0) return false to "در نقش های انتخابی شهروند وجود ندارد"
        if (mafia == 0) return false to "در نقش های انتخابی نقش مافیایی وجود ندارد"
        if (city <= mafia) return false to "نقش شهروندی باید از نقش مافیایی بیشتر باشد"
        return true to "همه چی ردیفه بزن بریم!"
    }

    /** Prepares counters, armour and the shuffled deck, as the source app's startGame / RolePrepare. */
    fun start(g: MafiaGame, settings: MafiaSettings, random: Random = Random.Default) {
        g.players.forEach { p ->
            val r = p.role ?: return@forEach
            p.counters.clear(); p.flags.clear(); p.marks.clear()
            MafiaRoleOptions.of(r).filter { !it.isSwitch && !it.label.startsWith("تعداد ${r.faName}") }.forEach { o -> p.counters[o.label] = g.optionInt(r, o.label, 0) }
            p.counters["once"] = 1; p.counters["poison"] = 1; p.counters["buy"] = 1; p.counters["trade"] = 1
            // Day action the narrator gave to an original role.
            MafiaRoleRegistry.user[r]?.takeIf { !r.isGeneric && (it.dayAbility ?: MafiaDayAbility.NONE) != MafiaDayAbility.NONE }?.let {
                p.counters[MafiaGenericRoles.DAY_USES] = it.dayUses?.takeIf { u -> u > 0 } ?: 1000
            }
            p.armor = MafiaRoleRegistry.user[r]?.armor?.takeIf { !r.isGeneric } ?: when (r) {
                MafiaRole.DIE_HARD -> 1
                MafiaRole.BODYGUARD -> 1
                else -> g.optionInt(r, "تعداد زره", 0)
            }
            p.alive = true; p.revealed = false; p.outNight = -1; p.deathType = MafiaDeath.NONE
        }
        val shuffled = g.deck.map { it.card }.shuffled(random)
        g.deck.clear(); shuffled.forEach { g.deck += MafiaCardState(it) }
        g.extra["selfKill"] = if (settings.selfKill) "1" else "0"
        g.extra["capo"] = g.extra["scenarioCapo"] ?: if (settings.capoGun) "1" else "0"
        g.night = 0; g.isDay = false; g.started = true; g.introDone = false
        g.records.clear(); g.day = MafiaDayState(); g.log.clear(); g.snapshots.clear()
        g.extra["lineup"] = g.players.joinToString("|") { "${it.id}:${it.role?.name}" }
        // Intro day: everyone talks once, without challenge, before the intro night.
        g.extra["introDay"] = "pending"
        g.extra.remove("starterBase")
        if (g.extra["noNight"] == "1") {
            // Scenario without a night phase: straight to the first day.
            g.night = 1; g.isDay = true; g.introDone = true
            g.lastReport = listOf("این سناریو فاز شب ندارد")
        }
        snapshot(g)
    }

    // ------------------------------------------------------------------ intro night

    fun introQueue(g: MafiaGame): List<IntroStep> {
        val out = ArrayList<IntroStep>()
        val mafia = g.players.filter { it.role?.side == MafiaSide.MAFIA }
        val roles = g.players.mapNotNull { it.role }.distinct()
        val mafiaIntro = g.extra["noMafiaIntro"] != "1"
        if (mafia.size > 1 && mafiaIntro) {
            val thief = g.inGame(MafiaRole.THIEF); val pablo = g.inGame(MafiaRole.PABLO)
            val text = when {
                thief && pablo -> "گروه مافیا به جز ${MafiaRole.THIEF.faName} و ${MafiaRole.PABLO.faName} از خواب بیدار شوند"
                pablo -> "گروه مافیا به جز ${MafiaRole.PABLO.faName} از خواب بیدار شوند"
                thief -> "گروه مافیا به جز ${MafiaRole.THIEF.faName} از خواب بیدار شوند"
                else -> "گروه مافیا از خواب بیدار شوند"
            }
            out += IntroStep("MAFIA_GROUP", "گروه مافیا", text, "بیدار شدند",
                mafia.filter { it.role != MafiaRole.THIEF && it.role != MafiaRole.PABLO }.map { it.id }, MafiaGlyph.MASK, MafiaCues.MAFIA_WAKE)
        }
        roles.filter { it.side == MafiaSide.MAFIA && it != MafiaRole.THIEF && it != MafiaRole.PABLO && it != MafiaRole.CAST_AWAY }.sortedBy { it.introOrder }.forEach { r ->
            out += IntroStep(r.name, r.faName, MafiaRoleText.intro(r), "معرفی شد", g.holders(r).map { it.id }, r.glyph, MafiaCues.wake(r))
        }
        if (mafia.size > 1 && mafiaIntro) out += IntroStep("ROLE_SLEEP", "گروه مافیا", "گروه مافیا بخوابند", "خوابیدند", emptyList(), MafiaGlyph.MOON, MafiaCues.MAFIA_SLEEP)
        roles.filter { it.side != MafiaSide.MAFIA || it == MafiaRole.THIEF || it == MafiaRole.PABLO || it == MafiaRole.CAST_AWAY }.sortedBy { it.introOrder }.forEach { r ->
            out += IntroStep(r.name, r.faName, MafiaRoleText.intro(r), "مشاهده شد", g.holders(r).map { it.id }, r.glyph, MafiaCues.wake(r))
        }
        out += IntroStep("ROLE_FINISH", "پایان معارفه", "شب معارفه به اتمام رسید", "پایان", emptyList(), MafiaGlyph.CROWN, MafiaCues.INTRO_END)
        return out
    }

    // ------------------------------------------------------------------ night end

    /** Null when the night may end, otherwise the source app's refusal. */
    fun nightEndRefusal(g: MafiaGame): String? {
        val missing = MafiaNight.jobs(g).filter { it.key !in g.records }
            .filter { job -> job.key.startsWith("CARD_") || MafiaNight.refusal(g, job) == null }
        return if (missing.isEmpty()) null else "تمام مراحل شب انجام نشده است"
    }

    fun insomnia(g: MafiaGame): Boolean = g.extra["insomnia"] == g.night.toString()

    /** Resolves the night and opens the next day; returns the morning report. */
    fun endNight(g: MafiaGame): MafiaNight.Outcome {
        val n = g.night
        g.players.forEach { it.flags.remove("illusioned") }
        val outcome = if (insomnia(g)) MafiaNight.Outcome(emptyList(), listOf("امشب بی خوابی داریم\nزد و خوردی انجام نمیشود\nمستقیم به روز بعد خواهیم رفت"), emptyList())
            else MafiaNight.resolve(g)
        if (n == 0) g.introDone = true
        if (g.extra["nightLikeDay"] == n.toString()) {
            g.extra["nightTrace"] = g.records.values.joinToString("\n") { r -> traceLine(g, r) }
        }
        g.records.values.filter { it.key != MafiaNight.INTRO }.forEach { r -> g.log += MafiaLog(g.phaseTitle, traceLine(g, r)) }
        outcome.dead.forEach { id -> g.player(id)?.let { g.log += MafiaLog(g.phaseTitle, "${it.name} (${it.role?.faName}) در شب از بازی خارج شد") } }
        g.records.clear()
        g.night = n + 1
        g.isDay = true
        g.day = MafiaDayState()
        g.lastReport = outcome.report
        g.lastDead = outcome.dead
        if (g.extra["dieHardQuery"] == g.night.toString()) g.out.forEach { it.revealed = true }
        snapshot(g)
        return outcome
    }

    private fun traceLine(g: MafiaGame, r: NightRecord): String {
        val who = when {
            r.key == MafiaNight.MAFIA_GROUP -> "گروه مافیا" + if (r.choice.isNotBlank()) " (" + r.choice + ")" else ""
            r.key.startsWith("CARD_") -> MafiaLastCard.values().firstOrNull { MafiaNight.cardKey(it) == r.key }?.faName ?: r.key
            else -> MafiaRole.values().firstOrNull { it.name == r.key }?.faName ?: r.key
        }
        val names = r.targets.mapNotNull { g.player(it)?.name }.joinToString("، ")
        val guess = r.guess?.let { " با حدس ${it.faName}" } ?: ""
        val body = if (names.isNotBlank()) "$names را انتخاب کرد$guess" else r.choice.ifBlank { "کاری انجام نداد" }
        return "$who: $body" + if (r.result.isNotBlank()) " — ${r.result.replace("\n", " ")}" else ""
    }

    /** Opens the next night (after the day's end checks). */
    fun startNight(g: MafiaGame) {
        g.isDay = false
        g.records.clear()
        g.players.forEach {
            it.flags.removeAll(setOf("redCarpet", "greenMile", "lambs", "discipline", "sabotaged"))
            it.marks.remove("gun")
        }
        // Day-only state of yesterday's night roles.
        listOf("rouletteOn", "rouletteNumber", "rouletteCount", "voodooWord", "voodooOn", "jigsaw", "interrogate", "bombOn", "bombCode",
            "capoTrustee", "capoGun", "capoPicks", "capoStage").forEach { g.extra.remove(it) }
        snapshot(g)
        if (g.extra["noNight"] == "1") {
            // The scenario has no night: the next day starts at once.
            endNight(g)
            g.lastReport = listOf("این سناریو فاز شب ندارد")
        }
    }

    // ------------------------------------------------------------------ day

    fun dayEndRefusal(g: MafiaGame): String? = when {
        shakeHandDue(g) -> if ("shake" in g.day.done) null else "تمام مراحل روز انجام نشده است"
        MafiaDay.capoDay(g) -> if ("capo" in g.day.done) null else "تمام مراحل روز انجام نشده است"
        g.night > 0 && !g.day.resultDone -> "تمام مراحل روز انجام نشده است"
        !g.day.speakingDone -> "تمام مراحل روز انجام نشده است"
        else -> null
    }

    fun shakeHandDue(g: MafiaGame): Boolean = g.alive.size <= 3 &&
        g.deck.none { (it.card == MafiaLastCard.FACE_OFF || it.card == MafiaLastCard.CARD_EXCHANGE) && it.pending }

    fun silenced(p: MafiaPlayer): String? = when {
        p.has("silenced") -> "بازیکن توسط ${MafiaRole.PSYCHOLOGIST.faName} ساکت شده است"
        p.has("natasha") -> "بازیکن توسط ${MafiaRole.NATASHA.faName} ساکت شده است"
        p.has("lambs") -> "بازیکن در سکوت بره ها ساکت شده است"
        p.has("discipline") -> "به دلیل انضباطی ساکت شده است"
        else -> null
    }

    fun speakTime(p: MafiaPlayer, s: MafiaSettings): Int = if (p.has("extraTime")) s.speakTime * 2 else s.speakTime

    fun firstEdge(n: Int, s: MafiaSettings): Int = if (s.voteEdgeUp) n / 2 + 1 else n / 2
    fun secondEdge(n: Int, s: MafiaSettings): Int = if (s.voteEdgeUp) (n - 1) / 2 + 1 else (n - 1) / 2

    /** The scenario may fix the vote rounding («edge» = up/down) or a defence threshold per number of players. */
    private fun scenarioSettings(g: MafiaGame, s: MafiaSettings): MafiaSettings = when (g.extra["edge"]) {
        "up" -> s.copy(voteEdgeUp = true)
        "down" -> s.copy(voteEdgeUp = false)
        else -> s
    }

    fun firstEdge(g: MafiaGame, s: MafiaSettings): Int {
        val n = g.alive.size
        thresholds(g)[n]?.let { return it }
        return firstEdge(n, scenarioSettings(g, s))
    }

    fun secondEdge(g: MafiaGame, s: MafiaSettings): Int = secondEdge(g.alive.size, scenarioSettings(g, s))

    fun thresholds(g: MafiaGame): Map<Int, Int> = g.extra["thresholds"]?.split(",")?.mapNotNull { e ->
        val p = e.split(":"); if (p.size == 2) p[0].toIntOrNull()?.let { a -> p[1].toIntOrNull()?.let { a to it } } else null
    }?.toMap() ?: emptyMap()

    fun queryCount(g: MafiaGame, s: MafiaSettings): Int = g.extra["queryCount"]?.toIntOrNull() ?: s.queryCount

    /** Players who may receive votes (green mile excluded). */
    fun votable(g: MafiaGame): List<MafiaPlayer> = g.alive.filter { !it.has("greenMile") }

    fun finishFirstVote(g: MafiaGame, s: MafiaSettings) {
        val edge = firstEdge(g, s)
        g.day.defenders.clear()
        g.alive.filter { !it.has("greenMile") && ((g.day.firstVotes[it.id] ?: 0) >= edge || it.has("redCarpet")) }.forEach { g.day.defenders += it.id }
        g.day.firstVoteDone = true
        g.alive.forEach { p -> (g.day.firstVotes[p.id] ?: 0).takeIf { it > 0 }?.let { g.log += MafiaLog(g.phaseTitle, "${p.name}: ${PersianText.digits(it)} رای، در رای گیری اول کسب کرد") } }
    }

    fun defenceAvailable(g: MafiaGame): Boolean = g.day.firstVoteDone && g.day.defenders.isNotEmpty() && g.alive.size > 3

    fun mayorAvailable(g: MafiaGame): Boolean {
        val mayor = g.aliveHolder(MafiaRole.MAYOR) ?: return false
        return g.day.secondVoteDone && g.day.defenders.size >= 2 && mayor.counter("تعداد مداخله در رای گیری") > 0
    }

    fun mayorRefusal(g: MafiaGame): String? {
        val mayor = g.aliveHolder(MafiaRole.MAYOR) ?: return null
        if (mayor.id in g.day.defenders) return "${MafiaRole.MAYOR.faName} در دفاع است و نمیتواند کاری انجام دهد"
        if (mayor.has("handcuff")) return "نقش ${MafiaRole.MAYOR.faName} دستبند خورده است"
        return null
    }

    /** Mayor decision: cancel (null target) or direct exit of one defender. */
    fun mayor(g: MafiaGame, exit: Int?) {
        val mayor = g.aliveHolder(MafiaRole.MAYOR) ?: return
        mayor.counters["تعداد مداخله در رای گیری"] = mayor.counter("تعداد مداخله در رای گیری").let { if (it >= 1000) it else it - 1 }
        if (exit == null) {
            g.day.secondVotes.keys.toList().forEach { g.day.secondVotes[it] = 0 }
            g.day.mayorText = "شهردار رای گیری را ملغی کرد"
        } else {
            g.day.secondVotes[exit] = 100
            g.day.mayorText = "شهردار رای خروج مستقیم داد به ${g.player(exit)?.name}"
        }
        g.day.mayorDone = true
        g.log += MafiaLog(g.phaseTitle, g.day.mayorText)
    }

    /**
     * Who leaves by vote: (players, tie). A tie between several defenders needs «قرعه مرگ».
     */
    fun voteOutcome(g: MafiaGame, s: MafiaSettings): Pair<List<Int>, Boolean> {
        val d = g.day
        if (d.defenders.isEmpty()) return emptyList<Int>() to false
        val n = g.alive.size
        if (d.defenders.size == 1) {
            val id = d.defenders[0]
            val v = d.secondVotes[id] ?: 0
            return (if (v >= secondEdge(g, s) || v >= 100) listOf(id) else emptyList()) to false
        }
        val max = d.defenders.maxOf { d.secondVotes[it] ?: 0 }
        if (max < 1) return emptyList<Int>() to false
        val top = d.defenders.filter { (d.secondVotes[it] ?: 0) == max }
        return top to (top.size > 1)
    }

    fun voteResultText(g: MafiaGame, exiting: List<Int>): String {
        val lines = ArrayList<String>()
        if (g.day.defenders.size > 1) {
            g.day.defenders.forEach { id -> lines += "${g.player(id)?.name}: ${PersianText.digits(g.day.secondVotes[id] ?: 0)} رای" }
            if (g.inGame(MafiaRole.MAYOR)) lines += g.day.mayorText.ifBlank { if (g.aliveHolder(MafiaRole.MAYOR)?.counter("تعداد مداخله در رای گیری") == 0) "شهردار از قابلیت خود استفاده کرده است" else "شهردار کاری انجام نداد" }
        }
        lines += when (exiting.size) {
            0 -> "هیچ بازیکنی از بازی خارج نخواهد شد"
            1 -> "بازیکن زیر از بازی خارج خواهد شد"
            else -> "بازیکنان زیر از بازی خارج خواهند شد"
        }
        return lines.joinToString("\n")
    }

    /** «قرعه مرگ»: one random tied player holds the cross. */
    fun lottery(tied: List<Int>, random: Random = Random.Default): Int = tied[random.nextInt(tied.size)]

    // ------------------------------------------------------------------ exits

    fun remainingCards(g: MafiaGame): List<MafiaCardState> = g.deck.filter { !it.picked }

    /**
     * Special exit rules (source DayExit priority). Returns (exits, message): the player may stay,
     * or take others along.
     */
    fun exitCheck(g: MafiaGame, id: Int): Pair<List<Int>, String> {
        val p = g.player(id) ?: return emptyList<Int>() to ""
        if (p.has("smith")) {
            p.flags -= "smith"
            return emptyList<Int>() to "بازیکن اخراجی توسط ${MafiaRole.SMITH.faName} محافظت شده است \nاو از بازی خارج نخواهد شد"
        }
        if (p.role == MafiaRole.JACK_SPARROW && !p.has("jackVoted")) {
            p.flags += "jackVoted"
            return emptyList<Int>() to "${p.name} از بازی خارج نخواهد شد\nاو از این پس قادر نخواهد بود طلسم خود را تغییر دهد"
        }
        val guardianRec = g.extra["guardianProtect"]
        if (guardianRec == id.toString()) {
            p.role = MafiaRole.CITIZEN
            return emptyList<Int>() to "بازیکن اخراجی ${p.name} از بازی خارج نخواهد شد\nاو از این پس با نقش ${MafiaRole.CITIZEN.faName} ایفای نقش خواهد کرد"
        }
        val out = arrayListOf(id)
        val lines = ArrayList<String>()
        g.extra["talisman"]?.toIntOrNull()?.takeIf { it == id }?.let {
            g.aliveHolder(MafiaRole.JACK_SPARROW)?.let { jack -> out += jack.id; lines += "بازیکن اخراجی طلسم ${MafiaRole.JACK_SPARROW.faName} است\nاو به همراه خود ${jack.name} را از بازی خارج خواهد کرد" }
        }
        if (p.role == MafiaRole.RESEARCHER) g.extra["researcherLink"]?.toIntOrNull()?.let { link ->
            g.player(link)?.takeIf { it.alive }?.let { m -> out += m.id; lines += "${MafiaRole.RESEARCHER.faName} در تحقیقات خود درست عمل کرده است\n ${m.name} را به همراه خود بیرون میبرد" }
        }
        if (p.role == MafiaRole.SUICIDE_BOMBER) {
            neighbours(g, id).forEach { out += it }
            lines += "بازیکن اخراجی ${MafiaRole.SUICIDE_BOMBER.faName} است و بازیکنان کناری خود را از بازی بیرون خواهد برد"
        }
        if (p.role == MafiaRole.TERRORIST) lines += "بازیکن اخراجی ${MafiaRole.TERRORIST.faName} است\nآیا ${MafiaRole.TERRORIST.faName} میخواهد کسی را ترور کند؟"
        return out to lines.joinToString("\n")
    }

    /** Alive seat neighbours (players list order is the seating order). */
    fun neighbours(g: MafiaGame, id: Int): List<Int> {
        val alive = g.alive
        val i = alive.indexOfFirst { it.id == id }
        if (i < 0 || alive.size < 3) return emptyList()
        return listOf(alive[(i - 1 + alive.size) % alive.size].id, alive[(i + 1) % alive.size].id).distinct().filter { it != id }
    }

    fun exit(g: MafiaGame, id: Int, type: String) {
        val p = g.player(id) ?: return
        if (!p.alive) return
        p.alive = false
        p.outNight = g.night
        p.deathType = type
        g.day.exited += id
        g.log += MafiaLog(g.phaseTitle, "${p.name} (${p.role?.faName}) — ${MafiaDeath.title(type)}")
    }

    fun returnPlayer(g: MafiaGame, id: Int) {
        val p = g.player(id) ?: return
        p.alive = true; p.deathType = MafiaDeath.NONE; p.outNight = -1
        g.day.exited.remove(id)
        g.log += MafiaLog(g.phaseTitle, "${p.name} به بازی بازگشت")
    }

    /** Draws a face-down card by its position among the remaining ones. */
    fun draw(g: MafiaGame, index: Int, holder: Int): MafiaCardState? {
        val st = remainingCards(g).getOrNull(index) ?: return null
        st.picked = true
        st.holder = holder
        g.log += MafiaLog(g.phaseTitle, "${g.player(holder)?.name} در کارت حرکت آخر «${st.card.faName}» را انتخاب کرد")
        return st
    }

    /** Applies a drawn card. [picks] are the chosen players, [guess] a guessed role, [answer] true for «حقیقت» answered right. */
    fun applyCard(g: MafiaGame, st: MafiaCardState, picks: List<Int>, guess: MafiaRole?, answer: Boolean = false, random: Random = Random.Default): CardOutcome {
        val holder = g.player(st.holder)
        st.targets = picks
        val n = g.night
        val first = picks.firstOrNull()?.let { g.player(it) }
        return when (st.card) {
            MafiaLastCard.INTELLIGENCE -> if (holder != null && silenced(holder) != null) CardOutcome(false, "بازیکن به دلیل سکوت قابلیت استفاده از کارت را ندارد و از بازی بدون هیچ وصیتی خارج میشود")
                else if (first != null && first.role == guess) { first.revealed = true; CardOutcome(true, "بازیکن نقش را درست حدس زد و در بازی میماند") }
                else CardOutcome(false, "بازیکن نقش را درست حدس نزد و از بازی خارج خواهد شد")
            MafiaLastCard.NOSTRADAMUS_BEAUTIFUL_MIND -> if (holder?.role == MafiaRole.NOSTRADAMUS) {
                holder.flags += "mortal"
                CardOutcome(true, "بازیکن اخراجی خود ${MafiaRole.NOSTRADAMUS.faName} است و از بازی خارج نخواهد شد\nاز این پس او در شب نامیرا نخواهد بود")
            } else if (first?.role == MafiaRole.NOSTRADAMUS) CardOutcome(true, "بازیکن نقش را درست حدس زد و در بازی خواهد ماند", alsoExit = listOf(first.id))
                else CardOutcome(false, "بازیکن نقش را درست حدس نزد و از بازی خارج خواهد شد")
            MafiaLastCard.JACK_SPARROW_BEAUTIFUL_MIND -> {
                val jack = g.aliveHolder(MafiaRole.JACK_SPARROW)
                val shown = jack?.revealed == true
                val right = if (shown) first?.id?.toString() == g.extra["talisman"] else first?.role == MafiaRole.JACK_SPARROW
                if (right && jack != null) CardOutcome(true, "بازیکن در بازی خواهد ماند و به جای او ${MafiaRole.JACK_SPARROW.faName} از بازی خارج خواهد شد", alsoExit = listOf(jack.id))
                else CardOutcome(false, "حدس اشتباه بود و بازیکن از بازی خارج خواهد شد")
            }
            MafiaLastCard.SHERLOCK_HOLMES_BEAUTIFUL_MIND -> if (first?.role == MafiaRole.SHERLOCK_HOLMES) {
                first.revealed = true
                CardOutcome(true, "بازیکن نقش را درست حدس زد و به جای او ${MafiaRole.SHERLOCK_HOLMES.faName} از بازی بیرون خواهد رفت", alsoExit = listOf(first.id))
            } else CardOutcome(false, "بازیکن نقش را درست حدس نزد و از بازی بیرون خواهد رفت")
            MafiaLastCard.RED_CARPET -> { first?.flags?.add("redCarpet"); CardOutcome(false, "${first?.name ?: ""} در هر شرایطی به دفاعیه وارد می‌شود") }
            MafiaLastCard.GREEN_MILE -> { first?.flags?.add("greenMile"); CardOutcome(false, "${first?.name ?: ""} در مسیر سبز است و در رأی‌گیری قرار نمی‌گیرد") }
            MafiaLastCard.VERTIGO, MafiaLastCard.FINAL_SHOT, MafiaLastCard.CARD_EXCHANGE, MafiaLastCard.FACE_OFF, MafiaLastCard.LAST_VENOM -> {
                st.pending = true
                CardOutcome(false, "عملیات «${st.card.faName}» در شب انجام خواهد شد")
            }
            MafiaLastCard.INSOMNIA -> { g.extra["insomnia"] = n.toString(); CardOutcome(false, "امشب بی خوابی داریم\nزد و خوردی انجام نمیشود\nمستقیم به روز بعد خواهیم رفت") }
            MafiaLastCard.CENSUS -> {
                val alive = g.alive.filter { it.id != st.holder }
                val text = "تعداد مافیا های حاضر در بازی : ${PersianText.digits(alive.count { it.side == MafiaSide.MAFIA })}\n" +
                    "تعداد شهروندان حاضر در بازی : ${PersianText.digits(alive.count { it.side == MafiaSide.CITY })}" +
                    (if (g.players.any { it.side == MafiaSide.INDEPENDENT }) "\nتعداد مستقل های حاضر در بازی : ${PersianText.digits(alive.count { it.side == MafiaSide.INDEPENDENT })}" else "")
                CardOutcome(false, text)
            }
            MafiaLastCard.EXHUMATION -> if (first == null) CardOutcome(false, "هیچ بازیکنی از بازی بیرون نرفته است")
                else { first.revealed = true; CardOutcome(false, "بازیکن انتخابی نقش زیر را دارد\n${first.name}: ${first.role?.faName}") }
            MafiaLastCard.GOD_FATHER_PRESENT -> CardOutcome(false, if (g.aliveHolder(MafiaRole.GODFATHER) != null) "${MafiaRole.GODFATHER.faName} در بازی حضور دارد" else "${MafiaRole.GODFATHER.faName} در بازی حضور ندارد")
            MafiaLastCard.SECOND_CHANCE -> CardOutcome(true, "از بازی خارج نمی شوی و در بازی می مانی")
            MafiaLastCard.THIRTEEN_LIE -> CardOutcome(false, "پس از گفتن دروغ و تأیید گرداننده، بازیکن از بازی خارج می‌شود")
            MafiaLastCard.GOODBYE -> CardOutcome(false, "بازیکن بدون هیچ کلامی از بازی خارج می‌شود")
            MafiaLastCard.PRIVATE_WILL -> CardOutcome(false, "بازیکن به صورت خصوصی به یک نفر وصیت می‌کند و از بازی خارج می‌شود")
            MafiaLastCard.DARE_OR_TRUTH -> if (first == null) CardOutcome(false, "")
                else if (answer) CardOutcome(false, "${first.name} حقیقت را انتخاب کرد و درست جواب داد؛ در بازی می‌ماند")
                else CardOutcome(false, "${first.name} نیز از بازی خارج خواهد شد", alsoExit = listOf(first.id))
            MafiaLastCard.SILENCE_OF_THE_LAMBS -> {
                val list = if (g.alive.size <= g.players.size / 2) picks.take(1) else picks
                list.forEach { g.player(it)?.flags?.add("lambs") }
                CardOutcome(false, list.mapNotNull { g.player(it)?.name }.joinToString("، ") + " فردا ساکت خواهند بود (حتی در دفاعیه)")
            }
            MafiaLastCard.SHOW_IDENTITY -> { holder?.revealed = true; holder?.flags?.add("noReturn"); CardOutcome(false, "بازیکن اخراجی نقش زیر را دارد\n${holder?.role?.faName ?: ""}") }
            MafiaLastCard.HANDCUFF -> { first?.flags?.add("handcuff"); CardOutcome(false, "${first?.name ?: ""} امشب توانایی خود را ندارد") }
            MafiaLastCard.LUCK -> if (first == null || holder == null) CardOutcome(false, "") else {
                val loser = if (random.nextBoolean()) holder.id else first.id
                if (loser == first.id) CardOutcome(true, "قرعهٔ مرگ به نام ${first.name} افتاد؛ بازیکن بالا از بازی خارج خواهد شد", alsoExit = listOf(first.id))
                else CardOutcome(false, "قرعهٔ مرگ به نام ${holder.name} افتاد؛ بازیکن بالا از بازی خارج خواهد شد")
            }
            MafiaLastCard.BLACK_NIGHT -> { g.extra["blackNight"] = n.toString(); CardOutcome(false, "امشب شب سیاه است؛ نقش‌های شهروندی نمی‌توانند از قابلیت خود استفاده کنند") }
            MafiaLastCard.NIGHT_LIKE_DAY -> { g.extra["nightLikeDay"] = n.toString(); CardOutcome(false, "تمامی اتفاقات امشب فردا برای همه علنی خواهد شد") }
            MafiaLastCard.BAD_CHANCE -> {
                val others = g.alive.filter { it.id != st.holder }
                if (others.isEmpty()) CardOutcome(false, "") else {
                    val victim = others[random.nextInt(others.size)]
                    CardOutcome(false, "نقش زیر باید از بازی بیرون برود\n${victim.name}", alsoExit = listOf(victim.id))
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers of the day

    fun groupQueryAvailable(g: MafiaGame, s: MafiaSettings): Boolean = g.night >= 2 && g.queriesUsed < queryCount(g, s) && !g.inGame(MafiaRole.DIE_HARD)

    fun groupQuery(g: MafiaGame): String {
        g.queriesUsed++
        val out = g.out
        val text = "تعداد مافیا های خارج از شهر : ${PersianText.digits(out.count { it.side == MafiaSide.MAFIA })}\n" +
            "تعداد شهروندان خارج از شهر : ${PersianText.digits(out.count { it.side == MafiaSide.CITY })}" +
            (if (g.players.any { it.role?.side == MafiaSide.INDEPENDENT }) "\nتعداد مستقل های خارج از شهر : ${PersianText.digits(out.count { it.side == MafiaSide.INDEPENDENT })}" else "")
        g.log += MafiaLog(g.phaseTitle, "استعلام گروهی: " + text.replace("\n", " | "))
        return text
    }

    fun shakeHand(g: MafiaGame, citizen: Int, chosen: Int) {
        g.log += MafiaLog(g.phaseTitle, "تعداد بازیکنان به ${PersianText.digits(3)} نفر رسید و کار به دست دادن کشید")
        g.alive.filter { it.id != citizen && it.id != chosen }.forEach { exit(g, it.id, MafiaDeath.SHAKE_HAND) }
        g.extra["shakeChooser"] = citizen.toString()
        g.day.done += "shake"
    }

    // ------------------------------------------------------------------ win

    /** Source Checker.isWin: returns the win message, or null. Also marks the game finished. */
    fun checkWin(g: MafiaGame): String? {
        if (g.finished) return g.winner
        if (g.extra["noAutoWin"] == g.night.toString() + (if (g.isDay) "d" else "n")) return null
        if (g.deck.any { (it.card == MafiaLastCard.FACE_OFF || it.card == MafiaLastCard.CARD_EXCHANGE || it.card == MafiaLastCard.FINAL_SHOT) && it.pending }) return null
        val alive = g.alive
        var city = alive.count { it.side == MafiaSide.CITY }
        var mafia = alive.count { it.side == MafiaSide.MAFIA }
        val nostra = g.aliveHolder(MafiaRole.NOSTRADAMUS)
        when (g.extra["nostradamusSide"]) { "مافیا" -> if (nostra != null) mafia++; "شهر" -> if (nostra != null) city++ }
        var msg: String? = when {
            mafia == 0 -> "شهروندها برنده بازی شدند"
            mafia >= city -> "گروه مافیا برنده بازی شدند"
            else -> null
        }
        if (msg != null && nostra != null) {
            val side = g.extra["nostradamusSide"]
            if (msg.startsWith("شهروند") && side == "شهر") msg = "گروه شهروند ها به همراه ${MafiaRole.NOSTRADAMUS.faName} پیروز شدند"
            if (msg.startsWith("گروه مافیا") && side == "مافیا") msg = "گروه مافیا به همراه ${MafiaRole.NOSTRADAMUS.faName} پیروز شدند"
        }
        fun ind(r: MafiaRole) = "${r.faName} برنده بازی شد"
        val joker = g.holders(MafiaRole.JOKER).firstOrNull()
        if (joker != null && !joker.alive && joker.deathType == MafiaDeath.VOTE) msg = ind(MafiaRole.JOKER)
        if (g.aliveHolder(MafiaRole.FELON) != null && alive.size <= 3) msg = ind(MafiaRole.FELON)
        g.aliveHolder(MafiaRole.CORONA)?.let { c -> if (alive.filter { it.id != c.id }.all { it.marks.containsKey("infected") }) msg = ind(MafiaRole.CORONA) }
        if (g.aliveHolder(MafiaRole.CHURCHILL) != null && ((alive.size == 3 && city == 1) || (alive.size == 2 && city == 1))) msg = ind(MafiaRole.CHURCHILL)
        if (g.aliveHolder(MafiaRole.KILLER) != null && g.players.count { it.side == MafiaSide.INDEPENDENT } == 1 &&
            listOf(MafiaRole.GODFATHER, MafiaRole.DOCTOR, MafiaRole.SPECIAL_DETECTIVE).all { g.aliveHolder(it) == null }) msg = ind(MafiaRole.KILLER)
        g.aliveHolder(MafiaRole.SHERLOCK_HOLMES)?.let { s ->
            if (alive.size == 2 && mafia > 0 && g.extra["shakeChooser"] == s.id.toString()) msg = ind(MafiaRole.SHERLOCK_HOLMES)
        }
        if (g.aliveHolder(MafiaRole.JIGSAW) != null) msg = if (mafia == 0 && city == 2) ind(MafiaRole.JIGSAW) else msg?.takeIf { !it.startsWith("شهروند") && !it.startsWith("گروه") }
        if (g.aliveHolder(MafiaRole.ZODIAC) != null) msg = if (alive.size <= 2) ind(MafiaRole.ZODIAC) else msg?.takeIf { !it.startsWith("شهروند") && !it.startsWith("گروه") }
        if (g.aliveHolder(MafiaRole.JACK_SPARROW) != null) msg = if (alive.size <= 3 || mafia == 0) ind(MafiaRole.JACK_SPARROW) else msg?.takeIf { !it.startsWith("شهروند") && !it.startsWith("گروه") }
        val result = msg ?: return null
        g.finished = true
        g.winner = result
        g.extra["winSide"] = winSideOf(result)
        g.log += MafiaLog(g.phaseTitle, "********پایان بازی*******\n$result")
        return result
    }

    /** CITY, MAFIA, CITY+NOSTRADAMUS, MAFIA+NOSTRADAMUS or the winning independent role. */
    fun winSideOf(msg: String): String {
        val nostra = msg.contains(MafiaRole.NOSTRADAMUS.faName) && msg.contains("به همراه")
        return when {
            msg.startsWith("شهروند") || msg.startsWith("گروه شهروند") -> if (nostra) "CITY+NOSTRADAMUS" else "CITY"
            msg.startsWith("گروه مافیا") -> if (nostra) "MAFIA+NOSTRADAMUS" else "MAFIA"
            else -> MafiaRole.values().firstOrNull { msg.startsWith(it.faName + " برنده") }?.name ?: ""
        }
    }

    fun winCue(msg: String): String = when {
        msg.startsWith("شهروند") || msg.startsWith("گروه شهروند") -> MafiaCues.WIN_CITY
        msg.startsWith("گروه مافیا") -> MafiaCues.WIN_MAFIA
        else -> MafiaCues.WIN_INDEPENDENT
    }

    // ------------------------------------------------------------------ past

    fun snapshot(g: MafiaGame) {
        val label = g.phaseTitle
        g.snapshots.removeAll { it.first == label }
        g.snapshots += label to MafiaCodec.encodeGame(g, withSnapshots = false).toString()
    }

    /** «بازگشت به گذشته»: restores a snapshot and forgets the later ones. */
    fun restore(g: MafiaGame, index: Int): MafiaGame {
        val (_, json) = g.snapshots[index]
        val restored = MafiaCodec.decodeGame(org.json.JSONObject(json))
        restored.snapshots.clear()
        g.snapshots.take(index + 1).forEach { restored.snapshots += it }
        return restored
    }

    /** Initial line-up for the game report («تیم شهروندی (n)»…). */
    fun lineup(g: MafiaGame): List<Pair<MafiaPlayer, MafiaRole?>> {
        val m = g.extra["lineup"]?.split("|")?.mapNotNull { e ->
            val i = e.indexOf(':'); if (i < 0) null else e.substring(0, i).toIntOrNull()?.let { it to MafiaRole.values().firstOrNull { r -> r.name == e.substring(i + 1) } }
        }?.toMap() ?: emptyMap()
        return g.players.map { it to (m[it.id] ?: it.role) }
    }
}
