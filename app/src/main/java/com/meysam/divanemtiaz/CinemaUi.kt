package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.*

/** Presentation scope only. No game rules, timers or stored data depend on this value. */
enum class CinemaScene { HOME, CARDS, MENFI, MAFIA, THEATRE, SPY, TROPHY, PRO, GENERAL }
object CinemaLook {
    var scene = CinemaScene.HOME
    fun tint(t: ThemeSpec = ThemeEngine.spec): Int = when (scene) {
        CinemaScene.MENFI -> if (t.light) 0xFF7041A4.toInt() else 0xFFB695FF.toInt()
        CinemaScene.MAFIA -> if (t.light) 0xFF9F243C.toInt() else 0xFFEB6278.toInt()
        CinemaScene.THEATRE -> if (t.light) 0xFF6738A3.toInt() else 0xFFB695FF.toInt()
        CinemaScene.SPY -> if (t.light) 0xFF215B9B.toInt() else 0xFF70BBFF.toInt()
        else -> t.accent
    }
    fun surface(t: ThemeSpec = ThemeEngine.spec): Int {
        val scope = when (scene) {
            CinemaScene.MAFIA -> 0xFF4B1228.toInt()
            CinemaScene.MENFI, CinemaScene.THEATRE -> 0xFF292052.toInt()
            CinemaScene.SPY -> 0xFF102F51.toInt()
            else -> return t.surface
        }
        return Royal.mix(if (t.light) t.surface else t.bg[0], scope, if (t.light) .06f else .55f)
    }
    fun primary(): IntArray = when (scene) {
        CinemaScene.MAFIA -> intArrayOf(0xFFF96D7E.toInt(), 0xFFD42B49.toInt(), 0xFF90132F.toInt())
        CinemaScene.MENFI, CinemaScene.THEATRE -> intArrayOf(0xFFA790FF.toInt(), 0xFF6741DF.toInt(), 0xFF3D239A.toInt())
        else -> ThemeEngine.spec.primary
    }
    fun onPrimary(): Int = if (scene == CinemaScene.MAFIA || scene == CinemaScene.MENFI || scene == CinemaScene.THEATRE) Color.WHITE else ThemeEngine.spec.primaryText
}

