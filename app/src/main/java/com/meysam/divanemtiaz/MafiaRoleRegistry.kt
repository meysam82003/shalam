package com.meysam.divanemtiaz

import org.json.JSONObject

/** What a role does when it wakes at night (for roles whose rules are defined as data). */
enum class MafiaAbility(val title: String, val hint: String) {
    NONE("بدون کار شب", "در شب بیدار نمی‌شود یا کاری ندارد"),
    RECORD("انتخاب و ثبت برای راوی", "بازیکن(ها) را انتخاب می‌کند؛ راوی اثرش را طبق قانون نقش اعمال می‌کند"),
    SHOT("شلیک", "به هدف شلیک می‌کند؛ نجات دکتر و زره جلوی آن را می‌گیرد"),
    KILL("کشتن قطعی", "هدف بدون هیچ نجاتی از بازی خارج می‌شود"),
    SAVE("نجات", "هدف را از شلیک امشب نجات می‌دهد"),
    INQUIRY("استعلام", "مافیا بودن یا نبودن هدف را می‌فهمد"),
    COMPARE("مقایسهٔ دو نفر", "می‌فهمد دو بازیکن هم‌ساید هستند یا نه"),
    SILENCE("ساکت کردن", "هدف فردا حق صحبت ندارد"),
    BLOCK("مسدود کردن", "توانایی هدف را امشب می‌گیرد"),
    GUESS_KILL("حدس نقش و حذف", "نقش هدف را حدس می‌زند؛ حدس درست = حذف (سلاخی)"),
    CONVERT("خرید / تبدیل", "شهروند ساده یا مظنون را به مافیا تبدیل می‌کند"),
    REVIVE("بازگرداندن", "یک بازیکن خارج‌شده را به بازی برمی‌گرداند"),
    ARMOR("زره دادن", "به هدف یک زره می‌دهد که یک شلیک یا یک خروج را دفع می‌کند"),
    FRAME("پاپوش", "استعلام امشب هدف را مثبت (مافیا) نشان می‌دهد")
}

/** What a role may do in daylight. */
enum class MafiaDayAbility(val title: String) {
    NONE("بدون اقدام روز"),
    TAKE_ALONG("خروج همراه یک نفر"),
    DAY_SHOT("شلیک روز"),
    KEEP_FROM_VOTE("نگه داشتن بازیکن خارج‌شده با رأی"),
    REMOVE_FROM_DEFENCE("خارج کردن یک نفر از دفاعیه"),
    GUESS_EXIT("حدس نقش و حذف در روز")
}

enum class MafiaNights(val title: String) {
    EVERY("هر شب"), EVEN("شب‌های زوج"), ODD("شب‌های فرد"), INTRO("فقط شب معارفه"), FROM_SECOND("از شب دوم")
}

enum class InquiryLook(val title: String) { NORMAL("عادی"), NEGATIVE("همیشه منفی"), POSITIVE("همیشه مثبت") }

/**
 * A role's rules as data. Every field is optional: null keeps the role's own value, so the same class holds
 * the narrator's edits of an existing role and the full definition of a new one.
 */
