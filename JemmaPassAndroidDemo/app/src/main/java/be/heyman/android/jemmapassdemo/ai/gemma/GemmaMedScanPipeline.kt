/*
 * GemmaMedScanPipeline.kt — JEMMA Pass · JemmaAppDemo · v2.6.2a.4
 *
 * 🆕 v2.6.2a.4 — KB CASCADE FIX backed by direct KB audit.
 *
 *   Diagnosed by parsing knowledge_full.db directly. The 3 concepts we
 *   need for Augmentin all exist in `terminology_codes` :
 *
 *     C0054066 | NULL    | "amoxicillin / clavulanate"            | Medication
 *     C3653015 | J01CR02 | "amoxicillin and beta-lactamase inh."  | Chemical_Allergen
 *     C0002645 | J01CA04 | "amoxicillin"                          | Chemical_Allergen
 *
 *   This KB uses a counter-intuitive category split :
 *     - category='Medication' -> RxNorm Clinical Drug Forms (specific
 *       formulations like "Product containing precisely amoxicillin
 *       500 mg") - most have NULL atc_code.
 *     - category='Chemical_Allergen' -> UMLS substance concepts -
 *       these are the ones with ATC codes attached (J01CR02, J01CA04).
 *
 *   So our previous searchCodes(query, categoryFilter="Medication", ...)
 *   was filtering OUT exactly the rows we needed (C3653015 has ATC J01CR02
 *   but category Chemical_Allergen). The KB sourcing is fine -- the SEARCH
 *   STRATEGY was wrong.
 *
 *   FIX : two-tier cascading search per candidate.
 *
 *     - Tier 1 -- categoryFilter="Medication" (fast happy path : if the
 *       med has a clinical drug entry with ATC, we get it cheap)
 *     - Tier 2 -- if Tier 1 returns no ATC-bearing hits for this candidate,
 *       retry with categoryFilter=null (no filter) so we sweep up the
 *       Chemical_Allergen rows that hold the ATC code.
 *
 *   Deduplication on ATC means we never get the same ATC twice across
 *   tiers, and orphan hits (NULL ATC) are still preserved in `withoutAtc`
 *   for diagnostic display.
 *
 *   Expected behavior on Augmentin :
 *     candidate "amoxicillin clavulanate"
 *       Tier 1 -> 3 orphan hits (Medication, NULL ATC) -> no ATC found
 *       Tier 2 -> C3653015 (Chemical_Allergen, ATC J01CR02) -> shortlist!
 *     candidate "amoxicillin"
 *       Tier 2 -> C0002645 (Chemical_Allergen, ATC J01CA04)
 *     -> CROSS_CHECK gets J01CR02 -> matches class J01C -> matches Kurodo
 *        penicillin allergy -> MAJEUR rouge.
 *
 * --- On @Tool function calling ---------------------------------------
 *
 *   The user asked why this pipeline doesn't use the @Tool methods from
 *   JemmaTools.kt (v2.6.0) for KB queries.
 *
 *   The webview-aligned pattern uses strict JSON output prompting rather
 *   than function calling because:
 *
 *     - Latency : 1 Gemma call ~ 8s on E4B. @Tool needs 2-4 round-trips,
 *       blowing past our <15s end-to-end target for the killer demo.
 *     - Token budget : tool defs + results re-prefix every loop. On 1024-
 *       tok phones (Samsung G781B), context overflows.
 *     - Field-proven : the webview prompt at L44.16.73 is deterministic
 *       across hundreds of OCR variants. @Tool on Gemma 4 E4B + LiteRT-LM
 *       remains flaky on NPU.
 *
 *   The right place for @Tool is :
 *     - Phase 1c multimodal disambiguation (2.6.2b) when Gemma may need
 *       to inspect specific KB entries before picking.
 *     - Future dictation surfaces (free narrative needs lookup).
 *
 *   For the scan pipeline, strict JSON + deterministic Kotlin KB search
 *   stays the right call. The KB cascade fix here proves it : we don't
 *   need Gemma to decide which table to query, we need our Kotlin code
 *   to know that one filter level isn't enough.
 *
 * Log channel : JEMMA-GEMMA-PIPE
 */
package be.heyman.android.jemmapassdemo.ai.gemma

import android.graphics.Bitmap
import android.util.Log
import be.heyman.android.jemmapassdemo.kb.KbConcept
import be.heyman.android.jemmapassdemo.kb.KbSearchHit
import be.heyman.android.jemmapassdemo.kb.KbSearchResult
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "JEMMA-GEMMA-PIPE"

