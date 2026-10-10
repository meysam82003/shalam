package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class RoyalIcon {
    BACK, NEXT, HOME, SETTINGS, HISTORY, RULES, PLAYERS, PLUS, MINUS, CHECK, CLOSE, EDIT, TRASH,
    EYE, EYE_OFF, CROWN, TROPHY, TIMER, CHART, STAR, PLAY, UNDO, FLAG, WARNING, SLIDERS, MENU,
    SPADE, HEART, DIAMOND, CLUB, SWAP, USER, SHARE, CARDS, SAVE, LEAGUE
}

/** Vector icons drawn on a 24-unit grid, so no image resource is needed. */
object RoyalIcons {
    private val cache = HashMap<RoyalIcon, Pair<Path, Boolean>>()

    fun draw(canvas: Canvas, icon: RoyalIcon, bounds: RectF, color: Int, paint: Paint, stroke: Float) {
        val (path, filled) = cache.getOrPut(icon) { build(icon) }
        val matrix = Matrix().apply { setRectToRect(RectF(0f, 0f, 24f, 24f), bounds, Matrix.ScaleToFit.CENTER) }
        val scaled = Path(path).apply { transform(matrix) }
        paint.shader = null
        paint.color = color
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        if (filled) {
            paint.style = Paint.Style.FILL
            canvas.drawPath(scaled, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            canvas.drawPath(scaled, paint)
        }
    }

    private fun build(icon: RoyalIcon): Pair<Path, Boolean> {
        val p = Path()
        var filled = false
        when (icon) {
            RoyalIcon.BACK -> { p.moveTo(9f, 5f); p.lineTo(16f, 12f); p.lineTo(9f, 19f) }
            RoyalIcon.NEXT -> { p.moveTo(15f, 5f); p.lineTo(8f, 12f); p.lineTo(15f, 19f) }
            RoyalIcon.HOME -> {
                p.moveTo(3.5f, 11.5f); p.lineTo(12f, 4f); p.lineTo(20.5f, 11.5f)
                p.moveTo(6f, 10f); p.lineTo(6f, 20f); p.lineTo(18f, 20f); p.lineTo(18f, 10f)
                p.moveTo(10f, 20f); p.lineTo(10f, 14.5f); p.lineTo(14f, 14.5f); p.lineTo(14f, 20f)
            }
            RoyalIcon.SETTINGS -> {
                p.addCircle(12f, 12f, 3.2f, Path.Direction.CW)
                p.addCircle(12f, 12f, 7f, Path.Direction.CW)
                for (i in 0 until 8) {
                    val a = Math.PI / 4 * i
                    p.moveTo(12f + (7f * cos(a)).toFloat(), 12f + (7f * sin(a)).toFloat())
                    p.lineTo(12f + (9.8f * cos(a)).toFloat(), 12f + (9.8f * sin(a)).toFloat())
                }
            }
            RoyalIcon.HISTORY -> {
                p.addCircle(12f, 12f, 8.5f, Path.Direction.CW)
                p.moveTo(12f, 7f); p.lineTo(12f, 12f); p.lineTo(15.5f, 14.5f)
            }
            RoyalIcon.RULES -> {
                p.moveTo(12f, 6.5f); p.cubicTo(9f, 4.8f, 5.6f, 4.6f, 3f, 5.6f); p.lineTo(3f, 19f)
                p.cubicTo(5.6f, 18f, 9f, 18.2f, 12f, 19.8f); p.cubicTo(15f, 18.2f, 18.4f, 18f, 21f, 19f)
                p.lineTo(21f, 5.6f); p.cubicTo(18.4f, 4.6f, 15f, 4.8f, 12f, 6.5f); p.lineTo(12f, 19.8f)
            }
            RoyalIcon.PLAYERS -> {
                p.addCircle(9f, 8.5f, 3.2f, Path.Direction.CW)
                p.moveTo(3f, 19.5f); p.cubicTo(3.5f, 15.5f, 6f, 13.5f, 9f, 13.5f); p.cubicTo(12f, 13.5f, 14.5f, 15.5f, 15f, 19.5f)
                p.addCircle(16.5f, 9f, 2.6f, Path.Direction.CW)
                p.moveTo(15.5f, 13.8f); p.cubicTo(18.5f, 13.6f, 20.6f, 15.5f, 21f, 18.5f)
            }
            RoyalIcon.USER -> {
                p.addCircle(12f, 8.5f, 3.6f, Path.Direction.CW)
                p.moveTo(5f, 20f); p.cubicTo(5.5f, 15.5f, 8.5f, 13.6f, 12f, 13.6f); p.cubicTo(15.5f, 13.6f, 18.5f, 15.5f, 19f, 20f)
            }
            RoyalIcon.PLUS -> { p.moveTo(12f, 5f); p.lineTo(12f, 19f); p.moveTo(5f, 12f); p.lineTo(19f, 12f) }
            RoyalIcon.MINUS -> { p.moveTo(5f, 12f); p.lineTo(19f, 12f) }
            RoyalIcon.CHECK -> { p.moveTo(4.5f, 12.5f); p.lineTo(9.5f, 17.5f); p.lineTo(19.5f, 6.5f) }
            RoyalIcon.CLOSE -> { p.moveTo(6f, 6f); p.lineTo(18f, 18f); p.moveTo(18f, 6f); p.lineTo(6f, 18f) }
            RoyalIcon.EDIT -> {
                p.moveTo(4f, 20f); p.lineTo(4.8f, 15.8f); p.lineTo(15.5f, 5.1f); p.lineTo(18.9f, 8.5f); p.lineTo(8.2f, 19.2f); p.close()
                p.moveTo(13.4f, 7.2f); p.lineTo(16.8f, 10.6f)
            }
            RoyalIcon.TRASH -> {
                p.moveTo(4f, 6.5f); p.lineTo(20f, 6.5f); p.moveTo(9f, 6.5f); p.lineTo(9.6f, 4f); p.lineTo(14.4f, 4f); p.lineTo(15f, 6.5f)
                p.moveTo(6f, 6.5f); p.lineTo(7f, 20f); p.lineTo(17f, 20f); p.lineTo(18f, 6.5f)
                p.moveTo(10f, 10f); p.lineTo(10f, 16.5f); p.moveTo(14f, 10f); p.lineTo(14f, 16.5f)
            }
            RoyalIcon.EYE -> {
                p.moveTo(2.5f, 12f); p.cubicTo(5.5f, 6.5f, 18.5f, 6.5f, 21.5f, 12f); p.cubicTo(18.5f, 17.5f, 5.5f, 17.5f, 2.5f, 12f); p.close()
                p.addCircle(12f, 12f, 3f, Path.Direction.CW)
            }
            RoyalIcon.EYE_OFF -> {
                p.moveTo(2.5f, 12f); p.cubicTo(5.5f, 6.5f, 18.5f, 6.5f, 21.5f, 12f); p.cubicTo(18.5f, 17.5f, 5.5f, 17.5f, 2.5f, 12f); p.close()
                p.moveTo(4f, 4f); p.lineTo(20f, 20f)
            }
            RoyalIcon.CROWN -> {
                p.moveTo(4f, 18f); p.lineTo(3f, 8f); p.lineTo(8f, 12f); p.lineTo(12f, 5f); p.lineTo(16f, 12f); p.lineTo(21f, 8f); p.lineTo(20f, 18f); p.close()
                p.addRect(4f, 19f, 20f, 21f, Path.Direction.CW)
                filled = true
            }
            RoyalIcon.TROPHY -> {
                p.moveTo(7f, 4f); p.lineTo(17f, 4f); p.lineTo(17f, 9f); p.cubicTo(17f, 12.5f, 14.5f, 14.5f, 12f, 14.5f); p.cubicTo(9.5f, 14.5f, 7f, 12.5f, 7f, 9f); p.close()
                p.moveTo(7f, 6f); p.cubicTo(3.5f, 6f, 3.5f, 11f, 7.5f, 11f)
                p.moveTo(17f, 6f); p.cubicTo(20.5f, 6f, 20.5f, 11f, 16.5f, 11f)
                p.moveTo(12f, 14.5f); p.lineTo(12f, 18f); p.moveTo(8f, 20f); p.lineTo(16f, 20f); p.moveTo(9f, 18f); p.lineTo(15f, 18f)
            }
            RoyalIcon.TIMER -> {
                p.addCircle(12f, 13.5f, 7.5f, Path.Direction.CW)
                p.moveTo(12f, 13.5f); p.lineTo(12f, 9.5f); p.moveTo(10f, 3.5f); p.lineTo(14f, 3.5f); p.moveTo(12f, 3.5f); p.lineTo(12f, 6f)
            }
            RoyalIcon.CHART -> {
                p.moveTo(4f, 20f); p.lineTo(20f, 20f)
                p.moveTo(7f, 17f); p.lineTo(7f, 12f); p.moveTo(12f, 17f); p.lineTo(12f, 6f); p.moveTo(17f, 17f); p.lineTo(17f, 9.5f)
            }
            RoyalIcon.STAR -> { p.set(RoyalShapes.star8Path(12f, 12f, 9.5f)); filled = true }
            RoyalIcon.PLAY -> { p.moveTo(17f, 5f); p.lineTo(17f, 19f); p.lineTo(6f, 12f); p.close(); filled = true }
            RoyalIcon.UNDO -> {
                p.moveTo(15f, 6f); p.lineTo(19f, 10f); p.lineTo(15f, 14f)
                p.moveTo(19f, 10f); p.lineTo(10f, 10f); p.cubicTo(6f, 10f, 4.5f, 13f, 4.5f, 15f); p.cubicTo(4.5f, 17.5f, 6.5f, 19.5f, 10f, 19.5f); p.lineTo(13f, 19.5f)
            }
            RoyalIcon.FLAG -> {
                p.moveTo(18f, 21f); p.lineTo(18f, 3.5f)
                p.moveTo(18f, 4.5f); p.lineTo(6f, 4.5f); p.lineTo(8.5f, 8.5f); p.lineTo(6f, 12.5f); p.lineTo(18f, 12.5f)
            }
            RoyalIcon.WARNING -> {
                p.moveTo(12f, 3.5f); p.lineTo(21.5f, 20f); p.lineTo(2.5f, 20f); p.close()
                p.moveTo(12f, 9.5f); p.lineTo(12f, 14f); p.moveTo(12f, 17f); p.lineTo(12f, 17.2f)
            }
            RoyalIcon.SLIDERS -> {
                p.moveTo(4f, 7f); p.lineTo(20f, 7f); p.moveTo(4f, 17f); p.lineTo(20f, 17f)
                p.addCircle(15f, 7f, 2.3f, Path.Direction.CW); p.addCircle(9f, 17f, 2.3f, Path.Direction.CW)
            }
            RoyalIcon.MENU -> {
                p.addCircle(12f, 5.5f, 1.6f, Path.Direction.CW); p.addCircle(12f, 12f, 1.6f, Path.Direction.CW); p.addCircle(12f, 18.5f, 1.6f, Path.Direction.CW)
                filled = true
            }
            RoyalIcon.SWAP -> {
                p.moveTo(4f, 8f); p.lineTo(19f, 8f); p.moveTo(15f, 4f); p.lineTo(19f, 8f); p.lineTo(15f, 12f)
                p.moveTo(20f, 16f); p.lineTo(5f, 16f); p.moveTo(9f, 12f); p.lineTo(5f, 16f); p.lineTo(9f, 20f)
            }
            RoyalIcon.SHARE -> {
                p.addCircle(17.5f, 5.5f, 2.6f, Path.Direction.CW)
                p.addCircle(6.5f, 12f, 2.6f, Path.Direction.CW)
                p.addCircle(17.5f, 18.5f, 2.6f, Path.Direction.CW)
                p.moveTo(8.8f, 10.7f); p.lineTo(15.2f, 6.8f)
                p.moveTo(8.8f, 13.3f); p.lineTo(15.2f, 17.2f)
            }
            RoyalIcon.CARDS -> {
                p.addRoundRect(RectF(3.5f, 6.5f, 13.5f, 20.5f), 2f, 2f, Path.Direction.CW)
                p.moveTo(8f, 3.5f); p.lineTo(18.5f, 3.5f); p.quadTo(20.5f, 3.5f, 20.5f, 5.5f); p.lineTo(20.5f, 17.5f)
            }
            RoyalIcon.SAVE -> {
                p.moveTo(12f, 4f); p.lineTo(12f, 15f); p.moveTo(7.5f, 10.5f); p.lineTo(12f, 15f); p.lineTo(16.5f, 10.5f)
                p.moveTo(4.5f, 16f); p.lineTo(4.5f, 20f); p.lineTo(19.5f, 20f); p.lineTo(19.5f, 16f)
            }
            RoyalIcon.LEAGUE -> {
                p.addRect(3f, 4.5f, 8f, 8.5f, Path.Direction.CW)
                p.addRect(3f, 15.5f, 8f, 19.5f, Path.Direction.CW)
                p.moveTo(8f, 6.5f); p.lineTo(11f, 6.5f); p.lineTo(11f, 17.5f); p.lineTo(8f, 17.5f)
                p.moveTo(11f, 12f); p.lineTo(14f, 12f)
                p.addRect(14f, 10f, 20f, 14f, Path.Direction.CW)
            }
            RoyalIcon.SPADE -> { p.set(SuitShapes.spade(24f)); filled = true }
            RoyalIcon.HEART -> { p.set(SuitShapes.heart(24f)); filled = true }
            RoyalIcon.DIAMOND -> { p.set(SuitShapes.diamond(24f)); filled = true }
            RoyalIcon.CLUB -> { p.set(SuitShapes.club(24f)); filled = true }
        }
        return p to filled
    }

    fun forSuit(suit: Int): RoyalIcon? = when (suit) {
        Suit.SPADE -> RoyalIcon.SPADE
        Suit.HEART -> RoyalIcon.HEART
        Suit.DIAMOND -> RoyalIcon.DIAMOND
        Suit.CLUB -> RoyalIcon.CLUB
        else -> null
    }

    fun suitColor(suit: Int): Int = if (suit == Suit.HEART || suit == Suit.DIAMOND) Royal.crimsonLight else Royal.ivory
}

object SuitShapes {
    fun spade(s: Float): Path = Path().apply {
        val k = s / 24f
        moveTo(12f * k, 2.5f * k)
        cubicTo(16f * k, 7f * k, 21f * k, 9.5f * k, 21f * k, 13.5f * k)
        cubicTo(21f * k, 16.5f * k, 18.5f * k, 18.5f * k, 15.8f * k, 18f * k)
        cubicTo(14.6f * k, 17.8f * k, 13.6f * k, 17f * k, 13f * k, 16f * k)
        lineTo(14.5f * k, 21.5f * k)
        lineTo(9.5f * k, 21.5f * k)
        lineTo(11f * k, 16f * k)
        cubicTo(10.4f * k, 17f * k, 9.4f * k, 17.8f * k, 8.2f * k, 18f * k)
        cubicTo(5.5f * k, 18.5f * k, 3f * k, 16.5f * k, 3f * k, 13.5f * k)
        cubicTo(3f * k, 9.5f * k, 8f * k, 7f * k, 12f * k, 2.5f * k)
        close()
    }

