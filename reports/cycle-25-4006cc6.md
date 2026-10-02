# Rapport d'Exécution Cycle 25 — SD Wave 1 (Free-text, Series, Contacts SD-19, SD-11, SD-23)

- **Date** : 2026-10-02
- **Commit testé** : `4006cc6`
- **Appareil** : Google Pixel 9 Pro XL (Android 17)
- **Dossier de sortie** : `qa/device/out/4006cc6-20261002-1759`
- **Statut global** : **SUCCÈS TOTAL (PASS 113/113 checks seed, 0 failed, 0 erreurs HL7 FHIR)**

---

## 1. Tableaux de Verdicts par Bloc

### BLOC A — Seed 18 piliers sur appareil
| Vérification | Attendu | Obtenu | Verdict |
|---|---|---|---|
| Invariants `verify_profiles` | 113 checks PASS | 113 checks, 0 failed | ✅ PASS |
| Profils seed restaurés | 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2, 🧪 K4 H5 Ka1, 📜 K2 H2 Ka0, 🩺 K1 H2 Ka3, 🤰 K0 H3 Ka0, ♿ K0 H2 Ka0 | Identique au seed | ✅ PASS |

### BLOC B — Validation HL7 FHIR Seed
| Profil | Erreurs HL7 FHIR | Avertissements | Verdict |
|---|---|---|---|
| `demo_haru` | 0 | 50 | ✅ PASS |
| `demo_kamekichi` | 0 | 31 | ✅ PASS |
| `demo_kurodo` | 0 | 28 | ✅ PASS |

### BLOC C — SD-19 Kamekichi Contacts d'Urgence
| Point | Action | Preuve / Observation | Verdict |
|---|---|---|---|
| **SD-19** | QR texte Kamekichi EN | Ligne 29 : `▪️ Kurodo Henro (unrelated friend) +32 2 000 00 02` · Rôle FRND + numéro affichés · `screenshots/243-qr-kamekichi-text-en.png` · `qr/kamekichi-text-en.txt` (884 octets) | ✅ PASS |

### BLOC D — Médicament en Saisie Libre (« Tisane maison »)
| Point | Action | Preuve / Observation | Verdict |
|---|---|---|---|
| **D.1** | Dialogue d'avertissement | « ⚠ Contrôle de sécurité incomplet » · `screenshots/260-freetext-dialog.png` | ✅ PASS |
| **D.2** | Bandeau profil ambre | « ⓘ Contrôles de sécurité incomplets — 1 élément n'a pas pu être vérifié » · `screenshots/261-freetext-profile-badge.png` | ✅ PASS |
| **D.3** | FHIR Bundle extrait | `Medication.code.text = "Tisane maison"` sans aucun `coding` · `json/demo_haru.fhir.json` | ✅ PASS |
| **D.4** | QR texte FR | Ligne 20 : `▪️ Tisane maison` · `screenshots/262-freetext-qr-fr.png` · `qr/haru-freetext-fr.txt` (1788 octets) | ✅ PASS |
| **D.5** | Nettoyage / Suppression | Tisane maison supprimée · Retour à 3 médicaments · Bandeau ambre disparu | ✅ PASS |

### BLOC E — Sélecteur Médicament Recherche « ibu »
| Point | Action | Preuve / Observation | Verdict |
|---|---|---|---|
| **E.1** | Compteur de résultats | « 20 résultat(s) » reflète uniquement les entrées codées · Entrée tel quel non comptée | ✅ PASS |
| **E.2** | Résultats codés avec doses | Ex: `ibuprofen 50 MG · 💊 1.2 g · oral` affichés avec doses WHO ATC/DDD | ✅ PASS |
| **E.3** | Entrée libre en fin de liste | `➕ Ajouter « ibu » tel quel` affichée en bas de liste · `screenshots/263-drug-picker-ibu.png` | ✅ PASS |

### BLOC F — Série Vaccinale Sans Numéro de Dose (Kurodo)
| Point | Action | Preuve / Observation | Verdict |
|---|---|---|---|
| **F.1** | Saisie « TestSeriesVaccine » | Dose vide, série = 3 | ✅ PASS |
| **F.2** | FHIR Bundle extrait | `protocolApplied` : `doseNumberString = "unknown"`, `seriesDosesPositiveInt = 3` · `occurrenceString = "unknown"` · `json/demo_kurodo.fhir.json` | ✅ PASS |
| **F.3** | Nettoyage / Suppression | TestSeriesVaccine supprimé · Retour à 4 vaccinations | ✅ PASS |

### BLOC G — Haru QR Texte FR et JA (Priorités SD-11)
| Langue | Fichier décodé | Taille (max 1800 octets) | Marqueur ✂️ | Capture | Verdict |
|---|---|---|---|---|---|
| **Haru (FR)** | `qr/haru-text-fr.txt` | **1764 octets** | Absent (non tronqué) | `screenshots/241-qr-haru-text-fr.png` | ✅ PASS |
| **Haru (JA)** | `qr/haru-text-ja.txt` | **1782 octets** | Absent (non tronqué) | `screenshots/242-qr-haru-text-ja.png` | ✅ PASS |

### BLOC H — Kurodo Résultat Valeur Très Grande (SD-23)
| Point | Action | Preuve / Observation | Verdict |
|---|---|---|---|
| **H.1** | Saisie 123456789012345678.123 | Examen TestLargeResult créé avec valeur 123456789012345678.123 | ✅ PASS |
| **H.2** | FHIR Bundle extrait | `json/demo_kurodo_large_result.fhir.json` : `valueQuantity.value = 1.2345678901234568e+17` (pas de crash, formatage grand nombre) | ✅ PASS |
| **H.3** | Nettoyage / Suppression | TestLargeResult supprimé · Retour à 4 résultats | ✅ PASS |

### BLOC I — Validation HL7 FHIR Suites Dérivées
| Suite | Erreurs HL7 FHIR | Avertissements | Fichier de résumé | Verdict |
|---|---|---|---|---|
| `files-freetext` | 0 | H 52, Ka 31, Ku 28 | `validator/summary-freetext.txt` | ✅ PASS |
| `files-series` | 0 | H 50, Ka 31, Ku 29 | `validator/summary-series.txt` | ✅ PASS |

### BLOC J — Logs & Stabilité
- **Logcat épuré** : `logs/logcat-ui.txt` (1026 lignes, 137361 octets).
- **Crashs / Exceptions** : 0 crash AndroidRuntime, 0 exception non interceptée.

---

## 2. Inventaire des Preuves (Chemins & Tailles Exactes en Octets)

### Captures d'Écran (Screenshots)
- `screenshots/241-qr-haru-text-fr.png` : 345418 octets
- `screenshots/242-qr-haru-text-ja.png` : 353471 octets
- `screenshots/243-qr-kamekichi-text-en.png` : 215748 octets
- `screenshots/260-freetext-dialog.png` : 156509 octets
- `screenshots/261-freetext-profile-badge.png` : 186320 octets
- `screenshots/262-freetext-qr-fr.png` : 342672 octets
- `screenshots/263-drug-picker-ibu.png` : 180332 octets

### Décodages QR Texte
- `qr/haru-freetext-fr.txt` : 1788 octets
- `qr/haru-text-fr.txt` : 1764 octets
- `qr/haru-text-ja.txt` : 1782 octets
- `qr/kamekichi-text-en.txt` : 884 octets

### Extraits JSON FHIR
- `json/demo_haru.fhir.json` : 41502 octets
- `json/demo_kurodo.fhir.json` : 28917 octets
- `json/demo_kurodo_large_result.fhir.json` : 29741 octets

### Résumés des Validations HL7
- `validator/summary.txt` : 951 octets
- `validator/summary-freetext.txt` : 901 octets
- `validator/summary-series.txt` : 887 octets

### Logs
- `logs/logcat-ui.txt` : 137361 octets (1026 lignes)

---

## 3. Contenus Bruts (Règle 8)

