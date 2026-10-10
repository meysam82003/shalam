package com.meysam.divanemtiaz

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

/** Where a narrator cue's sound comes from. */
object SoundSource {
    const val NONE = 0
    const val BUILTIN = 1
    /** A file the narrator picked from the phone or recorded; it is copied into the app's own storage. */
    const val FILE = 2
}

data class SoundChoice(val source: Int = SoundSource.NONE, val builtin: String = "", val path: String = "", val label: String = "") {
    val isSet: Boolean get() = source != SoundSource.NONE
}

/** The built-in sounds shipped in res/raw. */
object MafiaBuiltinSounds {
    val all: List<Pair<String, String>> = listOf(
        "mafia_night_sound" to "صدای شب",
        "mafia_morning_sound" to "صدای صبح",
        "mafia_heart_sound" to "ضربان قلب",
        "mafia_shot_sound" to "شلیک",
        "mafia_joker_laugh" to "خندهٔ جوکر",
        "mafia_typing_sound" to "تایپ گزارش",
        "mafia_end_timer" to "پایان زمان",
        "mafia_timer_one_tik" to "تیک ساعت",
        "mafia_horror_music" to "موسیقی شب"
    )

    fun title(name: String): String = all.firstOrNull { it.first == name }?.second ?: name
}

/**
 * Plays narrator cues and the optional night music, and records the narrator's own voice for a cue.
 * Every call is safe when a sound is missing: the narrator can always read the text instead.
 */
class MafiaAudio(private val context: Context) {
    private var cue: MediaPlayer? = null
    private var music: MediaPlayer? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    var volume: Float = 1f
    var musicVolume: Float = 0.35f

    val soundsDir: File get() = File(context.filesDir, "mafia_sounds").apply { mkdirs() }

    private fun create(choice: SoundChoice): MediaPlayer? = runCatching {
        when (choice.source) {
            SoundSource.BUILTIN -> {
                val id = context.resources.getIdentifier(choice.builtin, "raw", context.packageName)
                if (id == 0) null else MediaPlayer.create(context, id)
            }
            SoundSource.FILE -> {
                val file = File(choice.path)
                if (!file.exists()) null else MediaPlayer.create(context, Uri.fromFile(file))
            }
            else -> null
        }
    }.getOrNull()

    /** Plays one cue (stopping the previous one). Returns false when nothing could be played. */
    fun play(choice: SoundChoice?, onDone: (() -> Unit)? = null): Boolean {
        stopCue()
        if (choice == null || !choice.isSet) return false
        val player = create(choice) ?: return false
        cue = player
        player.setVolume(volume, volume)
        player.setOnCompletionListener {
            if (cue === it) cue = null
            it.release()
            onDone?.invoke()
        }
        runCatching { player.start() }.onFailure { player.release(); cue = null; return false }
        return true
    }

    fun stopCue() {
        cue?.let { runCatching { it.stop() }; it.release() }
        cue = null
    }

    val isPlaying: Boolean get() = runCatching { cue?.isPlaying == true }.getOrDefault(false)

    /** Looping background music for the night; [choice] NONE stops it. */
    fun startMusic(choice: SoundChoice?) {
        stopMusic()
        if (choice == null || !choice.isSet) return
        val player = create(choice) ?: return
        player.isLooping = true
        player.setVolume(musicVolume, musicVolume)
        runCatching { player.start() }.onFailure { player.release(); return }
        music = player
    }

    fun stopMusic() {
        music?.let { runCatching { it.stop() }; it.release() }
        music = null
    }

    val musicPlaying: Boolean get() = music != null

    /** Starts recording the narrator's voice for [key]; returns false if the microphone is not available. */
    fun startRecording(key: String): Boolean {
        stopRecording(discard = true)
        val file = File(soundsDir, "rec_${key}_${System.currentTimeMillis()}.m4a")
        return runCatching {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordingFile = file
            true
        }.getOrElse { recorder = null; recordingFile = null; false }
    }

    val isRecording: Boolean get() = recorder != null

    /** Stops recording; returns the recorded file unless [discard]. */
    fun stopRecording(discard: Boolean = false): File? {
        val r = recorder ?: return null
        val file = recordingFile
        recorder = null
        recordingFile = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (discard || !ok || file == null || file.length() == 0L) {
            file?.delete()
            return null
        }
        return file
    }

    /** Copies a picked audio file into the app so it keeps working after the original moves. */
    fun importFile(uri: Uri, key: String): File? = runCatching {
        val name = "file_${key}_${System.currentTimeMillis()}"
        val target = File(soundsDir, name)
        context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        if (target.length() == 0L) { target.delete(); null } else target
    }.getOrNull()

    fun release() {
        stopCue()
        stopMusic()
        stopRecording(discard = true)
    }

    companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
    }
}
