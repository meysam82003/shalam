package com.meysam.divanemtiaz

/**
 * «ارتش سری» (Secret Army): guns held by players and the narrator, private talks (VIP), day shots with
 * vests and Dr Pascal's saves, Reinhart's guesses, the pilot's vote shield, the status announcement and Chaos.
 * State lives in the game's extras, player flags and counters so it is saved with the game.
 */
object SecretArmy {
    const val ID = "secret_army"
    private const val GUNS = "saGuns"
    private const val TALKS = "saTalks"
    private const val SHOTS = "saShots"
    private const val TALKS_TOTAL = "saTalksTotal"
    private const val SHOTS_TOTAL = "saShotsTotal"
    const val SAVES = "saSaves"
    const val GUESSES = "saGuesses"
    const val MAX_PER_DAY = 2

    fun active(g: MafiaGame) = g.extra["scenario"] == ID

    /** First use: 6 guns with players (Curtis, two rescue agents, the three Gestapo) and 1 with the narrator. */
    fun init(g: MafiaGame) {
        if (g.extra.containsKey(GUNS)) return
        g.extra[GUNS] = "1"
        g.players.forEach { p ->
            if (p.role in setOf(MafiaRole.CURTIS, MafiaRole.PARTISAN, MafiaRole.KESSLER, MafiaRole.REINHART, MafiaRole.GESTAPO)) p.flags += "armed"
            if (p.role == MafiaRole.PASCAL) p.counters[SAVES] = 2
            if (p.role == MafiaRole.REINHART) p.counters[GUESSES] = 2
        }
    }

    fun narratorGuns(g: MafiaGame) = g.extra[GUNS]?.toIntOrNull() ?: 0
    private fun setGuns(g: MafiaGame, n: Int) { g.extra[GUNS] = n.toString() }

    private fun today(g: MafiaGame, key: String): Int =
        g.extra[key]?.split(":")?.takeIf { it.size == 2 && it[0] == g.night.toString() }?.get(1)?.toIntOrNull() ?: 0

    private fun bump(g: MafiaGame, key: String, total: String) {
        g.extra[key] = "${g.night}:${today(g, key) + 1}"
        g.extra[total] = ((g.extra[total]?.toIntOrNull() ?: 0) + 1).toString()
    }

    fun talksToday(g: MafiaGame) = today(g, TALKS)
    fun shotsToday(g: MafiaGame) = today(g, SHOTS)
    fun talksTotal(g: MafiaGame) = g.extra[TALKS_TOTAL]?.toIntOrNull() ?: 0
    fun shotsTotal(g: MafiaGame) = g.extra[SHOTS_TOTAL]?.toIntOrNull() ?: 0

    enum class Faction(val title: String) { RESCUE("گروه نجات"), GESTAPO("گشتاپو"), NEUTRAL("بی‌طرف") }

    fun faction(p: MafiaPlayer): Faction = when (p.side) {
        MafiaSide.CITY -> Faction.RESCUE
        MafiaSide.MAFIA -> Faction.GESTAPO
        else -> Faction.NEUTRAL
    }

    fun armed(p: MafiaPlayer) = p.has("armed")

    fun canTalk(g: MafiaGame, p: MafiaPlayer): String? = when {
        !p.alive -> "بازیکن در بازی نیست"
        p.has("talkUsed") -> "${p.name} فرصت درخواست گفتگوی خصوصی خود را مصرف کرده است"
        talksToday(g) >= MAX_PER_DAY -> "سقف ${MAX_PER_DAY} گفتگوی خصوصی امروز پر شده است"
        else -> null
    }

    /** What the invited player must answer about his side (Kessler and the pilot may lie). */
    fun mustTellSide(p: MafiaPlayer) = p.role != MafiaRole.KESSLER && p.role != MafiaRole.PILOT

    /** Records a private talk; only the requester's one-time chance is used. */
    fun talk(g: MafiaGame, requester: MafiaPlayer, invitee: MafiaPlayer) {
        requester.flags += "talkUsed"
        bump(g, TALKS, TALKS_TOTAL)
        g.log += MafiaLog(g.phaseTitle, "گفتگوی خصوصی: ${requester.name} با ${invitee.name}")
    }

