## T15.3 Free-text created

| Profile | Check | Status | Detail |
|---|---|---|---|
| `demo_kurodo` | P1 files present + parse | ✅ |  |
| `demo_kurodo` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kurodo` | P2 document Bundle · Composition first · 1 Patient | ✅ | 19 entries |
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
| `demo_kurodo` | P5 Composition section 30954-2 → Observation fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Observation-uv-ips profile · patient ref · status · date/string · category · value[x] | ✅ |  |
| `demo_kurodo` | P6c `_j.rs` values ⇄ Observation value[x] | ✅ | 4 values |
| `demo_kurodo` | P4 `_j.ph` projection ⇄ Condition resources | ✅ | 4 ph |
| `demo_kurodo` | P4b expected 4 Condition | ✅ |  |
| `demo_kurodo` | P5 Composition section 11348-0 → Condition fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Condition-uv-ips profile · patient ref · status · date/string · past clinicalStatus · onset ≤ abatement | ✅ |  |
| `demo_kurodo` | P6d `_j.ph` ab/sv ⇄ Condition abatement/severity | ✅ | 4 problems |
| `demo_kurodo` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |

**PASS** — 22 checks, 0 failed
