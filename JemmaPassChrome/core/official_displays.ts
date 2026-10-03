/**
 * JemmaPassChrome/core/official_displays.ts
 *
 * Libellés officiels de terminologie exigés par le validateur HL7 (miroir exact de IpsOfficialDisplays.kt sur Android).
 *
 * `coding.display` doit porter un libellé officiel du système de codage (tx.fhir.org ou le validateur
 * signale "Wrong Display Name" sinon). Les libellés conviviaux voyagent dans `text`.
 */

import { SYS_LOINC, SYS_SNOMED } from "./blood_group.ts";

export const OFFICIAL_DISPLAYS: Record<string, Record<string, string>> = {
  [SYS_LOINC]: {
    "882-1": "ABO and Rh group [Type] in Blood",
    "718-7": "Hemoglobin [Mass/volume] in Blood",
    "2089-1": "Cholesterol in LDL [Mass/volume] in Serum or Plasma",
    "2160-0": "Creatinine [Mass/volume] in Serum or Plasma",
    "2823-3": "Potassium [Moles/volume] in Serum or Plasma",
    "4548-4": "Hemoglobin A1c/Hemoglobin.total in Blood",
    "33914-3": "Glomerular filtration rate [Volume Rate/Area] in Serum or Plasma by Creatinine-based formula (MDRD)/1.73 sq M",
    // Pregnancy LOINC codes
    "82810-3": "Pregnancy status",
    "11778-8": "Delivery date Estimated",
    "11779-6": "Delivery date Estimated from last menstrual period",
    "11780-4": "Delivery date Estimated from ovulation date",
    "11640-0": "[#] Births total",
    "11636-8": "[#] Births.live",
    "11639-2": "[#] Births.term",
    "11637-6": "[#] Births.preterm",
    "11638-4": "[#] Births.still living",
    "11612-9": "[#] Abortions",
    "11614-5": "[#] Abortions.spontaneous",
    "11613-7": "[#] Abortions.induced",
    "33065-4": "[#] Ectopic pregnancy",
  },
  [SYS_SNOMED]: {
    "836378001": "Japanese encephalitis virus antigen-containing vaccine product",
    "871803007": "Hepatitis A and Hepatitis B virus antigens only vaccine product",
    "871876003": "Acellular Bordetella pertussis and Clostridium tetani and Corynebacterium diphtheriae antigens only vaccine product",
    "1181000221105": "Influenza virus antigen only vaccine product",
    "1801000221105": "Streptococcus pneumoniae capsular polysaccharide antigen conjugated only vaccine product",
  },
};

export function getOfficialDisplay(system: string | undefined, code: string | undefined): string | null {
  if (!code) return null;
  const sys = system || SYS_SNOMED;
  return OFFICIAL_DISPLAYS[sys]?.[code] || null;
}
