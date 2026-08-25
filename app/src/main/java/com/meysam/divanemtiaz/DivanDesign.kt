package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Final royal-minimal design system: dark lapis + gold with real 3D portrait assets. */
object DivanTheme {
    val bg = Color.rgb(6, 14, 25)
    val surface = Color.rgb(13, 28, 43)
    val surfaceHigh = Color.rgb(20, 39, 57)
    val surfaceSoft = Color.rgb(25, 47, 66)
    val ivory = Color.rgb(247, 239, 219)
    val gold = Color.rgb(224, 186, 94)
    val goldSoft = Color.rgb(173, 143, 78)
    val turquoise = Color.rgb(48, 154, 151)
    val muted = Color.rgb(158, 174, 187)
    val line = Color.rgb(48, 69, 87)
    val emerald = Color.rgb(35, 116, 91)
    val crimson = Color.rgb(145, 55, 68)
    val blue = Color.rgb(49, 104, 143)
    val warning = Color.rgb(190, 132, 55)
    val danger = Color.rgb(151, 51, 60)
    val disabled = Color.rgb(67, 76, 85)
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
        val raw = if (value > 0) "+$value" else value.toString()
        return fa(raw, persianDigits)
    }

    fun initials(label: String): String {
        val clean = label.trim()
        if (clean.isBlank()) return "د"
        val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}" else clean.take(2)
    }
}

fun TextView.useDivanTypography(bold: Boolean = false) {
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    includeFontPadding = false
    gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
    textDirection = View.TEXT_DIRECTION_RTL
}

/**
 * Avatar component used everywhere in the final UI.
 * It first tries royal_avatar_01...royal_avatar_16 from drawable-nodpi and draws
 * them as a circular center-cropped 3D portrait. If an asset is missing it falls
 * back to a generated Persian seal, so the screen can never crash for an avatar.
 */
class SealAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var label: String = "دیوان"
        set(value) { field = value; invalidate() }
    var variant: Int = 0
        set(value) { field = value; cachedVariant = Int.MIN_VALUE; invalidate() }
    var usePortrait: Boolean = true
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var cachedVariant = Int.MIN_VALUE
    private var cachedBitmap: Bitmap? = null
    private val palette = intArrayOf(
        DivanTheme.emerald, DivanTheme.crimson, DivanTheme.blue,
        Color.rgb(95, 72, 119), Color.rgb(126, 91, 48), Color.rgb(46, 92, 102)
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = context.dp(64)
        setMeasuredDimension(resolveSize(desired, widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    private fun portrait(): Bitmap? {
        if (!usePortrait) return null
        if (cachedVariant == variant) return cachedBitmap
        cachedVariant = variant
        val number = abs(variant).mod(16) + 1
        val name = String.format(Locale.US, "royal_avatar_%02d", number)
        val id = resources.getIdentifier(name, "drawable", context.packageName)
        cachedBitmap = if (id != 0) runCatching { BitmapFactory.decodeResource(resources, id) }.getOrNull() else null
        return cachedBitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * .44f
        val bitmap = portrait()

        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val diameter = r * 2f
            val scale = max(diameter / bitmap.width, diameter / bitmap.height)
            val dx = cx - bitmap.width * scale / 2f
            val dy = cy - bitmap.height * scale / 2f
            matrix.reset()
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
        } else {
            paint.style = Paint.Style.FILL
            paint.color = palette[abs(variant) % palette.size]
            canvas.drawCircle(cx, cy, r, paint)
            paint.color = DivanTheme.ivory
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textSize = r * .56f
            val fm = paint.fontMetrics
            canvas.drawText(DivanText.initials(label), cx, cy - (fm.ascent + fm.descent) / 2f, paint)
        }

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(2).toFloat()
        paint.color = DivanTheme.gold
        canvas.drawCircle(cx, cy, r, paint)
        paint.strokeWidth = context.dp(1).toFloat()
        paint.color = Color.argb(160, 224, 186, 94)
        canvas.drawCircle(cx, cy, r * .90f, paint)
    }
}

/** Minimal Persian-inspired divider: two lines and a central diamond. */
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
        val path = Path().apply {
            moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close()
        }
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }
}
