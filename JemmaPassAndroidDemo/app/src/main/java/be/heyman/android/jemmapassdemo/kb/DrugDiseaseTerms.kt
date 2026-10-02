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

    private val VARIANTS = listOf(
        "kidney" to "renal",
        "renal" to "kidney",
        "cardiac failure" to "heart failure",
        "heart failure" to "cardiac failure",
        "hepatic" to "liver",
        "liver" to "hepatic",
        "hypertensive disorder" to "hypertension",
        "renal failure" to "kidney disease",
        "kidney failure" to "kidney disease",
        "hepatic cirrhosis" to "liver disease",
        "liver cirrhosis" to "liver disease",
        "cirrhosis" to "liver disease",
    )

    private val EXCLUDED_QUALIFIERS = mapOf(
        "hypertension" to setOf("intracranial", "ocular", "portal", "pulmonary"),
        "diabetes" to setOf("insipidus"),
    )

    fun candidates(storedDisplay: String?, kbPrimaryDisplay: String?, localized: String?): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in listOf(storedDisplay, kbPrimaryDisplay, localized)) {
            val t = raw?.replace(SEMANTIC_TAG, "")?.trim()?.lowercase() ?: continue
            if (t.length < 4 || t == "—") continue
            out += t
            for ((a, b) in VARIANTS) {
                if (t.contains(a)) {
                    out += t.replace(a, b)
                    if (a == "hypertensive disorder") out += "hypertension"
                }
            }
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
                w == "diabetes" -> w
                w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
                w.length > 4 && w.endsWith("oses") -> w.dropLast(4) + "osis"
                w.length > 4 && w.endsWith("ses") -> w.dropLast(1)
                w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
                else -> w
            }
        }

    private fun wordsOf(s: String): List<String> =
        s.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }

    private fun containsWordSequence(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        for (i in 0..haystack.size - needle.size) {
            if (haystack.subList(i, i + needle.size) == needle) return true
        }
        return false
    }

    /** Containment in both directions, case-insensitive, plural-insensitive, whole-word aware. */
    fun matches(term: String, diseaseName: String): Boolean {
        val t = singular(term)
        val d = singular(diseaseName)
        if (t.isEmpty() || d.isEmpty()) return false

        val tWords = wordsOf(t)
        val dWords = wordsOf(d)
        if (tWords.isEmpty() || dWords.isEmpty()) return false

        if (tWords == dWords) return true

        // Direction 1: diseaseName contains term (e.g. term = "heart failure", diseaseName = "Congestive Heart Failure")
        if (containsWordSequence(dWords, tWords)) {
            val extraWords = dWords.filter { it !in tWords }.toSet()
            val hasExcludedQualifier = tWords.any { baseWord ->
                EXCLUDED_QUALIFIERS[baseWord]?.any { it in extraWords } == true
            }
            if (!hasExcludedQualifier) return true
        }

        // Direction 2: term contains diseaseName (e.g. term = "essential hypertension", diseaseName = "Hypertension")
        if (d.length >= MIN_REVERSE_LENGTH && containsWordSequence(tWords, dWords)) {
            val extraWords = tWords.filter { it !in dWords }.toSet()
            val hasExcludedQualifier = dWords.any { baseWord ->
                EXCLUDED_QUALIFIERS[baseWord]?.any { it in extraWords } == true
            }
            if (!hasExcludedQualifier) return true
        }

        return false
    }
}