    fun heart(s: Float): Path = Path().apply {
        val k = s / 24f
        moveTo(12f * k, 21f * k)
        cubicTo(6f * k, 16.5f * k, 2.5f * k, 13f * k, 2.5f * k, 8.8f * k)
        cubicTo(2.5f * k, 5.6f * k, 5f * k, 3.5f * k, 7.6f * k, 3.5f * k)
        cubicTo(9.5f * k, 3.5f * k, 11f * k, 4.6f * k, 12f * k, 6.3f * k)
        cubicTo(13f * k, 4.6f * k, 14.5f * k, 3.5f * k, 16.4f * k, 3.5f * k)
        cubicTo(19f * k, 3.5f * k, 21.5f * k, 5.6f * k, 21.5f * k, 8.8f * k)
        cubicTo(21.5f * k, 13f * k, 18f * k, 16.5f * k, 12f * k, 21f * k)
        close()
    }

    fun diamond(s: Float): Path = Path().apply {
        val k = s / 24f
        moveTo(12f * k, 2f * k)
        cubicTo(14.5f * k, 6f * k, 17f * k, 9f * k, 19.5f * k, 12f * k)
        cubicTo(17f * k, 15f * k, 14.5f * k, 18f * k, 12f * k, 22f * k)
        cubicTo(9.5f * k, 18f * k, 7f * k, 15f * k, 4.5f * k, 12f * k)
        cubicTo(7f * k, 9f * k, 9.5f * k, 6f * k, 12f * k, 2f * k)
        close()
    }

