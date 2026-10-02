/*
 * MedicationFormBottomSheet.kt — JEMMA Pass · Plan B · v2.6.0 · L_PHASE13
 *
 * 🆕 PHASE13 — Cross-check clinique (interactions médoc × allergie + DDI) :
 *   Double timing :
 *   1. À chaque pick de substance (onPick) → check immédiat. Si hits, alerte
 *      avec Cancel (clear le picked) / Save anyway (acked, garde la sélection).
 *   2. À chaque submit (onSubmit) → check final avant commit. Si hits, alerte
 *      avec Cancel (stay in form) / Save anyway (commit).
 *
 *   Utilise FormCrossCheckHelper qui appelle KbCrossCheck (allergy-class
 *   matching via ATC ancestors + DDInter 2.0 v_ddi_emergency + drug-disease).
 *
 *   Textes cliniques (descriptions, mécanismes, gestion) sont en EN car
 *   la KB n'a ces colonnes qu'en anglais (DDInter source). Un disclaimer
 *   "shown in English (clinical reference language)" est affiché en bas.
 *
 * 🆕 PHASE12 — Smart dose pre-fill + cache code IPS :
 *   - Quand l'user choisit un médicament dans KbDrugPickerDialog, le picker
 *     retourne maintenant aussi atcCode, doseDdd, doseUnit, doseRoute, doseNote
 *     (issus de la table `dosages` — WHO ATC/DDD reference).
 *   - Auto-fill dose : si la dose value du form est vide et que la KB a une
 *     DDD, on pré-remplit value+unit avec format display-friendly
 *     (mcg → μg, u → UI, etc.) ET on auto-coche la route correspondante.
 *   - Le code IPS (champ medicationFormSubstanceCode) est CACHÉ visiblement
 *     (visibility=GONE) — gain d'espace tout en gardant la donnée pour le
 *     persistence + export FHIR/QR.
 *
 * 🆕 PHASE11 — Smart indication picker :
 *   - openReasonCodePicker() résout maintenant l'ATC code du médicament
 *     choisi (via kb.resolveAtcCode) et le passe à KbConditionPicker.
 *   - Le picker affiche alors une section "SUGGÉRÉ pour [drug]" en haut
 *     avec les ~12 indications cliniques les plus probables, suivie de
 *     la liste alphabétique complète.
 *   - Exemple : pour Metformine → suggère "Diabète", "Diabète type 2",
 *     "Hyperglycémie", "Hypoglycémie" en premier ; reste accessible
 *     via scroll ou search.
 *
 * Bottom sheet pour CREATE ou EDIT une JMedication (FHIR MedicationStatement IPS).
 *
 * Champs alignés JMedication actuel :
 *   - Substance (c) : Tappable card → KbDrugPickerDialog (KB DIAMOND v1.2,
 *                     live-search FTS5 sur 300K drugs categorie Medication)
 *   - Route (r) : MaterialButtonToggleGroup 4-way (O/I/T/S)
 *   - Dose value (v) : TextInputEditText numeric
 *   - Dose unit (u) : TextInputEditText (mg, mL, g, IU...)
 *   - Timing (t) : TextInputEditText libre (ex: "1x/jour", "BID", "2× par jour")
 *   - Reason (rs) : TextInputEditText libre (ex: "Hypertension", "Thrombo-prévention")
 *
 * Mode :
 *   - CREATE : args = mode=CREATE, no index
 *   - EDIT : args = mode=EDIT, index=position, + existing values
 *
 * Result :
 *   setFragmentResult(RESULT_KEY, bundle) avec :
 *     ARG_MODE, ARG_INDEX, ARG_CODE, ARG_DISPLAY,
 *     ARG_ROUTE, ARG_DOSE_VALUE, ARG_DOSE_UNIT, ARG_TIMING, ARG_REASON.
 *
 * Logging : tag JEMMA-MEDICATIONS-FORM
 */
