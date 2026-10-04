/**
 * JemmaPass USB — core/devices.js
 *
 * Traitement et préservation des dispositifs médicaux (Device + DeviceUseStatement <-> _j.dv).
 * Strictement aligné sur le contrat commun qa/vectors/devices/ (dv-001, dv-002).
 * Préserve intégralement sans perte la date d'implantation, la note IRM, le site anatomique
 * et l'identification UDI/fabricant lors d'un cycle import-export.
 */
"use strict";

const PROFILE_DEVICE_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Device-uv-ips";
const PROFILE_DEVICE_USE_STATEMENT_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/DeviceUseStatement-uv-ips";

/**
 * Importe et décode les dispositifs médicaux depuis les entrées d'un Bundle FHIR R4.
 *
 * @param {Array<Object>} [entries] - Tableau des entrées (entry[]) du Bundle
 * @returns {Object} {
 *   j_dv: Array<Object>,         // Projection pour _j 1.2
 *   native_devices: Array<Object> // Paires complètes conservées pour réexport { device, statement }
 * }
 */
function importDevicesFromEntries(entries) {
  if (!Array.isArray(entries) || entries.length === 0) {
    return { j_dv: [], native_devices: [] };
  }

  // Indexation par fullUrl et par id
  const resourcesByUrl = new Map();
  const devicesById = new Map();
  const statements = [];

  for (const e of entries) {
    const res = e?.resource;
    if (!res) continue;
    if (e.fullUrl) resourcesByUrl.set(e.fullUrl, res);
    if (res.resourceType === "Device" && res.id) {
      devicesById.set(res.id, res);
    }
    if (res.resourceType === "DeviceUseStatement") {
      statements.push(res);
    }
  }

  const jDvList = [];
  const nativeDevices = [];

  for (const stmt of statements) {
    const devRef = stmt.device?.reference;
    let devRes = null;
    if (devRef) {
      devRes = resourcesByUrl.get(devRef) ||
        devicesById.get(devRef.replace(/^urn:uuid:/, "").replace(/^Device\//, ""));
    }

    // Extraction pour la projection _j.dv
    const jDv = {};
    const coding = devRes?.type?.coding?.[0];
    if (coding?.code) jDv.c = coding.code;

    // Note (ex: alerte IRM "MRI-conditional") — absent si aucune note
    const noteText = stmt.note?.[0]?.text;
    if (noteText) jDv.d = noteText;

    // Libellé court affiché
    const label = devRes?.type?.text || coding?.display;
    if (label) jDv.d_display = label;

    // Date / période d'implantation (timingDateTime 1..1 selon IPS)
    const timingDate = stmt.timingDateTime || stmt.recordedOn;
    if (timingDate) jDv.dt = timingDate;

    jDvList.push(jDv);
    nativeDevices.push({
      device: devRes,
      statement: stmt
    });
  }

  return {
    j_dv: jDvList,
    native_devices: nativeDevices
  };
}

/**
 * Réexporte les ressources Device et DeviceUseStatement sans perte pour un Bundle FHIR R4.
 * Résout les références @patient et @device vers les URNs du Bundle.
 *
 * @param {Array<Object>} nativeDevices - Liste des paires { device, statement }
 * @param {string} patientRef - URN du Patient (ex: "urn:uuid:patient-01" ou "@patient")
 * @param {string} [sid="jemma"] - SID du profil pour dérivation d'URN stable
 * @returns {Array<Object>} Tableau d'entrées FHIR (ressources avec fullUrl résolus)
 */
function exportDevicesToEntries(nativeDevices, patientRef = "@patient", sid = "jemma") {
  if (!Array.isArray(nativeDevices) || nativeDevices.length === 0) return [];
  const resultEntries = [];

  nativeDevices.forEach((pair, idx) => {
    if (!pair || !pair.statement) return;
    const deviceUrn = `@device_${idx}`;

    // 1. Ressource Device
    if (pair.device) {
      const devCopy = JSON.parse(JSON.stringify(pair.device));
      // Résolution du patient dans Device
      if (devCopy.patient) {
        devCopy.patient.reference = patientRef;
      }
      resultEntries.push({
        fullUrl: deviceUrn,
        resource: devCopy
      });
    }

    // 2. Ressource DeviceUseStatement
    const stmtCopy = JSON.parse(JSON.stringify(pair.statement));
    if (stmtCopy.subject) {
      stmtCopy.subject.reference = patientRef;
    }
    if (stmtCopy.device) {
      stmtCopy.device.reference = pair.device ? deviceUrn : (stmtCopy.device.reference || "@device");
    }
    resultEntries.push({
      fullUrl: `@statement_${idx}`,
      resource: stmtCopy
    });
  });

  return resultEntries;
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    PROFILE_DEVICE_UV_IPS,
    PROFILE_DEVICE_USE_STATEMENT_UV_IPS,
    importDevicesFromEntries,
    exportDevicesToEntries
  };
}
if (typeof globalThis !== "undefined") {
  globalThis.JemmaDevices = {
    PROFILE_DEVICE_UV_IPS,
    PROFILE_DEVICE_USE_STATEMENT_UV_IPS,
    importDevicesFromEntries,
    exportDevicesToEntries
  };
}
