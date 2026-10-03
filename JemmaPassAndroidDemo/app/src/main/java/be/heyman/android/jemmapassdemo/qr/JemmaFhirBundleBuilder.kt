/*
 * JemmaFhirBundleBuilder.kt — v2.2.16.14 (Fixed Enums Casing and Inner Classes)
 *
 * Refactored to use the official Kotlin-FHIR SDK (dev.ohs.fhir).
 * Fixed enum references and casing for all FHIR resources.
 *
 * FHIR-native pillars (feat/ips-18-pillars-cleanup):
 *   • `build()` now also takes the [IpsNativePillars] that live ONLY in the
 *     Bundle (Immunizations first) and appends their resources + IPS sections.
 *   • Intra-bundle `urn:uuid:` references are deterministic (derived from the
 *     profile sid + resource identity) so a rebuild of an unchanged profile
 *     yields the same document — no more random UUIDs on every save.
 */
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.ips.IpsBloodGroup
import be.heyman.android.jemmapassdemo.ips.IpsDecimal
import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.ips.IpsResult
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import dev.ohs.fhir.model.r4.AllergyIntolerance
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.Condition
import dev.ohs.fhir.model.r4.ContactPoint
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Dosage
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Instant as FhirInstant
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Medication
import dev.ohs.fhir.model.r4.MedicationStatement
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.terminologies.AdministrativeGender
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import java.util.Date
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.util.Locale

object JemmaFhirBundleBuilder {

    private const val TAG = "JEMMA-CODEC"

    // Code systems FHIR R4 / IPS canoniques.
    private const val SYS_SNOMED = "http://snomed.info/sct"
    private const val SYS_LOINC = "http://loinc.org"
    private const val SYS_ATC = "http://www.whocc.no/atc"
    private const val SYS_AI_CLINICAL = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical"
    private const val SYS_AI_VERIF = "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification"
    private const val SYS_COND_CLINICAL = "http://terminology.hl7.org/CodeSystem/condition-clinical"
    private const val SYS_COND_VERIF = "http://terminology.hl7.org/CodeSystem/condition-ver-status"
    private const val SYS_COND_CATEGORY = "http://terminology.hl7.org/CodeSystem/condition-category"
    private const val SYS_UCUM = "http://unitsofmeasure.org"

    private val fhirJson = FhirR4Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Construit un Bundle FHIR R4 type "document" depuis le profile
     * hydraté. Retourne le JSON sérialisé pretty-printed.
     */
    private val NON_UCUM_DOSE_UNITS = setOf("tab", "tabs", "tablet", "tablets", "cp", "comp", "cap", "caps", "capsule", "capsules", "puff", "puffs", "drop", "drops", "gtt", "sachet", "patch", "unit", "units", "dose", "doses")

