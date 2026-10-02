/*
 * QrFrameAssemblerTest.kt — multi-frame QR round trip (UC-QR-FRAME, QA cases
 * UC-QRF-002 / 004): what JemmaQrFrameSplitter cuts, JemmaQrFrameAssembler rebuilds,
 * whatever the scan order, with duplicates, and never from an incomplete set.
 */
package be.heyman.android.jemmapassdemo.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class QrFrameAssemblerTest {

    /** ~4000 bytes, ASCII — shape of a big `_j2:` compact payload. */
    private val compact = "_j2:" + (1..999).joinToString("") { "%04d".format(it) }

    /** Multi-byte payload: kanji (3 bytes), emoji (4 bytes, surrogate pair), CRLF, spaces. */
    private val japanese = (1..120).joinToString("\r\n") { "  ▪️ 緊急医療情報 $it 🏥 ワルファリン 5mg " }

    private fun split(payload: String, maxSingle: Int = 1800, chunk: Int = 1400): List<String> =
        JemmaQrFrameSplitter.split(payload, maxSingle, chunk)

    /** UC-QR-FRAME-01 (UC-QRF-002) — split then join in order gives the payload back. */
    @Test
    fun ucQrFrame01_roundTripInOrder() {
        val frames = split(compact)
        assertTrue(frames.size >= 3)
        assertEquals(compact, JemmaQrFrameAssembler.join(frames))

        val assembler = JemmaQrFrameAssembler()
        frames.dropLast(1).forEachIndexed { i, f ->
            val r = assembler.offer(f) as JemmaQrFrameAssembler.Result.Progress
            assertEquals(i + 1, r.received)
            assertEquals(frames.size, r.total)
            assertFalse(r.duplicate)
        }
        val done = assembler.offer(frames.last()) as JemmaQrFrameAssembler.Result.Complete
        assertEquals(compact, done.payload)
        assertEquals(frames.size, done.total)
        assertEquals(0, assembler.expectedTotal) // ready for another set
    }

    /** UC-QR-FRAME-02 — any scan order. */
    @Test
    fun ucQrFrame02_shuffledFrames() {
        val frames = split(japanese, maxSingle = 900, chunk = 700)
        assertTrue(frames.size >= 5)
        for (seed in 1L..20L) {
            val shuffled = frames.shuffled(Random(seed))
            assertEquals(japanese, JemmaQrFrameAssembler.join(shuffled))

            val assembler = JemmaQrFrameAssembler()
            var payload: String? = null
            for (f in shuffled) {
                val r = assembler.offer(f)
                if (r is JemmaQrFrameAssembler.Result.Complete) payload = r.payload
            }
            assertEquals(japanese, payload)
        }
    }

    /** UC-QR-FRAME-03 — a looping slideshow shows each frame many times. */
    @Test
    fun ucQrFrame03_duplicatedFrames() {
        val frames = split(compact)
        val seen = frames + frames.reversed() + frames
        assertEquals(compact, JemmaQrFrameAssembler.join(seen))

        val assembler = JemmaQrFrameAssembler()
        assertTrue(assembler.offer(frames[1]) is JemmaQrFrameAssembler.Result.Progress)
        val again = assembler.offer(frames[1]) as JemmaQrFrameAssembler.Result.Progress
        assertTrue(again.duplicate)
        assertEquals(1, again.received)
        assertFalse(again.restarted)
    }

    /** UC-QR-FRAME-04 — a missing frame is reported, never papered over. */
    @Test
    fun ucQrFrame04_missingFrameIsDetected() {
        val frames = split(compact)
        for (skip in frames.indices) {
            val partial = frames.filterIndexed { i, _ -> i != skip }
            assertNull("frame ${skip + 1} missing", JemmaQrFrameAssembler.join(partial))

            val assembler = JemmaQrFrameAssembler()
            var last: JemmaQrFrameAssembler.Result = JemmaQrFrameAssembler.Result.NotAFrame
            for (f in partial.shuffled(Random(skip.toLong()))) last = assembler.offer(f)
            val progress = last as JemmaQrFrameAssembler.Result.Progress
            assertEquals(listOf(skip + 1), progress.missing)
            assertEquals(listOf(skip + 1), assembler.missing())
        }
        assertNull(JemmaQrFrameAssembler.join(emptyList()))
    }

    /** UC-QR-FRAME-05 (UC-QRF-004) — frames respect the byte budget and never split a code point. */
    @Test
    fun ucQrFrame05_japaneseFramesAreBudgetedInBytes() {
        val frames = split(japanese, maxSingle = 900, chunk = 700)
        for (f in frames) {
            assertTrue(f.toByteArray(Charsets.UTF_8).size <= 700)
            assertEquals(f, String(f.toByteArray(Charsets.UTF_8), Charsets.UTF_8)) // no orphan surrogate
        }
        assertEquals(japanese, JemmaQrFrameAssembler.join(frames))
    }

    /** UC-QR-FRAME-06 — single-frame payloads and foreign QR codes are not frames. */
    @Test
    fun ucQrFrame06_nonFramesAreLeftAlone() {
        val single = split("_j2:abc")
        assertEquals(listOf("_j2:abc"), single)
        val assembler = JemmaQrFrameAssembler()
        for (raw in listOf(null, "", "_j2:abc", "{\"_j\":\"1.2\"}", "JF:", "JF:1/|x", "JF:0/3|x", "JF:4/3|x",
            "JF:a/3|x", "JF:1/3", "JF:-1/3|x", "JF:1/99999|x", " JF:1/3|x", "https://example.org")) {
            assertTrue("'$raw'", assembler.offer(raw) is JemmaQrFrameAssembler.Result.NotAFrame)
            assertFalse(JemmaQrFrameAssembler.isFrame(raw))
        }
        assertNull(JemmaQrFrameAssembler.join(listOf("JF:1/2|a", "hello")))
        // data kept verbatim: separators and whitespace inside the chunk survive
        assertEquals(JemmaQrFrameAssembler.Frame(2, 3, " a|b/c \r\n"), JemmaQrFrameAssembler.parse("JF:2/3| a|b/c \r\n"))
        assertEquals("x", (assembler.offer("JF:1/1|x") as JemmaQrFrameAssembler.Result.Complete).payload)
    }

    /** UC-QR-FRAME-07 — pointing the camera at another QR set restarts the collection. */
    @Test
    fun ucQrFrame07_framesOfAnotherSetRestartTheCollection() {
        val a = split(compact)                                 // N frames
        val b = split(japanese, maxSingle = 900, chunk = 700)  // another total
        assertTrue(a.size != b.size)

        val assembler = JemmaQrFrameAssembler()
        assembler.offer(a[0])
        assembler.offer(a[1])
        val r = assembler.offer(b[0]) as JemmaQrFrameAssembler.Result.Progress
        assertTrue(r.restarted)
        assertEquals(1, r.received)
        assertEquals(b.size, r.total)
        var payload: String? = null
        for (f in b.drop(1)) (assembler.offer(f) as? JemmaQrFrameAssembler.Result.Complete)?.let { payload = it.payload }
        assertEquals(japanese, payload)

        // same total, same index, other content → also a new set
        assembler.offer("JF:1/2|AAA")
        val other = assembler.offer("JF:1/2|BBB") as JemmaQrFrameAssembler.Result.Progress
        assertTrue(other.restarted)
        assertEquals("BBBccc", (assembler.offer("JF:2/2|ccc") as JemmaQrFrameAssembler.Result.Complete).payload)

        // one-shot join refuses mixed sets
        assertNull(JemmaQrFrameAssembler.join(a + b))
        assertNull(JemmaQrFrameAssembler.join(listOf("JF:1/2|AAA", "JF:1/2|BBB", "JF:2/2|ccc")))
    }
}
