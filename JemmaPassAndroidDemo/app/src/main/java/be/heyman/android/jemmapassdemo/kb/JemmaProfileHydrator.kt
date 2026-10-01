/*
 * JemmaProfileHydrator.kt — v2.2.16.4 (rewrite for schema 2.0-omnis)
 *
 * Bridge between the wire-format `JemmaProfileJ` (decoded from a `_j 1.2`
 * QR payload) and the local Clinical Forge KB.
 *
 * Major changes from v2.2.16 :
 *   • Cross-checks now use the REAL schema views/tables :
 *       v_ddi_emergency        (Major+Moderate, pre-filtered)
 *       drug_disease_interactions
 *       atc_hierarchy          (for ATC class matching)
 *       ips_valuesets_translations (for FR/JA display localization)
 *   • The O(N²) DDI loop is replaced by a SINGLE batched query against
 *     v_ddi_emergency. Even with N=10 medications (45 pairs), 1 SELECT IN
 *     beats 45 round-trips.
 *   • Allergy×medication matching now uses ATC class ancestry (not just
 *     substring matching) — if the patient is allergic to penicillin
 *     (J01CA*), every J01CA descendant in the prescribed meds is flagged.
 *   • Display names are pulled from `ips_valuesets_translations` so the
 *     rescuer sees medications in their UI language (FR / JA / etc.)
 *     instead of the English canonical.
 *
 * Performance target : ≤ 100ms total for a typical 5-allergy / 6-med
 * profile on Pixel 9 (measured budget : ~50ms hydration + ~10ms cross-
 * checks).
 */
package be.heyman.android.jemmapassdemo.kb

import android.util.Log
import java.util.Locale
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import be.heyman.android.jemmapassdemo.qr.JMedication
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import io.requery.android.database.sqlite.SQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import be.heyman.android.jemmapassdemo.pillars.IpsTranslationsRepository

