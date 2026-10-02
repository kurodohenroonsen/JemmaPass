/*
 * FormCrossCheckOutcomeTest.kt — UC-SAFE-SCAN : the profile forms tell "nothing to
 * check" apart from "the check crashed", and stored medications without a code are
 * reported instead of being skipped silently.
 */
package be.heyman.android.jemmapassdemo.ui.profile.common

import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.CrossSeverity
import be.heyman.android.jemmapassdemo.kb.DdiHit
import be.heyman.android.jemmapassdemo.kb.KbCheckReport
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.qr.JMedication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FormCrossCheckOutcomeTest {

    private fun ddiHit() = DdiHit(
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
        totalDurationMs = 2L, checks = checks,
    )

    private fun conflict() = NewAllergyConflict(
        existingMedDisplay = "Augmentin", existingMedAtc = "J01CR02", existingMedIndex = 0,
        matchedOn = "class:J01C", criticality = AllergyCriticality.HIGH,
    )

    // ── UC-SAFE-SCAN-21 : exception → not checked ───────────────────────────

    @Test
    fun ucSafeScan21_exception_isFailedAndNotChecked_notNothingToCheck() {
        val failed: FormCrossCheckOutcome = FormCrossCheckOutcome.Failed("db closed")
        assertEquals(KbSafetyVerdict.NOT_CHECKED, failed.verdict)
        assertTrue(failed.failed)
        assertFalse(failed.isClean)
        assertNull(failed.resultOrNull)                 // legacy accessor keeps returning null

        val skipped: FormCrossCheckOutcome =
            FormCrossCheckOutcome.NothingToCheck(FormCrossCheckOutcome.SkipReason.EMPTY_PROFILE)
        assertNull(skipped.resultOrNull)                // same legacy value…
        assertFalse(skipped.failed)                     // …but a different outcome
        assertEquals(KbSafetyVerdict.CLEAN, skipped.verdict)
    }

    @Test
    fun ucSafeScan21_blankInput_isNotChecked() {
        val o = FormCrossCheckOutcome.NothingToCheck(FormCrossCheckOutcome.SkipReason.BLANK_INPUT)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, o.verdict)
        assertEquals(
            KbSafetyVerdict.CLEAN,
            FormCrossCheckOutcome.NothingToCheck(FormCrossCheckOutcome.SkipReason.NO_PROFILE).verdict,
        )
    }

    @Test
    fun ucSafeScan21_allergyResultBuiltWithoutStatus_isNotChecked() {
        val legacy = NewAllergyConflicts(hits = emptyList(), durationMs = 1L)
        assertTrue(legacy.isEmpty)
        assertEquals(KbSafetyVerdict.NOT_CHECKED, legacy.verdict)
        assertFalse(legacy.isClean)

        val status = FormCrossCheckLogic.allergyCheckStatus(
            kbAvailable = false, medsToCheck = 2, uncodedMeds = 0, failedMeds = 0,
        )
        assertEquals(KbCheckStatus.KB_UNAVAILABLE, status)
    }

    // ── UC-SAFE-SCAN-22 : incomplete ────────────────────────────────────────

    @Test
    fun ucSafeScan22_uncodedMedications_areReportedNotSkipped() {
        val meds = listOf(
            JMedication(c = "B01AA03", displayLabel = "Warfarin"),
            JMedication(displayLabel = " Herbal tea "),
            JMedication(c = "  "),
        )
        assertEquals(listOf("Herbal tea", "?"), FormCrossCheckLogic.uncodedLabels(meds))

        val status = FormCrossCheckLogic.allergyCheckStatus(
            kbAvailable = true, medsToCheck = 3, uncodedMeds = 2, failedMeds = 0,
        )
        assertEquals(KbCheckStatus.INCOMPLETE, status)
        val r = NewAllergyConflicts(
            hits = emptyList(), durationMs = 1L, uncheckedMeds = listOf("Herbal tea", "?"), status = status,
        )
        assertEquals(KbSafetyVerdict.INCOMPLETE, r.verdict)
        assertFalse(r.isClean)
    }

    @Test
    fun ucSafeScan22_lookupFailureOnOneMedication_isIncomplete() {
        assertEquals(
            KbCheckStatus.INCOMPLETE,
            FormCrossCheckLogic.allergyCheckStatus(kbAvailable = true, medsToCheck = 2, uncodedMeds = 0, failedMeds = 1),
        )
    }

    @Test
    fun ucSafeScan22_medicationOutcome_incompleteWhenKbSaysSoOrMedsUncoded() {
        val partial = FormCrossCheckOutcome.Completed(result(checks = KbCheckReport(ddi = KbCheckStatus.INCOMPLETE)))
        assertEquals(KbSafetyVerdict.INCOMPLETE, partial.verdict)

        val uncoded = FormCrossCheckOutcome.Completed(result(), uncodedExistingMeds = listOf("Herbal tea"))
        assertEquals(KbSafetyVerdict.INCOMPLETE, uncoded.verdict)
        assertFalse(uncoded.isClean)

        val unknownDrug = FormCrossCheckOutcome.Completed(result(atc = ""))
        assertEquals(KbSafetyVerdict.NOT_CHECKED, unknownDrug.verdict)
    }

    // ── UC-SAFE-SCAN-23 : clean ─────────────────────────────────────────────

    @Test
    fun ucSafeScan23_clean_onlyWhenEverythingWasChecked() {
        val raw = result()
        val o: FormCrossCheckOutcome = FormCrossCheckOutcome.Completed(raw)
        assertEquals(KbSafetyVerdict.CLEAN, o.verdict)
        assertTrue(o.isClean)
        assertSame(raw, o.resultOrNull)

        val status = FormCrossCheckLogic.allergyCheckStatus(
            kbAvailable = true, medsToCheck = 2, uncodedMeds = 0, failedMeds = 0,
        )
        val allergy = NewAllergyConflicts(hits = emptyList(), durationMs = 1L, status = status)
        assertEquals(KbSafetyVerdict.CLEAN, allergy.verdict)
        assertTrue(allergy.isClean)
    }

    // ── UC-SAFE-SCAN-24 : alert ─────────────────────────────────────────────

    @Test
    fun ucSafeScan24_alert_hitsWinEvenOnPartialCheck() {
        val o = FormCrossCheckOutcome.Completed(
            result(ddi = listOf(ddiHit()), checks = KbCheckReport(allergy = KbCheckStatus.KB_UNAVAILABLE)),
            uncodedExistingMeds = listOf("Herbal tea"),
        )
        assertEquals(KbSafetyVerdict.ALERT, o.verdict)

        val allergy = NewAllergyConflicts(
            hits = listOf(conflict()), durationMs = 1L, status = KbCheckStatus.KB_UNAVAILABLE,
        )
        assertEquals(KbSafetyVerdict.ALERT, allergy.verdict)
        assertTrue(allergy.hasHigh)
    }
}
