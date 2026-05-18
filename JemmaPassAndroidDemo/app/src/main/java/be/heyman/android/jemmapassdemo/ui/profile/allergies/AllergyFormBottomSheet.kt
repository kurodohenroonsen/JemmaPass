/*
 * AllergyFormBottomSheet.kt — JEMMA Pass · Plan B · v2.6.0 · L_PHASE13
 *
 * 🆕 PHASE13 — Cross-check clinique (interactions allergie × médoc) :
 *   Double timing :
 *   1. À chaque pick de substance (onPick) → check immédiat. Si hits, alerte
 *      avec Cancel (clear le picked) / Save anyway (acked, garde la sélection).
 *   2. À chaque submit (onSubmit) → check final avant commit. Si hits, alerte
 *      avec Cancel (stay in form) / Save anyway (commit).
 *
 *   Algo via FormCrossCheckHelper.checkNewAllergyAgainstMeds() :
 *     - Pour chaque med du profile, on regarde si son ATC ancestry contient
 *       la classe allergène. Ex : user a Augmentin (J01CR02 → J01C ancestor)
 *       et ajoute "Penicillin" → match sur class:J01C.
 *
 *   Textes cliniques en EN (KB n'a que des descriptions en anglais).
 *
 * 🆕 v2.6.0 PHASE9 — 2 bug fixes :
 *   1. Pénicilline classifiée 🩹 au lieu de 💊 :
 *      → patch dans KnowledgeBaseServiceAllergyCat (display-aware mapper)
 *   2. Manifestation de réaction = Zar/Gout/Kuru/Koro/Yaws/Noma/Amok/Siti :
 *      → switch from KbConditionPicker.getTopByCategory (alphabetical UMLS
 *        soup) to kb.getAllergyReactionList(lang) which queries the proper
 *        IPS ValueSet 'allergy-reaction-snomed-ct-ips-free-set' (29 codes
 *        cliniquement pertinents : Anaphylaxie, Angioedème, Urticaire,
 *        Prurit, Dyspnée, Bronchospasme, Eczéma, Stevens-Johnson, etc.)
 *
 * 🆕 v2.6.0 L_HERO_BAR — Le substance picker reçoit maintenant aussi le
 * codeToCategory map (en plus du codeToEmoji), ce qui active la rangée
 * de chips de filtrage par catégorie (ALL · 🍴 · 💊 · 🌿 · 🧬 · 🩹).
 *
 * 🆕 v2.6.0 L_PICKERS — Substance picker enrichi :
 *   - Affiche l'emoji catégorie (🍴/💊/🌿/🧬) au lieu du code SNOMED brut
 *   - Au pick, auto-set pickedCategory via SnomedAllergyCategoryResolver
 *     (basé sur le bridge UMLS de knowledge_full.db, ~85% coverage)
 *   - Les codes non-classifiables affichent 🩹 (user toggle manuellement)
 *
 * Champs (alignés JAllergy actuel) :
 *   - Substance (c) : Tappable card → IpsCodePickerDialog avec
 *                     les 328 SNOMED IPS depuis IpsTranslationsRepository
 *   - Sévérité (s) : MaterialButtonToggleGroup 3-way (⚠️ Sévère · Légère · Inconnue)
 *   - Statut (st) : MaterialButtonToggleGroup 3-way (Active · Inactive · Résolue)
 *   - Mécanisme (m) : TextInputEditText, optional (ex: "IgE-mediated")
 *   - Notes/manifestation (d) : TextInputEditText multiline, optional
 *                                (ex: "anaphylaxie, urticaire, œdème…")
 *
 * Mode :
 *   - CREATE : args = mode=CREATE, no index
 *   - EDIT : args = mode=EDIT, index=position, + existing values
 *
 * Result :
 *   setFragmentResult(RESULT_KEY, bundle) avec :
 *     ARG_MODE, ARG_INDEX, ARG_CODE, ARG_DISPLAY (denorm label for fast render),
 *     ARG_SEVERITY, ARG_STATUS, ARG_MECHANISM, ARG_NOTES.
 *
 * Logging : tag JEMMA-ALLERGIES-FORM
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies

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
import be.heyman.android.jemmapassdemo.databinding.BottomSheetAllergyFormBinding
import be.heyman.android.jemmapassdemo.kb.AllergyCategoryEmoji
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.batchResolveAllergyFhirCategory
import be.heyman.android.jemmapassdemo.kb.getAllergyReactionList
import be.heyman.android.jemmapassdemo.pillars.IpsAllergyCategoryCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsAllergyTypeCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsClinicalStatusCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsCriticalityCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsReactionSeverityCatalog
import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JReaction
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.launch

enum class AllergyFormMode { CREATE, EDIT }

@AndroidEntryPoint
class AllergyFormBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "JEMMA-ALLERGIES-FORM"

        const val RESULT_KEY = "allergy_form_result"
        const val ARG_MODE = "mode"
        const val ARG_INDEX = "index"
        const val ARG_CODE = "code"
        const val ARG_DISPLAY = "display"
        const val ARG_SEVERITY = "severity"
        const val ARG_STATUS = "status"
        const val ARG_MECHANISM = "mechanism"
        const val ARG_NOTES = "notes"
        const val ARG_LANG = "lang"
        // 🆕 v2.6.0j — IPS-FULL fields
        const val ARG_TYPE = "type"
        const val ARG_CATEGORY = "category"
        const val ARG_ONSET = "onset"
        const val ARG_CODE_SYSTEM = "code_system"
        const val ARG_REACTION_MANIF_CODE = "reaction_manif_code"
        const val ARG_REACTION_MANIF_DISPLAY = "reaction_manif_display"
        const val ARG_REACTION_MANIF_SYSTEM = "reaction_manif_system"
        const val ARG_REACTION_SEVERITY = "reaction_severity"

        // 🔧 PHASE13 BUGFIX — Profile being edited (may differ from currentProfileId
        // which is the starred profile). Passed by AllergiesEditFragment.
        const val ARG_PROFILE_ID = "profile_id"

        private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun newInstance(
            mode: AllergyFormMode,
            lang: String,
            index: Int = -1,
            existing: JAllergy? = null,
            profileId: String? = null,
        ): AllergyFormBottomSheet = AllergyFormBottomSheet().apply {
            arguments = bundleOf(
                ARG_MODE to mode.name,
                ARG_INDEX to index,
                ARG_LANG to lang,
                ARG_CODE to existing?.c,
                ARG_DISPLAY to existing?.displayLabel,
                ARG_SEVERITY to existing?.s,
                ARG_STATUS to existing?.st,
                ARG_MECHANISM to existing?.m,
                ARG_NOTES to existing?.d,
                // 🆕 v2.6.0j — IPS-FULL
                ARG_TYPE to existing?.type,
                ARG_CATEGORY to existing?.category,
                ARG_ONSET to existing?.onset,
                ARG_CODE_SYSTEM to existing?.codeSystem,
                ARG_REACTION_MANIF_CODE to existing?.reactions?.firstOrNull()?.manifestationCode,
                ARG_REACTION_MANIF_DISPLAY to existing?.reactions?.firstOrNull()?.manifestationDisplay,
                ARG_REACTION_MANIF_SYSTEM to existing?.reactions?.firstOrNull()?.manifestationSystem,
                ARG_REACTION_SEVERITY to existing?.reactions?.firstOrNull()?.severity,
                // 🔧 PHASE13 BUGFIX
                ARG_PROFILE_ID to profileId,
            )
        }
    }

    @Inject
    lateinit var translations: IpsTranslationsRepository

    // 🆕 L_PICKERS — pour résoudre SNOMED code → catégorie FHIR auto
    @Inject
    lateinit var kbService: KnowledgeBaseService

    @Inject
    lateinit var kbManager: KnowledgeBaseManager

    // 🆕 PHASE13 — Cross-check facilities
    @Inject
    lateinit var profilesRepo: be.heyman.android.jemmapassdemo.profiles.ProfilesRepository

    @Inject
    lateinit var crossCheckHelper: be.heyman.android.jemmapassdemo.ui.profile.common.FormCrossCheckHelper

    private var _binding: BottomSheetAllergyFormBinding? = null
    private val binding get() = _binding!!

    private val mode: AllergyFormMode by lazy {
        AllergyFormMode.valueOf(arguments?.getString(ARG_MODE) ?: AllergyFormMode.CREATE.name)
    }
    private val index: Int by lazy { arguments?.getInt(ARG_INDEX, -1) ?: -1 }
    private val lang: String by lazy { arguments?.getString(ARG_LANG) ?: "en" }

    /** Currently picked SNOMED code (e.g. "91936005"). */
    private var pickedCode: String? = null

    /** Currently displayed substance label for the picked code (cached for save). */
    private var pickedDisplay: String? = null

    /** Substance code system URI, set when picked (typically SNOMED). */
    private var pickedCodeSystem: String? = null

    /** Currently picked criticality short code: H/L/U. */
    private var pickedSeverity: String? = null

    /** Currently picked clinical status short code: A/I/R. */
    private var pickedStatus: String? = null

    // 🆕 v2.6.0j — IPS-FULL state
    /** IPS type : "allergy" | "intolerance" | null. */
    private var pickedType: String? = null
    /** IPS category : "food" | "medication" | "environment" | "biologic" | null. */
    private var pickedCategory: String? = null
    /** Onset ISO YYYY-MM-DD or null. */
    private var pickedOnsetIso: String? = null
    /** Reaction.manifestation : KB code (SNOMED) + display + system. */
    private var pickedReactionCode: String? = null
    private var pickedReactionDisplay: String? = null
    private var pickedReactionSystem: String? = null
    /** Reaction.severity : "mild" | "moderate" | "severe" | null. */
    private var pickedReactionSeverity: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetAllergyFormBinding.inflate(inflater, container, false)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 onCreateView · mode=$mode · idx=$index · lang=$lang")
        return binding.root
    }

    /**
     * 🆕 Lot 14.5c21 — Force la BottomSheet à s'ouvrir EXPANDED en plein
     * écran (skip half-collapsed default). Sans ça, le formulaire allergie
     * étant long (substance + type + cat + sev + status + onset + react +
     * mech + notes), le bouton "Sauver" tombe sous l'écran et l'user ne
     * peut pas le voir/cliquer.
     *
     * On force aussi sheet.layoutParams.height = MATCH_PARENT pour que la
     * sheet prenne toute la hauteur dispo (en combinaison avec le layout
     * root match_parent + NestedScrollView weight=1 → save row sticky bas).
     */
    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
        ) ?: return
        sheet.layoutParams = sheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📐 onStart · sheet forced to EXPANDED · " +
                "height=${sheet.height}",
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.allergyFormTitle.setText(
            if (mode == AllergyFormMode.CREATE) R.string.allergy_form_title_create
            else R.string.allergy_form_title_edit
        )

        // Pre-fill from args
        pickedCode = arguments?.getString(ARG_CODE)
        pickedDisplay = arguments?.getString(ARG_DISPLAY)
        pickedCodeSystem = arguments?.getString(ARG_CODE_SYSTEM)
        pickedSeverity = arguments?.getString(ARG_SEVERITY) ?: "U"  // default Unknown
        pickedStatus = arguments?.getString(ARG_STATUS) ?: "A"      // default Active
        binding.allergyFormMechanism.setText(arguments?.getString(ARG_MECHANISM).orEmpty())
        binding.allergyFormNotes.setText(arguments?.getString(ARG_NOTES).orEmpty())

        // 🆕 v2.6.0j — IPS-FULL pre-fill
        pickedType = arguments?.getString(ARG_TYPE)
        pickedCategory = arguments?.getString(ARG_CATEGORY)
        pickedOnsetIso = arguments?.getString(ARG_ONSET)
        pickedReactionCode = arguments?.getString(ARG_REACTION_MANIF_CODE)
        pickedReactionDisplay = arguments?.getString(ARG_REACTION_MANIF_DISPLAY)
        pickedReactionSystem = arguments?.getString(ARG_REACTION_MANIF_SYSTEM)
        pickedReactionSeverity = arguments?.getString(ARG_REACTION_SEVERITY)

        renderSubstanceLabel()
        renderSeverityToggle()
        renderStatusToggle()
        // 🆕 IPS-FULL renders
        renderTypeToggle()
        renderCategoryToggle()
        renderOnsetLabel()
        renderReactionLabel()
        renderReactionSeverityToggle()

        binding.allergyFormSubstanceCard.setOnClickListener { openSubstancePicker() }

        // Severity toggle group buttons
        binding.allergyFormSeverityHigh.setOnClickListener { selectSeverity("H") }
        binding.allergyFormSeverityLow.setOnClickListener { selectSeverity("L") }
        binding.allergyFormSeverityUnknown.setOnClickListener { selectSeverity("U") }

        // Status toggle group buttons
        binding.allergyFormStatusActive.setOnClickListener { selectStatus("A") }
        binding.allergyFormStatusInactive.setOnClickListener { selectStatus("I") }
        binding.allergyFormStatusResolved.setOnClickListener { selectStatus("R") }

        // 🆕 v2.6.0j — IPS-FULL wiring
        binding.allergyFormTypeAllergy.setOnClickListener { selectType("allergy") }
        binding.allergyFormTypeIntolerance.setOnClickListener { selectType("intolerance") }
        binding.allergyFormCategoryFood.setOnClickListener { selectCategory("food") }
        binding.allergyFormCategoryMedication.setOnClickListener { selectCategory("medication") }
        binding.allergyFormCategoryEnvironment.setOnClickListener { selectCategory("environment") }
        binding.allergyFormCategoryBiologic.setOnClickListener { selectCategory("biologic") }
        binding.allergyFormOnsetRow.setOnClickListener { openOnsetPicker() }
        binding.allergyFormOnsetClear.setOnClickListener { clearOnset() }
        binding.allergyFormReactionRow.setOnClickListener { openReactionPicker() }
        binding.allergyFormReactionClear.setOnClickListener { clearReaction() }
        binding.allergyFormReactionSevMild.setOnClickListener { selectReactionSeverity("mild") }
        binding.allergyFormReactionSevModerate.setOnClickListener { selectReactionSeverity("moderate") }
        binding.allergyFormReactionSevSevere.setOnClickListener { selectReactionSeverity("severe") }

        binding.allergyFormCancelBtn.setOnClickListener {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ↩ cancel")
            dismiss()
        }
        binding.allergyFormSaveBtn.setOnClickListener { trySubmit() }
    }

    // ─── Substance label rendering ────────────────────────────────

    private fun renderSubstanceLabel() {
        val code = pickedCode
        if (code.isNullOrBlank()) {
            binding.allergyFormSubstanceCode.text = ""
            binding.allergyFormSubstanceDisplay.setText(R.string.allergy_form_substance_hint)
            return
        }
        binding.allergyFormSubstanceCode.text = code

        // 1) Show cached display first if any
        val cached = pickedDisplay
        if (!cached.isNullOrBlank()) {
            binding.allergyFormSubstanceDisplay.text = cached
        } else {
            binding.allergyFormSubstanceDisplay.text = code
            // 2) Look up the actual SNOMED translation
            viewLifecycleOwner.lifecycleScope.launch {
                val resolved = translations.get(code, lang)
                if (!resolved.isNullOrBlank() && _binding != null && pickedCode == code) {
                    pickedDisplay = resolved
                    binding.allergyFormSubstanceDisplay.text = resolved
                }
            }
        }
    }

    private fun openSubstancePicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open substance picker · lang=$lang")
        viewLifecycleOwner.lifecycleScope.launch {
            val catalog = translations.all(lang)
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 picker catalog loaded · n=${catalog.size}")
            if (catalog.isEmpty()) {
                Toast.makeText(requireContext(),
                    R.string.allergy_form_validation_catalog_empty, Toast.LENGTH_LONG).show()
                return@launch
            }

            // 🆕 L_PICKERS — batch resolve SNOMED → FHIR category pour les 328 codes
            // du catalogue IPS. Une seule query SQL (vs 328 individuelles).
            // Les codes non-résolvables sont absents du map et tombent sur 🩹.
            val codes = catalog.map { it.first }
            val codeToCategory = try {
                kbService.batchResolveAllergyFhirCategory(kbManager, codes)
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ batchResolve failed: ${e.message}", e)
                emptyMap()
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩹 batch category resolve · " +
                "${codeToCategory.size}/${codes.size} classified")

            // Build code→emoji map for the picker prefix display.
            // 🍴 food · 💊 medication · 🌿 environment · 🧬 biologic · 🩹 unknown
            val codeToEmoji = codes.associateWith { code ->
                AllergyCategoryEmoji.forFhir(codeToCategory[code])
            }

            val items = catalog.map { (code, t) ->
                IpsPickerItem(code = code, display = t.pick(lang))
            }
            IpsCodePickerDialog
                .newInstance(
                    title = getString(R.string.allergy_form_substance_picker_title),
                    items = items,
                    codeToPrefix = codeToEmoji,
                    codeToCategory = codeToCategory,  // 🆕 L_HERO_BAR — pour le filtre par chips
                )
                .setOnPicked { picked ->
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ substance picked: " +
                        "code=${picked.code} display='${picked.display}'")
                    pickedCode = picked.code
                    pickedDisplay = picked.display
                    pickedCodeSystem = "http://snomed.info/sct"  // IPS catalog = SNOMED CT
                    renderSubstanceLabel()

                    // 🆕 L_PICKERS — auto-set category if KB resolved it.
                    // Pour les codes non-classifiables, on laisse pickedCategory
                    // tel quel (l'user toggle manuellement).
                    val autoCategory = codeToCategory[picked.code]
                    if (autoCategory != null && pickedCategory != autoCategory) {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 auto-set category: " +
                            "$pickedCategory → $autoCategory (from KB)")
                        pickedCategory = autoCategory
                        renderCategoryToggle()
                    } else if (autoCategory == null) {
                        Log.d(TAG, "[t=${System.currentTimeMillis()}] ℹ️ no auto category " +
                            "for ${picked.code} — user must toggle manually")
                    }

                    // 🆕 PHASE13 — Cross-check at pick : check si la nouvelle
                    // allergie collisionne avec un médoc déjà au profile.
                    runCrossCheckAtPick()
                }
                .show(childFragmentManager, "substance_picker")
        }
    }

    // ─── 🆕 PHASE13 — Cross-check helpers ─────────────────────────────

    /**
     * Loads the profile being edited (NOT necessarily the starred/current
     * one — see PHASE13 BUGFIX) for cross-check. Strips the allergy
     * currently being edited (in EDIT mode) so we don't compare against
     * ourselves. Returns null if no profile available.
     *
     * Resolution order :
     *   1. ARG_PROFILE_ID  ← passed by AllergiesEditFragment (preferred)
     *   2. currentProfileId ← fallback for legacy callers
     */
    private suspend fun loadProfileSnapshotForXchk(): be.heyman.android.jemmapassdemo.qr.JemmaProfileJ? {
        val argPid = arguments?.getString(ARG_PROFILE_ID)?.takeIf { it.isNotBlank() }
        val pid = argPid ?: profilesRepo.currentProfileId ?: return null
        val profile = profilesRepo.loadProfile(pid) ?: return null
        return if (mode == AllergyFormMode.EDIT && index in profile.al.indices) {
            profile.copy(al = profile.al.toMutableList().apply { removeAt(index) })
        } else {
            profile
        }
    }

    /**
     * Cross-check trigger 1/2 : called right after the user picks an allergen
     * in the substance picker. Loads the profile, runs the helper, and shows
     * alert if hits. "Cancel" clears the picked substance ; "Save anyway"
     * just acks (form stays open with substance selected).
     */
    private fun runCrossCheckAtPick() {
        val code = pickedCode
        val display = pickedDisplay ?: code ?: return
        if (code.isNullOrBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = loadProfileSnapshotForXchk() ?: return@launch
            val result = crossCheckHelper.checkNewAllergyAgainstMeds(
                allergyDisplay = display,
                allergyCode = code,
                allergyCodeSystem = pickedCodeSystem,
                profile = profile,
                lang = lang,
            )
            if (result == null || result.isEmpty) {
                Log.d(TAG, "[t=${System.currentTimeMillis()}] 🟢 xchk clean on pick · $display")
                return@launch
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ⚠ xchk hits on pick · " +
                "hits=${result.hits.size} hasHigh=${result.hasHigh}")
            val ctx = context ?: return@launch
            be.heyman.android.jemmapassdemo.ui.profile.common.CrossCheckAlertDialog
                .showForNewAllergy(
                    context = ctx,
                    hits = result.hits,
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

    /**
     * Cross-check trigger 2/2 : called at submit. If hits, shows the alert
     * with [Review] (stay in form) / [Save anyway] (commit). If no profile
     * or no hits, calls proceed() directly.
     */
    private fun runCrossCheckAtSubmit(display: String, proceed: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = loadProfileSnapshotForXchk()
            if (profile == null) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ↪ no profile snapshot — skip xchk, commit")
                proceed()
                return@launch
            }
            val result = crossCheckHelper.checkNewAllergyAgainstMeds(
                allergyDisplay = display,
                allergyCode = pickedCode,
                allergyCodeSystem = pickedCodeSystem,
                profile = profile,
                lang = lang,
            )
            if (result == null || result.isEmpty) {
                Log.d(TAG, "[t=${System.currentTimeMillis()}] 🟢 xchk clean on submit · committing")
                proceed()
                return@launch
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ⚠ xchk hits on submit · hits=${result.hits.size}")
            val ctx = context ?: return@launch
            be.heyman.android.jemmapassdemo.ui.profile.common.CrossCheckAlertDialog
                .showForNewAllergy(
                    context = ctx,
                    hits = result.hits,
                    candidateDisplay = display,
                    onConfirm = {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✓ user confirmed save anyway")
                        proceed()
                    },
                    onCancel = {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✗ user cancelled — stay in form")
                    },
                )
        }
    }

    // ─── Toggle rendering ─────────────────────────────────────────

    private fun selectSeverity(code: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔘 severity selected: $code")
        pickedSeverity = code
        renderSeverityToggle()
    }

    private fun renderSeverityToggle() {
        binding.allergyFormSeverityHigh.isChecked = pickedSeverity == "H"
        binding.allergyFormSeverityLow.isChecked = pickedSeverity == "L"
        binding.allergyFormSeverityUnknown.isChecked = pickedSeverity == "U"
    }

    private fun selectStatus(code: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔘 status selected: $code")
        pickedStatus = code
        renderStatusToggle()
    }

    private fun renderStatusToggle() {
        binding.allergyFormStatusActive.isChecked = pickedStatus == "A"
        binding.allergyFormStatusInactive.isChecked = pickedStatus == "I"
        binding.allergyFormStatusResolved.isChecked = pickedStatus == "R"
    }

    // ─── 🆕 v2.6.0j — IPS-FULL methods ──────────────────────────

    private fun selectType(code: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔘 type selected: $code")
        pickedType = code
        renderTypeToggle()
    }

    private fun renderTypeToggle() {
        binding.allergyFormTypeAllergy.isChecked = pickedType == "allergy"
        binding.allergyFormTypeIntolerance.isChecked = pickedType == "intolerance"
    }

    private fun selectCategory(code: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔘 category selected: $code")
        pickedCategory = code
        renderCategoryToggle()
    }

    private fun renderCategoryToggle() {
        binding.allergyFormCategoryFood.isChecked = pickedCategory == "food"
        binding.allergyFormCategoryMedication.isChecked = pickedCategory == "medication"
        binding.allergyFormCategoryEnvironment.isChecked = pickedCategory == "environment"
        binding.allergyFormCategoryBiologic.isChecked = pickedCategory == "biologic"
    }

    private fun renderOnsetLabel() {
        val iso = pickedOnsetIso
        if (iso.isNullOrBlank()) {
            binding.allergyFormOnsetLabel.setText(R.string.allergy_form_onset_hint)
            binding.allergyFormOnsetClear.visibility = View.GONE
        } else {
            binding.allergyFormOnsetLabel.text = iso
            binding.allergyFormOnsetClear.visibility = View.VISIBLE
        }
    }

    private fun openOnsetPicker() {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📅 onset picker tap")
        val constraints = CalendarConstraints.Builder()
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        val initialSelection = pickedOnsetIso?.let { parseIsoDateUtc(it) }
            ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.allergy_form_onset_pick_title)
            .setSelection(initialSelection)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            val iso = formatUtcMillisAsIso(utcMillis)
            pickedOnsetIso = iso
            renderOnsetLabel()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 onset picked: $iso")
        }
        picker.show(childFragmentManager, "allergy_onset_picker")
    }

    private fun clearOnset() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear onset")
        pickedOnsetIso = null
        renderOnsetLabel()
    }

    private fun renderReactionLabel() {
        val code = pickedReactionCode
        if (code.isNullOrBlank()) {
            binding.allergyFormReactionCode.text = ""
            binding.allergyFormReactionLabel.setText(R.string.allergy_form_reaction_hint)
            binding.allergyFormReactionClear.visibility = View.GONE
            binding.allergyFormReactionSeverityGroup.visibility = View.GONE
        } else {
            binding.allergyFormReactionCode.text = code
            binding.allergyFormReactionLabel.text = pickedReactionDisplay ?: code
            binding.allergyFormReactionClear.visibility = View.VISIBLE
            binding.allergyFormReactionSeverityGroup.visibility = View.VISIBLE
        }
    }

    private fun openReactionPicker() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 open reaction picker · " +
            "switching to IPS-curated list (PHASE9 fix)")
        viewLifecycleOwner.lifecycleScope.launch {
            // 🆕 PHASE9 — Récupère les 29 codes IPS officiels au lieu de
            // l'ancien getTopByCategory("Condition") qui retournait du
            // bruit aléatoire (Zar, Gout, Kuru, Koro...).
            val reactions = try {
                kbService.getAllergyReactionList(kbManager, lang)
            } catch (e: Exception) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ reaction list load failed: ${e.message}", e)
                emptyList()
            }
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 📋 reaction list loaded · n=${reactions.size}")
            if (reactions.isEmpty()) {
                Toast.makeText(requireContext(),
                    R.string.allergy_form_validation_catalog_empty, Toast.LENGTH_LONG).show()
                return@launch
            }
            val items = reactions.map { rx ->
                IpsPickerItem(code = rx.code, display = rx.display)
            }
            IpsCodePickerDialog
                .newInstance(
                    title = getString(R.string.allergy_form_reaction_picker_title),
                    items = items,
                )
                .setOnPicked { picked ->
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ reaction picked · " +
                        "code=${picked.code} · display='${picked.display}'")
                    pickedReactionCode = picked.code
                    pickedReactionDisplay = picked.display
                    pickedReactionSystem = "http://snomed.info/sct"  // IPS reaction VS = SNOMED CT
                    renderReactionLabel()
                }
                .show(childFragmentManager, "reaction_picker")
        }
    }

    private fun clearReaction() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🗑 clear reaction")
        pickedReactionCode = null
        pickedReactionDisplay = null
        pickedReactionSystem = null
        pickedReactionSeverity = null
        renderReactionLabel()
        renderReactionSeverityToggle()
    }

    private fun selectReactionSeverity(code: String) {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔘 reaction severity: $code")
        pickedReactionSeverity = code
        renderReactionSeverityToggle()
    }

    private fun renderReactionSeverityToggle() {
        binding.allergyFormReactionSevMild.isChecked = pickedReactionSeverity == "mild"
        binding.allergyFormReactionSevModerate.isChecked = pickedReactionSeverity == "moderate"
        binding.allergyFormReactionSevSevere.isChecked = pickedReactionSeverity == "severe"
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
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = utcMillis
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(utcMillis))
    }

    // ─── Submit ───────────────────────────────────────────────────

    private fun trySubmit() {
        val code = pickedCode?.takeIf { it.isNotBlank() }
        val display = pickedDisplay?.takeIf { it.isNotBlank() }
        val severity = pickedSeverity?.takeIf { it.isNotBlank() } ?: "U"
        val status = pickedStatus?.takeIf { it.isNotBlank() } ?: "A"
        val mechanism = binding.allergyFormMechanism.text?.toString()?.trim().orEmpty()
        val notes = binding.allergyFormNotes.text?.toString()?.trim().orEmpty()

        // Validation : substance required
        if (code.isNullOrBlank()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: no substance picked")
            Toast.makeText(requireContext(),
                R.string.allergy_form_validation_substance, Toast.LENGTH_SHORT).show()
            return
        }

        // Validation : onset must be ISO if provided
        val onset = pickedOnsetIso?.takeIf { it.isNotBlank() }
        if (onset != null && !ISO_DATE_REGEX.matches(onset)) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ validation: onset invalid · $onset")
            pickedOnsetIso = null
        }

        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 submit · mode=$mode · idx=$index · " +
            "code=$code · display='$display' · severity=$severity · status=$status · " +
            "type=$pickedType · category=$pickedCategory · onset=$onset · " +
            "reactionCode=$pickedReactionCode · reactionSev=$pickedReactionSeverity · " +
            "mechanism='$mechanism' · notes len=${notes.length}")

        // 🆕 PHASE13 — Cross-check before commit
        val commit: () -> Unit = {
            setFragmentResult(
                RESULT_KEY,
                bundleOf(
                    ARG_MODE to mode.name,
                    ARG_INDEX to index,
                    ARG_CODE to code,
                    ARG_DISPLAY to display,
                    ARG_SEVERITY to severity,
                    ARG_STATUS to status,
                    ARG_MECHANISM to mechanism.ifBlank { null },
                    ARG_NOTES to notes.ifBlank { null },
                    // 🆕 v2.6.0j — IPS-FULL
                    ARG_TYPE to pickedType,
                    ARG_CATEGORY to pickedCategory,
                    ARG_ONSET to onset,
                    ARG_CODE_SYSTEM to pickedCodeSystem,
                    ARG_REACTION_MANIF_CODE to pickedReactionCode,
                    ARG_REACTION_MANIF_DISPLAY to pickedReactionDisplay,
                    ARG_REACTION_MANIF_SYSTEM to pickedReactionSystem,
                    ARG_REACTION_SEVERITY to pickedReactionSeverity,
                )
            )
            dismiss()
        }

        val displayForXchk = display ?: code
        runCrossCheckAtSubmit(displayForXchk, commit)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
