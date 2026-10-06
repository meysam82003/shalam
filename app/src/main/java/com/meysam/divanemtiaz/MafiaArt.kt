package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Relief glyphs for the Mafia roles, drawn on the same 100-unit box as the other medallion motifs. */
enum class MafiaGlyph {
    FEDORA, MASK, PISTOL, CROSSHAIR, MED_CROSS, MAGNIFIER, SHIELD, BADGE, BOMB, DAGGER, SKULL, EYE,
    CROWN, SCALES, HEART, KEY, MOON, FLAME, CYLINDER, BOOK, CROSS, WINGS, SNOWFLAKE, SPIDER, SCROLL,
    HAMMER, FLASK, SYRINGE, CARDS, JESTER, COINS, CRYSTAL, HEAD, LOCK, WALL, DUMBBELL, ANCHOR, PALM,
    BOWLER, VICTORY, PUZZLE, DOLL, TOPHAT, PIPE, HORNS, COWBOY, BOLT, VIRUS, KATANA, TOMBSTONE, CUFFS,
    ZODIAC, CITIZEN, COMPASS, LAPTOP, TREE, NEWSPAPER, HOURGLASS, MUZZLE, SLEEP, GLOBE, WAVE, ROSE,
    BRIEFCASE, THEATER, BINOCULARS, HELMET, DYNAMITE, RING, QUESTION, WITCH, ANVIL, GLOVE, BULB,
    PENTAGRAM, WIND, LEAF, STAR, HAND, GAVEL, BELL, TARGET
}

private class GlyphBuilder {
    val path = Path()

    fun circle(x: Float, y: Float, r: Float) = path.addCircle(x, y, r, Path.Direction.CW)

    fun oval(l: Float, t: Float, r: Float, b: Float) = path.addOval(RectF(l, t, r, b), Path.Direction.CW)

    fun rect(l: Float, t: Float, r: Float, b: Float) = path.addRect(l, t, r, b, Path.Direction.CW)

