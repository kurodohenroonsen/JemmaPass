/*
 * DeviceFormBottomSheet.kt — JEMMA Pass · IPS pillar "Medical Devices" (FHIR-native)
 *
 * CREATE / EDIT one device edited as an IpsDevice, persisted as a FHIR R4
 * `Device` (Device-uv-ips) + `DeviceUseStatement` (DeviceUseStatement-uv-ips) :
 *   - Device (type)      : tappable card → KbDrugPickerDialog in "Device" mode
 *                          (curated IpsDeviceCatalog suggestions + live KB FTS5 search),
 *                          free-text fallback when nothing is picked (Device.type.text)
 *   - UDI                : Device.udiCarrier.deviceIdentifier / carrierHRF (optional)
 *   - Manufacturer, model, serial : Device.manufacturer / modelNumber / serialNumber
 *   - Date (since)       : MaterialDatePicker → DeviceUseStatement.timingDateTime
 *   - Status             : active | inactive | entered-in-error
 *   - Body site, note    : free text
 *
 * Validation errors are inline (TextInputLayout.error) + toast + Log.w ; Save is
 * debounced ; a Delete button is offered in EDIT mode.
 *
 * Logging : tag JEMMA-DEVICES-FORM
 */
package be.heyman.android.jemmapassdemo.ui.profile.devices

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
import be.heyman.android.jemmapassdemo.databinding.BottomSheetDeviceFormBinding
import be.heyman.android.jemmapassdemo.ips.IpsDevice
import be.heyman.android.jemmapassdemo.ips.IpsDeviceStatus
import be.heyman.android.jemmapassdemo.pillars.IpsDeviceCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsDeviceStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.pillars.IpsUdi
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

enum class DeviceFormMode { CREATE, EDIT }

