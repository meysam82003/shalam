package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View

fun MenfiDeal.dealerName(sides: List<Side>, handIndex: Int): String = menfiSeatName(sides, dealer(handIndex))

fun menfiSeatName(sides: List<Side>, seat: Int): String {
    val side = sides[seat / 2]
    return side.members.getOrNull(seat % 2) ?: "نفر ${PersianText.digits(seat % 2 + 1)} از ${side.name}"
}

fun Screen.chooseMenfiDeal(sides: List<Side>, previous: MenfiDeal?, onDone: (MenfiDeal) -> Unit) {
    val seats = (previous?.seats ?: MenfiDeal.defaultSeats).toMutableList()
    var direction = previous?.direction ?: MenfiDeal.default.direction
    var first = previous?.order?.firstOrNull() ?: seats.first()
    val body = kit.vertical()
    val directions = listOf("چپ", "بالا", "راست", "پایین")

    fun rebuild() {
        body.removeAllViews()
        body.addView(kit.text("دست‌دهندهٔ اول را انتخاب کنید", TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
        body.addView(kit.text("هم‌تیمی‌ها روبه‌روی هم می‌نشینند.", TextStyle.CAPTION, Royal.muted, Gravity.CENTER))
        fun seat(position: Int): View {
            val player = seats[position]
            val selected = if (first == player) " ✓" else ""
            val label = "${directions[position]} · ${menfiSeatName(sides, player)}$selected"
            return kit.button(label, if (first == player) ButtonKind.PRIMARY else ButtonKind.SECONDARY, height = 44) {
                first = player
                rebuild()
            }
        }
        body.addView(kit.panel(PanelStyle.FLAT, 8).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(seat(1), kit.fill())
            // Physical left/right positions must stay in place in the RTL app.
            addView(kit.horizontal(Gravity.CENTER).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                addView(kit.weight(seat(0)))
                addView(kit.hgap(7))
                addView(kit.weight(seat(2)))
            }, kit.spaced(6))
            addView(seat(3), kit.fill())
        }, kit.spaced(8))

        fun swap(a: Int, b: Int) {
            val old = seats[a]
            seats[a] = seats[b]
            seats[b] = old
            rebuild()
        }
        body.addView(kit.grid(2, listOf(
            kit.button("↔  چپ و راست", ButtonKind.SECONDARY, height = 42) { swap(0, 2) },
            kit.button("↕  بالا و پایین", ButtonKind.SECONDARY, height = 42) { swap(1, 3) },
            kit.button("چرخش جای تیم‌ها", ButtonKind.GHOST, RoyalIcon.UNDO, 42) {
                seats.add(seats.removeAt(0))
                rebuild()
            }
        ), 5), kit.spaced(5))

        body.addView(kit.text("جهت گردش دست‌دهنده", TextStyle.LABEL_BOLD, Royal.muted, Gravity.CENTER))
        body.addView(kit.grid(2, listOf(
            MenfiDirection.CLOCKWISE to "↻  ساعت‌گرد",
            MenfiDirection.COUNTERCLOCKWISE to "↺  پادساعت‌گرد"
        ).map { (item, label) ->
            kit.button(label, if (direction == item) ButtonKind.SUCCESS else ButtonKind.SECONDARY, height = 44) {
                direction = item
                rebuild()
            }
        }, 6), kit.spaced(6))
        val order = MenfiDeal.fromSeats(seats, first, direction)
        body.addView(kit.panel(PanelStyle.SELECTED, 9).apply {
            addView(kit.text("نوبت دست‌دادن", TextStyle.HEADING, Royal.turquoiseLight, Gravity.CENTER))
            addView(kit.grid(2, order.order.mapIndexed { i, seat ->
                kit.text("${kit.n(i + 1)}. ${menfiSeatName(sides, seat)}",
                    TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER, 2)
            }, 8))
        }, kit.spaced(8))
    }
    rebuild()
    kit.dialog("چیدمان میز منفی", null, body, listOf(
        DialogAction("تأیید چیدمان", ButtonKind.PRIMARY) { onDone(MenfiDeal.fromSeats(seats, first, direction)) },
        DialogAction("لغو")
    )).show()
}
