package be.heyman.android.jemmapassdemo.kb

/**
 * SD-22 (UC-ALM-009, UC-ALM-010) — Allergy class keyword inference on whole words.
 *
 * When an allergy entry has no terminology code, this helper infers a candidate
 * ATC code from common French / English allergy names and drug class keywords.
 *
 * Crucial safety rule : keywords must match whole words only or defined word prefixes,
 * never substrings inside arbitrary words (e.g. "ains" in "grains", "statin" in "nystatine",
 * "iode" in "période").
 *
 * Pure Kotlin, no Android imports.
 */
object AllergyKeywords {

    private val WORD_REGEX = Regex("""[\p{L}\p{N}]+""")

    fun inferAtc(name: String): String? {
        if (name.isBlank()) return null
        val lower = name.lowercase()
        val words = WORD_REGEX.findAll(lower).map { it.value }.toList()
        if (words.isEmpty()) return null

        fun hasExactWord(vararg targets: String): Boolean =
            words.any { w -> targets.any { t -> w == t } }

        fun hasWordPrefix(vararg prefixes: String): Boolean =
            words.any { w -> prefixes.any { p -> w.startsWith(p) } }

        return when {
            // ── Beta-lactam antibacterials (J01C / J01D / J01DH) ──────
            hasWordPrefix("pénicill", "penicill", "amoxicill", "ampicill", "augmentin") ->
                "J01CA01" // → J01C class

            hasWordPrefix("céphalo", "cephalo", "céfa", "cefa", "ceftria") ->
                "J01DB01" // → J01D class

            hasWordPrefix("carbapén", "carbapen", "méropén", "meropen", "imipenem") ->
                "J01DH02" // → J01DH class

            // ── Sulfonamides (J01E) ───────────────────────────────────
            hasWordPrefix("sulfamide", "sulfonamide", "cotrimo", "bactrim") ||
                hasExactWord("sulfa", "sulfas") ->
                "J01EE01"

            // ── Macrolides (J01F) ─────────────────────────────────────
            hasWordPrefix("macrolid", "érythromy", "erythromy", "azithromy", "clarithromy") ->
                "J01FA01"

            // ── Tetracyclines (J01A) ──────────────────────────────────
            hasWordPrefix("tétracycl", "tetracycl", "doxycycl") ->
                "J01AA02"

            // ── Quinolones (J01M) ─────────────────────────────────────
            hasWordPrefix("quinolone", "ciprofloxa", "levofloxa", "moxifloxa") ->
                "J01MA02"

            // ── Aminoglycosides (J01G) ────────────────────────────────
            hasWordPrefix("aminoside", "aminoglyco", "gentamicin", "gentamicine") ->
                "J01GB03"

            // ── NSAIDs (M01A) ─────────────────────────────────────────
            // Whole words only for acronyms ("ains", "nsaid") to avoid "grains", "bains", etc.
            hasExactWord("ains", "nsaid", "nsaids") ||
                hasWordPrefix("ibuprof", "naprox", "diclof", "kétoprof", "ketoprof") ->
                "M01AE01"

            // ── Aspirin (N02BA / B01AC) ───────────────────────────────
            hasWordPrefix("aspirin") ||
                lower.contains("acide acétylsalicy") || lower.contains("acetylsalicy") ->
                "N02BA01"

            // ── Opioids (N02A) ────────────────────────────────────────
            hasWordPrefix("opioïd", "opioid", "morphin", "codéin", "codein", "tramadol") ->
                "N02AA01"

            // ── Iodine contrast (V08A) ────────────────────────────────
            // Whole words only for "iode"/"iodine" to avoid "période", "épisode", etc.
            hasExactWord("iode", "iodine") ||
                lower.contains("produit de contraste") || lower.contains("contrast media") ->
                "V08AB02"

            // ── Statins (C10AA) ───────────────────────────────────────
            // Whole words for "statin"/"statine" to avoid "nystatine", "cilastatin", etc.
            hasExactWord("statin", "statine", "statins", "statines") ||
                hasWordPrefix("atorvasta", "simvasta", "rosuvasta", "pravasta") ->
                "C10AA01"

            else -> null
        }
    }
}
