# Device QA · feat/ips-18-pillars-cleanup · 8fb6d1d · Pixel 9 Pro XL · Android 17 · 2026-10-01 07:55

- Exécutant : Antigravity · Hôte : macOS · Langue appareil : FR
- Appareil : Pixel 9 Pro XL · Android 17 (aucun numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 72 tests JVM, 0 failed (`logs/unit-tests.log`)
- Verdict global : **FAIL** — 9 ✅ · 1 ❌ · 13 ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_kurodo.fhir.json` | 72 tests JVM passés, seed vérifié : 🧪 Kurodo=4 · Haru=5 · Kamekichi=1 |
| T1 fiche Kurodo (section + tuile badge 4) | ⏭ | — | Non exécuté au cycle 14 (focus pilier Résultats) |
| T2 création (picker, date, dose, lot) | ⏭ | — | Non exécuté au cycle 14 |
| T3 édition + suppression vaccins | ⏭ | — | Non exécuté au cycle 14 |
| T4 édition allergie conserve les vaccins | ⏭ | — | Non exécuté au cycle 14 |
| T5 QR texte EN/FR/JA | ⏭ | — | Remplacé par T14.8 |
| T6 FHIR + validateur (bonus) | ⏭ | — | Remplacé par étape 5 |
| T7 Haru / Kamekichi (lecture seule) | ⏭ | — | Couvert par T14.1 et non-régression |
| T8 chemins alternatifs vaccins | ⏭ | — | Non exécuté au cycle 14 |
| T9 chemins d'erreur vaccins | ⏭ | — | Non exécuté au cycle 14 |
| T10 procédures — fiche + liste + création + édition + suppression | ⏭ | — | Non exécuté au cycle 14 |
| T11 dispositifs — fiche + liste + création (UDI) + édition + suppression | ⏭ | — | Non exécuté au cycle 14 |
| T12 chemins alternatifs + erreurs (procédures et dispositifs) | ⏭ | — | Non exécuté au cycle 14 |
| T13 canaux — QR texte 🏥/📟 EN/FR/JA + non-régression | ⏭ | — | Non exécuté au cycle 14 |
| T14.1 Fiche + liste Haru | ✅ | `screenshots/140-detail-haru-results.png`, `141-results-list.png` | Section « 🧪 RÉSULTATS (5) », tuile active avec badge 5, recycler avec 5 résultats et valeurs alignées à droite. |
| T14.2 Groupe sanguin dérivé | ✅ | `screenshots/142-results-derived-toast.png` | Tap sur « Groupe sanguin ABO / Rhésus · O+ » déclenche le toast « Le groupe sanguin vient du pilier Patient — modifie-le là », aucun formulaire ouvert. |
| T14.3 Création numérique (Kurodo Potassium) | ✅ | `screenshots/143-results-create-potassium.png`, `verify-t14-3.md` | FAB → LOINC 2823-3, unité `mmol/L`, valeur `5,9`, interprétation « 🔺 Élevé », réf `3.5-5.1`, date passée. Profil passe à 5 résultats. Observation conforme. |
| T14.4 Texte libre + imagerie | ✅ | `screenshots/144-results-create-imaging.png`, `verify-t14-4.md` | FAB → `Echographie abdominale`, catégorie `Imagerie`, valeur `Normale`. Profil passe à 6 résultats (`Observation-results-radiology-uv-ips`). |
| T14.5 Erreurs formulaire | ✅ | `screenshots/145-results-errors.png` | Save vide → erreur inline sur le nom. Test choisi sans valeur → erreur inline « Saisis la valeur du résultat ». Réf. basse 9 / haute 3 → erreur « La borne basse ne peut pas dépasser la borne haute ». |
| T14.6 Test codé (ABO/Rh) + absence doublon | ✅ | `screenshots/146-results-blood-group.png` | FAB → « Groupe sanguin ABO / Rhésus » bascule en sélecteur codé 🩸. Un seul 882-1 dans le Bundle de Kurodo (aucun doublon). |
| T14.7 Édition / suppression (retour à 4) | ✅ | `screenshots/147-results-back-to-4.png`, `verify-t14-7.md` | Suppression de l'échographie (appui long) et du potassium (bouton formulaire). Kurodo revient à 4 résultats. |
| T14.8 QR texte Haru EN/FR/JA | ✅ | `screenshots/148-qr-results-haru.png`, `148-qr-results-haru-en.png`, `148-qr-results-haru-fr.png`, `148-qr-results-haru-ja.png` | Décodage OpenCV réussi pour EN (1200 octets), FR (1260 octets) et JA (1345 octets via QRCodeDetectorAruco). Section résultats présente, ordonnée après 📟. 0 troncature (< 2200B). |
| Étape 5 Validateur HL7 officiel | ❌ | `validator/demo_kurodo_t14_4.txt` | 1 erreur HL7 détectée sur l'Observation sans date créée au point 4 : échec de contrainte `ips-1` sur `_effectiveDateTime` (voir Bug B1). |
| Non-régression fiches 3 personas | ✅ | `logs/logcat-ui.txt` | Fiches Kurodo (💉 4, 🏥 2, 📟 0, 🧪 4), Haru (💉 3, 🏥 2, 📟 2, 🧪 5), Kamekichi (💉 0, 🏥 0, 📟 0, 🧪 1). 0 crash applicatif. |

---

## Citations des ressources FHIR et vérifications

### Point 3 — Observation Potassium créée (`pull-t14-3/profiles/demo_kurodo.fhir.json`)

```json
{
  "resourceType": "Observation",
  "id": "31ac7485-496d-4ce9-a031-0ba33784ef4f",
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
  "interpretation": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
          "code": "H",
          "display": "High"
        }
      ],
      "text": "High"
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
  "effectiveDateTime": "2026-09-15",
  "valueQuantity": {
    "value": 5.9,
    "unit": "mmol/L",
    "system": "http://unitsofmeasure.org",
    "code": "mmol/L"
  }
}
```

### Point 4 — Observation Imagerie créée (`pull-t14-4/profiles/demo_kurodo.fhir.json`)

```json
{
  "id": "e031cfec-0551-4a2b-ac92-f488eb1865b9",
  "meta": {
    "profile": [
      "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-radiology-uv-ips"
    ]
  },
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
  "valueString": "Normale"
}
```

- `code` : `{"text": "Echographie abdominale"}` (sans coding LOINC/SNOMED)
- `valueString` : `"Normale"`
- `category` : code `imaging`, display `Imaging`, système `http://terminology.hl7.org/CodeSystem/observation-category`
- `meta.profile` : `["http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-radiology-uv-ips"]`

