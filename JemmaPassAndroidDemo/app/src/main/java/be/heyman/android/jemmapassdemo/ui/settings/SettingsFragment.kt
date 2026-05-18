/*
 * SettingsFragment.kt — binds fragment_settings.xml to SettingsViewModel.
 *
 * Major sections (top to bottom in the screen) :
 *   1. Toolbar
 *   2. ⚡ Prepare the demo (1-tap chained download)
 *   3. ✅ Preflight checklist (5 rows)
 *   4. 🧠 Gemma 4 Models cards (E2B, E4B + 270m hidden)
 *   5. 📚 Knowledge Base card
 *   6. 🌐 Language picker (already wired by xml only — TODO future delivery)
 *   7. 🧪 Demo personas seed button (placeholder)
 *   8. 🔒 Wipe data
 *   9. ℹ️ About + credits
 *
 * Live state binding :
 *   • viewModel.aggregate     → prepare-demo card progress + status text
 *   • viewModel.modelStatuses → each model card pill + buttons + progress
 *   • viewModel.kbState       → KB card pill + stats + boot message
 *   • viewModel.preflight     → 5 preflight rows (icon + color + detail)
 *
 * Tap delegation goes through ModelCardBinder + PreflightRowBinder helpers
 * so this Fragment stays under 400 lines and per-card logic is reusable.
 */
