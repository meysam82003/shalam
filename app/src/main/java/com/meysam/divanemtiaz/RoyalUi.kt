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
    // Semantic tokens of the active style (see [ThemeEngine]); the defaults are the classic royal look.
    /** Text and icons on accent fills; also the deepest shade of dark styles. */
    @JvmField var night = 0xFF050B1A.toInt()
    /** Page scrim behind bars and overlays. */
    @JvmField var scrim = 0xFF050B1A.toInt()
    @JvmField var deep = 0xFF0A1530.toInt()
    @JvmField var lapis = 0xFF12285A.toInt()
    @JvmField var lapisLight = 0xFF1D3B7C.toInt()
    @JvmField var panelTop = 0xFF16295A.toInt()
    @JvmField var panelBottom = 0xFF0B1937.toInt()
    /** Accent: icons, badges and highlights; [goldLight] is the accent colour of headings and values. */
    @JvmField var gold = 0xFFE6BE6A.toInt()
    @JvmField var goldLight = 0xFFFBE3A6.toInt()
    @JvmField var goldDeep = 0xFFB1832F.toInt()
    @JvmField var bronze = 0xFF6E4A1A.toInt()
    /** Metal of frames, medallions and seals. */
    @JvmField var metalLight = 0xFFFBE3A6.toInt()
    @JvmField var metal = 0xFFE6BE6A.toInt()
    @JvmField var metalDeep = 0xFFB1832F.toInt()
    @JvmField var metalShadow = 0xFF6E4A1A.toInt()
    /** Success and selection. */
    @JvmField var turquoise = 0xFF2ED3C6.toInt()
    @JvmField var turquoiseLight = 0xFF8EF0E6.toInt()
    @JvmField var turquoiseDeep = 0xFF0E8C86.toInt()
    /** Warnings and negative scores. */
    @JvmField var crimson = 0xFFD0414B.toInt()
    @JvmField var crimsonLight = 0xFFF5868C.toInt()
    @JvmField var crimsonDeep = 0xFF7A1724.toInt()
    /** Text: primary, secondary, tertiary and page titles. */
    @JvmField var ivory = 0xFFF8F0DC.toInt()
    @JvmField var muted = 0xFFB3C3DD.toInt()
    @JvmField var dim = 0xFF7487AE.toInt()
    @JvmField var title = 0xFFFBE3A6.toInt()
    /** Bright red of counters that reached their limit (last seconds, enough votes). */
    @JvmField var alert = 0xFFFF4F58.toInt()

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

/** The original royal palette, for artwork that keeps its own colours in every style (the physical role cards). */
object RoyalClassic {
    const val gold = 0xFFE6BE6A.toInt()
    const val goldLight = 0xFFFBE3A6.toInt()
    const val goldDeep = 0xFFB1832F.toInt()
    const val bronze = 0xFF6E4A1A.toInt()
    const val turquoiseLight = 0xFF8EF0E6.toInt()
    const val crimson = 0xFFD0414B.toInt()
    const val crimsonLight = 0xFFF5868C.toInt()
    const val ivory = 0xFFF8F0DC.toInt()
    const val night = 0xFF050B1A.toInt()
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

/** Page background of the active style: base gradient, texture, coloured lights and (dark styles) a vignette. */
class BackdropDrawable(private val density: Float) : SimpleDrawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tile: Bitmap? = null
    private var tileVersion = -1

    private var cache: Bitmap? = null
    private var cacheVersion = -1

