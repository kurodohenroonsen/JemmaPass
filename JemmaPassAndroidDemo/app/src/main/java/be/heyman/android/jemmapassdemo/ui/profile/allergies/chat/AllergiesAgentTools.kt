/*
 * AllergiesAgentTools.kt — JEMMA Pass · Lot 14.5c30 (PHASE 14)
 *
 * The @Tool surface Gemma 4 sees when collecting an allergy via voice
 * chat. Implements [com.google.ai.edge.litertlm.ToolSet] so LiteRT-LM
 * can expose these methods to the model via native function calling.
 *
 * Architecture — why so many tools :
 *   The Gemma 4 Good hackathon Special Tech Track LiteRT prize ($10k)
 *   awards "the most compelling use case built using Google AI Edge's
 *   LiteRT implementation of Gemma 4". Gemma 4's *unique* features are
 *   native function calling and multimodal. So we maximize the number
 *   of VISIBLE @Tool invocations per turn — each call becomes a chip
 *   in the chat, and the demo video shows Gemma reasoning live with
 *   its toolbelt. The more tool calls per turn, the more impressive.
 *
 * Tool families (16 total) :
 *   A · Draft setters         (6) — setSubstance, setCategory, setSeverity,
 *                                    setStatus, setManifestation, setOnsetDate
 *   B · KB-aware (active)     (4) — searchSnomedAllergyIntolerance,
 *                                    searchSnomedReaction, confirmSnomedCode,
 *                                    confirmReactionCode
 *   C · Cross-check + profile (2) — getProfileSummary, checkCrossWithMedications
 *   D · Interaction           (2) — playTTS, askChoice
 *   E · Commit                (2) — saveAllergyDraft, cancelAndExit
 *
 * Wiring :
 *   AllergiesChatFragment.onViewCreated
 *     → allergiesAgentTools.bind(profileId, lang)
 *     → gemma.configureForTask(taskId="allergies-chat",
 *                              systemPrompt=..., tools=[allergiesAgentTools, jemmaTools])
 *   AllergiesChatFragment.onDestroyView
 *     → allergiesAgentTools.unbind()
 *     → gemma.clearTaskConfig()
 *
 * Threading :
 *   LiteRT-LM invokes @Tool methods synchronously from its native thread.
 *   We MUST NOT do long blocking work here (would freeze inference).
 *   Strategy: setters are pure state mutations + event emit (<2 ms).
 *   KB search tools use runBlocking(Dispatchers.IO) because Gemma is
 *   waiting on the result — we have no choice. Latency budget per
 *   search ~30-200 ms (FTS5 + small SQLite IPS valueset).
 *
 * State :
 *   The agent maintains the in-progress [IpsAllergyDraft] internally
 *   (singleton-scoped to the current chat session via bind/unbind).
 *   The fragment observes [events] (a SharedFlow) and re-renders the
 *   IPS mini-card + the bg-action chip row. Single source of truth:
 *   THIS class. Fragment is read-only.
 */
package be.heyman.android.jemmapassdemo.ui.profile.allergies.chat

import android.content.Context
import android.util.Log
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.kb.AllergyReactionItem
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseManager
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.getAllergyIntoleranceList
import be.heyman.android.jemmapassdemo.kb.getAllergyReactionList
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

private const val TAG = "JEMMA-ALLERG-AGENT"

/**
 * Snapshot of the agent's internal draft. Exposed via [AllergiesAgentTools.snapshot]
 * for the fragment to render the IPS mini-card + build the save bundle.
 *
 * Note: substanceCode / manifestationCode are filled either by Gemma
 * (via confirmSnomedCode / confirmReactionCode tools) or by the fragment
 * (kickoffSubstanceLookup / kickoffManifestationLookup automatic KB
 * lookup). Both write back via [AllergiesAgentTools.setResolvedSnomedCode].
 */
data class AllergiesAgentDraft(
    val substance: String? = null,
    val category: String? = null,
    val severity: String? = null,
    val status: String? = null,
    val manifestation: String? = null,
    val onsetDate: String? = null,
    val substanceCode: String? = null,
    val substanceDisplay: String? = null,
    val substanceSystem: String? = null,
    val manifestationCode: String? = null,
    val manifestationDisplay: String? = null,
    val manifestationSystem: String? = null,
) {
    /**
     * 🆕 Lot 14.5c34 v2 — Two separate concepts now :
     *
     *  • filledCount : number of fields displayed in the IPS draft card
     *    that have been captured. Used for the "X/5" UI badge in the
     *    voice screen. The visible card shows 5 lines : substance,
     *    category, severity, status, manifestation. Date is NOT shown
     *    (collected only via manual form).
     *
     *  • isMinimallyComplete : the actual save-gate. Per the manual
     *    form validation (AllergyFormBottomSheet.kt:736 — only
     *    substance is required), ONE field is enough. Severity defaults
     *    to "U" and status to "A" at save if missing.
     */
    val filledCount: Int
        get() = listOfNotNull(substance, category, severity, status, manifestation).size
    val mandatoryFieldCount: Int get() = 5

    val isMinimallyComplete: Boolean
        get() = !substance.isNullOrBlank()
}


