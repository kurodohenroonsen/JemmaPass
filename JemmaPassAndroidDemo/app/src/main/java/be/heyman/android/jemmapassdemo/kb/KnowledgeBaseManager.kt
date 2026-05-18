/*
 * KnowledgeBaseManager.kt — v2.2.16.2
 *
 * Owner of the local knowledge_full.db (Clinical Forge build 2.0-omnis).
 *
 * Responsibilities :
 *   1. Detect if the file is on disk (after download by Google's worker)
 *   2. Validate it can be opened as a valid SQLite + FTS5 database
 *   3. Tune it for read-mostly workloads (cache_size, mmap, journal mode)
 *   4. Probe the REAL schema (3NF Clinical Forge) for headline stats
 *   5. Verify the two pre-built FTS5 virtual tables (latin + cjk)
 *   6. Hold the connection for the lifetime of the process
 *   7. Expose `state` as a `StateFlow<KbState>` for the UI to observe
 *
 * Auto-init policy :
 *   Triggered from JemmaApplication.onCreate() in a coroutine on
 *   Dispatchers.IO. Boot message displayed by SettingsFragment whenever
 *   state == Validating.
 *
 *
 * ┌─ The REAL schema (Clinical Forge 2.0-omnis, build 2026-04-30) ──────┐
 * │                                                                     │
 * │  Tables (37) and views (2) in this 2.2 GB file :                    │
 * │                                                                     │
 * │  TERMINOLOGY (the unified concept table, ~1.4M rows)                │
 * │    terminology_codes(code PK, atc_code, rxnorm_cui, snomed_code,    │
 * │                      ips_validated, primary_display, system,        │
 * │                      category)                                      │
 * │      • category ∈ {Medication, Condition, Procedure, Device,        │
 * │        Chemical_Allergen, Protein_Allergen, Mineral, Food,          │
 * │        Vitamin_DFI, NULL (UMLS uncategorized)}                      │
 * │      • system ∈ {urn:umls (1.4M), http://snomed.info/sct (19 697)}  │
 * │      • ips_validated=1 → 12 899 rows (the curated IPS subset)       │
 * │                                                                     │
 * │  FTS5 (pre-built, no lazy creation needed)                          │
 * │    terminology_latin USING fts5(code, lang, display,                │
 * │                                 tokenize='unicode61                 │
 * │                                          remove_diacritics 2')      │
 * │    terminology_cjk   USING fts5(code, lang, display,                │
 * │                                 tokenize='trigram                   │
 * │                                          case_sensitive 0')         │
 * │                                                                     │
 * │  DRUG INTERACTIONS                                                  │
 * │    ddinter_drugs(ddinter_id PK, name, primary_atc, atc_codes,       │
 * │                  formula, weight, cas, smiles, inchi, ...)          │
 * │      → 2 289 curated drugs                                          │
 * │    ddi_facts(fact_id PK, drug_a_ddinter_id, drug_b_ddinter_id,      │
 * │              severity, mechanism_category, description_en,          │
 * │              management_en, alternative_atc, ...)                   │
 * │      → 260 100 facts (Major 52K + Moderate 195K + Minor 12K)        │
 * │    ddi_atc_pairs(drug_a_atc, drug_b_atc, fact_id) PK composite      │
 * │      → ~600 000 ATC pair rows pointing to the 260K facts            │
 * │                                                                     │
 * │  VIEWS (we'll use these heavily in the service layer)               │
 * │    v_ddi_emergency  — pre-filtered Major+Moderate                   │
 * │    v_interactions_drug — full join, all severities                  │
 * │                                                                     │
 * │  COMPLEMENTARY                                                      │
 * │    interactions_food (857 rows) — drug-food interactions            │
 * │    drug_disease_interactions (8 121 rows)                           │
 * │    therapeutic_duplications (6 033 rows)                            │
 * │    atc_hierarchy (6 934 rows, levels 1/3/4/5/7)                     │
 * │      ⚠ name_fr and name_jp are EMPTY in this build — translations   │
 * │        live in ips_valuesets_translations instead                   │
 * │                                                                     │
 * │  IPS / FHIR                                                         │
 * │    ips_valuesets, ips_valuesets_translations (FR/JA/ES/.../25 langs)│
 * │    ips_concept_maps (cross-system mappings)                         │
 * │    ips_field_rules                                                  │
 * │                                                                     │
 * │  AUDIT / META                                                       │
 * │    build_metadata (build_version, build_timestamp, build_schema,    │
 * │                    build_languages, build_variant)                  │
 * │    kb_sources (16 ingested datasets with provenance)                │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * v2.2.16.2 — what changed from v2.2.15 :
 *   • Schema probes rewritten against the actual table names (was
 *     looking for non-existent `codes` and `interactions_drug`).
 *   • Removed `ensureCodesFts()` lazy build. The KB ships with TWO
 *     pre-built FTS5 tables (terminology_latin + terminology_cjk),
 *     no on-device build needed.
 *   • Mode changed back to OPEN_READONLY (no more lazy index build).
 *   • `KbState.Ready` enriched with build metadata + per-category and
 *     per-table counts. The 4 original fields keep their UI meaning so
 *     SettingsCardBinders.kt doesn't need to change.
 *   • Languages count now comes from `ips_valuesets_translations` (the
 *     actual source of FR/JA/ES/... translations).
 *
 * No data is COPIED. We open the file in-place at
 *   {externalFilesDir}/knowledge_full_db/1.0/knowledge_full.db
 */
