package com.meysam.divanemtiaz

enum class GameType(
    val key: String,
    val title: String,
    val subtitle: String,
    val minSides: Int,
    val maxSides: Int
) {
    SHALAM("shalam", "شلم", "داوری دو تیمی با قوانین کامل شلم", 2, 2),
    MENFI("menfi", "منفی", "ثبت پنهان عددها و نتیجهٔ سه‌حالته", 2, 2),
    HEZARTAII("hezartaii", "هزارتایی", "رقابت انفرادی و رتبه‌بندی تا هزار", 2, 6);

    val isTeamGame: Boolean get() = this != HEZARTAII

    companion object {
        fun fromKey(key: String?): GameType = values().firstOrNull { it.key == key } ?: SHALAM
    }
}

data class Side(val name: String, val avatar: Int)

enum class RoundKind(val key: String) {
    SHALAM_HAND("shalam_hand"),
    SHALAM_SHELEM("shalam_shelem"),
    SHALAM_DOUBLE_SHELEM("shalam_double_shelem"),
    SHALAM_PASS("shalam_pass"),
    MENFI_HAND("menfi_hand"),
    HEZAR_ROUND("hezar_round"),
    PENALTY("penalty"),
    ADJUST("adjust"),
    FIXED("fixed");

    val isShalamContract: Boolean
        get() = this == SHALAM_HAND || this == SHALAM_SHELEM || this == SHALAM_DOUBLE_SHELEM

    companion object {
        fun fromKey(key: String?): RoundKind = values().firstOrNull { it.key == key } ?: FIXED
    }
}

object Suit {
    const val NONE = 0
    const val SPADE = 1
    const val HEART = 2
    const val DIAMOND = 3
    const val CLUB = 4
    val all = listOf(SPADE, HEART, DIAMOND, CLUB)

    fun title(suit: Int): String = when (suit) {
        SPADE -> "پیک"
        HEART -> "دل"
        DIAMOND -> "خشت"
        CLUB -> "گشنیز"
        else -> ""
    }
}

/** Double choice of a failed Shalam contract: 0 = automatic from settings, 1 = positive, 2 = negative. */
object DoubleChoice {
    const val AUTO = 0
    const val POSITIVE = 1
    const val NEGATIVE = 2
}

/**
 * One recorded hand/round. [scores] always holds the final points of every side; the other
 * fields keep the raw input so a hand can be edited and recalculated.
 */
data class Round(
    val kind: RoundKind,
    val scores: List<Int>,
    val note: String = "",
    val contractTeam: Int = -1,
    val bid: Int = 0,
    val taken: Int = 0,
    val suit: Int = Suit.NONE,
    val double: Int = DoubleChoice.AUTO,
    val numbers: List<Int> = emptyList(),
    val outcome: Int = -1,
    val raw: List<Int> = emptyList()
) {
    fun score(side: Int): Int = scores.getOrElse(side) { 0 }
}

class GameSession(
    val id: Long,
    val game: GameType,
    sides: List<Side>,
    rounds: List<Round> = emptyList(),
    var rules: GameRules,
    var finished: Boolean = false,
    var updatedAt: Long = id,
    var elapsedMs: Long = 0L,
    var endedAt: Long? = null,
    var label: String = ""
) {
    val sides: MutableList<Side> = sides.toMutableList()
    val rounds: MutableList<Round> = rounds.toMutableList()

    fun copy(): GameSession = GameSession(
        id, game, sides.toList(), rounds.toList(), rules, finished, updatedAt, elapsedMs, endedAt, label
    )
}

data class ShalamModeRules(
    val maxPoints: Int = 165,
    val collectAll: Int = CollectAll.POINT,
    val collectAllCustom: Int = 165,
    val doubleLimit: Int = 85,
    val maxBidIsShelem: Boolean = false,
    val shelemMode: Int = ShelemMode.CONTRACTOR,
    val shelemContractor: Int = 165,
    val shelemOpponent: Int = -165,
    val endWithDiff: Boolean = false,
    val endDiff: Int = 1100,
    val doubleShelemMode: Int = DoubleShelemMode.DOUBLE,
    val doubleShelemWin: Int = 660,
    val doubleShelemLose: Int = -660,
    val defaultEndPoint: Int = 1165
) {
    companion object {
        val NO_JOKER = ShalamModeRules()
        val JOKER = ShalamModeRules(
            maxPoints = 200,
            collectAllCustom = 200,
            doubleLimit = 105,
            shelemContractor = 200,
            shelemOpponent = -200,
            doubleShelemWin = 800,
            doubleShelemLose = -800,
            defaultEndPoint = 1200
        )
    }
}

/** What happens when the contractor collects every point of a hand without calling Shalam. */
object CollectAll {
    const val POINT = 1
    const val DOUBLE = 2
    const val SHELEM = 3
    const val CUSTOM = 4
}

object ShelemMode {
    const val CONTRACTOR = 1
    const val OPPONENT_NEGATIVE = 2
    const val CONTRACTOR_AND_OPPONENT = 3
    const val CUSTOM = 5
}

object DoubleShelemMode {
    const val DOUBLE = 1
    const val CUSTOM = 2
}

object DoubleType {
    const val POSITIVE = 1
    const val NEGATIVE = 2
    const val ASK = 3
    const val DISABLED = 4
}

object DealType {
    const val TWELVE = 1
    const val TWELVE_POSITIVE_ONLY = 2
    const val FOUR = 3

