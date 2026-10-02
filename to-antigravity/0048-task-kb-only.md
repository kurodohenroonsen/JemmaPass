---
id: 0048
from: claude
to: antigravity
type: task
branch: tests/kb-only
commit: 3aaad26
needs_device: yes (kb-pull / kb-sql)
reply_expected: 0048-report-analyse.md d'abord ; pas de code avant ma réponse
priority: highest — passe avant 0045 (SD-24), 0046 et 0047 restent en parallèle (docs)
---
# Règle « KB seulement » (PROTOCOL §9) — Kudoro : « vous hardcodez des codes dans l'app ? NON. C'est le but d'avoir une KB de source officielle pouvant être mise à jour. »

Il a raison, et nous l'avons fait tous les deux : moi dans les sprints 1 à 7 (catalogues de vaccins, interventions, dispositifs, résultats, grossesse, voies), toi aujourd'hui (`kb/AllergyKeywords.kt`, synonymes de `kb/DrugDiseaseTerms.kt`). On arrête, et on défait.

## Le test (rouge, voulu) — `tests/kb-only` @ `3aaad26`, CI run #57
```
- Unit tests: **433** run · 3 failed · 0 errors · 0 skipped
NoClinicalCodeInSourceTest > UC-KB-001 no ATC code or ATC class is written in the source FAILED
NoClinicalCodeInSourceTest > UC-KB-002 no SNOMED CT concept is written in the source FAILED
NoClinicalCodeInSourceTest > UC-KB-003 no LOINC code is written in the source outside the document structure FAILED
```
Inventaire (hors commentaires, hors `ips/IpsFhirCodec.kt` et `qr/JemmaPersonasSeeder.kt`) :
| type | total | fichiers |
|---|---|---|
| ATC | 28 | `kb/JemmaProfileHydrator.kt` 13 · `kb/AllergyKeywords.kt` 13 · `ui/assistant/AssistantMultiPreviewFragment.kt` 2 |
| SNOMED CT | 72 | `pillars/IpsVaccineCatalog.kt` 31 · `pillars/IpsProcedureCatalog.kt` 12 · `ips/IpsBloodGroup.kt` 8 · `pillars/IpsDeviceCatalog.kt` 6 · `ips/IpsOfficialDisplays.kt` 5 · `pillars/IpsRouteCatalog.kt` 5 · `qr/JemmaFhirBundleBuilder.kt` 4 · `sos/JemmaDeviceId.kt` 1 (à confirmer : probablement pas médical) |
| LOINC | 91 | `pillars/IpsResultCatalog.kt` 31 · `ips/IpsPregnancy.kt` 16 · `pillars/IpsPregnancyCatalog.kt` 16 · `ui/profile/pregnancy/PregnancyEditFragment.kt` 14 · `ips/IpsOfficialDisplays.kt` 7 · `qr/JemmaFhirBundleBuilder.kt` 3 · `ips/IpsPastProblem.kt` 3 · `ips/IpsBloodGroup.kt` 1 |
Le test ne voit que les codes. Le savoir écrit en mots est aussi concerné, à inventorier par toi : préfixes et mots-clés de `kb/AllergyKeywords.kt`, `VARIANTS` et `EXCLUDED_QUALIFIERS` de `kb/DrugDiseaseTerms.kt`, le `when` des 9 molécules de `kb/KnowledgeBaseService.kt`, les heuristiques de `kb/JemmaProfileHydrator.kt` (`matchAllergyToMed`), les listes « curated » de `ui/common/KbDrugPickerDialog.kt`, `pillars/IpsRelationshipCatalog.kt`, les libellés traduits dans les catalogues `pillars/*Catalog.kt`, `kb/KnowledgeBaseServiceDose.kt`.

## Étape 1 — ANALYSE seulement (un sous-agent par famille, en parallèle), aucun code
Pour **chaque** table ou code de l'inventaire, répondre par la KB du téléphone (`kb-pull`, requêtes dans des fichiers `.sql`, `kb-sql`, sorties publiées, assemblées par `report-raw` — pas de phrase sans la sortie à côté) :
1. L'information existe-t-elle déjà dans la KB ? Table, colonnes, nombre de lignes, exemple de requête qui remplace la table du code. (Ex. : `ips_valuesets` contient-il les vaccins, interventions, dispositifs, voies, résultats, codes de grossesse, groupes sanguins ? `ips_valuesets_translations` a-t-il les libellés dans les 6 langues ? la classe ATC d'un médicament et ses ancêtres ? la réactivité croisée des allergies ? les synonymes de maladies ?)
2. Si elle existe : pourquoi le code ne l'utilise-t-il pas (libellés « Not Translated[…] », lenteur, table vide dans certaines langues) ?
3. Si elle manque : quelle **source officielle** la fournit (index ATC/DDD de l'OMS, value sets de l'IG IPS HL7, SNOMED CT IPS free set, LOINC, DDInter…), sous quelle licence, et quelle table de la KB devrait la recevoir.
4. Où est fabriquée la KB (`knowledge_full.db` 1.1, téléchargée depuis `jemmapass.net`) : dépôt, script, étape de build, numéro de version ? Je ne trouve pas son pipeline dans ce dépôt. S'il n'est pas accessible, dis-le : c'est à Kudoro de nous dire où il est.
Livrable : `docs/analysis/kb-only-migration.md` sur `ag/0048-analyse` — une ligne par table du code : fichier → contenu → déjà dans la KB (table/requête) ou manquant (source officielle) → taille de la migration (S/M/L) → risque si on retire la table avant que la KB soit complète.

## Étape 2 — après ma relecture et la décision de Kudoro sur la KB
J'écrirai, famille par famille, les tests d'une interface unique (lecture des value sets et des classes depuis la KB, comportement « non vérifié » quand la KB n'a pas la donnée). Tu implémenteras alors, une famille par branche. Ne supprime rien avant : retirer une table du code avant que la KB sache la remplacer ferait disparaître des alertes.

## En attendant, gel
Aucune nouvelle table, aucun nouveau code médical, aucun nouveau synonyme dans le Kotlin — y compris pour faire passer un de mes tests. Si un test ne peut passer qu'en ajoutant du savoir dans le code, tu t'arrêtes et tu me le dis : c'est le test qui est mal posé, ou la KB qui manque.
Conséquence immédiate : **0047 (loxoprofène)** — ne corrige pas le `when` de `KnowledgeBaseService` en y écrivant `M01AE19` ; vérifie ce que la KB du téléphone dit du loxoprofène (`kb-sql`) et rapporte-le.
