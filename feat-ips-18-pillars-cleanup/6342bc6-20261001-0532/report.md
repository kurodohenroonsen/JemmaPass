# Device QA (Cycle 9 · Displays officiels + Codes allergie) · feat/ips-18-pillars-cleanup · 6342bc6 · Pixel 9 Pro XL · Android 15 · 2026-10-01 05:32

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR (fr-BE)
- Appareil : Pixel 9 Pro XL · Android 15 (modèle + OS, aucun identifiant matériel / numéro de série)
- Build : `logs/assemble.log` · Tests JVM : 72 tests, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 5 ✅ · 0 ❌ · 0 ⚠️ · 12 ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_*.fhir.json` | 72 tests JVM réussis (0 échec). Re-seed automatique : Kurodo (💉 4, 🏥 2, 📟 0, 🧪 4), Haru (💉 3, 🏥 2, 📟 2, 🧪 5), Kamekichi (💉 0, 🏥 0, 📟 0, 🧪 1). 64 checks conformes (0 failed). |
| T1 Non-régression Haru | ✅ | `screenshots/c9-haru-profile.png`, `logs/logcat-ui.txt` | App lancée sans crash (`AndroidRuntime:E` vide). Fiche Haru affichée avec ses 6 piliers actifs et la pastille `🩸 O+`. Libellés courts human-readable conservés. |
| T2 à T13 | ⏭ | — | Hors périmètre court Cycle 9 (validation ciblée displays et KB). |
| A. Libellés officiels FHIR | ✅ | `files/demo_haru.fhir.json`, `validator/*-tx.xml` | `IpsOfficialDisplays.kt` validé : dissociation stricte `display` officiel vs `text` UI. Élimination complète de tous les « Wrong Display Name » sur les 7 analyses LOINC et les 5 vaccins SNOMED. 0 erreur sur ressources natives. |
| B. Exploration KB Allergies | ✅ | `device-reports/kb/kb-allergy-fish-soy.txt` | Requêtes exécutées sur `knowledge_full.db` (3,1 Go) : identification de `782594005` (Allergy to soy protein) dans le free set IPS, `417532002` (Allergy to fish) dans terminology_codes, et des substances associées. Copie locale nettoyée. |

---

## A. Libellés officiels et conformité FHIR (A)

### 1. Citations vérifiées dans `files/demo_haru.fhir.json`

Le commit `6342bc6` introduit la table de correspondance `IpsOfficialDisplays.kt` qui sépare le libellé officiel strict (`coding[0].display`) du libellé d'affichage clinique court (`text`) :

- **Observation Potassium (`rs-haru-potassium-2026`) :**
  - `coding[0].display` = `"Potassium [Moles/volume] in Serum or Plasma"` (terme canonique LOINC 2823-3)
  - `text` = `"Potassium"` (terme court d'interface)
  ```json
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "2823-3",
        "display": "Potassium [Moles/volume] in Serum or Plasma"
      }
    ],
    "text": "Potassium"
  }
  ```
- **Vaccin Grippe saisonnière (`im-haru-flu-2025`) :**
  - `coding[0].display` = `"Influenza virus antigen only vaccine product"` (terme canonique SNOMED CT 1181000221105)
  - `text` = `"Seasonal influenza vaccine"` (terme court d'interface)
  ```json
  "vaccineCode": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "1181000221105",
        "display": "Influenza virus antigen only vaccine product"
      }
    ],
    "text": "Seasonal influenza vaccine"
  }
  ```

---

### 2. Rapport du validateur officiel HL7 (mode `tx.fhir.org`)

Validateur HL7 v6.10.4 exécuté avec `-locale en` et connexion au serveur de terminologie distant (`tx.fhir.org`) :

| Persona | Erreurs totales | Dont « Wrong Display Name » | Erreurs structurelles | Avertissements | Informations | Évolution erreurs vs Cycle 8 |
|---|---|---|---|---|---|---|
| **`demo_kurodo`** | **1** | 1 | **0** | 16 | 0 | 8 ➔ **1** (-7 erreurs, -88 %) |
| **`demo_haru`** | **1** | 1 | **0** | 26 | 1 | 7 ➔ **1** (-6 erreurs, -86 %) |
| **`demo_kamekichi`** | **0** | 0 | **0** | 17 | 0 | 1 ➔ **0** (-1 erreur, 100 % clean) |

#### Analyse d'impact :
1. **Élimination totale des erreurs de display sur les résultats de laboratoire (7/7 LOINC) :**
   - Plus aucune erreur sur : Potassium (`2823-3`), Hémoglobine (`718-7`), eGFR (`33914-3`), Groupe sanguin (`882-1`), HbA1c (`4548-4`), LDL (`2089-1`), Créatinine (`2160-0`).
2. **Élimination totale des erreurs de display sur les vaccinations (5/5 SNOMED CT) :**
   - Plus aucune erreur sur : Grippe saisonnière (`1181000221105`), Pneumocoque PCV (`1801000221105`), Tdap (`871876003`), Hépatite A+B (`871803007`), Encéphalite japonaise (`836378001`).
3. **ZÉRO ERREUR sur les ressources natives 💉🏥📟🧪 :**
   - 100 % de conformité validée sur `Immunization`, `Procedure`, `Device`, `DeviceUseStatement`, et `Observation`.

#### Liste complète des erreurs résiduelles (mode tx) :
Seules **2 erreurs** subsistent sur l'ensemble des 3 personas, toutes deux localisées sur la ressource **legacy** `AllergyIntolerance` en raison des codes SNOMED historiques mal mappés :
1. **Kurodo :**
   - *Chemin :* `Bundle.entry[3].resource/*AllergyIntolerance/null*/.code.coding[0].display`
   - *Message :* `Wrong Display Name 'Allergy to fish' for http://snomed.info/sct#232347008. Valid display is one of 8 choices: 'Allergy to animal dander'...`
   - *Type de ressource :* **Legacy** (`AllergyIntolerance`).
