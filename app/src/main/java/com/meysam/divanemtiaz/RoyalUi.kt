package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue

/** Colour tokens of the royal Persian design system. */
object Royal {
    const val night = 0xFF050B1A.toInt()
    const val deep = 0xFF0A1530.toInt()
    const val lapis = 0xFF12285A.toInt()
    const val lapisLight = 0xFF1D3B7C.toInt()
    const val panelTop = 0xFF16295A.toInt()
    const val panelBottom = 0xFF0B1937.toInt()
    const val gold = 0xFFE6BE6A.toInt()
    const val goldLight = 0xFFFBE3A6.toInt()
    const val goldDeep = 0xFFB1832F.toInt()
    const val bronze = 0xFF6E4A1A.toInt()
    const val turquoise = 0xFF2ED3C6.toInt()
    const val turquoiseLight = 0xFF8EF0E6.toInt()
    const val turquoiseDeep = 0xFF0E8C86.toInt()
    const val crimson = 0xFFD0414B.toInt()
    const val crimsonLight = 0xFFF5868C.toInt()
    const val crimsonDeep = 0xFF7A1724.toInt()
    const val ivory = 0xFFF8F0DC.toInt()
    const val muted = 0xFFB3C3DD.toInt()
    const val dim = 0xFF7487AE.toInt()

    fun alpha(color: Int, alpha: Float): Int =
        Color.argb((Color.alpha(color) * alpha).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)
}

object RoyalFonts {
    /** Lets JVM screenshot tests supply the font files directly. */
    @Volatile var override: ((Boolean) -> Typeface?)? = null
    private var regular: Typeface? = null
    private var bold: Typeface? = null

    fun get(context: Context, isBold: Boolean): Typeface {
        override?.invoke(isBold)?.let { return it }
        val cached = if (isBold) bold else regular
        if (cached != null) return cached
        val loaded = runCatching {
            Typeface.createFromAsset(context.assets, if (isBold) "fonts/Vazirmatn-Bold.ttf" else "fonts/Vazirmatn-Regular.ttf")
        }.getOrElse { Typeface.create("sans-serif", if (isBold) Typeface.BOLD else Typeface.NORMAL) }
        if (isBold) bold = loaded else regular = loaded
        return loaded
    }
}

abstract class SimpleDrawable : Drawable() {
    private val shaders = HashMap<Int, Shader>()
    protected fun cachedShader(key: Int, create: () -> Shader): Shader = shaders.getOrPut(key, create)
    protected fun clearShaders() = shaders.clear()
    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        clearShaders()
    }
    private var drawAlpha = 255
    override fun setAlpha(alpha: Int) {
        drawAlpha = alpha
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    protected val alphaFraction: Float get() = drawAlpha / 255f
}

