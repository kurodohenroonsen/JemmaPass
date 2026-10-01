/*
 * PastProblemsEditFragment.kt — JEMMA Pass · IPS pillar "History of Past Illness" (FHIR-native)
 *
 * List + CRUD of the patient's past illnesses. Persistence goes through
 * ProfilesRepository.loadPastProblems() / savePastProblems(): the FHIR Bundle is
 * rewritten (source of truth) and `_j.ph` is re-projected for QR / mesh.
 * Same pattern as ProceduresEditFragment.
 *
 * Sprint 5: with the nav argument kind = "current" the same screen edits the 🩺
 * problem list (ProfilesRepository.loadProblems() / saveProblems(), `_j.cn`); the
 * rows are shown through the same UI model (IpsPastProblem without end date).
 *
 * Logging : tag JEMMA-PASTPROBLEMS-EDIT
 */
package be.heyman.android.jemmapassdemo.ui.profile.pastproblems

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
import be.heyman.android.jemmapassdemo.databinding.FragmentPastProblemsEditBinding
import be.heyman.android.jemmapassdemo.ips.IpsConditionSeverity
import be.heyman.android.jemmapassdemo.ips.IpsPastProblem
import be.heyman.android.jemmapassdemo.ips.IpsPastProblemStatus
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PastProblemsEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PASTPROBLEMS-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
        private const val ARG_KIND = "kind"

        fun toUi(p: be.heyman.android.jemmapassdemo.ips.IpsProblem): IpsPastProblem = IpsPastProblem(
            id = p.id, code = p.code, system = p.system, display = p.display, text = p.text,
            onset = p.onset, abatement = null, clinicalStatus = p.clinicalStatus, severity = p.severity, note = p.note,
        )

        fun toProblem(u: IpsPastProblem): be.heyman.android.jemmapassdemo.ips.IpsProblem = be.heyman.android.jemmapassdemo.ips.IpsProblem(
            id = u.id, code = u.code, system = u.system, display = u.display, text = u.text, onset = u.onset,
            clinicalStatus = be.heyman.android.jemmapassdemo.ips.IpsProblemStatus.normalize(u.clinicalStatus),
            severity = u.severity, note = u.note,
        )
    }

    @Inject lateinit var profilesRepo: ProfilesRepository
    @Inject lateinit var kb: KnowledgeBaseService

    private var _binding: FragmentPastProblemsEditBinding? = null
    private val binding get() = _binding!!

    private val argProfileId: String? by lazy { arguments?.getString(ARG_PROFILE_ID) }
    private val kind: String by lazy { arguments?.getString(ARG_KIND) ?: PastProblemFormBottomSheet.KIND_PAST }
    private val isCurrent: Boolean get() = kind == PastProblemFormBottomSheet.KIND_CURRENT
    private var profileId: String? = null
    private var loaded = false
    private val problems = mutableListOf<IpsPastProblem>()
    private lateinit var adapter: PastProblemsAdapter
    private val currentLang: String by lazy { Locale.getDefault().language.lowercase().take(2) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPastProblemsEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · argProfileId=$argProfileId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pastProblemsToolbarBack.setOnClickListener { findNavController().navigateUp() }

        adapter = PastProblemsAdapter(
            lang = currentLang,
            kb = kb,
            scope = viewLifecycleOwner.lifecycleScope,
            onTap = { pp, position -> openForm(PastProblemFormMode.EDIT, existing = pp, index = position) },
            onLongPress = { pp, position -> confirmDelete(pp, position) },
        )
        binding.pastProblemsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.pastProblemsRecycler.adapter = adapter
        binding.pastProblemsFabAdd.setOnClickListener { openForm(PastProblemFormMode.CREATE) }

        if (isCurrent) {
            binding.pastProblemsHeroTitle.setText(R.string.problems_edit_hero_title)
            binding.pastProblemsHeroEmoji.text = "🩺"
        }
        parentFragmentManager.setFragmentResultListener(PastProblemFormBottomSheet.resultKey(kind), viewLifecycleOwner) { _, bundle ->
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
            val list = if (isCurrent) profilesRepo.loadProblems(pid).map { toUi(it) } else profilesRepo.loadPastProblems(pid)
            problems.clear(); problems.addAll(list); loaded = true
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📂 loaded ${list.size} ${if (isCurrent) "problems" else "past problems"} for $pid")
            if (_binding != null) renderList()
        }
    }

    private fun renderList() {
        val sorted = problems.sortedWith(
            compareByDescending<IpsPastProblem> { it.onset != null || it.abatement != null }
                .thenByDescending { it.onset ?: it.abatement ?: "" }
        )
        adapter.submitList(sorted)
        val empty = problems.isEmpty()
        binding.pastProblemsEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.pastProblemsRecycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.pastProblemsEmptyText.setText(if (isCurrent) R.string.problems_empty_default else R.string.past_problems_empty_default)
        binding.pastProblemsFabAdd.isEnabled = true
        binding.pastProblemsCount.text = resources.getQuantityString(if (isCurrent) R.plurals.problems_count else R.plurals.past_problems_count, problems.size, problems.size)
    }

    private fun showEmptyStateNoProfile() {
        binding.pastProblemsEmptyState.visibility = View.VISIBLE
        binding.pastProblemsRecycler.visibility = View.GONE
        binding.pastProblemsEmptyText.setText(R.string.past_problems_empty_no_profile)
        binding.pastProblemsFabAdd.isEnabled = false
        binding.pastProblemsCount.text = resources.getQuantityString(if (isCurrent) R.plurals.problems_count else R.plurals.past_problems_count, 0, 0)
    }

    private fun openForm(mode: PastProblemFormMode, existing: IpsPastProblem? = null, index: Int = -1) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 openForm · mode=$mode · idx=$index · id=${existing?.id}")
        PastProblemFormBottomSheet.newInstance(mode, currentLang, existing, kind).show(parentFragmentManager, "past_problem_form")
    }

    private fun handleFormResult(bundle: Bundle) {
        val mode = PastProblemFormMode.valueOf(bundle.getString(PastProblemFormBottomSheet.ARG_MODE) ?: PastProblemFormMode.CREATE.name)
        if (bundle.getBoolean(PastProblemFormBottomSheet.ARG_DELETE, false)) {
            val targetId = bundle.getString(PastProblemFormBottomSheet.ARG_ID)
            problems.firstOrNull { it.id == targetId }?.let { confirmDelete(it, -1) }
                ?: Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ delete requested for unknown id=$targetId")
            return
        }
        val id = bundle.getString(PastProblemFormBottomSheet.ARG_ID)?.takeIf { it.isNotBlank() } ?: IpsPastProblem.newId()
        val code = bundle.getString(PastProblemFormBottomSheet.ARG_CODE)?.takeIf { it.isNotBlank() }
        val freeText = bundle.getString(PastProblemFormBottomSheet.ARG_TEXT)?.takeIf { it.isNotBlank() }
        val updated = IpsPastProblem(
            id = id,
            code = code,
            system = bundle.getString(PastProblemFormBottomSheet.ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() } ?: KnowledgeBaseService.SYSTEM_SNOMED,
            display = if (code != null) bundle.getString(PastProblemFormBottomSheet.ARG_DISPLAY)?.takeIf { it.isNotBlank() } else null,
            text = if (code == null) freeText else null,
            onset = bundle.getString(PastProblemFormBottomSheet.ARG_ONSET)?.takeIf { it.isNotBlank() },
            abatement = bundle.getString(PastProblemFormBottomSheet.ARG_ABATEMENT)?.takeIf { it.isNotBlank() },
            clinicalStatus = PastProblemFormBottomSheet.normalizeStatus(kind, bundle.getString(PastProblemFormBottomSheet.ARG_STATUS)),
            severity = IpsConditionSeverity.normalize(bundle.getString(PastProblemFormBottomSheet.ARG_SEVERITY)),
            note = bundle.getString(PastProblemFormBottomSheet.ARG_NOTE)?.takeIf { it.isNotBlank() },
        )
        when (mode) {
            PastProblemFormMode.CREATE -> problems.add(updated)
            PastProblemFormMode.EDIT -> {
                val idx = problems.indexOfFirst { it.id == updated.id }
                if (idx >= 0) problems[idx] = updated else problems.add(updated)
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ form result · mode=$mode · id=${updated.id} · code=${updated.code} · onset=${updated.onset} · abatement=${updated.abatement} · status=${updated.clinicalStatus}")
        renderList()
        persist()
    }

    private fun confirmDelete(pp: IpsPastProblem, position: Int) {
        val label = pp.label().takeIf { it.isNotBlank() } ?: getString(R.string.past_problems_unnamed)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isCurrent) R.string.problems_delete_title else R.string.past_problems_delete_title)
            .setMessage(getString(R.string.past_problems_delete_message, label))
            .setNegativeButton(R.string.past_problems_delete_cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.past_problems_delete_confirm) { d, _ ->
                val removed = problems.removeAll { it.id == pp.id }
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete · pos=$position · id=${pp.id} · removed=$removed")
                renderList(); persist(); d.dismiss()
            }
            .show()
    }

    private fun persist() {
        val pid = profileId ?: return
        val snapshot = problems.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ok = if (isCurrent) profilesRepo.saveProblems(pid, snapshot.map { toProblem(it) }) else profilesRepo.savePastProblems(pid, snapshot)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · profileId=$pid · count=${snapshot.size} · ok=$ok")
                if (_binding != null) {
                    Toast.makeText(requireContext(), when {
                        isCurrent && ok -> R.string.problems_saved
                        isCurrent -> R.string.problems_save_failed
                        ok -> R.string.past_problems_saved
                        else -> R.string.past_problems_save_failed
                    }, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ persist failed : ${e.message}", e)
                if (_binding != null) Toast.makeText(requireContext(), if (isCurrent) R.string.problems_save_failed else R.string.past_problems_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
