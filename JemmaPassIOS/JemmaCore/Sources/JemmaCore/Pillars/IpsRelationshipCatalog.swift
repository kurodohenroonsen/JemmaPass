import Foundation

// MARK: - IpsRelationshipCatalog
/// Catalog of the 39 codes of HL7 v3 RoleCode in the FHIR IPS ValueSet `personal-relationship-uv-ips`.
/// Used for Patient.contact.relationship.
public struct RelationshipEntry: Sendable, Equatable {
    public let code: String
    public let displayEn: String
    public let displayFr: String
    public let displayJa: String

    public init(code: String, displayEn: String, displayFr: String, displayJa: String) {
        self.code = code
        self.displayEn = displayEn
        self.displayFr = displayFr
        self.displayJa = displayJa
    }

    public func pick(lang: String) -> String {
        let prefix = String(lang.lowercased().prefix(2))
        switch prefix {
        case "fr":
            return displayFr
        case "ja":
            return displayJa
        default:
            return displayEn
        }
    }

    public func compactLabel(lang: String) -> String {
        return "\(code) · \(pick(lang: lang))"
    }
}

public enum IpsRelationshipCatalog: Sendable {
    public static let codeSystem = "http://terminology.hl7.org/CodeSystem/v3-RoleCode"
    public static let valueSetId = "personal-relationship-uv-ips"

