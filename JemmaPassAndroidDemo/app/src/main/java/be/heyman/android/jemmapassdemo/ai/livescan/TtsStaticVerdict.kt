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
 *   overall == NONE     → tts_safe
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

        if (report.overall == Severity.NONE) {
            return res.getString(R.string.tts_safe)
        }

        // Cherche le pire hit cross-pillars
        data class HitRef(val severity: Severity, val kind: String, val name: String)
        val candidates = mutableListOf<HitRef>()
        report.allergyHits.forEach { candidates.add(HitRef(it.severity, "allergy", it.patientAllergyName)) }
        report.ddiHits.forEach { candidates.add(HitRef(it.severity, "ddi", it.withDrugName)) }
        report.conditionHits.forEach { candidates.add(HitRef(it.severity, "condition", it.conditionName)) }

        val worst = candidates.maxByOrNull { it.severity.ordinal }
            ?: return res.getString(R.string.tts_safe)

        val stringId = when {
            worst.severity == Severity.MAJOR && worst.kind == "allergy" -> R.string.tts_major_allergy
            worst.severity == Severity.MAJOR && worst.kind == "ddi" -> R.string.tts_major_ddi
            worst.severity == Severity.MAJOR && worst.kind == "condition" -> R.string.tts_major_condition
            worst.severity == Severity.MODERATE && worst.kind == "allergy" -> R.string.tts_moderate_allergy
            worst.severity == Severity.MODERATE && worst.kind == "ddi" -> R.string.tts_moderate_ddi
            worst.severity == Severity.MODERATE && worst.kind == "condition" -> R.string.tts_moderate_condition
            else -> R.string.tts_minor
        }
        return res.getString(stringId, worst.name)
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
