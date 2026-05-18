/*
 * IpsReactionSeverityCatalog.kt — JEMMA Pass · v2.6.0h
 *
 * FHIR R4 AllergyIntolerance.reaction.severity valueset :
 *   mild | moderate | severe
 *
 * URI : http://hl7.org/fhir/reaction-event-severity
 *
 * À ne pas confondre avec AllergyIntolerance.criticality (high/low/unable-to-assess)
 * qui est une appréciation globale du risque.
 */
package be.heyman.android.jemmapassdemo.pillars

data class ReactionSeverityEntry(
    val code: String,
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
    val emoji: String,
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }
}

object IpsReactionSeverityCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/reaction-event-severity"

    val ALL: List<ReactionSeverityEntry> = listOf(
        ReactionSeverityEntry(
            code = "mild",
            displayEn = "Mild",     displayFr = "Légère",   displayJa = "軽度",
            emoji = "🟢",
        ),
        ReactionSeverityEntry(
            code = "moderate",
            displayEn = "Moderate", displayFr = "Modérée",  displayJa = "中等度",
            emoji = "🟡",
        ),
        ReactionSeverityEntry(
            code = "severe",
            displayEn = "Severe",   displayFr = "Sévère",   displayJa = "重度",
            emoji = "🔴",
        ),
    )

    private val byCode: Map<String, ReactionSeverityEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): ReactionSeverityEntry? {
        if (code.isNullOrBlank()) return null
        return byCode[code.trim().lowercase()]
    }

    fun getDisplay(code: String?, lang: String): String {
        if (code.isNullOrBlank()) return ""
        return byCode(code)?.pick(lang) ?: code
    }
}
