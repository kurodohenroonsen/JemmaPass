/*
 * ProfileDetailFragment.kt — v2.2.16.4
 *
 * Rich detail screen for a single profile. The Fragment loads the full
 * `JemmaProfileJ` from disk via [ProfilesRepository], then enriches it
 * with KB-resolved displays + clinical cross-checks via
 * [JemmaProfileHydrator], and renders :
 *
 *   ┌───────────────────────────────────────────┐
 *   │ 👤 Haru Nakamura                          │
 *   │    📅 1956-02-05 · 🩸 A+                  │
 *   │ ─────────────────────────────────────     │
 *   │ ⚠ 1 interaction MAJEURE détectée          │  ← red banner if any
 *   │ ─────────────────────────────────────     │
 *   │ 🩹 ALLERGIES (5)                          │
 *   │   • Pénicilline   [HIGH]                  │
 *   │   • Aspirine      [LOW]                   │
 *   │ 💊 MÉDICAMENTS (6)                        │
 *   │   • Warfarine 5mg                         │
 *   │   • Ibuprofène 400mg   ⚠                  │  ← red dot if in alert
 *   │ 🩺 CONDITIONS (1)                         │
 *   │   • Insuffisance cardiaque                │
 *   │ ⚡ ALERTES                                │
 *   │   • Warfarine × Ibuprofène · Major        │
 *   │     PD_Synergistic                        │
 *   │     [tap to see details]                  │
 *   └───────────────────────────────────────────┘
 *
 * Tap on a DDI alert → bottom sheet with full description / management
 * (currently rendered as an AlertDialog for simplicity ; can be promoted
 * to a BottomSheetDialogFragment later).
 *
 * Empty / not-found case : if the profile id is null or load fails,
 * show a Toast and pop back to the list.
 */
