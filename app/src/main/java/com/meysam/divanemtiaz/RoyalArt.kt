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
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class RoyalIcon {
    BACK, NEXT, HOME, SETTINGS, HISTORY, RULES, PLAYERS, PLUS, MINUS, CHECK, CLOSE, EDIT, TRASH,
    EYE, EYE_OFF, CROWN, TROPHY, TIMER, CHART, STAR, PLAY, UNDO, FLAG, WARNING, SLIDERS, MENU,
    SPADE, HEART, DIAMOND, CLUB, SWAP, USER, SHARE, CARDS, SAVE, LEAGUE, SEARCH
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
                for (i in 0 until 8) {
                    val a = Math.PI / 4 * i
                    p.moveTo(12f + (6.5f * cos(a)).toFloat(), 12f + (6.5f * sin(a)).toFloat())
                    p.lineTo(12f + (9.5f * cos(a)).toFloat(), 12f + (9.5f * sin(a)).toFloat())
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
            RoyalIcon.SEARCH -> {
                p.addCircle(10.5f, 10.5f, 6f, Path.Direction.CW)
                p.moveTo(15f, 15f); p.lineTo(20.5f, 20.5f)
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
        paint.color = Color.BLACK
        paint.shader = RadialGradient(cx, cy + radius * 0.18f, radius * 1.12f, intArrayOf(0x66000000, 0), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy + radius * 0.12f, radius * 1.1f, paint)
        paint.color = Color.BLACK
        paint.shader = LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
            intArrayOf(Royal.metalLight, Royal.metalDeep, Royal.metalLight, Royal.metalShadow, Royal.metal), floatArrayOf(0f, 0.35f, 0.55f, 0.8f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.color = 0x99000000.toInt()
        canvas.drawCircle(cx, cy, radius * 0.86f, paint)
        paint.color = Color.BLACK
        paint.shader = RadialGradient(cx - radius * 0.3f, cy - radius * 0.35f, radius * 1.15f, intArrayOf(Royal.mix(base, Color.WHITE, 0.38f), base, Royal.mix(base, Color.BLACK, 0.55f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 0.83f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.025f
        paint.color = Royal.alpha(Royal.metalLight, 0.45f)
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

    /** Soft top-left shine over the enamel, like the web seal's radial highlight. */
    fun drawGloss(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.shader = RadialGradient(cx - radius * 0.3f, cy - radius * 0.4f, radius * 1.5f,
            intArrayOf(Royal.alpha(Color.WHITE, 0.35f), 0, 0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    /** Draws a gold relief motif from a 100-unit design box centred in the medallion; [box] is the box size in radii. */
    fun drawRelief(canvas: Canvas, path: Path, cx: Float, cy: Float, radius: Float, paint: Paint, box: Float = 1.72f) {
        val size = radius * box
        val m = Matrix().apply {
            setRectToRect(RectF(0f, 0f, 100f, 100f), RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f), Matrix.ScaleToFit.CENTER)
        }
        val scaled = Path(path).apply { transform(m) }
        val shadow = Path(scaled).apply { offset(0f, radius * 0.035f) }
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x66000000
        canvas.drawPath(shadow, paint)
        paint.color = Color.BLACK
        paint.shader = goldFill(cy - size * 0.32f, cy + size * 0.32f)
        canvas.drawPath(scaled, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.022f
        paint.color = 0x802A1A05.toInt()
        canvas.drawPath(scaled, paint)
        paint.style = Paint.Style.FILL
    }

    /** The web version's vertical gold gradient used for every relief. */
    fun goldFill(top: Float, bottom: Float): Shader =
        LinearGradient(0f, top, 0f, bottom, intArrayOf(Royal.metalLight, Royal.metal, Royal.metalDeep), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)

    /** Gold text relief (used by the «۱۰۰۰» and «۲» seals). */
    fun drawReliefText(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float, typeface: android.graphics.Typeface, paint: Paint) {
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = typeface
        paint.textSize = size
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x77000000
        canvas.drawText(text, x, baseline + size * 0.06f, paint)
        paint.color = Color.BLACK
        paint.shader = goldFill(baseline - size * 0.75f, baseline)
        canvas.drawText(text, x, baseline, paint)
        paint.shader = null
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

/**
 * Base for code-drawn artwork: the drawing is rendered once into a bitmap per size and key,
 * so scrolling and re-layouts only blit pixels instead of re-tessellating paths and gradients.
 */
abstract class CachedArtView(context: Context) : View(context) {
    private var cache: android.graphics.Bitmap? = null
    private var cacheKey: String? = null
    /** Art skipped because the view was far off screen; drawn when it scrolls or moves near. */
    private var pending = false
    private val dst = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    protected abstract fun artKey(): Any

    protected abstract fun drawArt(canvas: Canvas)

    /** Highest pixel density the art is rendered at (lower = less memory and faster first draw). */
    protected open val maxArtDensity: Float = Float.MAX_VALUE

    fun invalidateArt() {
        cacheKey = null
        cache = null
        invalidate()
    }

    private fun key(): String = "${javaClass.name}|$width|$height|${artKey()}|${ThemeEngine.version}"

    private fun render(key: String): android.graphics.Bitmap {
        val s = min(1f, maxArtDensity / resources.displayMetrics.density)
        val bw = (width * s).toInt().coerceAtLeast(1)
        val bh = (height * s).toInt().coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(bw / width.toFloat(), bh / height.toFloat())
        drawArt(c)
        ArtCache.put(key, bitmap)
        return bitmap
    }

    /** Renders the art at a given size ahead of time, even before the view is laid out; true if work was done. */
    fun warmAt(w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0) return false
        if (width != w || height != h) {
            if (isAttachedToWindow) return false
            layout(0, 0, w, h)
        }
        val key = key()
        if (ArtCache.get(key) != null) return false
        render(key)
        return true
    }

    /** Renders the art into the shared cache ahead of time (called by [ArtWarmer]); true if work was done. */
    fun warm(): Boolean {
        if (width <= 0 || height <= 0 || !isAttachedToWindow) return false
        val key = key()
        if (ArtCache.get(key) != null) return false
        render(key)
        return true
    }

    private val loc = IntArray(2)
    private fun nearScreen(): Boolean {
        if (!isShown) return false
        getLocationOnScreen(loc)
        val dm = resources.displayMetrics
        // On screen (with a small margin): everything further away is drawn by ArtWarmer while the app is idle.
        val m = dm.heightPixels / 8
        return loc[1] + height > -m && loc[1] < dm.heightPixels + m && loc[0] + width > 0 && loc[0] < dm.widthPixels
    }

    private val wake = android.view.ViewTreeObserver.OnScrollChangedListener { checkPending() }
    private val wakeLayout = android.view.ViewTreeObserver.OnGlobalLayoutListener { checkPending() }

    private fun checkPending() {
        if (pending && nearScreen()) { pending = false; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val key = key()
        var bitmap = cache?.takeIf { key == cacheKey && !it.isRecycled }
        if (bitmap == null) {
            // Art is shared through one memory-bounded cache and drawn only near the screen: a page of two hundred
            // role cards no longer renders every card (and allocates every bitmap) before its first frame.
            bitmap = ArtCache.get(key)
            if (bitmap == null) {
                if (!nearScreen()) {
                    pending = true
                    ArtWarmer.enqueue(this)
                    return
                }
                bitmap = render(key)
            }
            cache = bitmap
            cacheKey = key
        }
        pending = false
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, null, dst, bitmapPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(wake)
        viewTreeObserver.addOnGlobalLayoutListener(wakeLayout)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnScrollChangedListener(wake)
        viewTreeObserver.removeOnGlobalLayoutListener(wakeLayout)
        cache = null
        cacheKey = null
    }
}

/** Process-wide cache of drawn art, sized to a quarter of the heap. Bitmaps are never recycled while shared. */
object ArtCache {
    private val lru = object : android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 4L).toInt().coerceAtLeast(4096)
    ) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(key: String): android.graphics.Bitmap? = lru.get(key)?.takeIf { !it.isRecycled }
    fun put(key: String, bitmap: android.graphics.Bitmap) { lru.put(key, bitmap) }
}

class EmblemAvatarView(context: Context, avatar: Int = 0) : CachedArtView(context) {
    var avatar: Int = avatar
        set(value) { field = value; invalidateArt() }
    var selectedRing = false
        set(value) { field = value; invalidateArt() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun artKey(): Any = avatar * 2 + if (selectedRing) 1 else 0

    override fun drawArt(canvas: Canvas) {
        val r = min(width, height) / 2f * 0.92f
        if (r <= 0f) return
        val cx = width / 2f
        val cy = height / 2f - r * 0.03f
        val photo = PhotoStore.bitmap(avatar)
        if (photo != null) {
            // A gallery picture inside the metal ring of the medallion.
            Emblems.drawMedallion(canvas, cx, cy, r, Emblems.hue(avatar), paint, selectedRing)
            PhotoStore.drawCircle(canvas, photo, cx, cy, r * 0.84f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.03f
            paint.color = Royal.alpha(Royal.metalLight, 0.6f)
            canvas.drawCircle(cx, cy, r * 0.84f, paint)
            paint.style = Paint.Style.FILL
            if (selectedRing) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = r * 0.08f
                paint.color = Royal.turquoise
                canvas.drawCircle(cx, cy, r * 0.97f, paint)
                paint.style = Paint.Style.FILL
            }
            Emblems.drawGloss(canvas, cx, cy, r * 0.83f, paint)
            return
        }
        Emblems.drawMedallion(canvas, cx, cy, r, Emblems.hue(avatar), paint, selectedRing)
        Emblems.drawRelief(canvas, Emblems.motif(avatar), cx, cy, r, paint)
        Emblems.drawGloss(canvas, cx, cy, r * 0.83f, paint)
    }
}

/** Game emblem used on cards and headers; the symbols follow the web version's seals. */
class GameSealView(context: Context, private val game: GameType?) : CachedArtView(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun artKey(): Any = game?.key ?: "app"

    override fun drawArt(canvas: Canvas) {
        val r = min(width, height) / 2f * 0.94f
        if (r <= 0f) return
        val cx = width / 2f
        val cy = height / 2f - r * 0.03f
        Emblems.drawMedallion(canvas, cx, cy, r, base(game), paint)
        // Web seals draw their symbols on the medallion's full 100-unit box (ring radius = 48 units).
        val unit = r / 48f
        val ox = cx - 50f * unit
        val oy = cy - 50f * unit
        when (game) {
            GameType.HEZARTAII -> drawCardFan(canvas, ox, oy, unit)
            GameType.DOLO -> {
                Emblems.drawRelief(canvas, SuitShapes.club(48f).apply { offset(23f, 22f) }, cx, cy, r, paint, 2f * 50f / 48f)
                paint.style = Paint.Style.FILL
                paint.shader = null
                paint.color = RoyalClassic.night
                canvas.drawCircle(ox + 72f * unit, oy + 70f * unit, 11f * unit, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * unit
                paint.color = Color.BLACK
                paint.shader = Emblems.goldFill(oy + 59f * unit, oy + 81f * unit)
                canvas.drawCircle(ox + 72f * unit, oy + 70f * unit, 11f * unit, paint)
                paint.shader = null
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = RoyalFonts.get(context, true)
                paint.textSize = 15f * unit
                paint.color = Royal.metalLight
                canvas.drawText("۲", ox + 72f * unit, oy + 76f * unit, paint)
            }
            GameType.MENFI -> {
                val eye = Path().apply {
                    moveTo(22f, 50f); cubicTo(22f, 50f, 33f, 33f, 50f, 33f); cubicTo(67f, 33f, 78f, 50f, 78f, 50f)
                    cubicTo(78f, 50f, 67f, 67f, 50f, 67f); cubicTo(33f, 67f, 22f, 50f, 22f, 50f); close()
                }
                Emblems.drawRelief(canvas, eye, cx, cy, r, paint, 2f * 50f / 48f)
                paint.style = Paint.Style.FILL
                paint.shader = null
                paint.color = Royal.alpha(RoyalClassic.night, 0.55f)
                canvas.drawCircle(ox + 50f * unit, oy + 50f * unit, 8f * unit, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 5f * unit
                paint.color = 0x66000000
                canvas.drawLine(ox + 27f * unit, oy + 73.5f * unit, ox + 73f * unit, oy + 29.5f * unit, paint)
                paint.color = Color.BLACK
                paint.shader = Emblems.goldFill(oy + 28f * unit, oy + 72f * unit)
                canvas.drawLine(ox + 27f * unit, oy + 72f * unit, ox + 73f * unit, oy + 28f * unit, paint)
                paint.shader = null
                paint.style = Paint.Style.FILL
            }
            GameType.SHALAM -> Emblems.drawRelief(canvas, SuitShapes.spade(48f).apply { offset(26f, 24f) }, cx, cy, r, paint, 2f * 50f / 48f)
            null -> Emblems.drawRelief(canvas, CROWN, cx, cy, r, paint, 2f * 50f / 48f)
        }
        Emblems.drawGloss(canvas, cx, cy, r * 0.83f, paint)
    }

    /** Pasur identity of «هزارتایی»: three playing cards fanned out, the front one with a club, and «۱۰۰۰» beneath. */
    private fun drawCardFan(canvas: Canvas, ox: Float, oy: Float, unit: Float) {
        val pivotX = ox + 50f * unit
        val pivotY = oy + 78f * unit
        val w = 25f * unit
        val h = 35f * unit
        val corner = 3.6f * unit
        listOf(-24f, 0f, 24f).forEachIndexed { i, angle ->
            canvas.save()
            canvas.rotate(angle, pivotX, pivotY)
            val card = RectF(pivotX - w / 2f, pivotY - h - 9f * unit, pivotX + w / 2f, pivotY - 9f * unit)
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = 0x66000000
            canvas.drawRoundRect(RectF(card.left + 1.2f * unit, card.top + 1.6f * unit, card.right + 1.2f * unit, card.bottom + 1.6f * unit), corner, corner, paint)
            paint.color = Color.BLACK
            paint.shader = Emblems.goldFill(card.top, card.bottom)
            canvas.drawRoundRect(card, corner, corner, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.1f * unit
            paint.color = Royal.alpha(Royal.metalShadow, 0.9f)
            canvas.drawRoundRect(card, corner, corner, paint)
            paint.strokeWidth = 0.7f * unit
            paint.color = Royal.alpha(Royal.metalShadow, 0.45f)
            val inset = 2.4f * unit
            canvas.drawRoundRect(RectF(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset), corner * 0.6f, corner * 0.6f, paint)
            paint.style = Paint.Style.FILL
            val suit = when (i) { 0 -> SuitShapes.diamond(13f * unit); 1 -> SuitShapes.club(14f * unit); else -> SuitShapes.heart(13f * unit) }
            val bounds = RectF().also { suit.computeBounds(it, true) }
            suit.offset(card.centerX() - bounds.centerX(), card.centerY() - bounds.centerY() - 1.5f * unit)
            paint.color = if (i == 1) Royal.alpha(0xFF0E1A33.toInt(), 0.85f) else Royal.alpha(0xFF8E1F2C.toInt(), 0.85f)
            canvas.drawPath(suit, paint)
            canvas.restore()
        }
        Emblems.drawReliefText(canvas, "۱۰۰۰", ox + 50f * unit, oy + 87f * unit, 15f * unit, RoyalFonts.get(context, true), paint)
    }

    companion object {
        fun base(game: GameType?): Int = when (game) {
            GameType.SHALAM -> 0xFF1F4FA8.toInt()
            GameType.MENFI -> 0xFFA62B35.toInt()
            GameType.HEZARTAII -> 0xFF11807D.toInt()
            GameType.DOLO -> 0xFF5B2C8C.toInt()
            null -> 0xFF3B3F9E.toInt()
        }

        /** The web crown: five points, a band and three pearls. */
        val CROWN: Path = Path().apply {
            moveTo(29f, 63f); lineTo(26f, 39f); lineTo(39f, 49f); lineTo(50f, 31f); lineTo(61f, 49f); lineTo(74f, 39f); lineTo(71f, 63f); close()
            addRoundRect(RectF(29f, 65f, 71f, 71f), 2f, 2f, Path.Direction.CW)
            addCircle(50f, 28f, 3.6f, Path.Direction.CW); addCircle(26f, 36f, 3f, Path.Direction.CW); addCircle(74f, 36f, 3f, Path.Direction.CW)
        }
    }
}

/** Home and splash crest: a fan of four playing cards (the card games the app keeps score of) under a small crown. */
class HeroCrestView(context: Context) : CachedArtView(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun artKey(): Any = "cards-crest"

    override fun drawArt(canvas: Canvas) = HeroCrestArt.draw(canvas, width, height, paint)
}

/** The fan of four cards under a crown (logo screen and about page), drawn still or at any moment of its entrance. */
object HeroCrestArt {
    private val FULL = floatArrayOf(1f, 1f, 1f, 1f)

    /**
     * Draws the fan in a [width] × [height] box. [cards] is the opening of each card (0 = not dealt yet, 1 = in its
     * place; a spring may pass 1 a little), [crown] the drop of the crown and [glow] the light and sparkles.
     */
    fun draw(canvas: Canvas, width: Int, height: Int, paint: Paint, cards: FloatArray = FULL, crown: Float = 1f, glow: Float = 1f) {
        // Design box 300 × 230, centred and scaled to fit.
        val k = min(width / 300f, height / 230f)
        if (k <= 0f) return
        val ox = (width - 300f * k) / 2f
        val oy = (height - 230f * k) / 2f
        fun x(v: Float) = ox + v * k
        fun y(v: Float) = oy + v * k
        val light = ThemeEngine.spec.light
        // Soft light behind the fan and its shadow on the table.
        paint.style = Paint.Style.FILL
        val g = glow.coerceIn(0f, 1f)
        if (g > 0f) {
        paint.shader = RadialGradient(x(150f), y(128f), 125f * k, intArrayOf(Royal.alpha(Royal.gold, (if (light) 0.20f else 0.28f) * g), Royal.alpha(Royal.gold, 0.06f * g), 0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x(150f), y(128f), 125f * k, paint)
        paint.shader = RadialGradient(x(150f), y(206f), 92f * k, intArrayOf(Royal.alpha(if (light) 0x30000000 else 0x66000000, g), 0), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, 0.16f, x(150f), y(206f))
        canvas.drawCircle(x(150f), y(206f), 92f * k, paint)
        canvas.restore()
        }
        paint.shader = null
        // Four cards: diamond, club, heart and the spade of shalam in front.
        val ink = 0xFF1B2233.toInt()
        val red = 0xFFC4303F.toInt()
        val suits = listOf(
            Triple(-33f, SuitShapes.diamond(36f * k), red),
            Triple(-11f, SuitShapes.club(38f * k), ink),
            Triple(11f, SuitShapes.heart(36f * k), red),
            Triple(33f, SuitShapes.spade(38f * k), ink)
        )
        val w = 72f * k
        val h = 100f * k
        val corner = 7f * k
        val pivotX = x(150f)
        val pivotY = y(212f)
        suits.forEachIndexed { i, (angle, suit, color) ->
            val p = cards.getOrElse(i) { 1f }
            if (p <= 0f) return@forEachIndexed
            canvas.save()
            // Dealt from the bottom: the card grows out of the pivot and swings open to its angle.
            val grow = 0.35f + 0.65f * p.coerceAtMost(1f)
            canvas.translate(0f, (1f - p.coerceAtMost(1f)) * 46f * k)
            canvas.scale(grow, grow, pivotX, pivotY)
            canvas.rotate(angle * p, pivotX, pivotY)
            val card = RectF(pivotX - w / 2f, pivotY - 30f * k - h, pivotX + w / 2f, pivotY - 30f * k)
            paint.style = Paint.Style.FILL
            paint.color = 0x2E000000
            canvas.drawRoundRect(RectF(card.left + 1.5f * k, card.top + 2f * k, card.right + 1.5f * k, card.bottom + 2f * k), corner, corner, paint)
            paint.color = Color.BLACK
            paint.shader = LinearGradient(0f, card.top, 0f, card.bottom, 0xFFFFFFFF.toInt(), 0xFFF3EFE7.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(card, corner, corner, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f * k
            paint.color = Color.BLACK
            paint.shader = LinearGradient(0f, card.top, 0f, card.bottom, Royal.metal, Royal.metalDeep, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(card, corner, corner, paint)
            paint.shader = null
            paint.strokeWidth = 0.9f * k
            paint.color = Royal.alpha(Royal.metalDeep, 0.45f)
            val inset = 4.5f * k
            canvas.drawRoundRect(RectF(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset), corner * 0.6f, corner * 0.6f, paint)
            paint.style = Paint.Style.FILL
            paint.color = color
            val b = RectF().also { suit.computeBounds(it, true) }
            canvas.drawPath(Path(suit).apply { offset(card.centerX() - b.centerX(), card.centerY() - b.centerY()) }, paint)
            // Small corner pips.
            val small = Matrix().apply { setScale(0.32f, 0.32f) }
            val pip = Path(suit).apply { transform(small) }
            val pb = RectF().also { pip.computeBounds(it, true) }
            canvas.drawPath(Path(pip).apply { offset(card.left + 10f * k - pb.centerX(), card.top + 12f * k - pb.centerY()) }, paint)
            canvas.drawPath(Path(pip).apply { offset(card.right - 10f * k - pb.centerX(), card.bottom - 12f * k - pb.centerY()) }, paint)
            canvas.restore()
        }
        // Crown over the fan, dropping in from above.
        if (crown <= 0f) return
        canvas.save()
        val cs = 0.55f + 0.45f * crown.coerceAtMost(1.1f)
        canvas.translate(0f, -(1f - crown) * 70f * k)
        canvas.scale(cs, cs, x(150f), y(52f))
        val crownPath = Path(GameSealView.CROWN)
        val m = Matrix().apply { setRectToRect(RectF(0f, 0f, 100f, 100f), RectF(x(112f), y(14f), x(188f), y(90f)), Matrix.ScaleToFit.CENTER) }
        crownPath.transform(m)
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawPath(Path(crownPath).apply { offset(0f, 2.2f * k) }, paint)
        paint.color = Color.BLACK
        paint.shader = Emblems.goldFill(y(34f), y(70f))
        canvas.drawPath(crownPath, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.9f * k
        paint.color = Royal.alpha(Royal.metalShadow, 0.7f)
        canvas.drawPath(crownPath, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
        // Two sparkles.
        listOf(Triple(96f, 44f, 5f), Triple(206f, 34f, 4f)).forEach { (sx, sy, r) ->
            if (g <= 0f) return@forEach
            paint.color = Royal.alpha(Royal.metalLight, 0.9f * g)
            canvas.drawPath(RoyalShapes.starPath(x(sx), y(sy), r * k * (0.4f + 0.6f * glow), 4, 0.32f), paint)
        }
    }
}

/**
 * The logo's entrance: the four cards are dealt from the bottom and swing open one after another on a spring, then
 * the crown drops in and the light and sparkles come up. Drawn live only while it plays ([DURATION] ms).
 */
class SplashFanView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cards = FloatArray(4)
    private var crown = 0f
    private var glow = 0f
    private var animator: android.animation.ValueAnimator? = null

    /** Shows the finished picture at once (no motion). */
    fun still() {
        animator?.cancel()
        cards.fill(1f); crown = 1f; glow = 1f
        invalidate()
    }

    fun play() {
        animator?.cancel()
        animator = android.animation.ValueAnimator.ofFloat(0f, DURATION.toFloat()).apply {
            duration = DURATION
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { a -> at(a.animatedValue as Float); invalidate() }
            start()
        }
    }

    private fun phase(t: Float, start: Float, length: Float, curve: android.animation.TimeInterpolator): Float =
        if (t <= start) 0f else if (t >= start + length) 1f else curve.getInterpolation((t - start) / length)

    private fun at(t: Float) {
        for (i in 0 until 4) cards[i] = phase(t, 60f + i * 110f, 760f, Motion.JELLY)
        crown = phase(t, 640f, 700f, Motion.JELLY)
        glow = phase(t, 0f, 900f, Motion.EASE_OUT)
    }

    override fun onDraw(canvas: Canvas) = HeroCrestArt.draw(canvas, width, height, paint, cards, crown, glow)

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        const val DURATION = 1400L
    }
}

/** Decorative iwan arch with colonnade and stars behind screen titles. */
class CrestView(context: Context) : CachedArtView(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun artKey(): Any = "arch"

    override fun drawArt(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val archW = min(w * 0.62f, h * 1.15f)
        val left = (w - archW) / 2f
        val arch = RoyalShapes.archPath(left, h * 0.04f, left + archW, h * 0.98f)
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.shader = RadialGradient(w / 2f, h * 0.45f, archW * 0.75f, intArrayOf(Royal.alpha(Royal.lapisLight, 0.75f), Royal.alpha(Royal.lapis, 0.35f), 0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(arch, paint)
        paint.shader = null
        paint.color = Royal.alpha(Royal.metal, 0.16f)
        canvas.drawPath(RoyalShapes.star8Path(w / 2f, h * 0.04f + archW * 0.16f, 9 * d), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * d
        paint.color = Color.BLACK
        paint.shader = LinearGradient(0f, 0f, 0f, h, Royal.metalLight, Royal.alpha(Royal.metalDeep, 0.2f), Shader.TileMode.CLAMP)
        canvas.drawPath(arch, paint)
        val inner = RoyalShapes.archPath(left + 7 * d, h * 0.04f + 9 * d, left + archW - 7 * d, h * 0.98f)
        paint.strokeWidth = 0.8f * d
        paint.shader = null
        paint.color = Royal.alpha(Royal.metal, 0.35f)
        canvas.drawPath(inner, paint)
        paint.strokeWidth = 1.1f * d
        paint.color = Royal.alpha(Royal.metal, 0.25f)
        val side = (w - archW) / 2f
        listOf(0f to side - 10 * d, left + archW + 10 * d to w).forEach { (a, b) ->
            val count = 3
            val step = (b - a) / count
            for (i in 0 until count) {
                val ax = a + step * i + step * 0.15f
                canvas.drawPath(RoyalShapes.archPath(ax, h * 0.48f, ax + step * 0.7f, h * 0.98f), paint)
            }
        }
        paint.color = Color.BLACK
        paint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(0, Royal.metal, 0), null, Shader.TileMode.CLAMP)
        paint.strokeWidth = 1.2f * d
        canvas.drawLine(0f, h - d, w, h - d, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }
}

class TrophyView(context: Context) : CachedArtView(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun artKey(): Any = "trophy"

    override fun drawArt(canvas: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        val left = (width - s) / 2f
        val top = (height - s) / 2f
        val m = Matrix().apply { setRectToRect(RectF(0f, 0f, 100f, 100f), RectF(left, top, left + s, top + s), Matrix.ScaleToFit.CENTER) }
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.shader = RadialGradient(width / 2f, height / 2f, s * 0.55f, intArrayOf(Royal.alpha(Royal.metal, 0.35f), 0), null, Shader.TileMode.CLAMP)
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
        paint.color = Color.BLACK
        paint.shader = LinearGradient(left, 0f, left + s, 0f, intArrayOf(Royal.metalDeep, Royal.metalLight, Royal.metal, Royal.metalDeep), floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(all, paint)
        paint.shader = null
        val star = RoyalShapes.star8Path(50f, 34f, 10f).apply { transform(m) }
        paint.color = Royal.turquoise
        canvas.drawPath(star, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.008f
        paint.color = Royal.metalShadow
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
        paint.color = Color.BLACK
        paint.shader = LinearGradient(0f, 0f, cx, 0f, 0, Royal.metal, Shader.TileMode.CLAMP)
        canvas.drawLine(0f, cy, cx - 12 * d, cy, paint)
        paint.color = Color.BLACK
        paint.shader = LinearGradient(cx, 0f, width.toFloat(), 0f, Royal.metal, 0, Shader.TileMode.CLAMP)
        canvas.drawLine(cx + 12 * d, cy, width.toFloat(), cy, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Royal.metal
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
        paint.color = Color.BLACK
        paint.shader = if (checked) {
            LinearGradient(0f, track.top, 0f, track.bottom, Royal.turquoise, Royal.turquoiseDeep, Shader.TileMode.CLAMP)
        } else {
            val t = ThemeEngine.spec
            LinearGradient(0f, track.top, 0f, track.bottom, Royal.mix(t.flatLow or 0xFF000000.toInt(), Royal.ivory, if (t.light) 0.16f else 0.06f),
                Royal.mix(t.flat or 0xFF000000.toInt(), Royal.ivory, if (t.light) 0.10f else 0.10f), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(track, h / 2f, h / 2f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d
        paint.color = if (checked) Royal.turquoiseLight else Royal.alpha(Royal.metal, 0.7f)
        canvas.drawRoundRect(track, h / 2f, h / 2f, paint)
        paint.style = Paint.Style.FILL
        val r = h / 2f - 3 * d
        val cx = if (checked) track.left + r + 3 * d else track.right - r - 3 * d
        val cy = track.centerY()
        paint.color = 0x66000000
        canvas.drawCircle(cx, cy + d, r, paint)
        paint.color = Color.BLACK
        paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.4f, intArrayOf(Royal.metalLight, Royal.metal, Royal.metalDeep), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }
}
