package com.meysam.divanemtiaz

import kotlin.random.Random

/** A day action a role may take (source app's extra day items). */
data class DayAction(val key: String, val title: String, val role: MafiaRole, val caption: String, val refusal: String?)

/**
 * Day rules of the roles that act in daylight: terrorist, cowboy, sacrifice, suicide bomber, gun holders,
 * russian roulette, cursed word, bomb defusing, Jigsaw's saw, interrogation and the Capo gun.
 */
object MafiaDay {
    const val TERROR = "terror"
    const val COWBOY = "cowboy"
    const val SUICIDE = "suicide"
    const val GUN = "gun"
    const val ROULETTE = "roulette"
    const val CURSE = "curse"
    const val SACRIFICE = "sacrifice"

    /** The first day with the Capo gun setting: voting is replaced by the Capo gun sequence. */
    fun capoDay(g: MafiaGame): Boolean = g.isDay && g.night == 1 && g.extra["capo"] == "1" && g.alive.size > 3

    /** Why a role cannot act by day now (dead, role taken, handcuffed, hostage), or null. */
    fun blocked(g: MafiaGame, role: MafiaRole): String? {
        val p = g.aliveHolder(role) ?: return "${role.faName} در بازی نیست"
        if (p.has("taken")) return "فروشنده نقش ${role.faName} را از او گرفته است"
        if (p.has("handcuff")) return "نقش ${role.faName} دستبند خورده است"
        if (p.has("illusioned")) return "نقش ${role.faName} توسط ${MafiaRole.ILLUSIONIST.faName} گرو گرفته شده است"
        if (p.has("vertigo") && p.marks["vertigoNight"] == g.night) return "${role.faName} امروز تحت اثر سرگیجه است"
        return null
    }

    fun actions(g: MafiaGame): List<DayAction> {
        val out = ArrayList<DayAction>()
        val d = g.day
        if (g.inGame(MafiaRole.TERRORIST) && g.aliveHolder(MafiaRole.TERRORIST) != null && !d.secondVoteDone)
            out += DayAction(TERROR, "ترور", MafiaRole.TERRORIST, "تروریست یک نفر را با خود بیرون می‌برد", blocked(g, MafiaRole.TERRORIST))
        if (g.aliveHolder(MafiaRole.COWBOY)?.counter("once")?.let { it > 0 } == true && !d.firstVoteDone)
            out += DayAction(COWBOY, "کابوی", MafiaRole.COWBOY, "کابوی با افشای نقش، یک نفر را با خود بیرون می‌برد", blocked(g, MafiaRole.COWBOY))
        if (g.aliveHolder(MafiaRole.SUICIDE_BOMBER) != null && g.night >= 1 && !d.firstVoteDone)
            out += DayAction(SUICIDE, "بمبر", MafiaRole.SUICIDE_BOMBER, "بمبر بین دو نفر کناری خود منفجر می‌شود", blocked(g, MafiaRole.SUICIDE_BOMBER))
        if (gunHolders(g).isNotEmpty() && !d.firstVoteDone)
            out += DayAction(GUN, "شلیک تیر", MafiaRole.GUNMAN, gunHolders(g).joinToString("، ") { it.name } + " تفنگ دارند", null)
        val rouletteHolder = g.extra["rouletteOn"]?.toIntOrNull()?.let { g.player(it) }
        if (rouletteHolder != null && rouletteHolder.alive)
            out += DayAction(ROULETTE, "شلیک با اسلحه رولت روسی", MafiaRole.RUSSIAN_ROULETTE, "تفنگ دست ${rouletteHolder.name} است", null)
        val cursed = g.extra["voodooOn"]?.toIntOrNull()?.let { g.player(it) }
        if (cursed != null && cursed.alive && !g.extra["voodooWord"].isNullOrBlank())
            out += DayAction(CURSE, "خروج با کلمه ممنوعه", MafiaRole.VOODOO, "کلمه ممنوعه : ${g.extra["voodooWord"]} — ${cursed.name}", null)
        val sacrifice = g.aliveHolder(MafiaRole.SACRIFICE)
        if (sacrifice != null && sacrifice.counter("once") > 0 && todaysDayDead(g).isNotEmpty())
            out += DayAction(SACRIFICE, "فدایی", MafiaRole.SACRIFICE, "فدایی به جای یکی از خارج‌شده‌های امروز بیرون می‌رود", sacrificeBlocked(g))
        out += MafiaGeneric.dayActions(g)
        return out
    }

