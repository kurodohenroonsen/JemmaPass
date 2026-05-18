/*
 * DrugNameExtractor.kt — JEMMA Pass · JemmaAppDemo · v2.6.1
 *
 * Heuristic to pick out a drug name from raw multilingual OCR output
 * (Japanese recognizer returns kanji + katakana + hiragana + romanji
 * interleaved on the same blister). Strategy ordered by confidence :
 *
 *   1. Try each line as-is against `KnowledgeBaseService.resolveDrug`
 *      (the KB's FTS5_cjk index handles kanji/katakana ; the LIKE
 *      fallback handles romanji). Keep the first Exact/Prefix match.
 *
 *   2. Try the longest line that contains ≥ 3 katakana characters —
 *      this catches names like "オーグメンチン" which the recognizer
 *      isolates on its own row above the dose info.
 *
 *   3. Try each individual word (length ≥ 4) — useful when the brand
 *      name and the dose are concatenated like "Augmentin250mg".
 *
 *   4. Last resort : fuzzy `KnowledgeBaseService.searchCodes` with
 *      `categoryFilter = "Medication"` against the longest line.
 *
 *   5. Give up — return `NotFound` carrying the raw OCR text for the
 *      UI to either prompt the user or trigger 2.6.2's Gemma flow.
 *
 * Performance budget (cold KB cache, Pixel 9) :
 *   ~50 ms for the resolveDrug loop (5 lines × ~10 ms each)
 *   ~80 ms for the searchCodes fallback
 *   Well under the 200 ms "feels instant" threshold.
 *
 * NOTE — this lives in its own file because the heuristic will grow.
 * 2.6.2 plans : katakana→latin transliteration, brand→INN alias table
 * (Augmentin → amoxicillin/clavulanate), and a side channel asking
 * Gemma 4 multimodal to read the image directly when text-only fails.
 */
package be.heyman.android.jemmapassdemo.ai.medscan

import android.util.Log
import be.heyman.android.jemmapassdemo.kb.KbConcept
import be.heyman.android.jemmapassdemo.kb.KbSearchResult
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.ResolvedConcept

private const val TAG = "JEMMA-MEDSCAN-X"

/**
 * Result of a drug-name extraction attempt. The match tier lets the UI
 * decorate confidence (a "code" tier deserves a hard verdict ; a
 * "fuzzy-search" tier should suggest "did you mean … ?" first).
 */
sealed class ExtractionResult {

    /** A KB concept matched directly on a line of OCR text. */
    data class Resolved(
        val concept: KbConcept,
        val matchedLine: String,
        val matchedVia: String,           // "line:exact" | "line:prefix" | "line:contains" | "word" | "search-fts"
        val durationMs: Long,
    ) : ExtractionResult()

    /** No KB match — raw OCR text returned for manual disambiguation. */
    data class NotFound(
        val ocrText: String,
        val tried: Int,                   // number of attempts (lines + words + search)
        val durationMs: Long,
    ) : ExtractionResult()
}

/**
 * Pure async helper — no DI machinery, callers just pass the
 * [KnowledgeBaseService] they have on hand. Not a `@Singleton` because
 * it carries no state ; callers can construct one ad hoc if they prefer.
 */
class DrugNameExtractor(private val kb: KnowledgeBaseService) {

