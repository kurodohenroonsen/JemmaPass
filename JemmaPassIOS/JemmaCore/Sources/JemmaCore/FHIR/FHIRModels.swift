import Foundation

// MARK: - FHIR R4 IPS Core Models

public struct FHIRBundle: Codable, Sendable, Equatable {
    public var resourceType: String = "Bundle"
    public var id: String?
    public var identifier: FHIRIdentifier?
    public var type: String = "document"
    public var timestamp: String?
    public var entry: [FHIRBundleEntry]

    public init(
        id: String? = nil,
        identifier: FHIRIdentifier? = nil,
        type: String = "document",
        timestamp: String? = nil,
        entry: [FHIRBundleEntry] = []
    ) {
        self.id = id
        self.identifier = identifier
        self.type = type
        self.timestamp = timestamp
        self.entry = entry
    }
}

public struct FHIRBundleEntry: Codable, Sendable, Equatable {
    public var fullUrl: String
    public var resource: FHIRResource

    public init(fullUrl: String, resource: FHIRResource) {
        self.fullUrl = fullUrl
        self.resource = resource
    }

    enum CodingKeys: String, CodingKey {
        case fullUrl
        case resource
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.fullUrl = try container.decode(String.self, forKey: .fullUrl)
        self.resource = try container.decode(FHIRResource.self, forKey: .resource)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(fullUrl, forKey: .fullUrl)
        try container.encode(resource, forKey: .resource)
    }
}

// MARK: - Polymorphic FHIR Resource
public enum FHIRResource: Codable, Sendable, Equatable {
    case patient(FHIRPatient)
    case composition(FHIRComposition)
    case allergyIntolerance(FHIRAllergyIntolerance)
    case medicationStatement(FHIRMedicationStatement)
    case medication(FHIRMedication)
    case condition(FHIRCondition)
    case observation(FHIRObservation)
    case immunization(FHIRImmunization)
    case procedure(FHIRProcedure)
    case deviceUseStatement(FHIRDeviceUseStatement)
    case device(FHIRDevice)
    case other([String: AnyCodable])

    public var resourceType: String {
        switch self {
        case .patient: return "Patient"
        case .composition: return "Composition"
        case .allergyIntolerance: return "AllergyIntolerance"
        case .medicationStatement: return "MedicationStatement"
        case .medication: return "Medication"
        case .condition: return "Condition"
        case .observation: return "Observation"
        case .immunization: return "Immunization"
        case .procedure: return "Procedure"
        case .deviceUseStatement: return "DeviceUseStatement"
        case .device: return "Device"
        case .other(let dict): return dict["resourceType"]?.value as? String ?? "Unknown"
        }
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: DynamicCodingKey.self)
        guard let typeKey = DynamicCodingKey(stringValue: "resourceType"),
              let type = try container.decodeIfPresent(String.self, forKey: typeKey) else {
            let dict = try decoder.singleValueContainer().decode([String: AnyCodable].self)
            self = .other(dict)
            return
        }

