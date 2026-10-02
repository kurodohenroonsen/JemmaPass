/*
 * RED TEST (wave 1) — SD-10, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * A laboratory value that is not a plain number ("<0.5", "1 234,5", ">100") keeps its unit,
 * in the `_j` projection and in the IPS document : a caregiver must never read a value
 * without knowing whether it is mg/L or mmol/L.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd10AlmostNumericResultUnitTest {

    private val typedValues = listOf("<0.5", "1 234,5", ">100", "5.")

    private fun crp(typed: String) =
        IpsResult(id = "rs-1", code = "1988-5", display = "CRP", value = typed, unit = "mg/L")

    /** "<0.5" is not "0.5" : whatever the fix does with the unit, the comparison sign and the digits stay. */
    private fun assertSignKept(typed: String, shown: String) {
        if (!typed.startsWith("<") && !typed.startsWith(">")) return
        assertTrue(
            "The value '$typed' is shown as '$shown' : the comparison sign or the number was lost. " +
                "'below 0.5' and '0.5' are not the same result.",
            shown.contains(typed.take(1)) && shown.contains(typed.drop(1)),
        )
    }

    @Test
    fun `SD-10 UC-RES-015 the unit of a value that is not a plain number stays in the compact projection`() {
        for (typed in typedValues) {
            val j = crp(typed).toJEntry()
            assertTrue(
                "CRP '$typed' mg/L is projected as v='${j.value}' u='${j.unit}' : the unit is lost, so the QR and the " +
                    "rescuer's screen show a value without unit. The unit must stay readable, in `u` or next to the value.",
                j.unit == "mg/L" || j.value.orEmpty().contains("mg/L"),
            )
            assertSignKept(typed, j.value.orEmpty())
        }
    }

    @Test
    fun `SD-10 UC-RES-015 the unit of a value that is not a plain number stays in the document`() {
        val profile = JemmaProfileJ(j = "1.2", sid = "qa_unit", p = JPatient(gn = "Haru"))
        for (typed in typedValues) {
            val json = JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile), IpsNativePillars(results = listOf(crp(typed))))
            val back = IpsFhirCodec.resultsOf(ProfileFixtures.parse(json)).single()
            assertTrue(
                "CRP '$typed' mg/L comes back from the IPS document as '${back.valueLabel()}' with unit '${back.unit}' : " +
                    "the unit is lost. The unit must stay readable in the document, as a unit or next to the value.",
                back.unit == "mg/L" || back.valueLabel().contains("mg/L"),
            )
            assertSignKept(typed, back.valueLabel())
        }
    }
}