    private val nightTypes = setOf(MafiaDeath.NIGHT, MafiaDeath.POISON, MafiaDeath.EXECUTE, MafiaDeath.LEFT, MafiaDeath.DISCIPLINE)

    fun todaysDayDead(g: MafiaGame): List<MafiaPlayer> = g.out.filter { it.outNight == g.night && it.deathType !in nightTypes && it.role != MafiaRole.SACRIFICE }

    fun sacrificeBlocked(g: MafiaGame): String? {
        val s = g.aliveHolder(MafiaRole.SACRIFICE) ?: return "${MafiaRole.SACRIFICE.faName} در بازی نیست"
        MafiaFlow.silenced(s)?.let { return "فدایی ساکت است و نمی‌تواند کلمهٔ فدایی را بگوید" }
        return blocked(g, MafiaRole.SACRIFICE)
    }

    /** True when the sacrifice may stand in for [leaving] right now. */
    fun sacrificeAvailable(g: MafiaGame, leaving: Int): Boolean {
        val s = g.aliveHolder(MafiaRole.SACRIFICE) ?: return false
        return g.isDay && s.id != leaving && s.counter("once") > 0 && sacrificeBlocked(g) == null
    }

    /** The sacrifice leaves instead of [saved]; returns his id. */
    fun sacrifice(g: MafiaGame, saved: Int): Int? {
        val s = g.aliveHolder(MafiaRole.SACRIFICE) ?: return null
        s.counters["once"] = 0
        s.revealed = true
        s.flags += "noReturn"
        MafiaFlow.exit(g, s.id, MafiaDeath.SACRIFICE)
        g.player(saved)?.let { if (!it.alive) MafiaFlow.returnPlayer(g, it.id) }
        g.log += MafiaLog(g.phaseTitle, "${s.name} (فدایی) خود را به جای ${g.player(saved)?.name} فدا کرد")
        return s.id
    }

    fun terrorTargets(g: MafiaGame): List<MafiaPlayer> {
        val t = g.aliveHolder(MafiaRole.TERRORIST) ?: return emptyList()
        val selfKill = g.extra["selfKill"] != "0"
        return g.alive.filter { it.id != t.id && (selfKill || it.side != MafiaSide.MAFIA) }
    }

    fun cowboy(g: MafiaGame) {
        g.aliveHolder(MafiaRole.COWBOY)?.let { it.counters["once"] = 0; it.revealed = true }
    }

    /** Suicide bomber and both seat neighbours. */
    fun suicideVictims(g: MafiaGame): List<Int> {
        val b = g.aliveHolder(MafiaRole.SUICIDE_BOMBER) ?: return emptyList()
        return listOf(b.id) + MafiaFlow.neighbours(g, b.id)
    }

    fun gunHolders(g: MafiaGame): List<MafiaPlayer> = g.alive.filter { it.marks.containsKey("gun") }

    /** War-gun holders who did not shoot (fire back at the end of speaking when the option is on). */
    fun fireback(g: MafiaGame): List<MafiaPlayer> =
        if (g.optionInt(MafiaRole.GUNMAN, "فایر بک", 1) == 1) g.alive.filter { it.marks["gun"] == 2 } else emptyList()

    /** A gun shot by [shooter] at [target]: (message, who leaves). */
    fun gunShot(g: MafiaGame, shooter: Int, target: Int?): Pair<String, List<Int>> {
        val s = g.player(shooter) ?: return "" to emptyList()
        val kind = s.marks.remove("gun") ?: return "این بازیکن تفنگ ندارد" to emptyList()
        val tName = target?.let { g.player(it)?.name } ?: ""
        g.log += MafiaLog(g.phaseTitle, "${s.name} با تفنگ ${if (kind == 2) "جنگی" else "مشقی"} به $tName شلیک کرد")
        if (target == null) return "تیر به صورت هوایی شلیک شد" to emptyList()
        if (kind != 2) return "تیر شلیک شد و نتیجه ای در بر نداشت" to emptyList()
        if (s.has("sabotaged")) return "تیر شلیک شد اما ${MafiaRole.WRECKER.faName} مانع کار شده است و خود بازیکن از بازی خارج خواهد شد" to listOf(s.id)
        return "تیر شلیک شد و بازیکن از بازی خارج خواهد شد" to listOf(target)
    }

