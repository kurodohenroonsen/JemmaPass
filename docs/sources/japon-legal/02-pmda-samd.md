# Source Preuve : Réglementation Logiciel Dispositif Médical (PMDA SaMD)

- **Affirmation du document** : « Réglementation SaMD PMDA (PMD Act / 薬機法) : une application émettant des alertes d'interactions cliniques directes relève du SaMD » (spec §6)
- **URL Source** : https://www.pmda.go.jp/english/review-services/regulatory-info/0002.html
- **Fichier brut** : `docs/sources/raw/legal-02-pmda-samd.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -i -E "SaMD|Medical Device|PMD Act" docs/sources/raw/legal-02-pmda-samd.html | head -n 2
PMDA SaMD (Software as a Medical Device) under the PMD Act: clinical decision support and alert software classification.
```

## Verdict
`CONFIRMÉ`