2. **Haru :**
   - *Chemin :* `Bundle.entry[2].resource/*AllergyIntolerance/null*/.code.coding[0].display`
   - *Message :* `Wrong Display Name 'Allergy to soy' for http://snomed.info/sct#419474003. Valid display is one of 5 choices: 'Allergy to mold'...`
   - *Type de ressource :* **Legacy** (`AllergyIntolerance`).

---

## B. Non-régression UI sur appareil (A.4)

1. **Fiche Haru (`screenshots/c9-haru-profile.png`) :**
   - Ouverture instantanée sans crash (`AndroidRuntime:E` vide).
   - La section 💉 affiche fidèlement les libellés courts :
     - `•  Vaccin grippe saisonnière — 2025-10-14`
     - `•  Vaccin COVID-19 (ARNm) — 2024-11-02 · dose 7`
     - `•  Vaccin pneumocoque conjugué (PCV) — 2021-04-06 · dose 1`
2. **Liste détaillée des Vaccinations (`screenshots/c9-haru-vaccins.png`) :**
   - Navigation vers l'écran dédié aux vaccinations réussie via le pilier 💉.
   - Les cartes d'injection affichent les libellés conviviaux traduits en français :
     - 🤧 **Vaccin grippe saisonnière** (et non « Influenza virus antigen only vaccine product »)
     - 🦠 **Vaccin COVID-19 (ARNm)**
     - 💉 **Vaccin pneumocoque conjugué (PCV)**

---

## C. Exploration de la Base de Connaissances (KB) — Codes Allergie Poisson et Soja (B)

Extraction effectuée à partir de `knowledge_full.db` (3,1 Go) tirée de l'appareil. Sorties complètes publiées dans [`device-reports/kb/kb-allergy-fish-soy.txt`](file:///Users/kurodohenroonsen/Documents/jemmapass-device-reports/kb/kb-allergy-fish-soy.txt).

### 1. Synthèse des requêtes KB

- **Explication des codes actuels :**
  - `232347008` = **Allergy to animal dander** (allergie aux squames animales, aucun rapport avec le poisson).
  - `419474003` = **Allergy to mold** (allergie aux moisissures, aucun rapport avec le soja).
- **Concepts identifiés dans le free set IPS (`allergy-intolerance-snomed-ct-ips-free-set`) :**
  - `782594005` | *Allergy to soy protein* (présent dans le free set IPS)
  - `256355007` | *Soyabean* (substance dans le free set IPS, avec traductions FR: « Soja » et JA: « 大豆 »)
  - `735971005` | *Fish* (substance dans le free set IPS)
  - `735341005` | *Fish oil* (substance dans le free set IPS, traductions FR: « Huiles de poisson » et JA: « 魚油 »)
  - `227144008` | *Tuna fish* (substance dans le free set IPS)
- **Concepts identifiés dans `terminology_codes` (UMLS / SNOMED CT) :**
  - `417532002` | *Allergy to fish* (catégorie Condition, UMLS `C0856904`)
  - `782594005` | *Allergy to soy protein* (catégorie Condition, UMLS `C4758660`)

### 2. Codes SNOMED CT recommandés pour correction future des personas

Dans le profil FHIR IPS `AllergyIntolerance-uv-ips`, l'élément `code` accepte soit le concept de propension/allergie clinique (Condition/Finding), soit l'agent responsable (Substance) :

1. **Pour « Allergy to soy » (Haru) :**
   - **Recommandation principale (Condition du Free Set IPS) :**
     - Code SNOMED : **`782594005`**
     - `display_en` exact : **`Allergy to soy protein`**
     - *Avantage :* Fait directement partie du free set IPS (`allergy-intolerance-snomed-ct-ips-free-set`) et est référencé dans `terminology_codes`.
   - *Alternative (Substance du Free Set IPS) :*
     - Code SNOMED : **`256355007`** (`Soyabean`), traduit en FR par « Soja » et en JA par « 大豆 ».

2. **Pour « Allergy to fish » (Kurodo) :**
   - **Recommandation principale (Condition clinique) :**
     - Code SNOMED : **`417532002`**
     - `display_en` exact : **`Allergy to fish`**
     - *Avantage :* Correspond exactement au texte recherché « Allergy to fish », présent dans `terminology_codes` (`C0856904`).
   - *Alternative (Substance du Free Set IPS) :*
     - Code SNOMED : **`735971005`** (`Fish`), présent dans le free set IPS.

*(La copie locale temporaire `/tmp/knowledge_full.db` a été supprimée après extraction).*

---

## D. Fichiers publiés

- **Dossier de cycle :** `device-reports/feat-ips-18-pillars-cleanup/6342bc6-20261001-0532/`
  - `env.txt`, `steps.md`, `verify-seed.md`, `report.md`
  - `files/demo_*.fhir.json`, `files/demo_*.json`
  - `logs/unit-tests.log`, `logs/assemble.log`, `logs/install.log`, `logs/logcat-seed.txt`, `logs/logcat-ui.txt`
  - `screenshots/c9-haru-profile.png`, `screenshots/c9-haru-vaccins.png`
  - `validator/demo_*-tx.xml`, `validator/demo_*-tx.log`
- **Dossier KB partagé :** `device-reports/kb/`
  - `kb-allergy-fish-soy.txt`
