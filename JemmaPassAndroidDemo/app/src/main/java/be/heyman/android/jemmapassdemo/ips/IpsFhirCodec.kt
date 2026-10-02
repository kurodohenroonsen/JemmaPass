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
import dev.ohs.fhir.model.r4.Condition
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Device
import dev.ohs.fhir.model.r4.DeviceUseStatement
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.Markdown
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.PositiveInt
import dev.ohs.fhir.model.r4.Procedure
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String
import dev.ohs.fhir.model.r4.Uri
import com.ionspin.kotlin.bignum.decimal.BigDecimal
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
    const val UDI_ISSUER_GS1 = "http://hl7.org/fhir/NamingSystem/gs1-di"  // spec value; not emitted (optional, unresolvable by validators)

    const val LOINC_SECTION_RESULTS = "30954-2"
    const val TITLE_SECTION_RESULTS = "Results"
    const val PROFILE_OBSERVATION_RESULTS_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-uv-ips"
    const val PROFILE_OBSERVATION_RESULTS_LABORATORY_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips"
    const val PROFILE_OBSERVATION_RESULTS_RADIOLOGY_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-radiology-uv-ips"
    const val SYSTEM_OBSERVATION_CATEGORY = "http://terminology.hl7.org/CodeSystem/observation-category"
    const val SYSTEM_OBSERVATION_INTERPRETATION = "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation"
    const val SYSTEM_UCUM = "http://unitsofmeasure.org"
    const val LOINC_SECTION_FUNCTIONAL = "47420-5"
    const val TITLE_SECTION_FUNCTIONAL = "Functional Status"

    const val LOINC_SECTION_PREGNANCY = "10162-6"
    const val TITLE_SECTION_PREGNANCY = "History of Pregnancy"
    const val PROFILE_PREGNANCY_STATUS_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-status-uv-ips"
    const val PROFILE_PREGNANCY_EDD_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-edd-uv-ips"
    const val PROFILE_PREGNANCY_OUTCOME_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-outcome-uv-ips"

    const val LOINC_SECTION_PROBLEMS = "11450-4"
    const val TITLE_SECTION_PROBLEMS = "Problems"
    const val SYSTEM_CONDITION_CATEGORY = "http://terminology.hl7.org/CodeSystem/condition-category"

    const val LOINC_SECTION_PAST_ILLNESS = "11348-0"
    const val TITLE_SECTION_PAST_ILLNESS = "History of Past Illness"
    const val PROFILE_CONDITION_UV_IPS =
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"

    const val EXT_DATA_ABSENT_REASON = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
    const val EXT_ORIGINAL_TEXT = "http://hl7.org/fhir/StructureDefinition/originalText"
    const val PATIENT_REPORTED = "Patient-reported"

    /** FHIR resource ids allow [A-Za-z0-9\-.]{1,64} only (profile sids carry underscores). */
    fun fhirId(raw: kotlin.String): kotlin.String =
        raw.replace(Regex("[^A-Za-z0-9.-]"), "-").take(64)

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

    /**
     * Observations of the Results pillar : profile `Observation-results-*` or category
     * laboratory / imaging / procedure. Vital signs and social history (future pillars)
     * are left alone.
     */
    fun resultsOf(bundle: Bundle): List<IpsResult> =
        resourcesOf(bundle).filterIsInstance<Observation>().filter { isResultsObservation(it) }.map { fromFhir(it) }

    fun isResultsObservation(o: Observation): Boolean {
        if (hasResultsProfile(o)) return true
        // UC-FHIR-025 — a pregnancy-coded Observation without a results profile belongs to
        // the pregnancy pillar only, whatever category an external system gave it.
        if (hasPregnancyCode(o)) return false
        val categories = o.category.flatMap { it.coding }.mapNotNull { it.code?.value }
        return categories.any { it in IpsResultCategory.ALL }
    }

    private fun hasResultsProfile(o: Observation): Boolean =
        o.meta?.profile?.any { it.value?.contains("Observation-results") == true } == true

    private fun hasPregnancyCode(o: Observation): Boolean =
        IpsPregnancyCodes.isPregnancyCode(o.code.coding.firstOrNull { it.system?.value == IpsCodeSystems.LOINC }?.code?.value)

    /**
     * Observations of the 🤰 pregnancy pillar. Disjoint from [resultsOf] (UC-FHIR-025): an
     * Observation that declares a results profile stays a result even with a pregnancy code,
     * so no resource is read into two pillars (and written back twice with the same id).
     */
    fun isPregnancyObservation(o: Observation): Boolean = hasPregnancyCode(o) && !hasResultsProfile(o)

    fun pregnancyOf(bundle: Bundle): List<IpsPregnancyObs> =
        resourcesOf(bundle).filterIsInstance<Observation>().filter { isPregnancyObservation(it) }.mapNotNull { pregnancyFromFhir(it) }

    /**
     * Conditions of the 📜 Past Problems pillar : the entries of the 11348-0 section,
     * plus (Bundles without that section) the Condition-uv-ips resources whose
     * clinicalStatus is resolved / inactive / remission. The legacy problem-list
     * Conditions (`_j.cn`, clinicalStatus active, no profile) are left alone.
     */
    fun pastProblemsOf(bundle: Bundle): List<IpsPastProblem> {
        val inSection: Set<kotlin.String> = resourcesOf(bundle).filterIsInstance<Composition>()
            .flatMap { it.section }
            .filter { s -> s.code?.coding?.any { it.code?.value == LOINC_SECTION_PAST_ILLNESS } == true }
            .flatMap { s -> s.entry.mapNotNull { it.reference?.value } }
            .toSet()
        val functional = sectionRefs(bundle, LOINC_SECTION_FUNCTIONAL)
        return bundle.entry.mapNotNull { e ->
            val c = e.resource as? Condition ?: return@mapNotNull null
            if (e.fullUrl?.value?.let { it in functional } == true) return@mapNotNull null
            val listed = e.fullUrl?.value?.let { it in inSection } == true
            if (isPastProblemCondition(c, listed)) fromFhir(c) else null
        }
    }

    private fun sectionRefs(bundle: Bundle, loinc: kotlin.String): Set<kotlin.String> =
        resourcesOf(bundle).filterIsInstance<Composition>()
            .flatMap { it.section }
            .filter { s -> s.code?.coding?.any { it.code?.value == loinc } == true }
            .flatMap { s -> s.entry.mapNotNull { it.reference?.value } }
            .toSet()

    /**
     * Conditions of the 🩺 Problem List pillar : the entries of the 11450-4 section
     * (including the legacy `_j.cn` Conditions written before sprint 5, which carry
     * no id — a stable one is derived from their fullUrl), plus Condition-uv-ips
     * resources whose clinicalStatus is active / recurrence / relapse.
     */
    fun problemsOf(bundle: Bundle): List<IpsProblem> {
        val inSection = sectionRefs(bundle, LOINC_SECTION_PROBLEMS)
        val functional = sectionRefs(bundle, LOINC_SECTION_FUNCTIONAL)
        return bundle.entry.mapNotNull { e ->
            val c = e.resource as? Condition ?: return@mapNotNull null
            val url = e.fullUrl?.value
            if (url != null && url in functional) return@mapNotNull null
            if (!isProblemCondition(c, url != null && url in inSection)) return@mapNotNull null
            problemFromFhir(c, fallbackId = url?.let { UUID.nameUUIDFromBytes(it.toByteArray(Charsets.UTF_8)).toString() })
        }
    }

    /** ♿ Functional status: the Conditions listed in the 47420-5 section, and only those. */
    fun functionalOf(bundle: Bundle): List<IpsFunctional> {
        val inSection = sectionRefs(bundle, LOINC_SECTION_FUNCTIONAL)
        return bundle.entry.mapNotNull { e ->
            val c = e.resource as? Condition ?: return@mapNotNull null
            if (e.fullUrl?.value?.let { it in inSection } != true) return@mapNotNull null
            functionalFromFhir(c)
        }
    }

    fun isProblemCondition(c: Condition, inSection: Boolean): Boolean {
        if (inSection) return true
        val ipsProfile = c.meta?.profile?.any { it.value == PROFILE_CONDITION_UV_IPS } == true
        val status = c.clinicalStatus?.coding?.firstOrNull()?.code?.value
        return ipsProfile && status in IpsProblemStatus.ALL
    }

    fun isPastProblemCondition(c: Condition, inSection: Boolean): Boolean {
        if (inSection) return true
        val ipsProfile = c.meta?.profile?.any { it.value == PROFILE_CONDITION_UV_IPS } == true
        val status = c.clinicalStatus?.coding?.firstOrNull()?.code?.value
        return ipsProfile && status in IpsPastProblemStatus.ALL
    }

    /** Every FHIR-native pillar found in a stored Bundle. */
    fun nativeOf(bundle: Bundle): IpsNativePillars = IpsNativePillars(
        immunizations = immunizationsOf(bundle),
        procedures = proceduresOf(bundle),
        devices = devicesOf(bundle),
        results = resultsOf(bundle),
        pastProblems = pastProblemsOf(bundle),
        problems = problemsOf(bundle),
        functional = functionalOf(bundle),
        pregnancy = pregnancyOf(bundle),
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

    fun resultUrn(profileSid: kotlin.String, resultId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Observation|$resultId")

    fun functionalUrn(profileSid: kotlin.String, entryId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Condition|functional|$entryId")

    fun pregnancyUrn(profileSid: kotlin.String, obsId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Observation|pregnancy|$obsId")

    fun problemUrn(profileSid: kotlin.String, problemId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Condition|problem|$problemId")

    fun pastProblemUrn(profileSid: kotlin.String, problemId: kotlin.String): kotlin.String =
        stableUrn("$profileSid|Condition|past|$problemId")

    // ──────────────────────────────────────────────────────────────────────
    // Immunization ⇄ IpsImmunization
    // ──────────────────────────────────────────────────────────────────────

    fun fromFhir(r: Immunization): IpsImmunization {
        val coding = r.vaccineCode.coding.firstOrNull()
        val code = coding?.code?.value?.takeIf { it.isNotBlank() }
        val (display, text) = IpsOfficialDisplays.friendly(
            coding?.system?.value, coding?.code?.value,
            coding?.display?.value?.takeIf { it.isNotBlank() }, r.vaccineCode.text?.value?.takeIf { it.isNotBlank() },
        )
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

        val vaccineCode = codeableConcept(if (im.hasCode) im.code else null, im.system, im.display, im.text)

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
            val doseNum = when {
                im.doseNumber != null && im.doseNumber > 0 ->
                    Immunization.ProtocolApplied.DoseNumber.PositiveInt(
                        PositiveInt.Builder().apply { value = im.doseNumber }.build()
                    )
                im.seriesDoses != null && im.seriesDoses > 0 ->
                    Immunization.ProtocolApplied.DoseNumber.String(
                        String.Builder().apply { value = OCCURRENCE_UNKNOWN }.build()
                    )
                else -> null
            }
            if (doseNum != null) {
                protocolApplied.add(
                    Immunization.ProtocolApplied.Builder(doseNum).apply {
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
                // Official term only when the friendly label can travel losslessly in `text`.
                val official = IpsOfficialDisplays.of(system ?: IpsCodeSystems.SNOMED, code)
                    ?.takeIf { text.isNullOrBlank() || text == display }
                coding.add(Coding.Builder().apply {
                    this.system = Uri.Builder().apply { value = system ?: IpsCodeSystems.SNOMED }
                    this.code = Code.Builder().apply { value = code }
                    (official ?: display?.takeIf { it.isNotBlank() })?.let { d -> this.display = String.Builder().apply { value = d } }
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
        val (display, text) = IpsOfficialDisplays.friendly(
            coding?.system?.value, coding?.code?.value,
            coding?.display?.value?.takeIf { it.isNotBlank() }, r.code?.text?.value?.takeIf { it.isNotBlank() },
        )
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
    // Observation (Results) ⇄ IpsResult
    // ──────────────────────────────────────────────────────────────────────

    fun fromFhir(o: Observation): IpsResult {
        val coding = firstCoding(o.code)
        val code = coding?.code?.value?.takeIf { it.isNotBlank() }
        val (display, text) = IpsOfficialDisplays.friendly(
            coding?.system?.value, coding?.code?.value,
            coding?.display?.value?.takeIf { it.isNotBlank() }, o.code.text?.value?.takeIf { it.isNotBlank() },
        )
        val category = o.category.flatMap { it.coding }.mapNotNull { it.code?.value }.firstOrNull { it in IpsResultCategory.ALL }
        val quantity = o.value?.asQuantity()?.value
        val codedValue = o.value?.asCodeableConcept()?.value
        val codedCoding = firstCoding(codedValue)
        val stringValue = o.value?.asString()?.value?.value
        val range = o.referenceRange.firstOrNull()
        return IpsResult(
            id = o.id ?: IpsResult.newId(),
            code = code,
            system = coding?.system?.value?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.LOINC,
            display = display,
            text = text?.takeIf { it != display },
            date = o.effective?.asDateTime()?.value?.value?.toString(),
            status = IpsResultStatus.normalize(o.status.value?.getCode()),
            category = IpsResultCategory.normalize(category),
            value = quantity?.extension?.firstOrNull { it.url == EXT_ORIGINAL_TEXT }
                ?.value?.asString()?.value?.value
                ?: run {
                    val comp = quantity?.comparator?.value?.getCode()
                    val num = quantity?.value?.value?.let { IpsDecimal.trimZeros(it.toStringExpanded()) }
                    if (comp != null && num != null) "$comp$num" else num
                },
            unit = (quantity?.code?.value ?: quantity?.unit?.value)?.takeIf { it.isNotBlank() },
            valueCode = codedCoding?.code?.value?.takeIf { it.isNotBlank() },
            valueCodeSystem = codedCoding?.system?.value?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
            valueDisplay = (codedCoding?.display?.value ?: codedValue?.text?.value)?.takeIf { it.isNotBlank() },
            valueText = stringValue?.takeIf { it.isNotBlank() },
            interpretation = IpsResultInterpretation.normalize(o.interpretation.flatMap { it.coding }.firstOrNull()?.code?.value),
            refLow = range?.low?.extension?.firstOrNull { it.url == EXT_ORIGINAL_TEXT }
                ?.value?.asString()?.value?.value
                ?: range?.low?.value?.value?.let { IpsDecimal.trimZeros(it.toStringExpanded()) },
            refHigh = range?.high?.extension?.firstOrNull { it.url == EXT_ORIGINAL_TEXT }
                ?.value?.asString()?.value?.value
                ?: range?.high?.value?.value?.let { IpsDecimal.trimZeros(it.toStringExpanded()) },
            performer = o.performer.firstOrNull()
                ?.takeUnless { it.reference?.value != null && it.reference?.value == o.subject?.reference?.value }
                ?.display?.value?.takeIf { it.isNotBlank() },
            note = o.note.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
        )
    }

    fun toFhir(rs: IpsResult, patientUrn: kotlin.String): Observation.Builder {
        val statusCode = try {
            Observation.ObservationStatus.fromCode(IpsResultStatus.normalize(rs.status))
        } catch (e: IllegalArgumentException) {
            Observation.ObservationStatus.Final
        }
        val category = IpsResultCategory.normalize(rs.category)
        // Radiology profile constraint ips-1: effectiveDateTime precise to the day (device QA
        // cycle 14). An imaging result without a full date falls back to the generic results profile.
        val dayPrecise = rs.date?.matches(Regex("^\\d{4}-\\d{2}-\\d{2}.*")) == true
        val profile = when (category) {
            IpsResultCategory.IMAGING -> if (dayPrecise) PROFILE_OBSERVATION_RESULTS_RADIOLOGY_UV_IPS else PROFILE_OBSERVATION_RESULTS_UV_IPS
            IpsResultCategory.LABORATORY -> PROFILE_OBSERVATION_RESULTS_LABORATORY_UV_IPS
            else -> PROFILE_OBSERVATION_RESULTS_UV_IPS
        }
        return Observation.Builder(
            Enumeration.of(statusCode, null),
            codeableConcept(rs.code, rs.system ?: IpsCodeSystems.LOINC, rs.display, rs.text),
        ).apply {
            id = fhirId(rs.id)
            meta = ipsMeta(profile)
            this.category.add(codeableConcept(category, SYSTEM_OBSERVATION_CATEGORY, categoryDisplay(category), null))
            subject = urnReference(patientUrn)
            // IPS results profiles: effective[x] 1..1 and performer 1..* (HL7 validator, cycle 7).
            effective = Observation.Effective.DateTime(
                (parseFhirDate(rs.date)?.let { dateTimeBuilder(it) } ?: unknownDateTimeBuilder()).build()
            )
            value = observationValue(rs)
            IpsResultInterpretation.normalize(rs.interpretation)?.let { ip ->
                interpretation.add(codeableConcept(ip, SYSTEM_OBSERVATION_INTERPRETATION, interpretationDisplay(ip), null))
            }
            val lo = IpsDecimal.normalize(rs.refLow)
            val hi = IpsDecimal.normalize(rs.refHigh)
            if (lo != null || hi != null) {
                referenceRange.add(Observation.ReferenceRange.Builder().apply {
                    lo?.let { low = quantity(it, rs.unit) }
                    hi?.let { high = quantity(it, rs.unit) }
                })
            }
            performer.add(
                rs.performer?.takeIf { it.isNotBlank() }?.let { displayReference(it) }
                    ?: urnReference(patientUrn).apply { display = String.Builder().apply { value = PATIENT_REPORTED } }
            )
            rs.note?.takeIf { it.isNotBlank() }?.let { note.add(Annotation.Builder(Markdown.Builder().apply { value = it })) }
        }
    }

    /** `_effectiveDateTime` carrying only a data-absent-reason "unknown" extension. */
    private fun unknownDateTimeBuilder(): DateTime.Builder = DateTime.Builder().apply {
        extension.add(Extension.Builder(EXT_DATA_ABSENT_REASON).apply {
            value = Extension.Value.Code(Code.Builder().apply { value = "unknown" }.build())
        })
    }

    private fun observationValue(rs: IpsResult): Observation.Value? {
        val almost = IpsAlmostNumeric.parse(rs.value)
        return when {
            almost != null -> Observation.Value.Quantity(
                quantity(almost.numericString, rs.unit, almost.comparator, almost.originalRaw).build()
            )
            !rs.valueCode.isNullOrBlank() ->
                Observation.Value.CodeableConcept(codeableConcept(rs.valueCode, rs.valueCodeSystem ?: IpsCodeSystems.SNOMED, rs.valueDisplay, null).build())
            !rs.valueText.isNullOrBlank() -> Observation.Value.String(String.Builder().apply { value = rs.valueText }.build())
            !rs.value.isNullOrBlank() -> Observation.Value.String(String.Builder().apply { value = rs.value }.build())
            else -> null
        }
    }

    /** UCUM quantity : `value` as an exact decimal, `code` = `unit` = the UCUM code. */
    private fun quantity(
        decimal: kotlin.String,
        ucum: kotlin.String?,
        comparatorCode: kotlin.String? = null,
        rawTyped: kotlin.String? = null,
    ): Quantity.Builder =
        Quantity.Builder().apply {
            value = Decimal.Builder().apply { value = BigDecimal.parseString(decimal) }
            comparatorCode?.let { comp ->
                try {
                    comparator = Enumeration.of(Quantity.QuantityComparator.fromCode(comp), null)
                } catch (_: Exception) {}
            }
            ucum?.takeIf { it.isNotBlank() }?.let { u ->
                unit = String.Builder().apply { value = u }
                system = Uri.Builder().apply { value = SYSTEM_UCUM }
                code = Code.Builder().apply { value = u }
            }
            if (comparatorCode != null && rawTyped != null) {
                extension.add(Extension.Builder(EXT_ORIGINAL_TEXT).apply {
                    value = Extension.Value.String(String.Builder().apply { value = rawTyped }.build())
                })
            } else if (decimal.filter { it.isDigit() }.length > 15) {
                val extreme = rawTyped ?: decimal
                extension.add(Extension.Builder(EXT_ORIGINAL_TEXT).apply {
                    value = Extension.Value.String(String.Builder().apply { value = extreme }.build())
                })
            }
        }

    private fun categoryDisplay(code: kotlin.String): kotlin.String = when (code) {
        IpsResultCategory.LABORATORY -> "Laboratory"
        IpsResultCategory.IMAGING -> "Imaging"
        IpsResultCategory.PROCEDURE -> "Procedure"
        else -> code
    }

    private fun interpretationDisplay(code: kotlin.String): kotlin.String = when (code) {
        IpsResultInterpretation.NORMAL -> "Normal"
        IpsResultInterpretation.HIGH -> "High"
        IpsResultInterpretation.LOW -> "Low"
        IpsResultInterpretation.CRITICAL_HIGH -> "Critical high"
        IpsResultInterpretation.CRITICAL_LOW -> "Critical low"
        IpsResultInterpretation.ABNORMAL -> "Abnormal"
        IpsResultInterpretation.POSITIVE -> "Positive"
        IpsResultInterpretation.NEGATIVE -> "Negative"
        else -> code
    }

    // ──────────────────────────────────────────────────────────────────────
    // Condition (past illness) ⇄ IpsPastProblem
    // ──────────────────────────────────────────────────────────────────────

    fun fromFhir(c: Condition): IpsPastProblem {
        val coding = firstCoding(c.code)
        val code = coding?.code?.value?.takeIf { it.isNotBlank() }
        val (display, text) = IpsOfficialDisplays.friendly(
            coding?.system?.value, coding?.code?.value,
            coding?.display?.value?.takeIf { it.isNotBlank() }, c.code?.text?.value?.takeIf { it.isNotBlank() },
        )
        return IpsPastProblem(
            id = c.id ?: IpsPastProblem.newId(),
            code = code,
            system = coding?.system?.value?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
            display = display,
            text = text?.takeIf { it != display },
            onset = c.onset?.asDateTime()?.value?.value?.toString(),
            abatement = c.abatement?.asDateTime()?.value?.value?.toString(),
            clinicalStatus = IpsPastProblemStatus.normalize(firstCoding(c.clinicalStatus)?.code?.value),
            severity = IpsConditionSeverity.normalize(firstCoding(c.severity)?.code?.value),
            note = c.note.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
        )
    }

    fun toFhir(pp: IpsPastProblem, patientUrn: kotlin.String): Condition.Builder {
        val status = IpsPastProblemStatus.normalize(pp.clinicalStatus)
        return Condition.Builder(urnReference(patientUrn)).apply {
            id = pp.id
            meta = ipsMeta(PROFILE_CONDITION_UV_IPS)
            clinicalStatus = CodeableConcept.Builder().apply {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = IpsPastProblemStatus.SYSTEM }
                    code = Code.Builder().apply { value = status }
                    display = String.Builder().apply { value = IpsPastProblemStatus.display(status) }
                })
            }
            IpsConditionSeverity.normalize(pp.severity)?.let { sv ->
                severity = CodeableConcept.Builder().apply {
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = IpsConditionSeverity.SYSTEM }
                        code = Code.Builder().apply { value = sv }
                        IpsConditionSeverity.display(sv)?.let { d -> display = String.Builder().apply { value = d } }
                    })
                }
            }
            code = codeableConcept(pp.code, pp.system, pp.display, pp.text)
            parseFhirDate(pp.onset)?.let { onset = Condition.Onset.DateTime(dateTimeBuilder(it).build()) }
            parseFhirDate(pp.abatement)?.let { abatement = Condition.Abatement.DateTime(dateTimeBuilder(it).build()) }
            pp.note?.takeIf { it.isNotBlank() }?.let { note.add(Annotation.Builder(Markdown.Builder().apply { value = it })) }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Condition (functional status) ⇄ IpsFunctional
    // ──────────────────────────────────────────────────────────────────────

    fun functionalFromFhir(c: Condition): IpsFunctional {
        val coding = firstCoding(c.code)
        val code = coding?.code?.value?.takeIf { it.isNotBlank() }
        val (display, text) = IpsOfficialDisplays.friendly(
            coding?.system?.value, coding?.code?.value,
            coding?.display?.value?.takeIf { it.isNotBlank() }, c.code?.text?.value?.takeIf { it.isNotBlank() },
        )
        return IpsFunctional(
            id = c.id ?: IpsFunctional.newId(),
            code = code,
            system = coding?.system?.value?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
            display = display,
            text = text?.takeIf { it != display },
            onset = c.onset?.asDateTime()?.value?.value?.toString(),
            clinicalStatus = IpsFunctionalStatus.normalize(firstCoding(c.clinicalStatus)?.code?.value),
            note = c.note.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
        )
    }

    fun toFhir(fs: IpsFunctional, patientUrn: kotlin.String): Condition.Builder {
        val status = IpsFunctionalStatus.normalize(fs.clinicalStatus)
        return Condition.Builder(urnReference(patientUrn)).apply {
            id = fs.id
            meta = ipsMeta(PROFILE_CONDITION_UV_IPS)
            clinicalStatus = CodeableConcept.Builder().apply {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = IpsPastProblemStatus.SYSTEM }
                    code = Code.Builder().apply { value = status }
                    display = String.Builder().apply { value = IpsFunctionalStatus.display(status) }
                })
            }
            code = codeableConcept(fs.code, fs.system, fs.display, fs.text)
            parseFhirDate(fs.onset)?.let { onset = Condition.Onset.DateTime(dateTimeBuilder(it).build()) }
            fs.note?.takeIf { it.isNotBlank() }?.let { note.add(Annotation.Builder(Markdown.Builder().apply { value = it })) }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Observation (pregnancy status / EDD / outcome) ⇄ IpsPregnancyObs
    // ──────────────────────────────────────────────────────────────────────

    /** Null when the Observation is not one of the pregnancy section codes. */
    fun pregnancyFromFhir(o: Observation): IpsPregnancyObs? {
        val code = o.code.coding.firstOrNull { it.system?.value == IpsCodeSystems.LOINC }?.code?.value
            ?.takeIf { IpsPregnancyCodes.isPregnancyCode(it) } ?: return null
        val base = IpsPregnancyObs(
            id = o.id ?: IpsPregnancyObs.newId(),
            code = code,
            date = o.effective?.asDateTime()?.value?.value?.toString(),
            note = o.note.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
        )
        return when (base.kind) {
            IpsPregnancyKind.STATUS -> base.copy(valueCode = o.value?.asCodeableConcept()?.value?.coding?.firstOrNull()?.code?.value)
            IpsPregnancyKind.EDD -> base.copy(valueDate = o.value?.asDateTime()?.value?.value?.toString())
            IpsPregnancyKind.OUTCOME -> base.copy(count = o.value?.asInteger()?.value?.value)
        }
    }

    fun toFhir(pg: IpsPregnancyObs, patientUrn: kotlin.String): Observation.Builder {
        val profile = when (pg.kind) {
            IpsPregnancyKind.STATUS -> PROFILE_PREGNANCY_STATUS_UV_IPS
            IpsPregnancyKind.EDD -> PROFILE_PREGNANCY_EDD_UV_IPS
            IpsPregnancyKind.OUTCOME -> PROFILE_PREGNANCY_OUTCOME_UV_IPS
        }
        return Observation.Builder(
            Enumeration.of(Observation.ObservationStatus.Final, null),
            codeableConcept(pg.code, IpsCodeSystems.LOINC, IpsPregnancyCodes.display(pg.code), null),
        ).apply {
            id = fhirId(pg.id)
            meta = ipsMeta(profile)
            subject = urnReference(patientUrn)
            effective = Observation.Effective.DateTime(
                (parseFhirDate(pg.date)?.let { dateTimeBuilder(it) } ?: unknownDateTimeBuilder()).build()
            )
            value = when (pg.kind) {
                IpsPregnancyKind.STATUS -> pg.valueCode?.takeIf { it.isNotBlank() }?.let {
                    Observation.Value.CodeableConcept(codeableConcept(it, IpsCodeSystems.LOINC, IpsPregnancyCodes.STATUS_ANSWERS[it], null).build())
                }
                IpsPregnancyKind.EDD -> parseFhirDate(pg.valueDate)?.let { Observation.Value.DateTime(dateTimeBuilder(it).build()) }
                IpsPregnancyKind.OUTCOME -> pg.count?.let { n -> Observation.Value.Integer(dev.ohs.fhir.model.r4.Integer.Builder().apply { value = n }.build()) }
            }
            pg.note?.takeIf { it.isNotBlank() }?.let { note.add(Annotation.Builder(Markdown.Builder().apply { value = it })) }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Condition (problem list) ⇄ IpsProblem
    // ──────────────────────────────────────────────────────────────────────

    fun problemFromFhir(c: Condition, fallbackId: kotlin.String? = null): IpsProblem {
        val coding = firstCoding(c.code)
        val code = coding?.code?.value?.takeIf { it.isNotBlank() }
        val (display, text) = IpsOfficialDisplays.friendly(
            coding?.system?.value, coding?.code?.value,
            coding?.display?.value?.takeIf { it.isNotBlank() }, c.code?.text?.value?.takeIf { it.isNotBlank() },
        )
        return IpsProblem(
            id = c.id ?: fallbackId ?: IpsProblem.newId(),
            code = code,
            system = coding?.system?.value?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED,
            display = display,
            text = text?.takeIf { it != display },
            onset = c.onset?.asDateTime()?.value?.value?.toString(),
            clinicalStatus = IpsProblemStatus.normalize(firstCoding(c.clinicalStatus)?.code?.value),
            severity = IpsConditionSeverity.normalize(firstCoding(c.severity)?.code?.value),
            note = c.note.firstOrNull()?.text?.value?.takeIf { it.isNotBlank() },
        )
    }

    fun toFhir(p: IpsProblem, patientUrn: kotlin.String): Condition.Builder {
        val status = IpsProblemStatus.normalize(p.clinicalStatus)
        return Condition.Builder(urnReference(patientUrn)).apply {
            id = p.id
            meta = ipsMeta(PROFILE_CONDITION_UV_IPS)
            clinicalStatus = CodeableConcept.Builder().apply {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = IpsPastProblemStatus.SYSTEM }
                    code = Code.Builder().apply { value = status }
                    display = String.Builder().apply { value = IpsProblemStatus.display(status) }
                })
            }
            category.add(CodeableConcept.Builder().apply {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = SYSTEM_CONDITION_CATEGORY }
                    code = Code.Builder().apply { value = "problem-list-item" }
                    display = String.Builder().apply { value = "Problem List Item" }
                })
            })
            IpsConditionSeverity.normalize(p.severity)?.let { sv ->
                severity = CodeableConcept.Builder().apply {
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = IpsConditionSeverity.SYSTEM }
                        code = Code.Builder().apply { value = sv }
                        IpsConditionSeverity.display(sv)?.let { d -> display = String.Builder().apply { value = d } }
                    })
                }
            }
            code = codeableConcept(p.code, p.system, p.display, p.text)
            parseFhirDate(p.onset)?.let { onset = Condition.Onset.DateTime(dateTimeBuilder(it).build()) }
            p.note?.takeIf { it.isNotBlank() }?.let { note.add(Annotation.Builder(Markdown.Builder().apply { value = it })) }
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
        section(TITLE_SECTION_IMMUNIZATIONS, LOINC_SECTION_IMMUNIZATIONS, "History of Immunization note", entryUrns)

    fun procedureSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_PROCEDURES, LOINC_SECTION_PROCEDURES, "History of Procedures Document", entryUrns)

    fun deviceSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_DEVICES, LOINC_SECTION_DEVICES, "History of medical device use", entryUrns)

    fun resultSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_RESULTS, LOINC_SECTION_RESULTS, "Relevant diagnostic tests/laboratory data note", entryUrns)

    fun functionalSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_FUNCTIONAL, LOINC_SECTION_FUNCTIONAL, "Functional status assessment note", entryUrns)

    fun pregnancySection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_PREGNANCY, LOINC_SECTION_PREGNANCY, "History of pregnancies Narrative", entryUrns)

    fun problemSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_PROBLEMS, LOINC_SECTION_PROBLEMS, "Problem list - Reported", entryUrns)

    fun pastProblemSection(entryUrns: List<kotlin.String>): Composition.Section.Builder? =
        section(TITLE_SECTION_PAST_ILLNESS, LOINC_SECTION_PAST_ILLNESS, "History of Past illness note", entryUrns)

    /** Serialise one resource alone (debug / tests). */
    fun encode(resource: Resource): kotlin.String = json.encodeToString(resource)
}
