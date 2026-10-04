/**
 * JemmaPassChrome/tests/test_vectors.ts
 *
 * Rejoue tous les fichiers de qa/vectors/contacts/ (ct-001..ct-006).
 * Contrat d'interopérabilité multi-plateformes partagé entre Android, iOS et Chrome.
 */

import test from "node:test";
import assert from "node:assert/strict";
import * as fs from "node:fs";
import * as path from "node:path";
import { execSync } from "node:child_process";
import { buildFhirBundle } from "../core/fhir_builder.ts";
import { buildTextPayload } from "../core/text_payload_builder.ts";
import { StaticLocaleCodeLabelResolver } from "../core/code_label_resolver.ts";

// Chargement des locales pour le resolver
const resolver = new StaticLocaleCodeLabelResolver();
for (const lang of ["en", "fr", "ja"]) {
  const locPath = path.resolve("JemmaPassChrome/_locales", lang, "messages.json");
  if (fs.existsSync(locPath)) {
    const raw = JSON.parse(fs.readFileSync(locPath, "utf-8"));
    resolver.setLocale(lang, raw);
  }
}

function loadVectorJson(id: string): any {
  // 1. Essai depuis le working tree si présent
  const localPath = path.resolve("qa/vectors/contacts", `${id}.json`);
  if (fs.existsSync(localPath)) {
    return JSON.parse(fs.readFileSync(localPath, "utf-8"));
  }

  // 2. Fallback: extraction depuis git (origin/tests/pillar-contacts)
  try {
    const raw = execSync(`git show origin/tests/pillar-contacts:qa/vectors/contacts/${id}.json`, {
      encoding: "utf-8",
      stdio: ["pipe", "pipe", "ignore"],
    });
    return JSON.parse(raw);
  } catch (err) {
    throw new Error(`Unable to load vector ${id}: ${err}`);
  }
}

import { parseFhirBundle } from "../core/fhir_codec.ts";

// ── 1. Contrat qa/vectors/contacts/ ──────────────────────────────

const contactVectorIds = [
  "ct-001-nominal",
  "ct-002-two-contacts-order",
  "ct-003-no-phone",
  "ct-004-phone-only",
  "ct-005-cjk-and-free-text",
  "ct-006-blank-fields",
];

for (const vecId of contactVectorIds) {
  test(`qa/vectors/contacts/${vecId}`, () => {
    const localPath = path.resolve("qa/vectors/contacts", `${vecId}.json`);
    const vec = JSON.parse(fs.readFileSync(localPath, "utf-8"));
    const uiLang = vec.ui_lang || "en";
    const inputJ = vec.input_j;
    const expect = vec.expect;

    // 1. Test du Bundle FHIR
    const bundle = buildFhirBundle(inputJ, { uiLang, labels: resolver });
    const patientEntry = bundle.entry.find(e => e.resource?.resourceType === "Patient");
    assert.ok(patientEntry, "Bundle must contain a Patient resource");
    const contacts = patientEntry.resource.contact || [];

    assert.deepEqual(
      JSON.parse(JSON.stringify(contacts)),
      expect.patient_contact,
      `FHIR Patient.contact must match expected for ${vecId}`
    );

    // 2. Test du QR Texte Universel
    const textQr = buildTextPayload(inputJ, uiLang, 1800, resolver);

    if (expect.text_qr_contains) {
      let lastIndex = 0;
      for (const expectedStr of expect.text_qr_contains) {
        const foundIndex = textQr.indexOf(expectedStr, lastIndex);
        assert.ok(
          foundIndex !== -1,
          `Text QR must contain "${expectedStr}" after index ${lastIndex}. Full QR:\n${textQr}`
        );
        lastIndex = foundIndex + expectedStr.length;
      }
    }

    if (expect.text_qr_absent) {
      for (const forbiddenStr of expect.text_qr_absent) {
        assert.strictEqual(
          textQr.includes(forbiddenStr),
          false,
          `Text QR must NOT contain "${forbiddenStr}"`
        );
      }
    }
  });
}

// ── 2. Contrat qa/vectors/bloodgroup/ ─────────────────────────────

const bgVectorFiles = fs.readdirSync(path.resolve("qa/vectors/bloodgroup"))
  .filter(f => f.startsWith("bg-") && f.endsWith(".json"))
  .sort();

