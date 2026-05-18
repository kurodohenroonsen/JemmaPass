/*
 * JemmaLiveScanFragment.kt — JEMMA Pass · Live Scan v0.5 · v4 FULL
 *
 * 🆕 v0.5 v4 FULL (2026-05-12 soir) — Refonte complète :
 *   • Repo refactor : handleOcrFrame(rawText, bitmap, lang)
 *   • Plus de scoring/auto-lock Kotlin
 *   • States : Accumulating → IdentifyingWithGemma → CrossChecking
 *             → RenderedWithVerdict → (NoSafeFound | Error)
 *   • PatientContextHolder setCurrent dans onViewCreated
 *   • Phase B : DrugIdentifier (Gemma vision)
 *   • Phase C : CrossCheckOrchestrator (SQL pur + TTS static)
 *   • Phase D : GemmaExplainer (streaming + cascade alternatives)
 *
 * Pipeline complet :
 *   tap scan → CameraX → OCR streaming → handleOcrFrame
 *      → trigger threshold (4 OCR avec codes OR 4 distinct codes)
 *      → DrugIdentifier.identify(payload, lang) [8-15s Gemma vision]
 *      → CrossCheckOrchestrator.runPhaseC [<500ms SQL + TTS speak]
 *      → GemmaExplainer.explain [5-15s streaming + cascade]
 *      → state RenderedWithVerdict full → render badges + safe alt
 *
 * Le legacy MedScanController reste intouché — coexistence garantie.
 * Aucun handoff direct via medScanController.startWithImage en v4.
 *
 * Pipeline complet maintenant :
 *
 *   tap "scan med"
 *      ↓
 *   findNavController().navigate(action_patient_to_live_scan, peerSid)
 *      ↓
 *   JemmaLiveScanFragment opens
 *      ├─ liveScanRepo.reset()
 *      ├─ medScanController.configureGemmaForRescueMode()  ← prime cache
 *      ├─ observeRepoState() launches (debounce 80ms)
 *      └─ startCamera() → preview + analysis + capture bound
 *      ↓
 *   ImageAnalysis ~10fps
 *      ↓
 *   TextRecognizer.process → handleOcrFrame → FTS5 double-query
 *      ↓
 *   state = Searching(drugs)
 *      ↓ debounce 80ms
 *   overlay.renderSearching → chips visible
 *      ↓ trigger threshold (4 OCR avec codes OR 4 distinct codes)
 *   eligible drug found (seenCount≥3, ≥1.5s, conf≥0.70)
 *      ↓
 *   state = Locked(drug)
 *      ↓
 *   DrugIdentifier.identify (Gemma vision)
 *      ↓ delay 800ms (annulable)
 *   CrossCheckOrchestrator.runPhaseC (SQL pur + TTS speak)
 *      ↓ imageCapture.takePicture
 *   onImageSaved(uri)
 *      ↓
 *   medScanController.startWithImage(uri, victimSnapshot, uiLang)
 *      ├─ MedScanController runs : OCR Gemma 4 multimodal + @Tool calls
 *      └─ MedScanController._state emits as it goes
 *      ↓
 *   GemmaExplainer.explain (streaming + cascade)
 *      ↓
 *   observer reçoit HandedOff → findNavController().navigateUp()
 *      ↓
 *   PatientDetailFragment re-resume STARTED
 *      ↓
 *   medScanController.state.collect (already wired since Lot 0)
 *      ↓
 *   bottom sheet attaché → verdict Gemma rendu dans la langue victime
 *      ↓
 *   🐢 fin pipeline
 *
 * 🔙 v0.3 Lot 3 (2026-05-12 PM) :
 *   • LiveScanOverlayView injectée dans le layout : chips bottom +
 *     lock frame center + hint banner dynamique
 *   • observeRepoState() : collect du StateFlow repo → délègue à
 *     overlay.renderXxx(state) selon la classe sealed
 *   • Anti-flicker : debounce(80ms) sur le flow (Piège #2 agent forge)
 *   • 🆕 v4 — Trigger threshold détecté par le repo après chaque
 *     handleOcrFrame() success (le repo accumule jusqu'au seuil)
 *   • Plus de tap chip — Gemma vision décide en Phase B
 *
 * 🔙 v0.2 (2026-05-12 PM) — Hotfixes post-feedback agent forge DIAMOND :
 *   • inject MedScanController et appel configureGemmaForRescueMode()
 *     dans onViewCreated (Q5 — idempotent, prime le Gemma session cache)
 *   • guard viewLifecycleOwnerLiveData.value dans le callback OCR
 *     (Piège #1 — évite IllegalStateException si TextRecognizer
 *     callback arrive après onDestroyView)
 *
 * Remplaçant offline-first du flow GmsDocumentScanner modal dans Rescue
 * Mode. Tu pointes la caméra vers une boîte de médicament, l'OCR
 * streame en continu, le repo fait des lookups FTS5 sub-2ms sur la
 * KB DIAMOND, et on affiche les drug candidates en temps réel.
 *
 * ─── Scope Lot 2 (ce fichier dans cette version) ─────────────────────
 *
 *   ✅ CameraX bind : Preview + ImageAnalysis (OCR streaming)
 *      ImageCapture (full-res JPEG) est *préparé* mais pas encore
 *      déclenché — on l'utilisera en Lot 4 pour le handoff.
 *   ✅ TextRecognizer Hilt-injected (JA + multilingual latin via
 *      OcrModule.kt)
 *   ✅ ImageAnalysis ≈ 10-15 fps, backpressure KEEP_ONLY_LATEST
 *   ✅ LiveScanRepository.handleOcrFrame(rawText, bitmap, lang) à chaque
 *      frame OCR success
 *   ✅ Victim snapshot built en onViewCreated (parsing inline pour
 *      ne pas dépendre d'un PatientDetailFragment helper)
 *   ✅ Cancel button → navigateUp
 *   ✅ liveScanRepo.reset() en onViewCreated ET onDestroyView
 *
 *   ⏳ Lot 3 : LiveScanOverlayView (chips + lock frame) +
 *      observer state → render UI
 *   ⏳ Lot 4 : capture HD + handoff vers MedScanController + nav
 *
 * ─── Pourquoi 2 use cases (ImageAnalysis + ImageCapture) ──────────────
 *
 * On bind les deux dès Lot 2 même si on n'utilise pas encore
 * ImageCapture, parce que CameraX rebind = reconfigure le pipeline
 * caméra. Faire `bindToLifecycle(preview, analysis)` au Lot 2 puis
 * `bindToLifecycle(preview, analysis, capture)` au Lot 4 forcerait
 * un re-bind. Plus simple de binder les trois dès le début.
 *
 *   • Preview        → PreviewView, ce que voit l'user
 *   • ImageAnalysis  → frames YUV ~640×480, alimentent TextRecognizer
 *   • ImageCapture   → JPEG full-res, alimentera Gemma 4 multimodal
 *
 * ─── Permission caméra ────────────────────────────────────────────────
 *
 * La perm CAMERA est déjà runtime-granted par PermissionsFragment au
 * boot de l'app (cf. AndroidManifest et flow rescue mode standard). On
 * fait quand même un guard checkSelfPermission au cas où, et fallback
 * navigateUp + Toast si manquante.
 *
 * Log channel : JEMMA-LIVESCAN
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.medscan.MedScanController
import be.heyman.android.jemmapassdemo.ai.medscan.VictimProfileSnapshot
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JCondition
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.radar.RadarController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@AndroidEntryPoint
class JemmaLiveScanFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-LIVESCAN"

        /** NavArg : nav_graph passe peerSid pour qu'on retrouve la victime. */
        const val ARG_PEER_SID = "peerSid"

        /**
         * 🆕 v0.3 Lot 3 — Délai de debounce sur le state flow de l'overlay.
         * Évite le flicker du banner quand 2 coroutines handleOcrFrame
         * parallèles émettent rapidement. 80ms est sous le seuil de
         * perception humaine (~100ms) donc les transitions Locked/
         * Capturing restent "instantanées" perceptuellement.
         */
        private const val STATE_DEBOUNCE_MS = 80L
    }

    // ─── Hilt-injected singletons ────────────────────────────────────

    @Inject lateinit var textRecognizer: TextRecognizer
    @Inject lateinit var liveScanRepo: LiveScanRepository
    @Inject lateinit var radar: RadarController

    /**
     * 🆕 v0.2 — Injecté pour appeler `configureGemmaForRescueMode()`
     * dans onViewCreated. Pas encore consommé pour `startWithImage()` —
     * ça vient en Lot 4 (handoff).
     */
    @Inject lateinit var medScanController: MedScanController

    // ─── 🆕 v4 FIX4 — Pipeline runner (Singleton, survit nav back) ───
    @Inject lateinit var patientContextHolder: PatientContextHolder
    @Inject lateinit var ttsStatic: TtsStaticVerdict
    @Inject lateinit var pipelineRunner: LiveScanPipelineRunner

    // ─── Views + camera state ────────────────────────────────────────

    private lateinit var previewView: PreviewView
    private lateinit var overlay: LiveScanOverlayView
    private lateinit var cameraExecutor: ExecutorService

    /** Préparé en Lot 2, utilisé en Lot 4 pour la capture HD. */
    private var imageCapture: ImageCapture? = null

    /**
     * Guard contre l'overlap d'OCR : si une frame est en cours
     * d'analyse, on close immédiatement les nouvelles frames sans
     * les feed au TextRecognizer (pour éviter de saturer ML Kit).
     * STRATEGY_KEEP_ONLY_LATEST nous donne déjà la frame la plus
     * récente, donc on ne perd rien.
     */
    private val isAnalyzing = AtomicBoolean(false)

    /** Compteur de frames OCR pour debug + future ocrFps. */
    private var ocrFrameCount: Long = 0L
    private var firstFrameAtMs: Long = 0L

    // ─── NavArgs + context ───────────────────────────────────────────

    private val peerSid: String by lazy { arguments?.getString(ARG_PEER_SID) ?: "" }

    /** Langue UI du rescuer (pour le display localisé des hits FTS5). */
    private val uiLang: String by lazy {
        Locale.getDefault().language.take(2).ifBlank { "en" }
    }

    /** Snapshot victime — capturé une fois à onViewCreated, utilisé au handoff (Lot 4). */
    private var victimSnapshot: VictimProfileSnapshot? = null

    // ─── Lifecycle ───────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · peerSid=$peerSid")
        return inflater.inflate(R.layout.fragment_jemma_live_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📋 onViewCreated · peerSid=$peerSid · " +
                "uiLang=$uiLang · activity=${activity?.javaClass?.simpleName}",
        )

        previewView = view.findViewById(R.id.livescan_preview)
        overlay = view.findViewById(R.id.livescan_overlay)
        view.findViewById<View>(R.id.livescan_cancel_btn).setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 CANCEL tap")
            findNavController().navigateUp()
        }

        if (!hasCameraPermission()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ camera permission missing — going back")
            Toast.makeText(
                requireContext(),
                getString(R.string.livescan_perm_missing),
                Toast.LENGTH_LONG,
            ).show()
            findNavController().navigateUp()
            return
        }

        victimSnapshot = buildVictimSnapshotFromPeerSid(peerSid)
        if (victimSnapshot == null) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ victim snapshot null · sid=$peerSid",
            )
            Toast.makeText(
                requireContext(),
                getString(R.string.livescan_victim_not_found),
                Toast.LENGTH_SHORT,
            ).show()
            findNavController().navigateUp()
            return
        }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✓ victim '${victimSnapshot!!.displayName}' · " +
                "al=${victimSnapshot!!.allergies.size} " +
                "md=${victimSnapshot!!.medications.size} " +
                "cn=${victimSnapshot!!.conditions.size}",
        )

        // 🆕 v4 — Set patient context (lu par les tools Phase D backend)
        patientContextHolder.setCurrent(victimSnapshot!!)

        // 🆕 v4 FIX4 — Start le pipeline runner (idempotent, survit aux navs)
        // et lui passer la lang UI pour Phase B/C/D.
        pipelineRunner.start()
        pipelineRunner.setUiLang(uiLang)

        // 🆕 v4 — Init TTS engine (async — ready callback peut tarder
        // de 100-500ms. La phrase TTS sera prête en Phase C ~10-15s
        // après, donc largement le temps pour TTS d'être ready.)
        ttsStatic.init {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔊 TTS ready callback")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Reset le repo dès l'entrée — purge l'éventuel état d'une
        // session précédente qui n'aurait pas été reset proprement.
        //
        // 🆕 v0.2 — Prime aussi la Gemma session avec le system prompt
        // rescue. L'agent forge a confirmé que l'appel est idempotent
        // et qu'il prime le cache de GemmaSession.configureForTask
        // pour le handoff Lot 4. Ne pas appeler clearGemmaTaskConfig
        // dans onDestroyView — risque de race avec MedScanController
        // qui peut être en train de générer le verdict après navigateUp.
        viewLifecycleOwner.lifecycleScope.launch {
            liveScanRepo.reset()
            medScanController.configureGemmaForRescueMode()
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🎯 Gemma rescue config primed",
            )
        }

        // 🆕 v0.3 Lot 3 — Observer du state repo qui pilote l'overlay.
        // Lancé séparément (pas dans la coroutine ci-dessus) pour ne
        // pas être bloqué par reset/configureGemma, et pour rester
        // collected pendant tout le STARTED lifecycle du Fragment.
        observeRepoState()

        startCamera()
    }

    override fun onDestroyView() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔴 onDestroyView · " +
            "totalOcrFrames=$ocrFrameCount · " +
            "duration=${if (firstFrameAtMs > 0) System.currentTimeMillis() - firstFrameAtMs else 0}ms")

        // 🆕 v4 FIX5 — Cleanup conditionnel.
        //
        // Avant FIX5 : on appelait aveuglément resetAsync + clear PII + TTS
        // shutdown ici. Conséquence : quand nav back automatique après
        // trigger Phase B, le PII était purgé pendant que le
        // LiveScanPipelineRunner Singleton continuait son boulot.
        // Résultat : Phase C voyait patient context null → ERROR.
        //
        // Après FIX5 : on vérifie l'état du repo. Si pipeline en cours,
        // on PRÉSERVE patient + state + TTS. Sinon (vrai cancel user),
        // full reset.
        val pipelineInProgress = when (liveScanRepo.state.value) {
            is LiveScanState.IdentifyingWithGemma,
            is LiveScanState.CrossChecking,
            is LiveScanState.RenderedWithVerdict,
            is LiveScanState.NoSafeFound -> true
            else -> false
        }

        if (pipelineInProgress) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] ⏭️ pipeline in progress " +
                    "(${liveScanRepo.state.value::class.simpleName}) — preserve state/PII/TTS",
            )
            // TTS reste actif (TtsStaticVerdict est @Singleton, speak() est
            // déclenché par Phase C ~24s plus tard depuis le runner background).
            // patientContextHolder reste setCurrent (Phase C/D ont besoin).
            // liveScanRepo reste dans son state actuel (le runner gère).
        } else {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🧹 user cancel ou state idle — full reset")
            liveScanRepo.resetAsync()
            patientContextHolder.clear()
            ttsStatic.shutdown()
        }

        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroyView()
    }

    // ─── Permissions ─────────────────────────────────────────────────

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    // ─── CameraX setup ───────────────────────────────────────────────

    private fun startCamera() {
        val ctx = requireContext()
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener(
            {
                val cameraProvider = providerFuture.get()

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis, imageCapture,
                    )
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 📷 camera bound " +
                            "(preview + analysis + capture)",
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ camera bind failed", e)
                    Toast.makeText(ctx, getString(R.string.error_camera_unavailable), Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            },
            ContextCompat.getMainExecutor(ctx),
        )
    }

    // ─── OCR streaming pipeline (hot path, ~10-15 fps) ───────────────

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy) {
        // Guard : si une analyse précédente est encore en cours, on
        // drop cette frame. STRATEGY_KEEP_ONLY_LATEST garantit qu'on
        // reverra la plus récente au prochain cycle.
        if (isAnalyzing.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        isAnalyzing.set(true)
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        val tStart = System.currentTimeMillis()

        if (firstFrameAtMs == 0L) firstFrameAtMs = tStart
        ocrFrameCount++

        textRecognizer.process(input)
            .addOnSuccessListener { result ->
                val safeOwner = viewLifecycleOwnerLiveData.value
                    ?: return@addOnSuccessListener
                val rawText = result.text
                if (rawText.isNotBlank()) {
                    // 🆕 v4 — Convertir ImageProxy → Bitmap pour
                    // pouvoir le passer en Phase B. ImageProxy va
                    // être closed par addOnCompleteListener, donc on
                    // capture la bitmap MAINTENANT (avant close).
                    val bitmapForFrame = try {
                        imageProxyToBitmap(imageProxy, rotation)
                    } catch (e: Exception) {
                        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ bitmap conv failed: ${e.message}")
                        null
                    }

                    if (bitmapForFrame != null) {
                        safeOwner.lifecycleScope.launch {
                            liveScanRepo.handleOcrFrame(rawText, bitmapForFrame, uiLang)
                        }
                    }
                    Log.v(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 🔤 ocr ${rawText.length}c " +
                            "in ${System.currentTimeMillis() - tStart}ms " +
                            "(frame #$ocrFrameCount)",
                    )
                }
            }
            .addOnFailureListener { e ->
                // ML Kit JA model download peut encore être en cours.
                // OcrModuleWarmer le prewarm au boot mais sur premier
                // run il peut throw `Waiting for the text optional
                // module to be downloaded`. On log juste — les frames
                // suivantes réussiront.
                Log.w(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ⚠️ ocr failed (frame #$ocrFrameCount) : " +
                        "${e.message}",
                )
            }
            .addOnCompleteListener {
                imageProxy.close()
                isAnalyzing.set(false)
            }
    }

    // ─── Repo state observer (Lot 3) ─────────────────────────────────

    /**
     * Collect le `liveScanRepo.state` et délègue à l'overlay.
     *
     * 🆕 Lot 3 — `repeatOnLifecycle(STARTED)` garantit qu'on collect
     * uniquement quand la View est dans STARTED, et qu'on arrête
     * proprement à STOPPED. Idéal pour le couplage UI ↔ Flow.
     *
     * **Anti-flicker (Piège #2 agent forge)** : `debounce(80ms)` sur
     * le flow pour fusionner les emits consécutifs de Searching qui
     * arrivent quand 2 coroutines `handleOcrFrame` parallèles émettent.
     * Sans ça, les chips peuvent clignoter à la rendition.
     *
     * Conséquence : toutes les transitions ont un délai uniforme de
     * 80ms avant render — imperceptible (<100ms), bien en dessous du
     * seuil de perception humaine. Les transitions vers Locked/
     * Capturing/HandedOff ne sont pas "manquées" car le `_state` du
     * repo retient la dernière valeur.
     */
    @OptIn(FlowPreview::class)
    private fun observeRepoState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                liveScanRepo.state
                    .debounce(STATE_DEBOUNCE_MS)
                    .collect { state ->
                        Log.v(
                            TAG,
                            "[t=${System.currentTimeMillis()}] 🎬 state → " +
                                state::class.simpleName,
                        )
                        when (state) {
                            is LiveScanState.Accumulating -> {
                                overlay.renderAccumulating(state)
                            }
                            is LiveScanState.IdentifyingWithGemma -> {
                                // 🆕 FIX4 — Phase B trigger : on quitte
                                // l'écran caméra et on retourne sur le
                                // PatientDetailFragment où un panneau
                                // dédié va afficher Phase B/C/D.
                                // Le LiveScanPipelineRunner (Singleton)
                                // pilote les phases en background.
                                Log.i(
                                    TAG,
                                    "[t=${System.currentTimeMillis()}] 🚀 Phase B detected → " +
                                        "navigateUp to PatientDetail",
                                )
                                overlay.renderIdentifying()
                                // Petit délai pour que l'utilisateur voie le "🧠 Identification…"
                                // avant la transition (sinon ça flashe trop vite).
                                launch {
                                    delay(300L)
                                    if (viewLifecycleOwnerLiveData.value != null) {
                                        findNavController().navigateUp()
                                    }
                                }
                            }
                            // Les states CrossChecking / RenderedWithVerdict /
                            // NoSafeFound ne sont plus rendus ici (le Fragment
                            // a déjà navigué back). Si on les reçoit c'est qu'on
                            // n'a pas encore navigateUp — on ignore (anti-flicker).
                            is LiveScanState.CrossChecking,
                            is LiveScanState.RenderedWithVerdict,
                            is LiveScanState.NoSafeFound -> {
                                Log.v(
                                    TAG,
                                    "[t=${System.currentTimeMillis()}] 🤫 ignoring " +
                                        "${state::class.simpleName} (handled in PatientDetail)",
                                )
                            }
                            is LiveScanState.Error -> {
                                Toast.makeText(
                                    requireContext(),
                                    state.reason,
                                    Toast.LENGTH_LONG,
                                ).show()
                                findNavController().navigateUp()
                            }
                        }
                    }
            }
        }
    }

    /**
     * Convertit un ImageProxy (YUV) en Bitmap RGBA pour le passer à
     * Gemma vision. Utilise un buffer intermédiaire YuvImage → JPEG → Bitmap
     * pour éviter les dépendances natives.
     *
     * Approche simple compatible toutes versions Android, latence ~30ms.
     */
    private fun imageProxyToBitmap(
        imageProxy: androidx.camera.core.ImageProxy,
        rotationDegrees: Int,
    ): android.graphics.Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null,
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            80,
            out,
        )
        val jpegBytes = out.toByteArray()
        var bmp = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        // Apply rotation
        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        return bmp
    }

    // ─── Victim snapshot resolution ──────────────────────────────────
    //
    // Duplicated logic vs. PatientDetailFragment.buildVictimSnapshot().
    // Le handoff Lot 2 a explicitement demandé de dupliquer plutôt que
    // d'extraire en Singleton — pour ne rien casser à PatientDetailFragment
    // avant le hackathon. Post-hackathon : extraire en `VictimSnapshotResolver`
    // @Singleton et faire que les deux Fragments l'injectent.

    private fun buildVictimSnapshotFromPeerSid(sid: String): VictimProfileSnapshot? {
        val raw = radar.radarGetPeersJson()
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "[t=${System.currentTimeMillis()}] ❌ radarGetPeersJson parse fail · " +
                    "raw=${raw.take(200)}",
                e,
            )
            return null
        }
        var match: JSONObject? = null
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val full = p.optString("sessionId", "")
            if (full == sid || full.take(4) == sid) {
                match = p
                break
            }
        }
        if (match == null) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ sid=$sid not in ${arr.length()} peers",
            )
            return null
        }
        val allergies = parsePillarToAllergies(match.optJSONArray("allergies"))
        val medications = parsePillarToMedications(match.optJSONArray("medications"))
        val conditions = parsePillarToConditions(match.optJSONArray("conditions"))
        val display = match.optString("name", "").ifBlank { "?" }
        return VictimProfileSnapshot(
            displayName = display,
            allergies = allergies,
            medications = medications,
            conditions = conditions,
        )
    }

    private fun parsePillarToAllergies(arr: JSONArray?): List<JAllergy> {
        if (arr == null) return emptyList()
        val out = mutableListOf<JAllergy>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code", "").ifBlank { o.optString("c", "") }
            if (code.isBlank()) continue
            out.add(
                JAllergy(
                    c = code,
                    s = o.optString("criticality", "").firstOrNull()?.toString(),
                    st = o.optString("status", "").firstOrNull()?.toString(),
                    d = o.optString("details", "").ifBlank { null },
                    m = o.optString("mechanism", "").ifBlank { null },
                    displayLabel = o.optString("display", "")
                        .ifBlank { o.optString("name", "").ifBlank { null } },
                ),
            )
        }
        return out
    }

    private fun parsePillarToMedications(arr: JSONArray?): List<JMedication> {
        if (arr == null) return emptyList()
        val out = mutableListOf<JMedication>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code", "").ifBlank { o.optString("c", "") }
            if (code.isBlank()) continue
            out.add(
                JMedication(
                    c = code,
                    t = o.optString("timing", "").ifBlank { null },
                    r = o.optString("route", "").firstOrNull()?.toString(),
                    v = o.optString("doseValue", "").ifBlank { null },
                    u = o.optString("doseUnit", "").ifBlank { null },
                    rs = o.optString("reasonSource", "").ifBlank { null },
                    rc = o.optString("reasonCode", "").ifBlank { null },
                    displayLabel = o.optString("display", "")
                        .ifBlank { o.optString("name", "").ifBlank { null } },
                ),
            )
        }
        return out
    }

    private fun parsePillarToConditions(arr: JSONArray?): List<JCondition> {
        if (arr == null) return emptyList()
        val out = mutableListOf<JCondition>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code", "").ifBlank { o.optString("c", "") }
            if (code.isBlank()) continue
            out.add(
                JCondition(
                    c = code,
                    s = o.optString("severity", "").firstOrNull()?.toString(),
                    st = o.optString("status", "").firstOrNull()?.toString(),
                    d = o.optString("details", "").ifBlank { null },
                    rs = o.optString("reasonSource", "").ifBlank { null },
                    rc = o.optString("reasonCode", "").ifBlank { null },
                    displayLabel = o.optString("display", "")
                        .ifBlank { o.optString("name", "").ifBlank { null } },
                ),
            )
        }
        return out
    }
}
