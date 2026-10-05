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
    val teamBSucceeded: Boolean,
    val index: Int = -1
)

/**
 * A Menfi hand has 13 tricks; each team must take at least its number. Which results can happen
 * follows from the sum of the two numbers:
 * sum ≤ 13 → both made / only A / only B; sum = 14 → exactly one makes it;
 * sum ≥ 15 → only A / only B / both failed.
 */
object MenfiEngine {
    const val BOTH = 0
    const val A_ONLY = 1
    const val B_ONLY = 2
    const val NONE = 3
    const val MANUAL = 9

    val readyNumbers: List<Int> = MenfiScoring.numbers

    fun successScore(number: Int, rules: MenfiRules = MenfiRules()): Int =
        if (rules.scoring == MenfiScoring.LEGACY) {
            if (number == 3) rules.threeSuccess else 13 - number
        } else rules.success.getOrNull(number - 3) ?: if (number == 10) 20 else number

    fun failureScore(number: Int, rules: MenfiRules = MenfiRules()): Int =
        if (rules.scoring == MenfiScoring.LEGACY) {
            if (number == 3) rules.threeFailure else -(13 - number)
        } else rules.failure.getOrNull(number - 3) ?: -number

    /** Result indices that are possible for these two numbers. */
    fun possible(teamANumber: Int, teamBNumber: Int, rules: MenfiRules = MenfiRules()): List<Int> {
        if (rules.scoring == MenfiScoring.LEGACY) return listOf(BOTH, A_ONLY, B_ONLY)
        val sum = teamANumber + teamBNumber
        return when {
            sum <= MenfiScoring.TRICKS -> listOf(BOTH, A_ONLY, B_ONLY)
            sum == MenfiScoring.TRICKS + 1 -> listOf(A_ONLY, B_ONLY)
            else -> listOf(A_ONLY, B_ONLY, NONE)
        }
    }

    fun outcome(teamANumber: Int, teamBNumber: Int, index: Int, rules: MenfiRules = MenfiRules()): MenfiOutcome {
        val aMade = index == BOTH || index == A_ONLY
        val bMade = index == BOTH || index == B_ONLY
        val a = if (aMade) successScore(teamANumber, rules) else failureScore(teamANumber, rules)
        val b = if (bMade) successScore(teamBNumber, rules) else failureScore(teamBNumber, rules)
        val title = when (index) {
            BOTH -> "هر دو تیم گرفتند"
            A_ONLY -> "تیم اول گرفت؛ تیم دوم نگرفت"
            B_ONLY -> "تیم اول نگرفت؛ تیم دوم گرفت"
            else -> "هیچ‌کدام نگرفتند"
        }
        return MenfiOutcome(a, b, title, aMade, bMade, index)
    }

    fun outcomes(teamANumber: Int, teamBNumber: Int, rules: MenfiRules = MenfiRules()): List<MenfiOutcome> {
        require(teamANumber in readyNumbers && teamBNumber in readyNumbers)
        return possible(teamANumber, teamBNumber, rules).map { outcome(teamANumber, teamBNumber, it, rules) }
    }

    fun scoreRound(round: Round, rules: MenfiRules): List<Int> {
        if (round.outcome == MANUAL) return round.raw
        val a = round.numbers.getOrNull(0)
        val b = round.numbers.getOrNull(1)
        if (a == null || b == null || a !in readyNumbers || b !in readyNumbers) return round.scores
        if (round.outcome !in listOf(BOTH, A_ONLY, B_ONLY, NONE)) return round.scores
        val o = outcome(a, b, round.outcome, rules)
        return listOf(o.teamAScore, o.teamBScore)
    }

    /** The last hand is played but both totals are equal: the referee adds extra hands. */
    fun tiedAtEnd(handsPlayed: Int, totals: List<Int>, rules: MenfiRules): Boolean =
        rules.tieBreak && rules.totalHands in 1..handsPlayed && totals.size >= 2 && totals[0] == totals[1]

