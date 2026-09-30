# Device QA · feat/ips-18-pillars-cleanup · 1c1b7e5 · Pixel 9 Pro XL · Android 17 · 2026-09-30 19:16

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR (fr-BE)
- Build : `logs/assemble.log` (243M) · Tests JVM : 30 tests, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 9 ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_kurodo.fhir.json` | 28/28 checks verts (Kurodo=4, Haru=3, Kamekichi=0) |
| T1 fiche Kurodo (section + tuile badge 4) | ✅ | `screenshots/10-detail-kurodo.png`, `11-detail-pillars.png`, `12-immunizations-list.png` | Section « 💉 VACCINATIONS (4) », tuile active avec badge 4, liste 4 cartes triées par date |
| T2 création (picker, date, dose, lot) | ✅ | `screenshots/20-form-empty.png` → `24-list-after-create.png`, `verify-t2.md` | Vaccin grippe choisi, date du jour, dose 1, lot QA-LOT-001. Kurodo=5 vérifié sur disque |
| T3 édition + suppression | ✅ | `screenshots/30-after-edit.png`, `31-delete-dialog.png`, `verify-t3.md` | Fabricant « QA Lab » édité puis suppression par appui long. Kurodo=4 vérifié sur disque. *(Déviation sélecteur : voir notes)* |
| T4 édition allergie conserve les vaccins | ✅ | `verify-t4.md` | Sauvegarde de l'allergie à la pénicilline : Kurodo conserve exactement ses 4 vaccins et URNs |
| T5 QR texte EN/FR | ✅ | `screenshots/50-qr-text-en.png`, `51-qr-text-fr.png` | `🌐 Texte 🇬🇧 · 662B · 1 frame` et `🌐 Texte 🇫🇷 · 681B · 1 frame`, bien en deçà du cap 2200B |
| T6 FHIR + validateur (bonus) | ✅ | `screenshots/60-qr-fhir.png`, `files/demo_kurodo.fhir.json` | `🏥 FHIR R4 · 8329B · 13 frames`, bundle document conforme, sections LOINC 60591-5 et 11369-6 |
| T7 Haru / Kamekichi | ✅ | `screenshots/70-haru.png`, `71-kamekichi.png`, `72-kamekichi-pillars.png` | Haru : « 💉 VACCINATIONS (3) ». Kamekichi : pas de section vaccins, tuile sans badge (0) |
| T8 régression rapide (radar, médicaments) | ✅ | `screenshots/80-haru-meds.png`, `81-radar-initial.png`, `82-radar-active.png`, `83-radar-stopped.png`, `logs/logcat-ui.txt` | Médicaments Haru ouverts/fermés. Radar SOS actif puis arrêté sans crash (`AndroidRuntime:E` vide) |

## Déviations et adaptations du protocole UI

1. **T3 — Sélecteur du bouton « Supprimer » dans le dialogue d'alerte** :
   - `ui.py tap --text "Supprimer"` cible par défaut la première occurrence de la chaîne, qui s'avère être `alertTitle` (`Supprimer cette vaccination ?`), non cliquable.
   - Contournement : ciblage via `--id button1` (ou `--text "Supprimer" --index 1`).
2. **T5 — Sélecteurs de puces de langue dans l'onglet QR texte** :
   - Le protocole mentionne `$UI tap --id qr_lang_en` / `qr_lang_fr`.
   - Dans le layout, les chips individuels ne portent pas d'identifiant dédié (ils sont enfants anonymes du groupe `qr_lang_chips`).
   - Contournement : ciblage textuel via `$UI tap --text "EN"` et `$UI tap --text "FR"`.
3. **Confidentialité / RGPD — Exclusion de la capture de lancement** :
   - `01-launch.png` affichait la liste des profils comprenant un profil réel sur l'appareil.
   - Conformément à la règle absolue 2 (zéro donnée personnelle dans le dépôt public), cette capture a été retirée du jeu de publication. Seules les captures de profils de démo (`demo_kurodo`, `demo_haru`, `demo_kamekichi`) et d'écrans fonctionnels sont publiées.

## Bugs

Aucun bug bloquant ou fonctionnel détecté dans le code de la branche `feat/ips-18-pillars-cleanup`.
Les 30 tests unitaires JVM passent, les invariants FHIR/IPS P1..P8 sont respectés à 100% à toutes les étapes, et les flux UI (création, édition, suppression, affichage profil, export QR texte multi-langues, QR animé FHIR R4, Radar SOS) sont fluides et stables (0 crash).

Points d'attention mineurs pour l'architecte :
- **ID des chips de langue QR** : attribuer des `resource-id` explicites (`qr_lang_en`, `qr_lang_fr`, etc.) sur les chips pour faciliter l'automatisation des tests UI sans dépendre du matching textuel du drapeau/libellé.
- **Accessibilité du bouton de suppression** : l'appui long pour supprimer une vaccination n'a pas d'alternative visible immédiate (comme un bouton d'action dans le formulaire d'édition). Prévoir éventuellement un bouton « Supprimer » directement au bas de `ImmunizationFormBottomSheet` en mode édition.

## Observations libres
- **Fluidité et performance** : Le build et l'encodage du Bundle FHIR sont extrêmement rapides (~9ms à 31ms sur le Pixel 9 Pro XL).
- **Rendu QR multi-frame** : La lecture et l'animation du QR FHIR R4 découpé en 13 frames fonctionne avec contrôles de lecture/pause clairs et prévisualisation du payload.
- **Isolation des personas** : La sauvegarde et la réhydratation automatique des profils n'ont à aucun moment impacté les données existantes de l'appareil (les 6 Go de modèle Gemma/KB sont restés intacts).

## Fichiers publiés
`env.txt`, `steps.md`, `verify-seed.md`, `verify-t2.md`, `verify-t3.md`, `verify-t4.md`, `report.md`, `files/demo_*.json`, `logs/*.txt|log`, `screenshots/*.png`
