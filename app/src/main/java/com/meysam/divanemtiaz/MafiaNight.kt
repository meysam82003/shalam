package com.meysam.divanemtiaz

import kotlin.random.Random

/** Who a night job may choose from. */
enum class TargetPool { NONE, ALIVE, ALIVE_OTHERS, NON_MAFIA, MAFIA_TEAM, OUT, TONIGHT_DEAD, SELF }

/** One way of doing a night job (most roles have one; the mafia group and a few roles have several). */
data class JobMode(
    val label: String,
    val pool: TargetPool,
    val min: Int,
    val max: Int,
    val guessRole: Boolean = false,
    val number: IntRange? = null,
    val word: Boolean = false,
    /** Each selected target also gets one of these types (e.g. war / exercise gun). */
    val typed: List<String> = emptyList(),
    val includeSelf: Boolean = false
)

data class NightJob(
    val key: String,
    val title: String,
    val role: MafiaRole?,
    val side: MafiaSide?,
    val glyph: MafiaGlyph,
    val prompt: String,
    val modes: List<JobMode>,
    val holders: List<Int>
)

/**
 * Night rules of «راوی مافیا»: which buttons appear, who may be chosen, what blocks a job,
 * the immediate answers shown to the narrator and the night resolution with its morning report.
 */
object MafiaNight {
    const val MAFIA_GROUP = "MAFIA_GROUP"
    const val INTRO = "INTRO"
    const val ATTAR_ANTIDOTE = "ATTAR_ANTIDOTE"
    fun cardKey(card: MafiaLastCard) = "CARD_" + card.name

    /** Roles that act through the mafia group's button rather than their own. */
    val teamActors = setOf(MafiaRole.GODFATHER, MafiaRole.MAFIA, MafiaRole.GOOD_MAN, MafiaRole.POISON_MAKER, MafiaRole.LOVER,
        MafiaRole.BODYGUARD, MafiaRole.NATO, MafiaRole.TERRORIST, MafiaRole.SPY, MafiaRole.STRONG_MAN)

    private val introNightRoles = listOf(MafiaRole.CORONA, MafiaRole.NOSTRADAMUS, MafiaRole.JACK_SPARROW, MafiaRole.INHERITOR, MafiaRole.CHURCHILL, MafiaRole.MASON)

    /** Mafia members who wake with the team. */
    fun mafiaTeam(g: MafiaGame): List<MafiaPlayer> = g.players.filter { p ->
        p.side == MafiaSide.MAFIA && when (p.role) {
            MafiaRole.THIEF -> p.has("revealedThief")
            MafiaRole.PABLO -> g.extra["pabloSwapped"] == "1"
            MafiaRole.CAST_AWAY -> p.has("joined")
            else -> true
        }
    }

    /** The mafia member who holds the night shot: the source's getMafiaHighLevel order. */
    fun shooter(g: MafiaGame): MafiaPlayer? {
        val order = listOf(MafiaRole.GODFATHER, MafiaRole.LOVER, MafiaRole.DOCTOR_LECTOR, MafiaRole.POISON_MAKER, MafiaRole.INVESTIGATOR,
            MafiaRole.TERRORIST, MafiaRole.CHARLATAN, MafiaRole.MATADOR, MafiaRole.NATO, MafiaRole.GOOD_MAN, MafiaRole.SPY, MafiaRole.BODYGUARD)
        val team = mafiaTeam(g).filter { it.alive }
        team.firstOrNull { p -> p.role?.let { MafiaGeneric.isTeamActor(it) && MafiaGeneric.def(it)?.ability == MafiaAbility.SHOT } == true }?.let { return it }
        order.forEach { r -> team.firstOrNull { it.role == r }?.let { return it } }
        return team.firstOrNull { it.role == MafiaRole.MAFIA } ?: team.firstOrNull()
    }

    private fun pendingCard(g: MafiaGame, card: MafiaLastCard) = g.deck.firstOrNull { it.card == card && it.pending }

    // ------------------------------------------------------------------ job list

    fun jobs(g: MafiaGame): List<NightJob> {
        val out = ArrayList<NightJob>()
        // Deferred last-move cards come first and must be done first.
        listOf(MafiaLastCard.VERTIGO, MafiaLastCard.CARD_EXCHANGE, MafiaLastCard.FACE_OFF, MafiaLastCard.FINAL_SHOT, MafiaLastCard.LAST_VENOM).forEach { c ->
            pendingCard(g, c)?.let { st -> out += cardJob(g, st) }
        }
        if (g.night == 0) {
            out += NightJob(INTRO, "معارفه", null, null, MafiaGlyph.MASK, "شب معارفه: نقش‌ها خود را به راوی معرفی می‌کنند", listOf(JobMode("", TargetPool.NONE, 0, 0)), emptyList())
            introNightRoles.forEach { r ->
                if (!g.inGame(r)) return@forEach
                if (r == MafiaRole.MASON && g.optionInt(r, "اجبار به انتخاب در شب معارفه", 1) == 0) return@forEach
                roleJob(g, r)?.let { out += it }
            }
            g.players.mapNotNull { it.role }.distinct().filter { MafiaGeneric.introNight(it) }.forEach { r -> MafiaGeneric.job(g, r)?.let { out += it } }
            return out
        }
        val endgame = g.alive.size <= 3 && pendingCard(g, MafiaLastCard.FACE_OFF) == null && pendingCard(g, MafiaLastCard.CARD_EXCHANGE) == null
        if (endgame) return out
        pendingAttar(g)?.let { victim ->
            out += NightJob(ATTAR_ANTIDOTE, "پادزهر عطار", MafiaRole.ATTAR, MafiaSide.CITY, MafiaGlyph.LEAF,
                "${victim.name} دیشب زهر خورده است. پس از دفاع او و رأی‌گیری، عطار تصمیم می‌گیرد پادزهر را به بازیکن بدهد یا نه.",
                listOf(JobMode("پادزهر می‌دهد", TargetPool.NONE, 0, 0), JobMode("پادزهر نمی‌دهد", TargetPool.NONE, 0, 0)), g.holders(MafiaRole.ATTAR).map { it.id })
        }
        val roles = g.players.mapNotNull { it.role }.distinct()
            .filter { it.wakeOrder > 0 && it !in teamActors && !MafiaGeneric.isTeamActor(it) }
            .sortedBy { it.wakeOrder }
        var groupAdded = false
        roles.forEach { r ->
            if (!groupAdded && r.wakeOrder > 25) { mafiaGroupJob(g)?.let { out += it }; groupAdded = true }
            roleJob(g, r)?.let { out += it }
        }
        if (!groupAdded) mafiaGroupJob(g)?.let { out += it }
        return out
    }

    private fun pendingAttar(g: MafiaGame): MafiaPlayer? =
        g.players.firstOrNull { it.alive && it.marks["attarPoison"] == g.night - 1 }

    private fun cardJob(g: MafiaGame, st: MafiaCardState): NightJob {
        val holder = g.player(st.holder)
        val c = st.card
        val (prompt, mode) = when (c) {
            MafiaLastCard.VERTIGO -> "دارندهٔ کارت سرگیجه (${holder?.name ?: ""}) بیدار شود و یک نفر را انتخاب کند؛ کارهای آن شخص تا ۲۴ ساعت بی‌اثر است." to JobMode("", TargetPool.ALIVE, 1, 1)
            MafiaLastCard.CARD_EXCHANGE -> "دارندهٔ کارت کارت‌به‌کارت دو نفر را انتخاب کند تا نقش‌هایشان جابه‌جا شود (قابل لغو نیست)." to JobMode("", TargetPool.ALIVE, 2, 2)
            MafiaLastCard.FACE_OFF -> "دارندهٔ کارت تغییر چهره نقش خود را با یک بازیکن عوض می‌کند. بازیکن انتخابی را بیدار کنید و نقش جدیدش را به او نشان دهید." to JobMode("", TargetPool.ALIVE, 1, 1)
            MafiaLastCard.FINAL_SHOT -> "دارندهٔ کارت شلیک نهایی به جای مافیا شلیک شب را انجام می‌دهد." to JobMode("", TargetPool.ALIVE, 1, 1)
            else -> "دارندهٔ کارت زهر آخر به یک نفر زهر می‌دهد؛ آن شخص بدون هیچ نجاتی از بازی بیرون می‌رود." to JobMode("", TargetPool.ALIVE, 1, 1)
        }
        return NightJob(cardKey(c), c.faName, null, null, c.glyph, prompt, listOf(mode), listOfNotNull(holder?.id))
    }

