package com.meysam.divanemtiaz

import kotlin.math.abs

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

enum class MenfiMatchDecision {
    CONTINUE,
    TIE,
    TEAM_A_WINS,
    TEAM_B_WINS
}

/**
 * Rules for one 13-trick fixed-trump negative hand.
 *
 * Scoring is intentionally tied to the declaration of the SAME hand:
 * - made declaration => +bid
 * - failed declaration => -bid
 *
 * Example: 8 / 3 becomes +8/+3, -8/+3, +8/-3 depending on the selected
 * feasible result. The old unrelated +20/-10 table is not used anymore.
 */
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
        return if (made) bid else -bid
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
            teamAScore = scoreForBid(bidA, madeA, settings),
            teamBScore = scoreForBid(bidB, madeB, settings),
            teamAMade = madeA,
            teamBMade = madeB
        )
    }

    fun matchDecision(rounds: List<V3Round>, targetHands: Int): MenfiMatchDecision {
        if (rounds.size < targetHands) return MenfiMatchDecision.CONTINUE
        val a = rounds.sumOf { it.scores.getOrElse(0) { 0 } }
        val b = rounds.sumOf { it.scores.getOrElse(1) { 0 } }
        return when {
            a > b -> MenfiMatchDecision.TEAM_A_WINS
            b > a -> MenfiMatchDecision.TEAM_B_WINS
            else -> MenfiMatchDecision.TIE
        }
    }
}

/**
 * High-confidence Shalam rules reconstructed from ShalamShomar 6.2.1, adapted to
 * Divan's existing UI. The recovered app uses 165 without Joker and 200 with
 * Joker; standard Shelem is 2 * maxPointsInHand (330/400).
 */
object ShalamReferenceEngine {
    fun total(settings: V3Settings): Int = if (settings.shalamWithJoker) 200 else 165

    fun defaultLimit(settings: V3Settings): Int = if (settings.shalamWithJoker) 105 else 85

    fun readyBids(settings: V3Settings): List<Int> = buildList {
        val total = total(settings)
        var value = settings.shalamMinBid.coerceIn(5, total - 5)
        // ShalamShomar's normal contracts remain stepped values; the full maximum
        // is presented as the Shelem action.
        while (value < total) {
            add(value)
            value += 5
        }
        add(total)
    }.distinct()

    fun score(
        contract: Int,
        opponentActual: Int,
        settings: V3Settings,
        declaredShalam: Boolean = false
    ): ShalamHandResult {
        val total = total(settings)
        require(contract in 1..total)
        require(opponentActual in 0..total)

        val contractActual = total - opponentActual
        val succeeded = contractActual >= contract
        val effectiveYasaLimit = when {
            settings.shalamWithJoker && settings.shalamYasaThreshold == 85 -> 105
            else -> settings.shalamYasaThreshold
        }
        val yasa = !succeeded && settings.shalamYasaEnabled && contractActual < effectiveYasaLimit
        val fullSweep = contractActual == total
        val shelem = fullSweep && (declaredShalam || contract == total)

        val contractScore = when {
            yasa -> -total
            !succeeded -> -contract
            shelem -> total * 2 // exact recovered default: 330 / 400
            settings.shalamAwardContractOnly -> contract
            else -> contractActual
        }

        return ShalamHandResult(
            contractScore = contractScore,
            opponentScore = opponentActual,
            contractActual = contractActual,
            succeeded = succeeded,
            yasaApplied = yasa,
            shalamApplied = shelem
        )
    }

    fun shouldEndGame(scoreA: Int, scoreB: Int, settings: V3Settings): Boolean {
        val target = settings.shalamTarget
        if (target <= 0 || (scoreA == 0 && scoreB == 0)) return false
        return scoreA >= target || scoreB >= target
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
