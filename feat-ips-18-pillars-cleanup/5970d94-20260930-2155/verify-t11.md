## T11 device created

| Profile | Check | Status | Detail |
|---|---|---|---|
| `demo_haru` | P1 files present + parse | ✅ |  |
| `demo_haru` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_haru` | P2 document Bundle · Composition first · 1 Patient | ✅ | 18 entries |
| `demo_haru` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_haru` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 3 im |
| `demo_haru` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 3 refs |
| `demo_haru` | P6 Immunization-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_haru` | P4 `_j.pr` projection ⇄ Procedure resources | ✅ | 2 pr |
| `demo_haru` | P5 Composition section 47519-4 → Procedure fullUrls | ✅ | 2 refs |
| `demo_haru` | P6 Procedure-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_haru` | P4 `_j.dv` projection ⇄ DeviceUseStatement resources | ✅ | 2 dv |
| `demo_haru` | P4b expected 2 DeviceUseStatement | ✅ |  |
| `demo_haru` | P5 Composition section 46264-8 → DeviceUseStatement fullUrls | ✅ | 2 refs |
| `demo_haru` | P6 DeviceUseStatement-uv-ips profile · patient ref · status · date/string · Device resolved + Device-uv-ips | ✅ |  |
| `demo_haru` | P8 legacy section Allergies (48765-2) present | ✅ | 1 entries |
| `demo_haru` | P8 legacy section Medications (10160-0) present | ✅ | 3 entries |
| `demo_kurodo` | P1 files present + parse | ✅ |  |
| `demo_kurodo` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kurodo` | P2 document Bundle · Composition first · 1 Patient | ✅ | 13 entries |
| `demo_kurodo` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_kurodo` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 4 im |
| `demo_kurodo` | P4b expected 4 Immunization | ✅ |  |
| `demo_kurodo` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Immunization-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_kurodo` | P4 `_j.pr` projection ⇄ Procedure resources | ✅ | 2 pr |
| `demo_kurodo` | P4b expected 2 Procedure | ✅ |  |
| `demo_kurodo` | P5 Composition section 47519-4 → Procedure fullUrls | ✅ | 2 refs |
| `demo_kurodo` | P6 Procedure-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_kurodo` | P4 `_j.dv` projection ⇄ DeviceUseStatement resources | ✅ | 1 dv |
| `demo_kurodo` | P4b expected 1 DeviceUseStatement | ✅ |  |
| `demo_kurodo` | P5 Composition section 46264-8 → DeviceUseStatement fullUrls | ✅ | 1 refs |
| `demo_kurodo` | P6 DeviceUseStatement-uv-ips profile · patient ref · status · date/string · Device resolved + Device-uv-ips | ✅ |  |
| `demo_kurodo` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |

**PASS** — 33 checks, 0 failed
