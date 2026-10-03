/**
 * JemmaPassChrome/core/code_label_resolver.ts
 *
 * Implémente la règle PROTOCOL §9.1 :
 * Le code médical vient de la KB (ou du pass entrant).
 * Le libellé court affiché pour un code que l'app met en avant (relation, voie, etc.)
 * est un texte d'interface : nom de ressource code_label_<prefix>_<cleaned_code>.
 * Strictement aucun catalogue médical ni table de savoir dans ce fichier.
 */

export interface CodeLabelResolver {
  getLabel(system: string, code: string, lang: string): string | null;
}

/**
 * Mappe un code system URI et un code vers le nom de ressource selon PROTOCOL §9.1 :
 *   - "http://snomed.info/sct" → prefix "sct"
 *   - "http://loinc.org" → prefix "loinc"
 *   - "http://terminology.hl7.org/CodeSystem/v3-RoleCode" → prefix "v3"
 *   - Tout autre système → null
 * Tout caractère non alphanumérique du code devient '_'.
 * Résultat : "code_label_<prefix>_<cleaned_code>".
 */
export function codeLabelResourceName(system: string, code: string): string | null {
  const sys = system.trim();
  let prefix: string;
  if (sys === "http://snomed.info/sct" || sys === "sct") {
    prefix = "sct";
  } else if (sys === "http://loinc.org" || sys === "loinc") {
    prefix = "loinc";
  } else if (sys === "http://terminology.hl7.org/CodeSystem/v3-RoleCode" || sys === "v3") {
    prefix = "v3";
  } else {
    return null;
  }
  const cleanCode = code.trim().replace(/[^A-Za-z0-9]/g, "_");
  return `code_label_${prefix}_${cleanCode}`;
}

export class StaticLocaleCodeLabelResolver implements CodeLabelResolver {
  private locales: Record<string, Record<string, string>> = {};

  constructor(localesMap?: Record<string, Record<string, string>>) {
    if (localesMap) {
      this.locales = localesMap;
    }
  }

  setLocale(lang: string, messages: Record<string, string | { message: string }>): void {
    const normLang = lang.toLowerCase().slice(0, 2);
    if (!this.locales[normLang]) {
      this.locales[normLang] = {};
    }
    for (const [k, v] of Object.entries(messages)) {
      if (typeof v === "string") {
        this.locales[normLang][k] = v;
      } else if (v && typeof v.message === "string") {
        this.locales[normLang][k] = v.message;
      }
    }
  }

  getLabel(system: string, code: string, lang: string): string | null {
    const key = codeLabelResourceName(system, code);
    if (!key) return null;
    const normLang = lang.toLowerCase().slice(0, 2);
    const inLang = this.locales[normLang]?.[key];
    if (inLang) return inLang;
    // Fallback to English
    return this.locales["en"]?.[key] || null;
  }
}