@Singleton
class AllergiesAgentTools @Inject constructor(
    private val kbService: KnowledgeBaseService,
    private val kbManager: KnowledgeBaseManager,
    private val profilesRepo: ProfilesRepository,
    private val translationsRepo: be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository,
    @ApplicationContext private val context: Context,
) : ToolSet {

    // ─────────────────────────────────────────────────────────────────
    // Session binding (called by the fragment at chat start/end)
    // ─────────────────────────────────────────────────────────────────

    private val draftRef = AtomicReference(AllergiesAgentDraft())
    private val profileIdRef = AtomicReference<String?>(null)
    private val langRef = AtomicReference("fr")

    private val _events = MutableSharedFlow<AllergiesAgentEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<AllergiesAgentEvent> = _events.asSharedFlow()

    /** Reset the agent state for a new chat session. */
    fun bind(profileId: String?, lang: String = "fr") {
        draftRef.set(AllergiesAgentDraft())
        profileIdRef.set(profileId)
        langRef.set(lang)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 bind · profileId=$profileId · lang=$lang")
    }

    fun unbind() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 unbind")
        draftRef.set(AllergiesAgentDraft())
        profileIdRef.set(null)
    }

    /** Public snapshot for the fragment (mini-card render, save bundle). */
    fun snapshot(): AllergiesAgentDraft = draftRef.get()

    /**
     * Public setter used by the fragment's automatic KB lookups
     * (kickoffSubstanceLookup runs in the fragment's coroutine scope and
     * writes back the resolved SNOMED code here once available).
     */
    fun setResolvedSubstanceCode(code: String?, display: String?, system: String?) {
        draftRef.updateAndGet {
            it.copy(
                substanceCode = code,
                substanceDisplay = display,
                substanceSystem = system,
            )
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📚 substance KB resolved · code=$code · display=$display")
    }

    fun setResolvedManifestationCode(code: String?, display: String?, system: String?) {
        draftRef.updateAndGet {
            it.copy(
                manifestationCode = code,
                manifestationDisplay = display,
                manifestationSystem = system,
            )
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📚 manifestation KB resolved · code=$code · display=$display")
    }

    /**
     * 🆕 Lot 14.5c30 v5 — KB-driven auto-category : called by the fragment
     * after a SNOMED code is resolved (Tier 0/1) AND the FHIR category is
     * looked up via [KnowledgeBaseService.resolveAllergyFhirCategory]. The
     * fragment short-circuits Gemma here because the KB knows the FHIR
     * category authoritatively (food/medication/environment/biologic).
     * Idempotent : skips if category is already set.
     */
    fun applyKbResolvedCategory(category: String?) {
        if (category.isNullOrBlank()) return
        val clean = category.trim().lowercase()
        if (clean !in listOf("food", "medication", "environment", "biologic")) return
        val prev = draftRef.get().category
        if (prev == clean) return
        draftRef.updateAndGet { it.copy(category = clean) }
        val displayValue = BgActionTemplates.translateValue(context, "category", clean)
        val autoKbStr = context.getString(R.string.allergies_chat_auto_kb)
        emitSync(
            toolName = "kbResolvedCategory",
            template = BgActionTemplates.SET_CATEGORY,
            value = "$displayValue $autoKbStr",
        )
        _events.tryEmit(AllergiesAgentEvent.DraftFieldUpdated(field = "category", value = clean))
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏷️ applyKbResolvedCategory · '$clean' (was '$prev')")
    }

    /**
     * 🆕 Lot 14.5c30 v5 — Kotlin-side date parser bypass : the fragment
     * extracts ISO date from the user's STT input directly, because Gemma
     * sometimes hallucinates / truncates digits under CPU sampler fallback
     * ("2024" → "204"). Format must be strict YYYY-MM-DD ; rejected otherwise.
     * Idempotent : skips if onsetDate already set.
     */
    fun applyResolvedOnsetDate(iso: String?) {
        if (iso.isNullOrBlank()) return
        // Validate strict YYYY-MM-DD with plausible year.
        val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(iso.trim()) ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ applyResolvedOnsetDate · rejected bad format '$iso'")
            return
        }
        val year = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val day = m.groupValues[3].toInt()
        if (year !in 1900..2100 || month !in 1..12 || day !in 1..31) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ applyResolvedOnsetDate · rejected out-of-range '$iso'")
            return
        }
        val prev = draftRef.get().onsetDate
        if (!prev.isNullOrBlank()) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] ℹ️ applyResolvedOnsetDate · skip (already set='$prev')")
            return
        }
        draftRef.updateAndGet { it.copy(onsetDate = iso.trim()) }
        val autoStr = context.getString(R.string.allergies_chat_auto)
        emitSync(
            toolName = "kbResolvedOnsetDate",
            template = BgActionTemplates.SET_ONSET_DATE,
            value = "${iso.trim()} $autoStr",
        )
        _events.tryEmit(AllergiesAgentEvent.DraftFieldUpdated(field = "onsetDate", value = iso.trim()))
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📅 applyResolvedOnsetDate · '${iso.trim()}'")
    }

    // Helper: emit a BgAction event paired (RUNNING + DONE) for synchronous
    // tools so the chip transitions in the UI even though work is <2 ms.
    // Returns the generated actionId so callers can chain RUNNING→DONE.
    private fun emitSync(
        toolName: String,
        template: BgActionTemplate,
        value: String? = null,
        detail: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val tStart = System.currentTimeMillis()
        _events.tryEmit(
            AllergiesAgentEvent.BgAction(
                actionId = id,
                toolName = toolName,
                emoji = template.emoji,
                label = context.getString(template.labelRes),
                value = value,
                state = AgentActionState.RUNNING,
            )
        )
        _events.tryEmit(
            AllergiesAgentEvent.BgAction(
                actionId = id,
                toolName = toolName,
                emoji = template.emoji,
                label = context.getString(template.labelRes),
                value = value,
                state = AgentActionState.DONE,
                detail = detail,
                latencyMs = System.currentTimeMillis() - tStart,
            )
        )
        return id
    }

    // Helper for async-style tools (KB search): emit RUNNING first, do
    // work, emit DONE with full latency.
    private fun emitRunning(toolName: String, template: BgActionTemplate, value: String? = null): Pair<String, Long> {
        val id = UUID.randomUUID().toString()
        _events.tryEmit(
            AllergiesAgentEvent.BgAction(
                actionId = id,
                toolName = toolName,
                emoji = template.emoji,
                label = context.getString(template.labelRes),
                value = value,
                state = AgentActionState.RUNNING,
            )
        )
        return id to System.currentTimeMillis()
    }

    private fun emitDone(
        actionId: String,
        toolName: String,
        template: BgActionTemplate,
        startMs: Long,
        value: String? = null,
        detail: String? = null,
        state: AgentActionState = AgentActionState.DONE,
    ) {
        _events.tryEmit(
            AllergiesAgentEvent.BgAction(
                actionId = actionId,
                toolName = toolName,
                emoji = template.emoji,
                label = context.getString(template.labelRes),
                value = value,
                state = state,
                detail = detail,
                latencyMs = System.currentTimeMillis() - startMs,
            )
        )
    }

    // =================================================================
    // FAMILY A — Draft setters (6 tools)
    // =================================================================

    @Tool(description = "Set the allergen or food intolerance substance. **Treat 'allergy', 'intolerance', 'allergic', 'intolerant' as semantically equivalent — always extract the substance and call this tool.** Call as soon as the user mentions any substance (drug, food, latex, pollen, etc.) regardless of whether they say 'allergic to' or 'intolerant to'. Value MUST be English singular noun, capitalized: 'Penicillin', 'Fish' (NOT 'Fishes'), 'Peanut', 'Latex'.")
    fun setSubstance(
        @ToolParam(description = "Substance name in English, singular, capitalized. Example: 'Penicillin', 'Fish'") name: String,
    ): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY setSubstance · raw='$name'")
        val clean = name.trim()
        if (clean.isEmpty()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ setSubstance · rejected · reason=empty_name")
            return mapOf("ok" to false, "error" to "empty_name")
        }
        draftRef.updateAndGet { it.copy(substance = clean, substanceCode = null, substanceDisplay = null) }
        emitSync(toolName = "setSubstance", template = BgActionTemplates.SET_SUBSTANCE, value = clean)
        _events.tryEmit(AllergiesAgentEvent.DraftFieldUpdated(field = "substance", value = clean))
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊 setSubstance · ACCEPTED · '$clean'")
        return mapOf("ok" to true, "substance" to clean)
    }

    @Tool(description = "🚫 DO NOT CALL THIS TOOL. The category is auto-inferred from the substance via the SNOMED knowledge base. This stub exists only for legacy bindings. Never call setCategory.")
    fun setCategory(
        @ToolParam(description = "DO NOT CALL") category: String,
    ): Map<String, Any> {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] 🚫 setCategory called but IGNORED · raw='$category' (category is auto-KB — c34)")
        return mapOf("ok" to true, "ignored" to true, "reason" to "category_is_auto_kb")
    }

    @Tool(description = "Set the allergy severity (FHIR R4 criticality). MUST be exactly one of these three English values: 'High', 'Low', 'Unknown'. **Map expressions BEFORE calling**: 'severe/anaphylactic/strong' → 'High'. 'mild/light/slightly' → 'Low'. 'I do not know' → 'Unknown'. NEVER pass any other value.")
    fun setSeverity(
        @ToolParam(description = "Exactly one of: High, Low, Unknown (English only)") severity: String,
    ): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY setSeverity · raw='$severity'")
        // Permissive normalization: French expressions Gemma may slip in.
        val raw = severity.trim().lowercase()
        val mapped = when (raw) {
            in setOf("high", "élevé", "élevée", "eleve", "grave", "sévère", "severe",
                "anaphylactique", "fort", "fortement", "très grave", "tres grave",
                "haut", "haute") -> "High"
            in setOf("low", "léger", "légère", "leger", "legere", "peu", "modéré",
                "modere", "modérée", "modere", "modérément", "moderement",
                "faible", "bas", "basse") -> "Low"
            in setOf("unknown", "inconnu", "inconnue", "je ne sais pas",
                "ne sait pas", "indéterminé", "indetermine") -> "Unknown"
            else -> severity.trim().replaceFirstChar { it.uppercase() }
        }
        if (mapped !in listOf("High", "Low", "Unknown")) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ setSeverity · rejected · raw='$severity' · mapped='$mapped'")
            return mapOf("ok" to false, "error" to "invalid_severity", "allowed" to listOf("High", "Low", "Unknown"))
        }
        draftRef.updateAndGet { it.copy(severity = mapped) }
        val displayValue = BgActionTemplates.translateValue(context, "severity", mapped)
        emitSync(toolName = "setSeverity", template = BgActionTemplates.SET_SEVERITY, value = displayValue)
        _events.tryEmit(AllergiesAgentEvent.DraftFieldUpdated(field = "severity", value = mapped))
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ⚠️ setSeverity · ACCEPTED · '$mapped' (from raw='$severity')")
        return mapOf("ok" to true, "severity" to mapped)
    }

    @Tool(description = "🚫 DO NOT CALL THIS TOOL. The clinical status defaults to 'Active' at save time. This stub exists only for legacy bindings. Never call setStatus.")
    fun setStatus(
        @ToolParam(description = "DO NOT CALL") status: String,
    ): Map<String, Any> {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] 🚫 setStatus called but IGNORED · raw='$status' (status defaults to Active — c34)")
        return mapOf("ok" to true, "ignored" to true, "reason" to "status_is_default_active")
    }

    @Tool(description = "Set the clinical manifestation/reaction observed (hives, anaphylactic shock, itching, etc.). English text, singular, capitalized. Triggers automatic background KB lookup against the SNOMED IPS allergy-reaction valueset.")
    fun setManifestation(
        @ToolParam(description = "Reaction description in English, capitalized. Example: 'Hives', 'Anaphylactic shock'") manifestation: String,
    ): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY setManifestation · raw='$manifestation'")
        val clean = manifestation.trim()
        if (clean.isEmpty()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ setManifestation · rejected · reason=empty")
            return mapOf("ok" to false, "error" to "empty_manifestation")
        }
        draftRef.updateAndGet { it.copy(manifestation = clean, manifestationCode = null, manifestationDisplay = null) }
        emitSync(toolName = "setManifestation", template = BgActionTemplates.SET_MANIFESTATION, value = clean)
        _events.tryEmit(AllergiesAgentEvent.DraftFieldUpdated(field = "manifestation", value = clean))
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔥 setManifestation · ACCEPTED · '$clean'")
        return mapOf("ok" to true, "manifestation" to clean)
    }

    @Tool(description = "🚫 DO NOT CALL THIS TOOL. The onset date is NOT collected by voice — it is set later in the manual form. This stub exists only for legacy bindings. If you find yourself wanting to capture a date, IGNORE it and move on. Never call setOnsetDate.")
    fun setOnsetDate(
        @ToolParam(description = "DO NOT CALL") isoDate: String,
    ): Map<String, Any> {
        Log.w(TAG, "[t=${System.currentTimeMillis()}] 🚫 setOnsetDate called but IGNORED · raw='$isoDate' (date is no longer collected via voice — c33)")
        // Silently no-op : do not mutate draft, do not emit event,
        // do not raise an error to Gemma either (so she just moves on).
        return mapOf("ok" to true, "ignored" to true, "reason" to "date_not_voice_collected")
    }

    // =================================================================
    // FAMILY B — KB-aware tools (4)
    // =================================================================

    @Tool(description = "Search the SNOMED-CT IPS allergy-intolerance valueset (292 codes) for a candidate matching the given query. Returns up to 5 ranked candidates with their SNOMED code and display. Call this when the user mentions an allergen and you want to confirm the exact SNOMED code before saving. After picking, call confirmSnomedCode with the chosen code.")
    fun searchSnomedAllergyIntolerance(
        @ToolParam(description = "Search query in French. Example: 'pénicilline', 'arachide', 'latex'") query: String,
    ): Map<String, Any> {
        val q = query.trim()
        if (q.isEmpty()) return mapOf("ok" to false, "error" to "empty_query", "candidates" to emptyList<Map<String, String>>())

        val (actionId, tStart) = emitRunning(
            toolName = "searchSnomedAllergyIntolerance",
            template = BgActionTemplates.KB_SEARCH_INTOLERANCE,
            value = q,
        )

        val candidates = runBlocking(Dispatchers.IO) {
            try {
                val lang = langRef.get() ?: "fr"
                val list = kbService.getAllergyIntoleranceList(kbManager, lang)
                val effectiveList = if (lang.lowercase().take(2) == "en") {
                    list.map { item ->
                        val tr = translationsRepo.get(item.code, "en")
                        if (!tr.isNullOrBlank()) item.copy(display = tr) else item
                    }
                } else list
                fuzzyTopN(q, effectiveList, n = 5)
            } catch (e: Throwable) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ searchSnomedAllergyIntolerance err: ${e.message}")
                emptyList()
            }
        }

        emitDone(
            actionId = actionId,
            toolName = "searchSnomedAllergyIntolerance",
            template = BgActionTemplates.KB_SEARCH_INTOLERANCE,
            startMs = tStart,
            value = "${candidates.size} candidats",
            detail = candidates.joinToString("\n") { "${it.code} · ${it.display}" },
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔍 searchSnomedAllergyIntolerance · q='$q' · n=${candidates.size}")
        return mapOf(
            "ok" to true,
            "query" to q,
            "count" to candidates.size,
            "candidates" to candidates.map { mapOf("code" to it.code, "display" to it.display, "system" to it.system) },
        )
    }

    @Tool(description = "Search the SNOMED-CT IPS allergy-reaction valueset (29 codes) for a candidate matching the given reaction query. Returns up to 5 candidates. Call this to find the exact SNOMED code for a clinical reaction. After picking, call confirmReactionCode.")
    fun searchSnomedReaction(
        @ToolParam(description = "Reaction query in French. Example: 'urticaire', 'choc', 'démangeaisons'") query: String,
    ): Map<String, Any> {
        val q = query.trim()
        if (q.isEmpty()) return mapOf("ok" to false, "error" to "empty_query", "candidates" to emptyList<Map<String, String>>())

        val (actionId, tStart) = emitRunning(
            toolName = "searchSnomedReaction",
            template = BgActionTemplates.KB_SEARCH_REACTION,
            value = q,
        )

        val candidates = runBlocking(Dispatchers.IO) {
            try {
                val lang = langRef.get() ?: "fr"
                val list = kbService.getAllergyReactionList(kbManager, lang)
                val effectiveList = if (lang.lowercase().take(2) == "en") {
                    list.map { item ->
                        val tr = translationsRepo.get(item.code, "en")
                        if (!tr.isNullOrBlank()) item.copy(display = tr) else item
                    }
                } else list
                fuzzyTopN(q, effectiveList, n = 5)
            } catch (e: Throwable) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ searchSnomedReaction err: ${e.message}")
                emptyList()
            }
        }

        emitDone(
            actionId = actionId,
            toolName = "searchSnomedReaction",
            template = BgActionTemplates.KB_SEARCH_REACTION,
            startMs = tStart,
            value = "${candidates.size} candidats",
            detail = candidates.joinToString("\n") { "${it.code} · ${it.display}" },
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔍 searchSnomedReaction · q='$q' · n=${candidates.size}")
        return mapOf(
            "ok" to true,
            "query" to q,
            "count" to candidates.size,
            "candidates" to candidates.map { mapOf("code" to it.code, "display" to it.display, "system" to it.system) },
        )
    }

    @Tool(description = "Confirm the canonical SNOMED-CT code for the substance after you've picked it from search results (or directly if you're certain). The code/display will be persisted in the FHIR AllergyIntolerance.code field on save.")
    fun confirmSnomedCode(
        @ToolParam(description = "The chosen SNOMED-CT code from searchSnomedAllergyIntolerance results, e.g. '91936005'.") code: String,
        @ToolParam(description = "The display string matching the code, e.g. 'Allergie à la pénicilline'.") display: String,
    ): Map<String, Any> {
        val c = code.trim()
        val d = display.trim()
        draftRef.updateAndGet {
            it.copy(
                substanceCode = c,
                substanceDisplay = d,
                substanceSystem = "http://snomed.info/sct",
            )
        }
        emitSync(
            toolName = "confirmSnomedCode",
            template = BgActionTemplates.KB_CONFIRM_CODE,
            value = "SNOMED $c",
            detail = d,
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ confirmSnomedCode · $c · '$d'")
        return mapOf("ok" to true, "code" to c, "display" to d)
    }

    @Tool(description = "Confirm the canonical SNOMED-CT code for the reaction/manifestation. The code/display will be persisted in the FHIR AllergyIntolerance.reaction[0].manifestation field on save.")
    fun confirmReactionCode(
        @ToolParam(description = "The chosen SNOMED-CT code from searchSnomedReaction, e.g. '126485001'.") code: String,
        @ToolParam(description = "The display string matching the code, e.g. 'Urticaire'.") display: String,
    ): Map<String, Any> {
        val c = code.trim()
        val d = display.trim()
        draftRef.updateAndGet {
            it.copy(
                manifestationCode = c,
                manifestationDisplay = d,
                manifestationSystem = "http://snomed.info/sct",
            )
        }
        emitSync(
            toolName = "confirmReactionCode",
            template = BgActionTemplates.KB_CONFIRM_REACTION,
            value = "SNOMED $c",
            detail = d,
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ confirmReactionCode · $c · '$d'")
        return mapOf("ok" to true, "code" to c, "display" to d)
    }

    // =================================================================
    // FAMILY C — Cross-check + profile (2)
    // =================================================================

    @Tool(description = "Get a summary of the current patient profile: name, demographics, count of allergies/medications/conditions. Useful to know who you're collecting an allergy for, and to detect drug-allergy contraindications before saving.")
    fun getProfileSummary(): Map<String, Any> {
        val (actionId, tStart) = emitRunning(
            toolName = "getProfileSummary",
            template = BgActionTemplates.GET_PROFILE_SUMMARY,
        )

        val profileId = profileIdRef.get()
        val summary = if (profileId == null) {
            mapOf("ok" to false, "error" to "no_profile_bound")
        } else {
            runBlocking(Dispatchers.IO) {
                try {
                    val p = profilesRepo.loadProfile(profileId)
                    if (p == null) {
                        mapOf("ok" to false, "error" to "profile_not_found", "profile_id" to profileId)
                    } else {
                        mapOf(
                            "ok" to true,
                            "profile_id" to profileId,
                            "patient_first_name" to (p.p?.gn ?: ""),
                            "patient_last_name" to (p.p?.fn ?: ""),
                            "allergies_count" to (p.al?.size ?: 0),
                            "medications_count" to (p.md?.size ?: 0),
                            "conditions_count" to (p.cn?.size ?: 0),
                        )
                    }
                } catch (e: Throwable) {
                    mapOf("ok" to false, "error" to (e.message ?: "load_failed"))
                }
            }
        }

        val name = "${summary["patient_first_name"] ?: ""} ${summary["patient_last_name"] ?: ""}".trim()
        emitDone(
            actionId = actionId,
            toolName = "getProfileSummary",
            template = BgActionTemplates.GET_PROFILE_SUMMARY,
            startMs = tStart,
            value = name.ifBlank { "—" },
            detail = "Allergies: ${summary["allergies_count"]} · Médicaments: ${summary["medications_count"]} · Conditions: ${summary["conditions_count"]}",
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👤 getProfileSummary · $summary")
        return summary
    }

    @Tool(description = "Cross-check the current allergy draft (substance + category) against the patient's existing medications to detect potential drug-allergy contraindications. Call this BEFORE saveAllergyDraft if the category is 'medication'. Returns the list of medications that may conflict. If any match has high severity, you SHOULD then call triggerRedAlert from the medical tools.")
    fun checkCrossWithMedications(): Map<String, Any> {
        val (actionId, tStart) = emitRunning(
            toolName = "checkCrossWithMedications",
            template = BgActionTemplates.CROSS_CHECK_MEDICATIONS,
        )
        val draft = draftRef.get()
        val substance = draft.substance
        val profileId = profileIdRef.get()
        if (substance.isNullOrBlank() || profileId == null) {
            emitDone(
                actionId = actionId,
                toolName = "checkCrossWithMedications",
                template = BgActionTemplates.CROSS_CHECK_MEDICATIONS,
                startMs = tStart,
                value = "—",
                state = AgentActionState.FAILED,
            )
            return mapOf("ok" to false, "error" to "missing_substance_or_profile")
        }

        val (matches, totalMeds) = runBlocking(Dispatchers.IO) {
            try {
                val p = profilesRepo.loadProfile(profileId)
                val meds = p?.md ?: emptyList()
                val needle = substance.lowercase()
                // Simple substring match against med display names. Cross-class
                // checks are handled by triggerRedAlert downstream via xcheck.
                val hits = meds.filter { m ->
                    val display = (m.displayLabel ?: "").lowercase()
                    needle.isNotEmpty() && (display.contains(needle) || needle.contains(display))
                }
                Pair(hits, meds.size)
            } catch (e: Throwable) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ checkCrossWithMedications err: ${e.message}")
                Pair(emptyList<be.heyman.android.jemmapassdemo.qr.JMedication>(), 0)
            }
        }

        val matchCount = matches.size
        val detail = if (matchCount > 0) {
            "Conflits potentiels : $matchCount match(es) sur $totalMeds médicaments"
        } else {
            "Aucun conflit · $totalMeds médicaments scannés"
        }
        emitDone(
            actionId = actionId,
            toolName = "checkCrossWithMedications",
            template = BgActionTemplates.CROSS_CHECK_MEDICATIONS,
            startMs = tStart,
            value = if (matchCount > 0) "$matchCount conflit(s)" else "OK",
            detail = detail,
            state = if (matchCount > 0) AgentActionState.DONE else AgentActionState.DONE,
        )
        _events.tryEmit(
            AllergiesAgentEvent.CrossCheckCompleted(matchCount = matchCount, details = detail)
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩺 checkCrossWithMedications · matches=$matchCount/$totalMeds")
        return mapOf(
            "ok" to true,
            "substance" to substance,
            "total_medications" to totalMeds,
            "match_count" to matchCount,
        )
    }

    // =================================================================
    // FAMILY D — Interaction (2)
    // =================================================================

    @Tool(description = "Speak a short message to the user via on-device TTS. Use this to acknowledge what you understood, ask the next missing field, or confirm before saving. Keep messages SHORT (1-2 sentences) and natural — they will be vocalized. Do NOT include markdown, bullet points, or emojis.")
    fun playTTS(
        @ToolParam(description = "The text to speak, 1-2 sentences in the user's language (French).") text: String,
    ): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY playTTS · raw='${text.take(80)}'")
        val clean = text.trim()
        if (clean.isEmpty()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ playTTS · rejected · empty")
            return mapOf("ok" to false, "error" to "empty_text")
        }
        emitSync(
            toolName = "playTTS",
            template = BgActionTemplates.PLAY_TTS,
            value = if (clean.length <= 48) clean else clean.take(45) + "…",
            detail = clean,
        )
        _events.tryEmit(
            AllergiesAgentEvent.Narration(text = clean, lang = langRef.get() ?: "fr")
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔊 playTTS · ACCEPTED · '${clean.take(60)}'")
        return mapOf("ok" to true, "text_length" to clean.length)
    }

    @Tool(description = "Ask the user to pick from a list of choices (typically SNOMED candidates). For now this just narrates the choices via TTS and waits for the next user turn to speak the answer. Use AFTER searchSnomedAllergyIntolerance if the patient's verbal description is ambiguous.")
    fun askChoice(
        @ToolParam(description = "Question in French, e.g. 'Quelle pénicilline ?'") question: String,
        @ToolParam(description = "Comma-separated choices, e.g. 'Allergie à la pénicilline, Penicillin G, Pénicilline V'") choices: String,
    ): Map<String, Any> {
        val q = question.trim()
        val c = choices.trim()
        emitSync(
            toolName = "askChoice",
            template = BgActionTemplates.ASK_CHOICE,
            value = q,
            detail = c,
        )
        // Vocalize: "<question> Choix : 1. <a>, 2. <b>, 3. <c>"
        val items = c.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val spoken = buildString {
            append(q)
            if (items.isNotEmpty()) {
                append(" Choix : ")
                items.forEachIndexed { i, item ->
                    append("${i + 1}. $item")
                    if (i < items.size - 1) append(", ")
                }
                append(".")
            }
        }
        _events.tryEmit(AllergiesAgentEvent.Narration(text = spoken, lang = langRef.get() ?: "fr"))
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ❓ askChoice · q='$q' · ${items.size} choix")
        return mapOf("ok" to true, "question" to q, "choices_count" to items.size)
    }

    // =================================================================
    // FAMILY E — Commit (2)
    // =================================================================

    @Tool(description = "Save the current allergy draft to the patient profile and close the chat. Call this ONLY after: (1) the 4 IPS mandatory fields are populated (substance, manifestation, severity, onsetDate); (2) the user has explicitly confirmed (e.g. said 'ok', 'valide', 'sauvegarde'). Category is auto-inferred from KB and status defaults to Active — NEVER ask the user about these.")
    fun saveAllergyDraft(): Map<String, Any> {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛠 @Tool ENTRY saveAllergyDraft")
        // 🆕 Lot 14.5c32 — auto-default status if Jemma didn't set it.
        val current = draftRef.get()
        if (current.status.isNullOrBlank()) {
            draftRef.set(current.copy(status = "Active"))
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🟢 status auto-defaulted to 'Active' at save")
        }
        val draft = draftRef.get()
        if (!draft.isMinimallyComplete) {
            emitSync(
                toolName = "saveAllergyDraft",
                template = BgActionTemplates.SAVE_DRAFT,
                value = "Bloqué",
                detail = "Substance manquante",
            )
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ saveAllergyDraft · rejected · substance missing")
            return mapOf("ok" to false, "error" to "draft_incomplete", "missing_substance" to true)
        }
        emitSync(
            toolName = "saveAllergyDraft",
            template = BgActionTemplates.SAVE_DRAFT,
            value = draft.substance,
            detail = "${draft.filledCount}/${draft.mandatoryFieldCount} champs · SNOMED ${draft.substanceCode ?: "(pending)"}",
        )
        _events.tryEmit(AllergiesAgentEvent.SaveCommit())
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💾 saveAllergyDraft · ACCEPTED · substance='${draft.substance}' · filled=${draft.filledCount}/${draft.mandatoryFieldCount}")
        return mapOf(
            "ok" to true,
            "substance" to (draft.substance ?: ""),
            "snomed_code" to (draft.substanceCode ?: ""),
            "filled_fields" to draft.filledCount,
        )
    }

    @Tool(description = "Abort the allergy collection and close the chat without saving. Call this if the user explicitly says they want to cancel, abandon, or stop.")
    fun cancelAndExit(
        @ToolParam(description = "Brief reason, e.g. 'user requested cancel'") reason: String,
    ): Map<String, Any> {
        emitSync(
            toolName = "cancelAndExit",
            template = BgActionTemplates.CANCEL_AND_EXIT,
            value = reason.take(40),
        )
        _events.tryEmit(AllergiesAgentEvent.CancelAndExit(reason = reason))
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 cancelAndExit · '$reason'")
        return mapOf("ok" to true, "reason" to reason)
    }

    // =================================================================
    // Internal helpers
    // =================================================================

    /** Top-N fuzzy match against an IPS list, ranked by score. */
    private fun fuzzyTopN(query: String, list: List<AllergyReactionItem>, n: Int = 5): List<AllergyReactionItem> {
        if (list.isEmpty() || query.isEmpty()) return emptyList()
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return list
            .map { item -> item to scoreMatch(q, normalize(item.display)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }

    private fun scoreMatch(needleNorm: String, hayNorm: String): Int {
        if (hayNorm.isEmpty()) return 0
        // 3 = exact, 2 = needle whole word in hay, 1 = needle substring in hay
        if (hayNorm == needleNorm) return 100
        val needleRegex = Regex("""\b${Regex.escape(needleNorm)}\b""")
        if (needleRegex.containsMatchIn(hayNorm)) return 50
        if (hayNorm.contains(needleNorm)) return 25
        // Allow stripped prefix matches: "pénicilline" vs "allergie à la pénicilline"
        val strippedHay = hayNorm
            .replace(Regex("""^(allergie|intolerance)\s+(a\s+l'|a\s+la\s+|aux\s+|au\s+|a\s+)"""), "")
            .replace(Regex("""^(allergie|intolerance)\s+"""), "")
        if (strippedHay == needleNorm) return 90
        if (Regex("""\b${Regex.escape(needleNorm)}\b""").containsMatchIn(strippedHay)) return 40
        return 0
    }

    private fun normalize(s: String): String {
        var v = stripAccents(s).lowercase().trim()
        v = v.replace(Regex("""\s+"""), " ")
        return v
    }

    private fun stripAccents(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")

    private fun isValidIsoDate(s: String): Boolean {
        if (s.length != 10) return false
        if (s[4] != '-' || s[7] != '-') return false
        if (!s.substring(0, 4).all { it.isDigit() }) return false
        if (!s.substring(5, 7).all { it.isDigit() }) return false
        if (!s.substring(8, 10).all { it.isDigit() }) return false
        return true
    }

    private fun formatHumanDate(iso: String): String {
        // 2026-01-20 → "20 janvier 2026"
        if (!isValidIsoDate(iso)) return iso
        val parts = iso.split("-")
        val day = parts[2].toIntOrNull()?.toString() ?: parts[2]
        val month = when (parts[1]) {
            "01" -> "janvier"; "02" -> "février"; "03" -> "mars"; "04" -> "avril"
            "05" -> "mai"; "06" -> "juin"; "07" -> "juillet"; "08" -> "août"
            "09" -> "septembre"; "10" -> "octobre"; "11" -> "novembre"; "12" -> "décembre"
            else -> parts[1]
        }
        return "$day $month ${parts[0]}"
    }
}
