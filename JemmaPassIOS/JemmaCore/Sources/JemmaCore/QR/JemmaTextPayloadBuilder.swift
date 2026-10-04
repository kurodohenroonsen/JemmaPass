import Foundation

// MARK: - JemmaTextPayloadBuilder
public enum JemmaTextPayloadBuilder: Sendable {
    public static let maxBytes = 1800
    public static let truncationMark = "✂️ …"
    private static let eol = "\r\n"
    private static let bullet = "  ▪️ "

    private static let rankAllergies = 1
    private static let rankMedications = 2
    private static let rankConditions = 3
    private static let rankPregnancy = 4
    private static let rankFunctional = 5
    private static let rankContacts = 6
    private static let rankDevices = 7
    private static let rankPatientExtra = 8
    private static let rankPastProblems = 9
    private static let rankProcedures = 10
    private static let rankResults = 11
    private static let rankImmunizations = 12

    private final class Part {
        let icon: String
        let title: String?
        let rank: Int
        var lines: [String]
        var dropped: Int = 0

        init(icon: String, title: String?, rank: Int, lines: [String]) {
            self.icon = icon
            self.title = title
            self.rank = rank
            self.lines = lines
        }
    }

    public static func utf8Size(_ s: String) -> Int {
        return s.utf8.count
    }

