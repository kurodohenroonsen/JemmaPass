# FHIR-native IPS pillars — architecture note

_Branch `feat/ips-18-pillars-cleanup` · September 2026_

## Why

The hackathon build authored every profile in the compact `_j 1.2` JSON and
regenerated a FHIR IPS Bundle from it on each save. That works for the three
demo pillars but does not scale to the 18 IPS sections: `_j` only has room for
`{c, d, d_display}` per generic entry, and everything HL7 cares about
(dates, statuses, lots, performers, values, units…) had nowhere to live.

Decision (Kudoro, 2026-09-30): **the FHIR R4 IPS Bundle is the source of
truth; `_j` becomes a projection.** Pillars migrate one by one: Immunizations
(sprint 1), then Procedures + Medical Devices (sprint 2).

## Storage

```
{externalFilesDir}/profiles/
├── {id}.json        JemmaProfileJ  — legacy-authored pillars (patient, allergies,
│                                     medications, conditions, contacts) + the
│                                     PROJECTION of the FHIR-native pillars (`im`, `pr`, `dv`, …)
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

A Bundle written before a pillar went native has no resources for it: the
reader falls back to that pillar's `_j` array (per pillar, `ifEmpty { fromJ }`),
and the next save migrates it into the Bundle.

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

## Sprint 2 — Procedures 🏥 and Medical Devices 📟

| Layer            | Procedures                                              | Medical devices                                                     |
|------------------|---------------------------------------------------------|---------------------------------------------------------------------|
| FHIR             | `Procedure` (Procedure-uv-ips), `performed[x]` DateTime or `performedString "unknown"`, `bodySite.text`, `outcome.text`, `performer[0].actor.display`, `location.display`, `note` | `Device` (Device-uv-ips: `type`, `status`, `patient`, `udiCarrier` GS1, `manufacturer`, `modelNumber`, `serialNumber`, `deviceName` patient-reported when free text) **+** `DeviceUseStatement` (DeviceUseStatement-uv-ips: `status`, `subject`, `device` → Device fullUrl, `timingDateTime`, `bodySite.text`, `note`) |
| Section          | LOINC `47519-4` → Procedure fullUrls                    | LOINC `46264-8` → DeviceUseStatement fullUrls                       |
| Domain           | `ips/IpsProcedure.kt` (`IpsProcedureStatus` = FHIR event-status) | `ips/IpsDevice.kt` (`IpsDeviceStatus` active / inactive / entered-in-error, mapped to `Device.status` and `DeviceUseStatement.status` active / completed / entered-in-error) |
| `_j` projection  | `pr[]` — `c, cs (non-SNOMED), d_display, d (note), dt, st (non-completed)` | `dv[]` — `c, cs, d_display, d (note), dt, st (non-active)`   |
| Store API        | `loadProcedures` / `saveProcedures`                     | `loadDevices` / `saveDevices`                                       |
| Catalogs         | `pillars/IpsProcedureCatalog.kt` (12 SNOMED procedures, EN/FR/JA) + `IpsProcedureStatusCatalog` | `pillars/IpsDeviceCatalog.kt` (6 SNOMED devices) + `IpsDeviceStatusCatalog` |
| Picker           | `KbDrugPickerDialog.newInstance(title, lang, category = "Procedure", suggestions, suggestionsSystem)` — catalog suggestions while the query is < 2 chars; while typing, the catalog entries whose aliases match come first, then KB FTS5 hits on `terminology_codes.category` de-duplicated by (system, code) and label; when a KB row carries `snomed_code` the picker surfaces the SNOMED code instead of the UMLS CUI and localises the label through `ips_valuesets_translations` (`getLocalizedDisplays`, one query per search); `searchHint` per pillar | same with `category = "Device"` |
| UI               | `ui/profile/procedures/` (EditFragment, Adapter, FormBottomSheet) + 3 layouts + `strings_jemma_procedures_edit.xml` ×6 | `ui/profile/devices/` (+ UDI plausibility check: GS1 `(01)…`, HIBCC `+…`, ICCBBA `=…`, bare 8–14-digit GTIN) + `strings_jemma_devices_edit.xml` ×6 |
| Wiring           | `dest_procedures`, `action_detail_to_procedures`, `action_gallery_to_procedures`, `renderProcedures` | `dest_devices`, `action_detail_to_devices`, `action_gallery_to_devices`, `renderDevices` |
| Channels         | text QR `🏥 [ PROCEDURES ]` (25 langs), Gemma `getFocusProfileProcedures` | text QR `📟 [ MEDICAL DEVICES ]`, Gemma `getFocusProfileDevices`   |
| Demo data        | Kurodo: appendectomy 1995-07-12, colonoscopy 2024-02-19 · Haru: CABG 2015-09-02, cesarean "1975" | Haru: pacemaker (UDI, Medtronic, 2021-03-15), hearing aid (Phonak, "2019-06") |
| Tests (JVM)      | `test/.../ips/IpsProcedureDeviceCodecTest.kt` (round trips, JSON essentials, projections, bundle wiring, determinism) | idem |

Knowledge base facts learnt from the device dump (`device-reports/kb/`):
`terminology_codes` holds SNOMED CT (19 697 codes, no category) and UMLS
(760 485 CUIs, categories Medication 135 541 · Condition 69 523 · Procedure
51 470 · Device 14 048 · allergens · food); `ips_valuesets(_translations)`
carries the IPS free sets — `procedures-snomed-ct-ips-free-set` (6 069),
`medical-devices-snomed-ct-ips-free-set` (260), `problems-…` (70 956),
`vaccines-…` (88), and the `results-*` sets (blood group, microorganism,
presence/absence, radiology, specimen). **No LOINC table**: the Results
pillar will need an in-app LOINC catalog for common lab observations.

## Sprint 3 — Results 🧪 (foundation)

| Layer            | Results                                                                                   |
|------------------|-------------------------------------------------------------------------------------------|
| FHIR             | `Observation` — profile `Observation-results-laboratory-uv-ips` (category `laboratory`), `Observation-results-radiology-uv-ips` (category `imaging`) or `Observation-results-uv-ips`; `status`, `category`, `code` (LOINC, free text allowed), `subject`, `effectiveDateTime`, **one** `value[x]` — `valueQuantity` (exact decimal + UCUM `unit`/`system`/`code`), `valueCodeableConcept` (e.g. SNOMED blood group) or `valueString` —, `interpretation` (v3 ObservationInterpretation), `referenceRange[0].low/high` (same UCUM unit), `performer[0].display`, `note` |
| Section          | LOINC `30954-2` "Relevant diagnostic tests/laboratory data" → Observation fullUrls         |
| Domain           | `ips/IpsResult.kt` (`IpsResultStatus`, `IpsResultCategory`, `IpsResultInterpretation`, `IpsDecimal`) |
| `_j` projection  | `rs[]` — `c, cs (non-LOINC), d_display, d (note), dt, st (non-final), v (value), u (UCUM), ip, rr ("3.5-5.1" / "≥60" / "≤100"), vc (coded value), ct (non-laboratory category)` |
| Store API        | `loadResults` / `saveResults`                                                             |
| Catalog          | `pillars/IpsResultCatalog.kt` — 31 embedded LOINC tests with EN/FR/JA labels, default UCUM unit and value kind (the KB has **no LOINC table**), `UNITS` for the unit picker, status / interpretation / category catalogs |
| Demo data        | Kurodo: HbA1c 5.6 % (N), LDL 131 mg/dL (H), creatinine 0.9 mg/dL · Haru: potassium, hemoglobin 11.8 g/dL (L), eGFR 48 (L), blood group O Rh+ (coded), chest X-ray (imaging, text) |
| Tests (JVM)      | `test/.../ips/IpsResultCodecTest.kt` (13), `pillars/IpsResultCatalogTest.kt` (4)          |
| QA kit           | `verify_profiles.py` 🧪 rs (P4/P5/P6 + P6c values ⇄ projection), `--expect-rs`             |

Blood type: `p.bt` is mirrored on every write as a derived Observation 882-1
(`IpsBloodGroup.sync`, id `rs-blood-group-<sid>`, SNOMED value from the IPS free
set confirmed in the KB dump) — the home-made Patient extension is gone.

HL7 validator (cycle 6) structural fixes: Bundle.identifier (bdl-9), blank name
parts omitted, UDI issuer `http://hl7.org/fhir/NamingSystem/gs1`, LOINC section
displays aligned. Run the validator with `-locale en`: the fr locale turns every
English display name into an error.

