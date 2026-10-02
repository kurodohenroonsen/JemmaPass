/*
 * NearbyEndpointBudgetTest.kt — what a victim's phone broadcasts to nearby rescuers
 * (JemmaNearbyEndpointCodec : 131 bytes per segment, hard limit of Nearby Connections).
 *
 * Use cases (qa/usecases/02) : UC-SOS-010 (header and codes reach the rescuer),
 * UC-SOS-011 (a long medication list never overflows a segment and keeps the first,
 * most critical codes), UC-SOS-014 (a non-latin name is cut without breaking a character),
 * UC-SOS-015 (garbage is ignored, no crash).
 *
 * Not pinned here (recorded in qa/usecases/suspected-defects.md) : codes longer than 10
 * characters are truncated, codes holding a dot are split, and the rescuer is not told
 * that codes were left out.
 */
package be.heyman.android.jemmapassdemo.sos

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyEndpointBudgetTest {

    private val limit = JemmaNearbyEndpointCodec.MAX_ENDPOINT_NAME_LEN

    private fun bytes(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    private fun decodeCodes(chunk: String): JemmaNearbyEndpointCodec.Decoded.VictimCodes {
        val d = JemmaNearbyEndpointCodec.decode(chunk)
        assertTrue("not a codes segment : $chunk", d is JemmaNearbyEndpointCodec.Decoded.VictimCodes)
        return d as JemmaNearbyEndpointCodec.Decoded.VictimCodes
    }

    private fun decodeHeader(chunk: String): JemmaNearbyEndpointCodec.Decoded.VictimHeader {
        val d = JemmaNearbyEndpointCodec.decode(chunk)
        assertTrue("not a header segment : $chunk", d is JemmaNearbyEndpointCodec.Decoded.VictimHeader)
        return d as JemmaNearbyEndpointCodec.Decoded.VictimHeader
    }

    @Test
    fun `UC-SOS-010 a short list of codes reaches the rescuer whole and in order`() {
        assertEquals(131, limit)
        val sections = mapOf(
            'A' to (JemmaNearbyEndpointCodec.VICTIM_CHUNK_ALLERGIES to listOf("91936005", "417532002", "419263009")),
            'M' to (JemmaNearbyEndpointCodec.VICTIM_CHUNK_MEDS to listOf("C07AB07", "B01AA03", "M01AE01", "G04BE03", "C01DA08")),
            'C' to (JemmaNearbyEndpointCodec.VICTIM_CHUNK_CONDITIONS to listOf("59621000", "49436004", "194828000")),
            'I' to (JemmaNearbyEndpointCodec.VICTIM_CHUNK_IMMUN to listOf("871876003", "871803007", "836378001", "1119349007")),
        )
        for ((marker, pair) in sections) {
            val (index, codes) = pair
            val chunk = JemmaNearbyEndpointCodec.encodeVictimCodes("ab12", index, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, marker, codes)
            assertTrue("$marker ${bytes(chunk)} bytes", bytes(chunk) <= limit)
            JemmaNearbyEndpointCodec.assertFitsBLE(chunk)
            val d = decodeCodes(chunk)
            assertEquals("ab12", d.sid)
            assertEquals(index, d.chunkIdx)
            assertEquals(JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, d.chunkTotal)
            assertEquals(marker, d.sectionMarker)
            assertEquals("$marker codes", codes, d.codes)
        }
    }

    @Test
    fun `UC-SOS-011 fifty medication codes never overflow a segment and the first ones are kept in order`() {
        val fifty = (1..50).map { String.format(java.util.Locale.ROOT, "B01AA%02d", it) }
        val chunk = JemmaNearbyEndpointCodec.encodeVictimCodes("ab12", JemmaNearbyEndpointCodec.VICTIM_CHUNK_MEDS, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, 'M', fifty)
        assertTrue("${bytes(chunk)} bytes", bytes(chunk) <= limit)
        JemmaNearbyEndpointCodec.assertFitsBLE(chunk)
        assertEquals(bytes(chunk), JemmaNearbyEndpointCodec.byteLengthOf(chunk))

        val kept = decodeCodes(chunk).codes
        assertTrue("some codes are kept (${kept.size})", kept.isNotEmpty())
        assertTrue("not all fifty fit (${kept.size})", kept.size < fifty.size)
        assertEquals("the first codes, in the order given (most critical first)", fifty.take(kept.size), kept)
        // the segment is full : one more code of that size would not have fit
        assertTrue(bytes(chunk) + 1 + fifty[kept.size].length > limit)
    }

    @Test
    fun `UC-SOS-011 an empty section is still announced`() {
        val chunk = JemmaNearbyEndpointCodec.encodeVictimCodes("ab12", JemmaNearbyEndpointCodec.VICTIM_CHUNK_ALLERGIES, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, 'A', emptyList())
        val d = decodeCodes(chunk)
        assertEquals('A', d.sectionMarker)
        assertTrue(d.codes.isEmpty())
    }

    @Test
    fun `UC-SOS-011 random code lists always fit and what is received is what was sent, never something else`() {
        val random = Random(20261002L)
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789-"
        repeat(2_000) { round ->
            val codes = (0 until random.nextInt(60)).map {
                (1..(1 + random.nextInt(10))).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            }
            val marker = "AMCI"[random.nextInt(4)]
            val chunk = JemmaNearbyEndpointCodec.encodeVictimCodes("c0de", 2 + random.nextInt(4), JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, marker, codes)
            assertTrue("round $round : ${bytes(chunk)} bytes", bytes(chunk) <= limit)
            val kept = decodeCodes(chunk).codes
            // every received code was sent, in the same relative order (a subsequence of the input)
            var cursor = 0
            for (k in kept) {
                val at = codes.subList(cursor, codes.size).indexOf(k)
                assertTrue("round $round : received '$k' was not sent at that place", at >= 0)
                cursor += at + 1
            }
            if (codes.isNotEmpty()) assertEquals("round $round : the first code always fits", codes.first(), kept.first())
        }
    }

    @Test
    fun `UC-SOS-014 a non latin name is cut on a character boundary and the header fits`() {
        val names = listOf(
            "亀吉 田中 太郎", "春", "محمد بن عبد الله آل سعود", "🙂🙂🙂🙂🙂🙂", "Jean-Baptiste de La Salle-Montmorency", "Zoë Ünïcödé Łukasiewicz",
            "A|B|C", "x", "",
        )
        for (name in names) {
            val chunk = JemmaNearbyEndpointCodec.encodeVictimHeader(
                sid = "ab12", chunkTotal = JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, name = name, sex = 'M', age = 47,
                bloodType = "AB+", criticality = 3, lat = -89.123456, lon = -179.123456, victimLang = "ja",
            )
            assertTrue("'$name' ${bytes(chunk)} bytes", bytes(chunk) <= limit)
            JemmaNearbyEndpointCodec.assertFitsBLE(chunk)
            val d = decodeHeader(chunk)
            assertTrue("'$name' → '${d.name}' is more than 16 bytes", bytes(d.name) <= 16)
            assertFalse("'$name' → '${d.name}' holds a broken character", d.name.contains('�'))
            assertTrue("'$name' → '${d.name}' is not the start of the name", name.replace('|', ' ').trim().startsWith(d.name.trim()))
            // the rest of the identity is intact whatever the name
            assertEquals("ab12", d.sid)
            assertEquals('M', d.sex)
            assertEquals(47, d.age)
            assertEquals("AB+", d.bloodType)
            assertEquals(3, d.criticality)
            assertEquals(-89.123456, d.lat, 1e-9)
            assertEquals(-179.123456, d.lon, 1e-9)
            assertEquals("ja", d.langCode)
        }
        // a short name is not touched
        val whole = decodeHeader(JemmaNearbyEndpointCodec.encodeVictimHeader("ab12", 5, "Haru", 'F', 80, "O+", 2, 35.0, 139.0, "ja"))
        assertEquals("Haru", whole.name)
        assertEquals('F', whole.sex)
        assertEquals(80, whole.age)
    }

    @Test
    fun `UC-SOS-015 anything that is not a JEMMA segment is ignored without a crash`() {
        for (garbage in listOf("", " ", "hello", "X|ab12|1|5", "V", "V|ab12", "V|ab12|1", "V|ab12|x|5|A:1", "V|ab12|2|y|A:1",
            "V|ab12|9|5|A:1", "V|ab12|2|5|nocolon", "V|ab12|2|5|:1", "V|ab12|1|5|Haru|F|80", "S|ab12|name", "|||||", "V||||")) {
            assertNull("'$garbage'", JemmaNearbyEndpointCodec.decode(garbage))
        }
        // a legacy header without the language field is still read
        val legacy = JemmaNearbyEndpointCodec.decode("V|ab12|1|5|Haru|F|80|O+|2|35.000000|139.000000")
        assertNotNull(legacy)
        assertEquals("", (legacy as JemmaNearbyEndpointCodec.Decoded.VictimHeader).langCode)
    }
}
