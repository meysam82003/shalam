package com.meysam.divanemtiaz

/** Players 0–1 belong to team A, 2–3 to team B. Seats run clockwise from the left. */
data class MenfiDeal private constructor(val order: List<Int>, val seats: List<Int>) {
    fun dealer(handIndex: Int): Int = order[handIndex.coerceAtLeast(0) % order.size]

    fun biddingOrder(handIndex: Int): List<Int> {
        val dealerTeam = dealer(handIndex) / 2
        return listOf(1 - dealerTeam, dealerTeam)
    }

    companion object {
        val clockwiseSeats = listOf(0, 2, 1, 3)
        val default = fromSeats(clockwiseSeats, first = 0)

        fun fromSeats(seats: List<Int>, first: Int): MenfiDeal {
            require(valid(seats) && first in seats)
            val offset = seats.indexOf(first)
            return MenfiDeal(seats.drop(offset) + seats.take(offset), seats.toList())
        }

        fun restore(order: List<Int>, seats: List<Int>): MenfiDeal? {
            if (!valid(order)) return null
            val layout = seats.takeIf(::valid) ?: order
            return fromSeats(layout, order.first())
        }

        private fun valid(seats: List<Int>): Boolean =
            seats.size == 4 && seats.toSet() == setOf(0, 1, 2, 3) && seats[0] / 2 == seats[2] / 2
    }
}

data class MenfiProjection(val totals: List<Int>, val winners: List<Int>, val extraHands: Boolean)

fun MenfiEngine.project(session: GameSession, round: Round, editIndex: Int? = null): MenfiProjection {
    val copy = session.copy()
    if (editIndex == null) copy.rounds.add(round) else copy.rounds[editIndex] = round
    GameEngine.recompute(copy)
    val totals = GameEngine.totals(copy)
    return MenfiProjection(totals, GameEngine.winners(copy), tiedAtEnd(GameEngine.playedHands(copy), totals, copy.rules.menfi))
}
