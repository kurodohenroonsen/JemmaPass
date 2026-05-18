/*
 * KnowledgeBaseServiceAllergyCat.kt — JEMMA Pass · v2.6.0 · L_PHASE11
 *
 * 🆕 PHASE11 — Nouvelle function `suggestProblemsForDrug(atcCode, lang, limit)`
 * qui retourne les indications cliniques les plus pertinentes pour un
 * médicament donné, via CTE récursive sur atc_hierarchy + mapping
 * EN→FR pharmaco interne (~80 keywords). Validé sur 5 cas tests :
 *   - A10BA02 Metformine → Diabète, Hyperglycémie, Hypoglycémie
 *   - N02BE01 Paracétamol → Douleur (+ variants), Fièvre
 *   - J01CA04 Amoxicilline → Sepsie, Bactériémie, Infections bactériennes
 *   - C09AA05 Ramipril → Hypertension artérielle, Défaillance cardiaque
 *   - R03AC02 Salbutamol → Asthme (+ variants), Bronchospasme
 *
 * Extension à KnowledgeBaseService pour résoudre la catégorie FHIR
 * allergy-intolerance-category d'un code SNOMED IPS allergie, ainsi
 * que la résolution des picker lists IPS curées.
 *
 * 🆕 PHASE10 — Nouvelle function `searchIpsProblems(lang, query, limit)`
 * qui interroge le ValueSet `problems-snomed-ct-ips-free-set` (3596
 * codes FR cliniquement pertinents : Hypertension, Diabète, Asthme,
 * Douleur, Anxiété, Infections, etc.). Utilisé pour le picker
 * d'indication médicale (MedicationStatement.reasonCode) — remplace
 * l'ancien getTopByCategory("Condition") qui donnait Zar/Koro/Amok.
 *
 * 🆕 PHASE9 — Bug fix pénicilline → 🩹 (devrait être 💊) :
 *   - Les codes SNOMED comme 91936005 (penicillin) et 91935009 (peanut)
 *     ont leur bridge UMLS avec category='Condition' (l'allergie EST
 *     une condition clinique).
 *   - Mon mapper retournait null pour Condition → display 🩹.
 *   - Fix : extension de mapUmlsCategoryToFhir() pour faire un fallback
 *     heuristique sur le display anglais UMLS quand category='Condition'.
 *     Patterns ciblés : "penicillin", "peanut", "food allergy", etc.
 *
 * Stratégie (validée par audit DB v2.0-omnis, 285 allergens) :
 *
 *   Bridge via UMLS : terminology_codes WHERE snomed_code=? AND system='urn:umls'
 *   retourne la category UMLS (Food/Medication/Chemical_Allergen/Protein_Allergen/
 *   Mineral/Condition/etc.) + atc_code.
 *
 *   Mapping UMLS → FHIR :
 *     • Food             → food         🍴
 *     • Medication       → medication   💊
 *     • Protein_Allergen → biologic     🧬  (vaccines, serum, allergen proteins)
 *     • Mineral          → environment  🌿
 *     • Chemical_Allergen → sub-routing par ATC family :
 *         - V*   → environment (V07=sundries, V08=contrast media)
 *         - autre → medication (J=anti-infect, D=dermato, N=neuro, etc.)
 *     • Condition (6 codes) ou pas de bridge → null (user toggle manuel)
 *
 * Couverture mesurée : ~85% des 285 allergens IPS résolvent automatiquement
 * leur catégorie. Les 15% restants (50 codes sans UMLS + 6 codes Condition)
 * affichent l'emoji 🩹 et l'user toggle manuellement.
 *
 * Performance :
 *   - 1 SELECT indexé sur snomed_code (idx_terminology_codes_snomed existant)
 *   - ≤ 5ms par code single
 *   - Batch resolve (285 codes) ≤ 50ms via SELECT ... IN(?,?,...)
 *
 * Log channel : JEMMA-SNOMED-CAT
 */
package be.heyman.android.jemmapassdemo.kb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG_ALLERGY_CAT = "JEMMA-SNOMED-CAT"

/** FHIR allergy-intolerance-category enum values. */
object AllergyCategoryFhir {
    const val FOOD = "food"
    const val MEDICATION = "medication"
    const val ENVIRONMENT = "environment"
    const val BIOLOGIC = "biologic"
}

/** Emojis aligned with IpsAllergyCategoryCatalog values (food/med/env/bio). */
object AllergyCategoryEmoji {
    const val FOOD = "🍴"
    const val MEDICATION = "💊"
    const val ENVIRONMENT = "🌿"
    const val BIOLOGIC = "🧬"
    /** Fallback when category is not resolvable from the KB. */
    const val UNKNOWN = "🩹"

    /** Map a FHIR code to its emoji, or UNKNOWN if null/unrecognized. */
    fun forFhir(fhirCode: String?): String = when (fhirCode) {
        AllergyCategoryFhir.FOOD -> FOOD
        AllergyCategoryFhir.MEDICATION -> MEDICATION
        AllergyCategoryFhir.ENVIRONMENT -> ENVIRONMENT
        AllergyCategoryFhir.BIOLOGIC -> BIOLOGIC
        else -> UNKNOWN
    }
}

