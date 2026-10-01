/*
 * DrugDiseaseTerms.kt — how a profile condition is matched against the DDInter
 * `drug_disease_interactions.disease_name_en` column (English, free text).
 *
 * Device QA cycle 18: 0 drug × disease hits for personas with obvious pairs, because
 * the query used the label in the UI language and only one containment direction
 * ("Hypertension" never contains "Essential hypertension"). Now: English terms first
 * (stored SNOMED display, KB primary display), the localised label last, a few
 * spelling variants, and containment in both directions.
 */
package be.heyman.android.jemmapassdemo.kb

object DrugDiseaseTerms {

    /** Disease names shorter than this never match by "term contains disease". */
    const val MIN_REVERSE_LENGTH = 6

    private val SEMANTIC_TAG = Regex("\\s*\\((disorder|finding|disease|situation)\\)\\s*$", RegexOption.IGNORE_CASE)

    private val VARIANTS = listOf("kidney" to "renal", "renal" to "kidney", "cardiac failure" to "heart failure")

    fun candidates(storedDisplay: String?, kbPrimaryDisplay: String?, localized: String?): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in listOf(storedDisplay, kbPrimaryDisplay, localized)) {
            val t = raw?.replace(SEMANTIC_TAG, "")?.trim()?.lowercase() ?: continue
            if (t.length < 4 || t == "—") continue
            out += t
            for ((a, b) in VARIANTS) if (t.contains(a)) out += t.replace(a, b)
        }
        // the localised label stays last even if a variant was added after it
        val loc = localized?.replace(SEMANTIC_TAG, "")?.trim()?.lowercase()
        if (loc != null && loc in out && out.size > 1) { out.remove(loc); out += loc }
        return out.toList()
    }

    /**
     * Word-wise singular form used on both sides of a comparison (device QA cycle 19:
     * DDInter says "Kidney Diseases", SNOMED "Chronic kidney disease stage 3").
     * Deliberately naive — applied symmetrically, it only has to be consistent.
     */
    fun singular(text: String): String =
        text.lowercase().trim().split(Regex("\\s+")).joinToString(" ") { w ->
            when {
                w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
                w.length > 4 && w.endsWith("ses") -> w.dropLast(1)
                w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
                else -> w
            }
        }

    /** Containment in both directions, case-insensitive, plural-insensitive. */
    fun matches(term: String, diseaseName: String): Boolean {
        val t = singular(term)
        val d = singular(diseaseName)
        if (t.isEmpty() || d.isEmpty()) return false
        return d.contains(t) || (d.length >= MIN_REVERSE_LENGTH && t.contains(d))
    }
}
