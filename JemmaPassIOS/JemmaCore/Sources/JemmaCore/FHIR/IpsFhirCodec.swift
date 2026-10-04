import Foundation
import CryptoKit

// MARK: - IpsFhirCodec
public enum IpsFhirCodec: Sendable {
    public static let loincSectionImmunizations = "11369-6"
    public static let profileImmunizationUvIps = "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"

    public static let loincSectionProcedures = "47519-4"
    public static let profileProcedureUvIps = "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips"

    public static let loincSectionDevices = "46264-8"
    public static let profileDeviceUseStatementUvIps = "http://hl7.org/fhir/uv/ips/StructureDefinition/DeviceUseStatement-uv-ips"
    public static let profileDeviceUvIps = "http://hl7.org/fhir/uv/ips/StructureDefinition/Device-uv-ips"

    public static let loincSectionResults = "30954-2"
    public static let profileObservationResultsUvIps = "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-uv-ips"

    public static let loincSectionFunctional = "47420-5"
    public static let loincSectionPregnancy = "10162-6"
    public static let loincSectionProblems = "11450-4"
    public static let loincSectionPastIllness = "11348-0"

    // MARK: - Deterministic UUID v3 Generation (Matching Java UUID.nameUUIDFromBytes)
    public static func stableUUID(seed: String) -> UUID {
        let digest = Insecure.MD5.hash(data: Data(seed.utf8))
        var bytes = Array(digest)
        bytes[6] = (bytes[6] & 0x0F) | 0x30 // Version 3
        bytes[8] = (bytes[8] & 0x3F) | 0x80 // RFC 4122 variant

        let tuple: uuid_t = (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11],
            bytes[12], bytes[13], bytes[14], bytes[15]
        )
        return UUID(uuid: tuple)
    }

    public static func stableUrn(_ seed: String) -> String {
        return "urn:uuid:" + stableUUID(seed: seed).uuidString.lowercased()
    }

    /// FHIR resource ids allow [A-Za-z0-9\-.]{1,64} only (profile sids carry underscores).
    public static func fhirId(_ raw: String) -> String {
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-")
        let replaced = raw.unicodeScalars.map { allowed.contains($0) ? Character($0) : "-" }
        return String(String(replaced).prefix(64))
    }

    public static func resultUrn(profileSid: String, resultId: String) -> String {
        return stableUrn("\(profileSid)|Observation|\(resultId)")
    }

    public static func deviceUrn(profileSid: String, deviceEntryId: String) -> String {
        return stableUrn("\(profileSid)|Device|\(deviceEntryId)")
    }

    public static func deviceUseStatementUrn(profileSid: String, entryId: String) -> String {
        return stableUrn("\(profileSid)|DeviceUseStatement|\(entryId)")
    }

    // MARK: - Device Extraction & Export
    public static func devicesOf(bundle: FHIRBundle) -> [IpsDevice] {
        var devicesByUrl: [String: FHIRDevice] = [:]
        var devicesById: [String: FHIRDevice] = [:]

        for entry in bundle.entry {
            if case .device(let d) = entry.resource {
                devicesByUrl[entry.fullUrl] = d
                if let id = d.id {
                    devicesById[id] = d
                }
            }
        }

        var results: [IpsDevice] = []
        for entry in bundle.entry {
            if case .deviceUseStatement(let st) = entry.resource {
                let ref = st.device.reference
                var matchingDevice: FHIRDevice? = nil
                if let ref = ref {
                    matchingDevice = devicesByUrl[ref]
                    if matchingDevice == nil {
                        let id = ref.components(separatedBy: "/").last ?? ref
                        matchingDevice = devicesById[id]
                    }
                }

                let firstCoding = matchingDevice?.type?.coding?.first
                let code = firstCoding?.code?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                let display = firstCoding?.display?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                let typeText = matchingDevice?.type?.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                let devName = matchingDevice?.deviceName?.first?.name.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                let text = (typeText ?? devName) != display ? (typeText ?? devName) : nil

                let udi = matchingDevice?.udiCarrier?.first?.deviceIdentifier?.nonEmpty
                    ?? matchingDevice?.udiCarrier?.first?.carrierHRF?.nonEmpty

                let rawStatus = matchingDevice?.status ?? st.status
                let status = IpsDeviceStatus.normalize(rawStatus)

                let deviceId = st.id ?? UUID().uuidString.lowercased()

                let ipsDev = IpsDevice(
                    id: deviceId,
                    code: code,
                    system: firstCoding?.system?.nonEmpty ?? "http://snomed.info/sct",
                    display: display,
                    text: text,
                    udi: udi,
                    manufacturer: matchingDevice?.manufacturer?.nonEmpty,
                    model: matchingDevice?.modelNumber?.nonEmpty,
                    serial: matchingDevice?.serialNumber?.nonEmpty,
                    date: st.timingDateTime?.nonEmpty,
                    status: status,
                    bodySite: st.bodySite?.text?.nonEmpty,
                    note: st.note?.first?.text.nonEmpty
                )
                results.append(ipsDev)
            }
        }
        return results
    }

    public static func toFhirDevice(dv: IpsDevice, patientUrn: String) -> FHIRDevice {
        let coding: [FHIRCoding]?
        if let code = dv.code?.nonEmpty {
            coding = [FHIRCoding(
                system: dv.system?.nonEmpty ?? "http://snomed.info/sct",
                code: code,
                display: dv.display?.nonEmpty
            )]
        } else {
            coding = nil
        }

        let typeText = dv.text?.nonEmpty ?? dv.display?.nonEmpty
        let typeConcept = (coding != nil || typeText != nil) ? FHIRCodeableConcept(coding: coding, text: typeText) : nil

        let udiCarriers: [FHIRUdiCarrier]?
        if let udi = dv.udi?.nonEmpty {
            udiCarriers = [FHIRUdiCarrier(deviceIdentifier: udi, carrierHRF: udi)]
        } else {
            udiCarriers = nil
        }

        let devStatus: String
        switch IpsDeviceStatus.normalize(dv.status) {
        case IpsDeviceStatus.inactive: devStatus = "inactive"
        case IpsDeviceStatus.enteredInError: devStatus = "entered-in-error"
        default: devStatus = "active"
        }

        var deviceNames: [FHIRDeviceName]? = nil
        if let t = dv.text?.nonEmpty, !dv.hasCode {
            deviceNames = [FHIRDeviceName(name: t)]
        }

        return FHIRDevice(
            id: "\(dv.id)-device",
            meta: FHIRMeta(profile: [profileDeviceUvIps]),
            udiCarrier: udiCarriers,
            status: devStatus,
            manufacturer: dv.manufacturer?.nonEmpty,
            serialNumber: dv.serial?.nonEmpty,
            modelNumber: dv.model?.nonEmpty,
            type: typeConcept,
            patient: FHIRReference(reference: patientUrn),
            deviceName: deviceNames
        )
    }

    public static func toFhirUseStatement(dv: IpsDevice, patientUrn: String, deviceUrn: String) -> FHIRDeviceUseStatement {
        let statusCode: String
        switch IpsDeviceStatus.normalize(dv.status) {
        case IpsDeviceStatus.inactive: statusCode = "completed"
        case IpsDeviceStatus.enteredInError: statusCode = "entered-in-error"
        default: statusCode = "active"
        }

        let bodyConcept = dv.bodySite?.nonEmpty != nil ? FHIRCodeableConcept(text: dv.bodySite) : nil
        let notes = dv.note?.nonEmpty != nil ? [FHIRAnnotation(text: dv.note!)] : nil

        return FHIRDeviceUseStatement(
            id: dv.id,
            meta: FHIRMeta(profile: [profileDeviceUseStatementUvIps]),
            status: statusCode,
            subject: FHIRReference(reference: patientUrn),
            device: FHIRReference(reference: deviceUrn),
            timingDateTime: dv.date?.nonEmpty,
            bodySite: bodyConcept,
            note: notes
        )
    }

    // MARK: - Bundle Parsing & Contact Extraction
    public static func parseBundle(jsonString: String) throws -> FHIRBundle {
        let data = Data(jsonString.utf8)
        let decoder = JSONDecoder()
        return try decoder.decode(FHIRBundle.self, from: data)
    }

    public static func contactsOf(bundle: FHIRBundle) -> [JContact] {
        guard let patientEntry = bundle.entry.first(where: {
            if case .patient = $0.resource { return true }
            return false
        }), case .patient(let patient) = patientEntry.resource else {
            return []
        }

        guard let contacts = patient.contact else { return [] }

        return contacts.map { c in
            let name = c.name?.text
            let phone = c.telecom?.first(where: { $0.system == "phone" })?.value
            let email = c.telecom?.first(where: { $0.system == "email" })?.value
            let address = c.address?.text

            var relationship: String? = nil
            if let rel = c.relationship?.first {
                if let coding = rel.coding?.first(where: { $0.system == IpsRelationshipCatalog.codeSystem }) {
                    relationship = coding.code
                } else if let text = rel.text, !text.isEmpty {
                    relationship = text
                }
            }

            return JContact(
                n: name,
                r: relationship,
                p: phone,
                e: email,
                adr: address
            )
        }
    }
}
