/*
 * IpsFhirCodec.kt — FHIR R4 ⇄ domain mapping for the FHIR-native pillars.
 *
 * Pure Kotlin (no Android dependency) so the round trips are covered by
 * plain JVM unit tests. Built on the Kotlin FHIR SDK (dev.ohs.fhir) already
 * used by [be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder].
 *
 * NOTE: inside this file `String` is `dev.ohs.fhir.model.r4.String`;
 * Kotlin's string type is spelled `kotlin.String`.
 */
package be.heyman.android.jemmapassdemo.ips

import dev.ohs.fhir.model.r4.Annotation
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.Markdown
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.PositiveInt
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String
import dev.ohs.fhir.model.r4.Uri
import java.util.UUID

object IpsFhirCodec {

    const val LOINC_SECTION_IMMUNIZATIONS = "11369-6"
    const val TITLE_SECTION_IMMUNIZATIONS = "Immunizations"
    const val PROFILE_IMMUNIZATION_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"

    /** Placeholder used by FHIR when the occurrence date is genuinely unknown. */
    const val OCCURRENCE_UNKNOWN = "unknown"

    val json: FhirR4Json = FhirR4Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // ──────────────────────────────────────────────────────────────────────
    // Bundle level
    // ──────────────────────────────────────────────────────────────────────

