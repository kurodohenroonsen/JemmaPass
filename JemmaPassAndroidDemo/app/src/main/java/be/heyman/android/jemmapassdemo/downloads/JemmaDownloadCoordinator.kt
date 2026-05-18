/*
 * JemmaDownloadCoordinator.kt — orchestrator that chains JEMMA's downloads
 * and exposes a consolidated state for the UI.
 *
 * Sits ON TOP of Google's `DefaultDownloadRepository` (we don't fork it).
 * Forwards each download request to Google's WorkManager-backed worker,
 * collects the per-model `ModelDownloadStatus` callbacks, merges them
 * into a single [JemmaDownloadAggregateState], and exposes :
 *
 *   • `aggregate: StateFlow<JemmaDownloadAggregateState>`
 *       Used by the "Prepare the demo (6.1 GB)" card on Settings to show
 *       the chained progress (step 1/2/3 + bytes/total + ETA).
 *
 *   • `statuses: StateFlow<Map<String, ModelDownloadStatus>>`
 *       Per-model status (E2B / E4B / KB), used by individual model
 *       cards in Settings.
 *
 *   • `prepareDemo()` / `cancelPrepareDemo()` / `downloadOne(modelId)`
 *
 * Skips already-downloaded artifacts when chaining (cheap).
 *
 * Does NOT fork Google's worker. Does NOT add a second download mechanism.
 * Pure thin coordination layer.
 */
package be.heyman.android.jemmapassdemo.downloads

