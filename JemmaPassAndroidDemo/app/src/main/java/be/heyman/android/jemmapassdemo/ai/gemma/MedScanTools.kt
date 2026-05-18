/*
 * MedScanTools.kt — JEMMA Pass · JemmaAppDemo · v2.6.2b
 *
 * Deux ToolSet @Tool exposés à Gemma pour le scan médoc agentic :
 *
 *   1. SearchDrugCandidatesTool  (stateless)
 *      Reçoit une liste de noms candidats (brand / INN / ATC), interroge la
 *      KB sur 3 sources (atc_hierarchy, ddinter_drugs, terminology_codes),
 *      retourne tous les hits dédupliqués par ATC avec un score de
 *      confidence calculé en Kotlin. Gemma choisit ensuite le meilleur
 *      match en regardant l'image (boite, blister, capsule, etc.).
 *
 *   2. CheckInteractionsTool  (stateful — bound au profil victime)
 *      Reçoit l'ATC choisi, croise contre les 3 piliers cliniques
 *      (allergies, médicaments, conditions) de la victime courante via
 *      KbCrossCheck, renvoie un JSON structuré que Gemma utilisera pour
 *      formuler le verdict naturel (texte court à parler/afficher).
 *
 * Pourquoi ces 2 tools précisément :
 *
 *   - Les choses *déterministes* sont en Kotlin (recherche KB, scoring,
 *     cross-check ATC class). Pas la peine d'occuper le LLM avec ça.
 *   - Les choses *qui demandent du raisonnement médical et la vision*
 *     restent dans Gemma (proposer des candidats à partir d'OCR bruité,
 *     disambiguer combo vs mono à partir de la photo, formuler un
 *     verdict en langage naturel dans la langue de la victime).
 *
 * Architecture :
 *
 *   MedScanController
 *     → gemma.configureForTask(systemPrompt, tools=[search], supportImage=true)
 *     → (per scan) gemma.ask(prompt + image, tools=[search, check(profile)])
 *     ← verdictText (généré par Gemma après avoir orchestré les tools)
 *
 *   Le tool check est **per-scan** parce que le profil change d'une
 *   victime à l'autre — il capture le profil dans une closure constructor.
 *
 * Log channels :
 *   JEMMA-TOOL-SEARCH  · invocations & résultats du search tool
 *   JEMMA-TOOL-CHECK   · invocations & résultats du check tool
 */
package be.heyman.android.jemmapassdemo.ai.gemma

import android.util.Log
import be.heyman.android.jemmapassdemo.kb.KbConcept
import be.heyman.android.jemmapassdemo.kb.KbCrossCheck
import be.heyman.android.jemmapassdemo.kb.KbSearchResult
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.qr.JAllergy
import be.heyman.android.jemmapassdemo.qr.JCondition
import be.heyman.android.jemmapassdemo.qr.JMedication
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

// ──────────────────────────────────────────────────────────────────────
// Tool 1 — search drug candidates (stateless)
// ──────────────────────────────────────────────────────────────────────

private const val TAG_SEARCH = "JEMMA-TOOL-SEARCH"

