## Seed verification (demo personas)

| Profile | Check | Status | Detail |
|---|---|---|---|
| `demo_haru` | P1 files present + parse | ✅ |  |
| `demo_haru` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_haru` | P2 document Bundle · Composition first · 1 Patient | ✅ | 12 entries |
| `demo_haru` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_haru` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 3 immunizations |
| `demo_haru` | P4b expected 3 immunizations | ✅ |  |
| `demo_haru` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 3 refs |
| `demo_haru` | P6 Immunization-uv-ips profile · patient ref · occurrence · status | ✅ |  |
| `demo_haru` | P8 legacy section Allergies (48765-2) present | ✅ | 1 entries |
| `demo_haru` | P8 legacy section Medications (10160-0) present | ✅ | 3 entries |
| `demo_kamekichi` | P1 files present + parse | ✅ |  |
| `demo_kamekichi` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kamekichi` | P2 document Bundle · Composition first · 1 Patient | ✅ | 15 entries |
| `demo_kamekichi` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_kamekichi` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 0 immunizations |
| `demo_kamekichi` | P4b expected 0 immunizations | ✅ |  |
| `demo_kamekichi` | P5 no 11369-6 section when there are no immunizations | ✅ |  |
| `demo_kamekichi` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |
| `demo_kamekichi` | P8 legacy section Medications (10160-0) present | ✅ | 5 entries |
| `demo_kurodo` | P1 files present + parse | ✅ |  |
| `demo_kurodo` | P7 `_j`=1.2, sid=file id | ✅ |  |
| `demo_kurodo` | P2 document Bundle · Composition first · 1 Patient | ✅ | 9 entries |
| `demo_kurodo` | P3 deterministic urn:uuid (v3) on every entry | ✅ |  |
| `demo_kurodo` | P4 `_j.im` projection ⇄ Immunization resources | ✅ | 4 immunizations |
| `demo_kurodo` | P4b expected 4 immunizations | ✅ |  |
| `demo_kurodo` | P5 Composition section 11369-6 → Immunization fullUrls | ✅ | 4 refs |
| `demo_kurodo` | P6 Immunization-uv-ips profile · patient ref · occurrence · status | ✅ |  |
| `demo_kurodo` | P8 legacy section Allergies (48765-2) present | ✅ | 3 entries |

**PASS** — 28 checks, 0 failed
