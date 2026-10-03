# Test vectors — emergency contacts (`p.ct` → `Patient.contact`)

Purpose: one set of language-neutral cases that every implementation of JemmaPass must pass.
The Android app replays them today (`pillar8/ContactsVectorsTest.kt`); the future iOS app will
replay the same files. A vector is never adapted to an implementation: fix the app.

All names, phone numbers, e-mail addresses and postal addresses are invented demo data.

## Format

One JSON file per case, UTF-8, named `<id>.json`.

| Key | Meaning |
|---|---|
| `id` | Stable identifier, equal to the file name without `.json` |
| `title` | What the case proves, one sentence |
| `ui_lang` | Language of the reader (ISO 639-1). Optional, default `en`. Used for the relationship text of the document and for the text QR |
| `input_j` | A complete `_j 1.2` profile; the contacts are in `p.ct[]` (`n` name, `r` relationship, `p` phone, `e` e-mail, `adr` address) |
| `expect.patient_contact` | The exact `Patient.contact` array of the FHIR R4 Bundle built from `input_j`. `[]` means the element is absent or empty |
| `expect.text_qr_contains` | Strings the text QR in `ui_lang` must contain, each one after the previous one |
| `expect.text_qr_absent` | Optional. Strings the text QR must not contain |

## Comparison rules

- `patient_contact` is compared structurally: same keys at every level (none missing, none extra),
  same values, key order ignored, array order significant.
- The relationship is a v3 RoleCode coding (`display` = official English label, `text` = label in
  `ui_lang`) when `r` is a code of the `personal-relationship-uv-ips` value set; otherwise `text` only.
- A blank or missing field produces no element. No empty string, array or object is ever expected.
- A phone number carries no `use`: the app does not ask which kind of line it is.

## Cases

| File | Case |
|---|---|
| `ct-001-nominal.json` | name, coded relationship, phone |
| `ct-002-two-contacts-order.json` | two contacts in list order; address in the document, not on the text QR |
| `ct-003-no-phone.json` | no phone, reached by e-mail |
| `ct-004-phone-only.json` | phone only, no name element |
| `ct-005-cjk-and-free-text.json` | Japanese names, Japanese reader, free-text relationship |
| `ct-006-blank-fields.json` | blank fields and an entirely blank contact |
