import Foundation

// MARK: - CodeLabelResolver
/// Protocol and default resolvers for localized interface labels following PROTOCOL §9.1.
public protocol CodeLabelResolver: Sendable {
    func getLabel(system: String, code: String, lang: String) -> String?
}

public struct EmptyCodeLabelResolver: CodeLabelResolver, Sendable {
    public init() {}
    public func getLabel(system: String, code: String, lang: String) -> String? {
        return nil
    }
}

public struct DictionaryCodeLabelResolver: CodeLabelResolver, Sendable {
    private let labels: [String: [String: String]] // key -> [lang -> label]

    public init(labels: [String: [String: String]] = [:]) {
        self.labels = labels
    }

    public func getLabel(system: String, code: String, lang: String) -> String? {
        guard let key = codeLabelResourceName(system: system, code: code) else {
            return nil
        }
        let langPrefix = String(lang.lowercased().prefix(2))
        return labels[key]?[langPrefix] ?? labels[key]?["en"]
    }
}

public enum CodeLabelResolvers {
    public static let none: any CodeLabelResolver = EmptyCodeLabelResolver()
}

/// Maps a code system URI and a code to its resource key according to PROTOCOL §9.1:
/// - "http://snomed.info/sct" -> prefix "sct"
/// - "http://loinc.org" -> prefix "loinc"
/// - "http://terminology.hl7.org/CodeSystem/v3-RoleCode" -> prefix "v3"
/// Result format: "code_label_<prefix>_<cleaned_code>".
public func codeLabelResourceName(system: String, code: String) -> String? {
    let s = system.trimmingCharacters(in: .whitespacesAndNewlines)
    let prefix: String
    switch s {
    case "http://snomed.info/sct", "sct":
        prefix = "sct"
    case "http://loinc.org", "loinc":
        prefix = "loinc"
    case "http://terminology.hl7.org/CodeSystem/v3-RoleCode", "v3":
        prefix = "v3"
    default:
        return nil
    }

    let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: "[^A-Za-z0-9]", with: "_", options: .regularExpression)
    return "code_label_\(prefix)_\(cleanCode)"
}
