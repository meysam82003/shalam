package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object TransferPlatform {
    @JvmStatic fun available() = true

    @JvmStatic fun frames(text: String): List<String> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= GameTransfer.MAX_BYTES)
        val stream = ByteArrayOutputStream()
        GZIPOutputStream(stream).use { it.write(bytes) }
        val chunks = Base64.encodeToString(stream.toByteArray(), FLAGS).chunked(CHUNK_SIZE)
        if (chunks.size > MAX_FRAMES) return emptyList()
        val hash = GameTransfer.digest(bytes)
        return chunks.mapIndexed { index, part -> "$PREFIX:$hash:${index + 1}:${chunks.size}:$part" }
    }

    @JvmStatic fun bitmap(text: String): Bitmap = BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, 720, 720,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))

    @JvmStatic fun startScan(activity: Activity) {
        IntentIntegrator(activity)
            .setDesiredBarcodeFormats(listOf(IntentIntegrator.QR_CODE))
            .setPrompt("کد انتقال دیوان امتیاز را روبه‌روی دوربین بگیرید")
            .setOrientationLocked(true)
            .setBeepEnabled(false)
            .initiateScan()
    }

    @JvmStatic fun isScan(request: Int) = request == IntentIntegrator.REQUEST_CODE

    @JvmStatic fun result(request: Int, code: Int, intent: Intent?): String? =
        IntentIntegrator.parseActivityResult(request, code, intent)?.contents

    @JvmStatic @Suppress("DEPRECATION")
    fun receivedFile(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_SEND && intent.type == GameTransfer.MIME) {
            runCatching { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }.getOrNull()
                ?.takeIf { it.scheme == "content" }
        } else null

    private const val PREFIX = "DIVAN1"
    private const val FLAGS = Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
    private const val CHUNK_SIZE = 700
    private const val MAX_FRAMES = 180

    class Scan {
        private var hash = ""
        private val parts = HashMap<Int, String>()
        var total = 0
            private set
        val received: Int get() = parts.size

        fun add(text: String): String? {
            require(text.length <= CHUNK_SIZE + 88)
            val fields = text.split(':', limit = 5)
            require(fields.size == 5 && fields[0] == PREFIX && fields[1].matches(Regex("[0-9a-f]{64}")))
            val count = fields[3].toInt()
            val index = fields[2].toInt()
            require(count in 1..MAX_FRAMES && index in 1..count)
            val part = fields[4]
            require(part.isNotEmpty() && part.length <= CHUNK_SIZE && part.all {
                it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-'
            })
            if (parts.isEmpty()) {
                hash = fields[1]
                total = count
            }
            require(hash == fields[1] && count == total)
            require(parts[index] == null || parts[index] == part)
            parts[index] = part
            if (received != total) return null
            val encoded = (1..total).joinToString("") { parts.getValue(it) }
            val bytes = Base64.decode(encoded, FLAGS)
            val raw = GZIPInputStream(ByteArrayInputStream(bytes)).use(GameTransfer::read)
            require(GameTransfer.digest(raw) == hash)
            return raw.toString(Charsets.UTF_8)
        }
    }
}
