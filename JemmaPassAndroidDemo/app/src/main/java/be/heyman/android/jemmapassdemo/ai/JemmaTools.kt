/*
 * JemmaTools.kt — JEMMA Pass · JemmaAppDemo · v2.6.0
 *
 * The @Tool surface exposed to Gemma 4 (via LiteRT-LM's ToolSet machinery)
 * for the rescuer + forge use cases. Sixteen tools across four families :
 *
 *   ─── KB lookup ──────────────────────────────────────────────────────
 *     resolveDrug        — drug name (any lang) → ATC + RxNorm + display
 *     resolveAllergy     — allergen name → SNOMED + display + category
 *     resolveByCode      — code + system → concept (when code is known)
 *     searchCodes        — fuzzy FTS5 search (autocomplete-style)
 *
 *   ─── Interactions ───────────────────────────────────────────────────
 *     checkDdi           — two free-text drug names → DDI verdict
 *     checkDdiByAtc      — two ATC codes (fast path) → DDI verdict
 *     getAtcAncestors    — walk ATC class hierarchy (debug + class match)
 *
 *   ─── Focus profile (the "current victim/patient under review") ──────
 *     getFocusProfileSummary       — demographics + pillar counts
 *     getFocusProfileAllergies     — allergy entries with localized labels
 *     getFocusProfileMedications   — medication entries with localized labels
 *     getFocusProfileConditions    — condition entries with localized labels
 *     getFocusProfileImmunizations — vaccination history (FHIR-native pillar)
 *     getFocusProfileProcedures    — history of procedures (FHIR-native pillar)
 *     getFocusProfileDevices       — implants / medical devices (FHIR-native pillar)
 *     getFocusProfileResults       — lab / imaging results (FHIR-native pillar)
 *
 *   ─── Cross-check (the killer) ───────────────────────────────────────
 *     checkOneDrugAgainstFocusProfile — single drug name → full clinical
 *                                       collision report (allergies +
 *                                       meds + conditions)
 *     checkOneAtcAgainstFocusProfile  — same but ATC fast-path
 *
 *   ─── UI bridge (event-emitting, side-effecting on the screen) ───────
 *     triggerRedAlert    — push a JemmaToolEvent.RedAlert
 *     triggerToast       — push a JemmaToolEvent.Toast
 *
 *   ─── Utility ────────────────────────────────────────────────────────
 *     getCurrentDateTime — ISO 8601 local time (timezone aware)
 *
 *
 * STATEFUL FOCUS PROFILE
 * ──────────────────────
 *
 * The "focus profile" is the patient/victim that the current Gemma session
 * is reasoning about. It's set by the consuming UI before the Gemma turn :
 *
 *   • Rescuer scanning a victim's potential drug :
 *       jemmaTools.bind(victimProfile)
 *       gemma.generate("Is Augmentin safe for this patient ?")
 *
 *   • Forge wizard collecting the active user's own data :
 *       jemmaTools.bind(activeUserProfile)
 *       gemma.generate("Add this dictation as an allergy entry.")
 *
 * If no focus profile is set, the four `getFocusProfile*` tools return an
 * empty result and log a warning. Cross-check tools return a zero-hit
 * `CrossCheckResult` whose `candidateAtc=""` signals "no focus".
 *
 * Thread safety : `bind` writes are guarded by an AtomicReference so the
 * tools can read concurrently with a UI thread mutating the focus.
 *
 *
 * SUSPEND BRIDGING
 * ────────────────
 *
 * LiteRT-LM invokes @Tool methods synchronously from its native thread.
 * Our KB primitives are `suspend fun` (they touch Dispatchers.IO). The
 * conventional shim — see Edge Gallery's `AgentTools.kt#L57` — is
 * `runBlocking(Dispatchers.IO) { ... }`. This blocks the LiteRT-LM worker
 * for the duration of the SQL hit (≤ 50 ms in practice), then resumes the
 * model generation.
 *
 *
 * RETURN TYPES
 * ────────────
 *
 * All tools return `Map<String, Any>` (the JSON-serializable Kotlin
 * equivalent that LiteRT-LM marshals to the model). Convention :
 *   • `ok: Boolean`          — true if the tool succeeded
 *   • `reason: String`       — when `ok=false`, a short explanation
 *   • `lang: String`         — the locale code used for displays
 *   • domain-specific fields after that
 *
 * Nullable values are coerced to empty strings or "0" to keep the JSON
 * shape stable — Gemma's tool-call parser is intolerant of `null`.
 */
package be.heyman.android.jemmapassdemo.ai

