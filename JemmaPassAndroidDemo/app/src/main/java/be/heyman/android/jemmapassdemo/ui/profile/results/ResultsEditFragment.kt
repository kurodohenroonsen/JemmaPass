/*
 * ResultsEditFragment.kt — JEMMA Pass · IPS pillar "Results" (FHIR-native, sprint 3)
 *
 * List + CRUD of the patient's results (laboratory, imaging, other diagnostics).
 * Persistence goes through ProfilesRepository.loadResults() / saveResults(): the
 * FHIR Bundle is rewritten (one Observation per entry, source of truth) and
 * `_j.rs` is re-projected. The blood-group Observation derived from the patient's
 * blood type (`p.bt`) is read-only here — it is edited in the Patient pillar.
 *
 * Logging : tag JEMMA-RESULTS-EDIT
 */
package be.heyman.android.jemmapassdemo.ui.profile.results

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
import be.heyman.android.jemmapassdemo.databinding.FragmentResultsEditBinding
import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.ips.IpsResultCategory
import be.heyman.android.jemmapassdemo.ips.IpsResultInterpretation
import be.heyman.android.jemmapassdemo.ips.IpsResultStatus
import be.heyman.android.jemmapassdemo.pillars.IpsResultCatalog
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResultsEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-RESULTS-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
    }

    @Inject lateinit var profilesRepo: ProfilesRepository

    private var _binding: FragmentResultsEditBinding? = null
    private val binding get() = _binding!!

    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }
    private var profileId: String? = null
    private var loaded = false
    private val results = mutableListOf<IpsResult>()
    private lateinit var adapter: ResultsAdapter
    private val currentLang: String by lazy { Locale.getDefault().language.lowercase().take(2) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultsEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · argProfileId=$argProfileId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.resultsToolbarBack.setOnClickListener { findNavController().navigateUp() }

        adapter = ResultsAdapter(
            lang = currentLang,
            onTap = { rs, position -> if (!guardDerived(rs)) openForm(ResultFormMode.EDIT, existing = rs, index = position) },
            onLongPress = { rs, position -> if (!guardDerived(rs)) confirmDelete(rs, position) },
        )
        binding.resultsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.resultsRecycler.adapter = adapter
        binding.resultsFabAdd.setOnClickListener { openForm(ResultFormMode.CREATE) }

        parentFragmentManager.setFragmentResultListener(ResultFormBottomSheet.RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            handleFormResult(bundle)
        }

        if (loaded) renderList() else loadAndRender()
    }

    /** The derived blood group mirrors `p.bt`: explain where to change it instead of editing a copy. */
    private fun guardDerived(rs: IpsResult): Boolean {
        if (!IpsBloodGroup.isDerived(rs)) return false
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩸 derived blood group is read-only · id=${rs.id}")
        Toast.makeText(requireContext(), R.string.results_derived_blood_group_hint, Toast.LENGTH_LONG).show()
        return true
    }

    private fun loadAndRender() {
        val pid = argProfileId ?: profilesRepo.currentProfileId
        if (pid == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no profile id")
            showEmptyStateNoProfile(); return
        }
        profileId = pid
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = profilesRepo.loadProfile(pid)
            if (profile == null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ profile $pid not found")
                showEmptyStateNoProfile(); return@launch
            }
            val list = profilesRepo.loadResults(pid)
            results.clear(); results.addAll(list); loaded = true
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📂 loaded ${list.size} results for $pid")
            if (_binding != null) renderList()
        }
    }

    private fun renderList() {
        val sorted = results.sortedWith(
            compareByDescending<IpsResult> { it.date != null }.thenByDescending { it.date ?: "" }
        )
        adapter.submitList(sorted)
        val empty = results.isEmpty()
        binding.resultsEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.resultsRecycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.resultsEmptyText.setText(R.string.results_empty_default)
        binding.resultsFabAdd.isEnabled = true
        binding.resultsCount.text = resources.getQuantityString(R.plurals.results_count, results.size, results.size)
    }

    private fun showEmptyStateNoProfile() {
        binding.resultsEmptyState.visibility = View.VISIBLE
        binding.resultsRecycler.visibility = View.GONE
        binding.resultsEmptyText.setText(R.string.results_empty_no_profile)
        binding.resultsFabAdd.isEnabled = false
        binding.resultsCount.text = resources.getQuantityString(R.plurals.results_count, 0, 0)
    }

    private fun openForm(mode: ResultFormMode, existing: IpsResult? = null, index: Int = -1) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 openForm · mode=$mode · idx=$index · id=${existing?.id}")
        ResultFormBottomSheet.newInstance(mode, currentLang, existing).show(parentFragmentManager, "result_form")
    }

    private fun handleFormResult(bundle: Bundle) {
        val mode = ResultFormMode.valueOf(bundle.getString(ResultFormBottomSheet.ARG_MODE) ?: ResultFormMode.CREATE.name)
        if (bundle.getBoolean(ResultFormBottomSheet.ARG_DELETE, false)) {
            val targetId = bundle.getString(ResultFormBottomSheet.ARG_ID)
            results.firstOrNull { it.id == targetId }?.let { confirmDelete(it, -1) }
                ?: Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ delete requested for unknown id=$targetId")
            return
        }
        fun arg(key: String): String? = bundle.getString(key)?.takeIf { it.isNotBlank() }
        val id = arg(ResultFormBottomSheet.ARG_ID) ?: IpsResult.newId()
        val code = arg(ResultFormBottomSheet.ARG_CODE)
        val updated = IpsResult(
            id = id,
            code = code,
            system = arg(ResultFormBottomSheet.ARG_CODE_SYSTEM) ?: IpsResultCatalog.CODE_SYSTEM,
            display = if (code != null) (arg(ResultFormBottomSheet.ARG_DISPLAY) ?: IpsResultCatalog.byCode(code)?.displayEn) else null,
            text = if (code == null) arg(ResultFormBottomSheet.ARG_TEXT) else null,
            date = arg(ResultFormBottomSheet.ARG_DATE),
            status = IpsResultStatus.normalize(arg(ResultFormBottomSheet.ARG_STATUS)),
            category = IpsResultCategory.normalize(arg(ResultFormBottomSheet.ARG_CATEGORY)),
            value = arg(ResultFormBottomSheet.ARG_VALUE),
            unit = arg(ResultFormBottomSheet.ARG_UNIT),
            valueCode = arg(ResultFormBottomSheet.ARG_VALUE_CODE),
            valueDisplay = arg(ResultFormBottomSheet.ARG_VALUE_DISPLAY),
            valueText = arg(ResultFormBottomSheet.ARG_VALUE_TEXT),
            interpretation = IpsResultInterpretation.normalize(arg(ResultFormBottomSheet.ARG_INTERPRETATION)),
            refLow = arg(ResultFormBottomSheet.ARG_REF_LOW),
            refHigh = arg(ResultFormBottomSheet.ARG_REF_HIGH),
            performer = arg(ResultFormBottomSheet.ARG_PERFORMER),
            note = arg(ResultFormBottomSheet.ARG_NOTE),
        )
        when (mode) {
            ResultFormMode.CREATE -> results.add(updated)
            ResultFormMode.EDIT -> {
                val idx = results.indexOfFirst { it.id == updated.id }
                if (idx >= 0) results[idx] = updated else results.add(updated)
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ form result · mode=$mode · id=${updated.id} · code=${updated.code} · value='${updated.valueLabel()}' · date=${updated.date}")
        renderList()
        persist()
    }

    private fun confirmDelete(rs: IpsResult, position: Int) {
        val label = ResultsAdapter.labelOf(rs, currentLang).takeIf { it != "—" } ?: getString(R.string.results_unnamed)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.results_delete_title)
            .setMessage(getString(R.string.results_delete_message, label))
            .setNegativeButton(R.string.results_delete_cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.results_delete_confirm) { d, _ ->
                val removed = results.removeAll { it.id == rs.id }
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete · pos=$position · id=${rs.id} · removed=$removed")
                renderList(); persist(); d.dismiss()
            }
            .show()
    }

    private fun persist() {
        val pid = profileId ?: return
        val snapshot = results.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ok = profilesRepo.saveResults(pid, snapshot)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · profileId=$pid · count=${snapshot.size} · ok=$ok")
                if (_binding != null) {
                    Toast.makeText(requireContext(), if (ok) R.string.results_saved else R.string.results_save_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ persist failed : ${e.message}", e)
                if (_binding != null) Toast.makeText(requireContext(), R.string.results_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
