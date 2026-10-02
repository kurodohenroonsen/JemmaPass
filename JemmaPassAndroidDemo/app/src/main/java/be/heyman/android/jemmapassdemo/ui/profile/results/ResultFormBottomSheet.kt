/*
 * ResultFormBottomSheet.kt — JEMMA Pass · IPS pillar "Results" (FHIR-native, sprint 3)
 *
 * CREATE / EDIT one FHIR R4 `Observation` (Observation-results-*-uv-ips) edited as an IpsResult :
 *   - Test (code)        : card → IpsCodePickerDialog over the embedded LOINC catalog
 *                          (EN/FR/JA aliases; the on-device KB has no LOINC), free-text fallback
 *   - Category           : laboratory | imaging | other diagnostic procedure
 *   - Value              : decimal (+ UCUM unit picker) or free text ; coded tests (ABO/Rh)
 *                          use a dedicated picker (SNOMED free-set codes)
 *   - Interpretation     : N / H / L / HH / LL / A / POS / NEG (optional)
 *   - Reference range    : low / high (same unit)
 *   - Date (effective), status, performer, note
 *
 * UC-BLOOD-10.. — a blood-group result (LOINC 882-1) that contradicts the profile's blood
 * group (`p.bt`, edited in identity) is refused before saving : a profile holds one blood
 * group only. The sheet reads `p.bt` itself through ProfilesRepository.
 *
 * Validation errors are inline (TextInputLayout.error) + toast + Log.w ; Save is
 * debounced ; Delete in EDIT mode.
 *
 * Logging : tag JEMMA-RESULTS-FORM
 */
package be.heyman.android.jemmapassdemo.ui.profile.results

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
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.BottomSheetResultFormBinding
import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import be.heyman.android.jemmapassdemo.ips.IpsCodeSystems
import be.heyman.android.jemmapassdemo.ips.IpsDecimal
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.ips.IpsResultCategory
import be.heyman.android.jemmapassdemo.ips.IpsResultInterpretation
import be.heyman.android.jemmapassdemo.ips.IpsResultStatus
import be.heyman.android.jemmapassdemo.pillars.IpsResultCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsResultCategoryCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsResultInterpretationCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsResultStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.ResultValueKind
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.ui.common.IpsCodePickerDialog
import be.heyman.android.jemmapassdemo.ui.common.IpsPickerItem
import be.heyman.android.jemmapassdemo.ui.common.normalizeForPickerSearch
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.launch

enum class ResultFormMode { CREATE, EDIT }

/**
 * UC-BLOOD-10.. — may this result be saved next to the profile's blood group ? Pure Kotlin.
 *
 * Stricter than [IpsBloodGroup.contradictsProfile] on purpose : a bare 882-1 value (no date,
 * no note) with another group "looks derived" to the repository, which would replace it by
 * the profile value without a word. In the form the user just typed it, so it is refused too.
 */
internal object ResultBloodGroupGuard {

    data class Conflict(
        /** Canonical profile blood group ("O+") — the reference. */
        val profileBloodGroup: String,
        /** Canonical group of the result being saved ("A+"), null when it cannot be read. */
        val entered: String?,
    )

    /**
     * Null = no objection : not a blood-group result, the profile has no (recognised) blood
     * group, or both state the same group. A 882-1 result whose value cannot be read as an
     * ABO/Rh group is a conflict as well : it cannot be shown to agree with the profile.
     */
    fun check(
        code: String?,
        valueCode: String?,
        valueDisplay: String?,
        valueText: String?,
        profileBloodType: String?,
    ): Conflict? {
        if (code?.trim() != IpsBloodGroup.LOINC_ABO_RH) return null
        val expected = IpsBloodGroup.normalize(profileBloodType) ?: return null
        val entered = IpsBloodGroup.labelOf(
            IpsResult(
                id = "blood-group-check",
                code = IpsBloodGroup.LOINC_ABO_RH,
                valueCode = valueCode,
                valueDisplay = valueDisplay,
                valueText = valueText,
            ),
        )
        return if (entered == expected) null else Conflict(expected, entered)
    }
}