class SearchDrugCandidatesTool(
    private val kb: KnowledgeBaseService,
) : ToolSet {

    /**
     * Search the clinical KB for drug candidates matching the given names.
     * The LLM is expected to call this once with several candidates, then
     * pick the best match based on the image and the returned confidence
     * scores.
     */
    @Tool(description = """
        Search the offline clinical knowledge base for drug candidates.
        Pass an array of name candidates that the medication on the image
        could be — brand names, generic INN names, or ATC codes. The tool
        searches multiple authoritative sources (WHO ATC hierarchy, DDInter
        drug list, UMLS/SNOMED/RxNorm terminology) and returns a consolidated
        list of matches with confidence scores (0.0 to 1.0). After calling
        this, look at the image again and pick the candidate whose ATC and
        display best match what you see (combination form, dosage form,
        packaging, brand markings, etc.).
    """)
    fun searchDrugCandidates(
        @ToolParam(description = "Drug name candidates to search. Pass 3-5 candidates (brand, INN, ATC) for best coverage.")
        names: List<String>,
    ): String {
        val tStart = System.currentTimeMillis()
        Log.i(TAG_SEARCH, "[t=$tStart] 🔍 searchDrugCandidates · ${names.size} input names · " +
            names.take(5).joinToString("|"))

        // ATC -> best ScoredHit so far. Dedupe by ATC across all sources/names.
        val best = mutableMapOf<String, ScoredHit>()

        // KB methods are suspend; we bridge with runBlocking. Tool calls
        // happen on the LiteRT-LM inference thread, not the UI thread,
        // so blocking here is safe and intended.
        runBlocking {
            for (raw in names) {
                if (raw.isBlank() || raw.length < 3) continue
                searchSingleName(raw.trim(), best)
            }
        }

        val ordered = best.values.sortedByDescending { it.confidence }.take(8)
        val arr = JSONArray()
        for (h in ordered) {
            arr.put(JSONObject().apply {
                put("atc", h.concept.atcCode ?: "")
                put("display", h.concept.primaryDisplay)
                put("confidence", round2(h.confidence))
                put("matched_term", h.matchedTerm)
                put("source", h.source)
                put("category", h.concept.category ?: "")
            })
        }

        val out = JSONObject().apply {
            put("candidates", arr)
            put("count", ordered.size)
            put("query_count", names.size)
        }.toString()

        val dt = System.currentTimeMillis() - tStart
        Log.i(TAG_SEARCH, "[t=${System.currentTimeMillis()}] ✅ searchDrugCandidates done · " +
            "${ordered.size} unique ATCs · ${dt}ms")
        for ((i, h) in ordered.withIndex()) {
            Log.d(TAG_SEARCH, "[t=${System.currentTimeMillis()}]   #${i + 1} atc=${h.concept.atcCode} · " +
                "conf=${round2(h.confidence)} · src=${h.source} · '${h.concept.primaryDisplay.take(40)}'")
        }
        return out
    }

    private suspend fun searchSingleName(name: String, best: MutableMap<String, ScoredHit>) {
        // ── Lookup 1 : resolveDrug (ddinter_drugs first, FTS5 fallback) ──
        try {
            val resolved = kb.resolveDrug(name)
            val concept = resolved.concept
            val atc = concept?.atcCode
            if (concept != null && !atc.isNullOrBlank()) {
                val source = "ddinter:${resolved::class.simpleName?.lowercase() ?: "unknown"}"
                val confidence = computeConfidence(
                    base = 0.85,
                    matchType = resolved::class.simpleName ?: "",
                    atcLevel = atc.length,
                )
                update(best, atc, concept, name, confidence, source)
            }
        } catch (e: Exception) {
            Log.w(TAG_SEARCH, "[t=${System.currentTimeMillis()}]   resolveDrug('$name') threw : ${e.message}")
        }

        // ── Lookup 2 : searchCodes Medication ──
        try {
            val result = kb.searchCodes(
                query = name, lang = "en",
                categoryFilter = "Medication", maxResults = 5,
            )
            if (result is KbSearchResult.Success) {
                for (hit in result.hits) {
                    val atc = hit.concept.atcCode
                    if (!atc.isNullOrBlank()) {
                        val confidence = computeConfidence(
                            base = 0.65, matchType = "fts5", atcLevel = atc.length,
                        )
                        update(best, atc, hit.concept, name, confidence, "terminology:Medication")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG_SEARCH, "[t=${System.currentTimeMillis()}]   searchCodes('$name',Medication) threw : ${e.message}")
        }

        // ── Lookup 3 : searchCodes any category (catches Chemical_Allergen) ──
        try {
            val result = kb.searchCodes(
                query = name, lang = "en",
                categoryFilter = null, maxResults = 5,
            )
            if (result is KbSearchResult.Success) {
                for (hit in result.hits) {
                    val atc = hit.concept.atcCode
                    if (!atc.isNullOrBlank()) {
                        val confidence = computeConfidence(
                            base = 0.55, matchType = "fts5", atcLevel = atc.length,
                        )
                        update(best, atc, hit.concept, name, confidence, "terminology:any")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG_SEARCH, "[t=${System.currentTimeMillis()}]   searchCodes('$name',any) threw : ${e.message}")
        }
    }

    private fun computeConfidence(base: Double, matchType: String, atcLevel: Int): Double {
        val matchBonus = when (matchType) {
            "Exact" -> 0.10
            "Prefix" -> 0.05
            "Contains" -> 0.0
            "fts5" -> -0.05
            else -> -0.10
        }
        val specificityBonus = when {
            atcLevel >= 7 -> 0.05    // specific drug code
            atcLevel == 5 -> 0.0     // pharmacological subgroup
            atcLevel == 4 -> -0.05   // chemical subgroup
            atcLevel == 3 -> -0.10   // therapeutic subgroup
            else -> -0.20            // 1-2 char = anatomical group, too broad
        }
        return (base + matchBonus + specificityBonus).coerceIn(0.0, 1.0)
    }

    private fun update(
        best: MutableMap<String, ScoredHit>,
        atc: String,
        concept: KbConcept,
        matchedTerm: String,
        confidence: Double,
        source: String,
    ) {
        val existing = best[atc]
        if (existing == null || confidence > existing.confidence) {
            best[atc] = ScoredHit(concept, matchedTerm, confidence, source)
        }
    }

    private fun round2(d: Double): Double = (d * 100).toInt() / 100.0

    private data class ScoredHit(
        val concept: KbConcept,
        val matchedTerm: String,
        val confidence: Double,
        val source: String,
    )
}

// ──────────────────────────────────────────────────────────────────────
// Tool 2 — check interactions for the bound victim profile (stateful)
// ──────────────────────────────────────────────────────────────────────

private const val TAG_CHECK = "JEMMA-TOOL-CHECK"

class CheckInteractionsTool(
    private val kb: KnowledgeBaseService,
    private val xcheck: KbCrossCheck,
    private val allergies: List<JAllergy>,
    private val medications: List<JMedication>,
    private val conditions: List<JCondition>,
    private val victimDisplayName: String,
    private val victimLang: String,
) : ToolSet {

    @Tool(description = """
        Check clinical interactions between a drug (identified by ATC code) and
        the victim's stored clinical profile (allergies, current medications,
        known conditions). Returns a structured JSON with :
          - severity_overall : "MAJOR" / "MODERATE" / "NONE"
          - allergy_hits     : matched allergies with reason and criticality
          - ddi_hits         : drug-drug interactions
          - condition_hits   : drug-disease contraindications
          - victim_lang      : the victim's preferred language — write the
                               final verdict text in this language
        Call this AFTER picking the best candidate from searchDrugCandidates.
        Then write a SHORT verdict (2 sentences max) in victim_lang explaining
        whether to administer the drug and why. If severity_overall is MAJOR,
        start with "DO NOT ADMINISTER" / "NE PAS DONNER" / "投与しないでください"
        depending on the language.
    """)
    fun checkInteractions(
        @ToolParam(description = "ATC code of the drug to check (e.g. 'J01CR02', 'B01AA03')")
        atcCode: String,
    ): String {
        val tStart = System.currentTimeMillis()
        val cleanAtc = atcCode.trim().uppercase()
        Log.i(TAG_CHECK, "[t=$tStart] 🚨 checkInteractions · atc=$cleanAtc · victim='$victimDisplayName' · " +
            "al=${allergies.size} md=${medications.size} cn=${conditions.size}")

        if (cleanAtc.isBlank()) {
            return JSONObject().apply { put("error", "atc_code is blank") }.toString()
        }

        val result = runBlocking {
            // Resolve display name via resolveDrug for nicer reporting.
            val resolved = kb.resolveDrug(cleanAtc)
            val concept = resolved.concept
            val displayName = concept?.primaryDisplay ?: cleanAtc

            // Build full ATC chain (primary + ancestors) so class-level
            // allergy matches fire (e.g. J01CR02 matches J01C penicillin).
            val ancestors = try { kb.getAtcAncestors(cleanAtc) } catch (_: Exception) { emptyList() }
            val allAtcs = (listOf(cleanAtc) + ancestors.map { it.atcCode }).distinct()

            val aHits = if (allergies.isNotEmpty()) {
                xcheck.checkOneAtcAgainstAllergies(
                    allergies = allergies,
                    candidateAtc = cleanAtc,
                    candidateAllAtcs = allAtcs,
                    candidateDisplay = displayName,
                    lang = victimLang,
                )
            } else emptyList()

            val dHits = if (medications.isNotEmpty()) {
                xcheck.checkOneAtcAgainstMedications(
                    meds = medications,
                    candidateAtc = cleanAtc,
                    candidateAllAtcs = allAtcs,
                    candidateDisplay = displayName,
                    lang = victimLang,
                )
            } else emptyList()

            val cHits = if (conditions.isNotEmpty()) {
                xcheck.checkOneAtcAgainstConditions(
                    conditions = conditions,
                    candidateAtc = cleanAtc,
                    candidateDisplay = displayName,
                    lang = victimLang,
                )
            } else emptyList()

            CheckPack(aHits, dHits, cHits, displayName, allAtcs)
        }

        val overall = when {
            result.allergyHits.isNotEmpty() -> "MAJOR"
            result.ddiHits.isNotEmpty() || result.conditionHits.isNotEmpty() -> "MODERATE"
            else -> "NONE"
        }

        val out = JSONObject().apply {
            put("drug_atc", cleanAtc)
            put("drug_display", result.displayName)
            put("drug_atc_chain", JSONArray(result.allAtcs))
            put("victim_display_name", victimDisplayName)
            put("victim_lang", victimLang)
            put("victim_pillars_summary",
                "${allergies.size} allergies, ${medications.size} medications, ${conditions.size} conditions")
            put("severity_overall", overall)
            put("allergy_hits", buildHitsArray(result.allergyHits))
            put("ddi_hits", buildHitsArray(result.ddiHits))
            put("condition_hits", buildHitsArray(result.conditionHits))
            put("total_hits",
                result.allergyHits.size + result.ddiHits.size + result.conditionHits.size)
        }.toString()

        val dt = System.currentTimeMillis() - tStart
        Log.i(TAG_CHECK, "[t=${System.currentTimeMillis()}] ✅ checkInteractions done · overall=$overall · " +
            "al=${result.allergyHits.size} ddi=${result.ddiHits.size} cn=${result.conditionHits.size} · ${dt}ms")
        return out
    }

    /**
     * Serialize a list of *Hit objects to JSON by reflecting over their
     * properties. Tolerant to different concrete types from KbCrossCheck.
     */
    private fun buildHitsArray(hits: List<Any>): JSONArray {
        val arr = JSONArray()
        for (hit in hits) {
            val obj = JSONObject()
            try {
                hit::class.members
                    .filter { it.parameters.size == 1 }   // properties = 1 receiver param
                    .forEach { member ->
                        try {
                            val v = member.call(hit)
                            if (v != null) {
                                obj.put(member.name, v.toString())
                            }
                        } catch (_: Exception) {}
                    }
            } catch (_: Exception) {
                obj.put("raw", hit.toString())
            }
            if (obj.length() == 0) obj.put("raw", hit.toString())
            arr.put(obj)
        }
        return arr
    }

    private data class CheckPack(
        val allergyHits: List<Any>,
        val ddiHits: List<Any>,
        val conditionHits: List<Any>,
        val displayName: String,
        val allAtcs: List<String>,
    )
}
