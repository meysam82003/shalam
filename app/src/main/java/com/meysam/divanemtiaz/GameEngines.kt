package com.meysam.divanemtiaz

import kotlin.math.abs

enum class ShalamResult { SUCCESS, FAIL, DOUBLE_POSITIVE, DOUBLE_NEGATIVE, SHELEM_WIN, SHELEM_LOSE, PASS }

data class ShalamOutcome(
    val contractor: Int,
    val opponent: Int,
    val result: ShalamResult,
    val actual: Int,
    val isDouble: Boolean = false,
    val needsDoubleChoice: Boolean = false,
    val collectedAll: Boolean = false
) {
    val succeeded: Boolean get() = result == ShalamResult.SUCCESS || result == ShalamResult.SHELEM_WIN
}

/**
 * Shalam scoring, matching the reference «شلم‌شمار» app:
 * a hand holds [ShalamModeRules.maxPoints] points (165, or 200 with jokers); the contractor makes
 * the contract when `bid + opponentPoints <= maxPoints`.
 */
object ShalamEngine {
    const val HAND_POINTS = 165
    const val MIN_PICKER_BID = 80
    const val MIN_TYPED_BID = 50

    fun bidOptions(mode: ShalamModeRules): List<Int> = (MIN_PICKER_BID..mode.maxPoints step 5).toList()

    fun takenOptions(mode: ShalamModeRules): List<Int> = (0 until mode.maxPoints step 5).toList()

    /** Typing «35» means 135 whenever 1xx is still a valid bid (reference keyboard shortcut). */
    fun normalizeTypedBid(value: Int, mode: ShalamModeRules): Int =
        if (value < 100 && value + 100 <= mode.maxPoints) value + 100 else value

    fun isValidBid(bid: Int, mode: ShalamModeRules): Boolean =
        bid >= MIN_TYPED_BID && bid <= mode.maxPoints && bid % 5 == 0

    fun isValidTaken(taken: Int, mode: ShalamModeRules): Boolean = taken >= 0 && taken < mode.maxPoints

    fun isDoubleHand(bid: Int, taken: Int, rules: ShalamRules): Boolean =
        bid + taken > rules.mode.maxPoints && taken >= rules.mode.doubleLimit && rules.doubleType != DoubleType.DISABLED

    fun scoreHand(bid: Int, taken: Int, rules: ShalamRules, double: Int = DoubleChoice.AUTO): ShalamOutcome {
        val mode = rules.mode
        val actual = mode.maxPoints - taken
        if (bid + taken <= mode.maxPoints) {
            var contractor = actual
            if (taken == 0) {
                when (mode.collectAll) {
                    CollectAll.SHELEM -> return scoreShelem(false, 0, rules).copy(collectedAll = true)
                    CollectAll.DOUBLE -> contractor = bid * 2
                    CollectAll.POINT -> contractor = mode.maxPoints
                    CollectAll.CUSTOM -> contractor = mode.collectAllCustom
                }
                return ShalamOutcome(contractor, taken, ShalamResult.SUCCESS, actual, collectedAll = true)
            }
            if (rules.contractOnly) contractor = bid
            return ShalamOutcome(contractor, taken, ShalamResult.SUCCESS, actual)
        }
        if (taken >= mode.doubleLimit && rules.doubleType != DoubleType.DISABLED) {
            val type = when (rules.doubleType) {
                DoubleType.ASK -> double
                else -> rules.doubleType
            }
            return when (type) {
                DoubleType.POSITIVE -> ShalamOutcome(-bid, taken * 2, ShalamResult.DOUBLE_POSITIVE, actual, isDouble = true)
                DoubleType.NEGATIVE -> ShalamOutcome(-bid * 2, taken, ShalamResult.DOUBLE_NEGATIVE, actual, isDouble = true)
                else -> ShalamOutcome(-bid, taken, ShalamResult.FAIL, actual, isDouble = true, needsDoubleChoice = true)
            }
        }
        return ShalamOutcome(-bid, taken, ShalamResult.FAIL, actual)
    }

    fun scoreShelem(doubleShelem: Boolean, taken: Int, rules: ShalamRules): ShalamOutcome {
        val mode = rules.mode
        val won = taken <= 0
        val base = mode.maxPoints * 2
        var contractor = 0
        var opponent = taken
        when (mode.shelemMode) {
            ShelemMode.CONTRACTOR -> {
                contractor = base
                opponent = 0
            }
            ShelemMode.OPPONENT_NEGATIVE -> opponent = -base
            ShelemMode.CONTRACTOR_AND_OPPONENT -> contractor = base
            ShelemMode.CUSTOM -> {
                contractor = mode.shelemContractor
                opponent = mode.shelemOpponent
            }
            else -> opponent = 0
        }
        if (!won) {
            contractor = -contractor
            if (mode.shelemMode != ShelemMode.CONTRACTOR_AND_OPPONENT && mode.shelemMode != ShelemMode.CUSTOM) {
                opponent = -opponent
            }
        }
        if (doubleShelem) {
            if (mode.doubleShelemMode == DoubleShelemMode.DOUBLE) {
                contractor *= 2
                opponent *= 2
            } else if (mode.doubleShelemMode == DoubleShelemMode.CUSTOM) {
                contractor = if (won) mode.doubleShelemWin else mode.doubleShelemLose
            }
        }
        return ShalamOutcome(
            contractor,
            opponent,
            if (won) ShalamResult.SHELEM_WIN else ShalamResult.SHELEM_LOSE,
            mode.maxPoints - taken
        )
    }