    public static func build(
        profile: JemmaProfileJ,
        lang: JemmaLang = .en,
        maxBytes: Int = maxBytes,
        labels: any CodeLabelResolver = CodeLabelResolvers.none
    ) -> String {
        func label(_ key: String) -> String {
            return JemmaTranslations.getLabel(lang: lang, key: key)
        }

        let p = profile.p
        var patientCore: [String] = []
        let patientExtra = Part(icon: "👤", title: nil, rank: rankPatientExtra, lines: [])

        if let p = p {
            let nameParts = [p.gn, p.fn].compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
            let name = nameParts.joined(separator: " ")
            let gender = formatGender(p.gs, lang: lang)
            let nameLine = " 🔹 " + name + (gender.isEmpty ? "" : " (\(gender))")
            patientCore.append(nameLine)

            if let bd = p.bd?.trimmingCharacters(in: .whitespacesAndNewlines), !bd.isEmpty {
                patientCore.append(" 📅 " + label("patient_birth") + ": " + bd)
            }
            if let bt = p.bt?.trimmingCharacters(in: .whitespacesAndNewlines), !bt.isEmpty {
                patientCore.append(" 🩸 " + label("patient_blood") + ": " + bt)
            }
            if let plang = p.lang?.trimmingCharacters(in: .whitespacesAndNewlines), !plang.isEmpty {
                patientCore.append(" 🗣 " + label("patient_lang") + ": " + plang)
            }

            if let adr = p.adr?.trimmingCharacters(in: .whitespacesAndNewlines), !adr.isEmpty {
                patientExtra.lines.append(" 📍 " + label("patient_addr") + ": " + adr)
            }
            if let tel = p.tel?.trimmingCharacters(in: .whitespacesAndNewlines), !tel.isEmpty {
                patientExtra.lines.append(" 📞 " + label("patient_phone") + ": " + safePhone(tel))
            }
            if let eml = p.eml?.trimmingCharacters(in: .whitespacesAndNewlines), !eml.isEmpty {
                patientExtra.lines.append(" 📧 " + label("patient_email") + ": " + eml)
            }
            if let idn = p.idn?.trimmingCharacters(in: .whitespacesAndNewlines), !idn.isEmpty {
                patientExtra.lines.append(" 🆔 " + label("patient_id") + ": " + idn)
            }
        }

        func part<T>(icon: String, titleKey: String, rank: Int, items: [T], formatter: (T) -> String?) -> Part {
            let lines = items.compactMap { item -> String? in
                guard let formatted = formatter(item), !formatted.isEmpty else { return nil }
                return bullet + formatted
            }
            return Part(icon: icon, title: label(titleKey), rank: rank, lines: lines)
        }

        // Emergency contacts
        let contactLines = (p?.ct ?? []).compactMap { formatContact($0, lang: lang) }
        let contactsPart = Part(icon: "☎️", title: label("contacts_title"), rank: rankContacts, lines: contactLines.map { bullet + $0 })

        // Sections list
        let sections: [Part] = [
            part(icon: "⚠️", titleKey: "allergies_title", rank: rankAllergies, items: profile.al) { a in
                formatAllergy(a)
            },
            part(icon: "💊", titleKey: "medications_title", rank: rankMedications, items: profile.md) { m in
                formatMedication(m)
            },
            part(icon: "🩺", titleKey: "conditions_title", rank: rankConditions, items: profile.cn) { c in
                c.d_display?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? c.c?.trimmingCharacters(in: .whitespacesAndNewlines)
            },
            contactsPart,
            part(icon: "💉", titleKey: "immunizations_title", rank: rankImmunizations, items: profile.im) { im in
                formatGenericEntry(im)
            },
            part(icon: "🏥", titleKey: "procedures_title", rank: rankProcedures, items: profile.pr) { pr in
                formatGenericEntry(pr)
            },
            part(icon: "📟", titleKey: "devices_title", rank: rankDevices, items: profile.dv) { dv in
                formatGenericEntry(dv)
            },
            part(icon: "🧪", titleKey: "results_title", rank: rankResults, items: profile.rs) { rs in
                formatResult(rs)
            },
            part(icon: "📜", titleKey: "past_problems_title", rank: rankPastProblems, items: profile.ph) { ph in
                formatGenericEntry(ph)
            },
            part(icon: "🤰", titleKey: "pregnancy_title", rank: rankPregnancy, items: profile.pg) { pg in
                formatGenericEntry(pg)
            },
            part(icon: "♿", titleKey: "functional_title", rank: rankFunctional, items: profile.fs) { fs in
                formatGenericEntry(fs)
            },
        ]

        let droppable = sections + [patientExtra]

        func render() -> String {
            var out = ""
            out += label("header") + eol + eol
            if p != nil {
                out += "👤 [ " + label("patient_title") + " ]" + eol
                for line in patientCore {
                    out += line + eol
                }
                for line in patientExtra.lines {
                    out += line + eol
                }
                out += eol
            }

            for s in sections {
                if s.lines.isEmpty { continue }
                out += s.icon + " [ " + (s.title ?? "") + " ]" + eol
                for line in s.lines {
                    out += line + eol
                }
                if s.dropped > 0 {
                    out += "  " + truncationMark + " +" + String(s.dropped) + eol
                }
                out += eol
            }

            let cut = droppable.filter { $0.dropped > 0 }
            if !cut.isEmpty {
                let icons = cut.sorted { $0.rank < $1.rank }.map { $0.icon }.joined(separator: " ")
                out += truncationMark + " [ " + label("truncated") + " ] " + icons + eol + eol
            }
            out += label("footer") + eol
            return out
        }

        var full = render()
        while utf8Size(full) > maxBytes {
            guard let victim = droppable.filter({ !$0.lines.isEmpty }).max(by: { $0.rank < $1.rank }) else {
                break
            }
            victim.lines.removeLast()
            victim.dropped += 1
            full = render()
        }

        if utf8Size(full) > maxBytes {
            let tail = eol + truncationMark + " [ " + label("truncated") + " ]" + eol
            let tailSize = utf8Size(tail)
            if tailSize <= maxBytes {
                full = cutUtf8(full, maxBytes: maxBytes - tailSize) + tail
            } else {
                full = cutUtf8(full, maxBytes: maxBytes)
            }
        }

        return full
    }

    public static func formatContact(_ c: JContact, lang: JemmaLang) -> String? {
        let name = c.n?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let rawRel = c.r?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let relation: String
        if rawRel.isEmpty {
            relation = ""
        } else if IpsRelationshipCatalog.isValidCode(rawRel) {
            relation = IpsRelationshipCatalog.getDisplay(code: rawRel, lang: lang.isoCode).trimmingCharacters(in: .whitespacesAndNewlines)
        } else if IpsRelationshipCatalog.isRoleCode(rawRel) {
            relation = ""
        } else {
            relation = rawRel
        }

        let phone = c.p?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let email = c.e?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let reach = !phone.isEmpty ? phone : email

        if name.isEmpty && reach.isEmpty {
            return nil
        }

        var parts: [String] = []
        if !name.isEmpty { parts.append(name) }
        if !relation.isEmpty { parts.append("(\(relation))") }
        if !reach.isEmpty { parts.append(reach) }
        return parts.joined(separator: " ")
    }