/**
 * Outcome of [GemmaMedScanPipeline.resolveDrugFromImage].
 *
 * 🆕 v2.6.2a.1 — added [NoAtcInKb] variant : Gemma's candidates DID match
 * the KB but the matched concept(s) have no ATC code attached (so cross-
 * check is impossible). Previously these were silently routed to NoKbHit,
 * which lost diagnostic information.
 */
sealed class GemmaResolveResult {

    data class Resolved(
        val concept: KbConcept,
        val candidates: List<String>,
        val matchedTerm: String,
        val matchedVia: String,
        val phase1aMs: Long,
        val phase1bMs: Long,
        val shortlistSize: Int,
    ) : GemmaResolveResult()

    data class NoCandidates(
        val gemmaRawResponse: String,
        val phase1aMs: Long,
    ) : GemmaResolveResult()

    data class NoKbHit(
        val candidates: List<String>,
        val phase1aMs: Long,
        val phase1bMs: Long,
    ) : GemmaResolveResult()

    /** Candidates matched the KB but matched concepts have no ATC. */
    data class NoAtcInKb(
        val candidates: List<String>,
        val orphanHitsDisplay: List<String>,
        val phase1aMs: Long,
        val phase1bMs: Long,
    ) : GemmaResolveResult()

    data class Failed(
        val phase: String,
        val reason: String,
        val durationMs: Long,
    ) : GemmaResolveResult()
}

