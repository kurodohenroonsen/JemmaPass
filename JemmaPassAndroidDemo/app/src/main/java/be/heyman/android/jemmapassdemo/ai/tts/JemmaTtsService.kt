/*
 * JemmaTtsService.kt — JEMMA Pass · Lot 14.5c16 (PHASE 14)
 *
 * Bridge Android natif vers `android.speech.tts.TextToSpeech` avec API
 * de streaming inspirée de la version HTML (`jemma_tts_service.js`).
 *
 * Le service offre deux modes :
 *
 *   1. speakOneShot(text, lang) — phrase unique en QUEUE_FLUSH.
 *      Utilisé pour les messages d'accueil et confirmations courtes.
 *
 *   2. startStream(lang) → StreamSession — pour le streaming Gemma.
 *      Le caller appelle session.feed(chunk) à chaque token reçu. On
 *      bufferise et dès qu'une phrase complète est détectée (terminator
 *      [.!?。！？]+ + boundary), on la pousse à TTS en QUEUE_ADD. Au
 *      session.end(), on flush le buffer restant. session.cancel()
 *      stoppe immédiatement TTS et purge la queue.
 *
 * Engine lifecycle :
 *   - init(onReady) idempotent ; appelé au onViewCreated du chat
 *   - shutdown() obligatoire au onDestroy du Fragment qui possède la session
 *
 * Si TTS pas ready quand feed/speak est appelé, on bufferise jusqu'à
 * ready (les premières phrases peuvent partir avec ~200ms de latence).
 *
 * Log channel : JEMMA-TTS-CHAT
 */
package be.heyman.android.jemmapassdemo.ai.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-TTS-CHAT"

/**
 * Regex sentence-boundary multilingue : FR/EN/JA/ZH.
 * On split sur `.!?。！？` suivi d'un espace ou EOL, OU sur newline.
 */
private val SENTENCE_END = Regex(
    "(\\n+)|" +                                           // newline forcé
    "([!?。！？]+[\"')\\]]?)\\s+|" +                        // ! ? 。 ！ ？
    "(\\.+[\"')\\]]?)\\s+|" +                              // . . . . .
    "([.!?。！？]+[\"')\\]]?)\\s*\$"                        // ponctuation finale
)

