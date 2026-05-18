/*
 * KbState.kt — exhaustive UI states for the local knowledge_full.db.
 *
 * The KnowledgeBaseManager exposes a StateFlow<KbState> that the
 * SettingsFragment, the future medication-scan pipeline, and any other
 * KB consumer can observe. Each state carries the information needed to
 * render the matching UI (no extra round-trip).
 *
 * Lifecycle :
 *
 *   NotPresent ── (download succeeds) ──→ Validating ── ok ──→ Ready
 *                                              │
 *                                              └─ ko ──→ Failed
 *
 *   Once Ready, the underlying SQLiteDatabase is held by the singleton
 *   for the rest of the process. Wipe / re-download cycles re-trigger
 *   the full sequence.
 *
 * v2.2.16.2 — schema 2.0-omnis aligned :
 *   New fields below were added to surface the rich metadata of the
 *   real Clinical Forge KB (build version, ATC hierarchy size, DDInter
 *   drug catalog count, dual FTS5 status latin/cjk, etc.). The 4
 *   pre-existing fields (totalCodes, totalLanguages, totalDdiPairs,
 *   fts5Ok) are preserved for backward compat with SettingsCardBinders.
 */
package be.heyman.android.jemmapassdemo.kb

sealed class KbState {

    /** No knowledge_full.db file on disk yet. Initial state on first run. */
    object NotPresent : KbState()

    /**
     * File found on disk, validation in progress.
     * UI : show a small spinner and the boot message string ("Initialisation
     * de la base de connaissances..." in FR).
     */
    object Validating : KbState()

    /**
     * KB ready : SQLite open in read-only, FTS5 verified, headline stats
     * fetched. The fields below are used to drive the KB card on Settings.
     *
     * v2.2.16.2 : the field set has grown to expose the richness of the
     * real Clinical Forge schema (build version, ATC count, drug count,
     * drug-disease, food, dual FTS5). The 4 original fields keep their
     * semantic meaning for the UI binder.
     */
    data class Ready(
        /** On-disk size of the .db file (bytes). */
        val sizeBytes: Long,

        // ─── 4 original fields preserved for SettingsCardBinders compat ───

        /** Total rows in `terminology_codes` (UMLS + SNOMED unified table). */
        val totalCodes: Long,
        /** Number of distinct languages with display translations available. */
        val totalLanguages: Int,
        /**
         * Total DDInter ATC↔ATC interaction pairs (sum of all severities).
         * UI label "PAIRES DDI". Backed by `ddi_atc_pairs` JOIN `ddi_facts`.
         */
        val totalDdiPairs: Long,
        /**
         * True iff at least one of the FTS5 virtual tables responded to a
         * probe query. UI label "FTS5". Backed by [fts5LatinOk] || [fts5CjkOk].
         */
        val fts5Ok: Boolean,

        /** Walltime spent during validation (ms), useful for telemetry. */
        val validationMs: Long,

        // ─── New fields v2.2.16.2 ─────────────────────────────────────────

        /** Build version string from `build_metadata` (e.g. "2.0-omnis"). */
        val buildVersion: String? = null,
        /** Build timestamp from `build_metadata` (e.g. "2026-04-30 06:00:57"). */
        val buildTimestamp: String? = null,
        /** Build schema label from `build_metadata` (e.g. "3NF (...)"). */
        val buildSchema: String? = null,
        /** Comma-separated language codes available in the KB build. */
        val buildLanguages: String? = null,

        /** Rows in `terminology_codes WHERE category='Medication'`. */
        val totalMedications: Long = 0,
        /** Rows in `terminology_codes` whose category contains 'Allergen'. */
        val totalAllergens: Long = 0,
        /** Rows in `terminology_codes WHERE category='Condition'`. */
        val totalConditions: Long = 0,
        /** Rows in `terminology_codes WHERE ips_validated = 1`. */
        val totalIpsValidated: Long = 0,

        /** Rows in `ddinter_drugs` (curated drug catalog). */
        val totalDdinterDrugs: Long = 0,
        /** Rows in `ddi_facts` (raw drug-drug interaction facts). */
        val totalDdiFacts: Long = 0,
        /** Rows in `interactions_food` (drug-food interactions). */
        val totalDfiPairs: Long = 0,
        /** Rows in `drug_disease_interactions` (drug-disease contraindications). */
        val totalDrugDiseasePairs: Long = 0,
        /** Rows in `therapeutic_duplications` (pharma class duplications). */
        val totalTherapeuticDuplications: Long = 0,

        /** Rows in `atc_hierarchy` (full ATC tree, all 5 levels). */
        val totalAtcCodes: Long = 0,
        /** Rows in `ips_valuesets` (official IPS valueset codes). */
        val totalIpsValuesetEntries: Long = 0,
        /** Rows in `ips_valuesets_translations` (FR/JA/ES/etc translations). */
        val totalIpsTranslations: Long = 0,

        /** True if `terminology_latin` FTS5 responded to a probe MATCH query. */
        val fts5LatinOk: Boolean = false,
        /** True if `terminology_cjk` FTS5 responded to a probe MATCH query. */
        val fts5CjkOk: Boolean = false,
    ) : KbState()

    /**
     * Validation failed. Cause is human-readable (English internal,
     * translated by the UI layer if needed for display).
     */
    data class Failed(
        val reason: String,
    ) : KbState()
}