    private fun mafiaGroupJob(g: MafiaGame): NightJob? {
        if (pendingCard(g, MafiaLastCard.FINAL_SHOT) != null) return null
        if (mafiaTeam(g).isEmpty()) return null
        val s = settingsSelfKill(g)
        val shotPool = if (s) TargetPool.ALIVE else TargetPool.NON_MAFIA
        val shots = 1 + (g.extra["extraShots"]?.toIntOrNull() ?: 0)
        val modes = ArrayList<JobMode>()
        modes += JobMode("شلیک شب", shotPool, 1, shots)
        val gf = g.aliveHolder(MafiaRole.GODFATHER)
        if (gf != null && g.optionInt(MafiaRole.GODFATHER, "حس ششم", 1) == 1) modes += JobMode("حس ششم", TargetPool.NON_MAFIA, 1, 1, guessRole = true)
        if (gf != null && g.optionInt(MafiaRole.GODFATHER, "سوداگری", 0) == 1 && gf.counter("trade") > 0) modes += JobMode("سوداگری", TargetPool.ALIVE_OTHERS, 2, 2)
        g.aliveHolder(MafiaRole.STRONG_MAN)?.takeIf { it.counter("تعداد انتخاب") > 0 }?.let { modes += JobMode("مرد قوی", shotPool, 1, 1) }
        g.aliveHolder(MafiaRole.POISON_MAKER)?.takeIf { it.counter("poison") > 0 }?.let { modes += JobMode("سم ساز", shotPool, 1, 1) }
        g.aliveHolder(MafiaRole.NATO)?.takeIf { it.counter("تعداد ناتویی") > 0 }?.let { modes += JobMode("ناتو", TargetPool.NON_MAFIA, 1, 1, guessRole = true) }
        g.aliveHolder(MafiaRole.GOOD_MAN)?.takeIf { it.counter("buy") > 0 && g.out.any { p -> p.side == MafiaSide.MAFIA } }?.let { modes += JobMode("ساول گودمن", TargetPool.NON_MAFIA, 1, 1) }
        modes += MafiaGeneric.groupModes(g).map { it.first }
        val holder = shooter(g)
        val prompt = "گروه مافیا از خواب بیدار شوند" + (holder?.let { "\nتیر به دست ${it.role?.faName ?: ""} (${it.name})" } ?: "") +
            "\nگروه مافیا هر شب می‌تواند به یک نفر شلیک کند یا به جای شلیک از یک قابلیت ویژه استفاده کند."
        return NightJob(MAFIA_GROUP, "گروه مافیا", null, MafiaSide.MAFIA, MafiaGlyph.MASK, prompt, modes, mafiaTeam(g).map { it.id })
    }

    private fun settingsSelfKill(g: MafiaGame): Boolean = g.extra["selfKill"] != "0"

    private fun roleJob(g: MafiaGame, r: MafiaRole): NightJob? {
        if (r.isGeneric) return MafiaGeneric.job(g, r)
        val job = codedJob(g, r) ?: return null
        // The narrator's own wording of the night call.
        return MafiaRoleRegistry.user[r]?.prompt?.takeIf { it.isNotBlank() }?.let { job.copy(prompt = it) } ?: job
    }