    /** The page is rendered once per size and style; scrolling then only blits one bitmap. */
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val cached = cache?.takeIf { it.width == b.width() && it.height == b.height() && cacheVersion == ThemeEngine.version } ?: render(b.width(), b.height())
        canvas.drawBitmap(cached, b.left.toFloat(), b.top.toFloat(), null)
    }

    private fun render(width: Int, height: Int): Bitmap {
        cache?.recycle()
        val t = ThemeEngine.spec
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val rect = RectF(0f, 0f, w, h)
        paint.color = Color.BLACK
        paint.shader = LinearGradient(0f, 0f, 0f, h, t.bg, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint)
        paint.color = Color.BLACK
        paint.shader = BitmapShader(patternTile(t), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        canvas.drawRect(rect, paint)
        t.glows.forEach { g ->
            paint.color = Color.BLACK
            paint.shader = RadialGradient(w * g.x, h * g.y, (w * g.radius).coerceAtLeast(1f), intArrayOf(g.color, Royal.alpha(g.color, 0.4f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint)
        }
        if (t.vignette != 0) {
            paint.color = Color.BLACK
            paint.shader = RadialGradient(w / 2f, h * 0.45f, maxOf(w, h) * 0.75f, intArrayOf(0, 0, t.vignette), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint)
        }
        paint.shader = null
        cache = bitmap
        cacheVersion = ThemeEngine.version
        return bitmap
    }

    private fun patternTile(t: ThemeSpec): Bitmap {
        tile?.takeIf { tileVersion == ThemeEngine.version }?.let { return it }
        val unit = when (t.pattern) {
            ThemePattern.KHATAM -> 64f
            ThemePattern.LATTICE -> 46f
            ThemePattern.DIAMOND -> 34f
            ThemePattern.GIRIH -> 76f
            ThemePattern.ARABESQUE -> 58f
            ThemePattern.DOTS -> 22f
            ThemePattern.NEON -> 30f
        }
        val size = (unit * density).toInt().coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = density * 0.9f }
        val col = t.patternColor
        val s = size.toFloat()
        val half = s / 2f
        when (t.pattern) {
            ThemePattern.KHATAM -> {
                p.color = Royal.alpha(col, 0.075f)
                RoyalShapes.star8(c, half, half, s * 0.30f, p)
                p.style = Paint.Style.FILL
                p.color = Royal.alpha(col, 0.10f)
                listOf(0f to 0f, s to 0f, 0f to s, s to s).forEach { (x, y) -> c.drawCircle(x, y, density * 1.6f, p) }
                p.style = Paint.Style.STROKE
                p.color = Royal.alpha(col, 0.045f)
                c.drawLine(0f, half, s * 0.18f, half, p)
                c.drawLine(s * 0.82f, half, s, half, p)
                c.drawLine(half, 0f, half, s * 0.18f, p)
                c.drawLine(half, s * 0.82f, half, s, p)
            }
            ThemePattern.LATTICE -> {
                p.strokeWidth = density * 0.7f
                p.color = Royal.alpha(col, 0.05f)
                c.drawLine(0f, 0f, s, s, p)
                c.drawLine(s, 0f, 0f, s, p)
                p.style = Paint.Style.FILL
                p.color = Royal.alpha(col, 0.08f)
                c.drawCircle(half, half, density * 1.1f, p)
            }
            ThemePattern.DIAMOND -> {
                p.strokeWidth = density * 0.7f
                p.color = Royal.alpha(col, 0.06f)
                c.drawPath(Path().apply { moveTo(half, 0f); lineTo(s, half); lineTo(half, s); lineTo(0f, half); close() }, p)
                p.style = Paint.Style.FILL
                p.color = Royal.alpha(col, 0.07f)
                c.drawCircle(half, half, density * 0.9f, p)
            }
            ThemePattern.GIRIH -> {
                p.strokeWidth = density * 0.8f
                p.color = Royal.alpha(col, 0.06f)
                RoyalShapes.star8(c, half, half, s * 0.24f, p)
                p.color = Royal.alpha(col, 0.035f)
                c.drawLine(half, 0f, half, half - s * 0.24f, p)
                c.drawLine(half, half + s * 0.24f, half, s, p)
                c.drawLine(0f, half, half - s * 0.24f, half, p)
                c.drawLine(half + s * 0.24f, half, s, half, p)
                listOf(0f to 0f, s to 0f, 0f to s, s to s).forEach { (x, y) -> c.drawCircle(x, y, s * 0.12f, p) }
            }
            ThemePattern.ARABESQUE -> {
                p.strokeWidth = density * 0.8f
                p.color = Royal.alpha(col, 0.05f)
                listOf(0f to 0f, s to 0f, 0f to s, s to s).forEach { (x, y) -> c.drawCircle(x, y, half, p) }
                p.style = Paint.Style.FILL
                p.color = Royal.alpha(col, 0.07f)
                RoyalShapes.diamond(c, half, half, density * 2.4f, p)
            }
            ThemePattern.DOTS -> {
                p.style = Paint.Style.FILL
                p.color = Royal.alpha(col, 0.10f)
                c.drawCircle(half, half, density * 0.9f, p)
                p.color = Royal.alpha(col, 0.05f)
                c.drawCircle(0f, 0f, density * 0.7f, p)
                c.drawCircle(s, 0f, density * 0.7f, p)
                c.drawCircle(0f, s, density * 0.7f, p)
                c.drawCircle(s, s, density * 0.7f, p)
            }
            ThemePattern.NEON -> {
                p.style = Paint.Style.FILL
                p.color = Royal.alpha(col, 0.05f)
                c.drawCircle(half, half, density * 0.8f, p)
            }
        }
        tile = bitmap
        tileVersion = ThemeEngine.version
        return bitmap
    }
}

enum class PanelStyle { NORMAL, RAISED, FLAT, SELECTED, DANGER, SUCCESS, GHOST }

/** Card face of the active style: frame, fill, light and shadow follow [ThemeEngine.spec]. */
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
        val t = ThemeEngine.spec
        if (t.frame == ThemeFrame.ORNATE) ornate(canvas, t) else modern(canvas, t)
        paint.shader = null
    }

    /** Embossed card with metal frame, inner hairline, soft shadow and (for RAISED) corner jewels. */
    private fun ornate(canvas: Canvas, t: ThemeSpec) {
        val b = bounds
        val r = radiusDp * density
        val shadow = t.shadowDepth * density
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
        val top0 = opaque(t.surface)
        val bottom0 = opaque(t.surfaceLow)
        val (top, bottom) = when (style) {
            PanelStyle.SELECTED -> Royal.mix(top0, Royal.turquoiseDeep, 0.35f) to Royal.mix(bottom0, Royal.turquoiseDeep, 0.25f)
            PanelStyle.DANGER -> Royal.mix(top0, Royal.crimsonDeep, 0.55f) to Royal.mix(bottom0, Royal.crimsonDeep, 0.45f)
            PanelStyle.SUCCESS -> Royal.mix(top0, Royal.turquoiseDeep, 0.45f) to Royal.mix(bottom0, Royal.turquoiseDeep, 0.3f)
            PanelStyle.RAISED -> t.raised to t.raisedLow
            PanelStyle.FLAT -> t.flat to t.flatLow
            PanelStyle.GHOST -> 0 to 0
            PanelStyle.NORMAL -> t.surface to t.surfaceLow
        }
        if (style != PanelStyle.GHOST) {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP)
            paint.alpha = (255 * alphaFraction).toInt()
            canvas.drawRoundRect(rect, r, r, paint)
            paint.shader = LinearGradient(0f, rect.top, 0f, rect.top + (rect.height() * 0.5f), Royal.alpha(Color.WHITE, t.gloss), 0, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, r, r, paint)
        }
        val (frameA, frameB) = when (style) {
            PanelStyle.SELECTED, PanelStyle.SUCCESS -> Royal.turquoiseLight to Royal.turquoiseDeep
            PanelStyle.DANGER -> Royal.crimsonLight to Royal.crimsonDeep
            PanelStyle.FLAT -> Royal.alpha(Royal.metal, 0.55f) to Royal.alpha(Royal.metalDeep, 0.35f)
            else -> t.border to t.borderLow
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (style == PanelStyle.RAISED || style == PanelStyle.SELECTED) 1.8f else t.frameWidth) * density
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, frameA, frameB, Shader.TileMode.CLAMP)
        paint.alpha = (235 * alphaFraction).toInt()
        val half = paint.strokeWidth / 2f
        canvas.drawRoundRect(RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half), r, r, paint)
        if (style == PanelStyle.RAISED || style == PanelStyle.NORMAL || style == PanelStyle.SELECTED) {
            paint.shader = null
            paint.strokeWidth = 0.8f * density
            paint.color = Royal.alpha(if (style == PanelStyle.SELECTED) Royal.turquoise else Royal.metal, 0.28f * alphaFraction)
            val inset = 4.5f * density
            canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), r - inset * 0.7f, r - inset * 0.7f, paint)
        }
        if (style == PanelStyle.RAISED && t.jewels) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            val jewel = 3.6f * density
            val off = r * 0.42f
            listOf(rect.left + off to rect.top + off, rect.right - off to rect.top + off, rect.left + off to rect.bottom - off, rect.right - off to rect.bottom - off).forEach { (x, y) ->
                paint.color = Royal.alpha(Royal.metal, 0.9f * alphaFraction)
                RoyalShapes.diamond(canvas, x, y, jewel, paint)
                paint.color = Royal.alpha(Royal.turquoise, 0.9f * alphaFraction)
                canvas.drawCircle(x, y, jewel * 0.38f, paint)
            }
        }
    }

    /** Hairline, soft and glass cards of the modern styles. */
    private fun modern(canvas: Canvas, t: ThemeSpec) {
        val b = bounds
        val r = radiusDp * density * t.radius
        val depth = t.shadowDepth * density
        rect.set(b.left + density, b.top.toFloat() + density * 0.5f, b.right - density, b.bottom - depth)
        val a = alphaFraction
        if (style != PanelStyle.GHOST) {
            val strength = if (style == PanelStyle.FLAT) 0.45f else 1f
            paint.style = Paint.Style.FILL
            paint.shader = null
            val base = Color.alpha(t.shadow) / 255f
            floatArrayOf(0.42f, 0.24f, 0.13f, 0.06f).forEachIndexed { i, k ->
                paint.color = Royal.alpha(t.shadow or 0xFF000000.toInt(), base * k * strength * a)
                val grow = i * 1.3f * density
                val dy = depth * (0.45f + i * 0.22f)
                canvas.drawRoundRect(RectF(rect.left - grow, rect.top + dy, rect.right + grow, rect.bottom + dy + grow * 0.5f), r + grow, r + grow, paint)
            }
        }
        val tint = if (t.light) 0.10f else 0.28f
        val (top, bottom) = when (style) {
            PanelStyle.SELECTED -> Royal.mix(t.surface, Royal.turquoise, tint) to Royal.mix(t.surfaceLow, Royal.turquoise, tint * 0.75f)
            PanelStyle.SUCCESS -> Royal.mix(t.surface, Royal.turquoise, tint * 1.2f) to Royal.mix(t.surfaceLow, Royal.turquoise, tint)
            PanelStyle.DANGER -> Royal.mix(t.surface, Royal.crimson, if (t.light) 0.09f else 0.36f) to Royal.mix(t.surfaceLow, Royal.crimson, if (t.light) 0.07f else 0.28f)
            PanelStyle.RAISED -> t.raised to t.raisedLow
            PanelStyle.FLAT -> t.flat to t.flatLow
            PanelStyle.GHOST -> 0 to 0
            PanelStyle.NORMAL -> t.surface to t.surfaceLow
        }
        if (style != PanelStyle.GHOST) {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP)
            paint.alpha = (255 * a).toInt()
            canvas.drawRoundRect(rect, r, r, paint)
            if (t.gloss > 0f) {
                paint.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * 0.5f, Royal.alpha(Color.WHITE, t.gloss), 0, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
        val (frameA, frameB) = when (style) {
            PanelStyle.SELECTED, PanelStyle.SUCCESS -> Royal.turquoise to Royal.alpha(Royal.turquoiseDeep, 0.8f)
            PanelStyle.DANGER -> Royal.crimson to Royal.alpha(Royal.crimsonDeep, 0.8f)
            PanelStyle.FLAT -> Royal.alpha(t.border, 0.75f) to Royal.alpha(t.borderLow, 0.6f)
            else -> t.border to t.borderLow
        }
        val width = (t.frameWidth + if (style == PanelStyle.SELECTED || style == PanelStyle.RAISED) 0.4f else 0f) * density
        if (t.frame == ThemeFrame.GLOW && style != PanelStyle.FLAT && style != PanelStyle.GHOST) {
            paint.style = Paint.Style.STROKE
            paint.shader = null
            floatArrayOf(5f, 2.8f).forEachIndexed { i, wdp ->
                paint.strokeWidth = wdp * density
                paint.color = Royal.alpha(frameA, (if (i == 0) 0.07f else 0.14f) * a)
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.shader = LinearGradient(rect.left, rect.top, if (t.frame == ThemeFrame.GLOW) rect.right else rect.left, rect.bottom, frameA, frameB, Shader.TileMode.CLAMP)
        paint.alpha = (255 * a).toInt()
        val half = width / 2f
        canvas.drawRoundRect(RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half), r, r, paint)
        if (t.inner != 0 && (style == PanelStyle.RAISED || style == PanelStyle.NORMAL || style == PanelStyle.SELECTED)) {
            paint.shader = null
            paint.strokeWidth = 0.8f * density
            paint.color = Royal.alpha(if (style == PanelStyle.SELECTED) Royal.alpha(Royal.turquoise, 0.35f) else t.inner, a)
            val inset = 4f * density
            canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), (r - inset * 0.8f).coerceAtLeast(0f), (r - inset * 0.8f).coerceAtLeast(0f), paint)
        }
    }

    private fun opaque(color: Int): Int = color or 0xFF000000.toInt()
}

