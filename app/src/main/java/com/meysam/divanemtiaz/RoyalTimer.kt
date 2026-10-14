package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Circular countdown of the active style: a gradient arc that empties smoothly between ticks, a dial of
 * minute marks, a glowing head and the remaining time in the middle. The last seconds turn red and pulse.
 */
class RingTimerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private val matrix = Matrix()

    /** Full length of the countdown. */
    var totalMs: Long = 60_000L
        set(value) { field = value.coerceAtLeast(1L); invalidate() }

    /** Caption under the time; hidden on small rings. */
    var label: String = ""
        set(value) { field = value; invalidate() }

    /** Small rings (player rows) show only seconds and no dial. */
    var compact = false
        set(value) { field = value; invalidate() }

    /** Seconds from which the ring turns red. */
    var alertSeconds = 10

    /** Live running state (the screen's countdown); when set it overrides the flag given to [update]. */
    var runningSource: (() -> Boolean)? = null

    private var anchorMs = 60_000L
    private var anchorAt = 0L
    private var running = false

    /** Sets the remaining time; while [isRunning], the arc keeps emptying smoothly until the next update. */
    fun update(remainingMs: Long, isRunning: Boolean) {
        anchorMs = remainingMs.coerceAtLeast(0L)
        anchorAt = SystemClock.uptimeMillis()
        running = isRunning && remainingMs > 0
        invalidate()
    }

    private fun shownMs(): Long =
        if (isRunning()) (anchorMs - (SystemClock.uptimeMillis() - anchorAt)).coerceIn(0L, anchorMs) else anchorMs

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val t = ThemeEngine.spec
        val d = resources.displayMetrics.density
        val ms = shownMs()
        val seconds = ((ms + 999) / 1000).toInt()
        val alert = seconds <= alertSeconds && ms < totalMs
        val fraction = (ms.toFloat() / totalMs).coerceIn(0f, 1f)
        val cx = width / 2f
        val cy = height / 2f
        val stroke = size * if (compact) 0.11f else 0.068f
        val r = size / 2f - stroke * (if (compact) 0.75f else 1.9f)
        val colors = if (alert) intArrayOf(Royal.crimson, Royal.alert, Royal.crimson) else t.ring
        val lead = colors[colors.size / 2]

        // Soft halo behind the ring.
        if (!compact) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(cx, cy, size / 2f, intArrayOf(Royal.alpha(lead, if (t.light) 0.10f else 0.20f), Royal.alpha(lead, 0.04f), 0), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, size / 2f, paint)
            paint.shader = null
            // Face of the dial.
            paint.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.2f, intArrayOf(Royal.alpha(t.raised, 0.95f), Royal.alpha(t.raisedLow, 0.95f)), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r - stroke * 0.9f, paint)
            paint.shader = null
            // Minute marks.
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 60) {
                val a = Math.PI * 2 * i / 60 - Math.PI / 2
                val major = i % 5 == 0
                val r1 = r + stroke * 0.95f
                val r2 = r1 + stroke * (if (major) 0.75f else 0.4f)
                paint.strokeWidth = (if (major) 1.6f else 1f) * d
                paint.color = Royal.alpha(Royal.ivory, if (major) 0.28f else 0.13f)
                canvas.drawLine(cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(), cx + (cos(a) * r2).toFloat(), cy + (sin(a) * r2).toFloat(), paint)
            }
        }
        // Track.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke
        paint.color = t.ringTrack
        canvas.drawCircle(cx, cy, r, paint)
        // Progress arc with a glow.
        arc.set(cx - r, cy - r, cx + r, cy + r)
        val sweep = 360f * fraction
        if (sweep > 0.5f) {
            val gradient = SweepGradient(cx, cy, colors + colors[0], null)
            matrix.setRotate(-90f, cx, cy)
            gradient.setLocalMatrix(matrix)
            if (t.glow || !t.light) {
                floatArrayOf(2.6f, 1.7f).forEachIndexed { i, k ->
                    paint.shader = null
                    paint.strokeWidth = stroke * k
                    paint.color = Royal.alpha(lead, if (i == 0) 0.07f else 0.13f)
                    canvas.drawArc(arc, -90f, sweep, false, paint)
                }
            }
            paint.shader = gradient
            paint.strokeWidth = stroke
            canvas.drawArc(arc, -90f, sweep, false, paint)
            paint.shader = null
            // Head of the arc.
            val a = Math.toRadians((-90f + sweep).toDouble())
            val hx = cx + (cos(a) * r).toFloat()
            val hy = cy + (sin(a) * r).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = Royal.alpha(lead, 0.35f)
            canvas.drawCircle(hx, hy, stroke * 0.95f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(hx, hy, stroke * 0.42f, paint)
        }
        // Time in the middle.
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = RoyalFonts.get(context, true)
        val text = if (compact) PersianText.digits(seconds) else clockText(ms)
        val pulse = if (alert && isRunning()) 1f + 0.05f * sin(SystemClock.uptimeMillis() / 160.0).toFloat() else 1f
        paint.textSize = size * (if (compact) 0.34f else 0.235f) * pulse
        paint.color = if (alert) Royal.alert else Royal.title
        val fm = paint.fontMetrics
        val textY = if (compact || label.isBlank()) cy - (fm.ascent + fm.descent) / 2f else cy - (fm.ascent + fm.descent) / 2f - size * 0.04f
        canvas.drawText(text, cx, textY, paint)
        if (!compact && label.isNotBlank()) {
            paint.typeface = RoyalFonts.get(context, false)
            paint.textSize = size * 0.06f
            paint.color = Royal.muted
            canvas.drawText(label, cx, textY + size * 0.115f, paint)
        }
        if (isRunning()) postInvalidateOnAnimation()
    }

    private fun isRunning(): Boolean {
        val live = runningSource?.invoke() ?: running
        return live && anchorMs > 0
    }
}