/** Night-sky lapis backdrop with a gold khatam lattice, a crown glow and a soft vignette. */
class BackdropDrawable(private val density: Float) : SimpleDrawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tile: Bitmap? = null

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        paint.shader = cachedShader(1) { LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF060D22.toInt(), 0xFF0A1734.toInt(), 0xFF0F2048.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }
        canvas.drawRect(b, paint)
        paint.shader = cachedShader(2) { BitmapShader(patternTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        paint.alpha = 255
        canvas.drawRect(b, paint)
        paint.shader = cachedShader(3) { RadialGradient(w / 2f, -h * 0.06f, w * 0.9f, intArrayOf(Royal.alpha(0xFF3A63C8.toInt(), 0.45f), Royal.alpha(0xFF1D3B7C.toInt(), 0.18f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) }
        canvas.drawRect(b, paint)
        paint.shader = cachedShader(4) { RadialGradient(w / 2f, h * 1.05f, w * 0.9f, intArrayOf(Royal.alpha(Royal.turquoise, 0.10f), 0), null, Shader.TileMode.CLAMP) }
        canvas.drawRect(b, paint)
        paint.shader = cachedShader(5) { RadialGradient(w / 2f, h * 0.45f, maxOf(w, h) * 0.75f, intArrayOf(0, 0, 0x88000000.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP) }
        canvas.drawRect(b, paint)
        paint.shader = null
    }

    private fun patternTile(): Bitmap {
        tile?.let { return it }
        val size = (64 * density).toInt().coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density * 0.9f
            color = Royal.alpha(Royal.gold, 0.075f)
        }
        val half = size / 2f
        RoyalShapes.star8(c, half, half, size * 0.30f, p)
        p.style = Paint.Style.FILL
        p.color = Royal.alpha(Royal.gold, 0.10f)
        listOf(0f to 0f, size.toFloat() to 0f, 0f to size.toFloat(), size.toFloat() to size.toFloat()).forEach { (x, y) ->
            c.drawCircle(x, y, density * 1.6f, p)
        }
        p.style = Paint.Style.STROKE
        p.color = Royal.alpha(Royal.gold, 0.045f)
        c.drawLine(0f, half, size * 0.18f, half, p)
        c.drawLine(size * 0.82f, half, size.toFloat(), half, p)
        c.drawLine(half, 0f, half, size * 0.18f, p)
        c.drawLine(half, size * 0.82f, half, size.toFloat(), p)
        tile = bitmap
        return bitmap
    }
}


enum class PanelStyle { NORMAL, RAISED, FLAT, SELECTED, DANGER, SUCCESS, GHOST }

/** Embossed card with gold frame, inner hairline, soft shadow and (for RAISED) corner jewels. */
class PanelDrawable(
    private val density: Float,
    private val style: PanelStyle = PanelStyle.NORMAL,
    private val radiusDp: Float = 18f
) : SimpleDrawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun getPadding(padding: Rect): Boolean {
        val pad = (14 * density).toInt()
        padding.set(pad, pad, pad, pad + (3 * density).toInt())
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val r = radiusDp * density
        val shadow = 3.5f * density
        rect.set(b.left + density, b.top.toFloat(), b.right - density, b.bottom - shadow)
        if (style != PanelStyle.GHOST && style != PanelStyle.FLAT) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            for (i in 0 until 3) {
                paint.color = Color.argb(((60 - i * 18) * alphaFraction).toInt(), 0, 0, 0)
                val grow = i * density
                canvas.drawRoundRect(RectF(rect.left - grow, rect.top + shadow * 0.6f, rect.right + grow, rect.bottom + shadow - i * 0.5f * density + grow), r + grow, r + grow, paint)
            }
        }
        val (top, bottom) = when (style) {
            PanelStyle.SELECTED -> Royal.mix(Royal.panelTop, Royal.turquoiseDeep, 0.35f) to Royal.mix(Royal.panelBottom, Royal.turquoiseDeep, 0.25f)
            PanelStyle.DANGER -> Royal.mix(Royal.panelTop, Royal.crimsonDeep, 0.55f) to Royal.mix(Royal.panelBottom, Royal.crimsonDeep, 0.45f)
            PanelStyle.SUCCESS -> Royal.mix(Royal.panelTop, Royal.turquoiseDeep, 0.45f) to Royal.mix(Royal.panelBottom, Royal.turquoiseDeep, 0.3f)
            PanelStyle.RAISED -> 0xFF1B3369.toInt() to 0xFF0C1A3B.toInt()
            PanelStyle.FLAT -> Royal.alpha(0xFF13254F.toInt(), 0.92f) to Royal.alpha(0xFF0D1C3F.toInt(), 0.92f)
            PanelStyle.GHOST -> 0 to 0
            PanelStyle.NORMAL -> Royal.alpha(Royal.panelTop, 0.96f) to Royal.alpha(Royal.panelBottom, 0.96f)
        }
        if (style != PanelStyle.GHOST) {
            paint.style = Paint.Style.FILL
            paint.shader = cachedShader(1) { LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP) }
            paint.alpha = (255 * alphaFraction).toInt()
            canvas.drawRoundRect(rect, r, r, paint)
            paint.shader = cachedShader(2) { LinearGradient(0f, rect.top, 0f, rect.top + (rect.height() * 0.5f), Royal.alpha(Color.WHITE, 0.07f), 0, Shader.TileMode.CLAMP) }
            canvas.drawRoundRect(rect, r, r, paint)
        }
        val (frameA, frameB) = when (style) {
            PanelStyle.SELECTED, PanelStyle.SUCCESS -> Royal.turquoiseLight to Royal.turquoiseDeep
            PanelStyle.DANGER -> Royal.crimsonLight to Royal.crimsonDeep
            PanelStyle.FLAT -> Royal.alpha(Royal.gold, 0.55f) to Royal.alpha(Royal.goldDeep, 0.35f)
            else -> Royal.goldLight to Royal.goldDeep
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (style == PanelStyle.RAISED || style == PanelStyle.SELECTED) 1.8f else 1.3f) * density
        paint.shader = cachedShader(3) { LinearGradient(0f, rect.top, 0f, rect.bottom, frameA, frameB, Shader.TileMode.CLAMP) }
        paint.alpha = (235 * alphaFraction).toInt()
        val half = paint.strokeWidth / 2f
        canvas.drawRoundRect(RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half), r, r, paint)
        if (style == PanelStyle.RAISED || style == PanelStyle.NORMAL || style == PanelStyle.SELECTED) {
            paint.shader = null
            paint.strokeWidth = 0.8f * density
            paint.color = Royal.alpha(if (style == PanelStyle.SELECTED) Royal.turquoise else Royal.gold, 0.28f * alphaFraction)
            val inset = 4.5f * density
            canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), r - inset * 0.7f, r - inset * 0.7f, paint)
        }
        if (style == PanelStyle.RAISED) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            val jewel = 3.6f * density
            val off = r * 0.42f
            listOf(rect.left + off to rect.top + off, rect.right - off to rect.top + off, rect.left + off to rect.bottom - off, rect.right - off to rect.bottom - off).forEach { (x, y) ->
                paint.color = Royal.alpha(Royal.gold, 0.9f * alphaFraction)
                RoyalShapes.diamond(canvas, x, y, jewel, paint)
                paint.color = Royal.alpha(Royal.turquoise, 0.9f * alphaFraction)
                canvas.drawCircle(x, y, jewel * 0.38f, paint)
            }
        }
        paint.shader = null
    }
}


