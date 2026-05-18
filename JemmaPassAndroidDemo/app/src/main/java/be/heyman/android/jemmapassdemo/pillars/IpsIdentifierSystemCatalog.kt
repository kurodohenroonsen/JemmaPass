/*
 * IpsIdentifierSystemCatalog.kt — JEMMA Pass · v2.6.0h
 *
 * Catalog des identifier systems couramment utilisés en EU + Japon,
 * pour FHIR Patient.identifier.system.
 *
 * Short codes mappés sur URIs FHIR canoniques :
 *   BE-NRN     → urn:oid:2.16.840.1.113883.2.8.2.1 (numéro registre national belge)
 *   JP-MyNumber→ urn:oid:1.2.392.100495.20.3.41 (マイナンバー)
 *   FR-NSS     → urn:oid:1.2.250.1.213.1.4.8 (numéro de sécurité sociale)
 *   EU-EHIC    → urn:oid:1.0.18013.5.1 (European Health Insurance Card)
 *   PASSPORT   → http://hl7.org/fhir/sid/passport-NNN (ISO 3166 country)
 *   OTHER      → urn:jemma:identifier:other (catch-all)
 */
package be.heyman.android.jemmapassdemo.pillars

data class IdentifierSystemEntry(
    val shortCode: String,
    val uri: String,
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

object IpsIdentifierSystemCatalog {

    val ALL: List<IdentifierSystemEntry> = listOf(
        IdentifierSystemEntry(
            shortCode = "BE-NRN",
            uri = "urn:oid:2.16.840.1.113883.2.8.2.1",
            displayEn = "Belgian National Register",
            displayFr = "Registre National (BE)",
            displayJa = "ベルギー国民登録番号",
            emoji = "🇧🇪",
        ),
        IdentifierSystemEntry(
            shortCode = "JP-MyNumber",
            uri = "urn:oid:1.2.392.100495.20.3.41",
            displayEn = "Japan My Number",
            displayFr = "My Number (JP)",
            displayJa = "マイナンバー",
            emoji = "🇯🇵",
        ),
        IdentifierSystemEntry(
            shortCode = "FR-NSS",
            uri = "urn:oid:1.2.250.1.213.1.4.8",
            displayEn = "French Social Security",
            displayFr = "N° Sécurité Sociale (FR)",
            displayJa = "フランス社会保障番号",
            emoji = "🇫🇷",
        ),
        IdentifierSystemEntry(
            shortCode = "EU-EHIC",
            uri = "urn:oid:1.0.18013.5.1",
            displayEn = "European Health Insurance Card",
            displayFr = "Carte Européenne d'Assurance Maladie",
            displayJa = "欧州健康保険カード",
            emoji = "🇪🇺",
        ),
        IdentifierSystemEntry(
            shortCode = "PASSPORT",
            uri = "http://hl7.org/fhir/sid/passport-INT",
            displayEn = "Passport number",
            displayFr = "Numéro de passeport",
            displayJa = "パスポート番号",
            emoji = "📕",
        ),
        IdentifierSystemEntry(
            shortCode = "OTHER",
            uri = "urn:jemma:identifier:other",
            displayEn = "Other ID",
            displayFr = "Autre identifiant",
            displayJa = "その他のID",
            emoji = "🆔",
        ),
    )

    private val byShort: Map<String, IdentifierSystemEntry> by lazy { ALL.associateBy { it.shortCode } }

    fun byShortCode(short: String?): IdentifierSystemEntry? {
        if (short.isNullOrBlank()) return null
        return byShort[short.trim()]
    }

    fun getDisplay(short: String?, lang: String): String {
        if (short.isNullOrBlank()) return ""
        return byShort[short]?.pick(lang) ?: short
    }
}
