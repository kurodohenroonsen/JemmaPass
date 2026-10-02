/*
 * TEST TO EXECUTE (wave 1) — SD-18, qa/usecases/suspected-defects.md. Outcome UNKNOWN today :
 * nothing in the code tells whether the FHIR SDK keeps very large / very small decimals exact.
 * If it fails, it is a red test : fix the app, not the test.
 *
 * A laboratory value is stored exactly as typed, whatever its size : no rounding, no
 * scientific notation.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsResult
import dev.ohs.fhir.model.r4.Observation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd18ExtremeDecimalTest {

    private fun roundTrip(typed: String): IpsResult {
        val r = IpsResult(id = "rs-1", code = "2823-3", display = "Potassium", value = typed, unit = "mmol/L")
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(r, "urn:uuid:00000000-0000-0000-0000-000000000001").build())
        val parsed = IpsFhirCodec.json.decodeFromString(json)
        assertTrue("the encoded resource must be an Observation", parsed is Observation)
        return IpsFhirCodec.fromFhir(parsed as Observation)
    }

    private fun assertExact(typed: String) {
        val back = roundTrip(typed)
        assertEquals(
            "The value '$typed' mmol/L comes back from the FHIR document as '${back.value}'. A laboratory value " +
                "must be stored as an exact decimal, without rounding and without scientific notation.",
            typed, back.value,
        )
        assertEquals("the unit of '$typed' must come back too", "mmol/L", back.unit)
    }

    @Test
    fun `SD-18 UC-RES-014 very large values are exact`() {
        for (typed in listOf("25000000", "123456789012", "1234567890123456789012345")) assertExact(typed)
    }

    @Test
    fun `SD-18 UC-RES-014 very small values are exact`() {
        for (typed in listOf("0.0000001", "0.123456789012345678")) assertExact(typed)
    }
}