enum class ButtonKind { PRIMARY, SECONDARY, SUCCESS, DANGER, GHOST, CHIP, CHIP_SELECTED, CHIP_GOLD }

/** Beveled 3D button face that reacts to pressed / disabled state. */
class ButtonDrawable(
    private val density: Float,
    private val kind: ButtonKind,
    private val radiusDp: Float = 15f
) : SimpleDrawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pressed = false
    private var enabled = true

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val newPressed = state.contains(android.R.attr.state_pressed)
        val newEnabled = state.contains(android.R.attr.state_enabled)
        val changed = newPressed != pressed || newEnabled != enabled
        pressed = newPressed
        enabled = newEnabled
        if (changed) { clearShaders(); invalidateSelf() }
        return changed
    }

    override fun getPadding(padding: Rect): Boolean {
        val h = (14 * density).toInt()
        val v = (6 * density).toInt()
        padding.set(h, v, h, v + (2 * density).toInt())
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val a = alphaFraction * if (enabled) 1f else 0.42f
        val r = radiusDp * density
        val depth = if (pressed) 0.6f * density else 2.4f * density
        val face = RectF(b.left + density, b.top + (if (pressed) 1.8f * density else 0f), b.right - density, b.bottom - depth)
        val colors = when (kind) {
            ButtonKind.PRIMARY, ButtonKind.CHIP_GOLD -> intArrayOf(Royal.goldLight, Royal.gold, Royal.goldDeep)
            ButtonKind.SUCCESS, ButtonKind.CHIP_SELECTED -> intArrayOf(Royal.turquoiseLight, Royal.turquoise, Royal.turquoiseDeep)
            ButtonKind.DANGER -> intArrayOf(0xFFE8656D.toInt(), Royal.crimson, Royal.crimsonDeep)
            ButtonKind.SECONDARY -> intArrayOf(0xFF24447F.toInt(), 0xFF18306A.toInt(), 0xFF0E1F48.toInt())
            ButtonKind.CHIP -> intArrayOf(0xFF1C346B.toInt(), 0xFF142A5A.toInt(), 0xFF0E1F48.toInt())
            ButtonKind.GHOST -> intArrayOf(0, 0, 0)
        }
        val edge = when (kind) {
            ButtonKind.PRIMARY, ButtonKind.CHIP_GOLD -> Royal.bronze
            ButtonKind.SUCCESS, ButtonKind.CHIP_SELECTED -> 0xFF0A5E5A.toInt()
            ButtonKind.DANGER -> 0xFF4E0D16.toInt()
            else -> 0xFF07112B.toInt()
        }
        if (kind == ButtonKind.GHOST) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Royal.alpha(Royal.gold, 0.10f * a)
            canvas.drawRoundRect(face, r, r, paint)
        }
        if (kind != ButtonKind.GHOST) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Royal.alpha(0xFF000000.toInt(), 0.45f * a)
            canvas.drawRoundRect(RectF(face.left, face.top + depth + density, face.right, face.bottom + depth), r, r, paint)
            paint.color = Royal.alpha(edge, a)
            canvas.drawRoundRect(RectF(face.left, face.top + depth, face.right, face.bottom + depth), r, r, paint)
            var c = colors
            if (pressed) c = c.map { Royal.mix(it, 0xFF000000.toInt(), 0.12f) }.toIntArray()
            paint.shader = cachedShader(1) { LinearGradient(0f, face.top, 0f, face.bottom, c, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) }
            paint.alpha = (255 * a).toInt()
            canvas.drawRoundRect(face, r, r, paint)
            paint.shader = cachedShader(2) { LinearGradient(0f, face.top, 0f, face.centerY(), Royal.alpha(Color.WHITE, 0.30f), 0, Shader.TileMode.CLAMP) }
            canvas.drawRoundRect(RectF(face.left + 2 * density, face.top + 1.5f * density, face.right - 2 * density, face.centerY()), r * 0.8f, r * 0.8f, paint)
            paint.shader = null
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (kind == ButtonKind.GHOST) 1.4f else 1.1f) * density
        paint.color = Royal.alpha(
            when (kind) {
                ButtonKind.SECONDARY, ButtonKind.GHOST -> Royal.gold
                ButtonKind.CHIP -> Royal.alpha(Royal.gold, 0.55f)
                ButtonKind.PRIMARY, ButtonKind.CHIP_GOLD -> Royal.goldLight
                ButtonKind.SUCCESS, ButtonKind.CHIP_SELECTED -> Royal.turquoiseLight
                ButtonKind.DANGER -> Royal.crimsonLight
            },
            a * if (kind == ButtonKind.CHIP) 1f else 0.9f
        )
        val half = paint.strokeWidth / 2f
        canvas.drawRoundRect(RectF(face.left + half, face.top + half, face.right - half, face.bottom - half), r, r, paint)
    }

    companion object {
        fun textColor(kind: ButtonKind): Int = when (kind) {
            ButtonKind.PRIMARY, ButtonKind.SUCCESS, ButtonKind.CHIP_SELECTED, ButtonKind.CHIP_GOLD -> Royal.night
            ButtonKind.DANGER -> Royal.ivory
            ButtonKind.SECONDARY, ButtonKind.GHOST -> Royal.goldLight
            ButtonKind.CHIP -> Royal.ivory
        }
    }
}


