/*
 * ProcedureFormBottomSheet.kt — JEMMA Pass · IPS pillar "History of Procedures" (FHIR-native)
 *
 * CREATE / EDIT one FHIR R4 `Procedure` (Procedure-uv-ips) edited as an IpsProcedure :
 *   - Procedure (code)  : tappable card → KbDrugPickerDialog in "Procedure" mode
 *                         (curated IpsProcedureCatalog suggestions + live KB FTS5 search),
 *                         free-text fallback when nothing is picked (Procedure.code.text)
 *   - Date (performed)  : MaterialDatePicker, clearable → performedString "unknown"
 *   - Status            : completed | in-progress | not-done | stopped | unknown | entered-in-error
 *   - Body site, outcome, performer, location, note : free text
 *
 * Validation errors are inline (TextInputLayout.error) + toast + Log.w ; Save is
 * debounced ; a Delete button is offered in EDIT mode.
 *
 * Logging : tag JEMMA-PROCEDURES-FORM
 */
package be.heyman.android.jemmapassdemo.ui.profile.procedures

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.BottomSheetProcedureFormBinding
import be.heyman.android.jemmapassdemo.ips.IpsProcedure
import be.heyman.android.jemmapassdemo.ips.IpsProcedureStatus
import be.heyman.android.jemmapassdemo.pillars.IpsProcedureCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsProcedureStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.ui.common.KbDrugPickerDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ProcedureFormMode { CREATE, EDIT }