    fun club(s: Float): Path = Path().apply {
        val k = s / 24f
        addCircle(12f * k, 7.2f * k, 4.3f * k, Path.Direction.CW)
        addCircle(7.2f * k, 13.3f * k, 4.3f * k, Path.Direction.CW)
        addCircle(16.8f * k, 13.3f * k, 4.3f * k, Path.Direction.CW)
        moveTo(11f * k, 12f * k)
        lineTo(13f * k, 12f * k)
        lineTo(14.6f * k, 21.5f * k)
        lineTo(9.4f * k, 21.5f * k)
        close()
    }
}

class IconView(context: Context, var icon: RoyalIcon, var color: Int = Royal.goldLight) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val size = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (size <= 0f) return
        val left = paddingLeft + (width - paddingLeft - paddingRight - size) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - size) / 2f
        RoyalIcons.draw(canvas, icon, RectF(left, top, left + size, top + size), color, paint, size / 12f)
    }
}

/** Sixteen code-drawn medallion avatars in a polished 3D Persian style. */
object Emblems {
    const val COUNT = 16
    private val hues = intArrayOf(
        0xFF1F4FA8.toInt(), 0xFFC4801C.toInt(), 0xFF3B3F9E.toInt(), 0xFF14805F.toInt(),
        0xFFB0436A.toInt(), 0xFF11807D.toInt(), 0xFFA62B35.toInt(), 0xFF3F5D7D.toInt(),
        0xFF6A3E9E.toInt(), 0xFF8A5A26.toInt(), 0xFF8E1F3C.toInt(), 0xFF5E7A2B.toInt(),
        0xFF1F6FB0.toInt(), 0xFF7D2F6E.toInt(), 0xFF46607E.toInt(), 0xFFA8552A.toInt()
    )
    val names = listOf(
        "تاج", "خورشید", "ماه و ستاره", "سرو", "نیلوفر", "ستارهٔ هشت‌پر", "آتش", "سپر",
        "بال", "ستون", "شمشیر", "کمان", "گوهر", "گل‌سرخ", "دماوند", "دژ"
    )

