# Device QA · feat/ips-18-pillars-cleanup · 8851a03 · Pixel 9 Pro XL · Android 17 · 2026-10-01 15:15

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR / JA (test per-app locale)
- Appareil : Pixel 9 Pro XL (komodo) · Android 17 (aucun numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 89 tests JVM, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 6/6 points ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats du cycle 17 (vérification commit 8851a03 + Règle 8)

| Point | Statut | Preuve (fichiers publiés dans le run) | Notes / vérifications |
|---|---|---|---|
| **Point 1** — Build, tests JVM & Seed | ✅ | [`logs/assemble.log`](logs/assemble.log), [`logs/unit-tests.log`](logs/unit-tests.log), [`verify-seed.md`](verify-seed.md) | 89 tests unitaires JVM passés (0 échec). Seed vert (77 checks PASS) : 📜 K2 H2 Ka0, 🧪 K4 H5 Ka1, 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2. |
| **Point 2** — QR texte Haru EN/FR/JA (Règle 8) | ✅ | [`screenshots/168-qr-past-problems-haru-en.png`](screenshots/168-qr-past-problems-haru-en.png)<br>[`screenshots/168-qr-past-problems-haru-fr.png`](screenshots/168-qr-past-problems-haru-fr.png)<br>[`screenshots/168-qr-past-problems-haru-ja.png`](screenshots/168-qr-past-problems-haru-ja.png)<br>[`qr/qr-haru-en.txt`](qr/qr-haru-en.txt)<br>[`qr/qr-haru-fr.txt`](qr/qr-haru-fr.txt)<br>[`qr/qr-haru-ja.txt`](qr/qr-haru-ja.txt) | Textes décodés bruts sauvegardés par redirection stdout. Tailles : EN = 1327 o, FR = 1395 o, JA = 1458 o (tous mono-trame <= 2200 o).<br>FR : « Infarctus du myocarde — 2015-08-27 → 2015-09 » et « Tuberculose — 1962 → 1963 ».<br>JA : « 心筋梗塞 — 2015-08-27 → 2015-09 » et « Tuberculosis — 1962 → 1963 » (repli anglais propre pour 56717001 sans traduction JA, aucun placeholder). |
| **Point 3** — Fiche & liste Haru en japonais | ✅ | [`screenshots/169-detail-haru-ja.png`](screenshots/169-detail-haru-ja.png)<br>[`screenshots/169-past-problems-list-ja.png`](screenshots/169-past-problems-list-ja.png) | Bascule per-app locale via `cmd locale set-app-locales be.heyman.android.jemmapassdemo --locales ja` sans `pm clear`. La section 📜 de la fiche et la liste `past_problems_recycler` affichent « 心筋梗塞 » et « Tuberculosis ». Aucun « Not Translated[ ». |
| **Point 4** — Compteur de piliers fiche Kurodo | ✅ | [`screenshots/170-kurodo-detail-8-active.png`](screenshots/170-kurodo-detail-8-active.png)<br>[`logs/logcat-ui.txt`](logs/logcat-ui.txt) | Le sous-titre affiche « Touche un pilier pour le modifier. 8 piliers actifs, 10 à venir. » (capture 170).<br>Ligne logcat verbatim : `active=8/8 · stub=10/10 · missing=0/18 · 4ms`. |
| **Point 5** — Validateur HL7 officiel (Règle 8) | ✅ | [`validator/demo_kurodo.txt`](validator/demo_kurodo.txt)<br>[`validator/demo_haru.txt`](validator/demo_haru.txt)<br>[`validator/demo_kamekichi.txt`](validator/demo_kamekichi.txt) | CLI v6.10.4 (`-version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a -output …`). Les 3 fichiers de sortie sont publiés in extenso. Comptage brut : **0 erreur** sur les 3 personas. |
| **Point 6** — Logcat scrubbé & traces de fin | ✅ | [`logs/logcat-ui.txt`](logs/logcat-ui.txt)<br>[`logs/logcat-seed.txt`](logs/logcat-seed.txt) | Dump des 22 tags explicites (§4), nettoyé par `scrub_logcat.py` (64 lignes conservées, 0 crash JemmaPass, 0 fuite de données personnelles ou matérielles). |

---

## Preuves brutes (Règle 8 : Copier-Coller strict sans reformulation)

### 1. Textes décodés des QR Haru (Point 2)

#### EN ([`qr/qr-haru-en.txt`](qr/qr-haru-en.txt) — 1327 octets)
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

#### FR ([`qr/qr-haru-fr.txt`](qr/qr-haru-fr.txt) — 1395 octets)
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
  ▪️ Infarctus du myocarde — 2015-08-27 → 2015-09
  ▪️ Tuberculose — 1962 → 1963

✅ JEMMA on-device · `_j 1.2`
```

#### JA ([`qr/qr-haru-ja.txt`](qr/qr-haru-ja.txt) — 1458 octets)
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
  ▪️ 心筋梗塞 — 2015-08-27 → 2015-09
  ▪️ Tuberculosis — 1962 → 1963

✅ JEMMA on-device · `_j 1.2`
```

### Tailles brutes (`wc -c $OUT/qr/qr-haru-*.txt`)
```text
    1327 qr/qr-haru-en.txt
    1395 qr/qr-haru-fr.txt
    1458 qr/qr-haru-ja.txt
    4180 total
```

---

### 2. Ligne JEMMA-PROFILE-DETAIL Kurodo (Point 4)

Recopiée verbatim depuis [`logs/logcat-ui.txt`](logs/logcat-ui.txt) :
```text
10-01 15:08:27.688 10931 10931 I JEMMA-PROFILE-DETAIL: [t=1790860107688] 🩺 renderPillars · END · active=8/8 · stub=10/10 · missing=0/18 · 4ms
```

---

### 3. Validateur HL7 officiel sur les 3 personas (Point 5)

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
validator/demo_haru.txt:40
validator/demo_kamekichi.txt:28
validator/demo_kurodo.txt:27
```
*(Tous les avertissements relèvent des contraintes `dom-6` narrative best-practice et de l'exécution hors-ligne sans serveur de terminologie `Error_validating_code_running_without_terminology_services`).*

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
├── screenshots/
│   ├── 168-qr-past-problems-haru-en.png
│   ├── 168-qr-past-problems-haru-fr.png
│   ├── 168-qr-past-problems-haru-ja.png
│   ├── 169-detail-haru-ja.png
│   ├── 169-past-problems-list-ja.png
│   └── 170-kurodo-detail-8-active.png
├── steps.md
├── validator/
│   ├── demo_haru.txt
│   ├── demo_kamekichi.txt
│   └── demo_kurodo.txt
└── verify-seed.md
```
