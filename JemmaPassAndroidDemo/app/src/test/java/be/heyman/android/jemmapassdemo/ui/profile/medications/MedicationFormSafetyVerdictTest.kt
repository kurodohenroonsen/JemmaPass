/*
 * MedicationFormSafetyVerdictTest.kt — UC-MED-ROUTE-10..14 (medication form, save step) :
 * the form decides "safety check complete" from CrossCheckResult.verdict / checked
 * (kb/KbSafety.kt). Only CLEAN saves without the "Safety check incomplete" dialog.
 */
package be.heyman.android.jemmapassdemo.ui.profile.medications

import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.CrossSeverity
import be.heyman.android.jemmapassdemo.kb.DdiHit
import be.heyman.android.jemmapassdemo.kb.KbCheckReport
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.qr.JMedication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationFormSafetyVerdictTest {

    private val warfarin = JMedication(c = "B01AA03", displayLabel = "Warfarin", codeSystem = "http://www.whocc.no/atc")
    private val herbal = JMedication(c = null, displayLabel = "Tisane de la grand-mère")

    private val ddiHit = DdiHit(
        existingMedDisplay = "Warfarin", existingMedAtc = "B01AA03",
        candidateAtc = "M01AE01", candidateDisplay = "Ibuprofen", severity = CrossSeverity.MAJOR,
        mechanism = null, description = null, management = null, alternativeAtc = null, queryDurationMs = 1L,
    )

    private fun result(
        atc: String = "M01AE01",
        ddi: List<DdiHit> = emptyList(),
        checks: KbCheckReport = KbCheckReport(),
    ) = CrossCheckResult(
        candidateAtc = atc, candidateDisplay = "Ibuprofen",
        allergyHits = emptyList(), ddiHits = ddi, drugDiseaseHits = emptyList(),
        totalDurationMs = 3L, checks = checks,
    )

    private fun gaps(r: CrossCheckResult?, meds: List<JMedication> = listOf(warfarin)) =
        MedicationFormLogic.safetyCheckGaps(profileHasData = true, result = r, otherMeds = meds)

    @Test
    fun `UC-MED-ROUTE-10 only a CLEAN verdict saves without a dialog`() {
        val clean = result()
        assertEquals(KbSafetyVerdict.CLEAN, clean.verdict)
        assertFalse(gaps(clean).any)
        // CLEAN means every stored medication was looked up, coded or not.
        assertFalse(gaps(clean, listOf(warfarin, herbal)).any)
    }

    @Test
    fun `UC-MED-ROUTE-11 NOT_CHECKED always shows the dialog, even with an ATC resolved`() {
        // KB unavailable although the candidate carries an ATC: the old rule (ATC + no hit) saved silently.
        val kbDown = result(checks = KbCheckReport.all(KbCheckStatus.KB_UNAVAILABLE))
        assertEquals(KbSafetyVerdict.NOT_CHECKED, kbDown.verdict)
        assertTrue(gaps(kbDown).checkNotRun)
        assertTrue(gaps(kbDown).any)

        val unresolved = result(atc = "", checks = KbCheckReport.all(KbCheckStatus.INCOMPLETE))
        assertEquals(KbSafetyVerdict.NOT_CHECKED, unresolved.verdict)
        assertTrue(gaps(unresolved).candidateUnresolved)
        assertTrue(gaps(unresolved).any)

        // The check did not return at all.
        assertTrue(gaps(null).checkNotRun)
    }

    @Test
    fun `UC-MED-ROUTE-12 INCOMPLETE always shows the dialog`() {
        // A stored medication without a code explains it: it is named.
        val ddiPartial = result(checks = KbCheckReport(ddi = KbCheckStatus.INCOMPLETE))
        assertEquals(KbSafetyVerdict.INCOMPLETE, ddiPartial.verdict)
        val named = gaps(ddiPartial, listOf(warfarin, herbal))
        assertEquals(listOf("Tisane de la grand-mère"), named.uncodedExistingMeds)
        assertFalse(named.checkIncomplete)
        assertTrue(named.any)

        // Every stored medication is coded (failed query, drug unknown to the KB): still a gap.
        val unexplained = gaps(ddiPartial, listOf(warfarin))
        assertTrue(unexplained.checkIncomplete)
        assertTrue(unexplained.any)

        // Another pillar is partial: the uncoded medication does not explain everything.
        val allergyPartial = gaps(
            result(checks = KbCheckReport(allergy = KbCheckStatus.INCOMPLETE, ddi = KbCheckStatus.INCOMPLETE)),
            listOf(warfarin, herbal),
        )
        assertTrue(allergyPartial.checkIncomplete)
        assertEquals(listOf("Tisane de la grand-mère"), allergyPartial.uncodedExistingMeds)

        // One pillar without the KB.
        val onePillarDown = result(checks = KbCheckReport(drugDisease = KbCheckStatus.KB_UNAVAILABLE))
        assertEquals(KbSafetyVerdict.NOT_CHECKED, onePillarDown.verdict)
        assertTrue(gaps(onePillarDown).checkNotRun)
    }

    @Test
    fun `UC-MED-ROUTE-13 after an alert the unverified part is still reported`() {
        val fullAlert = result(ddi = listOf(ddiHit))
        assertEquals(KbSafetyVerdict.ALERT, fullAlert.verdict)
        assertTrue(fullAlert.checked)
        assertFalse(gaps(fullAlert).any)

        val partialAlert = result(ddi = listOf(ddiHit), checks = KbCheckReport(allergy = KbCheckStatus.INCOMPLETE))
        assertEquals(KbSafetyVerdict.ALERT, partialAlert.verdict)
        assertFalse(partialAlert.checked)
        assertTrue(gaps(partialAlert).checkIncomplete)
    }

    @Test
    fun `UC-MED-ROUTE-14 an empty profile has nothing to compare against`() {
        assertFalse(MedicationFormLogic.safetyCheckGaps(profileHasData = false, result = null, otherMeds = emptyList()).any)
    }
}