        switch type {
        case "Patient":
            self = .patient(try FHIRPatient(from: decoder))
        case "Composition":
            self = .composition(try FHIRComposition(from: decoder))
        case "AllergyIntolerance":
            self = .allergyIntolerance(try FHIRAllergyIntolerance(from: decoder))
        case "MedicationStatement":
            self = .medicationStatement(try FHIRMedicationStatement(from: decoder))
        case "Medication":
            self = .medication(try FHIRMedication(from: decoder))
        case "Condition":
            self = .condition(try FHIRCondition(from: decoder))
        case "Observation":
            self = .observation(try FHIRObservation(from: decoder))
        case "Immunization":
            self = .immunization(try FHIRImmunization(from: decoder))
        case "Procedure":
            self = .procedure(try FHIRProcedure(from: decoder))
        case "DeviceUseStatement":
            self = .deviceUseStatement(try FHIRDeviceUseStatement(from: decoder))
        case "Device":
            self = .device(try FHIRDevice(from: decoder))
        default:
            let dict = try decoder.singleValueContainer().decode([String: AnyCodable].self)
            self = .other(dict)
        }
    }

    public func encode(to encoder: Encoder) throws {
        switch self {
        case .patient(let p): try p.encode(to: encoder)
        case .composition(let c): try c.encode(to: encoder)
        case .allergyIntolerance(let a): try a.encode(to: encoder)
        case .medicationStatement(let m): try m.encode(to: encoder)
        case .medication(let med): try med.encode(to: encoder)
        case .condition(let cond): try cond.encode(to: encoder)
        case .observation(let obs): try obs.encode(to: encoder)
        case .immunization(let im): try im.encode(to: encoder)
        case .procedure(let pr): try pr.encode(to: encoder)
        case .deviceUseStatement(let dus): try dus.encode(to: encoder)
        case .device(let dev): try dev.encode(to: encoder)
        case .other(let dict): try dict.encode(to: encoder)
        }
    }
}

// MARK: - Patient Resource
public struct FHIRPatient: Codable, Sendable, Equatable {
    public var resourceType: String = "Patient"
    public var id: String?
    public var meta: FHIRMeta?
    public var identifier: [FHIRIdentifier]?
    public var name: [FHIRHumanName]?
    public var telecom: [FHIRContactPoint]?
    public var gender: String?
    public var birthDate: String?
    public var address: [FHIRAddress]?
    public var communication: [FHIRCommunication]?
    public var contact: [FHIRPatientContact]?

    public init(
        id: String? = nil,
        meta: FHIRMeta? = nil,
        identifier: [FHIRIdentifier]? = nil,
        name: [FHIRHumanName]? = nil,
        telecom: [FHIRContactPoint]? = nil,
        gender: String? = nil,
        birthDate: String? = nil,
        address: [FHIRAddress]? = nil,
        communication: [FHIRCommunication]? = nil,
        contact: [FHIRPatientContact]? = nil
    ) {
        self.id = id
        self.meta = meta
        self.identifier = identifier
        self.name = name
        self.telecom = telecom
        self.gender = gender
        self.birthDate = birthDate
        self.address = address
        self.communication = communication
        self.contact = contact
    }
}

// MARK: - Patient.contact
public struct FHIRPatientContact: Codable, Sendable, Equatable {
    public var relationship: [FHIRCodeableConcept]?
    public var name: FHIRHumanName?
    public var telecom: [FHIRContactPoint]?
    public var address: FHIRAddress?

    public init(
        relationship: [FHIRCodeableConcept]? = nil,
        name: FHIRHumanName? = nil,
        telecom: [FHIRContactPoint]? = nil,
        address: FHIRAddress? = nil
    ) {
        self.relationship = relationship
        self.name = name
        self.telecom = telecom
        self.address = address
    }
}

// MARK: - Composition Resource
public struct FHIRComposition: Codable, Sendable, Equatable {
    public var resourceType: String = "Composition"
    public var id: String?
    public var status: String = "final"
    public var type: FHIRCodeableConcept
    public var subject: FHIRReference
    public var date: String
    public var author: [FHIRAuthor]
    public var title: String
    public var confidentiality: String?
    public var section: [FHIRSection]

    public init(
        id: String? = nil,
        status: String = "final",
        type: FHIRCodeableConcept,
        subject: FHIRReference,
        date: String,
        author: [FHIRAuthor],
        title: String,
        confidentiality: String? = "N",
        section: [FHIRSection] = []
    ) {
        self.id = id
        self.status = status
        self.type = type
        self.subject = subject
        self.date = date
        self.author = author
        self.title = title
        self.confidentiality = confidentiality
        self.section = section
    }
}

public struct FHIRAuthor: Codable, Sendable, Equatable {
    public var display: String

    public init(display: String) {
        self.display = display
    }
}

