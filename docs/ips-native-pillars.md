# FHIR-native IPS pillars — architecture note

_Branch `feat/ips-18-pillars-cleanup` · September 2026_

## Why

The hackathon build authored every profile in the compact `_j 1.2` JSON and
regenerated a FHIR IPS Bundle from it on each save. That works for the three
demo pillars but does not scale to the 18 IPS sections: `_j` only has room for
`{c, d, d_display}` per generic entry, and everything HL7 cares about
(dates, statuses, lots, performers, values, units…) had nowhere to live.

Decision (Kudoro, 2026-09-30): **the FHIR R4 IPS Bundle is the source of
truth; `_j` becomes a projection.** Pillars migrate one by one; Immunizations
is the first FHIR-native pillar.

## Storage

```
{externalFilesDir}/profiles/
├── {id}.json        JemmaProfileJ  — legacy-authored pillars (patient, allergies,
│                                     medications, conditions, contacts) + the
│                                     PROJECTION of the FHIR-native pillars (`im`, …)
└── {id}.fhir.json   FHIR R4 Bundle — SOURCE OF TRUTH for the FHIR-native pillars
```

`ProfilesRepository.writeProfileFiles(id, profile, native)` is the **single
write path**: it re-projects the native pillars into `_j`, writes the JSON,
then rebuilds the Bundle with `JemmaFhirBundleBuilder.build(hydrated, native)`.

Who is authoritative when a `_j` profile is saved (`resolveNativePillars`):

| `sourceFormat`                | native pillars come from                     |
|-------------------------------|----------------------------------------------|
| `MANUAL_EDIT`, `ASSISTANT_*`  | the existing Bundle (fallback: `_j` arrays)  |
| `DEMO_SEED`                   | `JemmaPersonasSeeder.getDemoNativePillars()` |
| anything else (QR, mesh, …)   | the incoming `_j` arrays (import wins)       |

A Bundle written before this branch has no `Immunization` resources: the
reader falls back to `_j.im`, and the next save migrates it into the Bundle.

Intra-bundle references are deterministic (`IpsFhirCodec.stableUrn(seed)` =
`urn:uuid:` + UUIDv3 of `sid|ResourceType|identity`), so an unchanged profile
rebuilds to the same document.

## Layers of one FHIR-native pillar (Immunizations as the template)

| Layer            | Immunizations                                                                 |
|------------------|-------------------------------------------------------------------------------|
| Domain           | `ips/IpsImmunization.kt` (+ `IpsNativePillars`)                               |
| FHIR mapping     | `ips/IpsFhirCodec.kt` — `toFhir` / `fromFhir` / `immunizationSection` (LOINC 11369-6) |
| `_j` projection  | `IpsImmunization.toJEntry()` / `fromJEntry()` — `JEntryGeneric{c,d,d_display,dt,cs,st,dn}` |
| Store API        | `ProfilesRepository.loadImmunizations` / `saveImmunizations`                   |
| Catalogs         | `pillars/IpsVaccineCatalog.kt` (SNOMED vaccine products, EN/FR/JA) + status   |
| UI               | `ui/profile/immunizations/` (EditFragment, Adapter, FormBottomSheet) + 3 layouts |
| Wiring           | `nav_graph.xml` (`dest_immunizations`, `action_detail_to_immunizations`), `MainActivity.FULL_BLEED_DESTINATION_NAMES`, `PillarRegistry.isActive`, `ProfileDetailFragment.renderPillars/renderImmunizations`, tile `<include layout="@layout/view_pillar_tile">` |
| Channels         | text QR section (`JemmaTextPayloadBuilder` + `JemmaTranslations` key), Gemma `@Tool getFocusProfileImmunizations` |
| Demo data        | `JemmaPersonasSeeder.getDemoNativePillars(sid)`                               |
| Tests (JVM)      | `test/.../ips/IpsImmunizationCodecTest.kt`, `IpsImmunizationProjectionTest.kt` |

### Checklist for the next pillar (Procedures, Devices, Results…)

1. Tests first: FHIR round trip (full / minimal / edge dates / status
   normalisation), Bundle embedding + section wiring, projection contract.
2. Domain class + `IpsNativePillars` field; `IpsFhirCodec.nativeOf()`.
3. `JemmaFhirBundleBuilder.build()`: entries + `Composition.section`
   (Procedures `47519-4`, Devices `46264-8`, Results `30954-2`).
4. `ProfilesRepository`: `load<Pillar>` / `save<Pillar>`; projection in
   `writeProfileFiles`; fallback in `readNativePillars`.
5. UI + wiring + strings (values, en, fr, ja, de, nl, zh-rCN).
6. Text QR section, Gemma tool, personas.

## Known limits / next steps

- Pocket Pass PDF does not print immunizations yet (≈55 pt free in column 1).
- Mesh SOS chunks carry `im[].c` only; `guessSystem` tags all-digit codes as
  SNOMED (CVX codes would be mis-tagged — we default to SNOMED products).
- Legacy pillars (patient, allergies, medications, conditions) are still
  `_j`-authored; each will flip to FHIR-native in its own sprint.
- CI: `.github/workflows/android-ci.yml` builds + runs the JVM tests on every
  push and publishes `<branch-slug>/latest.md` on the `ci-logs` branch.
