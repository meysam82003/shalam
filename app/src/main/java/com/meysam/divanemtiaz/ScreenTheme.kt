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
        val w = width.toFloat(); val h = height.toFloat(); if (w <= 0 || h <= 0) return
        val t = spec; val k = w / 180f; val height = h / k
        canvas.save(); canvas.scale(k,k)
        paint.style = Paint.Style.FILL; paint.shader = LinearGradient(0f,0f,180f,height,t.bg[0],t.surface,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f,0f,180f,height),14f,14f,paint)
        paint.shader = android.graphics.RadialGradient(90f,24f,120f,intArrayOf(Royal.alpha(t.accent,.13f),0),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f,0f,180f,height),14f,14f,paint)
        fun box(x:Float,y:Float,bw:Float,bh:Float,color:Int,r:Float=7f) {
            paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color
            canvas.drawRoundRect(RectF(x,y,x+bw,y+bh),r,r,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=.7f
            paint.shader=LinearGradient(x,y,x+bw,y+bh,t.metalLight,t.border,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(x,y,x+bw,y+bh),r,r,paint);paint.shader=null;paint.style=Paint.Style.FILL
        }
        val crown=android.graphics.Path().apply {moveTo(76f,23f);lineTo(72f,9f);lineTo(83f,15f);lineTo(90f,4f);lineTo(97f,15f);lineTo(108f,9f);lineTo(104f,23f);close()}
        paint.shader=LinearGradient(75f,4f,105f,26f,t.metalLight,t.accent,Shader.TileMode.CLAMP);canvas.drawPath(crown,paint)
        paint.shader=null;paint.color=t.accent;canvas.drawRoundRect(RectF(52f,30f,128f,35f),2f,2f,paint)
        val cellH=(height-88f)/3f
        val icons=listOf(RoyalIcon.CARDS,RoyalIcon.SPADE,RoyalIcon.TROPHY,RoyalIcon.CLUB)
        for(i in 0..3){val x=10f+(i%2)*83f;val y=45f+(i/2)*(cellH+5f)
            box(x,y,77f,cellH,t.surface)
            RoyalIcons.draw(canvas,icons[i],RectF(x+25f,y+6f,x+52f,y+33f),if(i%2==0)t.accent else t.success,paint,1.5f)
            paint.color=t.text;canvas.drawRoundRect(RectF(x+20f,y+cellH-11f,x+57f,y+cellH-8f),1f,1f,paint)
        }
        val y=45f+2*(cellH+5f)
        for(i in 0..2){val x=10f+i*55f;box(x,y,50f,cellH*.72f,t.surface)
            RoyalIcons.draw(canvas,listOf(RoyalIcon.USER,RoyalIcon.PLAY,RoyalIcon.EYE)[i],RectF(x+15f,y+6f,x+35f,y+26f),t.accent,paint,1.3f)
        }
        box(10f,height-20f,160f,13f,t.surface)
        for(i in 0..4){paint.color=if(i==4)t.accent else t.muted;canvas.drawCircle(30f+i*30f,height-13f,2.6f,paint)}
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
            addView(RegalCrownView(host), LinearLayout.LayoutParams(kit.dp(92), kit.dp(92)))
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
