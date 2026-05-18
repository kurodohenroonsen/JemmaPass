/*
 * KbModels.kt — v2.2.16.3 (schema 2.0-omnis aligned)
 *
 * Data classes for the Clinical Forge KB query API. Derived from the
 * 3NF schema observed in the real `knowledge_full.db` build 2.0-omnis :
 *
 *   • terminology_codes (UMLS + SNOMED unified, 1.4M rows)
 *   • ddinter_drugs (curated drug catalog, 2 289 rows)
 *   • ddi_facts + ddi_atc_pairs (260K facts × 876K ATC pairs)
 *   • interactions_food (857 rows)
 *   • drug_disease_interactions (8 121 rows)
 *   • atc_hierarchy (6 934 rows, 5 levels)
 *   • ips_valuesets + ips_valuesets_translations (20 languages)
 *
 * Sealed classes are used throughout so the compiler enforces exhaustive
 * `when` handling at the call site. No nullable fields scattered for
 * "found / not found / error" — that's exactly what these sealeds replace.
 *
 * Severity vocabulary aligns with DDInter 2.0 :
 *   "Major" | "Moderate" | "Minor" | "None" | "Unknown"
 */
package be.heyman.android.jemmapassdemo.kb

/**
 * A concept resolved from `terminology_codes` or `ddinter_drugs`. Returned
 * by `KnowledgeBaseService.resolveDrug()`, `resolveAllergy()`, and
 * `resolveByCode()`.
 *
 * The 3 found-branches differentiate match quality :
 *   • `Exact`    : canonical name/code exactly matched the query
 *   • `Prefix`   : name starts with the query (used by FTS5 prefix match)
 *   • `Contains` : name contains the query (LIKE %q%)
 *
 * The caller can use this signal to gate confidence (e.g. don't auto-pick
 * a "contains" match for clinical decisions, only show it as a suggestion
 * in autocomplete).
 *
 * Note : the sub-class ctor params are named `value` (not `concept`) to
 * avoid shadowing the parent's [concept] property — Kotlin compile error
 * "hides member of supertype" otherwise.
 */
sealed class ResolvedConcept {
    data class Exact(val value: KbConcept) : ResolvedConcept()
    data class Prefix(val value: KbConcept) : ResolvedConcept()
    data class Contains(val value: KbConcept) : ResolvedConcept()
    data object NotFound : ResolvedConcept()

    /** Returns the embedded concept if found, null otherwise. */
    val concept: KbConcept?
        get() = when (this) {
            is Exact -> value
            is Prefix -> value
            is Contains -> value
            NotFound -> null
        }

    /** Human-readable label for logs ("exact" / "prefix" / "contains" / "not_found"). */
    val matchedOn: String
        get() = when (this) {
            is Exact -> "exact"
            is Prefix -> "prefix"
            is Contains -> "contains"
            NotFound -> "not_found"
        }
}

/**
 * One row from either `terminology_codes` or `ddinter_drugs`, hydrated for
 * the consumer.
 *
 * Optional fields default to null because the schema is intentionally sparse
 * — a single drug can have ATC + RxNorm but no SNOMED, and conversely an
 * UMLS allergen can have only the CUI.
 *
 * v2.2.16.3 additions :
 *   • [category] — the terminology_codes.category column ('Medication',
 *     'Condition', 'Procedure', 'Device', 'Chemical_Allergen',
 *     'Protein_Allergen', 'Food', 'Mineral', 'Vitamin_DFI', etc.)
 *   • [ddinterId] — the ddinter_drugs.ddinter_id (when the row was resolved
 *     via the drug catalog rather than terminology_codes).
 *   • [allAtcCodes] — the comma-separated ATC list (CSV) found in
 *     ddinter_drugs.atc_codes ; many drugs have several ATC codes.
 *
 * The 3 displayFr/En/Jp legacy fields are kept for compat ; in practice
 * only [primaryDisplay] is populated from terminology_codes (English
 * canonical), and localized displays are fetched via
 * [KnowledgeBaseService.getLocalizedDisplay].
 */
