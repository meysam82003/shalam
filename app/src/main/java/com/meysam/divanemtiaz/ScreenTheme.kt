package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.min

/** Miniature page of a style, drawn from its own tokens (not the active style), for the style picker. */
class ThemePreviewView(context: Context, private val spec: ThemeSpec) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val t = spec; val k = w / 180f
        canvas.save(); canvas.scale(k, k)
        val height = h / k
        paint.style = Paint.Style.FILL; paint.shader = null; paint.color = t.bg[0]
        canvas.drawRect(0f, 0f, 180f, height, paint)
        DivanDesign.background(canvas, 180f, height, .5f, t, paint)
        fun box(x: Float, y: Float, bw: Float, bh: Float, color: Int, radius: Float = 12f) {
            paint.style = Paint.Style.FILL; paint.color = color
            val r = DivanDesign.radius(t, radius)
            canvas.drawRoundRect(RectF(x, y, x + bw, y + bh), r, r, paint)
        }
        fun line(x: Float, y: Float, length: Float, color: Int = t.muted) = box(x, y, length, 3f, color, 2f)
        line(108f, 12f, 58f, t.text); line(128f, 20f, 38f)
        val paper = t.layout == ThemeLayout.PAPER
        val center = t.layout == ThemeLayout.PALM || t.layout == ThemeLayout.ORBIT
        val heroH = if (center) 68f else 47f
        if (!paper) box(10f, 33f, 160f, heroH, t.surface)
        if (center) {
            box(77f, 40f, 26f, 26f, t.accent)
            line(56f, 76f, 68f, t.text); line(66f, 85f, 48f)
        } else {
            line(80f, 43f, 76f, t.accent); line(54f, 54f, 102f, t.text); line(99f, 65f, 57f)
            if (!paper) box(19f, 45f, 27f, 27f, t.accent)
        }
        val start = 33f + heroH + 12f
        if (paper || t.layout == ThemeLayout.WAVE) {
            for (i in 0..2) {
                box(10f, start + 31f * i, 160f, 26f, t.surface)
                box(140f, start + 5f + 31f * i, 16f, 16f, t.accent)
                line(74f, start + 8f + 31f * i, 56f, t.text)
                line(94f, start + 16f + 31f * i, 36f)
            }
        } else {
            val cardH = (height - start - 34f) / 2f
            for (i in 0..3) {
                val x = 10f + (i % 2) * 83f; val y = start + (i / 2) * (cardH + 6f)
                box(x, y, 77f, cardH, t.surface)
                box(x + 48f, y + 7f, 20f, 20f, t.accent)
                line(x + 28f, y + cardH - 13f, 40f, t.text)
            }
        }
        box(10f, height - 22f, 160f, 16f, t.surface)
        for (i in 0..4) box(25f + i * 30f, height - 17f, if (i == 4) 18f else 8f, 6f, if (i == 4) t.accent else t.muted, 3f)
        canvas.restore()
    }
}

/** «ظاهر برنامه»: the six styles with a live miniature of each; the choice applies to every page at once. */
class ThemeScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("ظاهر برنامه", "${kit.n(Themes.all.size)} سبک • همهٔ صفحه‌ها و بازی‌ها") {
        addView(kit.text("شش دنیای متفاوت برای دیوان؛ سبک دلخواهت را لمس کن تا تمام برنامه همان لحظه تغییر کند.",
            TextStyle.CAPTION, Royal.muted), kit.spaced(10))
        addView(themeGrid(this@ThemeScreen))
    }
}

/**
 * First launch: the user picks the look of the app before anything else. A tap shows the style at once;
 * «ادامه» keeps it, and from then on only the settings change it.
 */
class ThemeWelcomeScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("به دیوان امتیاز خوش آمدید", "ظاهر برنامه را انتخاب کنید", showBack = false,
        bottom = kit.button("ادامه با سبک «${ThemeEngine.spec.title}»", ButtonKind.PRIMARY, RoyalIcon.CHECK, 54) {
            val id = ThemeEngine.spec.id
            host.updateSettings { s -> s.copy(general = s.general.copy(theme = id)) }
            host.resetTo(HomeScreen(host))
        }) {
        addView(kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            addView(HeroCrestView(host), LinearLayout.LayoutParams(kit.dp(92), kit.dp(92)))
        }, kit.spaced(4))
        addView(kit.text("یکی از شش سبک را لمس کنید تا همین حالا روی برنامه ببینید؛ بعداً هم از «تنظیمات ← ظاهر برنامه» قابل تغییر است.",
            TextStyle.LABEL, Royal.muted, Gravity.CENTER), kit.spaced(10))
        addView(themeGrid(this@ThemeWelcomeScreen) { t -> host.applyTheme(t.id) })
    }
}

/** Two-column picker of every style; a tap saves the style, or calls [onPick] instead (first-launch preview). */
fun themeGrid(screen: Screen, onPick: ((ThemeSpec) -> Unit)? = null): View {
    val kit = screen.kit
    val host = screen.host
    val current = ThemeEngine.spec.id
    return kit.grid(2, Themes.all.map { t ->
        kit.vertical().apply {
            background = PanelDrawable(kit.density, if (t.id == current) PanelStyle.SELECTED else PanelStyle.NORMAL, 18f)
            setPadding(kit.dp(8), kit.dp(8), kit.dp(8), kit.dp(12))
            addView(ThemePreviewView(host, t), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(192)))
            addView(kit.gap(6))
            addView(kit.horizontal().apply {
                addView(kit.weight(kit.text(t.title, TextStyle.BODY_BOLD, if (t.id == current) Royal.turquoiseLight else Royal.goldLight, maxLines = 1)))
                if (t.id == current) addView(kit.icon(RoyalIcon.CHECK, Royal.turquoiseLight, 18))
            })
            addView(kit.text(t.latin, TextStyle.CAPTION, Royal.dim, maxLines = 1).apply { textDirection = View.TEXT_DIRECTION_LTR; gravity = Gravity.END })
            addView(kit.text(t.summary, TextStyle.CAPTION, Royal.muted, maxLines = 3))
            addView(kit.gap(8))
            addView(kit.badge(if (t.id == current) "ظاهر فعلی" else "انتخاب سبک", if (t.id == current) Royal.turquoise else Royal.gold, true))
            isClickable = true
            contentDescription = "سبک ${t.title}"
            setOnClickListener {
                kit.tap(it)
                if (onPick != null) onPick(t)
                else if (t.id != ThemeEngine.spec.id) host.updateSettings { s -> s.copy(general = s.general.copy(theme = t.id)) }
            }
        }
    }, 8)
}