    public static let all: [RelationshipEntry] = [
        RelationshipEntry(code: "AUNT",      displayEn: "aunt",                 displayFr: "Tante",                       displayJa: "おば"),
        RelationshipEntry(code: "CHILD",     displayEn: "child",                displayFr: "Enfant",                      displayJa: "子"),
        RelationshipEntry(code: "CHLDADOPT", displayEn: "adopted child",        displayFr: "Enfant adopté(e)",            displayJa: "養子"),
        RelationshipEntry(code: "CHLDFOST",  displayEn: "foster child",         displayFr: "Enfant en famille d'accueil", displayJa: "里子"),
        RelationshipEntry(code: "CHLDINLAW", displayEn: "child in-law",         displayFr: "Belle-fille / Beau-fils",     displayJa: "義理の子"),
        RelationshipEntry(code: "COUSN",     displayEn: "cousin",               displayFr: "Cousin(e)",                   displayJa: "いとこ"),
        RelationshipEntry(code: "DAU",       displayEn: "natural daughter",     displayFr: "Fille (biologique)",          displayJa: "実の娘"),
        RelationshipEntry(code: "DAUADOPT",  displayEn: "adopted daughter",     displayFr: "Fille adoptée",               displayJa: "養女"),
        RelationshipEntry(code: "DAUC",      displayEn: "daughter",             displayFr: "Fille",                       displayJa: "娘"),
        RelationshipEntry(code: "DAUFOST",   displayEn: "foster daughter",      displayFr: "Fille en famille d'accueil",  displayJa: "里娘"),
        RelationshipEntry(code: "DAUINLAW",  displayEn: "daughter in-law",      displayFr: "Belle-fille",                 displayJa: "義理の娘"),
        RelationshipEntry(code: "DOMPART",   displayEn: "domestic partner",     displayFr: "Partenaire",                  displayJa: "パートナー"),
        RelationshipEntry(code: "FAMMEMB",   displayEn: "family member",        displayFr: "Membre de la famille",        displayJa: "家族"),
        RelationshipEntry(code: "FRND",      displayEn: "unrelated friend",     displayFr: "Ami(e)",                      displayJa: "友人"),
        RelationshipEntry(code: "FTH",       displayEn: "father",               displayFr: "Père",                        displayJa: "父"),
        RelationshipEntry(code: "FTHINLAW",  displayEn: "father-in-law",        displayFr: "Beau-père",                   displayJa: "義父"),
        RelationshipEntry(code: "GGRPRN",    displayEn: "great grandparent",    displayFr: "Arrière-grand-parent",        displayJa: "曾祖父母"),
        RelationshipEntry(code: "GRNDCHILD", displayEn: "grandchild",           displayFr: "Petit-enfant",                displayJa: "孫"),
        RelationshipEntry(code: "GRPRN",     displayEn: "grandparent",          displayFr: "Grand-parent",                displayJa: "祖父母"),
        RelationshipEntry(code: "MTH",       displayEn: "mother",               displayFr: "Mère",                        displayJa: "母"),
        RelationshipEntry(code: "MTHINLAW",  displayEn: "mother-in-law",        displayFr: "Belle-mère",                  displayJa: "義母"),
        RelationshipEntry(code: "NBOR",      displayEn: "neighbor",             displayFr: "Voisin(e)",                   displayJa: "隣人"),
        RelationshipEntry(code: "NCHILD",    displayEn: "natural child",        displayFr: "Enfant biologique",           displayJa: "実の子"),
        RelationshipEntry(code: "NIENEPH",   displayEn: "niece/nephew",         displayFr: "Nièce / Neveu",               displayJa: "姪・甥"),
        RelationshipEntry(code: "PRN",       displayEn: "parent",               displayFr: "Parent",                      displayJa: "親"),
        RelationshipEntry(code: "PRNINLAW",  displayEn: "parent in-law",        displayFr: "Beau-parent",                 displayJa: "義理の親"),
        RelationshipEntry(code: "ROOM",      displayEn: "roomate",              displayFr: "Colocataire",                 displayJa: "ルームメイト"),
        RelationshipEntry(code: "SIB",       displayEn: "sibling",              displayFr: "Frère / Sœur",                displayJa: "兄弟姉妹"),
        RelationshipEntry(code: "SIGOTHR",   displayEn: "significant other",    displayFr: "Personne significative",      displayJa: "重要な相手"),
        RelationshipEntry(code: "SON",       displayEn: "natural son",          displayFr: "Fils (biologique)",           displayJa: "実の息子"),
        RelationshipEntry(code: "SONADOPT",  displayEn: "adopted son",          displayFr: "Fils adopté",                 displayJa: "養子"),
        RelationshipEntry(code: "SONC",      displayEn: "son",                  displayFr: "Fils",                        displayJa: "息子"),
        RelationshipEntry(code: "SONFOST",   displayEn: "foster son",           displayFr: "Fils en famille d'accueil",   displayJa: "里息子"),
        RelationshipEntry(code: "SONINLAW",  displayEn: "son in-law",           displayFr: "Gendre",                      displayJa: "義理の息子"),
        RelationshipEntry(code: "SPS",       displayEn: "spouse",               displayFr: "Conjoint(e)",                 displayJa: "配偶者"),
        RelationshipEntry(code: "STPCHLD",   displayEn: "step child",           displayFr: "Beau-fils / Belle-fille",     displayJa: "継子"),
        RelationshipEntry(code: "STPDAU",    displayEn: "stepdaughter",         displayFr: "Belle-fille (recomposée)",    displayJa: "継娘"),
        RelationshipEntry(code: "STPSON",    displayEn: "stepson",              displayFr: "Beau-fils (recomposé)",       displayJa: "継息子"),
        RelationshipEntry(code: "UNCLE",     displayEn: "uncle",                displayFr: "Oncle",                       displayJa: "おじ"),
    ]

    private static let byCode: [String: RelationshipEntry] = {
        var map = [String: RelationshipEntry]()
        for entry in all {
            map[entry.code] = entry
        }
        return map
    }()

    public static func getDisplay(code: String?, lang: String) -> String {
        guard let code = code?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty else {
            return ""
        }
        return byCode[code.uppercased()]?.pick(lang: lang) ?? code
    }

    public static func isValidCode(_ code: String?) -> Bool {
        guard let code = code?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty else {
            return false
        }
        return byCode[code.uppercased()] != nil
    }

    public static func isRoleCode(_ raw: String?) -> Bool {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return false
        }
        // Matches ASCII uppercase mnemonics (2+ chars, A-Z, 0-9, underscore)
        let pattern = "^[A-Z0-9_]{2,}$"
        return raw.range(of: pattern, options: .regularExpression) != nil
    }
}
