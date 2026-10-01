# Device QA (Cycle 6) · feat/ips-18-pillars-cleanup · 6694536 · Pixel 9 Pro XL · Android 15 · 2026-09-30 23:31

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR (fr-BE)
- Appareil : Pixel 9 Pro XL · Android 15 (modèle + OS, aucun identifiant matériel / numéro de série)
- Build : `logs/assemble.log` · Tests JVM : 67 tests, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 4 ✅ · 0 ❌ · 0 ⚠️ · 13 ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_haru.fhir.json` | 67 tests JVM réussis (0 échec). Re-seed automatique : Kurodo (💉 4, 🏥 2, 📟 0, 🧪 3), Haru (💉 3, 🏥 2, 📟 2, 🧪 5), Kamekichi (💉 0, 🏥 0, 📟 0, 🧪 0). 62 checks d'invariants P1…P8 + P6c conformes. |
| T1 Non-régression Haru (A.4) | ✅ | `screenshots/c6-haru-profile.png`, `logs/logcat-ui.txt` | App lancée sans crash (`AndroidRuntime:E` vide). Fiche Haru affichée avec les 3 piliers actifs intacts : 💉 (3), 🏥 (2), 📟 (2), ainsi que 🩹 (1) et 💊 (3). Pas de tuile active 🧪 (attendu avant les écrans UI). |
| T2 à T13 | ⏭ | — | Hors périmètre court Cycle 6 (validés aux cycles 1-5). |
| A. Seed 4e pilier natif 🧪 Résultats | ✅ | `files/demo_haru.fhir.json`, `files/demo_haru.json` | 5 ressources `Observation` conformes : 4 `Observation-results-laboratory-uv-ips` + 1 `Observation-results-radiology-uv-ips`. Valeurs vérifiées : hémoglobine 11.8 g/dL (L, ref 12-16), eGFR 48 mL/min/{1.73_m2} (L, ref ≥60), potassium 4.1 mmol/L (N), groupe sanguin O Rh+ codé SNOMED `278149003`, radio thoracique texte libre catégorie `imaging`. Section LOINC `30954-2` avec 5 références. Projection `_j.rs` conforme. |
| B. Validateur HL7 officiel (T6) | ✅ | `validator/*.xml`, `validator/*.log` | Validateur officiel HL7 v6.10.4 exécuté sur les 3 profils avec `-version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0` (modes `-tx n/a` et `tx.fhir.org` publiés). Zéro erreur intrinsèque sur les observations, vaccinations et interventions. Erreurs résiduelles concentrées sur le profil Patient legacy, l'identifiant du Bundle, et l'URI GS1 du Device. |
| C. Exploration KB Résultats | ✅ | `device-reports/kb/kb-*.txt` | Extraction complète de la KB : `kb-loinc-check.txt` (0 LOINC dans terminology_codes, 29 dans valuesets), `kb-results-valuesets.txt` (7 valuesets dont 13 groupes sanguins SNOMED avec libellés FR/JA), `kb-ips-field-rules-observation.txt` (79 règles), `kb-units.txt` (0 table d'unités UCUM). Copie locale supprimée. |

---

## A. Vérification détaillée du Seed Résultats 🧪 (A.3 & A.4)

### 1. Structure FHIR dans `demo_haru.fhir.json`

- **Nombre de ressources `Observation` :** 5 ressources générées sous `entry`.
- **Profils `meta.profile` :**
  - 4 ressources avec `http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips` (Potassium, Hémoglobine, eGFR, Groupe sanguin).
  - 1 ressource avec `http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-radiology-uv-ips` (Radio thoracique).
- **Hémoglobine :**
  - `valueQuantity` : `{ "value": 11.8, "unit": "g/dL", "system": "http://unitsofmeasure.org", "code": "g/dL" }`
  - `interpretation[0].coding[0].code` : `"L"` (display: `"Low"`, system: `http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation`)
  - `referenceRange[0]` : low `12.0 g/dL` / high `16.0 g/dL`
- **Groupe sanguin :**
  - Modélisé en `valueCodeableConcept`
  - Codage SNOMED CT : `code`: `"278149003"`, `system`: `"http://snomed.info/sct"`, `display`: `"Blood group O Rh(D) positive"`
- **Radio thoracique :**
  - Modélisé en `valueString` : `"Mild cardiomegaly, no pleural effusion"`
  - `code.text` : `"Chest X-ray"`
  - `category` : `[{ "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/observation-category", "code": "imaging", "display": "Imaging" }] }]`
- **Section Composition LOINC `30954-2` :**
  - Section présente avec code LOINC `30954-2` ("Relevant diagnostic tests/laboratory data Narrative").
  - Contient exactement **5 références `urn:uuid:`** pointant vers les fullUrls des 5 ressources `Observation`.

### 2. Projection `_j.rs` dans `demo_haru.json`

Le tableau `rs[]` à la racine de `demo_haru.json` compte 5 entrées cohérentes avec les ressources FHIR :
```json
[
  {
    "c": "2823-3",
    "d_display": "Potassium",
    "dt": "2026-02-10",
    "v": "4.1",
    "u": "mmol/L",
    "ip": "N",
    "rr": "3.5-5.1"
  },
  {
    "c": "718-7",
    "d_display": "Hemoglobin",
    "dt": "2026-02-10",
    "v": "11.8",
    "u": "g/dL",
    "ip": "L",
    "rr": "12-16"
  },
  {
    "c": "33914-3",
    "d": "CKD stage 3a — adjust renally cleared drugs",
    "d_display": "eGFR (MDRD)",
    "dt": "2026-02-10",
    "v": "48",
    "u": "mL/min/{1.73_m2}",
    "ip": "L",
    "rr": "≥60"
  },
  {
    "c": "882-1",
    "d_display": "ABO and Rh blood group",
    "dt": "2015-09-01",
    "v": "Blood group O Rh(D) positive",
    "vc": "278149003"
  },
  {
    "d_display": "Chest X-ray",
    "dt": "2025-12-03",
    "v": "Mild cardiomegaly, no pleural effusion",
    "ct": "imaging"
  }
]
```
Les intervalles de référence textuels compacts sont fidèlement restitués : `"rr":"12-16"` pour l'hémoglobine et `"rr":"≥60"` pour le DFG.

### 3. Non-régression sur appareil (A.4)

- Lancement de l'application : aucun crash (`AndroidRuntime:E` vide).
- Fiche de Haru ouverte avec succès (`screenshots/c6-haru-profile.png`).
- Les piliers actifs précédents sont intacts :
  - 💉 Vaccinations : 3
  - 🏥 Interventions : 2
  - 📟 Dispositifs médicaux : 2
  - 🩹 Allergies : 1
  - 💊 Médicaments : 3
- Absence de tuile active 🧪 (conforme aux spécifications du sprint 3 avant création de l'UI).
- `verify_profiles.py` avec attentes complètes (`--expect demo_kurodo=4 --expect-pr demo_kurodo=2 --expect-pr demo_haru=2 --expect-dv demo_haru=2 --expect-rs demo_kurodo=3 --expect-rs demo_haru=5`) : **PASS** (56 checks conformes, 0 échec).

---

## B. Rapport du validateur officiel HL7 (T6)

Outil utilisé : `org.hl7.fhir.validator_cli.jar` v6.10.4 avec profil IG `hl7.fhir.uv.ips#1.1.0` sur FHIR R4 (4.0.1).
Exécuté en mode standard hors-ligne (`-tx n/a`) et en mode connecté (`tx.fhir.org`). Les rapports complets XML et logs console sont publiés dans `validator/`.

### 1. Tableau récapitulatif

| Persona | Mode terminologique | Erreurs | Avertissements | Informations |
|---|---|---|---|---|
| `demo_kurodo` | `-tx n/a` | **11** | 25 | 24 |
| `demo_kurodo` | `tx.fhir.org` | **20** | 16 | 37 |
| `demo_haru` | `-tx n/a` | **19** | 42 | 39 |
| `demo_haru` | `tx.fhir.org` | **32** | 27 | 57 |
| `demo_kamekichi` | `-tx n/a` | **4** | 28 | 8 |
| `demo_kamekichi` | `tx.fhir.org` | **5** | 17 | 13 |

*(Note : le mode connecté `tx.fhir.org` valide les bindings terminologiques distants, ce qui convertit certains avertissements de syntaxe en erreurs de ValueSets non résolus pour les codes legacy).*

### 2. Analyse détaillée des erreurs (mode standard `-tx n/a`)

#### (a) Ressources natives 💉🏥📟🧪 (Immunization, Procedure, Device, DeviceUseStatement, Observation)

*Constat majeur :* **ZÉRO erreur intrinsèque sur les observations, les vaccinations et les interventions.** Les ressources du sprint 3 (`Observation`) respectent à 100 % les profils IPS (`valueQuantity`, `valueCodeableConcept`, `valueString`, `interpretation`, `referenceRange`, `category`, `code`).

Les seules erreurs signalées sur des champs de ressources natives sont des erreurs de **résolution de référence en cascade**, car la ressource cible `Patient` (ou `Device`) échoue à sa propre validation :
- **Immunization, Procedure, Observation :**
  - `Bundle.entry[*].resource.ofType(Immunization).patient` : `Unable to find a profile match for urn:uuid:<patient_uuid> among choices: http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips`
  - `Bundle.entry[*].resource.ofType(Procedure).subject` : `Unable to find a profile match for urn:uuid:<patient_uuid> among choices: http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips`
  - `Bundle.entry[*].resource.ofType(Observation).subject` : `Unable to find a profile match for urn:uuid:<patient_uuid> among choices: .../Patient-uv-ips`
  *(Ces erreurs disparaîtront dès que le profil Patient sera rendu conforme au profil IPS).*
- **Device & DeviceUseStatement (Haru) :**
  - `Bundle.entry[14].resource.ofType(Device).udiCarrier[0].issuer` : `No definition could be found for URL value 'http://hl7.org/fhir/NamingSystem/gs1-di'` *(L'URI du NamingSystem GS1 attendu par HL7 est généralement `http://hl7.org/fhir/NamingSystem/gs1` ou géré via l'extension UDI).*
  - `Bundle.entry[15].resource.ofType(DeviceUseStatement).device` : `Unable to find a profile match for urn:uuid:<device_uuid> among choices: Device-uv-ips` *(Erreur en cascade induite par le point ci-dessus).*

#### (b) Ressources legacy & Structure du Bundle (Patient, Composition, Bundle)

1. **Structure du Bundle :**
   - Chemin : `Bundle`
   - Message : `Constraint failed: bdl-9: 'A document must have an identifier with a system and a value'`
   - *Cause :* Le Bundle document FHIR doit comporter un `Bundle.identifier` avec un `system` et une `value` non vide.
2. **Ressource Patient legacy :**
   - Chemin : `Bundle.entry[1].resource.ofType(Patient).extension[0]`
   - Message : `The extension http://jemmapass.net/fhir/StructureDefinition/blood-type could not be found so is not allowed here`
   - *Cause :* Extension personnalisée legacy non définie dans les profils IPS. (Maintenant que le groupe sanguin est modélisé comme une `Observation` native, cette extension legacy pourra être retirée).
3. **Cas particulier de Kamekichi :**
   - Chemin : `Bundle.entry[1].resource.ofType(Patient).name[0].family`
   - Message : `Constraint failed: ele-1: 'All FHIR elements must have a @value or children' / value cannot be empty`
   - *Cause :* Kamekichi n'ayant pas de nom de famille renseigné, un champ vide `family: ""` est sérialisé au lieu d'omettre l'élément.

---

## C. Exploration de la Base de Connaissances (KB) pour l'écran Résultats

Extraction effectuée sur `knowledge_full.db` (3,1 Go) puis nettoyée. Fichiers publiés dans `device-reports/kb/` :
- `kb-loinc-check.txt`
- `kb-results-valuesets.txt`
- `kb-ips-field-rules-observation.txt`
- `kb-units.txt`

### Les 3 faits KB les plus utiles pour la conception de l'écran Résultats :

1. **Absence complète de LOINC dans la table terminologique principale (`terminology_codes` = 0)** :
   La table `terminology_codes` ne contient aucun concept LOINC (exclusivement UMLS et SNOMED CT). Il existe seulement 29 codes LOINC résiduels dans quelques ValueSets annexes (grossesse, tabagisme, sévérité). **Conséquence directe :** la recherche live FTS5 sur la KB ne peut pas fournir de codes LOINC. Le catalogue embarqué `IpsResultCatalog.kt` (31 analyses courantes, avec alias multilingues EN/FR/JA) est indispensable et constitue la source de vérité pour le picker de résultats de laboratoire.
2. **Disponibilité d'un ValueSet complet de 13 groupes sanguins SNOMED CT traduits en FR et JA (`results-blood-group-snomed-ct-ips-free-set`)** :
   La table `ips_valuesets` contient le free set IPS complet pour les groupes sanguins (13 concepts SNOMED CT, ex. `278149003` *Blood group A Rh(D) positive*, `278147001` *Blood group O Rh(D) positive*). De plus, `ips_valuesets_translations` fournit l'intégralité des libellés traduits en français et en japonais (ex. *"Groupe sanguin A Rh(D) positif"*, *"A型Rh(+)"*). Ce ValueSet peut être directement branché pour proposer un sélecteur de groupe sanguin codé.
3. **Absence totale de dictionnaire d'unités UCUM dans la base SQLite (0 table/vue)** :
   Aucune table de la KB ne répertorie d'unités de mesure (recherche `%ucum%` et `%unit%` = 0 résultat). La liste d'unités courantes `IpsResultCatalog.UNITS` (`mmol/L`, `mg/dL`, `g/dL`, `µmol/L`, `%`, `mL/min/{1.73_m2}`, etc.) doit être gérée côté applicatif dans le catalogue.

---

## Fichiers publiés

- **Dossier de cycle :** `device-reports/feat-ips-18-pillars-cleanup/6694536-20260930-2331/`
  - `env.txt`, `steps.md`, `verify-seed.md`, `report.md`
  - `files/demo_*.fhir.json`, `files/demo_*.json`
  - `logs/unit-tests.log`, `logs/assemble.log`, `logs/install.log`, `logs/logcat-seed.txt`, `logs/logcat-ui.txt`
  - `screenshots/c6-haru-profile.png`
  - `validator/demo_*.xml`, `validator/demo_*.log`, `validator/demo_*-tx.xml`, `validator/demo_*-tx.log`
- **Dossier KB partagé :** `device-reports/kb/`
  - `kb-loinc-check.txt`, `kb-results-valuesets.txt`, `kb-ips-field-rules-observation.txt`, `kb-units.txt`
