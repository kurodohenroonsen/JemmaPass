import Foundation

// MARK: - JemmaFhirBundleBuilder
public enum JemmaFhirBundleBuilder: Sendable {

    private static let sysSnomed = "http://snomed.info/sct"
    private static let sysLoinc = "http://loinc.org"
    private static let sysAiClinical = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical"
    private static let sysAiVerif = "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification"

    public static func build(
        profile: JemmaProfileJ,
        uiLang: String = "en",
        timestamp: String? = nil
    ) -> FHIRBundle {
        let sid = profile.sid?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? "no-sid"
        let patientUrn = IpsFhirCodec.stableUrn("\(sid)|Patient")
        let compositionUrn = IpsFhirCodec.stableUrn("\(sid)|Composition")

        // 1. Patient Resource
        let patient = buildPatient(p: profile.p, uiLang: uiLang)

        // 2. Clinical Resources & Composition Sections
        var bundleEntries: [FHIRBundleEntry] = []
        var compositionSections: [FHIRSection] = []

        // Allergies
        if !profile.al.isEmpty {
            var allergyRefs: [FHIRReference] = []
            for (i, a) in profile.al.enumerated() {
                let urn = IpsFhirCodec.stableUrn("\(sid)|AllergyIntolerance|\(i)|\(a.c ?? "")")
                allergyRefs.append(FHIRReference(reference: urn))

                let codeStr = a.c ?? ""
                let displayStr = a.d_display?.nonEmpty ?? a.d?.nonEmpty ?? codeStr
                let crit = (a.s == "H") ? "high" : ((a.s == "L") ? "low" : "unable-to-assess")

                let allergy = FHIRAllergyIntolerance(
                    id: "allergy-\(i + 1)",
                    clinicalStatus: FHIRCodeableConcept(coding: [
                        FHIRCoding(system: sysAiClinical, code: "active")
                    ]),
                    verificationStatus: FHIRCodeableConcept(coding: [
                        FHIRCoding(system: sysAiVerif, code: "confirmed")
                    ]),
                    type: "allergy",
                    criticality: crit,
                    code: FHIRCodeableConcept(
                        coding: !codeStr.isEmpty ? [FHIRCoding(system: sysSnomed, code: codeStr, display: displayStr)] : nil,
                        text: displayStr
                    ),
                    patient: FHIRReference(reference: patientUrn)
                )
                bundleEntries.append(FHIRBundleEntry(fullUrl: urn, resource: .allergyIntolerance(allergy)))
            }
            compositionSections.append(FHIRSection(
                title: "Allergies",
                code: FHIRCodeableConcept(coding: [FHIRCoding(system: sysLoinc, code: "48765-2")]),
                entry: allergyRefs
            ))
        }

        // Medications
        if !profile.md.isEmpty {
            var medRefs: [FHIRReference] = []
            for (i, m) in profile.md.enumerated() {
                let statUrn = IpsFhirCodec.stableUrn("\(sid)|MedicationStatement|\(i)|\(m.c ?? "")")
                let medUrn = IpsFhirCodec.stableUrn("\(sid)|Medication|\(i)|\(m.c ?? "")")
                medRefs.append(FHIRReference(reference: statUrn))

                let codeStr = m.c ?? ""
                let displayStr = m.d_display?.nonEmpty ?? codeStr

                let med = FHIRMedication(
                    id: "med-\(i + 1)",
                    code: FHIRCodeableConcept(
                        coding: !codeStr.isEmpty ? [FHIRCoding(system: m.cs ?? "http://www.whocc.no/atc", code: codeStr, display: displayStr)] : nil,
                        text: displayStr
                    )
                )
                bundleEntries.append(FHIRBundleEntry(fullUrl: medUrn, resource: .medication(med)))

                let statement = FHIRMedicationStatement(
                    id: "med-stmt-\(i + 1)",
                    status: m.ms ?? "active",
                    medicationReference: FHIRReference(reference: medUrn),
                    subject: FHIRReference(reference: patientUrn),
                    effectiveDateTime: m.eff,
                    dosage: buildDosage(m: m)
                )
                bundleEntries.append(FHIRBundleEntry(fullUrl: statUrn, resource: .medicationStatement(statement)))
            }
            compositionSections.append(FHIRSection(
                title: "Medications",
                code: FHIRCodeableConcept(coding: [FHIRCoding(system: sysLoinc, code: "10160-0")]),
                entry: medRefs
            ))
        }

        // Blood Group (Results Observation LOINC 882-1)
        if let bt = profile.p?.bt,
           let snomedCode = IpsBloodGroup.snomedCode(bt),
           let snomedDisplay = IpsBloodGroup.snomedDisplay(bt) {
            let resultId = IpsBloodGroup.derivedId(profileId: sid)
            let obsUrn = IpsFhirCodec.resultUrn(profileSid: sid, resultId: resultId)

            let obs = FHIRObservation(
                id: resultId,
                meta: FHIRMeta(profile: [
                    "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips"
                ]),
                status: "final",
                category: [
                    FHIRCodeableConcept(
                        coding: [
                            FHIRCoding(
                                system: "http://terminology.hl7.org/CodeSystem/observation-category",
                                code: "laboratory",
                                display: "Laboratory"
                            )
                        ],
                        text: "Laboratory"
                    )
                ],
                code: FHIRCodeableConcept(
                    coding: [
                        FHIRCoding(
                            system: sysLoinc,
                            code: IpsBloodGroup.loincAboRh,
                            display: "ABO and Rh group [Type] in Blood"
                        )
                    ],
                    text: IpsBloodGroup.displayAboRh
                ),
                subject: FHIRReference(reference: patientUrn),
                performer: [
                    FHIRReference(reference: patientUrn, display: "Patient-reported")
                ],
                _effectiveDateTime: FHIRElementExtension(
                    extension: [
                        FHIRExtension(
                            url: "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                            valueCode: "unknown"
                        )
                    ]
                ),
                valueCodeableConcept: FHIRCodeableConcept(
                    coding: [
                        FHIRCoding(
                            system: sysSnomed,
                            code: snomedCode,
                            display: snomedDisplay
                        )
                    ],
                    text: snomedDisplay
                )
            )

            bundleEntries.append(FHIRBundleEntry(fullUrl: obsUrn, resource: .observation(obs)))

            compositionSections.append(FHIRSection(
                title: "Results",
                code: FHIRCodeableConcept(
                    coding: [
                        FHIRCoding(
                            system: sysLoinc,
                            code: "30954-2",
                            display: "Relevant diagnostic tests/laboratory data note"
                        )
                    ]
                ),
                entry: [FHIRReference(reference: obsUrn)]
            ))
        }

        // Composition
        let compDate = timestamp ?? ISO8601DateFormatter().string(from: Date())
        let composition = FHIRComposition(
            id: "composition-01",
            status: "final",
            type: FHIRCodeableConcept(
                coding: [FHIRCoding(system: sysLoinc, code: "60591-5", display: "Patient summary Document")]
            ),
            subject: FHIRReference(reference: patientUrn),
            date: compDate,
            author: [FHIRAuthor(display: "JEMMA Pass on-device")],
            title: "International Patient Summary",
            confidentiality: "N",
            section: compositionSections
        )

        // Bundle composition is first entry
        var allEntries = [
            FHIRBundleEntry(fullUrl: compositionUrn, resource: .composition(composition)),
            FHIRBundleEntry(fullUrl: patientUrn, resource: .patient(patient)),
        ]
        allEntries.append(contentsOf: bundleEntries)

        let bundleIdentifier = FHIRIdentifier(
            system: "urn:ietf:rfc:3986",
            value: IpsFhirCodec.stableUrn("\(sid)|Bundle")
        )

        return FHIRBundle(
            id: sid,
            identifier: bundleIdentifier,
            type: "document",
            timestamp: compDate,
            entry: allEntries
        )
    }

