package com.meysam.divanemtiaz

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

fun MafiaSide?.tint(): Int = when (this) {
    MafiaSide.CITY -> MafiaColors.CITY
    MafiaSide.MAFIA -> MafiaColors.MAFIA
    MafiaSide.INDEPENDENT -> MafiaColors.INDEPENDENT
    null -> MafiaColors.NEUTRAL
}

fun MafiaSide?.textColor(): Int = when (this) {
    MafiaSide.CITY -> Royal.turquoiseLight
    MafiaSide.MAFIA -> Royal.crimsonLight
    MafiaSide.INDEPENDENT -> 0xFFD7B8FF.toInt()
    null -> Royal.muted
}

/** The narrator's own role pictures, copied into the app and decoded once per size. */
object MafiaPictures {
    private val cache = HashMap<String, Bitmap?>()

    fun dir(context: Context): File = File(context.filesDir, "mafia_pictures").apply { mkdirs() }

    fun import(context: Context, uri: Uri, key: String): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val file = File(dir(context), "${key}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        file.absolutePath
    }.getOrNull()

    fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return cache.getOrPut(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }

    fun forget(path: String) { cache.remove(path) }
}

/** Shared settings access and sound for every mafia screen. */
val Screen.mafiaSettings: MafiaSettings get() = host.mafia.settings()

/** Plays a narrator cue if sound effects are on; [onDone] runs when it ends (or right away when silent). */
fun Screen.playCue(key: String, onDone: (() -> Unit)? = null) {
    val s = mafiaSettings
    if (!s.soundEffects) { onDone?.invoke(); return }
    val audio = host.audio()
    audio.volume = s.effectVolume / 100f
    if (!audio.play(s.cue(key), onDone)) onDone?.invoke()
}

fun Screen.startNightMusic() {
    val s = mafiaSettings
    val audio = host.audio()
    audio.musicVolume = s.musicVolume / 100f
    audio.startMusic(s.nightMusic)
}

/** Small round play button for a cue (gold), shown next to every narrator text. */
fun Screen.cueButton(key: String, size: Int = 40): View {
    val set = mafiaSettings.cue(key)?.isSet == true
    return kit.iconButton(RoyalIcon.PLAY, "پخش صدا: ${MafiaCues.title(key)}", if (set) ButtonKind.CHIP_GOLD else ButtonKind.CHIP, size) {
        if (mafiaSettings.cue(key)?.isSet != true) kit.toast("برای «${MafiaCues.title(key)}» صدایی انتخاب نشده است؛ از تنظیمات راوی صدا بگذارید")
        else playCue(key)
    }
}

/** A role card drawn in code (with the narrator's own picture if he set one). */
fun Screen.roleCard(role: MafiaRole, faceDown: Boolean = false, dead: Boolean = false): RoleCardView = RoleCardView(host).apply {
    glyph = role.glyph
    tint = role.side.tint()
    title = role.faName
    subtitle = role.tagline
    portrait = MafiaPictures.load(mafiaSettings.rolePictures[role.name])
    this.faceDown = faceDown
    this.dead = dead
    contentDescription = role.faName
}

fun Screen.lastCard(card: MafiaLastCard, faceDown: Boolean = false): RoleCardView = RoleCardView(host).apply {
    glyph = card.glyph
    tint = MafiaColors.NEUTRAL
    title = card.faName.replace("\n", " ")
    subtitle = card.tagline
    portrait = MafiaPictures.load(mafiaSettings.rolePictures["CARD_" + card.name])
    this.faceDown = faceDown
    contentDescription = card.faName
}

fun Screen.seal(glyph: MafiaGlyph, side: MafiaSide?, size: Int): View = RoleSealView(host, glyph, side.tint()).apply {
    layoutParams = LinearLayout.LayoutParams(kit.dp(size), kit.dp(size))
}

/**
 * Player tile: emblem, name and (optionally) the role under it. Used in the night target grid,
 * the distribution grid and the players lists.
 */
