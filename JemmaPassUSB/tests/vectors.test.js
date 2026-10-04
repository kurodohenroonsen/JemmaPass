/**
 * JemmaPass USB — tests/vectors.test.js
 *
 * Suite de rejeu Node.js des vecteurs de contrat communs :
 *   1. qa/vectors/contacts/   (ct-001..006)
 *   2. qa/vectors/bloodgroup/ (bg-001..010)
 *   3. qa/vectors/devices/    (dv-001..002)
 *
 * Exécution : node --test JemmaPassUSB/tests/
 * Zéro dépendance externe (node:test natif).
 */
"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const { buildBloodGroupObservation } = require("../core/blood_group.js");
const { buildPatientContacts, buildTextQrContacts } = require("../core/contacts.js");
const { importDevicesFromEntries, exportDevicesToEntries } = require("../core/devices.js");

// Recherche du répertoire qa/vectors
function findVectorsDir(sub) {
  const candidates = [
    path.join(__dirname, "../../qa/vectors", sub),
    path.join(__dirname, "../qa/vectors", sub),
    path.join(process.cwd(), "qa/vectors", sub),
    path.join(process.cwd(), "../../qa/vectors", sub)
  ];
  for (const c of candidates) {
    if (fs.existsSync(c) && fs.statSync(c).isDirectory()) {
      return c;
    }
  }
  throw new Error(`qa/vectors/${sub} not found from ${__dirname}`);
}

function loadVectors(sub) {
  const dir = findVectorsDir(sub);
  return fs.readdirSync(dir)
    .filter(f => f.endsWith(".json"))
    .sort()
    .map(f => {
      const fullPath = path.join(dir, f);
      const data = JSON.parse(fs.readFileSync(fullPath, "utf-8"));
      return { file: f, data };
    });
}

// Comparaison structurelle récursive avec résolution de référence @patient
function diffStructural(pathStr, expected, actual, errors, patientRef = "@patient", deviceRef = "@device") {
  if (expected === null || expected === undefined) {
    if (actual !== null && actual !== undefined) {
      errors.push(`${pathStr}: expected ${expected}, found ${JSON.stringify(actual)}`);
    }
    return;
  }

  if (Array.isArray(expected)) {
    if (!Array.isArray(actual)) {
      errors.push(`${pathStr}: expected Array, found ${typeof actual}`);
      return;
    }
    if (expected.length !== actual.length) {
      errors.push(`${pathStr}: array length mismatch (expected ${expected.length}, got ${actual.length})`);
      return;
    }
    for (let i = 0; i < expected.length; i++) {
      diffStructural(`${pathStr}[${i}]`, expected[i], actual[i], errors, patientRef, deviceRef);
    }
    return;
  }

  if (typeof expected === "object") {
    if (!actual || typeof actual !== "object" || Array.isArray(actual)) {
      errors.push(`${pathStr}: expected Object, found ${typeof actual}`);
      return;
    }

    const expKeys = Object.keys(expected).filter(k => k !== "id"); // "id" est ignoré selon le contrat
    const actKeys = Object.keys(actual).filter(k => k !== "id");

    for (const k of expKeys) {
      if (!(k in actual)) {
        errors.push(`${pathStr}.${k} is missing (expected ${JSON.stringify(expected[k])})`);
      } else {
        diffStructural(`${pathStr}.${k}`, expected[k], actual[k], errors, patientRef, deviceRef);
      }
    }
    for (const k of actKeys) {
      if (!(k in expected)) {
        errors.push(`${pathStr}.${k} is unexpected (found ${JSON.stringify(actual[k])})`);
      }
    }
    return;
  }

  // Primitives
  let expVal = expected;
  if (typeof expVal === "string") {
    if (expVal === "@patient") expVal = patientRef;
    if (expVal === "@device") expVal = deviceRef;
  }
  let actVal = actual;

  if (expVal !== actVal) {
    errors.push(`${pathStr}: expected "${expVal}", found "${actVal}"`);
  }
}

