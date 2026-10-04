# Test vectors — devices (`DeviceUseStatement` + `Device`)

Language-neutral cases for every implementation that **imports** an IPS document. The FHIR Bundle is
the source of truth; `_j.dv` is only a projection and does not carry the body site, the manufacturer
or the UDI. An import that rebuilds the document from the projection loses them.

Origin: the resources are those the Android app wrote on the test device for `demo_haru`
(device cycle 27, `files/demo_haru.fhir.json` and `files/demo_haru.json`). Nothing was written from memory.

| Key | Meaning |
|---|---|
| `input_fhir.device`, `input_fhir.device_use_statement` | The two resources as found in a Bundle. `@patient` stands for the fullUrl of the Patient entry, `@device` for the fullUrl of the Device entry |
| `expect.reexported` | The same two resources after import then export: nothing lost, nothing added |
| `expect.j_dv` | The `_j.dv` entry of the projection (`c` code, `d` note, `d_display` label, `dt` date). A missing key means the key is absent |

Comparison: structural, `id` ignored, `@patient` / `@device` resolved as above.

Status: replayed by Chrome, iOS and USB. The Android replay test is still to be written; the
expected values are Android's own output.