fun Screen.playerTile(
    p: MafiaPlayer,
    showRole: Boolean,
    selected: Boolean = false,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): View = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
    background = PanelDrawable(kit.density, when {
        selected -> PanelStyle.SELECTED
        !p.alive -> PanelStyle.GHOST
        else -> PanelStyle.FLAT
    }, 16f)
    setPadding(kit.dp(4), kit.dp(8), kit.dp(4), kit.dp(10))
    addView(FrameLayout(host).apply {
        addView(kit.avatar(p.avatar, 50).apply { layoutParams = FrameLayout.LayoutParams(kit.dp(50), kit.dp(50), Gravity.CENTER) })
        val shown = p.shownRole
        if (showRole && shown != null) {
            addView(seal(shown.glyph, shown.side, 26).apply {
                layoutParams = FrameLayout.LayoutParams(kit.dp(26), kit.dp(26), Gravity.BOTTOM or Gravity.LEFT)
            })
        }
    }, LinearLayout.LayoutParams(kit.dp(60), kit.dp(54)))
    addView(kit.text(p.name, TextStyle.LABEL_BOLD, if (p.alive) Royal.ivory else Royal.dim, Gravity.CENTER, 1))
    if (showRole && p.shownRole != null) addView(kit.text(p.shownRole!!.faName, TextStyle.CAPTION, (p.side ?: p.shownRole!!.side).textColor(), Gravity.CENTER, 1))
    if (badge != null) addView(kit.badge(badge, if (selected) Royal.turquoise else Royal.gold).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = kit.dp(3) }
    })
    alpha = if (enabled) 1f else 0.45f
    if (onClick != null) {
        isClickable = true
        contentDescription = p.name
        setOnClickListener { kit.tap(it); onClick() }
    }
}

/** Big narrator text box: what to say now, with the cue play button. */
fun Screen.narration(text: String, cue: String?, style: PanelStyle = PanelStyle.RAISED): View = kit.panel(style, 14).apply {
    layoutParams = kit.spaced(10)
    addView(kit.horizontal().apply {
        addView(kit.icon(RoyalIcon.RULES, Royal.gold, 20))
        addView(kit.hgap(6))
        addView(kit.weight(kit.text("متن راوی", TextStyle.LABEL_BOLD, Royal.gold)))
        if (cue != null) addView(cueButton(cue, 40))
    })
    addView(kit.gap(6))
    addView(kit.text(text, TextStyle.HEADING, Royal.ivory, Gravity.CENTER))
}

/** Persian minute:second. */
fun Screen.clock(seconds: Int): String = kit.n(String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60))

/**
 * A countdown with start / pause / reset. Ticks in the last nine seconds and plays the end cue at zero,
 * like the source app's speaking rows.
 */
