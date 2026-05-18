/*
 * JemmaDrugTextProcessor.kt — JEMMA Pass · Live Scan v0.1
 *
 * Extrait des candidats noms de médicaments depuis du texte OCR brut.
 *
 * L'OCR ML Kit produit des outputs bruités — les boîtes contiennent
 * dosages, batch numbers, dates de péremption, adresses fabricants,
 * etc. On a besoin de filtrer pour les rares tokens qui POURRAIENT
 * être un drug name avant de lancer une query FTS5.
 *
 * Heuristiques (ordre d'application) :
 *
 *   1. Tokens latin : séquences [a-zA-Z]{3,20} pas dans la stoplist
 *      (mg, ml, cp, tab, tablet, capsule, oral, etc.).
 *
 *   2. Tokens combinés latin : paires adjacentes (ex: "amoxicillin
 *      clavulanate" → "amoxicillin clavulanate" en plus de chacun
 *      séparément) pour matcher les combos comme Augmentin.
 *
 *   3. Tokens katakana : séquences katakana ≥ 3 chars. Au Japon les
 *      drug names sont majoritairement en katakana (アスピリン,
 *      ロキソニン, アモキシシリン). Hiragana et kanji exclus car ils
 *      servent surtout pour la grammaire / excipients.
 *
 * Limite : LIMIT candidats max par frame pour ne pas saturer FTS5
 * (8 queries × ~1ms = sub-frame, OK).
 *
 * Stateless → safe Singleton Hilt.
 *
 * Log channel : JEMMA-LIVESCAN-PROC
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-LIVESCAN-PROC"

@Singleton
class JemmaDrugTextProcessor @Inject constructor() {

    /**
     * Extrait jusqu'à [LIMIT] candidats noms depuis le texte OCR brut.
     *
     * @param rawText Texte brut retourné par ML Kit TextRecognizer.
     * @return Liste ordonnée des candidats (latin solos → pairs → katakana).
     *   Vide si pas de candidat valable.
     */
    fun extractCandidateNames(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        val tStart = System.currentTimeMillis()

        val out = LinkedHashSet<String>()

        // Pass 1 — Latin tokens solos
        val latinRegex = Regex("[A-Za-z]{3,20}")
        val latinTokens = latinRegex.findAll(rawText)
            .map { it.value }
            .filter { it.lowercase() !in STOPWORDS }
            .toList()
        out.addAll(latinTokens)

        // Pass 2 — Latin token pairs (combos type "amoxicillin clavulanate")
        for (i in 0 until latinTokens.size - 1) {
            val pair = "${latinTokens[i]} ${latinTokens[i + 1]}"
            if (pair.length in 7..30) out.add(pair)
        }

        // Pass 3 — Katakana CJK (アスピリン, ロキソニン, アモキシシリン...)
        val katakanaRegex = Regex("[\\u30A0-\\u30FF]{3,15}")
        val katakanaTokens = katakanaRegex.findAll(rawText).map { it.value }.toList()
        out.addAll(katakanaTokens)

        val result = out.toList().take(LIMIT)
        if (result.isNotEmpty()) {
            Log.d(
                TAG,
                "[t=$tStart] 🔤 extracted ${result.size} candidates from " +
                    "${rawText.length}c · sample=${result.take(3)}",
            )
        }
        return result
    }

    companion object {
        /** Limite candidats par frame OCR — évite saturation FTS5. */
        const val LIMIT = 8

        /**
         * Stoplist anti-bruit : unités, formes galéniques, mots de
         * packaging. Toujours lowercase pour la comparaison.
         */
        private val STOPWORDS = setOf(
            // Unités
            "mg", "ml", "cp", "tab", "tablet", "tabs", "capsule", "caps",
            "oral", "ophth", "topical", "rect", "gel", "creme", "cream",
            "comprime", "comprimes", "gelule", "gelules", "ampoule", "flacon",
            // Packaging FR/EN
            "boite", "boites", "blister", "blisters", "sachet", "sachets",
            "use", "for", "see", "lot", "batch", "exp", "expiry",
            "made", "manufactured", "store",
            // Storage instructions
            "below", "above", "celsius", "fahrenheit", "keep", "away",
            "from", "children", "reach", "warning", "caution",
            // Mots-outils EN
            "the", "and", "with", "etc", "info", "ref", "package",
            // Suffixes entreprise
            "ltd", "inc", "gmbh", "co", "rev", "version", "edition",
            // Positionnement boîte
            "front", "back", "side", "top", "bottom", "outer", "inner",
        )
    }
}
