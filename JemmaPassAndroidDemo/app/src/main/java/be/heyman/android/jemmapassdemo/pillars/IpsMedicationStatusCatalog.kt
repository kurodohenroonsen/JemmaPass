/*
 * IpsMedicationStatusCatalog.kt — JEMMA Pass · v2.6.0h
 *
 * FHIR R4 MedicationStatement.status valueset (cardinality 1..1 IPS required) :
 *   active | completed | entered-in-error | intended | stopped | on-hold |
 *   unknown | not-taken
 *
 * URI : http://hl7.org/fhir/CodeSystem/medication-statement-status
 *
 * Pour le pitch hackathon, par défaut = "active" (le patient prend actuellement).
 */
package be.heyman.android.jemmapassdemo.pillars

data class MedStatusEntry(
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

object IpsMedicationStatusCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/CodeSystem/medication-statement-status"
    const val DEFAULT_CODE = "active"

    val ALL: List<MedStatusEntry> = listOf(
        MedStatusEntry("active",
            "Active",            "En cours",            "服用中",        "✅"),
        MedStatusEntry("completed",
            "Completed",         "Terminé",             "完了",          "🏁"),
        MedStatusEntry("intended",
            "Intended",          "Prévu",               "予定",          "📅"),
        MedStatusEntry("stopped",
            "Stopped",           "Arrêté",              "中止",          "⛔"),
        MedStatusEntry("on-hold",
            "On hold",           "En pause",            "一時停止",      "⏸"),
        MedStatusEntry("not-taken",
            "Not taken",         "Non pris",            "未服用",        "🚫"),
        MedStatusEntry("unknown",
            "Unknown",           "Inconnu",             "不明",          "❔"),
        MedStatusEntry("entered-in-error",
            "Entered in error",  "Saisi par erreur",    "誤入力",        "⚠️"),
    )

    private val byCode: Map<String, MedStatusEntry> by lazy { ALL.associateBy { it.code } }

    fun byCode(code: String?): MedStatusEntry? {
        if (code.isNullOrBlank()) return null
        return byCode[code.trim().lowercase()]
    }

    fun getDisplay(code: String?, lang: String): String {
        if (code.isNullOrBlank()) return ""
        return byCode(code)?.pick(lang) ?: code
    }
}