class MafiaTimer(private val screen: Screen, var total: Int, private val onTick: (Int, Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    var left = total
        private set
    var running = false
        private set
    private val step = object : Runnable {
        override fun run() {
            if (!running) return
            left -= 1
            if (left in 1..9) screen.playCue(MafiaCues.TIMER_TICK)
            if (left <= 0) {
                left = 0
                running = false
                screen.playCue(MafiaCues.TIMER_END)
                onTick(left, false)
                return
            }
            onTick(left, true)
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (running || left <= 0) return
        running = true
        onTick(left, true)
        handler.postDelayed(step, 1000)
    }

    fun pause() { running = false; handler.removeCallbacks(step); onTick(left, false) }

    fun reset(seconds: Int = total) { pause(); total = seconds; left = seconds; onTick(left, false) }

    fun stop() { running = false; handler.removeCallbacks(step) }
}

/** Row with a player and his own countdown (speaking / defence). */
fun Screen.timerRow(p: MafiaPlayer, seconds: Int, note: String?, blocked: String?, timers: MutableList<MafiaTimer>, extra: View? = null): View {
    val time = kit.text(clock(seconds), TextStyle.TITLE, Royal.goldLight, Gravity.CENTER, 1)
    lateinit var playButton: TextView
    val timer = MafiaTimer(this, seconds) { left, running ->
        time.text = clock(left)
        time.setTextColor(if (left in 1..10) Royal.crimsonLight else if (running) Royal.turquoiseLight else Royal.goldLight)
        playButton.text = if (running) "توقف" else if (left == 0) "تمام" else "شروع"
    }
    timers += timer
    playButton = kit.chip("شروع", false, ButtonKind.CHIP_GOLD) {
        if (blocked != null) { kit.toast(blocked); return@chip }
        if (timer.running) timer.pause() else {
            timers.filter { it !== timer }.forEach { it.pause() }
            timer.start()
        }
    }.apply { setPadding(kit.dp(14), kit.dp(4), kit.dp(14), kit.dp(6)) }
    return kit.vertical().apply {
        background = PanelDrawable(kit.density, if (blocked != null) PanelStyle.GHOST else PanelStyle.FLAT, 16f)
        setPadding(kit.dp(10), kit.dp(8), kit.dp(10), kit.dp(10))
        layoutParams = kit.spaced(6)
        addView(kit.horizontal().apply {
            addView(kit.avatar(p.avatar, 42))
            addView(kit.hgap(8))
            addView(kit.vertical().apply {
                addView(kit.text(p.name, TextStyle.BODY_BOLD, Royal.ivory, maxLines = 1))
                if (blocked != null) addView(kit.text(blocked, TextStyle.CAPTION, Royal.crimsonLight, maxLines = 2))
                else if (note != null) addView(kit.text(note, TextStyle.CAPTION, Royal.muted, maxLines = 2))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(time, LinearLayout.LayoutParams(kit.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(kit.hgap(6))
            addView(playButton)
            addView(kit.hgap(4))
            addView(kit.iconButton(RoyalIcon.UNDO, "بازنشانی", ButtonKind.CHIP, 38) { timer.reset() })
        })
        if (extra != null) { addView(kit.gap(6)); addView(extra) }
    }
}

/** Dialog listing roles to guess from (roles of the current game, as in the source app). */
fun Screen.rolePicker(title: String, roles: List<MafiaRole>, onPick: (MafiaRole) -> Unit) {
    lateinit var dialog: Dialog
    val cells = roles.map { r ->
        kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
            background = PanelDrawable(kit.density, PanelStyle.FLAT, 14f)
            setPadding(kit.dp(4), kit.dp(6), kit.dp(4), kit.dp(8))
            addView(seal(r.glyph, r.side, 44))
            addView(kit.text(r.faName, TextStyle.CAPTION, r.side.textColor(), Gravity.CENTER, 2))
            isClickable = true
            contentDescription = r.faName
            setOnClickListener { kit.tap(it); dialog.dismiss(); onPick(r) }
        }
    }
    val grid = kit.grid(3, cells, 6)
    val body = kit.vertical().apply {
        kit.searchFilter("جست‌وجوی نقش", roles.zip(cells).map { (r, v) -> v to r.faName + " " + r.defaultName }, minItems = 7)?.let { addView(it, kit.spaced(8)) }
        addView(grid)
    }
    dialog = kit.dialog(title, null, body, listOf(DialogAction("بستن")))
    dialog.show()
}

/** Role description dialog with its card, explanation and configured options. */
fun Screen.roleInfo(role: MafiaRole, options: Map<String, String> = emptyMap(), extraActions: List<DialogAction> = emptyList()) {
    val body = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        addView(roleCard(role), LinearLayout.LayoutParams(kit.dp(190), kit.dp((190 * RoleCardView.RATIO).toInt())))
        addView(kit.gap(8))
        addView(kit.badge(role.side.title, role.side.textColor()))
        addView(kit.gap(6))
        addView(kit.text("ویژگی های نقش : ", TextStyle.LABEL_BOLD, Royal.gold))
        addView(kit.text(MafiaRoleText.explain(role), TextStyle.BODY, Royal.ivory))
        val defs = MafiaRoleOptions.of(role)
        if (defs.isNotEmpty()) {
            addView(kit.gap(6))
            addView(kit.text("قابلیت های قابل تنظیم نقش", TextStyle.LABEL_BOLD, Royal.gold))
            defs.forEach { d ->
                addView(kit.horizontal().apply {
                    addView(kit.weight(kit.text(d.label, TextStyle.LABEL, Royal.muted)))
                    addView(kit.text(kit.n(options[d.label] ?: d.default), TextStyle.BODY_BOLD, Royal.ivory))
                })
            }
        }
    }
    kit.dialog(role.faName, null, body, extraActions + DialogAction("بستن")).show()
}

fun Screen.cardInfo(card: MafiaLastCard, extraActions: List<DialogAction> = emptyList()) {
    val body = kit.vertical(Gravity.CENTER_HORIZONTAL).apply {
        addView(lastCard(card), LinearLayout.LayoutParams(kit.dp(190), kit.dp((190 * RoleCardView.RATIO).toInt())))
        addView(kit.gap(8))
        addView(kit.text("توضیحات کارت", TextStyle.LABEL_BOLD, Royal.gold))
        addView(kit.text(card.explain, TextStyle.BODY, Royal.ivory))
    }
    kit.dialog(card.faName.replace("\n", " "), null, body, extraActions + DialogAction("بستن")).show()
}
