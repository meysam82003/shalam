package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object TransferPlatform {
    private val cryptoWorker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()

    @JvmStatic fun seal(text: String, done: (String?, String?) -> Unit) {
        cryptoWorker.execute {
            val result = runCatching {
                val bytes = text.toByteArray(Charsets.UTF_8)
                require(bytes.size <= GameTransfer.MAX_BYTES)
                val zipped = ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(bytes) } }.toByteArray()
                val key = ByteArray(32).also(random::nextBytes)
                val nonce = ByteArray(12).also(random::nextBytes)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                B64.encode(key, url = true) to B64.encode(nonce + cipher.doFinal(zipped), url = true)
            }.getOrNull()
            main.post { done(result?.first, result?.second) }
        }
    }

    @JvmStatic fun unseal(key: String, payload: String, done: (String?) -> Unit) {
        cryptoWorker.execute {
            val result = runCatching {
                require(payload.length in 40..5_600_000)
                val box = B64.decode(payload)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(B64.decode(key), "AES"), GCMParameterSpec(128, box.copyOfRange(0, 12)))
                val zip = cipher.doFinal(box, 12, box.size - 12)
                GZIPInputStream(ByteArrayInputStream(zip)).use(GameTransfer::read).toString(Charsets.UTF_8)
            }.getOrNull()
            main.post { done(result) }
        }
    }

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
    fun receivedFile(intent: Intent?): Uri? {
        if (intent?.type != GameTransfer.MIME) return null
        val file = when (intent.action) {
            Intent.ACTION_SEND -> runCatching { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }.getOrNull()
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        return file?.takeIf { it.scheme == "content" }
    }

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
