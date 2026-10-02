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
 *
 * UC-BLOOD-01.. — the derived Observation is recognised by what it is (882-1 with a bare
 * ABO/Rh coded value), not only by its id: an import through `_j.rs` gives it a new id
 * (IpsResult.fromJEntry) and it must still follow `p.bt`. While `p.bt` is a recognised
 * group it is the single 882-1 of the profile; a contradicting result entered by hand is
 * replaced and returned in [Reconciliation.conflicts] so the caller can tell the user.
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

    /** The entry carries the stable derived id (this profile's or the sender's). */
    fun isDerived(result: IpsResult): Boolean = result.id.startsWith(DERIVED_ID_PREFIX)

    /** Any ABO/Rh result: LOINC 882-1 (whoever wrote it) or the derived id. */
    fun isBloodGroup(result: IpsResult): Boolean =
        isDerived(result) || result.code?.trim() == LOINC_ABO_RH

    /** "Blood group O Rh(D) positive" → "O+" (the free-set display, e.g. a `_j.rs` value without its code). */
    fun labelFromSnomedDisplay(display: String?): String? {
        val t = display?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return SNOMED.entries.firstOrNull { it.value.second.equals(t, ignoreCase = true) }?.key
    }

    /** Canonical label ("O+") stated by a blood-group result, from its coded value or its text. */
    fun labelOf(result: IpsResult): String? =
        labelFromSnomed(result.valueCode?.trim())
            ?: labelFromSnomedDisplay(result.valueText)
            ?: labelFromSnomedDisplay(result.valueDisplay)
            ?: normalize(result.valueText)
            ?: normalize(result.valueDisplay)

    /**
     * Derivation rule: the mirror of `p.bt` is a 882-1 Observation holding nothing but a
     * coded ABO/Rh value under the derived label — no date, note, performer, interpretation,
     * range, text or numeric value, default status and category. That is exactly what
     * [derivedResult] produces and what survives a `_j.rs` round trip, whatever the id.
     * A result entered by hand with none of those details says nothing more than the
     * value, so treating it as the mirror loses nothing.
     */
    fun looksDerived(result: IpsResult): Boolean {
        if (isDerived(result)) return true
        if (result.code?.trim() != LOINC_ABO_RH) return false
        // The value is the free-set concept: its code, or its display when `_j.rs` lost the code.
        val coded = labelFromSnomed(result.valueCode?.trim()) != null
        val displayOnly = result.valueCode.isNullOrBlank() && labelFromSnomedDisplay(result.valueText) != null
        if (!coded && !displayOnly) return false
        val label = result.display?.trim().orEmpty()
        return (label.isEmpty() || label == DISPLAY_ABO_RH) &&
            result.text.isNullOrBlank() &&
            result.date.isNullOrBlank() &&
            result.status == IpsResultStatus.FINAL &&
            result.category == IpsResultCategory.LABORATORY &&
            result.value.isNullOrBlank() &&
            (displayOnly || result.valueText.isNullOrBlank()) &&
            result.interpretation.isNullOrBlank() &&
            result.refLow.isNullOrBlank() &&
            result.refHigh.isNullOrBlank() &&
            result.performer.isNullOrBlank() &&
            result.note.isNullOrBlank()
    }

    /**
     * True when [result] is a blood-group result entered by hand whose value is not the
     * profile's blood group (`p.bt` recognised). Saving it would not survive [sync]: a form
     * can call this before saving to refuse or explain.
     */
    fun contradictsProfile(result: IpsResult, bloodType: String?): Boolean {
        val expected = normalize(bloodType) ?: return false
        return isBloodGroup(result) && !looksDerived(result) && labelOf(result) != expected
    }

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

    /** Outcome of [reconcile]. */
    data class Reconciliation(
        /** The results to store: at most one 882-1 while `p.bt` is a recognised group. */
        val results: List<IpsResult>,
        /**
         * Blood-group results entered by hand that contradicted `p.bt` and were replaced by
         * the profile value. Never empty silently: the caller is expected to show them.
         */
        val conflicts: List<IpsResult> = emptyList(),
    ) {
        val hasConflict: Boolean get() = conflicts.isNotEmpty()
    }

    /**
     * Keeps the blood-group results in line with `p.bt`.
     *
     * `p.bt` recognised → exactly ONE 882-1 result, stating the profile's group:
     *   - a result entered by hand (date, laboratory, note…) with the same group is kept as
     *     is, in place, and no derived twin is added;
     *   - otherwise the derived result (stable id of this profile) is placed first;
     *   - every other blood-group result is dropped: derived copies whatever their id
     *     (stale value, import from another device), duplicates, and results entered by
     *     hand that contradict `p.bt` — those are returned in [Reconciliation.conflicts].
     *
     * `p.bt` absent or unreadable → no derived entry (the ones carrying the derived id are
     * removed); results entered by hand are left alone, nothing is invented.
     */
    fun reconcile(results: List<IpsResult>, profileId: String, bloodType: String?): Reconciliation {
        val expected = normalize(bloodType)
        val derived = derivedResult(profileId, bloodType)
        if (expected == null || derived == null) {
            return Reconciliation(results.filterNot { isDerived(it) })
        }
        val byHand = results.filter { isBloodGroup(it) && !looksDerived(it) }
        val keep = byHand.firstOrNull { labelOf(it) == expected }
        val conflicts = byHand.filter { labelOf(it) != expected }
        val synced = if (keep != null) {
            results.filter { !isBloodGroup(it) || it === keep }
        } else {
            listOf(derived) + results.filterNot { isBloodGroup(it) }
        }
        return Reconciliation(synced, conflicts)
    }

    /** [reconcile] without the conflict report (the repository write path). */
    fun sync(results: List<IpsResult>, profileId: String, bloodType: String?): List<IpsResult> =
        reconcile(results, profileId, bloodType).results
}
