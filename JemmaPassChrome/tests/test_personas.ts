/**
 * JemmaPassChrome/tests/test_personas.ts
 *
 * Rejoue les trois personas démo de référence (demo_kurodo, demo_haru, demo_kamekichi)
 * issus de la branche device-reports.
 * Vérifie l'équivalence structurelle avec le Bundle Android et prépare les fichiers
 * pour la validation officielle HL7 FHIR IPS 1.1.0.
 */

import test from "node:test";
import assert from "node:assert/strict";
import * as fs from "node:fs";
import * as path from "node:path";
import { execSync } from "node:child_process";
import { buildFhirBundle } from "../core/fhir_builder.ts";
import { buildTextPayload, utf8ByteLength } from "../core/text_payload_builder.ts";
import { StaticLocaleCodeLabelResolver } from "../core/code_label_resolver.ts";
import type { JemmaProfileJ, FhirBundle } from "../core/types.ts";

const baseDir = fs.existsSync(path.resolve("JemmaPassChrome")) ? path.resolve("JemmaPassChrome") : path.resolve(".");
const resolver = new StaticLocaleCodeLabelResolver();
for (const lang of ["en", "fr", "ja"]) {
  const locPath = path.resolve(baseDir, "_locales", lang, "messages.json");
  if (fs.existsSync(locPath)) {
    const raw = JSON.parse(fs.readFileSync(locPath, "utf-8"));
    resolver.setLocale(lang, raw);
  }
}

function loadDeviceReportFile(subpath: string): string {
  // Chemins connus dans device-reports
  const refPath = `origin/device-reports:feat-ips-18-pillars-cleanup/c5d6fd2-20261003-2218/files/${subpath}`;
  try {
    return execSync(`git show ${refPath}`, {
      encoding: "utf-8",
      stdio: ["pipe", "pipe", "ignore"],
    });
  } catch (err) {
    throw new Error(`Failed to load ${refPath}: ${err}`);
  }
}

const personas = ["demo_kurodo", "demo_haru", "demo_kamekichi"];
const outDir = path.resolve(baseDir, "tests/out/files");
fs.mkdirSync(outDir, { recursive: true });

for (const personaId of personas) {
  test(`Persona: ${personaId}`, () => {
    // 1. Charger le JSON de base _j 1.2
    const rawJ = loadDeviceReportFile(`${personaId}.json`);
    const profileJ: JemmaProfileJ = JSON.parse(rawJ);

    // 2. Charger le Bundle de référence généré par Android
    const androidFhirRaw = loadDeviceReportFile(`${personaId}.fhir.json`);
    const androidBundle: FhirBundle = JSON.parse(androidFhirRaw);

    // 3. Générer le Bundle avec JemmaPassChrome
    const generatedBundle = buildFhirBundle(profileJ, {
      timestamp: androidBundle.timestamp, // On aligne le timestamp pour faciliter la comparaison
      labels: resolver,
      uiLang: profileJ.p?.lang || "en",
    });

    // Sauvegarder le bundle produit pour le validateur HL7
    fs.writeFileSync(
      path.join(outDir, `${personaId}.fhir.json`),
      JSON.stringify(generatedBundle, null, 2) + "\n"
    );

    // 4. Vérifications d'équivalence
    assert.strictEqual(
      generatedBundle.resourceType,
      "Bundle",
      "Must be a Bundle"
    );
    assert.strictEqual(
      generatedBundle.type,
      "document",
      "Bundle type must be document"
    );

    // Identifier déterministe identique
    assert.deepEqual(
      generatedBundle.identifier,
      androidBundle.identifier,
      `Bundle identifier must match Android for ${personaId}`
    );

    // Première entrée: Composition
    assert.strictEqual(
      generatedBundle.entry[0]?.resource?.resourceType,
      "Composition",
      "First entry must be Composition"
    );
    assert.strictEqual(
      generatedBundle.entry[0]?.fullUrl,
      androidBundle.entry[0]?.fullUrl,
      "Composition fullUrl must match Android"
    );

    // Deuxième entrée: Patient
    assert.strictEqual(
      generatedBundle.entry[1]?.resource?.resourceType,
      "Patient",
      "Second entry must be Patient"
    );
    assert.strictEqual(
      generatedBundle.entry[1]?.fullUrl,
      androidBundle.entry[1]?.fullUrl,
      "Patient fullUrl must match Android"
    );

    // Vérifier les sections de la Composition
    const compSections = generatedBundle.entry[0].resource.section;
    const androidSections = androidBundle.entry[0].resource.section;
    assert.strictEqual(
      compSections.length,
      androidSections.length,
      `Composition sections count must match for ${personaId}`
    );
    for (let i = 0; i < compSections.length; i++) {
      assert.strictEqual(
        compSections[i].title,
        androidSections[i].title,
        `Section ${i} title must match`
      );
      assert.strictEqual(
        compSections[i].code.coding[0].code,
        androidSections[i].code.coding[0].code,
        `Section ${i} LOINC code must match`
      );
    }

    // 5. Vérifier le QR texte universel en FR, EN et JA
    for (const lang of ["en", "fr", "ja"]) {
      const textQr = buildTextPayload(profileJ, lang, 1800, resolver);
      const byteLen = utf8ByteLength(textQr);
      assert.ok(
        byteLen <= 1800,
        `Text QR (${lang}) for ${personaId} must fit within 1800 bytes (got ${byteLen})`
      );
      assert.ok(
        textQr.includes("JEMMA"),
        `Text QR must contain JEMMA header/footer for ${personaId}`
      );
    }
  });
}