public struct FHIRSection: Codable, Sendable, Equatable {
    public var title: String
    public var code: FHIRCodeableConcept
    public var entry: [FHIRReference]

    public init(title: String, code: FHIRCodeableConcept, entry: [FHIRReference]) {
        self.title = title
        self.code = code
        self.entry = entry
    }
}

// MARK: - Clinical Resources
public struct FHIRAllergyIntolerance: Codable, Sendable, Equatable {
    public var resourceType: String = "AllergyIntolerance"
    public var id: String?
    public var clinicalStatus: FHIRCodeableConcept
    public var verificationStatus: FHIRCodeableConcept
    public var type: String?
    public var criticality: String?
    public var code: FHIRCodeableConcept?
    public var patient: FHIRReference

    public init(
        id: String? = nil,
        clinicalStatus: FHIRCodeableConcept,
        verificationStatus: FHIRCodeableConcept,
        type: String? = nil,
        criticality: String? = nil,
        code: FHIRCodeableConcept? = nil,
        patient: FHIRReference
    ) {
        self.id = id
        self.clinicalStatus = clinicalStatus
        self.verificationStatus = verificationStatus
        self.type = type
        self.criticality = criticality
        self.code = code
        self.patient = patient
    }
}

public struct FHIRMedicationStatement: Codable, Sendable, Equatable {
    public var resourceType: String = "MedicationStatement"
    public var id: String?
    public var status: String
    public var medicationReference: FHIRReference?
    public var subject: FHIRReference
    public var effectiveDateTime: String?
    public var dosage: [FHIRDosage]?

    public init(
        id: String? = nil,
        status: String,
        medicationReference: FHIRReference? = nil,
        subject: FHIRReference,
        effectiveDateTime: String? = nil,
        dosage: [FHIRDosage]? = nil
    ) {
        self.id = id
        self.status = status
        self.medicationReference = medicationReference
        self.subject = subject
        self.effectiveDateTime = effectiveDateTime
        self.dosage = dosage
    }
}

public struct FHIRDosage: Codable, Sendable, Equatable {
    public var text: String?
    public var timing: FHIRTiming?
    public var route: FHIRCodeableConcept?
    public var doseAndRate: [FHIRDoseAndRate]?

    public init(
        text: String? = nil,
        timing: FHIRTiming? = nil,
        route: FHIRCodeableConcept? = nil,
        doseAndRate: [FHIRDoseAndRate]? = nil
    ) {
        self.text = text
        self.timing = timing
        self.route = route
        self.doseAndRate = doseAndRate
    }
}

public struct FHIRTiming: Codable, Sendable, Equatable {
    public var code: FHIRCodeableConcept?
    public init(code: FHIRCodeableConcept? = nil) { self.code = code }
}

public struct FHIRDoseAndRate: Codable, Sendable, Equatable {
    public var doseQuantity: FHIRQuantity?
    public init(doseQuantity: FHIRQuantity? = nil) { self.doseQuantity = doseQuantity }
}

public struct FHIRMedication: Codable, Sendable, Equatable {
    public var resourceType: String = "Medication"
    public var id: String?
    public var code: FHIRCodeableConcept?

    public init(id: String? = nil, code: FHIRCodeableConcept? = nil) {
        self.id = id
        self.code = code
    }
}

public struct FHIRCondition: Codable, Sendable, Equatable {
    public var resourceType: String = "Condition"
    public var id: String?
    public var clinicalStatus: FHIRCodeableConcept?
    public var verificationStatus: FHIRCodeableConcept?
    public var category: [FHIRCodeableConcept]?
    public var severity: FHIRCodeableConcept?
    public var code: FHIRCodeableConcept?
    public var subject: FHIRReference
    public var onsetDateTime: String?
    public var abatementDateTime: String?

