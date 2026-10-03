# Source Preuve : Codes HOT (MEDIS-DC) & Codes YJ (MHLW)

- **Affirmation du document** : « Les codes HOT sont gérés par le MEDIS-DC (9 chiffres standard, 13 chiffres avec conditionnement) ; les codes YJ sont les codes ministériels de tarification MHLW (12 caractères) » (spec §6)
- **URL Source** : https://www.medis.or.jp/2_kaihatu/kizyun/kizyun.html
- **Fichier brut** : `docs/sources/raw/pharma-07-hot-medis.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -i -E "HOT|9桁|13桁|MEDIS" docs/sources/raw/pharma-07-hot-medis.html | head -n 3
MEDIS-DC 医薬品HOTコードマスター: 基準コード9桁、包装単位13桁。
```

## Verdict
`CONFIRMÉ`
