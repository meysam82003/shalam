package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Enamel colours of the three sides; every role card, seal and badge uses them. */
object MafiaColors {
    const val CITY = 0xFF0E7F78.toInt()
    const val MAFIA = 0xFF9E2633.toInt()
    const val INDEPENDENT = 0xFF5B2C8C.toInt()
    const val NEUTRAL = 0xFF23367A.toInt()
    const val PLATE_TOP = 0xFF5E0F1B.toInt()
    const val PLATE_BOTTOM = 0xFF2A050B.toInt()

    fun light(side: Int): Int = when (side) {
        CITY -> Royal.turquoiseLight
        MAFIA -> Royal.crimsonLight
        INDEPENDENT -> 0xFFC6A2F2.toInt()
        else -> Royal.goldLight
    }
}

/**
 * A role or last-move card in the style of a framed playing card: a lit portrait panel (the role's
 * gold emblem, or a picture the narrator chose), a gold frame with corner ornaments, a crimson name
 * plate with a medallion, the name and a one-line description. Everything is drawn in code.
 */
class RoleCardView(context: Context) : CachedArtView(context) {
    var glyph: MafiaGlyph = MafiaGlyph.CITIZEN
        set(value) { field = value; invalidateArt() }
    var tint: Int = MafiaColors.CITY
        set(value) { field = value; invalidateArt() }
    var title: String = ""
        set(value) { field = value; invalidateArt() }
    var subtitle: String = ""
        set(value) { field = value; invalidateArt() }
    var portrait: Bitmap? = null
        set(value) { field = value; invalidateArt() }
    /** Card back, used while the card is still hidden from the player. */
    var faceDown: Boolean = false
        set(value) { field = value; invalidateArt() }
    var dead: Boolean = false
        set(value) { field = value; invalidateArt() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun artKey(): Any = listOf(glyph, tint, title, subtitle, portrait?.generationId ?: 0, faceDown, dead)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val h = if (mode == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else (w * RATIO).toInt()
        setMeasuredDimension(w, h)
    }

    override fun drawArt(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = w / 100f
        val outer = RectF(u * 1f, u * 1f, w - u * 1f, h - u * 1f)
        // Shadow and body.
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x66000000
        canvas.drawRoundRect(RectF(outer.left, outer.top + u * 1.2f, outer.right, outer.bottom + u * 0.8f), u * 5f, u * 5f, paint)
        paint.color = Color.BLACK
        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF1A0B12.toInt(), 0xFF0B0610.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(outer, u * 5f, u * 5f, paint)
        paint.shader = null
        if (faceDown) {
            drawBack(canvas, outer, u)
        } else {
            val plateTop = h * 0.64f
            val panel = RectF(outer.left + u * 4f, outer.top + u * 4f, outer.right - u * 4f, plateTop + u * 4f)
            drawPortrait(canvas, panel, u)
            drawPlate(canvas, RectF(outer.left + u * 3f, plateTop, outer.right - u * 3f, outer.bottom - u * 3f), u)
        }
        drawFrame(canvas, outer, u)
        if (dead) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = 0xAA050B1A.toInt()
            canvas.drawRoundRect(outer, u * 5f, u * 5f, paint)
            paint.color = Royal.crimson
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = u * 3f
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(w * 0.22f, h * 0.22f, w * 0.78f, h * 0.78f, paint)
            canvas.drawLine(w * 0.78f, h * 0.22f, w * 0.22f, h * 0.78f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawPortrait(canvas: Canvas, r: RectF, u: Float) {
        canvas.save()
        val clip = Path().apply { addRoundRect(r, u * 3f, u * 3f, Path.Direction.CW) }
        canvas.clipPath(clip)
        val photo = portrait
        if (photo != null && !photo.isRecycled) {
            val scale = maxOf(r.width() / photo.width, r.height() / photo.height)
            val dw = photo.width * scale
            val dh = photo.height * scale
            val m = Matrix().apply { setScale(scale, scale); postTranslate(r.centerX() - dw / 2f, r.top + (r.height() - dh) * 0.3f) }
            paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            paint.color = Color.BLACK
            canvas.drawRect(r, paint)
            paint.shader = null
        } else {
            // Stage light in the side colour, a faint khatam lattice and the role emblem in gold.
            paint.color = Color.BLACK
            paint.shader = RadialGradient(r.centerX(), r.top + r.height() * 0.38f, r.height() * 0.85f,
                intArrayOf(Royal.mix(tint, Color.WHITE, 0.15f), Royal.mix(tint, Color.BLACK, 0.55f), 0xFF07040A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(r, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = u * 0.35f
            paint.color = Royal.alpha(Royal.goldLight, 0.09f)
            val step = u * 14f
            var y = r.top
            var row = 0
            while (y < r.bottom + step) {
                var x = r.left + if (row % 2 == 0) 0f else step / 2f
                while (x < r.right + step) {
                    RoyalShapes.star8(canvas, x, y, step * 0.32f, paint)
                    x += step
                }
                y += step * 0.86f
                row++
            }
            paint.style = Paint.Style.FILL
            // Halo behind the emblem.
            val cx = r.centerX()
            val cy = r.top + r.height() * 0.46f
            val size = min(r.width(), r.height()) * 0.7f
            paint.shader = RadialGradient(cx, cy, size * 0.62f, intArrayOf(Royal.alpha(Royal.goldLight, 0.32f), 0), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, size * 0.62f, paint)
            paint.shader = null
            for (i in 0 until 24) {
                val a = Math.PI * 2 * i / 24
                paint.color = Royal.alpha(Royal.goldLight, if (i % 2 == 0) 0.12f else 0.06f)
                paint.strokeWidth = u * 0.5f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx + (cos(a) * size * 0.36f).toFloat(), cy + (sin(a) * size * 0.36f).toFloat(),
                    cx + (cos(a) * size * 0.62f).toFloat(), cy + (sin(a) * size * 0.62f).toFloat(), paint)
            }
            paint.style = Paint.Style.FILL
            val glyphPath = Path(MafiaArt.glyph(glyph))
            val m = Matrix().apply { setRectToRect(RectF(0f, 0f, 100f, 100f), RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f), Matrix.ScaleToFit.CENTER) }
            glyphPath.transform(m)
            paint.color = 0x99000000.toInt()
            canvas.drawPath(Path(glyphPath).apply { offset(0f, u * 1.4f) }, paint)
            paint.color = Color.BLACK
            paint.shader = Emblems.goldFill(cy - size * 0.4f, cy + size * 0.4f)
            canvas.drawPath(glyphPath, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = u * 0.5f
            paint.color = 0x802A1A05.toInt()
            canvas.drawPath(glyphPath, paint)
            paint.style = Paint.Style.FILL
        }
        // Bottom fade into the plate.
        paint.color = Color.BLACK
        paint.shader = LinearGradient(0f, r.bottom - r.height() * 0.35f, 0f, r.bottom, 0, 0xEE0B0610.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(r, paint)
        paint.shader = null
        canvas.restore()
    }

    private fun drawPlate(canvas: Canvas, r: RectF, u: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.shader = LinearGradient(0f, r.top, 0f, r.bottom, MafiaColors.PLATE_TOP, MafiaColors.PLATE_BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, u * 3f, u * 3f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.9f
        paint.color = Color.BLACK
        paint.shader = Emblems.goldFill(r.top, r.bottom)
        canvas.drawRoundRect(r, u * 3f, u * 3f, paint)
        paint.shader = null
        paint.strokeWidth = u * 0.35f
        paint.color = Royal.alpha(Royal.gold, 0.5f)
        canvas.drawRoundRect(RectF(r.left + u * 2f, r.top + u * 2f, r.right - u * 2f, r.bottom - u * 2f), u * 2f, u * 2f, paint)
        paint.style = Paint.Style.FILL
        // Medallion sitting on the plate's top edge.
        val mr = u * 9.5f
        val mcx = r.centerX()
        val mcy = r.top
        Emblems.drawMedallion(canvas, mcx, mcy, mr, MafiaColors.PLATE_TOP, paint)
        Emblems.drawRelief(canvas, MafiaArt.glyph(glyph), mcx, mcy, mr, paint, 1.4f)
        Emblems.drawGloss(canvas, mcx, mcy, mr * 0.83f, paint)
        // Little scrolls either side of the medallion.
        paint.color = Color.BLACK
        paint.shader = Emblems.goldFill(mcy - u * 2f, mcy + u * 2f)
        for (side in listOf(-1f, 1f)) {
            val x0 = mcx + side * (mr + u * 1.5f)
            canvas.drawPath(Path().apply {
                moveTo(x0, mcy - u * 0.8f); lineTo(x0 + side * u * 14f, mcy - u * 0.25f); lineTo(x0 + side * u * 14f, mcy + u * 0.25f); lineTo(x0, mcy + u * 0.8f); close()
            }, paint)
            canvas.drawCircle(x0 + side * u * 15.5f, mcy, u * 1.1f, paint)
        }
        paint.shader = null
        // Name and description.
        val titleSize = fitText(title, r.width() - u * 10f, u * 10.5f, true)
        paint.typeface = RoyalFonts.get(context, true)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = titleSize
        val titleY = r.top + mr + u * 1.5f + titleSize * 0.85f
        paint.color = 0x99000000.toInt()
        canvas.drawText(title, r.centerX(), titleY + u * 0.8f, paint)
        paint.color = Color.BLACK
        paint.shader = Emblems.goldFill(titleY - titleSize, titleY)
        canvas.drawText(title, r.centerX(), titleY, paint)
        paint.shader = null
        if (subtitle.isNotBlank()) {
            val lineY = titleY + u * 3.2f
            paint.color = Royal.alpha(Royal.gold, 0.6f)
            paint.strokeWidth = u * 0.35f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(r.left + u * 10f, lineY, r.right - u * 10f, lineY, paint)
            paint.style = Paint.Style.FILL
            val subSize = fitText(subtitle, r.width() - u * 10f, u * 5.6f, false)
            paint.typeface = RoyalFonts.get(context, false)
            paint.textSize = subSize
            paint.color = Royal.ivory
            canvas.drawText(subtitle, r.centerX(), lineY + u * 2.4f + subSize, paint)
        }
    }

    private fun fitText(text: String, maxWidth: Float, preferred: Float, bold: Boolean): Float {
        paint.typeface = RoyalFonts.get(context, bold)
        var size = preferred
        paint.textSize = size
        while (size > preferred * 0.5f && paint.measureText(text) > maxWidth) {
            size *= 0.92f
            paint.textSize = size
        }
        return size
    }

    private fun drawBack(canvas: Canvas, outer: RectF, u: Float) {
        val r = RectF(outer.left + u * 4f, outer.top + u * 4f, outer.right - u * 4f, outer.bottom - u * 4f)
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.shader = RadialGradient(r.centerX(), r.centerY(), r.height() * 0.7f, intArrayOf(0xFF4A0C17.toInt(), 0xFF1A050A.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, u * 3f, u * 3f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.4f
        paint.color = Royal.alpha(Royal.gold, 0.22f)
        var y = r.top + u * 6f
        var row = 0
        while (y < r.bottom) {
            var x = r.left + u * 6f + if (row % 2 == 0) 0f else u * 6f
            while (x < r.right) {
                RoyalShapes.star8(canvas, x, y, u * 3.2f, paint)
                x += u * 12f
            }
            y += u * 10f
            row++
        }
        paint.style = Paint.Style.FILL
        val cr = u * 24f
        Emblems.drawMedallion(canvas, r.centerX(), r.centerY(), cr, MafiaColors.PLATE_TOP, paint)
        Emblems.drawRelief(canvas, MafiaArt.glyph(MafiaGlyph.MASK), r.centerX(), r.centerY(), cr, paint, 1.5f)
        Emblems.drawGloss(canvas, r.centerX(), r.centerY(), cr * 0.83f, paint)
    }

    private fun drawFrame(canvas: Canvas, outer: RectF, u: Float) {
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = u * 1.4f
        paint.shader = LinearGradient(outer.left, outer.top, outer.right, outer.bottom,
            intArrayOf(Royal.goldLight, Royal.goldDeep, Royal.goldLight, Royal.bronze, Royal.gold), floatArrayOf(0f, 0.3f, 0.5f, 0.8f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(outer, u * 5f, u * 5f, paint)
        paint.strokeWidth = u * 0.5f
        canvas.drawRoundRect(RectF(outer.left + u * 2.2f, outer.top + u * 2.2f, outer.right - u * 2.2f, outer.bottom - u * 2.2f), u * 3.5f, u * 3.5f, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        // Corner ornaments: a curl and a jewel in each corner.
        val corners = listOf(
            Triple(outer.left + u * 4.5f, outer.top + u * 4.5f, 0f), Triple(outer.right - u * 4.5f, outer.top + u * 4.5f, 90f),
            Triple(outer.right - u * 4.5f, outer.bottom - u * 4.5f, 180f), Triple(outer.left + u * 4.5f, outer.bottom - u * 4.5f, 270f)
        )
        corners.forEach { (x, y, rot) ->
            canvas.save()
            canvas.rotate(rot, x, y)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = u * 0.8f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.BLACK
            paint.shader = Emblems.goldFill(y - u * 6f, y + u * 6f)
            canvas.drawPath(Path().apply {
                moveTo(x, y + u * 9f); quadTo(x, y, x + u * 9f, y)
                moveTo(x + u * 2.5f, y + u * 5f); quadTo(x + u * 2.5f, y + u * 2.5f, x + u * 5f, y + u * 2.5f)
            }, paint)
            paint.style = Paint.Style.FILL
            RoyalShapes.diamond(canvas, x, y, u * 1.6f, paint)
            paint.shader = null
            canvas.restore()
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    companion object {
        /** Height / width of a card. */
        const val RATIO = 1.42f
    }
}