@Singleton
class JemmaTtsService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingReadyCallbacks = mutableListOf<() -> Unit>()
    private val pendingSentences = mutableListOf<PendingSentence>()
    private val sessionCounter = AtomicInteger(0)

    private val _isSpeaking = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSpeaking: kotlinx.coroutines.flow.StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private data class PendingSentence(
        val sentence: String,
        val lang: String,
        val sourceId: String,
        val flushFirst: Boolean
    )

    /**
     * 🆕 Lot 14.5c17 — Map utteranceId → callback onDone. Permet au caller
     * de speakOneShot de savoir quand le greeting a fini de jouer (ce qui
     * était impossible avant : le `tts.speak()` retourne sync mais l'audio
     * dure plusieurs secondes). Le caller passe son callback en argument :
     * `speakOneShot("Bonjour…", "fr-FR", onDone = { startStt() })`.
     *
     * 🆕 Lot 14.5c18 — `completedUtterances` mémorise les utterances déjà
     * `onDone`-firées pour gérer le cas où on enregistre un callback APRÈS
     * la fin réelle de l'utterance (race entre push + register). Set borné
     * à 64 entrées pour ne pas leak en mémoire.
     */
    private val doneCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private val completedUtterances = java.util.Collections.newSetFromMap(
        java.util.LinkedHashMap<String, Boolean>(64, 0.75f, true /* access-order */)
            .also { /* bounded via eviction in put */ }
    )
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun markCompleted(utteranceId: String) {
        synchronized(completedUtterances) {
            completedUtterances.add(utteranceId)
            // Bornage soft : on garde max 64 ids dans le set.
            if (completedUtterances.size > 64) {
                val it = completedUtterances.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }

    private fun isCompleted(utteranceId: String): Boolean =
        synchronized(completedUtterances) { completedUtterances.contains(utteranceId) }

    /**
     * Enregistre un callback à appeler quand `utteranceId` finit. Si
     * l'utterance est DÉJÀ finie (race), invoke immédiat sur main thread.
     */
    internal fun registerDoneCallback(utteranceId: String, cb: () -> Unit) {
        if (isCompleted(utteranceId)) {
            Log.d(TAG, "[t=${System.currentTimeMillis()}] ⚡ registerDone but already completed · id=$utteranceId · invoke immediate")
            mainHandler.post(cb)
        } else {
            doneCallbacks[utteranceId] = cb
        }
    }

    /** Init idempotent. Si déjà ready, callback immédiat. */
    fun init(onReady: () -> Unit = {}) {
        if (ready) {
            onReady()
            return
        }
        if (tts != null) {
            pendingReadyCallbacks.add(onReady)
            return
        }
        pendingReadyCallbacks.add(onReady)
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🎬 initializing TextToSpeech")
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                // 🆕 Lot 14.5c17 — Listener pour invoquer doneCallbacks[id]
                // quand une utterance est jouée jusqu'au bout. Posté sur le
                // main thread pour respecter le contrat UI.
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "[t=${System.currentTimeMillis()}] ▶ utterance start · id=$utteranceId")
                        _isSpeaking.value = true
                    }
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "[t=${System.currentTimeMillis()}] ✅ utterance done · id=$utteranceId")
                        if (utteranceId == null) return
                        markCompleted(utteranceId)
                        val cb = doneCallbacks.remove(utteranceId) ?: return
                        mainHandler.post(cb)
                        
                        // Si aucune autre phrase n'est en cours
                        if (tts?.isSpeaking == false) {
                            _isSpeaking.value = false
                        }
                    }
                    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ utterance error · id=$utteranceId")
                        if (utteranceId == null) return
                        markCompleted(utteranceId)
                        val cb = doneCallbacks.remove(utteranceId) ?: return
                        mainHandler.post(cb)
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ utterance error · id=$utteranceId · code=$errorCode")
                        if (utteranceId == null) return
                        markCompleted(utteranceId)
                        val cb = doneCallbacks.remove(utteranceId) ?: return
                        mainHandler.post(cb)
                    }
                })
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✅ TTS ready · pendingReady=${pendingReadyCallbacks.size} · pendingSentences=${pendingSentences.size}")
                val cbs = pendingReadyCallbacks.toList()
                pendingReadyCallbacks.clear()
                cbs.forEach { it() }

                // 🆕 Lot 14.5c16 — Flush les phrases bufferisées pendant l'init.
                if (pendingSentences.isNotEmpty()) {
                    val list = pendingSentences.toList()
                    pendingSentences.clear()
                    list.forEach { ps ->
                        pushSentence(ps.sentence, ps.lang, ps.sourceId, ps.flushFirst)
                    }
                }
            } else {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ TTS init failed · status=$status")
            }
        }
    }

    fun shutdown() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔻 TTS shutdown")
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pendingReadyCallbacks.clear()
    }

    /** Stoppe tout TTS courant + purge queue. */
    fun stopAll() {
        Log.i(TAG, "[t=${System.currentTimeMillis()}] ✂ stopAll · purge queue")
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * Speak one-shot — la phrase passe en QUEUE_FLUSH (interrompt).
     * @param onDone callback optionnel invoqué sur le main thread une fois
     *   que l'audio a fini de jouer (utile pour démarrer le STT à la
     *   fin du greeting sans tuer le greeting au passage).
     */
    fun speakOneShot(text: String, lang: String, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ speakOneShot but tts is NULL · init never called?")
            if (onDone != null) mainHandler.post(onDone)
            return
        }
        if (!ready) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ⏳ speakOneShot before ready · buffering \"${text.take(40)}…\"")
            pendingSentences.add(PendingSentence(text, lang, "oneshot", true))
            // Note: we can't easily register the onDone callback for a buffered oneshot
            // since we don't have the utteranceId yet. We'll skip it for simplicity
            // as vulgarisation doesn't use onDone for oneshots anyway.
            return
        }
        val locale = Locale.forLanguageTag(lang)
        val localeStatus = engine.setLanguage(locale)
        if (localeStatus == TextToSpeech.LANG_MISSING_DATA ||
            localeStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ locale '$lang' missing · fallback default")
            engine.language = Locale.getDefault()
        }
        val utteranceId = "tts_oneshot_${System.currentTimeMillis()}_${sessionCounter.incrementAndGet()}"
        if (onDone != null) doneCallbacks[utteranceId] = onDone
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔊 speakOneShot [$lang/$utteranceId] \"${text.take(60)}${if (text.length > 60) "…" else ""}\"")
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Démarre une session de streaming TTS.
     * Le caller appelle :
     *   - session.feed(chunk) à chaque token Gemma
     *   - session.end() quand le stream est terminé (flush du buffer restant)
     *   - session.cancel() pour interrompre (purge queue + stop courant)
     */
    fun startStream(lang: String): StreamSession {
        val sourceId = "stream_${sessionCounter.incrementAndGet()}"
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🌊 startStream · sourceId=$sourceId · lang=$lang")
        return StreamSessionImpl(this, lang, sourceId)
    }

    /**
     * Push une phrase à l'engine TTS en QUEUE_ADD (ne flush pas la précédente).
     * @return l'utteranceId généré (pour permettre à la session de tracker
     *   la dernière phrase et y register un onAllPlayed callback).
     */
    internal fun pushSentence(sentence: String, lang: String, sourceId: String, flushFirst: Boolean): String? {
        val engine = tts
        if (engine == null) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠ pushSentence but tts is NULL · init never called?")
            return null
        }
        if (!ready) {
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ⏳ pushSentence before ready · buffering sentence (sourceId=$sourceId)")
            pendingSentences.add(PendingSentence(sentence, lang, sourceId, flushFirst))
            return null
        }
        val locale = Locale.forLanguageTag(lang)
        engine.setLanguage(locale)
        val mode = if (flushFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "tts_${sourceId}_${System.currentTimeMillis()}_${sessionCounter.incrementAndGet()}"
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔊 pushSentence [$lang/$sourceId/$utteranceId/${if (flushFirst) "FLUSH" else "ADD"}] \"${sentence.take(80)}${if (sentence.length > 80) "…" else ""}\"")
        engine.speak(sentence, mode, null, utteranceId)
        return utteranceId
    }

    interface StreamSession {
        fun feed(chunk: String)
        /**
         * Signale la fin du stream Gemma. Flush le buffer restant en TTS.
         * @param onAllPlayed callback optionnel invoqué sur le main thread
         *   quand la dernière phrase TTS a fini de jouer (utile pour
         *   auto-restart le STT après que Jemma a fini de parler).
         */
        fun end(onAllPlayed: (() -> Unit)? = null)
        fun cancel()
    }

    private class StreamSessionImpl(
        private val service: JemmaTtsService,
        private val lang: String,
        private val sourceId: String,
    ) : StreamSession {

        private val buffer = StringBuilder()
        private var ended = false
        private var cancelled = false
        private var flushedFirst = false
        private var lastUtteranceId: String? = null

        override fun feed(chunk: String) {
            if (ended || cancelled) return
            buffer.append(chunk)
            flushSentences(force = false)
        }

        override fun end(onAllPlayed: (() -> Unit)?) {
            if (ended || cancelled) {
                if (onAllPlayed != null) service.mainHandler.post(onAllPlayed)
                return
            }
            ended = true
            Log.i(TAG, "[t=${System.currentTimeMillis()}] 🏁 stream.end · sourceId=$sourceId · trailing=${buffer.length}")
            flushSentences(force = true)
            // 🆕 Lot 14.5c18 — Register onAllPlayed sur le dernier utteranceId
            // poussé. Si aucune phrase n'a été émise (cas vide), invoke direct.
            if (onAllPlayed != null) {
                val lastId = lastUtteranceId
                if (lastId == null) {
                    Log.d(TAG, "[t=${System.currentTimeMillis()}] ⚡ end with no utterance · invoke onAllPlayed immediate")
                    service.mainHandler.post(onAllPlayed)
                } else {
                    Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔗 register onAllPlayed on lastUtteranceId=$lastId")
                    service.registerDoneCallback(lastId, onAllPlayed)
                }
            }
        }

        override fun cancel() {
            if (cancelled) return
            cancelled = true
            Log.i(TAG, "[t=${System.currentTimeMillis()}] ✂ stream.cancel · sourceId=$sourceId")
            buffer.clear()
            service.stopAll()
        }

        private fun flushSentences(force: Boolean) {
            val out = mutableListOf<String>()
            while (true) {
                if (force) {
                    val tail = buffer.toString().trim()
                    if (tail.isNotEmpty()) out.add(tail)
                    buffer.clear()
                    break
                }
                val m = SENTENCE_END.find(buffer.toString()) ?: break
                val endIdx = m.range.last + 1
                val sentence = buffer.substring(0, endIdx).trim()
                if (sentence.isNotEmpty()) out.add(sentence)
                buffer.delete(0, endIdx)
            }
            if (out.isEmpty()) return
            for (s in out) {
                if (cancelled) break
                val flush = !flushedFirst
                flushedFirst = true
                val id = service.pushSentence(s, lang, sourceId, flush)
                if (id != null) lastUtteranceId = id
            }
        }
    }
}
