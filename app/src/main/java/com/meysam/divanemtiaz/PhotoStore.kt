package com.meysam.divanemtiaz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import java.io.File

/**
 * Pictures chosen from the gallery for people, teams, leagues and groups. A picture is stored as a small
 * square JPEG in the app's private storage and referenced by an avatar number from [BASE] up, so every
 * place that already keeps an avatar (sessions, roster, leagues, mafia players, pantomime teams) carries
 * pictures without any change to its data. A missing file falls back to an emblem.
 */
object PhotoStore {
    const val BASE = 1_000_000
    private const val SIZE = 384
    private var dir: File? = null
    private val cache = android.util.LruCache<Int, Bitmap>(24)

    fun init(context: Context) {
        if (dir == null) dir = File(context.applicationContext.filesDir, "photos").apply { mkdirs() }
    }

    fun isPhoto(avatar: Int): Boolean = avatar >= BASE

    private fun file(avatar: Int): File? = dir?.let { File(it, "p${avatar - BASE}.jpg") }

    fun exists(avatar: Int): Boolean = isPhoto(avatar) && file(avatar)?.exists() == true

    /** The picture of [avatar], or null when it is an emblem or the file is gone. */
    fun bitmap(avatar: Int): Bitmap? {
        if (!isPhoto(avatar)) return null
        cache.get(avatar)?.takeIf { !it.isRecycled }?.let { return it }
        val f = file(avatar)?.takeIf { it.exists() } ?: return null
        val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() ?: return null
        cache.put(avatar, bmp)
        return bmp
    }

    /** Copies the chosen image into a square picture; returns its avatar number, or null on failure. */
    fun save(context: Context, uri: Uri): Int? = runCatching {
        init(context)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rotated = rotate(raw, orientation(context, uri))
        val square = crop(rotated)
        store(square)
    }.getOrNull()

    /** Writes [square] as the next picture and returns its avatar number. */
    fun store(square: Bitmap): Int? {
        val folder = dir ?: return null
        val next = (folder.listFiles()?.mapNotNull { it.name.removePrefix("p").removeSuffix(".jpg").toIntOrNull() }?.maxOrNull() ?: 0) + 1
        File(folder, "p$next.jpg").outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cache.put(BASE + next, square)
        return BASE + next
    }

    private fun crop(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            val k = SIZE.toFloat() / side
            postTranslate(-(src.width - side) / 2f, -(src.height - side) / 2f)
            postScale(k, k)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun orientation(context: Context, uri: Uri): Int = runCatching {
        if (android.os.Build.VERSION.SDK_INT < 24) return 0
        context.contentResolver.openInputStream(uri)?.use { s ->
            when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun rotate(src: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    /** Draws the picture in a circle of radius [r]. */
    fun drawCircle(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, r: Float, paint: Paint) {
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val k = (r * 2f) / minOf(bitmap.width, bitmap.height)
        shader.setLocalMatrix(Matrix().apply { postScale(k, k); postTranslate(cx - bitmap.width * k / 2f, cy - bitmap.height * k / 2f) })
        paint.style = Paint.Style.FILL
        paint.shader = shader
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    /** Every stored picture as base64, for the backup file. */
    fun export(): Map<Int, String> = dir?.listFiles()?.mapNotNull { f ->
        val n = f.name.removePrefix("p").removeSuffix(".jpg").toIntOrNull() ?: return@mapNotNull null
        (BASE + n) to Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }?.toMap() ?: emptyMap()

    /** Restores pictures of a backup (existing files with the same number are kept). */
    fun import(photos: Map<Int, String>) {
        val folder = dir ?: return
        photos.forEach { (avatar, data) ->
            if (!isPhoto(avatar)) return@forEach
            val f = File(folder, "p${avatar - BASE}.jpg")
            if (!f.exists()) runCatching { f.writeBytes(Base64.decode(data, Base64.DEFAULT)) }
        }
    }
}
