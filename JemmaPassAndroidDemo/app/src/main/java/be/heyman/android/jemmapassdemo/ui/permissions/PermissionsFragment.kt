/*
 * PermissionsFragment.kt — onboarding screen that BLOCKS the user from
 * leaving until ALL 5 mandatory permissions are granted.
 *
 * Lifecycle :
 *   onCreateView  → inflate FragmentPermissionsBinding via ViewBinding
 *   onViewCreated → wire 5 cards + "Tout accorder" + "Continuer" buttons
 *   onResume      → re-check system state, auto-skip to Profiles if all OK
 *
 * Behaviour :
 *   • If all 5 cards GRANTED at onResume() → navigate to dest_profiles
 *     immediately (with popUpToInclusive so this screen is removed from
 *     the back stack, user never sees it on subsequent launches).
 *   • Otherwise, show the screen and update the status pill of each card.
 *   • "Tout accorder" button : single ActivityResultLauncher request for
 *     all 7 underlying permissions at once. Android groups related ones
 *     (the 3 BLE perms become a single dialog) and prompts sequentially
 *     for the rest.
 *   • Tap on a single card : re-prompt for its underlying perms (if not
 *     DENIED_PERMANENT) or open system Settings (if DENIED_PERMANENT).
 *   • "Continuer" button : disabled until allGranted=true.
 */
