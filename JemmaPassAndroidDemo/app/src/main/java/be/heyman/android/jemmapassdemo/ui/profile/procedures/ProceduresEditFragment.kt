/*
 * ProceduresEditFragment.kt — JEMMA Pass · IPS pillar "History of Procedures" (FHIR-native)
 *
 * List + CRUD of the patient's procedures. Persistence goes through
 * ProfilesRepository.loadProcedures() / saveProcedures(): the FHIR Bundle is
 * rewritten (source of truth) and `_j.pr` is re-projected for QR / mesh.
 * Same pattern as ImmunizationsEditFragment.
 *
 * Logging : tag JEMMA-PROCEDURES-EDIT
 */
package be.heyman.android.jemmapassdemo.ui.profile.procedures

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
import be.heyman.android.jemmapassdemo.databinding.FragmentProceduresEditBinding
import be.heyman.android.jemmapassdemo.ips.IpsProcedure
import be.heyman.android.jemmapassdemo.ips.IpsProcedureStatus
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.pillars.IpsProcedureCatalog
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProceduresEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PROCEDURES-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
    }

    @Inject lateinit var profilesRepo: ProfilesRepository
    @Inject lateinit var kb: KnowledgeBaseService

    private var _binding: FragmentProceduresEditBinding? = null
    private val binding get() = _binding!!

    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }
    private var profileId: String? = null
    private var loaded = false
    private val procedures = mutableListOf<IpsProcedure>()
    private lateinit var adapter: ProceduresAdapter
    private val currentLang: String by lazy { Locale.getDefault().language.lowercase().take(2) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProceduresEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · argProfileId=$argProfileId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.proceduresToolbarBack.setOnClickListener { findNavController().navigateUp() }

        adapter = ProceduresAdapter(
            lang = currentLang,
            kb = kb,
            scope = viewLifecycleOwner.lifecycleScope,
            onTap = { pr, position -> openForm(ProcedureFormMode.EDIT, existing = pr, index = position) },
            onLongPress = { pr, position -> confirmDelete(pr, position) },
        )
        binding.proceduresRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.proceduresRecycler.adapter = adapter
        binding.proceduresFabAdd.setOnClickListener { openForm(ProcedureFormMode.CREATE) }

        parentFragmentManager.setFragmentResultListener(ProcedureFormBottomSheet.RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            handleFormResult(bundle)
        }

        if (loaded) renderList() else loadAndRender()
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
            val list = profilesRepo.loadProcedures(pid)
            procedures.clear(); procedures.addAll(list); loaded = true
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📂 loaded ${list.size} procedures for $pid")
            if (_binding != null) renderList()
        }
    }

    private fun renderList() {
        val sorted = procedures.sortedWith(
            compareByDescending<IpsProcedure> { it.date != null }.thenByDescending { it.date ?: "" }
        )
        adapter.submitList(sorted)
        val empty = procedures.isEmpty()
        binding.proceduresEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.proceduresRecycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.proceduresEmptyText.setText(R.string.procedures_empty_default)
        binding.proceduresFabAdd.isEnabled = true
        binding.proceduresCount.text = resources.getQuantityString(R.plurals.procedures_count, procedures.size, procedures.size)
    }

    private fun showEmptyStateNoProfile() {
        binding.proceduresEmptyState.visibility = View.VISIBLE
        binding.proceduresRecycler.visibility = View.GONE
        binding.proceduresEmptyText.setText(R.string.procedures_empty_no_profile)
        binding.proceduresFabAdd.isEnabled = false
        binding.proceduresCount.text = resources.getQuantityString(R.plurals.procedures_count, 0, 0)
    }

    private fun openForm(mode: ProcedureFormMode, existing: IpsProcedure? = null, index: Int = -1) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 openForm · mode=$mode · idx=$index · id=${existing?.id}")
        ProcedureFormBottomSheet.newInstance(mode, currentLang, existing).show(parentFragmentManager, "procedure_form")
    }

    private fun handleFormResult(bundle: Bundle) {
        val mode = ProcedureFormMode.valueOf(bundle.getString(ProcedureFormBottomSheet.ARG_MODE) ?: ProcedureFormMode.CREATE.name)
        if (bundle.getBoolean(ProcedureFormBottomSheet.ARG_DELETE, false)) {
            val targetId = bundle.getString(ProcedureFormBottomSheet.ARG_ID)
            procedures.firstOrNull { it.id == targetId }?.let { confirmDelete(it, -1) }
                ?: Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ delete requested for unknown id=$targetId")
            return
        }
        val id = bundle.getString(ProcedureFormBottomSheet.ARG_ID)?.takeIf { it.isNotBlank() } ?: IpsProcedure.newId()
        val code = bundle.getString(ProcedureFormBottomSheet.ARG_CODE)?.takeIf { it.isNotBlank() }
        val freeText = bundle.getString(ProcedureFormBottomSheet.ARG_TEXT)?.takeIf { it.isNotBlank() }
        val display = bundle.getString(ProcedureFormBottomSheet.ARG_DISPLAY)?.takeIf { it.isNotBlank() }
            ?: IpsProcedureCatalog.byCode(code)?.displayEn
        val updated = IpsProcedure(
            id = id,
            code = code,
            system = bundle.getString(ProcedureFormBottomSheet.ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() } ?: IpsProcedureCatalog.CODE_SYSTEM,
            display = if (code != null) display else null,
            text = if (code == null) freeText else null,
            date = bundle.getString(ProcedureFormBottomSheet.ARG_DATE)?.takeIf { it.isNotBlank() },
            status = IpsProcedureStatus.normalize(bundle.getString(ProcedureFormBottomSheet.ARG_STATUS)),
            bodySite = bundle.getString(ProcedureFormBottomSheet.ARG_BODY_SITE)?.takeIf { it.isNotBlank() },
            outcome = bundle.getString(ProcedureFormBottomSheet.ARG_OUTCOME)?.takeIf { it.isNotBlank() },
            performer = bundle.getString(ProcedureFormBottomSheet.ARG_PERFORMER)?.takeIf { it.isNotBlank() },
            location = bundle.getString(ProcedureFormBottomSheet.ARG_LOCATION)?.takeIf { it.isNotBlank() },
            note = bundle.getString(ProcedureFormBottomSheet.ARG_NOTE)?.takeIf { it.isNotBlank() },
        )
        when (mode) {
            ProcedureFormMode.CREATE -> procedures.add(updated)
            ProcedureFormMode.EDIT -> {
                val idx = procedures.indexOfFirst { it.id == updated.id }
                if (idx >= 0) procedures[idx] = updated else procedures.add(updated)
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ form result · mode=$mode · id=${updated.id} · code=${updated.code} · date=${updated.date}")
        renderList()
        persist()
    }

    private fun confirmDelete(pr: IpsProcedure, position: Int) {
        val label = IpsProcedureCatalog.getDisplay(pr.code, currentLang)
            ?: pr.label().takeIf { it.isNotBlank() }
            ?: getString(R.string.procedures_unnamed)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.procedures_delete_title)
            .setMessage(getString(R.string.procedures_delete_message, label))
            .setNegativeButton(R.string.procedures_delete_cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.procedures_delete_confirm) { d, _ ->
                val removed = procedures.removeAll { it.id == pr.id }
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete · pos=$position · id=${pr.id} · removed=$removed")
                renderList(); persist(); d.dismiss()
            }
            .show()
    }

    private fun persist() {
        val pid = profileId ?: return
        val snapshot = procedures.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ok = profilesRepo.saveProcedures(pid, snapshot)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · profileId=$pid · count=${snapshot.size} · ok=$ok")
                if (_binding != null) {
                    Toast.makeText(requireContext(), if (ok) R.string.procedures_saved else R.string.procedures_save_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ persist failed : ${e.message}", e)
                if (_binding != null) Toast.makeText(requireContext(), R.string.procedures_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
