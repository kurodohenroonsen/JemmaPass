/*
 * IpsResult.kt — FHIR-native IPS pillar: Results (LOINC 30954-2, sprint 3).
 *
 * One entry = one FHIR R4 `Observation` (IPS profile Observation-results-uv-ips,
 * specialised as Observation-results-laboratory-uv-ips when the category is
 * "laboratory" and Observation-results-radiology-uv-ips when it is "imaging").
 * The Bundle is the source of truth; `_j 1.2` carries the pruned projection in `rs`
 * (`c` code · `d_display` label · `dt` date · `v` value · `u` unit · `ip`
 * interpretation · `rr` reference range · `vc` coded value · `ct` category).
 *
 * A result value is ONE of :
 *   • numeric  — [value] (decimal typed by the user) + [unit] (UCUM)   → valueQuantity
 *   • coded    — [valueCode] + [valueCodeSystem] + [valueDisplay]      → valueCodeableConcept
 *   • text     — [valueText]                                            → valueString
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import java.util.UUID

object IpsResultStatus {
    const val FINAL = "final"
    const val PRELIMINARY = "preliminary"
    const val AMENDED = "amended"
    const val CORRECTED = "corrected"
    const val CANCELLED = "cancelled"
    const val ENTERED_IN_ERROR = "entered-in-error"
    const val UNKNOWN = "unknown"
    val ALL = listOf(FINAL, PRELIMINARY, AMENDED, CORRECTED, CANCELLED, ENTERED_IN_ERROR, UNKNOWN)

    fun normalize(raw: String?): String {
        val v = raw?.trim()?.lowercase()
        return if (v != null && v in ALL) v else FINAL
    }
}

/** HL7 observation-category codes used by the Results pillar. */
object IpsResultCategory {
    const val LABORATORY = "laboratory"
    const val IMAGING = "imaging"
    const val PROCEDURE = "procedure"
    val ALL = listOf(LABORATORY, IMAGING, PROCEDURE)

    fun normalize(raw: String?): String {
        val v = raw?.trim()?.lowercase()
        return if (v != null && v in ALL) v else LABORATORY
    }
}

/** HL7 v3 ObservationInterpretation codes surfaced in the UI. */
object IpsResultInterpretation {
    const val NORMAL = "N"
    const val HIGH = "H"
    const val LOW = "L"
    const val CRITICAL_HIGH = "HH"
    const val CRITICAL_LOW = "LL"
    const val ABNORMAL = "A"
    const val POSITIVE = "POS"
    const val NEGATIVE = "NEG"
    val ALL = listOf(NORMAL, HIGH, LOW, CRITICAL_HIGH, CRITICAL_LOW, ABNORMAL, POSITIVE, NEGATIVE)

    /** null when blank or unknown (interpretation is optional). */
    fun normalize(raw: String?): String? {
        val v = raw?.trim()?.uppercase()
        return if (v != null && v in ALL) v else null
    }
}

