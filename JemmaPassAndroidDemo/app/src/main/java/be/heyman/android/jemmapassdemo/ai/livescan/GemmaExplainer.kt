/*
 * GemmaExplainer.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 5
 *
 * Phase D agent : après Phase C, Gemma streame une explication
 * naturelle dans la langue du patient. Chaîne 2 tools :
 *   1. getInteractionsForCurrentPatient(atc) — lecture du rapport
 *   2. findSafeAlternativeForCurrentPatient(atc, reason) — si Major
 *
 * Streaming via `askStreaming` extension (fallback chunks-buffer).
 *
 * ─── Latence ───
 *   • 5-15s total streaming sur Pixel 9
 *   • Visuellement perçu comme "Gemma réfléchit"
 *
 * ─── Aucune PII ───
 *   • Patient lu via PatientContextHolder en backend des tools
 *   • Prompt LLM ne mentionne jamais le patient nommément
 *
 * Log channel : JEMMA-EXPLAIN
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.util.Log
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.gemma.askStreaming
import com.google.ai.edge.litertlm.tool
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-EXPLAIN"

@Singleton
class GemmaExplainer @Inject constructor(
    private val gemma: GemmaSession,
    private val explainTools: ExplainTools,
    private val liveScanRepo: LiveScanRepository,
) {
    suspend fun explain(bestCode: String, drugName: String, lang: String) {
        val tStart = System.currentTimeMillis()
        Log.i(TAG, "[t=$tStart] 🧠 Phase D start · atc=$bestCode ($drugName) lang=$lang")

        explainTools.resetSession()
        val toolSet = explainTools.buildToolSet()

        try {
            gemma.configureForTask(
                taskId = "explain_verdict",
                systemPrompt = systemPromptFor(lang),
                tools = listOf(tool(toolSet)),
                supportImage = false,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ configureForTask threw", e)
            liveScanRepo.markExplanationDone(safeAlt = null)
            return
        }

        val userPrompt = """
            Drug just identified: $drugName ($bestCode).
            Output language: $lang.
            Stream your explanation now using the tools provided.
        """.trimIndent()

        // 🆕 FIX7 — Log COMPLET du system + user prompt Phase D
        val systemPrompt = systemPromptFor(lang)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 ─── FULL SYSTEM PROMPT Phase D (${systemPrompt.length}c) ───")
        systemPrompt.lines().forEachIndexed { i, line ->
            line.chunked(200).forEachIndexed { j, chunk ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 SYS$i.$j │ $chunk")
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 ─── FULL USER PROMPT Phase D (${userPrompt.length}c) ───")
        userPrompt.lines().forEachIndexed { i, line ->
            line.chunked(200).forEachIndexed { j, chunk ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 USR$i.$j │ $chunk")
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📝 ─── end prompts Phase D ───")

        // Buffer pour log COMPLET de la réponse streamée
        val streamedBuilder = StringBuilder()

        try {
            gemma.askStreaming(
                prompt = userPrompt,
                systemInstruction = systemPrompt,
                tools = listOf(tool(toolSet)),
                image = null,
                chunkSize = 25,
                chunkDelayMs = 80L,
            ) { token ->
                streamedBuilder.append(token)
                liveScanRepo.updateStreamingExplanation(token)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ askStreaming threw", e)
            liveScanRepo.markExplanationDone(safeAlt = explainTools.lastSafeAlt)
            return
        }

        // 🆕 FIX7 — Log COMPLET de la réponse stream finale
        val finalText = streamedBuilder.toString()
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💬 ─── FULL STREAMED RESPONSE Phase D (${finalText.length}c) ───")
        finalText.lines().forEachIndexed { i, line ->
            line.chunked(200).forEachIndexed { j, chunk ->
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 💬 STR$i.$j │ $chunk")
            }
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 💬 ─── end stream Phase D ───")

        val elapsed = System.currentTimeMillis() - tStart
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ Phase D done · ${elapsed}ms · " +
                "safeAlt=${explainTools.lastSafeAlt?.atc ?: "none"}",
        )

        liveScanRepo.markExplanationDone(safeAlt = explainTools.lastSafeAlt)
    }

    private fun systemPromptFor(lang: String): String = """You are a clinical educator. The rescuer just got a verdict about a medication. Explain WHY in clear simple language, in $lang.

You have these tools :
1. getInteractionsForCurrentPatient(atc) — call FIRST to get the clinical report.
2. findSafeAlternativeForCurrentPatient(originalAtc, reason) — call IF overall == "MAJOR".

Workflow :
1. Call getInteractionsForCurrentPatient with the drug ATC.
2. Read the JSON. Identify the worst hit and its severity.
3. If overall == "MAJOR" : call findSafeAlternativeForCurrentPatient with reason from the worst hit type ("allergy" / "ddi" / "condition").
4. Stream a 3-5 sentence explanation in $lang :
   - Name the issue (mechanism + risk in plain words).
   - If alternative found : recommend it by name.
   - If no alternative : recommend consulting a physician.

Rules :
- Never invent facts. Only use data from tools.
- NEVER name the patient or mention personal details.
- Tone : calm, educational, concise.
- Output : prose only, no JSON, no markdown."""
}
