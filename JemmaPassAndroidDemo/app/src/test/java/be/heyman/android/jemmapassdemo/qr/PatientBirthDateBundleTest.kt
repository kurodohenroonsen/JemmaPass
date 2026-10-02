/*
 * PatientBirthDateBundleTest.kt — a person whose birth date is unknown, or known to the
 * year only (refugee, incomplete civil registry, elderly person), must still get a
 * FHIR document. Without it the profile has no Bundle and the native pillars are stored
 * in the `_j` projection alone.
 *
 * Use cases (qa/usecases/03) : UC-HUM-001, UC-HUM-002, UC-HUM-003, UC-PAT-005.
 *
 * NOTE for the integrator : the form already accepts "1950" and "1950-06" (FormEditGuardsTest).
 * The first test below relies on the FHIR SDK accepting those two precisions for
 * Patient.birthDate, as it does for the dates of the native pillars
 * (IpsImmunizationCodecTest.partialDatesAreKeptAsIs). It was written by reading the code,
 * without running it : if it fails, the defect is real (no Bundle for these profiles) and
 * belongs in qa/usecases/suspected-defects.md, entry SD-07.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsImmunization
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PatientBirthDateBundleTest {

    private val vaccine = IpsImmunization(id = "im-1", code = "836374004", display = "Hepatitis B vaccine", date = "2001")

    private fun bundleJson(bd: String?): String = JemmaFhirBundleBuilder.build(
        ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = "qa_birth", p = JPatient(gn = "Amina", fn = "Diallo", gs = "F", bd = bd))),
        IpsNativePillars(immunizations = listOf(vaccine)),
    )

    private fun patient(json: String): JSONObject {
        val entries = JSONObject(json).getJSONArray("entry")
        return (0 until entries.length()).map { entries.getJSONObject(it).getJSONObject("resource") }
            .single { it.getString("resourceType") == "Patient" }
    }

    @Test
    fun `UC-HUM-002 a birth date known to the year or to the month is exported as given`() {
        for (bd in listOf("1950", "1950-06", "1950-06-15", "2000-02-29")) {
            val json = bundleJson(bd)
            assertEquals(bd, patient(json).getString("birthDate"))
            // the rest of the record is in the document too
            assertEquals(bd, listOf(vaccine), IpsFhirCodec.immunizationsOf(ProfileFixtures.parse(json)))
        }
    }

    @Test
    fun `UC-HUM-001 an unknown birth date gives a document without birthDate, not an invented one`() {
        for (bd in listOf(null, "", "   ")) {
            val json = bundleJson(bd)
            val p = patient(json)
            assertFalse("bd='$bd'", p.has("birthDate"))
            assertEquals("Diallo", p.getJSONArray("name").getJSONObject(0).getString("family"))
            assertEquals(listOf(vaccine), IpsFhirCodec.immunizationsOf(ProfileFixtures.parse(json)))
        }
    }
}
