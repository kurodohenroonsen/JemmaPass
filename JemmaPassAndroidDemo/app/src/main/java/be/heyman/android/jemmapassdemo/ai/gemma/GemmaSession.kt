/*
 * GemmaSession.kt — JEMMA Pass · JemmaAppDemo · v2.6.2b
 *
 * Singleton wrapper around Edge Gallery's `LlmChatModelHelper` exposing
 * a clean `suspend fun ask(prompt, image)` API for the rest of JEMMA.
 *
 * 🆕 v2.6.2b — `configureForTask()` for per-task @Tool sessions.
 *
 *   When the rescuer enters a specific surface (med-scan, dictation, etc.),
 *   the caller calls `configureForTask(taskId, systemPrompt, tools, ...)`.
 *   This stores the task-level system instruction and toolset so that
 *   subsequent `ask()` calls reuse them without the caller having to
 *   repeat them. Each `ask()` still resets the conversation (so multi-
 *   turn state from a previous scan doesn't leak into the next one),
 *   but with the stored config rather than `null` defaults.
 *
 *   `ask()` arguments take precedence over stored config — useful when
 *   a per-call tool (e.g. CheckInteractionsTool bound to a specific
 *   patient profile) needs to be added on top of the task baseline.
 *
 * 🆕 v2.6.2a.3 — RAM-aware max_tokens via Config-list path :
 *      > 10 GB total RAM → 4096 tokens   (Pixel 9 Pro family)
 *      else              → 1024 tokens   (Samsung G781B, S22)
 *
 * Log channel : JEMMA-GEMMA
 */
package be.heyman.android.jemmapassdemo.ai.gemma

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import be.heyman.android.jemmapassdemo.downloads.JemmaModelCatalog
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ToolProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "JEMMA-GEMMA"

/** Init lifecycle exposed via [GemmaSession.status]. */
sealed class InitStatus {
    object NotStarted : InitStatus()
    object Loading : InitStatus()
    object Ready : InitStatus()
    data class Failed(val reason: String) : InitStatus()
}

