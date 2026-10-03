package be.heyman.android.jemmapassdemo.qr

fun interface CodeLabelResolver {
    fun getLabel(system: String, code: String, lang: String): String?
    companion object { val NONE = CodeLabelResolver { _, _, _ -> null } }
}

/**
 * Maps a code system URI and a curated code to its Android string resource name according to PROTOCOL §9.1:
 *   - "http://snomed.info/sct" → prefix "sct"
 *   - "http://loinc.org" → prefix "loinc"
 *   - "http://terminology.hl7.org/CodeSystem/v3-RoleCode" → prefix "v3"
 *   - Any other system → null
 * Non-alphanumeric characters in [code] are replaced by '_'.
 * Result format: "code_label_<prefix>_<cleaned_code>".
 */
fun codeLabelResourceName(system: String, code: String): String? {
    val prefix = when (system.trim()) {
        "http://snomed.info/sct", "sct" -> "sct"
        "http://loinc.org", "loinc" -> "loinc"
        "http://terminology.hl7.org/CodeSystem/v3-RoleCode", "v3" -> "v3"
        else -> return null
    }
    val cleanCode = code.trim().replace(Regex("[^A-Za-z0-9]"), "_")
    return "code_label_${prefix}_$cleanCode"
}