@AndroidEntryPoint
class ProcedureFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "JEMMA-PROCEDURES-FORM"

        const val RESULT_KEY = "procedure_form_result"
        const val ARG_MODE = "mode"
        const val ARG_ID = "id"
        const val ARG_LANG = "lang"
        const val ARG_CODE = "code"
        const val ARG_CODE_SYSTEM = "code_system"
        const val ARG_DISPLAY = "display"
        const val ARG_TEXT = "text"
        const val ARG_DATE = "date"
        const val ARG_STATUS = "status"
        const val ARG_BODY_SITE = "body_site"
        const val ARG_OUTCOME = "outcome"
        const val ARG_PERFORMER = "performer"
        const val ARG_LOCATION = "location"
        const val ARG_NOTE = "note"
        const val ARG_DELETE = "delete"

        private val ISO_DATE_REGEX = Regex("^\\d{4}(-\\d{2}(-\\d{2})?)?$")

        fun newInstance(mode: ProcedureFormMode, lang: String, existing: IpsProcedure? = null): ProcedureFormBottomSheet =
            ProcedureFormBottomSheet().apply {
                arguments = bundleOf(
                    ARG_MODE to mode.name,
                    ARG_ID to existing?.id,
                    ARG_LANG to lang,
                    ARG_CODE to existing?.code,
                    ARG_CODE_SYSTEM to existing?.system,
                    ARG_DISPLAY to existing?.display,
                    ARG_TEXT to existing?.text,
                    ARG_DATE to existing?.date,
                    ARG_STATUS to existing?.status,
                    ARG_BODY_SITE to existing?.bodySite,
                    ARG_OUTCOME to existing?.outcome,
                    ARG_PERFORMER to existing?.performer,
                    ARG_LOCATION to existing?.location,
                    ARG_NOTE to existing?.note,
                )
            }
    }

    private var _binding: BottomSheetProcedureFormBinding? = null
    private val binding get() = _binding!!

    private val mode: ProcedureFormMode by lazy { ProcedureFormMode.valueOf(arguments?.getString(ARG_MODE) ?: ProcedureFormMode.CREATE.name) }
    private val lang: String by lazy { arguments?.getString(ARG_LANG) ?: "en" }

    private var pickedCode: String? = null
    private var pickedCodeSystem: String? = null
    private var pickedDisplay: String? = null
    private var pickedDateIso: String? = null
    private var pickedStatus: String = IpsProcedureStatusCatalog.DEFAULT_CODE

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetProcedureFormBinding.inflate(inflater, container, false)
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
        binding.procedureFormTitle.setText(if (mode == ProcedureFormMode.CREATE) R.string.procedure_form_title_create else R.string.procedure_form_title_edit)

        pickedCode = arguments?.getString(ARG_CODE)?.takeIf { it.isNotBlank() }
        pickedCodeSystem = arguments?.getString(ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() }
        pickedDisplay = arguments?.getString(ARG_DISPLAY)?.takeIf { it.isNotBlank() }
        pickedDateIso = arguments?.getString(ARG_DATE)?.takeIf { it.isNotBlank() }
        pickedStatus = IpsProcedureStatus.normalize(arguments?.getString(ARG_STATUS))

        binding.procedureFormText.setText(arguments?.getString(ARG_TEXT).orEmpty())
        binding.procedureFormBodySite.setText(arguments?.getString(ARG_BODY_SITE).orEmpty())
        binding.procedureFormOutcome.setText(arguments?.getString(ARG_OUTCOME).orEmpty())
        binding.procedureFormPerformer.setText(arguments?.getString(ARG_PERFORMER).orEmpty())
        binding.procedureFormLocation.setText(arguments?.getString(ARG_LOCATION).orEmpty())
        binding.procedureFormNote.setText(arguments?.getString(ARG_NOTE).orEmpty())

        renderCodeLabel(); renderDateLabel(); renderStatusLabel()

        binding.procedureFormCodeCard.setOnClickListener { openPicker() }
        binding.procedureFormCodeClear.setOnClickListener { clearCode() }
        binding.procedureFormDateRow.setOnClickListener { openDatePicker() }
        binding.procedureFormDateClear.setOnClickListener { pickedDateIso = null; renderDateLabel() }
        binding.procedureFormStatusRow.setOnClickListener { openStatusPicker() }
        binding.procedureFormText.doAfterTextChanged { binding.procedureFormTextLayout.error = null }

        binding.procedureFormCancelBtn.setOnClickListener { Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel"); dismiss() }
        binding.procedureFormSaveBtn.setOnClickListener { trySubmit() }
        binding.procedureFormDeleteBtn.visibility = if (mode == ProcedureFormMode.EDIT) View.VISIBLE else View.GONE
        binding.procedureFormDeleteBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete requested · id=${arguments?.getString(ARG_ID)}")
            setFragmentResult(RESULT_KEY, bundleOf(ARG_MODE to mode.name, ARG_ID to arguments?.getString(ARG_ID), ARG_DELETE to true))
            dismiss()
        }
    }

    // ─── Procedure code ──────────────────────────────────────────────

    private fun renderCodeLabel() {
        val code = pickedCode
        if (code.isNullOrBlank()) {
            binding.procedureFormCodeDisplay.setText(R.string.procedure_form_code_hint)
            binding.procedureFormCodeClear.visibility = View.GONE
            binding.procedureFormTextLayout.visibility = View.VISIBLE
            return
        }
        binding.procedureFormCodeClear.visibility = View.VISIBLE
        binding.procedureFormTextLayout.visibility = View.GONE
        val label = IpsProcedureCatalog.getDisplay(code, lang)
            ?: pickedDisplay?.let { IpsTranslationsRepository.cleanBilingual(it, lang) }
            ?: code
        binding.procedureFormCodeDisplay.text = label
    }

    private fun clearCode() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear procedure code")
        pickedCode = null; pickedCodeSystem = null; pickedDisplay = null
        renderCodeLabel()
    }

    private fun openPicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open procedure picker · lang=$lang")
        KbDrugPickerDialog
            .newInstance(
                title = getString(R.string.procedure_form_code_picker_title),
                lang = lang,
                category = KbDrugPickerDialog.CATEGORY_PROCEDURE,
                suggestions = IpsProcedureCatalog.ALL.map { it.code to "${it.emoji}  ${it.pick(lang)}" },
                suggestionsSystem = IpsProcedureCatalog.CODE_SYSTEM,
            )
            .setOnPicked { picked ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ procedure picked · code=${picked.code} · system=${picked.system} · display='${picked.display}'")
                pickedCode = picked.code
                pickedCodeSystem = picked.system
                pickedDisplay = IpsProcedureCatalog.byCode(picked.code)?.displayEn ?: picked.display.substringAfter("  ").trim()
                renderCodeLabel()
            }
            .show(childFragmentManager, "procedure_picker")
    }

    // ─── Date ────────────────────────────────────────────────────────

    private fun renderDateLabel() {
        val iso = pickedDateIso
        if (iso.isNullOrBlank()) {
            binding.procedureFormDateLabel.setText(R.string.procedure_form_date_hint)
            binding.procedureFormDateClear.visibility = View.GONE
        } else {
            binding.procedureFormDateLabel.text = iso
            binding.procedureFormDateClear.visibility = View.VISIBLE
        }
    }

    private fun openDatePicker() {
        val constraints = CalendarConstraints.Builder().setEnd(MaterialDatePicker.todayInUtcMilliseconds()).build()
        val initial = pickedDateIso?.let { parseIsoDateUtc(it) } ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.procedure_form_date_pick_title)
            .setSelection(initial)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            pickedDateIso = formatUtcMillisAsIso(utcMillis)
            renderDateLabel()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 date picked: $pickedDateIso")
        }
        picker.show(childFragmentManager, "procedure_date_picker")
    }

    private fun parseIsoDateUtc(iso: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time
    } catch (_: Exception) { null }

    private fun formatUtcMillisAsIso(utcMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(utcMillis))

    // ─── Status ──────────────────────────────────────────────────────

    private fun renderStatusLabel() {
        val entry = IpsProcedureStatusCatalog.byCode(pickedStatus)
        binding.procedureFormStatusLabel.text = if (entry == null) pickedStatus else "${entry.emoji} ${entry.pick(lang)}"
    }

    private fun openStatusPicker() {
        val catalog = IpsProcedureStatusCatalog.ALL
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.procedure_form_status_pick_title)
            .setItems(catalog.map { "${it.emoji}  ${it.pick(lang)}" }.toTypedArray()) { _, which ->
                pickedStatus = catalog[which].code
                renderStatusLabel()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ status picked: $pickedStatus")
            }
            .show()
    }

    // ─── Submit ──────────────────────────────────────────────────────

    private fun trySubmit() {
        if (!binding.procedureFormSaveBtn.isEnabled) return
        binding.procedureFormSaveBtn.isEnabled = false

        val code = pickedCode?.takeIf { it.isNotBlank() }
        val freeText = binding.procedureFormText.text?.toString()?.trim().orEmpty()
        if (code == null && freeText.isBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: no procedure picked nor typed")
            binding.procedureFormTextLayout.error = getString(R.string.procedure_form_validation_code)
            Toast.makeText(requireContext(), R.string.procedure_form_validation_code, Toast.LENGTH_SHORT).show()
            binding.procedureFormText.requestFocus()
            binding.procedureFormSaveBtn.isEnabled = true
            return
        }
        val date = pickedDateIso?.takeIf { it.isNotBlank() && ISO_DATE_REGEX.matches(it) }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 submit · mode=$mode · code=$code · text='$freeText' · date=$date · status=$pickedStatus")
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                ARG_MODE to mode.name,
                ARG_ID to arguments?.getString(ARG_ID),
                ARG_CODE to code,
                ARG_CODE_SYSTEM to (if (code != null) (pickedCodeSystem ?: IpsProcedureCatalog.CODE_SYSTEM) else null),
                ARG_DISPLAY to (if (code != null) pickedDisplay else null),
                ARG_TEXT to (if (code == null) freeText.ifBlank { null } else null),
                ARG_DATE to date,
                ARG_STATUS to pickedStatus,
                ARG_BODY_SITE to binding.procedureFormBodySite.text?.toString()?.trim()?.ifBlank { null },
                ARG_OUTCOME to binding.procedureFormOutcome.text?.toString()?.trim()?.ifBlank { null },
                ARG_PERFORMER to binding.procedureFormPerformer.text?.toString()?.trim()?.ifBlank { null },
                ARG_LOCATION to binding.procedureFormLocation.text?.toString()?.trim()?.ifBlank { null },
                ARG_NOTE to binding.procedureFormNote.text?.toString()?.trim()?.ifBlank { null },
            ),
        )
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
