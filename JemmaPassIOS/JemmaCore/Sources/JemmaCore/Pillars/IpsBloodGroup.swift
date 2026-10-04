import Foundation

// MARK: - IpsBloodGroup
/// Blood group mapping and LOINC 882-1 Observation handling.
public enum IpsBloodGroup: Sendable {
    public static let loincAboRh = "882-1"
    public static let displayAboRh = "ABO and Rh blood group"
    public static let derivedIdPrefix = "rs-blood-group-"

    public static let snomed: [String: (code: String, display: String)] = [
        "O+":  ("278147001", "Blood group O Rh(D) positive"),
        "O-":  ("278148006", "Blood group O Rh(D) negative"),
        "A+":  ("278149003", "Blood group A Rh(D) positive"),
        "A-":  ("278152006", "Blood group A Rh(D) negative"),
        "B+":  ("278150003", "Blood group B Rh(D) positive"),
        "B-":  ("278153001", "Blood group B Rh(D) negative"),
        "AB+": ("278151004", "Blood group AB Rh(D) positive"),
        "AB-": ("278154007", "Blood group AB Rh(D) negative"),
    ]

    public static let labels: [String] = ["O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-"]

    public static func normalize(_ raw: String?) -> String? {
        guard let raw = raw else { return nil }
        let t = raw.uppercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "RH", with: "")
            .replacingOccurrences(of: "(D)", with: "")

        let aboPrefixes = ["AB", "A", "B", "O"]
        guard let matchedAbo = aboPrefixes.first(where: { t.hasPrefix($0) }) else {
            return nil
        }
        let rest = String(t.dropFirst(matchedAbo.count))
        let rh: String
        if rest.hasPrefix("+") || rest.hasPrefix("POS") {
            rh = "+"
        } else if rest.hasPrefix("-") || rest.hasPrefix("NEG") || rest.hasPrefix("−") {
            rh = "-"
        } else {
            return nil
        }
        return matchedAbo + rh
    }

    public static func snomedCode(_ raw: String?) -> String? {
        guard let norm = normalize(raw) else { return nil }
        return snomed[norm]?.code
    }

    public static func snomedDisplay(_ raw: String?) -> String? {
        guard let norm = normalize(raw) else { return nil }
        return snomed[norm]?.display
    }

    public static func labelFromSnomed(_ code: String?) -> String? {
        guard let code = code?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty else {
            return nil
        }
        return snomed.first(where: { $0.value.code == code })?.key
    }

    public static func labelFromSnomedDisplay(_ display: String?) -> String? {
        guard let display = display?.trimmingCharacters(in: .whitespacesAndNewlines), !display.isEmpty else {
            return nil
        }
        return snomed.first(where: { $0.value.display.caseInsensitiveCompare(display) == .orderedSame })?.key
    }

    public static func derivedId(profileId: String) -> String {
        return IpsFhirCodec.fhirId(derivedIdPrefix + profileId)
    }
}
