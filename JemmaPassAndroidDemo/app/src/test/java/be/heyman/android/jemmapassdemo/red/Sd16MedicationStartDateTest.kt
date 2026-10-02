/*
 * RED TEST (wave 1) — SD-16, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * The start date of a treatment (`md[].eff`) is in the IPS document : a foreign doctor must
 * know since when the anticoagulant is taken.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd16MedicationStartDateTest {

    /** The MedicationStatement of a profile holding warfarin with the given `eff` value, as JSON text. */
    private fun statementOf(effective: String): String {
        val raw = JemmaProfileJ(
            j = "1.2", sid = "qa_eff", p = JPatient(gn = "Haru"),
            md = listOf(JMedication(c = "B01AA03", displayLabel = "Warfarin", codeSystem = "http://www.whocc.no/atc", effective = effective)),
        )
        val entries = JSONObject(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(raw))).getJSONArray("entry")
        return (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .single { it.getString("resourceType") == "MedicationStatement" }
            .toString()
    }

    @Test
    fun `SD-16 UC-MED-009 the start date of a treatment is in the document`() {
        assertTrue(
            "Warfarin taken since 2020-03-01 : the date is nowhere in the MedicationStatement of the IPS document " +
                "(it stays in the compact profile only). The statement must carry it as effectiveDateTime or effectivePeriod.",
            statementOf("2020-03-01").contains("2020-03-01"),
        )
    }

    @Test
    fun `SD-16 UC-FHIR-005 the start and the end of a finished treatment are in the document`() {
        val statement = statementOf("2020-03-01/2021-04-05")
        assertTrue(
            "Warfarin taken from 2020-03-01 to 2021-04-05 (stored as '2020-03-01/2021-04-05') : the MedicationStatement " +
                "of the IPS document holds neither date. It must carry both, as an effectivePeriod.",
            statement.contains("2020-03-01") && statement.contains("2021-04-05"),
        )
    }
}
