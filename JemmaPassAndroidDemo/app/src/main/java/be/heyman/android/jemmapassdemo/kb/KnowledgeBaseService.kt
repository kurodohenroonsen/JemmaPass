/*
 * KnowledgeBaseService.kt — v2.2.16.3
 *
 * Suspend-friendly query service over the Clinical Forge KB build 2.0-omnis.
 * Wraps the raw `SQLiteDatabase` exposed by [KnowledgeBaseManager] with a
 * clean coroutine-safe API aligned on the REAL 3NF schema :
 *
 *   • terminology_codes (1.4M rows · UMLS+SNOMED · ips_validated subset)
 *   • ddinter_drugs (2 289 curated drugs with primary_atc + atc_codes CSV)
 *   • ddi_facts + ddi_atc_pairs (260K facts × 876K ATC pair rows)
 *   • interactions_food (857 rows)
 *   • drug_disease_interactions (8 121 rows)
 *   • atc_hierarchy (6 934 rows · 5 levels · ⚠ name_fr/jp empty in 2.0-omnis)
 *   • ips_valuesets + ips_valuesets_translations (20 langs · 85 736 entries)
 *   • terminology_latin / terminology_cjk (FTS5 virtuals, pre-built)
 *
 * Key design points :
 *   • All public methods are `suspend` and run on Dispatchers.IO.
 *   • DDI lookups go through `v_ddi_emergency` (pre-filtered Major+Moderate)
 *     for emergency speed, or `v_interactions_drug` for full-severity scans.
 *   • Multilingual displays come from `ips_valuesets_translations`, NOT
 *     from `atc_hierarchy.name_fr/jp` which are NULL in this build.
 *   • Search uses `terminology_latin` (Romance + JP romaji) or
 *     `terminology_cjk` (kanji/kana/Chinese) depending on script detection.
 *   • Falls back to LIKE on `ddinter_drugs.name COLLATE NOCASE` when FTS5
 *     fails or the query is below MIN_CHARS.
 *
 * Threading note :
 *   SQLiteDatabase from requery is thread-safe for concurrent reads
 *   (multi-reader/single-writer SQLite serialization). We never write
 *   from this service. The KnowledgeBaseManager is the sole connection
 *   owner.
 *
 * Mapping JS reference → Kotlin :
 *   The legacy JS `jemma_kb_service.js` queries non-existent tables
 *   (`codes`, `interactions_drug`). We're NOT porting that 1:1 — we're
 *   re-implementing against the actual schema as observed via sqlite3.
 *   The semantics are equivalent (resolveDrug, queryDDI, etc.) but the
 *   SQL is fundamentally different.
 */
package be.heyman.android.jemmapassdemo.kb

