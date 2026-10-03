# Analyse de Migration « KB Seulement » (Protocole §9)

Date : 2026-10-03  
Auteur : Antigravity (Sous-agent Spécialiste KB & Clinical Data)  
Branche : `ag/0048-analyse`  
Base examinée : `knowledge_full.db` v1.1 (`/tmp/jp/kb/knowledge_full.db`, 3.36 Go, SHA-256 vérifié)  
Preuves brutes : dossier [`docs/analysis/kb-only-evidence/`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence) (10 requêtes `.sql` et `.out` + `coverage.tsv`)

---

## 1. Contexte & Règle « KB Seulement »
Sur décision de Kudoro (Product Owner) :
> *« Vous hardcodez des codes dans l'app ? NON. C'est le but d'avoir une KB de source officielle pouvant être mise à jour sans recompiler l'application. »*

Les tests rouges de la branche `tests/kb-only` (`3aaad26`, CI run #57) et l'analyse exhaustive du code source (`NoClinicalCodeInSourceTest.kt`) démontrent la présence de **191 littéraux cliniques** dans le code Kotlin :
- **ATC** : 28 littéraux (13 dans `JemmaProfileHydrator.kt`, 13 dans `AllergyKeywords.kt`, 2 dans `AssistantMultiPreviewFragment.kt`).
- **SNOMED CT** : 72 littéraux (31 vaccins, 12 interventions, 8 groupes sanguins, 6 dispositifs, 5 voies, 4 bundle builder, etc.).
- **LOINC** : 91 littéraux (31 résultats bio, 16 grossesses, 16 statuts obstétricaux, 14 formulaires, etc.).

L'inventaire code par code, fichier par fichier, avec statut de présence dans la KB est consigné dans [`kb-only-evidence/coverage.tsv`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/coverage.tsv).

---

## 2. État Réel des Données dans la KB du Téléphone (`knowledge_full.db`)

Les requêtes SQL directes sur la KB locale (sorties brutes vérifiables dans `kb-only-evidence/`) révèlent la couverture suivante :

| Domaine Médical | Table KB | Lignes dans KB | Schéma / Colonnes | Statut dans la KB | Preuve brute |
|---|---|---|---|---|---|
| **Hiérarchie ATC & Classes** | `atc_hierarchy` | 6,934 | `atc_code`, `parent_atc`, `level`, `name_en`, `name_fr`, `name_jp`, `description` | ✅ Complet (Niveaux 1 à 5, y compris `M01AE19`, `M02AA31`, `B01AF03`, `M01AE04`) | [`01-schema.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/01-schema.out), [`10-atc-literals.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/10-atc-literals.out) |
| **Value Sets IPS (SNOMED, LOINC, ATC)** | `ips_valuesets` | 8,554 | `vs_id`, `code`, `code_system`, `display_en`, `ips_required` | ✅ Présent (32 value sets : vaccins 78, procédures 690, dispositifs 72, sang 13, relations 39) | [`01-schema.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/01-schema.out), [`04-vaccines-translations.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/04-vaccines-translations.out) |
| **Traductions Multilingues Globales** | `ips_valuesets_translations` | 85,736 | `vs_id`, `code`, `lang`, `display` | ⚠️ Inégal : es:8020, cs:4421, fr:4413, pt:4408, de:4391, it:4379, sv:4333, nl:4298, pl:4294, ru:4294, fi:4274, ja:4221, ar/et/hu/is/ko/lt/lv:3943, no:2389 | [`03-translations-global.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/03-translations-global.out) |
| **Traductions Vaccins IPS** | `ips_valuesets_translations` | 78 vaccins | Sur 78 concepts : es:76, **fr:2, ja:1**, cs:2, autres:1 | ⚠️ Goulot d'étranglement majeur justifiant le catalogue Kotlin existant | [`04-vaccines-translations.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/04-vaccines-translations.out) |
| **Relations de Contacts** | `personal-relationship-uv-ips` | 39 concepts | 39 concepts présents mais **0 traduction** multilingue | ⚠️ Libellés anglais seuls sans catalogue | [`05-personal-relationships.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/05-personal-relationships.out) |
| **Groupes Sanguins** | `ips_valuesets` & `terminology_codes` | 8/8 présents | 8 SNOMED CT présents dans `results-blood-group-snomed-ct-ips-free-set` et `problems` | ✅ Conforme | [`09-blood-groups.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/09-blood-groups.out) |
| **Voies d'Administration** | `terminology_codes` | 8 voies | Voie inhalée `447694001`, IV `47625008`, IM `78421000`, sublinguale `37839007`, cutanée `6064005`, orale `26643006`. Le code `260548002` est absent (remplacé par `26643006`). | ✅ Couvert via `26643006` | [`06-routes-check.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/06-routes-check.out) |
| **Résultats Biologiques Courants** | `ips_valuesets` | 0/31 | Les 31 codes LOINC de `IpsResultCatalog` sont **absents** de `ips_valuesets` et `terminology_codes` | ❌ Manquant | [`07-loinc-results-31.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/07-loinc-results-31.out) |
| **Observations Grossesse** | `ips_valuesets` | 1/2 | `11778-8` (terme estimé) présent dans `edd-method-uv-ips`. `82810-3` absent. | ⚠️ Partiel | [`08-pregnancy-obs-codes.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/08-pregnancy-obs-codes.out) |
| **Tables CJK & Synonymes** | `drug_names_cjk` | FTS5 | Table FTS5 bien présente dans SQLite (`drug_names_cjk`, `terminology_cjk`) | ✅ Existe dans la base | [`02-tables.out`](file:///Users/kurodohenroonsen/.gemini/antigravity/worktrees/26346173-b55b-4af7-bcbe-8eeceece4534/ag-0048-analyse/docs/analysis/kb-only-evidence/02-tables.out) |

---

## 3. Inventaire Détaillé des Fichiers Kotlin et Causes du Codage en Dur

### A. Catalogues Statiques (`pillars/*Catalog.kt`)
1. **`pillars/IpsVaccineCatalog.kt`** (31 SNOMED CT) :
   - *Dans la KB* : `ips_valuesets` (`vaccines-snomed-ct-ips-free-set`) possède 78 vaccins en anglais (`display_en`).
   - *Pourquoi codé en dur* : `ips_valuesets_translations` n'a que **2 traductions en français** et **1 en japonais** pour les vaccins (contre 76 en espagnol) ! Sans le catalogue Kotlin, l'UI en français et japonais affichait des concepts non traduits (`Not Translated[...]`).
2. **`pillars/IpsProcedureCatalog.kt`** (12 SNOMED CT) :
   - *Dans la KB* : 690 procédures dans `procedures-snomed-ct-ips-free-set` (304 traductions FR, 296 JA).
3. **`pillars/IpsDeviceCatalog.kt`** (6 SNOMED CT) :
   - *Dans la KB* : 72 dispositifs (16 traductions FR, 14 JA).
4. **`pillars/IpsRouteCatalog.kt`** (5 SNOMED CT) :
   - *Dans la KB* : Voie inhalée `447694001`, IV `47625008`, IM `78421000`, sublinguale `37839007`, orale `26643006`. Le code SNOMED CT alternatif `260548002` n'est pas utilisé (la base et le catalogue Kotlin emploient tous deux `26643006`).
5. **`pillars/IpsRelationshipCatalog.kt`** (Rôles HL7 v3-RoleCode) :
   - *Dans la KB* : 39 concepts dans `personal-relationship-uv-ips`, mais **0 traduction** dans `ips_valuesets_translations`. Le catalogue Kotlin fournissait `Ami(e)`, `Conjoint(e)`, etc.

### B. Résultats et Grossesse (LOINC)
1. **`pillars/IpsResultCatalog.kt`** (31 LOINC) :
   - *Dans la KB* : Les 31 examens biologiques généraux (`882-1` ABO, `2345-7` Glucose, `2160-0` Créatinine, etc.) sont complètement absents de `ips_valuesets` (qui ne contient que de la radiologie et de la microbiologie) et de `terminology_codes`.
   - *Source officielle à intégrer dans la KB* : ValueSet HL7 IPS Results (LOINC) / LOINC Top 2000 Common Lab Results (Regenstrief Institute, licence libre LOINC).
2. **`pillars/IpsPregnancyCatalog.kt`** (16 LOINC) :
   - *Dans la KB* : `pregnancy-status-uv-ips` (`LA15173-0`, `LA26683-5`, `LA4489-6`) et `pregnancies-summary-uv-ips` (`11612-9` à `11640-0`) sont présents. L'observation `11778-8` est présente. Le code d'observation `82810-3` doit être ajouté dans la KB.

### C. Mots-clés, Heuristiques et Normalisation
1. **`kb/AllergyKeywords.kt`** (13 préfixes ATC + mots-clés) :
   - Mots-clés (`ains`, `penicilline`, `statin`) utilisés pour résoudre des allergies saisies sans code.
   - *Remplacement KB* : Enrichissement de `allergy_cross_reactivity` ou recherche FTS sur les familles d'allergènes.
2. **`kb/DrugDiseaseTerms.kt`** (`VARIANTS`, `EXCLUDED_QUALIFIERS`) :
   - Remplacement par matching FTS sur `drug_disease_interactions`.
3. **`kb/KnowledgeBaseService.kt`** (`when` de 9 molécules CJK) :
   - Remplacement par matching FTS sur la table `drug_names_cjk` / `terminology_cjk` dont l'existence est confirmée.

---

## 4. Tableau Synthétique d'Impact & Risque de Migration

| Fichier Source | Contenu | Statut KB | Source Officielle Référente | Taille | Risque si Retrait Prématuré |
|---|---|---|---|:---:|---|
| `pillars/IpsVaccineCatalog.kt` | 31 vaccins SNOMED | Partiel (EN seul, 2 FR, 1 JA) | HL7 IPS Vaccine ValueSet / WHO ATC | **M** | Moyen (libellés non traduits FR/JA) |
| `pillars/IpsProcedureCatalog.kt` | 12 procédures | Présent (690) | SNOMED CT IPS Free Set | **S** | Faible |
| `pillars/IpsRouteCatalog.kt` | 5 voies | Présent (5/5 sous codes KB) | EDQM Standard Terms / SNOMED CT | **S** | Faible |
| `pillars/IpsDeviceCatalog.kt` | 6 dispositifs | Présent (72) | SNOMED CT IPS Free Set / EMDN | **S** | Faible |
| `ips/IpsBloodGroup.kt` | 8 SNOMED + 1 LOINC | Présent (13) | LOINC 882-1, SNOMED Blood Groups | **S** | Élevé (triage groupe sanguin) |
| `pillars/IpsResultCatalog.kt` | 31 LOINC | 0/31 dans KB | LOINC Top 2000 Common Lab Results | **L** | Élevé (perte liste examens) |
| `pillars/IpsPregnancyCatalog.kt` | 16 LOINC | Partiel (réponses OK, 1/2 obs) | LOINC Clinical Observations | **M** | Moyen |
| `pillars/IpsRelationshipCatalog.kt` | Rôles v3-RoleCode | Présent (39, 0 trans) | HL7 v3-RoleCode ValueSet | **S** | Moyen (libellés EN bruts) |
| `kb/AllergyKeywords.kt` | 13 ATC + mots-clés | À modéliser | WHO ATC Index + MedDRA / MeSH | **L** | Élevé (alertes allergies manquées) |
| `kb/DrugDiseaseTerms.kt` | Variantes / exclusions | À modéliser | DDInter 2.0 / ICD-10 / SNOMED CT | **M** | Élevé (interactions manquées) |
| `kb/KnowledgeBaseService.kt` | `when` 9 molécules CJK | Présent (table `drug_names_cjk`) | JAPIC / PMDA Master / KEGG Drug | **M** | Moyen (fallback OCR CJK) |

---

## 5. Provenance et Pipeline de Construction de la KB

- **Localisation** : Le pipeline de construction de `knowledge_full.db` (ingestion UMLS, SNOMED CT, LOINC, WHO-ATC, DDInter 2.0) **n'est pas présent dans ce dépôt Git**.
- La base SQLite est distribuée précompilée à l'URL `https://jemmapass.net/kb/1.1/knowledge_full.db`.
- **Question pour Kudoro** : Où se trouve le dépôt source du pipeline de build de la KB afin d'y intégrer les Value Sets LOINC manquants et les traductions complètes FR/JA ?

---

## 6. Protocole d'Exécution Validé

1. **Étape 1 (Complète avec preuves brutes)** : Inventaire exhaustif des 191 littéraux dans `coverage.tsv` et 10 requêtes SQL publiées dans `kb-only-evidence/`. Aucun code applicatif n'est modifié.
2. **Étape 2** : Claude écrit les tests de l'interface unifiée `KbCatalogProvider` avec gestion du statut « non vérifié ».
3. **Étape 3** : Implémentation ordonnée, famille par famille, sans risque de régression clinique.