Decimals travel as exact `BigDecimal` (bignum) in the Kotlin FHIR model and as JSON
numbers on disk (`5.4`, `120.0`); `IpsDecimal.trimZeros` restores the typed text.

## Sprint 4 — Past Problems 📜 (History of Past Illness, LOINC 11348-0)

| Layer | Past Problems |
|---|---|
| Domain | `ips/IpsPastProblem.kt` — clinicalStatus resolved / inactive / remission, onset + abatement (partial dates, abatement ≥ onset), severity = IPS LOINC answers (LA6752-5 / LA6751-7 / LA6750-9) |
| FHIR | `Condition` (Condition-uv-ips), no category, no verificationStatus; section 11348-0 "History of Past illness note" |
| Membership | entries of the 11348-0 section; fallback Condition-uv-ips + past status — the legacy problem-list Conditions (`_j.cn`, active, no profile) never leak in |
| `_j` | `ph` (`c d d_display dt cs st` + new `ab` abatement, `sv` severity) |
| Terminology | KB `problems-snomed-ct-ips-free-set` (5 622 SNOMED, FR/JA… translations) via `KbConditionPicker`; `Coding.display` = `ips_valuesets.display_en` (`getIpsDisplayEn`), localised labels at render time; `searchIpsProblems` now serves English from `display_en` |
| UI | `ui/profile/pastproblems/*` — dates "exact" or "year only" |
| Seeds | Kurodo appendicitis + pneumonia · Haru myocardial infarction + tuberculosis · Kamekichi none |
| QA | `verify_profiles.py` 📜 `ph` (P4–P6, P6d ab/sv), README T15 |

