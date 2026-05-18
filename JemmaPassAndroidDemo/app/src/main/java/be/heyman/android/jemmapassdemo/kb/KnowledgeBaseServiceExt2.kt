/*
 * KnowledgeBaseServiceExt2.kt — JEMMA Pass · v2.6.0m
 *
 * Extensions pour le KbConditionPicker (pré-chargement + filtrage langue) :
 *   - getTopByCategory(category, lang, maxResults) :
 *       Retourne un échantillon préfiltré de concepts pour une catégorie,
 *       priorisant ips_validated=1 et displays courts (lisibles).
 *       Utilisé pour pré-populer le picker à l'ouverture, sans attendre
 *       que l'utilisateur tape 2 caractères.
 *
 *   - filterByScriptForLang(concepts, lang) :
 *       Filtre client-side qui exclut les rows dont le `primary_display`
 *       contient des chars hors du script attendu pour la langue. Évite
 *       que les recherches FR/EN ramènent du chinois/arabe (les data
 *       UMLS sont multilingues — l'index FTS5 ne discrimine pas).
 *
 * Fichier séparé pour ne PAS toucher KnowledgeBaseService.kt (1197 lignes).
 *
 * Log channel : JEMMA-KB-TOP
 */
package be.heyman.android.jemmapassdemo.kb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG_TOP = "JEMMA-KB-TOP"

/**
 * Retourne jusqu'à [maxResults] concepts de la [category] donnée, sans
 * requête utilisateur. Utile pour pré-populer un picker à l'ouverture.
 *
 * Priorise :
 *  1. `ips_validated = 1` (concepts validés cliniquement)
 *  2. `primary_display` court (concepts plus génériques/utiles d'abord)
 *  3. Script latin si lang ∈ {fr, en, ...} — filtré client-side
 *
 * @param kbManager le KnowledgeBaseManager Hilt-injected
 * @param category 'Condition' | 'Medication' | 'Procedure' | etc.
 * @param lang BCP-47 langue user (utilisée pour filter script-side)
 * @param maxResults plafond (défaut 20)
 */
