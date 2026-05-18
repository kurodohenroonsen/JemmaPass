/*
 * AssistantMedicationsExtractor.kt — Lot 14.5c1 (PHASE 14)
 *
 * Hotfix sur le Lot 14.5c suite test Kudoro :
 *   • Gemma retournait JSON tronqué (`{"boxes":[{"i":1,"c":[...]` sans
 *     fermeture) → parser fail. Maintenant on auto-complete les `]}`
 *     manquants pour récupérer un maximum d'items.
 *   • Pas de feedback UI pendant les 13s d'inférence. Maintenant on
 *     stream chaque token via `gemmaSession.ask(onPartial = ...)` et
 *     on update le step_body en live.
 */
package be.heyman.android.jemmapassdemo.ai.assistant

import android.util.Log
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.ai.gemma.GemmaSession
import be.heyman.android.jemmapassdemo.ai.ocr.StructuredOcrResult
import be.heyman.android.jemmapassdemo.ai.tts.JemmaTtsService
import be.heyman.android.jemmapassdemo.kb.KnowledgeBaseService
import be.heyman.android.jemmapassdemo.kb.ResolvedConcept
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "JEMMA-ASSISTANT-EXTRACT"

@Singleton
class AssistantMedicationsExtractor @Inject constructor(
    private val gemmaSession: GemmaSession,
    private val kb: KnowledgeBaseService,
    private val tts: JemmaTtsService,
) {

    /**
     * Le pipeline complet : OCR → Gemma extract → KB resolve.
     *
     * 🆕 Lot 14.5c1 — Ajoute le callback `onPartial` qui reçoit chaque
     * token incremental généré par Gemma (pour update UI live).
     *
     * @param ocr résultat OCR du Lot 14.5b
     * @param onStep callback progression UI — args : stepNum, state, body
     * @param onPartial callback streaming Gemma — args : tokenChunk (incremental)
     */
    suspend fun extract(
        ocr: StructuredOcrResult,
        imageUris: List<android.net.Uri>,
        context: android.content.Context,
        onStep: (Int, String, String?) -> Unit,
        onPartial: ((String) -> Unit)? = null,
    ): List<ExtractedMedication> {
        val lang = context.resources.configuration.locales[0].language
        // ── Step 2 : Gemma extract ──────────────────────────────────────
        onStep(2, "running", null)
        val tGemmaStart = System.currentTimeMillis()
        val serialized = ocr.serializeForLlm()

        Log.i(
            TAG,
            "[t=$tGemmaStart] 🤖 Gemma extract · serializedLen=${serialized.length} · pages=${ocr.pageCount}",
        )

        val gemmaReply: String = try {
            gemmaSession.ask(
                prompt = "BOXES TO ANALYZE :\n\n$serialized\n\n" +
                     "Output ONLY the JSON object now, beginning with {\"boxes\":",
                systemInstruction = L27_EXTRACT_SYS_INSTRUCTION,
                image = null,
                tools = emptyList(),
                onPartial = onPartial,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Gemma ask threw", e)
            onStep(2, "error", e.message ?: e::class.java.simpleName)
            return emptyList()
        }

        val dtGemma = System.currentTimeMillis() - tGemmaStart
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] 📥 Gemma reply · ${dtGemma}ms · " +
                "replyLen=${gemmaReply.length}",
        )
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 📥 Gemma reply full :\n$gemmaReply")

        val parsedBoxes = parseGemmaBoxes(gemmaReply)
        if (parsedBoxes == null) {
            Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ Gemma reply unusable: JSON parsing failed completely. Raw reply:\n$gemmaReply")
            onStep(2, "error", "Gemma JSON unparseable")
            return emptyList()
        }
        if (parsedBoxes.isEmpty()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ Gemma parsed successfully but found 0 candidate drugs across all pages. Raw reply:\n$gemmaReply")
        }

        val totalCandidates = parsedBoxes.values.sumOf { it.size }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ Gemma extract OK · boxes=${parsedBoxes.size} · " +
                "candidates=$totalCandidates",
        )
        val step2Msg = when (lang) {
            "fr" -> "${parsedBoxes.size} blocs · $totalCandidates candidats INN · ${dtGemma}ms"
            "ja" -> "${parsedBoxes.size} ブロック · $totalCandidates INN候補 · ${dtGemma}ms"
            else -> "${parsedBoxes.size} blocks · $totalCandidates INN candidates · ${dtGemma}ms"
        }
        onStep(2, "done", step2Msg)

        // ── Step 3 : KB resolve + dose hints ───────────────────────────
        onStep(3, "running", null)
        val tKbStart = System.currentTimeMillis()

        val extracted = mutableListOf<ExtractedMedication>()
        // 🔥 Lot 14.5c3 — 1 entry par PAGE (pas seulement les pages résolues).
        // Si Gemma a renvoyé c=[] pour une page, on crée quand même une entry
        // avec gemmaSkipped=true pour que l'user la voie dans le preview.
        for ((pageIdx0, page) in ocr.pages.withIndex()) {
            val gemmaBoxKey = pageIdx0 + 1
            val candidates = parsedBoxes[gemmaBoxKey] ?: emptyList()
            val ocrSnippet = page.blocks.take(3)
                .joinToString(" · ") { it.text.replace("\n", " ").take(40) }

            if (candidates.isEmpty()) {
                // Page existant mais rien identifié → row "❌ Non identifié"
                Log.i(
                    TAG,
                    "[t=${System.currentTimeMillis()}]   page ${page.pageIndex} (box $gemmaBoxKey) · " +
                        "Gemma found nothing → marked as skipped",
                )
                extracted += ExtractedMedication(
                    pageIndex = page.pageIndex,
                    ocrSnippet = ocrSnippet,
                    gemmaCandidates = emptyList(),
                    topCandidate = null,
                    kbResolution = ResolvedConcept.NotFound,
                    doseHints = emptyList(),
                    gemmaSkipped = true,
                    selected = false,  // pas coché par défaut → l'user décide
                    imageUri = if (page.pageIndex in imageUris.indices) imageUris[page.pageIndex].toString() else null
                )
                continue
            }

            val top = candidates.first().trim().ifBlank { null }
            val resolution: ResolvedConcept = if (top != null) {
                kb.resolveDrug(top)
            } else {
                ResolvedConcept.NotFound
            }

            // 🆕 Lot 14.5c3 — récupère les doses standards depuis la KB
            // (e.g. R06AX29 bilastine → ["20 mg"], N02BE01 paracetamol →
            // ["1000 mg", "500 mg", "300 mg"]).
            val atc = when (resolution) {
                is ResolvedConcept.Exact -> resolution.value.atcCode
                is ResolvedConcept.Prefix -> resolution.value.atcCode
                is ResolvedConcept.Contains -> resolution.value.atcCode
                ResolvedConcept.NotFound -> null
            }
            val doses = if (atc != null) kb.fetchDoseHints(atc) else emptyList()

            val displayInfo = when (resolution) {
                is ResolvedConcept.Exact -> "EXACT atc=${resolution.value.atcCode} '${resolution.value.primaryDisplay}'"
                is ResolvedConcept.Prefix -> "PREFIX atc=${resolution.value.atcCode} '${resolution.value.primaryDisplay}'"
                is ResolvedConcept.Contains -> "CONTAINS atc=${resolution.value.atcCode} '${resolution.value.primaryDisplay}'"
                ResolvedConcept.NotFound -> "NotFound"
            }
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}]   box $gemmaBoxKey · '$top' → $displayInfo · " +
                    "doses=${doses.take(3)} · (other candidates: ${candidates.drop(1)})",
            )

            extracted += ExtractedMedication(
                pageIndex = page.pageIndex,
                ocrSnippet = ocrSnippet,
                gemmaCandidates = candidates,
                topCandidate = top,
                kbResolution = resolution,
                doseHints = doses,
                gemmaSkipped = false,
                selected = resolution !is ResolvedConcept.NotFound,
                imageUri = if (page.pageIndex in imageUris.indices) imageUris[page.pageIndex].toString() else null
            )
        }

        val dtKb = System.currentTimeMillis() - tKbStart
        val resolvedCount = extracted.count { it.isResolved }
        val withDoseCount = extracted.count { it.doseHints.isNotEmpty() }
        val skippedCount = extracted.count { it.gemmaSkipped }
        Log.i(
            TAG,
            "[t=${System.currentTimeMillis()}] ✅ KB resolve OK · items=${extracted.size} · " +
                "resolved=$resolvedCount · skipped=$skippedCount · withDose=$withDoseCount · ${dtKb}ms",
        )
        val step3Msg = when (lang) {
            "fr" -> "$resolvedCount concepts trouvés · ${dtKb}ms"
            "ja" -> "$resolvedCount 概念を検出 · ${dtKb}ms"
            else -> "$resolvedCount concepts found · ${dtKb}ms"
        }
        onStep(3, "done", step3Msg)

        // ── Step 4 : Gemma Vision Validation / Disambiguation ──────────
        onStep(4, "running", null)
        val finalExtracted = mutableListOf<ExtractedMedication>()
        val reasonAccumulatorGlobal = java.lang.StringBuilder()

        if (imageUris.isNotEmpty()) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] Starting visual verification for ${extracted.size} items")
            val runningMsg = when (lang) {
                "fr" -> "Vérification visuelle par Gemma…"
                "ja" -> "Gemmaによる画像検証中…"
                else -> "Visual verification by Gemma…"
            }
            onStep(4, "running", runningMsg)

            for ((idx, item) in extracted.withIndex()) {
                if (item.gemmaSkipped || item.gemmaCandidates.isEmpty() || item.pageIndex < 0 || item.pageIndex >= imageUris.size) {
                    finalExtracted.add(item)
                    continue
                }

                val uri = imageUris[item.pageIndex]
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] [Page ${item.pageIndex+1}/${extracted.size}] Loading image: $uri")
                val bitmap = loadScaledBitmap(context, uri)
                if (bitmap == null) {
                    Log.w(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] [Page ${item.pageIndex+1}] Could not load bitmap, skipping Vision")
                    finalExtracted.add(item)
                    continue
                }

                val candidatesList = item.gemmaCandidates
                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] [Page ${idx+1}] Candidates to verify: $candidatesList")

                val step4RunningMsg = when (lang) {
                    "fr" -> "Vérification visuelle (${idx+1}/${extracted.size})…"
                    "ja" -> "画像検証中 (${idx+1}/${extracted.size})…"
                    else -> "Visual verification (${idx+1}/${extracted.size})…"
                }
                onStep(4, "running", step4RunningMsg)

                val locale = context.resources.configuration.locales[0] ?: java.util.Locale.getDefault()
                val langName = when (locale.language) {
                    "fr" -> "French (français)"
                    "ja" -> "Japanese (日本語)"
                    "nl" -> "Dutch (Nederlands)"
                    else -> "English"
                }

                val candidatesFormatted = candidatesList.mapIndexed { cIdx, name -> "${cIdx + 1}) $name" }.joinToString("\n")
                val visionPrompt = """
                You are a clinical verification auditor checking a scanned medication packaging.
                Here is the list of candidate drug concepts identified by the OCR and knowledge base:
                $candidatesFormatted
                
                Please carefully inspect the provided photo of the medication box. 
                Which of these candidates matches the packaging exactly in terms of name, active substance, and dosage?
                
                Respond strictly in JSON format as follows:
                {
                  "selected_index": [The number of the matching candidate, 1-based, or -1 if no candidate matches],
                  "reason": "precise visual description of why this matches or does not match"
                }
                
                CRITICAL: You must write the "reason" explanation in $langName, so that the patient can hear it clearly spoken in their language.
                """.trimIndent()

                Log.i(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] [Page ${idx+1}] Gemma Vision Prompt:\n$visionPrompt")

                // Start live streaming TTS session
                tts.init()
                val ttsSession = tts.startStream(locale.toLanguageTag())
                val reasonExtractor = StreamingReasonExtractor()
                val reasonAccumulatorLocal = java.lang.StringBuilder()

                try {
                    val reply = gemmaSession.ask(
                        prompt = visionPrompt,
                        image = bitmap,
                        systemInstruction = "You are a precise clinical auditor. Only output JSON.",
                        tools = emptyList(),
                        onPartial = { chunk ->
                            val newText = reasonExtractor.feed(chunk)
                            if (!newText.isNullOrEmpty()) {
                                val cleanText = newText
                                    .replace("\\n", " ")
                                    .replace("\\\"", "\"")
                                    .replace("\\'", "'")
                                
                                reasonAccumulatorLocal.append(cleanText)
                                ttsSession.feed(cleanText)
                                
                                // Live UI typewriter update under the clinical step 4 body
                                val reasonSoFar = reasonAccumulatorLocal.toString().trim()
                                val labelPage = when (locale.language) {
                                    "fr" -> "Page"
                                    "ja" -> "ページ"
                                    "nl" -> "Pagina"
                                    else -> "Page"
                                }
                                val fullText = buildString {
                                    if (reasonAccumulatorGlobal.isNotEmpty()) {
                                        append(reasonAccumulatorGlobal.toString())
                                        append("\n\n")
                                    }
                                    append("• [$labelPage ${idx+1}/${extracted.size}] ${item.topCandidate ?: ""}: \"$reasonSoFar\"")
                                }
                                onStep(4, "running", fullText)
                            }
                        }
                    )

                    // Stop stream and speak the remaining tail
                    val isLast = idx == extracted.size - 1
                    val finalReasonLocal = reasonAccumulatorLocal.toString().trim()
                    val labelPage = when (locale.language) {
                        "fr" -> "Page"
                        "ja" -> "ページ"
                        "nl" -> "Pagina"
                        else -> "Page"
                    }
                    val finalPageRepresentation = "• [$labelPage ${idx+1}/${extracted.size}] ${item.topCandidate ?: ""}: \"$finalReasonLocal\""

                    if (isLast) {
                        val fullAccumulated = buildString {
                            if (reasonAccumulatorGlobal.isNotEmpty()) {
                                append(reasonAccumulatorGlobal.toString())
                                append("\n\n")
                            }
                            append(finalPageRepresentation)
                        }
                        ttsSession.end(onAllPlayed = {
                            onStep(4, "done", fullAccumulated)
                        })
                    } else {
                        ttsSession.end()
                        if (reasonAccumulatorGlobal.isNotEmpty()) {
                            reasonAccumulatorGlobal.append("\n\n")
                        }
                        reasonAccumulatorGlobal.append(finalPageRepresentation)
                    }
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] [Page ${idx+1}] Gemma Vision Raw Reply:\n$reply")

                    val json = extractFirstBalancedJson(reply) ?: reply
                    val root = JSONObject(json)
                    val selectedIndex = root.optInt("selected_index", -1)
                    val reason = root.optString("reason", "No reason provided")

                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] [Page ${idx+1}] Parsed match: index=$selectedIndex, reason='$reason'")

                    if (selectedIndex in 1..candidatesList.size) {
                        val confirmedConcept = candidatesList[selectedIndex - 1]
                        Log.i(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] [Page ${idx+1}] Vision CONFIRMED concept: $confirmedConcept")

                        val newResolution = kb.resolveDrug(confirmedConcept)
                        val atc = when (newResolution) {
                            is ResolvedConcept.Exact -> newResolution.value.atcCode
                            is ResolvedConcept.Prefix -> newResolution.value.atcCode
                            is ResolvedConcept.Contains -> newResolution.value.atcCode
                            ResolvedConcept.NotFound -> null
                        }
                        val doses = if (atc != null) kb.fetchDoseHints(atc) else emptyList()

                        finalExtracted.add(
                            item.copy(
                                topCandidate = confirmedConcept,
                                kbResolution = newResolution,
                                doseHints = doses,
                                visionVerified = true,
                                visionReason = reason,
                                selected = newResolution !is ResolvedConcept.NotFound
                            )
                        )
                    } else {
                        Log.w(TAG, "[t=${System.currentTimeMillis()}] 👁️ [JEMMA-ASSISTANT-VISION] [Page ${idx+1}] Vision declared NO MATCH (-1). Keeping default KB candidate.")
                        finalExtracted.add(
                            item.copy(
                                visionVerified = false,
                                visionReason = "Gemma Vision did not confirm any candidate: $reason"
                            )
                        )
                    }
                } catch (e: Exception) {
                    ttsSession.cancel()
                    Log.e(TAG, "[t=${System.currentTimeMillis()}] 👁️ [Page ${idx+1}] Vision inference threw", e)
                    finalExtracted.add(item)
                }
            }
        } else {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] 👁️ No image URIs passed, skipping Step 4")
            finalExtracted.addAll(extracted)
            val skipMsg = when (lang) {
                "fr" -> "Pas d'images à valider (sauté)"
                "ja" -> "検証対象の画像はありません（スキップ）"
                else -> "No images to validate (skipped)"
            }
            onStep(4, "done", skipMsg)
            return finalExtracted
        }

        val verifiedCount = finalExtracted.count { it.visionVerified }
        // Synchronous loop is done, but we DO NOT call onStep(4, "done") here anymore.
        // The "done" event is triggered asynchronously in the ttsSession.end(onAllPlayed = ...) callback
        // when the user has actually finished listening to Jemma's voice.
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 Visual verification loop completed synchronously ($verifiedCount verified). Waiting for TTS stream to finish.")

        return finalExtracted
    }

    private fun loadScaledBitmap(context: android.content.Context, uri: android.net.Uri, maxDim: Int = 1024): android.graphics.Bitmap? {
        return try {
            val resolver = context.contentResolver
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri).use {
                android.graphics.BitmapFactory.decodeStream(it, null, options)
            }
            var scale = 1
            while (options.outWidth / scale / 2 >= maxDim && options.outHeight / scale / 2 >= maxDim) {
                scale *= 2
            }
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            resolver.openInputStream(uri).use {
                android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [JEMMA-ASSISTANT-VISION] Failed to load scaled bitmap for URI: $uri", e)
            null
        }
    }

    /**
     * Parse la réponse Gemma avec auto-completion pour gérer le cas où
     * Gemma tronque (vu sur Pixel 9 Pro : réponse = `{"boxes":[{"i":1,
     * "c":["bilastine"]}` sans fermeture finale). On compte les `{` et
     * `[` non fermés et on les ferme manuellement avant parse.
     */
    private fun parseGemmaBoxes(reply: String): Map<Int, List<String>>? {
        // 1) Essai direct : extract first balanced JSON.
        val balanced = extractFirstBalancedJson(reply)
        if (balanced != null) {
            tryParseJson(balanced)?.let { return it }
        }

        // 2) Fallback : auto-complete un JSON tronqué.
        val autoCompleted = autoCompleteJson(reply)
        if (autoCompleted != null) {
            Log.i(
                TAG,
                "[t=${System.currentTimeMillis()}] 🔧 auto-completed JSON from " +
                    "${reply.length} → ${autoCompleted.length} chars",
            )
            tryParseJson(autoCompleted)?.let { return it }
        }

        return null
    }

    /** Tente parse JSON, return Map ou null. */
    private fun tryParseJson(jsonStr: String): Map<Int, List<String>>? = try {
        val root = JSONObject(jsonStr)
        val arr = root.getJSONArray("boxes")
        val result = mutableMapOf<Int, List<String>>()
        for (i in 0 until arr.length()) {
            val box = arr.getJSONObject(i)
            val idx = box.optInt("i", -1)
            if (idx < 0) continue
            val candArr = box.optJSONArray("c") ?: continue
            val candidates = (0 until candArr.length()).map { candArr.optString(it) }
                .filter { it.isNotBlank() }
            if (candidates.isNotEmpty()) result[idx] = candidates
        }
        result
    } catch (e: JSONException) {
        Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ JSON parse failed! Raw string: '$jsonStr'", e)
        null
    }

    /** Auto-complete les `]` et `}` manquants à la fin de la réponse. */
    private fun autoCompleteJson(reply: String): String? {
        val start = reply.indexOf('{')
        if (start < 0) return null
        val raw = reply.substring(start)
        var braceDepth = 0
        var brackDepth = 0
        var inStr = false
        var escape = false
        for (c in raw) {
            if (escape) { escape = false; continue }
            if (c == '\\' && inStr) { escape = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            when (c) {
                '{' -> braceDepth++
                '}' -> braceDepth--
                '[' -> brackDepth++
                ']' -> brackDepth--
            }
        }
        if (braceDepth == 0 && brackDepth == 0 && !inStr) return null  // already balanced
        val sb = StringBuilder(raw)
        if (inStr) sb.append('"')
        // Si trailing comma juste avant la fin → la retirer (sinon JSONException)
        var lastIdx = sb.length - 1
        while (lastIdx >= 0 && sb[lastIdx].isWhitespace()) lastIdx--
        if (lastIdx >= 0 && sb[lastIdx] == ',') sb.deleteCharAt(lastIdx)
        repeat(brackDepth) { sb.append(']') }
        repeat(braceDepth) { sb.append('}') }
        return sb.toString()
    }

    /** Trouve le premier objet JSON balanced (ancienne logique). */
    private fun extractFirstBalancedJson(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

private class StreamingReasonExtractor {
    private val buffer = java.lang.StringBuilder()
    private var reasonStarted = false
    private var reasonEnded = false

    fun feed(chunk: String): String? {
        if (reasonEnded) return null

        val prevLen = buffer.length
        buffer.append(chunk)
        val full = buffer.toString()

        if (!reasonStarted) {
            val reasonKeyIdx = full.indexOf("\"reason\"")
            if (reasonKeyIdx != -1) {
                val colonIdx = full.indexOf(":", reasonKeyIdx + 8)
                if (colonIdx != -1) {
                    val openQuoteIdx = full.indexOf("\"", colonIdx + 1)
                    if (openQuoteIdx != -1) {
                        reasonStarted = true
                        val initialText = full.substring(openQuoteIdx + 1)
                        val closingQuoteIdx = initialText.indexOf("\"")
                        return if (closingQuoteIdx != -1) {
                            reasonEnded = true
                            initialText.substring(0, closingQuoteIdx)
                        } else {
                            initialText
                        }
                    }
                }
            }
            return null
        } else {
            val newPart = full.substring(prevLen)
            val closingQuoteIdx = newPart.indexOf("\"")
            if (closingQuoteIdx != -1) {
                val absoluteIdx = prevLen + closingQuoteIdx
                if (absoluteIdx > 0 && full[absoluteIdx - 1] != '\\') {
                    reasonEnded = true
                    return newPart.substring(0, closingQuoteIdx)
                }
            }
            return newPart
        }
    }
}