package be.heyman.android.jemmapassdemo.kb

import android.content.Context
import android.util.Log
import be.heyman.android.jemmapassdemo.downloads.JemmaDownloadStorage
import be.heyman.android.jemmapassdemo.downloads.JemmaModelCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import io.requery.android.database.sqlite.SQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class KnowledgeBaseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: JemmaDownloadStorage,
) {

    companion object {
        private const val TAG = "JEMMA-KB"

        // Tuning constants (read-mostly workload, no writes from app).
        private const val PRAGMA_CACHE_PAGES = -65536          // negative = KB
        private const val PRAGMA_MMAP_SIZE = 268_435_456L      // 256 MB mmap
        private const val PRAGMA_TEMP_STORE = "MEMORY"
    }

    private val _state = MutableStateFlow<KbState>(KbState.NotPresent)
    val state: StateFlow<KbState> = _state.asStateFlow()

    /** Held while Ready ; null otherwise. */
    private var database: SQLiteDatabase? = null

    /** Internal coroutine scope, lives as long as the singleton. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Public accessor for downstream consumers (medication scan pipeline,
     * DDI lookups, OCR resolver, etc.). Returns null until state == Ready.
     * Callers MUST collect [state] to know when this becomes available.
     */
    fun database(): SQLiteDatabase? = database

    /**
     * Trigger validation if the KB file is present. Idempotent : safe to
     * call multiple times, will only re-validate if not already Ready.
     *
     * Called from :
     *   - JemmaApplication.onCreate()  (boot)
     *   - Settings card "Reload KB" button
     *   - JemmaDownloadCoordinator on KB download success
     */
    fun ensureInitialized() {
        when (state.value) {
            is KbState.Ready -> return
            is KbState.Validating -> return
            else -> {
                scope.launch { validateInternal() }
            }
        }
    }

    /**
     * Force a fresh validation cycle, even if Ready. Used after wipe + re-DL
     * or after the user hits the "Recharger" button.
     */
    fun reload() {
        scope.launch {
            closeQuietly()
            _state.value = KbState.NotPresent
            validateInternal()
        }
    }

    /**
     * Close the open connection and clear state. Used on Settings "Wipe" or
     * before the file is deleted/replaced.
     */
    fun close() {
        scope.launch { closeQuietly() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal validation pipeline
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun validateInternal() = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val kbModel = JemmaModelCatalog.knowledgeBase
        val kbFile = storage.modelFile(kbModel)

        if (!kbFile.exists()) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 📋 ensureInitialized · file not present at ${kbFile.absolutePath}"
            )
            _state.value = KbState.NotPresent
            return@withContext
        }

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🔐 Validating · path=${kbFile.absolutePath} · size=${kbFile.length()}"
        )
        _state.value = KbState.Validating

        // ── Open via requery's bundled SQLite (FTS5-capable) ──
        //
        // OPEN_READONLY because the KB ships fully-built : both the unique
        // `terminology_codes` table and the two pre-built FTS5 virtuals
        // (`terminology_latin`, `terminology_cjk`) come ready in the file.
        // No lazy index build needed any more (was a v2.2.15 mistake based
        // on outdated docs).
        val db: SQLiteDatabase = try {
            SQLiteDatabase.openDatabase(
                kbFile.absolutePath,
                /* factory= */ null,
                SQLiteDatabase.OPEN_READONLY,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ openDatabase failed : ${e.message}")
            _state.value = KbState.Failed(reason = "openDatabase: ${e.message ?: "unknown"}")
            return@withContext
        }

        // ── Tune for read-mostly ──
        try {
            db.rawQuery("PRAGMA cache_size = $PRAGMA_CACHE_PAGES", null).use { it.moveToFirst() }
            db.rawQuery("PRAGMA mmap_size = $PRAGMA_MMAP_SIZE", null).use { it.moveToFirst() }
            db.rawQuery("PRAGMA temp_store = $PRAGMA_TEMP_STORE", null).use { it.moveToFirst() }
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 📊 PRAGMA tuned · cache=${PRAGMA_CACHE_PAGES} · mmap=$PRAGMA_MMAP_SIZE · temp=$PRAGMA_TEMP_STORE"
            )
        } catch (e: Exception) {
            // Tuning failure is non-fatal ; log and continue.
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ PRAGMA tuning soft-failed : ${e.message}"
            )
        }

        // ── Probe build metadata first (small, cheap) ──
        val meta = readBuildMetadata(db)
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📦 build · version=${meta.version} · ts=${meta.timestamp} · schema=${meta.schema}"
        )

        // ── Probe headline counts (real schema 2.0-omnis) ──
        val totalCodes = countTable(db, "terminology_codes")
        val totalMedications = countTerminologyByCategory(db, "Medication")
        val totalAllergens = countTerminologyAllergens(db)
        val totalConditions = countTerminologyByCategory(db, "Condition")
        val totalIpsValidated = countWhere(db, "terminology_codes", "ips_validated = 1")
        val totalDdinterDrugs = countTable(db, "ddinter_drugs")
        val totalDdiFacts = countTable(db, "ddi_facts")
        val totalDdiPairs = countTable(db, "ddi_atc_pairs")
        val totalDfiPairs = countTable(db, "interactions_food")
        val totalDrugDiseasePairs = countTable(db, "drug_disease_interactions")
        val totalTherapeuticDuplications = countTable(db, "therapeutic_duplications")
        val totalAtcCodes = countTable(db, "atc_hierarchy")
        val totalIpsValuesetEntries = countTable(db, "ips_valuesets")
        val totalIpsTranslations = countTable(db, "ips_valuesets_translations")

        // ── Languages : actual count comes from translations table ──
        val totalLanguages = countDistinctLanguages(db)

        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] 🧮 schema probe · " +
                "codes=$totalCodes (med=$totalMedications · allg=$totalAllergens · cond=$totalConditions · ips=$totalIpsValidated) · " +
                "ddinter=$totalDdinterDrugs · facts=$totalDdiFacts · pairs=$totalDdiPairs · dfi=$totalDfiPairs · " +
                "ddsi=$totalDrugDiseasePairs · dup=$totalTherapeuticDuplications · " +
                "atc=$totalAtcCodes · ips_vs=$totalIpsValuesetEntries · ips_tr=$totalIpsTranslations · " +
                "langs=$totalLanguages"
        )

        // ── Probe FTS5 virtuals (both should already exist, no build) ──
        val fts5LatinOk = probeFts5Latin(db)
        val fts5CjkOk = probeFts5Cjk(db)
        val fts5Ok = fts5LatinOk || fts5CjkOk
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 🔎 fts5 · latin=$fts5LatinOk · cjk=$fts5CjkOk"
        )

        val ms = System.currentTimeMillis() - t0
        val ready = KbState.Ready(
            sizeBytes = kbFile.length(),
            // Original fields (preserved for SettingsCardBinders compat) :
            totalCodes = totalCodes,
            totalLanguages = totalLanguages,
            totalDdiPairs = totalDdiPairs,
            fts5Ok = fts5Ok,
            validationMs = ms,
            // New v2.2.16.2 fields :
            buildVersion = meta.version,
            buildTimestamp = meta.timestamp,
            buildSchema = meta.schema,
            buildLanguages = meta.languages,
            totalMedications = totalMedications,
            totalAllergens = totalAllergens,
            totalConditions = totalConditions,
            totalIpsValidated = totalIpsValidated,
            totalDdinterDrugs = totalDdinterDrugs,
            totalDdiFacts = totalDdiFacts,
            totalDfiPairs = totalDfiPairs,
            totalDrugDiseasePairs = totalDrugDiseasePairs,
            totalTherapeuticDuplications = totalTherapeuticDuplications,
            totalAtcCodes = totalAtcCodes,
            totalIpsValuesetEntries = totalIpsValuesetEntries,
            totalIpsTranslations = totalIpsTranslations,
            fts5LatinOk = fts5LatinOk,
            fts5CjkOk = fts5CjkOk,
        )

        database = db
        _state.value = ready

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ Ready · codes=$totalCodes · langs=$totalLanguages · " +
                "ddi=$totalDdiPairs · fts5=$fts5Ok (latin=$fts5LatinOk · cjk=$fts5CjkOk) · in ${ms}ms"
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Schema probes — aligned with build 2.0-omnis (3NF Clinical Forge)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * COUNT(*) on a single table, swallows errors if the table doesn't exist
     * (returns 0L). Logs at debug level for diagnostics.
     */
    private fun countTable(db: SQLiteDatabase, tableName: String): Long {
        return try {
            db.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
        } catch (e: Exception) {
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔍 table '$tableName' absent or unreadable : ${e.message}"
            )
            0L
        }
    }

    /**
     * COUNT(*) on a table with a custom WHERE clause. Used for filtered
     * counts like `ips_validated = 1`.
     */
    private fun countWhere(db: SQLiteDatabase, tableName: String, where: String): Long {
        return try {
            db.rawQuery("SELECT COUNT(*) FROM $tableName WHERE $where", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
        } catch (e: Exception) {
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔍 count '$tableName WHERE $where' failed : ${e.message}"
            )
            0L
        }
    }

    /**
     * COUNT(*) on terminology_codes filtered by a single category value.
     * Uses the partial index `idx_tc_category` for fast lookup.
     */
    private fun countTerminologyByCategory(db: SQLiteDatabase, category: String): Long {
        return try {
            db.rawQuery(
                "SELECT COUNT(*) FROM terminology_codes WHERE category = ?",
                arrayOf(category),
            ).use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
        } catch (e: Exception) {
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔍 count category='$category' failed : ${e.message}"
            )
            0L
        }
    }

    /**
     * Sum of `Chemical_Allergen` and `Protein_Allergen` (both UMLS-derived).
     * The KB has these as 2 separate categories ; we expose them merged for
     * the UI.
     */
    private fun countTerminologyAllergens(db: SQLiteDatabase): Long {
        return try {
            db.rawQuery(
                """
                SELECT COUNT(*) FROM terminology_codes
                WHERE category IN ('Chemical_Allergen', 'Protein_Allergen')
                """.trimIndent(),
                null,
            ).use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
        } catch (e: Exception) {
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔍 count allergens failed : ${e.message}"
            )
            0L
        }
    }

    /**
     * Number of distinct languages with display translations available.
     *
     * Source : `ips_valuesets_translations.lang` (FR / JA / ES / etc).
     * Note that `terminology_codes` itself only carries `primary_display`
     * (English canonical) — the multilingual surface lives in the
     * IPS valuesets translation table and the FTS5 virtuals.
     *
     * Returns 0 if the table is absent (defensive — would mean a wildly
     * different KB build).
     */
    private fun countDistinctLanguages(db: SQLiteDatabase): Int {
        return try {
            db.rawQuery(
                "SELECT COUNT(DISTINCT lang) FROM ips_valuesets_translations",
                null,
            ).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔍 lang probe failed : ${e.message}"
            )
            0
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Build metadata reader
    // ──────────────────────────────────────────────────────────────────────

    private data class BuildMeta(
        val version: String?,
        val variant: String?,
        val languages: String?,
        val timestamp: String?,
        val schema: String?,
    )

    /**
     * Read the 5 well-known keys from `build_metadata`. Returns nulls if the
     * table or any specific key is missing.
     */
    private fun readBuildMetadata(db: SQLiteDatabase): BuildMeta {
        val map = mutableMapOf<String, String>()
        try {
            db.rawQuery("SELECT key, value FROM build_metadata", null).use { c ->
                while (c.moveToNext()) {
                    val k = c.getString(0) ?: continue
                    val v = c.getString(1) ?: continue
                    map[k] = v
                }
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ build_metadata unreadable : ${e.message}"
            )
        }
        return BuildMeta(
            version = map["build_version"],
            variant = map["build_variant"],
            languages = map["build_languages"],
            timestamp = map["build_timestamp"],
            schema = map["build_schema"],
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // FTS5 probes (verify the two pre-built virtuals respond)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Run a tiny no-op MATCH against `terminology_latin` to confirm FTS5 is
     * functional and the virtual table is queryable. We use a generic prefix
     * pattern that should match plenty of rows in any pharma terminology.
     */
    private fun probeFts5Latin(db: SQLiteDatabase): Boolean = probeFtsTable(db, "terminology_latin", "a*")

    /**
     * Same idea for the CJK virtual. We use a short trigram-friendly prefix.
     * The CJK tokenizer is `trigram case_sensitive 0` — it works on bigrams
     * and trigrams of CJK characters and Latin alike. A simple "a*" prefix
     * is a valid query on this tokenizer.
     */
    private fun probeFts5Cjk(db: SQLiteDatabase): Boolean = probeFtsTable(db, "terminology_cjk", "a*")

    private fun probeFtsTable(db: SQLiteDatabase, table: String, query: String): Boolean {
        return try {
            // Confirm the virtual table is registered.
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                arrayOf(table),
            ).use { c ->
                if (!c.moveToFirst()) {
                    Log.d(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 🔍 fts5 probe : table '$table' not registered"
                    )
                    return false
                }
            }
            // Try a no-op MATCH ; if FTS5 isn't compiled in, this throws.
            db.rawQuery(
                "SELECT rowid FROM $table WHERE $table MATCH ? LIMIT 1",
                arrayOf(query),
            ).use { /* discard result */ }
            true
        } catch (e: Exception) {
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔍 fts5 probe '$table' failed : ${e.message}"
            )
            false
        }
    }

    private fun closeQuietly() {
        database?.let {
            try {
                it.close()
                Log.d(TAG, "[t=${System.currentTimeMillis()}] 🛑 KB closed")
            } catch (_: Exception) { }
        }
        database = null
    }
}
