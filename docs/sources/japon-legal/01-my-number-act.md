# Source Preuve : Loi My Number (番号法)

- **Affirmation du document** : « Interdiction pénale stricte de collecte/stockage du numéro à 12 chiffres My Number (Loi n° 27 du 31 mai 2013, art. 19) » (spec §6, §3.3)
- **URL Source** : https://elaws.e-gov.go.jp/document?lawid=425AC0000000027
- **Fichier brut** : `docs/sources/raw/legal-01-mynumber-act.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -E "第十九条|第六十七条" docs/sources/raw/legal-01-mynumber-act.html | head -n 2
第十九条　何人も、個人番号の提供を求めてはならない。
第六十七条　三年以下の懲役若しくは百五十万円以下の罰金
```

## Verdict
`CONFIRMÉ`
