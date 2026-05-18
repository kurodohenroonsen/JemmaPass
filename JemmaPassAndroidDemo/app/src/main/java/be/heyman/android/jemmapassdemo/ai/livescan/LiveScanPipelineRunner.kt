/*
 * LiveScanPipelineRunner.kt — JEMMA Pass · Live Scan v0.5 v4 · FIX4
 *
 * Singleton Hilt qui orchestre Phase B (DrugIdentifier) → Phase C
 * (CrossCheckOrchestrator) → Phase D (GemmaExplainer) en réagissant
 * aux transitions de state du LiveScanRepository.
 *
 * ─── Pourquoi un Singleton ───
 *
 * Avant FIX4 : le Fragment `JemmaLiveScanFragment` pilotait Phase B/C/D
 * via son `viewLifecycleOwner.lifecycleScope`. Problème : dès qu'on
 * `navigateUp()` vers `PatientDetailFragment`, le lifecycle Fragment
 * meurt → les coroutines Phase B/C/D sont cancelled → pipeline arrête.
 *
 * Après FIX4 : ce runner Singleton observe `liveScanRepo.state` dans son
 * propre `internalScope` (SupervisorJob, hors lifecycle Fragment). Les
 * phases B/C/D continuent à tourner même quand le Fragment LiveScan
 * est détruit par la nav back vers PatientDetail.
 *
 * Le `PatientDetailFragment` observe aussi `liveScanRepo.state` et
 * affiche un panneau qui reflète l'état courant (Phase C/D/verdict).
 *
 * ─── Idempotence ───
 *
 * `start()` est idempotent : si le runner observe déjà, on ne relance
 * pas. Appelé au `JemmaApplication.onCreate()` pour démarrer dès le
 * boot de l'app. (Le scope étant Singleton, le collect tourne pour
 * toute la durée de vie du process.)
 *
 * Log channel : JEMMA-PIPELINE
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "JEMMA-PIPELINE"

@Singleton
class LiveScanPipelineRunner @Inject constructor(
    private val liveScanRepo: LiveScanRepository,
    private val drugIdentifier: DrugIdentifier,
    private val crossCheckOrchestrator: CrossCheckOrchestrator,
    private val gemmaExplainer: GemmaExplainer,
    private val patientContextHolder: PatientContextHolder,
) {
    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observerJob: Job? = null

    @Volatile
    private var lastHandledStateClass: String? = null

    @Volatile
    private var currentLang: String = "en"

    /**
     * Appelé une fois au boot de l'app (JemmaApplication.onCreate).
     * Lance le state observer en background.
     */
    fun start() {
        if (observerJob != null && observerJob?.isActive == true) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ⏭️ already running")
            return
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚀 starting pipeline runner")

        observerJob = internalScope.launch {
            liveScanRepo.state
                // 🆕 v4.2 HOTFIX-RUNNER — Avant ce fix, `distinctUntilChanged
                // { old::class == new::class }` filtrait par TYPE Kotlin, ce qui
                // rejetait le 2e scan car IdentifyingWithGemma(scan1) et
                // IdentifyingWithGemma(scan2) ont la même classe. Conséquence :
                // Phase B jamais lancée pour scan 2, 3, 4...
                //
                // Pas besoin de re-appliquer `.distinctUntilChanged()` standard :
                // StateFlow fait DÉJÀ le distinct-by-equals nativement via
                // operator fusion (cf StateFlow doc). Le compilateur Kotlin
                // d'ailleurs marque `.distinctUntilChanged()` sur StateFlow
                // comme deprecated / no-op.
                //
                // Comme IdentifyingWithGemma est une data class avec payload +
                // startedAtMs, deux scans successifs produisent des valeurs
                // equals=false → re-émises naturellement → le collect re-lance
                // handleIdentifying pour chaque scan. ✓
                //
                // Effet secondaire bénin : un peu plus de logs '🎬 state transition'
                // pendant Phase A (Accumulating ré-émis à chaque frame OCR avec
                // counts différents). C'est OK pour debug.
                .collect { state ->
                    val stateName = state::class.simpleName
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎬 state transition → $stateName")

                    when (state) {
                        is LiveScanState.IdentifyingWithGemma -> {
                            handleIdentifying(state)
                        }
                        else -> {
                            // Autres transitions sont gérées en cascade depuis
                            // handleIdentifying (Phase B → C → D enchaîné).
                        }
                    }
                    lastHandledStateClass = stateName
                }
        }
    }

    /**
     * Le Fragment LiveScan passe la langue UI courante avant de naviguer.
     * Le runner l'utilise pour les phases B/C/D backend.
     */
    fun setUiLang(lang: String) {
        currentLang = lang
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌐 lang set → $lang")
    }

    private suspend fun handleIdentifying(state: LiveScanState.IdentifyingWithGemma) {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🧠 Phase B start · " +
                "candidates=${state.payload.candidates.size} · " +
                "ocrTexts=${state.payload.ocrTexts.size}",
        )

        // Phase B
        val bestCode = try {
            drugIdentifier.identify(state.payload, currentLang)
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Phase B threw", e)
            liveScanRepo.markError("Identification failed: ${e.message}")
            return
        }

        if (bestCode == "UNKNOWN") {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ Gemma returned UNKNOWN")
            liveScanRepo.markError("Drug not identified")
            return
        }

        val patient = patientContextHolder.current ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ patient context lost")
            liveScanRepo.markError("Patient context lost during scan")
            return
        }

        // Phase C
        try {
            // 🆕 FIX7 — Propage les infos verbose pour affichage juges
            crossCheckOrchestrator.runPhaseC(
                bestCode = bestCode,
                patient = patient,
                lang = currentLang,
                lockReason = drugIdentifier.lastReason,
                ocrFrames = state.payload.ocrTexts,
                kbCandidates = state.payload.candidates
                    .sortedByDescending { it.frequency }
                    .map { it.atc to it.displayName },
                phaseBDurationMs = drugIdentifier.lastPhaseBDurationMs,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Phase C threw", e)
            liveScanRepo.markError("Cross-check failed: ${e.message}")
            return
        }

        // Phase C a transitionné le state vers RenderedWithVerdict.
        // Récupère le drugName depuis le state pour Phase D.
        val verdictState = liveScanRepo.state.value as? LiveScanState.RenderedWithVerdict ?: run {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ unexpected state after Phase C: " +
                    "${liveScanRepo.state.value::class.simpleName}",
            )
            return
        }

        // Phase D streaming
        try {
            gemmaExplainer.explain(bestCode, verdictState.drugName, currentLang)
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Phase D threw", e)
            liveScanRepo.markExplanationDone(safeAlt = null)
        }
    }
}
