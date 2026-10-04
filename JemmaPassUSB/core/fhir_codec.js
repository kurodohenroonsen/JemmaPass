/**
 * JemmaPass USB — core/fhir_codec.js
 *
 * Décodeur et constructeur de Bundle FHIR R4 conforme HL7 IPS 1.1.0.
 * Lit un document FHIR (.fhir.json) et le projette en format _j 1.2,
 * et inversement réexporte un Bundle FHIR R4 IPS valide sans perte.
 *
 * Zéro dépendance externe, compatible Node CLI et Navigateur.
 */
"use strict";

const { buildBloodGroupObservation, extractBloodGroupFromObservation } = typeof require !== "undefined"
  ? require("./blood_group.js")
  : globalThis.JemmaBloodGroup;

const { buildPatientContacts, buildTextQrContacts } = typeof require !== "undefined"
  ? require("./contacts.js")
  : globalThis.JemmaContacts;

const { importDevicesFromEntries, exportDevicesToEntries } = typeof require !== "undefined"
  ? require("./devices.js")
  : globalThis.JemmaDevices;

const PROFILE_PATIENT_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips";
const PROFILE_BUNDLE_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Bundle-uv-ips";
const PROFILE_COMPOSITION_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Composition-uv-ips";

/**
 * Analyse et décode un document Bundle FHIR R4 en structure de passeport JemmaPass.
 *
 * @param {string|Object} rawInput - Chaîne JSON ou objet Bundle FHIR
 * @returns {Object} {
 *   profile: Object,       // Format court _j 1.2
 *   native: Object,        // Ressources et métadonnées FHIR natives conservées
 *   rawBundle: Object      // Le document original
 * }
 */
function parseBundle(rawInput) {
  let bundle;
  if (typeof rawInput === "string") {
    bundle = JSON.parse(rawInput);
  } else if (rawInput && typeof rawInput === "object") {
    bundle = rawInput;
  } else {
    throw new TypeError("parseBundle: argument must be a JSON string or object");
  }

  if (bundle.resourceType !== "Bundle") {
    throw new Error("Invalid FHIR document: resourceType must be 'Bundle'");
  }

  const entries = bundle.entry || [];
  const resources = entries.map(e => e.resource).filter(Boolean);

  // 1. Patient
  const patient = resources.find(r => r.resourceType === "Patient") || {};
  const nameObj = patient.name?.[0] || {};
  const givenName = Array.isArray(nameObj.given) ? nameObj.given.join(" ") : (nameObj.given || "");
  const familyName = nameObj.family || "";

  // Contacts
  const rawContacts = [];
  if (Array.isArray(patient.contact)) {
    for (const c of patient.contact) {
      const contactItem = {};
      if (c.name?.text) contactItem.n = c.name.text;
      // Telecom
      if (Array.isArray(c.telecom)) {
        for (const t of c.telecom) {
          if (t.system === "phone" && t.value) contactItem.p = t.value;
          if (t.system === "email" && t.value) contactItem.e = t.value;
        }
      }
      // Address
      if (c.address?.text) contactItem.adr = c.address.text;
      // Relationship
      if (Array.isArray(c.relationship) && c.relationship.length > 0) {
        const rel = c.relationship[0];
        const code = rel.coding?.[0]?.code;
        contactItem.r = code || rel.text || "";
      }
      rawContacts.push(contactItem);
    }
  }

  // Telecom patient
  let phone = "";
  let email = "";
  if (Array.isArray(patient.telecom)) {
    for (const t of patient.telecom) {
      if (t.system === "phone" && t.value) phone = t.value;
      if (t.system === "email" && t.value) email = t.value;
    }
  }

  // Langue
  let lang = "";
  if (patient.communication?.[0]?.language?.coding?.[0]?.code) {
    lang = patient.communication[0].language.coding[0].code;
  }

  // 2. Groupe sanguin (depuis les Observations)
  let bloodType = null;
  const observations = resources.filter(r => r.resourceType === "Observation");
  for (const obs of observations) {
    const bt = extractBloodGroupFromObservation(obs);
    if (bt) {
      bloodType = bt;
      break;
    }
  }

  // 3. Dispositifs médicaux
  const deviceData = importDevicesFromEntries(entries);

  // Construction du profil _j 1.2
  const profileJ = {
    _j: "1.2",
    sid: bundle.id || "passport",
    p: {
      gn: givenName || undefined,
      fn: familyName || undefined,
      gs: patient.gender ? patient.gender.charAt(0).toUpperCase() : undefined,
      bd: patient.birthDate || undefined,
      bt: bloodType || undefined,
      adr: patient.address?.[0]?.text || undefined,
      tel: phone || undefined,
      eml: email || undefined,
      lang: lang || undefined,
      ct: rawContacts.length > 0 ? rawContacts : undefined
    },
    dv: deviceData.j_dv.length > 0 ? deviceData.j_dv : undefined,
    al: [],
    md: []
  };

  // Nettoyage des clés undefined dans p
  for (const k of Object.keys(profileJ.p)) {
    if (profileJ.p[k] === undefined) delete profileJ.p[k];
  }

  return {
    profile: profileJ,
    native: {
      native_devices: deviceData.native_devices,
      observations: observations,
      rawPatient: patient
    },
    rawBundle: bundle
  };
}