    private fun codedJob(g: MafiaGame, r: MafiaRole): NightJob? {
        val holders = g.holders(r)
        val holder = holders.firstOrNull()
        // A dead holder whose role was announced has no button; an unannounced one keeps it as a decoy.
        if (holders.isNotEmpty() && holders.all { !it.alive && it.revealed }) return null
        val n = g.night
        val modes: List<JobMode> = when (r) {
            MafiaRole.DOCTOR -> listOf(JobMode("", TargetPool.ALIVE, 1, if (n == 1) g.optionInt(r, "نجات شب اول", 1).coerceAtLeast(1) else 1, includeSelf = true))
            MafiaRole.DOCTOR_LECTOR -> listOf(JobMode("", TargetPool.MAFIA_TEAM, 1, 1, includeSelf = true))
            MafiaRole.PSYCHOLOGIST, MafiaRole.DETECTIVE, MafiaRole.RESEARCHER, MafiaRole.PROFESSIONAL, MafiaRole.SALESMAN,
            MafiaRole.REPORTER, MafiaRole.SABA, MafiaRole.FELON, MafiaRole.KILLER, MafiaRole.COMMANDO, MafiaRole.CITIZEN_KANE,
            MafiaRole.JUPITER, MafiaRole.JACK_SPARROW, MafiaRole.OCEAN, MafiaRole.SNIPER, MafiaRole.SMITH, MafiaRole.SPECIAL_DETECTIVE,
            MafiaRole.CAST_AWAY, MafiaRole.INHERITOR, MafiaRole.CHAPLIN, MafiaRole.SNOWMAN, MafiaRole.MASON -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, 1))
            MafiaRole.CHARLATAN -> listOf(JobMode("", TargetPool.ALIVE, 1, 1, includeSelf = true))
            MafiaRole.NATASHA -> listOf(JobMode("", TargetPool.ALIVE, 1, 1, includeSelf = true))
            MafiaRole.INTERROGATOR -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 2, 2))
            MafiaRole.DIE_HARD -> listOf(JobMode("استعلام می‌خواهد", TargetPool.NONE, 0, 0), JobMode("استعلام نمی‌خواهد", TargetPool.NONE, 0, 0))
            MafiaRole.SLEEP_WALKER -> listOf(JobMode("امشب مافیا را می‌خواباند", TargetPool.NONE, 0, 0), JobMode("کاری انجام نمی‌دهد", TargetPool.NONE, 0, 0))
            MafiaRole.CLEVER -> listOf(JobMode("بازیکن", TargetPool.ALIVE_OTHERS, 1, 1), JobMode("نقش", TargetPool.NONE, 0, 0, guessRole = true))
            MafiaRole.HACKER -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 3, 3))
            MafiaRole.SALVATION_ANGEL -> listOf(JobMode("", TargetPool.TONIGHT_DEAD, 0, 1))
            MafiaRole.THIEF -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 2, 2))
            MafiaRole.GUNMAN -> listOf(JobMode("", TargetPool.ALIVE, 1, 2, typed = listOf("تفنگ جنگی", "تفنگ مشقی"), includeSelf = true))
            MafiaRole.GUARD -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, if (n == 1) g.optionInt(r, "مراقبت شب اول", 2).coerceAtLeast(1) else 1))
            MafiaRole.GUARDIAN -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, if (n == 1) g.optionInt(r, "نگهبانی شب اول", 2).coerceAtLeast(1) else 1))
            MafiaRole.INVESTIGATOR, MafiaRole.FRAUD, MafiaRole.YAKUZA, MafiaRole.WITCHER, MafiaRole.MATADOR, MafiaRole.ILLUSIONIST ->
                listOf(JobMode("", TargetPool.NON_MAFIA, 1, 1))
            MafiaRole.CORONA -> if (n == 0) listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, g.optionInt(r, "انتخاب شب معارفه", 1).coerceAtLeast(1))) else return null
            MafiaRole.NOSTRADAMUS -> if (n == 0) listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, g.optionInt(r, "تعداد انتخاب", 3).coerceIn(1, (g.alive.size - 1).coerceAtLeast(1)))) else return null
            MafiaRole.CONSTANTINE -> listOf(JobMode("", TargetPool.OUT, 1, 1))
            MafiaRole.WRECKER -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, 2))
            MafiaRole.SHERLOCK_HOLMES -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, 1, guessRole = true))
            MafiaRole.ZODIAC -> if (n % 2 == 0) listOf(JobMode("شلیک", TargetPool.ALIVE_OTHERS, 1, 1), JobMode("نامه", TargetPool.NONE, 0, 0, word = true))
                else listOf(JobMode("نامه", TargetPool.NONE, 0, 0, word = true))
            MafiaRole.BOMBER -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, 1, number = 1..4))
            MafiaRole.COMMANDER -> {
                val sniper = g.holders(MafiaRole.SNIPER)
                if (sniper.isNotEmpty() && sniper.any { it.alive }) listOf(JobMode("تایید شلیک", TargetPool.NONE, 0, 0), JobMode("لغو شلیک", TargetPool.NONE, 0, 0))
                else listOf(JobMode("شلیک", TargetPool.ALIVE_OTHERS, 1, 1))
            }
            MafiaRole.PRIEST -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, g.optionInt(r, "تعداد نفرات در هر شب", 1).coerceAtLeast(1)))
            MafiaRole.RUSSIAN_ROULETTE -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, 1, number = 3..7))
            MafiaRole.JIGSAW -> listOf(JobMode("", TargetPool.ALIVE, 3, 3, includeSelf = true))
            MafiaRole.VOODOO -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, 1, word = true))
            MafiaRole.SAINT -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, if (n == 1) 2 else 1))
            MafiaRole.SPIDER -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, g.optionInt(r, "تعداد انتخاب", 2).coerceAtLeast(1)))
            MafiaRole.ATTAR -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, 1))
            MafiaRole.PABLO -> listOf(JobMode("", TargetPool.ALIVE_OTHERS, 1, 1))
            MafiaRole.CHURCHILL -> if (n == 0) listOf(JobMode("شرط خروج", TargetPool.ALIVE_OTHERS, 1, 1))
                else listOf(JobMode("شلیک", TargetPool.ALIVE_OTHERS, 1, 1), JobMode("سلاخی", TargetPool.ALIVE_OTHERS, 1, 1, guessRole = true))
            else -> return null
        }
        // Night-specific availability from the source app.
        when (r) {
            MafiaRole.INHERITOR, MafiaRole.JACK_SPARROW -> if (r == MafiaRole.INHERITOR && n != 0) return null
            MafiaRole.CHAPLIN -> if (n != 1) return null
            MafiaRole.HACKER -> if (n != 2) return null
            MafiaRole.CAST_AWAY -> if (n > 3 || holder?.has("joined") == true) return null
            MafiaRole.PABLO -> if (g.extra["pabloSwapped"] == "1" || g.aliveHolder(MafiaRole.GODFATHER) != null) return null
            else -> Unit
        }
        val prompt = rolePrompt(g, r)
        return NightJob(r.name, r.faName, r, r.side, r.glyph, prompt, modes, holders.map { it.id })
    }

    /** Text the narrator reads for a role's turn (wake instruction plus what the role does). */
    fun rolePrompt(g: MafiaGame, r: MafiaRole): String {
        val wake = "${r.faName} بیدار شود"
        val what = when (r) {
            MafiaRole.DOCTOR -> "دکتر می‌تواند یک نفر را نجات دهد" + if (g.night == 1) " (شب اول تا ${PersianText.digits(g.optionInt(r, "نجات شب اول", 1))} نفر)" else ""
            MafiaRole.DOCTOR_LECTOR -> "دکتر لکتر می‌تواند یکی از اعضای مافیا را نجات دهد"
            MafiaRole.PSYCHOLOGIST -> "روانپزشک می‌تواند یک نفر را برای فردا ساکت کند"
            MafiaRole.DETECTIVE -> "کارآگاه استعلام یک نفر را می‌گیرد"
            MafiaRole.PROFESSIONAL -> "حرفه‌ای می‌تواند به یک نفر شلیک کند؛ شلیک به شهروند باعث خروج خودش می‌شود"
            MafiaRole.SNIPER -> "اسنایپر یک نفر را هدف می‌گیرد"
            MafiaRole.MATADOR -> "ماتادور هر شب می‌تواند یک نفر را انتخاب کند\nاگر آن شخص قابلیت شب داشته باشد از انجام کار منع خواهد شد"
            MafiaRole.ILLUSIONIST -> "شعبده باز توانایی یک نفر را برای ۲۴ ساعت می‌گیرد"
            MafiaRole.NATASHA -> "ناتاشا یک نفر را برای فردا کاملاً ساکت می‌کند"
            MafiaRole.SLEEP_WALKER -> "خوابگرد می‌تواند یک بار در بازی گروه مافیا را بخواباند (و خودش از بازی خارج می‌شود)"
            MafiaRole.DIE_HARD -> "آیا جان سخت امشب استعلام وضعیت خارج‌شده‌ها را می‌خواهد؟"
            MafiaRole.JIGSAW -> "جیگساو سه نفر را انتخاب کند؛ نفر اول انتخابی صاحب اره است (اره را نمی‌تواند به خودش بدهد)"
            MafiaRole.GUNMAN -> "تفنگدار به یک یا دو نفر تفنگ بدهد و نوع هر تفنگ را مشخص کنید"
            MafiaRole.RUSSIAN_ROULETTE -> "رولت روسی به یک نفر تفنگ بدهد و یک عدد از ۳ تا ۷ اعلام کند"
            MafiaRole.BOMBER -> "بمب گذار جلوی یک نفر بمب بگذارد و کد خنثی سازی (۱ تا ۴) را اعلام کند"
            MafiaRole.VOODOO -> "افسونگر یک کلمهٔ چهار حرفی یا بیشتر را روی یک نفر افسون کند"
            MafiaRole.INTERROGATOR -> "بازپرس دو نفر را برای بازپرسی فردا انتخاب کند"
            else -> null
        }
        return if (what != null) "$wake\n$what" else wake
    }

    // ------------------------------------------------------------------ who may be chosen

    fun candidates(g: MafiaGame, job: NightJob, mode: JobMode): List<MafiaPlayer> {
        val holders = job.holders.toSet()
        return when (mode.pool) {
            TargetPool.NONE -> emptyList()
            TargetPool.ALIVE -> g.alive.filter { mode.includeSelf || it.id !in holders || job.role == null }.filter { selfAllowed(g, job, it) }
            TargetPool.ALIVE_OTHERS -> g.alive.filter { it.id !in holders }
            TargetPool.NON_MAFIA -> g.alive.filter { it.side != MafiaSide.MAFIA || (it.role == MafiaRole.THIEF && !it.has("revealedThief")) }
            TargetPool.MAFIA_TEAM -> g.alive.filter { it.side == MafiaSide.MAFIA }.filter { selfAllowed(g, job, it) }
            TargetPool.OUT -> g.out.filter { !it.has("noReturn") }
            TargetPool.TONIGHT_DEAD -> preview(g).dead.mapNotNull { g.player(it) }.filter { !it.has("noReturn") }
            TargetPool.SELF -> job.holders.mapNotNull { g.player(it) }.filter { it.alive }
        }.filter { p -> !(job.role == MafiaRole.MATADOR || job.role == MafiaRole.ILLUSIONIST) || g.extra["last_${job.role.name}"] != p.id.toString() }
    }

    private fun selfAllowed(g: MafiaGame, job: NightJob, p: MafiaPlayer): Boolean {
        if (p.id !in job.holders) return true
        return when (job.role) {
            MafiaRole.DOCTOR, MafiaRole.DOCTOR_LECTOR -> p.counter("تعداد نجات خود") > 0
            MafiaRole.GUNMAN, MafiaRole.CHARLATAN, MafiaRole.NATASHA, MafiaRole.JIGSAW -> true
            else -> false
        }
    }

    // ------------------------------------------------------------------ blocking

    /** Why a job cannot start now, or null. Decoys (dead holder) are not a refusal: see [isDecoy]. */
    fun refusal(g: MafiaGame, job: NightJob): String? {
        if (job.key == INTRO) return null
        val firstCards = listOf(MafiaLastCard.VERTIGO to "ابتدا باید عملیات سرگیجه انجام شود", MafiaLastCard.CARD_EXCHANGE to "ابتدا باید عملیات کارت به کارت انجام شود", MafiaLastCard.FACE_OFF to "ابتدا باید عملیات تغییر چهره انجام شود")
        firstCards.forEach { (c, msg) -> if (pendingCard(g, c) != null && cardKey(c) !in g.records && job.key != cardKey(c)) return msg }
        if (g.night == 0 && job.key != INTRO && job.role != MafiaRole.CORONA && job.role != MafiaRole.NOSTRADAMUS && job.role != MafiaRole.JACK_SPARROW && INTRO !in g.records && job.role !in introNightRoles) return "ابتدا معارفه را انجام دهید"
        val role = job.role ?: return null
        if (isDecoy(g, job)) return null
        val holder = g.player(job.holders.firstOrNull() ?: return null) ?: return null
        if (holder.has("taken")) return "نقش ${role.faName} توسط فروشنده گرفته شده است"
        if (holder.has("handcuff") && role.side != MafiaSide.MAFIA) return "نقش ${role.faName} دستبند خورده است"
        hostageBy(g, holder)?.let { by -> return "نقش ${role.faName} توسط ${by.faName} گرو گرفته شده است" }
        if (exhausted(g, role, holder)) return "${role.faName} از تمام قابلیت خود استفاده کرده است"
        return null
    }

    /** The holder is out but the role was never announced: the button still works so nobody can tell. */
    fun isDecoy(g: MafiaGame, job: NightJob): Boolean = job.role != null && job.holders.isNotEmpty() && job.holders.all { g.player(it)?.alive != true }

    fun hostageBy(g: MafiaGame, p: MafiaPlayer): MafiaRole? {
        listOf(MafiaRole.MATADOR, MafiaRole.ILLUSIONIST).forEach { r ->
            val rec = g.records[r.name] ?: return@forEach
            if (rec.void || p.id !in rec.targets) return@forEach
            val guardian = g.records[MafiaRole.GUARDIAN.name]
            val protected = guardian != null && !guardian.void && (p.id in guardian.targets || guardian.targets.any { g.player(it)?.role == r })
            if (!protected) return r
        }
        if (p.has("illusioned") && p.marks["illusionedNight"] == g.night - 1) return MafiaRole.ILLUSIONIST
        return MafiaGeneric.blockerOf(g, p)
    }

    private fun exhausted(g: MafiaGame, role: MafiaRole, p: MafiaPlayer): Boolean {
        val key = useKey(role) ?: return false
        return p.counter(key) <= 0
    }

    /** The counter a role spends per use, named after its option. */
    fun useKey(role: MafiaRole): String? = if (role.isGeneric) {
        if ((MafiaRoleRegistry.def(role)?.ability ?: MafiaAbility.NONE) != MafiaAbility.NONE) MafiaGenericRoles.USES else null
    } else when (role) {
        MafiaRole.DOCTOR, MafiaRole.DOCTOR_LECTOR -> "تعداد نجات کل"
        MafiaRole.PSYCHOLOGIST, MafiaRole.NATASHA -> "تعداد سکوت"
        MafiaRole.DETECTIVE, MafiaRole.CLEVER, MafiaRole.SPECIAL_DETECTIVE, MafiaRole.DIE_HARD -> "تعداد استعلام"
        MafiaRole.PROFESSIONAL, MafiaRole.SNIPER -> "تعداد تیر"
        MafiaRole.SALESMAN -> "تعداد قابلیت"
        MafiaRole.SALVATION_ANGEL, MafiaRole.CONSTANTINE -> "تعداد نجات"
        MafiaRole.SABA, MafiaRole.THIEF -> "تعداد نقش دزدی"
        MafiaRole.MASON, MafiaRole.OCEAN, MafiaRole.JUPITER, MafiaRole.SMITH, MafiaRole.FRAUD, MafiaRole.JIGSAW -> "تعداد انتخاب"
        MafiaRole.GUARD -> "تعداد مراقبت"
        MafiaRole.INVESTIGATOR -> "تعداد بازجویی"
        MafiaRole.COMMANDO -> "تعداد شلیک"
        MafiaRole.WRECKER -> "تعداد خرابکاری"
        MafiaRole.YAKUZA -> "تعداد یاکوزایی"
        MafiaRole.BOMBER -> "تعداد بمب گذاری"
        MafiaRole.PRIEST -> "تعداد دفعات انتخاب"
        MafiaRole.RUSSIAN_ROULETTE -> "تعداد رولت روسی"
        MafiaRole.ATTAR -> "تعداد زهر"
        MafiaRole.INTERROGATOR, MafiaRole.SLEEP_WALKER, MafiaRole.HACKER, MafiaRole.CITIZEN_KANE, MafiaRole.SPIDER,
        MafiaRole.VOODOO, MafiaRole.PABLO, MafiaRole.CAST_AWAY -> "once"
        else -> null
    }

    /** Remaining uses shown under a job (source app: «تعداد قابلیت های باقیمانده»). */
    fun remainingText(g: MafiaGame, role: MafiaRole): String? {
        val p = g.holders(role).firstOrNull() ?: return null
        val key = useKey(role) ?: return null
        val left = p.counter(key)
        val v = if (left >= 1000) "نامحدود" else PersianText.digits(left)
        val extra = if (role == MafiaRole.DOCTOR || role == MafiaRole.DOCTOR_LECTOR) "\nتعداد نجات خود : ${PersianText.digits(p.counter("تعداد نجات خود"))}" else ""
        return "تعداد قابلیت های باقیمانده : $v$extra"
    }

    // ------------------------------------------------------------------ doing a job

    /**
     * Stores a job's choice, spends its counters and returns what the narrator should see
     * (inquiry answer, role card, count…), or an empty string when nothing is shown.
     */
    fun perform(g: MafiaGame, job: NightJob, mode: JobMode, targets: List<Int>, guess: MafiaRole?, number: Int, text: String): String {
        val decoy = isDecoy(g, job)
        val holder = job.holders.firstOrNull()?.let { g.player(it) }
        val blackNight = g.extra["blackNight"] == g.night.toString() && job.side == MafiaSide.CITY
        val sleepwalk = g.records[MafiaRole.SLEEP_WALKER.name]?.let { it.choice.startsWith("امشب") && !it.void } == true &&
            (job.key == MAFIA_GROUP || (job.side == MafiaSide.MAFIA && job.role != null))
        val vertigo = holder?.has("vertigo") == true && holder.marks["vertigoNight"] == g.night
        val void = decoy || blackNight || sleepwalk || vertigo
        var result = ""
        if (!void && holder != null) {
            job.role?.let { r ->
                useKey(r)?.let { k -> if (k == "once") holder.counters[k] = 0 else if (holder.counter(k) < 1000) holder.counters[k] = holder.counter(k) - 1 }
                if ((r == MafiaRole.DOCTOR || r == MafiaRole.DOCTOR_LECTOR) && holder.id in targets) holder.counters["تعداد نجات خود"] = holder.counter("تعداد نجات خود") - 1
                if (r == MafiaRole.MATADOR || r == MafiaRole.ILLUSIONIST) targets.firstOrNull()?.let { g.extra["last_${r.name}"] = it.toString() }
            }
            result = immediateResult(g, job, mode, targets, guess, holder)
        }
        if (job.key == MAFIA_GROUP && !void) {
            when (mode.label) {
                "مرد قوی" -> g.aliveHolder(MafiaRole.STRONG_MAN)?.let { it.counters["تعداد انتخاب"] = (it.counter("تعداد انتخاب") - 1).coerceAtLeast(0) }
                "سم ساز" -> g.aliveHolder(MafiaRole.POISON_MAKER)?.counters?.set("poison", 0)
                "ناتو" -> g.aliveHolder(MafiaRole.NATO)?.let { it.counters["تعداد ناتویی"] = it.counter("تعداد ناتویی") - 1 }
                "ساول گودمن" -> g.aliveHolder(MafiaRole.GOOD_MAN)?.counters?.set("buy", 0)
                "سوداگری" -> g.aliveHolder(MafiaRole.GODFATHER)?.counters?.set("trade", 0)
                else -> MafiaGeneric.spendGroup(g, mode.label)
            }
            result = immediateResult(g, job, mode, targets, guess, holder)
        }
        if (job.key.startsWith("CARD_")) result = immediateResult(g, job, mode, targets, guess, holder)
        g.records[job.key] = NightRecord(job.key, targets, mode.label, guess, number, text, result, void)
        if (job.key.startsWith("CARD_")) g.deck.firstOrNull { cardKey(it.card) == job.key }?.pending = false
        return result
    }

    /** The role was woken but chose nobody: recorded as done, nothing spent. */
    fun skip(g: MafiaGame, job: NightJob) {
        g.records[job.key] = NightRecord(job.key, choice = "کاری انجام نداد", void = true)
        if (job.key.startsWith("CARD_")) g.deck.firstOrNull { cardKey(it.card) == job.key }?.pending = false
    }

    fun cancel(g: MafiaGame, job: NightJob) {
        val rec = g.records.remove(job.key) ?: return
        if (rec.void) return
        if (job.key == MAFIA_GROUP) { MafiaGeneric.refundGroup(g, rec.choice); return }
        val holder = job.holders.firstOrNull()?.let { g.player(it) } ?: return
        job.role?.let { r ->
            useKey(r)?.let { k -> if (k == "once") holder.counters[k] = 1 else if (holder.counter(k) < 1000) holder.counters[k] = holder.counter(k) + 1 }
            if ((r == MafiaRole.DOCTOR || r == MafiaRole.DOCTOR_LECTOR) && holder.id in rec.targets) holder.counters["تعداد نجات خود"] = holder.counter("تعداد نجات خود") + 1
        }
    }

    /** Inquiry-type answers, as the source app shows them right away. */
    private fun immediateResult(g: MafiaGame, job: NightJob, mode: JobMode, targets: List<Int>, guess: MafiaRole?, holder: MafiaPlayer?): String {
        val t = targets.firstOrNull()?.let { g.player(it) }
        if (job.role?.isGeneric == true) return MafiaGeneric.result(g, MafiaGeneric.abilityOf(job, mode), targets, guess, holder)
        return when (job.role) {
            MafiaRole.DETECTIVE -> if (t == null) "" else if (detectivePositive(g, t, holder)) "استعلام مثبت 👍 (مافیا)" else "استعلام منفی 👎"
            MafiaRole.SPECIAL_DETECTIVE -> if (t == null) "" else if (t.side == MafiaSide.INDEPENDENT) "استعلام مثبت 👍 (مستقل)" else "استعلام منفی 👎"
            MafiaRole.CLEVER -> if (mode.label == "نقش") {
                val owner = g.alive.firstOrNull { it.role == guess }
                if (owner == null) "این نقش در بازی نیست" else "صاحب نقش ${guess?.faName}: ${owner.name}"
            } else t?.let { "نقش ${it.name}: ${it.role?.faName}" } ?: ""
            MafiaRole.INVESTIGATOR -> if (t == null) "" else when {
                t.side != MafiaSide.CITY -> "بازجو فقط میتواند نقش های شهروندی را بازجویی کند\nبازیکن انتخابی جز شهروندان نیست"
                g.records[MafiaRole.GUARD.name]?.targets?.contains(t.id) == true -> "بازیکن انتخابی توسط مراقب، مراقبت شده است"
                else -> "نقش: ${t.role?.faName}\n" + actionOf(g, t)
            }
            MafiaRole.NOSTRADAMUS -> {
                val count = targets.mapNotNull { g.player(it) }.count { it.side == MafiaSide.MAFIA && it.role != MafiaRole.GODFATHER }
                "تعداد مافیا های انتخابی ${PersianText.digits(count)} نفر است\nنوستراداموس با شهر بازی میکند یا با مافیا"
            }
            MafiaRole.SABA -> t?.let { "صبا نقش ${it.name} را گرفت: ${it.role?.faName}" } ?: ""
            MafiaRole.CAST_AWAY -> if (t != null && t.side == MafiaSide.MAFIA && t.role != MafiaRole.THIEF) "شخص انتخابی مافیا است و دور افتاده با مافیا بیدار خواهد شد" else "استعلام دور افتاده نادرست است"
            MafiaRole.MASON -> if (t == null) "" else if (t.side == MafiaSide.CITY || t.role == MafiaRole.SPY) "دست بازیکن را لمس کنید او جز گروه فراماسون خواهد شد" else "بازیکن انتخابی شهروند نیست؛ گروه فراماسون امشب از بازی خارج می‌شود"
            MafiaRole.OCEAN -> if (t == null) "" else if (t.side == MafiaSide.CITY) "دست بازیکن را لمس کنید تا بیدار شود\nتمامی یاران اوشن از خواب بیدار شوند و با هم مشورت کنند" else "بازیکن انتخابی شهروند نیست؛ اوشن امشب از بازی خارج می‌شود"
            MafiaRole.REPORTER -> if (t != null && g.extra["boughtTonight"] == t.id.toString()) "استعلام مثبت 👍" else "استعلام منفی 👎"
            MafiaRole.HACKER -> {
                val list = targets.mapNotNull { g.player(it) }
                val gfIntact = list.any { it.role == MafiaRole.GODFATHER && !it.has("taken") }
                var count = list.count { it.side == MafiaSide.MAFIA && it.role != MafiaRole.GODFATHER } + list.count { it.role == MafiaRole.GODFATHER && it.has("taken") }
                if (gfIntact && count == 2) count = 3
                when (count) { 1 -> "لیست هکر خطرناک است"; 3 -> "انفجار لیست هکر رخ داده است"; else -> "لیست هکر غیر خطرناک است" }
            }
            null -> when (mode.label) {
                "حس ششم" -> if (t != null && guess != null && t.role == guess) "حدس درست بود؛ بازیکن سلاخی می‌شود" else "حدس اشتباه بود"
                "ناتو" -> if (t != null && guess != null && t.role == guess) "حدس درست بود؛ بازیکن امشب کشته می‌شود" else "حدس ناتو اشتباه بود"
                "ساول گودمن" -> if (t != null && convertible(g, t)) "خرید موفقیت آمیز بود\nبازیکن انتخابی نقش زیر را عهده دار خواهد بود: مافیا ساده" else "خرید موفقیت آمیز نبود\nدست بازیکن به صورت صوری لمس شود"
                else -> MafiaGeneric.groupAbility(g, mode.label)?.let { MafiaGeneric.result(g, it.second, targets, guess, holder) } ?: ""
            }
            MafiaRole.SHERLOCK_HOLMES -> if (t != null && guess != null && t.role == guess) "حدس درست بود؛ بازیکن سلاخی می‌شود و شرلوک نقش او را می‌گیرد" else "عملیات موفقیت آمیز نبود"
            MafiaRole.CHURCHILL -> if (mode.label == "سلاخی") (if (t != null && guess != null && t.role == guess) "حدس درست بود؛ بازیکن سلاخی می‌شود" else "حدس اشتباه بود") else ""
            MafiaRole.YAKUZA -> if (t != null && convertible(g, t) && g.records[MafiaRole.JUPITER.name]?.targets?.contains(t.id) != true) "عملیات یاکوزا با موفقیت انجام شد\nدست بازیکن لمس شود\nاو مافیا ساده خواهد بود" else "عملیات یاکوزا موفقت آمیز نبود\nلمس دست بازیکن به صورت صوری انجام خواهد شد"
            else -> ""
        }
    }

    private fun convertible(g: MafiaGame, t: MafiaPlayer): Boolean =
        (t.role == MafiaRole.CITIZEN || t.role == MafiaRole.SUSPECT || t.role == MafiaRole.HERO || (t.role == MafiaRole.DIE_HARD && (t.armor == 0 || t.has("taken")))) &&
            !t.has("chaplin") && !t.has("smith")

    /** Detective answer with the source app's exceptions (Godfather, Suspect, Charlatan, Fraud). */
    fun detectivePositive(g: MafiaGame, t: MafiaPlayer, detective: MafiaPlayer?): Boolean {
        val fraud = g.records[MafiaRole.FRAUD.name]
        if (fraud != null && !fraud.void && detective != null && detective.id in fraud.targets) return false
        val witcher = g.records[MafiaRole.WITCHER.name]
        val subject = if (witcher != null && !witcher.void && detective != null && detective.id in witcher.targets) detective else t
        val fixed = MafiaGeneric.look(g, subject)
        var positive = when {
            fixed != null -> fixed
            subject.role == MafiaRole.SUSPECT -> true
            subject.role == MafiaRole.GODFATHER && !subject.has("taken") && subject.counter("تعداد استعلام منفی") > 0 -> {
                if (subject.counter("تعداد استعلام منفی") < 1000) subject.counters["تعداد استعلام منفی"] = subject.counter("تعداد استعلام منفی") - 1
                false
            }
            else -> subject.side == MafiaSide.MAFIA
        }
        val charlatan = g.records[MafiaRole.CHARLATAN.name]
        if (charlatan != null && !charlatan.void && subject.id in charlatan.targets) positive = !positive
        return positive
    }

    /** What a player did tonight, for the Investigator and «شب مثل روز روشن». */
    fun actionOf(g: MafiaGame, p: MafiaPlayer): String {
        val rec = p.role?.let { g.records[it.name] } ?: return "هیچ کاری انجام نداده است"
        if (rec.void) return "هیچ کاری انجام نداده است"
        val names = rec.targets.mapNotNull { g.player(it)?.name }.joinToString("، ")
        return when (p.role) {
            MafiaRole.DOCTOR -> "$names را نجات داده است"
            MafiaRole.DETECTIVE, MafiaRole.SPECIAL_DETECTIVE -> "استعلام $names را گرفته است"
            MafiaRole.PROFESSIONAL, MafiaRole.SNIPER, MafiaRole.COMMANDO -> "به $names شلیک کرده است"
            else -> if (names.isNotBlank()) "$names را انتخاب کرده است" else (rec.choice.ifBlank { "کاری انجام داده است" })
        }
    }

    // ------------------------------------------------------------------ resolution

    class Outcome(val dead: List<Int>, val report: List<String>, val revived: List<Int>)

    /** Who would die if the night ended now (for the Salvation Angel). */
    fun preview(g: MafiaGame): Outcome = resolve(g, apply = false)

    /**
     * Resolves the night in the source app's order: effects, then shots against saves, armour and immunities,
     * then deaths and the morning report. With [apply] false nothing changes.
     */
    fun resolve(g: MafiaGame, apply: Boolean = true): Outcome {
        val shots = HashMap<Int, MutableList<String>>()
        val kills = HashMap<Int, String>()
        val saves = HashSet<Int>()
        val report = ArrayList<String>()
        fun rec(r: MafiaRole) = g.records[r.name]?.takeIf { !it.void }
        fun shoot(id: Int, by: String) { shots.getOrPut(id) { ArrayList() } += by }
        fun kill(id: Int, by: String) { kills[id] = by }
        val n = g.night

        // Thief: the first target's action lands on the second target.
        val thief = rec(MafiaRole.THIEF)
        val redirect = HashMap<Int, Int>()
        if (thief != null && thief.targets.size == 2) {
            val a = g.player(thief.targets[0]); val b = thief.targets[1]
            if (a != null) when (a.role) {
                MafiaRole.DETECTIVE, MafiaRole.CLEVER, MafiaRole.INVESTIGATOR, MafiaRole.SPECIAL_DETECTIVE, MafiaRole.RESEARCHER ->
                    g.aliveHolder(MafiaRole.THIEF)?.let { kill(it.id, "arrest") }
                else -> redirect[a.id] = b
            }
        }
        fun targetOf(role: MafiaRole, t: Int): Int {
            val holder = g.holders(role).firstOrNull()?.id ?: return t
            return redirect[holder] ?: t
        }

        // Last-move card jobs.
        g.records[cardKey(MafiaLastCard.FINAL_SHOT)]?.targets?.firstOrNull()?.let { shoot(it, "final") }
        g.records[cardKey(MafiaLastCard.LAST_VENOM)]?.targets?.firstOrNull()?.let { kill(it, "venom") }

        // Sleepwalker cancels the mafia night and leaves.
        rec(MafiaRole.SLEEP_WALKER)?.takeIf { it.choice.startsWith("امشب") }?.let { g.aliveHolder(MafiaRole.SLEEP_WALKER)?.let { p -> kill(p.id, "sleepwalk") } }

        // Mafia group.
        g.records[MAFIA_GROUP]?.takeIf { !it.void }?.let { r ->
            val t = r.targets.firstOrNull()
            when (r.choice) {
                "شلیک شب" -> r.targets.forEach { shoot(redirect[shooter(g)?.id ?: -1] ?: it, "mafia") }
                "حس ششم" -> if (t != null) {
                    val p = g.player(t)
                    if (p != null && p.role == r.guess) kill(t, "execute")
                    else if (r.guess == MafiaRole.JACK_SPARROW) g.aliveHolder(MafiaRole.GODFATHER)?.let { kill(it.id, "jack") }
                }
                "مرد قوی" -> if (t != null) kill(t, "strong")
                "ناتو" -> if (t != null && g.player(t)?.role == r.guess) kill(t, "nato")
                    else if (r.guess == MafiaRole.JACK_SPARROW) g.aliveHolder(MafiaRole.NATO)?.let { kill(it.id, "jack") }
                "سم ساز" -> if (t != null && apply) g.player(t)?.marks?.set("poisonDeath", n + g.optionInt(MafiaRole.POISON_MAKER, "زمان اثر سم (روز)", 2))
                "ساول گودمن" -> if (t != null && apply) g.player(t)?.takeIf { convertible(g, it) }?.let { it.role = MafiaRole.MAFIA; g.extra["boughtTonight"] = it.id.toString() }
                "سوداگری" -> if (r.targets.size == 2) {
                    kill(r.targets[0], "trade")
                    if (apply) g.player(r.targets[1])?.takeIf { convertible(g, it) && rec(MafiaRole.JUPITER)?.targets?.contains(it.id) != true }?.let { it.role = MafiaRole.MAFIA }
                }
            }
        }

        // Citizen and independent shooters.
        rec(MafiaRole.PROFESSIONAL)?.targets?.firstOrNull()?.let { t0 ->
            val t = targetOf(MafiaRole.PROFESSIONAL, t0)
            if (g.player(t)?.side == MafiaSide.CITY) g.aliveHolder(MafiaRole.PROFESSIONAL)?.let { kill(it.id, "pro") } else shoot(t, "pro")
        }
        rec(MafiaRole.SNIPER)?.targets?.firstOrNull()?.let { t0 ->
            val t = targetOf(MafiaRole.SNIPER, t0)
            val cmd = rec(MafiaRole.COMMANDER)
            val commanderAlive = g.aliveHolder(MafiaRole.COMMANDER) != null
            when {
                cmd?.choice == "لغو شلیک" -> { if (apply) g.aliveHolder(MafiaRole.SNIPER)?.let { it.counters["تعداد تیر"] = it.counter("تعداد تیر") + 1 }; Unit }
                !commanderAlive && g.player(t)?.side == MafiaSide.CITY -> g.aliveHolder(MafiaRole.SNIPER)?.let { kill(it.id, "sniper") }
                else -> shoot(t, "sniper")
            }
        }
        rec(MafiaRole.COMMANDER)?.takeIf { it.choice == "شلیک" }?.targets?.firstOrNull()?.let { t ->
            if (g.player(t)?.side == MafiaSide.CITY) g.aliveHolder(MafiaRole.COMMANDER)?.let { kill(it.id, "commander") } else shoot(t, "commander")
        }
        rec(MafiaRole.COMMANDO)?.targets?.firstOrNull()?.let { t0 ->
            val t = targetOf(MafiaRole.COMMANDO, t0)
            val p = g.player(t)
            when (p?.side) {
                MafiaSide.CITY -> g.aliveHolder(MafiaRole.COMMANDO)?.let { kill(it.id, "commando") }
                MafiaSide.MAFIA -> { if (p.role != MafiaRole.GODFATHER) shoot(t, "commando"); Unit }
                else -> Unit
            }
        }
        rec(MafiaRole.KILLER)?.targets?.firstOrNull()?.let { kill(targetOf(MafiaRole.KILLER, it), "killer") }
        rec(MafiaRole.FELON)?.targets?.firstOrNull()?.let { shoot(targetOf(MafiaRole.FELON, it), "felon") }
        rec(MafiaRole.ZODIAC)?.takeIf { it.choice == "شلیک" }?.targets?.firstOrNull()?.let { t ->
            if (g.player(t)?.role == MafiaRole.PROTECTOR) g.aliveHolder(MafiaRole.ZODIAC)?.let { kill(it.id, "zodiac") } else shoot(t, "zodiac")
        }
        rec(MafiaRole.CHURCHILL)?.let { r ->
            val t = r.targets.firstOrNull() ?: return@let
            when (r.choice) {
                "شلیک" -> shoot(t, "churchill")
                "سلاخی" -> if (g.player(t)?.role == r.guess) kill(t, "execute")
                "شرط خروج" -> if (apply) g.extra["churchillMark"] = t.toString()
            }
        }
        rec(MafiaRole.SHERLOCK_HOLMES)?.let { r ->
            val t = r.targets.firstOrNull()?.let { g.player(it) } ?: return@let
            if (t.role == r.guess) {
                kill(t.id, "execute")
                if (apply) g.aliveHolder(MafiaRole.SHERLOCK_HOLMES)?.let { s -> s.role = t.role; t.role = MafiaRole.SHERLOCK_HOLMES }
            }
        }
        rec(MafiaRole.YAKUZA)?.let { r ->
            g.aliveHolder(MafiaRole.YAKUZA)?.let { shoot(it.id, "yakuza") }
            if (apply) r.targets.firstOrNull()?.let { g.player(it) }?.takeIf { convertible(g, it) && rec(MafiaRole.JUPITER)?.targets?.contains(it.id) != true }?.let { it.role = MafiaRole.MAFIA }
        }
        rec(MafiaRole.HACKER)?.takeIf { it.result.startsWith("انفجار") }?.targets?.forEach { kill(it, "hacker") }
        rec(MafiaRole.MASON)?.targets?.firstOrNull()?.let { g.player(it) }?.let { t ->
            if (t.side != MafiaSide.CITY && t.role != MafiaRole.SPY) {
                g.alive.filter { it.role == MafiaRole.MASON || it.has("mason") }.forEach { kill(it.id, "mason") }
                report += "گروه فراماسون از بازی خارج شدند"
            } else if (apply) t.flags += "mason"
        }
        rec(MafiaRole.OCEAN)?.targets?.firstOrNull()?.let { g.player(it) }?.let { t ->
            if (t.side != MafiaSide.CITY) { g.aliveHolder(MafiaRole.OCEAN)?.let { kill(it.id, "ocean") } } else if (apply) { t.flags += "ocean" }; Unit
        }
        rec(MafiaRole.SPECIAL_DETECTIVE)?.let { r ->
            val sd = g.aliveHolder(MafiaRole.SPECIAL_DETECTIVE) ?: return@let
            if (r.result.contains("منفی")) {
                val streak = (sd.marks["negStreak"] ?: 0) + 1
                if (apply) sd.marks["negStreak"] = streak
                if (streak >= g.optionInt(MafiaRole.SPECIAL_DETECTIVE, "تعداد استعلام منفی", 3)) kill(sd.id, "special")
            } else if (apply) sd.marks["negStreak"] = 0
        }
        g.players.filter { it.alive && it.marks["poisonDeath"] == n }.forEach { kill(it.id, "poison") }
        g.players.filter { it.alive && it.marks["kaneDeath"] == n }.forEach { kill(it.id, "kane") }

        // Data-defined roles (scenario roles and the narrator's own roles).
        val generic = MafiaGeneric.effects(g, apply)
        generic.shots.forEach { shoot(it, "custom") }
        generic.kills.forEach { (id, by) -> kill(id, by) }
        saves += generic.saves
        report += generic.report

        // Saves, armour and protections.
        rec(MafiaRole.DOCTOR)?.targets?.forEach { saves += targetOf(MafiaRole.DOCTOR, it) }
        rec(MafiaRole.DOCTOR_LECTOR)?.targets?.forEach { saves += targetOf(MafiaRole.DOCTOR_LECTOR, it) }
        if (apply) rec(MafiaRole.SMITH)?.targets?.firstOrNull()?.let { g.player(targetOf(MafiaRole.SMITH, it))?.flags?.add("smith") }

        val dead = LinkedHashSet<Int>()
        val armorUse = HashMap<Int, Int>()
        g.alive.forEach { p ->
            val list = shots[p.id]?.toMutableList() ?: ArrayList()
            var killed = kills[p.id]
            val role = p.role
            if (list.isNotEmpty()) {
                val immune = when (role) {
                    MafiaRole.HERO, MafiaRole.JACK_SPARROW, MafiaRole.JIGSAW, MafiaRole.ZODIAC -> true
                    MafiaRole.NOSTRADAMUS -> !p.has("mortal")
                    MafiaRole.SHERLOCK_HOLMES -> n <= 2
                    MafiaRole.FELON -> n % 2 == 1
                    MafiaRole.INHERITOR -> p.marks["benefactor"]?.let { g.player(it) }?.let { it.alive && it.side == MafiaSide.CITY } == true
                    else -> false
                } && !p.has("taken") || MafiaGeneric.nightImmune(p)
                if (immune) list.clear()
                if (p.id in saves) list.clear()
                if (list.isNotEmpty() && p.armor > 0 && !p.has("taken")) {
                    val used = minOf(p.armor, list.size)
                    armorUse[p.id] = used
                    repeat(used) { list.removeAt(0) }
                }
                if (list.isNotEmpty() && p.has("smith")) { list.removeAt(0); if (apply) p.flags -= "smith" }
                if (list.isNotEmpty() && role == MafiaRole.GODFATHER) {
                    val bg = g.aliveHolder(MafiaRole.BODYGUARD)
                    if (bg != null && !bg.has("taken")) { list.clear(); dead += bg.id }
                }
            }
            if (killed != null && killed != "nato" && killed != "venom" && killed != "poison" && p.has("smith")) {
                killed = null
                if (apply) p.flags -= "smith"
            }
            if (list.isNotEmpty() || killed != null) dead += p.id
        }
        // Jack Sparrow leaves with his talisman; the researcher takes his linked mafia along.
        g.aliveHolder(MafiaRole.JACK_SPARROW)?.let { jack -> g.extra["talisman"]?.toIntOrNull()?.let { if (it in dead) dead += jack.id } }
        g.aliveHolder(MafiaRole.RESEARCHER)?.let { rs -> if (rs.id in dead) g.extra["researcherLink"]?.toIntOrNull()?.let { dead += it } }

        // Revivals: Salvation Angel (tonight's dead) and Constantine (earlier dead).
        val revived = ArrayList<Int>()
        rec(MafiaRole.SALVATION_ANGEL)?.targets?.firstOrNull()?.let { if (dead.remove(it)) revived += it }
        rec(MafiaRole.CONSTANTINE)?.targets?.firstOrNull()?.let { revived += it }
        generic.revives.forEach { id -> if (dead.remove(id) || g.player(id)?.alive == false) revived += id }

        if (!apply) return Outcome(dead.toList(), report, revived)

        // Persistent effects for tomorrow.
        armorUse.forEach { (id, used) -> g.player(id)?.let { it.armor -= used } }
        g.players.forEach { it.flags.removeAll(setOf("silenced", "natasha", "extraTime", "handcuff")) }
        rec(MafiaRole.PSYCHOLOGIST)?.targets?.firstOrNull()?.let { g.player(targetOf(MafiaRole.PSYCHOLOGIST, it))?.flags?.add("silenced") }
        rec(MafiaRole.NATASHA)?.targets?.forEach { g.player(it)?.flags?.add("natasha") }
        rec(MafiaRole.PRIEST)?.targets?.forEach { id -> g.player(id)?.let { it.flags += "extraTime"; it.flags -= "natasha" } }
        rec(MafiaRole.SALESMAN)?.targets?.firstOrNull()?.let { g.player(it) }?.let { t ->
            t.flags += "taken"
            if (t.role == MafiaRole.BODYGUARD || t.role == MafiaRole.DIE_HARD) t.armor = (t.armor - 1).coerceAtLeast(0)
        }
        rec(MafiaRole.ILLUSIONIST)?.targets?.firstOrNull()?.let { g.player(it) }?.let { it.flags += "illusioned"; it.marks["illusionedNight"] = n }
        rec(MafiaRole.CHAPLIN)?.targets?.firstOrNull()?.let { g.player(it)?.flags?.add("chaplin") }
        rec(MafiaRole.JACK_SPARROW)?.targets?.firstOrNull()?.let { g.extra["talisman"] = it.toString() }
        rec(MafiaRole.SPIDER)?.targets?.forEach { g.player(it)?.flags?.add("web") }
        rec(MafiaRole.RESEARCHER)?.targets?.firstOrNull()?.let { g.player(it) }?.let { t ->
            if (t.side == MafiaSide.MAFIA && t.role != MafiaRole.GODFATHER) g.extra["researcherLink"] = t.id.toString()
        }
        rec(MafiaRole.INHERITOR)?.targets?.firstOrNull()?.let { t -> g.aliveHolder(MafiaRole.INHERITOR)?.marks?.set("benefactor", t) }
        rec(MafiaRole.CORONA)?.targets?.forEach { g.player(it)?.marks?.set("infected", n) }
        rec(MafiaRole.NOSTRADAMUS)?.let { r -> if (r.text.isNotBlank()) g.extra["nostradamusSide"] = r.text }
        rec(MafiaRole.CAST_AWAY)?.let { r -> if (r.result.startsWith("شخص انتخابی مافیا")) g.aliveHolder(MafiaRole.CAST_AWAY)?.let { it.flags += "joined"; g.extra["extraShots"] = "1" } }
        rec(MafiaRole.PABLO)?.targets?.firstOrNull()?.let { g.player(it) }?.let { t ->
            g.extra["pabloSwapped"] = "1"
            val gf = g.holders(MafiaRole.GODFATHER).firstOrNull()
            gf?.role = t.role
            t.role = MafiaRole.GODFATHER
        }
        rec(MafiaRole.CITIZEN_KANE)?.targets?.firstOrNull()?.let { g.player(it) }?.let { t ->
            if (t.side == MafiaSide.MAFIA) {
                report += "همشهری کین استعلام مافیایی ${t.name} را گرفت"
                g.aliveHolder(MafiaRole.CITIZEN_KANE)?.marks?.set("kaneDeath", n + 1)
            }
        }
        rec(MafiaRole.BOMBER)?.let { r -> r.targets.firstOrNull()?.let { t -> g.extra["bombOn"] = t.toString(); g.extra["bombCode"] = r.number.toString(); report += "بمب گذار یک بمب به ${g.player(t)?.name} داد" } }
        rec(MafiaRole.VOODOO)?.let { r -> if (r.text.isNotBlank()) {
            val saint = rec(MafiaRole.SAINT)
            val cancelled = saint != null && (r.targets.any { it in saint.targets } || r.targets.any { g.player(it)?.role == MafiaRole.SAINT })
            if (!cancelled) { g.extra["voodooWord"] = r.text; g.extra["voodooOn"] = r.targets.firstOrNull()?.toString() ?: ""; report += "افسونگر کلمه --${r.text}-- را ممنوعه اعلام کرد" }
        } }
        rec(MafiaRole.ZODIAC)?.takeIf { it.text.isNotBlank() }?.let { g.extra["zodiacLetter"] = it.text; report += "زودیاک یک نامه نوشته است" }
        rec(MafiaRole.ATTAR)?.targets?.firstOrNull()?.let { g.player(it)?.marks?.set("attarPoison", n); report += "زهر توسط عطار به بازی وارد شد" }
        g.records[ATTAR_ANTIDOTE]?.let { r -> if (r.choice == "پادزهر نمی‌دهد") g.players.firstOrNull { it.marks["attarPoison"] == n - 1 }?.let { dead += it.id } }
        rec(MafiaRole.GUNMAN)?.let { r ->
            val gunman = g.aliveHolder(MafiaRole.GUNMAN)
            r.targets.forEachIndexed { i, id ->
                val war = r.text.split(",").getOrNull(i) == "تفنگ جنگی"
                g.player(id)?.marks?.set("gun", if (war) 2 else 1)
                val key = if (war) "تعداد تفنگ جنگی" else "تعداد تفنگ مشقی"
                gunman?.let { gm -> if (gm.counter(key) < 1000) gm.counters[key] = (gm.counter(key) - 1).coerceAtLeast(0) }
            }
        }
        rec(MafiaRole.WRECKER)?.targets?.forEach { g.player(it)?.flags?.add("sabotaged") }
        rec(MafiaRole.RUSSIAN_ROULETTE)?.let { r -> r.targets.firstOrNull()?.let { g.extra["rouletteOn"] = it.toString(); g.extra["rouletteNumber"] = r.number.toString() } }
        rec(MafiaRole.JIGSAW)?.let { g.extra["jigsaw"] = it.targets.joinToString(",") }
        rec(MafiaRole.INTERROGATOR)?.let { g.extra["interrogate"] = it.targets.joinToString(",") }
        rec(MafiaRole.DIE_HARD)?.let { r -> if (r.choice.startsWith("استعلام می")) { g.extra["dieHardQuery"] = (n + 1).toString(); report += "جان سخت استعلام گرفت" } else report += "جان سخت استعلام نگرفت" }
        g.records[cardKey(MafiaLastCard.VERTIGO)]?.targets?.firstOrNull()?.let { g.player(it)?.let { p -> p.flags += "vertigo"; p.marks["vertigoNight"] = n + 1 } }
        g.records[cardKey(MafiaLastCard.CARD_EXCHANGE)]?.targets?.takeIf { it.size == 2 }?.let { (a, b) ->
            val pa = g.player(a); val pb = g.player(b)
            if (pa != null && pb != null) { val r = pa.role; pa.role = pb.role; pb.role = r }
        }
        g.records[cardKey(MafiaLastCard.FACE_OFF)]?.let { r ->
            val card = g.deck.firstOrNull { it.card == MafiaLastCard.FACE_OFF }
            val holder = card?.holder?.let { g.player(it) }
            val t = r.targets.firstOrNull()?.let { g.player(it) }
            if (holder != null && t != null && t.role != MafiaRole.JACK_SPARROW) { val x = holder.role; holder.role = t.role; t.role = x }
        }
        // Psychologist / Natasha / Priest lines of the source report.
        rec(MafiaRole.PSYCHOLOGIST)?.targets?.firstOrNull()?.let { report += "روانپزشک به ${g.player(it)?.name} طول درمان داد" }
        if (g.inGame(MafiaRole.NATASHA)) report += rec(MafiaRole.NATASHA)?.targets?.mapNotNull { g.player(it)?.name }?.joinToString("، ")?.let { "ناتاشا $it را ساکت کرد" } ?: "ناتاشا کسی را ساکت نکرد"
        rec(MafiaRole.SALESMAN)?.targets?.firstOrNull()?.let { report += "فروشنده توانایی ${g.player(it)?.name} را گرفت" }

        // Deaths.
        dead.forEach { id ->
            g.player(id)?.let { p ->
                p.alive = false
                p.outNight = n
                p.deathType = if (kills[id] == "execute") MafiaDeath.EXECUTE else if (kills[id] == "poison" || kills[id] == "venom") MafiaDeath.POISON else MafiaDeath.NIGHT
                if (kills[id] == "execute" || kills[id] == "sleepwalk") p.flags += "noReturn"
                if (kills[id] == "execute") report += " ${p.name}سلاخی شد"
                // Guns return to the gunman's stock.
                p.marks.remove("gun")?.let { kind ->
                    val key = if (kind == 2) "تعداد تفنگ جنگی" else "تعداد تفنگ مشقی"
                    g.aliveHolder(MafiaRole.GUNMAN)?.let { gm -> if (gm.counter(key) < 1000) gm.counters[key] = gm.counter(key) + 1 }
                }
            }
        }
        revived.forEach { id -> g.player(id)?.let { p -> p.alive = true; p.deathType = MafiaDeath.NONE; p.outNight = -1 } }
        val deadCount = dead.size
        report.add(0, when {
            deadCount > 1 -> "دیشب  ${PersianText.digits(deadCount)} نفر کشته شدند"
            deadCount == 1 -> "دیشب  ${PersianText.digits(1)} نفر کشته شد"
            else -> "دیشب کشته ای نداشتیم"
        })
        return Outcome(dead.toList(), report, revived)
    }

    /** Random seat-to-role assignment that keeps manual picks (source setAllRoles). */
    fun distribute(g: MafiaGame, random: Random = Random.Default) {
        if (g.players.all { it.role != null }) g.players.forEach { it.role = null; it.seen = false }
        val pool = ArrayList<MafiaRole>()
        g.roleCounts.forEach { (r, c) -> repeat(c) { pool += r } }
        g.players.mapNotNull { it.role }.forEach { pool.remove(it) }
        g.players.filter { it.role == null }.forEach { p ->
            if (pool.isEmpty()) return@forEach
            p.role = pool.removeAt(random.nextInt(pool.size))
        }
    }
}
