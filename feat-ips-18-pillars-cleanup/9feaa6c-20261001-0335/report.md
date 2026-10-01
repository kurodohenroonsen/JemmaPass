# Device QA (Cycle 7 · Re-validation FHIR) · feat/ips-18-pillars-cleanup · 9feaa6c · Pixel 9 Pro XL · Android 15 · 2026-10-01 03:35

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR (fr-BE)
- Appareil : Pixel 9 Pro XL · Android 15 (modèle + OS, aucun identifiant matériel / numéro de série)
- Build : `logs/assemble.log` · Tests JVM : 70 tests, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 5 ✅ · 0 ❌ · 0 ⚠️ · 12 ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_*.fhir.json` | 70 tests JVM réussis (0 échec). Re-seed automatique : Kurodo (💉 4, 🏥 2, 📟 0, 🧪 4), Haru (💉 3, 🏥 2, 📟 2, 🧪 5), Kamekichi (💉 0, 🏥 0, 📟 0, 🧪 1). 64 checks d'invariants P1…P8 + P6c conformes (0 échec). |
| T1 Non-régression Haru | ✅ | `screenshots/c7-haru-profile.png`, `logs/logcat-ui.txt` | App lancée sans crash (`AndroidRuntime:E` vide). Fiche Haru ouverte sans crash. 6 piliers actifs affichés : 💉 (3), 🏥 (2), 📟 (2), 📝 (1), ⚠️ (1), 💊 (3), ainsi que le stub 🧪 (5). |
| T2 à T13 | ⏭ | — | Hors périmètre court Cycle 7 (re-validation FHIR). |
| A. Tests JVM & Re-seed | ✅ | `logs/unit-tests.log`, `verify-seed.md` | Commit `9feaa6c` validé. 70 tests JVM unitaires au vert. Distribution des résultats de laboratoire : Kurodo=4, Haru=5, Kamekichi=1. |
| B. Vérifications FHIR ciblées | ✅ | `files/demo_haru.fhir.json`, `demo_kamekichi.fhir.json`, `demo_kurodo.fhir.json` | Citations confirmées : `Bundle.identifier` RFC 3986 présent, `Patient.extension` supprimée, groupe sanguin modélisé en Observation LOINC 882-1 avec SNOMED CT approprié (O+, B+, A+), `Device.udiCarrier.issuer` passé à GS1, mononyme Kamekichi sans champ `family` vide. |
| C. Validateur officiel HL7 (T6) | ✅ | `validator/*.xml`, `validator/*.log` | Validateur HL7 v6.10.4 exécuté avec `-locale en` sur les 3 personas en modes `-tx n/a` et `tx.fhir.org`. Disparition totale des erreurs en cascade sur Patient et Bundle. Analyse détaillée des erreurs résiduelles (ressource Observation groupe sanguin et émetteur GS1). |

---

## A. Tests JVM et Re-seed (A)

- **Commit vérifié :** `9feaa6c` sur la branche `feat/ips-18-pillars-cleanup`.
- **Tests unitaires JVM :** 70 tests exécutés, **0 échec** (`logs/unit-tests.log`).
- **Seed des personas sur appareil :**
  - `demo_kurodo` : 💉 4 immunizations · 🏥 2 procedures · 📟 0 devices · 🧪 4 results
  - `demo_haru` : 💉 3 immunizations · 🏥 2 procedures · 📟 2 devices · 🧪 5 results
  - `demo_kamekichi` : 💉 0 immunizations · 🏥 0 procedures · 📟 0 devices · 🧪 1 result
- **Validation `verify-seed.md` :** 64 checks exécutés, **0 failed** (PASS).

---

## B. Citations et vérifications ciblées des fichiers FHIR (B)

### 1. `files/demo_haru.fhir.json`

- **`Bundle.identifier` :** Présent avec système RFC 3986 et UUID v3 déterministe.
  ```json
  "identifier": {
    "system": "urn:ietf:rfc:3986",
    "value": "urn:uuid:243a6333-028d-3926-a461-0566a8eb442f"
  }
  ```
- **`Patient.extension` :** Plus aucune extension personnalisée (`blood-type` retirée).
  ```json
  {
    "fullUrl": "urn:uuid:8b3e8568-1854-3c82-9f6b-7323880c5e7b",
    "resource": {
      "resourceType": "Patient",
      "id": "demo_haru",
      "name": [
        {
          "family": "Tanaka",
          "given": [
            "Haru"
          ]
        }
      ],
      "gender": "female",
      "birthDate": "1982-04-12"
    }
  }
  ```
- **Observation Groupe sanguin `rs-blood-group-demo_haru` :**
  - Code LOINC : `882-1` ("ABO and Rh blood group")
  - Code SNOMED CT résultat : `278147001` ("Blood group O Rh(D) positive")
  ```json
  {
    "fullUrl": "urn:uuid:f096238b-caee-3c5e-8557-ca5e7e1f4865",
    "resource": {
      "resourceType": "Observation",
      "id": "rs-blood-group-demo_haru",
      "meta": {
        "profile": [
          "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips"
        ]
      },
      "status": "final",
      "category": [
        {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "code": "laboratory"
            }
          ]
        }
      ],
      "code": {
        "coding": [
          {
            "system": "http://loinc.org",
            "code": "882-1",
            "display": "ABO and Rh blood group"
          }
        ],
        "text": "ABO and Rh blood group"
      },
      "subject": {
        "reference": "urn:uuid:8b3e8568-1854-3c82-9f6b-7323880c5e7b"
      },
      "valueCodeableConcept": {
        "coding": [
          {
            "system": "http://snomed.info/sct",
            "code": "278147001",
            "display": "Blood group O Rh(D) positive"
          }
        ],
        "text": "Blood group O Rh(D) positive"
      }
    }
  }
  ```
- **Émetteur UDI Device (`Device.udiCarrier[0].issuer`) :** Passé de `gs1-di` à `http://hl7.org/fhir/NamingSystem/gs1`.
  ```json
  "udiCarrier": [
    {
      "deviceIdentifier": "(01)00643169007222(21)PJN1234567",
      "issuer": "http://hl7.org/fhir/NamingSystem/gs1",
      "carrierHRF": "(01)00643169007222(21)PJN1234567"
    }
  ]
  ```

### 2. `files/demo_kamekichi.fhir.json`

- **Mononyme Kamekichi (`Patient.name[0]`) :** Plus de champ `family: ""` vide.
  ```json
  "name": [
    {
      "given": [
        "Kamekichi"
      ]
    }
  ]
  ```
- **Observation Groupe sanguin `rs-blood-group-demo_kamekichi` :**
  - Code LOINC : `882-1`
  - Code SNOMED CT résultat : `278150003` ("Blood group B Rh(D) positive")
  ```json
  "valueCodeableConcept": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "278150003",
        "display": "Blood group B Rh(D) positive"
      }
    ],
    "text": "Blood group B Rh(D) positive"
  }
  ```

### 3. `files/demo_kurodo.fhir.json`

- **Observation Groupe sanguin `rs-blood-group-demo_kurodo` :**
  - Code LOINC : `882-1`
  - Code SNOMED CT résultat : `278149003` ("Blood group A Rh(D) positive")
  ```json
  "valueCodeableConcept": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "278149003",
        "display": "Blood group A Rh(D) positive"
      }
    ],
    "text": "Blood group A Rh(D) positive"
  }
  ```

---

## C. Rapport du validateur officiel HL7 (C)

Validateur exécuté avec l'option `-locale en` pour éliminer les faux positifs de traduction, sous profil `hl7.fhir.uv.ips#1.1.0` (FHIR R4 4.0.1).
Deux passes exécutées : mode hors-ligne sans terminologie (`-tx n/a`) et mode connecté (`tx.fhir.org`).

### 1. Tableau comparatif par persona et par mode

| Persona | Mode | Erreurs | Avertissements | Informations | Évolution vs Cycle 6 (-tx n/a) |
|---|---|---|---|---|---|
| `demo_kurodo` | `-tx n/a` | **3** | 28 | 0 | 11 ➔ **3** (-8 erreurs, -73%) |
| `demo_kurodo` | `tx.fhir.org` | **11** | 19 | 7 | 20 ➔ **11** (-9 erreurs) |
| `demo_haru` | `-tx n/a` | **5** | 44 | 2 | 19 ➔ **5** (-14 erreurs, -74%) |
| `demo_haru` | `tx.fhir.org` | **15** | 29 | 11 | 32 ➔ **15** (-17 erreurs) |
| `demo_kamekichi` | `-tx n/a` | **3** | 31 | 0 | 4 ➔ **3** (-1 erreur) |
| `demo_kamekichi` | `tx.fhir.org` | **5** | 20 | 2 | 5 ➔ **5** (=) |

### 2. Analyse des correctifs validés (Impact majeur du commit `9feaa6c`)

1. **Suppression de l'erreur de structure Bundle `bdl-9` :** L'ajout de `Bundle.identifier` avec le système RFC 3986 a éliminé l'erreur sur les 3 personas.
2. **Élimination de l'erreur Patient `blood-type` :** Le retrait de l'extension propriétaire sur `Patient` a validé le profil `Patient-uv-ips`.
3. **Disparition de TOUTES les erreurs en cascade sur les ressources natives 💉🏥🧪 :**
   Dans le cycle 6, chaque `Immunization`, `Procedure` et `Observation` échouait avec `Unable to find a profile match ... Patient-uv-ips`.
   **Résultat Cycle 7 : ZÉRO erreur de référence patient.** Les profils `Immunization-uv-ips`, `Procedure-uv-ips` et les 4 `Observation-results-laboratory-uv-ips` standard (Potassium, Hémoglobine, eGFR, HbA1c, LDL, Créatinine) ainsi que l'`Observation-results-radiology-uv-ips` sont **100 % conformes sans aucune erreur** !
4. **Correction du mononyme Kamekichi :** L'omission de `name[0].family` a éliminé l'erreur `ele-1: value cannot be empty`.
5. **Ressources legacy & Bundle :** **0 erreur** en `-tx n/a` sur `Bundle`, `Composition`, et `Patient`.

---

### 3. Liste exhaustive des erreurs restantes en mode `-tx n/a`

Les erreurs restantes en mode hors-ligne se décomposent en deux origines :

#### A. Ressources natives 🧪 : Observation groupe sanguin (`rs-blood-group-demo_<persona>`) — commun aux 3 personas (3 erreurs × 3 = 9 occurrences)

1. **Caractère invalide dans l'identifiant FHIR :**
   - *Chemin :* `Bundle.entry[*].resource.ofType(Observation).id` (`rs-blood-group-demo_haru`, `rs-blood-group-demo_kurodo`, `rs-blood-group-demo_kamekichi`)
   - *Message :* `Invalid Resource id: Invalid Characters ('rs-blood-group-demo_<persona>')`
   - *Cause :* Le caractère souligné `_` est interdit par la grammaire des ID FHIR (`[A-Za-z0-9\-\.]{1,64}`). L'ID généré dans `IpsBloodGroup.kt` concatène l'ID du profil contenant un underscore. Remplacer par un tiret `-` (ex. `rs-blood-group-demo-haru`).
2. **Cardinalité manquante sur la date effective :**
   - *Chemin :* `Bundle.entry[*].resource.ofType(Observation)`
   - *Message :* `Observation.effective[x]: minimum required = 1, but only found 0 (from http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips|1.1.0)`
   - *Cause :* Le profil IPS laboratoire impose `effective[x] 1..1` (`effectiveDateTime` ou `effectivePeriod`). `IpsBloodGroup.kt` ne renseigne pas ce champ lors de la conversion de la projection legacy.
3. **Cardinalité manquante sur l'exécutant :**
   - *Chemin :* `Bundle.entry[*].resource.ofType(Observation)`
   - *Message :* `Observation.performer: minimum required = 1, but only found 0 (from http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips|1.1.0)`
   - *Cause :* Le profil IPS laboratoire impose `performer 1..*` (référence vers un praticien, organisation ou le patient lui-même).

#### B. Ressources natives 📟 : Émetteur UDI Device (Haru uniquement, 2 erreurs)

1. **Définition NamingSystem GS1 non reconnue localement :**
   - *Chemin :* `Bundle.entry[14].resource.ofType(Device).udiCarrier[0].issuer`
   - *Message :* `No definition could be found for URL value 'http://hl7.org/fhir/NamingSystem/gs1'`
   - *Cause :* Le package IG IPS v1.1.0 local ne contient pas la définition autonome du NamingSystem GS1 sous cette URL exacte.
2. **Erreur en cascade sur DeviceUseStatement :**
   - *Chemin :* `Bundle.entry[15].resource.ofType(DeviceUseStatement).device`
   - *Message :* `Unable to find a profile match for urn:uuid:0f937d77-1cd9-3f87-a338-7d7382046d56 among choices: http://hl7.org/fhir/uv/ips/StructureDefinition/Device-uv-ips`
   - *Cause :* Conséquence directe du point 1 ci-dessus.

#### C. Ressources legacy & Structure du Bundle
- **0 erreur** (conforme à 100 %).

---

### 4. Liste exhaustive des erreurs en mode connecté `tx.fhir.org`

En mode connecté, le validateur vérifie l'existence des codes et l'exactitude des libellés affichés (`display`).

#### A. Comptage et détail des « Wrong Display Name »

- **Total « Wrong Display Name » :**
  - `demo_kurodo` : **8**
  - `demo_haru` : **7**
  - `demo_kamekichi` : **1**

| Persona | Ressource | Code & Système | Libellé actuel | Libellé officiel attendu par l'ontologie |
|---|---|---|---|---|
| Kurodo | `AllergyIntolerance` | SCT `232347008` | `Allergy to fish` | `Allergy to animal dander` *(Note : code SNOMED legacy mal mappé, 232347008 désigne les squames animales)* |
| Kurodo | `Immunization` | SCT `871876003` | `Tetanus-diphtheria-pertussis (Tdap)` | `Diphtheria and acellular pertussis and tetanus vaccine` |
| Kurodo | `Immunization` | SCT `871803007` | `Hepatitis A + B vaccine` | `Hepatitis A and Hepatitis B virus antigens only vaccine product` |
| Kurodo | `Immunization` | SCT `836378001` | `Japanese encephalitis vaccine` | `Japanese encephalitis virus antigen-containing vaccine product` |
| Kurodo | `Observation` (Groupe) | LOINC `882-1` | `ABO and Rh blood group` | `ABO and Rh group [Type] in Blood` |
| Kurodo | `Observation` (HbA1c) | LOINC `4548-4` | `Hemoglobin A1c` | `Hemoglobin A1c/Hemoglobin.total in Blood` |
| Kurodo | `Observation` (LDL) | LOINC `2089-1` | `LDL cholesterol` | `Cholesterol in LDL [Mass/volume] in Serum or Plasma` |
| Kurodo | `Observation` (Créat) | LOINC `2160-0` | `Creatinine (serum/plasma)` | `Creatinine [Mass/volume] in Serum or Plasma` |
| Haru | `AllergyIntolerance` | SCT `419474003` | `Allergy to soy` | `Allergy to mold` *(Note : code SNOMED legacy mal mappé, 419474003 désigne l'allergie aux moisissures)* |
| Haru | `Immunization` | SCT `1181000221105`| `Seasonal influenza vaccine` | `Influenza virus antigen only vaccine product` |
| Haru | `Immunization` | SCT `1801000221105` | `Pneumococcal conjugate vaccine (PCV)` | `Streptococcus pneumoniae capsular polysaccharide antigen conjugated only vaccine product` |
| Haru | `Observation` (Groupe) | LOINC `882-1` | `ABO and Rh blood group` | `ABO and Rh group [Type] in Blood` |
| Haru | `Observation` (K+) | LOINC `2823-3` | `Potassium` | `Potassium [Moles/volume] in Serum or Plasma` |
| Haru | `Observation` (Hb) | LOINC `718-7` | `Hemoglobin` | `Hemoglobin [Mass/volume] in Blood` |
| Haru | `Observation` (eGFR) | LOINC `33914-3` | `eGFR (MDRD)` | `Glomerular filtration rate [Volume Rate/Area] in Serum or Plasma by Creatinine-based formula (MDRD)/1.73 sq M` |
| Kamekichi | `Observation` (Groupe) | LOINC `882-1` | `ABO and Rh blood group` | `ABO and Rh group [Type] in Blood` |

#### B. Erreurs restantes en mode `tx` NON liées à « Wrong Display Name »

En soustrayant les « Wrong Display Name », il reste exactement les erreurs suivantes :
- **Kurodo : 3 erreurs** (les 3 erreurs de structure de l'Observation groupe sanguin : `id` avec underscore, `effective[x]` manquant, `performer` manquant).
- **Haru : 8 erreurs** :
  - **3 × Unité médicamenteuse UCUM invalide :** `Unknown code 'tab' in the CodeSystem 'http://unitsofmeasure.org' version '2.2'` sur `MedicationStatement.dosage.doseAndRate.dose.code` (le mot `tab` / comprimé n'est pas une unité UCUM métrique valide ; UCUM utilise `{tbl}` ou `1`).
  - **2 × Émetteur UDI Device :** `udiCarrier[0].issuer` = `http://hl7.org/fhir/NamingSystem/gs1` et cascade sur `DeviceUseStatement.device`.
  - **3 × Observation groupe sanguin :** `id` avec underscore, `effective[x]` manquant, `performer` manquant.
- **Kamekichi : 4 erreurs** :
  - **1 × Unité médicamenteuse UCUM invalide :** `Unknown code 'tab' in http://unitsofmeasure.org` sur `MedicationStatement`.
  - **3 × Observation groupe sanguin :** `id` avec underscore, `effective[x]` manquant, `performer` manquant.

---

## D. Non-régression sur appareil (D)

- **Lancement et navigation :**
  - Application lancée sur Pixel 9 Pro XL sans aucun crash (`AndroidRuntime:E` vide).
  - Profil de Haru Tanaka ouvert : affichage fluide et immédiat (`hydrated in 193ms`).
  - Capture d'écran enregistrée : `screenshots/c7-haru-profile.png`.
- **Intégrité des piliers sur Haru :**
  - 📝 Patient : 1
  - ⚠️ Allergies : 1
  - 💊 Médicaments : 3
  - 💉 Vaccinations : 3 (pilier actif)
  - 🏥 Procédures : 2 (pilier actif)
  - 📟 Dispositifs médicaux : 2 (pilier actif)
  - 🧪 Résultats de laboratoire : 5 (stub)
- **Validation `verify_profiles.py` :**
  - Commande exécutée avec toutes les attentes :
    `python3 qa/device/verify_profiles.py files/ --expect demo_kurodo=4 --expect-pr demo_kurodo=2 --expect-pr demo_haru=2 --expect-dv demo_haru=2 --expect-rs demo_kurodo=4 --expect-rs demo_haru=5 --expect-rs demo_kamekichi=1`
  - Résultat : **PASS — 64 checks, 0 failed**.

---

## E. Synthèse des actions recommandées pour l'architecte

1. **`IpsBloodGroup.kt` (Observation groupe sanguin) :**
   - Remplacer l'underscore dans l'id : `id = "rs-blood-group-${profileId.replace('_', '-')}"`.
   - Ajouter `effectiveDateTime` (reprendre la date du seed ou `Instant.now()`).
   - Ajouter un `performer` (ex. référence vers le sujet Patient ou organisation générique de laboratoire).
2. **`Device.udiCarrier.issuer` :**
   - Vérifier l'URI standard attendue par le validateur HL7 pour GS1 (ex. `urn:oid:2.51.1.1` ou `http://hl7.org/fhir/NamingSystem/gs1` avec déclaration de NamingSystem ou via extension UDI).
3. **Unités UCUM dans les Médicaments legacy (`demo_haru`, `demo_kamekichi`) :**
   - Remplacer `tab` par `{tbl}` ou `1` dans `unitsofmeasure.org`.
4. **Ontologie SNOMED CT legacy (`demo_kurodo`, `demo_haru`) :**
   - Mettre à jour les codes SNOMED des allergies aux poissons et au soja (qui pointaient historiquement vers les squames d'animaux et les moisissures).

---

## Fichiers publiés

- **Dossier de cycle :** `device-reports/feat-ips-18-pillars-cleanup/9feaa6c-20261001-0335/`
  - `env.txt`, `steps.md`, `verify-seed.md`, `report.md`
  - `files/demo_*.fhir.json`, `files/demo_*.json`
  - `logs/unit-tests.log`, `logs/assemble.log`, `logs/install.log`, `logs/logcat-seed.txt`, `logs/logcat-ui.txt`
  - `screenshots/c7-haru-profile.png`
  - `validator/demo_*.xml`, `validator/demo_*.log`, `validator/demo_*-tx.xml`, `validator/demo_*-tx.log`
