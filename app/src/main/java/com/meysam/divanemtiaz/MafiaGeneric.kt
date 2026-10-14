package com.meysam.divanemtiaz

/**
 * Night and day behaviour of roles whose rules are data (scenario roles and the narrator's own roles):
 * their night button, who they may choose, the answer shown to the narrator and the effect at night's end.
 */
object MafiaGeneric {
    fun def(r: MafiaRole?): MafiaRoleDef? = r?.let { MafiaRoleRegistry.def(it) }

    /** Acts through the mafia team's turn instead of a button of its own. */
    fun isTeamActor(r: MafiaRole): Boolean = r.isGeneric && def(r)?.withMafia == true

    fun nightAllowed(d: MafiaRoleDef, night: Int): Boolean = when (d.nights ?: MafiaNights.EVERY) {
        MafiaNights.EVERY -> night >= 1
        MafiaNights.EVEN -> night >= 2 && night % 2 == 0
        MafiaNights.ODD -> night % 2 == 1
        MafiaNights.INTRO -> night == 0
        MafiaNights.FROM_SECOND -> night >= 2
    }

    fun introNight(r: MafiaRole): Boolean = r.isGeneric && def(r)?.nights == MafiaNights.INTRO && !isTeamActor(r)

    private fun pool(a: MafiaAbility, d: MafiaRoleDef): TargetPool = when {
        d.selfOnly == true -> TargetPool.SELF
        a == MafiaAbility.REVIVE -> TargetPool.OUT
        a == MafiaAbility.SAVE || a == MafiaAbility.ARMOR || a == MafiaAbility.RECORD -> TargetPool.ALIVE
        d.selfTarget == true -> TargetPool.ALIVE
        else -> TargetPool.ALIVE_OTHERS
    }

    fun mode(a: MafiaAbility, d: MafiaRoleDef, label: String = a.title): JobMode {
        val p = pool(a, d)
        val min = if (a == MafiaAbility.COMPARE) 2 else (d.min ?: 1).coerceAtLeast(0)
        val max = if (a == MafiaAbility.COMPARE) 2 else (d.max ?: 1).coerceAtLeast(maxOf(1, min))
        return JobMode(label, p, if (p == TargetPool.SELF) 0 else min, if (p == TargetPool.SELF) 1 else max,
            guessRole = a == MafiaAbility.GUESS_KILL, includeSelf = d.selfTarget != false && (a == MafiaAbility.SAVE || a == MafiaAbility.ARMOR || a == MafiaAbility.RECORD))
    }

    /** The night button of a data-defined role, or null when it has nothing to do tonight. */
    fun job(g: MafiaGame, r: MafiaRole): NightJob? {
        val d = def(r) ?: return null
        if (isTeamActor(r)) return null
        val a = d.ability ?: MafiaAbility.NONE
        if (a == MafiaAbility.NONE || d.wakes == false) return null
        if (!nightAllowed(d, g.night)) return null
        val holders = g.holders(r)
        if (holders.isNotEmpty() && holders.all { !it.alive && it.revealed }) return null
        val modes = ArrayList<JobMode>()
        modes += mode(a, d)
        d.altAbility?.takeIf { it != MafiaAbility.NONE && it != a }?.let { modes += mode(it, d) }
        val prompt = d.prompt ?: ("${r.faName} بیدار شود\n" + a.hint + (d.altAbility?.takeIf { it != MafiaAbility.NONE }?.let { " — یا: " + it.hint } ?: ""))
        return NightJob(MafiaRoleRegistry.jobKey(r), r.faName, r, r.side, r.glyph, prompt, modes, holders.map { it.id })
    }

    /** Extra choices of the mafia turn from data-defined team roles (e.g. «حدس نقش (سلطان)»). */
    fun groupModes(g: MafiaGame): List<Triple<JobMode, MafiaRole, MafiaAbility>> {
        val out = ArrayList<Triple<JobMode, MafiaRole, MafiaAbility>>()
        g.alive.mapNotNull { it.role }.distinct().filter { isTeamActor(it) }.forEach { r ->
            val d = def(r) ?: return@forEach
            val holder = g.aliveHolder(r) ?: return@forEach
            if (holder.counter(MafiaGenericRoles.USES) <= 0 && (d.ability ?: MafiaAbility.NONE) != MafiaAbility.NONE) return@forEach
            listOfNotNull(d.ability, d.altAbility).filter { it != MafiaAbility.NONE && it != MafiaAbility.SHOT && it != MafiaAbility.RECORD }.distinct().forEach { a ->
                if (nightAllowed(d, g.night)) out += Triple(mode(a, d, "${a.title} (${r.faName})"), r, a)
            }
        }
        return out
    }

