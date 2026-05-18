/*
 * JemmaSttService.kt — JEMMA Pass · Lot 14.5c16 (PHASE 14)
 *
 * Bridge Android natif vers `android.speech.SpeechRecognizer`.
 *
 * API inspirée du HTML `JemmaSTT` :
 *
 *   service.isSupported() : Boolean
 *   service.start(lang, listener) → démarre la reconnaissance continue
 *   service.stop() → arrête immédiatement (le listener reçoit onEnd
 *                     avec le texte final concaténé)
 *   service.cancel() → annule sans envoyer onEnd
 *
 * Le listener reçoit :
 *   - onPartial(text) : transcription partielle en cours (non finale)
 *   - onFinal(text) : segment final (entre deux pauses)
 *   - onEnd(fullText) : la reconnaissance s'est terminée (auto ou stop())
 *   - onError(code, msg) : SpeechRecognizer.ERROR_* code
 *
 * NB : SpeechRecognizer impose RECORD_AUDIO permission. Au lot 14.5c16,
 * cette permission est déjà demandée au boot via PermissionsFragment
 * (carte MICROPHONE — Manifest.permission.RECORD_AUDIO). Si elle n'est
 * pas accordée le start() émettra ERROR_INSUFFICIENT_PERMISSIONS et le
 * caller affichera un message d'erreur dans le chat.
 *
 * Log channel : JEMMA-STT
 */
package be.heyman.android.jemmapassdemo.ai.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-STT"

@Singleton
class JemmaSttService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var recognizer: SpeechRecognizer? = null
    private var currentListener: SttListener? = null
    private val committed = StringBuilder()
    private var active = false

    fun isSupported(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Démarre la reconnaissance. lang au format BCP-47 ("fr-FR", "en-US", "ja-JP").
     * Le listener reçoit partial/final/end/error.
     */
    fun start(lang: String, listener: SttListener) {
        if (active) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ start while already active · cancelling previous")
            cancel()
        }
        if (!isSupported()) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ STT not supported on device")
            listener.onError(SpeechRecognizer.ERROR_CLIENT, "STT not supported on this device")
            return
        }
        this.currentListener = listener
        this.committed.clear()
        active = true

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { rec ->
            rec.setRecognitionListener(InternalListener())
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎤 start · lang=$lang")
        recognizer?.startListening(intent)
    }

    /** Arrête la capture audio. Le résultat final arrive via listener.onEnd. */
    fun stop() {
        if (!active) return
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🛑 stop")
        recognizer?.stopListening()
    }

    /** Annule sans déclencher onEnd. */
    fun cancel() {
        if (!active) return
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✂ cancel")
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        currentListener = null
        committed.clear()
        active = false
    }

    interface SttListener {
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onEnd(fullText: String)
        fun onError(code: Int, msg: String)
        /** 🆕 Lot 14.5c30 v6 — RMS amplitude callback for waveform UI.
         *  Range typically -2.0..10.0 dB. Default no-op so callers can opt-in. */
        fun onRms(rmsDb: Float) {}
    }

    private inner class InternalListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🟢 onReadyForSpeech")
            currentListener?.onReady()
        }
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🗣 onBeginningOfSpeech")
        }
        override fun onRmsChanged(rmsdB: Float) {
            currentListener?.onRms(rmsdB)
        }
        override fun onBufferReceived(buffer: ByteArray?) { /* ignore */ }
        override fun onEndOfSpeech() {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] 🤐 onEndOfSpeech")
        }

        override fun onError(error: Int) {
            val msg = errorMessage(error)
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ❌ onError code=$error · $msg")
            val l = currentListener
            cleanupAfterTerminal()
            l?.onError(error, msg)
        }

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = list?.firstOrNull().orEmpty()
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ onResults final · \"${best.take(80)}${if (best.length > 80) "…" else ""}\"")
            committed.append(if (committed.isEmpty()) best else " $best")
            val l = currentListener
            val fullText = committed.toString().trim()
            cleanupAfterTerminal()
            l?.onFinal(best)
            l?.onEnd(fullText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = list?.firstOrNull().orEmpty()
            if (best.isNotBlank()) {
                Log.d(TAG, "[t=${System.currentTimeMillis()}] ⌛ onPartial · \"${best.take(60)}${if (best.length > 60) "…" else ""}\"")
                currentListener?.onPartial(best)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) { /* ignore */ }
    }

    private fun cleanupAfterTerminal() {
        recognizer?.destroy()
        recognizer = null
        active = false
        // currentListener intentionally kept so caller's onFinal/onEnd/onError
        // can still fire; cleared on next start() or cancel().
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "audio_error"
        SpeechRecognizer.ERROR_CLIENT -> "client_error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient_permissions"
        SpeechRecognizer.ERROR_NETWORK -> "network_error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network_timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer_busy"
        SpeechRecognizer.ERROR_SERVER -> "server_error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech_timeout"
        else -> "error_$code"
    }
}