### Point 6 — Décompte Observation 882-1 dans `demo_kurodo.fhir.json`

- Nombre d'occurrences d'Observation avec code `882-1` : **1 seule** (`rs-blood-group-demo-kurodo`).
- Aucun doublon entre le groupe sanguin dérivé et les résultats.

---

## Point 8 — Décodage des QR codes texte Haru avec OpenCV

### QR EN (`screenshots/148-qr-results-haru-en.png` · 1200 octets / 2200)

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
*Taille : 1200 octets (plafond 2200) · Troncature : aucune.*

### QR FR (`screenshots/148-qr-results-haru-fr.png` · 1260 octets / 2200)

```text
🏥 === JEMMA CLINICAL SUMMARY (FR) ===

👤 [ PATIENT ]
 🔹 Haru Tanaka (F)
 📅 Naissance: 1946-02-08
 🩸 Groupe: O+
 🗣 Langue: ja-JP
 📍 Adresse: Aomori, Japan
 🆔 ID: JP-12345678

⚠️ [ ALLERGIES ]
  ▪️ Allergie aux protéines de soja (LOW)

💊 [ MÉDICAMENTS ]
  ▪️ Fexofénadine 1tab Allegra FX (fexofenadine 60mg)
  ▪️ Dextrométhorphane 1tab Medicon Pro (dextromethorphan)
  ▪️ Furosémide 1tab Furosemide 20mg

💉 [ VACCINATIONS ]
  ▪️ Vaccin grippe saisonnière — 2025-10-14
  ▪️ Vaccin COVID-19 (ARNm) — 2024-11-02 · #7
  ▪️ Vaccin pneumocoque conjugué (PCV) — 2021-04-06 · #1

🏥 [ INTERVENTIONS ]
  ▪️ Pontage coronarien — 2015-09-02
  ▪️ Césarienne — 1975

📟 [ DISPOSITIFS MÉDICAUX ]
  ▪️ Stimulateur cardiaque (pacemaker) — 2021-03-15
  ▪️ Appareil auditif — 2019-06

🧪 [ RÉSULTATS ]
  ▪️ Potassium (kaliémie): 4.1 mmol/L — 2026-02-10
  ▪️ Hémoglobine: 11.8 g/dL (L) — 2026-02-10
  ▪️ DFG estimé (MDRD): 48 mL/min/{1.73_m2} (L) — 2026-02-10
  ▪️ Chest X-ray: Mild cardiomegaly, no pleural effusion — 2025-12-03
  ▪️ Groupe sanguin ABO / Rhésus: O+

✅ JEMMA on-device · `_j 1.2`
```
*Taille : 1260 octets (plafond 2200) · Troncature : aucune.*

### QR JA (`screenshots/148-qr-results-haru-ja.png` · 1345 octets / 2200)