    fun groupAbility(g: MafiaGame, label: String): Pair<MafiaRole, MafiaAbility>? =
        groupModes(g).firstOrNull { it.first.label == label }?.let { it.second to it.third }
            ?: MafiaRole.values().filter { isTeamActor(it) }.flatMap { r -> MafiaAbility.values().map { a -> Triple("${a.title} (${r.faName})", r, a) } }
                .firstOrNull { it.first == label }?.let { it.second to it.third }

    fun abilityOf(job: NightJob, mode: JobMode): MafiaAbility? {
        val r = job.role ?: return null
        if (!r.isGeneric) return null
        return MafiaAbility.values().firstOrNull { it.title == mode.label } ?: def(r)?.ability
    }

    /** Spends one use of the mafia-turn ability of a data-defined team role. */
    fun spendGroup(g: MafiaGame, label: String) {
        val (r, _) = groupAbility(g, label) ?: return
        val p = g.aliveHolder(r) ?: return
        val left = p.counter(MafiaGenericRoles.USES)
        if (left in 1..999) p.counters[MafiaGenericRoles.USES] = left - 1
    }

    fun refundGroup(g: MafiaGame, label: String) {
        val (r, _) = groupAbility(g, label) ?: return
        val p = g.aliveHolder(r) ?: return
        val left = p.counter(MafiaGenericRoles.USES)
        if (left < 1000) p.counters[MafiaGenericRoles.USES] = left + 1
    }

    private fun convertible(t: MafiaPlayer): Boolean =
        (t.role == MafiaRole.CITIZEN || t.role == MafiaRole.SUSPECT || t.role == MafiaRole.ROYALIST || (t.role == MafiaRole.ARMORED && t.armor <= 0)) && !t.has("chaplin") && !t.has("smith")

    /** How a role looks to inquiries: its fixed look when set, or null for the normal rule. */
    fun look(g: MafiaGame, p: MafiaPlayer): Boolean? {
        if (p.has("taken")) return null
        if (g.records.values.any { r -> !r.void && p.id in r.targets && abilityOfRecord(g, r) == MafiaAbility.FRAME }) return true
        return when (def(p.role)?.inquiry) {
            InquiryLook.NEGATIVE -> false
            InquiryLook.POSITIVE -> true
            else -> null
        }
    }

    /** Tonight's inquiry of this player is reversed by a data-defined «معکوس کردن استعلام». */
    fun inverted(g: MafiaGame, p: MafiaPlayer): Boolean =
        g.records.values.any { r -> !r.void && p.id in r.targets && abilityOfRecord(g, r) == MafiaAbility.INVERT }

    /** The ability a stored night record used (for data-defined roles and mafia-turn modes). */
    fun abilityOfRecord(g: MafiaGame, r: NightRecord): MafiaAbility? {
        if (r.key == MafiaNight.MAFIA_GROUP) return groupAbility(g, r.choice)?.second
        val role = MafiaRoleRegistry.roleOfKey(r.key) ?: return null
        if (!role.isGeneric) return null
        if (!role.isDataRole && !r.key.startsWith("G_")) return null
        return MafiaAbility.values().firstOrNull { it.title == r.choice } ?: def(role)?.ability
    }

