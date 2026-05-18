/*
 * ProfilesFragment.kt — v2.2.16.12 · L_HERO_BAR (2026-05-13)
 *
 * 🆕 L_HERO_BAR — Tap sur le bouton + dans la toolbar (menu_profiles_new)
 *    → navigate vers PatientEditFragment en mode CREATE (profileId=null).
 *    Le FAB orphelin existant reste mais est de toute façon caché par
 *    la bottom nav — le menu + est l'entrée principale.
 *
 * v2.2.16.11 UX polish :
 *   - Wire le FAB profilesFabNew (existait en XML, orphelin Kotlin)
 *     → tap = navigate vers action_profiles_to_perso avec profileId=null
 *     → PatientEditFragment ouvre en mode "create new profile"
 *
 * v2.2.16.10 changes :
 *   • ⭐ button on each card → toggles current profile (no long-press needed)
 *   • 🗑 button is bigger and visible, tap opens confirm dialog
 *   • Long-press still works as a fallback shortcut menu
 */
package be.heyman.android.jemmapassdemo.ui.profiles

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.FragmentProfilesBinding
import be.heyman.android.jemmapassdemo.profiles.ProfileSummary
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfilesFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PROFILES"
        const val ARG_PROFILE_ID = "profileId"
    }

    @Inject lateinit var repository: ProfilesRepository

    private var _binding: FragmentProfilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProfileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView (v2.2.16.10)")
        _binding = FragmentProfilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onViewCreated")

        setupToolbar()
        setupRecyclerView()
        setupEmptyStateActions()
        setupFabNew()
        observeProfiles()
    }

    /**
     * 🆕 L_UX_POLISH — Wire le FAB "+ Nouveau profil" qui était orphelin
     * (présent en XML mais jamais référencé). Tap = navigate vers
     * PatientEditFragment en mode CREATE (profileId=null) — l'user remplit
     * directement la fiche patient sans dialog intermédiaire.
     */
    private fun setupFabNew() {
        binding.profilesFabNew.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 FAB + new profile tap")
            // profileId=null → PatientEditFragment crée un nouveau JemmaProfileJ
            safeNavigate(
                R.id.action_profiles_to_perso,
                bundleOf(ARG_PROFILE_ID to null),
            )
        }
    }

    private fun setupToolbar() {
        binding.profilesToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                // 🆕 L_HERO_BAR — bouton + dans le header (FAB caché par bottom nav)
                R.id.menu_profiles_new -> {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 menu + new profile tap")
                    safeNavigate(
                        R.id.action_profiles_to_perso,
                        bundleOf(ARG_PROFILE_ID to null),  // null = CREATE mode
                    )
                    true
                }
                R.id.menu_profiles_import_qr -> {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 menu Import QR tap")
                    safeNavigate(R.id.action_profiles_to_qr_import)
                    true
                }
                // 🆕 Lot 14.1b — `menu_profiles_pillars_gallery` (i) et
                //    `menu_profiles_settings` (clef à molette) ont été
                //    retirés du menu. Les destinations dest_pillars_gallery
                //    et dest_settings restent dans nav_graph.xml — on
                //    décidera d'un autre point d'entrée plus tard.
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ProfileListAdapter(
            onProfileClick = ::onProfileTap,
            onProfileLongClick = ::onProfileLongPress,
            onProfileDelete = ::handleDeleteWithConfirm,
            onSetCurrent = ::handleSetCurrentToggle,
        )
        binding.profilesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.profilesRecycler.adapter = adapter
    }

    private fun setupEmptyStateActions() {
        binding.profilesEmptyState.emptyCtaWizard.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 Empty state Wizard tap")
            safeNavigate(
                R.id.action_profiles_to_perso,
                bundleOf(ARG_PROFILE_ID to null),
            )
        }

        binding.profilesEmptyState.emptyCtaQr.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 Empty state QR Import tap")
            safeNavigate(R.id.action_profiles_to_qr_import)
        }

        binding.profilesEmptyState.emptyCtaPersonas.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 Empty state seed personas clicked")
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
    }

    private fun observeProfiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repository.profilesFlow,
                    repository.currentIdFlow,
                ) { profiles, currentId -> profiles to currentId }
                    .collect { (profiles, currentId) ->
                        renderList(profiles, currentId)
                    }
            }
        }
    }

    private fun renderList(profiles: List<ProfileSummary>, currentId: String?) {
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] 📋 renderList · ${profiles.size} profiles · current=$currentId"
        )
        adapter.submit(profiles, currentId)
        val isEmpty = profiles.isEmpty()
        binding.profilesRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
        // v2.2.16.10 : hide the OUTER NestedScrollView, not just its inner
        // include. Otherwise the NestedScrollView (height=match_parent) stays
        // on top of the RecyclerView and SWALLOWS every touch — clicks on
        // cards / ⭐ / 🗑 never reach the adapter.
        binding.profilesEmptyStateScroll.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.profilesEmptyState.root.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun onProfileTap(summary: ProfileSummary) {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 👆 profile tap → detail · id=${summary.id} · name=${summary.displayName}"
        )
        safeNavigate(
            R.id.action_profiles_to_detail,
            bundleOf(ARG_PROFILE_ID to summary.id),
        )
    }

    private fun onProfileLongPress(summary: ProfileSummary) {
        val isCurrent = summary.id == repository.currentProfileId
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 👆 long-press · id=${summary.id} · isCurrent=$isCurrent"
        )

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (!isCurrent) {
            actions += getString(R.string.profile_action_set_current) to {
                handleSetCurrentToggle(summary)
            }
        }
        actions += getString(R.string.profile_action_delete) to {
            handleDeleteWithConfirm(summary)
        }

        val labels = actions.map { it.first }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.profile_long_press_menu_title, summary.displayName))
            .setItems(labels) { _, which -> actions[which].second() }
            .setNegativeButton(R.string.delete_cancel_action, null)
            .show()
    }

    /**
     * Toggle behaviour : if not current, set as current. If already current,
     * clear current (no profile selected).
     */
    private fun handleSetCurrentToggle(summary: ProfileSummary) {
        val wasCurrent = summary.id == repository.currentProfileId
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ⭐ setCurrentToggle · id=${summary.id} · wasCurrent=$wasCurrent"
        )
        viewLifecycleOwner.lifecycleScope.launch {
            if (wasCurrent) {
                repository.setCurrent(null)

            } else {
                repository.setCurrent(summary.id)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_set_current_toast, summary.displayName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun handleDeleteWithConfirm(summary: ProfileSummary) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete request · id=${summary.id}")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_message, summary.displayName))
            .setNegativeButton(R.string.delete_cancel_action, null)
            .setPositiveButton(R.string.delete_confirm_action) { _, _ ->
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🗑 delete confirmed · id=${summary.id}"
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.deleteProfile(summary.id)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.profile_deleted_toast, summary.displayName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .show()
    }

    private fun safeNavigate(actionId: Int, args: Bundle? = null) {
        try {
            findNavController().navigate(actionId, args)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ safeNavigate skipped : ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ safeNavigate skipped : ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