package be.heyman.android.jemmapassdemo.ui.profiles.detail

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.assistant.AlertVulgariseContext
import be.heyman.android.jemmapassdemo.ai.assistant.VulgariseRepository
import be.heyman.android.jemmapassdemo.ai.assistant.ThrottledTextAppender
import be.heyman.android.jemmapassdemo.ai.assistant.buildVulgariseSystemPrompt
import be.heyman.android.jemmapassdemo.ai.assistant.buildMedicationSystemPrompt
import be.heyman.android.jemmapassdemo.ai.assistant.MedicationVulgariseContext
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.tts.JemmaTtsService
import be.heyman.android.jemmapassdemo.kb.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaPdfExporter
import java.util.Locale
import be.heyman.android.jemmapassdemo.databinding.FragmentProfileDetailBinding
import be.heyman.android.jemmapassdemo.kb.AllergyAlert
import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.DDIResult
import be.heyman.android.jemmapassdemo.kb.DdiAlert
import be.heyman.android.jemmapassdemo.kb.DrugDiseaseAlert
import be.heyman.android.jemmapassdemo.kb.HydratedAllergy
import be.heyman.android.jemmapassdemo.kb.HydratedGenericEntry
import be.heyman.android.jemmapassdemo.kb.HydratedMedication
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.JemmaProfileHydrator
import be.heyman.android.jemmapassdemo.pillars.PillarRegistry
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.ui.profiles.ProfilesFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ProfileDetailFragment : Fragment() {

    companion object {
        private const val TAG = "JEMMA-PROFILE-DETAIL"
    }

    @Inject lateinit var repository: ProfilesRepository
    @Inject lateinit var hydrator: JemmaProfileHydrator
    @Inject lateinit var kb: KnowledgeBaseService
    @Inject lateinit var kbManager: KnowledgeBaseManager

    /** 🆕 Lot 14.5c8 — Gemma pour vulgariser les alertes dans le dialog de détail. */
    @Inject lateinit var gemma: GemmaSession
    @Inject lateinit var vulgariseRepo: be.heyman.android.jemmapassdemo.ai.assistant.VulgariseRepository
    @Inject lateinit var tts: be.heyman.android.jemmapassdemo.ai.tts.JemmaTtsService

    private var _binding: FragmentProfileDetailBinding? = null
    private val binding get() = _binding!!

    private var currentProfileId: String? = null
    private var hydratedProfile: HydratedProfile? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tts.init() // 🆕 Lot 14.5c16 — Init TTS engine for vulgarisation streaming.

        val profileId = arguments?.getString(ProfilesFragment.ARG_PROFILE_ID)
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 📋 onViewCreated · id=$profileId")
        currentProfileId = profileId

        binding.profileDetailToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // 🆕 Lot 14.5c43 — re-wire le bouton export 📤 dans la toolbar.
        // Le menu XML @menu/menu_profile_detail existait depuis le Lot 14.1
        // mais n'était plus inflate (manque `app:menu` côté layout) ni
        // câblé côté Kotlin → bouton invisible, plus aucun moyen d'ouvrir
        // QrViewerFragment depuis le détail d'un profil.
        binding.profileDetailToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_profile_detail_export -> {
                    val pid = currentProfileId
                    val h = hydratedProfile
                    if (pid.isNullOrBlank() || h == null) {
                        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ export tap · profile not ready — ignoring")
                        Toast.makeText(requireContext(),
                            getString(R.string.profile_detail_load_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        val options = arrayOf(
                            "📱 QR Codes (Application & Fallback)",
                            "📄 Pass PDF A4 (Pliable en 4)"
                        )
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.share_jemma_pass_title)
                            .setItems(options) { _, which ->
                                when (which) {
                                    0 -> {
                                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📤 Option QR Codes clicked → navigating to QrViewer")
                                        findNavController().navigate(
                                            R.id.action_detail_to_qr_viewer,
                                            bundleOf(
                                                "profileId" to pid,
                                                "formatKey" to "pruned",
                                            ),
                                        )
                                    }
                                    1 -> {
                                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📤 Option PDF clicked → exporting PDF directly")
                                        viewLifecycleOwner.lifecycleScope.launch {
                                            JemmaPdfExporter.exportPdf(requireContext(), h.raw, hydrator)
                                        }
                                    }
                                }
                            }
                            .show()
                    }
                    true
                }
                else -> false
            }
        }

        // 🆕 Lot 14.5c12 — Toggle expand/collapse de la section pillars.
        // Par défaut seule la 1ère ligne (Patient/Allergies/Médicaments/Contacts)
        // est visible. Tap sur le header → montre/cache les rows 2-5 + rotate
        // le chevron ▾ ↔ ▴.
        val pillarsExtra = view.findViewById<View>(R.id.profile_detail_pillars_extra)
        val pillarsHeader = view.findViewById<View>(R.id.profile_detail_pillars_header)
        val pillarsChevron = view.findViewById<TextView>(R.id.profile_detail_pillars_chevron)
        pillarsHeader?.setOnClickListener {
            val expanded = pillarsExtra?.visibility == View.VISIBLE
            val nextVisible = !expanded
            pillarsExtra?.visibility = if (nextVisible) View.VISIBLE else View.GONE
            pillarsChevron?.text = if (nextVisible) "▴" else "▾"
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🎴 pillars section toggled · expanded=$nextVisible",
            )
        }

        if (profileId.isNullOrBlank()) {
            toastAndExit(R.string.profile_detail_load_failed)
            return
        }

        loadAndRender(profileId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Loading + hydration
    // ──────────────────────────────────────────────────────────────────────

    private fun loadAndRender(profileId: String) {
        binding.profileDetailLoading.isVisible = true
        binding.profileDetailContent.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val profile = repository.loadProfile(profileId)
            if (profile == null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ profile not found id=$profileId")
                toastAndExit(R.string.profile_detail_load_failed)
                return@launch
            }

            // UI lang : take the device's primary language, defaulting to English.
            val uiLang = Locale.getDefault().language.lowercase().take(2).ifBlank { "en" }
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🧪 hydrating profile ($uiLang)..."
            )
            val hydrated = hydrator.hydrate(profile, uiLang = uiLang)
            hydratedProfile = hydrated
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] ✅ hydrated in ${hydrated.hydrationMs}ms · " +
                    "alerts ddi=${hydrated.ddiAlerts.size} al×md=${hydrated.allergyAlerts.size}"
            )

            renderProfile(hydrated)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Rendering
    // ──────────────────────────────────────────────────────────────────────

    private fun renderProfile(h: HydratedProfile) {
        val raw = h.raw
        val ctx = requireContext()

        binding.profileDetailLoading.isVisible = false
        binding.profileDetailContent.isVisible = true

        // ── Header ──
        val name = listOfNotNull(raw.p?.gn, raw.p?.fn)
            .joinToString(" ")
            .ifBlank { "—" }
        binding.profileDetailName.text = "👤  $name"

        val headerLine = buildList {
            raw.p?.bd?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.profile_detail_birthdate, it))
            }
            raw.p?.bt?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.profile_detail_blood, it))
            }
        }.joinToString(" · ")
        binding.profileDetailMeta.text = headerLine
        binding.profileDetailMeta.isVisible = headerLine.isNotEmpty()

        // ── Alert banner (red if any Major DDI / allergy) ──
        // UC-SAFE-UI-10.. — the unverified count comes from the checks themselves.
        val safety = SafetyBannerDecision.decide(
            checks = h.checks,
            totalAlerts = h.ddiAlerts.size + h.allergyAlerts.size + h.drugDiseaseAlerts.size,
            majorAlerts = h.totalMajorAlerts,
        )
        when (safety.alert) {
            SafetyAlertBanner.MAJOR -> binding.profileDetailAlertBanner.text = ctx.getString(
                R.string.profile_detail_alert_banner_major,
                safety.majorCount,
            )
            SafetyAlertBanner.OTHER -> binding.profileDetailAlertBanner.text = ctx.getString(
                R.string.profile_detail_alert_banner_other,
            )
            SafetyAlertBanner.NONE -> Unit
        }
        binding.profileDetailAlertBanner.isVisible = safety.showAlert

        // ── Safety check status (amber) : UC-SAFE-UI — "no alert" must not look
        //    like "verified clean" when the checks did not (fully) run. Shown
        //    next to the alert banner when both apply.
        val safetyNote: String? = when (safety.note) {
            SafetyCheckNote.NOT_CHECKED -> ctx.getString(R.string.profile_detail_safety_not_checked)
            SafetyCheckNote.INCOMPLETE ->
                if (safety.unverifiedCount > 0) {
                    ctx.resources.getQuantityString(
                        R.plurals.profile_detail_safety_incomplete_count,
                        safety.unverifiedCount,
                        safety.unverifiedCount,
                    )
                } else {
                    ctx.getString(R.string.profile_detail_safety_incomplete_generic)
                }
            SafetyCheckNote.NONE -> null
        }
        if (safetyNote != null) {
            val full = safetyNote + "\n" + ctx.getString(R.string.profile_detail_safety_status_hint)
            binding.profileDetailSafetyStatusBanner.text = full
            binding.profileDetailSafetyStatusBanner.contentDescription = ctx.getString(
                R.string.profile_detail_safety_status_desc,
                full.replace("ⓘ ", ""),
            )
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ safety banner · ${safety.verdict} · ${h.checks}")
        }
        binding.profileDetailSafetyStatusBanner.isVisible = safetyNote != null

        // ── Allergies ──
        renderAllergies(h)

        // ── Medications ──
        renderMedications(h)

        // ── Conditions ──
        renderConditions(h)
        renderImmunizations(h)
        renderProcedures(h)
        renderDevices(h)
        renderResults(h)
        renderPastProblems(h)
        renderPregnancy(h)
        renderFunctional(h)

        // ── Alerts section ──
        renderAlerts(h)

        // ── 🆕 v2.6.0c L5 — Pillars grid (18 tiles) ──
        renderPillars(h)
    }

    private fun renderAllergies(h: HydratedProfile) {
        if (h.allergies.isEmpty()) {
            binding.profileDetailAllergiesSection.isVisible = false
            return
        }
        binding.profileDetailAllergiesSection.isVisible = true
        binding.profileDetailAllergiesTitle.text = getString(
            R.string.profile_detail_allergies_title,
            h.allergies.size,
        )

        val listView = binding.profileDetailAllergiesList
        listView.removeAllViews()
        val matchedSet = h.allergyAlerts.map { it.allergyDisplay }.toSet()
        for (allergy in h.allergies) {
            val tv = makeListItemTextView()
            val highlighted = matchedSet.contains(allergy.displayLocalized)
            tv.text = formatAllergyLine(allergy, highlighted)
            listView.addView(tv)
        }
    }

    private fun formatAllergyLine(
        allergy: HydratedAllergy,
        highlighted: Boolean,
    ): CharSequence = buildSpannedString {
        append(if (highlighted) "⚠️  • " else "•  ")
        append(allergy.displayLocalized)

        val critLabel = when (allergy.criticality) {
            AllergyCriticality.HIGH -> getString(R.string.profile_detail_criticality_high)
            AllergyCriticality.LOW -> getString(R.string.profile_detail_criticality_low)
            AllergyCriticality.UNABLE_TO_ASSESS -> getString(R.string.profile_detail_criticality_unknown)
        }
        append("  ")
        inSpans(StyleSpan(android.graphics.Typeface.BOLD)) {
            append("[$critLabel]")
        }
    }

    private fun renderMedications(h: HydratedProfile) {
        if (h.medications.isEmpty()) {
            binding.profileDetailMedicationsSection.isVisible = false
            return
        }
        binding.profileDetailMedicationsSection.isVisible = true
        binding.profileDetailMedicationsTitle.text = getString(
            R.string.profile_detail_medications_title,
            h.medications.size,
        )

        val container = binding.profileDetailMedicationsList
        container.removeAllViews()
        
        // 🆕 Lot 14.5c20 — 2-column grid for "plusieurs par ligne"
        val grid = android.widget.GridLayout(requireContext()).apply {
            columnCount = 2
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2)
        }
        container.addView(grid)

        for (med in h.medications) {
            val card = createMedicationCard(med, h)
            grid.addView(card)
        }
    }

    private fun createMedicationCard(med: HydratedMedication, profile: HydratedProfile): View {
        val ctx = requireContext()
        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            cardElevation = dp(1.5f).toFloat()
            radius = dp(12).toFloat()
            // Highlight if DDI
            if (med.ddiCount > 0) {
                strokeWidth = dp(2)
                strokeColor = severityColor(DDIResult.Severity.MAJOR)
            } else {
                strokeWidth = 0
            }
            setCardBackgroundColor(resources.getColor(R.color.jemma_surface, ctx.theme))
            isClickable = true
            isFocusable = true
            setOnClickListener { showMedicationDetailDialog(med, profile) }
        }

        val inner = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        card.addView(inner)

        // Name
        val nameTv = TextView(ctx).apply {
            text = med.displayLocalized
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.jemma_text, ctx.theme))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        inner.addView(nameTv)

        // Dosage/Timing (compact)
        val sub = buildString {
            if (!med.doseValue.isNullOrBlank()) append("${med.doseValue}${med.doseUnit ?: ""}")
            if (!med.timing.isNullOrBlank()) {
                if (this.isNotEmpty()) append(" · ")
                append(med.timing)
            }
        }
        if (sub.isNotEmpty()) {
            val subTv = TextView(ctx).apply {
                text = sub
                textSize = 10.5f
                setTextColor(resources.getColor(R.color.jemma_text_muted, ctx.theme))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            inner.addView(subTv)
        }

        // Badges row
        val badgeRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        inner.addView(badgeRow)

        if (med.ddiCount > 0) {
            badgeRow.addView(createSmallBadge("DDI: ${med.ddiCount}", severityColor(DDIResult.Severity.MAJOR)))
        }
        if (med.foodInteractionCount > 0) {
            badgeRow.addView(createSmallBadge("F: ${med.foodInteractionCount}", resources.getColor(R.color.jemma_accent, ctx.theme)))
        }
        if (med.diseaseInteractionCount > 0) {
            badgeRow.addView(createSmallBadge("P: ${med.diseaseInteractionCount}", resources.getColor(R.color.jemma_accent, ctx.theme)))
        }

        return card
    }

    private fun createSmallBadge(txt: String, color: Int): View {
        val ctx = requireContext()
        return TextView(ctx).apply {
            text = txt
            textSize = 8.5f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(5), dp(1.5f), dp(5), dp(1.5f))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val gd = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(4).toFloat()
            }
            background = gd
            layoutParams = android.widget.LinearLayout.LayoutParams(-2, -2).apply {
                marginEnd = dp(3)
            }
        }
    }

    private fun formatMedicationLine(
        med: HydratedMedication,
        flagged: Boolean,
    ): CharSequence = buildSpannedString {
        append(if (flagged) "⚠️  • " else "•  ")
        append(med.displayLocalized)

        val doseDetails = buildList {
            if (!med.doseValue.isNullOrBlank()) {
                add("${med.doseValue}${med.doseUnit ?: ""}".trim())
            }
            if (!med.timing.isNullOrBlank()) add(med.timing)
        }
        if (doseDetails.isNotEmpty()) {
            append("  ")
            append("(${doseDetails.joinToString(" · ")})")
        }
        if (med.atcCode != null) {
            append("  ")
            append("[ATC ${med.atcCode}]")
        }
    }

    private fun renderConditions(h: HydratedProfile) {
        if (h.conditions.isEmpty()) {
            binding.profileDetailConditionsSection.isVisible = false
            return
        }
        binding.profileDetailConditionsSection.isVisible = true
        binding.profileDetailConditionsTitle.text = getString(
            R.string.profile_detail_conditions_title,
            h.conditions.size,
        )

        val listView = binding.profileDetailConditionsList
        listView.removeAllViews()
        for (cond in h.conditions) {
            val tv = makeListItemTextView()
            tv.text = "•  ${cond.displayLocalized}"
            listView.addView(tv)
        }
    }

    /**
     * 💉 FHIR-native pillar — rendered from the `_j.im` projection carried by
     * the hydrated profile (the Bundle is the source of truth ; the projection
     * is regenerated on every save so no extra I/O is needed here).
     */
    private fun renderImmunizations(h: HydratedProfile) {
        val entries = h.raw.im
        if (entries.isEmpty()) {
            binding.profileDetailImmunizationsSection.isVisible = false
            return
        }
        binding.profileDetailImmunizationsSection.isVisible = true
        binding.profileDetailImmunizationsTitle.text = getString(
            R.string.profile_detail_immunizations_title,
            entries.size,
        )
        val lang = java.util.Locale.getDefault().language.lowercase().take(2)
        val listView = binding.profileDetailImmunizationsList
        listView.removeAllViews()
        val sorted = entries.sortedWith(
            compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.date != null }
                .thenByDescending { it.date ?: "" }
        )
        for (im in sorted) {
            val label = be.heyman.android.jemmapassdemo.pillars.IpsVaccineCatalog.getDisplay(im.c, lang)
                ?: im.displayLabel?.takeIf { it.isNotBlank() }
                ?: im.c.orEmpty()
            val date = im.date?.takeIf { it.isNotBlank() }
                ?: getString(R.string.immunizations_date_unknown)
            val dose = im.doseNumber?.let { " · " + getString(R.string.immunizations_dose_number, it) } ?: ""
            val tv = makeListItemTextView()
            tv.text = "•  $label — $date$dose"
            listView.addView(tv)
        }
    }

    /** 🏥 FHIR-native pillar — rendered from the `_j.pr` projection (Bundle = source of truth). */
    private fun renderProcedures(h: HydratedProfile) {
        val entries = h.raw.pr
        if (entries.isEmpty()) {
            binding.profileDetailProceduresSection.isVisible = false
            return
        }
        binding.profileDetailProceduresSection.isVisible = true
        binding.profileDetailProceduresTitle.text = getString(R.string.profile_detail_procedures_title, entries.size)
        val lang = java.util.Locale.getDefault().language.lowercase().take(2)
        val listView = binding.profileDetailProceduresList
        listView.removeAllViews()
        val sorted = entries.sortedWith(
            compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.date != null }
                .thenByDescending { it.date ?: "" }
        )
        for (pr in sorted) {
            val label = be.heyman.android.jemmapassdemo.pillars.IpsProcedureCatalog.getDisplay(pr.c, lang)
                ?: pr.displayLabel?.takeIf { it.isNotBlank() }
                ?: pr.c.orEmpty()
            val date = pr.date?.takeIf { it.isNotBlank() } ?: getString(R.string.procedures_date_unknown)
            val status = pr.status?.takeIf { it.isNotBlank() && it != "completed" }
                ?.let { be.heyman.android.jemmapassdemo.pillars.IpsProcedureStatusCatalog.byCode(it) }
                ?.let { " · ${it.emoji} ${it.pick(lang)}" } ?: ""
            val tv = makeListItemTextView()
            tv.text = "•  $label — $date$status"
            listView.addView(tv)
        }
    }

    /** ♿ FHIR-native pillar — rendered from the `_j.fs` projection (Bundle = source of truth). */
    private fun renderFunctional(h: HydratedProfile) {
        val entries = h.raw.fs
        if (entries.isEmpty()) {
            binding.profileDetailFunctionalSection.isVisible = false
            return
        }
        binding.profileDetailFunctionalSection.isVisible = true
        binding.profileDetailFunctionalTitle.text = getString(R.string.profile_detail_functional_title, entries.size)
        val lang = java.util.Locale.getDefault().language.lowercase().take(2)
        val listView = binding.profileDetailFunctionalList
        listView.removeAllViews()
        for (fs in entries) {
            val tv = makeListItemTextView()
            val since = fs.date?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
            val base = fs.displayLabel?.takeIf { it.isNotBlank() } ?: fs.c.orEmpty()
            tv.text = "•  $base$since"
            listView.addView(tv)
            val code = fs.c
            if (!code.isNullOrBlank() && lang != "en") {
                viewLifecycleOwner.lifecycleScope.launch {
                    val localized = try {
                        kb.getLocalizedDisplay(code, fs.codeSystem ?: "http://snomed.info/sct", lang)
                    } catch (e: Throwable) { null }
                    if (!localized.isNullOrBlank()) tv.text = "•  $localized$since"
                }
            }
        }
    }

    /** 🤰 FHIR-native pillar — rendered from the `_j.pg` projection (Bundle = source of truth). */
    private fun renderPregnancy(h: HydratedProfile) {
        val lang = java.util.Locale.getDefault().language.lowercase().take(2)
        val obs = h.raw.pg.mapIndexedNotNull { i, e -> be.heyman.android.jemmapassdemo.ips.IpsPregnancyObs.fromJEntry(e, i) }
        if (obs.isEmpty()) {
            binding.profileDetailPregnancySection.isVisible = false
            return
        }
        binding.profileDetailPregnancySection.isVisible = true
        binding.profileDetailPregnancyTitle.text = getString(R.string.profile_detail_pregnancy_title, obs.size)
        val listView = binding.profileDetailPregnancyList
        listView.removeAllViews()
        for (o in obs) {
            val tv = makeListItemTextView()
            tv.text = "•  " + be.heyman.android.jemmapassdemo.pillars.IpsPregnancyCatalog.format(o, lang)
            listView.addView(tv)
        }
    }

    /** 📜 FHIR-native pillar — rendered from the `_j.ph` projection (Bundle = source of truth). */
    private fun renderPastProblems(h: HydratedProfile) {
        val entries = h.raw.ph
        if (entries.isEmpty()) {
            binding.profileDetailPastProblemsSection.isVisible = false
            return
        }
        binding.profileDetailPastProblemsSection.isVisible = true
        binding.profileDetailPastProblemsTitle.text = getString(R.string.profile_detail_past_problems_title, entries.size)
        val lang = java.util.Locale.getDefault().language.lowercase().take(2)
        val listView = binding.profileDetailPastProblemsList
        listView.removeAllViews()
        val sorted = entries.sortedWith(
            compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.date != null }
                .thenByDescending { it.date ?: "" }
        )
        for (ph in sorted) {
            val tv = makeListItemTextView()
            val period = be.heyman.android.jemmapassdemo.ui.profile.pastproblems.PastProblemsAdapter.period(ph.date, ph.abatement)
                ?.let { " — $it" } ?: ""
            val base = ph.displayLabel?.takeIf { it.isNotBlank() } ?: ph.c.orEmpty()
            tv.text = "•  $base$period"
            listView.addView(tv)
            val code = ph.c
            if (!code.isNullOrBlank() && lang != "en") {
                viewLifecycleOwner.lifecycleScope.launch {
                    val localized = try {
                        kb.getLocalizedDisplay(code, ph.codeSystem ?: "http://snomed.info/sct", lang)
                    } catch (e: Throwable) { null }
                    if (!localized.isNullOrBlank()) tv.text = "•  $localized$period"
                }
            }
        }
    }

    /** 🧪 FHIR-native pillar — rendered from the `_j.rs` projection (Bundle = source of truth). */
    private fun renderResults(h: HydratedProfile) {
        val entries = h.raw.rs
        if (entries.isEmpty()) {
            binding.profileDetailResultsSection.isVisible = false
            return
        }
        binding.profileDetailResultsSection.isVisible = true
        binding.profileDetailResultsTitle.text = getString(R.string.profile_detail_results_title, entries.size)
        val lang = java.util.Locale.getDefault().language.lowercase().take(2)
        val listView = binding.profileDetailResultsList
        listView.removeAllViews()
        val sorted = entries.sortedWith(
            compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.date != null }
                .thenByDescending { it.date ?: "" }
        )
        for (rs in sorted) {
            val label = be.heyman.android.jemmapassdemo.pillars.IpsResultCatalog.getDisplay(rs.c, lang)
                ?: rs.displayLabel?.takeIf { it.isNotBlank() }
                ?: rs.c.orEmpty()
            val value = be.heyman.android.jemmapassdemo.ips.IpsBloodGroup.labelFromSnomed(rs.valueCode)
                ?: listOfNotNull(rs.value, rs.unit).joinToString(" ")
            val flag = be.heyman.android.jemmapassdemo.pillars.IpsResultInterpretationCatalog.byCode(rs.interpretation)
                ?.takeIf { it.code != "N" }?.let { " ${it.emoji}" } ?: ""
            val date = rs.date?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
            val tv = makeListItemTextView()
            tv.text = "•  $label: $value$flag$date"
            listView.addView(tv)
        }
    }

    /** 📟 FHIR-native pillar — rendered from the `_j.dv` projection (Bundle = source of truth). */
    private fun renderDevices(h: HydratedProfile) {
        val entries = h.raw.dv
        if (entries.isEmpty()) {
            binding.profileDetailDevicesSection.isVisible = false
            return
        }
        binding.profileDetailDevicesSection.isVisible = true
        binding.profileDetailDevicesTitle.text = getString(R.string.profile_detail_devices_title, entries.size)
        val lang = java.util.Locale.getDefault().language.lowercase().take(2)
        val listView = binding.profileDetailDevicesList
        listView.removeAllViews()
        val sorted = entries.sortedWith(
            compareByDescending<be.heyman.android.jemmapassdemo.qr.JEntryGeneric> { it.status.isNullOrBlank() || it.status == "active" }
                .thenByDescending { it.date ?: "" }
        )
        for (dv in sorted) {
            val resId = dv.c?.let { c ->
                val system = dv.codeSystem ?: be.heyman.android.jemmapassdemo.pillars.IpsDeviceCatalog.CODE_SYSTEM
                be.heyman.android.jemmapassdemo.qr.codeLabelResourceName(system, c)
                    ?.let { name -> resources.getIdentifier(name, "string", requireContext().packageName) }
            } ?: 0
            val resLabel = if (resId != 0) getString(resId) else null
            val label = resLabel
                ?: be.heyman.android.jemmapassdemo.pillars.IpsDeviceCatalog.getDisplay(dv.c, lang)
                ?: dv.displayLabel?.takeIf { it.isNotBlank() }
                ?: dv.c.orEmpty()
            val since = dv.date?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
            val status = dv.status?.takeIf { it.isNotBlank() && it != "active" }
                ?.let { be.heyman.android.jemmapassdemo.pillars.IpsDeviceStatusCatalog.byCode(it) }
                ?.let { " · ${it.emoji} ${it.pick(lang)}" } ?: ""
            val tv = makeListItemTextView()
            tv.text = "•  $label$since$status"
            listView.addView(tv)
        }
    }

    private fun renderAlerts(h: HydratedProfile) {
        if (!h.hasAlerts) {
            binding.profileDetailAlertsSection.isVisible = false
            return
        }
        binding.profileDetailAlertsSection.isVisible = true

        val listView = binding.profileDetailAlertsList
        listView.removeAllViews()

        // Subtitle hint : "Tap an alert for details"
        val hint = TextView(requireContext()).apply {
            text = getString(R.string.profile_detail_alerts_hint)
            setTextColor(requireContext().getColor(R.color.jemma_text_muted))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.profile_detail_item_h_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.profile_detail_item_h_padding),
                resources.getDimensionPixelSize(R.dimen.profile_detail_item_v_padding),
            )
        }
        listView.addView(hint)

        for (ddi in h.ddiAlerts) {
            val tv = makeListItemTextView(clickable = true)
            tv.text = formatDdiAlertSummary(ddi)
            tv.setOnClickListener { showDdiBottomSheet(ddi) }
            listView.addView(tv)
        }
        for (allergyAlert in h.allergyAlerts) {
            val tv = makeListItemTextView(clickable = true)
            tv.text = formatAllergyAlertSummary(allergyAlert)
            tv.setOnClickListener { showAllergyAlertBottomSheet(allergyAlert) }
            listView.addView(tv)
        }
        for (drugDisease in h.drugDiseaseAlerts) {
            val tv = makeListItemTextView(clickable = true)
            tv.text = formatDrugDiseaseAlertSummary(drugDisease)
            tv.setOnClickListener { showDrugDiseaseBottomSheet(drugDisease) }
            listView.addView(tv)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Alert formatters & detail dialogs
    // ──────────────────────────────────────────────────────────────────────

    private fun formatMechanismLabel(mech: String?): String? {
        if (mech.isNullOrBlank()) return null
        val lang = Locale.getDefault().language
        return when (mech) {
            "PD_Synergistic" -> {
                if (lang == "fr") "Synergie (Effets cumulatifs)"
                else "Synergism (Additive effects)"
            }
            "PD_Antagonistic" -> {
                if (lang == "fr") "Antagonisme (Effets opposés)"
                else "Antagonism (Opposing effects)"
            }
            "PK_Metabolism_CYP" -> {
                if (lang == "fr") "Métabolisme (Voie CYP)"
                else "Metabolism (CYP pathway)"
            }
            "PK_Absorption" -> {
                if (lang == "fr") "Altération de l'absorption"
                else "Altered absorption"
            }
            "PK_Excretion" -> {
                if (lang == "fr") "Altération de l'élimination"
                else "Altered excretion"
            }
            else -> mech
        }
    }

    private fun formatDdiAlertSummary(ddi: DdiAlert): CharSequence = buildSpannedString {
        val color = severityColor(ddi.severity)
        color(color) {
            append("⚠ ")
            append(ddi.severity.label.uppercase())
        }
        append("  ")
        append("${ddi.medicationA.displayLocalized}  ×  ${ddi.medicationB.displayLocalized}")
        append("   ")
        color(color) { append("›") }
        val mechLabel = formatMechanismLabel(ddi.mechanism)
        if (!mechLabel.isNullOrBlank()) {
            append("\n    ")
            append(mechLabel)
        }
    }

    private fun formatAllergyAlertSummary(a: AllergyAlert): CharSequence = buildSpannedString {
        color(severityColor(DDIResult.Severity.MAJOR)) {
            append("⚠ ALLERGY")
        }
        append("  ")
        append("${a.allergyDisplay}  →  ${a.medication.displayLocalized}")
        append("   ")
        color(severityColor(DDIResult.Severity.MAJOR)) { append("›") }
        append("\n    ")
        append(getString(R.string.profile_detail_allergy_match_label, a.matchedOn))
    }

    private fun formatDrugDiseaseAlertSummary(d: DrugDiseaseAlert): CharSequence =
        buildSpannedString {
            color(severityColor(d.severity)) {
                append("⚠ ")
                append(d.severity.label.uppercase())
            }
            append("  ")
            append("${d.medication.displayLocalized}  +  ${d.condition.displayLocalized}")
            append("   ")
            color(severityColor(d.severity)) { append("›") }
        }

    private fun showDdiBottomSheet(ddi: DdiAlert) {
        val msg = buildString {
            append("📋 ${getString(R.string.profile_detail_ddi_severity_label)}: ")
            append(ddi.severity.label)
            append("\n\n")
            val mechLabel = formatMechanismLabel(ddi.mechanism)
            if (!mechLabel.isNullOrBlank()) {
                append("⚙ ${getString(R.string.profile_detail_ddi_mechanism_label)}: ")
                append(mechLabel)
                append("\n\n")
            }
            if (!ddi.description.isNullOrBlank()) {
                append("📖 ${getString(R.string.profile_detail_ddi_description_label)}\n")
                append(ddi.description)
                append("\n\n")
            }
            if (!ddi.management.isNullOrBlank()) {
                append("💉 ${getString(R.string.profile_detail_ddi_management_label)}\n")
                append(ddi.management)
                append("\n")
            }
            if (!ddi.alternativeAtc.isNullOrBlank()) {
                append("\n🔄 ${getString(R.string.profile_detail_ddi_alternative_label)}: ")
                append(ddi.alternativeAtc)
            }
        }
        showAlertDetailDialog(
            title = "${ddi.medicationA.displayLocalized} × ${ddi.medicationB.displayLocalized}",
            body = msg,
            ctx = AlertVulgariseContext(
                kind = "drug_drug_interaction",
                subjects = listOf(ddi.medicationA.displayLocalized, ddi.medicationB.displayLocalized),
                severity = ddi.severity.label,
                mechanism = ddi.mechanism,
                description = ddi.description,
                cacheKey = "DDI|${listOf<String>(ddi.medicationA.atcCode ?: "", ddi.medicationB.atcCode ?: "").sorted().joinToString("|")}",
                alternative = ddi.alternativeAtc,
            ),
        )
    }

    private fun showAllergyAlertBottomSheet(a: AllergyAlert) {
        val msg = buildString {
            append(getString(R.string.profile_detail_allergy_alert_msg, a.allergyDisplay, a.medication.displayLocalized))
            append("\n\n")
            append(getString(R.string.profile_detail_allergy_match_label, a.matchedOn))
        }
        showAlertDetailDialog(
            title = getString(R.string.profile_detail_allergy_alert_title),
            body = msg,
            ctx = AlertVulgariseContext(
                kind = "allergy",
                subjects = listOf(a.allergyDisplay, a.medication.displayLocalized),
                severity = a.criticality.name,
                mechanism = "Cross-reactivity / ATC class match (${a.matchedOn})",
                description = "Patient is allergic to ${a.allergyDisplay}; this drug " +
                    "(${a.medication.displayLocalized}) belongs to a class that may " +
                    "trigger the same reaction.",
                cacheKey = "AL|${a.allergyCode}|${a.medication.atcCode ?: ""}",
            ),
        )
    }

    private fun showDrugDiseaseBottomSheet(d: DrugDiseaseAlert) {
        val msg = buildString {
            append(getString(R.string.profile_detail_ddsi_severity_label, d.severity.label))
            append("\n\n")
            append(d.medication.displayLocalized)
            append(" + ")
            append(d.condition.displayLocalized)
            append("\n\n")
            d.description?.let {
                append("📖 ")
                append(it)
                append("\n\n")
            }
            d.management?.let {
                append("💉 ")
                append(it)
            }
        }
        showAlertDetailDialog(
            title = getString(R.string.profile_detail_ddsi_title),
            body = msg,
            ctx = AlertVulgariseContext(
                kind = "drug_disease",
                subjects = listOf(d.medication.displayLocalized, d.condition.displayLocalized),
                severity = d.severity.label,
                mechanism = "Contraindication for condition ${d.condition.displayLocalized}",
                description = d.description,
                cacheKey = "DDSI|${d.medication.atcCode ?: ""}|${d.condition.raw.c ?: ""}",
            ),
        )
    }

    /**
     * 🆕 Lot 14.5c8 — Affiche un dialog d'alerte avec :
     *   • Le corps de l'alerte (texte clinique)
     *   • Un bouton "✨ Expliquer simplement" qui appelle Gemma
     *   • Une zone où la vulgarisation stream live
     *   • Après réussite : bouton devient "🔄 Relancer"
     *
     * Remplace le simple .setMessage() par un custom ScrollView+LinearLayout
     * pour pouvoir héberger le bouton et le TextView de vulgarisation.
     */
    private fun showAlertDetailDialog(
        title: String,
        body: String,
        ctx: AlertVulgariseContext,
    ) {
        val context = requireContext()

        val column = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        val vulgariseBtn = MaterialButton(
            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            this.text = getString(R.string.assistant_alert_btn_vulgarise)
            textSize = 12f
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
        }
        val vulgariseHeaderRow = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        val jemmaAvatar = android.widget.ImageView(context).apply {
            setImageResource(R.drawable.jemmapass_icon)
            val size = dp(20)
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(6)
            }
        }
        vulgariseHeaderRow.addView(jemmaAvatar)
        val vulgariseHeader = TextView(context).apply {
            this.text = getString(R.string.assistant_alert_vulgarise_header)
            textSize = 12f
            setTextColor(resources.getColor(R.color.jemma_accent, context.theme))
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
        }
        val playBtn = MaterialButton(
            context, null, com.google.android.material.R.attr.materialIconButtonStyle
        ).apply {
            setIconResource(R.drawable.ic_play_arrow_24)
            setIconTintResource(R.color.jemma_accent)
            iconSize = dp(20)
            setPadding(0, 0, 0, 0)
            minWidth = dp(32)
            minHeight = dp(32)
            insetTop = 0
            insetBottom = 0
        }
        vulgariseHeaderRow.addView(vulgariseHeader, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        vulgariseHeaderRow.addView(playBtn)

        val vulgariseText = TextView(context).apply {
            visibility = View.GONE
            textSize = 13.5f
            setTextColor(resources.getColor(R.color.jemma_electric_blue, context.theme))
            setLineSpacing(0f, 1.3f)
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.ITALIC)
        }

        column.addView(vulgariseBtn, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) })
        column.addView(vulgariseHeaderRow)
        column.addView(vulgariseText)

        // Controller logic for Play/Stop
        playBtn.setOnClickListener {
            if (tts.isSpeaking.value) {
                tts.stopAll()
            } else {
                val textToSpeak = vulgariseText.text.toString()
                if (textToSpeak.isNotEmpty()) {
                    tts.speakOneShot(textToSpeak, Locale.getDefault().language)
                }
            }
        }

        // Observer for icon state
        viewLifecycleOwner.lifecycleScope.launch {
            tts.isSpeaking.collect { speaking ->
                if (speaking) {
                    playBtn.setIconResource(R.drawable.ic_stop_24)
                } else {
                    playBtn.setIconResource(R.drawable.ic_play_arrow_24)
                }
            }
        }

        // Corps clinique original.
        column.addView(TextView(context).apply {
            this.text = body
            textSize = 14f
            setTextColor(resources.getColor(R.color.jemma_text, context.theme))
            setLineSpacing(0f, 1.2f)
            setPadding(0, dp(12), 0, 0)
        })

        // 🆕 Lot 14.5c16 — Check GLOBAL cache before showing.
        viewLifecycleOwner.lifecycleScope.launch {
            val lang = Locale.getDefault().language
            val cached = vulgariseRepo.get(ctx.cacheKey, lang)
            if (cached != null) {
                Log.i(TAG, "🎯 [GLOBAL CACHE] vulgarisation found for ${ctx.cacheKey} in $lang")
                vulgariseHeaderRow.visibility = View.VISIBLE
                vulgariseText.visibility = View.VISIBLE
                vulgariseText.text = cached
                vulgariseBtn.text = getString(R.string.assistant_alert_btn_relancer)
            }
        }

        vulgariseBtn.setOnClickListener {
            vulgariseBtn.isEnabled = false
            vulgariseBtn.text = getString(R.string.assistant_alert_btn_vulgarising)
            vulgariseHeaderRow.visibility = View.VISIBLE
            vulgariseText.visibility = View.VISIBLE
            vulgariseText.text = ""
            launchVulgariseInDialog(ctx, vulgariseText, vulgariseBtn, vulgariseHeaderRow)
        }

        val scroll = android.widget.ScrollView(context).apply {
            addView(column)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.profile_detail_dialog_close, null)
            .show()

        dialog.setOnDismissListener {
            tts.stopAll()
        }
    }

    /** Pendant du launchVulgarise côté multi-preview. Stream tokens dans
     *  le TextView du dialog ; vit dans le scope du fragment (pas du
     *  dialog) pour pouvoir survivre si l'user ferme le dialog par
     *  inadvertance pendant l'inférence. */
    private fun launchVulgariseInDialog(
        ctx: AlertVulgariseContext,
        target: TextView,
        button: MaterialButton,
        headerRow: View,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val anchor = view ?: return@launch
            val langTag = Locale.getDefault().language

            // 1. Check GLOBAL cache first (fast path)
            button.isEnabled = false
            button.text = getString(R.string.assistant_alert_btn_vulgarising)
            val cached = vulgariseRepo.get(ctx.cacheKey, langTag)
            if (cached != null) {
                headerRow.visibility = View.VISIBLE
                target.visibility = View.VISIBLE
                target.text = cached
                button.isEnabled = true
                button.text = getString(R.string.assistant_alert_btn_relancer)
                return@launch
            }

            // 2. Slow path: Gemma inference
            val ttsStream = tts.startStream(Locale.getDefault().toLanguageTag())
            val appender = ThrottledTextAppender(anchor, target, ttsStream = ttsStream)
            try {
                val tStart = System.currentTimeMillis()
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✨ vulgarise (profile detail) · " +
                        "type=${ctx.kind} · lang=$langTag · subjects=${ctx.subjects}",
                )
                // On s'assure que Gemma tourne bien hors du main thread même si son API suspend
                val reply = withContext(Dispatchers.Default) {
                    gemma.ask(
                        prompt = ctx.toUserPrompt(),
                        systemInstruction = buildVulgariseSystemPrompt(),
                        onPartial = { partial -> appender.append(partial) },
                    )
                }
                appender.cancelUI()
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✨ vulgarise done (profile detail) · " +
                        "${System.currentTimeMillis() - tStart}ms",
                )
                if (isAdded && target.isAttachedToWindow) {
                    target.text = reply
                }
                appender.end()

                // Save to GLOBAL cache.
                vulgariseRepo.save(
                    key = ctx.cacheKey,
                    lang = langTag,
                    text = reply
                )

                if (isAdded && button.isAttachedToWindow) {
                    button.isEnabled = true
                    button.text = getString(R.string.assistant_alert_btn_relancer)
                }
            } catch (e: Throwable) {
                appender.cancel()
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ vulgarise failed (profile detail)", e)
                if (isAdded && target.isAttachedToWindow) {
                    target.text = getString(R.string.assistant_alert_vulgarise_failed)
                    button.isEnabled = true
                    button.text = getString(R.string.assistant_alert_btn_vulgarise)
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Misc helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun severityColor(sev: DDIResult.Severity): Int {
        val ctx = requireContext()
        return when (sev) {
            DDIResult.Severity.MAJOR -> ctx.getColor(android.R.color.holo_red_dark)
            DDIResult.Severity.MODERATE -> ctx.getColor(android.R.color.holo_orange_dark)
            DDIResult.Severity.MINOR -> ctx.getColor(android.R.color.holo_orange_light)
            DDIResult.Severity.NONE -> ctx.getColor(android.R.color.holo_green_dark)
            DDIResult.Severity.UNKNOWN -> ctx.getColor(android.R.color.darker_gray)
        }
    }

    private fun makeListItemTextView(clickable: Boolean = false): TextView {
        val ctx = requireContext()
        return TextView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(
                resources.getDimensionPixelSize(R.dimen.profile_detail_item_h_padding),
                resources.getDimensionPixelSize(R.dimen.profile_detail_item_v_padding),
                resources.getDimensionPixelSize(R.dimen.profile_detail_item_h_padding),
                resources.getDimensionPixelSize(R.dimen.profile_detail_item_v_padding),
            )
            textSize = 15f
            setTextColor(ctx.getColor(R.color.jemma_text_primary))
            if (clickable) {
                isClickable = true
                isFocusable = true
                val tv = android.util.TypedValue()
                ctx.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    tv,
                    true,
                )
                setBackgroundResource(tv.resourceId)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 🆕 v2.6.0c L5 — Pillars grid (18 tiles)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Bind les 18 tiles de la grille piliers. Chaque tile est un layout
     * `view_pillar_tile` (active) ou `view_pillar_tile_stub` (info-only)
     * inclus avec un ID `profile_detail_tile_<key>`.
     *
     * Pour chaque tile :
     *   1. Set emoji + label depuis PillarRegistry
     *   2. Set count badge depuis le profil hydraté (allergies/meds count)
     *   3. Wire onClick → navigation vers le fragment d'édition
     *
     * Les 4 actifs (patient/contacts/allergies/medications) routent vers
     * leur fragment d'édition dédié. Les 14 restants routent vers
     * dest_pillar_stub avec l'arg pillarKey.
     */
    private fun renderPillars(h: HydratedProfile) {
        val view = view ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] 🩺 renderPillars · view null · skip")
            return
        }
        val ctx = requireContext()
        val tStart = System.currentTimeMillis()
        Log.i(TAG, "[t=$tStart] 🩺 renderPillars · START · profileId=$currentProfileId")

        // Tile id (R.id) ↔ pillar key. Order = visual order in the grid.
        // 🆕 Lot 14.5c12 — Row 1 réordonnée pour la démo :
        //   patient · allergies · médicaments · contacts (contacts en stub)
        val tileBindings = listOf(
            // Active pillars (row 1) — patient/allergies/medications utilisent view_pillar_tile
            R.id.profile_detail_tile_patient to "patient",
            R.id.profile_detail_tile_allergies to "allergies",
            R.id.profile_detail_tile_medications to "medications",
            // Contacts est désormais en stub layout pour la démo (cadenas + alpha + tap = fiche FHIR).
            R.id.profile_detail_tile_contacts to "contacts",
            // Stubs (rows 2-5) — utilisent view_pillar_tile_stub
            R.id.profile_detail_tile_conditions to "conditions",
            R.id.profile_detail_tile_pastproblems to "pastProblems",
            R.id.profile_detail_tile_immunizations to "immunizations",
            R.id.profile_detail_tile_procedures to "procedures",
            R.id.profile_detail_tile_devices to "devices",
            R.id.profile_detail_tile_functional to "functional",
            R.id.profile_detail_tile_pregnancy to "pregnancy",
            R.id.profile_detail_tile_results to "results",
            R.id.profile_detail_tile_advancedirectives to "advanceDirectives",
            R.id.profile_detail_tile_consents to "consents",
            R.id.profile_detail_tile_goals to "goals",
            R.id.profile_detail_tile_encounters to "encounters",
            R.id.profile_detail_tile_occupational to "occupational",
            R.id.profile_detail_tile_providers to "providers",
        )

        // Counts for the 4 active pillars — derived from the hydrated profile.
        val counts: Map<String, Int> = mapOf(
            "patient" to (if (h.raw.p != null) 1 else 0),
            "contacts" to (h.raw.p?.ct?.size ?: 0),
            "allergies" to h.allergies.size,
            "medications" to h.medications.size,
            // Stubs — counts from raw lists (not hydrated)
            "conditions" to h.raw.cn.size,
            "pastProblems" to h.raw.ph.size,
            "immunizations" to h.raw.im.size,
            "procedures" to h.raw.pr.size,
            "devices" to h.raw.dv.size,
            "functional" to h.raw.fs.size,
            "pregnancy" to h.raw.pg.size,
            "results" to h.raw.rs.size,
            "advanceDirectives" to h.raw.ad.size,
            "consents" to h.raw.cs.size,
            "goals" to h.raw.gl.size,
            "encounters" to h.raw.en.size,
            "occupational" to h.raw.oc.size,
            "providers" to h.raw.pv.size,
        )
        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🩺 counts computed · " +
            "patient=${counts["patient"]} contacts=${counts["contacts"]} " +
            "allergies=${counts["allergies"]} medications=${counts["medications"]} " +
            "conditions=${counts["conditions"]} immunizations=${counts["immunizations"]} " +
            "procedures=${counts["procedures"]} devices=${counts["devices"]} " +
            "results=${counts["results"]} providers=${counts["providers"]}")

        var boundActive = 0
        var boundStub = 0
        var missing = 0

        tileBindings.forEach { (tileId, pillarKey) ->
            val pillar = PillarRegistry.get(pillarKey) ?: run {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ unknown pillar key='$pillarKey' · " +
                    "tileId=${ctx.resources.getResourceEntryName(tileId)}")
                missing++
                return@forEach
            }
            val tileRoot = view.findViewById<View>(tileId) ?: run {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ tile view not found · " +
                    "tileId=${ctx.resources.getResourceEntryName(tileId)} · pillar=$pillarKey")
                missing++
                return@forEach
            }

            // ─── Bind emoji + label — CRITICAL : les 2 tile layouts ont des IDs
            //     DIFFÉRENTS car ils sont définis dans 2 fichiers view_pillar_tile.xml
            //     vs view_pillar_tile_stub.xml. Si on cherche le mauvais ID, le texte
            //     ne sera jamais set et les stubs apparaissent vides → impression
            //     de "tiles cassés" !
            //
            //     Stratégie : on essaie le tile_emoji actif d'abord, puis le stub.
            val labelText = getString(pillar.titleRes)
            val emojiText = pillar.emoji

            val emojiActive = tileRoot.findViewById<TextView>(R.id.pillar_tile_emoji)
            val emojiStub = tileRoot.findViewById<TextView>(R.id.pillar_tile_stub_emoji)
            val labelActive = tileRoot.findViewById<TextView>(R.id.pillar_tile_label)
            val labelStub = tileRoot.findViewById<TextView>(R.id.pillar_tile_stub_label)

            val emojiView = emojiActive ?: emojiStub
            val labelView = labelActive ?: labelStub
            val isStubLayout = (emojiStub != null)

            if (emojiView == null || labelView == null) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ no emoji/label TextView found · " +
                    "pillar=$pillarKey · emojiActive=${emojiActive != null} · " +
                    "emojiStub=${emojiStub != null} · labelActive=${labelActive != null} · " +
                    "labelStub=${labelStub != null}")
                missing++
                return@forEach
            }
            emojiView.text = emojiText
            labelView.text = labelText

            // Bind count badge — only available on the ACTIVE tile layout (stubs
            // show a lock icon instead of count). 0 → hide.
            val countView = tileRoot.findViewById<TextView>(R.id.pillar_tile_count)
            val count = counts[pillarKey] ?: 0
            if (countView != null) {
                if (count > 0) {
                    countView.text = count.toString()
                    countView.visibility = View.VISIBLE
                } else {
                    countView.visibility = View.GONE
                }
            }

            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🎴 bound tile · key=$pillarKey · " +
                "isActive=${pillar.isActive} · layout=${if (isStubLayout) "stub" else "active"} · " +
                "emoji='$emojiText' · label='$labelText' · count=$count")

            // Wire tap — works regardless of layout (both have clickable=true)
            tileRoot.setOnClickListener {
                val pid = currentProfileId
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👆 pillar TAP · key=$pillarKey · " +
                    "isActive=${pillar.isActive} · profileId=$pid")

                if (pid.isNullOrBlank()) {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ tap with no profileId · abort nav")
                    Toast.makeText(ctx, R.string.profile_detail_load_failed, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (pillar.isActive) {
                    val (action, includeProfileId) = when (pillarKey) {
                        "patient" -> R.id.action_detail_to_perso to true
                        "contacts" -> R.id.action_detail_to_contacts to true
                        "allergies" -> R.id.action_detail_to_allergies to true
                        "medications" -> R.id.action_detail_to_medications to true
                        // 💉 FHIR-native pillar (feat/ips-18-pillars-cleanup)
                        "immunizations" -> R.id.action_detail_to_immunizations to true
                        // 🏥 📟 FHIR-native pillars (sprint 2)
                        "procedures" -> R.id.action_detail_to_procedures to true
                        "devices" -> R.id.action_detail_to_devices to true
                        "results" -> R.id.action_detail_to_results to true
                        // 📜 FHIR-native pillar (sprint 4)
                        "pastProblems" -> R.id.action_detail_to_past_problems to true
                        // 🩺 FHIR-native problem list (sprint 5)
                        "conditions" -> R.id.action_detail_to_problems to true
                        // 🤰 FHIR-native pregnancy history (sprint 6)
                        "pregnancy" -> R.id.action_detail_to_pregnancy to true
                        // ♿ FHIR-native functional status (sprint 7)
                        "functional" -> R.id.action_detail_to_functional to true
                        else -> R.id.action_detail_to_pillar_stub to false
                    }
                    val args = if (includeProfileId) {
                        bundleOf(
                            "pillarKey" to pillarKey,
                            "profileId" to pid,
                        )
                    } else {
                        bundleOf("pillarKey" to pillarKey)
                    }
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚦 navigate · " +
                        "action=${ctx.resources.getResourceEntryName(action)} · " +
                        "args=$args")
                    try {
                        findNavController().navigate(action, args)
                    } catch (e: Exception) {
                        Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ navigate failed · " +
                            "action=${ctx.resources.getResourceEntryName(action)} · ${e.message}", e)
                    }
                } else {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚦 navigate STUB · " +
                        "key=$pillarKey → dest_pillar_stub")
                    try {
                        findNavController().navigate(
                            R.id.action_detail_to_pillar_stub,
                            bundleOf("pillarKey" to pillarKey),
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ navigate stub failed · " +
                            "${e.message}", e)
                    }
                }
            }

            if (pillar.isActive) boundActive++ else boundStub++
        }
        val elapsed = System.currentTimeMillis() - tStart
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩺 renderPillars · END · " +
            "active=$boundActive/${be.heyman.android.jemmapassdemo.pillars.PillarRegistry.ALL.count { it.isActive }} · " +
            "stub=$boundStub/${be.heyman.android.jemmapassdemo.pillars.PillarRegistry.ALL.count { !it.isActive }} · " +
            "missing=$missing/${be.heyman.android.jemmapassdemo.pillars.PillarRegistry.ALL.size} · ${elapsed}ms")
        if (missing > 0) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ $missing tile(s) failed to bind · " +
                "vérifier que les ids profile_detail_tile_<key> existent dans fragment_profile_detail.xml")
        }
    }

    private fun showMedicationDetailDialog(med: HydratedMedication, profile: HydratedProfile) {
        val myContext = requireContext()
        val atc = med.atcCode ?: med.resolvedConcept?.atcCode ?: med.raw.c ?: ""

        val root = android.widget.LinearLayout(myContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = dp(20)
            setPadding(p, p, p, p)
        }

        val titleTv = TextView(myContext).apply {
            text = med.displayLocalized
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.jemma_text, myContext.theme))
        }
        root.addView(titleTv)

        if (atc.isNotEmpty()) {
            val atcTv = TextView(myContext).apply {
                text = "ATC: $atc"
                textSize = 14f
                setTextColor(resources.getColor(R.color.jemma_accent, myContext.theme))
                setPadding(0, 0, 0, dp(12))
            }
            root.addView(atcTv)
        }

        val infoScroll = android.widget.ScrollView(myContext).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2)
        }
        val infoContainer = android.widget.LinearLayout(myContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        infoScroll.addView(infoContainer)
        root.addView(infoScroll)

        val vulgariseBtn = MaterialButton(myContext, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.assistant_alert_btn_vulgarise)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        infoContainer.addView(vulgariseBtn, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val vulgariseHeaderRow = android.widget.LinearLayout(myContext).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(12), 0, 0)
        }
        
        // Circular Avatar Badge matching Jemma live verification style
        val avatarCard = com.google.android.material.card.MaterialCardView(myContext).apply {
            radius = dp(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#1A0D9688"))
            strokeColor = android.graphics.Color.parseColor("#800D9688")
            strokeWidth = dp(1)
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginEnd = dp(8)
            }
        }
        val jemmaAvatar = android.widget.ImageView(myContext).apply {
            setImageResource(R.drawable.jemmapass_icon)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
        }
        avatarCard.addView(jemmaAvatar)
        vulgariseHeaderRow.addView(avatarCard)
        val vulgariseHeader = TextView(myContext).apply {
            text = getString(R.string.assistant_alert_vulgarise_header)
            textSize = 12f
            setTextColor(resources.getColor(R.color.jemma_accent, myContext.theme))
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
        }
        val playBtn = MaterialButton(myContext, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            setIconResource(R.drawable.ic_play_arrow_24)
            setIconTintResource(R.color.jemma_accent)
            iconSize = dp(18)
            setPadding(0, 0, 0, 0)
            minWidth = dp(28)
            minHeight = dp(28)
            insetTop = 0
            insetBottom = 0
        }
        vulgariseHeaderRow.addView(vulgariseHeader, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        vulgariseHeaderRow.addView(playBtn)
        infoContainer.addView(vulgariseHeaderRow)

        val vulgariseText = TextView(myContext).apply {
            visibility = View.GONE
            textSize = 13.5f
            setTextColor(resources.getColor(R.color.jemma_electric_blue, myContext.theme))
            setLineSpacing(0f, 1.3f)
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.ITALIC)
        }
        infoContainer.addView(vulgariseText)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(myContext)
            .setView(root)
            .setPositiveButton(R.string.profile_detail_dialog_close, null)
            .show()

        dialog.setOnDismissListener { tts.stopAll() }

        // Load data from KB
        viewLifecycleOwner.lifecycleScope.launch {
            val detailed = kb.getDetailedDrugInfo(kbManager, atc)
            if (detailed != null) {
                renderDetailedInfo(infoContainer, detailed, profile, med)
                
                // Check cache
                val vCtx = MedicationVulgariseContext(
                    atcCode = detailed.atcCode,
                    name = detailed.canonicalName,
                    description = detailed.description,
                    emlStatus = detailed.whoEmlStatus,
                    awareCategory = detailed.whoAwareCategory
                )
                
                val lang = Locale.getDefault().language
                val cached = vulgariseRepo.get(vCtx.cacheKey, lang)
                if (cached != null) {
                    vulgariseHeaderRow.visibility = View.VISIBLE
                    vulgariseText.visibility = View.VISIBLE
                    vulgariseText.text = cached
                    vulgariseBtn.text = getString(R.string.assistant_alert_btn_relancer)
                }

                vulgariseBtn.setOnClickListener {
                    vulgariseBtn.isEnabled = false
                    vulgariseBtn.text = getString(R.string.assistant_alert_btn_vulgarising)
                    vulgariseHeaderRow.visibility = View.VISIBLE
                    vulgariseText.visibility = View.VISIBLE
                    vulgariseText.text = ""
                    launchMedicationVulgarise(vCtx, vulgariseText, vulgariseBtn, vulgariseHeaderRow)
                }

                playBtn.setOnClickListener {
                    if (tts.isSpeaking.value) {
                        tts.stopAll()
                    } else {
                        val txt = vulgariseText.text.toString()
                        if (txt.isNotEmpty()) tts.speakOneShot(txt, lang)
                    }
                }
            } else {
                val tv = TextView(myContext).apply {
                    text = "No additional information found in Knowledge Base."
                    textSize = 13f
                    setTextColor(resources.getColor(R.color.jemma_text_muted, myContext.theme))
                }
                infoContainer.addView(tv)
                vulgariseBtn.isVisible = false
            }
        }

        // Observer for TTS icon
        viewLifecycleOwner.lifecycleScope.launch {
            tts.isSpeaking.collect { speaking ->
                playBtn.setIconResource(if (speaking) R.drawable.ic_stop_24 else R.drawable.ic_play_arrow_24)
            }
        }
    }

    private fun renderDetailedInfo(
        container: android.widget.LinearLayout,
        info: DetailedDrugInfo,
        profile: HydratedProfile,
        currentMed: HydratedMedication
    ) {
        val ctx = container.context
        
        // --- 1. Active Alerts in this Profile ---
        val myDdis = profile.ddiAlerts.filter { it.medicationA === currentMed || it.medicationB === currentMed }
        val myAllergies = profile.allergyAlerts.filter { it.medication === currentMed }
        val myDiseases = profile.drugDiseaseAlerts.filter { it.medication === currentMed }

        if (myDdis.isNotEmpty() || myAllergies.isNotEmpty() || myDiseases.isNotEmpty()) {
            val alertTitle = TextView(ctx).apply {
                text = "⚠️ ACTIVE ALERTS FOR THIS PATIENT:"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(severityColor(DDIResult.Severity.MAJOR))
                setPadding(0, dp(4), 0, dp(4))
            }
            container.addView(alertTitle)

            for (ddi in myDdis) {
                val other = if (ddi.medicationA === currentMed) ddi.medicationB else ddi.medicationA
                val tv = TextView(ctx).apply {
                    text = "• Interaction with ${other.displayLocalized} (${ddi.severity.label})"
                    textSize = 12f
                    setTextColor(severityColor(ddi.severity))
                    setPadding(dp(8), 0, 0, dp(2))
                }
                container.addView(tv)
            }
            for (al in myAllergies) {
                val tv = TextView(ctx).apply {
                    text = "• Allergic to ${al.allergyDisplay} (Criticality: ${al.criticality})"
                    textSize = 12f
                    setTextColor(severityColor(DDIResult.Severity.MAJOR))
                    setPadding(dp(8), 0, 0, dp(2))
                }
                container.addView(tv)
            }
            for (ds in myDiseases) {
                val tv = TextView(ctx).apply {
                    text = "• Contraindicated with ${ds.condition.displayLocalized} (${ds.severity.label})"
                    textSize = 12f
                    setTextColor(severityColor(ds.severity))
                    setPadding(dp(8), 0, 0, dp(2))
                }
                container.addView(tv)
            }
            
            // Separator
            container.addView(View(ctx).apply { 
                layoutParams = android.widget.LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(8), 0, dp(8)) }
                setBackgroundColor(0x22000000.toInt())
            })
        }
        
        fun addLabelValue(label: String, value: String?) {
            if (value.isNullOrBlank()) return
            val row = TextView(ctx).apply {
                text = buildSpannedString {
                    inSpans(StyleSpan(android.graphics.Typeface.BOLD)) { append("$label: ") }
                    append(value)
                }
                textSize = 13f
                setTextColor(resources.getColor(R.color.jemma_text, ctx.theme))
                setPadding(0, dp(2), 0, dp(2))
            }
            container.addView(row)
        }

        if (!info.description.isNullOrBlank()) {
            val descTv = TextView(ctx).apply {
                text = info.description
                textSize = 13f
                setTextColor(resources.getColor(R.color.jemma_text, ctx.theme))
                setPadding(0, dp(8), 0, dp(8))
            }
            container.addView(descTv)
        }

        addLabelValue("Formula", info.chemicalFormula)
        addLabelValue("Weight", info.molecularWeight?.let { "$it g/mol" })
        addLabelValue("WHO EML", info.whoEmlStatus)
        addLabelValue("WHO AWaRe", info.whoAwareCategory)
        
        if (info.dosages.isNotEmpty()) {
            val doseTitle = TextView(ctx).apply {
                text = "Standard Dosages (WHO DDD):"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, dp(2))
                setTextColor(resources.getColor(R.color.jemma_accent, ctx.theme))
            }
            container.addView(doseTitle)
            for (d in info.dosages) {
                val dTv = TextView(ctx).apply {
                    val dddStr = if (d.ddd != null) "${d.ddd} ${d.unit ?: ""}" else "N/A"
                    text = "• ${d.route ?: "unknown"}: $dddStr (${d.population ?: "adult"})"
                    textSize = 12f
                    setPadding(dp(8), 0, 0, 0)
                    setTextColor(resources.getColor(R.color.jemma_text, ctx.theme))
                }
                container.addView(dTv)
            }
        }

        if (info.foodInteractions.isNotEmpty()) {
            val foodTitle = TextView(ctx).apply {
                text = "Food Interactions:"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, dp(2))
                setTextColor(resources.getColor(R.color.jemma_accent, ctx.theme))
            }
            container.addView(foodTitle)
            for (f in info.foodInteractions) {
                val fTv = TextView(ctx).apply {
                    text = buildSpannedString {
                        inSpans(StyleSpan(android.graphics.Typeface.BOLD)) { append("${f.entityName}: ") }
                        append(f.severity ?: "unknown")
                        if (!f.description.isNullOrBlank()) append("\n${f.description}")
                    }
                    textSize = 12f
                    setPadding(dp(8), 0, 0, dp(4))
                    setTextColor(resources.getColor(R.color.jemma_text, ctx.theme))
                }
                container.addView(fTv)
            }
        }

        if (info.diseaseInteractions.isNotEmpty()) {
            val diseaseTitle = TextView(ctx).apply {
                text = "General Contraindications (Disease):"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, dp(2))
                setTextColor(resources.getColor(R.color.jemma_accent, ctx.theme))
            }
            container.addView(diseaseTitle)
            for (d in info.diseaseInteractions) {
                val dTv = TextView(ctx).apply {
                    text = buildSpannedString {
                        inSpans(StyleSpan(android.graphics.Typeface.BOLD)) { append("${d.entityName}: ") }
                        append(d.severity ?: "unknown")
                        if (!d.description.isNullOrBlank()) append("\n${d.description}")
                    }
                    textSize = 12f
                    setPadding(dp(8), 0, 0, dp(4))
                    setTextColor(resources.getColor(R.color.jemma_text, ctx.theme))
                }
                container.addView(dTv)
            }
        }
    }

    private fun launchMedicationVulgarise(
        vCtx: MedicationVulgariseContext,
        target: TextView,
        btn: MaterialButton,
        headerRow: View,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val anchor = view ?: return@launch
            val lang = Locale.getDefault().language

            // 1. Check cache first (fast path)
            btn.isEnabled = false
            btn.text = getString(R.string.assistant_alert_btn_vulgarising)
            val cached = vulgariseRepo.get(vCtx.cacheKey, lang)
            if (cached != null) {
                headerRow.visibility = View.VISIBLE
                target.visibility = View.VISIBLE
                target.text = cached
                btn.isEnabled = true
                btn.text = getString(R.string.assistant_alert_btn_relancer)
                return@launch
            }

            // 2. Slow path: Gemma
            val stream = tts.startStream(lang)
            val appender = ThrottledTextAppender(anchor, target, ttsStream = stream)
            
            try {
                val tStart = System.currentTimeMillis()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✨ medication vulgarise · atc=${vCtx.atcCode}")
                
                val reply = withContext(Dispatchers.Default) {
                    gemma.ask(
                        prompt = vCtx.toUserPrompt(),
                        systemInstruction = buildMedicationSystemPrompt(),
                        onPartial = { appender.append(it) }
                    )
                }
                appender.cancelUI()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✨ medication vulgarise done · ${System.currentTimeMillis() - tStart}ms")
                
                if (isAdded && target.isAttachedToWindow) {
                    target.text = reply
                }
                appender.end()

                // Save to cache
                vulgariseRepo.save(vCtx.cacheKey, lang, reply)

                if (isAdded && btn.isAttachedToWindow) {
                    btn.isEnabled = true
                    btn.text = getString(R.string.assistant_alert_btn_relancer)
                }
            } catch (e: Throwable) {
                appender.cancel()
                Log.e(TAG, "Medication vulgarisation failed", e)
                if (isAdded && target.isAttachedToWindow) {
                    target.text = "Error: ${e.localizedMessage}"
                    btn.isEnabled = true
                    btn.text = getString(R.string.assistant_alert_btn_relancer)
                }
            }
        }
    }

    private fun toastAndExit(msgRes: Int) {
        Toast.makeText(requireContext(), msgRes, Toast.LENGTH_LONG).show()
        try {
            findNavController().navigateUp()
        } catch (_: Exception) { /* fragment gone */ }
    }

    private fun dp(n: Number): Int {
        return (n.toFloat() * resources.displayMetrics.density).toInt()
    }
}
