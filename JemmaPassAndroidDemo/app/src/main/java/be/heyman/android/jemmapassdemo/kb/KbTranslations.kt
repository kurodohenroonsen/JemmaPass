/*
 * KbTranslations.kt — hygiene of `ips_valuesets_translations.display` values.
 *
 * The KB stores untranslated concepts as "Not Translated[<English term>]" (device QA
 * cycle 16: 56717001 Tuberculosis in JA). Such a value is not a translation: lookups
 * treat it as missing (fallback to the English term), lists unwrap it.
 */
package be.heyman.android.jemmapassdemo.kb

object KbTranslations {
    private const val PREFIX = "Not Translated["

    fun isPlaceholder(s: String?): Boolean = s != null && s.trim().startsWith(PREFIX)

    /** A usable translation, or null (blank / placeholder). */
    fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && !isPlaceholder(it) }

    /** "Not Translated[Tuberculosis]" → "Tuberculosis"; anything else unchanged. */
    fun unwrap(s: String): String {
        val t = s.trim()
        return if (isPlaceholder(t)) t.removePrefix(PREFIX).removeSuffix("]").trim() else s
    }
}
