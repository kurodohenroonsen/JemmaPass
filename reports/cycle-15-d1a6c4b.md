# Device QA · feat/ips-18-pillars-cleanup · d1a6c4b · Pixel 9 Pro XL · Android 17 · 2026-10-01 09:35

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR
- Appareil : Pixel 9 Pro XL · Android 17 (aucun numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 73 tests JVM, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 5 ✅ · 0 ❌ · 13 ⏭

## Résultats du cycle 15

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed initial — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_kurodo.fhir.json` | 73 tests JVM passés (dont `imagingWithoutDayPreciseDateUsesTheGenericResultsProfile`). Seed vérifié : 🧪 Kurodo=4 · Haru=5 · Kamekichi=1 (💉 K4 H3 Ka0, 🏥 K2 H2 Ka0, 📟 H2 Ka0). |
| T1 à T13 | ⏭ | — | Non exécutés au cycle 15 (cycle ciblé sur la vérification des correctifs `d1a6c4b`). |
| Point 1 — Build + install + vérification seed | ✅ | `logs/assemble.log`, `logs/install.log`, `verify-seed.md` | Compilation debug, installation `-r -g` sans perte de données, seed des 3 personas conforme aux attentes. |
| Point 2 — B1 correctif ips-1 (imagerie non datée puis datée) | ✅ | `screenshots/151-b1-undated-imaging-form.png`, `152-results-list-undated-imaging.png`, `153-b1-dated-imaging-form.png`, `validator/*.txt` | Imagerie sans date : déclare `Observation-results-uv-ips` (PAS radiology), `_effectiveDateTime` avec `data-absent-reason: unknown`, 0 erreur HL7 sur les 3 personas. Modification date à « 2025-12-15 » : bascule en `Observation-results-radiology-uv-ips`, toujours 0 erreur HL7. |
| Point 3 — B2 correctif saisie virgule | ✅ | `screenshots/154-b2-comma-potassium-form.png`, `155-results-list-potassium-comma.png` | Saisie avec virgule : valeur `4,2`, réf. basse `3,5`, réf. haute `5,1`. Écran : affiche « 4.2 mmol/L » et « réf. 3.5-5.1 » sans tronquer la virgule. Bundle : `low.value = 3.5`, `high.value = 5.1`, `valueQuantity.value = 4.2`. Projection `_j.rs` : `v = "4.2"`, `rr = "3.5-5.1"`. |
| Point 4 — Suppression des 2 entrées + retour 🧪 K4 + QR texte EN | ✅ | `screenshots/156-results-back-to-4.png`, `157-qr-text-haru-en.png`, `run_device_qa.sh` | Kurodo revient à 4 résultats (`verify_profiles.py` PASS). `run_device_qa.sh` relancé en vert (73 tests JVM / 64 checks seed PASS). QR Haru EN décodé via OpenCV : 1200 octets (< 2200B plafond), section `🧪 [ RESULTS ]` complète, 0 troncature. |
| Point 5 — Logcat scrubbé & publication | ✅ | `logs/logcat-ui.txt` | Dump avec liste explicite des tags du README §4 (`JEMMA-*`, `AndroidRuntime:E`). Filtré par `scrub_logcat.py` (126 lignes conservées, 0 erreur `AndroidRuntime:E`). |

---

## Vérifications détaillées

### Point 2 — Observation Imagerie « Echographie abdominale » sans date (B1)

Extrait brut de `pull-b1-undated/profiles/demo_kurodo.fhir.json` (Entry 15) :
```json
{
  "resourceType": "Observation",
  "id": "b773aba1-8192-488e-8114-db5097168f75",
  "meta": {
    "profile": [
      "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-uv-ips"
    ]
  },
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "imaging",
          "display": "Imaging"
        }
      ],
      "text": "Imaging"
    }
  ],
  "code": {
    "text": "Echographie abdominale"
  },
  "subject": {
    "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
  },
  "performer": [
    {
      "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1",
      "display": "Patient-reported"
    }
  ],
  "_effectiveDateTime": {
    "extension": [
      {
        "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
        "valueCode": "unknown"
      }
    ]
  },
  "valueString": "Normal"
}
```

- `meta.profile` = `http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-uv-ips` (profil générique, PAS de contrainte `ips-1`).
- `effectiveDateTime` absent, représenté par l'élément ombré `_effectiveDateTime` avec l'extension `data-absent-reason: unknown`.

#### Modification de la date en « 2025-12-15 »

Extrait brut de `pull-b1-dated/profiles/demo_kurodo.fhir.json` (Entry 15) :
```json
{
  "resourceType": "Observation",
  "id": "b773aba1-8192-488e-8114-db5097168f75",
  "meta": {
    "profile": [
      "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-radiology-uv-ips"
    ]
  },
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "imaging",
          "display": "Imaging"
        }
      ],
      "text": "Imaging"
    }
  ],
  "code": {
    "text": "Echographie abdominale"
  },
  "subject": {
    "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
  },
  "performer": [
    {
      "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1",
      "display": "Patient-reported"
    }
  ],
  "effectiveDateTime": "2025-12-15",
  "valueString": "Normal"
}
```
- Le profil est automatiquement promu en `Observation-results-radiology-uv-ips`.
- `effectiveDateTime` est présent avec une précision au jour (`2025-12-15`), satisfaisant la contrainte `ips-1`.

---

### Point 3 — Observation Potassium avec bornes saisies avec virgule (B2)

Extrait brut de `pull-b2-potassium/profiles/demo_kurodo.fhir.json` (Entry 16) :
```json
{
  "resourceType": "Observation",
  "id": "d79c426a-35cf-4f5e-99f3-e63cb6c59085",
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
          "code": "laboratory",
          "display": "Laboratory"
        }
      ],
      "text": "Laboratory"
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "2823-3",
        "display": "Potassium [Moles/volume] in Serum or Plasma"
      }
    ],
    "text": "Potassium"
  },
  "subject": {
    "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
  },
  "performer": [
    {
      "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1",
      "display": "Patient-reported"
    }
  ],
  "referenceRange": [
    {
      "low": {
        "value": 3.5,
        "unit": "mmol/L",
        "system": "http://unitsofmeasure.org",
        "code": "mmol/L"
      },
      "high": {
        "value": 5.1,
        "unit": "mmol/L",
        "system": "http://unitsofmeasure.org",
        "code": "mmol/L"
      }
    }
  ],
  "_effectiveDateTime": {
    "extension": [
      {
        "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
        "valueCode": "unknown"
      }
    ]
  },
  "valueQuantity": {
    "value": 4.2,
    "unit": "mmol/L",
    "system": "http://unitsofmeasure.org",
    "code": "mmol/L"
  }
}
```

Extrait de la projection `_j.rs` dans `pull-b2-potassium/profiles/demo_kurodo.json` :
```json
{
  "c": "2823-3",
  "d_display": "Potassium",
  "v": "4.2",
  "u": "mmol/L",
  "rr": "3.5-5.1"
}
```

- Affichage écran : `4.2 mmol/L`, `réf. 3.5-5.1`.
- Bundle FHIR : `referenceRange[0].low.value = 3.5`, `referenceRange[0].high.value = 5.1`, `valueQuantity.value = 4.2`.
- Projection compacte `_j.rs` : `"v": "4.2"`, `"rr": "3.5-5.1"`.

---

## Sorties du validateur HL7 officiel (`org.hl7.fhir.validator_cli.jar` v6.10.4)

Options : `-version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a`

| Fichier validé | Erreurs | Avertissements | Informations | Statut |
|---|---|---|---|---|
| `demo_kurodo.fhir.json` (avec imagerie non datée) | **0** | 25 | 0 | **PASS** |
| `demo_haru.fhir.json` (seed Haru) | **0** | 38 | 0 | **PASS** |
| `demo_kamekichi.fhir.json` (seed Kamekichi) | **0** | 28 | 0 | **PASS** |
| `demo_kurodo.fhir.json` (avec imagerie datée 2025-12-15) | **0** | 27 | 0 | **PASS** |

Total erreurs : **0**. Les seuls avertissements correspondent à l'absence de serveur de terminologie distant (`-tx n/a`).

---

## Décodage du QR texte Haru (EN)

Décodé par OpenCV (`cv2.QRCodeDetector()` / `QRCodeDetectorAruco()`) depuis `screenshots/157-qr-text-haru-en.png` :
- **Taille encodée** : 1 200 octets (plafond 2 200 octets)
- **Nombre de frames** : 1 frame
- **Troncature** : 0 (toutes les sections sont intactes)

```text
🏥 === JEMMA CLINICAL SUMMARY (EN) ===

👤 [ PATIENT ]
 🔹 Haru Tanaka (F)
 📅 Birth: 1946-02-08
 🩸 Blood: O+
 🗣 Language: ja-JP
 📍 Address: Aomori, Japan
 🆔 ID: JP-12345678

⚠️ [ ALLERGIES ]
  ▪️ Allergy to soy protein (LOW)

💊 [ MEDICATIONS ]
  ▪️ Fexofenadine 1tab Allegra FX (fexofenadine 60mg)
  ▪️ Dextromethorphan 1tab Medicon Pro (dextromethorphan)
  ▪️ Furosemide 1tab Furosemide 20mg

💉 [ IMMUNIZATIONS ]
  ▪️ Seasonal influenza vaccine — 2025-10-14
  ▪️ COVID-19 mRNA vaccine — 2024-11-02 · #7
  ▪️ Pneumococcal conjugate vaccine (PCV) — 2021-04-06 · #1

🏥 [ PROCEDURES ]
  ▪️ Coronary artery bypass graft — 2015-09-02
  ▪️ Cesarean section — 1975

📟 [ MEDICAL DEVICES ]
  ▪️ Cardiac pacemaker — 2021-03-15
  ▪️ Hearing aid — 2019-06

🧪 [ RESULTS ]
  ▪️ Potassium: 4.1 mmol/L — 2026-02-10
  ▪️ Hemoglobin: 11.8 g/dL (L) — 2026-02-10
  ▪️ eGFR (MDRD): 48 mL/min/{1.73_m2} (L) — 2026-02-10
  ▪️ Chest X-ray: Mild cardiomegaly, no pleural effusion — 2025-12-03
  ▪️ ABO and Rh blood group: O+

✅ JEMMA on-device · `_j 1.2`
```

---

## État des bugs

### B1 — Erreur HL7 contrainte `ips-1` sur imagerie sans date
- **Sévérité** : Majeur
- **Statut** : **CORRIGÉ** en `d1a6c4b`
- **Vérification** : Une observation d'imagerie sans date (ou date partielle) déclare `Observation-results-uv-ips` au lieu de `Observation-results-radiology-uv-ips`. 0 erreur relevée par le validateur HL7 officiel sur les 3 personas. Dès qu'une date précise au jour est renseignée, le profil bascule vers la radiologie sans erreur.

### B2 — Saisie des bornes de référence avec virgule tronquée
- **Sévérité** : Mineur
- **Statut** : **CORRIGÉ** en `d1a6c4b`
- **Vérification** : `inputType="text|textNoSuggestions"` et `digits="0123456789.,-"` sur les champs bornes basse et haute. La saisie avec virgule (`3,5` et `5,1`) conserve les décimales et normalise correctement en `3.5` et `5.1` à l'écran, dans le Bundle FHIR et dans la projection `_j.rs`.

---

## Captures d'écran

- `screenshots/151-b1-undated-imaging-form.png` : Formulaire d'ajout « Echographie abdominale » sans date (valeur Normal, imagerie).
- `screenshots/152-results-list-undated-imaging.png` : Liste des résultats de Kurodo à 5 résultats avec l'échographie sans date.
- `screenshots/153-b1-dated-imaging-form.png` : Formulaire d'édition de l'échographie avec la date sélectionnée au 15/12/2025.
- `screenshots/154-b2-comma-potassium-form.png` : Formulaire d'ajout du Potassium avec saisie à virgule (`4,2`, `3,5`, `5,1`).
- `screenshots/155-results-list-potassium-comma.png` : Liste des résultats montrant « 4.2 mmol/L » et « réf. 3.5-5.1 ».
- `screenshots/156-results-back-to-4.png` : Liste des résultats de Kurodo revenue à 4 résultats après suppression des 2 entrées de test.
- `screenshots/157-qr-text-haru-en.png` : Écran du QR code texte Haru en langue anglaise (1 200 octets, 1 frame).

## Fichiers publiés

`env.txt`, `steps.md`, `verify-seed.md`, `report.md`, `files/demo_*.json`, `logs/*.txt|log`, `screenshots/*.png`, `validator/*.txt|log`