import android.util.Log
import java.util.Locale
import io.requery.android.database.sqlite.SQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class KnowledgeBaseService @Inject constructor(
    private val kbManager: KnowledgeBaseManager,
) {

    companion object {
        private const val TAG = "JEMMA-KB-SVC"

        /** How long suspend methods wait for KB readiness before giving up. */
        private const val READY_TIMEOUT_MS = 10_000L

        /** Min query length for live FTS5 search. */
        private const val SEARCH_MIN_CHARS = 2

        /** Hard limit on search result size. */
        private const val SEARCH_MAX_RESULTS = 20

        /** SNOMED CT URI used as the system identifier. */
        const val SYSTEM_SNOMED = "http://snomed.info/sct"

        /** RxNorm URI used as the system identifier. */
        const val SYSTEM_RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm"

        /** UMLS CUI namespace used by terminology_codes for ~1.4M of the 1.45M rows. */
        const val SYSTEM_UMLS = "urn:umls"

        /**
         * 🆕 Lot 14.5c6 — ATC code shape : 1 letter + 2 digits + 2 letters + 2 digits.
         * E.g. R06AX29 (bilastine), J01CA04 (amoxicillin/clavulanate), B01AA03 (warfarin).
         * Used by [resolveDrug] to detect when the input is actually a code (not a
         * drug name) so it can fall back to a direct `atc_code` lookup in
         * `terminology_codes` for drugs that aren't in `ddinter_drugs`.
         */
        private val atcCodeShape = Regex("^[A-Z][0-9]{2}[A-Z]{2}[0-9]{2}$")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Readiness gate
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Suspend until KB is Ready, or return null if it doesn't become Ready
     * within [READY_TIMEOUT_MS]. We don't trigger ensureInitialized here —
     * that's [JemmaApplication]'s job at boot. We just observe the state
     * flow.
     */
    private suspend fun awaitDb(): SQLiteDatabase? {
        val current = kbManager.state.value
        if (current is KbState.Ready) return kbManager.database()

        val ready = withTimeoutOrNull(READY_TIMEOUT_MS) {
            kbManager.state.firstOrNull { it is KbState.Ready }
        }
        if (ready == null) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⏱️ awaitDb timeout · current=$current"
            )
            return null
        }
        return kbManager.database()
    }

    // ══════════════════════════════════════════════════════════════════════
    // resolveDrug — by name or code
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Resolve a free-text drug name (or ATC/RxNorm code) to a [KbConcept].
     *
     * Resolution strategy (3 tiers, returns on first hit) :
     *   1. EXACT — case-insensitive on `ddinter_drugs.name`
     *      (uses `idx_dd_name_lower` partial index for O(log n) lookup)
     *   2. ATC — case-insensitive against `ddinter_drugs.primary_atc` AND
     *      against the comma-separated `atc_codes` (CSV match via LIKE)
     *   3. PREFIX — `ddinter_drugs.name LIKE 'q%'`
     *
     * If all 3 fail and FTS5 is available, retry via [searchCodes] limited
     * to category='Medication' and pick the top result with
     * [ResolvedConcept.Contains].
     *
     * Returns [ResolvedConcept.NotFound] otherwise.
     */
    suspend fun resolveDrug(name: String?): ResolvedConcept = withContext(Dispatchers.IO) {
        if (name.isNullOrBlank()) return@withContext ResolvedConcept.NotFound
        val db = awaitDb() ?: return@withContext ResolvedConcept.NotFound
        val q = name.trim()
        val qUpper = q.uppercase()
        val qLower = q.lowercase()

        // 🆕 Translation/Transliteration fallback for CJK / French emergency drugs.
        // If the query matches common non-English names or typos (especially from OCR),
        // we map them to their standard English equivalents to ensure they hit ddinter_drugs or English terminology.
        val normalizedQuery = when {
            qLower.contains("オーグメンチン") || qLower.contains("オーメンチン") || qLower.contains("オグメンチン") ||
            qLower.contains("augmentin") || qLower.contains("omentin") -> "Augmentin"
            qLower.contains("アモキシシリン") || qLower.contains("amoxicill") -> "Amoxicillin"
            qLower.contains("アスピリン") || qLower.contains("バイアスピリン") || qLower.contains("aspirin") -> "Aspirin"
            qLower.contains("ロキソニン") || qLower.contains("ロキソプロフェン") || qLower.contains("loxoprofen") -> "Loxoprofen"
            qLower.contains("カロナール") || qLower.contains("アセトアミノフェン") || qLower.contains("acetaminophen") -> "Acetaminophen"
            qLower.contains("ワーファリン") || qLower.contains("warfarin") -> "Warfarin"
            qLower.contains("バファリン") -> "Aspirin"
            qLower.contains("クラビット") || qLower.contains("レボフロキサシン") || qLower.contains("levofloxacin") -> "Levofloxacin"
            qLower.contains("アドレナリン") || qLower.contains("エピネフリン") || qLower.contains("epinephrine") -> "Epinephrine"
            qLower.contains("ボルタレン") || qLower.contains("diclofenac") -> "Diclofenac"
            else -> q
        }

        if (normalizedQuery != q) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🩹 resolveDrug('$q') → normalized to '$normalizedQuery'")
            return@withContext resolveDrug(normalizedQuery)
        }

        // Tier 1 : exact case-insensitive name match in ddinter_drugs.
        val exact = queryDdinterRow(
            db,
            sql = """
                SELECT ddinter_id, name, primary_atc, atc_codes
                FROM ddinter_drugs
                WHERE name = ? COLLATE NOCASE
                LIMIT 1
            """.trimIndent(),
            args = arrayOf(q),
        )
        if (exact != null) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 resolveDrug('$q') → exact (ddinter=${exact.ddinterId}, atc=${exact.primaryAtc})")
            return@withContext ResolvedConcept.Exact(exact.toKbConcept())
        }

        // 🆕 Lot 14.5c6 — Tier 1.5 : si la query ressemble à un code ATC
        // (format A##XX## comme R06AX29), faire un lookup direct dans
        // `terminology_codes` par atc_code. Ce tier capture les médocs qui
        // ne sont PAS dans ddinter (e.g. Bilastine sans interactions
        // cliniques documentées) mais qui ont leur entrée RxNorm/UMLS dans
        // terminology_codes. Sans ce tier, la liste des médocs affichait
        // "R06AX29" au lieu de "Bilastine only product" car la résolution
        // par code échouait sur tous les autres tiers.
        if (atcCodeShape.matches(qUpper)) {
            val byAtc = queryTerminologyRow(
                db,
                sql = """
                    SELECT code, atc_code, rxnorm_cui, snomed_code, ips_validated,
                           primary_display, system, category
                    FROM terminology_codes
                    WHERE atc_code = ? COLLATE NOCASE
                      AND category = 'Medication'
                      AND primary_display IS NOT NULL
                    ORDER BY ips_validated DESC, length(primary_display) ASC
                    LIMIT 1
                """.trimIndent(),
                args = arrayOf(qUpper),
            )
            if (byAtc != null) {
                // 🆕 Lot 14.5c9.6 — If there is a clean generic name in atc_hierarchy for this ATC code,
                // use it to override the primaryDisplay to avoid showing branded names like "Biomox Pill"!
                val cleanGeneric = queryAtcHierarchyName(db, qUpper)
                val finalConcept = if (cleanGeneric != null) {
                    byAtc.copy(primaryDisplay = cleanGeneric)
                } else {
                    byAtc
                }
                Log.d(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🔍 resolveDrug('$q') → atc lookup in terminology_codes " +
                        "(code=${finalConcept.code} display='${finalConcept.primaryDisplay}')",
                )
                return@withContext ResolvedConcept.Exact(finalConcept)
            }
        }

        // Tier 2 : ATC code match (primary OR within atc_codes CSV).
        val atcMatch = queryDdinterRow(
            db,
            sql = """
                SELECT ddinter_id, name, primary_atc, atc_codes
                FROM ddinter_drugs
                WHERE primary_atc = ? COLLATE NOCASE
                   OR atc_codes LIKE ? COLLATE NOCASE
                   OR atc_codes LIKE ? COLLATE NOCASE
                   OR atc_codes LIKE ? COLLATE NOCASE
                   OR atc_codes = ? COLLATE NOCASE
                LIMIT 1
            """.trimIndent(),
            args = arrayOf(
                qUpper,
                "$qUpper,%",       // ATC at start of CSV
                "%,$qUpper,%",     // ATC in middle
                "%,$qUpper",       // ATC at end
                qUpper,            // ATC is the only one
            ),
        )
        if (atcMatch != null) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 resolveDrug('$q') → atc match (${atcMatch.ddinterId}/${atcMatch.primaryAtc})")
            val concept = atcMatch.toKbConcept()
            // 🆕 Lot 14.5c9.6 — If there is a clean generic name in atc_hierarchy for this ATC code,
            // use it to override the primaryDisplay to avoid showing branded names like "Biomox Pill"!
            val cleanGeneric = queryAtcHierarchyName(db, qUpper)
            val finalConcept = if (cleanGeneric != null) {
                concept.copy(primaryDisplay = cleanGeneric)
            } else {
                concept
            }
            return@withContext ResolvedConcept.Exact(finalConcept)
        }

        // Tier 3 : prefix match.
        val prefix = queryDdinterRow(
            db,
            sql = """
                SELECT ddinter_id, name, primary_atc, atc_codes
                FROM ddinter_drugs
                WHERE name LIKE ? COLLATE NOCASE
                ORDER BY length(name) ASC
                LIMIT 1
            """.trimIndent(),
            args = arrayOf("$q%"),
        )
        if (prefix != null) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 resolveDrug('$q') → prefix (${prefix.ddinterId})")
            return@withContext ResolvedConcept.Prefix(prefix.toKbConcept())
        }

        // Tier 4 : fall through to FTS5 search restricted to medications.
        // We use the existing search method to leverage the same index
        // logic (latin/cjk script detection).
        val search = searchCodes(q, lang = "en", categoryFilter = "Medication", maxResults = 1)
        if (search is KbSearchResult.Success && search.hits.isNotEmpty()) {
            val hit = search.hits.first()
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 resolveDrug('$q') → fts contains (${hit.concept.code})")
            return@withContext ResolvedConcept.Contains(hit.concept)
        }

        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 resolveDrug('$q') → not_found")
        ResolvedConcept.NotFound
    }

    // ══════════════════════════════════════════════════════════════════════
    // resolveAllergy — by SNOMED code or free-text allergen name
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Resolve a free-text allergen name to a [KbConcept].
     *
     * Strategy :
     *   1. EXACT on `terminology_codes.primary_display` (case-insensitive),
     *      restricted to allergen-like categories (Chemical_Allergen,
     *      Protein_Allergen).
     *   2. CONTAINS on primary_display, same restriction.
     *   3. FTS5 fallback via [searchCodes] without category filter
     *      (allergens have very heterogeneous category labelling — some
     *      land under 'Condition' if it's an immune disorder).
     *
     * For best results, callers should prefer [resolveByCode] when a SNOMED
     * code is available (allergies in the QR profile usually carry one).
     */
    suspend fun resolveAllergy(name: String?): ResolvedConcept = withContext(Dispatchers.IO) {
        if (name.isNullOrBlank()) return@withContext ResolvedConcept.NotFound
        val db = awaitDb() ?: return@withContext ResolvedConcept.NotFound
        val q = name.trim()

        // Tier 1 : exact on primary_display, allergen categories only.
        val exact = queryTerminologyRow(
            db,
            sql = """
                SELECT code, atc_code, rxnorm_cui, snomed_code, ips_validated,
                       primary_display, system, category
                FROM terminology_codes
                WHERE primary_display = ? COLLATE NOCASE
                  AND category IN ('Chemical_Allergen', 'Protein_Allergen')
                LIMIT 1
            """.trimIndent(),
            args = arrayOf(q),
        )
        if (exact != null) {
            return@withContext ResolvedConcept.Exact(exact)
        }

        // Tier 2 : contains, allergen categories.
        val contains = queryTerminologyRow(
            db,
            sql = """
                SELECT code, atc_code, rxnorm_cui, snomed_code, ips_validated,
                       primary_display, system, category
                FROM terminology_codes
                WHERE primary_display LIKE ? COLLATE NOCASE
                  AND category IN ('Chemical_Allergen', 'Protein_Allergen')
                ORDER BY ips_validated DESC, length(primary_display) ASC
                LIMIT 1
            """.trimIndent(),
            args = arrayOf("%$q%"),
        )
        if (contains != null) {
            return@withContext ResolvedConcept.Contains(contains)
        }

        // Tier 3 : FTS5 fallback without category filter.
        val search = searchCodes(q, lang = "en", maxResults = 1)
        if (search is KbSearchResult.Success && search.hits.isNotEmpty()) {
            val hit = search.hits.first()
            return@withContext ResolvedConcept.Contains(hit.concept)
        }

        Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 resolveAllergy('$q') → not_found")
        ResolvedConcept.NotFound
    }

    // ══════════════════════════════════════════════════════════════════════
    // resolveByCode — exact lookup by code+system
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Look up a row by canonical (code, system) tuple. The search column
     * depends on the system :
     *
     *   • SYSTEM_SNOMED → match on `snomed_code` (preferred) or `code`
     *   • SYSTEM_RXNORM → match on `rxnorm_cui`
     *   • SYSTEM_UMLS   → match on `code` (UMLS CUI)
     *   • else          → fallback to `code` + `system` exact match
     *
     * Returns [ResolvedConcept.Exact] on hit, [ResolvedConcept.NotFound]
     * otherwise.
     *
     * Ues case : hydrate a profile QR code (e.g. SNOMED `91936005` for
     * "Allergy to penicillin") to a full KbConcept with primary_display.
     */
    suspend fun resolveByCode(code: String?, system: String?): ResolvedConcept =
        withContext(Dispatchers.IO) {
            if (code.isNullOrBlank() || system.isNullOrBlank()) return@withContext ResolvedConcept.NotFound
            val db = awaitDb() ?: return@withContext ResolvedConcept.NotFound

            val (whereClause, args) = when (system) {
                SYSTEM_SNOMED -> "(snomed_code = ? OR code = ?)" to arrayOf(code, code)
                SYSTEM_RXNORM -> "rxnorm_cui = ?" to arrayOf(code)
                SYSTEM_UMLS -> "code = ?" to arrayOf(code)
                else -> "code = ? AND system = ?" to arrayOf(code, system)
            }

            val concept = queryTerminologyRow(
                db,
                sql = """
                    SELECT code, atc_code, rxnorm_cui, snomed_code, ips_validated,
                           primary_display, system, category
                    FROM terminology_codes
                    WHERE $whereClause
                    LIMIT 1
                """.trimIndent(),
                args = args,
            )
            if (concept != null) ResolvedConcept.Exact(concept) else ResolvedConcept.NotFound
        }

    // ══════════════════════════════════════════════════════════════════════
    // queryDDIByAtc — emergency-grade Drug-Drug Interaction lookup
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Look up a Major or Moderate interaction between two ATC codes via
     * the pre-filtered `v_ddi_emergency` view. Symmetric — argument order
     * doesn't matter.
     *
     * This is the **killer demo path** for the rescuer use case : 1 SQL
     * call, fully indexed via `idx_dap_pair`, typically <5ms. If the pair
     * is not in v_ddi_emergency (i.e. it's Minor or absent), returns
     * [DDIResult.None].
     *
     * For full-severity scans (including Minor), use [queryDDIDetailed].
     *
     * Killer demo usage :
     * ```
     * val r = kb.queryDDIByAtc("B01AA03", "M01AE01")
     * // → DDIResult.Found(
     * //     severity=MAJOR,
     * //     mechanism="PD_Synergistic",
     * //     description="NSAIDs may potentiate the hypoprothrombinemic...",
     * //     management="The INR should be checked frequently...",
     * //     alternativeAtc="B01A",
     * //     drugAResolved="B01AA03",
     * //     drugBResolved="M01AE01")
     * ```
     */
    suspend fun queryDDIByAtc(atcA: String?, atcB: String?): DDIResult =
        withContext(Dispatchers.IO) {
            queryDDIInternal(atcA, atcB, viewName = "v_ddi_emergency")
        }

    /**
     * Same as [queryDDIByAtc] but searches `v_interactions_drug` (full
     * join, all severities including Minor and Unknown). Slightly slower
     * because the view has more rows, but still <10ms thanks to the same
     * `idx_dap_pair` index.
     */
    suspend fun queryDDIDetailed(atcA: String?, atcB: String?): DDIResult =
        withContext(Dispatchers.IO) {
            queryDDIInternal(atcA, atcB, viewName = "v_interactions_drug")
        }

    /**
     * Backward-compat wrapper for callers that have free-text drug names
     * (or codes) rather than ATC codes. Resolves both inputs via
     * [resolveDrug] then delegates to [queryDDIByAtc].
     *
     * If either input fails to resolve to an ATC code, returns
     * [DDIResult.None] with the original input strings as resolved labels.
     *
     * Example :
     * ```
     * kb.queryDDI("Warfarin", "Ibuprofen")
     * → resolves Warfarin → "B01AA03", Ibuprofen → "G02CC01" (primary_atc)
     * → calls queryDDIByAtc("B01AA03", "G02CC01")
     * ```
     *
     * Note : Ibuprofen has primary_atc=G02CC01 but its M01AE01 (NSAID)
     * variant is the one with documented Warfarin DDI in v_ddi_emergency.
     * For accurate DDI scans, prefer feeding ATC codes directly when
     * available, and consider iterating over [KbConcept.allAtcCodes] to
     * find the strongest match.
     */
    suspend fun queryDDI(drugA: String?, drugB: String?): DDIResult =
        withContext(Dispatchers.IO) {
            val tStart = System.currentTimeMillis()
            if (drugA.isNullOrBlank() || drugB.isNullOrBlank()) {
                return@withContext DDIResult.Error("drugA and drugB required", 0L)
            }

            // Resolve each input. If the input is already an ATC-shaped
            // string (1 letter + 2 digits + 2 letters + 2 digits), accept
            // it directly without DB lookup to save time.
            suspend fun resolveToAtc(input: String): String? {
                val trimmed = input.trim().uppercase()
                if (Regex("""^[A-Z]\d{2}[A-Z]{2}\d{2}$""").matches(trimmed)) {
                    return trimmed
                }
                val resolved = resolveDrug(input)
                return resolved.concept?.atcCode
                    ?: resolved.concept?.allAtcCodes?.firstOrNull()
            }

            val atcA = resolveToAtc(drugA)
            val atcB = resolveToAtc(drugB)

            if (atcA == null || atcB == null) {
                Log.d(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🩹 queryDDI('$drugA','$drugB') → no ATC resolved (a=$atcA · b=$atcB)"
                )
                return@withContext DDIResult.None(
                    drugAResolved = drugA,
                    drugBResolved = drugB,
                    queryDurationMs = System.currentTimeMillis() - tStart,
                )
            }

            // Delegate to ATC-based lookup.
            val result = queryDDIByAtc(atcA, atcB)
            // Re-wrap with the original names in the resolved fields for
            // readability in the caller's UI.
            when (result) {
                is DDIResult.Found -> result.copy(
                    drugAResolved = "$drugA ($atcA)",
                    drugBResolved = "$drugB ($atcB)",
                )
                is DDIResult.None -> result.copy(
                    drugAResolved = "$drugA ($atcA)",
                    drugBResolved = "$drugB ($atcB)",
                )
                is DDIResult.Error -> result
            }
        }

    /**
     * Shared implementation for [queryDDIByAtc] / [queryDDIDetailed].
     *
     * The two views have slightly different column sets but share the
     * 7 columns we read here.
     */
    private fun queryDDIInternal(
        atcA: String?,
        atcB: String?,
        viewName: String,
    ): DDIResult {
        val tStart = System.currentTimeMillis()
        if (atcA.isNullOrBlank() || atcB.isNullOrBlank()) {
            return DDIResult.Error("atcA and atcB required", 0L)
        }
        val db = kbManager.database() ?: return DDIResult.Error("KB not ready", 0L)

        val a = atcA.trim().uppercase()
        val b = atcB.trim().uppercase()

        // Symmetric lookup : (a,b) OR (b,a).
        // ORDER BY severity ranks Major before Moderate before Minor so
        // worst-case wins even if the same pair has multiple facts.
        val sql = """
            SELECT d.severity, d.mechanism_category, d.description_en, d.management_en, d.alternative_atc,
                   d.drug_a_atc, d.drug_b_atc, h.name_en, h.name_fr
            FROM $viewName d
            LEFT JOIN atc_hierarchy h ON h.atc_code = d.alternative_atc
            WHERE (d.drug_a_atc = ? AND d.drug_b_atc = ?)
               OR (d.drug_a_atc = ? AND d.drug_b_atc = ?)
            ORDER BY
                CASE d.severity
                    WHEN 'Major'    THEN 1
                    WHEN 'Moderate' THEN 2
                    WHEN 'Minor'    THEN 3
                    ELSE 4
                END
            LIMIT 1
        """.trimIndent()

        return try {
            db.rawQuery(sql, arrayOf(a, b, b, a)).use { c ->
                val durMs = System.currentTimeMillis() - tStart
                if (!c.moveToFirst()) {
                    Log.d(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 🩹 queryDDI($a×$b · $viewName) → none in ${durMs}ms"
                    )
                    return@use DDIResult.None(a, b, durMs)
                }
                val sev = DDIResult.Severity.fromString(c.getString(0))
                val mech = c.getStringOrNull(1)
                val desc = c.getStringOrNull(2)
                val mgmt = c.getStringOrNull(3)
                val rawAlt = c.getStringOrNull(4)
                val drugA = c.getStringOrNull(5) ?: a
                val drugB = c.getStringOrNull(6) ?: b
                val altEn = c.getStringOrNull(7)
                val altFr = c.getStringOrNull(8)
                val altName = if (Locale.getDefault().language == "fr" && !altFr.isNullOrBlank()) altFr else altEn
                val alt = if (!rawAlt.isNullOrBlank()) {
                    if (!altName.isNullOrBlank()) "$rawAlt - $altName" else rawAlt
                } else null

                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}] 🩹 queryDDI($a×$b · $viewName) → ${sev.label} · $mech in ${durMs}ms"
                )
                DDIResult.Found(
                    severity = sev,
                    mechanism = mech,
                    description = desc,
                    management = mgmt,
                    alternativeAtc = alt,
                    drugAResolved = drugA,
                    drugBResolved = drugB,
                    queryDurationMs = durMs,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ queryDDI failed : ${e.message}")
            DDIResult.Error(
                e.message ?: "unknown",
                System.currentTimeMillis() - tStart,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // queryDFI — Drug-Food Interaction
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Look up an interaction between a drug (by ATC) and a food name.
     *
     * Search is case-insensitive on `food_name_en`. The 857-row table is
     * tiny so even a full LIKE scan is sub-ms.
     *
     * @param atc full ATC code (e.g. "B01AA03" for Warfarin) ; primary_atc
     *            from the drug profile
     * @param foodName free-text food name (English, e.g. "grapefruit")
     */
    suspend fun queryDFI(atc: String?, foodName: String?): DFIResult =
        withContext(Dispatchers.IO) {
            val tStart = System.currentTimeMillis()
            if (atc.isNullOrBlank() || foodName.isNullOrBlank()) {
                return@withContext DFIResult.Error("atc and foodName required", 0L)
            }
            val db = awaitDb() ?: return@withContext DFIResult.Error(
                "KB not ready",
                System.currentTimeMillis() - tStart,
            )
            val a = atc.trim().uppercase()
            val f = foodName.trim()

            try {
                db.rawQuery(
                    """
                    SELECT severity, mechanism_category, description_en, management_en
                    FROM interactions_food
                    WHERE drug_atc = ?
                      AND food_name_en LIKE ? COLLATE NOCASE
                    ORDER BY
                        CASE severity
                            WHEN 'Major'    THEN 1
                            WHEN 'Moderate' THEN 2
                            WHEN 'Minor'    THEN 3
                            ELSE 4
                        END
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(a, "%$f%"),
                ).use { c ->
                    val durMs = System.currentTimeMillis() - tStart
                    if (!c.moveToFirst()) return@use DFIResult.None(durMs)
                    val sev = DDIResult.Severity.fromString(c.getString(0))
                    DFIResult.Found(
                        severity = sev,
                        mechanism = c.getStringOrNull(1),
                        description = c.getStringOrNull(2),
                        management = c.getStringOrNull(3),
                        queryDurationMs = durMs,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ queryDFI failed : ${e.message}")
                DFIResult.Error(
                    e.message ?: "unknown",
                    System.currentTimeMillis() - tStart,
                )
            }
        }

    // ══════════════════════════════════════════════════════════════════════
    // queryDrugDisease — contraindication lookup
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Check whether a drug (by ATC) has a documented contraindication for
     * a given disease (free-text English name).
     *
     * Backed by `drug_disease_interactions` (8 121 rows). Use case :
     * patient has chronic kidney disease, prescribed metformin → flag
     * elevated risk of lactic acidosis.
     *
     * @param atc full ATC code of the drug
     * @param diseaseName English free-text disease name (case-insensitive
     *                    LIKE match on `disease_name_en`)
     */
    suspend fun queryDrugDisease(atc: String?, diseaseName: String?): DrugDiseaseResult =
        withContext(Dispatchers.IO) {
            val tStart = System.currentTimeMillis()
            if (atc.isNullOrBlank() || diseaseName.isNullOrBlank()) {
                return@withContext DrugDiseaseResult.Error("atc and diseaseName required", 0L)
            }
            val db = awaitDb() ?: return@withContext DrugDiseaseResult.Error(
                "KB not ready",
                System.currentTimeMillis() - tStart,
            )
            val a = atc.trim().uppercase()
            val d = diseaseName.trim()

            try {
                db.rawQuery(
                    """
                    SELECT severity, description_en, management_en, drug_atc, disease_name_en
                    FROM drug_disease_interactions
                    WHERE drug_atc = ?
                      AND disease_name_en LIKE ? COLLATE NOCASE
                    ORDER BY
                        CASE severity
                            WHEN 'Major'    THEN 1
                            WHEN 'Moderate' THEN 2
                            WHEN 'Minor'    THEN 3
                            ELSE 4
                        END
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(a, "%$d%"),
                ).use { c ->
                    val durMs = System.currentTimeMillis() - tStart
                    if (!c.moveToFirst()) return@use DrugDiseaseResult.None(durMs)
                    val sev = DDIResult.Severity.fromString(c.getString(0))
                    DrugDiseaseResult.Found(
                        severity = sev,
                        description = c.getStringOrNull(1),
                        management = c.getStringOrNull(2),
                        drugAtc = c.getStringOrNull(3) ?: a,
                        diseaseName = c.getStringOrNull(4) ?: d,
                        queryDurationMs = durMs,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ queryDrugDisease failed : ${e.message}")
                DrugDiseaseResult.Error(
                    e.message ?: "unknown",
                    System.currentTimeMillis() - tStart,
                )
            }
        }

    // ══════════════════════════════════════════════════════════════════════
    // searchCodes — FTS5 live search (latin / cjk / fallback LIKE)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Live autocomplete search across `terminology_codes` via the two
     * pre-built FTS5 virtuals.
     *
     * Backend selection :
     *   • Latin script (no CJK chars in query) → `terminology_latin`
     *     (tokenize='unicode61 remove_diacritics 2')
     *   • CJK chars present in query → `terminology_cjk`
     *     (tokenize='trigram case_sensitive 0')
     *   • If the chosen FTS5 throws → fallback to LIKE on
     *     `terminology_codes.primary_display` (slower but still works)
     *
     * @param query raw user input (sanitized internally)
     * @param lang  BCP-47 language code for the localized display field
     *              ('en', 'fr', 'ja', 'es', etc). Default 'en'.
     * @param categoryFilter optional `terminology_codes.category` filter
     *                       (e.g. "Medication", "Condition"). Null = no filter.
     * @param maxResults hard limit, default 20.
     */
    suspend fun searchCodes(
        query: String?,
        lang: String = "en",
        categoryFilter: String? = null,
        maxResults: Int = SEARCH_MAX_RESULTS,
    ): KbSearchResult = withContext(Dispatchers.IO) {
        val tStart = System.currentTimeMillis()
        val q = query?.trim().orEmpty()
        if (q.length < SEARCH_MIN_CHARS) {
            return@withContext KbSearchResult.Success(emptyList(), 0L, usedFts5 = false)
        }
        val db = awaitDb() ?: return@withContext KbSearchResult.Error(
            "KB not ready",
            System.currentTimeMillis() - tStart,
        )

        // Detect script : if any char is in CJK Unicode ranges, use cjk.
        val useCjk = containsCjk(q)
        val backend = if (useCjk) "terminology_cjk" else "terminology_latin"

        // Try FTS5 first.
        val ftsRows = trySearchFts(db, q, backend, categoryFilter, maxResults)
        if (ftsRows != null) {
            val hits = ftsRows.map { row ->
                KbSearchHit(
                    concept = row,
                    display = pickDisplay(row, lang),
                    matchedVia = if (useCjk) "cjk" else "latin",
                )
            }
            val durMs = System.currentTimeMillis() - tStart
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔎 fts $backend \"$q\" cat=$categoryFilter → ${hits.size} (${durMs}ms)"
            )
            return@withContext KbSearchResult.Success(hits, durMs, usedFts5 = true)
        }

        // LIKE fallback : direct query on terminology_codes.primary_display.
        val likeRows = searchLike(db, q, categoryFilter, maxResults)
        val hits = likeRows.map { row ->
            KbSearchHit(
                concept = row,
                display = pickDisplay(row, lang),
                matchedVia = "like",
            )
        }
        val durMs = System.currentTimeMillis() - tStart
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] 🔎 like \"$q\" cat=$categoryFilter → ${hits.size} (${durMs}ms · fts5 unavailable)"
        )
        KbSearchResult.Success(hits, durMs, usedFts5 = false)
    }

    /**
     * Attempt FTS5 search against [backend] (`terminology_latin` or
     * `terminology_cjk`). Returns null if FTS5 throws (signals the caller
     * to fall back to LIKE).
     *
     * Joins back to `terminology_codes` to reconstruct the full concept.
     */
    private fun trySearchFts(
        db: SQLiteDatabase,
        rawQuery: String,
        backend: String,
        categoryFilter: String?,
        maxResults: Int,
    ): List<KbConcept>? {
        val ftsQ = sanitizeFtsQuery(rawQuery)
        if (ftsQ.isEmpty()) return emptyList()

        return try {
            val sql = if (categoryFilter != null) {
                """
                SELECT t.code, t.atc_code, t.rxnorm_cui, t.snomed_code, t.ips_validated,
                       t.primary_display, t.system, t.category
                FROM $backend f
                JOIN terminology_codes t ON t.code = f.code
                WHERE $backend MATCH ?
                  AND t.category = ?
                ORDER BY t.ips_validated DESC, length(t.primary_display) ASC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT t.code, t.atc_code, t.rxnorm_cui, t.snomed_code, t.ips_validated,
                       t.primary_display, t.system, t.category
                FROM $backend f
                JOIN terminology_codes t ON t.code = f.code
                WHERE $backend MATCH ?
                ORDER BY t.ips_validated DESC, length(t.primary_display) ASC
                LIMIT ?
                """.trimIndent()
            }
            val args = if (categoryFilter != null) {
                arrayOf(ftsQ, categoryFilter, maxResults.toString())
            } else {
                arrayOf(ftsQ, maxResults.toString())
            }
            db.rawQuery(sql, args).use { c -> readTerminologyConcepts(c) }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ FTS5 $backend failed (will fallback to LIKE) : ${e.message}"
            )
            null
        }
    }

    /**
     * LIKE-based fallback search, used when FTS5 is unavailable. Searches
     * `terminology_codes.primary_display` only (English-only fallback).
     */
    private fun searchLike(
        db: SQLiteDatabase,
        rawQuery: String,
        categoryFilter: String?,
        maxResults: Int,
    ): List<KbConcept> {
        val likePat = "%$rawQuery%"
        return try {
            val sql = if (categoryFilter != null) {
                """
                SELECT code, atc_code, rxnorm_cui, snomed_code, ips_validated,
                       primary_display, system, category
                FROM terminology_codes
                WHERE primary_display LIKE ? COLLATE NOCASE
                  AND category = ?
                ORDER BY ips_validated DESC, length(primary_display) ASC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT code, atc_code, rxnorm_cui, snomed_code, ips_validated,
                       primary_display, system, category
                FROM terminology_codes
                WHERE primary_display LIKE ? COLLATE NOCASE
                ORDER BY ips_validated DESC, length(primary_display) ASC
                LIMIT ?
                """.trimIndent()
            }
            val args = if (categoryFilter != null) {
                arrayOf(likePat, categoryFilter, maxResults.toString())
            } else {
                arrayOf(likePat, maxResults.toString())
            }
            db.rawQuery(sql, args).use { c -> readTerminologyConcepts(c) }
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ LIKE fallback failed : ${e.message}")
            emptyList()
        }
    }

    /**
     * Sanitize a raw user query for FTS5 :
     *   • Strip non-alphanumeric except spaces and hyphens (Latin)
     *   • For CJK queries, keep CJK chars as-is — the trigram tokenizer
     *     handles them natively
     *   • Append `*` to the last token for prefix match (live typing)
     */
    private fun sanitizeFtsQuery(raw: String): String {
        // For CJK, no diacritic stripping needed. Just trim and prefix-star.
        if (containsCjk(raw)) {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) "" else "$trimmed*"
        }
        val cleaned = raw.replace(Regex("""[^\w\s-]"""), " ").trim()
        if (cleaned.isEmpty()) return ""
        val tokens = cleaned.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val lastWithStar = tokens.last() + "*"
        return if (tokens.size == 1) {
            lastWithStar
        } else {
            tokens.dropLast(1).joinToString(" ") + " " + lastWithStar
        }
    }

    /**
     * Detect whether the string contains any CJK Unicode character.
     * Used to pick between `terminology_latin` and `terminology_cjk`
     * FTS5 backends. Coverage : Hiragana, Katakana, CJK Unified
     * Ideographs (Han), Hangul.
     */
    private fun containsCjk(s: String): Boolean {
        for (cp in s.codePoints().toArray()) {
            // Hiragana 3040-309F · Katakana 30A0-30FF
            // CJK Unified Ideographs 4E00-9FFF
            // Hangul Syllables AC00-D7AF
            // Halfwidth/Fullwidth FF00-FFEF
            if (cp in 0x3040..0x30FF
                || cp in 0x4E00..0x9FFF
                || cp in 0xAC00..0xD7AF
                || cp in 0xFF00..0xFFEF
            ) return true
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    // getAtcAncestors — climb the ATC hierarchy (5 levels)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Return the full chain of ATC ancestors for a given code, ordered
     * from the leaf (level 7, the specific drug) up to the root (level 1,
     * the anatomical group).
     *
     * Implementation : recursive CTE on `atc_hierarchy.parent_atc`.
     *
     * Example for Warfarin :
     *   getAtcAncestors("B01AA03") →
     *   [
     *     AtcNode("B01AA03", parent="B01AA",  level=7, name="warfarin"),
     *     AtcNode("B01AA",   parent="B01A",   level=5, name="Vitamin K antagonists"),
     *     AtcNode("B01A",    parent="B01",    level=4, name="ANTITHROMBOTIC AGENTS"),
     *     AtcNode("B01",     parent="B",      level=3, name="ANTITHROMBOTIC AGENTS"),
     *     AtcNode("B",       parent=null,     level=1, name="BLOOD AND BLOOD FORMING ORGANS"),
     *   ]
     *
     * Use case : matching a patient's allergy at the family level. If
     * the patient is allergic to ATC `J01CA01` (penicillin G) and the
     * profile prescribes any `J01CA*`, climb both ancestor chains and
     * find the common parent for an "allergy class match" warning.
     */
    suspend fun getAtcAncestors(atcCode: String?): List<AtcNode> = withContext(Dispatchers.IO) {
        if (atcCode.isNullOrBlank()) return@withContext emptyList()
        val db = awaitDb() ?: return@withContext emptyList()
        val q = atcCode.trim().uppercase()

        try {
            db.rawQuery(
                """
                WITH RECURSIVE ancestors(atc_code, parent_atc, level, name_en, name_fr, name_jp) AS (
                    SELECT atc_code, parent_atc, level, name_en, name_fr, name_jp
                    FROM atc_hierarchy WHERE atc_code = ?
                    UNION ALL
                    SELECT a.atc_code, a.parent_atc, a.level, a.name_en, a.name_fr, a.name_jp
                    FROM atc_hierarchy a
                    JOIN ancestors anc ON a.atc_code = anc.parent_atc
                )
                SELECT atc_code, parent_atc, level, name_en, name_fr, name_jp
                FROM ancestors
                """.trimIndent(),
                arrayOf(q),
            ).use { c ->
                val out = mutableListOf<AtcNode>()
                while (c.moveToNext()) {
                    out.add(
                        AtcNode(
                            atcCode = c.getString(0) ?: "",
                            parentAtc = c.getStringOrNull(1),
                            level = c.getInt(2),
                            nameEn = c.getStringOrNull(3) ?: "",
                            nameFr = c.getStringOrNull(4),
                            nameJp = c.getStringOrNull(5),
                        )
                    )
                }
                out
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ getAtcAncestors($q) failed : ${e.message}"
            )
            emptyList()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // getLocalizedDisplay — i18n via ips_valuesets_translations
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Look up a localized display string for a (code, system) tuple in a
     * specific language. Backed by `ips_valuesets_translations` which
     * carries 85 736 translations across 20 languages.
     *
     * Returns null if no translation exists for the given tuple+lang.
     *
     * Example :
     *   getLocalizedDisplay("102259006", SYSTEM_SNOMED, "fr")
     *   → "Fruits, agrumes" (or null if French translation missing)
     *
     * Note : not every (code, system) tuple has translations. Coverage is
     * highest on SNOMED IPS subset and lowest on UMLS proper. Caller
     * should fallback to [KbConcept.primaryDisplay] when null.
     */
    suspend fun getLocalizedDisplay(
        code: String?,
        system: String?,
        lang: String,
    ): String? = withContext(Dispatchers.IO) {
        if (code.isNullOrBlank() || system.isNullOrBlank() || lang.isBlank()) return@withContext null
        val db = awaitDb() ?: return@withContext null
        try {
            db.rawQuery(
                """
                SELECT display
                FROM ips_valuesets_translations
                WHERE code = ? AND code_system = ? AND lang = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(code, system, lang),
            ).use { c ->
                if (c.moveToFirst()) c.getStringOrNull(0) else null
            }
        } catch (e: Exception) {
            Log.d(
                TAG,
                "[t=${System.currentTimeMillis()}] 🌐 getLocalizedDisplay($code,$system,$lang) failed : ${e.message}"
            )
            null
        }
    }

    /**
     * Batch variant of [getLocalizedDisplay] for one code system : one `IN (…)`
     * query, returns `code → display` for the codes that have a translation in
     * [lang]. Used by the generic KB picker to localise SNOMED hits (procedures,
     * devices) without one query per row.
     */
    suspend fun getLocalizedDisplays(
        codes: Collection<String>,
        system: String,
        lang: String,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val distinct = codes.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty() || system.isBlank() || lang.isBlank()) return@withContext emptyMap()
        val db = awaitDb() ?: return@withContext emptyMap()
        val out = HashMap<String, String>()
        try {
            distinct.chunked(200).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                db.rawQuery(
                    """
                    SELECT code, display
                    FROM ips_valuesets_translations
                    WHERE code_system = ? AND lang = ? AND code IN ($placeholders)
                    """.trimIndent(),
                    arrayOf(system, lang, *chunk.toTypedArray()),
                ).use { c ->
                    while (c.moveToNext()) {
                        val code = c.getStringOrNull(0) ?: continue
                        val display = c.getStringOrNull(1) ?: continue
                        out.putIfAbsent(code, display)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🌐 getLocalizedDisplays(${distinct.size},$system,$lang) failed : ${e.message}")
        }
        out
    }

    /**
     * Pick the best display string for a [KbConcept] given a UI lang.
     * Tries (in order) :
     *   1. Localized display via `ips_valuesets_translations` (DB-backed,
     *      so this method is suspend in the i18n-aware variant —
     *      [pickLocalizedDisplay]).
     *   2. The 3 legacy display fields (displayFr/En/Jp) if populated.
     *   3. primaryDisplay (English canonical).
     *   4. The code itself (last resort).
     *
     * This non-suspend variant skips step 1 — for fast inline use in
     * search result rendering. Use [pickLocalizedDisplay] when you want
     * the full multilingual experience.
     */
    fun pickDisplay(concept: KbConcept, lang: String): String {
        val l = lang.lowercase()
        if (l == "fr" && !concept.displayFr.isNullOrBlank()) {
            return concept.displayFr
        }
        if ((l == "ja" || l == "jp") && !concept.displayJp.isNullOrBlank()) {
            return concept.displayJp
        }
        if (!concept.displayEn.isNullOrBlank()) {
            return concept.displayEn
        }
        if (!concept.primaryDisplay.isBlank()) {
            return concept.primaryDisplay
        }
        return concept.code
    }

    /**
     * i18n-aware variant of [pickDisplay] that hits
     * `ips_valuesets_translations` first. Only call from a suspend
     * context (does a DB lookup).
     */
    suspend fun pickLocalizedDisplay(concept: KbConcept, lang: String): String {
        // Map "jp" → "ja" (the KB uses "ja" per BCP-47).
        val ipsLang = when (lang.lowercase()) {
            "jp" -> "ja"
            else -> lang.lowercase()
        }
        // Skip the DB hit for English — primary_display is already EN.
        if (ipsLang == "en") return concept.primaryDisplay.ifBlank { concept.code }

        val translated = getLocalizedDisplay(concept.code, concept.system, ipsLang)
        if (!translated.isNullOrBlank()) return translated
        return pickDisplay(concept, lang)
    }

    // ══════════════════════════════════════════════════════════════════════
    // stats — headline counts
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Headline counts derived from [KbState.Ready]. Returns a snapshot ;
     * the values don't update unless the KB is reloaded.
     *
     * Returns a stats with all-zero fields if the KB isn't Ready.
     */
    suspend fun stats(): KbStats {
        val state = kbManager.state.value
        return if (state is KbState.Ready) {
            KbStats(
                codesCount = state.totalCodes,
                ddiCount = state.totalDdiPairs,
                dfiCount = state.totalDfiPairs,
                drugDiseaseCount = state.totalDrugDiseasePairs,
                ddinterDrugsCount = state.totalDdinterDrugs,
                atcCount = state.totalAtcCodes,
                languageCount = state.totalLanguages,
                fts5LatinAvailable = state.fts5LatinOk,
                fts5CjkAvailable = state.fts5CjkOk,
                buildVersion = state.buildVersion,
                buildTimestamp = state.buildTimestamp,
            )
        } else {
            KbStats(
                codesCount = 0,
                ddiCount = 0,
                dfiCount = 0,
                drugDiseaseCount = 0,
                ddinterDrugsCount = 0,
                atcCount = 0,
                languageCount = 0,
                fts5LatinAvailable = false,
                fts5CjkAvailable = false,
                buildVersion = null,
                buildTimestamp = null,
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // SQL helpers : terminology_codes mapping
    // ──────────────────────────────────────────────────────────────────────
    // SQL helpers : atc_hierarchy generic name lookup
    // ──────────────────────────────────────────────────────────────────────

    private fun queryAtcHierarchyName(db: SQLiteDatabase, atcCode: String): String? {
        val sql = "SELECT name_en FROM atc_hierarchy WHERE atc_code = ? LIMIT 1"
        return try {
            db.rawQuery(sql, arrayOf(atcCode)).use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) {
                        // Capitalize the first letter for professional display
                        name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query atc_hierarchy name: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────

    /**
     * Run a SELECT against `terminology_codes` (8-column projection) and
     * return the first row mapped to a [KbConcept], or null if no match.
     *
     * Expected column order :
     *   code, atc_code, rxnorm_cui, snomed_code, ips_validated,
     *   primary_display, system, category
     */
    private fun queryTerminologyRow(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String>,
    ): KbConcept? {
        return try {
            db.rawQuery(sql, args).use { c ->
                if (!c.moveToFirst()) null else readTerminologyConcept(c)
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ queryTerminologyRow failed : ${e.message}"
            )
            null
        }
    }

    private fun readTerminologyConcepts(c: android.database.Cursor): List<KbConcept> {
        if (!c.moveToFirst()) return emptyList()
        val out = ArrayList<KbConcept>(c.count)
        do {
            out.add(readTerminologyConcept(c))
        } while (c.moveToNext())
        return out
    }

    /**
     * Map a positioned cursor row to a [KbConcept].
     * Expects the 8-column terminology_codes projection.
     */
    private fun readTerminologyConcept(c: android.database.Cursor): KbConcept {
        return KbConcept(
            code = c.getStringOrNull(0) ?: "",
            atcCode = c.getStringOrNull(1),
            rxnormCui = c.getStringOrNull(2),
            snomedCode = c.getStringOrNull(3),
            ipsValidated = c.getInt(4) != 0,
            primaryDisplay = c.getStringOrNull(5) ?: "",
            system = c.getStringOrNull(6) ?: "",
            category = c.getStringOrNull(7),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // SQL helpers : ddinter_drugs mapping
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Internal struct for ddinter_drugs rows (4-column projection :
     * ddinter_id, name, primary_atc, atc_codes).
     */
    private data class DdinterRow(
        val ddinterId: String,
        val name: String,
        val primaryAtc: String?,
        val atcCodes: String?,   // CSV
    ) {
        fun toKbConcept(): KbConcept = KbConcept(
            code = primaryAtc ?: ddinterId,
            system = "http://www.whocc.no/atc",
            primaryDisplay = name,
            atcCode = primaryAtc,
            category = "Medication",
            ipsValidated = false,
            ddinterId = ddinterId,
            allAtcCodes = atcCodes
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
        )
    }

    private fun queryDdinterRow(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String>,
    ): DdinterRow? {
        return try {
            db.rawQuery(sql, args).use { c ->
                if (!c.moveToFirst()) null else DdinterRow(
                    ddinterId = c.getStringOrNull(0) ?: "",
                    name = c.getStringOrNull(1) ?: "",
                    primaryAtc = c.getStringOrNull(2),
                    atcCodes = c.getStringOrNull(3),
                )
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ queryDdinterRow failed : ${e.message}"
            )
            null
        }
    }

    // ─── 🆕 Lot 14.5c3 — Dose hints from terminology_codes.primary_display ────
    //
    // RxNorm (via terminology_codes) carries entries like :
    //   "Bilastine 20 MG Oral Tablet"
    //   "aspirin 500 MG Oral Tablet"
    //   "Warfarin Sodium 5 MG Oral Tablet"
    // We fetch all displays for a given ATC, regex-extract the dose tokens,
    // and return the distinct dose strings sorted by frequency. This lets the
    // Assistant pre-fill the dose field in the medication form even though
    // the user only scanned a brand-name box without typing the strength.
    //
    // The dose string is intentionally kept human-readable ("20 mg", "1000 mg")
    // so it shows nicely in the preview row. ExtractedMedication.toJMedication()
    // does the value/unit split for FHIR persistence.

    /**
     * Returns a list of distinct dose hints for the given ATC code, sorted
     * by frequency (most common first). Empty list if nothing parseable
     * was found. Limited to `maxHits` to bound the result.
     *
     * Example : fetchDoseHints("R06AX29") → ["20 mg"]
     *           fetchDoseHints("N02BE01") → ["1000 mg", "500 mg", "300 mg", ...]
     */
    suspend fun fetchDoseHints(
        atcCode: String?,
        maxHits: Int = 10,
    ): List<String> = withContext(Dispatchers.IO) {
        if (atcCode.isNullOrBlank()) return@withContext emptyList()
        val db = awaitDb() ?: return@withContext emptyList()
        val tStart = System.currentTimeMillis()

        // SQLite has no REGEXP by default; we filter wide with LIKE then
        // parse client-side. Performance is fine — typical ATC matches
        // 5-50 rows, all in one indexed lookup.
        val sql = """
            SELECT primary_display
            FROM terminology_codes
            WHERE atc_code = ? COLLATE NOCASE
              AND primary_display IS NOT NULL
              AND (primary_display LIKE '% mg%'
                OR primary_display LIKE '% MG%'
                OR primary_display LIKE '% ml%'
                OR primary_display LIKE '% ML%'
                OR primary_display LIKE '% mcg%'
                OR primary_display LIKE '% MCG%'
                OR primary_display LIKE '% UI%'
                OR primary_display LIKE '% IU%'
                OR primary_display LIKE '%µg%')
        """.trimIndent()

        val freq = mutableMapOf<String, Int>()
        try {
            db.rawQuery(sql, arrayOf(atcCode)).use { c ->
                while (c.moveToNext()) {
                    val display = c.getString(0) ?: continue
                    val dose = extractFirstDoseToken(display) ?: continue
                    freq[dose] = (freq[dose] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ fetchDoseHints('$atcCode') failed : ${e.message}"
            )
            return@withContext emptyList()
        }

        val sorted = freq.entries.sortedByDescending { it.value }.map { it.key }
        val result = sorted.take(maxHits)
        Log.d(
            TAG,
            "[t=${System.currentTimeMillis()}] 💊 fetchDoseHints('$atcCode') · " +
                "${result.size}/${freq.size} hits in ${System.currentTimeMillis() - tStart}ms · " +
                "first=${result.firstOrNull()}",
        )
        result
    }

    /** Regex applied to RxNorm-style displays to extract one dose token. */
    private val doseTokenRegex = Regex(
        """(\d+(?:[.,]\d+)?)\s*(mg|ml|mcg|µg|g|IU|UI)\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun extractFirstDoseToken(display: String): String? {
        val m = doseTokenRegex.find(display) ?: return null
        val value = m.groupValues[1].replace(',', '.')
        val unit = m.groupValues[2].lowercase()
            // Normalize variants : MCG → mcg, µg → mcg, UI → iu
            .let { if (it == "µg") "mcg" else if (it == "ui") "iu" else it }
        return "$value $unit"
    }

    /**
     * 🆕 Lot 14.5c20 — Batch fetch general interaction counts (food, disease) for ATC codes.
     */
    suspend fun getGeneralInteractionCounts(atcCodes: List<String>): Map<String, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val db = awaitDb() ?: return@withContext emptyMap()
        if (atcCodes.isEmpty()) return@withContext emptyMap()

        val results = mutableMapOf<String, Pair<Int, Int>>()
        val cleanCodes = atcCodes.map { it.trim().uppercase() }.distinct()
        val inClause = cleanCodes.joinToString { "'$it'" }

        // Food counts
        try {
            db.rawQuery("SELECT drug_atc, COUNT(*) FROM interactions_food WHERE drug_atc IN ($inClause) GROUP BY drug_atc", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val atc = cursor.getString(0) ?: continue
                    val count = cursor.getInt(1)
                    results[atc] = count to 0
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error counting food interactions", e) }

        // Disease counts
        try {
            db.rawQuery("SELECT drug_atc, COUNT(*) FROM drug_disease_interactions WHERE drug_atc IN ($inClause) GROUP BY drug_atc", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val atc = cursor.getString(0) ?: continue
                    val count = cursor.getInt(1)
                    val existing = results[atc] ?: (0 to 0)
                    results[atc] = existing.first to count
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error counting disease interactions", e) }

        results
    }
}

// ─── Cursor extensions ──────────────────────────────────────────────────────
//
// Robust nullable accessors that don't blow up when a column is absent.

private fun android.database.Cursor.getStringOrNull(idx: Int): String? =
    if (idx < 0 || isNull(idx)) null else getString(idx)
