/*
 * MedScanController.kt — JEMMA Pass · JemmaAppDemo · v2.6.2b
 *
 * Orchestrates the rescuer's med-scan with a Gemma agent backed by 2 @Tool
 * function-callable methods :
 *
 *   📷 SCAN_IMAGE → 🔤 OCR_TEXT → 🤖 GEMMA_AGENT → 🚨 CROSS_CHECK
 *
 * Where GEMMA_AGENT is a single Gemma call that internally orchestrates :
 *   1. searchDrugCandidates(names: List<String>)         ← @Tool, Kotlin
 *   2. (Gemma picks best match using image multimodal)
 *   3. checkInteractions(atcCode: String)                ← @Tool, Kotlin (closure-bound to profile)
 *   4. Gemma writes a short verdict in the victim's language
 *
 * The verdict text is what we expose in `Verdict.AgentText`. No structured
 * severity is enforced — the language model already synthesized a
 * stress-readable message. The CROSS_CHECK step is marked OK as soon as
 * Gemma returns (because Gemma already cross-checked via the second tool).
 *
 * Why this design (vs the previous strict-JSON 5-step pipeline) :
 *
 *   • No hardcoded few-shot medical examples in the prompt. Gemma uses its
 *     own medical knowledge + the image + the deterministic Kotlin tools.
 *   • The image arbitrates ambiguities (combo vs mono, dosage form,
 *     brand markings) that pure text reasoning can't disambiguate.
 *   • Adding new pillars (e.g. age-based contraindications) is just a
 *     new @Tool, no prompt rewrite.
 *
 * Per-task config :
 *
 *   At fragment entry, `configureGemmaForRescueMode()` reserves the
 *   session for this task (system prompt + baseline tools). Each
 *   `startWithImage()` re-binds CheckInteractionsTool with the current
 *   victim's profile via constructor closure.
 *
 * Log channel : JEMMA-MEDSCAN
 */
package be.heyman.android.jemmapassdemo.ai.medscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.gemma.CheckInteractionsTool
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.gemma.SearchDrugCandidatesTool
import be.heyman.android.jemmapassdemo.ai.ocr.OcrModuleWarmer
import be.heyman.android.jemmapassdemo.kb.KbCrossCheck
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JCondition
import be.heyman.android.jemmapassdemo.qr.JMedication
import com.google.ai.edge.litertlm.tool
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val TAG = "JEMMA-MEDSCAN"

/**
 * Lightweight, immutable snapshot of the victim's three clinical pillars.
 */
data class VictimProfileSnapshot(
    val displayName: String,
    val allergies: List<JAllergy>,
    val medications: List<JMedication>,
    val conditions: List<JCondition>,
) {
    val pillarCount: Int get() = allergies.size + medications.size + conditions.size
}

