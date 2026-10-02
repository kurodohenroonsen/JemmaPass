/*
 * RED TEST (wave 1) — SD-14, qa/usecases/suspected-defects.md. Expected to FAIL until the app is fixed.
 * Not to be edited by the implementer : fix the app, not the test.
 *
 * A result whose value is a coded answer that is not SNOMED CT (a LOINC answer code) must not
 * come back from the compact `_j` projection announced as a SNOMED CT code.
 */
package be.heyman.android.jemmapassdemo.red

import be.heyman.android.jemmapassdemo.ips.IpsCodeSystems
import be.heyman.android.jemmapassdemo.ips.IpsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sd14CodedValueSystemTest {

    @Test
    fun `SD-14 UC-RES-024 a LOINC answer code does not come back from the projection as a SNOMED code`() {
        val urineColour = IpsResult(
            id = "rs-1", code = "5778-6", display = "Urine colour",
            valueCode = "LA6576-8", valueCodeSystem = IpsCodeSystems.LOINC, valueDisplay = "Yellow",
        )
        val back = IpsResult.fromJEntry(urineColour.toJEntry())
        assertEquals("the answer must stay readable after the projection", "Yellow", back.valueLabel())
        assertTrue(
            "After a QR import the value 'LA6576-8' (a LOINC answer) is announced with the system '${back.valueCodeSystem}' : " +
                "the code does not exist in SNOMED CT, so the document is invalid for another system. Either the code " +
                "system travels with the code, or the value comes back as plain text without a code.",
            back.valueCode.isNullOrBlank() || back.valueCodeSystem == IpsCodeSystems.LOINC,
        )
    }
}