data class KbConcept(
    /** Canonical code, e.g. "C0000005" (UMLS CUI), "1197170008" (SNOMED), or "DDInter1951" (DDInter). */
    val code: String,
    /** Coding system URI, e.g. "http://snomed.info/sct" or "urn:umls". */
    val system: String,
    /** English canonical display (terminology_codes.primary_display). */
    val primaryDisplay: String,

    // Legacy multilingual fields (kept for compat — typically null for new
    // resolves ; use getLocalizedDisplay() instead).
    val displayFr: String? = null,
    val displayEn: String? = null,
    val displayJp: String? = null,

    /** ATC code (single, primary). */
    val atcCode: String? = null,
    /** RxNorm CUI (drug). */
    val rxnormCui: String? = null,
    /** SNOMED code (allergen, condition, procedure). */
    val snomedCode: String? = null,

    /** UI-pillar hint (legacy field, often null). */
    val pillarHint: String? = null,
    /** True iff `terminology_codes.ips_validated = 1`. */
    val ipsValidated: Boolean = false,

    // ─── v2.2.16.3 additions (aligned with real schema) ───────────────────

    /**
     * Category from `terminology_codes.category`. One of :
     * 'Medication', 'Condition', 'Procedure', 'Device',
     * 'Chemical_Allergen', 'Protein_Allergen', 'Food', 'Mineral',
     * 'Vitamin_DFI', or null (most common — UMLS uncategorized).
     */
    val category: String? = null,

    /**
     * DDInter drug catalog ID (e.g. "DDInter1951" for Warfarin). Set when
     * the concept was resolved via `ddinter_drugs` rather than
     * `terminology_codes`. Useful for downstream DDI lookups via
     * `ddi_facts.drug_a_ddinter_id`.
     */
    val ddinterId: String? = null,

    /**
     * All ATC codes for this drug, parsed from `ddinter_drugs.atc_codes`
     * CSV. e.g. Ibuprofen has 8 codes :
     * ["G02CC01", "C01EB16", "M01AE01", "M02AA13", "R02AX02", "N02AJ08",
     *  "N02AJ19", "M01AE51"]. Empty if no DDInter row matched.
     */
    val allAtcCodes: List<String> = emptyList(),
)

/**
 * Drug-Drug Interaction lookup result. Returned by `queryDDIByAtc()` and
 * `queryDDIDetailed()`.
 *
 * Severity is the most actionable field for UI :
 *   • Major    → red, modal alert with recommendation
 *   • Moderate → orange, badge + tooltip
 *   • Minor    → yellow, soft warning
 *   • None     → green, "no documented interaction"
 *   • Unknown  → grey, "could not determine" (input issue)
 *
 * v2.2.16.3 — [Found] now carries the management text and alternative_atc
 * fields from `ddi_facts`/`v_ddi_emergency`. Both are optional but the KB
 * provides them very consistently.
 */
sealed class DDIResult {
    data class Found(
        val severity: Severity,
        /** Mechanism category, e.g. 'PD_Synergistic', 'PK_Metabolism_CYP', 'Other'. */
        val mechanism: String? = null,
        /** Free-text English description of the interaction (DDInter 2.0). */
        val description: String? = null,
        /** Free-text English management recommendation (e.g. "monitor INR…"). */
        val management: String? = null,
        /**
         * Suggested alternative ATC class to consider (DDInter 2.0). For
         * Warfarin × Ibuprofen this might be "B01A" or null.
         */
        val alternativeAtc: String? = null,

        /** Display label for drug A as it appears in the UI / source. */
        val drugAResolved: String,
        /** Display label for drug B. */
        val drugBResolved: String,

        val sourceDatabase: String = "DDInter 2.0",
        val queryDurationMs: Long,
        /** True if matched via fuzzy LIKE %% rather than exact name/ATC. */
        val fuzzyMatch: Boolean = false,
    ) : DDIResult()

    data class None(
        val drugAResolved: String,
        val drugBResolved: String,
        val queryDurationMs: Long,
    ) : DDIResult()

    data class Error(
        val reason: String,
        val queryDurationMs: Long,
    ) : DDIResult()

    enum class Severity(val label: String) {
        MAJOR("Major"),
        MODERATE("Moderate"),
        MINOR("Minor"),
        NONE("None"),
        UNKNOWN("Unknown");

