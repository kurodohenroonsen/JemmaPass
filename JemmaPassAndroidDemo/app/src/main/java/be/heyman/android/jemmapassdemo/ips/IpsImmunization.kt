/*
 * IpsImmunization.kt — FHIR-native IPS pillar: Immunizations.
 *
 * Domain model for one entry of the IPS "History of Immunization" section
 * (LOINC 11369-6). The FHIR R4 `Immunization` resource stored in the
 * profile Bundle is the source of truth; this class is the in-memory view
 * the UI edits, and `_j 1.2` only ever carries a pruned projection of it
 * (see [toJEntry] / [fromJEntry]).
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import java.util.UUID

object IpsCodeSystems {
    const val SNOMED = "http://snomed.info/sct"
    const val CVX = "http://hl7.org/fhir/sid/cvx"
    const val ATC = "http://www.whocc.no/atc"
    const val LOINC = "http://loinc.org"
}

object IpsImmunizationStatus {
    const val COMPLETED = "completed"
    const val NOT_DONE = "not-done"
    const val ENTERED_IN_ERROR = "entered-in-error"
    val ALL = listOf(COMPLETED, NOT_DONE, ENTERED_IN_ERROR)

    fun normalize(raw: String?): String = when (raw?.trim()?.lowercase()) {
        NOT_DONE -> NOT_DONE
        ENTERED_IN_ERROR -> ENTERED_IN_ERROR
        else -> COMPLETED
    }
}

data class IpsImmunization(
    /** Stable resource id (FHIR `Immunization.id`), never regenerated once assigned. */
    val id: String,
    /** Vaccine code — SNOMED CT vaccine product by default, CVX / ATC accepted. */
    val code: String? = null,
    val system: String? = IpsCodeSystems.SNOMED,
    /** Display captured at entry time (any language). */
    val display: String? = null,
    /** Free-text vaccine name used when no code was picked (`vaccineCode.text`). */
    val text: String? = null,
    /** Occurrence date: `YYYY`, `YYYY-MM` or `YYYY-MM-DD`; null = unknown date. */
    val date: String? = null,
    val status: String = IpsImmunizationStatus.COMPLETED,
    val doseNumber: Int? = null,
    val seriesDoses: Int? = null,
    val lotNumber: String? = null,
    val manufacturer: String? = null,
    val performer: String? = null,
    val note: String? = null,
) {
    /** Best human label available without any KB lookup. */
    fun label(): String =
        display?.takeIf { it.isNotBlank() }
            ?: text?.takeIf { it.isNotBlank() }
            ?: code.orEmpty()

    val hasCode: Boolean get() = !code.isNullOrBlank()

    /**
     * Compact `_j 1.2` projection carried by QR / mesh payloads. Defaults are
     * omitted (SNOMED system, "completed" status) to keep the QR small.
     */
    fun toJEntry(): JEntryGeneric = JEntryGeneric(
        c = code?.takeIf { it.isNotBlank() },
        d = note?.takeIf { it.isNotBlank() },
        displayLabel = label().takeIf { it.isNotBlank() },
        date = date?.takeIf { it.isNotBlank() },
        codeSystem = system?.takeIf { it.isNotBlank() && it != IpsCodeSystems.SNOMED },
        status = status.takeIf { it != IpsImmunizationStatus.COMPLETED },
        doseNumber = doseNumber,
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        /**
         * Rebuild an immunization from a legacy / imported `_j` entry. The id is
         * derived deterministically from the entry content and position so that
         * re-importing the same payload yields the same FHIR resource ids.
         */
        fun fromJEntry(entry: JEntryGeneric, index: Int = 0): IpsImmunization {
            val code = entry.c?.takeIf { it.isNotBlank() }
            val seed = "im|${index}|${code.orEmpty()}|${entry.date.orEmpty()}|${entry.displayLabel.orEmpty()}"
            return IpsImmunization(
                id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(),
                code = code,
                system = entry.codeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
                display = if (code != null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                text = if (code == null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                date = entry.date?.takeIf { it.isNotBlank() },
                status = IpsImmunizationStatus.normalize(entry.status),
                doseNumber = entry.doseNumber,
                note = entry.d?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/**
 * The FHIR-native pillars carried alongside the legacy `_j`-authored ones.
 * Grows one pillar per sprint (Immunizations, Procedures + Devices, Results, Past problems, …).
 */
data class IpsNativePillars(
    val immunizations: List<IpsImmunization> = emptyList(),
    val procedures: List<IpsProcedure> = emptyList(),
    val devices: List<IpsDevice> = emptyList(),
    val results: List<IpsResult> = emptyList(),
    val pastProblems: List<IpsPastProblem> = emptyList(),
    val problems: List<IpsProblem> = emptyList(),
    val pregnancy: List<IpsPregnancyObs> = emptyList(),
    val functional: List<IpsFunctional> = emptyList(),
) {
    val isEmpty: Boolean get() = immunizations.isEmpty() && procedures.isEmpty() && devices.isEmpty() &&
        results.isEmpty() && pastProblems.isEmpty() && problems.isEmpty() && pregnancy.isEmpty() && functional.isEmpty()

    companion object {
        val EMPTY = IpsNativePillars()

        /** Lossy rebuild from a `_j` profile's generic arrays (imports, legacy files). */
        fun fromJEntries(
            im: List<JEntryGeneric>,
            pr: List<JEntryGeneric> = emptyList(),
            dv: List<JEntryGeneric> = emptyList(),
            rs: List<JEntryGeneric> = emptyList(),
            ph: List<JEntryGeneric> = emptyList(),
            cn: List<be.heyman.android.jemmapassdemo.qr.JCondition> = emptyList(),
            pg: List<JEntryGeneric> = emptyList(),
            fs: List<JEntryGeneric> = emptyList(),
        ): IpsNativePillars = IpsNativePillars(
            immunizations = im.mapIndexed { i, e -> IpsImmunization.fromJEntry(e, i) },
            procedures = pr.mapIndexed { i, e -> IpsProcedure.fromJEntry(e, i) },
            devices = dv.mapIndexed { i, e -> IpsDevice.fromJEntry(e, i) },
            results = rs.mapIndexed { i, e -> IpsResult.fromJEntry(e, i) },
            pastProblems = ph.mapIndexed { i, e -> IpsPastProblem.fromJEntry(e, i) },
            problems = cn.mapIndexed { i, c -> IpsProblem.fromJCondition(c, i) },
            pregnancy = pg.mapIndexedNotNull { i, e -> IpsPregnancyObs.fromJEntry(e, i) },
            functional = fs.mapIndexed { i, e -> IpsFunctional.fromJEntry(e, i) },
        )
    }
}
