/*
 * IpsTranslationsRepository.kt — JEMMA Pass · Plan B · v2.6.0 · L5b
 *
 * Repository singleton qui charge UNE SEULE FOIS au boot le fichier
 * `assets/jemma/ips_translations.json` (328 codes SNOMED IPS avec
 * traductions FR/EN/JA, généré depuis ips_codes_i18n.js du HTML legacy).
 *
 * Format JSON :
 *   {
 *     "91936005": { "en": "Allergy to penicillin",
 *                   "fr": "Allergie à la pénicilline",
 *                   "ja": "ペニシリンアレルギー" },
 *     "102263004": { "en": "Eggs (edible)", "fr": "Oeufs", "ja": "卵" },
 *     ...
 *   }
 *
 * Couvre les 3 ValueSets FHIR IPS :
 *   - allergy-intolerance-snomed-ct-ips-free-set (292)
 *   - allergy-reaction-snomed-ct-ips-free-set (31)
 *   - absent-or-unknown-allergies-uv-ips (5 "no-known-X" keys)
 *
 * Total ~328 entries · ~50 KB on disk · negligeable RAM footprint.
 *
 * 🎯 PHILOSOPHIE — code-first :
 *   - Persistance utilise TOUJOURS le SNOMED code, jamais le label.
 *   - L'UI demande à ce repo de localiser au moment du rendu.
 *   - Quand la KB DIAMOND v1.2 sera enrichie avec ips_valuesets_translations
 *     peuplée, ce repo peut switcher backend sans changer l'API.
 *
 * Logging : tag JEMMA-IPS-TRANS
 */
package be.heyman.android.jemmapassdemo.pillars

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Une traduction tri-lingue d'un code IPS.
 */
data class IpsTranslation(
    val en: String,
    val fr: String,
    val ja: String,
) {
    /** Retourne le display pour la lang demandée, fallback sur EN si manque. */
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> fr.ifBlank { en }
        "ja" -> ja.ifBlank { en }
        else -> en
    }
}

/**
 * Singleton Hilt — chargé à la 1re injection, met les 328 entries en
 * mémoire (~50 KB JSON parsé, vraiment rien).
 *
 * API publique :
 *   - get(snomedCode, lang) → display localisé ou null si inconnu
 *   - search(prefix, lang) → liste pour autocomplete
 *   - all(lang) → liste complète triée pour pickers
 */
