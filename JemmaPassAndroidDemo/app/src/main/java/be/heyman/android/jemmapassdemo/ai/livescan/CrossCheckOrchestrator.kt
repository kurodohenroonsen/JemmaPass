/*
 * CrossCheckOrchestrator.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 3
 *
 * Phase C : après l'identification Phase B, ce service :
 *   1. Appelle KbCrossCheck.checkOneDrugAgainstProfile(bestCode, patient)
 *      → SQL pur, déterministe, auditable
 *   2. Adapte le `CrossCheckResult` legacy en notre `CrossCheckReport`
 *      unifié (Severity enum + 3 pillars sans duplicate + verdict KB :
 *      CLEAN / ALERT / INCOMPLETE / NOT_CHECKED — UC-SAFE-SCAN)
 *   3. Update LiveScanRepository : CrossChecking → RenderedWithVerdict
 *   4. Déclenche TtsStaticVerdict en parallèle (TTS immédiat)
 *   5. Retourne le report — caller (Fragment) déclenche ensuite Phase D
 *
 * Les 3 sorties parallèles s'orchestrent ainsi :
 *   • SHOW INTER badges → naturellement via state observer Fragment
 *   • TTS STATIC        → invoqué depuis cette classe
 *   • GEMMA EXPLAIN     → démarré par Fragment via GemmaExplainer (Lot 5)
 *
 * Log channel : JEMMA-XCHECK
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import be.heyman.android.jemmapassdemo.ai.medscan.VictimProfileSnapshot
import be.heyman.android.jemmapassdemo.kb.AllergyHit as LegacyAllergyHit
import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.DdiHit as LegacyDdiHit
import be.heyman.android.jemmapassdemo.kb.DrugDiseaseHit as LegacyDrugDiseaseHit
import be.heyman.android.jemmapassdemo.kb.KbCrossCheck
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.getDrugDisplayForAtc
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-XCHECK"

@Singleton
class CrossCheckOrchestrator @Inject constructor(
    private val kbCrossCheck: KbCrossCheck,
    private val kb: KnowledgeBaseService,
    private val liveScanRepo: LiveScanRepository,
    private val ttsStatic: TtsStaticVerdict,
) {

    /**
     * Performe Phase C end-to-end pour un drug + patient donnés.
     *
     * @param bestCode ATC identifié par Phase B
     * @param patient victime courante (lue depuis PatientContextHolder par le caller)
     * @param lang ISO 639-1 pour TTS
     * @return CrossCheckReport — aussi posté dans le state via markRendered
     */
    suspend fun runPhaseC(
        bestCode: String,
        patient: VictimProfileSnapshot,
        lang: String,
        // 🆕 FIX7 — Infos verbose à propager dans le state pour affichage juges
        lockReason: String = "",
        ocrFrames: List<String> = emptyList(),
        kbCandidates: List<Pair<String, String>> = emptyList(),
        phaseBDurationMs: Long = 0L,
    ): CrossCheckReport {
        val tStart = System.currentTimeMillis()

        // Resolve drug name (utile pour UI + TTS)
        val drugName = kb.getDrugDisplayForAtc(bestCode, lang)
        liveScanRepo.markCrossChecking(bestCode, drugName)
        Log.i(TAG, "[t=$tStart] 🩺 Phase C start · atc=$bestCode ($drugName)")

        // Run SQL cross-check via API legacy. checkOneDrugAgainstProfile
        // prend un *nom* (qu'il resolve en interne) — on lui passe l'ATC,
        // il sait gérer.
        val rawResult: CrossCheckResult = try {
            kbCrossCheck.checkOneDrugAgainstProfile(
                candidateName = bestCode,
                allergies = patient.allergies,
                meds = patient.medications,
                conditions = patient.conditions,
                lang = lang,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ checkOneDrugAgainstProfile threw", e)
            // UC-SAFE-SCAN — the check did not run : NOT_CHECKED, never an empty
            // "all checked" result (which the TTS would read as "no interaction").
            ScanSafety.notCheckedResult(bestCode, drugName)
        }

        val report = adaptToReport(rawResult, bestCode, drugName)

        val elapsed = System.currentTimeMillis() - tStart
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ Phase C done · verdict=${report.verdict} · overall=${report.overall} · " +
                "al=${report.allergyHits.size} ddi=${report.ddiHits.size} " +
                "cn=${report.conditionHits.size} · ${elapsed}ms",
        )

        // Build TTS phrase + transition state
        val ttsPhrase = ttsStatic.buildPhrase(report, lang)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔊 TTS phrase: \"$ttsPhrase\"")

        liveScanRepo.markRendered(
            LiveScanState.RenderedWithVerdict(
                bestCode = bestCode,
                drugName = drugName,
                crossCheckReport = report,
                ttsStaticPhrase = ttsPhrase,
                // 🆕 FIX7 verbose pour les juges
                lockReason = lockReason,
                ocrFrames = ocrFrames,
                kbCandidates = kbCandidates,
                phaseBDurationMs = phaseBDurationMs,
                phaseCDurationMs = elapsed,
            )
        )

        // Speak immédiatement — parallèle au kickoff Gemma explainer
        ttsStatic.speak(ttsPhrase, lang)

        return report
    }

    /**
     * Adapte le `CrossCheckResult` legacy en `CrossCheckReport` unifié.
     * Public car aussi appelé par `AlternativeFinder.findSafe` pendant
     * la cascade (re-cross-check de chaque candidate alternative).
     *
     * UC-SAFE-SCAN : le `verdict` du résultat KB est propagé tel quel, et
     * `overall` ne vaut NONE que pour un verdict CLEAN (un check non fait ou
     * partiel remonte au minimum en MODERATE — cf. [ScanSafety.displaySeverity]).
     */
    suspend fun adaptToReport(
        rawResult: CrossCheckResult,
        bestCode: String,
        drugName: String,
    ): CrossCheckReport {
        val allergyHits = rawResult.allergyHits.map { it.toUnified() }
        val ddiHits = rawResult.ddiHits.map { it.toUnified() }
        val conditionHits = rawResult.drugDiseaseHits.map { it.toUnified() }

        val all = listOf(
            allergyHits.map { it.severity },
            ddiHits.map { it.severity },
            conditionHits.map { it.severity },
        ).flatten()
        val verdict = rawResult.verdict
        val fullyChecked = rawResult.checked
        val overall = ScanSafety.displaySeverity(
            verdict = verdict,
            maxHitSeverity = all.maxByOrNull { it.ordinal },
            fullyChecked = fullyChecked,
        )

        return CrossCheckReport(
            drugAtc = bestCode,
            drugName = drugName,
            overall = overall,
            allergyHits = allergyHits,
            ddiHits = ddiHits,
            conditionHits = conditionHits,
            verdict = verdict,
            fullyChecked = fullyChecked,
        )
    }

    // ─── Adapters legacy → unified ──────────────────────────────────

    private fun LegacyAllergyHit.toUnified(): AllergyHit = AllergyHit(
        patientAllergyCode = allergyCode,
        patientAllergyName = allergyDisplay,
        crossReactiveClass = matchedOn,
        severity = criticality.toSeverity(),
        source = matchedOn,
    )

    private fun LegacyDdiHit.toUnified(): DdiHit = DdiHit(
        withDrugAtc = existingMedAtc,
        withDrugName = existingMedDisplay,
        severity = severity.toSeverity(),
        mechanism = mechanism,
        description = description,
    )

    private fun LegacyDrugDiseaseHit.toUnified(): ConditionHit = ConditionHit(
        conditionCode = conditionCode ?: "",
        conditionName = conditionDisplay,
        severity = severity.toSeverity(),
        source = diseaseNameMatched,
    )
}
