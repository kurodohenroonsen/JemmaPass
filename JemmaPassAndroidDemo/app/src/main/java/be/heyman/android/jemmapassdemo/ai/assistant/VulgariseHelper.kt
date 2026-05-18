/*
 * VulgariseHelper.kt — Lot 14.5c8
 *
 * Helper partagé entre [AssistantMultiPreviewFragment] et
 * [ProfileDetailFragment] pour vulgariser une alerte clinique via Gemma
 * dans la langue du device. Centralise :
 *   • La data class [AlertVulgariseContext] qui capture les inputs
 *   • Le system prompt "expliquer à un enfant de 10 ans"
 *   • Le formatage du user prompt côté Gemma
 *
 * L'UI (bouton, streaming TextView, "Relancer") reste dans chaque
 * fragment puisque l'intégration diffère (RecyclerView item vs
 * AlertDialog setView).
 */
package be.heyman.android.jemmapassdemo.ai.assistant

import java.util.Locale

/** Capture le contexte minimal nécessaire pour vulgariser une alerte. */
data class AlertVulgariseContext(
    /** "allergy" | "drug_drug_interaction" | "drug_disease" */
    val kind: String,
    /** Les sujets de l'alerte (généralement 2 : médoc A, médoc B). */
    val subjects: List<String>,
    /** Sévérité (HIGH / MODERATE / MAJOR / MINOR...). */
    val severity: String,
    /** Mécanisme pharmacologique optionnel (PK_*, PD_*, etc.). */
    val mechanism: String?,
    /** Description clinique anglaise (DDInter / KB). */
    val description: String?,
    /** 🆕 Lot 14.5c16 — Unique key for persistence (e.g. "DDI|R06AX29|N06AB06"). */
    val cacheKey: String,
    /** 🆕 Optional suggested therapeutic class or drug alternative (e.g. "B01A - ANTITHROMBOTIC AGENTS"). */
    val alternative: String? = null,
) {
    /** Construit le user prompt envoyé à Gemma. */
    fun toUserPrompt(): String {
        val deviceLang = Locale.getDefault().displayLanguage
        val langTag = Locale.getDefault().language
        return buildString {
            // 🆕 Lot 14.5c9 — Directive langue en haut DU user prompt aussi.
            append("⚠️ RESPOND ONLY IN $deviceLang (tag $langTag). NO ENGLISH unless $deviceLang IS ENGLISH.\n\n")
            append("Clinical alert to explain in simple language.\n\n")
            append("Type: $kind\n")
            append("Subjects: ${subjects.joinToString(" × ")}\n")
            append("Severity: $severity\n")
            if (!mechanism.isNullOrBlank()) append("Mechanism: $mechanism\n")
            if (!description.isNullOrBlank()) append("Clinical description: $description\n")
            if (!alternative.isNullOrBlank()) append("Suggested safer alternative class/medication: $alternative\n")
            append("\nExplain this clearly to an adult patient who has no medical background. Describe what each medicine normally does, the specific medical reason why combining them is a concern, what could happen in the body, and what practical steps to take. ")
            if (!alternative.isNullOrBlank()) {
                append("If a suggested alternative class/medication is provided, mention it clearly in your explanation as a potential option they should discuss with their doctor, without prescribing it directly. ")
            }
            append("Ensure the medical concepts are accurate but easy to grasp. End with a one-line reminder to consult a doctor or pharmacist.\n\n")
            // 🆕 Lot 14.5c9 — Directive langue en bas aussi.
            append("⚠️ FINAL REMINDER: Your ENTIRE answer MUST be in $deviceLang. Start your first sentence in $deviceLang. Do not use any English (unless $deviceLang IS English).")
        }
    }
}

