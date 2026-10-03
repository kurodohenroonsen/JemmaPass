/*
 * RED TEST (KB update, PROTOCOL 9.2 — defect SD-25) — not to be edited by the implementer.
 * Today a knowledge base missing 5 % of its bytes is accepted as complete. The verdict of a download is a pure
 * function : only COMPLETE lets the app open the file as a trusted medical base.
 */
package be.heyman.android.jemmapassdemo.kbupdate

import be.heyman.android.jemmapassdemo.downloads.DownloadIntegrity.downloadVerdict
import be.heyman.android.jemmapassdemo.downloads.DownloadVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DownloadIntegrityTest {

    private val SIZE = 3_360_727_040L
    private val SHA = "237d899f9e81e6d22af01bc6969798d1495131f1f1d4caa0491d80ac6a06014c"
    private val OTHER = "08a5d4b48454ea6c85e081bd1c5b40aeee5bad48e1dd3221c6e1cd1cfb1f223e"

    @Test
    fun `UC-UPD-001 exact size and matching fingerprint is complete`() {
        assertEquals(DownloadVerdict.COMPLETE, downloadVerdict(SIZE, SIZE, SHA, SHA))
    }

    @Test
    fun `UC-UPD-002 a file missing five percent is truncated, whatever the fingerprint says`() {
        val ninetyFive = (SIZE * 0.95).toLong()
        assertEquals(DownloadVerdict.TRUNCATED, downloadVerdict(ninetyFive, SIZE, null, SHA))
        assertEquals(DownloadVerdict.TRUNCATED, downloadVerdict(ninetyFive, SIZE, SHA, SHA))
        assertEquals("one missing byte is enough", DownloadVerdict.TRUNCATED, downloadVerdict(SIZE - 1, SIZE, SHA, SHA))
    }

    @Test
    fun `UC-UPD-003 an empty or absent file is truncated`() {
        assertEquals(DownloadVerdict.TRUNCATED, downloadVerdict(0L, SIZE, null, SHA))
    }

    @Test
    fun `UC-UPD-004 the right size with another fingerprint is corrupt`() {
        assertEquals(DownloadVerdict.CORRUPT, downloadVerdict(SIZE, SIZE, OTHER, SHA))
    }

    @Test
    fun `UC-UPD-005 a file larger than announced is corrupt`() {
        assertEquals(DownloadVerdict.CORRUPT, downloadVerdict(SIZE + 4096, SIZE, SHA, SHA))
    }

    @Test
    fun `UC-UPD-006 the right size without an expected fingerprint is unverified, never complete`() {
        assertEquals(DownloadVerdict.UNVERIFIED, downloadVerdict(SIZE, SIZE, SHA, null))
        assertEquals(DownloadVerdict.UNVERIFIED, downloadVerdict(SIZE, SIZE, SHA, "   "))
    }

    @Test
    fun `UC-UPD-007 the right size whose fingerprint was not computed is unverified`() {
        assertEquals(DownloadVerdict.UNVERIFIED, downloadVerdict(SIZE, SIZE, null, SHA))
        assertEquals(DownloadVerdict.UNVERIFIED, downloadVerdict(SIZE, SIZE, "", SHA))
    }

    @Test
    fun `UC-UPD-008 fingerprints are compared without case and without surrounding spaces`() {
        assertEquals(DownloadVerdict.COMPLETE, downloadVerdict(SIZE, SIZE, SHA.uppercase(), " $SHA\n"))
    }

    @Test
    fun `UC-UPD-009 an unknown expected size never gives complete`() {
        assertNotEquals(DownloadVerdict.COMPLETE, downloadVerdict(SIZE, 0L, SHA, SHA))
        assertNotEquals(DownloadVerdict.COMPLETE, downloadVerdict(SIZE, -1L, SHA, SHA))
    }

    @Test
    fun `UC-UPD-010 a fingerprint that is not 64 hexadecimal characters never gives complete`() {
        assertNotEquals(DownloadVerdict.COMPLETE, downloadVerdict(SIZE, SIZE, "abc", "abc"))
        assertNotEquals(DownloadVerdict.COMPLETE, downloadVerdict(SIZE, SIZE, "z".repeat(64), "z".repeat(64)))
    }
}