    fun title(type: Int): String = when (type) {
        TWELVE_POSITIVE_ONLY -> "شلم ۱۲ برگ بدون منفی"
        FOUR -> "شلم ۴ برگ"
        else -> "شلم ۱۲ برگ"
    }
}

data class ShalamRules(
    val joker: Boolean = false,
    val endPoint: Int = 1165,
    val mode: ShalamModeRules = ShalamModeRules.NO_JOKER,
    val doubleType: Int = DoubleType.NEGATIVE,
    val contractOnly: Boolean = false,
    val highLimitEnabled: Boolean = false,
    val highLimit: Int = 1000,
    val loserPointsAboveLimit: Boolean = true,
    val dealType: Int = DealType.TWELVE
)

data class MenfiRules(
    val hands: Int = 8,
    val hidden: Boolean = true,
    val threeSuccess: Int = 20,
    val threeFailure: Int = -10,
    val highWins: Boolean = true
)

data class HezarRules(
    val target: Int = 1000,
    val rounds: Int = 10,
    val zeroPenalty: Int = -50
)

data class GameRules(
    val shalam: ShalamRules = ShalamRules(),
    val menfi: MenfiRules = MenfiRules(),
    val hezar: HezarRules = HezarRules()
)

data class GeneralSettings(
    val haptic: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val persianDigits: Boolean = true,
    val largeText: Boolean = false,
    val defaultTeam1: String = "ما",
    val defaultTeam2: String = "اونا"
)

data class ShalamSettings(
    val dealType: Int = DealType.TWELVE,
    val doubleType: Int = DoubleType.NEGATIVE,
    val contractOnly: Boolean = false,
    val highLimitEnabled: Boolean = false,
    val highLimit: Int = 1000,
    val loserPointsAboveLimit: Boolean = true,
    val keyboardInput: Boolean = true,
    val defaultJoker: Boolean = false,
    val noJoker: ShalamModeRules = ShalamModeRules.NO_JOKER,
    val joker: ShalamModeRules = ShalamModeRules.JOKER
) {
    fun mode(withJoker: Boolean): ShalamModeRules = if (withJoker) joker else noJoker

    fun rules(withJoker: Boolean, endPoint: Int = mode(withJoker).defaultEndPoint): ShalamRules = ShalamRules(
        joker = withJoker,
        endPoint = endPoint,
        mode = mode(withJoker),
        doubleType = doubleType,
        contractOnly = contractOnly,
        highLimitEnabled = highLimitEnabled,
        highLimit = highLimit,
        loserPointsAboveLimit = loserPointsAboveLimit,
        dealType = dealType
    )
}

data class MenfiSettings(
    val hands: Int = 8,
    val hidden: Boolean = true,
    val threeSuccess: Int = 20,
    val threeFailure: Int = -10,
    val highWins: Boolean = true
) {
    fun rules(): MenfiRules = MenfiRules(hands, hidden, threeSuccess, threeFailure, highWins)
}

data class HezarSettings(
    val target: Int = 1000,
    val rounds: Int = 10,
    val zeroPenalty: Int = -50,
    val players: Int = 3
) {
    fun rules(): HezarRules = HezarRules(target, rounds, zeroPenalty)
}

data class AppSettings(
    val general: GeneralSettings = GeneralSettings(),
    val shalam: ShalamSettings = ShalamSettings(),
    val menfi: MenfiSettings = MenfiSettings(),
    val hezar: HezarSettings = HezarSettings()
) {
    fun rulesFor(game: GameType, withJoker: Boolean = shalam.defaultJoker, endPoint: Int? = null): GameRules {
        val shalamRules = shalam.rules(withJoker, endPoint ?: shalam.mode(withJoker).defaultEndPoint)
        return when (game) {
            GameType.SHALAM -> GameRules(shalam = shalamRules)
            GameType.MENFI -> GameRules(menfi = menfi.rules())
            GameType.HEZARTAII -> GameRules(hezar = hezar.rules())
        }
    }
}

data class RosterEntry(val id: Long, val name: String, val avatar: Int, val isTeam: Boolean)

object PersianText {
    private const val LATIN = "0123456789"
    private const val PERSIAN = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC = "٠١٢٣٤٥٦٧٨٩"
    const val LRM = "‎"

    fun digits(value: Any, enabled: Boolean = true): String {
        val text = value.toString()
        if (!enabled) return text
        val out = StringBuilder(text.length)
        text.forEach { char ->
            val index = LATIN.indexOf(char)
            out.append(if (index >= 0) PERSIAN[index] else char)
        }
        return out.toString()
    }

    /** Signed number with an explicit sign, kept left-to-right inside RTL text. */
    fun signed(value: Int, enabled: Boolean = true): String {
        val body = when {
            value > 0 -> "+$value"
            value < 0 -> "−${-value}"
            else -> "0"
        }
        return LRM + digits(body, enabled) + LRM
    }

    fun toLatin(value: String): String {
        val out = StringBuilder(value.length)
        value.forEach { char ->
            val p = PERSIAN.indexOf(char)
            val a = ARABIC.indexOf(char)
            out.append(
                when {
                    p >= 0 -> LATIN[p]
                    a >= 0 -> LATIN[a]
                    char == '−' -> '-'
                    else -> char
                }
            )
        }
        return out.toString()
    }

    fun parseInt(value: String): Int? = toLatin(value).trim().replace(" ", "").toIntOrNull()

    fun duration(ms: Long, enabled: Boolean = true): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return digits(String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s), enabled)
    }
}
