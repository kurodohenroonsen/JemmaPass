/**
 * JemmaPassChrome/core/types.ts
 *
 * Types et interfaces purs pour le schéma `_j 1.2` et le document HL7 FHIR R4 IPS.
 * Strictement aucun accès au DOM ni aux APIs chrome.*
 */

export interface JIdentifier {
  s?: string; // system short-code or URI
  v?: string; // value
}

export interface JAddress {
  u?: string; // use: home | work | temp | old | billing
  l?: string; // line
  c?: string; // city
  z?: string; // postal code
  st?: string; // state
  o?: string; // country
}

export interface JTelecom {
  s?: string; // system: phone | email | sms | fax | url | other
  v?: string; // value
  u?: string; // use: home | work | temp | old | mobile
}

export interface JContact {
  n?: string; // name
  r?: string; // relationship (e.g. "DAUC", "FRND" or free text)
  p?: string; // phone
  e?: string; // email
  adr?: string; // address
}

export interface JReaction {
  m?: string; // manifestation code
  md?: string; // manifestation display
  ms?: string; // manifestation system
  sv?: string; // severity: mild | moderate | severe
}

export interface JAllergy {
  c?: string; // code (RxNorm / SNOMED CT / ATC)
  s?: string; // criticality: 'H' | 'L' | 'U'
  st?: string; // status: 'A' | 'I' | 'R'
  d?: string; // details
  m?: string; // mechanism
  d_display?: string; // display label
  cs?: string; // codeSystem URI
  tp?: string; // type: "allergy" | "intolerance"
  cat?: string; // category: "food" | "medication" | "environment" | "biologic"
  on?: string; // onset date ISO YYYY-MM-DD
  rxns?: JReaction[];
}

export interface JMedication {
  c?: string; // code (RxNorm, ATC, etc.)
  t?: string; // timing / frequency
  r?: string; // route: 'O' | 'I' | 'T' | 'S' | 'H'
  v?: string; // dose value
  u?: string; // dose unit
  rs?: string; // reason source / note
  rc?: string; // reason code
  d_display?: string; // display label
  cs?: string; // codeSystem URI
  ms?: string; // status: active | completed | entered-in-error | intended | stopped | on-hold | unknown | not-taken
  eff?: string; // effective date or period
  effar?: string; // effective absence reason
}

export interface JCondition {
  c?: string; // code
  s?: string; // severity
  st?: string; // clinical status
  d?: string; // details
  rs?: string;
  rc?: string;
  d_display?: string; // display label
  dt?: string; // onset date
  cs?: string; // codeSystem URI
}

export interface JEntryGeneric {
  c?: string; // code
  d?: string; // details / note
  d_display?: string; // display label
  dt?: string; // date
  cs?: string; // codeSystem URI
  st?: string; // status
  dn?: number; // doseNumber
  v?: string; // value
  u?: string; // unit
  ip?: string; // interpretation
  rr?: string; // referenceRange
  vc?: string; // valueCode
  vcs?: string; // valueCodeSystem
  ct?: string; // category
  ab?: string; // abatement date
  sv?: string; // severity
}

export interface JPatient {
  gn?: string; // given name
  fn?: string; // family name
  gs?: string; // gender: 'M' | 'F' | 'O' | 'U'
  bd?: string; // birth date YYYY-MM-DD
  nat?: string; // nationality
  bt?: string; // blood type: 'A+', 'O-', etc.
  adr?: string; // postal address
  tel?: string; // patient phone
  eml?: string; // patient email
  idn?: string; // national ID
  ids?: JIdentifier[];
  adrs?: JAddress[];
  tels?: JTelecom[];
  gp?: string; // general practitioner
  lang?: string; // BCP-47 language tag
  ct?: JContact[]; // emergency contacts
}

export interface JemmaProfileJ {
  _j?: string; // "1.2"
  sid?: string;
  p?: JPatient;
  al?: JAllergy[]; // allergies
  md?: JMedication[]; // medications
  cn?: JCondition[]; // active conditions / problems
  ph?: JEntryGeneric[]; // past problems
  im?: JEntryGeneric[]; // immunizations
  pr?: JEntryGeneric[]; // procedures
  dv?: JEntryGeneric[]; // devices
  fs?: JEntryGeneric[]; // functional status
  pg?: JEntryGeneric[]; // pregnancy
  rs?: JEntryGeneric[]; // results
  ad?: JEntryGeneric[]; // advance directives
  cs?: JEntryGeneric[]; // care services / consents
  gl?: JEntryGeneric[]; // goals
  en?: JEntryGeneric[]; // encounters
  oc?: JEntryGeneric[]; // occupations
  pv?: JEntryGeneric[]; // provenance
}

// ── FHIR R4 IPS Basic Typings ─────────────────────────────────────

export interface FhirCoding {
  system?: string;
  code?: string;
  display?: string;
}

export interface FhirCodeableConcept {
  coding?: FhirCoding[];
  text?: string;
}

export interface FhirIdentifier {
  system?: string;
  value?: string;
}

export interface FhirReference {
  reference?: string;
  display?: string;
}

export interface FhirPeriod {
  start?: string;
  end?: string;
}

export interface FhirQuantity {
  value?: number;
  unit?: string;
  system?: string;
  code?: string;
}

export interface FhirCompositionSection {
  title?: string;
  code?: FhirCodeableConcept;
  entry?: FhirReference[];
}

export interface FhirComposition {
  resourceType: "Composition";
  id?: string;
  status: "final" | "amended" | "preliminary";
  type: FhirCodeableConcept;
  subject: FhirReference;
  date: string;
  author: FhirReference[];
  title: string;
  confidentiality?: string;
  section: FhirCompositionSection[];
}

export interface FhirHumanName {
  family?: string;
  given?: string[];
  text?: string;
}

export interface FhirContactPoint {
  system?: "phone" | "email" | "fax" | "pager" | "url" | "sms" | "other";
  value?: string;
  use?: "home" | "work" | "temp" | "old" | "mobile";
}

export interface FhirAddress {
  use?: "home" | "work" | "temp" | "old" | "billing";
  text?: string;
  line?: string[];
  city?: string;
  postalCode?: string;
  state?: string;
  country?: string;
}

export interface FhirPatientContact {
  relationship?: FhirCodeableConcept[];
  name?: FhirHumanName;
  telecom?: FhirContactPoint[];
  address?: FhirAddress;
}

export interface FhirPatient {
  resourceType: "Patient";
  id?: string;
  meta?: { profile?: string[] };
  identifier?: FhirIdentifier[];
  name?: FhirHumanName[];
  telecom?: FhirContactPoint[];
  gender?: "male" | "female" | "other" | "unknown";
  birthDate?: string;
  address?: FhirAddress[];
  communication?: Array<{
    language: FhirCodeableConcept;
  }>;
  contact?: FhirPatientContact[];
}

export interface FhirEntry {
  fullUrl?: string;
  resource?: any;
}

export interface FhirBundle {
  resourceType: "Bundle";
  id?: string;
  identifier?: FhirIdentifier;
  type: "document";
  timestamp?: string;
  entry: FhirEntry[];
}