    public static func buildJson(
        profile: JemmaProfileJ,
        uiLang: String = "en",
        timestamp: String? = nil
    ) throws -> String {
        let bundle = build(profile: profile, uiLang: uiLang, timestamp: timestamp)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(bundle)
        return String(decoding: data, as: UTF8.self)
    }

    // MARK: - Patient Builder
    private static func buildPatient(p: JPatient?, uiLang: String) -> FHIRPatient {
        guard let p = p else {
            return FHIRPatient(
                id: "patient-01",
                meta: FHIRMeta(profile: ["http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips"])
            )
        }

        // HumanName
        var names: [FHIRHumanName]? = nil
        let gn = p.gn?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        let fn = p.fn?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        if gn != nil || fn != nil {
            names = [FHIRHumanName(family: fn, given: gn != nil ? [gn!] : nil)]
        }

        // Telecoms
        var telecoms: [FHIRContactPoint] = []
        if let tel = p.tel?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
            telecoms.append(FHIRContactPoint(system: "phone", value: tel))
        }
        if let eml = p.eml?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
            telecoms.append(FHIRContactPoint(system: "email", value: eml))
        }
        for t in p.tels {
            if let v = t.value?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
                telecoms.append(FHIRContactPoint(system: t.system ?? "phone", value: v, use: t.use))
            }
        }

        // Addresses
        var addresses: [FHIRAddress] = []
        if let adr = p.adr?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
            addresses.append(FHIRAddress(text: adr, line: [adr]))
        }
        for a in p.adrs {
            addresses.append(FHIRAddress(use: a.use, line: a.line.map { [$0] }, city: a.city, postalCode: a.postalCode, country: a.country))
        }

        // Language
        var comms: [FHIRCommunication]? = nil
        if let lang = p.lang?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
            comms = [FHIRCommunication(language: FHIRCodeableConcept(coding: [
                FHIRCoding(system: "urn:ietf:bcp:47", code: lang)
            ]))]
        }

        // Contacts (Emergency Contacts)
        var fhirContacts: [FHIRPatientContact] = []
        for c in p.ct {
            let n = c.n?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            let phone = c.p?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            let email = c.e?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            let adr = c.adr?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty

            // A blank or empty contact is ignored
            if n == nil && phone == nil && email == nil && adr == nil {
                continue
            }

            var contactTelecoms: [FHIRContactPoint] = []
            if let phone = phone {
                contactTelecoms.append(FHIRContactPoint(system: "phone", value: phone))
            }
            if let email = email {
                contactTelecoms.append(FHIRContactPoint(system: "email", value: email))
            }

            var relationships: [FHIRCodeableConcept]? = nil
            if let rawRel = c.r?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
                let entry = IpsRelationshipCatalog.all.first {
                    $0.code.caseInsensitiveCompare(rawRel) == .orderedSame
                }
                if let entry = entry {
                    let localizedText = entry.pick(lang: uiLang).nonEmpty ?? entry.displayEn
                    relationships = [FHIRCodeableConcept(
                        coding: [FHIRCoding(
                            system: IpsRelationshipCatalog.codeSystem,
                            code: entry.code,
                            display: entry.displayEn
                        )],
                        text: localizedText
                    )]
                } else {
                    relationships = [FHIRCodeableConcept(
                        coding: nil,
                        text: rawRel
                    )]
                }
            }

            let contactName = n != nil ? FHIRHumanName(text: n) : nil
            let contactAddress = adr != nil ? FHIRAddress(text: adr) : nil

            fhirContacts.append(FHIRPatientContact(
                relationship: relationships,
                name: contactName,
                telecom: contactTelecoms.nonEmpty,
                address: contactAddress
            ))
        }

        return FHIRPatient(
            id: "patient-01",
            meta: FHIRMeta(profile: ["http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips"]),
            name: names,
            telecom: telecoms.nonEmpty,
            gender: mapGender(p.gs),
            birthDate: p.bd?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            address: addresses.nonEmpty,
            communication: comms,
            contact: fhirContacts.nonEmpty
        )
    }

    private static func mapGender(_ gs: String?) -> String? {
        guard let gs = gs?.uppercased().trimmingCharacters(in: .whitespacesAndNewlines) else { return nil }
        switch gs {
        case "M": return "male"
        case "F": return "female"
        case "O": return "other"
        case "U": return "unknown"
        default: return nil
        }
    }

    private static func buildDosage(m: JMedication) -> [FHIRDosage]? {
        let routeConcept: FHIRCodeableConcept?
        if let r = m.r?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
            let key = r.uppercased()
            switch key {
            case "O", "ORAL":
                routeConcept = FHIRCodeableConcept(coding: [FHIRCoding(system: sysSnomed, code: "26643006", display: "Oral")], text: "Oral")
            case "T", "TOPICAL":
                routeConcept = FHIRCodeableConcept(coding: [FHIRCoding(system: sysSnomed, code: "6064005", display: "Topical")], text: "Topical")
            case "S", "SUBCUTANEOUS":
                routeConcept = FHIRCodeableConcept(coding: [FHIRCoding(system: sysSnomed, code: "34206005", display: "Subcutaneous")], text: "Subcutaneous")
            case "H", "INHALATION":
                routeConcept = FHIRCodeableConcept(coding: [FHIRCoding(system: sysSnomed, code: "447694001", display: "Inhalation")], text: "Inhalation")
            default:
                routeConcept = FHIRCodeableConcept(text: r)
            }
        } else {
            routeConcept = nil
        }

        var timing: FHIRTiming? = nil
        if let t = m.t?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
            timing = FHIRTiming(code: FHIRCodeableConcept(text: t))
        }

        var doseQuantity: FHIRQuantity? = nil
        if let vStr = m.v?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
           let vVal = Double(vStr.replacingOccurrences(of: ",", with: ".")) {
            doseQuantity = FHIRQuantity(value: vVal, unit: m.u)
        }

        if routeConcept == nil && timing == nil && doseQuantity == nil {
            return nil
        }

        return [FHIRDosage(
            timing: timing,
            route: routeConcept,
            doseAndRate: doseQuantity != nil ? [FHIRDoseAndRate(doseQuantity: doseQuantity)] : nil
        )]
    }
}

private extension Array {
    var nonEmpty: [Element]? {
        return isEmpty ? nil : self
    }
}

private extension String {
    var nonEmpty: String? {
        return isEmpty ? nil : self
    }
}
