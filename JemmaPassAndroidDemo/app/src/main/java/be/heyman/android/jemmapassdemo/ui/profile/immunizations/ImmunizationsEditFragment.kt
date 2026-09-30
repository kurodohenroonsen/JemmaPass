/*
 * ImmunizationsEditFragment.kt — JEMMA Pass · IPS pillar "Immunizations" (FHIR-native)
 *
 * First pillar edited directly against the FHIR IPS Bundle : the list is
 * loaded with ProfilesRepository.loadImmunizations() and every change is
 * persisted with saveImmunizations(), which rewrites the Bundle (source of
 * truth) and re-projects `_j.im` for the QR / mesh channels.
 *
 * Pattern (same as Medications / Allergies) :
 *   • Fragment + ViewBinding + Hilt field injection, the fragment owns the state
 *   • ListAdapter + DiffUtil, tap = EDIT, long-press = delete confirmation
 *   • ImmunizationFormBottomSheet returns a flat Bundle through the Fragment
 *     Result API on `parentFragmentManager`
 *
 * Nav args : profileId (nullable → falls back to the starred profile)
 * Logging   : tag JEMMA-IMMUNIZATIONS-EDIT
 */
package be.heyman.android.jemmapassdemo.ui.profile.immunizations

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.FragmentImmunizationsEditBinding
import be.heyman.android.jemmapassdemo.ips.IpsImmunization
import be.heyman.android.jemmapassdemo.ips.IpsImmunizationStatus
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.pillars.IpsVaccineCatalog
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImmunizationsEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-IMMUNIZATIONS-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
    }

    @Inject lateinit var profilesRepo: ProfilesRepository
    @Inject lateinit var translations: IpsTranslationsRepository

    private var _binding: FragmentImmunizationsEditBinding? = null
    private val binding get() = _binding!!

    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }

    /** Resolved profile id (arg first, starred profile as fallback). */
    private var profileId: String? = null
    private var loaded = false
    private val immunizations = mutableListOf<IpsImmunization>()
    private lateinit var adapter: ImmunizationsAdapter

    private val currentLang: String by lazy { Locale.getDefault().language.lowercase().take(2) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentImmunizationsEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · argProfileId=$argProfileId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.immunizationsToolbarBack.setOnClickListener { findNavController().navigateUp() }

        adapter = ImmunizationsAdapter(
            lang = currentLang,
            translations = translations,
            scope = viewLifecycleOwner.lifecycleScope,
            onTap = { im, position -> openForm(ImmunizationFormMode.EDIT, existing = im, index = position) },
            onLongPress = { im, position -> confirmDelete(im, position) },
        )
        binding.immunizationsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.immunizationsRecycler.adapter = adapter

        binding.immunizationsFabAdd.setOnClickListener { openForm(ImmunizationFormMode.CREATE) }

        parentFragmentManager.setFragmentResultListener(
            ImmunizationFormBottomSheet.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, bundle -> handleFormResult(bundle) }

        if (loaded) renderList() else loadAndRender()
    }

    // ─── Load ────────────────────────────────────────────────────────

    private fun loadAndRender() {
        val pid = argProfileId ?: profilesRepo.currentProfileId
        if (pid == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no profile id (arg null + no current)")
            showEmptyStateNoProfile()
            return
        }
        profileId = pid
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = profilesRepo.loadProfile(pid)
            if (profile == null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ profile $pid not found")
                showEmptyStateNoProfile()
                return@launch
            }
            val list = profilesRepo.loadImmunizations(pid)
            immunizations.clear()
            immunizations.addAll(list)
            loaded = true
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📂 loaded ${list.size} immunizations for $pid")
            if (_binding != null) renderList()
        }
    }

    private fun renderList() {
        // Most recent first ; unknown dates at the end.
        val sorted = immunizations.sortedWith(
            compareByDescending<IpsImmunization> { it.date != null }
                .thenByDescending { it.date ?: "" }
        )
        adapter.submitList(sorted)
        val empty = immunizations.isEmpty()
        binding.immunizationsEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.immunizationsRecycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.immunizationsEmptyText.setText(R.string.immunizations_empty_default)
        binding.immunizationsFabAdd.isEnabled = true
        binding.immunizationsCount.text = resources.getQuantityString(
            R.plurals.immunizations_count, immunizations.size, immunizations.size,
        )
    }

    private fun showEmptyStateNoProfile() {
        binding.immunizationsEmptyState.visibility = View.VISIBLE
        binding.immunizationsRecycler.visibility = View.GONE
        binding.immunizationsEmptyText.setText(R.string.immunizations_empty_no_profile)
        binding.immunizationsFabAdd.isEnabled = false
        binding.immunizationsCount.text = resources.getQuantityString(R.plurals.immunizations_count, 0, 0)
    }

    // ─── Form ────────────────────────────────────────────────────────

    private fun openForm(mode: ImmunizationFormMode, existing: IpsImmunization? = null, index: Int = -1) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 openForm · mode=$mode · idx=$index · id=${existing?.id}")
        ImmunizationFormBottomSheet
            .newInstance(mode, currentLang, existing)
            .show(parentFragmentManager, "immunization_form")
    }

    private fun handleFormResult(bundle: Bundle) {
        val mode = ImmunizationFormMode.valueOf(
            bundle.getString(ImmunizationFormBottomSheet.ARG_MODE) ?: ImmunizationFormMode.CREATE.name,
        )
        val id = bundle.getString(ImmunizationFormBottomSheet.ARG_ID)?.takeIf { it.isNotBlank() }
            ?: IpsImmunization.newId()
        val code = bundle.getString(ImmunizationFormBottomSheet.ARG_CODE)?.takeIf { it.isNotBlank() }
        val freeText = bundle.getString(ImmunizationFormBottomSheet.ARG_TEXT)?.takeIf { it.isNotBlank() }
        val display = bundle.getString(ImmunizationFormBottomSheet.ARG_DISPLAY)?.takeIf { it.isNotBlank() }
            ?: IpsVaccineCatalog.byCode(code)?.displayEn
        val doseNumber = bundle.getInt(ImmunizationFormBottomSheet.ARG_DOSE_NUMBER, 0).takeIf { it > 0 }
        val seriesDoses = bundle.getInt(ImmunizationFormBottomSheet.ARG_SERIES_DOSES, 0).takeIf { it > 0 }

        val updated = IpsImmunization(
            id = id,
            code = code,
            system = bundle.getString(ImmunizationFormBottomSheet.ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() }
                ?: IpsVaccineCatalog.CODE_SYSTEM,
            display = if (code != null) display else null,
            text = if (code == null) freeText else null,
            date = bundle.getString(ImmunizationFormBottomSheet.ARG_DATE)?.takeIf { it.isNotBlank() },
            status = IpsImmunizationStatus.normalize(bundle.getString(ImmunizationFormBottomSheet.ARG_STATUS)),
            doseNumber = doseNumber,
            seriesDoses = seriesDoses,
            lotNumber = bundle.getString(ImmunizationFormBottomSheet.ARG_LOT)?.takeIf { it.isNotBlank() },
            manufacturer = bundle.getString(ImmunizationFormBottomSheet.ARG_MANUFACTURER)?.takeIf { it.isNotBlank() },
            performer = bundle.getString(ImmunizationFormBottomSheet.ARG_PERFORMER)?.takeIf { it.isNotBlank() },
            note = bundle.getString(ImmunizationFormBottomSheet.ARG_NOTE)?.takeIf { it.isNotBlank() },
        )

        when (mode) {
            ImmunizationFormMode.CREATE -> immunizations.add(updated)
            ImmunizationFormMode.EDIT -> {
                val idx = immunizations.indexOfFirst { it.id == updated.id }
                if (idx >= 0) immunizations[idx] = updated else immunizations.add(updated)
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ form result · mode=$mode · id=${updated.id} · code=${updated.code} · date=${updated.date}")
        renderList()
        persist()
    }

    // ─── Delete ──────────────────────────────────────────────────────

    private fun confirmDelete(im: IpsImmunization, position: Int) {
        val label = IpsVaccineCatalog.getDisplay(im.code, currentLang)
            ?: im.label().takeIf { it.isNotBlank() }
            ?: getString(R.string.immunizations_unnamed)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.immunizations_delete_title)
            .setMessage(getString(R.string.immunizations_delete_message, label))
            .setNegativeButton(R.string.immunizations_delete_cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.immunizations_delete_confirm) { d, _ ->
                val removed = immunizations.removeAll { it.id == im.id }
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete · pos=$position · id=${im.id} · removed=$removed")
                renderList()
                persist()
                d.dismiss()
            }
            .show()
    }

    // ─── Persist (Bundle = source of truth) ──────────────────────────

    private fun persist() {
        val pid = profileId ?: return
        val snapshot = immunizations.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ok = profilesRepo.saveImmunizations(pid, snapshot)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · profileId=$pid · count=${snapshot.size} · ok=$ok")
                if (_binding != null) {
                    Toast.makeText(
                        requireContext(),
                        if (ok) R.string.immunizations_saved else R.string.immunizations_empty_no_profile,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ persist failed : ${e.message}", e)
                if (_binding != null) {
                    Toast.makeText(requireContext(), R.string.immunizations_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
