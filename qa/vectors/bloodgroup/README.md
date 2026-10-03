# Test vectors — blood group (`p.bt` → ABO/Rh result, LOINC 882-1)

One set of language-neutral cases that every implementation of JemmaPass must pass (Android, iOS,
Chrome, USB). The Android app replays them (`ips/BloodGroupVectorsTest.kt`). A vector is never
adapted to an implementation: fix the app.

Origin: the expected resource is the one the Android app wrote on the test device for the three demo
personas (device cycle 27, `files/demo_*.fhir.json`), with the eight values of
`ips/IpsBloodGroup.kt`. Nothing here was written from memory.

| Key | Meaning |
|---|---|
| `input_j` | A complete `_j 1.2` profile; the blood group is `p.bt` |
| `expect.blood_group_observations` | Every `Observation` of the Bundle whose `code` holds LOINC `882-1`, in Bundle order. `[]` means there is none |

Comparison: structural (same keys, same values, key order ignored, array order significant), with
two rules: the `id` of the resource is ignored, and a reference written `@patient` must be the
`fullUrl` of the Patient entry of the same Bundle.

A document must never hold two ABO/Rh results, and never one that disagrees with `p.bt`.