    /** A team at or above the limit earns opponent points only when the contractor fails (if allowed). */
    fun limitedOpponentScore(contractor: Int, opponent: Int, opponentTotalBefore: Int, rules: ShalamRules): Int {
        if (rules.highLimitEnabled && opponentTotalBefore >= rules.highLimit) {
            if (!(rules.loserPointsAboveLimit && contractor < 0)) return 0
        }
        return opponent
    }

    fun outcome(round: Round, rules: ShalamRules): ShalamOutcome = when (round.kind) {
        RoundKind.SHALAM_SHELEM -> scoreShelem(false, round.taken, rules)
        RoundKind.SHALAM_DOUBLE_SHELEM -> scoreShelem(true, round.taken, rules)
        RoundKind.SHALAM_HAND -> {
            val double = if (rules.doubleType == DoubleType.ASK && round.double == DoubleChoice.AUTO) {
                DoubleChoice.NEGATIVE
            } else round.double
            scoreHand(round.bid, round.taken, rules, double)
        }
        else -> ShalamOutcome(0, 0, ShalamResult.PASS, 0)
    }

    fun scoreRound(round: Round, rules: ShalamRules, totalsBefore: List<Int>): List<Int> {
        if (!round.kind.isShalamContract) return listOf(0, 0)
        val team = round.contractTeam.coerceIn(0, 1)
        val result = outcome(round, rules)
        val opponentTotal = totalsBefore.getOrElse(1 - team) { 0 }
        val opponent = limitedOpponentScore(result.contractor, result.opponent, opponentTotal, rules)
        return if (team == 0) listOf(result.contractor, opponent) else listOf(opponent, result.contractor)
    }

    fun isComplete(totals: List<Int>, rules: ShalamRules): Boolean {
        if (rules.endPoint <= 0 || totals.all { it == 0 }) return false
        val reached = totals.any { it >= rules.endPoint }
        val byDifference = rules.mode.endWithDiff && rules.mode.endDiff > 0 &&
            totals.size >= 2 && abs(totals[0] - totals[1]) >= rules.mode.endDiff
        return reached || byDifference
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

    fun successScore(number: Int, rules: MenfiRules = MenfiRules()): Int =
        if (number == 3) rules.threeSuccess else 13 - number

    fun failureScore(number: Int, rules: MenfiRules = MenfiRules()): Int =
        if (number == 3) rules.threeFailure else -(13 - number)

    fun outcomes(teamANumber: Int, teamBNumber: Int, rules: MenfiRules = MenfiRules()): List<MenfiOutcome> {
        require(teamANumber in readyNumbers && teamBNumber in readyNumbers)
        val aPlus = successScore(teamANumber, rules)
        val bPlus = successScore(teamBNumber, rules)
        val aMinus = failureScore(teamANumber, rules)
        val bMinus = failureScore(teamBNumber, rules)
        return listOf(
            MenfiOutcome(aPlus, bPlus, "هر دو تیم گرفتند", true, true),
            MenfiOutcome(aPlus, bMinus, "تیم اول گرفت؛ تیم دوم نگرفت", true, false),
            MenfiOutcome(aMinus, bPlus, "تیم اول نگرفت؛ تیم دوم گرفت", false, true)
        )
    }

    fun scoreRound(round: Round, rules: MenfiRules): List<Int> {
        val a = round.numbers.getOrNull(0)
        val b = round.numbers.getOrNull(1)
        if (a == null || b == null || a !in readyNumbers || b !in readyNumbers) return round.scores
        val outcome = outcomes(a, b, rules).getOrNull(round.outcome) ?: return round.scores
        return listOf(outcome.teamAScore, outcome.teamBScore)
    }

    fun isComplete(handsPlayed: Int, rules: MenfiRules): Boolean = rules.hands in 1..handsPlayed
}

object HezarEngine {
    fun roundScore(raw: Int, rules: HezarRules): Int = if (raw == 0) rules.zeroPenalty else raw

    fun scoreRound(round: Round, rules: HezarRules): List<Int> = round.raw.map { roundScore(it, rules) }

