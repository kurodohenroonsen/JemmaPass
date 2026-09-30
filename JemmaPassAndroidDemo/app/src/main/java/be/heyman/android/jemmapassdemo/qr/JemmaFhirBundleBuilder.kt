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

import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
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
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.terminologies.AdministrativeGender
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
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
    fun build(hydrated: HydratedProfile, native: IpsNativePillars = IpsNativePillars.EMPTY): kotlin.String {
        val t0 = java.lang.System.currentTimeMillis()

        // URNs pour cross-référencement intra-bundle — déterministes (sid + identité).
        val sid = hydrated.raw.sid?.takeIf { it.isNotBlank() } ?: "no-sid"
        val patientUrn = IpsFhirCodec.stableUrn("$sid|Patient")
        val compositionUrn = IpsFhirCodec.stableUrn("$sid|Composition")
        
        val allergyUrns = hydrated.allergies.mapIndexed { i, a -> IpsFhirCodec.stableUrn("$sid|AllergyIntolerance|$i|${a.raw.c.orEmpty()}") }
        val medStatementUrns = hydrated.medications.mapIndexed { i, m -> IpsFhirCodec.stableUrn("$sid|MedicationStatement|$i|${m.raw.c.orEmpty()}") }
        val medRefUrns = hydrated.medications.mapIndexed { i, m -> IpsFhirCodec.stableUrn("$sid|Medication|$i|${m.raw.c.orEmpty()}") }
        val conditionUrns = hydrated.conditions.mapIndexed { i, c -> IpsFhirCodec.stableUrn("$sid|Condition|$i|${c.raw.c.orEmpty()}") }
        val immunizationUrns = native.immunizations.map { im -> IpsFhirCodec.immunizationUrn(sid, im.id) }

        val nowIsoBuilder = nowDateTimeBuilder()

        // 1. Patient Resource
        val p = hydrated.raw.p
        val patientBuilder = Patient.Builder().apply {
            id = "patient-01"
            meta = Meta.Builder().apply {
                profile.add(Canonical.Builder().apply { value = "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips" })
            }
            
            name.add(HumanName.Builder().apply {
                family = String.Builder().apply { value = p?.fn ?: "" }
                given.add(String.Builder().apply { value = p?.gn ?: "" })
            })
            
            p?.gs?.let { gender = Enumeration.of(mapGender(it), null) }
            p?.bd?.takeIf { it.isNotBlank() }?.let {
                birthDate = dev.ohs.fhir.model.r4.Date.Builder().apply { value = FhirDate.fromString(it) }
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
                if (!c.n.isNullOrBlank() || !c.p.isNullOrBlank() || !c.e.isNullOrBlank()) {
                    contact.add(Patient.Contact.Builder().apply {
                        name = HumanName.Builder().apply {
                            text = String.Builder().apply { value = c.n ?: "" }
                        }
                        
                        c.p?.takeIf { it.isNotBlank() }?.let {
                            telecom.add(ContactPoint.Builder().apply {
                                system = Enumeration.of(ContactPoint.ContactPointSystem.Phone, null)
                                value = String.Builder().apply { value = it }
                                use = Enumeration.of(ContactPoint.ContactPointUse.Mobile, null)
                            })
                        }
                        c.e?.takeIf { it.isNotBlank() }?.let {
                            telecom.add(ContactPoint.Builder().apply {
                                system = Enumeration.of(ContactPoint.ContactPointSystem.Email, null)
                                value = String.Builder().apply { value = it }
                            })
                        }
                        c.r?.takeIf { it.isNotBlank() }?.let {
                            relationship.add(CodeableConcept.Builder().apply {
                                text = String.Builder().apply { value = it }
                            })
                        }
                    })
                }
            }
            
            p?.bt?.takeIf { it.isNotBlank() }?.let {
                extension.add(Extension.Builder("http://jemmapass.net/fhir/StructureDefinition/blood-type").apply {
                    value = Extension.Value.String(String.Builder().apply { value = it }.build())
                })
            }
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
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = SYS_SNOMED }
                        code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = codeStr }
                        display = String.Builder().apply { value = displayStr }
                    })
                    text = String.Builder().apply { value = displayStr }
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
                    text = String.Builder().apply { value = displayStr }
                }
            }
            
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = medRefUrns[i] }
                resource = medication
            })
            
            val statement = MedicationStatement.Builder(
                Enumeration.of(MedicationStatement.MedicationStatusCodes.Active, null),
                MedicationStatement.Medication.Reference(Reference.Builder().apply {
                    reference = String.Builder().apply { value = medRefUrns[i] }
                }.build()),
                Reference.Builder().apply { reference = String.Builder().apply { value = patientUrn } }
            ).apply {
                dateAsserted = nowDateTimeBuilder()
                
                val dosageText = listOfNotNull(
                    m.timing?.takeIf { it.isNotBlank() },
                    listOfNotNull(m.doseValue, m.doseUnit).joinToString("").takeIf { it.isNotEmpty() },
                ).joinToString(" • ").ifBlank { displayStr }
                
                dosage.add(Dosage.Builder().apply {
                    text = String.Builder().apply { value = dosageText }
                    m.route.name.takeIf { it.isNotBlank() }?.let {
                        route = CodeableConcept.Builder().apply { text = String.Builder().apply { value = it } }
                    }
                    m.doseValue?.toDoubleOrNull()?.let { dv ->
                        doseAndRate.add(Dosage.DoseAndRate.Builder().apply {
                            dose = Dosage.DoseAndRate.Dose.Quantity(Quantity.Builder().apply {
                                value = Decimal.Builder().apply {
                                    value = dv.toBigDecimal()
                                }
                                unit = String.Builder().apply { value = m.doseUnit ?: "" }
                                system = Uri.Builder().apply { value = SYS_UCUM }
                                code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = m.doseUnit ?: "" }
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

        // Condition entries
        hydrated.conditions.forEachIndexed { i, c ->
            val codeStr = c.raw.c.orEmpty()
            val displayStr = c.displayLocalized.ifBlank { codeStr }
            
            val condition = Condition.Builder(
                Reference.Builder().apply { reference = String.Builder().apply { value = patientUrn } }
            ).apply {
                clinicalStatus = CodeableConcept.Builder().apply {
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = SYS_COND_CLINICAL }
                        code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = "active" }
                    })
                }
                verificationStatus = CodeableConcept.Builder().apply {
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = SYS_COND_VERIF }
                        code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = "confirmed" }
                    })
                }
                category.add(CodeableConcept.Builder().apply {
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = SYS_COND_CATEGORY }
                        code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = "problem-list-item" }
                        display = String.Builder().apply { value = "Problem List Item" }
                    })
                })
                code = CodeableConcept.Builder().apply {
                    coding.add(Coding.Builder().apply {
                        system = Uri.Builder().apply { value = SYS_SNOMED }
                        code = dev.ohs.fhir.model.r4.Code.Builder().apply { value = codeStr }
                        display = String.Builder().apply { value = displayStr }
                    })
                    text = String.Builder().apply { value = displayStr }
                }
            }
            
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = conditionUrns[i] }
                resource = condition
            })
        }

        // 2b. FHIR-native pillars (source of truth = the Bundle itself)
        native.immunizations.forEachIndexed { i, im ->
            bundleEntries.add(Bundle.Entry.Builder().apply {
                fullUrl = Uri.Builder().apply { value = immunizationUrns[i] }
                resource = IpsFhirCodec.toFhir(im, patientUrn)
            })
        }

        // 3. Composition Resource
        val sections = listOfNotNull(
            sectionStub("Allergies", "48765-2", allergyUrns, hydrated.allergies),
            sectionStub("Medications", "10160-0", medStatementUrns, hydrated.medications),
            sectionStub("Problems", "11450-4", conditionUrns, hydrated.conditions),
            IpsFhirCodec.immunizationSection(immunizationUrns),
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
                a.use?.takeIf { it.isNotBlank() }?.let { use = Enumeration.of(dev.ohs.fhir.model.r4.Address.AddressUse.fromCode(it), null) }
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
                t.system?.takeIf { it.isNotBlank() }?.let { system = Enumeration.of(ContactPoint.ContactPointSystem.fromCode(t.system), null) }
                t.value?.takeIf { it.isNotBlank() }?.let { value = String.Builder().apply { value = it } }
                t.use?.takeIf { it.isNotBlank() }?.let { use = Enumeration.of(ContactPoint.ContactPointUse.fromCode(t.use), null) }
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