    fun isComplete(handsPlayed: Int, totals: List<Int>, rules: MenfiRules): Boolean =
        rules.totalHands in 1..handsPlayed && !tiedAtEnd(handsPlayed, totals, rules)
}

object HezarEngine {
    fun roundScore(raw: Int, rules: HezarRules): Int = if (raw == 0) rules.zeroPenalty else raw

    fun scoreRound(round: Round, rules: HezarRules): List<Int> = round.raw.map { roundScore(it, rules) }

    fun isComplete(totals: List<Int>, roundsPlayed: Int, rules: HezarRules): Boolean =
        (rules.target > 0 && totals.any { it >= rules.target }) ||
            (rules.rounds > 0 && roundsPlayed >= rules.rounds)
}

/** Counted cards of one player in «هزارتایی». */
data class CardCount(val low: Int = 0, val high: Int = 0, val aces: Int = 0, val jokers: Int = 0)

object CardCalc {
    fun points(count: CardCount, values: CardValues): Int =
        count.low * values.low + count.high * values.high + count.aces * values.ace + count.jokers * values.joker

    /** Points of one standard deck (2–9: 32 cards, 10–K: 16 cards, 4 aces) plus jokers. */
    fun deckPoints(decks: Int, jokers: Int, values: CardValues): Int =
        decks * (32 * values.low + 16 * values.high + 4 * values.ace) + jokers * values.joker
}

data class HezarDeal(
    val totalCards: Int,
    val handSize: Int,
    val packets: List<Int>,
    val dealt: Int,
    val stock: Int,
    val short: Int,
    val takeFromBottom: Int,
    val decksNeeded: Int,
    val maxHandSize: Int,
    val totalPoints: Int
) {
    val fits: Boolean get() = short == 0
    val fixableFromBottom: Boolean get() = short in 1..takeFromBottom
}

/** How the deal works out: everything fits, a few cards come from the bottom, or hands are reduced. */
enum class DealMode { FITS, BOTTOM, REDUCED }

data class AutoDeal(
    val totalCards: Int,
    val players: Int,
    val preferredHand: Int,
    val handSize: Int,
    val packets: List<Int>,
    val dealt: Int,
    val stock: Int,
    val short: Int,
    val mode: DealMode,
    val takeFromBottom: Int,
    val cardsNeeded: Int,
    val decksNeeded: Int
)

data class DoloDeal(
    val totalCards: Int,
    val players: Int,
    val perPlayer: Int,
    val removeCards: Int,
    val minimum: Int
)

/** Deck arithmetic: every result is a whole number of cards. */
object DeckCalc {
    /** Packets of a deal: the first packet, then equal packets, the last one holding the rest. */
    fun packets(handSize: Int, first: Int, next: Int): List<Int> {
        if (handSize <= 0) return emptyList()
        val out = mutableListOf<Int>()
        var left = handSize
        val firstSize = first.coerceAtLeast(1).coerceAtMost(left)
        out += firstSize
        left -= firstSize
        val step = next.coerceAtLeast(1)
        while (left > 0) {
            val p = minOf(step, left)
            out += p
            left -= p
        }
        return out
    }

    fun hezar(players: Int, decks: Int, jokers: Int, deck: DeckSettings, values: CardValues): HezarDeal {
        val p = players.coerceAtLeast(1)
        val total = decks.coerceAtLeast(0) * 52 + jokers.coerceAtLeast(0)
        val hand = deck.handSize.coerceAtLeast(1)
        val dealt = p * hand
        val stock = total - dealt
        val short = if (stock < 0) -stock else 0
        val jokersPerDeck = if (decks > 0) jokers.toDouble() / decks else 0.0
        var need = decks.coerceAtLeast(1)
        while (need * 52 + (need * jokersPerDeck).toInt() < dealt && need < 999) need++
        return HezarDeal(
            totalCards = total,
            handSize = hand,
            packets = packets(hand, deck.firstPacket, deck.nextPacket),
            dealt = dealt,
            stock = stock.coerceAtLeast(0),
            short = short,
            takeFromBottom = deck.shortAllowance.coerceAtLeast(0),
            decksNeeded = need,
            maxHandSize = total / p,
            totalPoints = CardCalc.deckPoints(decks, jokers, values)
        )
    }

