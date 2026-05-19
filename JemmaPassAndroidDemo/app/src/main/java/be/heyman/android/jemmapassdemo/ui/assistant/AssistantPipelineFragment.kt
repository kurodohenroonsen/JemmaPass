/*
 * AssistantPipelineFragment.kt — Lot 14.5b (PHASE 14)
 *
 * Écran d'orchestration du pipeline d'assistant. Multi-pages + OCR
 * structuré (blocs + lignes préservés).
 *
 * 🆕 Lot 14.5b (CE LOT) :
 *   • Accepte un argument `imageUris` (String joined par "|") — peut
 *     contenir 1 à 20 URIs venant du Document Scanner.
 *   • Affiche la 1ère page en preview + un chip "📄 N pages" si N>1
 *   • Lance l'OCR Japonais (kanji + hiragana + katakana + latin) sur
 *     CHAQUE page séquentiellement.
 *   • Pour chaque page, construit un [StructuredOcrPage] avec blocs +
 *     lignes + bounding boxes préservés (= ce que demandait Kudoro
 *     pour que Gemma reconstitue la table des 12 médocs de la fiche
 *     papa au lot 14.5c).
 *   • Stocke le résultat aggrégé dans `lastOcrResult` accessible au
 *     bouton "Aperçu" (lot 14.5c câblera la transition vers Gemma).
 *   • Step 1 body : "N pages · M chars · K blocs · Xms total"
 *   • Step 1 logue le format de sérialisation Gemma complet pour
 *     validation par Kudoro.
 *
 * Lots à venir :
 *   • 14.5c — Step 2 : Gemma 4 extraction candidats INN (port du
 *             pipeline L27 HTML)
 *   • 14.5c — Step 3 : KB resolve déterministe
 *   • 14.5c — Activate "Aperçu ▸" → mono form ou multi-preview
 *   • 14.6  — Step 4 multimodal disambiguation
 */
package be.heyman.android.jemmapassdemo.ui.assistant

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.assistant.AssistantHandoff
import be.heyman.android.jemmapassdemo.ai.assistant.AssistantMedicationsExtractor
import be.heyman.android.jemmapassdemo.ai.ocr.StructuredOcrPage
import be.heyman.android.jemmapassdemo.ai.ocr.StructuredOcrResult
import be.heyman.android.jemmapassdemo.ai.ocr.toStructuredPage
import be.heyman.android.jemmapassdemo.ai.tts.JemmaTtsService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@AndroidEntryPoint
class AssistantPipelineFragment : Fragment(R.layout.fragment_assistant_pipeline) {

    companion object {
        private const val TAG = "JEMMA-ASSISTANT"

        const val ARG_PILLAR = "pillar"
        const val ARG_MODE = "mode"

        /**
         * Lot 14.5b — N URIs joints par '|'. Si single page, la chaîne ne
         * contient qu'un seul élément (split renvoie une liste de 1).
         */
        const val ARG_IMAGE_URIS = "imageUris"
    }

    /** 🆕 OCR Japonais singleton (kanji + hiragana + katakana + latin). */
    @Inject
    lateinit var textRecognizer: TextRecognizer

    /** 🆕 Lot 14.5c — orchestrateur Gemma + KB. */
    @Inject
    lateinit var extractor: AssistantMedicationsExtractor

    @Inject
    lateinit var tts: JemmaTtsService

    /** Résultat OCR agrégé (set par runPipelineOcr, utilisé par runPipelineExtract). */
    private var lastOcrResult: StructuredOcrResult? = null

    /** 🆕 Lot 14.5c — items extraits Gemma+KB, set par runPipelineExtract. */
    private var lastExtracted: List<be.heyman.android.jemmapassdemo.ai.assistant.ExtractedMedication> = emptyList()

    /** 🆕 Navigation delayed until TTS finishes speaking the clinical reasoning! */
    private var navigateToPreviewOnTtsDone = false

