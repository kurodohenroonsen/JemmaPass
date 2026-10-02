/*
 * MedicationFormLogic.kt — pure logic of the medication form.
 *
 *   - apply()          : merges the form into the stored entry without dropping the
 *                        fields the form does not edit (UC-MED-003, UC-MED-022).
 *   - safetyCheckGaps(): says what the form's safety cross-check could NOT verify, so
 *                        the user is told instead of reading "nothing found"
 *                        (UC-MED-002, UC-MED-016, UC-MED-023 ; README « médicaments
 *                        sans code ignorés par les contrôles du formulaire »).
 *
 * No Android dependency : covered by MedicationFormLogicTest.
 */
package be.heyman.android.jemmapassdemo.ui.profile.medications

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
    ) {
        val any: Boolean
            get() = checkNotRun || candidateUnresolved || uncodedExistingMeds.isNotEmpty()
    }

    /**
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