    /** Deal in about three rounds; the first round takes the remainder (14 → 6 + 4 + 4). */
    fun splitPackets(hand: Int): List<Int> {
        if (hand <= 0) return emptyList()
        val rounds = when {
            hand >= 9 -> 3
            hand >= 4 -> 2
            else -> 1
        }
        val base = hand / rounds
        val rem = hand % rounds
        return List(rounds) { if (it == 0) base + rem else base }
    }

    /** Works out the hand size and dealing plan from players and the cards available. */
    fun hezarAuto(players: Int, deck: DeckSettings): AutoDeal {
        val p = players.coerceAtLeast(1)
        val total = deck.totalCards()
        val preferred = deck.handSize.coerceAtLeast(1)
        val need = p * preferred
        val allowance = deck.shortAllowance.coerceAtLeast(0)
        val (hand, mode) = when {
            need <= total -> preferred to DealMode.FITS
            need - total <= allowance -> preferred to DealMode.BOTTOM
            else -> (total / p).coerceAtLeast(1) to DealMode.REDUCED
        }
        val dealt = p * hand
        val jokersPerDeck = if (deck.decks > 0) deck.jokers.toDouble() / deck.decks else 0.0
        var decksNeeded = deck.decks.coerceAtLeast(1)
        while (decksNeeded * 52 + Math.round(decksNeeded * jokersPerDeck).toInt() < need && decksNeeded < 999) decksNeeded++
        return AutoDeal(
            totalCards = total,
            players = p,
            preferredHand = preferred,
            handSize = hand,
            packets = splitPackets(hand),
            dealt = dealt,
            stock = (total - dealt).coerceAtLeast(0),
            short = if (mode == DealMode.BOTTOM) need - total else 0,
            mode = mode,
            takeFromBottom = allowance,
            cardsNeeded = need,
            decksNeeded = decksNeeded
        )
    }

    fun dolo(players: Int, totalCards: Int, rules: DoloRules): DoloDeal {
        val p = players.coerceAtLeast(1)
        val total = totalCards.coerceAtLeast(0)
        return DoloDeal(total, p, total / p, total % p, rules.minimumFor(p))
    }
}

/** Elimination state of a «دو لو گشنیز» game at its current end. */
data class DoloState(
    val active: List<Int>,
    val eliminatedAt: Map<Int, Int>,
    val handsInStage: Int,
    val handsDue: Int,
    val stageTotals: List<Int>,
    val extension: Boolean
) {
    val eliminationDue: Boolean get() = active.size > 1 && handsDue > 0 && handsInStage >= handsDue
}

object DoloEngine {
    fun minimum(players: Int, rules: DoloRules): Int = rules.minimumFor(players)

    /** Declared numbers in [Round.numbers] (−1 = not playing); [Round.raw] = 1 made / 0 missed. */
    fun scoreRound(round: Round, sideCount: Int, rules: DoloRules): List<Int> {
        if (round.outcome == MenfiEngine.MANUAL) return round.raw
        return List(sideCount) { i ->
            val declared = round.numbers.getOrElse(i) { -1 }
            if (declared < 0) 0
            else if (round.raw.getOrElse(i) { 0 } == 1) declared * rules.madeMultiplier
            else -declared * rules.failMultiplier
        }
    }