/** Native scene painting: velvet folds, stage light, felt, city silhouette and metallic inlays. */
class CinemaBackdrop(private val context: Context, private val density: Float, private val scene: CinemaScene) : SimpleDrawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cached: Bitmap? = null
    private var stamp = ""
    override fun draw(canvas: Canvas) {
        val w = bounds.width(); val h = bounds.height()
        if (w <= 0 || h <= 0) return
        val palace = if (ThemeEngine.spec.light) null else CinemaAssets.get(context, "palace")
        val key = "$w.$h.${ThemeEngine.version}.$scene.${palace != null}.${palace?.generationId ?: 0}"
        if (stamp != key || cached == null) {
            cached?.recycle()
            cached = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { paintScene(Canvas(it), w.toFloat(), h.toFloat()) }
            stamp = key
        }
        canvas.drawBitmap(cached!!, bounds.left.toFloat(), bounds.top.toFloat(), null)
    }
    private fun paintScene(c: Canvas, w: Float, h: Float) {
        val t = ThemeEngine.spec
        val tint = when (scene) {
            CinemaScene.MENFI -> 0xFF462070.toInt()
            CinemaScene.PRO, CinemaScene.TROPHY -> 0xFF6D4C1B.toInt()
            CinemaScene.MAFIA -> 0xFF86132E.toInt()
            CinemaScene.THEATRE -> 0xFF4931A7.toInt()
            CinemaScene.SPY -> 0xFF164779.toInt()
            else -> t.surface
        }
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Royal.mix(t.bg[0], tint, if (t.light) .05f else .32f), t.bg[0], t.bg[0]), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        if (!t.light) CinemaAssets.get(context, "palace")?.let { image ->
            p.shader = null; p.alpha = if (scene == CinemaScene.MAFIA || scene == CinemaScene.THEATRE) 90 else 175
            p.isFilterBitmap = true
            c.drawBitmap(image, null, RectF(0f, 0f, w, h), p)
            p.alpha = 255
        }
        fun glow(x: Float, y: Float, r: Float, color: Int, strength: Float) {
            p.shader = RadialGradient(x, y, r.coerceAtLeast(1f), intArrayOf(Royal.alpha(color, strength), 0), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
        }
        glow(w * .5f, h * .15f, w * .8f, if (t.light) t.accent else tint, if (t.light) .08f else .65f)
        glow(w * .98f, h * .75f, w * .75f, CinemaLook.tint(t), if (t.light) .035f else .12f)
        // Fine woven material remains visible between cards, without a repeating grid.
        p.shader = null; p.strokeWidth = density * .45f; p.color = Royal.alpha(t.text, if (t.light) .025f else .018f)
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y + w * .18f, p); y += density * 7f }
        if (scene == CinemaScene.THEATRE || scene == CinemaScene.MAFIA) {
            p.alpha = 255
            // Curtains are drawn at the sides; content remains on a quiet central stage.
            for (side in 0..1) {
                c.save(); if (side == 1) { c.translate(w, 0f); c.scale(-1f, 1f) }
                for (i in 0..5) {
                    val x = i * w * .025f
                    p.shader = LinearGradient(x, 0f, x + w * .09f, 0f, intArrayOf(Royal.alpha(tint, if (t.light) .08f else .65f), Royal.alpha(tint, .02f), Royal.alpha(Color.BLACK, if (t.light) .03f else .6f)), null, Shader.TileMode.CLAMP)
                    val path = Path().apply { moveTo(x - w * .05f, 0f); lineTo(x + w * .08f, 0f); cubicTo(x + w * .13f, h * .15f, x - w * .05f, h * .32f, -w * .1f, h * .72f); close() }
                    c.drawPath(path, p)
                }
                c.restore()
            }
        }
        if (scene == CinemaScene.HOME || scene == CinemaScene.CARDS) {
            val oval = RectF(-w * .38f, -w * .26f, w * 1.38f, w * .91f)
            p.shader = RadialGradient(w * .5f, w * .16f, w * .8f, intArrayOf(Royal.alpha(t.accent, .045f), 0), null, Shader.TileMode.CLAMP)
            c.drawOval(oval, p)
            p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = density
            p.color = Royal.alpha(t.metalLight, if (t.light) .14f else .2f); c.drawOval(oval, p)
            oval.inset(density * 7, density * 7); p.color = Royal.alpha(t.metalDeep, .25f); c.drawOval(oval, p)
        }
        p.style = Paint.Style.FILL; p.shader = null
        // Side inlays and corner flourishes, kept outside touch targets.
        val edge = density * 5f
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0, Royal.alpha(t.metal, .48f), Royal.alpha(CinemaLook.tint(t), .12f), Royal.alpha(t.metal, .4f), 0), null, Shader.TileMode.CLAMP)
        c.drawRect(edge, 0f, edge + density * .7f, h, p); c.drawRect(w - edge - density, 0f, w - edge, h, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = density * .7f; p.color = Royal.alpha(t.metal, .26f)
        for (side in 0..1) {
            c.save(); if (side == 1) { c.translate(w, 0f); c.scale(-1f, 1f) }
            for (i in 0..2) {
                val x = edge + density * (3f + i * 4f)
                c.drawPath(Path().apply { moveTo(x, h * .7f); cubicTo(x + w * .16f, h * .83f, x - w * .05f, h * .94f, x + w * .12f, h) }, p)
            }
            c.restore()
        }
        p.style = Paint.Style.FILL; p.shader = null
    }
}