data class IpsResult(
    val id: String,
    /** Observation code — LOINC for laboratory tests, SNOMED CT allowed; null for free text. */
    val code: String? = null,
    val system: String? = IpsCodeSystems.LOINC,
    val display: String? = null,
    /** Free-text test name when no code (`Observation.code.text`). */
    val text: String? = null,
    /** effectiveDateTime: `YYYY`, `YYYY-MM` or `YYYY-MM-DD`; null = unknown. */
    val date: String? = null,
    val status: String = IpsResultStatus.FINAL,
    val category: String = IpsResultCategory.LABORATORY,
    /** Numeric value exactly as typed ("5.4", "120"); null when the result is coded or textual. */
    val value: String? = null,
    /** UCUM unit code of [value]. */
    val unit: String? = null,
    /** Coded value (e.g. SNOMED CT blood group) — `valueCodeableConcept`. */
    val valueCode: String? = null,
    val valueCodeSystem: String? = IpsCodeSystems.SNOMED,
    val valueDisplay: String? = null,
    /** Free-text result — `valueString`. */
    val valueText: String? = null,
    /** v3 ObservationInterpretation code (H, L, N, …) or null. */
    val interpretation: String? = null,
    /** Reference range bounds as typed (same unit as [unit]); either may be null. */
    val refLow: String? = null,
    val refHigh: String? = null,
    /** Laboratory / radiologist / clinic (display only). */
    val performer: String? = null,
    val note: String? = null,
) {
    fun label(): String =
        display?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() } ?: code.orEmpty()

    val hasCode: Boolean get() = !code.isNullOrBlank()
    /** True when [value] parses as a decimal ("5.4", "5,4", "120"). */
    val isNumeric: Boolean get() = IpsDecimal.normalize(value) != null
    val isCoded: Boolean get() = !isNumeric && !valueCode.isNullOrBlank()

    /** "5.4 mmol/L", "O Rh(D) positive" or the free text — what a rescuer reads. */
    fun valueLabel(): String = when {
        isNumeric -> listOfNotNull(IpsDecimal.normalize(value), unit?.takeIf { it.isNotBlank() }).joinToString(" ")
        isCoded -> valueDisplay?.takeIf { it.isNotBlank() } ?: valueCode.orEmpty()
        value != null && value.isNotBlank() -> listOfNotNull(value, unit?.takeIf { it.isNotBlank() }).joinToString(" ")
        valueText != null && valueText.isNotBlank() -> valueText
        else -> ""
    }

    /** "3.5-5.1", "≥3.5", "≤5.1" or null. */
    fun referenceRangeLabel(): String? {
        val lo = refLow?.takeIf { it.isNotBlank() }
        val hi = refHigh?.takeIf { it.isNotBlank() }
        return when {
            lo != null && hi != null -> "$lo-$hi"
            lo != null -> "≥$lo"
            hi != null -> "≤$hi"
            else -> null
        }
    }

    fun toJEntry(): JEntryGeneric = JEntryGeneric(
        c = code?.takeIf { it.isNotBlank() },
        d = note?.takeIf { it.isNotBlank() },
        displayLabel = label().takeIf { it.isNotBlank() },
        date = date?.takeIf { it.isNotBlank() },
        codeSystem = system?.takeIf { it.isNotBlank() && it != IpsCodeSystems.LOINC },
        status = status.takeIf { it != IpsResultStatus.FINAL },
        value = when {
            isNumeric -> IpsDecimal.normalize(value)
            isCoded -> valueDisplay?.takeIf { it.isNotBlank() } ?: valueCode.orEmpty()
            value != null && value.isNotBlank() -> value
            valueText != null && valueText.isNotBlank() -> valueText
            else -> null
        },
        unit = unit?.takeIf { it.isNotBlank() },
        interpretation = IpsResultInterpretation.normalize(interpretation),
        referenceRange = referenceRangeLabel(),
        valueCode = if (isCoded) valueCode else null,
        valueCodeSystem = if (isCoded && valueCodeSystem != null && valueCodeSystem != IpsCodeSystems.SNOMED) valueCodeSystem else null,
        category = category.takeIf { it != IpsResultCategory.LABORATORY },
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        private val RANGE_BOTH = Regex("^\\s*([-+]?[0-9]+(?:[.,][0-9]+)?)\\s*-\\s*([-+]?[0-9]+(?:[.,][0-9]+)?)\\s*$")
        private val RANGE_LOW = Regex("^\\s*[≥>]=?\\s*([-+]?[0-9]+(?:[.,][0-9]+)?)\\s*$")
        private val RANGE_HIGH = Regex("^\\s*[≤<]=?\\s*([-+]?[0-9]+(?:[.,][0-9]+)?)\\s*$")

        /** Inverse of [referenceRangeLabel]: "3.5-5.1" → (3.5, 5.1), "≥3.5" → (3.5, null). */
        fun parseReferenceRange(raw: String?): Pair<String?, String?> {
            if (raw.isNullOrBlank()) return null to null
            RANGE_BOTH.find(raw)?.let { return it.groupValues[1] to it.groupValues[2] }
            RANGE_LOW.find(raw)?.let { return it.groupValues[1] to null }
            RANGE_HIGH.find(raw)?.let { return null to it.groupValues[1] }
            return null to null
        }

        /**
         * The id is derived from the entry content, so the blood-group Observation mirrored
         * from `p.bt` (stable id on the sender) comes back with another id: it is recognised
         * by its shape, see [IpsBloodGroup.looksDerived] / [IpsBloodGroup.reconcile].
         */
        fun fromJEntry(entry: JEntryGeneric, index: Int = 0): IpsResult {
            val code = entry.c?.takeIf { it.isNotBlank() }
            val seed = "rs|${index}|${code.orEmpty()}|${entry.date.orEmpty()}|${entry.displayLabel.orEmpty()}|${entry.value.orEmpty()}"
            val (lo, hi) = parseReferenceRange(entry.referenceRange)
            val rawValue = entry.value?.takeIf { it.isNotBlank() }
            val coded = entry.valueCode?.takeIf { it.isNotBlank() }
            val numeric = rawValue != null && coded == null && IpsDecimal.isDecimal(rawValue)
            return IpsResult(
                id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(),
                code = code,
                system = entry.codeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.LOINC,
                display = if (code != null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                text = if (code == null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                date = entry.date?.takeIf { it.isNotBlank() },
                status = IpsResultStatus.normalize(entry.status),
                category = IpsResultCategory.normalize(entry.category),
                value = if (numeric) rawValue else null,
                unit = entry.unit?.takeIf { it.isNotBlank() },
                valueCode = coded,
                valueCodeSystem = entry.valueCodeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
                valueDisplay = if (coded != null) rawValue else null,
                valueText = if (!numeric && coded == null) rawValue else null,
                interpretation = IpsResultInterpretation.normalize(entry.interpretation),
                refLow = lo,
                refHigh = hi,
                note = entry.d?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/** Decimal helpers shared by the form, the projection and the FHIR codec. */
object IpsDecimal {
    private val DECIMAL = Regex("^[-+]?[0-9]+([.,][0-9]+)?$")

    fun isDecimal(raw: String?): Boolean = raw != null && DECIMAL.matches(raw.trim())

    /** "5,4" → "5.4", " 120 " → "120"; null when not a decimal. */
    fun normalize(raw: String?): String? {
        val t = raw?.trim()?.replace(',', '.') ?: return null
        if (!DECIMAL.matches(t)) return null
        return t.removePrefix("+")
    }

    /** "120.0" → "120", "5.40" → "5.4", "0.50" → "0.5" (FHIR JSON always writes a fraction part). */
    fun trimZeros(plain: String): String =
        if (plain.contains('.')) plain.trimEnd('0').trimEnd('.') else plain
}

/** Parser for almost-numeric laboratory values: comparators ("<0.5", ">=10"), thousands spaces ("1 234,5"), trailing dot ("5."). */
object IpsAlmostNumeric {
    private val COMPARATOR_REGEX = Regex("^\\s*(<=|>=|<|>)\\s*(.*)$")

    data class ParsedQuantity(
        val comparator: String?,
        val numericString: String,
        val originalRaw: String,
    )

    fun parse(raw: String?): ParsedQuantity? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val compMatch = COMPARATOR_REGEX.find(trimmed)
        val comparator = compMatch?.groupValues?.get(1)
        val rest = (if (compMatch != null) compMatch.groupValues[2] else trimmed).trim()
        val normalized = rest.replace(" ", "").replace(',', '.')
            .let { if (it.endsWith(".")) it.dropLast(1) else it }
        if (IpsDecimal.isDecimal(normalized)) {
            val validDecimal = IpsDecimal.normalize(normalized) ?: return null
            return ParsedQuantity(
                comparator = comparator,
                numericString = validDecimal,
                originalRaw = trimmed,
            )
        }
        return null
    }
}