    fun isComplete(totals: List<Int>, roundsPlayed: Int, rules: HezarRules): Boolean =
        (rules.target > 0 && totals.any { it >= rules.target }) ||
            (rules.rounds > 0 && roundsPlayed >= rules.rounds)
}

data class ShalamTeamStats(
    val contracts: Int = 0,
    val wins: Int = 0,
    val fails: Int = 0,
    val shelemCalls: Int = 0,
    val shelemWins: Int = 0,
    val doubled: Int = 0,
    val collectedAll: Int = 0
)

data class ShalamStats(
    val teams: List<ShalamTeamStats>,
    val hands: Int,
    val passes: Int,
    val maxLead: Int,
    val maxLeadTeam: Int,
    val suits: Map<Int, Int>
)

object GameEngine {
    fun recompute(session: GameSession) {
        val sideCount = session.sides.size
        val totals = MutableList(sideCount) { 0 }
        for (index in session.rounds.indices) {
            val round = session.rounds[index]
            val computed = when (round.kind) {
                RoundKind.SHALAM_HAND, RoundKind.SHALAM_SHELEM, RoundKind.SHALAM_DOUBLE_SHELEM ->
                    ShalamEngine.scoreRound(round, session.rules.shalam, totals)
                RoundKind.SHALAM_PASS -> List(sideCount) { 0 }
                RoundKind.MENFI_HAND -> MenfiEngine.scoreRound(round, session.rules.menfi)
                RoundKind.HEZAR_ROUND -> HezarEngine.scoreRound(round, session.rules.hezar)
                RoundKind.PENALTY, RoundKind.ADJUST -> round.raw
                RoundKind.FIXED -> round.scores
            }
            val normalized = List(sideCount) { computed.getOrElse(it) { 0 } }
            session.rounds[index] = round.copy(scores = normalized)
            normalized.forEachIndexed { side, value -> totals[side] += value }
        }
    }

    fun totals(session: GameSession): List<Int> =
        session.sides.indices.map { side -> session.rounds.sumOf { it.score(side) } }

    /** Hands that count toward hand/round limits; manual penalties and corrections are excluded. */
    fun playedHands(session: GameSession): Int =
        session.rounds.count { it.kind != RoundKind.PENALTY && it.kind != RoundKind.ADJUST }

    fun isComplete(session: GameSession): Boolean {
        val totals = totals(session)
        return when (session.game) {
            GameType.SHALAM -> ShalamEngine.isComplete(totals, session.rules.shalam)
            GameType.MENFI -> MenfiEngine.isComplete(playedHands(session), session.rules.menfi)
            GameType.HEZARTAII -> HezarEngine.isComplete(totals, playedHands(session), session.rules.hezar)
        }
    }

    fun highWins(session: GameSession): Boolean =
        session.game != GameType.MENFI || session.rules.menfi.highWins

    /** Side indices from best to worst. */
    fun ranking(session: GameSession): List<Int> {
        val totals = totals(session)
        val high = highWins(session)
        return totals.indices.sortedWith(compareBy<Int> { if (high) -totals[it] else totals[it] }.thenBy { it })
    }

    /** Best side(s); empty when every side is tied. */
    fun winners(session: GameSession): List<Int> {
        val totals = totals(session)
        if (totals.isEmpty() || totals.distinct().size == 1) return emptyList()
        val best = if (highWins(session)) totals.maxOrNull() else totals.minOrNull()
        return totals.indices.filter { totals[it] == best }
    }

    fun shalamStats(session: GameSession): ShalamStats {
        val teams = MutableList(2) { ShalamTeamStats() }
        val suits = mutableMapOf<Int, Int>()
        var passes = 0
        var hands = 0
        var maxLead = 0
        var maxLeadTeam = -1
        var a = 0
        var b = 0
        session.rounds.forEach { round ->
            if (round.kind == RoundKind.SHALAM_PASS) passes++
            if (round.kind.isShalamContract && round.contractTeam in 0..1) {
                hands++
                val team = round.contractTeam
                val outcome = ShalamEngine.outcome(round, session.rules.shalam)
                val shelem = round.kind != RoundKind.SHALAM_HAND
                val current = teams[team]
                teams[team] = current.copy(
                    contracts = current.contracts + 1,
                    wins = current.wins + if (outcome.succeeded) 1 else 0,
                    fails = current.fails + if (outcome.succeeded) 0 else 1,
                    shelemCalls = current.shelemCalls + if (shelem) 1 else 0,
                    shelemWins = current.shelemWins + if (shelem && outcome.succeeded) 1 else 0,
                    doubled = current.doubled + if (outcome.isDouble) 1 else 0,
                    collectedAll = current.collectedAll + if (outcome.collectedAll) 1 else 0
                )
                if (round.suit != Suit.NONE) suits[round.suit] = (suits[round.suit] ?: 0) + 1
            }
            a += round.score(0)
            b += round.score(1)
            if (abs(a - b) > maxLead) {
                maxLead = abs(a - b)
                maxLeadTeam = if (a > b) 0 else 1
            }
        }
        return ShalamStats(teams, hands, passes, maxLead, maxLeadTeam, suits)
    }
}