    /** 🆕 Timer active step tracking */
    @Volatile
    private var activeStepId: Int? = null
    @Volatile
    private var activeStepStartTime: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🆕 Programmatic update of AI Engine and Hardware accelerator labels
        val prefs = requireContext().getSharedPreferences("jemma_settings", android.content.Context.MODE_PRIVATE)
        val activeModelId = prefs.getString("gemma_active_model", "gemma-4-E4B-it") ?: "gemma-4-E4B-it"
        val activeModelDisplayName = when (activeModelId) {
            "gemma-4-E2B-it" -> getString(R.string.model_name_e2b)
            "gemma-4-E4B-it" -> getString(R.string.model_name_e4b)
            else -> activeModelId
        }
        val accelerator = prefs.getString("gemma_accelerator", "GPU") ?: "GPU"

        view.findViewById<TextView>(R.id.ai_engine_model_label)?.text = getString(R.string.assistant_ai_engine_model, activeModelDisplayName)
        view.findViewById<TextView>(R.id.ai_engine_hardware_label)?.text = getString(R.string.assistant_ai_engine_hardware, accelerator)

        // Warm up TTS engine
        tts.init()

        // 🆕 Lot 14.5c9.12 — Collect TTS speaking flow to delay navigation if needed
        viewLifecycleOwner.lifecycleScope.launch {
            tts.isSpeaking.collect { speaking ->
                if (!speaking && navigateToPreviewOnTtsDone) {
                    navigateToPreviewOnTtsDone = false
                    val root = getView() ?: return@collect
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚀 TTS finished speaking! Navigating to multi-preview now.")
                    
                    // 🆕 Keep floating chat notification open until multi-preview screen transitions!

                    // 2. Auto-navigate to review
                    if (lastExtracted.isNotEmpty()) {
                        AssistantHandoff.set(lastExtracted)
                        if (isAdded && root.isAttachedToWindow) {
                            findNavController().navigate(R.id.action_pipeline_to_multi_preview)
                        }
                    }
                }
            }
        }