import android.util.Log
import com.google.ai.edge.gallery.data.DownloadRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class JemmaDownloadCoordinator @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val storage: JemmaDownloadStorage,
) {

    companion object {
        private const val TAG = "JEMMA-SETTINGS"
    }

    /** Per-model status. Initial = whatever is on disk. */
    private val _statuses = MutableStateFlow(initialStatusMap())
    val statuses: StateFlow<Map<String, ModelDownloadStatus>> = _statuses.asStateFlow()

    /** Aggregate state for the 1-tap "Prepare the demo" card. */
    private val _aggregate = MutableStateFlow(JemmaDownloadAggregateState.idle())
    val aggregate: StateFlow<JemmaDownloadAggregateState> = _aggregate.asStateFlow()

    private fun initialStatusMap(): Map<String, ModelDownloadStatus> =
        JemmaModelCatalog.all().associate { model ->
            model.name to if (storage.isFullyDownloaded(model)) {
                ModelDownloadStatus(
                    status = ModelDownloadStatusType.SUCCEEDED,
                    totalBytes = storage.modelFile(model).length(),
                    receivedBytes = storage.modelFile(model).length(),
                )
            } else {
                val partial = storage.partialBytes(model)
                ModelDownloadStatus(
                    status = if (partial > 0L)
                        ModelDownloadStatusType.PARTIALLY_DOWNLOADED
                    else
                        ModelDownloadStatusType.NOT_DOWNLOADED,
                    totalBytes = model.sizeInBytes,
                    receivedBytes = partial,
                )
            }
        }

    // ──────────────────────────────────────────────────────────────────────
    // Per-model API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Start downloading [modelId] via Google's WorkManager. Idempotent : if
     * the model is already SUCCEEDED, returns immediately. Hooks the
     * progress callback into our `_statuses` flow.
     */
    fun downloadOne(modelId: String) {
        val model = JemmaModelCatalog.byId(modelId)
        if (model == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ unknown modelId=$modelId")
            return
        }
        if (storage.isFullyDownloaded(model)) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] ✅ ${model.name} already on disk → skip"
            )
            updateStatus(model.name, succeededStatus(model))
            return
        }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📥 downloadOne ${model.name} · " +
                "url=${model.url} · expected=${model.sizeInBytes}"
        )
        downloadRepository.downloadModel(
            task = null,
            model = model,
        ) { _, status ->
            updateStatus(model.name, status)
        }
    }

    /**
     * Cancel any in-flight download for [modelId]. No-op if not downloading.
     */
    fun cancelOne(modelId: String) {
        val model = JemmaModelCatalog.byId(modelId) ?: return
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 cancelOne ${model.name}")
        downloadRepository.cancelDownloadModel(model)
    }

    /**
     * 🆕 v2.6.2 — Re-scan le disque pour chaque modèle du catalog et
     * met à jour `_statuses` en conséquence.
     *
     * À appeler depuis le ViewModel APRÈS toute opération externe qui
     * modifie l'état des fichiers sur disque sans passer par
     * downloadOne/cancelOne — typiquement après `storage.deleteModel()`
     * (la deletion n'est pas notifiée au coordinator par défaut, donc
     * `_statuses` reste sur SUCCEEDED alors que le fichier a disparu).
     *
     * Effet de bord : déclenche un emit sur `statuses` StateFlow, ce
     * qui réveille les collectors UI pour re-render les cards.
     *
     * Coût : ~quelques ms (3-4 stat() calls disque).
     */
    fun refreshStatuses() {
        val fresh = initialStatusMap()
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🔄 refreshStatuses · " +
                fresh.entries.joinToString(", ") { "${it.key}=${it.value.status}" },
        )
        _statuses.value = fresh
    }

    // ──────────────────────────────────────────────────────────────────────
    // Chained "Prepare the demo (6.1 GB)" — E4B + KB
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Trigger the 1-tap setup. Internally :
     *   1. Build the chain (skipping anything already downloaded)
     *   2. Set aggregate state to RUNNING with step 1/N
     *   3. Hook a single callback that advances to step k+1 when step k
     *      reports SUCCEEDED, and updates the aggregate state in real-time
     *
     * The "demo bundle" is :
     *   • Gemma 4 E4B  (3.4 GB)
     *   • knowledge_full.db  (2.0 GB)
     *
     * Total ~5.4 GB ; we round to "6.1 GB" in UI labels for caution
     * (server may report slightly higher).
     */
    fun prepareDemo() {
        // v2.2.13 — Parallel demo prep.
        //
        // Original behaviour was sequential (E4B → KB chained one after the
        // other). User feedback : when individually tapping each card it
        // already runs in parallel, so the "Démarrer (6.1 GB)" button should
        // do the same — there's no reason to halve the bandwidth utilisation.
        //
        // We now fire both downloads simultaneously and aggregate their
        // progress streams in a single state. The aggregate state semantics
        // change slightly :
        //   • `stepIndex` = number of steps already completed (was : current
        //                   step 0-indexed). Both yield "X/N" indicators that
        //                   make sense.
        //   • `totalSteps` = total number of steps in the demo (unchanged).
        //   • `currentModelId` = the most recently active model that emitted
        //                        progress (just for the label "Téléchargement
        //                        de gemma-4-E4B-it..."). Less meaningful in
        //                        parallel mode but kept for compatibility.
        //   • `bytesPerSecond` = SUM of all active downloads' rates.
        //   • `remainingMs` = MAX of remaining ms across all active downloads
        //                     (the demo is done when the slowest finishes).
        val targets = listOf(JemmaModelCatalog.gemmaE4B, JemmaModelCatalog.knowledgeBase)
            .filterNot { storage.isFullyDownloaded(it) }

        if (targets.isEmpty()) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] ✅ prepareDemo · all artifacts already on disk"
            )
            _aggregate.value = JemmaDownloadAggregateState.done()
            return
        }

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📥 prepareDemo · launching ${targets.size} parallel " +
                "download(s) : ${targets.joinToString { it.name }}"
        )

        // Track per-model progress for the running demo. Used to compute the
        // aggregate state on every tick.
        val totalBytesByModel: Map<String, Long> = targets.associate { it.name to it.sizeInBytes }
        val perModelProgress: MutableMap<String, ModelDownloadStatus> = targets.associate {
            it.name to ModelDownloadStatus(
                status = ModelDownloadStatusType.IN_PROGRESS,
                totalBytes = it.sizeInBytes,
                receivedBytes = 0L,
            )
        }.toMutableMap()

        _aggregate.value = JemmaDownloadAggregateState(
            running = true,
            stepIndex = 0,
            totalSteps = targets.size,
            currentModelId = targets[0].name,
            totalBytes = targets.sumOf { it.sizeInBytes },
            receivedBytes = 0L,
            bytesPerSecond = 0L,
            remainingMs = 0L,
            done = false,
            error = null,
        )

        // Fire all downloads at once. Each has its own callback that updates
        // its slot in `perModelProgress` and re-derives the aggregate.
        for (model in targets) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 📥 prepareDemo step start · ${model.name}"
            )
            downloadRepository.downloadModel(
                task = null,
                model = model,
            ) { _, status ->
                // Per-model status — drives individual cards.
                updateStatus(model.name, status)

                // Update local snapshot for aggregate computation.
                perModelProgress[model.name] = status

                recomputeAggregate(
                    perModelProgress = perModelProgress,
                    totalBytesByModel = totalBytesByModel,
                    lastActiveModelId = model.name,
                )

                if (status.status == ModelDownloadStatusType.SUCCEEDED) {
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] ✅ prepareDemo step done · ${model.name}"
                    )
                } else if (status.status == ModelDownloadStatusType.FAILED) {
                    Log.e(
                        TAG,
                        "[t=${System.currentTimeMillis()}] ❌ prepareDemo step FAILED · ${model.name} : " +
                            status.errorMessage
                    )
                }
            }
        }
    }

    fun cancelPrepareDemo() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 cancelPrepareDemo")
        // Cancel both ; harmless on non-running ones.
        downloadRepository.cancelDownloadModel(JemmaModelCatalog.gemmaE4B)
        downloadRepository.cancelDownloadModel(JemmaModelCatalog.knowledgeBase)
        _aggregate.value = JemmaDownloadAggregateState.idle()
    }

    /**
     * Recomputes the aggregate state from the per-model progress snapshot.
     * Called on every per-model status update during a parallel demo run.
     *
     * Aggregate done = ALL targets SUCCEEDED.
     * Aggregate error = ANY target FAILED (we still let the others continue
     * — the user can retry just the failed one from its individual card).
     */
    private fun recomputeAggregate(
        perModelProgress: Map<String, ModelDownloadStatus>,
        totalBytesByModel: Map<String, Long>,
        lastActiveModelId: String,
    ) {
        val totalBytes = totalBytesByModel.values.sum()

        // For received bytes : count actual receivedBytes for in-flight, and
        // the model's full sizeInBytes for completed (some workers don't keep
        // emitting receivedBytes after SUCCEEDED).
        val receivedBytes = perModelProgress.entries.sumOf { (id, s) ->
            if (s.status == ModelDownloadStatusType.SUCCEEDED) {
                totalBytesByModel[id] ?: s.receivedBytes
            } else {
                s.receivedBytes
            }
        }

        // Aggregate rate = sum of currently-active rates. Completed models
        // contribute 0.
        val bytesPerSecond = perModelProgress.values
            .filter { it.status != ModelDownloadStatusType.SUCCEEDED }
            .sumOf { it.bytesPerSecond }

        // Remaining ms = how long until the SLOWEST one finishes. We take
        // the max across in-flight downloads ; the demo is "done" only when
        // the slowest finishes, so its ETA is the relevant one to show.
        val remainingMs = perModelProgress.values
            .filter { it.status != ModelDownloadStatusType.SUCCEEDED }
            .maxOfOrNull { it.remainingMs }
            ?: 0L

        val completedCount = perModelProgress.values.count {
            it.status == ModelDownloadStatusType.SUCCEEDED
        }
        val totalCount = perModelProgress.size
        val allDone = completedCount == totalCount
        val firstFailure = perModelProgress.values.firstOrNull {
            it.status == ModelDownloadStatusType.FAILED
        }

        _aggregate.value = _aggregate.value.copy(
            running = !allDone && firstFailure == null,
            done = allDone,
            stepIndex = completedCount,
            totalSteps = totalCount,
            currentModelId = lastActiveModelId,
            totalBytes = totalBytes,
            receivedBytes = receivedBytes,
            bytesPerSecond = bytesPerSecond,
            remainingMs = remainingMs,
            error = firstFailure?.errorMessage,
        )

        if (allDone) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] ✅ prepareDemo all parallel downloads complete"
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────────────────────────────

    private fun updateStatus(modelId: String, status: ModelDownloadStatus) {
        _statuses.value = _statuses.value.toMutableMap().also {
            it[modelId] = status
        }
    }

    private fun succeededStatus(model: Model): ModelDownloadStatus =
        ModelDownloadStatus(
            status = ModelDownloadStatusType.SUCCEEDED,
            totalBytes = model.sizeInBytes,
            receivedBytes = model.sizeInBytes,
        )
}