package be.heyman.android.jemmapassdemo.ui.profile.medications

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.databinding.BottomSheetMedicationFormBinding
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.ResolvedConcept
import be.heyman.android.jemmapassdemo.kb.resolveAtcCode
import be.heyman.android.jemmapassdemo.pillars.IpsMedicationStatusCatalog
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.ui.common.KbConditionPicker
import be.heyman.android.jemmapassdemo.ui.common.KbDrugPickerDialog
import be.heyman.android.jemmapassdemo.ui.profile.common.CrossCheckAlertDialog
import be.heyman.android.jemmapassdemo.ui.profile.common.FormCrossCheckHelper
import be.heyman.android.jemmapassdemo.ui.profile.common.SingleShotGuard
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
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

enum class MedicationFormMode { CREATE, EDIT }

@AndroidEntryPoint
class MedicationFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "JEMMA-MEDICATIONS-FORM"

        const val RESULT_KEY = "medication_form_result"
        const val ARG_MODE = "mode"
        const val ARG_INDEX = "index"
        const val ARG_CODE = "code"
        const val ARG_DISPLAY = "display"
        const val ARG_ROUTE = "route"
        const val ARG_DOSE_VALUE = "dose_value"
        const val ARG_DOSE_UNIT = "dose_unit"
        const val ARG_TIMING = "timing"
        const val ARG_REASON = "reason"
        const val ARG_LANG = "lang"
        // 🆕 v2.6.0k — IPS-FULL fields
        const val ARG_CODE_SYSTEM = "code_system"
        const val ARG_STATUS = "status"
        const val ARG_EFFECTIVE = "effective"
        const val ARG_REASON_CODE = "reason_code"
        const val ARG_REASON_DISPLAY = "reason_display"
        const val ARG_REASON_SYSTEM = "reason_system"

        // 🔧 PHASE13 BUGFIX — Profile being edited (may differ from currentProfileId
        // which is the starred profile). Passed by MedicationsEditFragment so the
        // cross-check compares against the right meds list.
        const val ARG_PROFILE_ID = "profile_id"

        private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun newInstance(
            mode: MedicationFormMode,
            lang: String,
            index: Int = -1,
            existing: JMedication? = null,
            profileId: String? = null,
        ): MedicationFormBottomSheet = MedicationFormBottomSheet().apply {
            arguments = bundleOf(
                ARG_MODE to mode.name,
                ARG_INDEX to index,
                ARG_LANG to lang,
                ARG_CODE to existing?.c,
                ARG_DISPLAY to existing?.displayLabel,
                ARG_ROUTE to existing?.r,
                ARG_DOSE_VALUE to existing?.v,
                ARG_DOSE_UNIT to existing?.u,
                ARG_TIMING to existing?.t,
                ARG_REASON to existing?.rs,
                // 🆕 v2.6.0k — IPS-FULL
                ARG_CODE_SYSTEM to existing?.codeSystem,
                ARG_STATUS to existing?.status,
                ARG_EFFECTIVE to existing?.effective,
                ARG_REASON_CODE to existing?.rc,
                ARG_REASON_DISPLAY to null,  // not persisted in JMedication for now
                ARG_REASON_SYSTEM to null,
                // 🔧 PHASE13 BUGFIX
                ARG_PROFILE_ID to profileId,
            )
        }
    }

    @Inject
    lateinit var kb: KnowledgeBaseService

    // 🆕 PHASE11 — Needed for resolveAtcCode + suggestProblemsForDrug helpers
    @Inject
    lateinit var kbManager: be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager

    // 🆕 PHASE13 — Cross-check infrastructure (allergy × med + DDI)
    @Inject
    lateinit var crossCheckHelper: FormCrossCheckHelper

    @Inject
    lateinit var profilesRepo: ProfilesRepository

    private var _binding: BottomSheetMedicationFormBinding? = null
    private val binding get() = _binding!!

    private val mode: MedicationFormMode by lazy {
        MedicationFormMode.valueOf(
            arguments?.getString(ARG_MODE) ?: MedicationFormMode.CREATE.name,
        )
    }
    private val index: Int by lazy { arguments?.getInt(ARG_INDEX, -1) ?: -1 }
    private val lang: String by lazy { arguments?.getString(ARG_LANG) ?: "en" }

    private var pickedCode: String? = null
    private var pickedDisplay: String? = null
    private var pickedRoute: String? = null

    // 🔧 PHASE13 BUGFIX — ATC code resolved by the drug picker (KB lookup).
    // Used preferentially at submit-time xchk over `pickedCode` which may
    // be a UMLS CUI (e.g. C3228325 for Augmentin) that resolveDrug() can't
    // re-resolve via FTS5. Set in KbDrugPickerDialog.onPicked callback.
    private var pickedAtc: String? = null

    // 🆕 v2.6.0k — IPS-FULL state
    /** Code system URI for substance (ATC / RxNorm / SNOMED) set by drug picker. */
    private var pickedCodeSystem: String? = null
    /** IPS MedicationStatement.status (active|completed|...|unknown). */
    private var pickedStatus: String? = IpsMedicationStatusCatalog.DEFAULT_CODE
    /** IPS effective ISO YYYY-MM-DD ou null. */
    private var pickedEffectiveIso: String? = null
    /** reasonCode (Condition) from KB. */
    private var pickedReasonCode: String? = null
    private var pickedReasonDisplay: String? = null
    private var pickedReasonSystem: String? = null

    /** UC-MED-007 — a second tap on Save while a submit is in progress is ignored. */
    private val submitGuard = SingleShotGuard()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetMedicationFormBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · mode=$mode · idx=$index · lang=$lang")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.medicationFormTitle.setText(
            if (mode == MedicationFormMode.CREATE) R.string.medication_form_title_create
            else R.string.medication_form_title_edit,
        )

        // Pre-fill
        pickedCode = arguments?.getString(ARG_CODE)
        pickedDisplay = arguments?.getString(ARG_DISPLAY)
        pickedRoute = arguments?.getString(ARG_ROUTE) ?: "O"  // default oral
        // 🆕 v2.6.0k — IPS-FULL pre-fill
        pickedCodeSystem = arguments?.getString(ARG_CODE_SYSTEM)
        pickedStatus = arguments?.getString(ARG_STATUS)?.takeIf { it.isNotBlank() }
            ?: IpsMedicationStatusCatalog.DEFAULT_CODE
        pickedEffectiveIso = arguments?.getString(ARG_EFFECTIVE)
        pickedReasonCode = arguments?.getString(ARG_REASON_CODE)
        pickedReasonDisplay = arguments?.getString(ARG_REASON_DISPLAY)
        pickedReasonSystem = arguments?.getString(ARG_REASON_SYSTEM)

        binding.medicationFormDoseValue.setText(arguments?.getString(ARG_DOSE_VALUE).orEmpty())
        binding.medicationFormDoseUnit.setText(arguments?.getString(ARG_DOSE_UNIT).orEmpty())
        binding.medicationFormTiming.setText(arguments?.getString(ARG_TIMING).orEmpty())
        binding.medicationFormReason.setText(arguments?.getString(ARG_REASON).orEmpty())

        renderSubstanceLabel()
        renderRouteToggle()
        // 🆕 IPS-FULL renders
        renderStatusLabel()
        renderEffectiveLabel()
        renderReasonCodeLabel()

        binding.medicationFormSubstanceCard.setOnClickListener { openDrugPicker() }

        binding.medicationFormRouteOral.setOnClickListener { selectRoute("O") }
        binding.medicationFormRouteInjection.setOnClickListener { selectRoute("I") }
        binding.medicationFormRouteTopical.setOnClickListener { selectRoute("T") }
        binding.medicationFormRouteSubcutaneous.setOnClickListener { selectRoute("S") }

        // 🆕 v2.6.0k — IPS-FULL wiring
        binding.medicationFormStatusRow.setOnClickListener { openStatusPicker() }
        binding.medicationFormEffectiveRow.setOnClickListener { openEffectivePicker() }
        binding.medicationFormEffectiveClear.setOnClickListener { clearEffective() }
        binding.medicationFormReasonCodeRow.setOnClickListener { openReasonCodePicker() }
        binding.medicationFormReasonCodeClear.setOnClickListener { clearReasonCode() }

        binding.medicationFormCancelBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel")
            dismiss()
        }
        binding.medicationFormSaveBtn.setOnClickListener { trySubmit() }
    }

    // ─── Substance ────────────────────────────────────────────────

    private fun renderSubstanceLabel() {
        val code = pickedCode
        // 🆕 PHASE12 — Le code IPS est caché en permanence (gain de place).
        // L'info reste dans pickedCode pour persistence + export FHIR/QR.
        binding.medicationFormSubstanceCode.visibility = View.GONE
        binding.medicationFormSubstanceCode.text = code.orEmpty()

        if (code.isNullOrBlank()) {
            binding.medicationFormSubstanceDisplay.setText(R.string.medication_form_substance_hint)
            return
        }

        val cached = pickedDisplay
        if (!cached.isNullOrBlank()) {
            binding.medicationFormSubstanceDisplay.text = cached
        } else {
            binding.medicationFormSubstanceDisplay.text = code
            viewLifecycleOwner.lifecycleScope.launch {
                val resolved = kb.resolveDrug(code)
                val display = when (resolved) {
                    is ResolvedConcept.Exact -> resolved.value.primaryDisplay
                    is ResolvedConcept.Prefix -> resolved.value.primaryDisplay
                    is ResolvedConcept.Contains -> resolved.value.primaryDisplay
                    is ResolvedConcept.NotFound -> null
                }
                if (!display.isNullOrBlank() && _binding != null && pickedCode == code) {
                    pickedDisplay = display
                    binding.medicationFormSubstanceDisplay.text = display
                }
            }
        }
    }

    // ─── 🆕 PHASE13 — Cross-check helpers ─────────────────────────────

    /**
     * Loads the profile being edited (NOT necessarily the starred/current
     * one — see PHASE13 BUGFIX) for cross-check. Strips the medication
     * currently being edited (in EDIT mode) so we don't compare against
     * ourselves. Returns null if no profile available.
     *
     * Resolution order :
     *   1. ARG_PROFILE_ID  ← passed by MedicationsEditFragment (preferred)
     *   2. currentProfileId ← fallback for legacy callers
     */
    private suspend fun loadProfileSnapshotForXchk(): JemmaProfileJ? {
        val argPid = arguments?.getString(ARG_PROFILE_ID)?.takeIf { it.isNotBlank() }
        val pid = argPid ?: profilesRepo.currentProfileId ?: return null
        val profile = profilesRepo.loadProfile(pid) ?: return null
        return if (mode == MedicationFormMode.EDIT && index in profile.md.indices) {
            profile.copy(md = profile.md.toMutableList().apply { removeAt(index) })
        } else {
            profile
        }
    }

    /**
     * Cross-check trigger 1/2 : called right after the user picks a substance
     * in the drug picker. Loads the profile, runs the helper, and shows alert
     * if hits. "Cancel" clears the picked substance ; "Save anyway" just acks
     * (form stays open with substance selected).
     */
    private fun triggerCrossCheckOnPick(codeOrAtc: String, display: String) {
        if (codeOrAtc.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = loadProfileSnapshotForXchk() ?: return@launch
            val result = crossCheckHelper.checkNewMedicationAgainstProfile(
                medDisplay = codeOrAtc,
                profile = profile,
                lang = lang,
            ) ?: return@launch
            if (result.totalHits == 0) {
                Log.d(TAG, "[t=${System.currentTimeMillis()}] 🟢 xchk clean on pick · $codeOrAtc")
                return@launch
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ⚠ xchk hits on pick · " +
                "al=${result.allergyHits.size} ddi=${result.ddiHits.size} dd=${result.drugDiseaseHits.size}")
            val ctx = context ?: return@launch
            CrossCheckAlertDialog.showForNewMedication(
                context = ctx,
                result = result,
                candidateDisplay = display,
                onConfirm = {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✓ user acked xchk on pick")
                },
                onCancel = {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✗ user cancelled — clearing picked substance")
                    pickedCode = null
                    pickedDisplay = null
                    pickedCodeSystem = null
                    renderSubstanceLabel()
                },
            )
        }
    }

    // ─── Drug picker ──────────────────────────────────────────────────

    private fun openDrugPicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open drug picker · lang=$lang")
        KbDrugPickerDialog
            .newInstance(
                title = getString(R.string.medication_form_substance_picker_title),
                lang = lang,
            )
            .setOnPicked { picked ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ drug picked · " +
                    "code=${picked.code} · system=${picked.system} · display='${picked.display}' · " +
                    "atc=${picked.atcCode} · dose=${picked.doseDdd}${picked.doseUnit} route=${picked.doseRoute}")
                pickedCode = picked.code
                pickedDisplay = picked.display
                pickedCodeSystem = picked.system  // 🆕 IPS-FULL : record source system
                pickedAtc = picked.atcCode  // 🔧 PHASE13 BUGFIX — keep ATC for xchk at submit

                // 🆕 PHASE12 — Auto-fill dose si la KB a une DDD (WHO ATC/DDD reference)
                // Sécurité : on n'auto-fill QUE si le dose value est actuellement vide
                // (l'user peut overrider en cliquant à nouveau pour rechoisir le médoc).
                val ddd = picked.doseDdd
                val unit = picked.doseUnit
                if (ddd != null && unit != null) {
                    val currentValue = binding.medicationFormDoseValue.text?.toString()?.trim().orEmpty()
                    val currentUnit = binding.medicationFormDoseUnit.text?.toString()?.trim().orEmpty()
                    if (currentValue.isEmpty() && currentUnit.isEmpty()) {
                        // Format value : 2.0 → "2", 0.85 → "0.85"
                        val valueStr = if (ddd == ddd.toLong().toDouble()) {
                            ddd.toLong().toString()
                        } else {
                            ddd.toString().trimEnd('0').trimEnd('.')
                        }
                        // Map unit raw KB → display UCUM (cf formatDoseForDisplay logic)
                        val displayUnit = when (unit.lowercase()) {
                            "mcg" -> "μg"
                            "u" -> if (lang.startsWith("en")) "IU" else "UI"
                            "tu" -> if (lang.startsWith("en")) "kIU" else "kUI"
                            "mu" -> if (lang.startsWith("en")) "MIU" else "MUI"
                            "ml" -> "mL"
                            "tablet" -> if (lang.startsWith("en")) "tab" else "cp"
                            else -> unit
                        }
                        binding.medicationFormDoseValue.setText(valueStr)
                        binding.medicationFormDoseUnit.setText(displayUnit)
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊 auto-filled dose: $valueStr $displayUnit (WHO DDD ref)")
                    } else {
                        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🚫 dose not auto-filled · existing value preserved")
                    }
                    // Auto-cocher la route correspondante si on a un mapping
                    val mappedRoute = when (picked.doseRoute?.lowercase()?.trim()) {
                        "oral", "sublingual", "chewing gum" -> "O"
                        "parenteral", "implant", "s.c. implant",
                        "inhal.aerosol", "inhal.powder", "inhal.solution" -> "I"
                        "topical", "transdermal", "nasal",
                        "instill.solution", "intravesical",
                        "rectal", "vaginal" -> "T"
                        else -> null
                    }
                    if (mappedRoute != null && pickedRoute == "O") {
                        // On considère que "O" est le default — si l'user n'a pas explicitement
                        // choisi autre chose, on aligne sur la route DDD
                        pickedRoute = mappedRoute
                        renderRouteToggle()
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊 auto-route: $mappedRoute (from KB route '${picked.doseRoute}')")
                    }
                }

                renderSubstanceLabel()

                // 🆕 PHASE13 — Cross-check on pick (timing 1/2)
                triggerCrossCheckOnPick(picked.atcCode ?: picked.code, picked.display)
            }
            .show(childFragmentManager, "drug_picker")
    }

    // ─── Route toggle ─────────────────────────────────────────────

    private fun selectRoute(code: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔘 route selected: $code")
        pickedRoute = code
        renderRouteToggle()
    }

    private fun renderRouteToggle() {
        binding.medicationFormRouteOral.isChecked = pickedRoute == "O"
        binding.medicationFormRouteInjection.isChecked = pickedRoute == "I"
        binding.medicationFormRouteTopical.isChecked = pickedRoute == "T"
        binding.medicationFormRouteSubcutaneous.isChecked = pickedRoute == "S"
    }

    // ─── 🆕 v2.6.0k — IPS-FULL : Status picker ───────────────────

    private fun renderStatusLabel() {
        val code = pickedStatus
        val entry = IpsMedicationStatusCatalog.byCode(code)
        if (entry == null) {
            binding.medicationFormStatusLabel.setText(R.string.medication_form_status_hint)
        } else {
            binding.medicationFormStatusLabel.text = "${entry.emoji} ${entry.pick(lang)}"
        }
    }

    private fun openStatusPicker() {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 status picker tap")
        val catalog = IpsMedicationStatusCatalog.ALL
        val labels = catalog.map { "${it.emoji}  ${it.pick(lang)}" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.medication_form_status_pick_title)
            .setItems(labels) { _, which ->
                val pick = catalog[which]
                pickedStatus = pick.code
                renderStatusLabel()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ status picked: ${pick.code}")
            }
            .show()
    }

    // ─── 🆕 v2.6.0k — IPS-FULL : Effective date ───────────────────

    private fun renderEffectiveLabel() {
        val iso = pickedEffectiveIso
        if (iso.isNullOrBlank()) {
            binding.medicationFormEffectiveLabel.setText(R.string.medication_form_effective_hint)
            binding.medicationFormEffectiveClear.visibility = View.GONE
        } else {
            binding.medicationFormEffectiveLabel.text = iso
            binding.medicationFormEffectiveClear.visibility = View.VISIBLE
        }
    }

    private fun openEffectivePicker() {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📅 effective picker tap")
        // UC-MED-010 — setEnd only bounds the months shown : the validator is what
        // makes the days after today unselectable.
        val constraints = CalendarConstraints.Builder()
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
            .setValidator(DateValidatorPointBackward.now())
            .build()
        val initial = pickedEffectiveIso?.let { parseIsoDateUtc(it) }
            ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.medication_form_effective_pick_title)
            .setSelection(initial)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            val iso = formatUtcMillisAsIso(utcMillis)
            pickedEffectiveIso = iso
            renderEffectiveLabel()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 effective picked: $iso")
        }
        picker.show(childFragmentManager, "medication_effective_picker")
    }

    private fun clearEffective() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear effective")
        pickedEffectiveIso = null
        renderEffectiveLabel()
    }

    // ─── 🆕 v2.6.0k — IPS-FULL : Reason Code (KB Condition) ────

    private fun renderReasonCodeLabel() {
        val code = pickedReasonCode
        if (code.isNullOrBlank()) {
            binding.medicationFormReasonCode.text = ""
            binding.medicationFormReasonCodeLabel.setText(R.string.medication_form_reason_code_hint)
            binding.medicationFormReasonCodeClear.visibility = View.GONE
        } else {
            binding.medicationFormReasonCode.text = code
            binding.medicationFormReasonCodeLabel.text = pickedReasonDisplay ?: code
            binding.medicationFormReasonCodeClear.visibility = View.VISIBLE
        }
    }

    private fun openReasonCodePicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open reason (Condition) picker · " +
            "drug code=$pickedCode system=$pickedCodeSystem")
        // 🆕 PHASE11 — Résout l'ATC code du médicament choisi (si dispo)
        // pour activer le smart-suggest dans le picker.
        viewLifecycleOwner.lifecycleScope.launch {
            val atc = kb.resolveAtcCode(kbManager, pickedCode, pickedCodeSystem)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 💡 resolved atc=$atc for picker context")
            KbConditionPicker
                .newInstance(
                    title = getString(R.string.medication_form_reason_code_picker_title),
                    lang = lang,
                    atcCode = atc,
                )
                .setOnPicked { picked ->
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ reason picked · " +
                        "code=${picked.code} · system=${picked.system} · display='${picked.display}'")
                    pickedReasonCode = picked.code
                    pickedReasonDisplay = picked.display
                    pickedReasonSystem = picked.system
                    renderReasonCodeLabel()
                }
                .show(childFragmentManager, "reason_code_picker")
        }
    }

    private fun clearReasonCode() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear reasonCode")
        pickedReasonCode = null
        pickedReasonDisplay = null
        pickedReasonSystem = null
        renderReasonCodeLabel()
    }

    private fun parseIsoDateUtc(iso: String): Long? {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(iso)?.time
        } catch (_: Exception) { null }
    }

    private fun formatUtcMillisAsIso(utcMillis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(utcMillis))
    }

    // ─── Submit ───────────────────────────────────────────────────

    private fun trySubmit() {
        // UC-MED-007 — the cross-check below is asynchronous : without this guard a
        // second tap sends a second result, i.e. a duplicate medication.
        if (!submitGuard.tryAcquire()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⏳ submit already in progress — tap ignored")
            return
        }
        binding.medicationFormSaveBtn.isEnabled = false

        val code = pickedCode?.takeIf { it.isNotBlank() }
        val display = pickedDisplay?.takeIf { it.isNotBlank() }
        val route = pickedRoute?.takeIf { it.isNotBlank() } ?: "O"
        val doseValue = binding.medicationFormDoseValue.text?.toString()?.trim().orEmpty()
        val doseUnit = binding.medicationFormDoseUnit.text?.toString()?.trim().orEmpty()
        val timing = binding.medicationFormTiming.text?.toString()?.trim().orEmpty()
        val reason = binding.medicationFormReason.text?.toString()?.trim().orEmpty()

        if (code.isNullOrBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: no substance picked")
            Toast.makeText(requireContext(),
                R.string.medication_form_validation_substance, Toast.LENGTH_SHORT).show()
            releaseSubmit()
            return
        }

        // Dose value : if provided, must be numeric (positive)
        if (doseValue.isNotBlank()) {
            val asDouble = doseValue.replace(',', '.').toDoubleOrNull()
            if (asDouble == null || asDouble <= 0) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: dose value invalid '$doseValue'")
                Toast.makeText(requireContext(),
                    R.string.medication_form_validation_dose, Toast.LENGTH_SHORT).show()
                binding.medicationFormDoseValue.requestFocus()
                releaseSubmit()
                return
            }
        }

        // 🆕 v2.6.0k — Validate effective ISO if provided
        val effective = pickedEffectiveIso?.takeIf { it.isNotBlank() }
        if (effective != null && !ISO_DATE_REGEX.matches(effective)) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ effective invalid · $effective · resetting")
            pickedEffectiveIso = null
        }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 submit (pre-xchk) · mode=$mode · idx=$index · " +
            "code=$code · display='$display' · route=$route · v='$doseValue' · u='$doseUnit' · " +
            "t='$timing' · rs='$reason' · " +
            "status=$pickedStatus · effective=$effective · " +
            "reasonCode=$pickedReasonCode · codeSystem=$pickedCodeSystem")

        // 🆕 PHASE13 — Cross-check on submit (timing 2/2). If hits, show alert with
        // Cancel (stay in form) / Save anyway (commit). What the check could NOT
        // verify (medication not recognised, stored medications without a code, check
        // not run) is then shown too, instead of committing as if all was verified.
        val commit: () -> Unit = {
            commitSubmit(code, display, route, doseValue, doseUnit, timing, reason, effective)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = loadProfileSnapshotForXchk()
                if (profile == null) {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ↪ no profile snapshot — skip xchk, commit")
                    commit()
                    return@launch
                }
                val result = crossCheckHelper.checkNewMedicationAgainstProfile(
                    medDisplay = pickedAtc ?: display ?: code,  // 🔧 PHASE13 BUGFIX prefer ATC (KB-resolvable), fallback display name (FTS5-resolvable), last raw code
                    profile = profile,
                    lang = lang,
                )
                val gaps = MedicationFormLogic.safetyCheckGaps(
                    profileHasData = profile.al.isNotEmpty() || profile.md.isNotEmpty() || profile.cn.isNotEmpty(),
                    resultAvailable = result != null,
                    candidateAtc = result?.candidateAtc,
                    otherMeds = profile.md,
                )
                val candidateLabel = display ?: code
                if (result == null || result.totalHits == 0) {
                    Log.d(TAG, "[t=${System.currentTimeMillis()}] 🟢 no xchk hit on submit · gaps=$gaps")
                    confirmSafetyGapsThen(gaps, candidateLabel, commit)
                    return@launch
                }
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ⚠ xchk hits on submit · " +
                    "al=${result.allergyHits.size} ddi=${result.ddiHits.size} dd=${result.drugDiseaseHits.size}")
                val ctx = context
                if (ctx == null) {
                    releaseSubmit()
                    return@launch
                }
                CrossCheckAlertDialog.showForNewMedication(
                    context = ctx,
                    result = result,
                    candidateDisplay = candidateLabel,
                    onConfirm = {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✓ user confirmed save anyway")
                        confirmSafetyGapsThen(gaps, candidateLabel, commit)
                    },
                    onCancel = {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✗ user cancelled — stay in form")
                        releaseSubmit()
                    },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ submit failed: ${e.message}", e)
                releaseSubmit()
                context?.let {
                    Toast.makeText(it, getString(R.string.assistant_save_failed, e.message.orEmpty()),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Tells the user what the safety cross-check could not verify, then [proceed] on
     * "Save anyway". Calls [proceed] directly when the check was complete.
     */
    private fun confirmSafetyGapsThen(
        gaps: MedicationFormLogic.SafetyCheckGaps,
        candidateLabel: String,
        proceed: () -> Unit,
    ) {
        if (!gaps.any) {
            proceed()
            return
        }
        val ctx = context
        if (ctx == null) {
            releaseSubmit()
            return
        }
        val message = when {
            gaps.checkNotRun -> getString(R.string.medication_form_xchk_gap_not_run)
            gaps.candidateUnresolved ->
                getString(R.string.medication_form_xchk_gap_unresolved, candidateLabel)
            else -> getString(
                R.string.medication_form_xchk_gap_uncoded_meds,
                gaps.uncodedExistingMeds.joinToString(", "),
            )
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ⚠ xchk gap shown · $gaps")
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.medication_form_xchk_gap_title)
            .setMessage(message)
            .setPositiveButton(R.string.xchk_save_anyway) { _, _ -> proceed() }
            .setNegativeButton(R.string.xchk_cancel) { _, _ -> releaseSubmit() }
            .setOnCancelListener { releaseSubmit() }
            .show()
    }

    /** UC-MED-007 — the form stays open : allow a new Save. */
    private fun releaseSubmit() {
        submitGuard.release()
        _binding?.medicationFormSaveBtn?.isEnabled = true
    }

    /**
     * 🆕 PHASE13 — Actual commit step (extracted from trySubmit so the cross-check
     * alert can defer it). Sets fragment result + dismisses the BottomSheet.
     */
    private fun commitSubmit(
        code: String,
        display: String?,
        route: String,
        doseValue: String,
        doseUnit: String,
        timing: String,
        reason: String,
        effective: String?,
    ) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 commit · code=$code")
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                ARG_MODE to mode.name,
                ARG_INDEX to index,
                ARG_CODE to code,
                ARG_DISPLAY to display,
                ARG_ROUTE to route,
                ARG_DOSE_VALUE to doseValue.ifBlank { null },
                ARG_DOSE_UNIT to doseUnit.ifBlank { null },
                ARG_TIMING to timing.ifBlank { null },
                ARG_REASON to reason.ifBlank { null },
                // 🆕 v2.6.0k — IPS-FULL
                ARG_CODE_SYSTEM to pickedCodeSystem,
                ARG_STATUS to pickedStatus,
                ARG_EFFECTIVE to effective,
                ARG_REASON_CODE to pickedReasonCode,
                ARG_REASON_DISPLAY to pickedReasonDisplay,
                ARG_REASON_SYSTEM to pickedReasonSystem,
            ),
        )
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
