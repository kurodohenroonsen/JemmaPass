import Foundation

// MARK: - IpsDeviceStatus
public enum IpsDeviceStatus: Sendable {
    public static let active = "active"
    public static let inactive = "inactive"
    public static let enteredInError = "entered-in-error"
    public static let all = [active, inactive, enteredInError]

    public static func normalize(_ raw: String?) -> String {
        guard let s = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else { return active }
        switch s {
        case inactive, "completed", "stopped": return inactive
        case enteredInError: return enteredInError
        default: return active
        }
    }
}

// MARK: - IpsDevice
public struct IpsDevice: Sendable, Equatable {
    public var id: String
    public var code: String?
    public var system: String?
    public var display: String?
    public var text: String?
    public var udi: String?
    public var manufacturer: String?
    public var model: String?
    public var serial: String?
    public var date: String?
    public var status: String
    public var bodySite: String?
    public var note: String?

    public init(
        id: String,
        code: String? = nil,
        system: String? = "http://snomed.info/sct",
        display: String? = nil,
        text: String? = nil,
        udi: String? = nil,
        manufacturer: String? = nil,
        model: String? = nil,
        serial: String? = nil,
        date: String? = nil,
        status: String = IpsDeviceStatus.active,
        bodySite: String? = nil,
        note: String? = nil
    ) {
        self.id = id
        self.code = code
        self.system = system
        self.display = display
        self.text = text
        self.udi = udi
        self.manufacturer = manufacturer
        self.model = model
        self.serial = serial
        self.date = date
        self.status = status
        self.bodySite = bodySite
        self.note = note
    }

    public var hasCode: Bool {
        guard let c = code?.trimmingCharacters(in: .whitespacesAndNewlines) else { return false }
        return !c.isEmpty
    }

    public func label() -> String {
        if let d = display?.trimmingCharacters(in: .whitespacesAndNewlines), !d.isEmpty {
            return d
        }
        if let t = text?.trimmingCharacters(in: .whitespacesAndNewlines), !t.isEmpty {
            return t
        }
        return code ?? ""
    }

    public func toJEntry() -> JEntryGeneric {
        let trimmedCode = code?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        let trimmedNote = note?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        let trimmedDate = date?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        let trimmedLabel = label().trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty

        let nonDefaultSystem = (system != "http://snomed.info/sct") ? system?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty : nil
        let nonDefaultStatus = (status != IpsDeviceStatus.active) ? status : nil

        return JEntryGeneric(
            c: trimmedCode,
            d: trimmedNote,
            d_display: trimmedLabel,
            dt: trimmedDate,
            cs: nonDefaultSystem,
            st: nonDefaultStatus
        )
    }

    public static func fromJEntry(_ entry: JEntryGeneric, index: Int = 0) -> IpsDevice {
        let code = entry.c?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        let seed = "dv|\(index)|\(code ?? "")|\(entry.dt ?? "")|\(entry.d_display ?? "")"
        let stableId = IpsFhirCodec.stableUUID(seed: seed).uuidString.lowercased()
        let disp = code != nil ? entry.d_display?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty : nil
        let txt = code == nil ? entry.d_display?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty : nil

        return IpsDevice(
            id: stableId,
            code: code,
            system: entry.cs?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? "http://snomed.info/sct",
            display: disp,
            text: txt,
            date: entry.dt?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            status: IpsDeviceStatus.normalize(entry.st),
            note: entry.d?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        )
    }
}
