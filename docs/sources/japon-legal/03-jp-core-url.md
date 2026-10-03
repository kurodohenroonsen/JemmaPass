# Source Preuve : URL Officielle JP Core FHIR

- **Affirmation du document** : « Profil National Japon JP Core : https://j-core.org/ » (spec §6)
- **URL Testée** : https://j-core.org/
- **URL Officielle Réelle** : https://jpfhir.jp/ (NeXEHRS / HL7 Japan)
- **Fichier brut** : `docs/sources/raw/legal-03-jp-core.html`
- **Date de consultation** : 2026-10-02

## Constat & Commande curl
```bash
$ curl -sI https://j-core.org/
# Le domaine j-core.org n'est pas le site officiel du profil HL7 FHIR JP Core.
# L'organisation officielle est NeXEHRS (Next Generation Electronic Health Record System) sur https://jpfhir.jp/
```

## Verdict
`CONTREDIT (l'URL officielle de JP Core FHIR est https://jpfhir.jp/ maintenue par NeXEHRS / HL7 Japan, pas j-core.org)`
