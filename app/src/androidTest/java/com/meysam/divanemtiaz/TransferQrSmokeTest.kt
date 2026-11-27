package com.meysam.divanemtiaz

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class TransferQrSmokeTest {
    @Test fun aSingleQrRecoversTheEntireEncryptedGameAndRejectsAChangedKey() {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = Random(567)
        val text = """{"caption":"نقش خصوصی","history":"${List(14000) { alphabet[random.nextInt(alphabet.length)] }.joinToString("")}"}"""
        assertTrue(TransferPlatform.frames(text).size > 6)
        val id = "1234567890abcdef1234567890abcdef"
        var uploaded: JSONObject? = null
        Api.transport = { method, route, body, _ ->
            val data = if (route != "v1/game-transfer") JSONObject()
            else if (method == "POST") {
                uploaded = body
                JSONObject().put("id", id)
            } else JSONObject().put("payload", uploaded?.optString("payload"))
            JSONObject().put("ok", true).put("data", data)
        }
        try {
            val creating = CountDownLatch(1)
            var link: String? = null
            TransferLink.create(text) { link = it; creating.countDown() }
            assertTrue(creating.await(8, TimeUnit.SECONDS))
            val scanned = TransferLink.read(Uri.parse(decodeBitmap(requireNotNull(link))))!!
            assertEquals(id, scanned.id)
            assertFalse(uploaded!!.toString().contains("نقش خصوصی"))
            assertFalse(uploaded!!.toString().contains(scanned.key))
            assertEquals(1, uploaded!!.length())
            assertEquals(scanned, TransferLink.read(Uri.parse("divan://transfer/#${scanned.id}.${scanned.key}")))
            assertNull(TransferLink.read(Uri.parse(link!!.replace("divan-emtiaz.ir", "example.com"))))

            val fetching = CountDownLatch(2)
            var decoded: String? = null
            var corrupted: String? = "pending"
            TransferLink.fetch(scanned) { decoded = it; fetching.countDown() }
            val wrongKey = (if (scanned.key.first() == 'a') 'b' else 'a') + scanned.key.substring(1)
            TransferLink.fetch(scanned.copy(key = wrongKey)) { corrupted = it; fetching.countDown() }
            assertTrue(fetching.await(8, TimeUnit.SECONDS))
            assertEquals(text, decoded)
            assertNull(corrupted)
        } finally {
            Api.transport = null
        }
    }

    @Test fun qrFramesRoundTripOutOfOrderAndRejectMixing() {
        val random = Random(566)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val payload = """{"caption":"انتقال بازی","history":"${(1..1100).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")}"}"""
        val frames = TransferPlatform.frames(payload)
        assertTrue(frames.size > 1)
        val scanner = TransferPlatform.Scan()
        val first = decodeBitmap(frames.last())
        assertNull(scanner.add(first))
        assertNull(scanner.add(first))
        assertEquals(1, scanner.received)
        try {
            scanner.add(TransferPlatform.frames("different").single())
            fail("A QR belonging to another transfer was accepted")
        } catch (_: IllegalArgumentException) {
            assertEquals(1, scanner.received)
        }
        var result: String? = null
        for (frame in frames.dropLast(1).asReversed()) result = scanner.add(decodeBitmap(frame))
        assertEquals(payload, result)
    }

    private fun decodeBitmap(frame: String): String {
        val bitmap = TransferPlatform.bitmap(frame)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }
}
