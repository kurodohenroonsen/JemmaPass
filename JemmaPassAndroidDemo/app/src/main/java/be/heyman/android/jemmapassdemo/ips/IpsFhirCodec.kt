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
import dev.ohs.fhir.model.r4.Device
import dev.ohs.fhir.model.r4.DeviceUseStatement
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.Markdown
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.PositiveInt
import dev.ohs.fhir.model.r4.Procedure
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

    const val LOINC_SECTION_PROCEDURES = "47519-4"
    const val TITLE_SECTION_PROCEDURES = "History of Procedures"
    const val PROFILE_PROCEDURE_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips"

    const val LOINC_SECTION_DEVICES = "46264-8"
    const val TITLE_SECTION_DEVICES = "Medical Devices"
    const val PROFILE_DEVICE_USE_STATEMENT_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/DeviceUseStatement-uv-ips"
    const val PROFILE_DEVICE_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Device-uv-ips"
    const val UDI_ISSUER_GS1 = "http://hl7.org/fhir/NamingSystem/gs1-di"

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

    fun proceduresOf(bundle: Bundle): List<IpsProcedure> =
        resourcesOf(bundle).filterIsInstance<Procedure>().map { fromFhir(it) }

    /** DeviceUseStatement + its Device (resolved through the statement's device reference / fullUrl). */
    fun devicesOf(bundle: Bundle): List<IpsDevice> {
        val byUrl: Map<kotlin.String, Resource> = bundle.entry
            .filter { it.fullUrl?.value != null && it.resource != null }
            .associate { it.fullUrl!!.value!! to it.resource!! }
        val byId: Map<kotlin.String, Device> = resourcesOf(bundle).filterIsInstance<Device>()
            .filter { it.id != null }.associateBy { it.id!! }
        return resourcesOf(bundle).filterIsInstance<DeviceUseStatement>().map { st ->
            val ref = st.device.reference?.value
            val device = (ref?.let { byUrl[it] } as? Device)
                ?: ref?.substringAfter("Device/", "")?.takeIf { it.isNotBlank() }?.let { byId[it] }
            fromFhir(st, device)
        }
    }

    /** Every FHIR-native pillar found in a stored Bundle. */
    fun nativeOf(bundle: Bundle): IpsNativePillars = IpsNativePillars(
        immunizations = immunizationsOf(bundle),
        procedures = proceduresOf(bundle),
        devices = devicesOf(bundle),
    )

    /** Deterministic `urn:uuid:` for intra-bundle references (stable across rebuilds). */
    fun stableUrn(seed: kotlin.String): kotlin.String =
        "urn:uuid:" + UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()

    fun immunizationUrn(profileSid: kotlin.String, immunizationId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Immunization|$immunizationId")

    fun procedureUrn(profileSid: kotlin.String, procedureId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Procedure|$procedureId")

    fun deviceUseStatementUrn(profileSid: kotlin.String, deviceEntryId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|DeviceUseStatement|$deviceEntryId")

    fun deviceUrn(profileSid: kotlin.String, deviceEntryId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Device|$deviceEntryId")

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

    // ──────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun codeableConcept(code: kotlin.String?, system: kotlin.String?, display: kotlin.String?, text: kotlin.String?): CodeableConcept.Builder =
        CodeableConcept.Builder().apply {
            if (!code.isNullOrBlank()) {
                coding.add(Coding.Builder().apply {
                    this.system = Uri.Builder().apply { value = system ?: IpsCodeSystems.SNOMED }
                    this.code = Code.Builder().apply { value = code }
                    display?.takeIf { it.isNotBlank() }?.let { d -> this.display = String.Builder().apply { value = d } }
                })
            }
            val label = text?.takeIf { it.isNotBlank() } ?: display?.takeIf { it.isNotBlank() }
            label?.let { this.text = String.Builder().apply { value = it } }
        }

    private fun textConcept(text: kotlin.String): CodeableConcept.Builder =
        CodeableConcept.Builder().apply { this.text = String.Builder().apply { value = text } }

    private fun displayReference(display: kotlin.String): Reference.Builder =
        Reference.Builder().apply { this.display = String.Builder().apply { value = display } }

    private fun urnReference(urn: kotlin.String): Reference.Builder =
        Reference.Builder().apply { reference = String.Builder().apply { value = urn } }

    private fun ipsMeta(profile: kotlin.String): Meta.Builder =
        Meta.Builder().apply { this.profile.add(Canonical.Builder().apply { value = profile }) }

    private fun parseFhirDate(date: kotlin.String?): FhirDateTime? =
        date?.takeIf { it.isNotBlank() }?.let { d -> try { FhirDateTime.fromString(d) } catch (e: Throwable) { null } }

    private fun dateTimeBuilder(fhirDate: FhirDateTime): DateTime.Builder =
        DateTime.Builder().apply { value = fhirDate }

    private fun firstCoding(cc: CodeableConcept?): Coding? = cc?.coding?.firstOrNull()

    // ──────────────────────────────────────────────────────────────────────
    // Procedure ⇄ IpsProcedure
    // ──────────────────────────────────────────────────────────────────────

    fun fromFhir(r: Procedure): IpsProcedure {
        val coding = firstCoding(r.code)
        val code = coding?.code?.value?.takeIf { it.isNotBlank() }
        val display = coding?.display?.value?.takeIf { it.isNotBlank() }
        val text = r.code?.text?.value?.takeIf { it.isNotBlank() }
        return IpsProcedure(
            id = r.id ?: IpsProcedure.newId(),
            code = code,
            system = coding?.system?.value?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
            display = display,
            text = text?.takeIf { it != display },
            date = r.performed?.asDateTime()?.value?.value?.toString(),
            status = IpsProcedureStatus.normalize(r.status.value?.getCode()),
            bodySite = r.bodySite.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
            outcome = r.outcome?.text?.value?.takeIf { it.isNotBlank() },
            performer = r.performer.firstOrNull()?.actor?.display?.value?.takeIf { it.isNotBlank() },
            location = r.location?.display?.value?.takeIf { it.isNotBlank() },
            note = r.note.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
        )
    }

    fun toFhir(pr: IpsProcedure, patientUrn: kotlin.String): Procedure.Builder {
        val statusCode = try {
            Procedure.EventStatus.fromCode(IpsProcedureStatus.normalize(pr.status))
        } catch (e: IllegalArgumentException) {
            Procedure.EventStatus.Completed
        }
        return Procedure.Builder(Enumeration.of(statusCode, null), urnReference(patientUrn)).apply {
            id = pr.id
            meta = ipsMeta(PROFILE_PROCEDURE_UV_IPS)
            code = codeableConcept(pr.code, pr.system, pr.display, pr.text)
            performed = parseFhirDate(pr.date)?.let { Procedure.Performed.DateTime(dateTimeBuilder(it).build()) }
                ?: Procedure.Performed.String(String.Builder().apply { value = OCCURRENCE_UNKNOWN }.build())
            pr.bodySite?.takeIf { it.isNotBlank() }?.let { bodySite.add(textConcept(it)) }
            pr.outcome?.takeIf { it.isNotBlank() }?.let { outcome = textConcept(it) }
            pr.performer?.takeIf { it.isNotBlank() }?.let { performer.add(Procedure.Performer.Builder(displayReference(it))) }
            pr.location?.takeIf { it.isNotBlank() }?.let { location = displayReference(it) }
            pr.note?.takeIf { it.isNotBlank() }?.let { note.add(Annotation.Builder(Markdown.Builder().apply { value = it })) }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // DeviceUseStatement + Device ⇄ IpsDevice
    // ──────────────────────────────────────────────────────────────────────

    fun fromFhir(st: DeviceUseStatement, device: Device?): IpsDevice {
        val coding = firstCoding(device?.type)
        val code = coding?.code?.value?.takeIf { it.isNotBlank() }
        val display = coding?.display?.value?.takeIf { it.isNotBlank() }
        val text = device?.type?.text?.value?.takeIf { it.isNotBlank() }
        val udi = device?.udiCarrier?.firstOrNull()
        val statusRaw = device?.status?.value?.getCode()
            ?: when (st.status.value) {
                DeviceUseStatement.DeviceUseStatementStatus.Active -> IpsDeviceStatus.ACTIVE
                DeviceUseStatement.DeviceUseStatementStatus.Entered_In_Error -> IpsDeviceStatus.ENTERED_IN_ERROR
                else -> IpsDeviceStatus.INACTIVE
            }
        return IpsDevice(
            id = st.id ?: IpsDevice.newId(),
            code = code,
            system = coding?.system?.value?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
            display = display,
            text = (text ?: device?.deviceName?.firstOrNull()?.name?.value)?.takeIf { it != display && !it.isNullOrBlank() },
            udi = (udi?.deviceIdentifier?.value ?: udi?.carrierHRF?.value)?.takeIf { it.isNotBlank() },
            manufacturer = device?.manufacturer?.value?.takeIf { it.isNotBlank() },
            model = device?.modelNumber?.value?.takeIf { it.isNotBlank() },
            serial = device?.serialNumber?.value?.takeIf { it.isNotBlank() },
            date = st.timing?.asDateTime()?.value?.value?.toString(),
            status = IpsDeviceStatus.normalize(statusRaw),
            bodySite = st.bodySite?.text?.value?.takeIf { it.isNotBlank() },
            note = st.note.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
        )
    }

    /** The Device resource of an entry (referenced by [toFhirUseStatement]). */
    fun toFhirDevice(dv: IpsDevice, patientUrn: kotlin.String): Device.Builder =
        Device.Builder().apply {
            id = "${dv.id}-device"
            meta = ipsMeta(PROFILE_DEVICE_UV_IPS)
            type = codeableConcept(dv.code, dv.system, dv.display, dv.text)
            status = Enumeration.of(
                when (IpsDeviceStatus.normalize(dv.status)) {
                    IpsDeviceStatus.INACTIVE -> Device.FHIRDeviceStatus.Inactive
                    IpsDeviceStatus.ENTERED_IN_ERROR -> Device.FHIRDeviceStatus.Entered_In_Error
                    else -> Device.FHIRDeviceStatus.Active
                },
                null,
            )
            patient = urnReference(patientUrn)
            dv.udi?.takeIf { it.isNotBlank() }?.let { u ->
                udiCarrier.add(Device.UdiCarrier.Builder().apply {
                    deviceIdentifier = String.Builder().apply { value = u }
                    issuer = Uri.Builder().apply { value = UDI_ISSUER_GS1 }
                    carrierHRF = String.Builder().apply { value = u }
                })
            }
            dv.manufacturer?.takeIf { it.isNotBlank() }?.let { manufacturer = String.Builder().apply { value = it } }
            dv.model?.takeIf { it.isNotBlank() }?.let { modelNumber = String.Builder().apply { value = it } }
            dv.serial?.takeIf { it.isNotBlank() }?.let { serialNumber = String.Builder().apply { value = it } }
            dv.text?.takeIf { it.isNotBlank() && !dv.hasCode }?.let { n ->
                deviceName.add(
                    Device.DeviceName.Builder(
                        String.Builder().apply { value = n },
                        Enumeration.of(Device.DeviceNameType.Patient_Reported_Name, null),
                    )
                )
            }
        }

    fun toFhirUseStatement(dv: IpsDevice, patientUrn: kotlin.String, deviceUrn: kotlin.String): DeviceUseStatement.Builder {
        val statusCode = when (IpsDeviceStatus.normalize(dv.status)) {
            IpsDeviceStatus.INACTIVE -> DeviceUseStatement.DeviceUseStatementStatus.Completed
            IpsDeviceStatus.ENTERED_IN_ERROR -> DeviceUseStatement.DeviceUseStatementStatus.Entered_In_Error
            else -> DeviceUseStatement.DeviceUseStatementStatus.Active
        }
        return DeviceUseStatement.Builder(
            Enumeration.of(statusCode, null),
            urnReference(patientUrn),
            urnReference(deviceUrn),
        ).apply {
            id = dv.id
            meta = ipsMeta(PROFILE_DEVICE_USE_STATEMENT_UV_IPS)
            parseFhirDate(dv.date)?.let { timing = DeviceUseStatement.Timing.DateTime(dateTimeBuilder(it).build()) }
            dv.bodySite?.takeIf { it.isNotBlank() }?.let { bodySite = textConcept(it) }
            dv.note?.takeIf { it.isNotBlank() }?.let { note.add(Annotation.Builder(Markdown.Builder().apply { value = it })) }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Composition sections
    // ──────────────────────────────────────────────────────────────────────

    private fun section(title: kotlin.String, loinc: kotlin.String, loincDisplay: kotlin.String, entryUrns: List<kotlin.String>): Composition.Section.Builder? {
        if (entryUrns.isEmpty()) return null
        return Composition.Section.Builder().apply {
            this.title = String.Builder().apply { value = title }
            code = CodeableConcept.Builder().apply {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = IpsCodeSystems.LOINC }
                    code = Code.Builder().apply { value = loinc }
                    display = String.Builder().apply { value = loincDisplay }
                })
            }
            entry.addAll(entryUrns.map { urn -> urnReference(urn) })
        }
    }

    /** Composition section listing the immunization entries (null when empty). */
    fun immunizationSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_IMMUNIZATIONS, LOINC_SECTION_IMMUNIZATIONS, "History of Immunization Narrative", entryUrns)

    fun procedureSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_PROCEDURES, LOINC_SECTION_PROCEDURES, "History of Procedures Document", entryUrns)

    fun deviceSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_DEVICES, LOINC_SECTION_DEVICES, "History of medical device use", entryUrns)

    /** Serialise one resource alone (debug / tests). */
    fun encode(resource: Resource): kotlin.String = json.encodeToString(resource)
}
