package com.meysam.divanemtiaz

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.MessageQueue
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/** Shared motion curves and timings. */
object Motion {
    /** easeOutCubic. */
    val EASE_OUT = PathInterpolator(0.33f, 1f, 0.68f, 1f)
    /** easeInOutCubic. */
    val EASE_IN_OUT = PathInterpolator(0.65f, 0f, 0.35f, 1f)
    /** easeInCubic, for leaving elements. */
    val EASE_IN = PathInterpolator(0.32f, 0f, 0.67f, 0f)
    const val PAGE_IN = 280L
    const val PAGE_OUT = 220L
    const val ITEM = 240L
    const val STAGGER = 30L
}

/**
 * Renders card art while the main thread is idle, so cards scrolled or filtered into view are already drawn.
 * Held back while transitions run, so it never competes with an animation frame.
 */
object ArtWarmer {
    /** Small units of work; each returns true when it did something worth a frame's time. */
    private val queue = ArrayDeque<() -> Boolean>()
    private var holdUntil = 0L
    private var installed = false
    private val handler by lazy { android.os.Handler(Looper.getMainLooper()) }
    private val resume = Runnable { install() }

    // One unit per idle moment of the main thread: each is a few milliseconds and never blocks a frame.
    private val idle = MessageQueue.IdleHandler {
        val wait = holdUntil - SystemClock.uptimeMillis()
        if (wait > 0) {
            installed = false
            handler.removeCallbacks(resume)
            handler.postDelayed(resume, wait)
            return@IdleHandler false
        }
        while (queue.isNotEmpty()) {
            if (queue.removeFirst().invoke()) break
        }
        if (queue.isEmpty()) { installed = false; false } else true
    }

    private fun install() {
        if (!installed && queue.isNotEmpty()) {
            installed = true
            Looper.myQueue().addIdleHandler(idle)
        }
    }

    fun enqueue(v: CachedArtView) {
        val ref = WeakReference(v)
        post { ref.get()?.warm() ?: false }
    }

    /** Queues [task] to run while the main thread is idle. */
    fun post(task: () -> Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        queue.addLast(task)
        install()
    }

    /** Keeps the warmer quiet for [ms] (page and grid transitions). */
    fun hold(ms: Long) { holdUntil = max(holdUntil, SystemClock.uptimeMillis() + ms) }
}

/** Persian-aware search: Arabic ي/ك, ZWNJ, spaces and case are ignored. */
object SearchText {
    fun norm(s: String): String = s.lowercase()
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').replace('ة', 'ه').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace("‌", "").replace(" ", "").replace("\n", "")

    fun matches(query: String, vararg fields: String?): Boolean {
        val q = norm(query.trim())
        if (q.isEmpty()) return true
        return fields.any { it != null && norm(it).contains(q) }
    }
}

/**
 * Grid of cards keyed by a stable id. Changing the shown set never rebuilds a card: each key keeps one view, cards
 * that stay glide to their new cell, leaving cards fade and shrink, arriving cards fade and grow with a small stagger.
 */
class CardGrid(context: Context, private val columns: Int, private val gapPx: Int) : ViewGroup(context) {
    private val byKey = HashMap<String, View>()
    private var order: List<String> = emptyList()
    private val leaving = HashSet<View>()
    private val oldFrames = HashMap<View, Rect>()
    private val entering = ArrayList<View>()
    private var animateNext = false
    var animations = true

    init {
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        clipChildren = false
    }

    val keys: List<String> get() = order

    fun viewFor(key: String): View? = byKey[key]

    /** Shows [keys] in this order; [factory] is called only for keys never shown before. */
    fun submit(keys: List<String>, animate: Boolean = true, factory: (String) -> View) {
        val next = keys.distinct()
        val wanted = next.toHashSet()
        val run = animate && animations && isLaidOut && order.isNotEmpty()
        oldFrames.clear()
        entering.clear()
        if (run) {
            for (k in order) byKey[k]?.let { v ->
                if (v.visibility == View.VISIBLE && v !in leaving) {
                    val dx = v.translationX.toInt(); val dy = v.translationY.toInt()
                    oldFrames[v] = Rect(v.left + dx, v.top + dy, v.right + dx, v.bottom + dy)
                }
            }
        }
        for (k in order) if (k !in wanted) byKey[k]?.let { v -> if (run) leave(v) else { v.animate().cancel(); v.visibility = View.GONE } }
        for (k in next) {
            var v = byKey[k]
            if (v == null) {
                v = factory(k)
                (v.parent as? ViewGroup)?.removeView(v)
                byKey[k] = v
                addView(v)
                if (run) entering += v
            } else {
                if (v in leaving) {
                    leaving -= v
                    v.animate().cancel()
                    v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f
                    oldFrames.remove(v)
                }
                if (v.visibility != View.VISIBLE || k !in order) {
                    v.visibility = View.VISIBLE
                    if (run) entering += v
                }
            }
        }
        order = next
        animateNext = run
        if (run) ArtWarmer.hold(Motion.ITEM + 200)
        requestLayout()
    }