        companion object {
            /** Parse the DDInter severity string (case-insensitive). */
            fun fromString(s: String?): Severity = when (s?.trim()?.lowercase()) {
                "major" -> MAJOR
                "moderate" -> MODERATE
                "minor" -> MINOR
                "none" -> NONE
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Drug-Food Interaction lookup result. Returned by `queryDFI()`. Schema
 * is simpler than DDI (no resolved-name field — drug_atc and food_name_en
 * are passed back as-is in the description).
 */
sealed class DFIResult {
    data class Found(
        val severity: DDIResult.Severity,
        val mechanism: String? = null,
        val description: String? = null,
        val management: String? = null,
        val queryDurationMs: Long,
    ) : DFIResult()

    data class None(val queryDurationMs: Long) : DFIResult()

    data class Error(val reason: String, val queryDurationMs: Long) : DFIResult()
}

/**
 * Drug-Disease Interaction (contraindication) lookup result. Returned by
 * `queryDrugDisease()`. Backed by the 8 121-row `drug_disease_interactions`
 * table.
 *
 * Use case : the patient has condition X (e.g. "Severe renal impairment"),
 * a medication Y is prescribed → check whether X is a documented
 * contraindication for Y.
 *
 * v2.2.16.3 NEW.
 */
sealed class DrugDiseaseResult {
    data class Found(
        val severity: DDIResult.Severity,
        val description: String? = null,
        val management: String? = null,
        val drugAtc: String,
        val diseaseName: String,
        val queryDurationMs: Long,
    ) : DrugDiseaseResult()

    data class None(val queryDurationMs: Long) : DrugDiseaseResult()

    data class Error(val reason: String, val queryDurationMs: Long) : DrugDiseaseResult()
}

/**
 * Single search hit from `searchCodes()`. The `display` field is
 * pre-localized at the service boundary (caller passes a BCP-47 lang
 * code).
 */
data class KbSearchHit(
    val concept: KbConcept,
    /** Pre-localized display string per the requested lang. */
    val display: String,
    /**
     * Which FTS5 backend matched : "latin", "cjk", or "like" (when the
     * service had to fall back).
     */
    val matchedVia: String = "latin",
)

/**
 * Wrapper around a search response so we can convey latency + error
 * context without throwing.
 */
sealed class KbSearchResult {
    data class Success(
        val hits: List<KbSearchHit>,
        val latencyMs: Long,
        /** True if at least one of the FTS5 virtuals was used (vs LIKE fallback). */
        val usedFts5: Boolean,
    ) : KbSearchResult()

    data class Error(
        val reason: String,
        val latencyMs: Long,
    ) : KbSearchResult()
}

/**
 * Headline stats — used by the Settings card and the demo prep banner.
 * Mirrors the relevant subset of [KbState.Ready] for callers that want
 * stats without listening to the StateFlow.
 */
data class KbStats(
    val codesCount: Long,
    val ddiCount: Long,
    val dfiCount: Long,
    val drugDiseaseCount: Long,
    val ddinterDrugsCount: Long,
    val atcCount: Long,
    val languageCount: Int,
    val fts5LatinAvailable: Boolean,
    val fts5CjkAvailable: Boolean,
    val buildVersion: String?,
    val buildTimestamp: String?,
) {
    /** Convenience for older callers. */
    val fts5Available: Boolean get() = fts5LatinAvailable || fts5CjkAvailable
}

/**
 * One ancestor in the ATC hierarchy. Returned by `getAtcAncestors()`.
 *
 * Example for Warfarin (B01AA03) the list returned is :
 *   [
 *     AtcNode("B01AA03", level=7, name_en="warfarin", parent="B01AA"),
 *     AtcNode("B01AA",   level=5, name_en="Vitamin K antagonists", parent="B01A"),
 *     AtcNode("B01A",    level=4, name_en="ANTITHROMBOTIC AGENTS", parent="B01"),
 *     AtcNode("B01",     level=3, name_en="ANTITHROMBOTIC AGENTS", parent="B"),
 *     AtcNode("B",       level=1, name_en="BLOOD AND BLOOD FORMING ORGANS", parent=null),
 *   ]
 *
 * Note : as observed in build 2.0-omnis, `name_fr` and `name_jp` columns
 * exist in the schema but are NULL for all 6 934 rows. We expose them
 * anyway for forward compat — the localized labels live in
 * `ips_valuesets_translations` instead, fetched via
 * [KnowledgeBaseService.getLocalizedDisplay].
 *
 * v2.2.16.3 NEW.
 */
data class AtcNode(
    val atcCode: String,
    val parentAtc: String?,
    val level: Int,
    val nameEn: String,
    val nameFr: String? = null,
    val nameJp: String? = null,
)

/**
 * 🆕 Lot 14.5c19 — Rich details for a medication, pulled from `ddinter_drugs`,
 * `atc_hierarchy`, and `dosages`.
 */
data class DetailedDrugInfo(
    val atcCode: String,
    val canonicalName: String,
    val description: String? = null,
    val chemicalFormula: String? = null,
    val molecularWeight: Double? = null,
    val smiles: String? = null,
    val whoEmlStatus: String? = null,
    val whoAwareCategory: String? = null,
    val dosages: List<DrugDoseInfo> = emptyList(),
    val foodInteractions: List<InteractionInfo> = emptyList(),
    val diseaseInteractions: List<InteractionInfo> = emptyList(),
)

data class DrugDoseInfo(
    val route: String? = null,
    val ddd: Double? = null,
    val unit: String? = null,
    val population: String? = null,
    val note: String? = null,
)

data class InteractionInfo(
    val entityName: String, // food name or disease name
    val severity: String? = null,
    val description: String? = null,
    val management: String? = null
)
