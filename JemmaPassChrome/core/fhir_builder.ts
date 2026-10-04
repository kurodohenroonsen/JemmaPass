/**
 * JemmaPassChrome/core/fhir_builder.ts
 *
 * Constructeur de Bundle FHIR R4 IPS déterministe conforme à HL7 IPS 1.1.0 et JemmaFhirBundleBuilder.kt.
 * Strictement aucun accès DOM ni chrome.*
 */

import type {
  JemmaProfileJ,
  JPatient,
  JContact,
  JAllergy,
  JMedication,
  JCondition,
  JEntryGeneric,
  FhirBundle,
  FhirEntry,
  FhirComposition,
  FhirCompositionSection,
  FhirPatient,
  FhirCodeableConcept,
  FhirCoding,
  FhirPatientContact,
} from "./types.ts";
import { reconcileBloodGroup, LOINC_ABO_RH, SYS_LOINC, SYS_SNOMED } from "./blood_group.ts";
import { getOfficialDisplay } from "./official_displays.ts";
import type { CodeLabelResolver } from "./code_label_resolver.ts";
import { codeLabelResourceName } from "./code_label_resolver.ts";

export const SYS_ATC = "http://www.whocc.no/atc";
export const SYS_RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";
export const SYS_AI_CLINICAL = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical";
export const SYS_AI_VERIF = "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification";
export const SYS_COND_CATEGORY = "http://terminology.hl7.org/CodeSystem/condition-category";
export const SYS_COND_CLINICAL = "http://terminology.hl7.org/CodeSystem/condition-clinical";
export const SYS_COND_VERIF = "http://terminology.hl7.org/CodeSystem/condition-ver-status";
export const SYS_UCUM = "http://unitsofmeasure.org";
export const SYS_OBS_CATEGORY = "http://terminology.hl7.org/CodeSystem/observation-category";
export const SYS_OBS_INTERPRETATION = "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";
export const SYS_V3_ROLECODE = "http://terminology.hl7.org/CodeSystem/v3-RoleCode";

export const PROFILE_BUNDLE_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Bundle-uv-ips";
export const PROFILE_COMPOSITION_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Composition-uv-ips";
export const PROFILE_PATIENT_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips";
export const PROFILE_ALLERGY_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/AllergyIntolerance-uv-ips";
export const PROFILE_MED_STATEMENT_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/MedicationStatement-uv-ips";
export const PROFILE_MEDICATION_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Medication-uv-ips";
export const PROFILE_CONDITION_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips";
export const PROFILE_IMMUNIZATION_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips";
export const PROFILE_PROCEDURE_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips";
export const PROFILE_DEVICE_USE_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/DeviceUseStatement-uv-ips";
export const PROFILE_DEVICE_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Device-uv-ips";
export const PROFILE_OBS_RESULTS_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-uv-ips";
export const PROFILE_OBS_RESULTS_LAB_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips";
export const PROFILE_OBS_RESULTS_RAD_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-radiology-uv-ips";
export const PROFILE_OBS_PREG_STATUS_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-status-uv-ips";
export const PROFILE_OBS_PREG_EDD_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-edd-uv-ips";
export const PROFILE_OBS_PREG_OUTCOME_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-outcome-uv-ips";

const NON_UCUM_DOSE_UNITS = new Set([
  "tab", "tabs", "tablet", "tablets", "cp", "comp", "cap", "caps", "capsule", "capsules",
  "puff", "puffs", "drop", "drops", "gtt", "sachet", "patch", "unit", "units", "dose", "doses"
]);

/**
 * Calcul MD5 purement JavaScript pour la génération déterministe UUID v3 (RFC 4122).
 */