```text
🏥 === JEMMA 臨床サマリー (JA) ===

👤 [ 患者 ]
 🔹 Haru Tanaka (女)
 📅 生年月日: 1946-02-08
 🩸 血液型: O+
 🗣 言語: ja-JP
 📍 住所: Aomori, Japan
 🆔 ID: JP-12345678

⚠️ [ アレルギー ]
  ▪️ 大豆タンパク質アレルギー (LOW)

💊 [ 服薬 ]
  ▪️ フェキソフェナジン 1tab Allegra FX (fexofenadine 60mg)
  ▪️ デキストロメトルファン 1tab Medicon Pro (dextromethorphan)
  ▪️ フロセミド 1tab Furosemide 20mg

💉 [ 予防接種 ]
  ▪️ 季節性インフルエンザワクチン — 2025-10-14
  ▪️ 新型コロナワクチン（mRNA） — 2024-11-02 · #7
  ▪️ 肺炎球菌結合型ワクチン（PCV） — 2021-04-06 · #1

🏥 [ 処置・手術歴 ]
  ▪️ 冠動脈バイパス術 — 2015-09-02
  ▪️ 帝王切開 — 1975

📟 [ 医療機器 ]
  ▪️ 心臓ペースメーカー — 2021-03-15
  ▪️ 補聴器 — 2019-06

🧪 [ 検査結果 ]
  ▪️ カリウム: 4.1 mmol/L — 2026-02-10
  ▪️ ヘモグロビン: 11.8 g/dL (L) — 2026-02-10
  ▪️ 推算糸球体濾過量（eGFR, MDRD）: 48 mL/min/{1.73_m2} (L) — 2026-02-10
  ▪️ Chest X-ray: Mild cardiomegaly, no pleural effusion — 2025-12-03
  ▪️ ABO・Rh血液型: O+

✅ JEMMA on-device · `_j 1.2`
```
*Taille : 1345 octets (plafond 2200) · Troncature : aucune. Décodé avec `cv2.QRCodeDetectorAruco()`.*

---

## Étape 5 — Rapport du validateur HL7 officiel

Commande exécutée :
```bash
java -jar /tmp/fhir-validator/validator_cli.jar \
  qa/device/out/8fb6d1d-20261001-0644/pull-t14-4/profiles/demo_kurodo.fhir.json \
  -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a
```

Résultat : **1 erreur, 14 avertissements (warnings), 4 notes**.

Détail de l'erreur :
```
Error @ Bundle.entry[16].resource/*Observation/e031cfec-0551-4a2b-ac92-f488eb1865b9*/.effective.ofType(dateTime) (line 945, col18): Constraint failed: ips-1: 'Datetime must be at least to day.', validating against International Patient Summary Implementation Guide v1.1.0
```

---

## Bugs

### B1 — Erreur HL7 ips-1 sur Observation sans date (`_effectiveDateTime` avec `data-absent-reason: unknown`)
- **Sévérité** : Majeur (non-conformité stricte au validateur officiel HL7 IPS)
- **Étapes de reproduction** :
  1. Ouvrir le profil Kurodo Henro.
  2. Ajouter un résultat sans renseigner de date (ex. point 4 : texte libre `Echographie abdominale`, imagerie).
  3. Enregistrer.
  4. Extraire `demo_kurodo.fhir.json` et lancer `validator_cli.jar -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a`.
- **Attendu / Observé** :
  - *Attendu* : 0 erreur au validateur HL7.
  - *Observé* : Échec de la contrainte `ips-1` (`Datetime must be at least to day.`) sur l'élément `effectiveDateTime`. Le codec génère un élément `_effectiveDateTime` avec extension `http://hl7.org/fhir/StructureDefinition/data-absent-reason` = `unknown`. Le validateur HL7 évalue la contrainte `ips-1` sur le type `dateTime` et échoue car la valeur primitive de la date est absente.
- **Preuves** : `validator/demo_kurodo_t14_4.txt` (extrait ci-dessus).

### B2 — Perte de la virgule décimale lors de la saisie des bornes de référence
- **Sévérité** : Mineur (UX)
- **Étapes de reproduction** :
  1. Dans `ResultFormBottomSheet`, saisir `3,5` dans `result_form_ref_low` ou `5,1` dans `result_form_ref_high`.
- **Attendu / Observé** :
  - *Attendu* : La valeur `3,5` est acceptée et normalisée en `3.5` par `IpsDecimal.normalize`.
  - *Observé* : `android:inputType="numberDecimal|numberSigned"` dans le layout `bottom_sheet_result_form.xml` applique un filtre Android qui rejette le caractère virgule `,` en locale standard, transformant silencieusement `3,5` en `35` et `5,1` en `51`.

---

## Fichiers publiés
`env.txt`, `steps.md`, `verify-seed.md`, `verify-t14-3.md`, `verify-t14-4.md`, `verify-t14-7.md`, `files/demo_*.json`, `logs/*.txt|log`, `screenshots/*.png`, `validator/*.txt`