    /** Russian roulette: (message, the shot player leaves?). The shot player gets the gun otherwise. */
    fun rouletteShot(g: MafiaGame, target: Int): Pair<String, Boolean> {
        val count = (g.extra["rouletteCount"]?.toIntOrNull() ?: 0) + 1
        val secret = g.extra["rouletteNumber"]?.toIntOrNull() ?: 3
        val shooter = g.extra["rouletteOn"]?.toIntOrNull()?.let { g.player(it)?.name } ?: ""
        g.log += MafiaLog(g.phaseTitle, "$shooter با اسلحهٔ رولت روسی به ${g.player(target)?.name} شلیک کرد (شلیک ${PersianText.digits(count)})")
        return if (count >= secret) {
            listOf("rouletteOn", "rouletteNumber", "rouletteCount").forEach { g.extra.remove(it) }
            "تیر شلیک شد و بازیکن بالا از بازی خارج خواهد شد" to true
        } else {
            g.extra["rouletteCount"] = count.toString()
            g.extra["rouletteOn"] = target.toString()
            "تفنگ خالی بود و به دست نفر بالا خواهد رسید" to false
        }
    }

    fun curseUsed(g: MafiaGame) {
        g.aliveHolder(MafiaRole.VOODOO)?.counters?.set("once", 0)
        g.holders(MafiaRole.VOODOO).forEach { it.counters["once"] = 0 }
        g.extra.remove("voodooWord"); g.extra.remove("voodooOn")
    }

    // ------------------------------------------------------------------ bomb

    fun bombHolder(g: MafiaGame): MafiaPlayer? = g.extra["bombOn"]?.toIntOrNull()?.let { g.player(it) }?.takeIf { it.alive }

    fun bombPending(g: MafiaGame): Boolean = bombHolder(g) != null && "bomb" !in g.day.done

    /** Protector may defuse: alive, not blocked and the bomb is not in front of himself. */
    fun protectorCan(g: MafiaGame): Boolean = blocked(g, MafiaRole.PROTECTOR) == null

    fun bombOnProtector(g: MafiaGame): Boolean = bombHolder(g)?.role == MafiaRole.PROTECTOR && protectorCan(g)

    fun bombDone(g: MafiaGame, text: String) {
        g.day.done += "bomb"
        g.extra.remove("bombOn"); g.extra.remove("bombCode")
        g.log += MafiaLog(g.phaseTitle, text)
    }

    // ------------------------------------------------------------------ saw and interrogation

    /** (saw owner, the two others) when all three are still in the game. */
    fun saw(g: MafiaGame): Pair<MafiaPlayer, List<MafiaPlayer>>? {
        val ids = g.extra["jigsaw"]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (ids.size < 3) return null
        val ps = ids.mapNotNull { g.player(it) }
        if (ps.size < 3) return null
        return ps[0] to ps.drop(1)
    }

    fun sawPending(g: MafiaGame): Boolean = !g.extra["jigsaw"].isNullOrBlank() && "jigsaw" !in g.day.done

    fun interrogated(g: MafiaGame): List<MafiaPlayer> =
        g.extra["interrogate"]?.split(",")?.mapNotNull { it.toIntOrNull()?.let { id -> g.player(id) } } ?: emptyList()

    fun interrogationPending(g: MafiaGame): Boolean = !g.extra["interrogate"].isNullOrBlank() && "interrogation" !in g.day.done

    // ------------------------------------------------------------------ capo

    /** Capo gun result: loaded gun at a target = exit; otherwise the shot is empty or in the air. */
    fun capoShot(g: MafiaGame, gun: Int, target: Int?): Pair<String, Int?> {
        val loaded = g.extra["capoGun"]?.toIntOrNull() ?: 1
        val trustee = g.extra["capoTrustee"]?.toIntOrNull()?.let { g.player(it)?.name } ?: ""
        g.log += MafiaLog(g.phaseTitle, "معتمد شهر ($trustee) با تفنگ شماره ${PersianText.digits(gun)} " + (target?.let { "به ${g.player(it)?.name} شلیک کرد" } ?: "شلیک هوایی کرد"))
        return when {
            target == null -> "تیر به صورت هوایی شلیک شد" to null
            gun != loaded -> "تیر شلیک شد و نتیجه ای در بر نداشت" to null
            else -> "با شلیک تفنگ بازیکن از بازی بیرون خواهد رفت" to target
        }
    }

    fun randomCode(random: Random = Random.Default) = random.nextInt(1, 5)
}