enum class ButtonKind { PRIMARY, SECONDARY, SUCCESS, DANGER, GHOST, CHIP, CHIP_SELECTED, CHIP_GOLD }

/** Button and chip face of the active style; reacts to pressed / disabled state. */
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
        if (changed) invalidateSelf()
        return changed
    }

    override fun getPadding(padding: Rect): Boolean {
        val h = (14 * density).toInt()
        val v = (6 * density).toInt()
        padding.set(h, v, h, v + (2 * density).toInt())
        return true
    }

    private val filled: Boolean get() = kind == ButtonKind.PRIMARY || kind == ButtonKind.CHIP_GOLD || kind == ButtonKind.SUCCESS ||
        kind == ButtonKind.CHIP_SELECTED || kind == ButtonKind.DANGER

    private fun colors(t: ThemeSpec): IntArray = when (kind) {
        ButtonKind.PRIMARY, ButtonKind.CHIP_GOLD -> t.primary
        ButtonKind.SUCCESS, ButtonKind.CHIP_SELECTED -> t.successFill
        ButtonKind.DANGER -> t.dangerFill
        ButtonKind.SECONDARY -> t.secondary
        ButtonKind.CHIP -> t.chip
        ButtonKind.GHOST -> intArrayOf(0, 0, 0)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val t = ThemeEngine.spec
        if (t.button == ThemeButton.BEVEL) bevel(canvas, t) else modern(canvas, t)
        paint.shader = null
    }

    /** Beveled 3D face of the classic style. */
    private fun bevel(canvas: Canvas, t: ThemeSpec) {
        val b = bounds
        val a = alphaFraction * if (enabled) 1f else 0.42f
        val r = radiusDp * density
        val depth = if (pressed) 0.6f * density else 2.4f * density
        val face = RectF(b.left + density, b.top + (if (pressed) 1.8f * density else 0f), b.right - density, b.bottom - depth)
        val colors = colors(t)
        val edge = when (kind) {
            ButtonKind.PRIMARY, ButtonKind.CHIP_GOLD -> t.primaryEdge
            ButtonKind.SUCCESS, ButtonKind.CHIP_SELECTED -> 0xFF0A5E5A.toInt()
            ButtonKind.DANGER -> 0xFF4E0D16.toInt()
            else -> 0xFF07112B.toInt()
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
            paint.shader = LinearGradient(0f, face.top, 0f, face.bottom, c, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            paint.alpha = (255 * a).toInt()
            canvas.drawRoundRect(face, r, r, paint)
            gloss(canvas, face, r, 0.30f)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (kind == ButtonKind.GHOST) 1.4f else 1.1f) * density
        paint.color = Royal.alpha(
            when (kind) {
                ButtonKind.SECONDARY, ButtonKind.GHOST -> t.secondaryBorder
                ButtonKind.CHIP -> t.chipBorder
                ButtonKind.PRIMARY, ButtonKind.CHIP_GOLD -> t.primary[0]
                ButtonKind.SUCCESS, ButtonKind.CHIP_SELECTED -> t.successFill[0]
                ButtonKind.DANGER -> Royal.crimsonLight
            },
            a * if (kind == ButtonKind.CHIP) 1f else 0.9f
        )
        val half = paint.strokeWidth / 2f
        canvas.drawRoundRect(RectF(face.left + half, face.top + half, face.right - half, face.bottom - half), r, r, paint)
    }

    /** Flat, matte, gradient and glass faces of the modern styles. */
    private fun modern(canvas: Canvas, t: ThemeSpec) {
        val b = bounds
        val a = alphaFraction * if (enabled) 1f else 0.42f
        val lift = 2.6f * density
        val face = RectF(b.left + density, b.top + (if (pressed) 1.2f * density else 0f), b.right - density, b.bottom - lift)
        val r = (radiusDp * density * t.buttonRadius).coerceAtMost(face.height() / 2f)
        var colors = colors(t)
        if (pressed) colors = colors.map { Royal.mix(it, if (t.light && !filled) 0xFF5A6478.toInt() else 0xFF000000.toInt(), 0.10f) }.toIntArray()
        paint.style = Paint.Style.FILL
        paint.shader = null
        if (kind != ButtonKind.GHOST) {
            // Shadow: coloured under filled buttons, neutral under plain ones.
            val shadowColor = when {
                t.button == ThemeButton.MATTE -> 0xFF000000.toInt()
                filled -> colors[colors.size / 2] or 0xFF000000.toInt()
                else -> t.shadow or 0xFF000000.toInt()
            }
            val strength = when {
                t.button == ThemeButton.MATTE -> 0.5f
                filled -> if (t.light) 0.28f else 0.42f
                else -> Color.alpha(t.shadow) / 255f * 0.8f
            } * (if (pressed) 0.5f else 1f)
            floatArrayOf(0.55f, 0.28f, 0.12f).forEachIndexed { i, k ->
                paint.color = Royal.alpha(shadowColor, strength * k * a)
                val grow = i * 1.2f * density
                val dy = lift * (0.55f + i * 0.3f) - (if (pressed) density else 0f)
                canvas.drawRoundRect(RectF(face.left - grow * 0.6f, face.top + dy, face.right + grow * 0.6f, face.bottom + dy + grow * 0.4f), r + grow, r + grow, paint)
            }
            if (t.glow && filled && !pressed) {
                paint.style = Paint.Style.STROKE
                floatArrayOf(9f, 5f, 2.4f).forEachIndexed { i, wdp ->
                    paint.strokeWidth = wdp * density
                    paint.color = Royal.alpha(colors[i % colors.size] or 0xFF000000.toInt(), (0.05f + i * 0.05f) * a)
                    canvas.drawRoundRect(face, r, r, paint)
                }
                paint.style = Paint.Style.FILL
            }
            val horizontal = filled && t.primaryHorizontal
            paint.shader = if (horizontal) LinearGradient(face.right, 0f, face.left, 0f, colors, null, Shader.TileMode.CLAMP)
            else LinearGradient(0f, face.top, 0f, face.bottom, colors, null, Shader.TileMode.CLAMP)
            paint.alpha = (255 * a).toInt()
            canvas.drawRoundRect(face, r, r, paint)
            paint.shader = null
            when (t.button) {
                ThemeButton.GRADIENT -> if (filled) gloss(canvas, face, r, 0.22f)
                ThemeButton.GLASS -> gloss(canvas, face, r, if (filled) 0.26f else 0.12f)
                ThemeButton.MATTE -> gloss(canvas, face, r, 0.06f)
                else -> Unit
            }
        }
        val border = when (kind) {
            ButtonKind.SECONDARY -> t.secondaryBorder
            ButtonKind.CHIP -> t.chipBorder
            ButtonKind.GHOST -> Royal.alpha(Royal.gold, 0.75f)
            else -> if (t.button == ThemeButton.GLASS) Royal.alpha(Color.WHITE, 0.28f) else 0
        }
        if (border != 0) {
            paint.style = Paint.Style.STROKE
            paint.shader = null
            paint.strokeWidth = (if (kind == ButtonKind.GHOST) 1.3f else 1f) * density
            paint.color = Royal.alpha(border, a)
            val half = paint.strokeWidth / 2f
            canvas.drawRoundRect(RectF(face.left + half, face.top + half, face.right - half, face.bottom - half), r, r, paint)
        }
    }

    /** Top shine kept strictly inside the face, so round buttons never show corners outside the circle. */
    private fun gloss(canvas: Canvas, face: RectF, r: Float, strength: Float) {
        val inner = RectF(face.left + 1.6f * density, face.top + 1.4f * density, face.right - 1.6f * density, face.bottom - 1.4f * density)
        val innerR = (r - 1.5f * density).coerceAtLeast(0f)
        val clip = Path().apply { addRoundRect(inner, innerR, innerR, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, inner.top, 0f, face.centerY(), Royal.alpha(Color.WHITE, strength * alphaFraction * if (enabled) 1f else 0.42f), 0, Shader.TileMode.CLAMP)
        canvas.drawRect(inner.left, inner.top, inner.right, face.centerY(), paint)
        canvas.restore()
        paint.shader = null
    }

    companion object {
        fun textColor(kind: ButtonKind): Int {
            val t = ThemeEngine.spec
            return when (kind) {
                ButtonKind.PRIMARY, ButtonKind.CHIP_GOLD -> t.primaryText
                ButtonKind.SUCCESS, ButtonKind.CHIP_SELECTED -> t.successOn
                ButtonKind.DANGER -> t.dangerOn
                ButtonKind.SECONDARY, ButtonKind.GHOST -> t.secondaryText
                ButtonKind.CHIP -> t.chipText
            }
        }
    }
}

/** Progress rail with a coloured fill. */
class ProgressDrawable(private val density: Float, var fraction: Float, var fill: Int = Royal.gold) : SimpleDrawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val light = ThemeEngine.spec.light
        val r = b.height() / 2f
        paint.shader = null
        paint.color = if (light) Royal.alpha(Royal.ivory, 0.09f) else Royal.alpha(0xFF000000.toInt(), 0.45f)
        canvas.drawRoundRect(RectF(b), r, r, paint)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            val right = b.left + b.width() * f
            paint.color = Color.BLACK
            paint.shader = LinearGradient(b.left.toFloat(), 0f, right, 0f, Royal.mix(fill, if (light) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0.25f), fill, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(b.left.toFloat(), b.top.toFloat(), maxOf(right, b.left + b.height().toFloat()), b.bottom.toFloat()), r, r, paint)
            paint.shader = null
        }
        if (!light) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density * 0.8f
            paint.color = Royal.alpha(Royal.metal, 0.5f)
            canvas.drawRoundRect(RectF(b.left + density / 2, b.top + density / 2, b.right - density / 2, b.bottom - density / 2), r, r, paint)
            paint.style = Paint.Style.FILL
        }
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
