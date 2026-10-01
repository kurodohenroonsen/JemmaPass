# Audit de couverture de la Base de Connaissances (KB) pour les piliers IPS restants

- Date : 2026-10-01
- Source analysée : `knowledge_full.db` (version 1.1 · 3,1 Go embarquée sur l'appareil)
- Référence des données brutes : [`kb-coverage-remaining-pillars.txt`](file:///Users/kurodohenroonsen/Documents/jemmapass-device-reports/kb/kb-coverage-remaining-pillars.txt)

---

## 1. Synthèse générale de la KB actuelle

1. **Volume global :**
   - `terminology_codes` : **1 454 447** entrées au total :
     - `Medication` : 135 541
     - `Condition` : 69 523 (dont **46 638** avec un code `snomed_code` non-null)
     - `Procedure` : 51 470
     - `Device` : 14 048
     - `Chemical_Allergen` : 232 744
     - `Protein_Allergen` : 162 465
     - `Mineral` : 3 498
     - `Food` : 2 856
     - `Vitamin_DFI` : 124
     - Sans catégorie (`None`) : 780 182
   - `ips_valuesets` : 37 associations ValueSet-Système réparties sur 32 `vs_id` distincts, totalisant **8 757 codes**.
   - `ips_valuesets_translations` : 20 langues disponibles (ar, cs, de, es, et, fi, fr, hu, is, it, ja, ko, lt, lv, nl, no, pl, pt, ru, sv).
2. **Codes LOINC dans la base :**
   - Exactement **29 codes LOINC** présents dans `ips_valuesets` (répartis sur 7 value sets : `pregnancies-summary-uv-ips` [9], `current-smoking-status-uv-ips` [8], `condition-severity-uv-ips` [3], `edd-method-uv-ips` [3], `pregnancy-status-uv-ips` [3], `results-radiology-txtobs-snomed-dicom-loinc-uv-ips` [2], `problem-type-loinc` [1]).
   - **0 code LOINC** dans `terminology_codes` (la recherche FTS5 ne couvre pas LOINC).
   - Les 29 codes LOINC sont exclusivement rédigés en anglais (`display_en`), sans aucune traduction dans `ips_valuesets_translations`.
3. **Règles de validation FHIR (`ips_field_rules`) :**
   - **222 règles** répertoriées pour les ressources examinées : `Observation` (68), `Condition` (46), `CarePlan` (38), `AllergyIntolerance` (23), `Consent` (18), `Flag` (15), `MedicationStatement` (14), `ClinicalImpression` (0).

---

## 2. Tableau d'audit de couverture des 8 piliers IPS demandés

| Pilier IPS | Statut | Nombre de codes | Value Set(s) source | Langues disponibles | Analyse & Constat ontologique |
|---|---|---|---|---|---|
| **📜 Problèmes antérieurs**<br>*(History of Past Illness)* | **Couvert** *(Riche)* | **5 622** (Free set IPS)<br>+ 69 523 (`terminology_codes`) | `problems-snomed-ct-ips-free-set`<br>`condition-severity-uv-ips`<br>`absent-or-unknown-problems-uv-ips`<br>`problem-type-loinc` / `uv-ips` | **20 langues** sur le free set<br>(FR: 3 596, JA: 3 512, ES: 5 265, EN: 5 622).<br>Sévérité / absence : EN seul. | `problems-snomed-ct-ips-free-set` couvre massivement les pathologies en SNOMED CT avec traductions officielles FR/JA. 46 638 conditions SNOMED dans `terminology_codes` FTS5. Sévérité couverte (3 codes LOINC `LA6750-9`..`LA6752-5`). 46 règles `ips_field_rules`. |
| **🤰 Grossesse**<br>*(Pregnancy)* | **Partiel** | **15 codes** LOINC | `pregnancy-status-uv-ips` (3)<br>`pregnancies-summary-uv-ips` (9)<br>`edd-method-uv-ips` (3) | **Anglais uniquement**<br>(0 traduction dans `ips_valuesets_translations`) | Statut clinique (`LA15173-0` Pregnant, `LA26683-5` Not pregnant, `LA4489-6` Unknown). Antécédents obstétricaux LOINC (naissances, avortements, parité : `11612-9`..`33065-4`). Date estimée d'accouchement (`11778-8`..`11780-4`). Manque les libellés FR/JA. |
| **♿ Statut fonctionnel**<br>*(Functional Status)* | **Absent** | **0 code dédié** | Aucun value set fonctionnel (`functional` / `disability` = 0) | — | Aucun concept d'évaluation d'autonomie ou d'incapacité. 0 règle pour `ClinicalImpression` dans `ips_field_rules`. Seules quelques mentions de déficiences diagnostiques existent dans le free set des problèmes (`problems-snomed-ct-ips-free-set`). |
| **🚬 Antécédents sociaux**<br>*(Social History : Tabac / Alcool)* | **Partiel** (Tabac)<br>**Absent** (Alcool) | **8 codes** (Tabac)<br>**0 code** (Alcool) | `current-smoking-status-uv-ips` (8) | **Anglais uniquement** pour le tabac<br>(0 traduction dans `ips_valuesets_translations`) | Tabagisme modélisé via 8 codes LOINC Answer list (`LA15920-4` Former smoker, `LA18976-3` Current every day smoker, etc.). **Absence totale de ValueSet, codes ou règles pour la consommation d'alcool**. |
| **💓 Signes vitaux**<br>*(Vital Signs)* | **Absent** *(en DB)* | **0 code dédié** dans `ips_valuesets` | Aucun value set vital signs en base SQLite | — | Aucun value set `results-vital-signs-uv-ips` dans la KB. Les panels de signes vitaux LOINC (TA `85354-9`, FC `8867-4`, Temp `8310-5`, FR `9279-1`, SpO2 `2708-6`, Poids `29463-7`, Taille `8302-2`, IMC `39156-5`) sont absents de la DB et doivent être fournis via un catalogue applicatif embarqué. |
| **✍️ Directives anticipées**<br>*(Advance Directives)* | **Absent** *(en terminologie)*<br>*(Structure FHIR couverte)* | **0 code de volonté/mandat** | Aucun value set terminologique pour directives anticipées | — | Aucune terminologie pour les souhaits de fin de vie, limitations de réanimation (DNR/DNI) ou mandataires médicaux. Structurellement, la ressource `Consent` est cadrée par 18 règles dans `ips_field_rules`. |
| **📋 Plan de soins**<br>*(Care Plan)* | **Absent** *(en terminologie)*<br>*(Structure FHIR couverte)* | **0 code d'activité/objectif** | Aucun value set terminologique pour activités de plan de soins | — | Aucun catalogue d'actions, protocoles de surveillance ou objectifs thérapeutiques. En revanche, la ressource `CarePlan` possède 38 règles complètes dans `ips_field_rules`. |
| **⚠️ Alertes**<br>*(Alerts / Flag)* | **Absent** *(en terminologie)*<br>*(Structure FHIR couverte)* | **0 code d'alerte** | Aucun value set terminologique pour alertes cliniques | — | Aucun catalogue de niveaux de criticité ou de types d'alerte médicale d'urgence. La ressource `Flag` dispose de 15 règles structurelles dans `ips_field_rules`. |

---

## 3. Recommandations stratégiques d'enrichissement et d'implémentation

1. **Sprint immédiat — 📜 Problèmes antérieurs :**
   - **Prêt pour le développement immédiat** sans enrichissement KB requis.
   - S'appuyer sur `problems-snomed-ct-ips-free-set` (5 622 concepts, traductions FR/JA déjà intégrées) en tête de liste et recherche FTS5 sur `terminology_codes` (`Condition`: 46 638 entrées SNOMED CT).
   - Intégrer les libellés de sévérité FR dans `ips_translations.json` (Léger, Modéré, Sévère).
2. **Piliers à compléter par catalogue applicatif embarqué (stratégie adoptée au Sprint 3 pour 🧪) :**
   - **💓 Signes vitaux :** Implémenter `IpsVitalSignsCatalog.kt` calqué sur `IpsResultCatalog.kt` avec les 8 mesures LOINC universelles, leurs codes canoniques, leurs unités UCUM fixes (`mm[Hg]`, `/min`, `Cel`, `kg`, `cm`, `kg/m2`, `%`) et leurs libellés FR/EN/JA.
   - **🤰 Grossesse :** Définir `IpsPregnancyCatalog.kt` associant les codes LOINC existants (`pregnancy-status-uv-ips`, `pregnancies-summary-uv-ips`, `edd-method-uv-ips`) à leurs traductions françaises et japonaises.
   - **🚬 Tabac & Alcool :** Créer un catalogue social embarqué associant les 8 statuts tabagiques LOINC (`LA15920-4`..) et les catégories d'usage d'alcool standard (abstinent, occasionnel, régulier, excessif) avec traductions FR/JA.
3. **Piliers administratifs / documentaires (Directives anticipées, Plan de soins, Alertes) :**
   - Ressources cadrées structurellement par `ips_field_rules` (`Consent`: 18, `CarePlan`: 38, `Flag`: 15).
   - Privilégier des formulaires structurés avec statuts FHIR stricts (`Consent.status`, `CarePlan.status`, `Flag.status`) et texte libre / catégories simples plutôt qu'une terminologie lourde.
