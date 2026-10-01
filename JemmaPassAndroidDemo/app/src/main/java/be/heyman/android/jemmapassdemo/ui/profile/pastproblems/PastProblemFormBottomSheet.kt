/*
 * PastProblemFormBottomSheet.kt — JEMMA Pass · IPS pillar "History of Past Illness" (FHIR-native)
 *
 * CREATE / EDIT one FHIR R4 `Condition` (Condition-uv-ips) edited as an IpsPastProblem :
 *   - Illness (code)   : tappable card → KbConditionPicker (IPS problems-snomed-ct-ips-free-set,
 *                        localised labels from the KB); the English term of the picked code is
 *                        fetched for Coding.display. Free-text fallback (Condition.code.text).
 *   - Onset / end      : "exact date" (MaterialDatePicker) or "year only" (old illnesses are
 *                        often remembered by year) — clearable; end never before onset
 *   - Clinical status  : resolved | inactive | remission
 *   - Severity         : none | mild | moderate | severe (IPS LOINC answers)
 *   - Note             : free text
 *
 * Validation errors are inline + toast + Log.w ; Save is debounced ; Delete in EDIT mode.
 *
 * Logging : tag JEMMA-PASTPROBLEMS-FORM
 */
package be.heyman.android.jemmapassdemo.ui.profile.pastproblems

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.BottomSheetPastProblemFormBinding
import be.heyman.android.jemmapassdemo.ips.IpsCodeSystems
import be.heyman.android.jemmapassdemo.ips.IpsConditionSeverity
import be.heyman.android.jemmapassdemo.ips.IpsPastProblem
import be.heyman.android.jemmapassdemo.ips.IpsPastProblemStatus
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.ui.common.KbConditionPicker
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.launch

enum class PastProblemFormMode { CREATE, EDIT }

