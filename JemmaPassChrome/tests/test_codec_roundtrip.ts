/**
 * JemmaPassChrome/tests/test_codec_roundtrip.ts
 *
 * Tests de cycle complet :
 *   - Compression/Décompression QR `_j2:` (RFC 1951)
 *   - Décodage JSON brut LEGACY
 *   - Import Bundle FHIR R4 IPS -> JemmaProfileJ
 *   - Export JemmaProfileJ -> Bundle FHIR R4 IPS
 *   - Réconciliation et inviolabilité du groupe sanguin
 */

import test from "node:test";
import assert from "node:assert/strict";
import { decodePayload, encodeCompressed, PayloadFormat } from "../core/payload_codec.ts";
import { parseFhirBundle } from "../core/fhir_codec.ts";
import { buildFhirBundle } from "../core/fhir_builder.ts";
import { reconcileBloodGroup, normalizeBloodGroup, snomedCodeFromBloodGroup } from "../core/blood_group.ts";
import type { JemmaProfileJ } from "../core/types.ts";

test("Roundtrip _j2: compression and decompression", async () => {
  const profile: JemmaProfileJ = {
    _j: "1.2",
    sid: "test_roundtrip",
    p: {
      gn: "Taro",
      fn: "Yamada",
      gs: "M",
      bd: "1990-01-01",
      bt: "A+",
      adr: "Tokyo, Japan",
      ct: [
        { n: "Hanako Yamada", r: "SPS", p: "+81 90 1234 5678" }
      ]
    },
    al: [
      { c: "91936005", s: "H", st: "A", d_display: "Penicillin" }
    ],
    md: [
      { c: "C07AB07", d_display: "Bisoprolol", v: "2.5", u: "mg", r: "O" }
    ]
  };

  // 1. Encode vers _j2:
  const encResult = await encodeCompressed(profile);
  assert.ok(encResult.success, "Encoding must succeed");
  assert.ok(encResult.payload.startsWith("_j2:"), "Must have _j2: prefix");

  // 2. Decode depuis _j2:
  const decResult = await decodePayload(encResult.payload);
  assert.ok(decResult.success, "Decoding must succeed");
  assert.strictEqual(decResult.format, PayloadFormat.COMPRESSED);

  // 3. Vérification des données restaurées
  assert.strictEqual(decResult.profile.p?.gn, "Taro");
  assert.strictEqual(decResult.profile.p?.fn, "Yamada");
  assert.strictEqual(decResult.profile.p?.bt, "A+");
  assert.strictEqual(decResult.profile.al?.length, 1);
  assert.strictEqual(decResult.profile.al?.[0].c, "91936005");
  assert.strictEqual(decResult.profile.md?.length, 1);
  assert.strictEqual(decResult.profile.md?.[0].c, "C07AB07");
});

test("Decoding legacy JSON format", async () => {
  const legacyJson = JSON.stringify({
    _j: "1.2",
    p: { gn: "Alice", fn: "Smith", gs: "F", bt: "O-" },
    al: [],
    md: []
  });

  const res = await decodePayload(legacyJson);
  assert.ok(res.success);
  assert.strictEqual(res.format, PayloadFormat.LEGACY);
  assert.strictEqual(res.profile.p?.gn, "Alice");
  assert.strictEqual(res.profile.p?.bt, "O-");
});

test("Blood group normalization and reconciliation", () => {
  assert.strictEqual(normalizeBloodGroup("A+"), "A+");
  assert.strictEqual(normalizeBloodGroup("a positive"), "A+");
  assert.strictEqual(normalizeBloodGroup("O Rh(D) pos"), "O+");
  assert.strictEqual(normalizeBloodGroup("ab -"), "AB-");
  assert.strictEqual(normalizeBloodGroup("invalid"), null);

  assert.strictEqual(snomedCodeFromBloodGroup("O+"), "278147001");
  assert.strictEqual(snomedCodeFromBloodGroup("A+"), "278149003");

  // Réconciliation: si p.bt = "B+", une observation 882-1 avec code SNOMED 278150003 est générée
  const results = reconcileBloodGroup([], "test_sid", "B+");
  assert.strictEqual(results.length, 1);
  assert.strictEqual(results[0].c, "882-1");
  assert.strictEqual(results[0].vc, "278150003");
});

test("FHIR Bundle export and import roundtrip", () => {
  const initial: JemmaProfileJ = {
    _j: "1.2",
    sid: "roundtrip_fhir",
    p: {
      gn: "Kenji",
      fn: "Sato",
      gs: "M",
      bd: "1985-05-15",
      bt: "AB+",
      lang: "ja-JP",
      ct: [{ n: "Aoi Sato", r: "DAUC", p: "+81 80 9876 5432" }]
    },
    al: [{ c: "300916003", s: "L", st: "A", d_display: "Latex allergy" }],
    md: [{ c: "N02BE01", d_display: "Paracetamol", v: "500", u: "mg", r: "O", ms: "active" }],
    cn: [{ c: "59621000", st: "active", d_display: "Hypertension" }]
  };

  // 1. Export vers Bundle FHIR R4
  const bundle = buildFhirBundle(initial);
  const bundleJson = JSON.stringify(bundle);

  // 2. Import depuis Bundle FHIR R4
  const imported = parseFhirBundle(bundleJson);

  // 3. Vérifications
  assert.strictEqual(imported.p?.gn, "Kenji");
  assert.strictEqual(imported.p?.fn, "Sato");
  assert.strictEqual(imported.p?.gs, "M");
  assert.strictEqual(imported.p?.bd, "1985-05-15");
  assert.strictEqual(imported.p?.bt, "AB+");
  assert.strictEqual(imported.al?.length, 1);
  assert.strictEqual(imported.al?.[0].c, "300916003");
  assert.strictEqual(imported.md?.length, 1);
  assert.strictEqual(imported.md?.[0].c, "N02BE01");
  assert.strictEqual(imported.cn?.length, 1);
  assert.strictEqual(imported.cn?.[0].c, "59621000");
  assert.strictEqual(imported.p?.ct?.length, 1);
  assert.strictEqual(imported.p?.ct?.[0].n, "Aoi Sato");
});