### `qr/haru-freetext-fr.txt`
```
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
  ▪️ Tisane maison

🩺 [ CONDITIONS ]
  ▪️ Défaillance cardiaque
  ▪️ Maladie rénale chronique stade 3

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

📜 [ ANTÉCÉDENTS MÉDICAUX ]
  ▪️ Infarctus du myocarde — 2015-08-27 → 2015-09
  ▪️ Tuberculose — 1962 → 1963

🤰 [ GROSSESSES ]
  ▪️ Naissances (total): 2 — 2026-02-10
  ▪️ Naissances vivantes: 2 — 2026-02-10
  ▪️ Naissances à terme: 2 — 2026-02-10

♿ [ AUTONOMIE ]
  ▪️ Perte d'audition — 2019
  ▪️ Walks with a cane outdoors — 2021

✅ JEMMA on-device · `_j 1.2`
```

### `qr/haru-text-fr.txt`
```
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

🩺 [ CONDITIONS ]
  ▪️ Défaillance cardiaque
  ▪️ Maladie rénale chronique stade 3

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

📜 [ ANTÉCÉDENTS MÉDICAUX ]
  ▪️ Infarctus du myocarde — 2015-08-27 → 2015-09
  ▪️ Tuberculose — 1962 → 1963

🤰 [ GROSSESSES ]
  ▪️ Naissances (total): 2 — 2026-02-10
  ▪️ Naissances vivantes: 2 — 2026-02-10
  ▪️ Naissances à terme: 2 — 2026-02-10

♿ [ AUTONOMIE ]
  ▪️ Perte d'audition — 2019
  ▪️ Walks with a cane outdoors — 2021

✅ JEMMA on-device · `_j 1.2`
```

### `qr/haru-text-ja.txt`
```
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

🩺 [ 病態 ]
  ▪️ 心不全
  ▪️ 慢性腎臓病第３期

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

📜 [ 既往歴 ]
  ▪️ 心筋梗塞 — 2015-08-27 → 2015-09
  ▪️ Tuberculosis — 1962 → 1963

🤰 [ 妊娠歴 ]
  ▪️ 出産回数（合計）: 2 — 2026-02-10
  ▪️ 生産数: 2 — 2026-02-10
  ▪️ 正期産数: 2 — 2026-02-10

♿ [ 生活機能 ]
  ▪️ 難聴 — 2019
  ▪️ Walks with a cane outdoors — 2021

✅ JEMMA on-device · `_j 1.2`
```

### `qr/kamekichi-text-en.txt`
```
🏥 === JEMMA CLINICAL SUMMARY (EN) ===

👤 [ PATIENT ]
 🔹 Kamekichi  (M)
 📅 Birth: 2000-05-20
 🩸 Blood: B+
 🗣 Language: ja-JP
 📍 Address: 75 Avenue Louise, Bruxelles, Belgique
 🆔 ID: BE-570815-987-65

⚠️ [ ALLERGIES ]
  ▪️ Latex allergy (LOW)
  ▪️ Allergy to penicillin (HIGH)
  ▪️ Allergy to peanut (HIGH)

💊 [ MEDICATIONS ]
  ▪️ Bisoprolol 1tab Bisoprolol 2.5mg
  ▪️ Warfarin 5mg Warfarin 5mg
  ▪️ Ibuprofen 400mg Ibuprofen 400mg
  ▪️ Sildenafil 50mg Sildenafil 50mg
  ▪️ Isosorbide dinitrate 20mg Isosorbide Dinitrate 20mg

🩺 [ CONDITIONS ]
  ▪️ Essential hypertension
  ▪️ Atrial fibrillation
  ▪️ Angina

☎️ [ CONTACTS ]
  ▪️ Kurodo Henro (unrelated friend) +32 2 000 00 02

🧪 [ RESULTS ]
  ▪️ ABO and Rh blood group: B+

✅ JEMMA on-device · `_j 1.2`
```

