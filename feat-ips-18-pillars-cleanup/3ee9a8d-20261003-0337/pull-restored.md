## Seed restored

| Profile | Check | Status | Detail |
|---|---|---|---|
| `demo_kurodo` | P1 files present + parse | ✅ |  |
| `demo_kurodo` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kurodo` | P2 document Bundle · Composition first · 1 Patient | ✅ | 18 entries |
| `demo_kurodo` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_kurodo` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 4 im |
| `demo_kurodo` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Immunization-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_kurodo` | P4 `_j.pr` projection ⇄ Procedure resources | ✅ | 2 pr |
| `demo_kurodo` | P5 Composition section 47519-4 → Procedure fullUrls | ✅ | 2 refs |
| `demo_kurodo` | P6 Procedure-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_kurodo` | P4 `_j.dv` projection ⇄ DeviceUseStatement resources | ✅ | 0 dv |
| `demo_kurodo` | P5 no 46264-8 section when there are no DeviceUseStatement | ✅ |  |
| `demo_kurodo` | P4 `_j.rs` projection ⇄ Observation resources | ✅ | 4 rs |
| `demo_kurodo` | P4b expected 4 Observation | ✅ |  |
| `demo_kurodo` | P5 Composition section 30954-2 → Observation fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Observation-uv-ips profile · patient ref · status · date/string · category · value[x] | ✅ |  |
| `demo_kurodo` | P6c `_j.rs` values ⇄ Observation value[x] | ✅ | 4 values |
| `demo_kurodo` | P4 `_j.pg` projection ⇄ Observation (pregnancy) resources | ✅ | 0 pg |
| `demo_kurodo` | P5 no 10162-6 section when there are no Observation (pregnancy) | ✅ |  |
| `demo_kurodo` | P4 `_j.cn` projection ⇄ Condition (problem list) resources | ✅ | 1 cn |
| `demo_kurodo` | P5 Composition section 11450-4 → Condition (problem list) fullUrls | ✅ | 1 refs |
| `demo_kurodo` | P6 Condition (problem list)-uv-ips profile · patient ref · status · date/string · current clinicalStatus · no abatement | ✅ |  |
| `demo_kurodo` | P6e `_j.cn` st ⇄ Condition clinicalStatus | ✅ | 1 problems |
| `demo_kurodo` | P4 `_j.ph` projection ⇄ Condition resources | ✅ | 2 ph |
| `demo_kurodo` | P5 Composition section 11348-0 → Condition fullUrls | ✅ | 2 refs |
| `demo_kurodo` | P6 Condition-uv-ips profile · patient ref · status · date/string · past clinicalStatus · onset ≤ abatement | ✅ |  |
| `demo_kurodo` | P6d `_j.ph` ab/sv ⇄ Condition abatement/severity | ✅ | 2 problems |
| `demo_kurodo` | P4 `_j.fs` projection ⇄ Condition (functional status) resources | ✅ | 0 fs |
| `demo_kurodo` | P5 no 47420-5 section when there are no Condition (functional status) | ✅ |  |
| `demo_kurodo` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |

**PASS** — 30 checks, 0 failed
