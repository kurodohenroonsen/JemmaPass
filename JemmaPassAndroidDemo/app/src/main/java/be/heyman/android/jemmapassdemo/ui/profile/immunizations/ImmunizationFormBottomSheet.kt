/*
 * ImmunizationFormBottomSheet.kt — JEMMA Pass · IPS pillar "Immunizations" (FHIR-native)
 *
 * Bottom sheet to CREATE or EDIT one FHIR R4 `Immunization` (IPS profile
 * Immunization-uv-ips), edited as an IpsImmunization :
 *
 *   - Vaccine (vaccineCode)  : tappable card → IpsCodePickerDialog fed by the
 *                              curated IpsVaccineCatalog (short EN/FR/JA labels)
 *                              + the SNOMED vaccine products of the bundled
 *                              asset translations. A free-text field is offered
 *                              when nothing is picked (vaccineCode.text only).
 *   - Date (occurrence[x])   : MaterialDatePicker → YYYY-MM-DD, clearable
 *                              (unknown date → occurrenceString "unknown")
 *   - Status                 : completed | not-done | entered-in-error
 *   - Dose n° / series doses : protocolApplied.doseNumber / seriesDoses
 *   - Lot, manufacturer, performer, note : free text
 *
 * Result : setFragmentResult(RESULT_KEY, flat Bundle) consumed by
 * ImmunizationsEditFragment, which rebuilds the IpsImmunization (keeping
 * the stable id in EDIT mode) and persists through the FHIR-native store.
 *
 * Logging : tag JEMMA-IMMUNIZATIONS-FORM
 */
package be.heyman.android.jemmapassdemo.ui.profile.immunizations

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.BottomSheetImmunizationFormBinding
import be.heyman.android.jemmapassdemo.ips.IpsImmunization
import be.heyman.android.jemmapassdemo.ips.IpsImmunizationStatus
import be.heyman.android.jemmapassdemo.pillars.IpsImmunizationStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.pillars.IpsVaccineCatalog
import be.heyman.android.jemmapassdemo.ui.common.IpsCodePickerDialog
import be.heyman.android.jemmapassdemo.ui.common.IpsPickerItem
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
import javax.inject.Inject
import kotlinx.coroutines.launch

enum class ImmunizationFormMode { CREATE, EDIT }