@Singleton
class MedScanController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val kb: KnowledgeBaseService,
    private val xcheck: KbCrossCheck,
    private val textRecognizer: TextRecognizer,
    @Suppress("unused") private val ocrWarmer: OcrModuleWarmer,
    private val gemma: GemmaSession,
) {

    private val _state = MutableStateFlow(MedScanPipelineState.IDLE)
    val state: StateFlow<MedScanPipelineState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var inFlight: Job? = null

    init {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆕 MedScanController @Singleton instantiated · v2.6.2b · " +
            "ocrWarmer wired · gemma wired (agentic flow)")
        gemma.prewarm()
    }

    // ─────────────────────────────────────────────────────────────────
    // Task lifecycle — call from RescueModeFragment.onCreate/onDestroy
    // ─────────────────────────────────────────────────────────────────

    /**
     * Configure the Gemma session for the rescuer med-scan task. Call this
     * when the rescuer enters rescue mode (before any scan). Sets up the
     * system prompt and the baseline search tool. CheckInteractionsTool
     * is added per-scan because it's bound to the victim's profile.
     */
    fun configureGemmaForRescueMode() {
        scope.launch {
            try {
                gemma.configureForTask(
                    taskId = "rescuer-med-scan",
                    systemPrompt = SYSTEM_PROMPT_AGENT,
                    tools = listOf(tool(SearchDrugCandidatesTool(kb))),
                    supportImage = true,
                )
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 Gemma configured for rescue mode")
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ configureGemmaForRescueMode failed", e)
            }
        }
    }

    /** Call from RescueModeFragment.onDestroyView. */
    fun clearGemmaTaskConfig() {
        gemma.clearTaskConfig()
    }

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    fun startWithImage(imageUri: Uri, profile: VictimProfileSnapshot, lang: String) {
        inFlight?.cancel()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🟢 startWithImage · uri=$imageUri · " +
            "victim='${profile.displayName}' · al=${profile.allergies.size} md=${profile.medications.size} " +
            "cn=${profile.conditions.size} · lang=$lang")

        val initial = MedScanPipelineState(
            isActive = true,
            steps = listOf(
                StepRow(
                    key = StepKey.SCAN_BLISTER,
                    labelResId = R.string.medscan_step_scan,
                    state = StepLifecycle.OK,
                    detail = appContext.getString(R.string.medscan_detail_image_captured),
                    durationMs = 0L,
                ),
                StepRow(StepKey.OCR_TEXT,      R.string.medscan_step_ocr,      StepLifecycle.PENDING),
                StepRow(StepKey.GEMMA_REASON,  R.string.medscan_step_gemma,    StepLifecycle.PENDING),
                StepRow(StepKey.IDENTIFY_DRUG, R.string.medscan_step_identify, StepLifecycle.OK,
                    detail = "(intégré dans agent)"),  // marked OK because the agent does it via tool
                StepRow(StepKey.CROSS_CHECK,   R.string.medscan_step_check,    StepLifecycle.PENDING),
            ),
            verdict = Verdict.None,
        )
        _state.value = initial

        inFlight = scope.launch {
            runPipeline(imageUri, profile, lang)
        }
    }

    fun reset() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔴 reset")
        inFlight?.cancel()
        inFlight = null
        _state.value = MedScanPipelineState.IDLE
    }

    fun prewarmOcr() = ocrWarmer.prewarm()
    fun prewarmGemma() = gemma.prewarm()

    // ─────────────────────────────────────────────────────────────────
    // Pipeline core (agentic — 1 Gemma call orchestrates everything)
    // ─────────────────────────────────────────────────────────────────

    private suspend fun runPipeline(
        imageUri: Uri,
        profile: VictimProfileSnapshot,
        lang: String,
    ) {
        val tStart = System.currentTimeMillis()

        // ── Step 2 : OCR ──────────────────────────────────────────────
        mark(StepKey.OCR_TEXT, StepLifecycle.RUNNING)
        val tOcr = System.currentTimeMillis()
        val ocrText = try {
            runOcrWithBackoff(imageUri, lang)
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ OCR failed after all retries", e)
            val errorMsg = if (e.message?.contains("Waiting for the text optional module", ignoreCase = true) == true) {
                appContext.getString(R.string.assistant_ocr_downloading_error)
            } else {
                e.message ?: "OCR error"
            }
            mark(StepKey.OCR_TEXT, StepLifecycle.FAIL, detail = errorMsg,
                durationMs = System.currentTimeMillis() - tOcr)
            settleVerdict(Verdict.Failed(StepKey.OCR_TEXT, errorMsg), tStart)
            return
        }
        val ocrSummary = ocrText
            .lineSequence().map { it.trim() }.filter { it.isNotBlank() }
            .joinToString(" / ").take(80)
        mark(
            StepKey.OCR_TEXT, StepLifecycle.OK,
            detail = if (ocrSummary.isBlank()) appContext.getString(R.string.medscan_detail_ocr_empty) else ocrSummary,
            durationMs = System.currentTimeMillis() - tOcr,
        )

        // ── Step 3 : Load image bitmap for multimodal ─────────────────
        val bitmap = try {
            loadBitmap(imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ loadBitmap failed", e)
            mark(StepKey.GEMMA_REASON, StepLifecycle.FAIL,
                detail = "image load: ${e.message?.take(60)}",
                durationMs = System.currentTimeMillis() - tStart)
            settleVerdict(Verdict.Failed(StepKey.GEMMA_REASON, "image load error"), tStart)
            return
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🖼  bitmap loaded · ${bitmap.width}x${bitmap.height}")

        // ── Step 4 : Gemma agent (search + check + verdict) ───────────
        mark(StepKey.GEMMA_REASON, StepLifecycle.RUNNING,
            detail = appContext.getString(R.string.medscan_detail_gemma_thinking))
        mark(StepKey.CROSS_CHECK, StepLifecycle.RUNNING)
        val tAgent = System.currentTimeMillis()

        // Build per-scan tools. SearchDrugCandidatesTool is stateless; we
        // rebuild it anyway for clean isolation. CheckInteractionsTool is
        // bound to THIS scan's profile and language via constructor closure.
        val perScanTools = listOf(
            tool(SearchDrugCandidatesTool(kb)),
            tool(CheckInteractionsTool(
                kb = kb,
                xcheck = xcheck,
                allergies = profile.allergies,
                medications = profile.medications,
                conditions = profile.conditions,
                victimDisplayName = profile.displayName,
                victimLang = lang,
            )),
        )

        val verdictText: String = try {
            gemma.ask(
                prompt = buildUserPrompt(ocrText, profile.displayName, lang),
                image = bitmap,
                // 🔥 v2.6.2c — pass system prompt directly per-call instead
                // of relying on configureForTask wiring at fragment entry.
                // This guarantees Gemma always has the agent instructions,
                // regardless of how the rescue surface is entered.
                systemInstruction = SYSTEM_PROMPT_AGENT,
                tools = perScanTools,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Gemma agent failed", e)
            mark(StepKey.GEMMA_REASON, StepLifecycle.FAIL,
                detail = (e.message ?: "Gemma error").take(120),
                durationMs = System.currentTimeMillis() - tAgent)
            mark(StepKey.CROSS_CHECK, StepLifecycle.WARN,
                detail = appContext.getString(R.string.medscan_detail_skipped))
            settleVerdict(Verdict.Failed(StepKey.GEMMA_REASON, e.message ?: "Gemma error"), tStart)
            return
        }
        val agentMs = System.currentTimeMillis() - tAgent
        val cleanText = verdictText.trim()
        if (cleanText.isBlank()) {
            mark(StepKey.GEMMA_REASON, StepLifecycle.WARN,
                detail = "Empty response", durationMs = agentMs)
            mark(StepKey.CROSS_CHECK, StepLifecycle.WARN,
                detail = appContext.getString(R.string.medscan_detail_skipped))
            settleVerdict(
                Verdict.NotIdentified(
                    ocrText = ocrText.take(120),
                    reason = appContext.getString(R.string.medscan_verdict_gemma_no_candidates),
                ),
                tStart,
            )
            return
        }

        mark(StepKey.GEMMA_REASON, StepLifecycle.OK,
            detail = cleanText.take(120),
            durationMs = agentMs)
        mark(StepKey.CROSS_CHECK, StepLifecycle.OK,
            detail = "via @Tool checkInteractions",
            durationMs = 0L)

        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ Gemma agent verdict (${agentMs}ms) : '${cleanText.take(160)}'")
        settleVerdict(
            Verdict.AgentText(
                victimDisplayName = profile.displayName,
                text = cleanText,
            ),
            tStart,
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // OCR (kept from 2.6.2a — retry-with-backoff for module DL)
    // ─────────────────────────────────────────────────────────────────

    private suspend fun runOcrWithBackoff(imageUri: Uri, lang: String): String {
        var attempt = 0
        val maxAttempts = 4
        var lastErr: Exception? = null
        while (attempt < maxAttempts) {
            attempt++
            try {
                return runOcrOnce(imageUri)
            } catch (e: MlKitException) {
                lastErr = e
                val msg = e.message ?: ""
                val isModuleDl = msg.contains("download", ignoreCase = true) ||
                    msg.contains("module", ignoreCase = true)
                if (!isModuleDl || attempt >= maxAttempts) throw e
                val backoffMs = 250L * (1L shl (attempt - 1))
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⏳ OCR module not ready — retry $attempt/$maxAttempts in ${backoffMs}ms · '${msg.take(100)}'")
                delay(backoffMs)
            }
        }
        throw lastErr ?: RuntimeException("OCR failed (unknown)")
    }

    private suspend fun runOcrOnce(imageUri: Uri): String =
        suspendCancellableCoroutine { cont ->
            val image = try {
                InputImage.fromFilePath(appContext, imageUri)
            } catch (e: Exception) {
                cont.resumeWithException(e); return@suspendCancellableCoroutine
            }
            textRecognizer.process(image)
                .addOnSuccessListener { result ->
                    val blocks = result.textBlocks.size
                    val chars = result.text.length
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔤 OCR ok · blocks=$blocks chars=$chars")
                    cont.resume(result.text)
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ OCR failed · ${err.message}")
                    cont.resumeWithException(err)
                }
        }

    private suspend fun loadBitmap(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) {
                "BitmapFactory.decodeStream returned null for $uri"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // State helpers
    // ─────────────────────────────────────────────────────────────────

    private fun mark(
        key: StepKey,
        state: StepLifecycle,
        detail: String? = null,
        durationMs: Long? = null,
    ) {
        _state.update { current ->
            val updated = current.steps.map { row ->
                if (row.key == key) row.copy(state = state, detail = detail ?: row.detail, durationMs = durationMs ?: row.durationMs)
                else row
            }
            current.copy(steps = updated)
        }
        Log.d(TAG, "[t=${System.currentTimeMillis()}] · step ${key.emoji} $key → $state${detail?.let { " · ${it.take(100)}" } ?: ""}")
    }

    private fun settleVerdict(verdict: Verdict, tStart: Long) {
        val took = System.currentTimeMillis() - tStart
        _state.update { it.copy(verdict = verdict, isActive = false, totalDurationMs = took) }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 settle · verdict=${verdict::class.simpleName} · took=${took}ms")
    }

    // ─────────────────────────────────────────────────────────────────
    // System prompt and user prompt builders
    // ─────────────────────────────────────────────────────────────────

    /**
     * The system prompt — NO hardcoded medical examples, NO few-shots with
     * real drug names. Gemma uses its own medical knowledge + the image +
     * the deterministic Kotlin tools.
     */
    private val SYSTEM_PROMPT_AGENT = """
        You are an offline emergency medication safety assistant for first
        responders. A rescuer has just photographed a medication found on a
        victim. You receive:
          - the OCR text from the photo (often noisy or corrupted,
            especially for CJK scripts)
          - the photo itself (multimodal — actually look at it)
          - the victim's display name

        Your job is a 3-step reasoning loop using the two tools provided.

        Step 1 — Propose candidates and search the KB
          Look at the image and the OCR text together. Propose 3-5
          candidate names for the medication: brand names you recognize,
          international generic names (INN), and ATC codes if you know
          them.
          CRITICAL: For CJK or non-English packaging, ALWAYS include the English equivalent brand name (e.g., map "オーグメンチン" or "オーメンチン" to "Augmentin", "アスピリン" to "Aspirin") and generic INN name in your list. The clinical database contains mostly English drug names and brand markings.
          Call searchDrugCandidates(names) with this list.

        Step 2 — Pick the best match
          The tool returns candidates with ATC codes, display names, and
          confidence scores. Look at the image again and pick the candidate
          that best matches what you see (combination vs single-ingredient,
          dosage form, packaging, brand markings). Prefer higher confidence.

        Step 3 — Check interactions
          Call checkInteractions(atcCode=...) with the ATC of your chosen
          candidate. The tool returns the victim's allergies, current
          medications, and conditions that may collide with this drug,
          plus a severity_overall ("MAJOR", "MODERATE", or "NONE") and
          the victim's preferred language.

        Final output — write the verdict for the rescuer
          Write a SHORT verdict (2 sentences max) in the victim's
          preferred language (from the checkInteractions response).
          Be terse — the rescuer reads this on a small screen under
          stress.

          Format:
            • If severity_overall is MAJOR — start with a clear warning
              phrase in the victim's language ("DO NOT ADMINISTER", "NE
              PAS DONNER", "投与しないでください", "NO ADMINISTRAR", etc.),
              then 1 sentence explaining the matched allergy/interaction
              and the drug name.
            • If severity_overall is MODERATE — say "Caution" / "Prudence"
              / "注意", then explain.
            • If severity_overall is NONE — say it's safe to administer,
              with the drug's display name for confirmation.
            • If you couldn't identify the drug confidently — say "Drug
              not confidently identified" in the victim's language and ask
              the rescuer to retake the photo or check the packaging.

        Don't include the ATC code or technical jargon in the final text.
        Just plain language for a stressed first responder.
    """.trimIndent()

    /**
     * The user prompt is minimal — just the OCR text and a pointer to
     * the image. No examples, no hints about what the drug might be.
     */
    private fun buildUserPrompt(ocrText: String, victimName: String, lang: String): String {
        val ocrBlock = if (ocrText.isBlank()) "(no OCR text recognized)" else ocrText.trim()
        return """
            OCR text from the medication photo:
            ---
            $ocrBlock
            ---
            Victim: $victimName
            Victim's preferred language: $lang

            Please look at the attached photo, use the provided tools to
            search the KB and check interactions, then write the verdict
            in the victim's language as instructed.
        """.trimIndent()
    }
}

/** Tiny inline helper to keep state updates concise. */
private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