/** Glass, enamel and metal are drawn under native labels and controls, never as a UI screenshot. */
object CinemaSurface {
    fun panel(c: Canvas, b: Rect, d: Float, style: PanelStyle, requested: Float, p: Paint) {
        val t = ThemeEngine.spec; val r = (requested * d).coerceAtMost(min(b.width(), b.height()) * .42f)
        val rect = RectF(b.left + d, b.top + d, b.right - d, b.bottom - d * 4f)
        if (rect.width() <= 0 || rect.height() <= 0) return
        val selected = style == PanelStyle.SELECTED || style == PanelStyle.SUCCESS
        val tint = when (style) { PanelStyle.DANGER -> t.danger; PanelStyle.SUCCESS, PanelStyle.SELECTED -> t.success; else -> CinemaLook.tint() }
        p.shader = null; p.style = Paint.Style.FILL
        p.color = Royal.alpha(Color.BLACK, if (t.light) .09f else .45f)
        c.drawRoundRect(RectF(rect.left, rect.top + d * 3f, rect.right, rect.bottom + d * 3f), r, r, p)
        val surface = CinemaLook.surface(t)
        p.alpha = if (t.light) 255 else 242
        val top = Royal.mix(surface, tint, if (selected) .12f else if (style == PanelStyle.RAISED) .07f else .025f)
        val bottom = Royal.mix(surface, t.bg[0], .4f)
        if (style != PanelStyle.GHOST) {
            p.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(top, surface, bottom), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, r, r, p)
            p.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * .43f, Royal.alpha(Color.WHITE, if (t.light) .4f else .065f), 0, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, r, r, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = d * if (selected) 1.6f else 1f
        val edge = if (selected) tint else if (style == PanelStyle.RAISED) t.metal else if (CinemaLook.scene in listOf(CinemaScene.MAFIA, CinemaScene.THEATRE, CinemaScene.SPY)) tint else t.border
        p.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(Royal.alpha(t.metalLight, .85f), Royal.alpha(edge, .65f), Royal.alpha(edge, .2f), Royal.alpha(edge, .6f)), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p)
        val inset = d * 2.7f
        p.shader = null; p.strokeWidth = d * .6f; p.color = Royal.alpha(t.text, if (t.light) .11f else .07f)
        c.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), max(1f, r - inset), max(1f, r - inset), p)
        p.style = Paint.Style.FILL; p.shader = null
    }
}