suspend fun KnowledgeBaseService.getTopByCategory(
    kbManager: KnowledgeBaseManager,
    category: String,
    lang: String,
    maxResults: Int = 20,
): List<KbConcept> = withContext(Dispatchers.IO) {
    val tStart = System.currentTimeMillis()
    val db = kbManager.database()
    if (db == null) {
        Log.w(TAG_TOP, "[t=${System.currentTimeMillis()}] ⚠️ DB not ready for getTopByCategory($category)")
        return@withContext emptyList()
    }

    // On surfetch 4× pour pouvoir filter script-side et conserver ~maxResults
    val sqlLimit = (maxResults * 4).coerceAtMost(200)

    val out = mutableListOf<KbConcept>()
    try {
        val cur = db.rawQuery(
            """
            SELECT code, atc_code, rxnorm_cui, snomed_code, ips_validated,
                   primary_display, system, category
            FROM terminology_codes
            WHERE category = ?
              AND primary_display IS NOT NULL
              AND length(primary_display) > 0
            ORDER BY ips_validated DESC, length(primary_display) ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(category, sqlLimit.toString()),
        )
        cur.use { c ->
            while (c.moveToNext()) {
                out.add(
                    KbConcept(
                        code = c.getString(0) ?: continue,
                        atcCode = c.getString(1),
                        rxnormCui = c.getString(2),
                        snomedCode = c.getString(3),
                        ipsValidated = c.getInt(4) == 1,
                        primaryDisplay = c.getString(5) ?: "",
                        system = c.getString(6) ?: "",
                        category = c.getString(7),
                    ),
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG_TOP, "[t=${System.currentTimeMillis()}] ⚠️ getTopByCategory($category) threw: ${e.message}")
        return@withContext emptyList()
    }

    // Filter script-side and trim to maxResults
    val filtered = filterByScriptForLang(out, lang).take(maxResults)
    Log.i(TAG_TOP, "[t=${System.currentTimeMillis()}] 🔝 top $category lang=$lang · " +
        "raw=${out.size} → kept=${filtered.size} · ${System.currentTimeMillis() - tStart}ms")
    filtered
}

/**
 * Filtre client-side qui exclut les concepts dont le `primary_display`
 * est dans un script incompatible avec la langue user.
 *
 * Règles :
 *  - lang ∈ {fr, en, es, it, pt, nl, de, …} → garder seulement les displays
 *    dont les chars sont majoritairement Latin-1 (ASCII + diacritiques courants).
 *  - lang = ja → garder hiragana / katakana / kanji + ASCII.
 *  - lang = zh → garder Han + ASCII.
 *  - lang = ko → garder Hangul + ASCII.
 *  - lang = ar → garder Arabic + ASCII.
 *  - Default (lang inconnue) → pas de filtre.
 *
 * Heuristique simple : on regarde si plus de 30% des chars sont dans un
 * script "incompatible" avec celui attendu. Si oui, on jette le concept.
 */
fun filterByScriptForLang(concepts: List<KbConcept>, lang: String): List<KbConcept> {
    val l = lang.lowercase().take(2)
    val expectedScript = when (l) {
        "ja", "jp", "zh", "ko" -> "cjk"
        "ar", "fa", "he", "ur" -> "rtl"
        "ru", "uk", "bg", "sr" -> "cyrillic"
        // Latin scripts : fr/en/es/it/pt/nl/de/sv/no/da/fi/pl/cs/...
        else -> "latin"
    }
    return concepts.filter { matchesScript(it.primaryDisplay, expectedScript) }
}

/**
 * Returns true if [s] is compatible with [script].
 *
 *  - "latin"   : ≤ 30% of chars are outside Latin-1 Supplement + Latin Extended-A
 *  - "cjk"     : ≥ 30% of chars are in CJK ranges (or pure ASCII OK)
 *  - "cyrillic": ≥ 30% in Cyrillic block (or pure ASCII)
 *  - "rtl"     : ≥ 30% in Arabic/Hebrew blocks (or pure ASCII)
 */
private fun matchesScript(s: String, script: String): Boolean {
    if (s.isEmpty()) return false
    var total = 0
    var inScript = 0
    var outOfScript = 0
    for (cp in s.codePoints().toArray()) {
        // Skip whitespace, digits and most punctuation
        if (cp <= 0x002F || cp in 0x003A..0x0040 || cp in 0x005B..0x0060 || cp in 0x007B..0x007E) continue
        if (cp in 0x0030..0x0039) continue  // digits 0-9
        total++
        val isLatin = cp in 0x0041..0x005A
            || cp in 0x0061..0x007A
            || cp in 0x00C0..0x024F   // Latin-1 sup + Latin Ext-A + Ext-B
        val isCjk = cp in 0x3040..0x30FF
            || cp in 0x4E00..0x9FFF
            || cp in 0xAC00..0xD7AF
            || cp in 0xFF00..0xFFEF
        val isCyrillic = cp in 0x0400..0x04FF
        val isArabicHebrew = cp in 0x0590..0x06FF || cp in 0x0750..0x077F
        when (script) {
            "latin" -> {
                if (isLatin) inScript++ else if (isCjk || isCyrillic || isArabicHebrew) outOfScript++
            }
            "cjk" -> {
                if (isCjk || isLatin) inScript++ else if (isCyrillic || isArabicHebrew) outOfScript++
            }
            "cyrillic" -> {
                if (isCyrillic || isLatin) inScript++ else if (isCjk || isArabicHebrew) outOfScript++
            }
            "rtl" -> {
                if (isArabicHebrew || isLatin) inScript++ else if (isCjk || isCyrillic) outOfScript++
            }
        }
    }
    if (total == 0) return false
    val outRatio = outOfScript.toFloat() / total.toFloat()
    return outRatio <= 0.30f
}

/**
 * Strip diacritiques d'une string. Utilisé pour normaliser une query
 * utilisateur avant FTS5, au cas où l'index ne strip pas les diacritiques
 * (cf. tokenize='unicode61 remove_diacritics 2' devrait gérer mais on
 * normalise pour être robuste).
 *
 * Ex : "mère" → "mere", "café" → "cafe", "São Paulo" → "Sao Paulo".
 */
fun stripDiacritics(s: String): String {
    // Normalize NFD then drop combining marks
    val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}
