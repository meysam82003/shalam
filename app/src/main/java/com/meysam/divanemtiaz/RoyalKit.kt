package com.meysam.divanemtiaz

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

enum class TextStyle(val sp: Float, val bold: Boolean) {
    DISPLAY(26f, true),
    TITLE(19f, true),
    HEADING(16f, true),
    BODY(14f, false),
    BODY_BOLD(14f, true),
    LABEL(12.5f, false),
    LABEL_BOLD(12.5f, true),
    CAPTION(11f, false),
    NUMBER_L(21f, true),
    NUMBER_XL(27f, true)
}

data class DialogAction(val label: String, val kind: ButtonKind = ButtonKind.SECONDARY, val dismiss: Boolean = true, val onClick: () -> Unit = {})

/** Builders for every widget of the design system; all screens are composed from these. */
class RoyalKit(val context: Context, private val settingsProvider: () -> AppSettings) {
    val settings: AppSettings get() = settingsProvider()
    private val metrics get() = context.resources.displayMetrics

    /** Interface scale: the «اندازهٔ نمایش» setting, slightly reduced on narrow phones. */
    val scale: Float
        get() {
            val widthDp = metrics.widthPixels / metrics.density
            val narrow = when {
                widthDp < 340f -> 0.88f
                widthDp < 380f -> 0.94f
                else -> 1f
            }
            return settings.general.uiScale.coerceIn(70, 140) / 100f * narrow
        }

    /** Density used by every widget and drawable, so the whole interface follows [scale]. */
    val density: Float get() = metrics.density * scale

    fun textSize(sp: Float): Float = (sp + if (settings.general.largeText) 2f else 0f) * scale

    fun dp(value: Int): Int = (value * density).toInt()
    fun dpf(value: Float): Float = value * density

    fun n(value: Any): String = PersianText.digits(value, settings.general.persianDigits)
    fun signed(value: Int): String = PersianText.signed(value, settings.general.persianDigits)

    fun tap(view: View) {
        if (settings.general.haptic) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun text(
        value: CharSequence,
        style: TextStyle = TextStyle.BODY,
        color: Int = Royal.ivory,
        gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
        maxLines: Int = 0
    ): TextView = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize(style.sp))
        typeface = RoyalFonts.get(context, style.bold)
        this.gravity = gravity
        textDirection = View.TEXT_DIRECTION_RTL
        includeFontPadding = true
        setLineSpacing(0f, 1.12f)
        if (maxLines > 0) {
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    fun vertical(gravity: Int = Gravity.NO_GRAVITY): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        this.gravity = gravity
    }

    fun horizontal(gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    fun gap(height: Int): View = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

    fun hgap(width: Int): View = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }

    fun weight(view: View, weight: Float = 1f, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): View = view.apply {
        layoutParams = LinearLayout.LayoutParams(0, height, weight)
    }

