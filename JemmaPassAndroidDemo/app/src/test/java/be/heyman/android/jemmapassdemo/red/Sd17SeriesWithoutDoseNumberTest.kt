/*
 * RED TEST (wave 1) — SD-17, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * A vaccine recorded as "series of 3, dose number unknown" keeps its series when the profile
 * is written and read again.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsImmunization
import dev.ohs.fhir.model.r4.Immunization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd17SeriesWithoutDoseNumberTest {

    @Test
    fun `SD-17 UC-VAC-012 a vaccine series is kept when the dose number is unknown`() {
        val im = IpsImmunization(id = "im-1", code = "836374004", display = "Hepatitis B vaccine", date = "2001", seriesDoses = 3)
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(im, "urn:uuid:00000000-0000-0000-0000-000000000001").build())
        val parsed = IpsFhirCodec.json.decodeFromString(json)
        assertTrue("the encoded resource must be an Immunization", parsed is Immunization)
        val back = IpsFhirCodec.fromFhir(parsed as Immunization)
        assertEquals(
            "A hepatitis B vaccine recorded as a series of 3 doses, dose number unknown, comes back without its series : " +
                "the series is only written when a dose number exists. The series must survive a save and a read.",
            3, back.seriesDoses,
        )
        assertNull("the dose number was not recorded : it must not be invented", back.doseNumber)
    }
}
