# Device QA (Cycle 3) · feat/ips-18-pillars-cleanup · c3a383f · Pixel 9 Pro XL · Android 17 · 2026-09-30 20:58

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR (fr-BE)
- Build : `logs/assemble.log` · Tests JVM : 35 tests, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 4 ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_kurodo.fhir.json` | 28 invariants P1…P8 validés sur les 3 personas (Kurodo=4, Haru=3, Kamekichi=0). 35 tests JVM réussis (+5 tests `IpsVaccineCatalogTest`). |
| 3a. Erreurs inline sur les champs (B1) | ✅ | `screenshots/c3-3a-1-empty-vaccine-error.png` → `c3-3a-3b-dose-series-cleared.png`, `logs/logcat-ui.txt` | Les 3 cas (aucun vaccin, dose 0, dose 3 > série 2) affichent l'erreur en rouge persistant sur le `TextInputLayout` avec icône d'erreur, consignent un `Log.w`, et s'effacent instantanément dès la modification du champ. |
| 3b. Recherche multilingue dans le picker (B2) | ✅ | `screenshots/c3-3b-1-search-influenza.png` → `c3-3b-4-search-code.png` | `🤧  Vaccin grippe saisonnière` apparaît en 1re position pour « influenza », « grippe », « saisonniere » (sans accent) et « 1181000221105 ». |
| 3c. Anti-rebond Enregistrer (B3) | ✅ | `screenshots/c3-3c-antirebond-5x.png`, `c3-3c-after-cleanup.png`, `verify-final.md` | 5 doubles-taps consécutifs en parallèle (`input tap & input tap`) : exactement 1 seule carte créée par tentative (4 → 5 → 6 → 7 → 8 → 9). Nettoyage des 5 cartes, retour à 4 vaccins conforme sur disque (`verify-final.md` PASS). |
| Tâche annexe — Schéma KB | ✅ | `kb/kb-schema.sql`, `kb/kb-valuesets.txt`, `kb/kb-systems.txt`, `kb/kb-tables.txt` | Base `knowledge_full.db` (3,1 Go) extraite depuis `/sdcard/Android/data/be.heyman.android.jemmapassdemo/files/knowledge_full_db/1.1/`. Schéma et métadonnées publiés dans `device-reports/kb/`. |

---

## Vérification détaillée des trois correctifs du commit `c3a383f`

### 1. Correctif B1 — Erreurs de validation inline (`TextInputLayout.error`)
- **Statut :** **CONFIRMÉ**
- **Preuves :**
  - **Save sans vaccin :** Erreur inline rouge « Choisis un vaccin ou saisis son nom » sous `immunization_form_text_layout` (`screenshots/c3-3a-1-empty-vaccine-error.png`). Logcat émis : `W JEMMA-IMMUNIZATIONS-FORM: ⚠ validation: no vaccine picked nor typed`. Effacement immédiat dès la saisie d'un caractère (`screenshots/c3-3a-1b-empty-vaccine-cleared.png`).
  - **Dose 0 :** Erreur inline rouge « Les numéros de dose doivent être des entiers positifs » sous `immunization_form_dose_number_layout` (`screenshots/c3-3a-2-dose-zero-error.png`). Logcat émis : `W JEMMA-IMMUNIZATIONS-FORM: ⚠ validation: dose number invalid '0'`. Effacement immédiat dès la saisie de '1' (`screenshots/c3-3a-2b-dose-zero-cleared.png`).
  - **Dose 3 > série 2 :** Erreur inline rouge « Le numéro de dose ne peut pas dépasser le nombre de doses du schéma » sous `immunization_form_series_doses_layout` (`screenshots/c3-3a-3-dose-series-error.png`). Logcat émis : `W JEMMA-IMMUNIZATIONS-FORM: ⚠ validation: dose 3 > series 2`. Effacement immédiat dès modification du schéma (`screenshots/c3-3a-3b-dose-series-cleared.png`).

### 2. Correctif B2 — Recherche multilingue et par code dans le catalogue de vaccins
- **Statut :** **CONFIRMÉ**
- **Preuves :**
  - Requête `"influenza"` (terme anglais / nom générique médical) : `🤧  Vaccin grippe saisonnière` est affiché en 1re position (`screenshots/c3-3b-1-search-influenza.png`).
  - Requête `"grippe"` (libellé localisé français) : `🤧  Vaccin grippe saisonnière` en 1re position (`screenshots/c3-3b-2-search-grippe.png`).
  - Requête `"saisonniere"` (insensible aux accents) : `🤧  Vaccin grippe saisonnière` en 1re position (`screenshots/c3-3b-3-search-saisonniere.png`).
  - Requête `"1181000221105"` (code numérique SNOMED CT) : `🤧  Vaccin grippe saisonnière` en 1re position (`screenshots/c3-3b-4-search-code.png`).

### 3. Correctif B3 — Verrouillage anti-rebond immédiat sur le bouton « Enregistrer »
- **Statut :** **CONFIRMÉ**
- **Preuves :**
  - Exécution de 5 créations consécutives en envoyant deux taps simultanés (`adb shell "input tap 798 2104 & input tap 798 2104"`).
  - Le compteur de vaccinations a progressé strictement de 1 à chaque tentative : 5, 6, 7, 8, puis 9 vaccinations (`screenshots/c3-3c-antirebond-5x.png`).
  - Aucun doublon généré (protection `if (!saveBtn.isEnabled) return; saveBtn.isEnabled = false`).
  - Suppression successive des 5 entrées jetables (`REBOND-005` à `REBOND-001`), retour à 4 vaccinations (`screenshots/c3-3c-after-cleanup.png`).
  - Vérification disque FHIR : `verify-final.md` PASS (9 invariants conformes, count=4).

---

## Tâche annexe — Base de connaissances (KB)

- **Fichier source sur appareil :** `/sdcard/Android/data/be.heyman.android.jemmapassdemo/files/knowledge_full_db/1.1/knowledge_full.db` (3 360 727 040 octets · 3,1 Go). Le fichier est resté intact sur l'appareil.
- **Dossier publié :** `device-reports/kb/` (à la racine de la branche `device-reports`) :
  - `kb-tables.txt` : 64 tables et vues (dont `ddi_facts`, `atc_hierarchy`, `terminology_codes`, `ips_valuesets_translations`, FTS5 tables).
  - `kb-schema.sql` : 25 Ko de DDL complet.
  - `kb-systems.txt` : Répartition par système terminologique (UMLS : 760 485 concepts, SNOMED CT : 19 697 codes).
  - `kb-valuesets.txt` : 18 value sets IPS traduits (dont 88 vaccins, 70 956 problèmes, 6 069 procédures).

---

## Fichiers publiés

`env.txt`, `steps.md`, `verify-seed.md`, `verify-final.md`, `files/demo_*.json`, `logs/*.txt|log`, `screenshots/*.png`, `kb/*`
