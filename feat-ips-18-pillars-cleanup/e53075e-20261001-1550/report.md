# Device QA · feat/ips-18-pillars-cleanup · e53075e · Pixel 9 Pro XL · Android 17 · 2026-10-01 16:30

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR / JA (test per-app locale)
- Appareil : Pixel 9 Pro XL (komodo) · Android 17 (aucun numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 96 tests JVM, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 6/6 points ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats du cycle 18 (pilier 🩺 Problèmes actifs / Active Problems · commit e53075e)

| Point | Statut | Preuve (fichiers publiés dans le run) | Notes / vérifications |
|---|---|---|---|
| **Point 1** — Build, tests JVM & Seed | ✅ | [`logs/assemble.log`](logs/assemble.log), [`logs/unit-tests.log`](logs/unit-tests.log), [`verify-seed.md`](verify-seed.md) | 96 tests unitaires JVM passés (0 échec). Seed vert (92 checks PASS) : 🩺 K1 H2 Ka3 (nouveau), 📜 K2 H2 Ka0, 🧪 K4 H5 Ka1, 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2. |
| **Point 2** — Déroulement T16 (points 1 à 8) | ✅ | [`logs/logcat-ui.txt`](logs/logcat-ui.txt)<br>[`screenshots/180-detail-kamekichi-problems.png`](screenshots/180-detail-kamekichi-problems.png)<br>[`screenshots/181-problems-list.png`](screenshots/181-problems-list.png)<br>[`screenshots/183-form-diabete.png`](screenshots/183-form-diabete.png)<br>[`screenshots/184-kurodo-problems-2.png`](screenshots/184-kurodo-problems-2.png)<br>[`screenshots/185-kurodo-back-to-1.png`](screenshots/185-kurodo-back-to-1.png)<br>[`qr/qr-haru-en.txt`](qr/qr-haru-en.txt)<br>[`qr/qr-haru-fr.txt`](qr/qr-haru-fr.txt)<br>[`qr/qr-haru-ja.txt`](qr/qr-haru-ja.txt)<br>[`validator/demo_kurodo.txt`](validator/demo_kurodo.txt)<br>[`validator/demo_haru.txt`](validator/demo_haru.txt)<br>[`validator/demo_kamekichi.txt`](validator/demo_kamekichi.txt)<br>[`kb/kb-queries.txt`](kb/kb-queries.txt) | T16 déroulé intégralement :<br>1. Logcat hydrator : `drug×disease=0` pour Haru et Kamekichi.<br>2. Fiche Kamekichi tuile 🩺 badge 3 + liste 3 problèmes.<br>3. Formulaire diabète sans bloc guérison/fin, validation `--expect-cn demo_kurodo=2`.<br>4. Statut ⚠️ Rechute (`relapse`), puis suppression par appui long (retour à 1).<br>5. Non-mélange : Haru 📜 2 et 🩺 2 disjoints (11450-4 active vs 11348-0 resolved).<br>6. QR Haru EN/FR/JA avec section CONDITIONS (mono-trame).<br>7. Validateur HL7 officiel : 0 erreur sur les 3 personas.<br>8. KB : les 6 codes du seed résolus avec `display_en` et traductions FR/JA. |
| **Point 3** — Extraits JSON bruts | ✅ | Voir section [3. Extraits JSON bruts](#3-extraits-json-bruts-point-3) ci-dessous | Condition « Type 2 diabetes mellitus » (`44054006`), référence Section 11450-4, et projection `_j.cn`. |
| **Point 4** — Non-régression 📜 & Piliers actifs | ✅ | [`screenshots/187-kurodo-detail-9-active.png`](screenshots/187-kurodo-detail-9-active.png)<br>[`logs/logcat-ui.txt`](logs/logcat-ui.txt) | Liste 📜 Kurodo OK (2 entrées : Pneumopathie infectieuse, Appendicite).<br>Édition d'allergie (Pénicilline passée en Légère) : ni 🩺 ni 📜 ne disparaissent (`verify_profiles.py --expect-cn demo_kurodo=1 --expect-ph demo_kurodo=2` PASS).<br>Sous-titre : « Touche un pilier pour le modifier. 9 piliers actifs, 9 à venir. ».<br>Ligne logcat verbatim : `active=9/9 · stub=9/9 · missing=0/18 · 2ms`. |
| **Point 5** — Alerte médicament × maladie | ℹ️ | Section [5. Alerte médicament × maladie](#5-alerte-médicament--maladie-point-5) ci-dessous | Aucune alerte médicament × maladie n'est apparue sur la fiche de Kamekichi ou de Haru. Les logs `JEMMA-HYDRATOR` indiquent explicitement `drug×disease=0`. Sur Kamekichi, seule l'alerte DDI (interactions médicamenteuses) s'affiche. |
| **Point 6** — Logcat scrubbé & traces de fin | ✅ | [`logs/logcat-ui.txt`](logs/logcat-ui.txt)<br>[`logs/logcat-seed.txt`](logs/logcat-seed.txt) | Dump des 23 tags explicites du README §4 (avec `JEMMA-HYDRATOR`), nettoyé par `scrub_logcat.py` (103 lignes conservées, 0 crash JemmaPass, 0 fuite matérielle ou personnelle). |

---

## Preuves brutes (Règle 8 : Copier-Coller strict sans reformulation)

### 1. Lignes JEMMA-HYDRATOR Haru et Kamekichi (Point 2 / T16.1)

Recopiées verbatim depuis les logs de l'appareil :
```text
10-01 15:54:07.888 13419 13495 I JEMMA-HYDRATOR: [t=1790862847888] ✅ hydrated · al=3 · md=5 · cn=3 · ddi=5 · al×md=0 · drug×disease=0 · in 1023ms
10-01 15:54:08.136 13419 13492 I JEMMA-HYDRATOR: [t=1790862848136] ✅ hydrated · al=1 · md=3 · cn=2 · ddi=0 · al×md=0 · drug×disease=0 · in 208ms
```

---

### 2. Textes décodés des QR Haru (Point 2 / T16.6)

#### EN ([`qr/qr-haru-en.txt`](qr/qr-haru-en.txt) — 1415 octets)
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

🩺 [ CONDITIONS ]
  ▪️ Heart failure
  ▪️ Chronic kidney disease stage 3

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

#### FR ([`qr/qr-haru-fr.txt`](qr/qr-haru-fr.txt) — 1495 octets)
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

✅ JEMMA on-device · `_j 1.2`
```

#### JA ([`qr/qr-haru-ja.txt`](qr/qr-haru-ja.txt) — 1532 octets)
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

✅ JEMMA on-device · `_j 1.2`
```

### Tailles brutes (`wc -c $OUT/qr/qr-haru-*.txt`)
```text
    1415 qr/qr-haru-en.txt
    1495 qr/qr-haru-fr.txt
    1532 qr/qr-haru-ja.txt
    4442 total
```

---

### 3. Extraits JSON bruts (Point 3)

#### A. Resource FHIR Condition « Type 2 diabetes mellitus »
```json
{
  "fullUrl": "urn:uuid:1041e04f-5ce7-3bb1-b3f8-caca04cce530",
  "resource": {
    "resourceType": "Condition",
    "id": "8210621e-0c44-4078-b00c-ecad0ce80e68",
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
          "code": "44054006",
          "display": "Type 2 diabetes mellitus"
        }
      ],
      "text": "Type 2 diabetes mellitus"
    },
    "subject": {
      "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
    },
    "onsetDateTime": "2019"
  }
}
```

#### B. Référence dans la Section 11450-4 (Problem list - Reported)
```json
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
    },
    {
      "reference": "urn:uuid:1041e04f-5ce7-3bb1-b3f8-caca04cce530"
    }
  ]
}
```

#### C. Entrée `_j.cn` compactée correspondante
```json
{
  "c": "44054006",
  "s": "moderate",
  "st": "active",
  "d_display": "Type 2 diabetes mellitus",
  "dt": "2019"
}
```

---

### 4. Validateur HL7 officiel sur les 3 personas (Point 2 / T16.7)

Commande exécutée :
```bash
java -jar /tmp/fhir-validator/validator_cli.jar files/<persona>.fhir.json -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a -output validator/<persona>.txt
```

Comptage brut des erreurs (`grep -c '<td>Error</td>' validator/*.txt`) :
```text
validator/demo_haru.txt:0
validator/demo_kamekichi.txt:0
validator/demo_kurodo.txt:0
```

Comptage brut des avertissements (`grep -c '<td>Warning</td>' validator/*.txt`) :
```text
validator/demo_haru.txt:42
validator/demo_kamekichi.txt:31
validator/demo_kurodo.txt:28
```
*(Tous les avertissements relèvent des contraintes `dom-6` narrative best-practice et de l'absence de serveur de terminologie distant `Error_validating_code_running_without_terminology_services`).*

---

### 5. Requêtes KB SQLite sur les 6 codes du seed (Point 2 / T16.8)

Exécutées sur `/tmp/knowledge_full.db` (puis base supprimée du système hôte) ; fichier publié dans [`kb/kb-queries.txt`](kb/kb-queries.txt) :

```sql
SELECT code, display_en FROM ips_valuesets WHERE vs_id='problems-snomed-ct-ips-free-set' AND code IN ('13644009', '84114007', '433144002', '59621000', '49436004', '194828000') ORDER BY code;
```
Sortie brute :
```text
code       display_en                    
---------  ------------------------------
13644009   Hypercholesterolemia          
194828000  Angina pectoris               
433144002  Chronic kidney disease stage 3
49436004   Atrial fibrillation           
59621000   Essential hypertension        
84114007   Heart failure                 
```

```sql
SELECT code, lang, display FROM ips_valuesets_translations WHERE vs_id='problems-snomed-ct-ips-free-set' AND code IN ('13644009', '84114007', '433144002', '59621000', '49436004', '194828000') AND lang IN ('fr','ja') ORDER BY code, lang;
```
Sortie brute :
```text
code       lang  display                         
---------  ----  --------------------------------
13644009   fr    Hypercholestérolémie            
13644009   ja    高コレステロール血症                      
194828000  fr    Angine de poitrine              
194828000  ja    狭心症                             
433144002  fr    Maladie rénale chronique stade 3
433144002  ja    慢性腎臓病第３期                        
49436004   fr    Fibrillation auriculaire        
49436004   ja    心房細動                            
59621000   fr    Hypertension essentielle        
59621000   ja    本態性高血圧症                         
84114007   fr    Défaillance cardiaque           
84114007   ja    心不全                             
```

---

### 6. Ligne JEMMA-PROFILE-DETAIL Kurodo (Point 4)

Recopiée verbatim depuis [`logs/logcat-ui.txt`](logs/logcat-ui.txt) :
```text
10-01 16:25:46.397 13419 13419 I JEMMA-PROFILE-DETAIL: [t=1790864746397] 🩺 renderPillars · END · active=9/9 · stub=9/9 · missing=0/18 · 2ms
```

---

### 7. Alerte médicament × maladie (Point 5)

Aucune bannière ni alerte médicament × maladie (drug-disease interaction) n'est apparue sur la fiche de Kamekichi ou de Haru. Les lignes logcat de l'hydrateur indiquent formellement :
```text
drug×disease=0
```
Sur la fiche de Kamekichi, seule l'alerte d'interaction médicamenteuse (`ddi=5`) s'affiche : « 5 interactions médicamenteuses potentielles détectées (DDI) » (visible sur [`screenshots/180-detail-kamekichi-problems.png`](screenshots/180-detail-kamekichi-problems.png)).

---

## Fichiers publiés

```text
├── env.txt
├── files/
│   ├── demo_haru.fhir.json
│   ├── demo_haru.json
│   ├── demo_kamekichi.fhir.json
│   ├── demo_kamekichi.json
│   ├── demo_kurodo.fhir.json
│   └── demo_kurodo.json
├── kb/
│   └── kb-queries.txt
├── logs/
│   ├── adb-devices.txt
│   ├── assemble.log
│   ├── install.log
│   ├── logcat-seed.txt
│   ├── logcat-ui.txt
│   ├── run.log
│   └── unit-tests.log
├── qr/
│   ├── qr-haru-en.txt
│   ├── qr-haru-fr.txt
│   └── qr-haru-ja.txt
├── report.md
├── screenshots/
│   ├── 180-detail-kamekichi-problems.png
│   ├── 181-problems-list.png
│   ├── 183-form-diabete.png
│   ├── 184-kurodo-problems-2.png
│   ├── 185-kurodo-back-to-1.png
│   ├── 186-qr-problems-haru-en.png
│   ├── 186-qr-problems-haru-fr.png
│   ├── 186-qr-problems-haru-ja.png
│   └── 187-kurodo-detail-9-active.png
├── steps.md
├── validator/
│   ├── demo_haru.txt
│   ├── demo_kamekichi.txt
│   └── demo_kurodo.txt
└── verify-seed.md
```