@Singleton
class JemmaProfileHydrator @Inject constructor(
    private val kb: KnowledgeBaseService,
    private val kbManager: KnowledgeBaseManager,
    private val ipsTranslations: IpsTranslationsRepository,
) {

    companion object {
        private const val TAG = "JEMMA-HYDRATOR"
    }

    // ──────────────────────────────────────────────────────────────────────
    // Top-level API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Enrich a freshly decoded `_j 1.2` profile with KB-resolved displays
     * and clinical cross-checks. Returns gracefully (with no enrichments,
     * just localized fallbacks) if KB isn't Ready.
     *
     * @param profile  The wire-format profile from the QR payload.
     * @param uiLang   BCP-47 lang code for the rescuer's UI (en / fr / ja).
     */
    suspend fun hydrate(profile: JemmaProfileJ, uiLang: String = "en"): HydratedProfile =
        withContext(Dispatchers.IO) {
            val tStart = System.currentTimeMillis()

            coroutineScope {
                // Resolve all entries in parallel.
                val allergiesAsync = async { profile.al.map { hydrateAllergy(it, uiLang) } }
                val medicationsAsync = async { profile.md.map { hydrateMedication(it, uiLang) } }
                val conditionsAsync = async {
                    profile.cn.map {
                        hydrateGenericByCode(it.c, it.d, it.displayLabel, uiLang)
                    }
                }

                val pastProblemLabelsAsync = async { localizePastProblems(profile, uiLang) }

                val allergies = allergiesAsync.await()
                val medications = medicationsAsync.await()
                val conditions = conditionsAsync.await()
                val pastProblemLabels = pastProblemLabelsAsync.await()

                // Cross-checks : run in parallel (independent queries).
                val ddiAlertsAsync = async { batchCrossCheckDdi(medications) }
                val allergyAlertsAsync = async { crossCheckAllergyMedication(allergies, medications) }
                val drugDiseaseAlertsAsync = async {
                    crossCheckDrugDisease(medications, conditions)
                }

                val ddiAlerts = ddiAlertsAsync.await()
                val allergyAlerts = allergyAlertsAsync.await()
                val drugDiseaseAlerts = drugDiseaseAlertsAsync.await()

                val durMs = System.currentTimeMillis() - tStart
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] ✅ hydrated · " +
                        "al=${allergies.size} · md=${medications.size} · cn=${conditions.size} · " +
                        "ddi=${ddiAlerts.size} · al×md=${allergyAlerts.size} · " +
                        "drug×disease=${drugDiseaseAlerts.size} · in ${durMs}ms"
                )

                val atcList = medications.mapNotNull { it.atcCode }
                val generalCounts = kb.getGeneralInteractionCounts(atcList)

                val enrichedMeds = medications.map { med ->
                    val atc = med.atcCode
                    val ddiCount = ddiAlerts.count { it.medicationA === med || it.medicationB === med }
                    val (food, disease) = generalCounts[atc] ?: (0 to 0)
                    med.copy(
                        ddiCount = ddiCount,
                        foodInteractionCount = food,
                        diseaseInteractionCount = disease
                    )
                }

                HydratedProfile(
                    raw = profile,
                    uiLang = uiLang,
                    allergies = allergies,
                    medications = enrichedMeds,
                    conditions = conditions,
                    ddiAlerts = ddiAlerts,
                    allergyAlerts = allergyAlerts,
                    drugDiseaseAlerts = drugDiseaseAlerts,
                    hydrationMs = durMs,
                    pastProblemLabels = pastProblemLabels,
                )
            }
        }

    /** 📜 One batch query: SNOMED past-illness codes → labels in [uiLang] (empty for English). */
    private suspend fun localizePastProblems(profile: JemmaProfileJ, uiLang: String): Map<String, String> {
        val lang = uiLang.lowercase().take(2)
        if (lang == "en") return emptyMap()
        val codes = (profile.ph + profile.fs).filter { (it.codeSystem ?: KnowledgeBaseService.SYSTEM_SNOMED) == KnowledgeBaseService.SYSTEM_SNOMED }
            .mapNotNull { it.c?.takeIf { c -> c.isNotBlank() } }
        if (codes.isEmpty()) return emptyMap()
        return try {
            kb.getLocalizedDisplays(codes, KnowledgeBaseService.SYSTEM_SNOMED, lang)
        } catch (e: Throwable) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ past-problem labels ($lang) failed : ${e.message}")
            emptyMap()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Per-entry hydration (with localization)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Hydrate a single allergy entry :
     *   1. If a code is present, try [resolveByCode] with SNOMED system
     *   2. If unresolved, try [resolveAllergy] on the d_display label
     *   3. Pick localized display from `ips_valuesets_translations` when
     *      available, else fall back to wire d_display
     */
    private suspend fun hydrateAllergy(allergy: JAllergy, lang: String): HydratedAllergy {
        var resolved: ResolvedConcept = ResolvedConcept.NotFound
        if (!allergy.c.isNullOrBlank()) {
            resolved = kb.resolveByCode(allergy.c, KnowledgeBaseService.SYSTEM_SNOMED)
            if (resolved is ResolvedConcept.NotFound) {
                resolved = kb.resolveAllergy(allergy.c)
            }
        }
        if (resolved is ResolvedConcept.NotFound && !allergy.displayLabel.isNullOrBlank()) {
            resolved = kb.resolveAllergy(allergy.displayLabel)
        }

        val display = resolveLocalizedDisplay(resolved, allergy.displayLabel, lang, rawCodeFallback = allergy.c)

        return HydratedAllergy(
            raw = allergy,
            resolvedConcept = resolved.concept,
            displayLocalized = display,
            criticality = parseCriticality(allergy.s),
            clinicalStatus = parseClinicalStatus(allergy.st),
        )
    }

    /**
     * Hydrate a single medication entry. Tries by-code (RxNorm → SNOMED),
     * then by-name via `resolveDrug`. Captures the ATC code (essential
     * for downstream DDI queries).
     */
    private suspend fun hydrateMedication(med: JMedication, lang: String): HydratedMedication {
        var resolved: ResolvedConcept = ResolvedConcept.NotFound

        if (!med.c.isNullOrBlank()) {
            // Try as RxNorm first (most common in IPS exports), then SNOMED,
            // then via the DDInter drug catalog (which also handles ATC).
            resolved = kb.resolveByCode(med.c, KnowledgeBaseService.SYSTEM_RXNORM)
            if (resolved is ResolvedConcept.NotFound) {
                resolved = kb.resolveByCode(med.c, KnowledgeBaseService.SYSTEM_SNOMED)
            }
            if (resolved is ResolvedConcept.NotFound) {
                resolved = kb.resolveDrug(med.c)
            }
        }
        if (resolved is ResolvedConcept.NotFound && !med.displayLabel.isNullOrBlank()) {
            resolved = kb.resolveDrug(med.displayLabel)
        }

        val display = resolveLocalizedDisplay(resolved, med.displayLabel, lang, med.c)
        val concept = resolved.concept

        // Extract a "best ATC" : prefer the resolved.atcCode, else the first
        // entry of allAtcCodes (if the row came from ddinter_drugs), else
        // detect ATC shape from the raw c field.
        val atc = concept?.atcCode
            ?: concept?.allAtcCodes?.firstOrNull()
            ?: med.c.takeIf { isAtcCodeShape(it) }

        return HydratedMedication(
            raw = med,
            resolvedConcept = concept,
            displayLocalized = display,
            timing = med.t,
            doseValue = med.v,
            doseUnit = med.u,
            route = parseMedicationRoute(med.r),
            atcCode = atc,
            allAtcCodes = concept?.allAtcCodes ?: emptyList(),
            rxnormCui = concept?.rxnormCui,
        )
    }

    /**
     * Hydrate a generic entry (condition / procedure / device / etc.).
     */
    private suspend fun hydrateGenericByCode(
        code: String?,
        details: String?,
        displayLabel: String?,
        lang: String,
    ): HydratedGenericEntry {
        var resolved: ResolvedConcept = ResolvedConcept.NotFound
        if (!code.isNullOrBlank()) {
            resolved = kb.resolveByCode(code, KnowledgeBaseService.SYSTEM_SNOMED)
        }
        val display = resolveLocalizedDisplay(resolved, displayLabel, lang, code)
        return HydratedGenericEntry(
            raw = JEntryGeneric(c = code, d = details, displayLabel = displayLabel),
            resolvedConcept = resolved.concept,
            displayLocalized = display,
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cross-checks (DDI, allergy×med, drug×disease) — batched
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Single batched query for all DDI alerts in the profile.
     *
     * Strategy : collect all distinct ATC codes from all medications
     * (some drugs have multiple ATCs in `ddinter_drugs.atc_codes`), then
     * run one SELECT against `v_ddi_emergency` with the IN clauses on
     * both sides. Filter to pairs where both sides come from different
     * profile medications.
     *
     * Performance : 1 query × ~5ms vs N(N-1)/2 queries for the v2.2.16
     * loop approach.
     */
    private suspend fun batchCrossCheckDdi(
        meds: List<HydratedMedication>,
    ): List<DdiAlert> = withContext(Dispatchers.IO) {
        if (meds.size < 2) return@withContext emptyList()
        val db = kbManager.database() ?: return@withContext emptyList()

        // Build a map ATC → list of medications carrying that ATC. A single
        // med can map to several ATCs (e.g. Ibuprofen has 8). We need to
        // know which med "owns" a given ATC for the alert payload.
        val atcToMeds = mutableMapOf<String, MutableList<HydratedMedication>>()
        for (med in meds) {
            // Always include primary atcCode AND the full allAtcCodes list.
            val atcs = buildSet {
                med.atcCode?.let { add(it.uppercase()) }
                med.allAtcCodes.forEach { add(it.uppercase()) }
            }
            for (atc in atcs) {
                atcToMeds.getOrPut(atc) { mutableListOf() }.add(med)
            }
        }
        if (atcToMeds.isEmpty()) return@withContext emptyList()

        val placeholders = atcToMeds.keys.joinToString(",") { "?" }
        val args = atcToMeds.keys.toTypedArray()

        // One query : grab every Major/Moderate row where BOTH sides are
        // among the profile's ATCs.
        val sql = """
            SELECT d.drug_a_atc, d.drug_b_atc, d.severity, d.mechanism_category,
                   d.description_en, d.management_en, d.alternative_atc,
                   h.name_en, h.name_fr
            FROM v_ddi_emergency d
            LEFT JOIN atc_hierarchy h ON h.atc_code = d.alternative_atc
            WHERE d.drug_a_atc IN ($placeholders)
              AND d.drug_b_atc IN ($placeholders)
        """.trimIndent()

        val alerts = mutableListOf<DdiAlert>()
        // Track unique pairs (sorted ATCs to dedupe symmetric matches).
        val seenPairs = mutableSetOf<Pair<String, String>>()

        try {
            db.rawQuery(sql, args + args).use { c ->
                while (c.moveToNext()) {
                    val atcA = c.getString(0)?.uppercase() ?: continue
                    val atcB = c.getString(1)?.uppercase() ?: continue
                    val pairKey = if (atcA < atcB) atcA to atcB else atcB to atcA
                    if (!seenPairs.add(pairKey)) continue   // already handled

                    val medsA = atcToMeds[atcA] ?: continue
                    val medsB = atcToMeds[atcB] ?: continue

                    // Build cartesian (medA, medB) but skip same-medication
                    // self-pairs (a single med having both ATCs).
                    for (medA in medsA) {
                        for (medB in medsB) {
                            if (medA === medB) continue
                            val sev = DDIResult.Severity.fromString(c.getStringOrNull(2))
                            val rawAlt = c.getStringOrNull(6)
                            val altEn = c.getStringOrNull(7)
                            val altFr = c.getStringOrNull(8)
                            val altName = if (Locale.getDefault().language == "fr" && !altFr.isNullOrBlank()) altFr else altEn
                            val alternativeDisplay = if (!rawAlt.isNullOrBlank()) {
                                if (!altName.isNullOrBlank()) "$rawAlt - $altName" else rawAlt
                            } else null

                            alerts.add(
                                DdiAlert(
                                    medicationA = medA,
                                    medicationB = medB,
                                    severity = sev,
                                    mechanism = c.getStringOrNull(3),
                                    description = c.getStringOrNull(4),
                                    management = c.getStringOrNull(5),
                                    alternativeAtc = alternativeDisplay,
                                    fuzzyMatch = false,
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ batchCrossCheckDdi failed : ${e.message}"
            )
        }

        // Dedupe alerts by (medA, medB) pair (independent of order) — a
        // single pair of medications can show up multiple times if both
        // share several ATC codes.
        val deduped = alerts
            .groupBy {
                val a = System.identityHashCode(it.medicationA)
                val b = System.identityHashCode(it.medicationB)
                if (a < b) a to b else b to a
            }
            .map { (_, bucket) ->
                bucket.minByOrNull { it.severity.ordinal } ?: bucket.first()
            }
            .sortedBy { it.severity.ordinal }

        deduped
    }

    /**
     * Cross-check : for each medication, does the profile carry an allergy
     * to that drug ?
     *
     * Strategy (in order of confidence) :
     *   1. Code match : allergy.code == medication's code or any of its
     *      ATC codes
     *   2. ATC class match : if both have ATC codes, walk the allergy's
     *      ATC ancestors and check if the medication's ATC starts with
     *      one of them (e.g. allergy J01CA01 → med J01CA08 = same J01CA
     *      family = penicillins → flag as cross-class)
     *   3. Substring fallback : allergy display name appears (case-
     *      insensitive) inside the medication display name
     */
    private suspend fun crossCheckAllergyMedication(
        allergies: List<HydratedAllergy>,
        meds: List<HydratedMedication>,
    ): List<AllergyAlert> {
        if (allergies.isEmpty() || meds.isEmpty()) return emptyList()

        val out = mutableListOf<AllergyAlert>()
        for (allergy in allergies) {
            val allergyCode = allergy.raw.c?.trim().orEmpty()
            val allergyName = allergy.displayLocalized.trim().lowercase()
            val allergyConcept = allergy.resolvedConcept

            // 🔧 Lot 14.5c7 — Fallback : si la KB n'a pas mapping SNOMED → ATC
            // (e.g. SNOMED 91936005 "Penicillin allergy" → atcCode=null dans
            // KB v1.2), on infère l'ATC depuis le nom de l'allergie. Sans ce
            // fallback, le profil détail affichait `al×md=0` même quand
            // l'amoxicillin (J01CA04, classe J01C) était évidemment matchée
            // par l'allergie pénicilline. Logique alignée avec
            // KbCrossCheck.inferAtcFromAllergyName.
            val allergyAtc: String? = allergyConcept?.atcCode
                ?: inferAtcFromAllergyName(allergyName)
            if (allergyConcept?.atcCode == null && allergyAtc != null) {
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🩹 hydrator inferred ATC '$allergyAtc' " +
                        "from allergy name '$allergyName' (KB had no SNOMED→ATC mapping)",
                )
            }

            // Pre-fetch ancestors once per allergy. Maintenant on calcule
            // les ancêtres aussi quand l'ATC est inféré (pas seulement depuis
            // le KbConcept).
            val allergyAncestorAtcs: Set<String> = if (allergyAtc != null) {
                val direct = kb.getAtcAncestors(allergyAtc).map { it.atcCode }.toMutableSet()
                // ajoute aussi les niveaux ATC manuels pour les inferred codes
                // (les ancestors peuvent ne pas être complets pour des ATC
                // qui ne sont pas dans terminology_codes)
                if (allergyAtc.length >= 4) direct.add(allergyAtc.substring(0, 4))   // L3
                if (allergyAtc.length >= 5) direct.add(allergyAtc.substring(0, 5))   // L4
                direct
            } else emptySet()

            for (med in meds) {
                val matchType = matchAllergyToMed(
                    allergyCode = allergyCode,
                    allergyName = allergyName,
                    allergyAtc = allergyAtc,
                    allergyAncestorAtcs = allergyAncestorAtcs,
                    med = med,
                )
                if (matchType != null) {
                    out.add(
                        AllergyAlert(
                            allergyDisplay = allergy.displayLocalized,
                            allergyCode = allergyCode,
                            medication = med,
                            criticality = allergy.criticality,
                            matchedOn = matchType,
                        )
                    )
                }
            }
        }
        return out
    }

    /**
     * Decide if a single (allergy, medication) pair matches, and how. Returns
     * the match type label or null. Order matters : we report the strongest
     * match first.
     */
    private fun matchAllergyToMed(
        allergyCode: String,
        allergyName: String,
        allergyAtc: String?,
        allergyAncestorAtcs: Set<String>,
        med: HydratedMedication,
    ): String? {
        val medCode = med.raw.c?.trim().orEmpty()
        val medAtc = med.atcCode?.uppercase()
        val medAtcs = med.allAtcCodes.map { it.uppercase() }.toSet()
        val medName = med.displayLocalized.lowercase()

        // 1. Exact code match.
        if (allergyCode.isNotEmpty() && (
                allergyCode.equals(medCode, ignoreCase = true)
                    || allergyCode.equals(medAtc, ignoreCase = true)
                    || medAtcs.any { it.equals(allergyCode, ignoreCase = true) }
                )
        ) {
            return "code"
        }

        // 2. ATC class (allergy ancestor matches one of the med's ATCs).
        if (allergyAncestorAtcs.isNotEmpty()) {
            val medAllAtcs = buildSet {
                medAtc?.let { add(it) }
                addAll(medAtcs)
            }
            for (ancestor in allergyAncestorAtcs) {
                if (medAllAtcs.contains(ancestor)) return "class:$ancestor"
                // Also check prefix (e.g. allergy ancestor "J01CA" should
                // match med "J01CA08" even if J01CA08 isn't in the
                // ancestors list of the med because we only walk allergy's
                // ancestors). Use startsWith for ancestors of length ≥ 4.
                if (ancestor.length >= 4) {
                    for (medAtcCode in medAllAtcs) {
                        if (medAtcCode.startsWith(ancestor)) return "class:$ancestor"
                    }
                }
            }
        }

        // 3. Substring fallback — only for allergens with display names ≥ 4
        // chars (avoid false positives on short tokens like "iron").
        if (allergyName.length >= 4 && medName.contains(allergyName)) {
            return "name"
        }

        return null
    }

    /**
     * Cross-check : does the profile carry a documented contraindication
     * for any of the medications ?
     *
     * For each medication ATC, query `drug_disease_interactions` for any
     * disease whose name appears (LIKE %X%) in any of the profile's
     * conditions display labels.
     *
     * Pragmatic heuristic — the KB carries ~8 121 such pairs, mostly
     * keyed by the natural-language disease name (no SNOMED join). We
     * do a fuzzy LIKE on the condition display.
     */
    private suspend fun crossCheckDrugDisease(
        meds: List<HydratedMedication>,
        conditions: List<HydratedGenericEntry>,
    ): List<DrugDiseaseAlert> = withContext(Dispatchers.IO) {
        if (meds.isEmpty() || conditions.isEmpty()) return@withContext emptyList()
        kbManager.database() ?: return@withContext emptyList()

        val out = mutableListOf<DrugDiseaseAlert>()
        // For each med × condition pair, ask the KB.
        // Only a small product (typically ≤ 30 pairs), so even individual
        // queries stay under 50ms total. If profiles grow we'll batch.
        for (med in meds) {
            val atc = med.atcCode ?: continue
            for (cond in conditions) {
                // English first (stored SNOMED display, KB primary display), UI label last.
                val terms = DrugDiseaseTerms.candidates(
                    cond.raw.displayLabel, cond.resolvedConcept?.primaryDisplay, cond.displayLocalized,
                )
                if (terms.isEmpty()) continue
                val r = kb.queryDrugDiseaseTerms(atc, terms)
                if (r is DrugDiseaseResult.Found) {
                    out.add(
                        DrugDiseaseAlert(
                            medication = med,
                            condition = cond,
                            severity = r.severity,
                            description = r.description,
                            management = r.management,
                            diseaseNameMatched = r.diseaseName,
                        )
                    )
                }
            }
        }
        out.sortedBy { it.severity.ordinal }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Pick the best display name in priority :
     *   1. Localized via `ips_valuesets_translations` (DB lookup)
     *   2. Wire `d_display` snapshot from the QR
     *   3. KB `primary_display` (English canonical)
     *   4. "—" placeholder
     */
    private suspend fun resolveLocalizedDisplay(
        resolved: ResolvedConcept,
        wireDisplay: String?,
        lang: String,
        rawCodeFallback: String? = null,
    ): String {
        val ipsLang = when (lang.lowercase()) {
            "jp", "ja" -> "ja"
            else -> lang.lowercase()
        }

        // 1. Try rawCodeFallback first in pre-packaged asset translations
        val cleanRawCode = rawCodeFallback?.trim()
        if (!cleanRawCode.isNullOrBlank()) {
            val assetTranslation = ipsTranslations.get(cleanRawCode, ipsLang)
            if (!assetTranslation.isNullOrBlank()) {
                return IpsTranslationsRepository.cleanBilingual(assetTranslation, ipsLang)
            }
        }

        val concept = resolved.concept

        // 2. Try standard code mapping fields (snomedCode, rxnormCui, atcCode) in asset translations
        if (concept != null) {
            val mappedCodes = listOfNotNull(concept.snomedCode, concept.rxnormCui, concept.atcCode)
            for (mappedCode in mappedCodes) {
                val cleanMappedCode = mappedCode.trim()
                if (cleanMappedCode.isNotEmpty()) {
                    val assetTranslation = ipsTranslations.get(cleanMappedCode, ipsLang)
                    if (!assetTranslation.isNullOrBlank()) {
                        return IpsTranslationsRepository.cleanBilingual(assetTranslation, ipsLang)
                    }
                }
            }
        }

        // 3. Try concept.code (UMLS CUI) as fallback in asset translations
        if (concept != null && concept.code.isNotBlank()) {
            val assetTranslation = ipsTranslations.get(concept.code.trim(), ipsLang)
            if (!assetTranslation.isNullOrBlank()) {
                return IpsTranslationsRepository.cleanBilingual(assetTranslation, ipsLang)
            }
        }

        if (concept != null) {
            // DB translation first
            var translation = kb.getLocalizedDisplay(concept.code, concept.system, ipsLang)
            
            // Check local concept fields (legacy displayJp / displayFr)
            if (translation.isNullOrBlank()) {
                translation = when (ipsLang) {
                    "fr" -> concept.displayFr?.takeIf { it.isNotBlank() }
                    "ja" -> concept.displayJp?.takeIf { it.isNotBlank() }
                    else -> null
                }
            }

            if (!translation.isNullOrBlank() && translation != concept.code) {
                return IpsTranslationsRepository.cleanBilingual(translation, ipsLang)
            }

            // Translation is not present in the requested language -> Fallback to English (EN)
            val enTranslation = concept.displayEn?.takeIf { it.isNotBlank() }
                ?: concept.primaryDisplay?.takeIf { it.isNotBlank() }
            
            if (!enTranslation.isNullOrBlank() && enTranslation != concept.code) {
                return IpsTranslationsRepository.cleanBilingual(enTranslation, ipsLang)
            }
        }

        // If translation is missing or concept not resolved, check wire display
        val hasValidWireDisplay = !wireDisplay.isNullOrBlank() && 
            wireDisplay != concept?.code && 
            !isAtcCodeShape(wireDisplay) && 
            !Regex("^\\d+$").matches(wireDisplay.trim())

        if (hasValidWireDisplay) {
            return IpsTranslationsRepository.cleanBilingual(wireDisplay!!, ipsLang)
        }

        if (!wireDisplay.isNullOrBlank()) {
            return IpsTranslationsRepository.cleanBilingual(wireDisplay, ipsLang)
        }

        val codeForFallback = concept?.code ?: rawCodeFallback
        return tryAtcHierarchyFallback(codeForFallback) ?: "—"
    }

    /**
     * Best-effort lookup in atc_hierarchy when nothing else resolved a
     * display. Returns formatted "<name_en> [ATC <code>]" or null if no
     * row matched. Returns the raw code as bracket-only when only the
     * code is known but no name (rare in build 2.0-omnis but possible).
     */
    private fun tryAtcHierarchyFallback(code: String?): String? {
        if (code.isNullOrBlank() || !isAtcCodeShape(code)) return null
        val db = kbManager.database() ?: return "[ATC $code]"
        val sql = "SELECT name_en FROM atc_hierarchy WHERE atc_code = ? LIMIT 1"
        return try {
            db.rawQuery(sql, arrayOf(code)).use { c ->
                if (c.moveToFirst()) {
                    val name = c.getStringOrNull(0)?.takeIf { it.isNotBlank() }
                    if (name != null) "$name [ATC $code]" else "[ATC $code]"
                } else {
                    "[ATC $code]"
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ atc_hierarchy fallback failed for $code : ${e.message}")
            "[ATC $code]"
        }
    }

    /**
     * Detect ATC-shaped strings : 1 letter + 2 digits + 2 letters + 2 digits
     * (e.g. "N02BE01", "B01AA03").
     */
    private fun isAtcCodeShape(s: String?): Boolean =
        s != null && Regex("""^[A-Za-z]\d{2}[A-Za-z]{2}\d{2}$""").matches(s.trim())

    private fun parseCriticality(s: String?): AllergyCriticality = when (s?.uppercase()) {
        "H" -> AllergyCriticality.HIGH
        "L" -> AllergyCriticality.LOW
        else -> AllergyCriticality.UNABLE_TO_ASSESS
    }

    private fun parseClinicalStatus(s: String?): ClinicalStatus = when (s?.uppercase()) {
        "A" -> ClinicalStatus.ACTIVE
        "I" -> ClinicalStatus.INACTIVE
        "R" -> ClinicalStatus.RESOLVED
        else -> ClinicalStatus.ACTIVE   // FHIR R4 IPS default
    }

    private fun parseMedicationRoute(s: String?): MedicationRoute = when (s?.uppercase()) {
        "O" -> MedicationRoute.ORAL
        "I" -> MedicationRoute.INJECTION
        "T" -> MedicationRoute.TOPICAL
        "S" -> MedicationRoute.SUBCUTANEOUS
        else -> MedicationRoute.UNKNOWN
    }

    /**
     * 🆕 Lot 14.5c7 — Inférence ATC depuis nom d'allergie.
     *
     * Aligned with [KbCrossCheck.inferAtcFromAllergyName] (copy, kept here
     * to avoid a circular dependency hydrator → cross-check). Returns a
     * representative ATC code for the allergen class so that cross-check
     * can still match by class-prefix even when the KB lacks SNOMED → ATC
     * mapping for the allergy code (e.g. SNOMED 91936005 "Penicillin
     * allergy" → no ATC in KB v1.2).
     *
     * Order matters : most specific first.
     */
    private fun inferAtcFromAllergyName(name: String): String? {
        if (name.isBlank()) return null
        val n = name.lowercase()
        return when {
            // Beta-lactam antibacterials (J01C)
            n.contains("pénicill") || n.contains("penicill") ||
            n.contains("amoxicill") || n.contains("ampicill") ||
            n.contains("augmentin") -> "J01CA01"
            // Cephalosporins (J01D)
            n.contains("céphalo") || n.contains("cephalo") ||
            n.contains("céfa") || n.contains("cefa") ||
            n.contains("ceftria") -> "J01DB01"
            // Carbapenems (J01DH)
            n.contains("carbapénème") || n.contains("carbapenem") ||
            n.contains("méropénème") || n.contains("meropenem") ||
            n.contains("imipenem") -> "J01DH02"
            // Sulfonamides (J01E)
            n.contains("sulfamide") || n.contains("sulfonamide") ||
            n.contains("sulfa ") || n.endsWith("sulfa") ||
            n.contains("cotrimo") || n.contains("bactrim") -> "J01EE01"
            // Macrolides (J01F)
            n.contains("macrolid") || n.contains("érythromy") ||
            n.contains("erythromy") || n.contains("azithromy") ||
            n.contains("clarithromy") -> "J01FA01"
            // Tetracyclines (J01A)
            n.contains("tétracycl") || n.contains("tetracycl") ||
            n.contains("doxycycl") -> "J01AA02"
            // Quinolones (J01M)
            n.contains("quinolone") || n.contains("ciprofloxa") ||
            n.contains("levofloxa") || n.contains("moxifloxa") -> "J01MA02"
            // Aminoglycosides (J01G)
            n.contains("aminoside") || n.contains("aminoglyco") ||
            n.contains("gentamicine") || n.contains("gentamicin") -> "J01GB03"
            // NSAIDs (M01A)
            n.contains("ains") || n.contains("nsaid") ||
            n.contains("ibuprofène") || n.contains("ibuprofen") ||
            n.contains("naproxène") || n.contains("naproxen") ||
            n.contains("diclofénac") || n.contains("diclofenac") ||
            n.contains("kétoprofène") || n.contains("ketoprofen") -> "M01AE01"
            // Aspirin
            n.contains("aspirine") || n.contains("aspirin") ||
            n.contains("acide acétylsalicy") || n.contains("acetylsalicy") -> "N02BA01"
            // Opioids
            n.contains("opioïde") || n.contains("opioid") ||
            n.contains("morphine") || n.contains("codéine") || n.contains("codeine") ||
            n.contains("tramadol") -> "N02AA01"
            // Iodine contrast
            n.contains("iode") || n.contains("iodine") ||
            n.contains("produit de contraste") || n.contains("contrast media") -> "V08AB02"
            // Statins
            n.contains("statine") || n.contains("statin") ||
            n.contains("atorvasta") || n.contains("simvasta") -> "C10AA01"
            else -> null
        }
    }
}

// ─── Cursor extensions (file-private to this Hydrator) ──────────────────────

private fun android.database.Cursor.getStringOrNull(idx: Int): String? =
    if (idx < 0 || isNull(idx)) null else getString(idx)

// ─── Hydrated data classes (output of [JemmaProfileHydrator.hydrate]) ───────

/**
 * Profile + KB-resolved enrichments. The card renderer iterates these
 * pre-localized lists and pre-computed alerts without any further DB hits.
 */
data class HydratedProfile(
    val raw: JemmaProfileJ,
    val uiLang: String,
    val allergies: List<HydratedAllergy>,
    val medications: List<HydratedMedication>,
    val conditions: List<HydratedGenericEntry>,
    /** DDI alerts (Major + Moderate severity), sorted Major → Moderate. */
    val ddiAlerts: List<DdiAlert>,
    /** Allergens that match an active medication. */
    val allergyAlerts: List<AllergyAlert>,
    /** Drug × disease contraindications detected against profile conditions. */
    val drugDiseaseAlerts: List<DrugDiseaseAlert>,
    val hydrationMs: Long,
    /** 📜 SNOMED code → past-illness label in [uiLang] (KB free-set translation; absent = English term). */
    val pastProblemLabels: Map<String, String> = emptyMap(),
) {
    val hasAlerts: Boolean
        get() = ddiAlerts.isNotEmpty() || allergyAlerts.isNotEmpty() || drugDiseaseAlerts.isNotEmpty()

    val majorDdiCount: Int
        get() = ddiAlerts.count { it.severity == DDIResult.Severity.MAJOR }

    val majorAllergyAlertCount: Int
        get() = allergyAlerts.count { it.criticality == AllergyCriticality.HIGH }

    val majorDrugDiseaseCount: Int
        get() = drugDiseaseAlerts.count { it.severity == DDIResult.Severity.MAJOR }

    val totalMajorAlerts: Int
        get() = majorDdiCount + majorAllergyAlertCount + majorDrugDiseaseCount
}

data class HydratedAllergy(
    val raw: JAllergy,
    val resolvedConcept: KbConcept?,
    val displayLocalized: String,
    val criticality: AllergyCriticality,
    val clinicalStatus: ClinicalStatus,
)

data class HydratedMedication(
    val raw: JMedication,
    val resolvedConcept: KbConcept?,
    val displayLocalized: String,
    val timing: String?,
    val doseValue: String?,
    val doseUnit: String?,
    val route: MedicationRoute,
    /** Best ATC code (resolved or detected). Used for DDI lookups. */
    val atcCode: String?,
    /** All ATC codes from `ddinter_drugs.atc_codes` CSV (8 codes for Ibuprofen e.g.). */
    val allAtcCodes: List<String>,
    val rxnormCui: String?,
    
    // Counts for UI badges
    val ddiCount: Int = 0,
    val foodInteractionCount: Int = 0,
    val diseaseInteractionCount: Int = 0,
)

data class HydratedGenericEntry(
    val raw: JEntryGeneric,
    val resolvedConcept: KbConcept?,
    val displayLocalized: String,
)

/**
 * One DDI alert between two medications in the profile.
 */
data class DdiAlert(
    val medicationA: HydratedMedication,
    val medicationB: HydratedMedication,
    val severity: DDIResult.Severity,
    val mechanism: String?,
    val description: String?,
    val management: String?,
    val alternativeAtc: String?,
    val fuzzyMatch: Boolean,
)

/**
 * One allergy × medication match.
 */
data class AllergyAlert(
    val allergyDisplay: String,
    val allergyCode: String,
    val medication: HydratedMedication,
    val criticality: AllergyCriticality,
    /** "code", "name", or "class:Xnnn" (the ATC class label). */
    val matchedOn: String,
)

/**
 * One drug × disease contraindication match.
 */
data class DrugDiseaseAlert(
    val medication: HydratedMedication,
    val condition: HydratedGenericEntry,
    val severity: DDIResult.Severity,
    val description: String?,
    val management: String?,
    val diseaseNameMatched: String,
)

enum class AllergyCriticality { HIGH, LOW, UNABLE_TO_ASSESS }
enum class ClinicalStatus { ACTIVE, INACTIVE, RESOLVED }
enum class MedicationRoute { ORAL, INJECTION, TOPICAL, SUBCUTANEOUS, UNKNOWN }
