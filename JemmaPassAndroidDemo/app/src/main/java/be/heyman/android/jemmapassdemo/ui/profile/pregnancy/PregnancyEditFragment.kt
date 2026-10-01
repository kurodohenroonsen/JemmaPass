/*
 * PregnancyEditFragment.kt — JEMMA Pass · IPS pillar "History of Pregnancy" (FHIR-native)
 *
 * One screen (not a list): the pillar is a small fixed set of Observations.
 *   - Current status   : 82810-3 → Pregnant / Not pregnant / Unknown, with its date
 *   - Due date         : shown only when "Pregnant" — date + estimation method (edd-method codes)
 *   - Obstetric summary: nine optional counts (pregnancies-summary codes) + the summary date
 *
 * Save rebuilds the Observation list (ids of existing entries are kept, by LOINC code) and goes
 * through ProfilesRepository.savePregnancy(): Bundle rewritten, `_j.pg` re-projected.
 * Validation: due date not before the status date; live births ≤ total births.
 *
 * Logging : tag JEMMA-PREGNANCY-EDIT
 */
package be.heyman.android.jemmapassdemo.ui.profile.pregnancy

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.FragmentPregnancyEditBinding
import be.heyman.android.jemmapassdemo.ips.IpsPregnancyCodes
import be.heyman.android.jemmapassdemo.ips.IpsPregnancyKind
import be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PregnancyEditFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PREGNANCY-EDIT"
        private const val ARG_PROFILE_ID = "profileId"
        private const val DEFAULT_EDD_METHOD = "11778-8"

        /** LOINC outcome code → count field of the layout. */
        private val COUNT_FIELDS: List<Pair<String, Int>> = listOf(
            "11640-0" to R.id.pregnancy_count_11640_0,
            "11636-8" to R.id.pregnancy_count_11636_8,
            "11639-2" to R.id.pregnancy_count_11639_2,
            "11637-6" to R.id.pregnancy_count_11637_6,
            "11638-4" to R.id.pregnancy_count_11638_4,
            "11612-9" to R.id.pregnancy_count_11612_9,
            "11614-5" to R.id.pregnancy_count_11614_5,
            "11613-7" to R.id.pregnancy_count_11613_7,
            "33065-4" to R.id.pregnancy_count_33065_4,
        )

        fun statusLabelRes(code: String?): Int = when (code) {
            IpsPregnancyCodes.PREGNANT -> R.string.pregnancy_status_pregnant
            IpsPregnancyCodes.NOT_PREGNANT -> R.string.pregnancy_status_not_pregnant
            IpsPregnancyCodes.UNKNOWN -> R.string.pregnancy_status_unknown
            else -> R.string.pregnancy_status_none
        }

        fun eddMethodLabelRes(code: String?): Int = when (code) {
            "11779-6" -> R.string.pregnancy_edd_method_lmp
            "11780-4" -> R.string.pregnancy_edd_method_ovulation
            else -> R.string.pregnancy_edd_method_generic
        }
    }

    @Inject lateinit var profilesRepo: ProfilesRepository

    private var _binding: FragmentPregnancyEditBinding? = null
    private val binding get() = _binding!!

    private var profileId: String? = null
    private var existing: List<IpsPregnancyObs> = emptyList()
    private var loaded = false

    private var status: String? = null
    private var statusDate: String? = null
    private var edd: String? = null
    private var eddMethod: String = DEFAULT_EDD_METHOD
    private var summaryDate: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPregnancyEditBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · argProfileId=${arguments?.getString(ARG_PROFILE_ID)}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pregnancyToolbarBack.setOnClickListener { findNavController().navigateUp() }
        binding.pregnancyStatusRow.setOnClickListener { pickStatus() }
        binding.pregnancyStatusDateRow.setOnClickListener { pickDate(statusDate, allowFuture = false) { statusDate = it; render() } }
        binding.pregnancyStatusDateClear.setOnClickListener { statusDate = null; render() }
        binding.pregnancyEddRow.setOnClickListener { pickDate(edd, allowFuture = true) { edd = it; render() } }
        binding.pregnancyEddClear.setOnClickListener { edd = null; render() }
        binding.pregnancyEddMethodRow.setOnClickListener { pickEddMethod() }
        binding.pregnancySummaryDateRow.setOnClickListener { pickDate(summaryDate, allowFuture = false) { summaryDate = it; render() } }
        binding.pregnancySummaryDateClear.setOnClickListener { summaryDate = null; render() }
        binding.pregnancySaveBtn.setOnClickListener { trySave() }
        binding.pregnancyClearAllBtn.setOnClickListener { confirmClearAll() }

        if (loaded) render() else load()
    }

    private fun countField(code: String): EditText? =
        COUNT_FIELDS.firstOrNull { it.first == code }?.let { binding.root.findViewById(it.second) }

    // ─── Load ────────────────────────────────────────────────────────

    private fun load() {
        val pid = arguments?.getString(ARG_PROFILE_ID) ?: profilesRepo.currentProfileId
        if (pid == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no profile id")
            showError(getString(R.string.pregnancy_no_profile))
            binding.pregnancySaveBtn.isEnabled = false
            binding.pregnancyClearAllBtn.isEnabled = false
            return
        }
        profileId = pid
        viewLifecycleOwner.lifecycleScope.launch {
            existing = profilesRepo.loadPregnancy(pid)
            loaded = true
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📂 loaded ${existing.size} pregnancy obs for $pid")
            val st = existing.firstOrNull { it.kind == IpsPregnancyKind.STATUS }
            status = st?.valueCode?.takeIf { it in IpsPregnancyCodes.STATUS_ANSWERS }
            statusDate = st?.date
            val e = existing.firstOrNull { it.kind == IpsPregnancyKind.EDD }
            edd = e?.valueDate
            eddMethod = e?.code ?: DEFAULT_EDD_METHOD
            val outcomes = existing.filter { it.kind == IpsPregnancyKind.OUTCOME }
            summaryDate = outcomes.firstNotNullOfOrNull { it.date }
            if (_binding != null) {
                COUNT_FIELDS.forEach { (code, _) ->
                    countField(code)?.setText(outcomes.firstOrNull { it.code == code }?.count?.toString().orEmpty())
                }
                render()
            }
        }
    }

    // ─── Render ──────────────────────────────────────────────────────

    private fun render() {
        binding.pregnancyStatusLabel.setText(statusLabelRes(status))
        renderDate(statusDate, binding.pregnancyStatusDateLabel, binding.pregnancyStatusDateClear, R.string.pregnancy_date_hint)
        binding.pregnancyEddBlock.visibility = if (status == IpsPregnancyCodes.PREGNANT) View.VISIBLE else View.GONE
        renderDate(edd, binding.pregnancyEddLabel, binding.pregnancyEddClear, R.string.pregnancy_edd_hint)
        binding.pregnancyEddMethodLabel.setText(eddMethodLabelRes(eddMethod))
        renderDate(summaryDate, binding.pregnancySummaryDateLabel, binding.pregnancySummaryDateClear, R.string.pregnancy_date_hint)
        binding.pregnancyError.visibility = View.GONE
    }

    private fun renderDate(iso: String?, label: TextView, clear: View, hintRes: Int) {
        if (iso.isNullOrBlank()) {
            label.setText(hintRes); clear.visibility = View.GONE
        } else {
            label.text = iso; clear.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        binding.pregnancyError.text = message
        binding.pregnancyError.visibility = View.VISIBLE
    }

    // ─── Pickers ─────────────────────────────────────────────────────

    private fun pickStatus() {
        val codes: List<String?> = listOf(null) + IpsPregnancyCodes.STATUS_ANSWERS.keys
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pregnancy_status_pick_title)
            .setItems(codes.map { getString(statusLabelRes(it)) }.toTypedArray()) { _, which ->
                status = codes[which]
                if (status != IpsPregnancyCodes.PREGNANT) edd = null
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ status picked: ${status ?: "none"}")
                render()
            }
            .show()
    }

    private fun pickEddMethod() {
        val codes = IpsPregnancyCodes.EDD_METHODS.keys.toList()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pregnancy_edd_method_pick_title)
            .setItems(codes.map { getString(eddMethodLabelRes(it)) }.toTypedArray()) { _, which ->
                eddMethod = codes[which]
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ EDD method picked: $eddMethod")
                render()
            }
            .show()
    }

    private fun pickDate(current: String?, allowFuture: Boolean, onPicked: (String) -> Unit) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val initial = current?.takeIf { it.length == 10 }?.let { try { fmt.parse(it)?.time } catch (_: Exception) { null } }
            ?: MaterialDatePicker.todayInUtcMilliseconds()
        val builder = MaterialDatePicker.Builder.datePicker().setSelection(initial)
        if (!allowFuture) {
            builder.setCalendarConstraints(
                com.google.android.material.datepicker.CalendarConstraints.Builder()
                    .setEnd(MaterialDatePicker.todayInUtcMilliseconds()).build()
            )
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { utc ->
            val iso = fmt.format(Date(utc))
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 date picked: $iso")
            onPicked(iso)
        }
        picker.show(childFragmentManager, "pregnancy_date_picker")
    }

    // ─── Save ────────────────────────────────────────────────────────

    private fun readCounts(): Map<String, Int> =
        COUNT_FIELDS.mapNotNull { (code, _) ->
            countField(code)?.text?.toString()?.trim()?.toIntOrNull()?.let { code to it }
        }.toMap()

    private fun idFor(code: String): String =
        existing.firstOrNull { it.code == code }?.id ?: IpsPregnancyObs.newId()

    private fun trySave() {
        val pid = profileId ?: return
        if (!binding.pregnancySaveBtn.isEnabled) return
        val counts = readCounts()

        val error: Int? = when {
            status == null && edd != null -> R.string.pregnancy_validation_status_needed
            edd != null && statusDate != null && edd!! < statusDate!! -> R.string.pregnancy_validation_edd_past
            (counts["11636-8"] ?: 0) > (counts["11640-0"] ?: Int.MAX_VALUE) -> R.string.pregnancy_validation_live_gt_total
            else -> null
        }
        if (error != null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: ${resources.getResourceEntryName(error)}")
            showError(getString(error))
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            return
        }

        val obs = mutableListOf<IpsPregnancyObs>()
        status?.let { st ->
            obs += IpsPregnancyObs(id = idFor(IpsPregnancyCodes.STATUS), code = IpsPregnancyCodes.STATUS, valueCode = st, date = statusDate)
            if (st == IpsPregnancyCodes.PREGNANT) edd?.let { d ->
                obs += IpsPregnancyObs(id = idFor(eddMethod), code = eddMethod, valueDate = d, date = statusDate)
            }
        }
        COUNT_FIELDS.forEach { (code, _) ->
            counts[code]?.let { n -> obs += IpsPregnancyObs(id = idFor(code), code = code, count = n, date = summaryDate) }
        }
        persist(pid, obs)
    }

    private fun confirmClearAll() {
        val pid = profileId ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pregnancy_clear_all_title)
            .setMessage(R.string.pregnancy_clear_all_message)
            .setNegativeButton(R.string.pregnancy_cancel, null)
            .setPositiveButton(R.string.pregnancy_confirm) { _, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear all")
                status = null; statusDate = null; edd = null; eddMethod = DEFAULT_EDD_METHOD; summaryDate = null
                COUNT_FIELDS.forEach { (code, _) -> countField(code)?.setText("") }
                render()
                persist(pid, emptyList())
            }
            .show()
    }

    private fun persist(pid: String, obs: List<IpsPregnancyObs>) {
        binding.pregnancySaveBtn.isEnabled = false
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 persist · profileId=$pid · obs=${obs.size} · " +
            "status=${status ?: "none"} · edd=${edd ?: "-"} · counts=${obs.count { it.kind == IpsPregnancyKind.OUTCOME }}")
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = try {
                profilesRepo.savePregnancy(pid, obs)
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ persist failed : ${e.message}", e)
                false
            }
            if (ok) existing = obs
            if (_binding != null) {
                binding.pregnancySaveBtn.isEnabled = true
                Toast.makeText(requireContext(), if (ok) R.string.pregnancy_saved else R.string.pregnancy_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
