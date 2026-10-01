/*
 * IpsBloodGroup.kt — the patient's blood type as an IPS Results Observation.
 *
 * The hackathon build stored the blood type in a home-made Patient extension
 * (`http://jemmapass.net/fhir/StructureDefinition/blood-type`) that the HL7
 * validator rejects (device QA cycle 6). IPS expects it as an Observation
 * LOINC 882-1 "ABO and Rh group [Type] in Blood" whose value is a SNOMED CT
 * concept of the `results-blood-group-snomed-ct-ips-free-set` value set.
 *
 * `p.bt` (e.g. "O+") stays the patient-pillar source; one derived result with a
 * stable id is kept in sync by ProfilesRepository on every write.
 */
package be.heyman.android.jemmapassdemo.ips

object IpsBloodGroup {

    const val LOINC_ABO_RH = "882-1"
    const val DISPLAY_ABO_RH = "ABO and Rh blood group"
    const val DERIVED_ID_PREFIX = "rs-blood-group-"

    /** SNOMED CT codes confirmed against the on-device KB free set (device-reports/kb/kb-results-valuesets.txt). */
    private val SNOMED: Map<String, Pair<String, String>> = mapOf(
        "O+" to ("278147001" to "Blood group O Rh(D) positive"),
        "O-" to ("278148006" to "Blood group O Rh(D) negative"),
        "A+" to ("278149003" to "Blood group A Rh(D) positive"),
        "A-" to ("278152006" to "Blood group A Rh(D) negative"),
        "B+" to ("278150003" to "Blood group B Rh(D) positive"),
        "B-" to ("278153001" to "Blood group B Rh(D) negative"),
        "AB+" to ("278151004" to "Blood group AB Rh(D) positive"),
        "AB-" to ("278154007" to "Blood group AB Rh(D) negative"),
    )

    /** "o +", "O Rh+", "AB-", "A positive" → canonical "O+" … or null. */
    fun normalize(raw: String?): String? {
        val t = raw?.uppercase()?.replace(" ", "")?.replace("RH", "")?.replace("(D)", "") ?: return null
        val abo = Regex("^(AB|A|B|O)").find(t)?.value ?: return null
        val rest = t.removePrefix(abo)
        val rh = when {
            rest.startsWith("+") || rest.startsWith("POS") -> "+"
            rest.startsWith("-") || rest.startsWith("NEG") || rest.startsWith("−") -> "-"
            else -> return null
        }
        return abo + rh
    }

    fun snomedCode(raw: String?): String? = normalize(raw)?.let { SNOMED[it]?.first }

    /** "278147001" → "O+" (inverse lookup, null when not an ABO/Rh free-set code). */
    fun labelFromSnomed(code: String?): String? =
        if (code.isNullOrBlank()) null else SNOMED.entries.firstOrNull { it.value.first == code }?.key

    /** The 8 canonical labels, in picker order. */
    val LABELS: List<String> = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")
    fun snomedDisplay(raw: String?): String? = normalize(raw)?.let { SNOMED[it]?.second }

    fun derivedId(profileId: String): String = IpsFhirCodec.fhirId(DERIVED_ID_PREFIX + profileId)

    fun isDerived(result: IpsResult): Boolean = result.id.startsWith(DERIVED_ID_PREFIX)

    /** The Observation mirroring `p.bt`, or null when the blood type is unknown / unparsable. */
    fun derivedResult(profileId: String, bloodType: String?): IpsResult? {
        val canonical = normalize(bloodType) ?: return null
        val (code, display) = SNOMED[canonical] ?: return null
        return IpsResult(
            id = derivedId(profileId),
            code = LOINC_ABO_RH,
            system = IpsCodeSystems.LOINC,
            display = DISPLAY_ABO_RH,
            category = IpsResultCategory.LABORATORY,
            valueCode = code,
            valueCodeSystem = IpsCodeSystems.SNOMED,
            valueDisplay = display,
        )
    }

    /**
     * Keep exactly one blood-group result in sync with `p.bt`: the derived entry is
     * (re)placed first; a user-authored 882-1 result (different id) is left alone and
     * then suppresses the derived one to avoid duplicates.
     */
    fun sync(results: List<IpsResult>, profileId: String, bloodType: String?): List<IpsResult> {
        val others = results.filterNot { isDerived(it) }
        val userHasBloodGroup = others.any { it.code == LOINC_ABO_RH }
        val derived = if (userHasBloodGroup) null else derivedResult(profileId, bloodType)
        return if (derived == null) others else listOf(derived) + others
    }
}
