package com.meysam.divanemtiaz

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

// Layout pieces of the second design language: icon chips, grouped lists, chip rails, banners,
// dashboard tiles and the podium. They read every colour and shape from the active style.

/** Rounded square tinted with [tint] holding an icon; the leading mark of rows, tools and sections. */
fun RoyalKit.iconChip(icon: RoyalIcon, size: Int = 36, tint: Int = Royal.gold): View = FrameLayout(context).apply {
    val t = ThemeEngine.spec
    background = GradientDrawable().apply {
        cornerRadius = dpf(size * 0.32f * t.radius)
        setColor(Royal.alpha(tint, if (t.light) 0.11f else 0.15f))
        setStroke(dp(1), Royal.alpha(tint, if (t.light) 0.20f else 0.26f))
    }
    addView(IconView(context, icon, tint), FrameLayout.LayoutParams((size * 0.54f * density).toInt(), (size * 0.54f * density).toInt(), Gravity.CENTER))
    layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
}

/** Background of a pressable row: transparent, tinted while pressed. */
private fun RoyalKit.pressable(radius: Float): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
        cornerRadius = dpf(radius)
        setColor(Royal.alpha(Royal.gold, if (ThemeEngine.spec.light) 0.08f else 0.10f))
    })
    addState(intArrayOf(), GradientDrawable().apply { setColor(0) })
}

/** One row of a grouped list: leading mark, title and caption, and a trailing control or a chevron. */
fun RoyalKit.listRow(
    leading: View?,
    title: String,
    caption: String? = null,
    trailing: View? = null,
    titleColor: Int = Royal.ivory,
    onClick: (() -> Unit)? = null
): View = horizontal().apply {
    setPadding(dp(10), dp(9), dp(10), dp(10))
    minimumHeight = dp(58)
    if (leading != null) {
        addView(leading)
        addView(hgap(12))
    }
    addView(vertical().apply {
        addView(text(title, TextStyle.BODY_BOLD, titleColor, maxLines = 2))
        if (!caption.isNullOrBlank()) addView(text(caption, TextStyle.CAPTION, Royal.muted, maxLines = 2))
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    if (trailing != null) {
        addView(hgap(8))
        addView(trailing)
    } else if (onClick != null) {
        addView(hgap(6))
        addView(icon(RoyalIcon.NEXT, Royal.dim, 16))
    }
    if (onClick != null) {
        background = pressable(14f)
        isClickable = true
        contentDescription = title
        setOnClickListener { tap(it); onClick() }
    }
}

/** Rows gathered in one card, separated by hairlines (the settings-style list of the modern styles). */
fun RoyalKit.listGroup(rows: List<View>, title: String? = null): View = vertical().apply {
    layoutParams = spaced(12)
    if (title != null) addView(text(title, TextStyle.LABEL_BOLD, Royal.muted).apply { setPadding(dp(6), 0, dp(6), dp(6)) })
    addView(vertical().apply {
        background = PanelDrawable(density, PanelStyle.NORMAL, 20f)
        setPadding(dp(5), dp(5), dp(5), dp(9))
        rows.forEachIndexed { i, row ->
            if (i > 0) addView(View(context).apply { setBackgroundColor(Royal.alpha(Royal.ivory, if (ThemeEngine.spec.light) 0.07f else 0.09f)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(1) / 2 + 1)).apply { marginStart = dp(58); marginEnd = dp(10) })
            addView(row, fill())
        }
    })
}

/** Filters that scroll sideways in one line; the selected chip is brought into view. */
fun RoyalKit.chipRail(chips: List<View>, selected: Int = -1, gap: Int = 6): View = HorizontalScrollView(context).apply {
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    val row = horizontal().apply {
        setPadding(dp(2), dp(2), dp(2), dp(4))
        chips.forEachIndexed { i, c ->
            if (i > 0) addView(hgap(gap))
            addView(c)
        }
    }
    addView(row)
    // A horizontal scroller starts at its left edge; in a right-to-left page the first chip is on the right.
    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
            if (row.width <= 0 || width <= 0) return
            removeOnLayoutChangeListener(this)
            val target = chips.getOrNull(selected)
            val x = if (target != null && target.width > 0) (target.left - (width - target.width) / 2).coerceIn(0, maxOf(0, row.width - width))
            else maxOf(0, row.width - width)
            scrollTo(x, 0)
        }
    })
}

/** Small value-over-label figure (dashboard counters). */
fun RoyalKit.stat(value: String, label: String): View = vertical(Gravity.CENTER_HORIZONTAL).apply {
    background = GradientDrawable().apply {
        cornerRadius = dpf(14f * ThemeEngine.spec.radius)
        setColor(Royal.alpha(Royal.ivory, if (ThemeEngine.spec.light) 0.045f else 0.06f))
        setStroke(dp(1), Royal.alpha(Royal.gold, 0.18f))
    }
    setPadding(dp(4), dp(6), dp(4), dp(8))
    addView(text(value, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER, 1))
    addView(text(label, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 1))
}

