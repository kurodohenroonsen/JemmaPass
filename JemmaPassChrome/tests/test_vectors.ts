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

const vectorIds = [
  "ct-001-nominal",
  "ct-002-two-contacts-order",
  "ct-003-no-phone",
  "ct-004-phone-only",
  "ct-005-cjk-and-free-text",
  "ct-006-blank-fields",
];

for (const vecId of vectorIds) {
  test(`qa/vectors/contacts/${vecId}`, () => {
    const vec = loadVectorJson(vecId);
    const uiLang = vec.ui_lang || "en";
    const inputJ = vec.input_j;
    const expect = vec.expect;

    // 1. Test du Bundle FHIR
    const bundle = buildFhirBundle(inputJ, { uiLang, labels: resolver });
    const patientEntry = bundle.entry.find(e => e.resource?.resourceType === "Patient");
    assert.ok(patientEntry, "Bundle must contain a Patient resource");
    const contacts = patientEntry.resource.contact || [];

    // Nettoyer les contacts attendus et réels pour comparaison structurelle exacte
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