    fun build(hydrated: HydratedProfile, nativeIn: IpsNativePillars = IpsNativePillars.EMPTY): kotlin.String {
        val t0 = java.lang.System.currentTimeMillis()
        // Callers without the stored native pillars (FHIR QR channel) rebuild them from the
        // `_j` projections so the Bundle still carries every pillar.
        val raw = hydrated.raw
        val nativeRaw = if (nativeIn.isEmpty) {
            IpsNativePillars.fromJEntries(raw.im, raw.pr, raw.dv, raw.rs, raw.ph, raw.cn, raw.pg, raw.fs)
        } else nativeIn

        // URNs pour cross-référencement intra-bundle — déterministes (sid + identité).
        val sid = hydrated.raw.sid?.takeIf { it.isNotBlank() } ?: "no-sid"

        // UC-FHIR-027 — the Bundle carries exactly one ABO/Rh Observation (LOINC 882-1) and it
        // agrees with the patient pillar (`p.bt`), whatever the incoming results look like.
        val native = nativeRaw.copy(results = reconcileBloodGroup(nativeRaw.results, sid, raw.p?.bt))
        val patientUrn = IpsFhirCodec.stableUrn("$sid|Patient")
        val compositionUrn = IpsFhirCodec.stableUrn("$sid|Composition")
        
        val allergyUrns = hydrated.allergies.mapIndexed { i, a -> IpsFhirCodec.stableUrn("$sid|AllergyIntolerance|$i|${a.raw.c.orEmpty()}") }
        val medStatementUrns = hydrated.medications.mapIndexed { i, m -> IpsFhirCodec.stableUrn("$sid|MedicationStatement|$i|${m.raw.c.orEmpty()}") }
        val medRefUrns = hydrated.medications.mapIndexed { i, m -> IpsFhirCodec.stableUrn("$sid|Medication|$i|${m.raw.c.orEmpty()}") }
        val problemUrns = native.problems.map { pb -> IpsFhirCodec.problemUrn(sid, pb.id) }
        val functionalUrns = native.functional.map { fs -> IpsFhirCodec.functionalUrn(sid, fs.id) }
        val pregnancyUrns = native.pregnancy.map { pg -> IpsFhirCodec.pregnancyUrn(sid, pg.id) }
        val immunizationUrns = native.immunizations.map { im -> IpsFhirCodec.immunizationUrn(sid, im.id) }
        val procedureUrns = native.procedures.map { pr -> IpsFhirCodec.procedureUrn(sid, pr.id) }
        val deviceStatementUrns = native.devices.map { dv -> IpsFhirCodec.deviceUseStatementUrn(sid, dv.id) }
        val deviceUrns = native.devices.map { dv -> IpsFhirCodec.deviceUrn(sid, dv.id) }
        val resultUrns = native.results.map { rs -> IpsFhirCodec.resultUrn(sid, rs.id) }
        val pastProblemUrns = native.pastProblems.map { pp -> IpsFhirCodec.pastProblemUrn(sid, pp.id) }

        val nowIsoBuilder = nowDateTimeBuilder()

        // 1. Patient Resource
        val p = hydrated.raw.p
        val patientBuilder = Patient.Builder().apply {
            id = "patient-01"
            meta = Meta.Builder().apply {
                profile.add(Canonical.Builder().apply { value = "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips" })
            }
            
            // HL7 validator (cycle 6): empty strings are invalid FHIR primitives — omit blanks
            // (Kamekichi is mononymous: given name only).
            name.add(HumanName.Builder().apply {
                p?.fn?.takeIf { it.isNotBlank() }?.let { fn -> family = String.Builder().apply { value = fn } }
                p?.gn?.takeIf { it.isNotBlank() }?.let { gn -> given.add(String.Builder().apply { value = gn }) }
            })
            
            p?.gs?.let { gender = Enumeration.of(mapGender(it), null) }
            p?.bd?.takeIf { it.isNotBlank() }?.let {
                try {
                    birthDate = dev.ohs.fhir.model.r4.Date.Builder().apply { value = FhirDate.fromString(it) }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "build: birthDate not ISO, omitted", e)
                }
            }
            
            address.addAll(buildPatientAddresses(hydrated))
            telecom.addAll(buildPatientTelecoms(hydrated))
            
            p?.lang?.takeIf { it.isNotBlank() }?.let { bcp47 ->
                communication.add(Patient.Communication.Builder(
                    CodeableConcept.Builder().apply {
                        coding.add(Coding.Builder().apply {
                            system = Uri.Builder().apply { value = "urn:ietf:bcp:47" }
                            code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = bcp47 }
                        })
                    }
                ))
            }
            
