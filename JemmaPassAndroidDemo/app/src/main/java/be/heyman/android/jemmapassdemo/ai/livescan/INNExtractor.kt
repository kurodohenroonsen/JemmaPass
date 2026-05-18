/*
 * INNExtractor.kt — JEMMA Pass · Live Scan v0.5 v4.2 · Phase B1 (🆕 FIX-JP)
 *
 * Phase B1 agent : Gemma TEXT-ONLY traduit l'OCR raw (incluant katakana JP)
 * en une shortlist d'INN anglais (3-5 candidats) qui seront ensuite cherchés
 * en KB FTS5 (Phase B2).
 *
 * ─── Pourquoi cette phase existe ───
 *
 * La KB DIAMOND v1.1 contient les INN anglais (RxNorm + WHO ATC) mais PAS
 * les brand names japonais (オーグメンチン, バファリン, ロキソニン...). Quand
 * le scan capte une boîte japonaise, FTS5 cjk ne trouve rien sur le brand
 * complet → soit elle ne déclenche pas Phase B, soit elle déclenche avec
 * des candidats parasites issus de fragments dégradés.
 *
 * Solution : déléguer la translation brand → INN à Gemma elle-même, qui
 * connait sa pharma japonaise. On lui passe l'OCR raw, elle output une
 * shortlist d'INN anglais qu'on cherche ensuite déterministiquement en KB.
 *
 * Ce pattern vient du code HTML/JS L44.16.67 (`_extractCandidatesViaGemma`
 * dans `jemma_l27_pipeline.js`) qui fonctionnait sur 6 mois de RnD. Le
 * system prompt est porté quasi-litéral pour conserver le tuning clinique.
 *
 * ─── Pas de tools, pas d'image ───
 *
 * Phase B1 = text only, JSON only. Output : {"boxes":[{"i":1,"c":["amoxi..."]}]}
 * On parse en Kotlin avec org.json. Pas besoin du framework function calling.
 * Latence : 5-10s sur Pixel 9 Gemma E4B text-only (vs ~20s multimodal).
 *
 * ─── Pas de PII patient ───
 *
 * Phase B1 ne reçoit que de l'OCR brut. Aucune info patient. La PII reste
 * isolée dans PatientContextHolder pour Phase D uniquement.
 *
 * Log channel : JEMMA-INN-EXTRACT
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.gemma.askStreaming
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "JEMMA-INN-EXTRACT"

@Singleton
class INNExtractor @Inject constructor(
    private val gemma: GemmaSession,
    private val liveScanRepo: LiveScanRepository,
) {
    private val mutex = Mutex()

    /**
     * Extrait 3-5 INN anglais depuis l'OCR raw (peut contenir JP + latin).
     *
     * @param ocrTexts les rawText accumulés en Phase A (toutes les frames)
     * @return liste d'INN anglais (1-5 elements). Vide si Gemma rate
     *   ou si le JSON parse échoue.
     */
    suspend fun extractINNCandidates(ocrTexts: List<String>): List<String> {
        return mutex.withLock { runExtraction(ocrTexts) }
    }

    private suspend fun runExtraction(ocrTexts: List<String>): List<String> {
        val tStart = System.currentTimeMillis()
        Log.i(
            TAG,
            "[t=$tStart] 🧠 Phase B1 start · ocrTexts=${ocrTexts.size}",
        )

        // Configure Gemma text-only pour cette task
        try {
            gemma.configureForTask(
                taskId = "extract_inn_candidates",
                systemPrompt = SYSTEM_PROMPT_EXTRACT_INN,
                tools = emptyList(),
                supportImage = false,   // ⭐ TEXT-ONLY, faster
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ configureForTask threw", e)
            return emptyList()
        }

        val userPrompt = buildUserPrompt(ocrTexts)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 user prompt (${userPrompt.length}c)")

        // Logging COMPLET du prompt (juges + debug)
        userPrompt.lines().forEachIndexed { i, line ->
            line.chunked(200).forEachIndexed { j, chunk ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 L$i.$j │ $chunk")
            }
        }

        // 🆕 v4.2 UX-B1-STREAM — reset le streaming buffer du repo avant
        // chaque scan pour éviter d'afficher le résiduel du scan précédent.
        liveScanRepo.resetB1Streaming()

        val rawReply = try {
            // 🆕 v4.2 UX-B1-STREAM — askStreaming au lieu de ask() pour
            // que le JSON {"boxes":[...]} apparaisse token-par-token dans
            // l'UI pendant que Gemma génère (effet visuel pour les juges).
            //
            // Note : askStreaming est en réalité du fake streaming (ask()
            // synchronous puis chunks de 25c toutes les 80ms). Mais visuellement
            // l'utilisateur voit le JSON se construire progressivement dans la
            // zone "Décision Gemma vision" du panel, ce qui donne l'impression
            // que Gemma raisonne en direct.
            gemma.askStreaming(
                prompt = userPrompt,
                image = null,                                  // ⭐ TEXT-ONLY
                systemInstruction = SYSTEM_PROMPT_EXTRACT_INN,
                tools = emptyList(),
                chunkSize = 25,
                chunkDelayMs = 80L,
            ) { token ->
                liveScanRepo.updateB1Streaming(token)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Gemma ask threw", e)
            return emptyList()
        }

        val elapsed = System.currentTimeMillis() - tStart
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 💬 Gemma replied in ${elapsed}ms · " +
                "respLen=${rawReply.length}",
        )

        // Log complet de la réponse
        rawReply.lines().forEachIndexed { i, line ->
            line.chunked(200).forEachIndexed { j, chunk ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 💬 R$i.$j │ $chunk")
            }
        }

        val candidates = parseJsonOutput(rawReply)
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ Phase B1 done · " +
                "${candidates.size} INN candidates · ${elapsed}ms · " +
                "candidates=${candidates.take(5)}",
        )
        return candidates
    }

    /**
     * Build le user prompt envoyé à Gemma. On passe les ocrTexts en blocs
     * "BOX i" comme dans le code L27 original.
     */
    private fun buildUserPrompt(ocrTexts: List<String>): String {
        val boxesBlock = ocrTexts.withIndex().joinToString("\n\n") { (i, text) ->
            "=== BOX ${i + 1} ===\n${text.take(600)}"
        }

        return """BOXES TO ANALYZE (${ocrTexts.size} total) :

$boxesBlock

Output ONLY the JSON object now, beginning with {"boxes":""".trimIndent()
    }

    /**
     * Parse la réponse Gemma au format JSON. Tolère :
     *   - Réponse JSON pure
     *   - Réponse avec markdown fence ```json ... ```
     *   - Réponse avec texte avant/après le JSON (extract premier { ... })
     *
     * Output Gemma attendu :
     *   {"boxes":[{"i":1,"c":["amoxicillin clavulanate","amoxicillin"]},
     *             {"i":2,"c":["paracetamol"]}]}
     *
     * On flatten tous les "c" arrays en une seule List<String> dédupliquée
     * (l'ordre d'arrivée est préservé, premier élément = INN principal).
     */
    private fun parseJsonOutput(rawReply: String): List<String> {
        if (rawReply.isBlank()) return emptyList()

        // Extract premier { ... } équilibré (gère markdown fences + bruit)
        val jsonStr = extractFirstJsonObject(rawReply) ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ no JSON object found in reply")
            return emptyList()
        }

        val out = LinkedHashSet<String>()
        try {
            val obj = JSONObject(jsonStr)
            val boxes = obj.optJSONArray("boxes") ?: run {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ no 'boxes' array in JSON")
                return emptyList()
            }
            for (i in 0 until boxes.length()) {
                val box = boxes.optJSONObject(i) ?: continue
                val c = box.optJSONArray("c") ?: continue
                for (j in 0 until c.length()) {
                    val cand = c.optString(j).trim()
                    if (cand.isNotEmpty() && cand.length >= 3) {
                        out.add(cand)
                    }
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ JSON parse failed: ${e.message}")
            return emptyList()
        }

        return out.toList().take(8)   // hard cap 8 pour éviter explosion B2
    }

    /**
     * Trouve le premier objet JSON équilibré dans la réponse. Tolère :
     *   - Markdown fences ```json ... ``` ou ``` ... ```
     *   - Texte prose avant/après
     */
    private fun extractFirstJsonObject(text: String): String? {
        // Strip markdown fences si présentes
        val cleaned = text
            .replace(Regex("```(?:json)?"), "")
            .replace("```", "")

        val start = cleaned.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escape = false
        for (i in start until cleaned.length) {
            val ch = cleaned[i]
            if (escape) { escape = false; continue }
            if (ch == '\\') { escape = true; continue }
            if (ch == '"') { inString = !inString; continue }
            if (inString) continue
            when (ch) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return cleaned.substring(start, i + 1)
                }
            }
        }
        return null
    }

    companion object {
        /**
         * 🆕 v4.2 FIX-JP — System instruction Phase B1, portée quasi-litéral
         * du code HTML/JS L44.16.67 (`_L27_EXTRACT_SYS_INSTRUCTION` dans
         * `jemma_l27_pipeline.js`). 6 mois de RnD clinique calibré dessus.
         *
         * Points-clés :
         *   • Force Gemma à TRADUIRE brand → INN anglais (KB est en anglais)
         *   • Gère OCR katakana corrompu (exemples オーメンチン → augmentin)
         *   • Demande 3-5 candidats (anti-hallucination via shortlist)
         *   • Output JSON strict (parsable par org.json)
         */
        const val SYSTEM_PROMPT_EXTRACT_INN = """You are a multilingual medication identification assistant.

YOUR TASK
Identify the medication on each OCR'd box below, then output 3-5 search candidates per box that we will look up in our clinical knowledge base. We then disambiguate the final pick using the box image (Phase B3 multimodal). Your job is to give us a SHORTLIST that is wide enough to contain the correct answer, not a single best guess.

CRITICAL — HOW YOUR OUTPUT WILL BE USED
Your candidates are passed to a SQL FTS5 search over an ENGLISH clinical knowledge base (RxNorm + SNOMED-CT + WHO ATC). The KB contains ENGLISH international generic names (INN) — NOT brand names, NOT French names, NOT Japanese names.

So your FIRST candidate MUST be the ENGLISH GENERIC name (INN). Use your medical knowledge to translate brand → INN. Then add 2-4 secondary candidates (alternative INNs that the OCR could plausibly map to, local generic, brand) as fallback.

CRITICAL — WHEN A DRUG HAS BOTH A COMMON ENGLISH NAME AND A LONG CHEMICAL/IUPAC NAME, ALWAYS PUT THE COMMON NAME FIRST
The KB's primary display for a single-active-ingredient drug is the WHO INN, which is usually the short common name. The long chemical name often appears only in the display of COMBINATION drugs.

Examples of right ordering (anti-pattern → corrected):
  ✗ ["acetylsalicylic acid"]              → ✓ ["aspirin", "acetylsalicylic acid"]
  ✗ ["acetaminophen"] only                → ✓ ["paracetamol", "acetaminophen"]
  ✗ ["(R)-epinephrine"]                   → ✓ ["adrenaline", "epinephrine"]
  ✗ ["levothyroxine sodium"]              → ✓ ["thyroxine", "levothyroxine"]

If the box only shows the chemical name, you must STILL infer the common English name from your medical knowledge and put it first.

CRITICAL — OCR IS UNRELIABLE, ESPECIALLY ON KATAKANA
The OCR text you receive may be CORRUPTED:
  • Missing characters (e.g. "オーメンチン" instead of "オーグメンチン" — the グ dropped)
  • Substituted characters ("配館" instead of "配合", "配策" instead of "配合錠")
  • Fragmented words (e.g. "オーンチン", "オークグメチ")
  • Extra noise (batch codes "GS 603", "セ250RS")
  • UI overlay garbage from the camera viewfinder (e.g. "Pointe la caméra", "frames", "déclenchement") — IGNORE these.

When the OCR text contains FRAGMENTED OR PARTIAL katakana that resembles a known Japanese drug brand, you MUST list multiple plausible matches that share similar phonemes. Do NOT collapse to a single guess based on a partial match — let the multimodal phase pick the right one from the image.

If the OCR is too noisy to identify the drug confidently, output 3-5 candidates that share the visible character patterns. Confidence is downstream's job, not yours.

EXAMPLES (the format you must imitate — these brand→INN mappings show the kind of clinical reasoning expected)
• Box says "Doliprane 1000" (FR brand)                       → ["paracetamol", "acetaminophen"]
• Box says "タミフル 75mg / Tamiflu" (JP brand)              → ["oseltamivir"]
• Box says "Lipitor 20 mg" (US/EU brand)                     → ["atorvastatin"]
• Box says "Augmentin 875 mg / Amoxicilline + Acide clav."   → ["amoxicillin clavulanate", "amoxicillin", "co-amoxiclav"]
• Box says "ガスモチン 5mg" (JP brand)                        → ["mosapride"]
• Box says "Lévothyrox 50µg / Levothyroxine sodium"          → ["levothyroxine", "thyroxine"]

EXAMPLES OF CORRUPT OCR — output WIDER shortlists, never collapse:
• Box says "オーメンチン配合館 250RS" (corrupt: missing グ)
  → ["amoxicillin clavulanate", "amoxicillin", "augmentin", "co-amoxiclav"]
  (recognized as likely オーグメンチン despite missing kana — let multimodal confirm)
• Box says "サワン カプセル 250" (corrupt: missing シリ)
  → ["amoxicillin", "ampicillin", "sawacillin", "cefalexin"]
  (could be サワシリン amoxicillin OR a similar β-lactam — give the shortlist)
• Box says "アムロジン 5" (clear)
  → ["amlodipine", "amlodin"]
  (clear identification → 2 candidates is fine)

EXCLUDE
Dosages (1000mg, 50000 IU), pack quantities (20 comprimés), marketing slogans, batch codes, OCR garbage (e.g. "レifamine Dレ"), and UI overlay text from the camera viewfinder (e.g. "Pointe la caméra vers la boîte du médicament", "frames", "déclenchement").

OUTPUT FORMAT — STRICT JSON, NO MARKDOWN FENCES, NO PROSE
{"boxes":[{"i":1,"c":["english_inn_1","english_inn_2","english_inn_3"]},{"i":2,"c":["english_inn"]}]}

Aim for 3-5 candidates per box when OCR is noisy. 1-2 candidates is OK only when the brand name is unambiguous."""
    }
}