        val pillarName = arguments?.getString(ARG_PILLAR) ?: "GENERIC"
        val mode = arguments?.getString(ARG_MODE) ?: "photo"
        val urisStr = arguments?.getString(ARG_IMAGE_URIS)
        val uris: List<Uri> = urisStr
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.map(Uri::parse)
            ?: emptyList()

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🤖 pipeline screen open · pillar=$pillarName · mode=$mode · pages=${uris.size}",
        )

        // Toolbar back.
        view.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.assistant_pipeline_toolbar
        )?.apply {
            setTitle(titleResForPillar(pillarName))
            setNavigationOnClickListener { findNavController().navigateUp() }
        }

        // 4 steps en état "waiting" par défaut.
        renderStepInitial(view, R.id.assistant_step_1, R.string.assistant_step_1_title)
        renderStepInitial(view, R.id.assistant_step_2, R.string.assistant_step_2_title)
        renderStepInitial(view, R.id.assistant_step_3, R.string.assistant_step_3_title)
        renderStepInitial(view, R.id.assistant_step_4, R.string.assistant_step_4_title)

        view.findViewById<View>(R.id.assistant_pipeline_btn_cancel)?.setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<View>(R.id.assistant_pipeline_btn_preview)?.setOnClickListener {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 👆 preview tap · items=${lastExtracted.size}",
            )
            if (lastExtracted.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    R.string.assistant_step_no_candidates,
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnClickListener
            }
            // Handoff → multi-preview
            AssistantHandoff.set(lastExtracted)
            findNavController().navigate(R.id.action_pipeline_to_multi_preview)
        }

        if (uris.isNotEmpty()) {
            renderPreviewAndChip(view, uris)
            viewLifecycleOwner.lifecycleScope.launch {
                runPipelineOcr(view, uris)
            }
        } else {
            view.findViewById<View>(R.id.assistant_capture_empty_state)?.isVisible = true
            view.findViewById<View>(R.id.assistant_pages_pager)?.isVisible = false
            view.findViewById<View>(R.id.assistant_pages_chip)?.isVisible = false
        }

        // 🆕 Dynamic timer for running steps
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val stepId = activeStepId
                val startTime = activeStepStartTime
                if (stepId != null && startTime > 0L) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                    view.post {
                        val root = view.findViewById<View>(stepId)
                        if (root != null) {
                            val statusTv = root.findViewById<TextView>(R.id.step_status)
                            if (statusTv != null) {
                                val runningText = getString(R.string.assistant_step_status_running)
                                val cleanText = runningText.removeSuffix("…")
                                statusTv.text = "$cleanText (${elapsedSeconds}s)…"
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    override fun onDestroyView() {
        tts.stopAll()
        super.onDestroyView()
    }

    /**
     * 🆕 Lot 14.5b1 — Setup le pager swipeable des N pages.
     *
     *  • Single page : RecyclerView avec 1 item, chip caché (Page 1/1
     *    serait redondant)
     *  • Multi pages : RecyclerView horizontale + PagerSnapHelper +
     *    chip "📄 Page X / N" qui update à chaque snap.
     */
    private fun renderPreviewAndChip(view: View, uris: List<Uri>) {
        view.findViewById<View>(R.id.assistant_capture_empty_state)?.isVisible = false

        val pager = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.assistant_pages_pager)
        val chip = view.findViewById<TextView>(R.id.assistant_pages_chip)
        if (pager == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ pager view missing in layout")
            return
        }

        pager.isVisible = true
        pager.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(),
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false,
        )
        pager.adapter = AssistantPagesAdapter(uris)
        // PagerSnapHelper : snap-to-page comme un ViewPager2 sans la dep.
        androidx.recyclerview.widget.PagerSnapHelper().attachToRecyclerView(pager)

        if (uris.size > 1) {
            chip?.isVisible = true
            chip?.text = "📄 " + getString(R.string.assistant_pages_chip_position, 1, uris.size)
            // Update le chip à chaque fin de snap (state IDLE).
            pager.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(
                    recyclerView: androidx.recyclerview.widget.RecyclerView,
                    newState: Int,
                ) {
                    if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                        val lm = recyclerView.layoutManager
                            as? androidx.recyclerview.widget.LinearLayoutManager
                        val pos = lm?.findFirstCompletelyVisibleItemPosition() ?: 0
                        if (pos >= 0) {
                            chip?.text = "📄 " + getString(
                                R.string.assistant_pages_chip_position,
                                pos + 1, uris.size,
                            )
                        }
                    }
                }
            })
        } else {
            // Single page : pas de chip (Page 1/1 = bruit visuel).
            chip?.isVisible = false
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🖼️ pager wired · pages=${uris.size}")
    }

    /**
     * 🆕 Lot 14.5b — Pipeline OCR multi-pages, séquentiel pour rester
     * simple (la fiche papa = 1 page · les blisters = 1 page · les
     * vraies fiches multi-pages sont rares).
     *
     * Chaque page est convertie en [StructuredOcrPage] (blocs+lignes+bbox
     * préservés). L'agrégat est mis dans `lastOcrResult` et logué au
     * format Gemma pour validation visuelle par Kudoro.
     */
    private suspend fun runPipelineOcr(view: View, uris: List<Uri>) {
        markStepRunning(view, R.id.assistant_step_1, R.string.assistant_step_ocr_running)
        val tPipelineStart = System.currentTimeMillis()
        val pages = mutableListOf<StructuredOcrPage>()

        try {
            for ((idx, uri) in uris.withIndex()) {
                // Update status pour multi-pages : "Lecture page 2/3..."
                if (uris.size > 1) {
                    updateStepStatusText(
                        view, R.id.assistant_step_1,
                        getString(
                            R.string.assistant_step_ocr_running_page,
                            idx + 1, uris.size,
                        ),
                    )
                    
                    // 🆕 Auto-scroll the pager to show which page is being read
                    val pager = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.assistant_pages_pager)
                    pager?.post {
                        pager.smoothScrollToPosition(idx)
                    }
                }
                val tPage = System.currentTimeMillis()
                val text = runOcrOnce(uri)
                val dtPage = System.currentTimeMillis() - tPage
                val structured = text.toStructuredPage(
                    pageIndex = idx,
                    sourceUri = uri.toString(),
                    durationMs = dtPage,
                )
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ OCR page ${idx + 1}/${uris.size} · " +
                        "chars=${structured.charCount} · blocks=${structured.blocks.size} · " +
                        "lines=${structured.lineCount} · dt=${dtPage}ms",
                )
                pages += structured
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ OCR failed", e)
            val errorMsg = if (e.message?.contains("Waiting for the text optional module", ignoreCase = true) == true) {
                getString(R.string.assistant_ocr_downloading_error)
            } else {
                getString(R.string.assistant_step_ocr_failed, e.message ?: e::class.java.simpleName)
            }
            markStepError(
                view, R.id.assistant_step_1,
                errorMsg,
            )
            return
        }

        val totalDt = System.currentTimeMillis() - tPipelineStart
        val result = StructuredOcrResult(pages = pages, totalDurationMs = totalDt)
        lastOcrResult = result

        if (result.isEmpty) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ all pages empty")
            markStepError(view, R.id.assistant_step_1, getString(R.string.assistant_step_ocr_empty))
            return
        }

        // Log le format de sérialisation Gemma pour validation Kudoro.
        // C'est ce STRING-LA qui sera envoyé à Gemma au lot 14.5c.
        val serialized = result.serializeForLlm()
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📤 OCR result · pages=${result.pageCount} · " +
                "totalChars=${result.totalChars} · totalBlocks=${result.totalBlocks} · " +
                "totalDt=${totalDt}ms",
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📤 SERIALIZED FOR GEMMA :\n$serialized")

        // Body de la step 1 done.
        val statusLine = getString(
            R.string.assistant_step_ocr_done_multipage,
            result.pageCount,
            result.totalChars,
            result.totalBlocks,
            totalDt.toInt(),
        )
        // Preview = première ligne de chaque page (clean snippet).
        val previewSnippet = pages.take(2).joinToString(" · ") { p ->
            p.blocks.firstOrNull()?.lines?.firstOrNull()?.text?.take(60) ?: "(empty)"
        }
        val body = "$statusLine\n${getString(R.string.assistant_step_ocr_done_preview, previewSnippet)}"
        markStepDone(view, R.id.assistant_step_1, body)

        // 🆕 Lot 14.5c — Chaîne Gemma extract + KB resolve.
        runPipelineExtract(view, result, uris)
    }

    /**
     * 🆕 Lot 14.5c — Steps 2 + 3 : Gemma extract (port L27) + KB resolve.
     * 🔥 Lot 14.5c1 — Streaming token-par-token visible dans step_2 body
     *   pour donner du feedback pendant les 10-15s d'inférence Gemma.
     */
    private suspend fun runPipelineExtract(view: View, ocr: StructuredOcrResult, uris: List<Uri>) {
        // Pour le streaming UI, on accumule les tokens et on update le
        // step_2 body en live à chaque token reçu. Le 1er token déclenche
        // l'affichage "Gemma génère…" avec le texte qui apparaît.
        val streamingBuffer = StringBuilder()
        val tStreamStart = System.currentTimeMillis()
        val onPartial: (String) -> Unit = { token ->
            streamingBuffer.append(token)
            // Update sur main thread.
            view.post {
                val include = view.findViewById<View>(R.id.assistant_step_2) ?: return@post
                val bodyTv = include.findViewById<android.widget.TextView>(R.id.step_body)
                    ?: return@post
                // Affichage compact : on track l'elapsed + les derniers chars
                // générés. Si plus de 200 chars, on tronque le début.
                val elapsed = (System.currentTimeMillis() - tStreamStart) / 1000
                val txt = streamingBuffer.toString()
                val shown = if (txt.length > 200) "…" + txt.takeLast(200) else txt
                bodyTv.text = "[${elapsed}s · ${txt.length}c]\n$shown"
            }
        }

        val extracted = extractor.extract(
            ocr = ocr,
            imageUris = uris,
            context = requireContext(),
            onStep = { stepNum, state, body ->
                view.post {
                    if (!isAdded || activity == null) return@post
                    val includeId = when (stepNum) {
                        2 -> R.id.assistant_step_2
                        3 -> R.id.assistant_step_3
                        4 -> R.id.assistant_step_4
                        else -> return@post
                    }
                    when (state) {
                        "running" -> {
                            val runningRes = when (stepNum) {
                                2 -> R.string.assistant_step_gemma_running
                                3 -> R.string.assistant_step_kb_running
                                else -> R.string.assistant_step_vision_running
                            }
                            markStepRunning(view, includeId, runningRes)
                            if (body != null) {
                                // 🆕 Stop text streaming into Step 4 validation body on screen
                                if (stepNum != 4) {
                                    updateStepStatusText(view, includeId, body)
                                }
                                
                                // 🤖 Jemma Voice Notification Bubble at the top!
                                if (stepNum == 4) {
                                    val notifCard = view.findViewById<View>(R.id.jemma_voice_notification)
                                    if (notifCard != null) {
                                        if (notifCard.visibility != View.VISIBLE) {
                                            notifCard.alpha = 0f
                                            notifCard.visibility = View.VISIBLE
                                            notifCard.animate().alpha(0.85f).setDuration(300).start()
                                        }
                                        val bodyTv = notifCard.findViewById<TextView>(R.id.jemma_notification_body)
                                        bodyTv?.text = body
                                    }
                                }
                                
                                // 🆕 Auto-scroll pager to highlight currently audited page by Gemma Vision!
                                if (stepNum == 4 && body.contains("(") && body.contains("/")) {
                                    try {
                                        val startIdx = body.indexOf('(') + 1
                                        val endIdx = body.indexOf('/')
                                        if (startIdx > 0 && endIdx > startIdx) {
                                            val currentPageNum = body.substring(startIdx, endIdx).toInt()
                                            val pager = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.assistant_pages_pager)
                                            pager?.smoothScrollToPosition(currentPageNum - 1)
                                        }
                                    } catch (e: Exception) {
                                        // Safe ignore
                                    }
                                }
                            }
                        }
                        "done" -> {
                            markStepDone(view, includeId, body ?: "✅")
                            
                            // If Step 4 is done (all visual pages complete AND voice finished speaking)
                            if (stepNum == 4) {
                                // 2. Enable review preview button
                                view.findViewById<View>(R.id.assistant_pipeline_btn_preview)?.isEnabled = true
                                
                                // 3. Auto-navigate to review or wait for TTS to finish speaking Jemma's reasoning
                                if (lastExtracted.isNotEmpty()) {
                                    if (tts.isSpeaking.value) {
                                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗣️ Vision step done but Jemma is still speaking clinical reasoning. Delaying navigation until TTS finishes.")
                                        navigateToPreviewOnTtsDone = true
                                    } else {
                                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚀 auto-navigate to multi-preview (voice already done) · ${lastExtracted.size} items")
                                        
                                        // 🆕 Keep floating chat notification open until review screen!

                                        AssistantHandoff.set(lastExtracted)
                                        view.postDelayed({
                                            if (isAdded && view.isAttachedToWindow) {
                                                findNavController().navigate(R.id.action_pipeline_to_multi_preview)
                                            }
                                        }, 400L) // Beautiful 400ms delay to enjoy the complete step ticks
                                    }
                                }
                            }
                        }
                        "error" -> {
                            markStepError(
                                view, includeId,
                                when (stepNum) {
                                    2 -> getString(R.string.assistant_step_gemma_failed, body ?: "?")
                                    4 -> getString(R.string.assistant_step_vision_failed, body ?: "?")
                                    else -> body ?: "?"
                                }
                            )
                            if (stepNum == 4) {
                                val notifCard = view.findViewById<View>(R.id.jemma_voice_notification)
                                notifCard?.visibility = View.GONE
                            }
                        }
                    }
                }
            },
            onPartial = onPartial,
        )
        lastExtracted = extracted

        view.findViewById<View>(R.id.assistant_pipeline_btn_preview)?.isEnabled =
            extracted.isNotEmpty()
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🏁 pipeline complete · extracted=${extracted.size} · " +
                "preview button enabled=${extracted.isNotEmpty()}",
        )

        // 🆕 Auto-open Aperçu only if no images (asynchronous voice finished callback handles image case)
        if (uris.isEmpty() && extracted.isNotEmpty()) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🚀 auto-navigate to multi-preview (no images) · ${extracted.size} items",
            )
            AssistantHandoff.set(extracted)
            view.postDelayed({
                if (isAdded && view.isAttachedToWindow) {
                    findNavController().navigate(R.id.action_pipeline_to_multi_preview)
                }
            }, 300L)
        }
    }

    /** Wrapper simple du TextRecognizer ML Kit en suspend. */
    private suspend fun runOcrOnce(uri: Uri): Text = suspendCancellableCoroutine { cont ->
        val image = try {
            InputImage.fromFilePath(requireContext(), uri)
        } catch (e: Exception) {
            cont.resumeWithException(e); return@suspendCancellableCoroutine
        }
        textRecognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result) }
            .addOnFailureListener { err -> cont.resumeWithException(err) }
    }

    // ─── Helpers de rendu des steps ────────────────────────────────────

    private fun renderStepInitial(parent: View, includeId: Int, titleRes: Int) {
        if (activeStepId == includeId) {
            activeStepId = null
            activeStepStartTime = 0L
        }
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_title)?.setText(titleRes)
        root.findViewById<TextView>(R.id.step_icon)?.text = "◯"
        root.findViewById<TextView>(R.id.step_status)?.setText(R.string.assistant_step_status_waiting)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = false
        root.findViewById<TextView>(R.id.step_body)?.isVisible = false
    }

    private fun markStepRunning(parent: View, includeId: Int, statusRes: Int) {
        activeStepId = includeId
        activeStepStartTime = System.currentTimeMillis()
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "⏳"
        root.findViewById<TextView>(R.id.step_status)?.setText(R.string.assistant_step_status_running)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = true
        val body = root.findViewById<TextView>(R.id.step_body)
        body?.isVisible = true
        body?.setText(statusRes)
    }

    private fun updateStepStatusText(parent: View, includeId: Int, text: String) {
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_body)?.text = text
    }

    private fun markStepDone(parent: View, includeId: Int, body: String) {
        if (activeStepId == includeId) {
            activeStepId = null
            activeStepStartTime = 0L
        }
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "✅"
        root.findViewById<TextView>(R.id.step_status)?.setText(R.string.assistant_step_status_done)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = false
        val bodyView = root.findViewById<TextView>(R.id.step_body)
        bodyView?.isVisible = true
        bodyView?.text = body
    }

    private fun markStepError(parent: View, includeId: Int, body: String) {
        if (activeStepId == includeId) {
            activeStepId = null
            activeStepStartTime = 0L
        }
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "❌"
        root.findViewById<TextView>(R.id.step_status)?.setText(R.string.assistant_step_status_error)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = false
        val bodyView = root.findViewById<TextView>(R.id.step_body)
        bodyView?.isVisible = true
        bodyView?.text = body
    }

    private fun markStepWaitingWithBody(parent: View, includeId: Int, body: String) {
        val root = parent.findViewById<View>(includeId) ?: return
        root.findViewById<TextView>(R.id.step_icon)?.text = "◯"
        root.findViewById<TextView>(R.id.step_status)?.setText(R.string.assistant_step_status_waiting)
        root.findViewById<ProgressBar>(R.id.step_spinner)?.isVisible = false
        val bodyView = root.findViewById<TextView>(R.id.step_body)
        bodyView?.isVisible = true
        bodyView?.text = body
    }

    /** Mapping Pillar.name → string res titre de toolbar. */
    private fun titleResForPillar(pillarName: String): Int = when (pillarName.uppercase()) {
        "MEDICATIONS" -> R.string.assistant_pipeline_title_medications
        "ALLERGIES" -> R.string.assistant_pipeline_title_allergies
        "CONTACTS" -> R.string.assistant_pipeline_title_contacts
        "PATIENT" -> R.string.assistant_pipeline_title_patient
        "CONDITIONS" -> R.string.assistant_pipeline_title_conditions
        else -> R.string.assistant_pipeline_title_generic
    }
}
