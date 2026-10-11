package com.meysam.divanemtiaz

/**
 * The narrator's hand controls: seating order, substitutes, manual exits and returns, role changes,
 * forced phase changes and declaring (or withdrawing) the result. Everything is logged in the game report.
 */
object MafiaManual {
    private fun log(g: MafiaGame, text: String) { g.log += MafiaLog(g.phaseTitle, text) }

    // ------------------------------------------------------------------ seating

    /** Alive players in seat order, starting from the chosen first speaker («سر صحبت»). */
    fun speakingOrder(g: MafiaGame): List<MafiaPlayer> {
        val alive = g.alive
        val start = g.extra["starter"]?.toIntOrNull()?.let { id -> alive.indexOfFirst { it.id == id } } ?: -1
        return if (start <= 0) alive else alive.drop(start) + alive.take(start)
    }

    fun setStarter(g: MafiaGame, id: Int) { g.extra["starter"] = id.toString() }

    /** Moves a player one seat up (−1) or down (+1) in the whole table order. */
    fun move(g: MafiaGame, id: Int, delta: Int) {
        val i = g.players.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in g.players.indices) return
        val p = g.players.removeAt(i)
        g.players.add(j, p)
    }

    fun seatLog(g: MafiaGame) = log(g, "ترتیب نشستن: " + g.players.joinToString("، ") { it.name })

    // ------------------------------------------------------------------ substitutes

    private fun joined(g: MafiaGame, id: Int) = g.extra["joined_$id"]?.toIntOrNull() ?: 0

    /**
     * [leaving] hands his role and its whole state to [incoming] (an out player) or to a new person,
     * who sits in his seat; the game continues. Returns the player now holding the role.
     */
    fun substitute(g: MafiaGame, leaving: Int, incoming: Int?, name: String = "", avatar: Int = 0): MafiaPlayer? {
        val a = g.player(leaving)?.takeIf { it.alive } ?: return null
        val role = a.role ?: return null
        val b = if (incoming != null) g.player(incoming)?.takeIf { !it.alive } ?: return null
            else MafiaPlayer((g.players.maxOfOrNull { it.id } ?: 0) + 1, name, avatar).also { g.players += it }
        // Close the earlier stretches for the statistics.
        if (incoming != null) b.role?.let { g.stints += MafiaStint(b.name, it, b.side ?: it.side, joined(g, b.id), b.outNight, b.deathType) }
        g.stints += MafiaStint(a.name, role, a.side ?: role.side, joined(g, a.id), g.night, MafiaDeath.LEFT)
        // The role with all its state moves to the substitute.
        b.role = role
        b.formerRole = null
        b.counters.clear(); b.counters.putAll(a.counters)
        b.flags.clear(); b.flags.addAll(a.flags)
        b.marks.clear(); b.marks.putAll(a.marks)
        b.armor = a.armor
        b.revealed = a.revealed
        b.seen = true
        b.alive = true
        b.deathType = MafiaDeath.NONE
        b.outNight = -1
        g.extra["joined_${b.id}"] = g.night.toString()
        a.formerRole = role
        a.role = null
        a.alive = false
        a.deathType = MafiaDeath.LEFT
        a.outNight = g.night
        a.counters.clear(); a.flags.clear(); a.marks.clear(); a.armor = 0
        // Same seat.
        g.players.remove(b)
        g.players.add(g.players.indexOf(a), b)
        remap(g, a.id, b.id)
        log(g, "${a.name} بازی را ترک کرد و ${b.name} با نقش او (${role.faName}) به جای او نشست")
        return b
    }

    /** Every reference to [from] (votes, targets, talisman, bomb, guns…) now points to [to]. */
    private fun remap(g: MafiaGame, from: Int, to: Int) {
        val f = from.toString()
        g.extra.keys.filter { it != "lineup" && !it.startsWith("joined_") }.forEach { k ->
            val v = g.extra[k] ?: return@forEach
            if (v.split(",").contains(f)) g.extra[k] = v.split(",").joinToString(",") { if (it == f) to.toString() else it }
        }
        val d = g.day
        d.firstVotes.remove(from)?.let { d.firstVotes[to] = it }
        d.secondVotes.remove(from)?.let { d.secondVotes[to] = it }
        d.defenders.replaceAll { if (it == from) to else it }
        g.records.keys.toList().forEach { k ->
            val r = g.records[k] ?: return@forEach
            if (from in r.targets) g.records[k] = r.copy(targets = r.targets.map { if (it == from) to else it })
        }
        g.deck.forEach { st ->
            if (st.holder == from && st.pending) st.holder = to
            if (from in st.targets) st.targets = st.targets.map { if (it == from) to else it }
        }
    }

    // ------------------------------------------------------------------ players

    fun exit(g: MafiaGame, id: Int, type: String) {
        MafiaFlow.exit(g, id, type)
        log(g, "راوی ${g.player(id)?.name} را با «${MafiaDeath.title(type)}» از بازی خارج کرد")
    }

    fun revive(g: MafiaGame, id: Int) {
        val p = g.player(id) ?: return
        if (p.role == null && p.formerRole != null) return
        p.flags -= "noReturn"
        MafiaFlow.returnPlayer(g, id)
        log(g, "راوی ${p.name} را به بازی برگرداند")
    }

    /** Gives a player another role with fresh abilities from that role's options. */
    fun setRole(g: MafiaGame, id: Int, role: MafiaRole) {
        val p = g.player(id) ?: return
        val old = p.role
        p.role = role
        p.formerRole = null
        p.flags.remove("converted")
        p.counters.clear()
        MafiaRoleOptions.of(role).filter { !it.isSwitch && !it.label.startsWith("تعداد ${role.faName}") }.forEach { o -> p.counters[o.label] = g.optionInt(role, o.label, 0) }
        p.counters["once"] = 1; p.counters["poison"] = 1; p.counters["buy"] = 1; p.counters["trade"] = 1
        p.armor = when (role) { MafiaRole.DIE_HARD, MafiaRole.BODYGUARD -> 1; else -> g.optionInt(role, "تعداد زره", 0) }
        if (!g.roleCounts.containsKey(role)) g.roleCounts[role] = 1
        log(g, "راوی نقش ${p.name} را از ${old?.faName ?: "-"} به ${role.faName} تغییر داد")
    }

    fun toggleSilence(g: MafiaGame, id: Int) {
        val p = g.player(id) ?: return
        val on = !(p.has("discipline") || p.has("silenced") || p.has("natasha") || p.has("lambs"))
        if (on) p.flags += "discipline" else p.flags.removeAll(setOf("discipline", "silenced", "natasha", "lambs"))
        log(g, if (on) "راوی ${p.name} را ساکت کرد" else "راوی سکوت ${p.name} را برداشت")
    }

    /** Narrator's side change (e.g. a customer who joins a team in ارتش سری); null = back to the role's own side. */
    fun setSide(g: MafiaGame, id: Int, side: MafiaSide?) {
        val p = g.player(id) ?: return
        p.flags.removeAll { it.startsWith("side:") }
        if (side != null) p.flags += "side:" + side.name
        log(g, "راوی ساید ${p.name} را به ${(side ?: p.role?.side)?.title ?: "-"} تغییر داد")
    }

    fun toggleReveal(g: MafiaGame, id: Int) {
        val p = g.player(id) ?: return
        p.revealed = !p.revealed
        log(g, if (p.revealed) "نقش ${p.name} اعلام شد" else "نقش ${p.name} پنهان شد")
    }

    // ------------------------------------------------------------------ phases and result

    fun forceDay(g: MafiaGame): MafiaNight.Outcome {
        log(g, "راوی شب را بدون انجام همهٔ مراحل به پایان رساند")
        return MafiaFlow.endNight(g)
    }

    fun forceNight(g: MafiaGame) {
        log(g, "راوی روز را بدون انجام همهٔ مراحل به پایان رساند")
        MafiaFlow.startNight(g)
    }

    /** Clears today's votes (speaking stays done). */
    fun resetVotes(g: MafiaGame) {
        val spoke = g.day.speakingDone
        val done = g.day.done.filter { it == "interrogation" || it == "jigsaw" || it == "bomb" }
        g.day = MafiaDayState().apply { speakingDone = spoke; this.done.addAll(done) }
        log(g, "راوی رأی‌گیری امروز را از نو شروع کرد")
    }

    /** Marks today's remaining steps done so the night can start. */
    fun skipDay(g: MafiaGame) {
        g.day.speakingDone = true
        g.day.resultDone = true
        g.day.done += listOf("shake", "capo", "interrogation", "jigsaw", "bomb")
        log(g, "راوی مراحل باقی‌ماندهٔ روز را رد کرد")
    }

    /** Winner keys: CITY, MAFIA or an independent role name. */
    fun declare(g: MafiaGame, key: String) {
        val msg = when (key) {
            "CITY" -> "شهروندها برنده بازی شدند"
            "MAFIA" -> "گروه مافیا برنده بازی شدند"
            else -> MafiaRole.values().firstOrNull { it.name == key }?.let { "${it.faName} برنده بازی شد" } ?: return
        }
        g.finished = true
        g.winner = msg
        g.extra["winSide"] = key
        log(g, "راوی بازی را به پایان رساند\n********پایان بازی*******\n$msg")
    }

    /** Takes back the end of the game so play continues. */
    fun resume(g: MafiaGame) {
        g.finished = false
        g.winner = ""
        g.extra.remove("winSide")
        g.extra["noAutoWin"] = g.night.toString() + (if (g.isDay) "d" else "n")
        log(g, "راوی پایان بازی را لغو کرد و بازی ادامه پیدا کرد")
    }
}