/**
 * Construit un Bundle FHIR R4 type "document" conforme HL7 IPS 1.1.0 depuis un profil _j 1.2.
 *
 * @param {Object} profileJ - Profil au format _j 1.2
 * @param {Object} [options] - Options (nativeResources, uiLang, sid)
 * @returns {Object} Bundle FHIR R4 structuré
 */
function buildBundle(profileJ, options = {}) {
  const p = profileJ?.p || {};
  const sid = profileJ?.sid || options.sid || "jemma-01";
  const uiLang = options.uiLang || p.lang || "en";

  const patientUrn = "urn:uuid:patient-01";
  const compositionUrn = "urn:uuid:composition-01";
  const entries = [];

  // 1. Patient Resource
  const patientResource = {
    resourceType: "Patient",
    id: "patient-01",
    meta: {
      profile: [PROFILE_PATIENT_UV_IPS]
    },
    name: []
  };

  const nameEntry = {};
  if (p.fn) nameEntry.family = p.fn;
  if (p.gn) nameEntry.given = [p.gn];
  if (p.fn || p.gn) patientResource.name.push(nameEntry);

  if (p.gs) {
    const g = p.gs.toUpperCase();
    if (g === "M") patientResource.gender = "male";
    else if (g === "F") patientResource.gender = "female";
    else patientResource.gender = "other";
  }

  if (p.bd) patientResource.birthDate = p.bd;

  // Télécoms
  const patientTelecoms = [];
  if (p.tel) patientTelecoms.push({ system: "phone", value: p.tel });
  if (p.eml) patientTelecoms.push({ system: "email", value: p.eml });
  if (patientTelecoms.length > 0) patientResource.telecom = patientTelecoms;

  // Adresse
  if (p.adr) patientResource.address = [{ text: p.adr }];

  // Langue
  if (p.lang) {
    patientResource.communication = [
      {
        language: {
          coding: [
            {
              system: "urn:ietf:bcp:47",
              code: p.lang
            }
          ]
        }
      }
    ];
  }

  // Contacts d'urgence (via core/contacts.js)
  const fhirContacts = buildPatientContacts(p.ct, uiLang);
  if (fhirContacts.length > 0) {
    patientResource.contact = fhirContacts;
  }

  entries.push({
    fullUrl: patientUrn,
    resource: patientResource
  });

  // 2. Groupe sanguin (Observation LOINC 882-1 via core/blood_group.js)
  const bgObs = buildBloodGroupObservation(p.bt, patientUrn);
  if (bgObs) {
    entries.push({
      fullUrl: "urn:uuid:observation-bloodgroup-01",
      resource: bgObs
    });
  }

  // 3. Dispositifs médicaux
  const nativeDevs = options.nativeDevices || [];
  if (nativeDevs.length > 0) {
    const devEntries = exportDevicesToEntries(nativeDevs, patientUrn, sid);
    entries.push(...devEntries);
  }

  // 4. Composition (En-tête clinique IPS)
  const compositionResource = {
    resourceType: "Composition",
    id: "composition-01",
    meta: {
      profile: [PROFILE_COMPOSITION_UV_IPS]
    },
    status: "final",
    type: {
      coding: [
        {
          system: "http://loinc.org",
          code: "60591-5",
          display: "Patient summary Document"
        }
      ]
    },
    subject: {
      reference: patientUrn
    },
    date: new Date().toISOString(),
    author: [
      {
        reference: patientUrn
      }
    ],
    title: "International Patient Summary (IPS)",
    section: []
  };

  // Section Résultats si groupe sanguin présent
  if (bgObs) {
    compositionResource.section.push({
      title: "Results",
      code: {
        coding: [
          {
            system: "http://loinc.org",
            code: "30954-2",
            display: "Relevant diagnostic tests/laboratory data Narrative"
          }
        ]
      },
      entry: [
        {
          reference: "urn:uuid:observation-bloodgroup-01"
        }
      ]
    });
  }

  // Placer la Composition en première entrée selon la norme FHIR Document
  entries.unshift({
    fullUrl: compositionUrn,
    resource: compositionResource
  });

  return {
    resourceType: "Bundle",
    id: sid,
    meta: {
      profile: [PROFILE_BUNDLE_UV_IPS]
    },
    type: "document",
    timestamp: new Date().toISOString(),
    entry: entries
  };
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    parseBundle,
    buildBundle
  };
}
if (typeof globalThis !== "undefined") {
  globalThis.JemmaFhirCodec = {
    parseBundle,
    buildBundle
  };
}
