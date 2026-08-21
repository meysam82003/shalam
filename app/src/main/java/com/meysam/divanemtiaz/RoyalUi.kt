package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView

object RoyalPalette {
    val midnight = Color.rgb(3, 16, 34)
    val navy = Color.rgb(6, 27, 52)
    val navyRaised = Color.rgb(9, 40, 67)
    val teal = Color.rgb(26, 153, 144)
    val turquoise = Color.rgb(39, 210, 199)
    val gold = Color.rgb(218, 170, 77)
    val paleGold = Color.rgb(250, 211, 126)
    val cream = Color.rgb(250, 243, 224)
    val muted = Color.rgb(174, 195, 205)
    val crimson = Color.rgb(122, 30, 35)
    val green = Color.rgb(17, 96, 87)
    val danger = Color.rgb(221, 80, 73)
}

object PersianText {
    private val latin = "0123456789"
    private val persian = "۰۱۲۳۴۵۶۷۸۹"

    fun digits(value: Any, enabled: Boolean = true): String {
        var result = value.toString()
        if (enabled) latin.forEachIndexed { index, char -> result = result.replace(char, persian[index]) }
        return result
    }

    fun signed(value: Int, enabled: Boolean = true): String =
        digits(if (value > 0) "+$value" else value.toString(), enabled)
}

object RoyalFonts {
    fun regular(context: Context): Typeface = load(context, "fonts/Vazirmatn-Regular.ttf", Typeface.NORMAL)
    fun bold(context: Context): Typeface = load(context, "fonts/Vazirmatn-Bold.ttf", Typeface.BOLD)

    private fun load(context: Context, asset: String, style: Int): Typeface = runCatching {
        Typeface.createFromAsset(context.assets, asset)
    }.getOrElse { Typeface.create("sans-serif", style) }
}

fun royalShape(
    fill: Int,
    stroke: Int = RoyalPalette.gold,
    radiusDp: Float = 18f,
    strokeDp: Int = 1,
    context: Context
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(fill)
    setStroke((strokeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1), stroke)
    cornerRadius = radiusDp * context.resources.displayMetrics.density
}

fun royalGradient(context: Context, selected: Boolean = false): GradientDrawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    if (selected) intArrayOf(RoyalPalette.paleGold, RoyalPalette.gold)
    else intArrayOf(RoyalPalette.navyRaised, RoyalPalette.navy)
).apply {
    cornerRadius = 16f * context.resources.displayMetrics.density
    setStroke((1.2f * context.resources.displayMetrics.density).toInt(), RoyalPalette.gold)
}

open class AtlasCropView(
    context: Context,
    private var bitmapResource: Int,
    private var source: Rect,
    private val cornerDp: Float = 18f,
    private val border: Boolean = true
) : View(context) {
    private var bitmap: Bitmap = BitmapFactory.decodeResource(resources, bitmapResource)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = RoyalPalette.gold
    }

    fun update(resource: Int, rect: Rect) {
        bitmapResource = resource
        source = rect
        bitmap = BitmapFactory.decodeResource(resources, resource)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || bitmap.isRecycled) return
        val inset = if (border) borderPaint.strokeWidth / 2f else 0f
        val destination = RectF(inset, inset, width - inset, height - inset)
        val radius = cornerDp * resources.displayMetrics.density
        val path = Path().apply { addRoundRect(destination, radius, radius, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(RoyalPalette.navy)
        canvas.drawBitmap(bitmap, source, destination, imagePaint)
        canvas.restoreToCount(save)
        if (border) canvas.drawRoundRect(destination, radius, radius, borderPaint)
    }
}

class AvatarCropView(context: Context, avatarIndex: Int = 0) : AtlasCropView(
    context,
    avatarResource(avatarIndex),
    Rect(0, 0, 184, 228),
    20f,
    true
) {
    fun setAvatar(index: Int) = update(avatarResource(index), Rect(0, 0, 184, 228))

    companion object {
        private val avatars = intArrayOf(
            R.drawable.royal_avatar_01, R.drawable.royal_avatar_02,
            R.drawable.royal_avatar_03, R.drawable.royal_avatar_04,
            R.drawable.royal_avatar_05, R.drawable.royal_avatar_06,
            R.drawable.royal_avatar_07, R.drawable.royal_avatar_08,
            R.drawable.royal_avatar_09, R.drawable.royal_avatar_10,
            R.drawable.royal_avatar_11, R.drawable.royal_avatar_12,
            R.drawable.royal_avatar_13, R.drawable.royal_avatar_14,
            R.drawable.royal_avatar_15, R.drawable.royal_avatar_16
        )

        fun avatarResource(index: Int): Int = avatars[index.coerceIn(0, avatars.lastIndex)]
    }
}

class GameArtView(context: Context, game: GameType) : AtlasCropView(
    context,
    when (game) {
        GameType.SHALAM -> R.drawable.royal_game_shalam
        GameType.MENFI -> R.drawable.royal_game_menfi
        GameType.HEZARTAII -> R.drawable.royal_game_hezartaii
    },
    when (game) {
        GameType.SHALAM, GameType.MENFI, GameType.HEZARTAII -> Rect(0, 0, 640, 280)
    },
    16f,
    true
)

class RoyalDivider(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RoyalPalette.gold
        strokeWidth = resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val center = height / 2f
        canvas.drawLine(0f, center, width * .43f, center, paint)
        canvas.drawLine(width * .57f, center, width.toFloat(), center, paint)
        paint.color = RoyalPalette.turquoise
        canvas.rotate(45f, width / 2f, center)
        canvas.drawRect(width / 2f - 5f, center - 5f, width / 2f + 5f, center + 5f, paint)
        paint.color = RoyalPalette.gold
    }
}

fun TextView.preparePersianText(context: Context, bold: Boolean = false) {
    gravity = if (gravity == Gravity.NO_GRAVITY) Gravity.RIGHT or Gravity.CENTER_VERTICAL else gravity
    textDirection = View.TEXT_DIRECTION_RTL
    typeface = if (bold) RoyalFonts.bold(context) else RoyalFonts.regular(context)
    includeFontPadding = true
    setLineSpacing(0f, 1.15f)
}