data class MafiaRoleDef(
    val name: String? = null,
    val side: MafiaSide? = null,
    val glyph: MafiaGlyph? = null,
    val tagline: String? = null,
    val explain: String? = null,
    val intro: String? = null,
    val prompt: String? = null,
    val wakes: Boolean? = null,
    val wakeOrder: Int? = null,
    val introOrder: Int? = null,
    val ability: MafiaAbility? = null,
    val altAbility: MafiaAbility? = null,
    val min: Int? = null,
    val max: Int? = null,
    /** Times the night ability can be used; 0 = unlimited. */
    val uses: Int? = null,
    val nights: MafiaNights? = null,
    val selfTarget: Boolean? = null,
    val selfOnly: Boolean? = null,
    val dayAbility: MafiaDayAbility? = null,
    val dayUses: Int? = null,
    val nightImmune: Boolean? = null,
    val inquiry: InquiryLook? = null,
    val armor: Int? = null,
    /** Wakes with the mafia team (and acts through the team's turn). */
    val withMafia: Boolean? = null
) {
    /** Fields of [over] win over this definition's fields. */
    fun merge(over: MafiaRoleDef?): MafiaRoleDef = if (over == null) this else MafiaRoleDef(
        over.name ?: name, over.side ?: side, over.glyph ?: glyph, over.tagline ?: tagline, over.explain ?: explain,
        over.intro ?: intro, over.prompt ?: prompt, over.wakes ?: wakes, over.wakeOrder ?: wakeOrder, over.introOrder ?: introOrder,
        over.ability ?: ability, over.altAbility ?: altAbility, over.min ?: min, over.max ?: max, over.uses ?: uses,
        over.nights ?: nights, over.selfTarget ?: selfTarget, over.selfOnly ?: selfOnly, over.dayAbility ?: dayAbility,
        over.dayUses ?: dayUses, over.nightImmune ?: nightImmune, over.inquiry ?: inquiry, over.armor ?: armor, over.withMafia ?: withMafia
    )

    fun toJson(): JSONObject = JSONObject().apply {
        name?.let { put("name", it) }; side?.let { put("side", it.name) }; glyph?.let { put("glyph", it.name) }
        tagline?.let { put("tagline", it) }; explain?.let { put("explain", it) }; intro?.let { put("intro", it) }
        prompt?.let { put("prompt", it) }; wakes?.let { put("wakes", it) }; wakeOrder?.let { put("wake", it) }
        introOrder?.let { put("introOrder", it) }; ability?.let { put("ability", it.name) }; altAbility?.let { put("alt", it.name) }
        min?.let { put("min", it) }; max?.let { put("max", it) }; uses?.let { put("uses", it) }; nights?.let { put("nights", it.name) }
        selfTarget?.let { put("self", it) }; selfOnly?.let { put("selfOnly", it) }; dayAbility?.let { put("day", it.name) }
        dayUses?.let { put("dayUses", it) }; nightImmune?.let { put("immune", it) }; inquiry?.let { put("inquiry", it.name) }
        armor?.let { put("armor", it) }; withMafia?.let { put("withMafia", it) }
    }

    companion object {
        private inline fun <reified E : Enum<E>> JSONObject.enumOrNull(key: String): E? =
            if (has(key)) enumValues<E>().firstOrNull { it.name == optString(key) } else null

        private fun JSONObject.intOrNull(key: String) = if (has(key)) optInt(key) else null
        private fun JSONObject.boolOrNull(key: String) = if (has(key)) optBoolean(key) else null
        private fun JSONObject.strOrNull(key: String) = if (has(key)) optString(key) else null

        fun fromJson(o: JSONObject) = MafiaRoleDef(
            o.strOrNull("name"), o.enumOrNull<MafiaSide>("side"), o.enumOrNull<MafiaGlyph>("glyph"), o.strOrNull("tagline"),
            o.strOrNull("explain"), o.strOrNull("intro"), o.strOrNull("prompt"), o.boolOrNull("wakes"), o.intOrNull("wake"),
            o.intOrNull("introOrder"), o.enumOrNull<MafiaAbility>("ability"), o.enumOrNull<MafiaAbility>("alt"), o.intOrNull("min"),
            o.intOrNull("max"), o.intOrNull("uses"), o.enumOrNull<MafiaNights>("nights"), o.boolOrNull("self"), o.boolOrNull("selfOnly"),
            o.enumOrNull<MafiaDayAbility>("day"), o.intOrNull("dayUses"), o.boolOrNull("immune"), o.enumOrNull<InquiryLook>("inquiry"),
            o.intOrNull("armor"), o.boolOrNull("withMafia")
        )
    }
}

/**
 * Role rules in three layers: the built-in definitions of data-defined roles, the narrator's own edits and new
 * roles (saved), and the active scenario's names (e.g. «دکتر واتسون» for the doctor in پدرخوانده).
 */
