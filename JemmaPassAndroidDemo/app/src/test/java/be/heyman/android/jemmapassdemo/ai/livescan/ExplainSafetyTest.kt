/*
 * ExplainSafetyTest.kt — UC-SAFE-SCAN-3x : the report JSON handed to the explainer LLM
 * carries verdict / checked / is_clean / instruction, so an empty hit list on an
 * unverified result cannot be explained as "safe".
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainSafetyTest {

    private fun assertTellsToAskPharmacistOrDoctor(text: String) {
        val t = text.lowercase()
        assertTrue(text, t.contains("pharmacist"))
        assertTrue(text, t.contains("doctor"))
        assertTrue(text, t.contains("could not be"))
        assertTrue(text, t.contains("do not say"))
    }

    @Test
    fun `UC-SAFE-SCAN-37 CLEAN fully checked is the only clean answer`() {
        val f = ExplainSafety.safetyFields(KbSafetyVerdict.CLEAN, fullyChecked = true)
        assertEquals("CLEAN", f["verdict"])
        assertEquals(true, f["checked"])
        assertEquals(true, f["is_clean"])
        assertEquals(ExplainSafety.INSTRUCTION_CLEAN, f["instruction"])

        for (v in KbSafetyVerdict.values()) {
            for (checked in listOf(true, false)) {
                val clean = ExplainSafety.safetyFields(v, checked)["is_clean"]
                assertEquals("$v checked=$checked", v == KbSafetyVerdict.CLEAN && checked, clean)
            }
        }
    }

    @Test
    fun `UC-SAFE-SCAN-37 NOT_CHECKED tells to ask a pharmacist or a doctor`() {
        val f = ExplainSafety.safetyFields(KbSafetyVerdict.NOT_CHECKED, fullyChecked = false)
        assertEquals("NOT_CHECKED", f["verdict"])
        assertEquals(false, f["checked"])
        assertEquals(false, f["is_clean"])
        assertTellsToAskPharmacistOrDoctor(f["instruction"] as String)
    }

    @Test
    fun `UC-SAFE-SCAN-37 INCOMPLETE tells to ask a pharmacist or a doctor`() {
        val f = ExplainSafety.safetyFields(KbSafetyVerdict.INCOMPLETE, fullyChecked = false)
        assertEquals("INCOMPLETE", f["verdict"])
        assertEquals(false, f["is_clean"])
        assertTellsToAskPharmacistOrDoctor(f["instruction"] as String)
    }

    @Test
    fun `UC-SAFE-SCAN-37 CLEAN without a full check fails safe`() {
        val f = ExplainSafety.safetyFields(KbSafetyVerdict.CLEAN, fullyChecked = false)
        assertEquals(false, f["is_clean"])
        assertTellsToAskPharmacistOrDoctor(f["instruction"] as String)
    }

    @Test
    fun `UC-SAFE-SCAN-37 ALERT reports hits and flags a partial check`() {
        assertEquals(
            ExplainSafety.INSTRUCTION_ALERT,
            ExplainSafety.safetyFields(KbSafetyVerdict.ALERT, fullyChecked = true)["instruction"],
        )
        val partial = ExplainSafety.safetyFields(KbSafetyVerdict.ALERT, fullyChecked = false)
        assertEquals("ALERT", partial["verdict"])
        assertEquals(false, partial["checked"])
        val t = (partial["instruction"] as String).lowercase()
        assertTrue(t, t.contains("pharmacist") && t.contains("doctor"))
    }

    @Test
    fun `UC-SAFE-SCAN-37 error answer is NOT_CHECKED`() {
        val f = ExplainSafety.failureFields("cross_check_failed")
        assertEquals("cross_check_failed", f["error"])
        assertEquals("NOT_CHECKED", f["verdict"])
        assertEquals(false, f["checked"])
        assertEquals(false, f["is_clean"])
        assertTellsToAskPharmacistOrDoctor(f["instruction"] as String)
    }
}