    fun hue(index: Int): Int = hues[((index % COUNT) + COUNT) % COUNT]

    fun drawMedallion(canvas: Canvas, cx: Float, cy: Float, radius: Float, base: Int, paint: Paint, selected: Boolean = false) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy + radius * 0.18f, radius * 1.12f, intArrayOf(0x66000000, 0), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy + radius * 0.12f, radius * 1.1f, paint)
        paint.shader = SweepGradient(cx, cy, intArrayOf(Royal.goldLight, Royal.goldDeep, Royal.goldLight, Royal.bronze, Royal.gold, Royal.goldLight), null)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.color = 0x99000000.toInt()
        canvas.drawCircle(cx, cy, radius * 0.86f, paint)
        paint.shader = RadialGradient(cx - radius * 0.3f, cy - radius * 0.35f, radius * 1.15f, intArrayOf(Royal.mix(base, Color.WHITE, 0.38f), base, Royal.mix(base, Color.BLACK, 0.55f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 0.83f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.025f
        paint.color = Royal.alpha(Royal.goldLight, 0.45f)
        canvas.drawCircle(cx, cy, radius * 0.74f, paint)
        paint.style = Paint.Style.FILL
        if (selected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = radius * 0.08f
            paint.color = Royal.turquoise
            canvas.drawCircle(cx, cy, radius * 0.97f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    fun drawGloss(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, cy - radius * 0.8f, 0f, cy, Royal.alpha(Color.WHITE, 0.28f), 0, Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(cx - radius * 0.58f, cy - radius * 0.78f, cx + radius * 0.58f, cy - radius * 0.05f), paint)
        paint.shader = null
    }

    /** Draws a gold relief motif from a 100-unit design box centred in the medallion. */
    fun drawRelief(canvas: Canvas, path: Path, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val size = radius * 1.32f
        val m = Matrix().apply {
            setRectToRect(RectF(0f, 0f, 100f, 100f), RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f), Matrix.ScaleToFit.CENTER)
        }
        val scaled = Path(path).apply { transform(m) }
        val shadow = Path(scaled).apply { offset(0f, radius * 0.045f) }
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x88000000.toInt()
        canvas.drawPath(shadow, paint)
        paint.shader = LinearGradient(0f, cy - size / 2f, 0f, cy + size / 2f, intArrayOf(Royal.goldLight, Royal.gold, Royal.goldDeep), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(scaled, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.018f
        paint.color = Royal.alpha(Royal.bronze, 0.8f)
        canvas.drawPath(scaled, paint)
        paint.style = Paint.Style.FILL
    }

    private val motifs = HashMap<Int, Path>()

    /** Cached motif outline; callers copy it before transforming. */
    fun motif(index: Int): Path {
        val key = ((index % COUNT) + COUNT) % COUNT
        return motifs.getOrPut(key) { buildMotif(key) }
    }

    private fun buildMotif(key: Int): Path {
        val p = Path()
        when (key) {
            0 -> {
                p.moveTo(26f, 64f); p.lineTo(21f, 34f); p.lineTo(37f, 47f); p.lineTo(50f, 26f); p.lineTo(63f, 47f); p.lineTo(79f, 34f); p.lineTo(74f, 64f); p.close()
                p.addRect(26f, 67f, 74f, 75f, Path.Direction.CW)
                p.addCircle(21f, 31f, 4f, Path.Direction.CW); p.addCircle(50f, 22.5f, 4.5f, Path.Direction.CW); p.addCircle(79f, 31f, 4f, Path.Direction.CW)
            }
            1 -> {
                p.addCircle(50f, 50f, 14f, Path.Direction.CW)
                for (i in 0 until 12) {
                    val a = Math.PI / 6 * i
                    val a1 = a - 0.16
                    val a2 = a + 0.16
                    p.moveTo(50f + (18f * cos(a1)).toFloat(), 50f + (18f * sin(a1)).toFloat())
                    p.lineTo(50f + (32f * cos(a)).toFloat(), 50f + (32f * sin(a)).toFloat())
                    p.lineTo(50f + (18f * cos(a2)).toFloat(), 50f + (18f * sin(a2)).toFloat())
                    p.close()
                }
            }
            2 -> {
                val outer = Path().apply { addCircle(46f, 52f, 26f, Path.Direction.CW) }
                val inner = Path().apply { addCircle(57f, 45f, 22f, Path.Direction.CW) }
                outer.op(inner, Path.Op.DIFFERENCE)
                p.addPath(outer)
                p.addPath(RoyalShapes.starPath(68f, 40f, 10f, 5, 0.45f))
            }
            3 -> {
                p.moveTo(50f, 14f); p.cubicTo(64f, 32f, 69f, 52f, 62f, 68f); p.cubicTo(58f, 76f, 54f, 79f, 50f, 81f)
                p.cubicTo(46f, 79f, 42f, 76f, 38f, 68f); p.cubicTo(31f, 52f, 36f, 32f, 50f, 14f); p.close()
                p.addRect(46.5f, 79f, 53.5f, 88f, Path.Direction.CW)
            }
            4 -> {
                p.addPath(petal(50f, 70f, 0f))
                p.addPath(petal(50f, 70f, -38f)); p.addPath(petal(50f, 70f, 38f))
                p.addPath(petal(50f, 70f, -74f)); p.addPath(petal(50f, 70f, 74f))
                p.addRect(30f, 72f, 70f, 77f, Path.Direction.CW)
            }
            5 -> {
                p.addPath(RoyalShapes.star8Path(50f, 50f, 33f))
                val hole = Path().apply { addCircle(50f, 50f, 10f, Path.Direction.CW) }
                p.op(hole, Path.Op.DIFFERENCE)
            }
            6 -> {
                p.moveTo(50f, 12f); p.cubicTo(62f, 28f, 74f, 38f, 70f, 58f); p.cubicTo(67f, 72f, 58f, 82f, 50f, 84f)
                p.cubicTo(42f, 82f, 32f, 72f, 30f, 58f); p.cubicTo(28f, 44f, 40f, 38f, 42f, 26f); p.cubicTo(46f, 34f, 50f, 38f, 50f, 38f)
                p.cubicTo(53f, 30f, 53f, 22f, 50f, 12f); p.close()
                val core = Path().apply {
                    moveTo(50f, 50f); cubicTo(57f, 58f, 60f, 66f, 56f, 73f); cubicTo(54f, 77f, 52f, 78f, 50f, 79f)
                    cubicTo(48f, 78f, 46f, 77f, 44f, 73f); cubicTo(40f, 66f, 43f, 58f, 50f, 50f); close()
                }
                p.op(core, Path.Op.DIFFERENCE)
            }
            7 -> {
                p.moveTo(50f, 15f); p.lineTo(77f, 25f); p.lineTo(77f, 47f); p.cubicTo(77f, 65f, 64f, 77f, 50f, 85f)
                p.cubicTo(36f, 77f, 23f, 65f, 23f, 47f); p.lineTo(23f, 25f); p.close()
                val cross = Path().apply {
                    addRect(46f, 27f, 54f, 73f, Path.Direction.CW)
                    addRect(32f, 42f, 68f, 50f, Path.Direction.CW)
                }
                p.op(cross, Path.Op.DIFFERENCE)
            }
            8 -> {
                p.addPath(wing(false)); p.addPath(wing(true))
                p.addCircle(50f, 47f, 7f, Path.Direction.CW)
                p.moveTo(44f, 58f); p.lineTo(56f, 58f); p.lineTo(53f, 74f); p.lineTo(47f, 74f); p.close()
            }
            9 -> {
                p.addRect(28f, 20f, 72f, 29f, Path.Direction.CW)
                p.addCircle(31f, 33f, 6f, Path.Direction.CW); p.addCircle(69f, 33f, 6f, Path.Direction.CW)
                p.addRect(42f, 30f, 58f, 74f, Path.Direction.CW)
                p.addRect(34f, 74f, 66f, 82f, Path.Direction.CW)
                val flutes = Path().apply {
                    addRect(46f, 36f, 47.5f, 70f, Path.Direction.CW); addRect(52.5f, 36f, 54f, 70f, Path.Direction.CW)
                }
                p.op(flutes, Path.Op.DIFFERENCE)
            }
            10 -> {
                p.moveTo(50f, 12f); p.lineTo(56f, 22f); p.lineTo(56f, 62f); p.lineTo(44f, 62f); p.lineTo(44f, 22f); p.close()
                p.addRect(32f, 62f, 68f, 68f, Path.Direction.CW)
                p.addRect(46.5f, 68f, 53.5f, 82f, Path.Direction.CW)
                p.addCircle(50f, 86f, 4.5f, Path.Direction.CW)
            }
            11 -> {
                p.moveTo(30f, 18f); p.cubicTo(58f, 28f, 58f, 72f, 30f, 82f); p.lineTo(34f, 82f); p.cubicTo(64f, 70f, 64f, 30f, 34f, 18f); p.close()
                p.addRect(31f, 18f, 33f, 82f, Path.Direction.CW)
                p.addRect(30f, 48.5f, 76f, 51.5f, Path.Direction.CW)
                p.moveTo(84f, 50f); p.lineTo(72f, 42f); p.lineTo(72f, 58f); p.close()
            }
            12 -> {
                p.moveTo(50f, 18f); p.lineTo(72f, 38f); p.lineTo(50f, 84f); p.lineTo(28f, 38f); p.close()
                val facets = Path().apply {
                    moveTo(50f, 26f); lineTo(62f, 38f); lineTo(50f, 70f); lineTo(38f, 38f); close()
                }
                p.op(facets, Path.Op.DIFFERENCE)
                p.addPath(Path().apply { moveTo(50f, 32f); lineTo(57f, 38.5f); lineTo(50f, 58f); lineTo(43f, 38.5f); close() })
            }
            13 -> {
                for (i in 0 until 8) {
                    val a = 45f * i
                    p.addPath(Path().apply { addOval(RectF(44f, 16f, 56f, 46f), Path.Direction.CW) }, Matrix().apply { setRotate(a, 50f, 50f) })
                }
                p.addCircle(50f, 50f, 9f, Path.Direction.CW)
            }
            14 -> {
                p.moveTo(14f, 78f); p.lineTo(38f, 42f); p.lineTo(46f, 52f); p.lineTo(58f, 26f); p.lineTo(86f, 78f); p.close()
                val snow = Path().apply { moveTo(58f, 26f); lineTo(66f, 41f); lineTo(61f, 38f); lineTo(57f, 44f); lineTo(53f, 38f); lineTo(50f, 42f); close() }
                p.op(snow, Path.Op.DIFFERENCE)
            }
            else -> {
                p.moveTo(28f, 84f); p.lineTo(28f, 38f); p.lineTo(32f, 38f); p.lineTo(32f, 30f); p.lineTo(39f, 30f); p.lineTo(39f, 38f)
                p.lineTo(46f, 38f); p.lineTo(46f, 30f); p.lineTo(54f, 30f); p.lineTo(54f, 38f); p.lineTo(61f, 38f); p.lineTo(61f, 30f)
                p.lineTo(68f, 30f); p.lineTo(68f, 38f); p.lineTo(72f, 38f); p.lineTo(72f, 84f); p.close()
                val door = Path().apply { addPath(RoyalShapes.archPath(42f, 58f, 58f, 84f)) }
                p.op(door, Path.Op.DIFFERENCE)
            }
        }
        return p
    }

    private fun petal(cx: Float, cy: Float, rotation: Float): Path {
        val petal = Path().apply {
            moveTo(cx, cy); cubicTo(cx - 10f, cy - 16f, cx - 8f, cy - 36f, cx, cy - 48f); cubicTo(cx + 8f, cy - 36f, cx + 10f, cy - 16f, cx, cy); close()
        }
        petal.transform(Matrix().apply { setRotate(rotation, cx, cy) })
        return petal
    }

    private fun wing(mirror: Boolean): Path {
        val w = Path().apply {
            moveTo(44f, 44f); cubicTo(34f, 36f, 20f, 33f, 8f, 36f); cubicTo(16f, 40f, 22f, 42f, 28f, 44f)
            cubicTo(20f, 45f, 14f, 47f, 10f, 51f); cubicTo(20f, 51f, 28f, 51f, 34f, 51f)
            cubicTo(28f, 53f, 24f, 56f, 22f, 60f); cubicTo(32f, 58f, 40f, 56f, 45f, 52f); close()
        }
        if (mirror) w.transform(Matrix().apply { setScale(-1f, 1f, 50f, 50f) })
        return w
    }
}

class EmblemAvatarView(context: Context, avatar: Int = 0) : View(context) {
    var avatar: Int = avatar
        set(value) { field = value; invalidate() }
    var selectedRing = false
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f * 0.9f
        if (r <= 0f) return
        val cx = width / 2f
        val cy = height / 2f - r * 0.04f
        Emblems.drawMedallion(canvas, cx, cy, r, Emblems.hue(avatar), paint, selectedRing)
        Emblems.drawRelief(canvas, Emblems.motif(avatar), cx, cy, r, paint)
        Emblems.drawGloss(canvas, cx, cy, r * 0.83f, paint)
    }
}

/** Game emblem used on cards and headers. */
class GameSealView(context: Context, private val game: GameType?) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val path: Path by lazy { when (game) {
            GameType.SHALAM -> Path().apply {
                addPath(SuitShapes.spade(100f), Matrix().apply { setScale(0.62f, 0.62f); postTranslate(19f, 34f) })
                moveTo(32f, 30f); lineTo(29f, 14f); lineTo(39f, 21f); lineTo(50f, 9f); lineTo(61f, 21f); lineTo(71f, 14f); lineTo(68f, 30f); close()
            }
            GameType.MENFI -> Path().apply {
                moveTo(14f, 46f); cubicTo(30f, 24f, 70f, 24f, 86f, 46f); cubicTo(70f, 68f, 30f, 68f, 14f, 46f); close()
                val pupil = Path().apply { addCircle(50f, 46f, 11f, Path.Direction.CW) }
                op(pupil, Path.Op.DIFFERENCE)
                addCircle(50f, 46f, 5f, Path.Direction.CW)
                addRect(30f, 76f, 70f, 84f, Path.Direction.CW)
            }
            GameType.HEZARTAII -> Path().apply {
                for (i in 0 until 6) {
                    val deg = 105.0 + i * 24.0
                    val a = Math.toRadians(deg)
                    val x = 50f + (36f * cos(a)).toFloat()
                    val y = 50f + (36f * sin(a)).toFloat()
                    val leaf = Path().apply { addOval(RectF(x - 4f, y - 8.5f, x + 4f, y + 8.5f), Path.Direction.CW) }
                    addPath(leaf, Matrix().apply { setRotate((deg + 90.0).toFloat(), x, y) })
                    val mirror = Path().apply { addOval(RectF(100f - x - 4f, y - 8.5f, 100f - x + 4f, y + 8.5f), Path.Direction.CW) }
                    addPath(mirror, Matrix().apply { setRotate((-(deg + 90.0)).toFloat(), 100f - x, y) })
                }
                addPath(RoyalShapes.starPath(50f, 17f, 7f, 5, 0.45f))
            }
            GameType.DOLO -> Path().apply {
                addPath(SuitShapes.club(100f), Matrix().apply { setScale(0.5f, 0.5f); postTranslate(13f, 25f) })
            }
            null -> RoyalShapes.star8Path(50f, 50f, 34f)
        } }

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f * 0.92f
        if (r <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val base = when (game) {
            GameType.SHALAM -> 0xFF1C4AA0.toInt()
            GameType.MENFI -> 0xFF9E2633.toInt()
            GameType.HEZARTAII -> 0xFF0F7F73.toInt()
            GameType.DOLO -> 0xFF5B2C8C.toInt()
            null -> 0xFF23367A.toInt()
        }
        Emblems.drawMedallion(canvas, cx, cy, r, base, paint)
        Emblems.drawRelief(canvas, path, cx, cy, r, paint)
        if (game == GameType.HEZARTAII || game == GameType.DOLO) {
            val label = if (game == GameType.DOLO) "۲" else "۱۰۰۰"
            val x = if (game == GameType.DOLO) cx + r * 0.36f else cx
            val y = if (game == GameType.DOLO) cy + r * 0.12f else cy + r * 0.16f
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = RoyalFonts.get(context, true)
            paint.textSize = r * if (game == GameType.DOLO) 0.62f else 0.42f
            paint.color = 0x99000000.toInt()
            canvas.drawText(label, x, y + r * 0.03f, paint)
            paint.shader = LinearGradient(0f, cy - r * 0.2f, 0f, cy + r * 0.25f, Royal.goldLight, Royal.goldDeep, Shader.TileMode.CLAMP)
            canvas.drawText(label, x, y, paint)
            paint.shader = null
        }
        Emblems.drawGloss(canvas, cx, cy, r * 0.83f, paint)
    }
}

/** Decorative iwan arch with colonnade and stars behind screen titles. */
class CrestView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val archW = min(w * 0.62f, h * 1.15f)
        val left = (w - archW) / 2f
        val arch = RoyalShapes.archPath(left, h * 0.04f, left + archW, h * 0.98f)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(w / 2f, h * 0.45f, archW * 0.75f, intArrayOf(Royal.alpha(Royal.lapisLight, 0.75f), Royal.alpha(Royal.lapis, 0.35f), 0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(arch, paint)
        paint.shader = null
        paint.color = Royal.alpha(Royal.gold, 0.16f)
        canvas.drawPath(RoyalShapes.star8Path(w / 2f, h * 0.04f + archW * 0.16f, 9 * d), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * d
        paint.shader = LinearGradient(0f, 0f, 0f, h, Royal.goldLight, Royal.alpha(Royal.goldDeep, 0.2f), Shader.TileMode.CLAMP)
        canvas.drawPath(arch, paint)
        val inner = RoyalShapes.archPath(left + 7 * d, h * 0.04f + 9 * d, left + archW - 7 * d, h * 0.98f)
        paint.strokeWidth = 0.8f * d
        paint.shader = null
        paint.color = Royal.alpha(Royal.gold, 0.35f)
        canvas.drawPath(inner, paint)
        paint.strokeWidth = 1.1f * d
        paint.color = Royal.alpha(Royal.gold, 0.25f)
        val side = (w - archW) / 2f
        listOf(0f to side - 10 * d, left + archW + 10 * d to w).forEach { (a, b) ->
            val count = 3
            val step = (b - a) / count
            for (i in 0 until count) {
                val ax = a + step * i + step * 0.15f
                canvas.drawPath(RoyalShapes.archPath(ax, h * 0.48f, ax + step * 0.7f, h * 0.98f), paint)
            }
        }
        paint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(0, Royal.gold, 0), null, Shader.TileMode.CLAMP)
        paint.strokeWidth = 1.2f * d
        canvas.drawLine(0f, h - d, w, h - d, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }
}

class TrophyView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        val left = (width - s) / 2f
        val top = (height - s) / 2f
        val m = Matrix().apply { setRectToRect(RectF(0f, 0f, 100f, 100f), RectF(left, top, left + s, top + s), Matrix.ScaleToFit.CENTER) }
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(width / 2f, height / 2f, s * 0.55f, intArrayOf(Royal.alpha(Royal.gold, 0.35f), 0), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(width / 2f, height / 2f, s * 0.55f, paint)
        val cup = Path().apply {
            moveTo(28f, 16f); lineTo(72f, 16f); lineTo(72f, 34f); cubicTo(72f, 50f, 62f, 60f, 50f, 60f); cubicTo(38f, 60f, 28f, 50f, 28f, 34f); close()
            addRect(45f, 60f, 55f, 72f, Path.Direction.CW)
            addRect(36f, 72f, 64f, 78f, Path.Direction.CW)
            addRect(30f, 78f, 70f, 86f, Path.Direction.CW)
        }
        val handles = Path().apply {
            moveTo(28f, 22f); cubicTo(12f, 22f, 12f, 44f, 31f, 44f); lineTo(32f, 39f); cubicTo(20f, 39f, 19f, 27f, 28f, 27f); close()
            moveTo(72f, 22f); cubicTo(88f, 22f, 88f, 44f, 69f, 44f); lineTo(68f, 39f); cubicTo(80f, 39f, 81f, 27f, 72f, 27f); close()
        }
        val all = Path().apply { addPath(cup); addPath(handles) }
        all.transform(m)
        paint.shader = null
        paint.color = 0x77000000
        canvas.drawPath(Path(all).apply { offset(0f, s * 0.02f) }, paint)
        paint.shader = LinearGradient(left, 0f, left + s, 0f, intArrayOf(Royal.goldDeep, Royal.goldLight, Royal.gold, Royal.goldDeep), floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(all, paint)
        paint.shader = null
        val star = RoyalShapes.star8Path(50f, 34f, 10f).apply { transform(m) }
        paint.color = Royal.turquoise
        canvas.drawPath(star, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.008f
        paint.color = Royal.bronze
        canvas.drawPath(all, paint)
        paint.style = Paint.Style.FILL
    }
}

/** Gold hairline divider with a khatam star in the middle. */
class RoyalDivider(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cy = height / 2f
        val cx = width / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d
        paint.shader = LinearGradient(0f, 0f, cx, 0f, 0, Royal.gold, Shader.TileMode.CLAMP)
        canvas.drawLine(0f, cy, cx - 12 * d, cy, paint)
        paint.shader = LinearGradient(cx, 0f, width.toFloat(), 0f, Royal.gold, 0, Shader.TileMode.CLAMP)
        canvas.drawLine(cx + 12 * d, cy, width.toFloat(), cy, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Royal.gold
        canvas.drawPath(RoyalShapes.star8Path(cx, cy, 6 * d), paint)
        paint.color = Royal.turquoise
        canvas.drawCircle(cx, cy, 2 * d, paint)
    }
}

/** Custom-drawn toggle so settings do not fall back to the stock Material switch. */
class RoyalSwitch(context: Context, checked: Boolean) : View(context) {
    var checked: Boolean = checked
        private set
    var onChange: ((Boolean) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "switch"
        setOnClickListener { setChecked(!this.checked, true) }
    }

    fun setChecked(value: Boolean, notify: Boolean) {
        if (value == checked) return
        checked = value
        invalidate()
        if (notify) onChange?.invoke(value)
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val h = min(height.toFloat(), 30 * d)
        val w = min(width.toFloat(), 54 * d)
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        val track = RectF(left, top, left + w, top + h)
        paint.style = Paint.Style.FILL
        paint.shader = if (checked) {
            LinearGradient(0f, track.top, 0f, track.bottom, Royal.turquoise, Royal.turquoiseDeep, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, track.top, 0f, track.bottom, 0xFF0A1630.toInt(), 0xFF142A5A.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(track, h / 2f, h / 2f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d
        paint.color = if (checked) Royal.turquoiseLight else Royal.alpha(Royal.gold, 0.7f)
        canvas.drawRoundRect(track, h / 2f, h / 2f, paint)
        paint.style = Paint.Style.FILL
        val r = h / 2f - 3 * d
        val cx = if (checked) track.left + r + 3 * d else track.right - r - 3 * d
        val cy = track.centerY()
        paint.color = 0x66000000
        canvas.drawCircle(cx, cy + d, r, paint)
        paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.4f, intArrayOf(Royal.goldLight, Royal.gold, Royal.goldDeep), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }
}