/** Dashboard tile of a game: seal, name and one line about it. */
fun RoyalKit.gameTile(seal: View, title: String, caption: String, badge: String? = null, onClick: () -> Unit): View = vertical().apply {
    background = PanelDrawable(density, PanelStyle.RAISED, 22f)
    setPadding(dp(12), dp(12), dp(12), dp(15))
    addView(horizontal().apply {
        addView(seal, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Royal.alpha(Royal.gold, if (ThemeEngine.spec.light) 0.10f else 0.14f)) }
            addView(IconView(context, RoyalIcon.NEXT, Royal.goldLight), FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { gravity = Gravity.TOP })
    })
    addView(gap(8))
    addView(text(title, TextStyle.HEADING, Royal.title, maxLines = 1))
    addView(text(caption, TextStyle.CAPTION, Royal.muted, maxLines = 2))
    if (badge != null) {
        addView(gap(6))
        addView(badge(badge, Royal.turquoise, true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    isClickable = true
    contentDescription = "شروع $title"
    setOnClickListener { tap(it); onClick() }
}

/** Shortcut of the dashboard: a tinted icon and a short name, no card. */
fun RoyalKit.toolTile(icon: RoyalIcon, label: String, onClick: () -> Unit): View = vertical(Gravity.CENTER_HORIZONTAL).apply {
    setPadding(0, dp(6), 0, dp(6))
    background = pressable(16f)
    addView(iconChip(icon, 46))
    addView(gap(5))
    addView(text(label, TextStyle.CAPTION, Royal.ivory, Gravity.CENTER, 2).apply { typeface = RoyalFonts.get(context, true) })
    isClickable = true
    contentDescription = label
    setOnClickListener { tap(it); onClick() }
}

/** One place of a ranking. */
class PodiumItem(val name: String, val avatar: Int, val value: String, val caption: String, val onClick: (() -> Unit)? = null)

/** The first three of a ranking on stepped pedestals, the winner raised and crowned in the middle. */
fun Screen.podium(items: List<PodiumItem>): View {
    val medals = if (ThemeEngine.spec.light) intArrayOf(0xFFD4A42E.toInt(), 0xFF95A3B5.toInt(), 0xFFC07F48.toInt()) else intArrayOf(0xFFE8C15E.toInt(), 0xFFC9D1DC.toInt(), 0xFFD49A66.toInt())
    return kit.horizontal(Gravity.BOTTOM).apply {
        layoutParams = kit.spaced(12)
        setPadding(kit.dp(2), kit.dp(6), kit.dp(2), 0)
        listOf(1, 0, 2).filter { it < items.size }.forEach { place ->
            val item = items[place]
            val first = place == 0
            addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
                if (first) addView(kit.icon(RoyalIcon.CROWN, medals[0], 28))
                val size = if (first) 70 else 56
                addView(FrameLayout(host).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Royal.alpha(medals[place], 0.10f)); setStroke(kit.dp(3), medals[place]) }
                    addView(kit.avatar(item.avatar, size), FrameLayout.LayoutParams(kit.dp(size), kit.dp(size), Gravity.CENTER))
                }, LinearLayout.LayoutParams(kit.dp(size + 10), kit.dp(size + 10)))
                addView(kit.gap(4))
                addView(kit.text(item.name, TextStyle.BODY_BOLD, Royal.ivory, Gravity.CENTER, 1))
                addView(kit.text(item.value, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER, 1))
                addView(kit.gap(6))
                addView(kit.vertical(Gravity.CENTER).apply {
                    background = PanelDrawable(kit.density, if (first) PanelStyle.RAISED else PanelStyle.NORMAL, 14f)
                    addView(kit.text(kit.n(place + 1), TextStyle.NUMBER_L, medals[place], Gravity.CENTER, 1))
                    addView(kit.text(item.caption, TextStyle.CAPTION, Royal.muted, Gravity.CENTER, 1))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(if (first) 110 else if (place == 1) 98 else 92)))
                if (item.onClick != null) {
                    isClickable = true
                    contentDescription = item.name
                    setOnClickListener { kit.tap(it); item.onClick.invoke() }
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, if (first) 1.15f else 1f).apply {
                marginStart = kit.dp(4); marginEnd = kit.dp(4)
            })
        }
    }
}

/** Headline banner of a game hub: a large seal on a glow, the name and a line about it, and an optional call to action. */
fun Screen.hubBanner(seal: View, title: String, caption: String, action: View? = null): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
    background = PanelDrawable(kit.density, PanelStyle.RAISED, 26f)
    setPadding(kit.dp(16), kit.dp(18), kit.dp(16), kit.dp(18))
    layoutParams = kit.spaced(12)
    addView(FrameLayout(host).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = kit.dpf(62f)
            colors = intArrayOf(Royal.alpha(Royal.gold, if (ThemeEngine.spec.light) 0.22f else 0.30f), 0)
        }
        addView(seal, FrameLayout.LayoutParams(kit.dp(92), kit.dp(92), Gravity.CENTER))
    }, LinearLayout.LayoutParams(kit.dp(130), kit.dp(120)))
    addView(kit.text(title, TextStyle.DISPLAY, Royal.title, Gravity.CENTER))
    addView(kit.text(caption, TextStyle.LABEL, Royal.muted, Gravity.CENTER))
    if (action != null) {
        addView(kit.gap(14))
        addView(action, kit.fill())
    }
}

/** Compact card to go back to a game in progress: seal, what it is, and a round play button. */
fun Screen.resumeStrip(seal: View, title: String, caption: String, onOpen: () -> Unit): View = kit.horizontal().apply {
    background = PanelDrawable(kit.density, PanelStyle.SELECTED, 20f)
    setPadding(kit.dp(12), kit.dp(10), kit.dp(10), kit.dp(13))
    layoutParams = kit.spaced(12)
    addView(seal, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)))
    addView(kit.hgap(10))
    addView(kit.vertical().apply {
        addView(kit.text(title, TextStyle.BODY_BOLD, Royal.turquoiseLight, maxLines = 1))
        addView(kit.text(caption, TextStyle.CAPTION, Royal.muted, maxLines = 2))
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    addView(kit.hgap(8))
    addView(kit.iconButton(RoyalIcon.PLAY, "ادامهٔ داوری", ButtonKind.SUCCESS, 48) { onOpen() })
    isClickable = true
    contentDescription = title
    setOnClickListener { kit.tap(it); onOpen() }
}