object MafiaRoleRegistry {
    @Volatile var user: Map<MafiaRole, MafiaRoleDef> = emptyMap()
    @Volatile var scenarioNames: Map<MafiaRole, String> = emptyMap()
    @Volatile var scenarioWake: Map<MafiaRole, Int> = emptyMap()

    /** Effective rules of a role (built-in data merged with the narrator's edits). */
    fun def(r: MafiaRole): MafiaRoleDef? {
        val base = MafiaGenericRoles.builtin[r]
        val u = user[r]
        return when {
            base == null -> u
            else -> base.merge(u)
        }
    }

    /** An original (hand-coded) role whose night work the narrator replaced with his own rule. */
    fun overrides(r: MafiaRole): Boolean = !r.isDataRole && user[r]?.ability != null

    /** Night-record key of a data-defined job (kept apart from the hand-coded records of an overridden role). */
    fun jobKey(r: MafiaRole): String = if (r.isDataRole) r.name else "G_" + r.name

    fun roleOfKey(key: String): MafiaRole? = MafiaRole.values().firstOrNull { it.name == key.removePrefix("G_") }

    fun name(r: MafiaRole): String? = scenarioNames[r] ?: user[r]?.name ?: MafiaGenericRoles.builtin[r]?.name

    /** Roles that can be chosen: the original roles, the built-in data roles and defined custom slots. */
    fun available(): List<MafiaRole> = MafiaRole.values().filter { !it.isCustomSlot || user.containsKey(it) }

    fun nextFreeSlot(): MafiaRole? = MafiaRole.values().firstOrNull { it.isCustomSlot && !user.containsKey(it) }

    fun encode(m: Map<MafiaRole, MafiaRoleDef>): String = JSONObject().apply { m.forEach { (r, d) -> put(r.name, d.toJson()) } }.toString()

    fun decode(raw: String?): Map<MafiaRole, MafiaRoleDef> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            val out = LinkedHashMap<MafiaRole, MafiaRoleDef>()
            o.keys().forEach { k -> MafiaRole.values().firstOrNull { it.name == k }?.let { r -> out[r] = MafiaRoleDef.fromJson(o.getJSONObject(k)) } }
            out
        }.getOrDefault(emptyMap())
    }

    /** Rules of a role written out from its definition (for roles the narrator made without a text). */
    fun describe(r: MafiaRole): String {
        val d = def(r) ?: return "${r.faName}: نقش ساخت راوی؛ قوانین آن را در «نقش‌ها و قوانین» تعریف کنید."
        val lines = ArrayList<String>()
        lines += "ساید: ${r.side.title}"
        val a = d.ability ?: MafiaAbility.NONE
        if (d.wakes == false || a == MafiaAbility.NONE) lines += "در شب کاری ندارد"
        else {
            lines += "کار شب: ${a.title} — ${a.hint}" + ((d.nights ?: MafiaNights.EVERY).takeIf { it != MafiaNights.EVERY }?.let { " (${it.title})" } ?: "")
            d.altAbility?.takeIf { it != MafiaAbility.NONE }?.let { lines += "یا به جای آن: ${it.title} — ${it.hint}" }
            if ((d.uses ?: 0) > 0) lines += "تعداد استفاده: ${PersianText.digits(d.uses!!)}"
            if (d.withMafia == true) lines += "با تیم مافیا بیدار می‌شود"
        }
        d.dayAbility?.takeIf { it != MafiaDayAbility.NONE }?.let { lines += "اقدام روز: ${it.title}" }
        if (d.nightImmune == true) lines += "در شب نامیرا است"
        d.inquiry?.takeIf { it != InquiryLook.NORMAL }?.let { lines += "استعلام: ${it.title}" }
        if ((d.armor ?: 0) > 0) lines += "زره: ${PersianText.digits(d.armor!!)}"
        return lines.joinToString("\n")
    }

    /** Applies a scenario's role names and night order (or clears them with null). */
    fun activate(s: MafiaScenario?) {
        scenarioNames = s?.names ?: emptyMap()
        scenarioWake = s?.wake ?: emptyMap()
    }
}
