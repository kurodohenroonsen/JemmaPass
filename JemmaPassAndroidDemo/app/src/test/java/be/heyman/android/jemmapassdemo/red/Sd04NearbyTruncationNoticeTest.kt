/*
 * RED TEST (wave 1) — SD-04, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * When the medication list does not fit in one broadcast segment, the rescuer must be able
 * to see that the list is incomplete.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.sos.JemmaNearbyEndpointCodec
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd04NearbyTruncationNoticeTest {

    private val fifty: List<String> = (1..50).map { String.format(Locale.ROOT, "B01AA%02d", it) }

    private fun segment(codes: List<String>): String = JemmaNearbyEndpointCodec.encodeVictimCodes(
        "ab12", JemmaNearbyEndpointCodec.VICTIM_CHUNK_MEDS, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, 'M', codes,
    )

    @Test
    fun `SD-04 UC-SOS-011 a medication list that was cut does not look like a complete list`() {
        val complete = segment(fifty.take(14))
        val cut = segment(fifty)
        assertTrue(
            "the segment of the 50-medication list is ${JemmaNearbyEndpointCodec.byteLengthOf(cut)} bytes, over the 131-byte limit",
            JemmaNearbyEndpointCodec.byteLengthOf(cut) <= JemmaNearbyEndpointCodec.MAX_ENDPOINT_NAME_LEN,
        )
        assertNotEquals(
            "A victim with 50 medications and a victim with 14 medications broadcast exactly the same segment : " +
                "36 medications were left out and nothing tells the rescuer. A cut list must carry a visible sign " +
                "that it is incomplete (the dropped count is only written to the log today).",
            complete, cut,
        )
    }

    @Test
    fun `SD-04 UC-SOS-011 a complete list is still received as it was sent`() {
        // Guard for the fix : marking cut lists must not damage a list that fits.
        val sent = fifty.take(14)
        val decoded = JemmaNearbyEndpointCodec.decode(segment(sent)) as JemmaNearbyEndpointCodec.Decoded.VictimCodes
        assertEquals("A list that fits in the segment must reach the rescuer unchanged.", sent, decoded.codes)
        // … and the two lists must be told apart on the receiving side too.
        val cut = JemmaNearbyEndpointCodec.decode(segment(fifty))
        assertNotEquals(
            "After decoding, the cut list of 50 medications is identical to the complete list of 14 : " +
                "the rescuer's phone has no way to show that medications are missing.",
            decoded, cut,
        )
    }
}