@AndroidEntryPoint
class PastProblemFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "JEMMA-PASTPROBLEMS-FORM"

        const val RESULT_KEY = "past_problem_form_result"
        const val ARG_MODE = "mode"
        const val ARG_ID = "id"
        const val ARG_LANG = "lang"
        const val ARG_CODE = "code"
        const val ARG_CODE_SYSTEM = "code_system"
        const val ARG_DISPLAY = "display"
        const val ARG_TEXT = "text"
        const val ARG_ONSET = "onset"
        const val ARG_ABATEMENT = "abatement"
        const val ARG_STATUS = "status"
        const val ARG_SEVERITY = "severity"
        const val ARG_NOTE = "note"
        const val ARG_DELETE = "delete"

        private val ISO_DATE_REGEX = Regex("^\\d{4}(-\\d{2}(-\\d{2})?)?$")

        fun newInstance(mode: PastProblemFormMode, lang: String, existing: IpsPastProblem? = null): PastProblemFormBottomSheet =
            PastProblemFormBottomSheet().apply {
                arguments = bundleOf(
                    ARG_MODE to mode.name,
                    ARG_ID to existing?.id,
                    ARG_LANG to lang,
                    ARG_CODE to existing?.code,
                    ARG_CODE_SYSTEM to existing?.system,
                    ARG_DISPLAY to existing?.display,
                    ARG_TEXT to existing?.text,
                    ARG_ONSET to existing?.onset,
                    ARG_ABATEMENT to existing?.abatement,
                    ARG_STATUS to existing?.clinicalStatus,
                    ARG_SEVERITY to existing?.severity,
                    ARG_NOTE to existing?.note,
                )
            }

        fun statusLabelRes(code: String): Int = when (code) {
            IpsPastProblemStatus.INACTIVE -> R.string.past_problem_status_inactive
            IpsPastProblemStatus.REMISSION -> R.string.past_problem_status_remission
            else -> R.string.past_problem_status_resolved
        }

        fun severityLabelRes(code: String?): Int = when (code) {
            IpsConditionSeverity.MILD -> R.string.past_problem_severity_mild
            IpsConditionSeverity.MODERATE -> R.string.past_problem_severity_moderate
            IpsConditionSeverity.SEVERE -> R.string.past_problem_severity_severe
            else -> R.string.past_problem_severity_none
        }
    }

    @Inject lateinit var kb: KnowledgeBaseService

    private var _binding: BottomSheetPastProblemFormBinding? = null
    private val binding get() = _binding!!

    private val mode: PastProblemFormMode by lazy { PastProblemFormMode.valueOf(arguments?.getString(ARG_MODE) ?: PastProblemFormMode.CREATE.name) }
    private val lang: String by lazy { arguments?.getString(ARG_LANG) ?: "en" }

    private var pickedCode: String? = null
    private var pickedCodeSystem: String? = null
    /** English display (Coding.display). */
    private var pickedDisplay: String? = null
    /** Label shown in the card (localised when picked from a FR/JA list). */
    private var pickedLabel: String? = null
    private var onsetIso: String? = null
    private var abatementIso: String? = null
    private var pickedStatus: String = IpsPastProblemStatus.RESOLVED
    private var pickedSeverity: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetPastProblemFormBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · mode=$mode · lang=$lang")
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
        BottomSheetBehavior.from(sheet).apply { state = BottomSheetBehavior.STATE_EXPANDED; skipCollapsed = true }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pastProblemFormTitle.setText(
            if (mode == PastProblemFormMode.CREATE) R.string.past_problem_form_title_create else R.string.past_problem_form_title_edit
        )

        pickedCode = arguments?.getString(ARG_CODE)?.takeIf { it.isNotBlank() }
        pickedCodeSystem = arguments?.getString(ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() }
        pickedDisplay = arguments?.getString(ARG_DISPLAY)?.takeIf { it.isNotBlank() }
        onsetIso = arguments?.getString(ARG_ONSET)?.takeIf { it.isNotBlank() }
        abatementIso = arguments?.getString(ARG_ABATEMENT)?.takeIf { it.isNotBlank() }
        pickedStatus = IpsPastProblemStatus.normalize(arguments?.getString(ARG_STATUS))
        pickedSeverity = IpsConditionSeverity.normalize(arguments?.getString(ARG_SEVERITY))

        binding.pastProblemFormText.setText(arguments?.getString(ARG_TEXT).orEmpty())
        binding.pastProblemFormNote.setText(arguments?.getString(ARG_NOTE).orEmpty())

        renderCodeLabel(); renderDates(); renderStatusLabel(); renderSeverityLabel()
        localizeExistingCode()

        binding.pastProblemFormCodeCard.setOnClickListener { openPicker() }
        binding.pastProblemFormCodeClear.setOnClickListener { clearCode() }
        binding.pastProblemFormOnsetRow.setOnClickListener { openDateChooser(isOnset = true) }
        binding.pastProblemFormOnsetClear.setOnClickListener { onsetIso = null; renderDates() }
        binding.pastProblemFormAbatementRow.setOnClickListener { openDateChooser(isOnset = false) }
        binding.pastProblemFormAbatementClear.setOnClickListener { abatementIso = null; renderDates() }
        binding.pastProblemFormStatusRow.setOnClickListener { openStatusPicker() }
        binding.pastProblemFormSeverityRow.setOnClickListener { openSeverityPicker() }
        binding.pastProblemFormText.doAfterTextChanged { binding.pastProblemFormTextLayout.error = null }

        binding.pastProblemFormCancelBtn.setOnClickListener { Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel"); dismiss() }
        binding.pastProblemFormSaveBtn.setOnClickListener { trySubmit() }
        binding.pastProblemFormDeleteBtn.visibility = if (mode == PastProblemFormMode.EDIT) View.VISIBLE else View.GONE
        binding.pastProblemFormDeleteBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete requested · id=${arguments?.getString(ARG_ID)}")
            setFragmentResult(RESULT_KEY, bundleOf(ARG_MODE to mode.name, ARG_ID to arguments?.getString(ARG_ID), ARG_DELETE to true))
            dismiss()
        }
    }

    // ─── Illness code ────────────────────────────────────────────────

    private fun renderCodeLabel() {
        val code = pickedCode
        if (code.isNullOrBlank()) {
            binding.pastProblemFormCodeDisplay.setText(R.string.past_problem_form_code_hint)
            binding.pastProblemFormCodeClear.visibility = View.GONE
            binding.pastProblemFormTextLayout.visibility = View.VISIBLE
            return
        }
        binding.pastProblemFormCodeClear.visibility = View.VISIBLE
        binding.pastProblemFormTextLayout.visibility = View.GONE
        val label = pickedLabel ?: pickedDisplay?.let { IpsTranslationsRepository.cleanBilingual(it, lang) } ?: code
        binding.pastProblemFormCodeDisplay.text = label
    }

    /** EDIT mode: show the stored code in the UI language (KB free-set translation). */
    private fun localizeExistingCode() {
        val code = pickedCode ?: return
        if (lang == "en") return
        viewLifecycleOwner.lifecycleScope.launch {
            val localized = try {
                kb.getLocalizedDisplay(code, pickedCodeSystem ?: KnowledgeBaseService.SYSTEM_SNOMED, lang)
            } catch (e: Throwable) { null }
            if (!localized.isNullOrBlank() && _binding != null && pickedCode == code) {
                pickedLabel = localized
                renderCodeLabel()
            }
        }
    }

    private fun clearCode() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear illness code")
        pickedCode = null; pickedCodeSystem = null; pickedDisplay = null; pickedLabel = null
        renderCodeLabel()
    }

    private fun openPicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open past-illness picker · lang=$lang")
        KbConditionPicker
            .newInstance(title = getString(R.string.past_problem_form_code_picker_title), lang = lang)
            .setOnPicked { picked ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ illness picked · code=${picked.code} · system=${picked.system} · display='${picked.display}'")
                pickedCode = picked.code
                pickedCodeSystem = picked.system
                pickedLabel = picked.display
                pickedDisplay = picked.display
                renderCodeLabel()
                // Coding.display must be the English SNOMED term, not the FR/JA label.
                viewLifecycleOwner.lifecycleScope.launch {
                    val en = try { kb.getIpsDisplayEn(picked.code, picked.system) } catch (e: Throwable) { null }
                    if (pickedCode == picked.code) {
                        pickedDisplay = en ?: picked.display
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌐 English display · code=${picked.code} · en='${en ?: "—"}'")
                    }
                }
            }
            .show(childFragmentManager, "past_problem_picker")
    }

    // ─── Dates (exact or year only) ──────────────────────────────────

    private fun renderDates() {
        renderDate(onsetIso, binding.pastProblemFormOnsetLabel, binding.pastProblemFormOnsetClear, R.string.past_problem_form_onset_hint)
        renderDate(abatementIso, binding.pastProblemFormAbatementLabel, binding.pastProblemFormAbatementClear, R.string.past_problem_form_abatement_hint)
        binding.pastProblemFormAbatementError.visibility =
            if (IpsPastProblem.isChronologyValid(onsetIso, abatementIso)) View.GONE else View.VISIBLE
    }

    private fun renderDate(iso: String?, label: android.widget.TextView, clear: View, hintRes: Int) {
        if (iso.isNullOrBlank()) {
            label.setText(hintRes)
            clear.visibility = View.GONE
        } else {
            label.text = iso
            clear.visibility = View.VISIBLE
        }
    }

    private fun openDateChooser(isOnset: Boolean) {
        val items = arrayOf(getString(R.string.past_problem_form_date_mode_exact), getString(R.string.past_problem_form_date_mode_year))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isOnset) R.string.past_problem_form_onset_pick_title else R.string.past_problem_form_abatement_pick_title)
            .setItems(items) { _, which -> if (which == 0) openDatePicker(isOnset) else openYearDialog(isOnset) }
            .show()
    }

    private fun setDate(isOnset: Boolean, iso: String) {
        if (isOnset) onsetIso = iso else abatementIso = iso
        renderDates()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 ${if (isOnset) "onset" else "abatement"} set: $iso")
    }

    private fun openDatePicker(isOnset: Boolean) {
        val constraints = CalendarConstraints.Builder().setEnd(MaterialDatePicker.todayInUtcMilliseconds()).build()
        val current = if (isOnset) onsetIso else abatementIso
        val initial = current?.takeIf { it.length == 10 }?.let { parseIsoDateUtc(it) } ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isOnset) R.string.past_problem_form_onset_pick_title else R.string.past_problem_form_abatement_pick_title)
            .setSelection(initial)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis -> setDate(isOnset, formatUtcMillisAsIso(utcMillis)) }
        picker.show(childFragmentManager, if (isOnset) "past_problem_onset_picker" else "past_problem_abatement_picker")
    }

    private fun openYearDialog(isOnset: Boolean) {
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(4))
            hint = getString(R.string.past_problem_form_year_hint)
            setText((if (isOnset) onsetIso else abatementIso)?.take(4).orEmpty())
        }
        val container = FrameLayout(requireContext()).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isOnset) R.string.past_problem_form_onset_pick_title else R.string.past_problem_form_abatement_pick_title)
            .setView(container)
            .setNegativeButton(R.string.past_problem_form_cancel, null)
            .setPositiveButton(R.string.past_problem_form_ok) { _, _ ->
                val year = input.text?.toString()?.trim()?.toIntOrNull()
                if (year == null || year < 1900 || year > thisYear) {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ invalid year '${input.text}'")
                    Toast.makeText(requireContext(), R.string.past_problem_form_year_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    setDate(isOnset, year.toString())
                }
            }
            .show()
    }

    private fun parseIsoDateUtc(iso: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time
    } catch (_: Exception) { null }

    private fun formatUtcMillisAsIso(utcMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(utcMillis))

    // ─── Status / severity ───────────────────────────────────────────

    private fun renderStatusLabel() { binding.pastProblemFormStatusLabel.setText(statusLabelRes(pickedStatus)) }

    private fun renderSeverityLabel() { binding.pastProblemFormSeverityLabel.setText(severityLabelRes(pickedSeverity)) }

    private fun openStatusPicker() {
        val codes = IpsPastProblemStatus.ALL
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.past_problem_form_status_pick_title)
            .setItems(codes.map { getString(statusLabelRes(it)) }.toTypedArray()) { _, which ->
                pickedStatus = codes[which]
                renderStatusLabel()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ status picked: $pickedStatus")
            }
            .show()
    }

    private fun openSeverityPicker() {
        val codes: List<String?> = listOf(null) + IpsConditionSeverity.ALL.reversed()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.past_problem_form_severity_pick_title)
            .setItems(codes.map { getString(severityLabelRes(it)) }.toTypedArray()) { _, which ->
                pickedSeverity = codes[which]
                renderSeverityLabel()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ severity picked: ${pickedSeverity ?: "none"}")
            }
            .show()
    }

    // ─── Submit ──────────────────────────────────────────────────────

    private fun trySubmit() {
        if (!binding.pastProblemFormSaveBtn.isEnabled) return
        binding.pastProblemFormSaveBtn.isEnabled = false

        val code = pickedCode?.takeIf { it.isNotBlank() }
        val freeText = binding.pastProblemFormText.text?.toString()?.trim().orEmpty()
        if (code == null && freeText.isBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: no illness picked nor typed")
            binding.pastProblemFormTextLayout.error = getString(R.string.past_problem_form_validation_code)
            Toast.makeText(requireContext(), R.string.past_problem_form_validation_code, Toast.LENGTH_SHORT).show()
            binding.pastProblemFormText.requestFocus()
            binding.pastProblemFormSaveBtn.isEnabled = true
            return
        }
        val onset = onsetIso?.takeIf { it.isNotBlank() && ISO_DATE_REGEX.matches(it) }
        val abatement = abatementIso?.takeIf { it.isNotBlank() && ISO_DATE_REGEX.matches(it) }
        if (!IpsPastProblem.isChronologyValid(onset, abatement)) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: abatement $abatement before onset $onset")
            binding.pastProblemFormAbatementError.visibility = View.VISIBLE
            Toast.makeText(requireContext(), R.string.past_problem_form_validation_chronology, Toast.LENGTH_SHORT).show()
            binding.pastProblemFormSaveBtn.isEnabled = true
            return
        }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 submit · mode=$mode · code=$code · display='${pickedDisplay ?: ""}' · text='$freeText' · onset=$onset · abatement=$abatement · status=$pickedStatus · severity=${pickedSeverity ?: "none"}")
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                ARG_MODE to mode.name,
                ARG_ID to arguments?.getString(ARG_ID),
                ARG_CODE to code,
                ARG_CODE_SYSTEM to (if (code != null) (pickedCodeSystem ?: IpsCodeSystems.SNOMED) else null),
                ARG_DISPLAY to (if (code != null) pickedDisplay else null),
                ARG_TEXT to (if (code == null) freeText.ifBlank { null } else null),
                ARG_ONSET to onset,
                ARG_ABATEMENT to abatement,
                ARG_STATUS to pickedStatus,
                ARG_SEVERITY to pickedSeverity,
                ARG_NOTE to binding.pastProblemFormNote.text?.toString()?.trim()?.ifBlank { null },
            ),
        )
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
