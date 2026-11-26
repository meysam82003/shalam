package com.meysam.divanemtiaz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class TransferQrSmokeTest {
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
