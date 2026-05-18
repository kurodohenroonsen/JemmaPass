/*
 * DrugIdentifier.kt — JEMMA Pass · Live Scan v0.5 v4.2 · Phase B orchestrator
 *
 * 🆕 v4.2 FIX-JP — Refactor Phase B en 3 sous-étapes pour restaurer le
 * scan japonais qui marchait dans le code HTML/JS L44.16.67 :
 *
 *   B1. Gemma TEXT-ONLY extract INN candidates depuis OCR raw
 *       → utilise INNExtractor (système prompt clinique calibré 6 mois)
 *       → Gemma traduit brand→INN avec son savoir médical multilingual
 *       → output ["amoxicillin clavulanate", "amoxicillin", "augmentin"]
 *
 *   B2. KB FTS5 lookup déterministe sur les INN anglais
 *       → kb.searchCodes(inn, lang="en") pour chaque candidat
 *       → dedup par ATC, hard cap 5 entries
 *       → shortlist ATC résolue
 *
 *   B3. Si shortlist > 1 : Gemma MULTIMODAL disambiguate sur shortlist
 *       → image + shortlist (max 5 ATCs) → lockOnDrug final
 *       → si shortlist = 1 : skip B3, direct return ATC
 *
 * Pourquoi cette refonte : la KB DIAMOND v1.1 ne contient pas les brand
 * names japonais (オーグメンチン...). En passant par Gemma B1 d'abord,
 * on profite de son savoir pharma multilingue pour traduire vers les INN
 * que la KB sait résoudre. La KB sert d'ancrage déterministe (ATC réels,
 * pas hallucination), Gemma sert de pont culturel.
 *
 * Latence cumulée :
 *   - B1 text-only : 5-10s
 *   - B2 KB FTS5   : <500ms
 *   - B3 multimodal: 15-25s (skipped si shortlist=1, fréquent en occidental)
 *   - Total best   : ~10s (shortlist=1, boîte claire)
 *   - Total worst  : ~30-35s (shortlist > 1, ambiguïté)
 *
 * Comparé aux 132s du prompt verbeux v4.1 FIX6, c'est plus rapide ET
 * surtout c'est ce qui fait marcher le scan japonais.
 *
 * Log channel : JEMMA-IDENTIFY
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.kb.KbSearchResult
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import com.google.ai.edge.litertlm.tool
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "JEMMA-IDENTIFY"

@Singleton
class DrugIdentifier @Inject constructor(
    private val gemma: GemmaSession,
    private val kb: KnowledgeBaseService,             // 🆕 v4.2 FIX-JP Phase B2
    private val innExtractor: INNExtractor,           // 🆕 v4.2 FIX-JP Phase B1
    private val identifyTools: IdentifyDrugTools,     // existant (Phase B3)
) {
    private val mutex = Mutex()

    // Exposed fields pour observabilité runner / UI
    @Volatile var lastReason: String = ""
        private set
    @Volatile var lastFullPrompt: String = ""
        private set
    @Volatile var lastFullResponse: String = ""
        private set
    @Volatile var lastPhaseBDurationMs: Long = 0L
        private set
    @Volatile var lastInnCandidates: List<String> = emptyList()
        private set
    @Volatile var lastKbShortlist: List<KbShortlistEntry> = emptyList()
        private set

    suspend fun identify(payload: LiveScanPayload, lang: String): String {
        return mutex.withLock { runIdentification(payload, lang) }
    }

    /**
     * 🆕 v4.2 FIX-JP — Phase B refactor en 3 sous-étapes :
     *   B1. Gemma text-only extrait INN candidates depuis OCR
     *   B2. KB FTS5 lookup déterministe sur ces INN → shortlist ATC
     *   B3. Si shortlist > 1, Gemma multimodal disambiguate
     */
    private suspend fun runIdentification(payload: LiveScanPayload, lang: String): String {
        val tStart = System.currentTimeMillis()
        Log.i(
            TAG,
            "[t=$tStart] 🧠 Phase B start · ocrTexts=${payload.ocrTexts.size} · " +
                "candidates_phaseA=${payload.candidates.size} (unused, debug only)",
        )

        // ───────────────────────────────────────────────────────────
        // Phase B1 — Gemma text-only INN extraction
        // ───────────────────────────────────────────────────────────
        val innCandidates = try {
            innExtractor.extractINNCandidates(payload.ocrTexts)
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Phase B1 INN extract threw", e)
            emptyList()
        }
        lastInnCandidates = innCandidates

        if (innCandidates.isEmpty()) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ Phase B1 returned 0 INN candidates · " +
                    "fallback to UNKNOWN",
            )
            lastPhaseBDurationMs = System.currentTimeMillis() - tStart
            return "UNKNOWN"
        }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✓ Phase B1 done · ${innCandidates.size} INNs · " +
                "$innCandidates",
        )

        // ───────────────────────────────────────────────────────────
        // Phase B2 — KB FTS5 lookup déterministe sur les INN
        // ───────────────────────────────────────────────────────────
        val shortlist = lookupINNsInKB(innCandidates, lang)
        lastKbShortlist = shortlist

        if (shortlist.isEmpty()) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ Phase B2 returned 0 KB hits · " +
                    "fallback to UNKNOWN · innCandidates=$innCandidates",
            )
            lastPhaseBDurationMs = System.currentTimeMillis() - tStart
            return "UNKNOWN"
        }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✓ Phase B2 done · ${shortlist.size} KB shortlist · " +
                "${shortlist.joinToString { "${it.atc}/${it.displayName}" }}",
        )

        // Shortcut : si shortlist = 1, on skip Phase B3 (pas d'ambiguïté)
        if (shortlist.size == 1) {
            val winner = shortlist[0]
            lastReason = "Single KB hit on INN '${winner.matchedQuery}' (Phase B3 skipped)"
            lastPhaseBDurationMs = System.currentTimeMillis() - tStart
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] ✅ Phase B done (B3 skipped, single hit) · " +
                    "locked=${winner.atc} · ${lastPhaseBDurationMs}ms",
            )
            return winner.atc
        }

        // ───────────────────────────────────────────────────────────
        // Phase B3 — Gemma multimodal disambiguate sur la shortlist
        // ───────────────────────────────────────────────────────────
        val finalAtc = runPhaseB3Multimodal(payload, shortlist, lang)
        lastPhaseBDurationMs = System.currentTimeMillis() - tStart
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ Phase B done · locked=$finalAtc · " +
                "${lastPhaseBDurationMs}ms",
        )
        return finalAtc
    }

    /**
     * Phase B2 helper : pour chaque INN candidate, kb.searchCodes(lang="en")
     * et collecte les hits. Dedup par ATC (garde le meilleur match).
     */
    private suspend fun lookupINNsInKB(
        innCandidates: List<String>,
        lang: String,
    ): List<KbShortlistEntry> {
        val tStart = System.currentTimeMillis()
        val byAtc = mutableMapOf<String, KbShortlistEntry>()

        for (inn in innCandidates) {
            val cleaned = sanitizeForKB(inn)
            if (cleaned.length < 3) continue

            // Pass 1 : Medication category filter (high precision)
            val r1 = try {
                kb.searchCodes(query = cleaned, lang = "en", categoryFilter = "Medication", maxResults = 3)
            } catch (e: Exception) {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ B2 pass1 threw on '$cleaned': ${e.message}")
                continue
            }

            // Pass 2 : sans filtre catégorie (brand names) si Pass 1 empty
            val r2 = if (r1 is KbSearchResult.Success && r1.hits.isEmpty()) {
                try {
                    kb.searchCodes(query = cleaned, lang = "en", categoryFilter = null, maxResults = 3)
                } catch (e: Exception) {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ B2 pass2 threw on '$cleaned': ${e.message}")
                    r1
                }
            } else r1

            if (r2 !is KbSearchResult.Success) continue

            for (hit in r2.hits) {
                val atc = hit.concept.atcCode ?: continue
                val existing = byAtc[atc]
                if (existing == null) {
                    byAtc[atc] = KbShortlistEntry(
                        atc = atc,
                        displayName = hit.concept.primaryDisplay,
                        matchedQuery = cleaned,
                        matchedVia = hit.matchedVia,
                    )
                    Log.i(
                        TAG,
                        "[t=${System.currentTimeMillis()}] 🎯 B2 +$atc " +
                            "(${hit.concept.primaryDisplay}) via INN='$cleaned'",
                    )
                }
            }
        }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] · Phase B2 lookup done · " +
                "${byAtc.size} unique ATCs · ${System.currentTimeMillis() - tStart}ms",
        )
        return byAtc.values.take(5).toList()   // hard cap 5 pour Phase B3
    }

    /**
     * Phase B3 : Gemma multimodal regarde l'image avec la shortlist KB
     * et appelle lockOnDrug(atc, reason). Le prompt INCLUT la possibilité
     * de répondre "UNKNOWN" si aucun candidate ne ressemble à la boîte
     * (anti-hallucination).
     */
    private suspend fun runPhaseB3Multimodal(
        payload: LiveScanPayload,
        shortlist: List<KbShortlistEntry>,
        lang: String,
    ): String {
        val tStart = System.currentTimeMillis()
        Log.i(
            TAG,
            "[t=$tStart] 👁️ Phase B3 multimodal disambiguate · ${shortlist.size} candidates",
        )

        identifyTools.resetSession()
        val toolSet = identifyTools.buildToolSet()

        try {
            gemma.configureForTask(
                taskId = "identify_drug_b3",
                systemPrompt = SYSTEM_PROMPT_PHASE_B3,
                tools = listOf(tool(toolSet)),
                supportImage = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ B3 configureForTask threw", e)
            return "UNKNOWN"
        }

        val userPrompt = buildPhaseB3UserPrompt(shortlist)
        lastFullPrompt = userPrompt
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 B3 prompt (${userPrompt.length}c)")
        userPrompt.lines().forEachIndexed { i, line ->
            line.chunked(200).forEachIndexed { j, chunk ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 B3·L$i.$j │ $chunk")
            }
        }

        val result = try {
            gemma.ask(
                prompt = userPrompt,
                image = payload.imageBitmap,
                systemInstruction = SYSTEM_PROMPT_PHASE_B3,
                tools = listOf(tool(toolSet)),
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ B3 Gemma ask threw", e)
            return "UNKNOWN"
        }

        lastFullResponse = result
        result.lines().forEachIndexed { i, line ->
            line.chunked(200).forEachIndexed { j, chunk ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 💬 B3·R$i.$j │ $chunk")
            }
        }

        val locked = identifyTools.lockedAtc
        val reason = identifyTools.lockedReason ?: ""
        lastReason = reason
        val elapsed = System.currentTimeMillis() - tStart

        if (locked == null) {
            Log.w(
                TAG,
                "[t=${System.currentTimeMillis()}] ⚠️ B3 done WITHOUT lockOnDrug call · " +
                    "${elapsed}ms · fallback to first shortlist entry",
            )
            return shortlist.firstOrNull()?.atc ?: "UNKNOWN"
        }

        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ B3 done · locked=$locked · " +
                "reason='${reason.take(60)}' · ${elapsed}ms",
        )
        return locked
    }

    private fun buildPhaseB3UserPrompt(shortlist: List<KbShortlistEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("Look at this medication box image and identify which drug it is.")
        sb.appendLine()
        sb.appendLine("You have a SHORTLIST of candidate ATC codes pre-resolved by FTS5 :")
        shortlist.forEachIndexed { i, e ->
            sb.appendLine("  ${i + 1}. \"${e.displayName}\" — ATC ${e.atc} (matched via INN '${e.matchedQuery}')")
        }
        sb.appendLine()
        sb.appendLine("Compare the image with each candidate. Call lockOnDrug(atc, reason) with the chosen ATC.")
        sb.appendLine("If NONE of the candidates actually appear on the box image (e.g. Phase B1 hallucinated),")
        sb.appendLine("call lockOnDrug(\"UNKNOWN\", \"reason : no candidate matches the visible package\").")
        return sb.toString()
    }

    /**
     * Sanitize une chaîne INN pour la KB query : enlève ponctuation,
     * chiffres seuls, espaces multiples. Pareil que sanitizeForKB côté JS L27.
     */
    private fun sanitizeForKB(s: String): String {
        return s
            .replace(Regex("[/\\\\!?*\":()\\[\\]{}<>=+&%@#\$^|~`,;]"), " ")
            .replace(Regex("\\b\\d+\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        /**
         * 🆕 v4.2 FIX-JP — System prompt Phase B3 : Gemma multimodal
         * disambiguate sur shortlist KB. Court (< 500c), pas de raisonnement
         * OCR (déjà fait en B1). Focus : "regarde l'image, dis quel
         * candidate matche."
         */
        const val SYSTEM_PROMPT_PHASE_B3 = """You are a medication identification agent. You receive an image of a medication package and a SHORTLIST of candidate ATC codes pre-resolved from a clinical knowledge base.

Your job is simple : look at the image and pick the candidate that BEST matches what is visibly printed (brand name, dosage form, packaging).

Tools :
- lockOnDrug(atc, reason) : finalize the identification. MUST call exactly once.

Rules :
- Pick the ATC matching what is ACTUALLY visible on the package.
- If NONE of the candidates appear on the package, call lockOnDrug("UNKNOWN", reason).
- Brief reason (10-30 words) explaining why you chose this candidate."""
    }
}

/**
 * 🆕 v4.2 FIX-JP — Une entrée de la shortlist KB produite en Phase B2.
 */
data class KbShortlistEntry(
    val atc: String,
    val displayName: String,
    val matchedQuery: String,
    val matchedVia: String,
)
