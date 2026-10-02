/*
 * RED TEST (wave 1) — SD-02, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * A clinical code broadcast to nearby rescuers arrives whole, or does not arrive : never a
 * shortened or split code that names something else.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.sos.JemmaNearbyEndpointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd02NearbyCodeIntegrityTest {

    /** What the rescuer's phone decodes from the segment the victim's phone broadcasts. */
    private fun received(chunkIdx: Int, marker: Char, codes: List<String>): List<String> {
        val segment = JemmaNearbyEndpointCodec.encodeVictimCodes(
            "ab12", chunkIdx, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, marker, codes,
        )
        assertTrue(
            "the segment is ${JemmaNearbyEndpointCodec.byteLengthOf(segment)} bytes, over the 131-byte limit of Nearby Connections",
            JemmaNearbyEndpointCodec.byteLengthOf(segment) <= JemmaNearbyEndpointCodec.MAX_ENDPOINT_NAME_LEN,
        )
        val decoded = JemmaNearbyEndpointCodec.decode(segment)
        assertNotNull("the segment '$segment' could not be decoded", decoded)
        assertTrue("the segment '$segment' is not a victim codes segment", decoded is JemmaNearbyEndpointCodec.Decoded.VictimCodes)
        return (decoded as JemmaNearbyEndpointCodec.Decoded.VictimCodes).codes
    }

    @Test
    fun `SD-02 UC-SOS-012 a vaccine code longer than 10 characters is never shortened`() {
        // Haru's seeded vaccines : SNOMED national-extension codes of 13 digits.
        val sent = listOf("1181000221105", "1801000221105", "1119349007")
        val got = received(JemmaNearbyEndpointCodec.VICTIM_CHUNK_IMMUN, 'I', sent)
        for (code in got) {
            assertTrue(
                "The rescuer received the code '$code', which the victim's phone never sent (sent : $sent). " +
                    "A code must be broadcast whole or left out, never cut to 10 characters.",
                code in sent,
            )
        }
        assertEquals(
            "The three codes fit in one 131-byte segment, so all three must reach the rescuer unchanged.",
            sent, got,
        )
    }

    @Test
    fun `SD-02 UC-SOS-012 an allergy code of 18 digits is whole or absent`() {
        val sent = listOf("911000221103000001", "91936005")
        val got = received(JemmaNearbyEndpointCodec.VICTIM_CHUNK_ALLERGIES, 'A', sent)
        for (code in got) {
            assertTrue(
                "The rescuer received the allergy code '$code', which was never sent (sent : $sent). " +
                    "SNOMED extension codes go up to 18 digits and must not be cut.",
                code in sent,
            )
        }
    }

    @Test
    fun `SD-02 UC-SOS-012 a code that holds a dot arrives as one code`() {
        val sent = listOf("I48.0", "E11.9")
        val got = received(JemmaNearbyEndpointCodec.VICTIM_CHUNK_CONDITIONS, 'C', sent)
        assertEquals(
            "The victim's phone sent the codes $sent and the rescuer decoded $got : a code holding a dot is split " +
                "in two codes that mean something else. The rescuer must read exactly the codes that were sent.",
            sent, got,
        )
    }
}