package be.heyman.android.jemmapassdemo.ui.permissions

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.FragmentPermissionsBinding
import be.heyman.android.jemmapassdemo.databinding.ViewPermissionCardBinding
import be.heyman.android.jemmapassdemo.downloads.JemmaDownloadStorage
import be.heyman.android.jemmapassdemo.downloads.JemmaModelCatalog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PermissionsFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PERMS"

        // Tracks "denied once" per card so we can detect the transition
        // to DENIED_PERMANENT on the next denial without UI feedback.
        // Key: card id ; Value: count of consecutive denies in this session.
        private const val PREF_NAME = "jemma_permissions"
        private const val PREF_KEY_DENIED_ONCE = "denied_once_set"
    }

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PermissionsViewModel by viewModels()

    /**
     * v2.2.10 — injected to drive smart routing after permissions are
     * granted : if no Gemma model AND no KB are on disk, route the user
     * to Settings (preflight) instead of Profiles (which would be empty
     * and confusing — there's nothing to do without the model + KB).
     */
    @Inject
    lateinit var downloadStorage: JemmaDownloadStorage

    /** Map<cardId, the included CardView> for fast iteration. */
    private val cardBindings = mutableMapOf<PermissionCardId, ViewPermissionCardBinding>()

    /** Single launcher for the "Tout accorder" multi-permission request. */
    private val grantAllLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ grantAll result · ${result.size} entries · " +
                "granted=${result.values.count { it }}/${result.size}"
        )
        // Mark every requested perm that came back DENIED as a "denied once" event,
        // then refresh the UI from scratch.
        markDeniedOnce(result.filterValues { !it }.keys)
        refreshAllStatuses()
    }

    /** Per-card launcher : launched when the user taps a single card to retry. */
    private val singleCardLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ singleCard result · " +
                "granted=${result.values.count { it }}/${result.size}"
        )
        markDeniedOnce(result.filterValues { !it }.keys)
        refreshAllStatuses()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView")
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onViewCreated")

        // Bind every <include> by id. The included layout is view_permission_card.xml.
        cardBindings[PermissionCardId.CAMERA] =
            ViewPermissionCardBinding.bind(binding.permissionsCardCamera.root)
        cardBindings[PermissionCardId.MICROPHONE] =
            ViewPermissionCardBinding.bind(binding.permissionsCardMicrophone.root)
        cardBindings[PermissionCardId.BLUETOOTH] =
            ViewPermissionCardBinding.bind(binding.permissionsCardBluetooth.root)
        cardBindings[PermissionCardId.LOCATION] =
            ViewPermissionCardBinding.bind(binding.permissionsCardLocation.root)
        cardBindings[PermissionCardId.NOTIFICATIONS] =
            ViewPermissionCardBinding.bind(binding.permissionsCardNotifications.root)

        // Populate static content of each card (emoji + title + desc + underlying)
        // and wire its tap handler.
        PermissionCard.all().forEach { card ->
            val cb = cardBindings[card.id] ?: return@forEach
            cb.permCardEmoji.text = card.emoji
            cb.permCardTitle.setText(card.titleRes)
            cb.permCardDescription.setText(card.descriptionRes)
            cb.permCardUnderlying.text = card.underlyingDisplay
            cb.root.setOnClickListener { onCardTap(card) }
        }

        // Wire bottom buttons.
        binding.permissionsBtnGrantAll.setOnClickListener { onGrantAllTap() }
        binding.permissionsBtnContinue.setOnClickListener { onContinueTap() }

        // Toggle the "Why mandatory?" expandable text.
        binding.permissionsWhyLink.setOnClickListener {
            binding.permissionsWhyText.isVisible = !binding.permissionsWhyText.isVisible
        }

        // Observe ViewModel state to reflect statuses + button enable.
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statuses.collect { statuses ->
                renderStatuses(statuses)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.grantedCount.combine(viewModel.allGranted) { (g, t), all ->
                Triple(g, t, all)
            }.collect { (granted, total, all) ->
                binding.permissionsSummary.text =
                    getString(R.string.permissions_summary_template, granted, total)
                binding.permissionsBtnContinue.isEnabled = all
                Log.d(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 📊 summary $granted/$total · allGranted=$all"
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔐 onResume — refreshing statuses")
        refreshAllStatuses()

        // Auto-skip if everything is already granted (subsequent launches).
        // We wait one frame so the ViewModel state has propagated.
        binding.root.post {
            if (allCardsGranted()) {
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ all granted → routing"
                )
                routeAfterPermissions()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cardBindings.clear()
        _binding = null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Status computation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Re-read system state for every card, push to ViewModel.
     */
    private fun refreshAllStatuses() {
        val map = PermissionCard.all().associate { card ->
            card.id to computeStatus(card)
        }
        viewModel.setStatuses(map)
    }

    /**
     * Compute the live status of one card based on :
     *  - whether its underlying perms are currently granted (PackageManager)
     *  - whether the user has previously denied the perm (rationale flag)
     *  - whether we've recorded a "denied once" event in our local prefs
     *
     * The DENIED_PERMANENT detection logic on Android 11+ :
     *   shouldShowRationale=false AND (we recorded ≥1 prior deny) → permanent
     *   This is the standard heuristic recommended by Google.
     */
    private fun computeStatus(card: PermissionCard): PermissionCardStatus {
        val ctx = requireContext()
        val perms = card.runtimePermissions

        // Empty list = auto-GRANTED (e.g. NOTIFICATIONS on Android < 13).
        if (perms.isEmpty()) return PermissionCardStatus.GRANTED

        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return PermissionCardStatus.GRANTED

        // Not granted → distinguish DENIED vs DENIED_PERMANENT.
        val deniedOnce = isDeniedOnce(card.id)
        val anyShowRationale = perms.any { shouldShowRequestPermissionRationale(it) }

        return if (deniedOnce && !anyShowRationale) {
            PermissionCardStatus.DENIED_PERMANENT
        } else if (anyShowRationale || deniedOnce) {
            PermissionCardStatus.DENIED
        } else {
            PermissionCardStatus.PENDING
        }
    }

    private fun allCardsGranted(): Boolean =
        viewModel.statuses.value.values.all { it == PermissionCardStatus.GRANTED }

    // ──────────────────────────────────────────────────────────────────────
    // Rendering
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Update each card's pill background + label to reflect its status.
     */
    private fun renderStatuses(statuses: Map<PermissionCardId, PermissionCardStatus>) {
        statuses.forEach { (id, status) ->
            val cb = cardBindings[id] ?: return@forEach
            val pill = cb.permCardStatusPill
            when (status) {
                PermissionCardStatus.PENDING -> {
                    pill.setBackgroundResource(R.drawable.bg_pill_locked)
                    pill.setText(R.string.permission_status_pending)
                    pill.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.jemma_text_muted)
                    )
                }
                PermissionCardStatus.GRANTED -> {
                    pill.setBackgroundResource(R.drawable.bg_pill_count)
                    pill.setText(R.string.permission_status_granted)
                    pill.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.jemma_primary)
                    )
                }
                PermissionCardStatus.DENIED -> {
                    pill.setBackgroundResource(R.drawable.bg_pill_locked)
                    pill.setText(R.string.permission_status_denied)
                    pill.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.jemma_danger)
                    )
                }
                PermissionCardStatus.DENIED_PERMANENT -> {
                    pill.setBackgroundResource(R.drawable.bg_warning_chip)
                    pill.setText(R.string.permission_status_settings)
                    pill.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.severity_high)
                    )
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tap handlers
    // ──────────────────────────────────────────────────────────────────────

    private fun onGrantAllTap() {
        val perms = PermissionCard.allRuntimePermissions()
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 👆 onGrantAllTap · requesting ${perms.size} perms"
        )
        if (perms.isEmpty()) {
            // Edge case : everything is auto-granted (very old OS), just refresh.
            refreshAllStatuses()
            return
        }
        grantAllLauncher.launch(perms)
    }

    /**
     * One-card tap : re-prompt if not DENIED_PERMANENT, else open Settings.
     */
    private fun onCardTap(card: PermissionCard) {
        val status = viewModel.statuses.value[card.id] ?: PermissionCardStatus.PENDING
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 👆 onCardTap · id=${card.id} · status=$status"
        )

        when (status) {
            PermissionCardStatus.GRANTED -> {
                // Nothing to do, already granted.
            }
            PermissionCardStatus.DENIED_PERMANENT -> {
                showSettingsRationaleDialog()
            }
            else -> {
                // PENDING or DENIED → re-prompt.
                if (card.runtimePermissions.isNotEmpty()) {
                    singleCardLauncher.launch(card.runtimePermissions.toTypedArray())
                }
            }
        }
    }

    private fun onContinueTap() {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 👆 onContinueTap · routing"
        )
        routeAfterPermissions()
    }

    /**
     * Smart routing after permissions are OK.
     *
     * The user's first onboarding milestone is "permissions granted". After
     * that there are 3 possible UX states :
     *
     *   1. Demo not prepared (no Gemma + no KB on disk) → Settings is the
     *      next logical step. Profiles would be empty, FAB-to-create would
     *      be useless without the model + KB. We send the user to Settings
     *      and let the "Préparer la démo (6.1 GB)" CTA do its job.
     *
     *   2. Half-prepared (Gemma OR KB present, not both) → still Settings.
     *      The preflight surface there will show what's still missing.
     *
     *   3. Fully prepared (Gemma E2B|E4B AND KB on disk) → Profiles, the
     *      real "do work" screen.
     *
     * We only check the file presence on disk (synchronous, fast) — not the
     * KB validation status (which is async and may not have finished yet at
     * this point in the boot). A file present but corrupt KB will be caught
     * by SettingsFragment when the user lands there.
     *
     * Both paths popUpTo dest_permissions inclusive so the back button
     * doesn't bring the user back to the permissions screen.
     */
    private fun routeAfterPermissions() {
        val gemmaReady = downloadStorage.isFullyDownloaded(JemmaModelCatalog.gemmaE2B)
                || downloadStorage.isFullyDownloaded(JemmaModelCatalog.gemmaE4B)
        val kbReady = downloadStorage.isFullyDownloaded(JemmaModelCatalog.knowledgeBase)
        val demoReady = gemmaReady && kbReady

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🧭 routing · " +
                "gemma=$gemmaReady · kb=$kbReady · demoReady=$demoReady"
        )

        val actionId = if (demoReady) {
            R.id.action_permissions_to_profiles
        } else {
            R.id.action_permissions_to_settings
        }
        safeNavigate(actionId)
    }

    private fun safeNavigate(actionId: Int) {
        try {
            findNavController().navigate(actionId)
        } catch (e: IllegalStateException) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ safeNavigate failed : ${e.message}"
            )
        } catch (e: IllegalArgumentException) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ unknown action : ${e.message}"
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // DENIED_PERMANENT path : open Settings page for the app
    // ──────────────────────────────────────────────────────────────────────

    private fun showSettingsRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.permissions_rationale_title)
            .setMessage(R.string.permissions_rationale_message)
            .setPositiveButton(R.string.permissions_rationale_btn_open) { d, _ ->
                d.dismiss()
                openAppSettings()
            }
            .setNegativeButton(R.string.permissions_rationale_btn_cancel) { d, _ ->
                d.dismiss()
            }
            .show()
    }

    private fun openAppSettings() {
        val ctx = requireContext()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ➡️ openAppSettings · package=${ctx.packageName}"
        )
        startActivity(intent)
    }

    // ──────────────────────────────────────────────────────────────────────
    // "Denied once" tracking — persists across this app session via prefs
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Mark a set of denied perm strings as "denied at least once". This lets
     * us distinguish DENIED from DENIED_PERMANENT on the next computeStatus.
     *
     * Indexed by raw permission string (not card id) because Android's
     * shouldShowRequestPermissionRationale() is per-permission.
     */
    private fun markDeniedOnce(deniedPerms: Set<String>) {
        if (deniedPerms.isEmpty()) return
        val prefs = requireContext().getSharedPreferences(PREF_NAME, 0)
        val existing = prefs.getStringSet(PREF_KEY_DENIED_ONCE, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        existing.addAll(deniedPerms)
        prefs.edit().putStringSet(PREF_KEY_DENIED_ONCE, existing).apply()
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] 💾 markDeniedOnce · adding ${deniedPerms.size} · " +
                "total=${existing.size}"
        )
    }

    /**
     * True iff at least one of this card's perms is in the denied-once set.
     */
    private fun isDeniedOnce(id: PermissionCardId): Boolean {
        val card = PermissionCard.all().firstOrNull { it.id == id } ?: return false
        if (card.runtimePermissions.isEmpty()) return false
        val prefs = requireContext().getSharedPreferences(PREF_NAME, 0)
        val deniedSet = prefs.getStringSet(PREF_KEY_DENIED_ONCE, emptySet()) ?: emptySet()
        return card.runtimePermissions.any { it in deniedSet }
    }
}