object CinemaAssets {
    private val cache = HashMap<String, Bitmap>()
    fun get(context: Context, name: String): Bitmap? {
        cache[name]?.takeIf { !it.isRecycled }?.let { return it }
        val bitmap = runCatching { context.assets.open("cinema/" + name + if (name == "palace") ".webp" else ".jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        if (bitmap != null) cache[name] = bitmap
        return bitmap
    }
}

/** Three-dimensional enamel symbols rendered at any density. */
object CinemaSymbols {
    fun color(game: GameType?): Int = when (game) {
        GameType.MENFI -> 0xFF9D67EF.toInt()
        GameType.HEZARTAII -> 0xFFE8B64E.toInt()
        GameType.DOLO -> 0xFF21C59A.toInt()
        else -> 0xFF46C7A1.toInt()
    }
    fun game(c: Canvas, w: Int, h: Int, game: GameType?, p: Paint) {
        val k = min(w, h) / 100f; if (k <= 0) return
        p.alpha = 255
        c.save(); c.translate((w - 100 * k) / 2f, (h - 100 * k) / 2f); c.scale(k, k)
        val tint = color(game)
        p.style = Paint.Style.FILL; p.shader = RadialGradient(50f, 50f, 49f, intArrayOf(Royal.alpha(tint, .28f), 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(50f, 50f, 49f, p)
        if (game == GameType.HEZARTAII) {
            p.shader = null; p.alpha = 255; p.style = Paint.Style.STROKE; p.strokeWidth = 6f
            p.shader = LinearGradient(18f,15f,80f,76f,intArrayOf(0xFFFFEEAC.toInt(),0xFFC58A22.toInt(),0xFF7C4A10.toInt()),null,Shader.TileMode.CLAMP)
            c.drawOval(RectF(13f,22f,40f,54f),p);c.drawOval(RectF(60f,22f,87f,54f),p)
            val cup=Path().apply {moveTo(27f,16f);lineTo(73f,16f);lineTo(69f,43f);quadTo(65f,59f,54f,62f);lineTo(54f,76f);lineTo(68f,82f);lineTo(68f,89f);lineTo(32f,89f);lineTo(32f,82f);lineTo(46f,76f);lineTo(46f,62f);quadTo(34f,58f,31f,43f);close()}
            p.style=Paint.Style.FILL
            p.shader=LinearGradient(27f,0f,74f,0f,intArrayOf(0xFF956219.toInt(),0xFFFFE9A8.toInt(),0xFFF0C05F.toInt(),0xFF9E6B20.toInt(),0xFFFFD580.toInt()),null,Shader.TileMode.CLAMP)
            c.drawPath(cup,p)
            p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=1.1f;p.color=0xFFFFE8A4.toInt();c.drawPath(cup,p)
            p.style=Paint.Style.FILL;p.color=0x88FFFFFF.toInt();c.drawRoundRect(RectF(35f,22f,39f,43f),2f,2f,p)
        } else if (game == GameType.SHALAM || game == null) {
            for (i in 0..3) {
                c.save(); c.rotate(-27f + i * 18f, 50f, 84f)
                val rect = RectF(29f, 13f, 72f, 79f)
                p.shader = null; p.color = 0x66000000; c.drawRoundRect(RectF(30f, 17f, 74f, 83f), 5f, 5f, p)
                p.shader = LinearGradient(29f, 13f, 72f, 79f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF4D9.toInt(), 0xFFBFAE87.toInt()), null, Shader.TileMode.CLAMP)
                p.alpha = 255
                c.drawRoundRect(rect, 5f, 5f, p)
                p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = .7f; p.color = 0xFFBD9A53.toInt(); c.drawRoundRect(RectF(32f, 16f, 69f, 76f), 3f, 3f, p)
                p.style = Paint.Style.FILL
                val icon = listOf(RoyalIcon.DIAMOND, RoyalIcon.CLUB, RoyalIcon.HEART, RoyalIcon.SPADE)[i]
                RoyalIcons.draw(c, icon, RectF(37f, 32f, 63f, 61f), if (i % 2 == 0) 0xFFB92741.toInt() else 0xFF17332E.toInt(), p, 1f)
                RoyalIcons.draw(c, icon, RectF(33f, 18f, 42f, 27f), if (i % 2 == 0) 0xFFB92741.toInt() else 0xFF17332E.toInt(), p, 1f)
                c.restore()
            }
        } else {
            val path = if (game == GameType.MENFI) SuitShapes.spade(64f) else SuitShapes.club(64f)
            c.save(); c.translate(18f, 18f)
            p.shader = null; p.color = 0x99000000.toInt(); c.save(); c.translate(0f, 5f); c.drawPath(path, p); c.restore()
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.alpha = 255; p.shader = LinearGradient(0f, 0f, 64f, 64f, Color.WHITE, Royal.mix(tint, Color.BLACK, .6f), Shader.TileMode.CLAMP); c.drawPath(path, p)
            p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, 0f, 50f, 65f, intArrayOf(Royal.mix(tint, Color.WHITE, .5f), tint, Royal.mix(tint, Color.BLACK, .6f)), null, Shader.TileMode.CLAMP); c.drawPath(path, p)
            c.restore()
        }
        p.shader = null; p.style = Paint.Style.FILL; c.restore()
    }
}

/** Header cover holds artwork only; every title, score and control is a real native View. */
class CinemaArtView(context: Context, private val scene: CinemaScene, private val game: GameType? = null) : CachedArtView(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun artKey(): Any {
        // The browser decodes a bundled image asynchronously; a ready bitmap must replace the initial vector fallback.
        val image = when (scene) {
            CinemaScene.MAFIA -> CinemaAssets.get(context, "mafia")
            CinemaScene.THEATRE -> CinemaAssets.get(context, "panto")
            else -> null
        }
        return listOf(scene, game, image != null, image?.generationId ?: 0)
    }
    override fun drawArt(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); if (w <= 0 || h <= 0) return
        p.alpha = 255
        val tint = when (scene) { CinemaScene.MAFIA -> 0xFF7E0C2A.toInt(); CinemaScene.THEATRE -> 0xFF3C207C.toInt(); CinemaScene.SPY -> 0xFF154B7B.toInt(); else -> CinemaSymbols.color(game) }
        p.shader = RadialGradient(w * .5f, h * .42f, max(w, h) * .65f, intArrayOf(Royal.alpha(tint, .4f), 0), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p); p.shader = null
        if (scene == CinemaScene.MAFIA || scene == CinemaScene.THEATRE) {
            val bitmap = CinemaAssets.get(context, if (scene == CinemaScene.MAFIA) "mafia" else "panto")
            if (bitmap != null) {
                val sourceW = min(bitmap.width, (bitmap.height * w / h).toInt()).coerceAtLeast(1)
                val sourceH = min(bitmap.height, (bitmap.width * h / w).toInt()).coerceAtLeast(1)
                val left = (bitmap.width - sourceW) / 2
                val top = ((bitmap.height - sourceH) * .32f).toInt()
                val src = Rect(left, top, left + sourceW, top + sourceH)
                val inset = resources.displayMetrics.density
                val dst = RectF(inset, inset, w - inset, h - inset * 2f)
                val radius = 16f * resources.displayMetrics.density
                canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
                p.isFilterBitmap = true; canvas.drawBitmap(bitmap, src, dst, p)
                p.shader = LinearGradient(0f, h * .55f, 0f, h, 0, ThemeEngine.spec.bg[0], Shader.TileMode.CLAMP)
                canvas.drawRect(dst, p); canvas.restore()
                p.style = Paint.Style.STROKE; p.strokeWidth = inset
                p.shader = LinearGradient(0f,0f,w,h,ThemeEngine.spec.metalLight,CinemaLook.tint(),Shader.TileMode.CLAMP)
                canvas.drawRoundRect(dst,radius,radius,p)
                p.style = Paint.Style.FILL; p.shader = null
                return
            }
        }
        if (scene == CinemaScene.SPY) {
            val k = min(w / 160f, h / 110f)
            canvas.save(); canvas.translate(w / 2f - 80f * k, h / 2f - 55f * k); canvas.scale(k, k)
            p.shader = null; p.style = Paint.Style.FILL; p.color = 0xFF06101D.toInt()
            canvas.drawOval(RectF(26f, 27f, 135f, 57f), p)
            val hat = Path().apply { moveTo(45f, 42f); lineTo(54f, 3f); quadTo(78f, 16f, 105f, 3f); lineTo(119f, 42f); close() }
            p.shader = LinearGradient(40f, 0f, 115f, 48f, intArrayOf(0xFF1D3B59.toInt(), 0xFF06101B.toInt(), 0xFF243F54.toInt()), null, Shader.TileMode.CLAMP); canvas.drawPath(hat, p)
            p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0xFFA5C9E5.toInt(); canvas.drawPath(hat, p)
            p.style = Paint.Style.FILL; p.color = 0xFF07111B.toInt(); canvas.drawPath(Path().apply { moveTo(56f, 55f); lineTo(104f, 55f); lineTo(91f, 82f); lineTo(120f, 110f); lineTo(39f, 110f); lineTo(68f, 82f); close() }, p)
            p.color = 0xFF68D1FF.toInt(); canvas.drawRoundRect(RectF(56f, 55f, 76f, 62f), 3f, 3f, p); canvas.drawRoundRect(RectF(84f, 55f, 104f, 62f), 3f, 3f, p)
            canvas.restore(); return
        }
        CinemaSymbols.game(canvas, width, height, game, p)
    }
}

fun Screen.cinemaHero(scene: CinemaScene, title: String, caption: String, game: GameType? = null, action: View? = null): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
    layoutParams = kit.spaced(12)
    addView(CinemaArtView(host, scene, game), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(if (scene == CinemaScene.CARDS) 122 else 160)))
    addView(kit.text(title, TextStyle.DISPLAY, Royal.goldLight, Gravity.CENTER))
    addView(kit.text(caption, TextStyle.LABEL, Royal.muted, Gravity.CENTER))
    if (action != null) { addView(kit.gap(12)); addView(action, kit.fill()) }
}

