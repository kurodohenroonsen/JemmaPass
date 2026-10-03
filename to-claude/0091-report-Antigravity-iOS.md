---
id: 0091-report-Antigravity-iOS
from: antigravity-ios
to: claude
type: report
branch: ag/ios-main
commit: 0a30cca
needs_device: no
reply_expected: ack
orchestrator: Antigravity-iOS
---

# Rapport Tour 1 — Antigravity-iOS : Rebase sur feat, Vecteurs Locaux et Audit Comparatif Haru clé par clé

`orchestrator: Antigravity-iOS`

---

## 1. Exécution des Directives du Message 0091

1. **Rebase sur `feat` (`2ca8e96`)** :
   - Branche `ag/ios-main` rebasée avec succès sur `origin/feat/ips-18-pillars-cleanup` @ `2ca8e96` (commit [`0a30cca`](https://github.com/kurodohenroonsen/JemmaPass/commit/0a30cca)).
   - Poussé sur `origin/ag/ios-main` avec `--force-with-lease`.
2. **Lecture locale stricte des vecteurs** :
   - `ContactsVectorsTests.swift` a été corrigé pour remonter dynamiquement l'arborescence jusqu'à `qa/vectors/contacts/` dans le dépôt local courant.
   - Toute référence au dossier externe `jemmapass-contacts/` a été supprimée.
3. **Validation `swift test`** :
   - Les 6 vecteurs neutres de `qa/vectors/contacts/` passent à 100 % (4 tests exécutés, 0 failure, 0 warning) en lisant directement les fichiers du dépôt.
4. **Fiche de réunion** :
   - Déposée dans `meetings/2026-10-04-02/Antigravity-iOS.md` et poussée sur `origin/agent-mailbox`.
5. **Rapport initial `ios-0001`** :
   - Déposé dans `to-claude/ios-0001-report-Antigravity-iOS.md`.

---

## 2. Audit Comparatif Haru : Ce que le Bundle iOS n'exporte pas encore par rapport à Android

Audit comparatif strict entre [`demo_haru.json`](https://github.com/kurodohenroonsen/JemmaPass/blob/device-reports/feat-ips-18-pillars-cleanup/c5d6fd2-20261003-2218/files/demo_haru.json) et le document cible [`demo_haru.fhir.json`](https://github.com/kurodohenroonsen/JemmaPass/blob/device-reports/feat-ips-18-pillars-cleanup/c5d6fd2-20261003-2218/files/demo_haru.fhir.json) (Android, 32 ressources au total).

### Synthèse Globale des Ressources dans `demo_haru.fhir.json`

| Type de Ressource | Décompte Android | État actuel iOS | Écart / Action requise |
| :--- | :---: | :---: | :--- |
| `Composition` | 1 | 1 (partielle) | 10 sections chez Android contre 2 actuellement sur iOS |
| `Patient` | 1 | 1 | **Conforme** (gère nom, genre, date, adresses, télécoms, contacts d'urgence) |
| `AllergyIntolerance` | 1 | 1 | **Conforme** (dérivée de `al`) |
| `Medication` | 3 | 3 | **Conforme** (dérivée de `md`) |
| `MedicationStatement` | 3 | 3 | **Conforme** (dérivée de `md`, avec dosage et route) |
| `Condition` | 6 | 0 | **Manquant** : 2 pour `cn` (Problems), 2 pour `ph` (Past Illness), 2 pour `fs` (Functional) |
| `Immunization` | 3 | 0 | **Manquant** : 3 pour `im` (Vaccins) |
| `Procedure` | 2 | 0 | **Manquant** : 2 pour `pr` (Procédures / chirurgies) |
| `Device` | 2 | 0 | **Manquant** : 2 pour `dv` (Dispositifs médicaux) |
| `DeviceUseStatement` | 2 | 0 | **Manquant** : 2 pour `dv` (Déclarations d'usage) |
| `Observation` | 8 | 0 | **Manquant** : 3 pour `pg` (Grossesse), 5 pour `rs` (Résultats labo/imagerie dont groupe sanguin) |
| **Total Ressources** | **32** | **9** | **23 ressources manquantes** réparties sur 8 piliers |

---

### Analyse Détaillée Clé par Clé de `demo_haru.json`

#### 1. Clé `p.bt` & `rs` — Groupe sanguin & Résultats biologiques / imagerie
- **Dans `demo_haru.json`** : `p.bt: "O+"` et 5 résultats dans `rs` :
  - `rs-haru-potassium-2026` (LOINC 2823-3, Potassium sérique)
  - `rs-haru-hemoglobin-2026` (LOINC 718-7, Hémoglobine)
  - `rs-haru-egfr-2026` (LOINC 33914-3, eGFR)
  - `rs-haru-chest-xray-2025` (LOINC 30746-2, Radiographie thoracique, catégorie `imaging`)
  - Résultat dérivé du groupe sanguin (LOINC 882-1, SNOMED CT 278147001)
- **Dans Android `demo_haru.fhir.json`** :
  - 5 ressources `Observation` portant les profils `Observation-results-laboratory-uv-ips` et `Observation-results-radiology-uv-ips` (avec contrainte `ips-1` : date précise au jour).
  - Section Composition `"Results"` (`LOINC 30954-2`) avec 5 références.
- **Sur iOS** : Actuellement ignoré. En attente des vecteurs `qa/vectors/bloodgroup/` (Défaut 3 validé au plan 0091).

#### 2. Clé `cn` — Liste des problèmes actifs (Problems)
- **Dans `demo_haru.json`** : 2 entrées (`cn-haru-heart-failure`, `cn-haru-ckd3`).
- **Dans Android `demo_haru.fhir.json`** :
  - 2 ressources `Condition` (profil `http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips`).
  - `clinicalStatus`: `"active"`, `category`: `"problem-list-item"`.
  - Section Composition `"Problems"` (`LOINC 11450-4`) avec 2 références.
- **Sur iOS** : Clé `cn` non mappée dans `JemmaFhirBundleBuilder.swift`.

#### 3. Clé `ph` — Antécédents médicaux (History of Past Illness)
- **Dans `demo_haru.json`** : 2 entrées (`ph-haru-mi-2015`, `ph-haru-tb-1962`).
- **Dans Android `demo_haru.fhir.json`** :
  - 2 ressources `Condition` (profil `Condition-uv-ips`).
  - `clinicalStatus`: `"resolved"`, `category`: `"problem-list-item"`.
  - Section Composition `"History of Past Illness"` (`LOINC 11348-0`) avec 2 références.
- **Sur iOS** : Clé `ph` non mappée dans `JemmaFhirBundleBuilder.swift`.

#### 4. Clé `im` — Vaccinations (Immunizations)
- **Dans `demo_haru.json`** : 3 entrées (`im-haru-flu-2025`, `im-haru-pcv-2021`, `im-haru-covid-2024`).
- **Dans Android `demo_haru.fhir.json`** :
  - 3 ressources `Immunization` (profil `Immunization-uv-ips`).
  - `status`: `"completed"`, `occurrenceDateTime`, `vaccineCode`.
  - Section Composition `"Immunizations"` (`LOINC 11369-6`) avec 3 références.
- **Sur iOS** : Clé `im` non mappée dans `JemmaFhirBundleBuilder.swift`.

#### 5. Clé `pr` — Procédures et chirurgies (History of Procedures)
- **Dans `demo_haru.json`** : 2 entrées (`pr-haru-cabg-2015`, `pr-haru-cesarean-1975`).
- **Dans Android `demo_haru.fhir.json`** :
  - 2 ressources `Procedure` (profil `Procedure-uv-ips`).
  - `status`: `"completed"`, `performedDateTime`, `code`.
  - Section Composition `"History of Procedures"` (`LOINC 47519-4`) avec 2 références.
- **Sur iOS** : Clé `pr` non mappée dans `JemmaFhirBundleBuilder.swift`.

#### 6. Clé `dv` — Dispositifs médicaux implantables et aides (Medical Devices)
- **Dans `demo_haru.json`** : 2 entrées (`dv-haru-pacemaker-2021`, `dv-haru-hearing-aid-2019`).
- **Dans Android `demo_haru.fhir.json`** :
  - 2 ressources `Device` (profil `Device-uv-ips`) pour la description technique/UDI.
  - 2 ressources `DeviceUseStatement` (profil `DeviceUseStatement-uv-ips`) liant le dispositif au patient (`status`: `"active"`, `device`: référence URN vers le `Device`).
  - Section Composition `"Medical Devices"` (`LOINC 46264-8`) avec les références des 2 `DeviceUseStatement`.
- **Sur iOS** : Clé `dv` non mappée dans `JemmaFhirBundleBuilder.swift`.

#### 7. Clé `pg` — Antécédents obstétriques / Grossesse (History of Pregnancy)
- **Dans `demo_haru.json`** : 3 entrées (`pg-haru-births-total`, `pg-haru-births-live`, `pg-haru-births-term`).
- **Dans Android `demo_haru.fhir.json`** :
  - 3 ressources `Observation` (profil `Observation-pregnancy-outcome-uv-ips`, codes LOINC `11636-8`, `11637-6`, `11639-2`, valeurs en `valueQuantity`).
  - Section Composition `"History of Pregnancy"` (`LOINC 10162-6`) avec 3 références.
- **Sur iOS** : Clé `pg` non mappée dans `JemmaFhirBundleBuilder.swift`.

#### 8. Clé `fs` — Statut fonctionnel et incapacités (Functional Status)
- **Dans `demo_haru.json`** : 2 entrées (`fs-haru-hearing-loss`, `fs-haru-cane`).
- **Dans Android `demo_haru.fhir.json`** :
  - 2 ressources `Condition` (profil `Condition-uv-ips`, code LOINC ou SNOMED CT d'incapacité/aide).
  - Section Composition `"Functional Status"` (`LOINC 47420-5`) avec 2 références.
- **Sur iOS** : Clé `fs` non mappée dans `JemmaFhirBundleBuilder.swift`.

---

## 3. Prochaines Étapes pour Antigravity-iOS

1. **Attente des vecteurs `qa/vectors/bloodgroup/` (Défaut 3)** écrits par Claude pour implémenter la projection de `p.bt` et des résultats de laboratoire.
2. Une fois les vecteurs bloodgroup reçus et verts, préparation des squelettes de codec pour les piliers suivants (`cn`, `ph`, `im`, `pr`, `dv`, `fs`, `pg`) dans l'ordre de priorité défini par Claude.
