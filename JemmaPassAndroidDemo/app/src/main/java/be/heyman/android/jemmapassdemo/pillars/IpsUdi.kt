/*
 * IpsUdi.kt — JEMMA Pass · Medical Devices pillar (sprint 2)
 *
 * Lenient plausibility check for a Unique Device Identifier typed from an
 * implant card. We accept the three FDA/EU issuing-agency shapes and a bare
 * GTIN — the goal is to catch typos ("ABC"), not to validate check digits.
 */
package be.heyman.android.jemmapassdemo.pillars

object IpsUdi {

    /**
     * • GS1 human-readable form  `(01)<14 digits>` followed by optional AIs `(11)…`, `(17)…`, `(10)…`, `(21)…`
     * • HIBCC                    `+…`
     * • ICCBBA                   `=…`
     * • bare GTIN / DI           8 to 14 digits
     */
    private val UDI_REGEX = Regex("^(\\(01\\)\\d{14}(\\(\\d{2,4}\\)[^()\\s]+)*|\\+\\S{4,}|=\\S{4,}|\\d{8,14})$")

    /** Whitespace is ignored (cards often print groups separated by spaces). */
    fun normalize(raw: String?): String? = raw?.replace(Regex("\\s+"), "")?.takeIf { it.isNotBlank() }

    fun isPlausible(raw: String?): Boolean {
        val udi = normalize(raw) ?: return false
        return UDI_REGEX.matches(udi)
    }

    /** The GS1 device identifier (GTIN) when the UDI is in GS1 HRF form, else null. */
    fun gs1DeviceIdentifier(raw: String?): String? {
        val udi = normalize(raw) ?: return null
        return Regex("^\\(01\\)(\\d{14})").find(udi)?.groupValues?.get(1)
    }
}
