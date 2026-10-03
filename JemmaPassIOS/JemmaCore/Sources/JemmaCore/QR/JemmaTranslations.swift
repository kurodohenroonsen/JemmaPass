import Foundation

// MARK: - JemmaLang
public enum JemmaLang: String, CaseIterable, Sendable {
    case en = "en"
    case fr = "fr"
    case ja = "ja"
    case es = "es"
    case de = "de"
    case it = "it"
    case pt = "pt"
    case nl = "nl"
    case zh = "zh"
    case ko = "ko"
    case ar = "ar"
    case ru = "ru"
    case hi = "hi"
    case bn = "bn"
    case tr = "tr"
    case pl = "pl"
    case uk = "uk"
    case vi = "vi"
    case th = "th"
    case id = "id"
    case sv = "sv"
    case no = "no"
    case da = "da"
    case fi = "fi"
    case ro = "ro"

    public var isoCode: String { rawValue }

    public static func from(iso: String?) -> JemmaLang {
        guard let iso = iso?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return .en
        }
        let prefix = String(iso.prefix(2))
        return JemmaLang(rawValue: prefix) ?? .en
    }
}

// MARK: - JemmaTranslations
public enum JemmaTranslations: Sendable {
    private static let summaries: [JemmaLang: [String: String]] = [
        .en: [
            "header": "🏥 === JEMMA CLINICAL SUMMARY (EN) ===",
            "patient_title": "PATIENT",
            "patient_birth": "Birth",
            "patient_blood": "Blood",
            "patient_lang": "Language",
            "patient_addr": "Address",
            "patient_phone": "Phone",
            "patient_email": "Email",
            "patient_id": "ID",
            "allergies_title": "ALLERGIES",
            "medications_title": "MEDICATIONS",
            "conditions_title": "CONDITIONS",
            "immunizations_title": "IMMUNIZATIONS",
            "procedures_title": "PROCEDURES",
            "devices_title": "MEDICAL DEVICES",
            "results_title": "RESULTS",
            "past_problems_title": "PAST ILLNESSES",
            "pregnancy_title": "PREGNANCY HISTORY",
            "functional_title": "FUNCTIONAL STATUS",
            "contacts_title": "CONTACTS",
            "footer": "✅ JEMMA on-device · `_j 1.2`",
            "empty": "(none)",
            "truncated": "INCOMPLETE RECORD",
            "gender_m": "M",
            "gender_f": "F",
            "gender_o": "O",
            "gender_u": "U",
        ],
        .fr: [
            "header": "🏥 === JEMMA CLINICAL SUMMARY (FR) ===",
            "patient_title": "PATIENT",
            "patient_birth": "Naissance",
            "patient_blood": "Groupe",
            "patient_lang": "Langue",
            "patient_addr": "Adresse",
            "patient_phone": "Tél",
            "patient_email": "Email",
            "patient_id": "ID",
            "allergies_title": "ALLERGIES",
            "medications_title": "MÉDICAMENTS",
            "conditions_title": "CONDITIONS",
            "immunizations_title": "VACCINATIONS",
            "procedures_title": "INTERVENTIONS",
            "devices_title": "DISPOSITIFS MÉDICAUX",
            "results_title": "RÉSULTATS",
            "past_problems_title": "ANTÉCÉDENTS MÉDICAUX",
            "pregnancy_title": "GROSSESSES",
            "functional_title": "AUTONOMIE",
            "contacts_title": "CONTACTS",
            "footer": "✅ JEMMA on-device · `_j 1.2`",
            "empty": "(aucun)",
            "truncated": "FICHE INCOMPLÈTE",
            "gender_m": "H",
            "gender_f": "F",
            "gender_o": "A",
            "gender_u": "I",
        ],
        .ja: [
            "header": "🏥 === JEMMA 臨床サマリー (JA) ===",
            "patient_title": "患者",
            "patient_birth": "生年月日",
            "patient_blood": "血液型",
            "patient_lang": "言語",
            "patient_addr": "住所",
            "patient_phone": "電話",
            "patient_email": "メール",
            "patient_id": "ID",
            "allergies_title": "アレルギー",
            "medications_title": "服薬",
            "conditions_title": "病態",
            "immunizations_title": "予防接種",
            "procedures_title": "処置・手術歴",
            "devices_title": "医療機器",
            "results_title": "検査結果",
            "past_problems_title": "既往歴",
            "pregnancy_title": "妊娠歴",
            "functional_title": "生活機能",
            "contacts_title": "緊急連絡先",
            "footer": "✅ JEMMA on-device · `_j 1.2`",
            "empty": "(なし)",
            "truncated": "記録は不完全です",
            "gender_m": "男",
            "gender_f": "女",
            "gender_o": "他",
            "gender_u": "不",
        ],
        .es: [
            "header": "🏥 === JEMMA RESUMEN CLÍNICO (ES) ===",
            "patient_title": "PACIENTE",
            "patient_birth": "Nacimiento",
            "patient_blood": "Grupo",
            "patient_lang": "Idioma",
            "patient_addr": "Dirección",
            "patient_phone": "Tel",
            "patient_email": "Email",
            "patient_id": "ID",
            "allergies_title": "ALERGIAS",
            "medications_title": "MEDICAMENTOS",
            "conditions_title": "CONDICIONES",
            "immunizations_title": "VACUNAS",
            "procedures_title": "PROCEDIMIENTOS",
            "devices_title": "DISPOSITIVOS MÉDICOS",
            "results_title": "RESULTADOS",
            "past_problems_title": "ANTECEDENTES PATOLÓGICOS",
            "pregnancy_title": "HISTORIA OBSTÉTRICA",
            "functional_title": "ESTADO FUNCIONAL",
            "contacts_title": "CONTACTOS",
            "footer": "✅ JEMMA en el dispositivo · `_j 1.2`",
            "empty": "(ninguno)",
            "truncated": "REGISTRO INCOMPLETO",
            "gender_m": "M",
            "gender_f": "F",
            "gender_o": "O",
            "gender_u": "U",
        ],
        .de: [
            "header": "🏥 === JEMMA KLINISCHER AUSZUG (DE) ===",
            "patient_title": "PATIENT",
            "patient_birth": "Geburtsdatum",
            "patient_blood": "Blutgruppe",
            "patient_lang": "Sprache",
            "patient_addr": "Adresse",
            "patient_phone": "Tel",
            "patient_email": "E-Mail",
            "patient_id": "ID",
            "allergies_title": "ALLERGIEN",
            "medications_title": "MEDIKAMENTE",
            "conditions_title": "BEFUNDE",
            "immunizations_title": "IMPFUNGEN",
            "procedures_title": "EINGRIFFE",
            "devices_title": "MEDIZINPRODUKTE",
            "results_title": "BEFUNDE",
            "past_problems_title": "FRÜHERE ERKRANKUNGEN",
            "pregnancy_title": "SCHWANGERSCHAFTEN",
            "functional_title": "FUNKTIONSSTATUS",
            "contacts_title": "KONTAKTE",
            "footer": "✅ JEMMA auf dem Gerät · `_j 1.2`",
            "empty": "(keine)",
            "truncated": "UNVOLLSTÄNDIGER DATENSATZ",
            "gender_m": "M",
            "gender_f": "W",
            "gender_o": "D",
            "gender_u": "U",
        ]
    ]

    public static func getLabel(lang: JemmaLang, key: String) -> String {
        return summaries[lang]?[key] ?? summaries[.en]?[key] ?? key
    }
}
