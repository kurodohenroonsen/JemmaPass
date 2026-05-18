/*
 * GemmaSessionStreamingExt.kt — JEMMA Pass · v0.4 refonte
 *
 * Extension `askStreaming` qui simule un vrai stream via chunks-buffer
 * fallback (validé par Kudoro comme acceptable pour hackathon).
 *
 * ─── Approche ───
 *
 * GemmaSession.ask() en interne fait déjà du streaming via
 * `LlmChatModelHelper.runInference.resultListener(partial, isDone, _)`,
 * MAIS bufferise tout dans un StringBuilder et résume la coroutine
 * uniquement à `isDone=true`. Pour exposer un vrai stream, il faudrait
 * modifier GemmaSession.kt — risqué à J-6.
 *
 * Workaround : on appelle `ask()` normalement (récupère le texte
 * complet) puis on émet par chunks de ~25 chars avec delay 80ms entre
 * chaque chunk vers le callback `onToken`. Visuellement indiscernable
 * d'un vrai stream pour l'utilisateur — le rescuer voit le texte
 * apparaître progressivement.
 *
 * ─── Pourquoi 25 chars / 80ms ───
 *
 * Vitesse de lecture humaine moyenne : ~250 mots/min = ~25 chars/seconde
 * pour le français. Donc :
 *   • 25 chars × (1000/80) = ~312 chars/sec côté streaming
 *   • L'utilisateur reçoit le texte ~12× plus vite qu'il ne peut le lire
 *   • Mais le **rendu progressif** lui donne le sentiment d'IA "en train de réfléchir"
 *
 * Si tu veux un vrai stream token-par-token plus tard, ajoute une
 * surcharge à `runInference` dans GemmaSession qui forward le `partial`
 * vers un callback externe. ~15 LOC dans GemmaSession.kt.
 *
 * Log channel : JEMMA-STREAM
 */
package be.heyman.android.jemmapassdemo.ai.gemma

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.ToolProvider
import kotlinx.coroutines.delay

private const val TAG = "JEMMA-STREAM"

/**
 * Wrapper streaming au-dessus de `GemmaSession.ask()`.
 *
 * @param prompt user prompt
 * @param systemInstruction system prompt (peut être null si déjà set via configureForTask)
 * @param tools tools list (peut être null si déjà set via configureForTask)
 * @param image image optionnelle pour les tâches multimodales
 * @param chunkSize taille des chunks émis (défaut 25 chars)
 * @param chunkDelayMs delay entre chunks (défaut 80ms)
 * @param onToken callback appelé pour chaque chunk de texte
 * @return le texte complet (au cas où le caller veut aussi avoir le full result)
 */
suspend fun GemmaSession.askStreaming(
    prompt: String,
    systemInstruction: String? = null,
    tools: List<ToolProvider>? = null,
    image: Bitmap? = null,
    chunkSize: Int = 25,
    chunkDelayMs: Long = 80L,
    onToken: (String) -> Unit,
): String {
    val tStart = System.currentTimeMillis()
    Log.i(TAG, "[t=$tStart] 📡 askStreaming start · promptLen=${prompt.length}")

    // 1. Appel synchrone normal (qui bufferise tout)
    val fullText = try {
        ask(
            prompt = prompt,
            image = image,
            systemInstruction = systemInstruction,
            tools = tools,
        )
    } catch (e: Exception) {
        Log.e(TAG, "[t=${System.currentTimeMillis()}] ❌ underlying ask() threw", e)
        throw e
    }

    val elapsedToFullText = System.currentTimeMillis() - tStart
    Log.i(TAG, "[t=${System.currentTimeMillis()}] 📦 buffered ${fullText.length}c · ${elapsedToFullText}ms")

    // 2. Émission progressive vers onToken
    var idx = 0
    while (idx < fullText.length) {
        val end = (idx + chunkSize).coerceAtMost(fullText.length)
        val chunk = fullText.substring(idx, end)
        onToken(chunk)
        idx = end
        if (idx < fullText.length) {
            delay(chunkDelayMs)
        }
    }

    val totalElapsed = System.currentTimeMillis() - tStart
    Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ askStreaming done · ${totalElapsed}ms")
    return fullText
}
