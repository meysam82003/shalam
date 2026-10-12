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
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val t = spec
        val outer = 14f * d
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, h, t.bg, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, outer, outer, paint)
        t.glows.take(2).forEach { g ->
            paint.shader = android.graphics.RadialGradient(w * g.x, h * g.y, w * g.radius, g.color, 0, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, outer, outer, paint)
        }
        paint.shader = null
        val pad = 10f * d
        // Title bar
        paint.color = t.titleColor
        canvas.drawRoundRect(RectF(w - pad - w * 0.42f, pad, w - pad, pad + 6f * d), 3f * d, 3f * d, paint)
        paint.color = Royal.alpha(t.muted, 0.7f)
        canvas.drawRoundRect(RectF(w - pad - w * 0.28f, pad + 9f * d, w - pad, pad + 12.5f * d), 2f * d, 2f * d, paint)
        // Card with a timer ring
        val r = 10f * d * t.radius
        val card = RectF(pad, pad + 20f * d, w - pad, h * 0.62f)
        paint.color = Royal.alpha(t.shadow or 0xFF000000.toInt(), Color.alpha(t.shadow) / 255f * 0.5f)
        canvas.drawRoundRect(RectF(card.left, card.top + 2.5f * d, card.right, card.bottom + 2.5f * d), r, r, paint)
        paint.shader = LinearGradient(0f, card.top, 0f, card.bottom, t.raised, t.raisedLow, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card, r, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = t.frameWidth * d
        paint.shader = LinearGradient(card.left, card.top, card.left, card.bottom, t.border, t.borderLow, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card, r, r, paint)
        paint.shader = null
        val ringR = min(card.height(), card.width()) * 0.30f
        val cx = card.left + card.width() * 0.3f
        val cy = card.centerY()
        paint.strokeWidth = 4.5f * d
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = t.ringTrack
        canvas.drawCircle(cx, cy, ringR, paint)
        paint.shader = SweepGradient(cx, cy, t.ring + t.ring[0], null)
        canvas.drawArc(RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR), -90f, 250f, false, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = t.text
        canvas.drawRoundRect(RectF(card.right - pad - card.width() * 0.38f, cy - 9f * d, card.right - pad, cy - 3f * d), 3f * d, 3f * d, paint)
        paint.color = t.muted
        canvas.drawRoundRect(RectF(card.right - pad - card.width() * 0.26f, cy + 2f * d, card.right - pad, cy + 6f * d), 2f * d, 2f * d, paint)
        // Chips
        val chipTop = h * 0.66f
        val chipH = 11f * d
        val chipW = (w - pad * 2 - 6f * d * 2) / 3f
        for (i in 0 until 3) {
            val left = w - pad - (i + 1) * chipW - i * 6f * d
            val chip = RectF(left, chipTop, left + chipW, chipTop + chipH)
            val fill = if (i == 0) t.successFill else t.chip
            paint.shader = LinearGradient(0f, chip.top, 0f, chip.bottom, fill, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(chip, chipH / 2f, chipH / 2f, paint)
            paint.shader = null
            if (i != 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = d
                paint.color = t.chipBorder
                canvas.drawRoundRect(chip, chipH / 2f, chipH / 2f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        // Primary button
        val btn = RectF(pad, h - pad - 15f * d, w - pad, h - pad)
        val br = (8f * d * t.buttonRadius).coerceAtMost(btn.height() / 2f)
        paint.shader = if (t.primaryHorizontal) LinearGradient(btn.right, 0f, btn.left, 0f, t.primary, null, Shader.TileMode.CLAMP)
        else LinearGradient(0f, btn.top, 0f, btn.bottom, t.primary, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(btn, br, br, paint)
        paint.shader = null
        paint.color = t.primaryText
        canvas.drawRoundRect(RectF(btn.centerX() - w * 0.14f, btn.centerY() - 1.6f * d, btn.centerX() + w * 0.14f, btn.centerY() + 1.6f * d), d, d, paint)
    }
}

/** «ظاهر برنامه»: the six styles with a live miniature of each; the choice applies to every page at once. */
class ThemeScreen(host: MainActivity) : Screen(host) {
    override fun build(): View = scaffold("ظاهر برنامه", "${kit.n(Themes.all.size)} سبک • همهٔ صفحه‌ها و بازی‌ها") {
        addView(kit.text("هر سبک رنگ، قاب کارت‌ها، دکمه‌ها، نور و بافت پس‌زمینه و زمان‌سنج‌های خودش را دارد؛ داده‌ها و بازی‌ها تغییری نمی‌کنند.",
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
            addView(HeroCrestView(host), LinearLayout.LayoutParams(kit.dp(200), kit.dp(150)))
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
            addView(ThemePreviewView(host, t), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(132)))
            addView(kit.gap(6))
            addView(kit.horizontal().apply {
                addView(kit.weight(kit.text(t.title, TextStyle.BODY_BOLD, if (t.id == current) Royal.turquoiseLight else Royal.goldLight, maxLines = 1)))
                if (t.id == current) addView(kit.icon(RoyalIcon.CHECK, Royal.turquoiseLight, 18))
            })
            addView(kit.text(t.latin, TextStyle.CAPTION, Royal.dim, maxLines = 1).apply { textDirection = View.TEXT_DIRECTION_LTR; gravity = Gravity.END })
            addView(kit.text(t.summary, TextStyle.CAPTION, Royal.muted, maxLines = 3))
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
