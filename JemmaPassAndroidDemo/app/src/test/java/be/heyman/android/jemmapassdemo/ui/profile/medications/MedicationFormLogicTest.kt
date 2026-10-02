/*
 * MedicationFormLogicTest.kt — qa/usecases/01-pillar-editing.md :
 *   UC-MED-002 / UC-MED-016 / UC-MED-023 : the form says what its safety cross-check
 *   could not verify (medication not recognised, stored medications without a code,
 *   check not run) ; UC-MED-003 / UC-MED-022 : an edit keeps the fields the form does
 *   not show.
 */
package be.heyman.android.jemmapassdemo.ui.profile.medications

import be.heyman.android.jemmapassdemo.qr.JMedication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationFormLogicTest {

    private val warfarin = JMedication(c = "B01AA03", displayLabel = "Warfarin", codeSystem = "http://www.whocc.no/atc")
    private val herbal = JMedication(c = null, displayLabel = "Tisane de la grand-mère")
    private val blank = JMedication(c = "  ", displayLabel = " ")

    @Test
    fun `UC-MED-023 stored medications without a code are listed by label`() {
        assertEquals(
            listOf("Tisane de la grand-mère", "?"),
            MedicationFormLogic.uncodedLabels(listOf(warfarin, herbal, blank)),
        )
        assertEquals(emptyList<String>(), MedicationFormLogic.uncodedLabels(listOf(warfarin)))
    }

    @Test
    fun `UC-MED-023 a complete check with an uncoded stored medication reports it`() {
        val gaps = MedicationFormLogic.safetyCheckGaps(
            profileHasData = true, resultAvailable = true, candidateAtc = "M01AE01",
            otherMeds = listOf(warfarin, herbal),
        )
        assertTrue(gaps.any)
        assertFalse(gaps.checkNotRun)
        assertFalse(gaps.candidateUnresolved)
        assertEquals(listOf("Tisane de la grand-mère"), gaps.uncodedExistingMeds)
    }

    @Test
    fun `UC-MED-002 a medication the KB does not recognise is reported as not checked`() {
        for (atc in listOf(null, "", "  ")) {
            val gaps = MedicationFormLogic.safetyCheckGaps(
                profileHasData = true, resultAvailable = true, candidateAtc = atc, otherMeds = listOf(warfarin),
            )
            assertTrue("atc='$atc'", gaps.candidateUnresolved)
            assertTrue(gaps.any)
        }
    }

    @Test
    fun `UC-MED-016 a check that did not run is reported`() {
        val gaps = MedicationFormLogic.safetyCheckGaps(
            profileHasData = true, resultAvailable = false, candidateAtc = null, otherMeds = listOf(warfarin),
        )
        assertTrue(gaps.checkNotRun)
        assertTrue(gaps.any)
    }

    @Test
    fun `UC-MED-001 nothing is reported when the check was complete or not needed`() {
        assertFalse(
            MedicationFormLogic.safetyCheckGaps(
                profileHasData = true, resultAvailable = true, candidateAtc = "M01AE01", otherMeds = listOf(warfarin),
            ).any,
        )
        // Empty profile : there is nothing to compare against, no check is expected.
        assertFalse(
            MedicationFormLogic.safetyCheckGaps(
                profileHasData = false, resultAvailable = false, candidateAtc = null, otherMeds = emptyList(),
            ).any,
        )
    }

    @Test
    fun `UC-MED-003 an edit keeps the effective absence reason while there is no date`() {
        val stored = warfarin.copy(v = "5", u = "mg", effectiveAbsenceReason = "asked-unknown")
        val form = warfarin.copy(v = "2.5", u = "mg")
        val saved = MedicationFormLogic.apply(stored, form)
        assertEquals("2.5", saved.v)
        assertEquals("asked-unknown", saved.effectiveAbsenceReason)
        assertEquals(form.copy(effectiveAbsenceReason = "asked-unknown"), saved)
    }

    @Test
    fun `UC-MED-003 the absence reason is dropped once a date is entered`() {
        val stored = warfarin.copy(effectiveAbsenceReason = "asked-unknown")
        val saved = MedicationFormLogic.apply(stored, warfarin.copy(effective = "2020-03-01"))
        assertEquals("2020-03-01", saved.effective)
        assertNull(saved.effectiveAbsenceReason)
    }

    @Test
    fun `UC-MED-001 creation returns the form entry unchanged`() {
        assertSame(warfarin, MedicationFormLogic.apply(null, warfarin))
    }
}