/**
 * Snapshot UI state for the "Prepare the demo" card. Exposed as a
 * [StateFlow] by [JemmaDownloadCoordinator]. The Settings Fragment renders
 * progress bar + ETA + step label from this single value.
 */
data class JemmaDownloadAggregateState(
    /** True iff a chain is currently running. */
    val running: Boolean,
    /** 0-based index of the current step (e.g. 1 means we're on step 2/N). */
    val stepIndex: Int,
    /** Total steps in the chain. */
    val totalSteps: Int,
    /** Model id currently being downloaded (or last one if running=false). */
    val currentModelId: String?,
    /** Sum of expected bytes across all chain steps. */
    val totalBytes: Long,
    /** Sum of received bytes across already-completed + current step. */
    val receivedBytes: Long,
    /** Live transfer rate (current step). */
    val bytesPerSecond: Long,
    /** Live remaining ms (current step). */
    val remainingMs: Long,
    /** True when the whole chain finished successfully. */
    val done: Boolean,
    /** Error message if the chain failed at a step. */
    val error: String?,
) {
    /** Aggregate progress 0..1 across the whole chain. */
    val progressFraction: Float
        get() = if (totalBytes > 0L) (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    /** Pretty step indicator like "Étape 2/3". Localizable in UI later. */
    fun stepIndicator(): String = "${stepIndex + 1}/$totalSteps"

    companion object {
        fun idle(): JemmaDownloadAggregateState = JemmaDownloadAggregateState(
            running = false,
            stepIndex = 0,
            totalSteps = 0,
            currentModelId = null,
            totalBytes = 0L,
            receivedBytes = 0L,
            bytesPerSecond = 0L,
            remainingMs = 0L,
            done = false,
            error = null,
        )

        fun done(): JemmaDownloadAggregateState = idle().copy(done = true)
    }
}