@AndroidEntryPoint
class ResultFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "JEMMA-RESULTS-FORM"

        const val RESULT_KEY = "result_form_result"
        const val ARG_MODE = "mode"
        const val ARG_ID = "id"
        const val ARG_LANG = "lang"
        const val ARG_CODE = "code"
        const val ARG_CODE_SYSTEM = "code_system"
        const val ARG_DISPLAY = "display"
        const val ARG_TEXT = "text"
        const val ARG_DATE = "date"
        const val ARG_STATUS = "status"
        const val ARG_CATEGORY = "category"
        const val ARG_VALUE = "value"
        const val ARG_UNIT = "unit"
        const val ARG_VALUE_CODE = "value_code"
        const val ARG_VALUE_DISPLAY = "value_display"
        const val ARG_VALUE_TEXT = "value_text"
        const val ARG_INTERPRETATION = "interpretation"
        const val ARG_REF_LOW = "ref_low"
        const val ARG_REF_HIGH = "ref_high"
        const val ARG_PERFORMER = "performer"
        const val ARG_NOTE = "note"
        const val ARG_DELETE = "delete"
        /** Profile being edited (optional : resolved from the Results screen when absent). */
        const val ARG_PROFILE_ID = "profile_id"
        private const val NAV_ARG_PROFILE_ID = "profileId"

        private val ISO_DATE_REGEX = Regex("^\\d{4}(-\\d{2}(-\\d{2})?)?$")

        fun newInstance(
            mode: ResultFormMode,
            lang: String,
            existing: IpsResult? = null,
            profileId: String? = null,
        ): ResultFormBottomSheet =
            ResultFormBottomSheet().apply {
                arguments = bundleOf(
                    ARG_MODE to mode.name,
                    ARG_PROFILE_ID to profileId,
                    ARG_ID to existing?.id,
                    ARG_LANG to lang,
                    ARG_CODE to existing?.code,
                    ARG_CODE_SYSTEM to existing?.system,
                    ARG_DISPLAY to existing?.display,
                    ARG_TEXT to existing?.text,
                    ARG_DATE to existing?.date,
                    ARG_STATUS to existing?.status,
                    ARG_CATEGORY to existing?.category,
                    // A non-numeric "value" from a legacy projection is shown as text.
                    ARG_VALUE to (if (existing?.isNumeric == true) existing.value else existing?.valueText ?: existing?.value),
                    ARG_UNIT to existing?.unit,
                    ARG_VALUE_CODE to existing?.valueCode,
                    ARG_VALUE_DISPLAY to existing?.valueDisplay,
                    ARG_INTERPRETATION to existing?.interpretation,
                    ARG_REF_LOW to existing?.refLow,
                    ARG_REF_HIGH to existing?.refHigh,
                    ARG_PERFORMER to existing?.performer,
                    ARG_NOTE to existing?.note,
                )
            }
    }

    @Inject lateinit var profilesRepo: ProfilesRepository

    private var _binding: BottomSheetResultFormBinding? = null
    private val binding get() = _binding!!

    private val mode: ResultFormMode by lazy { ResultFormMode.valueOf(arguments?.getString(ARG_MODE) ?: ResultFormMode.CREATE.name) }
    private val lang: String by lazy { arguments?.getString(ARG_LANG) ?: "en" }

    private var pickedCode: String? = null
    private var pickedCodeSystem: String? = null
    private var pickedDisplay: String? = null
    private var pickedDateIso: String? = null
    private var pickedStatus: String = IpsResultStatus.FINAL
    private var pickedCategory: String = IpsResultCategory.LABORATORY
    private var pickedUnit: String? = null
    private var pickedInterpretation: String? = null
    private var pickedValueCode: String? = null
    private var pickedValueDisplay: String? = null

    private val isCodedTest: Boolean
        get() = IpsResultCatalog.byCode(pickedCode)?.kind == ResultValueKind.CODED

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetResultFormBinding.inflate(inflater, container, false)
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
        binding.resultFormTitle.setText(if (mode == ResultFormMode.CREATE) R.string.result_form_title_create else R.string.result_form_title_edit)

        val args = arguments
        pickedCode = args?.getString(ARG_CODE)?.takeIf { it.isNotBlank() }
        pickedCodeSystem = args?.getString(ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() }
        pickedDisplay = args?.getString(ARG_DISPLAY)?.takeIf { it.isNotBlank() }
        pickedDateIso = args?.getString(ARG_DATE)?.takeIf { it.isNotBlank() }
        pickedStatus = IpsResultStatus.normalize(args?.getString(ARG_STATUS))
        pickedCategory = IpsResultCategory.normalize(args?.getString(ARG_CATEGORY))
        pickedUnit = args?.getString(ARG_UNIT)?.takeIf { it.isNotBlank() }
        pickedInterpretation = IpsResultInterpretation.normalize(args?.getString(ARG_INTERPRETATION))
        pickedValueCode = args?.getString(ARG_VALUE_CODE)?.takeIf { it.isNotBlank() }
        pickedValueDisplay = args?.getString(ARG_VALUE_DISPLAY)?.takeIf { it.isNotBlank() }

        binding.resultFormText.setText(args?.getString(ARG_TEXT).orEmpty())
        binding.resultFormValue.setText(args?.getString(ARG_VALUE).orEmpty())
        binding.resultFormRefLow.setText(args?.getString(ARG_REF_LOW).orEmpty())
        binding.resultFormRefHigh.setText(args?.getString(ARG_REF_HIGH).orEmpty())
        binding.resultFormPerformer.setText(args?.getString(ARG_PERFORMER).orEmpty())
        binding.resultFormNote.setText(args?.getString(ARG_NOTE).orEmpty())

        renderCode(); renderCategory(); renderDate(); renderStatus(); renderUnit(); renderInterpretation(); renderCodedValue()

        binding.resultFormCodeCard.setOnClickListener { openTestPicker() }
        binding.resultFormCodeClear.setOnClickListener { clearCode() }
        binding.resultFormCategoryRow.setOnClickListener { openCategoryPicker() }
        binding.resultFormDateRow.setOnClickListener { openDatePicker() }
        binding.resultFormDateClear.setOnClickListener { pickedDateIso = null; renderDate() }
        binding.resultFormStatusRow.setOnClickListener { openStatusPicker() }
        binding.resultFormUnitRow.setOnClickListener { openUnitPicker() }
        binding.resultFormInterpretationRow.setOnClickListener { openInterpretationPicker() }
        binding.resultFormCodedRow.setOnClickListener { openBloodGroupPicker() }

        binding.resultFormText.doAfterTextChanged { binding.resultFormTextLayout.error = null }
        binding.resultFormValue.doAfterTextChanged { binding.resultFormValueLayout.error = null }
        binding.resultFormRefLow.doAfterTextChanged { binding.resultFormRefLowLayout.error = null; binding.resultFormRefHighLayout.error = null }
        binding.resultFormRefHigh.doAfterTextChanged { binding.resultFormRefLowLayout.error = null; binding.resultFormRefHighLayout.error = null }

        binding.resultFormCancelBtn.setOnClickListener { Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel"); dismiss() }
        binding.resultFormSaveBtn.setOnClickListener { trySubmit() }
        binding.resultFormDeleteBtn.visibility = if (mode == ResultFormMode.EDIT) View.VISIBLE else View.GONE
        binding.resultFormDeleteBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete requested · id=${arguments?.getString(ARG_ID)}")
            setFragmentResult(RESULT_KEY, bundleOf(ARG_MODE to mode.name, ARG_ID to arguments?.getString(ARG_ID), ARG_DELETE to true))
            dismiss()
        }
    }

    // ─── Test (code) ─────────────────────────────────────────────────

    private fun renderCode() {
        val code = pickedCode
        if (code.isNullOrBlank()) {
            binding.resultFormCodeDisplay.setText(R.string.result_form_code_hint)
            binding.resultFormCodeClear.visibility = View.GONE
            binding.resultFormTextLayout.visibility = View.VISIBLE
        } else {
            binding.resultFormCodeClear.visibility = View.VISIBLE
            binding.resultFormTextLayout.visibility = View.GONE
            binding.resultFormCodeDisplay.text = IpsResultCatalog.getDisplay(code, lang) ?: pickedDisplay ?: code
        }
        // Coded tests (ABO/Rh) swap the free value + unit for a dedicated picker.
        val coded = isCodedTest
        binding.resultFormValueLayout.visibility = if (coded) View.GONE else View.VISIBLE
        binding.resultFormUnitRow.visibility = if (coded) View.GONE else View.VISIBLE
        binding.resultFormUnitLabelHeader.visibility = if (coded) View.GONE else View.VISIBLE
        binding.resultFormRefRow.visibility = if (coded) View.GONE else View.VISIBLE
        binding.resultFormCodedRow.visibility = if (coded) View.VISIBLE else View.GONE
        binding.resultFormCodedHeader.visibility = if (coded) View.VISIBLE else View.GONE
    }

    private fun clearCode() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear test code")
        pickedCode = null; pickedCodeSystem = null; pickedDisplay = null
        pickedValueCode = null; pickedValueDisplay = null
        renderCode(); renderCodedValue()
    }

    private fun openTestPicker() {
        val items = IpsResultCatalog.ALL.map {
            IpsPickerItem(code = it.code, display = it.pick(lang), searchKey = normalizeForPickerSearch(it.searchAliases()))
        }
        val prefixes = IpsResultCatalog.ALL.associate { it.code to it.emoji }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open test picker · ${items.size} LOINC entries")
        IpsCodePickerDialog.newInstance(getString(R.string.result_form_code_picker_title), items, prefixes)
            .setOnPicked { picked ->
                val entry = IpsResultCatalog.byCode(picked.code)
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ test picked · code=${picked.code} · unit=${entry?.unit}")
                pickedCode = picked.code
                pickedCodeSystem = IpsCodeSystems.LOINC
                pickedDisplay = entry?.displayEn ?: picked.display
                entry?.let {
                    pickedCategory = it.category
                    if (it.unit != null) pickedUnit = it.unit
                }
                renderCode(); renderCategory(); renderUnit(); renderCodedValue()
            }
            .show(childFragmentManager, "result_test_picker")
    }

    // ─── Category / status / interpretation / unit pickers ──────────

    private fun renderCategory() {
        val e = IpsResultCategoryCatalog.byCode(pickedCategory)
        binding.resultFormCategoryLabel.text = if (e == null) pickedCategory else "${e.emoji} ${e.pick(lang)}"
    }

    private fun openCategoryPicker() {
        val all = IpsResultCategoryCatalog.ALL
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.result_form_category_pick_title)
            .setItems(all.map { "${it.emoji}  ${it.pick(lang)}" }.toTypedArray()) { _, which ->
                pickedCategory = all[which].code; renderCategory()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ category picked: $pickedCategory")
            }
            .show()
    }

    private fun renderStatus() {
        val e = IpsResultStatusCatalog.byCode(pickedStatus)
        binding.resultFormStatusLabel.text = if (e == null) pickedStatus else "${e.emoji} ${e.pick(lang)}"
    }

    private fun openStatusPicker() {
        val all = IpsResultStatusCatalog.ALL
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.result_form_status_pick_title)
            .setItems(all.map { "${it.emoji}  ${it.pick(lang)}" }.toTypedArray()) { _, which ->
                pickedStatus = all[which].code; renderStatus()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ status picked: $pickedStatus")
            }
            .show()
    }

    private fun renderInterpretation() {
        val e = IpsResultInterpretationCatalog.byCode(pickedInterpretation)
        binding.resultFormInterpretationLabel.text =
            if (e == null) getString(R.string.result_form_interpretation_none) else "${e.emoji} ${e.pick(lang)}"
    }

    private fun openInterpretationPicker() {
        val all = IpsResultInterpretationCatalog.ALL
        val labels = listOf(getString(R.string.result_form_interpretation_none)) + all.map { "${it.emoji}  ${it.pick(lang)}" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.result_form_interpretation_pick_title)
            .setItems(labels.toTypedArray()) { _, which ->
                pickedInterpretation = if (which == 0) null else all[which - 1].code
                renderInterpretation()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ interpretation picked: $pickedInterpretation")
            }
            .show()
    }

    private fun renderUnit() {
        binding.resultFormUnitLabel.text = pickedUnit ?: getString(R.string.result_form_unit_none)
    }

    private fun openUnitPicker() {
        val units = (listOfNotNull(pickedUnit) + IpsResultCatalog.UNITS).distinct()
        val labels = listOf(getString(R.string.result_form_unit_none)) + units
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.result_form_unit_pick_title)
            .setItems(labels.toTypedArray()) { _, which ->
                pickedUnit = if (which == 0) null else units[which - 1]
                renderUnit()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ unit picked: $pickedUnit")
            }
            .show()
    }

    private fun renderCodedValue() {
        binding.resultFormCodedLabel.text = IpsBloodGroup.labelFromSnomed(pickedValueCode)
            ?: pickedValueDisplay
            ?: getString(R.string.result_form_coded_hint)
    }

    private fun openBloodGroupPicker() {
        val labels = IpsBloodGroup.LABELS
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.result_form_coded_pick_title)
            .setItems(labels.map { "🩸  $it" }.toTypedArray()) { _, which ->
                val l = labels[which]
                pickedValueCode = IpsBloodGroup.snomedCode(l)
                pickedValueDisplay = IpsBloodGroup.snomedDisplay(l)
                renderCodedValue()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ blood group picked: $l → $pickedValueCode")
            }
            .show()
    }

    // ─── Date ────────────────────────────────────────────────────────

    private fun renderDate() {
        val iso = pickedDateIso
        if (iso.isNullOrBlank()) {
            binding.resultFormDateLabel.setText(R.string.result_form_date_hint)
            binding.resultFormDateClear.visibility = View.GONE
        } else {
            binding.resultFormDateLabel.text = iso
            binding.resultFormDateClear.visibility = View.VISIBLE
        }
    }

    private fun openDatePicker() {
        val constraints = CalendarConstraints.Builder().setEnd(MaterialDatePicker.todayInUtcMilliseconds()).build()
        val initial = pickedDateIso?.let { parseIsoDateUtc(it) } ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.result_form_date_pick_title)
            .setSelection(initial)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            pickedDateIso = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(utcMillis))
            renderDate()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 date picked: $pickedDateIso")
        }
        picker.show(childFragmentManager, "result_date_picker")
    }

    private fun parseIsoDateUtc(iso: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time
    } catch (_: Exception) { null }

    // ─── Submit ──────────────────────────────────────────────────────

    private fun trySubmit() {
        if (!binding.resultFormSaveBtn.isEnabled) return
        binding.resultFormSaveBtn.isEnabled = false

        val code = pickedCode?.takeIf { it.isNotBlank() }
        val freeText = binding.resultFormText.text?.toString()?.trim().orEmpty()
        if (code == null && freeText.isBlank()) {
            reject(R.string.result_form_validation_code, binding.resultFormTextLayout, "no test picked nor typed")
            return
        }

        val rawValue = binding.resultFormValue.text?.toString()?.trim().orEmpty()
        val numeric = IpsDecimal.normalize(rawValue)
        if (isCodedTest) {
            if (pickedValueCode == null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: coded test without value")
                Toast.makeText(requireContext(), R.string.result_form_validation_coded, Toast.LENGTH_SHORT).show()
                binding.resultFormSaveBtn.isEnabled = true
                return
            }
        } else if (rawValue.isBlank()) {
            reject(R.string.result_form_validation_value, binding.resultFormValueLayout, "no value")
            return
        }

        val lowRaw = binding.resultFormRefLow.text?.toString()?.trim().orEmpty()
        val highRaw = binding.resultFormRefHigh.text?.toString()?.trim().orEmpty()
        val low = IpsDecimal.normalize(lowRaw)
        val high = IpsDecimal.normalize(highRaw)
        if (!isCodedTest) {
            if (lowRaw.isNotBlank() && low == null) { reject(R.string.result_form_validation_range_number, binding.resultFormRefLowLayout, "ref low not a number"); return }
            if (highRaw.isNotBlank() && high == null) { reject(R.string.result_form_validation_range_number, binding.resultFormRefHighLayout, "ref high not a number"); return }
            if (low != null && high != null && low.toDouble() > high.toDouble()) {
                reject(R.string.result_form_validation_range_order, binding.resultFormRefHighLayout, "ref low > high"); return
            }
        }
        val date = pickedDateIso?.takeIf { it.isNotBlank() && ISO_DATE_REGEX.matches(it) }

        val coded = isCodedTest
        val deliver = {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 submit · mode=$mode · code=$code · value='$rawValue' · numeric=${numeric != null} · unit=$pickedUnit · coded=$pickedValueCode · date=$date")
            setFragmentResult(
                RESULT_KEY,
                bundleOf(
                    ARG_MODE to mode.name,
                    ARG_ID to arguments?.getString(ARG_ID),
                    ARG_CODE to code,
                    ARG_CODE_SYSTEM to (if (code != null) (pickedCodeSystem ?: IpsCodeSystems.LOINC) else null),
                    ARG_DISPLAY to (if (code != null) pickedDisplay else null),
                    ARG_TEXT to (if (code == null) freeText else null),
                    ARG_DATE to date,
                    ARG_STATUS to pickedStatus,
                    ARG_CATEGORY to pickedCategory,
                    ARG_VALUE to (if (!isCodedTest && numeric != null) numeric else null),
                    ARG_UNIT to (if (!isCodedTest && numeric != null) pickedUnit else null),
                    ARG_VALUE_CODE to (if (isCodedTest) pickedValueCode else null),
                    ARG_VALUE_DISPLAY to (if (isCodedTest) pickedValueDisplay else null),
                    ARG_VALUE_TEXT to (if (!isCodedTest && numeric == null) rawValue else null),
                    ARG_INTERPRETATION to pickedInterpretation,
                    ARG_REF_LOW to (if (!isCodedTest) low else null),
                    ARG_REF_HIGH to (if (!isCodedTest) high else null),
                    ARG_PERFORMER to binding.resultFormPerformer.text?.toString()?.trim()?.ifBlank { null },
                    ARG_NOTE to binding.resultFormNote.text?.toString()?.trim()?.ifBlank { null },
                ),
            )
            dismiss()
        }

        // UC-BLOOD-10.. — a blood-group result is only saved when it agrees with the profile.
        if (code?.trim() != IpsBloodGroup.LOINC_ABO_RH) {
            deliver()
            return
        }
        val valueCode = if (coded) pickedValueCode else null
        val valueDisplay = if (coded) pickedValueDisplay else null
        val valueText = if (!coded && numeric == null) rawValue else null
        viewLifecycleOwner.lifecycleScope.launch {
            val pid = resolveProfileId()
            val profile = try {
                pid?.let { profilesRepo.loadProfile(it) }
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ blood group check: profile $pid unreadable : ${e.message}")
                null
            }
            if (_binding == null) return@launch
            if (isStateSaved) {
                // Too late to deliver a result or show a dialog : nothing saved, Save stays usable.
                binding.resultFormSaveBtn.isEnabled = true
                return@launch
            }
            if (profile == null) {
                // No profile to compare with : the repository still keeps a single blood group
                // on write (IpsBloodGroup.reconcile) and reports what it replaced.
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ blood group check skipped: no profile (id=$pid)")
            }
            val conflict = ResultBloodGroupGuard.check(code, valueCode, valueDisplay, valueText, profile?.p?.bt)
            if (conflict == null) {
                deliver()
            } else {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] 🩸⚠ blood group result ${conflict.entered ?: "?"} contradicts the profile (${conflict.profileBloodGroup}) — not saved")
                showBloodGroupConflict(conflict, pid)
            }
        }
    }

    // ─── Blood group vs profile (UC-BLOOD-10..) ──────────────────────

    /** The form's own argument, else the `profileId` of the Results screen, else the active profile. */
    private fun resolveProfileId(): String? {
        arguments?.getString(ARG_PROFILE_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val fromNav = try {
            findNavController().currentBackStackEntry
                ?.takeIf { it.destination.id == R.id.dest_results }
                ?.arguments?.getString(NAV_ARG_PROFILE_ID)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no nav controller to read the profile id : ${e.message}")
            null
        }
        return fromNav ?: profilesRepo.currentProfileId
    }

    /** Blocking explanation : nothing is saved, whichever button is used. */
    private fun showBloodGroupConflict(conflict: ResultBloodGroupGuard.Conflict, profileId: String?) {
        binding.resultFormSaveBtn.isEnabled = true
        val entered = conflict.entered ?: getString(R.string.result_form_blood_conflict_unreadable)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.result_form_blood_conflict_title)
            .setMessage(getString(R.string.result_form_blood_conflict_message, conflict.profileBloodGroup, entered))
            .setCancelable(false)
            .setNegativeButton(R.string.result_form_blood_conflict_cancel) { d, _ ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ blood group conflict · back to the form, nothing saved")
                d.dismiss()
            }
            .setPositiveButton(R.string.result_form_blood_conflict_change) { d, _ ->
                d.dismiss()
                openIdentity(profileId)
            }
            .show()
    }

    /** Leaves the form WITHOUT saving and opens the identity screen of the same profile. */
    private fun openIdentity(profileId: String?) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩸 blood group conflict · go to identity · profileId=$profileId")
        val appContext = requireContext().applicationContext
        // A null profileId means "create a new profile" for the identity screen : never navigate without one.
        val nav = if (profileId.isNullOrBlank()) null else try { findNavController() } catch (_: Exception) { null }
        dismiss()
        val opened = nav != null && try {
            nav.navigate(R.id.dest_perso, bundleOf(NAV_ARG_PROFILE_ID to profileId))
            true
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ cannot open identity : ${e.message}")
            false
        }
        if (!opened) Toast.makeText(appContext, R.string.results_derived_blood_group_hint, Toast.LENGTH_LONG).show()
    }

    private fun reject(messageRes: Int, layout: TextInputLayout, why: String) {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: $why")
        layout.error = getString(messageRes)
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
        layout.editText?.requestFocus()
        binding.resultFormSaveBtn.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
