## Haru restored after contact delete

| Profile | Check | Status | Detail |
|---|---|---|---|
| `demo_haru` | P1 files present + parse | ✅ |  |
| `demo_haru` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_haru` | P2 document Bundle · Composition first · 1 Patient | ✅ | 32 entries |
| `demo_haru` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_haru` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 3 im |
| `demo_haru` | P4b expected 3 Immunization | ✅ |  |
| `demo_haru` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 3 refs |
| `demo_haru` | P6 Immunization-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_haru` | P4 `_j.pr` projection ⇄ Procedure resources | ✅ | 2 pr |
| `demo_haru` | P4b expected 2 Procedure | ✅ |  |
| `demo_haru` | P5 Composition section 47519-4 → Procedure fullUrls | ✅ | 2 refs |
| `demo_haru` | P6 Procedure-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_haru` | P4 `_j.dv` projection ⇄ DeviceUseStatement resources | ✅ | 2 dv |
| `demo_haru` | P4b expected 2 DeviceUseStatement | ✅ |  |
| `demo_haru` | P5 Composition section 46264-8 → DeviceUseStatement fullUrls | ✅ | 2 refs |
| `demo_haru` | P6 DeviceUseStatement-uv-ips profile · patient ref · status · date/string · Device resolved + Device-uv-ips | ✅ |  |
| `demo_haru` | P4 `_j.rs` projection ⇄ Observation resources | ✅ | 5 rs |
| `demo_haru` | P4b expected 5 Observation | ✅ |  |
| `demo_haru` | P5 Composition section 30954-2 → Observation fullUrls | ✅ | 5 refs |
| `demo_haru` | P6 Observation-uv-ips profile · patient ref · status · date/string · category · value[x] | ✅ |  |
| `demo_haru` | P6c `_j.rs` values ⇄ Observation value[x] | ✅ | 5 values |
| `demo_haru` | P4 `_j.pg` projection ⇄ Observation (pregnancy) resources | ✅ | 3 pg |
| `demo_haru` | P4b expected 3 Observation (pregnancy) | ✅ |  |
| `demo_haru` | P5 Composition section 10162-6 → Observation (pregnancy) fullUrls | ✅ | 3 refs |
| `demo_haru` | P6 Observation (pregnancy)-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_haru` | P4 `_j.cn` projection ⇄ Condition (problem list) resources | ✅ | 2 cn |
| `demo_haru` | P4b expected 2 Condition (problem list) | ✅ |  |
| `demo_haru` | P5 Composition section 11450-4 → Condition (problem list) fullUrls | ✅ | 2 refs |
| `demo_haru` | P6 Condition (problem list)-uv-ips profile · patient ref · status · date/string · current clinicalStatus · no abatement | ✅ |  |
| `demo_haru` | P6e `_j.cn` st ⇄ Condition clinicalStatus | ✅ | 2 problems |
| `demo_haru` | P4 `_j.ph` projection ⇄ Condition resources | ✅ | 2 ph |
| `demo_haru` | P4b expected 2 Condition | ✅ |  |
| `demo_haru` | P5 Composition section 11348-0 → Condition fullUrls | ✅ | 2 refs |
| `demo_haru` | P6 Condition-uv-ips profile · patient ref · status · date/string · past clinicalStatus · onset ≤ abatement | ✅ |  |
| `demo_haru` | P6d `_j.ph` ab/sv ⇄ Condition abatement/severity | ✅ | 2 problems |
| `demo_haru` | P4 `_j.fs` projection ⇄ Condition (functional status) resources | ✅ | 2 fs |
| `demo_haru` | P4b expected 2 Condition (functional status) | ✅ |  |
| `demo_haru` | P5 Composition section 47420-5 → Condition (functional status) fullUrls | ✅ | 2 refs |
| `demo_haru` | P6 Condition (functional status)-uv-ips profile · patient ref · status · date/string · clinicalStatus active/inactive/resolved | ✅ |  |
| `demo_haru` | P6f `_j.fs` st ⇄ Condition clinicalStatus | ✅ | 2 entries |
| `demo_haru` | P8 legacy section Allergies (48765-2) present | ✅ | 1 entries |
| `demo_haru` | P8 legacy section Medications (10160-0) present | ✅ | 3 entries |

**PASS** — 42 checks, 0 failed
