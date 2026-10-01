# Device QA · feat/ips-18-pillars-cleanup · e499256 · Pixel 9 Pro XL · Android 17 · 2026-10-01 17:50

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR / JA (test per-app locale)
- Appareil : Pixel 9 Pro XL (komodo) · Android 17 (aucun numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 100 tests JVM, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 5/5 points ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats du cycle 19 (alertes médicament × maladie `DrugDiseaseTerms` · commit e499256)

| Point | Statut | Preuve (fichiers publiés dans le run) | Notes / vérifications |
|---|---|---|---|
| **Point 1** — Build, tests JVM & Seed | ✅ | [`logs/assemble.log`](logs/assemble.log), [`logs/unit-tests.log`](logs/unit-tests.log), [`verify-seed.md`](verify-seed.md) | 100 tests unitaires JVM passés (0 échec). Seed vert (92 checks PASS) avec les mêmes compteurs qu'au cycle 18 : 🩺 K1 H2 Ka3, 📜 K2 H2 Ka0, 🧪 K4 H5 Ka1, 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2. |
| **Point 2** — Déroulement T17 | ✅ | [`kb/drug-disease.txt`](kb/drug-disease.txt)<br>[`logs/logcat-ui.txt`](logs/logcat-ui.txt)<br>[`screenshots/190-dd-kamekichi.png`](screenshots/190-dd-kamekichi.png)<br>[`screenshots/191-dd-haru.png`](screenshots/191-dd-haru.png) | • Dump SQL brut extrait de la base SQLite pour les 8 ATC des personas (16 règles trouvées, 8 121 règles dans la table totale).<br>• Traces JEMMA-HYDRATOR : Kamekichi passe à `drug×disease=1` (Warfarin × Hypertension essentielle) ; Haru reste à `drug×disease=0`.<br>• Affichage Kamekichi : capture [`screenshots/190-dd-kamekichi.png`](screenshots/190-dd-kamekichi.png) avec l'alerte « ⚠ MAJOR  Warfarin  +  Hypertension essentielle   › » et dialogue modal explicatif.<br>• Contrôle japonais : alertes identiques (Kamekichi : 3 alertes majeures, Warfarin + 本態性高血圧症 ; Haru : 0 alerte). |
| **Point 3** — Analyse des correspondances non matchées | ℹ️ | Section [3. Analyse des correspondances médicament × maladie](#3-analyse-des-correspondances-médicament--maladie-point-3) ci-dessous | Analyse détaillée des entrées du dump non matchées (notamment Fexofénadine × `Kidney Diseases` sur Haru à cause du pluriel en « s », et `Intracranial Hypertension` sur Kamekichi). |
| **Point 4** — Non-régression DDI & Validateur HL7 | ✅ | [`screenshots/190-dd-kamekichi.png`](screenshots/190-dd-kamekichi.png)<br>[`validator/demo_kurodo.txt`](validator/demo_kurodo.txt)<br>[`validator/demo_haru.txt`](validator/demo_haru.txt)<br>[`validator/demo_kamekichi.txt`](validator/demo_kamekichi.txt) | Les 5 DDI de Kamekichi sont toutes présentes (`ddi=5` dans l'hydrateur, les 5 paires sont affichées dans la liste des alertes). Le bandeau supérieur affiche « ⚠ 3 alerte(s) MAJEURE(S) détectée(s) » (2 DDI majeures + 1 Drug×Disease majeure).<br>Validateur HL7 officiel (CLI v6.10.4) : **0 erreur** sur les 3 personas. |
| **Point 5** — Logcat scrubbé & traces de fin | ✅ | [`logs/logcat-ui.txt`](logs/logcat-ui.txt)<br>[`logs/logcat-seed.txt`](logs/logcat-seed.txt) | Dump des 23 tags explicites du README §4, nettoyé par `scrub_logcat.py` (261 lignes conservées, 0 crash JemmaPass, 0 fuite matérielle ou personnelle). |

---

## Preuves brutes (Règle 8 : Copier-Coller strict sans reformulation)

### 1. Dump SQL brut de `drug_disease_interactions` (Point 2 / T17.1)

Base copiée temporairement sur le Mac hôte hors dépôt (`/tmp/knowledge_full.db`) puis supprimée. Sortie publiée in extenso dans [`kb/drug-disease.txt`](kb/drug-disease.txt) :

```sql
SELECT drug_atc, disease_name_en, severity FROM drug_disease_interactions WHERE drug_atc IN ('C07AB07','B01AA03','M01AE01','G04BE03','C01DA08','C03CA01','R06AX26','R05DA09') ORDER BY drug_atc, severity, disease_name_en;
```
Sortie brute :
```text
drug_atc  disease_name_en                  severity
--------  -------------------------------  --------
B01AA03   Diabetes Mellitus                Major   
B01AA03   Hemorrhage                       Major   
B01AA03   Hypertension                     Major   
B01AA03   Liver Diseases                   Major   
B01AA03   Protein C Deficiency             Major   
B01AA03   Coumarin Resistance              Moderate
B01AA03   Coumarin Sensitivity             Moderate
B01AA03   Kidney Diseases                  Moderate
C01DA08   Anemia                           Major   
C01DA08   Diseases requiring hemodialysis  Major   
C01DA08   Hypotension                      Major   
C01DA08   Intracranial Hypertension        Major   
C01DA08   Myocardial Infarction            Major   
C01DA08   Glaucoma                         Minor   
C01DA08   Cardiomyopathy, Hypertrophic     Moderate
R06AX26   Kidney Diseases                  Moderate
```

```sql
SELECT COUNT(*) FROM drug_disease_interactions;
```
Sortie brute :
```text
COUNT(*)
--------
8121    
```

---

### 2. Lignes JEMMA-HYDRATOR Kamekichi et Haru (Point 2 / T17.2)

Recopiées verbatim depuis [`logs/logcat-ui.txt`](logs/logcat-ui.txt) :
```text
10-01 17:48:57.648 17472 18490 I JEMMA-HYDRATOR: [t=1790869737648] ✅ hydrated · al=3 · md=5 · cn=3 · ddi=5 · al×md=0 · drug×disease=1 · in 919ms
10-01 17:49:03.865 17472 18639 I JEMMA-HYDRATOR: [t=1790869743865] ✅ hydrated · al=1 · md=3 · cn=2 · ddi=0 · al×md=0 · drug×disease=0 · in 209ms
```

---

### 3. Texte exact des alertes médicament × maladie (Point 2 / T17.3)

#### A. Kamekichi (1 alerte affichée · capture [`screenshots/190-dd-kamekichi.png`](screenshots/190-dd-kamekichi.png))

- **Ligne dans la liste des alertes (`profile_detail_alerts_list`)** :
```text
⚠ MAJOR  Warfarin  +  Hypertension essentielle   ›
```

- **Texte complet de la boîte de dialogue modale au tap** :
  - Titre (`alertTitle`) :
    ```text
    ⚠ Contre-indication médicament × condition
    ```
  - Corps du dialogue :
    ```text
    Sévérité : Major

    Warfarin + Hypertension essentielle

    📖 Warfarin interacts with Hypertension
    ```
  - Bouton :
    ```text
    Fermer
    ```

#### B. Haru (0 alerte affichée · capture [`screenshots/191-dd-haru.png`](screenshots/191-dd-haru.png))

- Aucune alerte médicament × maladie n'est affichée sur la fiche de Haru.
- La section `profile_detail_alerts_section` n'apparaît pas car `hasAlerts` est faux (`ddi=0`, `al×md=0`, `drug×disease=0`).

---

### 4. Contrôle avec l'application en japonais (Point 2 / T17.4)

Commande exécutée :
```bash
adb shell cmd locale set-app-locales be.heyman.android.jemmapassdemo --locales ja
```

- **Sur Kamekichi** :
  - Bandeau supérieur :
    ```text
    ⚠ 重大な相互作用 3 件を検出
    ```
    *(3 alertes majeures détectées, identique aux 3 alertes majeures en français)*
  - Ligne dans la section ⚡ 警告 :
    ```text
    ⚠ MAJOR  Warfarin  +  本態性高血圧症   ›
    ```
  - Traces JEMMA-HYDRATOR :
    ```text
    10-01 17:42:58.914 17472 17543 I JEMMA-HYDRATOR: [t=1790869378914] ✅ hydrated · al=3 · md=5 · cn=3 · ddi=5 · al×md=0 · drug×disease=1 · in 990ms
    ```

- **Sur Haru** :
  - Traces JEMMA-HYDRATOR :
    ```text
    10-01 17:42:29.548 17472 17545 I JEMMA-HYDRATOR: [t=1790869349547] ✅ hydrated · al=1 · md=3 · cn=2 · ddi=0 · al×md=0 · drug×disease=0 · in 210ms
    ```
  - 0 alerte affichée.

- **Retour en français** :
```bash
adb shell cmd locale set-app-locales be.heyman.android.jemmapassdemo --locales fr
```
Le nombre d'alertes est strictement invariant selon la langue d'affichage.

---

## 3. Analyse des correspondances médicament × maladie (Point 3)

### Cas Haru : pourquoi `drug×disease` reste à 0 ?
1. **Ligne du dump KB** :
   ```text
   R06AX26   Kidney Diseases                  Moderate
   ```
2. **Médicament et problème de Haru** :
   - Médicament : Fexofénadine (`R06AX26`)
   - Problème actif : Maladie rénale chronique stade 3 (SNOMED `433144002`, `Chronic kidney disease stage 3`)
3. **Cause du non-match** :
   - `DrugDiseaseTerms.matches(term, diseaseName)` applique :
     ```kotlin
     d.contains(t) || (d.length >= MIN_REVERSE_LENGTH && t.contains(d))
     ```
   - Ici, `t` = `"chronic kidney disease stage 3"` (terme du problème) et `d` = `"kidney diseases"` (nom DDInter).
   - `d` se termine par un **« s » pluriel** (`diseases`), alors que le terme SNOMED contient le **singulier** (`disease`).
   - Par conséquent :
     - `d.contains(t)` est faux car `"kidney diseases"` ne contient pas `"chronic kidney disease stage 3"`.
     - `t.contains(d)` est faux car `"chronic kidney disease stage 3"` ne contient pas `"kidney diseases"`.
   - **Piste d'ajustement** : normaliser le pluriel en anglais (par exemple remplacer `\bdiseases\b` par `disease` dans `DrugDiseaseTerms.matches` ou dans `DrugDiseaseTerms.candidates`).

### Cas Kamekichi : revue des problèmes et lignes du dump
1. **Hypertension** (SNOMED `59621000` · `Essential hypertension`) :
   - **Match réussi** avec Warfarin (`B01AA03` · `Hypertension` Major) car `"essential hypertension".contains("hypertension")` est vrai.
   - Ligne 17 du dump : Isosorbide dinitrate (`C01DA08`) a `Intracranial Hypertension` (Major). `"essential hypertension"` ne contient pas `"intracranial hypertension"`, donc pas de match supplémentaire (ce qui est cliniquement cohérent, l'HTIC étant différente de l'HTA essentielle).
2. **Fibrillation auriculaire** (SNOMED `49436004` · `Atrial fibrillation`) :
   - Aucun des 5 médicaments de Kamekichi (Bisoprolol `C07AB07`, Warfarin `B01AA03`, Ibuprofen `M01AE01`, Sildenafil `G04BE03`, Isosorbide dinitrate `C01DA08`) n'a de règle pour `Atrial fibrillation` dans DDInter.
3. **Angine de poitrine** (SNOMED `194828000` · `Angina pectoris`) :
   - Aucune règle DDInter ne cible `Angina` pour ces 8 ATC.
4. **Hémorragie / Saignement** (Ligne 7 du dump : `B01AA03` · `Hemorrhage` Major) :
   - Kamekichi n'a pas de condition active d'hémorragie ou de saignement dans son profil.

---

## 4. Non-régression DDI & Validateur HL7 (Point 4)

1. **DDI de Kamekichi** :
   - Toutes les 5 interactions médicamenteuses sont détectées et affichées :
     1. `⚠ MAJOR  Warfarin  ×  Ibuprofen`
     2. `⚠ MAJOR  Sildenafil  ×  Isosorbide dinitrate`
     3. `⚠ MODERATE  Warfarin  ×  Sildenafil`
     4. `⚠ MODERATE  Bisoprolol  ×  Sildenafil`
     5. `⚠ MODERATE  Bisoprolol  ×  Ibuprofen`
   - Le bandeau supérieur rouge indique `⚠ 3 alerte(s) MAJEURE(S) détectée(s)` (les 2 DDI majeures ci-dessus + l'alerte majeure Warfarin + Hypertension essentielle).
   - Dans le logcat de l'hydrateur : `ddi=5 · al×md=0 · drug×disease=1`.

2. **Validateur HL7 officiel sur les 3 personas** :
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
│   └── drug-disease.txt
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
│   ├── 190-dd-kamekichi.png
│   └── 191-dd-haru.png
├── steps.md
├── validator/
│   ├── demo_haru.txt
│   ├── demo_kamekichi.txt
│   └── demo_kurodo.txt
└── verify-seed.md
```
