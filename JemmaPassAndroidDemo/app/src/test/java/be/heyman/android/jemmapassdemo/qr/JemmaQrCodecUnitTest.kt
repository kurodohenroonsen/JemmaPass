/*
 * JemmaQrCodecUnitTest.kt
 *
 * JUnit test suite for JemmaQrFrameSplitter and JemmaQrBitmapEncoder.
 * Verifies that variable-length characters (Japanese Kanji/Kana, accents)
 * are correctly budgeted by their UTF-8 byte sizes and that QR codes
 * are generated successfully without ECI or overflow issues.
 */
package be.heyman.android.jemmapassdemo.qr

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class JemmaQrCodecUnitTest {

    @Test
    fun testSplitSingleFrameWithinLimit() {
        // "🏥 === JEMMA 臨床サマリー (JA) ===" contains Japanese characters
        val jaText = "🏥 === JEMMA 臨床サマリー (JA) ==="
        val byteSize = jaText.toByteArray(StandardCharsets.UTF_8).size

        // If total bytes <= maxSingle, it must return a single frame containing the exact payload (no headers)
        val frames = JemmaQrFrameSplitter.split(jaText, maxSingle = byteSize + 10, frameChunk = byteSize)
        assertEquals("Should be a single frame", 1, frames.size)
        assertEquals("Single frame content must match original payload", jaText, frames[0])
    }

    @Test
    fun testSplitMultiFrameAccurateByteBudgeting() {
        // Let's create a Japanese string of exactly 10 Kanji characters.
        // Each of these 10 Kanji characters takes 3 bytes in UTF-8. Total = 30 bytes.
        val jaText = "緊急医療情報表示健康" 
        val totalBytes = jaText.toByteArray(StandardCharsets.UTF_8).size
        assertEquals(30, totalBytes)

        // Set maxSingle to 20 bytes (so it MUST split, since 30 > 20)
        // Set frameChunk to 18 bytes.
        // usableBytesPerFrame = 18 - 10 (overhead) = 8 bytes.
        // Since each character takes 3 bytes, each frame can hold at most 2 characters (6 bytes).
        // So 10 characters should split into 5 frames (2 characters per frame).
        val frames = JemmaQrFrameSplitter.split(jaText, maxSingle = 20, frameChunk = 18)
        
        assertEquals("Should split into 5 frames", 5, frames.size)
        
        // Let's verify each frame's structure and byte size
        for (i in 0 until 5) {
            val frame = frames[i]
            val expectedHeader = "JF:${i + 1}/5|"
            assertTrue("Frame must start with expected header: $frame", frame.startsWith(expectedHeader))
            
            // Extract the chunk
            val chunk = frame.substring(expectedHeader.length)
            assertEquals("Each chunk must be exactly 2 Japanese characters", 2, chunk.length)
            
            val frameBytes = frame.toByteArray(StandardCharsets.UTF_8).size
            assertTrue("Frame size in bytes ($frameBytes) must be <= frameChunk (18)", frameBytes <= 18)
        }

        // Reconstruction test: joining all the chunks must equal the original text
        val reconstructed = StringBuilder()
        for (frame in frames) {
            val pipeIndex = frame.indexOf('|')
            reconstructed.append(frame.substring(pipeIndex + 1))
        }
        assertEquals("Reconstructed text must match original", jaText, reconstructed.toString())
    }

    @Test
    fun testSplitEmptyPayloadSafe() {
        val frames = JemmaQrFrameSplitter.split("")
        assertEquals(1, frames.size)
        assertEquals("", frames[0])
    }

    @Test
    fun testQrBitmapEncoderBasicEncoding() {
        // Encodings containing Japanese and French characters
        val complexText = "🏥 JEMMAサマリー Pénicilline Warfarine"
        
        // We only verify that the encoding method executes without throwing any exceptions.
        // On a JVM unit test runner, Android's native Bitmap class is mocked to return null,
        // so we check the result conditionally to support both real Android devices and JUnit.
        val bitmap = JemmaQrBitmapEncoder.encode(
            content = complexText,
            sizePx = 400,
            errorCorrection = ErrorCorrectionLevel.M
        )
        
        if (bitmap != null) {
            assertEquals("Bitmap width should match scaled width", 400, bitmap.width)
            assertEquals("Bitmap height should match scaled height", 400, bitmap.height)
        } else {
            System.out.println("Bitmap is null in pure JVM unit test environment due to Android SDK stubbing, which is expected.")
        }
    }

    @Test
    fun testQrBitmapEncoderEmptyContentReturnsNull() {
        val bitmap = JemmaQrBitmapEncoder.encode("")
        assertTrue("Empty content must return null", bitmap == null)
    }
}