### Checklist for the next pillar (Functional status, Pregnancy, Vital signs…)

1. Tests first: FHIR round trip (full / minimal / edge dates / status
   normalisation), Bundle embedding + section wiring, projection contract.
2. Domain class + `IpsNativePillars` field; `IpsFhirCodec.nativeOf()`.
3. `JemmaFhirBundleBuilder.build()`: entries + `Composition.section`
   (Functional status `47420-5`, Pregnancy `10162-6`, Vital signs `8716-3`).
4. `ProfilesRepository`: `load<Pillar>` / `save<Pillar>`; projection in
   `writeProfileFiles`; fallback in `readNativePillars`.
5. UI + wiring + strings (values, en, fr, ja, de, nl, zh-rCN).
6. Text QR section, Gemma tool, personas.

## Known limits / next steps

- Pocket Pass PDF does not print immunizations / procedures / devices yet (≈55 pt free in column 1).
- Mesh SOS chunks carry `im[].c` only; `guessSystem` tags all-digit codes as
  SNOMED (CVX codes would be mis-tagged — we default to SNOMED products).
- Legacy pillars (patient, allergies, medications, conditions) are still
  `_j`-authored; each will flip to FHIR-native in its own sprint.
- CI: `.github/workflows/android-ci.yml` builds + runs the JVM tests on every
  push and publishes `<branch-slug>/latest.md` on the `ci-logs` branch.