@Singleton
class IpsTranslationsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "JEMMA-IPS-TRANS"
        private const val ASSET_PATH = "jemma/ips_translations.json"

        /**
         * Clean up bilingual wireDisplay strings that contain a middle dot '·'.
         * Filters to keep either CJK (ja) or Western (fr, en) parts based on requested language.
         */
        fun cleanBilingual(wireDisplay: String, lang: String): String {
            if (!wireDisplay.contains("·")) return wireDisplay
            val parts = wireDisplay.split("·").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size != 2) return wireDisplay

            val isJa = lang.lowercase() in listOf("jp", "ja")
            val part1HasCjk = parts[0].any { it.code in 0x3000..0x9FFF || it.code in 0x3040..0x30FF || it.code in 0xFF00..0xFFEF }
            val part2HasCjk = parts[1].any { it.code in 0x3000..0x9FFF || it.code in 0x3040..0x30FF || it.code in 0xFF00..0xFFEF }

            return if (isJa) {
                if (part1HasCjk) parts[0] else if (part2HasCjk) parts[1] else parts[0]
            } else {
                if (!part1HasCjk) parts[0] else if (!part2HasCjk) parts[1] else parts[1]
            }
        }
    }

    private val loadMutex = Mutex()

    @Volatile
    private var cache: Map<String, IpsTranslation>? = null

    /**
     * Charge le JSON depuis assets (idempotent — premier appel parse,
     * suivants retournent la map cachée).
     *
     * 🆕 v2.6.0n FIX — Ajout de KotlinJsonAdapterFactory() pour que Moshi
     * sache désérialiser la `data class IpsTranslation` (sans cette
     * factory, Moshi fail silencieusement et retourne emptyMap, causant
     * le toast "Catalogue SNOMED indisponible").
     */
    private suspend fun ensureLoaded(): Map<String, IpsTranslation> {
        cache?.let { return it }
        return loadMutex.withLock {
            cache?.let { return@withLock it }   // double-check pattern
            withContext(Dispatchers.IO) {
                val tStart = System.currentTimeMillis()
                try {
                    // Diagnostic: check asset is bundled
                    val assetExists = try {
                        context.assets.list("jemma")?.contains("ips_translations.json") == true
                    } catch (e: Exception) {
                        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ assets.list('jemma') threw: ${e.message}")
                        false
                    }
                    Log.d(TAG, "[t=${System.currentTimeMillis()}] 🔍 asset $ASSET_PATH bundled=$assetExists")

                    val json = context.assets.open(ASSET_PATH)
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    Log.d(TAG, "[t=${System.currentTimeMillis()}] 📥 read ${json.length} chars from $ASSET_PATH")

                    // 🆕 v2.6.0n — Add KotlinJsonAdapterFactory : without it, Moshi
                    // cannot reflect on the IpsTranslation Kotlin data class and
                    // returns null/empty silently.
                    val moshi = Moshi.Builder()
                        .add(KotlinJsonAdapterFactory())
                        .build()
                    val type = Types.newParameterizedType(
                        Map::class.java,
                        String::class.java,
                        IpsTranslation::class.java,
                    )
                    val adapter: JsonAdapter<Map<String, IpsTranslation>> = moshi.adapter(type)
                    val parsed = adapter.fromJson(json) ?: emptyMap()
                    cache = parsed
                    val elapsed = System.currentTimeMillis() - tStart
                    if (parsed.isEmpty()) {
                        Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ parsed map is EMPTY despite ${json.length} chars read — Moshi adapter failed silently")
                    } else {
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📚 loaded ${parsed.size} IPS translations · ${elapsed}ms")
                        // Sample log to verify content
                        val sample = parsed.entries.take(3)
                        sample.forEach { (code, t) ->
                            Log.d(TAG, "[t=${System.currentTimeMillis()}] 📚 sample · $code · en='${t.en.take(40)}' fr='${t.fr.take(40)}'")
                        }
                    }
                    parsed
                } catch (e: Exception) {
                    Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ failed to load $ASSET_PATH : ${e.message}", e)
                    val empty = emptyMap<String, IpsTranslation>()
                    cache = empty
                    empty
                }
            }
        }
    }

    /**
     * Retourne le display localisé pour un code SNOMED IPS.
     * Retourne null si le code n'est pas dans le catalog.
     *
     * Exemple : get("91936005", "fr") → "Allergie à la pénicilline"
     */
    suspend fun get(snomedCode: String, lang: String): String? {
        if (snomedCode.isBlank()) return null
        val translations = ensureLoaded()
        return translations[snomedCode]?.pick(lang)
    }

    /**
     * Recherche par préfixe pour autocomplete d'un input substance.
     * Case-insensitive, accent-insensitive via NFD normalization.
     * Limite à 30 résultats (suffisant pour une dropdown).
     *
     * Si query vide, retourne emptyList (le caller doit appeler all()
     * explicitement pour avoir le catalog complet).
     */
    suspend fun search(query: String, lang: String, limit: Int = 30): List<Pair<String, IpsTranslation>> {
        if (query.isBlank()) return emptyList()
        val translations = ensureLoaded()
        val needle = normalizeForSearch(query)
        return translations.asSequence()
            .filter { (_, t) ->
                normalizeForSearch(t.pick(lang)).contains(needle) ||
                normalizeForSearch(t.en).contains(needle)
            }
            .sortedBy { (_, t) ->
                // Préfixe match d'abord, puis substring
                val display = normalizeForSearch(t.pick(lang))
                if (display.startsWith(needle)) 0 else 1
            }
            .take(limit)
            .map { (code, t) -> code to t }
            .toList()
    }

    /**
     * Catalog complet trié par display (lang demandé). Utile pour
     * un picker "tout afficher" (~328 items, scrollable).
     */
    suspend fun all(lang: String): List<Pair<String, IpsTranslation>> {
        val translations = ensureLoaded()
        return translations.entries
            .sortedBy { it.value.pick(lang).lowercase() }
            .map { it.key to it.value }
    }

    /**
     * Normalise pour recherche : minuscules, sans accents, sans
     * espaces multiples. NFD decomposition + strip combining marks.
     */
    private fun normalizeForSearch(s: String): String {
        val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        val stripped = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
        return stripped.lowercase().replace(Regex("\\s+"), " ").trim()
    }
}
