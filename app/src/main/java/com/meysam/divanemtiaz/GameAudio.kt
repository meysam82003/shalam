package com.meysam.divanemtiaz

import android.view.View
import org.json.JSONObject

/** Per-game user files. No bundled music or answer effects; the countdown tick is the sole fallback. */
class GameAudio(private val host: MainActivity) {
    private val player = MafiaAudio(host)
    private val ticks = GameSounds { true }
    private var scope: String? = null
    private fun key(game: String, cue: String) = "game_audio_${game}_$cue"
    fun choice(game: String, cue: String): SoundChoice? = host.repo.store.get(key(game, cue))?.let {
        runCatching { JSONObject(it).let { j -> SoundChoice(SoundSource.FILE, path = j.getString("path"), label = j.optString("label")) } }.getOrNull()
    }
    fun save(game: String, cue: String, choice: SoundChoice?) {
        host.repo.store.write(mapOf(key(game, cue) to choice?.let { JSONObject().put("path", it.path).put("label", it.label).toString() }))
        if (cue == "music" && scope == game) { scope = null; enter(game) }
    }
    fun enter(game: String?) {
        if (scope == game) return
        player.stopCue(); player.stopMusic(); scope = game
        if (game != null) player.startMusic(choice(game, "music"))
    }
    fun play(game: String, cue: String) {
        val selected = choice(game, cue)
        if (selected != null) player.play(selected)
        else if (cue == "tick") ticks.tick()
    }
    fun pause() { player.stopCue(); player.stopMusic(); scope = null }
    fun release() { pause(); player.release(); ticks.release() }
    val panto = Effects("panto")
    inner class Effects(private val game: String) {
        fun correct() = play(game, "correct")
        fun wrong() = play(game, "wrong")
        fun tick() = play(game, "tick")
        fun bell() = play(game, "end")
    }
}

class GameAudioSettingsScreen(host: MainActivity, private val game: String) : Screen(host) {
    override val audioScope: String get() = game
    override fun build(): View = scaffold("صداهای ${if (game == "panto") "پانتومیم" else "شلم"}", "فایل‌های دلخواه از گوشی شما") {
        val cues = if (game == "panto") listOf("music" to "موسیقی پس‌زمینه", "end" to "پایان وقت", "tick" to "۱۰ ثانیهٔ آخر", "correct" to "پاسخ درست", "wrong" to "پاسخ نادرست و خطا", "swap" to "تعویض کلمه")
        else listOf("music" to "موسیقی پس‌زمینه", "score" to "ثبت عدد و امتیاز")
        cues.forEach { (cue, title) ->
            addView(kit.panel(PanelStyle.FLAT).apply {
                addView(kit.text(title, TextStyle.BODY_BOLD, Royal.goldLight))
                addView(kit.text(host.gameAudio.choice(game, cue)?.label ?: if (cue == "tick") "تیک تایمر" else "بدون صدا", TextStyle.CAPTION, Royal.muted))
                addView(kit.grid(2, listOf(
                    kit.button("انتخاب فایل", ButtonKind.SECONDARY, RoyalIcon.PLUS, 42) {
                        host.pickFile(arrayOf("audio/*")) { uri ->
                            if (uri != null) {
                                val file = host.audio().importFile(uri, "${game}_$cue")
                                if (file == null) kit.toast("فایل خوانده نشد") else {
                                    host.gameAudio.save(game, cue, SoundChoice(SoundSource.FILE, path = file.absolutePath, label = uri.lastPathSegment?.substringAfterLast('/') ?: file.name))
                                    host.refresh()
                                }
                            }
                        }
                    },
                    kit.button("حذف صدا", ButtonKind.GHOST, RoyalIcon.TRASH, 42) { host.gameAudio.save(game, cue, null); host.refresh() }
                ), 6), kit.spaced(6))
            }, kit.spaced(8))
        }
    }
}
