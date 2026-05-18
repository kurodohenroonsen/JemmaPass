/*
 * AssistantMultiPreviewFragment.kt — Lot 14.5c (PHASE 14)
 *
 * Reçoit la liste d'[ExtractedMedication] via le singleton
 * [AssistantHandoff] (set par AssistantPipelineFragment après pipeline
 * OK), les affiche dans un RecyclerView avec checkbox individuelle, et
 * permet à l'user de valider la sélection puis save batch dans le profil.
 *
 * 🆕 Lot 14.5c (CE LOT) :
 *   • Consume AssistantHandoff.consume() pour récupérer les vrais items
 *   • RecyclerView dynamique remplace les 3 mocks hardcoded
 *   • Save batch : profilesRepo.loadProfile() → md+= → saveProfile()
 *   • Toast résultat + popBackStack(dest_medications) pour revenir à
 *     la liste qui re-rendra avec le N items ajoutés
 *
 * Fallback : si AssistantHandoff est vide (entrée debug, ou process
 * killed), on garde les 3 mocks du Lot 14.4 pour préview design.
 */
package be.heyman.android.jemmapassdemo.ui.assistant

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.assistant.VulgariseRepository
import be.heyman.android.jemmapassdemo.ai.assistant.AlertVulgariseContext
import be.heyman.android.jemmapassdemo.ai.assistant.AssistantHandoff
import be.heyman.android.jemmapassdemo.ai.assistant.ExtractedMedication
import be.heyman.android.jemmapassdemo.ai.assistant.ThrottledTextAppender
import be.heyman.android.jemmapassdemo.ai.assistant.buildVulgariseSystemPrompt
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.kb.AllergyHit
import be.heyman.android.jemmapassdemo.kb.CrossSeverity
import be.heyman.android.jemmapassdemo.kb.DdiHit
import be.heyman.android.jemmapassdemo.kb.DrugDiseaseHit
import be.heyman.android.jemmapassdemo.kb.KbConcept
import be.heyman.android.jemmapassdemo.kb.KbCrossCheck
import be.heyman.android.jemmapassdemo.kb.ResolvedConcept
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AssistantMultiPreviewFragment : Fragment(R.layout.fragment_assistant_multi_preview) {

    companion object {
        private const val TAG = "JEMMA-ASSISTANT"
    }

    @Inject
    lateinit var profilesRepo: ProfilesRepository

    /** 🆕 Lot 14.5c5 — KbCrossCheck pour cross-check ciblé sur les NOUVEAUX médocs.
     *  Remplace JemmaProfileHydrator (bug : pas d'inférence ATC depuis nom d'allergie). */
    @Inject
    lateinit var crossCheck: KbCrossCheck

    /** 🆕 Lot 14.5c7 — Gemma pour vulgariser les alertes dans la langue du device. */
    @Inject
    lateinit var gemma: GemmaSession

    @Inject
    lateinit var vulgariseRepo: be.heyman.android.jemmapassdemo.ai.assistant.VulgariseRepository

    @Inject
    lateinit var tts: be.heyman.android.jemmapassdemo.ai.tts.JemmaTtsService

    private val items = mutableListOf<ExtractedMedication>()
    private var adapter: AssistantMedRowAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tts.init() // 🆕 Lot 14.5c16 — Init TTS engine for vulgarisation streaming.

        // ── Récupère les items depuis le handoff (set par le pipeline). ──
        val realItems = AssistantHandoff.consume()
        if (realItems.isNotEmpty()) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 📋 multi-preview · ${realItems.size} items from handoff",
            )
            items.addAll(realItems)
        } else {
            // Fallback design preview (Lot 14.4 mocks) si handoff vide.
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 📋 multi-preview · handoff empty · using mock items (fallback)",
            )
            items.addAll(buildMockItems())
        }

        // Toolbar back.
        view.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.multi_preview_toolbar
        )?.setNavigationOnClickListener {
            // 🔧 Lot 14.5c3 — skip le pipeline screen (sinon il relance
            // OCR + Gemma 30s pour rien). Pop direct vers dest_medications.
            cancelAndReturnToMedications()
        }

        // Header count.
        view.findViewById<TextView>(R.id.multi_preview_count_title)?.text =
            getString(R.string.assistant_multi_title, items.size)

        // Wire RecyclerView.
        val recycler = view.findViewById<RecyclerView>(R.id.multi_preview_recycler)
        recycler?.layoutManager = LinearLayoutManager(requireContext())
        adapter = AssistantMedRowAdapter(items) {
            refreshSaveButton(view)
        }
        recycler?.adapter = adapter

        // Toggle all button.
        val btnToggle = view.findViewById<MaterialButton>(R.id.multi_preview_toggle_all)
        btnToggle?.setOnClickListener {
            val allChecked = items.all { it.selected }
            val newState = !allChecked
            for (item in items) item.selected = newState
            adapter?.notifyDataSetChanged()
            btnToggle.setText(
                if (newState) R.string.assistant_multi_unselect_all
                else R.string.assistant_multi_select_all,
            )
            refreshSaveButton(view)
        }

        // Cancel + Save.
        view.findViewById<View>(R.id.multi_preview_btn_cancel)?.setOnClickListener {
            cancelAndReturnToMedications()
        }
        view.findViewById<View>(R.id.multi_preview_btn_save)?.setOnClickListener {
            onSaveClicked()
        }

        refreshSaveButton(view)

        // 🆕 Lot 14.5c4 — Lance le cross-check clinique en arrière-plan.
        // Construit un profil virtuel = existant + nouveaux médocs scannés,
        // hydrate-le, et affiche les DDI / allergies / drug×disease alerts
        // dans la card du haut. L'user voit AVANT de save si ses scans
        // créeraient un conflit avec son profil actuel.
        runClinicalCrossCheck(view)
    }

    /**
     * 🆕 Lot 14.5c3 — Cancel skip le pipeline screen (qui relancerait OCR + Gemma).
     * On pop direct vers dest_medications + clear le handoff.
     */
    private fun cancelAndReturnToMedications() {
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🚪 cancel · pop direct to dest_medications",
        )
        AssistantHandoff.clearAll()
        findNavController().popBackStack(R.id.dest_medications, false)
    }

    /**
     * 🆕 Lot 14.5c5 — Cross-check clinique CIBLÉ sur les NOUVEAUX médocs.
     *
     * Pour chaque médoc scanné résolu, appelle `checkOneDrugAgainstProfile`
     * qui compare ce candidat à toutes les allergies + meds existantes + cond
     * du profil. Avantages vs hydrate() :
     *   • Inférence ATC depuis nom d'allergie (KB v1.2 n'a pas tous les
     *     mappings SNOMED→ATC). C'est ce qui faisait rater
     *     pénicilline (91936005) × amoxicillin (J01CA04) au Lot 14.5c4.
     *   • Cross-réactivité ATC (J01C × J01D, etc.).
     *   • Filtre naturel : on ne montre QUE ce qui change suite au scan.
     *     Plus de spam "Quetiapine × Sertraline" qui existait déjà.
     *
     * Performance : N appels (N = nouveaux médocs résolus). Chaque appel
     * fait 3 sub-checks. Sur les 6 médocs précédents : ~500ms estimé.
     */
    private fun runClinicalCrossCheck(view: View) {
        val targetPid = AssistantHandoff.getTargetProfile()
            ?: profilesRepo.currentProfileId
            ?: return
        val newMeds = items.filter { it.isResolved && !it.gemmaSkipped }
        if (newMeds.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tStart = System.currentTimeMillis()
                val baseProfile = profilesRepo.loadProfile(targetPid) ?: return@launch
                val existingAllergies = baseProfile.al ?: emptyList()
                val existingMeds = baseProfile.md ?: emptyList()
                val existingConditions = baseProfile.cn ?: emptyList()
                val uiLang = resources.configuration.locales[0].language
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🩺 cross-check ciblé · pid=$targetPid · " +
                        "scanned=${newMeds.size} · existing(al=${existingAllergies.size} " +
                        "md=${existingMeds.size} cn=${existingConditions.size}) · lang=$uiLang",
                )

                val newAlerts = mutableListOf<NewAlert>()
                for ((idx, item) in newMeds.withIndex()) {
                    val candidateName = item.kbConcept()?.primaryDisplay
                        ?: item.topCandidate
                        ?: continue
                    val result = crossCheck.checkOneDrugAgainstProfile(
                        candidateName = candidateName,
                        allergies = existingAllergies,
                        // ⚠️ inclure les autres médocs scannés aussi → permet
                        //    de détecter DDI entre 2 médocs nouveaux dans le
                        //    même scan (ex: scanner Warfarin + Aspirine).
                        meds = existingMeds + (newMeds.drop(idx + 1).mapNotNull {
                            it.toJMedication().takeIf { jm -> jm.c != null }
                        }),
                        conditions = existingConditions,
                        lang = uiLang,
                    )
                    for (h in result.allergyHits) newAlerts += NewAlert.Allergy(item, h)
                    for (h in result.ddiHits) newAlerts += NewAlert.Ddi(item, h)
                    for (h in result.drugDiseaseHits) newAlerts += NewAlert.DrugDisease(item, h)
                }

                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🩺 cross-check ciblé done · " +
                        "newAlerts=${newAlerts.size} · in ${System.currentTimeMillis() - tStart}ms",
                )
                renderClinicalAlerts(view, newAlerts)
            } catch (e: Throwable) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ cross-check failed", e)
            }
        }
    }

    /** Union type pour grouper les alertes par ordre de sévérité au rendu. */
    private sealed class NewAlert(open val triggeredBy: ExtractedMedication) {
        data class Allergy(
            override val triggeredBy: ExtractedMedication,
            val hit: AllergyHit,
        ) : NewAlert(triggeredBy)
        data class Ddi(
            override val triggeredBy: ExtractedMedication,
            val hit: DdiHit,
        ) : NewAlert(triggeredBy)
        data class DrugDisease(
            override val triggeredBy: ExtractedMedication,
            val hit: DrugDiseaseHit,
        ) : NewAlert(triggeredBy)
    }

    /** Render les NOUVELLES alertes (impliquant au moins un médoc scanné). */
    private fun renderClinicalAlerts(view: View, alerts: List<NewAlert>) {
        val card = view.findViewById<View>(R.id.multi_preview_alerts_card) ?: return
        val header = view.findViewById<TextView>(R.id.multi_preview_alerts_header) ?: return
        val subtitle = view.findViewById<TextView>(R.id.multi_preview_alerts_subtitle) ?: return
        val container = view.findViewById<android.widget.LinearLayout>(
            R.id.multi_preview_alerts_container
        ) ?: return

        if (alerts.isEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val total = alerts.size
        header.text = resources.getQuantityString(
            R.plurals.assistant_alerts_header,
            total, total,
        )
        val majors = alerts.count { isMajor(it) }
        subtitle.text = if (majors > 0) {
            getString(R.string.assistant_alerts_subtitle_with_majors, majors, total - majors)
        } else {
            getString(R.string.assistant_alerts_subtitle_no_majors, total)
        }

        container.removeAllViews()
        // Ordre : majors first, puis moderates, et regroupe par type.
        val sorted = alerts.sortedWith(
            compareByDescending<NewAlert> { isMajor(it) }
                .thenBy { typeOrder(it) }
        )
        for (a in sorted) {
            container.addView(buildAlertRow(formatAlert(a), toContext(a)))
        }
    }

    /** Convertit une NewAlert en AlertVulgariseContext pour le bouton Vulgariser. */
    private fun toContext(a: NewAlert): AlertVulgariseContext = when (a) {
        is NewAlert.Allergy -> AlertVulgariseContext(
            kind = "allergy",
            subjects = listOf(a.hit.allergyDisplay, a.hit.candidateDisplay),
            severity = a.hit.criticality.name,
            mechanism = "Cross-reactivity / ATC class match (${a.hit.matchedOn})",
            description = "Patient is allergic to ${a.hit.allergyDisplay}; this drug " +
                "(${a.hit.candidateDisplay}, ATC ${a.hit.candidateAtc}) belongs to a " +
                "class that may trigger the same reaction.",
            cacheKey = "AL|${a.hit.allergyCode}|${a.hit.candidateAtc}",
        )
        is NewAlert.Ddi -> AlertVulgariseContext(
            kind = "drug_drug_interaction",
            subjects = listOf(a.triggeredBy.displayName(), a.hit.existingMedDisplay),
            severity = a.hit.severity.name,
            mechanism = a.hit.mechanism,
            description = a.hit.description,
            cacheKey = "DDI|${listOf<String>(a.hit.candidateAtc, a.hit.existingMedAtc).sorted().joinToString("|")}",
        )
        is NewAlert.DrugDisease -> AlertVulgariseContext(
            kind = "drug_disease",
            subjects = listOf(a.triggeredBy.displayName(), a.hit.conditionDisplay),
            severity = a.hit.severity.name,
            mechanism = "Contraindication for condition ${a.hit.diseaseNameMatched}",
            description = a.hit.description,
            cacheKey = "DDSI|${a.triggeredBy.atcCode() ?: ""}|${a.hit.conditionCode}",
        )
    }

    private fun isMajor(a: NewAlert): Boolean = when (a) {
        is NewAlert.Allergy -> a.hit.criticality.name == "HIGH"
        is NewAlert.Ddi -> a.hit.severity == CrossSeverity.MAJOR
        is NewAlert.DrugDisease -> a.hit.severity == CrossSeverity.MAJOR
    }

    private fun typeOrder(a: NewAlert): Int = when (a) {
        is NewAlert.Allergy -> 0       // allergie en premier (le plus actionnable en urgence)
        is NewAlert.DrugDisease -> 1
        is NewAlert.Ddi -> 2
    }

    /**
     * 🆕 Lot 14.5c8 — Build une row d'alerte avec :
     *   • Texte de l'alerte clinique (KB anglais)
     *   • Bouton "✨ Expliquer simplement" qui appelle Gemma
     *   • Zone de texte vulgarisée avec en-tête ("✨ Expliqué simplement :")
     *   • Après réussite : bouton devient "🔄 Relancer", re-cliquable
     *   • Séparateur visuel discret en haut de chaque row pour distinguer
     *     les alertes entre elles
     */
    private fun buildAlertRow(
        alertText: String,
        alertContext: AlertVulgariseContext,
    ): View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(n: Int): Int = (n * density).toInt()

        val column = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        // Bouton "✨ Expliquer simplement" + zone de vulgarisation.
        val vulgariseBtn = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            this.text = getString(R.string.assistant_alert_btn_vulgarise)
            textSize = 11.5f
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
        }

        // En-tête "✨ Expliqué simplement :" (visible quand on a une vulgarisation).
        val vulgariseHeaderRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        val jemmaAvatar = android.widget.ImageView(ctx).apply {
            setImageResource(R.drawable.jemmapass_icon)
            val size = dp(20)
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(6)
            }
        }
        vulgariseHeaderRow.addView(jemmaAvatar)
        val vulgariseHeader = TextView(ctx).apply {
            this.text = getString(R.string.assistant_alert_vulgarise_header)
            textSize = 11.5f
            setTextColor(resources.getColor(R.color.jemma_accent, ctx.theme))
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
        }
        val playBtn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
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

        val vulgariseText = TextView(ctx).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(resources.getColor(R.color.jemma_text, ctx.theme))
            setLineSpacing(0f, 1.25f)
            setPadding(0, 0, 0, 0)
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.ITALIC)
        }

        column.addView(vulgariseBtn, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(6) })
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

        // L'alerte d'origine (clinique).
        val original = TextView(ctx).apply {
            this.text = alertText
            textSize = 12.5f
            setTextColor(resources.getColor(R.color.jemma_text, ctx.theme))
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(6), 0, 0)
        }
        column.addView(original)

        // 🆕 Lot 14.5c16 — Check GLOBAL cache before showing.
        viewLifecycleOwner.lifecycleScope.launch {
            val lang = Locale.getDefault().language
            val cached = vulgariseRepo.get(alertContext.cacheKey, lang)
            if (cached != null) {
                Log.i(TAG, "🎯 [GLOBAL CACHE] vulgarisation found for ${alertContext.cacheKey} in $lang")
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
            launchVulgarise(alertContext, vulgariseText, vulgariseBtn)
        }

        return column
    }

    /**
     * 🆕 Lot 14.5c8 — Appelle Gemma pour vulgariser une alerte clinique
     * dans la langue du device. Stream les tokens en live dans le TextView.
     *
     * Après réussite : bouton reste visible, devient "🔄 Relancer" et
     * peut être re-cliqué pour regénérer (utile si la première sortie
     * est moche ou trop technique).
     */
    private fun launchVulgarise(
        ctx: AlertVulgariseContext,
        target: TextView,
        button: MaterialButton,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val anchor = view ?: return@launch
            val langTag = Locale.getDefault().language
            val ttsStream = tts.startStream(Locale.getDefault().toLanguageTag())
            val appender = ThrottledTextAppender(anchor, target, ttsStream = ttsStream)
            try {
                val tStart = System.currentTimeMillis()
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✨ vulgarise (multi-preview) · " +
                        "type=${ctx.kind} · lang=$langTag · subjects=${ctx.subjects}",
                )
                
                // On force l'exécution hors du main thread pour éviter de figer l'UI
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
                    "[t=${System.currentTimeMillis()}] ✨ vulgarise done (multi-preview) · " +
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
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ vulgarise failed (multi-preview)", e)
                if (isAdded && target.isAttachedToWindow) {
                    target.text = getString(R.string.assistant_alert_vulgarise_failed)
                    button.isEnabled = true
                    button.text = getString(R.string.assistant_alert_btn_vulgarise)
                }
            }
        }
    }

    /** [AlertVulgariseContext] est défini dans
     *  `ai/assistant/VulgariseHelper.kt`. Plus de class locale ici. */

    private fun formatAlert(a: NewAlert): String {
        val triggerName = a.triggeredBy.displayName()
        val lang = resources.configuration.locales[0].language
        return when (a) {
            is NewAlert.Allergy -> {
                val sev = if (a.hit.criticality.name == "HIGH") "🚫" else "⚠️"
                val match = when {
                    a.hit.matchedOn == "code" -> ""
                    a.hit.matchedOn == "name" -> ""
                    a.hit.matchedOn.startsWith("class:") -> {
                        val classLabel = when (lang) {
                            "fr" -> "classe"
                            "ja" -> "クラス"
                            else -> "class"
                        }
                        " ($classLabel ${a.hit.matchedOn.removePrefix("class:")})"
                    }
                    else -> ""
                }
                val label = when (lang) {
                    "fr" -> "Allergie"
                    "ja" -> "アレルギー"
                    else -> "Allergy"
                }
                "$sev $label : ${a.hit.allergyDisplay} × $triggerName$match"
            }
            is NewAlert.Ddi -> {
                val sev = if (a.hit.severity == CrossSeverity.MAJOR) "🟥" else "🟧"
                val desc = a.hit.description?.take(120) ?: a.hit.mechanism ?: "interaction"
                "$sev $triggerName × ${a.hit.existingMedDisplay}\n   → $desc"
            }
            is NewAlert.DrugDisease -> {
                val sev = if (a.hit.severity == CrossSeverity.MAJOR) "⚕️🟥" else "⚕️🟧"
                val desc = a.hit.description?.take(120) ?: ""
                "$sev $triggerName × ${a.hit.conditionDisplay}\n   → $desc"
            }
        }
    }

    private fun onSaveClicked() {
        // 🔧 Lot 14.5c3 — Ne save QUE les items :
        //   • cochés par l'user (selected=true)
        //   • effectivement résolus dans la KB (skipped les "Non identifié"
        //     même si l'user a coché — y a rien à persister sans ATC code).
        val checkedItems = items.filter { it.selected && !it.gemmaSkipped && it.isResolved }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 💾 batch save · checked=${checkedItems.size}/${items.size} " +
                "(filtered out unresolved+skipped)",
        )
        if (checkedItems.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            // 🔧 Lot 14.5c2 — priorité au profil cible explicite set par
            // MedicationsEditFragment avant le navigate. Fallback sur
            // currentProfileId si null (cas où on arrive en debug direct).
            val pid = AssistantHandoff.getTargetProfile()
                ?: profilesRepo.currentProfileId
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 💾 batch save · pid=$pid · " +
                    "(handoffTarget=${AssistantHandoff.getTargetProfile()} · " +
                    "current=${profilesRepo.currentProfileId})",
            )
            if (pid == null) {
                Toast.makeText(
                    requireContext(),
                    R.string.assistant_save_no_profile,
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            try {
                val profile = profilesRepo.loadProfile(pid) ?: run {
                    Toast.makeText(
                        requireContext(),
                        R.string.assistant_save_no_profile,
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                val newMeds = checkedItems.map { it.toJMedication() }
                val updated = profile.copy(md = (profile.md ?: emptyList()) + newMeds)
                profilesRepo.saveProfile(updated, sourceFormat = "ASSISTANT_GEMMA_KB")
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ batch save OK · ${newMeds.size} med(s) added to profile=$pid",
                )
                // 🔧 Lot 14.5c2 — clear le handoff après save pour éviter
                // qu'un prochain pipeline réutilise un targetProfileId périmé
                // si l'utilisateur navigue back et revient.
                AssistantHandoff.clearAll()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.assistant_save_success, newMeds.size),
                    Toast.LENGTH_LONG,
                ).show()
                // Pop back jusqu'à la liste de médicaments.
                findNavController().popBackStack(R.id.dest_medications, false)
            } catch (e: Throwable) {
                Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ batch save failed", e)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.assistant_save_failed, e.message ?: "?"),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** Le bouton Save reflète le count des items SAUVABLES (cochés + résolus). */
    private fun refreshSaveButton(rootView: View) {
        val btn = rootView.findViewById<MaterialButton>(R.id.multi_preview_btn_save) ?: return
        // 🔧 Lot 14.5c3 — exclut les gemmaSkipped + non résolus du count.
        val checked = items.count { it.selected && !it.gemmaSkipped && it.isResolved }
        if (checked == 0) {
            btn.setText(R.string.assistant_multi_save_none)
            btn.isEnabled = false
        } else {
            btn.text = getString(R.string.assistant_multi_save_n, checked)
            btn.isEnabled = true
        }
    }

    /**
     * Mock items du Lot 14.4 — utilisés UNIQUEMENT en fallback si on
     * arrive sur l'écran sans avoir alimenté AssistantHandoff (entrée
     * debug, process killed, etc.). Permet de garder une preview
     * design utilisable même hors flow normal.
     */
    private fun buildMockItems(): List<ExtractedMedication> = listOf(
        ExtractedMedication(
            pageIndex = 0,
            ocrSnippet = "BURINEX COMP 20 X5 MG · Hebdomadaire 12/02/2026",
            gemmaCandidates = listOf("bumetanide"),
            topCandidate = "bumetanide",
            kbResolution = ResolvedConcept.Exact(
                KbConcept(
                    code = "MOCK-1",
                    system = "mock",
                    primaryDisplay = "Bumetanide",
                    atcCode = "C03CA01",
                )
            ),
        ),
        ExtractedMedication(
            pageIndex = 0,
            ocrSnippet = "CARBONATE CALCIUM 1GR · Quotidienne",
            gemmaCandidates = listOf("calcium carbonate"),
            topCandidate = "calcium carbonate",
            kbResolution = ResolvedConcept.Exact(
                KbConcept(
                    code = "MOCK-2",
                    system = "mock",
                    primaryDisplay = "Calcium carbonate",
                    atcCode = "A12AA04",
                )
            ),
        ),
        ExtractedMedication(
            pageIndex = 0,
            ocrSnippet = "D-CURE 25000UI GELULES 12 · Mensuelle",
            gemmaCandidates = listOf("cholecalciferol"),
            topCandidate = "cholecalciferol",
            kbResolution = ResolvedConcept.NotFound,
        ),
    )
}
