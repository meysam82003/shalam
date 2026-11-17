package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
import android.view.View
import java.io.File

/** Native GIF animation and static image rendering, including Android 5–8. */
@Suppress("DEPRECATION")
class AdImageView(context: Context, file: File) : View(context) {
    private val movie = runCatching { file.inputStream().use { Movie.decodeStream(it) } }.getOrNull()
    private val bitmap = if (movie == null) BitmapFactory.decodeFile(file.path) else null
    private val started = SystemClock.uptimeMillis()
    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = movie?.width() ?: bitmap?.width ?: return
        val h = movie?.height() ?: bitmap?.height ?: return
        if (w <= 0 || h <= 0) return
        val scale = minOf(width.toFloat() / w, height.toFloat() / h)
        val save = canvas.save()
        canvas.translate((width - w * scale) / 2, (height - h * scale) / 2)
        canvas.scale(scale, scale)
        if (movie != null) {
            movie.setTime(((SystemClock.uptimeMillis() - started) % (movie.duration().takeIf { it > 0 } ?: 1000)).toInt())
            movie.draw(canvas, 0f, 0f)
            if (isShown) postInvalidateDelayed(40)
        } else bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.restoreToCount(save)
    }
}
