/*
 * TtsStaticVerdict.kt — JEMMA Pass · Live Scan v0.4 refonte · Lot 3
 *
 * Phase C output #2 : verdict verbal déterministe avec
 * `android.speech.tts.TextToSpeech` et phrases pré-fab dans
 * `res/values<lang>/strings_tts.xml`.
 *
 * Pas de LLM. Pas de streaming. <100ms entre report et premier phonème.
 *
 * ─── Sélection de phrase ───
 *
 *   verdict CLEAN       → tts_safe (seul chemin vers "aucune interaction")
 *   verdict NOT_CHECKED → tts_scan_not_checked ("vérification impossible, demander
 *                         à un pharmacien ou un médecin")
 *   verdict INCOMPLETE  → tts_scan_incomplete (idem, vérification partielle)
 *   hit + check partiel → phrase du hit + tts_scan_incomplete_note
 *   allergy HIGH/Major  → tts_major_allergy avec nom allergène
 *   ddi MAJOR           → tts_major_ddi avec nom drug du patient
 *   condition MAJOR     → tts_major_condition avec nom condition
 *   xxx MODERATE        → tts_moderate_xxx
 *   xxx MINOR           → tts_minor (générique)
 *
 * Toutes les strings sont localisées en fr/en/ja/es minimum.
 *
 * ─── TTS engine lifecycle ───
 *
 *   • init(onReady?) — appelé une fois au démarrage du Fragment
 *   • speak(phrase, lang) — peut être appelé plusieurs fois
 *   • shutdown() — appelé dans onDestroyView (Fragment scope)
 *
 * Init est asynchrone : si tu speak avant `ready=true`, la phrase est
 * droppée (avec warning log). Le Fragment doit init() dès
 * onViewCreated pour éviter le drop sur le premier scan.
 *
 * Log channel : JEMMA-TTS
 */
package be.heyman.android.jemmapassdemo.ai.livescan

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import be.heyman.android.jemmapassdemo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JEMMA-TTS"

@Singleton
class TtsStaticVerdict @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    /**
     * Init le TTS engine. Appel idempotent — si déjà ready, callback immédiat.
     */
    fun init(onReady: () -> Unit = {}) {
        if (tts != null && ready) {
            onReady()
            return
        }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                Log.i(TAG, "[t=${System.currentTimeMillis()}] ✓ TTS engine ready")
                onReady()
            } else {
                Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ TTS init failed status=$status")
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    /**
     * Construit la phrase TTS pour un report donné dans la langue
     * demandée. Pure function — testable sans Android dependencies
     * modulo le `Resources` accès.
     */
    fun buildPhrase(report: CrossCheckReport, lang: String): String {
        val res = localizedResources(lang)

        // UC-SAFE-SCAN — the sentence is chosen by pure logic (ScanSafety) from the
        // report verdict : tts_safe is reachable only for a CLEAN verdict.
        val plan = ScanSafety.phrasePlan(report)
        val name = plan.arg.orEmpty()
        val main = when (plan.phrase) {
            ScanSafety.Phrase.SAFE -> res.getString(R.string.tts_safe)
            ScanSafety.Phrase.NOT_CHECKED -> res.getString(R.string.tts_scan_not_checked)
            ScanSafety.Phrase.INCOMPLETE -> res.getString(R.string.tts_scan_incomplete)
            ScanSafety.Phrase.MAJOR_ALLERGY -> res.getString(R.string.tts_major_allergy, name)
            ScanSafety.Phrase.MAJOR_DDI -> res.getString(R.string.tts_major_ddi, name)
            ScanSafety.Phrase.MAJOR_CONDITION -> res.getString(R.string.tts_major_condition, name)
            ScanSafety.Phrase.MODERATE_ALLERGY -> res.getString(R.string.tts_moderate_allergy, name)
            ScanSafety.Phrase.MODERATE_DDI -> res.getString(R.string.tts_moderate_ddi, name)
            ScanSafety.Phrase.MODERATE_CONDITION -> res.getString(R.string.tts_moderate_condition, name)
            ScanSafety.Phrase.MINOR -> res.getString(R.string.tts_minor, name)
        }
        return if (plan.appendIncompleteNote) {
            main + " " + res.getString(R.string.tts_scan_incomplete_note)
        } else {
            main
        }
    }

    /**
     * Prononce la phrase. Si pas ready, drop avec warning.
     * setLanguage() retourne LANG_NOT_SUPPORTED si la voix n'est pas
     * installée → on fallback sur la locale par défaut du device.
     */
    fun speak(phrase: String, lang: String) {
        val engine = tts ?: run {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ speak before init — dropping: \"$phrase\"")
            return
        }
        if (!ready) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ TTS not ready — dropping: \"$phrase\"")
            return
        }
        val locale = Locale.forLanguageTag(lang)
        val localeStatus = engine.setLanguage(locale)
        if (localeStatus == TextToSpeech.LANG_MISSING_DATA ||
            localeStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "[t=${System.currentTimeMillis()}] ⚠️ locale '$lang' not supported, fallback")
            engine.language = Locale.getDefault()
        }
        Log.i(TAG, "[t=${System.currentTimeMillis()}] 🔊 speak [$lang]: \"$phrase\"")
        engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "tts_verdict")
    }

    private fun localizedResources(lang: String): android.content.res.Resources {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        return context.createConfigurationContext(config).resources
    }
}