    private fun leave(v: View) {
        v.animate().cancel()
        if (!isNearScreen(v)) {
            v.visibility = View.GONE
            v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; v.translationX = 0f; v.translationY = 0f
            return
        }
        leaving += v
        v.animate().alpha(0f).scaleX(0.82f).scaleY(0.82f).setDuration(Motion.ITEM - 60).setStartDelay(0).setInterpolator(Motion.EASE_IN)
            .withEndAction {
                if (v in leaving) {
                    leaving -= v
                    v.visibility = View.GONE
                    v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; v.translationX = 0f; v.translationY = 0f
                }
            }.start()
    }

    private fun cell(width: Int) = ((width - paddingLeft - paddingRight - gapPx * (columns - 1)) / columns).coerceAtLeast(0)

    private fun shown(): List<View> = order.mapNotNull { byKey[it] }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cell = cell(width)
        val list = shown()
        var height = paddingTop + paddingBottom
        var i = 0
        while (i < list.size) {
            val row = list.subList(i, min(i + columns, list.size))
            row.forEach { it.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) }
            val rowMax = row.maxOf { it.measuredHeight }
            row.forEach { if (it.measuredHeight != rowMax) it.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowMax, MeasureSpec.EXACTLY)) }
            height += rowMax + if (i + columns < list.size) gapPx else 0
            i += columns
        }
        // Leaving cards keep their size while they fade out.
        leaving.forEach { v -> if (v.measuredWidth == 0) v.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val cell = cell(width)
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val list = shown()
        var y = paddingTop
        var i = 0
        while (i < list.size) {
            val row = list.subList(i, min(i + columns, list.size))
            var rowHeight = 0
            row.forEachIndexed { col, child ->
                val x = if (rtl) width - paddingRight - (col + 1) * cell - col * gapPx else paddingLeft + col * (cell + gapPx)
                child.layout(x, y, x + cell, y + child.measuredHeight)
                rowHeight = max(rowHeight, child.measuredHeight)
            }
            y += rowHeight + gapPx
            i += columns
        }
        if (animateNext) {
            animateNext = false
            var n = 0
            list.forEach { v ->
                val old = oldFrames[v]
                val visible = isNearScreen(v) || (old != null && isNearScreen(old.top, old.height()))
                if (!visible) {
                    // Off screen: no animation, just the final state.
                    if (v in entering || old != null) { v.animate().cancel(); v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; v.translationX = 0f; v.translationY = 0f }
                } else if (v in entering) {
                    v.animate().cancel()
                    v.translationX = 0f; v.translationY = 0f
                    v.alpha = 0f; v.scaleX = 0.86f; v.scaleY = 0.86f
                    v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.ITEM).setInterpolator(Motion.EASE_OUT)
                        .setStartDelay(if (visible) min(n++, 10) * Motion.STAGGER else 0L).start()
                } else if (old != null && (old.left != v.left || old.top != v.top)) {
                    v.animate().cancel()
                    v.translationX = (old.left - v.left).toFloat()
                    v.translationY = (old.top - v.top).toFloat()
                    v.animate().translationX(0f).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(Motion.ITEM + 20).setStartDelay(0).setInterpolator(Motion.EASE_IN_OUT).start()
                }
            }
            entering.clear()
            oldFrames.clear()
        }
    }

    private val tmp = IntArray(2)
    private fun isNearScreen(v: View): Boolean = isNearScreen(v.top, v.height)
    private fun isNearScreen(top: Int, height: Int): Boolean {
        getLocationOnScreen(tmp)
        val h = resources.displayMetrics.heightPixels
        val y = tmp[1] + top
        return y < h && y + height > 0
    }
}