    fun state(session: GameSession, upTo: Int = session.rounds.size): DoloState {
        val rules = session.rules.dolo
        val n = session.sides.size
        val eliminatedAt = linkedMapOf<Int, Int>()
        var handsInStage = 0
        var extension = false
        val stageTotals = MutableList(n) { 0 }
        fun regularDue(active: Int) = (if (rules.handsPerRound > 0) rules.handsPerRound else active) * rules.eliminateEvery.coerceAtLeast(1)
        var handsDue = regularDue(n)
        for (index in 0 until upTo.coerceAtMost(session.rounds.size)) {
            val round = session.rounds[index]
            when (round.kind) {
                RoundKind.DOLO_ELIM -> {
                    round.raw.forEachIndexed { side, flag -> if (flag == 1 && side !in eliminatedAt) eliminatedAt[side] = index }
                    val activeCount = n - eliminatedAt.size
                    handsInStage = 0
                    if (round.bid > 0) {
                        extension = true
                        handsDue = round.bid
                    } else {
                        extension = false
                        handsDue = regularDue(activeCount)
                        if (rules.resetAfterElimination) stageTotals.indices.forEach { stageTotals[it] = 0 }
                    }
                }
                RoundKind.PENALTY, RoundKind.ADJUST -> round.scores.forEachIndexed { i, v -> if (i < n) stageTotals[i] += v }
                else -> {
                    handsInStage++
                    round.scores.forEachIndexed { i, v -> if (i < n) stageTotals[i] += v }
                }
            }
        }
        val active = (0 until n).filter { it !in eliminatedAt }
        return DoloState(active, eliminatedAt, handsInStage, handsDue, stageTotals, extension)
    }

    /** Lowest players for the cut, and whether a tie crosses the cut line. */
    fun candidates(session: GameSession): Pair<List<Int>, Boolean> {
        val st = state(session)
        val count = session.rules.dolo.eliminateCount.coerceIn(1, (st.active.size - 1).coerceAtLeast(1))
        val sorted = st.active.sortedWith(compareBy<Int> { st.stageTotals[it] }.thenBy { it })
        val cut = sorted.take(count)
        val next = sorted.getOrNull(count)
        val tie = next != null && cut.isNotEmpty() && st.stageTotals[next] == st.stageTotals[cut.last()]
        return cut to tie
    }

    fun isComplete(session: GameSession): Boolean = session.sides.size > 1 && state(session).active.size <= 1
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
                RoundKind.DOLO_HAND -> DoloEngine.scoreRound(round, sideCount, session.rules.dolo)
                RoundKind.DOLO_ELIM -> List(sideCount) { 0 }
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
        session.rounds.count { it.kind != RoundKind.PENALTY && it.kind != RoundKind.ADJUST && it.kind != RoundKind.DOLO_ELIM }

    fun isComplete(session: GameSession): Boolean {
        val totals = totals(session)
        return when (session.game) {
            GameType.SHALAM -> ShalamEngine.isComplete(totals, session.rules.shalam)
            GameType.MENFI -> MenfiEngine.isComplete(playedHands(session), totals, session.rules.menfi)
            GameType.HEZARTAII -> HezarEngine.isComplete(totals, playedHands(session), session.rules.hezar)
            GameType.DOLO -> DoloEngine.isComplete(session)
        }
    }

    fun highWins(session: GameSession): Boolean =
        session.game != GameType.MENFI || session.rules.menfi.highWins

    /** Side indices from best to worst. In «دو لو گشنیز» players still in the game come first. */
    fun ranking(session: GameSession): List<Int> {
        val totals = totals(session)
        if (session.game == GameType.DOLO) {
            val st = DoloEngine.state(session)
            return totals.indices.sortedWith(
                compareBy<Int> { if (it in st.active) 0 else 1 }
                    .thenByDescending { st.eliminatedAt[it] ?: Int.MAX_VALUE }
                    .thenByDescending { totals[it] }
                    .thenBy { it }
            )
        }
        val high = highWins(session)
        return totals.indices.sortedWith(compareBy<Int> { if (high) -totals[it] else totals[it] }.thenBy { it })
    }

    /** Best side(s); empty when every side is tied. */
    fun winners(session: GameSession): List<Int> {
        val totals = totals(session)
        if (session.game == GameType.DOLO) {
            val active = DoloEngine.state(session).active
            if (active.size == 1) return active
            val activeTotals = active.map { totals[it] }
            if (activeTotals.isEmpty() || activeTotals.distinct().size == 1) return emptyList()
            val best = activeTotals.maxOrNull()
            return active.filter { totals[it] == best }
        }
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
