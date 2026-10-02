/*
 * IpsPastProblem.kt — FHIR-native IPS pillar: History of Past Illness (LOINC 11348-0).
 *
 * One entry = one FHIR R4 `Condition` (IPS profile Condition-uv-ips) that is no
 * longer active: clinicalStatus resolved | inactive | remission. Codes come from
 * the IPS `problems-snomed-ct-ips-free-set` (KB, 5 622 SNOMED CT concepts with
 * FR/JA… translations); free text is allowed (`Condition.code.text`).
 *
 * The Bundle is the source of truth; `_j 1.2` carries the pruned projection in `ph`.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import java.util.UUID

/** Condition.clinicalStatus values that make sense for a *past* problem. */
object IpsPastProblemStatus {
    const val SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-clinical"
    const val RESOLVED = "resolved"
    const val INACTIVE = "inactive"
    const val REMISSION = "remission"
    val ALL = listOf(RESOLVED, INACTIVE, REMISSION)

    fun normalize(raw: String?): String {
        val v = raw?.trim()?.lowercase()
        return if (v != null && v in ALL) v else RESOLVED
    }

    fun display(code: String): String = when (code) {
        INACTIVE -> "Inactive"
        REMISSION -> "Remission"
        else -> "Resolved"
    }
}

/** IPS `condition-severity-uv-ips` (LOINC answer list, present in the on-device KB). */
object IpsConditionSeverity {
    const val SYSTEM = IpsCodeSystems.LOINC
    const val MILD = "LA6752-5"
    const val MODERATE = "LA6751-7"
    const val SEVERE = "LA6750-9"
    val ALL = listOf(MILD, MODERATE, SEVERE)

    fun normalize(raw: String?): String? = raw?.trim()?.takeIf { it in ALL }

    fun display(code: String?): String? = when (code) {
        MILD -> "Mild"
        MODERATE -> "Moderate"
        SEVERE -> "Severe"
        else -> null
    }
}

data class IpsPastProblem(
    val id: String,
    /** SNOMED CT concept of the IPS problems free set; null for free text. */
    val code: String? = null,
    val system: String? = IpsCodeSystems.SNOMED,
    /** English display of the code (localised labels come from the KB at render time). */
    val display: String? = null,
    /** Free-text problem when no code (`Condition.code.text`). */
    val text: String? = null,
    /** onsetDateTime: `YYYY`, `YYYY-MM` or `YYYY-MM-DD`; null = unknown. */
    val onset: String? = null,
    /** abatementDateTime (resolution): same precision rules; null = unknown. */
    val abatement: String? = null,
    val clinicalStatus: String = IpsPastProblemStatus.RESOLVED,
    /** LOINC answer of condition-severity-uv-ips, or null. */
    val severity: String? = null,
    val note: String? = null,
) {
    fun label(): String =
        display?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() } ?: code.orEmpty()

    val hasCode: Boolean get() = !code.isNullOrBlank()

    fun toJEntry(): JEntryGeneric = JEntryGeneric(
        c = code?.takeIf { it.isNotBlank() },
        d = note?.takeIf { it.isNotBlank() },
        displayLabel = label().takeIf { it.isNotBlank() },
        date = onset?.takeIf { it.isNotBlank() },
        codeSystem = system?.takeIf { it.isNotBlank() && it != IpsCodeSystems.SNOMED },
        status = clinicalStatus.takeIf { it != IpsPastProblemStatus.RESOLVED },
        abatement = abatement?.takeIf { it.isNotBlank() },
        severity = severity?.takeIf { it.isNotBlank() },
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        /**
         * Resolution cannot precede onset. Partial dates compare on their common
         * prefix ("1995" vs "1995-07-12" is fine, "2001" vs "1999-06" is not).
         */
        fun isChronologyValid(onset: String?, abatement: String?): Boolean {
            if (onset.isNullOrBlank() || abatement.isNullOrBlank()) return true
            val n = minOf(onset.length, abatement.length)
            return abatement.take(n) >= onset.take(n)
        }

        fun fromJEntry(entry: JEntryGeneric, index: Int = 0): IpsPastProblem {
            val code = entry.c?.takeIf { it.isNotBlank() }
            val seed = "ph|${index}|${code.orEmpty()}|${entry.date.orEmpty()}|${entry.displayLabel.orEmpty()}"
            return IpsPastProblem(
                id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(),
                code = code,
                system = entry.codeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
                display = if (code != null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                text = if (code == null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                onset = entry.date?.takeIf { it.isNotBlank() },
                abatement = entry.abatement?.takeIf { it.isNotBlank() },
                clinicalStatus = IpsPastProblemStatus.normalize(entry.status),
                severity = IpsConditionSeverity.normalize(entry.severity),
                note = entry.d?.takeIf { it.isNotBlank() },
            )
        }

        fun fromJCondition(c: be.heyman.android.jemmapassdemo.qr.JCondition, index: Int = 0): IpsPastProblem {
            val code = c.c?.takeIf { it.isNotBlank() }
            val seed = "ph-cn|${index}|${code.orEmpty()}|${c.date.orEmpty()}|${c.displayLabel.orEmpty()}"
            return IpsPastProblem(
                id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(),
                code = code,
                system = c.codeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
                display = if (code != null) c.displayLabel?.takeIf { it.isNotBlank() } else null,
                text = if (code == null) c.displayLabel?.takeIf { it.isNotBlank() } else null,
                onset = c.date?.takeIf { it.isNotBlank() },
                clinicalStatus = IpsPastProblemStatus.normalize(c.st),
                severity = IpsProblem.severityFromWord(c.s),
                note = c.d?.takeIf { it.isNotBlank() },
            )
        }
    }
}
