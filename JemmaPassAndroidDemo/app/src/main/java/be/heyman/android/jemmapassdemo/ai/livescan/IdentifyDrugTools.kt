/*
 * IdentifyDrugTools.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 2
 *
 * Phase B ToolSet exposé à Gemma pour identification du drug.
 *
 * Deux tools :
 *   1. lockOnDrug(atc, reason) — Gemma commit sur un ATC unique
 *   2. searchDrugCandidates(names) — fallback FTS5 si Gemma confus
 *
 * Pattern exact aligné sur `MedScanTools.SearchDrugCandidatesTool`
 * (com.google.ai.edge.litertlm @Tool / @ToolParam / ToolSet).
 *
 * ─── Session-scoped state ───
 *
 * `lockedAtc` est capturé via side-effect quand Gemma appelle
 * lockOnDrug. Doit être reset avant chaque identification via
 * resetSession(). NON thread-safe pour identifications concurrentes
 * — OK pour le rescue mode (un scan à la fois).
 *
 * Log channels :
 *   JEMMA-TOOL-IDENTIFY · invocations lockOnDrug
 *   JEMMA-TOOL-SEARCH-ID · invocations searchDrugCandidates en phase B
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import be.heyman.android.jemmapassdemo.kb.KbSearchResult
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private const val TAG_ID = "JEMMA-TOOL-IDENTIFY"
private const val TAG_SEARCH = "JEMMA-TOOL-SEARCH-ID"

/**
 * Container Hilt qui détient la session state pour Phase B.
 * Le ToolSet réel est créé par `buildToolSet()` au démarrage de chaque
 * identification (pour récupérer une référence stable à `this`).
 */
@Singleton
class IdentifyDrugTools @Inject constructor(
    private val kb: KnowledgeBaseService,
) {
    @Volatile
    var lockedAtc: String? = null
        private set

    @Volatile
    var lockedReason: String? = null
        private set

    /** Reset l'état session avant un nouveau Phase B. */
    fun resetSession() {
        lockedAtc = null
        lockedReason = null
    }

    /**
     * Crée le ToolSet à passer à Gemma. Pattern emprunté à
     * `MedScanTools.SearchDrugCandidatesTool` — un wrapper qui capture
     * `this` dans une closure pour que les @Tool methods voient le state.
     */
    fun buildToolSet(): IdentifyDrugToolSet = IdentifyDrugToolSet(this, kb)
}

/**
 * ToolSet effectif exposé à Gemma. Une instance par identification.
 */
class IdentifyDrugToolSet(
    private val parent: IdentifyDrugTools,
    private val kb: KnowledgeBaseService,
) : ToolSet {

    @Tool(description = """
        Lock in your final identification of the medication. You MUST call this
        exactly once at the end of your reasoning, with the chosen ATC code
        and a short reason explaining why this candidate matches the image best.
        After calling this tool, stop and do not produce additional free text.

        If you genuinely cannot identify the medication from the image and OCR,
        call lockOnDrug with atc="UNKNOWN" and a brief reason.
    """)
    fun lockOnDrug(
        @ToolParam(description = "The ATC L5 code chosen (e.g. 'J01CR02', 'B01AC06'), or 'UNKNOWN' if you cannot identify.")
        atc: String,
        @ToolParam(description = "Brief reason (10-30 words) why this candidate matches the image.")
        reason: String,
    ): String {
        val cleanAtc = atc.trim().uppercase()
        val isValid = cleanAtc == "UNKNOWN" || cleanAtc.matches(Regex("[A-Z0-9]{2,7}"))
        if (!isValid) {
            Log.w(TAG_ID, "[t=${System.currentTimeMillis()}] ⚠️ invalid atc='$atc' from Gemma — rejecting")
            return """{"status":"invalid_atc","received":"$atc"}"""
        }
        // Side-effect : capture dans le parent
        // (Workaround Kotlin/Java reflection — accès direct car package-same)
        IdentifyDrugToolsInternal.captureLock(parent, cleanAtc, reason)
        Log.i(TAG_ID, "[t=${System.currentTimeMillis()}] 🔒 lockOnDrug('$cleanAtc') · " +
            "reason='${reason.take(80)}'")
        return """{"status":"locked","atc":"$cleanAtc"}"""
    }

    @Tool(description = """
        Fallback search of the offline clinical KB. Use this ONLY if the
        pre-resolved candidates in the prompt don't fit what you see in
        the image. Pass 1-5 name candidates (brand, INN, ATC). Returns a
        JSON array of {atc, name, source}. After receiving results, call
        lockOnDrug with your final choice.
    """)
    fun searchDrugCandidates(
        @ToolParam(description = "1-5 candidate drug names to search (brand, INN, or ATC).")
        names: List<String>,
    ): String {
        val tStart = System.currentTimeMillis()
        Log.i(TAG_SEARCH, "[t=$tStart] 🔍 fallback search · ${names.size} names · " +
            names.take(5).joinToString("|"))

        val arr = JSONArray()
        val seen = mutableSetOf<String>()

        // Bridging suspend→blocking : on est sur le thread inference LiteRT-LM
        // (pas UI), runBlocking est safe ici.
        runBlocking {
            for (name in names.take(5)) {
                val r1 = try { kb.searchCodes(name, "en", "Medication", 3) }
                catch (e: Exception) {
                    Log.w(TAG_SEARCH, "[t=${System.currentTimeMillis()}] ⚠️ pass1 threw on '$name': ${e.message}")
                    continue
                }
                val r2 = if (r1 is KbSearchResult.Success && r1.hits.isEmpty()) {
                    try { kb.searchCodes(name, "en", null, 3) } catch (e: Exception) { r1 }
                } else r1

                val hits = (r2 as? KbSearchResult.Success)?.hits.orEmpty()
                for (hit in hits) {
                    val atc = hit.concept.atcCode ?: continue
                    if (atc in seen) continue
                    seen.add(atc)
                    arr.put(JSONObject().apply {
                        put("atc", atc)
                        put("name", hit.concept.primaryDisplay)
                        put("source", hit.matchedVia)
                    })
                }
            }
        }

        Log.i(TAG_SEARCH, "[t=${System.currentTimeMillis()}] ✅ fallback done · " +
            "${arr.length()} unique atcs · ${System.currentTimeMillis() - tStart}ms")
        return arr.toString()
    }
}

/**
 * Helper interne pour permettre à `IdentifyDrugToolSet` de muter
 * `parent.lockedAtc` (qui a `private set`). Pattern emprunté pour ne
 * pas exposer le setter publiquement.
 */
internal object IdentifyDrugToolsInternal {
    fun captureLock(parent: IdentifyDrugTools, atc: String, reason: String) {
        // Reflection sur le backing field
        val atcField = IdentifyDrugTools::class.java.getDeclaredField("lockedAtc").apply { isAccessible = true }
        val reasonField = IdentifyDrugTools::class.java.getDeclaredField("lockedReason").apply { isAccessible = true }
        atcField.set(parent, atc)
        reasonField.set(parent, reason)
    }
}
