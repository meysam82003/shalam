package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View

fun MenfiDeal.dealerName(sides: List<Side>, handIndex: Int): String = menfiSeatName(sides, dealer(handIndex))

private fun menfiSeatName(sides: List<Side>, seat: Int): String {
    val side = sides[seat / 2]
    return side.members.getOrNull(seat % 2) ?: "نفر ${PersianText.digits(seat % 2 + 1)} از ${side.name}"
}

fun Screen.chooseMenfiDeal(sides: List<Side>, previous: MenfiDeal?, onDone: (MenfiDeal) -> Unit) {
    val seats = (previous?.seats ?: MenfiDeal.clockwiseSeats).toMutableList()
    var first = previous?.order?.firstOrNull() ?: seats.first()
    val body = kit.vertical()
    val directions = listOf("چپ", "بالا", "راست", "پایین")

    fun rebuild() {
        body.removeAllViews()
        body.addView(kit.text("چه کسی دست اول را می‌دهد؟", TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
        body.addView(kit.text("روی نام یکی از چهار نفر بزنید. هم‌تیمی‌ها روبه‌روی هم می‌نشینند.", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
        fun seat(position: Int): View {
            val player = seats[position]
            val label = "${directions[position]} · ${menfiSeatName(sides, player)}"
            return kit.button(label, if (first == player) ButtonKind.PRIMARY else ButtonKind.SECONDARY, height = 44) {
                first = player
                rebuild()
            }
        }
        body.addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(seat(1))
            // Physical left/right positions must stay in place in the RTL app.
            addView(kit.horizontal(Gravity.CENTER).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                addView(kit.weight(seat(0)))
                addView(kit.hgap(7))
                addView(kit.weight(seat(2)))
            }, kit.spaced(6))
            addView(seat(3))
        }, kit.spaced(8))

        fun swap(a: Int, b: Int) {
            val old = seats[a]
            seats[a] = seats[b]
            seats[b] = old
            rebuild()
        }
        body.addView(kit.grid(2, listOf(
            kit.button("جابه‌جایی چپ و راست", ButtonKind.GHOST, RoyalIcon.SWAP, 42) { swap(0, 2) },
            kit.button("جابه‌جایی بالا و پایین", ButtonKind.GHOST, RoyalIcon.SWAP, 42) { swap(1, 3) },
            kit.button("چرخش جای تیم‌ها", ButtonKind.GHOST, RoyalIcon.UNDO, 42) {
                seats.add(seats.removeAt(0))
                rebuild()
            }
        ), 5), kit.spaced(5))

        val order = MenfiDeal.fromSeats(seats, first)
        body.addView(kit.text("نوبت دست‌دادن: " + order.order.joinToString(" ← ") { menfiSeatName(sides, it) },
            TextStyle.LABEL, Royal.turquoiseLight, Gravity.CENTER))
    }
    rebuild()
    kit.dialog("چیدمان میز منفی", null, body, listOf(
        DialogAction("تأیید چیدمان", ButtonKind.PRIMARY) { onDone(MenfiDeal.fromSeats(seats, first)) },
        DialogAction("لغو")
    )).show()
}
