## T4 allergy edit keeps immunizations

| Profile | Check | Status | Detail |
|---|---|---|---|
| `demo_kurodo` | P1 files present + parse | ✅ |  |
| `demo_kurodo` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kurodo` | P2 document Bundle · Composition first · 1 Patient | ✅ | 9 entries |
| `demo_kurodo` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_kurodo` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 4 immunizations |
| `demo_kurodo` | P4b expected 4 immunizations | ✅ |  |
| `demo_kurodo` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Immunization-uv-ips profile · patient ref · occurrence · status | ✅ |  |
| `demo_kurodo` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |

**PASS** — 9 checks, 0 failed
