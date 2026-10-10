package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object ChartColors {
    val series = intArrayOf(
        Royal.gold, Royal.turquoise, Royal.crimsonLight, 0xFF8FB4F5.toInt(), 0xFFC6A2F2.toInt(), Royal.ivory
    )

    fun of(i: Int): Int = series[((i % series.size) + series.size) % series.size]
}

data class ChartSeries(val name: String, val color: Int, val values: List<Float>)

/** Line chart with a faint grid, gold axis labels, end dots and an optional legend row of names. */
class LineChartView(
    context: Context,
    private val series: List<ChartSeries>,
    private val digits: (Any) -> String,
    private val density: Float,
    private val xLabel: (Int) -> String = { digits(it) },
    private val fill: Boolean = false,
    private val includeZero: Boolean = true
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val count = series.maxOfOrNull { it.values.size } ?: 0
        if (count == 0) return
        val all = series.flatMap { it.values }
        var lo = if (includeZero) min(0f, all.minOrNull() ?: 0f) else (all.minOrNull() ?: 0f)
        var hi = if (includeZero) max(0f, all.maxOrNull() ?: 0f) else (all.maxOrNull() ?: 0f)
        if (!includeZero) { lo -= 10f; hi += 10f }
        if (hi - lo < 1f) { hi += 1f; lo -= if (lo < 0f) 1f else 0f }
        val step = niceStep((hi - lo) / 4f)
        lo = floor(lo / step) * step
        hi = ceil(hi / step) * step
        paint.typeface = RoyalFonts.get(context, false)
        paint.textSize = 10.5f * density
        val labelW = maxOf(paint.measureText(digits(lo.toInt())), paint.measureText(digits(hi.toInt())))
        val left = 6 * density
        val right = width - labelW - 10 * density
        val top = 8 * density
        val bottom = height - 18 * density
        val plotW = right - left
        val plotH = bottom - top
        fun x(i: Int) = if (count == 1) left + plotW / 2 else left + plotW * i / (count - 1)
        fun y(v: Float) = bottom - (v - lo) / (hi - lo) * plotH
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * 0.7f
        var g = lo
        while (g <= hi + 0.001f) {
            val yy = y(g)
            paint.color = Royal.alpha(Royal.gold, if (abs(g) < 0.001f) 0.55f else 0.16f)
            paint.pathEffect = if (abs(g) < 0.001f) null else DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
            canvas.drawLine(left, yy, right, yy, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            paint.color = Royal.dim
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(digits(g.toInt()), right + 6 * density, yy + 3.5f * density, paint)
            paint.style = Paint.Style.STROKE
            g += step
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = Royal.dim
        val every = max(1, ceil(count / 8f).toInt())
        for (i in 0 until count step every) canvas.drawText(xLabel(i), x(i), height - 4 * density, paint)
        series.forEach { s ->
            if (s.values.isEmpty()) return@forEach
            val path = Path()
            s.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            if (fill && series.size == 1) {
                val area = Path(path).apply {
                    lineTo(x(s.values.lastIndex), bottom)
                    lineTo(x(0), bottom)
                    close()
                }
                paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                paint.shader = LinearGradient(0f, top, 0f, bottom, Royal.alpha(s.color, 0.35f), 0, Shader.TileMode.CLAMP)
                canvas.drawPath(area, paint)
                paint.shader = null
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f * density
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = s.color
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            val last = s.values.lastIndex
            canvas.drawCircle(x(last), y(s.values[last]), 3.6f * density, paint)
            paint.color = Royal.night
            canvas.drawCircle(x(last), y(s.values[last]), 1.6f * density, paint)
        }
    }

    private fun niceStep(raw: Float): Float {
        if (raw <= 0f) return 1f
        val mag = 10f.pow(floor(log10(raw)))
        val n = raw / mag
        val nice = when {
            n <= 1f -> 1f
            n <= 2f -> 2f
            n <= 5f -> 5f
            else -> 10f
        }
        return max(1f, nice * mag)
    }
}

/** Win / draw / loss ring with the win rate in the middle. */
class DonutView(
    context: Context,
    private val parts: List<Pair<Int, Int>>,
    private val center: String,
    private val caption: String,
    private val density: Float
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val stroke = size * 0.13f
        val rect = RectF((width - size) / 2 + stroke, (height - size) / 2 + stroke, (width + size) / 2 - stroke, (height + size) / 2 - stroke)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = Royal.alpha(Royal.night, 0.8f)
        canvas.drawArc(rect, 0f, 360f, false, paint)
        val total = parts.sumOf { it.first }
        if (total > 0) {
            var start = -90f
            paint.strokeCap = Paint.Cap.BUTT
            parts.forEach { (value, color) ->
                if (value <= 0) return@forEach
                val sweep = 360f * value / total
                paint.color = color
                canvas.drawArc(rect, start, sweep - if (parts.count { it.first > 0 } > 1) 1.5f else 0f, false, paint)
                start += sweep
            }
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = RoyalFonts.get(context, true)
        paint.color = Royal.goldLight
        paint.textSize = size * 0.2f
        canvas.drawText(center, width / 2f, height / 2f + size * 0.04f, paint)
        paint.typeface = RoyalFonts.get(context, false)
        paint.color = Royal.muted
        paint.textSize = size * 0.09f
        canvas.drawText(caption, width / 2f, height / 2f + size * 0.17f, paint)
    }
}