    public init(
        id: String? = nil,
        clinicalStatus: FHIRCodeableConcept? = nil,
        verificationStatus: FHIRCodeableConcept? = nil,
        category: [FHIRCodeableConcept]? = nil,
        severity: FHIRCodeableConcept? = nil,
        code: FHIRCodeableConcept? = nil,
        subject: FHIRReference,
        onsetDateTime: String? = nil,
        abatementDateTime: String? = nil
    ) {
        self.id = id
        self.clinicalStatus = clinicalStatus
        self.verificationStatus = verificationStatus
        self.category = category
        self.severity = severity
        self.code = code
        self.subject = subject
        self.onsetDateTime = onsetDateTime
        self.abatementDateTime = abatementDateTime
    }
}

public struct FHIRExtension: Codable, Sendable, Equatable {
    public var url: String
    public var valueCode: String?
    public var valueString: String?

    public init(url: String, valueCode: String? = nil, valueString: String? = nil) {
        self.url = url
        self.valueCode = valueCode
        self.valueString = valueString
    }
}

public struct FHIRElementExtension: Codable, Sendable, Equatable {
    public var `extension`: [FHIRExtension]

    enum CodingKeys: String, CodingKey {
        case `extension` = "extension"
    }

    public init(extension: [FHIRExtension]) {
        self.extension = `extension`
    }
}

public struct FHIRObservation: Codable, Sendable, Equatable {
    public var resourceType: String = "Observation"
    public var id: String?
    public var meta: FHIRMeta?
    public var status: String = "final"
    public var category: [FHIRCodeableConcept]?
    public var code: FHIRCodeableConcept
    public var subject: FHIRReference
    public var performer: [FHIRReference]?
    public var effectiveDateTime: String?
    public var _effectiveDateTime: FHIRElementExtension?
    public var valueQuantity: FHIRQuantity?
    public var valueCodeableConcept: FHIRCodeableConcept?
    public var valueString: String?
    public var interpretation: [FHIRCodeableConcept]?

    enum CodingKeys: String, CodingKey {
        case resourceType
        case id
        case meta
        case status
        case category
        case code
        case subject
        case performer
        case effectiveDateTime
        case _effectiveDateTime = "_effectiveDateTime"
        case valueQuantity
        case valueCodeableConcept
        case valueString
        case interpretation
    }

    public init(
        id: String? = nil,
        meta: FHIRMeta? = nil,
        status: String = "final",
        category: [FHIRCodeableConcept]? = nil,
        code: FHIRCodeableConcept,
        subject: FHIRReference,
        performer: [FHIRReference]? = nil,
        effectiveDateTime: String? = nil,
        _effectiveDateTime: FHIRElementExtension? = nil,
        valueQuantity: FHIRQuantity? = nil,
        valueCodeableConcept: FHIRCodeableConcept? = nil,
        valueString: String? = nil,
        interpretation: [FHIRCodeableConcept]? = nil
    ) {
        self.id = id
        self.meta = meta
        self.status = status
        self.category = category
        self.code = code
        self.subject = subject
        self.performer = performer
        self.effectiveDateTime = effectiveDateTime
        self._effectiveDateTime = _effectiveDateTime
        self.valueQuantity = valueQuantity
        self.valueCodeableConcept = valueCodeableConcept
        self.valueString = valueString
        self.interpretation = interpretation
    }
}

public struct FHIRImmunization: Codable, Sendable, Equatable {
    public var resourceType: String = "Immunization"
    public var id: String?
    public var status: String
    public var vaccineCode: FHIRCodeableConcept
    public var patient: FHIRReference
    public var occurrenceDateTime: String?

    public init(
        id: String? = nil,
        status: String = "completed",
        vaccineCode: FHIRCodeableConcept,
        patient: FHIRReference,
        occurrenceDateTime: String? = nil
    ) {
        self.id = id
        self.status = status
        self.vaccineCode = vaccineCode
        self.patient = patient
        self.occurrenceDateTime = occurrenceDateTime
    }
}