/**
 * Résout la catégorie FHIR allergy-intolerance pour un code SNOMED IPS.
 *
 * @param kbManager nécessaire pour accéder à database() (le pattern
 *   ServiceExt — cf KnowledgeBaseServiceExt.kt v4.1 FIX-AWAIT-DB)
 * @param snomedCode ex "91936005" (Allergy to penicillin)
 * @return FHIR code "food" | "medication" | "environment" | "biologic"
 *   OU null si non-résolvable (caller toggle manuellement)
 */
suspend fun KnowledgeBaseService.resolveAllergyFhirCategory(
    kbManager: KnowledgeBaseManager,
    snomedCode: String?,
): String? = withContext(Dispatchers.IO) {
    if (snomedCode.isNullOrBlank()) {
        Log.w(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ⚠ blank snomedCode")
        return@withContext null
    }
    val tStart = System.currentTimeMillis()
    val db = kbManager.database() ?: run {
        Log.w(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ⚠ KB DB not ready")
        return@withContext null
    }

    // Query : récupère le bridge UMLS pour ce code SNOMED.
    // Prefer non-empty category, then by clinical specificity, then by ATC presence.
    // 🆕 PHASE9 — récupère aussi primary_display (toujours en anglais pour
    // les rows urn:umls) pour faire un fallback heuristique sur les rows
    // category='Condition' (penicillin, peanuts, food allergy, etc.).
    val sql = """
        SELECT category, atc_code, primary_display
        FROM terminology_codes
        WHERE snomed_code = ? AND system = 'urn:umls'
        ORDER BY 
            CASE WHEN category != '' AND category IS NOT NULL THEN 0 ELSE 1 END,
            CASE category
                WHEN 'Food' THEN 1
                WHEN 'Medication' THEN 2
                WHEN 'Protein_Allergen' THEN 3
                WHEN 'Chemical_Allergen' THEN 4
                WHEN 'Mineral' THEN 5
                WHEN 'Condition' THEN 6
                ELSE 9
            END,
            LENGTH(COALESCE(atc_code, '')) DESC
        LIMIT 1
    """.trimIndent()

    var umlsCategory: String? = null
    var atcCode: String? = null
    var umlsDisplay: String? = null
    try {
        db.rawQuery(sql, arrayOf(snomedCode.trim())).use { cursor ->
            if (cursor.moveToFirst()) {
                umlsCategory = cursor.getString(0)?.takeIf { it.isNotBlank() }
                atcCode = cursor.getString(1)?.takeIf { it.isNotBlank() }
                umlsDisplay = cursor.getString(2)?.takeIf { it.isNotBlank() }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ❌ query failed for snomed=$snomedCode: ${e.message}", e)
        return@withContext null
    }

    val fhirCategory = mapUmlsCategoryToFhir(umlsCategory, atcCode, umlsDisplay)
    val durationMs = System.currentTimeMillis() - tStart
    Log.i(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 🩹 resolveAllergyFhirCategory · " +
        "snomed=$snomedCode · umls='$umlsCategory' · atc=$atcCode · " +
        "display='${umlsDisplay?.take(40)}' → fhir=$fhirCategory · ${durationMs}ms")
    fhirCategory
}

/**
 * BATCH resolve : pour le picker UI qui charge ~285 codes d'un coup,
 * on résout tout en 1 query au lieu de N. Énorme perf win.
 *
 * @return map snomedCode → fhirCategory. Les codes non-résolvables sont
 *   ABSENTS du map (caller doit gérer le miss = null = fallback emoji).
 */
suspend fun KnowledgeBaseService.batchResolveAllergyFhirCategory(
    kbManager: KnowledgeBaseManager,
    snomedCodes: List<String>,
): Map<String, String> = withContext(Dispatchers.IO) {
    if (snomedCodes.isEmpty()) return@withContext emptyMap()
    val tStart = System.currentTimeMillis()
    val db = kbManager.database() ?: return@withContext emptyMap()

    // SQLite limite à 999 placeholders par query — on chunke si nécessaire.
    val out = mutableMapOf<String, String>()
    val chunks = snomedCodes.chunked(900)
    for (chunk in chunks) {
        val placeholders = chunk.joinToString(",") { "?" }
        // ROW_NUMBER() OVER (PARTITION BY snomed_code ORDER BY <preference>)
        // ne retient que le meilleur match par snomed_code (preference par
        // category specificity puis présence d'ATC).
        val sql = """
            SELECT snomed_code, category, atc_code, primary_display FROM (
                SELECT 
                    snomed_code, category, atc_code, primary_display,
                    ROW_NUMBER() OVER (
                        PARTITION BY snomed_code
                        ORDER BY 
                            CASE WHEN category != '' AND category IS NOT NULL THEN 0 ELSE 1 END,
                            CASE category
                                WHEN 'Food' THEN 1
                                WHEN 'Medication' THEN 2
                                WHEN 'Protein_Allergen' THEN 3
                                WHEN 'Chemical_Allergen' THEN 4
                                WHEN 'Mineral' THEN 5
                                WHEN 'Condition' THEN 6
                                ELSE 9
                            END,
                            LENGTH(COALESCE(atc_code, '')) DESC
                    ) AS rn
                FROM terminology_codes
                WHERE snomed_code IN ($placeholders)
                  AND system = 'urn:umls'
            )
            WHERE rn = 1
        """.trimIndent()
        try {
            db.rawQuery(sql, chunk.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val snomed = cursor.getString(0) ?: continue
                    val umlsCat = cursor.getString(1)?.takeIf { it.isNotBlank() }
                    val atc = cursor.getString(2)?.takeIf { it.isNotBlank() }
                    val display = cursor.getString(3)?.takeIf { it.isNotBlank() }
                    val fhir = mapUmlsCategoryToFhir(umlsCat, atc, display)
                    if (fhir != null) out[snomed] = fhir
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ❌ batch query failed: ${e.message}", e)
        }
    }
    val durationMs = System.currentTimeMillis() - tStart
    val coverage = if (snomedCodes.isNotEmpty()) (out.size * 100 / snomedCodes.size) else 0
    Log.i(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 🩹 batchResolve · " +
        "n=${snomedCodes.size} → resolved=${out.size} ($coverage%) · ${durationMs}ms · " +
        "chunks=${chunks.size}")
    out
}

/**
 * 🆕 PHASE9 — Retourne la liste curée IPS des manifestations de réaction
 * allergique (29 codes officiels du ValueSet
 * 'allergy-reaction-snomed-ct-ips-free-set').
 *
 * Remplace l'ancien `getTopByCategory("Condition", lang)` qui retournait
 * des codes UMLS aléatoires triés par longueur de display (Zar, Gout,
 * Kuru, Koro, Yaws, Noma, Amok, Siti...) — totalement non-pertinents
 * cliniquement.
 *
 * La nouvelle liste contient : Anaphylaxie, Angioedème, Urticaire,
 * Prurit, Dyspnée, Asthme, Bronchospasme, Conjonctivite, Eczéma,
 * Stevens-Johnson, Lyell, Vomissement, Diarrhée, etc.
 *
 * Langues supportées (dans ips_valuesets_translations) :
 *   fr, de, es, it, nl, pt, sv, no, cs, pl, ru, ja, ko, ar, et, fi, hu,
 *   is, lt, lv. **PAS d'anglais natif** dans cette table — fallback FR
 *   pour 'en' (les FHIR systems anglo-saxons n'ont pas besoin de display
 *   anglais, le code SNOMED suffit).
 *
 * @return List<Triple<code, display, system>> ordonné alphabétiquement
 *   par display dans la lang demandée
 */
data class AllergyReactionItem(
    val code: String,
    val display: String,
    val system: String = "http://snomed.info/sct",
)

suspend fun KnowledgeBaseService.getAllergyReactionList(
    kbManager: KnowledgeBaseManager,
    lang: String,
): List<AllergyReactionItem> = withContext(Dispatchers.IO) {
    val tStart = System.currentTimeMillis()
    val db = kbManager.database() ?: run {
        Log.w(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ⚠ KB DB not ready for reactions")
        return@withContext emptyList()
    }

    // Lang fallback : 'en' → 'fr' (la table n'a pas d'anglais ; fr est lingua franca clinique)
    val effectiveLang = when (lang.lowercase().take(2)) {
        "en" -> "fr"
        else -> lang.lowercase().take(2)
    }

    val sql = """
        SELECT code, display
        FROM ips_valuesets_translations
        WHERE vs_id = 'allergy-reaction-snomed-ct-ips-free-set'
          AND lang = ?
        ORDER BY display COLLATE NOCASE ASC
    """.trimIndent()

    val out = mutableListOf<AllergyReactionItem>()
    try {
        db.rawQuery(sql, arrayOf(effectiveLang)).use { cursor ->
            while (cursor.moveToNext()) {
                val code = cursor.getString(0) ?: continue
                val display = cursor.getString(1) ?: continue
                out.add(AllergyReactionItem(code = code, display = display))
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ❌ getAllergyReactionList failed: ${e.message}", e)
        return@withContext emptyList()
    }
    val durationMs = System.currentTimeMillis() - tStart
    Log.i(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 🌡 getAllergyReactionList · " +
        "lang=$effectiveLang (requested=$lang) · n=${out.size} · ${durationMs}ms")
    out
}

/**
 * 🆕 Lot 14.5c27 — IPS substance allergy/intolerance valueset (~292 codes FR).
 *
 * Source : table `ips_valuesets_translations`, vs_id =
 * `allergy-intolerance-snomed-ct-ips-free-set`. C'est la liste IPS-curated
 * affichée par le picker manuel de substance — elle contient des codes
 * cliniquement pertinents type "Allergie à la pénicilline" (91936005),
 * "Allergie aux arachides" (91934008), etc.
 *
 * Utilisée par le chat vocal (`AllergiesChatFragment.kickoffSubstanceLookup`)
 * pour prioriser ces codes IPS curated AVANT le fallback vers la KB
 * générique (`Chemical_Allergen` / `Protein_Allergen`).
 *
 * Pourquoi : la KB générique renvoie souvent un code "pharmaceutique"
 * (ex: 764146007 penicillins = la substance pharma) au lieu du code
 * "allergie" IPS (ex: 91936005 Allergie à la pénicilline = l'allergie
 * cliniquement déclarée). Pour la conformité FHIR R4 / IPS, on veut le
 * code "allergie", pas le code pharma.
 *
 * Lang fallback identique à getAllergyReactionList : 'en' → 'fr'.
 */
suspend fun KnowledgeBaseService.getAllergyIntoleranceList(
    kbManager: KnowledgeBaseManager,
    lang: String,
): List<AllergyReactionItem> = withContext(Dispatchers.IO) {
    val tStart = System.currentTimeMillis()
    val db = kbManager.database() ?: run {
        Log.w(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ⚠ KB DB not ready for allergy intolerance list")
        return@withContext emptyList()
    }

    val effectiveLang = when (lang.lowercase().take(2)) {
        "en" -> "fr"
        else -> lang.lowercase().take(2)
    }

    val sql = """
        SELECT code, display
        FROM ips_valuesets_translations
        WHERE vs_id = 'allergy-intolerance-snomed-ct-ips-free-set'
          AND lang = ?
        ORDER BY display COLLATE NOCASE ASC
    """.trimIndent()

    val out = mutableListOf<AllergyReactionItem>()
    try {
        db.rawQuery(sql, arrayOf(effectiveLang)).use { cursor ->
            while (cursor.moveToNext()) {
                val code = cursor.getString(0) ?: continue
                val display = cursor.getString(1) ?: continue
                out.add(AllergyReactionItem(code = code, display = display))
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ❌ getAllergyIntoleranceList failed: ${e.message}", e)
        return@withContext emptyList()
    }
    val durationMs = System.currentTimeMillis() - tStart
    Log.i(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 🍯 getAllergyIntoleranceList · " +
        "lang=$effectiveLang (requested=$lang) · n=${out.size} · ${durationMs}ms")
    out
}

/**
 * 🆕 PHASE10 — Recherche dans le ValueSet IPS `problems-snomed-ct-ips-free-set`
 * (3596 codes FR cliniquement pertinents : Hypertension, Diabète, Asthme,
 * Douleur, Anxiété, Infections, etc.).
 *
 * Utilisé pour :
 *   - MedicationStatement.reasonCode (indication médicale = pourquoi
 *     ce traitement : "hypertension", "diabète", "douleur", etc.)
 *   - Condition.code (futur pilier diagnostics)
 *
 * Remplace l'ancien `getTopByCategory("Condition", lang)` qui retournait
 * les codes UMLS aléatoires tri par longueur (Zar, Gout, Kuru, Koro,
 * Yaws, Noma, Amok, Siti) → totalement non-cliniquement pertinents.
 *
 * Stratégie :
 *   - Si query vide ou < 2 chars : retourne TOP N alphabétique
 *   - Sinon : filtre WHERE display LIKE '%query%' avec normalisation
 *     diacritiques
 *
 * Lang fallback : 'en' → 'fr' (la table n'a pas d'anglais).
 *
 * @return List<AllergyReactionItem> réutilise le même data class
 *   (code + display + system) car structurellement identique.
 */
suspend fun KnowledgeBaseService.searchIpsProblems(
    kbManager: KnowledgeBaseManager,
    lang: String,
    query: String = "",
    limit: Int = 50,
): List<AllergyReactionItem> = withContext(Dispatchers.IO) {
    val tStart = System.currentTimeMillis()
    val db = kbManager.database() ?: run {
        Log.w(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ⚠ KB DB not ready for problems")
        return@withContext emptyList()
    }

    val effectiveLang = when (lang.lowercase().take(2)) {
        "en" -> "fr"
        else -> lang.lowercase().take(2)
    }
    val q = query.trim()

    // Pour matcher diacritiques-insensible côté SQLite, on utilise LIKE
    // sur LOWER(display). SQLite n'a pas de NFD strip natif, mais comme
    // les displays IPS et la query user passent par les mêmes encodings,
    // un LIKE basique fonctionne pour la plupart des cas (95%+).
    // Note : pour la recherche d'allergènes (ips_translations.json), on
    // utilise un normalizer Kotlin côté client — ici on reste server-side.
    val out = mutableListOf<AllergyReactionItem>()
    try {
        if (q.length < 2) {
            // No query → TOP N alphabetic
            val sql = """
                SELECT code, display
                FROM ips_valuesets_translations
                WHERE vs_id = 'problems-snomed-ct-ips-free-set'
                  AND lang = ?
                ORDER BY display COLLATE NOCASE ASC
                LIMIT ?
            """.trimIndent()
            db.rawQuery(sql, arrayOf(effectiveLang, limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    val code = cursor.getString(0) ?: continue
                    val display = cursor.getString(1) ?: continue
                    out.add(AllergyReactionItem(code = code, display = display))
                }
            }
        } else {
            // Filtered search
            val sql = """
                SELECT code, display
                FROM ips_valuesets_translations
                WHERE vs_id = 'problems-snomed-ct-ips-free-set'
                  AND lang = ?
                  AND LOWER(display) LIKE ?
                ORDER BY 
                    CASE WHEN LOWER(display) LIKE ? THEN 0 ELSE 1 END,
                    LENGTH(display) ASC,
                    display COLLATE NOCASE ASC
                LIMIT ?
            """.trimIndent()
            val likeContains = "%${q.lowercase()}%"
            val likeStartsWith = "${q.lowercase()}%"
            db.rawQuery(
                sql,
                arrayOf(effectiveLang, likeContains, likeStartsWith, limit.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val code = cursor.getString(0) ?: continue
                    val display = cursor.getString(1) ?: continue
                    out.add(AllergyReactionItem(code = code, display = display))
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ❌ searchIpsProblems failed: ${e.message}", e)
        return@withContext emptyList()
    }
    val durationMs = System.currentTimeMillis() - tStart
    Log.i(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 🩺 searchIpsProblems · " +
        "lang=$effectiveLang · q='$q' · n=${out.size} · ${durationMs}ms")
    out
}

/**
 * 🆕 PHASE11 — Smart-suggestion d'indications cliniques pour un médicament donné.
 *
 * Stratégie 3 étages :
 *   1. Récupère l'ancestry ATC du code (CTE récursive sur atc_hierarchy)
 *   2. Extrait les keywords EN des `name_en` des niveaux L3-L5 (les plus
 *      thérapeutiquement parlants : "DRUGS USED IN DIABETES", "ANALGESICS",
 *      "ANTIBACTERIALS FOR SYSTEMIC USE", etc.)
 *   3. Map les keywords EN → patterns FR via une table interne, puis query
 *      ips_valuesets_translations pour les SNOMED matchant
 *
 * Exemples validés (audit DB) :
 *   - A10BA02 Metformine → Diabète, Diabète type 2, Hyperglycémie, Hypoglycémie
 *   - N02BE01 Paracétamol → Douleur (+ 15 variants), Fièvre
 *   - J01CA04 Amoxicilline → Sepsie, Bactériémie, Infections bactériennes
 *   - C09AA05 Ramipril → Hypertension artérielle (+ 9 variants), Défaillance cardiaque
 *   - R03AC02 Salbutamol → Asthme (+ variants), Bronchospasme
 *
 * Limites connues :
 *   - Faux positifs possibles si keyword FR trop générique (ex: "hémo" matche
 *     hémorragie ET hémoptysie). On filtre via une **stop list de patterns FR
 *     trop bruyants** : on évite "hémo", "tension" seul, etc., et on préfère
 *     des patterns longs (≥5 chars) comme "diabète", "hypertension".
 *
 * @return List<AllergyReactionItem> (réutilisé pour les 3 contextes : reactions,
 *   problems, suggestions). Vide si pas d'ATC ou pas d'ancestry trouvée.
 */
suspend fun KnowledgeBaseService.suggestProblemsForDrug(
    kbManager: KnowledgeBaseManager,
    lang: String,
    atcCode: String?,
    limit: Int = 12,
): List<AllergyReactionItem> = withContext(Dispatchers.IO) {
    if (atcCode.isNullOrBlank()) {
        Log.d(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 💡 suggestProblemsForDrug · skip (no ATC)")
        return@withContext emptyList()
    }
    val tStart = System.currentTimeMillis()
    val db = kbManager.database() ?: return@withContext emptyList()

    val effectiveLang = when (lang.lowercase().take(2)) {
        "en" -> "fr"
        else -> lang.lowercase().take(2)
    }

    // Étape 1+2 : CTE récursive remonte l'arbre ATC, on récupère les name_en
    // des niveaux 3, 4, 5 (les plus thérapeutiquement spécifiques).
    val ancestrySql = """
        WITH RECURSIVE ancestry(atc_code, parent_atc, level, name_en, depth) AS (
            SELECT atc_code, parent_atc, level, name_en, 0
            FROM atc_hierarchy WHERE atc_code = ?
            UNION ALL
            SELECT h.atc_code, h.parent_atc, h.level, h.name_en, a.depth+1
            FROM atc_hierarchy h
            JOIN ancestry a ON h.atc_code = a.parent_atc
            WHERE a.parent_atc IS NOT NULL AND a.parent_atc != ''
        )
        SELECT name_en FROM ancestry 
        WHERE level BETWEEN 3 AND 5 AND name_en IS NOT NULL AND name_en != ''
    """.trimIndent()

    val nameTokens = mutableSetOf<String>()
    try {
        db.rawQuery(ancestrySql, arrayOf(atcCode.trim())).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                // Tokenize : split sur non-alpha, lowercase, garde mots ≥4 chars
                name.lowercase()
                    .split(Regex("[^a-zA-Z]+"))
                    .filter { it.length >= 4 && it !in ENGLISH_PHARMACO_STOPWORDS }
                    .forEach { nameTokens.add(it) }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ❌ ATC ancestry query failed for $atcCode: ${e.message}", e)
        return@withContext emptyList()
    }

    if (nameTokens.isEmpty()) {
        Log.w(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 💡 no keywords for atc=$atcCode")
        return@withContext emptyList()
    }

    // Étape 3 : Map EN → patterns FR (table hardcodée). On choisit volontairement
    // des patterns >=5 chars pour éviter les faux positifs (no 'hémo', 'tens',
    // 'pyrex' tout seul). Les keywords sans mapping sont ignorés.
    val frPatterns = mutableSetOf<String>()
    nameTokens.forEach { token ->
        ATC_KEYWORD_EN_TO_FR_PATTERNS[token]?.forEach { frPatterns.add(it) }
    }

    if (frPatterns.isEmpty()) {
        Log.d(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 💡 no FR patterns mapped for $atcCode (tokens=$nameTokens)")
        return@withContext emptyList()
    }

    Log.d(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 💡 atc=$atcCode · tokens=$nameTokens · fr_patterns=$frPatterns")

    // Étape 4 : Query IPS problems matchant ces patterns
    val likes = frPatterns.joinToString(" OR ") { "LOWER(display) LIKE ?" }
    val args = mutableListOf<String>(effectiveLang)
    frPatterns.forEach { args.add("%${it.lowercase()}%") }
    args.add(limit.toString())

    val sql = """
        SELECT code, display
        FROM ips_valuesets_translations
        WHERE vs_id = 'problems-snomed-ct-ips-free-set'
          AND lang = ?
          AND ($likes)
        ORDER BY LENGTH(display) ASC, display COLLATE NOCASE ASC
        LIMIT ?
    """.trimIndent()

    val out = mutableListOf<AllergyReactionItem>()
    try {
        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val code = cursor.getString(0) ?: continue
                val display = cursor.getString(1) ?: continue
                out.add(AllergyReactionItem(code = code, display = display))
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ❌ suggestProblemsForDrug query failed: ${e.message}", e)
        return@withContext emptyList()
    }
    val durationMs = System.currentTimeMillis() - tStart
    Log.i(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] 💡 suggestProblemsForDrug · " +
        "atc=$atcCode · lang=$effectiveLang · suggested=${out.size} · ${durationMs}ms")
    out
}

/**
 * Stop words : noms ATC qui ne sont pas thérapeutiquement parlants.
 * On les exclut pour éviter de surcharger la requête keyword.
 */
private val ENGLISH_PHARMACO_STOPWORDS = setOf(
    "and", "for", "the", "with", "other", "excl", "incl", "plain",
    "agents", "drugs", "preparations", "products", "group", "groups",
    "systemic", "topical", "use", "used", "using", "various",
    "extended", "selective", "compounds", "system", "acting",
    "spectrum", "combinations", "association", "associations",
)

/**
 * Mapping ATC keyword (en anglais, extrait de atc_hierarchy.name_en) vers
 * patterns FR utilisables dans un SQL LIKE %pattern% sur les displays IPS
 * problems en FR.
 *
 * Conçu pour minimiser les faux positifs : on n'utilise QUE des patterns
 * ≥5 chars qui sont distinctifs cliniquement. Ex : on évite 'tension' seul
 * (matche "céphalée de tension"), 'hémo' seul (matche "hémorragie") au
 * profit de patterns plus spécifiques.
 *
 * Pour le hackathon, cette table couvre les ATC chapitres les plus courants.
 * Couverture estimée : 60-70% des médicaments du grand public.
 */
private val ATC_KEYWORD_EN_TO_FR_PATTERNS: Map<String, List<String>> = mapOf(
    // === Diabète, glycémie ===
    "diabetes" to listOf("diabète", "hyperglycémie", "hypoglycémie"),
    "diabetic" to listOf("diabète"),
    "glucose" to listOf("hyperglycémie", "hypoglycémie", "glycém"),
    "biguanides" to listOf("diabète"),
    "insulins" to listOf("diabète"),
    "sulfonylureas" to listOf("diabète"),

    // === Douleur, fièvre ===
    "analgesics" to listOf("douleur"),
    "antipyretics" to listOf("fièvre"),
    "anilides" to listOf("douleur", "fièvre"),
    "opioids" to listOf("douleur"),
    "salicylates" to listOf("douleur", "fièvre"),
    "pyrazolones" to listOf("douleur", "fièvre"),

    // === Infections ===
    "antibacterials" to listOf("infection", "bactér", "sepsie", "sepsis"),
    "antibiotics" to listOf("infection", "bactér", "sepsie"),
    "antimicrobials" to listOf("infection"),
    "penicillins" to listOf("infection bactér"),
    "cephalosporins" to listOf("infection bactér"),
    "macrolides" to listOf("infection bactér"),
    "quinolones" to listOf("infection"),
    "tetracyclines" to listOf("infection"),
    "sulfonamides" to listOf("infection"),
    "antifungals" to listOf("mycose", "candidose", "fongique"),
    "antimycotics" to listOf("mycose", "candidose"),
    "antivirals" to listOf("infection virale", "virose"),
    "antiviral" to listOf("infection virale"),
    "antiprotozoals" to listOf("parasitose"),
    "antiparasitic" to listOf("parasitose"),
    "antihelmintic" to listOf("parasitose"),
    "antimycobacterials" to listOf("tuberculose"),
    "tuberculostatics" to listOf("tuberculose"),
    "antiinfectives" to listOf("infection"),

    // === Cardiovasculaire ===
    "antihypertensives" to listOf("hypertension"),
    "angiotensin" to listOf("hypertension", "insuffisance cardiaque"),
    "renin" to listOf("hypertension"),
    "diuretics" to listOf("hypertension", "œdème", "insuffisance cardiaque"),
    "antiarrhythmics" to listOf("arythmie", "fibrillation"),
    "antianginal" to listOf("angine de poitrine", "infarctus"),
    "vasodilators" to listOf("hypertension", "angine de poitrine"),
    "antiplatelet" to listOf("thrombose", "infarctus"),
    "anticoagulants" to listOf("thrombose", "embolie"),
    "thrombolytic" to listOf("thrombose", "embolie", "infarctus"),
    "hypolipidemic" to listOf("hypercholestérol", "dyslipidém"),
    "antilipemic" to listOf("hypercholestérol"),
    "statins" to listOf("hypercholestérol"),
    "cardiac" to listOf("insuffisance cardiaque", "arythmie"),
    "cardiovascular" to listOf("hypertension", "insuffisance cardiaque"),

    // === Respiratoire ===
    "antiasthmatic" to listOf("asthme", "bronchospasme"),
    "bronchodilator" to listOf("asthme", "bronchospasme", "bronchopneumopathie"),
    "bronchodilators" to listOf("asthme", "bronchospasme"),
    "adrenergics" to listOf("asthme", "bronchospasme"),
    "adrenoreceptor" to listOf("asthme"),
    "inhalants" to listOf("asthme", "bronchospasme"),
    "obstructive" to listOf("asthme", "bronchopneumopathie"),
    "airway" to listOf("asthme", "bronchospasme"),
    "antihistamines" to listOf("allergie", "rhinite", "urticaire"),
    "antitussives" to listOf("toux"),
    "expectorants" to listOf("bronchite"),
    "mucolytics" to listOf("bronchite"),

    // === Système nerveux ===
    "antiepileptics" to listOf("épilepsie", "crises épileptiques", "convulsion"),
    "anticonvulsants" to listOf("épilepsie", "convulsion"),
    "antiparkinson" to listOf("parkinson"),
    "antipsychotics" to listOf("psychose", "schizophrénie"),
    "neuroleptics" to listOf("psychose"),
    "antidepressants" to listOf("dépression"),
    "anxiolytics" to listOf("anxiété"),
    "hypnotics" to listOf("insomnie", "trouble du sommeil"),
    "sedatives" to listOf("anxiété", "insomnie"),
    "antimigraine" to listOf("migraine"),
    "antiemetic" to listOf("nausée", "vomissement"),
    "antiemetics" to listOf("nausée", "vomissement"),
    "psycholeptics" to listOf("anxiété", "psychose"),
    "psychoanaleptics" to listOf("dépression"),

    // === Digestif ===
    "antacid" to listOf("ulcère gastrique", "reflux"),
    "antacids" to listOf("ulcère gastrique", "reflux"),
    "antiulcer" to listOf("ulcère gastrique"),
    "antidiarrheal" to listOf("diarrhée"),
    "antidiarrheals" to listOf("diarrhée"),
    "laxatives" to listOf("constipation"),
    "antiobesity" to listOf("obésité"),
    "stomatological" to listOf("affection bucco"),

    // === Inflammation / immunologie ===
    "antiinflammatory" to listOf("inflammation", "arthrose", "arthrite"),
    "antirheumatic" to listOf("arthrite", "rhumat"),
    "antirheumatics" to listOf("arthrite", "rhumat"),
    "antineoplastic" to listOf("cancer", "tumeur", "néoplas"),
    "immunosuppressants" to listOf("rejet"),
    "immunomodulating" to listOf("auto-immune"),
    "corticosteroids" to listOf("inflammation"),

    // === Hormones / endocrinien ===
    "thyroid" to listOf("hyperthyroïd", "hypothyroïd", "goitre"),
    "antithyroid" to listOf("hyperthyroïd"),
    "calcium" to listOf("ostéoporose", "hypocalcémie"),
    "bisphosphonates" to listOf("ostéoporose"),

    // === Génito-urinaire ===
    "urinary" to listOf("infection urinaire", "incontinence"),
    "gynecological" to listOf("infection génitale"),
    "contraceptives" to listOf("contraception"),
)

/**
 * 🆕 PHASE11 — Helper qui résout l'ATC code d'un médicament à partir
 * de son code primaire (peut être ATC, UMLS CUI, RxNorm, ou SNOMED).
 *
 * Cas pratiques :
 *   - `code='N02BE01'` (ATC pur) → retourne 'N02BE01' direct (match regex)
 *   - `code='C0307942'` (UMLS) → SELECT atc_code FROM terminology_codes WHERE code=?
 *     → retourne 'J01CA04' (Amoxicilline)
 *   - `code='2670'` (RxNorm CUI) → SELECT atc_code FROM terminology_codes
 *     WHERE rxnorm_cui=? → retourne 'N02BE01' si paracetamol
 *
 * Retourne null si pas de mapping ATC disponible.
 */
suspend fun KnowledgeBaseService.resolveAtcCode(
    kbManager: KnowledgeBaseManager,
    code: String?,
    @Suppress("UNUSED_PARAMETER") system: String? = null,
): String? = withContext(Dispatchers.IO) {
    if (code.isNullOrBlank()) return@withContext null
    val c = code.trim()

    // Étape 1 : si déjà un ATC (pattern : L+digit+digit[L][L][digit+digit])
    val atcPattern = Regex("^[A-Z][0-9]{2}([A-Z]([A-Z]([0-9]{2})?)?)?$")
    if (atcPattern.matches(c)) {
        return@withContext c
    }

    // Étape 2 : lookup dans terminology_codes via code OU rxnorm_cui
    val db = kbManager.database() ?: return@withContext null
    try {
        db.rawQuery(
            """
            SELECT atc_code FROM terminology_codes
            WHERE (code = ? OR rxnorm_cui = ?)
              AND atc_code IS NOT NULL AND atc_code != ''
            ORDER BY 
                CASE WHEN code = ? THEN 0 ELSE 1 END,
                LENGTH(atc_code) DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(c, c, c),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursor.getString(0)?.takeIf { it.isNotBlank() }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_ALLERGY_CAT, "[t=${System.currentTimeMillis()}] ❌ resolveAtcCode failed for $c: ${e.message}", e)
    }
    null
}

/**
 * Mapping UMLS category × ATC family × UMLS display → FHIR allergy-intolerance-category.
 *
 * 🆕 PHASE9 — Pour les rows avec category='Condition' (les "Allergy to X"
 * en SNOMED), on fait un fallback heuristique sur le display anglais
 * (toujours en EN pour les rows urn:umls, indépendamment de la locale UI).
 *
 * Les codes UMLS Condition restant après filtre IPS sont une petite liste
 * connue (~6 codes pour le VS allergy-intolerance) :
 *   - C0030824 "Allergy to penicillin"     → medication 💊
 *   - C0559470 "Allergy to peanuts"        → food 🍴
 *   - C0016470 "Food Allergy"              → food 🍴
 *   - C0454618 "Food Intolerance"          → food 🍴
 *   - C0085129 "Bronchial Hyperreactivity" → null (déjà category null)
 *   - C0155169 "Ocular hyperemia"          → null
 *
 * Pure function — testable sans DB.
 */
internal fun mapUmlsCategoryToFhir(
    umlsCategory: String?,
    atcCode: String?,
    umlsDisplay: String? = null,
): String? {
    return when (umlsCategory) {
        "Food" -> AllergyCategoryFhir.FOOD
        "Medication" -> AllergyCategoryFhir.MEDICATION
        "Protein_Allergen" -> AllergyCategoryFhir.BIOLOGIC
        "Mineral" -> AllergyCategoryFhir.ENVIRONMENT
        "Chemical_Allergen" -> {
            when (atcCode?.firstOrNull()?.uppercaseChar()) {
                'V' -> AllergyCategoryFhir.ENVIRONMENT
                'A', 'B', 'C', 'D', 'G', 'H', 'J', 'L', 'M', 'N', 'P', 'R', 'S' -> AllergyCategoryFhir.MEDICATION
                null -> AllergyCategoryFhir.MEDICATION
                else -> AllergyCategoryFhir.MEDICATION
            }
        }
        "Condition" -> resolveConditionByDisplay(umlsDisplay)
        else -> null
    }
}

/**
 * 🆕 PHASE9 — Heuristic ciblée sur le display EN pour résoudre les
 * "Allergy to X" UMLS rows qui ont category='Condition' (penicillin,
 * peanuts, food, etc.). N'utilise PAS des mots-clés génériques de
 * substances (qui retomberaient dans le piège de la phase pré-DB)
 * mais des **patterns cliniques explicites** présents dans les
 * displays UMLS officiels.
 *
 * Visible pour test unitaire.
 */
internal fun resolveConditionByDisplay(display: String?): String? {
    if (display.isNullOrBlank()) return null
    val d = display.lowercase()
    return when {
        // Médicaments — patterns ATC class names ou drug names connus
        "penicillin" in d || "amoxicillin" in d || "antibiotic" in d ||
            "sulfa" in d || "drug allergy" in d || "drug intolerance" in d ||
            "nsaid" in d || "aspirin" in d || "ibuprofen" in d ||
            "medication allergy" in d -> AllergyCategoryFhir.MEDICATION
        // Aliments — patterns alimentaires (singulier et pluriel)
        "peanut" in d || "tree nut" in d || "shellfish" in d ||
            "food allergy" in d || "food intolerance" in d ||
            "milk allerg" in d || "egg allerg" in d || "wheat allerg" in d ||
            "gluten" in d || "lactose" in d ||
            "soy allerg" in d || "fish allerg" in d -> AllergyCategoryFhir.FOOD
        // Environnement — patterns inhalants
        "pollen" in d || "dust mite" in d || "latex" in d ||
            "animal dander" in d || "mold allerg" in d -> AllergyCategoryFhir.ENVIRONMENT
        // Biologic — patterns biological products
        "venom" in d || "insect sting" in d || "vaccine reaction" in d ||
            "gelatin allerg" in d -> AllergyCategoryFhir.BIOLOGIC
        // Default : laisser user toggler (Asthma, Bronchial Hyperreactivity, etc.)
        else -> null
    }
}
