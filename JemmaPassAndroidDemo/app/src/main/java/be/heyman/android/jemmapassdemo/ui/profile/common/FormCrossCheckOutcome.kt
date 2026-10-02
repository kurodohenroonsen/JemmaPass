/*
 * FormCrossCheckOutcome.kt — JEMMA Pass · profile forms · UC-SAFE-SCAN
 *
 * Pure-Kotlin result types and decision logic of [FormCrossCheckHelper]
 * (no Android class, JVM unit-testable).
 *
 * The legacy helper API returned `null` both for "there is nothing to check"
 * and for "the check crashed". The outcome below keeps the two apart, so a
 * form can only display "no interaction" when [verdict] is CLEAN.
 */
package be.heyman.android.jemmapassdemo.ui.profile.common

import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafety
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.qr.JMedication

/** Outcome of "new medication × profile" (see [FormCrossCheckHelper.checkNewMedication]). */
sealed class FormCrossCheckOutcome {

    enum class SkipReason {
        /** No medication name / code was given : nothing could be checked. */
        BLANK_INPUT,

        /** No profile yet (first entry) : nothing to collide with. */
        NO_PROFILE,

        /** The profile holds no allergy, medication or condition : nothing to collide with. */
        EMPTY_PROFILE,
    }

    /** The check was not needed (or not possible for a blank input). */
    data class NothingToCheck(val reason: SkipReason) : FormCrossCheckOutcome()

    /**
     * The KB cross-check returned.
     *
     * @param uncodedExistingMeds labels of the stored medications that carry no code and
     *                            therefore cannot be compared reliably
     */
    data class Completed(
        val result: CrossCheckResult,
        val uncodedExistingMeds: List<String> = emptyList(),
    ) : FormCrossCheckOutcome()

    /** The cross-check threw : NOTHING was verified. */
    data class Failed(val message: String?) : FormCrossCheckOutcome()

    /** Legacy accessor : the KB result, or null when skipped or failed. */
    val resultOrNull: CrossCheckResult?
        get() = (this as? Completed)?.result

    /** True when the check crashed (as opposed to "nothing to check"). */
    val failed: Boolean
        get() = this is Failed

    /** What the form may say. CLEAN is the only verdict that may be shown as "no interaction". */
    val verdict: KbSafetyVerdict
        get() = when (this) {
            is NothingToCheck ->
                if (reason == SkipReason.BLANK_INPUT) KbSafetyVerdict.NOT_CHECKED else KbSafetyVerdict.CLEAN
            is Failed -> KbSafetyVerdict.NOT_CHECKED
            is Completed -> when {
                result.verdict == KbSafetyVerdict.CLEAN && uncodedExistingMeds.isNotEmpty() ->
                    KbSafetyVerdict.INCOMPLETE
                else -> result.verdict
            }
        }

    val isClean: Boolean
        get() = verdict == KbSafetyVerdict.CLEAN
}

/** Pure decision helpers of [FormCrossCheckHelper]. */
object FormCrossCheckLogic {

    /** Labels of the medications that carry no code (free text) : the checks cannot compare them. */
    fun uncodedLabels(meds: List<JMedication>): List<String> =
        meds.filter { it.c.isNullOrBlank() }
            .map { it.displayLabel?.trim().orEmpty().ifBlank { "?" } }

    /**
     * Status of "new allergy × stored medications".
     *
     * @param kbAvailable   the knowledge base could be opened
     * @param medsToCheck   number of stored medications
     * @param uncodedMeds   how many were skipped because they carry no code
     * @param failedMeds    how many could not be checked because the lookup threw
     */
    fun allergyCheckStatus(
        kbAvailable: Boolean,
        medsToCheck: Int,
        uncodedMeds: Int,
        failedMeds: Int,
    ): KbCheckStatus = KbSafety.pillarStatus(
        kbAvailable = kbAvailable,
        itemsToCheck = medsToCheck,
        itemsUnverified = uncodedMeds + failedMeds,
    )
}
