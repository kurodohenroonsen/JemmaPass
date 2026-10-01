/*
 * IpsFunctional.kt — FHIR-native IPS pillar: Functional Status (LOINC 47420-5).
 *
 * One entry = one FHIR R4 `Condition` (Condition-uv-ips) describing a disability, a
 * functional limitation or a reliance on an aid, listed in the 47420-5 section.
 * clinicalStatus: active (present) | inactive | resolved. SNOMED code from the IPS
 * problems free set (KB) or free text. `_j.fs` carries the projection.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import java.util.UUID

object IpsFunctionalStatus {
    const val ACTIVE = "active"
    const val INACTIVE = "inactive"
    const val RESOLVED = "resolved"
    val ALL = listOf(ACTIVE, INACTIVE, RESOLVED)

    fun normalize(raw: String?): String {
        val v = raw?.trim()?.lowercase()
        return if (v != null && v in ALL) v else ACTIVE
    }

    fun display(code: String): String = when (code) {
        INACTIVE -> "Inactive"
        RESOLVED -> "Resolved"
        else -> "Active"
    }
}

data class IpsFunctional(
    val id: String,
    val code: String? = null,
    val system: String? = IpsCodeSystems.SNOMED,
    val display: String? = null,
    val text: String? = null,
    /** onsetDateTime: `YYYY`, `YYYY-MM` or `YYYY-MM-DD`; null = unknown. */
    val onset: String? = null,
    val clinicalStatus: String = IpsFunctionalStatus.ACTIVE,
    val note: String? = null,
) {
    fun label(): String =
        display?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() } ?: code.orEmpty()

    fun toJEntry(): JEntryGeneric = JEntryGeneric(
        c = code?.takeIf { it.isNotBlank() },
        d = note?.takeIf { it.isNotBlank() },
        displayLabel = label().takeIf { it.isNotBlank() },
        date = onset?.takeIf { it.isNotBlank() },
        codeSystem = system?.takeIf { it.isNotBlank() && it != IpsCodeSystems.SNOMED },
        status = clinicalStatus.takeIf { it != IpsFunctionalStatus.ACTIVE },
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJEntry(e: JEntryGeneric, index: Int = 0): IpsFunctional {
            val code = e.c?.takeIf { it.isNotBlank() }
            val seed = "fs|$index|${code.orEmpty()}|${e.date.orEmpty()}|${e.displayLabel.orEmpty()}"
            return IpsFunctional(
                id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(),
                code = code,
                system = e.codeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
                display = if (code != null) e.displayLabel?.takeIf { it.isNotBlank() } else null,
                text = if (code == null) e.displayLabel?.takeIf { it.isNotBlank() } else null,
                onset = e.date?.takeIf { it.isNotBlank() },
                clinicalStatus = IpsFunctionalStatus.normalize(e.status),
                note = e.d?.takeIf { it.isNotBlank() },
            )
        }
    }
}