/** 🆕 Lot 14.5c19 — Capture le contexte minimal pour vulgariser un médicament. */
data class MedicationVulgariseContext(
    val atcCode: String,
    val name: String,
    val description: String?,
    val emlStatus: String?,
    val awareCategory: String?,
) {
    val cacheKey: String get() = "drug_$atcCode"

    fun toUserPrompt(): String {
        val deviceLang = Locale.getDefault().displayLanguage
        val langTag = Locale.getDefault().language
        return buildString {
            append("⚠️ RESPOND ONLY IN $deviceLang (tag $langTag). NO ENGLISH unless $deviceLang IS ENGLISH.\n\n")
            append("Medication to explain in simple language.\n\n")
            append("Drug: $name (ATC: $atcCode)\n")
            if (!description.isNullOrBlank()) append("Clinical details: $description\n")
            if (!emlStatus.isNullOrBlank()) append("Clinical Status: $emlStatus\n")
            if (!awareCategory.isNullOrBlank()) append("AWaRe Category: $awareCategory\n")

            append("\nExplain this clearly to an adult patient. Describe what this medicine is for, how it generally helps the body, its main purpose, and if there are any specific warnings to keep in mind. Ensure the tone is reassuring and professional. End with a one-line reminder to consult a doctor or pharmacist.\n\n")
            append("⚠️ FINAL REMINDER: Your ENTIRE answer MUST be in $deviceLang. Start your first sentence in $deviceLang. Do not use any English (unless $deviceLang IS English).")
        }
    }
}

/**
 * System prompt pour Gemma — vulgarisation longue style "expliquer à
 * un enfant de 10 ans". Plus longue et plus rassurante que la version
 * 14.5c7 (2-3 phrases trop sèches).
 *
 * 🆕 Lot 14.5c9 — Renforcement de la directive de langue :
 *   • Instruction de langue en MAJUSCULES au DÉBUT et à la FIN du
 *     system prompt (Gemma a tendance à recopier les exemples anglais
 *     du prompt verbatim et continuer en anglais malgré "ONLY")
 *   • Exemples de comparaisons sans phrases anglaises (placeholders)
 *   • Le user prompt finit également par une directive de langue
 */
fun buildVulgariseSystemPrompt(): String {
    val deviceLang = Locale.getDefault().displayLanguage
    val langTag = Locale.getDefault().language
    return """
        ⚠️ CRITICAL LANGUAGE RULE — READ FIRST ⚠️
        YOU MUST RESPOND ENTIRELY IN $deviceLang (BCP-47 tag: $langTag).
        NOT A SINGLE WORD OF ENGLISH (unless $deviceLang IS ENGLISH).
        DO NOT START YOUR ANSWER WITH "Imagine your body…" OR ANY OTHER ENGLISH PHRASE.
        EVERY SENTENCE MUST BE IN $deviceLang.

        ROLE
        You are a professional, empathetic health educator. Your job is to explain a clinical
        alert (a drug-drug interaction, an allergy reaction, or a drug-disease
        contraindication) to an adult patient who does not have a medical background.

        WRITING RULES
        - Write 5 to 8 clear, concise sentences. Maintain a reassuring, informative, and professional tone.
        - Explain the medical concepts accurately but accessibly. Use mature, relatable analogies if helpful (like a traffic jam, a filter system, or chemical messengers), but avoid sounding childish or patronizing. Always write the analogy in $deviceLang.
        - Avoid overly complex medical jargon. If you must mention a body part or organ,
          briefly say what it does (in $deviceLang).
        - Structure : (1) say what each medicine does in plain $deviceLang words ;
          (2) explain WHY combining them is a worry ; (3) describe what could
          happen in the body, simply ; (4) end with a single warm sentence
          telling the patient to talk to their doctor or pharmacist (translated
          into $deviceLang).
        - Plain prose only. NO bullet points, NO headers, NO markdown formatting,
          NO asterisks, NO bold/italic syntax.
        - Do not repeat the input fields ("Type:", "Severity:", etc.) — write a
          flowing explanation.

        ⚠️ FINAL REMINDER — LANGUAGE ⚠️
        Your ENTIRE response MUST be in $deviceLang. If $deviceLang is "français",
        write in French. If $deviceLang is "日本語", write in Japanese. If
        $deviceLang is "español", write in Spanish. Translate everything,
        including the comparison/metaphor. Do not output any English word
        unless $deviceLang IS English.
    """.trimIndent()
}

