# Device QA · feat/ips-18-pillars-cleanup · 4473749 · Pixel 9 Pro XL · Android 17 · 2026-10-01 18:25

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR
- Appareil : Pixel 9 Pro XL (komodo) · Android 17 (aucun numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 101 tests JVM, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 5/5 points ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats du cycle 20 (pluriels médicament × maladie & couverture DDInter · commit 4473749)

| Point | Statut | Preuve (fichiers publiés dans le run) | Notes / vérifications |
|---|---|---|---|
| **Point 1** — Build, tests JVM & Seed | ✅ | [`logs/assemble.log`](logs/assemble.log)<br>[`logs/unit-tests.log`](logs/unit-tests.log)<br>[`verify-seed.md`](verify-seed.md) | 101 tests unitaires JVM passés (0 échec). Seed vert (92 checks PASS) avec les compteurs attendus : 🩺 K1 H2 Ka3, 📜 K2 H2 Ka0, 🧪 K4 H5 Ka1, 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2. |
| **Point 2** — Déroulement T18 | ✅ | [`kb/ddinter-coverage.txt`](kb/ddinter-coverage.txt)<br>[`logs/logcat-ui.txt`](logs/logcat-ui.txt)<br>[`screenshots/195-dd-haru.png`](screenshots/195-dd-haru.png)<br>[`screenshots/196-dd-haru-dialog.png`](screenshots/196-dd-haru-dialog.png)<br>[`screenshots/197-dd-kamekichi.png`](screenshots/197-dd-kamekichi.png)<br>[`screenshots/198-dd-kamekichi-dialog.png`](screenshots/198-dd-kamekichi-dialog.png) | • Haru : ligne JEMMA-HYDRATOR `drug×disease=1` (Fexofénadine × Maladie rénale chronique stade 3 débloqué grâce à l'insensibilité au pluriel `Kidney Diseases` ↔ `disease`). Capture [`screenshots/195-dd-haru.png`](screenshots/195-dd-haru.png) et dialogue [`screenshots/196-dd-haru-dialog.png`](screenshots/196-dd-haru-dialog.png) avec texte exact recopié.<br>• Kamekichi : toujours `drug×disease=1` (Warfarin + Hypertension essentielle), 5 DDI confirmées.<br>• 3 requêtes SQL de couverture DDInter exécutées et publiées dans [`kb/ddinter-coverage.txt`](kb/ddinter-coverage.txt). |
| **Point 3** — Analyse des correspondances sous ATC différent ou NULL | ℹ️ | Sorties brutes dans la section [3. Analyse de couverture DDInter](#3-analyse-de-couverture-ddinter-point-3) ci-dessous | Les 4 molécules (Ibuprofène, Furosémide, Bisoprolol, Sildénafil) ont bien des règles dans `drug_disease_interactions` (13, 9, 18 et 9 lignes) mais sont indexées sous leur `primary_atc` respectif (`G02CC01`, `C03EB01`, `C07FX04`, `G01AE10`), distinct des ATC thérapeutiques usuels utilisés dans les profils démo (`M01AE01`, `C03CA01`, `C07AB07`, `G04BE03`). Leurs codes ATC démo sont tous référencés dans `ddinter_drugs.atc_codes`. |
| **Point 4** — Non-régression DDI & Validateur HL7 | ✅ | [`screenshots/197-dd-kamekichi.png`](screenshots/197-dd-kamekichi.png)<br>[`validator/demo_kurodo.txt`](validator/demo_kurodo.txt)<br>[`validator/demo_haru.txt`](validator/demo_haru.txt)<br>[`validator/demo_kamekichi.txt`](validator/demo_kamekichi.txt) | • Kamekichi conserve ses 5 DDI intactes (`ddi=5` dans l'hydrateur).<br>• Validateur HL7 officiel (CLI v6.10.4) : **0 erreur** sur les 3 personas (Kurodo: 0 err / 28 warn, Haru: 0 err / 42 warn, Kamekichi: 0 err / 31 warn). |
| **Point 5** — Logcat scrubbé & publication | ✅ | [`logs/logcat-ui.txt`](logs/logcat-ui.txt)<br>[`logs/logcat-seed.txt`](logs/logcat-seed.txt) | Dump des 23 tags explicites du README §4, nettoyé par `scrub_logcat.py` (155 lignes conservées, 0 crash JemmaPass, 0 fuite matérielle ou personnelle). |

---

## Preuves brutes (Règle 8 : Copier-Coller strict sans reformulation)

### 1. Lignes JEMMA-HYDRATOR Haru et Kamekichi (Point 2 / T18)

Recopiées verbatim depuis [`logs/logcat-ui.txt`](logs/logcat-ui.txt) :

- **Haru Tanaka** :
```text
10-01 18:03:36.362 19115 19177 I JEMMA-HYDRATOR: [t=1790870616362] ✅ hydrated · al=1 · md=3 · cn=2 · ddi=0 · al×md=0 · drug×disease=1 · in 162ms
10-01 18:07:57.115 19115 19176 I JEMMA-HYDRATOR: [t=1790870877115] ✅ hydrated · al=1 · md=3 · cn=2 · ddi=0 · al×md=0 · drug×disease=1 · in 202ms
```

- **Kamekichi** :
```text
10-01 18:03:36.171 19115 19177 I JEMMA-HYDRATOR: [t=1790870616171] ✅ hydrated · al=3 · md=5 · cn=3 · ddi=5 · al×md=0 · drug×disease=1 · in 922ms
10-01 18:09:26.586 19115 19566 I JEMMA-HYDRATOR: [t=1790870966586] ✅ hydrated · al=3 · md=5 · cn=3 · ddi=5 · al×md=0 · drug×disease=1 · in 994ms
```

---

### 2. Texte exact de l'alerte et du dialogue — Haru Tanaka (Point 2 / T18)

Captures : [`screenshots/195-dd-haru.png`](screenshots/195-dd-haru.png) (liste) et [`screenshots/196-dd-haru-dialog.png`](screenshots/196-dd-haru-dialog.png) (dialogue).

- **Ligne affichée dans la liste des alertes (`profile_detail_alerts_list`)** :
```text
⚠ MODERATE  Fexofénadine  +  Maladie rénale chronique stade 3   ›
```

- **Boîte de dialogue modale au tap** :
  - **Titre (`alertTitle`)** :
    ```text
    ⚠ Contre-indication médicament × condition
    ```
  - **Corps du message** :
    ```text
    Sévérité : Moderate

    Fexofénadine + Maladie rénale chronique stade 3

    📖 Fexofenadine interacts with Kidney Diseases
    ```
  - **Bouton** :
    ```text
    Fermer
    ```

---

### 3. Texte exact de l'alerte et du dialogue — Kamekichi (Point 2 / T18)

Captures : [`screenshots/197-dd-kamekichi.png`](screenshots/197-dd-kamekichi.png) (liste) et [`screenshots/198-dd-kamekichi-dialog.png`](screenshots/198-dd-kamekichi-dialog.png) (dialogue).

- **Ligne affichée dans la liste des alertes (`profile_detail_alerts_list`)** :
```text
⚠ MAJOR  Warfarin  +  Hypertension essentielle   ›
```

- **Boîte de dialogue modale au tap** :
  - **Titre (`alertTitle`)** :
    ```text
    ⚠ Contre-indication médicament × condition
    ```
  - **Corps du message** :
    ```text
    Sévérité : Major

    Warfarin + Hypertension essentielle

    📖 Warfarin interacts with Hypertension
    ```
  - **Bouton** :
    ```text
    Fermer
    ```

---

### 4. Requêtes de couverture DDInter (Point 2 / T18)

Base SQLite extraite temporairement sur la machine hôte (`/tmp/knowledge_full.db`) puis supprimée immédiatement. Sortie brute publiée dans [`kb/ddinter-coverage.txt`](kb/ddinter-coverage.txt) :

```text
=== SQL QUERY 1: Total interactions vs with drug_atc ===
SELECT COUNT(*), COUNT(drug_atc) FROM drug_disease_interactions;

COUNT(*)  COUNT(drug_atc)
--------  ---------------
8121      7591           


=== SQL QUERY 2: Drug disease interactions for the 8 persona drugs ===
SELECT drug_name, drug_atc, COUNT(*) FROM drug_disease_interactions WHERE lower(drug_name) IN ('ibuprofen','furosemide','bisoprolol','sildenafil','dextromethorphan','fexofenadine','warfarin','isosorbide dinitrate') GROUP BY 1,2;

drug_name             drug_atc  COUNT(*)
--------------------  --------  --------
Bisoprolol            C07FX04   18      
Fexofenadine          R06AX26   1       
Furosemide            C03EB01   9       
Ibuprofen             G02CC01   13      
Isosorbide dinitrate  C01DA08   7       
Sildenafil            G01AE10   9       
Warfarin              B01AA03   8       


=== SQL QUERY 3: ddinter_drugs primary_atc and atc_codes ===
SELECT d.name, d.primary_atc, d.atc_codes FROM ddinter_drugs d WHERE lower(d.name) IN ('ibuprofen','furosemide','bisoprolol','sildenafil');

name        primary_atc  atc_codes                                                      
----------  -----------  ---------------------------------------------------------------
Sildenafil  G01AE10      G01AE10,G04BE03                                                
Bisoprolol  C07FX04      C07FX04,C07BB07,C07AB07,C09BX05,C09BX02,C09BX04,C07FB07        
Furosemide  C03EB01      C03EB01,C03CA01,G01AE10,C03CB01                                
Ibuprofen   G02CC01      G02CC01,C01EB16,M01AE01,M02AA13,R02AX02,N02AJ08,N02AJ19,M01AE51
```

---

## 3. Analyse de couverture DDInter (Point 3)

### Diagnostic des lignes brutes sous `drug_atc` différent

Les 4 molécules interrogées renvoient toutes des lignes dans `drug_disease_interactions`, mais chacune est enregistrée sous son `primary_atc` DDInter (code primaire de la molécule dans DDInter), qui diffère du code ATC précis de la forme ou indication clinique utilisée dans les personas :

| Molécule | `drug_atc` dans `drug_disease_interactions` | Lignes | ATC utilisé dans le persona | Présence dans `ddinter_drugs.atc_codes` |
|---|---|---|---|---|
| **Bisoprolol** | `C07FX04` *(bisoprolol et autres antihypertenseurs)* | 18 | `C07AB07` *(bisoprolol seul, bêta-bloquant)* | ✅ Oui (`C07FX04,C07BB07,C07AB07,C09BX05,C09BX02,C09BX04,C07FB07`) |
| **Furosémide** | `C03EB01` *(furosémide et dérivés épargneurs de potassium)* | 9 | `C03CA01` *(furosémide seul, diurétique de l'anse)* | ✅ Oui (`C03EB01,C03CA01,G01AE10,C03CB01`) |
| **Ibuprofène** | `G02CC01` *(ibuprofène gynécologique)* | 13 | `M01AE01` *(ibuprofène AINS oral)* | ✅ Oui (`G02CC01,C01EB16,M01AE01,M02AA13,R02AX02,N02AJ08,N02AJ19,M01AE51`) |
| **Sildénafil** | `G01AE10` *(anti-infectieux/antiseptique gynécologique)* | 9 | `G04BE03` *(sildénafil urologie / dysfonction érectile)* | ✅ Oui (`G01AE10,G04BE03`) |

### Recommandation pour élargir la recherche

Actuellement, `drug_disease_interactions` n'est requêté que sur le champ `drug_atc` qui contient le `primary_atc`.
Pour couvrir l'ensemble des formes cliniques sans manquer les interactions médicament × maladie :

1. **Recherche via `ddinter_drugs` (Recommandé)** :
   Résoudre l'ATC du médicament du patient (ex. `M01AE01`) vers son `primary_atc` via la table `ddinter_drugs` :
   ```sql
   SELECT primary_atc FROM ddinter_drugs WHERE atc_codes LIKE '%' || :atc || '%';
   ```
   ou directement dans la requête de croisement :
   ```sql
   SELECT ddi.*
   FROM drug_disease_interactions ddi
   WHERE ddi.drug_atc = :atc
      OR ddi.drug_atc IN (
          SELECT primary_atc FROM ddinter_drugs WHERE atc_codes LIKE '%' || :atc || '%'
      );
   ```
2. **Recherche alternative par nom de substance (`drug_name`)** :
   En complément, si le médicament est identifié par son nom générique (ex: `lower(drug_name) = 'ibuprofen'`), requêter par `drug_name` lorsque l'ATC direct n'a pas retourné de résultat.

---

## 4. Non-régression DDI & Validateur HL7 (Point 4)

### 1. DDI de Kamekichi
Les 5 interactions médicamenteuses de Kamekichi sont confirmées et affichées :
1. `⚠ MAJOR  Warfarin  ×  Ibuprofen   ›` (Synergie)
2. `⚠ MAJOR  Sildenafil  ×  Isosorbide dinitrate   ›` (Synergie)
3. `⚠ MODERATE  Warfarin  ×  Sildenafil   ›` (Synergie)
4. `⚠ MODERATE  Bisoprolol  ×  Sildenafil   ›` (Synergie)
5. `⚠ MODERATE  Bisoprolol  ×  Ibuprofen   ›` (Antagonisme)

Et le croisement médicament × maladie :
6. `⚠ MAJOR  Warfarin  +  Hypertension essentielle   ›`

Le bandeau d'alerte affiche : `⚠ 3 alerte(s) MAJEURE(S) détectée(s)` (les 2 DDI majeures + 1 Drug×Disease majeure).
Dans `logs/logcat-ui.txt` : `ddi=5 · al×md=0 · drug×disease=1`.

### 2. Validateur HL7 officiel sur les 3 personas
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
*(0 erreur sur l'ensemble des 3 personas. Les avertissements correspondent aux règles `dom-6` narrative best-practice et à l'exécution hors serveur de terminologie distant).*

---

## 5. Fichiers publiés

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
│   └── ddinter-coverage.txt
├── logs/
│   ├── adb-devices.txt
│   ├── assemble.log
│   ├── install.log
│   ├── logcat-seed.txt
│   ├── logcat-ui.txt
│   ├── run.log
│   └── unit-tests.log
├── report.md
├── screenshots/
│   ├── 195-dd-haru.png
│   ├── 196-dd-haru-dialog.png
│   ├── 197-dd-kamekichi.png
│   └── 198-dd-kamekichi-dialog.png
├── steps.md
├── validator/
│   ├── demo_haru.txt
│   ├── demo_kamekichi.txt
│   └── demo_kurodo.txt
└── verify-seed.md
```
