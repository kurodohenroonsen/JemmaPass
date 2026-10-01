# Device QA · feat/ips-18-pillars-cleanup · ed20225 · Pixel 9 Pro XL · Android 17 · 2026-10-01 11:55

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR
- Appareil : Pixel 9 Pro XL (komodo) · Android 17 (aucun numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 86 tests JVM, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 11 ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats du cycle 16

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Point 0 — Masquage anciens serials | ✅ | `git grep -n -i -E "46071\|FDAS"` | 4 fichiers nettoyés sur `device-reports` (`1c1b7e5`, `c3a383f`, `f7c500e`, `5970d94`), commit `7124961`, 0 fuite résiduelle. |
| Point 1 — Build + JVM + Seed | ✅ | `logs/assemble.log`, `logs/unit-tests.log`, `verify-seed.md` | 86 tests JVM réussis. Seed 77 checks PASS : 📜 K2 H2 Ka0 (nouveau), 🧪 K4 H5 Ka1, 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2. |
| T15.1 — Fiche + liste Haru | ✅ | `screenshots/160-detail-haru-past-problems.png`, `161-past-problems-list.png` | Section « 📜 ANTÉCÉDENTS (2) », tuile active badge 2. Liste `past_problems_recycler` (« 2 antécédents », Infarctus du myocarde 2015-08-27 → 2015-09 · ✅ Guérie · 🔴 Sévère, Tuberculose 1962 → 1963 · ✅ Guérie). |
| T15.2 — Création codée (Rougeole) | ✅ | `screenshots/162-form-rougeole.png`, `163-kurodo-past-problems-3.png`, `verify-t15-2.md` | Picker condition IPS -> Rougeole (`14189004`), Début 1975, Fin 1975 (« Année seulement »), Guérie, Légère (`LA6752-5`). Kurodo passe à 3 ph. Display anglais `Measles`. Réf. dans section `11348-0`. Projection `_j.ph` conforme. |
| T15.3 — Texte libre (Hépatite) | ✅ | `screenshots/164-form-free-text.png`, `165-kurodo-past-problems-4.png`, `verify-t15-3.md` | Texte libre `Hepatite virale enfance`, statut Inactive, aucune date, aucune sévérité. Kurodo passe à 4 ph. Bundle : `code.text` sans `coding`, `clinicalStatus=inactive`. |
| T15.4 — Chemins d'erreur formulaire | ✅ | `screenshots/166-errors.png`, `logs/logcat-ui.txt` | (1) Save vide -> erreur inline « Choisis une maladie ou saisis son nom ». (2) Année 1850 -> toast « Saisis une année entre 1900 et cette année » et rejetée. (3) Début 2010-05-01 / Fin 2005 -> erreur rouge inline « La fin ne peut pas précéder le début », toast, aucun enregistrement. |
| T15.5 — Édition sévérité | ✅ | `pull-t15-5/profiles/demo_kurodo.fhir.json`, `demo_kurodo.json`, `verify-t15-5.md` | Appendicite passée à « Sévère » -> Bundle `LA6750-9` et `_j.ph[].sv="LA6750-9"` (P6d PASS). Remise à « Modérée » (`LA6751-7`). |
| T15.6 — Suppression entrées de test | ✅ | `screenshots/167-kurodo-back-to-2.png`, `verify-t15-6.md` | (a) Bouton Supprimer formulaire sur Hépatite. (b) Appui long sur Rougeole -> dialogue Supprimer. Kurodo revient à 2 ph (`verify_profiles.py` PASS). |
| T15.7 — QR texte Haru EN/FR/JA | ✅ | `screenshots/168-qr-past-problems-haru-en.png`, `168-qr-past-problems-haru-fr.png`, `168-qr-past-problems-haru-ja.png` | Décodé OpenCV : section `📜 [ PAST ILLNESSES ]` (EN, 1327B) / `[ ANTÉCÉDENTS MÉDICAUX ]` (FR, 1396B) / `[ 既往歴 ]` (JA, 1467B) placée après 🧪. Lignes exactes 2015 puis 1962. Tailles <= 2200B (1 frame). |
| T15.8 — Validateur HL7 (3 personas) | ✅ | Sortie brute ci-dessous | `-version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a` : **0 ERREUR**, 0 fatal sur les 3 personas (`demo_kurodo`, `demo_haru`, `demo_kamekichi`). Uniquement avertissements `dom-6` (best practice narrative) et terminologie offline. |
| T15.9 — Requêtes KB SQLite | ✅ | Sortie brute SQL ci-dessous | Requêtes sur `ips_valuesets` et `ips_valuesets_translations` pour les 4 codes du seed (`74400008`, `233604007`, `22298006`, `56717001`). Les 4 codes sont présents. Base `/tmp/knowledge_full.db` supprimée immédiatement après. |
| Point 4 — Log JEMMA-PROFILE-DETAIL | ✅ | Extrait logcat ci-dessous | `10-01 11:21:27.488 28824 28824 I JEMMA-PROFILE-DETAIL: [t=1790846487488] 🩺 renderPillars · END · active=8/9 · stub=10/9 · missing=0/18 · 1ms` |
| Point 5 — Non-régression rapide | ✅ | `verify-point5.md` | Listes 🧪 (`results_recycler`), 🏥 (`procedures_recycler`), 📟 (`devices_empty_state`) ouvertes sur Kurodo sans crash. Édition d'allergie (pénicilline) puis Save conserve les antécédents (`verify --expect-ph demo_kurodo=2` PASS, 22 checks). |
| Point 6 — Logcat scrubbé | ✅ | `logs/logcat-ui.txt` | Dump des 22 tags explicites du README §4. Filtré par `scrub_logcat.py` (1641 lignes conservées, 0 fuite, 0 crash JemmaPass). |

---

## Extraits JSON bruts demandés (Point 3)

### 1. Condition « Rougeole » (T15.2)
Extrait de `demo_kurodo.fhir.json` (pull après création) :
```json
{
  "fullUrl": "urn:uuid:7e4c4222-5a1d-3c1b-a208-d6704bc56f72",
  "resource": {
    "resourceType": "Condition",
    "id": "0b66661c-1bdc-4858-9ef3-4ea94bfc3211",
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
          "code": "14189004",
          "display": "Measles"
        }
      ],
      "text": "Measles"
    },
    "subject": {
      "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
    },
    "onsetDateTime": "1975",
    "abatementDateTime": "1975"
  }
}
```

### 2. Référence dans la section Composition 11348-0 (History of Past Illness)
Extrait de la ressource `Composition` dans `demo_kurodo.fhir.json` :
```json
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
    },
    {
      "reference": "urn:uuid:7e4c4222-5a1d-3c1b-a208-d6704bc56f72"
    }
  ]
}
```

### 3. Entrée `_j.ph` correspondante (Rougeole)
Extrait de `demo_kurodo.json` :
```json
{
  "c": "14189004",
  "d_display": "Measles",
  "dt": "1975",
  "ab": "1975",
  "sv": "LA6752-5"
}
```

### 4. Condition en texte libre (T15.3 « Hepatite virale enfance »)
Extrait de `demo_kurodo.fhir.json` :
```json
{
  "fullUrl": "urn:uuid:656323c7-56a3-3cf7-891f-40dc121d59be",
  "resource": {
    "resourceType": "Condition",
    "id": "0cb68709-21d5-44d0-8d6b-f7e766a67cc9",
    "meta": {
      "profile": [
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Condition-uv-ips"
      ]
    },
    "clinicalStatus": {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
          "code": "inactive",
          "display": "Inactive"
        }
      ]
    },
    "code": {
      "text": "Hepatite virale enfance"
    },
    "subject": {
      "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
    }
  }
}
```
Entrée correspondante dans `_j.ph` (`demo_kurodo.json`) :
```json
{
  "d_display": "Hepatite virale enfance",
  "st": "inactive"
}
```

---

## Ligne de log JEMMA-PROFILE-DETAIL (Point 4)

Ligne recopiée telle quelle depuis le logcat lors de l'ouverture de la fiche Kurodo :
```
10-01 11:21:27.488 28824 28824 I JEMMA-PROFILE-DETAIL: [t=1790846487488] 🩺 renderPillars · END · active=8/9 · stub=10/9 · missing=0/18 · 1ms
```

> **Observation / Anomalie de comptage (B1)** :
> Le sous-titre de la section dans l'écran de profil indique :
> `Touche un pilier pour le modifier. 9 piliers actifs, 9 à venir.`
> Cependant, la ligne de log indique `active=8/9 · stub=10/9`.
> En analysant `PillarRegistry.kt`, le pilier `contacts` est configuré avec `isActive = false` (passé en stub lors du lot 14.5c12 pour la démo du concours). Les piliers réellement actifs sont au nombre de 8 (`patient`, `allergies`, `medications`, `pastProblems`, `immunizations`, `procedures`, `devices`, `results`), et les stubs sont au nombre de 10. Le dénominateur hardcodé `/9` dans le format de log (`"active=$boundActive/9 · stub=$boundStub/9"`) combiné à la chaîne fixe `9 piliers actifs, 9 à venir` crée cette divergence 8/9 vs 10/9.

---

## Requêtes KB SQLite (Point 2 / T15.9)

Requêtes exécutées sur la base `/tmp/knowledge_full.db` extraite de l'appareil (`/sdcard/Android/data/be.heyman.android.jemmapassdemo/files/knowledge_full_db/1.1/knowledge_full.db`) :

```sql
SELECT code, display_en FROM ips_valuesets WHERE vs_id='problems-snomed-ct-ips-free-set' AND code IN ('74400008','233604007','22298006','56717001') ORDER BY code;
```
Sortie brute :
```
code       display_en           
---------  ---------------------
22298006   Myocardial infarction
233604007  Pneumonia            
56717001   Tuberculosis         
74400008   Appendicitis         
```

```sql
SELECT code, lang, display FROM ips_valuesets_translations WHERE vs_id='problems-snomed-ct-ips-free-set' AND code IN ('74400008','233604007','22298006','56717001') AND lang IN ('fr','ja') ORDER BY code, lang;
```
Sortie brute :
```
code       lang  display                     
---------  ----  ----------------------------
22298006   fr    Infarctus du myocarde       
22298006   ja    心筋梗塞                        
233604007  fr    Pneumopathie infectieuse    
233604007  ja    肺炎                          
56717001   fr    Tuberculose                 
56717001   ja    Not Translated[Tuberculosis]
74400008   fr    Appendicite                 
74400008   ja    虫垂炎                         
```

> **Observation (B2)** : Pour le code SNOMED `56717001` (Tuberculosis), la traduction japonaise dans `ips_valuesets_translations` vaut littéralement `Not Translated[Tuberculosis]`. Le terme japonais standard attendu est `結核`.

---

## Validateur HL7 FHIR (Point 2 / T15.8)

Commande : `java -jar /tmp/fhir-validator/validator_cli.jar files/<persona>.fhir.json -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a`

### Kurodo (`demo_kurodo.fhir.json`)
```
Done. Times: Loading: 01:32.315, validation: 00:01.930. Max Memory = 8Gb
Errors: 0 · Warnings: dom-6 (Best Practice Recommendation narrative)
```

### Haru (`demo_haru.fhir.json`)
```
Done. Times: Loading: 01:06.789, validation: 00:01.023. Max Memory = 8Gb
Errors: 0 · Warnings: dom-6, Observation.code (offline value set)
```

### Kamekichi (`demo_kamekichi.fhir.json`)
```
Done. Times: Loading: 00:55.632, validation: 00:00.676. Max Memory = 8Gb
Errors: 0 · Warnings: dom-6, ATC code system offline, UCUM offline
```

**Verdict structurel : 0 ERREUR sur les 3 personas.**

---

## QR Texte Haru EN / FR / JA (T15.7)

Décodage OpenCV effectué sur les captures d'écran originales :

### EN (`168-qr-past-problems-haru-en.png` — 1327 octets — 1 frame)
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

📜 [ PAST ILLNESSES ]
  ▪️ Myocardial infarction — 2015-08-27 → 2015-09
  ▪️ Tuberculosis — 1962 → 1963

✅ JEMMA on-device · `_j 1.2`
```

### FR (`168-qr-past-problems-haru-fr.png` — 1396 octets — 1 frame)
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

📜 [ ANTÉCÉDENTS MÉDICAUX ]
  ▪️ Myocardial infarction — 2015-08-27 → 2015-09
  ▪️ Tuberculosis — 1962 → 1963

✅ JEMMA on-device · `_j 1.2`
```

### JA (`168-qr-past-problems-haru-ja.png` — 1467 octets — 1 frame)
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

📜 [ 既往歴 ]
  ▪️ Myocardial infarction — 2015-08-27 → 2015-09
  ▪️ Tuberculosis — 1962 → 1963

✅ JEMMA on-device · `_j 1.2`
```

---

## Bugs et anomalies constatées

### B1 — Incohérence décompte piliers actifs (ProfileDetail)
- **Sévérité** : Cosmétique
- **Étapes** :
  1. Ouvrir la fiche d'un profil (ex. Kurodo).
  2. Observer le sous-titre de la section : « 9 piliers actifs, 9 à venir ».
  3. Inspecter le logcat : `🩺 renderPillars · END · active=8/9 · stub=10/9 · missing=0/18`.
- **Attendu / Observé** :
  - Observé : `active=8/9 · stub=10/9` car `contacts` a été basculé en `isActive = false` dans `PillarRegistry.kt` (lot 14.5c12 pour la démo), réduisant les piliers actifs à 8 et montant les stubs à 10. Le format de log et la chaîne de sous-titre sont restés calés sur 9/9.
  - Suggestion : Rendre dynamique le texte de sous-titre `R.string.pillar_subtitle` à partir du décompte réel de `PillarRegistry.ALL.count { it.isActive }` ou réactiver `contacts` comme pilier actif.

### B2 — Traduction japonaise manquante pour Tuberculose (`56717001`)
- **Sévérité** : Mineur (KB)
- **Étapes** :
  1. Interroger la table `ips_valuesets_translations` pour `vs_id='problems-snomed-ct-ips-free-set'` et `code='56717001'`.
- **Attendu / Observé** :
  - Observé : `Not Translated[Tuberculosis]`.
  - Attendu : `結核`.

---

## Fichiers publiés

- `env.txt`
- `steps.md`
- `verify-seed.md`
- `verify-t15-2.md`
- `verify-t15-3.md`
- `verify-t15-5.md`
- `verify-t15-6.md`
- `verify-point5.md`
- `files/demo_kurodo.fhir.json`
- `files/demo_kurodo.json`
- `files/demo_haru.fhir.json`
- `files/demo_haru.json`
- `files/demo_kamekichi.fhir.json`
- `files/demo_kamekichi.json`
- `logs/adb-devices.txt`
- `logs/assemble.log`
- `logs/install.log`
- `logs/unit-tests.log`
- `logs/logcat-ui.txt`
- `screenshots/160-detail-haru-past-problems.png`
- `screenshots/161-past-problems-list.png`
- `screenshots/162-form-rougeole.png`
- `screenshots/163-kurodo-past-problems-3.png`
- `screenshots/164-form-free-text.png`
- `screenshots/165-kurodo-past-problems-4.png`
- `screenshots/166-errors.png`
- `screenshots/167-kurodo-back-to-2.png`
- `screenshots/168-qr-past-problems-haru-en.png`
- `screenshots/168-qr-past-problems-haru-fr.png`
- `screenshots/168-qr-past-problems-haru-ja.png`
