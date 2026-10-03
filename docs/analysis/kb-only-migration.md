# Analyse de Migration « KB Seulement » (Protocole §9)

Date : 2026-10-02  
Auteur : Antigravity (Sous-agent Spécialiste KB & Clinical Data)  
Branche : `ag/0048-analyse`  
Base examinée : `knowledge_full.db` v1.1 (/tmp/jp/kb/knowledge_full.db, 3.1 Go, SHA-256 vérifié)

---

## 1. Contexte & Règle « KB Seulement »
Sur décision de Kudoro (Product Owner) :
> *« Vous hardcodez des codes dans l'app ? NON. C'est le but d'avoir une KB de source officielle pouvant être mise à jour sans recompiler l'application. »*

Les tests rouges de la branche `tests/kb-only` (`3aaad26`, CI run #57) prouvent la présence de littéraux cliniques dans le code Kotlin :
- **ATC** : 28 littéraux détectés (13 dans `JemmaProfileHydrator.kt`, 13 dans `AllergyKeywords.kt`, 2 dans `AssistantMultiPreviewFragment.kt`).
- **SNOMED CT** : 72 littéraux détectés (31 vaccins, 12 interventions, 8 groupes sanguins, 6 dispositifs, 5 voies, etc.).
- **LOINC** : 91 littéraux détectés (31 résultats bio, 16 grossesses, 16 statuts obstétricaux, 14 formulaires, etc.).

---

## 2. État Réel des Données dans la KB du Téléphone (`knowledge_full.db`)

Les requêtes SQL directes sur la KB locale révèlent la couverture suivante :

| Domaine Médical | Table KB | Lignes dans KB | Statut dans la KB | Exemple de Requête Remplaçante |
|---|---|---|---|---|
| **Hiérarchie ATC & Classes** | `atc_hierarchy` | 6,934 | ✅ Complet (Niveaux 1 à 5, y compris `M01AE19`, `M02AA31`, `B01AF03`, `M01AE04`) | `SELECT atc_code, name_en FROM atc_hierarchy WHERE parent_atc = 'M01A';` |
| **Value Sets IPS (SNOMED, LOINC, ATC)** | `ips_valuesets` | 8,554 | ✅ Présent (32 value sets : vaccins 78, procédures 690, dispositifs 72, sang 13, relations 39) | `SELECT code, display_en, code_system FROM ips_valuesets WHERE vs_id = 'vaccines-snomed-ct-ips-free-set';` |
| **Traductions Multilingues** | `ips_valuesets_translations` | 85,736 | ⚠️ Partiel (ar:3943, cs:4421, de:4391, es:8020, et:3943, fi:4274, fr:4413, hu:3943, is:3943, it:4379, ja:4221, ko:3943, lt:3943, lv:3943, nl:4298, no:2389, pl:4294, pt:4408, ru:4294, sv:4333) | `SELECT display FROM ips_valuesets_translations WHERE code = ? AND lang = 'fr';` |
| **Réactivité Croisée Allergies** | `allergy_cross_reactivity` | 52 | ✅ Présent (classes croisées bêta-lactamines, sulfamides, AINS) | `SELECT cross_reactive_code, severity FROM allergy_cross_reactivity WHERE allergen_code = ?;` |
| **Interactions Médicament-Pathologie** | `drug_disease_interactions` | 8,121 | ✅ Présent | `SELECT severity, description FROM drug_disease_interactions WHERE drug_atc = ?;` |
| **Concepts DCI & Noms Commerciaux** | `ddinter_drugs` | 2,239 | ✅ Présent (DCI + synonymes ATC) | `SELECT ddinter_id, primary_atc, atc_codes FROM ddinter_drugs WHERE name = ?;` |

---

## 3. Inventaire Détaillé des Fichiers Kotlin et Causes du Codage en Dur

### A. Catalogues Statiques (`pillars/*Catalog.kt`)
1. **`pillars/IpsVaccineCatalog.kt`** (31 SNOMED CT) :
   - *Dans la KB* : `ips_valuesets` (`vaccines-snomed-ct-ips-free-set`) possède 78 vaccins en anglais (`display_en`).
   - *Pourquoi codé en dur* : `ips_valuesets_translations` n'a que **2 traductions en français** et **1 en japonais** pour les vaccins (contre 76 en espagnol) ! Sans le catalogue Kotlin, l'UI en français et japonais affichait des concepts non traduits.
2. **`pillars/IpsProcedureCatalog.kt`** (12 SNOMED CT) :
   - *Dans la KB* : 690 procédures dans `procedures-snomed-ct-ips-free-set` (304 traductions FR, 296 JA).
3. **`pillars/IpsDeviceCatalog.kt`** (6 SNOMED CT) :
   - *Dans la KB* : 72 dispositifs (16 traductions FR, 14 JA).
4. **`pillars/IpsRouteCatalog.kt`** (5 SNOMED CT) :
   - *Dans la KB* : Voie inhalée `447694001`, IV `47625008`, IM `78421000`, sublinguale `37839007` sont dans `terminology_codes`. La voie orale `260548002` manque dans la KB actuelle.
5. **`pillars/IpsRelationshipCatalog.kt`** (Rôles HL7 v3-RoleCode) :
   - *Dans la KB* : 39 concepts dans `personal-relationship-uv-ips`, mais **0 traduction** dans `ips_valuesets_translations`. Le catalogue Kotlin fournissait `Ami(e)`, `Conjoint(e)`, etc.

### B. Résultats et Grossesse (LOINC)
1. **`pillars/IpsResultCatalog.kt`** (31 LOINC) :
   - *Dans la KB* : Les examens biologiques généraux (`882-1` ABO, `2345-7` Glucose, `2160-0` Créatinine) ne figurent pas dans `ips_valuesets` (qui n'a que la radiologie et la microbiologie).
   - *Source officielle à intégrer dans la KB* : ValueSet HL7 IPS Results (LOINC) / LOINC Top 2000 Common Lab Results (Regenstrief Institute, licence libre LOINC).
2. **`pillars/IpsPregnancyCatalog.kt`** (16 LOINC) :
   - *Dans la KB* : `pregnancy-status-uv-ips` (`LA15173-0`, `LA26683-5`, `LA4489-6`) et `pregnancies-summary-uv-ips` (`11612-9` à `11640-0`) sont présents. Les codes d'observation `82810-3` et `11778-8` doivent être ajoutés dans la KB.

### C. Mots-clés, Heuristiques et Normalisation
1. **`kb/AllergyKeywords.kt`** (13 préfixes ATC + mots-clés) :
   - Mots-clés (`ains`, `penicilline`, `statin`) utilisés pour résoudre des allergies saisies sans code.
   - *Remplacement KB* : Table FTS sur les familles d'allergènes ou enrichissement de `allergy_cross_reactivity`.
2. **`kb/DrugDiseaseTerms.kt`** (`VARIANTS`, `EXCLUDED_QUALIFIERS`) :
   - Remplacement par matching FTS sur `drug_disease_interactions`.
3. **`kb/KnowledgeBaseService.kt`** (`when` de 9 molécules CJK) :
   - Remplacement par la table `terminology_cjk` / `drug_names_cjk`.

---

## 4. Tableau Synthétique d'Impact & Risque de Migration

| Fichier Source | Contenu | Statut KB | Source Officielle Référente | Taille | Risque si Retrait Prématuré |
|---|---|---|---|:---:|---|
| `pillars/IpsVaccineCatalog.kt` | 31 vaccins SNOMED | Partiel (EN seul) | HL7 IPS Vaccine ValueSet / WHO ATC | **M** | Moyen (libellés non traduits FR/JA) |
| `pillars/IpsProcedureCatalog.kt` | 12 procédures | Présent (690) | SNOMED CT IPS Free Set | **S** | Faible |
| `pillars/IpsRouteCatalog.kt` | 5 voies | 4/5 dans KB | EDQM Standard Terms / SNOMED CT | **S** | Faible |
| `pillars/IpsDeviceCatalog.kt` | 6 dispositifs | Présent (72) | SNOMED CT IPS Free Set / EMDN | **S** | Faible |
| `ips/IpsBloodGroup.kt` | 8 SNOMED + 1 LOINC | Présent (13) | LOINC 882-1, SNOMED Blood Groups | **S** | Élevé (triage groupe sanguin) |
| `pillars/IpsResultCatalog.kt` | 31 LOINC | Manquant | LOINC Top 2000 Common Lab Results | **L** | Élevé (perte liste examens) |
| `pillars/IpsPregnancyCatalog.kt` | 16 LOINC | Partiel (réponses OK) | LOINC Clinical Observations | **M** | Moyen |
| `pillars/IpsRelationshipCatalog.kt` | Rôles v3-RoleCode | Présent (39, 0 trans) | HL7 v3-RoleCode ValueSet | **S** | Moyen (libellés EN bruts) |
| `kb/AllergyKeywords.kt` | 13 ATC + mots-clés | À modéliser | WHO ATC Index + MedDRA / MeSH | **L** | Élevé (alertes allergies manquées) |
| `kb/DrugDiseaseTerms.kt` | Variantes / exclusions | À modéliser | DDInter 2.0 / ICD-10 / SNOMED CT | **M** | Élevé (interactions manquées) |
| `kb/KnowledgeBaseService.kt` | `when` 9 molécules CJK | À migrer table FTS | JAPIC / PMDA Master / KEGG Drug | **M** | Moyen (fallback OCR CJK) |

---

## 5. Provenance et Pipeline de Construction de la KB

- **Localisation** : Le pipeline de construction de `knowledge_full.db` (ingestion UMLS, SNOMED CT, LOINC, WHO-ATC, DDInter 2.0) **n'est pas présent dans ce dépôt Git**.
- La base SQLite est distribuée précompilée à l'URL `https://jemmapass.net/kb/1.1/knowledge_full.db`.
- **Question pour Kudoro** : Où se trouve le dépôt source du pipeline de build de la KB afin d'y intégrer les Value Sets LOINC manquants et les traductions complètes FR/JA ?

---

## 6. Protocole d'Exécution Validé

1. **Étape 1 (Faite)** : Inventaire exhaustif et preuve sur pièces par requêtes SQL sur la KB. Aucun code applicatif n'est modifié ni supprimé.
2. **Étape 2** : Claude écrit les tests de l'interface unifiée `KbCatalogProvider` avec gestion du statut « non vérifié ».
3. **Étape 3** : Implémentation ordonnée, famille par famille, sans risque de régression clinique.
