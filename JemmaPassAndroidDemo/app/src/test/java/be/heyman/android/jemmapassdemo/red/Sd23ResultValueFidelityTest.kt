/*
 * RED/GUARD TEST (wave 1) — SD-23, written while reviewing ag/0027-impl @ e352381 (SD-10 and SD-14 fixes).
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * 1. "1,000" is ambiguous : one (decimal comma, Belgium, Japan writes 1.000) or one thousand
 *    (thousands comma, UK/US/Japan). Turning it into the number 1 — or 1000 — silently is a
 *    thousand-fold error on a laboratory value. When the app cannot know, the value stays the text
 *    the person typed, with its unit, and is never converted into a quantity.
 * 2. The code system of a coded answer survives the compact projection (`vcs`), in both directions.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.ips.IpsCodeSystems
import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd23ResultValueFidelityTest {

    private val profile = JemmaProfileJ(j = "1.2", sid = "qa_fidelity", p = JPatient(gn = "Haru"))

    private fun result(typed: String) =
        IpsResult(id = "rs-1", code = "2345-7", display = "Glucose", value = typed, unit = "mg/dL")

    private fun bundle(r: IpsResult): String =
        JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile), IpsNativePillars(results = listOf(r)))

    private fun observation(json: String): JSONObject {
        val entries = JSONObject(json).getJSONArray("entry")
        return (0 until entries.length())
            .map { entries.getJSONObject(it).getJSONObject("resource") }
            .single { it.getString("resourceType") == "Observation" }
    }

    @Test
    fun `SD-23 UC-RES-016 a value with one comma and three digits is never turned into a number`() {
        for (typed in listOf("1,000", "2,500", "<1,000", "12,345")) {
            val obs = observation(bundle(result(typed)))
            assertFalse(
                "'$typed' mg/dL was exported as the quantity ${obs.optJSONObject("valueQuantity")} : it can mean " +
                    "${typed.filter { it.isDigit() || it == '<' }} or a decimal a thousand times smaller. " +
                    "An ambiguous value must stay text, exactly as typed.",
                obs.has("valueQuantity"),
            )
            val back = IpsFhirCodec.resultsOf(ProfileFixtures.parse(bundle(result(typed)))).single()
            assertTrue(
                "'$typed' comes back from the document as '${back.valueLabel()}' : the digits and the comma the person typed must be unchanged.",
                back.valueLabel().contains(typed),
            )
        }
    }

    @Test
    fun `SD-23 UC-RES-015 unambiguous decimals are still numbers`() {
        // Guards : a decimal comma with one or two decimals, or with a thousands space, is not ambiguous.
        for ((typed, expected) in listOf("5,5" to 5.5, "0,25" to 0.25, "1 234,5" to 1234.5, "7.2" to 7.2)) {
            val q = observation(bundle(result(typed))).optJSONObject("valueQuantity")
            assertTrue("'$typed' mg/dL is an unambiguous number and must be exported as a quantity.", q != null)
            assertEquals("'$typed' must be exported as $expected.", expected, q!!.getDouble("value"), 1e-9)
        }
    }

    @Test
    fun `SD-23 UC-RES-024 the code system of a coded answer survives the compact projection`() {
        val loinc = IpsResult(
            id = "rs-2", code = "5778-6", display = "Urine colour",
            valueCode = "LA6576-8", valueDisplay = "Yellow", valueCodeSystem = IpsCodeSystems.LOINC,
        )
        val back = IpsResult.fromJEntry(loinc.toJEntry(), 0)
        assertEquals("The answer code must survive the projection.", "LA6576-8", back.valueCode)
        assertEquals("A LOINC answer must come back as LOINC, not as SNOMED and not without a system.", IpsCodeSystems.LOINC, back.valueCodeSystem)

        val snomed = loinc.copy(id = "rs-3", valueCode = "77386006", valueCodeSystem = IpsCodeSystems.SNOMED)
        val backSnomed = IpsResult.fromJEntry(snomed.toJEntry(), 0)
        assertEquals("77386006", backSnomed.valueCode)
        assertEquals("A SNOMED answer still comes back as SNOMED (old QR codes have no system field).", IpsCodeSystems.SNOMED, backSnomed.valueCodeSystem)
    }
}
