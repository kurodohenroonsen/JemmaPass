# Device QA · feat/ips-18-pillars-cleanup · 08947ec · Pixel 9 Pro XL · Android 17 · 2026-10-01 06:04

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR (fr-BE)
- Appareil : Pixel 9 Pro XL · Android 17 (aucun identifiant matériel ni numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 72 tests, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 5 ✅ · 0 ❌ · 0 ⚠️ · 13 ⏭

---

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_*.fhir.json` | 72 tests JVM réussis (0 échec). Re-seed automatique : Kurodo (💉 4, 🏥 2, 📟 0, 🧪 4), Haru (💉 3, 🏥 2, 📟 2, 🧪 5), Kamekichi (💉 0, 🏥 0, 📟 0, 🧪 1). 64 checks conformes (0 failed). |
| T1 à T13 | ⏭ | — | Hors périmètre court Cycle 10 (consigne : T1–T13 en ⏭). |
| A.1/A.2 Citations FHIR | ✅ | `files/demo_kurodo.fhir.json`, `files/demo_haru.fhir.json` | Vérification des codes SNOMED CT officiels d'allergie : Kurodo = `417532002` (« Allergy to fish »), Haru = `782594005` (« Allergy to soy protein »). |
| A.3 Validateur HL7 (0 erreur) | ✅ | `validator/*.xml`, `validator/*.log` | 6 passes exécutées (-locale en) : 3 offline (`-tx n/a`) + 3 en ligne (`tx.fhir.org`). **ZÉRO ERREUR PARTOUT** sur l'ensemble des 3 personas. |
| A.4 Non-régression UI Allergies | ✅ | `screenshots/c10-kurodo-allergies.png`, `screenshots/c10-haru-allergies.png`, `logs/logcat-ui.txt` | Fiches Haru et Kurodo ouvertes sans crash (`AndroidRuntime:E` vide). Tuile Allergies : libellés FR « Allergie au poisson » et « Allergie aux protéines de soja » confirmés, sans aucun « moisissures » ni « squames ». |
| B. Audit couverture KB | ✅ | `device-reports/kb/kb-coverage-remaining-pillars.txt`, `device-reports/kb/kb-coverage-report.md` | Audit complet sur `knowledge_full.db` (3,1 Go) pour les 8 piliers IPS restants. Copie locale nettoyée. |

---

## A. Zéro erreur FHIR (A)

### 1. Citations vérifiées dans les bundles FHIR générés

Après la correction livrée dans le commit `08947ec` sur `feat/ips-18-pillars-cleanup`, les ressources `AllergyIntolerance` utilisent désormais les codes canoniques SNOMED CT correspondant aux libellés d'affichage :

- **Kurodo Henro (`files/demo_kurodo.fhir.json`) :**
  ```json
  "code": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "417532002",
        "display": "Allergy to fish"
      }
    ],
    "text": "Allergie au poisson"
  }
  ```
- **Haru Tanaka (`files/demo_haru.fhir.json`) :**
  ```json
  "code": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "782594005",
        "display": "Allergy to soy protein"
      }
    ],
    "text": "Allergie aux protéines de soja"
  }
  ```

---

### 2. Rapport du validateur officiel HL7 (6 passes complètes)

Validateur officiel HL7 v6.10.4 (`hl7.fhir.uv.ips#1.1.0`, `-version 4.0.1`, `-locale en`) :

| Persona | Mode de validation | Erreurs | Avertissements | Informations | Statut |
|---|---|:---:|:---:|:---:|:---:|
| **`demo_kurodo`** | Hors-ligne (`-tx n/a`) | **0** | 25 | 0 | ✅ CONFORME |
| **`demo_kurodo`** | En ligne (`tx.fhir.org`) | **0** | 15 | 0 | ✅ CONFORME |
| **`demo_haru`** | Hors-ligne (`-tx n/a`) | **0** | 38 | 0 | ✅ CONFORME |
| **`demo_haru`** | En ligne (`tx.fhir.org`) | **0** | 26 | 1 | ✅ CONFORME |
| **`demo_kamekichi`** | Hors-ligne (`-tx n/a`) | **0** | 28 | 0 | ✅ CONFORME |
| **`demo_kamekichi`** | En ligne (`tx.fhir.org`) | **0** | 17 | 0 | ✅ CONFORME |

> [!IMPORTANT]
> **ZÉRO ERREUR RESTANTE SUR L'ENSEMBLE DES 3 PERSONAS ET DES 2 MODES DE VALIDATION.**
> Les 2 dernières erreurs de display historiques sur `AllergyIntolerance` (squames animales vs poisson, et moisissures vs soja) sont définitivement résolues.

---

### 3. Top 10 des avertissements les plus fréquents

Dédoublonnage et comptage sur l'ensemble des sorties du validateur HL7 pour arbitrage et priorisation :

#### A. En mode connecté (`tx.fhir.org` — 3 personas, 58 avertissements au total)
1. **[51×] Recommandation narrative `dom-6` :**
   `Constraint failed: dom-6: 'A resource should have narrative for robust management' (defined in http://hl7.org/fhir/StructureDefinition/DomainResource) (Best Practice Recommendation)`
   - *Exemple :* `Bundle.entry[0].resource/*Composition/null*/`, `Bundle.entry[1].resource/*Patient/patient-01*/`
2. **[2×] Recommandation lien de contact patient :**
   `No code provided, and a code should be provided from the value set 'Patient Contact Relationship ' (http://hl7.org/fhir/ValueSet/patient-contactrelationship|4.0.1)`
   - *Exemple :* `Bundle.entry[1].resource/*Patient/patient-01*/.contact[0].relationship[0]`
3. **[2×] Annotation d'unité UCUM dans eGFR :**
   `UCUM Codes that contain human readable annotations like {1.73_m2} can be misleading (e.g. they are ignored when comparing units). Best Practice is not to depend on annotations in the UCUM code, so this usage should be checked`
   - *Exemple :* `Bundle.entry[21].resource/*Observation/rs-haru-egfr-2026*/.value.ofType(Quantity)`
4. **[1×] Statut concept LOINC eGFR :**
   `The concept '33914-3' has a status of DISCOURAGED and its use should be reviewed`
   - *Exemple :* `Bundle.entry[21].resource/*Observation/rs-haru-egfr-2026*/.code.coding[0]`
5. **[1×] Recommandation code radiologie IPS :**
   `No code provided, and a code should be provided from the value set 'Results Radiology Observation - IPS' (http://hl7.org/fhir/uv/ips/ValueSet/results-radiology-observations-uv-ips|1.1.0)`
   - *Exemple :* `Bundle.entry[22].resource/*Observation/rs-haru-chest-xray-2025*/.code`
6. **[1×] Concept inactif SNOMED CT :**
   `The concept '300916003' has a status of inactive and its use should be reviewed`
   - *Exemple :* `Bundle.entry[2].resource/*AllergyIntolerance/null*/.code.coding[0]`

#### B. Avertissements spécifiques au mode hors-ligne (`-tx n/a`)
En mode déconnecté, le validateur ne peut interroger les ontologies externes pour les unités ou systèmes sans IG embarqué :
- `[8×] CodeSystem 'http://www.whocc.no/atc' could not be found`
- `[15×] Unable to validate code in 'http://unitsofmeasure.org' ('mg/dL', 'mg', '%', 'mmol/L', 'g/dL')`
- `[6×] CodeSystem 'urn:ietf:bcp:47' could not be found / version null`

---

### 4. Non-régression UI sur appareil (A.4)

Vérification visuelle et fonctionnelle réalisée sur le Pixel 9 Pro XL après re-seed :

1. **Fiche Kurodo Henro (`screenshots/c10-kurodo-allergies.png`) :**
   - Écran des allergies ouvert via la tuile `profile_detail_tile_allergies`.
   - Libellé d'affichage en français : **« Allergie au poisson »** (avec note sous-jacente `Allergy to fish`).
   - Sévérité affichée : `⚠️ Sévère · Active · Urticaria + tongue swelling`.
   - **Absence totale de mention de « squames » animales.**
2. **Fiche Haru Tanaka (`screenshots/c10-haru-allergies.png`) :**
   - Écran des allergies ouvert via la tuile `profile_detail_tile_allergies`.
   - Libellé d'affichage en français : **« Allergie aux protéines de soja »** (avec note sous-jacente `Allergy to soy protein`).
   - Sévérité affichée : `Légère · Active · Mild GI symptoms`.
   - **Absence totale de mention de « moisissures ».**
3. **Stabilité runtime :**
   - Aucune exception ni crash observé (`AndroidRuntime:E` vide dans le logcat).
   - Logcat UI complet nettoyé et consigné dans `logs/logcat-ui.txt`.

---

## B. Audit de couverture de la Base de Connaissances (KB)

Audit complet réalisé sur la base SQLite `knowledge_full.db` (3,1 Go) extraite de l'appareil.
Rapport détaillé et données brutes publiés dans [`device-reports/kb/`](file:///Users/kurodohenroonsen/Documents/jemmapass-device-reports/kb/) :
- Données textuelles brutes de requêtes SQL : [`kb-coverage-remaining-pillars.txt`](file:///Users/kurodohenroonsen/Documents/jemmapass-device-reports/kb/kb-coverage-remaining-pillars.txt)
- Rapport d'analyse et recommandations : [`kb-coverage-report.md`](file:///Users/kurodohenroonsen/Documents/jemmapass-device-reports/kb/kb-coverage-report.md)

### Tableau de couverture des 8 piliers IPS analysés

| Pilier IPS | Statut | Nombre de codes | Value Set(s) source | Langues disponibles | Analyse & Constat ontologique |
|---|---|---|---|---|---|
| **📜 Problèmes antérieurs**<br>*(History of Past Illness)* | **Couvert** *(Riche)* | **5 622** (Free set IPS)<br>+ 69 523 (`terminology_codes`) | `problems-snomed-ct-ips-free-set`<br>`condition-severity-uv-ips`<br>`absent-or-unknown-problems-uv-ips`<br>`problem-type-loinc` / `uv-ips` | **20 langues** sur le free set<br>(FR: 3 596, JA: 3 512, ES: 5 265, EN: 5 622).<br>Sévérité / absence : EN seul. | `problems-snomed-ct-ips-free-set` couvre massivement les pathologies en SNOMED CT avec traductions officielles FR/JA. 46 638 conditions SNOMED dans `terminology_codes` FTS5. Sévérité couverte (3 codes LOINC `LA6750-9`..`LA6752-5`). 46 règles `ips_field_rules`. |
| **🤰 Grossesse**<br>*(Pregnancy)* | **Partiel** | **15 codes** LOINC | `pregnancy-status-uv-ips` (3)<br>`pregnancies-summary-uv-ips` (9)<br>`edd-method-uv-ips` (3) | **Anglais uniquement**<br>(0 traduction dans `ips_valuesets_translations`) | Statut clinique (`LA15173-0` Pregnant, `LA26683-5` Not pregnant, `LA4489-6` Unknown). Antécédents obstétricaux LOINC (naissances, avortements, parité : `11612-9`..`33065-4`). Date estimée d'accouchement (`11778-8`..`11780-4`). Manque les libellés FR/JA. |
| **♿ Statut fonctionnel**<br>*(Functional Status)* | **Absent** | **0 code dédié** | Aucun value set fonctionnel (`functional` / `disability` = 0) | — | Aucun concept d'évaluation d'autonomie ou d'incapacité. 0 règle pour `ClinicalImpression` dans `ips_field_rules`. Seules quelques mentions de déficiences diagnostiques existent dans le free set des problèmes (`problems-snomed-ct-ips-free-set`). |
| **🚬 Antécédents sociaux**<br>*(Social History : Tabac / Alcool)* | **Partiel** (Tabac)<br>**Absent** (Alcool) | **8 codes** (Tabac)<br>**0 code** (Alcool) | `current-smoking-status-uv-ips` (8) | **Anglais uniquement** pour le tabac<br>(0 traduction dans `ips_valuesets_translations`) | Tabagisme modélisé via 8 codes LOINC Answer list (`LA15920-4` Former smoker, `LA18976-3` Current every day smoker, etc.). **Absence totale de ValueSet, codes ou règles pour la consommation d'alcool**. |
| **💓 Signes vitaux**<br>*(Vital Signs)* | **Absent** *(en DB)* | **0 code dédié** dans `ips_valuesets` | Aucun value set vital signs en base SQLite | — | Aucun value set `results-vital-signs-uv-ips` dans la KB. Les panels de signes vitaux LOINC (TA `85354-9`, FC `8867-4`, Temp `8310-5`, FR `9279-1`, SpO2 `2708-6`, Poids `29463-7`, Taille `8302-2`, IMC `39156-5`) sont absents de la DB et doivent être fournis via un catalogue applicatif embarqué. |
| **✍️ Directives anticipées**<br>*(Advance Directives)* | **Absent** *(en terminologie)*<br>*(Structure FHIR couverte)* | **0 code de volonté/mandat** | Aucun value set terminologique pour directives anticipées | — | Aucune terminologie pour les souhaits de fin de vie, limitations de réanimation (DNR/DNI) ou mandataires médicaux. Structurellement, la ressource `Consent` est cadrée par 18 règles dans `ips_field_rules`. |
| **📋 Plan de soins**<br>*(Care Plan)* | **Absent** *(en terminologie)*<br>*(Structure FHIR couverte)* | **0 code d'activité/objectif** | Aucun value set terminologique pour activités de plan de soins | — | Aucun catalogue d'actions, protocoles de surveillance ou objectifs thérapeutiques. En revanche, la ressource `CarePlan` possède 38 règles complètes dans `ips_field_rules`. |
| **⚠️ Alertes**<br>*(Alerts / Flag)* | **Absent** *(en terminologie)*<br>*(Structure FHIR couverte)* | **0 code d'alerte** | Aucun value set terminologique pour alertes cliniques | — | Aucun catalogue de niveaux de criticité ou de types d'alerte médicale d'urgence. La ressource `Flag` dispose de 15 règles structurelles dans `ips_field_rules`. |

---

## Fichiers publiés

- `env.txt`
- `steps.md`
- `verify-seed.md`
- `report.md`
- `files/demo_*.fhir.json` (bundles FHIR générés)
- `validator/demo_*.xml` et `validator/demo_*.log` (rapports d'exécution HL7 v6.10.4)
- `logs/assemble.log`, `logs/unit-tests.log`, `logs/logcat-ui.txt`
- `screenshots/c10-kurodo-allergies.png`, `screenshots/c10-haru-allergies.png`
- `kb/kb-coverage-remaining-pillars.txt` (extractions SQL complètes)
- `kb/kb-coverage-report.md` (synthèse ontologique)
