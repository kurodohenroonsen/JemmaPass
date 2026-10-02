/*
 * IpsRouteCatalog.kt — JEMMA Pass · Plan B · v2.6.0 · L5d
 *
 * Catalog FHIR R4 des routes d'administration de medications.
 * 5 valeurs courtes mappées sur JMedication.r (1 char compact pour QR).
 *
 * Mapping JEMMA short → FHIR SNOMED route code :
 *   O = oral         → 26643006   (Oral route)
 *   I = injection    → 47625008   (Intravenous route) — utilisée par défaut pour
 *                                                       toute injection (IV/IM/SC)
 *   T = topical      → 6064005    (Topical route)
 *   S = subcutaneous → 34206005   (Subcutaneous route)
 *   H = inhaled      → 447694001  (Respiratory tract route) — UC-MED-ROUTE-01.
 *                                  "I" was stored for inhalers before; "I" keeps
 *                                  meaning injection (no migration of stored entries),
 *                                  new inhaled entries are stored as "H" — the value
 *                                  JemmaFhirBundleBuilder.routeConcept already exports
 *                                  as 447694001.
 *
 * Pourquoi 4 valeurs et pas plus : c'est ce qui est dans JMedication.r doc string
 * actuel. Suffisant pour le pitch hackathon — un médecin urgentiste a juste besoin
 * de savoir si c'est oral vs injection vs cutané, le détail (IV/IM) tient dans
 * le timing/posologie.
 *
 * Pour étendre plus tard (sublingual, inhaled, rectal, etc.), c'est juste
 * ajouter des entries ici sans casser le QR codec.
 */
package be.heyman.android.jemmapassdemo.pillars

data class RouteEntry(
    val shortCode: String,    // 1 char, persisted in JMedication.r
    val snomedCode: String,   // FHIR SNOMED route code for export
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

object IpsRouteCatalog {

    /** SNOMED-CT codeset for FHIR Medication.route export */
    const val CODE_SYSTEM = "http://snomed.info/sct"

    /** Short code of the inhaled route. Never "I", which is injection. */
    const val INHALED = "H"

    val ALL: List<RouteEntry> = listOf(
        RouteEntry(
            shortCode = "O", snomedCode = "26643006",
            displayEn = "Oral",       displayFr = "Orale",      displayJa = "経口",
            emoji = "💊",
        ),
        RouteEntry(
            shortCode = "I", snomedCode = "47625008",
            displayEn = "Injection",  displayFr = "Injection",  displayJa = "注射",
            emoji = "💉",
        ),
        RouteEntry(
            shortCode = "T", snomedCode = "6064005",
            displayEn = "Topical",    displayFr = "Topique",    displayJa = "外用",
            emoji = "🧴",
        ),
        RouteEntry(
            shortCode = "S", snomedCode = "34206005",
            displayEn = "Subcutaneous", displayFr = "Sous-cutané", displayJa = "皮下",
            emoji = "🩹",
        ),
        RouteEntry(
            shortCode = INHALED, snomedCode = "447694001",
            displayEn = "Inhaled",    displayFr = "Inhalée",    displayJa = "吸入",
            emoji = "🫁",
        ),
    )

    private val byShort: Map<String, RouteEntry> by lazy { ALL.associateBy { it.shortCode } }

    fun getDisplay(shortCode: String?, lang: String): String {
        if (shortCode.isNullOrBlank()) return ""
        return byShort[shortCode.trim().uppercase()]?.pick(lang) ?: shortCode
    }

    fun byShortCode(shortCode: String?): RouteEntry? {
        if (shortCode.isNullOrBlank()) return null
        return byShort[shortCode.trim().uppercase()]
    }
}
