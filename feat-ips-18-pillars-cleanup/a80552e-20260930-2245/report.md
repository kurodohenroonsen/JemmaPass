# Device QA (Cycle 5) · feat/ips-18-pillars-cleanup · a80552e · Pixel 9 Pro XL · Android 15 · 2026-09-30 22:45

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR (fr-BE)
- Appareil : Pixel 9 Pro XL · Android 15 (modèle + OS, aucun identifiant matériel / numéro de série)
- Build : `logs/assemble.log` · Tests JVM : 50 tests, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 6 ✅ · 0 ❌ · 0 ⚠️ · 13 ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_kurodo.fhir.json` | 50 tests JVM réussis. Seed réinitialisé et vérifié : Kurodo (💉 4, 🏥 2, 📟 0), Haru (💉 3, 🏥 2, 📟 2), Kamekichi (💉 0, 🏥 0, 📟 0). 49 checks conformes. |
| T1 fiche Kurodo (section + tuile badge 4) | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T2 création (picker, date, dose, lot) | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T3 édition + suppression | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T4 édition allergie conserve les vaccins | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T5 QR texte EN/FR/JA | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T6 FHIR + validateur | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T7 Haru / Kamekichi | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T8 chemins alternatifs | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T9 chemins d'erreur | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T10 procédures — cycle 4 | ⏭ | — | Remplacé par les vérifications ciblées C1…C4 ci-dessous. |
| T11 dispositifs — cycle 4 | ⏭ | — | Remplacé par la vérification ciblée D ci-dessous. |
| T12 chemins alternatifs + erreurs | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| T13 canaux QR texte 🏥/📟 | ⏭ | — | Hors périmètre court Cycle 5 (validé cycle 4). |
| C1 Picker Procédures (hint) | ✅ | `screenshots/c5-1-hint-procedure.png` | Hint `🔎 Cherche une intervention (FR, EN, SNOMED…)` conforme (ne mentionne plus « médicament »). Titre de la boîte : « Choisir une intervention ». |
| C2 Picker Procédures (`appendic` & déduplication) | ✅ | `screenshots/c5-2-search-appendic.png`, `logs/logcat-ui.txt` | « 🔪  Appendicectomie » en 1re position. 15 résultats affichés. 0 doublon : `Appendectomy` 0 fois (subsumé par le catalogue), `Fistulization of appendix` exactement 1 fois. Logcat : `generic picker · curated=1 · kb=20 · deduped=6 · localized=1`. |
| C3 Picker Procédures (alias FR & code) | ✅ | `screenshots/c5-3a.png`, `screenshots/c5-3b.png` | « 🔬  Coloscopie » en 1re position pour l'alias français `coloscop` (`c5-3a.png`) et pour le code SNOMED CT `73761001` (`c5-3b.png`). |
| C4 Picker Procédures (localisation FR & FHIR) | ✅ | `screenshots/c5-4-localized.png`, `pull-c5-pr/profiles/demo_kurodo.fhir.json` | Recherche `cholecyst` : 6 résultats. Localisation FR fonctionnelle via `ips_valuesets_translations` (`Cholécystectomie laparoscopique`). Persistance FHIR validée (`code: 45595009`, `system: http://snomed.info/sct`, `display: Cholécystectomie laparoscopique`). `verify_profiles.py` PASS (`--expect-pr demo_kurodo=3`). Suppression par appui long confirmée, retour à 2 interventions. |
| D Picker Dispositifs (hint, `pacem`, `stent`) | ✅ | `screenshots/c5-5-hint-device.png`, `screenshots/c5-6-search-pacem.png`, `screenshots/c5-7-search-stent.png` | Hint `🔎 Cherche un dispositif (FR, EN, SNOMED…)`. `pacem` → « ❤️  Stimulateur cardiaque (pacemaker) » en 1re position. `stent` → « ❤️  Stent coronaire » en 1re position, résultats KB sans aucun doublon (`JJ stent`, `Metal stent`, `Aortic stent`, `Stent, device`, `Plastic stent`, `Tracheal stent`). Annulé sans création. |
| E Non-régression Médicaments (`metfor`, dose) | ✅ | `screenshots/c5-8-medication-picker.png`, `logs/logcat-ui.txt` | Hint inchangé `🔎 Cherche un médicament (FR, EN, ATC…)`. Requête `metfor` : ligne de posologie « 💊 2 g · oral » conservée sur les produits pertinents, variantes de dosage non fusionnées par le dédoublonnage générique (conforme à l'exigence médicament). Annulé sans création. |

---

## Vérification détaillée des points du Cycle 5

### C. Picker Procédures

1. **C1 — Hint du champ de recherche :**
   - **Observé :** `🔎 Cherche une intervention (FR, EN, SNOMED…)` sous l'ID `drug_picker_search`.
   - **Preuve :** `screenshots/c5-1-hint-procedure.png`.
   - Le terme « médicament » a été totalement supprimé pour ce pilier. Le titre de dialogue est bien « Choisir une intervention ».

2. **C2 — Recherche `appendic` & Déduplication :**
   - **Observé :** « 🔪  Appendicectomie » (entrée du catalogue avec emoji et libellé FR) apparaît en tête (position 1).
   - **Comptage des résultats :** `15 résultat(s)` affiché dans `drug_picker_status`.
   - **Déduplication des libellés :**
     - `Appendectomy` : 0 occurrence dans les résultats KB (subsumée par l'entrée du catalogue).
     - `Fistulization of appendix` : exactement 1 occurrence (auparavant présent 3 fois dans la capture 105 du cycle 4).
   - **Logcat émis :**
     ```text
     09-30 23:01:35.785 30100 30100 I JEMMA-KB-DRUG-PICKER: [t=1790802095785] 🧭 generic picker · curated=1 · kb=20 · deduped=6 · localized=1
     09-30 23:01:35.787 30100 30100 I JEMMA-KB-DRUG-PICKER: [t=1790802095787] ✅ search done · raw=40 → script-kept=20 (dropped=20) · doses=0 · 6ms · fts5=true
     ```
   - **Preuve :** `screenshots/c5-2-search-appendic.png`.

3. **C3 — Alias français et recherche par code SNOMED CT :**
   - Recherche `coloscop` : « 🔬  Coloscopie » en tête (`screenshots/c5-3a.png`).
   - Recherche `73761001` : « 🔬  Coloscopie » en tête (`screenshots/c5-3b.png`).

4. **C4 — Localisation française des concepts KB hors catalogue (`cholecyst`) :**
   - **Affichage des résultats :** Au-delà de l'entrée du catalogue (« 🔪  Cholécystectomie »), les concepts KB disposant d'une traduction dans `ips_valuesets_translations` pour le code SNOMED mappé s'affichent bien en français. Les concepts sans traduction française restent affichés avec leur libellé anglais natif UMLS/SNOMED.
   - **3 exemples observés :**
     1. `Cholécystectomie laparoscopique` (FR — traduit via `ips_valuesets_translations`, code SNOMED `45595009`).
     2. `Cholecystectomy with exploration of common duct (procedure)` (EN — concept sans traduction FR dans le ValueSet).
     3. `Cholecystostomy` (EN — concept sans traduction FR).
   - **Preuve visuelle :** `screenshots/c5-4-localized.png`.
   - **Persistance FHIR et validation :**
     - Sélection de `Cholécystectomie laparoscopique`, date `2024-03-15`, Save.
     - `verify_profiles.py` exécuté avec `--expect demo_kurodo=4 --expect-pr demo_kurodo=3` : **PASS** (50 checks, 0 failed).
     - Richesse FHIR vérifiée dans la ressource `Procedure` de `demo_kurodo.fhir.json` :
       - `code`: `"45595009"`
       - `system`: `"http://snomed.info/sct"`
       - `display`: `"Cholécystectomie laparoscopique"`
     - Suppression de la carte effectuée par appui long -> retour à 2 interventions (`Coloscopie`, `Appendicectomie`).

---

### D. Picker Dispositifs Médicaux

1. **Hint :** `🔎 Cherche un dispositif (FR, EN, SNOMED…)` (`screenshots/c5-5-hint-device.png`).
2. **Recherche `pacem` :** « ❤️  Stimulateur cardiaque (pacemaker) » en 1re position (`screenshots/c5-6-search-pacem.png`).
3. **Recherche `stent` :** « ❤️  Stent coronaire » en 1re position (`screenshots/c5-7-search-stent.png`). Les résultats KB suivants ne comportent aucun doublon de libellé : `JJ stent`, `Metal stent`, `Aortic stent`, `Stent, device`, `Plastic stent`, `Tracheal stent`.
4. Annulé sans création (Kurodo reste à 0 dispositif).

---

### E. Non-régression Médicaments

1. **Hint :** Inchangé `🔎 Cherche un médicament (FR, EN, ATC…)` (`screenshots/c5-8-medication-picker.png`).
2. **Recherche `metfor` :**
   - La ligne de posologie « 💊 2 g · oral » est présente et enrichie comme attendu.
   - Les différentes variantes galéniques et posologiques (`Metformin only product`, `metformin hydrochloride 500 MG Oral Tablet`, `metformin Pill`, `glyburide / metformin`, `glipizide / metformin`) sont conservées et ne subissent pas le dédoublonnage strict des libellés appliqué aux procédures/dispositifs.
3. Annulé sans création.

---

### F. Chronométrage (horodatages logcat `[t=…]`)

Mesures extraites des horodatages synchrones `[t=...]` de `JEMMA-PROCEDURES-FORM` et `JEMMA-KB-DRUG-PICKER` sur Pixel 9 Pro XL :

1. **Temps entre le tap sur la carte et l'affichage des suggestions :**
   - Tap carte (`procedure_form_code_card`) : `09-30 23:01:29.758 [t=1790802089758] 📋 open procedure picker · lang=fr`
   - Initialisation et affichage suggestions : `09-30 23:01:29.766 [t=1790802089766] 📋 picker open · title='Choisir une intervention' · lang=fr · category=Procedure · suggestions=12`
   - **Délai :** **8 ms** (affichage instantané des 12 suggestions du catalogue).

2. **Temps entre la fin de saisie de `appendic` et l'affichage des résultats :**
   - Fin de saisie / déclenchement recherche après debounce (350 ms) : `09-30 23:01:35.761 [t=1790802095760] 🔎 search · q='appendic' · stripped='appendic' · lang=fr`
   - Traitement catalogue & déduplication : `09-30 23:01:35.785 [t=1790802095785] 🧭 generic picker · curated=1 · kb=20 · deduped=6 · localized=1`
   - Fin de recherche & affichage adaptateur : `09-30 23:01:35.787 [t=1790802095787] ✅ search done · raw=40 → script-kept=20 (dropped=20) · doses=0 · 6ms · fts5=true`
   - **Temps d'exécution recherche KB :** **27 ms** (dont **6 ms** de latence brute FTS5 SQLite).
   - **Temps ressenti utilisateur :** **377 ms** (350 ms de debounce anti-clignotement + 27 ms d'exécution et rendu).

---

## Bugs

**0 bug détecté.**
Le bug B1 relevé lors du Cycle 4 (doublons de concepts KB, libellés exclusivement en anglais sans exploitation des traductions FR du ValueSet SNOMED, absence de priorisation des entrées du catalogue lors de la recherche, hint erroné « médicament ») est entièrement corrigé.

---

## Fichiers publiés

`env.txt`, `steps.md`, `verify-seed.md`, `report.md`, `files/demo_*.json`, `logs/*.txt|log`, `screenshots/c5-*.png`