package be.heyman.android.jemmapassdemo.ui.settings

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.databinding.FragmentSettingsBinding
import be.heyman.android.jemmapassdemo.databinding.ViewKbCardBinding
import com.google.android.material.button.MaterialButtonToggleGroup
import javax.inject.Inject
import kotlinx.coroutines.launch
import be.heyman.android.jemmapassdemo.databinding.ViewModelCardV2Binding
import be.heyman.android.jemmapassdemo.databinding.ViewPreflightRowBinding
import be.heyman.android.jemmapassdemo.downloads.JemmaDownloadFormat
import be.heyman.android.jemmapassdemo.downloads.JemmaModelCatalog
import be.heyman.android.jemmapassdemo.kb.KbState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-SETTINGS"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    /** Per-model card binders (reused across emissions). */
    private lateinit var modelE2BBinder: ModelCardBinder
    private lateinit var modelE4BBinder: ModelCardBinder
    private lateinit var kbCardBinder: KbCardBinder

    // 🆕 v4.1 FIX #3 — GemmaSession pour forceReload après toggle accelerator
    @Inject lateinit var gemmaSession: GemmaSession
    @Inject lateinit var repository: ProfilesRepository

    /** Preflight row binders (5 of them). */
    private lateinit var preflightBinders: Map<PreflightField, PreflightRowBinder>

    private enum class PreflightField { PERMS, MODEL, KB, PROFILE, STORAGE }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView")
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onViewCreated")

        setupToolbar()
        setupPrepareDemoCard()
        setupPreflight()
        setupModelCards()
        setupKbCard()
        setupAccelerator()   // 🆕 v4.1 FIX #3
        setupBottomActions()

        // Live state observers.
        observeAggregate()
        observePreflight()
        observeModelStatuses()
        observeKbState()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onResume — refreshing preflight")
        // Permissions can have changed (user came back from system Settings)
        viewModel.refreshPreflight()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Setup blocks (called once in onViewCreated)
    // ──────────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupPrepareDemoCard() {
        binding.settingsPrepareBtn.setOnClickListener {
            val state = viewModel.aggregate.value
            if (state.running) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 cancelPrepareDemo")
                viewModel.onCancelPrepareDemoTap()
            } else {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 prepareDemo")
                viewModel.onPrepareDemoTap()
            }
        }
    }

    private fun setupPreflight() {
        preflightBinders = mapOf(
            PreflightField.PERMS to PreflightRowBinder(
                ViewPreflightRowBinding.bind(binding.settingsPreflightPermissions.root),
                getString(R.string.preflight_permissions_label),
            ),
            PreflightField.MODEL to PreflightRowBinder(
                ViewPreflightRowBinding.bind(binding.settingsPreflightModel.root),
                getString(R.string.preflight_model_label),
            ),
            PreflightField.KB to PreflightRowBinder(
                ViewPreflightRowBinding.bind(binding.settingsPreflightKb.root),
                getString(R.string.preflight_kb_label),
            ),
            PreflightField.PROFILE to PreflightRowBinder(
                ViewPreflightRowBinding.bind(binding.settingsPreflightProfile.root),
                getString(R.string.preflight_profile_label),
            ),
            PreflightField.STORAGE to PreflightRowBinder(
                ViewPreflightRowBinding.bind(binding.settingsPreflightStorage.root),
                getString(R.string.preflight_storage_label),
            ),
        )
    }

    private fun setupModelCards() {
        modelE2BBinder = ModelCardBinder(
            binding = ViewModelCardV2Binding.bind(binding.settingsModelE2b.root),
            model = JemmaModelCatalog.gemmaE2B,
            onPrimaryTap = { onModelPrimaryTap(JemmaModelCatalog.gemmaE2B.name) },
            onDeleteTap = { onModelDeleteTap(JemmaModelCatalog.gemmaE2B.name) },
            getActiveId = { viewModel.activeModelId.value },
        )
        modelE4BBinder = ModelCardBinder(
            binding = ViewModelCardV2Binding.bind(binding.settingsModelE4b.root),
            model = JemmaModelCatalog.gemmaE4B,
            onPrimaryTap = { onModelPrimaryTap(JemmaModelCatalog.gemmaE4B.name) },
            onDeleteTap = { onModelDeleteTap(JemmaModelCatalog.gemmaE4B.name) },
            getActiveId = { viewModel.activeModelId.value },
        )
        // The 3rd card (settings_model_270m) is hidden — no Gemma 3 270M
        // in our catalog. We just hide the included view.
        binding.settingsModel270m.root.isVisible = false
    }

    private fun setupKbCard() {
        kbCardBinder = KbCardBinder(
            binding = ViewKbCardBinding.bind(binding.settingsKbCard.root),
            model = JemmaModelCatalog.knowledgeBase,
            onPrimaryTap = { onKbPrimaryTap() },
            onDeleteTap = { onModelDeleteTap(JemmaModelCatalog.knowledgeBase.name) },
        )
    }

    /**
     * 🆕 v4.1 FIX #3 — Toggle GPU/CPU pour l'inférence Gemma.
     *
     * Le pattern : SharedPreferences "jemma_settings" / "gemma_accelerator"
     * lue par GemmaSession.configureMaxTokensForDevice() au prochain
     * ensureInit(). Forcer reload pour appliquer immédiatement.
     *
     * Pourquoi un toggle utilisateur :
     *   • Galaxy S26 (Xclipse 960) : GPU peut crasher → fallback CPU
     *   • Pixel 9 Tensor G4 : GPU plus rapide mais peut throttle
     *   • Dev debug : possibilité de comparer perf
     */
    private fun setupAccelerator() {
        val prefs = requireContext().getSharedPreferences("jemma_settings", Context.MODE_PRIVATE)
        val toggle = binding.root.findViewById<MaterialButtonToggleGroup>(R.id.settings_accelerator_toggle)
        if (toggle == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ settings_accelerator_toggle not found in layout")
            return
        }

        // Init UI state from current pref
        val current = prefs.getString("gemma_accelerator", "GPU") ?: "GPU"
        val initialBtnId = if (current == "GPU") R.id.settings_accelerator_gpu else R.id.settings_accelerator_cpu
        toggle.check(initialBtnId)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔧 accelerator UI initialized · current=$current")

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener  // only react to "selected"
            val newPref = if (checkedId == R.id.settings_accelerator_gpu) "GPU" else "CPU"
            val previousPref = prefs.getString("gemma_accelerator", "GPU") ?: "GPU"
            if (newPref == previousPref) {
                // No-op : c'est juste un set programmatique au start
                return@addOnButtonCheckedListener
            }
            prefs.edit().putString("gemma_accelerator", newPref).apply()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔧 accelerator → $newPref (was $previousPref)")

            Toast.makeText(
                requireContext(),
                getString(R.string.settings_accelerator_changed_reload),
                Toast.LENGTH_LONG,
            ).show()

            // Force reload du modèle pour appliquer immédiatement.
            // Si une inférence est en flight, mutex.withLock l'attend.
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    gemmaSession.forceReload()
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✓ forceReload done · next inference will use $newPref")
                } catch (e: Exception) {
                    Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ forceReload threw", e)
                }
            }
        }
    }

    private fun setupBottomActions() {
        binding.settingsBtnSeedPersonas.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 seedPersonas clicked")
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val profiles = be.heyman.android.jemmapassdemo.qr.JemmaPersonasSeeder.getDemoProfiles()
                    profiles.forEach { repository.saveProfile(it, sourceFormat = "DEMO_SEED") }
                    Toast.makeText(
                        requireContext(),
                        R.string.seed_personas_done_toast,
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to seed profiles", e)
                    Toast.makeText(
                        requireContext(),
                        "Failed to seed profiles: ${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        binding.settingsBtnWipe.setOnClickListener { showWipeConfirmDialog() }

        binding.settingsAbout.text = getString(R.string.app_name) + " v2.2.0"
    }

    // ──────────────────────────────────────────────────────────────────────
    // Live observers (collected on viewLifecycleOwner.lifecycleScope)
    // ──────────────────────────────────────────────────────────────────────

    private fun observeAggregate() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.aggregate.collect { state ->
                val ctx = requireContext()
                val pct = (state.progressFraction * 100).toInt()
                binding.settingsPrepareProgress.progress = pct
                binding.settingsPrepareProgress.isVisible = state.running || state.done
                binding.settingsPrepareStatus.isVisible = state.running || state.error != null
                binding.settingsPrepareStatus.text = when {
                    state.error != null -> getString(R.string.dl_failed_template, state.error)
                    state.running -> JemmaDownloadFormat.aggregateStatusLine(ctx, state)
                    else -> ""
                }
                binding.settingsPrepareBtn.text = when {
                    state.done -> getString(R.string.dl_done_label)
                    state.running -> getString(R.string.dl_btn_pause)
                    state.error != null -> getString(R.string.dl_btn_retry)
                    else -> getString(R.string.settings_prepare_btn_start)
                }
                binding.settingsPrepareBtn.isEnabled = !state.done
            }
        }
    }

    private fun observePreflight() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.preflight.collect { state ->
                preflightBinders[PreflightField.PERMS]?.bind(state.permissions)
                preflightBinders[PreflightField.MODEL]?.bind(state.model)
                preflightBinders[PreflightField.KB]?.bind(state.kb)
                preflightBinders[PreflightField.PROFILE]?.bind(state.profile)
                preflightBinders[PreflightField.STORAGE]?.bind(state.storage)
            }
        }
    }

    private fun observeModelStatuses() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.modelStatuses.collect { statuses ->
                modelE2BBinder.bind(statuses[JemmaModelCatalog.Id.GEMMA_E2B])
                modelE4BBinder.bind(statuses[JemmaModelCatalog.Id.GEMMA_E4B])
                kbCardBinder.bindDownloadStatus(statuses[JemmaModelCatalog.Id.KB])
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeModelId.collect { _ ->
                // Re-render Gemma cards to update "★ ACTIVE" pill.
                val statuses = viewModel.modelStatuses.value
                modelE2BBinder.bind(statuses[JemmaModelCatalog.Id.GEMMA_E2B])
                modelE4BBinder.bind(statuses[JemmaModelCatalog.Id.GEMMA_E4B])
            }
        }
    }

    private fun observeKbState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.kbState.collect { state ->
                kbCardBinder.bindKbState(state)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tap handlers
    // ──────────────────────────────────────────────────────────────────────

    private fun onModelPrimaryTap(modelId: String) {
        val status = viewModel.modelStatuses.value[modelId]
        when (status?.status) {
            null,
            com.google.ai.edge.gallery.data.ModelDownloadStatusType.NOT_DOWNLOADED,
            com.google.ai.edge.gallery.data.ModelDownloadStatusType.PARTIALLY_DOWNLOADED,
            com.google.ai.edge.gallery.data.ModelDownloadStatusType.FAILED -> {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 download $modelId")
                viewModel.onModelDownloadTap(modelId)
            }
            com.google.ai.edge.gallery.data.ModelDownloadStatusType.IN_PROGRESS,
            com.google.ai.edge.gallery.data.ModelDownloadStatusType.UNZIPPING -> {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 cancel $modelId")
                viewModel.onModelCancelTap(modelId)
            }
            com.google.ai.edge.gallery.data.ModelDownloadStatusType.SUCCEEDED -> {
                if (viewModel.activeModelId.value != modelId) {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 activate $modelId")
                    viewModel.onModelActivateTap(modelId)
                    val displayName = JemmaModelCatalog.byId(modelId)?.displayName ?: modelId
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_model_activated_template, displayName),
                        Toast.LENGTH_SHORT,
                    ).show()

                    // Force reload of GemmaSession to swap the model in memory immediately
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            gemmaSession.forceReload()
                            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✓ forceReload done · next inference will use $modelId")
                        } catch (e: Exception) {
                            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ forceReload threw", e)
                        }
                    }
                }
            }
        }
    }

    private fun onModelDeleteTap(modelId: String) {
        val model = JemmaModelCatalog.byId(modelId) ?: return
        val sizeStr = Formatter.formatFileSize(requireContext(), model.sizeInBytes)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dl_delete_confirm_title)
            .setMessage(getString(R.string.dl_delete_confirm_message, model.displayName, sizeStr))
            .setPositiveButton(R.string.dl_delete_confirm_btn_yes) { d, _ ->
                d.dismiss()
                viewModel.onModelDeleteTap(modelId)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_model_deleted_template, model.displayName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(R.string.dl_delete_confirm_btn_no) { d, _ -> d.dismiss() }
            .show()
    }

    private fun onKbPrimaryTap() {
        val kbStatus = viewModel.modelStatuses.value[JemmaModelCatalog.Id.KB]
        when {
            kbStatus?.status == com.google.ai.edge.gallery.data.ModelDownloadStatusType.IN_PROGRESS -> {
                viewModel.onModelCancelTap(JemmaModelCatalog.Id.KB)
            }
            viewModel.kbState.value is KbState.Ready -> {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 KB reload")
                viewModel.onKbReloadTap()
                Toast.makeText(
                    requireContext(),
                    R.string.toast_kb_reloaded,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            else -> {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 KB download")
                viewModel.onModelDownloadTap(JemmaModelCatalog.Id.KB)
            }
        }
    }

    private fun showWipeConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.wipe_all_confirm_title)
            .setMessage(R.string.wipe_all_confirm_message)
            .setPositiveButton(R.string.wipe_all_confirm_btn_yes) { d, _ ->
                d.dismiss()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 wipeAll")
                JemmaModelCatalog.all().forEach { model ->
                    viewModel.onModelDeleteTap(model.name)
                }
                Toast.makeText(
                    requireContext(),
                    R.string.wipe_all_done_toast,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(R.string.wipe_all_confirm_btn_no) { d, _ -> d.dismiss() }
            .show()
    }
}