@Singleton
class GemmaMedScanPipeline @Inject constructor(
    private val gemma: GemmaSession,
    private val kb: KnowledgeBaseService,
) {

    init {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🆕 GemmaMedScanPipeline @Singleton instantiated · v2.6.2a.1 (webview-aligned prompt)")
    }

    suspend fun resolveDrugFromImage(
        ocrText: String,
        @Suppress("UNUSED_PARAMETER") image: Bitmap?,
        lang: String,
    ): GemmaResolveResult {
        val tStart = System.currentTimeMillis()
        Log.i(TAG, "[t=$tStart] 🟢 resolveDrugFromImage · ocrLen=${ocrText.length} · " +
            "image=${image != null} · lang=$lang")

        val t1a = System.currentTimeMillis()
        val candidates: List<String> = try {
            phase1aExtractCandidates(ocrText, lang)
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ phase 1a threw", e)
            return GemmaResolveResult.Failed(
                phase = "1a",
                reason = e.message ?: e::class.java.simpleName,
                durationMs = System.currentTimeMillis() - tStart,
            )
        }
        val phase1aMs = System.currentTimeMillis() - t1a
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ phase 1a · ${candidates.size} candidate(s) " +
            "in ${phase1aMs}ms · " + candidates.joinToString(" | "))

        if (candidates.isEmpty()) {
            return GemmaResolveResult.NoCandidates(
                gemmaRawResponse = "(empty parse)",
                phase1aMs = phase1aMs,
            )
        }

        val t1b = System.currentTimeMillis()
        val (shortlist, orphans) = phase1bSearchKbInternal(candidates, lang)
        val phase1bMs = System.currentTimeMillis() - t1b
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ phase 1b · ${shortlist.size} ATC hit(s) + " +
            "${orphans.size} orphan(s) (no ATC) in ${phase1bMs}ms · " +
            shortlist.take(5).joinToString(" | ") { "${it.hit.concept.atcCode}:${it.hit.display.take(20)}" })

        if (shortlist.isEmpty()) {
            return if (orphans.isNotEmpty()) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ all KB hits orphan (no ATC) · " +
                    "orphans=${orphans.joinToString(", ") { it.hit.display.take(30) }}")
                GemmaResolveResult.NoAtcInKb(
                    candidates = candidates,
                    orphanHitsDisplay = orphans.map { it.hit.display }.distinct().take(5),
                    phase1aMs = phase1aMs,
                    phase1bMs = phase1bMs,
                )
            } else {
                GemmaResolveResult.NoKbHit(
                    candidates = candidates,
                    phase1aMs = phase1aMs,
                    phase1bMs = phase1bMs,
                )
            }
        }

        val top = shortlist.first()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 picked top KB hit · " +
            "atc=${top.hit.concept.atcCode} · display='${top.hit.display}' · " +
            "matched='${top.matchedTerm}' via=${top.hit.matchedVia}")

        return GemmaResolveResult.Resolved(
            concept = top.hit.concept,
            candidates = candidates,
            matchedTerm = top.matchedTerm,
            matchedVia = top.hit.matchedVia,
            phase1aMs = phase1aMs,
            phase1bMs = phase1bMs,
            shortlistSize = shortlist.size,
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // Phase 1a — webview-aligned prompt
    // ────────────────────────────────────────────────────────────────────

    /**
     * Public access to phase 1a. Returns 3-5 candidates as English INNs
     * (with brand/JP fallbacks). Empty list = Gemma failed to produce
     * usable candidates (caller should surface this as NotIdentified).
     */
    suspend fun phase1aExtractCandidates(ocrText: String, lang: String): List<String> {
        val systemInstr = SYSTEM_INSTRUCTION_PHASE_1A
        val userPrompt = buildString {
            appendLine("BOX TO ANALYZE :")
            appendLine()
            appendLine("=== BOX 1 ===")
            appendLine(ocrText.trim().take(600))
            appendLine()
            appendLine("Output ONLY the JSON object now, beginning with {\"candidates\":")
        }
        val raw = gemma.ask(
            prompt = userPrompt,
            image = null, // phase 1c (2.6.2b) is where the image enters
            systemInstruction = systemInstr,
        )
        return parseCandidates(raw)
    }

    /**
     * Result of [phase1bSearchKb] : separates ATC-bearing hits (the actual
     * shortlist — usable for cross-check) from orphan hits (concepts that
     * matched but have no ATC code attached — useful diagnostic info).
     */
    data class Phase1bResult(
        val withAtc: List<KbCandidate>,
        val withoutAtc: List<KbCandidate>,
    ) {
        val isEmpty: Boolean get() = withAtc.isEmpty() && withoutAtc.isEmpty()
        val hasUsableHits: Boolean get() = withAtc.isNotEmpty()
    }

    suspend fun phase1bSearchKb(
        candidates: List<String>,
        lang: String,
    ): Phase1bResult {
        val (shortlistInternal, orphansInternal) = phase1bSearchKbInternal(candidates, lang)
        return Phase1bResult(
            withAtc = shortlistInternal.map { sh ->
                KbCandidate(
                    concept = sh.hit.concept,
                    display = sh.hit.display,
                    matchedTerm = sh.matchedTerm,
                    matchedVia = sh.hit.matchedVia,
                )
            },
            withoutAtc = orphansInternal.map { sh ->
                KbCandidate(
                    concept = sh.hit.concept,
                    display = sh.hit.display,
                    matchedTerm = sh.matchedTerm,
                    matchedVia = sh.hit.matchedVia,
                )
            },
        )
    }

    /** Public clean form of a KB hit. */
    data class KbCandidate(
        val concept: KbConcept,
        val display: String,
        val matchedTerm: String,
        val matchedVia: String,
    )

    private data class ScoredHit(
        val hit: KbSearchHit,
        val matchedTerm: String,
        val scoreRank: Int,
    )

    /**
     * Returns (withAtc, withoutAtc). The withAtc list is the actual
     * shortlist — sorted ATC-first, then by candidate priority. The
     * withoutAtc list is kept separately so the caller can surface
     * helpful diagnostics ("Gemma's candidates matched the KB but the
     * concepts have no ATC code attached").
     *
     * 🆕 v2.6.2a.4 — two-tier cascade search.
     *   Tier 1 : Medication filter (fast happy path)
     *   Tier 2 : no filter (catches Chemical_Allergen rows with ATC like
     *            J01CR02 which sit outside the Medication category in
     *            this KB)
     */
    private suspend fun phase1bSearchKbInternal(
        candidates: List<String>,
        lang: String,
    ): Pair<List<ScoredHit>, List<ScoredHit>> {
        val withAtc = mutableListOf<ScoredHit>()
        val withoutAtc = mutableListOf<ScoredHit>()
        val seenAtcs = mutableSetOf<String>()
        var globalRank = 0
        for (rawCandidate in candidates) {
            val candidate = sanitizeForKb(rawCandidate)
            if (candidate.length < 3) {
                Log.d(TAG, "[t=${System.currentTimeMillis()}]    skip '$rawCandidate' (sanitized to '$candidate', too short)")
                continue
            }

            val foundAtcCountForCandidate = withAtc.count { it.matchedTerm == candidate }

            // ── Tier 1 : with Medication filter (fast happy path) ─────
            val tier1 = kb.searchCodes(
                query = candidate,
                lang = lang,
                categoryFilter = "Medication",
                maxResults = 5,
            )
            if (tier1 is KbSearchResult.Success && tier1.hits.isNotEmpty()) {
                Log.d(TAG, "[t=${System.currentTimeMillis()}]    [T1] searchCodes('$candidate', cat=Medication) → ${tier1.hits.size} hits in ${tier1.latencyMs}ms")
                for (hit in tier1.hits) {
                    val atc = hit.concept.atcCode
                    if (atc.isNullOrBlank()) {
                        withoutAtc.add(ScoredHit(hit, candidate, globalRank++))
                        Log.d(TAG, "[t=${System.currentTimeMillis()}]       [T1] '${hit.display.take(40)}' → orphan")
                    } else if (atc !in seenAtcs) {
                        seenAtcs.add(atc)
                        withAtc.add(ScoredHit(hit, candidate, globalRank++))
                        Log.d(TAG, "[t=${System.currentTimeMillis()}]       [T1] '${hit.display.take(40)}' → atc=$atc")
                    }
                }
            } else {
                Log.d(TAG, "[t=${System.currentTimeMillis()}]    [T1] searchCodes('$candidate', cat=Medication) → 0 hits")
            }

            // ── Tier 2 : without filter — only if Tier 1 didn't yield ─
            // an ATC for this specific candidate. Catches Chemical_Allergen
            // rows like C3653015 (amox+clav, J01CR02) and C0002645 (amox,
            // J01CA04) which sit outside Medication in this KB.
            val newAtcsAfterT1 = withAtc.count { it.matchedTerm == candidate } - foundAtcCountForCandidate
            if (newAtcsAfterT1 == 0) {
                val tier2 = kb.searchCodes(
                    query = candidate,
                    lang = lang,
                    categoryFilter = null,
                    maxResults = 5,
                )
                if (tier2 is KbSearchResult.Success && tier2.hits.isNotEmpty()) {
                    Log.d(TAG, "[t=${System.currentTimeMillis()}]    [T2] searchCodes('$candidate', cat=ANY) → ${tier2.hits.size} hits in ${tier2.latencyMs}ms")
                    for (hit in tier2.hits) {
                        val atc = hit.concept.atcCode
                        if (atc.isNullOrBlank()) {
                            // Already have Tier 1 orphans for this candidate,
                            // no point duplicating them in withoutAtc — keep
                            // only ATC-bearing rows from Tier 2.
                            continue
                        }
                        if (atc !in seenAtcs) {
                            seenAtcs.add(atc)
                            withAtc.add(ScoredHit(hit, candidate, globalRank++))
                            Log.d(TAG, "[t=${System.currentTimeMillis()}]       [T2] '${hit.display.take(40)}' → atc=$atc (cat=${hit.concept.category})")
                        }
                    }
                } else {
                    Log.d(TAG, "[t=${System.currentTimeMillis()}]    [T2] searchCodes('$candidate', cat=ANY) → 0 hits")
                }
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}]    cascade summary · withAtc=${withAtc.size} · withoutAtc=${withoutAtc.size} · uniqueAtcs=${seenAtcs.size}")
        // ATC-only shortlist sorted by globalRank (Gemma's confidence order).
        return Pair(withAtc.sortedBy { it.scoreRank }, withoutAtc)
    }

    // ────────────────────────────────────────────────────────────────────
    // Webview-aligned system prompt (port of _L27_EXTRACT_SYS_INSTRUCTION)
    // ────────────────────────────────────────────────────────────────────

    private val SYSTEM_INSTRUCTION_PHASE_1A = """
        You are a multilingual medication identification assistant.

        YOUR TASK
        Identify the medication on the OCR'd box below, then output 3-5 search candidates
        that we will look up in our clinical knowledge base. Your job is to give us a
        SHORTLIST that is wide enough to contain the correct answer, not a single best
        guess. Phase 1c (multimodal) will pick the final answer from your shortlist.

        CRITICAL — HOW YOUR OUTPUT WILL BE USED
        Your candidates are passed to a SQL FTS5 search over an ENGLISH clinical
        knowledge base (RxNorm + SNOMED-CT + WHO ATC). The KB contains ENGLISH
        international generic names (INN) — NOT brand names, NOT French names,
        NOT Japanese names.

        So your FIRST candidate MUST be the ENGLISH GENERIC name (INN). Use your
        medical knowledge to translate brand → INN. Then add 2-4 secondary candidates
        (alternative INNs that the OCR could plausibly map to, local generic, brand)
        as fallback.

        CRITICAL — WHEN A DRUG HAS BOTH A COMMON ENGLISH NAME AND A LONG CHEMICAL NAME,
        ALWAYS PUT THE COMMON NAME FIRST.
        The KB's primary display is usually the WHO INN, which is the short common name.
        The long chemical name often appears only in the display of COMBINATION drugs,
        so if you put the chemical name first you risk matching the WRONG combination
        ATC for the patient.

        Examples of right ordering (anti-pattern → corrected):
          ✗ ["acetylsalicylic acid"]    → ✓ ["aspirin", "acetylsalicylic acid"]
          ✗ ["acetaminophen"]            → ✓ ["paracetamol", "acetaminophen"]
          ✗ ["levothyroxine sodium"]     → ✓ ["thyroxine", "levothyroxine"]

        CRITICAL — OCR IS UNRELIABLE, ESPECIALLY ON KATAKANA
        The OCR text you receive may be CORRUPTED:
          • Missing characters (e.g. "オーメンチン" instead of "オーグメンチン" — the グ dropped)
          • Substituted characters ("配館" instead of "配合", "配合設" instead of "配合錠")
          • Fragmented words (e.g. "オークメン後", "オーク火U")
          • Extra noise (batch codes "GS 603", "250RS", "セ250RS")

        When the OCR contains FRAGMENTED OR PARTIAL katakana that resembles a known
        Japanese drug brand, you MUST list multiple plausible matches that share
        similar phonemes. Do NOT collapse to a single guess from partial OCR — let
        the multimodal phase pick the right one from the image. Confidence is
        downstream's job, not yours.

        EXAMPLES — brand→INN mappings showing the kind of clinical reasoning expected:
        • Box says "Doliprane 1000" (FR brand)
            → ["paracetamol", "acetaminophen"]
        • Box says "タミフル 75mg / Tamiflu" (JP brand)
            → ["oseltamivir"]
        • Box says "Lipitor 20 mg" (US/EU brand)
            → ["atorvastatin"]
        • Box says "Augmentin 875 mg / Amoxicilline + Acide clav."
            → ["amoxicillin clavulanate", "amoxicillin", "co-amoxiclav"]
        • Box says "ガスモチン 5mg" (JP brand)
            → ["mosapride"]
        • Box says "Lévothyrox 50µg / Levothyroxine sodium"
            → ["levothyroxine", "thyroxine"]  (sodium = salt form, ignore)

        EXAMPLES OF CORRUPT OCR — output WIDER shortlists, never collapse:
        • Box says "オーメンチン配合館 250RS" (corrupt: missing グ)
            → ["amoxicillin clavulanate", "amoxicillin", "augmentin", "co-amoxiclav"]
          (recognized as likely オーグメンチン despite missing kana — let multimodal confirm)
        • Box says "サワン カプセル 250" (corrupt: missing シリ)
            → ["amoxicillin", "ampicillin", "sawacillin", "cefalexin"]
        • Box says "アムロジン 5" (clear)
            → ["amlodipine", "amlodin"]
          (clear identification → 2 candidates is fine)

        EXCLUDE
        Dosages (1000mg, 50000 IU), pack quantities (20 comprimés), marketing slogans
        ("DOULEURS ET FIEVRE", "TAMPONNÉE"), batch codes, OCR garbage (e.g. "Oukmentin",
        "オーメンチン250RS" — these are NOT real drug names, just noise).

        OUTPUT FORMAT — STRICT JSON, NO MARKDOWN FENCES, NO PROSE
        {"candidates":["english_inn_1","english_inn_2","english_inn_3"]}

        Aim for 3-5 candidates when OCR is noisy. 1-2 candidates is OK only when the
        brand name is unambiguous.
    """.trimIndent()

    // ────────────────────────────────────────────────────────────────────
    // Parsers — JSON first, bullets fallback, longest-line last resort
    // ────────────────────────────────────────────────────────────────────

    /**
     * Robust parse :
     *   1. Try strict JSON (with markdown fence stripping)
     *   2. Try bullet-list (`- foo` / `* foo` / `1. foo`)
     *   3. Last resort : longest line
     */
    internal fun parseCandidates(rawResponse: String): List<String> {
        // Strategy 1 : JSON
        parseJson(rawResponse)?.let { fromJson ->
            if (fromJson.isNotEmpty()) {
                Log.d(TAG, "[t=${System.currentTimeMillis()}]    parsed ${fromJson.size} candidate(s) from JSON")
                return fromJson.take(5)
            }
        }
        // Strategy 2 : bullets
        val fromBullets = parseBullets(rawResponse)
        if (fromBullets.isNotEmpty()) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}]    parsed ${fromBullets.size} candidate(s) from bullets (JSON failed)")
            return fromBullets.take(5)
        }
        // Strategy 3 : longest line
        val longest = rawResponse.lines()
            .map { it.trim() }
            .filter { it.length in 3..80 }
            .maxByOrNull { it.length }
        if (longest != null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}]    ⚠️ no JSON/bullets, falling back to longest line: $longest")
            return listOf(longest)
        }
        return emptyList()
    }

    private fun parseJson(raw: String): List<String>? {
        return try {
            val stripped = stripJsonFences(raw)
            val firstBrace = stripped.indexOf('{')
            val firstBracket = stripped.indexOf('[')
            val startIdx = when {
                firstBrace >= 0 && (firstBracket < 0 || firstBrace < firstBracket) -> firstBrace
                firstBracket >= 0 -> firstBracket
                else -> return null
            }
            val (jsonStr, _) = extractBalanced(stripped, startIdx) ?: return null
            // Either {"candidates":[...]} or a bare array
            val candidates = when {
                jsonStr.trimStart().startsWith("[") -> JSONArray(jsonStr)
                else -> {
                    val obj = JSONObject(jsonStr)
                    obj.optJSONArray("candidates")
                        ?: obj.optJSONArray("c")
                        ?: obj.optJSONArray("inn")
                        ?: obj.optJSONArray("results")
                        ?: return null
                }
            }
            val out = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            for (i in 0 until candidates.length()) {
                val raw = candidates.optString(i, "").trim()
                if (raw.isEmpty()) continue
                val cleaned = cleanCandidate(raw)
                if (cleaned.isEmpty()) continue
                val key = cleaned.lowercase()
                if (key in seen) continue
                seen.add(key)
                out.add(cleaned)
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    private fun parseBullets(raw: String): List<String> {
        val bulletRegex = Regex("""^\s*[-*•▪➤]+\s*(.+?)\s*$""")
        val numberedRegex = Regex("""^\s*\d+[.)]\s*(.+?)\s*$""")
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (rawLine in raw.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val candidate = bulletRegex.find(line)?.groupValues?.get(1)
                ?: numberedRegex.find(line)?.groupValues?.get(1)
                ?: continue
            val cleaned = cleanCandidate(candidate)
            if (cleaned.isEmpty()) continue
            val key = cleaned.lowercase()
            if (key in seen) continue
            seen.add(key)
            out.add(cleaned)
            if (out.size >= 8) break
        }
        return out
    }

    private fun cleanCandidate(raw: String): String = raw
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .removeSurrounding("«", "»")
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""\*(.+?)\*"""), "$1")
        .trim()
        .trimEnd(',', '.', ';', ':')
        .trim()

    private fun stripJsonFences(s: String): String {
        return s.trim()
            .replace(Regex("""^```(?:json|JSON)?\s*""", RegexOption.MULTILINE), "")
            .replace(Regex("""```\s*$""", RegexOption.MULTILINE), "")
            .trim()
    }

    /**
     * Bracket-balanced JSON extractor — finds the matching closing brace
     * for the opening at startIdx, respecting nested objects and strings.
     */
    private fun extractBalanced(s: String, startIdx: Int): Pair<String, Int>? {
        val openChar = s.getOrNull(startIdx) ?: return null
        val closeChar = when (openChar) {
            '{' -> '}'
            '[' -> ']'
            else -> return null
        }
        var depth = 0
        var inString = false
        var escape = false
        for (i in startIdx until s.length) {
            val c = s[i]
            if (inString) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) {
                        return Pair(s.substring(startIdx, i + 1), i + 1)
                    }
                }
            }
        }
        return null
    }

    // ────────────────────────────────────────────────────────────────────
    // KB query sanitization (port of webview sanitizeForKB)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Port of webview `sanitizeForKB` — strips special chars and standalone
     * digits so candidates like "Augmentin 250mg" become "Augmentin" and
     * "amoxicillin / clavulanate" becomes "amoxicillin clavulanate" before
     * hitting FTS5.
     */
    private fun sanitizeForKb(s: String): String {
        if (s.isBlank()) return ""
        return s
            .replace(Regex("""[/\\!?*"():\[\]{}<>=+&%@#$^|~`,;]"""), " ")
            .replace(Regex("""\b\d+\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