@AndroidEntryPoint
class ImmunizationFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "JEMMA-IMMUNIZATIONS-FORM"

        const val RESULT_KEY = "immunization_form_result"
        const val ARG_MODE = "mode"
        const val ARG_ID = "id"
        const val ARG_LANG = "lang"
        const val ARG_CODE = "code"
        const val ARG_CODE_SYSTEM = "code_system"
        const val ARG_DISPLAY = "display"
        const val ARG_TEXT = "text"
        const val ARG_DATE = "date"
        const val ARG_STATUS = "status"
        const val ARG_DOSE_NUMBER = "dose_number"
        const val ARG_SERIES_DOSES = "series_doses"
        const val ARG_LOT = "lot"
        const val ARG_MANUFACTURER = "manufacturer"
        const val ARG_PERFORMER = "performer"
        const val ARG_NOTE = "note"
        /** Result flag: the user asked to delete the entry identified by ARG_ID. */
        const val ARG_DELETE = "delete"

        private val ISO_DATE_REGEX = Regex("^\\d{4}(-\\d{2}(-\\d{2})?)?$")

        fun newInstance(
            mode: ImmunizationFormMode,
            lang: String,
            existing: IpsImmunization? = null,
        ): ImmunizationFormBottomSheet = ImmunizationFormBottomSheet().apply {
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
                ARG_DOSE_NUMBER to (existing?.doseNumber ?: 0),
                ARG_SERIES_DOSES to (existing?.seriesDoses ?: 0),
                ARG_LOT to existing?.lotNumber,
                ARG_MANUFACTURER to existing?.manufacturer,
                ARG_PERFORMER to existing?.performer,
                ARG_NOTE to existing?.note,
            )
        }
    }

    @Inject
    lateinit var translations: IpsTranslationsRepository

    private var _binding: BottomSheetImmunizationFormBinding? = null
    private val binding get() = _binding!!

    private val mode: ImmunizationFormMode by lazy {
        ImmunizationFormMode.valueOf(arguments?.getString(ARG_MODE) ?: ImmunizationFormMode.CREATE.name)
    }
    private val lang: String by lazy { arguments?.getString(ARG_LANG) ?: "en" }

    private var pickedCode: String? = null
    private var pickedCodeSystem: String? = null
    private var pickedDisplay: String? = null
    private var pickedDateIso: String? = null
    private var pickedStatus: String = IpsImmunizationStatusCatalog.DEFAULT_CODE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetImmunizationFormBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · mode=$mode · lang=$lang")
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Long form → open fully expanded (same trick as AllergyFormBottomSheet).
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.immunizationFormTitle.setText(
            if (mode == ImmunizationFormMode.CREATE) R.string.immunization_form_title_create
            else R.string.immunization_form_title_edit,
        )

        // Pre-fill
        pickedCode = arguments?.getString(ARG_CODE)?.takeIf { it.isNotBlank() }
        pickedCodeSystem = arguments?.getString(ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() }
        pickedDisplay = arguments?.getString(ARG_DISPLAY)?.takeIf { it.isNotBlank() }
        pickedDateIso = arguments?.getString(ARG_DATE)?.takeIf { it.isNotBlank() }
        pickedStatus = IpsImmunizationStatus.normalize(arguments?.getString(ARG_STATUS))

        binding.immunizationFormText.setText(arguments?.getString(ARG_TEXT).orEmpty())
        val dn = arguments?.getInt(ARG_DOSE_NUMBER, 0) ?: 0
        val sd = arguments?.getInt(ARG_SERIES_DOSES, 0) ?: 0
        binding.immunizationFormDoseNumber.setText(if (dn > 0) dn.toString() else "")
        binding.immunizationFormSeriesDoses.setText(if (sd > 0) sd.toString() else "")
        binding.immunizationFormLot.setText(arguments?.getString(ARG_LOT).orEmpty())
        binding.immunizationFormManufacturer.setText(arguments?.getString(ARG_MANUFACTURER).orEmpty())
        binding.immunizationFormPerformer.setText(arguments?.getString(ARG_PERFORMER).orEmpty())
        binding.immunizationFormNote.setText(arguments?.getString(ARG_NOTE).orEmpty())

        renderVaccineLabel()
        renderDateLabel()
        renderStatusLabel()

        binding.immunizationFormVaccineCard.setOnClickListener { openVaccinePicker() }
        binding.immunizationFormVaccineClear.setOnClickListener { clearVaccine() }
        binding.immunizationFormDateRow.setOnClickListener { openDatePicker() }
        binding.immunizationFormDateClear.setOnClickListener { clearDate() }
        binding.immunizationFormStatusRow.setOnClickListener { openStatusPicker() }

        // Inline errors clear as soon as the user edits the offending field.
        binding.immunizationFormText.doAfterTextChanged { binding.immunizationFormTextLayout.error = null }
        binding.immunizationFormDoseNumber.doAfterTextChanged {
            binding.immunizationFormDoseNumberLayout.error = null
            binding.immunizationFormSeriesDosesLayout.error = null
        }
        binding.immunizationFormSeriesDoses.doAfterTextChanged {
            binding.immunizationFormDoseNumberLayout.error = null
            binding.immunizationFormSeriesDosesLayout.error = null
        }

        binding.immunizationFormCancelBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel")
            dismiss()
        }
        binding.immunizationFormSaveBtn.setOnClickListener { trySubmit() }

        // Discoverable alternative to the long-press on the list card.
        binding.immunizationFormDeleteBtn.visibility =
            if (mode == ImmunizationFormMode.EDIT) View.VISIBLE else View.GONE
        binding.immunizationFormDeleteBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete requested from form · id=${arguments?.getString(ARG_ID)}")
            setFragmentResult(
                RESULT_KEY,
                bundleOf(
                    ARG_MODE to mode.name,
                    ARG_ID to arguments?.getString(ARG_ID),
                    ARG_DELETE to true,
                ),
            )
            dismiss()
        }
    }

    // ─── Vaccine ─────────────────────────────────────────────────────

    private fun renderVaccineLabel() {
        val code = pickedCode
        binding.immunizationFormVaccineCode.visibility = View.GONE
        binding.immunizationFormVaccineCode.text = code.orEmpty()

        if (code.isNullOrBlank()) {
            binding.immunizationFormVaccineDisplay.setText(R.string.immunization_form_vaccine_hint)
            binding.immunizationFormVaccineClear.visibility = View.GONE
            binding.immunizationFormTextLayout.visibility = View.VISIBLE
            return
        }
        binding.immunizationFormVaccineClear.visibility = View.VISIBLE
        // A coded vaccine makes the free-text name redundant.
        binding.immunizationFormTextLayout.visibility = View.GONE

        val curated = IpsVaccineCatalog.getDisplay(code, lang)
        val cached = pickedDisplay
        when {
            curated != null -> binding.immunizationFormVaccineDisplay.text = curated
            !cached.isNullOrBlank() -> binding.immunizationFormVaccineDisplay.text =
                IpsTranslationsRepository.cleanBilingual(cached, lang)
            else -> {
                binding.immunizationFormVaccineDisplay.text = code
                viewLifecycleOwner.lifecycleScope.launch {
                    val localized = try { translations.get(code, lang) } catch (e: Throwable) { null }
                    if (!localized.isNullOrBlank() && _binding != null && pickedCode == code) {
                        pickedDisplay = localized
                        binding.immunizationFormVaccineDisplay.text = localized
                    }
                }
            }
        }
    }

    private fun clearVaccine() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear vaccine")
        pickedCode = null
        pickedCodeSystem = null
        pickedDisplay = null
        renderVaccineLabel()
    }

    private fun openVaccinePicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open vaccine picker · lang=$lang")
        viewLifecycleOwner.lifecycleScope.launch {
            val curated = IpsVaccineCatalog.ALL
            val curatedCodes = curated.map { it.code }.toSet()
            val items = mutableListOf<IpsPickerItem>()
            val prefixes = mutableMapOf<String, String>()
            curated.forEach { v ->
                items.add(IpsPickerItem(code = v.code, display = v.pick(lang), searchKey = v.searchAliases()))
                prefixes[v.code] = v.emoji
            }
            // Second tier : every SNOMED "vaccine product" of the bundled asset
            // translations (long FSN-style labels), minus allergen immunotherapy.
            val assetVaccines = try {
                translations.all(lang).filter { (code, t) ->
                    code !in curatedCodes &&
                        t.en.contains("vaccin", ignoreCase = true) &&
                        !t.en.contains("allerg", ignoreCase = true)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ asset translations unavailable : ${e.message}")
                emptyList()
            }
            assetVaccines.sortedBy { it.second.pick(lang) }.forEach { (code, t) ->
                items.add(IpsPickerItem(code = code, display = t.pick(lang), searchKey = "$code ${t.en} ${t.fr} ${t.ja}"))
            }
            if (_binding == null) return@launch
            IpsCodePickerDialog
                .newInstance(
                    title = getString(R.string.immunization_form_vaccine_picker_title),
                    items = items,
                    codeToPrefix = prefixes,
                )
                .setOnPicked { picked ->
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ vaccine picked · code=${picked.code} · display='${picked.display}'")
                    pickedCode = picked.code
                    pickedCodeSystem = IpsVaccineCatalog.CODE_SYSTEM
                    // Store the English label as the canonical display when we have it.
                    pickedDisplay = IpsVaccineCatalog.byCode(picked.code)?.displayEn ?: picked.display
                    renderVaccineLabel()
                }
                .show(childFragmentManager, "vaccine_picker")
        }
    }

    // ─── Date ────────────────────────────────────────────────────────

    private fun renderDateLabel() {
        val iso = pickedDateIso
        if (iso.isNullOrBlank()) {
            binding.immunizationFormDateLabel.setText(R.string.immunization_form_date_hint)
            binding.immunizationFormDateClear.visibility = View.GONE
        } else {
            binding.immunizationFormDateLabel.text = iso
            binding.immunizationFormDateClear.visibility = View.VISIBLE
        }
    }

    private fun openDatePicker() {
        val constraints = CalendarConstraints.Builder()
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        val initial = pickedDateIso?.let { parseIsoDateUtc(it) } ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.immunization_form_date_pick_title)
            .setSelection(initial)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            pickedDateIso = formatUtcMillisAsIso(utcMillis)
            renderDateLabel()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 date picked: $pickedDateIso")
        }
        picker.show(childFragmentManager, "immunization_date_picker")
    }

    private fun clearDate() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear date (unknown)")
        pickedDateIso = null
        renderDateLabel()
    }

    private fun parseIsoDateUtc(iso: String): Long? {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
            fmt.parse(iso)?.time
        } catch (_: Exception) { null }
    }

    private fun formatUtcMillisAsIso(utcMillis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return fmt.format(Date(utcMillis))
    }

    // ─── Status ──────────────────────────────────────────────────────

    private fun renderStatusLabel() {
        val entry = IpsImmunizationStatusCatalog.byCode(pickedStatus)
        binding.immunizationFormStatusLabel.text =
            if (entry == null) pickedStatus else "${entry.emoji} ${entry.pick(lang)}"
    }

    private fun openStatusPicker() {
        val catalog = IpsImmunizationStatusCatalog.ALL
        val labels = catalog.map { "${it.emoji}  ${it.pick(lang)}" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.immunization_form_status_pick_title)
            .setItems(labels) { _, which ->
                pickedStatus = catalog[which].code
                renderStatusLabel()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ status picked: $pickedStatus")
            }
            .show()
    }

    // ─── Submit ──────────────────────────────────────────────────────

    /** Inline error on the field (persistent, red) + toast (visible even if the field is scrolled away). */
    private fun reject(layout: com.google.android.material.textfield.TextInputLayout, field: View, @androidx.annotation.StringRes msgRes: Int, why: String) {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: $why")
        layout.error = getString(msgRes)
        Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
        field.requestFocus()
        binding.immunizationFormSaveBtn.isEnabled = true
    }

    private fun trySubmit() {
        // Debounce: a second tap before dismiss() must not emit a second result.
        if (!binding.immunizationFormSaveBtn.isEnabled) return
        binding.immunizationFormSaveBtn.isEnabled = false

        val code = pickedCode?.takeIf { it.isNotBlank() }
        val freeText = binding.immunizationFormText.text?.toString()?.trim().orEmpty()
        val doseNumberStr = binding.immunizationFormDoseNumber.text?.toString()?.trim().orEmpty()
        val seriesDosesStr = binding.immunizationFormSeriesDoses.text?.toString()?.trim().orEmpty()

        if (code == null && freeText.isBlank()) {
            reject(binding.immunizationFormTextLayout, binding.immunizationFormText,
                R.string.immunization_form_validation_vaccine, "no vaccine picked nor typed")
            return
        }

        val doseNumber = doseNumberStr.toIntOrNull()
        if (doseNumberStr.isNotBlank() && (doseNumber == null || doseNumber <= 0)) {
            reject(binding.immunizationFormDoseNumberLayout, binding.immunizationFormDoseNumber,
                R.string.immunization_form_validation_dose, "dose number invalid '$doseNumberStr'")
            return
        }
        val seriesDoses = seriesDosesStr.toIntOrNull()
        if (seriesDosesStr.isNotBlank() && (seriesDoses == null || seriesDoses <= 0)) {
            reject(binding.immunizationFormSeriesDosesLayout, binding.immunizationFormSeriesDoses,
                R.string.immunization_form_validation_dose, "series doses invalid '$seriesDosesStr'")
            return
        }
        if (doseNumber != null && seriesDoses != null && doseNumber > seriesDoses) {
            reject(binding.immunizationFormSeriesDosesLayout, binding.immunizationFormSeriesDoses,
                R.string.immunization_form_validation_series, "dose $doseNumber > series $seriesDoses")
            return
        }

        val date = pickedDateIso?.takeIf { it.isNotBlank() && ISO_DATE_REGEX.matches(it) }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 submit · mode=$mode · code=$code · text='$freeText' · " +
            "date=$date · status=$pickedStatus · dose=$doseNumber/$seriesDoses")

        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                ARG_MODE to mode.name,
                ARG_ID to arguments?.getString(ARG_ID),
                ARG_CODE to code,
                ARG_CODE_SYSTEM to (if (code != null) (pickedCodeSystem ?: IpsVaccineCatalog.CODE_SYSTEM) else null),
                ARG_DISPLAY to (if (code != null) pickedDisplay else null),
                ARG_TEXT to (if (code == null) freeText.ifBlank { null } else null),
                ARG_DATE to date,
                ARG_STATUS to pickedStatus,
                ARG_DOSE_NUMBER to (doseNumber ?: 0),
                ARG_SERIES_DOSES to (seriesDoses ?: 0),
                ARG_LOT to binding.immunizationFormLot.text?.toString()?.trim()?.ifBlank { null },
                ARG_MANUFACTURER to binding.immunizationFormManufacturer.text?.toString()?.trim()?.ifBlank { null },
                ARG_PERFORMER to binding.immunizationFormPerformer.text?.toString()?.trim()?.ifBlank { null },
                ARG_NOTE to binding.immunizationFormNote.text?.toString()?.trim()?.ifBlank { null },
            ),
        )
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