/** Gold progress rail with a turquoise or crimson fill. */
class ProgressDrawable(private val density: Float, var fraction: Float, var fill: Int = Royal.gold) : SimpleDrawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val r = b.height() / 2f
        paint.shader = null
        paint.color = Royal.alpha(0xFF000000.toInt(), 0.45f)
        canvas.drawRoundRect(RectF(b), r, r, paint)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            val right = b.left + b.width() * f
            paint.shader = LinearGradient(b.left.toFloat(), 0f, right, 0f, Royal.mix(fill, 0xFF000000.toInt(), 0.25f), fill, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(b.left.toFloat(), b.top.toFloat(), maxOf(right, b.left + b.height().toFloat()), b.bottom.toFloat()), r, r, paint)
            paint.shader = null
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * 0.8f
        paint.color = Royal.alpha(Royal.gold, 0.5f)
        canvas.drawRoundRect(RectF(b.left + density / 2, b.top + density / 2, b.right - density / 2, b.bottom - density / 2), r, r, paint)
        paint.style = Paint.Style.FILL
    }
}

object RoyalShapes {
    /** Eight-pointed khatam star made of two squares. */
    fun star8(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        canvas.drawPath(star8Path(cx, cy, radius), paint)
    }

    fun star8Path(cx: Float, cy: Float, radius: Float): Path = Path().apply {
        val inner = radius * 0.72f
        for (i in 0 until 16) {
            val angle = Math.PI / 8 * i - Math.PI / 2
            val rr = if (i % 2 == 0) radius else inner
            val x = cx + (rr * Math.cos(angle)).toFloat()
            val y = cy + (rr * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun starPath(cx: Float, cy: Float, radius: Float, points: Int, innerRatio: Float): Path = Path().apply {
        for (i in 0 until points * 2) {
            val angle = Math.PI / points * i - Math.PI / 2
            val rr = if (i % 2 == 0) radius else radius * innerRatio
            val x = cx + (rr * Math.cos(angle)).toFloat()
            val y = cy + (rr * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun diamond(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        canvas.drawPath(Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        }, paint)
    }

    /** Persian pointed (iwan) arch inside the given rect. */
    fun archPath(left: Float, top: Float, right: Float, bottom: Float): Path = Path().apply {
        val w = right - left
        val shoulder = top + w * 0.42f
        moveTo(left, bottom)
        lineTo(left, shoulder)
        cubicTo(left, top + w * 0.12f, left + w * 0.34f, top + w * 0.05f, left + w / 2f, top)
        cubicTo(right - w * 0.34f, top + w * 0.05f, right, top + w * 0.12f, right, shoulder)
        lineTo(right, bottom)
        close()
    }
}