### `json/demo_haru.fhir.json`
```
{
  "resourceType": "Bundle",
  "identifier": {
    "system": "urn:ietf:rfc:3986",
    "value": "urn:uuid:243a6333-028d-3926-a461-0566a8eb442f"
  },
  "type": "document",
  "timestamp": "2026-10-02T16:04:42.407Z",
  "entry": [
    {
      "fullUrl": "urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947",
      "resource": {
        "resourceType": "Composition",
        "status": "final",
        "type": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "60591-5",
              "display": "Patient summary Document"
            }
          ]
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "date": "2026-10-02T16:04:42.382Z",
        "author": [
          {
            "display": "JEMMA Pass on-device"
          }
        ],
        "title": "International Patient Summary",
        "confidentiality": "N",
        "section": [
          {
            "title": "Allergies",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "48765-2"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:ad91354c-9fce-3a4f-813f-bf4ba8ff19d6"
              }
            ]
          },
          {
            "title": "Medications",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "10160-0"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:359c2770-4bb9-312f-a3df-472535720d70"
              },
              {
                "reference": "urn:uuid:f4fb2770-b898-3bbb-ae00-5d5a79988462"
              },
              {
                "reference": "urn:uuid:df0e3c0e-0daf-3b11-9d37-d6a1fba38253"
              },
              {
                "reference": "urn:uuid:16aed101-d56e-37ba-855c-05da1583d6eb"
              }
            ]
          },
          {
            "title": "Problems",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11450-4",
                  "display": "Problem list - Reported"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:ec2d139b-f5b6-352e-9061-db082fa3fe6c"
              },
              {
                "reference": "urn:uuid:2c811ef7-4f33-3334-adda-4da21a1e2a1f"
              }
            ]
          },
          {
            "title": "History of Past Illness",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11348-0",
                  "display": "History of Past illness note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:0dc3b078-cf8a-3248-9a64-559b279dcf92"
              },
              {
                "reference": "urn:uuid:91e6ba24-67ec-3674-a7bf-7dad30c769bc"
              }
            ]
          },
          {
            "title": "History of Pregnancy",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "10162-6",
                  "display": "History of pregnancies Narrative"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:68fa45ea-8251-374e-b3a3-592e837b3bb3"
              },
              {
                "reference": "urn:uuid:3444b98a-1c67-3028-8c18-7a343041fc4e"
              },
              {
                "reference": "urn:uuid:ad863416-bfd6-3ce5-90bd-8dc4dec8fca2"
              }
            ]
          },
          {
            "title": "Functional Status",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "47420-5",
                  "display": "Functional status assessment note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:156e3f5b-5324-3ea1-a3cd-4aa31d4295a5"
              },
              {
                "reference": "urn:uuid:881107ab-98ee-34d7-ae1c-4f95dffc1637"
              }
            ]
          },
          {
            "title": "Immunizations",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11369-6",
                  "display": "History of Immunization note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:ac08dfd8-d2af-32c2-bf9e-da86059ab58d"
              },
              {
                "reference": "urn:uuid:e3e461be-fe38-3090-9beb-ee688e01296a"
              },
              {
                "reference": "urn:uuid:d2321d4b-35cd-30fd-a034-a3997af51c52"
              }
            ]
          },
          {
            "title": "History of Procedures",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "47519-4",
                  "display": "History of Procedures Document"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:4d69b0df-3853-3ae2-84ca-96c8836625c6"
              },
              {
                "reference": "urn:uuid:fbe6378c-b49b-322c-bee4-5dc13a8f8092"
              }
            ]
          },
          {
            "title": "Medical Devices",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "46264-8",
                  "display": "History of medical device use"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:4060dbf6-1ac8-36ce-908c-f0a406515944"
              },
              {
                "reference": "urn:uuid:531614cb-ddf3-3037-b091-b0571a93b4a8"
              }
            ]
          },
          {
            "title": "Results",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "30954-2",
                  "display": "Relevant diagnostic tests/laboratory data note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:1858446d-4293-325a-ac0e-13edc47e9850"
              },
              {
                "reference": "urn:uuid:78373a52-444f-3fc6-85d0-665a89327f08"
              },
              {
                "reference": "urn:uuid:c7ff92a4-7dae-3cca-8b63-b05f5ce84c8a"
              },
              {
                "reference": "urn:uuid:4a40e360-0048-3c98-bd2a-3bb5955a2ffc"
              },
              {
                "reference": "urn:uuid:e55919e6-1333-3b82-b7b0-9aff175564af"
              }
            ]
          }
        ]
      }
    },
    {
      "fullUrl": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf",
      "resource": {
        "resourceType": "Patient",
        "id": "patient-01",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips"
          ]
        },
        "name": [
          {
            "family": "Tanaka",
            "given": [
              "Haru"
            ]
          }
        ],
        "gender": "female",
        "birthDate": "1946-02-08",
        "address": [
          {
            "text": "Aomori, Japan",
            "line": [
              "Aomori, Japan"
            ]
          }
        ],
        "communication": [
          {
            "language": {
              "coding": [
                {
                  "system": "urn:ietf:bcp:47",
                  "code": "ja-JP"
                }
              ]
            }
          }
        ]
      }
    },
    {
      "fullUrl": "urn:uuid:ad91354c-9fce-3a4f-813f-bf4ba8ff19d6",
      "resource": {
        "resourceType": "AllergyIntolerance",
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
              "code": "active"
            }
          ]
        },
        "verificationStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
              "code": "confirmed"
            }
          ]
        },
        "type": "allergy",
        "criticality": "low",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "782594005",
              "display": "Allergy to soy protein"
            }
          ],
          "text": "Allergy to soy protein"
        },
        "patient": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:e7084d28-06ba-37a8-aaea-dfa3af3479ff",
      "resource": {
        "resourceType": "Medication",
        "code": {
          "coding": [
            {
              "system": "http://www.whocc.no/atc",
              "code": "R06AX26",
              "display": "Fexofenadine"
            }
          ],
          "text": "Fexofenadine"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:359c2770-4bb9-312f-a3df-472535720d70",
      "resource": {
        "resourceType": "MedicationStatement",
        "status": "active",
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "dateAsserted": "2026-10-02T16:04:42.386Z",
        "dosage": [
          {
            "text": "Allegra FX (fexofenadine 60mg) • 1tab",
            "route": {
              "coding": [
                {
                  "system": "http://snomed.info/sct",
                  "code": "26643006"
                }
              ],
              "text": "Oral"
            },
            "doseAndRate": [
              {
                "doseQuantity": {
                  "value": 1.0,
                  "unit": "tab"
                }
              }
            ]
          }
        ],
        "medicationReference": {
          "reference": "urn:uuid:e7084d28-06ba-37a8-aaea-dfa3af3479ff"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:b8d0edc4-7917-3bc8-b1cf-aa991249a1c3",
      "resource": {
        "resourceType": "Medication",
        "code": {
          "coding": [
            {
              "system": "http://www.whocc.no/atc",
              "code": "R05DA09",
              "display": "Dextromethorphan"
            }
          ],
          "text": "Dextromethorphan"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:f4fb2770-b898-3bbb-ae00-5d5a79988462",
      "resource": {
        "resourceType": "MedicationStatement",
        "status": "active",
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "dateAsserted": "2026-10-02T16:04:42.391Z",
        "dosage": [
          {
            "text": "Medicon Pro (dextromethorphan) • 1tab",
            "route": {
              "coding": [
                {
                  "system": "http://snomed.info/sct",
                  "code": "26643006"
                }
              ],
              "text": "Oral"
            },
            "doseAndRate": [
              {
                "doseQuantity": {
                  "value": 1.0,
                  "unit": "tab"
                }
              }
            ]
          }
        ],
        "medicationReference": {
          "reference": "urn:uuid:b8d0edc4-7917-3bc8-b1cf-aa991249a1c3"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:ba74b4b2-100b-3c04-8cb0-d6c74ec9295d",
      "resource": {
        "resourceType": "Medication",
        "code": {
          "coding": [
            {
              "system": "http://www.whocc.no/atc",
              "code": "C03CA01",
              "display": "Furosemide"
            }
          ],
          "text": "Furosemide"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:df0e3c0e-0daf-3b11-9d37-d6a1fba38253",
      "resource": {
        "resourceType": "MedicationStatement",
        "status": "active",
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "dateAsserted": "2026-10-02T16:04:42.395Z",
        "dosage": [
          {
            "text": "Furosemide 20mg • 1tab",
            "route": {
              "coding": [
                {
                  "system": "http://snomed.info/sct",
                  "code": "26643006"
                }
              ],
              "text": "Oral"
            },
            "doseAndRate": [
              {
                "doseQuantity": {
                  "value": 1.0,
                  "unit": "tab"
                }
              }
            ]
          }
        ],
        "medicationReference": {
          "reference": "urn:uuid:ba74b4b2-100b-3c04-8cb0-d6c74ec9295d"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:d2287236-3d7b-36e0-bcad-2ec13a1d0446",
      "resource": {
        "resourceType": "Medication",
        "code": {
          "text": "Tisane maison"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:16aed101-d56e-37ba-855c-05da1583d6eb",
      "resource": {
        "resourceType": "MedicationStatement",
        "status": "active",
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "dateAsserted": "2026-10-02T16:04:42.396Z",
        "dosage": [
          {
            "text": "Tisane maison",
            "route": {
              "coding": [
                {
                  "system": "http://snomed.info/sct",
                  "code": "26643006"
                }
              ],
              "text": "Oral"
            }
          }
        ],
        "medicationReference": {
          "reference": "urn:uuid:d2287236-3d7b-36e0-bcad-2ec13a1d0446"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:ec2d139b-f5b6-352e-9061-db082fa3fe6c",
      "resource": {
        "resourceType": "Condition",
        "id": "cn-haru-heart-failure",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "active",
              "display": "Active"
            }
          ]
        },
        "category": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/condition-category",
                "code": "problem-list-item",
                "display": "Problem List Item"
              }
            ]
          }
        ],
        "severity": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "LA6751-7",
              "display": "Moderate"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "84114007",
              "display": "Heart failure"
            }
          ],
          "text": "Heart failure"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "note": [
          {
            "text": "NYHA II, on furosemide"
          }
        ],
        "onsetDateTime": "2020-11"
      }
    },
    {
      "fullUrl": "urn:uuid:2c811ef7-4f33-3334-adda-4da21a1e2a1f",
      "resource": {
        "resourceType": "Condition",
        "id": "cn-haru-ckd3",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "active",
              "display": "Active"
            }
          ]
        },
        "category": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/condition-category",
                "code": "problem-list-item",
                "display": "Problem List Item"
              }
            ]
          }
        ],
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "433144002",
              "display": "Chronic kidney disease stage 3"
            }
          ],
          "text": "Chronic kidney disease stage 3"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "note": [
          {
            "text": "eGFR 48 (2026-02)"
          }
        ],
        "onsetDateTime": "2022"
      }
    },
    {
      "fullUrl": "urn:uuid:ac08dfd8-d2af-32c2-bf9e-da86059ab58d",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-haru-flu-2025",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "1181000221105",
              "display": "Influenza virus antigen only vaccine product"
            }
          ],
          "text": "Seasonal influenza vaccine"
        },
        "patient": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "performer": [
          {
            "actor": {
              "display": "青森市民病院"
            }
          }
        ],
        "note": [
          {
            "text": "定期接種（高齢者）"
          }
        ],
        "occurrenceDateTime": "2025-10-14"
      }
    },
    {
      "fullUrl": "urn:uuid:e3e461be-fe38-3090-9beb-ee688e01296a",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-haru-pcv-2021",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "1801000221105",
              "display": "Streptococcus pneumoniae capsular polysaccharide antigen conjugated only vaccine product"
            }
          ],
          "text": "Pneumococcal conjugate vaccine (PCV)"
        },
        "patient": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "protocolApplied": [
          {
            "doseNumberPositiveInt": 1,
            "seriesDosesPositiveInt": 1
          }
        ],
        "occurrenceDateTime": "2021-04-06"
      }
    },
    {
      "fullUrl": "urn:uuid:d2321d4b-35cd-30fd-a034-a3997af51c52",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-haru-covid-2024",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "1119349007",
              "display": "COVID-19 mRNA vaccine"
            }
          ],
          "text": "COVID-19 mRNA vaccine"
        },
        "patient": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "manufacturer": {
          "display": "Pfizer-BioNTech"
        },
        "lotNumber": "HG1282",
        "protocolApplied": [
          {
            "doseNumberPositiveInt": 7
          }
        ],
        "occurrenceDateTime": "2024-11-02"
      }
    },
    {
      "fullUrl": "urn:uuid:4d69b0df-3853-3ae2-84ca-96c8836625c6",
      "resource": {
        "resourceType": "Procedure",
        "id": "pr-haru-cabg-2015",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips"
          ]
        },
        "status": "completed",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "232717009",
              "display": "Coronary artery bypass graft"
            }
          ],
          "text": "Coronary artery bypass graft"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "location": {
          "display": "青森市民病院"
        },
        "bodySite": [
          {
            "text": "Heart"
          }
        ],
        "outcome": {
          "text": "Triple bypass, good recovery"
        },
        "performedDateTime": "2015-09-02"
      }
    },
    {
      "fullUrl": "urn:uuid:fbe6378c-b49b-322c-bee4-5dc13a8f8092",
      "resource": {
        "resourceType": "Procedure",
        "id": "pr-haru-cesarean-1975",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips"
          ]
        },
        "status": "completed",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "11466000",
              "display": "Cesarean section"
            }
          ],
          "text": "Cesarean section"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "performedDateTime": "1975"
      }
    },
    {
      "fullUrl": "urn:uuid:0f937d77-1cd9-3f87-a338-7d7382046d56",
      "resource": {
        "resourceType": "Device",
        "id": "dv-haru-pacemaker-2021-device",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Device-uv-ips"
          ]
        },
        "udiCarrier": [
          {
            "deviceIdentifier": "(01)00643169007222(21)PJN1234567",
            "carrierHRF": "(01)00643169007222(21)PJN1234567"
          }
        ],
        "status": "active",
        "manufacturer": "Medtronic",
        "serialNumber": "PJN1234567",
        "modelNumber": "Azure XT DR MRI SureScan",
        "type": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "14106009",
              "display": "Cardiac pacemaker"
            }
          ],
          "text": "Cardiac pacemaker"
        },
        "patient": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:4060dbf6-1ac8-36ce-908c-f0a406515944",
      "resource": {
        "resourceType": "DeviceUseStatement",
        "id": "dv-haru-pacemaker-2021",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/DeviceUseStatement-uv-ips"
          ]
        },
        "status": "active",
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "device": {
          "reference": "urn:uuid:0f937d77-1cd9-3f87-a338-7d7382046d56"
        },
        "bodySite": {
          "text": "Left pectoral"
        },
        "note": [
          {
            "text": "MRI-conditional"
          }
        ],
        "timingDateTime": "2021-03-15"
      }
    },
    {
      "fullUrl": "urn:uuid:90df7d94-7ef9-3d56-93d2-bfd1135c653a",
      "resource": {
        "resourceType": "Device",
        "id": "dv-haru-hearing-aid-2019-device",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Device-uv-ips"
          ]
        },
        "status": "active",
        "manufacturer": "Phonak",
        "type": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "6012004",
              "display": "Hearing aid"
            }
          ],
          "text": "Hearing aid"
        },
        "patient": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:531614cb-ddf3-3037-b091-b0571a93b4a8",
      "resource": {
        "resourceType": "DeviceUseStatement",
        "id": "dv-haru-hearing-aid-2019",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/DeviceUseStatement-uv-ips"
          ]
        },
        "status": "active",
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "device": {
          "reference": "urn:uuid:90df7d94-7ef9-3d56-93d2-bfd1135c653a"
        },
        "bodySite": {
          "text": "Both ears"
        },
        "timingDateTime": "2019-06"
      }
    },
    {
      "fullUrl": "urn:uuid:1858446d-4293-325a-ac0e-13edc47e9850",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-blood-group-demo-haru",
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
              "code": "882-1",
              "display": "ABO and Rh group [Type] in Blood"
            }
          ],
          "text": "ABO and Rh blood group"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "performer": [
          {
            "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf",
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
    },
    {
      "fullUrl": "urn:uuid:78373a52-444f-3fc6-85d0-665a89327f08",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-haru-potassium-2026",
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
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "performer": [
          {
            "display": "Matsuyama Red Cross Hospital laboratory"
          }
        ],
        "interpretation": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "code": "N",
                "display": "Normal"
              }
            ],
            "text": "Normal"
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
        "effectiveDateTime": "2026-02-10",
        "valueQuantity": {
          "value": 4.1,
          "unit": "mmol/L",
          "system": "http://unitsofmeasure.org",
          "code": "mmol/L"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:c7ff92a4-7dae-3cca-8b63-b05f5ce84c8a",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-haru-hemoglobin-2026",
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
              "code": "718-7",
              "display": "Hemoglobin [Mass/volume] in Blood"
            }
          ],
          "text": "Hemoglobin"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "performer": [
          {
            "display": "Matsuyama Red Cross Hospital laboratory"
          }
        ],
        "interpretation": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "code": "L",
                "display": "Low"
              }
            ],
            "text": "Low"
          }
        ],
        "referenceRange": [
          {
            "low": {
              "value": 12.0,
              "unit": "g/dL",
              "system": "http://unitsofmeasure.org",
              "code": "g/dL"
            },
            "high": {
              "value": 16.0,
              "unit": "g/dL",
              "system": "http://unitsofmeasure.org",
              "code": "g/dL"
            }
          }
        ],
        "effectiveDateTime": "2026-02-10",
        "valueQuantity": {
          "value": 11.8,
          "unit": "g/dL",
          "system": "http://unitsofmeasure.org",
          "code": "g/dL"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:4a40e360-0048-3c98-bd2a-3bb5955a2ffc",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-haru-egfr-2026",
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
              "code": "33914-3",
              "display": "Glomerular filtration rate [Volume Rate/Area] in Serum or Plasma by Creatinine-based formula (MDRD)/1.73 sq M"
            }
          ],
          "text": "eGFR (MDRD)"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "performer": [
          {
            "display": "Matsuyama Red Cross Hospital laboratory"
          }
        ],
        "interpretation": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "code": "L",
                "display": "Low"
              }
            ],
            "text": "Low"
          }
        ],
        "note": [
          {
            "text": "CKD stage 3a — adjust renally cleared drugs"
          }
        ],
        "referenceRange": [
          {
            "low": {
              "value": 60.0,
              "unit": "mL/min/{1.73_m2}",
              "system": "http://unitsofmeasure.org",
              "code": "mL/min/{1.73_m2}"
            }
          }
        ],
        "effectiveDateTime": "2026-02-10",
        "valueQuantity": {
          "value": 48.0,
          "unit": "mL/min/{1.73_m2}",
          "system": "http://unitsofmeasure.org",
          "code": "mL/min/{1.73_m2}"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:e55919e6-1333-3b82-b7b0-9aff175564af",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-haru-chest-xray-2025",
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
          "text": "Chest X-ray"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "performer": [
          {
            "display": "Radiology, Matsuyama Red Cross Hospital"
          }
        ],
        "effectiveDateTime": "2025-12-03",
        "valueString": "Mild cardiomegaly, no pleural effusion"
      }
    },
    {
      "fullUrl": "urn:uuid:0dc3b078-cf8a-3248-9a64-559b279dcf92",
      "resource": {
        "resourceType": "Condition",
        "id": "ph-haru-mi-2015",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "resolved",
              "display": "Resolved"
            }
          ]
        },
        "severity": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "LA6750-9",
              "display": "Severe"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "22298006",
              "display": "Myocardial infarction"
            }
          ],
          "text": "Myocardial infarction"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "note": [
          {
            "text": "Treated by coronary bypass (2015-09)"
          }
        ],
        "onsetDateTime": "2015-08-27",
        "abatementDateTime": "2015-09"
      }
    },
    {
      "fullUrl": "urn:uuid:91e6ba24-67ec-3674-a7bf-7dad30c769bc",
      "resource": {
        "resourceType": "Condition",
        "id": "ph-haru-tb-1962",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "resolved",
              "display": "Resolved"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "56717001",
              "display": "Tuberculosis"
            }
          ],
          "text": "Tuberculosis"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "note": [
          {
            "text": "Pulmonary, treated — calcified scar on chest X-ray"
          }
        ],
        "onsetDateTime": "1962",
        "abatementDateTime": "1963"
      }
    },
    {
      "fullUrl": "urn:uuid:68fa45ea-8251-374e-b3a3-592e837b3bb3",
      "resource": {
        "resourceType": "Observation",
        "id": "pg-haru-births-total",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-outcome-uv-ips"
          ]
        },
        "status": "final",
        "code": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "11640-0",
              "display": "[#] Births total"
            }
          ],
          "text": "[#] Births total"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "effectiveDateTime": "2026-02-10",
        "valueInteger": 2
      }
    },
    {
      "fullUrl": "urn:uuid:3444b98a-1c67-3028-8c18-7a343041fc4e",
      "resource": {
        "resourceType": "Observation",
        "id": "pg-haru-births-live",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-outcome-uv-ips"
          ]
        },
        "status": "final",
        "code": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "11636-8",
              "display": "[#] Births.live"
            }
          ],
          "text": "[#] Births.live"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "effectiveDateTime": "2026-02-10",
        "valueInteger": 2
      }
    },
    {
      "fullUrl": "urn:uuid:ad863416-bfd6-3ce5-90bd-8dc4dec8fca2",
      "resource": {
        "resourceType": "Observation",
        "id": "pg-haru-births-term",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-outcome-uv-ips"
          ]
        },
        "status": "final",
        "code": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "11639-2",
              "display": "[#] Births.term"
            }
          ],
          "text": "[#] Births.term"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "effectiveDateTime": "2026-02-10",
        "valueInteger": 2
      }
    },
    {
      "fullUrl": "urn:uuid:156e3f5b-5324-3ea1-a3cd-4aa31d4295a5",
      "resource": {
        "resourceType": "Condition",
        "id": "fs-haru-hearing-loss",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "active",
              "display": "Active"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "15188001",
              "display": "Hearing loss"
            }
          ],
          "text": "Hearing loss"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "note": [
          {
            "text": "Bilateral hearing aids"
          }
        ],
        "onsetDateTime": "2019"
      }
    },
    {
      "fullUrl": "urn:uuid:881107ab-98ee-34d7-ae1c-4f95dffc1637",
      "resource": {
        "resourceType": "Condition",
        "id": "fs-haru-cane",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "active",
              "display": "Active"
            }
          ]
        },
        "code": {
          "text": "Walks with a cane outdoors"
        },
        "subject": {
          "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
        },
        "onsetDateTime": "2021"
      }
    }
  ]
}
```