            (p?.ct ?: emptyList()).forEach { c ->
                if (!c.n.isNullOrBlank() || !c.p.isNullOrBlank() || !c.e.isNullOrBlank() || !c.adr.isNullOrBlank()) {
                    contact.add(Patient.Contact.Builder().apply {
                        if (!c.n.isNullOrBlank()) {
                            name = HumanName.Builder().apply {
                                text = String.Builder().apply { value = c.n }
                            }
                        }
                        
                        c.p?.takeIf { it.isNotBlank() }?.let {
                            telecom.add(ContactPoint.Builder().apply {
                                system = Enumeration.of(ContactPoint.ContactPointSystem.Phone, null)
                                value = String.Builder().apply { value = it }
                            })
                        }
                        c.e?.takeIf { it.isNotBlank() }?.let {
                            telecom.add(ContactPoint.Builder().apply {
                                system = Enumeration.of(ContactPoint.ContactPointSystem.Email, null)
                                value = String.Builder().apply { value = it }
                            })
                        }
                        c.adr?.takeIf { it.isNotBlank() }?.let {
                            address = dev.ohs.fhir.model.r4.Address.Builder().apply {
                                text = String.Builder().apply { value = it }
                            }
                        }
                        c.r?.takeIf { it.isNotBlank() }?.let { rawRel ->
                            val entry = be.heyman.android.jemmapassdemo.pillars.IpsRelationshipCatalog.ALL.firstOrNull {
                                it.code.equals(rawRel.trim(), ignoreCase = true)
                            }
                            relationship.add(CodeableConcept.Builder().apply {
                                if (entry != null) {
                                    coding.add(Coding.Builder().apply {
                                        system = Uri.Builder().apply {
                                            value = be.heyman.android.jemmapassdemo.pillars.IpsRelationshipCatalog.CODE_SYSTEM
                                        }
                                        code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = entry.code }
                                        display = String.Builder().apply { value = entry.displayEn }
                                    })
                                    text = String.Builder().apply { value = entry.pick(hydrated.uiLang).ifBlank { entry.displayEn } }
                                } else {
                                    text = String.Builder().apply { value = rawRel }
                                }
                            })
                        }
                    })
                }
            }
            
            // Blood type: no longer a home-made Patient extension (rejected by the HL7
            // validator) — it travels as a Results Observation (LOINC 882-1, SNOMED value)
            // derived from `p.bt` by ProfilesRepository / IpsBloodGroup.
        }

        // 2. Entries
        val bundleEntries = mutableListOf<Bundle.Entry.Builder>()
        
        // AllergyIntolerance entries
        hydrated.allergies.forEachIndexed { i, a ->
            val codeStr = a.raw.c.orEmpty()
            val displayStr = a.displayLocalized.ifBlank { codeStr }
            
            val allergy = AllergyIntolerance.Builder(
                Reference.Builder().apply { reference = String.Builder().apply { value = patientUrn } }
            ).apply {
                clinicalStatus = CodeableConcept.Builder().apply {
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = SYS_AI_CLINICAL }
                        code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = a.clinicalStatus.name.lowercase() }
                    })
                }
                verificationStatus = CodeableConcept.Builder().apply {
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = SYS_AI_VERIF }
                        code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = "confirmed" }
                    })
                }
                type = Enumeration.of(AllergyIntolerance.AllergyIntoleranceType.Allergy, null)
                criticality = Enumeration.of(when (a.criticality.name) {
                    "HIGH" -> AllergyIntolerance.AllergyIntoleranceCriticality.High
                    "LOW" -> AllergyIntolerance.AllergyIntoleranceCriticality.Low
                    else -> AllergyIntolerance.AllergyIntoleranceCriticality.Unable_To_Assess
                }, null)
                code = CodeableConcept.Builder().apply {
                    if (codeStr.isNotBlank()) {
                        coding.add(Coding.Builder().apply {
                            system = Uri.Builder().apply { value = SYS_SNOMED }
                            code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = codeStr }
                            display = String.Builder().apply { value = displayStr }
                        })
                    }
                    if (displayStr.isNotBlank()) {
                        text = String.Builder().apply { value = displayStr }
                    }
                }
            }
            
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = allergyUrns[i] }
                resource = allergy
            })
        }

        // Medication entries
        hydrated.medications.forEachIndexed { i, m ->
            val displayStr = m.displayLocalized.ifBlank { m.raw.c.orEmpty() }
            
            // 🆕 Lot 14.5c9.4 — Dynamically detect the proper code system instead of hardcoding SNOMED CT.
            // In HL7 FHIR IPS:
            // - ATC (Anatomical Therapeutic Chemical) uses "http://www.whocc.no/atc"
            // - RxNorm uses "http://www.nlm.nih.gov/research/umls/rxnorm"
            // - SNOMED CT uses "http://snomed.info/sct"
            val rawCode = m.raw.c.orEmpty()
            val isAtc = rawCode.isNotBlank() && Regex("^[A-Za-z]\\d{2}[A-Za-z]{2}\\d{2}$").matches(rawCode.trim())
            
            val primarySystem = when {
                !m.raw.codeSystem.isNullOrBlank() -> m.raw.codeSystem
                !m.rxnormCui.isNullOrBlank() -> "http://www.nlm.nih.gov/research/umls/rxnorm"
                isAtc -> SYS_ATC
                else -> SYS_SNOMED
            }

            val medication = Medication.Builder().apply {
                code = CodeableConcept.Builder().apply {
                    if (rawCode.isNotBlank()) {
                        coding.add(Coding.Builder().apply {
                            system = Uri.Builder().apply { value = primarySystem }
                            code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = rawCode }
                            display = String.Builder().apply { value = displayStr }
                        })
                        // Add secondary ATC code if available and not already the primary code
                        m.atcCode?.takeIf { it.isNotBlank() && it != rawCode }?.let { atc ->
                            coding.add(Coding.Builder().apply {
                                system = Uri.Builder().apply { value = SYS_ATC }
                                code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = atc }
                                display = String.Builder().apply { value = displayStr }
                            })
                        }
                    }
                    if (displayStr.isNotBlank()) {
                        text = String.Builder().apply { value = displayStr }
                    }
                }
            }
            
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = medRefUrns[i] }
                resource = medication
            })
            
            val statement = MedicationStatement.Builder(
                Enumeration.of(medicationStatus(m.raw.status), null),
                MedicationStatement.Medication.Reference(Reference.Builder().apply {
                    reference = String.Builder().apply { value = medRefUrns[i] }
                }.build()),
                Reference.Builder().apply { reference = String.Builder().apply { value = patientUrn } }
            ).apply {
                dateAsserted = nowDateTimeBuilder()

                m.raw.effective?.takeIf { it.isNotBlank() }?.let { eff ->
                    if (eff.contains("/")) {
                        val parts = eff.split("/")
                        val s = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
                        val e = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                        effective = MedicationStatement.Effective.Period(Period.Builder().apply {
                            s?.let { startStr ->
                                try {
                                    start = DateTime.Builder().apply { value = FhirDateTime.fromString(startStr) }
                                } catch (e: Exception) {
                                    android.util.Log.w(TAG, "build: medication effective start unparseable, omitted", e)
                                }
                            }
                            e?.let { endStr ->
                                try {
                                    end = DateTime.Builder().apply { value = FhirDateTime.fromString(endStr) }
                                } catch (e: Exception) {
                                    android.util.Log.w(TAG, "build: medication effective end unparseable, omitted", e)
                                }
                            }
                        }.build())
                    } else {
                        try {
                            effective = MedicationStatement.Effective.DateTime(
                                DateTime.Builder().apply { value = FhirDateTime.fromString(eff.trim()) }.build()
                            )
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "build: medication effective date unparseable, omitted", e)
                        }
                    }
                }
                
                val dosageText = listOfNotNull(
                    m.timing?.takeIf { it.isNotBlank() },
                    listOfNotNull(m.doseValue, m.doseUnit).joinToString("").takeIf { it.isNotEmpty() },
                ).joinToString(" • ").ifBlank { displayStr }
                
                dosage.add(Dosage.Builder().apply {
                    text = String.Builder().apply { value = dosageText }
                    routeConcept(m.raw.r)?.let { route = it }
                    parseDose(m.doseValue, m.doseUnit)?.let { (dv, du) ->
                        doseAndRate.add(Dosage.DoseAndRate.Builder().apply {
                            dose = Dosage.DoseAndRate.Dose.Quantity(Quantity.Builder().apply {
                                value = Decimal.Builder().apply {
                                    value = BigDecimal.parseString(dv)
                                }
                                val u = du
                                if (u.isNotBlank()) unit = String.Builder().apply { value = u }
                                // Count units ("tab", "caps", "puff"…) are not UCUM codes (HL7 validator, cycle 7).
                                if (u.isNotBlank() && u.lowercase() !in NON_UCUM_DOSE_UNITS) {
                                    system = Uri.Builder().apply { value = SYS_UCUM }
                                    code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = u }
                                }
                            }.build())
                        })
                    }
                })
            }
            
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = medStatementUrns[i] }
                resource = statement
            })
        }

        // Problem list (FHIR-native since sprint 5; `_j.cn` is its projection)
        native.problems.forEachIndexed { i, pb ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = problemUrns[i] }
                resource = IpsFhirCodec.toFhir(pb, patientUrn)
            })
        }

        // 2b. FHIR-native pillars (source of truth = the Bundle itself)
        native.immunizations.forEachIndexed { i, im ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = immunizationUrns[i] }
                resource = IpsFhirCodec.toFhir(im, patientUrn)
            })
        }
        native.procedures.forEachIndexed { i, pr ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = procedureUrns[i] }
                resource = IpsFhirCodec.toFhir(pr, patientUrn)
            })
        }
        native.devices.forEachIndexed { i, dv ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = deviceUrns[i] }
                resource = IpsFhirCodec.toFhirDevice(dv, patientUrn)
            })
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = deviceStatementUrns[i] }
                resource = IpsFhirCodec.toFhirUseStatement(dv, patientUrn, deviceUrns[i])
            })
        }
        native.results.forEachIndexed { i, rs ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = resultUrns[i] }
                resource = IpsFhirCodec.toFhir(rs, patientUrn)
            })
        }

        native.pastProblems.forEachIndexed { i, pp ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = pastProblemUrns[i] }
                resource = IpsFhirCodec.toFhir(pp, patientUrn)
            })
        }

        native.pregnancy.forEachIndexed { i, pg ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = pregnancyUrns[i] }
                resource = IpsFhirCodec.toFhir(pg, patientUrn)
            })
        }

        native.functional.forEachIndexed { i, fs ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = functionalUrns[i] }
                resource = IpsFhirCodec.toFhir(fs, patientUrn)
            })
        }

        // 3. Composition Resource
        val sections = listOfNotNull(
            sectionStub("Allergies", "48765-2", allergyUrns, hydrated.allergies),
            sectionStub("Medications", "10160-0", medStatementUrns, hydrated.medications),
            IpsFhirCodec.problemSection(problemUrns),
            IpsFhirCodec.pastProblemSection(pastProblemUrns),
            IpsFhirCodec.pregnancySection(pregnancyUrns),
            IpsFhirCodec.functionalSection(functionalUrns),
            IpsFhirCodec.immunizationSection(immunizationUrns),
            IpsFhirCodec.procedureSection(procedureUrns),
            IpsFhirCodec.deviceSection(deviceStatementUrns),
            IpsFhirCodec.resultSection(resultUrns),
        ).map { it.build() }
        
        val composition = Composition.Builder(
            Enumeration.of(Composition.CompositionStatus.Final, null),
            CodeableConcept.Builder().apply {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = SYS_LOINC }
                    code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = "60591-5" }
                    display = String.Builder().apply { value = "Patient summary Document" }
                })
            },
            nowIsoBuilder,
            mutableListOf(Reference.Builder().apply { display = String.Builder().apply { value = "JEMMA Pass on-device" } }),
            String.Builder().apply { value = "International Patient Summary" }
        ).apply {
            subject = Reference.Builder().apply { reference = String.Builder().apply { value = patientUrn } }
            confidentiality = Enumeration.of(Composition.V3ConfidentialityClassification.N, null)
            section.addAll(sections.map { it.toBuilder() })
        }.build()

        // 4. Final Bundle
        val bundle = Bundle.Builder(Enumeration.of(Bundle.BundleType.Document, null)).apply {
            // bdl-9: a document Bundle must carry an identifier (system + value); deterministic per profile.
            identifier = dev.ohs.fhir.model.r4.Identifier.Builder().apply {
                system = Uri.Builder().apply { value = "urn:ietf:rfc:3986" }
                value = String.Builder().apply { value = IpsFhirCodec.stableUrn("$sid|Bundle") }
            }
            timestamp = FhirInstant.Builder().apply {
                value = FhirDateTime.fromString(getCurrentIsoTimestamp())
            }
            
            entry.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = compositionUrn }
                resource = composition.toBuilder()
            })
            
            entry.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = patientUrn }
                resource = patientBuilder
            })
            
            entry.addAll(bundleEntries)
        }.build()

        val json = fhirJson.encodeToString(bundle)
        val dt = java.lang.System.currentTimeMillis() - t0
        android.util.Log.i(TAG, "🏥 FHIR Bundle built using SDK in ${dt}ms : ${bundle.entry.size} entries")
        return json
    }

    /**
     * UC-FHIR-008 — `md[].ms` → MedicationStatement.status (R4 medication-statement-status).
     * Blank = `active` (the form's default); a value outside the value set = `unknown`,
     * never `active`: a stopped treatment must not be exported as a current one.
     */
    internal fun medicationStatus(raw: kotlin.String?): MedicationStatement.MedicationStatusCodes {
        val code = raw?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')?.replace(' ', '-').orEmpty()
        return when (code) {
            "", "active" -> MedicationStatement.MedicationStatusCodes.Active
            "completed" -> MedicationStatement.MedicationStatusCodes.Completed
            "entered-in-error" -> MedicationStatement.MedicationStatusCodes.Entered_In_Error
            "intended" -> MedicationStatement.MedicationStatusCodes.Intended
            "stopped" -> MedicationStatement.MedicationStatusCodes.Stopped
            "on-hold" -> MedicationStatement.MedicationStatusCodes.On_Hold
            "not-taken" -> MedicationStatement.MedicationStatusCodes.Not_Taken
            else -> MedicationStatement.MedicationStatusCodes.Unknown
        }
    }

    private val DOSE_PATTERN = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*([A-Za-zµμ%][A-Za-zµμ%/.]*)?$")
    private val AMBIGUOUS_THOUSANDS = Regex("^[1-9][0-9]{0,2},[0-9]{3}$")

    /**
     * UC-FHIR-024 — dose as typed → (decimal with a dot, unit). The comma is a decimal
     * separator ("0,5" → "0.5"); a unit typed in the value field ("0,5 mg") is used when
     * the unit field is empty. Null (dose kept as text only) when the value is not a plain
     * number, or when "1,000" could mean one or one thousand.
     */
    internal fun parseDose(rawValue: kotlin.String?, rawUnit: kotlin.String?): Pair<kotlin.String, kotlin.String>? {
        val match = DOSE_PATTERN.matchEntire(rawValue?.trim().orEmpty()) ?: return null
        val number = match.groupValues[1]
        if (AMBIGUOUS_THOUSANDS.matches(number)) return null
        val decimal = IpsDecimal.normalize(number) ?: return null
        val unit = rawUnit?.trim().orEmpty().ifBlank { match.groupValues[2] }
        return decimal to unit
    }

    /**
     * UC-FHIR-026 — `md[].r` → Dosage.route. Only an unambiguous route gets a SNOMED CT code;
     * "H" (what the form and the KB dose mapping store for inhalers, UC-MED-ROUTE-01) is the
     * respiratory route. "I" (any injection: IV, IM… — and inhalers saved before "H" existed,
     * which are not migrated) and unrecognised values are text-only, so the Bundle never
     * asserts a route it does not know. A blank route is omitted.
     */
    internal fun routeConcept(rawRoute: kotlin.String?): CodeableConcept.Builder? {
        val r = rawRoute?.trim().orEmpty()
        if (r.isEmpty()) return null
        val key = r.uppercase(Locale.ROOT)
        val snomed: kotlin.String?
        val label: kotlin.String
        when {
            key == "O" || key == "ORAL" -> { snomed = "26643006"; label = "Oral" }
            key == "T" || key == "TOPICAL" -> { snomed = "6064005"; label = "Topical" }
            key == "S" || key == "SUBCUTANEOUS" -> { snomed = "34206005"; label = "Subcutaneous" }
            key == "H" || key.startsWith("INH") -> { snomed = "447694001"; label = "Inhalation" }
            key == "I" || key == "INJECTION" -> { snomed = null; label = "Injection" }
            else -> { snomed = null; label = r }
        }
        return CodeableConcept.Builder().apply {
            snomed?.let { c ->
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = SYS_SNOMED }
                    code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = c }
                })
            }
            text = String.Builder().apply { value = label }
        }
    }

    /**
     * UC-FHIR-027 — `p.bt` is the patient-pillar source for the blood group. When it is a
     * recognised ABO/Rh label, the results keep ONE 882-1 entry whose value matches it (an
     * existing matching entry is kept as is, else the derived one is inserted first) and every
     * other 882-1 entry — stale copy from an import, duplicate, contradiction — is dropped.
     * When `p.bt` is absent or unreadable, no derived entry is emitted; results entered by
     * hand are left alone.
     */
    internal fun reconcileBloodGroup(results: List<IpsResult>, sid: kotlin.String, bloodType: kotlin.String?): List<IpsResult> {
        val expected = IpsBloodGroup.snomedCode(bloodType)
            ?: return results.filterNot { IpsBloodGroup.isDerived(it) }
        val canonical = IpsBloodGroup.normalize(bloodType)
        val isBloodGroup = { r: IpsResult -> r.code == IpsBloodGroup.LOINC_ABO_RH || IpsBloodGroup.isDerived(r) }
        val keep = results.firstOrNull { isBloodGroup(it) && (it.valueCode == expected || IpsBloodGroup.labelOf(it) == canonical) }
        if (keep != null) return results.filter { !isBloodGroup(it) || it === keep }
        val others = results.filterNot { isBloodGroup(it) }
        val derived = IpsBloodGroup.derivedResult(sid, bloodType) ?: return others
        return listOf(derived) + others
    }

    private fun <T> sectionStub(title: kotlin.String, loinc: kotlin.String, refs: List<kotlin.String>, items: List<T>): Composition.Section.Builder? {
        if (items.isEmpty()) return null
        return Composition.Section.Builder().apply {
            this.title = String.Builder().apply { value = title }
            code = CodeableConcept.Builder().apply {
                coding.add(Coding.Builder().apply {
                    system = Uri.Builder().apply { value = SYS_LOINC }
                    code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = loinc }
                })
            }
            entry.addAll(refs.map { r -> Reference.Builder().apply { reference = String.Builder().apply { value = r } } })
        }
    }

    private fun buildPatientAddresses(h: HydratedProfile): List<dev.ohs.fhir.model.r4.Address.Builder> {
        val p = h.raw.p ?: return emptyList()
        val out = mutableListOf<dev.ohs.fhir.model.r4.Address.Builder>()
        p.adr?.takeIf { it.isNotBlank() }?.let {
            out.add(dev.ohs.fhir.model.r4.Address.Builder().apply {
                text = String.Builder().apply { value = it }
                line.add(String.Builder().apply { value = it })
            })
        }
        p.adrs.forEach { a ->
            out.add(dev.ohs.fhir.model.r4.Address.Builder().apply {
                a.use?.takeIf { it.isNotBlank() }?.let {
                    try {
                        use = Enumeration.of(dev.ohs.fhir.model.r4.Address.AddressUse.fromCode(it), null)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "build: address.use unrecognised, omitted", e)
                    }
                }
                a.line?.takeIf { it.isNotBlank() }?.let { line.add(String.Builder().apply { value = it }) }
                a.city?.takeIf { it.isNotBlank() }?.let { city = String.Builder().apply { value = it } }
                a.postalCode?.takeIf { it.isNotBlank() }?.let { postalCode = String.Builder().apply { value = it } }
                a.country?.takeIf { it.isNotBlank() }?.let { country = String.Builder().apply { value = it } }
            })
        }
        return out
    }

    private fun buildPatientTelecoms(h: HydratedProfile): List<ContactPoint.Builder> {
        val p = h.raw.p ?: return emptyList()
        val out = mutableListOf<ContactPoint.Builder>()
        p.tel?.takeIf { it.isNotBlank() }?.let {
            out.add(ContactPoint.Builder().apply {
                system = Enumeration.of(ContactPoint.ContactPointSystem.Phone, null)
                value = String.Builder().apply { value = it }
            })
        }
        p.eml?.takeIf { it.isNotBlank() }?.let {
            out.add(ContactPoint.Builder().apply {
                system = Enumeration.of(ContactPoint.ContactPointSystem.Email, null)
                value = String.Builder().apply { value = it }
            })
        }
        p.tels.forEach { t ->
            out.add(ContactPoint.Builder().apply {
                t.system?.takeIf { it.isNotBlank() }?.let {
                    try {
                        system = Enumeration.of(ContactPoint.ContactPointSystem.fromCode(t.system), null)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "build: telecom.system unrecognised, omitted", e)
                    }
                }
                t.value?.takeIf { it.isNotBlank() }?.let { value = String.Builder().apply { value = it } }
                t.use?.takeIf { it.isNotBlank() }?.let {
                    try {
                        use = Enumeration.of(ContactPoint.ContactPointUse.fromCode(t.use), null)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "build: telecom.use unrecognised, omitted", e)
                    }
                }
            })
        }
        return out
    }

    private fun mapGender(gs: kotlin.String): AdministrativeGender = when (gs.uppercase()) {
        "M" -> AdministrativeGender.Male
        "F" -> AdministrativeGender.Female
        "O" -> AdministrativeGender.Other
        else -> AdministrativeGender.Unknown
    }

    private fun nowDateTimeBuilder(): DateTime.Builder {
        return DateTime.Builder().apply {
            value = FhirDateTime.fromString(getCurrentIsoTimestamp())
        }
    }

    private fun getCurrentIsoTimestamp(): kotlin.String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
