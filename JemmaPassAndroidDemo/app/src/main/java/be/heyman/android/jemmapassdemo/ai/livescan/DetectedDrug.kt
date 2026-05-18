/*
 * DetectedDrug.kt — JEMMA Pass · Live Scan v0.1
 *
 * Lightweight data class pour un médicament détecté par le pipeline
 * OCR live + FTS5 lookup. Passe au travers d'un StateFlow chaud à
 * ~10-15 fps sans pression GC notable.
 *
 * Visé : rendu chip overlay temps réel.
 *
 * Note d'archi : si le FTS5 ne trouve pas d'ATC pour un candidat
 * extrait par JemmaDrugTextProcessor, le candidat est droppé en amont
 * dans LiveScanRepository — on ne construit JAMAIS un DetectedDrug
 * avec atcCode null. Ça permet de garantir non-nullable côté UI.
 */
package be.heyman.android.jemmapassdemo.ai.livescan

/**
 * Un médicament détecté par le pipeline OCR live + FTS5 lookup.
 *
 * @property atcCode Code ATC L5 normalisé (ex: "J01CR02"). Toujours
 *   non-null — si le FTS5 n'a pas trouvé d'ATC, le candidat est
 *   filtré en amont par LiveScanRepository.
 * @property canonicalName Nom canonique English depuis
 *   terminology_codes.primary_display.
 * @property displayLocalized Nom localisé (langue victime) si dispo,
 *   sinon canonicalName.
 * @property matchedText Sous-texte OCR qui a déclenché le match
 *   (ex: "Augmentin").
 * @property matchedLang Backend FTS5 qui a matché : "latin" / "cjk" /
 *   "like".
 * @property confidence 0.0 - 1.0. Composite : rank FTS5 + exact match
 *   bonus + lang script bonus.
 * @property category "Medication" en principal, "Chemical_Allergen"
 *   toléré pour fallback excipient.
 * @property firstSeenAtMs Wall clock du 1er sighting cette session.
 *   Pour debouncing + lock-on-stable.
 * @property seenCount Nombre de fois vu (incrémenté à chaque OCR frame
 *   match). Seuil de lock : 3.
 */
data class DetectedDrug(
    val atcCode: String,
    val canonicalName: String,
    val displayLocalized: String,
    val matchedText: String,
    val matchedLang: String,
    val confidence: Float,
    val category: String,
    val firstSeenAtMs: Long = System.currentTimeMillis(),
    val seenCount: Int = 1,
)
