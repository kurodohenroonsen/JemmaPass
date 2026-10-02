/*
 * MedScanStepSafety.kt — JEMMA Pass · med scan agent · UC-SAFE-SCAN-3x
 *
 * Pure-Kotlin decision (no Android / LiteRT class, JVM unit-testable) of what
 * the CROSS_CHECK timeline step and the final banner may show once the Gemma
 * agent has answered.
 *
 * The agent is free to skip the `checkInteractions` tool. "The model returned
 * some text" therefore proves nothing : the step is OK only when the tool was
 * really invoked during THIS scan AND its last answer was a full check
 * (CLEAN) or found collisions (ALERT). Everything else is NOT VERIFIED and
 * the agent's prose (which may say "safe") must not be relayed.
 */
package be.heyman.android.jemmapassdemo.ai.medscan

import be.heyman.android.jemmapassdemo.kb.KbSafetyVerdict

/** What really happened to the safety cross-check during one scan. */
enum class CrossCheckOutcome {
    /** Tool called, fully checked, nothing found. */
    VERIFIED_CLEAN,

    /** Tool called, at least one collision found. */
    VERIFIED_ALERT,

    /** Tool called, but only part of the profile could be verified and nothing was found in that part. */
    INCOMPLETE,

    /** Tool called, but nothing could be verified (no KB, unknown drug, exception, blank ATC). */
    NOT_CHECKED,

    /** The model never called the tool during this scan. */
    NOT_RUN,
}

data class CrossCheckStepDecision(
    val outcome: CrossCheckOutcome,
    /** Lifecycle of the CROSS_CHECK timeline row. OK only for a verified outcome. */
    val lifecycle: StepLifecycle,
    /** Detail shown on the CROSS_CHECK timeline row. */
    val detail: String,
    /**
     * True only when the agent's free text is backed by a real check result
     * (CLEAN or ALERT). When false the text must NOT be shown as the verdict.
     */
    val agentTextTrusted: Boolean,
) {
    val verified: Boolean get() = agentTextTrusted
}

object MedScanStepSafety {

    const val DETAIL_CLEAN = "via @Tool checkInteractions · checked"
    const val DETAIL_ALERT = "via @Tool checkInteractions · alert"
    const val DETAIL_INCOMPLETE = "NOT VERIFIED · check incomplete"
    const val DETAIL_NOT_CHECKED = "NOT VERIFIED · check could not be done"
    const val DETAIL_NOT_RUN = "NOT VERIFIED · safety check was not run"

    const val AGENT_DETAIL_UNTRUSTED = "Answer discarded : safety check not verified"

    /**
     * @param invocationsBefore  `CheckInteractionsTool.invocationCount` before the agent call
     * @param invocationsAfter   same counter once the agent answered
     * @param lastVerdict        `CheckInteractionsTool.lastVerdict` once the agent answered
     */
    fun decide(
        invocationsBefore: Int,
        invocationsAfter: Int,
        lastVerdict: KbSafetyVerdict?,
    ): CrossCheckStepDecision {
        val calledDuringScan = invocationsAfter > invocationsBefore
        if (!calledDuringScan) {
            // A verdict left over from an earlier call does not count for this scan.
            return CrossCheckStepDecision(CrossCheckOutcome.NOT_RUN, StepLifecycle.FAIL, DETAIL_NOT_RUN, false)
        }
        return when (lastVerdict) {
            KbSafetyVerdict.CLEAN ->
                CrossCheckStepDecision(CrossCheckOutcome.VERIFIED_CLEAN, StepLifecycle.OK, DETAIL_CLEAN, true)
            KbSafetyVerdict.ALERT ->
                CrossCheckStepDecision(CrossCheckOutcome.VERIFIED_ALERT, StepLifecycle.OK, DETAIL_ALERT, true)
            KbSafetyVerdict.INCOMPLETE ->
                CrossCheckStepDecision(CrossCheckOutcome.INCOMPLETE, StepLifecycle.WARN, DETAIL_INCOMPLETE, false)
            KbSafetyVerdict.NOT_CHECKED, null ->
                CrossCheckStepDecision(CrossCheckOutcome.NOT_CHECKED, StepLifecycle.FAIL, DETAIL_NOT_CHECKED, false)
        }
    }

    /**
     * Text shown to the rescuer instead of the agent's prose when the check is not
     * verified. Never contains a "safe" wording ; always points to a pharmacist / doctor.
     * Falls back to English for a language without a translation here.
     */
    fun notVerifiedNotice(lang: String, outcome: CrossCheckOutcome): String {
        val incomplete = outcome == CrossCheckOutcome.INCOMPLETE
        return when (lang.trim().lowercase().substringBefore('-').substringBefore('_')) {
            "fr" ->
                if (incomplete) {
                    "NON VÉRIFIÉ : la vérification de sécurité n'a pas pu être terminée. " +
                        "Demandez à un pharmacien ou à un médecin avant de donner ce médicament."
                } else {
                    "NON VÉRIFIÉ : la vérification de sécurité n'a pas pu être faite. " +
                        "Demandez à un pharmacien ou à un médecin avant de donner ce médicament."
                }
            "nl" ->
                if (incomplete) {
                    "NIET GECONTROLEERD: de veiligheidscontrole kon niet worden voltooid. " +
                        "Vraag een apotheker of arts voordat u dit geneesmiddel geeft."
                } else {
                    "NIET GECONTROLEERD: de veiligheidscontrole kon niet worden uitgevoerd. " +
                        "Vraag een apotheker of arts voordat u dit geneesmiddel geeft."
                }
            "de" ->
                if (incomplete) {
                    "NICHT GEPRÜFT: Die Sicherheitsprüfung konnte nicht abgeschlossen werden. " +
                        "Fragen Sie einen Apotheker oder Arzt, bevor Sie dieses Medikament geben."
                } else {
                    "NICHT GEPRÜFT: Die Sicherheitsprüfung konnte nicht durchgeführt werden. " +
                        "Fragen Sie einen Apotheker oder Arzt, bevor Sie dieses Medikament geben."
                }
            "es" ->
                if (incomplete) {
                    "NO VERIFICADO: la comprobación de seguridad no se pudo completar. " +
                        "Consulte a un farmacéutico o a un médico antes de dar este medicamento."
                } else {
                    "NO VERIFICADO: la comprobación de seguridad no se pudo realizar. " +
                        "Consulte a un farmacéutico o a un médico antes de dar este medicamento."
                }
            "ja" ->
                if (incomplete) {
                    "未確認：安全性チェックを完了できませんでした。この薬を投与する前に薬剤師または医師に確認してください。"
                } else {
                    "未確認：安全性チェックを実行できませんでした。この薬を投与する前に薬剤師または医師に確認してください。"
                }
            else ->
                if (incomplete) {
                    "NOT VERIFIED: the safety check could not be completed. " +
                        "Ask a pharmacist or a doctor before giving this medication."
                } else {
                    "NOT VERIFIED: the safety check could not be done. " +
                        "Ask a pharmacist or a doctor before giving this medication."
                }
        }
    }
}
