package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.TextView
import kotlin.math.min

/**
 * Visual system for Divan Emtiaz v3.
 * Intentionally self-contained: no dependency on the legacy RoyalUi/assets.
 */
object DivanTheme {
    val bg = Color.rgb(7, 17, 29)
    val surface = Color.rgb(16, 30, 45)
    val surfaceHigh = Color.rgb(24, 42, 59)
    val ivory = Color.rgb(244, 235, 216)
    val gold = Color.rgb(214, 178, 97)
    val goldSoft = Color.rgb(174, 145, 81)
    val muted = Color.rgb(153, 169, 181)
    val line = Color.rgb(47, 65, 82)
    val emerald = Color.rgb(27, 91, 73)
    val crimson = Color.rgb(113, 50, 58)
    val blue = Color.rgb(43, 88, 120)
    val warning = Color.rgb(174, 121, 57)
    val danger = Color.rgb(126, 47, 54)
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun divanShape(
    context: Context,
    fill: Int = DivanTheme.surface,
    stroke: Int = DivanTheme.line,
    radiusDp: Int = 18,
    strokeDp: Int = 1
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(fill)
    cornerRadius = context.dp(radiusDp).toFloat()
    if (strokeDp > 0) setStroke(context.dp(strokeDp), stroke)
}

object DivanText {
    private const val FA = "۰۱۲۳۴۵۶۷۸۹"
    private const val EN = "0123456789"

    fun fa(value: Any, enabled: Boolean = true): String {
        val raw = value.toString()
        if (!enabled) return raw
        var result = raw
        EN.forEachIndexed { i, c -> result = result.replace(c, FA[i]) }
        return result
    }

    fun latin(raw: String): String {
        var result = raw
        FA.forEachIndexed { i, c -> result = result.replace(c, EN[i]) }
        return result
    }

    fun signed(value: Int, persianDigits: Boolean = true): String {
        val raw = when {
            value > 0 -> "+$value"
            else -> value.toString()
        }
        return fa(raw, persianDigits)
    }

    fun initials(label: String): String {
        val clean = label.trim()
        if (clean.isBlank()) return "د"
        val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
            else -> clean.take(2)
        }
    }
}

fun TextView.useDivanTypography(bold: Boolean = false) {
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    includeFontPadding = false
    gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
    textDirection = View.TEXT_DIRECTION_RTL
}

/** A lightweight generated avatar/seal; removes the need for old image atlases. */
class SealAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var label: String = "دیوان"
        set(value) { field = value; invalidate() }
    var variant: Int = 0
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val palette = intArrayOf(
        DivanTheme.emerald, DivanTheme.crimson, DivanTheme.blue,
        Color.rgb(95, 72, 119), Color.rgb(126, 91, 48), Color.rgb(46, 92, 102)
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = context.dp(64)
        setMeasuredDimension(resolveSize(desired, widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * .43f
        paint.style = Paint.Style.FILL
        paint.color = palette[kotlin.math.abs(variant) % palette.size]
        canvas.drawCircle(cx, cy, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(2).toFloat()
        paint.color = DivanTheme.gold
        canvas.drawCircle(cx, cy, r, paint)
        paint.strokeWidth = context.dp(1).toFloat()
        canvas.drawCircle(cx, cy, r * .78f, paint)

        paint.style = Paint.Style.FILL
        paint.color = DivanTheme.ivory
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = r * .58f
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(DivanText.initials(label), cx, baseline, paint)
    }
}

/** Minimal Persian-inspired divider: two lines and one central diamond. */
class DivanDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DivanTheme.goldSoft
        strokeWidth = context.dp(1).toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(240), widthMeasureSpec), resolveSize(context.dp(22), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val cx = width / 2f
        val gap = context.dp(16).toFloat()
        canvas.drawLine(0f, cy, cx - gap, cy, paint)
        canvas.drawLine(cx + gap, cy, width.toFloat(), cy, paint)
        val d = context.dp(5).toFloat()
        val path = android.graphics.Path().apply {
            moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close()
        }
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }
}