@AndroidEntryPoint
class DeviceFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "JEMMA-DEVICES-FORM"

        const val RESULT_KEY = "device_form_result"
        const val ARG_MODE = "mode"
        const val ARG_ID = "id"
        const val ARG_LANG = "lang"
        const val ARG_CODE = "code"
        const val ARG_CODE_SYSTEM = "code_system"
        const val ARG_DISPLAY = "display"
        const val ARG_TEXT = "text"
        const val ARG_UDI = "udi"
        const val ARG_MANUFACTURER = "manufacturer"
        const val ARG_MODEL = "model"
        const val ARG_SERIAL = "serial"
        const val ARG_DATE = "date"
        const val ARG_STATUS = "status"
        const val ARG_BODY_SITE = "body_site"
        const val ARG_NOTE = "note"
        const val ARG_DELETE = "delete"

        private val ISO_DATE_REGEX = Regex("^\\d{4}(-\\d{2}(-\\d{2})?)?$")

        fun newInstance(mode: DeviceFormMode, lang: String, existing: IpsDevice? = null): DeviceFormBottomSheet =
            DeviceFormBottomSheet().apply {
                arguments = bundleOf(
                    ARG_MODE to mode.name,
                    ARG_ID to existing?.id,
                    ARG_LANG to lang,
                    ARG_CODE to existing?.code,
                    ARG_CODE_SYSTEM to existing?.system,
                    ARG_DISPLAY to existing?.display,
                    ARG_TEXT to existing?.text,
                    ARG_UDI to existing?.udi,
                    ARG_MANUFACTURER to existing?.manufacturer,
                    ARG_MODEL to existing?.model,
                    ARG_SERIAL to existing?.serial,
                    ARG_DATE to existing?.date,
                    ARG_STATUS to existing?.status,
                    ARG_BODY_SITE to existing?.bodySite,
                    ARG_NOTE to existing?.note,
                )
            }
    }

    private var _binding: BottomSheetDeviceFormBinding? = null
    private val binding get() = _binding!!

    private val mode: DeviceFormMode by lazy { DeviceFormMode.valueOf(arguments?.getString(ARG_MODE) ?: DeviceFormMode.CREATE.name) }
    private val lang: String by lazy { arguments?.getString(ARG_LANG) ?: "en" }

    private var pickedCode: String? = null
    private var pickedCodeSystem: String? = null
    private var pickedDisplay: String? = null
    private var pickedDateIso: String? = null
    private var pickedStatus: String = IpsDeviceStatusCatalog.DEFAULT_CODE

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetDeviceFormBinding.inflate(inflater, container, false)
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
        binding.deviceFormTitle.setText(if (mode == DeviceFormMode.CREATE) R.string.device_form_title_create else R.string.device_form_title_edit)

        pickedCode = arguments?.getString(ARG_CODE)?.takeIf { it.isNotBlank() }
        pickedCodeSystem = arguments?.getString(ARG_CODE_SYSTEM)?.takeIf { it.isNotBlank() }
        pickedDisplay = arguments?.getString(ARG_DISPLAY)?.takeIf { it.isNotBlank() }
        pickedDateIso = arguments?.getString(ARG_DATE)?.takeIf { it.isNotBlank() }
        pickedStatus = IpsDeviceStatus.normalize(arguments?.getString(ARG_STATUS))

        binding.deviceFormText.setText(arguments?.getString(ARG_TEXT).orEmpty())
        binding.deviceFormUdi.setText(arguments?.getString(ARG_UDI).orEmpty())
        binding.deviceFormManufacturer.setText(arguments?.getString(ARG_MANUFACTURER).orEmpty())
        binding.deviceFormModel.setText(arguments?.getString(ARG_MODEL).orEmpty())
        binding.deviceFormSerial.setText(arguments?.getString(ARG_SERIAL).orEmpty())
        binding.deviceFormBodySite.setText(arguments?.getString(ARG_BODY_SITE).orEmpty())
        binding.deviceFormNote.setText(arguments?.getString(ARG_NOTE).orEmpty())

        renderCodeLabel(); renderDateLabel(); renderStatusLabel()

        binding.deviceFormCodeCard.setOnClickListener { openPicker() }
        binding.deviceFormCodeClear.setOnClickListener { clearCode() }
        binding.deviceFormDateRow.setOnClickListener { openDatePicker() }
        binding.deviceFormDateClear.setOnClickListener { pickedDateIso = null; renderDateLabel() }
        binding.deviceFormStatusRow.setOnClickListener { openStatusPicker() }
        binding.deviceFormText.doAfterTextChanged { binding.deviceFormTextLayout.error = null }
        binding.deviceFormUdi.doAfterTextChanged { binding.deviceFormUdiLayout.error = null }

        binding.deviceFormCancelBtn.setOnClickListener { Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel"); dismiss() }
        binding.deviceFormSaveBtn.setOnClickListener { trySubmit() }
        binding.deviceFormDeleteBtn.visibility = if (mode == DeviceFormMode.EDIT) View.VISIBLE else View.GONE
        binding.deviceFormDeleteBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 delete requested · id=${arguments?.getString(ARG_ID)}")
            setFragmentResult(RESULT_KEY, bundleOf(ARG_MODE to mode.name, ARG_ID to arguments?.getString(ARG_ID), ARG_DELETE to true))
            dismiss()
        }
    }

    // ─── Device type ─────────────────────────────────────────────────

    private fun renderCodeLabel() {
        val code = pickedCode
        if (code.isNullOrBlank()) {
            binding.deviceFormCodeDisplay.setText(R.string.device_form_code_hint)
            binding.deviceFormCodeClear.visibility = View.GONE
            binding.deviceFormTextLayout.visibility = View.VISIBLE
            return
        }
        binding.deviceFormCodeClear.visibility = View.VISIBLE
        binding.deviceFormTextLayout.visibility = View.GONE
        val label = IpsDeviceCatalog.getDisplay(code, lang)
            ?: pickedDisplay?.let { IpsTranslationsRepository.cleanBilingual(it, lang) }
            ?: code
        binding.deviceFormCodeDisplay.text = label
    }

    private fun clearCode() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear device code")
        pickedCode = null; pickedCodeSystem = null; pickedDisplay = null
        renderCodeLabel()
    }

    private fun openPicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open device picker · lang=$lang")
        KbDrugPickerDialog
            .newInstance(
                title = getString(R.string.device_form_code_picker_title),
                lang = lang,
                category = KbDrugPickerDialog.CATEGORY_DEVICE,
                suggestions = IpsDeviceCatalog.ALL.map { it.code to "${it.emoji}  ${it.pick(lang)}" },
                suggestionsSystem = IpsDeviceCatalog.CODE_SYSTEM,
            )
            .setOnPicked { picked ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ device picked · code=${picked.code} · system=${picked.system} · display='${picked.display}'")
                pickedCode = picked.code
                pickedCodeSystem = picked.system
                pickedDisplay = IpsDeviceCatalog.byCode(picked.code)?.displayEn ?: picked.display.substringAfter("  ").trim()
                renderCodeLabel()
            }
            .show(childFragmentManager, "device_picker")
    }

    // ─── Date ────────────────────────────────────────────────────────

    private fun renderDateLabel() {
        val iso = pickedDateIso
        if (iso.isNullOrBlank()) {
            binding.deviceFormDateLabel.setText(R.string.device_form_date_hint)
            binding.deviceFormDateClear.visibility = View.GONE
        } else {
            binding.deviceFormDateLabel.text = iso
            binding.deviceFormDateClear.visibility = View.VISIBLE
        }
    }

    private fun openDatePicker() {
        val constraints = CalendarConstraints.Builder().setEnd(MaterialDatePicker.todayInUtcMilliseconds()).build()
        val initial = pickedDateIso?.let { parseIsoDateUtc(it) } ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.device_form_date_pick_title)
            .setSelection(initial)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            pickedDateIso = formatUtcMillisAsIso(utcMillis)
            renderDateLabel()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 date picked: $pickedDateIso")
        }
        picker.show(childFragmentManager, "device_date_picker")
    }

    private fun parseIsoDateUtc(iso: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time
    } catch (_: Exception) { null }

    private fun formatUtcMillisAsIso(utcMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(utcMillis))

    // ─── Status ──────────────────────────────────────────────────────

    private fun renderStatusLabel() {
        val entry = IpsDeviceStatusCatalog.byCode(pickedStatus)
        binding.deviceFormStatusLabel.text = if (entry == null) pickedStatus else "${entry.emoji} ${entry.pick(lang)}"
    }

    private fun openStatusPicker() {
        val catalog = IpsDeviceStatusCatalog.ALL
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_form_status_pick_title)
            .setItems(catalog.map { "${it.emoji}  ${it.pick(lang)}" }.toTypedArray()) { _, which ->
                pickedStatus = catalog[which].code
                renderStatusLabel()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ status picked: $pickedStatus")
            }
            .show()
    }

    // ─── Submit ──────────────────────────────────────────────────────

    private fun trySubmit() {
        if (!binding.deviceFormSaveBtn.isEnabled) return
        binding.deviceFormSaveBtn.isEnabled = false

        val code = pickedCode?.takeIf { it.isNotBlank() }
        val freeText = binding.deviceFormText.text?.toString()?.trim().orEmpty()
        if (code == null && freeText.isBlank()) {
            reject(R.string.device_form_validation_code, binding.deviceFormTextLayout, "no device picked nor typed")
            binding.deviceFormText.requestFocus()
            return
        }
        val udi = IpsUdi.normalize(binding.deviceFormUdi.text?.toString())
        if (udi != null && !IpsUdi.isPlausible(udi)) {
            reject(R.string.device_form_validation_udi, binding.deviceFormUdiLayout, "implausible UDI '$udi'")
            binding.deviceFormUdi.requestFocus()
            return
        }
        val date = pickedDateIso?.takeIf { it.isNotBlank() && ISO_DATE_REGEX.matches(it) }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 submit · mode=$mode · code=$code · text='$freeText' · udi=${udi != null} · date=$date · status=$pickedStatus")
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                ARG_MODE to mode.name,
                ARG_ID to arguments?.getString(ARG_ID),
                ARG_CODE to code,
                ARG_CODE_SYSTEM to (if (code != null) (pickedCodeSystem ?: IpsDeviceCatalog.CODE_SYSTEM) else null),
                ARG_DISPLAY to (if (code != null) pickedDisplay else null),
                ARG_TEXT to (if (code == null) freeText.ifBlank { null } else null),
                ARG_UDI to udi,
                ARG_MANUFACTURER to binding.deviceFormManufacturer.text?.toString()?.trim()?.ifBlank { null },
                ARG_MODEL to binding.deviceFormModel.text?.toString()?.trim()?.ifBlank { null },
                ARG_SERIAL to binding.deviceFormSerial.text?.toString()?.trim()?.ifBlank { null },
                ARG_DATE to date,
                ARG_STATUS to pickedStatus,
                ARG_BODY_SITE to binding.deviceFormBodySite.text?.toString()?.trim()?.ifBlank { null },
                ARG_NOTE to binding.deviceFormNote.text?.toString()?.trim()?.ifBlank { null },
            ),
        )
        dismiss()
    }

    private fun reject(messageRes: Int, layout: com.google.android.material.textfield.TextInputLayout, why: String) {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: $why")
        layout.error = getString(messageRes)
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
        binding.deviceFormSaveBtn.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
