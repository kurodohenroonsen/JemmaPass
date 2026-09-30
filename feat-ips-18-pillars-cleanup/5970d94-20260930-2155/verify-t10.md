## T10 procedure created

| Profile | Check | Status | Detail |
|---|---|---|---|
| `demo_kurodo` | P1 files present + parse | ✅ |  |
| `demo_kurodo` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kurodo` | P2 document Bundle · Composition first · 1 Patient | ✅ | 12 entries |
| `demo_kurodo` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_kurodo` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 4 im |
| `demo_kurodo` | P4b expected 4 Immunization | ✅ |  |
| `demo_kurodo` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Immunization-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_kurodo` | P4 `_j.pr` projection ⇄ Procedure resources | ✅ | 3 pr |
| `demo_kurodo` | P4b expected 3 Procedure | ✅ |  |
| `demo_kurodo` | P5 Composition section 47519-4 → Procedure fullUrls | ✅ | 3 refs |
| `demo_kurodo` | P6 Procedure-uv-ips profile · patient ref · status · date/string | ✅ |  |
| `demo_kurodo` | P4 `_j.dv` projection ⇄ DeviceUseStatement resources | ✅ | 0 dv |
| `demo_kurodo` | P5 no 46264-8 section when there are no DeviceUseStatement | ✅ |  |
| `demo_kurodo` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |

**PASS** — 15 checks, 0 failed