// =========================================================================
// 1. Tests Contacts (qa/vectors/contacts/)
// =========================================================================
test("USB-VEC-CT Contacts Vectors (ct-001..ct-006)", async (t) => {
  const vectors = loadVectors("contacts");
  assert.equal(vectors.length >= 6, true, "Au moins 6 vecteurs contacts requis");

  for (const { file, data } of vectors) {
    await t.test(`Vector ${data.id} (${file}): ${data.title}`, () => {
      const uiLang = data.ui_lang || "en";
      const rawContacts = data.input_j?.p?.ct || [];
      const actualContacts = buildPatientContacts(rawContacts, uiLang);

      // Vérification Patient.contact
      const expectedContacts = data.expect?.patient_contact || [];
      const errors = [];
      diffStructural("patient_contact", expectedContacts, actualContacts, errors);
      assert.deepEqual(errors, [], `Divergences sur ${data.id}:\n${errors.join("\n")}`);

      // Vérification QR texte
      if (Array.isArray(data.expect?.text_qr_contains)) {
        const qrLines = buildTextQrContacts(rawContacts, uiLang);
        const qrCombined = qrLines.join("\n");
        for (const expectedStr of data.expect.text_qr_contains) {
          assert.equal(
            qrCombined.includes(expectedStr),
            true,
            `${data.id}: QR text doit contenir "${expectedStr}" (actuel:\n${qrCombined})`
          );
        }
      }
    });
  }
});

// =========================================================================
// 2. Tests Groupe Sanguin (qa/vectors/bloodgroup/)
// =========================================================================
test("USB-VEC-BG Blood Group Vectors (bg-001..bg-010)", async (t) => {
  const vectors = loadVectors("bloodgroup");
  assert.equal(vectors.length >= 10, true, "Au moins 10 vecteurs bloodgroup requis");

  for (const { file, data } of vectors) {
    await t.test(`Vector ${data.id} (${file}): ${data.title}`, () => {
      const bloodType = data.input_j?.p?.bt;
      const obs = buildBloodGroupObservation(bloodType, "@patient");
      const actualList = obs ? [obs] : [];

      const expectedList = data.expect?.blood_group_observations || [];
      const errors = [];
      diffStructural("blood_group_observations", expectedList, actualList, errors);
      assert.deepEqual(errors, [], `Divergences sur ${data.id}:\n${errors.join("\n")}`);
    });
  }
});

// =========================================================================
// 3. Tests Dispositifs Médicaux (qa/vectors/devices/)
// =========================================================================
test("USB-VEC-DV Devices Vectors (dv-001..dv-002)", async (t) => {
  const vectors = loadVectors("devices");
  assert.equal(vectors.length >= 2, true, "Au moins 2 vecteurs devices requis");

  for (const { file, data } of vectors) {
    await t.test(`Vector ${data.id} (${file}): ${data.title}`, () => {
      const inputDev = data.input_fhir?.device;
      const inputStmt = data.input_fhir?.device_use_statement;

      // Construction des entrées d'entrée
      const entries = [
        { fullUrl: "@device", resource: inputDev },
        { fullUrl: "@statement", resource: inputStmt }
      ];

      // Import
      const imported = importDevicesFromEntries(entries);

      // 1. Vérification projection _j.dv
      const expectedJDv = data.expect?.j_dv;
      if (expectedJDv) {
        assert.equal(imported.j_dv.length > 0, true, "j_dv ne doit pas être vide");
        const actualJDv = imported.j_dv[0];
        const errors = [];
        diffStructural("j_dv", expectedJDv, actualJDv, errors);
        assert.deepEqual(errors, [], `Divergences sur j_dv pour ${data.id}:\n${errors.join("\n")}`);
      }

      // 2. Vérification réexport sans perte
      const expectedReexported = data.expect?.reexported;
      if (expectedReexported) {
        const reexportedEntries = exportDevicesToEntries(imported.native_devices, "@patient", "test-sid");
        const reexportedDev = reexportedEntries.find(e => e.resource?.resourceType === "Device")?.resource;
        const reexportedStmt = reexportedEntries.find(e => e.resource?.resourceType === "DeviceUseStatement")?.resource;

        const errors = [];
        diffStructural("reexported.device", expectedReexported.device, reexportedDev, errors, "@patient", "@device_0");
        diffStructural("reexported.device_use_statement", expectedReexported.device_use_statement, reexportedStmt, errors, "@patient", "@device_0");
        assert.deepEqual(errors, [], `Divergences réexport pour ${data.id}:\n${errors.join("\n")}`);
      }
    });
  }
});
