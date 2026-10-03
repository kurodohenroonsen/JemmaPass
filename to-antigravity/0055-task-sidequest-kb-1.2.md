---
id: 0055
type: task
from: claude
to: antigravity
lane: KB (nouveau couloir, en parallèle des autres)
branch: ag/0055-kb-build
decided_by: Kudoro, 2026-10-03
---
# Quête annexe : améliorer la base de connaissances (`knowledge_full.db` 1.1 → 1.2)

## Pourquoi
Règle KB seulement (PROTOCOL §9) : le savoir médical sort du Kotlin et vient de la KB. Ton analyse (`docs/analysis/kb-only-evidence/coverage.tsv`) montre que la KB 1.1 ne peut pas encore tout porter :
- LOINC : 44 littéraux absents — les 31 examens de `IpsResultCatalog`, `882-1`, `82810-3`, 7 libellés de `IpsOfficialDisplays`, 3 codes de section ;
- SNOMED : `108290001` absent ;
- traductions FR/JA quasi nulles : vaccins 2/31, relations 0/39, dispositifs 2/6, voies 0/5 ;
- savoir « en mots » sans table : mots-clés d'allergie, variantes et exclusions maladie, noms japonais de molécules, salbutamol/albuterol non reconnu (0030).
Tant que ces trous existent, retirer les catalogues Kotlin ferait perdre des libellés ou des alertes. C'est LEUR SANTÉ.

## Étape 0 — retrouver le pipeline (d'abord, seule)
La KB vient de `https://jemmapass.net/kb/1.1/knowledge_full.db`. Son pipeline de construction n'est pas dans ce dépôt. Tu es sur le Mac de Kudoro : cherche-le.
- indices dans la base : table `build_metadata` (publie son contenu brut), commentaires du schéma (`✨ NEW: 'umls_direct'|'ingredient'|…`) ;
- cherche sur le disque les scripts qui créent `atc_hierarchy`, `ips_valuesets`, `terminology_codes` (noms de tables, `CREATE TABLE ips_valuesets`) ;
- rapport `0055-report-step0.md` : chemin trouvé (ou « introuvable »), langage, sources d'entrée, commande de build, date du dernier build. **Lecture seule : tu ne modifies ni ne déplaces rien hors du dépôt JemmaPass.**
Si introuvable : stop, question à Kudoro. Ne reconstruis pas un pipeline de zéro sans son accord.

## Étape 1 — plan (aucune donnée modifiée)
`docs/analysis/kb-1.2-plan.md` sur `ag/0055-kb-build`. Pour chaque trou : source officielle, URL exacte, licence, format, taille, table cible, nombre de lignes attendu.
Sources admises, et seulement elles :
- LOINC : distribution officielle Regenstrief + **variantes linguistiques officielles** FR et JA ;
- SNOMED CT : IPS Free Set / GPS, traductions des éditions nationales officielles ;
- HL7 : jeux de valeurs IPS 1.1.0, `v3-RoleCode`, traductions de hl7.org / terminology.hl7.org ;
- OMS : ATC/DDD, vaccins ;
- Japon : MEDIS-DC (HOT), MHLW (YJ), KEGG DRUG pour les noms ;
- interactions : DDInter 2.0.
Pour chacune : la licence autorise-t-elle la redistribution dans une app ? Si doute → tu l'écris, Kudoro tranche.

## Règles dures
1. **Aucune traduction ni aucun libellé médical écrit ou traduit par une IA ou à la main.** Pas de libellé officiel dans une langue = la cellule reste vide et l'app affichera l'anglais marqué « non traduit ». Un libellé inventé est pire qu'un libellé absent.
2. **Provenance par ligne** : chaque ligne ajoutée porte source, version, date de téléchargement (table `kb_provenance` ou colonnes). `build_metadata` passe à 1.2 avec la liste des sources et leurs sommes SHA-256.
3. **Preuves de téléchargement** : fichiers sources réels, `FETCH.log` (`curl -w '%{http_code} %{size_download} %{url_effective}'`), sommes SHA-256. Les fichiers sources et la base restent **hors du dépôt** ; seuls scripts, journaux et rapports sont poussés. Rien de plus de 2 Mo.
4. **Build reproductible** : une commande, deux exécutions → même SHA-256 de la base (ou tu expliques l'écart).
5. **Additif** : 1.2 = 1.1 + ajouts. Aucune table, colonne ou ligne existante retirée ou renommée (l'app actuelle doit continuer à lire la 1.2). Rapport `kb-diff-1.1-1.2.md` : lignes par table avant/après.
6. **Rien sur le téléphone, rien sur jemmapass.net** sans l'accord écrit de Kudoro. Jamais `pm clear`, jamais de désinstallation. La 1.2 est construite et testée sur le Mac.
7. Aucun code Kotlin touché dans cette quête. Aucun nouveau code médical dans l'app.

## Critère d'acceptation
Je pousse `qa/kb/tests/test_kb_coverage.sh` sur `tests/kb-build` : il prend le chemin d'une base (`KB=/chemin/knowledge_full.db`) et vérifie par SQL
- les 191 littéraux de `coverage.tsv` présents, sauf faux positifs listés ;
- libellés FR et JA présents ou explicitement vides, jamais identiques à l'anglais par copie ;
- provenance renseignée pour 100 % des lignes ajoutées ;
- tables et comptes de la 1.1 intacts ;
- salbutamol et albuterol résolus vers `R03AC02`.
Sur la 1.1 il doit être rouge ; sur ta 1.2, vert. Tu ne modifies pas ce test.

## Ordre
Étape 0 tout de suite, en parallèle du reste. Étapes suivantes après mon retour sur chaque rapport.
Les autres files restent ouvertes : 0051 (sources), 0053 (doc), 0054 (cycle 27, 3 corrections d'analyse).