public struct FHIRProcedure: Codable, Sendable, Equatable {
    public var resourceType: String = "Procedure"
    public var id: String?
    public var status: String
    public var code: FHIRCodeableConcept
    public var subject: FHIRReference
    public var performedDateTime: String?

    public init(
        id: String? = nil,
        status: String = "completed",
        code: FHIRCodeableConcept,
        subject: FHIRReference,
        performedDateTime: String? = nil
    ) {
        self.id = id
        self.status = status
        self.code = code
        self.subject = subject
        self.performedDateTime = performedDateTime
    }
}

public struct FHIRUdiCarrier: Codable, Sendable, Equatable {
    public var deviceIdentifier: String?
    public var carrierHRF: String?

    public init(deviceIdentifier: String? = nil, carrierHRF: String? = nil) {
        self.deviceIdentifier = deviceIdentifier
        self.carrierHRF = carrierHRF
    }
}

public struct FHIRDeviceName: Codable, Sendable, Equatable {
    public var name: String
    public var type: String

    public init(name: String, type: String = "patient-reported-name") {
        self.name = name
        self.type = type
    }
}

public struct FHIRDevice: Codable, Sendable, Equatable {
    public var resourceType: String = "Device"
    public var id: String?
    public var meta: FHIRMeta?
    public var udiCarrier: [FHIRUdiCarrier]?
    public var status: String?
    public var manufacturer: String?
    public var serialNumber: String?
    public var modelNumber: String?
    public var type: FHIRCodeableConcept?
    public var patient: FHIRReference?
    public var deviceName: [FHIRDeviceName]?

    public init(
        id: String? = nil,
        meta: FHIRMeta? = nil,
        udiCarrier: [FHIRUdiCarrier]? = nil,
        status: String? = "active",
        manufacturer: String? = nil,
        serialNumber: String? = nil,
        modelNumber: String? = nil,
        type: FHIRCodeableConcept? = nil,
        patient: FHIRReference? = nil,
        deviceName: [FHIRDeviceName]? = nil
    ) {
        self.id = id
        self.meta = meta
        self.udiCarrier = udiCarrier
        self.status = status
        self.manufacturer = manufacturer
        self.serialNumber = serialNumber
        self.modelNumber = modelNumber
        self.type = type
        self.patient = patient
        self.deviceName = deviceName
    }
}

public struct FHIRAnnotation: Codable, Sendable, Equatable {
    public var text: String

    public init(text: String) {
        self.text = text
    }
}

public struct FHIRDeviceUseStatement: Codable, Sendable, Equatable {
    public var resourceType: String = "DeviceUseStatement"
    public var id: String?
    public var meta: FHIRMeta?
    public var status: String
    public var subject: FHIRReference
    public var device: FHIRReference
    public var timingDateTime: String?
    public var bodySite: FHIRCodeableConcept?
    public var note: [FHIRAnnotation]?

    public init(
        id: String? = nil,
        meta: FHIRMeta? = nil,
        status: String = "active",
        subject: FHIRReference,
        device: FHIRReference,
        timingDateTime: String? = nil,
        bodySite: FHIRCodeableConcept? = nil,
        note: [FHIRAnnotation]? = nil
    ) {
        self.id = id
        self.meta = meta
        self.status = status
        self.subject = subject
        self.device = device
        self.timingDateTime = timingDateTime
        self.bodySite = bodySite
        self.note = note
    }
}

// MARK: - Common Datatypes
public struct FHIRMeta: Codable, Sendable, Equatable {
    public var profile: [String]?
    public init(profile: [String]? = nil) { self.profile = profile }
}

public struct FHIRIdentifier: Codable, Sendable, Equatable {
    public var system: String?
    public var value: String?

    public init(system: String? = nil, value: String? = nil) {
        self.system = system
        self.value = value
    }
}

public struct FHIRReference: Codable, Sendable, Equatable {
    public var reference: String?
    public var display: String?

