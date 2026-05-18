/*
 * SettingsViewModel.kt — combines downloads + KB + permissions + storage
 * into the live state consumed by SettingsFragment.
 *
 * Relies entirely on existing singletons (no new persistence) :
 *   • JemmaDownloadCoordinator  (livraison 2.1.0)
 *   • JemmaDownloadStorage      (livraison 2.1.0)
 *   • KnowledgeBaseManager      (this delivery)
 *
 * Permissions snapshot is computed lazily by re-checking PackageManager
 * each time the Fragment becomes visible (cheap : 5 system calls). We do
 * NOT keep a reference to PermissionsViewModel because that VM is scoped
 * to the PermissionsFragment, not survivable here.
 */
package be.heyman.android.jemmapassdemo.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.Formatter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.downloads.JemmaDownloadAggregateState
import be.heyman.android.jemmapassdemo.downloads.JemmaDownloadCoordinator
import be.heyman.android.jemmapassdemo.downloads.JemmaDownloadStorage
import be.heyman.android.jemmapassdemo.downloads.JemmaModelCatalog
import be.heyman.android.jemmapassdemo.kb.KbState
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager
import be.heyman.android.jemmapassdemo.ui.permissions.PermissionCard
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val coordinator: JemmaDownloadCoordinator,
    private val storage: JemmaDownloadStorage,
    private val kbManager: KnowledgeBaseManager,
) : ViewModel() {

    companion object {
        private const val TAG = "JEMMA-SETTINGS"
    }

    /** Aggregate state of the "Prepare the demo" 1-tap card. */
    val aggregate: StateFlow<JemmaDownloadAggregateState> = coordinator.aggregate

    /** Per-model download statuses (keyed by Model.name). */
    val modelStatuses: StateFlow<Map<String, ModelDownloadStatus>> = coordinator.statuses

    /** KB lifecycle state (NotPresent / Validating / Ready / Failed). */
    val kbState: StateFlow<KbState> = kbManager.state

    private val prefs = appContext.getSharedPreferences("jemma_settings", Context.MODE_PRIVATE)

    /** Currently active Gemma model (E2B or E4B). User picks via "Activate". */
    private val _activeModelId = MutableStateFlow<String?>(
        prefs.getString("gemma_active_model", null) ?: run {
            val modelId = when {
                storage.isFullyDownloaded(JemmaModelCatalog.gemmaE4B) -> JemmaModelCatalog.Id.GEMMA_E4B
                storage.isFullyDownloaded(JemmaModelCatalog.gemmaE2B) -> JemmaModelCatalog.Id.GEMMA_E2B
                else -> null
            }
            if (modelId != null) {
                prefs.edit().putString("gemma_active_model", modelId).apply()
            }
            modelId
        }
    )
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    /** Preflight state, recomputed whenever any source flow changes. */
    private val _preflight = MutableStateFlow(PreflightState.initial())
    val preflight: StateFlow<PreflightState> = _preflight.asStateFlow()

    init {
        // Reactive recomputation of preflight whenever any input changes.
        // We trigger refreshAll() on every emission of any source flow.
        combine(modelStatuses, kbState, activeModelId) { _, _, _ -> Unit }
            .onEach { refreshPreflight() }
            .launchIn(viewModelScope)

        // ─── v2.2.11 — Auto-glue when downloads complete ───────────────
        //
        // The DownloadWorker (Google's code) flips a model's status to
        // SUCCEEDED when its file is fully on disk. But we need to take
        // 2 follow-up actions that the worker doesn't know about :
        //
        //   1. KB → SUCCEEDED → trigger kbManager.reload() so the KbState
        //      flow goes from NotPresent to Validating to Ready, which is
        //      what the preflight UI binds to.
        //
        //   2. GEMMA_E2B or GEMMA_E4B → SUCCEEDED → if no model is yet
        //      active, auto-activate this one. Without this, the user
        //      sees "Modèle Gemma téléchargé : ❌" even right after the
        //      download finished, and is forced to manually tap "Activer".
        //
        // We track the previous status per modelId to detect the
        // transition (and avoid re-firing on every progress emission).
        modelStatuses
            .onEach { currentMap ->
                currentMap.forEach { (modelId, status) ->
                    val prev = lastSeenStatus[modelId]
                    val isFreshSuccess =
                        status.status == ModelDownloadStatusType.SUCCEEDED &&
                        prev != ModelDownloadStatusType.SUCCEEDED

                    if (isFreshSuccess) {
                        Log.i(
                            TAG,
                            "[t=${System.currentTimeMillis()}] ✅ download complete · $modelId"
                        )
                        when (modelId) {
                            JemmaModelCatalog.Id.KB -> {
                                Log.i(
                                    TAG,
                                    "[t=${System.currentTimeMillis()}] ➡️ kbManager.reload() (KB just landed)"
                                )
                                kbManager.reload()
                            }
                            JemmaModelCatalog.Id.GEMMA_E2B,
                            JemmaModelCatalog.Id.GEMMA_E4B -> {
                                if (_activeModelId.value == null) {
                                    Log.i(
                                        TAG,
                                        "[t=${System.currentTimeMillis()}] ➡️ auto-activate $modelId (no active model)"
                                    )
                                    _activeModelId.value = modelId
                                    prefs.edit().putString("gemma_active_model", modelId).apply()
                                }
                            }
                        }
                    }
                    lastSeenStatus[modelId] = status.status
                }
            }
            .launchIn(viewModelScope)
    }

    /** Per-model status snapshot from the previous emission. Used to
     *  detect SUCCEEDED transitions and fire follow-up actions exactly
     *  once instead of on every progress tick.
     */
    private val lastSeenStatus = mutableMapOf<String, ModelDownloadStatusType>()

    // ──────────────────────────────────────────────────────────────────────
    // User actions wired by the Fragment
    // ──────────────────────────────────────────────────────────────────────

    fun onPrepareDemoTap() {
        coordinator.prepareDemo()
    }

    fun onCancelPrepareDemoTap() {
        coordinator.cancelPrepareDemo()
    }

    fun onModelDownloadTap(modelId: String) {
        coordinator.downloadOne(modelId)
    }

    fun onModelCancelTap(modelId: String) {
        coordinator.cancelOne(modelId)
    }

    fun onModelDeleteTap(modelId: String) {
        val model = JemmaModelCatalog.byId(modelId) ?: return
        storage.deleteModel(model)
        // If we just deleted the KB, also tell the manager to release its
        // open connection (would lock the file otherwise on some OEMs).
        if (modelId == JemmaModelCatalog.Id.KB) {
            kbManager.close()
        }
        if (_activeModelId.value == modelId) {
            _activeModelId.value = null
            prefs.edit().remove("gemma_active_model").apply()
        }
        // 🆕 v2.6.2 — Force le coordinator à re-scanner le disque et
        // émettre un nouveau statuses → réveille les collectors UI
        // pour re-render les cards "Pas téléchargé".
        // Sans ça, statuses reste sur SUCCEEDED alors que le fichier
        // a disparu, et la card download reste verrouillée en UI.
        coordinator.refreshStatuses()
        refreshPreflight()
    }

    fun onModelActivateTap(modelId: String) {
        // Only Gemma models can be "active" ; KB is always-on once Ready.
        if (modelId == JemmaModelCatalog.Id.GEMMA_E2B ||
            modelId == JemmaModelCatalog.Id.GEMMA_E4B
        ) {
            _activeModelId.value = modelId
            prefs.edit().putString("gemma_active_model", modelId).apply()
            refreshPreflight()
        }
    }

    fun onKbReloadTap() {
        kbManager.reload()
    }

    /** Called from Fragment.onResume() to refresh permission count. */
    fun refreshPreflight() {
        _preflight.value = computePreflight()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Preflight computation
    // ──────────────────────────────────────────────────────────────────────

    private fun computePreflight(): PreflightState {
        val permsRow = computePermissionsRow()
        val modelRow = computeModelRow()
        val kbRow = computeKbRow()
        val profileRow = computeProfileRow()
        val storageRow = computeStorageRow()
        return PreflightState(
            permissions = permsRow,
            model = modelRow,
            kb = kbRow,
            profile = profileRow,
            storage = storageRow,
        )
    }

    private fun computePermissionsRow(): PreflightRow {
        val cards = PermissionCard.all()
        var granted = 0
        for (card in cards) {
            val perms = card.runtimePermissions
            if (perms.isEmpty()) {
                granted++
                continue
            }
            val allOk = perms.all {
                ContextCompat.checkSelfPermission(appContext, it) ==
                    PackageManager.PERMISSION_GRANTED
            }
            if (allOk) granted++
        }
        val total = cards.size
        val detail = appContext.getString(
            R.string.preflight_perms_detail_template, granted, total,
        )
        return PreflightRow(
            status = if (granted == total) PreflightStatus.OK else PreflightStatus.KO,
            detail = detail,
        )
    }

    private fun computeModelRow(): PreflightRow {
        val activeId = _activeModelId.value
        if (activeId != null) {
            val model = JemmaModelCatalog.byId(activeId)
            if (model != null && storage.isFullyDownloaded(model)) {
                val sizeStr = Formatter.formatFileSize(appContext, model.sizeInBytes)
                val detail = appContext.getString(
                    R.string.preflight_model_detail_active_template,
                    model.displayName, sizeStr,
                )
                return PreflightRow(PreflightStatus.OK, detail)
            }
        }
        // No active selected ; check if any Gemma is downloaded
        val anyDownloaded = JemmaModelCatalog.gemmaModels()
            .any { storage.isFullyDownloaded(it) }
        return if (anyDownloaded) {
            // downloaded but not activated : KO with hint
            PreflightRow(
                PreflightStatus.KO,
                appContext.getString(R.string.preflight_model_detail_none),
            )
        } else {
            // check if any is currently downloading
            val anyDownloading = modelStatuses.value.any { (id, status) ->
                (id == JemmaModelCatalog.Id.GEMMA_E2B ||
                 id == JemmaModelCatalog.Id.GEMMA_E4B) &&
                status.status == ModelDownloadStatusType.IN_PROGRESS
            }
            PreflightRow(
                status = if (anyDownloading) PreflightStatus.IN_PROGRESS else PreflightStatus.KO,
                detail = appContext.getString(R.string.preflight_model_detail_none),
            )
        }
    }

    private fun computeKbRow(): PreflightRow {
        return when (val s = kbState.value) {
            is KbState.Ready -> {
                val sizeStr = Formatter.formatFileSize(appContext, s.sizeBytes)
                PreflightRow(
                    status = PreflightStatus.OK,
                    detail = appContext.getString(
                        R.string.preflight_kb_detail_template, sizeStr,
                    ),
                )
            }
            is KbState.Validating -> PreflightRow(
                status = PreflightStatus.IN_PROGRESS,
                detail = appContext.getString(R.string.kb_boot_validating),
            )
            is KbState.Failed -> PreflightRow(
                status = PreflightStatus.KO,
                detail = s.reason,
            )
            is KbState.NotPresent -> {
                val kbStatus = modelStatuses.value[JemmaModelCatalog.Id.KB]
                if (kbStatus?.status == ModelDownloadStatusType.IN_PROGRESS) {
                    PreflightRow(
                        status = PreflightStatus.IN_PROGRESS,
                        detail = appContext.getString(R.string.dl_pill_downloading),
                    )
                } else {
                    PreflightRow(
                        status = PreflightStatus.KO,
                        detail = appContext.getString(R.string.preflight_kb_detail_none),
                    )
                }
            }
        }
    }

    private fun computeProfileRow(): PreflightRow {
        // Profiles persistence is in livraison kotlin_profiles_2.3.0+
        // Until then, we report 0 profiles always (KO).
        // The Fragment can still display this row.
        val count = 0
        return PreflightRow(
            status = if (count > 0) PreflightStatus.OK else PreflightStatus.KO,
            detail = appContext.resources.getQuantityString(
                R.plurals.preflight_profile_detail_count, count, count,
            ),
        )
    }

    private fun computeStorageRow(): PreflightRow {
        val free = storage.freeStorageBytes()
        val needed = JemmaModelCatalog.preparePackageSizeBytes()
        val freeStr = Formatter.formatFileSize(appContext, free)
        val detail = appContext.getString(
            R.string.preflight_storage_detail_template, freeStr,
        )
        return PreflightRow(
            status = if (free >= needed) PreflightStatus.OK else PreflightStatus.KO,
            detail = detail,
        )
    }
}