class RegalCrownView(context: Context) : CachedArtView(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun artKey(): Any = "regal-crown"
    override fun drawArt(canvas: Canvas) {
        val k = min(width / 100f, height / 76f); if (k <= 0) return
        canvas.save(); canvas.translate((width - 100f * k) / 2f, (height - 76f * k) / 2f); canvas.scale(k, k)
        p.style = Paint.Style.FILL; p.shader = RadialGradient(50f, 36f, 48f, intArrayOf(0x55FFC75E, 0), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 100f, 76f, p)
        val crown = Path().apply { moveTo(20f, 53f); lineTo(12f, 19f); lineTo(34f, 34f); lineTo(50f, 10f); lineTo(66f, 34f); lineTo(88f, 19f); lineTo(80f, 53f); close() }
        p.shader = LinearGradient(15f, 10f, 80f, 60f, intArrayOf(0xFFFFF2BE.toInt(), 0xFFF0BF58.toInt(), 0xFFA76D18.toInt(), 0xFFFFD88A.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPath(crown, p); canvas.drawRoundRect(RectF(19f, 55f, 81f, 64f), 4f, 4f, p)
        p.shader = null; p.color = 0xFFFFE8A5.toInt()
        for ((x,y) in listOf(12f to 17f, 50f to 8f, 88f to 17f)) canvas.drawCircle(x, y, 4f, p)
        p.color = 0xFF1C876D.toInt(); canvas.drawCircle(50f, 42f, 4f, p)
        canvas.restore()
    }
}

class CinemaTileDrawable(private val density: Float, private val tint: Int) : SimpleDrawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val t = ThemeEngine.spec; val b = bounds
        val r = 15f * density; val rect = RectF(b.left + density, b.top + density, b.right - density, b.bottom - density * 3)
        p.style = Paint.Style.FILL; p.shader = null; p.color = Royal.alpha(Color.BLACK, if (t.light) .1f else .6f)
        canvas.drawRoundRect(RectF(rect.left, rect.top + 2 * density, rect.right, rect.bottom + 2 * density), r, r, p)
        val base = Royal.mix(t.surface, tint, if (t.light) .07f else .22f)
        p.alpha = 255
        p.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(base, t.surface, Royal.mix(t.bg[0], tint, if (t.light) .025f else .09f)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, r, r, p)
        p.shader = RadialGradient(rect.centerX(), rect.top, rect.width(), intArrayOf(Royal.alpha(tint, if (t.light) .1f else .18f), 0), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, r, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f * density
        p.alpha = 255
        p.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(Royal.alpha(t.metalLight, .8f), Royal.alpha(tint, .6f), Royal.alpha(tint, .3f), Royal.alpha(t.metal, .6f)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, r, r, p)
        p.shader = null; p.color = Royal.alpha(Color.WHITE, .13f); p.strokeWidth = density * .6f
        canvas.drawRoundRect(RectF(rect.left + 3*density,rect.top + 3*density,rect.right - 3*density,rect.bottom - 3*density),r-2*density,r-2*density,p)
        p.style = Paint.Style.FILL; p.shader = null
    }
}

class CinemaPaperDrawable(private val density: Float, private val golden: Boolean = false) : SimpleDrawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b = bounds; val r = 22f * density
        p.alpha = 255
        val rect = RectF(b.left + 2*density,b.top + density,b.right - 2*density,b.bottom - 4*density)
        p.style = Paint.Style.FILL; p.shader = LinearGradient(rect.left,rect.top,rect.right,rect.bottom,
            if (golden) intArrayOf(0xFFFFE6A1.toInt(),0xFFF0CA7D.toInt(),0xFFD9A749.toInt()) else intArrayOf(0xFFFFFAED.toInt(),0xFFF4E9CC.toInt(),0xFFE4D1A6.toInt()),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,r,r,p)
        p.style = Paint.Style.STROKE; p.strokeWidth = density * 1.5f
        p.shader = LinearGradient(rect.left,rect.top,rect.right,rect.bottom,0xFFFFF4CA.toInt(),0xFFAF8035.toInt(),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,r,r,p)
        p.shader = null; p.strokeWidth = density * .7f; p.color = 0x66896632
        val d = 6f*density
        canvas.drawRoundRect(RectF(rect.left+d,rect.top+d,rect.right-d,rect.bottom-d),r-d,r-d,p)
        for (side in 0..1) {
            val x=if(side==0)rect.left+d*2 else rect.right-d*2
            val y=rect.top+d*2
            canvas.drawCircle(x,y,d*.45f,p)
            canvas.drawCircle(x,y,d*.8f,p)
        }
        p.style=Paint.Style.FILL; p.shader=null
    }
}