for (const bgFile of bgVectorFiles) {
  const vecId = bgFile.replace(".json", "");
  test(`qa/vectors/bloodgroup/${vecId}`, () => {
    const localPath = path.resolve("qa/vectors/bloodgroup", bgFile);
    const vec = JSON.parse(fs.readFileSync(localPath, "utf-8"));
    const inputJ = vec.input_j;
    const expectObs = vec.expect.blood_group_observations;

    const bundle = buildFhirBundle(inputJ, { uiLang: "en", labels: resolver });
    const patientEntry = bundle.entry.find(e => e.resource?.resourceType === "Patient");
    assert.ok(patientEntry, "Bundle must contain a Patient resource");
    const patientUrl = patientEntry.fullUrl;

    const actualBloodObs = bundle.entry
      .filter(e => e.resource?.resourceType === "Observation" &&
        e.resource?.code?.coding?.some((c: any) => c.code === "882-1"))
      .map(e => {
        const copy = JSON.parse(JSON.stringify(e.resource));
        delete copy.id;
        return copy;
      });

    // Remplacer @patient dans l'attendu par l'URL patient réelle
    const expectedNormalized = JSON.parse(
      JSON.stringify(expectObs).replaceAll("@patient", patientUrl)
    );
    for (const obs of expectedNormalized) {
      delete obs.id;
    }

    assert.deepEqual(
      actualBloodObs,
      expectedNormalized,
      `Blood group observations must match for ${vecId}`
    );
  });
}

// ── 3. Contrat qa/vectors/devices/ ────────────────────────────────

const dvVectorFiles = fs.readdirSync(path.resolve("qa/vectors/devices"))
  .filter(f => f.startsWith("dv-") && f.endsWith(".json"))
  .sort();

for (const dvFile of dvVectorFiles) {
  const vecId = dvFile.replace(".json", "");
  test(`qa/vectors/devices/${vecId}`, () => {
    const localPath = path.resolve("qa/vectors/devices", dvFile);
    const vec = JSON.parse(fs.readFileSync(localPath, "utf-8"));

    const patientUrl = "urn:uuid:patient-01";
    const devUrl = "urn:uuid:device-01";
    const devUseUrl = "urn:uuid:device-use-01";

    const devIn = JSON.parse(JSON.stringify(vec.input_fhir.device).replaceAll("@patient", patientUrl));
    const devUseIn = JSON.parse(
      JSON.stringify(vec.input_fhir.device_use_statement)
        .replaceAll("@patient", patientUrl)
        .replaceAll("@device", devUrl)
    );

    const inputBundle = {
      resourceType: "Bundle",
      id: "bundle-import-test",
      type: "document",
      entry: [
        {
          fullUrl: patientUrl,
          resource: {
            resourceType: "Patient",
            id: "patient-01",
            name: [{ family: "Haru" }]
          }
        },
        {
          fullUrl: devUrl,
          resource: devIn
        },
        {
          fullUrl: devUseUrl,
          resource: devUseIn
        }
      ]
    };

    // 1. Test de l'import FHIR vers projection _j
    const profile = parseFhirBundle(JSON.stringify(inputBundle));
    assert.ok(profile.dv && profile.dv.length > 0, "Imported profile must have dv entry");
    const actualJDv = profile.dv[0];

    // Ne comparer que les champs définis dans le contrat expect.j_dv (c, d, d_display, dt)
    const expectedJDv = vec.expect.j_dv;
    const projectedActual: any = {};
    if (actualJDv.c !== undefined) projectedActual.c = actualJDv.c;
    if (actualJDv.d !== undefined) projectedActual.d = actualJDv.d;
    if (actualJDv.d_display !== undefined) projectedActual.d_display = actualJDv.d_display;
    if (actualJDv.dt !== undefined) projectedActual.dt = actualJDv.dt;

    assert.deepEqual(
      projectedActual,
      expectedJDv,
      `Imported _j.dv projection must match expected for ${vecId}`
    );

    // 2. Test du réexport FHIR (rien perdu, rien ajouté)
    const reexportedBundle = buildFhirBundle(profile, { uiLang: "en", labels: resolver });
    const reexportedDevEntry = reexportedBundle.entry.find(e => e.resource?.resourceType === "Device");
    const reexportedDevUseEntry = reexportedBundle.entry.find(e => e.resource?.resourceType === "DeviceUseStatement");
    const reexportedPatientEntry = reexportedBundle.entry.find(e => e.resource?.resourceType === "Patient");

    assert.ok(reexportedDevEntry, "Reexported bundle must contain Device");
    assert.ok(reexportedDevUseEntry, "Reexported bundle must contain DeviceUseStatement");
    assert.ok(reexportedPatientEntry, "Reexported bundle must contain Patient");

    const actualDev = JSON.parse(JSON.stringify(reexportedDevEntry.resource));
    const actualDevUse = JSON.parse(JSON.stringify(reexportedDevUseEntry.resource));
    delete actualDev.id;
    delete actualDevUse.id;

    const expectedDev = JSON.parse(
      JSON.stringify(vec.expect.reexported.device).replaceAll("@patient", reexportedPatientEntry.fullUrl)
    );
    const expectedDevUse = JSON.parse(
      JSON.stringify(vec.expect.reexported.device_use_statement)
        .replaceAll("@patient", reexportedPatientEntry.fullUrl)
        .replaceAll("@device", reexportedDevEntry.fullUrl)
    );
    delete expectedDev.id;
    delete expectedDevUse.id;

    assert.deepEqual(actualDev, expectedDev, `Reexported Device must match expected for ${vecId}`);
    assert.deepEqual(actualDevUse, expectedDevUse, `Reexported DeviceUseStatement must match expected for ${vecId}`);
  });
}
