/*
 * MedScanSafety.kt — JEMMA Pass · med scan agent · UC-SAFE-SCAN
 *
 * Pure-Kotlin decision logic behind the `checkInteractions` @Tool answer
 * (no Android / LiteRT class, JVM unit-testable).
 *
 * The agent prompt renders `severity_overall == "NONE"` as "safe to
 * administer". "NONE" is therefore emitted ONLY for a CLEAN verdict ; a check
 * that threw, had no knowledge base, did not recognise the drug or only
 * partly ran answers "NOT_CHECKED" / "INCOMPLETE" together with an explicit
 * instruction to say the check could not be done and to ask a pharmacist or
 * a doctor.
 */
package be.heyman.android.jemmapassdemo.ai.gemma

import be.heyman.android.jemmapassdemo.kb.AllergyHit
import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.DdiHit
import be.heyman.android.jemmapassdemo.kb.DrugDiseaseHit
import be.heyman.android.jemmapassdemo.kb.KbCheckReport
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafety
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict
import be.heyman.android.jemmapassdemo.kb.PillarCheck

object MedScanSafety {

    const val SEVERITY_MAJOR = "MAJOR"
    const val SEVERITY_MODERATE = "MODERATE"
    const val SEVERITY_NONE = "NONE"
    const val SEVERITY_INCOMPLETE = "INCOMPLETE"
    const val SEVERITY_NOT_CHECKED = "NOT_CHECKED"

    const val REASON_CHECK_FAILED = "check_failed"
    const val REASON_BLANK_ATC = "atc_code_blank"

    const val INSTRUCTION_NOT_CHECKED =
        "The safety check could NOT be done for this medication. Do NOT say it is safe, clean or OK. " +
            "Tell the user, in victim_lang, that the check could not be done and to ask a pharmacist " +
            "or a doctor before giving it."
    const val INSTRUCTION_INCOMPLETE =
        "The safety check is INCOMPLETE : part of the profile could not be verified. Do NOT say it is " +
            "safe, clean or OK. Tell the user, in victim_lang, that the check could not be completed and " +
            "to ask a pharmacist or a doctor before giving it."
    const val INSTRUCTION_ALERT_INCOMPLETE =
        "Report the hits found. The check is also INCOMPLETE : add that the rest could not be verified " +
            "and to ask a pharmacist or a doctor."
    const val INSTRUCTION_ALERT = "Report the hits found."
    const val INSTRUCTION_CLEAN =
        "Fully checked against the victim profile : no allergy, interaction or condition collision found."

    /**
     * Bundle the three pillar checks of the tool into a [CrossCheckResult].
     *
     * @param candidateResolved  the ATC was found in the knowledge base
     * @param kbAvailable        the knowledge base could be opened
     * @param ancestorsResolved  the ATC class chain was read (false = class-level allergy
     *                           matches may have been missed → allergy pillar is INCOMPLETE)
     */
    fun bundle(
        atc: String,
        display: String,
        candidateResolved: Boolean,
        kbAvailable: Boolean,
        ancestorsResolved: Boolean,
        allergy: PillarCheck<AllergyHit>,
        ddi: PillarCheck<DdiHit>,
        disease: PillarCheck<DrugDiseaseHit>,
        durationMs: Long = 0L,
        allergiesInProfile: Int = 0,
    ): CrossCheckResult {
        val checks = if (!kbAvailable) {
            KbCheckReport.all(KbCheckStatus.KB_UNAVAILABLE)
        } else {
            val allergyStatus = if (!ancestorsResolved && allergiesInProfile > 0) {
                KbSafety.worst(allergy.status, KbCheckStatus.INCOMPLETE)
            } else {
                allergy.status
            }
            KbCheckReport(allergyStatus, ddi.status, disease.status)
        }
        return CrossCheckResult(
            // blank ATC = "candidate not resolved" for CrossCheckResult.verdict → NOT_CHECKED
            candidateAtc = if (candidateResolved && kbAvailable) atc else "",
            candidateDisplay = display,
            allergyHits = allergy.hits,
            ddiHits = ddi.hits,
            drugDiseaseHits = disease.hits,
            totalDurationMs = durationMs,
            checks = checks,
        )
    }

    /** `severity_overall` of the tool answer. "NONE" only for a CLEAN verdict. */
    fun severityOverall(r: CrossCheckResult): String = when (r.verdict) {
        KbSafetyVerdict.ALERT ->
            if (r.allergyHits.isNotEmpty() || r.hasMajor) SEVERITY_MAJOR else SEVERITY_MODERATE
        KbSafetyVerdict.CLEAN -> SEVERITY_NONE
        KbSafetyVerdict.INCOMPLETE -> SEVERITY_INCOMPLETE
        KbSafetyVerdict.NOT_CHECKED -> SEVERITY_NOT_CHECKED
    }

    /** What the LLM must tell the user for this result. */
    fun instruction(r: CrossCheckResult): String = when (r.verdict) {
        KbSafetyVerdict.ALERT -> if (r.checked) INSTRUCTION_ALERT else INSTRUCTION_ALERT_INCOMPLETE
        KbSafetyVerdict.CLEAN -> INSTRUCTION_CLEAN
        KbSafetyVerdict.INCOMPLETE -> INSTRUCTION_INCOMPLETE
        KbSafetyVerdict.NOT_CHECKED -> INSTRUCTION_NOT_CHECKED
    }

    /**
     * Safety fields of the tool answer : the shared KB fields (`verdict`, `is_clean`,
     * `checked`, `kb_available`, per-pillar status, `warning`…) plus `severity_overall`
     * and the `instruction` for the LLM.
     */
    fun safetyFields(r: CrossCheckResult): Map<String, Any> =
        KbSafety.crossCheckSafetyFields(r) + mapOf(
            "severity_overall" to severityOverall(r),
            "instruction" to instruction(r),
        )

    /** Tool answer fields when the check itself failed (exception, blank ATC) : NOT_CHECKED. */
    fun failureFields(reason: String): Map<String, Any> = mapOf(
        "ok" to false,
        "error" to reason,
        "reason" to reason,
        "is_clean" to false,
        "checked" to false,
        "verdict" to KbSafetyVerdict.NOT_CHECKED.name,
        "severity_overall" to SEVERITY_NOT_CHECKED,
        "warning" to INSTRUCTION_NOT_CHECKED,
        "instruction" to INSTRUCTION_NOT_CHECKED,
        "total_hits" to 0,
    )
}
