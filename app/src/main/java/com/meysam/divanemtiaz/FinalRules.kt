package com.meysam.divanemtiaz

enum class MenfiOutcomeV4(val key: String) {
    BOTH_MADE("both_made"),
    A_FAILED_B_MADE("a_failed_b_made"),
    A_MADE_B_FAILED("a_made_b_failed"),
    BOTH_FAILED("both_failed");

    companion object {
        fun fromKey(key: String): MenfiOutcomeV4? = values().firstOrNull { it.key == key }
    }
}

data class MenfiOutcomeOptionV4(
    val outcome: MenfiOutcomeV4,
    val enabled: Boolean,
    val reason: String = ""
)

data class MenfiHandResultV4(
    val teamAScore: Int,
    val teamBScore: Int,
    val teamAMade: Boolean,
    val teamBMade: Boolean
)

/** Mathematical rules for one 13-trick fixed-trump negative hand. */
object MenfiRulesV4 {
    const val TOTAL_TRICKS = 13

    fun validateBid(bid: Int, settings: V3Settings) {
        require(bid in settings.menfiMinBid..TOTAL_TRICKS)
    }

    fun outcomeOptions(bidA: Int, bidB: Int, settings: V3Settings): List<MenfiOutcomeOptionV4> {
        validateBid(bidA, settings)
        validateBid(bidB, settings)
        val sum = bidA + bidB
        return listOf(
            MenfiOutcomeOptionV4(
                MenfiOutcomeV4.BOTH_MADE,
                sum <= TOTAL_TRICKS,
                if (sum <= TOTAL_TRICKS) "" else "جمع اعلام‌ها بیشتر از ۱۳ است؛ هر دو گروه نمی‌توانند هم‌زمان موفق شوند."
            ),
            MenfiOutcomeOptionV4(MenfiOutcomeV4.A_FAILED_B_MADE, true),
            MenfiOutcomeOptionV4(MenfiOutcomeV4.A_MADE_B_FAILED, true),
            MenfiOutcomeOptionV4(
                MenfiOutcomeV4.BOTH_FAILED,
                sum >= TOTAL_TRICKS + 2,
                if (sum >= TOTAL_TRICKS + 2) "" else "برای منفی شدن هر دو گروه، جمع اعلام‌ها باید حداقل ۱۵ باشد."
            )
        )
    }

    fun isFeasible(bidA: Int, bidB: Int, outcome: MenfiOutcomeV4, settings: V3Settings): Boolean =
        outcomeOptions(bidA, bidB, settings).first { it.outcome == outcome }.enabled

    fun scoreForBid(bid: Int, made: Boolean, settings: V3Settings): Int {
        validateBid(bid, settings)
        if (bid >= 11) {
            return when (bid) {
                11 -> if (made) settings.menfiWin11 else settings.menfiLoss11
                12 -> if (made) settings.menfiWin12 else settings.menfiLoss12
                else -> if (made) settings.menfiWin13 else settings.menfiLoss13
            }
        }
        val delta = (bid - settings.menfiMinBid).coerceAtLeast(0)
        return if (made) settings.menfiBaseWin + delta * settings.menfiWinStep
        else settings.menfiBaseLoss - delta * kotlin.math.abs(settings.menfiLossStep)
    }

    fun score(
        bidA: Int,
        bidB: Int,
        outcome: MenfiOutcomeV4,
        settings: V3Settings
    ): MenfiHandResultV4 {
        require(isFeasible(bidA, bidB, outcome, settings))
        val (madeA, madeB) = when (outcome) {
            MenfiOutcomeV4.BOTH_MADE -> true to true
            MenfiOutcomeV4.A_FAILED_B_MADE -> false to true
            MenfiOutcomeV4.A_MADE_B_FAILED -> true to false
            MenfiOutcomeV4.BOTH_FAILED -> false to false
        }
        return MenfiHandResultV4(
            scoreForBid(bidA, madeA, settings),
            scoreForBid(bidB, madeB, settings),
            madeA,
            madeB
        )
    }
}

object HezartaiiRulesV4 {
    const val MIN_PLAYERS = 5
    const val MAX_PLAYERS = 60

    fun validatePlayers(players: List<V3Player>) {
        require(players.size in MIN_PLAYERS..MAX_PLAYERS)
        val names = players.map { it.name.trim() }
        require(names.none { it.isBlank() })
        require(names.distinct().size == names.size)
    }

    fun normalize(raw: Int, settings: V3Settings): Int =
        if (raw == 0) settings.hezartaiiZeroPenalty else raw

    fun totals(players: List<V3Player>, rounds: List<V3Round>): List<Int> =
        players.indices.map { index -> rounds.sumOf { it.scores.getOrElse(index) { 0 } } }

    fun ranking(players: List<V3Player>, rounds: List<V3Round>): List<Pair<V3Player, Int>> {
        val totals = totals(players, rounds)
        return players.mapIndexed { index, player -> player to totals[index] }
            .sortedByDescending { it.second }
    }
}