    /**
     * Extract a candidate drug from raw OCR text.
     *
     * @param ocrText the full text returned by ML Kit (lines joined by '\n')
     * @param lang BCP-47 lang code for the localized display in the result
     */
    suspend fun extract(ocrText: String, lang: String = "en"): ExtractionResult {
        val tStart = System.currentTimeMillis()
        var tried = 0
        if (ocrText.isBlank()) {
            return ExtractionResult.NotFound(
                ocrText = ocrText,
                tried = 0,
                durationMs = System.currentTimeMillis() - tStart,
            )
        }

        // ─── Step 1 — line-by-line resolveDrug ──────────────────────────────
        val lines = ocrText.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        for (line in lines) {
            tried++
            val resolved = kb.resolveDrug(line)
            if (resolved !is ResolvedConcept.NotFound) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ extract line='${line}' " +
                    "matched=${resolved.matchedOn} concept=${resolved.concept?.atcCode} · " +
                    "tried=$tried · took=${System.currentTimeMillis() - tStart}ms")
                return ExtractionResult.Resolved(
                    concept = resolved.concept!!,
                    matchedLine = line,
                    matchedVia = "line:${resolved.matchedOn}",
                    durationMs = System.currentTimeMillis() - tStart,
                )
            }
        }

        // ─── Step 2 — longest katakana-heavy line ───────────────────────────
        val katakanaCandidate = lines
            .filter { countKatakana(it) >= 3 }
            .maxByOrNull { it.length }
        if (katakanaCandidate != null) {
            tried++
            val resolved = kb.resolveDrug(katakanaCandidate)
            if (resolved !is ResolvedConcept.NotFound) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ extract katakana='${katakanaCandidate}' " +
                    "matched=${resolved.matchedOn} · tried=$tried · took=${System.currentTimeMillis() - tStart}ms")
                return ExtractionResult.Resolved(
                    concept = resolved.concept!!,
                    matchedLine = katakanaCandidate,
                    matchedVia = "katakana:${resolved.matchedOn}",
                    durationMs = System.currentTimeMillis() - tStart,
                )
            }
        }

        // ─── Step 3 — individual words (length ≥ 4) ─────────────────────────
        val words = lines
            .flatMap { line -> line.split(Regex("[\\s,;:()\\[\\]/.]+")) }
            .map { it.trim() }
            .filter { it.length >= 4 }
            .distinct()

        for (word in words.take(20)) {     // safety cap — never loop on 100 words
            tried++
            val resolved = kb.resolveDrug(word)
            if (resolved is ResolvedConcept.Exact || resolved is ResolvedConcept.Prefix) {
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ extract word='${word}' " +
                    "matched=${resolved.matchedOn} · tried=$tried · took=${System.currentTimeMillis() - tStart}ms")
                return ExtractionResult.Resolved(
                    concept = resolved.concept!!,
                    matchedLine = word,
                    matchedVia = "word:${resolved.matchedOn}",
                    durationMs = System.currentTimeMillis() - tStart,
                )
            }
        }

        // ─── Step 4 — FTS5 fuzzy search on the longest line ────────────────
        val longest = lines.maxByOrNull { it.length }
        if (!longest.isNullOrBlank()) {
            tried++
            val res = kb.searchCodes(
                query = longest,
                lang = lang,
                categoryFilter = "Medication",
                maxResults = 3,
            )
            if (res is KbSearchResult.Success && res.hits.isNotEmpty()) {
                val top = res.hits.first()
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ extract search='${longest}' " +
                    "matched=${top.matchedVia} concept=${top.concept.atcCode} · " +
                    "tried=$tried · took=${System.currentTimeMillis() - tStart}ms")
                return ExtractionResult.Resolved(
                    concept = top.concept,
                    matchedLine = longest,
                    matchedVia = "search-fts:${top.matchedVia}",
                    durationMs = System.currentTimeMillis() - tStart,
                )
            }
        }

        // ─── Step 5 — give up ───────────────────────────────────────────────
        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ no KB match for OCR='${ocrText.take(80)}' · " +
            "tried=$tried · took=${System.currentTimeMillis() - tStart}ms")
        return ExtractionResult.NotFound(
            ocrText = ocrText,
            tried = tried,
            durationMs = System.currentTimeMillis() - tStart,
        )
    }

    /** Count katakana characters (U+30A0…U+30FF) in a string. */
    private fun countKatakana(s: String): Int {
        var n = 0
        for (c in s) if (c.code in 0x30A0..0x30FF) n++
        return n
    }
}
