package com.meysam.divanemtiaz

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Presentation only: all values come from the current saved game. */
fun Screen.boardArena(session: GameSession, totals: List<Int>, leader: Int?, hidden: Boolean = false, target: Int? = null): View = kit.panel(PanelStyle.RAISED, 16, 28f).apply {
    layoutParams = kit.spaced(12)
    addView(kit.horizontal().apply {
        addView(GameSealView(host, session.game), LinearLayout.LayoutParams(kit.dp(54), kit.dp(54)))
        addView(kit.hgap(10))
        addView(kit.weight(kit.vertical().apply {
            addView(kit.text("میز امتیاز ${session.game.title}", TextStyle.HEADING, Royal.title))
            addView(kit.text(if (hidden) "نتایج تا زمان نمایش محفوظ می‌مانند" else "${kit.n(GameEngine.playedHands(session))} دست ثبت‌شده", TextStyle.CAPTION, Royal.muted))
        }))
        if (target != null) addView(kit.badge("هدف ${kit.n(target)}", Royal.goldLight))
    })
    addView(kit.gap(12))
    session.sides.forEachIndexed { i, side ->
        addView(kit.horizontal().apply {
            background = PanelDrawable(kit.density, if (leader == i && !hidden) PanelStyle.SELECTED else PanelStyle.FLAT, 18f)
            setPadding(kit.dp(10), kit.dp(10), kit.dp(10), kit.dp(13))
            addView(kit.avatar(side.avatar, 42))
            addView(kit.hgap(10))
            addView(kit.weight(kit.vertical().apply {
                addView(kit.text(side.name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 2))
                if (side.members.isNotEmpty()) addView(kit.text(side.members.joinToString(" و "), TextStyle.CAPTION, Royal.muted, maxLines = 2))
                if (target != null && !hidden) {
                    addView(kit.gap(6))
                    addView(kit.progress(totals[i].toFloat() / target.coerceAtLeast(1), if (leader == i) Royal.turquoise else Royal.gold, 4))
                }
            }))
            addView(kit.hgap(10))
            addView(kit.text(if (hidden) "•••" else kit.signed(totals[i]), TextStyle.NUMBER_XL,
                if (hidden) Royal.muted else scoreColor(totals[i]), Gravity.CENTER, 1).apply { textSize = kit.textSize(32f) })
        }, kit.spaced(6))
    }
    if (!hidden && totals.size == 2) {
        addView(kit.gap(4))
        addView(kit.text("اختلاف امتیاز  ${kit.n(kotlin.math.abs(totals[0] - totals[1]))}", TextStyle.LABEL_BOLD, Royal.goldLight, Gravity.CENTER))
    }
}

fun Screen.dashboardStats(items: List<Pair<String, String>>): View = kit.horizontal().apply {
    layoutParams = kit.spaced(12)
    items.forEachIndexed { i, (label, value) ->
        if (i > 0) addView(kit.hgap(8))
        addView(kit.weight(kit.panel(PanelStyle.FLAT, 10, 18f).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(kit.text(value, TextStyle.NUMBER_L, Royal.goldLight, Gravity.CENTER, 1))
            addView(kit.text(label, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 2))
        }))
    }
}

/** Decorative backplates have no listeners and never reveal a previous player's card. */
fun Screen.secretDeck(card: View): View = FrameLayout(host).apply {
    setPadding(kit.dp(15), kit.dp(16), kit.dp(15), kit.dp(16))
    for (angle in listOf(-4f, 4f)) addView(View(host).apply {
        background = PanelDrawable(kit.density, PanelStyle.RAISED, 26f)
        rotation = angle
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}
