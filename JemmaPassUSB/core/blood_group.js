/**
 * JemmaPass USB — core/blood_group.js
 *
 * Normalisation et conversion du groupe sanguin (p.bt <-> LOINC 882-1 Observation).
 * Strictement aligné sur le contrat commun qa/vectors/bloodgroup/ (bg-001..010).
 * Zéro dépendance externe, compatible Node CLI et Navigateur.
 */
"use strict";

const LOINC_SYSTEM = "http://loinc.org";
const LOINC_CODE_ABO_RH = "882-1";
const LOINC_DISPLAY_ABO_RH = "ABO and Rh group [Type] in Blood";
const SNOMED_SYSTEM = "http://snomed.info/sct";

const SNOMED_BLOOD_GROUPS = {
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
 * Normalise une chaîne brute de groupe sanguin en code canonique ("A+", "O-", etc.)
 * ou renvoie null si vide / non reconnu.
 */
function normalizeBloodGroup(raw) {
  if (!raw || typeof raw !== "string") return null;
  const cleaned = raw.toUpperCase().replace(/\s+/g, "").replace(/RH/g, "").replace(/\(D\)/g, "");
  const match = cleaned.match(/^(AB|A|B|O)([+\-−]|POS|NEG)/);
  if (!match) return null;
  const abo = match[1];
  const rhRaw = match[2];
  const rh = (rhRaw === "+" || rhRaw === "POS") ? "+" : "-";
  const canonical = abo + rh;
  return SNOMED_BLOOD_GROUPS[canonical] ? canonical : null;
}

/**
 * Construit la ressource Observation FHIR LOINC 882-1 pour un groupe sanguin donné.
 * Conforme au contrat des vecteurs qa/vectors/bloodgroup/.
 *
 * @param {string} bloodType - Groupe sanguin (ex: "O+", "A-")
 * @param {string} [patientRef="@patient"] - Référence vers le Patient
 * @returns {Object|null} Objet Observation FHIR R4 ou null si non reconnu/absent
 */
function buildBloodGroupObservation(bloodType, patientRef = "@patient") {
  const canonical = normalizeBloodGroup(bloodType);
  if (!canonical) return null;
  const snomed = SNOMED_BLOOD_GROUPS[canonical];

  return {
    resourceType: "Observation",
    meta: {
      profile: [
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips"
      ]
    },
    status: "final",
    category: [
      {
        coding: [
          {
            system: "http://terminology.hl7.org/CodeSystem/observation-category",
            code: "laboratory",
            display: "Laboratory"
          }
        ],
        text: "Laboratory"
      }
    ],
    code: {
      coding: [
        {
          system: LOINC_SYSTEM,
          code: LOINC_CODE_ABO_RH,
          display: LOINC_DISPLAY_ABO_RH
        }
      ],
      text: "ABO and Rh blood group"
    },
    subject: {
      reference: patientRef
    },
    performer: [
      {
        reference: patientRef,
        display: "Patient-reported"
      }
    ],
    _effectiveDateTime: {
      extension: [
        {
          url: "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
          valueCode: "unknown"
        }
      ]
    },
    valueCodeableConcept: {
      coding: [
        {
          system: SNOMED_SYSTEM,
          code: snomed.code,
          display: snomed.display
        }
      ],
      text: snomed.display
    }
  };
}

/**
 * Extrait le groupe sanguin depuis une ressource Observation FHIR LOINC 882-1.
 * @param {Object} observation - Ressource Observation FHIR
 * @returns {string|null} Groupe canonique ("A+", "O-", etc.) ou null
 */
function extractBloodGroupFromObservation(observation) {
  if (!observation || observation.resourceType !== "Observation") return null;
  const codings = observation.code?.coding || [];
  const isAboRh = codings.some(c => c.system === LOINC_SYSTEM && c.code === LOINC_CODE_ABO_RH);
  if (!isAboRh) return null;

  const valueCodings = observation.valueCodeableConcept?.coding || [];
  for (const vc of valueCodings) {
    if (vc.system === SNOMED_SYSTEM && vc.code) {
      for (const [group, info] of Object.entries(SNOMED_BLOOD_GROUPS)) {
        if (info.code === vc.code) return group;
      }
    }
  }

  // Repli sur le text ou display
  const text = observation.valueCodeableConcept?.text || observation.valueCodeableConcept?.coding?.[0]?.display;
  if (text) {
    for (const [group, info] of Object.entries(SNOMED_BLOOD_GROUPS)) {
      if (info.display.toLowerCase() === text.trim().toLowerCase()) return group;
    }
    return normalizeBloodGroup(text);
  }
  return null;
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    LOINC_SYSTEM,
    LOINC_CODE_ABO_RH,
    LOINC_DISPLAY_ABO_RH,
    SNOMED_SYSTEM,
    SNOMED_BLOOD_GROUPS,
    normalizeBloodGroup,
    buildBloodGroupObservation,
    extractBloodGroupFromObservation
  };
}
if (typeof globalThis !== "undefined") {
  globalThis.JemmaBloodGroup = {
    normalizeBloodGroup,
    buildBloodGroupObservation,
    extractBloodGroupFromObservation
  };
}