### `json/demo_kurodo.fhir.json`
```
{
  "resourceType": "Bundle",
  "identifier": {
    "system": "urn:ietf:rfc:3986",
    "value": "urn:uuid:01abb322-d1da-37aa-a447-16732b94e776"
  },
  "type": "document",
  "timestamp": "2026-10-02T16:08:10.287Z",
  "entry": [
    {
      "fullUrl": "urn:uuid:87752277-1319-36dc-89bc-84401f621cde",
      "resource": {
        "resourceType": "Composition",
        "status": "final",
        "type": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "60591-5",
              "display": "Patient summary Document"
            }
          ]
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "date": "2026-10-02T16:08:10.266Z",
        "author": [
          {
            "display": "JEMMA Pass on-device"
          }
        ],
        "title": "International Patient Summary",
        "confidentiality": "N",
        "section": [
          {
            "title": "Allergies",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "48765-2"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:7aa6d0b0-0f52-30aa-a629-6f4e6458f5c7"
              },
              {
                "reference": "urn:uuid:50affadc-b3b3-343a-92a8-08787a10c6c2"
              },
              {
                "reference": "urn:uuid:022f7ed5-e2b8-34d3-b410-43637b00d2b7"
              }
            ]
          },
          {
            "title": "Problems",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11450-4",
                  "display": "Problem list - Reported"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:75ee3f04-7d6d-3d94-8c6b-db36cd75405e"
              }
            ]
          },
          {
            "title": "History of Past Illness",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11348-0",
                  "display": "History of Past illness note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:3c83477d-07c1-3a33-8655-d09e5b886445"
              },
              {
                "reference": "urn:uuid:8cc1fee8-3a06-34be-9dee-94e480b39710"
              }
            ]
          },
          {
            "title": "Immunizations",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11369-6",
                  "display": "History of Immunization note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:53d9a549-9968-3e73-92eb-cfdbc04879bc"
              },
              {
                "reference": "urn:uuid:bb2daf49-a43f-3738-9fc7-8882d8bddc67"
              },
              {
                "reference": "urn:uuid:8fa2fdf0-9d8b-38ef-a038-01790dfaca5c"
              },
              {
                "reference": "urn:uuid:4f6e47c5-601e-3cc5-bd06-49685ef16e94"
              },
              {
                "reference": "urn:uuid:cd1a2368-3e84-3e7f-8bb5-b40fbf548314"
              }
            ]
          },
          {
            "title": "History of Procedures",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "47519-4",
                  "display": "History of Procedures Document"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:dae4d977-5d62-3129-b704-46e606ca9930"
              },
              {
                "reference": "urn:uuid:8b292a6e-8347-3d6d-86ab-d27b91623d54"
              }
            ]
          },
          {
            "title": "Results",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "30954-2",
                  "display": "Relevant diagnostic tests/laboratory data note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:46275685-ae0f-3616-ac70-d3c9188c011b"
              },
              {
                "reference": "urn:uuid:e0d17c69-2b04-39bf-83d9-02f147ab0fcd"
              },
              {
                "reference": "urn:uuid:d6075483-92fb-3723-b928-7a72788cafe9"
              },
              {
                "reference": "urn:uuid:840d74c8-e2c1-3bc3-8f8f-fd091f7ab2f6"
              }
            ]
          }
        ]
      }
    },
    {
      "fullUrl": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1",
      "resource": {
        "resourceType": "Patient",
        "id": "patient-01",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips"
          ]
        },
        "name": [
          {
            "family": "Henro",
            "given": [
              "Kurodo"
            ]
          }
        ],
        "gender": "male",
        "birthDate": "1979-04-04",
        "address": [
          {
            "text": "Rue de la Paix 12, 5660 Couvin, Belgique",
            "line": [
              "Rue de la Paix 12, 5660 Couvin, Belgique"
            ]
          }
        ],
        "contact": [
          {
            "relationship": [
              {
                "text": "FRND"
              }
            ],
            "name": {
              "text": "Kamekichi"
            },
            "telecom": [
              {
                "system": "phone",
                "value": "+32 2 000 00 01",
                "use": "mobile"
              }
            ]
          }
        ],
        "communication": [
          {
            "language": {
              "coding": [
                {
                  "system": "urn:ietf:bcp:47",
                  "code": "fr-FR"
                }
              ]
            }
          }
        ]
      }
    },
    {
      "fullUrl": "urn:uuid:7aa6d0b0-0f52-30aa-a629-6f4e6458f5c7",
      "resource": {
        "resourceType": "AllergyIntolerance",
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
              "code": "active"
            }
          ]
        },
        "verificationStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
              "code": "confirmed"
            }
          ]
        },
        "type": "allergy",
        "criticality": "high",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "91936005",
              "display": "Allergy to penicillin"
            }
          ],
          "text": "Allergy to penicillin"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:50affadc-b3b3-343a-92a8-08787a10c6c2",
      "resource": {
        "resourceType": "AllergyIntolerance",
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
              "code": "active"
            }
          ]
        },
        "verificationStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
              "code": "confirmed"
            }
          ]
        },
        "type": "allergy",
        "criticality": "high",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "417532002",
              "display": "Allergy to fish"
            }
          ],
          "text": "Allergy to fish"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:022f7ed5-e2b8-34d3-b410-43637b00d2b7",
      "resource": {
        "resourceType": "AllergyIntolerance",
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
              "code": "active"
            }
          ]
        },
        "verificationStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
              "code": "confirmed"
            }
          ]
        },
        "type": "allergy",
        "criticality": "low",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "419263009",
              "display": "Allergy to tree pollen"
            }
          ],
          "text": "Allergy to tree pollen"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:75ee3f04-7d6d-3d94-8c6b-db36cd75405e",
      "resource": {
        "resourceType": "Condition",
        "id": "cn-kurodo-hypercholesterolemia",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "active",
              "display": "Active"
            }
          ]
        },
        "category": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/condition-category",
                "code": "problem-list-item",
                "display": "Problem List Item"
              }
            ]
          }
        ],
        "severity": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "LA6752-5",
              "display": "Mild"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "13644009",
              "display": "Hypercholesterolemia"
            }
          ],
          "text": "Hypercholesterolemia"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "note": [
          {
            "text": "LDL 131 mg/dL — lifestyle first"
          }
        ],
        "onsetDateTime": "2026-01"
      }
    },
    {
      "fullUrl": "urn:uuid:53d9a549-9968-3e73-92eb-cfdbc04879bc",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-kurodo-tdap-2022",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "871876003",
              "display": "Acellular Bordetella pertussis and Clostridium tetani and Corynebacterium diphtheriae antigens only vaccine product"
            }
          ],
          "text": "Tetanus-diphtheria-pertussis (Tdap)"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "manufacturer": {
          "display": "Sanofi Pasteur"
        },
        "lotNumber": "AC52B213BC",
        "performer": [
          {
            "actor": {
              "display": "Dr. Lambert, Couvin"
            }
          }
        ],
        "occurrenceDateTime": "2022-05-17"
      }
    },
    {
      "fullUrl": "urn:uuid:bb2daf49-a43f-3738-9fc7-8882d8bddc67",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-kurodo-hepab-2016",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "871803007",
              "display": "Hepatitis A and Hepatitis B virus antigens only vaccine product"
            }
          ],
          "text": "Hepatitis A + B vaccine"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "manufacturer": {
          "display": "GSK"
        },
        "note": [
          {
            "text": "Series completed before the first Shikoku pilgrimage"
          }
        ],
        "protocolApplied": [
          {
            "doseNumberPositiveInt": 3,
            "seriesDosesPositiveInt": 3
          }
        ],
        "occurrenceDateTime": "2016-03-02"
      }
    },
    {
      "fullUrl": "urn:uuid:8fa2fdf0-9d8b-38ef-a038-01790dfaca5c",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-kurodo-je-2023",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "836378001",
              "display": "Japanese encephalitis virus antigen-containing vaccine product"
            }
          ],
          "text": "Japanese encephalitis vaccine"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "manufacturer": {
          "display": "Valneva"
        },
        "protocolApplied": [
          {
            "doseNumberPositiveInt": 2,
            "seriesDosesPositiveInt": 2
          }
        ],
        "occurrenceDateTime": "2023-01-20"
      }
    },
    {
      "fullUrl": "urn:uuid:4f6e47c5-601e-3cc5-bd06-49685ef16e94",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-kurodo-covid-2021",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "1119349007",
              "display": "COVID-19 mRNA vaccine"
            }
          ],
          "text": "COVID-19 mRNA vaccine"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "manufacturer": {
          "display": "Pfizer-BioNTech"
        },
        "lotNumber": "FD0168",
        "protocolApplied": [
          {
            "doseNumberPositiveInt": 2,
            "seriesDosesPositiveInt": 2
          }
        ],
        "occurrenceDateTime": "2021-06-11"
      }
    },
    {
      "fullUrl": "urn:uuid:cd1a2368-3e84-3e7f-8bb5-b40fbf548314",
      "resource": {
        "resourceType": "Immunization",
        "id": "9f29f965-aa74-473b-b77c-2eae731d1496",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "text": "TestSeriesVaccine"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "protocolApplied": [
          {
            "doseNumberString": "unknown",
            "seriesDosesPositiveInt": 3
          }
        ],
        "occurrenceString": "unknown"
      }
    },
    {
      "fullUrl": "urn:uuid:dae4d977-5d62-3129-b704-46e606ca9930",
      "resource": {
        "resourceType": "Procedure",
        "id": "pr-kurodo-appendectomy-1995",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips"
          ]
        },
        "status": "completed",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "80146002",
              "display": "Appendectomy"
            }
          ],
          "text": "Appendectomy"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "location": {
          "display": "CHU UCL Namur (Godinne)"
        },
        "outcome": {
          "text": "Uneventful recovery"
        },
        "note": [
          {
            "text": "Laparoscopic"
          }
        ],
        "performedDateTime": "1995-07-12"
      }
    },
    {
      "fullUrl": "urn:uuid:8b292a6e-8347-3d6d-86ab-d27b91623d54",
      "resource": {
        "resourceType": "Procedure",
        "id": "pr-kurodo-colonoscopy-2024",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips"
          ]
        },
        "status": "completed",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "73761001",
              "display": "Colonoscopy"
            }
          ],
          "text": "Colonoscopy"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "performer": [
          {
            "actor": {
              "display": "Dr. Lambert, Couvin"
            }
          }
        ],
        "outcome": {
          "text": "Normal — screening"
        },
        "note": [
          {
            "text": "Next screening 2034"
          }
        ],
        "performedDateTime": "2024-02-19"
      }
    },
    {
      "fullUrl": "urn:uuid:46275685-ae0f-3616-ac70-d3c9188c011b",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-blood-group-demo-kurodo",
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
              "code": "882-1",
              "display": "ABO and Rh group [Type] in Blood"
            }
          ],
          "text": "ABO and Rh blood group"
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
      }
    },
    {
      "fullUrl": "urn:uuid:e0d17c69-2b04-39bf-83d9-02f147ab0fcd",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-kurodo-hba1c-2026",
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
              "code": "4548-4",
              "display": "Hemoglobin A1c/Hemoglobin.total in Blood"
            }
          ],
          "text": "Hemoglobin A1c"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "performer": [
          {
            "display": "Laboratoire CHU UCL Namur"
          }
        ],
        "interpretation": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "code": "N",
                "display": "Normal"
              }
            ],
            "text": "Normal"
          }
        ],
        "referenceRange": [
          {
            "low": {
              "value": 4.0,
              "unit": "%",
              "system": "http://unitsofmeasure.org",
              "code": "%"
            },
            "high": {
              "value": 6.0,
              "unit": "%",
              "system": "http://unitsofmeasure.org",
              "code": "%"
            }
          }
        ],
        "effectiveDateTime": "2026-01-15",
        "valueQuantity": {
          "value": 5.6,
          "unit": "%",
          "system": "http://unitsofmeasure.org",
          "code": "%"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:d6075483-92fb-3723-b928-7a72788cafe9",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-kurodo-ldl-2026",
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
              "code": "2089-1",
              "display": "Cholesterol in LDL [Mass/volume] in Serum or Plasma"
            }
          ],
          "text": "LDL cholesterol"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "performer": [
          {
            "display": "Laboratoire CHU UCL Namur"
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
        "note": [
          {
            "text": "Lifestyle advice, recheck in 6 months"
          }
        ],
        "referenceRange": [
          {
            "high": {
              "value": 100.0,
              "unit": "mg/dL",
              "system": "http://unitsofmeasure.org",
              "code": "mg/dL"
            }
          }
        ],
        "effectiveDateTime": "2026-01-15",
        "valueQuantity": {
          "value": 131.0,
          "unit": "mg/dL",
          "system": "http://unitsofmeasure.org",
          "code": "mg/dL"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:840d74c8-e2c1-3bc3-8f8f-fd091f7ab2f6",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-kurodo-creat-2026",
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
              "code": "2160-0",
              "display": "Creatinine [Mass/volume] in Serum or Plasma"
            }
          ],
          "text": "Creatinine (serum/plasma)"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "performer": [
          {
            "display": "Laboratoire CHU UCL Namur"
          }
        ],
        "interpretation": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "code": "N",
                "display": "Normal"
              }
            ],
            "text": "Normal"
          }
        ],
        "referenceRange": [
          {
            "low": {
              "value": 0.7,
              "unit": "mg/dL",
              "system": "http://unitsofmeasure.org",
              "code": "mg/dL"
            },
            "high": {
              "value": 1.2,
              "unit": "mg/dL",
              "system": "http://unitsofmeasure.org",
              "code": "mg/dL"
            }
          }
        ],
        "effectiveDateTime": "2026-01-15",
        "valueQuantity": {
          "value": 0.9,
          "unit": "mg/dL",
          "system": "http://unitsofmeasure.org",
          "code": "mg/dL"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:3c83477d-07c1-3a33-8655-d09e5b886445",
      "resource": {
        "resourceType": "Condition",
        "id": "ph-kurodo-appendicitis-1995",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "resolved",
              "display": "Resolved"
            }
          ]
        },
        "severity": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "LA6751-7",
              "display": "Moderate"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "74400008",
              "display": "Appendicitis"
            }
          ],
          "text": "Appendicitis"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "note": [
          {
            "text": "Treated by appendectomy"
          }
        ],
        "onsetDateTime": "1995-07-10",
        "abatementDateTime": "1995-07-12"
      }
    },
    {
      "fullUrl": "urn:uuid:8cc1fee8-3a06-34be-9dee-94e480b39710",
      "resource": {
        "resourceType": "Condition",
        "id": "ph-kurodo-pneumonia-2018",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "resolved",
              "display": "Resolved"
            }
          ]
        },
        "severity": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "LA6752-5",
              "display": "Mild"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "233604007",
              "display": "Pneumonia"
            }
          ],
          "text": "Pneumonia"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "onsetDateTime": "2018-02",
        "abatementDateTime": "2018-03"
      }
    }
  ]
}
```

