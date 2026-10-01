## Seed verification (demo personas)

| Profile | Check | Status | Detail |
|---|---|---|---|
| `demo_haru` | P1 files present + parse | ✅ |  |
| `demo_haru` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_haru` | P2 document Bundle · Composition first · 1 Patient | ✅ | 27 entries |
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
| `demo_haru` | P8 legacy section Allergies (48765-2) present | ✅ | 1 entries |
| `demo_haru` | P8 legacy section Medications (10160-0) present | ✅ | 3 entries |
| `demo_kamekichi` | P1 files present + parse | ✅ |  |
| `demo_kamekichi` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kamekichi` | P2 document Bundle · Composition first · 1 Patient | ✅ | 19 entries |
| `demo_kamekichi` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_kamekichi` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 0 im |
| `demo_kamekichi` | P4b expected 0 Immunization | ✅ |  |
| `demo_kamekichi` | P5 no 11369-6 section when there are no Immunization | ✅ |  |
| `demo_kamekichi` | P4 `_j.pr` projection ⇄ Procedure resources | ✅ | 0 pr |
| `demo_kamekichi` | P4b expected 0 Procedure | ✅ |  |
| `demo_kamekichi` | P5 no 47519-4 section when there are no Procedure | ✅ |  |
| `demo_kamekichi` | P4 `_j.dv` projection ⇄ DeviceUseStatement resources | ✅ | 0 dv |
| `demo_kamekichi` | P4b expected 0 DeviceUseStatement | ✅ |  |
| `demo_kamekichi` | P5 no 46264-8 section when there are no DeviceUseStatement | ✅ |  |
| `demo_kamekichi` | P4 `_j.rs` projection ⇄ Observation resources | ✅ | 1 rs |
| `demo_kamekichi` | P4b expected 1 Observation | ✅ |  |
| `demo_kamekichi` | P5 Composition section 30954-2 → Observation fullUrls | ✅ | 1 refs |
| `demo_kamekichi` | P6 Observation-uv-ips profile · patient ref · status · date/string · category · value[x] | ✅ |  |
| `demo_kamekichi` | P6c `_j.rs` values ⇄ Observation value[x] | ✅ | 1 values |
| `demo_kamekichi` | P4 `_j.cn` projection ⇄ Condition (problem list) resources | ✅ | 3 cn |
| `demo_kamekichi` | P4b expected 3 Condition (problem list) | ✅ |  |
| `demo_kamekichi` | P5 Composition section 11450-4 → Condition (problem list) fullUrls | ✅ | 3 refs |
| `demo_kamekichi` | P6 Condition (problem list)-uv-ips profile · patient ref · status · date/string · current clinicalStatus · no abatement | ✅ |  |
| `demo_kamekichi` | P6e `_j.cn` st ⇄ Condition clinicalStatus | ✅ | 3 problems |
| `demo_kamekichi` | P4 `_j.ph` projection ⇄ Condition resources | ✅ | 0 ph |
| `demo_kamekichi` | P4b expected 0 Condition | ✅ |  |
| `demo_kamekichi` | P5 no 11348-0 section when there are no Condition | ✅ |  |
| `demo_kamekichi` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |
| `demo_kamekichi` | P8 legacy section Medications (10160-0) present | ✅ | 5 entries |
| `demo_kurodo` | P1 files present + parse | ✅ |  |
| `demo_kurodo` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kurodo` | P2 document Bundle · Composition first · 1 Patient | ✅ | 18 entries |
| `demo_kurodo` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_kurodo` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 4 im |
| `demo_kurodo` | P4b expected 4 Immunization | ✅ |  |
| `demo_kurodo` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Immunization-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_kurodo` | P4 `_j.pr` projection ⇄ Procedure resources | ✅ | 2 pr |
| `demo_kurodo` | P4b expected 2 Procedure | ✅ |  |
| `demo_kurodo` | P5 Composition section 47519-4 → Procedure fullUrls | ✅ | 2 refs |
| `demo_kurodo` | P6 Procedure-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_kurodo` | P4 `_j.dv` projection ⇄ DeviceUseStatement resources | ✅ | 0 dv |
| `demo_kurodo` | P4b expected 0 DeviceUseStatement | ✅ |  |
| `demo_kurodo` | P5 no 46264-8 section when there are no DeviceUseStatement | ✅ |  |
| `demo_kurodo` | P4 `_j.rs` projection ⇄ Observation resources | ✅ | 4 rs |
| `demo_kurodo` | P4b expected 4 Observation | ✅ |  |
| `demo_kurodo` | P5 Composition section 30954-2 → Observation fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Observation-uv-ips profile · patient ref · status · date/string · category · value[x] | ✅ |  |
| `demo_kurodo` | P6c `_j.rs` values ⇄ Observation value[x] | ✅ | 4 values |
| `demo_kurodo` | P4 `_j.cn` projection ⇄ Condition (problem list) resources | ✅ | 1 cn |
| `demo_kurodo` | P4b expected 1 Condition (problem list) | ✅ |  |
| `demo_kurodo` | P5 Composition section 11450-4 → Condition (problem list) fullUrls | ✅ | 1 refs |
| `demo_kurodo` | P6 Condition (problem list)-uv-ips profile · patient ref · status · date/string · current clinicalStatus · no abatement | ✅ |  |
| `demo_kurodo` | P6e `_j.cn` st ⇄ Condition clinicalStatus | ✅ | 1 problems |
| `demo_kurodo` | P4 `_j.ph` projection ⇄ Condition resources | ✅ | 2 ph |
| `demo_kurodo` | P4b expected 2 Condition | ✅ |  |
| `demo_kurodo` | P5 Composition section 11348-0 → Condition fullUrls | ✅ | 2 refs |
| `demo_kurodo` | P6 Condition-uv-ips profile · patient ref · status · date/string · past clinicalStatus · onset ≤ abatement | ✅ |  |
| `demo_kurodo` | P6d `_j.ph` ab/sv ⇄ Condition abatement/severity | ✅ | 2 problems |
| `demo_kurodo` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |

**PASS** — 92 checks, 0 failed