    fun rrect(l: Float, t: Float, r: Float, b: Float, radius: Float) = path.addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW)

    fun poly(vararg p: Float) {
        val s = Path()
        s.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) { s.lineTo(p[i], p[i + 1]); i += 2 }
        s.close()
        add(s)
    }

    /** A thick straight stroke between two points, with rounded ends. */
    fun bar(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
        val len = hypot(x2 - x1, y2 - y1)
        val angle = Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
        val s = Path().apply { addRoundRect(RectF(x1 - w / 2, y1 - w / 2, x1 + len + w / 2, y1 + w / 2), w / 2, w / 2, Path.Direction.CW) }
        s.transform(Matrix().apply { setRotate(angle, x1, y1) })
        add(s)
    }

    fun ring(x: Float, y: Float, r: Float, w: Float) {
        val s = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
        s.op(Path().apply { addCircle(x, y, r - w, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        add(s)
    }

    fun arc(x: Float, y: Float, r: Float, start: Float, sweep: Float, w: Float) {
        val steps = 24
        for (i in 0 until steps) {
            val a1 = Math.toRadians((start + sweep * i / steps).toDouble())
            val a2 = Math.toRadians((start + sweep * (i + 1) / steps).toDouble())
            bar(x + r * cos(a1).toFloat(), y + r * sin(a1).toFloat(), x + r * cos(a2).toFloat(), y + r * sin(a2).toFloat(), w)
        }
    }

    fun cutArc(x: Float, y: Float, r: Float, start: Float, sweep: Float, w: Float) {
        val holder = GlyphBuilder()
        holder.arc(x, y, r, start, sweep, w)
        cut(holder.path)
    }

    fun star(x: Float, y: Float, r: Float, points: Int = 5, inner: Float = 0.45f) = add(RoyalShapes.starPath(x, y, r, points, inner))

    fun add(s: Path) { path.op(s, Path.Op.UNION) }

    fun cut(s: Path) { path.op(s, Path.Op.DIFFERENCE) }

    fun cutCircle(x: Float, y: Float, r: Float) = cut(Path().apply { addCircle(x, y, r, Path.Direction.CW) })

    fun cutRect(l: Float, t: Float, r: Float, b: Float) = cut(Path().apply { addRect(l, t, r, b, Path.Direction.CW) })

    fun cutBar(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
        val holder = GlyphBuilder()
        holder.bar(x1, y1, x2, y2, w)
        cut(holder.path)
    }

    fun cutPoly(vararg p: Float) {
        val holder = GlyphBuilder()
        holder.poly(*p)
        cut(holder.path)
    }

    fun shape(block: Path.() -> Unit) = add(Path().apply(block))
}

object MafiaArt {
    private val cache = HashMap<MafiaGlyph, Path>()

    fun glyph(g: MafiaGlyph): Path = cache.getOrPut(g) { build(g) }

    private fun build(g: MafiaGlyph): Path {
        val b = GlyphBuilder()
        with(b) {
            when (g) {
                MafiaGlyph.FEDORA -> {
                    oval(10f, 58f, 90f, 74f)
                    shape {
                        moveTo(26f, 64f); cubicTo(24f, 46f, 26f, 30f, 34f, 26f); cubicTo(40f, 24f, 44f, 32f, 50f, 32f)
                        cubicTo(56f, 32f, 60f, 24f, 66f, 26f); cubicTo(74f, 30f, 76f, 46f, 74f, 64f); close()
                    }
                    cutRect(25f, 52f, 75f, 57f)
                }
                MafiaGlyph.MASK -> {
                    shape {
                        moveTo(8f, 40f); cubicTo(20f, 30f, 38f, 32f, 50f, 40f); cubicTo(62f, 32f, 80f, 30f, 92f, 40f)
                        cubicTo(92f, 60f, 78f, 70f, 64f, 66f); cubicTo(56f, 64f, 54f, 56f, 50f, 56f)
                        cubicTo(46f, 56f, 44f, 64f, 36f, 66f); cubicTo(22f, 70f, 8f, 60f, 8f, 40f); close()
                    }
                    cut(Path().apply { addOval(RectF(20f, 42f, 40f, 54f), Path.Direction.CW) })
                    cut(Path().apply { addOval(RectF(60f, 42f, 80f, 54f), Path.Direction.CW) })
                    bar(10f, 42f, 2f, 30f, 3f); bar(90f, 42f, 98f, 30f, 3f)
                }
                MafiaGlyph.PISTOL -> {
                    rrect(14f, 30f, 86f, 44f, 3f)
                    rect(80f, 26f, 86f, 31f)
                    poly(56f, 42f, 76f, 42f, 82f, 78f, 62f, 78f)
                    ring(54f, 52f, 9f, 3.5f)
                    cutRect(46f, 42f, 56f, 46f)
                    cutRect(20f, 34f, 50f, 36f)
                }
                MafiaGlyph.CROSSHAIR -> {
                    ring(50f, 50f, 32f, 6f)
                    bar(50f, 8f, 50f, 34f, 5f); bar(50f, 66f, 50f, 92f, 5f)
                    bar(8f, 50f, 34f, 50f, 5f); bar(66f, 50f, 92f, 50f, 5f)
                    circle(50f, 50f, 6f)
                }
                MafiaGlyph.MED_CROSS -> {
                    rrect(36f, 12f, 64f, 88f, 7f); rrect(12f, 36f, 88f, 64f, 7f)
                    cut(Path().apply { addRoundRect(RectF(41f, 17f, 59f, 83f), 4f, 4f, Path.Direction.CW) })
                    cut(Path().apply { addRoundRect(RectF(17f, 41f, 83f, 59f), 4f, 4f, Path.Direction.CW) })
                    rrect(44f, 20f, 56f, 80f, 3f); rrect(20f, 44f, 80f, 56f, 3f)
                }
                MafiaGlyph.MAGNIFIER -> {
                    ring(42f, 42f, 26f, 8f)
                    bar(61f, 61f, 86f, 86f, 12f)
                    oval(28f, 28f, 40f, 36f)
                }
                MafiaGlyph.SHIELD -> {
                    shape {
                        moveTo(50f, 10f); cubicTo(62f, 18f, 76f, 20f, 86f, 20f); cubicTo(86f, 56f, 72f, 78f, 50f, 92f)
                        cubicTo(28f, 78f, 14f, 56f, 14f, 20f); cubicTo(24f, 20f, 38f, 18f, 50f, 10f); close()
                    }
                    cutBar(50f, 26f, 50f, 78f, 5f)
                    cutBar(26f, 42f, 74f, 42f, 5f)
                }
                MafiaGlyph.BADGE -> {
                    star(50f, 52f, 40f, 6, 0.62f)
                    cutCircle(50f, 52f, 16f)
                    circle(50f, 52f, 11f)
                }
                MafiaGlyph.BOMB -> {
                    circle(44f, 60f, 28f)
                    rrect(52f, 26f, 68f, 38f, 3f)
                    shape { moveTo(60f, 27f); cubicTo(62f, 16f, 72f, 12f, 78f, 18f); lineTo(76f, 21f); cubicTo(72f, 17f, 66f, 20f, 64f, 28f); close() }
                    star(82f, 14f, 10f, 8, 0.4f)
                    oval(28f, 44f, 40f, 54f).also { cut(Path().apply { addOval(RectF(30f, 46f, 38f, 52f), Path.Direction.CW) }) }
                }
                MafiaGlyph.DAGGER -> {
                    poly(50f, 6f, 59f, 22f, 57f, 62f, 43f, 62f, 41f, 22f)
                    rrect(28f, 62f, 72f, 70f, 4f)
                    rrect(45f, 70f, 55f, 88f, 3f)
                    circle(50f, 92f, 6f)
                    cutBar(50f, 18f, 50f, 56f, 2f)
                }
                MafiaGlyph.SKULL -> {
                    circle(50f, 42f, 30f)
                    rrect(32f, 58f, 68f, 82f, 6f)
                    cutCircle(38f, 44f, 8f); cutCircle(62f, 44f, 8f)
                    cutPoly(50f, 54f, 45f, 63f, 55f, 63f)
                    cutRect(41f, 72f, 43.5f, 82f); cutRect(48.75f, 72f, 51.25f, 82f); cutRect(56.5f, 72f, 59f, 82f)
                }
                MafiaGlyph.EYE -> {
                    shape { moveTo(8f, 50f); cubicTo(26f, 22f, 74f, 22f, 92f, 50f); cubicTo(74f, 78f, 26f, 78f, 8f, 50f); close() }
                    cutCircle(50f, 50f, 15f)
                    circle(50f, 50f, 8f)
                }
                MafiaGlyph.CROWN -> {
                    poly(14f, 72f, 10f, 30f, 32f, 50f, 50f, 20f, 68f, 50f, 90f, 30f, 86f, 72f)
                    rrect(14f, 76f, 86f, 86f, 3f)
                    circle(10f, 28f, 5f); circle(50f, 18f, 6f); circle(90f, 28f, 5f)
                    cutCircle(50f, 60f, 5f)
                }
                MafiaGlyph.SCALES -> {
                    rect(47f, 16f, 53f, 84f); rrect(30f, 82f, 70f, 90f, 3f)
                    bar(16f, 26f, 84f, 26f, 5f)
                    circle(50f, 16f, 6f)
                    bar(18f, 26f, 8f, 54f, 2.5f); bar(18f, 26f, 28f, 54f, 2.5f)
                    bar(82f, 26f, 72f, 54f, 2.5f); bar(82f, 26f, 92f, 54f, 2.5f)
                    shape { moveTo(4f, 54f); lineTo(32f, 54f); cubicTo(32f, 66f, 4f, 66f, 4f, 54f); close() }
                    shape { moveTo(68f, 54f); lineTo(96f, 54f); cubicTo(96f, 66f, 68f, 66f, 68f, 54f); close() }
                }
                MafiaGlyph.HEART -> add(SuitShapes.heart(100f).apply { transform(Matrix().apply { setScale(0.78f, 0.78f); postTranslate(11f, 13f) }) })
                MafiaGlyph.KEY -> {
                    ring(30f, 34f, 20f, 8f)
                    bar(42f, 46f, 84f, 88f, 9f)
                    bar(66f, 70f, 76f, 60f, 8f); bar(76f, 80f, 86f, 70f, 8f)
                }
                MafiaGlyph.MOON -> {
                    circle(46f, 50f, 34f)
                    cutCircle(62f, 40f, 30f)
                    star(74f, 64f, 10f)
                }
                MafiaGlyph.FLAME -> {
                    shape {
                        moveTo(50f, 6f); cubicTo(58f, 26f, 80f, 38f, 80f, 62f); cubicTo(80f, 80f, 66f, 92f, 50f, 92f)
                        cubicTo(34f, 92f, 20f, 80f, 20f, 62f); cubicTo(20f, 48f, 30f, 40f, 34f, 30f); cubicTo(38f, 42f, 44f, 44f, 46f, 46f)
                        cubicTo(48f, 30f, 46f, 18f, 50f, 6f); close()
                    }
                    cut(Path().apply { moveTo(50f, 54f); cubicTo(56f, 64f, 64f, 70f, 62f, 78f); cubicTo(60f, 86f, 40f, 86f, 38f, 78f); cubicTo(36f, 70f, 46f, 64f, 50f, 54f); close() })
                }
                MafiaGlyph.CYLINDER -> {
                    circle(50f, 50f, 38f)
                    for (i in 0 until 6) {
                        val a = Math.toRadians(60.0 * i - 90.0)
                        cutCircle(50f + 22f * cos(a).toFloat(), 50f + 22f * sin(a).toFloat(), 9f)
                    }
                    cutCircle(50f, 50f, 5f)
                    circle(50f + 22f * cos(Math.toRadians(-90.0)).toFloat(), 50f + 22f * sin(Math.toRadians(-90.0)).toFloat(), 5f)
                }
                MafiaGlyph.BOOK -> {
                    shape { moveTo(50f, 26f); cubicTo(38f, 18f, 20f, 18f, 8f, 22f); lineTo(8f, 80f); cubicTo(20f, 76f, 38f, 76f, 50f, 84f); close() }
                    shape { moveTo(50f, 26f); cubicTo(62f, 18f, 80f, 18f, 92f, 22f); lineTo(92f, 80f); cubicTo(80f, 76f, 62f, 76f, 50f, 84f); close() }
                    cutBar(50f, 26f, 50f, 84f, 3f)
                    cutBar(30f, 40f, 30f, 62f, 3f); cutBar(20f, 51f, 40f, 51f, 3f)
                }
                MafiaGlyph.CROSS -> {
                    rrect(42f, 8f, 58f, 92f, 3f); rrect(22f, 26f, 78f, 40f, 3f)
                }
                MafiaGlyph.WINGS -> {
                    for (side in listOf(-1f, 1f)) {
                        val s = Path().apply {
                            moveTo(50f, 58f); cubicTo(40f, 34f, 22f, 22f, 2f, 22f); cubicTo(6f, 34f, 10f, 40f, 16f, 44f)
                            cubicTo(10f, 48f, 8f, 54f, 10f, 58f); cubicTo(16f, 60f, 20f, 62f, 22f, 68f); cubicTo(20f, 72f, 22f, 78f, 26f, 82f)
                            cubicTo(38f, 80f, 46f, 70f, 50f, 58f); close()
                        }
                        if (side > 0) s.transform(Matrix().apply { setScale(-1f, 1f, 50f, 50f) })
                        add(s)
                    }
                    shape { addOval(RectF(36f, 8f, 64f, 20f), Path.Direction.CW) }
                    cut(Path().apply { addOval(RectF(41f, 11f, 59f, 17f), Path.Direction.CW) })
                    oval(42f, 26f, 58f, 92f)
                }
                MafiaGlyph.SNOWFLAKE -> {
                    for (i in 0 until 6) {
                        val a = Math.toRadians(60.0 * i)
                        val x = 50f + 40f * cos(a).toFloat(); val y = 50f + 40f * sin(a).toFloat()
                        bar(50f, 50f, x, y, 6f)
                        val mx = 50f + 26f * cos(a).toFloat(); val my = 50f + 26f * sin(a).toFloat()
                        for (d in listOf(-40.0, 40.0)) {
                            val b2 = a + Math.toRadians(d)
                            bar(mx, my, mx + 12f * cos(b2).toFloat(), my + 12f * sin(b2).toFloat(), 5f)
                        }
                    }
                }
                MafiaGlyph.SPIDER -> {
                    oval(38f, 40f, 62f, 76f); circle(50f, 34f, 10f)
                    for (side in listOf(-1f, 1f)) {
                        val x0 = 50f + side * 8f
                        bar(x0, 46f, 50f + side * 30f, 30f, 4f); bar(50f + side * 30f, 30f, 50f + side * 40f, 12f, 4f)
                        bar(x0, 52f, 50f + side * 36f, 46f, 4f); bar(50f + side * 36f, 46f, 50f + side * 46f, 34f, 4f)
                        bar(x0, 58f, 50f + side * 36f, 62f, 4f); bar(50f + side * 36f, 62f, 50f + side * 44f, 78f, 4f)
                        bar(x0, 64f, 50f + side * 28f, 76f, 4f); bar(50f + side * 28f, 76f, 50f + side * 32f, 94f, 4f)
                    }
                    cutPoly(50f, 50f, 44f, 58f, 50f, 66f, 56f, 58f)
                }
                MafiaGlyph.SCROLL -> {
                    rect(22f, 20f, 78f, 80f)
                    rrect(14f, 12f, 86f, 26f, 7f); rrect(14f, 74f, 86f, 88f, 7f)
                    cutBar(32f, 38f, 68f, 38f, 3f); cutBar(32f, 48f, 68f, 48f, 3f); cutBar(32f, 58f, 58f, 58f, 3f)
                }
                MafiaGlyph.HAMMER -> {
                    rrect(18f, 14f, 70f, 34f, 4f); poly(70f, 18f, 86f, 12f, 86f, 36f, 70f, 30f)
                    bar(42f, 34f, 42f, 90f, 10f)
                }
                MafiaGlyph.FLASK -> {
                    rect(42f, 10f, 58f, 38f); rrect(36f, 6f, 64f, 14f, 3f)
                    shape { moveTo(42f, 36f); lineTo(58f, 36f); lineTo(84f, 82f); cubicTo(86f, 88f, 82f, 92f, 76f, 92f); lineTo(24f, 92f); cubicTo(18f, 92f, 14f, 88f, 16f, 82f); close() }
                    cut(Path().apply { moveTo(30f, 70f); lineTo(70f, 70f); lineTo(76f, 84f); lineTo(24f, 84f); close() })
                    circle(46f, 78f, 3f); circle(58f, 76f, 2f)
                }
                MafiaGlyph.SYRINGE -> {
                    bar(30f, 70f, 68f, 32f, 18f)
                    cutBar(36f, 64f, 56f, 44f, 8f)
                    bar(68f, 32f, 92f, 8f, 3f)
                    bar(26f, 74f, 14f, 86f, 5f)
                    bar(6f, 80f, 20f, 94f, 6f)
                    bar(18f, 66f, 34f, 82f, 5f)
                }
                MafiaGlyph.CARDS -> {
                    val back = Path().apply { addRoundRect(RectF(16f, 16f, 60f, 78f), 6f, 6f, Path.Direction.CW); transform(Matrix().apply { setRotate(-14f, 38f, 47f) }) }
                    add(back)
                    val front = Path().apply { addRoundRect(RectF(40f, 22f, 84f, 84f), 6f, 6f, Path.Direction.CW); transform(Matrix().apply { setRotate(12f, 62f, 53f) }) }
                    cut(Path(front).apply { transform(Matrix().apply { setScale(1.1f, 1.08f, 62f, 53f) }) })
                    add(front)
                    cut(SuitShapes.spade(100f).apply { transform(Matrix().apply { setScale(0.24f, 0.24f); postTranslate(50f, 40f) }) })
                }
                MafiaGlyph.JESTER -> {
                    shape {
                        moveTo(18f, 70f); cubicTo(16f, 46f, 8f, 30f, 6f, 22f); cubicTo(22f, 26f, 36f, 40f, 42f, 56f)
                        cubicTo(42f, 40f, 46f, 22f, 50f, 10f); cubicTo(54f, 22f, 58f, 40f, 58f, 56f)
                        cubicTo(64f, 40f, 78f, 26f, 94f, 22f); cubicTo(92f, 30f, 84f, 46f, 82f, 70f); close()
                    }
                    rrect(16f, 70f, 84f, 84f, 5f)
                    circle(6f, 20f, 6f); circle(50f, 8f, 6f); circle(94f, 20f, 6f)
                    cutCircle(32f, 77f, 3f); cutCircle(50f, 77f, 3f); cutCircle(68f, 77f, 3f)
                }
                MafiaGlyph.COINS -> {
                    for (i in 0 until 4) rrect(14f, 70f - i * 11f, 58f, 80f - i * 11f, 5f)
                    circle(66f, 56f, 24f)
                    cutCircle(66f, 56f, 18f)
                    circle(66f, 56f, 14f)
                    cutRect(64f, 46f, 68f, 66f)
                }
                MafiaGlyph.CRYSTAL -> {
                    circle(50f, 44f, 32f)
                    poly(26f, 92f, 32f, 74f, 68f, 74f, 74f, 92f)
                    cut(Path().apply { addOval(RectF(30f, 24f, 48f, 36f), Path.Direction.CW); transform(Matrix().apply { setRotate(-30f, 39f, 30f) }) })
                    cut(RoyalShapes.starPath(58f, 50f, 9f, 4, 0.35f))
                }
                MafiaGlyph.HEAD -> {
                    shape {
                        moveTo(30f, 92f); lineTo(30f, 74f); cubicTo(18f, 66f, 14f, 50f, 18f, 36f); cubicTo(24f, 16f, 46f, 8f, 64f, 14f)
                        cubicTo(80f, 20f, 86f, 36f, 82f, 48f); lineTo(90f, 60f); lineTo(82f, 62f); lineTo(82f, 72f)
                        cubicTo(82f, 78f, 76f, 80f, 66f, 78f); lineTo(66f, 92f); close()
                    }
                    cutCircle(50f, 40f, 14f)
                    circle(50f, 40f, 8f)
                    cutCircle(50f, 40f, 3f)
                }
                MafiaGlyph.LOCK -> {
                    arc(50f, 40f, 20f, 180f, 180f, 8f)
                    rect(26f, 38f, 34f, 46f); rect(66f, 38f, 74f, 46f)
                    rrect(18f, 44f, 82f, 90f, 8f)
                    cutCircle(50f, 62f, 7f); cutRect(47f, 62f, 53f, 78f)
                }
                MafiaGlyph.WALL -> {
                    rect(8f, 18f, 92f, 86f)
                    for (row in 0 until 4) {
                        val y = 18f + row * 17f
                        if (row > 0) cutRect(8f, y - 1.5f, 92f, y + 1.5f)
                        val offset = if (row % 2 == 0) 0f else 14f
                        var x = 8f + offset + 28f
                        while (x < 92f) { cutRect(x - 1.5f, y, x + 1.5f, y + 17f); x += 28f }
                    }
                    for (i in 0 until 5) rect(8f + i * 18f, 8f, 20f + i * 18f, 18f)
                }
                MafiaGlyph.DUMBBELL -> {
                    rrect(20f, 46f, 80f, 54f, 3f)
                    rrect(12f, 26f, 26f, 74f, 4f); rrect(74f, 26f, 88f, 74f, 4f)
                    rrect(4f, 34f, 14f, 66f, 3f); rrect(86f, 34f, 96f, 66f, 3f)
                }
                MafiaGlyph.ANCHOR -> {
                    ring(50f, 16f, 9f, 4.5f)
                    rect(46f, 24f, 54f, 86f)
                    rrect(30f, 32f, 70f, 40f, 3f)
                    arc(50f, 54f, 34f, 20f, 140f, 7f)
                    poly(10f, 60f, 22f, 54f, 18f, 68f); poly(90f, 60f, 78f, 54f, 82f, 68f)
                }
                MafiaGlyph.PALM -> {
                    shape { moveTo(46f, 92f); cubicTo(48f, 70f, 52f, 54f, 56f, 40f); lineTo(62f, 42f); cubicTo(58f, 56f, 56f, 72f, 56f, 92f); close() }
                    for (a in listOf(-160f, -120f, -60f, -20f, 20f)) {
                        val r = Math.toRadians(a.toDouble())
                        val ex = 58f + 36f * cos(r).toFloat(); val ey = 38f + 30f * sin(r).toFloat() + 10f
                        shape { moveTo(58f, 38f); quadTo((58f + ex) / 2f, (38f + ey) / 2f - 14f, ex, ey); quadTo((58f + ex) / 2f, (38f + ey) / 2f - 2f, 58f, 42f); close() }
                    }
                    oval(20f, 86f, 84f, 96f)
                }
                MafiaGlyph.BOWLER -> {
                    shape { moveTo(22f, 52f); cubicTo(22f, 22f, 70f, 22f, 70f, 52f); close() }
                    oval(12f, 48f, 80f, 60f)
                    bar(78f, 44f, 78f, 92f, 5f)
                    arc(70f, 44f, 8f, 180f, 180f, 5f)
                }
                MafiaGlyph.VICTORY -> {
                    bar(50f, 80f, 24f, 14f, 13f); bar(50f, 80f, 76f, 14f, 13f)
                    cutBar(50f, 80f, 24f, 14f, 4f); cutBar(50f, 80f, 76f, 14f, 4f)
                    bar(14f, 90f, 54f, 90f, 7f); circle(60f, 90f, 4f)
                }
                MafiaGlyph.PUZZLE -> {
                    rrect(16f, 26f, 76f, 86f, 4f)
                    circle(46f, 22f, 10f); circle(80f, 56f, 10f)
                    cutCircle(16f, 56f, 10f); cutCircle(46f, 86f, 10f)
                }
                MafiaGlyph.DOLL -> {
                    circle(50f, 22f, 14f)
                    rrect(36f, 34f, 64f, 70f, 8f)
                    bar(36f, 42f, 16f, 54f, 10f); bar(64f, 42f, 84f, 54f, 10f)
                    bar(42f, 66f, 34f, 90f, 10f); bar(58f, 66f, 66f, 90f, 10f)
                    cutBar(43f, 17f, 49f, 23f, 2f); cutBar(49f, 17f, 43f, 23f, 2f)
                    cutBar(51f, 17f, 57f, 23f, 2f); cutBar(57f, 17f, 51f, 23f, 2f)
                    cutBar(40f, 46f, 60f, 46f, 2f); cutBar(40f, 54f, 60f, 54f, 2f)
                    bar(70f, 30f, 46f, 56f, 2.5f); circle(72f, 28f, 4f)
                }
                MafiaGlyph.TOPHAT -> {
                    rrect(30f, 14f, 70f, 62f, 4f); oval(14f, 58f, 86f, 70f)
                    cutRect(30f, 50f, 70f, 54f)
                    bar(64f, 92f, 92f, 74f, 5f)
                    star(16f, 82f, 9f, 4, 0.35f)
                }
                MafiaGlyph.PIPE -> {
                    shape { moveTo(54f, 46f); lineTo(84f, 46f); cubicTo(86f, 70f, 76f, 86f, 64f, 86f); cubicTo(54f, 86f, 50f, 76f, 54f, 46f); close() }
                    shape { moveTo(10f, 30f); cubicTo(26f, 28f, 42f, 46f, 56f, 62f); lineTo(56f, 72f); cubicTo(40f, 58f, 24f, 40f, 10f, 38f); close() }
                    cut(Path().apply { addOval(RectF(58f, 44f, 82f, 52f), Path.Direction.CW) })
                    arc(70f, 30f, 8f, 120f, 200f, 3f)
                }
                MafiaGlyph.HORNS -> {
                    shape { moveTo(30f, 46f); cubicTo(26f, 70f, 40f, 88f, 50f, 90f); cubicTo(60f, 88f, 74f, 70f, 70f, 46f); cubicTo(64f, 40f, 36f, 40f, 30f, 46f); close() }
                    shape { moveTo(32f, 46f); cubicTo(16f, 46f, 6f, 36f, 6f, 14f); cubicTo(14f, 28f, 22f, 34f, 36f, 38f); close() }
                    shape { moveTo(68f, 46f); cubicTo(84f, 46f, 94f, 36f, 94f, 14f); cubicTo(86f, 28f, 78f, 34f, 64f, 38f); close() }
                    cutCircle(40f, 58f, 4f); cutCircle(60f, 58f, 4f)
                    ring(50f, 82f, 6f, 2.5f)
                }
                MafiaGlyph.COWBOY -> {
                    shape { moveTo(4f, 50f); cubicTo(20f, 70f, 80f, 70f, 96f, 50f); cubicTo(86f, 62f, 70f, 62f, 50f, 62f); cubicTo(30f, 62f, 14f, 62f, 4f, 50f); close() }
                    shape { moveTo(26f, 58f); cubicTo(24f, 36f, 30f, 22f, 40f, 22f); cubicTo(46f, 22f, 46f, 28f, 50f, 28f); cubicTo(54f, 28f, 54f, 22f, 60f, 22f); cubicTo(70f, 22f, 76f, 36f, 74f, 58f); close() }
                    cutRect(26f, 48f, 74f, 52f)
                    star(50f, 76f, 9f)
                }
                MafiaGlyph.BOLT -> poly(58f, 4f, 20f, 56f, 46f, 56f, 36f, 96f, 80f, 40f, 54f, 40f)
                MafiaGlyph.VIRUS -> {
                    circle(50f, 50f, 26f)
                    for (i in 0 until 10) {
                        val a = Math.toRadians(36.0 * i)
                        bar(50f + 24f * cos(a).toFloat(), 50f + 24f * sin(a).toFloat(), 50f + 38f * cos(a).toFloat(), 50f + 38f * sin(a).toFloat(), 4f)
                        circle(50f + 40f * cos(a).toFloat(), 50f + 40f * sin(a).toFloat(), 5f)
                    }
                    cutCircle(42f, 44f, 5f); cutCircle(58f, 56f, 6f); cutCircle(56f, 38f, 3f)
                }
                MafiaGlyph.KATANA -> {
                    shape { moveTo(20f, 74f); cubicTo(40f, 50f, 64f, 26f, 94f, 6f); cubicTo(74f, 30f, 52f, 54f, 26f, 80f); close() }
                    val guard = Path().apply { addOval(RectF(12f, 70f, 34f, 84f), Path.Direction.CW); transform(Matrix().apply { setRotate(-45f, 23f, 77f) }) }
                    add(guard)
                    bar(18f, 82f, 6f, 94f, 7f)
                    star(80f, 76f, 12f, 5, 0.4f)
                }
                MafiaGlyph.TOMBSTONE -> {
                    shape { moveTo(22f, 86f); lineTo(22f, 40f); cubicTo(22f, 10f, 78f, 10f, 78f, 40f); lineTo(78f, 86f); close() }
                    rrect(12f, 84f, 88f, 94f, 3f)
                    cutBar(50f, 30f, 50f, 64f, 5f); cutBar(38f, 42f, 62f, 42f, 5f)
                }
                MafiaGlyph.CUFFS -> {
                    ring(30f, 60f, 20f, 7f); ring(70f, 60f, 20f, 7f)
                    rrect(20f, 30f, 40f, 42f, 3f); rrect(60f, 30f, 80f, 42f, 3f)
                    for (i in 0 until 3) ring(42f + i * 8f, 30f, 5f, 2.5f)
                }
                MafiaGlyph.ZODIAC -> {
                    ring(50f, 50f, 30f, 7f)
                    bar(50f, 6f, 50f, 94f, 7f); bar(6f, 50f, 94f, 50f, 7f)
                }
                MafiaGlyph.CITIZEN -> {
                    circle(50f, 32f, 18f)
                    shape { moveTo(14f, 92f); cubicTo(16f, 64f, 32f, 54f, 50f, 54f); cubicTo(68f, 54f, 84f, 64f, 86f, 92f); close() }
                    cutPoly(44f, 56f, 56f, 56f, 50f, 70f)
                }
                MafiaGlyph.COMPASS -> {
                    circle(50f, 14f, 7f)
                    bar(48f, 18f, 22f, 86f, 7f); bar(52f, 18f, 78f, 86f, 7f)
                    arc(50f, 30f, 46f, 60f, 60f, 5f)
                    bar(14f, 54f, 86f, 54f, 6f)
                }
                MafiaGlyph.LAPTOP -> {
                    rrect(18f, 18f, 82f, 64f, 4f)
                    cutRect(24f, 24f, 76f, 58f)
                    poly(6f, 70f, 94f, 70f, 88f, 82f, 12f, 82f)
                    bar(34f, 34f, 42f, 41f, 3f); bar(42f, 41f, 34f, 48f, 3f); rect(46f, 46f, 60f, 49f)
                }
                MafiaGlyph.TREE -> {
                    circle(50f, 34f, 24f); circle(30f, 48f, 16f); circle(70f, 48f, 16f)
                    rect(45f, 52f, 55f, 88f)
                    bar(50f, 70f, 34f, 58f, 5f); bar(50f, 66f, 66f, 56f, 5f)
                    rrect(28f, 86f, 72f, 94f, 3f)
                }
                MafiaGlyph.NEWSPAPER -> {
                    rrect(10f, 16f, 90f, 86f, 4f)
                    cutRect(18f, 24f, 82f, 34f)
                    cutRect(18f, 42f, 46f, 70f)
                    cutRect(52f, 42f, 82f, 45f); cutRect(52f, 52f, 82f, 55f); cutRect(52f, 62f, 82f, 65f); cutRect(18f, 74f, 82f, 77f)
                }
                MafiaGlyph.HOURGLASS -> {
                    rrect(18f, 8f, 82f, 16f, 3f); rrect(18f, 84f, 82f, 92f, 3f)
                    shape { moveTo(24f, 16f); lineTo(76f, 16f); cubicTo(76f, 36f, 56f, 44f, 56f, 50f); cubicTo(56f, 56f, 76f, 64f, 76f, 84f); lineTo(24f, 84f); cubicTo(24f, 64f, 44f, 56f, 44f, 50f); cubicTo(44f, 44f, 24f, 36f, 24f, 16f); close() }
                    cut(Path().apply { moveTo(30f, 22f); lineTo(70f, 22f); cubicTo(68f, 32f, 56f, 38f, 50f, 46f); cubicTo(44f, 38f, 32f, 32f, 30f, 22f); close() })
                    add(Path().apply { moveTo(36f, 28f); lineTo(64f, 28f); cubicTo(60f, 34f, 54f, 38f, 50f, 42f); cubicTo(46f, 38f, 40f, 34f, 36f, 28f); close() })
                }
                MafiaGlyph.MUZZLE -> {
                    oval(20f, 10f, 80f, 90f)
                    cutCircle(38f, 38f, 7f); cutCircle(62f, 38f, 7f)
                    cut(Path().apply { addRoundRect(RectF(30f, 54f, 70f, 80f), 6f, 6f, Path.Direction.CW) })
                    for (i in 0 until 5) rect(32f + i * 8f, 54f, 35f + i * 8f, 80f)
                    rect(30f, 64f, 70f, 67f)
                    bar(20f, 60f, 4f, 52f, 4f); bar(80f, 60f, 96f, 52f, 4f)
                }
                MafiaGlyph.SLEEP -> {
                    circle(40f, 56f, 32f)
                    cutCircle(56f, 46f, 28f)
                    for ((x, y, s) in listOf(Triple(62f, 52f, 16f), Triple(78f, 30f, 12f))) {
                        rect(x, y, x + s, y + s * 0.2f); rect(x, y + s * 0.8f, x + s, y + s)
                        bar(x + s - 1f, y + s * 0.2f, x + 1f, y + s * 0.8f, s * 0.2f)
                    }
                }
                MafiaGlyph.GLOBE -> {
                    circle(50f, 50f, 38f)
                    cut(Path().apply { addOval(RectF(36f, 14f, 64f, 86f), Path.Direction.CW) })
                    add(Path().apply { addOval(RectF(40f, 14f, 60f, 86f), Path.Direction.CW) })
                    cutRect(12f, 33f, 88f, 36f); cutRect(10f, 48.5f, 90f, 51.5f); cutRect(12f, 64f, 88f, 67f)
                    cutRect(48.5f, 12f, 51.5f, 88f)
                }
                MafiaGlyph.WAVE -> {
                    for (row in 0 until 3) {
                        val y = 34f + row * 20f
                        shape {
                            moveTo(6f, y); cubicTo(18f, y - 12f, 26f, y - 12f, 38f, y); cubicTo(50f, y + 12f, 58f, y + 12f, 70f, y)
                            cubicTo(80f, y - 10f, 88f, y - 10f, 94f, y - 4f); lineTo(94f, y + 6f); cubicTo(88f, y, 80f, y, 70f, y + 10f)
                            cubicTo(58f, y + 22f, 50f, y + 22f, 38f, y + 10f); cubicTo(26f, y - 2f, 18f, y - 2f, 6f, y + 10f); close()
                        }
                    }
                }
                MafiaGlyph.ROSE -> {
                    circle(50f, 30f, 22f)
                    cutArc(50f, 30f, 14f, 200f, 250f, 3f)
                    cutArc(50f, 30f, 7f, 20f, 250f, 3f)
                    bar(50f, 50f, 50f, 94f, 5f)
                    shape { moveTo(50f, 72f); cubicTo(38f, 60f, 26f, 62f, 22f, 70f); cubicTo(32f, 76f, 42f, 76f, 50f, 72f); close() }
                    shape { moveTo(50f, 64f); cubicTo(62f, 52f, 74f, 54f, 78f, 62f); cubicTo(68f, 68f, 58f, 68f, 50f, 64f); close() }
                }
                MafiaGlyph.BRIEFCASE -> {
                    rrect(8f, 32f, 92f, 86f, 6f)
                    arc(50f, 32f, 14f, 180f, 180f, 6f)
                    cutRect(8f, 52f, 92f, 56f)
                    rrect(42f, 48f, 58f, 62f, 3f)
                }
                MafiaGlyph.THEATER -> {
                    shape { moveTo(8f, 18f); cubicTo(22f, 22f, 40f, 22f, 54f, 16f); cubicTo(58f, 46f, 50f, 70f, 32f, 72f); cubicTo(14f, 70f, 6f, 46f, 8f, 18f); close() }
                    cutCircle(22f, 36f, 5f); cutCircle(40f, 34f, 5f)
                    cut(Path().apply { moveTo(20f, 50f); cubicTo(26f, 60f, 38f, 60f, 44f, 48f); cubicTo(36f, 52f, 28f, 52f, 20f, 50f); close() })
                    val sad = Path().apply {
                        moveTo(46f, 32f); cubicTo(60f, 36f, 78f, 36f, 92f, 30f); cubicTo(96f, 60f, 86f, 84f, 68f, 86f); cubicTo(50f, 84f, 42f, 60f, 46f, 32f); close()
                    }
                    cut(Path(sad).apply { transform(Matrix().apply { setScale(1.08f, 1.08f, 69f, 58f) }) })
                    add(sad)
                    cutCircle(60f, 50f, 5f); cutCircle(78f, 48f, 5f)
                    cut(Path().apply { moveTo(58f, 72f); cubicTo(64f, 62f, 76f, 62f, 82f, 72f); cubicTo(74f, 68f, 66f, 68f, 58f, 72f); close() })
                }
                MafiaGlyph.BINOCULARS -> {
                    circle(28f, 64f, 20f); circle(72f, 64f, 20f)
                    rrect(16f, 24f, 40f, 60f, 6f); rrect(60f, 24f, 84f, 60f, 6f)
                    rect(40f, 40f, 60f, 56f)
                    cutCircle(28f, 64f, 11f); cutCircle(72f, 64f, 11f)
                }
                MafiaGlyph.HELMET -> {
                    shape { moveTo(10f, 62f); cubicTo(10f, 24f, 90f, 24f, 90f, 62f); close() }
                    rrect(4f, 60f, 96f, 70f, 4f)
                    bar(26f, 70f, 34f, 86f, 4f); bar(74f, 70f, 66f, 86f, 4f); bar(34f, 86f, 66f, 86f, 4f)
                    star(50f, 46f, 10f)
                }
                MafiaGlyph.DYNAMITE -> {
                    for (i in 0 until 3) rrect(16f + i * 24f, 36f, 36f + i * 24f, 90f, 6f)
                    cutRect(14f, 54f, 86f, 58f); cutRect(14f, 70f, 86f, 74f)
                    shape { moveTo(48f, 36f); cubicTo(46f, 24f, 56f, 18f, 66f, 20f); lineTo(65f, 24f); cubicTo(58f, 23f, 51f, 27f, 52f, 36f); close() }
                    star(70f, 16f, 11f, 8, 0.4f)
                }
                MafiaGlyph.RING -> {
                    ring(50f, 62f, 28f, 7f)
                    poly(36f, 30f, 42f, 20f, 58f, 20f, 64f, 30f, 50f, 42f)
                    cutBar(42f, 22f, 50f, 40f, 1.5f); cutBar(58f, 22f, 50f, 40f, 1.5f)
                }
                MafiaGlyph.QUESTION -> {
                    arc(50f, 34f, 20f, 160f, 250f, 11f)
                    bar(58f, 50f, 50f, 60f, 11f); bar(50f, 60f, 50f, 66f, 11f)
                    circle(50f, 84f, 7f)
                }
                MafiaGlyph.WITCH -> {
                    shape { moveTo(22f, 72f); cubicTo(30f, 50f, 40f, 30f, 52f, 18f); cubicTo(60f, 10f, 74f, 8f, 84f, 14f); cubicTo(72f, 14f, 66f, 22f, 64f, 34f); lineTo(76f, 72f); close() }
                    oval(4f, 66f, 96f, 82f)
                    cutRect(24f, 60f, 74f, 65f)
                    rrect(42f, 56f, 54f, 68f, 2f).also { cutRect(45f, 59f, 51f, 65f) }
                }
                MafiaGlyph.ANVIL -> {
                    shape { moveTo(10f, 30f); lineTo(80f, 30f); lineTo(92f, 40f); lineTo(70f, 46f); lineTo(64f, 60f); lineTo(36f, 60f); lineTo(30f, 46f); cubicTo(18f, 44f, 10f, 38f, 10f, 30f); close() }
                    rect(38f, 58f, 62f, 74f)
                    rrect(24f, 72f, 76f, 86f, 3f)
                    star(28f, 16f, 8f, 4, 0.35f); star(60f, 14f, 6f, 4, 0.35f)
                }
                MafiaGlyph.GLOVE -> {
                    shape { moveTo(24f, 56f); cubicTo(18f, 30f, 32f, 12f, 56f, 12f); cubicTo(80f, 12f, 90f, 30f, 86f, 52f); cubicTo(84f, 66f, 74f, 72f, 64f, 72f); lineTo(30f, 72f); close() }
                    shape { moveTo(24f, 50f); cubicTo(10f, 48f, 8f, 64f, 18f, 68f); cubicTo(24f, 70f, 28f, 66f, 30f, 62f); close() }
                    rrect(28f, 72f, 70f, 92f, 4f)
                    cutRect(28f, 78f, 70f, 81f)
                    cutBar(44f, 26f, 76f, 26f, 2.5f)
                }
                MafiaGlyph.BULB -> {
                    shape { moveTo(36f, 64f); cubicTo(36f, 54f, 22f, 48f, 22f, 32f); cubicTo(22f, 16f, 34f, 6f, 50f, 6f); cubicTo(66f, 6f, 78f, 16f, 78f, 32f); cubicTo(78f, 48f, 64f, 54f, 64f, 64f); close() }
                    rrect(36f, 68f, 64f, 74f, 2f); rrect(38f, 77f, 62f, 83f, 2f); rrect(44f, 86f, 56f, 92f, 3f)
                    cutBar(42f, 56f, 50f, 34f, 3f); cutBar(58f, 56f, 50f, 34f, 3f)
                }
                MafiaGlyph.PENTAGRAM -> {
                    ring(50f, 52f, 40f, 5f)
                    val pts = (0 until 5).map { i -> val a = Math.toRadians(-90.0 + 144.0 * i); Pair(50f + 34f * cos(a).toFloat(), 52f + 34f * sin(a).toFloat()) }
                    for (i in 0 until 5) bar(pts[i].first, pts[i].second, pts[(i + 1) % 5].first, pts[(i + 1) % 5].second, 5f)
                }
                MafiaGlyph.WIND -> {
                    shape { moveTo(8f, 38f); lineTo(62f, 38f); cubicTo(74f, 38f, 78f, 22f, 66f, 18f); cubicTo(58f, 16f, 54f, 22f, 56f, 28f); lineTo(50f, 28f); cubicTo(46f, 16f, 56f, 8f, 66f, 10f); cubicTo(86f, 14f, 84f, 46f, 62f, 46f); lineTo(8f, 46f); close() }
                    shape { moveTo(8f, 56f); lineTo(74f, 56f); cubicTo(96f, 56f, 96f, 88f, 76f, 90f); cubicTo(64f, 92f, 58f, 82f, 62f, 74f); lineTo(68f, 76f); cubicTo(66f, 82f, 70f, 84f, 76f, 82f); cubicTo(88f, 80f, 86f, 64f, 74f, 64f); lineTo(8f, 64f); close() }
                    rrect(20f, 72f, 50f, 79f, 3.5f)
                }
                MafiaGlyph.LEAF -> {
                    shape { moveTo(14f, 86f); cubicTo(10f, 40f, 40f, 12f, 88f, 12f); cubicTo(88f, 60f, 60f, 90f, 14f, 86f); close() }
                    cutBar(18f, 82f, 70f, 30f, 3f)
                    cutBar(40f, 60f, 40f, 40f, 2.5f); cutBar(52f, 48f, 70f, 50f, 2.5f)
                }
                MafiaGlyph.STAR -> star(50f, 52f, 42f, 5, 0.45f)
                MafiaGlyph.HAND -> {
                    rrect(28f, 46f, 74f, 90f, 12f)
                    for ((i, h) in listOf(26f, 16f, 18f, 26f).withIndex()) rrect(28f + i * 11.5f, h, 38f + i * 11.5f, 60f, 5f)
                    bar(30f, 70f, 14f, 50f, 10f)
                }
                MafiaGlyph.GAVEL -> {
                    val head = Path().apply {
                        addRoundRect(RectF(36f, 18f, 80f, 42f), 5f, 5f, Path.Direction.CW)
                        addRect(RectF(32f, 16f, 38f, 44f), Path.Direction.CW); addRect(RectF(78f, 16f, 84f, 44f), Path.Direction.CW)
                        transform(Matrix().apply { setRotate(-40f, 58f, 30f) })
                    }
                    add(head)
                    bar(58f, 30f, 20f, 78f, 8f)
                    rrect(40f, 82f, 94f, 92f, 3f)
                }
                MafiaGlyph.BELL -> {
                    shape { moveTo(18f, 76f); cubicTo(26f, 66f, 24f, 50f, 26f, 40f); cubicTo(28f, 24f, 38f, 16f, 50f, 16f); cubicTo(62f, 16f, 72f, 24f, 74f, 40f); cubicTo(76f, 50f, 74f, 66f, 82f, 76f); close() }
                    circle(50f, 84f, 8f)
                    ring(50f, 12f, 6f, 3f)
                }
                MafiaGlyph.TARGET -> {
                    ring(50f, 50f, 40f, 8f); ring(50f, 50f, 24f, 8f); circle(50f, 50f, 8f)
                }
            }
        }
        return b.path
    }
}

/** A role medallion: side-coloured enamel with the role's gold relief glyph. */
class RoleSealView(context: Context, glyph: MafiaGlyph, base: Int) : View(context) {
    var glyph: MafiaGlyph = glyph
        set(value) { field = value; invalidate() }
    var base: Int = base
        set(value) { field = value; invalidate() }
    var hidden = false
        set(value) { field = value; invalidate() }
    var dead = false
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f * 0.9f
        if (r <= 0f) return
        val cx = width / 2f
        val cy = height / 2f - r * 0.04f
        Emblems.drawMedallion(canvas, cx, cy, r, if (hidden) 0xFF23367A.toInt() else base, paint)
        Emblems.drawRelief(canvas, if (hidden) MafiaArt.glyph(MafiaGlyph.QUESTION) else MafiaArt.glyph(glyph), cx, cy, r, paint)
        Emblems.drawGloss(canvas, cx, cy, r * 0.83f, paint)
        if (dead) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = 0x99050B1A.toInt()
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.12f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Royal.crimson
            canvas.drawLine(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
