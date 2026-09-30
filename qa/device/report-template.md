# Device QA · feat/ips-18-pillars-cleanup · <sha7> · <Pixel 9 · Android 15> · <YYYY-MM-DD HH:MM>

- Exécutant : Antigravity · Hôte : macOS · Langue appareil : <EN|FR|JA>
- Appareil : <modèle> · Android <version> (jamais de numéro de série)
- Build : `logs/assemble.log` · Tests JVM : <50 tests, 0 failed> (`logs/unit-tests.log`)
- Verdict global : **<PASS|FAIL>** — <n> ✅ · <m> ❌ · <k> ⚠️ · <s> ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_kurodo.fhir.json` | |
| T1 fiche Kurodo (section + tuile badge 4) | | `screenshots/10-…`, `11-…`, `12-…` | |
| T2 création (picker, date, dose, lot) | | `screenshots/20-…` → `24-…`, `verify-t2.md` | |
| T3 édition + suppression (bouton du formulaire ET appui long) | | `screenshots/30-…` → `33-…`, `verify-t3.md` | |
| T4 édition allergie conserve les vaccins | | `verify-t4.md` | |
| T5 QR texte EN/FR/JA (texte décodé du QR collé) | | `screenshots/50-…`, `51-…`, `52-…` | |
| T6 FHIR + validateur (bonus) | | | |
| T7 Haru / Kamekichi | | `screenshots/70-…`, `71-…` | |
| T8 chemins alternatifs (texte libre, not-done, ✕, annuler) | | `screenshots/80-…` | |
| T9 chemins d'erreur (validation, date future, rotation, double-tap) | | `screenshots/90-…`, `logs/logcat-ui.txt` | |
| T10 procédures — fiche + liste + création + édition + suppression | | `screenshots/100-…` → `108-…`, `verify-t10.md` | |
| T11 dispositifs — fiche + liste + création (UDI) + édition + suppression | | `screenshots/110-…` → `118-…`, `verify-t11.md` | |
| T12 chemins alternatifs + erreurs (procédures et dispositifs) | | `screenshots/120-…`, `logs/logcat-ui.txt` | |
| T13 canaux — QR texte 🏥/📟 EN/FR/JA + non-régression vaccins/allergies | | `screenshots/130-…`, `verify-t13.md` | |

## Bugs (un bloc par bug)

### B1 — <titre court>
- Sévérité : <bloquant | majeur | mineur | cosmétique>
- Étapes : 1. … 2. … 3. …
- Attendu / Observé :
- Preuves : `screenshots/…`, `logs/…` (extrait logcat ci-dessous)
```
<extrait logcat>
```

## Observations libres
- UX, performances (temps d'ouverture de la liste, du picker), traductions, idées.

## Fichiers publiés
`env.txt`, `steps.md`, `verify-*.md`, `files/demo_*.json`, `logs/*.txt|log`, `screenshots/*.png`
