/*
 * MedicationFormLogic.kt — pure logic of the medication form.
 *
 *   - apply()          : merges the form into the stored entry without dropping the
 *                        fields the form does not edit (UC-MED-003, UC-MED-022).
 *   - safetyCheckGaps(): says what the form's safety cross-check could NOT verify (from
 *                        the KbSafety verdict : only CLEAN saves without a dialog), so
 *                        the user is told instead of reading "nothing found"
 *                        (UC-MED-002, UC-MED-016, UC-MED-023 ; README « médicaments
 *                        sans code ignorés par les contrôles du formulaire »).
 *
 * No Android dependency : covered by MedicationFormLogicTest.
 */
package be.heyman.android.jemmapassdemo.ui.profile.medications

import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.qr.JMedication

object MedicationFormLogic {

    /**
     * @param existing the stored entry being edited (null when creating)
     * @param edited   the entry rebuilt from the form
     */
    fun apply(existing: JMedication?, edited: JMedication): JMedication {
        if (existing == null) return edited
        return edited.copy(
            // Not shown by the form. Only meaningful while there is no effective date.
            effectiveAbsenceReason =
                if (edited.effective.isNullOrBlank()) existing.effectiveAbsenceReason else null,
        )
    }

    /**
     * Labels of the stored medications that the cross-checks skip because they carry
     * no code (free text, no ATC / RxNorm / SNOMED).
     */
    fun uncodedLabels(meds: List<JMedication>): List<String> =
        meds.filter { it.c.isNullOrBlank() }
            .map { it.displayLabel?.trim().orEmpty().ifBlank { "?" } }

    /** What a safety cross-check left unverified. */
    data class SafetyCheckGaps(
        /** The check did not run at all (KB unavailable, exception). */
        val checkNotRun: Boolean = false,
        /** The medication being saved was not recognised : nothing was compared. */
        val candidateUnresolved: Boolean = false,
        /** Stored medications without a code, skipped by the check. */
        val uncodedExistingMeds: List<String> = emptyList(),
        /**
         * The check ran only partly (verdict INCOMPLETE, or hits found on a partial check)
         * for a reason the uncoded medications do not explain.
         */
        val checkIncomplete: Boolean = false,
    ) {
        val any: Boolean
            get() = checkNotRun || candidateUnresolved || checkIncomplete || uncodedExistingMeds.isNotEmpty()
    }

    /**
     * Gaps of a cross-check, decided by [CrossCheckResult.verdict] / [CrossCheckResult.checked]
     * (kb/KbSafety.kt) and no longer by "an ATC was resolved and there is no hit" :
     *
     *   CLEAN        → no gap : the only verdict that saves without a dialog
     *   NOT_CHECKED  → [SafetyCheckGaps.checkNotRun] (KB unavailable) or
     *                  [SafetyCheckGaps.candidateUnresolved] (drug unknown to the KB)
     *   INCOMPLETE   → the uncoded stored medications when they explain it, else
     *                  [SafetyCheckGaps.checkIncomplete]
     *   ALERT        → the hits are shown by the alert dialog ; what was NOT verified
     *                  (same rules, from `checked`) is still reported after it
     *
     * @param profileHasData the profile has at least one allergy, medication or condition ;
     *                       when it has none there is nothing to compare against and the
     *                       form helper does not run the check (null result, no gap)
     * @param result         the cross-check result, null when it did not run
     * @param otherMeds      the other medications of the profile
     */
    fun safetyCheckGaps(
        profileHasData: Boolean,
        result: CrossCheckResult?,
        otherMeds: List<JMedication>,
    ): SafetyCheckGaps {
        if (!profileHasData) return SafetyCheckGaps()
        if (result == null) return SafetyCheckGaps(checkNotRun = true)
        if (result.verdict == KbSafetyVerdict.CLEAN) return SafetyCheckGaps()
        if (!result.kbAvailable) return SafetyCheckGaps(checkNotRun = true)
        if (!result.candidateResolved) return SafetyCheckGaps(candidateUnresolved = true)
        // ALERT on a full check : the hits are the whole story.
        if (result.checked) return SafetyCheckGaps()
        val uncoded = uncodedLabels(otherMeds)
        val onlyMedicationsUnverified = result.checks.allergy == KbCheckStatus.CHECKED &&
            result.checks.drugDisease == KbCheckStatus.CHECKED
        return SafetyCheckGaps(
            uncodedExistingMeds = uncoded,
            checkIncomplete = uncoded.isEmpty() || !onlyMedicationsUnverified,
        )
    }

    /**
     * Legacy rule (ATC resolved = check complete), kept for its tests ; the form uses the
     * verdict-based overload above.
     *
     * @param profileHasData   the profile has at least one allergy, medication or condition
     *                         to compare against (otherwise no check is needed)
     * @param resultAvailable  the cross-check returned a result
     * @param candidateAtc     ATC the cross-check resolved for the medication being saved
     *                         (blank = not recognised)
     * @param otherMeds        the other medications of the profile
     */
    fun safetyCheckGaps(
        profileHasData: Boolean,
        resultAvailable: Boolean,
        candidateAtc: String?,
        otherMeds: List<JMedication>,
    ): SafetyCheckGaps {
        if (!profileHasData) return SafetyCheckGaps()
        if (!resultAvailable) return SafetyCheckGaps(checkNotRun = true)
        if (candidateAtc.isNullOrBlank()) return SafetyCheckGaps(candidateUnresolved = true)
        return SafetyCheckGaps(uncodedExistingMeds = uncodedLabels(otherMeds))
    }
}