/** 🆕 Lot 14.5c19 — System prompt pour Gemma — vulgarisation d'un médicament seul. */
fun buildMedicationSystemPrompt(): String {
    val deviceLang = Locale.getDefault().displayLanguage
    val langTag = Locale.getDefault().language
    return """
        ⚠️ CRITICAL LANGUAGE RULE — READ FIRST ⚠️
        YOU MUST RESPOND ENTIRELY IN $deviceLang (BCP-47 tag: $langTag).
        NOT A SINGLE WORD OF ENGLISH (unless $deviceLang IS ENGLISH).
        
        ROLE
        You are a professional, empathetic health educator. Your job is to explain a 
        specific medication to an adult patient who does not have a medical background.
        
        WRITING RULES
        - Write 5 to 7 clear, concise sentences. Maintain a reassuring and professional tone.
        - Explain what the medicine is used for in plain $deviceLang words.
        - Describe how it helps the body (using simple analogies if needed).
        - Highlight any major warnings or precautions mentioned in the clinical details.
        - End with a single warm sentence telling the patient to talk to their doctor or pharmacist (translated into $deviceLang).
        - Plain prose only. NO markdown, NO bullet points.
        
        ⚠️ FINAL REMINDER — LANGUAGE ⚠️
        Your ENTIRE response MUST be in $deviceLang.
    """.trimIndent()
}

/**
 * 🆕 Lot 14.5c10 — ThrottledTextAppender
 *
 * Évite l'ANR pendant le streaming Gemma. Avant : chaque token (~20-30/s)
 * déclenchait `view.post { target.append(partial) }` ce qui causait un
 * layout pass complet du ScrollView à chaque token. Si l'user scrollait
 * en même temps, le UI thread saturait et le système levait un ANR.
 *
 * Maintenant : on accumule les partials thread-safe et on flush au max
 * 1× toutes les ~120ms. Le streaming reste visible et fluide.
 *
 *   val appender = ThrottledTextAppender(view, target, ttsStream = stream)
 *   try {
 *       val reply = gemma.ask(..., onPartial = { p -> appender.append(p) })
 *       appender.cancelUI()
 *       target.text = reply  // texte final autoritaire
 *       appender.end() // Termine le stream TTS
 *   } finally {
 *       appender.cancel()
 *   }
 */
class ThrottledTextAppender(
    private val anchorView: android.view.View,
    private val target: android.widget.TextView,
    private val intervalMs: Long = 150L, // Légèrement plus lent pour économiser le CPU
    private val ttsStream: be.heyman.android.jemmapassdemo.ai.tts.JemmaTtsService.StreamSession? = null,
) {
    private val buffer = StringBuilder()
    private val lock = Any()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var pending = false

    private val flushRunnable = Runnable {
        if (!anchorView.isAttachedToWindow) {
            pending = false
            return@Runnable
        }
        val toFlush = synchronized(lock) {
            if (buffer.isEmpty()) null else {
                val s = buffer.toString()
                buffer.clear()
                s
            }
        }
        pending = false
        if (toFlush != null) {
            target.append(toFlush)
        }
    }

    /** Appelable depuis n'importe quel thread (typiquement le worker Gemma). */
    fun append(partial: String) {
        if (partial.isEmpty()) return
        ttsStream?.feed(partial)
        synchronized(lock) { buffer.append(partial) }
        if (!pending) {
            pending = true
            handler.postDelayed(flushRunnable, intervalMs)
        }
    }

    /** À appeler en fin d'inférence pour annuler tout flush UI en attente.
     *  Le code appelant définit ensuite `target.text = reply` final. */
    fun cancelUI() {
        handler.removeCallbacks(flushRunnable)
        synchronized(lock) { buffer.clear() }
        pending = false
    }

    /** Signale la fin du flux texte au TTS. */
    fun end() {
        ttsStream?.end()
    }

    /** Annule l'UI et coupe instantanément le TTS en cours. */
    fun cancel() {
        cancelUI()
        ttsStream?.cancel()
    }
}
