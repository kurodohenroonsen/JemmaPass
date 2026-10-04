/**
 * JemmaPassChrome/core/blood_group.ts
 *
 * Synchronisation et normalisation du groupe sanguin entre `p.bt` et l'Observation LOINC 882-1.
 * Conforme à IpsBloodGroup.kt sur Android.
 */

import type { JEntryGeneric } from "./types.ts";

export const LOINC_ABO_RH = "882-1";
export const DISPLAY_ABO_RH = "ABO and Rh blood group";
export const DERIVED_ID_PREFIX = "rs-blood-group-";
export const SYS_SNOMED = "http://snomed.info/sct";
export const SYS_LOINC = "http://loinc.org";

export const SNOMED_BLOOD_GROUPS: Record<string, { code: string; display: string }> = {
  "O+": { code: "278147001", display: "Blood group O Rh(D) positive" },
  "O-": { code: "278148006", display: "Blood group O Rh(D) negative" },
  "A+": { code: "278149003", display: "Blood group A Rh(D) positive" },
  "A-": { code: "278152006", display: "Blood group A Rh(D) negative" },
  "B+": { code: "278150003", display: "Blood group B Rh(D) positive" },
  "B-": { code: "278153001", display: "Blood group B Rh(D) negative" },
  "AB+": { code: "278151004", display: "Blood group AB Rh(D) positive" },
  "AB-": { code: "278154007", display: "Blood group AB Rh(D) negative" },
};

/**
 * Normalise "o +", "O Rh+", "A positive", "AB-" → canonical "O+", "A+", etc.
 */
export function normalizeBloodGroup(raw?: string | null): string | null {
  if (!raw) return null;
  const t = raw.toUpperCase().replace(/\s+/g, "").replace(/RH/g, "").replace(/\(D\)/g, "");
  const match = t.match(/^(AB|A|B|O)/);
  if (!match) return null;
  const abo = match[1];
  const rest = t.slice(abo.length);
  let rh = "";
  if (rest.startsWith("+") || rest.startsWith("POS")) {
    rh = "+";
  } else if (rest.startsWith("-") || rest.startsWith("NEG") || rest.startsWith("−")) {
    rh = "-";
  } else {
    return null;
  }
  return abo + rh;
}

export function snomedCodeFromBloodGroup(raw?: string | null): string | null {
  const norm = normalizeBloodGroup(raw);
  return norm ? SNOMED_BLOOD_GROUPS[norm]?.code || null : null;
}

export function snomedDisplayFromBloodGroup(raw?: string | null): string | null {
  const norm = normalizeBloodGroup(raw);
  return norm ? SNOMED_BLOOD_GROUPS[norm]?.display || null : null;
}

export function labelFromSnomedCode(code?: string | null): string | null {
  if (!code) return null;
  for (const [k, v] of Object.entries(SNOMED_BLOOD_GROUPS)) {
    if (v.code === code) return k;
  }
  return null;
}

export function isBloodGroupEntry(entry: JEntryGeneric): boolean {
  if (entry.c?.trim() === LOINC_ABO_RH) return true;
  if (entry.d_display?.toLowerCase().includes("blood group")) return true;
  if (entry.vc && labelFromSnomedCode(entry.vc)) return true;
  return false;
}

/**
 * Réconcilie les résultats pour s'assurer qu'il y a exactement une Observation 882-1
 * correspondant au groupe sanguin déclaré dans `p.bt`.
 */
export function reconcileBloodGroup(
  results: JEntryGeneric[],
  sid: string,
  bloodType?: string | null
): JEntryGeneric[] {
  const expectedSnomed = snomedCodeFromBloodGroup(bloodType);
  if (!expectedSnomed) {
    // Si p.bt est absent ou non reconnu, on filtre seulement les entrées auto-dérivées
    return results.filter(r => !r.c || r.c !== LOINC_ABO_RH || !r.vc);
  }
  const expectedDisplay = snomedDisplayFromBloodGroup(bloodType)!;
  const canonical = normalizeBloodGroup(bloodType)!;

  // Recherche d'une entrée existante concordante
  const matching = results.find(
    r => isBloodGroupEntry(r) && (r.vc === expectedSnomed || normalizeBloodGroup(r.v) === canonical)
  );

  const nonBlood = results.filter(r => !isBloodGroupEntry(r));

  if (matching) {
    // On conserve celle-ci
    const cleaned: JEntryGeneric = {
      ...matching,
      c: LOINC_ABO_RH,
      d_display: DISPLAY_ABO_RH,
      vc: expectedSnomed,
      v: expectedDisplay,
      vcs: SYS_SNOMED,
      cs: SYS_LOINC,
    };
    return [cleaned, ...nonBlood];
  }

  // Création de l'entrée dérivée
  const derived: JEntryGeneric = {
    c: LOINC_ABO_RH,
    d_display: DISPLAY_ABO_RH,
    v: expectedDisplay,
    vc: expectedSnomed,
    vcs: SYS_SNOMED,
    cs: SYS_LOINC,
  };

  return [derived, ...nonBlood];
}
