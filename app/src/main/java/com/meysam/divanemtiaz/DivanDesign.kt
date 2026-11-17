package com.meysam.divanemtiaz

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.content.Context
import kotlin.math.min

/** Geometry shared by the real controls and the miniature previews. No bitmaps or network assets. */
object DivanDesign {
    fun radius(t: ThemeSpec, requested: Float): Float = when (t.layout) {
        ThemeLayout.PAPER -> min(requested, 3f)
        ThemeLayout.VOLT -> min(requested, 2f)
        ThemeLayout.ATLAS -> min(requested, 12f)
        ThemeLayout.ORBIT -> requested * 1.4f
        ThemeLayout.PALM -> requested * 1.65f
        ThemeLayout.WAVE -> requested * 1.25f
    }

    fun background(canvas: Canvas, w: Float, h: Float, d: Float, t: ThemeSpec, p: Paint) {
        p.shader = null; p.style = Paint.Style.FILL
        when (t.layout) {
            ThemeLayout.ATLAS -> {
                p.color = Royal.alpha(t.accent, .035f)
                var x = 0f
                while (x < w) { canvas.drawRect(x, 0f, x + d, h, p); x += 32f * d }
                var y = 0f
                while (y < h) { canvas.drawRect(0f, y, w, y + d, p); y += 32f * d }
            }
            ThemeLayout.PAPER -> {
                p.color = Royal.alpha(t.text, .035f)
                var y = 24f * d
                while (y < h) { canvas.drawRect(0f, y, w, y + d * .5f, p); y += 28f * d }
            }
            ThemeLayout.ORBIT -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = d
                p.color = Royal.alpha(t.accent, .09f)
                canvas.drawCircle(w * .05f, h * .18f, w * .75f, p)
                canvas.drawCircle(w, h * .72f, w * .6f, p)
            }
            ThemeLayout.PALM -> {
                p.color = Royal.alpha(t.accent, .035f)
                canvas.drawOval(RectF(-w * .5f, -w * .7f, w * .8f, w * .5f), p)
                canvas.drawOval(RectF(w * .5f, h * .5f, w * 1.6f, h * .5f + w * 1.4f), p)
            }
            ThemeLayout.VOLT -> {
                p.color = Royal.alpha(t.accent, .055f)
                canvas.drawRect(w - 8f * d, 0f, w, h, p)
            }
            ThemeLayout.WAVE -> {
                p.color = Royal.alpha(t.accent, .04f)
                canvas.drawCircle(w * 1.05f, -w * .1f, w * .8f, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    /** New app mark: an interlocking D and score dots, separate from the old crown artwork. */
    fun brand(canvas: Canvas, width: Int, height: Int, p: Paint) {
        val t = ThemeEngine.spec
        val k = min(width, height) / 100f
        if (k <= 0f) return
        canvas.save(); canvas.translate((width - 100f * k) / 2f, (height - 100f * k) / 2f); canvas.scale(k, k)
        p.shader = null; p.style = Paint.Style.FILL
        p.color = Royal.alpha(t.accent, .10f)
        val r = radius(t, 24f)
        canvas.drawRoundRect(RectF(5f, 5f, 95f, 95f), r, r, p)
        p.color = t.accent
        canvas.drawRoundRect(RectF(24f, 23f, 37f, 76f), 6f, 6f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 12f; p.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(21f, 28f, 74f, 71f), -90f, 180f, false, p)
        p.style = Paint.Style.FILL
        p.color = t.text
        canvas.drawCircle(70f, 80f, 4f, p); canvas.drawCircle(83f, 80f, 4f, p)
        canvas.restore()
    }

    fun game(canvas: Canvas, width: Int, height: Int, game: GameType?, p: Paint) {
        val t = ThemeEngine.spec
        val k = min(width, height) / 100f
        if (k <= 0f) return
        canvas.save(); canvas.translate((width - 100f * k) / 2f, (height - 100f * k) / 2f); canvas.scale(k, k)
        p.shader = null; p.style = Paint.Style.FILL; p.color = Royal.alpha(t.accent, .10f)
        val r = radius(t, 26f)
        canvas.drawRoundRect(RectF(4f, 4f, 96f, 96f), r, r, p)
        p.color = t.accent
        when (game) {
            GameType.SHALAM -> {
                val path = Path().apply { moveTo(50f, 20f); lineTo(74f, 50f); lineTo(50f, 80f); lineTo(26f, 50f); close() }
                canvas.drawPath(path, p)
                p.color = t.onAccent; canvas.drawCircle(50f, 50f, 5f, p)
            }
            GameType.MENFI -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.strokeCap = Paint.Cap.ROUND
                canvas.drawCircle(50f, 50f, 28f, p); canvas.drawLine(37f, 50f, 63f, 50f, p)
            }
            GameType.HEZARTAII -> {
                p.color = Royal.alpha(t.accent, .3f); canvas.drawRoundRect(RectF(22f, 18f, 65f, 73f), 7f, 7f, p)
                p.color = t.accent; canvas.drawRoundRect(RectF(34f, 29f, 77f, 84f), 7f, 7f, p)
                p.color = t.onAccent; canvas.drawCircle(55f, 57f, 7f, p)
            }
            GameType.DOLO -> {
                canvas.drawCircle(38f, 39f, 13f, p); canvas.drawCircle(62f, 39f, 13f, p); canvas.drawCircle(50f, 58f, 13f, p)
                canvas.drawRoundRect(RectF(44f, 59f, 56f, 81f), 3f, 3f, p)
            }
            null -> { canvas.restore(); brand(canvas, width, height, p); return }
        }
        p.style = Paint.Style.FILL; canvas.restore()
    }

    fun role(canvas: Canvas, width: Int, height: Int, glyph: MafiaGlyph, hidden: Boolean, dead: Boolean, p: Paint, tint: Int = ThemeEngine.spec.accent) {
        val t = ThemeEngine.spec
        val k = min(width, height) / 100f
        if (k <= 0f) return
        canvas.save(); canvas.translate((width - 100f * k) / 2f, (height - 100f * k) / 2f); canvas.scale(k, k)
        p.shader = null; p.style = Paint.Style.FILL; p.color = Royal.alpha(if (dead) t.danger else t.accent, .12f)
        val r = radius(t, 26f)
        canvas.drawRoundRect(RectF(4f, 4f, 96f, 96f), r, r, p)
        canvas.save(); canvas.translate(22f, 22f); canvas.scale(.56f, .56f)
        p.color = if (dead) t.danger else if (hidden) t.accent else if (t.light) tint else Royal.mix(tint, 0xFFFFFFFF.toInt(), .55f)
        canvas.drawPath(MafiaArt.glyph(if (hidden) MafiaGlyph.QUESTION else glyph), p)
        canvas.restore()
        if (dead) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.strokeCap = Paint.Cap.ROUND; p.color = t.danger
            canvas.drawLine(22f, 78f, 78f, 22f, p)
        }
        p.style = Paint.Style.FILL; canvas.restore()
    }
}