    public init(reference: String? = nil, display: String? = nil) {
        self.reference = reference
        self.display = display
    }
}

public struct FHIRHumanName: Codable, Sendable, Equatable {
    public var text: String?
    public var family: String?
    public var given: [String]?

    public init(text: String? = nil, family: String? = nil, given: [String]? = nil) {
        self.text = text
        self.family = family
        self.given = given
    }
}

public struct FHIRContactPoint: Codable, Sendable, Equatable {
    public var system: String?
    public var value: String?
    public var use: String?

    public init(system: String? = nil, value: String? = nil, use: String? = nil) {
        self.system = system
        self.value = value
        self.use = use
    }
}

public struct FHIRAddress: Codable, Sendable, Equatable {
    public var use: String?
    public var text: String?
    public var line: [String]?
    public var city: String?
    public var postalCode: String?
    public var country: String?

    public init(
        use: String? = nil,
        text: String? = nil,
        line: [String]? = nil,
        city: String? = nil,
        postalCode: String? = nil,
        country: String? = nil
    ) {
        self.use = use
        self.text = text
        self.line = line
        self.city = city
        self.postalCode = postalCode
        self.country = country
    }
}

public struct FHIRCommunication: Codable, Sendable, Equatable {
    public var language: FHIRCodeableConcept
    public init(language: FHIRCodeableConcept) { self.language = language }
}

public struct FHIRCodeableConcept: Codable, Sendable, Equatable {
    public var coding: [FHIRCoding]?
    public var text: String?

    public init(coding: [FHIRCoding]? = nil, text: String? = nil) {
        self.coding = coding
        self.text = text
    }
}

public struct FHIRCoding: Codable, Sendable, Equatable {
    public var system: String?
    public var code: String?
    public var display: String?

    public init(system: String? = nil, code: String? = nil, display: String? = nil) {
        self.system = system
        self.code = code
        self.display = display
    }
}

public struct FHIRQuantity: Codable, Sendable, Equatable {
    public var value: Double?
    public var unit: String?
    public var system: String?
    public var code: String?

    public init(value: Double? = nil, unit: String? = nil, system: String? = nil, code: String? = nil) {
        self.value = value
        self.unit = unit
        self.system = system
        self.code = code
    }
}

// MARK: - Dynamic Coding Key
private struct DynamicCodingKey: CodingKey {
    var stringValue: String
    var intValue: Int?

    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { self.intValue = intValue; self.stringValue = String(intValue) }
}

// MARK: - AnyCodable for extensible / unsupported resources
public struct AnyCodable: Codable, @unchecked Sendable, Equatable {
    public let value: Any

    public init(_ value: Any) {
        self.value = value
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let boolVal = try? container.decode(Bool.self) {
            value = boolVal
        } else if let intVal = try? container.decode(Int.self) {
            value = intVal
        } else if let doubleVal = try? container.decode(Double.self) {
            value = doubleVal
        } else if let stringVal = try? container.decode(String.self) {
            value = stringVal
        } else if let arrayVal = try? container.decode([AnyCodable].self) {
            value = arrayVal.map { $0.value }
        } else if let dictVal = try? container.decode([String: AnyCodable].self) {
            value = dictVal.mapValues { $0.value }
        } else {
            value = ()
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case let num as Int: try container.encode(num)
        case let num as Double: try container.encode(num)
        case let str as String: try container.encode(str)
        case let bool as Bool: try container.encode(bool)
        case let arr as [Any]:
            try container.encode(arr.map { AnyCodable($0) })
        case let dict as [String: Any]:
            try container.encode(dict.mapValues { AnyCodable($0) })
        default:
            try container.encodeNil()
        }
    }

    public static func == (lhs: AnyCodable, rhs: AnyCodable) -> Bool {
        return String(describing: lhs.value) == String(describing: rhs.value)
    }
}