    private static func formatAllergy(_ a: JAllergy) -> String {
        let name = a.d_display?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? a.c ?? ""
        let crit = a.s ?? "U"
        let critLabel: String
        switch crit {
        case "H": critLabel = "HIGH"
        case "L": critLabel = "LOW"
        default: critLabel = "UNABLE-TO-ASSESS"
        }
        return "\(name) (\(critLabel))"
    }

    private static func formatMedication(_ m: JMedication) -> String {
        let name = m.d_display?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? m.c ?? ""
        let doseVal = m.v?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let doseUnit = m.u?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let dose = doseVal + doseUnit
        let timing = m.t?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        var parts: [String] = []
        if !name.isEmpty { parts.append(name) }
        if !dose.isEmpty { parts.append(dose) }
        if !timing.isEmpty { parts.append(timing) }
        return parts.joined(separator: " ")
    }

    private static func formatGenericEntry(_ e: JEntryGeneric) -> String {
        let label = e.d_display?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? e.d ?? e.c ?? ""
        var out = label
        if let dt = e.dt?.trimmingCharacters(in: .whitespacesAndNewlines), !dt.isEmpty {
            out += " — \(dt)"
        }
        if let dn = e.dn {
            out += " · #\(dn)"
        }
        if let st = e.st?.trimmingCharacters(in: .whitespacesAndNewlines), !st.isEmpty, st != "completed", st != "active", st != "resolved" {
            out += " (\(st))"
        }
        return out
    }

    private static func formatResult(_ rs: JEntryGeneric) -> String {
        let label = rs.d_display?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? rs.d ?? rs.c ?? ""
        let value = IpsBloodGroup.labelFromSnomed(rs.vc)
            ?? [rs.v, rs.u].compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }.joined(separator: " ")
        var out = label
        if !value.isEmpty {
            out += ": \(value)"
        }
        if let ip = rs.ip?.trimmingCharacters(in: .whitespacesAndNewlines), !ip.isEmpty, ip != "N" {
            out += " (\(ip))"
        }
        if let dt = rs.dt?.trimmingCharacters(in: .whitespacesAndNewlines), !dt.isEmpty {
            out += " — \(dt)"
        }
        return out
    }

    private static func formatGender(_ gs: String?, lang: JemmaLang) -> String {
        guard let gs = gs?.uppercased().trimmingCharacters(in: .whitespacesAndNewlines) else { return "" }
        switch gs {
        case "M": return JemmaTranslations.getLabel(lang: lang, key: "gender_m")
        case "F": return JemmaTranslations.getLabel(lang: lang, key: "gender_f")
        case "O": return JemmaTranslations.getLabel(lang: lang, key: "gender_o")
        case "U": return JemmaTranslations.getLabel(lang: lang, key: "gender_u")
        default: return ""
        }
    }

    public static func safePhone(_ phone: String) -> String {
        var result = ""
        var count = 0
        for char in phone {
            result.append(char)
            if char.isNumber {
                count += 1
                if count % 2 == 0 {
                    result.append(".")
                }
            }
        }
        while result.hasSuffix(".") {
            result.removeLast()
        }
        return result
    }

    public static func cutUtf8(_ text: String, maxBytes: Int) -> String {
        guard maxBytes > 0 else { return "" }
        var bytes = 0
        var endIndex = text.startIndex
        for index in text.indices {
            let charBytes = String(text[index]).utf8.count
            if bytes + charBytes > maxBytes {
                break
            }
            bytes += charBytes
            endIndex = text.index(after: index)
        }
        return String(text[..<endIndex])
    }
}
