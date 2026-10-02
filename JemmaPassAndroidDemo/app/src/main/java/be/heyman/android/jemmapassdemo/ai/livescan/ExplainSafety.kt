/*
 * ExplainSafety.kt — JEMMA Pass · Live Scan explainer · UC-SAFE-SCAN-3x
 *
 * Pure-Kotlin safety fields (no Android / LiteRT class, JVM unit-testable) added
 * to the JSON that `getInteractionsForCurrentPatient` hands to the LLM. Without
 * them an empty hit list reads as "nothing found" even when the check did not
 * (fully) run, and the explanation could claim the drug is safe.
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict

object ExplainSafety {

    const val INSTRUCTION_CLEAN =
        "Fully checked against the patient profile : no allergy, interaction or condition collision found."
    const val INSTRUCTION_ALERT = "Explain the hits found."
    const val INSTRUCTION_ALERT_INCOMPLETE =
        "Explain the hits found. The check is also INCOMPLETE : add that the rest could not be verified " +
            "and to ask a pharmacist or a doctor."
    const val INSTRUCTION_INCOMPLETE =
        "The safety check is INCOMPLETE : part of the profile could not be verified. Do NOT say the drug " +
            "is safe, clean or OK. Say the check could not be completed and to ask a pharmacist or a doctor."
    const val INSTRUCTION_NOT_CHECKED =
        "The safety check could NOT be done for this drug. Do NOT say the drug is safe, clean or OK. " +
            "Say the check could not be done and to ask a pharmacist or a doctor."

    fun instruction(verdict: KbSafetyVerdict, fullyChecked: Boolean): String = when (verdict) {
        KbSafetyVerdict.ALERT -> if (fullyChecked) INSTRUCTION_ALERT else INSTRUCTION_ALERT_INCOMPLETE
        // CLEAN without a full check is contradictory : fail safe.
        KbSafetyVerdict.CLEAN -> if (fullyChecked) INSTRUCTION_CLEAN else INSTRUCTION_INCOMPLETE
        KbSafetyVerdict.INCOMPLETE -> INSTRUCTION_INCOMPLETE
        KbSafetyVerdict.NOT_CHECKED -> INSTRUCTION_NOT_CHECKED
    }

    /** Safety fields of the report JSON. `is_clean` only for a fully checked CLEAN verdict. */
    fun safetyFields(verdict: KbSafetyVerdict, fullyChecked: Boolean): Map<String, Any> = mapOf(
        "verdict" to verdict.name,
        "checked" to fullyChecked,
        "is_clean" to (verdict == KbSafetyVerdict.CLEAN && fullyChecked),
        "instruction" to instruction(verdict, fullyChecked),
    )

    /** Safety fields of an error answer (no patient, exception) : nothing was checked. */
    fun failureFields(reason: String): Map<String, Any> =
        mapOf("error" to reason) + safetyFields(KbSafetyVerdict.NOT_CHECKED, fullyChecked = false)
}