@Singleton
class GemmaSession @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    /** Which Gemma to use. E4B is the premium 3.4 GB multimodal; E2B is lighter (2.4 GB). */
    private val model: Model = JemmaModelCatalog.gemmaE4B

    /** Serializes calls — engine isn't thread-safe. */
    private val mutex = Mutex()

    /** Init coalescing — prevents two concurrent inits if prewarm() and ask() race. */
    private var initJob: Job? = null

    private val _status = MutableStateFlow<InitStatus>(InitStatus.NotStarted)
    val status: StateFlow<InitStatus> = _status.asStateFlow()

    /** Background scope for prewarm. */
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Task-level config (set by configureForTask) ─────────────────────
    // Stored so per-call `ask()` doesn't need to repeat them. Per-call
    // args still override these when provided.
    private var taskId: String = "default"
    private var taskSystemPrompt: String? = null
    private var taskTools: List<ToolProvider> = emptyList()
    private var taskSupportImage: Boolean = true

    init {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆕 GemmaSession @Singleton instantiated · " +
            "model=${model.name} (${model.sizeInBytes / 1_048_576} MB)")
        configureMaxTokensForDevice()
    }

    /**
     * Port of webview L44.15.5 — RAM-aware max_tokens.
     *
     * 2.6.2a.3 path : write through `model.configs` (List<Config>) then
     * call `preProcess()` so configValues is rebuilt from configs. This
     * is the official Edge Gallery path used by built-in tasks (see
     * Config.kt:236 `LabelConfig(key = MAX_TOKENS, defaultValue = "$N")`).
     * Going through this path means any internal reset/refresh code
     * inside Edge Gallery preserves our value.
     *
     *   > 10 GB total → 8000 tokens   (Pixel 9 Pro family) — 🆕 Lot 14.5c30 :
     *                                   bumped from 4096 to 8000 to fit the
     *                                   agent system prompt (16 @Tool descriptions
     *                                   in FR ≈ 2500 tokens) + per-turn state
     *                                   preamble + user message + tool call
     *                                   round-trips. With 4096 we hit a
     *                                   `Input token ids are too long` runtime
     *                                   error at the very first turn.
     *   else          → 2048 tokens   (Samsung G781B, S22) — bumped from 1024
     *                                  for the same reason; the agent chat
     *                                  is unusable below 2k tokens.
     */
    private fun configureMaxTokensForDevice() {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ ActivityManager null — keeping default 2048 max_tokens")
            return
        }
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / 1024.0 / 1024.0 / 1024.0
        val availGb = memInfo.availMem / 1024.0 / 1024.0 / 1024.0
        val maxTokens = if (totalRamGb > 10.0) 8000 else 2048
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📊 Device RAM: ${"%.1f".format(totalRamGb)} GB total, " +
            "${"%.1f".format(availGb)} GB free · lowMemory=${memInfo.lowMemory}")
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🪜 max_tokens=$maxTokens chosen (threshold=10 GB)")

        // 🆕 v4.1 FIX #3 — Read user-selected accelerator preference (GPU/CPU)
        // from SharedPreferences. Default GPU (matches Edge Gallery default).
        // Le toggle UI est dans SettingsFragment > MaterialButtonToggleGroup.
        val prefs = appContext.getSharedPreferences("jemma_settings", Context.MODE_PRIVATE)
        val acceleratorPref = prefs.getString("gemma_accelerator", "GPU") ?: "GPU"
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔧 accelerator preference: $acceleratorPref")

        // 1. Add a real LabelConfig to model.configs so preProcess() picks
        //    it up. Edge Gallery's convertValueToTargetType handles String
        //    → Int conversion just fine (Config.kt:186).
        // 2. Call preProcess() to rebuild configValues from the configs list.
        //    This wipes the empty configValues and writes "max_tokens" → "4096".
        // 🆕 v4.1 FIX #3 : on ajoute aussi ConfigKeys.ACCELERATOR dans la même
        //    liste, sinon le premier `model.configs = listOf(...)` écrase l'autre.
        try {
            model.configs = listOf(
                com.google.ai.edge.gallery.data.LabelConfig(
                    key = com.google.ai.edge.gallery.data.ConfigKeys.MAX_TOKENS,
                    defaultValue = "$maxTokens",
                ),
                com.google.ai.edge.gallery.data.LabelConfig(
                    key = com.google.ai.edge.gallery.data.ConfigKeys.ACCELERATOR,
                    defaultValue = acceleratorPref,
                ),
            )
            model.preProcess()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🪜 model.configs populated · " +
                "configValues=${model.configValues}")
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ configureMaxTokensForDevice threw — keeping defaults", e)
        }
    }

    /**
     * Read back max_tokens just before init/inference, for diagnostic.
     * Called by ensureInit() so we can correlate "what we wrote" vs
     * "what the engine sees" via logs.
     */
    private fun logReadBackMaxTokens(stage: String) {
        try {
            val maxTokens = model.getIntConfigValue(
                key = com.google.ai.edge.gallery.data.ConfigKeys.MAX_TOKENS,
                defaultValue = 1024,
            )
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔬 read-back @ $stage · " +
                "max_tokens=$maxTokens · configValues=${model.configValues}")
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] 🔬 read-back @ $stage threw : ${e.message}")
        }
    }

    /**
     * Kick off the model load in background. Idempotent — safe to call
     * multiple times. Call from a UI hook (e.g. when the rescuer opens
     * the patient detail) so the model is hot by the time they tap SCAN.
     */
    fun prewarm() {
        if (_status.value is InitStatus.Ready || _status.value is InitStatus.Loading) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 📡 prewarm skipped · status=${_status.value::class.simpleName}")
            return
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📡 prewarm · launching background init")
        bgScope.launch {
            try {
                ensureInit()
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ prewarm init failed (will retry on first ask)", e)
            }
        }
    }

    /**
     * 🆕 v2.6.2b — Configure the session for a specific task (rescue mode,
     * dictation, scan, etc.).
     *
     * Stores the system prompt and toolset that subsequent `ask()` calls
     * will use by default. Each ask() still resets the conversation, but
     * with this stored config rather than null/empty defaults.
     *
     * The actual `resetConversation` happens at the next `ask()` — this
     * function only stores the config. Calling it twice in a row with
     * different configs just keeps the latest, no extra work.
     *
     * Call this at the entry of a task surface (e.g. RescueModeFragment.
     * onCreate or MedScanController.startWithImage's first line).
     *
     * @param taskId  log marker (also reported as part of init logs)
     * @param systemPrompt  system instruction for this task — defines the
     *                      LLM's role, output format, behavior
     * @param tools  baseline @Tool objects available to the LLM throughout
     *               this task. Per-call ask(tools=...) can add to this.
     * @param supportImage  whether the task uses multimodal images.
     *                      Must be set at engine init time, can't change
     *                      per-call.
     */
    suspend fun configureForTask(
        taskId: String,
        systemPrompt: String,
        tools: List<ToolProvider>,
        supportImage: Boolean = true,
    ) {
        ensureInit()
        this.taskId = taskId
        this.taskSystemPrompt = systemPrompt
        this.taskTools = tools
        this.taskSupportImage = supportImage
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 configureForTask · " +
            "taskId='$taskId' · tools=${tools.size} · supportImage=$supportImage · " +
            "systemPromptLen=${systemPrompt.length}")
    }

    /**
     * Reset task-level config back to defaults. Call this on task exit
     * (e.g. RescueModeFragment.onDestroyView).
     */
    fun clearTaskConfig() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🧹 clearTaskConfig · was taskId='$taskId'")
        this.taskId = "default"
        this.taskSystemPrompt = null
        this.taskTools = emptyList()
        this.taskSupportImage = true
    }

    /**
     * Ask Gemma a single question. Each call resets the conversation
     * so calls are independent. Returns the full response as a single
     * String once the model says `isDone=true`.
     *
     * @param prompt the user prompt — already formatted (we don't do
     *               prompt-engineering at this layer)
     * @param image optional bitmap for multimodal calls (must be set if
     *              the prompt references "the image")
     * @param systemInstruction optional override for the task-level
     *                          system prompt
     * @param tools optional override for the task-level toolset. Pass
     *              `null` to use the task's tools; pass an explicit list
     *              (possibly empty) to replace them for this call.
     */
    suspend fun ask(
        prompt: String,
        image: Bitmap? = null,
        systemInstruction: String? = null,
        tools: List<ToolProvider>? = null,
        /**
         * 🆕 Lot 14.5c31 — Raw PCM byte array (16-bit mono, 16 kHz LE) to
         * be sent to Gemma 4 multimodal audio input. The engine performs
         * STT natively on-device — no SpeechRecognizer / Google Speech
         * Services involved. Pass `null` for text-only / image-only asks.
         *
         * The audioBackend was activated at init time, so any ask can
         * include audio. The engine resets the conversation per call.
         */
        audioClip: ByteArray? = null,
        /**
         * 🆕 Lot 14.5c1 — Stream incremental token-par-token.
         * Si non-null, chaque token généré par Gemma est forwardé vers
         * ce callback AVANT la fin de l'inférence. Le caller peut update
         * son UI en live (visible le texte qui apparaît caractère par
         * caractère pendant que Gemma génère).
         *
         * Le callback reçoit le partial = un fragment incremental
         * (PAS cumulatif). Concatener côté caller pour avoir le texte
         * en cours.
         */
        onPartial: ((String) -> Unit)? = null,
    ): String {
        ensureInit()
        return mutex.withLock {
            val tStart = System.currentTimeMillis()
            // Per-call args take precedence over task-level config.
            val effectiveSystem = systemInstruction ?: taskSystemPrompt
            val effectiveTools = tools ?: taskTools
            // supportImage is determined by both the image presence AND
            // the task config (the engine must be initialized with vision
            // enabled, which we do by default in init).
            val supportImage = image != null || taskSupportImage
            Log.i(TAG, "[t=$tStart] 💬 ask · taskId='$taskId' · supportImage=$supportImage · " +
                "tools=${effectiveTools.size} · systemPrompt=${effectiveSystem != null} · " +
                "promptLen=${prompt.length} " +
                "(first 80=\"${prompt.take(80).replace('\n', ' ')}\")")

            // 🆕 Lot 14.5c8 — Logs verbeux pour analyser exactement quoi on
            // envoie à Gemma et ce qu'elle nous rend. Pratique pour debug
            // des system prompts (vulgarisation, extraction médicaments...).
            Log.i(TAG, "[t=$tStart] 📤 ask · config · model=${model.name} · " +
                "configValues=${model.configValues} · " +
                "hasImage=${image != null} · hasAudio=${audioClip != null} · " +
                "audioBytes=${audioClip?.size ?: 0} · onPartial=${onPartial != null}")
            Log.i(TAG, "[t=$tStart] 📤 ask · SYSTEM PROMPT :\n${effectiveSystem ?: "(none)"}")
            Log.i(TAG, "[t=$tStart] 📤 ask · USER PROMPT :\n$prompt")

            // Reset conversation so each ask is independent.
            try {
                LlmChatModelHelper.resetConversation(
                    model = model,
                    supportImage = supportImage,
                    // 🆕 Lot 14.5c31 — supportAudio matches engine init.
                    // Must be true here so that Content.AudioBytes is accepted
                    // when audioClip != null. Safe when audioClip == null too:
                    // the engine just doesn't allocate audio buffers.
                    supportAudio = audioClip != null,
                    systemInstruction = effectiveSystem?.let { sysText ->
                        Contents.of(listOf(Content.Text(sysText)))
                    },
                    tools = effectiveTools,
                    // 🔥 v2.6.2c — CRITICAL : was false, blocked tool invocations.
                    // Edge Gallery's TinyGardenTask and AgentChatScreen both use
                    // true when they want Gemma to respect the @Tool schema.
                    // Setting to true forces the constrained decoder to emit
                    // valid tool calls instead of free-form text.
                    // 🆕 Lot 14.5d — Disable if no tools are provided (e.g. Gemma Vision)
                    // to prevent immediate truncation/hangs in the decoder.
                    enableConversationConstrainedDecoding = effectiveTools.isNotEmpty(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ resetConversation threw", e)
                throw e
            }

            // Run inference, collect streaming chunks, resume on done.
            val result = suspendCancellableCoroutine<String> { cont ->
                val buffer = StringBuilder()
                // 🆕 Lot 14.5c32 — log every partial chunk so we see EXACTLY
                // what Gemma is producing token-by-token. Helps diagnose
                // "responseLen=0" cases where Gemma may emit tool-call
                // markers we'd otherwise lose.
                var chunkIndex = 0
                LlmChatModelHelper.runInference(
                    model = model,
                    input = prompt,
                    resultListener = { partial, isDone, _ /* thinking ignored */ ->
                        if (partial.isNotEmpty()) {
                            buffer.append(partial)
                            if (chunkIndex < 80) {  // cap to first 80 chunks to avoid log flooding
                                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🧩 chunk#${chunkIndex} len=${partial.length} · '${partial.replace('\n', '⏎')}'")
                            } else if (chunkIndex == 80) {
                                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🧩 …chunk log capped at 80, continuing silently…")
                            }
                            chunkIndex++
                            onPartial?.invoke(partial)
                        }
                        if (isDone) {
                            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 isDone=true · totalChunks=${chunkIndex} · bufLen=${buffer.length}")
                            if (!cont.isCompleted) {
                                cont.resume(buffer.toString())
                            }
                        }
                    },
                    cleanUpListener = { /* engine close — not for us */ },
                    onError = { msg ->
                        Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ inference error · $msg")
                        if (!cont.isCompleted) {
                            cont.resumeWithException(RuntimeException("Gemma error: $msg"))
                        }
                    },
                    images = if (image != null) listOf(image) else emptyList(),
                    audioClips = if (audioClip != null) listOf(audioClip) else emptyList(),
                    coroutineScope = null,
                    extraContext = null,
                )
                cont.invokeOnCancellation {
                    try { LlmChatModelHelper.stopResponse(model) } catch (_: Exception) {}
                }
            }
            val dt = System.currentTimeMillis() - tStart
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ ask done in ${dt}ms · " +
                "respLen=${result.length} (first 80=\"${result.take(80).replace('\n', ' ')}\")")
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📥 ask · FULL REPLY :\n$result")
            result
        }
    }

    /**
     * Lazily initialize the engine. Multiple concurrent callers share
     * the same init Job — only one actual `LlmChatModelHelper.initialize`
     * call happens.
     */
    private suspend fun ensureInit() {
        if (_status.value is InitStatus.Ready) return
        // If init is already in flight, wait for it.
        initJob?.let { existing ->
            if (existing.isActive) {
                Log.d(TAG, "[t=${System.currentTimeMillis()}] 📡 ensureInit · waiting on in-flight init job")
                existing.join()
                if (_status.value is InitStatus.Ready) return
                if (_status.value is InitStatus.Failed) {
                    throw RuntimeException("Gemma init previously failed: ${(_status.value as InitStatus.Failed).reason}")
                }
            }
        }
        // Start fresh init under mutex.
        mutex.withLock {
            if (_status.value is InitStatus.Ready) return@withLock
            val tStart = System.currentTimeMillis()
            _status.value = InitStatus.Loading
            Log.i(TAG, "[t=$tStart] 🚀 init starting · model=${model.name} · " +
                "supportImage=true · taskId=jemma-med-scan")
            logReadBackMaxTokens("just-before-initialize") // 🆕 2.6.2a.3 diagnostic
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    LlmChatModelHelper.initialize(
                        context = appContext,
                        model = model,
                        taskId = "jemma-med-scan",
                        supportImage = true,
                        // 🆕 Lot 14.5c31 — Gemma 4 audio input enabled at engine init.
                        // EngineConfig.audioBackend = Backend.CPU() (must be CPU for
                        // Gemma 3n/4 per Gallery's LlmChatModelHelper.kt:117). This
                        // unlocks Content.AudioBytes(pcmBytes) in runInference, which
                        // is full on-device STT via Gemma multimodal — no Google
                        // Speech Services round-trip.
                        supportAudio = true,
                        onDone = { error ->
                            if (error.isBlank()) {
                                val dt = System.currentTimeMillis() - tStart
                                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ init OK in ${dt}ms")
                                _status.value = InitStatus.Ready
                                if (!cont.isCompleted) cont.resume(Unit)
                            } else {
                                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ init failed · $error")
                                _status.value = InitStatus.Failed(error)
                                if (!cont.isCompleted) cont.resumeWithException(
                                    RuntimeException("Gemma init failed: $error")
                                )
                            }
                        },
                        systemInstruction = null,
                        tools = emptyList(),
                        // 🔥 v2.6.2c — must be true so that subsequent
                        // resetConversation calls with tools actually
                        // engage constrained decoding properly.
                        enableConversationConstrainedDecoding = true,
                        coroutineScope = null,
                    )
                }
            } catch (e: Exception) {
                _status.value = InitStatus.Failed(e.message ?: e::class.java.simpleName)
                throw e
            }
        }
    }

    /**
     * 🆕 v4.1 FIX #3 — Force re-initialisation du modèle au prochain ensureInit.
     * Utilisé quand l'utilisateur change le toggle GPU/CPU dans Settings.
     *
     * Effets :
     *   • cleanup engine actuel (libère mémoire GPU/CPU)
     *   • cancel init job en flight
     *   • reset status à NotStarted
     * → le prochain `ask` / `askStreaming` re-déclenche `ensureInit()`
     *   qui relit la pref accelerator depuis SharedPreferences et
     *   reconfigure model.configs.
     *
     * Thread-safe via mutex partagé avec ensureInit.
     */
    suspend fun forceReload() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔄 forceReload requested")
        mutex.withLock {
            try {
                LlmChatModelHelper.cleanUp(model) {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔄 forceReload cleanup done")
                }
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ forceReload cleanup threw: ${e.message}")
            }
            initJob?.cancel()
            initJob = null
            _status.value = InitStatus.NotStarted
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔄 forceReload done — next ensureInit will re-init")
        }
    }

    /** For long-running test surfaces — clears the in-memory engine. */
    fun cleanUp() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🧹 cleanUp")
        try {
            LlmChatModelHelper.cleanUp(model) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🧹 cleanUp done")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ cleanUp threw", e)
        }
        _status.value = InitStatus.NotStarted
    }
}