    fun fill(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun spaced(bottom: Int = 10): LinearLayout.LayoutParams = fill().apply { bottomMargin = dp(bottom) }

    fun panel(style: PanelStyle = PanelStyle.NORMAL, padding: Int = 14, radius: Float = 18f): LinearLayout = vertical().apply {
        background = PanelDrawable(density, style, radius)
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding + 3))
    }

    fun divider(): View = RoyalDivider(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)) }

    fun icon(icon: RoyalIcon, color: Int = Royal.goldLight, size: Int = 22): IconView = IconView(context, icon, color).apply {
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    fun avatar(index: Int, size: Int): EmblemAvatarView = EmblemAvatarView(context, index).apply {
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        contentDescription = Emblems.names[((index % Emblems.COUNT) + Emblems.COUNT) % Emblems.COUNT]
    }

    fun section(title: String, icon: RoyalIcon? = null, trailing: View? = null): View = vertical().apply {
        layoutParams = fill().apply { topMargin = dp(10); bottomMargin = dp(6) }
        addView(horizontal().apply {
            if (icon != null) {
                addView(icon(icon, Royal.gold, 18))
                addView(hgap(8))
            }
            addView(weight(text(title, TextStyle.HEADING, Royal.goldLight)))
            if (trailing != null) addView(trailing)
        })
        addView(divider())
    }

    fun button(
        label: String,
        kind: ButtonKind = ButtonKind.PRIMARY,
        icon: RoyalIcon? = null,
        height: Int = 46,
        onClick: () -> Unit
    ): LinearLayout = horizontal(Gravity.CENTER).apply {
        background = ButtonDrawable(density, kind)
        minimumHeight = dp(height)
        isClickable = true
        isFocusable = true
        val color = ButtonDrawable.textColor(kind)
        if (icon != null) {
            addView(icon(icon, color, 18))
            addView(hgap(6))
        }
        addView(text(label, TextStyle.BODY_BOLD, color, Gravity.CENTER, 2))
        contentDescription = label
        setOnClickListener { tap(it); onClick() }
    }

    fun iconButton(icon: RoyalIcon, description: String, kind: ButtonKind = ButtonKind.SECONDARY, size: Int = 44, onClick: () -> Unit): View =
        FrameLayout(context).apply {
            background = ButtonDrawable(density, kind, size / 2.2f)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            isClickable = true
            contentDescription = description
            addView(IconView(context, icon, ButtonDrawable.textColor(kind)), FrameLayout.LayoutParams(dp(size / 2), dp(size / 2), Gravity.CENTER).apply {
                bottomMargin = dp(1)
            })
            setOnClickListener { tap(it); onClick() }
        }

    fun chip(label: String, selected: Boolean, selectedKind: ButtonKind = ButtonKind.CHIP_SELECTED, onClick: () -> Unit): TextView =
        text(label, TextStyle.LABEL_BOLD, ButtonDrawable.textColor(if (selected) selectedKind else ButtonKind.CHIP), Gravity.CENTER, 2).apply {
            background = ButtonDrawable(density, if (selected) selectedKind else ButtonKind.CHIP, 12f)
            minHeight = dp(40)
            minWidth = dp(40)
            isClickable = true
            contentDescription = label
            setOnClickListener { tap(it); onClick() }
        }

    fun badge(label: String, color: Int = Royal.gold, filled: Boolean = false): TextView =
        text(label, TextStyle.CAPTION, if (filled) Royal.night else color, Gravity.CENTER, 1).apply {
            typeface = RoyalFonts.get(context, true)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpf(10f)
                if (filled) setColor(color) else {
                    setColor(Royal.alpha(color, 0.12f))
                    setStroke(dp(1), Royal.alpha(color, 0.7f))
                }
            }
            setPadding(dp(8), dp(1), dp(8), dp(2))
        }

    fun progress(fraction: Float, color: Int = Royal.gold, height: Int = 8): View = View(context).apply {
        background = ProgressDrawable(density, fraction, color)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
    }

    fun field(hint: String, value: String = "", numeric: Boolean = false, signed: Boolean = false): EditText = EditText(context).apply {
        this.hint = hint
        setText(value)
        setTextColor(Royal.ivory)
        setHintTextColor(Royal.dim)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize(15f))
        typeface = RoyalFonts.get(context, true)
        background = PanelDrawable(density, PanelStyle.FLAT, 14f)
        setPadding(dp(12), dp(8), dp(12), dp(11))
        minHeight = dp(46)
        setSingleLine(true)
        imeOptions = EditorInfo.IME_ACTION_DONE
        if (numeric) {
            inputType = InputType.TYPE_CLASS_NUMBER or if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
        } else {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
        }
    }

    fun grid(columns: Int, views: List<View>, gap: Int = 6): EqualGrid = EqualGrid(context, columns, dp(gap)).apply {
        views.forEach { addView(it) }
    }

    fun flow(views: List<View>, gap: Int = 6): FlowLayout = FlowLayout(context, dp(gap)).apply { views.forEach { addView(it) } }

    fun settingRow(title: String, subtitle: String?, control: View, onClick: (() -> Unit)? = null): LinearLayout =
        horizontal().apply {
            background = PanelDrawable(density, PanelStyle.FLAT, 16f)
            setPadding(dp(12), dp(9), dp(12), dp(12))
            layoutParams = spaced(6)
            addView(vertical().apply {
                addView(text(title, TextStyle.BODY_BOLD, Royal.ivory))
                if (!subtitle.isNullOrBlank()) addView(text(subtitle, TextStyle.CAPTION, Royal.muted))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(hgap(10))
            addView(control)
            if (onClick != null) {
                isClickable = true
                setOnClickListener { tap(it); onClick() }
            }
        }

    fun switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val toggle = RoyalSwitch(context, checked).apply {
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(36))
            contentDescription = title
            this.onChange = { tap(this); onChange(it) }
        }
        return settingRow(title, subtitle, toggle) { toggle.setChecked(!toggle.checked, true) }
    }

    /** Numeric setting: −/+ buttons, and a tap on the value opens direct entry. */
    fun stepperRow(
        title: String,
        subtitle: String?,
        value: Int,
        min: Int,
        max: Int,
        step: Int,
        format: (Int) -> String = { n(it) },
        onChange: (Int) -> Unit
    ): View {
        var current = value
        val label = text(format(current), TextStyle.HEADING, Royal.goldLight, Gravity.CENTER, 1)
        fun set(v: Int) {
            current = v.coerceIn(min, max)
            label.text = format(current)
            onChange(current)
        }
        val control = horizontal().apply {
            addView(iconButton(RoyalIcon.PLUS, "افزایش $title", ButtonKind.CHIP, 34) { set(current + step) })
            addView(label, LinearLayout.LayoutParams(dp(68), dp(36)))
            addView(iconButton(RoyalIcon.MINUS, "کاهش $title", ButtonKind.CHIP, 34) { set(current - step) })
        }
        label.setOnClickListener {
            numberPrompt(title, current, min < 0, "از ${n(min)} تا ${n(max)}") { set(it) }
        }
        return vertical().apply {
            background = PanelDrawable(density, PanelStyle.FLAT, 16f)
            setPadding(dp(12), dp(9), dp(12), dp(12))
            layoutParams = spaced(6)
            addView(horizontal().apply {
                addView(vertical().apply {
                    addView(text(title, TextStyle.BODY_BOLD, Royal.ivory))
                    if (!subtitle.isNullOrBlank()) addView(text(subtitle, TextStyle.CAPTION, Royal.muted))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(hgap(6))
                addView(control)
            })
        }
    }

    fun choiceRow(title: String, subtitle: String?, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit): View {
        val box = vertical().apply {
            background = PanelDrawable(density, PanelStyle.FLAT, 16f)
            setPadding(dp(12), dp(9), dp(12), dp(12))
            layoutParams = spaced(6)
        }
        box.addView(text(title, TextStyle.BODY_BOLD, Royal.ivory))
        if (!subtitle.isNullOrBlank()) box.addView(text(subtitle, TextStyle.CAPTION, Royal.muted))
        box.addView(gap(6))
        val chips = mutableListOf<TextView>()
        var current = selected
        fun restyle() {
            chips.forEachIndexed { i, chip ->
                val on = options[i].first == current
                chip.background = ButtonDrawable(density, if (on) ButtonKind.CHIP_SELECTED else ButtonKind.CHIP, 12f)
                chip.setTextColor(ButtonDrawable.textColor(if (on) ButtonKind.CHIP_SELECTED else ButtonKind.CHIP))
            }
        }
        options.forEach { (key, label) ->
            chips += chip(label, key == current) {
                current = key
                restyle()
                onSelect(key)
            }.apply { setPadding(dp(10), dp(4), dp(10), dp(6)) }
        }
        box.addView(flow(chips))
        return box
    }

    fun dialog(title: String, message: String? = null, body: View? = null, actions: List<DialogAction>): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = panel(PanelStyle.RAISED, 16).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        content.addView(text(title, TextStyle.HEADING, Royal.goldLight, Gravity.CENTER))
        content.addView(divider())
        if (message != null) {
            content.addView(text(message, TextStyle.BODY, Royal.ivory, Gravity.CENTER))
            content.addView(gap(10))
        }
        if (body != null) {
            content.addView(ScrollView(context).apply {
                isFillViewport = false
                addView(body)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            content.addView(gap(12))
        }
        if (actions.isNotEmpty()) {
            val row = horizontal()
            actions.forEachIndexed { i, action ->
                if (i > 0) row.addView(hgap(8))
                row.addView(weight(button(action.label, action.kind, height = 44) {
                    if (action.dismiss) dialog.dismiss()
                    action.onClick()
                }))
            }
            content.addView(row)
        }
        dialog.setContentView(content)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = min(context.resources.displayMetrics.widthPixels - dp(32), dp(440))
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            // Keep tall dialogs inside the screen; the body scrolls instead.
            body?.let { content.post { limitDialogHeight(content) } }
            window.setDimAmount(0.72f)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        return dialog
    }

    private fun limitDialogHeight(content: LinearLayout) {
        val max = (context.resources.displayMetrics.heightPixels * 0.86f).toInt()
        if (content.height <= max) return
        val scroll = (0 until content.childCount).map { content.getChildAt(it) }.firstOrNull { it is ScrollView } ?: return
        val lp = scroll.layoutParams as LinearLayout.LayoutParams
        lp.height = (scroll.height - (content.height - max)).coerceAtLeast(dp(120))
        lp.weight = 0f
        scroll.layoutParams = lp
    }

    fun confirm(title: String, message: String, confirmLabel: String = "تأیید", danger: Boolean = false, onConfirm: () -> Unit) {
        dialog(
            title, message, null,
            listOf(
                DialogAction(confirmLabel, if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY, onClick = onConfirm),
                DialogAction("انصراف", ButtonKind.SECONDARY)
            )
        ).show()
    }

    fun numberPrompt(title: String, initial: Int?, signed: Boolean, hint: String = "", onValue: (Int) -> Unit) {
        val input = field(hint.ifBlank { title }, initial?.toString() ?: "", numeric = true, signed = signed)
        val error = text("", TextStyle.CAPTION, Royal.crimsonLight, Gravity.CENTER)
        lateinit var dialog: Dialog
        dialog = dialog(title, null, vertical().apply {
            addView(input, fill())
            addView(error)
        }, listOf(
            DialogAction("ثبت", ButtonKind.PRIMARY, dismiss = false) {
                val value = PersianText.parseInt(input.text.toString())
                if (value == null) error.text = "عدد معتبر وارد کنید" else {
                    dialog.dismiss()
                    onValue(value)
                }
            },
            DialogAction("انصراف")
        ))
        dialog.show()
        input.requestFocus()
        input.selectAll()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    fun textPrompt(title: String, initial: String, onValue: (String) -> Unit) {
        val input = field(title, initial)
        lateinit var dialog: Dialog
        dialog = dialog(title, null, vertical().apply { addView(input, fill()) }, listOf(
            DialogAction("ذخیره", ButtonKind.PRIMARY, dismiss = false) {
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    dialog.dismiss()
                    onValue(value)
                }
            },
            DialogAction("انصراف")
        ))
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/** Lays children out in equal-width columns, first child at the start (right in RTL). */
class EqualGrid(context: Context, private val columns: Int, private val gapPx: Int) : ViewGroup(context) {
    init { layoutDirection = View.LAYOUT_DIRECTION_RTL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cell = ((width - paddingLeft - paddingRight - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
        var height = paddingTop + paddingBottom
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            rowHeight = max(rowHeight, child.measuredHeight)
            if (i % columns == columns - 1 || i == childCount - 1) {
                height += rowHeight + if (i == childCount - 1) 0 else gapPx
                rowHeight = 0
            }
        }
        for (i in 0 until childCount step columns) {
            val rowMax = (i until min(i + columns, childCount)).maxOf { getChildAt(it).measuredHeight }
            (i until min(i + columns, childCount)).forEach {
                val child = getChildAt(it)
                if (child.measuredHeight != rowMax) {
                    child.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowMax, MeasureSpec.EXACTLY))
                }
            }
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val cell = ((width - paddingLeft - paddingRight - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        var y = paddingTop
        var i = 0
        while (i < childCount) {
            val rowEnd = min(i + columns, childCount)
            var rowHeight = 0
            for (j in i until rowEnd) {
                val child = getChildAt(j)
                val col = j - i
                val x = if (rtl) width - paddingRight - (col + 1) * cell - col * gapPx else paddingLeft + col * (cell + gapPx)
                child.layout(x, y, x + cell, y + child.measuredHeight)
                rowHeight = max(rowHeight, child.measuredHeight)
            }
            y += rowHeight + gapPx
            i = rowEnd
        }
    }
}

/** Wraps children onto new lines, starting at the right edge in RTL. */
class FlowLayout(context: Context, private val gapPx: Int) : ViewGroup(context) {
    init { layoutDirection = View.LAYOUT_DIRECTION_RTL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = width - paddingLeft - paddingRight
        var x = 0
        var y = paddingTop
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + child.measuredWidth > available) {
                x = 0
                y += lineHeight + gapPx
                lineHeight = 0
            }
            x += child.measuredWidth + gapPx
            lineHeight = max(lineHeight, child.measuredHeight)
        }
        setMeasuredDimension(width, y + lineHeight + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val available = width - paddingLeft - paddingRight
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        var x = 0
        var y = paddingTop
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x > 0 && x + child.measuredWidth > available) {
                x = 0
                y += lineHeight + gapPx
                lineHeight = 0
            }
            val left = if (rtl) width - paddingRight - x - child.measuredWidth else paddingLeft + x
            child.layout(left, y, left + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gapPx
            lineHeight = max(lineHeight, child.measuredHeight)
        }
    }
}
