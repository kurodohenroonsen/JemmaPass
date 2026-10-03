/**
 * JemmaPassChrome/core/translations.ts
 *
 * Libellés d'interface pour le canal QR Texte Universel lisible par l'humain.
 * Aligné avec JemmaTranslations.kt sur Android.
 */

export interface TextLabels {
  header: string;
  patient_title: string;
  patient_birth: string;
  patient_blood: string;
  patient_lang: string;
  patient_addr: string;
  patient_phone: string;
  patient_email: string;
  patient_id: string;
  allergies_title: string;
  medications_title: string;
  conditions_title: string;
  contacts_title: string;
  immunizations_title: string;
  procedures_title: string;
  devices_title: string;
  results_title: string;
  past_problems_title: string;
  pregnancy_title: string;
  functional_title: string;
  truncated: string;
  footer: string;
  gender_m: string;
  gender_f: string;
  gender_o: string;
  gender_u: string;
}

export const TEXT_TRANSLATIONS: Record<string, TextLabels> = {
  en: {
    header: "🏥 === JEMMA CLINICAL SUMMARY (EN) ===",
    patient_title: "PATIENT",
    patient_birth: "Birth",
    patient_blood: "Blood",
    patient_lang: "Language",
    patient_addr: "Address",
    patient_phone: "Phone",
    patient_email: "Email",
    patient_id: "ID",
    allergies_title: "ALLERGIES",
    medications_title: "MEDICATIONS",
    conditions_title: "CONDITIONS",
    contacts_title: "CONTACTS",
    immunizations_title: "IMMUNIZATIONS",
    procedures_title: "PROCEDURES",
    devices_title: "DEVICES",
    results_title: "RESULTS",
    past_problems_title: "PAST ILLNESSES",
    pregnancy_title: "PREGNANCY",
    functional_title: "FUNCTIONAL STATUS",
    truncated: "INCOMPLETE RECORD",
    footer: "✅ JEMMA v2.6 - on-device",
    gender_m: "M",
    gender_f: "F",
    gender_o: "O",
    gender_u: "U",
  },
  fr: {
    header: "🏥 === JEMMA CLINICAL SUMMARY (FR) ===",
    patient_title: "PATIENT",
    patient_birth: "Naissance",
    patient_blood: "Groupe",
    patient_lang: "Langue",
    patient_addr: "Adresse",
    patient_phone: "Tél",
    patient_email: "Email",
    patient_id: "ID",
    allergies_title: "ALLERGIES",
    medications_title: "MÉDICAMENTS",
    conditions_title: "CONDITIONS",
    contacts_title: "CONTACTS",
    immunizations_title: "VACCINATIONS",
    procedures_title: "ACTES",
    devices_title: "DISPOSITIFS",
    results_title: "RÉSULTATS",
    past_problems_title: "ANTÉCÉDENTS",
    pregnancy_title: "GROSSESSE",
    functional_title: "STATUT FONCTIONNEL",
    truncated: "DOSSIER INCOMPLET",
    footer: "✅ JEMMA v2.6 - on-device",
    gender_m: "H",
    gender_f: "F",
    gender_o: "A",
    gender_u: "I",
  },
  ja: {
    header: "🏥 === JEMMA CLINICAL SUMMARY (JA) ===",
    patient_title: "患者情報",
    patient_birth: "生年月日",
    patient_blood: "血液型",
    patient_lang: "言語",
    patient_addr: "住所",
    patient_phone: "電話",
    patient_email: "メール",
    patient_id: "身元確認番号",
    allergies_title: "アレルギー",
    medications_title: "処方薬",
    conditions_title: "現病歴",
    contacts_title: "緊急連絡先",
    immunizations_title: "予防接種",
    procedures_title: "処置・手術",
    devices_title: "医療機器",
    results_title: "検査結果",
    past_problems_title: "既往歴",
    pregnancy_title: "妊娠歴",
    functional_title: "生活機能",
    truncated: "一部省略",
    footer: "✅ JEMMA v2.6 - on-device",
    gender_m: "男",
    gender_f: "女",
    gender_o: "他",
    gender_u: "不",
  },
};

export function getTextLabels(langCode: string): TextLabels {
  const norm = langCode.toLowerCase().slice(0, 2);
  return TEXT_TRANSLATIONS[norm] || TEXT_TRANSLATIONS["en"];
}