    /** The narrator's three answers to a gun request. */
    enum class GunAnswer(val text: String) {
        APPROVED("تأیید می‌شود، مبادله می‌شود."),
        NO_GUN("اسلحه‌ای در دست گرداننده نیست."),
        NOT_ALLOWED("امکان مبادله وجود ندارد.")
    }

    private val rescueGivers = setOf(MafiaRole.CURTIS, MafiaRole.PARTISAN)
    private val gestapoGivers = setOf(MafiaRole.KESSLER, MafiaRole.REINHART, MafiaRole.GESTAPO)

    /**
     * Arming through the narrator's gun: rescue (Curtis, agents) may arm a customer or the pilot, the Gestapo only a
     * customer; Pascal never. Kessler «armed» by the rescue is answered «approved» but nothing moves (he has a gun).
     */
    fun requestGun(g: MafiaGame, requester: MafiaPlayer, target: MafiaPlayer): GunAnswer {
        val r = requester.role
        val t = target.role
        val customer = t == MafiaRole.CUSTOMER && faction(target) == Faction.NEUTRAL
        val allowed = when {
            t == MafiaRole.PASCAL || !target.alive -> false
            r in rescueGivers && t == MafiaRole.KESSLER -> true
            armed(target) -> false
            r in rescueGivers -> customer || t == MafiaRole.PILOT
            r in gestapoGivers -> customer
            else -> false
        }
        if (!allowed) return GunAnswer.NOT_ALLOWED
        if (narratorGuns(g) <= 0) return GunAnswer.NO_GUN
        if (t == MafiaRole.KESSLER) {
            g.log += MafiaLog(g.phaseTitle, "${requester.name} برای مسلح کردن ${target.name} (کسلر) درخواست داد؛ تأیید ظاهری بدون جابه‌جایی اسلحه")
            return GunAnswer.APPROVED
        }
        setGuns(g, narratorGuns(g) - 1)
        target.flags += "armed"
        if (t == MafiaRole.CUSTOMER) MafiaManual.setSide(g, target.id, if (faction(requester) == Faction.GESTAPO) MafiaSide.MAFIA else MafiaSide.CITY)
        g.log += MafiaLog(g.phaseTitle, "${target.name} از طرف ${requester.name} مسلح شد")
        return GunAnswer.APPROVED
    }

    fun canShoot(g: MafiaGame, p: MafiaPlayer): String? = when {
        !p.alive -> "بازیکن در بازی نیست"
        p.has("fired") -> "${p.name} شلیک خود را در این بازی انجام داده است"
        !armed(p) -> "${p.name} اسلحه ندارد"
        shotsToday(g) >= MAX_PER_DAY -> "سقف ${MAX_PER_DAY} شلیک امروز پر شده است"
        else -> null
    }

    enum class ShotState { VEST, SAVABLE, DIES }

    /** Fires the shooter's one shot; the vest of the pilot / Kessler takes the first hit. */
    fun shoot(g: MafiaGame, shooter: MafiaPlayer, target: MafiaPlayer): ShotState {
        shooter.flags += "fired"
        shooter.flags -= "armed"
        bump(g, SHOTS, SHOTS_TOTAL)
        target.counters["saHits"] = target.counter("saHits") + 1
        g.log += MafiaLog(g.phaseTitle, "${shooter.name} به ${target.name} شلیک کرد")
        val vested = target.role == MafiaRole.PILOT || target.role == MafiaRole.KESSLER
        if (vested && !target.has("shotShield")) {
            target.flags += "shotShield"
            g.log += MafiaLog(g.phaseTitle, "جلیقهٔ ${target.name} شلیک اول را گرفت")
            return ShotState.VEST
        }
        return if (savable(g, target)) ShotState.SAVABLE else ShotState.DIES
    }

    fun pascal(g: MafiaGame) = g.aliveHolder(MafiaRole.PASCAL)

    /** Pascal can save: saves left, not the pilot or Kessler, not a second shot on the same player. */
    fun savable(g: MafiaGame, target: MafiaPlayer): Boolean {
        val doc = pascal(g) ?: return false
        if (doc.counter(SAVES) <= 0) return false
        if (target.role == MafiaRole.PILOT || target.role == MafiaRole.KESSLER) return false
        return target.counter("saHits") < 2
    }