export function md5(string: string): number[] {
  function rotateLeft(lValue: number, iShiftBits: number): number {
    return (lValue << iShiftBits) | (lValue >>> (32 - iShiftBits));
  }
  function addUnsigned(lX: number, lY: number): number {
    const lX8 = lX & 0x80000000;
    const lY8 = lY & 0x80000000;
    const lX4 = lX & 0x40000000;
    const lY4 = lY & 0x40000000;
    const lResult = (lX & 0x3fffffff) + (lY & 0x3fffffff);
    if (lX4 & lY4) return lResult ^ 0x80000000 ^ lX8 ^ lY8;
    if (lX4 | lY4) {
      if (lResult & 0x40000000) return lResult ^ 0xc0000000 ^ lX8 ^ lY8;
      else return lResult ^ 0x40000000 ^ lX8 ^ lY8;
    } else {
      return lResult ^ lX8 ^ lY8;
    }
  }
  function F(x: number, y: number, z: number) { return (x & y) | (~x & z); }
  function G(x: number, y: number, z: number) { return (x & z) | (y & ~z); }
  function H(x: number, y: number, z: number) { return x ^ y ^ z; }
  function I(x: number, y: number, z: number) { return y ^ (x | ~z); }

  function FF(a: number, b: number, c: number, d: number, x: number, s: number, ac: number) {
    a = addUnsigned(a, addUnsigned(addUnsigned(F(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
  }
  function GG(a: number, b: number, c: number, d: number, x: number, s: number, ac: number) {
    a = addUnsigned(a, addUnsigned(addUnsigned(G(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
  }
  function HH(a: number, b: number, c: number, d: number, x: number, s: number, ac: number) {
    a = addUnsigned(a, addUnsigned(addUnsigned(H(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
  }
  function II(a: number, b: number, c: number, d: number, x: number, s: number, ac: number) {
    a = addUnsigned(a, addUnsigned(addUnsigned(I(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
  }

  // UTF-8 encode
  const utf8Bytes = new TextEncoder().encode(string);
  const lMessageLength = utf8Bytes.length;
  const lNumberOfWordsTemp1 = lMessageLength + 8;
  const lNumberOfWordsTemp2 = (lNumberOfWordsTemp1 - (lNumberOfWordsTemp1 % 64)) / 64;
  const lNumberOfWords = (lNumberOfWordsTemp2 + 1) * 16;
  const x = new Array(lNumberOfWords).fill(0);

  for (let i = 0; i < lMessageLength; i++) {
    const lWordCount = (i - (i % 4)) / 4;
    const lBytePosition = (i % 4) * 8;
    x[lWordCount] = x[lWordCount] | (utf8Bytes[i] << lBytePosition);
  }
  const lWordCount = (lMessageLength - (lMessageLength % 4)) / 4;
  const lBytePosition = (lMessageLength % 4) * 8;
  x[lWordCount] = x[lWordCount] | (0x80 << lBytePosition);
  x[lNumberOfWords - 2] = lMessageLength * 8;
  x[lNumberOfWords - 1] = Math.floor(lMessageLength / 0x20000000);

  let a = 0x67452301, b = 0xefcdab89, c = 0x98badcfe, d = 0x10325476;
  for (let k = 0; k < x.length; k += 16) {
    const AA = a, BB = b, CC = c, DD = d;
    a = FF(a, b, c, d, x[k + 0], 7, 0xd76aa478);
    d = FF(d, a, b, c, x[k + 1], 12, 0xe8c7b756);
    c = FF(c, d, a, b, x[k + 2], 17, 0x242070db);
    b = FF(b, c, d, a, x[k + 3], 22, 0xc1bdceee);
    a = FF(a, b, c, d, x[k + 4], 7, 0xf57c0faf);
    d = FF(d, a, b, c, x[k + 5], 12, 0x4787c62a);
    c = FF(c, d, a, b, x[k + 6], 17, 0xa8304613);
    b = FF(b, c, d, a, x[k + 7], 22, 0xfd469501);
    a = FF(a, b, c, d, x[k + 8], 7, 0x698098d8);
    d = FF(d, a, b, c, x[k + 9], 12, 0x8b44f7af);
    c = FF(c, d, a, b, x[k + 10], 17, 0xffff5bb1);
    b = FF(b, c, d, a, x[k + 11], 22, 0x895cd7be);
    a = FF(a, b, c, d, x[k + 12], 7, 0x6b901122);
    d = FF(d, a, b, c, x[k + 13], 12, 0xfd987193);
    c = FF(c, d, a, b, x[k + 14], 17, 0xa679438e);
    b = FF(b, c, d, a, x[k + 15], 22, 0x49b40821);

    a = GG(a, b, c, d, x[k + 1], 5, 0xf61e2562);
    d = GG(d, a, b, c, x[k + 6], 9, 0xc040b340);
    c = GG(c, d, a, b, x[k + 11], 14, 0x265e5a51);
    b = GG(b, c, d, a, x[k + 0], 20, 0xe9b6c7aa);
    a = GG(a, b, c, d, x[k + 5], 5, 0xd62f105d);
    d = GG(d, a, b, c, x[k + 10], 9, 0x2441453);
    c = GG(c, d, a, b, x[k + 15], 14, 0xd8a1e681);
    b = GG(b, c, d, a, x[k + 4], 20, 0xe7d3fbc8);
    a = GG(a, b, c, d, x[k + 9], 5, 0x21e1cde6);
    d = GG(d, a, b, c, x[k + 14], 9, 0xc33707d6);
    c = GG(c, d, a, b, x[k + 3], 14, 0xf4d50d87);
    b = GG(b, c, d, a, x[k + 8], 20, 0x455a14ed);
    a = GG(a, b, c, d, x[k + 13], 5, 0xa9e3e905);
    d = GG(d, a, b, c, x[k + 2], 9, 0xfcefa3f8);
    c = GG(c, d, a, b, x[k + 7], 14, 0x676f02d9);
    b = GG(b, c, d, a, x[k + 12], 20, 0x8d2a4c8a);

    a = HH(a, b, c, d, x[k + 5], 4, 0xfffa3942);
    d = HH(d, a, b, c, x[k + 8], 11, 0x8771f681);
    c = HH(c, d, a, b, x[k + 11], 16, 0x6d9d6122);
    b = HH(b, c, d, a, x[k + 14], 23, 0xfde5380c);
    a = HH(a, b, c, d, x[k + 1], 4, 0xa4beea44);
    d = HH(d, a, b, c, x[k + 4], 11, 0x4bdecfa9);
    c = HH(c, d, a, b, x[k + 7], 16, 0xf6bb4b60);
    b = HH(b, c, d, a, x[k + 10], 23, 0xbebfbc70);
    a = HH(a, b, c, d, x[k + 13], 4, 0x289b7ec6);
    d = HH(d, a, b, c, x[k + 0], 11, 0xeaa127fa);
    c = HH(c, d, a, b, x[k + 3], 16, 0xd4ef3085);
    b = HH(b, c, d, a, x[k + 6], 23, 0x4881d05);
    a = HH(a, b, c, d, x[k + 9], 4, 0xd9d4d039);
    d = HH(d, a, b, c, x[k + 12], 11, 0xe6db99e5);
    c = HH(c, d, a, b, x[k + 15], 16, 0x1fa27cf8);
    b = HH(b, c, d, a, x[k + 2], 23, 0xc4ac5665);

    a = II(a, b, c, d, x[k + 0], 6, 0xf4292244);
    d = II(d, a, b, c, x[k + 7], 10, 0x432aff97);
    c = II(c, d, a, b, x[k + 14], 15, 0xab9423a7);
    b = II(b, c, d, a, x[k + 5], 21, 0xfc93a039);
    a = II(a, b, c, d, x[k + 12], 6, 0x655b59c3);
    d = II(d, a, b, c, x[k + 3], 10, 0x8f0ccc92);
    c = II(c, d, a, b, x[k + 10], 15, 0xffeff47d);
    b = II(b, c, d, a, x[k + 1], 21, 0x85845dd1);
    a = II(a, b, c, d, x[k + 8], 6, 0x6fa87e4f);
    d = II(d, a, b, c, x[k + 15], 10, 0xfe2ce6e0);
    c = II(c, d, a, b, x[k + 6], 15, 0xa3014314);
    b = II(b, c, d, a, x[k + 13], 21, 0x4e0811a1);
    a = II(a, b, c, d, x[k + 4], 6, 0xf7537e82);
    d = II(d, a, b, c, x[k + 11], 10, 0xbd3af235);
    c = II(c, d, a, b, x[k + 2], 15, 0x2ad7d2bb);
    b = II(b, c, d, a, x[k + 9], 21, 0xeb86d391);

    a = addUnsigned(a, AA);
    b = addUnsigned(b, BB);
    c = addUnsigned(c, CC);
    d = addUnsigned(d, DD);
  }

  function wordToBytes(word: number): number[] {
    return [word & 0xff, (word >>> 8) & 0xff, (word >>> 16) & 0xff, (word >>> 24) & 0xff];
  }
  return [...wordToBytes(a), ...wordToBytes(b), ...wordToBytes(c), ...wordToBytes(d)];
}

/**
 * Produit un URN déterministe RFC 4122 v3 ("urn:uuid:<uuid>") à partir d'un seed.
 * Identique au UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)) Kotlin.
 */
export function stableUrn(seed: string): string {
  const bytes = md5(seed);
  bytes[6] = (bytes[6] & 0x0f) | 0x30; // version 3
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant RFC 4122
  const hex = bytes.map(b => b.toString(16).padStart(2, "0")).join("");
  const uuid = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
  return `urn:uuid:${uuid}`;
}

export function parseDose(rawValue?: string | null, rawUnit?: string | null): { value: string; unit: string; isUcum: boolean } | null {
  if (!rawValue || !rawValue.trim()) return null;
  const match = rawValue.trim().match(/^([0-9]+(?:[.,][0-9]+)?)\s*([A-Za-zµμ%][A-Za-zµμ%/.]*)?$/);
  if (!match) return null;
  const numberStr = match[1];
  // Ambiguous thousands: 1,000 (could mean 1 or 1000)
  if (/^[1-9][0-9]{0,2},[0-9]{3}$/.test(numberStr)) return null;
  const normalizedNumber = numberStr.replace(",", ".");
  const unit = (rawUnit && rawUnit.trim()) || match[2] || "";
  const isUcum = unit.length > 0 && !NON_UCUM_DOSE_UNITS.has(unit.toLowerCase());
  return { value: normalizedNumber, unit, isUcum };
}

export function routeConcept(rawRoute?: string | null): FhirCodeableConcept | null {
  if (!rawRoute || !rawRoute.trim()) return null;
  const key = rawRoute.trim().toUpperCase();
  let snomed: string | null = null;
  let label = rawRoute.trim();
  if (key === "O" || key === "ORAL") {
    snomed = "26643006";
    label = "Oral";
  } else if (key === "T" || key === "TOPICAL") {
    snomed = "6064005";
    label = "Topical";
  } else if (key === "S" || key === "SUBCUTANEOUS") {
    snomed = "34206005";
    label = "Subcutaneous";
  } else if (key === "H" || key.startsWith("INH")) {
    snomed = "447694001";
    label = "Inhalation";
  } else if (key === "I" || key === "INJECTION") {
    snomed = null;
    label = "Injection";
  }

  const codings: FhirCoding[] = [];
  if (snomed) {
    codings.push({
      system: SYS_SNOMED,
      code: snomed,
    });
  }
  return {
    coding: codings.length > 0 ? codings : undefined,
    text: label,
  };
}

export function medicationStatus(raw?: string | null): string {
  const code = (raw || "").trim().toLowerCase().replace(/[_\s]/g, "-");
  switch (code) {
    case "":
    case "active":
      return "active";
    case "completed":
      return "completed";
    case "entered-in-error":
      return "entered-in-error";
    case "intended":
      return "intended";
    case "stopped":
      return "stopped";
    case "on-hold":
      return "on-hold";
    case "not-taken":
      return "not-taken";
    default:
      return "unknown";
  }
}

/**
 * Construit un Bundle FHIR R4 IPS (Document) conforme à la spécification HL7 IPS 1.1.0.
 */
export function buildFhirBundle(
  profile: JemmaProfileJ,
  options?: {
    timestamp?: string;
    labels?: CodeLabelResolver;
    uiLang?: string;
  }
): FhirBundle {
  const sid = profile.sid && profile.sid.trim() ? profile.sid.trim() : "no-sid";
  const nowIso = options?.timestamp || new Date().toISOString();
  const uiLang = options?.uiLang || profile.p?.lang || "en";
  const labels = options?.labels;

  const patientUrn = stableUrn(`${sid}|Patient`);
  const compositionUrn = stableUrn(`${sid}|Composition`);

  // Réconciliation du groupe sanguin
  const reconciledResults = reconcileBloodGroup(profile.rs || [], sid, profile.p?.bt);

  // Génération des URNs déterministes
  const allergyUrns = (profile.al || []).map((a, i) => stableUrn(`${sid}|AllergyIntolerance|${i}|${a.c || ""}`));
  const medStatementUrns = (profile.md || []).map((m, i) => stableUrn(`${sid}|MedicationStatement|${i}|${m.c || ""}`));
  const medRefUrns = (profile.md || []).map((m, i) => stableUrn(`${sid}|Medication|${i}|${m.c || ""}`));
  const problemUrns = (profile.cn || []).map((c, i) => stableUrn(`${sid}|Condition|problem|${c.c || i}`));
  const pastProblemUrns = (profile.ph || []).map((ph, i) => stableUrn(`${sid}|Condition|past|${ph.c || i}`));
  const immunizationUrns = (profile.im || []).map((im, i) => stableUrn(`${sid}|Immunization|${im.c || i}`));
  const procedureUrns = (profile.pr || []).map((pr, i) => stableUrn(`${sid}|Procedure|${pr.c || i}`));
  const deviceUrns = (profile.dv || []).map((dv, i) => stableUrn(`${sid}|Device|${dv.c || i}`));
  const deviceStatementUrns = (profile.dv || []).map((dv, i) => stableUrn(`${sid}|DeviceUseStatement|${dv.c || i}`));
  const resultUrns = reconciledResults.map((rs, i) => stableUrn(`${sid}|Observation|${rs.c || i}`));
  const pregnancyUrns = (profile.pg || []).map((pg, i) => stableUrn(`${sid}|Observation|pregnancy|${pg.c || i}`));
  const functionalUrns = (profile.fs || []).map((fs, i) => stableUrn(`${sid}|Condition|functional|${fs.c || i}`));

  // 1. Ressource Patient
  const p = profile.p;
  const fhirPatient: FhirPatient = {
    resourceType: "Patient",
    id: "patient-01",
    meta: {
      profile: [PROFILE_PATIENT_UV_IPS],
    },
  };

  // Nom du patient
  const nameGiven = p?.gn && p.gn.trim() ? [p.gn.trim()] : undefined;
  const nameFamily = p?.fn && p.fn.trim() ? p.fn.trim() : undefined;
  if (nameGiven || nameFamily) {
    fhirPatient.name = [
      {
        given: nameGiven,
        family: nameFamily,
      },
    ];
  }

  // Genre
  if (p?.gs) {
    const gs = p.gs.toUpperCase();
    fhirPatient.gender = gs === "M" ? "male" : gs === "F" ? "female" : gs === "O" ? "other" : "unknown";
  }

  // Date de naissance
  if (p?.bd && p.bd.trim()) {
    fhirPatient.birthDate = p.bd.trim();
  }

  // Adresses
  const addresses: any[] = [];
  if (p?.adr && p.adr.trim()) {
    addresses.push({
      text: p.adr.trim(),
      line: [p.adr.trim()],
    });
  }
  if (p?.adrs) {
    for (const a of p.adrs) {
      const addr: any = {};
      if (a.u) addr.use = a.u;
      if (a.l) addr.line = [a.l];
      if (a.c) addr.city = a.c;
      if (a.z) addr.postalCode = a.z;
      if (a.st) addr.state = a.st;
      if (a.o) addr.country = a.o;
      if (Object.keys(addr).length > 0) addresses.push(addr);
    }
  }
  if (addresses.length > 0) {
    fhirPatient.address = addresses;
  }

  // Télécoms
  const telecoms: any[] = [];
  if (p?.tel && p.tel.trim()) {
    telecoms.push({ system: "phone", value: p.tel.trim() });
  }
  if (p?.eml && p.eml.trim()) {
    telecoms.push({ system: "email", value: p.eml.trim() });
  }
  if (p?.tels) {
    for (const t of p.tels) {
      if (t.v) {
        telecoms.push({
          system: t.s || "phone",
          value: t.v,
          use: t.u,
        });
      }
    }
  }
  if (telecoms.length > 0) {
    fhirPatient.telecom = telecoms;
  }

  // Langue
  if (p?.lang && p.lang.trim()) {
    fhirPatient.communication = [
      {
        language: {
          coding: [
            {
              system: "urn:ietf:bcp:47",
              code: p.lang.trim(),
            },
          ],
        },
      },
    ];
  }

  // Contacts d'urgence (Patient.contact)
  if (p?.ct && p.ct.length > 0) {
    const contacts: FhirPatientContact[] = [];
    for (const c of p.ct) {
      const hasName = Boolean(c.n && c.n.trim());
      const hasPhone = Boolean(c.p && c.p.trim());
      const hasEmail = Boolean(c.e && c.e.trim());
      const hasAddr = Boolean(c.adr && c.adr.trim());
      const hasRel = Boolean(c.r && c.r.trim());

      // Conformité ct-006 & UC-CT-026: contact ignoré si entièrement vide
      // ou si relationship-only sans aucun point de contact
      if (!hasName && !hasPhone && !hasEmail && !hasAddr) {
        continue;
      }

      const fhirC: FhirPatientContact = {};

      if (hasRel) {
        const rawRel = c.r!.trim();
        // Vérification si code RoleCode officiel
        const labelKey = codeLabelResourceName(SYS_V3_ROLECODE, rawRel);
        // Display officiel anglais pour RoleCode
        const officialDisplay = getOfficialRoleCodeDisplay(rawRel);
        const textInLang = labels?.getLabel(SYS_V3_ROLECODE, rawRel, uiLang) || getRoleCodeTranslation(rawRel, uiLang);

        if (officialDisplay) {
          fhirC.relationship = [
            {
              coding: [
                {
                  system: SYS_V3_ROLECODE,
                  code: rawRel,
                  display: officialDisplay,
                },
              ],
              text: textInLang || officialDisplay,
            },
          ];
        } else {
          fhirC.relationship = [
            {
              text: rawRel,
            },
          ];
        }
      }

      if (hasName) {
        fhirC.name = {
          text: c.n!.trim(),
        };
      }

      const contactTelecoms: any[] = [];
      if (hasPhone) {
        contactTelecoms.push({
          system: "phone",
          value: c.p!.trim(),
        });
      }
      if (hasEmail) {
        contactTelecoms.push({
          system: "email",
          value: c.e!.trim(),
        });
      }
      if (contactTelecoms.length > 0) {
        fhirC.telecom = contactTelecoms;
      }

      if (hasAddr) {
        fhirC.address = {
          text: c.adr!.trim(),
        };
      }

      contacts.push(fhirC);
    }

    if (contacts.length > 0) {
      fhirPatient.contact = contacts;
    }
  }

  // 2. Entries du Bundle
  const entries: FhirEntry[] = [];

  // AllergyIntolerance
  (profile.al || []).forEach((a, i) => {
    const codeStr = a.c || "";
    const displayStr = a.d_display || codeStr;
    const crit = a.s === "H" ? "high" : a.s === "L" ? "low" : "unable-to-assess";
    const clinStatus = a.st === "I" ? "inactive" : a.st === "R" ? "resolved" : "active";

    const allergyResource: any = {
      resourceType: "AllergyIntolerance",
      clinicalStatus: {
        coding: [{ system: SYS_AI_CLINICAL, code: clinStatus }],
      },
      verificationStatus: {
        coding: [{ system: SYS_AI_VERIF, code: "confirmed" }],
      },
      type: "allergy",
      criticality: crit,
      patient: { reference: patientUrn },
    };

    if (codeStr || displayStr) {
      allergyResource.code = {
        coding: codeStr ? [{ system: SYS_SNOMED, code: codeStr, display: displayStr }] : undefined,
        text: displayStr || undefined,
      };
    }

    entries.push({
      fullUrl: allergyUrns[i],
      resource: allergyResource,
    });
  });

  // Medications
  (profile.md || []).forEach((m, i) => {
    const rawCode = (m.c || "").trim();
    const displayStr = m.d_display || rawCode;
    const isAtc = /^[A-Za-z]\d{2}[A-Za-z]{2}\d{2}$/.test(rawCode);
    const primarySystem = m.cs || (isAtc ? SYS_ATC : SYS_SNOMED);

    const medResource: any = {
      resourceType: "Medication",
      code: {
        coding: rawCode ? [{ system: primarySystem, code: rawCode, display: displayStr }] : undefined,
        text: displayStr || undefined,
      },
    };
    entries.push({
      fullUrl: medRefUrns[i],
      resource: medResource,
    });

    const statementResource: any = {
      resourceType: "MedicationStatement",
      status: medicationStatus(m.ms),
      medicationReference: { reference: medRefUrns[i] },
      subject: { reference: patientUrn },
      dateAsserted: nowIso,
    };

    if (m.eff && m.eff.trim()) {
      const eff = m.eff.trim();
      if (eff.includes("/")) {
        const [s, e] = eff.split("/");
        statementResource.effectivePeriod = {
          start: s?.trim() || undefined,
          end: e?.trim() || undefined,
        };
      } else {
        statementResource.effectiveDateTime = eff;
      }
    }

    const parsedDose = parseDose(m.v, m.u);
    const route = routeConcept(m.r);
    const dosageText = [m.t, [m.v, m.u].filter(Boolean).join("")].filter(Boolean).join(" • ") || displayStr;

    statementResource.dosage = [
      {
        text: dosageText,
        route: route || undefined,
        doseAndRate: parsedDose
          ? [
              {
                doseQuantity: {
                  value: parseFloat(parsedDose.value),
                  unit: parsedDose.unit || undefined,
                  system: parsedDose.isUcum ? SYS_UCUM : undefined,
                  code: parsedDose.isUcum ? parsedDose.unit : undefined,
                },
              },
            ]
          : undefined,
      },
    ];

    entries.push({
      fullUrl: medStatementUrns[i],
      resource: statementResource,
    });
  });

  // Problems (Condition active)
  (profile.cn || []).forEach((c, i) => {
    const rawCode = c.c || "";
    const displayStr = c.d_display || rawCode;
    const condResource: any = {
      resourceType: "Condition",
      id: `condition-prob-${i}`,
      meta: { profile: [PROFILE_CONDITION_UV_IPS] },
      clinicalStatus: {
        coding: [{ system: SYS_COND_CLINICAL, code: c.st || "active" }],
      },
      category: [
        {
          coding: [{ system: SYS_COND_CATEGORY, code: "problem-list-item", display: "Problem List Item" }],
        },
      ],
      code: {
        coding: rawCode ? [{ system: c.cs || SYS_SNOMED, code: rawCode, display: displayStr }] : undefined,
        text: displayStr || undefined,
      },
      subject: { reference: patientUrn },
    };
    if (c.dt) condResource.onsetDateTime = c.dt;
    if (c.d) condResource.note = [{ text: c.d }];
    entries.push({
      fullUrl: problemUrns[i],
      resource: condResource,
    });
  });

  // Past problems (Condition inactive/resolved)
  (profile.ph || []).forEach((ph, i) => {
    const rawCode = ph.c || "";
    const displayStr = ph.d_display || rawCode;
    const pastResource: any = {
      resourceType: "Condition",
      id: `condition-past-${i}`,
      meta: { profile: [PROFILE_CONDITION_UV_IPS] },
      clinicalStatus: {
        coding: [{ system: SYS_COND_CLINICAL, code: ph.st || "resolved" }],
      },
      code: {
        coding: rawCode ? [{ system: ph.cs || SYS_SNOMED, code: rawCode, display: displayStr }] : undefined,
        text: displayStr || undefined,
      },
      subject: { reference: patientUrn },
    };
    if (ph.dt) pastResource.onsetDateTime = ph.dt;
    if (ph.ab) pastResource.abatementDateTime = ph.ab;
    if (ph.d) pastResource.note = [{ text: ph.d }];
    entries.push({
      fullUrl: pastProblemUrns[i],
      resource: pastResource,
    });
  });

  // Immunizations
  (profile.im || []).forEach((im, i) => {
    const rawCode = im.c || "";
    const displayStr = im.d_display || rawCode;
    const officialImmDisplay = getOfficialDisplay(im.cs || SYS_SNOMED, rawCode);
    const immResource: any = {
      resourceType: "Immunization",
      id: `immunization-${i}`,
      meta: { profile: [PROFILE_IMMUNIZATION_UV_IPS] },
      status: im.st || "completed",
      vaccineCode: {
        coding: rawCode ? [{ system: im.cs || SYS_SNOMED, code: rawCode, display: officialImmDisplay || displayStr }] : undefined,
        text: displayStr || undefined,
      },
      patient: { reference: patientUrn },
      occurrenceDateTime: im.dt || nowIso,
    };
    if (im.dn != null) {
      immResource.protocolApplied = [{ doseNumberPositiveInt: im.dn }];
    }
    entries.push({
      fullUrl: immunizationUrns[i],
      resource: immResource,
    });
  });

  // Procedures
  (profile.pr || []).forEach((pr, i) => {
    const rawCode = pr.c || "";
    const displayStr = pr.d_display || rawCode;
    const procResource: any = {
      resourceType: "Procedure",
      id: `procedure-${i}`,
      meta: { profile: [PROFILE_PROCEDURE_UV_IPS] },
      status: pr.st || "completed",
      code: {
        coding: rawCode ? [{ system: pr.cs || SYS_SNOMED, code: rawCode, display: displayStr }] : undefined,
        text: displayStr || undefined,
      },
      subject: { reference: patientUrn },
      performedDateTime: pr.dt || undefined,
    };
    entries.push({
      fullUrl: procedureUrns[i],
      resource: procResource,
    });
  });

  // Devices
  (profile.dv || []).forEach((dv, i) => {
    const rawCode = dv.c || "";
    const displayStr = dv.d_display || rawCode;
    const devResource: any = {
      resourceType: "Device",
      id: `device-${i}`,
      meta: { profile: [PROFILE_DEVICE_UV_IPS] },
      type: {
        coding: rawCode ? [{ system: dv.cs || SYS_SNOMED, code: rawCode, display: displayStr }] : undefined,
        text: displayStr || undefined,
      },
      patient: { reference: patientUrn },
    };
    entries.push({
      fullUrl: deviceUrns[i],
      resource: devResource,
    });

    const devUseResource: any = {
      resourceType: "DeviceUseStatement",
      id: `device-use-${i}`,
      meta: { profile: [PROFILE_DEVICE_USE_UV_IPS] },
      status: dv.st || "active",
      subject: { reference: patientUrn },
      device: { reference: deviceUrns[i] },
    };
    if (dv.dt) {
      devUseResource.timingDateTime = dv.dt;
    } else {
      devUseResource._timingDateTime = {
        extension: [{ url: "http://hl7.org/fhir/StructureDefinition/data-absent-reason", valueCode: "unknown" }]
      };
    }
    if (dv.bd) {
      devUseResource.bodySite = { text: dv.bd };
    }
    if (dv.d) {
      devUseResource.note = [{ text: dv.d }];
    }
    entries.push({
      fullUrl: deviceStatementUrns[i],
      resource: devUseResource,
    });
  });

  // Results / Lab / Blood group
  reconciledResults.forEach((rs, i) => {
    const rawCode = rs.c || "";
    const displayStr = rs.d_display || rawCode;
    const isBlood = rawCode === LOINC_ABO_RH;
    const cat = rs.ct === "imaging" ? "imaging" : "laboratory";
    const dayPrecise = rs.dt ? /^\d{4}-\d{2}-\d{2}/.test(rs.dt) : false;
    let profileUrl = PROFILE_OBS_RESULTS_UV_IPS;
    if (cat === "imaging") {
      profileUrl = dayPrecise ? PROFILE_OBS_RESULTS_RAD_UV_IPS : PROFILE_OBS_RESULTS_UV_IPS;
    } else if (cat === "laboratory" || isBlood) {
      profileUrl = PROFILE_OBS_RESULTS_LAB_UV_IPS;
    }

    const officialCodeDisplay = getOfficialDisplay(rs.cs || SYS_LOINC, rawCode);

    const obsResource: any = {
      resourceType: "Observation",
      id: isBlood ? "rs-blood-group-0" : `observation-result-${i}`,
      meta: { profile: [profileUrl] },
      status: rs.st || "final",
      category: [
        {
          coding: [{ system: SYS_OBS_CATEGORY, code: cat, display: cat === "imaging" ? "Imaging" : "Laboratory" }],
          text: cat === "imaging" ? "Imaging" : "Laboratory",
        },
      ],
      code: {
        coding: rawCode ? [{ system: rs.cs || SYS_LOINC, code: rawCode, display: officialCodeDisplay || displayStr }] : undefined,
        text: displayStr || undefined,
      },
      subject: { reference: patientUrn },
    };

    if (rs.dt) {
      obsResource.effectiveDateTime = rs.dt;
    } else {
      obsResource._effectiveDateTime = {
        extension: [{ url: "http://hl7.org/fhir/StructureDefinition/data-absent-reason", valueCode: "unknown" }]
      };
    }

    if (isBlood && rs.vc) {
      obsResource.valueCodeableConcept = {
        coding: [
          {
            system: rs.vcs || SYS_SNOMED,
            code: rs.vc,
            display: rs.v || undefined,
          },
        ],
        text: rs.v || undefined,
      };
    } else if (rs.v && !isNaN(Number(rs.v)) && rs.u) {
      obsResource.valueQuantity = {
        value: Number(rs.v),
        unit: rs.u,
        system: SYS_UCUM,
        code: rs.u,
      };
    } else if (rs.v) {
      obsResource.valueString = rs.v;
    }

    if (rs.ip) {
      const ipDisplay = rs.ip === "N" ? "Normal" : rs.ip === "H" ? "High" : rs.ip === "L" ? "Low" : undefined;
      obsResource.interpretation = [
        {
          coding: [{ system: SYS_OBS_INTERPRETATION, code: rs.ip, display: ipDisplay }],
          text: ipDisplay || rs.ip,
        },
      ];
    }

    if (rs.rl != null || rs.rh != null) {
      const rrObj: any = {};
      if (rs.rl != null && !isNaN(Number(rs.rl))) {
        rrObj.low = { value: Number(rs.rl), unit: rs.u, system: SYS_UCUM, code: rs.u };
      }
      if (rs.rh != null && !isNaN(Number(rs.rh))) {
        rrObj.high = { value: Number(rs.rh), unit: rs.u, system: SYS_UCUM, code: rs.u };
      }
      obsResource.referenceRange = [rrObj];
    } else if (rs.rr) {
      obsResource.referenceRange = [{ text: rs.rr }];
    }

    if (rs.d) {
      obsResource.performer = [{ display: rs.d }];
    } else {
      obsResource.performer = [{ reference: patientUrn, display: "Patient-reported" }];
    }

    if (rs.nt) {
      obsResource.note = [{ text: rs.nt }];
    }

    entries.push({
      fullUrl: resultUrns[i],
      resource: obsResource,
    });
  });

  // Functional status (Condition in section 47420-5)
  (profile.fs || []).forEach((fs, i) => {
    const rawCode = fs.c || "";
    const displayStr = fs.d_display || rawCode;
    const fsResource: any = {
      resourceType: "Condition",
      id: `functional-${i}`,
      meta: { profile: [PROFILE_CONDITION_UV_IPS] },
      clinicalStatus: {
        coding: [{ system: SYS_COND_CLINICAL, code: fs.st || "active" }],
      },
      code: {
        coding: rawCode ? [{ system: fs.cs || SYS_SNOMED, code: rawCode, display: displayStr }] : undefined,
        text: displayStr || undefined,
      },
      subject: { reference: patientUrn },
    };
    if (fs.dt) fsResource.onsetDateTime = fs.dt;
    entries.push({
      fullUrl: functionalUrns[i],
      resource: fsResource,
    });
  });

  // Pregnancy (Observation)
  (profile.pg || []).forEach((pg, i) => {
    const rawCode = pg.c || "";
    const displayStr = pg.d_display || rawCode;
    const officialPregDisplay = getOfficialDisplay(SYS_LOINC, rawCode);

    let profileUrl = PROFILE_OBS_PREG_OUTCOME_UV_IPS;
    if (rawCode === "82810-3") {
      profileUrl = PROFILE_OBS_PREG_STATUS_UV_IPS;
    } else if (["11778-8", "11779-6", "11780-4"].includes(rawCode)) {
      profileUrl = PROFILE_OBS_PREG_EDD_UV_IPS;
    }

    const pgResource: any = {
      resourceType: "Observation",
      id: `pregnancy-${i}`,
      meta: { profile: [profileUrl] },
      status: "final",
      code: {
        coding: rawCode ? [{ system: SYS_LOINC, code: rawCode, display: officialPregDisplay || displayStr }] : undefined,
        text: displayStr || undefined,
      },
      subject: { reference: patientUrn },
    };

    if (pg.dt) {
      pgResource.effectiveDateTime = pg.dt;
    } else {
      pgResource._effectiveDateTime = {
        extension: [{ url: "http://hl7.org/fhir/StructureDefinition/data-absent-reason", valueCode: "unknown" }]
      };
    }

    if (pg.vc) {
      const statusAnswers: Record<string, string> = {
        "LA15173-0": "Pregnant",
        "LA26683-5": "Not pregnant",
        "LA4489-6": "Unknown",
      };
      pgResource.valueCodeableConcept = {
        coding: [{
          system: SYS_LOINC,
          code: pg.vc,
          display: statusAnswers[pg.vc] || pg.v || undefined,
        }],
        text: statusAnswers[pg.vc] || pg.v || undefined,
      };
    } else if (pg.v != null && !isNaN(Number(pg.v))) {
      pgResource.valueInteger = parseInt(pg.v, 10);
    } else if (pg.v) {
      pgResource.valueString = pg.v;
    }

    if (pg.d) {
      pgResource.note = [{ text: pg.d }];
    }

    entries.push({
      fullUrl: pregnancyUrns[i],
      resource: pgResource,
    });
  });

  // 3. Sections de la Composition
  function makeSection(title: string, loincCode: string, entryRefs: string[]): FhirCompositionSection | null {
    if (entryRefs.length === 0) return null;
    return {
      title,
      code: {
        coding: [{ system: SYS_LOINC, code: loincCode }],
      },
      entry: entryRefs.map(r => ({ reference: r })),
    };
  }

  const sections: FhirCompositionSection[] = [
    makeSection("Allergies", "48765-2", allergyUrns),
    makeSection("Medications", "10160-0", medStatementUrns),
    makeSection("Problems", "11450-4", problemUrns),
    makeSection("History of Past Illness", "11348-0", pastProblemUrns),
    makeSection("History of Pregnancy", "10162-6", pregnancyUrns),
    makeSection("Functional Status", "47420-5", functionalUrns),
    makeSection("Immunizations", "11369-6", immunizationUrns),
    makeSection("History of Procedures", "47519-4", procedureUrns),
    makeSection("Medical Devices", "46264-8", deviceStatementUrns),
    makeSection("Results", "30954-2", resultUrns),
  ].filter((s): s is FhirCompositionSection => s !== null);

  const fhirComposition: FhirComposition = {
    resourceType: "Composition",
    id: "composition-01",
    status: "final",
    type: {
      coding: [
        {
          system: SYS_LOINC,
          code: "60591-5",
          display: "Patient summary Document",
        },
      ],
    },
    subject: { reference: patientUrn },
    date: nowIso,
    author: [{ display: "JEMMA Pass on-device" }],
    title: "International Patient Summary",
    confidentiality: "N",
    section: sections,
  };

  // 4. Assemblage final du Bundle
  const bundleEntries: FhirEntry[] = [
    {
      fullUrl: compositionUrn,
      resource: fhirComposition,
    },
    {
      fullUrl: patientUrn,
      resource: fhirPatient,
    },
    ...entries,
  ];

  const bundle: FhirBundle = {
    resourceType: "Bundle",
    identifier: {
      system: "urn:ietf:rfc:3986",
      value: stableUrn(`${sid}|Bundle`),
    },
    type: "document",
    timestamp: nowIso,
    entry: bundleEntries,
  };

  return bundle;
}

// ── RoleCode helpers ───────────────────────────────────────────────

const OFFICIAL_ROLE_CODES: Record<string, string> = {
  AUNT: "aunt",
  CHILD: "child",
  CHLDADOPT: "adopted child",
  CHLDFOST: "foster child",
  CHLDINLAW: "child in-law",
  COUSN: "cousin",
  DAU: "natural daughter",
  DAUADOPT: "adopted daughter",
  DAUC: "daughter",
  DAUFOST: "foster daughter",
  DAUINLAW: "daughter in-law",
  DOMPART: "domestic partner",
  FAMMEMB: "family member",
  FRND: "unrelated friend",
  FTH: "father",
  FTHINLAW: "father-in-law",
  GGRPRN: "great grandparent",
  GRNDCHILD: "grandchild",
  GRPRN: "grandparent",
  MTH: "mother",
  MTHINLAW: "mother-in-law",
  NBOR: "neighbor",
  NCHILD: "natural child",
  NIENEPH: "niece/nephew",
  PRN: "parent",
  PRNINLAW: "parent in-law",
  ROOM: "roomate",
  SIB: "sibling",
  SIGOTHR: "significant other",
  SON: "natural son",
  SONADOPT: "adopted son",
  SONC: "son",
  SONFOST: "foster son",
  SONINLAW: "son in-law",
  SPS: "spouse",
  STPCHLD: "step child",
  STPDAU: "stepdaughter",
  STPSON: "stepson",
  UNCLE: "uncle",
};

const ROLE_CODE_JA: Record<string, string> = {
  AUNT: "おば",
  CHILD: "子",
  CHLDADOPT: "養子",
  CHLDFOST: "里子",
  CHLDINLAW: "義理の子",
  COUSN: "いとこ",
  DAU: "実の娘",
  DAUADOPT: "養女",
  DAUC: "娘",
  DAUFOST: "里娘",
  DAUINLAW: "義理の娘",
  DOMPART: "パートナー",
  FAMMEMB: "家族",
  FRND: "友人",
  FTH: "父",
  FTHINLAW: "義父",
  GGRPRN: "曾祖父母",
  GRNDCHILD: "孫",
  GRPRN: "祖父母",
  MTH: "母",
  MTHINLAW: "義母",
  NBOR: "隣人",
  NCHILD: "実の子",
  NIENEPH: "姪・甥",
  PRN: "親",
  PRNINLAW: "義理の親",
  ROOM: "ルームメイト",
  SIB: "兄弟姉妹",
  SIGOTHR: "重要な相手",
  SON: "実の息子",
  SONADOPT: "養子",
  SONC: "息子",
  SONFOST: "里息子",
  SONINLAW: "義理の息子",
  SPS: "配偶者",
  STPCHLD: "継子",
  STPDAU: "継娘",
  STPSON: "継息子",
  UNCLE: "おじ",
};

const ROLE_CODE_FR: Record<string, string> = {
  AUNT: "Tante",
  CHILD: "Enfant",
  CHLDADOPT: "Enfant adopté(e)",
  CHLDFOST: "Enfant en famille d'accueil",
  CHLDINLAW: "Belle-fille / Beau-fils",
  COUSN: "Cousin(e)",
  DAU: "Fille (biologique)",
  DAUADOPT: "Fille adoptée",
  DAUC: "Fille",
  DAUFOST: "Fille en famille d'accueil",
  DAUINLAW: "Belle-fille",
  DOMPART: "Partenaire",
  FAMMEMB: "Membre de la famille",
  FRND: "Ami(e)",
  FTH: "Père",
  FTHINLAW: "Beau-père",
  GGRPRN: "Arrière-grand-parent",
  GRNDCHILD: "Petit-enfant",
  GRPRN: "Grand-parent",
  MTH: "Mère",
  MTHINLAW: "Belle-mère",
  NBOR: "Voisin(e)",
  NCHILD: "Enfant biologique",
  NIENEPH: "Nièce / Neveu",
  PRN: "Parent",
  PRNINLAW: "Beau-parent",
  ROOM: "Colocataire",
  SIB: "Frère / Sœur",
  SIGOTHR: "Personne significative",
  SON: "Fils (biologique)",
  SONADOPT: "Fils adopté",
  SONC: "Fils",
  SONFOST: "Fils en famille d'accueil",
  SONINLAW: "Gendre",
  SPS: "Conjoint(e)",
  STPCHLD: "Beau-fils / Belle-fille",
  STPDAU: "Belle-fille (recomposée)",
  STPSON: "Beau-fils (recomposé)",
  UNCLE: "Oncle",
};

export function getOfficialRoleCodeDisplay(code?: string | null): string | null {
  if (!code) return null;
  return OFFICIAL_ROLE_CODES[code.trim().toUpperCase()] || null;
}

export function getRoleCodeTranslation(code: string, lang: string): string | null {
  const norm = code.trim().toUpperCase();
  const l = lang.toLowerCase().slice(0, 2);
  if (l === "ja") return ROLE_CODE_JA[norm] || null;
  if (l === "fr") return ROLE_CODE_FR[norm] || null;
  return OFFICIAL_ROLE_CODES[norm] || null;
}
