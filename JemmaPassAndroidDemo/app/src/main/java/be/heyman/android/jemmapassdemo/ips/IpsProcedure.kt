/*
 * IpsProcedure.kt — FHIR-native IPS pillar: History of Procedures (LOINC 47519-4).
 *
 * One entry = one FHIR R4 `Procedure` (IPS profile Procedure-uv-ips). The Bundle
 * is the source of truth; `_j 1.2` carries the pruned projection in `pr`.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import java.util.UUID

object IpsProcedureStatus {
    const val COMPLETED = "completed"
    const val IN_PROGRESS = "in-progress"
    const val NOT_DONE = "not-done"
    const val STOPPED = "stopped"
    const val ENTERED_IN_ERROR = "entered-in-error"
    const val UNKNOWN = "unknown"
    val ALL = listOf(COMPLETED, IN_PROGRESS, NOT_DONE, STOPPED, ENTERED_IN_ERROR, UNKNOWN)

    fun normalize(raw: String?): String {
        val v = raw?.trim()?.lowercase()
        return if (v != null && v in ALL) v else COMPLETED
    }
}

data class IpsProcedure(
    val id: String,
    /** SNOMED CT procedure code (KB category "Procedure"); null for free text. */
    val code: String? = null,
    val system: String? = IpsCodeSystems.SNOMED,
    val display: String? = null,
    /** Free-text procedure when no code (`Procedure.code.text`). */
    val text: String? = null,
    /** performedDateTime: `YYYY`, `YYYY-MM` or `YYYY-MM-DD`; null = unknown. */
    val date: String? = null,
    val status: String = IpsProcedureStatus.COMPLETED,
    val bodySite: String? = null,
    val outcome: String? = null,
    val performer: String? = null,
    val location: String? = null,
    val note: String? = null,
) {
    fun label(): String =
        display?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() } ?: code.orEmpty()

    val hasCode: Boolean get() = !code.isNullOrBlank()

    fun toJEntry(): JEntryGeneric = JEntryGeneric(
        c = code?.takeIf { it.isNotBlank() },
        d = note?.takeIf { it.isNotBlank() },
        displayLabel = label().takeIf { it.isNotBlank() },
        date = date?.takeIf { it.isNotBlank() },
        codeSystem = system?.takeIf { it.isNotBlank() && it != IpsCodeSystems.SNOMED },
        status = status.takeIf { it != IpsProcedureStatus.COMPLETED },
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJEntry(entry: JEntryGeneric, index: Int = 0): IpsProcedure {
            val code = entry.c?.takeIf { it.isNotBlank() }
            val seed = "pr|${index}|${code.orEmpty()}|${entry.date.orEmpty()}|${entry.displayLabel.orEmpty()}"
            return IpsProcedure(
                id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(),
                code = code,
                system = entry.codeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
                display = if (code != null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                text = if (code == null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                date = entry.date?.takeIf { it.isNotBlank() },
                status = IpsProcedureStatus.normalize(entry.status),
                note = entry.d?.takeIf { it.isNotBlank() },
            )
        }
    }
}