    /** Parse a stored `{id}.fhir.json`; null when the text is not a FHIR Bundle. */
    fun parseBundle(text: kotlin.String?): Bundle? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(text) as? Bundle
        } catch (e: Throwable) {
            null
        }
    }

    fun resourcesOf(bundle: Bundle): List<Resource> = bundle.entry.mapNotNull { it.resource }

    fun immunizationsOf(bundle: Bundle): List<IpsImmunization> =
        resourcesOf(bundle).filterIsInstance<Immunization>().map { fromFhir(it) }

    /** Every FHIR-native pillar found in a stored Bundle. */
    fun nativeOf(bundle: Bundle): IpsNativePillars = IpsNativePillars(
        immunizations = immunizationsOf(bundle),
    )

    /** Deterministic `urn:uuid:` for intra-bundle references (stable across rebuilds). */
    fun stableUrn(seed: kotlin.String): kotlin.String =
        "urn:uuid:" + UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()

    fun immunizationUrn(profileSid: kotlin.String, immunizationId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Immunization|$immunizationId")

    // ──────────────────────────────────────────────────────────────────────
    // Immunization ⇄ IpsImmunization
    // ──────────────────────────────────────────────────────────────────────

    fun fromFhir(r: Immunization): IpsImmunization {
        val coding = r.vaccineCode.coding.firstOrNull()
        val code = coding?.code?.value?.takeIf { it.isNotBlank() }
        val display = coding?.display?.value?.takeIf { it.isNotBlank() }
        val text = r.vaccineCode.text?.value?.takeIf { it.isNotBlank() }
        val date = r.occurrence.asDateTime()?.value?.value?.toString()
        val protocol = r.protocolApplied.firstOrNull()
        return IpsImmunization(
            id = r.id ?: IpsImmunization.newId(),
            code = code,
            system = coding?.system?.value?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
            display = display,
            // Keep the free text only when it is not a mere copy of the coding display.
            text = text?.takeIf { it != display },
            date = date,
            status = IpsImmunizationStatus.normalize(r.status.value?.getCode()),
            doseNumber = protocol?.doseNumber?.asPositiveInt()?.value?.value,
            seriesDoses = protocol?.seriesDoses?.asPositiveInt()?.value?.value,
            lotNumber = r.lotNumber?.value?.takeIf { it.isNotBlank() },
            manufacturer = r.manufacturer?.display?.value?.takeIf { it.isNotBlank() },
            performer = r.performer.firstOrNull()?.actor?.display?.value?.takeIf { it.isNotBlank() },
            note = r.note.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
        )
    }

    fun toFhir(im: IpsImmunization, patientUrn: kotlin.String): Immunization.Builder {
        val statusCode = try {
            Immunization.ImmunizationStatusCodes.fromCode(IpsImmunizationStatus.normalize(im.status))
        } catch (e: IllegalArgumentException) {
            Immunization.ImmunizationStatusCodes.Completed
        }

        val vaccineCode = CodeableConcept.Builder().apply {
            if (im.hasCode) {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = im.system ?: IpsCodeSystems.SNOMED }
                    code = Code.Builder().apply { value = im.code }
                    im.display?.takeIf { it.isNotBlank() }?.let { d ->
                        display = String.Builder().apply { value = d }
                    }
                })
            }
            val label = im.text?.takeIf { it.isNotBlank() } ?: im.display?.takeIf { it.isNotBlank() }
            label?.let { text = String.Builder().apply { value = it } }
        }

        val occurrence: Immunization.Occurrence = im.date?.takeIf { it.isNotBlank() }?.let { d ->
            val parsed = try { FhirDateTime.fromString(d) } catch (e: Throwable) { null }
            parsed?.let { Immunization.Occurrence.DateTime(DateTime.Builder().apply { value = it }.build()) }
        } ?: Immunization.Occurrence.String(String.Builder().apply { value = OCCURRENCE_UNKNOWN }.build())

        return Immunization.Builder(
            Enumeration.of(statusCode, null),
            vaccineCode,
            Reference.Builder().apply { reference = String.Builder().apply { value = patientUrn } },
            occurrence,
        ).apply {
            id = im.id
            meta = Meta.Builder().apply {
                profile.add(Canonical.Builder().apply { value = PROFILE_IMMUNIZATION_UV_IPS })
            }
            im.lotNumber?.takeIf { it.isNotBlank() }?.let { lot ->
                lotNumber = String.Builder().apply { value = lot }
            }
            im.manufacturer?.takeIf { it.isNotBlank() }?.let { m ->
                manufacturer = Reference.Builder().apply { display = String.Builder().apply { value = m } }
            }
            im.performer?.takeIf { it.isNotBlank() }?.let { p ->
                performer.add(
                    Immunization.Performer.Builder(
                        Reference.Builder().apply { display = String.Builder().apply { value = p } }
                    )
                )
            }
            im.note?.takeIf { it.isNotBlank() }?.let { n ->
                note.add(Annotation.Builder(Markdown.Builder().apply { value = n }))
            }
            im.doseNumber?.takeIf { it > 0 }?.let { dn ->
                protocolApplied.add(
                    Immunization.ProtocolApplied.Builder(
                        Immunization.ProtocolApplied.DoseNumber.PositiveInt(
                            PositiveInt.Builder().apply { value = dn }.build()
                        )
                    ).apply {
                        im.seriesDoses?.takeIf { it > 0 }?.let { sd ->
                            seriesDoses = Immunization.ProtocolApplied.SeriesDoses.PositiveInt(
                                PositiveInt.Builder().apply { value = sd }.build()
                            )
                        }
                    }
                )
            }
        }
    }

    /** Composition section listing the immunization entries (null when empty). */
    fun immunizationSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? {
        if (entryUrns.isEmpty()) return null
        return Composition.Section.Builder().apply {
            title = String.Builder().apply { value = TITLE_SECTION_IMMUNIZATIONS }
            code = CodeableConcept.Builder().apply {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = IpsCodeSystems.LOINC }
                    code = Code.Builder().apply { value = LOINC_SECTION_IMMUNIZATIONS }
                    display = String.Builder().apply { value = "History of Immunization Narrative" }
                })
            }
            entry.addAll(entryUrns.map { urn ->
                Reference.Builder().apply { reference = String.Builder().apply { value = urn } }
            })
        }
    }

    /** Serialise one resource alone (debug / tests). */
    fun encode(resource: Resource): kotlin.String = json.encodeToString(resource)
}
