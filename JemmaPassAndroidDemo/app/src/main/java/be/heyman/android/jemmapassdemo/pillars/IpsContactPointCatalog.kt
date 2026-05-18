/*
 * IpsContactPointCatalog.kt — JEMMA Pass · v2.6.0h
 *
 * Catalogs FHIR R4 pour Patient.address.use + Patient.telecom.system + Patient.telecom.use.
 * Trois petits enums regroupés dans un seul fichier pour proximité fonctionnelle.
 *
 * Spec URIs :
 *   address.use     : http://hl7.org/fhir/address-use
 *   telecom.system  : http://hl7.org/fhir/contact-point-system
 *   telecom.use     : http://hl7.org/fhir/contact-point-use
 */
package be.heyman.android.jemmapassdemo.pillars

data class CodedEntry(
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

private fun List<CodedEntry>.lookup(c: String?): CodedEntry? {
    if (c.isNullOrBlank()) return null
    return firstOrNull { it.code == c.trim().lowercase() }
}

object IpsAddressUseCatalog {
    const val CODE_SYSTEM = "http://hl7.org/fhir/address-use"

    val ALL: List<CodedEntry> = listOf(
        CodedEntry("home",    "Home",    "Domicile",   "自宅",        "🏠"),
        CodedEntry("work",    "Work",    "Travail",    "勤務先",      "🏢"),
        CodedEntry("temp",    "Temporary", "Temporaire", "一時的",     "📍"),
        CodedEntry("old",     "Previous", "Ancienne",  "以前",        "📦"),
        CodedEntry("billing", "Billing", "Facturation", "請求先",     "💳"),
    )

    fun byCode(code: String?): CodedEntry? = ALL.lookup(code)
    fun getDisplay(code: String?, lang: String): String =
        byCode(code)?.pick(lang) ?: code.orEmpty()
}

object IpsTelecomSystemCatalog {
    const val CODE_SYSTEM = "http://hl7.org/fhir/contact-point-system"

    val ALL: List<CodedEntry> = listOf(
        CodedEntry("phone", "Phone",  "Téléphone",     "電話",      "📞"),
        CodedEntry("email", "Email",  "Email",         "メール",    "✉️"),
        CodedEntry("sms",   "SMS",    "SMS",           "SMS",       "💬"),
        CodedEntry("fax",   "Fax",    "Fax",           "ファックス", "📠"),
        CodedEntry("url",   "URL",    "URL",           "URL",       "🌐"),
        CodedEntry("other", "Other",  "Autre",         "その他",    "❔"),
    )

    fun byCode(code: String?): CodedEntry? = ALL.lookup(code)
    fun getDisplay(code: String?, lang: String): String =
        byCode(code)?.pick(lang) ?: code.orEmpty()
}

object IpsTelecomUseCatalog {
    const val CODE_SYSTEM = "http://hl7.org/fhir/contact-point-use"

    val ALL: List<CodedEntry> = listOf(
        CodedEntry("home",   "Home",       "Domicile",    "自宅",      "🏠"),
        CodedEntry("work",   "Work",       "Travail",     "勤務先",    "🏢"),
        CodedEntry("mobile", "Mobile",     "Mobile",      "携帯",      "📱"),
        CodedEntry("temp",   "Temporary",  "Temporaire",  "一時的",    "📍"),
        CodedEntry("old",    "Previous",   "Ancien",      "以前",      "📦"),
    )

    fun byCode(code: String?): CodedEntry? = ALL.lookup(code)
    fun getDisplay(code: String?, lang: String): String =
        byCode(code)?.pick(lang) ?: code.orEmpty()
}