    /** Answer shown to the narrator right after the choice. */
    fun result(g: MafiaGame, a: MafiaAbility?, targets: List<Int>, guess: MafiaRole?, holder: MafiaPlayer?): String {
        val t = targets.firstOrNull()?.let { g.player(it) }
        return when (a) {
            MafiaAbility.INQUIRY -> if (t == null) "" else if (MafiaNight.detectivePositive(g, t, holder)) "استعلام مثبت 👍 (مافیا)" else "استعلام منفی 👎"
            MafiaAbility.COMPARE -> {
                val ps = targets.mapNotNull { g.player(it) }
                if (ps.size < 2) "" else {
                    fun seen(p: MafiaPlayer) = when { look(g, p) == false -> MafiaSide.CITY; p.role == MafiaRole.SPY || p.role == MafiaRole.GODFATHER -> MafiaSide.CITY; else -> p.side }
                    if (seen(ps[0]) == seen(ps[1])) "هم‌ساید هستند (تأیید) 👍" else "هم‌ساید نیستند (رد) 👎"
                }
            }
            MafiaAbility.GUESS_KILL -> if (t != null && guess != null && t.role == guess) "حدس درست بود؛ بازیکن سلاخی می‌شود" else "حدس اشتباه بود"
            MafiaAbility.CONVERT -> if (t != null && convertible(t)) "تبدیل موفق بود؛ دست بازیکن لمس شود، او مافیا ساده خواهد بود" else "تبدیل موفق نبود؛ لمس دست به صورت صوری انجام شود"
            MafiaAbility.REVIVE -> t?.let { "${it.name} به بازی برمی‌گردد" } ?: ""
            else -> ""
        }
    }

    /** Who blocks this player's job tonight through a data-defined «مسدود کردن». */
    fun blockerOf(g: MafiaGame, p: MafiaPlayer): MafiaRole? {
        g.records.values.forEach { r ->
            if (r.void || p.id !in r.targets) return@forEach
            if (abilityOfRecord(g, r) == MafiaAbility.BLOCK) {
                return if (r.key == MafiaNight.MAFIA_GROUP) groupAbility(g, r.choice)?.first else MafiaRoleRegistry.roleOfKey(r.key)
            }
        }
        return null
    }

    class Effects {
        val shots = ArrayList<Int>()
        val kills = ArrayList<Pair<Int, String>>()
        val saves = ArrayList<Int>()
        val revives = ArrayList<Int>()
        val report = ArrayList<String>()
    }

    /** Effects of tonight's data-defined actions; persistent changes only when [apply]. */
    fun effects(g: MafiaGame, apply: Boolean): Effects {
        val e = Effects()
        g.records.values.forEach { r ->
            if (r.void) return@forEach
            val a = abilityOfRecord(g, r) ?: return@forEach
            val ts = r.targets
            val t = ts.firstOrNull()
            when (a) {
                MafiaAbility.SHOT -> ts.forEach { e.shots += it }
                MafiaAbility.KILL -> ts.forEach { e.kills += it to "custom" }
                MafiaAbility.SAVE -> ts.forEach { e.saves += it }
                MafiaAbility.REVIVE -> ts.forEach { e.revives += it }
                MafiaAbility.GUESS_KILL -> if (t != null && g.player(t)?.role == r.guess) e.kills += t to "execute"
                MafiaAbility.SILENCE -> if (apply) ts.forEach { g.player(it)?.flags?.add("silenced") }
                MafiaAbility.ARMOR -> if (apply) ts.forEach { g.player(it)?.let { p -> p.armor += 1 } }
                MafiaAbility.CONVERT -> if (apply && t != null) g.player(t)?.takeIf { convertible(it) }?.let { it.role = MafiaRole.MAFIA }
                else -> Unit
            }
        }
        return e
    }

    fun nightImmune(p: MafiaPlayer): Boolean = !p.has("taken") && def(p.role)?.nightImmune == true

    // ------------------------------------------------------------------ day

    fun dayActions(g: MafiaGame): List<DayAction> {
        val out = ArrayList<DayAction>()
        g.alive.mapNotNull { it.role }.distinct().forEach { r ->
            val d = def(r) ?: return@forEach
            val day = d.dayAbility ?: MafiaDayAbility.NONE
            if (day == MafiaDayAbility.NONE) return@forEach
            val holder = g.aliveHolder(r) ?: return@forEach
            if (holder.counters.containsKey(MafiaGenericRoles.DAY_USES) && holder.counter(MafiaGenericRoles.DAY_USES) <= 0) return@forEach
            out += DayAction("G_" + r.name, "${day.title} (${r.faName})", r, MafiaRoleText.explain(r).take(80), MafiaDay.blocked(g, r))
        }
        return out
    }

    fun spendDay(g: MafiaGame, r: MafiaRole) {
        val p = g.aliveHolder(r) ?: return
        val left = p.counter(MafiaGenericRoles.DAY_USES)
        if (left in 1..999) p.counters[MafiaGenericRoles.DAY_USES] = left - 1
    }
}