/** Segmented tabs whose gold indicator slides between options. Labels can be updated in place. */
class SlidingTabs(
    context: Context,
    private val kit: RoyalKit,
    labels: List<String>,
    selected: Int,
    private val onSelect: (Int) -> Unit
) : LinearLayout(context) {
    private val tabs = ArrayList<TextView>()
    private val indicator = ButtonDrawable(kit.density, ButtonKind.CHIP_GOLD, 12f)
    private val track = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
    private var indicatorX = -1f
    private var indicatorW = 0f
    private var anim: ValueAnimator? = null
    var selected: Int = selected
        private set
    private val onColor = ButtonDrawable.textColor(ButtonKind.CHIP_GOLD)
    private val offColor = ButtonDrawable.textColor(ButtonKind.CHIP)

    init {
        orientation = HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setWillNotDraw(false)
        background = track
        val pad = kit.dp(3)
        setPadding(pad, pad, pad, pad)
        labels.forEachIndexed { i, label ->
            val t = kit.text(label, TextStyle.LABEL_BOLD, if (i == selected) onColor else offColor, Gravity.CENTER, 2).apply {
                minHeight = kit.dp(42)
                setPadding(kit.dp(6), kit.dp(4), kit.dp(6), kit.dp(6))
                isClickable = true
                contentDescription = label
                setOnClickListener { kit.tap(this@SlidingTabs); select(i, true, notify = true) }
            }
            tabs += t
            addView(t, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    fun setLabel(i: Int, label: String) { tabs.getOrNull(i)?.let { it.text = label; it.contentDescription = label } }

    fun select(i: Int, animate: Boolean, notify: Boolean = false) {
        if (i !in tabs.indices) return
        val from = selected
        selected = i
        val target = tabs[i]
        val animateNow = animate && kit.settings.general.animations && target.width > 0
        anim?.cancel()
        if (animateNow) {
            val startX = indicatorX; val startW = indicatorW
            val endX = target.left.toFloat(); val endW = target.width.toFloat()
            val eval = ArgbEvaluator()
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260
                interpolator = Motion.EASE_OUT
                addUpdateListener { a ->
                    val f = a.animatedValue as Float
                    indicatorX = startX + (endX - startX) * f
                    indicatorW = startW + (endW - startW) * f
                    tabs.getOrNull(from)?.setTextColor(eval.evaluate(f, onColor, offColor) as Int)
                    target.setTextColor(eval.evaluate(f, offColor, onColor) as Int)
                    invalidate()
                }
                start()
            }
        } else {
            tabs.forEachIndexed { j, t -> t.setTextColor(if (j == i) onColor else offColor) }
            if (target.width > 0) { indicatorX = target.left.toFloat(); indicatorW = target.width.toFloat() } else indicatorX = -1f
            invalidate()
        }
        if (notify && from != i) onSelect(i)
        else if (notify) onSelect(i)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (anim?.isRunning != true) {
            tabs.getOrNull(selected)?.let { indicatorX = it.left.toFloat(); indicatorW = it.width.toFloat() }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (indicatorX < 0f || indicatorW <= 0f) return
        indicator.setBounds(indicatorX.toInt(), paddingTop, (indicatorX + indicatorW).toInt(), height - paddingBottom)
        indicator.draw(canvas)
    }
}

/** Live search field (with icon and clear button) for card lists. */
fun RoyalKit.searchBox(hint: String, initial: String = "", onQuery: (String) -> Unit): View {
    val box = FrameLayout(context)
    val edit = EditText(context).apply {
        this.hint = hint
        setText(initial)
        setTextColor(Royal.ivory)
        setHintTextColor(Royal.dim)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize(14f))
        typeface = RoyalFonts.get(context, false)
        background = PanelDrawable(density, PanelStyle.FLAT, 14f)
        setPadding(dp(42), dp(8), dp(40), dp(10))
        minHeight = dp(44)
        setSingleLine(true)
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        textDirection = View.TEXT_DIRECTION_RTL
        contentDescription = hint
    }
    val clear = icon(RoyalIcon.CLOSE, Royal.muted, 18).apply {
        isClickable = true
        contentDescription = "پاک کردن جست‌وجو"
        visibility = if (initial.isEmpty()) View.GONE else View.VISIBLE
        setOnClickListener { edit.setText("") }
    }
    edit.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            onQuery(s?.toString() ?: "")
        }
    })
    box.addView(edit, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    // RTL: the search icon sits at the start (right), the clear button at the end (left).
    box.addView(icon(RoyalIcon.SEARCH, Royal.gold, 20), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL or Gravity.RIGHT).apply { rightMargin = dp(12) })
    box.addView(clear, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER_VERTICAL or Gravity.LEFT).apply { leftMargin = dp(14) })
    return box
}

/**
 * Search box that filters existing views in place (no rebuild): each entry is a view and the text it is found by.
 * [minItems]: below this many entries no box is shown (short lists need none).
 */
fun RoyalKit.searchFilter(hint: String, entries: List<Pair<View, String>>, initial: String = "", minItems: Int = 0, onChange: ((String) -> Unit)? = null): View? {
    if (entries.size < minItems) return null
    fun apply(q: String) { entries.forEach { (v, text) -> v.visibility = if (SearchText.matches(q, text)) View.VISIBLE else View.GONE } }
    if (initial.isNotEmpty()) apply(initial)
    return searchBox(hint, initial) { q -> apply(q); onChange?.invoke(q) }
}