import android.util.Log
import be.heyman.android.jemmapassdemo.kb.AllergyCriticality
import be.heyman.android.jemmapassdemo.kb.AllergyHit
import be.heyman.android.jemmapassdemo.kb.CrossCheckResult
import be.heyman.android.jemmapassdemo.kb.CrossSeverity
import be.heyman.android.jemmapassdemo.kb.DDIResult
import be.heyman.android.jemmapassdemo.kb.DdiHit
import be.heyman.android.jemmapassdemo.kb.DrugDiseaseHit
import be.heyman.android.jemmapassdemo.kb.KbCrossCheck
import be.heyman.android.jemmapassdemo.kb.KbSearchResult
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.ResolvedConcept
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JCondition
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

private const val TAG = "JEMMA-TOOLS"

@Singleton
class JemmaTools @Inject constructor(
    private val kb: KnowledgeBaseService,
    private val xcheck: KbCrossCheck,
) : ToolSet {

    // ─────────────────────────────────────────────────────────────────
    // Focus profile + event stream
    // ─────────────────────────────────────────────────────────────────

    private val focusRef = AtomicReference<JemmaProfileJ?>(null)

    /** Suspended buffered event stream. Replay = 0 (consumers see only
     *  events emitted after they subscribed) ; extraBufferCapacity = 32
     *  so a slow consumer doesn't block the tool thread. */
    private val _events = MutableSharedFlow<JemmaToolEvent>(
        replay = 0,
        extraBufferCapacity = 32,
    )
    val events: SharedFlow<JemmaToolEvent> = _events.asSharedFlow()

    /** Set the profile the current Gemma session should reason about. */
    fun bind(profile: JemmaProfileJ?) {
        focusRef.set(profile)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 bind focus profile · " +
            "patient=${profile?.p?.gn ?: "(none)"} ${profile?.p?.fn ?: ""} · " +
            "al=${profile?.al?.size ?: 0} md=${profile?.md?.size ?: 0} cn=${profile?.cn?.size ?: 0}")
    }

    /** Drop the focus profile. Call when the Gemma session ends. */
    fun unbind() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎯 unbind focus profile")
        focusRef.set(null)
    }

    /** Returns the active focus profile (snapshot) or null. */
    fun focus(): JemmaProfileJ? = focusRef.get()

    /** Helper for the UI : emit a pipeline-step frame on the event bus. */
    fun emitStep(event: JemmaToolEvent) {
        _events.tryEmit(event).also {
            if (!it) Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ event buffer full, drop : $event")
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // KB lookup — Family A (4 tools)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Resolve a drug name (in any supported language — French, English,
     * Japanese, brand, INN, RxNorm CUI, ATC) to its canonical KB entry.
     * Returns the ATC code, RxNorm CUI, canonical English display, and
     * a localized display in the device language.
     *
     * Gemma should call this BEFORE [checkDdi] when it has only a name
     * and is unsure whether the KB recognizes it.
     */
    @Tool(description = "Resolve a drug name (any language, brand, INN, RxNorm or ATC code) to its KB entry. Returns ATC, RxNorm, canonical and localized display. Always call this first when only a free-text drug name is available before invoking checkDdi or any cross-check tool.")
    fun resolveDrug(
        @ToolParam(description = "Drug name in any supported language, or a code (RxNorm CUI, ATC).") name: String
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val resolved = kb.resolveDrug(name)
        val concept = resolved.concept
        val lang = currentLang()

        val out: Map<String, Any> = if (concept == null) {
            mapOf(
                "ok" to false,
                "reason" to "drug_not_found_in_kb",
                "query" to name,
            )
        } else {
            mapOf(
                "ok" to true,
                "matched_on" to resolved.matchedOn,
                "query" to name,
                "code" to (concept.code),
                "system" to concept.system,
                "atc" to (concept.atcCode ?: ""),
                "all_atcs" to concept.allAtcCodes,
                "rxnorm_cui" to (concept.rxnormCui ?: ""),
                "snomed" to (concept.snomedCode ?: ""),
                "primary_display" to concept.primaryDisplay,
                "localized_display" to kb.pickLocalizedDisplay(concept, lang),
                "lang" to lang,
                "category" to (concept.category ?: ""),
                "ips_validated" to concept.ipsValidated,
            )
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔎 resolveDrug('$name') → " +
            "${if (concept != null) concept.atcCode else "NOT_FOUND"} · ${System.currentTimeMillis() - tStart}ms")
        out
    }

    /**
     * Resolve an allergen name to its SNOMED CT code and category. The
     * KB carries 328 IPS-validated allergen valueset entries pre-translated
     * in FR / EN / JA.
     */
    @Tool(description = "Resolve an allergen common name to its SNOMED-CT code, category (Chemical_Allergen, Protein_Allergen, Food, etc.) and localized display. Use when the patient or rescuer mentions an allergy by name and you need a structured code for downstream cross-checks.")
    fun resolveAllergy(
        @ToolParam(description = "Allergen common name (any supported language).") name: String
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val resolved = kb.resolveAllergy(name)
        val concept = resolved.concept
        val lang = currentLang()

        val out: Map<String, Any> = if (concept == null) {
            mapOf("ok" to false, "reason" to "allergen_not_found_in_kb", "query" to name)
        } else {
            mapOf(
                "ok" to true,
                "matched_on" to resolved.matchedOn,
                "query" to name,
                "code" to concept.code,
                "system" to concept.system,
                "snomed" to (concept.snomedCode ?: ""),
                "atc" to (concept.atcCode ?: ""),
                "primary_display" to concept.primaryDisplay,
                "localized_display" to kb.pickLocalizedDisplay(concept, lang),
                "lang" to lang,
                "category" to (concept.category ?: ""),
                "ips_validated" to concept.ipsValidated,
            )
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩹 resolveAllergy('$name') → " +
            "${if (concept != null) concept.snomedCode ?: concept.code else "NOT_FOUND"} · ${System.currentTimeMillis() - tStart}ms")
        out
    }

    /**
     * Resolve a known code (SNOMED, RxNorm, or UMLS) to its full KB entry.
     * Faster than [resolveDrug] / [resolveAllergy] because we skip the
     * name-search heuristics.
     */
    @Tool(description = "Resolve a known terminology code (SNOMED, RxNorm CUI, UMLS) to its KB entry. The system parameter must be 'snomed', 'rxnorm', or 'umls'. Use when you already have a structured code and just need the localized display + cross-system mappings.")
    fun resolveByCode(
        @ToolParam(description = "The code value, e.g. '91936005' or 'C0040028'.") code: String,
        @ToolParam(description = "Terminology system. Accepted values: snomed, rxnorm, umls.") system: String,
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val systemUri = when (system.lowercase().trim()) {
            "snomed", "snomed-ct", "sct" -> KnowledgeBaseService.SYSTEM_SNOMED
            "rxnorm", "rx" -> KnowledgeBaseService.SYSTEM_RXNORM
            "umls", "cui" -> KnowledgeBaseService.SYSTEM_UMLS
            else -> system
        }
        val resolved = kb.resolveByCode(code, systemUri)
        val concept = resolved.concept
        val lang = currentLang()

        val out: Map<String, Any> = if (concept == null) {
            mapOf("ok" to false, "reason" to "code_not_found", "query_code" to code, "query_system" to system)
        } else {
            mapOf(
                "ok" to true,
                "code" to concept.code,
                "system" to concept.system,
                "atc" to (concept.atcCode ?: ""),
                "rxnorm" to (concept.rxnormCui ?: ""),
                "snomed" to (concept.snomedCode ?: ""),
                "primary_display" to concept.primaryDisplay,
                "localized_display" to kb.pickLocalizedDisplay(concept, lang),
                "lang" to lang,
                "category" to (concept.category ?: ""),
            )
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔑 resolveByCode($code, $system) → " +
            "${if (concept != null) "OK" else "NOT_FOUND"} · ${System.currentTimeMillis() - tStart}ms")
        out
    }

    /**
     * Fuzzy full-text search across the KB (terminology + drugs). Useful
     * for autocomplete-style flows where the user is typing.
     */
    @Tool(description = "Fuzzy full-text search across the KB terminology (drugs, allergens, conditions). Returns up to 5 ranked matches. Use when the input is partial or ambiguous and you want to suggest options to the user rather than commit to a single resolution.")
    fun searchCodes(
        @ToolParam(description = "Search query (partial name, any language).") query: String,
        @ToolParam(description = "Optional category filter: Medication, Condition, Procedure, Device, Chemical_Allergen, Protein_Allergen, Food. Empty string for no filter.") category: String,
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val lang = currentLang()
        val cleanCategory = category.takeIf { it.isNotBlank() }
        val result = kb.searchCodes(query, lang = lang, categoryFilter = cleanCategory, maxResults = 5)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔍 searchCodes('$query', cat=$cleanCategory) → " +
            "${(result as? KbSearchResult.Success)?.hits?.size ?: 0} hits · ${System.currentTimeMillis() - tStart}ms")
        when (result) {
            is KbSearchResult.Success -> mapOf(
                "ok" to true,
                "query" to query,
                "lang" to lang,
                "count" to result.hits.size,
                "used_fts5" to result.usedFts5,
                "latency_ms" to result.latencyMs,
                "results" to result.hits.map {
                    mapOf(
                        "code" to it.concept.code,
                        "system" to it.concept.system,
                        "display" to it.display,
                        "category" to (it.concept.category ?: ""),
                        "atc" to (it.concept.atcCode ?: ""),
                        "snomed" to (it.concept.snomedCode ?: ""),
                        "rxnorm" to (it.concept.rxnormCui ?: ""),
                        "matched_via" to it.matchedVia,
                    )
                },
            )
            is KbSearchResult.Error -> mapOf(
                "ok" to false,
                "reason" to result.reason,
                "query" to query,
                "latency_ms" to result.latencyMs,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Interactions — Family B (3 tools)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Check whether two drugs (by free-text name) have a documented
     * interaction in DDInter 2.0. Resolves both names to ATC first.
     */
    @Tool(description = "Check for a documented drug-drug interaction (DDInter 2.0) between two drugs given by name. Resolves each name to ATC then queries v_ddi_emergency (Major + Moderate). ALWAYS call this BEFORE answering any question that mentions two or more drugs being combined.")
    fun checkDdi(
        @ToolParam(description = "First drug — common name (any language), brand, INN, or RxNorm/ATC code.") drugA: String,
        @ToolParam(description = "Second drug — same format as drugA.") drugB: String,
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val result = kb.queryDDI(drugA, drugB)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊⚡ checkDdi('$drugA', '$drugB') → " +
            "${result::class.simpleName} · ${System.currentTimeMillis() - tStart}ms")
        ddiResultToMap(result, drugA, drugB)
    }

    /**
     * Fast path : check DDI between two ATC codes directly, skipping
     * name resolution. Use this when you already have ATCs (e.g. after
     * [resolveDrug] returned them).
     */
    @Tool(description = "Fast-path DDI lookup when both drugs are already known by ATC code. Skips name resolution. Prefer this over checkDdi after you have called resolveDrug and obtained the ATC.")
    fun checkDdiByAtc(
        @ToolParam(description = "First ATC code (e.g. 'B01AA03' for Warfarin).") atcA: String,
        @ToolParam(description = "Second ATC code (e.g. 'M01AE01' for Ibuprofen).") atcB: String,
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val result = kb.queryDDIByAtc(atcA, atcB)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💊⚡ checkDdiByAtc($atcA, $atcB) → " +
            "${result::class.simpleName} · ${System.currentTimeMillis() - tStart}ms")
        ddiResultToMap(result, atcA, atcB)
    }

    /**
     * Walk the ATC class hierarchy for a code (5 levels : anatomic →
     * therapeutic → pharmacological → chemical subgroup → chemical
     * substance). Used by Gemma to explain why a cross-class allergy
     * collision happened ("amoxicillin is a J01CA, you're allergic to
     * the J01C beta-lactam class").
     */
    @Tool(description = "Walk the ATC hierarchy for a code (returns the 1-5 ancestor classes). Use to explain class-level allergy matches or to find a broader therapeutic family.")
    fun getAtcAncestors(
        @ToolParam(description = "ATC code, e.g. 'J01CA04' (amoxicillin).") atc: String
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val nodes = kb.getAtcAncestors(atc)
        val lang = currentLang()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌳 getAtcAncestors($atc) → " +
            "${nodes.size} levels · ${System.currentTimeMillis() - tStart}ms")
        mapOf(
            "ok" to true,
            "query" to atc,
            "lang" to lang,
            "count" to nodes.size,
            "ancestors" to nodes.map {
                val localized = when (lang) {
                    "fr" -> it.nameFr ?: it.nameEn
                    "ja", "jp" -> it.nameJp ?: it.nameEn
                    else -> it.nameEn
                }
                mapOf(
                    "code" to it.atcCode,
                    "parent" to (it.parentAtc ?: ""),
                    "level" to it.level,
                    "name" to localized,
                    "name_en" to it.nameEn,
                )
            },
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Focus profile — Family C (4 tools)
    // ─────────────────────────────────────────────────────────────────

    @Tool(description = "Get a compact summary of the focus profile (the patient currently under review): demographics + counts of each clinical pillar. Always prefer this over getFocusProfileAllergies/Medications/Conditions when only an overview is needed.")
    fun getFocusProfileSummary(): Map<String, Any> {
        val p = focusRef.get() ?: return profileMissingMap()
        val patient = p.p
        return mapOf(
            "ok" to true,
            "has_patient" to (patient != null),
            "given_name" to (patient?.gn ?: ""),
            "family_name" to (patient?.fn ?: ""),
            "sex" to (patient?.gs ?: ""),
            "birth_date" to (patient?.bd ?: ""),
            "blood_type" to (patient?.bt ?: ""),
            "nationality" to (patient?.nat ?: ""),
            "allergies_count" to p.al.size,
            "medications_count" to p.md.size,
            "conditions_count" to p.cn.size,
            "immunizations_count" to p.im.size,
            "procedures_count" to p.pr.size,
            "devices_count" to p.dv.size,
            "results_count" to p.rs.size,
            "contacts_count" to (patient?.ct?.size ?: 0),
        )
    }

    @Tool(description = "List the allergies in the focus profile with their localized display, code, criticality, and clinical status. Each row has the SNOMED-or-RxNorm code Gemma can pass to checkDdi if a new drug is being considered.")
    fun getFocusProfileAllergies(): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val rows = mutableListOf<Map<String, Any>>()
        for (a in p.al) rows.add(allergyToMap(a, lang))
        mapOf("ok" to true, "lang" to lang, "count" to rows.size, "allergies" to rows)
    }

    @Tool(description = "List the medications currently taken by the focus profile, with localized display, code, dose, timing, route. The 'atc' field for each row should be passed to checkDdi / checkDdiByAtc when considering a new drug.")
    fun getFocusProfileMedications(): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val rows = mutableListOf<Map<String, Any>>()
        for (m in p.md) rows.add(medicationToMap(m, lang))
        mapOf("ok" to true, "lang" to lang, "count" to rows.size, "medications" to rows)
    }

    @Tool(description = "List the active medical conditions of the focus profile, with localized display labels. Used for drug-disease contraindication checks.")
    fun getFocusProfileConditions(): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val rows = mutableListOf<Map<String, Any>>()
        for (c in p.cn) rows.add(conditionToMap(c, lang))
        mapOf("ok" to true, "lang" to lang, "count" to rows.size, "conditions" to rows)
    }

    @Tool(description = "List the immunizations (vaccination history) of the focus profile: vaccine label, SNOMED/CVX code, date (YYYY-MM-DD or unknown), dose number and status. Use it to answer questions such as 'is the patient vaccinated against tetanus?' or to advise a booster after an injury.")
    fun getFocusProfileImmunizations(): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val rows = mutableListOf<Map<String, Any>>()
        for (im in p.im) {
            val label = be.heyman.android.jemmapassdemo.pillars.IpsVaccineCatalog.getDisplay(im.c, lang)
                ?: im.displayLabel?.takeIf { it.isNotBlank() }
                ?: im.c.orEmpty()
            rows.add(
                mapOf(
                    "vaccine" to label,
                    "code" to im.c.orEmpty(),
                    "system" to (im.codeSystem ?: "http://snomed.info/sct"),
                    "date" to (im.date ?: "unknown"),
                    "dose_number" to (im.doseNumber ?: 0),
                    "status" to (im.status ?: "completed"),
                    "note" to im.d.orEmpty(),
                )
            )
        }
        mapOf("ok" to true, "lang" to lang, "count" to rows.size, "immunizations" to rows)
    }

    @Tool(description = "List the history of procedures (surgeries, interventions, major exams) of the focus profile: label, SNOMED code, date (YYYY-MM-DD or unknown) and status. Use it to answer 'has the patient had surgery?' or to flag prior operations relevant to an emergency (e.g. appendectomy, bypass, cesarean).")
    fun getFocusProfileProcedures(): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val rows = mutableListOf<Map<String, Any>>()
        for (pr in p.pr) {
            val label = be.heyman.android.jemmapassdemo.pillars.IpsProcedureCatalog.getDisplay(pr.c, lang)
                ?: pr.displayLabel?.takeIf { it.isNotBlank() }
                ?: pr.c.orEmpty()
            rows.add(
                mapOf(
                    "procedure" to label,
                    "code" to pr.c.orEmpty(),
                    "system" to (pr.codeSystem ?: "http://snomed.info/sct"),
                    "date" to (pr.date ?: "unknown"),
                    "status" to (pr.status ?: "completed"),
                    "note" to pr.d.orEmpty(),
                )
            )
        }
        mapOf("ok" to true, "lang" to lang, "count" to rows.size, "procedures" to rows)
    }

    @Tool(description = "List the medical devices and implants of the focus profile (pacemaker, defibrillator, stent, prosthesis, insulin pump, hearing aid…): label, SNOMED code, in-use date, status (active / inactive) and note. Critical for MRI safety, defibrillation and emergency care.")
    fun getFocusProfileDevices(): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val rows = mutableListOf<Map<String, Any>>()
        for (dv in p.dv) {
            val label = be.heyman.android.jemmapassdemo.pillars.IpsDeviceCatalog.getDisplay(dv.c, lang)
                ?: dv.displayLabel?.takeIf { it.isNotBlank() }
                ?: dv.c.orEmpty()
            rows.add(
                mapOf(
                    "device" to label,
                    "code" to dv.c.orEmpty(),
                    "system" to (dv.codeSystem ?: "http://snomed.info/sct"),
                    "since" to (dv.date ?: "unknown"),
                    "status" to (dv.status ?: "active"),
                    "note" to dv.d.orEmpty(),
                )
            )
        }
        mapOf("ok" to true, "lang" to lang, "count" to rows.size, "devices" to rows)
    }

    @Tool(description = "List the diagnostic results of the focus profile (lab tests such as potassium, hemoglobin, eGFR, HbA1c, LDL, blood group; imaging conclusions): test label, LOINC code, value with UCUM unit, interpretation (H high, L low, HH/LL critical, N normal), reference range and date. Use it to check renal function before dosing, anemia, blood group for transfusion, or abnormal values.")
    fun getFocusProfileResults(): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val rows = mutableListOf<Map<String, Any>>()
        for (rs in p.rs) {
            val label = be.heyman.android.jemmapassdemo.pillars.IpsResultCatalog.getDisplay(rs.c, lang)
                ?: rs.displayLabel?.takeIf { it.isNotBlank() }
                ?: rs.c.orEmpty()
            rows.add(
                mapOf(
                    "test" to label,
                    "code" to rs.c.orEmpty(),
                    "system" to (rs.codeSystem ?: "http://loinc.org"),
                    "value" to (be.heyman.android.jemmapassdemo.ips.IpsBloodGroup.labelFromSnomed(rs.valueCode) ?: rs.value.orEmpty()),
                    "unit" to rs.unit.orEmpty(),
                    "interpretation" to rs.interpretation.orEmpty(),
                    "reference_range" to rs.referenceRange.orEmpty(),
                    "date" to (rs.date ?: "unknown"),
                    "category" to (rs.category ?: "laboratory"),
                    "status" to (rs.status ?: "final"),
                    "note" to rs.d.orEmpty(),
                )
            )
        }
        mapOf("ok" to true, "lang" to lang, "count" to rows.size, "results" to rows)
    }

    // ─────────────────────────────────────────────────────────────────
    // Cross-check — Family D (2 tools, the killers)
    // ─────────────────────────────────────────────────────────────────

    /**
     * **THE KILLER TOOL.** Given a candidate drug name (anything from a
     * blister OCR to a verbal mention), check it against the entire
     * focus profile : allergies, current medications (DDI), and known
     * conditions (contraindications). Returns a structured verdict
     * suitable for both Gemma's reasoning and the UI alert banner.
     */
    @Tool(description = "MASTER cross-check: given a candidate drug name, check it against ALL three pillars of the focus profile (allergies, current medications for DDI, and known conditions for contraindications). Returns a structured collision report. Call this whenever the rescuer scans a new medication or considers giving a drug to the patient. This is the single most useful tool for clinical safety.")
    fun checkOneDrugAgainstFocusProfile(
        @ToolParam(description = "Candidate drug — common name (any language), brand, INN, or RxNorm/ATC code.") drugName: String
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val tStart = System.currentTimeMillis()
        val result = xcheck.checkOneDrugAgainstProfile(drugName, p.al, p.md, p.cn, lang)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚨 checkOneDrugAgainstFocusProfile('$drugName') → " +
            "🩹${result.allergyHits.size} 💊${result.ddiHits.size} 🦠${result.drugDiseaseHits.size} · " +
            "${System.currentTimeMillis() - tStart}ms")
        crossCheckResultToMap(result, drugName, lang)
    }

    /**
     * Fast-path version of [checkOneDrugAgainstFocusProfile] when the
     * candidate is already known by ATC. Skips drug name resolution.
     */
    @Tool(description = "Fast-path cross-check when the candidate drug is already known by ATC code (e.g. you already called resolveDrug and got the ATC back). Same returned shape as checkOneDrugAgainstFocusProfile.")
    fun checkOneAtcAgainstFocusProfile(
        @ToolParam(description = "Candidate drug ATC code (e.g. 'J01CR02' for amoxicillin+clavulanate).") atc: String,
        @ToolParam(description = "Localized display label for the candidate (used in the UI report).") display: String,
    ): Map<String, Any> = runBlocking(Dispatchers.IO) {
        val p = focusRef.get() ?: return@runBlocking profileMissingMap()
        val lang = currentLang()
        val tStart = System.currentTimeMillis()

        // Resolve the ATC → concept to get allAtcCodes for the matcher.
        val resolved = kb.resolveDrug(atc).concept
        val allAtcs = resolved?.allAtcCodes ?: listOf(atc)
        val resolvedDisplay = resolved?.let { kb.pickLocalizedDisplay(it, lang) } ?: display

        val allergyHits = xcheck.checkOneAtcAgainstAllergies(p.al, atc, allAtcs, resolvedDisplay, lang)
        val ddiHits = xcheck.checkOneAtcAgainstMedications(p.md, atc, allAtcs, resolvedDisplay, lang = lang)
        val drugDiseaseHits = xcheck.checkOneAtcAgainstConditions(p.cn, atc, resolvedDisplay, lang)

        val bundle = CrossCheckResult(
            candidateAtc = atc,
            candidateDisplay = resolvedDisplay,
            allergyHits = allergyHits,
            ddiHits = ddiHits,
            drugDiseaseHits = drugDiseaseHits,
            totalDurationMs = System.currentTimeMillis() - tStart,
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🚨 checkOneAtcAgainstFocusProfile($atc) → " +
            "🩹${bundle.allergyHits.size} 💊${bundle.ddiHits.size} 🦠${bundle.drugDiseaseHits.size} · ${bundle.totalDurationMs}ms")
        crossCheckResultToMap(bundle, atc, lang)
    }

    // ─────────────────────────────────────────────────────────────────
    // UI bridge — Family E (2 tools, event-emitting)
    // ─────────────────────────────────────────────────────────────────

    @Tool(description = "Trigger a FULL-SCREEN RED alert with sound. ONLY use for genuinely critical findings: Major DDIs, severe (HIGH criticality) allergy matches, or contraindicated medications. Do NOT use for soft suggestions or unclear inputs — those should be a toast.")
    fun triggerRedAlert(
        @ToolParam(description = "Short alert title, 3-6 words.") title: String,
        @ToolParam(description = "One-or-two-sentence explanation in the user's language.") body: String,
    ): Map<String, Any> {
        val event = JemmaToolEvent.RedAlert(title = title, body = body)
        _events.tryEmit(event)
        Log.w(TAG, "[t=${System.currentTimeMillis()}] 🚨 RED ALERT · '$title' · '$body'")
        return mapOf("ok" to true, "kind" to "red_alert", "title" to title)
    }

    @Tool(description = "Show a non-blocking toast message. Use for minor findings, status updates, or to acknowledge a resolved drug. The severity parameter must be 'info' (default), 'warning', or 'critical'.")
    fun triggerToast(
        @ToolParam(description = "Toast message, ≤ 80 characters.") message: String,
        @ToolParam(description = "Severity: info, warning, or critical.") severity: String,
    ): Map<String, Any> {
        val sev = when (severity.lowercase().trim()) {
            "critical", "crit", "error" -> ToastSeverity.CRITICAL
            "warning", "warn" -> ToastSeverity.WARNING
            else -> ToastSeverity.INFO
        }
        val event = JemmaToolEvent.Toast(message = message, severity = sev)
        _events.tryEmit(event)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔔 TOAST [$sev] · '$message'")
        return mapOf("ok" to true, "kind" to "toast", "severity" to sev.name.lowercase())
    }

    // ─────────────────────────────────────────────────────────────────
    // Utility — Family F (1 tool)
    // ─────────────────────────────────────────────────────────────────

    @Tool(description = "Get the current local date and time in ISO 8601 format. Useful for prescription expiration, missed doses, time-since-vaccination computations.")
    fun getCurrentDateTime(): Map<String, Any> {
        val now = LocalDateTime.now()
        val iso = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return mapOf(
            "ok" to true,
            "iso_local" to iso,
            "epoch_ms" to System.currentTimeMillis(),
            "lang" to currentLang(),
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────

    private fun currentLang(): String = Locale.getDefault().language.lowercase().take(2)

    private fun profileMissingMap(): Map<String, Any> = mapOf(
        "ok" to false,
        "reason" to "no_focus_profile",
        "hint" to "call jemmaTools.bind(profile) from the consuming UI before invoking Gemma.",
    )

    private suspend fun allergyToMap(a: JAllergy, lang: String): Map<String, Any> {
        val concept = a.c?.let { kb.resolveByCode(it, KnowledgeBaseService.SYSTEM_SNOMED).concept }
            ?: a.c?.let { kb.resolveAllergy(it).concept }
            ?: a.displayLabel?.let { kb.resolveAllergy(it).concept }
        val display = concept?.let { kb.pickLocalizedDisplay(it, lang) } ?: a.displayLabel ?: a.c ?: "?"
        return mapOf(
            "code" to (a.c ?: ""),
            "display" to display,
            "criticality" to (a.s ?: ""),
            "status" to (a.st ?: ""),
            "mechanism" to (a.m ?: ""),
            "atc" to (concept?.atcCode ?: ""),
            "category" to (concept?.category ?: ""),
        )
    }

    private suspend fun medicationToMap(m: JMedication, lang: String): Map<String, Any> {
        val concept = m.c?.let { kb.resolveByCode(it, KnowledgeBaseService.SYSTEM_RXNORM).concept }
            ?: m.c?.let { kb.resolveDrug(it).concept }
            ?: m.displayLabel?.let { kb.resolveDrug(it).concept }
        val display = concept?.let { kb.pickLocalizedDisplay(it, lang) } ?: m.displayLabel ?: m.c ?: "?"
        return mapOf(
            "code" to (m.c ?: ""),
            "display" to display,
            "dose_value" to (m.v ?: ""),
            "dose_unit" to (m.u ?: ""),
            "timing" to (m.t ?: ""),
            "route" to (m.r ?: ""),
            "atc" to (concept?.atcCode ?: ""),
            "all_atcs" to (concept?.allAtcCodes ?: emptyList<String>()),
            "rxnorm_cui" to (concept?.rxnormCui ?: ""),
        )
    }

    private suspend fun conditionToMap(c: JCondition, lang: String): Map<String, Any> {
        val concept = c.c?.let { kb.resolveByCode(it, KnowledgeBaseService.SYSTEM_SNOMED).concept }
        val display = concept?.let { kb.pickLocalizedDisplay(it, lang) } ?: c.displayLabel ?: c.c ?: "?"
        return mapOf(
            "code" to (c.c ?: ""),
            "display" to display,
            "status" to (c.st ?: ""),
            "severity" to (c.s ?: ""),
        )
    }

    private fun ddiResultToMap(result: DDIResult, qA: String, qB: String): Map<String, Any> = when (result) {
        is DDIResult.Found -> mapOf(
            "ok" to true,
            "found" to true,
            "severity" to result.severity.label,
            "mechanism" to (result.mechanism ?: ""),
            "description" to (result.description ?: ""),
            "management" to (result.management ?: ""),
            "alternative_atc" to (result.alternativeAtc ?: ""),
            "drug_a_resolved" to result.drugAResolved,
            "drug_b_resolved" to result.drugBResolved,
            "source" to result.sourceDatabase,
            "query_ms" to result.queryDurationMs,
            "fuzzy" to result.fuzzyMatch,
        )
        is DDIResult.None -> mapOf(
            "ok" to true,
            "found" to false,
            "drug_a_resolved" to result.drugAResolved,
            "drug_b_resolved" to result.drugBResolved,
            "query_ms" to result.queryDurationMs,
        )
        is DDIResult.Error -> mapOf(
            "ok" to false,
            "reason" to result.reason,
            "query_a" to qA,
            "query_b" to qB,
            "query_ms" to result.queryDurationMs,
        )
    }

    private fun crossCheckResultToMap(r: CrossCheckResult, query: String, lang: String): Map<String, Any> =
        mapOf(
            "ok" to (r.candidateAtc.isNotEmpty()),
            "reason" to (if (r.candidateAtc.isEmpty()) "candidate_not_resolved" else ""),
            "query" to query,
            "candidate_atc" to r.candidateAtc,
            "candidate_display" to r.candidateDisplay,
            "lang" to lang,
            "duration_ms" to r.totalDurationMs,
            "is_clean" to r.isClean,
            "has_major" to r.hasMajor,
            "total_hits" to r.totalHits,
            "allergy_hits" to r.allergyHits.map { allergyHitToMap(it) },
            "ddi_hits" to r.ddiHits.map { ddiHitToMap(it) },
            "drug_disease_hits" to r.drugDiseaseHits.map { drugDiseaseHitToMap(it) },
        )

    private fun allergyHitToMap(h: AllergyHit): Map<String, Any> = mapOf(
        "allergy_display" to h.allergyDisplay,
        "allergy_code" to h.allergyCode,
        "matched_on" to h.matchedOn,
        "candidate_atc" to h.candidateAtc,
        "candidate_display" to h.candidateDisplay,
        "criticality" to (when (h.criticality) {
            AllergyCriticality.HIGH -> "HIGH"
            AllergyCriticality.LOW -> "LOW"
            AllergyCriticality.UNABLE_TO_ASSESS -> "UNKNOWN"
        }),
    )

    private fun ddiHitToMap(h: DdiHit): Map<String, Any> = mapOf(
        "existing_med_display" to h.existingMedDisplay,
        "existing_med_atc" to h.existingMedAtc,
        "candidate_atc" to h.candidateAtc,
        "candidate_display" to h.candidateDisplay,
        "severity" to crossSeverityName(h.severity),
        "mechanism" to (h.mechanism ?: ""),
        "description" to (h.description ?: ""),
        "management" to (h.management ?: ""),
        "alternative_atc" to (h.alternativeAtc ?: ""),
        "query_ms" to h.queryDurationMs,
    )

    private fun drugDiseaseHitToMap(h: DrugDiseaseHit): Map<String, Any> = mapOf(
        "condition_display" to h.conditionDisplay,
        "condition_code" to (h.conditionCode ?: ""),
        "candidate_atc" to h.candidateAtc,
        "candidate_display" to h.candidateDisplay,
        "severity" to crossSeverityName(h.severity),
        "description" to (h.description ?: ""),
        "management" to (h.management ?: ""),
        "disease_name_matched" to h.diseaseNameMatched,
    )

    private fun crossSeverityName(s: CrossSeverity): String = when (s) {
        CrossSeverity.MAJOR -> "Major"
        CrossSeverity.MODERATE -> "Moderate"
        CrossSeverity.MINOR -> "Minor"
        CrossSeverity.UNKNOWN -> "Unknown"
        CrossSeverity.NONE -> "None"
    }
}
