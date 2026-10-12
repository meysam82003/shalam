package com.meysam.divanemtiaz

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Short game sounds made by the device's tone generator (no sound files): right answer, wrong answer, the last
 * seconds' tick, the end-of-time bell and a repeating alarm that can be stopped.
 */
class GameSounds(private val enabled: () -> Boolean) {
    private var tone: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var alarmOn = false

    private fun gen(): ToneGenerator? = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull().also { tone = it }

    private fun play(type: Int, ms: Int) {
        if (!enabled()) return
        runCatching { gen()?.startTone(type, ms) }
    }

    fun correct() = play(ToneGenerator.TONE_PROP_ACK, 220)
    fun wrong() = play(ToneGenerator.TONE_PROP_NACK, 300)
    fun tick() = play(ToneGenerator.TONE_PROP_BEEP, 80)
    fun bell() = play(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)

    private val alarmLoop = object : Runnable {
        override fun run() {
            if (!alarmOn) return
            play(ToneGenerator.TONE_CDMA_HIGH_PBX_SLS, 900)
            handler.postDelayed(this, 1300)
        }
    }

    val alarmPlaying: Boolean get() = alarmOn

    /** Rings until [stopAlarm] (end of a timed game). */
    fun startAlarm() {
        if (alarmOn) return
        alarmOn = true
        handler.post(alarmLoop)
    }

    fun stopAlarm() {
        alarmOn = false
        handler.removeCallbacks(alarmLoop)
        runCatching { tone?.stopTone() }
    }

    fun release() {
        stopAlarm()
        runCatching { tone?.release() }
        tone = null
    }
}

/** A countdown on the main thread that reports every tenth of a second (pauses keep the remaining time). */
class Countdown(private var remainingMs: Long, private val onTick: (Long) -> Unit, private val onEnd: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var last = 0L
    var running = false
        private set
    val remaining: Long get() = remainingMs

    private val step = object : Runnable {
        override fun run() {
            if (!running) return
            val now = android.os.SystemClock.uptimeMillis()
            remainingMs = (remainingMs - (now - last)).coerceAtLeast(0)
            last = now
            onTick(remainingMs)
            if (remainingMs <= 0) { running = false; onEnd() } else handler.postDelayed(this, 100)
        }
    }

    fun start() {
        if (running || remainingMs <= 0) return
        running = true
        last = android.os.SystemClock.uptimeMillis()
        handler.postDelayed(step, 100)
    }

    fun pause() {
        if (!running) return
        val now = android.os.SystemClock.uptimeMillis()
        remainingMs = (remainingMs - (now - last)).coerceAtLeast(0)
        running = false
        handler.removeCallbacks(step)
    }

    fun reset(ms: Long) { pause(); remainingMs = ms }
}

fun clockText(ms: Long): String {
    val s = ((ms + 999) / 1000).toInt()
    return PersianText.digits("%02d:%02d".format(s / 60, s % 60))
}