    fun save(g: MafiaGame, target: MafiaPlayer, forced: Boolean) {
        val doc = pascal(g) ?: return
        doc.counters[SAVES] = doc.counter(SAVES) - 1
        g.log += MafiaLog(g.phaseTitle, "دکتر پاسکال ${target.name} را نجات داد" + if (forced) " (حدس درست، نجات اجباری)" else "")
    }

    fun reinhartReady(g: MafiaGame): MafiaPlayer? =
        g.aliveHolder(MafiaRole.REINHART)?.takeIf { !it.has("reinhartDone") && it.counter(GUESSES) > 0 }

    /** Reinhart's guess before the emergency vote; never the pilot. Returns true when right. */
    fun reinhartGuess(g: MafiaGame, target: MafiaPlayer, role: MafiaRole): Boolean {
        val r = g.aliveHolder(MafiaRole.REINHART) ?: return false
        val right = target.role == role && target.role != MafiaRole.PILOT
        if (right) r.flags += "reinhartDone" else {
            r.counters[GUESSES] = r.counter(GUESSES) - 1
            if (r.counter(GUESSES) <= 0) r.flags += "reinhartDone"
        }
        g.log += MafiaLog(g.phaseTitle, "راینهارت نقش ${target.name} را ${role.faName} حدس زد؛ " + if (right) "درست بود" else "اشتباه بود")
        return right
    }

    /**
     * Keeps the rules that follow any exit: an unused gun goes back to the narrator, and the pilot survives his
     * first successful vote. Returns lines for the narrator.
     */
    fun sync(g: MafiaGame): List<String> {
        if (!active(g)) return emptyList()
        init(g)
        val notes = ArrayList<String>()
        g.players.filter { !it.alive && it.has("armed") }.forEach { p ->
            p.flags -= "armed"
            setGuns(g, narratorGuns(g) + 1)
            notes += "اسلحهٔ استفاده‌نشدهٔ ${p.name} به گرداننده برگشت"
        }
        g.players.firstOrNull { it.role == MafiaRole.PILOT && !it.alive && it.deathType == MafiaDeath.VOTE && !it.has("voteShield") }?.let { p ->
            p.flags += "voteShield"
            MafiaFlow.returnPlayer(g, p.id)
            notes += "${p.name} (خلبان) با مصونیت رأی اول در بازی ماند"
        }
        notes.forEach { g.log += MafiaLog(g.phaseTitle, it) }
        return notes
    }

    /** Status announcement (after two exits): sides of those out, shots, talks and the narrator's free guns. */
    fun status(g: MafiaGame): String {
        val out = g.out.joinToString("\n") { "${it.name}: ${faction(it).title}" }.ifBlank { "هنوز کسی خارج نشده است" }
        return "$out\nتعداد شلیک‌ها: ${PersianText.digits(shotsTotal(g))}\nتعداد گفتگوهای خصوصی: ${PersianText.digits(talksTotal(g))}\nاسلحه‌های آزاد نزد گرداننده: ${PersianText.digits(narratorGuns(g))}"
    }

    /** Chaos: the pilot, one rescue member and one Gestapo are left. */
    fun chaos(g: MafiaGame): Boolean {
        val a = g.alive
        if (a.size != 3 || a.none { it.role == MafiaRole.PILOT }) return false
        val others = a.filter { it.role != MafiaRole.PILOT }
        return others.count { faction(it) == Faction.RESCUE } == 1 && others.count { faction(it) == Faction.GESTAPO } == 1
    }

    /** Win by numbers (neutral customers are not counted): all Gestapo out, or Gestapo equal to the rescue. */
    fun winner(g: MafiaGame): String? {
        val gestapo = g.alive.count { faction(it) == Faction.GESTAPO }
        val rescue = g.alive.count { faction(it) == Faction.RESCUE }
        return when {
            gestapo == 0 -> "CITY"
            chaos(g) -> null
            gestapo >= rescue -> "MAFIA"
            else -> null
        }
    }
}
