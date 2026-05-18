/*
 * IpsAllergyTypeCatalog.kt — JEMMA Pass · v2.6.0h
 *
 * FHIR R4 AllergyIntolerance.type valueset :
 *   allergy     = immune mechanism (IgE, etc.)
 *   intolerance = non-immune (pharmacological, idiosyncratic)
 *
 * URI : http://hl7.org/fhir/allergy-intolerance-type
 */
package be.heyman.android.jemmapassdemo.pillars

data class AllergyTypeEntry(
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

object IpsAllergyTypeCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/allergy-intolerance-type"

    val ALL: List<AllergyTypeEntry> = listOf(
        AllergyTypeEntry(
            code = "allergy",
            displayEn = "Allergy",     displayFr = "Allergie",     displayJa = "アレルギー",
            emoji = "⚡",
        ),
        AllergyTypeEntry(
            code = "intolerance",
            displayEn = "Intolerance", displayFr = "Intolérance",  displayJa = "不耐症",
            emoji = "⚠️",
        ),
    )

    private val byCode: Map<String, AllergyTypeEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): AllergyTypeEntry? {
        if (code.isNullOrBlank()) return null
        return byCode[code.trim().lowercase()]
    }

    fun getDisplay(code: String?, lang: String): String {
        if (code.isNullOrBlank()) return ""
        return byCode(code)?.pick(lang) ?: code
    }
}
