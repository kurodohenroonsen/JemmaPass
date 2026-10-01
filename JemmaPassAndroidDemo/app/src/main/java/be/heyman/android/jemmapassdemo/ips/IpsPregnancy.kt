/*
 * IpsPregnancy.kt — FHIR-native IPS pillar: History of Pregnancy (LOINC 10162-6).
 *
 * The IPS models this section as Observations of three kinds, all LOINC-coded and
 * present in the on-device KB value sets:
 *   • status  — Observation-pregnancy-status-uv-ips, code 82810-3, value = LA answer
 *               (pregnancy-status-uv-ips: Pregnant / Not pregnant / Unknown)
 *   • edd     — Observation-pregnancy-edd-uv-ips, code from edd-method-uv-ips, valueDateTime
 *   • outcome — Observation-pregnancy-outcome-uv-ips, code from pregnancies-summary-uv-ips
 *               ([#] Births.live, [#] Abortions…), valueInteger
 *
 * The Bundle is the source of truth; `_j.pg` carries the projection
 * (c = LOINC code, vc = status answer, v = EDD date or count, dt = effective date).
 */
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JEntryGeneric
import java.util.UUID

object IpsPregnancyCodes {
    const val STATUS = "82810-3"
    const val STATUS_DISPLAY = "Pregnancy status"

    const val PREGNANT = "LA15173-0"
    const val NOT_PREGNANT = "LA26683-5"
    const val UNKNOWN = "LA4489-6"
    val STATUS_ANSWERS: Map<String, String> = linkedMapOf(
        PREGNANT to "Pregnant",
        NOT_PREGNANT to "Not pregnant",
        UNKNOWN to "Unknown",
    )

    /** edd-method-uv-ips (KB). */
    val EDD_METHODS: Map<String, String> = linkedMapOf(
        "11778-8" to "Delivery date Estimated",
        "11779-6" to "Delivery date Estimated from last menstrual period",
        "11780-4" to "Delivery date Estimated from ovulation date",
    )

    /** pregnancies-summary-uv-ips (KB), in the order shown to the user. */
    val OUTCOMES: Map<String, String> = linkedMapOf(
        "11640-0" to "[#] Births total",
        "11636-8" to "[#] Births.live",
        "11639-2" to "[#] Births.term",
        "11637-6" to "[#] Births.preterm",
        "11638-4" to "[#] Births.still living",
        "11612-9" to "[#] Abortions",
        "11614-5" to "[#] Abortions.spontaneous",
        "11613-7" to "[#] Abortions.induced",
        "33065-4" to "[#] Ectopic pregnancy",
    )

    fun display(code: String?): String? = when (code) {
        STATUS -> STATUS_DISPLAY
        else -> EDD_METHODS[code] ?: OUTCOMES[code]
    }

    fun isPregnancyCode(code: String?): Boolean = code == STATUS || code in EDD_METHODS || code in OUTCOMES
}

enum class IpsPregnancyKind { STATUS, EDD, OUTCOME }

data class IpsPregnancyObs(
    val id: String,
    /** LOINC: 82810-3, an edd-method code or a pregnancies-summary code. */
    val code: String,
    /** Status answer (LA…) — STATUS only. */
    val valueCode: String? = null,
    /** Estimated delivery date YYYY-MM-DD — EDD only. */
    val valueDate: String? = null,
    /** Count — OUTCOME only. */
    val count: Int? = null,
    /** effectiveDateTime: when the status was recorded / the summary was made. */
    val date: String? = null,
    val note: String? = null,
) {
    val kind: IpsPregnancyKind
        get() = when (code) {
            IpsPregnancyCodes.STATUS -> IpsPregnancyKind.STATUS
            in IpsPregnancyCodes.EDD_METHODS -> IpsPregnancyKind.EDD
            else -> IpsPregnancyKind.OUTCOME
        }

    fun label(): String = IpsPregnancyCodes.display(code) ?: code

    /** "Pregnant", "2026-05-14", "2" … */
    fun valueLabel(): String = when (kind) {
        IpsPregnancyKind.STATUS -> IpsPregnancyCodes.STATUS_ANSWERS[valueCode] ?: valueCode.orEmpty()
        IpsPregnancyKind.EDD -> valueDate.orEmpty()
        IpsPregnancyKind.OUTCOME -> count?.toString().orEmpty()
    }

    fun toJEntry(): JEntryGeneric = JEntryGeneric(
        c = code,
        d = note?.takeIf { it.isNotBlank() },
        displayLabel = label(),
        date = date?.takeIf { it.isNotBlank() },
        valueCode = valueCode?.takeIf { kind == IpsPregnancyKind.STATUS && it.isNotBlank() },
        value = when (kind) {
            IpsPregnancyKind.EDD -> valueDate?.takeIf { it.isNotBlank() }
            IpsPregnancyKind.OUTCOME -> count?.toString()
            IpsPregnancyKind.STATUS -> null
        },
    )

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJEntry(e: JEntryGeneric, index: Int = 0): IpsPregnancyObs? {
            val code = e.c?.takeIf { IpsPregnancyCodes.isPregnancyCode(it) } ?: return null
            val seed = "pg|$index|$code|${e.date.orEmpty()}|${e.value.orEmpty()}|${e.valueCode.orEmpty()}"
            val obs = IpsPregnancyObs(id = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString(), code = code,
                date = e.date?.takeIf { it.isNotBlank() }, note = e.d?.takeIf { it.isNotBlank() })
            return when (obs.kind) {
                IpsPregnancyKind.STATUS -> obs.copy(valueCode = e.valueCode?.takeIf { it in IpsPregnancyCodes.STATUS_ANSWERS })
                IpsPregnancyKind.EDD -> obs.copy(valueDate = e.value?.takeIf { it.isNotBlank() })
                IpsPregnancyKind.OUTCOME -> obs.copy(count = e.value?.trim()?.toIntOrNull())
            }
        }
    }
}
