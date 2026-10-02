/*
 * AllergyFormSafetyLogic.kt — pure logic of the allergy form safety check (UC-SAFE-UI-2x).
 *
 * "No conflict found" is not "verified" : the form may save without a dialog ONLY
 * when the check "new allergy × stored medications" is CLEAN (kb/KbSafety.kt).
 * A check that did not run (no profile snapshot, knowledge base unavailable) or
 * that ran only partly (medication without a code, lookup error) is reported in a
 * "Safety check incomplete" dialog, like the medication form does.
 *
 * No Android dependency : covered by AllergyFormSafetyLogicTest.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies

import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.ui.profile.common.NewAllergyConflicts

object AllergyFormSafetyLogic {

    /** What the check "new allergy × stored medications" left unverified. */
    data class SafetyCheckGaps(
        /** Nothing was verified (no profile snapshot, knowledge base unavailable, no result). */
        val checkNotRun: Boolean = false,
        /** Stored medications that could not be compared (no code, or the lookup failed). */
        val uncheckedMeds: List<String> = emptyList(),
        /** The check ran only partly and no medication label explains it. */
        val checkIncomplete: Boolean = false,
    ) {
        val any: Boolean
            get() = checkNotRun || checkIncomplete || uncheckedMeds.isNotEmpty()
    }

    /** Which sentence the "Safety check incomplete" dialog shows. */
    enum class GapMessage { NOT_RUN, UNCHECKED_MEDS, INCOMPLETE }

    /**
     * Gaps of the submit-time check.
     *
     *   no profile snapshot           → [SafetyCheckGaps.checkNotRun]
     *   no stored medication          → no gap (nothing to collide with)
     *   medications but no result     → [SafetyCheckGaps.checkNotRun]
     *   status CHECKED (CLEAN, or ALERT on a full check) → no gap
     *   status KB_UNAVAILABLE         → [SafetyCheckGaps.checkNotRun]
     *   status INCOMPLETE             → the unchecked medications, else
     *                                   [SafetyCheckGaps.checkIncomplete]
     *
     * With hits (ALERT) the conflict dialog is shown first ; the gaps returned here
     * are still reported after it.
     *
     * @param profileAvailable the profile being edited could be loaded
     * @param storedMedCount   number of medications stored in that profile
     * @param result           result of `FormCrossCheckHelper.checkNewAllergyAgainstMeds`
     */
    fun safetyCheckGaps(
        profileAvailable: Boolean,
        storedMedCount: Int,
        result: NewAllergyConflicts?,
    ): SafetyCheckGaps {
        if (!profileAvailable) return SafetyCheckGaps(checkNotRun = true)
        if (result == null) {
            return if (storedMedCount <= 0) SafetyCheckGaps() else SafetyCheckGaps(checkNotRun = true)
        }
        if (result.verdict == KbSafetyVerdict.CLEAN) return SafetyCheckGaps()
        return when (result.status) {
            KbCheckStatus.CHECKED -> SafetyCheckGaps()
            KbCheckStatus.KB_UNAVAILABLE -> SafetyCheckGaps(checkNotRun = true)
            KbCheckStatus.INCOMPLETE -> SafetyCheckGaps(
                uncheckedMeds = result.uncheckedMeds,
                checkIncomplete = result.uncheckedMeds.isEmpty(),
            )
        }
    }

    /** True when the form may commit without the "Safety check incomplete" dialog. */
    fun maySaveSilently(gaps: SafetyCheckGaps): Boolean = !gaps.any

    /** Sentence of the dialog, null when there is no gap. "Not run" wins over the rest. */
    fun gapMessage(gaps: SafetyCheckGaps): GapMessage? = when {
        gaps.checkNotRun -> GapMessage.NOT_RUN
        gaps.uncheckedMeds.isNotEmpty() -> GapMessage.UNCHECKED_MEDS
        gaps.checkIncomplete -> GapMessage.INCOMPLETE
        else -> null
    }

    /**
     * Pick-time result : true only when the check really ran and found nothing.
     * (A null result means there was no medication to compare against.)
     */
    fun isCleanAtPick(result: NewAllergyConflicts?): Boolean =
        result == null || result.verdict == KbSafetyVerdict.CLEAN
}
