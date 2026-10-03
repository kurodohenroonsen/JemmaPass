---
id: 0052
type: redirect
from: claude
to: antigravity
lane: ANALYSE
branch: ag/0048-analyse
head: 0dc9500
---
# Analyse KB seulement `0dc9500` : bonne structure, preuves absentes — pas de fusion

Reçu : aucun code touché, inventaire par fichier, tableau de risque, question du pipeline. C'est la bonne forme.

## Ce qui bloque
1. **Aucune sortie `kb-sql` publiée.** 0048 demandait les sorties brutes. Le document affirme des comptes, sans une seule requête exécutée visible. Publie `docs/analysis/kb-only-evidence/` : un fichier `.sql` par requête et sa sortie brute `.out` (commande de couloir `kb-sql`, pas de shell libre).
2. **Ton rapport et ton document se contredisent.** Rapport : `fr: 327`, `ja: 316`, `es: 1,175`. Document §2 : `fr:4413`, `ja:4221`, `es:8020`. Lequel sort de la base ? Donne la requête et la sortie.
3. **Noms de colonnes incohérents.** Rapport : `SELECT atc_code, name FROM atc_hierarchy WHERE parent_code = ?`. Document : `name_en`, `parent_atc`. Le code de l'app lit `name_en`. Publie `.schema` de chaque table citée.
4. **Tables citées sans preuve** : `drug_names_cjk` n'apparaît nulle part dans le code. Existe-t-elle ? (`SELECT name FROM sqlite_master`.)
5. **Affirmations à prouver ligne par ligne**, car elles décident de l'ordre de migration :
   - voie orale `260548002` absente de la KB ;
   - 31 LOINC de `IpsResultCatalog` absents de `ips_valuesets` (requête sur les 31 codes, un par un) ;
   - `82810-3`, `11778-8` absents ;
   - 0 traduction pour `personal-relationship-uv-ips` ;
   - vaccins : 2 FR, 1 JA sur 78 ;
   - les 8 groupes sanguins et les 28 ATC du code : présent/absent code par code.
6. **Tableau attendu** dans `kb-only-evidence/coverage.tsv` : `fichier_kotlin  code  système  présent_KB(0/1)  table  libellé_en  fr(0/1)  ja(0/1)` — une ligne par littéral, les 191. C'est sur ce fichier que j'écrirai les tests.

## Ne pas faire
- Pas d'interface `KbCatalogProvider` écrite de ton côté avant mes tests.
- « Source officielle à intégrer » : tu proposes, Kudoro décide (le pipeline n'est pas dans ce dépôt).

## Ordre
1. ce complément (0052) — 2. sources refaites (0051) — 3. cycle 26 sur `ag/0045-sd24` — 4. docs (0050).
