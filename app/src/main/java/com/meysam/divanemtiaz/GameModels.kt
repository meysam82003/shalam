package com.meysam.divanemtiaz

enum class GameType(val key: String, val title: String, val subtitle: String, val target: Int) {
    SHALAM("shalam", "شلم", "داوری حرفه‌ای شلم", 1650),
    MENFI("menfi", "منفی", "ثبت پنهان و نتیجهٔ مرحله‌ای", 0),
    HEZARTAII("hezartaii", "هزارتایی", "رقابت انفرادی تا هزار", 1000);

    companion object {
        fun fromKey(key: String): GameType = values().firstOrNull { it.key == key } ?: SHALAM
    }
}

enum class ShalamBidKind { PASS, CONTRACT, SHALAM, DOUBLE_SHALAM }

data class ShalamBid(val kind: ShalamBidKind, val value: Int, val title: String)

data class ShalamResult(
    val contractScore: Int,
    val opponentScore: Int,
    val actualContractPoints: Int,
    val succeeded: Boolean
)

object ShalamEngine {
    const val HAND_POINTS = 165

    val readyBids: List<ShalamBid> = buildList {
        add(ShalamBid(ShalamBidKind.PASS, 0, "پاس"))
        (100..160 step 5).forEach { add(ShalamBid(ShalamBidKind.CONTRACT, it, it.toString())) }
        add(ShalamBid(ShalamBidKind.SHALAM, 165, "شلم"))
        add(ShalamBid(ShalamBidKind.DOUBLE_SHALAM, 330, "دبل‌شلم"))
    }

    fun calculate(
        bid: ShalamBid,
        opponentPoints: Int,
        awardContractOnly: Boolean = false,
        shalamValue: Int = 330
    ): ShalamResult {
        require(bid.kind != ShalamBidKind.PASS) { "Pass does not produce a result" }
        require(opponentPoints in 0..HAND_POINTS) { "Opponent points must be 0..165" }
        val actual = HAND_POINTS - opponentPoints
        val threshold = when (bid.kind) {
            ShalamBidKind.CONTRACT -> bid.value
            ShalamBidKind.SHALAM, ShalamBidKind.DOUBLE_SHALAM -> HAND_POINTS
            ShalamBidKind.PASS -> error("Pass does not produce a result")
        }
        val succeeded = actual >= threshold
        val contractScore = when {
            !succeeded -> -bid.value
            bid.kind == ShalamBidKind.SHALAM -> shalamValue
            bid.kind == ShalamBidKind.DOUBLE_SHALAM -> shalamValue * 2
            awardContractOnly -> bid.value
            else -> actual
        }
        return ShalamResult(contractScore, opponentPoints, actual, succeeded)
    }
}

data class MenfiOutcome(
    val teamAScore: Int,
    val teamBScore: Int,
    val title: String,
    val teamASucceeded: Boolean,
    val teamBSucceeded: Boolean
)

object MenfiEngine {
    val readyNumbers: List<Int> = (3..13).toList()

    fun successScore(number: Int): Int = if (number == 3) 20 else 13 - number
    fun failureScore(number: Int): Int = if (number == 3) -10 else -(13 - number)

    fun outcomes(teamANumber: Int, teamBNumber: Int): List<MenfiOutcome> {
        require(teamANumber in readyNumbers && teamBNumber in readyNumbers)
        val aPlus = successScore(teamANumber)
        val bPlus = successScore(teamBNumber)
        val aMinus = failureScore(teamANumber)
        val bMinus = failureScore(teamBNumber)
        return listOf(
            MenfiOutcome(aPlus, bPlus, signedPair(aPlus, bPlus), true, true),
            MenfiOutcome(aPlus, bMinus, signedPair(aPlus, bMinus), true, false),
            MenfiOutcome(aMinus, bPlus, signedPair(aMinus, bPlus), false, true)
        )
    }

    private fun signedPair(a: Int, b: Int): String = "${signed(a)}  |  ${signed(b)}"
    fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()
}

data class ScoreRound(
    var teamA: Int,
    var teamB: Int,
    val note: String = "",
    val sourceA: Int? = null,
    val sourceB: Int? = null,
    val contractTeam: Int? = null,
    val contract: String? = null
)

data class GameSession(
    val game: GameType,
    var teamA: String,
    var teamB: String,
    var avatarA: Int,
    var avatarB: Int,
    val rounds: MutableList<ScoreRound> = mutableListOf(),
    val startedAt: Long = System.currentTimeMillis()
)

object ScoreEngine {
    fun total(rounds: List<ScoreRound>, teamA: Boolean): Int =
        rounds.sumOf { if (teamA) it.teamA else it.teamB }

    fun winner(game: GameType, scoreA: Int, scoreB: Int): Int = when {
        scoreA == scoreB -> 0
        game == GameType.MENFI && scoreA < scoreB -> 1
        game == GameType.MENFI -> 2
        scoreA > scoreB -> 1
        else -> 2
    }

    fun reachedTarget(game: GameType, scoreA: Int, scoreB: Int, target: Int = game.target): Boolean =
        target > 0 && (scoreA >= target || scoreB >= target)
}

data class AppSettings(
    var haptic: Boolean = true,
    var keepScreenAwake: Boolean = true,
    var persianDigits: Boolean = true,
    var largeText: Boolean = false,
    var shalamTarget: Int = 1650,
    var shalamValue: Int = 330,
    var shalamAwardContractOnly: Boolean = false,
    var shalamWithJoker: Boolean = false,
    var shalamAskDouble: Boolean = true,
    var shalamEndDifference: Int = 0,
    var menfiHands: Int = 8,
    var menfiHiddenUntilReveal: Boolean = true,
    var hezartaiiRounds: Int = 10,
    var hezartaiiZeroPenalty: Int = -50
)
