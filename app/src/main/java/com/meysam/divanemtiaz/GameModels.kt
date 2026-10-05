package com.meysam.divanemtiaz

enum class GameType(
    val key: String,
    val title: String,
    val subtitle: String,
    val minSides: Int,
    val maxSides: Int
) {
    SHALAM("shalam", "شلم", "داوری دو تیمی با قوانین کامل شلم", 2, 2),
    MENFI("menfi", "منفی", "ثبت پنهان عددها و حالت رخ‌داده", 2, 2),
    HEZARTAII("hezartaii", "هزارتایی", "رقابت انفرادی و رتبه‌بندی تا هزار", 2, MAX_PLAYERS),
    DOLO("dolo", "دو لو گشنیز", "حکم با دارندهٔ دو لو گشنیز، حداقل و حذف", 3, MAX_PLAYERS);

    val isTeamGame: Boolean get() = this == SHALAM || this == MENFI

    companion object {
        fun fromKey(key: String?): GameType = values().firstOrNull { it.key == key } ?: SHALAM
    }
}

/** Practical upper bound for individual games; far above any real table. */
const val MAX_PLAYERS = 99

data class Side(val name: String, val avatar: Int)

enum class RoundKind(val key: String) {
    SHALAM_HAND("shalam_hand"),
    SHALAM_SHELEM("shalam_shelem"),
    SHALAM_DOUBLE_SHELEM("shalam_double_shelem"),
    SHALAM_PASS("shalam_pass"),
    MENFI_HAND("menfi_hand"),
    HEZAR_ROUND("hezar_round"),
    DOLO_HAND("dolo_hand"),
    DOLO_ELIM("dolo_elim"),
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
    var label: String = "",
    var leagueId: Long = 0L
) {
    val sides: MutableList<Side> = sides.toMutableList()
    val rounds: MutableList<Round> = rounds.toMutableList()

    fun copy(): GameSession = GameSession(
        id, game, sides.toList(), rounds.toList(), rules, finished, updatedAt, elapsedMs, endedAt, label, leagueId
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

/**
 * Menfi scoring. [MenfiScoring.TABLE] (default): a team that takes at least its number gets
 * [success] of that number, otherwise [failure] (±number, and +20 / −10 for ten).
 * [MenfiScoring.LEGACY] keeps the formula of version 3.0 for games recorded with it.
 */
data class MenfiRules(
    val hands: Int = 8,
    val hidden: Boolean = true,
    val threeSuccess: Int = 20,
    val threeFailure: Int = -10,
    val highWins: Boolean = true,
    val scoring: Int = MenfiScoring.TABLE,
    val success: List<Int> = MenfiScoring.defaultSuccess,
    val failure: List<Int> = MenfiScoring.defaultFailure,
    val tieBreak: Boolean = true,
    val tieExtraHands: Int = 2,
    val extraHands: Int = 0
) {
    /** Hands of this game including extra hands added after a tie. */
    val totalHands: Int get() = hands + extraHands
}

object MenfiScoring {
    const val LEGACY = 1
    const val TABLE = 2
    const val TRICKS = 13
    val numbers: List<Int> = (3..13).toList()
    val defaultSuccess: List<Int> = numbers.map { if (it == 10) 20 else it }
    val defaultFailure: List<Int> = numbers.map { -it }

    /** Always 11 values (numbers 3..13); missing entries fall back to the defaults. */
    fun normalize(values: List<Int>?, defaults: List<Int>): List<Int> =
        numbers.indices.map { values?.getOrNull(it) ?: defaults[it] }
}

data class HezarRules(
    val target: Int = 1000,
    val rounds: Int = 10,
    val zeroPenalty: Int = -50
)

/**
 * «دو لو گشنیز»: whoever holds the two of clubs names trump; each player declares at least the
 * minimum number of tricks; a made declaration scores +number, a missed one −number. After every
 * [eliminateEvery] rounds the lowest [eliminateCount] players leave; a tie at the cut gives
 * [tieExtraHands] more hands. The last remaining player wins.
 */
data class DoloRules(
    val minUpTo4: Int = 3,
    val min5to6: Int = 2,
    val minFrom7: Int = 1,
    val madeMultiplier: Int = 1,
    val failMultiplier: Int = 1,
    val handsPerRound: Int = 0,
    val eliminateEvery: Int = 1,
    val eliminateCount: Int = 1,
    val tieExtraHands: Int = 2,
    val resetAfterElimination: Boolean = false
) {
    fun minimumFor(players: Int): Int = when {
        players <= 4 -> minUpTo4
        players <= 6 -> min5to6
        else -> minFrom7
    }.coerceAtLeast(0)
}

data class GameRules(
    val shalam: ShalamRules = ShalamRules(),
    val menfi: MenfiRules = MenfiRules(),
    val hezar: HezarRules = HezarRules(),
    val dolo: DoloRules = DoloRules()
)

/** Card values of «هزارتایی»: 2–9, 10–K, ace and joker. */
data class CardValues(val low: Int = 5, val high: Int = 10, val ace: Int = 20, val joker: Int = 40)

/** Defaults of the deck calculator. */
data class DeckSettings(
    val decks: Int = 2,
    val jokers: Int = 4,
    val handSize: Int = 14,
    val firstPacket: Int = 6,
    val nextPacket: Int = 4,
    val shortAllowance: Int = 6,
    val doloCards: Int = 52,
    val autoHand: Boolean = true,
    val customTotalEnabled: Boolean = false,
    val customTotal: Int = 80
) {
    fun totalCards(): Int = if (customTotalEnabled) customTotal.coerceAtLeast(0) else decks.coerceAtLeast(0) * 52 + jokers.coerceAtLeast(0)
}

object LeagueFormat {
    const val KNOCKOUT = 1
    const val ROUND_ROBIN = 2
}

data class LeagueSettings(
    val format: Int = LeagueFormat.KNOCKOUT,
    val winsNeeded: Int = 2,
    val pointsWin: Int = 3,
    val pointsDraw: Int = 1,
    val pointsLoss: Int = 0,
    val doubleRoundRobin: Boolean = false,
    val finalAfterTable: Boolean = true
)

data class GeneralSettings(
    val haptic: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val persianDigits: Boolean = true,
    val largeText: Boolean = false,
    val defaultTeam1: String = "ما",
    val defaultTeam2: String = "اونا",
    val uiScale: Int = UiScale.NORMAL
)

/** Display size of the whole interface, in percent. */
object UiScale {
    const val COMPACT = 85
    const val NORMAL = 100
    const val LARGE = 115
    val options = listOf(COMPACT to "فشرده", NORMAL to "معمولی", LARGE to "بزرگ")
}

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
    val highWins: Boolean = true,
    val success: List<Int> = MenfiScoring.defaultSuccess,
    val failure: List<Int> = MenfiScoring.defaultFailure,
    val tieBreak: Boolean = true,
    val tieExtraHands: Int = 2
) {
    fun rules(): MenfiRules = MenfiRules(
        hands = hands, hidden = hidden, highWins = highWins, scoring = MenfiScoring.TABLE,
        success = success, failure = failure, tieBreak = tieBreak, tieExtraHands = tieExtraHands
    )
}

data class HezarSettings(
    val target: Int = 1000,
    val rounds: Int = 10,
    val zeroPenalty: Int = -50,
    val players: Int = 3,
    val cards: CardValues = CardValues()
) {
    fun rules(): HezarRules = HezarRules(target, rounds, zeroPenalty)
}

data class DoloSettings(
    val rules: DoloRules = DoloRules(),
    val players: Int = 6
)

data class AppSettings(
    val general: GeneralSettings = GeneralSettings(),
    val shalam: ShalamSettings = ShalamSettings(),
    val menfi: MenfiSettings = MenfiSettings(),
    val hezar: HezarSettings = HezarSettings(),
    val dolo: DoloSettings = DoloSettings(),
    val deck: DeckSettings = DeckSettings(),
    val league: LeagueSettings = LeagueSettings()
) {
    fun rulesFor(game: GameType, withJoker: Boolean = shalam.defaultJoker, endPoint: Int? = null): GameRules {
        val shalamRules = shalam.rules(withJoker, endPoint ?: shalam.mode(withJoker).defaultEndPoint)
        return when (game) {
            GameType.SHALAM -> GameRules(shalam = shalamRules)
            GameType.MENFI -> GameRules(menfi = menfi.rules())
            GameType.HEZARTAII -> GameRules(hezar = hezar.rules())
            GameType.DOLO -> GameRules(dolo = dolo.rules)
        }
    }
}

/** One knockout tie or table fixture of a league; its games are ordinary saved sessions. */
data class LeagueMatch(
    val id: Int,
    val stage: Int,
    val teamA: Int,
    val teamB: Int,
    val feederA: Int = -1,
    val feederB: Int = -1,
    val winsNeeded: Int = 1,
    val games: List<Long> = emptyList(),
    val isFinal: Boolean = false
) {
    val isBye: Boolean get() = teamB == BYE && feederB == -1

    companion object {
        const val BYE = -1
    }
}

class League(
    val id: Long,
    var name: String,
    val game: GameType,
    teams: List<Side>,
    var rules: GameRules,
    val format: Int,
    val config: LeagueSettings,
    matches: List<LeagueMatch> = emptyList(),
    var updatedAt: Long = id,
    var finished: Boolean = false
) {
    val teams: MutableList<Side> = teams.toMutableList()
    val matches: MutableList<LeagueMatch> = matches.toMutableList()
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
