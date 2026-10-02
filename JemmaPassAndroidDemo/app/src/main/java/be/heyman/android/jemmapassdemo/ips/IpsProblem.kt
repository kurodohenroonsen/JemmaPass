/*
 * IpsProblem.kt — FHIR-native IPS pillar: Problem List (LOINC 11450-4, required IPS section).
 *
 * One entry = one FHIR R4 `Condition` (Condition-uv-ips, category problem-list-item)
 * that is current: clinicalStatus active | recurrence | relapse. Codes come from the
 * IPS problems free set (KB); free text is allowed.
 *
 * The Bundle is the source of truth; `_j.cn` stays the projection read by the KB
 * cross-checks (drug × disease), the Gemma tools and the QR / mesh channels — its
 * legacy shape (JCondition) is kept, with the onset date added (`dt`).
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JCondition
import java.util.UUID

/** Condition.clinicalStatus values of a *current* problem. */
object IpsProblemStatus {
    const val ACTIVE = "active"
    const val RECURRENCE = "recurrence"
    const val RELAPSE = "relapse"
    val ALL = listOf(ACTIVE, RECURRENCE, RELAPSE)

    /** Full codes, plus the legacy one-letter `_j` values ("A"). */
    fun normalize(raw: String?): String {
        val v = raw?.trim()?.lowercase()
        return when {
            v != null && v in ALL -> v
            else -> ACTIVE
        }
    }

    fun display(code: String): String = when (code) {
        RECURRENCE -> "Recurrence"
        RELAPSE -> "Relapse"
        else -> "Active"
    }
}

data class IpsProblem(
    val id: String,
    val code: String? = null,
    val system: String? = IpsCodeSystems.SNOMED,
    /** English display of the code (localised labels come from the KB at render time). */
    val display: String? = null,
    val text: String? = null,
    /** onsetDateTime: `YYYY`, `YYYY-MM` or `YYYY-MM-DD`; null = unknown. */
    val onset: String? = null,
    val clinicalStatus: String = IpsProblemStatus.ACTIVE,
    /** LOINC answer of condition-severity-uv-ips, or null. */
    val severity: String? = null,
    val note: String? = null,
) {
    fun label(): String =
        display?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() } ?: code.orEmpty()

    /** Legacy `_j.cn` shape (cross-checks, Gemma tools, QR / mesh). */
    fun toJCondition(): JCondition = JCondition(
        c = code?.takeIf { it.isNotBlank() },
        s = severityWord(severity),
        st = clinicalStatus,
        d = note?.takeIf { it.isNotBlank() },
        displayLabel = label().takeIf { it.isNotBlank() },
        date = onset?.takeIf { it.isNotBlank() },
        codeSystem = system?.takeIf { it.isNotBlank() && it != IpsCodeSystems.SNOMED },
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun severityWord(code: String?): String? = when (code) {
            IpsConditionSeverity.MILD -> "mild"
            IpsConditionSeverity.MODERATE -> "moderate"
            IpsConditionSeverity.SEVERE -> "severe"
            else -> null
        }

        /** "severe" / "LA6750-9" / legacy "H" → LOINC answer; unknown → null. */
        fun severityFromWord(raw: String?): String? {
            val v = raw?.trim()?.lowercase() ?: return null
            IpsConditionSeverity.normalize(raw.trim())?.let { return it }
            return when (v) {
                "severe", "high", "h", "s" -> IpsConditionSeverity.SEVERE
                "moderate" -> IpsConditionSeverity.MODERATE
                "mild", "low", "l" -> IpsConditionSeverity.MILD
                else -> null
            }
        }

        fun fromJCondition(c: JCondition, index: Int = 0): IpsProblem {
            val code = c.c?.takeIf { it.isNotBlank() }
            val seed = "cn|${index}|${code.orEmpty()}|${c.date.orEmpty()}|${c.displayLabel.orEmpty()}"
            return IpsProblem(
                id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(),
                code = code,
                system = c.codeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
                display = if (code != null) c.displayLabel?.takeIf { it.isNotBlank() } else null,
                text = if (code == null) c.displayLabel?.takeIf { it.isNotBlank() } else null,
                onset = c.date?.takeIf { it.isNotBlank() },
                clinicalStatus = IpsProblemStatus.normalize(c.st),
                severity = severityFromWord(c.s),
                note = c.d?.takeIf { it.isNotBlank() },
            )
        }
    }
}
