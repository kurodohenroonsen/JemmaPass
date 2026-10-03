# Source Preuve : Loxoprofène ATC Oral

- **Affirmation du document** : « Le loxoprofène n'a aucun code ATC OMS de niveau 5 oral » (spec §3.2, §6)
- **URL Source** : https://atcddd.fhi.no/atc_ddd_index/?code=M01AE19
- **Fichier brut** : `docs/sources/raw/pharma-01-loxoprofen-oral.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -i -C 1 "loxoprofen" docs/sources/raw/pharma-01-loxoprofen-oral.html
<tr><td><a href="./?code=M01AE19">M01AE19</a></td><td>loxoprofen</td></tr>
```

## Verdict
`CONTREDIT (l'index ATC/DDD de l'OMS attribue le code M01AE19 au loxoprofène oral/systémique)`
