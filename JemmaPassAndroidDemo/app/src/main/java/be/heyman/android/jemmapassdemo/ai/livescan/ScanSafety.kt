/*
 * ScanSafety.kt — JEMMA Pass · Live Scan · UC-SAFE-SCAN
 *
 * Pure-Kotlin decision logic of the scan cross-check (no Android class, JVM
 * unit-testable) : what the banner colour and the spoken verdict are allowed
 * to be for a given cross-check outcome.
 *
 * Rule : "no interaction / safe" is reachable ONLY through
 * [KbSafetyVerdict.CLEAN]. A check that threw, could not reach the knowledge
 * base, did not recognise the drug or only partly ran is told as "could not
 * be checked — ask a pharmacist or a doctor".
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.KbCheckReport
import be.heyman.android.jemmapassdemo.kb.KbCheckStatus
import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict

object ScanSafety {

    /** Which pre-written sentence the TTS / banner uses. */
    enum class Phrase {
        /** "No interactions detected." — CLEAN only. */
        SAFE,

        /** Nothing was verified : say so and send to a pharmacist / doctor. */
        NOT_CHECKED,

        /** Partly verified, nothing found in the part that ran : same advice. */
        INCOMPLETE,

        MAJOR_ALLERGY,
        MAJOR_DDI,
        MAJOR_CONDITION,
        MODERATE_ALLERGY,
        MODERATE_DDI,
        MODERATE_CONDITION,
        MINOR,
    }

    /**
     * @param phrase                main sentence
     * @param arg                   name inserted in a hit sentence (allergen / drug / condition), else null
     * @param appendIncompleteNote  a hit was found but the check did not fully run : the hit
     *                              sentence is followed by "the check was incomplete, ask…"
     */
    data class PhrasePlan(
        val phrase: Phrase,
        val arg: String? = null,
        val appendIncompleteNote: Boolean = false,
    )

    /**
     * Result used when the cross-check threw : nothing was verified, so every
     * pillar is KB_UNAVAILABLE and the verdict is NOT_CHECKED (never CLEAN).
     */
    fun notCheckedResult(candidateAtc: String, candidateDisplay: String): CrossCheckResult =
        CrossCheckResult(
            candidateAtc = candidateAtc,
            candidateDisplay = candidateDisplay,
            allergyHits = emptyList(),
            ddiHits = emptyList(),
            drugDiseaseHits = emptyList(),
            totalDurationMs = 0L,
            checks = KbCheckReport.all(KbCheckStatus.KB_UNAVAILABLE),
        )

    /**
     * Severity shown by the UI (banner colour) and read by the alternative
     * cascade. [Severity.NONE] (green) is returned only for a CLEAN verdict ;
     * anything not fully verified is at least [Severity.MODERATE], so readers
     * that only look at `overall` cannot turn "not checked" into "safe".
     *
     * @param maxHitSeverity worst severity among the hits, null when there is none
     * @param fullyChecked   the drug was recognised and the three pillars really ran
     */
    fun displaySeverity(
        verdict: KbSafetyVerdict,
        maxHitSeverity: Severity?,
        fullyChecked: Boolean,
    ): Severity = when (verdict) {
        KbSafetyVerdict.CLEAN ->
            if (fullyChecked && maxHitSeverity == null) Severity.NONE else Severity.MODERATE
        KbSafetyVerdict.ALERT -> {
            val floor = if (fullyChecked) Severity.MINOR else Severity.MODERATE
            val hit = maxHitSeverity ?: floor
            if (hit.ordinal >= floor.ordinal) hit else floor
        }
        KbSafetyVerdict.INCOMPLETE,
        KbSafetyVerdict.NOT_CHECKED -> {
            val hit = maxHitSeverity ?: Severity.MODERATE
            if (hit.ordinal >= Severity.MODERATE.ordinal) hit else Severity.MODERATE
        }
    }

    /** Sentence selection for a report. SAFE only when the report verdict is CLEAN and it has no hit. */
    fun phrasePlan(report: CrossCheckReport): PhrasePlan {
        data class HitRef(val severity: Severity, val kind: String, val name: String)

        val candidates = mutableListOf<HitRef>()
        report.allergyHits.forEach { candidates.add(HitRef(it.severity, "allergy", it.patientAllergyName)) }
        report.ddiHits.forEach { candidates.add(HitRef(it.severity, "ddi", it.withDrugName)) }
        report.conditionHits.forEach { candidates.add(HitRef(it.severity, "condition", it.conditionName)) }

        val worst = candidates.maxByOrNull { it.severity.ordinal }
            ?: return when (report.verdict) {
                KbSafetyVerdict.CLEAN -> PhrasePlan(Phrase.SAFE)
                KbSafetyVerdict.INCOMPLETE -> PhrasePlan(Phrase.INCOMPLETE)
                // ALERT without any hit is not a coherent report : treat as not verified.
                KbSafetyVerdict.NOT_CHECKED,
                KbSafetyVerdict.ALERT -> PhrasePlan(Phrase.NOT_CHECKED)
            }

        val phrase = when {
            worst.severity == Severity.MAJOR && worst.kind == "allergy" -> Phrase.MAJOR_ALLERGY
            worst.severity == Severity.MAJOR && worst.kind == "ddi" -> Phrase.MAJOR_DDI
            worst.severity == Severity.MAJOR && worst.kind == "condition" -> Phrase.MAJOR_CONDITION
            worst.severity == Severity.MODERATE && worst.kind == "allergy" -> Phrase.MODERATE_ALLERGY
            worst.severity == Severity.MODERATE && worst.kind == "ddi" -> Phrase.MODERATE_DDI
            worst.severity == Severity.MODERATE && worst.kind == "condition" -> Phrase.MODERATE_CONDITION
            else -> Phrase.MINOR
        }
        return PhrasePlan(phrase, worst.name, appendIncompleteNote = !report.fullyChecked)
    }
}
