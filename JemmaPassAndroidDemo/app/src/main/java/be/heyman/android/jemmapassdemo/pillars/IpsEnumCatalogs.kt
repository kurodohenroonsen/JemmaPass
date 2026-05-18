/*
 * IpsEnumCatalogs.kt — JEMMA Pass · Plan B · v2.6.0 · L5b
 *
 * Catalogs des enums FHIR IPS courts (≤5 valeurs) utilisés par les forms
 * piliers Allergies / Medications.
 *
 * Ces enums sont **stables, documentés, multilingues-neutres** côté FHIR
 * — ils ne changeront pas. On les hardcode en Kotlin object + traductions
 * FR/JA puisque :
 *   - <5 valeurs chacun (pas worth de querier la KB)
 *   - vocabulaire bien connu (criticality H/L/U, status A/I/R)
 *   - aligné guide DIAMOND v1.2 §0 : "Les seuls strings admis dans
 *     WHERE sont des ENUMs finis"
 *
 * Sources :
 *   - AllergyIntolerance.criticality :  HL7 FHIR ValueSet
 *       http://hl7.org/fhir/ValueSet/allergy-intolerance-criticality
 *       (high | low | unable-to-assess)
 *   - AllergyIntolerance.clinicalStatus : HL7 FHIR ValueSet
 *       http://hl7.org/fhir/ValueSet/allergyintolerance-clinical
 *       (active | inactive | resolved)
 *   - AllergyIntolerance.category (FHIR R4 standard enum) :
 *       food | medication | environment | biologic
 *
 * Toutes les traductions FR/JA proviennent de i18n_forge.js du HTML legacy
 * (donc validées par Kudoro déjà).
 *
 * 🎯 PHILOSOPHIE — code-first :
 *   JAllergy.s = "H" / "L" / "U" (1 char compact pour QR)
 *   JAllergy.st = "A" / "I" / "R"
 *   On mappe ces shorts vers les codes FHIR canoniques lors de l'export FHIR.
 */
package be.heyman.android.jemmapassdemo.pillars

// ═════════════════════════════════════════════════════════════════════
//  Allergy Intolerance Criticality — FHIR enum
// ═════════════════════════════════════════════════════════════════════

/**
 * @property shortCode 1 char persisté dans JAllergy.s (compact QR)
 * @property fhirCode code FHIR canonique pour l'export Bundle IPS
 */
data class CriticalityEntry(
    val shortCode: String,
    val fhirCode: String,
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

object IpsCriticalityCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/allergy-intolerance-criticality"

    val ALL: List<CriticalityEntry> = listOf(
        CriticalityEntry(
            shortCode = "H", fhirCode = "high",
            displayEn = "⚠️ Severe",  displayFr = "⚠️ Sévère",
            displayJa = "⚠️ 重度", emoji = "⚠️",
        ),
        CriticalityEntry(
            shortCode = "L", fhirCode = "low",
            displayEn = "Mild", displayFr = "Légère",
            displayJa = "軽度", emoji = "🔸",
        ),
        CriticalityEntry(
            shortCode = "U", fhirCode = "unable-to-assess",
            displayEn = "Unknown", displayFr = "Inconnue",
            displayJa = "不明", emoji = "❔",
        ),
    )

    private val byShort: Map<String, CriticalityEntry> by lazy { ALL.associateBy { it.shortCode } }

    fun getDisplay(shortCode: String?, lang: String): String {
        if (shortCode.isNullOrBlank()) return ""
        return byShort[shortCode.trim().uppercase()]?.pick(lang) ?: shortCode
    }

    fun byShortCode(shortCode: String?): CriticalityEntry? {
        if (shortCode.isNullOrBlank()) return null
        return byShort[shortCode.trim().uppercase()]
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Allergy Intolerance Clinical Status — FHIR enum
// ═════════════════════════════════════════════════════════════════════

data class ClinicalStatusEntry(
    val shortCode: String,
    val fhirCode: String,
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
) {
    fun pick(lang: String): String = when (lang.lowercase().take(2)) {
        "fr" -> displayFr
        "ja" -> displayJa
        else -> displayEn
    }
}

object IpsClinicalStatusCatalog {

    const val CODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical"

    val ALL: List<ClinicalStatusEntry> = listOf(
        ClinicalStatusEntry(
            shortCode = "A", fhirCode = "active",
            displayEn = "Active",   displayFr = "Active",   displayJa = "有効",
        ),
        ClinicalStatusEntry(
            shortCode = "I", fhirCode = "inactive",
            displayEn = "Inactive", displayFr = "Inactive", displayJa = "無効",
        ),
        ClinicalStatusEntry(
            shortCode = "R", fhirCode = "resolved",
            displayEn = "Resolved", displayFr = "Résolue",  displayJa = "解消",
        ),
    )

    private val byShort: Map<String, ClinicalStatusEntry> by lazy { ALL.associateBy { it.shortCode } }

    fun getDisplay(shortCode: String?, lang: String): String {
        if (shortCode.isNullOrBlank()) return ""
        return byShort[shortCode.trim().uppercase()]?.pick(lang) ?: shortCode
    }

    fun byShortCode(shortCode: String?): ClinicalStatusEntry? {
        if (shortCode.isNullOrBlank()) return null
        return byShort[shortCode.trim().uppercase()]
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Allergy Intolerance Category — FHIR enum (R4 standard)
// ═════════════════════════════════════════════════════════════════════

data class AllergyCategoryEntry(
    val fhirCode: String,   // ← persisted directly (4 values, all short)
    val displayEn: String,
    val displayFr: String,
    val displayJa: String,
    val emoji: String,
)

object IpsAllergyCategoryCatalog {

    const val CODE_SYSTEM = "http://hl7.org/fhir/allergy-intolerance-category"

    val ALL: List<AllergyCategoryEntry> = listOf(
        AllergyCategoryEntry(
            fhirCode = "food",
            displayEn = "Food",       displayFr = "Aliment",       displayJa = "食物",
            emoji = "🍴",
        ),
        AllergyCategoryEntry(
            fhirCode = "medication",
            displayEn = "Medication", displayFr = "Médicament",    displayJa = "薬物",
            emoji = "💊",
        ),
        AllergyCategoryEntry(
            fhirCode = "environment",
            displayEn = "Environment", displayFr = "Environnement", displayJa = "環境",
            emoji = "🌿",
        ),
        AllergyCategoryEntry(
            fhirCode = "biologic",
            displayEn = "Biologic",   displayFr = "Biologique",    displayJa = "生物製剤",
            emoji = "🧬",
        ),
    )

    private val byCode: Map<String, AllergyCategoryEntry> by lazy { ALL.associateBy { it.fhirCode } }

    fun getDisplay(fhirCode: String?, lang: String): String {
        if (fhirCode.isNullOrBlank()) return ""
        val entry = byCode[fhirCode.trim().lowercase()] ?: return fhirCode
        return when (lang.lowercase().take(2)) {
            "fr" -> entry.displayFr
            "ja" -> entry.displayJa
            else -> entry.displayEn
        }
    }

    fun byFhirCode(fhirCode: String?): AllergyCategoryEntry? {
        if (fhirCode.isNullOrBlank()) return null
        return byCode[fhirCode.trim().lowercase()]
    }
}
