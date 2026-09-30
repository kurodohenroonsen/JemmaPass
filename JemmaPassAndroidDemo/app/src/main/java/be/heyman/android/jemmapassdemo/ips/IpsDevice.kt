/*
 * IpsDevice.kt — FHIR-native IPS pillar: Medical Devices (LOINC 46264-8).
 *
 * One entry = one FHIR R4 `DeviceUseStatement` (IPS DeviceUseStatement-uv-ips)
 * pointing at one `Device` (Device-uv-ips) in the same Bundle. The Bundle is the
 * source of truth; `_j 1.2` carries the pruned projection in `dv`.
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import java.util.UUID

object IpsDeviceStatus {
    const val ACTIVE = "active"
    const val INACTIVE = "inactive"
    const val ENTERED_IN_ERROR = "entered-in-error"
    val ALL = listOf(ACTIVE, INACTIVE, ENTERED_IN_ERROR)

    fun normalize(raw: String?): String = when (raw?.trim()?.lowercase()) {
        INACTIVE, "completed", "stopped" -> INACTIVE
        ENTERED_IN_ERROR -> ENTERED_IN_ERROR
        else -> ACTIVE
    }
}

data class IpsDevice(
    val id: String,
    /** SNOMED CT device type (KB category "Device"); null for free text. */
    val code: String? = null,
    val system: String? = IpsCodeSystems.SNOMED,
    val display: String? = null,
    /** Free-text device when no code (`Device.type.text`). */
    val text: String? = null,
    /** UDI device identifier (GS1 GTIN / HIBCC / ICCBBA) or the human-readable carrier. */
    val udi: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val serial: String? = null,
    /** Implantation / start of use: `YYYY`, `YYYY-MM` or `YYYY-MM-DD`; null = unknown. */
    val date: String? = null,
    val status: String = IpsDeviceStatus.ACTIVE,
    val bodySite: String? = null,
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
        status = status.takeIf { it != IpsDeviceStatus.ACTIVE },
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJEntry(entry: JEntryGeneric, index: Int = 0): IpsDevice {
            val code = entry.c?.takeIf { it.isNotBlank() }
            val seed = "dv|${index}|${code.orEmpty()}|${entry.date.orEmpty()}|${entry.displayLabel.orEmpty()}"
            return IpsDevice(
                id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(),
                code = code,
                system = entry.codeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
                display = if (code != null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                text = if (code == null) entry.displayLabel?.takeIf { it.isNotBlank() } else null,
                date = entry.date?.takeIf { it.isNotBlank() },
                status = IpsDeviceStatus.normalize(entry.status),
                note = entry.d?.takeIf { it.isNotBlank() },
            )
        }
    }
}