### `json/demo_kurodo_large_result.fhir.json`
```
{
  "resourceType": "Bundle",
  "identifier": {
    "system": "urn:ietf:rfc:3986",
    "value": "urn:uuid:01abb322-d1da-37aa-a447-16732b94e776"
  },
  "type": "document",
  "timestamp": "2026-10-02T16:13:23.316Z",
  "entry": [
    {
      "fullUrl": "urn:uuid:87752277-1319-36dc-89bc-84401f621cde",
      "resource": {
        "resourceType": "Composition",
        "status": "final",
        "type": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "60591-5",
              "display": "Patient summary Document"
            }
          ]
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "date": "2026-10-02T16:13:23.295Z",
        "author": [
          {
            "display": "JEMMA Pass on-device"
          }
        ],
        "title": "International Patient Summary",
        "confidentiality": "N",
        "section": [
          {
            "title": "Allergies",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "48765-2"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:7aa6d0b0-0f52-30aa-a629-6f4e6458f5c7"
              },
              {
                "reference": "urn:uuid:50affadc-b3b3-343a-92a8-08787a10c6c2"
              },
              {
                "reference": "urn:uuid:022f7ed5-e2b8-34d3-b410-43637b00d2b7"
              }
            ]
          },
          {
            "title": "Problems",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11450-4",
                  "display": "Problem list - Reported"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:75ee3f04-7d6d-3d94-8c6b-db36cd75405e"
              }
            ]
          },
          {
            "title": "History of Past Illness",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11348-0",
                  "display": "History of Past illness note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:3c83477d-07c1-3a33-8655-d09e5b886445"
              },
              {
                "reference": "urn:uuid:8cc1fee8-3a06-34be-9dee-94e480b39710"
              }
            ]
          },
          {
            "title": "Immunizations",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "11369-6",
                  "display": "History of Immunization note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:53d9a549-9968-3e73-92eb-cfdbc04879bc"
              },
              {
                "reference": "urn:uuid:bb2daf49-a43f-3738-9fc7-8882d8bddc67"
              },
              {
                "reference": "urn:uuid:8fa2fdf0-9d8b-38ef-a038-01790dfaca5c"
              },
              {
                "reference": "urn:uuid:4f6e47c5-601e-3cc5-bd06-49685ef16e94"
              }
            ]
          },
          {
            "title": "History of Procedures",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "47519-4",
                  "display": "History of Procedures Document"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:dae4d977-5d62-3129-b704-46e606ca9930"
              },
              {
                "reference": "urn:uuid:8b292a6e-8347-3d6d-86ab-d27b91623d54"
              }
            ]
          },
          {
            "title": "Results",
            "code": {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "30954-2",
                  "display": "Relevant diagnostic tests/laboratory data note"
                }
              ]
            },
            "entry": [
              {
                "reference": "urn:uuid:46275685-ae0f-3616-ac70-d3c9188c011b"
              },
              {
                "reference": "urn:uuid:e0d17c69-2b04-39bf-83d9-02f147ab0fcd"
              },
              {
                "reference": "urn:uuid:d6075483-92fb-3723-b928-7a72788cafe9"
              },
              {
                "reference": "urn:uuid:840d74c8-e2c1-3bc3-8f8f-fd091f7ab2f6"
              },
              {
                "reference": "urn:uuid:cfbf9e3a-9dc1-3cd1-a264-109a682287c7"
              }
            ]
          }
        ]
      }
    },
    {
      "fullUrl": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1",
      "resource": {
        "resourceType": "Patient",
        "id": "patient-01",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips"
          ]
        },
        "name": [
          {
            "family": "Henro",
            "given": [
              "Kurodo"
            ]
          }
        ],
        "gender": "male",
        "birthDate": "1979-04-04",
        "address": [
          {
            "text": "Rue de la Paix 12, 5660 Couvin, Belgique",
            "line": [
              "Rue de la Paix 12, 5660 Couvin, Belgique"
            ]
          }
        ],
        "contact": [
          {
            "relationship": [
              {
                "text": "FRND"
              }
            ],
            "name": {
              "text": "Kamekichi"
            },
            "telecom": [
              {
                "system": "phone",
                "value": "+32 2 000 00 01",
                "use": "mobile"
              }
            ]
          }
        ],
        "communication": [
          {
            "language": {
              "coding": [
                {
                  "system": "urn:ietf:bcp:47",
                  "code": "fr-FR"
                }
              ]
            }
          }
        ]
      }
    },
    {
      "fullUrl": "urn:uuid:7aa6d0b0-0f52-30aa-a629-6f4e6458f5c7",
      "resource": {
        "resourceType": "AllergyIntolerance",
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
              "code": "active"
            }
          ]
        },
        "verificationStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
              "code": "confirmed"
            }
          ]
        },
        "type": "allergy",
        "criticality": "high",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "91936005",
              "display": "Allergy to penicillin"
            }
          ],
          "text": "Allergy to penicillin"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:50affadc-b3b3-343a-92a8-08787a10c6c2",
      "resource": {
        "resourceType": "AllergyIntolerance",
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
              "code": "active"
            }
          ]
        },
        "verificationStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
              "code": "confirmed"
            }
          ]
        },
        "type": "allergy",
        "criticality": "high",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "417532002",
              "display": "Allergy to fish"
            }
          ],
          "text": "Allergy to fish"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:022f7ed5-e2b8-34d3-b410-43637b00d2b7",
      "resource": {
        "resourceType": "AllergyIntolerance",
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
              "code": "active"
            }
          ]
        },
        "verificationStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
              "code": "confirmed"
            }
          ]
        },
        "type": "allergy",
        "criticality": "low",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "419263009",
              "display": "Allergy to tree pollen"
            }
          ],
          "text": "Allergy to tree pollen"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:75ee3f04-7d6d-3d94-8c6b-db36cd75405e",
      "resource": {
        "resourceType": "Condition",
        "id": "cn-kurodo-hypercholesterolemia",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "active",
              "display": "Active"
            }
          ]
        },
        "category": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/condition-category",
                "code": "problem-list-item",
                "display": "Problem List Item"
              }
            ]
          }
        ],
        "severity": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "LA6752-5",
              "display": "Mild"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "13644009",
              "display": "Hypercholesterolemia"
            }
          ],
          "text": "Hypercholesterolemia"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "note": [
          {
            "text": "LDL 131 mg/dL — lifestyle first"
          }
        ],
        "onsetDateTime": "2026-01"
      }
    },
    {
      "fullUrl": "urn:uuid:53d9a549-9968-3e73-92eb-cfdbc04879bc",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-kurodo-tdap-2022",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "871876003",
              "display": "Acellular Bordetella pertussis and Clostridium tetani and Corynebacterium diphtheriae antigens only vaccine product"
            }
          ],
          "text": "Tetanus-diphtheria-pertussis (Tdap)"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "manufacturer": {
          "display": "Sanofi Pasteur"
        },
        "lotNumber": "AC52B213BC",
        "performer": [
          {
            "actor": {
              "display": "Dr. Lambert, Couvin"
            }
          }
        ],
        "occurrenceDateTime": "2022-05-17"
      }
    },
    {
      "fullUrl": "urn:uuid:bb2daf49-a43f-3738-9fc7-8882d8bddc67",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-kurodo-hepab-2016",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "871803007",
              "display": "Hepatitis A and Hepatitis B virus antigens only vaccine product"
            }
          ],
          "text": "Hepatitis A + B vaccine"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "manufacturer": {
          "display": "GSK"
        },
        "note": [
          {
            "text": "Series completed before the first Shikoku pilgrimage"
          }
        ],
        "protocolApplied": [
          {
            "doseNumberPositiveInt": 3,
            "seriesDosesPositiveInt": 3
          }
        ],
        "occurrenceDateTime": "2016-03-02"
      }
    },
    {
      "fullUrl": "urn:uuid:8fa2fdf0-9d8b-38ef-a038-01790dfaca5c",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-kurodo-je-2023",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "836378001",
              "display": "Japanese encephalitis virus antigen-containing vaccine product"
            }
          ],
          "text": "Japanese encephalitis vaccine"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "manufacturer": {
          "display": "Valneva"
        },
        "protocolApplied": [
          {
            "doseNumberPositiveInt": 2,
            "seriesDosesPositiveInt": 2
          }
        ],
        "occurrenceDateTime": "2023-01-20"
      }
    },
    {
      "fullUrl": "urn:uuid:4f6e47c5-601e-3cc5-bd06-49685ef16e94",
      "resource": {
        "resourceType": "Immunization",
        "id": "im-kurodo-covid-2021",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
          ]
        },
        "status": "completed",
        "vaccineCode": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "1119349007",
              "display": "COVID-19 mRNA vaccine"
            }
          ],
          "text": "COVID-19 mRNA vaccine"
        },
        "patient": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "manufacturer": {
          "display": "Pfizer-BioNTech"
        },
        "lotNumber": "FD0168",
        "protocolApplied": [
          {
            "doseNumberPositiveInt": 2,
            "seriesDosesPositiveInt": 2
          }
        ],
        "occurrenceDateTime": "2021-06-11"
      }
    },
    {
      "fullUrl": "urn:uuid:dae4d977-5d62-3129-b704-46e606ca9930",
      "resource": {
        "resourceType": "Procedure",
        "id": "pr-kurodo-appendectomy-1995",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips"
          ]
        },
        "status": "completed",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "80146002",
              "display": "Appendectomy"
            }
          ],
          "text": "Appendectomy"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "location": {
          "display": "CHU UCL Namur (Godinne)"
        },
        "outcome": {
          "text": "Uneventful recovery"
        },
        "note": [
          {
            "text": "Laparoscopic"
          }
        ],
        "performedDateTime": "1995-07-12"
      }
    },
    {
      "fullUrl": "urn:uuid:8b292a6e-8347-3d6d-86ab-d27b91623d54",
      "resource": {
        "resourceType": "Procedure",
        "id": "pr-kurodo-colonoscopy-2024",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Procedure-uv-ips"
          ]
        },
        "status": "completed",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "73761001",
              "display": "Colonoscopy"
            }
          ],
          "text": "Colonoscopy"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "performer": [
          {
            "actor": {
              "display": "Dr. Lambert, Couvin"
            }
          }
        ],
        "outcome": {
          "text": "Normal — screening"
        },
        "note": [
          {
            "text": "Next screening 2034"
          }
        ],
        "performedDateTime": "2024-02-19"
      }
    },
    {
      "fullUrl": "urn:uuid:46275685-ae0f-3616-ac70-d3c9188c011b",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-blood-group-demo-kurodo",
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
              "code": "882-1",
              "display": "ABO and Rh group [Type] in Blood"
            }
          ],
          "text": "ABO and Rh blood group"
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
      }
    },
    {
      "fullUrl": "urn:uuid:e0d17c69-2b04-39bf-83d9-02f147ab0fcd",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-kurodo-hba1c-2026",
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
              "code": "4548-4",
              "display": "Hemoglobin A1c/Hemoglobin.total in Blood"
            }
          ],
          "text": "Hemoglobin A1c"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "performer": [
          {
            "display": "Laboratoire CHU UCL Namur"
          }
        ],
        "interpretation": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "code": "N",
                "display": "Normal"
              }
            ],
            "text": "Normal"
          }
        ],
        "referenceRange": [
          {
            "low": {
              "value": 4.0,
              "unit": "%",
              "system": "http://unitsofmeasure.org",
              "code": "%"
            },
            "high": {
              "value": 6.0,
              "unit": "%",
              "system": "http://unitsofmeasure.org",
              "code": "%"
            }
          }
        ],
        "effectiveDateTime": "2026-01-15",
        "valueQuantity": {
          "value": 5.6,
          "unit": "%",
          "system": "http://unitsofmeasure.org",
          "code": "%"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:d6075483-92fb-3723-b928-7a72788cafe9",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-kurodo-ldl-2026",
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
              "code": "2089-1",
              "display": "Cholesterol in LDL [Mass/volume] in Serum or Plasma"
            }
          ],
          "text": "LDL cholesterol"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "performer": [
          {
            "display": "Laboratoire CHU UCL Namur"
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
        "note": [
          {
            "text": "Lifestyle advice, recheck in 6 months"
          }
        ],
        "referenceRange": [
          {
            "high": {
              "value": 100.0,
              "unit": "mg/dL",
              "system": "http://unitsofmeasure.org",
              "code": "mg/dL"
            }
          }
        ],
        "effectiveDateTime": "2026-01-15",
        "valueQuantity": {
          "value": 131.0,
          "unit": "mg/dL",
          "system": "http://unitsofmeasure.org",
          "code": "mg/dL"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:840d74c8-e2c1-3bc3-8f8f-fd091f7ab2f6",
      "resource": {
        "resourceType": "Observation",
        "id": "rs-kurodo-creat-2026",
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
              "code": "2160-0",
              "display": "Creatinine [Mass/volume] in Serum or Plasma"
            }
          ],
          "text": "Creatinine (serum/plasma)"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "performer": [
          {
            "display": "Laboratoire CHU UCL Namur"
          }
        ],
        "interpretation": [
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "code": "N",
                "display": "Normal"
              }
            ],
            "text": "Normal"
          }
        ],
        "referenceRange": [
          {
            "low": {
              "value": 0.7,
              "unit": "mg/dL",
              "system": "http://unitsofmeasure.org",
              "code": "mg/dL"
            },
            "high": {
              "value": 1.2,
              "unit": "mg/dL",
              "system": "http://unitsofmeasure.org",
              "code": "mg/dL"
            }
          }
        ],
        "effectiveDateTime": "2026-01-15",
        "valueQuantity": {
          "value": 0.9,
          "unit": "mg/dL",
          "system": "http://unitsofmeasure.org",
          "code": "mg/dL"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:cfbf9e3a-9dc1-3cd1-a264-109a682287c7",
      "resource": {
        "resourceType": "Observation",
        "id": "c4e9fb5f-9d4b-4d6c-bcd9-a0dd7c97d88c",
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
          "text": "TestLargeResult"
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
        "valueQuantity": {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/originalText",
              "valueString": "123456789012345678.123"
            }
          ],
          "value": 1.2345678901234568e+17
        }
      }
    },
    {
      "fullUrl": "urn:uuid:3c83477d-07c1-3a33-8655-d09e5b886445",
      "resource": {
        "resourceType": "Condition",
        "id": "ph-kurodo-appendicitis-1995",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "resolved",
              "display": "Resolved"
            }
          ]
        },
        "severity": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "LA6751-7",
              "display": "Moderate"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "74400008",
              "display": "Appendicitis"
            }
          ],
          "text": "Appendicitis"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "note": [
          {
            "text": "Treated by appendectomy"
          }
        ],
        "onsetDateTime": "1995-07-10",
        "abatementDateTime": "1995-07-12"
      }
    },
    {
      "fullUrl": "urn:uuid:8cc1fee8-3a06-34be-9dee-94e480b39710",
      "resource": {
        "resourceType": "Condition",
        "id": "ph-kurodo-pneumonia-2018",
        "meta": {
          "profile": [
            "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
          ]
        },
        "clinicalStatus": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
              "code": "resolved",
              "display": "Resolved"
            }
          ]
        },
        "severity": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "LA6752-5",
              "display": "Mild"
            }
          ]
        },
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "233604007",
              "display": "Pneumonia"
            }
          ],
          "text": "Pneumonia"
        },
        "subject": {
          "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
        },
        "onsetDateTime": "2018-02",
        "abatementDateTime": "2018-03"
      }
    }
  ]
}
```

### `validator/summary-freetext.txt`
```
errors:
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_haru-freetext.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kamekichi-freetext.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kurodo-freetext.txt:0
warnings:
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_haru-freetext.txt:52
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kamekichi-freetext.txt:31
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kurodo-freetext.txt:28
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/summary-freetext.txt:0
```

### `validator/summary-series.txt`
```
errors:
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_haru-series.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kamekichi-series.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kurodo-series.txt:0
warnings:
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_haru-series.txt:50
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kamekichi-series.txt:31
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kurodo-series.txt:29
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/summary-series.txt:0
```

### `validator/summary.txt`
```
errors:
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_haru.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kamekichi.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kurodo.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/summary.txt:0
warnings:
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_haru.txt:50
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kamekichi.txt:31
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/demo_kurodo.txt:28
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/4006cc6-20261002-1759/validator/summary.txt:0
```

