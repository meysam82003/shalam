package com.meysam.divanemtiaz

enum class MenfiDirection { COUNTERCLOCKWISE, CLOCKWISE }

/** Players 0–1 belong to team A, 2–3 to team B. Seats are stored left, top, right, bottom. */
data class MenfiDeal private constructor(val order: List<Int>, val seats: List<Int>, val direction: MenfiDirection) {
    fun dealer(handIndex: Int): Int = order[handIndex.coerceAtLeast(0) % order.size]

    fun biddingOrder(handIndex: Int): List<Int> {
        val dealerTeam = dealer(handIndex) / 2
        return listOf(1 - dealerTeam, dealerTeam)
    }

    companion object {
        val defaultSeats = listOf(0, 2, 1, 3)
        val default = fromSeats(defaultSeats, first = 0)

        fun fromSeats(seats: List<Int>, first: Int, direction: MenfiDirection = MenfiDirection.COUNTERCLOCKWISE): MenfiDeal {
            require(valid(seats) && first in seats)
            // The scorekeeper's counterclockwise order is right → bottom → left → top.
            val walk = if (direction == MenfiDirection.COUNTERCLOCKWISE) seats else seats.reversed()
            val offset = walk.indexOf(first)
            return MenfiDeal(walk.drop(offset) + walk.take(offset), seats.toList(), direction)
        }

        fun restore(order: List<Int>, seats: List<Int>): MenfiDeal? {
            if (!valid(order)) return null
            val layout = seats.takeIf(::valid) ?: order
            return MenfiDirection.values().asSequence()
                .map { fromSeats(layout, order.first(), it) }
                .firstOrNull { it.order == order }
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
